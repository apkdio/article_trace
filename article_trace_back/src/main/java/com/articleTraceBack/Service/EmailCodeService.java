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

    /**
     * 发送验证码。
     *
     * <p>同一邮箱在冷却期内重复请求会被拒绝（返回 false）。</p>
     *
     * @param email 收件邮箱
     * @param scene 场景标识，见 {@link #SCENE_REGISTER} / {@link #SCENE_RESET}
     * @return 是否已受理发送
     */
    boolean send(String email, String scene);

    /**
     * 校验验证码；**校验成功后立即失效**（一次性）。
     */
    boolean verify(String email, String scene, String code);
}
