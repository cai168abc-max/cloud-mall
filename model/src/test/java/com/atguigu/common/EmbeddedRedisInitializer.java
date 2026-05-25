package com.atguigu.common;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class EmbeddedRedisInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedRedisInitializer.class);

    private static final int REDIS_PORT = 6379;
    private static final int REDIS_CONNECT_TIMEOUT_MS = 2000;

    @Override
    public void initialize(@NotNull final ConfigurableApplicationContext context) {
        // 先检测 Redis 是否已经可用（CI 环境通常通过 Docker 提供真实的 Redis 服务）
        if (isRedisAvailable(REDIS_PORT)) {
            log.info("检测到 Redis 已在端口 {} 运行，跳过嵌入式 Redis 启动", REDIS_PORT);
            return;
        }

        try {
            final RedisServer redisServer = RedisServer.builder()
                    .port(REDIS_PORT)
                    .setting("maxmemory 128M")
                    .setting("daemonize no")
                    .build();
            redisServer.start();
            log.info("嵌入式Redis启动成功，端口: {}", REDIS_PORT);

            context.addApplicationListener((ApplicationListener<ContextClosedEvent>) event -> {
                if (redisServer.isActive()) {
                    redisServer.stop();
                    log.info("嵌入式Redis已停止");
                }
            });
        } catch (final Exception e) {
            log.error("嵌入式Redis启动失败", e);
            throw new RuntimeException("嵌入式Redis启动失败", e);
        }
    }

    /**
     * 检测指定端口的 Redis 是否已可用
     * 通过尝试 TCP 连接来判断 Redis 服务是否已在运行
     */
    private static boolean isRedisAvailable(final int port) {
        try (final Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), REDIS_CONNECT_TIMEOUT_MS);
            log.debug("成功连接到端口 {}, Redis 已可用", port);
            return true;
        } catch (final IOException e) {
            log.debug("端口 {} 无法连接，Redis 尚未启动", port);
            return false;
        }
    }
}
