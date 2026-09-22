package com.articleTraceBack.Service;

/** 登录失败计数与黑名单（按「IP + User-Agent」指纹统计）：达阈值需图形验证码，再达阈值进入黑名单；登录成功即清空。 */
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
