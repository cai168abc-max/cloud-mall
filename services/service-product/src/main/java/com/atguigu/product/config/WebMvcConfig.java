package com.atguigu.product.config;

import com.atguigu.common.config.BaseWebMvcConfig;
import com.atguigu.common.interceptor.InternalRequestInterceptor;
import org.springframework.context.annotation.Configuration;

/**
 * WebMvc配置类
 * 继承公共基类，复用拦截器配置
 */
@Configuration
public class WebMvcConfig extends BaseWebMvcConfig {
    
    public WebMvcConfig(final InternalRequestInterceptor internalRequestInterceptor) {
        super(internalRequestInterceptor);
    }
}
