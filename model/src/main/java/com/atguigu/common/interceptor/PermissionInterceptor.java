package com.atguigu.common.interceptor;

import com.atguigu.common.annotation.RequirePermission;
import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.context.UserContext;
import com.atguigu.common.enums.RequireMode;
import com.atguigu.common.enums.UserRole;
import com.atguigu.common.exception.PermissionDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 权限验证拦截器
 * 用于验证用户是否具有所需的权限
 * 权限体系设计：
 * 1. 基于角色的权限映射：
 *    - ADMIN: 拥有所有权限
 *    - MERCHANT: 拥有商品管理、订单管理等商家权限
 *    - USER: 拥有基础用户权限
 * 2. 权限标识格式：资源:操作
 *    例如：user:read, product:write, order:delete
 * 3. 验证模式：
 *    - ANY: 满足任一权限即可
 *    - ALL: 必须满足所有权限
 */
public class PermissionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PermissionInterceptor.class);

    /**
     * 管理员角色拥有的所有权限（通配符）
     */
    private static final Set<String> ADMIN_PERMISSIONS = Set.of("*");

    /**
     * 商家角色拥有的权限集合
     */
    private static final Set<String> MERCHANT_PERMISSIONS = Set.of(
            "product:read", "product:write", "product:delete",
            "order:read", "order:ship",
            "inventory:read", "inventory:write",
            "merchant:manage"
    );

    /**
     * 普通用户拥有的权限集合
     */
    private static final Set<String> USER_PERMISSIONS = Set.of(
            "user:read", "user:write",
            "product:read",
            "order:read", "order:write", "order:cancel",
            "cart:read", "cart:write",
            "address:read", "address:write", "address:delete"
    );

    /**
     * 验证权限
     * @param annotation 权限注解
     * @throws PermissionDeniedException 权限不足时抛出异常
     */
    public void checkPermission(final RequirePermission annotation) throws PermissionDeniedException {
        if (annotation == null) {
            return;
        }

        final String[] requiredPermissions = annotation.value();
        if (requiredPermissions == null || requiredPermissions.length == 0) {
            return;
        }

        final UserInfo user = UserContext.get();
        if (user == null) {
            log.warn("权限验证失败: 用户未登录");
            throw new PermissionDeniedException("请先登录");
        }

        final Set<String> userPermissions = getUserPermissions(user);
        final boolean hasPermission = checkPermissions(requiredPermissions, userPermissions, annotation.mode());
        
        if (!hasPermission) {
            log.warn("权限验证失败: 用户={}, 需要权限={}, 用户权限={}, 模式={}",
                    user.getId(), Arrays.toString(requiredPermissions), userPermissions, annotation.mode());
            throw new PermissionDeniedException(
                    requiredPermissions,
                    userPermissions.toArray(new String[0]),
                    annotation.mode(),
                    annotation.message()
            );
        }

        log.debug("权限验证通过: 用户={}, 权限={}", user.getId(), Arrays.toString(requiredPermissions));
    }

    private Set<String> getUserPermissions(final UserInfo user) {
        final UserRole role = user.getRole();
        if (role == null) {
            return new HashSet<>();
        }

        return switch (role) {
            case ADMIN -> ADMIN_PERMISSIONS;
            case MERCHANT -> MERCHANT_PERMISSIONS;
            case USER -> USER_PERMISSIONS;
        };
    }

    private boolean checkPermissions(final String[] requiredPermissions, 
            final Set<String> userPermissions, final RequireMode mode) {
        if (userPermissions.contains("*")) {
            return true;
        }

        if (mode == RequireMode.ALL) {
            for (final String permission : requiredPermissions) {
                if (!hasPermission(userPermissions, permission)) {
                    return false;
                }
            }
            return true;
        } else {
            for (final String permission : requiredPermissions) {
                if (hasPermission(userPermissions, permission)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean hasPermission(final Set<String> userPermissions, final String requiredPermission) {
        if (userPermissions.contains(requiredPermission)) {
            return true;
        }

        final String[] parts = requiredPermission.split(":");
        if (parts.length == 2) {
            final String wildcardPermission = parts[0] + ":*";
            return userPermissions.contains(wildcardPermission);
        }

        return false;
    }
}
