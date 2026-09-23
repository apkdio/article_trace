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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 昵称修改链路：正常改名立即生效并起 7 天锁定期；命中内容规则落待审且保留旧值；格式类直接拒、不进队列。
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
        return edit;
    }

    @Test
    public void normalRenameTakesEffectAndLocksForSevenDays() {
        int userId = fixtures.ensureUser(2);
        stringRedisTemplate.delete(RedisKeys.PROFILE_NICKNAME_LOCK + userId);

        User edit = editOf(userId);
        edit.setNickname("zz新名字");
        assertEquals(UserService.ProfileUpdateResult.Kind.UPDATED, userService.updateNickname(edit).kind());
        assertEquals("zz新名字", userMapper.selectById(userId).getNickname(), "正常昵称应当立即生效");

        Long ttl = stringRedisTemplate.getExpire(RedisKeys.PROFILE_NICKNAME_LOCK + userId, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 6 * 24 * 3600 && ttl <= 7 * 24 * 3600, "锁定期应为 7 天，实际 TTL=" + ttl);

        User again = editOf(userId);
        again.setNickname("zz另一个名字");
        assertEquals(UserService.ProfileUpdateResult.Kind.LOCKED, userService.updateNickname(again).kind(),
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
        assertEquals(UserService.ProfileUpdateResult.Kind.PENDING, userService.updateNickname(edit).kind());

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
        assertEquals(UserService.ProfileUpdateResult.Kind.REJECTED, userService.updateNickname(edit).kind());
        assertEquals(before, userMapper.selectById(userId).getNickname(), "被拒时库里不能变");
        assertEquals(0, profileApplyMapper.selectCount(new QueryWrapper<ProfileApply>()
                .eq("user_id", userId)), "格式类不该留待审记录");
    }
}
