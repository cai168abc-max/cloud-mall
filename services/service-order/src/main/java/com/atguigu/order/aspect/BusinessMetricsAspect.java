package com.atguigu.order.aspect;

import com.atguigu.common.enums.OrderStatus;
import com.atguigu.order.bean.Order;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Aspect
@Component
public class BusinessMetricsAspect {

    private static final Logger log = LoggerFactory.getLogger(BusinessMetricsAspect.class);

    private final AtomicLong orderCreateCount = new AtomicLong(0);
    private final AtomicLong orderPayCount = new AtomicLong(0);
    private final AtomicLong orderCancelCount = new AtomicLong(0);
    private final AtomicLong seckillCount = new AtomicLong(0);

    @Around("execution(* com.atguigu.order.service.impl.OrderServiceImpl.createOrder*(..))")
    public Object recordOrderCreate(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        if (result instanceof Order) {
            orderCreateCount.incrementAndGet();
            log.debug("订单创建计数: {}", orderCreateCount.get());
        }
        return result;
    }

    @Around("execution(* com.atguigu.order.service.impl.OrderServiceImpl.payOrder(..))")
    public Object recordOrderPay(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        if (result instanceof Order) {
            orderPayCount.incrementAndGet();
            log.debug("订单支付计数: {}", orderPayCount.get());
        }
        return result;
    }

    @Around("execution(* com.atguigu.order.service.impl.OrderServiceImpl.cancelOrder(..))")
    public Object recordOrderCancel(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        if (result instanceof Order) {
            orderCancelCount.incrementAndGet();
            log.debug("订单取消计数: {}", orderCancelCount.get());
        }
        return result;
    }

    @Around("execution(* com.atguigu.order.service.impl.SeckillServiceImpl.seckill(..))")
    public Object recordSeckill(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        if (result instanceof Order) {
            seckillCount.incrementAndGet();
            log.debug("秒杀成功计数: {}", seckillCount.get());
        }
        return result;
    }

    public long getOrderCreateCount() {
        return orderCreateCount.get();
    }

    public long getOrderPayCount() {
        return orderPayCount.get();
    }

    public long getOrderCancelCount() {
        return orderCancelCount.get();
    }

    public long getSeckillCount() {
        return seckillCount.get();
    }
}
