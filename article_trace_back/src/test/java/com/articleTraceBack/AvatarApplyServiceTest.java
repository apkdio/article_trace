package com.articleTraceBack;

import com.articleTraceBack.Service.AvatarApplyService;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.mapper.AvatarApplyMapper;
import com.articleTraceBack.mapper.NotificationMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.AvatarApply;
import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 头像上传审核的核心链路。
 *
 * <p>关键约定：待审期间 {@code user.user_pic} 一动不动，只有通过才换；
 * 换的时候先写库再删旧对象，所以 DB 的指向永远不会落在已删除的对象上。</p>
 *
 * <p>{@link RustFsUtil} 被替换为 mock，用例不依赖真实的 RustFS（也不碰 avatar 桶）。</p>
 *
 * <pre>mvn test -Dtest=AvatarApplyServiceTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class AvatarApplyServiceTest {

    /** setUp 里预置的旧头像对象名 */
    private static final String OLD_PIC = "zz-test-old-logo.png";

    @Autowired
    private AvatarApplyService avatarApplyService;

    @Autowired
    private AvatarApplyMapper applyMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NotificationMapper notificationMapper;

    @MockitoBean
    private RustFsUtil rustFsUtil;

    private Integer testUserId;
    private String testUsername;

    @BeforeEach
    public void setUp() {
        testUsername = "av" + (System.currentTimeMillis() % 100000000L);
        User user = new User();
        user.setUsername(testUsername);
        user.setNickname("头像测试");
        user.setPassword("x");
        user.setEmail(testUsername + "@example.invalid");
        user.setType(2);
        user.setUserPic(OLD_PIC);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        testUserId = user.getId();

        given(rustFsUtil.upload(any(), anyString(), anyString())).willReturn(true);
        given(rustFsUtil.copyTo(anyString(), anyString(), anyString())).willReturn(true);
        given(rustFsUtil.delete(anyString(), anyString())).willReturn(true);
    }

    @AfterEach
    public void tearDown() {
        if (testUserId == null) {
            return;
        }
        QueryWrapper<AvatarApply> aw = new QueryWrapper<>();
        aw.eq("user_id", testUserId);
        applyMapper.delete(aw);

        // 发给申请人本人的通知
        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("receiver_id", testUserId);
        notificationMapper.delete(nw);

        // 发给站长的待审通知（内容里带测试用户名，便于精确清理）
        QueryWrapper<Notification> masterNw = new QueryWrapper<>();
        masterNw.like("content", testUsername);
        notificationMapper.delete(masterNw);

        userMapper.deleteById(testUserId);
    }

    private MultipartFile png() {
        return new MockMultipartFile("userLogo", "avatar.png", "image/png", new byte[]{1, 2, 3});
    }

    @Test
    public void testSubmitThenApprove() {
        // 提交成功，记录处于待审，且对象确实传进了 avatar 桶
        assertTrue(avatarApplyService.submit(testUserId, png()), "首次提交应成功");
        AvatarApply mine = avatarApplyService.findMine(testUserId);
        assertNotNull(mine, "应能查到我提交的记录");
        assertEquals(AvatarApply.STATUS_PENDING, mine.getStatus());
        assertNotNull(mine.getPendingPic());
        verify(rustFsUtil).upload(any(), eq("avatar"), eq(mine.getPendingPic()));

        // 待审期间用户头像不变——这是「显示旧头像 + 待审标识」的前提
        assertEquals(OLD_PIC, userMapper.selectById(testUserId).getUserPic(),
                "待审期间 user_pic 不应改变");

        // 重复提交被拒
        assertFalse(avatarApplyService.submit(testUserId, png()), "有待审记录时不应允许重复提交");

        // 待审数量
        assertTrue(avatarApplyService.pendingCount() >= 1);

        // 提交后站长收到待审通知，且独立归入 avatar 类型（前端可按类型筛）
        QueryWrapper<Notification> masterNw = new QueryWrapper<>();
        masterNw.eq("type", Notification.TYPE_AUDIT).like("content", testUsername);
        assertTrue(notificationMapper.selectCount(masterNw) >= 1, "提交后站长应收到待审通知");

        // 通过：先把待审对象搬到 pic 桶，再换 user_pic，最后清理旧头像与 avatar 桶那份
        assertTrue(avatarApplyService.review(mine.getId(), true, null, testUserId));
        AvatarApply after = applyMapper.selectById(mine.getId());
        assertEquals(AvatarApply.STATUS_APPROVED, after.getStatus());
        assertNotNull(after.getReviewTime());
        verify(rustFsUtil).copyTo(mine.getPendingPic(), "avatar", "image");
        assertEquals(mine.getPendingPic(), userMapper.selectById(testUserId).getUserPic(),
                "通过后 user_pic 应指向待审对象");
        verify(rustFsUtil).delete(OLD_PIC, "image");
        verify(rustFsUtil).delete(mine.getPendingPic(), "avatar");

        // 通过是轻量告知，只发站内信（avatar-approved 配置为 inbox）
        QueryWrapper<Notification> approvedNw = new QueryWrapper<>();
        approvedNw.eq("receiver_id", testUserId).eq("title", "头像审核已通过");
        assertTrue(notificationMapper.selectCount(approvedNw) >= 1, "通过后申请人应收到站内信");

        // 已处理的记录不能重复审批
        assertFalse(avatarApplyService.review(mine.getId(), true, null, testUserId),
                "已处理的记录不应能重复审批");
    }

    @Test
    public void testApprovePromoteFailureRollsBackToPending() {
        // 对象搬不到 pic 桶就不该写成 approved——否则 user_pic 要么没换、要么指向 pic 桶里不存在的对象
        assertTrue(avatarApplyService.submit(testUserId, png()));
        AvatarApply mine = avatarApplyService.findMine(testUserId);
        given(rustFsUtil.copyTo(anyString(), anyString(), anyString())).willReturn(false);

        assertFalse(avatarApplyService.review(mine.getId(), true, null, testUserId),
                "转正失败时审批应返回失败");

        AvatarApply after = applyMapper.selectById(mine.getId());
        assertEquals(AvatarApply.STATUS_PENDING, after.getStatus(), "转正失败应退回待审，便于站长重试");
        assertNull(after.getReviewTime(), "回退后不应留下审核时间");
        assertEquals(OLD_PIC, userMapper.selectById(testUserId).getUserPic(),
                "转正失败时 user_pic 不应改变");
        verify(rustFsUtil, never()).delete(eq(OLD_PIC), eq("image"));

        // 审批没成功就不该告诉用户「已通过」
        QueryWrapper<Notification> approvedNw = new QueryWrapper<>();
        approvedNw.eq("receiver_id", testUserId).eq("title", "头像审核已通过");
        assertEquals(0L, notificationMapper.selectCount(approvedNw).longValue(),
                "转正失败时不应发出通过通知");
    }

    @Test
    public void testSubmitThenReject() {
        assertTrue(avatarApplyService.submit(testUserId, png()));
        AvatarApply mine = avatarApplyService.findMine(testUserId);
        String pendingPic = mine.getPendingPic();

        assertTrue(avatarApplyService.review(mine.getId(), false, "图片不清晰", testUserId));

        AvatarApply after = applyMapper.selectById(mine.getId());
        assertEquals(AvatarApply.STATUS_REJECTED, after.getStatus());
        assertEquals("图片不清晰", after.getRejectReason());

        // 拒绝不动 user_pic，只丢掉待审对象
        assertEquals(OLD_PIC, userMapper.selectById(testUserId).getUserPic(),
                "拒绝后 user_pic 不应改变");
        verify(rustFsUtil).delete(pendingPic, "avatar");
        verify(rustFsUtil, never()).delete(eq(OLD_PIC), eq("image"));

        // 拒绝理由要送达用户（avatar-rejected 配置为 both，测试里邮件渠道关闭）
        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("receiver_id", testUserId).eq("title", "头像审核未通过");
        List<Notification> rejected = notificationMapper.selectList(nw);
        assertEquals(1, rejected.size(), "拒绝应产生一条站内信");
        assertTrue(rejected.get(0).getContent().contains("图片不清晰"), "站内信应带上拒绝理由");
        assertEquals(Notification.TYPE_AUDIT, rejected.get(0).getType(), "应归入审核类型");

        // 被拒后可重新提交；再提交时旧记录不再占着待审位
        assertTrue(avatarApplyService.submit(testUserId, png()), "被拒后应允许重新提交");
    }

    @Test
    public void testSubmitWithoutExistingLogo() {
        // 用户本来没有头像：通过后没有旧对象要删
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", testUserId).set("user_pic", ""));

        assertTrue(avatarApplyService.submit(testUserId, png()));
        AvatarApply mine = avatarApplyService.findMine(testUserId);
        assertTrue(avatarApplyService.review(mine.getId(), true, null, testUserId));

        assertEquals(mine.getPendingPic(), userMapper.selectById(testUserId).getUserPic());
        verify(rustFsUtil, never()).delete(eq(""), eq("image"));
        verify(rustFsUtil, never()).delete(eq(OLD_PIC), eq("image"));
    }

    @Test
    public void testSubmitUploadFailureGivesNoRecord() {
        // 对象没传上去就不该留下待审记录，否则用户会卡在「待审」且没有可审的图
        given(rustFsUtil.upload(any(), anyString(), anyString())).willReturn(false);

        assertFalse(avatarApplyService.submit(testUserId, png()), "上传失败时提交应失败");
        assertNull(avatarApplyService.findMine(testUserId), "上传失败不应留下待审记录");
    }
}
