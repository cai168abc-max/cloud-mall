package com.atguigu.order.controller;

import com.atguigu.common.result.R;
import com.atguigu.common.enums.UserRole;
import com.atguigu.common.service.RateLimitService;
import com.atguigu.order.bean.Order;
import com.atguigu.common.context.UserContext;
import com.atguigu.order.service.SeckillService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * 秒杀控制器
 * 安全特性：
 * 1. 用户维度限流：防止用户频繁刷单
 * 2. 角色校验：仅普通用户可参与秒杀
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
@RestController
@RequestMapping("/api/seckill")
@Validated
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;
    private final RateLimitService rateLimitService;
    
    // 用户秒杀限流配置：每用户每秒最多1次请求
    private static final int SECKILL_RATE_LIMIT_PER_SECOND = 1;
    private static final int SECKILL_RATE_LIMIT_WINDOW_SECONDS = 1;

    @PostMapping
    public R doSeckill(@RequestParam @NotNull final Long productId) {
        if (UserContext.get() == null || UserContext.get().getRole() != UserRole.USER) {
            return R.error(403, "仅普通用户可以参与秒杀");
        }
        Long userId = UserContext.get().getId();
        
        // 用户维度限流检查
        String rateLimitKey = "seckill:user:" + userId;
        if (!rateLimitService.tryAcquire(rateLimitKey, SECKILL_RATE_LIMIT_PER_SECOND, SECKILL_RATE_LIMIT_WINDOW_SECONDS)) {
            return R.error(429, "请求过于频繁，请稍后再试");
        }
        
        Order order = seckillService.seckill(productId, userId);
        if (order == null) {
            return R.error(400, "秒杀失败，可能已售罄");
        }
        return R.ok("秒杀成功", order);
    }
}


