# 无人健身房 SaaS · 后端工程（backend）

面向校企合作课程的模块化单体后端：顾客小程序 + 商家 Web 共用一个 Java 服务。
**契约唯一来源**：`docs/接口文档.md`（v1.2，150 个入口）、`docs/数据库设计.md`（26 表 schema v1.1）、
`database/schema.sql`、`api/first-loop.openapi.yaml`。本目录只放实现，不复制契约。

## 1. 技术栈（与 docs/系统设计.md §1 一致）

| 项 | 版本/选型 |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.5（Web / Validation / Security / Scheduling / Actuator） |
| 持久层 | MyBatis-Plus 3.5.9（分页插件 + JSqlParser） |
| 数据库 | MySQL 8.4 / InnoDB / utf8mb4_0900_as_cs，连接固定 `time_zone=+00:00` |
| 缓存 | Redis 7（登录限流、短期扫码凭证、平台 token；**不作为容量/资金事实来源**） |
| 鉴权 | Spring Security 6 + JWT（HS256，7200s）+ BCrypt |
| 文档 | springdoc（customer / admin 两个分组） |
| 测试 | JUnit 5 + Testcontainers（MySQL 8.4） |

## 2. 模块与分支（一个模块一个分支）

| Maven 模块 | 包根 | 对应接口文档 | 分支 |
|---|---|---|---|
| gym-common | `com.wrgym.common` | 公共约定 §1、§19 | `feat/be-00-foundation` |
| gym-security | `com.wrgym.security` | 认证/权限 §1.3 | `feat/be-00-foundation` |
| gym-adapter-api / -mock / -ezviz | `com.wrgym.adapter.*` | 设备边界（设备与部署.md §11） | `feat/be-00b-adapter` |
| gym-module-auth | `com.wrgym.auth` | §2 认证与个人资料（10） | `feat/be-m01-auth-profile` |
| gym-module-merchant | `com.wrgym.merchant` | §3 商家与员工权限（8） | `feat/be-m02-merchant-staff` |
| gym-module-store | `com.wrgym.store` | §4 门店/规则/时段（15） | `feat/be-m03-store-slot` |
| gym-module-booking | `com.wrgym.booking` | §6 预约订单与支付退款（10） | `feat/be-m04a-booking-pay` / `-m04b-cancel-refund` |
| gym-module-device | `com.wrgym.device` | §10 设备/命令/事件（18） | `feat/be-m05-device-command` |
| gym-module-visit | `com.wrgym.visit` | §8 通行凭证与实际到店（12） | `feat/be-m06-visit-access` |
| gym-module-billing | `com.wrgym.billing` | §9 账单与资金流水（11） | `feat/be-m07-billing` |
| gym-module-customer | `com.wrgym.customer` | §5 顾客/黑名单/信用（11） | `feat/be-m08-customer-credit` |
| gym-module-card | `com.wrgym.card` | §7 卡产品与权益（23） | `feat/be-m09-card-benefit` |
| gym-module-review | `com.wrgym.review` | §14 异常待办与复核（7） | `feat/be-m10-review` |
| gym-module-report | `com.wrgym.report` | §15 日报/统计/审计（8） | `feat/be-m11-report-audit` |
| gym-module-lighting | `com.wrgym.lighting` | §13 灯控（4） | `feat/be-m12-lighting` |
| gym-module-video | `com.wrgym.video` | §11 视频（3） | `feat/be-m13-video` |
| gym-module-face | `com.wrgym.face` | §12 人脸（5） | `feat/be-m14-face` |
| gym-module-dev | `com.wrgym.dev` | §16 开发 Mock/回调（5） | `feat/be-m15-dev-mock` |
| gym-module-scheduler | `com.wrgym.scheduler` | §19.9 定时任务 | `feat/be-m16-scheduler` |
| gym-bootstrap | `com.wrgym.bootstrap` | 装配/配置 | `feat/be-00-foundation` |

**父子 POM 模块清单在 `feat/be-00-foundation` 一次性定型**，后续分支只在自己模块目录内改动；
跨模块调用只通过 `com.wrgym.common.port` 接口与 record DTO，禁止 import 其他模块的
entity / mapper / service。

