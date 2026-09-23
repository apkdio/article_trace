package com.articleTraceBack.Service;

import com.articleTraceBack.config.NotificationProperties;
import com.articleTraceBack.Utils.EmailTemplateUtil;
import com.articleTraceBack.mapper.NotificationMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通知基础设施实现（站内信 + 邮件）：全部 try-catch，失败只记日志，不影响主业务。
 * 邮件经 {@code MailService} 落库 + 异步发送 + 失败重试。
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    /** 系统消息发送方 */
    private static final int SYSTEM_SENDER_ID = -1;

    private static final int UNREAD = 0;
    private static final int READ = 1;

    private static final String CHANNEL_INBOX = "inbox";
    private static final String CHANNEL_MAIL = "mail";
    private static final String CHANNEL_BOTH = "both";

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final NotificationProperties properties;
    private final MailService mailService;
    private final EmailTemplateUtil emailTemplateUtil;

    /** 邮件模板里的 logo 地址；留空则 logo 位置为空 */
    @Value("${email.logoUrl:}")
    private String logoUrl;

    public NotificationServiceImpl(NotificationMapper notificationMapper,
                                   UserMapper userMapper,
                                   NotificationProperties properties,
                                   MailService mailService,
                                   EmailTemplateUtil emailTemplateUtil) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
        this.properties = properties;
        this.mailService = mailService;
        this.emailTemplateUtil = emailTemplateUtil;
    }

    @Override
    public void notify(int receiverId, String scene, String title, String content,
                       String mailTemplate, Map<String, String> templateVars) {
        try {
            String channel = resolveChannel(scene);
            if (supportsInbox(channel)) {
                Notification notification = new Notification();
                notification.setTitle(title);
                notification.setType(resolveType(scene));
                notification.setSenderId(SYSTEM_SENDER_ID);
                notification.setReceiverId(receiverId);
                notification.setContent(content);
                notification.setCreateTime(LocalDateTime.now());
                notification.setIsRead(UNREAD);
                notificationMapper.insert(notification);
            }
            if (supportsMail(channel)) {
                sendMail(receiverId, title, content, mailTemplate, templateVars);
            }
        } catch (Exception e) {
            log.error("notify failed: receiverId={}, scene={}", receiverId, scene, e);
        }
    }

    @Override
    public void notifyRole(int roleType, String scene, String title, String content) {
        try {
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("type", roleType);
            List<User> users = userMapper.selectList(wrapper);
            for (User user : users) {
                notify(user.getId(), scene, title, content);
            }
        } catch (Exception e) {
            log.error("notifyRole failed: roleType={}, scene={}", roleType, scene, e);
        }
    }

    @Override
    public PageBean<Notification> listByReceiver(int receiverId, String type, int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 1);
        String safeType = normalizeType(type);
        int total = notificationMapper.countByReceiver(receiverId, safeType);
        int offset = (safePageNum - 1) * safePageSize;
        List<Notification> items = notificationMapper.selectPageByReceiver(
                receiverId, safeType, offset, safePageSize);
        return new PageBean<>(total, items);
    }

    @Override
    public int unreadCount(int receiverId) {
        QueryWrapper<Notification> wrapper = new QueryWrapper<>();
        wrapper.eq("receiver_id", receiverId).eq("is_read", UNREAD);
        return Math.toIntExact(notificationMapper.selectCount(wrapper));
    }

    @Override
    public boolean markRead(int receiverId, int id) {
        UpdateWrapper<Notification> wrapper = new UpdateWrapper<>();
        // 只更新未读的记录：既避免无谓写库，也保证重复调用返回 false（幂等）
        wrapper.eq("id", id).eq("receiver_id", receiverId).eq("is_read", UNREAD).set("is_read", READ);
        return notificationMapper.update(null, wrapper) > 0;
    }

    @Override
    public int markAllRead(int receiverId) {
        UpdateWrapper<Notification> wrapper = new UpdateWrapper<>();
        wrapper.eq("receiver_id", receiverId).eq("is_read", UNREAD).set("is_read", READ);
        return notificationMapper.update(null, wrapper);
    }

    @Override
    public boolean delete(int receiverId, int id) {
        QueryWrapper<Notification> wrapper = new QueryWrapper<>();
        // 限定 receiver_id：只能删自己的消息
        wrapper.eq("id", id).eq("receiver_id", receiverId);
        return notificationMapper.delete(wrapper) > 0;
    }

    @Override
    public int cleanupExpired(int keepDays) {
        if (keepDays <= 0) {
            return 0;
        }
        try {
            QueryWrapper<Notification> wrapper = new QueryWrapper<>();
            wrapper.lt("create_time", LocalDateTime.now().minusDays(keepDays));
            int deleted = notificationMapper.delete(wrapper);
            log.info("notification cleanup: {} record(s) older than {} day(s) removed", deleted, keepDays);
            return deleted;
        } catch (Exception e) {
            log.error("notification cleanup failed", e);
            return 0;
        }
    }

    /** 场景 → 站内信类型：头像、作者申请、资料均属审核类，举报单独一类，其余归系统 */
    private String resolveType(String scene) {
        if (scene == null) {
            return Notification.TYPE_SYSTEM;
        }
        if (scene.startsWith("author-apply") || scene.startsWith("avatar") || scene.startsWith("profile")) {
            return Notification.TYPE_AUDIT;
        }
        if (scene.startsWith("report")) {
            return Notification.TYPE_REPORT;
        }
        return Notification.TYPE_SYSTEM;
    }

    /** 把前端的类型筛选参数归一化：非法/空/全部 一律返回 null（查全部） */
    private String normalizeType(String type) {
        if (type == null || type.isBlank() || "all".equalsIgnoreCase(type)) {
            return null;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        // apply / avatar 是合并前的旧类型，一并归到 audit，旧记录仍能按「审核」筛出来
        if ("apply".equals(normalized) || "avatar".equals(normalized)) {
            return Notification.TYPE_AUDIT;
        }
        if (Notification.TYPE_SYSTEM.equals(normalized)
                || Notification.TYPE_AUDIT.equals(normalized)
                || Notification.TYPE_REPORT.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    /** 取接收方邮箱；用户不存在或未填邮箱时返回 null */
    private String findUserEmail(int receiverId) {
        User user = userMapper.selectById(receiverId);
        return user == null ? null : user.getEmail();
    }

    /** 投递邮件渠道：指定模板则渲染双载体，否则发纯文本；两个载体用不同变量表（HTML 转义、纯文本不转义）。 */
    private void sendMail(int receiverId, String title, String content,
                          String mailTemplate, Map<String, String> templateVars) {
        String toEmail = findUserEmail(receiverId);
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("skip mail channel: receiver has no email, receiverId={}", receiverId);
            return;
        }
        if (mailTemplate == null || mailTemplate.isBlank()) {
            mailService.send(toEmail, title, content);
            return;
        }
        Map<String, String> textVars = buildTemplateVars(templateVars);
        String text = emailTemplateUtil.render(mailTemplate, "txt", textVars);
        String html = emailTemplateUtil.render(mailTemplate, "html", escapeHtml(textVars));
        if (text == null || html == null) {
            // 模板缺失属于部署问题：退回纯文本，宁可不美化也不发一封空邮件
            log.error("mail template missing, fallback to plain text: template={}, text={}, html={}",
                    mailTemplate, text != null, html != null);
            mailService.send(toEmail, title, content);
            return;
        }
        mailService.send(toEmail, title, text, html);
    }

    /** 补上模板通用变量（年份、logo 地址），再叠加业务变量 */
    private Map<String, String> buildTemplateVars(Map<String, String> businessVars) {
        Map<String, String> vars = new HashMap<>();
        vars.put("year", String.valueOf(Year.now().getValue()));
        vars.put("logoUrl", logoUrl == null ? "" : logoUrl.trim());
        if (businessVars != null) {
            vars.putAll(businessVars);
        }
        return vars;
    }

    /** 逐个变量做 HTML 转义，供 HTML 载体使用 */
    private Map<String, String> escapeHtml(Map<String, String> vars) {
        Map<String, String> escaped = new HashMap<>(vars.size());
        vars.forEach((key, value) -> escaped.put(key, HtmlUtils.htmlEscape(value)));
        return escaped;
    }

    /** 解析场景对应的投递渠道；未配置时取 defaultChannel */
    private String resolveChannel(String scene) {
        String channel = (scene == null) ? null : properties.getScenes().get(scene);
        if (channel == null || channel.isBlank()) {
            channel = properties.getDefaultChannel();
        }
        return channel == null ? CHANNEL_INBOX : channel.toLowerCase(Locale.ROOT);
    }

    private boolean supportsInbox(String channel) {
        return CHANNEL_INBOX.equals(channel) || CHANNEL_BOTH.equals(channel);
    }

    private boolean supportsMail(String channel) {
        return properties.getMail().isEnabled()
                && (CHANNEL_MAIL.equals(channel) || CHANNEL_BOTH.equals(channel));
    }
}
