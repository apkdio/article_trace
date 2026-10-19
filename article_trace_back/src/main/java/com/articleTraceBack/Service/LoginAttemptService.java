package com.articleTraceBack.Service;

/**
 * 登录失败计数与黑名单：**账号名与「IP + User-Agent」指纹双维度叠加**——任一维度达阈值即要求图形码 / 进黑名单。
 * IP 取自请求头、客户端可伪造（见 IPUtil），只作辅助；账号维度才是防爆破的抓手。
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
     * 黑名单剩余秒数（两个维度取较大者）。
     *
     * @return 剩余秒数；0 表示未锁定
     */
    long blockRemainingSeconds(String username, String clientKey);

    /** 是否已被要求出示图形验证码（任一维度达阈值） */
    boolean needCaptcha(String username, String clientKey);

    /**
     * 记录一次登录失败（两个维度都计数）。
     *
     * @return 该窗口内累计失败次数（两个维度取较大者）
     */
    long recordFailure(String username, String clientKey);

    /** 登录成功：清空两个维度的失败计数与黑名单 */
    void clear(String username, String clientKey);
}
