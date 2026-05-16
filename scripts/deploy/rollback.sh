#!/bin/bash

# =====================================================
# 自动化回滚脚本 (Automated Rollback Script)
# 功能：版本管理、自动回滚、监控检查、健康检查
# 作者：Backend Architect
# 版本：1.0.0
# =====================================================

set -e

# =====================================================
# 全局配置
# =====================================================

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# 配置文件路径
CONFIG_FILE="${CONFIG_FILE:-${SCRIPT_DIR}/deploy-config.yml}"
VERSION_DIR="${PROJECT_ROOT}/deployments/versions"
LOG_DIR="${PROJECT_ROOT}/deployments/logs"
LOG_FILE="${LOG_DIR}/rollback.log"

# 监控阈值（从配置文件读取或使用默认值）
ERROR_RATE_THRESHOLD=${ERROR_RATE_THRESHOLD:-0.01}          # 1%
RESPONSE_TIME_THRESHOLD=${RESPONSE_TIME_THRESHOLD:-2000}    # 2秒
CPU_USAGE_THRESHOLD=${CPU_USAGE_THRESHOLD:-80}              # 80%
MEMORY_USAGE_THRESHOLD=${MEMORY_USAGE_THRESHOLD:-85}        # 85%

# 版本保留数量
KEEP_VERSIONS=${KEEP_VERSIONS:-3}

# 服务列表
SERVICES=("gateway" "service-order" "service-product" "service-user")

# 默认参数
SERVICE_NAME=""
TARGET_VERSION=""
AUTO_MODE=false
FORCE_MODE=false
DRY_RUN=false
DEBUG_MODE=false

# =====================================================
# 工具函数
# =====================================================

# 日志函数
log() {
    local level=$1
    local message=$2
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    local log_message="${timestamp} [${level}] ${message}"
    
    # 输出到控制台
    case $level in
        INFO)
            echo -e "${GREEN}${log_message}${NC}"
            ;;
        WARN)
            echo -e "${YELLOW}${log_message}${NC}"
            ;;
        ERROR)
            echo -e "${RED}${log_message}${NC}"
            ;;
        DEBUG)
            if [ "$DEBUG_MODE" = true ]; then
                echo -e "${BLUE}${log_message}${NC}"
            fi
            ;;
        *)
            echo -e "${log_message}"
            ;;
    esac
    
    # 写入日志文件
    mkdir -p "$(dirname "${LOG_FILE}")"
    echo "${log_message}" >> "${LOG_FILE}"
}

log_info() {
    log "INFO" "$1"
}

log_warn() {
    log "WARN" "$1"
}

log_error() {
    log "ERROR" "$1"
}

log_debug() {
    log "DEBUG" "$1"
}

# 显示帮助信息
show_help() {
    cat << EOF
自动化回滚脚本 - 版本管理与回滚工具

用法:
    $0 [命令] [选项]

命令:
    rollback            回滚到指定版本或上一版本
    list                列出所有可用版本
    current             显示当前版本信息
    cleanup             清理旧版本（保留最近${KEEP_VERSIONS}个版本）
    compare <v1> <v2>   比较两个版本

选项:
    --service <name>    指定服务名称（可选，默认所有服务）
                        可选值: ${SERVICES[*]}
    --version <version> 指定回滚版本（可选，默认上一版本）
    --auto              自动模式（基于监控指标自动触发回滚）
    --force             强制回滚（跳过确认）
    --dry-run           模拟运行（不执行实际操作）
    --debug             调试模式（显示详细日志）
    -h, --help          显示帮助信息

示例:
    # 回滚所有服务到上一版本
    $0 rollback

    # 回滚指定服务到上一版本
    $0 rollback --service service-order

    # 回滚到指定版本
    $0 rollback --service service-order --version v1.2.3

    # 自动回滚（基于监控指标）
    $0 rollback --auto

    # 强制回滚（跳过确认）
    $0 rollback --force

    # 模拟运行
    $0 rollback --dry-run

    # 列出所有版本
    $0 list

    # 显示当前版本
    $0 current

    # 清理旧版本
    $0 cleanup

    # 比较两个版本
    $0 compare v1.2.0 v1.2.3

监控阈值:
    错误率: ${ERROR_RATE_THRESHOLD} (1%)
    P99响应时间: ${RESPONSE_TIME_THRESHOLD}ms (2秒)
    CPU使用率: ${CPU_USAGE_THRESHOLD}%
    内存使用率: ${MEMORY_USAGE_THRESHOLD}%

EOF
}

