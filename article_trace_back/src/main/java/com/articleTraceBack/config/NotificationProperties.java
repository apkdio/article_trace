package com.articleTraceBack.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知基础设施配置（对应 application.yml 的 notification.*）。
 *
 * <ul>
 *   <li>{@code notification.scenes} —— 场景到投递渠道的映射（inbox / mail / both / none）</li>
 *   <li>{@code notification.defaultChannel} —— 未配置场景的默认渠道</li>
 *   <li>{@code notification.mail} —— 邮件渠道开关与重试参数</li>
 *   <li>{@code notification.cleanup} —— 站内信清理参数</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    /** 场景 → 渠道 */
    private Map<String, String> scenes = new HashMap<>();

    /** 未配置场景的默认渠道 */
    private String defaultChannel = "inbox";

    private Mail mail = new Mail();

    private Cleanup cleanup = new Cleanup();

    @Data
    public static class Mail {
        /** 邮件渠道总开关 */
        private boolean enabled = true;
        /** 最多重发次数 */
        private int maxRetry = 2;
        /** 重试扫描 cron */
        private String retryCron = "0 */10 * * * ?";
    }

    @Data
    public static class Cleanup {
        /** 清理任务 cron */
        private String cron = "0 0 3 * * ?";
        /** 站内信保留天数 */
        private int keepDays = 30;
    }
}
