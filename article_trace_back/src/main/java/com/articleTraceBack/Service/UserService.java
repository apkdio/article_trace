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

    /** 校验原密码并改密；原密码已变（并发改密）时返回 false，不覆盖先写者的新密码 */
    boolean updatePass(String username, String newPass, String oriPass);

    /** 昵称修改的四种结果：决定上层怎么提示（成功 / 待审 / 被拒 / 锁定期内） */
    record ProfileUpdateResult(Kind kind, String reason, String field) {
        public enum Kind {UPDATED, PENDING, REJECTED, LOCKED}
    }

    /**
     * 按内容规则修改昵称：格式类直接拒、内容类落待审（保留旧值）、通过则立即生效并起 7 天锁定期。
     * 同一次提交里的邮箱照常写入——用户改昵称顺带改邮箱时，不该因为昵称要审核就整单丢掉。
     */
    ProfileUpdateResult updateNickname(User user);

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
