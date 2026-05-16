# 自动化回滚系统使用指南

## 概述

自动化回滚系统提供了完整的版本管理和回滚功能，支持手动回滚、自动回滚、版本管理、监控检查等功能。

## 快速开始

### 1. 初始化版本管理

首次使用前，需要初始化版本管理目录：

```bash
# 初始化版本管理目录和示例数据
./scripts/deploy/init-version.sh

# 设置脚本执行权限
chmod +x ./scripts/deploy/rollback.sh
chmod +x ./scripts/deploy/init-version.sh
```

### 2. 基本使用

```bash
# 查看所有服务的版本列表
./scripts/deploy/rollback.sh list

# 查看指定服务的版本列表
./scripts/deploy/rollback.sh list --service service-order

# 查看当前版本
./scripts/deploy/rollback.sh current

# 回滚到上一版本（所有服务）
./scripts/deploy/rollback.sh rollback

# 回滚指定服务到上一版本
./scripts/deploy/rollback.sh rollback --service service-order

# 回滚到指定版本
./scripts/deploy/rollback.sh rollback --service service-order --version v20260512_140000
```

## 功能详解

### 1. 版本管理

#### 1.1 查看版本列表

```bash
# 查看所有服务的版本
./scripts/deploy/rollback.sh list

# 查看指定服务的版本
./scripts/deploy/rollback.sh list --service gateway
```

输出示例：
```
==========================================
版本列表 - gateway
==========================================
  v20260513_143000 [当前版本]
    时间: 2026-05-13 14:30:00
    部署者: admin
    Git SHA: abc123d

  v20260512_140000
    时间: 2026-05-12 14:00:00
    部署者: admin
    Git SHA: def456g
```

#### 1.2 查看当前版本

```bash
./scripts/deploy/rollback.sh current --service service-order
```

#### 1.3 比较版本

```bash
# 比较两个版本
./scripts/deploy/rollback.sh compare v1.0.0 v1.0.1

# 比较指定服务的版本
./scripts/deploy/rollback.sh compare v1.0.0 v1.0.1 --service service-order
```

#### 1.4 清理旧版本

```bash
# 清理所有服务的旧版本（保留最近3个版本）
./scripts/deploy/rollback.sh cleanup

# 清理指定服务的旧版本
./scripts/deploy/rollback.sh cleanup --service service-order
```

### 2. 回滚操作

#### 2.1 手动回滚

**回滚到上一版本：**
```bash
# 回滚所有服务到上一版本
./scripts/deploy/rollback.sh rollback

# 回滚指定服务到上一版本
./scripts/deploy/rollback.sh rollback --service service-order
```

**回滚到指定版本：**
```bash
./scripts/deploy/rollback.sh rollback --service service-order --version v20260512_140000
```

**强制回滚（跳过确认）：**
```bash
./scripts/deploy/rollback.sh rollback --service service-order --force
```

#### 2.2 自动回滚

基于监控指标自动触发回滚：

```bash
# 自动回滚所有服务
./scripts/deploy/rollback.sh rollback --auto

# 自动回滚指定服务
./scripts/deploy/rollback.sh rollback --auto --service service-order
```

自动回滚会检查以下监控指标：
- 错误率 > 1%
- P99响应时间 > 2秒
- CPU使用率 > 80%
- 内存使用率 > 85%

如果任何指标超过阈值，系统会自动触发回滚。

#### 2.3 模拟运行（Dry Run）

```bash
# 模拟回滚操作（不执行实际操作）
./scripts/deploy/rollback.sh rollback --dry-run

# 模拟回滚指定服务
./scripts/deploy/rollback.sh rollback --service service-order --dry-run
```

### 3. 监控检查

#### 3.1 监控阈值配置

监控阈值可在 `deploy-config.yml` 中配置：

```yaml
thresholds:
  error_rate: 0.01              # 错误率阈值 1%
  response_time_p99: 2000       # P99响应时间阈值 2秒
  cpu_usage: 80                 # CPU使用率阈值 80%
  memory_usage: 85              # 内存使用率阈值 85%
  check_interval: 30            # 检查间隔 30秒
```

或通过环境变量设置：

