package com.atguigu.order.controller;

import com.atguigu.common.result.R;
import com.atguigu.common.enums.UserRole;
import com.atguigu.order.bean.Coupon;
import com.atguigu.common.context.UserContext;
import com.atguigu.order.service.CouponService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupon")
@Validated
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @GetMapping("/{id}")
    public R getCoupon(@PathVariable("id") Long couponId) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Coupon coupon = couponService.getCouponById(couponId);
        if (coupon == null) {
            return R.error(404, "优惠券不存在");
        }
        return R.ok("查询优惠券成功", coupon);
    }

    @GetMapping("/my")
    public R listMyCoupons() {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        List<Coupon> list = couponService.listCouponsByUserId(userId);
        return R.ok("查询我的优惠券成功", list);
    }

    @GetMapping("/my/valid")
    public R listValidCoupons() {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        List<Coupon> list = couponService.listValidCouponsForUser(userId);
        return R.ok("查询可用优惠券成功", list);
    }

    @PostMapping("/acquire")
    public R acquireCoupon(@RequestParam("couponId") Long couponId) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        boolean success = couponService.acquireCoupon(couponId, userId);
        if (success) {
            return R.ok("领取优惠券成功", null);
        } else {
            return R.error(400, "领取失败，优惠券已领完或不在领取时间");
        }
    }

    @PostMapping("/use")
    public R useCoupon(@RequestParam("couponId") Long couponId) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        Coupon coupon = couponService.getValidCouponForUse(couponId, userId);
        if (coupon == null) {
            return R.error(400, "优惠券不可用");
        }
        return R.ok("优惠券可用", coupon);
    }

    @GetMapping("/manage")
    public R listAllCoupons() {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        if (UserContext.get().getRole() != UserRole.MERCHANT && UserContext.get().getRole() != UserRole.ADMIN) {
            return R.error(403, "仅商家或管理员可以管理优惠券");
        }
        List<Coupon> list = couponService.listAllCoupons();
        return R.ok("查询优惠券列表成功", list);
    }

    @PostMapping("/manage/create")
    public R createCoupon(@RequestBody Coupon coupon) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        if (UserContext.get().getRole() != UserRole.MERCHANT && UserContext.get().getRole() != UserRole.ADMIN) {
            return R.error(403, "仅商家或管理员可以创建优惠券");
        }
        Coupon created = couponService.createCoupon(coupon);
        return R.ok("创建优惠券成功", created);
    }

    @PutMapping("/manage/{id}")
    public R updateCoupon(@PathVariable("id") Long couponId, @RequestBody Coupon coupon) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        if (UserContext.get().getRole() != UserRole.MERCHANT && UserContext.get().getRole() != UserRole.ADMIN) {
            return R.error(403, "仅商家或管理员可以修改优惠券");
        }
        coupon.setId(couponId);
        Coupon updated = couponService.updateCoupon(coupon);
        return R.ok("更新优惠券成功", updated);
    }

    @DeleteMapping("/manage/{id}")
    public R deleteCoupon(@PathVariable("id") Long couponId) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        if (UserContext.get().getRole() != UserRole.MERCHANT && UserContext.get().getRole() != UserRole.ADMIN) {
            return R.error(403, "仅商家或管理员可以删除优惠券");
        }
        couponService.deleteCoupon(couponId);
        return R.ok("删除优惠券成功", null);
    }

    @PostMapping("/manage/expire")
    public R expireCoupons() {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        if (UserContext.get().getRole() != UserRole.ADMIN) {
            return R.error(403, "仅管理员可以执行过期操作");
        }
        couponService.expireCoupons();
        return R.ok("优惠券过期处理完成", null);
    }
}
