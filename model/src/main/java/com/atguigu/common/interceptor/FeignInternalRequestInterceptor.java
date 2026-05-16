package com.atguigu.common.interceptor;

import com.atguigu.common.utils.HmacSignatureUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 为 Feign 出站请求生成与目标路径一致的 {@code X-Internal-Request} 头，
 * 以便通过各微服务 {@link InternalRequestInterceptor} 的校验（与 Gateway 使用同一密钥与算法）。
 */
@Component
public class FeignInternalRequestInterceptor implements RequestInterceptor {

    @Value("${security.internal.secret}")
    private String internalSecret;

    @Override
    public void apply(RequestTemplate template) {
        if (internalSecret == null || internalSecret.isBlank()) {
            return;
        }
        String path = template.path();
        if (path == null || path.isEmpty()) {
            String url = template.url();
            if (url != null && !url.isEmpty()) {
                int q = url.indexOf('?');
                path = q > 0 ? url.substring(0, q) : url;
            }
        }
        if (path == null || path.isEmpty()) {
            return;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String token = HmacSignatureUtil.generateInternalRequestToken(internalSecret, path);
        template.header("X-Internal-Request", token);
    }
}
