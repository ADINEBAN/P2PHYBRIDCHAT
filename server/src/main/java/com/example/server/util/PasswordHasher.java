package com.example.server.util;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordHasher {
    // Mã hóa mật khẩu
    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    // Kiểm tra mật khẩu (so sánh mật khẩu nhập vào và hash trong DB)
    public static boolean check(String plainPassword, String hashedPassword) {
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }
}