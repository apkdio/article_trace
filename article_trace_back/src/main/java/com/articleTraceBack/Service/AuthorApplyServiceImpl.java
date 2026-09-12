package com.articleTraceBack.Service;

import com.articleTraceBack.mapper.AuthorApplyMapper;
import com.articleTraceBack.pojo.AuthorApply;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作者申请-审批实现。
 *
 * <p>通知走 {@link NotificationService}，渠道由 {@code notification.scenes} 配置决定
 * （提交 → both 通知站长；审批结果 → inbox 通知申请人）。</p>
 */
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

    public AuthorApplyServiceImpl(AuthorApplyMapper applyMapper,
                                  UserService userService,
                                  NotificationService notificationService) {
        this.applyMapper = applyMapper;
        this.userService = userService;
        this.notificationService = notificationService;
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
        if (applyMapper.insert(apply) != 1) {
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
        AuthorApply apply = applyMapper.selectById(applyId);
        if (apply == null || apply.getStatus() == null
                || apply.getStatus() != AuthorApply.STATUS_PENDING) {
            return false;
        }

        apply.setStatus(pass ? AuthorApply.STATUS_APPROVED : AuthorApply.STATUS_REJECTED);
        apply.setReviewUser(reviewerId);
        apply.setReviewTime(LocalDateTime.now());
        if (!pass) {
            apply.setRejectReason(rejectReason);
        }
        if (applyMapper.updateById(apply) != 1) {
            return false;
        }

        if (pass) {
            // 提升为作者；并使其现有登录态失效（重新登录后拿到新身份）
            User applicant = userService.findUserById(apply.getUserId());
            if (applicant != null) {
                userService.changeType(apply.getUserId(), ROLE_AUTHOR);
                userService.deleteRedisToken(applicant.getUsername());
            }
            notificationService.notify(apply.getUserId(), SCENE_APPROVED, "作者申请已通过",
                    "恭喜！你的作者申请已通过，现在可以发布文章了。");
            log.info("author apply approved: applyId={}, userId={}", applyId, apply.getUserId());
        } else {
            String reason = (rejectReason == null || rejectReason.isBlank()) ? "未说明原因" : rejectReason;
            notificationService.notify(apply.getUserId(), SCENE_REJECTED, "作者申请未通过",
                    "很遗憾，你的作者申请未通过。原因：" + reason);
            log.info("author apply rejected: applyId={}, userId={}", applyId, apply.getUserId());
        }
        return true;
    }
}
