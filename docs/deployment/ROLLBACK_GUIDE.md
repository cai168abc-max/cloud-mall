# 回滚操作指南

> **文档版本**: 2.0
> **最后更新**: 2026-05-13
> **适用场景**: 服务故障恢复、版本回退、紧急修复

---

## 目录

1. [回滚机制概述](#1-回滚机制概述)
2. [版本管理说明](#2-版本管理说明)
3. [rollback.sh脚本使用方法](#3-rollbacksh脚本使用方法)
4. [手动回滚操作步骤](#4-手动回滚操作步骤)
5. [自动回滚配置](#5-自动回滚配置)
6. [监控指标检查说明](#6-监控指标检查说明)
7. [健康检查机制](#7-健康检查机制)
8. [回滚最佳实践](#8-回滚最佳实践)
9. [故障排查指南](#9-故障排查指南)

---

## 1. 回滚机制概述

### 1.1 什么是回滚

回滚（Rollback）是指将系统从当前版本恢复到之前稳定版本的操作。当新版本出现严重问题或不符合预期时，通过回滚快速恢复服务。

```
当前状态: v1.3.0 (有问题)
          ↓ 回滚
目标状态: v1.2.0 (稳定版本)
```

### 1.2 回滚类型

#### 1.2.1 按触发方式分类

| 类型 | 触发方式 | 响应时间 | 适用场景 |
|------|---------|---------|---------|
| 手动回滚 | 人工操作 | 分钟级 | 计划内回滚、问题确认后回滚 |
| 自动回滚 | 监控触发 | 秒级 | 严重故障、指标异常 |
| 定时回滚 | 定时任务 | 小时级 | 临时版本、测试版本 |

#### 1.2.2 按回滚范围分类

| 类型 | 范围 | 影响面 | 操作复杂度 |
|------|------|--------|-----------|
| 单服务回滚 | 单个服务 | 小 | 低 |
| 多服务回滚 | 多个服务 | 中 | 中 |
| 全系统回滚 | 所有服务 | 大 | 高 |

### 1.3 回滚策略

#### 1.3.1 快速回滚

**适用场景**:
- 服务完全不可用
- 严重数据错误
- 安全漏洞

**特点**:
- 立即执行
- 跳过确认步骤
- 优先恢复服务

#### 1.3.2 安全回滚

**适用场景**:
- 性能下降
- 非关键功能异常
- 用户体验问题

**特点**:
- 先备份数据
- 逐步执行
- 验证每个步骤

### 1.4 CloudTry回滚架构

```
┌─────────────────────────────────────────────────────────────┐
│                    回滚系统架构                               │
└─────────────────────────────────────────────────────────────┘

监控系统
├── Prometheus (指标采集)
├── Grafana (可视化)
└── AlertManager (告警)
        │
        ├── 指标异常 ──┐
        │              │
        └── 健康检查失败 ─┤
                       │
                       ↓
              自动回滚触发器
                       │
                       ↓
              ┌────────────────┐
              │  rollback.sh   │
              │  回滚脚本       │
              └────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
        ↓              ↓              ↓
   版本管理        健康检查       监控验证
        │              │              │
        └──────────────┼──────────────┘
                       │
                       ↓
              服务恢复完成
```

---

## 2. 版本管理说明

### 2.1 版本命名规范

#### 2.1.1 版本ID格式

```
vYYYYMMDD_HHMMSS

示例:
v20260513_143000
```

**组成**:
- `v`: 版本前缀
- `YYYYMMDD`: 日期（年月日）
- `HHMMSS`: 时间（时分秒）

#### 2.1.2 Docker镜像标签

```
格式1: YYYYMMDD-git-sha
示例: 20260513-abc1234

格式2: latest
说明: 最新版本
```

### 2.2 版本存储结构

#### 2.2.1 目录结构

```
deployments/
└── versions/
    ├── gateway/
    │   ├── v20260513_143000.json
    │   ├── v20260513_120000.json
    │   ├── v20260512_180000.json
    │   └── current.json
    ├── service-order/
    │   ├── v20260513_143000.json
    │   ├── v20260513_120000.json
    │   └── current.json
    ├── service-product/
    │   └── ...
    └── service-user/
        └── ...
```

#### 2.2.2 版本记录文件

**文件名**: `v20260513_143000.json`

**内容示例**:
```json
{
    "version": "v20260513_143000",
    "service": "service-order",
    "timestamp": "2026-05-13 14:30:00",
    "deployer": "zhangsan",
    "git": {
        "sha": "abc1234def567890",
        "branch": "main",
        "message": "feat: 添加订单超时自动取消功能"
    },
    "image": {
        "tag": "20260513-abc1234",
        "registry": "docker.io/username"
    },
    "status": "deployed",
    "rollback_count": 0
}
```

### 2.3 版本管理操作

#### 2.3.1 查看版本列表

```bash
# 查看所有服务的版本列表
./scripts/deploy/rollback.sh list

# 查看指定服务的版本列表
./scripts/deploy/rollback.sh list --service service-order
```

**输出示例**:
```
==========================================
版本列表 - service-order
==========================================
  v20260513_143000 [当前版本]
    时间: 2026-05-13 14:30:00
    部署者: zhangsan
    Git SHA: abc1234

  v20260513_120000
    时间: 2026-05-13 12:00:00
    部署者: lisi
    Git SHA: def5678

  v20260512_180000
    时间: 2026-05-12 18:00:00
    部署者: wangwu
    Git SHA: 1234567
```

#### 2.3.2 查看当前版本

```bash
# 查看所有服务的当前版本
./scripts/deploy/rollback.sh current

# 查看指定服务的当前版本
./scripts/deploy/rollback.sh current --service service-order
```

**输出示例**:
```
==========================================
当前版本 - service-order
==========================================
  "version": "v20260513_143000",
  "service": "service-order",
  "timestamp": "2026-05-13 14:30:00",
  "deployer": "zhangsan",
  "git": {
    "sha": "abc1234def567890",
    "branch": "main",
    "message": "feat: 添加订单超时自动取消功能"
  },
  "image": {
    "tag": "20260513-abc1234",
    "registry": "docker.io/username"
  },
  "status": "deployed",
  "rollback_count": 0
```

#### 2.3.3 比较版本

```bash
# 比较两个版本
./scripts/deploy/rollback.sh compare v20260512_180000 v20260513_143000

# 比较指定服务的版本
./scripts/deploy/rollback.sh compare v1.2.0 v1.3.0 --service service-order
```

**输出示例**:
```
==========================================
版本比较 - service-order
==========================================
版本 v20260512_180000:
  Git SHA: 1234567
  镜像标签: 20260512-1234567
  部署时间: 2026-05-12 18:00:00

版本 v20260513_143000:
  Git SHA: abc1234
  镜像标签: 20260513-abc1234
  部署时间: 2026-05-13 14:30:00
```

#### 2.3.4 清理旧版本

```bash
# 清理所有服务的旧版本（保留最近3个版本）
./scripts/deploy/rollback.sh cleanup

# 清理指定服务的旧版本
./scripts/deploy/rollback.sh cleanup --service service-order
```

**输出示例**:
```
==========================================
清理旧版本 - service-order
==========================================
已删除版本: v20260510_100000
已删除版本: v20260509_150000
清理完成，删除了 2 个旧版本
```

### 2.4 版本保留策略

#### 2.4.1 默认保留策略

```yaml
rollback:
  keep_versions: 3  # 保留最近3个版本
```

**说明**:
- 保留最近N个版本
- 当前版本不会被删除
- 旧版本自动清理

#### 2.4.2 自定义保留策略

**按时间保留**:
```bash
# 保留最近7天的版本
find deployments/versions/service-order -name "*.json" -mtime +7 -delete
```

**按数量保留**:
```bash
# 保留最近5个版本
ls -t deployments/versions/service-order/*.json | tail -n +6 | xargs rm -f
```

---

## 3. rollback.sh脚本使用方法

### 3.1 脚本概述

**文件位置**: `scripts/deploy/rollback.sh`
**配置文件**: `scripts/deploy/deploy-config.yml`
**日志文件**: `deployments/logs/rollback.log`

**主要功能**:
- 版本管理（列表、查看、比较、清理）
- 手动回滚（到上一版本或指定版本）
- 自动回滚（基于监控指标）
- 健康检查和服务注册检查
- 监控指标检查
- 日志记录和调试模式

### 3.2 命令格式

```bash
./rollback.sh [命令] [选项]
```

### 3.3 支持的命令

#### 3.3.1 rollback - 执行回滚

**功能**: 回滚到指定版本或上一版本

**用法**:
```bash
# 回滚到上一版本（所有服务）
./rollback.sh rollback

# 回滚指定服务到上一版本
./rollback.sh rollback --service service-order

# 回滚到指定版本
./rollback.sh rollback --service service-order --version v1.2.3

# 自动回滚（基于监控指标）
./rollback.sh rollback --auto

# 强制回滚（跳过确认）
./rollback.sh rollback --force
```

#### 3.3.2 list - 列出版本

**功能**: 列出所有可用版本

**用法**:
```bash
# 列出所有服务的版本
./rollback.sh list

# 列出指定服务的版本
./rollback.sh list --service service-order
```

#### 3.3.3 current - 显示当前版本

**功能**: 显示当前版本信息

**用法**:
```bash
# 显示所有服务的当前版本
./rollback.sh current

# 显示指定服务的当前版本
./rollback.sh current --service service-order
```

#### 3.3.4 cleanup - 清理旧版本

**功能**: 清理旧版本，保留最近N个版本

**用法**:
```bash
# 清理所有服务的旧版本
./rollback.sh cleanup

# 清理指定服务的旧版本
./rollback.sh cleanup --service service-order
```

#### 3.3.5 compare - 比较版本

**功能**: 比较两个版本的差异

**用法**:
```bash
# 比较两个版本
./rollback.sh compare v1.2.0 v1.3.0

# 比较指定服务的版本
./rollback.sh compare v1.2.0 v1.3.0 --service service-order
```

### 3.4 支持的选项

| 选项 | 说明 | 示例 |
|------|------|------|
| `--service <name>` | 指定服务名称 | `--service service-order` |
| `--version <version>` | 指定回滚版本 | `--version v1.2.3` |
| `--auto` | 自动模式（基于监控指标） | `--auto` |
| `--force` | 强制回滚（跳过确认） | `--force` |
| `--dry-run` | 模拟运行（不执行实际操作） | `--dry-run` |
| `--debug` | 调试模式（显示详细日志） | `--debug` |
| `-h, --help` | 显示帮助信息 | `--help` |

### 3.5 使用示例

#### 3.5.1 基础示例

```bash
# 1. 查看帮助
./rollback.sh --help

# 2. 查看版本列表
./rollback.sh list

# 3. 查看当前版本
./rollback.sh current --service service-order

# 4. 回滚到上一版本
./rollback.sh rollback --service service-order

# 5. 回滚到指定版本
./rollback.sh rollback --service service-order --version v20260512_180000
```

#### 3.5.2 高级示例

```bash
# 1. 自动回滚（基于监控指标）
./rollback.sh rollback --auto

# 2. 强制回滚（跳过确认）
./rollback.sh rollback --service service-order --force

# 3. 模拟运行
./rollback.sh rollback --service service-order --dry-run

# 4. 调试模式
./rollback.sh rollback --service service-order --debug

# 5. 组合使用
./rollback.sh rollback --service service-order --version v1.2.3 --force --debug
```

### 3.6 执行流程详解

#### 3.6.1 回滚执行流程

```
1. 参数解析
   ├── 验证服务名称
   ├── 验证版本号
   └── 解析选项参数

2. 初始化
   ├── 创建版本目录
   ├── 加载配置文件
   └── 初始化日志

3. 获取版本信息
   ├── 获取当前版本
   ├── 获取目标版本
   └── 验证目标版本

4. 确认回滚
   ├── 显示回滚信息
   ├── 等待用户确认
   └── 强制模式跳过确认

5. 执行回滚
   ├── 停止当前版本
   ├── 切换到目标版本
   ├── 启动目标版本
   └── 更新版本记录

6. 验证回滚
   ├── 健康检查
   ├── 服务注册检查
   └── 监控指标检查

7. 输出结果
   ├── 显示回滚摘要
   ├── 记录日志
   └── 返回状态码
```

#### 3.6.2 输出示例

**成功示例**:
```
==========================================
自动化回滚脚本 v1.0.0
==========================================

==========================================
开始回滚流程 - service-order
==========================================

即将回滚服务: service-order
目标版本: v20260512_180000

确认执行回滚? (yes/no): yes

==========================================
执行回滚 - service-order
目标版本: v20260512_180000
==========================================
当前版本: v20260513_143000
目标版本验证通过: v20260512_180000
开始回滚...
停止当前版本...
切换到目标版本...
更新版本记录...
回滚操作完成

==========================================
回滚验证 - service-order
==========================================
执行健康检查: service-order
健康检查通过: service-order
服务注册状态正常: service-order (2 个实例)

==========================================
监控指标检查 - service-order
==========================================
错误率检查通过: 0.005 <= 0.01
响应时间检查通过: 1500ms <= 2000ms
CPU使用率检查通过: 65% <= 80%
内存使用率检查通过: 70% <= 85%
所有监控检查通过

回滚验证完成

==========================================
回滚成功完成 - service-order
==========================================
```

**失败示例**:
```
==========================================
执行回滚 - service-order
目标版本: v20260512_180000
==========================================
当前版本: v20260513_143000
目标版本验证通过: v20260512_180000
开始回滚...
停止当前版本...
切换到目标版本...
更新版本记录...
回滚操作完成

==========================================
回滚验证 - service-order
==========================================
执行健康检查: service-order
✗ 健康检查失败: service-order (尝试 30 次)

==========================================
回滚验证失败
==========================================
```

---

## 4. 手动回滚操作步骤

### 4.1 准备阶段

#### 4.1.1 确认回滚需求

**问题确认**:
- [ ] 确认问题确实存在
- [ ] 确认问题由新版本引起
- [ ] 评估问题影响范围
- [ ] 确认回滚可以解决问题

**决策流程**:
```
发现问题
├── 记录问题详情
├── 评估严重程度
│   ├── P0: 立即回滚
│   ├── P1: 尽快回滚
│   └── P2: 评估后决定
└── 通知相关人员
    ├── 开发团队
    ├── 运维团队
    └── 业务团队
```

#### 4.1.2 选择目标版本

**版本选择原则**:
1. 优先选择上一个稳定版本
2. 避免选择已知有问题的版本
3. 考虑数据兼容性
4. 考虑配置兼容性

**查看可用版本**:
```bash
# 查看版本列表
./rollback.sh list --service service-order

# 查看当前版本
./rollback.sh current --service service-order

# 比较版本差异
./rollback.sh compare v1.2.0 v1.3.0 --service service-order
```

#### 4.1.3 备份当前状态

**数据备份**:
```bash
# 备份数据库
mysqldump -u root -p cloudtry_order > backup_$(date +%Y%m%d_%H%M%S).sql

# 备份配置文件
cp -r services/service-order/src/main/resources backup_config/

# 备份Docker镜像
docker save username/cloudtry-service-order:latest -o service-order-latest.tar
```

**状态记录**:
```bash
# 记录当前服务状态
docker ps > current_status.txt
curl http://localhost:8000/actuator/health > current_health.txt

# 记录监控指标
curl http://prometheus:9090/api/v1/query?query=up > current_metrics.txt
```

### 4.2 执行回滚

#### 4.2.1 单服务回滚

**步骤1: 查看当前状态**
```bash
# 查看当前版本
./rollback.sh current --service service-order

# 查看服务状态
docker ps | grep service-order

# 查看服务日志
docker logs service-order --tail 100
```

**步骤2: 执行回滚**
```bash
# 方式1: 使用脚本回滚到上一版本
./rollback.sh rollback --service service-order

# 方式2: 回滚到指定版本
./rollback.sh rollback --service service-order --version v20260512_180000

# 方式3: 强制回滚（跳过确认）
./rollback.sh rollback --service service-order --force
```

**步骤3: 验证回滚结果**
```bash
# 健康检查
curl http://localhost:8000/actuator/health

# 查看服务日志
docker logs service-order --tail 50

# 检查服务注册
curl http://nacos:8848/nacos/v1/ns/instance/list?serviceName=service-order

# 检查监控指标
# 访问Grafana仪表板
```

#### 4.2.2 多服务回滚

**步骤1: 确定回滚顺序**

**推荐顺序**:
```
1. 回滚依赖服务
   ├── service-product
   └── service-user

2. 回滚核心服务
   └── service-order

3. 回滚网关
   └── gateway
```

**步骤2: 逐个回滚**
```bash
# 1. 回滚商品服务
./rollback.sh rollback --service service-product

# 2. 回滚用户服务
./rollback.sh rollback --service service-user

# 3. 回滚订单服务
./rollback.sh rollback --service service-order

# 4. 回滚网关
./rollback.sh rollback --service gateway
```

**步骤3: 验证整体状态**
```bash
# 检查所有服务健康状态
for service in gateway service-order service-product service-user; do
    echo "Checking $service..."
    curl http://localhost:${port}/actuator/health
done

# 检查服务间调用
curl http://localhost:80/api/order/list

# 检查监控仪表板
# 访问Grafana
```

#### 4.2.3 全系统回滚

**步骤1: 停止所有服务**
```bash
# 停止所有服务
docker-compose down

# 或者逐个停止
docker stop gateway service-order service-product service-user
```

**步骤2: 回滚所有服务**
```bash
# 使用脚本回滚所有服务
./rollback.sh rollback

# 或者逐个回滚
for service in gateway service-order service-product service-user; do
    ./rollback.sh rollback --service $service --force
done
```

**步骤3: 启动所有服务**
```bash
# 启动所有服务
docker-compose up -d

# 或者逐个启动
docker start gateway service-order service-product service-user
```

**步骤4: 验证系统状态**
```bash
# 检查所有服务状态
docker ps

# 检查服务注册
curl http://nacos:8848/nacos/v1/ns/service/list

# 执行集成测试
./run_integration_tests.sh
```

### 4.3 回滚后操作

#### 4.3.1 监控观察

**监控指标**:
- 错误率
- 响应时间
- QPS
- 资源使用率

**观察时间**:
- 至少观察30分钟
- 关键业务观察2小时
- 高峰期观察1个周期

#### 4.3.2 通知团队

**通知内容**:
```
回滚通知

服务: service-order
回滚时间: 2026-05-13 15:00:00
回滚版本: v20260512_180000
回滚原因: 新版本存在严重性能问题
当前状态: 服务已恢复正常

请各位知悉，如有问题及时反馈。
```

#### 4.3.3 问题复盘

**复盘内容**:
- 问题原因分析
- 影响范围评估
- 回滚过程记录
- 改进措施制定

---

## 5. 自动回滚配置

### 5.1 自动回滚触发条件

#### 5.1.1 监控指标触发

**错误率触发**:
```yaml
thresholds:
  error_rate: 0.01  # 错误率 > 1% 触发回滚
```

**响应时间触发**:
```yaml
thresholds:
  response_time_p99: 2000  # P99响应时间 > 2秒 触发回滚
```

**资源使用触发**:
```yaml
thresholds:
  cpu_usage: 80      # CPU使用率 > 80% 触发告警
  memory_usage: 85   # 内存使用率 > 85% 触发告警
```

#### 5.1.2 健康检查触发

**服务不可用**:
- 健康检查连续失败30次
- 服务启动超时
- 服务进程崩溃

**服务注册失败**:
- 服务未注册到Nacos
- 服务实例数为0
- 服务状态异常

#### 5.1.3 自定义触发条件

**业务指标触发**:
```bash
# 订单创建成功率 < 95%
ORDER_SUCCESS_RATE=$(curl -s "http://prometheus:9090/api/v1/query?query=..." | jq -r '.data.result[0].value[1]')

if [ "$(echo "${ORDER_SUCCESS_RATE} < 0.95" | bc -l)" -eq 1 ]; then
    echo "订单成功率过低，触发回滚"
    ./rollback.sh rollback --service service-order --auto
fi
```

### 5.2 自动回滚配置

#### 5.2.1 配置文件

```yaml
# deploy-config.yml
rollback:
  # 启用自动回滚
  auto_rollback: true
  
  # 保留的历史版本数
  keep_versions: 3
  
  # 回滚超时时间（秒）
  timeout: 300
  
  # 回滚前等待时间（秒）
  wait_before_rollback: 10
  
  # 回滚后健康检查超时（秒）
  health_check_timeout: 120
  
  # 回滚失败时告警
  alert_on_failure: true
```

#### 5.2.2 监控告警配置

**Prometheus告警规则**:
```yaml
# prometheus_rules.yml
groups:
  - name: auto_rollback
    rules:
      - alert: HighErrorRate
        expr: |
          rate(http_server_requests_seconds_count{status=~"5.."}[5m])
          /
          rate(http_server_requests_seconds_count[5m]) > 0.01
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value }}"
      
      - alert: HighResponseTime
        expr: |
          histogram_quantile(0.99,
            rate(http_server_requests_seconds_bucket[5m])
          ) > 2
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High response time detected"
          description: "P99 response time is {{ $value }}s"
```

**AlertManager配置**:
```yaml
# alertmanager.yml
route:
  receiver: 'auto-rollback'
  routes:
    - match:
        severity: critical
      receiver: 'auto-rollback'

receivers:
  - name: 'auto-rollback'
    webhook_configs:
      - url: 'http://rollback-service:8080/api/rollback'
        send_resolved: true
```

#### 5.2.3 自动回滚服务

**实现方式**:
```python
# rollback_service.py
from flask import Flask, request
import subprocess
import logging

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)

@app.route('/api/rollback', methods=['POST'])
def trigger_rollback():
    data = request.json
    
    # 解析告警信息
    alerts = data.get('alerts', [])
    
    for alert in alerts:
        service = alert['labels'].get('service')
        severity = alert['labels'].get('severity')
        
        if severity == 'critical':
            logging.info(f"Triggering auto rollback for {service}")
            
            # 执行回滚
            result = subprocess.run(
                ['./scripts/deploy/rollback.sh', 'rollback', '--service', service, '--auto', '--force'],
                capture_output=True,
                text=True
            )
            
            if result.returncode == 0:
                logging.info(f"Rollback successful for {service}")
            else:
                logging.error(f"Rollback failed for {service}: {result.stderr}")
    
    return 'OK', 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)
```

### 5.3 自动回滚流程

```
监控指标异常
├── Prometheus采集指标
├── 触发告警规则
└── 发送告警到AlertManager
        │
        ↓
AlertManager处理
├── 路由告警
├── 发送Webhook
└── 调用回滚服务
        │
        ↓
回滚服务执行
├── 解析告警信息
├── 确认回滚条件
└── 调用rollback.sh
        │
        ↓
执行回滚
├── 停止当前版本
├── 启动旧版本
└── 验证服务状态
        │
        ↓
发送通知
├── 邮件通知
├── 钉钉/企业微信通知
└── 记录回滚日志
```

---

## 6. 监控指标检查说明

### 6.1 监控指标体系

#### 6.1.1 应用指标

**错误率**:
```promql
# 计算公式
错误率 = (5xx请求数 / 总请求数) × 100%

# Prometheus查询
rate(http_server_requests_seconds_count{status=~"5..",service="service-order"}[5m])
/
rate(http_server_requests_seconds_count{service="service-order"}[5m])
```

**响应时间**:
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

**QPS**:
```promql
# 每秒请求数
rate(http_server_requests_seconds_count{service="service-order"}[5m])
```

#### 6.1.2 系统指标

**CPU使用率**:
```promql
# 进程CPU使用率
process_cpu_usage{service="service-order"}

# 系统CPU使用率
100 - (avg by(instance) (irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)
```

**内存使用率**:
```promql
# JVM堆内存使用率
sum(jvm_memory_used_bytes{service="service-order",area="heap"})
/
sum(jvm_memory_max_bytes{service="service-order",area="heap"})
* 100

# 系统内存使用率
(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100
```

**磁盘IO**:
```promql
# 磁盘读取速率
rate(node_disk_read_bytes_total[5m])

# 磁盘写入速率
rate(node_disk_written_bytes_total[5m])
```

#### 6.1.3 业务指标

**订单创建成功率**:
```promql
# 订单创建成功数 / 订单创建总数
sum(rate(order_create_total{status="success"}[5m]))
/
sum(rate(order_create_total[5m]))
```

**支付成功率**:
```promql
# 支付成功数 / 支付总数
sum(rate(payment_total{status="success"}[5m]))
/
sum(rate(payment_total[5m]))
```

### 6.2 监控检查实现

#### 6.2.1 错误率检查

```bash
check_error_rate() {
    local service=$1
    
    log_debug "检查错误率: ${service}"
    
    # 查询Prometheus
    local error_rate=$(curl -s "http://prometheus:9090/api/v1/query?query=rate(http_server_requests_seconds_count{status=~\"5..\",service=\"${service}\"}[5m])/rate(http_server_requests_seconds_count{service=\"${service}\"}[5m])" | jq -r '.data.result[0].value[1] // "0"')
    
    log_debug "当前错误率: ${error_rate}"
    
    # 比较阈值
    if [ "$(echo "${error_rate} > ${ERROR_RATE_THRESHOLD}" | bc -l 2>/dev/null || echo 0)" -eq 1 ]; then
        log_error "错误率超过阈值: ${error_rate} > ${ERROR_RATE_THRESHOLD}"
        return 1
    fi
    
    log_info "错误率检查通过: ${error_rate} <= ${ERROR_RATE_THRESHOLD}"
    return 0
}
```

#### 6.2.2 响应时间检查

```bash
check_response_time() {
    local service=$1
    
    log_debug "检查响应时间: ${service}"
    
    # 查询P99响应时间
    local p99=$(curl -s "http://prometheus:9090/api/v1/query?query=histogram_quantile(0.99,rate(http_server_requests_seconds_bucket{service=\"${service}\"}[5m]))" | jq -r '.data.result[0].value[1] // "0"')
    
    # 转换为毫秒
    local p99_ms=$(echo "${p99} * 1000" | bc -l)
    
    log_debug "当前P99响应时间: ${p99_ms}ms"
    
    # 比较阈值
    if [ "$(echo "${p99_ms} > ${RESPONSE_TIME_THRESHOLD}" | bc -l 2>/dev/null || echo 0)" -eq 1 ]; then
        log_error "P99响应时间超过阈值: ${p99_ms}ms > ${RESPONSE_TIME_THRESHOLD}ms"
        return 1
    fi
    
    log_info "响应时间检查通过: ${p99_ms}ms <= ${RESPONSE_TIME_THRESHOLD}ms"
    return 0
}
```

#### 6.2.3 资源使用检查

```bash
check_cpu_usage() {
    local service=$1
    
    log_debug "检查CPU使用率: ${service}"
    
    # 查询CPU使用率
    local cpu_usage=$(curl -s "http://prometheus:9090/api/v1/query?query=process_cpu_usage{service=\"${service}\"}" | jq -r '.data.result[0].value[1] // "0"')
    
    # 转换为百分比
    local cpu_percent=$(echo "${cpu_usage} * 100" | bc -l)
    
    log_debug "当前CPU使用率: ${cpu_percent}%"
    
    # 比较阈值
    if [ "$(echo "${cpu_percent} > ${CPU_USAGE_THRESHOLD}" | bc -l 2>/dev/null || echo 0)" -eq 1 ]; then
        log_error "CPU使用率超过阈值: ${cpu_percent}% > ${CPU_USAGE_THRESHOLD}%"
        return 1
    fi
    
    log_info "CPU使用率检查通过: ${cpu_percent}% <= ${CPU_USAGE_THRESHOLD}%"
    return 0
}
```

### 6.3 监控检查配置

#### 6.3.1 阈值配置

```yaml
# deploy-config.yml
thresholds:
  # 错误率阈值
  error_rate: 0.01              # 1%
  
  # 响应时间阈值
  response_time_p99: 2000       # 2秒
  response_time_p95: 1000       # 1秒
  response_time_avg: 500        # 500ms
  
  # 资源使用阈值
  cpu_usage: 80                 # 80%
  memory_usage: 85              # 85%
  
  # 检查间隔
  check_interval: 30            # 30秒
  
  # 指标采集时间窗口
  metrics_window: 300           # 5分钟
```

#### 6.3.2 环境差异化配置

```yaml
environments:
  dev:
    thresholds:
      error_rate: 0.05          # 开发环境放宽
      response_time_p99: 5000
      
  staging:
    thresholds:
      error_rate: 0.02
      response_time_p99: 3000
      
  prod:
    thresholds:
      error_rate: 0.01          # 生产环境严格
      response_time_p99: 2000
```

---

## 7. 健康检查机制

### 7.1 健康检查配置

#### 7.1.1 Spring Boot Actuator配置

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  health:
    liveness:
      enabled: true
    readiness:
      enabled: true
    db:
      enabled: true
    redis:
      enabled: true
```

#### 7.1.2 健康检查端点

**综合健康检查**:
```bash
curl http://localhost:8000/actuator/health
```

**响应示例**:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.0"
      }
    },
    "nacos": {
      "status": "UP"
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 107374182400,
        "free": 53687091200,
        "threshold": 10485760
      }
    }
  }
}
```

**存活探针**:
```bash
curl http://localhost:8000/actuator/health/liveness
```

**就绪探针**:
```bash
curl http://localhost:8000/actuator/health/readiness
```

### 7.2 健康检查实现

#### 7.2.1 基础健康检查

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
            log_debug "健康检查失败，${retry_interval}秒后重试 (${retry}/${max_retries})"
            sleep $retry_interval
        fi
    done
    
    log_error "${service} 健康检查失败"
    return 1
}
```

#### 7.2.2 详细健康检查

```bash
detailed_health_check() {
    local service=$1
    local port=$2
    
    log_info "执行详细健康检查: ${service}"
    
    local health_url="http://localhost:${port}/actuator/health"
    local response=$(curl -sf "$health_url" 2>/dev/null)
    
    if [ -z "$response" ]; then
        log_error "无法获取健康检查响应"
        return 1
    fi
    
    # 解析响应
    local status=$(echo "$response" | jq -r '.status')
    
    if [ "$status" != "UP" ]; then
        log_error "服务状态异常: ${status}"
        
        # 检查各个组件
        local components=$(echo "$response" | jq -r '.components | to_entries[] | select(.value.status != "UP") | .key')
        
        for component in $components; do
            log_error "组件异常: ${component}"
        done
        
        return 1
    fi
    
    log_success "${service} 详细健康检查通过"
    return 0
}
```

### 7.3 健康检查策略

#### 7.3.1 检查时机

**启动时检查**:
- 服务启动后等待60秒
- 执行健康检查
- 检查失败则重试

**运行时检查**:
- 定期检查（每30秒）
- 检查失败触发告警
- 连续失败触发回滚

**回滚后检查**:
- 回滚完成后立即检查
- 验证服务恢复
- 检查失败则告警

#### 7.3.2 检查参数

```yaml
health_check:
  endpoint: /actuator/health
  timeout: 10                   # 单次检查超时（秒）
  max_retries: 30               # 最大重试次数
  retry_interval: 5             # 重试间隔（秒）
  startup_wait: 60              # 启动等待时间（秒）
  success_threshold: 1          # 成功阈值
  failure_threshold: 3          # 失败阈值
```

---

## 8. 回滚最佳实践

### 8.1 回滚前准备

#### 8.1.1 问题确认清单

- [ ] 确认问题确实存在
- [ ] 确认问题由新版本引起
- [ ] 评估问题影响范围
- [ ] 确认回滚可以解决问题
- [ ] 选择合适的目标版本
- [ ] 准备回滚通知

#### 8.1.2 回滚计划

**回滚计划模板**:
```
回滚计划

1. 回滚原因
   - 问题描述：
   - 影响范围：
   - 严重程度：

2. 回滚目标
   - 目标版本：
   - 回滚时间：
   - 预计耗时：

3. 回滚步骤
   - 步骤1：
   - 步骤2：
   - 步骤3：

4. 验证方案
   - 健康检查：
   - 功能验证：
   - 性能验证：

5. 应急预案
   - 回滚失败处理：
   - 数据恢复方案：
   - 联系人：

6. 通知计划
   - 通知对象：
   - 通知时间：
   - 通知内容：
```

### 8.2 回滚执行

#### 8.2.1 执行原则

**安全第一**:
- 先备份，后回滚
- 先测试，后执行
- 先通知，后操作

**快速响应**:
- P0问题立即回滚
- P1问题尽快回滚
- P2问题评估后决定

**完整验证**:
- 回滚前验证
- 回滚中监控
- 回滚后验证

#### 8.2.2 执行步骤

**标准回滚流程**:
```
1. 准备阶段（10分钟）
   ├── 确认回滚需求
   ├── 选择目标版本
   ├── 备份当前状态
   └── 通知相关人员

2. 执行阶段（5-10分钟）
   ├── 停止当前版本
   ├── 切换到目标版本
   ├── 启动目标版本
   └── 更新版本记录

3. 验证阶段（10-15分钟）
   ├── 健康检查
   ├── 功能验证
   ├── 性能验证
   └── 监控观察

4. 完成阶段（5分钟）
   ├── 发送通知
   ├── 记录日志
   └── 问题复盘
```

### 8.3 回滚后验证

#### 8.3.1 功能验证

**核心功能验证**:
```bash
# 用户登录
curl -X POST http://localhost:80/api/user/login -d '{"username":"test","password":"test"}'

# 商品查询
curl http://localhost:80/api/product/list

# 订单创建
curl -X POST http://localhost:80/api/order/create -d '{"userId":1,"productId":1,"quantity":1}'
```

#### 8.3.2 性能验证

**响应时间验证**:
```bash
# 使用ab工具压测
ab -n 1000 -c 10 http://localhost:80/api/product/list

# 使用wrk工具压测
wrk -t4 -c100 -d30s http://localhost:80/api/product/list
```

**资源使用验证**:
```bash
# 查看CPU使用率
top -p $(pgrep -f service-order)

# 查看内存使用
free -h

# 查看磁盘IO
iostat -x 1
```

#### 8.3.3 监控验证

**监控指标验证**:
- 错误率 < 1%
- P99响应时间 < 2秒
- CPU使用率 < 80%
- 内存使用率 < 85%

**日志验证**:
```bash
# 查看错误日志
docker logs service-order | grep ERROR

# 查看异常堆栈
docker logs service-order | grep Exception
```

### 8.4 团队协作

#### 8.4.1 角色分工

**回滚负责人**:
- 制定回滚计划
- 协调各方资源
- 决策关键问题

**开发团队**:
- 问题分析
- 代码修复
- 技术支持

**运维团队**:
- 执行回滚
- 监控验证
- 故障处理

**测试团队**:
- 功能验证
- 性能验证
- 质量保障

#### 8.4.2 沟通机制

**回滚前**:
- 回滚计划评审
- 风险评估
- 准备情况确认

**回滚中**:
- 实时沟通群
- 状态更新
- 问题快速响应

**回滚后**:
- 回滚总结会
- 问题复盘
- 经验分享

---

## 9. 故障排查指南

### 9.1 常见问题

#### 9.1.1 回滚失败

**症状**:
```
✗ 回滚执行失败
✗ 健康检查失败
```

**排查步骤**:
```bash
# 1. 查看回滚日志
cat deployments/logs/rollback.log

# 2. 查看服务日志
docker logs service-order

# 3. 检查版本文件
cat deployments/versions/service-order/v20260512_180000.json

# 4. 检查Docker镜像
docker images | grep service-order

# 5. 检查服务状态
docker ps -a | grep service-order
```

**解决方案**:
- 检查版本文件是否完整
- 检查Docker镜像是否存在
- 检查配置文件是否正确
- 手动启动服务

#### 9.1.2 版本文件丢失

**症状**:
```
✗ 目标版本不存在: v20260512_180000
```

**排查步骤**:
```bash
# 1. 查看版本目录
ls -la deployments/versions/service-order/

# 2. 检查版本文件
cat deployments/versions/service-order/*.json

# 3. 查看Git历史
git log --oneline --all
```

**解决方案**:
- 从Git历史恢复版本文件
- 从备份恢复
- 重新创建版本记录

#### 9.1.3 服务无法启动

**症状**:
```
✗ 健康检查失败: service-order (尝试 30 次)
```

**排查步骤**:
```bash
# 1. 查看服务日志
docker logs service-order

# 2. 检查端口占用
netstat -tlnp | grep 8000

# 3. 检查依赖服务
curl http://mysql:3306
curl http://redis:6379
curl http://nacos:8848

# 4. 检查配置文件
cat services/service-order/src/main/resources/application.yml

# 5. 检查资源使用
df -h
free -h
```

**解决方案**:
- 修复配置错误
- 启动依赖服务
- 释放端口占用
- 扩容资源

### 9.2 日志查看

#### 9.2.1 回滚日志

```bash
# 查看完整日志
cat deployments/logs/rollback.log

# 实时查看日志
tail -f deployments/logs/rollback.log

# 搜索错误日志
grep ERROR deployments/logs/rollback.log

# 查看特定服务的日志
grep "service-order" deployments/logs/rollback.log
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

### 9.3 应急处理

#### 9.3.1 紧急回滚

```bash
# 1. 立即停止服务
docker stop service-order

# 2. 启动旧版本
docker run -d --name service-order-old username/cloudtry-service-order:v1.2.3

# 3. 更新路由
# 修改Nginx/Gateway配置

# 4. 验证服务
curl http://localhost:8000/actuator/health

# 5. 通知团队
```

#### 9.3.2 数据恢复

```bash
# 1. 恢复数据库
mysql -u root -p cloudtry_order < backup_20260513_150000.sql

# 2. 恢复Redis数据
redis-cli --pipe < redis_backup.txt

# 3. 验证数据
mysql -u root -p -e "SELECT COUNT(*) FROM cloudtry_order.orders;"
```

---

## 附录

### A. 相关文档

- [CI/CD使用指南](./CI_CD_GUIDE.md)
- [灰度发布指南](./CANARY_DEPLOY_GUIDE.md)
- [快速参考](./QUICK_REFERENCE.md)

### B. 常用命令速查

```bash
# 版本管理
./rollback.sh list                    # 查看版本列表
./rollback.sh current                 # 查看当前版本
./rollback.sh compare v1.2.0 v1.3.0   # 比较版本

# 回滚操作
./rollback.sh rollback                # 回滚到上一版本
./rollback.sh rollback --service service-order  # 回滚指定服务
./rollback.sh rollback --version v1.2.3         # 回滚到指定版本
./rollback.sh rollback --auto         # 自动回滚
./rollback.sh rollback --force        # 强制回滚

# 清理操作
./rollback.sh cleanup                 # 清理旧版本

# 日志查看
tail -f deployments/logs/rollback.log
docker logs -f service-order
```

### C. 故障排查清单

- [ ] 检查回滚日志
- [ ] 检查服务日志
- [ ] 检查版本文件
- [ ] 检查Docker镜像
- [ ] 检查服务状态
- [ ] 检查依赖服务
- [ ] 检查配置文件
- [ ] 检查资源使用

### D. 联系方式

如有问题，请联系：
- **DevOps团队**: devops@example.com
- **技术支持**: tech-support@example.com
- **紧急联系**: 电话/钉钉群

---

**文档维护**: DevOps团队
**最后审核**: 2026-05-13
