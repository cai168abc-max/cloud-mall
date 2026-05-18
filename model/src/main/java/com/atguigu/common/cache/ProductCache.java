package com.atguigu.common.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCache implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long productId;
    private String name;
    private String description;
    private java.math.BigDecimal price;
    private Integer stock;
    private long expireTime;

    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }
}
