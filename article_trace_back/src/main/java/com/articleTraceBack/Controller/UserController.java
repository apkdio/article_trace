package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.UserService;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;
    @Value("${Password.registerPass}")
    private String registerPass;
    @Value("${spring.application.admin.defaultUser}")
    private String defaultUser;
    @Value("${Password.masterPass}")
    private String masterPassword;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/loginCheck")
    public void loginCheck() {
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody @Validated RegisterUserPojo user) {
        Map<String, Object> error = new HashMap<>();
        String username = user.getUsername();
        String password = user.getPassword();
        String registerPassword = user.getRegisterPassword();
        String confirmPassword = user.getConfirmPassword();
        int type = user.getType();
        if (!password.equals(confirmPassword)) {
            error.put("confirmPassword", "两次密码不一致！");
            return Result.error(error);
        }
        if (!registerPassword.equals(registerPass) && type == 1) {
            error.put("registerPassword", "注册码不正确！");
            return Result.error(error);
        }
        if (type != 1 && type != 2) {
            error.put("error", "类型错误！");
            return Result.error(error);
        }
        if (userService.findUserByName(username) == null) {
            User registerUser = new User();
            registerUser.setUsername(username);
            registerUser.setPassword(password);
            registerUser.setType(type);
            String resetPassOri = userService.genResetPassOri(10);
            registerUser.setResetPass(resetPassOri);
            if (userService.userRegister(registerUser)) {
                Map<String, Object> map = new HashMap<>();
                map.put("success", resetPassOri);
                return Result.success(map);
            }
            error.put("error", "注册失败！请重试！");
            return Result.error(error);
        }
        error.put("username", "用户名已占用！");
        return Result.error(error);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Validated(User.login.class) User user) {
        Map<String, Object> error = new HashMap<>();
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
                String lastLogin;
                if (result.getLastLogin() == null) {
                    lastLogin = "暂无";
                } else {
                    lastLogin = result.getLastLogin().format(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    );
                }
                userService.updateLoginTime(username);
                Map<String, Object> loginResult = new HashMap<>();
                loginResult.put("token", token);
                loginResult.put("lastLogin", lastLogin);
                return Result.success(loginResult);
            }
            error.put("password", "密码错误！");
            return Result.error(error);
        }
        error.put("username", "用户名不存在！");
        return Result.error(error);
    }

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
            String nickName = user.getNickname();
            User userByNickName = userService.findUserByNickName(nickName);
            if (userByNickName != null) {
                if (userByNickName.getId() != id) {
                    error.put("nickname", "昵称已存在！");
                    return Result.error(error);
                }
            }
            if (userService.update(user, 0)) {
                return Result.success();
            }
            error.put("error", "更新失败！请重试！");
            return Result.error(error);
        }
        error.put("error", "Token不匹配！");
        return Result.error(error);
    }

    // 使用Param形式传递
    @PatchMapping("/updateUserLogo")
    public Result<String> updateUserLogo(@RequestParam("userLogo") MultipartFile userLogo) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        String username = userInfo.get("name").toString();
        if (userLogo == null || userLogo.isEmpty()) {
            error.put("file", "文件为空！");
            return Result.error(error);
        }
        if (userLogo.getContentType() == null || !userLogo.getContentType().contains("image")) {
            error.put("file", "文件不是图片文件！");
            return Result.error(error);
        }
        if (userLogo.getSize() >= 5 * 1024 * 1024) {
            error.put("file", "文件过大（>=5MB）!");
            return Result.error(error);
        }
        if (userService.isValidFile(userLogo)) {
            if (userService.upload(userLogo, username)) {
                return Result.success();
            }
            error.put("error", "上传失败！");
            return Result.error(error);
        }
        error.put("file", "不是一个图片文件！");
        return Result.error(error);
    }

    @PatchMapping("/updatePass")
    public Result<String> updatePass(@RequestBody @Validated UpdatePassPojo passInfo) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        String username = userInfo.get("name").toString();
        String oriPass = passInfo.getOriPass();
        String newPass = passInfo.getNewPass();
        String confirmPass = passInfo.getConfirmPass();
        if (userService.checkPass(oriPass, username, "password")) {
            if (newPass.equals(confirmPass)) {
                User user = new User();
                user.setUsername(username);
                user.setPassword(newPass);
                if (userService.update(user, 1)) {
                    if (userService.deleteRedisToken(username)) {
                        return Result.success();
                    }
                    error.put("Redis", "缓存删除失败！");
                    return Result.error(error);
                }
                error.put("error", "修改失败！请重试！");
                return Result.error(error);
            }
            error.put("confirmPass", "两次密码不一致！");
            return Result.error(error);
        }
        error.put("oriPass", "原密码不正确");
        return Result.error(error);
    }

    @PostMapping("/forgetPass")
    public Result<String> forgetPass(@RequestBody @Validated ForgetPassPojo passInfo) {
        Map<String, Object> error = new HashMap<>();
        String username = passInfo.getUsername();
        if (userService.findUserByName(username) != null) {
            String securePass = passInfo.getResetPassword();
            if (userService.checkPass(securePass, username, "reset_pass")) {
                String newPass = passInfo.getPassword();
                String confirmPass = passInfo.getConfirmPassword();
                if (newPass.equals(confirmPass)) {
                    User user = new User();
                    user.setUsername(username);
                    user.setPassword(newPass);
                    if (userService.update(user, 1)) {
                        return Result.success();
                    }
                    error.put("error", "设置新密码失败！请重试！");
                    return Result.error(error);
                }
                error.put("confirmPassword", "两次密码不一致！");
                return Result.error(error);
            }
            error.put("resetPassword", "重置码错误！");
            return Result.error(error);
        }
        error.put("username", "用户名不存在！");
        return Result.error(error);
    }

    @GetMapping("/logout")
    public void logout() {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        String username = userInfo.get("name").toString();
        userService.deleteRedisToken(username);
    }

    @DeleteMapping("/removeUserLogo")
    public Result<String> removeUserLogo() {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        String username = userInfo.get("name").toString();
        if (userService.removeUserLogo(username)) {
            return Result.success();
        }
        error.put("error", "重置失败！");
        return Result.error(error);
    }

    @GetMapping("/accountManage")
    public Result<PageBean<User>> accountManage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize) {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        Map<String, Object> error = new HashMap<>();
        String username = userInfo.get("name").toString();
        if (pageNum <= 0) {
            pageNum = 1;
        }
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
