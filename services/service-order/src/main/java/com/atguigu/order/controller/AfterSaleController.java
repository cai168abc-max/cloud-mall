package com.atguigu.order.controller;

import com.atguigu.common.context.UserContext;
import com.atguigu.common.enums.UserRole;
import com.atguigu.common.result.R;
import com.atguigu.order.bean.AfterSaleTicket;
import com.atguigu.order.service.AfterSaleService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 售后控制器
 */
@RestController
@RequestMapping("/api/aftersale")
@Validated
@Tag(name = "售后管理", description = "售后相关接口")
@RequiredArgsConstructor
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    @PostMapping("/apply")
    @Operation(summary = "申请售后", description = "用户申请售后")
    public R applyAfterSale(
            @Parameter(description = "订单ID") @RequestParam @NotNull Long orderId,
            @Parameter(description = "售后类型：REFUND-仅退款/RETURN-退货退款/EXCHANGE-换货") @RequestParam @NotNull String type,
            @Parameter(description = "申请原因") @RequestParam @NotNull String reason,
            @Parameter(description = "详细描述") @RequestParam(required = false) String description,
            @Parameter(description = "凭证图片（逗号分隔）") @RequestParam(required = false) String images) {
        
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        Long userId = UserContext.get().getId();
        AfterSaleTicket ticket = afterSaleService.applyAfterSale(orderId, type, reason, description, images, userId);
        return R.ok("售后申请已提交", ticket);
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "同意售后", description = "商家同意售后申请")
    public R approveAfterSale(
            @Parameter(description = "工单ID") @PathVariable @NotNull Long id,
            @Parameter(description = "退款金额（可选，为空则使用订单支付金额）") @RequestParam(required = false) BigDecimal refundAmount) {
        
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        R roleCheck = requireRole(UserRole.MERCHANT, UserRole.ADMIN);
        if (roleCheck != null) {
            return roleCheck;
        }

        Long merchantId = UserContext.get().getId();
        boolean success = afterSaleService.approveAfterSale(id, merchantId, refundAmount);
        return success ? R.ok("售后已同意，退款成功") : R.error("操作失败");
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "拒绝售后", description = "商家拒绝售后申请")
    public R rejectAfterSale(
            @Parameter(description = "工单ID") @PathVariable @NotNull Long id,
            @Parameter(description = "拒绝原因") @RequestParam @NotNull String rejectReason) {
        
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        R roleCheck = requireRole(UserRole.MERCHANT, UserRole.ADMIN);
        if (roleCheck != null) {
            return roleCheck;
        }

        Long merchantId = UserContext.get().getId();
        boolean success = afterSaleService.rejectAfterSale(id, merchantId, rejectReason);
        return success ? R.ok("售后已拒绝") : R.error("操作失败");
    }

    @PutMapping("/{id}/manual-refund")
    @Operation(summary = "手动退款", description = "手动退款（兜底方案，用于自动退款失败的情况）")
    public R manualRefund(
            @Parameter(description = "工单ID") @PathVariable @NotNull Long id) {
        
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        R roleCheck = requireRole(UserRole.MERCHANT, UserRole.ADMIN);
        if (roleCheck != null) {
            return roleCheck;
        }

        Long merchantId = UserContext.get().getId();
        boolean success = afterSaleService.manualRefund(id, merchantId);
        return success ? R.ok("手动退款成功") : R.error("退款失败");
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "取消售后", description = "用户取消售后申请")
    public R cancelAfterSale(
            @Parameter(description = "工单ID") @PathVariable @NotNull Long id) {
        
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        Long userId = UserContext.get().getId();
        boolean success = afterSaleService.cancelAfterSale(id, userId);
        return success ? R.ok("售后已取消") : R.error("操作失败");
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询售后工单详情", description = "根据工单ID查询售后工单详情")
    public R getTicket(
            @Parameter(description = "工单ID") @PathVariable @NotNull Long id) {
        
        AfterSaleTicket ticket = afterSaleService.getTicketById(id);
        if (ticket == null) {
            return R.error(404, "工单不存在");
        }
        return R.ok("查询成功", ticket);
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "根据订单ID查询售后工单", description = "根据订单ID查询最新的售后工单")
    public R getTicketByOrderId(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long orderId) {
        
        AfterSaleTicket ticket = afterSaleService.getTicketByOrderId(orderId);
        if (ticket == null) {
            return R.error(404, "该订单暂无售后工单");
        }
        return R.ok("查询成功", ticket);
    }

    @GetMapping("/my")
    @Operation(summary = "查询我的售后工单列表", description = "查询当前用户的售后工单列表")
    public R listMyTickets() {
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        Long userId = UserContext.get().getId();
        List<AfterSaleTicket> tickets = afterSaleService.listTicketsByUserId(userId);
        return R.ok("查询成功", tickets);
    }

    @GetMapping("/my/page")
    @Operation(summary = "分页查询我的售后工单列表", description = "分页查询当前用户的售后工单列表")
    public R listMyTicketsPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize) {
        
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        Long userId = UserContext.get().getId();
        IPage<AfterSaleTicket> page = afterSaleService.listTicketsByUserId(userId, pageNum, pageSize);
        return R.ok("查询成功", page);
    }

    @GetMapping("/merchant")
    @Operation(summary = "查询商家售后工单列表", description = "查询当前商家的售后工单列表")
    public R listMerchantTickets() {
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        R roleCheck = requireRole(UserRole.MERCHANT, UserRole.ADMIN);
        if (roleCheck != null) {
            return roleCheck;
        }

        Long merchantId = UserContext.get().getId();
        List<AfterSaleTicket> tickets = afterSaleService.listTicketsByMerchantId(merchantId);
        return R.ok("查询成功", tickets);
    }

    @GetMapping("/merchant/page")
    @Operation(summary = "分页查询商家售后工单列表", description = "分页查询当前商家的售后工单列表")
    public R listMerchantTicketsPage(
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
        IPage<AfterSaleTicket> page = afterSaleService.listTicketsByMerchantId(merchantId, pageNum, pageSize);
        return R.ok("查询成功", page);
    }

    @GetMapping("/merchant/status/{status}")
    @Operation(summary = "根据状态查询商家售后工单列表", description = "根据状态查询当前商家的售后工单列表")
    public R listMerchantTicketsByStatus(
            @Parameter(description = "状态：PENDING-待处理/APPROVED-已同意/REJECTED-已拒绝/PROCESSING-处理中/COMPLETED-已完成/CANCELLED-已取消") 
            @PathVariable @NotNull String status) {
        
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        R roleCheck = requireRole(UserRole.MERCHANT, UserRole.ADMIN);
        if (roleCheck != null) {
            return roleCheck;
        }

        Long merchantId = UserContext.get().getId();
        List<AfterSaleTicket> tickets = afterSaleService.listTicketsByMerchantIdAndStatus(merchantId, status);
        return R.ok("查询成功", tickets);
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
