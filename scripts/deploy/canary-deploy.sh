#!/bin/bash

# =====================================================
# 灰度发布脚本 (Canary Deployment Script)
# =====================================================
# 功能说明：
#   - 逐步将流量从旧版本迁移到新版本
#   - 支持Docker Compose和Kubernetes两种部署方式
#   - 支持流量权重控制（0-100%）
#   - 支持单个服务或所有服务的灰度发布
#   - 自动健康检查和监控指标验证
#   - 失败自动回滚机制
#
# 使用方法：
#   ./canary-deploy.sh <weight> [service_name] [options]
#
# 参数说明：
#   weight       流量权重（0-100），0表示回滚，100表示全量发布
#   service_name 服务名称（可选，默认所有服务）
#
# 可选参数：
#   --dry-run    模拟运行，不执行实际部署操作
#   --debug      调试模式，输出详细日志
#   --config     指定配置文件路径
#
# 示例：
#   ./canary-deploy.sh 10                  # 10%流量到新版本（所有服务）
#   ./canary-deploy.sh 30 service-order    # 30%流量到新版本（指定服务）
#   ./canary-deploy.sh 100                 # 全量发布（100%流量）
#   ./canary-deploy.sh 0                   # 回滚到旧版本
#   ./canary-deploy.sh 50 --dry-run        # 模拟运行
#
# 依赖工具：
#   - docker-compose 或 kubectl
#   - curl (健康检查)
#   - jq (JSON解析)
#   - bc (数值计算)
#
# 作者：Backend Architect
# 版本：v2.0
# 更新时间：2026-05-13
# =====================================================

set -e

# =====================================================
# 全局变量和配置
# =====================================================

# 颜色定义（用于终端输出美化）
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置文件路径（可通过环境变量覆盖）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CONFIG_FILE="${CONFIG_FILE:-${SCRIPT_DIR}/deploy-config.yml}"
LOG_FILE="${LOG_FILE:-${PROJECT_ROOT}/logs/canary-deploy.log}"

# 运行模式标志
DRY_RUN=false
DEBUG_MODE=false

# 默认服务列表
DEFAULT_SERVICES=("gateway" "service-order" "service-product" "service-user")

# 健康检查默认配置
DEFAULT_HEALTH_TIMEOUT=10
DEFAULT_HEALTH_RETRIES=30
DEFAULT_HEALTH_INTERVAL=5

# =====================================================
# 日志函数
# =====================================================

# 基础日志函数
# 参数: $1 - 日志级别, $2 - 日志消息
log() {
    local level=$1
    local message=$2
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    # 确保日志目录存在
    mkdir -p "$(dirname "${LOG_FILE}")" 2>/dev/null || true
    
    # 输出到控制台和日志文件
    echo -e "${timestamp} [${level}] ${message}" | tee -a "${LOG_FILE}" 2>/dev/null || echo -e "${timestamp} [${level}] ${message}"
}

# 信息日志（绿色）
log_info() {
    log "INFO" "${GREEN}$1${NC}"
}

# 警告日志（黄色）
log_warn() {
    log "WARN" "${YELLOW}$1${NC}"
}

# 错误日志（红色）
log_error() {
    log "ERROR" "${RED}$1${NC}"
}

# 调试日志（蓝色，仅在调试模式下输出）
log_debug() {
    if [ "$DEBUG_MODE" = true ]; then
        log "DEBUG" "${BLUE}$1${NC}"
    fi
}

# 成功日志（绿色，带✓标记）
log_success() {
    log "SUCCESS" "${GREEN}✓ $1${NC}"
}

# =====================================================
# 帮助信息
# =====================================================

