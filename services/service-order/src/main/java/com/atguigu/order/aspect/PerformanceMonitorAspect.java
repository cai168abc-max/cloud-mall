package com.atguigu.order.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PerformanceMonitorAspect {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMonitorAspect.class);

    @Around("execution(* com.atguigu.order.controller..*.*(..))")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            if (duration > 1000) {
                log.warn("慢请求告警: {}.{} 耗时 {}ms", className, methodName, duration);
            } else if (duration > 500) {
                log.info("请求耗时: {}.{} 耗时 {}ms", className, methodName, duration);
            }

            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("请求异常: {}.{} 耗时 {}ms, 异常: {}", className, methodName, duration, e.getMessage());
            throw e;
        }
    }
}
