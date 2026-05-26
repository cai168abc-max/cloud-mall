package com.atguigu.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@EnableRetry
@MapperScan("com.atguigu.order.mapper")
@ComponentScan(basePackages = {"com.atguigu.order", "com.atguigu.common"})
@SpringBootApplication
public class OrderMainApplication {
    private OrderMainApplication() { }
    public static void main(final String[] args) {
        SpringApplication.run(OrderMainApplication.class, args);
    }
}
