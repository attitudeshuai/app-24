# 阶段1：构建
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# 创建Maven settings.xml配置阿里云镜像
RUN mkdir -p /root/.m2 && cat > /root/.m2/settings.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <mirrors>
        <mirror>
            <id>aliyunmaven</id>
            <mirrorOf>*</mirrorOf>
            <name>阿里云公共仓库</name>
            <url>https://maven.aliyun.com/repository/public</url>
        </mirror>
    </mirrors>
    <profiles>
        <profile>
            <id>aliyun</id>
            <repositories>
                <repository>
                    <id>aliyun</id>
                    <url>https://maven.aliyun.com/repository/public</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>true</enabled></snapshots>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>aliyun</id>
                    <url>https://maven.aliyun.com/repository/public</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>true</enabled></snapshots>
                </pluginRepository>
            </pluginRepositories>
        </profile>
    </profiles>
    <activeProfiles>
        <activeProfile>aliyun</activeProfile>
    </activeProfiles>
</settings>
EOF

COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -s /root/.m2/settings.xml

# 阶段2：运行
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8094
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8094/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
