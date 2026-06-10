# ---- Stage 1: Build ứng dụng ----
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy cấu hình Gradle
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

# Cấp quyền thực thi VÀ loại bỏ ký tự CRLF của Windows để tránh lỗi "bad interpreter"
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Download dependencies trước để tận dụng Docker cache
RUN ./gradlew build -x test --no-daemon || true

# Copy source code và tiến hành build
COPY src ./src
RUN ./gradlew clean bootJar -x test --no-daemon

# ---- Stage 2: Chạy ứng dụng ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# (Khuyến nghị) Tạo non-root user để chạy ứng dụng an toàn hơn
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy file jar từ bước builder sang
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose port mặc định của Spring Boot
EXPOSE 8080

# Healthcheck để kiểm tra ứng dụng có sống không
HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Lệnh chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]
