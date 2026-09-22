import {defineStore} from "pinia";
import {ref} from "vue";
import request from "@/utils/request.js";
import {getAllCategories} from "@/api/category.js";
import {getUserNowPicSrc} from "@/api/user.js";

export const userInfoStore
    = defineStore("userInfo", () => {
        const id = ref()
        const username = ref("")
        const nickname = ref("")
        const email = ref("")
        const createTime = ref("")
        const updateTime = ref("")
        const lastLogin = ref("")
        const userPic = ref("")
        const userPicSrc = ref("")
        const userPicThumbSrc = ref("")
        const articlesTotal = ref(0)
        const categories = ref(0)
        const type = ref()
        const allArticles = ref()
        const waitTotal = ref(0)
        const accessTotal = ref(0)
        const rejectTotal = ref(0)

        function fetchUserInfo() {
            // 返回 Promise 才能被 await / catch：调用方（登录、改资料、应用启动）
            // 都要等它写完 store 再取值，否则就是各写各的 setTimeout
            return request.get("/user/userInfo").then((result) => {
                id.value = result.data.id
                username.value = result.data.username
                nickname.value = result.data.nickname
                email.value = result.data.email
                createTime.value = result.data.createTime
                updateTime.value = result.data.updateTime
                userPic.value = result.data.userPic
                userPicSrc.value = result.data.userPicSrc
                userPicThumbSrc.value = result.data.userPicThumbSrc
                articlesTotal.value = result.data.articlesTotal
                type.value = result.data.type
            }).catch((err) => {
                return Promise.reject(err)
            })
        }

        /**
         * 只刷新头像三个字段。
         *
         * 头像页提交 / 重置后、应用启动时用它：比 fetchUserInfo 轻，
         * 也不会顺手改掉昵称 / 统计等字段——那些有自己的刷新时机。
         */
        async function refreshUserLogo() {
            const result = await getUserNowPicSrc()
            if (result.code !== 0) return
            userPic.value = result.data.userPic
            userPicSrc.value = result.data.userPicSrc
            userPicThumbSrc.value = result.data.userPicThumbSrc
        }

        function fetchCategories() {
            getAllCategories().then(result => {
                categories.value = result.data.length
            }).catch((err) => {
                return Promise.reject(err)
            })
        }
        function fetchArticlesCount() {
            request.get("/article/count").then(result => {
                allArticles.value = result.data.totalArticles
                waitTotal.value = result.data.waitAccessTotal
                accessTotal.value = result.data.accessTotal
                rejectTotal.value = result.data.rejectTotal
            }).catch((err) => {
                return Promise.reject(err)
            })
        }

        function setLastLogin(date) {
            lastLogin.value = date
        }

        function clearUserInfo() {
            id.value = null
            username.value = ""
            nickname.value = ""
            email.value = ""
            createTime.value = ""
            updateTime.value = ""
            lastLogin.value = ""
            userPic.value = ""
            userPicSrc.value = ""
            userPicThumbSrc.value = ""
            categories.value = 0
            articlesTotal.value = 0
            type.value = null
            allArticles.value = 0
            waitTotal.value = 0
            rejectTotal.value = 0
            accessTotal.value = 0
        }

        return {
            fetchUserInfo, refreshUserLogo, clearUserInfo,
            username, userPic, userPicSrc, userPicThumbSrc, email, nickname, lastLogin, id,
            updateTime, createTime, categories, articlesTotal, setLastLogin,
            type, allArticles, fetchArticlesCount, waitTotal,accessTotal,rejectTotal,fetchCategories
        }
    },
    {
        persist: {
            key: 'userInfo', // 存储的 key，默认是 store id
            storage: localStorage,
            paths: null
        }
    }
)