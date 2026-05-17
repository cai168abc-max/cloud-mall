package com.atguigu.order.controller;

import com.atguigu.common.context.UserContext;
import com.atguigu.common.enums.UserRole;
import com.atguigu.common.result.R;
import com.atguigu.order.bean.OrderReview;
import com.atguigu.order.service.OrderReviewService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单评价控制器
 */
@RestController
@RequestMapping("/api/order/review")
@Validated
@Tag(name = "订单评价管理", description = "订单评价相关接口")
@RequiredArgsConstructor
public class OrderReviewController {

    private final OrderReviewService orderReviewService;

    @PostMapping
    @Operation(summary = "创建评价", description = "用户对已完成订单进行评价")
    public R createReview(
            @Parameter(description = "订单ID") @RequestParam @NotNull Long orderId,
            @Parameter(description = "评分(1-5)") @RequestParam @NotNull @Min(1) @Max(5) Integer rating,
            @Parameter(description = "评价内容") @RequestParam(required = false) String content,
            @Parameter(description = "评价图片URL列表") @RequestParam(required = false) List<String> images,
            @Parameter(description = "是否匿名") @RequestParam(required = false, defaultValue = "false") Boolean anonymous) {

        // 登录校验
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        Long userId = UserContext.get().getId();
        OrderReview review = orderReviewService.createReview(orderId, userId, rating, content, images, anonymous);
        return R.ok("评价成功", review);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取评价详情", description = "根据评价ID获取评价详情")
    public R getReview(
            @Parameter(description = "评价ID") @PathVariable @NotNull Long id) {
        OrderReview review = orderReviewService.getReviewById(id);
        if (review == null) {
            return R.notFound("评价不存在");
        }
        return R.ok("查询成功", review);
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "根据订单ID获取评价", description = "根据订单ID获取评价信息")
    public R getReviewByOrderId(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long orderId) {
        OrderReview review = orderReviewService.getReviewByOrderId(orderId);
        if (review == null) {
            return R.notFound("该订单暂无评价");
        }
        return R.ok("查询成功", review);
    }

    @GetMapping("/my")
    @Operation(summary = "获取我的评价列表", description = "分页获取当前用户的评价列表")
    public R listMyReviews(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize) {

        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        Long userId = UserContext.get().getId();
        IPage<OrderReview> page = orderReviewService.listReviewsByUserId(userId, pageNum, pageSize);
        return R.ok("查询成功", page);
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "获取商品评价列表", description = "分页获取商品的评价列表")
    public R listProductReviews(
            @Parameter(description = "商品ID") @PathVariable @NotNull Long productId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize) {

        IPage<OrderReview> page = orderReviewService.listReviewsByProductId(productId, pageNum, pageSize);
        return R.ok("查询成功", page);
    }

    @GetMapping("/merchant")
    @Operation(summary = "获取商家评价列表", description = "分页获取商家的评价列表")
    public R listMerchantReviews(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize) {

        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        R roleCheck = requireRole(UserRole.MERCHANT, UserRole.ADMIN);
        if (roleCheck != null) {
            return roleCheck;
        }

        Long merchantId = UserContext.get().getId();
        IPage<OrderReview> page = orderReviewService.listReviewsByMerchantId(merchantId, pageNum, pageSize);
        return R.ok("查询成功", page);
    }

    @GetMapping("/stats/{productId}")
    @Operation(summary = "获取商品评价统计", description = "获取商品的评价数量、平均分、各评分分布")
    public R getProductReviewStats(
            @Parameter(description = "商品ID") @PathVariable @NotNull Long productId) {

        Map<String, Object> stats = orderReviewService.getProductReviewStats(productId);
        return R.ok("查询成功", stats);
    }

    @GetMapping("/count/{productId}")
    @Operation(summary = "获取商品评价数量", description = "获取商品的评价数量（从缓存）")
    public R getProductReviewCount(
            @Parameter(description = "商品ID") @PathVariable @NotNull Long productId) {

        Long count = orderReviewService.getProductReviewCount(productId);
        return R.ok("查询成功", count);
    }

    @GetMapping("/avg/{productId}")
    @Operation(summary = "获取商品平均评分", description = "获取商品的平均评分（从缓存）")
    public R getProductAvgRating(
            @Parameter(description = "商品ID") @PathVariable @NotNull Long productId) {

        Double avgRating = orderReviewService.getProductAvgRating(productId);
        return R.ok("查询成功", avgRating);
    }

    @GetMapping("/check/{orderId}")
    @Operation(summary = "检查订单是否已评价", description = "检查指定订单是否已评价")
    public R checkReviewed(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long orderId) {

        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        boolean reviewed = orderReviewService.hasReviewed(orderId);
        return R.ok("查询成功", reviewed);
    }

    @PutMapping("/{reviewId}/status")
    @Operation(summary = "更新评价状态", description = "管理员隐藏/显示评价")
    public R updateReviewStatus(
            @Parameter(description = "评价ID") @PathVariable @NotNull Long reviewId,
            @Parameter(description = "状态(1正常,0隐藏)") @RequestParam @NotNull Integer status) {

        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        R roleCheck = requireRole(UserRole.ADMIN);
        if (roleCheck != null) {
            return roleCheck;
        }

        Long operator = UserContext.get().getId();
        boolean success = orderReviewService.updateReviewStatus(reviewId, status, operator);
        return success ? R.ok("更新成功") : R.error("更新失败");
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 检查用户是否已登录
     * @return 未登录返回错误响应，已登录返回null
     */
    private R requireLogin() {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        return null;
    }

    /**
     * 检查用户是否具有指定角色
     * @param allowedRoles 允许的角色列表
     * @return 无权限返回错误响应，有权限返回null
     */
    private R requireRole(UserRole... allowedRoles) {
        UserRole currentRole = UserContext.get().getRole();
        for (UserRole role : allowedRoles) {
            if (currentRole == role) {
                return null;
            }
        }
        return R.error(403, "无权限");
    }
}
