package com.atguigu.common.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PasswordUtil {

    private PasswordUtil() {
    }

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public static String hashPassword(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Raw password cannot be null");
        }
        return encoder.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String hashedPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Raw password cannot be null");
        }
        if (hashedPassword == null) {
            throw new IllegalArgumentException("Encoded password cannot be null");
        }
        return encoder.matches(rawPassword, hashedPassword);
    }
}
