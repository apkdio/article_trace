package com.articleTraceBack.config;

import com.articleTraceBack.Utils.AhoCorasickUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
public class SensitiveWordConfig {

    @Value("${sensitive_word.filePath}")
    private String sensitiveWordsFilePath;

    @Bean
    public AhoCorasickUtil ahoCorasick() {
        AhoCorasickUtil ac = new AhoCorasickUtil();
        List<String> keywords = loadSensitiveWords();
        for (String kw : keywords) {
            ac.addKeyword(kw);
        }
        ac.build();
        log.info("AC build successful !");
        return ac;
    }

    private List<String> loadSensitiveWords() {
        // 1. 优先尝试读取外部文件
        log.info("Trying to read sensitive words from external file: {}", sensitiveWordsFilePath);
        Path filePath = Paths.get(sensitiveWordsFilePath);
        if (Files.exists(filePath)) {
            try (var lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
                return lines.filter(line -> !line.trim().isEmpty())
                        .toList();
            } catch (Exception e) {
                // 外部文件读取失败时记录日志，并降级到 classpath 文件
                log.warn("Failure to read external file! Use default file. detail:" + e.getMessage());
            }
        }
        log.warn("Not found external file:{}",filePath);

        // 2. 降级：从 classpath 读取默认文件
        ClassPathResource resource = new ClassPathResource("sensitive_words.txt");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.trim().isEmpty())
                    .toList();
        } catch (Exception e) {
            // 如果 classpath 文件也不存在，返回空列表，避免 NPE
            log.warn("Not found words file! Use empty list.");
            return new ArrayList<>();
        }
    }
}