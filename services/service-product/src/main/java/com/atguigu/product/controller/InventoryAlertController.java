package com.atguigu.product.controller;

import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.context.UserContext;
import com.atguigu.common.enums.UserRole;
import com.atguigu.common.result.R;
import com.atguigu.product.bean.InventoryAlertConfig;
import com.atguigu.product.bean.InventoryAlertLog;
import com.atguigu.product.service.InventoryAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "库存预警管理", description = "库存预警配置和记录管理接口")
@RestController
@RequestMapping("/api/product/alert")
@RequiredArgsConstructor
public class InventoryAlertController {

    private final InventoryAlertService inventoryAlertService;

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    @Operation(summary = "创建或更新预警配置", description = "为商品创建或更新库存预警配置")
    @PostMapping("/config")
    public R saveOrUpdateConfig(@RequestBody InventoryAlertConfig config) {
        UserInfo userInfo = UserContext.get();
        if (userInfo == null) {
            return R.error(401, "请先登录");
        }
        if (userInfo.getRole() != UserRole.MERCHANT && userInfo.getRole() != UserRole.ADMIN) {
            return R.error(403, "仅商家或管理员可以操作");
        }
        if (config == null) {
            return R.badRequest("预警配置不能为空");
        }
        if (config.getProductId() == null) {
            return R.badRequest("商品ID不能为空");
        }
        if (config.getThreshold() == null || config.getThreshold() <= 0) {
            return R.badRequest("预警阈值必须大于0");
        }

        try {
            boolean success = inventoryAlertService.saveOrUpdateConfig(config);
            if (success) {
                log.info("预警配置保存成功，productId={}", config.getProductId());
                return R.ok("预警配置保存成功");
            } else {
                log.warn("预警配置保存失败，productId={}", config.getProductId());
                return R.error(500, "预警配置保存失败");
            }
        } catch (Exception e) {
            log.error("保存预警配置异常，productId={}", config.getProductId(), e);
            return R.error(500, "系统异常，请稍后重试");
        }
    }

    @Operation(summary = "查询商品预警配置", description = "根据商品ID查询预警配置")
    @GetMapping("/config/{productId}")
    public R getConfig(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        if (productId == null || productId <= 0) {
            return R.badRequest("商品ID无效");
        }

        try {
            InventoryAlertConfig config = inventoryAlertService.getConfigByProductId(productId);
            if (config == null) {
                return R.notFound("预警配置不存在");
            }
            return R.ok(config);
        } catch (Exception e) {
            log.error("查询预警配置异常，productId={}", productId, e);
            return R.error(500, "系统异常，请稍后重试");
        }
    }

    @Operation(summary = "查询所有启用的预警配置", description = "查询所有状态为启用的预警配置")
    @GetMapping("/config/enabled")
    public R listEnabledConfigs() {
        try {
            List<InventoryAlertConfig> configs = inventoryAlertService.listAllEnabledConfigs();
            return R.ok(configs);
        } catch (Exception e) {
            log.error("查询启用的预警配置异常", e);
            return R.error(500, "系统异常，请稍后重试");
        }
    }

    @Operation(summary = "更新预警配置状态", description = "启用或禁用预警配置")
    @PutMapping("/config/{id}/status")
    public R updateConfigStatus(
            @Parameter(description = "配置ID") @PathVariable Long id,
            @Parameter(description = "状态：1启用 0禁用") @RequestParam Integer status) {
        UserInfo userInfo = UserContext.get();
        if (userInfo == null) {
            return R.error(401, "请先登录");
        }
        if (userInfo.getRole() != UserRole.MERCHANT && userInfo.getRole() != UserRole.ADMIN) {
            return R.error(403, "仅商家或管理员可以操作");
        }
        if (id == null || id <= 0) {
            return R.badRequest("配置ID无效");
        }
        if (status == null || (status != 0 && status != 1)) {
            return R.badRequest("状态值必须为0或1");
        }

        try {
            boolean success = inventoryAlertService.updateConfigStatus(id, status);
            if (success) {
                log.info("预警配置状态更新成功，id={}, status={}", id, status);
                return R.ok("状态更新成功");
            } else {
                log.warn("预警配置状态更新失败，id={}, status={}", id, status);
                return R.error(500, "状态更新失败");
            }
        } catch (Exception e) {
            log.error("更新预警配置状态异常，id={}, status={}", id, status, e);
            return R.error(500, "系统异常，请稍后重试");
        }
    }

    @Operation(summary = "查询商品预警记录", description = "查询商品的库存预警历史记录")
    @GetMapping("/log/{productId}")
    public R listAlertLogs(
            @Parameter(description = "商品ID") @PathVariable Long productId,
            @Parameter(description = "限制数量") @RequestParam(defaultValue = "20") int limit) {
        if (productId == null || productId <= 0) {
            return R.badRequest("商品ID无效");
        }
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            return R.badRequest("限制数量必须在" + MIN_LIMIT + "到" + MAX_LIMIT + "之间");
        }

        try {
            List<InventoryAlertLog> logs = inventoryAlertService.listAlertLogs(productId, limit);
            return R.ok(logs);
        } catch (Exception e) {
            log.error("查询预警记录异常，productId={}", productId, e);
            return R.error(500, "系统异常，请稍后重试");
        }
    }

    @Operation(summary = "手动触发预警", description = "手动触发商品库存预警（兜底方案）")
    @PostMapping("/trigger/{productId}")
    public R triggerAlert(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        UserInfo userInfo = UserContext.get();
        if (userInfo == null) {
            return R.error(401, "请先登录");
        }
        if (userInfo.getRole() != UserRole.ADMIN) {
            return R.error(403, "仅管理员可以触发预警");
        }
        if (productId == null || productId <= 0) {
            return R.badRequest("商品ID无效");
        }

        try {
            log.info("手动触发预警，productId={}", productId);
            boolean success = inventoryAlertService.triggerAlertManually(productId);
            if (success) {
                log.info("预警触发成功，productId={}", productId);
                return R.ok("预警触发成功");
            } else {
                log.warn("预警触发失败，productId={}", productId);
                return R.error(500, "预警触发失败");
            }
        } catch (Exception e) {
            log.error("手动触发预警异常，productId={}", productId, e);
            return R.error(500, "系统异常，请稍后重试");
        }
    }

    @Operation(summary = "检查是否可以发送预警", description = "检查商品今日是否还可以发送预警")
    @GetMapping("/can-send/{productId}")
    public R canSendAlert(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        if (productId == null || productId <= 0) {
            return R.badRequest("商品ID无效");
        }

        try {
            boolean canSend = inventoryAlertService.canSendAlert(productId);
            return R.ok(canSend);
        } catch (Exception e) {
            log.error("检查预警状态异常，productId={}", productId, e);
            return R.error(500, "系统异常，请稍后重试");
        }
    }
}
