package com.articleTraceBack.Service;

import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface UserService {
    User findUserByName(String name);

    /** 注册；写库失败抛 {@code BusinessException}（唯一键冲突由调用方按 DuplicateKeyException 处理） */
    void userRegister(User user);

    String genToken(Map<String, Object> map, int type);

    boolean update(User user, int type);

    void updateLoginTime(String name);

    boolean checkPass(String oriPass, String username, String checkColumn);

    /** 校验原密码并改密；原密码不符或并发改密落空时抛 {@code BusinessException}，不覆盖先写者的新密码 */
    void updatePass(String username, String newPass, String oriPass);

    /** 昵称修改的四种结果：决定上层怎么提示（成功 / 待审 / 被拒 / 锁定期内） */
    record ProfileUpdateResult(Kind kind, String reason, String field) {
        public enum Kind {UPDATED, PENDING, REJECTED, LOCKED}
    }

    /**
     * 按内容规则修改个人资料（昵称与个签）：格式类直接拒、内容类落待审（保留旧值）、全部通过则生效并起 7 天锁定期。
     * 两者共用一个锁定期；个签只开放给作者与站长，读者提交会被忽略。同一次提交里的邮箱照常写入。
     */
    ProfileUpdateResult updateProfile(User user);

    boolean isValidFile(MultipartFile file);

    /**
     * 更新头像指向。
     * {@code ok=false} 表示没写进库（用户不存在或影响 0 行）；{@code oldPic} 只有在成功时才有意义，
     * 用户本来没有头像时是 null——所以不能只看返回值为 null 判断成败。
     */
    record UpdateUserPicResult(boolean ok, String oldPic) {
    }

    UpdateUserPicResult updateUserPic(int userId, String newPic);

    boolean setRedisToken(String username, String token, int type);

    boolean deleteRedisToken(String username);

    /** 已发布文章数：首页「我的发布」算的是产出，草稿、待审、已驳回都不计 */
    int countPublishedArticles(int uid);

    /** 重置为默认头像；用户不存在 / 没有头像 / 写库失败抛 {@code BusinessException} */
    void removeUserLogo(String username);

    int findAllAccounts();

    /** 注销账号并清理关联数据；用户不存在或删除失败抛 {@code BusinessException} */
    void deleteUser(int id);

    PageBean<User> findAllAccountsWithPage(int pageNum, int pageSize);

    /** 变更身份；写库失败抛 {@code BusinessException} */
    void changeType(int userId, int type);

    User findUserById(int userId);

    /** 按邮箱查用户（找回密码用） */
    User findUserByEmail(String email);
}
