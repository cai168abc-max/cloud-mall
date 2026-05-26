package com.atguigu.order.fallback;

import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.cache.CacheKeyConstants;
import com.atguigu.common.enums.UserRole;
import com.atguigu.common.result.R;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * UserFeign降级处理
 * 
 * <p>降级策略：</p>
 * <ul>
 *   <li>优先从Redis缓存获取用户信息</li>
 *   <li>缓存不存在时返回默认用户信息</li>
 *   <li>Redis不可用时直接返回默认值</li>
 * </ul>
 */
@Component
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class UserFeignFallback implements com.atguigu.order.feign.UserFeign {

    private static final Logger log = LoggerFactory.getLogger(UserFeignFallback.class);
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public UserFeignFallback(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public R getUserInfo(Long userId) {
        log.warn("UserFeign降级触发 - 用户服务不可用, userId={}", userId);
        
        // 1. 尝试从Redis缓存获取
        try {
            String cacheKey = CacheKeyConstants.userInfo(userId);
            UserInfo cachedUserInfo = (UserInfo) redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedUserInfo != null) {
                log.info("降级返回缓存用户数据, userId={}", userId);
                return R.ok("服务降级：返回缓存数据", cachedUserInfo);
            }
        } catch (Exception e) {
            log.error("Redis获取用户缓存失败, userId={}, error={}", userId, e.getMessage());
        }
        
        // 2. 返回默认用户信息
        UserInfo defaultUserInfo = createDefaultUserInfo(userId);
        log.warn("降级返回默认用户数据, userId={}", userId);
        return R.ok("服务降级：返回默认数据", defaultUserInfo);
    }
    
    /**
     * 创建默认用户信息
     */
    private UserInfo createDefaultUserInfo(Long userId) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        userInfo.setNickName("用户信息加载中");
        userInfo.setRole(UserRole.USER);
        userInfo.setEnabled(false);
        userInfo.setVerified(false);
        return userInfo;
    }
}
