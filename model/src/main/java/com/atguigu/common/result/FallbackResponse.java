package com.atguigu.common.result;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一降级响应格式
 * 
 * <p>用于服务降级时返回标准化的响应结构，包含降级来源、降级原因等信息</p>
 * 
 * <p>降级策略：</p>
 * <ul>
 *   <li>优先级1：从Redis缓存获取数据</li>
 *   <li>优先级2：返回默认值</li>
 *   <li>优先级3：返回友好提示</li>
 * </ul>
 */
@Data
public class FallbackResponse<T> implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * 响应状态码
     */
    private Integer code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 降级来源：cache-缓存, default-默认值, error-错误
     */
    private String source;
    
    /**
     * 降级原因
     */
    private String reason;
    
    /**
     * 服务名称
     */
    private String serviceName;
    
    /**
     * 时间戳
     */
    private String timestamp;
    
    /**
     * 是否为降级响应
     */
    private Boolean degraded = true;
    
    public FallbackResponse() {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    /**
     * 创建缓存降级响应
     */
    public static <T> FallbackResponse<T> fromCache(final T data, final String serviceName) {
        FallbackResponse<T> response = new FallbackResponse<>();
        response.setCode(200);
        response.setMessage("服务降级：返回缓存数据");
        response.setData(data);
        response.setSource("cache");
        response.setReason("远程服务不可用，使用缓存数据");
        response.setServiceName(serviceName);
        return response;
    }
    
    /**
     * 创建默认值降级响应
     */
    public static <T> FallbackResponse<T> fromDefault(T data, String serviceName, String reason) {
        FallbackResponse<T> response = new FallbackResponse<>();
        response.setCode(200);
        response.setMessage("服务降级：返回默认数据");
        response.setData(data);
        response.setSource("default");
        response.setReason(reason);
        response.setServiceName(serviceName);
        return response;
    }
    
    /**
     * 创建错误降级响应
     */
    public static <T> FallbackResponse<T> fromError(final Integer code, final String message, final String serviceName, final String reason) {
        FallbackResponse<T> response = new FallbackResponse<>();
        response.setCode(code);
        response.setMessage(message);
        response.setSource("error");
        response.setReason(reason);
        response.setServiceName(serviceName);
        return response;
    }
    
    /**
     * 判断是否来自缓存
     */
    public boolean isFromCache() {
        return "cache".equals(this.source);
    }
    
    /**
     * 判断是否为默认值
     */
    public boolean isFromDefault() {
        return "default".equals(this.source);
    }
    
    /**
     * 判断是否为错误
     */
    public boolean isFromError() {
        return "error".equals(this.source);
    }
}
