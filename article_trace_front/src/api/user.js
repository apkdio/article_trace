import request from "@/utils/request.js"
import {userInfoStore} from "@/stores/userInfo.js";


export const registerService = (registerData) => {
    return request.post('/user/register', registerData)
}
export const loginService = (loginData) => {
    return request.post('/user/login', loginData)
}
export const forgetPassService = (forgetPassData) => {
    return request.post('/user/forgetPass', forgetPassData)
}
export const logoutService = () => {
    return request.get('/user/logout')
}
export const updateUserPassService = (updateData) => {
    return request.patch('/user/updatePass', updateData)
}

export const updateUserInfoService = (updateData) => {
    return request.patch('/user/update', updateData)
}

export const removeUserLogoService = () => {
    return request.delete('/user/removeUserLogo')
}

export const getAllAccountsService = () => {
    return request.get('/user/accountManage')
}

export const deleteAccountService = (id, masterPass) => {
    return request.delete('/user/delete', {params: {userId: id, masterPass: masterPass}})
}
export const checkType = (type) => {
    return type.includes(userInfoStore().type);
}
export const changeType = (id, type, masterPass) => {
    return request.patch('/user/changeType', null,
        {params: {userId: id, type: type, masterPass: masterPass}})
}