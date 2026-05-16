package com.atguigu.order.mapper;

import com.atguigu.order.bean.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    @Select("SELECT * FROM coupon WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<Coupon> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM coupon WHERE user_id = #{userId} AND status = #{status} ORDER BY create_time DESC")
    List<Coupon> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    @Select("SELECT * FROM coupon WHERE merchant_id = #{merchantId} ORDER BY create_time DESC")
    List<Coupon> selectByMerchantId(@Param("merchantId") Long merchantId);

    @Select("SELECT * FROM coupon WHERE status = #{status} ORDER BY create_time DESC")
    List<Coupon> selectByStatus(@Param("status") String status);

    @Select("SELECT * FROM coupon WHERE valid_from <= #{now} AND valid_to >= #{now}")
    List<Coupon> selectAvailableCoupons(@Param("now") LocalDateTime now);

    @Select("SELECT * FROM coupon ORDER BY create_time DESC")
    IPage<Coupon> selectPageByCondition(Page<Coupon> page);

    @Update("UPDATE coupon SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Insert("INSERT INTO coupon (merchant_id, user_id, name, amount, threshold, stock, status, valid_from, valid_to, create_time, update_time) " +
            "VALUES (#{merchantId}, #{userId}, #{name}, #{amount}, #{threshold}, #{stock}, #{status}, #{validFrom}, #{validTo}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertCoupon(Coupon coupon);

    @Delete("DELETE FROM coupon WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("<script>" +
            "SELECT id, merchant_id, user_id, name, amount, threshold, stock, status, valid_from, valid_to, create_time, update_time " +
            "FROM coupon WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<Coupon> batchSelectByIds(@Param("ids") List<Long> ids);

    @Select("SELECT * FROM coupon WHERE user_id = #{userId} AND status = 'ACTIVE' AND valid_from <= #{now} AND valid_to >= #{now}")
    List<Coupon> selectValidCouponsForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("UPDATE coupon SET status = 'EXPIRED', update_time = NOW() WHERE valid_to < #{now} AND status = 'ACTIVE'")
    int expireCoupons(@Param("now") LocalDateTime now);

    @Update("UPDATE coupon SET stock = stock - 1, update_time = NOW() WHERE id = #{id} AND stock > 0")
    int decrementStock(@Param("id") Long id);
}
