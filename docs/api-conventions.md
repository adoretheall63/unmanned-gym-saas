# API v1 公共约定（冻结稿）

基准：`origin/main` 检出 `e3a6c11` 的 `docs/接口文档.md` §1、`api/first-loop.openapi.yaml` 和后端 `ErrorCode.java`。下文“新增”表示本次冻结的目标行为，不代表后端已经实现。

## 1. 响应与链路标识

- **规则**：项目 JSON 成功响应必有 `{code,message,requestId,serverTime,data}`，`code` 恒为 `OK`；没有业务数据时显式返回 `data: null`。读取或同步完成用 200，创建用 201，异步受理用 202；202 不表示业务已完成。普通请求有非空 `X-Request-Id` 时原样透传，否则由服务端生成；响应头 `X-Request-Id` 与 JSON `requestId` 相同，失败响应也一样。幂等重放使用首次响应的链路标识，见 §4。厂商启用后的原始回调应答从其协议，不套项目 JSON 信封。
- **正例**：请求 `X-Request-Id: req-42`，响应 `200`、`X-Request-Id: req-42`，正文 `{"code":"OK","message":"操作成功","requestId":"req-42","serverTime":"2026-11-24T02:30:00Z","data":null}`。
- **反例**：响应头为 `req-42`，正文却写 `"requestId":"req-43"`；或者成功时 `code` 写 `SUCCESS` 或省略 `data`。
- **来源**：沿用 §1.1、§16.5 和 `ApiResponse` 的信封、`OK`、成功状态语义及厂商例外；`RequestIdFilter` 已设置响应头，但忽略入站值。新增入站透传及空结果约定。

## 2. HTTP 状态与业务错误码

- **规则**：使用对应的非 2xx HTTP 状态，错误体固定为 `{code,message,requestId,serverTime,fieldErrors}`，没有字段错误时返回 `fieldErrors: []`。字段错误项是 `{field,message}`；客户端按 `code` 分支，不解析 `message`，响应不回显堆栈、凭据或其他租户资源。
- **正例**：`400` + `{"code":"VALIDATION_ERROR","message":"请求参数不合法","requestId":"req-42","serverTime":"2026-11-24T02:30:00Z","fieldErrors":[{"field":"page","message":"必须大于等于 1"}]}`。
- **反例**：业务冲突返回 `200` + `STATE_CONFLICT`；把跨租户不可见资源报成 403；或省略 `fieldErrors`。
- **来源**：沿用 §1.4 和 `ApiError`；现网 HTTP 映射以 `ErrorCode.java` 为准，405 和 500 补入契约。

以下是 `ErrorCode` 全集（13 项）。`契约已有` 指基准文档 §1.4 已列出；`代码有、文档无` 指本次补记。表内反例给出相近但应使用其他码的情形。

