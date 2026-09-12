# =============================================================================
# 后端镜像：Spring Boot 3.5 / Java 21
# 构建期由 protobuf-maven-plugin 自动生成 gRPC stub（src/main/proto → rpc.gen）
# =============================================================================

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只拷 pom，依赖层可缓存
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Shanghai

# 运行时配置由 compose 挂载到 /app/config/application.yml（见 docker/backend/）
COPY --from=build /build/target/article_trace-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
