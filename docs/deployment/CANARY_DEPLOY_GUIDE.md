# 灰度发布指南

> **文档版本**: 2.0
> **最后更新**: 2026-05-13
> **适用场景**: 生产环境灰度发布、A/B测试、风险控制

---

## 目录

1. [灰度发布概念和优势](#1-灰度发布概念和优势)
2. [灰度发布流程说明](#2-灰度发布流程说明)
3. [canary-deploy.sh脚本使用方法](#3-canary-deploysh脚本使用方法)
4. [流量权重配置说明](#4-流量权重配置说明)
5. [监控指标阈值配置](#5-监控指标阈值配置)
6. [健康检查机制说明](#6-健康检查机制说明)
7. [自动回滚触发条件](#7-自动回滚触发条件)
8. [灰度发布最佳实践](#8-灰度发布最佳实践)
9. [故障排查指南](#9-故障排查指南)

---

## 1. 灰度发布概念和优势

### 1.1 什么是灰度发布

灰度发布（Canary Deployment）是一种软件发布策略，通过逐步将流量从旧版本迁移到新版本，降低发布风险。在灰度发布过程中，只有部分用户会访问到新版本，其余用户仍然访问旧版本。

```
传统发布:
旧版本 100% ───────────> 新版本 100%
           (一次性切换，风险高)

灰度发布:
旧版本 100% ──> 90% ──> 70% ──> 50% ──> 30% ──> 0%
新版本   0% ──> 10% ──> 30% ──> 50% ──> 70% ──> 100%
           (逐步迁移，风险可控)
```

### 1.2 灰度发布的优势

#### 1.2.1 降低发布风险

- **问题影响范围小**: 只有部分用户受影响
- **快速发现问题**: 小流量下问题更容易暴露
- **快速回滚**: 可以立即回滚到旧版本

#### 1.2.2 平滑过渡

- **用户体验无感知**: 用户不会感觉到服务中断
- **负载均衡**: 逐步增加负载，避免服务器压力骤增
- **数据迁移**: 为数据库迁移提供缓冲时间

#### 1.2.3 验证新功能

- **A/B测试**: 可以对比新旧版本的性能和用户体验
- **真实环境验证**: 在生产环境中验证新功能
- **收集反馈**: 收集早期用户的反馈

### 1.3 灰度发布 vs 其他发布策略

| 发布策略 | 停机时间 | 风险 | 回滚速度 | 资源消耗 | 适用场景 |
|---------|---------|------|---------|---------|---------|
| 蓝绿部署 | 无 | 中 | 快 | 高 | 需要快速切换 |
| 滚动更新 | 无 | 中 | 中 | 低 | 常规更新 |
| 灰度发布 | 无 | 低 | 快 | 中 | 高风险更新 |
| 金丝雀发布 | 无 | 低 | 快 | 中 | 新功能验证 |

---

## 2. 灰度发布流程说明

### 2.1 标准灰度发布流程

```
┌─────────────────────────────────────────────────────────────┐
│                    灰度发布流程                               │
└─────────────────────────────────────────────────────────────┘

阶段1: 准备阶段
├── 1.1 代码审查和测试
├── 1.2 配置监控告警
├── 1.3 准备回滚方案
└── 1.4 通知相关人员

阶段2: 初始灰度（10%流量）
├── 2.1 部署新版本实例
├── 2.2 配置10%流量路由
├── 2.3 监控5-10分钟
│   ├── 错误率检查
│   ├── 响应时间检查
│   └── 资源使用检查
└── 2.4 决策：继续/回滚

阶段3: 扩大灰度（30%流量）
├── 3.1 调整流量到30%
├── 3.2 监控5-10分钟
└── 3.3 决策：继续/回滚

阶段4: 半数灰度（50%流量）
├── 4.1 调整流量到50%
├── 4.2 监控5-10分钟
└── 4.3 决策：继续/回滚

阶段5: 全量发布（100%流量）
├── 5.1 调整流量到100%
├── 5.2 下线旧版本实例
├── 5.3 最终健康检查
└── 5.4 发布完成通知
```

### 2.2 CloudTry灰度发布流程

#### 2.2.1 CI/CD自动化流程

```yaml
# .github/workflows/ci-cd.yml
deploy-production:
  steps:
    # 第一阶段：10%流量
    - name: Canary deployment (10% traffic)
      run: ./scripts/deploy/canary-deploy.sh 10
    
    - name: Monitor canary metrics (5 minutes)
      run: sleep 300
    
    # 第二阶段：50%流量
    - name: Increase canary to 50% traffic
      run: ./scripts/deploy/canary-deploy.sh 50
    
    - name: Monitor canary metrics (3 minutes)
      run: sleep 180
    
    # 第三阶段：100%流量
    - name: Promote canary to full deployment
      run: ./scripts/deploy/canary-deploy.sh 100
```

#### 2.2.2 手动执行流程

```bash
# 1. 准备阶段
# 确保监控正常
curl http://prometheus:9090/-/healthy

# 2. 初始灰度（10%）
./scripts/deploy/canary-deploy.sh 10

# 3. 观察监控（5分钟）
# 查看Grafana仪表板
# 检查错误率、响应时间

# 4. 扩大灰度（30%）
./scripts/deploy/canary-deploy.sh 30

# 5. 继续观察（5分钟）

# 6. 半数灰度（50%）
./scripts/deploy/canary-deploy.sh 50

# 7. 全量发布（100%）
./scripts/deploy/canary-deploy.sh 100
```

### 2.3 决策点说明

在每个阶段都有决策点，需要根据监控指标决定是否继续：

**继续条件**:
- 错误率 < 1%
- P99响应时间 < 2秒
- CPU使用率 < 80%
- 内存使用率 < 85%
- 无严重错误日志

**回滚条件**:
- 错误率 > 1%
- P99响应时间 > 2秒
- 出现严重错误
- 用户反馈严重问题

---

## 3. canary-deploy.sh脚本使用方法

### 3.1 脚本概述

**文件位置**: `scripts/deploy/canary-deploy.sh`
**配置文件**: `scripts/deploy/deploy-config.yml`
**日志文件**: `logs/canary-deploy.log`

**主要功能**:
- 支持流量权重控制（0-100%）
- 支持单个服务或所有服务
- 支持Docker Compose和Kubernetes
- 自动健康检查
- 监控指标验证
- 失败自动回滚
- 模拟运行模式

### 3.2 基本用法

#### 3.2.1 命令格式

```bash
./canary-deploy.sh <weight> [service_name] [options]
```

#### 3.2.2 参数说明

**必需参数**:
- `weight`: 流量权重（0-100）
  - `0`: 回滚到旧版本
  - `1-99`: 灰度发布，指定百分比流量到新版本
  - `100`: 全量发布到新版本

**可选参数**:
- `service_name`: 服务名称（可选，默认所有服务）
  - `gateway`: API网关
  - `service-order`: 订单服务
  - `service-product`: 商品服务
  - `service-user`: 用户服务

**选项参数**:
- `--dry-run`: 模拟运行，不执行实际部署操作
- `--debug`: 调试模式，输出详细日志
- `--config FILE`: 指定配置文件路径
- `--help, -h`: 显示帮助信息

### 3.3 使用示例

#### 3.3.1 基础示例

```bash
# 1. 查看帮助信息
./canary-deploy.sh --help

# 2. 10%流量到新版本（所有服务）
./canary-deploy.sh 10

# 3. 30%流量到新版本（仅订单服务）
./canary-deploy.sh 30 service-order

# 4. 50%流量到新版本
./canary-deploy.sh 50

# 5. 全量发布（100%流量）
./canary-deploy.sh 100

# 6. 回滚到旧版本（0%流量）
./canary-deploy.sh 0
```

#### 3.3.2 高级示例

```bash
# 1. 模拟运行（不执行实际操作）
./canary-deploy.sh 10 --dry-run

# 2. 调试模式（详细日志）
./canary-deploy.sh 30 service-order --debug

# 3. 使用自定义配置文件
./canary-deploy.sh 50 --config /path/to/config.yml

# 4. 组合使用
./canary-deploy.sh 30 service-order --dry-run --debug
```

### 3.4 执行流程详解

#### 3.4.1 脚本执行步骤

```
1. 参数解析
   ├── 验证权重参数（0-100）
   ├── 验证服务名称
   └── 解析选项参数

2. 加载配置
   ├── 读取deploy-config.yml
   ├── 加载服务配置
   ├── 加载监控阈值
   └── 加载健康检查配置

3. 检测部署平台
   ├── 检测Kubernetes环境
   ├── 检测Docker Compose环境
   └── 选择部署方式

4. 执行部署
   ├── 计算实例数量
   ├── 执行部署命令
   └── 等待服务启动

5. 健康检查
   ├── HTTP健康检查
   ├── 重试机制
   └── 失败处理

6. 监控检查
   ├── 检查错误率
   ├── 检查响应时间
   ├── 检查资源使用
   └── 失败自动回滚

7. 输出结果
   ├── 显示部署摘要
   ├── 记录日志
   └── 返回状态码
```

#### 3.4.2 输出示例

**成功示例**:
```
==========================================
灰度发布脚本启动
==========================================
流量权重: 10%
目标服务: 所有服务
配置文件: scripts/deploy/deploy-config.yml
日志文件: logs/canary-deploy.log
模拟运行: false
调试模式: false
==========================================

----------------------------------------
处理服务: gateway
----------------------------------------
使用Docker Compose部署 gateway (权重: 10%)
服务 gateway 实例分配:
  总实例数: 2
  旧版本实例: 2 (90%)
  新版本实例: 1 (10%)
执行健康检查: gateway (端口: 80)
✓ gateway 健康检查通过 (HTTP 200)
✓ gateway 监控指标检查通过
✓ gateway 部署成功

----------------------------------------
处理服务: service-order
----------------------------------------
...

==========================================
部署摘要
==========================================
总服务数: 4
成功数: 4
失败数: 0
✓ 所有服务部署成功
==========================================
```

**失败示例**:
```
----------------------------------------
处理服务: service-order
----------------------------------------
使用Docker Compose部署 service-order (权重: 10%)
执行健康检查: service-order (端口: 8000)
✗ service-order 健康检查失败，已重试 30 次
✗ service-order 部署失败
启动自动回滚...
✓ service-order 回滚成功

==========================================
部署摘要
==========================================
总服务数: 4
成功数: 3
失败数: 1
✗ 失败的服务: service-order
==========================================
```

---

## 4. 流量权重配置说明

### 4.1 权重计算规则

#### 4.1.1 实例数量计算

```bash
# 计算公式
新版本实例数 = ceil(总实例数 * 权重 / 100)
旧版本实例数 = 总实例数 - 新版本实例数

# 示例：总实例数=3，权重=30%
新版本实例数 = ceil(3 * 30 / 100) = ceil(0.9) = 1
旧版本实例数 = 3 - 1 = 2
```

#### 4.1.2 实例分配示例

| 总实例数 | 权重 | 新版本实例 | 旧版本实例 | 实际流量比例 |
|---------|------|-----------|-----------|-------------|
| 2 | 10% | 1 | 1 | 50% : 50% |
| 2 | 30% | 1 | 1 | 50% : 50% |
| 2 | 50% | 1 | 1 | 50% : 50% |
| 3 | 10% | 1 | 2 | 33% : 67% |
| 3 | 30% | 1 | 2 | 33% : 67% |
| 3 | 50% | 2 | 1 | 67% : 33% |
| 4 | 10% | 1 | 3 | 25% : 75% |
| 4 | 30% | 1 | 3 | 25% : 75% |
| 4 | 50% | 2 | 2 | 50% : 50% |

**注意**: 实例数量较少时，实际流量比例可能与权重不完全一致。

### 4.2 推荐权重阶段

#### 4.2.1 标准灰度阶段

```yaml
# deploy-config.yml
canary:
  stages:
    # 第一阶段：10%流量
    - weight: 10
      duration: 300        # 持续5分钟
      auto_promote: false  # 需要手动确认
      description: 初始灰度，小流量验证
      
    # 第二阶段：30%流量
    - weight: 30
      duration: 300
      auto_promote: false
      description: 扩大灰度范围
      
    # 第三阶段：50%流量
    - weight: 50
      duration: 300
      auto_promote: false
      description: 半数流量，全面验证
      
    # 第四阶段：100%流量
    - weight: 100
      duration: 0
      auto_promote: true
      description: 全量发布
```

#### 4.2.2 激进灰度阶段（高风险功能）

```yaml
canary:
  stages:
    - weight: 5
      duration: 600    # 10分钟
      auto_promote: false
      
    - weight: 15
      duration: 600
      auto_promote: false
      
    - weight: 30
      duration: 300
      auto_promote: false
      
    - weight: 50
      duration: 300
      auto_promote: false
      
    - weight: 100
      duration: 0
      auto_promote: true
```

#### 4.2.3 保守灰度阶段（关键服务）

```yaml
canary:
  stages:
    - weight: 5
      duration: 900    # 15分钟
      auto_promote: false
      
    - weight: 10
      duration: 900
      auto_promote: false
      
    - weight: 20
      duration: 600
      auto_promote: false
      
    - weight: 30
      duration: 600
      auto_promote: false
      
    - weight: 50
      duration: 300
      auto_promote: false
      
    - weight: 100
      duration: 0
      auto_promote: true
```

### 4.3 不同环境权重配置

#### 4.3.1 开发环境

```yaml
environments:
  dev:
    canary:
      stages:
        - weight: 50
          duration: 60
          auto_promote: true
        - weight: 100
          duration: 0
          auto_promote: true
```

**说明**: 开发环境快速验证，简化流程。

#### 4.3.2 预发布环境

```yaml
environments:
  staging:
    canary:
      stages:
        - weight: 10
          duration: 120
          auto_promote: false
        - weight: 50
          duration: 120
          auto_promote: false
        - weight: 100
          duration: 0
          auto_promote: true
```

**说明**: 预发布环境接近生产，需要完整验证。

#### 4.3.3 生产环境

```yaml
environments:
  prod:
    canary:
      stages:
        - weight: 10
          duration: 300
          auto_promote: false
        - weight: 30
          duration: 300
          auto_promote: false
        - weight: 50
          duration: 300
          auto_promote: false
        - weight: 100
          duration: 0
          auto_promote: true
```

**说明**: 生产环境严格验证，确保稳定。

---

## 5. 监控指标阈值配置

### 5.1 关键监控指标

#### 5.1.1 错误率

**定义**: HTTP 5xx错误占总请求的比例

```yaml
thresholds:
  error_rate: 0.01  # 1%
```

**计算公式**:
```
错误率 = (5xx请求数 / 总请求数) × 100%
```

**监控查询**（Prometheus）:
```promql
rate(http_server_requests_seconds_count{status=~"5..",service="service-order"}[5m])
/
rate(http_server_requests_seconds_count{service="service-order"}[5m])
```

**告警规则**:
- 错误率 > 1%: 触发告警
- 错误率 > 5%: 自动回滚

#### 5.1.2 响应时间

**定义**: 请求处理的耗时统计

```yaml
thresholds:
  response_time_p99: 2000  # P99响应时间2秒
  response_time_p95: 1000  # P95响应时间1秒
  response_time_avg: 500   # 平均响应时间500ms
```

**监控查询**:
```promql
# P99响应时间
histogram_quantile(0.99,
  rate(http_server_requests_seconds_bucket{service="service-order"}[5m])
)

# P95响应时间
histogram_quantile(0.95,
  rate(http_server_requests_seconds_bucket{service="service-order"}[5m])
)

# 平均响应时间
rate(http_server_requests_seconds_sum{service="service-order"}[5m])
/
rate(http_server_requests_seconds_count{service="service-order"}[5m])
```

**告警规则**:
- P99 > 2秒: 触发告警
- P99 > 5秒: 自动回滚

#### 5.1.3 资源使用率

**CPU使用率**:
```yaml
thresholds:
  cpu_usage: 80  # 80%
```

**监控查询**:
```promql
# 进程CPU使用率
process_cpu_usage{service="service-order"}

# 系统CPU使用率
100 - (avg by(instance) (irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)
```

**内存使用率**:
```yaml
thresholds:
  memory_usage: 85  # 85%
```

**监控查询**:
```promql
# JVM堆内存使用率
sum(jvm_memory_used_bytes{service="service-order",area="heap"})
/
sum(jvm_memory_max_bytes{service="service-order",area="heap"})
* 100
```

### 5.2 阈值配置建议

#### 5.2.1 按环境配置

| 指标 | 开发环境 | 预发布环境 | 生产环境 |
|------|---------|-----------|---------|
| 错误率 | 5% | 2% | 1% |
| P99响应时间 | 5秒 | 3秒 | 2秒 |
| CPU使用率 | 90% | 85% | 80% |
| 内存使用率 | 90% | 85% | 85% |

#### 5.2.2 按服务配置

**核心服务**（订单、支付）:
```yaml
thresholds:
  error_rate: 0.005      # 0.5%
  response_time_p99: 1500
  cpu_usage: 70
  memory_usage: 80
```

**非核心服务**（评论、通知）:
```yaml
thresholds:
  error_rate: 0.02       # 2%
  response_time_p99: 3000
  cpu_usage: 85
  memory_usage: 90
```

### 5.3 监控检查实现

#### 5.3.1 脚本中的监控检查

```bash
# canary-deploy.sh中的监控检查函数
check_metrics() {
    local service=$1
    
    # 1. 检查错误率
    local error_rate=$(curl -s "http://prometheus:9090/api/v1/query?query=..." | jq -r '.data.result[0].value[1]')
    
    if [ "$(echo "${error_rate} > ${ERROR_RATE_THRESHOLD}" | bc -l)" -eq 1 ]; then
        log_error "错误率过高: ${error_rate} > ${ERROR_RATE_THRESHOLD}"
        return 1
    fi
    
    # 2. 检查响应时间
    # 3. 检查CPU使用率
    # 4. 检查内存使用率
    
    return 0
}
```

#### 5.3.2 CI/CD中的监控检查

```yaml
# .github/workflows/ci-cd.yml
- name: Monitor canary metrics
  run: |
    # 检查错误率
    ERROR_RATE=$(curl -s "http://prometheus/api/v1/query?query=..." | jq -r '.data.result[0].value[1]')
    
    if (( $(echo "$ERROR_RATE > 0.01" | bc -l) )); then
      echo "::error::Error rate too high: ${ERROR_RATE}"
      exit 1
    fi
```

---

## 6. 健康检查机制说明

### 6.1 健康检查配置

#### 6.1.1 配置参数

```yaml
health_check:
  endpoint: /actuator/health    # 健康检查端点
  timeout: 10                   # 超时时间（秒）
  max_retries: 30               # 最大重试次数
  retry_interval: 5             # 重试间隔（秒）
  startup_wait: 60              # 启动等待时间（秒）
  success_threshold: 1          # 成功阈值
  failure_threshold: 3          # 失败阈值
```

#### 6.1.2 健康检查端点

**Spring Boot Actuator端点**:

```
/actuator/health              # 综合健康检查
/actuator/health/liveness     # 存活探针
/actuator/health/readiness    # 就绪探针
```

**响应示例**:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    },
    "redis": {
      "status": "UP"
    },
    "nacos": {
      "status": "UP"
    }
  }
}
```

### 6.2 健康检查流程

#### 6.2.1 标准流程

```
1. 服务启动
   └── 等待 startup_wait 秒

2. 开始健康检查
   ├── 发送HTTP请求到 /actuator/health
   ├── 检查响应状态码
   │   ├── 200: 检查通过
   │   └── 非200: 检查失败
   └── 解析响应内容
       ├── status: UP → 成功
       └── status: DOWN → 失败

3. 重试机制
   ├── 失败后等待 retry_interval 秒
   ├── 重试次数 +1
   └── 达到 max_retries 次后判定为失败

4. 结果判定
   ├── 连续成功 success_threshold 次 → 健康
   └── 连续失败 failure_threshold 次 → 不健康
```

#### 6.2.2 健康检查脚本实现

```bash
health_check() {
    local service=$1
    local port=$2
    local max_retries=${3:-30}
    local retry_interval=${4:-5}
    
    log_info "执行健康检查: ${service}"
    
    local retry=0
    local health_url="http://localhost:${port}/actuator/health"
    
    while [ $retry -lt $max_retries ]; do
        # 执行健康检查
        local http_code=$(curl -sf -o /dev/null -w "%{http_code}" --max-time 10 "$health_url" 2>/dev/null)
        
        if [ "$http_code" = "200" ]; then
            log_success "${service} 健康检查通过"
            return 0
        fi
        
        retry=$((retry + 1))
        
        if [ $retry -lt $max_retries ]; then
            log_warn "健康检查失败，${retry_interval}秒后重试 (${retry}/${max_retries})"
            sleep $retry_interval
        fi
    done
    
    log_error "${service} 健康检查失败"
    return 1
}
```

### 6.3 不同服务的健康检查

#### 6.3.1 服务端口配置

```yaml
services:
  gateway:
    port: 80
    health_check: /actuator/health
    
  service-order:
    port: 8000
    health_check: /actuator/health
    
  service-product:
    port: 9000
    health_check: /actuator/health
    
  service-user:
    port: 7000
    health_check: /actuator/health
```

#### 6.3.2 批量健康检查

```bash
# 检查所有服务
health_check_all() {
    local services=("gateway" "service-order" "service-product" "service-user")
    local failed_services=()
    
    for service in "${services[@]}"; do
        local port=$(get_service_port "$service")
        
        if ! health_check "$service" "$port"; then
            failed_services+=("$service")
        fi
    done
    
    if [ ${#failed_services[@]} -gt 0 ]; then
        log_error "以下服务健康检查失败: ${failed_services[*]}"
        return 1
    fi
    
    log_success "所有服务健康检查通过"
    return 0
}
```

---

## 7. 自动回滚触发条件

### 7.1 自动回滚场景

#### 7.1.1 健康检查失败

**触发条件**:
- 服务启动超时
- 健康检查连续失败30次
- 健康检查返回非200状态码

**回滚流程**:
```bash
if ! health_check "$service" "$port"; then
    log_error "${service} 健康检查失败，启动回滚..."
    deploy "$service" 0  # 回滚到旧版本
    return 1
fi
```

#### 7.1.2 监控指标异常

**触发条件**:
- 错误率 > 1%
- P99响应时间 > 2秒
- CPU使用率 > 80%
- 内存使用率 > 85%

**回滚流程**:
```bash
if ! check_metrics "$service"; then
    log_error "${service} 监控指标异常，启动回滚..."
    deploy "$service" 0
    return 1
fi
```

#### 7.1.3 服务注册失败

**触发条件**:
- 服务未注册到Nacos
- 服务实例数为0
- 服务状态异常

**回滚流程**:
```bash
if ! check_service_registration "$service"; then
    log_error "${service} 服务注册失败，启动回滚..."
    deploy "$service" 0
    return 1
fi
```

### 7.2 回滚配置

#### 7.2.1 回滚参数

```yaml
rollback:
  auto_rollback: true           # 启用自动回滚
  keep_versions: 3              # 保留最近3个版本
  timeout: 300                  # 回滚超时时间（秒）
  wait_before_rollback: 10      # 回滚前等待时间（秒）
  health_check_timeout: 120     # 回滚后健康检查超时（秒）
  alert_on_failure: true        # 回滚失败时告警
```

#### 7.2.2 回滚策略

**立即回滚**:
- 严重错误（服务不可用）
- 数据丢失风险
- 安全漏洞

**延迟回滚**:
- 性能下降
- 非关键功能异常
- 用户体验问题

### 7.3 回滚执行流程

```
1. 检测到异常
   ├── 健康检查失败
   ├── 监控指标异常
   └── 服务注册失败

2. 触发回滚
   ├── 记录异常信息
   ├── 发送告警通知
   └── 启动回滚流程

3. 执行回滚
   ├── 停止新版本服务
   ├── 启动旧版本服务
   └── 更新流量路由

4. 验证回滚
   ├── 健康检查
   ├── 监控检查
   └── 服务注册检查

5. 回滚完成
   ├── 记录回滚日志
   ├── 发送通知
   └── 保留现场数据
```

---

## 8. 灰度发布最佳实践

### 8.1 发布前准备

#### 8.1.1 代码审查

- [ ] 代码审查通过
- [ ] 单元测试覆盖率达标
- [ ] 集成测试通过
- [ ] 性能测试通过
- [ ] 安全扫描通过

#### 8.1.2 配置检查

- [ ] 数据库迁移脚本准备
- [ ] 配置文件更新
- [ ] 环境变量设置
- [ ] Secrets配置正确

#### 8.1.3 监控准备

- [ ] 监控仪表板配置
- [ ] 告警规则设置
- [ ] 日志收集配置
- [ ] 链路追踪配置

#### 8.1.4 回滚准备

- [ ] 回滚脚本测试
- [ ] 回滚流程演练
- [ ] 数据备份完成
- [ ] 回滚通知准备

### 8.2 发布时间选择

#### 8.2.1 推荐时间

**最佳发布时间**:
- 工作日 10:00-16:00
- 避开业务高峰期
- 有足够时间处理问题

**避免时间**:
- 周五下午（周末无人值守）
- 节假日前
- 业务高峰期
- 深夜（响应不及时）

#### 8.2.2 紧急发布

**紧急发布场景**:
- 严重bug修复
- 安全漏洞修补
- 服务不可用恢复

**紧急发布流程**:
1. 技术负责人审批
2. 缩短灰度时间
3. 加强监控
4. 准备快速回滚

### 8.3 监控和观察

#### 8.3.1 关键监控指标

**应用指标**:
- 请求成功率
- 响应时间（P50, P95, P99）
- QPS
- 错误日志

**系统指标**:
- CPU使用率
- 内存使用率
- 磁盘IO
- 网络IO

**业务指标**:
- 订单创建成功率
- 支付成功率
- 用户活跃度

#### 8.3.2 观察要点

**每个阶段观察**:
1. 错误率是否正常
2. 响应时间是否稳定
3. 资源使用是否合理
4. 是否有异常日志
5. 用户反馈是否正常

**对比分析**:
- 新旧版本指标对比
- 不同时间段对比
- 不同用户群体对比

### 8.4 问题处理

#### 8.4.1 问题分级

**P0（严重）**:
- 服务完全不可用
- 数据丢失
- 安全漏洞
- **处理**: 立即回滚

**P1（重要）**:
- 性能严重下降
- 部分功能不可用
- **处理**: 暂停发布，评估影响

**P2（一般）**:
- 小问题
- 非关键功能异常
- **处理**: 记录问题，继续观察

#### 8.4.2 问题处理流程

```
发现问题
├── 记录问题详情
│   ├── 时间
│   ├── 现象
│   ├── 日志
│   └── 影响范围
├── 评估问题严重性
│   ├── P0: 立即回滚
│   ├── P1: 暂停发布
│   └── P2: 继续观察
├── 通知相关人员
│   ├── 开发团队
│   ├── 运维团队
│   └── 业务团队
└── 执行处理方案
    ├── 回滚
    ├── 修复
    └── 继续发布
```

### 8.5 团队协作

#### 8.5.1 角色分工

**发布负责人**:
- 制定发布计划
- 协调各方资源
- 决策关键问题

**开发团队**:
- 代码准备
- 问题修复
- 技术支持

**运维团队**:
- 环境准备
- 监控配置
- 发布执行

**测试团队**:
- 测试验证
- 问题发现
- 质量保障

#### 8.5.2 沟通机制

**发布前**:
- 发布计划评审会
- 风险评估
- 准备情况确认

**发布中**:
- 实时沟通群
- 定时状态更新
- 问题快速响应

**发布后**:
- 发布总结会
- 问题复盘
- 经验分享

---

## 9. 故障排查指南

### 9.1 常见问题

#### 9.1.1 灰度发布失败

**症状**:
```
✗ service-order 健康检查失败，启动回滚...
```

**排查步骤**:
```bash
# 1. 查看服务日志
docker logs service-order

# 2. 检查服务状态
docker ps -a | grep service-order

# 3. 查看健康检查日志
curl http://localhost:8000/actuator/health

# 4. 检查依赖服务
# 数据库、Redis、Nacos等

# 5. 查看灰度发布日志
cat logs/canary-deploy.log
```

**解决方案**:
- 修复配置错误
- 启动依赖服务
- 调整健康检查参数
- 回滚到稳定版本

#### 9.1.2 流量分配不均

**症状**:
- 实际流量比例与预期不符
- 新版本流量过多或过少

**排查步骤**:
```bash
# 1. 检查实例数量
docker ps | grep service-order

# 2. 检查负载均衡配置
# Nginx/Gateway配置

# 3. 查看流量统计
# Prometheus查询

# 4. 检查服务注册
curl http://nacos:8848/nacos/v1/ns/instance/list?serviceName=service-order
```

**解决方案**:
- 调整实例数量
- 修改负载均衡权重
- 检查服务注册配置

#### 9.1.3 监控指标异常

**症状**:
```
✗ 错误率过高: 0.05 > 0.01
```

**排查步骤**:
```bash
# 1. 查看错误日志
docker logs service-order | grep ERROR

# 2. 检查错误类型
# 数据库错误、网络错误、业务错误

# 3. 查看调用链路
# Zipkin/Jaeger

# 4. 检查依赖服务
# 数据库、Redis、其他服务

# 5. 对比新旧版本
# 查看旧版本是否有相同问题
```

**解决方案**:
- 修复代码bug
- 调整配置参数
- 扩容资源
- 回滚到旧版本

### 9.2 日志查看

#### 9.2.1 灰度发布日志

```bash
# 查看完整日志
cat logs/canary-deploy.log

# 实时查看日志
tail -f logs/canary-deploy.log

# 搜索错误日志
grep ERROR logs/canary-deploy.log

# 查看特定服务的日志
grep "service-order" logs/canary-deploy.log
```

#### 9.2.2 服务日志

```bash
# Docker服务日志
docker logs service-order

# 实时查看
docker logs -f service-order

# 查看最近100行
docker logs --tail 100 service-order

# 查看指定时间段
docker logs --since 2026-05-13T10:00:00 service-order
```

#### 9.2.3 系统日志

```bash
# 系统日志
journalctl -u docker

# 内核日志
dmesg | grep docker

# 审计日志
ausearch -m avc -ts recent
```

### 9.3 性能分析

#### 9.3.1 CPU分析

```bash
# 查看CPU使用率
top -p $(pgrep -f service-order)

# 生成线程dump
jstack <pid> > thread_dump.txt

# 分析CPU热点
perf top -p <pid>
```

#### 9.3.2 内存分析

```bash
# 查看内存使用
jmap -heap <pid>

# 生成堆dump
jmap -dump:format=b,file=heap.hprof <pid>

# 分析内存泄漏
jhat heap.hprof
```

#### 9.3.3 网络分析

```bash
# 查看网络连接
netstat -tlnp | grep 8000

# 抓包分析
tcpdump -i eth0 port 8000 -w capture.pcap

# 查看连接状态
ss -tunap | grep 8000
```

### 9.4 回滚操作

#### 9.4.1 手动回滚

```bash
# 回滚到上一版本
./scripts/deploy/rollback.sh rollback --service service-order

# 回滚到指定版本
./scripts/deploy/rollback.sh rollback --service service-order --version v1.2.3

# 强制回滚（跳过确认）
./scripts/deploy/rollback.sh rollback --service service-order --force
```

#### 9.4.2 紧急回滚

```bash
# 1. 立即停止新版本
docker stop service-order-new

# 2. 启动旧版本
docker start service-order-old

# 3. 更新流量路由
# 修改Nginx/Gateway配置

# 4. 验证服务
curl http://localhost:8000/actuator/health

# 5. 通知团队
# 发送回滚通知
```

---

## 附录

### A. 配置文件示例

完整的配置文件请参考: [deploy-config.yml](../../scripts/deploy/deploy-config.yml)

### B. 相关文档

- [CI/CD使用指南](./CI_CD_GUIDE.md)
- [回滚操作指南](./ROLLBACK_GUIDE.md)
- [快速参考](./QUICK_REFERENCE.md)

### C. 常用命令速查

```bash
# 灰度发布
./canary-deploy.sh 10                    # 10%流量
./canary-deploy.sh 30 service-order      # 30%流量（指定服务）
./canary-deploy.sh 100                   # 全量发布
./canary-deploy.sh 0                     # 回滚

# 查看日志
tail -f logs/canary-deploy.log           # 实时日志
docker logs -f service-order             # 服务日志

# 健康检查
curl http://localhost:8000/actuator/health

# 回滚
./rollback.sh rollback --service service-order
```

### D. 故障排查清单

- [ ] 检查服务日志
- [ ] 检查健康检查端点
- [ ] 检查依赖服务状态
- [ ] 检查监控指标
- [ ] 检查资源使用情况
- [ ] 检查网络连接
- [ ] 检查配置文件
- [ ] 检查版本兼容性

---

**文档维护**: DevOps团队
**最后审核**: 2026-05-13
