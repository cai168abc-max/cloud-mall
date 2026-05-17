package com.atguigu.order.controller;

import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.context.UserContext;
import com.atguigu.common.enums.UserRole;
import com.atguigu.order.aspect.BusinessMetricsAspect;
import com.atguigu.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final BusinessMetricsAspect metricsAspect;

    @GetMapping("/business")
    public R getBusinessMetrics() {
        UserInfo userInfo = UserContext.get();
        if (userInfo == null) {
            return R.error(401, "请先登录");
        }
        if (userInfo.getRole() != UserRole.ADMIN) {
            return R.error(403, "仅管理员可以查看业务指标");
        }
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("orderCreateCount", metricsAspect.getOrderCreateCount());
        metrics.put("orderPayCount", metricsAspect.getOrderPayCount());
        metrics.put("orderCancelCount", metricsAspect.getOrderCancelCount());
        metrics.put("seckillCount", metricsAspect.getSeckillCount());
        return R.ok("业务指标查询成功", metrics);
    }
}
