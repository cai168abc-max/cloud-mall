package com.atguigu.gateway.filter;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.crypto.SecretKey;

/**
 * 管理员权限过滤器：确保只有管理员可以访问管理接口
 * 安全特性：
 * 1. 重新验证JWT签名，**完全不信任**请求头中的X-User-Role信息
 * 2. 自动移除请求中可能存在的伪造X-User-*头
 * 3. 路径规范化防止路径遍历绕过
 * 4. OPTIONS预检请求直接放行
 * 5. 详细的安全审计日志
 * 6. JSON转义防止注入攻击
 */
@Component
public class AdminAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);

    // 管理接口路径前缀
    private static final String ADMIN_PATH_PREFIX = "/v1/api/admin/";
    private static final String LEGACY_ADMIN_PATH_PREFIX = "/api/admin/";

    // 需要移除的敏感请求头（防止伪造）
    private static final String[] SENSITIVE_HEADERS = {
            "X-User-Id", "X-User-Name", "X-User-Role", "X-Internal-Request"
    };

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    private SecretKey jwtSigningKey;

    @PostConstruct
    public void init() {
        // 初始化验证：确保JWT密钥配置正确
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("JWT密钥未配置，请设置配置项: security.jwt.secret");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT密钥长度不足，至少需要32个字符，当前长度: " + jwtSecret.length());
        }

        // 提前初始化签名密钥，避免每次验证都重新创建
        this.jwtSigningKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        log.info("AdminAuthFilter初始化完成，管理员权限验证已启用");
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull final ServerWebExchange exchange, @NonNull final GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String originalPath = request.getURI().getPath();
        String normalizedPath = normalizePath(originalPath);
        String requestId = exchange.getRequest().getId();

        // 1. 检查是否为管理接口
        boolean isAdminPath = normalizedPath.startsWith(ADMIN_PATH_PREFIX)
                || normalizedPath.startsWith(LEGACY_ADMIN_PATH_PREFIX);

        if (!isAdminPath) {
            // 非管理接口，直接放行
            return chain.filter(exchange);
        }

        // 2. OPTIONS预检请求直接放行（CORS需要）
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            log.debug("OPTIONS预检请求，跳过管理员权限验证: ID={}, Path={}", requestId, normalizedPath);
            return chain.filter(exchange);
        }

        // 3. 安全加固：移除所有可能伪造的用户信息头
        ServerHttpRequest.Builder requestBuilder = request.mutate();
        for (String header : SENSITIVE_HEADERS) {
            requestBuilder.headers(headers -> headers.remove(header));
        }

        // 4. 从请求中获取并验证token
        String token = resolveToken(request);
        if (token == null) {
            log.warn("管理接口访问未提供token: ID={}, Path={}, IP={}",
                    requestId, normalizedPath, getClientIp(request));
            return forbidden(exchange, "未提供认证信息");
        }

        // 5. 重新验证JWT签名并提取角色（完全不信任请求头）
        JwtVerifyResult result = verifyJwtAndGetRole(token);
        if (!result.success()) {
            // 修复：明确断言errorMessage不为null
            String errorMessage = Objects.requireNonNull(result.errorMessage(), "错误信息不能为空");
            log.warn("管理接口访问JWT验证失败: ID={}, Path={}, 原因: {}",
                    requestId, normalizedPath, errorMessage);
            return forbidden(exchange, errorMessage);
        }

        // 6. 验证管理员角色
        // 修复：明确断言userId和role不为null
        String userId = Objects.requireNonNull(result.userId(), "用户ID不能为空");
        String role = Objects.requireNonNull(result.role(), "用户角色不能为空");

        if (!"ADMIN".equals(role)) {
            log.error("非管理员尝试访问管理接口: ID={}, Path={}, UserId={}, Role={}, IP={}",
                    requestId, normalizedPath, userId, role, getClientIp(request));
            return forbidden(exchange, "仅管理员可以访问此接口");
        }

        // 7. 添加经过验证的用户信息头（安全可靠）
        requestBuilder
                .header("X-User-Id", userId)
                .header("X-User-Role", "ADMIN");

        log.info("管理员访问管理接口: ID={}, Path={}, UserId={}, IP={}",
                requestId, normalizedPath, userId, getClientIp(request));

        // 8. 继续执行过滤器链
        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    /**
     * 规范化URL路径，防止路径遍历绕过认证
     * 例如：/api/../api/admin/ -> /api/admin/
     */
    @NonNull
    private String normalizePath(@NonNull String path) {
        String normalized = path.replaceAll("/+", "/");
        while (normalized.contains("/../")) {
            normalized = normalized.replaceAll("/[^/]+/\\.\\./", "/");
        }
        normalized = normalized.replaceAll("/\\./", "/");
        return normalized;
    }

    /**
     * 获取客户端真实IP地址
     */
    @NonNull
    private String getClientIp(@NonNull ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            // 多个IP的情况，取第一个
            return ip.split(",")[0].trim();
        }
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }
        return Objects.requireNonNull(request.getRemoteAddress()).getAddress().getHostAddress();
    }

    /**
     * 从请求中解析token
     * 修复了"Bearer "后面没有内容的情况
     */
    @Nullable
    private String resolveToken(@NonNull ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            return !token.isEmpty() ? token : null;
        }

        String xToken = request.getHeaders().getFirst("X-Token");
        if (xToken != null) {
            String token = xToken.trim();
            return !token.isEmpty() ? token : null;
        }

        return null;
    }

    /**
     * 验证JWT签名并获取用户信息
     * 区分不同类型的异常，提供更精确的错误信息
     * 修复：移除了多余的null检查，验证逻辑集中在JwtVerifyResult.success()中
     */
    @NonNull
    private JwtVerifyResult verifyJwtAndGetRole(@NonNull String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(jwtSigningKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            // 验证逻辑完全由JwtVerifyResult.success()工厂方法负责
            return JwtVerifyResult.success(userId, role);
        } catch (ExpiredJwtException e) {
            return JwtVerifyResult.failure("认证已过期，请重新登录");
        } catch (MalformedJwtException e) {
            return JwtVerifyResult.failure("认证格式错误");
        } catch (SignatureException e) {
            return JwtVerifyResult.failure("认证签名验证失败");
        } catch (UnsupportedJwtException e) {
            return JwtVerifyResult.failure("不支持的认证格式");
        } catch (IllegalArgumentException e) {
            return JwtVerifyResult.failure("认证参数非法");
        } catch (Exception e) {
            log.error("验证JWT时发生未知错误", e);
            return JwtVerifyResult.failure("认证验证失败");
        }
    }

    /**
     * 返回403禁止访问响应
     * 添加了JSON转义，防止JSON注入攻击
     * 修复：添加了对message参数的null检查
     */
    @NonNull
    private Mono<Void> forbidden(@NonNull ServerWebExchange exchange, @Nullable String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        // 处理null情况
        String safeMessage = message != null ? message : "禁止访问";

        // 对错误信息进行JSON转义，防止注入攻击
        String escapedMessage = safeMessage
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");

        String body = String.format("{\"code\": 403, \"message\": \"%s\"}", escapedMessage);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        // 在AuthTokenFilter(-100)之后执行，确保已经完成了基础认证
        return -90;
    }

    /**
     * JWT验证结果封装类
     */
    private record JwtVerifyResult(
            boolean success,
            @Nullable String userId,
            @Nullable String role,
            @Nullable String errorMessage
    ) {
        @NonNull
        static JwtVerifyResult success(@Nullable String userId, @Nullable String role) {
            // 所有验证逻辑集中在这里
            if (userId == null || userId.trim().isEmpty()) {
                return JwtVerifyResult.failure("JWT缺少用户ID信息");
            }
            if (role == null || role.trim().isEmpty()) {
                return JwtVerifyResult.failure("JWT缺少用户角色信息");
            }
            return new JwtVerifyResult(true, userId, role, null);
        }

        @NonNull
        @SuppressWarnings("ConstantConditions") // 抑制"errorMessage == null始终为false"警告
        static JwtVerifyResult failure(@Nullable String errorMessage) {
            // 保留这个检查以提高代码健壮性
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                return new JwtVerifyResult(false, null, null, "未知错误");
            }
            return new JwtVerifyResult(false, null, null, errorMessage);
        }
    }
}