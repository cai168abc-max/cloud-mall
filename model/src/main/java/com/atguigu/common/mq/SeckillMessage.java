package com.atguigu.common.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long productId;
    private Long userId;
    private Long timestamp;
}
