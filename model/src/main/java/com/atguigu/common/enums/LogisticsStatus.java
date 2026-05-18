package com.atguigu.common.enums;

import lombok.Getter;

/**
 * 物流状态枚举
 */
@Getter
public enum LogisticsStatus {
    /**
     * 待发货
     */
    PENDING("待发货"),
    
    /**
     * 已发货
     */
    SHIPPED("已发货"),
    
    /**
     * 运输中
     */
    IN_TRANSIT("运输中"),
    
    /**
     * 已签收
     */
    DELIVERED("已签收");
    
    private final String description;
    
    LogisticsStatus(String description) {
        this.description = description;
    }

}
