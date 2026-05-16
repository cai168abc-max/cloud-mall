package com.atguigu.order.mapper;

import com.atguigu.order.bean.OrderItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("SELECT * FROM order_item WHERE order_id = #{orderId}")
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);

    @Select("SELECT * FROM order_item WHERE product_id = #{productId}")
    List<OrderItem> selectByProductId(@Param("productId") Long productId);

    @Select("SELECT COUNT(*) FROM order_item WHERE product_id = #{productId}")
    long countByProductId(@Param("productId") Long productId);

    @Insert("INSERT INTO order_item (order_id, product_id, product_name, price, quantity, create_time) " +
            "VALUES (#{orderId}, #{productId}, #{productName}, #{price}, #{quantity}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertOrderItem(OrderItem orderItem);

    @Delete("DELETE FROM order_item WHERE order_id = #{orderId}")
    int deleteByOrderId(@Param("orderId") Long orderId);
}