```bash
export ERROR_RATE_THRESHOLD=0.01
export RESPONSE_TIME_THRESHOLD=2000
export CPU_USAGE_THRESHOLD=80
export MEMORY_USAGE_THRESHOLD=85
```

#### 3.2 监控检查流程

回滚前会自动执行以下检查：

1. **错误率检查**
   - 从Prometheus获取最近5分钟的错误率
   - 对比阈值，超过则触发回滚

2. **响应时间检查**
   - 获取P99响应时间
   - 对比阈值，超过则触发回滚

3. **CPU使用率检查**
   - 获取当前CPU使用率
   - 对比阈值，超过则触发回滚

4. **内存使用率检查**
   - 获取当前内存使用率
   - 对比阈值，超过则触发回滚

### 4. 健康检查

#### 4.1 健康检查流程

回滚后会自动执行健康检查：

1. **服务健康检查**
   - 调用 `/actuator/health` 端点
   - 最多重试30次，每次间隔5秒

2. **服务注册检查**
   - 检查服务是否注册到Nacos
   - 验证实例数量

#### 4.2 健康检查配置

不同服务的健康检查端口：

| 服务 | 端口 | 健康检查路径 |
|------|------|--------------|
| gateway | 80 | /actuator/health |
| service-order | 8000 | /actuator/health |
| service-product | 9000 | /actuator/health |
| service-user | 7000 | /actuator/health |

### 5. 日志和调试

#### 5.1 查看日志

```bash
# 查看回滚日志
tail -f deployments/logs/rollback.log

# 查看最近的回滚操作
grep "rollback" deployments/logs/rollback.log | tail -20
```

#### 5.2 调试模式

```bash
# 启用调试模式
./scripts/deploy/rollback.sh rollback --debug

# 调试模式会显示详细的执行过程
```

## 完整参数说明

### 命令

| 命令 | 说明 |
|------|------|
| rollback | 执行回滚操作 |
| list | 列出所有版本 |
| current | 显示当前版本 |
| cleanup | 清理旧版本 |
| compare | 比较两个版本 |

### 选项

| 选项 | 说明 | 示例 |
|------|------|------|
| --service | 指定服务名称 | --service service-order |
| --version | 指定回滚版本 | --version v1.0.0 |
| --auto | 自动模式（基于监控） | --auto |
| --force | 强制回滚（跳过确认） | --force |
| --dry-run | 模拟运行 | --dry-run |
| --debug | 调试模式 | --debug |
| -h, --help | 显示帮助 | --help |

## 使用场景

### 场景1：部署后发现问题，快速回滚

```bash
# 1. 查看当前版本
./scripts/deploy/rollback.sh current --service service-order

# 2. 查看版本列表
./scripts/deploy/rollback.sh list --service service-order

# 3. 回滚到上一版本
./scripts/deploy/rollback.sh rollback --service service-order --force
```

### 场景2：监控告警，自动回滚

```bash
# 设置监控告警触发自动回滚
./scripts/deploy/rollback.sh rollback --auto --service service-order
```

### 场景3：测试回滚流程

```bash
# 使用dry-run模式测试
./scripts/deploy/rollback.sh rollback --service service-order --dry-run --debug
```

### 场景4：回滚到特定版本

```bash
# 1. 查看版本列表，找到目标版本
./scripts/deploy/rollback.sh list --service service-order

# 2. 比较当前版本和目标版本
./scripts/deploy/rollback.sh compare v1.0.0 v1.0.1 --service service-order

# 3. 回滚到目标版本
./scripts/deploy/rollback.sh rollback --service service-order --version v1.0.0
```

## 最佳实践

### 1. 版本管理

- **保留足够的版本记录**：建议保留最近3-5个版本
- **记录详细的版本信息**：包括Git SHA、镜像标签、部署者等
- **定期清理旧版本**：避免占用过多存储空间

### 2. 回滚操作

- **先测试后回滚**：使用 `--dry-run` 模式测试回滚流程
- **监控先行**：回滚前检查监控指标
- **逐步回滚**：优先回滚问题服务，避免全量回滚
- **验证结果**：回滚后验证服务可用性

### 3. 自动化集成

- **CI/CD集成**：在部署流程中集成回滚脚本
- **监控告警**：配置监控告警自动触发回滚
- **日志审计**：记录所有回滚操作

