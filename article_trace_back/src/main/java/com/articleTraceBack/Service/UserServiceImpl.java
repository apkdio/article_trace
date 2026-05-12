package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.BcryptUtils;
import com.articleTraceBack.Utils.GenResetPass;
import com.articleTraceBack.Utils.JwtUtil;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.micrometer.common.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {
    private final StringRedisTemplate stringRedisTemplate;
    @Value("${JWT.longTime}")
    private long longTime;
    @Value("${JWT.shortTime}")
    private long shortTime;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final GenResetPass genSecurePass;
    private final RustFsUtil rustFsUtil;
    private final ArticleService articleService;

    public UserServiceImpl(UserMapper userMapper, GenResetPass genSecurePass,
                           JwtUtil jwtUtil, RustFsUtil rustFsUtil,
                           StringRedisTemplate stringRedisTemplate, ArticleService articleService) {
        this.userMapper = userMapper;
        this.articleService = articleService;
        this.genSecurePass = genSecurePass;
        this.jwtUtil = jwtUtil;
        this.rustFsUtil = rustFsUtil;
        this.stringRedisTemplate = stringRedisTemplate;
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
        } else {
            user.setUserPicSrc("");
        }
        return user;
    }

    @Override
    public boolean userRegister(User user) {
        String encoderPass = BcryptUtils.encodePass(user.getPassword());
        String resetPass = BcryptUtils.encodePass(user.getResetPass());
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setPassword(encoderPass);
        user.setResetPass(resetPass);
        return userMapper.insert(user) == 1;
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
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username).select(checkColumn);
        String userOriPass = (String) userMapper.selectObjs(queryWrapper).getFirst();
        return BcryptUtils.checkPass(oriPass, userOriPass);
    }

    @Override
    public String genResetPassOri(int i) {
        return genSecurePass.generatePass(i);
    }

    @Override
    public boolean isValidFile(MultipartFile file) {
        String contentType = file.getContentType();
        String[] allowedTypes = {"image/jpeg", "image/png", "image/gif", "image/webp"};

        if (contentType == null) {
            return false;
        }

        for (String type : allowedTypes) {
            if (contentType.startsWith(type)) {
                return true;
            }
        }

        String filename = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();
        return filename.endsWith(".jpg") || filename.endsWith(".jpeg")
                || filename.endsWith(".png") || filename.endsWith(".gif")
                || filename.endsWith(".webp") || filename.endsWith(".bmp");
    }

    @Override
    public boolean upload(MultipartFile userLogo, String username) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            return false;
        }
        String extension = Objects.requireNonNull(userLogo.getOriginalFilename()).substring(userLogo.getOriginalFilename().lastIndexOf("."));
        String rawName = user.getUserPic();
        String fileName = System.currentTimeMillis() + username + "logo" + extension;
        if (StringUtils.isBlank(rawName)) {
            if (rustFsUtil.upload(userLogo, "image", fileName)) {
                UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("username", username)
                        .set("user_pic", fileName)
                        .set("update_time", LocalDateTime.now());
                return userMapper.update(updateWrapper) == 1;
            }
            return false;
        }
        if (rustFsUtil.upload(userLogo, "image", fileName)) {
            UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("username", username)
                    .set("user_pic", fileName)
                    .set("update_time", LocalDateTime.now());
            if (userMapper.update(updateWrapper) == 1) {
                return rustFsUtil.delete(rawName, "image");
            }
            return false;
        }
        return false;
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
            System.out.println(e.getMessage());
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
        if (!StringUtils.isBlank(userPic)) {
            stringRedisTemplate.delete(userPic);
            if (rustFsUtil.delete(userPic, "image")) {
                UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("username", username)
                        .set("user_pic", "");
                return userMapper.update(updateWrapper) == 1;
            }
            return false;
        }
        return false;
    }

    @Override
    public int findAllAccounts() {
        return userMapper.selectList(null).size();
    }

    @Override
    public boolean deleteUser(int id) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        return userMapper.delete(queryWrapper) == 1;
    }

    @Override
    public PageBean<User> findAllAccountsWithPage(int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        PageBean<User> usersData = new PageBean<>();
        List<User> users = userMapper.findAllAccountsWithPage(offset, pageSize);
        for (User user : users) {
            if (!StringUtils.isBlank(user.getUserPic())) user.setUserPicSrc(rustFsUtil.getPciUrl(user.getUserPic()));
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
        queryWrapper.eq("nickname", nickName);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public User findUserById(int userId) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", userId);
        return userMapper.selectOne(queryWrapper);
    }
}
