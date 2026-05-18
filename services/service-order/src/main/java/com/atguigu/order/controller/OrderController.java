package com.atguigu.order.controller;

import com.atguigu.common.annotation.RequirePermission;
import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.context.UserContext;
import com.atguigu.common.enums.RequireMode;
import com.atguigu.common.result.R;
import com.atguigu.order.bean.Order;
import com.atguigu.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单控制器
 * 使用权限注解进行权限控制
 */
@RestController
@RequestMapping("/api/order")
@Validated
@Tag(name = "订单管理", description = "订单相关接口")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/{id}")
    @Operation(summary = "获取订单详情", description = "根据订单ID获取订单详情")
    @RequirePermission("order:read")
    public R getOrder(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long id) {
        Order order = orderService.getOrderById(id);
        if (order == null) {
            return R.error(404, "订单不存在");
        }
        return R.ok("查询订单成功", order);
    }

    @GetMapping("/my")
    @Operation(summary = "获取我的订单列表", description = "获取当前用户的订单列表")
    @RequirePermission("order:read")
    public R listMyOrders() {
        Long userId = UserContext.get().getId();
        List<Order> list = orderService.listOrdersByUserId(userId);
        return R.ok("查询订单列表成功", list);
    }

    @GetMapping("/merchant")
    @Operation(summary = "获取商家订单列表", description = "获取当前商家的订单列表")
    @RequirePermission(value = {"order:read", "merchant:manage"}, mode = RequireMode.ANY)
    public R listMerchantOrders() {
        UserInfo user = UserContext.get();
        Long merchantId = user.getId();
        List<Order> list = orderService.listOrdersByMerchantId(merchantId);
        return R.ok("查询商家订单成功", list);
    }

    @PostMapping
    @Operation(summary = "创建订单", description = "创建新订单")
    @RequirePermission("order:write")
    public R createOrder(
            @Parameter(description = "商品ID") @RequestParam @NotNull Long productId,
            @Parameter(description = "优惠券ID") @RequestParam(value = "couponId", required = false) Long couponId) {
        Long userId = UserContext.get().getId();
        Order order;
        if (couponId != null) {
            order = orderService.createOrderWithCoupon(productId, userId, couponId);
        } else {
            order = orderService.createOrder(productId, userId);
        }
        return R.ok("创建订单成功", order);
    }

    @PostMapping("/cart")
    @Operation(summary = "从购物车创建订单", description = "从购物车批量创建订单")
    @RequirePermission("order:write")
    public R createOrderFromCart() {
        Long userId = UserContext.get().getId();
        Map<String, Object> result = orderService.createOrdersFromCart(userId);
        return R.ok("批量创建订单成功", result);
    }

    @PutMapping("/{id}/pay")
    @Operation(summary = "支付订单", description = "支付指定订单")
    @RequirePermission("order:write")
    public R payOrder(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long id) {
        Long userId = UserContext.get().getId();
        Order order = orderService.payOrder(id, userId);
        return order != null ? R.ok("支付成功") : R.error("支付失败");
    }

    @PutMapping("/{id}/ship")
    @Operation(summary = "发货", description = "商家发货")
    @RequirePermission("order:ship")
    public R shipOrder(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long id) {
        Long merchantId = UserContext.get().getId();
        Order order = orderService.shipOrder(id, merchantId);
        return order != null ? R.ok("发货成功") : R.error("发货失败");
    }

    @PutMapping("/{id}/complete")
    @Operation(summary = "确认收货", description = "用户确认收货")
    @RequirePermission("order:write")
    public R completeOrder(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long id) {
        Long userId = UserContext.get().getId();
        Order order = orderService.completeOrder(id, userId);
        return order != null ? R.ok("确认收货成功") : R.error("确认收货失败");
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "取消订单", description = "取消指定订单")
    @RequirePermission("order:cancel")
    public R cancelOrder(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long id) {
        Long userId = UserContext.get().getId();
        Order order = orderService.cancelOrder(id, userId);
        return order != null ? R.ok("取消订单成功") : R.error("取消订单失败");
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "申请退款", description = "用户申请退款")
    @RequirePermission("order:write")
    public R applyRefund(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long id) {
        Long userId = UserContext.get().getId();
        Order order = orderService.applyRefund(id, userId);
        return order != null ? R.ok("退款申请已提交") : R.error("退款申请失败，订单状态不正确");
    }

    @PutMapping("/{id}/refund/approve")
    @Operation(summary = "批准退款", description = "商家批准退款")
    @RequirePermission("order:ship")
    public R approveRefund(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long id) {
        Long merchantId = UserContext.get().getId();
        boolean success = orderService.approveRefund(id, merchantId);
        return success ? R.ok("退款已批准") : R.error("退款批准失败");
    }

    @PutMapping("/{id}/refund/reject")
    @Operation(summary = "拒绝退款", description = "商家拒绝退款")
    @RequirePermission("order:ship")
    public R rejectRefund(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long id) {
        Long merchantId = UserContext.get().getId();
        boolean success = orderService.rejectRefund(id, merchantId);
        return success ? R.ok("退款已拒绝") : R.error("退款拒绝失败");
    }

    @PostMapping("/{id}/aftersale")
    @Operation(summary = "申请售后", description = "用户申请售后")
    @RequirePermission("order:write")
    public R applyAfterSale(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long id,
            @Parameter(description = "售后原因") @RequestParam String reason) {
        Long userId = UserContext.get().getId();
        Order order = orderService.applyAfterSale(id, reason, userId);
        return order != null ? R.ok("售后申请已提交") : R.error("售后申请失败，订单状态不正确");
    }

    @PostMapping("/batch/pay")
    @Operation(summary = "批量支付", description = "批量支付订单")
    @RequirePermission("order:write")
    public R batchPayOrders(
            @Parameter(description = "订单ID列表") @RequestParam @NotNull List<Long> orderIds) {
        Long userId = UserContext.get().getId();
        Map<Long, Order> result = orderService.batchPayOrders(orderIds, userId);
        return R.ok("批量支付完成", result);
    }

    @PostMapping("/batch/ship")
    @Operation(summary = "批量发货", description = "商家批量发货")
    @RequirePermission("order:ship")
    public R batchShipOrders(
            @Parameter(description = "订单ID列表") @RequestParam @NotNull List<Long> orderIds) {
        Long merchantId = UserContext.get().getId();
        Map<Long, Order> result = orderService.batchShipOrders(orderIds, merchantId);
        return R.ok("批量发货完成", result);
    }

    @PostMapping("/batch/complete")
    @Operation(summary = "批量确认收货", description = "批量确认收货")
    @RequirePermission("order:write")
    public R batchCompleteOrders(
            @Parameter(description = "订单ID列表") @RequestParam @NotNull List<Long> orderIds) {
        Long userId = UserContext.get().getId();
        Map<Long, Order> result = orderService.batchCompleteOrders(orderIds, userId);
        return R.ok("批量确认收货完成", result);
    }

    @PostMapping("/batch/cancel")
    @Operation(summary = "批量取消订单", description = "批量取消订单")
    @RequirePermission("order:cancel")
    public R batchCancelOrders(
            @Parameter(description = "订单ID列表") @RequestParam @NotNull List<Long> orderIds) {
        Long userId = UserContext.get().getId();
        Map<Long, Order> result = orderService.batchCancelOrders(orderIds, userId);
        return R.ok("批量取消订单完成", result);
    }
}
