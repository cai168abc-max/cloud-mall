# CloudTry 架构设计文档

## 1. 系统概述

CloudTry 是一个基于 Spring Cloud Alibaba 构建的微服务电商平台，采用领域驱动设计（DDD）理念，实现了用户管理、商品管理、订单管理等核心电商功能。

### 1.1 设计目标

- **高可用**：服务熔断、限流、降级机制
- **高性能**：多级缓存、异步处理、消息队列削峰
- **可扩展**：微服务架构，支持水平扩展
- **安全性**：JWT认证、XSS过滤、敏感数据脱敏

### 1.2 技术选型

| 层次 | 技术栈 | 版本 |
|------|--------|------|
| 基础框架 | Spring Boot | 3.3.4 |
| 微服务框架 | Spring Cloud Alibaba | 2023.0.3.2 |
| 服务注册/配置中心 | Nacos | 2.x |
| 流量控制 | Sentinel | 1.8.8 |
| 分布式事务 | Seata | 2.5.0 |
| 消息队列 | RocketMQ | 2.3.5 |
| 缓存 | Redis + Redisson | 6.x/7.x |
| 数据库 | MySQL | 8.x |
| ORM框架 | MyBatis-Plus | 3.5.5 |

## 2. 系统架构

### 2.1 整体架构图

```
                                    +------------------+
                                    |    客户端请求    |
                                    +--------+---------+
                                             |
                                             v
                                    +------------------+
                                    |   API Gateway    |
                                    |   (端口: 80)     |
                                    +--------+---------+
                                             |
                    +------------------------+------------------------+
                    |                        |                        |
                    v                        v                        v
           +----------------+       +----------------+       +----------------+
           |  service-user  |       |service-product |       | service-order  |
           |   (端口:7000)  |       |  (端口:9000)   |       |  (端口:8000)   |
           +-------+--------+       +-------+--------+       +-------+--------+
                   |                        |                        |
                   +------------------------+------------------------+
                                            |
                    +------------------------+------------------------+
                    |                        |                        |
                    v                        v                        v
           +----------------+       +----------------+       +----------------+
           |     Nacos      |       |    Sentinel    |       |     Seata      |
           |   (端口:8848)  |       | (Dashboard)    |       |  (端口:8091)   |
           +----------------+       +----------------+       +----------------+
                    |
                    v
           +----------------+
           |    RocketMQ    |
           | (端口:9876)    |
           +----------------+
                    |
                    v
           +----------------+
           |     Redis      |
           |  (端口:6379)   |
           +----------------+
                    |
                    v
           +----------------+
           |     MySQL      |
           |  (端口:3307)   |
           +----------------+
```

### 2.2 服务划分

| 服务名称 | 端口 | 数据库 | 职责 |
|----------|------|--------|------|
| gateway | 80 | - | API网关、路由、认证 |
| service-user | 7000 | cloudtry_user | 用户注册、登录、认证 |
| service-product | 9000 | cloudtry_product | 商品管理、库存管理 |
| service-order | 8000 | cloudtry_order | 订单管理、购物车、秒杀 |

## 3. 核心模块设计

### 3.1 公共模块 (model)

```
model/
├── bean/                    # 数据模型
│   ├── UserAccount.java     # 用户账户
│   ├── UserInfo.java        # 用户信息
│   └── UserAddress.java     # 用户地址
├── cache/                   # 缓存服务
│   ├── CacheService.java    # 缓存服务接口
│   ├── BloomFilterService.java  # 布隆过滤器
│   └── MultiLevelCacheService.java  # 多级缓存
├── exception/               # 异常定义
│   ├── BusinessException.java
│   ├── ValidationException.java
│   ├── ResourceNotFoundException.java
│   ├── ForbiddenException.java
│   └── ServiceUnavailableException.java
├── result/                  # 统一返回结果
│   └── R.java
├── utils/                   # 工具类
│   ├── PasswordUtil.java
│   ├── JwtKeyGenerator.java
│   └── SensitiveDataMasker.java
└── service/                 # 公共服务
    ├── IdempotencyService.java
    ├── RateLimitService.java
    └── VerificationCodeService.java
```

### 3.2 异常处理体系

```
BusinessException (基类)
├── ValidationException (400) - 参数校验异常
├── ResourceNotFoundException (404) - 资源不存在
├── ForbiddenException (403) - 权限不足
└── ServiceUnavailableException (503) - 服务不可用
```