## 3. 本地运行

前置：JDK 21、Maven 3.9、Docker Desktop（WSL2 后端）。

```bash
# 1) 数据库（mysql:8.4 + redis:7，只读挂载仓库的 database/schema.sql 与 app_privileges.sql）
cd backend
cp .env.example .env
docker compose up -d
docker compose ps            # 两个容器都应为 healthy

# 2) 构建
mvn -s ../your-settings.xml package      # settings 里把 localRepository 指到可写目录
mvn -pl gym-bootstrap -am test           # 需要 Docker（Testcontainers 起 MySQL 8.4）

# 3) 启动（默认 dev profile，Mock 设备）
mvn -pl gym-bootstrap -am spring-boot:run
# Swagger：http://localhost:8080/swagger-ui.html
# 健康检查：http://localhost:8080/actuator/health
```

数据库账号（仅本地，见 `.env.example` 与 `docker/mysql-init/03-dev-app-user.sql`）：

- `gym_dev`：绑定 `gym_app` 角色，普通表 SELECT/INSERT/UPDATE，
  `settlement_revision` / `card_ledger` / `credit_event` / `business_event` 只有 SELECT/INSERT
  —— **应用与集成测试用它，才能验证"只追加表不可改"**。
- `gym_test`：仅测试夹具建/清数据用，禁止配给应用。

## 4. 约定（实现时必须遵守）

1. **统一响应**：成功 `{code,message,requestId,serverTime,data}`；失败再加 `fieldErrors[]`。
   `code` 是字符串稳定码（`com.wrgym.common.api.ErrorCode`），前端只按 code 判断。
2. **HTTP 状态**：400/401/403/404/409/412/413/415/422/428/429/501/503 由 `ErrorCode.httpStatus()` 决定。
3. **ID / 金额 / 时间**：ID 为十进制字符串（Long 已全局序列化为字符串）；金额整数分；
   时间 UTC 存储、API 输出带 `Z` 的 ISO 8601、业务日按 Asia/Shanghai 换算。
4. **分页**：`page`(默认1) + `pageSize`(默认20，≤100)，响应 `PageResult{items,total,page,pageSize}`。
5. **写接口**：带 `Idempotency-Key`(8–64)，幂等结果 `api_request` 与业务同事务；
   配置类更新用 `If-Match`/ETag（428/412）；资金与权益唯一性不依赖幂等键。
6. **事务与锁**：REPEATABLE READ + 当前读；固定锁顺序
   `customer_user → merchant_customer → store 配置 → store_slot → booking_order → member_card →`
   `visit_session → visit_settlement → device → access_credential/credential_window → device_operation →`
   `payment_record/card_ledger`；网络/设备调用一律在事务外。
7. **隔离**：后台 merchantId 只来自 JWT；越权统一 404；不信任请求体里的 merchantId。
8. **日志**：不记录 JWT、口令、人脸原图、临时密码、播放凭据、平台密钥。
9. **设备**：只替换适配层（`gym-adapter-api`），业务模块禁止 `if (mock)` 分支。
10. **禁止提交**：`.env`、密钥/证书、人脸图片、设备验证码、运行数据库、IDE 个人配置；
    测试数据不得使用真实身份与设备秘密（与仓库根 `.gitignore`、README 协作约定一致）。

## 5. 分支与 PR

```bash
git switch main && git pull --ff-only origin main
git switch -c feat/be-m03-store-slot
# 只改 backend/gym-module-store/**
mvn -pl gym-module-store -am test
git commit -m "feat(be-m03): 实现门店、营业规则与时段 15 个接口"
git push -u origin feat/be-m03-store-slot   # PR 目标：adoretheall63/unmanned-gym-saas:main
```

每个模块 PR 必须包含：接口编号范围、涉及表、规则编号（Q0xx）、测试证据、
OpenAPI 同步（首闭环操作同步 `api/first-loop.openapi.yaml`）、约定自检清单。

## 6. 当前状态

