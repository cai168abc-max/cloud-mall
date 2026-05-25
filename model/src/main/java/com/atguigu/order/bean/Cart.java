package com.atguigu.order.bean;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@ToString
public class Cart {
    private Long userId;
    private List<CartItem> items = new ArrayList<>();
    private Integer totalCount;
    private BigDecimal totalAmount;
    
    public Cart() {
        this.items = new ArrayList<>();
    }
    
    public void setItems(List<CartItem> items) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }
    
    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }
    
    public List<CartItem> getItemsInternal() {
        return items;
    }
    
    public void calculateTotal() {
        this.totalCount = items.stream()
                .filter(Objects::nonNull)
                .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                .filter(item -> item.getQuantity() != null && item.getQuantity() > 0)
                .mapToInt(CartItem::getQuantity)
                .sum();
                
        this.totalAmount = items.stream()
                .filter(Objects::nonNull)
                .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                .filter(item -> item.getPrice() != null && item.getQuantity() != null && item.getQuantity() > 0)
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cart cart = (Cart) o;
        return Objects.equals(userId, cart.userId) && 
               items.equals(cart.items) &&
               Objects.equals(totalCount, cart.totalCount) &&
               Objects.equals(totalAmount, cart.totalAmount);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(userId, items, totalCount, totalAmount);
    }
}