package com.articleTraceBack;

import com.articleTraceBack.Service.ProfileApplyService;
import com.articleTraceBack.Service.UserService;
import com.articleTraceBack.constant.RedisKeys;
import com.articleTraceBack.mapper.ProfileApplyMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.ProfileApply;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 昵称审核：通过写回昵称并起锁定期、拒绝只记理由且不动用户、重复处理被 CAS 挡住。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class ProfileApplyReviewTest {

    @Autowired
    private UserService userService;

    @Autowired
    private ProfileApplyService profileApplyService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProfileApplyMapper profileApplyMapper;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    @Qualifier("stringRedisTemplate")
    private StringRedisTemplate stringRedisTemplate;

    private User editOf(int userId) {
        User current = userMapper.selectById(userId);
        User edit = new User();
        edit.setId(userId);
        edit.setUsername(current.getUsername());
        edit.setEmail(current.getEmail());
        return edit;
    }

    /** 造一条待审：把昵称改成命中内容规则的值，返回那条待审记录 */
    private ProfileApply submitPending(int userId, String nickname) {
        stringRedisTemplate.delete(RedisKeys.PROFILE_NICKNAME_LOCK + userId);
        User edit = editOf(userId);
        edit.setNickname(nickname);
        assertEquals(UserService.ProfileUpdateResult.Kind.PENDING, userService.updateNickname(edit).kind());
        ProfileApply pending = profileApplyMapper.selectOne(new QueryWrapper<ProfileApply>()
                .eq("user_id", userId)
                .eq("type", ProfileApply.TYPE_NICKNAME)
                .eq("status", ProfileApply.STATUS_PENDING));
        assertNotNull(pending, "应当落一条待审记录");
        return pending;
    }

    @Test
    public void approveWritesNicknameAndStartsLock() {
        int userId = fixtures.ensureUser(2);
        ProfileApply pending = submitPending(userId, "微信abc123");

        assertTrue(profileApplyService.review(pending.getId(), true, null, 1));
        assertEquals("微信abc123", userMapper.selectById(userId).getNickname(), "通过后应当写回待审值");
        assertEquals(ProfileApply.STATUS_APPROVED, profileApplyMapper.selectById(pending.getId()).getStatus());

        Long ttl = stringRedisTemplate.getExpire(RedisKeys.PROFILE_NICKNAME_LOCK + userId, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 6 * 24 * 3600 && ttl <= 7 * 24 * 3600, "通过后应当起 7 天锁定期，实际 TTL=" + ttl);

        assertFalse(profileApplyService.review(pending.getId(), true, null, 1), "同一条不能处理两次");
    }

    @Test
    public void rejectKeepsOldNicknameAndRecordsReason() {
        int userId = fixtures.ensureUser(2);
        String before = userMapper.selectById(userId).getNickname();
        ProfileApply pending = submitPending(userId, "看我主页 www.spam.cn");

        assertTrue(profileApplyService.review(pending.getId(), false, "昵称里不要放链接", 1));
        ProfileApply after = profileApplyMapper.selectById(pending.getId());
        assertEquals(ProfileApply.STATUS_REJECTED, after.getStatus());
        assertEquals("昵称里不要放链接", after.getRejectReason());
        assertEquals(before, userMapper.selectById(userId).getNickname(), "拒绝时用户表不能动");
        assertFalse(stringRedisTemplate.hasKey(RedisKeys.PROFILE_NICKNAME_LOCK + userId),
                "拒绝不该起锁定期");
    }

    @Test
    public void pendingCountAndListSeeTheRow() {
        int userId = fixtures.ensureUser(2);
        ProfileApply pending = submitPending(userId, "qq123456");

        assertTrue(profileApplyService.pendingCount() >= 1, "至少能看到刚提交的这条");
        assertTrue(profileApplyService.list(ProfileApply.STATUS_PENDING, 1, 50).getItems().stream()
                        .anyMatch(item -> item.getId().equals(pending.getId())),
                "待审列表应当包含这条");
    }
}
