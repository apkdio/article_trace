import request from "@/utils/request.js";

/**
 * 我的站内信分页（按发送时间倒序）。
 *
 * @param type system / apply；不传或 all 查全部
 */
export function listNotifications(type, pageNum = 1, pageSize = 10) {
    return request.get("/notification/list", {params: {type, pageNum, pageSize}})
}

/**
 * 删除单条站内信（仅限本人）。
 */
export function deleteNotification(id) {
    return request.delete("/notification/" + id)
}

/**
 * 我的未读数（铃铛角标）。
 */
export function unreadCount() {
    return request.get("/notification/unreadCount")
}

/**
 * 标记单条已读。
 */
export function markRead(id) {
    return request.patch("/notification/read/" + id)
}

/**
 * 全部标记已读。
 */
export function markAllRead() {
    return request.patch("/notification/readAll")
}
