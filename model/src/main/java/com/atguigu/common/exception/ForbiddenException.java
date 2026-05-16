package com.atguigu.common.exception;

/**
 * 权限不足异常
 * 用于用户无权访问资源的场景，返回403错误码
 */
public class ForbiddenException extends BusinessException {
    
    private String resource;
    private String action;
    
    /**
     * 默认构造函数
     * @param message 异常信息
     */
    public ForbiddenException(String message) {
        super(403, message);
    }
    
    /**
     * 带资源和操作信息的构造函数
     * @param resource 资源名称
     * @param action 操作类型（如：read, write, delete）
     */
    public ForbiddenException(String resource, String action) {
        super(403, String.format("无权访问资源: %s，操作: %s", resource, action));
        this.resource = resource;
        this.action = action;
    }
    
    /**
     * 获取资源名称
     * @return 资源名称
     */
    public String getResource() {
        return resource;
    }
    
    /**
     * 获取操作类型
     * @return 操作类型
     */
    public String getAction() {
        return action;
    }
}
