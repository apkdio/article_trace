package com.articleTraceBack.Service;

/**
 * 邮箱验证码：生成、限流、暂存与校验。
 *
 * <p>验证码存 Redis（与 token 同库），发送走 {@link MailService} 的异步投递链路
 * （自带落库与失败重试）。</p>
 */
public interface EmailCodeService {

    /** 注册场景 */
    String SCENE_REGISTER = "register";

    /** 找回密码场景 */
    String SCENE_RESET = "reset";

    /** 校验通过（验证码已消费） */
    int CODE_OK = 1;
    /** 验证码错误；也包含验证码已过期 / 从不存在 */
    int CODE_WRONG = 0;
    /** 连续失败次数用尽：验证码已作废，且该邮箱被暂时锁定 */
    int CODE_EXHAUSTED = -1;
    /** 该邮箱处于锁定中（含「重新获取验证码」也会被拒） */
    int CODE_LOCKED = -2;
    /** 同一来源请求过于频繁（防脚本扫不同邮箱） */
    int CODE_RATE_LIMITED = -3;

    /**
     * 发送验证码。
     *
     * <p>同一邮箱在冷却期内重复请求会被拒绝（返回 false）；
     * 该邮箱处于锁定中时同样拒绝——否则重新发一枚码就把失败计数与作废绕过去了。</p>
     *
     * @param email 收件邮箱
     * @param scene 场景标识，见 {@link #SCENE_REGISTER} / {@link #SCENE_RESET}
     * @return 是否已受理发送
     */
    boolean send(String email, String scene);

    /**
     * 校验验证码，返回 {@link #CODE_OK} 等结果码。
     *
     * <p>校验成功即失效（一次性）；**输错不消费**，但会累计失败次数，达到阈值后
     * 作废验证码并锁定该邮箱若干分钟——否则 6 位码在 5 分钟有效期内可以被撞库撞开。</p>
     *
     * <p>计数、作废、上锁全部在一次 Redis Lua 里完成，保证并发下的唯一性。</p>
     *
     * @param clientKey 请求来源指纹（IP + User-Agent），用于来源限流
     */
    int verify(String email, String scene, String code, String clientKey);

    /** 该邮箱在当前场景下的锁定剩余秒数；未锁定返回 0 */
    long lockRemainingSeconds(String email, String scene);
}