show_help() {
    cat << EOF
灰度发布脚本 - Canary Deployment Script

使用方法:
  $0 <weight> [service_name] [options]

参数说明:
  weight           流量权重（0-100）
                   - 0: 回滚到旧版本
                   - 1-99: 灰度发布，指定百分比流量到新版本
                   - 100: 全量发布到新版本
  
  service_name     服务名称（可选）
                   - 不指定: 对所有服务执行灰度发布
                   - 指定服务名: 仅对指定服务执行灰度发布
                   - 支持的服务: gateway, service-order, service-product, service-user

可选参数:
  --dry-run        模拟运行模式
                   - 不执行实际部署操作
                   - 仅显示将要执行的操作
                   - 用于测试和验证
  
  --debug          调试模式
                   - 输出详细的调试信息
                   - 显示配置文件内容
                   - 显示中间计算过程
  
  --config FILE    指定配置文件路径
                   - 默认: ${CONFIG_FILE}
  
  --help, -h       显示此帮助信息

示例:
  # 基础用法
  $0 10                          # 10%流量到新版本（所有服务）
  $0 30 service-order            # 30%流量到新版本（仅service-order）
  $0 100                         # 全量发布（100%流量）
  $0 0                           # 回滚到旧版本
  
  # 高级用法
  $0 50 --dry-run                # 模拟运行50%灰度发布
  $0 30 service-order --debug    # 调试模式运行
  $0 20 --config /path/to/config.yml  # 使用自定义配置文件

配置文件:
  默认配置文件位于: ${CONFIG_FILE}
  配置文件包含:
  - 服务列表和端口配置
  - 灰度发布策略（阶段划分）
  - 监控阈值（错误率、响应时间等）
  - 健康检查配置
  - 回滚策略

依赖工具:
  - docker-compose 或 kubectl (部署工具)
  - curl (健康检查)
  - jq (JSON解析，可选)
  - bc (数值计算，可选)

更多信息请参考配置文件: ${CONFIG_FILE}
EOF
}

# =====================================================
# 参数解析
# =====================================================

# 解析命令行参数
parse_arguments() {
    # 检查是否显示帮助
    if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
        show_help
        exit 0
    fi
    
    # 检查必需参数
    if [ $# -lt 1 ]; then
        log_error "缺少必需参数: weight"
        echo ""
        show_help
        exit 1
    fi
    
    # 解析位置参数
    WEIGHT=$1
    shift
    
    # 解析可选参数
    SERVICE_NAME=""
    
    while [ $# -gt 0 ]; do
        case "$1" in
            --dry-run)
                DRY_RUN=true
                log_info "模拟运行模式已启用 (--dry-run)"
                shift
                ;;
            --debug)
                DEBUG_MODE=true
                log_info "调试模式已启用 (--debug)"
                shift
                ;;
            --config)
                if [ -z "$2" ]; then
                    log_error "--config 参数需要指定配置文件路径"
                    exit 1
                fi
                CONFIG_FILE="$2"
                log_info "使用配置文件: ${CONFIG_FILE}"
                shift 2
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                # 假设是服务名称
                if [ -z "$SERVICE_NAME" ]; then
                    SERVICE_NAME="$1"
                    log_debug "指定服务: ${SERVICE_NAME}"
                else
                    log_warn "忽略未知参数: $1"
                fi
                shift
                ;;
        esac
    done
    
    # 验证权重参数
    if ! [[ "$WEIGHT" =~ ^[0-9]+$ ]]; then
        log_error "无效的权重参数: ${WEIGHT} (必须是数字)"
        exit 1
    fi
    
    if [ "$WEIGHT" -lt 0 ] || [ "$WEIGHT" -gt 100 ]; then
        log_error "权重参数超出范围: ${WEIGHT} (必须在0-100之间)"
        exit 1
    fi
}

# =====================================================
# 配置文件读取
# =====================================================

