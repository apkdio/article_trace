package com.articleTraceBack;

import com.articleTraceBack.Service.ReaderService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 邮件链路验证测试：真实调用 SMTP 发一封测试邮件。
 *
 * <p><b>默认跳过</b>，避免常规构建误发邮件；需显式指定收件人才会执行：</p>
 * <pre>mvn test -Dtest=EmailUtilTest -Dmail.to=your@mail.com</pre>
 *
 * <p>发送失败时会打印异常堆栈，便于排查 SMTP 配置（授权码、端口、SSL 等）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class EmailUtilTest {

    @Autowired
    private ReaderService readerService;

    @Test
    public void testSendMail() {
        String to = System.getProperty("mail.to");
        Assumptions.assumeTrue(to != null && !to.isBlank(),
                "未指定 -Dmail.to，跳过发信测试");

        boolean ok = readerService.sendTestMail(to);
        assertTrue(ok, "邮件发送失败：请检查 SMTP 配置与服务端日志");
    }
}
