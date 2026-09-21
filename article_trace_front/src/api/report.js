import request from "@/utils/request.js";

/**
 * 举报 API。
 *
 * 提交是普通用户动作（登录即可），列表与处置归站长——按 URL 前缀 `/report/manage`
 * 由后端拦截器与 Controller 两道把关。
 */

/**
 * 提交举报。targetType: article | comment | user；reporterId 不传，后端取登录态。
 */
export function submitReport(targetType, targetId, reason) {
    return request.post("/report", {targetType, targetId, reason})
}

/**
 * 举报列表（站长）；status 不传查全部（0 待处理 / 1 已处置 / 2 已驳回）。
 */
export function listReports(status, pageNum = 1, pageSize = 10) {
    return request.get("/report/manage/list", {params: {status, pageNum, pageSize}})
}

/**
 * 待处理数量（站长，菜单角标用）。
 */
export function pendingReportCount() {
    return request.get("/report/manage/pendingCount")
}

/**
 * 处置（站长）：handled=true 认定违规，false 驳回举报。
 *
 * 只改举报记录本身；下架文章、删除评论仍走各自原有的接口。
 */
export function handleReport(id, handled) {
    return request.patch("/report/manage/handle/" + id, null, {params: {handled}})
}
