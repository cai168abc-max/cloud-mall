package com.atguigu.common.interceptor;

import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.context.UserContext;
import com.atguigu.common.enums.UserRole;
import io.micrometer.common.lang.NonNull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户上下文拦截器
 * 从请求头中解析用户信息，写入 UserContext
 * 
 * <p>支持的请求头：</p>
 * <ul>
 *   <li>X-User-Id: 用户ID</li>
 *   <li>X-User-Name: 用户昵称</li>
 *   <li>X-User-Role: 用户角色(ADMIN/USER/MERCHANT)</li>
 * </ul>
 * 
 * <p>使用方式：在WebMvcConfig中注册此拦截器</p>
 * 
 * @author Backend Architect
 * @since 1.0.0
 */
public class UserContextInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UserContextInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response,@NonNull Object handler) {
        String idStr = request.getHeader("X-User-Id");
        String name = request.getHeader("X-User-Name");
        String roleStr = request.getHeader("X-User-Role");
        
        if (idStr != null && roleStr != null) {
            try {
                UserInfo user = new UserInfo();
                user.setId(Long.parseLong(idStr));
                user.setNickName(name != null ? name : ("user-" + idStr));
                user.setRole(UserRole.valueOf(roleStr));
                UserContext.set(user);
            } catch (Exception e) {
                log.warn("解析用户头信息失败: id={}, role={}", idStr, roleStr, e);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,@NonNull HttpServletResponse response,@NonNull Object handler, Exception ex) {
        UserContext.clear();
    }
}
