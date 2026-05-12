import {ElMessageBox} from 'element-plus'

export const showResetPassword = (resetPass) => {
    let countdownTimer = null
    let countdown = 5
    let confirmButton = null;
    ElMessageBox.confirm(
        `请妥善保存您的恢复密码（用于重置密码，无法更改！）\n${resetPass}`,
        '注册成功！',
        {
            confirmButtonText: `确认 (${countdown}s)`,
            type: 'success',
            showCancelButton: false,
            closeOnClickModal: false,
            closeOnPressEscape: false,
            showClose: false,
            center: true,
            customStyle: {textAlign: "center"}
        }
    )

    const element = document.getElementsByClassName("el-message-box__message")
    for (let i = 0; i < element.length; i++) {
        element[i].style.textAlign = 'center';
    }
    console.log(element)
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

