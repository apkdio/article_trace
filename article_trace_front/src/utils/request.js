import axios from 'axios'
import {ElMessage} from "element-plus";
import router from "@/router/index.js";
import {userInfoStore} from "@/stores/userInfo.js";
import {isAuthHandled, markAuthHandled} from "@/utils/session.js";

const baseUrl = '/api'
// withCredentials：令牌在 HttpOnly Cookie 里，要允许跨端口/跨域调试时也带上它。
// 同源请求本来就带 Cookie，这里显式写出来是为了避免以后改代理配置时踩坑。
const instance = axios.create({baseURL: baseUrl, withCredentials: true})

// 故意没有请求拦截器：令牌由浏览器自动附带，前端拿不到也不该拿。
// 以前往 Authorization 头里塞 token，等于把凭证暴露给同源 JS，XSS 一打就丢。

// 响应拦截器
instance.interceptors.response.use(
    result => {
        return result.data
    },
    err => {
        // 检查 err.response 是否存在
        if (err.response) {
            // 413：请求体超过 nginx / Spring 的上限。默认只会提示「网络错误」，
            // 用户猜不到是内容太大——长文里粘了图片（编辑器会转成 base64）时最容易踩到。
            if (err.response.status === 413) {
                ElMessage.error("内容过大，未能保存。请精简正文，或把图片改用封面 / 外链。")
                return Promise.reject(err)
            }
            // 401 未授权错误
            if (err.response.status === 401) {
                if (!isAuthHandled()) {
                    markAuthHandled()
                    ElMessage.warning("登录已失效！")
                    userInfoStore().clearUserInfo()
                    router.push({name: "PublicHome"})
                }
                return Promise.reject(err)
            }
        }
        return Promise.reject(err)
    }
)

export default instance
