# 回滚脚本快速参考

## 常用命令

### 版本管理

```bash
# 初始化版本管理
./scripts/deploy/init-version.sh

# 查看所有版本
./scripts/deploy/rollback.sh list

# 查看指定服务版本
./scripts/deploy/rollback.sh list --service service-order

# 查看当前版本
./scripts/deploy/rollback.sh current

# 清理旧版本
./scripts/deploy/rollback.sh cleanup
```

### 回滚操作

```bash
# 回滚到上一版本
./scripts/deploy/rollback.sh rollback

# 回滚指定服务
./scripts/deploy/rollback.sh rollback --service service-order

# 回滚到指定版本
./scripts/deploy/rollback.sh rollback --service service-order --version v1.0.0

# 自动回滚（基于监控）
./scripts/deploy/rollback.sh rollback --auto

# 强制回滚（跳过确认）
./scripts/deploy/rollback.sh rollback --force

# 模拟运行
./scripts/deploy/rollback.sh rollback --dry-run

# 调试模式
./scripts/deploy/rollback.sh rollback --debug
```

## 参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| --service | 服务名称 | 所有服务 |
| --version | 目标版本 | 上一版本 |
| --auto | 自动模式 | false |
| --force | 强制执行 | false |
| --dry-run | 模拟运行 | false |
| --debug | 调试模式 | false |

## 服务列表

- gateway (端口: 80)
- service-order (端口: 8000)
- service-product (端口: 9000)
- service-user (端口: 7000)

## 监控阈值

| 指标 | 阈值 |
|------|------|
| 错误率 | 1% |
| P99响应时间 | 2秒 |
| CPU使用率 | 80% |
| 内存使用率 | 85% |

## 典型场景

### 快速回滚
```bash
./scripts/deploy/rollback.sh rollback --service service-order --force
```

### 测试回滚
```bash
./scripts/deploy/rollback.sh rollback --service service-order --dry-run --debug
```

### 自动回滚
```bash
./scripts/deploy/rollback.sh rollback --auto --service service-order
```

## 日志位置

- 回滚日志: `deployments/logs/rollback.log`
- 版本记录: `deployments/versions/{service}/`

## 故障排查

```bash
# 查看日志
tail -f deployments/logs/rollback.log

# 检查版本记录
ls -la deployments/versions/service-order/

# 手动健康检查
curl http://localhost:8000/actuator/health
```

## 注意事项

1. 默认保留最近3个版本
2. 当前版本不会被清理
3. 回滚前会进行健康检查
4. 支持强制回滚和模拟运行
5. 自动回滚基于监控指标触发
