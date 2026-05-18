package com.atguigu.common.annotation;

import com.atguigu.common.enums.RequireMode;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限验证注解
 * 用于标注在Controller方法或类上，声明访问该资源所需的权限
 * 使用示例：
 * <pre>
 * // 单个权限
 * {@code @RequirePermission("user:read")}
 * public R getUser(Long id) { ... }
 * 
 * // 多个权限，满足任一即可
 * {@code @RequirePermission(value = {"user:read", "user:write"}, mode = RequireMode.ANY)}
 * public R getUserInfo(Long id) { ... }
 * 
 * // 多个权限，必须全部满足
 * {@code @RequirePermission(value = {"order:read", "order:write"}, mode = RequireMode.ALL)}
 * public R updateOrder(Long id) { ... }
 * 
 * // 自定义错误消息
 * {@code @RequirePermission(value = "admin:manage", message = "需要管理员权限")}
 * public R manageSystem() { ... }
 * </pre>
 * 
 * 权限命名规范：
 * - 格式：资源:操作，如 user:read, product:write, order:delete
 * - 常见操作：read(读), write(写), delete(删), manage(管理)
 * 
 * @see RequireMode
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    
    /**
     * 所需权限列表
     * 支持配置一个或多个权限
     * @return 权限标识数组
     */
    String[] value() default {};
    
    /**
     * 权限验证模式
     * ANY: 满足任一权限即可（默认）
     * ALL: 必须满足所有权限
     * @return 验证模式
     */
    RequireMode mode() default RequireMode.ANY;
    
    /**
     * 权限不足时的提示消息
     * @return 错误提示消息
     */
    String message() default "权限不足";
}
