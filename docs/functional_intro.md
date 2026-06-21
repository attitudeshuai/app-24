# 业务功能说明文档

## 业务背景

在合租公寓或多成员家庭中，电费分摊往往是一个容易引发矛盾的问题。传统的按人头均摊方式存在诸多不公平因素：

- 不同房间面积差异大，按人均摊对大房间住户不公平
- 部分房间有空调等高耗电设备，而其他房间没有
- 公共区域（客厅、厨房、卫生间）用电如何合理分配
- 每次手工计算繁琐，容易出错且缺乏透明度

**ElectricitySplit** 旨在提供一个公正、透明、自动化的电费分摊解决方案，让合租生活更和谐。

## 用户角色

| 角色 | 说明 | 权限 |
|------|------|------|
| 房东/管理员 | 创建住户、管理房间和账单的用户 | 对自己创建的所有数据拥有完全操作权限 |
| 普通住户 | 实际居住在房间中的成员 | 可被指定为房间居住者，系统支持关联用户查询 |

> 注：系统采用基于用户的数据隔离机制，每位用户注册后独立管理自己的住户、房间、账单等数据，互不干扰。

## 核心用例

### 典型业务流程

```
注册账号 → 创建住户 → 添加房间 → 录入电表读数 → 生成账单 → 发送账单 → 支付确认
```

### 核心用例列表

1. **创建住户（Household）**：用户注册后，首先创建自己的住户单位（一套房子/公寓），填写名称和地址。
2. **添加房间（Room）**：在住户下添加各个房间，设置房间名称、面积、居住人、是否有空调等信息。
3. **录入电表读数（MeterReading）**：每月抄表后录入总用电量和电费金额，系统记录读数日期。
4. **生成账单（Bill）**：根据电表读数周期创建账单，系统按规则自动计算每个房间的分摊金额。
5. **账单明细（BillItem）**：每个房间的详细分摊项，包含基础分摊、空调分摊、公共分摊三部分。
6. **支付确认**：确认各房间的付款状态，账单整体从草稿→已发送→已支付流转。
7. **数据统计（Stats）**：概览住户数量、账单总金额、支付状态统计，以及用电量趋势分析。

## 功能模块详细说明

### 模块一：认证模块（Auth）

| 功能 | 接口 | 说明 |
|------|------|------|
| 用户注册 | `POST /api/auth/register` | 填写用户名、邮箱、密码完成注册 |
| 用户登录 | `POST /api/auth/login` | 用户名/邮箱 + 密码登录，返回 JWT Token |
| 获取当前用户 | `GET /api/auth/me` | 获取当前登录用户信息 |
| 更新当前用户 | `PUT /api/auth/me` | 修改头像、密码等个人信息 |

### 模块二：住户管理（Household）

| 功能 | 接口 | 说明 |
|------|------|------|
| 住户列表 | `GET /api/households` | 分页查询，支持关键词搜索和排序 |
| 创建住户 | `POST /api/households` | 输入名称、地址创建新住户 |
| 住户详情 | `GET /api/households/{id}` | 查看单个住户的详细信息 |
| 更新住户 | `PUT /api/households/{id}` | 修改住户名称、地址 |
| 删除住户 | `DELETE /api/households/{id}` | 删除住户及其关联的所有房间、读数、账单数据 |

### 模块三：房间管理（Room）

| 功能 | 接口 | 说明 |
|------|------|------|
| 房间列表 | `GET /api/rooms?householdId=` | 按住户ID查询房间列表，支持关键词搜索 |
| 创建房间 | `POST /api/rooms` | 关联住户，设置名称、面积、居住人、是否有空调 |
| 房间详情 | `GET /api/rooms/{id}` | 查看单个房间详细信息 |
| 更新房间 | `PUT /api/rooms/{id}` | 修改房间各项属性 |
| 删除房间 | `DELETE /api/rooms/{id}` | 删除房间及其关联的账单明细 |

### 模块四：电表读数（MeterReading）

| 功能 | 接口 | 说明 |
|------|------|------|
| 读数列表 | `GET /api/meterreadings?householdId=` | 按住户查询历史读数，支持日期范围筛选 |
| 录入读数 | `POST /api/meterreadings` | 录入读数日期、总用电量(kWh)、电费金额 |
| 读数详情 | `GET /api/meterreadings/{id}` | 查看单条读数记录 |
| 更新读数 | `PUT /api/meterreadings/{id}` | 修改读数信息 |
| 删除读数 | `DELETE /api/meterreadings/{id}` | 删除读数记录 |

