package com.atguigu.user.aspect;

import com.atguigu.common.annotation.RequirePermission;
import com.atguigu.common.interceptor.PermissionInterceptor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 权限验证切面
 * 拦截带有@RequirePermission注解的方法，进行权限验证
 * 功能特性：
 * 1. 支持方法级别和类级别的权限注解
 * 2. 方法级别注解优先于类级别注解
 * 3. 记录权限验证日志
 * 4. 权限验证失败时抛出PermissionDeniedException
 * 执行顺序：
 * 设置为较高优先级（Order=1），确保在业务逻辑执行前完成权限验证
 */
@Aspect
@Component
@Order(1)
public class PermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

    private final PermissionInterceptor permissionInterceptor;

    public PermissionAspect() {
        this.permissionInterceptor = new PermissionInterceptor();
    }

    /**
     * 拦截所有带有@RequirePermission注解的方法
     */
    @Around("@annotation(com.atguigu.common.annotation.RequirePermission) || " +
            "@within(com.atguigu.common.annotation.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        log.debug("开始权限验证: {}.{}", className, methodName);

        // 获取权限注解（优先方法级别，其次类级别）
        RequirePermission annotation = getPermissionAnnotation(joinPoint);
        
        if (annotation != null) {
            try {
                // 执行权限验证
                permissionInterceptor.checkPermission(annotation);
                log.debug("权限验证通过: {}.{}", className, methodName);
            } catch (Exception e) {
                log.warn("权限验证失败: {}.{}, 原因: {}", className, methodName, e.getMessage());
                throw e;
            }
        }

        // 继续执行目标方法
        return joinPoint.proceed();
    }

    /**
     * 获取权限注解
     * 优先获取方法级别的注解，如果没有则获取类级别的注解
     * @param joinPoint 切点
     * @return 权限注解，可能为null
     */
    private RequirePermission getPermissionAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 优先获取方法级别的注解
        RequirePermission methodAnnotation = method.getAnnotation(RequirePermission.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        
        // 其次获取类级别的注解
        return joinPoint.getTarget().getClass().getAnnotation(RequirePermission.class);
    }
}
