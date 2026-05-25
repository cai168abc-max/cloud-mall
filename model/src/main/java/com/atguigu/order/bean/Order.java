package com.atguigu.order.bean;

import com.atguigu.common.enums.OrderStatus;
import com.atguigu.product.bean.Product;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("orders")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long merchantId;
    private String nickName;
    private String address;
    private BigDecimal totalPrice;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private Long couponId;

    @TableField("status")
    private OrderStatus status;

    @TableField("pay_time")
    private LocalDateTime payTime;

    @TableField("ship_time")
    private LocalDateTime shipTime;

    @TableField("complete_time")
    private LocalDateTime completeTime;

    /**
     * 是否已评价：0否 1是
     */
    @TableField("reviewed")
    private Integer reviewed;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<Product> productList;
    
    public Order(final Long id, final Long userId, final Long merchantId, final String nickName, final String address,
                 final BigDecimal totalPrice, final BigDecimal discountAmount, final BigDecimal payAmount,
                 final Long couponId, final OrderStatus status, final LocalDateTime payTime, final LocalDateTime shipTime,
                 final LocalDateTime completeTime, final Integer reviewed, final LocalDateTime createTime,
                 final LocalDateTime updateTime, final List<Product> productList) {
        this.id = id;
        this.userId = userId;
        this.merchantId = merchantId;
        this.nickName = nickName;
        this.address = address;
        this.totalPrice = totalPrice;
        this.discountAmount = discountAmount;
        this.payAmount = payAmount;
        this.couponId = couponId;
        this.status = status;
        this.payTime = payTime;
        this.shipTime = shipTime;
        this.completeTime = completeTime;
        this.reviewed = reviewed;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.productList = productList != null ? new ArrayList<>(productList) : null;
    }
    
    public List<Product> getProductList() {
        return productList != null ? Collections.unmodifiableList(productList) : null;
    }
    
    public void setProductList(final List<Product> productList) {
        this.productList = productList != null ? new ArrayList<>(productList) : null;
    }
    
    public static OrderBuilder builder() {
        return new OrderBuilder();
    }
    
    public static class OrderBuilder {
        private Long id;
        private Long userId;
        private Long merchantId;
        private String nickName;
        private String address;
        private BigDecimal totalPrice;
        private BigDecimal discountAmount;
        private BigDecimal payAmount;
        private Long couponId;
        private OrderStatus status;
        private LocalDateTime payTime;
        private LocalDateTime shipTime;
        private LocalDateTime completeTime;
        private Integer reviewed;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private List<Product> productList;
        
        public OrderBuilder id(final Long id) {
            this.id = id;
            return this;
        }
        
        public OrderBuilder userId(final Long userId) {
            this.userId = userId;
            return this;
        }
        
        public OrderBuilder merchantId(final Long merchantId) {
            this.merchantId = merchantId;
            return this;
        }
        
        public OrderBuilder nickName(final String nickName) {
            this.nickName = nickName;
            return this;
        }
        
        public OrderBuilder address(final String address) {
            this.address = address;
            return this;
        }
        
        public OrderBuilder totalPrice(final BigDecimal totalPrice) {
            this.totalPrice = totalPrice;
            return this;
        }
        
        public OrderBuilder discountAmount(final BigDecimal discountAmount) {
            this.discountAmount = discountAmount;
            return this;
        }
        
        public OrderBuilder payAmount(final BigDecimal payAmount) {
            this.payAmount = payAmount;
            return this;
        }
        
        public OrderBuilder couponId(final Long couponId) {
            this.couponId = couponId;
            return this;
        }
        
        public OrderBuilder status(final OrderStatus status) {
            this.status = status;
            return this;
        }
        
        public OrderBuilder payTime(final LocalDateTime payTime) {
            this.payTime = payTime;
            return this;
        }
        
        public OrderBuilder shipTime(final LocalDateTime shipTime) {
            this.shipTime = shipTime;
            return this;
        }
        
        public OrderBuilder completeTime(final LocalDateTime completeTime) {
            this.completeTime = completeTime;
            return this;
        }
        
        public OrderBuilder reviewed(final Integer reviewed) {
            this.reviewed = reviewed;
            return this;
        }
        
        public OrderBuilder createTime(final LocalDateTime createTime) {
            this.createTime = createTime;
            return this;
        }
        
        public OrderBuilder updateTime(final LocalDateTime updateTime) {
            this.updateTime = updateTime;
            return this;
        }
        
        public OrderBuilder productList(final List<Product> productList) {
            this.productList = productList != null ? new ArrayList<>(productList) : null;
            return this;
        }
        
        public Order build() {
            return new Order(id, userId, merchantId, nickName, address, totalPrice,
                    discountAmount, payAmount, couponId, status, payTime, shipTime,
                    completeTime, reviewed, createTime, updateTime, this.productList);
        }
    }
}
