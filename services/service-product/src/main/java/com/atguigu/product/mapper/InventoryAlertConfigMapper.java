package com.atguigu.product.mapper;

import com.atguigu.product.bean.InventoryAlertConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 库存预警配置Mapper
 */
@Mapper
public interface InventoryAlertConfigMapper extends BaseMapper<InventoryAlertConfig> {

    /**
     * 查询所有启用的预警配置
     */
    @Select("SELECT * FROM inventory_alert_config WHERE status = 1")
    List<InventoryAlertConfig> selectAllEnabled();

    /**
     * 根据商品ID查询预警配置
     */
    @Select("SELECT * FROM inventory_alert_config WHERE product_id = #{productId}")
    InventoryAlertConfig selectByProductId(@Param("productId") Long productId);

    /**
     * 插入或更新预警配置
     */
    @Insert("INSERT INTO inventory_alert_config (product_id, threshold, alert_interval, status, create_time, update_time) "
            + "VALUES (#{productId}, #{threshold}, #{alertInterval}, #{status}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE threshold = #{threshold}, alert_interval = #{alertInterval}, "
            + "status = #{status}, update_time = NOW()")
    int insertOrUpdate(InventoryAlertConfig config);

    /**
     * 更新预警配置状态
     */
    @Update("UPDATE inventory_alert_config SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
