package com.atguigu.order.mapper;

import com.atguigu.order.bean.OrderReview;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 订单评价Mapper
 */
@Mapper
public interface OrderReviewMapper extends BaseMapper<OrderReview> {

    /**
     * 根据订单ID查询评价
     * @param orderId 订单ID
     * @return 评价信息
     */
    @Select("SELECT * FROM order_review WHERE order_id = #{orderId}")
    OrderReview selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据用户ID分页查询评价
     * @param page 分页对象
     * @param userId 用户ID
     * @return 分页评价列表
     */
    @Select("SELECT * FROM order_review WHERE user_id = #{userId} ORDER BY create_time DESC")
    IPage<OrderReview> selectByUserId(Page<OrderReview> page, @Param("userId") Long userId);

    /**
     * 根据商品ID分页查询评价
     * @param page 分页对象
     * @param productId 商品ID
     * @return 分页评价列表
     */
    @Select("SELECT * FROM order_review WHERE product_id = #{productId} AND status = 1 ORDER BY create_time DESC")
    IPage<OrderReview> selectByProductId(Page<OrderReview> page, @Param("productId") Long productId);

    /**
     * 根据商家ID分页查询评价
     * @param page 分页对象
     * @param merchantId 商家ID
     * @return 分页评价列表
     */
    @Select("SELECT * FROM order_review WHERE merchant_id = #{merchantId} ORDER BY create_time DESC")
    IPage<OrderReview> selectByMerchantId(Page<OrderReview> page, @Param("merchantId") Long merchantId);

    /**
     * 统计商品评价数量
     * @param productId 商品ID
     * @return 评价数量
     */
    @Select("SELECT COUNT(*) FROM order_review WHERE product_id = #{productId} AND status = 1")
    long countByProductId(@Param("productId") Long productId);

    /**
     * 计算商品平均评分
     * @param productId 商品ID
     * @return 平均评分
     */
    @Select("SELECT IFNULL(AVG(rating), 0) FROM order_review WHERE product_id = #{productId} AND status = 1")
    Double avgRatingByProductId(@Param("productId") Long productId);

    /**
     * 统计商品各评分数量
     * @param productId 商品ID
     * @return 各评分数量列表
     */
    @Select("SELECT rating, COUNT(*) as count FROM order_review WHERE product_id = #{productId} AND status = 1 GROUP BY rating ORDER BY rating DESC")
    List<RatingCount> countGroupByRating(@Param("productId") Long productId);

    /**
     * 检查订单是否已评价
     * @param orderId 订单ID
     * @return 是否存在评价
     */
    @Select("SELECT COUNT(*) > 0 FROM order_review WHERE order_id = #{orderId}")
    boolean existsByOrderId(@Param("orderId") Long orderId);

    /**
     * 插入评价
     * @param review 评价信息
     * @return 影响行数
     */
    @Insert("INSERT INTO order_review (order_id, user_id, merchant_id, product_id, rating, content, images, anonymous, status, create_time, update_time) " +
            "VALUES (#{orderId}, #{userId}, #{merchantId}, #{productId}, #{rating}, #{content}, #{images}, #{anonymous}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertReview(OrderReview review);

    /**
     * 更新评价状态
     * @param id 评价ID
     * @param status 状态
     * @return 影响行数
     */
    @Update("UPDATE order_review SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 评分统计内部类
     */
    interface RatingCount {
        Integer getRating();
        Long getCount();
    }
}
