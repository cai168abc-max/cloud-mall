package com.atguigu.order.service;

import com.atguigu.common.result.R;

/**
 * 用户Feign服务接口
 * 提供带重试机制的用户服务调用
 */
public interface UserFeignService {
    
    /**
     * 获取用户信息（带重试）
     */
    R getUserInfo(Long userId);
}
