package com.articleTraceBack.Utils;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 邮件发送工具（Spring JavaMailSender / SMTP）：验证码与站内信邮件均经此发送。
 * 发送失败只记日志并返回 {@code false}，不影响调用方主流程。
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

    /**
    * 发送同时含纯文本与 HTML 两载体的邮件（{@code multipart/alternative}），由客户端择一显示。
    * @param text 纯文本载体（兜底）
    * @param html HTML 载体
    * @return 是否发送成功
    */
    public boolean sendMultipart(String to, String subject, String text, String html) {
        if (isBlank(to)) {
            log.warn("sendMultipart skipped: empty receiver");
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(resolveFrom());
            helper.setTo(to);
            helper.setSubject(withPrefix(subject));
            // 两参版本由 Spring 自动组装 multipart/alternative，纯文本部分在前
            helper.setText(text, html);
            mailSender.send(message);
            log.info("multipart mail sent: to={}, subject={}", to, subject);
            return true;
        } catch (Exception e) {
            log.error("send multipart mail failed: to={}, subject={}", to, subject, e);
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
