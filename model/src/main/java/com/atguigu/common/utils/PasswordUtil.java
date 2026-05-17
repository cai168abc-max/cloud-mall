package com.atguigu.common.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordUtil {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public static String hashPassword(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String hashedPassword) {
        if (hashedPassword == null) {
            throw new IllegalArgumentException("Encoded password cannot be null");
        }
        if (rawPassword == null) {
            return false;
        }
        return encoder.matches(rawPassword, hashedPassword);
    }
}
