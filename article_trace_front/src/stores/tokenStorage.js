import {defineStore} from "pinia";
import {ref} from "vue";

export const tokenStorage = {}
    = defineStore("tokenStorage", () => {
        const token = ref("")
        const processed = ref(false)

        function setToken(newToken) {
            token.value = newToken
        }

        function clearToken() {
            token.value = "";
        }


        return {token, setToken, clearToken,processed}
    },
    {
        persist: {
            key: 'token', // 存储的 key，默认是 store id
            storage: localStorage, // 存储方式
            paths: ['token'] // 指定要持久化的字段
        }
    })