| 业务码 | HTTP | 触发条件 | 正例 | 反例 | 来源 |
|---|---:|---|---|---|---|
| `VALIDATION_ERROR` | 400 | 请求格式、必填字段或请求头、类型、范围、枚举不合法；配置并发头缺失除外 | `page=0`；业务写缺 `Idempotency-Key` | 缺 `If-Match` → 428 | 契约已有；缺幂等键修正见 §4 |
| `UNAUTHENTICATED` | 401 | 受保护接口令牌缺失、无效、过期或已撤销；登录凭据无效 | JWT 已过期 | 有效 JWT 但权限不足 → 403 | 契约已有 |
| `FORBIDDEN` | 403 | 身份已识别但操作不被授权，或登录账号已停用 | 操作员没有退款权限 | 查询其他商家的订单 → 404 | 契约已有 |
| `NOT_FOUND` | 404 | 路由或资源不存在；资源因归属不可见；开发路由在非开发环境未开放 | 查询其他租户订单 | 已有路径不支持请求方法 → 405 | 契约已有 |
| `METHOD_NOT_ALLOWED` | 405 | 目标路径存在，但请求方法未注册 | 对只支持 GET 的详情路径发 POST | 路径不存在 → 404 | 代码有、文档无；本次补记 |
| `STATE_CONFLICT` | 409 | 当前状态不允许操作，或业务唯一键冲突 | 使用新幂等键提交已消费的手机号绑定码 | 同幂等键但不同请求体 → `IDEMPOTENCY_CONFLICT` | 契约已有 |
| `IDEMPOTENCY_CONFLICT` | 409 | 同作用域、同幂等键，规范化请求指纹不同 | 同键把 `nickname` 从 `A` 改成 `B` | 同键同请求 → 重放首次响应 | 契约已有 |
| `ETAG_MISMATCH` | 412 | 配置写已提交非空 `If-Match`，但与当前强 ETag 不匹配 | 拿旧版本 ETag 更新 | 未提交 `If-Match` → 428 | 契约已有 |
| `PRECONDITION_REQUIRED` | 428 | 配置写缺 `If-Match`，或头值为空 | PATCH 配置时省略 `If-Match` | 已提交不匹配的 ETag → 412 | 契约已有 |
| `RATE_LIMITED` | 429 | 请求触发已配置的频率限制 | 登录尝试超限 | 限流存储暂不可用 → 503 | 契约已有 |
| `PHONE_BINDING_UNAVAILABLE` | 422 | 请求格式正确，但本环境未开通或未提供手机号绑定能力 | 提交合法授权码，服务未配置手机号能力 | 授权码字段缺失 → 400 | 契约已有 |
| `DEPENDENCY_UNAVAILABLE` | 503 | 微信、数据库、限流存储等依赖暂不可用，或幂等事务暂时无法完成 | 微信服务超时；数据库连接不可用 | 未被分类的程序缺陷 → 500 | 契约已有 |
| `INTERNAL_ERROR` | 500 | 其他未识别、未转换的后端异常 | 业务代码抛出未处理的空指针异常 | 缺幂等键 → 400，不进入 500 兜底 | 代码有、文档无；本次补记 |

§1.4 另有下列**设计契约码**，不属于 `ErrorCode` 现网枚举。只在具体 operation 明列且实现后使用，不把接口示例当作现网支持清单：

| HTTP | §1.4 已列、后端枚举未列的码 |
|---|---|
| 403 | `VIDEO_STORAGE_FORBIDDEN` |
| 404 | `RECORDING_NOT_FOUND` |
| 409 | `SLOT_FULL`、`TIME_OVERLAP`、`PAYMENT_EXPIRED`、`VERSION_CONFLICT`、`PREVIEW_STALE`、`RULE_CONFLICT`、`REVIEW_CHANGED`、`REVIEW_DEPENDENCY`、`DEVICE_IN_USE`、`CARD_HAS_ACTIVE_ORDERS`、`ORDER_ALREADY_USED`、`OCCUPANCY_UNKNOWN`、`OPERATION_UNCERTAIN`、`LIGHT_OFF_UNSAFE` |
| 413 | `FILE_TOO_LARGE` |
| 415 | `UNSUPPORTED_MEDIA_TYPE` |
| 422 | `BENEFIT_NOT_SUPPORTED`、`BENEFIT_MISMATCH`、`CARD_EXPIRED`、`CARD_FROZEN`、`INSUFFICIENT_COUNT`、`SECRET_UNAVAILABLE`、`CAPABILITY_UNSUPPORTED`、`INVALID_TIME_RANGE` |
| 501 | `INTEGRATION_NOT_VERIFIED`（仅未启用的厂商协议保留入口） |
| 503 | `VIDEO_AUTHORIZATION_UNAVAILABLE` |

## 3. 分页与排序

