package com.articleTraceBack.Service;

import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface UserService {
    User findUserByName(String name);

    boolean userRegister(User user);

    String genToken(Map<String, Object> map, int type);

    boolean update(User user, int type);

    void updateLoginTime(String name);

    boolean checkPass(String oriPass, String username, String checkColumn);

    boolean isValidFile(MultipartFile file);

    /**
     * 把用户头像换成新的对象名。
     *
     * @return 被替换掉的旧头像对象名，供调用方清理；原本无头像、用户不存在或更新失败时为 null
     */
    String updateUserPic(int userId, String newPic);

    boolean setRedisToken(String username, String token, int type);

    boolean deleteRedisToken(String username);

    int getAllArticles(int uid);

    boolean removeUserLogo(String username);

    int findAllAccounts();

    boolean deleteUser(int id);

    PageBean<User> findAllAccountsWithPage(int pageNum, int pageSize);

    boolean changeType(int userId, int type);

    User findUserByNickName(String nickName);

    User findUserById(int userId);

    /** 按邮箱查用户（找回密码用） */
    User findUserByEmail(String email);
}
