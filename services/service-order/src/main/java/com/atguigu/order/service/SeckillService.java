package com.atguigu.order.service;

import com.atguigu.order.bean.Order;

public interface SeckillService {

    /**
     * 秒杀下单（高并发场景）
     */
    Order seckill(Long productId, Long userId);
}


