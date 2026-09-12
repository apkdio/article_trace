package com.articleTraceBack.Service;

import com.articleTraceBack.pojo.AuthorApply;
import com.articleTraceBack.pojo.PageBean;

/**
 * 作者申请-审批。
 *
 * <p>审批通过时把申请人提升为作者（type=1）并强制其重新登录；
 * 提交与审批结果均通过 {@link NotificationService} 发送通知。</p>
 */
public interface AuthorApplyService {

    /** 提交申请；已有待审申请时返回 false */
    boolean submit(int userId, String reason);

    /** 我的最新一条申请（无则 null） */
    AuthorApply findMine(int userId);

    /** 申请分页；status 为 null 时查全部 */
    PageBean<AuthorApply> list(Integer status, int pageNum, int pageSize);

    /** 待审数量 */
    int pendingCount();

    /** 审批：pass=true 通过并提升为作者，false 拒绝 */
    boolean review(int applyId, boolean pass, String rejectReason, int reviewerId);
}
