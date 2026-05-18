package com.atguigu.order.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel流量控制和熔断降级配置
 * 
 * <p>系统自适应保护规则说明：</p>
 * <ul>
 *   <li>load: 系统负载阈值，超过则触发保护（Linux/Unix有效）</li>
 *   <li>avgRt: 平均响应时间阈值（毫秒）</li>
 *   <li>maxThread: 最大并发线程数</li>
 *   <li>qps: 系统入口QPS阈值</li>
 *   <li>cpu: CPU使用率阈值（0-1）</li>
 * </ul>
 * 
 * <p>注意：流量控制和熔断规则已迁移到Nacos配置中心</p>
 * <p>配置文件位置：</p>
 * <ul>
 *   <li>流量控制规则: service-order-flow-rules.json (SENTINEL_GROUP)</li>
 *   <li>熔断降级规则: service-order-degrade-rules.json (SENTINEL_GROUP)</li>
 * </ul>
 */
@Configuration
public class SentinelConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelConfig.class);

    /**
     * 系统负载阈值，超过则触发保护
     * 仅在Linux/Unix系统有效，建议设置为CPU核心数*2
     */
    @Value("${sentinel.system.load-threshold:-1}")
    private double loadThreshold;

    /**
     * 平均响应时间阈值（毫秒）
     * 超过则触发保护，建议根据业务SLA设置
     */
    @Value("${sentinel.system.avg-rt-threshold:-1}")
    private long avgRtThreshold;

    /**
     * 最大并发线程数
     * 超过则触发保护，建议根据线程池配置设置
     */
    @Value("${sentinel.system.max-thread-threshold:-1}")
    private int maxThreadThreshold;

    /**
     * 系统入口QPS阈值
     * 超过则触发保护
     */
    @Value("${sentinel.system.qps-threshold:-1}")
    private int qpsThreshold;

    /**
     * CPU使用率阈值（0-1）
     * 超过则触发保护
     */
    @Value("${sentinel.system.cpu-threshold:0.8}")
    private double cpuThreshold;

    /**
     * Sentinel切面Bean
     */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    /**
     * 初始化系统自适应保护规则
     */
    @PostConstruct
    public void initSystemRules() {
        List<SystemRule> rules = new ArrayList<>();

        // CPU使用率保护（推荐启用）
        if (cpuThreshold > 0 && cpuThreshold <= 1) {
            SystemRule cpuRule = new SystemRule();
            cpuRule.setHighestCpuUsage(cpuThreshold);
            rules.add(cpuRule);
            log.info("Sentinel系统保护 - CPU阈值: {}", cpuThreshold);
        }

        // 系统负载保护（仅Linux/Unix有效）
        if (loadThreshold > 0) {
            SystemRule loadRule = new SystemRule();
            loadRule.setHighestSystemLoad(loadThreshold);
            rules.add(loadRule);
            log.info("Sentinel系统保护 - 负载阈值: {}", loadThreshold);
        }

        // 平均响应时间保护
        if (avgRtThreshold > 0) {
            SystemRule rtRule = new SystemRule();
            rtRule.setAvgRt(avgRtThreshold);
            rules.add(rtRule);
            log.info("Sentinel系统保护 - 平均响应时间阈值: {}ms", avgRtThreshold);
        }

        // 最大并发线程数保护
        if (maxThreadThreshold > 0) {
            SystemRule threadRule = new SystemRule();
            threadRule.setMaxThread(maxThreadThreshold);
            rules.add(threadRule);
            log.info("Sentinel系统保护 - 最大并发线程数: {}", maxThreadThreshold);
        }

        // QPS保护
        if (qpsThreshold > 0) {
            SystemRule qpsRule = new SystemRule();
            qpsRule.setQps(qpsThreshold);
            rules.add(qpsRule);
            log.info("Sentinel系统保护 - QPS阈值: {}", qpsThreshold);
        }

        if (!rules.isEmpty()) {
            SystemRuleManager.loadRules(rules);
            log.info("Sentinel系统自适应保护规则已加载，共{}条规则", rules.size());
        } else {
            log.info("未配置Sentinel系统自适应保护规则");
        }
    }
}
