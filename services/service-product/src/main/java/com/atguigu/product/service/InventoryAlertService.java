package com.atguigu.product.service;

import com.atguigu.product.bean.InventoryAlertConfig;
import com.atguigu.product.bean.InventoryAlertLog;

import java.util.List;

/**
 * 库存预警服务接口
 */
public interface InventoryAlertService {

    /**
     * 检查库存预警
     * 定时任务调用，检查所有启用预警配置的商品库存
     */
    void checkInventoryAlert();

    /**
     * 手动触发预警
     * 兜底方案：当预警丢失时可手动触发
     *
     * @param productId 商品ID
     * @return 是否触发成功
     */
    boolean triggerAlertManually(Long productId);

    /**
     * 创建或更新预警配置
     *
     * @param config 预警配置
     * @return 是否成功
     */
    boolean saveOrUpdateConfig(InventoryAlertConfig config);

    /**
     * 查询商品的预警配置
     *
     * @param productId 商品ID
     * @return 预警配置
     */
    InventoryAlertConfig getConfigByProductId(Long productId);

    /**
     * 查询所有启用的预警配置
     *
     * @return 预警配置列表
     */
    List<InventoryAlertConfig> listAllEnabledConfigs();

    /**
     * 更新预警配置状态
     *
     * @param id     配置ID
     * @param status 状态：1启用 0禁用
     * @return 是否成功
     */
    boolean updateConfigStatus(Long id, Integer status);

    /**
     * 查询商品预警记录
     *
     * @param productId 商品ID
     * @param limit     限制数量
     * @return 预警记录列表
     */
    List<InventoryAlertLog> listAlertLogs(Long productId, int limit);

    /**
     * 处理预警通知
     * MQ消费者调用
     *
     * @param alertLogId 预警记录ID
     */
    void processAlertNotification(Long alertLogId);

    /**
     * 检查是否可以发送预警
     * 防止预警风暴：单商品每天最多发送3次预警
     *
     * @param productId 商品ID
     * @return 是否可以发送
     */
    boolean canSendAlert(Long productId);
}
