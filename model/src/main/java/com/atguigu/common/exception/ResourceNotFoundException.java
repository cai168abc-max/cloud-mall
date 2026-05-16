package com.atguigu.common.exception;

/**
 * 资源不存在异常
 * 用于查询资源不存在的场景，返回404错误码
 */
public class ResourceNotFoundException extends BusinessException {
    
    private String resourceType;
    private Object resourceId;
    
    /**
     * 默认构造函数
     * @param message 异常信息
     */
    public ResourceNotFoundException(String message) {
        super(404, message);
    }
    
    /**
     * 带资源信息的构造函数
     * @param resourceType 资源类型（如：User, Product, Order）
     * @param resourceId 资源ID
     */
    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(404, String.format("%s不存在: %s", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    /**
     * 带资源信息和自定义消息的构造函数
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @param message 自定义消息
     */
    public ResourceNotFoundException(String resourceType, Object resourceId, String message) {
        super(404, message);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    /**
     * 获取资源类型
     * @return 资源类型
     */
    public String getResourceType() {
        return resourceType;
    }
    
    /**
     * 获取资源ID
     * @return 资源ID
     */
    public Object getResourceId() {
        return resourceId;
    }
}
