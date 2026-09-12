package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.NotificationService;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站内信接口。
 *
 * <p>接收方一律取登录态，不接受前端传 userId，避免越权读取他人消息。</p>
 */
@RestController
@RequestMapping("/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 我的站内信分页（倒序） */
    @GetMapping("/list")
    public Result<PageBean<Notification>> list(@RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "10") int pageSize) {
        Integer userId = currentUserId();
        if (userId == null) {
            return Result.error("未登录！");
        }
        return Result.success(notificationService.listByReceiver(userId, pageNum, pageSize));
    }

    /** 未读数（前端角标） */
    @GetMapping("/unreadCount")
    public Result<Integer> unreadCount() {
        Integer userId = currentUserId();
        if (userId == null) {
            return Result.error("未登录！");
        }
        return Result.success(notificationService.unreadCount(userId));
    }

    /** 标记单条已读 */
    @PatchMapping("/read/{id}")
    public Result<String> read(@PathVariable int id) {
        Integer userId = currentUserId();
        if (userId == null) {
            return Result.error("未登录！");
        }
        if (!notificationService.markRead(userId, id)) {
            return Result.error("消息不存在或已读！");
        }
        return Result.success();
    }

    /** 全部标记已读，返回受影响条数 */
    @PatchMapping("/readAll")
    public Result<Integer> readAll() {
        Integer userId = currentUserId();
        if (userId == null) {
            return Result.error("未登录！");
        }
        return Result.success(notificationService.markAllRead(userId));
    }

    /** 从 ThreadLocal 取当前登录用户 id（TokenCheck 已校验登录） */
    private Integer currentUserId() {
        Object info = ThreadLocalUtil.get();
        if (info instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (id instanceof Integer value) {
                return value;
            }
            if (id != null) {
                try {
                    return Integer.valueOf(id.toString());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
