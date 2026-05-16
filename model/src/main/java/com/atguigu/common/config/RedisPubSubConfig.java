package com.atguigu.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Redis发布订阅配置类
 * 用于配置Redis消息监听容器，支持多实例缓存同步
 */
@Configuration
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // 使用ThreadPoolTaskExecutor，支持Spring生命周期管理
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(4);
        taskExecutor.setMaxPoolSize(4);
        taskExecutor.setThreadNamePrefix("redis-msg-");
        taskExecutor.setDaemon(true);
        taskExecutor.initialize();
        
        ThreadPoolTaskExecutor subExecutor = new ThreadPoolTaskExecutor();
        subExecutor.setCorePoolSize(2);
        subExecutor.setMaxPoolSize(2);
        subExecutor.setThreadNamePrefix("redis-sub-");
        subExecutor.setDaemon(true);
        subExecutor.initialize();
        
        container.setTaskExecutor(taskExecutor);
        container.setSubscriptionExecutor(subExecutor);
        return container;
    }
}