import {ElMessageBox} from 'element-plus';

/**
 * 弹窗输入站长密码。
 */
export function promptMasterPassword() {
    return ElMessageBox.prompt('请输入站长密码!', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        showCancelButton: true,
        buttonSize: 'default',
        inputType: 'password'
    });
}

/**
 * 弹窗提示「请先完善个人信息」，确认后跳转个人资料页。
 */
export function confirmCompleteProfile(router) {
    return ElMessageBox.confirm('请先完善个人信息！', '提示', {
        type: 'warning',
        confirmButtonText: '确认',
        showCancelButton: false,
        closeOnClickModal: false,
        closeOnPressEscape: false,
        showClose: false,
        center: true,
        customStyle: {textAlign: 'center'}
    }).then(() => {
        router.push({name: 'UserInfo'});
    });
}
