package com.articleTraceBack;

import com.articleTraceBack.Service.AuthorApplyService;
import com.articleTraceBack.mapper.AuthorApplyMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.AuthorApply;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.articleTraceBack.Service.UserService;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 作者审批的事务性（bugs_report P1-5）：提权失败时，CAS 置 approved 必须一起回滚，
 * 回到可重试的待审状态——不允许出现「记录已通过、人没提权」且无法重试的中间态。
 *
 * <pre>mvn test -Dtest=AuthorApplyApprovalTxTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class AuthorApplyApprovalTxTest {

    @Autowired
    private AuthorApplyService applyService;

    @Autowired
    private AuthorApplyMapper applyMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TestFixtures fixtures;

    @MockitoBean
    private UserService userService;

    /** 造一条待审申请，返回其 id */
    private int pendingApply(int userId) {
        AuthorApply apply = new AuthorApply();
        apply.setUserId(userId);
        apply.setReason("事务测试");
        apply.setStatus(AuthorApply.STATUS_PENDING);
        apply.setCreateTime(LocalDateTime.now());
        applyMapper.insert(apply);
        return apply.getId();
    }

    @Test
    public void promotionFailureRollsBackApproval() {
        int userId = fixtures.ensureUser(2);
        User reader = userMapper.selectById(userId);
        int applyId = pendingApply(userId);
        // changeType 返回 false：模拟提权失败（旧实现会丢弃返回值，留下已通过但没提权的记录）
        org.mockito.BDDMockito.given(userService.changeType(userId, 1)).willReturn(false);
        org.mockito.BDDMockito.given(userService.findUserById(userId)).willReturn(reader);

        assertFalse(applyService.review(applyId, true, null, 1), "提权失败时审批应返回失败");

        AuthorApply after = applyMapper.selectById(applyId);
        assertEquals(AuthorApply.STATUS_PENDING, after.getStatus(),
                "提权失败必须整体回滚到待审，审批可重试");
        assertEquals(2, userMapper.selectById(userId).getType(), "用户不得被提权");

        applyMapper.deleteById(applyId);
    }

    @Test
    public void concurrentSecondApprovalFailsCleanly() {
        int userId = fixtures.ensureUser(2);
        User reader = userMapper.selectById(userId);
        int applyId = pendingApply(userId);
        // 先把记录置为已处理（模拟另一个站长先审完）
        applyMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AuthorApply>()
                .eq("id", applyId).set("status", AuthorApply.STATUS_APPROVED));

        org.mockito.BDDMockito.given(userService.findUserById(userId)).willReturn(reader);

        assertFalse(applyService.review(applyId, true, null, 1), "已被处理的申请不能再审");

        applyMapper.deleteById(applyId);
    }
}
