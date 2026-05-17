package com.atguigu.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * WebFlux版本的XSS攻击防护过滤器
 * 
 * 功能说明：
 * 1. 拦截所有HTTP请求
 * 2. 对请求参数和请求体进行XSS过滤
 * 3. 防止脚本注入攻击
 * 4. 支持配置化排除URL
 * 
 * 安全特性：
 * - URL路径规范化，防止路径遍历绕过
 * - Content-Type检查，避免过滤文件上传
 * - HTTP方法区分，OPTIONS预检请求直接放行
 * - 完善的异常处理和日志记录
 * 
 * 注意：Gateway使用WebFlux，Servlet XSS过滤器不生效
 */
@Component
public class XssWebFluxFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(XssWebFluxFilter.class);

    // 默认排除的URL路径（不需要XSS过滤的路径）
    private static final Set<String> DEFAULT_EXCLUDE_URLS = new HashSet<>(Arrays.asList(
            "/static/", "/css/", "/js/", "/images/", "/favicon.ico",
            "/swagger-ui/", "/swagger-resources", "/v3/api-docs", "/doc.html", "/webjars/",
            "/actuator/", "/health", "/metrics",
            "/assets/", "/public/"
    ));

    // 不需要过滤的Content-Type前缀
    private static final Set<String> SKIP_CONTENT_TYPES = new HashSet<>(Arrays.asList(
            "multipart/form-data",
            "application/octet-stream",
            "application/pdf",
            "image/",
            "video/",
            "audio/"
    ));

    // 不需要过滤的HTTP方法
    private static final Set<String> SKIP_HTTP_METHODS = new HashSet<>(Arrays.asList(
            "OPTIONS", "HEAD"
    ));

    // XSS过滤正则表达式
    private static final Pattern[] XSS_PATTERNS = {
            Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<iframe[^>]*>.*?</iframe>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<object[^>]*>.*?</object>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("data\\s*:", Pattern.CASE_INSENSITIVE)
    };

    @Value("${xss.filter.exclude-urls:}")
    private String configuredExcludeUrls;

    @Value("${xss.filter.enabled:true}")
    private boolean enabled;

    // 合并后的排除URL集合
    private Set<String> excludeUrls;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 检查是否启用
        if (!enabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String requestURI = request.getURI().getPath();
        String method = request.getMethod().name();

        try {
            // 规范化URL路径
            String normalizedUri = normalizeUrl(requestURI);

            // 检查HTTP方法是否需要跳过
            if (SKIP_HTTP_METHODS.contains(method.toUpperCase())) {
                log.debug("HTTP方法 {} 无需XSS过滤, URI: {}", method, normalizedUri);
                return chain.filter(exchange);
            }

            // 检查是否为排除的URL
            if (isExcludedUrl(normalizedUri)) {
                log.debug("URL在排除列表中，跳过XSS过滤: {}", normalizedUri);
                return chain.filter(exchange);
            }

            // 检查Content-Type
            MediaType contentType = request.getHeaders().getContentType();
            if (shouldSkipContentType(contentType)) {
                log.debug("Content-Type {} 无需XSS过滤, URI: {}", contentType, normalizedUri);
                return chain.filter(exchange);
            }

            log.debug("XSS过滤处理请求: {} {}", method, normalizedUri);

            // 对于POST/PUT/PATCH请求，过滤请求体
            if (contentType != null && contentType.includes(MediaType.APPLICATION_JSON)) {
                return filterRequestBody(exchange, chain);
            }

            // 对于其他请求，过滤查询参数
            return filterQueryParams(exchange, chain);

        } catch (Exception e) {
            log.error("XSS过滤处理异常, URI: {}, Method: {}, 错误: {}", 
                    requestURI, method, e.getMessage(), e);
            return chain.filter(exchange);
        }
    }

    /**
     * 过滤请求体
     */
    private Mono<Void> filterRequestBody(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        return DataBufferUtils.join(request.getBody())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    
                    String body = new String(bytes, StandardCharsets.UTF_8);
                    String filteredBody = stripXss(body);
                    
                    byte[] filteredBytes = filteredBody.getBytes(StandardCharsets.UTF_8);
                    DataBuffer newBuffer = new DefaultDataBufferFactory().wrap(filteredBytes);
                    
                    ServerHttpRequest newRequest = new ServerHttpRequestDecorator(request) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.just(newBuffer);
                        }
                        
                        @Override
                        public org.springframework.http.HttpHeaders getHeaders() {
                            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                            headers.putAll(super.getHeaders());
                            headers.setContentLength(filteredBytes.length);
                            return headers;
                        }
                    };
                    
                    return chain.filter(exchange.mutate().request(newRequest).build());
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    /**
     * 过滤查询参数
     */
    private Mono<Void> filterQueryParams(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String query = request.getURI().getQuery();
        
        if (query != null && !query.isEmpty()) {
            String filteredQuery = stripXss(query);
            if (!query.equals(filteredQuery)) {
                ServerHttpRequest newRequest = request.mutate()
                        .uri(request.getURI().resolve("?" + filteredQuery))
                        .build();
                return chain.filter(exchange.mutate().request(newRequest).build());
            }
        }
        
        return chain.filter(exchange);
    }

    /**
     * XSS过滤
     */
    private String stripXss(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        String result = value;
        for (Pattern pattern : XSS_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }
        
        // HTML实体编码
        result = htmlEncode(result);
        
        return result;
    }

    /**
     * HTML实体编码
     */
    private String htmlEncode(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    /**
     * 规范化URL路径
     */
    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "/";
        }

        String decoded = url;
        try {
            decoded = java.net.URLDecoder.decode(url, "UTF-8");
        } catch (Exception e) {
            log.warn("URL解码失败，使用原始URL: {}", url);
        }

        String normalized = decoded.replaceAll("/+", "/");
        while (normalized.contains("/../")) {
            normalized = normalized.replaceAll("/[^/]+/\\.\\./", "/");
        }
        normalized = normalized.replaceAll("/\\./", "/");

        return normalized;
    }

    /**
     * 检查URL是否在排除列表中
     */
    private boolean isExcludedUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        // 延迟初始化排除URL集合
        if (excludeUrls == null) {
            excludeUrls = new HashSet<>(DEFAULT_EXCLUDE_URLS);
            if (configuredExcludeUrls != null && !configuredExcludeUrls.trim().isEmpty()) {
                String[] customUrls = configuredExcludeUrls.split(",");
                for (String excludeUrl : customUrls) {
                    String trimmed = excludeUrl.trim();
                    if (!trimmed.isEmpty()) {
                        excludeUrls.add(trimmed);
                    }
                }
            }
        }

        for (String excludeUrl : excludeUrls) {
            if (url.startsWith(excludeUrl)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查Content-Type是否需要跳过过滤
     */
    private boolean shouldSkipContentType(MediaType contentType) {
        if (contentType == null) {
            return false;
        }

        String contentTypeStr = contentType.toString().toLowerCase();
        for (String skipType : SKIP_CONTENT_TYPES) {
            if (contentTypeStr.startsWith(skipType.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        // 在AuthTokenFilter之后执行
        return -40;
    }
}
