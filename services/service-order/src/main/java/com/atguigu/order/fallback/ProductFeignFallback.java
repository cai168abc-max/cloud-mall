package com.atguigu.order.fallback;

import com.atguigu.common.cache.CacheKeyConstants;
import com.atguigu.common.exception.BusinessException;
import com.atguigu.order.feign.ProductFeign;
import com.atguigu.product.bean.Product;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * ProductFeign降级处理
 * 
 * <p>降级策略：</p>
 * <ul>
 *   <li>查询操作：优先从Redis缓存获取 → 返回默认商品信息</li>
 *   <li>写操作（库存扣减/恢复）：抛出异常，避免数据不一致</li>
 * </ul>
 */
@Component
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ProductFeignFallback implements ProductFeign {

    private static final Logger log = LoggerFactory.getLogger(ProductFeignFallback.class);
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public ProductFeignFallback(final RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Product getProductById(final long id) {
        log.warn("ProductFeign降级触发 - 商品服务不可用, productId={}", id);
        
        // 1. 尝试从Redis缓存获取
        try {
            String cacheKey = CacheKeyConstants.productInfo(id);
            Product cachedProduct = (Product) redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedProduct != null) {
                log.info("降级返回缓存商品数据, productId={}", id);
                return cachedProduct;
            }
        } catch (Exception e) {
            log.error("Redis获取商品缓存失败, productId={}, error={}", id, e.getMessage());
        }
        
        // 2. 返回默认商品信息
        Product defaultProduct = createDefaultProduct(id);
        log.warn("降级返回默认商品数据, productId={}", id);
        return defaultProduct;
    }

    @Override
    public List<Product> batchGetProducts(final List<Long> ids) {
        log.warn("ProductFeign降级触发 - 批量获取商品服务不可用, idsCount={}", ids != null ? ids.size() : 0);
        
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        
        // 返回默认商品信息列表
        return ids.stream()
                .map(this::createDefaultProduct)
                .toList();
    }

    @Override
    public int decreaseStock(Long productId, Integer quantity) {
        log.error("ProductFeign降级触发 - 库存扣减失败, productId={}, quantity={}", productId, quantity);
        // 库存操作必须抛出异常，避免数据不一致
        throw new BusinessException(503, "商品服务暂时不可用，库存扣减失败");
    }

    @Override
    public int increaseStock(final Long productId, final Integer quantity) {
        log.error("ProductFeign降级触发 - 库存恢复失败, productId={}, quantity={}", productId, quantity);
        // 库存操作必须抛出异常，避免数据不一致
        throw new BusinessException(503, "商品服务暂时不可用，库存恢复失败");
    }

    @Override
    public int batchIncreaseStock(List<Map<String, Object>> stockItems) {
        log.error("ProductFeign降级触发 - 批量库存恢复失败, itemsCount={}", stockItems != null ? stockItems.size() : 0);
        // 库存操作必须抛出异常，避免数据不一致
        throw new BusinessException(503, "商品服务暂时不可用，批量库存恢复失败");
    }

    @Override
    public int batchDecreaseStock(final List<Map<String, Object>> stockItems) {
        log.error("ProductFeign降级触发 - 批量库存扣减失败, itemsCount={}", stockItems != null ? stockItems.size() : 0);
        // 库存操作必须抛出异常，避免数据不一致
        throw new BusinessException(503, "商品服务暂时不可用，批量库存扣减失败");
    }
    
    /**
     * 创建默认商品信息
     */
    private Product createDefaultProduct(final Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("商品信息加载中");
        product.setDescription("服务暂时不可用，请稍后重试");
        product.setPrice(BigDecimal.ZERO);
        product.setNum(0);
        product.setSales(0);
        product.setEnabled(false);
        product.setVerified(false);
        return product;
    }
}