**feat/be-00-foundation（基础设施，已提交）**

- ✅ 父 POM + 22 个模块（业务模块为空壳，等各自分支实现）
- ✅ `gym-common`：统一响应/错误码/异常/分页/BaseEntity/UTC 时间/JSON 序列化/requestId/MyBatis 配置
- ✅ `gym-security`：双端 FilterChain、JWT 签发校验、401/403 统一响应
- ✅ `gym-bootstrap`：启动类、application.yml、OpenAPI 分组、CORS、冒烟测试
- ✅ `docker-compose.yml`：MySQL 8.4 + Redis 7（26 表 / 369 列 / 55 外键 / 141 索引已验证）

**feat/be-m01-auth-profile（认证与个人资料，接口文档 §2 全部 10 个接口）**

- ✅ 2.1 微信登录（M1 测试替身，openid 唯一身份，首次自动建号）、2.3 顾客退出（token_version 递增）
- ✅ 2.2 后台登录（BCrypt、失败统一 401、10 分钟 5 次失败限流、账号/商家停用 403）、2.4 后台退出
- ✅ 2.5 本人资料、2.6 修改昵称（Idempotency-Key + 同键不同体 409）、
  2.7 绑定手机号（AES-256-GCM 密文、一次性 code、能力未开通 422）、2.8 解除绑定
- ✅ 2.9 后台身份（每请求复核账号状态与 token_version）、2.10 修改密码（旧密码校验 + 禁止复用 + 版本递增）
- ✅ 跨模块能力：`AccountPort` 端口、`PrincipalRef`、幂等执行器（`api_request` 同事务 + 唯一键兜底）、
  登录限流（Redis + 进程内降级）、每请求账号状态过滤
- ✅ 验证证据：`mvn package` 通过；`JsonContractTest` 7/7；HTTP 端到端 `tools/verify-auth-api.ps1` **18/18 PASS**
  （含幂等重放/冲突、退出与改密后旧 JWT 401、脱敏与显式 null 字段）

**feat/be-m02-merchant-staff（商家与员工权限，接口文档 §3 全部 8 个接口）**

- ✅ 3.1 读取本商家信息（返回 ETag）、3.2 修改商家显示名（ADMIN + Idempotency-Key + If-Match 428/412，锁内重算比对）
- ✅ 3.3 员工列表（page/pageSize/total/items，按 JWT 商家过滤）、3.4 员工详情（返回 ETag，不输出密码哈希）
- ✅ 3.5 创建员工（201、登录名全局唯一、BCrypt、权限白名单、ADMIN 必须空权限数组、审计）
- ✅ 3.6 调整角色权限（幂等 + ETag + 管理员集合串行锁 + 最后一位启用管理员保护 + token_version 递增 + 审计）
- ✅ 3.7 启停账号（停用立即失效旧 JWT、禁止停用最后一位启用管理员、禁止物理删除）
- ✅ 3.8 管理员重置密码（BCrypt、token_version 递增、幂等快照只保存 accepted，不含密码）
- ✅ 基础设施增量：`Etags`/`Ids` 工具、`PermissionCodes` 白名单、`AuditWriter`（business_event 只追加）、
  `SecurityUtils.requireAdminRole/requirePermission`（角色取数据库权威状态，不信任 JWT 快照）
- ✅ 验证证据：`mvn package` 通过；HTTP 端到端 `tools/verify-merchant-staff.ps1` **31/31 PASS**
  （含 428/412、幂等重放与冲突、最后管理员 409、停用/改密后旧 JWT 401、跨商家 404、操作员 403）

**feat/be-m03-store-profile-slot（门店资料与时段查询，接口文档 §4 的第 1 批：4.1–4.7、4.13）**