- **规则**：分页 Query 使用整数 `page`（从 1 起，默认 1）和 `pageSize`（1–100，默认 20）；越界返回 400，不静默截断。分页 `data` 固定为 `{page,pageSize,total,items}`；`total` 为过滤后、分页前的总数，当前页无结果时 `items: []`，总数不因请求页越界而归零。允许调用方选择顺序时，只开放单值 `sort`，由各接口声明白名单枚举和默认值；枚举代表预定义顺序，不接收字段名、字段列表或表达式。未另行约定时按 `createdAt DESC, id DESC`，同值以 `id` 稳定排序。未开放排序的接口不声明 `sort`。
- **正例**：接口声明 `sort` 为 `NEWEST | OLDEST`；`?page=1&pageSize=20&sort=NEWEST` 在无匹配记录时返回 `{"page":1,"pageSize":20,"total":0,"items":[]}`。`NEWEST` 映射到 `createdAt DESC, id DESC`。
- **反例**：`?page=0&pageSize=200`、`?sort=createdAt` 或 `?sort=createdAt,desc`；响应只返回 `items` 数组。
- **来源**：沿用 §1.1 的默认值、上限、默认排序和空列表行为；新增统一分页包、错误处理和 `sort` 白名单。

## 4. 幂等写入

- **规则**：所有非 GET 的项目业务写（创建、修改、删除及会改变状态的动作）必须带 `Idempotency-Key`，32–128 个 ASCII 字符，仅允许 `[A-Za-z0-9_-]`。对进入业务写接口的请求，缺键、空键或格式不符一律 400 `VALIDATION_ERROR`，不进入 500 兜底。GET 不执行业务写；不落业务状态的调用不因使用 POST 而自动要求此头。厂商原始回调不要求项目 Header，按经确认的厂商协议验证和去重。
- **规则**：键按主体类型/ID、`operationId` 和规范化目标路径隔离；指纹覆盖方法、路径、影响写入的参数和规范化请求体，multipart 还须包含文件摘要。重试同一动作应复用键。同作用域同键同指纹，重新鉴权后重放首次已提交成功响应的 HTTP 状态、正文和业务响应头（例如 `ETag`），保留原 `requestId`、`serverTime`；响应 `X-Request-Id` 与该原始 `requestId` 一致，额外带 `Idempotent-Replay: true`，不重复执行业务。不以重试传入的新 ID 覆盖首次响应。对同作用域同键不同指纹返回 409 `IDEMPOTENCY_CONFLICT`。失败事务回滚，不作为首次成功结果记录；新键也不绕过业务唯一性约束。响应含凭据时不得把明文凭据放入通用幂等快照，接口须有专门的重放实现才能声明符合本约定。
- **正例**：`POST /api/v1/app/orders` 带 `Idempotency-Key: 0123456789abcdef0123456789abcdef` 首次返回 `201`；原样重试仍为 `201` 且正文相同，并增加 `Idempotent-Replay: true`。
- **反例**：未传该头时返回 500；传同键却更换 `slotId` 时再创建一笔订单；将 JWT 存为通用快照。
- **来源**：沿用 §1.2 的作用域、事务、冲突和秘密处理基线；新增通用必填、长度、重放头和首次响应语义。`AuthController` 的必填头缺失会在进入服务前进入 `GlobalExceptionHandler` 的 500 兜底；这是冻结稿明确修正的行为。当前 `IdempotencyService` 接受 8–64 字符，只恢复 HTTP 状态及业务快照，`api_request.idempotency_key` 为 `VARCHAR(64)`；实现时须同步入口校验、完整重放和存储容量。

## 5. 配置并发

- **规则**：配置详情 GET 通过响应头返回强 `ETag`（带双引号）；同一配置的写必须通过 `If-Match` 回传该单个值。缺失或空值返回 428 `PRECONDITION_REQUIRED`；其余未精确匹配当前强标签（含 `*`、弱标签或标签列表）返回 412 `ETAG_MISMATCH`。在锁定资源后比较只由可写配置生成的标签；配置写成功后为仍存在的配置返回新标签。若 DTO 有 `etag`，值与 HTTP 头一致。不用配置 ETag 替代订单等业务状态的事务控制。已提交的幂等重放在重新鉴权后直接返回首次结果，不因第一次写已经改变标签而变成 412；配置写的 `If-Match` 是写入指纹的一部分。
- **正例**：GET 返回 `ETag: "cfg-17"`；PATCH 带 `If-Match: "cfg-17"` 与幂等键，成功后返回 `ETag: "cfg-18"`。
- **反例**：缺头时无条件覆盖；拿旧标签写入成功；将设备心跳计入配置标签；用 JSON `etag` 代替 `If-Match`。
- **来源**：沿用 §1.2 和 `ConfigurationEtags` 的比较边界；新增明确强标签、写后返回以及与幂等的优先关系。

