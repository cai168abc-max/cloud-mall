package com.atguigu.common.exception;

import com.atguigu.common.enums.RequireMode;

/**
 * 权限拒绝异常
 * 当用户缺少所需权限时抛出此异常
 * 
 * 与ForbiddenException的区别：
 * - ForbiddenException: 用于通用的权限不足场景（如资源访问被拒绝）
 * - PermissionDeniedException: 专门用于@RequirePermission注解的权限验证失败场景
 */
public class PermissionDeniedException extends BusinessException {
    
    /**
     * 用户当前拥有的权限
     */
    private String[] userPermissions;
    
    /**
     * 需要的权限
     */
    private String[] requiredPermissions;
    
    /**
     * 权限验证模式
     */
    private RequireMode mode;
    
    /**
     * 默认构造函数
     * @param message 异常信息
     */
    public PermissionDeniedException(String message) {
        super(403, message);
    }
    
    /**
     * 带权限详情的构造函数
     * @param requiredPermissions 需要的权限
     * @param userPermissions 用户拥有的权限
     * @param mode 验证模式
     * @param message 错误消息
     */
    public PermissionDeniedException(String[] requiredPermissions, String[] userPermissions, 
                                     RequireMode mode, String message) {
        super(403, message);
        this.requiredPermissions = requiredPermissions;
        this.userPermissions = userPermissions;
        this.mode = mode;
    }
    
    /**
     * 简化构造函数
     * @param requiredPermission 需要的权限
     * @param message 错误消息
     */
    public PermissionDeniedException(String requiredPermission, String message) {
        super(403, message);
        this.requiredPermissions = new String[]{requiredPermission};
    }
    
    public String[] getUserPermissions() {
        return userPermissions;
    }
    
    public String[] getRequiredPermissions() {
        return requiredPermissions;
    }
    
    public RequireMode getMode() {
        return mode;
    }
    
    /**
     * 获取格式化的权限详情
     * @return 权限详情字符串
     */
    public String getPermissionDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("需要的权限: ");
        if (requiredPermissions != null && requiredPermissions.length > 0) {
            sb.append(String.join(", ", requiredPermissions));
        } else {
            sb.append("无");
        }
        
        if (mode != null) {
            sb.append(" (模式: ").append(mode.name()).append(")");
        }
        
        sb.append(", 用户权限: ");
        if (userPermissions != null && userPermissions.length > 0) {
            sb.append(String.join(", ", userPermissions));
        } else {
            sb.append("无");
        }
        
        return sb.toString();
    }
}