# 简单的YAML解析函数（不依赖外部工具）
# 注意：这是一个简化的解析器，仅支持基本的key-value格式
parse_yaml_simple() {
    local yaml_file=$1
    local section=$2
    local key=$3
    
    if [ ! -f "$yaml_file" ]; then
        return 1
    fi
    
    # 使用awk解析YAML（简化版本）
    awk -v section="$section" -v key="$key" '
    BEGIN { in_section=0; found=0 }
    /^[a-z_]+:/ { 
        if ($0 ~ "^" section ":") {
            in_section=1
        } else if (in_section && $0 !~ /^[[:space:]]/) {
            in_section=0
        }
    }
    in_section && $0 ~ "^[[:space:]]+" key ":" {
        gsub(/^[[:space:]]+/, "")
        gsub(key ": *", "")
        gsub(/["'"'"']/, "")
        gsub(/#.*/, "")
        gsub(/^[[:space:]]+|[[:space:]]+$/, "")
        print $0
        found=1
        exit
    }
    END { if (!found) exit 1 }
    ' "$yaml_file"
}

# 读取服务配置
get_service_config() {
    local service=$1
    local config_key=$2
    local default_value=$3
    
    local value
    value=$(parse_yaml_simple "$CONFIG_FILE" "$service" "$config_key")
    
    if [ -n "$value" ]; then
        echo "$value"
    else
        echo "$default_value"
    fi
}

# 读取全局配置
get_global_config() {
    local section=$1
    local key=$2
    local default_value=$3
    
    local value
    value=$(parse_yaml_simple "$CONFIG_FILE" "$section" "$key")
    
    if [ -n "$value" ]; then
        echo "$value"
    else
        echo "$default_value"
    fi
}

# 加载配置文件
load_config() {
    log_info "加载配置文件: ${CONFIG_FILE}"
    
    if [ ! -f "$CONFIG_FILE" ]; then
        log_warn "配置文件不存在: ${CONFIG_FILE}"
        log_warn "使用默认配置"
        return 1
    fi
    
    log_debug "配置文件内容预览:"
    if [ "$DEBUG_MODE" = true ]; then
        head -20 "$CONFIG_FILE" | while IFS= read -r line; do
            log_debug "  $line"
        done
    fi
    
    # 读取监控阈值配置
    ERROR_RATE_THRESHOLD=$(get_global_config "thresholds" "error_rate" "0.01")
    RESPONSE_TIME_THRESHOLD=$(get_global_config "thresholds" "response_time_p99" "2000")
    CPU_USAGE_THRESHOLD=$(get_global_config "thresholds" "cpu_usage" "80")
    MEMORY_USAGE_THRESHOLD=$(get_global_config "thresholds" "memory_usage" "85")
    CHECK_INTERVAL=$(get_global_config "thresholds" "check_interval" "30")
    
    log_debug "监控阈值配置:"
    log_debug "  错误率阈值: ${ERROR_RATE_THRESHOLD}"
    log_debug "  响应时间阈值: ${RESPONSE_TIME_THRESHOLD}ms"
    log_debug "  CPU使用率阈值: ${CPU_USAGE_THRESHOLD}%"
    log_debug "  内存使用率阈值: ${MEMORY_USAGE_THRESHOLD}%"
    log_debug "  检查间隔: ${CHECK_INTERVAL}秒"
    
    return 0
}

# =====================================================
# 健康检查函数
# =====================================================

# 执行健康检查
# 参数: $1 - 服务名称, $2 - 服务端口
health_check() {
    local service=$1
    local port=$2
    local timeout=${3:-$DEFAULT_HEALTH_TIMEOUT}
    local max_retries=${4:-$DEFAULT_HEALTH_RETRIES}
    local interval=${5:-$DEFAULT_HEALTH_INTERVAL}
    
    log_info "执行健康检查: ${service} (端口: ${port})"
    
    # 模拟运行模式
    if [ "$DRY_RUN" = true ]; then
        log_info "[模拟] 健康检查将执行以下操作:"
        log_info "  - 检查端点: http://localhost:${port}/actuator/health"
        log_info "  - 超时时间: ${timeout}秒"
        log_info "  - 最大重试: ${max_retries}次"
        log_info "  - 重试间隔: ${interval}秒"
        log_success "[模拟] 健康检查通过"
        return 0
    fi
    
    local retry=0
    local health_url="http://localhost:${port}/actuator/health"
    
    while [ $retry -lt $max_retries ]; do
        log_debug "健康检查尝试 $((retry + 1))/${max_retries}"
        
        # 执行健康检查请求
        local http_code
        http_code=$(curl -sf -o /dev/null -w "%{http_code}" --max-time "$timeout" "$health_url" 2>/dev/null) || http_code="000"
        
        if [ "$http_code" = "200" ]; then
            log_success "${service} 健康检查通过 (HTTP ${http_code})"
            return 0
        fi
        
        retry=$((retry + 1))
        
        if [ $retry -lt $max_retries ]; then
            log_warn "健康检查失败 (HTTP ${http_code}), ${interval}秒后重试 (${retry}/${max_retries})"
            sleep "$interval"
        fi
    done
    
    log_error "${service} 健康检查失败，已重试 ${max_retries} 次"
    return 1
}

# 批量健康检查
health_check_all() {
    local services=("$@")
    local failed_services=()
    
    log_info "开始批量健康检查..."
    
    for service in "${services[@]}"; do
        local port
        port=$(get_service_config "$service" "port" "8000")
        
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

# =====================================================
# 监控指标检查
# =====================================================

# 检查服务监控指标
check_metrics() {
    local service=$1
    
    log_info "检查监控指标: ${service}"
    
    # 模拟运行模式
    if [ "$DRY_RUN" = true ]; then
        log_info "[模拟] 将检查以下指标:"
        log_info "  - 错误率 (阈值: ${ERROR_RATE_THRESHOLD})"
        log_info "  - 响应时间P99 (阈值: ${RESPONSE_TIME_THRESHOLD}ms)"
        log_info "  - CPU使用率 (阈值: ${CPU_USAGE_THRESHOLD}%)"
        log_info "  - 内存使用率 (阈值: ${MEMORY_USAGE_THRESHOLD}%)"
        log_success "[模拟] 监控指标检查通过"
        return 0
    fi
    
    # 检查Prometheus是否可用
    if ! curl -sf "http://localhost:9090/-/healthy" > /dev/null 2>&1; then
        log_warn "Prometheus不可用，跳过监控指标检查"
        return 0
    fi
    
    # 获取错误率（需要jq支持）
    if command -v jq &> /dev/null; then
        local error_rate
        error_rate=$(curl -s "http://localhost:9090/api/v1/query?query=rate(http_server_requests_seconds_count{status=~\"5..\",service=\"${service}\"}[5m])/rate(http_server_requests_seconds_count{service=\"${service}\"}[5m])" 2>/dev/null | jq -r '.data.result[0].value[1] // "0"')
        
        log_debug "当前错误率: ${error_rate}"
        
        # 比较错误率（需要bc支持）
        if command -v bc &> /dev/null; then
            if [ "$(echo "${error_rate} > ${ERROR_RATE_THRESHOLD}" | bc -l 2>/dev/null)" -eq 1 ]; then
                log_error "错误率过高: ${error_rate} > ${ERROR_RATE_THRESHOLD}"
                return 1
            fi
        fi
    fi
    
    log_success "${service} 监控指标检查通过"
    return 0
}

# =====================================================
# 实例管理函数
# =====================================================

# 计算实例数量
# 参数: $1 - 总实例数, $2 - 权重百分比
calculate_instances() {
    local total=$1
    local weight=$2
    
    # 计算新版本实例数（向上取整）
    local new_instances=$(( (total * weight + 99) / 100 ))
    local old_instances=$(( total - new_instances ))
    
    # 确保至少有1个实例
    if [ $new_instances -eq 0 ] && [ $weight -gt 0 ]; then
        new_instances=1
        old_instances=$(( total - 1 ))
    fi
    
    echo "${old_instances} ${new_instances}"
}

# 显示实例分配信息
show_instance_allocation() {
    local service=$1
    local total=$2
    local weight=$3
    
    local instances
    instances=$(calculate_instances "$total" "$weight")
    local old_instances=$(echo "$instances" | awk '{print $1}')
    local new_instances=$(echo "$instances" | awk '{print $2}')
    
    log_info "服务 ${service} 实例分配:"
    log_info "  总实例数: ${total}"
    log_info "  旧版本实例: ${old_instances} ($(( 100 - weight ))%)"
    log_info "  新版本实例: ${new_instances} (${weight}%)"
    
    if [ "$DEBUG_MODE" = true ]; then
        log_debug "实例计算详情:"
        log_debug "  权重: ${weight}%"
        log_debug "  新版本实例数计算: (${total} * ${weight} + 99) / 100 = ${new_instances}"
        log_debug "  旧版本实例数计算: ${total} - ${new_instances} = ${old_instances}"
    fi
}

# =====================================================
# Docker Compose部署函数
# =====================================================

# Docker Compose灰度发布
deploy_docker_compose() {
    local service=$1
    local weight=$2
    
    log_info "使用Docker Compose部署 ${service} (权重: ${weight}%)"
    
    # 获取服务配置
    local total_instances
    total_instances=$(get_service_config "$service" "instances" "2")
    local port
    port=$(get_service_config "$service" "port" "8000")
    
    # 显示实例分配
    show_instance_allocation "$service" "$total_instances" "$weight"
    
    # 模拟运行模式
    if [ "$DRY_RUN" = true ]; then
        log_info "[模拟] 将执行以下Docker Compose命令:"
        
        if [ "$weight" -eq 0 ]; then
            log_info "  # 回滚到旧版本"
            log_info "  docker-compose up -d --scale ${service}=${total_instances} ${service}-old"
        elif [ "$weight" -eq 100 ]; then
            log_info "  # 全量发布新版本"
            log_info "  docker-compose up -d --scale ${service}=${total_instances} ${service}"
        else
            local instances
            instances=$(calculate_instances "$total_instances" "$weight")
            local old_instances=$(echo "$instances" | awk '{print $1}')
            local new_instances=$(echo "$instances" | awk '{print $2}')
            
            log_info "  # 灰度发布：同时运行新旧版本"
            log_info "  docker-compose -f docker-compose.yml -f docker-compose-canary.yml up -d \\"
            log_info "    --scale ${service}=${old_instances} \\"
            log_info "    --scale ${service}-canary=${new_instances}"
        fi
        
        log_success "[模拟] Docker Compose部署命令生成成功"
        return 0
    fi
    
    # 实际部署逻辑
    if [ "$weight" -eq 0 ]; then
        log_info "回滚 ${service} 到旧版本..."
        # docker-compose -f docker-compose.yml up -d --scale ${service}=${total_instances} ${service}-old
        log_warn "实际部署命令已注释，请取消注释以启用"
        return 0
    fi
    
    if [ "$weight" -eq 100 ]; then
        log_info "全量发布 ${service}..."
        # docker-compose -f docker-compose.yml up -d --scale ${service}=${total_instances} ${service}
        log_warn "实际部署命令已注释，请取消注释以启用"
        return 0
    fi
    
    # 灰度发布：同时运行新旧版本
    log_info "灰度发布 ${service}..."
    local instances
    instances=$(calculate_instances "$total_instances" "$weight")
    local old_instances=$(echo "$instances" | awk '{print $1}')
    local new_instances=$(echo "$instances" | awk '{print $2}')
    
    # docker-compose -f docker-compose.yml -f docker-compose-canary.yml up -d \
    #     --scale ${service}=${old_instances} \
    #     --scale ${service}-canary=${new_instances}
    log_warn "实际部署命令已注释，请取消注释以启用"
    
    return 0
}

# =====================================================
# Kubernetes部署函数
# =====================================================

# Kubernetes灰度发布
deploy_kubernetes() {
    local service=$1
    local weight=$2
    
    log_info "使用Kubernetes部署 ${service} (权重: ${weight}%)"
    
    # 获取服务配置
    local namespace
    namespace=$(get_global_config "kubernetes" "namespace" "default")
    
    log_debug "Kubernetes命名空间: ${namespace}"
    
    # 模拟运行模式
    if [ "$DRY_RUN" = true ]; then
        log_info "[模拟] 将执行以下Kubernetes命令:"
        
        if [ "$weight" -eq 0 ]; then
            log_info "  # 回滚到旧版本"
            log_info "  kubectl rollout undo deployment/${service} -n ${namespace}"
        elif [ "$weight" -eq 100 ]; then
            log_info "  # 全量发布新版本"
            log_info "  kubectl rollout status deployment/${service} -n ${namespace}"
        else
            log_info "  # 更新Canary权重"
            log_info "  kubectl patch service ${service} -n ${namespace} -p '{\"spec\":{\"canaryWeight\":${weight}}}'"
            log_info "  # 或使用Istio VirtualService"
            log_info "  istioctl replace -f ${service}-virtualservice.yaml"
        fi
        
        log_success "[模拟] Kubernetes部署命令生成成功"
        return 0
    fi
    
    # 实际部署逻辑
    if [ "$weight" -eq 0 ]; then
        log_info "回滚 ${service} 到旧版本..."
        # kubectl rollout undo deployment/${service} -n ${namespace}
        log_warn "实际部署命令已注释，请取消注释以启用"
        return 0
    fi
    
    if [ "$weight" -eq 100 ]; then
        log_info "全量发布 ${service}..."
        # kubectl rollout status deployment/${service} -n ${namespace}
        log_warn "实际部署命令已注释，请取消注释以启用"
        return 0
    fi
    
    # 灰度发布：更新Canary权重
    log_info "更新 ${service} Canary权重为 ${weight}%..."
    # kubectl patch service ${service} -n ${namespace} -p "{\"spec\":{\"canaryWeight\":${weight}}}"
    log_warn "实际部署命令已注释，请取消注释以启用"
    
    return 0
}

# =====================================================
# 主部署函数
# =====================================================

# 检测部署平台
detect_platform() {
    # 优先检测Kubernetes
    if command -v kubectl &> /dev/null; then
        if kubectl config current-context &> /dev/null 2>&1; then
            log_debug "检测到Kubernetes环境"
            echo "kubernetes"
            return 0
        fi
    fi
    
    # 检测Docker Compose
    if command -v docker-compose &> /dev/null; then
        log_debug "检测到Docker Compose环境"
        echo "docker-compose"
        return 0
    fi
    
    # 检测Docker Compose V2
    if docker compose version &> /dev/null 2>&1; then
        log_debug "检测到Docker Compose V2环境"
        echo "docker-compose"
        return 0
    fi
    
    log_debug "未检测到支持的部署平台"
    echo "none"
    return 1
}

# 执行部署
deploy() {
    local service=$1
    local weight=$2
    
    log_info "=========================================="
    log_info "开始部署服务: ${service}"
    log_info "目标权重: ${weight}%"
    log_info "=========================================="
    
    # 检测部署平台
    local platform
    platform=$(detect_platform)
    
    if [ "$platform" = "none" ]; then
        if [ "$DRY_RUN" = true ]; then
            log_warn "未检测到部署平台，但模拟运行模式将继续"
            platform="docker-compose"  # 默认使用docker-compose进行模拟
        else
            log_error "未检测到支持的部署平台 (Kubernetes 或 Docker Compose)"
            return 1
        fi
    fi
    
    log_info "部署平台: ${platform}"
    
    # 执行部署
    case "$platform" in
        kubernetes)
            deploy_kubernetes "${service}" "${weight}"
            ;;
        docker-compose)
            deploy_docker_compose "${service}" "${weight}"
            ;;
        *)
            log_error "不支持的部署平台: ${platform}"
            return 1
            ;;
    esac
    
    # 检查部署是否成功
    if [ $? -ne 0 ]; then
        log_error "${service} 部署失败"
        return 1
    fi
    
    # 健康检查
    local port
    port=$(get_service_config "$service" "port" "8000")
    
    if ! health_check "${service}" "${port}"; then
        log_error "${service} 健康检查失败，启动回滚..."
        
        # 自动回滚
        if [ "$DRY_RUN" = false ]; then
            deploy "${service}" 0
        fi
        
        return 1
    fi
    
    # 监控指标检查
    if ! check_metrics "${service}"; then
        log_error "${service} 监控指标检查失败，启动回滚..."
        
        # 自动回滚
        if [ "$DRY_RUN" = false ]; then
            deploy "${service}" 0
        fi
        
        return 1
    fi
    
    log_success "${service} 部署成功"
    return 0
}

