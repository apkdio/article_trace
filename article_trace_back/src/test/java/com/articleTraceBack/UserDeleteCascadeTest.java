package com.articleTraceBack;

import com.articleTraceBack.Service.UserService;
import com.articleTraceBack.mapper.AuthorApplyMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.AuthorApply;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注销用户时，其作者申请应被一并清理（否则会留下没有对应用户的悬挂记录）。
 *
 * <pre>mvn test -Dtest=UserDeleteCascadeTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class UserDeleteCascadeTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AuthorApplyMapper applyMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testDeleteUserRemovesAuthorApplies() {
        String username = "ud" + (System.currentTimeMillis() % 100000000L);
        User user = new User();
        user.setUsername(username);
        user.setNickname("注销测试");
        user.setPassword("x");
        user.setEmail(username + "@example.invalid");  // email 非空且唯一（uk_email）
        user.setType(2);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        Integer userId = user.getId();

        // 造两条申请（一条待审、一条已拒绝）
        for (int status : new int[]{AuthorApply.STATUS_PENDING, AuthorApply.STATUS_REJECTED}) {
            AuthorApply apply = new AuthorApply();
            apply.setUserId(userId);
            apply.setReason("注销级联测试");
            apply.setStatus(status);
            apply.setCreateTime(LocalDateTime.now());
            applyMapper.insert(apply);
        }

        QueryWrapper<AuthorApply> countWrapper = new QueryWrapper<>();
        countWrapper.eq("user_id", userId);
        assertEquals(2L, applyMapper.selectCount(countWrapper).longValue(), "前置：应有 2 条申请");

        assertTrue(userService.deleteUser(userId), "注销应成功");

        assertEquals(0L, applyMapper.selectCount(countWrapper).longValue(), "注销后其作者申请应一并删除");
    }

    /** 注销必须连带清掉 Redis 令牌，否则旧 Cookie 在 JWT 有效期内仍能通过双校验（bugs_report P0-6） */
    @Test
    public void testDeleteUserClearsRedisToken() {
        String username = "udt" + (System.currentTimeMillis() % 100000000L);
        User user = new User();
        user.setUsername(username);
        user.setNickname("注销清令牌测试");
        user.setPassword("x");
        user.setEmail(username + "@example.invalid");
        user.setType(2);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);

        stringRedisTemplate.opsForValue().set(username, "token-to-be-cleared");

        assertTrue(userService.deleteUser(user.getId()), "注销应成功");
        assertNull(stringRedisTemplate.opsForValue().get(username), "注销后 Redis 令牌应被清除");
    }
}
