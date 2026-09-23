package com.articleTraceBack.Controller;

import com.articleTraceBack.Utils.*;
import com.articleTraceBack.config.SiteFeatureProperties;

import com.articleTraceBack.Service.AvatarApplyService;
import com.articleTraceBack.Service.CaptchaService;
import com.articleTraceBack.Service.EmailCodeService;
import com.articleTraceBack.Service.LoginAttemptService;
import com.articleTraceBack.Service.UserService;
import org.springframework.dao.DuplicateKeyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.articleTraceBack.pojo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/user")
public class UserController {

    /**
     * 注册一律为读者；成为作者走「申请-审批」流程
     */
    private static final int ROLE_READER = 2;

    /**
     * 邮箱格式（宽松校验，真实可达性由验证码保证）
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserService userService;
    private final AvatarApplyService avatarApplyService;
    private final EmailCodeService emailCodeService;
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;
    private final SiteFeatureProperties siteFeatures;
    @Value("${spring.application.admin.defaultUser}")
    private String defaultUser;
    @Value("${Password.masterPass}")
    private String masterPassword;
    @Value("${JWT.longTime}")
    private long longTime;
    @Value("${JWT.shortTime}")
    private long shortTime;
    /**
     * 令牌 Cookie 是否只在 HTTPS 下发送；本地 http 调试必须为 false，否则浏览器不保存
     */
    @Value("${JWT.cookieSecure:false}")
    private boolean cookieSecure;

    public UserController(UserService userService, AvatarApplyService avatarApplyService,
                          EmailCodeService emailCodeService,
                          CaptchaService captchaService, LoginAttemptService loginAttemptService,
                          SiteFeatureProperties siteFeatures) {
        this.userService = userService;
        this.avatarApplyService = avatarApplyService;
        this.emailCodeService = emailCodeService;
        this.captchaService = captchaService;
        this.loginAttemptService = loginAttemptService;
        this.siteFeatures = siteFeatures;
    }

    /**
     * 生成图形验证码（人机校验，发送邮箱验证码前使用）。
     */
    @GetMapping("/captcha")
    public Result<Map<String, String>> captcha(HttpServletRequest request) {
        Map<String, String> captcha = captchaService.generate(IPUtil.mixOf(request));
        if (captcha == null) {
            return Result.error("请求过于频繁，请稍后再试！");
        }
        return Result.success(captcha);
    }

    /** 发送邮箱验证码；需先通过图形验证码，{@code scene} 默认 register，找回密码传 reset。 */
    @PostMapping("/email/code")
    public Result<String> sendEmailCode(@RequestBody(required = false) Map<String, String> body) {
        String email = (body == null) ? null : body.get("email");
        String scene = (body == null) ? null : body.getOrDefault("scene", EmailCodeService.SCENE_REGISTER);
        String captchaId = (body == null) ? null : body.get("captchaId");
        String captchaCode = (body == null) ? null : body.get("captchaCode");

        // 1. 人机校验（图形验证码，一次性）
        if (!captchaService.verify(captchaId, captchaCode)) {
            return Result.error("图形验证码错误或已过期！");
        }
        // 2. 邮箱格式
        if (email == null || email.isBlank()) {
            return Result.error("邮箱不能为空！");
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            return Result.error("邮箱格式不正确！");
        }
        // 3. 场景校验
        if (EmailCodeService.SCENE_RESET.equals(scene)) {
            if (userService.findUserByEmail(email.trim()) == null) {
                return Result.error("该邮箱尚未注册！");
            }
        } else {
            scene = EmailCodeService.SCENE_REGISTER;
        }
        // 4. 注册场景在单用户态下直接拒绕：注册接口关了但验证码照发，等于没关
        if (EmailCodeService.SCENE_REGISTER.equals(scene) && !siteFeatures.isRegisterEnabled()) {
            return Result.error("本站暂不开放注册！");
        }
        // 5. 发码（锁定中的邮箱会被 send 拒绕，这里先给出带剩余时长的提示）
        long lockedSeconds = emailCodeService.lockRemainingSeconds(email, scene);
        if (lockedSeconds > 0) {
            return Result.error("尝试次数过多，请 " + minutesOf(lockedSeconds) + " 分钟后再试！");
        }
        if (!emailCodeService.send(email, scene)) {
            return Result.error("发送过于频繁，请稍后再试！");
        }
        return Result.success("验证码已发送");
    }

    /** 登录态探活：能访问到即已通过鉴权 */
    @GetMapping("/loginCheck")
    public void loginCheck() {
    }

