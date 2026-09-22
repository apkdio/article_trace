package com.articleTraceBack.Service;

/** 邮箱验证码：生成、限流、暂存与校验；发送走 {@link MailService} 的异步投递链路。 */
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
    * 发送验证码；同一邮箱在冷却期内或处于锁定中时拒绝发送。
    * @param email 收件邮箱
    * @param scene 场景标识，见 {@link #SCENE_REGISTER} / {@link #SCENE_RESET}
    * @return 是否已受理发送
    */
    boolean send(String email, String scene);

    /**
    * 校验验证码，返回 {@link #CODE_OK} 等结果码。成功即失效；输错不消费但累计失败，达阈值后作废并锁定邮箱；计数、作废、上锁在一次 Redis Lua 内完成。
    * @param clientKey 请求来源指纹（IP + User-Agent），用于来源限流
    */
    int verify(String email, String scene, String code, String clientKey);

    /** 该邮箱在当前场景下的锁定剩余秒数；未锁定返回 0 */
    long lockRemainingSeconds(String email, String scene);
}
