package com.atguigu.order.service;

import com.atguigu.product.bean.Product;

import java.util.List;
import java.util.Map;

/**
 * 商品Feign服务接口
 * 提供带重试机制的商品服务调用
 */
public interface ProductFeignService {
    
    /**
     * 获取商品详情（带重试）
     */
    Product getProductById(long id);
    
    /**
     * 扣减库存（带重试）
     */
    int decreaseStock(Long productId, Integer quantity);
    
    /**
     * 增加库存（带重试）
     */
    int increaseStock(Long productId, Integer quantity);
    
    /**
     * 批量增加库存（带重试）
     */
    int batchIncreaseStock(List<Map<String, Object>> stockItems);
    
    /**
     * 批量扣减库存（带重试）
     */
    int batchDecreaseStock(List<Map<String, Object>> stockItems);
}