## 6. 命名

- **规则**：固定路径段全小写，用 kebab-case；资源名用复数，操作建模为子资源或有业务含义的专用子路径。路径参数占位符、JSON 字段、Query 参数用 camelCase；占位符不是固定路径段。已有路径不直接改名，按 §12 迁移。
- **正例**：`POST /api/v1/admin/orders/{orderId}/refund-requests`；Query `?pageSize=20`；JSON `{"amountCent":2400}`。
- **反例**：`POST /api/v1/admin/Order/{orderId}/refund`；`?page_size=20`；`{"AmountCent":2400}`。
- **来源**：沿用 §1.1 的 JSON 风格和已有资源路径惯例；新增固定路径段、复数和动作命名的统一约束。

## 7. 金额

- **规则**：金额用整数分，字段名以 `Cent` 结尾，绝对值不超过 `9007199254740991`。普通金额用非负 `Money`；只在接口明确为有符号差额时允许负数并单独声明范围。加减前核验币种一致；币种由接口固定或通过 `currency` 明示，不直接跨币种相加。
- **正例**：人民币 24 元写作 `{"amountCent":2400,"currency":"CNY"}`；同币种 2400 分减 500 分等于 1900 分。
- **反例**：`{"amountYuan":24.00}`、`{"amountCent":24.5}` 或将 CNY 与 USD 的分值直接相加。
- **来源**：沿用 §1.1 的整数分和上限；新增 `Cent` 后缀、非负组件与同币种运算要求。

## 8. 时间

- **规则**：瞬时字段使用 ISO-8601 UTC 且以 `Z` 结尾，字段名以 `At` 结尾，固定信封字段 `serverTime` 除外。自然日用 `date`，格式 `YYYY-MM-DD`；本项目营业日和统计日按 `Asia/Shanghai`。时长用整数 `*Minutes`。营业规则的本地钟点不是瞬时，仍用 `HH:mm:ss`；区间默认 `[start,end)`。
- **正例**：`{"createdAt":"2026-11-24T02:30:00Z","date":"2026-11-24","durationMinutes":60,"openTime":"07:00:00"}`。
- **反例**：`{"createdAt":"2026-11-24T10:30:00+08:00","date":"2026-11-24T00:00:00Z","durationHours":0.5}`。
- **来源**：沿用 §1.1 的 UTC 存储、本地营业日、营业钟点和半开区间；新增传输强制 `Z`、字段后缀与分钟单位。

## 9. 标识

- **规则**：主键及引用主键的 JSON 字段使用正整数 `number`，不是字符串或小数。给 JavaScript 客户端公开的主键限制在 `1..9007199254740991`，不得把超界值四舍五入。`orderNo`、`visitNo` 是独立的对外业务号，始终为字符串并保留前导零，不把业务号和主键混用。URL 的路径参数本身是文本，按其 ID 约束解析。
- **正例**：`{"id":101,"visitId":19,"orderNo":"ORD-000101","visitNo":"VIS-000019"}`。
- **反例**：`{"id":"101","orderNo":101}`；将 `9007199254740993` 转成 JS `Number` 后传输。
- **来源**：新增数字主键和编号分离约定。与 §1.1 原有十进制字符串 ID 不兼容；切换前核对历史数据范围并迁移 DTO 和客户端，超界记录需先安排可追溯迁移。

## 10. 范围与前缀

