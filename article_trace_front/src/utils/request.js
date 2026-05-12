import axios from 'axios'
import {tokenStorage} from "@/stores/tokenStorage.js";
import {ElMessage} from "element-plus";
import router from "@/router/index.js";
import {userInfoStore} from "@/stores/userInfo.js";

const baseUrl = '/api'
const instance = axios.create({baseURL: baseUrl})

// 请求拦截器
instance.interceptors.request.use(config => {
    const token = tokenStorage().token
    if (token != null) {
        config.headers.Authorization = token
    }
    return config
}, error => {
    return Promise.reject(error)
})

// 响应拦截器
instance.interceptors.response.use(
    result => {
        return result.data
    },
    err => {
        // 检查 err.response 是否存在
        if (err.response) {
            // 401 未授权错误
            if (err.response.status === 401) {
                if (!tokenStorage().processed) {
                    tokenStorage().processed = true
                    ElMessage.warning("登录已失效！")
                    tokenStorage().clearToken()
                    userInfoStore().clearUserInfo()
                    router.push({name: "PublicHome"})
                    return Promise.reject(err)
                } else {
                    return Promise.reject(err)
                }
            }
        }
        return Promise.reject(err)
    }
)

export default instance