package com.articleTraceBack.Service;

import com.articleTraceBack.config.NotificationProperties;
import com.articleTraceBack.mapper.NotificationMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 通知基础设施实现（P1：站内信）。
 *
 * <p><b>调用方不感知成败</b>：全部 try-catch，失败只记日志，绝不影响主业务。</p>
 * <p>邮件渠道在 P2 实现（写 notification_mail + 异步发送 + 失败重试）。</p>
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

    public NotificationServiceImpl(NotificationMapper notificationMapper,
                                   UserMapper userMapper,
                                   NotificationProperties properties,
                                   MailService mailService) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
        this.properties = properties;
        this.mailService = mailService;
    }

    @Override
    public void notify(int receiverId, String scene, String title, String content) {
        try {
            String channel = resolveChannel(scene);
            if (supportsInbox(channel)) {
                Notification notification = new Notification();
                notification.setTitle(title);
                notification.setSenderId(SYSTEM_SENDER_ID);
                notification.setReceiverId(receiverId);
                notification.setContent(content);
                notification.setCreateTime(LocalDateTime.now());
                notification.setIsRead(UNREAD);
                notificationMapper.insert(notification);
            }
            if (supportsMail(channel)) {
                String toEmail = findUserEmail(receiverId);
                if (toEmail == null || toEmail.isBlank()) {
                    log.warn("skip mail channel: receiver has no email, receiverId={}, scene={}", receiverId, scene);
                } else {
                    mailService.send(toEmail, title, content);
                }
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
    public PageBean<Notification> listByReceiver(int receiverId, int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 1);
        int total = notificationMapper.countByReceiver(receiverId);
        int offset = (safePageNum - 1) * safePageSize;
        List<Notification> items = notificationMapper.selectPageByReceiver(receiverId, offset, safePageSize);
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

    /** 取接收方邮箱；用户不存在或未填邮箱时返回 null */
    private String findUserEmail(int receiverId) {
        User user = userMapper.selectById(receiverId);
        return user == null ? null : user.getEmail();
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
