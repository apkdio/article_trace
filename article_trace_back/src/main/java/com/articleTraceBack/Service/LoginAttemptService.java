package com.articleTraceBack.Service;

/**
 * 登录失败计数与黑名单（按「IP + User-Agent」指纹统计，防暴力破解）。
 *
 * <p>失败达到 {@link #CAPTCHA_THRESHOLD} 次后登录必须附带图形验证码；
 * 达到 {@link #BLOCK_THRESHOLD} 次进入 {@link #BLOCK_MINUTES} 分钟黑名单。
 * 计数窗口为 {@link #WINDOW_MINUTES} 分钟（固定窗口，不随失败续期），
 * 登录成功立即清空。</p>
 */
public interface LoginAttemptService {

    /** 失败达到此值后要求图形验证码 */
    long CAPTCHA_THRESHOLD = 3;
    /** 失败达到此值后进入黑名单 */
    long BLOCK_THRESHOLD = 10;
    /** 失败计数保留时长（分钟） */
    long WINDOW_MINUTES = 15;
    /** 黑名单时长（分钟） */
    long BLOCK_MINUTES = 5;

    /**
     * 黑名单剩余秒数。
     *
     * @return 剩余秒数；0 表示未锁定
     */
    long blockRemainingSeconds(String clientKey);

    /** 是否已被要求出示图形验证码 */
    boolean needCaptcha(String clientKey);

    /**
     * 记录一次登录失败。
     *
     * @return 该窗口内累计失败次数
     */
    long recordFailure(String clientKey);

    /** 登录成功：清空失败计数与黑名单 */
    void clear(String clientKey);
}
