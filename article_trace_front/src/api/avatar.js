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
