package com.articleTraceBack.Service;

import com.articleTraceBack.mapper.AuthorApplyMapper;
import com.articleTraceBack.pojo.AuthorApply;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/** 作者申请-审批实现；通知渠道由 {@code notification.scenes} 配置决定。 */
@Slf4j
@Service
public class AuthorApplyServiceImpl implements AuthorApplyService {

    /** 与 application.yml 的 notification.scenes 对齐 */
    private static final String SCENE_SUBMITTED = "author-apply-submitted";
    private static final String SCENE_APPROVED = "author-apply-approved";
    private static final String SCENE_REJECTED = "author-apply-rejected";

    /** 站长角色 type */
    private static final int ROLE_MASTER = 0;
    /** 作者角色 type */
    private static final int ROLE_AUTHOR = 1;

    private final AuthorApplyMapper applyMapper;
    private final UserService userService;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;

    public AuthorApplyServiceImpl(AuthorApplyMapper applyMapper,
                                  UserService userService,
                                  NotificationService notificationService,
                                  TransactionTemplate transactionTemplate) {
        this.applyMapper = applyMapper;
        this.userService = userService;
        this.notificationService = notificationService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public boolean submit(int userId, String reason) {
        // 已有待审申请 → 不允许重复提交
        QueryWrapper<AuthorApply> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("status", AuthorApply.STATUS_PENDING);
        if (applyMapper.selectCount(wrapper) > 0) {
            return false;
        }

        AuthorApply apply = new AuthorApply();
        apply.setUserId(userId);
        apply.setReason(reason);
        apply.setStatus(AuthorApply.STATUS_PENDING);
        apply.setCreateTime(LocalDateTime.now());
        try {
            if (applyMapper.insert(apply) != 1) {
                return false;
            }
        } catch (DuplicateKeyException e) {
            // 并发下两个请求可能同时通过上面的查重；
            // 由唯一索引 uk_pending(user_id, pending_flag) 兜底，这里只当作「已有待审」处理
            log.info("author apply rejected by unique index: userId={}", userId);
            return false;
        }

        // 通知所有站长（配置为 both：站内 + 邮件）
        User applicant = userService.findUserById(userId);
        String who = (applicant == null)
                ? ("用户#" + userId)
                : (applicant.getNickname() + "（" + applicant.getUsername() + "）");
        notificationService.notifyRole(ROLE_MASTER, SCENE_SUBMITTED, "新的作者申请",
                who + " 提交了作者申请，请及时处理。");
        log.info("author apply submitted: userId={}, applyId={}", userId, apply.getId());
        return true;
    }

    @Override
    public AuthorApply findMine(int userId) {
        QueryWrapper<AuthorApply> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("create_time").last("limit 1");
        return applyMapper.selectOne(wrapper);
    }

    @Override
    public PageBean<AuthorApply> list(Integer status, int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 1);
        int total = applyMapper.countByStatus(status);
        int offset = (safePageNum - 1) * safePageSize;
        List<AuthorApply> items = applyMapper.selectPageWithUser(status, offset, safePageSize);
        return new PageBean<>(total, items);
    }

    @Override
    public int pendingCount() {
        QueryWrapper<AuthorApply> wrapper = new QueryWrapper<>();
        wrapper.eq("status", AuthorApply.STATUS_PENDING);
        return Math.toIntExact(applyMapper.selectCount(wrapper));
    }

    @Override
    public boolean review(int applyId, boolean pass, String rejectReason, int reviewerId) {
        // 只为拿申请人 id（后续提权/通知要用）；状态判断不依赖这次读取
        AuthorApply apply = applyMapper.selectById(applyId);
        if (apply == null || apply.getUserId() == null) {
            return false;
        }
        int applicantId = apply.getUserId();

        if (pass) {
            // CAS 置 approved 与提权必须在一个事务里：提权失败要整体回滚，回到可重试的待审状态。
            // 此前 changeType 返回值被丢弃——会出现「记录已通过、人没提权、通知照发」且无法重试。
            // 通知与令牌失效不进事务：邮件线程异步跑，未提交的事务里它读不到刚落的行。
            User applicant = userService.findUserById(applicantId);
            if (applicant == null || !approveAtomically(applyId, reviewerId, applicantId)) {
                return false;
            }
            userService.deleteRedisToken(applicant.getUsername());
            notificationService.notify(applicantId, SCENE_APPROVED, "作者申请已通过",
                    "恭喜！你的作者申请已通过，现在可以发布文章了。");
            log.info("author apply approved: applyId={}, userId={}", applyId, applicantId);
            return true;
        }

        // 拒绝：单表 CAS + 通知；通知失败不产生状态不一致，维持无事务
        UpdateWrapper<AuthorApply> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", applyId)
                .eq("status", AuthorApply.STATUS_PENDING)
                .set("status", AuthorApply.STATUS_REJECTED)
                .set("review_user", reviewerId)
                .set("review_time", LocalDateTime.now())
                .set("reject_reason", rejectReason);
        if (applyMapper.update(null, updateWrapper) != 1) {
            log.info("author apply already handled, skip: applyId={}, reviewerId={}", applyId, reviewerId);
            return false;
        }
        String reason = (rejectReason == null || rejectReason.isBlank()) ? "未说明原因" : rejectReason;
        notificationService.notify(applicantId, SCENE_REJECTED, "作者申请未通过",
                "很遗憾，你的作者申请未通过。原因：" + reason);
        log.info("author apply rejected: applyId={}, userId={}", applyId, applicantId);
        return true;
    }

    /** 通过路径的事务单元：CAS 置 approved + 提权，任一步失败回滚到待审 */
    private boolean approveAtomically(int applyId, int reviewerId, int applicantId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            UpdateWrapper<AuthorApply> cas = new UpdateWrapper<>();
            cas.eq("id", applyId)
                    .eq("status", AuthorApply.STATUS_PENDING)
                    .set("status", AuthorApply.STATUS_APPROVED)
                    .set("review_user", reviewerId)
                    .set("review_time", LocalDateTime.now());
            if (applyMapper.update(null, cas) != 1) {
                log.info("author apply already handled, skip: applyId={}, reviewerId={}", applyId, reviewerId);
                status.setRollbackOnly();
                return false;
            }
            if (!userService.changeType(applicantId, ROLE_AUTHOR)) {
                log.warn("promote to author failed, approval rolled back: applyId={}, userId={}", applyId, applicantId);
                status.setRollbackOnly();
                return false;
            }
            return true;
        }));
    }
}