### 模块五：账单管理（Bill）

| 功能 | 接口 | 说明 |
|------|------|------|
| 账单列表 | `GET /api/bills?householdId=` | 按住户查询账单，支持状态、日期范围筛选 |
| 创建账单 | `POST /api/bills` | 指定账期起止日、总金额，系统自动计算各房间分摊 |
| 账单详情 | `GET /api/bills/{id}` | 查看账单及其明细列表 |
| 更新账单 | `PUT /api/bills/{id}` | 修改账单信息 |
| 删除账单 | `DELETE /api/bills/{id}` | 删除账单及其明细 |
| 更新状态 | `PATCH /api/bills/{id}/status` | 修改账单状态（Draft/Sent/Paid） |

### 模块六：账单明细（BillItem）

| 功能 | 接口 | 说明 |
|------|------|------|
| 明细列表 | `GET /api/billitems` | 按账单ID或房间ID查询明细 |
| 创建明细 | `POST /api/billitems` | 手动创建或调整分摊明细 |
| 明细详情 | `GET /api/billitems/{id}` | 查看单条明细 |
| 更新明细 | `PUT /api/billitems/{id}` | 调整各项分摊金额 |
| 删除明细 | `DELETE /api/billitems/{id}` | 删除单条明细 |

### 模块七：数据统计（Stats）

| 功能 | 接口 | 说明 |
|------|------|------|
| 概览统计 | `GET /api/stats/overview` | 住户数、房间数、账单数、总金额、已付/待付统计 |
| 趋势分析 | `GET /api/stats/trend` | 按日期范围统计月度用电量和电费趋势 |

## 数据库ER图（文字描述）

系统共包含 **6 张核心数据表**，关系结构如下：

```
┌─────────────┐       1:N       ┌─────────────────┐
│    User     │───────────────▶│   Household     │
│  (用户表)   │                 │   (住户表)      │
└─────────────┘                 └────────┬────────┘
       ▲                                 │
       │                                 │ 1:N
       │                                 ▼
       │ 1:N (居住人关联)        ┌─────────────────┐
       └────────────────────────│      Room       │
                                │    (房间表)     │
                                └────────┬────────┘
                                         │
                                         │ 1:N
                                         ▼
┌─────────────────┐            ┌─────────────────┐
│  MeterReading   │            │     Bill        │
│  (电表读数表)   │            │    (账单表)     │
└─────────────────┘            └────────┬────────┘
       ▲                                 │
       │ 1:N                             │ 1:N
       │                                 ▼
       │                        ┌─────────────────┐
       │ N:1 (通过住户关联)     │    BillItem     │
       └────────────────────────│  (账单明细表)   │
          通过 room_id 关联 ──▶ └─────────────────┘
          通过 household_id 关联
```

### 各表说明

| 表名 | 关键字段 | 关系 |
|------|----------|------|
| **User（用户表）** | id, username, email, passwordHash, avatar | 创建者：1个用户可创建多个Household；居住者：1个用户可被指定为多个Room的居住人 |
| **Household（住户表）** | id, name, address, createdBy_id | 属于1个User；拥有多个Room、MeterReading、Bill |
| **Room（房间表）** | id, name, area, hasAirConditioner, household_id, occupant_id | 属于1个Household；可关联1个User作为居住者；拥有多个BillItem |
| **MeterReading（电表读数表）** | id, readingDate, totalKwh, amount, household_id | 属于1个Household，用于记录每月抄表数据 |
| **Bill（账单表）** | id, periodStart, periodEnd, totalAmount, status, household_id | 属于1个Household；包含多个BillItem |
| **BillItem（账单明细表）** | id, baseShare, acShare, publicShare, totalDue, isPaid, paidAt, bill_id, room_id | 属于1个Bill，关联1个Room，记录该房间的三部分分摊金额 |

## 关键业务规则

### 1. 权限校验规则（数据隔离）

系统采用**基于创建者的数据隔离策略**：

