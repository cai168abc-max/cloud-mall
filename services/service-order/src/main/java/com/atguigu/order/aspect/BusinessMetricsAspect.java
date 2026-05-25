package com.atguigu.order.aspect;

import com.atguigu.order.bean.Order;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.LongAdder;

/**
 * 业务指标统计切面
 * 功能：统计订单相关业务的核心指标
 * 统计维度：
 * 1. 订单创建成功次数
 * 2. 订单支付成功次数
 * 3. 订单取消成功次数
 * 4. 秒杀成功次数
 * 5. 各方法执行时间
 * 6. 各方法异常次数
 * 特性：
 * - 高并发优化：使用LongAdder替代AtomicLong
 * - 异常隔离：切面异常不会影响原业务
 * - 定期输出：每分钟输出一次统计信息
 * - 性能监控：自动统计方法执行时间
 * - 异常统计：统计各方法的异常次数
 */
@Aspect
@Component
public class BusinessMetricsAspect {

    private static final Logger log = LoggerFactory.getLogger(BusinessMetricsAspect.class);

    // 使用LongAdder替代AtomicLong，高并发场景下性能提升3-5倍
    private final LongAdder orderCreateCount = new LongAdder();
    private final LongAdder orderCreateErrorCount = new LongAdder();
    private final LongAdder orderPayCount = new LongAdder();
    private final LongAdder orderPayErrorCount = new LongAdder();
    private final LongAdder orderCancelCount = new LongAdder();
    private final LongAdder orderCancelErrorCount = new LongAdder();
    private final LongAdder seckillCount = new LongAdder();
    private final LongAdder seckillErrorCount = new LongAdder();

    // 执行时间统计（累计毫秒数）
    private final LongAdder orderCreateTotalTime = new LongAdder();
    private final LongAdder orderPayTotalTime = new LongAdder();
    private final LongAdder orderCancelTotalTime = new LongAdder();
    private final LongAdder seckillTotalTime = new LongAdder();

    // 定义切点，避免代码冗余
    @Pointcut("execution(* com.atguigu.order.service.impl.OrderServiceImpl.createOrder*(..))")
    public void orderCreatePointcut() {}

    @Pointcut("execution(* com.atguigu.order.service.impl.OrderServiceImpl.payOrder(..))")
    public void orderPayPointcut() {}

    @Pointcut("execution(* com.atguigu.order.service.impl.OrderServiceImpl.cancelOrder(..))")
    public void orderCancelPointcut() {}

    @Pointcut("execution(* com.atguigu.order.service.impl.SeckillServiceImpl.seckill(..))")
    public void seckillPointcut() {}

    @Around("orderCreatePointcut()")
    public Object aroundOrderCreate(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMetrics(
                joinPoint,
                "订单创建",
                orderCreateCount,
                orderCreateErrorCount,
                orderCreateTotalTime
        );
    }

    @Around("orderPayPointcut()")
    public Object aroundOrderPay(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMetrics(
                joinPoint,
                "订单支付",
                orderPayCount,
                orderPayErrorCount,
                orderPayTotalTime
        );
    }

    @Around("orderCancelPointcut()")
    public Object aroundOrderCancel(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMetrics(
                joinPoint,
                "订单取消",
                orderCancelCount,
                orderCancelErrorCount,
                orderCancelTotalTime
        );
    }

    @Around("seckillPointcut()")
    public Object aroundSeckill(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMetrics(
                joinPoint,
                "秒杀下单",
                seckillCount,
                seckillErrorCount,
                seckillTotalTime
        );
    }

    /**
     * 通用的指标统计方法
     * 封装了所有统计逻辑，避免代码重复
     * 异常隔离：切面内部异常不会影响原业务方法
     */
    private Object executeWithMetrics(
            ProceedingJoinPoint joinPoint,
            String operationName,
            LongAdder successCounter,
            LongAdder errorCounter,
            LongAdder totalTimeCounter
    ) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result;
        boolean success = false;

