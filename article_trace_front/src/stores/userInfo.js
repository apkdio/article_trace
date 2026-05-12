import {defineStore} from "pinia";
import {ref} from "vue";
import request from "@/utils/request.js";
import {getAllCategories} from "@/api/category.js";

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
        const articlesTotal = ref(0)
        const categories = ref(0)
        const type = ref()
        const allArticles = ref()
        const waitTotal = ref(0)
        const accessTotal = ref(0)
        const rejectTotal = ref(0)

        function fetchUserInfo() {
            request.get("/user/userInfo").then((result) => {
                id.value = result.data.id
                username.value = result.data.username
                nickname.value = result.data.nickname
                email.value = result.data.email
                createTime.value = result.data.createTime
                updateTime.value = result.data.updateTime
                userPic.value = result.data.userPic
                userPicSrc.value = result.data.userPicSrc
                articlesTotal.value = result.data.articlesTotal
                type.value = result.data.type
            }).catch((err) => {
                return Promise.reject(err)
            })
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
            categories.value = 0
            articlesTotal.value = 0
            type.value = null
            allArticles.value = 0
            waitTotal.value = 0
            rejectTotal.value = 0
            accessTotal.value = 0
        }

        return {
            fetchUserInfo, clearUserInfo,
            username, userPic, userPicSrc, email, nickname, lastLogin, id,
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