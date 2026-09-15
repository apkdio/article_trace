package com.articleTraceBack.config;

import com.articleTraceBack.Utils.AhoCorasickUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词匹配器的持有者。
 *
 * <p><b>为什么需要它</b>：敏感词表支持运行时热更新，而匹配器本身是不可变对象（构建后不能改词）。
 * 所以要热更新，业务就不能直接注入一个固定的 {@code AhoCorasickUtil} bean——
 * 那等于把启动那一刻的快照焊死在业务里。这里改为持有一个 {@code volatile} 引用，
 * 业务每次通过 {@link #get()} 取当前实例，重载时整体替换引用即可。
 *
 * <p><b>此前的问题</b>：{@code SensitiveWordConfig} 建了一个 bean 注入给业务，
 * 而 {@code SyncSensitiveWordLoader} 又自行构建新实例写进自己的字段，两边不相干——
 * 定时任务每 30 分钟认真重载并打日志，业务用的却始终是启动快照，热更新形同虚设。</p>
 *
 * <p><b>替换而非修改</b>：{@code AhoCorasickUtil} 构建后不可变，所以不存在"读到半成品"的问题，
 * 正在使用旧实例的线程可以安全地把这次匹配做完。</p>
 */
@Slf4j
@Component
public class SensitiveWordHolder {

    @Value("${sensitive_word.filePath}")
    private String wordFilePath;

    /** 当前生效的匹配器；初始为空实例，避免调用方拿到 null */
    private volatile AhoCorasickUtil current = buildFrom(new ArrayList<>());

    @PostConstruct
    public void init() {
        if (reload()) {
            log.info("sensitive words loaded from {}", wordFilePath);
        } else {
            log.warn("sensitive words load failed at startup, word matching is disabled until next successful reload");
        }
    }

    /** 取当前生效的匹配器。 */
    public AhoCorasickUtil get() {
        return current;
    }

    /**
     * 从词表文件重新加载并替换当前匹配器。
     *
     * @return 是否加载成功；失败时**保留**原有匹配器，不会让校验能力凭空失效
     */
    public synchronized boolean reload() {
        List<String> words = loadWords();
        if (words == null) {
            return false;
        }
        this.current = buildFrom(words);
        return true;
    }

    /**
     * 读取词表：优先外部文件，读不到或读取失败时降级到 classpath 内置词表。
     *
     * @return 词条列表；连内置词表都读不到时返回 {@code null} 表示本次加载失败
     */
    private List<String> loadWords() {
        Path path = Paths.get(wordFilePath);
        if (Files.exists(path)) {
            try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
                return lines.map(String::trim).filter(s -> !s.isEmpty()).toList();
            } catch (Exception e) {
                log.warn("read external sensitive words failed, fallback to classpath: {}", e.getMessage());
            }
        } else {
            log.warn("external sensitive words file not found: {}, fallback to classpath", path);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("sensitive_words.txt").getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
        } catch (Exception e) {
            log.error("read classpath sensitive words failed", e);
            return null;
        }
    }

    private AhoCorasickUtil buildFrom(List<String> words) {
        AhoCorasickUtil ac = new AhoCorasickUtil();
        for (String w : words) {
            ac.addKeyword(w);
        }
        ac.build();
        return ac;
    }
}