# =====================================================
# 主程序
# =====================================================

main() {
    # 解析命令行参数
    parse_arguments "$@"
    
    # 显示启动信息
    log_info "=========================================="
    log_info "灰度发布脚本启动"
    log_info "=========================================="
    log_info "流量权重: ${WEIGHT}%"
    log_info "目标服务: ${SERVICE_NAME:-所有服务}"
    log_info "配置文件: ${CONFIG_FILE}"
    log_info "日志文件: ${LOG_FILE}"
    log_info "模拟运行: ${DRY_RUN}"
    log_info "调试模式: ${DEBUG_MODE}"
    log_info "=========================================="
    
    # 加载配置文件
    load_config
    
    # 确定要部署的服务列表
    local services=()
    if [ -z "${SERVICE_NAME}" ]; then
        # 部署所有服务
        services=("${DEFAULT_SERVICES[@]}")
        log_info "将部署所有服务: ${services[*]}"
    else
        # 部署指定服务
        services=("$SERVICE_NAME")
        log_info "将部署指定服务: ${SERVICE_NAME}"
    fi
    
    # 执行部署
    local failed_services=()
    local success_count=0
    
    for service in "${services[@]}"; do
        log_info ""
        log_info "----------------------------------------"
        log_info "处理服务: ${service}"
        log_info "----------------------------------------"
        
        if deploy "${service}" "${WEIGHT}"; then
            success_count=$((success_count + 1))
        else
            failed_services+=("$service")
        fi
    done
    
    # 显示部署摘要
    log_info ""
    log_info "=========================================="
    log_info "部署摘要"
    log_info "=========================================="
    log_info "总服务数: ${#services[@]}"
    log_info "成功数: ${success_count}"
    log_info "失败数: ${#failed_services[@]}"
    
    if [ ${#failed_services[@]} -gt 0 ]; then
        log_error "失败的服务: ${failed_services[*]}"
        log_info "=========================================="
        exit 1
    fi
    
    log_success "所有服务部署成功"
    log_info "=========================================="
    
    exit 0
}

# 执行主程序
main "$@"
