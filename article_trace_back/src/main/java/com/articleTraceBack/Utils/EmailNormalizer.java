package com.articleTraceBack.Utils;

import java.util.Locale;

/**
 * 邮箱归一化：去首尾空白 + 转小写。
 *
 * <p>落库、查重、按邮箱取用户与验证码的 Redis 键必须同口径，否则大小写变体会让「查重说没有、唯一索引说有」。</p>
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /** null 原样返回，便于直接接在可能为空的字段上（MyBatis-Plus 会跳过 null 列） */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
