package com.atguigu.product.mapper;

import com.atguigu.product.bean.ProductAuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 商品审核日志Mapper
 */
@Mapper
public interface ProductAuditLogMapper extends BaseMapper<ProductAuditLog> {

    /**
     * 批量插入审核日志
     */
    @Insert("<script>"
            + "INSERT INTO product_audit_log (product_id, merchant_id, auditor_id, before_status, after_status, reason, create_time) VALUES "
            + "<foreach item='log' collection='logs' separator=','>"
            + "(#{log.productId}, #{log.merchantId}, #{log.auditorId}, #{log.beforeStatus}, #{log.afterStatus}, #{log.reason}, NOW())"
            + "</foreach>"
            + "</script>")
    int batchInsert(@Param("logs") List<ProductAuditLog> logs);

    /**
     * 根据商品ID查询最新审核记录
     */
    @Select("SELECT * FROM product_audit_log WHERE product_id = #{productId} ORDER BY create_time DESC LIMIT 1")
    ProductAuditLog selectLatestByProductId(@Param("productId") Long productId);

    /**
     * 根据商品ID查询审核历史
     */
    @Select("SELECT * FROM product_audit_log WHERE product_id = #{productId} ORDER BY create_time DESC")
    List<ProductAuditLog> selectByProductId(@Param("productId") Long productId);

    /**
     * 根据商家ID查询审核记录
     */
    @Select("SELECT * FROM product_audit_log WHERE merchant_id = #{merchantId} ORDER BY create_time DESC")
    List<ProductAuditLog> selectByMerchantId(@Param("merchantId") Long merchantId);
}
