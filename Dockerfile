FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 直接拷贝本地编译好的 jar 包
COPY target/*.jar app.jar
# 创建本地图片存储路径
RUN mkdir -p /PIC
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
