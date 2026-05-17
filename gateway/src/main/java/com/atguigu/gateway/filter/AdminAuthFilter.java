package com.atguigu.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

/**
 * 管理员权限过滤器：确保只有管理员可以访问管理接口
 * 
 * 安全特性：
 * 1. 重新验证JWT签名，不信任请求头中的角色信息
 * 2. 防止伪造X-User-Role头绕过权限检查
 */
@Component
public class AdminAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);
    
    // 管理接口路径前缀
    private static final String ADMIN_PATH_PREFIX = "/v1/api/admin/";
    private static final String LEGACY_ADMIN_PATH_PREFIX = "/api/admin/";
    
    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        // 检查是否为管理接口
        if (path.startsWith(ADMIN_PATH_PREFIX) || path.startsWith(LEGACY_ADMIN_PATH_PREFIX)) {
            // 从请求中获取token
            String token = resolveToken(request);
            
            if (token == null) {
                log.warn("管理接口访问未提供token: {}", path);
                return forbidden(exchange, "未提供认证信息");
            }
            
            // 重新验证JWT签名并提取角色
            String role = verifyJwtAndGetRole(token);
            
            if (role == null) {
                log.warn("管理接口访问JWT验证失败: {}", path);
                return forbidden(exchange, "认证验证失败");
            }
            
            if (!"ADMIN".equals(role)) {
                log.warn("非管理员尝试访问管理接口: {}, 角色: {}", path, role);
                return forbidden(exchange, "仅管理员可以访问此接口");
            }
            
            log.debug("管理员访问管理接口: {}", path);
        }
        
        // 允许继续访问
        return chain.filter(exchange);
    }
    
    /**
     * 从请求中解析token
     */
    private String resolveToken(ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return request.getHeaders().getFirst("X-Token");
    }
    
    /**
     * 验证JWT签名并获取角色
     * @param token JWT token
     * @return 角色字符串，验证失败返回null
     */
    private String verifyJwtAndGetRole(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            // 验证必要字段存在
            if (claims.getSubject() == null) {
                log.warn("JWT缺少subject");
                return null;
            }
            
            String role = claims.get("role", String.class);
            if (role == null) {
                log.warn("JWT缺少role声明");
                return null;
            }
            
            return role;
        } catch (Exception e) {
            log.warn("JWT验证失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 返回403禁止访问响应
     */
    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = String.format("{\"code\": 403, \"message\": \"%s\"}", message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    @Override
    public int getOrder() {
        // 在AuthTokenFilter之后执行，确保已经解析了用户角色
        return -90;
    }
}