### 3.3 配置管理

配置文件层次结构：
```
bootstrap.yml (最先加载)
    └── 配置Nacos连接信息

application.yml (主配置)
    ├── 引用Nacos共享配置
    └── 服务特定配置

application-{profile}.yml (环境配置)
    ├── application-dev.yml (开发环境)
    ├── application-test.yml (测试环境)
    └── application-prod.yml (生产环境)
```

## 4. 分布式事务设计

### 4.1 Seata AT模式

项目使用 Seata AT 模式处理分布式事务：

```java
@GlobalTransactional(name = "create-order", timeoutMills = 60000, rollbackFor = Exception.class)
public Order createOrder(Long productId, Long userId) {
    // 1. 查询商品信息（调用商品服务）
    // 2. 扣减库存（调用商品服务）
    // 3. 创建订单（本地事务）
    // 4. 发送消息通知
}
```

### 4.2 事务隔离级别

- 全局事务超时时间：60秒
- 本地事务隔离级别：READ_COMMITTED
- 回滚日志表：undo_log

## 5. 高并发设计

### 5.1 多级缓存

```
请求 -> 本地缓存(Caffeine) -> Redis缓存 -> 数据库
```

### 5.2 限流策略

| 资源 | 限流类型 | 阈值 |
|------|----------|------|
| createOrder | QPS限流 | 100 |
| seckill | 热点参数限流 | 根据商品ID |
| getProduct | QPS限流 | 500 |

### 5.3 熔断降级

- 错误比例阈值：50%
- 熔断时长：10秒
- 最小请求数：5

## 6. 安全设计

### 6.1 认证授权

- JWT Token认证
- 角色权限控制（USER/MERCHANT/ADMIN）
- 内部服务调用签名验证

### 6.2 数据安全

- 密码加密存储（BCrypt）
- 敏感数据脱敏（手机号、邮箱、身份证）
- XSS过滤
- SQL注入防护

## 7. 可观测性

### 7.1 日志规范

- 统一日志格式
- traceId链路追踪
- 敏感信息脱敏

### 7.2 监控指标

- 服务健康检查
- 接口响应时间
- 错误率统计
- JVM监控

## 8. 部署架构

### 8.1 容器化部署

```yaml
# docker-compose.yml 示例
services:
  gateway:
    image: cloudtry/gateway:latest
    ports:
      - "80:80"
  
  service-user:
    image: cloudtry/service-user:latest
    ports:
      - "7000:7000"
  
  service-product:
    image: cloudtry/service-product:latest
    ports:
      - "9000:9000"
  
  service-order:
    image: cloudtry/service-order:latest
    ports:
      - "8000:8000"
```

### 8.2 环境变量

| 变量名 | 说明 | 示例 |
|--------|------|------|
| NACOS_SERVER_ADDR | Nacos服务地址 | 127.0.0.1:8848 |
| NACOS_NAMESPACE | 命名空间 | dev |
| DB_URL | 数据库连接 | jdbc:mysql://... |
| DB_PASSWORD | 数据库密码 | xxx |
| REDIS_HOST | Redis地址 | localhost |
| JWT_SECRET | JWT密钥 | xxx |

## 9. 版本兼容性说明

### 9.1 Spring Boot 3.x 要求

- JDK 17+ (必须)
- 使用 jakarta.* 命名空间 (非 javax.*)
- 依赖版本需与 Spring Boot 3.x 兼容

### 9.2 依赖版本矩阵

| 组件 | 版本 | 兼容性 |
|------|------|--------|
| Spring Boot | 3.3.4 | - |
| Spring Cloud | 2023.0.3 | 兼容 Spring Boot 3.x |
| Spring Cloud Alibaba | 2023.0.3.2 | 兼容 Spring Boot 3.x |
| Seata | 2.5.0 | 支持 Spring Boot 3.x |
| Sentinel | 1.8.8 | 支持 Spring Boot 3.x |

## 10. 开发规范

### 10.1 代码规范

- 遵循阿里巴巴Java开发规范
- 使用Lombok简化代码
- 统一异常处理
- 统一返回格式

### 10.2 命名规范

- 服务名：service-{domain}
- 数据库：cloudtry_{domain}
- 配置文件：{service}-flow-rules.json
- 消息主题：{domain}-{action}-topic

---

*文档版本：1.0*
*最后更新：2024年*
