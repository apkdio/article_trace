package com.articleTraceBack.Utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 邮件模板加载与渲染。模板位于 {@code classpath:templates/email/}，同名可备 {@code .html} 与 {@code .txt} 两份，占位符写作 {{key}}，未提供值的原样保留。
 * 本类不做 HTML 转义，需要透传用户内容时由调用方先转义。
 */
@Slf4j
@Component
public class EmailTemplateUtil {

    private static final String BASE_PATH = "templates/email/";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    /**
     * 渲染模板。
     *
     * @param templateName 模板名，不含扩展名，如 {@code email-code}
     * @param extension    扩展名，{@code html} 或 {@code txt}
     * @param variables    占位符变量
     * @return 渲染结果；模板不存在或读取失败时返回 {@code null}
     */
    public String render(String templateName, String extension, Map<String, String> variables) {
        String path = BASE_PATH + templateName + "." + extension;
        String raw;
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                log.error("email template not found: {}", path);
                return null;
            }
            raw = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("read email template failed: {}", path, e);
            return null;
        }

        Matcher matcher = PLACEHOLDER.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value == null) {
                log.warn("email template placeholder without value: template={}, key={}",
                        templateName, key);
                value = matcher.group(0);
            }
            // quoteReplacement：变量里的 $ 与 \ 不是替换语法的一部分
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
