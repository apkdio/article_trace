package com.articleTraceBack.Utils;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 邮件发送工具（基于 Spring JavaMailSender / SMTP）。
 *
 * <p>目前只提供发信能力，尚未接入注册、找回密码等主链路，作为后续邮箱验证码的基础设施。</p>
 *
 * <p>发送失败只记录日志并返回 {@code false}，不向上抛异常，避免影响调用方主流程。</p>
 */
@Slf4j
@Component
public class EmailUtil {

    private final JavaMailSender mailSender;

    /** 发件人；留空则回退到 SMTP 登录账号 */
    @Value("${email.from:}")
    private String from;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    /** 主题前缀，如 "[文迹]" */
    @Value("${email.subjectPrefix:}")
    private String subjectPrefix;

    public EmailUtil(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 发送纯文本邮件。
     *
     * @param to      收件人
     * @param subject 主题（自动加前缀）
     * @param content 正文
     * @return 是否发送成功
     */
    public boolean sendText(String to, String subject, String content) {
        if (isBlank(to)) {
            log.warn("sendText skipped: empty receiver");
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(resolveFrom());
            message.setTo(to);
            message.setSubject(withPrefix(subject));
            message.setText(content);
            mailSender.send(message);
            log.info("mail sent: to={}, subject={}", to, subject);
            return true;
        } catch (Exception e) {
            log.error("send mail failed: to={}, subject={}", to, subject, e);
            return false;
        }
    }

    /**
     * 发送 HTML 邮件（验证码等富文本场景）。
     *
     * @return 是否发送成功
     */
    public boolean sendHtml(String to, String subject, String html) {
        if (isBlank(to)) {
            log.warn("sendHtml skipped: empty receiver");
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(resolveFrom());
            helper.setTo(to);
            helper.setSubject(withPrefix(subject));
            helper.setText(html, true);
            mailSender.send(message);
            log.info("html mail sent: to={}, subject={}", to, subject);
            return true;
        } catch (Exception e) {
            log.error("send html mail failed: to={}, subject={}", to, subject, e);
            return false;
        }
    }

    private String resolveFrom() {
        return isBlank(from) ? mailUsername : from;
    }

    private String withPrefix(String subject) {
        String text = subject == null ? "" : subject;
        return isBlank(subjectPrefix) ? text : subjectPrefix + text;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
