package com.atguigu.order.bean;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户购物车
 */
@Data
public class Cart {
    private Long userId;
    private List<CartItem> items;
    private Integer totalCount;
    private BigDecimal totalAmount;
    
    /**
     * 计算购物车总价
     */
    public void calculateTotal() {
        if (items == null || items.isEmpty()) {
            this.totalCount = 0;
            this.totalAmount = BigDecimal.ZERO;
            return;
        }
        
        this.totalCount = items.stream()
                .filter(CartItem::getChecked)
                .mapToInt(CartItem::getQuantity)
                .sum();
                
        this.totalAmount = items.stream()
                .filter(CartItem::getChecked)
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
