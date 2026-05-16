package com.atguigu.common.exception;

/**
 * 服务不可用异常
 * 用于表示依赖服务不可用或超时的场景
 */
public class ServiceUnavailableException extends BusinessException {
    
    /**
     * 构造函数，错误码固定为503
     * @param message 异常信息
     */
    public ServiceUnavailableException(String message) {
        super(503, message);
    }
    
    /**
     * 构造函数，支持传入原因异常
     * @param message 异常信息
     * @param cause 原因异常
     */
    public ServiceUnavailableException(String message, Throwable cause) {
        super(503, message);
        initCause(cause);
    }
}
