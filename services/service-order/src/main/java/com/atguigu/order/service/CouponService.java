package com.atguigu.order.service;

import com.atguigu.order.bean.Coupon;

import java.util.List;

public interface CouponService {

    /**
     * 管理员创建优惠券（内存 + Redis 库存）
     */
    Coupon createCoupon(Coupon coupon);

    /**
     * 用户领取优惠券（高并发下扣减 Redis 库存）
     */
    boolean acquireCoupon(Long couponId, Long userId);

    /**
     * 下单时查询并校验优惠券，返回可用优惠券信息
     */
    Coupon getValidCouponForUse(Long couponId, Long userId);

    /**
     * 根据ID查询优惠券
     */
    Coupon getCouponById(Long couponId);

    /**
     * 查询用户的所有优惠券
     */
    List<Coupon> listCouponsByUserId(Long userId);

    /**
     * 查询用户可用优惠券
     */
    List<Coupon> listValidCouponsForUser(Long userId);

    /**
     * 查询所有优惠券（管理员）
     */
    List<Coupon> listAllCoupons();

    /**
     * 更新优惠券
     */
    Coupon updateCoupon(Coupon coupon);

    /**
     * 删除优惠券
     */
    void deleteCoupon(Long couponId);

    /**
     * 优惠券过期处理
     */
    void expireCoupons();
}
