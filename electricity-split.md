# 项目24：家庭电费分摊计算器（ElectricitySplit）

## 请帮我从 0 到 1 实现以下小众项目

### 项目概述
针对合租或家庭按房间/成员分摊电费。记录每期电表读数、公共用电、个人大功率设备使用时长，自动生成账单与支付记录。

### 创新点 / 小众定位
解决合租电费"均摊不公平"痛点，支持按房间面积、空调时长、公共用电比例自定义分摊规则。

### 目标用户
合租青年、多代家庭房东、宿舍管理员

## 项目范围说明
- 本项目为纯后端系统开发，不涉及任何前端页面、UI、CSS/JS 改动。
- 所有功能均通过 RESTful API 对外提供服务，可使用 Postman、curl 或任意 HTTP 客户端进行测试与验收。

## 技术栈（必须严格使用）
- **后端框架**: Java Spring Boot 3.2 (Spring Web)
- **数据库**: MySQL 8.0
- **ORM**: Spring Data JPA + Hibernate
- **认证**: JWT (Spring Security)
- **API文档**: SpringDoc OpenAPI (Swagger)
- **定时任务**: Spring Scheduler（如需要）
- **容器化**: Docker + Docker Compose
- **测试**: JUnit 5 + Testcontainers（可选）+ Postman 测试集合

