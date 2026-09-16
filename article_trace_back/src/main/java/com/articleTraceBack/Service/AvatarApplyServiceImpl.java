package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.FileCheckUtil;
import com.articleTraceBack.Utils.PageUtil;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.mapper.AvatarApplyMapper;
import com.articleTraceBack.pojo.AvatarApply;
import com.articleTraceBack.pojo.PageBean;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 头像上传审核实现。
 *
 * <p>审批链路照 {@link AuthorApplyService} 的乐观 CAS：条件更新带 {@code status=待审}，
 * 影响行数为 0 即视为「已被他人处理」，后续动作全部不执行。</p>
 */
@Slf4j
@Service
public class AvatarApplyServiceImpl implements AvatarApplyService {

    /** 待审头像的业务类型，落到 avatar 桶 */
    private static final String TYPE_AVATAR = "avatar";
    /** 已生效头像的业务类型，在 pic 桶 */
    private static final String TYPE_IMAGE = "image";

    private final AvatarApplyMapper applyMapper;
    private final UserService userService;
    private final RustFsUtil rustFsUtil;

    public AvatarApplyServiceImpl(AvatarApplyMapper applyMapper,
                                  UserService userService,
                                  RustFsUtil rustFsUtil) {
        this.applyMapper = applyMapper;
        this.userService = userService;
        this.rustFsUtil = rustFsUtil;
    }

    @Override
    public boolean submit(int userId, MultipartFile file) {
        // 已有待审记录 → 不允许重复提交
        QueryWrapper<AvatarApply> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("status", AvatarApply.STATUS_PENDING);
        if (applyMapper.selectCount(wrapper) > 0) {
            return false;
        }

        String extension = FileCheckUtil.extensionOf(file);
        if (extension == null) {
            return false;
        }
        String fileName = System.currentTimeMillis() + userId + "avatar" + extension;
        if (!rustFsUtil.upload(file, TYPE_AVATAR, fileName)) {
            log.error("avatar upload failed: userId={}, key={}", userId, fileName);
            return false;
        }

        AvatarApply apply = new AvatarApply();
        apply.setUserId(userId);
        apply.setPendingPic(fileName);
        apply.setStatus(AvatarApply.STATUS_PENDING);
        apply.setCreateTime(LocalDateTime.now());
        try {
            if (applyMapper.insert(apply) != 1) {
                reclaim(fileName);
                return false;
            }
        } catch (DuplicateKeyException e) {
            // 并发下两个请求可能同时通过上面的查重，由唯一索引 uk_pending(user_id, pending_flag) 兜底。
            // 记录没建成，刚上传的对象要回收，否则又是在存储里留孤儿。
            log.info("avatar apply rejected by unique index: userId={}", userId);
            reclaim(fileName);
            return false;
        }
        log.info("avatar apply submitted: userId={}, applyId={}", userId, apply.getId());
        return true;
    }

    @Override
    public AvatarApply findMine(int userId) {
        QueryWrapper<AvatarApply> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("create_time").last("limit 1");
        return applyMapper.selectOne(wrapper);
    }

    @Override
    public PageBean<AvatarApply> list(Integer status, int pageNum, int pageSize) {
        int safePageNum = PageUtil.normalizePageNum(pageNum);
        int safePageSize = PageUtil.normalizePageSize(pageSize);
        int total = applyMapper.countByStatus(status);
        int offset = (safePageNum - 1) * safePageSize;
        List<AvatarApply> items = applyMapper.selectPageWithUser(status, offset, safePageSize);
        items.forEach(this::fillPicUrl);
        return new PageBean<>(total, items);
    }

    @Override
    public int pendingCount() {
        QueryWrapper<AvatarApply> wrapper = new QueryWrapper<>();
        wrapper.eq("status", AvatarApply.STATUS_PENDING);
        return Math.toIntExact(applyMapper.selectCount(wrapper));
    }

