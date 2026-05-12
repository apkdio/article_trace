package com.articleTraceBack.Utils;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Component;

@Component
// BCrypt加密
public class BcryptUtils {

    // 加密密码
    public static String encodePass(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    // 验证密码
    public static boolean checkPass(String rawPassword, String hashedPassword) {
        try {
            return BCrypt.checkpw(rawPassword, hashedPassword);
        } catch (Exception e) {
            System.err.println("密码验证异常: " + e.getMessage());
            return false;
        }
    }

}