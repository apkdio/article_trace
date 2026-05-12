package com.articleTraceBack.scheduledTask;

import com.articleTraceBack.Utils.AhoCorasickUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

@Component
@EnableScheduling
public class SyncSensitiveWordLoader {

    private static final Logger log = LoggerFactory.getLogger(SyncSensitiveWordLoader.class);

    @Value("${sensitive_word.filePath}")
    private String wordFilePath;

    @Getter
    private volatile AhoCorasickUtil latestAhoCorasick;
    private volatile long lastModified = 0L;
    @Setter
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        loadAndBuild();
        log.info("Sensitive word hot sync service enabled. External file path: {}", wordFilePath);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            log.info("Disable words hot sync service.");
        }
    }

    @Scheduled(cron = "${sensitive_word.cron}")
    private void checkAndReload() {
        Path path = Paths.get(wordFilePath);
        if (!Files.exists(path)) {
            return;
        }
        try {
            long currentModified = Files.getLastModifiedTime(path).toMillis();
            if (currentModified > lastModified) {
                log.info("Detected file changed! Reload words list...");
                loadAndBuild();
                log.info("Reload words list complete!");
            }
        } catch (IOException e) {
            log.error("Check file time failure! detail:", e);
        }
    }

    private synchronized void loadAndBuild() {
        Path path = Paths.get(wordFilePath);
        if (!Files.exists(path)) {
            log.error("Not found file: {}", wordFilePath);
            return;
        }
        try (var lines = Files.lines(path)) {
            List<String> words = lines.map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();

            AhoCorasickUtil newAc = new AhoCorasickUtil();
            for (String word : words) {
                newAc.addKeyword(word);
            }
            newAc.build();
            this.latestAhoCorasick = newAc;
            this.lastModified = Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            log.error("Failure to load file! Words list not changed!", e);
        }
    }

}