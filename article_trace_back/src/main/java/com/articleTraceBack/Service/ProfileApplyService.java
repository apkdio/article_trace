package com.articleTraceBack.Service;

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
}
