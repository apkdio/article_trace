import {userInfoStore} from "@/stores/userInfo.js";
import {tokenStorage} from "@/stores/tokenStorage.js";
import router from "@/router/index.js";
import axios from "axios";

export function loginCheck() {
    const username = userInfoStore().username
    const token = tokenStorage().token
    if (username && token) {
        router.push({name: "PublicHome"})
    }
}

export async function loginCheckPublic() {
    if (tokenStorage().token !== "") return axios.get("/api/user/loginCheck", {
        headers: {
            "Authorization": tokenStorage().token
        }
    })
    else throw new Error("not login")
}