# 连接池配置优化指南

## 配置参数说明

### HikariCP 连接池参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| maximum-pool-size | 20 | 最大连接数 |
| minimum-idle | 5 | 最小空闲连接数 |
| connection-timeout | 30000 | 连接超时时间（毫秒） |
| idle-timeout | 600000 | 空闲连接超时时间（毫秒） |
| max-lifetime | 1800000 | 连接最大生命周期（毫秒） |
| leak-detection-threshold | 60000 | 连接泄漏检测阈值（毫秒） |
| connection-test-query | SELECT 1 | 连接测试查询 |
| validation-timeout | 5000 | 连接验证超时（毫秒） |

## 根据CPU核心数计算最优连接数

### 计算公式

**IO密集型应用（数据库操作为主）：**
```
maximum-pool-size = CPU核心数 * 2 + 1
```

**CPU密集型应用（计算为主）：**
```
maximum-pool-size = CPU核心数 + 1
```

### 推荐配置

| CPU核心数 | IO密集型 | CPU密集型 |
|-----------|----------|-----------|
| 2核 | 5 | 3 |
| 4核 | 9 | 5 |
| 8核 | 17 | 9 |
| 16核 | 33 | 17 |
| 32核 | 65 | 33 |

## 环境变量配置

在 `.env` 文件或启动参数中设置：

```bash
# 8核服务器推荐配置
HIKARI_MAX_POOL_SIZE=17
HIKARI_MIN_IDLE=5

# 16核服务器推荐配置
HIKARI_MAX_POOL_SIZE=33
HIKARI_MIN_IDLE=10
```

## Docker Compose 配置示例

```yaml
services:
  service-order:
    environment:
      - HIKARI_MAX_POOL_SIZE=17
      - HIKARI_MIN_IDLE=5
```

## 监控指标

通过 Spring Boot Actuator 监控连接池状态：

```bash
# 获取HikariCP指标
curl http://localhost:8000/actuator/metrics/hikaricp.connections.active
curl http://localhost:8000/actuator/metrics/hikaricp.connections.idle
curl http://localhost:8000/actuator/metrics/hikaricp.connections.pending
```

## 注意事项

1. **不要设置过大的连接数**：过多的连接会增加数据库负担，反而降低性能
2. **minimum-idle 不要设置过小**：会导致频繁创建连接
3. **监控连接泄漏**：leak-detection-threshold 帮助发现未关闭的连接
4. **定期检查连接池状态**：通过Actuator监控活跃连接数

## 性能测试建议

1. 使用JMeter进行压力测试
2. 监控连接池使用率
3. 根据测试结果调整连接数
4. 关注数据库CPU和内存使用情况
