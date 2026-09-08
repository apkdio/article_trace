/**
 * 密码二次确认校验器工厂：返回一个 Element Plus 表单 validator，
 * 用于校验「确认密码」是否与首次输入的密码一致。
 *
 * @param getFirstPassword 获取首次密码的函数
 */
export function confirmPasswordValid(getFirstPassword) {
    return (rule, value, callback) => {
        if (!value) return callback(new Error('请再次确认密码'));
        if (getFirstPassword() !== value) return callback(new Error('两次输入密码不一致!'));
        callback();
    };
}
