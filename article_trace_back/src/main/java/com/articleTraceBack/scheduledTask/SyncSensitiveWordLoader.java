package com.articleTraceBack.scheduledTask;

import com.articleTraceBack.config.SensitiveWordHolder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 敏感词表热更新。
 *
 * <p>只负责「发现文件变了 → 让 holder 重载」，自己不保存任何匹配器实例——
 * 早先这里构建的新实例写进了本类字段，而业务读的是另一个 bean，导致热更新一直没生效。</p>
 */
@Slf4j
@Component
@EnableScheduling
public class SyncSensitiveWordLoader {

    @Value("${sensitive_word.filePath}")
    private String wordFilePath;

    private final SensitiveWordHolder holder;

    private volatile long lastModified = 0L;

    public SyncSensitiveWordLoader(SensitiveWordHolder holder) {
        this.holder = holder;
    }

    @PostConstruct
    public void init() {
        Path path = Paths.get(wordFilePath);
        if (Files.exists(path)) {
            try {
                lastModified = Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                log.warn("read words file timestamp failed: {}", e.getMessage());
            }
        }
        log.info("sensitive word hot reload enabled. external file: {}", wordFilePath);
    }

    @Scheduled(cron = "${sensitive_word.cron}")
    public void checkAndReload() {
        Path path = Paths.get(wordFilePath);
        if (!Files.exists(path)) {
            return;
        }
        try {
            long currentModified = Files.getLastModifiedTime(path).toMillis();
            if (currentModified > lastModified) {
                log.info("detected sensitive words changed, reloading...");
                if (holder.reload()) {
                    lastModified = currentModified;
                    log.info("sensitive words reloaded, effective immediately");
                } else {
                    // 本次不动 lastModified：下一轮会再试，避免一次失败就再也感知不到这次变更
                    log.warn("sensitive words reload failed, will retry on next tick");
                }
            }
        } catch (IOException e) {
            log.error("check sensitive words file failed", e);
        }
    }
}
