package com.atguigu.common.bean;

import com.atguigu.common.enums.UserRole;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class UserSession {
    private String sessionId;
    private Long userId;
    private String token;
    private String deviceInfo;
    private String ipAddress;
    private Instant loginTime;
    private Instant lastActiveTime;
    private Instant expireTime;
    private SessionStatus status;
    private UserRole userRole;
    private String userAgent;
    private Boolean isActive;

    public enum SessionStatus {
        ACTIVE,
        EXPIRED,
        LOGGED_OUT,
        FORCE_LOGOUT
    }

    public UserSession() {
        this.sessionId = UUID.randomUUID().toString();
        this.loginTime = Instant.now();
        this.lastActiveTime = Instant.now();
        this.status = SessionStatus.ACTIVE;
        this.isActive = true;
    }

    public UserSession(final Long userId, final String token, final String deviceInfo, 
            final String ipAddress, final UserRole role, final String userAgent) {
        this();
        this.userId = userId;
        this.token = token;
        this.deviceInfo = deviceInfo;
        this.ipAddress = ipAddress;
        this.userRole = role;
        this.userAgent = userAgent;
    }

    public void updateLastActiveTime() {
        this.lastActiveTime = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expireTime) || status != SessionStatus.ACTIVE;
    }

    public void logout() {
        this.status = SessionStatus.LOGGED_OUT;
        this.isActive = false;
    }

    public void forceLogout() {
        this.status = SessionStatus.FORCE_LOGOUT;
        this.isActive = false;
    }

    public void activate() {
        this.status = SessionStatus.ACTIVE;
        this.isActive = true;
        updateLastActiveTime();
    }
}
