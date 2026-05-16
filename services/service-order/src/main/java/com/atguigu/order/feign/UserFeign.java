package com.atguigu.order.feign;

import com.atguigu.common.result.R;
import com.atguigu.common.bean.UserAddress;
import com.atguigu.order.fallback.UserFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "service-user", fallback = UserFeignFallback.class)
public interface UserFeign {
    
    // 获取用户地址列表
    @GetMapping("/api/user/me")
    R getUserInfo(@RequestParam("userId") Long userId);
}
