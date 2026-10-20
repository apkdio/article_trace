package com.articleTraceBack;

import com.articleTraceBack.Service.UserService;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 邮箱落库与查询必须同口径：注册、改资料都先 trim + 转小写，按邮箱查人时同样归一化。
 *
 * <pre>mvn test -Dtest=UserEmailNormalizeTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class UserEmailNormalizeTest {

    private static final int ROLE_READER = 2;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Test
    public void registerStoresLowercaseEmail() {
        String tag = String.valueOf(System.nanoTime());
        User u = new User();
        u.setUsername("eml" + tag.substring(tag.length() - 10));
        u.setPassword("pass123456");
        u.setType(ROLE_READER);
        u.setEmail("  MiXeD." + tag + "@Example.INVALID  ");
        assertTrue(userService.userRegister(u), "注册应当成功");
        try {
            assertEquals("mixed." + tag + "@example.invalid",
                    userMapper.selectById(u.getId()).getEmail(), "注册落库前要 trim + 小写");
            assertNotNull(userService.findUserByEmail("MIXED." + tag + "@EXAMPLE.invalid"),
                    "按邮箱查人也要同口径");
        } finally {
            userMapper.deleteById(u.getId());
        }
    }

    @Test
    public void profileUpdateStoresLowercaseEmail() {
        String tag = String.valueOf(System.nanoTime());
        User u = new User();
        u.setUsername("eml" + tag.substring(tag.length() - 10));
        u.setPassword("pass123456");
        u.setType(ROLE_READER);
        u.setEmail("lower." + tag + "@example.invalid");
        assertTrue(userService.userRegister(u), "注册应当成功");
        try {
            User edit = new User();
            edit.setUsername(u.getUsername());
            edit.setEmail("  UPPER." + tag + "@Example.INVALID  ");
            assertTrue(userService.update(edit, 0), "改资料应当成功");
            assertEquals("upper." + tag + "@example.invalid",
                    userMapper.selectById(u.getId()).getEmail(), "改资料落库同样要归一化");
        } finally {
            userMapper.deleteById(u.getId());
        }
    }
}
