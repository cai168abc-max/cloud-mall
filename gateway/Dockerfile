# Dockerfile for Gateway Service
# 多阶段构建：先构建，再运行

# 构建阶段
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 复制pom文件
COPY pom.xml .
COPY model/pom.xml model/
COPY gateway/pom.xml gateway/

# 下载依赖（利用Docker缓存）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY model/src model/src
COPY gateway/src gateway/src

# 构建
RUN mvn clean package -DskipTests -pl gateway -am

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 创建非root用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 复制构建产物
COPY --from=builder /build/gateway/target/*.jar app.jar

# 设置权限
RUN chown -R appuser:appgroup /app

USER appuser

# 暴露端口
EXPOSE 80

# JVM参数
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
