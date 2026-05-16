package com.atguigu.product.schedule;

import com.atguigu.common.cache.HotProductService;
import com.atguigu.common.cache.MultiLevelCacheService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 商品热点数据刷新任务
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>定时刷新热点商品数据</li>
 *   <li>输出缓存统计信息用于监控</li>
 * </ul>
 * 
 * <p>注意：本地缓存清理已优化为按需清理策略：</p>
 * <ul>
 *   <li>Caffeine已配置基于时间的自动过期（expireAfterWrite）</li>
 *   <li>Caffeine已配置基于大小的自动驱逐（maximumSize）</li>
 *   <li>无需定时清理，避免不必要的性能开销</li>
 * </ul>
 */
@Component
@ConditionalOnBean({HotProductService.class, MultiLevelCacheService.class})
@RequiredArgsConstructor
public class HotDataRefreshTask {

    private static final Logger log = LoggerFactory.getLogger(HotDataRefreshTask.class);

    @Nullable
    private final HotProductService hotProductService;

    @Nullable
    private final MultiLevelCacheService multiLevelCacheService;

    /**
     * 定时刷新热点商品数据
     * 每小时执行一次
     */
    @Scheduled(fixedDelay = 3600000)
    public void refreshHotProducts() {
        if (hotProductService == null) {
            return;
        }
        try {
            log.info("开始刷新热点商品数据...");
            var hotProductIds = hotProductService.getHotProductIds();
            log.info("发现 {} 个热点商品", hotProductIds.size());
        } catch (Exception e) {
            log.error("热点数据刷新失败", e);
        }
    }

    /**
     * 定时输出缓存统计信息（用于监控）
     * 每5分钟执行一次
     */
    @Scheduled(fixedDelay = 300000)
    public void logCacheStats() {
        if (multiLevelCacheService == null) {
            return;
        }
        try {
            String stats = multiLevelCacheService.getCacheStats();
            log.debug("缓存统计: {}", stats);
        } catch (Exception e) {
            log.error("获取缓存统计失败", e);
        }
    }
}