- 所有写操作（创建/更新/删除）必须验证数据的创建者是否为当前登录用户
- 所有读操作（查询列表/详情）自动过滤只返回当前用户创建的数据
- 通过 `SecurityContextHolder` 获取当前用户，在 Service 层进行权限校验
- 跨用户访问会抛出 `BusinessException`，返回 403 错误

### 2. 账单状态流转

账单支持三种状态，流转路径如下：

```
  Draft（草稿）  ──▶  Sent（已发送）  ──▶  Paid（已支付）
       ▲                  │
       └──────────────────┘
           可回退修改
```

| 状态 | 说明 | 可执行操作 |
|------|------|-----------|
| Draft | 草稿状态，账单刚创建或被退回修改 | 可自由编辑所有字段，可删除 |
| Sent | 已发送给各房间住户确认 | 不可修改分摊明细，可更新状态为 Paid 或退回 Draft |
| Paid | 所有房间已确认支付 | 账单归档，不可修改和删除 |

### 3. 自动计算规则

账单明细的总应付金额自动计算，公式如下：

```
totalDue = baseShare + acShare + publicShare
```

| 分摊项 | 说明 | 计算依据 |
|--------|------|----------|
| **baseShare（基础分摊）** | 按房间面积占比分配的基础电费 | 房间面积 / 总房间面积 × 基础电费总额 |
| **acShare（空调分摊）** | 有空调房间额外分摊的电费 | 仅对 hasAirConditioner=true 的房间分配，按面积或均摊 |
| **publicShare（公共分摊）** | 公共区域用电的分摊 | 可按房间数均摊或按居住人数分配 |

> 系统支持手动调整各项分摊金额，调整后 totalDue 会自动重新计算。

### 4. 级联删除规则

删除主数据时，关联数据的处理策略：

- 删除 **Household** → 级联删除该住户下所有 Room、MeterReading、Bill 及其 BillItem
- 删除 **Room** → 级联删除该房间关联的所有 BillItem
- 删除 **Bill** → 级联删除该账单下的所有 BillItem
- 删除 **MeterReading** → 仅删除自身，不影响其他数据

## 接口调用示例

### 1. 用户注册

```bash
curl -X POST http://localhost:8094/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "email": "newuser@example.com",
    "password": "123456",
    "avatar": "新用户头像"
  }'
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400
  }
}
```

---

### 2. 用户登录

```bash
curl -X POST http://localhost:8094/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "usernameOrEmail": "zhangsan",
    "password": "123456"
  }'
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.xxx.yyy",
    "tokenType": "Bearer",
    "expiresIn": 86400
  }
}
```

> 注：将返回的 token 保存，后续请求需在 Header 中携带 `Authorization: Bearer {token}`

---

### 3. 创建住户

```bash
curl -X POST http://localhost:8094/api/households \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.xxx.yyy" \
  -d '{
    "name": "和谐家园A栋",
    "address": "广州市天河区和谐家园A栋1801"
  }'
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 3,
    "name": "和谐家园A栋",
    "address": "广州市天河区和谐家园A栋1801",
    "createdById": 1,
    "createdByUsername": "zhangsan",
    "createdAt": "2026-06-21T10:30:00"
  }
}
```

---

### 4. 获取住户列表

```bash
# 基础分页查询
curl -X GET "http://localhost:8094/api/households?page=0&size=10" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.xxx.yyy"

# 带关键词搜索和排序
curl -X GET "http://localhost:8094/api/households?page=0&size=10&keyword=花园&sort=createdAt,desc" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.xxx.yyy"
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "阳光花园3栋2单元",
        "address": "北京市朝阳区阳光花园3栋2单元501",
        "createdById": 1,
        "createdByUsername": "zhangsan",
        "createdAt": "2026-06-21T09:00:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 10,
    "number": 0
  }
}
```

---

### 5. 创建房间

```bash
curl -X POST http://localhost:8094/api/rooms \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.xxx.yyy" \
  -d '{
    "householdId": 1,
    "name": "超大主卧",
    "area": 25.5,
    "occupantId": 1,
    "hasAirConditioner": true
  }'
```

---

### 6. 获取当前用户信息

```bash
curl -X GET http://localhost:8094/api/auth/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.xxx.yyy"
```

---

### 7. 健康检查（无需认证）

```bash
curl -X GET http://localhost:8094/actuator/health
```

**响应示例：**
```json
{
  "status": "UP"
}
```
