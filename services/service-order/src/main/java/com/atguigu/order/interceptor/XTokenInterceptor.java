package com.atguigu.order.interceptor;

import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.context.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.apache.seata.core.context.RootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Feign请求拦截器：传递认证信息到下游服务
 * 功能：
 * 1. 传递原始JWT Token（Authorization头）
 * 2. 传递用户上下文信息（X-User-*头）
 * 3. 支持服务间调用的认证链路追踪
 */
@Component
public class XTokenInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(XTokenInterceptor.class);

    @Override
    public void apply(final RequestTemplate requestTemplate) {
        // 1. 尝试从当前请求中获取原始Authorization头
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authorization = request.getHeader("Authorization");
            if (authorization != null && !authorization.isEmpty()) {
                requestTemplate.header("Authorization", authorization);
                log.debug("Feign请求传递Authorization头");
            }
            
            // 2. 传递X-Token（如果存在）
            String xToken = request.getHeader("X-Token");
            if (xToken != null && !xToken.isEmpty()) {
                requestTemplate.header("X-Token", xToken);
                log.debug("Feign请求传递X-Token头");
            }
        }
        
        // 3. 传递用户上下文信息（作为备份）
        UserInfo user = UserContext.get();
        if (user != null) {
            requestTemplate.header("X-User-Id", String.valueOf(user.getId()));
            if (user.getNickName() != null) {
                requestTemplate.header("X-User-Name", user.getNickName());
            }
            if (user.getRole() != null) {
                requestTemplate.header("X-User-Role", user.getRole().name());
            }
            log.debug("Feign请求传递用户上下文: userId={}, role={}", user.getId(), user.getRole());
        }
        String xid = RootContext.getXID();
        if (xid != null) {
            requestTemplate.header("TX_XID", xid);
            log.debug("Feign请求传播Seata XID: {}", xid);
        }
    }
}
