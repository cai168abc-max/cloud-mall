# CI/CD 使用指南

> **文档版本**: 1.0
> **最后更新**: 2026-05-13
> **适用环境**: 开发、预发布、生产环境

---

## 目录

1. [CI/CD流水线概述](#1-cicd流水线概述)
2. [流水线阶段说明](#2-流水线阶段说明)
3. [环境配置说明](#3-环境配置说明)
4. [GitHub Secrets配置](#4-github-secrets配置)
5. [GitHub Environment保护规则](#5-github-environment保护规则)
6. [手动触发部署流程](#6-手动触发部署流程)
7. [常见问题和故障排查](#7-常见问题和故障排查)
8. [最佳实践建议](#8-最佳实践建议)

---

## 1. CI/CD流水线概述

### 1.1 流水线架构

CloudTry项目采用GitHub Actions作为CI/CD平台，实现了从代码提交到生产部署的全自动化流程。

```
代码提交 → 代码检查 → 单元测试 → 构建 → Docker镜像构建 → 部署
    ↓          ↓          ↓         ↓          ↓              ↓
  触发CI    Checkstyle  JUnit    Maven     Docker Build    灰度发布
```

### 1.2 流水线特性

- **自动化构建**: 代码提交自动触发构建流程
- **多环境部署**: 支持dev、staging、production三个环境
- **灰度发布**: 生产环境采用灰度发布策略（10% → 50% → 100%）
- **自动回滚**: 监控指标异常时自动回滚
- **审批机制**: 生产环境部署需要人工审批
- **通知机制**: 部署结果自动通知

### 1.3 流水线配置文件

- **配置文件**: `.github/workflows/ci-cd.yml`
- **触发条件**:
  - `main` 分支推送 → 部署到staging和production
  - `develop` 分支推送 → 部署到dev
  - Pull Request → 执行CI检查
  - 手动触发 → 可选择任意环境

---

## 2. 流水线阶段说明

### 2.1 CI阶段（持续集成）

#### 2.1.1 代码检查（code-check）

**功能**: 代码质量检查和静态分析

**执行步骤**:
1. **Checkout代码**: 拉取最新代码
2. **设置JDK 17**: 配置Java开发环境
3. **Maven缓存**: 加速依赖下载
4. **POM验证**: 验证Maven配置文件
5. **Checkstyle检查**: 代码风格检查
6. **SpotBugs检查**: 静态代码分析

**输出**:
- Checkstyle报告: `checkstyle-report`
- SpotBugs报告: 构建日志中

**注意事项**:
- Checkstyle和SpotBugs检查失败不会中断构建（`continue-on-error: true`）
- 检查结果会作为警告显示在构建日志中

#### 2.1.2 单元测试（unit-test）

**功能**: 执行单元测试并生成报告

**执行步骤**:
1. **Checkout代码**: 拉取最新代码
2. **设置JDK 17**: 配置Java开发环境
3. **Maven缓存**: 加速依赖下载
4. **执行测试**: `mvn test`
5. **生成报告**: JUnit测试报告

**输出**:
- 测试报告: `test-results`
- 测试覆盖率: 在构建日志中显示

**注意事项**:
- 单元测试失败会中断构建
- 可通过`skip_tests`参数跳过测试（手动触发时）

#### 2.1.3 构建（build）

**功能**: 编译打包项目

**执行步骤**:
1. **Checkout代码**: 拉取最新代码
2. **设置JDK 17**: 配置Java开发环境
3. **Maven缓存**: 加速依赖下载
4. **构建项目**: `mvn clean package -DskipTests`
5. **上传构件**: 保存JAR文件

**输出**:
- 构建构件: `build-artifacts`
  - `services/service-order/target/*.jar`
  - `services/service-product/target/*.jar`
  - `services/service-user/target/*.jar`
  - `gateway/target/*.jar`

**注意事项**:
- 构建失败会中断流水线
- 构件保留7天

### 2.2 CD阶段（持续部署）

#### 2.2.1 Docker镜像构建（docker-build）

**功能**: 构建Docker镜像并推送到镜像仓库

**执行步骤**:
1. **下载构建构件**: 获取上一步的JAR文件
2. **设置Docker Buildx**: 配置Docker构建工具
3. **登录Docker Hub**: 使用Secrets中的凭据
4. **提取版本号**: 格式为 `日期-Git SHA`
5. **构建并推送镜像**:
   - `cloudtry-gateway`
   - `cloudtry-service-order`
   - `cloudtry-service-product`
   - `cloudtry-service-user`

**输出**:
- Docker镜像标签:
  - `latest`: 最新版本
  - `YYYYMMDD-git-sha`: 具体版本

**触发条件**:
- `main` 或 `develop` 分支推送

**注意事项**:
- 需要配置`DOCKER_USERNAME`和`DOCKER_PASSWORD` Secrets
- 使用GitHub Actions缓存加速构建

#### 2.2.2 部署到开发环境（deploy-dev）

**功能**: 部署到开发环境

**执行步骤**:
1. **Checkout代码**: 拉取部署脚本
2. **部署服务**: 使用Docker Compose部署
3. **健康检查**: 等待服务启动并检查健康状态
4. **生成摘要**: 输出部署结果

**环境信息**:
- 环境名称: `development`
- 访问地址: `https://dev.cloudtry.example.com`

**触发条件**:
- `develop` 分支推送

**注意事项**:
- 实际部署命令已注释，需要根据实际环境配置
- 健康检查等待30秒

#### 2.2.3 部署到预发布环境（deploy-staging）

**功能**: 部署到预发布环境

**执行步骤**:
1. **Checkout代码**: 拉取部署脚本
2. **部署服务**: 使用Docker Compose部署
3. **健康检查**: 等待服务启动并检查健康状态
4. **生成摘要**: 输出部署结果

**环境信息**:
- 环境名称: `staging`
- 访问地址: `https://staging.cloudtry.example.com`

**触发条件**:
- `main` 分支推送

**注意事项**:
- 预发布环境部署完成后，会自动触发生产环境部署
- 需要配置Environment保护规则

#### 2.2.4 部署到生产环境（deploy-production）

**功能**: 使用灰度发布策略部署到生产环境

**执行步骤**:
1. **Checkout代码**: 拉取部署脚本
2. **设置脚本权限**: `chmod +x scripts/deploy/canary-deploy.sh`
3. **第一阶段灰度**: 10%流量到新版本
4. **监控5分钟**: 观察监控指标
5. **第二阶段灰度**: 50%流量到新版本
6. **监控3分钟**: 观察监控指标
7. **全量发布**: 100%流量到新版本
8. **最终健康检查**: 验证服务状态

**环境信息**:
- 环境名称: `production`
- 访问地址: `https://cloudtry.example.com`

**灰度发布策略**:
```
阶段1: 10%流量 → 监控5分钟 → 验证指标
阶段2: 50%流量 → 监控3分钟 → 验证指标
阶段3: 100%流量 → 最终健康检查
```

**触发条件**:
- `main` 分支推送
- staging环境部署成功

**注意事项**:
- 需要配置Environment保护规则（审批）
- 监控指标异常会触发自动回滚
- 实际部署命令已注释，需要根据实际环境配置

#### 2.2.5 通知（notify）

**功能**: 发送部署结果通知

**执行步骤**:
1. **成功通知**: 部署成功时发送
2. **失败通知**: 部署失败时发送
3. **取消通知**: 部署取消时发送

**通知方式**:
- 邮件（需配置）
- 钉钉（需配置Webhook）
- 企业微信（需配置Webhook）

**注意事项**:
- 通知代码已注释，需要根据实际需求启用
- 需要配置`WEBHOOK_URL` Secret

---

## 3. 环境配置说明

### 3.1 环境划分

| 环境 | 分支 | 用途 | 访问地址 | 审批 |
|------|------|------|----------|------|
| development | develop | 开发测试 | dev.cloudtry.example.com | 否 |
| staging | main | 预发布测试 | staging.cloudtry.example.com | 否 |
| production | main | 生产环境 | cloudtry.example.com | 是 |

### 3.2 环境配置文件

每个服务都有对应的环境配置文件：

```
services/service-order/src/main/resources/
├── application.yml          # 通用配置
├── application-dev.yml      # 开发环境配置
├── application-test.yml     # 测试环境配置
└── application-prod.yml     # 生产环境配置
```

### 3.3 环境变量

#### 3.3.1 开发环境（dev）

```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/cloudtry_order
    username: root
    password: root
  data:
    redis:
      host: localhost
      port: 6379
```

#### 3.3.2 预发布环境（staging）

```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:mysql://staging-db:3306/cloudtry_order
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  data:
    redis:
      host: staging-redis
      port: 6379
```

#### 3.3.3 生产环境（production）

```yaml
# application-prod.yml
spring:
  datasource:
    url: jdbc:mysql://prod-db:3306/cloudtry_order
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  data:
    redis:
      host: prod-redis
      port: 6379
      password: ${REDIS_PASSWORD}
```

---

## 4. GitHub Secrets配置

### 4.1 必需的Secrets

#### 4.1.1 Docker Hub凭据

**Secret名称**: `DOCKER_USERNAME`
**说明**: Docker Hub用户名
**获取方式**:
1. 访问 https://hub.docker.com/
2. 注册或登录账号
3. 使用您的用户名

**Secret名称**: `DOCKER_PASSWORD`
**说明**: Docker Hub密码或访问令牌
**获取方式**:
1. 登录Docker Hub
2. 进入 Account Settings → Security
3. 点击 "New Access Token"
4. 创建令牌（权限选择：Read, Write, Delete）
5. 复制令牌值

**配置步骤**:
```bash
# 1. 进入GitHub仓库
# 2. 点击 Settings → Secrets and variables → Actions
# 3. 点击 "New repository secret"
# 4. 添加以下两个Secrets:
#    - Name: DOCKER_USERNAME
#      Value: your_docker_username
#    - Name: DOCKER_PASSWORD
#      Value: your_docker_access_token
```

#### 4.1.2 Webhook URL（可选）

**Secret名称**: `WEBHOOK_URL`
**说明**: 通知Webhook地址（钉钉/企业微信）
**获取方式**:

**钉钉**:
1. 打开钉钉群聊
2. 点击群设置 → 智能群助手 → 添加机器人
3. 选择 "自定义" 机器人
4. 设置机器人名称和头像
5. 安全设置选择 "自定义关键词"，输入 "部署"
6. 复制Webhook地址

**企业微信**:
1. 打开企业微信群聊
2. 点击群设置 → 群机器人 → 添加机器人
3. 设置机器人名称
4. 复制Webhook地址

### 4.2 配置Secrets步骤

1. **进入仓库设置**:
   - 打开GitHub仓库
   - 点击 `Settings` 标签

2. **进入Secrets页面**:
   - 左侧菜单选择 `Secrets and variables`
   - 点击 `Actions`

3. **添加Secret**:
   - 点击 `New repository secret` 按钮
   - 输入 `Name` 和 `Value`
   - 点击 `Add secret` 保存

4. **验证配置**:
   - 在Actions工作流中引用: `${{ secrets.DOCKER_USERNAME }}`
   - 运行一次构建验证是否成功

### 4.3 Secrets安全建议

- **使用访问令牌**: 不要使用Docker Hub密码，使用访问令牌
- **定期轮换**: 每3-6个月更换一次令牌
- **最小权限**: 令牌权限仅授予必需的权限
- **不要提交**: 永远不要将Secrets提交到代码库
- **审计日志**: 定期检查Secrets使用记录

---

## 5. GitHub Environment保护规则

### 5.1 创建Environment

1. **进入仓库设置**:
   - 打开GitHub仓库
   - 点击 `Settings` 标签

2. **进入Environments页面**:
   - 左侧菜单选择 `Environments`

3. **创建新Environment**:
   - 点击 `New environment` 按钮
   - 输入环境名称: `production`
   - 点击 `Configure`

### 5.2 配置保护规则

#### 5.2.1 必需审批者

**配置步骤**:
1. 在Environment配置页面
2. 找到 "Required reviewers" 部分
3. 勾选 "Required reviewers"
4. 添加审批者（用户或团队）
5. 点击 "Save protection rules"

**说明**:
- 最多可添加6个审批者或团队
- 审批者必须对仓库有写入权限
- 审批者不能是触发部署的人

#### 5.2.2 等待时间

**配置步骤**:
1. 在 "Wait timer" 部分
2. 设置等待时间（分钟）
3. 点击 "Save protection rules"

**说明**:
- 部署开始前等待指定时间
- 给审批者时间审查变更

#### 5.2.3 部署分支限制

**配置步骤**:
1. 在 "Deployment branches" 部分
2. 选择 "Selected branches"
3. 添加分支模式: `main`
4. 点击 "Save protection rules"

**说明**:
- 只有指定分支可以部署到此环境
- 支持通配符模式

#### 5.2.4 环境Secrets

**配置步骤**:
1. 在 "Environment secrets" 部分
2. 点击 "Add secret"
3. 输入Secret名称和值
4. 点击 "Add secret"

**说明**:
- Environment级别的Secrets仅在此环境中可用
- 优先级高于Repository Secrets
- 适合存储环境特定的敏感信息

### 5.3 Environment配置示例

**生产环境配置**:
```
Environment name: production
URL: https://cloudtry.example.com

Protection rules:
  ✓ Required reviewers: devops-team, tech-lead
  ✓ Wait timer: 5 minutes
  ✓ Deployment branches: main

Environment secrets:
  - DB_PASSWORD: ***
  - REDIS_PASSWORD: ***
  - JWT_SECRET: ***
```

**预发布环境配置**:
```
Environment name: staging
URL: https://staging.cloudtry.example.com

Protection rules:
  ✓ Deployment branches: main

Environment secrets:
  - DB_PASSWORD: ***
  - REDIS_PASSWORD: ***
```

---

## 6. 手动触发部署流程

### 6.1 触发方式

#### 6.1.1 通过GitHub UI

1. **进入Actions页面**:
   - 打开GitHub仓库
   - 点击 `Actions` 标签

2. **选择工作流**:
   - 左侧选择 "CI/CD Pipeline"
   - 点击右侧 "Run workflow" 按钮

3. **配置参数**:
   - **Branch**: 选择要部署的分支（main/develop）
   - **environment**: 选择部署环境（dev/staging/production）
   - **skip_tests**: 是否跳过单元测试

4. **运行工作流**:
   - 点击 "Run workflow" 按钮
   - 等待工作流启动

#### 6.1.2 通过GitHub CLI

```bash
# 安装GitHub CLI
# Windows: winget install GitHub.cli
# macOS: brew install gh
# Linux: sudo apt install gh

# 登录GitHub
gh auth login

# 触发工作流
gh workflow run ci-cd.yml \
  -f environment=staging \
  -f skip_tests=false

# 查看工作流状态
gh run list --workflow=ci-cd.yml
gh run view
```

#### 6.1.3 通过API调用

```bash
# 获取GitHub Token
# Settings → Developer settings → Personal access tokens → Tokens (classic)

# 触发工作流
curl -X POST \
  -H "Accept: application/vnd.github.v3+json" \
  -H "Authorization: token YOUR_GITHUB_TOKEN" \
  https://api.github.com/repos/OWNER/REPO/actions/workflows/ci-cd.yml/dispatches \
  -d '{"ref":"main","inputs":{"environment":"production","skip_tests":"false"}}'
```

### 6.2 部署审批流程

#### 6.2.1 审批通知

当部署到生产环境时，审批者会收到通知：
- GitHub通知（网页和邮件）
- 部署请求详情页面

#### 6.2.2 审批步骤

1. **查看部署请求**:
   - 打开GitHub仓库
   - 点击 `Actions` 标签
   - 找到等待审批的工作流
   - 点击进入详情页面

2. **审查变更**:
   - 查看提交记录
   - 查看变更内容
   - 查看测试结果

3. **批准或拒绝**:
   - 点击 "Review deployments" 按钮
   - 选择要批准的环境
   - 添加审批评论
   - 点击 "Approve and deploy" 或 "Reject"

#### 6.2.3 审批超时

- 审批请求默认不会超时
- 可以在Environment设置中配置自动拒绝时间

### 6.3 查看部署日志

#### 6.3.1 实时查看

1. **进入工作流运行页面**:
   - Actions → 选择工作流运行

2. **查看各阶段日志**:
   - 点击左侧的Job名称
   - 展开各个Step查看详细日志
   - 实时滚动显示日志输出

#### 6.3.2 下载日志

1. **在工作流运行页面**
2. 点击右上角 "..." 按钮
3. 选择 "Download log archive"
4. 解压查看完整日志

#### 6.3.3 查看构件

1. **在工作流运行页面**
2. 滚动到页面底部 "Artifacts" 部分
3. 点击构件名称下载
4. 查看测试报告、构建产物等

---

## 7. 常见问题和故障排查

### 7.1 构建失败

#### 7.1.1 Maven依赖下载失败

**症状**:
```
Could not resolve dependencies for project com.atguigu:service-order:jar:1.0.0
Failed to read artifact descriptor for org.springframework.boot:spring-boot-starter-web:jar:3.3.4
```

**原因**:
- Maven仓库访问失败
- 网络问题
- 依赖版本不存在

**解决方案**:
```bash
# 1. 检查pom.xml中的依赖版本
# 2. 清理Maven缓存
mvn dependency:purge-local-repository

# 3. 使用阿里云镜像
# 在pom.xml中添加:
<repositories>
    <repository>
        <id>aliyun</id>
        <url>https://maven.aliyun.com/repository/public</url>
    </repository>
</repositories>

# 4. 检查网络连接
ping repo.maven.apache.org
```

#### 7.1.2 单元测试失败

**症状**:
```
Tests run: 10, Failures: 2, Errors: 0, Skipped: 0
```

**原因**:
- 测试代码有误
- 测试环境配置不正确
- 测试数据问题

**解决方案**:
```bash
# 1. 本地运行测试
mvn test

# 2. 查看失败测试详情
mvn test -Dtest=FailedTestClass

# 3. 跳过测试（仅用于紧急情况）
gh workflow run ci-cd.yml -f skip_tests=true

# 4. 查看测试报告
# 下载 test-results 构件
```

#### 7.1.3 编译错误

**症状**:
```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.11.0:compile
[ERROR] compilation failure
```

**原因**:
- Java版本不匹配
- 代码语法错误
- 缺少依赖

**解决方案**:
```bash
# 1. 检查Java版本
java -version  # 应该是17+

# 2. 检查pom.xml中的Java版本配置
<properties>
    <java.version>17</java.version>
</properties>

# 3. 本地编译测试
mvn clean compile

# 4. 查看详细错误信息
mvn clean compile -X
```

### 7.2 Docker构建失败

#### 7.2.1 Docker登录失败

**症状**:
```
Error: denied: requested access to the resource is denied
```

**原因**:
- Docker Hub凭据错误
- Secrets配置错误
- 访问令牌权限不足

**解决方案**:
```bash
# 1. 验证Docker Hub凭据
echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin

# 2. 检查Secrets配置
# Settings → Secrets → Actions → DOCKER_USERNAME, DOCKER_PASSWORD

# 3. 重新生成访问令牌
# Docker Hub → Account Settings → Security → New Access Token

# 4. 确认令牌权限
# 需要 Read, Write, Delete 权限
```

#### 7.2.2 镜像推送失败

**症状**:
```
denied: requested access to the resource is denied
```

**原因**:
- 镜像仓库不存在
- 用户名错误
- 网络问题

**解决方案**:
```bash
# 1. 在Docker Hub创建仓库
# https://hub.docker.com/ → Create Repository

# 2. 检查镜像标签格式
# 正确格式: username/image-name:tag
docker tag my-image username/cloudtry-gateway:latest

# 3. 测试推送
docker push username/cloudtry-gateway:latest

# 4. 检查网络连接
ping hub.docker.com
```

#### 7.2.3 构建超时

**症状**:
```
Error: The operation was canceled.
```

**原因**:
- 构建时间过长
- 网络慢
- 资源不足

**解决方案**:
```bash
# 1. 优化Dockerfile
# 使用多阶段构建
# 减少层数

# 2. 使用构建缓存
# GitHub Actions自动缓存

# 3. 增加超时时间
# 在workflow中添加:
jobs:
  build:
    timeout-minutes: 60  # 默认360分钟
```

### 7.3 部署失败

#### 7.3.1 健康检查失败

**症状**:
```
Health check failed for service service-order
```

**原因**:
- 服务启动失败
- 端口配置错误
- 依赖服务不可用

**解决方案**:
```bash
# 1. 查看服务日志
docker logs service-order

# 2. 检查端口配置
netstat -tlnp | grep 8000

# 3. 检查依赖服务
# 数据库、Redis、Nacos等

# 4. 手动健康检查
curl http://localhost:8000/actuator/health

# 5. 增加健康检查等待时间
# 在ci-cd.yml中修改sleep时间
```

#### 7.3.2 服务注册失败

**症状**:
```
Service not registered to Nacos
```

**原因**:
- Nacos服务不可用
- 配置错误
- 网络问题

**解决方案**:
```bash
# 1. 检查Nacos服务
curl http://nacos:8848/nacos/v1/ns/service/list

# 2. 检查服务配置
# application.yml中的nacos配置

# 3. 查看服务日志
docker logs service-order | grep nacos

# 4. 检查网络连接
ping nacos
```

#### 7.3.3 灰度发布失败

**症状**:
```
Canary deployment failed, rolling back...
```

**原因**:
- 监控指标异常
- 健康检查失败
- 脚本执行错误

**解决方案**:
```bash
# 1. 查看监控指标
# Prometheus/Grafana

# 2. 检查灰度发布脚本
./scripts/deploy/canary-deploy.sh --debug

# 3. 手动回滚
./scripts/deploy/rollback.sh rollback --service service-order

# 4. 查看部署日志
cat logs/canary-deploy.log
```

### 7.4 审批问题

#### 7.4.1 无法审批

**症状**:
- 找不到审批按钮
- 审批按钮灰色

**原因**:
- 没有审批权限
- 自己触发的部署

**解决方案**:
```bash
# 1. 检查审批权限
# Settings → Environments → production → Required reviewers

# 2. 确认不是自己触发的部署
# 审批者不能是触发者

# 3. 联系仓库管理员添加审批权限
```

#### 7.4.2 审批超时

**症状**:
- 审批请求长时间未处理

**解决方案**:
```bash
# 1. 配置审批通知
# Settings → Notifications

# 2. 设置审批截止时间
# 在Environment中配置

# 3. 联系审批者
# 通过邮件或即时通讯工具
```

---

## 8. 最佳实践建议

### 8.1 代码提交规范

#### 8.1.1 分支管理

```
main (生产分支)
  ├── develop (开发分支)
  │     ├── feature/user-auth (功能分支)
  │     ├── feature/order-system (功能分支)
  │     └── bugfix/login-error (修复分支)
  └── hotfix/critical-bug (紧急修复分支)
```

**建议**:
- `main` 分支保护，禁止直接推送
- 所有变更通过Pull Request合并
- 功能分支从`develop`创建
- 紧急修复从`main`创建

#### 8.1.2 提交信息规范

使用约定式提交：

```
<type>(<scope>): <subject>

<body>

<footer>
```

**类型**:
- `feat`: 新功能
- `fix`: 修复bug
- `docs`: 文档变更
- `style`: 代码格式
- `refactor`: 重构
- `test`: 测试
- `chore`: 构建/工具

**示例**:
```
feat(order): 添加订单超时自动取消功能

- 实现订单超时检测
- 添加定时任务
- 更新订单状态

Closes #123
```

### 8.2 测试策略

#### 8.2.1 单元测试

**覆盖率要求**:
- 核心业务逻辑: ≥ 80%
- 工具类: ≥ 90%
- Controller: ≥ 70%

**最佳实践**:
```java
// 使用JUnit 5 + Mockito
@SpringBootTest
class OrderServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("创建订单 - 成功")
    void testCreateOrder_Success() {
        // Given
        Order order = new Order();
        order.setUserId(1L);

        // When
        when(orderMapper.insert(any())).thenReturn(1);
        Long orderId = orderService.createOrder(order);

        // Then
        assertNotNull(orderId);
        verify(orderMapper, times(1)).insert(any());
    }
}
```

#### 8.2.2 集成测试

**建议**:
- 使用Testcontainers启动真实依赖
- 测试服务间调用
- 测试数据库事务

### 8.3 部署策略

#### 8.3.1 灰度发布最佳实践

**流量分配建议**:
```
阶段1: 10%流量 → 观察5-10分钟
阶段2: 30%流量 → 观察5-10分钟
阶段3: 50%流量 → 观察5-10分钟
阶段4: 100%流量 → 完成
```

**监控指标**:
- 错误率 < 1%
- P99响应时间 < 2秒
- CPU使用率 < 80%
- 内存使用率 < 85%

**回滚策略**:
- 监控指标异常 → 自动回滚
- 用户反馈严重问题 → 手动回滚
- 回滚后保留现场日志

#### 8.3.2 部署时间选择

**建议部署时间**:
- 工作日 10:00-16:00（有足够时间处理问题）
- 避免周五下午（周末无人值守）
- 避免业务高峰期

**紧急部署**:
- 严重bug修复
- 安全漏洞修补
- 需要技术负责人审批

### 8.4 监控和告警

#### 8.4.1 关键监控指标

**应用指标**:
- 请求成功率
- 响应时间（P50, P95, P99）
- QPS（每秒查询数）
- 错误日志数量

**系统指标**:
- CPU使用率
- 内存使用率
- 磁盘使用率
- 网络IO

**业务指标**:
- 订单创建成功率
- 支付成功率
- 用户活跃度

#### 8.4.2 告警配置

**告警渠道**:
- 邮件
- 钉钉/企业微信
- 短信（严重告警）

**告警级别**:
- **P0（严重）**: 服务不可用，立即处理
- **P1（重要）**: 性能下降，1小时内处理
- **P2（一般）**: 异常告警，4小时内处理
- **P3（提示）**: 信息通知，24小时内处理

### 8.5 安全建议

#### 8.5.1 Secrets管理

- 使用Environment级别的Secrets
- 定期轮换敏感信息
- 最小权限原则
- 审计Secrets使用记录

#### 8.5.2 访问控制

- 限制main分支推送权限
- 配置Environment审批者
- 定期审查访问权限
- 使用GitHub Teams管理权限

#### 8.5.3 安全扫描

- 启用Dependabot安全更新
- 定期运行安全扫描
- 及时更新依赖版本
- 修复已知漏洞

### 8.6 文档维护

#### 8.6.1 更新文档

当以下情况发生时更新文档：
- 新增部署步骤
- 修改配置参数
- 变更部署策略
- 添加新的监控指标

#### 8.6.2 文档版本

- 在文档顶部标注版本号和更新日期
- 使用Git管理文档变更
- 重要变更记录在CHANGELOG中

---

## 附录

### A. 相关文档

- [灰度发布指南](./CANARY_DEPLOY_GUIDE.md)
- [回滚操作指南](./ROLLBACK_GUIDE.md)
- [快速参考](./QUICK_REFERENCE.md)

### B. 常用命令

```bash
# 查看工作流运行状态
gh run list --workflow=ci-cd.yml --limit 10

# 查看特定运行详情
gh run view RUN_ID

# 重新运行失败的工作流
gh run rerun RUN_ID

# 取消正在运行的工作流
gh run cancel RUN_ID

# 下载构件
gh run download RUN_ID

# 查看Secrets（仅名称）
gh secret list

# 手动触发工作流
gh workflow run ci-cd.yml -f environment=staging
```

### C. 故障排查清单

- [ ] 检查GitHub Actions服务状态
- [ ] 验证Secrets配置是否正确
- [ ] 检查分支保护规则
- [ ] 确认Environment配置
- [ ] 查看构建日志详细错误
- [ ] 检查依赖服务状态
- [ ] 验证网络连接
- [ ] 检查资源使用情况

### D. 联系方式

如有问题，请联系：
- **DevOps团队**: devops@example.com
- **技术支持**: tech-support@example.com
- **紧急联系**: 电话/钉钉群

---

**文档维护**: DevOps团队
**最后审核**: 2026-05-13
