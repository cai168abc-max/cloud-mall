package com.atguigu.product.service.impl;

import com.atguigu.common.cache.CacheKeyConstants;
import com.atguigu.common.mq.InventoryAlertMessage;
import com.atguigu.product.bean.InventoryAlertConfig;
import com.atguigu.product.bean.InventoryAlertLog;
import com.atguigu.product.bean.Product;
import com.atguigu.product.mapper.InventoryAlertConfigMapper;
import com.atguigu.product.mapper.InventoryAlertLogMapper;
import com.atguigu.product.mapper.ProductMapper;
import com.atguigu.product.service.InventoryAlertService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class InventoryAlertServiceImpl implements InventoryAlertService {

    private static final Logger log = LoggerFactory.getLogger(InventoryAlertServiceImpl.class);

    private static final String ALERT_LOCK_PREFIX = "lock:alert:";
    private static final String ALERT_TOPIC = "inventory-alert-topic";
    private static final int LOCK_WAIT_TIME = 3;
    private static final int LOCK_LEASE_TIME = 10;
    private static final int MAX_ALERT_PER_DAY = 3;
    private static final int ALERT_SENT_EXPIRE_HOURS = 24;
    private static final int DEFAULT_ALERT_INTERVAL = 1440;
    private static final int MAX_LIMIT = 100;

    private final InventoryAlertConfigMapper alertConfigMapper;
    private final InventoryAlertLogMapper alertLogMapper;
    private final ProductMapper productMapper;
    private final RedissonClient redissonClient;
    private final RocketMQTemplate rocketMQTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void checkInventoryAlert() {
        log.info("开始执行库存预警检查...");

        try {
            List<InventoryAlertConfig> configs = alertConfigMapper.selectAllEnabled();
            if (configs == null || configs.isEmpty()) {
                log.info("没有启用的预警配置");
                return;
            }

            log.info("发现 {} 个启用的预警配置", configs.size());

            int alertCount = 0;
            int skipCount = 0;

            for (InventoryAlertConfig config : configs) {
                try {
                    boolean alerted = checkAndAlert(config);
                    if (alerted) {
                        alertCount++;
                    } else {
                        skipCount++;
                    }
                } catch (org.springframework.dao.DataAccessException e) {
                    log.error("检查商品库存预警数据库异常，productId={}", config.getProductId(), e);
                } catch (Exception e) {
                    log.error("检查商品库存预警失败，productId={}", config.getProductId(), e);
                }
            }

            log.info("库存预警检查完成，发送预警 {} 个，跳过 {} 个", alertCount, skipCount);

        } catch (Exception e) {
            log.error("库存预警检查任务执行失败", e);
        }
    }

    private boolean checkAndAlert(final InventoryAlertConfig config) {
        if (config == null || config.getProductId() == null) {
            return false;
        }
        
        Long productId = config.getProductId();

        Product product = productMapper.selectById(productId);
        if (product == null) {
            log.warn("商品不存在，productId={}", productId);
            return false;
        }

        Integer productNum = product.getNum() != null ? product.getNum() : 0;
        Integer threshold = config.getThreshold() != null ? config.getThreshold() : 0;
        
        if (productNum > threshold) {
            log.debug("商品库存充足，productId={}, stock={}, threshold={}", 
                    productId, productNum, threshold);
            return false;
        }

        log.info("商品库存低于阈值，productId={}, stock={}, threshold={}", 
                productId, productNum, threshold);

        if (!canSendAlert(productId)) {
            log.info("商品今日预警次数已达上限，跳过，productId={}", productId);
            return false;
        }

        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String alertSentKey = CacheKeyConstants.alertSent(productId, today);

        try {
            Boolean sent = stringRedisTemplate.opsForValue().setIfAbsent(
                    alertSentKey, "1", ALERT_SENT_EXPIRE_HOURS, TimeUnit.HOURS);
            if (sent == null || !sent) {
                log.info("商品今日已发送过预警，跳过，productId={}", productId);
                return false;
            }
        } catch (Exception e) {
            log.warn("Redis不可用，使用数据库检查预警记录，productId={}", productId);
            Integer alertInterval = config.getAlertInterval();
            if (!checkAlertFromDb(productId, alertInterval)) {
                log.info("根据数据库记录，商品在预警间隔内已发送过预警，跳过，productId={}", productId);
                return false;
            }
        }

        InventoryAlertLog alertLog = doCreateAlertLog(product, config);
        if (alertLog == null) {
            log.error("创建预警记录失败，productId={}", productId);
            return false;
        }

        sendAlertMessage(alertLog);

        incrementAlertCount(productId, today);

        return true;
    }

    @Override
    public boolean canSendAlert(final Long productId) {
        if (productId == null) {
            return false;
        }

        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String countKey = CacheKeyConstants.alertCount(productId, today);

        try {
            String countStr = stringRedisTemplate.opsForValue().get(countKey);
            int count = 0;
            if (countStr != null) {
                try {
                    count = Integer.parseInt(countStr);
                } catch (NumberFormatException e) {
                    log.warn("解析预警次数失败，countStr={}", countStr, e);
                }
            }
            return count < MAX_ALERT_PER_DAY;
        } catch (Exception e) {
            log.warn("Redis不可用，使用数据库检查预警次数，productId={}", productId);
            int dbCount = alertLogMapper.countTodayByProductId(productId);
            return dbCount < MAX_ALERT_PER_DAY;
        }
    }

    private boolean checkAlertFromDb(final Long productId, final Integer alertInterval) {
        int interval = DEFAULT_ALERT_INTERVAL;
        if (alertInterval != null && alertInterval > 0) {
            interval = alertInterval;
        }

        LocalDateTime startTime = LocalDateTime.now().minusMinutes(interval);
        int count = alertLogMapper.countByProductIdAndTime(productId, startTime);
        return count == 0;
    }

    public InventoryAlertLog doCreateAlertLog(Product product, InventoryAlertConfig config) {
        if (product == null || config == null) {
            return null;
        }
        
        InventoryAlertLog alertLog = new InventoryAlertLog();
        alertLog.setProductId(product.getId());
        alertLog.setMerchantId(product.getMerchantId());
        alertLog.setStock(product.getNum() != null ? product.getNum() : 0);
        alertLog.setThreshold(config.getThreshold() != null ? config.getThreshold() : 0);
        alertLog.setStatus("PENDING");
        alertLog.setCreateTime(LocalDateTime.now());

        try {
            int inserted = alertLogMapper.insert(alertLog);
            if (inserted <= 0) {
                log.error("插入预警记录失败，productId={}", product.getId());
                return null;
            }
            log.info("创建预警记录成功，alertLogId={}, productId={}", alertLog.getId(), product.getId());
            return alertLog;
        } catch (Exception e) {
            log.error("创建预警记录异常，productId={}", product.getId(), e);
            throw e;
        }
    }

    private void sendAlertMessage(InventoryAlertLog alertLog) {
        if (alertLog == null) {
            return;
        }
        
        try {
            InventoryAlertMessage message = InventoryAlertMessage.builder()
                    .alertLogId(alertLog.getId())
                    .productId(alertLog.getProductId())
                    .merchantId(alertLog.getMerchantId())
                    .stock(alertLog.getStock())
                    .threshold(alertLog.getThreshold())
                    .timestamp(System.currentTimeMillis())
                    .build();

            rocketMQTemplate.asyncSend(ALERT_TOPIC, message, new org.apache.rocketmq.client.producer.SendCallback() {
                @Override
                public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                    log.info("预警消息发送成功，alertLogId={}, productId={}", 
                            alertLog.getId(), alertLog.getProductId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("预警消息发送失败，alertLogId={}, productId={}", 
                            alertLog.getId(), alertLog.getProductId(), e);
                    try {
                        alertLogMapper.updateStatus(alertLog.getId(), "FAILED");
                    } catch (Exception ex) {
                        log.error("更新预警记录状态失败，alertLogId={}", alertLog.getId(), ex);
                    }
                }
            });
        } catch (Exception e) {
            log.error("发送预警消息异常，alertLogId={}", alertLog.getId(), e);
        }
    }

    private void incrementAlertCount(final Long productId, final String date) {
        String countKey = CacheKeyConstants.alertCount(productId, date);
        try {
            stringRedisTemplate.opsForValue().increment(countKey);
            stringRedisTemplate.expire(countKey, ALERT_SENT_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("更新预警次数失败，productId={}", productId, e);
        }
    }

    @Override
    public boolean triggerAlertManually(final Long productId) {
        if (productId == null) {
            log.warn("商品ID不能为空");
            return false;
        }

        log.info("手动触发预警，productId={}", productId);

        InventoryAlertConfig config = alertConfigMapper.selectByProductId(productId);
        if (config == null) {
            log.warn("商品未配置预警，productId={}", productId);
            return false;
        }
        
        Integer configStatus = config.getStatus();
        if (configStatus == null || configStatus != 1) {
            log.warn("预警已禁用，productId={}, status={}", productId, configStatus);
            return false;
        }

        Product product = productMapper.selectById(productId);
        if (product == null) {
            log.warn("商品不存在，productId={}", productId);
            return false;
        }

        InventoryAlertLog alertLog = doCreateAlertLog(product, config);
        if (alertLog == null) {
            return false;
        }

        sendAlertMessage(alertLog);

        return true;
    }

    @Override
    public boolean saveOrUpdateConfig(final InventoryAlertConfig config) {
        if (config == null || config.getProductId() == null) {
            log.warn("预警配置参数不完整");
            return false;
        }

        try {
            int result = alertConfigMapper.insertOrUpdate(config);
            return result > 0;
        } catch (Exception e) {
            log.error("保存预警配置失败，productId={}", config.getProductId(), e);
            return false;
        }
    }

    @Override
    public InventoryAlertConfig getConfigByProductId(final Long productId) {
        if (productId == null) {
            return null;
        }
        return alertConfigMapper.selectByProductId(productId);
    }

    @Override
    public List<InventoryAlertConfig> listAllEnabledConfigs() {
        List<InventoryAlertConfig> configs = alertConfigMapper.selectAllEnabled();
        return configs != null ? configs : Collections.emptyList();
    }

    @Override
    public boolean updateConfigStatus(Long id, Integer status) {
        if (id == null || status == null) {
            return false;
        }
        try {
            int result = alertConfigMapper.updateStatus(id, status);
            return result > 0;
        } catch (Exception e) {
            log.error("更新预警配置状态失败，id={}", id, e);
            return false;
        }
    }

    @Override
    public List<InventoryAlertLog> listAlertLogs(final Long productId, final int limit) {
        if (productId == null) {
            return Collections.emptyList();
        }
        
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        
        return alertLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InventoryAlertLog>()
                        .eq(InventoryAlertLog::getProductId, productId)
                        .orderByDesc(InventoryAlertLog::getCreateTime)
                        .last("LIMIT " + safeLimit)
        );
    }

    @Override
    public void processAlertNotification(final Long alertLogId) {
        if (alertLogId == null) {
            log.warn("预警记录ID不能为空");
            return;
        }

        log.info("处理预警通知，alertLogId={}", alertLogId);

        try {
            InventoryAlertLog alertLog = alertLogMapper.selectById(alertLogId);
            if (alertLog == null) {
                log.warn("预警记录不存在，alertLogId={}", alertLogId);
                return;
            }

            String status = alertLog.getStatus();
            if (!"PENDING".equals(status)) {
                log.info("预警记录已处理，alertLogId={}, status={}", alertLogId, status);
                return;
            }

            log.info("查询商家联系方式，merchantId={}", alertLog.getMerchantId());

            log.info("发送预警通知，productId={}, merchantId={}, stock={}, threshold={}",
                    alertLog.getProductId(), alertLog.getMerchantId(), 
                    alertLog.getStock(), alertLog.getThreshold());

            alertLogMapper.updateStatus(alertLogId, "SENT");

            String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String alertSentKey = CacheKeyConstants.alertSent(alertLog.getProductId(), today);
            try {
                stringRedisTemplate.opsForValue().set(alertSentKey, "1", 
                        ALERT_SENT_EXPIRE_HOURS, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warn("设置Redis去重标记失败，productId={}", alertLog.getProductId(), e);
            }

            log.info("预警通知处理完成，alertLogId={}", alertLogId);

        } catch (org.springframework.dao.DataAccessException e) {
            log.error("处理预警通知数据库异常，alertLogId={}", alertLogId, e);
            try {
                alertLogMapper.updateStatus(alertLogId, "FAILED");
            } catch (Exception ex) {
                log.error("更新预警记录状态失败，alertLogId={}", alertLogId, ex);
            }
        } catch (Exception e) {
            log.error("处理预警通知失败，alertLogId={}", alertLogId, e);
            try {
                alertLogMapper.updateStatus(alertLogId, "FAILED");
            } catch (Exception ex) {
                log.error("更新预警记录状态失败，alertLogId={}", alertLogId, ex);
            }
        }
    }
}
