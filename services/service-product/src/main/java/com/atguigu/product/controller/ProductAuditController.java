package com.atguigu.product.controller;

import com.atguigu.common.context.UserContext;
import com.atguigu.common.enums.UserRole;
import com.atguigu.common.result.R;
import com.atguigu.product.bean.ProductAuditLog;
import com.atguigu.product.service.ProductAuditService;
import com.atguigu.product.service.ProductAuditService.AuditResult;
import com.atguigu.product.service.ProductAuditService.BatchAuditResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品审核Controller
 * 
 * 提供商品审核相关接口：
 * - 单个商品审核
 * - 批量商品审核
 * - 查询审核历史
 */
@Tag(name = "商品审核管理", description = "商品审核相关接口")
@RestController
@RequestMapping("/api/product/audit")
@RequiredArgsConstructor
@Validated
public class ProductAuditController {

    private final ProductAuditService productAuditService;

    /**
     * 审核请求DTO
     */
    public static class AuditRequest {
        @NotNull(message = "商品ID不能为空")
        private Long productId;

        @NotNull(message = "审核结果不能为空")
        private Boolean approved;

        private String reason;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Boolean getApproved() {
            return approved;
        }

        public void setApproved(Boolean approved) {
            this.approved = approved;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /**
     * 批量审核请求DTO
     */
    public static class BatchAuditRequest {
        @NotEmpty(message = "商品ID列表不能为空")
        private List<Long> productIds;

        @NotNull(message = "审核结果不能为空")
        private Boolean approved;

        private String reason;

        public List<Long> getProductIds() {
            return productIds;
        }

        public void setProductIds(List<Long> productIds) {
            this.productIds = productIds;
        }

        public Boolean getApproved() {
            return approved;
        }

        public void setApproved(Boolean approved) {
            this.approved = approved;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /**
     * 单个商品审核
     *
     * @param request 审核请求
     * @return 审核结果
     */
    @Operation(summary = "单个商品审核", description = "管理员审核单个商品的上架申请")
    @PostMapping("/single")
    public R auditProduct(@RequestBody @Validated AuditRequest request) {
        com.atguigu.common.bean.UserInfo userInfo = UserContext.get();
        Long auditorId = userInfo != null ? userInfo.getId() : null;
        if (auditorId == null) {
            return R.unauthorized("请先登录");
        }
        if (userInfo.getRole() != UserRole.ADMIN) {
            return R.error(403, "仅管理员可以审核商品");
        }

        AuditResult result = productAuditService.auditProduct(
                request.getProductId(),
                auditorId,
                request.getApproved(),
                request.getReason()
        );

        if (result.isSuccess()) {
            return R.ok("审核成功", result);
        } else {
            return R.error(400, result.getMessage());
        }
    }

    /**
     * 批量商品审核
     *
     * @param request 批量审核请求
     * @return 批量审核结果
     */
    @Operation(summary = "批量商品审核", description = "管理员批量审核商品的上架申请，最多支持100个商品同时审核")
    @PostMapping("/batch")
    public R batchAuditProducts(@RequestBody @Validated BatchAuditRequest request) {
        com.atguigu.common.bean.UserInfo userInfo = UserContext.get();
        Long auditorId = userInfo != null ? userInfo.getId() : null;
        if (auditorId == null) {
            return R.unauthorized("请先登录");
        }
        if (userInfo.getRole() != UserRole.ADMIN) {
            return R.error(403, "仅管理员可以审核商品");
        }

        BatchAuditResult result = productAuditService.batchAuditProducts(
                request.getProductIds(),
                auditorId,
                request.getApproved(),
                request.getReason()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("totalCount", result.getTotalCount());
        data.put("successCount", result.getSuccessCount());
        data.put("failCount", result.getFailCount());
        data.put("failedProducts", result.getFailedProducts());

        if (result.getFailCount() == 0) {
            return R.ok("批量审核成功", data);
        } else if (result.getSuccessCount() > 0) {
            return R.ok("部分审核成功", data);
        } else {
            R response = R.error(400, "批量审核失败");
            response.setData(data);
            return response;
        }
    }

    /**
     * 查询商品审核历史
     *
     * @param productId 商品ID
     * @return 审核历史记录
     */
    @Operation(summary = "查询商品审核历史", description = "查询指定商品的审核历史记录")
    @GetMapping("/history/{productId}")
    public R getAuditHistory(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        List<ProductAuditLog> history = productAuditService.getAuditHistory(productId);
        return R.ok(history);
    }

    /**
     * 查询商家商品的审核记录
     *
     * @param merchantId 商家ID
     * @return 审核记录列表
     */
    @Operation(summary = "查询商家审核记录", description = "查询指定商家所有商品的审核记录")
    @GetMapping("/merchant/{merchantId}")
    public R getAuditHistoryByMerchant(
            @Parameter(description = "商家ID") @PathVariable Long merchantId) {
        List<ProductAuditLog> history = productAuditService.getAuditHistoryByMerchant(merchantId);
        return R.ok(history);
    }
}
