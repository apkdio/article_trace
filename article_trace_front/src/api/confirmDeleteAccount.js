import {ElMessage, ElMessageBox} from 'element-plus'
import {deleteAccountService} from "@/api/user.js";

export const confirmDeleteAccount = (id, username) => {
    return new Promise((resolve, reject) => {
            let countdownTimer = null
            let countdown = 5
            let confirmButton = null;

            ElMessageBox.confirm(
                `将会永久删除该账号：\n${username} 此操作不可恢复！`,
                '删除该账号？',
                {
                    confirmButtonText: `确认 (${countdown}s)`,
                    type: 'warning',
                    closeOnClickModal: false,
                    closeOnPressEscape: false,
                    showClose: false,
                    center: true,
                    customStyle: {textAlign: "center"}
                }
            ).then(() => {
                ElMessageBox.prompt('请输入站长密码!', '提示', {
                        confirmButtonText: "确定",
                        cancelButtonText: "取消",
                        type: "warning",
                        showCancelButton: true,
                        buttonSize: "default",
                        inputType: "password"
                    }
                ).then(({value}) => {
                    deleteAccountService(id,`${value}`).then(result => {
                        if (result.code === 0) {
                            ElMessage.success("删除成功！")
                            resolve()
                        } else {
                            ElMessage.error(result.message?.error || "删除失败")
                            reject(new Error(result.message?.error || "删除失败"))
                        }
                    }).catch(err => {
                        ElMessage.error("服务端响应失败！")
                        reject(err)
                    })
                })
                    .catch(() => {
                        ElMessage.primary("取消删除！")
                    })
            })
                .catch(() => {
                    ElMessage.primary("取消删除！")
                    reject(new Error('cancel')) // 用户取消删除
                })

            // 设置消息文本居中
            setTimeout(() => {
                const elements = document.getElementsByClassName("el-message-box__message")
                for (let i = 0; i < elements.length; i++) {
                    elements[i].style.textAlign = 'center';
                }
            }, 0)

            // 获取确认按钮并禁用
            setTimeout(() => {
                const confirmBtn = document.querySelector('.el-message-box .el-button--primary')
                if (confirmBtn) {
                    confirmButton = confirmBtn
                    confirmButton.disabled = true
                    confirmButton.classList.add('is-disabled')
                }
            }, 100)

            // 开始倒计时
            countdownTimer = setInterval(() => {
                countdown--
                // 更新按钮文本
                if (confirmButton) {
                    confirmButton.textContent = `确认 (${countdown}s)`
                }

                // 倒计时结束，启用按钮
                if (countdown <= 0) {
                    if (confirmButton) {
                        confirmButton.disabled = false
                        confirmButton.classList.remove('is-disabled')
                        confirmButton.textContent = '确认'
                    }
                    clearInterval(countdownTimer)
                    countdownTimer = null
                }
            }, 1000)
        }
    )
}