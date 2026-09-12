import request from "@/utils/request.js";

/**
 * 我的站内信分页（按发送时间倒序）。
 */
export function listNotifications(pageNum = 1, pageSize = 10) {
    return request.get("/notification/list", {params: {pageNum, pageSize}})
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
