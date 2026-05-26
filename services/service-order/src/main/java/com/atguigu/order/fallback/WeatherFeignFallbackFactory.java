package com.atguigu.order.fallback;

import com.atguigu.order.feign.WeatherFeign;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class WeatherFeignFallbackFactory implements FallbackFactory<WeatherFeign> {

    private static final Logger log = LoggerFactory.getLogger(WeatherFeignFallbackFactory.class);

    @Override
    public WeatherFeign create(final Throwable cause) {
        log.error("WeatherFeign调用失败, 原因: {}", cause.getMessage(), cause);

        return new WeatherFeign() {
            @Override
            public String getWeatherByCityId(final String cityId) {
                log.warn("WeatherFeign降级触发 - 天气服务不可用, cityId={}", cityId);
                return "{\"error\": \"天气服务暂时不可用\"}";
            }

            @Override
            public String getWeatherByCityName(final String cityName) {
                log.warn("WeatherFeign降级触发 - 天气服务不可用, cityName={}", cityName);
                return "{\"error\": \"天气服务暂时不可用\"}";
            }
        };
    }
}
