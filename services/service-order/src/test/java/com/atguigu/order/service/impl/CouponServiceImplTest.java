package com.atguigu.order.service.impl;

import com.atguigu.common.enums.CouponStatus;
import com.atguigu.order.bean.Coupon;
import com.atguigu.order.mapper.CouponMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CouponServiceImpl 单元测试类
 * 测试优惠券服务相关业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponServiceImpl 单元测试")
class CouponServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private CouponMapper couponMapper;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private CouponServiceImpl couponService;

    private static final String COUPON_STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String USER_COUPON_KEY_PREFIX = "user:coupon:";

    @Nested
    @DisplayName("createCoupon 方法测试")
    class CreateCouponTests {

        @Test
        @DisplayName("应该成功创建优惠券")
        void should_createCoupon_successfully() {
            // Given
            Coupon coupon = createTestCoupon(null, "测试优惠券", BigDecimal.valueOf(50));
            
            when(couponMapper.insertCoupon(any(Coupon.class))).thenAnswer(invocation -> {
                Coupon c = invocation.getArgument(0);
                c.setId(1L);
                return 1;
            });
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // When
            Coupon result = couponService.createCoupon(coupon);

            // Then
            assertNotNull(result, "结果不应为空");
            assertNotNull(result.getId(), "ID应被设置");
            assertEquals(CouponStatus.ACTIVE, result.getStatus(), "状态应为ACTIVE");
            assertNotNull(result.getValidFrom(), "生效时间应被设置");
            assertNotNull(result.getValidTo(), "过期时间应被设置");
            verify(redisTemplate.opsForValue()).set(startsWith(COUPON_STOCK_KEY_PREFIX), eq(coupon.getStock()));
        }

        @Test
        @DisplayName("应该使用默认有效期当未指定时")
        void should_useDefaultValidity_whenNotSpecified() {
            // Given
            Coupon coupon = new Coupon();
            coupon.setName("测试优惠券");
            coupon.setAmount(BigDecimal.valueOf(50));
            coupon.setStock(100);
            // 不设置validFrom和validTo
            
            when(couponMapper.insertCoupon(any(Coupon.class))).thenAnswer(invocation -> {
                Coupon c = invocation.getArgument(0);
                c.setId(1L);
                return 1;
            });
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // When
            Coupon result = couponService.createCoupon(coupon);

            // Then
            assertNotNull(result.getValidFrom(), "生效时间应被设置");
            assertNotNull(result.getValidTo(), "过期时间应被设置");
        }
    }

    @Nested
    @DisplayName("acquireCoupon 方法测试")
    class AcquireCouponTests {

        @Test
        @DisplayName("应该成功领取优惠券")
        void should_acquireCoupon_successfully() {
            // Given
            Long couponId = 1L;
            Long userId = 1L;
            String userKey = USER_COUPON_KEY_PREFIX + userId + ":" + couponId;
            
            Coupon coupon = createTestCoupon(couponId, "测试优惠券", BigDecimal.valueOf(50));
            coupon.setValidFrom(LocalDateTime.now().minusDays(1));
            coupon.setValidTo(LocalDateTime.now().plusDays(7));
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(userKey), eq("1"), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(couponMapper.selectById(couponId)).thenReturn(coupon);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any())).thenReturn(99L);

            // When
            boolean result = couponService.acquireCoupon(couponId, userId);

            // Then
            assertTrue(result, "领取优惠券应成功");
        }

        @Test
        @DisplayName("应该返回false当用户已领取过")
        void should_returnFalse_whenAlreadyAcquired() {
            // Given
            Long couponId = 1L;
            Long userId = 1L;
            String userKey = USER_COUPON_KEY_PREFIX + userId + ":" + couponId;
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(userKey), eq("1"), anyLong(), any(TimeUnit.class))).thenReturn(false);

            // When
            boolean result = couponService.acquireCoupon(couponId, userId);

            // Then
            assertFalse(result, "已领取过应返回false");
            verify(couponMapper, never()).selectById(any());
        }

        @Test
        @DisplayName("应该返回false当优惠券不存在")
        void should_returnFalse_whenCouponNotExist() {
            // Given
            Long couponId = 999L;
            Long userId = 1L;
            String userKey = USER_COUPON_KEY_PREFIX + userId + ":" + couponId;
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(userKey), eq("1"), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(couponMapper.selectById(couponId)).thenReturn(null);

            // When
            boolean result = couponService.acquireCoupon(couponId, userId);

            // Then
            assertFalse(result, "优惠券不存在应返回false");
        }

        @Test
        @DisplayName("应该返回false当优惠券状态不正确")
        void should_returnFalse_whenCouponStatusInvalid() {
            // Given
            Long couponId = 1L;
            Long userId = 1L;
            String userKey = USER_COUPON_KEY_PREFIX + userId + ":" + couponId;
            
            Coupon coupon = createTestCoupon(couponId, "测试优惠券", BigDecimal.valueOf(50));
            coupon.setStatus(CouponStatus.USED_OUT);
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(userKey), eq("1"), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(couponMapper.selectById(couponId)).thenReturn(coupon);

            // When
            boolean result = couponService.acquireCoupon(couponId, userId);

            // Then
            assertFalse(result, "状态不正确应返回false");
        }

        @Test
        @DisplayName("应该返回false当优惠券已过期")
        void should_returnFalse_whenCouponExpired() {
            // Given
            Long couponId = 1L;
            Long userId = 1L;
            String userKey = USER_COUPON_KEY_PREFIX + userId + ":" + couponId;
            
            Coupon coupon = createTestCoupon(couponId, "测试优惠券", BigDecimal.valueOf(50));
            coupon.setValidFrom(LocalDateTime.now().minusDays(10));
            coupon.setValidTo(LocalDateTime.now().minusDays(1)); // 已过期
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(userKey), eq("1"), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(couponMapper.selectById(couponId)).thenReturn(coupon);

            // When
            boolean result = couponService.acquireCoupon(couponId, userId);

            // Then
            assertFalse(result, "已过期应返回false");
        }

        @Test
        @DisplayName("应该返回false当库存不足")
        void should_returnFalse_whenStockInsufficient() {
            // Given
            Long couponId = 1L;
            Long userId = 1L;
            String userKey = USER_COUPON_KEY_PREFIX + userId + ":" + couponId;
            
            Coupon coupon = createTestCoupon(couponId, "测试优惠券", BigDecimal.valueOf(50));
            coupon.setValidFrom(LocalDateTime.now().minusDays(1));
            coupon.setValidTo(LocalDateTime.now().plusDays(7));
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(userKey), eq("1"), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(couponMapper.selectById(couponId)).thenReturn(coupon);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any())).thenReturn(-1L);
            when(redisTemplate.delete(userKey)).thenReturn(true);

            // When
            boolean result = couponService.acquireCoupon(couponId, userId);

            // Then
            assertFalse(result, "库存不足应返回false");
            verify(couponMapper).updateStatus(couponId, CouponStatus.USED_OUT.name());
        }
    }

    @Nested
    @DisplayName("getValidCouponForUse 方法测试")
    class GetValidCouponForUseTests {

        @Test
        @DisplayName("应该返回有效优惠券")
        void should_returnValidCoupon() {
            // Given
            Long couponId = 1L;
            Long userId = 1L;
            String userKey = USER_COUPON_KEY_PREFIX + userId + ":" + couponId;
            
            Coupon coupon = createTestCoupon(couponId, "测试优惠券", BigDecimal.valueOf(50));
            coupon.setValidFrom(LocalDateTime.now().minusDays(1));
            coupon.setValidTo(LocalDateTime.now().plusDays(7));
            
            when(couponMapper.selectById(couponId)).thenReturn(coupon);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(userKey)).thenReturn("1");

            // When
            Coupon result = couponService.getValidCouponForUse(couponId, userId);

            // Then
            assertNotNull(result, "应返回有效优惠券");
        }

        @Test
        @DisplayName("应该返回null当优惠券不存在")
        void should_returnNull_whenCouponNotExist() {
            // Given
            Long couponId = 999L;
            Long userId = 1L;
            
            when(couponMapper.selectById(couponId)).thenReturn(null);

            // When
            Coupon result = couponService.getValidCouponForUse(couponId, userId);

            // Then
            assertNull(result, "优惠券不存在应返回null");
        }

        @Test
        @DisplayName("应该返回null当优惠券状态不正确")
        void should_returnNull_whenCouponStatusInvalid() {
            // Given
            Long couponId = 1L;
            Long userId = 1L;
            
            Coupon coupon = createTestCoupon(couponId, "测试优惠券", BigDecimal.valueOf(50));
            coupon.setStatus(CouponStatus.USED_OUT);
            
            when(couponMapper.selectById(couponId)).thenReturn(coupon);

            // When
            Coupon result = couponService.getValidCouponForUse(couponId, userId);

            // Then
            assertNull(result, "状态不正确应返回null");
        }

        @Test
        @DisplayName("应该返回null当用户未领取该优惠券")
        void should_returnNull_whenUserNotAcquired() {
            // Given
            Long couponId = 1L;
            Long userId = 1L;
            String userKey = USER_COUPON_KEY_PREFIX + userId + ":" + couponId;
            
            Coupon coupon = createTestCoupon(couponId, "测试优惠券", BigDecimal.valueOf(50));
            coupon.setValidFrom(LocalDateTime.now().minusDays(1));
            coupon.setValidTo(LocalDateTime.now().plusDays(7));
            
            when(couponMapper.selectById(couponId)).thenReturn(coupon);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(userKey)).thenReturn(null);

            // When
            Coupon result = couponService.getValidCouponForUse(couponId, userId);

            // Then
            assertNull(result, "用户未领取应返回null");
        }
    }

    @Nested
    @DisplayName("getCouponById 方法测试")
    class GetCouponByIdTests {

        @Test
        @DisplayName("应该返回优惠券信息")
        void should_returnCoupon() {
            // Given
            Long couponId = 1L;
            Coupon coupon = createTestCoupon(couponId, "测试优惠券", BigDecimal.valueOf(50));
            
            when(couponMapper.selectById(couponId)).thenReturn(coupon);

            // When
            Coupon result = couponService.getCouponById(couponId);

            // Then
            assertNotNull(result, "应返回优惠券");
            assertEquals(couponId, result.getId(), "优惠券ID应正确");
        }
    }

    @Nested
    @DisplayName("listCouponsByUserId 方法测试")
    class ListCouponsByUserIdTests {

        @Test
        @DisplayName("应该返回用户优惠券列表")
        void should_listCouponsByUserId() {
            // Given
            Long userId = 1L;
            Coupon coupon1 = createTestCoupon(1L, "优惠券1", BigDecimal.valueOf(50));
            Coupon coupon2 = createTestCoupon(2L, "优惠券2", BigDecimal.valueOf(100));
            
            when(couponMapper.selectByUserId(userId)).thenReturn(Arrays.asList(coupon1, coupon2));

            // When
            List<Coupon> result = couponService.listCouponsByUserId(userId);

            // Then
            assertNotNull(result, "结果不应为空");
            assertEquals(2, result.size(), "应返回2个优惠券");
        }
    }

    @Nested
    @DisplayName("listValidCouponsForUser 方法测试")
    class ListValidCouponsForUserTests {

        @Test
        @DisplayName("应该返回用户有效优惠券列表")
        void should_listValidCouponsForUser() {
            // Given
            Long userId = 1L;
            Coupon coupon1 = createTestCoupon(1L, "有效优惠券", BigDecimal.valueOf(50));
            coupon1.setStatus(CouponStatus.ACTIVE);
            coupon1.setValidFrom(LocalDateTime.now().minusDays(1));
            coupon1.setValidTo(LocalDateTime.now().plusDays(7));
            
            when(couponMapper.selectValidCouponsForUser(eq(userId), any(LocalDateTime.class)))
                .thenReturn(List.of(coupon1));

            // When
            List<Coupon> result = couponService.listValidCouponsForUser(userId);

            // Then
            assertNotNull(result, "结果不应为空");
            assertEquals(1, result.size(), "应返回1个有效优惠券");
        }
    }

    @Nested
    @DisplayName("updateCoupon 方法测试")
    class UpdateCouponTests {

        @Test
        @DisplayName("应该成功更新优惠券")
        void should_updateCoupon_successfully() {
            // Given
            Coupon coupon = createTestCoupon(1L, "更新后的优惠券", BigDecimal.valueOf(80));
            
            when(couponMapper.updateById(coupon)).thenReturn(1);
            when(couponMapper.selectById(coupon.getId())).thenReturn(coupon);

            // When
            Coupon result = couponService.updateCoupon(coupon);

            // Then
            assertNotNull(result, "结果不应为空");
            assertEquals("更新后的优惠券", result.getName(), "名称应更新");
        }
    }

    @Nested
    @DisplayName("deleteCoupon 方法测试")
    class DeleteCouponTests {

        @Test
        @DisplayName("应该成功删除优惠券")
        void should_deleteCoupon_successfully() {
            // Given
            Long couponId = 1L;
            
            when(couponMapper.deleteById(couponId)).thenReturn(1);
            when(redisTemplate.delete(startsWith(COUPON_STOCK_KEY_PREFIX))).thenReturn(true);

            // When
            couponService.deleteCoupon(couponId);

            // Then
            verify(couponMapper).deleteById(couponId);
            verify(redisTemplate).delete(startsWith(COUPON_STOCK_KEY_PREFIX));
        }
    }

    @Nested
    @DisplayName("expireCoupons 方法测试")
    class ExpireCouponsTests {

        @Test
        @DisplayName("应该成功过期优惠券")
        void should_expireCoupons_successfully() {
            // Given
            when(couponMapper.expireCoupons(any(LocalDateTime.class))).thenReturn(5);

            // When
            couponService.expireCoupons();

            // Then
            verify(couponMapper).expireCoupons(any(LocalDateTime.class));
        }
    }

    // ========== 辅助方法 ==========

    private Coupon createTestCoupon(Long id, String name, BigDecimal amount) {
        Coupon coupon = new Coupon();
        coupon.setId(id);
        coupon.setName(name);
        coupon.setAmount(amount);
        coupon.setThreshold(BigDecimal.valueOf(100));
        coupon.setStock(100);
        coupon.setStatus(CouponStatus.ACTIVE);
        coupon.setMerchantId(1L);
        coupon.setValidFrom(LocalDateTime.now().minusDays(1));
        coupon.setValidTo(LocalDateTime.now().plusDays(7));
        return coupon;
    }
}
