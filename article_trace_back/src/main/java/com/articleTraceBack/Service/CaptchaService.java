package com.articleTraceBack.Service;

import java.util.Map;

/**
 * 图形验证码（人机校验）。
 *
 * <p>用于「发送邮箱验证码」之前的一道门槛，防止被脚本刷短信/邮件。
 * 答案存 Redis（2 分钟），**校验一次即失效**。</p>
 */
public interface CaptchaService {

    /**
     * 生成一张图形验证码。
     *
     * @param clientKey 客户端指纹（IP+UA），用于限制拉图频率；可为 null
     * @return {@code captchaId}（回传校验用）与 {@code image}（data URI，可直接给 img src）；
     * 触发频率限制时返回 {@code null}
     */
    Map<String, String> generate(String clientKey);

    /**
     * 校验图形验证码；无论成败都立即失效（防止暴力尝试）。
     */
    boolean verify(String captchaId, String code);
}