- ✅ 4.1 公开营业门店列表（启用商家 + OPEN 门店，匿名可访问，分页）
- ✅ 4.2 公开门店详情（公开/后台两套 DTO：公开坐标 number、不返回 capacity 与 etag；固定 60 分钟、7 个自然日）
- ✅ 4.3 公开时段（只返回尚未开始时段；日期限含当天 7 天，超范围 422；`remaining` 为可预约余量、不可售给原因、公开响应不含 etag）
- ✅ 4.4 后台门店列表（`STORE_READ` 权限，分页 + 状态筛选，项目 etag）
- ✅ 4.5 后台门店详情（返回 HTTP ETag 与 body.etag，跨商家 404）
- ✅ 4.6 创建门店（ADMIN、201、创建即 CLOSED、营业时间规则校验、幂等）
- ✅ 4.7 修改展示资料（ADMIN、幂等 + If-Match 428/412、锁内重算、显式 null 清除可空字段、图片只允许本商家对象键）
- ✅ 4.13 后台时段列表（含已开始/关闭时段，逐项 etag；GET 不触发生成）
- ✅ 基础设施增量：`TimeZones`（UTC 存储 / Asia/Shanghai 营业日）、`MerchantPort`（门店模块不直读 merchant 表）、
  `Etags`/`Ids` 复用、Jackson `HH:mm:ss` 营业时间口径
- ✅ 验证证据：`mvn package` 通过；`JsonContractTest` 7/7；HTTP 端到端 `tools/verify-store-slot.ps1` **29/29 PASS**
  （含公开匿名访问、7 天范围 422、428/412、幂等重放、跨商家 404、STORE_READ 403、公开/后台 DTO 差异）

**feat/be-m03b-store-rules（门店规则与运营，接口文档 §4 的第 2 批：4.8–4.12、4.14、4.15）**

- ✅ 4.8 门店展示图片上传（201、multipart、JPEG/PNG/WebP 魔数 + 可解码校验、≤5MiB、objectKey 属本商家、幂等含文件摘要）
- ✅ 4.9 规则预览（只演算：previewHash/affectedUnsoldSlots/conflicts/uncoveredMinutes/canApply，不写库）
- ✅ 4.10 应用规则（幂等 + If-Match 428/412、锁内重算 hash、过期 409 PREVIEW_STALE、冲突 409 RULE_CONFLICT 带订单 ID、
  容量不得低于承诺、只改未售未来时段、按新规则补齐时段）
- ✅ 4.11 营业状态（DISABLED 前检查未结束到店/未履约订单/未决设备命令，冲突 409；CLOSED 停止新销售）
- ✅ 4.12 准入暂停/恢复（ADMIN 或 STORE_CONTROL；恢复前当前读检查门店状态与在场人数/容量，不安全 409）
- ✅ 4.14 补齐未来七天时段（200 + 仅 accepted；唯一键防重、只补未来完整小时段、跨午夜不截断）
- ✅ 4.15 调整未售时段（幂等 + 逐项 etag If-Match；已开始/已承诺 409 SLOT_COMMITTED；只改 capacity/price/status）
- ✅ 基础设施增量：`gym.storage` 配置 + `LocalObjectStorage`（M5 开发实现）、`StoreOpsQueryMapper`
  （架构例外的只读跨模块检查，待 visit/booking/device 端口替换）、时段生成与规则摘要复用
- ✅ 验证证据：`mvn package` 通过；`JsonContractTest` 7/7；HTTP 端到端 `tools/verify-store-rules.ps1` **30/30 PASS**
  （含幂等重放、428/412、PREVIEW_STALE、RULE 冲突、SLOT_COMMITTED、STORE_CONTROL 403、图片魔数 415、上传幂等）

**下一步**：`feat/be-m04a-booking-pay`（§6 预约下单与模拟支付，首闭环核心）。

## 目录：验证脚本

| 脚本 | 覆盖 |
|---|---|
| `tools/verify-auth-api.ps1` | §2 认证与个人资料（18 项） |
| `tools/verify-merchant-staff.ps1` | §3 商家与员工权限（31 项） |
| `tools/verify-store-slot.ps1` | §4.1–4.7、4.13（29 项） |
| `tools/verify-store-rules.ps1` | §4.8–4.12、4.14、4.15（30 项） |

脚本默认连 `http://127.0.0.1:18080`，并使用本地 `gym-mysql` 准备/清理测试数据（仅开发环境）。
