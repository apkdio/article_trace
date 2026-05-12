import {userInfoStore} from "@/stores/userInfo.js";

export const checkPersonInfo = () => {
    return !(userInfoStore().nickname === "" || userInfoStore().email === "");
}