        try {
            result = joinPoint.proceed();

            // 只有返回Order对象才认为是成功
            if (result instanceof Order) {
                successCounter.increment();
                success = true;
            }

            return result;
        } catch (Throwable e) {
            // 统计异常次数
            errorCounter.increment();
            log.warn("{}方法执行异常: {}", operationName, e.getMessage());
            throw e; // 重新抛出异常，不改变原业务行为
        } finally {
            // 统计执行时间（无论成功失败）
            long executionTime = System.currentTimeMillis() - startTime;
            totalTimeCounter.add(executionTime);

            // 记录慢查询（超过100ms）
            if (executionTime > 100) {
                log.warn("{}方法执行较慢: {}ms", operationName, executionTime);
            }

            // 记录trace级别的详细日志
            if (log.isTraceEnabled()) {
                log.trace("{}方法执行完成: 成功={}, 耗时={}ms",
                        operationName, success, executionTime);
            }
        }
    }

    /**
     * 定期输出统计信息（每分钟一次）
     * 生产环境中可以将这些指标推送到Prometheus、Grafana等监控系统
     */
    @Scheduled(fixedRate = 60000)
    public void reportMetrics() {
        long createCount = orderCreateCount.sum();
        long createErrorCount = orderCreateErrorCount.sum();
        long payCount = orderPayCount.sum();
        long payErrorCount = orderPayErrorCount.sum();
        long cancelCount = orderCancelCount.sum();
        long cancelErrorCount = orderCancelErrorCount.sum();
        long seckillCountValue = seckillCount.sum();
        long seckillErrorCountValue = seckillErrorCount.sum();

        // 计算平均执行时间
        double avgCreateTime = createCount > 0 ?
                (double) orderCreateTotalTime.sum() / createCount : 0;
        double avgPayTime = payCount > 0 ?
                (double) orderPayTotalTime.sum() / payCount : 0;
        double avgCancelTime = cancelCount > 0 ?
                (double) orderCancelTotalTime.sum() / cancelCount : 0;
        double avgSeckillTime = seckillCountValue > 0 ?
                (double) seckillTotalTime.sum() / seckillCountValue : 0;

        log.info("========== 业务指标统计 ==========");
        log.info("订单创建: 成功={}, 失败={}, 平均耗时={}ms",
                createCount, createErrorCount, String.format("%.2f", avgCreateTime));
        log.info("订单支付: 成功={}, 失败={}, 平均耗时={}ms",
                payCount, payErrorCount, String.format("%.2f", avgPayTime));
        log.info("订单取消: 成功={}, 失败={}, 平均耗时={}ms",
                cancelCount, cancelErrorCount, String.format("%.2f", avgCancelTime));
        log.info("秒杀下单: 成功={}, 失败={}, 平均耗时={}ms",
                seckillCountValue, seckillErrorCountValue, String.format("%.2f", avgSeckillTime));
        log.info("==================================");
    }

    /**
     * 重置所有统计指标
     * 用于测试或定期重置
     */
    public void resetMetrics() {
        orderCreateCount.reset();
        orderCreateErrorCount.reset();
        orderPayCount.reset();
        orderPayErrorCount.reset();
        orderCancelCount.reset();
        orderCancelErrorCount.reset();
        seckillCount.reset();
        seckillErrorCount.reset();
        orderCreateTotalTime.reset();
        orderPayTotalTime.reset();
        orderCancelTotalTime.reset();
        seckillTotalTime.reset();
        log.info("所有业务指标已重置");
    }

    // Getter方法
    public long getOrderCreateCount() {
        return orderCreateCount.sum();
    }

    public long getOrderCreateErrorCount() {
        return orderCreateErrorCount.sum();
    }

    public long getOrderPayCount() {
        return orderPayCount.sum();
    }

    public long getOrderPayErrorCount() {
        return orderPayErrorCount.sum();
    }

    public long getOrderCancelCount() {
        return orderCancelCount.sum();
    }

    public long getOrderCancelErrorCount() {
        return orderCancelErrorCount.sum();
    }

    public long getSeckillCount() {
        return seckillCount.sum();
    }

    public long getSeckillErrorCount() {
        return seckillErrorCount.sum();
    }

    public double getAvgOrderCreateTime() {
        long count = orderCreateCount.sum();
        return count > 0 ? (double) orderCreateTotalTime.sum() / count : 0;
    }

    public double getAvgOrderPayTime() {
        long count = orderPayCount.sum();
        return count > 0 ? (double) orderPayTotalTime.sum() / count : 0;
    }

    public double getAvgOrderCancelTime() {
        long count = orderCancelCount.sum();
        return count > 0 ? (double) orderCancelTotalTime.sum() / count : 0;
    }

    public double getAvgSeckillTime() {
        long count = seckillCount.sum();
        return count > 0 ? (double) seckillTotalTime.sum() / count : 0;
    }
}