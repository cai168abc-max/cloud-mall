package com.atguigu.order.feign;

import com.atguigu.order.fallback.ProductFeignFallbackFactory;
import com.atguigu.product.bean.Product;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 商品服务Feign客户端
 * 
 * 内部API使用/internal前缀，用于区分外部公开接口和内部服务间调用接口
 * 便于：
 * 1. 权限控制：内部接口可跳过某些外部校验
 * 2. 监控统计：区分内外部调用
 * 3. 安全隔离：防止外部直接调用内部接口
 */
@FeignClient(value = "service-product", fallbackFactory = ProductFeignFallbackFactory.class)
public interface ProductFeign {
    /**
     * 获取商品详情（内部接口）
     */
    @GetMapping("/internal/api/product/{id}")
    Product getProductById(@PathVariable("id") long id);

    /**
     * 批量获取商品详情（内部接口）
     * 性能优化：减少N+1调用问题
     * @param ids 商品ID列表
     * @return 商品列表
     */
    @PostMapping("/internal/api/product/batch")
    List<Product> batchGetProducts(@RequestBody List<Long> ids);

    /**
     * 扣减库存（内部接口）
     */
    @PutMapping("/internal/api/product/decreaseStock")
    int decreaseStock(@RequestParam("productId") Long productId, @RequestParam("quantity") Integer quantity);

    /**
     * 增加库存（内部接口）
     */
    @PutMapping("/internal/api/product/increaseStock")
    int increaseStock(@RequestParam("productId") Long productId, @RequestParam("quantity") Integer quantity);

    /**
     * 批量增加库存（内部接口）
     * 用于订单取消/退款时的库存回滚
     * @param stockItems 库存项列表，每个项包含productId和quantity
     * @return 成功回滚的数量
     */
    @PutMapping("/internal/api/product/batchIncreaseStock")
    int batchIncreaseStock(@RequestBody List<Map<String, Object>> stockItems);

    /**
     * 批量扣减库存（内部接口）
     * 用于购物车批量下单
     * @param stockItems 库存项列表，每个项包含productId和quantity
     * @return 成功扣减的数量
     */
    @PutMapping("/internal/api/product/batchDecreaseStock")
    int batchDecreaseStock(@RequestBody List<Map<String, Object>> stockItems);
}
