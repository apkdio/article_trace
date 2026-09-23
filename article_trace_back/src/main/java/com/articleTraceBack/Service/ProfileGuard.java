package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.TextNormalizer;
import com.articleTraceBack.config.SensitiveWordHolder;
import com.articleTraceBack.config.SiteFeatureProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 昵称 / 个签的内容规则：格式类是无效输入，直接拒；内容类落待审，交人工判断。
 * 违禁词直接依赖 {@link SensitiveWordHolder}（同一份热更新词表），不经 {@code ArticleService}。
 */
@Slf4j
@Component
public class ProfileGuard {

    /** 判定结果：{@code PASS} 通过；{@code FORMAT} 直接拒；{@code CONTENT} 进待审 */
    public record Verdict(Kind kind, String reason) {
        public enum Kind {PASS, FORMAT, CONTENT}

        public boolean pass() {
            return kind == Kind.PASS;
        }
    }

    private static final Verdict PASS = new Verdict(Verdict.Kind.PASS, null);
    private static final int NICKNAME_MIN = 2;
    private static final int NICKNAME_MAX = 20;
    private static final int SIGNATURE_MAX = 30;

    /** 冒充站方身份的词，逗号分隔；站名另从 {@code site.display-name} 取。放配置里，改词不必重新发版 */
    @Value("${profile.impersonation-words:站长,管理员,管理員,官方,客服,认证}")
    private String impersonationWords;

    /** 联系方式正则。匹配归一化后的文本，关键词单独出现也算命中；误判的代价只是多进一次人工 */
    private static final List<Pattern> CONTACTS = List.of(
            Pattern.compile("1[3-9]\\d-?\\d{4}-?\\d{4}"),
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[a-z]{2,}"),
            Pattern.compile("https?://|www\\.|[\\w-]+\\.(com|cn|net|org|top|xyz|cc)"),
            Pattern.compile("qq|微信|weixin|wx|vx|v信|威信|扣扣|企鹅|加v|加我|私聊|联系方式"));

    private final SensitiveWordHolder sensitiveWordHolder;
    private final SiteFeatureProperties siteFeatureProperties;

    public ProfileGuard(SensitiveWordHolder sensitiveWordHolder, SiteFeatureProperties siteFeatureProperties) {
        this.sensitiveWordHolder = sensitiveWordHolder;
        this.siteFeatureProperties = siteFeatureProperties;
    }

    /** 昵称：长度与字符集是格式类，联系方式 / 冒充 / 违禁词是内容类 */
    public Verdict checkNickname(String nickname) {
        String raw = nickname == null ? "" : nickname;
        Verdict format = checkFormat(raw, NICKNAME_MIN, NICKNAME_MAX);
        return format.pass() ? checkContent(raw, true) : format;
    }

    /** 个签：与昵称同一套规则，但长度上限不同、不查冒充；清空视为合法 */
    public Verdict checkSignature(String signature) {
        String raw = signature == null ? "" : signature;
        if (raw.isBlank()) {
            return PASS;
        }
        Verdict format = checkFormat(raw, 1, SIGNATURE_MAX);
        return format.pass() ? checkContent(raw, false) : format;
    }

    private Verdict checkFormat(String raw, int min, int max) {
        if (!raw.equals(raw.strip())) {
            return new Verdict(Verdict.Kind.FORMAT, "首尾不能有空格");
        }
        if (raw.length() < min || raw.length() > max) {
            return new Verdict(Verdict.Kind.FORMAT, "长度需为 " + min + "-" + max + " 个字符");
        }
        // 字符集看原文：归一化会抹掉零宽字符，在归一化结果上判断等于放行。
        // 全角字母数字与半角同形，放行后交给归一化再判内容。
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '\u3000' || c == '_' || c == '-' || c == '.' || c == '·') {
                continue;
            }
            if (c < 128 && Character.isLetterOrDigit(c)) {
                continue;
            }
            if (c >= '\uFF10' && c <= '\uFF19') {
                continue;
            }
            if (c >= '\uFF21' && c <= '\uFF3A') {
                continue;
            }
            if (c >= '\uFF41' && c <= '\uFF5A') {
                continue;
            }
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                continue;
            }
            return new Verdict(Verdict.Kind.FORMAT, "只能使用中文、字母、数字、下划线、连字符、中点与空格");
        }
        return PASS;
    }

    private Verdict checkContent(String raw, boolean withImpersonation) {
        String normalized = TextNormalizer.forMatch(raw);
        List<String> hit = sensitiveWordHolder.get().search(normalized).stream()
                .map(m -> m.keyword).distinct().toList();
        if (!hit.isEmpty()) {
            // 命中的词只记日志给站长，不回给作者，否则等于把词表告诉他
            log.info("profile blocked by sensitive word: hits={}", hit);
            return new Verdict(Verdict.Kind.CONTENT, "包含不允许的词汇");
        }
        for (Pattern pattern : CONTACTS) {
            if (pattern.matcher(normalized).find()) {
                return new Verdict(Verdict.Kind.CONTENT, "不要包含联系方式");
            }
        }
        if (withImpersonation && containsImpersonation(normalized)) {
            return new Verdict(Verdict.Kind.CONTENT, "疑似冒充站方身份");
        }
        return PASS;
    }

    private boolean containsImpersonation(String normalized) {
        for (String word : impersonationWords.split(",")) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty() && normalized.contains(trimmed)) {
                return true;
            }
        }
        String displayName = siteFeatureProperties.getDisplayName();
        return displayName != null && !displayName.isBlank()
                && normalized.contains(TextNormalizer.forMatch(displayName));
    }
}
