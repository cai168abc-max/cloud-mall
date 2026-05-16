package com.atguigu.product.config;

import org.springframework.context.annotation.Configuration;

/**
 * Sentinel流量控制和熔断降级配置
 * 
 * 注意：流量控制和熔断规则已迁移到Nacos配置中心
 * 配置文件位置：
 * - 流量控制规则: service-product-flow-rules.json (SENTINEL_GROUP)
 * - 熔断降级规则: service-product-degrade-rules.json (SENTINEL_GROUP)
 * 
 * 通过Nacos控制台可以动态调整限流阈值和熔断规则
 */
@Configuration
public class SentinelConfig {
    // 规则已迁移到Nacos配置中心，通过application.yml中的sentinel.datasource配置加载
}
