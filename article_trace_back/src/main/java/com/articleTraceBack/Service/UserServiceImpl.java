package com.articleTraceBack.Service;

import lombok.extern.slf4j.Slf4j;
import com.articleTraceBack.Utils.BcryptUtils;
import com.articleTraceBack.Utils.FileCheckUtil;
import com.articleTraceBack.Utils.JwtUtil;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.mapper.AuthorApplyMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.AuthorApply;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.constant.RedisKeys;
import com.articleTraceBack.pojo.ProfileApply;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.micrometer.common.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl implements UserService {
    private final StringRedisTemplate stringRedisTemplate;
    /** 默认昵称前缀与随机后缀字符集（去掉易混淆的 0/o/1/l） */
    private static final String NICKNAME_PREFIX = "文迹探索者";
    private static final String NICKNAME_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int NICKNAME_SUFFIX_LEN = 6;
    private static final SecureRandom NICKNAME_RANDOM = new SecureRandom();
    /** 改名锁定期：成功改名后多少天内不能再改 */
    private static final int NICKNAME_LOCK_DAYS = 7;

    @Value("${JWT.longTime}")
    private long longTime;
    @Value("${JWT.shortTime}")
    private long shortTime;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RustFsUtil rustFsUtil;
    private final ArticleService articleService;
    private final AgentSessionService agentSessionService;
    private final AuthorApplyMapper authorApplyMapper;
    private final ProfileGuard profileGuard;
    private final ProfileApplyService profileApplyService;

    public UserServiceImpl(UserMapper userMapper,
                           JwtUtil jwtUtil, RustFsUtil rustFsUtil,
                           StringRedisTemplate stringRedisTemplate, ArticleService articleService,
                           AgentSessionService agentSessionService, AuthorApplyMapper authorApplyMapper,
                           ProfileGuard profileGuard, ProfileApplyService profileApplyService) {
        this.userMapper = userMapper;
        this.articleService = articleService;
        this.jwtUtil = jwtUtil;
        this.rustFsUtil = rustFsUtil;
        this.stringRedisTemplate = stringRedisTemplate;
        this.agentSessionService = agentSessionService;
        this.authorApplyMapper = authorApplyMapper;
        this.profileGuard = profileGuard;
        this.profileApplyService = profileApplyService;
    }

    @Override
    public User findUserByName(String name) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", name);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            return null;
        }
        if (!StringUtils.isBlank(user.getUserPic())) {
            user.setUserPicSrc(rustFsUtil.getPciUrl(user.getUserPic()));
            user.setUserPicThumbSrc(rustFsUtil.getThumbUrl(user.getUserPic()));
        } else {
            user.setUserPicSrc("");
            user.setUserPicThumbSrc("");
        }
        return user;
    }

    @Override
    public boolean userRegister(User user) {
        String encoderPass = BcryptUtils.encodePass(user.getPassword());
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setPassword(encoderPass);
        // 注册不要求填昵称，这里生成一个默认的（昵称允许重复，无需保证唯一）
        if (StringUtils.isBlank(user.getNickname())) {
            user.setNickname(genDefaultNickname());
        }
        return userMapper.insert(user) == 1;
    }

    /** 默认昵称：文迹探索者 + 6 位随机串，如「文迹探索者k7m2x9」 */
    private String genDefaultNickname() {
        StringBuilder sb = new StringBuilder(NICKNAME_PREFIX);
        for (int i = 0; i < NICKNAME_SUFFIX_LEN; i++) {
            sb.append(NICKNAME_CHARS.charAt(NICKNAME_RANDOM.nextInt(NICKNAME_CHARS.length())));
        }
        return sb.toString();
    }

    @Override
    public String genToken(Map<String, Object> map, int type) {
        return jwtUtil.genToken(map, type);
    }

    @Override
    public boolean update(User user, int type) {
        User newUser = new User();
        if (type == 0) {
            newUser.setNickname(user.getNickname());
            newUser.setEmail(user.getEmail());
        } else if (type == 1) {
            String encodePass = BcryptUtils.encodePass(user.getPassword());
            newUser.setPassword(encodePass);
        } else {
            return false;
        }
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("username", user.getUsername());
        newUser.setUpdateTime(LocalDateTime.now());
        return userMapper.update(newUser, updateWrapper) == 1;
    }

    @Override
    public void updateLoginTime(String name) {
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("username", name)
                .set("last_login", LocalDateTime.now());
        userMapper.update(null, updateWrapper);
    }


    @Override
    public boolean checkPass(String oriPass, String username, String checkColumn) {
        String stored = selectColumn(username, checkColumn);
        return stored != null && BcryptUtils.checkPass(oriPass, stored);
    }

    @Override
    public boolean updatePass(String username, String newPass, String oriPass) {
        // 把当前哈希写进 WHERE：两人同时改密时，后写者更新 0 行，
        // 而不是静默覆盖先写者的新密码（原先两端都返回成功）。
        String current = selectColumn(username, "password");
        if (current == null || !BcryptUtils.checkPass(oriPass, current)) {
            return false;
        }
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("username", username)
                .eq("password", current)
                .set("password", BcryptUtils.encodePass(newPass))
                .set("update_time", LocalDateTime.now());
        return userMapper.update(null, updateWrapper) == 1;
    }

    /** 取单列字符串；用户不存在（或并发注销）时 selectObjs 为空，不能直接 getFirst 取 */
    private String selectColumn(String username, String column) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username).select(column);
        List<Object> values = userMapper.selectObjs(queryWrapper);
        if (values == null || values.isEmpty() || values.getFirst() == null) {
            return null;
        }
        return (String) values.getFirst();
    }

    @Override
    public ProfileUpdateResult updateNickname(User user) {
        User current = userMapper.selectById(user.getId());
        if (current == null) {
            return new ProfileUpdateResult(ProfileUpdateResult.Kind.REJECTED, "用户不存在", "nickname");
        }
        if (Objects.equals(current.getNickname(), user.getNickname())) {
            // 只改了邮箱（或什么都没改）：不碰规则、不碰锁定期，走原来的路径
            return update(user, 0)
                    ? new ProfileUpdateResult(ProfileUpdateResult.Kind.UPDATED, null, null)
                    : new ProfileUpdateResult(ProfileUpdateResult.Kind.REJECTED, "修改失败！请重试！", "error");
        }
        String lockKey = RedisKeys.PROFILE_NICKNAME_LOCK + user.getId();
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
            return new ProfileUpdateResult(ProfileUpdateResult.Kind.LOCKED, "昵称 7 天内只能改一次", "nickname");
        }
        ProfileGuard.Verdict verdict = profileGuard.checkNickname(user.getNickname());
        if (verdict.kind() == ProfileGuard.Verdict.Kind.FORMAT) {
            // 无效输入：直接拒，不给待审队列添垃圾
            return new ProfileUpdateResult(ProfileUpdateResult.Kind.REJECTED, verdict.reason(), "nickname");
        }
        if (verdict.kind() == ProfileGuard.Verdict.Kind.CONTENT) {
            // 内容类：保留旧值、落待审；本次提交里的邮箱照常写，别让用户白改一次
            String awaiting = user.getNickname();
            user.setNickname(current.getNickname());
            update(user, 0);
            boolean submitted = profileApplyService.submit(user.getId(), ProfileApply.TYPE_NICKNAME, awaiting);
            return new ProfileUpdateResult(ProfileUpdateResult.Kind.PENDING,
                    submitted ? "昵称已提交审核，通过后自动生效" : "昵称已有一条待审申请，请等审核结果",
                    "nickname");
        }
        if (!update(user, 0)) {
            return new ProfileUpdateResult(ProfileUpdateResult.Kind.REJECTED, "修改失败！请重试！", "error");
        }
        stringRedisTemplate.opsForValue().set(lockKey, "1", NICKNAME_LOCK_DAYS, TimeUnit.DAYS);
        // 此前若留着一条待审，它一旦被批准就会覆盖掉刚改好的名字
        profileApplyService.cancelPending(user.getId(), ProfileApply.TYPE_NICKNAME);
        return new ProfileUpdateResult(ProfileUpdateResult.Kind.UPDATED, null, null);
    }

    @Override
    public boolean isValidFile(MultipartFile file) {
        // 统一走 FileCheckUtil：此前只要 MIME 匹配就直接放行、不再看扩展名，
        // 而扩展名才决定最终的对象名，MIME 是客户端可伪造的。
        return FileCheckUtil.isAcceptableImage(file);
    }

    @Override
    public String updateUserPic(int userId, String newPic) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        String oldPic = user.getUserPic();
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", userId)
                .set("user_pic", newPic)
                .set("update_time", LocalDateTime.now());
        if (userMapper.update(updateWrapper) != 1) {
            return null;
        }
        return oldPic;
    }

    @Override
    public boolean setRedisToken(String username, String token, int type) {
        long expireTime;
        if (type == 1) {
            expireTime = longTime;
        } else {
            expireTime = shortTime;
        }
        try {
            stringRedisTemplate.opsForValue().set(username, token, expireTime, TimeUnit.MILLISECONDS);
            return stringRedisTemplate.opsForValue().get(username) != null;
        } catch (Exception e) {
            log.error("set redis token failed", e);
            return false;
        }
    }

    @Override
    public boolean deleteRedisToken(String username) {
        stringRedisTemplate.delete(username);
        return stringRedisTemplate.opsForValue().get(username) == null;
    }

    @Override
    public int getAllArticles(int uid) {
        return articleService.findAllArticlesWithConditions(uid, null, null, null);
    }

    @Override
    public boolean removeUserLogo(String username) {
        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            return false;
        }
        String userPic = user.getUserPic();
        if (StringUtils.isBlank(userPic)) {
            return false;
        }
        // 先清 DB 指向，成功后再删对象：反过来的话，写库失败会让 DB 指向一个已删除的对象，
        // 用户头像会变成裂图且自己恢复不了（与文章正文的处理保持一致）。
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("username", username).set("user_pic", "");
        if (userMapper.update(updateWrapper) != 1) {
            return false;
        }
        if (!rustFsUtil.delete(userPic, "image")) {
            log.warn("user logo object delete failed, may be orphan: username={}, key={}", username, userPic);
        }
        return true;
    }

    @Override
    public int findAllAccounts() {
        return userMapper.selectList(null).size();
    }

    @Override
    public User findUserByEmail(String email) {
        if (StringUtils.isBlank(email)) {
            return null;
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("email", email.trim());
        List<User> users = userMapper.selectList(queryWrapper);
        // 邮箱已有唯一约束，limit 1 仅作防御
        return users.isEmpty() ? null : users.getFirst();
    }

    @Override
    public boolean deleteUser(int id) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        boolean deleted = userMapper.delete(queryWrapper) == 1;
        if (deleted) {
            // 账号注销后，同步清理该用户在 agent 侧的会话数据与归属索引
            agentSessionService.clearAll(String.valueOf(id));
            // 以及其作者申请：留着会变成没有对应用户的悬挂记录，
            // 站长列表里查不到申请人、角标也清不掉
            QueryWrapper<AuthorApply> applyWrapper = new QueryWrapper<>();
            applyWrapper.eq("user_id", id);
            int removed = authorApplyMapper.delete(applyWrapper);
            if (removed > 0) {
                log.info("removed author applies on user deletion: userId={}, count={}", id, removed);
            }
        }
        return deleted;
    }

    @Override
    public PageBean<User> findAllAccountsWithPage(int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        PageBean<User> usersData = new PageBean<>();
        List<User> users = userMapper.findAllAccountsWithPage(offset, pageSize);
        for (User user : users) {
            if (!StringUtils.isBlank(user.getUserPic())) {
                user.setUserPicSrc(rustFsUtil.getPciUrl(user.getUserPic()));
                user.setUserPicThumbSrc(rustFsUtil.getThumbUrl(user.getUserPic()));
            }
        }
        usersData.setItems(users);
        return usersData;
    }

    @Override
    public boolean changeType(int userId, int type) {
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", userId).set("type", type);
        return userMapper.update(updateWrapper) == 1;
    }

    @Override
    public User findUserByNickName(String nickName) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        // nickname 无唯一索引，重复时取首条，避免 selectOne 抛 TooManyResultsException
        queryWrapper.eq("nickname", nickName).last("limit 1");
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public User findUserById(int userId) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", userId);
        return userMapper.selectOne(queryWrapper);
    }
}
