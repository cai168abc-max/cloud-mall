package com.atguigu.common.exception;

import lombok.Getter;

import java.util.List;

/**
 * 参数校验异常
 * 用于参数验证失败的场景，返回400错误码
 */
@Getter
public class ValidationException extends BusinessException {

    /**
     * -- GETTER --
     *  获取校验错误列表
     *
     */
    private List<String> validationErrors;
    
    /**
     * 默认构造函数，错误码为400
     * @param message 异常信息
     */
    public ValidationException(String message) {
        super(400, message);
    }
    
    /**
     * 带校验错误列表的构造函数
     * @param message 异常信息
     * @param validationErrors 校验错误列表
     */
    public ValidationException(String message, List<String> validationErrors) {
        super(400, message);
        this.validationErrors = validationErrors;
    }

}
