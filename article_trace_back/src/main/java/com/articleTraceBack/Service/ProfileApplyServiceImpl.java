package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.PageUtil;
import com.articleTraceBack.constant.RedisKeys;
import com.articleTraceBack.mapper.ProfileApplyMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.ProfileApply;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ProfileApplyServiceImpl implements ProfileApplyService {

    private static final String SCENE_APPROVED = "profile-approved";
    private static final String SCENE_REJECTED = "profile-rejected";
    /** 拒绝邮件模板（放 templates/email/），需 .html 与 .txt 两份 */
    private static final String TEMPLATE_REJECTED = "profile-rejected";

    private final ProfileApplyMapper profileApplyMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationService notificationService;

    public ProfileApplyServiceImpl(ProfileApplyMapper profileApplyMapper, UserMapper userMapper,
                                   StringRedisTemplate stringRedisTemplate,
                                   NotificationService notificationService) {
        this.profileApplyMapper = profileApplyMapper;
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.notificationService = notificationService;
    }

    @Override
    public boolean submit(int userId, int type, String pendingValue) {
        ProfileApply apply = new ProfileApply();
        apply.setUserId(userId);
        apply.setType(type);
        apply.setPendingValue(pendingValue);
        apply.setStatus(ProfileApply.STATUS_PENDING);
        apply.setCreateTime(LocalDateTime.now());
        try {
            return profileApplyMapper.insert(apply) == 1;
        } catch (DuplicateKeyException e) {
            // uk_pending(user_id, type, pending_flag)：同一人同一类已经有一条待审
            log.info("profile apply already pending: userId={}, type={}", userId, type);
            return false;
        }
    }

    @Override
    public void cancelPending(int userId, int type) {
        profileApplyMapper.delete(new QueryWrapper<ProfileApply>()
                .eq("user_id", userId)
                .eq("type", type)
                .eq("status", ProfileApply.STATUS_PENDING));
    }

    @Override
    public PageBean<ProfileApply> list(Integer status, int pageNum, int pageSize) {
        pageNum = PageUtil.normalizePageNum(pageNum);
        pageSize = PageUtil.normalizePageSize(pageSize);
        PageBean<ProfileApply> pageBean = new PageBean<>();
        int total = profileApplyMapper.countByStatus(status);
        pageBean.setTotal(total);
        pageBean.setItems(total == 0
                ? List.of()
                : profileApplyMapper.selectPageWithUser(status, (pageNum - 1) * pageSize, pageSize));
        return pageBean;
    }

    @Override
    public int pendingCount() {
        return profileApplyMapper.countByStatus(ProfileApply.STATUS_PENDING);
    }

    @Override
    public boolean review(int applyId, boolean pass, String rejectReason, Integer reviewerId) {
        ProfileApply apply = profileApplyMapper.selectById(applyId);
        // 只处理昵称与个签两类；个签目前没有提交入口以外的用法
        if (apply == null || apply.getType() == null || apply.getType() > ProfileApply.TYPE_SIGNATURE) {
            return false;
        }
        String item = apply.getType() == ProfileApply.TYPE_SIGNATURE ? "个签" : "昵称";
        UpdateWrapper<ProfileApply> cas = new UpdateWrapper<>();
        cas.eq("id", applyId).eq("status", ProfileApply.STATUS_PENDING)
                .set("status", pass ? ProfileApply.STATUS_APPROVED : ProfileApply.STATUS_REJECTED)
                .set("review_user", reviewerId)
                .set("review_time", LocalDateTime.now());
        if (!pass) {
            cas.set("reject_reason", rejectReason);
        }
        if (profileApplyMapper.update(null, cas) != 1) {
            return false;
        }
        if (!pass) {
            // 理由要送到用户手上，所以场景配成站内信 + 邮件
            String reason = (rejectReason == null || rejectReason.isBlank()) ? "未说明原因" : rejectReason;
            notificationService.notify(apply.getUserId(), SCENE_REJECTED, item + "审核未通过",
                    "你提交的" + item + "未通过审核。原因：" + reason,
                    TEMPLATE_REJECTED,
                    Map.of("reason", reason, "item", item, "nickname", nicknameOf(apply.getUserId())));
            log.info("profile apply rejected: applyId={}, userId={}", applyId, apply.getUserId());
            return true;
        }
        // 通过：写回待审值并起锁定期（昵称与个签同一个锁）。用户若在此期间正常改过，
        // 待审行已被 cancelPending 删掉，上面的 CAS 取不到行、不会覆盖他后来改的值。
        User user = new User();
        user.setUpdateTime(LocalDateTime.now());
        if (apply.getType() == ProfileApply.TYPE_SIGNATURE) {
            user.setSignature(apply.getPendingValue());
        } else {
            user.setNickname(apply.getPendingValue());
        }
        userMapper.update(user, new UpdateWrapper<User>().eq("id", apply.getUserId()));
        stringRedisTemplate.opsForValue().set(RedisKeys.PROFILE_NICKNAME_LOCK + apply.getUserId(),
                "1", RedisKeys.PROFILE_NICKNAME_LOCK_DAYS, TimeUnit.DAYS);
        // 通过是「已生效」的轻量告知，场景配成仅站内信
        notificationService.notify(apply.getUserId(), SCENE_APPROVED, item + "审核已通过",
                "你的新" + item + "已通过审核，现在已经在使用了。");
        log.info("profile apply approved: applyId={}, userId={}", applyId, apply.getUserId());
        return true;
    }

    /** 称呼用的当前昵称；取不到就退回用户名 */
    private String nicknameOf(int userId) {
        User user = userMapper.selectById(userId);
        return user == null || user.getNickname() == null || user.getNickname().isBlank()
                ? ("用户#" + userId) : user.getNickname();
    }
}
