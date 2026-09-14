package com.articleTraceBack;

import com.articleTraceBack.Service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 账号被并发删除 / 不存在时，密码校验应返回 false 而不是抛异常。
 *
 * <pre>mvn test -Dtest UserCheckPassTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class UserCheckPassTest {

    @Autowired
    private UserService userService;

    @Test
    public void testCheckPassOnMissingUserReturnsFalse() {
        String missing = "no-such-user-" + System.currentTimeMillis();
        assertFalse(userService.checkPass("whatever", missing, "password"),
                "用户不存在时应返回 false");
    }
}
