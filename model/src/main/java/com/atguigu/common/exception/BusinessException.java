package com.atguigu.common.exception;

import lombok.Getter;

/**
 * 自定义业务异常
 * 用于业务逻辑中抛出的可预期异常
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * -- GETTER --
     *  获取错误码
     *
     */
    private final int code;
    
    /**
     * 默认构造函数，错误码为400
     * @param message 异常信息
     */
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }
    
    /**
     * 自定义错误码构造函数
     * @param code 错误码
     * @param message 异常信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

}
