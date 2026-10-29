package com.articleTraceBack.Service.Support;

import com.articleTraceBack.Service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 登录到期提醒：铃铛轮询时顺带检查会话剩余 TTL，在 6 小时与 1 小时两档各提醒一次。
 * 剩余不足 1 小时时只发 1 小时档（优先给可行动的那条）；同一 token 的同一档位只发一条。
 */
@Slf4j
@Component
public class SessionExpiryNotifier {

    /** 场景只投站内信（配置见 notification.scenes，缺省回落 defaultChannel） */
    private static final String SCENE = "session-expire";
    private static final long ONE_HOUR = 3600;
    private static final long SIX_HOURS = 6 * ONE_HOUR;
    /** 去重标记：同一 token 同一档位只发一次；TTL 跟随会话剩余时长 */
    private static final String MARK_KEY = "login:expire-notice:%s:%s";

    private final StringRedisTemplate redis;
    private final NotificationService notificationService;

    public SessionExpiryNotifier(StringRedisTemplate redis, NotificationService notificationService) {
        this.redis = redis;
        this.notificationService = notificationService;
    }

    /**
     * 检查当前会话并在跨过阈值时发一条站内信。
     *
     * @return 会话剩余秒数；没有有效会话返回 0
     */
    public long notifyIfNeeded(int receiverId, String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        try {
            Long ttl = redis.getExpire(username, TimeUnit.SECONDS);
            if (ttl == null || ttl <= 0) {
                return 0;
            }
            String token = redis.opsForValue().get(username);
            if (token == null) {
                return 0;
            }
            String tokenHash = DigestUtils.md5DigestAsHex(token.getBytes(StandardCharsets.UTF_8));
            if (ttl <= ONE_HOUR) {
                if (markOnce(tokenHash, "1h", ttl)) {
                    notificationService.notify(receiverId, SCENE,
                            "【登录提醒】登录将在 1 小时内过期",
                            "你的登录状态大约还剩 " + minutesOf(ttl) + " 分钟。未保存的草稿会保留在本地，重新登录后可以继续。");
                }
            } else if (ttl <= SIX_HOURS) {
                if (markOnce(tokenHash, "6h", ttl)) {
                    notificationService.notify(receiverId, SCENE,
                            "【登录提醒】登录将在 6 小时内过期",
                            "你的登录状态大约还剩 " + hoursOf(ttl) + " 小时。请留意保存进度，过期后需要重新登录。");
                }
            }
            return ttl;
        } catch (Exception e) {
            log.warn("session expiry check failed: username={}", username, e);
            return 0;
        }
    }

    private boolean markOnce(String tokenHash, String bucket, long ttlSeconds) {
        Boolean first = redis.opsForValue().setIfAbsent(
                String.format(MARK_KEY, bucket, tokenHash), "1", ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(first);
    }

    private long minutesOf(long seconds) {
        return Math.max(1, (seconds + 59) / 60);
    }

    private long hoursOf(long seconds) {
        return Math.max(1, (seconds + 3599) / 3600);
    }
}
