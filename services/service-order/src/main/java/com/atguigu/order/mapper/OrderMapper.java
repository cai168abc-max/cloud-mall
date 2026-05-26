package com.atguigu.order.mapper;

import com.atguigu.order.bean.Order;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 性能优化：添加分页参数，避免返回全量数据
     * @param page MyBatis-Plus分页对象
     * @param userId 用户ID
     * @return 分页订单列表
     */
    @Select("SELECT * FROM `orders` WHERE user_id = #{userId} ORDER BY create_time DESC")
    IPage<Order> selectByUserId(Page<Order> page, @Param("userId") Long userId);

    /**
     * 性能优化：添加分页参数，避免返回全量数据
     * @param page MyBatis-Plus分页对象
     * @param userId 用户ID
     * @param status 订单状态
     * @return 分页订单列表
     */
    @Select("SELECT * FROM `orders` WHERE user_id = #{userId} AND status = #{status} ORDER BY create_time DESC")
    IPage<Order> selectByUserIdAndStatus(Page<Order> page, @Param("userId") Long userId, @Param("status") String status);

    /**
     * 性能优化：添加分页参数，避免返回全量数据
     * @param page MyBatis-Plus分页对象
     * @param merchantId 商家ID
     * @return 分页订单列表
     */
    @Select("SELECT * FROM `orders` WHERE merchant_id = #{merchantId} ORDER BY create_time DESC")
    IPage<Order> selectByMerchantId(Page<Order> page, @Param("merchantId") Long merchantId);

    @Select("SELECT * FROM `orders` WHERE create_time >= #{startTime} AND create_time <= #{endTime}")
    List<Order> selectByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM `orders` ORDER BY create_time DESC")
    IPage<Order> selectPageByCondition(Page<Order> page);

    @Update("UPDATE `orders` SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE `orders` SET status = #{status}, pay_time = NOW(), update_time = NOW() WHERE id = #{id}")
    int updateStatusToPaid(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE `orders` SET status = #{status}, ship_time = NOW(), update_time = NOW() WHERE id = #{id}")
    int updateStatusToShipped(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE `orders` SET status = #{status}, complete_time = NOW(), update_time = NOW() WHERE id = #{id}")
    int updateStatusToCompleted(@Param("id") Long id, @Param("status") String status);

    @Insert("INSERT INTO `orders` (user_id, merchant_id, nick_name, address, total_price, discount_amount, pay_amount, status, coupon_id, create_time, update_time) "
            + "VALUES (#{userId}, #{merchantId}, #{nickName}, #{address}, #{totalPrice}, #{discountAmount}, #{payAmount}, #{status}, #{couponId}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertOrder(Order order);

    @Delete("DELETE FROM `orders` WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("<script>"
            + "SELECT id, user_id, merchant_id, nick_name, address, total_price, discount_amount, pay_amount, status, coupon_id, create_time, update_time "
            + "FROM `orders` WHERE id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + "</script>")
    List<Order> batchSelectByIds(@Param("ids") List<Long> ids);

    @Select("SELECT COUNT(*) FROM `orders` WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM `orders` WHERE merchant_id = #{merchantId}")
    long countByMerchantId(@Param("merchantId") Long merchantId);

    /**
     * 查询超时订单（带分页）
     * 性能优化：使用分页参数避免一次返回过多数据
     * 
     * @param page MyBatis-Plus分页对象
     * @param timeoutMinutes 超时时间（分钟）
     * @return 分页超时订单列表
     */
    @Select("SELECT * FROM `orders` WHERE status = 'CREATED' AND create_time < DATE_SUB(NOW(), INTERVAL #{timeoutMinutes} MINUTE) ORDER BY create_time ASC")
    IPage<Order> selectTimeoutOrders(Page<Order> page, @Param("timeoutMinutes") int timeoutMinutes);

    /**
     * 查询超时订单（兼容旧接口，默认返回前100条）
     * @deprecated 建议使用分页方法 {@link #selectTimeoutOrders(Page, int)}
     * @param timeoutMinutes 超时时间（分钟）
     * @return 超时订单列表
     */
    @Deprecated
    @Select("SELECT * FROM `orders` WHERE status = 'CREATED' AND create_time < DATE_SUB(NOW(), INTERVAL #{timeoutMinutes} MINUTE) ORDER BY create_time ASC LIMIT 100")
    List<Order> selectTimeoutOrders(@Param("timeoutMinutes") int timeoutMinutes);

    // ==================== 批量操作方法 ====================

    /**
     * 批量更新订单状态为已支付
     * 性能优化：使用单条SQL批量更新，避免循环逐个更新
     * 
     * @param ids 订单ID列表
     * @param status 目标状态
     * @return 更新记录数
     */
    @Update("<script>" +
            "UPDATE `orders` SET status = #{status}, pay_time = NOW(), update_time = NOW() WHERE id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + "</script>")

    int batchUpdateStatusToPaid(@Param("ids") List<Long> ids, @Param("status") String status);

    /**
     * 批量更新订单状态为已发货
     * 性能优化：使用单条SQL批量更新，避免循环逐个更新
     * 
     * @param ids 订单ID列表
     * @param status 目标状态
     * @return 更新记录数
     */
    @Update("<script>"
            + "UPDATE `orders` SET status = #{status}, ship_time = NOW(), update_time = NOW() WHERE id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + "</script>")
    int batchUpdateStatusToShipped(@Param("ids") List<Long> ids, @Param("status") String status);

    /**
     * 批量更新订单状态为已完成
     * 性能优化：使用单条SQL批量更新，避免循环逐个更新
     * 
     * @param ids 订单ID列表
     * @param status 目标状态
     * @return 更新记录数
     */
    @Update("<script>"
            + "UPDATE `orders` SET status = #{status}, complete_time = NOW(), update_time = NOW() WHERE id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + "</script>")
    int batchUpdateStatusToCompleted(@Param("ids") List<Long> ids, @Param("status") String status);

    /**
     * 批量更新订单状态
     * 性能优化：使用单条SQL批量更新，避免循环逐个更新
     * 
     * @param ids 订单ID列表
     * @param status 目标状态
     * @return 更新记录数
     */
    @Update("<script>"
            + "UPDATE `orders` SET status = #{status}, update_time = NOW() WHERE id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + "</script>")
    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") String status);

    /**
     * 批量查询订单（带状态过滤）
     * 
     * @param ids 订单ID列表
     * @param status 订单状态
     * @return 订单列表
     */
    @Select("<script>"
            + "SELECT * FROM `orders` WHERE status = #{status} AND id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + "</script>")
    List<Order> batchSelectByIdsAndStatus(@Param("ids") List<Long> ids, @Param("status") String status);

    // ==================== 评价相关方法 ====================

    /**
     * 更新订单评价状态
     * @param orderId 订单ID
     * @param reviewed 是否已评价：0否 1是
     * @return 影响行数
     */
    @Update("UPDATE `orders` SET reviewed = #{reviewed}, update_time = NOW() WHERE id = #{orderId}")
    int updateReviewed(@Param("orderId") Long orderId, @Param("reviewed") Integer reviewed);
}
