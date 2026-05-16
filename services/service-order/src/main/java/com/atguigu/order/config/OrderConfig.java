package com.atguigu.order.config;
import feign.Logger;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderConfig {
    @Bean
    Logger.Level firstLoggerLevel() {
        return Logger.Level.FULL;
    }
//    @Bean
    Retryer retryer() {
        return new Retryer.Default();
    }
}
