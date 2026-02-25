# PushApp Backend 开发规则

> ⚠️ 以下规则强制执行，不可跳过。

**RULES_VERSION: push-server@2.0**

## 加载确认（强制）

1. **启动自报：** 加载本文件后，在执行任何任务之前，必须先输出一行：
   ```
   ✅ [PushApp Backend Rules v2.0 loaded]
   ```
2. **问答验证：** 当用户问"你的 RULES 版本号是什么？"时，必须回答 `push-server@2.0`。

## 身份

你正在开发 **PushApp 后端服务**（Kotlin 2.2 + Spring Boot 4 + JPA/PostgreSQL + RabbitMQ）。

## 按需读文档（不要一次全读）

文档位于 `../push_docs/modules/`，**只在涉及对应模块时才读**：

| 需要做什么         | 读什么                                       |
| ------------------ | -------------------------------------------- |
| 开始任何任务       | 目标模块的 `README.md`（了解需求和验收标准） |
| 涉及接口开发       | 目标模块的 `api.md` + `models.md`            |
| 理解业务流程       | 目标模块的 `flow.md`                         |
| 查看已知问题       | 目标模块的 `bugs.md` + `improvements.md`     |
| 涉及跨模块         | 依赖模块的 `README.md`                       |
| 首次接触项目       | `skills/pushdev/push-architecture.md`        |
| 有 CURRENT_PLAN.md | 直接读计划文件，按步骤继续执行               |

## 强制规则

### 1. 接口对齐

- HTTP 格式严格按 `api.md` 实现
- 文档有误 → 先更新文档再改代码

### 2. 变更记录（双轨同步）

每次代码变更后必须更新：

- `<module>/changelog.md` — `[Server] 变更描述`
- 根 `CHANGELOG.md` — 全局记录
- `<module>/README.md` — 验收标准打 ✅ + AI Model + 日期

### 3. AI 可追溯性

- Changelog 格式：`## YYYY-MM-DD - 描述 (@操作者 / AI模型名)`

### 4. Git 提交

- 先提交 `push_docs`，再提交代码
- 格式：`<type>(<module>): <description>`

### 5. Bug / 优化发现

- Bug → `<module>/bugs.md` | 优化 → `<module>/improvements.md`

### 6. 代码结构防线（Gate 5 — 反屎山铁律）

**一个文件只做一件事，文件名就是那件事。** 添加代码前必问：这段代码和文件名描述的职责是同一件事吗？不是 → 新建文件。

**目录结构（按层组织 + Service 单一职责）：**

```
src/main/kotlin/com/layababateam/pushserver/
  config/                       # Spring 配置（RabbitMQ、Security 等）
  controller/                   # HTTP REST 入口（薄壳）
  service/                      # 业务逻辑（核心层，单一职责）
    auth/                       # 按业务域分子目录
    messaging/
    user/
    subscription/
  entity/                       # JPA 实体（PostgreSQL）
  dto/                          # 数据传输对象
  repository/                   # 数据访问层（JpaRepository）
  mq/                           # RabbitMQ 生产者/消费者
```

**文件职责铁律：**

- Controller → 薄壳，只做参数校验 + 调 Service + 返回结果，**不写业务逻辑**
- Service → 核心业务逻辑，超过 300 行或 10+ public 方法 → 按职责拆分到子目录
- Entity → JPA 实体类（`class`，非 `data class`），不写业务逻辑
- DTO → 每个 API 端点必须有请求 DTO + 响应 DTO，**禁止直接返回 Entity 给前端**
- Repository → 只做数据访问，不写业务逻辑
- Consumer (MQ) → 薄分发器，反序列化 + 调 Service，不直接操作 Repository

**绝对禁止：**

- `Map<String, Any>` / `Any` 在业务逻辑中 — 定义 DTO
- 裸 `!!` — 用 `?: throw` 或 `?.let`
- 空 catch `catch (e: Exception) { }` — 至少 log 或转为 BusinessException
- 直接返回 Entity 给前端 — 用响应 DTO
- Service 循环依赖 — 说明职责划分有问题
- 往已膨胀的 Service 里追加新职责

### 7. 状态管理（Gate 6 — Service 单一职责）

- Service 是核心层，所有业务逻辑在这里
- Controller 不写业务逻辑
- Service 之间通过构造器注入调用，禁止循环依赖

### 8. 执行规范与测试机制

- **实操判例库**：具体的架构约定与编码范例，必须严格遵守 `skills/pushdev/code-rules/server-code-rules.md`。
- **按需 TDD**：默认以纯净的直接实现（Implementation）为主，依据文档作为断言契约。遇到复杂、核心的纯业务逻辑时，主动运用 Mock 或内存替身完成单元测试/TDD。严禁在未经隔离的情况下直接依赖本地外置 DB 进行测试。

## 涉及模块

4 个模块：auth, user, messaging, subscription
