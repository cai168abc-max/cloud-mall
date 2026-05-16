package com.atguigu.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 * 
 * <p>线程池参数说明：</p>
 * <ul>
 *   <li>corePoolSize: 核心线程数，建议设置为CPU核心数</li>
 *   <li>maxPoolSize: 最大线程数，建议设置为CPU核心数*2</li>
 *   <li>queueCapacity: 队列容量，建议根据业务峰值调整</li>
 *   <li>keepAliveSeconds: 非核心线程空闲存活时间</li>
 * </ul>
 * 
 * <p>配置项：</p>
 * <ul>
 *   <li>async.executor.core-pool-size: 核心线程数，默认10</li>
 *   <li>async.executor.max-pool-size: 最大线程数，默认20</li>
 *   <li>async.executor.queue-capacity: 队列容量，默认500</li>
 *   <li>async.executor.keep-alive-seconds: 空闲线程存活时间，默认60秒</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 核心线程数：CPU密集型任务建议设置为CPU核心数，IO密集型任务可适当增加
     */
    @Value("${async.executor.core-pool-size:10}")
    private int corePoolSize;

    /**
     * 最大线程数：建议为核心线程数的1.5-2倍
     */
    @Value("${async.executor.max-pool-size:20}")
    private int maxPoolSize;

    /**
     * 队列容量：根据业务峰值和任务执行时间调整
     */
    @Value("${async.executor.queue-capacity:500}")
    private int queueCapacity;

    /**
     * 非核心线程空闲存活时间（秒）
     */
    @Value("${async.executor.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    /**
     * 线程名前缀
     */
    private static final String THREAD_NAME_PREFIX = "async-order-";

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        
        // 拒绝策略：调用者运行，由调用线程执行任务，避免任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 优雅关闭：等待任务完成后再关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        
        return executor;
    }
}
