package com.articleTraceBack.Service;

import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.ProfileApply;

/** 昵称 / 个签的待审记录。 */
public interface ProfileApplyService {

    /**
     * 落一条待审记录。
     *
     * @return 是否落成功；同一用户同一类型已有待审时返回 false（由 `uk_pending` 唯一索引兜底）
     */
    boolean submit(int userId, int type, String pendingValue);

    /** 清掉该用户该类型的待审记录：正常改名成功后，此前那条待审就成了过期数据（批准它会覆盖掉刚改的名字）。 */
    void cancelPending(int userId, int type);

    /** 审核列表（分页） */
    PageBean<ProfileApply> list(Integer status, int pageNum, int pageSize);

    /** 待审数量 */
    int pendingCount();

    /**
     * 审批：CAS 置为通过或拒绝；通过则写回 user.nickname 并起 7 天锁定期。
     *
     * @return 是否处理成功；记录不存在或已被处理过时返回 false
     */
    boolean review(int applyId, boolean pass, String rejectReason, Integer reviewerId);
}
