# CloudTry - 微服务电商平台

基于 Spring Cloud Alibaba 构建的微服务电商平台，实现用户管理、商品管理、订单管理等核心电商功能，注重高并发、高可用和分布式事务处理能力。

## 核心特性

- **微服务架构** — Spring Cloud Alibaba 生态（Nacos 注册配置 + Sentinel 限流熔断 + Seata 分布式事务 + RocketMQ 消息驱动）
- **高并发防护** — Redisson 分布式锁 + Lua 原子操作 + Redis 多级缓存 + 消息队列削峰填谷
- **分布式事务** — Seata AT 模式 + TransactionTemplate 编程式事务 + afterCommit 消息一致性
- **安全防护** — JWT 认证 + HMAC 内部请求签名 + XSS 过滤 + 敏感数据脱敏 + 权限拦截
- **容器化部署** — Docker Compose 编排 + GitHub Actions CI/CD + 灰度发布

## 系统架构

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

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.3.4 | 基础框架 (JDK 17+) |
| Spring Cloud | 2023.0.3 | 微服务生态 |
| Spring Cloud Alibaba | 2023.0.3.2 | 微服务生态 |
| Nacos | 2.x | 服务注册与配置中心 |
| Sentinel | 1.8.8 | 流量控制与熔断降级 |
| Seata | 2.5.0 | 分布式事务 |
| RocketMQ | 2.3.5 | 消息队列 |
| Redis | 6.x/7.x | 缓存与分布式锁 |
| MySQL | 8.x | 关系型数据库 |
| MyBatis-Plus | 3.5.5 | ORM 框架 |
| Redisson | 3.27.2 | 分布式锁与缓存 |

## 项目结构

```
cloudtry/
├── gateway/                        # API 网关 (端口 80)
│   ├── filter/
│   │   ├── AuthTokenFilter.java        # JWT认证 + 内部API路径保护
│   │   ├── AdminAuthFilter.java        # 管理员权限过滤
│   │   └── XssWebFluxFilter.java       # XSS攻击过滤
│   └── predicate/
│       └── VipRoutePredicateFactory.java  # VIP用户路由谓词
│
├── model/                          # 公共模型模块
│   ├── bean/                           # UserAccount, Order, Product, Coupon 等
│   ├── cache/                          # CacheService, BloomFilterService, MultiLevelCacheService
│   ├── config/                         # RedissonConfig, OpenApiConfig
│   ├── enums/                          # OrderStatus, CouponStatus, UserRole
│   ├── exception/                      # BusinessException, GlobalExceptionHandler
│   ├── interceptor/                    # FeignInternalRequestInterceptor, PermissionInterceptor
│   ├── mq/                             # OrderNotifyMessage, SeckillMessage 等
│   ├── service/                        # IdempotencyService, RateLimitService
│   └── utils/                          # HmacSignatureUtil, PasswordUtil, SensitiveDataMasker
│
├── services/
│   ├── service-user/               # 用户服务 (端口 7000)
│   │   ├── controller/                 # UserController, FileController
│   │   ├── service/impl/               # UserAuthServiceImpl, LocalFileStorageServiceImpl
│   │   ├── consumer/                   # OrderNotifyConsumer, VerifyCodeConsumer
│   │   └── mapper/                     # UserAccountMapper, UserAddressMapper
│   │
│   ├── service-product/            # 商品服务 (端口 9000)
│   │   ├── controller/                 # ProductController, CategoryController, ProductAuditController
│   │   │                               # InventoryAlertController, InternalProductController
│   │   ├── service/impl/               # ProductServiceImpl, ProductAuditServiceImpl
│   │   │                               # CategoryServiceImpl, InventoryAlertServiceImpl
│   │   ├── consumer/                   # InventoryAlertConsumer
│   │   ├── schedule/                   # HotDataRefreshTask, InventoryAlertTask
│   │   └── mapper/                     # ProductMapper, CategoryMapper, ProductAuditLogMapper
│   │                                   # InventoryAlertConfigMapper, InventoryAlertLogMapper
│   │
│   └── service-order/              # 订单服务 (端口 8000)
│       ├── controller/                 # OrderController, CartController, SeckillController
│       │                               # AfterSaleController, CouponController
│       │                               # LogisticsController, OrderReviewController
│       │                               # VirtualAccountController, MetricsController
│       ├── service/impl/               # OrderServiceImpl, CouponServiceImpl
│       │                               # VirtualAccountServiceImpl, AfterSaleServiceImpl
│       │                               # SeckillServiceImpl, CartServiceImpl
│       │                               # LogisticsServiceImpl, OrderReviewServiceImpl
│       ├── mapper/                     # OrderMapper, CouponMapper, VirtualAccountMapper 等
│       ├── feign/                      # ProductFeign, UserFeign, WeatherFeign
│       ├── consumer/                   # ReviewConsumer, SeckillConsumer
│       ├── schedule/                   # OrderTimeoutTask, ReviewStatsSyncTask
│       └── aspect/                     # BusinessMetricsAspect, PerformanceMonitorAspect
│
├── scripts/
│   ├── init.sql                    # 数据库初始化
│   ├── seata_undo_log.sql          # Seata回滚日志表
│   ├── create-mq-topics.sh         # 创建MQ主题
│   └── deploy/                     # 部署脚本（灰度发布、回滚）
│
├── .github/workflows/ci-cd.yml    # GitHub Actions CI/CD
├── docker-compose.yml              # Docker编排
├── .env.example                    # 环境变量模板
└── pom.xml                         # 父POM
```

