# Seata Nacos 配置中心配置说明

本文档描述如何在 Nacos 配置中心创建 `seata-config.properties` 配置文件。

## 一、前置条件

1. Nacos Server 已启动并可访问
2. Seata Server 已启动并注册到 Nacos

## 二、在 Nacos 中创建配置

### 2.1 登录 Nacos 控制台

访问 Nacos 控制台：`http://{NACOS_SERVER_ADDR}/nacos`

默认账号密码：`nacos/nacos`

### 2.2 创建配置文件

**配置管理 -> 配置列表 -> 点击 "+" 创建配置**

| 配置项 | 值 |
|--------|-----|
| Data ID | `seata-config.properties` |
| Group | `SEATA_GROUP` |
| 配置格式 | `Properties` |
| 配置内容 | 见下方 |

### 2.3 seata-config.properties 配置内容

```properties
# ========================================
# Seata 服务端配置
# ========================================

# 事务分组映射（重要：客户端 tx-service-group 映射到 Seata Server 集群）
service.vgroupMapping.default_tx_group=default

# Seata Server 集群地址（仅当 registry.type=file 时需要配置）
service.default.grouplist=127.0.0.1:8091

# ========================================
# 存储模式配置
# ========================================

# 存储模式: file(文件)、db(数据库)、redis
store.mode=db

# ========================================
# 数据库存储配置（store.mode=db 时需要）
# ========================================

# 数据源类型
store.db.datasource=druid
store.db.dbType=mysql
store.db.driverClassName=com.mysql.cj.jdbc.Driver

# Seata Server 专用数据库连接（需提前创建 seata 数据库）
store.db.url=jdbc:mysql://localhost:3307/seata?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
store.db.user=root
store.db.password=${DB_PASSWORD:your_password}

# 连接池配置
store.db.minConn=5
store.db.maxConn=30
store.db.globalTable=global_table
store.db.branchTable=branch_table
store.db.lockTable=lock_table
store.db.queryLimit=100
store.db.maxWait=5000

# ========================================
# Redis 存储配置（store.mode=redis 时需要）
# ========================================

# store.mode=redis
# store.redis.host=localhost
# store.redis.port=6379
# store.redis.password=
# store.redis.database=0

# ========================================
# 服务端配置
# ========================================

# 服务端口
server.port=8091

# 服务名称
server.serviceName=seata-server

# ========================================
# 事务配置
# ========================================

# 全局事务超时时间（毫秒）
transaction.globalTransactionTimeout=60000

# 全局事务重试次数
transaction.globalTransactionRetryTimes=3

# 全局事务重试间隔（毫秒）
transaction.globalTransactionRetryInterval=1000

# ========================================
# 客户端配置
# ========================================

# RM 客户端配置
client.rm.asyncCommitBufferLimit=10000
client.rm.lock.retryInterval=10
client.rm.lock.retryTimes=30
client.rm.lock.retryPolicyBranchRollbackOnConflict=true
client.rm.reportRetryCount=5
client.rm.tableMetaCheckEnable=true
client.rm.tableMetaCheckerInterval=60000
client.rm.sqlParserType=druid
client.rm.reportSuccessEnable=false
client.rm.sagaBranchRegisterEnable=false
client.rm.sagaJsonParser=fastjson
client.rm.tccActionInterceptorOrder=-2147482648

# TM 客户端配置
client.tm.commitRetryCount=5
client.tm.rollbackRetryCount=5
client.tm.defaultGlobalTransactionTimeout=60000
client.tm.degradeCheck=false
client.tm.degradeCheckAllowTimes=10
client.tm.degradeCheckPeriod=2000
client.tm.interceptorOrder=-2147482648

# Undo 日志配置
client.undo.dataValidation=true
client.undo.logSerialization=jackson
client.undo.onlyCareUpdateColumns=true
client.undo.logTable=undo_log
client.undo.compress.enable=true
client.undo.compress.type=zip
client.undo.compress.threshold=64k

# ========================================
# 日志配置
# ========================================

# 日志级别
log.level=info

# ========================================
# 性能优化配置
# ========================================

# 会话配置
server.session.timeout=60000
server.session.maxActive=1000

# 消息队列配置
server.messageQueue.capacity=10000

# 线程池配置
server.threadPool.coreSize=50
server.threadPool.maxSize=200
server.threadPool.queueCapacity=10000
server.threadPool.keepAliveTime=60
```

## 三、创建 Seata Server 数据库

在 MySQL 中创建 Seata Server 专用的数据库和表：

```sql
-- 创建 Seata 数据库
CREATE DATABASE IF NOT EXISTS seata DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE seata;

-- 全局事务表
CREATE TABLE IF NOT EXISTS `global_table` (
    `xid` VARCHAR(128) NOT NULL,
    `transaction_id` BIGINT,
    `status` TINYINT NOT NULL,
    `application_id` VARCHAR(32),
    `transaction_service_group` VARCHAR(32),
    `transaction_name` VARCHAR(128),
    `timeout` INT,
    `begin_time` BIGINT,
    `application_data` VARCHAR(2000),
    `gmt_create` DATETIME,
    `gmt_modified` DATETIME,
    PRIMARY KEY (`xid`),
    KEY `idx_status_gmt_modified` (`status`, `gmt_modified`),
    KEY `idx_transaction_id` (`transaction_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 分支事务表
