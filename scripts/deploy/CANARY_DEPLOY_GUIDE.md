# 灰度发布脚本使用说明

## 文件位置

- **脚本文件**: `scripts/deploy/canary-deploy.sh`
- **配置文件**: `scripts/deploy/deploy-config.yml`

## 功能特性

### 1. 灰度发布脚本 (canary-deploy.sh)

#### 主要功能

- ✅ 支持流量权重控制（0-100%）
- ✅ 支持单个服务或所有服务的灰度发布
- ✅ 支持Docker Compose和Kubernetes两种部署方式
- ✅ 自动健康检查和监控指标验证
- ✅ 失败自动回滚机制
- ✅ 模拟运行模式（--dry-run）
- ✅ 调试模式（--debug）
- ✅ 详细的日志输出

#### 参数说明

```bash
./canary-deploy.sh <weight> [service_name] [options]
```

**必需参数**:
- `weight`: 流量权重（0-100）
  - 0: 回滚到旧版本
  - 1-99: 灰度发布，指定百分比流量到新版本
  - 100: 全量发布到新版本

**可选参数**:
- `service_name`: 服务名称（可选，默认所有服务）
  - gateway
  - service-order
  - service-product
  - service-user

**选项参数**:
- `--dry-run`: 模拟运行，不执行实际部署操作
- `--debug`: 调试模式，输出详细日志
- `--config FILE`: 指定配置文件路径
- `--help, -h`: 显示帮助信息

#### 使用示例

```bash
# 基础用法
./canary-deploy.sh 10                          # 10%流量到新版本（所有服务）
./canary-deploy.sh 30 service-order            # 30%流量到新版本（仅service-order）
./canary-deploy.sh 100                         # 全量发布（100%流量）
./canary-deploy.sh 0                           # 回滚到旧版本

# 高级用法
./canary-deploy.sh 50 --dry-run                # 模拟运行50%灰度发布
./canary-deploy.sh 30 service-order --debug    # 调试模式运行
./canary-deploy.sh 20 --config /path/to/config.yml  # 使用自定义配置文件
```

#### 灰度发布流程

1. **参数验证**: 验证权重参数和服务名称
2. **配置加载**: 从deploy-config.yml加载配置
3. **平台检测**: 自动检测部署平台（Kubernetes或Docker Compose）
4. **实例计算**: 根据权重计算新旧版本实例数量
5. **执行部署**: 执行实际的部署操作
6. **健康检查**: 验证服务健康状态
7. **监控检查**: 检查监控指标（错误率、响应时间等）
8. **自动回滚**: 如果检查失败，自动回滚到旧版本

#### 实例管理

脚本会根据权重自动计算实例分配：

- **总实例数**: 从配置文件读取（默认2个）
- **新版本实例数**: `(总实例数 * 权重 + 99) / 100`
- **旧版本实例数**: `总实例数 - 新版本实例数`

示例（总实例数=2）：
- 权重10%: 旧版本2个，新版本1个
- 权重30%: 旧版本1个，新版本1个
- 权重50%: 旧版本1个，新版本1个
- 权重100%: 旧版本0个，新版本2个

### 2. 灰度发布配置 (deploy-config.yml)

#### 配置结构

```yaml
# 服务配置
services:
  gateway:
    name: cloudtry-gateway
    port: 80
    health_check: /actuator/health
    instances: 2
    
# 灰度发布策略
canary:
  stages:
    - weight: 10
      duration: 300
      auto_promote: false
      
# 监控阈值
thresholds:
  error_rate: 0.01
  response_time_p99: 2000
  cpu_usage: 80
  memory_usage: 85
  
# 健康检查配置
health_check:
  endpoint: /actuator/health
  timeout: 10
  max_retries: 30
  retry_interval: 5
  
# 回滚配置
rollback:
  auto_rollback: true
  keep_versions: 3
  timeout: 300
```

#### 灰度发布策略

配置文件定义了4个阶段：

1. **第一阶段**: 10%流量，持续5分钟，手动确认
2. **第二阶段**: 30%流量，持续5分钟，手动确认
3. **第三阶段**: 50%流量，持续5分钟，手动确认
4. **第四阶段**: 100%流量，全量发布，自动推进

#### 监控阈值

- **错误率阈值**: 1%（超过触发回滚）
- **响应时间P99**: 2秒（超过触发回滚）
- **CPU使用率**: 80%（超过触发告警）
- **内存使用率**: 85%（超过触发告警）
- **检查间隔**: 30秒

#### 健康检查配置

- **健康检查端点**: `/actuator/health`
- **超时时间**: 10秒
- **最大重试次数**: 30次
- **重试间隔**: 5秒
- **启动等待时间**: 60秒

## 部署平台支持

### Docker Compose

脚本会生成以下命令：