    @Override
    public boolean review(int applyId, boolean pass, String rejectReason, int reviewerId) {
        // 只为拿申请人 id 与待审对象名；状态判断不依赖这次读取
        AvatarApply apply = applyMapper.selectById(applyId);
        if (apply == null || apply.getUserId() == null) {
            return false;
        }
        int applicantId = apply.getUserId();
        String pendingPic = apply.getPendingPic();

        // 条件更新：只有仍是「待审」才改得动。
        // 并发下（两个站长同时审批）只有一个请求的影响行数是 1，其余返回 0 → 直接失败。
        UpdateWrapper<AvatarApply> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", applyId)
                .eq("status", AvatarApply.STATUS_PENDING)
                .set("status", pass ? AvatarApply.STATUS_APPROVED : AvatarApply.STATUS_REJECTED)
                .set("review_user", reviewerId)
                .set("review_time", LocalDateTime.now());
        if (!pass) {
            updateWrapper.set("reject_reason", rejectReason);
        }
        if (applyMapper.update(null, updateWrapper) != 1) {
            log.info("avatar apply already handled, skip: applyId={}, reviewerId={}", applyId, reviewerId);
            return false;
        }

        if (pass) {
            // 待审对象在 avatar 桶，而 user_pic 的读取侧（签名、重置、缩略图补齐）一律按 pic 桶解析，
            // 所以先把它搬到 pic 桶再写 user_pic，否则头像会变成裂图。
            if (!rustFsUtil.copyTo(pendingPic, TYPE_AVATAR, TYPE_IMAGE)) {
                // 搬不动就不要往下走：退回待审，站长还可以重试。
                // 留下一条 approved 但 user_pic 没换的记录反而更难收拾。
                rollbackToPending(applyId);
                log.error("avatar promote failed, rolled back to pending: applyId={}, key={}", applyId, pendingPic);
                return false;
            }

            // 先换 DB 指向、拿到旧头像名，成功后再删旧对象——反过来的话，写库失败会让
            // user_pic 指向一个已删除的对象，头像变裂图且用户自己恢复不了。
            String oldPic = userService.updateUserPic(applicantId, pendingPic);
            if (oldPic != null && !oldPic.isEmpty() && !rustFsUtil.delete(oldPic, TYPE_IMAGE)) {
                log.warn("old user logo delete failed, may be orphan: userId={}, key={}", applicantId, oldPic);
            }
            // pic 桶已经有转正的那份，avatar 桶的待审对象可以丢了
            if (!rustFsUtil.delete(pendingPic, TYPE_AVATAR)) {
                log.warn("pending avatar cleanup failed, may be orphan: key={}", pendingPic);
            }
            log.info("avatar apply approved: applyId={}, userId={}", applyId, applicantId);
        } else {
            // 拒绝：user_pic 不动，只丢掉待审对象
            if (pendingPic != null && !pendingPic.isEmpty() && !rustFsUtil.delete(pendingPic, TYPE_AVATAR)) {
                log.warn("pending avatar delete failed, may be orphan: key={}", pendingPic);
            }
            log.info("avatar apply rejected: applyId={}, userId={}", applyId, applicantId);
        }
        return true;
    }

    /** 填充待审头像的访问链接（原图 + 缩略图），供审核列表展示 */
    private void fillPicUrl(AvatarApply item) {
        String key = item.getPendingPic();
        if (key == null || key.isEmpty() || item.getStatus() == null
                || item.getStatus() != AvatarApply.STATUS_PENDING) {
            // 只有待审记录的对象还在 avatar 桶：通过后已搬到 pic 桶，拒绝后已删除
            return;
        }
        item.setPendingPicSrc(rustFsUtil.getPciUrl(key, TYPE_AVATAR));
        item.setPendingPicThumbSrc(rustFsUtil.getThumbUrl(key, TYPE_AVATAR));
    }

    /** 回收建记录失败时已经上传的对象 */
    private void reclaim(String key) {
        if (!rustFsUtil.delete(key, TYPE_AVATAR)) {
            log.warn("pending avatar rollback failed, may be orphan: key={}", key);
        }
    }

    /** 转正失败时把记录退回待审，让站长还能重试 */
    private void rollbackToPending(int applyId) {
        UpdateWrapper<AvatarApply> rollback = new UpdateWrapper<>();
        rollback.eq("id", applyId)
                .eq("status", AvatarApply.STATUS_APPROVED)
                .set("status", AvatarApply.STATUS_PENDING)
                .set("review_user", null)
                .set("review_time", null);
        if (applyMapper.update(null, rollback) != 1) {
            log.error("avatar rollback failed, record stays approved: applyId={}", applyId);
        }
    }
}
