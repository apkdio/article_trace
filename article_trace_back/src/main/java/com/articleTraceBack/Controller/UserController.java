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
import java.util.List;
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

    /** 头像上限：与前端 utils/upload.js 的 MAX_IMAGE_SIZE 同口径，`>=` 即拒（恰好 2MB 也不放行） */
    private static final long MAX_LOGO_SIZE = 2L * 1024 * 1024;

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
            throw new BusinessException("请求过于频繁，请稍后再试！");
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
            throw new BusinessException("图形验证码错误或已过期！");
        }
        // 2. 邮箱格式
        if (email == null || email.isBlank()) {
            throw new BusinessException("邮箱不能为空！");
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new BusinessException("邮箱格式不正确！");
        }
        // 3. 场景校验
        if (EmailCodeService.SCENE_RESET.equals(scene)) {
            if (userService.findUserByEmail(email.trim()) == null) {
                throw new BusinessException("该邮箱尚未注册！");
            }
        } else {
            scene = EmailCodeService.SCENE_REGISTER;
        }
        // 4. 注册场景在单用户态下直接拒绕：注册接口关了但验证码照发，等于没关
        if (EmailCodeService.SCENE_REGISTER.equals(scene) && !siteFeatures.isRegisterEnabled()) {
            throw new BusinessException("本站暂不开放注册！");
        }
        // 5. 发码（锁定中的邮箱会被 send 拒绕，这里先给出带剩余时长的提示）
        long lockedSeconds = emailCodeService.lockRemainingSeconds(email, scene);
        if (lockedSeconds > 0) {
            throw new BusinessException("尝试次数过多，请 " + minutesOf(lockedSeconds) + " 分钟后再试！");
        }
        if (!emailCodeService.send(email, scene)) {
            throw new BusinessException("发送过于频繁，请稍后再试！");
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
        // 单用户态下关闭注册（个人备案要求：开放注册属于交互式服务，且邮箱注册不是实名）
        if (!siteFeatures.isRegisterEnabled()) {
            throw new BusinessException("本站暂不开放注册！");
        }
        String username = user.getUsername();
        String password = user.getPassword();
        String confirmPassword = user.getConfirmPassword();
        String email = user.getEmail();
        String emailCode = user.getEmailCode();

        if (!password.equals(confirmPassword)) {
            throw new BusinessException("confirmPassword", "两次密码不一致！");
        }
        // 先查占用，避免无效请求白白消耗验证码
        if (userService.findUserByName(username) != null) {
            throw new BusinessException("username", "用户名已占用！");
        }
        if (userService.findUserByEmail(email) != null) {
            throw new BusinessException("email", "该邮箱已被注册！");
        }
        // 邮箱验证码：一次性；输错会累计次数，用尽即作废该码并锁定邮箱
        int codeResult = emailCodeService.verify(email, EmailCodeService.SCENE_REGISTER, emailCode,
                IPUtil.mixOf(request));
        if (codeResult != EmailCodeService.CODE_OK) {
            throw new BusinessException("emailCode", emailCodeMessage(codeResult, email, EmailCodeService.SCENE_REGISTER));
        }
        // 注册一律为读者；成为作者请走「申请成为作者」
        User registerUser = new User();
        registerUser.setUsername(username);
        registerUser.setPassword(password);
        registerUser.setEmail(email);
        registerUser.setType(ROLE_READER);
        try {
            userService.userRegister(registerUser);
        } catch (DuplicateKeyException e) {
            // 并发下两个请求可能同时通过查重，由唯一索引（uk_email）兜底
            throw new BusinessException("email", "该邮箱已被注册！");
        }
        return Result.success();
    }

    /** 登录：校验验证码与失败次数后签发 JWT */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Validated(User.login.class) User user,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        // 限流两个维度：clientKey 是 IP+UA 指纹（请求头可伪造，仅辅助），username 才是防爆破的抓手
        String clientKey = IPUtil.mixOf(request);
        String username = user.getUsername();

        // 1. 黑名单：连续失败过多，直接拒绝
        long blockedSeconds = loginAttemptService.blockRemainingSeconds(username, clientKey);
        if (blockedSeconds > 0) {
            long minutes = minutesOf(blockedSeconds);
            throw new BusinessException("error", "登录尝试过于频繁，请 " + minutes + " 分钟后再试！",
                    Map.of("blocked", minutes));
        }

        // 2. 失败超过阈值后必须出示图形验证码；图形码错误不计入失败次数
        boolean needCaptcha = loginAttemptService.needCaptcha(username, clientKey);
        if (needCaptcha && !captchaService.verify(user.getCaptchaId(), user.getCaptchaCode())) {
            throw new BusinessException("captcha", "图形验证码错误或已过期！", Map.of("needCaptcha", true));
        }

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
                    throw new BusinessException("error", "Redis 服务异常！");
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
                loginAttemptService.clear(username, clientKey);
                Map<String, Object> loginResult = new HashMap<>();
                loginResult.put("lastLogin", lastLogin);
                return Result.success(loginResult);
            }
            throw loginFailure(username, clientKey, "password", "密码错误！");
        }
        throw loginFailure(username, clientKey, "username", "用户名不存在！");
    }

    /**
     * 记一次登录失败，并把「下次是否需要图形码 / 距离锁定还剩几次」带给前端。
     */
    private BusinessException loginFailure(String username, String clientKey, String field, String message) {
        long failures = loginAttemptService.recordFailure(username, clientKey);
        return new BusinessException(field, message, Map.of(
                "needCaptcha", failures >= LoginAttemptService.CAPTCHA_THRESHOLD,
                "remaining", Math.max(0, LoginAttemptService.BLOCK_THRESHOLD - failures)));
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
     * 秒数向上取整成分钟
     */
    private long minutesOf(long seconds) {
        return Math.max(1, (seconds + 59) / 60);
    }

    /** 当前登录用户信息 */
    @GetMapping("/userInfo")
    public Result<Object> userInfo() {
        CurrentUser.Session me = CurrentUser.require();
        String username = me.username();
        int uid = me.id();
        User user = userService.findUserByName(username);
        if (user == null) {
            throw new BusinessException("error", "未找到用户！");
        }
        user.setArticlesTotal(userService.countPublishedArticles(uid));
        return Result.success(user);
    }

    /** 修改昵称 / 邮箱 */
    @PatchMapping("/update")
    public Result<Map<String, String>> update(@RequestBody @Validated(User.update.class) User user) {
        CurrentUser.Session me = CurrentUser.require();
        String name = user.getUsername();
        String username = me.username();
        int id = user.getId();
        int tokenUid = me.id();
        if (Objects.equals(name, username)
                && Objects.equals(id, tokenUid)) {
            try {
                UserService.ProfileUpdateResult result = userService.updateProfile(user);
                if (result.kind() == UserService.ProfileUpdateResult.Kind.UPDATED) {
                    return Result.success(Map.of("status", "UPDATED"));
                }
                if (result.kind() == UserService.ProfileUpdateResult.Kind.PENDING) {
                    // 待审不是失败：邮箱等字段已生效，昵称/个签进了审核队列。
                    // 仍然分清状态——只回「成功」的话用户会以为昵称已经改了
                    return Result.success(Map.of("status", "PENDING", "message", result.reason()));
                }
                // 按服务端给的字段落位：个签的错误不能显示在昵称输入框下；没有字段名才落到通用 key
                String field = result.field() == null || result.field().isBlank() ? "error" : result.field();
                throw new BusinessException(field, result.reason());
            } catch (DuplicateKeyException e) {
                // 并发下由唯一索引兜底：昵称可重复，只有邮箱有唯一约束
                throw new BusinessException("email", "邮箱已被使用！");
            }
        }
        throw new BusinessException("error", "Token不匹配！");
    }

    /** 上传头像（multipart 表单参数，进入审核队列，通过后生效） */
    @PatchMapping("/updateUserLogo")
    public Result<String> updateUserLogo(@RequestParam("userLogo") MultipartFile userLogo) {
        int userId = CurrentUser.require().id();
        if (userLogo == null || userLogo.isEmpty()) {
            throw new BusinessException("file", "文件为空！");
        }
        if (userLogo.getContentType() == null || !userLogo.getContentType().contains("image")) {
            throw new BusinessException("file", "文件不是图片文件！");
        }
        if (userLogo.getSize() >= MAX_LOGO_SIZE) {
            throw new BusinessException("file", "文件过大（不能超过 " + (MAX_LOGO_SIZE / 1024 / 1024) + "MB）！");
        }
        if (!userService.isValidFile(userLogo)) {
            throw new BusinessException("file", "不是一个图片文件！");
        }
        // 站长改自己的头像不进审核队列：他是唯一能审的人，审自己没意义，
        // 还会让待审状态卡在自己身上。副作用与审核通过完全一致（见 submitDirect）。
        if (CurrentUser.require().type() == 0) {
            avatarApplyService.submitDirect(userId, userLogo);
            return Result.success();
        }
        // 头像不再直接生效：先进 avatar 桶等待审核，通过后才写进 user_pic。
        // 先探一次待审状态，这样「重复提交」和「上传失败」能给出不同的提示，
        // 而不是把存储故障报成「已提交过」。
        AvatarApply mine = avatarApplyService.findMine(userId);
        if (mine != null && mine.getStatus() == AvatarApply.STATUS_PENDING) {
            throw new BusinessException("error", "已有待审核的头像，请勿重复提交！");
        }
        avatarApplyService.submit(userId, userLogo);
        return Result.success();
    }

    /** 返回当前生效头像的三个字段：对象名、原图地址、缩略图地址。 */
    @GetMapping("/nowLogo")
    public Result<Map<String, Object>> nowLogo() {
        User user = userService.findUserByName(CurrentUser.require().username());
        if (user == null) {
            throw new BusinessException("error", "用户不存在!");
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
    public Result<String> updatePass(@RequestBody @Validated UpdatePassPojo passInfo,
                                     HttpServletResponse response) {
        String username = CurrentUser.require().username();
        String newPass = passInfo.getNewPass();
        String confirmPass = passInfo.getConfirmPass();
        if (!newPass.equals(confirmPass)) {
            throw new BusinessException("confirmPass", "两次密码不一致！");
        }
        userService.updatePass(username, newPass, passInfo.getOriPass());
        if (!endSession(username, response)) {
            throw new BusinessException("Redis", "缓存删除失败！");
        }
        return Result.success();
    }

    /** 忘记密码：使用邮件验证码设置新密码 */
    @PostMapping("/forgetPass")
    public Result<String> forgetPass(@RequestBody @Validated ForgetPassPojo passInfo,
                                     HttpServletRequest request,HttpServletResponse response) {
        String email = passInfo.getEmail();
        String emailCode = passInfo.getEmailCode();
        String newPass = passInfo.getPassword();
        String confirmPass = passInfo.getConfirmPassword();

        if (!newPass.equals(confirmPass)) {
            throw new BusinessException("confirmPassword", "两次密码不一致！");
        }
        // 邮箱验证码（一次性；输错累计次数，用尽即作废并锁定）
        int codeResult = emailCodeService.verify(email, EmailCodeService.SCENE_RESET, emailCode,
                IPUtil.mixOf(request));
        if (codeResult != EmailCodeService.CODE_OK) {
            throw new BusinessException("emailCode", emailCodeMessage(codeResult, email, EmailCodeService.SCENE_RESET));
        }
        User user = userService.findUserByEmail(email);
        if (user == null) {
            throw new BusinessException("email", "该邮箱尚未注册！");
        }
        User update = new User();
        update.setUsername(user.getUsername());
        update.setPassword(newPass);
        if (!userService.update(update, 1)) {
            throw new BusinessException("error", "设置新密码失败！请重试！");
        }
        // 改密后让其现有登录态失效：Redis 令牌与浏览器 Cookie 一起清
        endSession(user.getUsername(), response);
        return Result.success();
    }

    /** 退出登录：清 Cookie 与 Redis 中的令牌 */
    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        String username = CurrentUser.require().username();
        endSession(username, response);
    }

    /**
     * 让某个账号的现有会话立即失效：删 Redis 令牌，并清掉请求方浏览器里的 Cookie。
     * 退出 / 改密 / 找回密码共用同一套；未登录时清 Cookie 也无害（只是下发一个过期 Cookie）。
     *
     * @return Redis 令牌是否删除成功；失败时调用方决定要不要提示「缓存未清除」
     */
    private boolean endSession(String username, HttpServletResponse response) {
        boolean cleared = userService.deleteRedisToken(username);
        CookieUtil.clearToken(response, cookieSecure);
        return cleared;
    }

    /** 重置为默认头像 */
    @DeleteMapping("/removeUserLogo")
    public Result<String> removeUserLogo() {
        CurrentUser.Session me = CurrentUser.require();
        String username = me.username();
        int userId = me.id();
        // 有待审头像时不允许重置：重置清掉的是当前生效的头像，待审记录仍留在队列里，
        // 审批通过后又会把那张图设回去——用户会以为「重置没生效」。
        // 前端在待审期间已禁用按钮，这里是不走界面的兜底。
        AvatarApply mine = avatarApplyService.findMine(userId);
        if (mine != null && mine.getStatus() == AvatarApply.STATUS_PENDING) {
            throw new BusinessException("error", "有待审核的头像，请等审核结果出来再重置！");
        }
        userService.removeUserLogo(username);
        return Result.success();
    }

    /** 账号分页（站长） */
    @GetMapping("/accountManage")
    public Result<PageBean<User>> accountManage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize) {
        CurrentUser.Session me = CurrentUser.require();
        String username = me.username();
        pageNum = PageUtil.normalizePageNum(pageNum);
        pageSize = PageUtil.normalizePageSize(pageSize);
        User master = userService.findUserByName(username);
        if (master == null || master.getType() != 0) {
            throw new BusinessException("error", "权限不足！");
        }
        int total = userService.findAllAccounts();
        int pageTotal = PageUtil.pageTotal(total, pageSize);
        if (pageTotal == 0) {
            // 空列表不是错误：返回空页，前端渲染空表
            return Result.success(new PageBean<>(0, List.of()));
        }
        if (pageNum > pageTotal) {
            pageNum = pageTotal;
        }
        PageBean<User> users = userService.findAllAccountsWithPage(pageNum, pageSize);
        users.setTotal(total);
        return Result.success(users);
    }

    /** 变更用户身份（站长，需站长密码） */
    @PatchMapping("/changeType")
    public Result<String> changeType(@RequestParam int userId,
                                     @RequestParam int type, String masterPass) {
        String username = CurrentUser.require().username();
        User master = userService.findUserByName(username);
        if (type != 0 && type != 1) {
            throw new BusinessException("error", "身份错误！");
        }
        if (master == null || master.getType() != 0) {
            throw new BusinessException("error", "权限不足！");
        }
        if (!Objects.equals(masterPass, masterPassword)) {
            throw new BusinessException("error", "站长密码错误！");
        }
        User waitChange = userService.findUserById(userId);
        if (waitChange == null) {
            throw new BusinessException("error", "用户不存在！");
        }
        if (waitChange.getType() == type) {
            throw new BusinessException("error", "用户已处于该身份！");
        }
        if (Objects.equals(waitChange.getId(), master.getId())) {
            throw new BusinessException("error", "不能更改自己的身份！");
        }
        if (Objects.equals(waitChange.getUsername(), defaultUser)) {
            throw new BusinessException("error", "更改对象为默认用户！");
        }
        userService.changeType(userId, type);
        userService.deleteRedisToken(waitChange.getUsername());
        return Result.success();
    }

    /** 删除账号（站长，需站长密码） */
    @DeleteMapping("/delete")
    public Result<String> deleteUser(@RequestParam int userId, @RequestParam String masterPass) {
        String username = CurrentUser.require().username();
        User master = userService.findUserByName(username);
        if (master == null || master.getType() != 0) {
            throw new BusinessException("error", "权限不足！");
        }
        if (!Objects.equals(masterPass, masterPassword)) {
            throw new BusinessException("error", "站长密码错误！");
        }
        User waitDelete = userService.findUserById(userId);
        if (waitDelete == null) {
            throw new BusinessException("error", "用户不存在！");
        }
        if (Objects.equals(waitDelete.getUsername(), defaultUser)) {
            throw new BusinessException("error", "无法删除默认账号！");
        }
        if (userId == master.getId()) {
            throw new BusinessException("error", "无法删除自己的账号！");
        }
        userService.deleteUser(userId);
        return Result.success();
    }
}