## 项目必须包含的交付物
- **Dockerfile**：多阶段构建，基于上述技术栈。
- **docker-compose.yml**：一键启动应用服务 + MySQL 8.0 + 可选管理工具（如 Adminer）。
- **.gitignore**：针对 Java Spring Boot 的标准忽略配置。
- **README.md**：项目简介、目录说明、快速启动、API 文档入口、测试方式。
- **docs/functional_intro.md**：功能说明、ER 图文字描述、核心用例、业务规则。
- **src/**：完整后端源码（Controller / Service / Repository / Entity / DTO / Mapper / Config 等）。
- **tests/**：单元测试 + 集成测试。
- **postman_collection.json**（或同等测试脚本）：覆盖所有接口的功能测试集合。
- **初始化 SQL / Seed Data**：Docker 启动后自动建表并插入示例数据。

## 数据库设计

### 主要数据表
1. **Users** - 用户表
   - Id（主键）
   - Username（用户名，唯一）
   - Email（邮箱，唯一）
   - PasswordHash（密码哈希）
   - Avatar（头像 URL，可选）
   - CreatedAt / UpdatedAt

2. **Households** - 住户
   - Name
   - Address
   - CreatedBy
   - CreatedAt

3. **Rooms** - 房间
   - HouseholdId
   - Name
   - Area
   - OccupantId
   - HasAirConditioner
   - CreatedAt

4. **MeterReadings** - 电表读数
   - HouseholdId
   - ReadingDate
   - TotalKwh
   - Amount
   - CreatedAt

5. **Bills** - 账单
   - HouseholdId
   - PeriodStart
   - PeriodEnd
   - TotalAmount
   - Status（Draft / Sent / Paid）
   - CreatedAt

6. **BillItems** - 账单明细
   - BillId
   - RoomId
   - BaseShare
   - AcShare
   - PublicShare
   - TotalDue
   - IsPaid
   - PaidAt

## 核心功能模块
### 1. 用户认证模块
- 用户注册 / 登录 / JWT 鉴权
- 获取当前登录用户信息

### 2. 住户管理模块
- 住户的增删改查（支持分页、搜索、排序）
- 住户状态/详情/关联操作
- 住户权限控制（仅所有者或管理员可操作）

### 3. 房间管理模块
- 房间的增删改查（支持分页、搜索、排序）
- 房间状态/详情/关联操作
- 房间权限控制（仅所有者或管理员可操作）

### 4. 电表读数管理模块
- 电表读数的增删改查（支持分页、搜索、排序）
- 电表读数状态/详情/关联操作
- 电表读数权限控制（仅所有者或管理员可操作）

### 5. 账单管理模块
- 账单的增删改查（支持分页、搜索、排序）
- 账单状态/详情/关联操作
- 账单权限控制（仅所有者或管理员可操作）

### 6. 账单明细管理模块
- 账单明细的增删改查（支持分页、搜索、排序）
- 账单明细状态/详情/关联操作
- 账单明细权限控制（仅所有者或管理员可操作）

### 7. 统计与搜索模块
- 全局搜索与筛选
- 基础数据看板（数量、趋势、排行榜等）
- 导出关键数据（可选）

## API 接口清单
### Auth
- POST /api/auth/register - 用户注册
- POST /api/auth/login - 用户登录
- GET /api/auth/me - 获取当前用户信息
- PUT /api/auth/me - 更新个人信息

### Households（住户）
- GET /api/households - 获取住户列表（支持分页、搜索、筛选）
- POST /api/households - 创建住户
- GET /api/households/{id} - 获取住户详情
- PUT /api/households/{id} - 更新住户
- DELETE /api/households/{id} - 删除住户

### Rooms（房间）
- GET /api/rooms - 获取房间列表（支持分页、搜索、筛选）
- POST /api/rooms - 创建房间
- GET /api/rooms/{id} - 获取房间详情
- PUT /api/rooms/{id} - 更新房间
- DELETE /api/rooms/{id} - 删除房间

### MeterReadings（电表读数）
- GET /api/meterreadings - 获取电表读数列表（支持分页、搜索、筛选）
- POST /api/meterreadings - 创建电表读数
- GET /api/meterreadings/{id} - 获取电表读数详情
- PUT /api/meterreadings/{id} - 更新电表读数
- DELETE /api/meterreadings/{id} - 删除电表读数

### Bills（账单）
- GET /api/bills - 获取账单列表（支持分页、搜索、筛选）
- POST /api/bills - 创建账单
- GET /api/bills/{id} - 获取账单详情
- PUT /api/bills/{id} - 更新账单
- DELETE /api/bills/{id} - 删除账单
- PATCH /api/bills/{id}/status - 修改账单状态

### BillItems（账单明细）
- GET /api/billitems - 获取账单明细列表（支持分页、搜索、筛选）
- POST /api/billitems - 创建账单明细
- GET /api/billitems/{id} - 获取账单明细详情
- PUT /api/billitems/{id} - 更新账单明细
- DELETE /api/billitems/{id} - 删除账单明细

### Statistics
- GET /api/stats/overview - 总览统计
- GET /api/stats/trend - 趋势统计（按时间范围）

## Docker 配置要求

### Dockerfile（Java Spring Boot）
```dockerfile
# 阶段1：构建
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 阶段2：运行
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8094
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3   CMD wget --no-verbose --tries=1 --spider http://localhost:8094/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

要求：
1. 使用 Maven 多阶段构建，最终镜像只包含 JRE 与 jar 包。
2. 暴露 8094 端口。
3. 启用 Spring Boot Actuator 健康检查 `/actuator/health`。
4. 通过环境变量读取数据库连接配置。

### docker-compose.yml 要求
```yaml
version: '3.8'
services:
  app:
    build: .
    container_name: electricitysplit_app
    ports:
      - "8094:8094"
    environment:
      - DB_HOST=mysql
      - DB_PORT=3306
      - DB_NAME=electricitysplit
      - DB_USER=app_user
      - DB_PASSWORD=app_pass
    depends_on:
      mysql:
        condition: service_healthy
  mysql:
    image: mysql:8.0
    container_name: electricitysplit_mysql
    environment:
      - MYSQL_ROOT_PASSWORD=root_pass
      - MYSQL_DATABASE=electricitysplit
      - MYSQL_USER=app_user
      - MYSQL_PASSWORD=app_pass
    ports:
      - "13319:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5
  volumes:
    mysql_data:
```

要求：
1. MySQL 使用 8.0 镜像。
2. 应用服务必须等 MySQL healthy 后再启动。
3. 使用 named volume 持久化数据库数据。
4. 环境变量集中管理，禁止在源码中硬编码密码。

## .gitignore 参考
```text
# Java / Maven / Gradle
target/
build/
*.class
*.jar
*.war
*.ear
*.iml

# IDE
.idea/
*.ipr
*.iws
.vscode/

# Secrets & local config
.env
.env.local
application-local.properties
application-dev.properties

# Logs
*.log
logs/

# Test results
surefire-reports/

# OS
Thumbs.db
.DS_Store
```

## 文档要求

### README.md
至少包含：
1. 项目名称与一句话介绍。
2. 功能亮点（3-5 条）。
3. 技术栈说明。
4. 目录结构说明。
5. 快速启动步骤（克隆 → Docker 启动 → 访问接口）。
6. 测试命令与 Postman 集合导入说明。
7. 贡献与许可（可选）。

### docs/functional_intro.md
至少包含：
1. 业务背景与解决的问题。
2. 用户角色与核心用例。
3. 功能模块详细说明。
4. 数据库 ER 图文字描述（表关系）。
5. 关键业务规则（如状态流转、权限规则、时间计算逻辑）。
6. 接口调用示例（至少 3 个）。

## 运行与测试步骤

1. **克隆并进入项目目录**：
   ```bash
   git clone <repo-url>
   cd ElectricitySplit
   ```

2. **Docker 启动**：
   ```bash
   docker-compose up --build -d
   ```

3. **查看日志**：
   ```bash
   docker-compose logs -f app
   ```

4. **验证服务健康**：
   - .NET：`curl http://localhost:8094/health`
   - Java：`curl http://localhost:8094/actuator/health`

5. **导入并执行 Postman 测试集合**，验证所有接口：
   - 注册 / 登录
   - 各实体的 CRUD
   - 搜索 / 筛选 / 分页
   - 统计接口
   - 权限控制（未登录访问受限资源应返回 401）

6. **执行自动化测试**：
   - .NET：`dotnet test`
   - Java：`./mvnw test` 或 `mvn test`

7. **停止服务**：
   ```bash
   docker-compose down -v
   ```

## 其他质量要求
- 使用 Spring Data JPA 操作 MySQL，禁止手写 SQL 进行日常 CRUD（复杂统计可手写）。
- 代码分层清晰，遵循 RESTful API 设计规范。
- 关键代码必须有中文注释，说明业务意图。
- 统一的异常处理与参数校验（.NET FluentValidation / Spring Validation）。
- 使用 JWT 保护敏感接口，未携带 Token 返回 401。
- 数据库连接字符串通过环境变量注入，支持 Docker 内外运行。
- 提供 Seed Data，容器启动后至少有 5-10 条示例数据可用于测试。
- 接口返回统一包装格式（code / message / data）。
- 日志使用框架原生日志（.NET ILogger / SLF4J），记录关键操作与异常。
- 项目必须是小众生活/工作场景，禁止做成通用商城、OA、CMS、ERP。
