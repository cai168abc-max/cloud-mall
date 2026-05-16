#!/bin/bash

# =====================================================
# 版本管理初始化脚本
# 功能：初始化版本目录结构，创建示例版本记录
# 使用：./init-version.sh
# =====================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# 版本目录
VERSION_DIR="${PROJECT_ROOT}/deployments/versions"
LOG_DIR="${PROJECT_ROOT}/deployments/logs"

# 服务列表
SERVICES=("gateway" "service-order" "service-product" "service-user")

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 创建目录结构
create_directories() {
    log_info "创建版本管理目录结构..."
    
    mkdir -p "${VERSION_DIR}"
    mkdir -p "${LOG_DIR}"
    
    for service in "${SERVICES[@]}"; do
        mkdir -p "${VERSION_DIR}/${service}"
        log_info "  创建目录: ${VERSION_DIR}/${service}"
    done
    
    log_info "目录结构创建完成"
}

# 创建示例版本记录
create_sample_versions() {
    log_info "创建示例版本记录..."
    
    local timestamp=$(date '+%Y%m%d_%H%M%S')
    local git_sha=$(git rev-parse HEAD 2>/dev/null || echo "abc123def456")
    local git_branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "main")
    local deployer=$(whoami)
    
    # 为每个服务创建3个示例版本
    for service in "${SERVICES[@]}"; do
        log_info "为 ${service} 创建示例版本..."
        
        # 版本1 (最旧)
        local v1="v$(date -d '2 days ago' '+%Y%m%d_%H%M%S' 2>/dev/null || date -v-2d '+%Y%m%d_%H%M%S' 2>/dev/null || echo "20260511_100000")"
        cat > "${VERSION_DIR}/${service}/${v1}.json" << EOF
{
    "version": "${v1}",
    "service": "${service}",
    "timestamp": "$(date -d '2 days ago' '+%Y-%m-%d %H:%M:%S' 2>/dev/null || date -v-2d '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo "2026-05-11 10:00:00")",
    "deployer": "${deployer}",
    "git": {
        "sha": "${git_sha}",
        "branch": "${git_branch}",
        "message": "feat: 添加新功能"
    },
    "image": {
        "tag": "1.0.0",
        "registry": "registry.example.com"
    },
    "status": "deployed",
    "rollback_count": 0
}
EOF
        
        # 版本2 (中间)
        local v2="v$(date -d '1 day ago' '+%Y%m%d_%H%M%S' 2>/dev/null || date -v-1d '+%Y%m%d_%H%M%S' 2>/dev/null || echo "20260512_140000")"
        cat > "${VERSION_DIR}/${service}/${v2}.json" << EOF
{
    "version": "${v2}",
    "service": "${service}",
    "timestamp": "$(date -d '1 day ago' '+%Y-%m-%d %H:%M:%S' 2>/dev/null || date -v-1d '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo "2026-05-12 14:00:00")",
    "deployer": "${deployer}",
    "git": {
        "sha": "${git_sha}",
        "branch": "${git_branch}",
        "message": "fix: 修复bug"
    },
    "image": {
        "tag": "1.0.1",
        "registry": "registry.example.com"
    },
    "status": "deployed",
    "rollback_count": 0
}
EOF
        
        # 版本3 (当前)
        local v3="v${timestamp}"
        cat > "${VERSION_DIR}/${service}/${v3}.json" << EOF
{
    "version": "${v3}",
    "service": "${service}",
    "timestamp": "$(date '+%Y-%m-%d %H:%M:%S')",
    "deployer": "${deployer}",
    "git": {
        "sha": "${git_sha}",
        "branch": "${git_branch}",
        "message": "feat: 最新功能更新"
    },
    "image": {
        "tag": "1.0.2",
        "registry": "registry.example.com"
    },
    "status": "deployed",
    "rollback_count": 0
}
EOF
        
        # 创建当前版本链接
        cp "${VERSION_DIR}/${service}/${v3}.json" "${VERSION_DIR}/${service}/current.json"
        
        log_info "  创建版本: ${v1}, ${v2}, ${v3}"
    done
    
    log_info "示例版本记录创建完成"
}

# 创建README文件
create_readme() {
    log_info "创建版本管理说明文件..."
    
    cat > "${PROJECT_ROOT}/deployments/README.md" << 'EOF'
# 版本管理目录

## 目录结构

```
deployments/
├── versions/              # 版本记录存储
│   ├── gateway/
│   ├── service-order/
│   ├── service-product/
│   └── service-user/
├── logs/                  # 日志文件
│   └── rollback.log
└── README.md
```

## 版本记录格式

每个版本记录为JSON格式，包含以下信息：

```json
{
    "version": "v20260513_143000",
    "service": "service-order",
    "timestamp": "2026-05-13 14:30:00",
    "deployer": "user",
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

## 使用方法

### 查看版本列表
```bash
./scripts/deploy/rollback.sh list
./scripts/deploy/rollback.sh list --service service-order
```

### 查看当前版本
```bash
./scripts/deploy/rollback.sh current
./scripts/deploy/rollback.sh current --service service-order
```

### 回滚到上一版本
```bash
./scripts/deploy/rollback.sh rollback
./scripts/deploy/rollback.sh rollback --service service-order
```

### 回滚到指定版本
```bash
./scripts/deploy/rollback.sh rollback --service service-order --version v20260512_140000
```

### 自动回滚（基于监控指标）
```bash
./scripts/deploy/rollback.sh rollback --auto
```

### 清理旧版本
```bash
./scripts/deploy/rollback.sh cleanup
```

### 比较版本
```bash
./scripts/deploy/rollback.sh compare v1.0.0 v1.0.1
```

## 注意事项

1. 默认保留最近3个版本
2. 当前版本不会被清理
3. 回滚前会进行健康检查和监控检查
4. 支持强制回滚（--force）和模拟运行（--dry-run）

EOF
    
    log_info "README文件创建完成"
}

# 主函数
main() {
    log_info "=========================================="
    log_info "版本管理初始化"
    log_info "=========================================="
    
    create_directories
    create_sample_versions
    create_readme
    
    log_info "=========================================="
    log_info "初始化完成"
    log_info "=========================================="
    
    echo ""
    log_info "版本目录: ${VERSION_DIR}"
    log_info "日志目录: ${LOG_DIR}"
    echo ""
    log_info "下一步："
    log_info "  1. 查看版本列表: ./scripts/deploy/rollback.sh list"
    log_info "  2. 查看当前版本: ./scripts/deploy/rollback.sh current"
    log_info "  3. 测试回滚: ./scripts/deploy/rollback.sh rollback --dry-run"
}

# 执行主函数
main
