package com.articleTraceBack.Utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
@Component
public class GenResetPass {
    // 生成重置密码时验证的安全密码（用户无法修改）
    // 使用 SecureRandom（更安全）
    @Value("${Password.character}")
    private String chars;
    public String generatePass(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomIndex = random.nextInt(chars.length());
            sb.append(chars.charAt(randomIndex));
        }
        return sb.toString();
    }
}