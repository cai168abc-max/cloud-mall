package com.atguigu.product.service.impl;

import com.atguigu.common.mq.ProductAuditMessage;
import com.atguigu.product.bean.Product;
import com.atguigu.product.bean.ProductAuditLog;
import com.atguigu.product.mapper.ProductAuditLogMapper;
import com.atguigu.product.mapper.ProductMapper;
import com.atguigu.product.service.ProductAuditService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 商品审核服务实现类
 * 实现功能：
 * 1. 分布式锁防重复审核
 * 2. 商品状态校验
 * 3. 更新审核状态
 * 4. 记录审核日志
 * 5. MQ通知
 * 6. 异常处理和兜底方案
 */
@Service
public class ProductAuditServiceImpl implements ProductAuditService {

    private static final Logger log = LoggerFactory.getLogger(ProductAuditServiceImpl.class);

    private static final String AUDIT_LOCK_PREFIX = "product:audit:";
    private static final String AUDIT_TOPIC = "product-audit-topic";
    private static final int LOCK_WAIT_TIME = 3;
    private static final int LOCK_LEASE_TIME = 10;
    private static final int BATCH_MAX_SIZE = 100;

    private final ProductMapper productMapper;
    private final ProductAuditLogMapper productAuditLogMapper;
    private final RedissonClient redissonClient;
    private final RocketMQTemplate rocketMQTemplate;
    private final TransactionTemplate transactionTemplate;

