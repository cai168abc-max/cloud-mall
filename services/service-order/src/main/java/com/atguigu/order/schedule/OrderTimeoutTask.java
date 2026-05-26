package com.atguigu.order.schedule;

import com.atguigu.common.enums.OrderStatus;
import com.atguigu.order.bean.Order;
import com.atguigu.order.mapper.OrderMapper;
import com.atguigu.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings("EI_EXPOSE_REP2")
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutTask.class);

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60000)
    public void cancelTimeoutOrders() {
        List<Order> timeoutOrders = orderMapper.selectTimeoutOrders(30);
        
        for (Order order : timeoutOrders) {
            try {
                if (order.getStatus() == OrderStatus.CREATED) {
                    orderService.cancelOrder(order.getId(), order.getUserId());
                    log.info("订单超时自动取消: orderId={}, timeoutMinutes=30", order.getId());
                }
            } catch (Exception e) {
                log.error("订单超时取消失败: orderId={}", order.getId(), e);
            }
        }
    }
}