## 服务间调用链路

### Feign 同步调用

```
service-order ──Feign──> service-product
  ├── getProductById(Long id)
  ├── decreaseStock(Long productId, Integer quantity)
  ├── batchDecreaseStock(List<StockItem>)
  └── batchIncreaseStock(List<StockItem>)

service-order ──Feign──> service-user
  └── getUserById(Long id)

service-order ──Feign──> 外部天气服务 (固定URL)
  └── getWeather(String city)
```

### MQ 异步通信

```
service-order
  ├── 生产: order-notify-topic     (订单创建通知)
  ├── 生产/消费: seckill-topic     (秒杀请求 → 秒杀订单创建)
  ├── 生产: after-sale-notify-topic (售后通知)
  └── 生产/消费: review-topic      (评价消息 → 评价缓存更新)

service-product
  ├── 生产: product-audit-topic    (商品审核通知)
  └── 生产/消费: inventory-alert-topic (库存预警通知 → 预警处理)

service-user
  ├── 消费: order-notify-topic     (订单通知处理)
  └── 生产/消费: verify-code-topic (验证码发送)
```

## 核心业务

### 用户服务
- 注册 / 登录 / JWT 认证 / 商家入驻审核
- 用户地址管理 / 头像上传 / 验证码发送

### 商品服务
- 商品 CRUD / 分类管理 / 库存管理
- 商品审核流程 / 库存预警 / 热点商品缓存

### 订单服务
- 购物车 / 下单 / 支付 / 确认收货 / 评价
- 优惠券（Lua 原子扣减防超卖）/ 秒杀（Redis SETNX 用户幂等）
- 售后工单 / 虚拟账户（乐观锁 + afterCommit 缓存一致性）
- 物流管理 / 分布式事务（Seata + afterCommit MQ 一致性）

### API 网关
- 统一路由 / JWT 认证 / 内部 API 签名验证
- XSS 过滤 / Sentinel 限流 / VIP 路由谓词

## API 接口概览

### 用户服务 `/api/user`

| 方法 | 路径 | 功能 | 认证 |
|------|------|------|------|
| POST | `/login` | 用户登录 | 否 |
| POST | `/register` | 用户注册 | 否 |
| POST | `/registerMerchant` | 商家注册 | 否 |
| POST | `/resetPassword` | 重置密码 | 否 |
| POST | `/sendVerificationCode` | 发送验证码 | 否 |
| GET | `/me` | 获取用户信息 | 是 |
| GET | `/address` | 获取地址列表 | 是 |
| POST | `/address` | 添加地址 | 是 |
| PUT | `/address/{id}` | 更新地址 | 是 |
| DELETE | `/address/{id}` | 删除地址 | 是 |

### 商品服务 `/api/product`