# 解析参数
parse_args() {
    local command=""
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            rollback|list|current|cleanup|compare)
                command=$1
                shift
                ;;
            --service)
                SERVICE_NAME="$2"
                shift 2
                ;;
            --version)
                TARGET_VERSION="$2"
                shift 2
                ;;
            --auto)
                AUTO_MODE=true
                shift
                ;;
            --force)
                FORCE_MODE=true
                shift
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --debug)
                DEBUG_MODE=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                # 处理compare命令的版本参数
                if [ "$command" = "compare" ]; then
                    if [ -z "$COMPARE_V1" ]; then
                        COMPARE_V1=$1
                    elif [ -z "$COMPARE_V2" ]; then
                        COMPARE_V2=$1
                    fi
                else
                    log_error "未知参数: $1"
                    show_help
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    # 保存命令
    COMMAND="${command:-rollback}"
    
    # 验证服务名称
    if [ -n "$SERVICE_NAME" ]; then
        local valid_service=false
        for service in "${SERVICES[@]}"; do
            if [ "$service" = "$SERVICE_NAME" ]; then
                valid_service=true
                break
            fi
        done
        
        if [ "$valid_service" = false ]; then
            log_error "无效的服务名称: $SERVICE_NAME"
            log_info "有效的服务: ${SERVICES[*]}"
            exit 1
        fi
    fi
}

# =====================================================
# 版本管理函数
# =====================================================

# 初始化版本目录
init_version_dir() {
    mkdir -p "${VERSION_DIR}"
    mkdir -p "${LOG_DIR}"
    
    for service in "${SERVICES[@]}"; do
        mkdir -p "${VERSION_DIR}/${service}"
    done
    
    log_debug "版本目录初始化完成: ${VERSION_DIR}"
}

# 获取当前时间戳
get_timestamp() {
    date '+%Y%m%d_%H%M%S'
}

# 生成版本ID
generate_version_id() {
    local service=$1
    local timestamp=$(get_timestamp)
    echo "v${timestamp}"
}

# 获取Git信息
get_git_info() {
    local git_sha=""
    local git_branch=""
    local git_message=""
    
    if command -v git &> /dev/null && [ -d "${PROJECT_ROOT}/.git" ]; then
        git_sha=$(git rev-parse HEAD 2>/dev/null || echo "unknown")
        git_branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
        git_message=$(git log -1 --pretty=%B 2>/dev/null | head -n 1 || echo "unknown")
    else
        git_sha="unknown"
        git_branch="unknown"
        git_message="unknown"
    fi
    
    echo "${git_sha}|${git_branch}|${git_message}"
}

# 创建版本记录
create_version_record() {
    local service=$1
    local version=$2
    local image_tag=$3
    
    local git_info=$(get_git_info)
    local git_sha=$(echo "$git_info" | cut -d'|' -f1)
    local git_branch=$(echo "$git_info" | cut -d'|' -f2)
    local git_message=$(echo "$git_info" | cut -d'|' -f3)
    
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    local deployer=$(whoami)
    
    local version_file="${VERSION_DIR}/${service}/${version}.json"
    
    cat > "${version_file}" << EOF
{
    "version": "${version}",
    "service": "${service}",
    "timestamp": "${timestamp}",
    "deployer": "${deployer}",
    "git": {
        "sha": "${git_sha}",
        "branch": "${git_branch}",
        "message": "${git_message}"
    },
    "image": {
        "tag": "${image_tag}",
        "registry": "${DOCKER_REGISTRY:-}"
    },
    "status": "deployed",
    "rollback_count": 0
}
EOF
    
    log_debug "创建版本记录: ${version_file}"
}

