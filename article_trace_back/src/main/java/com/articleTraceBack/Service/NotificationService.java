package com.articleTraceBack.Service;

import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.PageBean;

/**
 * 站内通知与邮件投递基础设施。
 *
 * <p>与具体业务解耦：业务只负责发起调用，投递渠道按场景配置解析；
 * 投递成败由本服务内部处理（记日志、必要时代发/重试），不向调用方抛异常。</p>
 */
public interface NotificationService {

    /**
     * 给单个用户发通知（发送方固定为系统，sender_id = -1）。
     *
     * @param scene 场景标识，用于解析投递渠道（见 notification.scenes）
     */
    void notify(int receiverId, String scene, String title, String content);

    /**
     * 发给某角色的全部用户（每人一条站内信）。
     *
     * @param roleType 0 站长 / 1 作者 / 2 读者
     */
    void notifyRole(int roleType, String scene, String title, String content);

    /** 我的站内信分页（按发送时间倒序） */
    PageBean<Notification> listByReceiver(int receiverId, int pageNum, int pageSize);

    /** 我的未读数 */
    int unreadCount(int receiverId);

    /** 标记单条已读（仅限本人的消息） */
    boolean markRead(int receiverId, int id);

    /** 全部标记已读，返回受影响行数 */
    int markAllRead(int receiverId);

    /** 清理超过 keepDays 天的站内信（无论是否已读），返回删除行数 */
    int cleanupExpired(int keepDays);
}
