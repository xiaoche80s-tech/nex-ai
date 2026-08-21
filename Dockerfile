# syntax=docker/dockerfile:1.7
# NexAI 管理平台镜像：多阶段构建（Maven 构建全部依赖模块 → JRE 运行）
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml lombok.config ./
COPY nexai-dependencies nexai-dependencies
COPY nexai-framework nexai-framework
COPY nexai-module-system nexai-module-system
COPY nexai-module-infra nexai-module-infra
COPY nexai-server nexai-server
# BuildKit 缓存挂载加速重复构建
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -pl nexai-server -am package -DskipTests

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/nexai-server/target/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=docker \
    TZ=Asia/Shanghai
EXPOSE 48080
ENTRYPOINT ["java", "-jar", "app.jar"]