### 4. 安全考虑

- **权限控制**：限制回滚脚本的执行权限
- **操作审计**：记录所有回滚操作
- **回滚确认**：生产环境建议保留确认步骤

## 故障排查

### 问题1：版本记录丢失

**原因**：版本目录被删除或损坏

**解决方案**：
```bash
# 重新初始化版本管理
./scripts/deploy/init-version.sh
```

### 问题2：健康检查失败

**原因**：服务未启动或端口配置错误

**解决方案**：
```bash
# 检查服务状态
docker ps | grep service-order

# 检查端口配置
netstat -tlnp | grep 8000

# 手动健康检查
curl http://localhost:8000/actuator/health
```

### 问题3：监控检查失败

**原因**：监控系统不可用或阈值配置错误

**解决方案**：
```bash
# 检查Prometheus连接
curl http://prometheus:9090/-/healthy

# 调整监控阈值
export ERROR_RATE_THRESHOLD=0.05
./scripts/deploy/rollback.sh rollback --auto
```

### 问题4：回滚失败

**原因**：目标版本不可用或配置错误

**解决方案**：
```bash
# 检查目标版本
./scripts/deploy/rollback.sh list --service service-order

# 验证目标版本
cat deployments/versions/service-order/v1.0.0.json

# 使用强制回滚
./scripts/deploy/rollback.sh rollback --service service-order --force
```

## 与CI/CD集成

### GitLab CI/CD

```yaml
# .gitlab-ci.yml
rollback:
  stage: rollback
  script:
    - ./scripts/deploy/rollback.sh rollback --service $SERVICE_NAME --version $VERSION --force
  when: manual
  environment:
    name: production
```

### Jenkins Pipeline

```groovy
// Jenkinsfile
stage('Rollback') {
    steps {
        script {
            sh "./scripts/deploy/rollback.sh rollback --service ${SERVICE_NAME} --version ${VERSION} --force"
        }
    }
}
```

### GitHub Actions

```yaml
# .github/workflows/rollback.yml
name: Rollback
on:
  workflow_dispatch:
    inputs:
      service:
        description: 'Service name'
        required: true
      version:
        description: 'Target version'
        required: false

jobs:
  rollback:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Execute rollback
        run: |
          chmod +x ./scripts/deploy/rollback.sh
          ./scripts/deploy/rollback.sh rollback --service ${{ github.event.inputs.service }} --version ${{ github.event.inputs.version }} --force
```

## 附录

### A. 版本记录格式

```json
{
    "version": "v20260513_143000",
    "service": "service-order",
    "timestamp": "2026-05-13 14:30:00",
    "deployer": "admin",
    "git": {
        "sha": "abc123def456",
        "branch": "main",
        "message": "feat: 新功能"
    },
    "image": {
        "tag": "1.0.0",
        "registry": "registry.example.com"
    },
    "status": "deployed",
    "rollback_count": 0
}
```

### B. 服务端口映射

| 服务 | 默认端口 | 健康检查路径 |
|------|----------|--------------|
| gateway | 80 | /actuator/health |
| service-order | 8000 | /actuator/health |
| service-product | 9000 | /actuator/health |
| service-user | 7000 | /actuator/health |

### C. 监控指标查询示例

```bash
# 错误率
curl -s "http://prometheus:9090/api/v1/query?query=rate(http_server_requests_seconds_count{status=~\"5..\",service=\"service-order\"}[5m])/rate(http_server_requests_seconds_count{service=\"service-order\"}[5m])"

# P99响应时间
curl -s "http://prometheus:9090/api/v1/query?query=histogram_quantile(0.99,rate(http_server_requests_seconds_bucket{service=\"service-order\"}[5m]))"

# CPU使用率
curl -s "http://prometheus:9090/api/v1/query?query=process_cpu_usage{service=\"service-order\"}"

# 内存使用率
curl -s "http://prometheus:9090/api/v1/query?query=jvm_memory_used_bytes{service=\"service-order\"}/jvm_memory_max_bytes{service=\"service-order\"}"
```

## 联系支持

如有问题，请联系：
- 技术支持：tech-support@example.com
- 文档更新：docs@example.com
