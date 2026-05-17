package com.atguigu.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import redis.embedded.RedisServer;

public class EmbeddedRedisInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedRedisInitializer.class);

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        try {
            RedisServer redisServer = RedisServer.builder()
                    .port(6379)
                    .setting("maxmemory 128M")
                    .setting("daemonize no")
                    .build();
            redisServer.start();
            log.info("嵌入式Redis启动成功，端口: 6379");

            context.addApplicationListener((ApplicationListener<ContextClosedEvent>) event -> {
                if (redisServer.isActive()) {
                    redisServer.stop();
                    log.info("嵌入式Redis已停止");
                }
            });
        } catch (Exception e) {
            log.error("嵌入式Redis启动失败", e);
            throw new RuntimeException("嵌入式Redis启动失败", e);
        }
    }
}
