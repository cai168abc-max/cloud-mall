# 自动化回滚系统 - 文件清单

## 创建的文件

### 1. 核心脚本

#### 1.1 rollback.sh
**路径**: `scripts/deploy/rollback.sh`

**功能**: 主回滚脚本，提供完整的版本管理和回滚功能

**主要功能**:
- 版本管理（列表、查看、比较、清理）
- 手动回滚（到上一版本或指定版本）
- 自动回滚（基于监控指标）
- 健康检查和服务注册检查
- 监控指标检查（错误率、响应时间、CPU、内存）
- 日志记录和调试模式
- 模拟运行（dry-run）模式

**支持的命令**:
- `rollback`: 执行回滚操作
- `list`: 列出所有版本
- `current`: 显示当前版本
- `cleanup`: 清理旧版本
- `compare`: 比较两个版本

**支持的参数**:
- `--service <name>`: 指定服务名称
- `--version <version>`: 指定回滚版本
- `--auto`: 自动模式（基于监控指标）
- `--force`: 强制回滚（跳过确认）
- `--dry-run`: 模拟运行
- `--debug`: 调试模式

#### 1.2 init-version.sh
**路径**: `scripts/deploy/init-version.sh`

**功能**: 初始化版本管理目录和创建示例版本记录

**主要功能**:
- 创建版本管理目录结构
- 创建示例版本记录（每个服务3个版本）
- 创建版本管理说明文件

### 2. 文档文件

#### 2.1 ROLLBACK_GUIDE.md
**路径**: `scripts/deploy/ROLLBACK_GUIDE.md`

**功能**: 详细的使用指南文档

**主要内容**:
- 快速开始指南
- 功能详解（版本管理、回滚操作、监控检查等）
- 使用场景示例
- 最佳实践
- 故障排查
- CI/CD集成示例
- 附录（版本格式、端口映射、监控查询等）

#### 2.2 QUICK_REFERENCE.md
**路径**: `scripts/deploy/QUICK_REFERENCE.md`

**功能**: 快速参考卡片

**主要内容**:
- 常用命令速查
- 参数说明
- 服务列表
- 监控阈值
- 典型场景
- 故障排查

### 3. 测试脚本

#### 3.1 test-rollback.sh
**路径**: `scripts/deploy/test-rollback.sh`

**功能**: 测试回滚脚本的各项功能

**主要功能**:
- 自动化测试回滚脚本的各项功能
- 测试帮助信息、版本列表、当前版本等
- 测试回滚操作（模拟模式）
- 测试错误处理
- 生成测试报告

## 目录结构

```
cloudtry/
├── scripts/
│   └── deploy/
│       ├── rollback.sh              # 主回滚脚本
│       ├── init-version.sh          # 版本管理初始化脚本
│       ├── test-rollback.sh         # 测试脚本
│       ├── ROLLBACK_GUIDE.md        # 详细使用指南
│       ├── QUICK_REFERENCE.md       # 快速参考
│       ├── deploy-config.yml        # 部署配置文件（已存在）
│       └── canary-deploy.sh         # 灰度发布脚本（已存在）
│
└── deployments/
    ├── versions/                    # 版本记录存储目录
    │   ├── gateway/
    │   ├── service-order/
    │   ├── service-product/
    │   └── service-user/
    ├── logs/                        # 日志文件目录
    │   └── rollback.log
    └── README.md                    # 版本管理说明文件
```

## 快速开始

### 1. 初始化

```bash
# 初始化版本管理
./scripts/deploy/init-version.sh

# 设置执行权限
chmod +x ./scripts/deploy/rollback.sh
chmod +x ./scripts/deploy/init-version.sh
chmod +x ./scripts/deploy/test-rollback.sh
```

### 2. 测试

```bash
# 运行测试脚本
./scripts/deploy/test-rollback.sh
```

### 3. 使用

