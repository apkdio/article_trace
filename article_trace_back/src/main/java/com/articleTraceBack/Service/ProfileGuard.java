package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.TextNormalizer;
import com.articleTraceBack.config.SensitiveWordHolder;
import com.articleTraceBack.config.SiteFeatureProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 昵称 / 个签的内容规则。两类结果分开：**格式类**（长度、字符集）是无效输入，直接拒；
 * **内容类**（联系方式、冒充、违禁词）交给上层落待审——它们「像不像引流 / 冒充」需要人判断。
 *
 * <p>违禁词直接依赖 {@link SensitiveWordHolder}（同一份热更新词表），不经 {@code ArticleService}，
 * 免得用户资料模块平白依赖文章模块。匹配前先过 {@link TextNormalizer}，`微 信`、`ＱＱ` 这类绕写同样命中。</p>
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

    /** 冒充站方身份的词；站名本身另从 {@code site.display-name} 取，不写死 */
    private static final List<String> IMPERSONATION_WORDS = List.of(
            "站长", "管理员", "管理員", "官方", "客服", "认证", "文迹");

    /**
     * 联系方式。匹配的是**归一化后**的文本，所以 `V：`、`微 信`、`ＱＱ` 都会还原成同一种写法。
     * 关键词单独出现也算命中（`微信abc123` 就没有「数字紧跟」这个特征），误判代价只是多进一次人工。
     */
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

    /** 个签：与昵称同一套规则，但上限不同、不查冒充（个签里出现「客服」属正常表达）；清空视为合法 */
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
        // 字符集看**原文**：归一化会把零宽字符抹掉，若在归一化后的文本上判断，
        // 「张&#8203;三」就会被当成正常的「张三」放过去。
        // 全角字母数字（ＦＦ１０-ＦＦ１９ / ＦＦ２１-ＦＦ３Ａ / ＦＦ４１-ＦＦ５Ａ）放行：
        // 它们与半角同形，是中文输入法下的正常产物、也是绕写手法——交给归一化后再判内容。
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
            // 命中的词只记日志给站长，不回给作者——告诉他词表长什么样，等于教他怎么绕
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
        for (String word : IMPERSONATION_WORDS) {
            if (normalized.contains(word)) {
                return true;
            }
        }
        String displayName = siteFeatureProperties.getDisplayName();
        return displayName != null && !displayName.isBlank()
                && normalized.contains(TextNormalizer.forMatch(displayName));
    }
}
