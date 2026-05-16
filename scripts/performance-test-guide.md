# 性能测试指南

## 测试类型

### 1. 压力测试 (Stress Testing)
使用 Apache JMeter 进行压力测试，测试系统在高负载下的表现。

### 2. 基准测试 (Benchmark Testing)
使用 JMH (Java Microbenchmark Harness) 进行微基准测试，测试关键方法的性能。

### 3. 容量测试 (Capacity Testing)
测试系统最大处理能力，确定系统的容量上限。

## JMeter 测试脚本

### 测试场景

| 场景 | 描述 | 并发数 | 持续时间 |
|------|------|--------|----------|
| 商品查询 | 测试商品查询接口性能 | 100-500 | 5分钟 |
| 订单创建 | 测试订单创建接口性能 | 50-200 | 5分钟 |
| 订单支付 | 测试订单支付接口性能 | 50-200 | 5分钟 |
| 混合场景 | 模拟真实用户行为 | 100-300 | 10分钟 |

### 运行 JMeter 测试

```bash
# GUI模式（用于调试）
jmeter -t scripts/jmeter/product-query.jmx

# 命令行模式（用于压测）
jmeter -n -t scripts/jmeter/product-query.jmx -l results/product-query.jtl -e -o results/report

# 分布式压测
jmeter -n -t scripts/jmeter/product-query.jmx -R 192.168.1.101,192.168.1.102 -l results/distributed.jtl
```

## JMH 基准测试

### 运行 JMH 测试

```bash
# 编译项目
mvn clean package -DskipTests

# 运行基准测试
java -jar target/benchmarks.jar -wi 3 -i 5 -t 4 -f 1
```

### JMH 参数说明

| 参数 | 说明 |
|------|------|
| -wi | 预热迭代次数 |
| -i | 测量迭代次数 |
| -t | 线程数 |
| -f | fork次数 |

## 性能指标

### 响应时间

| 指标 | 目标值 | 说明 |
|------|--------|------|
| P50 | < 100ms | 50%请求响应时间 |
| P90 | < 300ms | 90%请求响应时间 |
| P99 | < 500ms | 99%请求响应时间 |
| 最大值 | < 2000ms | 最大响应时间 |

### 吞吐量

| 接口 | 目标值 | 说明 |
|------|--------|------|
| 商品查询 | > 1000 TPS | 每秒处理请求数 |
| 订单创建 | > 500 TPS | 每秒处理请求数 |
| 订单支付 | > 300 TPS | 每秒处理请求数 |

### 错误率

| 指标 | 目标值 |
|------|--------|
| 错误率 | < 0.1% |
| 超时率 | < 0.5% |

## 性能监控

### JVM 监控

```bash
# 启动JMX
java -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9010 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -jar app.jar

# 使用JConsole连接
jconsole localhost:9010

# 使用VisualVM连接
jvisualvm --openjmx localhost:9010
```

### 数据库监控

```sql
-- 查看慢查询
SELECT * FROM mysql.slow_log ORDER BY query_time DESC LIMIT 10;

-- 查看连接数
SHOW STATUS LIKE 'Threads_connected';

-- 查看锁等待
SHOW ENGINE INNODB STATUS;
```

### Redis 监控

```bash
# 查看Redis信息
redis-cli info

# 查看慢查询
redis-cli slowlog get 10

# 实时监控
redis-cli monitor
```

## 性能优化建议

### 1. 数据库优化
- 添加必要的索引
- 优化SQL查询
- 使用连接池
- 读写分离

### 2. 缓存优化
- 使用多级缓存
- 合理设置过期时间
- 防止缓存穿透/击穿/雪崩

### 3. 代码优化
- 使用并行处理
- 减少不必要的对象创建
- 使用连接池和线程池

### 4. JVM 优化
- 合理设置堆内存
- 选择合适的GC算法
- 监控GC日志
