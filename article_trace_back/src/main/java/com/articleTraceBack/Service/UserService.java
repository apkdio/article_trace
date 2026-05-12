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

    String genResetPassOri(int i);

    boolean isValidFile(MultipartFile file);

    boolean upload(MultipartFile userLogo, String username);

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
}
