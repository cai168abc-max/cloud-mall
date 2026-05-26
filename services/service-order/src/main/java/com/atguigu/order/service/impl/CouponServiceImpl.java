package com.atguigu.order.service.impl;

import com.atguigu.common.enums.CouponStatus;
import com.atguigu.order.bean.Coupon;
import com.atguigu.order.mapper.CouponMapper;
import com.atguigu.order.service.CouponService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class CouponServiceImpl implements CouponService {

    private static final String COUPON_STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String USER_COUPON_KEY_PREFIX = "user:coupon:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final CouponMapper couponMapper;

    private void executeAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static final String DEDUCT_STOCK_SCRIPT = """
            local stockKey = KEYS[1]
            local quantity = tonumber(ARGV[1])
            local current = tonumber(redis.call('GET', stockKey) or '0')
            if current >= quantity then
                redis.call('DECRBY', stockKey, quantity)
                return current - quantity
            else
                return -1
            end
            """;

    private final DefaultRedisScript<Long> deductStockScript = new DefaultRedisScript<>(DEDUCT_STOCK_SCRIPT, Long.class);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Coupon createCoupon(Coupon coupon) {
        if (coupon.getValidFrom() == null) {
            coupon.setValidFrom(LocalDateTime.now());
        }
        if (coupon.getValidTo() == null) {
            coupon.setValidTo(LocalDateTime.now().plusDays(7));
        }
        coupon.setStatus(CouponStatus.ACTIVE);
        couponMapper.insertCoupon(coupon);

        final Long couponId = coupon.getId();
        final Integer stock = coupon.getStock();
        executeAfterCommit(() -> {
            String stockKey = COUPON_STOCK_KEY_PREFIX + couponId;
            redisTemplate.opsForValue().set(stockKey, stock);
        });
        return coupon;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean acquireCoupon(Long couponId, Long userId) {
        String userKey = USER_COUPON_KEY_PREFIX + userId + ":" + couponId;
        Boolean isAcquired = redisTemplate.opsForValue().setIfAbsent(userKey, "1", 7, java.util.concurrent.TimeUnit.DAYS);
        if (Boolean.FALSE.equals(isAcquired)) {
            return false;
        }

        try {
            Coupon coupon = couponMapper.selectById(couponId);
            if (coupon == null) {
                return false;
            }
            if (!CouponStatus.ACTIVE.equals(coupon.getStatus())) {
                return false;
            }
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(coupon.getValidFrom()) || now.isAfter(coupon.getValidTo())) {
                return false;
            }

            String stockKey = COUPON_STOCK_KEY_PREFIX + couponId;
            Long remain = redisTemplate.execute(
                    deductStockScript,
                    Collections.singletonList(stockKey),
                    "1"
            );

            if (remain == null || remain < 0) {
                redisTemplate.delete(userKey);
                couponMapper.updateStatus(couponId, CouponStatus.USED_OUT.name());
                return false;
            }

            couponMapper.decrementStock(couponId);
            return true;
        } catch (Exception e) {
            redisTemplate.delete(userKey);
            throw e;
        }
    }

    @Override
    public Coupon getValidCouponForUse(Long couponId, Long userId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!CouponStatus.ACTIVE.equals(coupon.getStatus())
                || now.isBefore(coupon.getValidFrom())
                || now.isAfter(coupon.getValidTo())) {
            return null;
        }
        String userKey = USER_COUPON_KEY_PREFIX + userId + ":" + couponId;
        Object flag = redisTemplate.opsForValue().get(userKey);
        if (flag == null) {
            return null;
        }
        return coupon;
    }

    @Override
    public Coupon getCouponById(final Long couponId) {
        return couponMapper.selectById(couponId);
    }

    @Override
    public List<Coupon> listCouponsByUserId(Long userId) {
        return couponMapper.selectByUserId(userId);
    }

    @Override
    public List<Coupon> listValidCouponsForUser(Long userId) {
        return couponMapper.selectValidCouponsForUser(userId, LocalDateTime.now());
    }

    @Override
    public List<Coupon> listAllCoupons() {
        return couponMapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Coupon updateCoupon(Coupon coupon) {
        couponMapper.updateById(coupon);
        final Long couponIdRef = coupon.getId();
        executeAfterCommit(() -> {
            String stockKey = COUPON_STOCK_KEY_PREFIX + couponIdRef;
            redisTemplate.delete(stockKey);
        });
        return couponMapper.selectById(coupon.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCoupon(final Long couponId) {
        couponMapper.deleteById(couponId);
        final Long couponIdRef = couponId;
        executeAfterCommit(() -> {
            String stockKey = COUPON_STOCK_KEY_PREFIX + couponIdRef;
            redisTemplate.delete(stockKey);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireCoupons() {
        couponMapper.expireCoupons(LocalDateTime.now());
    }
}
