package com.atguigu.order.feign;

import com.atguigu.order.fallback.WeatherFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 天气服务Feign客户端
 * 
 * <p>用于获取天气信息，支持根据城市ID或城市名称查询</p>
 * <p>fallback配置确保服务不可用时有明确的降级处理</p>
 */
@FeignClient(value = "weather-client", url = "${weather.api.url:http://aliv18.data.moji.com}", fallback = WeatherFeignFallback.class)
public interface WeatherFeign {

    /**
     * 根据城市ID获取天气信息
     * 
     * @param cityId 城市ID
     * @return 天气信息JSON字符串
     */
    @GetMapping("/whapi/json/aliweather/briefcondition")
    String getWeatherByCityId(@RequestParam("cityId") String cityId);

    /**
     * 根据城市名称获取天气信息
     * 
     * @param cityName 城市名称
     * @return 天气信息JSON字符串
     */
    @GetMapping("/whapi/json/aliweather/briefcondition")
    String getWeatherByCityName(@RequestParam("cityName") String cityName);
}
