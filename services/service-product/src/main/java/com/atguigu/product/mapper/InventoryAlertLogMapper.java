package com.atguigu.product.mapper;

import com.atguigu.product.bean.InventoryAlertLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存预警记录Mapper
 */
@Mapper
public interface InventoryAlertLogMapper extends BaseMapper<InventoryAlertLog> {

    /**
     * 查询商品最近的预警记录
     */
    @Select("SELECT * FROM inventory_alert_log WHERE product_id = #{productId} ORDER BY create_time DESC LIMIT 1")
    InventoryAlertLog selectLatestByProductId(@Param("productId") Long productId);

    /**
     * 查询商品指定时间范围内的预警记录数量
     */
    @Select("SELECT COUNT(*) FROM inventory_alert_log WHERE product_id = #{productId} AND create_time >= #{startTime}")
    int countByProductIdAndTime(@Param("productId") Long productId, @Param("startTime") LocalDateTime startTime);

    /**
     * 更新预警记录状态
     */
    @Update("UPDATE inventory_alert_log SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 查询待发送的预警记录
     */
    @Select("SELECT * FROM inventory_alert_log WHERE status = 'PENDING' ORDER BY create_time ASC LIMIT #{limit}")
    List<InventoryAlertLog> selectPending(@Param("limit") int limit);

    /**
     * 查询商品当天预警记录数量
     */
    @Select("SELECT COUNT(*) FROM inventory_alert_log WHERE product_id = #{productId} AND DATE(create_time) = CURDATE()")
    int countTodayByProductId(@Param("productId") Long productId);

    @Insert("INSERT INTO inventory_alert_log (product_id, merchant_id, stock, threshold, status, create_time) " +
            "VALUES (#{productId}, #{merchantId}, #{stock}, #{threshold}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertAlertLog(InventoryAlertLog alertLog);
}
