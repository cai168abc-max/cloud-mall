package com.atguigu.common.interceptor;

import com.atguigu.common.utils.HmacSignatureUtil;
import io.micrometer.common.lang.NonNull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 内部请求验证拦截器
 * 功能说明：
 * 1. 验证请求是否来自Gateway（通过检查X-Internal-Request头）
 * 2. 防止外部请求绕过Gateway直接访问微服务
 * 3. 保护用户信息头不被伪造
 * 安全特性：
 * - 使用HMAC-SHA256签名验证内部请求
 * - 包含时间戳防重放攻击（有效期5分钟）
 * - 签名内容包含请求路径，防止签名被复用
 * - 验证失败返回403禁止访问
 * - 记录安全审计日志
 * 使用方式：
 * - 在各微服务的WebMvcConfig中注册此拦截器
 * - 排除健康检查、Swagger等公开路径
 */
@Component
public class InternalRequestInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(InternalRequestInterceptor.class);

    private static final String INTERNAL_REQUEST_HEADER = "X-Internal-Request";

    @Value("${security.internal.secret}")
    private String internalSecret;

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response,@NonNull Object handler) throws Exception {
        String internalHeader = request.getHeader(INTERNAL_REQUEST_HEADER);
        String path = request.getRequestURI();
        
        // 使用HMAC签名验证内部请求标识
        if (!HmacSignatureUtil.verifyInternalRequestToken(internalSecret, path, internalHeader)) {
            log.warn("拒绝未授权的直接访问请求: URI={}, RemoteAddr={}, X-Internal-Request={}", 
                    path, 
                    request.getRemoteAddr(),
                    internalHeader);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Direct access not allowed");
            return false;
        }
        
        return true;
    }
}
