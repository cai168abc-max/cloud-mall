#!/bin/bash

# =====================================================
# 回滚脚本测试工具
# 功能：测试回滚脚本的各项功能
# 使用：./test-rollback.sh
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

# 测试计数
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 测试函数
run_test() {
    local test_name=$1
    local test_command=$2
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}测试 ${TOTAL_TESTS}: ${test_name}${NC}"
    echo -e "${BLUE}========================================${NC}"
    
    echo -e "${YELLOW}执行命令: ${test_command}${NC}"
    echo ""
    
    if eval "$test_command"; then
        echo -e "\n${GREEN}✓ 测试通过${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "\n${RED}✗ 测试失败${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
}

# 初始化测试环境
init_test_env() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}初始化测试环境${NC}"
    echo -e "${BLUE}========================================${NC}"
    
    # 设置执行权限
    chmod +x "${SCRIPT_DIR}/rollback.sh"
    chmod +x "${SCRIPT_DIR}/init-version.sh"
    
    # 初始化版本管理
    echo -e "${YELLOW}初始化版本管理...${NC}"
    "${SCRIPT_DIR}/init-version.sh"
    
    echo -e "${GREEN}测试环境初始化完成${NC}\n"
}

# 测试帮助信息
test_help() {
    run_test "帮助信息" "${SCRIPT_DIR}/rollback.sh --help"
}

# 测试版本列表
test_list_versions() {
    run_test "列出所有版本" "${SCRIPT_DIR}/rollback.sh list"
    run_test "列出指定服务版本" "${SCRIPT_DIR}/rollback.sh list --service service-order"
}

# 测试当前版本
test_current_version() {
    run_test "显示当前版本（所有服务）" "${SCRIPT_DIR}/rollback.sh current"
    run_test "显示当前版本（指定服务）" "${SCRIPT_DIR}/rollback.sh current --service service-order"
}

# 测试版本比较
test_compare_versions() {
    # 获取两个版本号
    local versions=($(ls -t "${PROJECT_ROOT}/deployments/versions/service-order"/*.json 2>/dev/null | grep -v "current.json" | head -n 2))
    
    if [ ${#versions[@]} -ge 2 ]; then
        local v1=$(basename "${versions[0]}" .json)
        local v2=$(basename "${versions[1]}" .json)
        run_test "版本比较" "${SCRIPT_DIR}/rollback.sh compare ${v1} ${v2} --service service-order"
    else
        echo -e "${YELLOW}跳过版本比较测试（版本数量不足）${NC}"
    fi
}

# 测试清理旧版本
test_cleanup() {
    run_test "清理旧版本（模拟）" "${SCRIPT_DIR}/rollback.sh cleanup --dry-run"
}

# 测试回滚（模拟）
test_rollback_dry_run() {
    run_test "回滚到上一版本（模拟）" "${SCRIPT_DIR}/rollback.sh rollback --service service-order --dry-run"
    run_test "自动回滚（模拟）" "${SCRIPT_DIR}/rollback.sh rollback --auto --service service-order --dry-run"
}

# 测试调试模式
test_debug_mode() {
    run_test "调试模式" "${SCRIPT_DIR}/rollback.sh current --service service-order --debug"
}

# 测试错误处理
test_error_handling() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}测试错误处理${NC}"
    echo -e "${BLUE}========================================${NC}"
    
    # 测试无效服务名
    echo -e "${YELLOW}测试无效服务名...${NC}"
    if "${SCRIPT_DIR}/rollback.sh" list --service invalid-service 2>&1 | grep -q "无效的服务名称"; then
        echo -e "${GREEN}✓ 无效服务名检测正常${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}✗ 无效服务名检测失败${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

# 显示测试结果
show_results() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}测试结果汇总${NC}"
    echo -e "${BLUE}========================================${NC}"
    
    echo -e "总测试数: ${TOTAL_TESTS}"
    echo -e "${GREEN}通过: ${PASSED_TESTS}${NC}"
    echo -e "${RED}失败: ${FAILED_TESTS}${NC}"
    
    local pass_rate=0
    if [ $TOTAL_TESTS -gt 0 ]; then
        pass_rate=$((PASSED_TESTS * 100 / TOTAL_TESTS))
    fi
    
    echo -e "通过率: ${pass_rate}%"
    
    if [ $FAILED_TESTS -eq 0 ]; then
        echo -e "\n${GREEN}所有测试通过！${NC}"
        return 0
    else
        echo -e "\n${RED}部分测试失败，请检查日志${NC}"
        return 1
    fi
}

# 主函数
main() {
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}回滚脚本测试工具${NC}"
    echo -e "${GREEN}========================================${NC}"
    
    # 初始化测试环境
    init_test_env
    
    # 执行测试
    test_help
    test_list_versions
    test_current_version
    test_compare_versions
    test_cleanup
    test_rollback_dry_run
    test_debug_mode
    test_error_handling
    
    # 显示结果
    show_results
}

# 执行主函数
main
