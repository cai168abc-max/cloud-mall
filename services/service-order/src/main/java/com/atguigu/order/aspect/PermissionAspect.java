package com.atguigu.order.aspect;

import com.atguigu.common.annotation.RequirePermission;
import com.atguigu.common.interceptor.PermissionInterceptor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

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

    // 保留原有实例化方式，不做任何改动
    private final PermissionInterceptor permissionInterceptor = new PermissionInterceptor();

    /**
     * 拦截所有带有@RequirePermission注解的方法或类
     */
    @Around("@annotation(com.atguigu.common.annotation.RequirePermission) || " +
            "@within(com.atguigu.common.annotation.RequirePermission)")
    @NonNull
    public Object checkPermission(@NonNull ProceedingJoinPoint joinPoint) throws Throwable {
        // 提前获取方法签名，避免重复调用
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = AopUtils.getTargetClass(joinPoint.getTarget()).getSimpleName();
        String methodName = signature.getName();

        log.debug("开始权限验证: {}.{}", className, methodName);

        // 获取权限注解（优先方法级别，其次类级别）
        RequirePermission annotation = getPermissionAnnotation(joinPoint, signature);

        if (annotation != null) {
            // 提前获取权限值，便于日志和异常处理
            String permission = Arrays.toString(annotation.value());
            try {
                // 执行权限验证
                permissionInterceptor.checkPermission(annotation);
                log.debug("权限验证通过: {}.{} [权限: {}]", className, methodName, permission);
            } catch (RuntimeException e) {
                log.warn("权限验证失败: {}.{} [权限: {}], 原因: {}",
                        className, methodName, permission, e.getMessage());
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
     * @param signature 方法签名（提前传入避免重复获取）
     * @return 权限注解，可能为null
     */
    private RequirePermission getPermissionAnnotation(
            @NonNull ProceedingJoinPoint joinPoint,
            @NonNull MethodSignature signature
    ) {
        Method method = signature.getMethod();

        // 优先获取方法级别的注解（不受代理影响，永远正确）
        RequirePermission methodAnnotation = method.getAnnotation(RequirePermission.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        // 获取真实目标类（解决CGLIB代理类无法获取注解的问题）
        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        return targetClass.getAnnotation(RequirePermission.class);
    }
}