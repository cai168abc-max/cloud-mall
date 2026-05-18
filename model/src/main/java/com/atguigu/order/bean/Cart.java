package com.atguigu.order.bean;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class Cart {
    private Long userId;
    private List<CartItem> items = new ArrayList<>();
    private Integer totalCount;
    private BigDecimal totalAmount;
    
    public Cart() {
        this.items = new ArrayList<>();
    }
    
    public void setItems(List<CartItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
    
    public List<CartItem> getItems() {
        if (items == null) {
            items = new ArrayList<>();
        }
        return items;
    }
    
    public void calculateTotal() {
        List<CartItem> safeItems = getItems();
        
        if (safeItems.isEmpty()) {
            this.totalCount = 0;
            this.totalAmount = BigDecimal.ZERO;
            return;
        }
        
        this.totalCount = safeItems.stream()
                .filter(Objects::nonNull)
                .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                .filter(item -> item.getQuantity() != null && item.getQuantity() > 0)
                .mapToInt(CartItem::getQuantity)
                .sum();
                
        this.totalAmount = safeItems.stream()
                .filter(Objects::nonNull)
                .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                .filter(item -> item.getPrice() != null && item.getQuantity() != null && item.getQuantity() > 0)
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}