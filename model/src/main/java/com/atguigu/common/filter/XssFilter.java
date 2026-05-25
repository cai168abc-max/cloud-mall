package com.atguigu.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * XSS攻击防护过滤器
 * 功能说明：
 * 1. 拦截所有HTTP请求
 * 2. 对请求参数进行XSS过滤
 * 3. 防止脚本注入攻击
 * 4. 支持配置化排除URL
 * 5. 智能识别Content-Type，避免过滤二进制数据
 * 使用方式：
 * - 在model模块中作为公共组件
 * - 各微服务引入model模块后自动生效
 * - 通过@Order(1)确保最先执行
 * - 通过配置文件 xss.filter.exclude-urls 自定义排除URL
 * - 通过配置 xss.filter.enabled=false 禁用过滤器
 * 安全特性：
 * - URL路径规范化，防止路径遍历绕过
 * - Content-Type检查，避免过滤文件上传
 * - HTTP方法区分，OPTIONS预检请求直接放行
 * - 完善的异常处理和日志记录
 */
@Component
@Order(1)
public class XssFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(XssFilter.class);

    // 默认排除的URL路径（不需要XSS过滤的路径）
    private static final Set<String> DEFAULT_EXCLUDE_URLS = new HashSet<>(Arrays.asList(
            "/static/", "/css/", "/js/", "/images/", "/favicon.ico",
            // API文档路径
            "/swagger-ui/", "/swagger-resources", "/v3/api-docs", "/doc.html", "/webjars/",
            // 监控与健康检查
            "/actuator/", "/health", "/metrics",
            // 静态资源常见路径
            "/assets/", "/public/", "/webjars/"
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

    // 可配置的排除URL列表
    @Value("${xss.filter.exclude-urls:}")
    private String configuredExcludeUrls;

    // 是否启用XSS过滤
    @Value("${xss.filter.enabled:true}")
    private boolean enabled;

    // 合并后的排除URL集合
    private Set<String> excludeUrls;

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        // 初始化排除URL集合
        excludeUrls = new HashSet<>(DEFAULT_EXCLUDE_URLS);
        
        // 合并配置文件中的排除URL
        if (configuredExcludeUrls != null && !configuredExcludeUrls.trim().isEmpty()) {
            String[] customUrls = configuredExcludeUrls.split(",");
            for (String url : customUrls) {
                String trimmed = url.trim();
                if (!trimmed.isEmpty()) {
                    excludeUrls.add(trimmed);
                }
            }
        }
        
        log.info("XSS过滤器初始化完成, 启用状态: {}, 排除URL数量: {}", enabled, excludeUrls.size());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        // 检查是否启用
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        // 类型安全检查：确保是HTTP请求
        if (!(request instanceof HttpServletRequest httpRequest)) {
            log.debug("非HTTP请求，直接放行");
            chain.doFilter(request, response);
            return;
        }

        String requestURI = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        try {
            // 规范化URL路径，防止路径遍历绕过
            String normalizedUri = normalizeUrl(requestURI);

            // 检查HTTP方法是否需要跳过
            if (SKIP_HTTP_METHODS.contains(method.toUpperCase())) {
                log.debug("HTTP方法 {} 无需XSS过滤, URI: {}", method, normalizedUri);
                chain.doFilter(request, response);
                return;
            }

            // 检查是否为排除的URL
            if (isExcludedUrl(normalizedUri)) {
                log.debug("URL在排除列表中，跳过XSS过滤: {}", normalizedUri);
                chain.doFilter(request, response);
                return;
            }

            // 检查Content-Type，避免过滤文件上传等二进制数据
            String contentType = httpRequest.getContentType();
            if (shouldSkipContentType(contentType)) {
                log.debug("Content-Type {} 无需XSS过滤, URI: {}", contentType, normalizedUri);
                chain.doFilter(request, response);
                return;
            }

            // 记录请求信息（用于安全审计）
            log.debug("XSS过滤处理请求: {} {}", method, normalizedUri);

            // 使用XSS包装器包装请求
            XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(httpRequest);
            chain.doFilter(wrapper, response);

        } catch (Exception e) {
            // 异常处理：记录日志并放行，避免影响业务
            log.error("XSS过滤处理异常, URI: {}, Method: {}, 错误: {}", 
                    requestURI, method, e.getMessage(), e);
            // 降级处理：异常情况下放行原始请求，保证业务可用性
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        log.info("XSS过滤器销毁");
        if (excludeUrls != null) {
            excludeUrls.clear();
        }
    }

    /**
     * 规范化URL路径
     * 处理路径遍历、URL编码等问题
     *
     * @param url 原始URL
     * @return 规范化后的URL
     */
    private String normalizeUrl(final String url) {
        if (url == null || url.isEmpty()) {
            return "/";
        }

        // 解码URL编码字符（处理编码绕过）
        String decoded;
        try {
            // 使用java.net.URLDecoder解码
            decoded = java.net.URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("URL解码失败，使用原始URL: {}", url);
            decoded = url;
        }

        // 规范化路径：移除多余的斜杠和路径遍历
        String normalized = decoded.replaceAll("/+", "/");
        
        // 处理路径遍历（简化处理，实际生产环境建议使用更严格的路径规范化）
        while (normalized.contains("/../")) {
            normalized = normalized.replaceAll("/[^/]+/\\.\\./", "/");
        }
        normalized = normalized.replaceAll("/\\./", "/");

        return normalized;
    }

    /**
     * 检查URL是否在排除列表中
     * 使用startsWith匹配，防止contains绕过
     *
     * @param url 规范化后的URL
     * @return true表示需要排除
     */
    private boolean isExcludedUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        // 使用startsWith精确匹配，防止路径注入绕过
        for (String excludeUrl : excludeUrls) {
            if (url.startsWith(excludeUrl)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查Content-Type是否需要跳过过滤
     *
     * @param contentType 请求的Content-Type
     * @return true表示需要跳过
     */
    private boolean shouldSkipContentType(final String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return false;
        }

        String lowerContentType = contentType.toLowerCase();
        for (String skipType : SKIP_CONTENT_TYPES) {
            if (lowerContentType.startsWith(skipType.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
