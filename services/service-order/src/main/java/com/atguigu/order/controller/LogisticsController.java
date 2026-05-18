package com.atguigu.order.controller;

import com.atguigu.common.context.UserContext;
import com.atguigu.common.enums.UserRole;
import com.atguigu.common.result.R;
import com.atguigu.order.bean.LogisticsInfo;
import com.atguigu.order.service.LogisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物流控制器
 * 提供物流跟踪相关接口：
 * 1. 发货（商家）
 * 2. 查询物流信息（用户/商家）
 * 3. 确认签收（用户）
 * 4. 添加物流轨迹（物流公司回调）
 */
@RestController
@RequestMapping("/api/logistics")
@Validated
@Tag(name = "物流管理", description = "物流跟踪相关接口")
@RequiredArgsConstructor
public class LogisticsController {

    private final LogisticsService logisticsService;

    /**
     * 发货（商家操作）
     * 包含：订单状态校验、分布式锁、创建物流信息、更新订单状态、MQ通知
     */
    @PostMapping("/ship")
    @Operation(summary = "发货", description = "商家发货，创建物流信息")
    public R shipOrder(
            @Parameter(description = "订单ID") @RequestParam @NotNull Long orderId,
            @Parameter(description = "物流单号") @RequestParam @NotBlank String trackingNo,
            @Parameter(description = "物流公司") @RequestParam @NotBlank String carrier,
            @Parameter(description = "发件人姓名") @RequestParam(required = false) String senderName,
            @Parameter(description = "发件人电话") @RequestParam(required = false) String senderPhone,
            @Parameter(description = "发件人地址") @RequestParam(required = false) String senderAddress) {
        
        // 权限校验
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }
        
        R roleCheck = requireRole(UserRole.MERCHANT, UserRole.ADMIN);
        if (roleCheck != null) {
            return roleCheck;
        }
        
        Long merchantId = UserContext.get().getId();
        
        try {
            LogisticsInfo logisticsInfo = logisticsService.shipOrder(
                    orderId, merchantId, trackingNo, carrier, 
                    senderName, senderPhone, senderAddress);
            return R.ok("发货成功", logisticsInfo);
        } catch (Exception e) {
            return R.error(400, e.getMessage());
        }
    }

    /**
     * 根据订单ID查询物流信息
     * 使用Redis缓存，过期时间30分钟
     */
    @GetMapping("/order/{orderId}")
    @Operation(summary = "根据订单ID查询物流", description = "查询指定订单的物流信息（含轨迹）")
    public R getLogisticsByOrderId(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long orderId) {
        
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }
        
        LogisticsInfo logisticsInfo = logisticsService.getLogisticsByOrderId(orderId);
        if (logisticsInfo == null) {
            return R.notFound("物流信息不存在");
        }
        
        // 权限校验：只有订单所有者或商家可以查看
        Long currentUserId = UserContext.get().getId();
        UserRole currentRole = UserContext.get().getRole();
        if (!logisticsInfo.getUserId().equals(currentUserId) 
                && !logisticsInfo.getMerchantId().equals(currentUserId)
                && currentRole != UserRole.ADMIN) {
            return R.forbidden("无权查看此物流信息");
        }
        
        return R.ok("查询成功", logisticsInfo);
    }

    /**
     * 根据物流单号查询物流信息
     */
    @GetMapping("/tracking/{trackingNo}")
    @Operation(summary = "根据物流单号查询", description = "根据物流单号查询物流信息")
    public R getLogisticsByTrackingNo(
            @Parameter(description = "物流单号") @PathVariable @NotBlank String trackingNo) {

        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        LogisticsInfo logisticsInfo = logisticsService.getLogisticsByTrackingNo(trackingNo);
        if (logisticsInfo == null) {
            return R.notFound("物流信息不存在");
        }

        Long currentUserId = UserContext.get().getId();
        UserRole currentRole = UserContext.get().getRole();
        if (!logisticsInfo.getUserId().equals(currentUserId)
                && !logisticsInfo.getMerchantId().equals(currentUserId)
                && currentRole != UserRole.ADMIN) {
            return R.forbidden("无权查看此物流信息");
        }

        return R.ok("查询成功", logisticsInfo);
    }

    /**
     * 查询当前用户的物流列表
     */
    @GetMapping("/my")
    @Operation(summary = "查询我的物流", description = "查询当前用户的所有物流信息")
    public R listMyLogistics() {
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }
        
        Long userId = UserContext.get().getId();
        List<LogisticsInfo> logisticsList = logisticsService.listLogisticsByUserId(userId);
        return R.ok("查询成功", logisticsList);
    }

    /**
     * 查询商家的物流列表
     */
    @GetMapping("/merchant")
    @Operation(summary = "查询商家物流", description = "查询当前商家的所有物流信息")
    public R listMerchantLogistics() {
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }
        
        R roleCheck = requireRole(UserRole.MERCHANT, UserRole.ADMIN);
        if (roleCheck != null) {
            return roleCheck;
        }
        
        Long merchantId = UserContext.get().getId();
        List<LogisticsInfo> logisticsList = logisticsService.listLogisticsByMerchantId(merchantId);
        return R.ok("查询成功", logisticsList);
    }

    /**
     * 确认签收（用户操作）
     */
    @PutMapping("/confirm/{orderId}")
    @Operation(summary = "确认签收", description = "用户确认收货")
    public R confirmDelivery(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long orderId) {
        
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }
        
        Long userId = UserContext.get().getId();
        
        try {
            boolean success = logisticsService.confirmDelivery(orderId, userId);
            return success ? R.ok("确认签收成功") : R.error("确认签收失败");
        } catch (Exception e) {
            return R.error(400, e.getMessage());
        }
    }

    /**
     * 添加物流轨迹（物流公司回调接口）
     */
    @PostMapping("/trace")
    @Operation(summary = "添加物流轨迹", description = "物流公司回调接口，添加物流轨迹")
    public R addLogisticsTrace(
            @Parameter(description = "物流ID") @RequestParam @NotNull Long logisticsId,
            @Parameter(description = "轨迹时间") @RequestParam @NotBlank String traceTime,
            @Parameter(description = "轨迹状态") @RequestParam @NotBlank String status,
            @Parameter(description = "所在地点") @RequestParam(required = false) String location,
            @Parameter(description = "轨迹描述") @RequestParam @NotBlank String description,
            @Parameter(description = "操作人/网点") @RequestParam(required = false) String operator) {
        
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }

        R roleCheck = requireRole(UserRole.MERCHANT, UserRole.ADMIN);
        if (roleCheck != null) {
            return roleCheck;
        }

        try {
            boolean success = logisticsService.addLogisticsTrace(
                    logisticsId, traceTime, status, location, description, operator);
            return success ? R.ok("添加轨迹成功") : R.error("添加轨迹失败");
        } catch (Exception e) {
            return R.error(400, e.getMessage());
        }
    }

    /**
     * 刷新物流缓存
     */
    @PostMapping("/refresh/{orderId}")
    @Operation(summary = "刷新物流缓存", description = "手动刷新物流缓存")
    public R refreshCache(
            @Parameter(description = "订单ID") @PathVariable @NotNull Long orderId) {
        
        R authCheck = requireLogin();
        if (authCheck != null) {
            return authCheck;
        }
        
        R roleCheck = requireRole(UserRole.ADMIN);
        if (roleCheck != null) {
            return roleCheck;
        }
        
        logisticsService.refreshLogisticsCache(orderId);
        return R.ok("刷新缓存成功");
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
