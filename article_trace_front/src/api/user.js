import request from "@/utils/request.js"
import {userInfoStore} from "@/stores/userInfo.js";


export const registerService = (registerData) => {
    return request.post('/user/register', registerData)
}
export const getCaptchaService = () => {
    return request.get('/user/captcha')
}
export const sendEmailCodeService = (data) => {
    return request.post('/user/email/code', data)
}
export const loginService = (loginData) => {
    return request.post('/user/login', loginData)
}
export const forgetPassService = (forgetPassData) => {
    return request.post('/user/forgetPass', forgetPassData)
}
export const logoutService = () => {
    return request.post('/user/logout')
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

/**
 * 提交待审头像（multipart）。
 *
 * 上传成功不等于头像已换——会先进入待审队列，站长审批通过后才写进 user_pic。
 */
export const submitUserLogoService = (userLogo) => {
    const formData = new FormData()
    formData.append('userLogo', userLogo)
    return request.patch('/user/updateUserLogo', formData)
}
/**
 * 当前生效头像（对象名 + 原图地址 + 缩略图地址）。
 *
 * 比 `/user/userInfo` 轻，只用来回写头像那三个字段。
 */
export const getUserNowPicSrc = () => {
    return request.get('/user/nowLogo')
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