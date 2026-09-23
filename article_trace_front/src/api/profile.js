import request from "@/utils/request.js";

/**
 * 昵称 / 个签审核 API。
 *
 * 提交入口是 `PATCH /user/update`（按 URL 前缀归在 `api/user.js`），
 * 这里只放审核领域自己的接口。
 */

/**
 * 审核列表（站长）；status 不传查全部。
 */
export function listProfileApplies(status, pageNum = 1, pageSize = 10) {
    return request.get("/profile/manage/list", {params: {status, pageNum, pageSize}})
}

/**
 * 待审数量（站长）。
 */
export function pendingProfileCount() {
    return request.get("/profile/manage/pendingCount")
}

/**
 * 审批（站长）：pass=true 通过并把待审值写回用户，false 拒绝（需给理由）。
 */
export function reviewProfileApply(id, pass, rejectReason) {
    return request.patch("/profile/manage/review/" + id, null, {params: {pass, rejectReason}})
}
