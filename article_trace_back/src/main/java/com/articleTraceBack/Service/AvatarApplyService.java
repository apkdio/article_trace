package com.articleTraceBack.Service;

import com.articleTraceBack.pojo.AvatarApply;
import com.articleTraceBack.pojo.PageBean;
import org.springframework.web.multipart.MultipartFile;

/**
 * 头像上传审核。
 *
 * <p>用户提交的头像先进 avatar 桶等待审核，通过才写入 {@code user.user_pic}。
 * 用户侧在待审期间仍显示旧头像。</p>
 */
public interface AvatarApplyService {

    /** 提交待审头像；已有待审记录或对象上传失败时返回 false */
    boolean submit(int userId, MultipartFile file);

    /** 站长直通：上传后直接生效，不进审核队列（他是唯一能审的人，审自己没有意义） */
    boolean submitDirect(int userId, MultipartFile file);

    /** 我的最新一条记录（无则 null） */
    AvatarApply findMine(int userId);

    /** 审核分页；status 为 null 时查全部 */
    PageBean<AvatarApply> list(Integer status, int pageNum, int pageSize);

    /** 待审数量 */
    int pendingCount();

    /** 审批：pass=true 通过并把待审头像设为用户头像，false 拒绝 */
    boolean review(int applyId, boolean pass, String rejectReason, int reviewerId);
}