```bash
# 查看版本列表
./scripts/deploy/rollback.sh list

# 查看当前版本
./scripts/deploy/rollback.sh current

# 回滚到上一版本
./scripts/deploy/rollback.sh rollback --service service-order

# 模拟回滚
./scripts/deploy/rollback.sh rollback --service service-order --dry-run
```

## 功能特性

### 1. 版本管理
- ✓ 版本记录存储（JSON格式）
- ✓ 版本列表查看
- ✓ 当前版本显示
- ✓ 版本比较
- ✓ 旧版本清理（保留最近3个版本）

### 2. 回滚功能
- ✓ 回滚到上一版本
- ✓ 回滚到指定版本
- ✓ 自动回滚（基于监控指标）
- ✓ 强制回滚（跳过确认）
- ✓ 模拟运行（dry-run）

### 3. 监控检查
- ✓ 错误率检查（阈值: 1%）
- ✓ P99响应时间检查（阈值: 2秒）
- ✓ CPU使用率检查（阈值: 80%）
- ✓ 内存使用率检查（阈值: 85%）

### 4. 健康检查
- ✓ 服务健康检查（/actuator/health）
- ✓ 服务注册状态检查
- ✓ 自动重试机制

### 5. 日志和调试
- ✓ 详细的日志记录
- ✓ 调试模式
- ✓ 错误处理和提示

### 6. 安全性
- ✓ 回滚确认机制
- ✓ 版本验证
- ✓ 操作审计日志

## 监控阈值配置

| 指标 | 默认阈值 | 配置方式 |
|------|----------|----------|
| 错误率 | 1% | 环境变量 `ERROR_RATE_THRESHOLD` |
| P99响应时间 | 2秒 | 环境变量 `RESPONSE_TIME_THRESHOLD` |
| CPU使用率 | 80% | 环境变量 `CPU_USAGE_THRESHOLD` |
| 内存使用率 | 85% | 环境变量 `MEMORY_USAGE_THRESHOLD` |

## 服务配置

| 服务 | 默认端口 | 健康检查路径 |
|------|----------|--------------|
| gateway | 80 | /actuator/health |
| service-order | 8000 | /actuator/health |
| service-product | 9000 | /actuator/health |
| service-user | 7000 | /actuator/health |

## 使用场景

### 场景1: 快速回滚
部署后发现严重问题，需要立即回滚到上一版本。

```bash
./scripts/deploy/rollback.sh rollback --service service-order --force
```

### 场景2: 监控告警自动回滚
监控系统检测到异常指标，自动触发回滚。

```bash
./scripts/deploy/rollback.sh rollback --auto --service service-order
```

### 场景3: 测试回滚流程
在生产环境执行前，先测试回滚流程。

```bash
./scripts/deploy/rollback.sh rollback --service service-order --dry-run --debug
```

### 场景4: 回滚到特定版本
需要回滚到特定的历史版本。

```bash
# 查看版本列表
./scripts/deploy/rollback.sh list --service service-order

# 回滚到指定版本
./scripts/deploy/rollback.sh rollback --service service-order --version v20260512_140000
```

## 注意事项

1. **首次使用**: 需要先运行 `init-version.sh` 初始化版本管理
2. **权限设置**: 确保脚本有执行权限
3. **版本保留**: 默认保留最近3个版本，可在脚本中修改 `KEEP_VERSIONS` 变量
4. **监控集成**: 实际环境需要配置Prometheus等监控系统
5. **健康检查**: 确保服务的健康检查端点可访问
6. **日志查看**: 所有操作都会记录到 `deployments/logs/rollback.log`

## 下一步

1. 根据实际环境修改监控检查的实现（目前是模拟数据）
2. 配置实际的Docker或Kubernetes回滚操作
3. 集成到CI/CD流程中
4. 配置监控告警触发自动回滚
5. 根据团队需求调整监控阈值

## 技术支持

详细使用说明请参考:
- [ROLLBACK_GUIDE.md](./ROLLBACK_GUIDE.md) - 完整使用指南
- [QUICK_REFERENCE.md](./QUICK_REFERENCE.md) - 快速参考卡片
