package com.articleTraceBack;

import com.articleTraceBack.Controller.AuthorApplyController;
import com.articleTraceBack.Controller.ReaderController;
import com.articleTraceBack.Controller.UserController;
import com.articleTraceBack.pojo.Comment;
import com.articleTraceBack.pojo.RegisterUserPojo;
import com.articleTraceBack.pojo.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单用户态的门禁：开关关掉时接口必须直接拒（个人备案要求）。
 *
 * 直接调 Controller 方法而不是走 HTTP —— 门禁就在方法最前面，这样断言的是门禁本身，
 * 顺带绕开 {@code @Validated}（不经 Spring MVC 不会触发参数校验，所以空 body 也能走到判断）。
 * 发码接口「注册场景」那条门禁排在图形验证码之后，没有有效验证码到不了那一步，因此这里不覆盖。
 *
 * <pre>mvn test -Dtest=SiteGatingTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "site.register-enabled=false",
                "site.comment-enabled=false",
                "site.author-apply-enabled=false",
                "notification.mail.enabled=false"
        })
public class SiteGatingTest {

    @Autowired
    private UserController userController;

    @Autowired
    private ReaderController readerController;

    @Autowired
    private AuthorApplyController authorApplyController;

    @Test
    public void registerIsRejected() {
        Result<String> result = userController.register(new RegisterUserPojo(), null);
        assertEquals(1, result.getCode(), "注册关闭时必须拒");
        assertTrue(String.valueOf(result.getMessage()).contains("暂不开放注册"), "提示要说明注册已关");
    }

    @Test
    public void commentIsRejected() {
        Result<String> result = readerController.addComment(new Comment());
        assertEquals(1, result.getCode(), "评论关闭时必须拒");
        assertTrue(String.valueOf(result.getMessage()).contains("暂未开放"), "提示要说明评论已关");
    }

    @Test
    public void authorApplyIsRejected() {
        Result<String> result = authorApplyController.submit(new HashMap<>());
        assertEquals(1, result.getCode(), "作者申请关闭时必须拒");
        assertTrue(String.valueOf(result.getMessage()).contains("暂不开放作者申请"), "提示要说明申请已关");
    }
}
