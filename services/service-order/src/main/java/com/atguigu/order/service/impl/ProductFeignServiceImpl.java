package com.atguigu.order.service.impl;

import com.atguigu.common.cache.CacheKeyConstants;
import com.atguigu.common.exception.BusinessException;
import com.atguigu.order.feign.ProductFeign;
import com.atguigu.order.service.ProductFeignService;
import com.atguigu.product.bean.Product;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商品Feign服务实现类
 * 
 * <p>重试策略：</p>
 * <ul>
 *   <li>最大重试次数：3次</li>
 *   <li>退避策略：指数退避（初始1秒，乘数2，最大5秒）</li>
 *   <li>重试异常：所有Exception</li>
 *   <li>兜底方案：重试失败后触发@Recover降级方法</li>
 * </ul>
 */
@Service
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ProductFeignServiceImpl implements ProductFeignService {

    private static final Logger log = LoggerFactory.getLogger(ProductFeignServiceImpl.class);
    
    private final ProductFeign productFeign;
    private final RedisTemplate<String, Object> redisTemplate;
    
    public ProductFeignServiceImpl(final ProductFeign productFeign, 
                                   final RedisTemplate<String, Object> redisTemplate) {
        this.productFeign = productFeign;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Retryable(
        retryFor = Exception.class,
            backoff = @Backoff(
            delay = 1000,
            multiplier = 2.0,
            maxDelay = 5000
        )
    )
    public Product getProductById(final long id) {
        log.debug("调用商品服务获取商品详情, productId={}", id);
        return productFeign.getProductById(id);
    }
    
    @Recover
    public Product getProductByIdRecover(final Exception e, final long id) {
        log.error("ProductFeign重试失败 - 商品服务不可用, productId={}, error={}", id, e.getMessage());
        
        // 1. 尝试从Redis缓存获取
        try {
            String cacheKey = CacheKeyConstants.productInfo(id);
            Product cachedProduct = (Product) redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedProduct != null) {
                log.info("重试失败后返回缓存商品数据, productId={}", id);
                return cachedProduct;
            }
        } catch (Exception redisEx) {
            log.error("Redis获取商品缓存失败, productId={}, error={}", id, redisEx.getMessage());
        }
        
        // 2. 返回默认商品信息
        Product defaultProduct = createDefaultProduct(id);
        log.warn("重试失败后返回默认商品数据, productId={}", id);
        return defaultProduct;
    }

    @Override
    @Retryable(
        retryFor = Exception.class,
            backoff = @Backoff(
            delay = 1000,
            multiplier = 2.0,
            maxDelay = 5000
        )
    )
    public int decreaseStock(final Long productId, final Integer quantity) {
        log.debug("调用商品服务扣减库存, productId={}, quantity={}", productId, quantity);
        return productFeign.decreaseStock(productId, quantity);
    }
    
    @Recover
    public int decreaseStockRecover(final Exception e, final Long productId, final Integer quantity) {
        log.error("ProductFeign重试失败 - 库存扣减失败, productId={}, quantity={}, error={}", 
                  productId, quantity, e.getMessage());
        // 库存操作必须抛出异常，避免数据不一致
        throw new BusinessException(503, "商品服务暂时不可用，库存扣减失败");
    }

    @Override
    @Retryable(
        retryFor = Exception.class,
            backoff = @Backoff(
            delay = 1000,
            multiplier = 2.0,
            maxDelay = 5000
        )
    )
    public int increaseStock(final Long productId, final Integer quantity) {
        log.debug("调用商品服务增加库存, productId={}, quantity={}", productId, quantity);
        return productFeign.increaseStock(productId, quantity);
    }
    
    @Recover
    public int increaseStockRecover(final Exception e, final Long productId, final Integer quantity) {
        log.error("ProductFeign重试失败 - 库存恢复失败, productId={}, quantity={}, error={}", 
                  productId, quantity, e.getMessage());
        // 库存操作必须抛出异常，避免数据不一致
        throw new BusinessException(503, "商品服务暂时不可用，库存恢复失败");
    }

    @Override
    @Retryable(
        retryFor = Exception.class,
            backoff = @Backoff(
            delay = 1000,
            multiplier = 2.0,
            maxDelay = 5000
        )
    )
    public int batchIncreaseStock(final List<Map<String, Object>> stockItems) {
        log.debug("调用商品服务批量增加库存, itemsCount={}", stockItems != null ? stockItems.size() : 0);
        return productFeign.batchIncreaseStock(stockItems);
    }
    
    @Recover
    public int batchIncreaseStockRecover(final Exception e, final List<Map<String, Object>> stockItems) {
        log.error("ProductFeign重试失败 - 批量库存恢复失败, itemsCount={}, error={}", 
                  stockItems != null ? stockItems.size() : 0, e.getMessage());
        // 库存操作必须抛出异常，避免数据不一致
        throw new BusinessException(503, "商品服务暂时不可用，批量库存恢复失败");
    }

    @Override
    @Retryable(
        retryFor = Exception.class,
            backoff = @Backoff(
            delay = 1000,
            multiplier = 2.0,
            maxDelay = 5000
        )
    )
    public int batchDecreaseStock(final List<Map<String, Object>> stockItems) {
        log.debug("调用商品服务批量扣减库存, itemsCount={}", stockItems != null ? stockItems.size() : 0);
        return productFeign.batchDecreaseStock(stockItems);
    }
    
    @Recover
    public int batchDecreaseStockRecover(final Exception e, final List<Map<String, Object>> stockItems) {
        log.error("ProductFeign重试失败 - 批量库存扣减失败, itemsCount={}, error={}", 
                  stockItems != null ? stockItems.size() : 0, e.getMessage());
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