    /** 注册（读者；作者走申请审批） */
    @PostMapping("/register")
    public Result<String> register(@RequestBody @Validated RegisterUserPojo user,
                                   HttpServletRequest request) {
        Map<String, Object> error = new HashMap<>();
        // 单用户态下关闭注册（个人备案要求：开放注册属于交互式服务，且邮箱注册不是实名）
        if (!siteFeatures.isRegisterEnabled()) {
            return Result.error("本站暂不开放注册！");
        }
        String username = user.getUsername();
        String password = user.getPassword();
        String confirmPassword = user.getConfirmPassword();
        String email = user.getEmail();
        String emailCode = user.getEmailCode();

        if (!password.equals(confirmPassword)) {
            error.put("confirmPassword", "两次密码不一致！");
            return Result.error(error);
        }
        // 先查占用，避免无效请求白白消耗验证码
        if (userService.findUserByName(username) != null) {
            error.put("username", "用户名已占用！");
            return Result.error(error);
        }
        if (userService.findUserByEmail(email) != null) {
            error.put("email", "该邮箱已被注册！");
            return Result.error(error);
        }
        // 邮箱验证码：一次性；输错会累计次数，用尽即作废该码并锁定邮箱
        int codeResult = emailCodeService.verify(email, EmailCodeService.SCENE_REGISTER, emailCode,
                IPUtil.mixOf(request));
        if (codeResult != EmailCodeService.CODE_OK) {
            error.put("emailCode", emailCodeMessage(codeResult, email, EmailCodeService.SCENE_REGISTER));
            return Result.error(error);
        }
        // 注册一律为读者；成为作者请走「申请成为作者」
        User registerUser = new User();
        registerUser.setUsername(username);
        registerUser.setPassword(password);
        registerUser.setEmail(email);
        registerUser.setType(ROLE_READER);
        try {
            if (userService.userRegister(registerUser)) {
                return Result.success();
            }
        } catch (DuplicateKeyException e) {
            // 并发下两个请求可能同时通过查重，由唯一索引（uk_email）兜底
            error.put("email", "该邮箱已被注册！");
            return Result.error(error);
        }
        error.put("error", "注册失败！请重试！");
        return Result.error(error);
    }

    /** 登录：校验验证码与失败次数后签发 JWT */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Validated(User.login.class) User user,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        Map<String, Object> error = new HashMap<>();
        String clientKey = IPUtil.mixOf(request);

        // 1. 黑名单：连续失败过多，直接拒绝
        long blockedSeconds = loginAttemptService.blockRemainingSeconds(clientKey);
        if (blockedSeconds > 0) {
            long minutes = (blockedSeconds + 59) / 60;
            error.put("blocked", minutes);
            error.put("error", "登录尝试过于频繁，请 " + minutes + " 分钟后再试！");
            return Result.error(error);
        }

        // 2. 失败超过阈值后必须出示图形验证码；图形码错误不计入失败次数
        boolean needCaptcha = loginAttemptService.needCaptcha(clientKey);
        if (needCaptcha && !captchaService.verify(user.getCaptchaId(), user.getCaptchaCode())) {
            error.put("captcha", "图形验证码错误或已过期！");
            error.put("needCaptcha", true);
            return Result.error(error);
        }

