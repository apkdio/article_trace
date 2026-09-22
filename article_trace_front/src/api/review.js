import request from "@/utils/request.js";

/**
 * 审核中心 API（T19 第二期）。
 *
 * 只有「各类型待办数」这一个接口——列表与处置**仍走各自原有的接口**
 * （`/article/manageArticles`、`/avatar/manage/list`、`/applyAuthor/manage/list`、`/report/manage/list`），
 * 各类型的数据模型天然不同，不新建统一审核表。
 */

/**
 * 四类待办数（站长）。返回固定四个键：article / avatar / authorApply / report。
 */
export function getReviewSummary() {
    return request.get("/review/summary")
}
