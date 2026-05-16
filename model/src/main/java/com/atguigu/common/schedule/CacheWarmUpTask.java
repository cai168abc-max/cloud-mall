package com.atguigu.common.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 缓存预热任务模板
 * 
 * <p>注意：此任务已迁移至具体服务模块实现：</p>
 * <ul>
 *   <li>商品缓存预热：service-product模块</li>
 *   <li>用户缓存预热：service-user模块</li>
 *   <li>订单缓存预热：service-order模块</li>
 * </ul>
 * 
 * <p>如需在具体服务中实现缓存预热，请参考以下方式：</p>
 * <pre>
 * &#64;Component
 * public class ProductCacheWarmUpTask {
 *     &#64;EventListener(ApplicationReadyEvent.class)
 *     public void warmUpCache() {
 *         // 实现具体的缓存预热逻辑
 *     }
 * }
 * </pre>
 * 
 * @deprecated 请在具体服务模块中实现缓存预热逻辑
 */
@Component
@Deprecated
public class CacheWarmUpTask {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmUpTask.class);

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        log.info("缓存预热任务模板已加载，请在具体服务模块中实现缓存预热逻辑");
        log.info("参考位置：service-product/schedule/ProductCacheWarmUpTask.java");
    }
}
