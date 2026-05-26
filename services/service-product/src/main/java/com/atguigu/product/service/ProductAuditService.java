package com.atguigu.product.service;

import com.atguigu.product.bean.ProductAuditLog;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品审核服务接口
 */
public interface ProductAuditService {

    /**
     * 审核结果DTO
     */
    @Setter
    @Getter
    class AuditResult {
        private boolean success;
        private String message;
        private Long productId;

        public AuditResult(final boolean success, final String message, final Long productId) {
            this.success = success;
            this.message = message;
            this.productId = productId;
        }

    }

    /**
     * 批量审核结果DTO
     */
    @Setter
    @Getter
    class BatchAuditResult {
        private int totalCount;
        private int successCount;
        private int failCount;
        private Map<Long, String> failedProducts;

        public BatchAuditResult(final int totalCount, final int successCount, final int failCount, final Map<Long, String> failedProducts) {
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.failCount = failCount;
            this.failedProducts = new HashMap<>(failedProducts);
        }

        public void setFailedProducts(final Map<Long, String> failedProducts) {
            this.failedProducts = new HashMap<>(failedProducts);
        }

        public Map<Long, String> getFailedProducts() {
            return new HashMap<>(failedProducts);
        }

    }

    /**
     * 单个商品审核
     *
     * @param productId  商品ID
     * @param auditorId  审核人ID
     * @param approved   是否通过
     * @param reason     审核原因/拒绝理由
     * @return 审核结果
     */
    AuditResult auditProduct(Long productId, Long auditorId, Boolean approved, String reason);

    /**
     * 批量商品审核
     *
     * @param productIds 商品ID列表
     * @param auditorId  审核人ID
     * @param approved   是否通过
     * @param reason     审核原因/拒绝理由
     * @return 批量审核结果
     */
    BatchAuditResult batchAuditProducts(List<Long> productIds, Long auditorId, Boolean approved, String reason);

    /**
     * 查询商品审核历史
     *
     * @param productId 商品ID
     * @return 审核日志列表
     */
    List<ProductAuditLog> getAuditHistory(Long productId);

    /**
     * 查询商家商品的审核记录
     *
     * @param merchantId 商家ID
     * @return 审核日志列表
     */
    List<ProductAuditLog> getAuditHistoryByMerchant(Long merchantId);
}
