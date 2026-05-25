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
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.crypto.SecretKey;

/**
 * 全局鉴权过滤器：
 * - 从 Authorization: Bearer xxx 或 X-Token 头中获取 token
 * - 仅支持JWT格式token，解析出 userId,userName,role 并写入 X-User-* 头
 * - JWT解析失败时返回401错误（对于需要认证的接口）
 * - 添加HMAC签名的内部请求标识，防止绕过Gateway直接访问服务
 * 安全特性：
 * - 使用HMAC-SHA256签名验证内部请求
 * - 包含时间戳防止重放攻击
 * - 签名内容包含请求路径防止签名复用
 * - 路径规范化防止路径遍历绕过
 * - JSON转义防止注入攻击
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

    private SecretKey jwtSigningKey;

    @PostConstruct
    public void init() {
        // 提前初始化JWT签名密钥，避免每次解析都重新创建
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("JWT密钥未配置，请设置配置项: security.jwt.secret");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT密钥长度不足，至少需要32个字符，当前长度: " + jwtSecret.length());
        }
        if (internalSecret == null || internalSecret.trim().isEmpty()) {
            throw new IllegalStateException("内部请求密钥未配置，请设置配置项: security.internal.secret");
        }
        if (internalSecret.length() < 32) {
            throw new IllegalStateException("内部请求密钥长度不足，至少需要32个字符，当前长度: " + internalSecret.length());
        }

        this.jwtSigningKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        log.info("AuthTokenFilter初始化完成，JWT密钥和内部请求密钥已验证");
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String originalPath = request.getPath().value();
        String normalizedPath = normalizePath(originalPath);

        // 记录请求信息，方便追踪
        String requestId = exchange.getRequest().getId();
        log.debug("处理认证请求: ID={}, Method={}, Path={}",
                requestId, request.getMethod(), normalizedPath);

        // 1. 拒绝通过Gateway访问内部API
        if (isInternalApiPath(normalizedPath)) {
            log.warn("拒绝通过Gateway访问内部API: ID={}, Path={}", requestId, normalizedPath);
            return unauthorized(exchange, "内部接口不允许通过Gateway访问");
        }

        // 2. 检测并处理内部请求（防止循环调用）
        if (isInternalRequest(exchange)) {
            log.warn("检测到内部请求标识，直接放行: ID={}, Path={}", requestId, normalizedPath);
            return chain.filter(addInternalRequestHeader(exchange));
        }

        // 3. 公开接口直接放行
        if (isPublicPath(normalizedPath)) {
            log.debug("公开接口，跳过认证: ID={}, Path={}", requestId, normalizedPath);
            return chain.filter(addInternalRequestHeader(exchange));
        }

        // 4. 提取并验证token
        String token = resolveToken(exchange);
        if (token == null) {
            log.warn("未提供认证token，拒绝访问: ID={}, Path={}", requestId, normalizedPath);
            return unauthorized(exchange, "未提供认证信息");
        }

        // 5. 解析JWT
        JwtParseResult result = parseJwt(token);
        if (result.success()) {
            log.debug("JWT解析成功: ID={}, UserId={}, Role={}",
                    requestId, result.userId(), result.role());
            ServerWebExchange mutatedExchange = addUserHeaders(exchange, result);
            return chain.filter(addInternalRequestHeader(mutatedExchange));
        } else {
            // 修复：明确断言errorMessage不为null
            String errorMessage = Objects.requireNonNull(result.errorMessage(), "错误信息不能为空");
            log.warn("JWT解析失败: ID={}, Path={}, 原因: {}",
                    requestId, normalizedPath, errorMessage);
            return unauthorized(exchange, errorMessage);
        }
    }

    /**
     * 规范化URL路径，防止路径遍历绕过认证
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
     * 检查是否为公开接口路径
     */
    private boolean isPublicPath(@NonNull String path) {
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否为内部API路径
     * 修复了原代码中的数组索引错误（segments[3] -> segments[2]）
     */
    private boolean isInternalApiPath(@NonNull String path) {
        String normalizedPath = path;
        if (normalizedPath.startsWith("/v1/")) {
            normalizedPath = normalizedPath.substring(3);
        }
        if (!normalizedPath.startsWith("/api/")) {
            return false;
        }
        String[] segments = normalizedPath.split("/");
        // 路径格式: /api/internal/xxx -> segments = ["", "api", "internal", "xxx"]
        return segments.length >= 3 && "internal".equals(segments[2]);
    }

    /**
     * 验证请求是否带有有效的内部请求标识
     * 添加了异常处理，防止HMAC验证异常导致请求失败
     */
    private boolean isInternalRequest(@NonNull ServerWebExchange exchange) {
        String internalHeader = exchange.getRequest().getHeaders().getFirst(INTERNAL_REQUEST_HEADER);
        if (internalHeader == null || internalHeader.trim().isEmpty()) {
            return false;
        }

        try {
            String path = exchange.getRequest().getPath().value();
            return HmacSignatureUtil.verifyInternalRequestToken(internalSecret, path, internalHeader);
        } catch (Exception e) {
            log.error("验证内部请求标识时发生异常", e);
            return false;
        }
    }

    /**
     * 添加HMAC签名的内部请求标识头
     * 下游微服务应验证此标识，拒绝没有此标识的直接访问
     */
    @NonNull
    private ServerWebExchange addInternalRequestHeader(@NonNull ServerWebExchange exchange) {
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
     * 修复：添加Objects.requireNonNull明确断言userId和role不为null
     */
    @NonNull
    private ServerWebExchange addUserHeaders(@NonNull ServerWebExchange exchange, @NonNull JwtParseResult result) {
        // 在success情况下，userId和role一定不为null
        String userId = Objects.requireNonNull(result.userId(), "用户ID不能为空");
        String role = Objects.requireNonNull(result.role(), "用户角色不能为空");

        ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-Role", role);

        if (result.nickName() != null && !result.nickName().trim().isEmpty()) {
            builder.header("X-User-Name", result.nickName());
        } else {
            builder.header("X-User-Name", "user-" + userId);
        }

        return exchange.mutate().request(builder.build()).build();
    }

    /**
     * 解析JWT并返回结果
     * 提前初始化了签名密钥，提高性能
     * 修复：移除了多余的null检查，验证逻辑集中在JwtParseResult.success()中
     */
    @NonNull
    private JwtParseResult parseJwt(@NonNull String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(jwtSigningKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String userId = claims.getSubject();
            String nickName = claims.get("nickName", String.class);
            String role = claims.get("role", String.class);

            // 验证逻辑完全由JwtParseResult.success()工厂方法负责
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

    /**
     * 从请求头中提取token
     * 修复了"Bearer "后面没有内容的情况
     */
    @Nullable
    private String resolveToken(@NonNull ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            return !token.isEmpty() ? token : null;
        }

        String xToken = exchange.getRequest().getHeaders().getFirst("X-Token");
        if (xToken != null) {
            String token = xToken.trim();
            return !token.isEmpty() ? token : null;
        }

        return null;
    }

    /**
     * 返回401未授权响应
     * 添加了JSON转义，防止JSON注入攻击
     * 修复：添加了对message参数的null检查
     */
    @NonNull
    private Mono<Void> unauthorized(@NonNull ServerWebExchange exchange, @Nullable String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        // 处理null情况
        String safeMessage = message != null ? message : "未授权访问";

        // 对错误信息进行JSON转义，防止注入攻击
        String escapedMessage = safeMessage
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");

        String body = String.format("{\"code\": 401, \"message\": \"%s\"}", escapedMessage);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        // 在较前位置执行，保证下游服务都能拿到 X-User-* 头
        return -100;
    }

    /**
     * JWT解析结果封装类
     * 添加了参数非null检查
     */
    private record JwtParseResult(
            boolean success,
            @Nullable String userId,
            @Nullable String nickName,
            @Nullable String role,
            @Nullable String errorMessage
    ) {

        @NonNull
        static JwtParseResult success(@Nullable String userId, @Nullable String nickName, @Nullable String role) {
            // 所有验证逻辑集中在这里
            if (userId == null || userId.trim().isEmpty()) {
                return JwtParseResult.failure("JWT缺少用户ID信息");
            }
            if (role == null || role.trim().isEmpty()) {
                return JwtParseResult.failure("JWT缺少用户角色信息");
            }
            return new JwtParseResult(true, userId, nickName, role, null);
        }

        @NonNull
        @SuppressWarnings("ConstantConditions") // 抑制"errorMessage == null始终为false"警告
        static JwtParseResult failure(@Nullable String errorMessage) {
            // 保留这个检查以提高代码健壮性
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                return new JwtParseResult(false, null, null, null, "未知错误");
            }
            return new JwtParseResult(false, null, null, null, errorMessage);
        }
    }
}