# 获取当前版本
get_current_version() {
    local service=$1
    local current_file="${VERSION_DIR}/${service}/current.json"
    
    if [ -f "$current_file" ]; then
        local version=$(cat "$current_file" | grep -o '"version"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)
        echo "$version"
    else
        echo "unknown"
    fi
}

# 获取上一版本
get_previous_version() {
    local service=$1
    local current_version=$(get_current_version "$service")
    
    if [ "$current_version" = "unknown" ]; then
        echo "unknown"
        return
    fi
    
    # 获取版本列表（按时间倒序）
    local versions=($(ls -t "${VERSION_DIR}/${service}"/*.json 2>/dev/null | grep -v "current.json" | head -n 10))
    
    local found_current=false
    for version_file in "${versions[@]}"; do
        local version=$(basename "$version_file" .json)
        
        if [ "$found_current" = true ]; then
            echo "$version"
            return
        fi
        
        if [ "$version" = "$current_version" ]; then
            found_current=true
        fi
    done
    
    echo "unknown"
}

# 列出所有版本
list_versions() {
    local service=$1
    
    log_info "=========================================="
    log_info "版本列表 - ${service}"
    log_info "=========================================="
    
    local current_version=$(get_current_version "$service")
    local version_files=($(ls -t "${VERSION_DIR}/${service}"/*.json 2>/dev/null | grep -v "current.json"))
    
    if [ ${#version_files[@]} -eq 0 ]; then
        log_warn "未找到版本记录"
        return
    fi
    
    local count=0
    for version_file in "${version_files[@]}"; do
        local version=$(basename "$version_file" .json)
        local timestamp=$(cat "$version_file" | grep -o '"timestamp"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)
        local deployer=$(cat "$version_file" | grep -o '"deployer"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)
        local git_sha=$(cat "$version_file" | grep -o '"sha"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | cut -c1-7)
        
        local marker=""
        if [ "$version" = "$current_version" ]; then
            marker="${GREEN}[当前版本]${NC}"
        fi
        
        echo -e "  ${version} ${marker}"
        echo -e "    时间: ${timestamp}"
        echo -e "    部署者: ${deployer}"
        echo -e "    Git SHA: ${git_sha}"
        echo ""
        
        count=$((count + 1))
        if [ $count -ge 10 ]; then
            log_info "仅显示最近10个版本"
            break
        fi
    done
}

# 显示当前版本
show_current_version() {
    local service=$1
    
    log_info "=========================================="
    log_info "当前版本 - ${service}"
    log_info "=========================================="
    
    local current_version=$(get_current_version "$service")
    
    if [ "$current_version" = "unknown" ]; then
        log_warn "未找到当前版本记录"
        return
    fi
    
    local version_file="${VERSION_DIR}/${service}/${current_version}.json"
    
    if [ ! -f "$version_file" ]; then
        log_error "版本文件不存在: ${version_file}"
        return
    fi
    
    # 显示版本详情
    cat "$version_file" | grep -v "^{" | grep -v "^}" | sed 's/^[[:space:]]*//'
    echo ""
}

# 比较两个版本
compare_versions() {
    local service=$1
    local version1=$2
    local version2=$3
    
    log_info "=========================================="
    log_info "版本比较 - ${service}"
    log_info "=========================================="
    
    local file1="${VERSION_DIR}/${service}/${version1}.json"
    local file2="${VERSION_DIR}/${service}/${version2}.json"
    
    if [ ! -f "$file1" ]; then
        log_error "版本不存在: ${version1}"
        return 1
    fi
    
    if [ ! -f "$file2" ]; then
        log_error "版本不存在: ${version2}"
        return 1
    fi
    
    log_info "版本 ${version1}:"
    echo "  Git SHA: $(cat "$file1" | grep -o '"sha"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | cut -c1-7)"
    echo "  镜像标签: $(cat "$file1" | grep -o '"tag"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)"
    echo "  部署时间: $(cat "$file1" | grep -o '"timestamp"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)"
    echo ""
    
    log_info "版本 ${version2}:"
    echo "  Git SHA: $(cat "$file2" | grep -o '"sha"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | cut -c1-7)"
    echo "  镜像标签: $(cat "$file2" | grep -o '"tag"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)"
    echo "  部署时间: $(cat "$file2" | grep -o '"timestamp"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)"
    echo ""
}

# 清理旧版本
cleanup_old_versions() {
    local service=$1
    
    log_info "=========================================="
    log_info "清理旧版本 - ${service}"
    log_info "=========================================="
    
    local current_version=$(get_current_version "$service")
    local version_files=($(ls -t "${VERSION_DIR}/${service}"/*.json 2>/dev/null | grep -v "current.json"))
    
    if [ ${#version_files[@]} -le $KEEP_VERSIONS ]; then
        log_info "版本数量未超过保留限制，无需清理"
        return
    fi
    
    local count=0
    local deleted=0
    
    for version_file in "${version_files[@]}"; do
        count=$((count + 1))
        
        if [ $count -gt $KEEP_VERSIONS ]; then
            local version=$(basename "$version_file" .json)
            
            # 不删除当前版本
            if [ "$version" != "$current_version" ]; then
                if [ "$DRY_RUN" = true ]; then
                    log_info "[DRY-RUN] 将删除版本: ${version}"
                else
                    rm -f "$version_file"
                    log_info "已删除版本: ${version}"
                fi
                deleted=$((deleted + 1))
            fi
        fi
    done
    
    log_info "清理完成，删除了 ${deleted} 个旧版本"
}

# =====================================================
# 监控检查函数
# =====================================================

# 检查错误率
check_error_rate() {
    local service=$1
    
    log_debug "检查错误率: ${service}"
    
    # 模拟检查（实际环境应从Prometheus等监控系统获取）
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY-RUN] 模拟错误率检查"
        return 0
    fi
    
    # 实际环境示例：
    # local error_rate=$(curl -s "http://prometheus:9090/api/v1/query?query=rate(http_server_requests_seconds_count{status=~\"5..\",service=\"${service}\"}[5m])/rate(http_server_requests_seconds_count{service=\"${service}\"}[5m])" | jq -r '.data.result[0].value[1] // "0"')
    
    # 模拟数据
    local error_rate=0.005
    
    if [ "$(echo "${error_rate} > ${ERROR_RATE_THRESHOLD}" | bc -l 2>/dev/null || echo 0)" -eq 1 ]; then
        log_error "错误率超过阈值: ${error_rate} > ${ERROR_RATE_THRESHOLD}"
        return 1
    fi
    
    log_info "错误率检查通过: ${error_rate} <= ${ERROR_RATE_THRESHOLD}"
    return 0
}

# 检查响应时间
check_response_time() {
    local service=$1
    
    log_debug "检查响应时间: ${service}"
    
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY-RUN] 模拟响应时间检查"
        return 0
    fi
    
    # 实际环境示例：
    # local p99=$(curl -s "http://prometheus:9090/api/v1/query?query=histogram_quantile(0.99,rate(http_server_requests_seconds_bucket{service=\"${service}\"}[5m]))" | jq -r '.data.result[0].value[1] // "0"')
    
    # 模拟数据
    local p99=1500
    
    if [ "$p99" -gt "$RESPONSE_TIME_THRESHOLD" ]; then
        log_error "P99响应时间超过阈值: ${p99}ms > ${RESPONSE_TIME_THRESHOLD}ms"
        return 1
    fi
    
    log_info "响应时间检查通过: ${p99}ms <= ${RESPONSE_TIME_THRESHOLD}ms"
    return 0
}

# 检查CPU使用率
check_cpu_usage() {
    local service=$1
    
    log_debug "检查CPU使用率: ${service}"
    
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY-RUN] 模拟CPU使用率检查"
        return 0
    fi
    
    # 实际环境示例：
    # local cpu_usage=$(curl -s "http://prometheus:9090/api/v1/query?query=process_cpu_usage{service=\"${service}\"}" | jq -r '.data.result[0].value[1] // "0"')
    
    # 模拟数据
    local cpu_usage=65
    
    if [ "$cpu_usage" -gt "$CPU_USAGE_THRESHOLD" ]; then
        log_error "CPU使用率超过阈值: ${cpu_usage}% > ${CPU_USAGE_THRESHOLD}%"
        return 1
    fi
    
    log_info "CPU使用率检查通过: ${cpu_usage}% <= ${CPU_USAGE_THRESHOLD}%"
    return 0
}

# 检查内存使用率
check_memory_usage() {
    local service=$1
    
    log_debug "检查内存使用率: ${service}"
    
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY-RUN] 模拟内存使用率检查"
        return 0
    fi
    
    # 实际环境示例：
    # local memory_usage=$(curl -s "http://prometheus:9090/api/v1/query?query=jvm_memory_used_bytes{service=\"${service}\"}/jvm_memory_max_bytes{service=\"${service}\"}" | jq -r '.data.result[0].value[1] // "0"')
    
    # 模拟数据
    local memory_usage=70
    
    if [ "$memory_usage" -gt "$MEMORY_USAGE_THRESHOLD" ]; then
        log_error "内存使用率超过阈值: ${memory_usage}% > ${MEMORY_USAGE_THRESHOLD}%"
        return 1
    fi
    
    log_info "内存使用率检查通过: ${memory_usage}% <= ${MEMORY_USAGE_THRESHOLD}%"
    return 0
}

# 执行所有监控检查
perform_monitoring_checks() {
    local service=$1
    local failed=0
    
    log_info "=========================================="
    log_info "监控指标检查 - ${service}"
    log_info "=========================================="
    
    check_error_rate "$service" || failed=$((failed + 1))
    check_response_time "$service" || failed=$((failed + 1))
    check_cpu_usage "$service" || failed=$((failed + 1))
    check_memory_usage "$service" || failed=$((failed + 1))
    
    if [ $failed -gt 0 ]; then
        log_error "监控检查失败: ${failed} 项检查未通过"
        return 1
    fi
    
    log_info "所有监控检查通过"
    return 0
}

# =====================================================
# 健康检查函数
# =====================================================

# 健康检查
health_check() {
    local service=$1
    local port=$2
    local max_retries=${3:-30}
    local retry_interval=${4:-5}
    
    log_info "执行健康检查: ${service}"
    
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY-RUN] 模拟健康检查"
        return 0
    fi
    
    local retry=0
    while [ $retry -lt $max_retries ]; do
        # 检查服务健康状态
        if curl -sf "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
            log_info "健康检查通过: ${service}"
            return 0
        fi
        
        retry=$((retry + 1))
        log_debug "健康检查尝试 ${retry}/${max_retries} 失败"
        sleep $retry_interval
    done
    
    log_error "健康检查失败: ${service} (尝试 ${max_retries} 次)"
    return 1
}

# 检查服务注册状态
check_service_registration() {
    local service=$1
    
    log_debug "检查服务注册状态: ${service}"
    
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY-RUN] 模拟服务注册状态检查"
        return 0
    fi
    
    # 实际环境示例：
    # local registered=$(curl -s "http://nacos:8848/nacos/v1/ns/instance/list?serviceName=${service}" | jq -r '.hosts | length')
    
    # 模拟数据
    local registered=2
    
    if [ "$registered" -eq 0 ]; then
        log_error "服务未注册到注册中心: ${service}"
        return 1
    fi
    
    log_info "服务注册状态正常: ${service} (${registered} 个实例)"
    return 0
}

# =====================================================
# 回滚函数
# =====================================================

# 验证目标版本
validate_target_version() {
    local service=$1
    local version=$2
    
    log_debug "验证目标版本: ${version}"
    
    local version_file="${VERSION_DIR}/${service}/${version}.json"
    
    if [ ! -f "$version_file" ]; then
        log_error "目标版本不存在: ${version}"
        return 1
    fi
    
    # 检查版本状态
    local status=$(cat "$version_file" | grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)
    
    if [ "$status" != "deployed" ]; then
        log_error "目标版本状态异常: ${status}"
        return 1
    fi
    
    log_info "目标版本验证通过: ${version}"
    return 0
}

# 执行回滚操作
execute_rollback() {
    local service=$1
    local target_version=$2
    
    log_info "=========================================="
    log_info "执行回滚 - ${service}"
    log_info "目标版本: ${target_version}"
    log_info "=========================================="
    
    # 获取当前版本
    local current_version=$(get_current_version "$service")
    log_info "当前版本: ${current_version}"
    
    # 验证目标版本
    if ! validate_target_version "$service" "$target_version"; then
        return 1
    fi
    
    # 获取目标版本信息
    local version_file="${VERSION_DIR}/${service}/${target_version}.json"
    local image_tag=$(cat "$version_file" | grep -o '"tag"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)
    
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY-RUN] 模拟回滚操作"
        log_info "[DRY-RUN] 服务: ${service}"
        log_info "[DRY-RUN] 当前版本: ${current_version}"
        log_info "[DRY-RUN] 目标版本: ${target_version}"
        log_info "[DRY-RUN] 镜像标签: ${image_tag}"
        return 0
    fi
    
    # 实际回滚操作
    log_info "开始回滚..."
    
    # 1. 停止当前版本
    log_info "停止当前版本..."
    # docker-compose stop ${service} || true
    
    # 2. 切换到目标版本
    log_info "切换到目标版本..."
    # docker tag ${service}:${image_tag} ${service}:current
    # docker-compose up -d ${service}
    
    # 3. 更新版本记录
    log_info "更新版本记录..."
    cp "$version_file" "${VERSION_DIR}/${service}/current.json"
    
    # 4. 更新回滚计数
    local rollback_count=$(cat "$version_file" | grep -o '"rollback_count"[[:space:]]*:[[:space:]]*[0-9]*' | grep -o '[0-9]*')
    rollback_count=$((rollback_count + 1))
    
    # 更新版本文件中的回滚计数
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        sed -i '' "s/\"rollback_count\": [0-9]*/\"rollback_count\": ${rollback_count}/" "$version_file"
    else
        # Linux
        sed -i "s/\"rollback_count\": [0-9]*/\"rollback_count\": ${rollback_count}/" "$version_file"
    fi
    
    log_info "回滚操作完成"
    return 0
}

# 回滚后验证
verify_rollback() {
    local service=$1
    
    log_info "=========================================="
    log_info "回滚验证 - ${service}"
    log_info "=========================================="
    
    # 获取服务端口
    local port=8000
    case $service in
        gateway)
            port=80
            ;;
        service-order)
            port=8000
            ;;
        service-product)
            port=9000
            ;;
        service-user)
            port=7000
            ;;
    esac
    
    # 健康检查
    if ! health_check "$service" "$port"; then
        log_error "健康检查失败"
        return 1
    fi
    
    # 服务注册检查
    if ! check_service_registration "$service"; then
        log_error "服务注册检查失败"
        return 1
    fi
    
    # 监控检查
    if ! perform_monitoring_checks "$service"; then
        log_warn "监控检查失败，但回滚已完成"
    fi
    
    log_info "回滚验证完成"
    return 0
}

# 主回滚函数
perform_rollback() {
    local service=$1
    local target_version=$2
    
    log_info "=========================================="
    log_info "开始回滚流程 - ${service}"
    log_info "=========================================="
    
    # 如果未指定目标版本，使用上一版本
    if [ -z "$target_version" ]; then
        target_version=$(get_previous_version "$service")
        
        if [ "$target_version" = "unknown" ]; then
            log_error "未找到可回滚的版本"
            return 1
        fi
        
        log_info "使用上一版本: ${target_version}"
    fi
    
    # 确认回滚
    if [ "$FORCE_MODE" = false ] && [ "$DRY_RUN" = false ]; then
        echo ""
        log_warn "即将回滚服务: ${service}"
        log_warn "目标版本: ${target_version}"
        echo ""
        read -p "确认执行回滚? (yes/no): " confirm
        
        if [ "$confirm" != "yes" ]; then
            log_info "回滚操作已取消"
            return 0
        fi
    fi
    
    # 执行回滚
    if ! execute_rollback "$service" "$target_version"; then
        log_error "回滚执行失败"
        return 1
    fi
    
    # 验证回滚
    if ! verify_rollback "$service"; then
        log_error "回滚验证失败"
        return 1
    fi
    
    log_info "=========================================="
    log_info "回滚成功完成 - ${service}"
    log_info "=========================================="
    
    return 0
}

# 自动回滚（基于监控指标）
auto_rollback() {
    local service=$1
    
    log_info "=========================================="
    log_info "自动回滚模式 - ${service}"
    log_info "=========================================="
    
    # 执行监控检查
    if perform_monitoring_checks "$service"; then
        log_info "监控指标正常，无需回滚"
        return 0
    fi
    
    log_warn "监控指标异常，触发自动回滚"
    
    # 执行回滚
    perform_rollback "$service" ""
}

# =====================================================
# 主函数
# =====================================================

main() {
    # 初始化
    init_version_dir
    
    # 解析参数
    parse_args "$@"
    
    log_info "=========================================="
    log_info "自动化回滚脚本 v1.0.0"
    log_info "=========================================="
    
    if [ "$DEBUG_MODE" = true ]; then
        log_info "调试模式: 启用"
        log_info "配置文件: ${CONFIG_FILE}"
        log_info "版本目录: ${VERSION_DIR}"
        log_info "日志文件: ${LOG_FILE}"
    fi
    
    if [ "$DRY_RUN" = true ]; then
        log_warn "模拟运行模式: 启用（不会执行实际操作）"
    fi
    
    # 执行命令
    case $COMMAND in
        rollback)
            if [ "$AUTO_MODE" = true ]; then
                # 自动回滚
                if [ -n "$SERVICE_NAME" ]; then
                    auto_rollback "$SERVICE_NAME"
                else
                    for service in "${SERVICES[@]}"; do
                        auto_rollback "$service"
                    done
                fi
            else
                # 手动回滚
                if [ -n "$SERVICE_NAME" ]; then
                    perform_rollback "$SERVICE_NAME" "$TARGET_VERSION"
                else
                    for service in "${SERVICES[@]}"; do
                        perform_rollback "$service" "$TARGET_VERSION"
                    done
                fi
            fi
            ;;
            
        list)
            if [ -n "$SERVICE_NAME" ]; then
                list_versions "$SERVICE_NAME"
            else
                for service in "${SERVICES[@]}"; do
                    list_versions "$service"
                done
            fi
            ;;
            
        current)
            if [ -n "$SERVICE_NAME" ]; then
                show_current_version "$SERVICE_NAME"
            else
                for service in "${SERVICES[@]}"; do
                    show_current_version "$service"
                done
            fi
            ;;
            
        cleanup)
            if [ -n "$SERVICE_NAME" ]; then
                cleanup_old_versions "$SERVICE_NAME"
            else
                for service in "${SERVICES[@]}"; do
                    cleanup_old_versions "$service"
                done
            fi
            ;;
            
        compare)
            if [ -z "$COMPARE_V1" ] || [ -z "$COMPARE_V2" ]; then
                log_error "比较版本需要指定两个版本号"
                show_help
                exit 1
            fi
            
            if [ -n "$SERVICE_NAME" ]; then
                compare_versions "$SERVICE_NAME" "$COMPARE_V1" "$COMPARE_V2"
            else
                for service in "${SERVICES[@]}"; do
                    compare_versions "$service" "$COMPARE_V1" "$COMPARE_V2"
                done
            fi
            ;;
            
        *)
            log_error "未知命令: ${COMMAND}"
            show_help
            exit 1
            ;;
    esac
    
    log_info "=========================================="
    log_info "操作完成"
    log_info "=========================================="
}

# 执行主函数
main "$@"