```bash
# 灰度发布
docker-compose -f docker-compose.yml -f docker-compose-canary.yml up -d \
    --scale service-order=1 \
    --scale service-order-canary=1

# 全量发布
docker-compose up -d --scale service-order=2 service-order

# 回滚
docker-compose up -d --scale service-order=2 service-order-old
```

### Kubernetes

脚本会生成以下命令：

```bash
# 灰度发布
kubectl patch service service-order -n default -p '{"spec":{"canaryWeight":30}}'

# 全量发布
kubectl rollout status deployment/service-order -n default

# 回滚
kubectl rollout undo deployment/service-order -n default
```

## 日志和监控

### 日志输出

脚本提供详细的日志输出：

```
2026-05-13 10:00:00 [INFO] 灰度发布脚本启动
2026-05-13 10:00:00 [INFO] 流量权重: 30%
2026-05-13 10:00:00 [INFO] 目标服务: service-order
2026-05-13 10:00:00 [INFO] 部署平台: docker-compose
2026-05-13 10:00:01 [INFO] 服务 service-order 实例分配:
2026-05-13 10:00:01 [INFO]   总实例数: 2
2026-05-13 10:00:01 [INFO]   旧版本实例: 1 (70%)
2026-05-13 10:00:01 [INFO]   新版本实例: 1 (30%)
2026-05-13 10:00:02 [SUCCESS] ✓ service-order 健康检查通过 (HTTP 200)
2026-05-13 10:00:03 [SUCCESS] ✓ service-order 监控指标检查通过
2026-05-13 10:00:03 [SUCCESS] ✓ service-order 部署成功
```

### 日志文件

日志文件位置: `./logs/canary-deploy.log`

## 错误处理

### 自动回滚触发条件

1. 健康检查失败（连续30次重试失败）
2. 错误率超过阈值（默认1%）
3. 响应时间超过阈值（默认P99 > 2秒）
4. 部署命令执行失败

### 回滚流程

1. 检测到异常
2. 记录错误日志
3. 执行回滚命令（权重设为0）
4. 验证回滚成功
5. 发送通知（如果配置）

## 最佳实践

### 1. 使用模拟运行测试

```bash
# 先使用--dry-run测试部署流程
./canary-deploy.sh 30 service-order --dry-run
```

### 2. 逐步推进灰度

```bash
# 第一阶段：10%流量
./canary-deploy.sh 10 service-order

# 观察监控指标，确认无异常后继续

# 第二阶段：30%流量
./canary-deploy.sh 30 service-order

# 第三阶段：50%流量
./canary-deploy.sh 50 service-order

# 第四阶段：全量发布
./canary-deploy.sh 100 service-order
```

### 3. 使用调试模式排查问题

```bash
./canary-deploy.sh 30 service-order --debug
```

### 4. 监控关键指标

在灰度发布过程中，重点关注：
- 错误率是否超过阈值
- 响应时间是否正常
- CPU和内存使用率
- 服务健康状态

## 注意事项

1. **首次使用**: 建议先使用`--dry-run`模式测试
2. **生产环境**: 生产环境建议使用更严格的监控阈值
3. **回滚准备**: 确保旧版本镜像可用，以便快速回滚
4. **监控集成**: 建议集成Prometheus和Grafana进行实时监控
5. **通知配置**: 生产环境建议配置钉钉或邮件通知

## 依赖工具

- **必需**:
  - bash (Linux/macOS) 或 Git Bash (Windows)
  - curl (健康检查)

- **可选**:
  - docker-compose 或 docker compose (Docker部署)
  - kubectl (Kubernetes部署)
  - jq (JSON解析)
  - bc (数值计算)

## 故障排查

### 问题1: 脚本无法执行

**解决方案**:
```bash
# Linux/macOS
chmod +x scripts/deploy/canary-deploy.sh

# Windows (Git Bash)
git update-index --chmod=+x scripts/deploy/canary-deploy.sh
```

### 问题2: 健康检查失败

**解决方案**:
1. 检查服务是否正常启动
2. 检查端口配置是否正确
3. 检查健康检查端点是否可访问
4. 使用`--debug`模式查看详细信息

### 问题3: 配置文件读取失败

**解决方案**:
1. 确认配置文件路径正确
2. 检查YAML格式是否正确
3. 使用`--config`参数指定配置文件路径

## 扩展功能

### 自定义健康检查

可以在配置文件中自定义健康检查参数：

```yaml
health_check:
  endpoint: /actuator/health
  timeout: 15
  max_retries: 60
  retry_interval: 3
```

### 多环境配置

配置文件支持多环境配置：

```yaml
environments:
  dev:
    thresholds:
      error_rate: 0.05
  staging:
    thresholds:
      error_rate: 0.02
  prod:
    thresholds:
      error_rate: 0.01
```

## 联系方式

如有问题或建议，请联系：
- 作者: Backend Architect
- 版本: v2.0
- 更新时间: 2026-05-13