        String username = user.getUsername();
        String password = user.getPassword();
        int rememberMe = user.getRememberMe();
        User result = userService.findUserByName(username);
        if (result != null) {
            if (userService.checkPass(password, username, "password")) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", result.getId());
                userInfo.put("name", username);
                userInfo.put("type", result.getType());
                String token = userService.genToken(userInfo, rememberMe);
                if (!userService.setRedisToken(username, token, rememberMe)) {
                    error.put("error", "Redis 服务异常！");
                    return Result.error(error);
                }
                // 令牌只下发到 HttpOnly Cookie，不再交给前端脚本；
                // 有效期与 Redis 里的 TTL 保持一致，否则会出现「Cookie 还在、后端已不认」。
                CookieUtil.writeToken(response, token,
                        (rememberMe == 1 ? longTime : shortTime) / 1000, cookieSecure);
                String lastLogin;
                if (result.getLastLogin() == null) {
                    lastLogin = "暂无";
                } else {
                    lastLogin = result.getLastLogin().format(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    );
                }
                userService.updateLoginTime(username);
                // 登录成功立即清零，避免正常用户被历史失败继续累计
                loginAttemptService.clear(clientKey);
                Map<String, Object> loginResult = new HashMap<>();
                loginResult.put("lastLogin", lastLogin);
                return Result.success(loginResult);
            }
            return loginFailure(error, clientKey, "password", "密码错误！");
        }
        return loginFailure(error, clientKey, "username", "用户名不存在！");
    }

    /**
     * 记一次登录失败，并把「下次是否需要图形码 / 距离锁定还剩几次」带回前端。
     */
    private Result<Map<String, Object>> loginFailure(Map<String, Object> error, String clientKey,
                                                     String field, String message) {
        long failures = loginAttemptService.recordFailure(clientKey);
        error.put(field, message);
        error.put("needCaptcha", failures >= LoginAttemptService.CAPTCHA_THRESHOLD);
        error.put("remaining", Math.max(0, LoginAttemptService.BLOCK_THRESHOLD - failures));
        return Result.error(error);
    }

    /**
     * 把验证码校验的结果码翻成给用户看的话；“次数用尽”与“锁定中”都要把等待时长说清楚
     */
    private String emailCodeMessage(int result, String email, String scene) {
        return switch (result) {
            case EmailCodeService.CODE_EXHAUSTED -> "验证码错误次数过多，该验证码已作废；请 "
                    + minutesOf(emailCodeService.lockRemainingSeconds(email, scene))
                    + " 分钟后重新获取！";
            case EmailCodeService.CODE_LOCKED -> "该邮箱已被暂时锁定，请 "
                    + minutesOf(emailCodeService.lockRemainingSeconds(email, scene))
                    + " 分钟后再试！";
            case EmailCodeService.CODE_RATE_LIMITED -> "操作过于频繁，请稍后再试！";
            default -> "验证码错误或已过期！";
        };
    }

    /**
     * 秒数向上取整成分钟，至少算 1 分钟——“请 0 分钟后再试”比不说还糟
     */
    private long minutesOf(long seconds) {
        return Math.max(1, (seconds + 59) / 60);
    }

    /** 当前登录用户信息 */
    @GetMapping("/userInfo")
    public Result<Object> userInfo() {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        Map<String, Object> error = new HashMap<>();
        String username = (String) userInfo.get("name");
        int uid = (int) userInfo.get("id");
        User user = userService.findUserByName(username);
        if (user == null) {
            error.put("error", "未找到用户！");
            return Result.error(error);
        }
        user.setArticlesTotal(userService.getAllArticles(uid));
        return Result.success(user);
    }

    /** 修改昵称 / 邮箱 */
    @PatchMapping("/update")
    public Result<String> update(@RequestBody @Validated(User.update.class) User user) {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        Map<String, Object> error = new HashMap<>();
        String name = user.getUsername();
        String username = userInfo.get("name").toString();
        int id = user.getId();
        int tokenUid = (int) userInfo.get("id");
        if (Objects.equals(name, username)
                && Objects.equals(id, tokenUid)) {
            try {
                UserService.ProfileUpdateResult result = userService.updateNickname(user);
                if (result.kind() == UserService.ProfileUpdateResult.Kind.UPDATED) {
                    return Result.success();
                }
                // 待审 / 被拒 / 锁定期内：都用一个字符串 message 回，前端直接展示 res.message
                return Result.error(result.reason());
            } catch (DuplicateKeyException e) {
                // 并发下由唯一索引兜底：昵称可重复，只有邮箱有唯一约束
                error.put("email", "邮箱已被使用！");
                return Result.error(error);
            }
        }
        error.put("error", "Token不匹配！");
        return Result.error(error);
    }

    /** 上传头像（multipart 表单参数，进入审核队列，通过后生效） */
    @PatchMapping("/updateUserLogo")
    public Result<String> updateUserLogo(@RequestParam("userLogo") MultipartFile userLogo) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        int userId = (int) userInfo.get("id");
        if (userLogo == null || userLogo.isEmpty()) {
            error.put("file", "文件为空！");
            return Result.error(error);
        }
        if (userLogo.getContentType() == null || !userLogo.getContentType().contains("image")) {
            error.put("file", "文件不是图片文件！");
            return Result.error(error);
        }
        if (userLogo.getSize() >= 2 * 1024 * 1024) {
            error.put("file", "文件过大（>=2MB）!");
            return Result.error(error);
        }
        if (userService.isValidFile(userLogo)) {
            // 站长改自己的头像不进审核队列：他是唯一能审的人，审自己没意义，
            // 还会让待审状态卡在自己身上。副作用与审核通过完全一致（见 submitDirect）。
            if ((int) userInfo.get("type") == 0) {
                if (avatarApplyService.submitDirect(userId, userLogo)) {
                    return Result.success();
                }
                error.put("error", "提交失败，请稍后重试！");
                return Result.error(error);
            }
            // 头像不再直接生效：先进 avatar 桶等待审核，通过后才写进 user_pic。
            // 先探一次待审状态，这样「重复提交」和「上传失败」能给出不同的提示，
            // 而不是把存储故障报成「已提交过」。
            AvatarApply mine = avatarApplyService.findMine(userId);
            if (mine != null && mine.getStatus() == AvatarApply.STATUS_PENDING) {
                error.put("error", "已有待审核的头像，请勿重复提交！");
                return Result.error(error);
            }
            if (avatarApplyService.submit(userId, userLogo)) {
                return Result.success();
            }
            error.put("error", "提交失败，请稍后重试！");
            return Result.error(error);
        }
        error.put("file", "不是一个图片文件！");
        return Result.error(error);
    }

    /** 返回当前生效头像的三个字段：对象名、原图地址、缩略图地址。 */
    @GetMapping("/nowLogo")
    public Result<Map<String, Object>> nowLogo() {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        if (userInfo == null) {
            return Result.error("未登录！");
        }
        User user = userService.findUserByName(userInfo.get("name").toString());
        Map<String, Object> error = new HashMap<>();
        if (user == null) {
            error.put("error", "用户不存在!");
            return Result.error(error);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("userPic", user.getUserPic());
        data.put("userPicSrc", user.getUserPicSrc());
        // 缩略图必须是**签名后的地址**：getThumbKey 只拼出对象名（thumb_xxx），
        // 前端拿它当 src 会请求到站点自身的路径下，必然 404 ——
        // findUserByName 已经把带签名的那个填好了，直接用。
        data.put("userPicThumbSrc", user.getUserPicThumbSrc());
        return Result.success(data);
    }

    /** 修改密码（校验原密码，成功后强制重新登录） */
    @PatchMapping("/updatePass")
    public Result<String> updatePass(@RequestBody @Validated UpdatePassPojo passInfo) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        String username = userInfo.get("name").toString();
        String newPass = passInfo.getNewPass();
        String confirmPass = passInfo.getConfirmPass();
        if (!newPass.equals(confirmPass)) {
            error.put("confirmPass", "两次密码不一致！");
            return Result.error(error);
        }
        if (userService.updatePass(username, newPass, passInfo.getOriPass())) {
            if (userService.deleteRedisToken(username)) {
                return Result.success();
            }
            error.put("Redis", "缓存删除失败！");
            return Result.error(error);
        }
        error.put("oriPass", "原密码不正确，或已在其它设备改过，请重新登录后重试");
        return Result.error(error);
    }

    /** 忘记密码：凭重置码设置新密码 */
    @PostMapping("/forgetPass")
    public Result<String> forgetPass(@RequestBody @Validated ForgetPassPojo passInfo,
                                     HttpServletRequest request) {
        Map<String, Object> error = new HashMap<>();
        String email = passInfo.getEmail();
        String emailCode = passInfo.getEmailCode();
        String newPass = passInfo.getPassword();
        String confirmPass = passInfo.getConfirmPassword();

        if (!newPass.equals(confirmPass)) {
            error.put("confirmPassword", "两次密码不一致！");
            return Result.error(error);
        }
        // 邮箱验证码（一次性；输错累计次数，用尽即作废并锁定）
        int codeResult = emailCodeService.verify(email, EmailCodeService.SCENE_RESET, emailCode,
                IPUtil.mixOf(request));
        if (codeResult != EmailCodeService.CODE_OK) {
            error.put("emailCode", emailCodeMessage(codeResult, email, EmailCodeService.SCENE_RESET));
            return Result.error(error);
        }
        User user = userService.findUserByEmail(email);
        if (user == null) {
            error.put("email", "该邮箱尚未注册！");
            return Result.error(error);
        }
        User update = new User();
        update.setUsername(user.getUsername());
        update.setPassword(newPass);
        if (!userService.update(update, 1)) {
            error.put("error", "设置新密码失败！请重试！");
            return Result.error(error);
        }
        // 改密后让其现有登录态失效
        userService.deleteRedisToken(user.getUsername());
        return Result.success();
    }

    /** 退出登录：清 Cookie 与 Redis 中的令牌 */
    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        String username = userInfo.get("name").toString();
        userService.deleteRedisToken(username);
        // 光删 Redis 不够：Cookie 还在浏览器里，得让它一起过期
        CookieUtil.clearToken(response, cookieSecure);
    }

    /** 重置为默认头像 */
    @DeleteMapping("/removeUserLogo")
    public Result<String> removeUserLogo() {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        String username = userInfo.get("name").toString();
        int userId = (int) userInfo.get("id");
        // 有待审头像时不允许重置：重置清掉的是当前生效的头像，待审记录仍留在队列里，
        // 审批通过后又会把那张图设回去——用户会以为「重置没生效」。
        // 前端在待审期间已禁用按钮，这里是不走界面的兜底。
        AvatarApply mine = avatarApplyService.findMine(userId);
        if (mine != null && mine.getStatus() == AvatarApply.STATUS_PENDING) {
            error.put("error", "有待审核的头像，请等审核结果出来再重置！");
            return Result.error(error);
        }
        if (userService.removeUserLogo(username)) {
            return Result.success();
        }
        error.put("error", "重置失败！");
        return Result.error(error);
    }

    /** 账号分页（站长） */
    @GetMapping("/accountManage")
    public Result<PageBean<User>> accountManage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize) {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        Map<String, Object> error = new HashMap<>();
        String username = userInfo.get("name").toString();
        pageNum = PageUtil.normalizePageNum(pageNum);
        pageSize = PageUtil.normalizePageSize(pageSize);
        User master = userService.findUserByName(username);
        if (master != null && master.getType() == 0) {
            int total = userService.findAllAccounts();
            int pageTotal = (int) Math.ceil(total * 1.0 / pageSize);
            if (pageTotal == 0) {
                error.put("error", "无数据！");
                return Result.error(error);
            }
            if (pageNum > pageTotal) {
                pageNum = pageTotal;
            }
            PageBean<User> users = userService.findAllAccountsWithPage(pageNum, pageSize);
            users.setTotal(total);
            return Result.success(users);
        }
        error.put("error", "权限不足！");
        return Result.error(error);
    }

    /** 变更用户身份（站长，需站长密码） */
    @PatchMapping("/changeType")
    public Result<String> changeType(@RequestParam int userId,
                                     @RequestParam int type, String masterPass) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        String username = userInfo.get("name").toString();
        User master = userService.findUserByName(username);
        if (type != 0 && type != 1) {
            error.put("error", "身份错误！");
            return Result.error(error);
        }
        if (master != null && master.getType() == 0) {
            if (!Objects.equals(masterPass, masterPassword)) {
                error.put("error", "站长密码错误！");
                return Result.error(error);
            }
            User waitChange = userService.findUserById(userId);
            if (waitChange == null) {
                error.put("error", "用户不存在！");
                return Result.error(error);
            }
            if (waitChange.getType() == type) {
                error.put("error", "用户已处于该身份！");
                return Result.error(error);
            }
            if (Objects.equals(waitChange.getId(), master.getId())) {
                error.put("error", "不能更改自己的身份！");
                return Result.error(error);
            }
            if (Objects.equals(waitChange.getUsername(), defaultUser)) {
                error.put("error", "更改对象为默认用户！");
                return Result.error(error);
            }
            if (userService.changeType(userId, type)) {
                userService.deleteRedisToken(waitChange.getUsername());
                return Result.success();
            }
            error.put("error", "失败！");
            return Result.error(error);
        }
        error.put("error", "权限不足！");
        return Result.error(error);
    }

    /** 删除账号（站长，需站长密码） */
    @DeleteMapping("/delete")
    public Result<String> deleteUser(@RequestParam int userId, @RequestParam String masterPass) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        String username = userInfo.get("name").toString();
        User master = userService.findUserByName(username);
        if (master != null && master.getType() == 0) {
            if (!Objects.equals(masterPass, masterPassword)) {
                error.put("error", "站长密码错误！");
                return Result.error(error);
            }
            User waitDelete = userService.findUserById(userId);
            if (waitDelete == null) {
                error.put("error", "用户不存在！");
                return Result.error(error);
            }
            if (Objects.equals(waitDelete.getUsername(), defaultUser)) {
                error.put("error", "无法删除默认账号！");
                return Result.error(error);
            }
            if (userId == master.getId()) {
                error.put("error", "无法删除自己的账号！");
                return Result.error(error);
            }
            if (userService.deleteUser(userId)) {
                return Result.success();
            }
            error.put("error", "删除失败！");
            return Result.error(error);
        }
        error.put("error", "权限不足！");
        return Result.error(error);
    }
}
