import request from "@/utils/request.js";

/**
 * 提交作者申请。
 */
export function submitAuthorApply(reason) {
    return request.post("/apply/author", {reason})
}

/**
 * 我的最新一条申请（无申请时 data 为 null）。
 */
export function getMyApply() {
    return request.get("/apply/author/mine")
}

/**
 * 申请列表（站长）；status 不传查全部。
 */
export function listApplies(status, pageNum = 1, pageSize = 10) {
    return request.get("/apply/author/list", {params: {status, pageNum, pageSize}})
}

/**
 * 待审数量（站长）。
 */
export function pendingApplyCount() {
    return request.get("/apply/author/pendingCount")
}

/**
 * 审批（站长）：pass=true 通过，false 拒绝。
 */
export function reviewApply(id, pass, rejectReason) {
    return request.patch("/apply/author/review/" + id, null, {params: {pass, rejectReason}})
}
