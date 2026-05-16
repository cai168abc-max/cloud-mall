package com.atguigu.common.config;

import com.atguigu.common.interceptor.InternalRequestInterceptor;
import com.atguigu.common.interceptor.UserContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc公共配置基类
 * 提供统一的拦截器配置，各服务可继承此类并根据需要扩展
 * 
 * <p>默认配置：</p>
 * <ul>
 *   <li>内部请求验证拦截器（优先级0）：防止绕过Gateway直接访问服务</li>
 *   <li>用户上下文拦截器（优先级1）：解析用户信息到ThreadLocal</li>
 * </ul>
 * 
 * <p>使用方式：</p>
 * <pre>
 * &#064;Configuration
 * public class MyWebMvcConfig extends BaseWebMvcConfig {
 *     public MyWebMvcConfig(InternalRequestInterceptor internalRequestInterceptor) {
 *         super(internalRequestInterceptor);
 *     }
 *     
 *     // 如需添加额外拦截器，重写 addInterceptors 方法
 * }
 * </pre>
 * 
 * @author Backend Architect
 * @since 1.0.0
 */
@RequiredArgsConstructor
public abstract class BaseWebMvcConfig implements WebMvcConfigurer {
    
    protected final InternalRequestInterceptor internalRequestInterceptor;
    
    /**
     * 公共排除路径，这些路径不需要经过拦截器
     */
    protected static final String[] COMMON_EXCLUDE_PATHS = {
            "/actuator/**",
            "/health",
            "/metrics",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/doc.html",
            "/webjars/**",
            "/error"
    };
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 内部请求验证拦截器（优先级最高，防止绕过Gateway）
        registry.addInterceptor(internalRequestInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(COMMON_EXCLUDE_PATHS)
                .order(0);
        
        // 用户上下文拦截器
        registry.addInterceptor(new UserContextInterceptor())
                .addPathPatterns("/**")
                .order(1);
    }
}