- **规则**：`/api/v1/app` 为顾客小程序；`/api/v1/admin` 为管理后台；`/api/v1/dev` 为显式启用的开发/Mock 工具；`/api/v1/integrations` 为厂商或外部系统接入（即 `vendor` scope）。OpenAPI 若把 `/api/v1` 写进 `servers.url`，`paths` 不重复此前缀。
- **正例**：`/api/v1/app/orders`、`/api/v1/admin/orders`、`/api/v1/dev/auth/customer-login`、`/api/v1/integrations/ezviz/{accountRef}/callback`。
- **反例**：把真实厂商回调置于 `/api/v1/dev`；最终拼出 `/api/v1/api/v1/app/orders`。
- **来源**：沿用 §1.1、既有 OpenAPI 和 §16 的路径；新增四类 scope 的显式映射。

## 11. Operation 归属与状态

- **规则**：每个 operation 必填一个 `x-owner`（`mini-app` / `admin-web` / `backend`）和一个 `x-status`（`implemented` / `planned` / `reserved`）。公共基础设施 operation（认证、幂等、审计、设备回调）优先归 `backend`，不会仅因某业务接口调用了这些能力而改归属；其余 app → `mini-app`，admin → `admin-web`，dev 或 vendor → `backend`。`implemented` 表示已核对该版本有可执行实现；`planned` 表示有业务契约但尚无实现；`reserved` 表示只保留入口、行为待确认或验收，不能当作可用能力。
- **正例**：已实现的 `/api/v1/app/auth/wechat-login` 标 `x-owner: backend`、`x-status: implemented`；待厂商协议验收的回调标 `x-owner: backend`、`x-status: reserved`。
- **反例**：认证因挂在 `/app` 就标 `mini-app`；给只有文档的操作标 `implemented`；把标记只放在路径或标签层而未放进 operation。
- **来源**：新增扩展字段、优先级和状态语义；沿用 §16 对厂商保留入口的区分。

## 12. 弃用

- **规则**：旧 operation 保留在契约和路由中，声明 `deprecated: true` 和 `x-replaced-by`（值为替代 operation 的 `operationId`）；先提供替代接口并迁移调用方，不在本版契约直接删除旧路径或方法。
- **正例**：旧 `POST /api/v1/admin/orders/{orderId}/refund` 保留，标 `deprecated: true`、`x-replaced-by: createRefundRequest`；替代 operation 使用 `POST /api/v1/admin/orders/{orderId}/refund-requests`。
- **反例**：删除旧路径只保留新路径；或标 `deprecated: true` 但没有指向实际替代 operation。
- **来源**：沿用基准文档保留首闭环旧路径的做法；新增显式弃用标记和指向规则。

## OpenAPI 3.1 引用示例

将 `components.yaml` 的根节点 `components` 合并到 OpenAPI 主文件，不把整个片段再嵌套一层。`ApiEnvelope` 的 `data` 及 `Page` 的 `items` 元素由接口收窄；以下以主文件已有 `Order` 为例：

```yaml
schema:
  allOf:
    - $ref: '#/components/schemas/ApiEnvelope'
    - properties:
        data:
          allOf:
            - $ref: '#/components/schemas/Page'
            - properties:
                items:
                  items:
                    $ref: '#/components/schemas/Order'
```

`SortParam` 是模板。组装主文件时，启用排序的接口复制其 `name`、`in` 等字段，在本地参数对象的 `schema` 中写 `enum` 与默认值；不能在 Parameter `$ref` 同级加 `schema` 试图改写引用。相同白名单的接口可复用已具体化的参数。405、500 等未预置的错误响应按 operation 声明并引用 `ApiError`。

## YAML 自检

指定命令退出 1：当前 Python 未安装 PyYAML；当前 Node 也未安装 `yaml` 或 `js-yaml`。使用本机 Ruby/Psych 解析同一文件，退出 0。

```console
$ rtk proxy python3 -c "import yaml,sys;yaml.safe_load(open('/tmp/unify/components.yaml'))"
Traceback (most recent call last):
  File "<string>", line 1, in <module>
ModuleNotFoundError: No module named 'yaml'
$ rtk proxy ruby -rpsych -e 'doc = Psych.safe_load(File.read(ARGV.fetch(0)), aliases: true); puts "OK: YAML 可解析；components 分组 #{doc.fetch("components").size} 个"' /tmp/unify/components.yaml
OK: YAML 可解析；components 分组 5 个
```
