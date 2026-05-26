package com.atguigu.order.fallback;

import com.atguigu.order.feign.WeatherFeign;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * WeatherFeign降级处理
 * 
 * <p>降级策略：</p>
 * <ul>
 *   <li>天气服务为非核心服务，降级时返回默认响应</li>
 *   <li>不进行Redis缓存，避免缓存污染</li>
 *   <li>返回JSON格式的默认响应，便于前端解析</li>
 * </ul>
 */
@Component
public class WeatherFeignFallback implements WeatherFeign {

    private static final Logger log = LoggerFactory.getLogger(WeatherFeignFallback.class);

    /**
     * 默认天气响应（JSON格式）
     * 包含降级标识和友好提示
     */
    private static final String DEFAULT_WEATHER_RESPONSE = "{\"code\":503,\"message\":\"天气服务暂时不可用\",\"data\":null,\"degraded\":true}";

    @Override
    public String getWeatherByCityId(final String cityId) {
        log.warn("WeatherFeign降级触发 - 天气服务不可用, cityId={}", cityId);
        return DEFAULT_WEATHER_RESPONSE;
    }

    @Override
    public String getWeatherByCityName(final String cityName) {
        log.warn("WeatherFeign降级触发 - 天气服务不可用, cityName={}", cityName);
        return DEFAULT_WEATHER_RESPONSE;
    }
}