    public ProductAuditServiceImpl(ProductMapper productMapper,
                                   ProductAuditLogMapper productAuditLogMapper,
                                   RedissonClient redissonClient,
                                   RocketMQTemplate rocketMQTemplate,
                                   PlatformTransactionManager transactionManager) {
        this.productMapper = productMapper;
        this.productAuditLogMapper = productAuditLogMapper;
        this.redissonClient = redissonClient;
        this.rocketMQTemplate = rocketMQTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public AuditResult auditProduct(Long productId, Long auditorId, Boolean approved, String reason) {
        if (productId == null || auditorId == null || approved == null) {
            return new AuditResult(false, "参数不完整", productId);
        }

        RLock lock = null;
        boolean lockAcquired = false;

        try {
            lock = redissonClient.getLock(AUDIT_LOCK_PREFIX + productId);
            lockAcquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!lockAcquired) {
                log.warn("获取审核锁失败，可能正在处理中，productId={}", productId);
                return executeAuditWithDbLock(productId, auditorId, approved, reason);
            }

            log.info("获取审核锁成功，productId={}, auditorId={}", productId, auditorId);

            return executeAuditInTransaction(productId, auditorId, approved, reason);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取审核锁被中断，productId={}", productId, e);
            return executeAuditWithDbLock(productId, auditorId, approved, reason);
        } catch (Exception e) {
            log.error("审核过程发生异常，productId={}", productId, e);
            return new AuditResult(false, "审核失败，请重试", productId);
        } finally {
            if (lockAcquired && lock != null && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                    log.info("释放审核锁成功，productId={}", productId);
                } catch (Exception e) {
                    log.error("释放审核锁异常，productId={}", productId, e);
                }
            }
        }
    }

    private AuditResult executeAuditWithDbLock(Long productId, Long auditorId, Boolean approved, String reason) {
        log.info("使用数据库行锁进行审核，productId={}", productId);
        final Product[] productHolder = new Product[1];
        AuditResult result = transactionTemplate.execute(status -> {
            try {
                Product product = productMapper.selectByIdForUpdate(productId);

                if (product == null) {
                    return new AuditResult(false, "商品不存在", productId);
                }

                if (product.getVerified() != null && product.getVerified()) {
                    return new AuditResult(false, "商品已审核，请勿重复操作", productId);
                }

                productHolder[0] = product;
                return doAuditInTransaction(product, auditorId, approved, reason);

            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("数据库行锁审核失败，productId={}", productId, e);
                return new AuditResult(false, "审核失败，请重试", productId);
            }
        });

        if (result != null && result.isSuccess() && productHolder[0] != null) {
            sendAuditMessage(productHolder[0], auditorId, approved, reason, "SINGLE");
        }

        return result;
    }

    private AuditResult executeAuditInTransaction(Long productId, Long auditorId, Boolean approved, String reason) {
        final Product[] productHolder = new Product[1];
        AuditResult result = transactionTemplate.execute(status -> {
            try {
                Product product = productMapper.selectById(productId);

                if (product == null) {
                    return new AuditResult(false, "商品不存在", productId);
                }

                if (product.getVerified() != null && product.getVerified()) {
                    return new AuditResult(false, "商品已审核，请勿重复操作", productId);
                }

                productHolder[0] = product;
                return doAuditInTransaction(product, auditorId, approved, reason);

            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("审核事务失败，productId={}", productId, e);
                return new AuditResult(false, "审核失败，请重试", productId);
            }
        });

        if (result != null && result.isSuccess() && productHolder[0] != null) {
            sendAuditMessage(productHolder[0], auditorId, approved, reason, "SINGLE");
        }

        return result;
    }

    /**
     * 在事务中执行审核操作
     */
    private AuditResult doAuditInTransaction(Product product, Long auditorId, Boolean approved, String reason) {
        Long productId = product.getId();
        Integer beforeStatus = (product.getVerified() != null && product.getVerified()) ? 1 : 0;
        Integer afterStatus = approved ? 1 : 0;

        int updated = productMapper.updateVerified(productId, approved);
        if (updated <= 0) {
            log.error("更新商品审核状态失败，productId={}", productId);
            throw new RuntimeException("更新商品审核状态失败");
        }

        ProductAuditLog auditLog = new ProductAuditLog();
        auditLog.setProductId(productId);
        auditLog.setMerchantId(product.getMerchantId() != null ? product.getMerchantId() : 0L);
        auditLog.setAuditorId(auditorId);
        auditLog.setBeforeStatus(beforeStatus);
        auditLog.setAfterStatus(afterStatus);
        auditLog.setReason(reason);
        auditLog.setCreateTime(java.time.LocalDateTime.now());

        int logInserted = productAuditLogMapper.insert(auditLog);
        if (logInserted <= 0) {
            log.error("记录审核日志失败，productId={}", productId);
            throw new RuntimeException("记录审核日志失败");
        }

        log.info("审核完成，productId={}, approved={}, auditorId={}", productId, approved, auditorId);

        return new AuditResult(true, "审核成功", productId);
    }

    @Override
    public BatchAuditResult batchAuditProducts(List<Long> productIds, Long auditorId, Boolean approved, String reason) {
        // 参数校验
        if (productIds == null || productIds.isEmpty()) {
            return new BatchAuditResult(0, 0, 0, Collections.emptyMap());
        }

        if (productIds.size() > BATCH_MAX_SIZE) {
            Map<Long, String> errorMap = new HashMap<>();
            errorMap.put(0L, "最多支持" + BATCH_MAX_SIZE + "个商品同时审核");
            return new BatchAuditResult(productIds.size(), 0, productIds.size(), errorMap);
        }

        if (auditorId == null || approved == null) {
            Map<Long, String> errorMap = new HashMap<>();
            errorMap.put(0L, "参数不完整");
            return new BatchAuditResult(productIds.size(), 0, productIds.size(), errorMap);
        }

        int successCount = 0;
        int failCount = 0;
        Map<Long, String> failedProducts = new HashMap<>();

        // 批量处理
        for (Long productId : productIds) {
            AuditResult result = auditProduct(productId, auditorId, approved, reason);
            if (result.isSuccess()) {
                successCount++;
            } else {
                failCount++;
                failedProducts.put(productId, result.getMessage());
            }
        }

        // 发送批量审核消息
        if (successCount > 0) {
            final int finalSuccessCount = successCount;
            try {
                ProductAuditMessage message = ProductAuditMessage.builder()
                        .auditorId(auditorId)
                        .approved(approved)
                        .reason(reason)
                        .type("BATCH")
                        .timestamp(System.currentTimeMillis())
                        .build();
                rocketMQTemplate.asyncSend(AUDIT_TOPIC, message, new org.apache.rocketmq.client.producer.SendCallback() {
                    @Override
                    public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                        log.info("批量审核消息发送成功，successCount={}", finalSuccessCount);
                    }

                    @Override
                    public void onException(Throwable e) {
                        log.error("批量审核消息发送失败，successCount={}", finalSuccessCount, e);
                    }
                });
            } catch (Exception e) {
                log.error("发送批量审核消息异常", e);
            }
        }

        log.info("批量审核完成，total={}, success={}, fail={}", productIds.size(), successCount, failCount);

        return new BatchAuditResult(productIds.size(), successCount, failCount, failedProducts);
    }

    /**
     * 发送审核结果消息到MQ
     */
    private void sendAuditMessage(Product product, Long auditorId, Boolean approved, String reason, String type) {
        if (product == null) {
            return;
        }
        
        try {
            ProductAuditMessage message = ProductAuditMessage.builder()
                    .productId(product.getId())
                    .merchantId(product.getMerchantId() != null ? product.getMerchantId() : 0L)
                    .auditorId(auditorId)
                    .approved(approved)
                    .reason(reason)
                    .type(type)
                    .timestamp(System.currentTimeMillis())
                    .build();

            rocketMQTemplate.asyncSend(AUDIT_TOPIC, message, new org.apache.rocketmq.client.producer.SendCallback() {
                @Override
                public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                    log.info("审核消息发送成功，productId={}", product.getId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("审核消息发送失败，productId={}", product.getId(), e);
                    // MQ不可用时的兜底：记录日志，后续可通过定时任务补偿
                }
            });
        } catch (Exception e) {
            log.error("发送审核消息异常，productId={}", product.getId(), e);
        }
    }

    @Override
    public List<ProductAuditLog> getAuditHistory(Long productId) {
        if (productId == null) {
            return Collections.emptyList();
        }
        return productAuditLogMapper.selectByProductId(productId);
    }

    @Override
    public List<ProductAuditLog> getAuditHistoryByMerchant(Long merchantId) {
        if (merchantId == null) {
            return Collections.emptyList();
        }
        return productAuditLogMapper.selectByMerchantId(merchantId);
    }
}
