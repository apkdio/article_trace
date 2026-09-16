import request from "@/utils/request.js";

/**
 * 头像审核 API。
 *
 * 提交入口是 `PATCH /user/updateUserLogo`，按 URL 前缀归在 `api/user.js`；
 * 这里只放审核领域自己的接口（查自己的待审状态 + 站长侧审核）。
 */

/**
 * 我的最新一条提交记录（无提交时 data 为 null）。
 */
export function getMyAvatarApply() {
    return request.get("/avatar/mine")
}

/**
 * 待审数量（站长）。
 */
export function pendingAvatarCount() {
    return request.get("/avatar/manage/pendingCount")
}

/**
 * 审核列表（站长）；status 不传查全部。
 */
export function listAvatarApplies(status, pageNum = 1, pageSize = 10) {
    return request.get("/avatar/manage/list", {params: {status, pageNum, pageSize}})
}

/**
 * 审批（站长）：pass=true 通过，false 拒绝（拒绝需给理由）。
 */
export function reviewAvatarApply(id, pass, rejectReason) {
    return request.patch("/avatar/manage/review/" + id, null, {params: {pass, rejectReason}})
}
