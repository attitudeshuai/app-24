# 家庭电费分摊计算器（ElectricitySplit）

针对合租或家庭按房间/成员分摊电费的后端系统

## 功能亮点

- **自定义分摊规则**：支持按面积、空调使用、公共用电三种维度灵活配置分摊方式
- **多维度电费拆分**：基础电费按面积均摊、空调电费单独计量、公共用电按需分配
- **完善的权限控制**：基于用户的数据隔离，每个用户只能查看和操作自己创建的数据
- **JWT 安全认证**：采用 JWT Token 实现无状态认证，支持前后端分离架构
- **Docker 一键部署**：提供 Dockerfile 和 docker-compose.yml，开箱即用

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.5 | 核心框架 |
| MySQL | 8.0 | 关系型数据库 |
| Spring Data JPA | 3.2.5 | ORM 持久层 |
| Spring Security | 6.x | 安全框架 |
| JWT (jjwt) | 0.12.5 | Token 认证 |
| SpringDoc OpenAPI | 2.5.0 | API 文档 |
| Docker | - | 容器化部署 |
| Maven | - | 构建工具 |

## 目录结构

```
src/main/java/com/electricitysplit/
├── ElectricitySplitApplication.java    # 启动类
├── config/                              # 配置类
│   ├── DataInitializer.java           # 数据初始化器（种子数据）
│   ├── JwtAuthenticationFilter.java   # JWT 认证过滤器
│   ├── JwtUtil.java                    # JWT 工具类
│   ├── OpenApiConfig.java              # Swagger 配置
│   └── SecurityConfig.java             # Spring Security 配置
├── controller/                          # 控制器层
│   ├── AuthController.java             # 认证接口
│   ├── BillController.java             # 账单接口
│   ├── BillItemController.java         # 账单明细接口
│   ├── HouseholdController.java        # 住户接口
│   ├── MeterReadingController.java     # 电表读数接口
│   ├── RoomController.java             # 房间接口
│   └── StatsController.java            # 统计接口
├── dto/                                 # 数据传输对象
│   ├── ApiResponse.java                # 统一响应封装
│   ├── AuthDto.java                    # 认证相关 DTO
│   ├── BillDto.java                    # 账单相关 DTO
│   ├── BillItemDto.java                # 账单明细 DTO
│   ├── HouseholdDto.java               # 住户 DTO
│   ├── MeterReadingDto.java            # 电表读数 DTO
│   ├── PageResponse.java               # 分页响应封装
│   ├── RoomDto.java                    # 房间 DTO
│   └── StatsDto.java                   # 统计 DTO
├── entity/                              # 实体类
│   ├── Bill.java                       # 账单实体
│   ├── BillItem.java                   # 账单明细实体
│   ├── Household.java                  # 住户实体
│   ├── MeterReading.java               # 电表读数实体
│   ├── Room.java                       # 房间实体
│   └── User.java                       # 用户实体
├── exception/                           # 异常处理
│   ├── BusinessException.java          # 业务异常
│   └── GlobalExceptionHandler.java     # 全局异常处理器
├── repository/                          # 数据访问层
│   ├── BillItemRepository.java
│   ├── BillRepository.java
│   ├── HouseholdRepository.java
│   ├── MeterReadingRepository.java
│   ├── RoomRepository.java
│   └── UserRepository.java
└── service/                             # 业务逻辑层
    ├── AuthService.java
    ├── BillItemService.java
    ├── BillService.java
    ├── HouseholdService.java
    ├── MeterReadingService.java
    ├── RoomService.java
    └── StatsService.java
```

## 快速启动

### 前置条件

- JDK 17+
- Docker & Docker Compose（推荐）
- Maven 3.8+

### 方式一：Docker Compose 一键启动（推荐）

```bash
# 1. 克隆项目
git clone <repository-url>
cd app-24

# 2. 一键启动（MySQL + 应用）
docker-compose up -d

# 3. 访问服务
# 应用地址: http://localhost:8094
# API 文档: http://localhost:8094/swagger-ui.html
# 健康检查: http://localhost:8094/actuator/health
```

### 方式二：本地开发启动

```bash
# 1. 确保本地已安装 MySQL 8.0，创建数据库
CREATE DATABASE electricity_split CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 2. 修改 application.properties 中的数据库配置
# 或设置环境变量覆盖

# 3. 编译运行
mvn clean spring-boot:run

# 4. 访问服务
# 应用地址: http://localhost:8094
```

## 测试账号

系统启动后会自动初始化以下测试账号，密码统一为 `123456`：

| 用户名 | 邮箱 | 说明 |
|--------|------|------|
| zhangsan | zhangsan@example.com | 张三（含完整测试数据） |
| lisi | lisi@example.com | 李四（含完整测试数据） |
| wangwu | wangwu@example.com | 王五 |
| zhaoliu | zhaoliu@example.com | 赵六 |

## 相关入口

- **API 文档**：[http://localhost:8094/swagger-ui.html](http://localhost:8094/swagger-ui.html)
- **健康检查**：[http://localhost:8094/actuator/health](http://localhost:8094/actuator/health)
- **OpenAPI JSON**：[http://localhost:8094/v3/api-docs](http://localhost:8094/v3/api-docs)

## 文档

- 业务功能说明：[docs/functional_intro.md](docs/functional_intro.md)
- Postman 测试集合：[postman_collection.json](postman_collection.json)
