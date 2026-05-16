package com.atguigu.product.controller;

import com.atguigu.product.bean.Product;
import com.atguigu.product.mapper.ProductMapper;
import com.atguigu.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品服务内部API控制器
 * 用于服务间调用，不对外暴露
 * 路径使用/internal前缀
 */
@RestController
@RequestMapping("/internal/api/product")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductService productService;

    /**
     * 获取商品详情（内部接口）
     */
    @GetMapping("/{id}")
    public Product getProductById(@PathVariable("id") long id) {
        return productService.getProductById(id);
    }

    /**
     * 批量获取商品详情（内部接口）
     * 性能优化：减少N+1调用问题
     */
    @PostMapping("/batch")
    public List<Product> batchGetProducts(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productService.batchGetProducts(ids);
    }

    /**
     * 扣减库存（内部接口）
     */
    @PutMapping("/decreaseStock")
    public int decreaseStock(@RequestParam("productId") Long productId, @RequestParam("quantity") Integer quantity) {
        boolean success = productService.decreaseStock(productId, quantity);
        return success ? 1 : 0;
    }

    /**
     * 增加库存（内部接口）
     */
    @PutMapping("/increaseStock")
    public int increaseStock(@RequestParam("productId") Long productId, @RequestParam("quantity") Integer quantity) {
        boolean success = productService.increaseStock(productId, quantity);
        return success ? 1 : 0;
    }

    /**
     * 批量增加库存（内部接口）
     * 用于订单取消/退款时的库存回滚
     */
    @PutMapping("/batchIncreaseStock")
    public int batchIncreaseStock(@RequestBody List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        List<ProductMapper.StockItem> stockItems = items.stream()
                .map(item -> {
                    ProductMapper.StockItem stockItem = new ProductMapper.StockItem();
                    stockItem.setProductId(Long.valueOf(item.get("productId").toString()));
                    stockItem.setQuantity(Integer.valueOf(item.get("quantity").toString()));
                    return stockItem;
                })
                .collect(Collectors.toList());
        boolean success = productService.batchIncreaseStock(stockItems);
        return success ? stockItems.size() : 0;
    }

    /**
     * 批量扣减库存（内部接口）
     * 用于购物车批量下单
     */
    @PutMapping("/batchDecreaseStock")
    public int batchDecreaseStock(@RequestBody List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int successCount = 0;
        for (Map<String, Object> item : items) {
            Long productId = Long.valueOf(item.get("productId").toString());
            Integer quantity = Integer.valueOf(item.get("quantity").toString());
            boolean success = productService.decreaseStock(productId, quantity);
            if (success) {
                successCount++;
            }
        }
        return successCount;
    }
}
