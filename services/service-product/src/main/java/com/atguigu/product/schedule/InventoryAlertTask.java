package com.atguigu.product.schedule;

import com.atguigu.product.service.InventoryAlertService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 库存预警检查定时任务
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>每5分钟执行一次库存预警检查</li>
 *   <li>检查所有启用预警配置的商品库存</li>
 *   <li>库存低于阈值时发送预警通知</li>
 * </ul>
 * 
 * <p>异常处理：</p>
 * <ul>
 *   <li>定时任务执行失败：记录日志，下次继续</li>
 *   <li>Redis不可用：查询数据库预警记录判断是否重复</li>
 *   <li>MQ不可用：直接调用通知服务</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class InventoryAlertTask {

    private static final Logger log = LoggerFactory.getLogger(InventoryAlertTask.class);

    private final InventoryAlertService inventoryAlertService;

    /**
     * 定时检查库存预警
     * 每5分钟执行一次
     */
    @Scheduled(fixedDelay = 300000) // 5分钟 = 300000毫秒
    public void checkInventoryAlert() {
        log.info("========== 开始执行库存预警检查任务 ==========");

        try {
            inventoryAlertService.checkInventoryAlert();
            log.info("========== 库存预警检查任务执行完成 ==========");
        } catch (Exception e) {
            log.error("========== 库存预警检查任务执行失败 ==========", e);
            // 记录日志，下次继续执行
        }
    }
}
