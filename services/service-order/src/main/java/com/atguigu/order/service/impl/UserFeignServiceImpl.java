package com.atguigu.order.service.impl;

import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.cache.CacheKeyConstants;
import com.atguigu.common.enums.UserRole;
import com.atguigu.common.result.R;
import com.atguigu.order.feign.UserFeign;
import com.atguigu.order.service.UserFeignService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * 用户Feign服务实现类
 * 
 * <p>重试策略：</p>
 * <ul>
 *   <li>最大重试次数：3次</li>
 *   <li>退避策略：指数退避（初始1秒，乘数2，最大5秒）</li>
 *   <li>重试异常：所有Exception</li>
 *   <li>兜底方案：重试失败后触发@Recover降级方法</li>
 * </ul>
 */
@Service
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class UserFeignServiceImpl implements UserFeignService {

    private static final Logger log = LoggerFactory.getLogger(UserFeignServiceImpl.class);
    
    private final UserFeign userFeign;
    private final RedisTemplate<String, Object> redisTemplate;
    
    public UserFeignServiceImpl(UserFeign userFeign, 
                                RedisTemplate<String, Object> redisTemplate) {
        this.userFeign = userFeign;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Retryable(
        retryFor = Exception.class,
            backoff = @Backoff(
            delay = 1000,
            multiplier = 2.0,
            maxDelay = 5000
        )
    )
    public R getUserInfo(final Long userId) {
        log.debug("调用用户服务获取用户信息, userId={}", userId);
        return userFeign.getUserInfo(userId);
    }
    
    @Recover
    public R getUserInfoRecover(final Exception e, final Long userId) {
        log.error("UserFeign重试失败 - 用户服务不可用, userId={}, error={}", userId, e.getMessage());
        
        // 1. 尝试从Redis缓存获取
        try {
            String cacheKey = CacheKeyConstants.userInfo(userId);
            UserInfo cachedUserInfo = (UserInfo) redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedUserInfo != null) {
                log.info("重试失败后返回缓存用户数据, userId={}", userId);
                return R.ok("服务降级：返回缓存数据", cachedUserInfo);
            }
        } catch (Exception redisEx) {
            log.error("Redis获取用户缓存失败, userId={}, error={}", userId, redisEx.getMessage());
        }
        
        // 2. 返回默认用户信息
        UserInfo defaultUserInfo = createDefaultUserInfo(userId);
        log.warn("重试失败后返回默认用户数据, userId={}", userId);
        return R.ok("服务降级：返回默认数据", defaultUserInfo);
    }
    
    /**
     * 创建默认用户信息
     */
    private UserInfo createDefaultUserInfo(final Long userId) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        userInfo.setNickName("用户信息加载中");
        userInfo.setRole(UserRole.USER);
        userInfo.setEnabled(false);
        userInfo.setVerified(false);
        return userInfo;
    }
}