| 方法 | 路径 | 功能 | 认证 |
|------|------|------|------|
| GET | `/{id}` | 获取商品详情 | 否 |
| GET | `/` | 获取商品列表 | 否 |
| GET | `/hot` | 获取热点商品 | 否 |
| GET | `/search` | 搜索商品 | 否 |
| POST | `/manage/save` | 保存商品 | MERCHANT/ADMIN |
| DELETE | `/manage/{id}` | 删除商品 | MERCHANT/ADMIN |
| PUT | `/manage/{id}/price` | 更新价格 | MERCHANT/ADMIN |
| POST | `/audit/single` | 单个商品审核 | ADMIN |
| POST | `/audit/batch` | 批量商品审核 | ADMIN |
| POST | `/alert/config` | 保存预警配置 | MERCHANT/ADMIN |
| POST | `/alert/trigger/{productId}` | 手动触发预警 | MERCHANT/ADMIN |

### 订单服务 `/api/order`

| 方法 | 路径 | 功能 | 认证 |
|------|------|------|------|
| GET | `/{id}` | 获取订单详情 | 是 |
| GET | `/my` | 获取我的订单 | 是 |
| POST | `/` | 创建订单 | 是 |
| PUT | `/{id}/pay` | 支付订单 | 是 |
| PUT | `/{id}/ship` | 发货 | MERCHANT |
| PUT | `/{id}/complete` | 确认收货 | 是 |
| PUT | `/{id}/cancel` | 取消订单 | 是 |

### 购物车 `/api/cart`

| 方法 | 路径 | 功能 | 认证 |
|------|------|------|------|
| GET | `/` | 获取购物车 | 是 |
| POST | `/add` | 添加购物车 | 是 |

### 秒杀 `/api/seckill`

| 方法 | 路径 | 功能 | 认证 |
|------|------|------|------|
| POST | `/` | 秒杀 | USER |

### 售后 `/api/aftersale`

| 方法 | 路径 | 功能 | 认证 |
|------|------|------|------|
| POST | `/apply` | 申请售后 | 是 |
| PUT | `/{id}/approve` | 同意售后 | MERCHANT |
| PUT | `/{id}/reject` | 拒绝售后 | MERCHANT |
| PUT | `/{id}/manual-refund` | 手动退款 | MERCHANT |
| PUT | `/{id}/cancel` | 取消售后 | 是 |
| GET | `/my` | 我的售后列表 | 是 |
| GET | `/merchant` | 商家售后列表 | MERCHANT |

### 虚拟账户 `/api/account`

| 方法 | 路径 | 功能 | 认证 |
|------|------|------|------|
| POST | `/recharge` | 充值 | 是 |
| GET | `/balance` | 查询余额 | 是 |
| GET | `/logs` | 交易流水 | 是 |
| POST | `/internal/pay` | 内部支付接口 | 内部 |
| POST | `/internal/refund` | 内部退款接口 | 内部 |

## 核心业务流程

### 下单支付

```
创建订单 → 获取幂等性锁 → 验证商品 → 扣减库存(@GlobalTransactional) → 应用优惠券
        → 创建订单记录 → 发送订单通知消息 → 释放幂等性锁

支付订单 → Redisson分布式锁 → 验证订单状态 → 虚拟账户扣款(乐观锁) → 更新状态为已支付
        → afterCommit: 更新缓存 + 发送MQ

确认收货 → 更新状态为已完成 → 可发起评价
```

### 秒杀

```
秒杀请求 → Sentinel限流 → 用户维度限流(1次/秒) → 布隆过滤器检查商品
        → Redis Lua脚本预扣库存 → 用户级SETNX幂等检查 → 发送秒杀消息到MQ → 返回排队中

秒杀消费 → 消费秒杀消息 → 创建订单 → 扣减数据库库存 → 失败时Lua脚本回滚库存
```

### 售后

```
用户申请售后 → 校验订单状态(COMPLETED)+7天期限 → 创建售后工单(PENDING)
商家同意 → 自动退款(虚拟账户) → 退款成功 → 工单COMPLETED + 订单REFUNDED
                              → 退款失败 → 工单PROCESSING(需人工处理) → 手动退款兜底
商家拒绝 → 工单REJECTED → MQ通知
用户取消 → 工单CANCELLED (仅PENDING状态可取消)
```

