package com.atguigu.gateway.filter;

import com.atguigu.common.utils.HmacSignatureUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.crypto.SecretKey;

/**
 * 全局鉴权过滤器：
 * - 从 Authorization: Bearer xxx 或 X-Token 头中获取 token
 * - 仅支持JWT格式token，解析出 userId,userName,role 并写入 X-User-* 头
 * - JWT解析失败时返回401错误（对于需要认证的接口）
 * - 添加HMAC签名的内部请求标识，防止绕过Gateway直接访问服务
 * 
 * 安全特性：
 * - 使用HMAC-SHA256签名验证内部请求
 * - 包含时间戳防止重放攻击
 * - 签名内容包含请求路径防止签名复用
 */
@Component
public class AuthTokenFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenFilter.class);

    // 内部请求标识常量
    private static final String INTERNAL_REQUEST_HEADER = "X-Internal-Request";
    
    /**
     * 公开接口路径（不需要认证）
     * 包括：登录、注册、密码重置、验证码、健康检查、Swagger等
     */
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/user/login",
            "/api/user/register",
            "/api/user/registerMerchant",
            "/api/user/resetPassword",
            "/api/user/sendVerificationCode",
            "/v1/api/user/login",
            "/v1/api/user/register",
            "/v1/api/user/registerMerchant",
            "/v1/api/user/resetPassword",
            "/v1/api/user/sendVerificationCode",
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui",
            "/v3/api-docs",
            "/webjars/"
    );

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.internal.secret}")
    private String internalSecret;

    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("JWT密钥未配置，请设置环境变量JWT_SECRET或配置security.jwt.secret");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT密钥长度不足，至少需要32个字符");
        }
        if (internalSecret == null || internalSecret.trim().isEmpty()) {
            throw new IllegalStateException("内部请求密钥未配置，请设置环境变量INTERNAL_SECRET或配置security.internal.secret");
        }
        if (internalSecret.length() < 32) {
            throw new IllegalStateException("内部请求密钥长度不足，至少需要32个字符");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        if (isInternalApiPath(path)) {
            log.warn("拒绝通过Gateway访问内部API: {}", path);
            return unauthorized(exchange, "内部接口不允许通过Gateway访问");
        }
        
        if (isInternalRequest(exchange)) {
            log.warn("检测到内部请求标识，可能存在循环调用风险，路径: {}", path);
            // 内部请求直接放行，但记录日志用于审计
            return chain.filter(addInternalRequestHeader(exchange));
        }
        
        // 检查是否为公开接口
        if (isPublicPath(path)) {
            log.debug("公开接口，跳过认证: {}", path);
            return chain.filter(addInternalRequestHeader(exchange));
        }
        
        String token = resolveToken(exchange);
        
        // 需要认证的接口，但没有提供token
        if (token == null) {
            log.warn("未提供认证token，拒绝访问: {}", path);
            return unauthorized(exchange, "未提供认证信息");
        }
        
        // 尝试解析JWT
        JwtParseResult result = parseJwt(token);
        if (result.isSuccess()) {
            // JWT解析成功，添加用户信息头
            ServerWebExchange mutatedExchange = addUserHeaders(exchange, result);
            return chain.filter(addInternalRequestHeader(mutatedExchange));
        } else {
            // JWT解析失败，返回401错误
            log.warn("JWT解析失败，拒绝访问: {}, 原因: {}", path, result.getErrorMessage());
            return unauthorized(exchange, result.getErrorMessage());
        }
    }
    
    /**
     * 检查是否为公开接口路径
     */
    private boolean isPublicPath(String path) {
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isInternalApiPath(String path) {
        String normalizedPath = path;
        if (normalizedPath.startsWith("/v1/")) {
            normalizedPath = normalizedPath.substring(3);
        }
        if (!normalizedPath.startsWith("/api/")) {
            return false;
        }
        String[] segments = normalizedPath.split("/");
        return segments.length >= 4 && "internal".equals(segments[3]);
    }
    
    /**
     * 验证请求是否带有有效的内部请求标识
     * 用于检测内部服务之间的调用，防止循环请求
     */
    private boolean isInternalRequest(ServerWebExchange exchange) {
        String internalHeader = exchange.getRequest().getHeaders().getFirst(INTERNAL_REQUEST_HEADER);
        if (internalHeader == null) {
            return false;
        }
        
        String path = exchange.getRequest().getPath().value();
        return HmacSignatureUtil.verifyInternalRequestToken(internalSecret, path, internalHeader);
    }
    
    /**
     * 添加HMAC签名的内部请求标识头
     * 下游微服务应验证此标识，拒绝没有此标识的直接访问
     */
    private ServerWebExchange addInternalRequestHeader(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        String token = HmacSignatureUtil.generateInternalRequestToken(internalSecret, path);
        
        ServerHttpRequest newRequest = exchange.getRequest()
                .mutate()
                .header(INTERNAL_REQUEST_HEADER, token)
                .build();
        return exchange.mutate().request(newRequest).build();
    }
    
    /**
     * 添加用户信息头
     */
    private ServerWebExchange addUserHeaders(ServerWebExchange exchange, JwtParseResult result) {
        ServerHttpRequest newRequest = exchange.getRequest()
                .mutate()
                .header("X-User-Id", result.getUserId())
                .header("X-User-Name", result.getNickName() != null ? result.getNickName() : ("user-" + result.getUserId()))
                .header("X-User-Role", result.getRole())
                .build();
        return exchange.mutate().request(newRequest).build();
    }

    /**
     * 解析JWT并返回结果
     */
    private JwtParseResult parseJwt(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            String userId = claims.getSubject();
            String nickName = claims.get("nickName", String.class);
            String role = claims.get("role", String.class);
            
            if (userId == null || role == null) {
                return JwtParseResult.failure("JWT缺少必要的用户信息");
            }
            
            return JwtParseResult.success(userId, nickName, role);
        } catch (ExpiredJwtException e) {
            return JwtParseResult.failure("认证已过期，请重新登录");
        } catch (MalformedJwtException e) {
            return JwtParseResult.failure("认证格式错误");
        } catch (SignatureException e) {
            return JwtParseResult.failure("认证签名验证失败");
        } catch (UnsupportedJwtException e) {
            return JwtParseResult.failure("不支持的认证格式");
        } catch (IllegalArgumentException e) {
            return JwtParseResult.failure("认证参数非法");
        } catch (Exception e) {
            log.error("解析JWT时发生未知错误", e);
            return JwtParseResult.failure("认证解析失败");
        }
    }

    private String resolveToken(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return exchange.getRequest().getHeaders().getFirst("X-Token");
    }
    
    /**
     * 返回401未授权响应
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = String.format("{\"code\": 401, \"message\": \"%s\"}", message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    @Override
    public int getOrder() {
        // 在较前位置执行，保证下游服务都能拿到 X-User-* 头
        return -100;
    }
    
    /**
     * JWT解析结果封装类
     */
    private static class JwtParseResult {
        private final boolean success;
        private final String userId;
        private final String nickName;
        private final String role;
        private final String errorMessage;
        
        private JwtParseResult(boolean success, String userId, String nickName, String role, String errorMessage) {
            this.success = success;
            this.userId = userId;
            this.nickName = nickName;
            this.role = role;
            this.errorMessage = errorMessage;
        }
        
        static JwtParseResult success(String userId, String nickName, String role) {
            return new JwtParseResult(true, userId, nickName, role, null);
        }
        
        static JwtParseResult failure(String errorMessage) {
            return new JwtParseResult(false, null, null, null, errorMessage);
        }
        
        boolean isSuccess() { return success; }
        String getUserId() { return userId; }
        String getNickName() { return nickName; }
        String getRole() { return role; }
        String getErrorMessage() { return errorMessage; }
    }
}