CREATE TABLE IF NOT EXISTS `branch_table` (
    `branch_id` BIGINT NOT NULL,
    `xid` VARCHAR(128) NOT NULL,
    `transaction_id` BIGINT,
    `resource_group_id` VARCHAR(32),
    `resource_id` VARCHAR(256),
    `branch_type` VARCHAR(8),
    `status` TINYINT,
    `client_id` VARCHAR(64),
    `application_data` VARCHAR(2000),
    `gmt_create` DATETIME(6),
    `gmt_modified` DATETIME(6),
    PRIMARY KEY (`branch_id`),
    KEY `idx_xid` (`xid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 全局锁表
CREATE TABLE IF NOT EXISTS `lock_table` (
    `row_key` VARCHAR(128) NOT NULL,
    `xid` VARCHAR(128),
    `transaction_id` BIGINT,
    `branch_id` BIGINT NOT NULL,
    `resource_id` VARCHAR(256),
    `table_name` VARCHAR(32),
    `pk` VARCHAR(36),
    `status` TINYINT NOT NULL DEFAULT 0,
    `gmt_create` DATETIME,
    `gmt_modified` DATETIME,
    PRIMARY KEY (`row_key`),
    KEY `idx_status` (`status`),
    KEY `idx_branch_id` (`branch_id`),
    KEY `idx_xid_and_branch_id` (`xid`, `branch_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

## 四、环境变量配置

各服务通过以下环境变量连接 Nacos：

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `NACOS_SERVER_ADDR` | Nacos 服务地址 | `localhost:8848` |
| `NACOS_NAMESPACE` | Nacos 命名空间 ID | 空（public 命名空间） |
| `NACOS_USERNAME` | Nacos 用户名 | 空 |
| `NACOS_PASSWORD` | Nacos 密码 | 空 |

### 4.1 开发环境配置示例

```bash
# .env 文件
NACOS_SERVER_ADDR=localhost:8848
NACOS_NAMESPACE=
NACOS_USERNAME=nacos
NACOS_PASSWORD=nacos
```

### 4.2 生产环境配置示例

```bash
# 生产环境环境变量
NACOS_SERVER_ADDR=nacos-prod.example.com:8848
NACOS_NAMESPACE=seata-prod
NACOS_USERNAME=seata_user
NACOS_PASSWORD=secure_password
```

## 五、Seata Server 配置

Seata Server 也需要配置 Nacos 作为注册中心和配置中心。

### 5.1 Seata Server application.yml

```yaml
server:
  port: 8091

spring:
  application:
    name: seata-server

seata:
  config:
    type: nacos
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
      namespace: ${NACOS_NAMESPACE:}
      group: SEATA_GROUP
      username: ${NACOS_USERNAME:}
      password: ${NACOS_PASSWORD:}
      data-id: seata-config.properties
  registry:
    type: nacos
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
      namespace: ${NACOS_NAMESPACE:}
      group: SEATA_GROUP
      application: seata-server
      username: ${NACOS_USERNAME:}
      password: ${NACOS_PASSWORD:}
  store:
    mode: db
    db:
      datasource: druid
      db-type: mysql
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3307/seata?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
      user: root
      password: ${DB_PASSWORD}
      min-conn: 5
      max-conn: 30
      global-table: global_table
      branch-table: branch_table
      lock-table: lock_table
      query-limit: 100
      max-wait: 5000
```

## 六、验证配置

### 6.1 检查 Nacos 配置

1. 登录 Nacos 控制台
2. 进入 **配置管理 -> 配置列表**
3. 确认 `seata-config.properties` 配置存在于 `SEATA_GROUP` 分组下

### 6.2 检查服务注册

1. 进入 **服务管理 -> 服务列表**
2. 确认 `seata-server` 已注册
3. 确认各微服务实例已注册

### 6.3 测试分布式事务

启动所有服务后，通过下单接口测试分布式事务是否正常工作。

## 七、常见问题

### 7.1 服务无法连接 Nacos

**检查项：**
- Nacos Server 是否正常运行
- 网络是否可达
- 环境变量是否正确配置

### 7.2 找不到事务分组

**检查项：**
- `seata-config.properties` 中 `service.vgroupMapping.default_tx_group` 是否配置
- 客户端 `tx-service-group` 是否与服务端映射一致

### 7.3 分布式事务失败

**检查项：**
- 各数据库是否已创建 `undo_log` 表
- Seata Server 数据库是否已创建 `global_table`、`branch_table`、`lock_table`
- 查看各服务日志，确认 Seata 相关错误信息

## 八、配置迁移说明

### 8.1 从 file 模式迁移到 nacos 模式

1. 在 Nacos 中创建 `seata-config.properties` 配置
2. 修改各服务的 `application.yml`，将 `config.type` 和 `registry.type` 改为 `nacos`
3. 重启所有服务

### 8.2 回滚到 file 模式

如需回滚，将配置改回：

```yaml
seata:
  config:
    type: file
  registry:
    type: file
  service:
    vgroup-mapping:
      default_tx_group: default
    grouplist:
      default: 127.0.0.1:8091
```
