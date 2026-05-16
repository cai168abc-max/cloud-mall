package com.atguigu.common.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.atguigu.common.bean.UserInfo;

/**
 * 用户上下文工具类，基于 TransmittableThreadLocal 保存从请求头中解析的用户信息
 * 统一的用户上下文管理，供所有微服务共享使用
 * 
 * 使用 TransmittableThreadLocal 替代普通 ThreadLocal，支持：
 * 1. 线程池中的异步任务上下文传递
 * 2. @Async 注解的异步方法上下文传递
 * 3. CompletableFuture 等异步编程场景
 * 
 * 注意：对于使用 ExecutorService 的场景，需要使用 TtlExecutors.getTtlExecutorService() 包装
 */
public class UserContext {
    private static final TransmittableThreadLocal<UserInfo> HOLDER = new TransmittableThreadLocal<>();

    /**
     * 设置当前线程的用户信息
     * @param user 用户信息对象
     */
    public static void set(UserInfo user) {
        HOLDER.set(user);
    }

    /**
     * 获取当前线程的用户信息
     * @return 用户信息对象
     */
    public static UserInfo get() {
        return HOLDER.get();
    }

    /**
     * 清除当前线程的用户信息
     */
    public static void clear() {
        HOLDER.remove();
    }
}