## 高并发设计

| 场景 | 方案 |
|------|------|
| 优惠券超卖 | Lua 脚本原子 decrement-and-check + DB 同步扣减 |
| 订单双重支付 | Redisson 分布式锁 + 幂等 transactionNo |
| 乐观锁重试余额计算 | 重试后基于 `account.getBalance()±amount` 计算 balanceAfter |
| 缓存事务一致性 | `TransactionSynchronization.afterCommit()` 更新缓存和发送 MQ |
| 锁-事务协调 | 分布式锁在 `afterCommit` 中释放，异常时立即释放 |
| 秒杀并发 | Lua 原子扣减 + 用户级 SETNX 幂等 + MQ 异步下单 |
| 购物车并发 | Redisson 分布式锁保护写操作 |

## 缓存策略

```
请求 → 本地缓存(Caffeine) → Redis缓存 → 数据库
```

| 策略 | 实现 | 用途 |
|------|------|------|
| 缓存穿透防护 | 空值缓存(60s) | 查询不存在的数据 |
| 缓存击穿防护 | 互斥锁 | 热点Key过期 |
| 缓存雪崩防护 | 随机过期时间 | 大量Key同时过期 |
| 缓存一致性 | 双删策略 + afterCommit | 数据更新后保持一致 |

## 数据库

每个微服务使用独立数据库：

| 服务 | 数据库 | 核心表 |
|------|--------|--------|
| service-user | cloudtry_user | user_account, user_address |
| service-product | cloudtry_product | product, product_audit_log, inventory_alert_config, inventory_alert_log |
| service-order | cloudtry_order | order, order_item, cart_item, coupon, virtual_account, virtual_account_log, after_sale_ticket, logistics_info, logistics_trace, order_review |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- Nacos 2.x
- RocketMQ 4.9+
- Seata Server 2.x

### 安装步骤

1. **克隆项目**
   ```bash
   git clone https://github.com/你的用户名/CloudTry.git
   cd CloudTry
   ```

2. **配置环境变量**
   ```bash
   cp .env.example .env
   # 编辑 .env 文件，填写实际配置值（数据库密码、JWT密钥等）
   ```

3. **初始化数据库**
   ```bash
   mysql -u root -p < scripts/init.sql
   mysql -u root -p < scripts/seata_undo_log.sql
   ```

4. **启动中间件**
   - Nacos（端口 8848）
   - Redis（端口 6379）
   - RocketMQ NameServer + Broker
   - Seata Server（端口 8091）

5. **构建项目**
   ```bash
   mvn clean install -DskipTests
   ```

6. **启动服务**
   ```bash
   # 1. 网关
   cd gateway && mvn spring-boot:run
   # 2. 用户服务
   cd services/service-user && mvn spring-boot:run
   # 3. 商品服务
   cd services/service-product && mvn spring-boot:run
   # 4. 订单服务
   cd services/service-order && mvn spring-boot:run
   ```

7. **验证**
   - Nacos 控制台：http://localhost:8848/nacos
   - API 网关：http://localhost:80

### Docker 部署

```bash
docker-compose up -d
```

包含 MySQL、Redis、Nacos、RocketMQ、Seata、Sentinel 及全部应用服务的一键启动。

## 安全机制

- **JWT 认证** — 网关统一验证，用户信息透传到下游服务
- **HMAC 签名** — Gateway → 微服务内部请求携带 `X-Internal-Request` 签名头（HMAC-SHA256，5分钟有效）
- **内部 API 保护** — 网关精确路径段匹配，防止外部绕过认证访问内部接口
- **OpenFeign 直连** — `FeignInternalRequestInterceptor` 按 Feign 目标路径生成令牌
- **XSS 过滤** — WebFlux + Servlet 双层过滤
- **敏感数据脱敏** — 手机号、身份证、银行卡号自动脱敏
- **权限拦截** — `@RequirePermission` 注解 + 拦截器
- **登录限流** — 5次失败锁定5分钟，防暴力破解

## 许可证

本项目仅供学习参考使用。
