package com.atguigu.common.enums;

/**
 * 权限验证模式
 * 用于定义多权限时的验证逻辑
 */
public enum RequireMode {
    /**
     * 满足任一权限即可
     * 例如：@RequirePermission(value = {"user:read", "user:write"}, mode = RequireMode.ANY)
     * 表示用户只要有user:read或user:write任一权限即可通过验证
     */
    ANY,

    /**
     * 必须满足所有权限
     * 例如：@RequirePermission(value = {"user:read", "user:write"}, mode = RequireMode.ALL)
     * 表示用户必须同时拥有user:read和user:write权限才能通过验证
     */
    ALL
}
