package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.NotificationService;
import com.articleTraceBack.Service.Support.SessionExpiryNotifier;
import com.articleTraceBack.Utils.CurrentUser;
import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** 站内信接口。接收方一律取登录态，不接受前端传 userId，避免越权读取他人消息。 */
@RestController
@RequestMapping("/notification")
public class NotificationController {

    private final NotificationService notificationService;
    private final SessionExpiryNotifier sessionExpiryNotifier;

    public NotificationController(NotificationService notificationService,
                                  SessionExpiryNotifier sessionExpiryNotifier) {
        this.notificationService = notificationService;
        this.sessionExpiryNotifier = sessionExpiryNotifier;
    }

    /** 我的站内信分页（倒序）；type 可选：system / apply，不传或 all 查全部 */
    @GetMapping("/list")
    public Result<PageBean<Notification>> list(@RequestParam(required = false) String type,
                                               @RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "10") int pageSize) {
        Integer userId = currentUserId();
        return Result.success(notificationService.listByReceiver(userId, type, pageNum, pageSize));
    }

    /** 未读数（前端角标）；铃铛轮询兼作会话检查，快到期时顺带发站内信提醒 */
    @GetMapping("/unreadCount")
    public Result<Map<String, Object>> unreadCount() {
        CurrentUser.Session me = CurrentUser.require();
        int userId = me.id();
        long expiresIn = sessionExpiryNotifier.notifyIfNeeded(userId, me.username());
        Map<String, Object> data = new HashMap<>();
        data.put("count", notificationService.unreadCount(userId));
        data.put("expiresIn", expiresIn);
        return Result.success(data);
    }

    /** 标记单条已读 */
    @PatchMapping("/read/{id}")
    public Result<String> read(@PathVariable int id) {
        Integer userId = currentUserId();
        if (!notificationService.markRead(userId, id)) {
            return Result.error("消息不存在或已读！");
        }
        return Result.success();
    }

    /** 全部标记已读，返回受影响条数 */
    @PatchMapping("/readAll")
    public Result<Integer> readAll() {
        Integer userId = currentUserId();
        return Result.success(notificationService.markAllRead(userId));
    }

    /** 删除单条站内信（仅限本人） */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable int id) {
        Integer userId = currentUserId();
        if (!notificationService.delete(userId, id)) {
            return Result.error("消息不存在！");
        }
        return Result.success();
    }

    /** 从 ThreadLocal 取当前登录用户 id（TokenCheck 已校验登录） */
    private Integer currentUserId() {
        return CurrentUser.require().id();
    }
}
