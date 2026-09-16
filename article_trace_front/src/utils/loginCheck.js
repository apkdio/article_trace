import {userInfoStore} from "@/stores/userInfo.js";
import router from "@/router/index.js";
import axios from "axios";

export function loginCheck() {
    const username = userInfoStore().username
    if (username) {
        router.push({name: "PublicHome"})
    }
}

/**
 * 探测当前是否处于登录态。
 *
 * 前端已经没有 token 可看了，只能问后端：Cookie 有效则返回用户信息，无效则 401。
 * 同源请求浏览器会自动带上 Cookie，不用手动设请求头。
 */
export async function loginCheckPublic() {
    if (!userInfoStore().username) throw new Error("not login")
    return axios.get("/api/user/loginCheck")
}
