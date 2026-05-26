package com.atguigu.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@MapperScan("com.atguigu.product.mapper")
@ComponentScan(basePackages = {"com.atguigu.product", "com.atguigu.common"})
@SpringBootApplication
public class ProductMainApplication {

    private ProductMainApplication() {
        // utility class - do not instantiate
    }

    public static void main(String[] args) {
        SpringApplication.run(ProductMainApplication.class, args);
    }
}
