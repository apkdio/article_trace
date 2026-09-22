package com.articleTraceBack.Service;

import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.Report;

/** 举报：提交、查询、处置。只记录「谁报了谁、理由、处理结果」，对违规内容的实际动作仍走各自原有接口。 */
public interface ReportService {

    /**
     * 提交举报。
     *
     * @return 提交成功返回 true；对象不存在、举报自己、重复举报、理由不合法均返回 false
     */
    boolean submit(int reporterId, String targetType, int targetId, String reason);

    /** 举报分页（站长）；status 为 null 查全部 */
    PageBean<Report> list(Integer status, int pageNum, int pageSize);

    /** 待处理数量（站长，前端角标用） */
    int pendingCount();

    /**
     * 处置举报。
     *
     * @param handled true = 认定违规并处置，false = 驳回举报
     * @return 处置成功返回 true；记录不存在或已被处理返回 false
     */
    boolean handle(int reportId, boolean handled, int handlerId);
}
