package com.atguigu.order.feign;

import com.atguigu.common.result.R;
import com.atguigu.order.fallback.UserFeignFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "service-user", fallbackFactory = UserFeignFallbackFactory.class)
public interface UserFeign {

    @GetMapping("/internal/api/user/{userId}")
    R getUserInfo(@PathVariable("userId") Long userId);
}
