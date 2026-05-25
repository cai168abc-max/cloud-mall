package com.atguigu.common.exception;

import lombok.Getter;

/**
 * 权限不足异常
 * 用于用户无权访问资源的场景，返回403错误码
 */
@Getter
public class ForbiddenException extends BusinessException {

    /**
     * -- GETTER --
     *  获取资源名称
     *
     */
    private String resource;
    /**
     * -- GETTER --
     *  获取操作类型
     *
     */
    private String action;
    
    /**
     * 默认构造函数
     * @param message 异常信息
     */
    public ForbiddenException(final String message) {
        super(403, message);
    }
    
    /**
     * 带资源和操作信息的构造函数
     * @param resource 资源名称
     * @param action 操作类型（如：read, write, delete）
     */
    public ForbiddenException(final String resource, final String action) {
        super(403, String.format("无权访问资源: %s，操作: %s", resource, action));
        this.resource = resource;
        this.action = action;
    }

}
