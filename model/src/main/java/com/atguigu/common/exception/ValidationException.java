package com.atguigu.common.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 参数校验异常
 * 用于参数验证失败的场景，返回400错误码
 */
public class ValidationException extends BusinessException {

    /**
     * 校验错误列表
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
        this.validationErrors = validationErrors != null ? new ArrayList<>(validationErrors) : null;
    }
    
    /**
     * 获取校验错误列表（返回不可变副本）
     * @return 校验错误列表
     */
    public List<String> getValidationErrors() {
        return validationErrors != null ? Collections.unmodifiableList(validationErrors) : null;
    }

}
