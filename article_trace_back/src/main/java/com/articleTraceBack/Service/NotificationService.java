package com.articleTraceBack.Service;

import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.PageBean;

import java.util.Map;

/** 站内通知与邮件投递基础设施：业务只发起调用，渠道按场景配置解析，投递成败内部处理，不向调用方抛异常。 */
public interface NotificationService {

    /**
     * 给单个用户发通知（发送方固定为系统，sender_id = -1）。
     *
     * @param scene 场景标识，用于解析投递渠道（见 notification.scenes）
     */
    default void notify(int receiverId, String scene, String title, String content) {
        notify(receiverId, scene, title, content, null, null);
    }

    /**
    * 给单个用户发通知，邮件渠道可指定模板并按「HTML + 纯文本」双载体投递；不指定模板时为纯文本单载体。
    * @param mailTemplate 模板名（不含扩展名，位于 {@code templates/email/}）；为 null 走纯文本
    * @param templateVars 业务模板变量。**传原始值即可**——HTML 载体会由本服务转义，
    * 纯文本载体按原样输出（否则 {@code &amp;} 之类会在纯文本客户端里显示成字面量）。
    * {@code year} 与 {@code logoUrl} 由本服务统一补齐，不必传。
    */
    void notify(int receiverId, String scene, String title, String content,
                String mailTemplate, Map<String, String> templateVars);

    /**
     * 发给某角色的全部用户（每人一条站内信）。
     *
     * @param roleType 0 站长 / 1 作者 / 2 读者
     */
    void notifyRole(int roleType, String scene, String title, String content);

    /**
     * 我的站内信分页（按发送时间倒序）。
     *
     * @param type 类型筛选，见 {@link Notification#TYPE_SYSTEM} / {@link Notification#TYPE_AUDIT}
     *             / {@link Notification#TYPE_REPORT}；null 查全部
     */
    PageBean<Notification> listByReceiver(int receiverId, String type, int pageNum, int pageSize);

    /** 我的未读数 */
    int unreadCount(int receiverId);

    /** 标记单条已读（仅限本人的消息） */
    boolean markRead(int receiverId, int id);

    /** 全部标记已读，返回受影响行数 */
    int markAllRead(int receiverId);

    /** 删除单条站内信（仅限本人的消息）；返回是否删掉了 1 条 */
    boolean delete(int receiverId, int id);

    /** 清理超过 keepDays 天的站内信（无论是否已读），返回删除行数 */
    int cleanupExpired(int keepDays);
}
