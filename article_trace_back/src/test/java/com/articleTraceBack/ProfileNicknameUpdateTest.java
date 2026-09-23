package com.articleTraceBack;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 个人资料修改链路：昵称与个签的规则判定、7 天锁定期、内容命中落待审、读者不开放个签。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class ProfileNicknameUpdateTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProfileApplyMapper profileApplyMapper;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    @Qualifier("stringRedisTemplate")
    private StringRedisTemplate stringRedisTemplate;

    /** 构造一次「只改昵称」的提交：其余字段照抄当前值，避免顺带改邮箱撞唯一索引 */
    private User editOf(int userId) {
        User current = userMapper.selectById(userId);
        User edit = new User();
        edit.setId(userId);
        edit.setUsername(current.getUsername());
        edit.setEmail(current.getEmail());
        edit.setNickname(current.getNickname());
        edit.setSignature(current.getSignature());
        return edit;
    }

    @Test
    public void normalRenameTakesEffectAndLocksForSevenDays() {
        int userId = fixtures.ensureUser(2);
        stringRedisTemplate.delete(RedisKeys.PROFILE_NICKNAME_LOCK + userId);

        User edit = editOf(userId);
        edit.setNickname("zz新名字");
        assertEquals(UserService.ProfileUpdateResult.Kind.UPDATED, userService.updateProfile(edit).kind());
        assertEquals("zz新名字", userMapper.selectById(userId).getNickname(), "正常昵称应当立即生效");

        Long ttl = stringRedisTemplate.getExpire(RedisKeys.PROFILE_NICKNAME_LOCK + userId, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 6 * 24 * 3600 && ttl <= 7 * 24 * 3600, "锁定期应为 7 天，实际 TTL=" + ttl);

        User again = editOf(userId);
        again.setNickname("zz另一个名字");
        assertEquals(UserService.ProfileUpdateResult.Kind.LOCKED, userService.updateProfile(again).kind(),
                "7 天内再改应当被锁定期挡住");
        assertEquals("zz新名字", userMapper.selectById(userId).getNickname(), "被挡住时库里不能变");
    }

    @Test
    public void contentHitGoesToPendingAndKeepsOldName() {
        int userId = fixtures.ensureUser(2);
        stringRedisTemplate.delete(RedisKeys.PROFILE_NICKNAME_LOCK + userId);
        String before = userMapper.selectById(userId).getNickname();

        User edit = editOf(userId);
        edit.setNickname("微信abc123");
        assertEquals(UserService.ProfileUpdateResult.Kind.PENDING, userService.updateProfile(edit).kind());

        assertEquals(before, userMapper.selectById(userId).getNickname(), "待审期间必须保留旧值");
        ProfileApply pending = profileApplyMapper.selectOne(new QueryWrapper<ProfileApply>()
                .eq("user_id", userId)
                .eq("type", ProfileApply.TYPE_NICKNAME)
                .eq("status", ProfileApply.STATUS_PENDING));
        assertNotNull(pending, "应当落一条待审记录");
        assertEquals("微信abc123", pending.getPendingValue());
    }

    @Test
    public void formatProblemIsRejectedWithoutQueueing() {
        int userId = fixtures.ensureUser(2);
        String before = userMapper.selectById(userId).getNickname();

        User edit = editOf(userId);
        edit.setNickname("a");
        assertEquals(UserService.ProfileUpdateResult.Kind.REJECTED, userService.updateProfile(edit).kind());
        assertEquals(before, userMapper.selectById(userId).getNickname(), "被拒时库里不能变");
        assertEquals(0, profileApplyMapper.selectCount(new QueryWrapper<ProfileApply>()
                .eq("user_id", userId)), "格式类不该留待审记录");
    }

    @Test
    public void signatureFollowsTheSameRulesAndLock() {
        int userId = fixtures.ensureUser(1);
        stringRedisTemplate.delete(RedisKeys.PROFILE_NICKNAME_LOCK + userId);

        User edit = editOf(userId);
        edit.setSignature("爱写代码的人");
        assertEquals(UserService.ProfileUpdateResult.Kind.UPDATED, userService.updateProfile(edit).kind());
        assertEquals("爱写代码的人", userMapper.selectById(userId).getSignature(), "正常个签应当立即生效");
        Long ttl = stringRedisTemplate.getExpire(RedisKeys.PROFILE_NICKNAME_LOCK + userId, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0, "改个签同样起锁定期（与昵称共用一个）");

        stringRedisTemplate.delete(RedisKeys.PROFILE_NICKNAME_LOCK + userId);
        User second = editOf(userId);
        second.setSignature("微信abc123");
        assertEquals(UserService.ProfileUpdateResult.Kind.PENDING, userService.updateProfile(second).kind());
        assertEquals("爱写代码的人", userMapper.selectById(userId).getSignature(), "命中时保留旧个签");
        ProfileApply pending = profileApplyMapper.selectOne(new QueryWrapper<ProfileApply>()
                .eq("user_id", userId)
                .eq("type", ProfileApply.TYPE_SIGNATURE)
                .eq("status", ProfileApply.STATUS_PENDING));
        assertNotNull(pending, "应当按个签类型落待审");
    }

    @Test
    public void readerSignatureIsIgnored() {
        int userId = fixtures.ensureUser(2);
        stringRedisTemplate.delete(RedisKeys.PROFILE_NICKNAME_LOCK + userId);

        User edit = editOf(userId);
        edit.setSignature("读者不该有个签");
        assertEquals(UserService.ProfileUpdateResult.Kind.UPDATED, userService.updateProfile(edit).kind(),
                "读者只改个签时视为「什么都没改」，走原路径成功");
        assertNull(userMapper.selectById(userId).getSignature(), "读者的个签不该写库");
    }
}
