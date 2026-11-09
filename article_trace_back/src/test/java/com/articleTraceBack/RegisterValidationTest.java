package com.articleTraceBack;

import com.articleTraceBack.pojo.RegisterUserPojo;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册口径：用户名 1-15、密码 1-72，都不允许空白（与登录 / 改密的 {@code \S} 规则对齐）。
 *
 * <pre>mvn test -Dtest=RegisterValidationTest</pre>
 */
public class RegisterValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private RegisterUserPojo pojo(String username, String password) {
        RegisterUserPojo p = new RegisterUserPojo();
        p.setUsername(username);
        p.setPassword(password);
        p.setConfirmPassword(password);
        p.setEmail("someone@example.com");
        p.setEmailCode("123456");
        return p;
    }

    private Set<String> invalidFields(RegisterUserPojo p) {
        return VALIDATOR.validate(p).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    public void validRequestPasses() {
        assertTrue(invalidFields(pojo("zhangsan", "pass1234")).isEmpty());
        assertTrue(invalidFields(pojo("a".repeat(15), "a".repeat(72))).isEmpty());
    }

    @Test
    public void usernameWithSpaceIsRejected() {
        assertTrue(invalidFields(pojo("zhang san", "pass1234")).contains("username"));
    }

    @Test
    public void usernameLongerThan15IsRejected() {
        assertTrue(invalidFields(pojo("a".repeat(16), "pass1234")).contains("username"));
    }

    @Test
    public void usernameThatIsOnlySpaceIsRejected() {
        assertTrue(invalidFields(pojo(" ", "pass1234")).contains("username"));
    }

    @Test
    public void passwordWithSpaceIsRejected() {
        assertTrue(invalidFields(pojo("zhangsan", "pass 1234")).contains("password"));
    }

    @Test
    public void passwordLongerThan72IsRejected() {
        assertTrue(invalidFields(pojo("zhangsan", "a".repeat(73))).contains("password"));
    }
}
