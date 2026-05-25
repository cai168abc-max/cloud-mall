package com.atguigu.order.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * 接口性能监控切面
 * 功能：监控所有Controller接口的执行时间和异常情况
 * 特性：
 * - 高精度计时：使用System.nanoTime()，精度达纳秒级
 * - 分级日志：根据耗时自动选择日志级别
 * - 可配置阈值：支持通过配置文件调整慢请求阈值
 * - 完整异常堆栈：记录完整的异常信息，便于排查问题
 * - 参数日志：记录方法参数，便于复现问题
 * - 异常隔离：切面异常不会影响原业务执行
 */
@Aspect
@Component
public class PerformanceMonitorAspect {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMonitorAspect.class);

    /**
     * 信息级日志阈值（毫秒）
     * 超过此值记录INFO级日志
     */
    @Value("${performance.monitor.info-threshold:500}")
    private long infoThreshold;

    /**
     * 警告级日志阈值（毫秒）
     * 超过此值记录WARN级日志
     */
    @Value("${performance.monitor.warn-threshold:1000}")
    private long warnThreshold;

    /**
     * 是否记录方法参数
     */
    @Value("${performance.monitor.log-parameters:true}")
    private boolean logParameters;

    /**
     * 定义切点：拦截所有Controller的public方法
     * 只拦截@RequestMapping/@GetMapping/@PostMapping等注解标记的方法
     */
    @Pointcut("@annotation(org.springframework.web.bind.annotation.RequestMapping) " +
            "|| @annotation(org.springframework.web.bind.annotation.GetMapping) " +
            "|| @annotation(org.springframework.web.bind.annotation.PostMapping) " +
            "|| @annotation(org.springframework.web.bind.annotation.PutMapping) " +
            "|| @annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void controllerEndpointPointcut() {}

    @Around("controllerEndpointPointcut()")
    @NonNull
    public Object monitorPerformance(@NonNull ProceedingJoinPoint joinPoint) throws Throwable {
        // 使用nanoTime计时，精度更高且不受系统时间调整影响
        long startTimeNanos = System.nanoTime();
        String methodSignature = getMethodSignature(joinPoint);
        String parameters = logParameters ? getParametersString(joinPoint) : "";

        try {
            Object result = joinPoint.proceed();
            long durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000;

            // 根据耗时分级记录日志
            if (durationMs > warnThreshold) {
                log.warn("【慢请求告警】{} 耗时 {}ms 参数: {}", methodSignature, durationMs, parameters);
            } else if (durationMs > infoThreshold) {
                log.info("【请求耗时】{} 耗时 {}ms 参数: {}", methodSignature, durationMs, parameters);
            } else if (log.isDebugEnabled()) {
                log.debug("【请求完成】{} 耗时 {}ms 参数: {}", methodSignature, durationMs, parameters);
            }

            return result;
        } catch (Throwable e) {
            long durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000;
            // 记录完整异常堆栈，便于排查问题
            log.error("【请求异常】{} 耗时 {}ms 参数: {}", methodSignature, durationMs, parameters, e);
            throw e; // 重新抛出异常，不改变原业务行为
        }
    }

    /**
     * 获取完整的方法签名
     * 包含类名和方法名，便于定位
     */
    @NonNull
    private String getMethodSignature(@NonNull ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        return className + "." + methodName;
    }

    /**
     * 获取方法参数的字符串表示
     * 限制长度，避免日志过大
     */
    @NonNull
    private String getParametersString(@NonNull ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return "无参数";
            }

            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                if (arg == null) {
                    sb.append("null");
                } else {
                    String argStr = arg.toString();
                    // 限制单个参数长度，避免日志过大
                    if (argStr.length() > 200) {
                        argStr = argStr.substring(0, 200) + "...";
                    }
                    sb.append(argStr);
                }
            }

            // 限制总长度
            String result = sb.toString();
            if (result.length() > 500) {
                result = result.substring(0, 500) + "...";
            }
            return result;
        } catch (Exception e) {
            log.warn("获取方法参数失败", e);
            return "参数获取失败";
        }
    }
}