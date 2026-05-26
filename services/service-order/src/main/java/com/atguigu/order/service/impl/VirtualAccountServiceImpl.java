package com.atguigu.order.service.impl;

import com.atguigu.common.exception.BusinessException;
import com.atguigu.order.bean.VirtualAccount;
import com.atguigu.order.bean.VirtualAccountLog;
import com.atguigu.order.mapper.VirtualAccountLogMapper;
import com.atguigu.order.mapper.VirtualAccountMapper;
import com.atguigu.order.service.VirtualAccountService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 虚拟账户服务实现类
 * 
 * 核心特性：
 * 1. 幂等性：Redis SETNX + 数据库唯一约束
 * 2. 分布式锁：Redisson实现
 * 3. 乐观锁：version字段
 * 4. Redis缓存：余额查询缓存
 * 5. MQ消息：支付/退款通知
 */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class VirtualAccountServiceImpl implements VirtualAccountService {

    private static final Logger log = LoggerFactory.getLogger(VirtualAccountServiceImpl.class);

    private final VirtualAccountMapper accountMapper;
    private final VirtualAccountLogMapper logMapper;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RocketMQTemplate rocketMQTemplate;

    // 缓存相关常量
    private static final String BALANCE_CACHE_KEY_PREFIX = "account:balance:";
    private static final long BALANCE_CACHE_EXPIRE_SECONDS = 300; // 5分钟
    private static final String IDEMPOTENCY_KEY_PREFIX = "account:idempotency:";
    private static final long IDEMPOTENCY_EXPIRE_HOURS = 24;

    // 分布式锁配置
    private static final long LOCK_WAIT_SECONDS = 3;
    private static final long LOCK_LEASE_SECONDS = 5;

    // 乐观锁重试次数
    private static final int OPTIMISTIC_LOCK_RETRY_TIMES = 2;

    private static final Random RANDOM = new Random();

    @Override
    public VirtualAccount getOrCreateAccount(final Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }

        VirtualAccount account = accountMapper.selectByUserId(userId);
        if (account != null) {
            return account;
        }

        // 创建新账户
        account = VirtualAccount.builder()
                .userId(userId)
                .balance(BigDecimal.ZERO)
                .frozenAmount(BigDecimal.ZERO)
                .version(0)
                .status(VirtualAccount.STATUS_NORMAL)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        try {
            accountMapper.insert(account);
            log.info("创建虚拟账户成功, userId={}, accountId={}", userId, account.getId());
        } catch (DuplicateKeyException e) {
            // 并发创建，重新查询
            account = accountMapper.selectByUserId(userId);
            if (account == null) {
                throw new BusinessException("账户创建失败，请重试");
            }
        }

        return account;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VirtualAccountLog recharge(final Long userId, final BigDecimal amount, final String transactionNo) {
        // 参数校验
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("充值金额必须大于0");
        }

        // 生成或使用交易流水号
        String actualTransactionNo = (transactionNo == null || transactionNo.isEmpty()) 
            ? generateTransactionNo("RECHARGE", userId) 
            : transactionNo;

        // 幂等性检查
        if (!checkIdempotency(actualTransactionNo)) {
            // 查询已存在的流水记录
            VirtualAccountLog existLog = logMapper.selectByTransactionNo(actualTransactionNo);
            if (existLog != null) {
                log.info("充值请求重复, transactionNo={}, 已返回历史记录", actualTransactionNo);
                return existLog;
            }
            throw new BusinessException("重复请求，请勿重复提交");
        }

        // 获取分布式锁
        String lockKey = "account:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                deleteIdempotencyKey(actualTransactionNo);
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            try {
                VirtualAccountLog result = doRecharge(userId, amount, actualTransactionNo);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                });
                return result;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteIdempotencyKey(actualTransactionNo);
            throw new BusinessException("系统异常，请稍后重试");
        } catch (Exception e) {
            deleteIdempotencyKey(actualTransactionNo);
            throw e;
        }
    }

    /**
     * 执行充值操作
     */
    private VirtualAccountLog doRecharge(final Long userId, final BigDecimal amount, final String transactionNo) {
        VirtualAccount account = getOrCreateAccount(userId);
        
        if (account.getStatus() != VirtualAccount.STATUS_NORMAL) {
            throw new BusinessException("账户已冻结，无法充值");
        }

        BigDecimal balanceBefore = account.getBalance();
        
        int retryCount = 0;
        int updated = 0;
        
        while (retryCount < OPTIMISTIC_LOCK_RETRY_TIMES) {
            updated = accountMapper.updateBalanceWithVersion(account.getId(), amount, account.getVersion());
            if (updated > 0) {
                break;
            }
            retryCount++;
            account = accountMapper.selectById(account.getId());
            if (account == null) {
                throw new BusinessException("账户不存在");
            }
            balanceBefore = account.getBalance();
        }

        if (updated <= 0) {
            throw new BusinessException("系统繁忙，请稍后重试");
        }

        BigDecimal balanceAfter = account.getBalance().add(amount);

        VirtualAccountLog accountLog = VirtualAccountLog.builder()
                .transactionNo(transactionNo)
                .accountId(account.getId())
                .userId(userId)
                .type(VirtualAccountLog.TYPE_RECHARGE)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .status(VirtualAccountLog.STATUS_SUCCESS)
                .remark("账户充值")
                .createTime(LocalDateTime.now())
                .build();

        try {
            logMapper.insert(accountLog);
        } catch (DuplicateKeyException e) {
            log.warn("充值流水号重复, transactionNo={}", transactionNo);
            VirtualAccountLog existLog = logMapper.selectByTransactionNo(transactionNo);
            if (existLog != null) {
                return existLog;
            }
            throw new BusinessException("充值记录保存失败");
        }

        final Long userIdRef = userId;
        final BigDecimal balanceAfterRef = balanceAfter;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                updateBalanceCache(userIdRef, balanceAfterRef);
            }
        });

        log.info("充值成功, userId={}, amount={}, balanceAfter={}", userId, amount, balanceAfter);
        return accountLog;
    }

    @Override
    public BigDecimal getBalance(final Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }

        // 先查缓存
        String cacheKey = BALANCE_CACHE_KEY_PREFIX + userId;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return new BigDecimal(cached.toString());
            }
        } catch (Exception e) {
            log.warn("Redis缓存读取失败, userId={}, error={}", userId, e.getMessage());
            // Redis不可用时，继续查询数据库
        }

        // 查询数据库
        VirtualAccount account = accountMapper.selectByUserId(userId);
        if (account == null) {
            return BigDecimal.ZERO;
        }

        // 写入缓存
        try {
            redisTemplate.opsForValue().set(cacheKey, account.getBalance().toString(), 
                    BALANCE_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis缓存写入失败, userId={}, error={}", userId, e.getMessage());
        }

        return account.getBalance();
    }

    @Override
    public VirtualAccount getAccount(final Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        return accountMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VirtualAccountLog pay(final Long userId, final BigDecimal amount, final Long orderId, final String transactionNo) {
        // 参数校验
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("支付金额必须大于0");
        }
        if (orderId == null) {
            throw new BusinessException("订单ID不能为空");
        }

        // 生成或使用交易流水号
        String actualTransactionNo = (transactionNo == null || transactionNo.isEmpty()) 
            ? generateTransactionNo("PAY", orderId) 
            : transactionNo;

        // 幂等性检查
        if (!checkIdempotency(actualTransactionNo)) {
            VirtualAccountLog existLog = logMapper.selectByTransactionNo(actualTransactionNo);
            if (existLog != null) {
                log.info("支付请求重复, transactionNo={}, 已返回历史记录", actualTransactionNo);
                return existLog;
            }
            throw new BusinessException("重复请求，请勿重复提交");
        }

        // 获取分布式锁
        String lockKey = "account:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                deleteIdempotencyKey(actualTransactionNo);
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            try {
                VirtualAccountLog result = doPay(userId, amount, orderId, actualTransactionNo);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                });
                return result;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteIdempotencyKey(actualTransactionNo);
            throw new BusinessException("系统异常，请稍后重试");
        } catch (Exception e) {
            deleteIdempotencyKey(actualTransactionNo);
            throw e;
        }
    }

    /**
     * 执行支付操作
     */
    private VirtualAccountLog doPay(final Long userId, final BigDecimal amount, final Long orderId, final String transactionNo) {
        VirtualAccount account = getOrCreateAccount(userId);
        
        if (account.getStatus() != VirtualAccount.STATUS_NORMAL) {
            throw new BusinessException("账户已冻结，无法支付");
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("余额不足");
        }

        BigDecimal balanceBefore = account.getBalance();
        
        int retryCount = 0;
        int updated = 0;
        
        while (retryCount < OPTIMISTIC_LOCK_RETRY_TIMES) {
            updated = accountMapper.deductBalanceWithVersion(account.getId(), amount, account.getVersion());
            if (updated > 0) {
                break;
            }
            retryCount++;
            account = accountMapper.selectById(account.getId());
            if (account == null) {
                throw new BusinessException("账户不存在");
            }
            if (account.getBalance().compareTo(amount) < 0) {
                throw new BusinessException("余额不足");
            }
            balanceBefore = account.getBalance();
        }

        if (updated <= 0) {
            throw new BusinessException("系统繁忙，请稍后重试");
        }

        BigDecimal balanceAfter = account.getBalance().subtract(amount);

        VirtualAccountLog accountLog = VirtualAccountLog.builder()
                .transactionNo(transactionNo)
                .accountId(account.getId())
                .userId(userId)
                .type(VirtualAccountLog.TYPE_PAY)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .relatedOrderId(orderId)
                .status(VirtualAccountLog.STATUS_SUCCESS)
                .remark("订单支付")
                .createTime(LocalDateTime.now())
                .build();

        try {
            logMapper.insert(accountLog);
        } catch (DuplicateKeyException e) {
            log.warn("支付流水号重复, transactionNo={}", transactionNo);
            VirtualAccountLog existLog = logMapper.selectByTransactionNo(transactionNo);
            if (existLog != null) {
                return existLog;
            }
            throw new BusinessException("支付记录保存失败");
        }

        final Long userIdRef = userId;
        final BigDecimal balanceAfterRef = balanceAfter;
        final Long orderIdRef = orderId;
        final BigDecimal amountRef = amount;
        final String transactionNoRef = transactionNo;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                updateBalanceCache(userIdRef, balanceAfterRef);
                sendPaymentMessage(userIdRef, orderIdRef, amountRef, transactionNoRef);
            }
        });

        log.info("支付成功, userId={}, orderId={}, amount={}, balanceAfter={}", 
                userId, orderId, amount, balanceAfter);
        return accountLog;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VirtualAccountLog refund(final Long userId, final BigDecimal amount, final Long orderId, final String transactionNo) {
        // 参数校验
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("退款金额必须大于0");
        }
        if (orderId == null) {
            throw new BusinessException("订单ID不能为空");
        }

        // 生成或使用交易流水号
        String actualTransactionNo = (transactionNo == null || transactionNo.isEmpty()) 
            ? generateTransactionNo("REFUND", orderId) 
            : transactionNo;

        // 幂等性检查
        if (!checkIdempotency(actualTransactionNo)) {
            VirtualAccountLog existLog = logMapper.selectByTransactionNo(actualTransactionNo);
            if (existLog != null) {
                log.info("退款请求重复, transactionNo={}, 已返回历史记录", actualTransactionNo);
                return existLog;
            }
            throw new BusinessException("重复请求，请勿重复提交");
        }

        // 获取分布式锁
        String lockKey = "account:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                deleteIdempotencyKey(actualTransactionNo);
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            try {
                VirtualAccountLog result = doRefund(userId, amount, orderId, actualTransactionNo);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                });
                return result;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteIdempotencyKey(actualTransactionNo);
            throw new BusinessException("系统异常，请稍后重试");
        } catch (Exception e) {
            deleteIdempotencyKey(actualTransactionNo);
            throw e;
        }
    }

    /**
     * 执行退款操作
     */
    private VirtualAccountLog doRefund(final Long userId, final BigDecimal amount, final Long orderId, final String transactionNo) {
        VirtualAccount account = getOrCreateAccount(userId);
        
        if (account.getStatus() != VirtualAccount.STATUS_NORMAL) {
            throw new BusinessException("账户已冻结，无法退款");
        }

        BigDecimal balanceBefore = account.getBalance();
        
        int retryCount = 0;
        int updated = 0;
        
        while (retryCount < OPTIMISTIC_LOCK_RETRY_TIMES) {
            updated = accountMapper.updateBalanceWithVersion(account.getId(), amount, account.getVersion());
            if (updated > 0) {
                break;
            }
            retryCount++;
            account = accountMapper.selectById(account.getId());
            if (account == null) {
                throw new BusinessException("账户不存在");
            }
            balanceBefore = account.getBalance();
        }

        if (updated <= 0) {
            throw new BusinessException("系统繁忙，请稍后重试");
        }

        BigDecimal balanceAfter = account.getBalance().add(amount);

        VirtualAccountLog accountLog = VirtualAccountLog.builder()
                .transactionNo(transactionNo)
                .accountId(account.getId())
                .userId(userId)
                .type(VirtualAccountLog.TYPE_REFUND)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .relatedOrderId(orderId)
                .status(VirtualAccountLog.STATUS_SUCCESS)
                .remark("订单退款")
                .createTime(LocalDateTime.now())
                .build();

        try {
            logMapper.insert(accountLog);
        } catch (DuplicateKeyException e) {
            log.warn("退款流水号重复, transactionNo={}", transactionNo);
            VirtualAccountLog existLog = logMapper.selectByTransactionNo(transactionNo);
            if (existLog != null) {
                return existLog;
            }
            throw new BusinessException("退款记录保存失败");
        }

        final Long userIdRef = userId;
        final BigDecimal balanceAfterRef = balanceAfter;
        final Long orderIdRef = orderId;
        final BigDecimal amountRef = amount;
        final String transactionNoRef = transactionNo;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                updateBalanceCache(userIdRef, balanceAfterRef);
                sendRefundMessage(userIdRef, orderIdRef, amountRef, transactionNoRef);
            }
        });

        log.info("退款成功, userId={}, orderId={}, amount={}, balanceAfter={}", 
                userId, orderId, amount, balanceAfter);
        return accountLog;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public boolean freeze(final Long userId, final BigDecimal amount) {
        if (userId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("参数无效");
        }

        String lockKey = "account:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            try {
                VirtualAccount account = getOrCreateAccount(userId);
                
                if (account.getBalance().compareTo(amount) < 0) {
                    throw new BusinessException("余额不足");
                }

                int updated = accountMapper.freezeBalanceWithVersion(
                        account.getId(), amount, account.getVersion());
                
                if (updated > 0) {
                    final Long userIdRef = userId;
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            clearBalanceCache(userIdRef);
                            if (lock.isHeldByCurrentThread()) {
                                lock.unlock();
                            }
                        }
                    });
                    return true;
                }
                
                return false;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public boolean unfreeze(final Long userId, final BigDecimal amount) {
        if (userId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("参数无效");
        }

        String lockKey = "account:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            try {
                VirtualAccount account = getOrCreateAccount(userId);
                
                if (account.getFrozenAmount().compareTo(amount) < 0) {
                    throw new BusinessException("冻结金额不足");
                }

                int updated = accountMapper.unfreezeBalanceWithVersion(
                        account.getId(), amount, account.getVersion());
                
                if (updated > 0) {
                    final Long userIdRef = userId;
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            clearBalanceCache(userIdRef);
                            if (lock.isHeldByCurrentThread()) {
                                lock.unlock();
                            }
                        }
                    });
                    return true;
                }
                
                return false;
            } catch (Exception e) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    public List<VirtualAccountLog> getTransactionLogs(final Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        return logMapper.selectByUserId(userId);
    }

    @Override
    public VirtualAccountLog getLogByOrderId(final Long orderId) {
        if (orderId == null) {
            return null;
        }
        return logMapper.selectByOrderId(orderId);
    }

    @Override
    public void refreshBalanceCache(final Long userId) {
        if (userId == null) {
            return;
        }
        clearBalanceCache(userId);
        getBalance(userId);
    }

    // ==================== 私有方法 ====================

    /**
     * 生成交易流水号
     * 格式：类型 + 用户ID/订单ID + 时间戳 + 随机数
     */
    private String generateTransactionNo(final String type, final Long id) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int random = RANDOM.nextInt(10000);
        return String.format("%s_%d_%s_%04d", type, id, timestamp, random);
    }

    /**
     * 幂等性检查
     * @param transactionNo 交易流水号
     * @return true-首次请求，false-重复请求
     */
    private boolean checkIdempotency(final String transactionNo) {
        String key = IDEMPOTENCY_KEY_PREFIX + transactionNo;
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                    key, "1", IDEMPOTENCY_EXPIRE_HOURS, TimeUnit.HOURS);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.warn("Redis幂等性检查失败, transactionNo={}, error={}", transactionNo, e.getMessage());
            // Redis不可用时，依赖数据库唯一约束
            return true;
        }
    }

    /**
     * 删除幂等性标记
     */
    private void deleteIdempotencyKey(final String transactionNo) {
        String key = IDEMPOTENCY_KEY_PREFIX + transactionNo;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("删除幂等性标记失败, transactionNo={}, error={}", transactionNo, e.getMessage());
        }
    }

    /**
     * 更新余额缓存
     */
    private void updateBalanceCache(final Long userId, final BigDecimal balance) {
        String cacheKey = BALANCE_CACHE_KEY_PREFIX + userId;
        try {
            redisTemplate.opsForValue().set(cacheKey, balance.toString(), 
                    BALANCE_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("更新余额缓存失败, userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 清除余额缓存
     */
    private void clearBalanceCache(final Long userId) {
        String cacheKey = BALANCE_CACHE_KEY_PREFIX + userId;
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("清除余额缓存失败, userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 发送支付成功消息到MQ
     */
    private void sendPaymentMessage(final Long userId, final Long orderId, final BigDecimal amount, final String transactionNo) {
        try {
            java.util.Map<String, Object> message = new java.util.HashMap<>();
            message.put("userId", userId);
            message.put("orderId", orderId);
            message.put("amount", amount);
            message.put("transactionNo", transactionNo);
            message.put("type", "PAY");
            message.put("timestamp", System.currentTimeMillis());

            rocketMQTemplate.asyncSend("virtual-account-topic", message, 
                    new org.apache.rocketmq.client.producer.SendCallback() {
                        @Override
                        public void onSuccess(final org.apache.rocketmq.client.producer.SendResult sendResult) {
                            log.info("支付消息发送成功, orderId={}, transactionNo={}", orderId, transactionNo);
                        }
                        @Override
                        public void onException(final Throwable e) {
                            log.error("支付消息发送失败, orderId={}, transactionNo={}", orderId, transactionNo, e);
                        }
                    });
        } catch (Exception e) {
            log.error("发送支付消息异常, orderId={}", orderId, e);
        }
    }

    /**
     * 发送退款消息到MQ
     */
    private void sendRefundMessage(final Long userId, final Long orderId, final BigDecimal amount, final String transactionNo) {
        try {
            java.util.Map<String, Object> message = new java.util.HashMap<>();
            message.put("userId", userId);
            message.put("orderId", orderId);
            message.put("amount", amount);
            message.put("transactionNo", transactionNo);
            message.put("type", "REFUND");
            message.put("timestamp", System.currentTimeMillis());

            rocketMQTemplate.asyncSend("virtual-account-topic", message, 
                    new org.apache.rocketmq.client.producer.SendCallback() {
                        @Override
                        public void onSuccess(final org.apache.rocketmq.client.producer.SendResult sendResult) {
                            log.info("退款消息发送成功, orderId={}, transactionNo={}", orderId, transactionNo);
                        }
                        @Override
                        public void onException(final Throwable e) {
                            log.error("退款消息发送失败, orderId={}, transactionNo={}", orderId, transactionNo, e);
                        }
                    });
        } catch (Exception e) {
            log.error("发送退款消息异常, orderId={}", orderId, e);
        }
    }
}
