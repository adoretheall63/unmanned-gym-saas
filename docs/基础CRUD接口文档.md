# 无人健身房 SaaS 基础 CRUD 接口文档（62个接口）

整理日期：2026-11-16。来源为《完整接口文档v1.2》第19章的A类基础CRUD，共62个接口。保留原接口编号、方法、路由、权限、请求参数及响应结构，便于与完整设计交叉核对；本文件不包含复杂事务、设备控制、支付退款、卡权益确认、视频、人脸上传或统计计算接口。

本文为设计契约，尚未实现后端服务。示例身份、门店、ID及JSON均为演示内容，不是真实生产请求或凭据。所有路由已有/api/v1前缀。读接口应查询真实业务数据库或测试fixture；禁止为方便开发长期硬编码业务返回。

“基础CRUD”仍须校验本人/商家归属、权限和参数。本清单含56个查询和6个新增/修改接口；6个写接口按已有契约携带Idempotency-Key，其中3个配置更新还要求If-Match。写业务记录、幂等结果及必要审计使用短事务，不因分类简单而取消这些保障。目录中的产品创建不等于给顾客发卡，登记设备展示配置修改不等于操作硬件。

完整OpenAPI目前仍是首闭环v1.1，不是本文所有接口的机器可读镜像；卡产品/后台Slot.etag等新定义以当前完整设计为准。本文件保留范围内字段，不引入新的数据库表或业务规则。


完整契约与复杂接口见[接口文档](接口文档.md#implementation-classification)；本文作为62个CRUD接口的分工视图保留，不单独决定接口变更。

## 1 公共约定


### 1.1 地址、参数与响应

| 项目 | 约定 |
|---|---|
| 地址前缀 | 下文路由均已写全 `/api/v1`。本地 `http://localhost:8080`；部署使用项目自己的HTTPS域名。 |
| 请求格式 | 除上传为multipart/form-data、厂商原始回调遵守厂商协议外，均application/json；JSON属性使用camelCase，拒绝未定义字段。Body为空对象的操作发送 `{}`；标“无请求体”的不发送Body。 |
| 路径和查询 | Path参数必填；Query不放JSON请求体；分页page默认1、pageSize默认20且<=100。布尔Query用true/false。每个接口的参数表列出额外Header、Path、Query与Body。 |
| 鉴权 | 顾客和后台使用各自Bearer JWT：`Authorization: Bearer <token>`。有效期7200秒；每次验证账号启用状态、token_version和商家状态。未登录401、角色不足403、资源不属于本人或本商家统一404。 |
| 隔离 | 后台merchantId来自JWT；顾客userId来自JWT；门店归属从slot、订单、设备反查。查询筛选merchantId或授权选择商家不等于允许指定资源所有者。共享企业账号的回调按登记设备归属分发。 |
| ID | 业务ID为1..9223372036854775807的十进制字符串，不能转JS Number；版本、数量、页码为整数。 |
| 金额 | 单位分、整数且绝对值不超过9007199254740991。预约费/补缴/购卡金额由后端计算；仅管理员的有原因退款及补偿操作允许提交经校验的调整值。 |
| 时间 | 时间点ISO 8601带时区，保存UTC；营业时间HH:mm:ss，营业日和统计日按Asia/Shanghai。区间通常[start,end)，7天包含当天；60分钟时段、提前/延迟宽限0。 |
| 默认排序 | 除接口特别说明，业务列表按createdAt,id倒序；同时间用id保证稳定。时间筛选以包含起点、不含终点计算。 |
| 成功响应 | 200读取/完成，201创建，202异步受理。JSON顶层统一code、message、requestId、serverTime、data。202不表示设备已执行、已撤权或退款完成。 |
| 空值与数组 | 必有字段可以为null时明确写出；无列表结果返回items=[]、total=0，无当前到店返回visit=null；不得用0冒充未知电量、人数、在线率。数组子字段“否/条件”指有元素时按其对象定义出现。 |
| 模拟标识 | 收退款channel=MOCK，设备provider=MOCK/EZVIZ，统计provider=MOCK/REAL/MIXED；界面逐项标记，不能全局一个开关宣称全真实。 |
| 日志和缓存 | 身份、账单、支付、临时密码、人脸及视频授权响应Cache-Control:no-store。JWT、code、密码、人脸原图、播放凭据、平台主密钥不进入日志和幂等快照。公开门店数据可短暂缓存，不能用缓存放弃最终鉴权。 |

### 1.2 写入、幂等与配置并发

标记Idempotency-Key的写接口必须提供8–64位字母数字、下划线或短横线；同一动作网络重试复用，同键不同参数409 IDEMPOTENCY_CONFLICT。作用域为主体类型/ID、operationId及规范化路径，包含目标资源，hash包含方法、路径、规范化JSON；multipart加入文件字节摘要。业务结果和api_request在同一本地事务提交；失败回滚。不在快照保存秘密；登录、改本人密码、按需读密码、视频短期授权等例外在各接口注明。即使换键，原款退款额度、扣次/返次和同订单未决开门等业务唯一性仍须保证。

配置更新标记If-Match：GET详情HTTP ETag与响应etag（有该字段时）一致；列表项可返回单项etag。未定义etag属性的旧DTO仅通过HTTP Header提供。摘要由可写配置字段生成，不新增数据库version列；锁定资源后重新计算，不把设备心跳或reservedCount的变化当配置修改。缺少Header返回428 PRECONDITION_REQUIRED，不匹配412 ETAG_MISMATCH。涉及订单/结算的业务竞争用行锁和expectedVersion，不用配置ETag代替事务。

当前读、锁顺序、退款额度、fencing及外部调用事务外执行，遵循现有《数据库事务与查询约定v1.1》。写入时重新鉴权，独立SQL在关键锁后读取now；不能靠GET触发到期/爽约或状态变化。资金、权益、结算修订和审计事实不可删除；更正只追加冲正或版本。JSON解释快照不能与数值列分别手改。

### 1.3 后台权限

| 身份/权限码 | 范围 |
|---|---|
| ADMIN | 本商家全部权限；管理员、员工、退款、卡补偿、信用及账单复核等关键操作仅ADMIN。 |
| STORE_READ / STORE_CONTROL | 门店资料只读 / 日常暂停和恢复准入；规则和计费修改仍ADMIN。 |
| CUSTOMER_READ / ORDER_READ / VISIT_READ / CARD_READ | 本商家顾客、预约、到店、卡权益读取。 |
| FINANCE_READ | 本商家现金流水与账单修订读取；无退款权限。 |
| DEVICE_READ / DEVICE_CONTROL / VIDEO_READ / LIGHT_CONTROL | 设备观察 / 受业务约束的控制 / 摄像机短期授权 / 演示灯具操作。 |
| REVIEW_READ / REVIEW_HANDLE | 待办读取 / 无资金及时间修正的日常复核；具体接口可要求ADMIN。 |
| REPORT_READ / REPORT_REFRESH / AUDIT_READ | 报表读取 / 从事实重算 / 操作记录只读。 |

操作员权限从admin_user.permissions白名单数组取得，不做逐门店员工授权；ADMIN覆盖所有权限码。上表为补齐接口所用的实现权限码，不增加平台管理员、入驻、抽佣或SaaS订阅功能。

### 1.4 统一失败响应

| JSON字段 | 类型 | 必有 | 说明 |
|---|---|---|---|
| code | string | 是 | 稳定业务错误码，前端不要解析message做状态判断 |
| message | string | 是 | 可展示错误说明，不暴露堆栈、密钥或其他商家资源 |
| requestId | string | 是 | 排查用链路标识 |
| serverTime | string(date-time) | 是 | 服务端时间 |
| fieldErrors | array | 是 | 无字段错误时[] |
| fieldErrors[].field | string | 条件 | 错误字段路径 |
| fieldErrors[].message | string | 条件 | 校验原因 |

```json
{"code":"SLOT_FULL","message":"该时段已约满","requestId":"req-demo-error","serverTime":"2026-11-15T09:50:00.000Z","fieldErrors":[]}
```

| HTTP | 业务码示例与使用条件 |
|---|---|
| 400 | VALIDATION_ERROR：格式、未定义字段、ID超界、未满足类型/枚举；字段明细在fieldErrors。 |
| 401 | UNAUTHENTICATED：缺令牌、过期、token_version失效；登录统一认证失败。 |
| 403 | FORBIDDEN、VIDEO_STORAGE_FORBIDDEN：已登录但无操作权限或依赖授权不足。 |
| 404 | NOT_FOUND、RECORDING_NOT_FOUND：资源不可见或没有录像；开发路由在非开发环境不存在。 |
| 409 | SLOT_FULL、TIME_OVERLAP、PAYMENT_EXPIRED、STATE_CONFLICT、IDEMPOTENCY_CONFLICT、VERSION_CONFLICT、PREVIEW_STALE、RULE_CONFLICT、REVIEW_CHANGED、REVIEW_DEPENDENCY、DEVICE_IN_USE、CARD_HAS_ACTIVE_ORDERS、ORDER_ALREADY_USED、OCCUPANCY_UNKNOWN、OPERATION_UNCERTAIN、LIGHT_OFF_UNSAFE：状态或并发冲突。 |
| 412 / 428 | ETAG_MISMATCH / PRECONDITION_REQUIRED：配置并发头不匹配 / 缺失。 |
| 413 / 415 | FILE_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE：上传过大或不支持的文件类型。 |
| 422 | BENEFIT_NOT_SUPPORTED、BENEFIT_MISMATCH、CARD_EXPIRED、CARD_FROZEN、INSUFFICIENT_COUNT、PHONE_BINDING_UNAVAILABLE、SECRET_UNAVAILABLE、CAPABILITY_UNSUPPORTED、INVALID_TIME_RANGE：格式正确但业务不允许。 |
| 429 | RATE_LIMITED：登录、控制、授权或其他接口限流。 |
| 501 | INTEGRATION_NOT_VERIFIED：仅厂商回调保留入口尚未验收时使用，不冒充已接入。 |
| 503 | DEPENDENCY_UNAVAILABLE、VIDEO_AUTHORIZATION_UNAVAILABLE：微信/设备/存储依赖暂不可用，不能伪造成功。 |

下面每个CRUD接口列成功响应字段表和JSON结构示例；失败采用本节表。示例是格式说明，不是已执行返回，也不表示所有对象在同一时间线。请求参数表的必填指字段出现要求；条件必填及跨字段校验以接口说明为准。


## 接口总目录

**认证与个人资料**

| 原编号 | 接口 | 方法 | 路由 |
|---|---|---|---|
| 2.5 | 读取本人资料 | GET | `/api/v1/app/me` |
| 2.6 | 修改本人昵称 | PATCH | `/api/v1/app/me` |
| 2.9 | 读取后台登录身份 | GET | `/api/v1/admin/me` |

**商家与员工权限**

| 原编号 | 接口 | 方法 | 路由 |
|---|---|---|---|
| 3.1 | 读取本商家信息 | GET | `/api/v1/admin/merchant` |
| 3.2 | 修改本商家显示名称 | PATCH | `/api/v1/admin/merchant` |
| 3.3 | 员工列表 | GET | `/api/v1/admin/staff` |
| 3.4 | 员工详情 | GET | `/api/v1/admin/staff/{staffId}` |

**门店、营业规则与时段**

| 原编号 | 接口 | 方法 | 路由 |
|---|---|---|---|
| 4.1 | 公开营业门店列表 | GET | `/api/v1/app/stores` |
| 4.2 | 门店详情 | GET | `/api/v1/app/stores/{storeId}` |
| 4.3 | 查询某日时段 | GET | `/api/v1/app/stores/{storeId}/slots` |
| 4.4 | 后台门店列表 | GET | `/api/v1/admin/stores` |
| 4.5 | 后台门店详情 | GET | `/api/v1/admin/stores/{storeId}` |
| 4.6 | 创建门店 | POST | `/api/v1/admin/stores` |
| 4.7 | 修改门店展示资料 | PATCH | `/api/v1/admin/stores/{storeId}` |
| 4.13 | 后台时段列表 | GET | `/api/v1/admin/stores/{storeId}/slots` |

**顾客、黑名单与信用**

| 原编号 | 接口 | 方法 | 路由 |
|---|---|---|---|
| 5.3 | 本人信用流水 | GET | `/api/v1/app/merchants/{merchantId}/credit-events` |
| 5.6 | 某顾客在本商家的订单 | GET | `/api/v1/admin/customers/{userId}/orders` |
| 5.7 | 某顾客在本商家的到店记录 | GET | `/api/v1/admin/customers/{userId}/visits` |
| 5.9 | 商家顾客信用流水 | GET | `/api/v1/admin/customers/{userId}/credit-events` |

**预约订单与支付退款**

| 原编号 | 接口 | 方法 | 路由 |
|---|---|---|---|
| 6.2 | 本人跨商家订单 | GET | `/api/v1/app/orders` |
| 6.3 | 本人订单详情 | GET | `/api/v1/app/orders/{orderId}` |
| 6.6 | 本商家订单列表 | GET | `/api/v1/admin/orders` |
| 6.7 | 本商家订单详情 | GET | `/api/v1/admin/orders/{orderId}` |

**卡产品、持有卡与权益**

| 原编号 | 接口 | 方法 | 路由 |
|---|---|---|---|
| 7.1 | 公开卡产品列表 | GET | `/api/v1/app/merchants/{merchantId}/card-products` |
| 7.2 | 公开卡产品详情 | GET | `/api/v1/app/merchants/{merchantId}/card-products/{productId}` |
| 7.4 | 本人持有卡列表 | GET | `/api/v1/app/member-cards` |
| 7.5 | 本人持有卡详情 | GET | `/api/v1/app/member-cards/{cardId}` |
| 7.11 | 本人卡权益流水 | GET | `/api/v1/app/member-cards/{cardId}/ledger` |
| 7.12 | 后台卡产品列表 | GET | `/api/v1/admin/card-products` |
| 7.13 | 后台卡产品详情 | GET | `/api/v1/admin/card-products/{productId}` |
| 7.14 | 创建卡产品 | POST | `/api/v1/admin/card-products` |
| 7.17 | 后台持有卡列表 | GET | `/api/v1/admin/member-cards` |
| 7.18 | 后台持有卡详情 | GET | `/api/v1/admin/member-cards/{cardId}` |
| 7.19 | 后台卡权益流水 | GET | `/api/v1/admin/member-cards/{cardId}/ledger` |

**通行凭证与实际到店**

| 原编号 | 接口 | 方法 | 路由 |
|---|---|---|---|
| 8.1 | 解析入口二维码 | GET | `/api/v1/app/access-points/{scanCode}` |
| 8.5 | 本人到店记录 | GET | `/api/v1/app/visits` |
| 8.6 | 查询指定门店当前到店 | GET | `/api/v1/app/visits/current` |
| 8.7 | 本人到店详情 | GET | `/api/v1/app/visits/{visitId}` |
| 8.9 | 本商家到店列表 | GET | `/api/v1/admin/visits` |
| 8.12 | 后台到店详情 | GET | `/api/v1/admin/visits/{visitId}` |

**账单与资金流水**

| 原编号 | 接口 | 方法 | 路由 |
|---|---|---|---|
| 9.4 | 本人收退款流水 | GET | `/api/v1/app/payments` |
| 9.5 | 本人收退款详情 | GET | `/api/v1/app/payments/{paymentId}` |
| 9.6 | 后台收退款流水 | GET | `/api/v1/admin/payments` |
| 9.7 | 后台收退款详情 | GET | `/api/v1/admin/payments/{paymentId}` |
| 9.8 | 后台账单修订历史 | GET | `/api/v1/admin/visits/{visitId}/settlement/revisions` |
| 9.9 | 本人账单修订历史 | GET | `/api/v1/app/visits/{visitId}/settlement/revisions` |

**设备、命令与事件**

| 原编号 | 接口 | 方法 | 路由 |
|---|---|---|---|
| 10.1 | 设备列表 | GET | `/api/v1/admin/devices` |
| 10.2 | 设备详情 | GET | `/api/v1/admin/devices/{deviceId}` |
| 10.4 | 修改设备展示配置 | PATCH | `/api/v1/admin/devices/{deviceId}` |
| 10.9 | 设备命令列表 | GET | `/api/v1/admin/device-operations` |
| 10.10 | 设备命令详情 | GET | `/api/v1/admin/device-operations/{operationId}` |
| 10.13 | 设备事件列表 | GET | `/api/v1/admin/device-events` |
| 10.14 | 设备事件详情 | GET | `/api/v1/admin/device-events/{eventId}` |
| 10.15 | 查询稳定设备人员映射 | GET | `/api/v1/admin/devices/{deviceId}/persons` |
| 10.16 | 后台通行凭证列表 | GET | `/api/v1/admin/access-credentials` |
| 10.17 | 后台通行凭证详情 | GET | `/api/v1/admin/access-credentials/{credentialId}` |

**人脸授权与资料删除**

| 原编号 | 接口 | 方法 | 路由 |
|---|---|---|---|
| 12.2 | 本人已授权人脸资料列表 | GET | `/api/v1/app/face-profiles` |
| 12.4 | 查看本人授权资料元数据 | GET | `/api/v1/app/face-profiles/{profileId}` |

**异常待办与人工复核**

| 原编号 | 接口 | 方法 | 路由 |
|---|---|---|---|
| 14.1 | 本商家异常待办列表 | GET | `/api/v1/admin/review-tasks` |
| 14.2 | 待办详情 | GET | `/api/v1/admin/review-tasks/{taskId}` |

**日报、统计与审计**

| 原编号 | 接口 | 方法 | 路由 |
|---|---|---|---|
| 15.7 | 操作审计列表 | GET | `/api/v1/admin/audit-events` |
| 15.8 | 操作审计详情 | GET | `/api/v1/admin/audit-events/{auditId}` |

## 2 认证与个人资料

### 2.5 读取本人资料


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/me` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q009–Q012 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.nickname | string / null | 是 | 昵称 |
| data.phoneMasked | string / null | 是 | 已验证手机号脱敏展示 |
| data.phoneVerified | boolean | 是 | 是否已通过后端校验 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "nickname": null,
    "phoneMasked": null,
    "phoneVerified": false
  }
}
```

**业务处理与限制**

不返回 openid、手机号密文或人脸原图。

### 2.6 修改本人昵称


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/me` |
| 请求方式 | PATCH |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q009 Q013 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| Idempotency-Key | Header | string | 是 | 同一次动作的网络重试复用；不同动作换键；8–64位字母数字、下划线或短横线；最短 8；最长 64 |
| nickname | Body | string | 是 | 昵称，最长64字符；最短 1；最长 64 |

请求JSON示例：

```json
{
  "nickname": "同学A"
}
```

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.nickname | string / null | 是 | 昵称 |
| data.phoneMasked | string / null | 是 | 已验证手机号脱敏展示 |
| data.phoneVerified | boolean | 是 | 是否已通过后端校验 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "nickname": null,
    "phoneMasked": null,
    "phoneVerified": false
  }
}
```

**业务处理与限制**

只接受 nickname；不能修改身份、角色或商家归属。

### 2.9 读取后台登录身份


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/me` |
| 请求方式 | GET |
| 权限 | 后台 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q014–Q016 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.merchantId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.username | string | 是 | 后台登录名 |
| data.role | string | 是 | 业务数据字段；枚举：ADMIN, OPERATOR |
| data.permissions | array<string> | 是 | 允许的权限码 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "merchantId": "101",
    "username": "demo-value",
    "role": "ADMIN",
    "permissions": []
  }
}
```

**业务处理与限制**

返回当前角色及权限；后台每次请求仍重新校验账号状态与token_version。

## 3 商家与员工权限

### 3.1 读取本商家信息


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/merchant` |
| 请求方式 | GET |
| 权限 | 后台 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q001 Q013 Q014 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.name | string | 是 | 商家名称 |
| data.status | string | 是 | 状态；枚举：ACTIVE, DISABLED |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "name": "演示商家",
    "status": "ACTIVE"
  }
}
```

**业务处理与限制**

商家由JWT确定；无商家列表、入驻或订阅收费接口。返回HTTP ETag。

### 3.2 修改本商家显示名称


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/merchant` |
| 请求方式 | PATCH |
| 权限 | ADMIN |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q001 Q104 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| Idempotency-Key | Header | string | 是 | 同一次动作的网络重试复用；不同动作换键；8–64位字母数字、下划线或短横线；最短 8；最长 64 |
| If-Match | Header | string | 是 | 缺失428，资源配置变化412；锁内再次比对；读取资源时返回的 ETag |
| name | Body | string | 是 | 商家名，最长100字符；最短 1；最长 100 |
| reason | Body | string | 是 | 操作原因，非空；写入审计；最短 1；最长 500 |

请求JSON示例：

```json
{
  "name": "演示商家",
  "reason": "课程演示设备故障核实"
}
```

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.name | string | 是 | 商家名称 |
| data.status | string | 是 | 状态；枚举：ACTIVE, DISABLED |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "name": "演示商家",
    "status": "ACTIVE"
  }
}
```

**业务处理与限制**

不能在此停用商家或改变归属；原始交易事实保留，展示名修改留审计。

### 3.3 员工列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/staff` |
| 请求方式 | GET |
| 权限 | ADMIN |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q014 Q015 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| status | Query | string | 否 | ACTIVE/DISABLED；ACTIVE/DISABLED |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].username | string | 否/条件 | 全局唯一登录名 |
| data.items[].role | string | 否/条件 | 角色；枚举：ADMIN, OPERATOR |
| data.items[].permissions | array<string> | 否/条件 | 授权权限码 |
| data.items[].status | string | 否/条件 | 状态；枚举：ACTIVE, DISABLED |
| data.items[].createdAt | string | 否/条件 | 创建时间；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "username": "operator01",
        "role": "OPERATOR",
        "permissions": [],
        "status": "ACTIVE",
        "createdAt": "2026-11-15T10:00:00.000Z"
      }
    ]
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

### 3.4 员工详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/staff/{staffId}` |
| 请求方式 | GET |
| 权限 | ADMIN |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q014 Q015 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| staffId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.username | string | 是 | 全局唯一登录名 |
| data.role | string | 是 | 角色；枚举：ADMIN, OPERATOR |
| data.permissions | array<string> | 是 | 授权权限码 |
| data.status | string | 是 | 状态；枚举：ACTIVE, DISABLED |
| data.createdAt | string | 是 | 创建时间；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "username": "operator01",
    "role": "OPERATOR",
    "permissions": [],
    "status": "ACTIVE",
    "createdAt": "2026-11-15T10:00:00.000Z"
  }
}
```

**业务处理与限制**

返回HTTP ETag；不返回密码哈希。

## 4 门店、营业规则与时段

### 4.1 公开营业门店列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/stores` |
| 请求方式 | GET |
| 权限 | 公开 |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 沿用首闭环 v1.1 |
| 业务规则 | Q002 Q028 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| page | Query | integer | 否 | 当前页；最小 1；默认 1 |
| pageSize | Query | integer | 否 | 每页条数；最小 1；最大 100；默认 20 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data | object | 是 | 业务数据 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].merchantId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].merchantName | string | 否/条件 | 商家名称 |
| data.items[].name | string | 否/条件 | 名称 |
| data.items[].address | string | 否/条件 | 门店地址 |
| data.items[].status | string | 否/条件 | 业务状态，枚举见本行；枚举：OPEN, CLOSED, DISABLED |
| data.items[].slotPriceCent | integer | 否/条件 | 当前默认价格；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].estimatedOccupancy | integer / null | 否/条件 | 按确认进出计算，未知为null；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].occupancySource | string | 否/条件 | 业务数据字段；枚举：CONFIRMED_RECORDS, UNKNOWN |
| data.items[].deviceSource | string | 否/条件 | 业务数据字段；枚举：MOCK, EZVIZ, MIXED, UNKNOWN |
| data.page | integer | 是 | 页码；最小 1；最大 9007199254740991；格式 int64 |
| data.pageSize | integer | 是 | 页大小；最小 1；最大 9007199254740991；格式 int64 |
| data.total | integer | 是 | 总数；最小 0；最大 9007199254740991；格式 int64 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "demo-value",
  "requestId": "101",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "items": [
      {
        "id": "101",
        "merchantId": "101",
        "merchantName": "演示商家",
        "name": "演示门店",
        "address": "课程演示资料",
        "status": "OPEN",
        "slotPriceCent": 0,
        "estimatedOccupancy": null,
        "occupancySource": "CONFIRMED_RECORDS",
        "deviceSource": "MOCK"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1
  }
}
```

**业务处理与限制**

只返回启用商家的公开营业门店。每张卡片显示商家名；不返回员工、密钥或敏感设备数据。

### 4.2 门店详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/stores/{storeId}` |
| 请求方式 | GET |
| 权限 | 公开 |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 沿用首闭环 v1.1 |
| 业务规则 | Q019–Q029 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| storeId | Path | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data | object | 是 | 业务数据 |
| data.store | object | 是 | 门店卡片资料 |
| data.store.id | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.store.merchantId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.store.merchantName | string | 是 | 商家名称 |
| data.store.name | string | 是 | 名称 |
| data.store.address | string | 是 | 门店地址 |
| data.store.status | string | 是 | 业务状态，枚举见本行；枚举：OPEN, CLOSED, DISABLED |
| data.store.slotPriceCent | integer | 是 | 当前默认价格；最小 0；最大 9007199254740991；格式 int64 |
| data.store.estimatedOccupancy | integer / null | 是 | 按确认进出计算，未知为null；最小 0；最大 9007199254740991；格式 int64 |
| data.store.occupancySource | string | 是 | 业务数据字段；枚举：CONFIRMED_RECORDS, UNKNOWN |
| data.store.deviceSource | string | 是 | 业务数据字段；枚举：MOCK, EZVIZ, MIXED, UNKNOWN |
| data.description | string / null | 是 | 门店器械及展示说明 |
| data.images | array<string> | 是 | 公开门店展示图片数组 |
| data.latitude | number / null | 是 | 纬度，未登记时null |
| data.longitude | number / null | 是 | 经度，未登记时null |
| data.openTime | string | 是 | 北京时间营业起点HH:mm:ss；格式表达式 ^[0-9]{2}:[0-9]{2}:[0-9]{2}$ |
| data.closeTime | string | 是 | 北京时间营业结束HH:mm:ss；格式表达式 ^[0-9]{2}:[0-9]{2}:[0-9]{2}$ |
| data.is24h | boolean | 是 | 是否全天营业 |
| data.endsNextDay | boolean | 是 | 营业结束是否在次日 |
| data.timezone | string | 是 | 业务时区；枚举：Asia/Shanghai |
| data.slotMinutes | integer | 是 | 固定60；最小 1；最大 9007199254740991；格式 int64 |
| data.bookingDays | integer | 是 | 含当天共7天；最小 1；最大 9007199254740991；格式 int64 |
| data.overtimePriceCent | integer | 是 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.overtimeCapCent | integer | 是 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.admissionPaused | boolean | 是 | 当前是否暂停新入场 |
| data.pauseReason | string / null | 是 | 暂停准入的原因 |
| data.entryGraceMinutes | integer | 是 | 固定0；最小 0；最大 9007199254740991；格式 int64 |
| data.exitGraceMinutes | integer | 是 | 固定0；最小 0；最大 9007199254740991；格式 int64 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "demo-value",
  "requestId": "101",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "store": {
      "id": "101",
      "merchantId": "101",
      "merchantName": "演示商家",
      "name": "演示门店",
      "address": "课程演示资料",
      "status": "OPEN",
      "slotPriceCent": 0,
      "estimatedOccupancy": null,
      "occupancySource": "CONFIRMED_RECORDS",
      "deviceSource": "MOCK"
    },
    "description": null,
    "images": [],
    "latitude": null,
    "longitude": null,
    "openTime": "08:00:00",
    "closeTime": "23:00:00",
    "is24h": false,
    "endsNextDay": false,
    "timezone": "Asia/Shanghai",
    "slotMinutes": 60,
    "bookingDays": 7,
    "overtimePriceCent": 100,
    "overtimeCapCent": 3000,
    "admissionPaused": false,
    "pauseReason": null,
    "entryGraceMinutes": 0,
    "exitGraceMinutes": 0
  }
}
```

**业务处理与限制**

只允许公开可展示门店；停用或不可见资源404。

### 4.3 查询某日时段


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/stores/{storeId}/slots` |
| 请求方式 | GET |
| 权限 | 公开 |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 沿用首闭环 v1.1 |
| 业务规则 | Q020–Q026 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| date | Query | string | 是 | Asia/Shanghai日期，含今天未来七个自然日；格式 date |
| storeId | Path | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data | object | 是 | 业务数据 |
| data.storeId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.date | string | 是 | 北京时间日期；格式 date |
| data.lastBookableDate | string | 是 | 业务数据字段；格式 date |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].storeId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].startAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].endAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].capacity | integer | 否/条件 | 容量；最小 1；最大 9007199254740991；格式 int64 |
| data.items[].remaining | integer | 否/条件 | 可预约余量，非实际空位；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].priceCent | integer | 否/条件 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].status | string | 否/条件 | 业务状态，枚举见本行；枚举：OPEN, CLOSED |
| data.items[].bookable | boolean | 否/条件 | 业务数据字段 |
| data.items[].unavailableReason | string / null | 否/条件 | 开始、停店或不可售等原因 |
| data.items[].etag | string | 否/条件 | 后台列表项配置摘要，供单时段If-Match；公开时段响应不包含此字段 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "demo-value",
  "requestId": "101",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "storeId": "101",
    "date": "2026-11-15",
    "lastBookableDate": "2026-11-15",
    "items": [
      {
        "id": "101",
        "storeId": "101",
        "startAt": "2026-11-15T10:00:00.000Z",
        "endAt": "2026-11-15T11:00:00.000Z",
        "capacity": 10,
        "remaining": 0,
        "priceCent": 1000,
        "status": "OPEN",
        "bookable": false,
        "unavailableReason": null
      }
    ]
  }
}
```

**业务处理与限制**

返回尚未开始的时段；不可售时段可列出原因。date超范围422；跨日营业以时段startAt的本地日期归类，不截断跨日时段。

### 4.4 后台门店列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/stores` |
| 请求方式 | GET |
| 权限 | 后台 JWT + STORE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q019–Q029 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| status | Query | string | 否 | OPEN/CLOSED/DISABLED；OPEN/CLOSED/DISABLED |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].name | string | 否/条件 | 门店名称 |
| data.items[].address | string | 否/条件 | 地址 |
| data.items[].latitude | string / null | 否/条件 | 十进制定点坐标字符串，最多7位小数 |
| data.items[].longitude | string / null | 否/条件 | 十进制定点坐标字符串，最多7位小数 |
| data.items[].description | string / null | 否/条件 | 器械文字说明 |
| data.items[].images | array<string> | 否/条件 | 本系统上传后登记的公开图片对象键或白名单地址 |
| data.items[].openTime | string | 否/条件 | HH:mm:ss |
| data.items[].closeTime | string | 否/条件 | HH:mm:ss |
| data.items[].is24h | boolean | 否/条件 | 全天营业 |
| data.items[].endsNextDay | boolean | 否/条件 | 结束在次日 |
| data.items[].capacity | integer | 否/条件 | 默认每时段容量；最小 1；最大 9007199254740991 |
| data.items[].slotPriceCent | integer | 否/条件 | 每60分钟默认售价；最小 0；最大 9007199254740991 |
| data.items[].overtimePriceCent | integer | 否/条件 | 每分钟超时费；最小 0；最大 9007199254740991 |
| data.items[].overtimeCapCent | integer | 否/条件 | 每次连续使用超时封顶；最小 0；最大 9007199254740991 |
| data.items[].status | string | 否/条件 | 门店状态；枚举：OPEN, CLOSED, DISABLED |
| data.items[].admissionPaused | boolean | 否/条件 | 是否暂停新入场 |
| data.items[].pauseReason | string / null | 否/条件 | 暂停原因 |
| data.items[].etag | string | 否/条件 | 可写配置的内容摘要，亦随 HTTP ETag 返回 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "name": "演示门店",
        "address": "学校教学楼",
        "latitude": null,
        "longitude": null,
        "description": null,
        "images": [],
        "openTime": "08:00:00",
        "closeTime": "23:00:00",
        "is24h": false,
        "endsNextDay": false,
        "capacity": 10,
        "slotPriceCent": 1000,
        "overtimePriceCent": 100,
        "overtimeCapCent": 3000,
        "status": "OPEN",
        "admissionPaused": false,
        "pauseReason": null,
        "etag": "\"cfg-89bfa2\""
      }
    ]
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

### 4.5 后台门店详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/stores/{storeId}` |
| 请求方式 | GET |
| 权限 | 后台 JWT + STORE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q019–Q029 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| storeId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.name | string | 是 | 门店名称 |
| data.address | string | 是 | 地址 |
| data.latitude | string / null | 是 | 十进制定点坐标字符串，最多7位小数 |
| data.longitude | string / null | 是 | 十进制定点坐标字符串，最多7位小数 |
| data.description | string / null | 是 | 器械文字说明 |
| data.images | array<string> | 是 | 本系统上传后登记的公开图片对象键或白名单地址 |
| data.openTime | string | 是 | HH:mm:ss |
| data.closeTime | string | 是 | HH:mm:ss |
| data.is24h | boolean | 是 | 全天营业 |
| data.endsNextDay | boolean | 是 | 结束在次日 |
| data.capacity | integer | 是 | 默认每时段容量；最小 1；最大 9007199254740991 |
| data.slotPriceCent | integer | 是 | 每60分钟默认售价；最小 0；最大 9007199254740991 |
| data.overtimePriceCent | integer | 是 | 每分钟超时费；最小 0；最大 9007199254740991 |
| data.overtimeCapCent | integer | 是 | 每次连续使用超时封顶；最小 0；最大 9007199254740991 |
| data.status | string | 是 | 门店状态；枚举：OPEN, CLOSED, DISABLED |
| data.admissionPaused | boolean | 是 | 是否暂停新入场 |
| data.pauseReason | string / null | 是 | 暂停原因 |
| data.etag | string | 是 | 可写配置的内容摘要，亦随 HTTP ETag 返回 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "name": "演示门店",
    "address": "学校教学楼",
    "latitude": null,
    "longitude": null,
    "description": null,
    "images": [],
    "openTime": "08:00:00",
    "closeTime": "23:00:00",
    "is24h": false,
    "endsNextDay": false,
    "capacity": 10,
    "slotPriceCent": 1000,
    "overtimePriceCent": 100,
    "overtimeCapCent": 3000,
    "status": "OPEN",
    "admissionPaused": false,
    "pauseReason": null,
    "etag": "\"cfg-89bfa2\""
  }
}
```

**业务处理与限制**

返回HTTP ETag；消息状态及灯控观察结果不能通过门店更新接口伪造。

### 4.6 创建门店


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/stores` |
| 请求方式 | POST |
| 权限 | ADMIN |
| Content-Type | application/json |
| 成功/受理HTTP | 201 |
| 来源 | 新增设计 |
| 业务规则 | Q019–Q029 Q037 Q038 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| Idempotency-Key | Header | string | 是 | 同一次动作的网络重试复用；不同动作换键；8–64位字母数字、下划线或短横线；最短 8；最长 64 |
| name | Body | string | 是 | 门店名称 |
| address | Body | string | 是 | 地址 |
| latitude | Body | string / null | 否/条件 | 十进制定点坐标字符串，最多7位小数 |
| longitude | Body | string / null | 否/条件 | 十进制定点坐标字符串，最多7位小数 |
| description | Body | string / null | 否/条件 | 器械文字说明 |
| images | Body | array<string> | 否/条件 | 本系统上传后登记的公开图片对象键或白名单地址 |
| openTime | Body | string | 是 | HH:mm:ss |
| closeTime | Body | string | 是 | HH:mm:ss |
| is24h | Body | boolean | 是 | 全天营业 |
| endsNextDay | Body | boolean | 是 | 结束在次日 |
| capacity | Body | integer | 是 | 默认每时段容量；最小 1；最大 9007199254740991 |
| slotPriceCent | Body | integer | 是 | 每60分钟默认售价；最小 0；最大 9007199254740991 |
| overtimePriceCent | Body | integer | 是 | 每分钟超时费；最小 0；最大 9007199254740991 |
| overtimeCapCent | Body | integer | 是 | 每次连续使用超时封顶；最小 0；最大 9007199254740991 |
| reason | Body | string | 是 | 操作原因，非空；写入审计；最短 1；最长 500 |

请求JSON示例：

```json
{
  "name": "演示门店",
  "address": "学校教学楼",
  "openTime": "08:00:00",
  "closeTime": "23:00:00",
  "is24h": false,
  "endsNextDay": false,
  "capacity": 10,
  "slotPriceCent": 1000,
  "overtimePriceCent": 100,
  "overtimeCapCent": 3000,
  "reason": "课程演示设备故障核实"
}
```

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.name | string | 是 | 门店名称 |
| data.address | string | 是 | 地址 |
| data.latitude | string / null | 是 | 十进制定点坐标字符串，最多7位小数 |
| data.longitude | string / null | 是 | 十进制定点坐标字符串，最多7位小数 |
| data.description | string / null | 是 | 器械文字说明 |
| data.images | array<string> | 是 | 本系统上传后登记的公开图片对象键或白名单地址 |
| data.openTime | string | 是 | HH:mm:ss |
| data.closeTime | string | 是 | HH:mm:ss |
| data.is24h | boolean | 是 | 全天营业 |
| data.endsNextDay | boolean | 是 | 结束在次日 |
| data.capacity | integer | 是 | 默认每时段容量；最小 1；最大 9007199254740991 |
| data.slotPriceCent | integer | 是 | 每60分钟默认售价；最小 0；最大 9007199254740991 |
| data.overtimePriceCent | integer | 是 | 每分钟超时费；最小 0；最大 9007199254740991 |
| data.overtimeCapCent | integer | 是 | 每次连续使用超时封顶；最小 0；最大 9007199254740991 |
| data.status | string | 是 | 门店状态；枚举：OPEN, CLOSED, DISABLED |
| data.admissionPaused | boolean | 是 | 是否暂停新入场 |
| data.pauseReason | string / null | 是 | 暂停原因 |
| data.etag | string | 是 | 可写配置的内容摘要，亦随 HTTP ETag 返回 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "name": "演示门店",
    "address": "学校教学楼",
    "latitude": null,
    "longitude": null,
    "description": null,
    "images": [],
    "openTime": "08:00:00",
    "closeTime": "23:00:00",
    "is24h": false,
    "endsNextDay": false,
    "capacity": 10,
    "slotPriceCent": 1000,
    "overtimePriceCent": 100,
    "overtimeCapCent": 3000,
    "status": "CLOSED",
    "admissionPaused": false,
    "pauseReason": null,
    "etag": "\"cfg-89bfa2\""
  }
}
```

**业务处理与限制**

固定60分钟、含当天7自然日、提前及离场宽限0。全天门店endsNextDay=false；非全天跨日须closeTime<openTime；非跨日closeTime>openTime。创建为CLOSED，启用前配置设备并生成可售时段。images未传为空数组。

### 4.7 修改门店展示资料


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/stores/{storeId}` |
| 请求方式 | PATCH |
| 权限 | ADMIN |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q028 Q104 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| storeId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| Idempotency-Key | Header | string | 是 | 同一次动作的网络重试复用；不同动作换键；8–64位字母数字、下划线或短横线；最短 8；最长 64 |
| If-Match | Header | string | 是 | 缺失428，资源配置变化412；锁内再次比对；读取资源时返回的 ETag |
| name | Body | string | 否/条件 | 门店名称 |
| address | Body | string | 否/条件 | 地址 |
| latitude | Body | string / null | 否/条件 | 十进制定点坐标字符串，最多7位小数 |
| longitude | Body | string / null | 否/条件 | 十进制定点坐标字符串，最多7位小数 |
| description | Body | string / null | 否/条件 | 器械文字说明 |
| images | Body | array<string> | 否/条件 | 本系统上传后登记的公开图片对象键或白名单地址 |
| reason | Body | string | 是 | 操作原因，非空；写入审计；最短 1；最长 500 |

请求JSON示例：

```json
{
  "reason": "课程演示设备故障核实"
}
```

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.name | string | 是 | 门店名称 |
| data.address | string | 是 | 地址 |
| data.latitude | string / null | 是 | 十进制定点坐标字符串，最多7位小数 |
| data.longitude | string / null | 是 | 十进制定点坐标字符串，最多7位小数 |
| data.description | string / null | 是 | 器械文字说明 |
| data.images | array<string> | 是 | 本系统上传后登记的公开图片对象键或白名单地址 |
| data.openTime | string | 是 | HH:mm:ss |
| data.closeTime | string | 是 | HH:mm:ss |
| data.is24h | boolean | 是 | 全天营业 |
| data.endsNextDay | boolean | 是 | 结束在次日 |
| data.capacity | integer | 是 | 默认每时段容量；最小 1；最大 9007199254740991 |
| data.slotPriceCent | integer | 是 | 每60分钟默认售价；最小 0；最大 9007199254740991 |
| data.overtimePriceCent | integer | 是 | 每分钟超时费；最小 0；最大 9007199254740991 |
| data.overtimeCapCent | integer | 是 | 每次连续使用超时封顶；最小 0；最大 9007199254740991 |
| data.status | string | 是 | 门店状态；枚举：OPEN, CLOSED, DISABLED |
| data.admissionPaused | boolean | 是 | 是否暂停新入场 |
| data.pauseReason | string / null | 是 | 暂停原因 |
| data.etag | string | 是 | 可写配置的内容摘要，亦随 HTTP ETag 返回 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "name": "演示门店",
    "address": "学校教学楼",
    "latitude": null,
    "longitude": null,
    "description": null,
    "images": [],
    "openTime": "08:00:00",
    "closeTime": "23:00:00",
    "is24h": false,
    "endsNextDay": false,
    "capacity": 10,
    "slotPriceCent": 1000,
    "overtimePriceCent": 100,
    "overtimeCapCent": 3000,
    "status": "OPEN",
    "admissionPaused": false,
    "pauseReason": null,
    "etag": "\"cfg-89bfa2\""
  }
}
```

**业务处理与限制**

除reason至少一个字段；缺省保持，显式null仅清除可空字段；图片必须属于本商家，拒绝任意外链。此接口不修改时段或计费。

### 4.13 后台时段列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/stores/{storeId}/slots` |
| 请求方式 | GET |
| 权限 | 后台 JWT + STORE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q020–Q029 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| storeId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| date | Query | string | 是 | 北京时间日期；可查历史，返回该起始日期的时段；北京时间日期；可查历史，返回该起始日期的时段；格式 date |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.storeId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.date | string | 是 | 北京时间日期；格式 date |
| data.lastBookableDate | string | 是 | 业务数据字段；格式 date |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].storeId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].startAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].endAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].capacity | integer | 否/条件 | 容量；最小 1；最大 9007199254740991；格式 int64 |
| data.items[].remaining | integer | 否/条件 | 可预约余量，非实际空位；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].priceCent | integer | 否/条件 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].status | string | 否/条件 | 业务状态，枚举见本行；枚举：OPEN, CLOSED |
| data.items[].bookable | boolean | 否/条件 | 业务数据字段 |
| data.items[].unavailableReason | string / null | 否/条件 | 开始、停店或不可售等原因 |
| data.items[].etag | string | 否/条件 | 后台列表项配置摘要，供单时段If-Match；公开时段响应不包含此字段 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "storeId": "101",
    "date": "2026-11-15",
    "lastBookableDate": "2026-11-15",
    "items": [
      {
        "id": "101",
        "storeId": "101",
        "startAt": "2026-11-15T10:00:00.000Z",
        "endAt": "2026-11-15T11:00:00.000Z",
        "capacity": 10,
        "remaining": 0,
        "priceCent": 1000,
        "status": "OPEN",
        "bookable": false,
        "unavailableReason": null
      }
    ]
  }
}
```

**业务处理与限制**

与公开列表不同：包括已开始及关闭时段；复用SlotList字段，GET不触发生成。

## 5 顾客、黑名单与信用

### 5.3 本人信用流水


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/merchants/{merchantId}/credit-events` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q075–Q078 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| merchantId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].orderId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].scoreDelta | integer | 否/条件 | 扣分为负，冲正为正；最小 -9007199254740991；最大 9007199254740991 |
| data.items[].reversalOfId | string / null | 否/条件 | 原信用事件；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].reason | string | 否/条件 | 扣分或冲正原因 |
| data.items[].occurredAt | string | 否/条件 | 原事实发生时间；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "orderId": "101",
        "scoreDelta": -10,
        "reversalOfId": null,
        "reason": "最终确认爽约",
        "occurredAt": "2026-11-15T10:00:00.000Z"
      }
    ]
  }
}
```

**业务处理与限制**

仅本人的该商家信用；按occurredAt、id倒序，冲正不删除原记录。

### 5.6 某顾客在本商家的订单


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/customers/{userId}/orders` |
| 请求方式 | GET |
| 权限 | ADMIN 或 ORDER_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q013 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| userId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].orderNo | string | 否/条件 | 服务端订单号 |
| data.items[].merchantId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].storeId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].storeName | string | 否/条件 | 门店名称 |
| data.items[].slotId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].startAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].endAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].benefitType | string | 否/条件 | 预约权益；枚举：TIME_FEE, COUNT_CARD, MONTH_CARD |
| data.items[].baseAmountCent | integer | 否/条件 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].payDeadline | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].status | string | 否/条件 | 业务状态，枚举见本行；枚举：PENDING_PAYMENT, RESERVED, IN_USE, FINISHED, CANCEL_PENDING, CANCELLED, NO_SHOW_PENDING, NO_SHOW |
| data.items[].capacityHeld | boolean | 否/条件 | 此订单是否仍持有原时段承诺名额 |
| data.items[].confirmedAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].cancelStage | string | 否/条件 | 取消处理阶段；枚举：NONE, REVOKE_PENDING, RETURN_PENDING, COMPLETED |
| data.items[].cancelRequestedAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].cancelledAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].cancelReason | string / null | 否/条件 | 取消原因 |
| data.items[].reviewRequired | boolean | 否/条件 | 是否需要人工复核 |
| data.items[].ruleSnapshot | object | 否/条件 | 下单时冻结的营业/计费规则 |
| data.items[].ruleSnapshot.version | string | 否/条件 | 业务数据字段；枚举：v1.0 |
| data.items[].ruleSnapshot.slotMinutes | integer | 否/条件 | 固定60分钟；枚举：60 |
| data.items[].ruleSnapshot.entryGraceMinutes | integer | 否/条件 | 固定0，不支持提前入场；枚举：0 |
| data.items[].ruleSnapshot.exitGraceMinutes | integer | 否/条件 | 固定0，无免费延迟；枚举：0 |
| data.items[].ruleSnapshot.overtimePriceCent | integer | 否/条件 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].ruleSnapshot.overtimeCapCent | integer | 否/条件 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].ruleSnapshot.rounding | string | 否/条件 | 业务数据字段；枚举：CEIL_MINUTE |
| data.items[].ruleSnapshot.timezone | string | 否/条件 | 业务时区；枚举：Asia/Shanghai |
| data.items[].ruleSnapshot.merchantName | string | 否/条件 | 商家名称 |
| data.items[].ruleSnapshot.storeName | string | 否/条件 | 门店名称 |
| data.items[].paymentMode | string | 否/条件 | 模拟支付标识；枚举：MOCK |
| data.items[].memberCardId | string / null | 否/条件 | 仅卡订单非空；完整契约新增字段；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.page | integer | 是 | 页码；最小 1；最大 9007199254740991；格式 int64 |
| data.pageSize | integer | 是 | 页大小；最小 1；最大 9007199254740991；格式 int64 |
| data.total | integer | 是 | 总数；最小 0；最大 9007199254740991；格式 int64 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "items": [
      {
        "id": "101",
        "orderNo": "O202611150001",
        "merchantId": "101",
        "storeId": "101",
        "storeName": "演示门店",
        "slotId": "101",
        "startAt": "2026-11-15T10:00:00.000Z",
        "endAt": "2026-11-15T11:00:00.000Z",
        "benefitType": "TIME_FEE",
        "baseAmountCent": 1000,
        "payDeadline": "2026-11-15T09:55:00.000Z",
        "status": "PENDING_PAYMENT",
        "capacityHeld": true,
        "confirmedAt": null,
        "cancelStage": "NONE",
        "cancelRequestedAt": null,
        "cancelledAt": null,
        "cancelReason": null,
        "reviewRequired": false,
        "ruleSnapshot": {
          "version": "v1.0",
          "slotMinutes": 60,
          "entryGraceMinutes": 0,
          "exitGraceMinutes": 0,
          "overtimePriceCent": 100,
          "overtimeCapCent": 3000,
          "rounding": "CEIL_MINUTE",
          "timezone": "Asia/Shanghai",
          "merchantName": "演示商家",
          "storeName": "演示门店"
        },
        "paymentMode": "MOCK",
        "memberCardId": null
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

### 5.7 某顾客在本商家的到店记录


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/customers/{userId}/visits` |
| 请求方式 | GET |
| 权限 | ADMIN 或 VISIT_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q013 Q102 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| userId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].visitNo | string | 否/条件 | 到店业务号 |
| data.items[].merchantId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].storeId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].userId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].status | string | 否/条件 | 业务状态，枚举见本行；枚举：IN_STORE, LEFT |
| data.items[].entryAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].exitAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].entrySource | string | 否/条件 | 入场来源DEVICE/SELF_CONFIRM/MERCHANT_REVIEW；枚举：DEVICE, SELF_CONFIRM, MERCHANT_REVIEW |
| data.items[].exitSource | string / null | 否/条件 | 离场确认来源；枚举：DEVICE, SELF_CONFIRM, MERCHANT_REVIEW |
| data.items[].authorizedEndAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].lastOrderId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].usedOrderIds | array<string> | 否/条件 | 本次到店已实际使用的订单，不提前含未来段 |
| data.items[].reviewRequired | boolean | 否/条件 | 是否需要人工复核 |
| data.page | integer | 是 | 页码；最小 1；最大 9007199254740991；格式 int64 |
| data.pageSize | integer | 是 | 页大小；最小 1；最大 9007199254740991；格式 int64 |
| data.total | integer | 是 | 总数；最小 0；最大 9007199254740991；格式 int64 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "items": [
      {
        "id": "101",
        "visitNo": "V202611150001",
        "merchantId": "101",
        "storeId": "101",
        "userId": "101",
        "status": "IN_STORE",
        "entryAt": "2026-11-15T10:10:00.000Z",
        "exitAt": null,
        "entrySource": "SELF_CONFIRM",
        "exitSource": null,
        "authorizedEndAt": "2026-11-15T11:00:00.000Z",
        "lastOrderId": "101",
        "usedOrderIds": [
          "101"
        ],
        "reviewRequired": false
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

### 5.9 商家顾客信用流水


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/customers/{userId}/credit-events` |
| 请求方式 | GET |
| 权限 | ADMIN 或 CUSTOMER_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q077 Q078 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| userId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].orderId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].scoreDelta | integer | 否/条件 | 扣分为负，冲正为正；最小 -9007199254740991；最大 9007199254740991 |
| data.items[].reversalOfId | string / null | 否/条件 | 原信用事件；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].reason | string | 否/条件 | 扣分或冲正原因 |
| data.items[].occurredAt | string | 否/条件 | 原事实发生时间；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "orderId": "101",
        "scoreDelta": -10,
        "reversalOfId": null,
        "reason": "最终确认爽约",
        "occurredAt": "2026-11-15T10:00:00.000Z"
      }
    ]
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

## 6 预约订单与支付退款

### 6.2 本人跨商家订单


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/orders` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 沿用首闭环 v1.1 |
| 业务规则 | Q002 Q013 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 当前页；最小 1；默认 1 |
| pageSize | Query | integer | 否 | 每页条数；最小 1；最大 100；默认 20 |
| status | Query | string | 否 | 业务状态，枚举见本行；枚举：PENDING_PAYMENT, RESERVED, IN_USE, FINISHED, CANCEL_PENDING, CANCELLED, NO_SHOW_PENDING, NO_SHOW |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data | object | 是 | 业务数据 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].orderNo | string | 否/条件 | 服务端订单号 |
| data.items[].merchantId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].storeId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].storeName | string | 否/条件 | 门店名称 |
| data.items[].slotId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].startAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].endAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].benefitType | string | 否/条件 | 预约权益；枚举：TIME_FEE, COUNT_CARD, MONTH_CARD |
| data.items[].baseAmountCent | integer | 否/条件 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].payDeadline | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].status | string | 否/条件 | 业务状态，枚举见本行；枚举：PENDING_PAYMENT, RESERVED, IN_USE, FINISHED, CANCEL_PENDING, CANCELLED, NO_SHOW_PENDING, NO_SHOW |
| data.items[].capacityHeld | boolean | 否/条件 | 此订单是否仍持有原时段承诺名额 |
| data.items[].confirmedAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].cancelStage | string | 否/条件 | 取消处理阶段；枚举：NONE, REVOKE_PENDING, RETURN_PENDING, COMPLETED |
| data.items[].cancelRequestedAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].cancelledAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].cancelReason | string / null | 否/条件 | 取消原因 |
| data.items[].reviewRequired | boolean | 否/条件 | 是否需要人工复核 |
| data.items[].ruleSnapshot | object | 否/条件 | 下单时冻结的营业/计费规则 |
| data.items[].ruleSnapshot.version | string | 否/条件 | 业务数据字段；枚举：v1.0 |
| data.items[].ruleSnapshot.slotMinutes | integer | 否/条件 | 固定60分钟；枚举：60 |
| data.items[].ruleSnapshot.entryGraceMinutes | integer | 否/条件 | 固定0，不支持提前入场；枚举：0 |
| data.items[].ruleSnapshot.exitGraceMinutes | integer | 否/条件 | 固定0，无免费延迟；枚举：0 |
| data.items[].ruleSnapshot.overtimePriceCent | integer | 否/条件 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].ruleSnapshot.overtimeCapCent | integer | 否/条件 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].ruleSnapshot.rounding | string | 否/条件 | 业务数据字段；枚举：CEIL_MINUTE |
| data.items[].ruleSnapshot.timezone | string | 否/条件 | 业务时区；枚举：Asia/Shanghai |
| data.items[].ruleSnapshot.merchantName | string | 否/条件 | 商家名称 |
| data.items[].ruleSnapshot.storeName | string | 否/条件 | 门店名称 |
| data.items[].paymentMode | string | 否/条件 | 模拟支付标识；枚举：MOCK |
| data.items[].memberCardId | string / null | 否/条件 | 仅卡订单非空；完整契约新增字段；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.page | integer | 是 | 页码；最小 1；最大 9007199254740991；格式 int64 |
| data.pageSize | integer | 是 | 页大小；最小 1；最大 9007199254740991；格式 int64 |
| data.total | integer | 是 | 总数；最小 0；最大 9007199254740991；格式 int64 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "demo-value",
  "requestId": "101",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "items": [
      {
        "id": "101",
        "orderNo": "O202611150001",
        "merchantId": "101",
        "storeId": "101",
        "storeName": "演示门店",
        "slotId": "101",
        "startAt": "2026-11-15T10:00:00.000Z",
        "endAt": "2026-11-15T11:00:00.000Z",
        "benefitType": "TIME_FEE",
        "baseAmountCent": 1000,
        "payDeadline": "2026-11-15T09:55:00.000Z",
        "status": "PENDING_PAYMENT",
        "capacityHeld": true,
        "confirmedAt": null,
        "cancelStage": "NONE",
        "cancelRequestedAt": null,
        "cancelledAt": null,
        "cancelReason": null,
        "reviewRequired": false,
        "ruleSnapshot": {
          "version": "v1.0",
          "slotMinutes": 60,
          "entryGraceMinutes": 0,
          "exitGraceMinutes": 0,
          "overtimePriceCent": 100,
          "overtimeCapCent": 3000,
          "rounding": "CEIL_MINUTE",
          "timezone": "Asia/Shanghai",
          "merchantName": "演示商家",
          "storeName": "演示门店"
        },
        "paymentMode": "MOCK",
        "memberCardId": null
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1
  }
}
```

**业务处理与限制**

只查当前顾客；按created_at、id倒序。

### 6.3 本人订单详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/orders/{orderId}` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 沿用首闭环 v1.1 |
| 业务规则 | Q013 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| orderId | Path | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.orderNo | string | 是 | 服务端订单号 |
| data.merchantId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.storeId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.storeName | string | 是 | 门店名称 |
| data.slotId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.startAt | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.endAt | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.benefitType | string | 是 | 预约权益；枚举：TIME_FEE, COUNT_CARD, MONTH_CARD |
| data.baseAmountCent | integer | 是 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.payDeadline | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.status | string | 是 | 业务状态，枚举见本行；枚举：PENDING_PAYMENT, RESERVED, IN_USE, FINISHED, CANCEL_PENDING, CANCELLED, NO_SHOW_PENDING, NO_SHOW |
| data.capacityHeld | boolean | 是 | 此订单是否仍持有原时段承诺名额 |
| data.confirmedAt | string / null | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.cancelStage | string | 是 | 取消处理阶段；枚举：NONE, REVOKE_PENDING, RETURN_PENDING, COMPLETED |
| data.cancelRequestedAt | string / null | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.cancelledAt | string / null | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.cancelReason | string / null | 是 | 取消原因 |
| data.reviewRequired | boolean | 是 | 是否需要人工复核 |
| data.ruleSnapshot | object | 是 | 下单时冻结的营业/计费规则 |
| data.ruleSnapshot.version | string | 是 | 业务数据字段；枚举：v1.0 |
| data.ruleSnapshot.slotMinutes | integer | 是 | 固定60分钟；枚举：60 |
| data.ruleSnapshot.entryGraceMinutes | integer | 是 | 固定0，不支持提前入场；枚举：0 |
| data.ruleSnapshot.exitGraceMinutes | integer | 是 | 固定0，无免费延迟；枚举：0 |
| data.ruleSnapshot.overtimePriceCent | integer | 是 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.ruleSnapshot.overtimeCapCent | integer | 是 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.ruleSnapshot.rounding | string | 是 | 业务数据字段；枚举：CEIL_MINUTE |
| data.ruleSnapshot.timezone | string | 是 | 业务时区；枚举：Asia/Shanghai |
| data.ruleSnapshot.merchantName | string | 是 | 商家名称 |
| data.ruleSnapshot.storeName | string | 是 | 门店名称 |
| data.paymentMode | string | 是 | 模拟支付标识；枚举：MOCK |
| data.memberCardId | string / null | 是 | 仅卡订单非空；完整契约新增字段；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "demo-value",
  "requestId": "101",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "orderNo": "O202611150001",
    "merchantId": "101",
    "storeId": "101",
    "storeName": "演示门店",
    "slotId": "101",
    "startAt": "2026-11-15T10:00:00.000Z",
    "endAt": "2026-11-15T11:00:00.000Z",
    "benefitType": "TIME_FEE",
    "baseAmountCent": 1000,
    "payDeadline": "2026-11-15T09:55:00.000Z",
    "status": "PENDING_PAYMENT",
    "capacityHeld": true,
    "confirmedAt": null,
    "cancelStage": "NONE",
    "cancelRequestedAt": null,
    "cancelledAt": null,
    "cancelReason": null,
    "reviewRequired": false,
    "ruleSnapshot": {
      "version": "v1.0",
      "slotMinutes": 60,
      "entryGraceMinutes": 0,
      "exitGraceMinutes": 0,
      "overtimePriceCent": 100,
      "overtimeCapCent": 3000,
      "rounding": "CEIL_MINUTE",
      "timezone": "Asia/Shanghai",
      "merchantName": "演示商家",
      "storeName": "演示门店"
    },
    "paymentMode": "MOCK",
    "memberCardId": null
  }
}
```

**业务处理与限制**

他人订单统一404，不泄露存在性。

### 6.6 本商家订单列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/orders` |
| 请求方式 | GET |
| 权限 | 本商家后台 JWT；ADMIN 或 ORDER_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 沿用首闭环 v1.1 |
| 业务规则 | Q013 Q014 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 当前页；最小 1；默认 1 |
| pageSize | Query | integer | 否 | 每页条数；最小 1；最大 100；默认 20 |
| storeId | Query | string | 否 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| status | Query | string | 否 | 业务状态，枚举见本行；枚举：PENDING_PAYMENT, RESERVED, IN_USE, FINISHED, CANCEL_PENDING, CANCELLED, NO_SHOW_PENDING, NO_SHOW |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data | object | 是 | 业务数据 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].orderNo | string | 否/条件 | 服务端订单号 |
| data.items[].merchantId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].storeId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].storeName | string | 否/条件 | 门店名称 |
| data.items[].slotId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].startAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].endAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].benefitType | string | 否/条件 | 预约权益；枚举：TIME_FEE, COUNT_CARD, MONTH_CARD |
| data.items[].baseAmountCent | integer | 否/条件 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].payDeadline | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].status | string | 否/条件 | 业务状态，枚举见本行；枚举：PENDING_PAYMENT, RESERVED, IN_USE, FINISHED, CANCEL_PENDING, CANCELLED, NO_SHOW_PENDING, NO_SHOW |
| data.items[].capacityHeld | boolean | 否/条件 | 此订单是否仍持有原时段承诺名额 |
| data.items[].confirmedAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].cancelStage | string | 否/条件 | 取消处理阶段；枚举：NONE, REVOKE_PENDING, RETURN_PENDING, COMPLETED |
| data.items[].cancelRequestedAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].cancelledAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].cancelReason | string / null | 否/条件 | 取消原因 |
| data.items[].reviewRequired | boolean | 否/条件 | 是否需要人工复核 |
| data.items[].ruleSnapshot | object | 否/条件 | 下单时冻结的营业/计费规则 |
| data.items[].ruleSnapshot.version | string | 否/条件 | 业务数据字段；枚举：v1.0 |
| data.items[].ruleSnapshot.slotMinutes | integer | 否/条件 | 固定60分钟；枚举：60 |
| data.items[].ruleSnapshot.entryGraceMinutes | integer | 否/条件 | 固定0，不支持提前入场；枚举：0 |
| data.items[].ruleSnapshot.exitGraceMinutes | integer | 否/条件 | 固定0，无免费延迟；枚举：0 |
| data.items[].ruleSnapshot.overtimePriceCent | integer | 否/条件 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].ruleSnapshot.overtimeCapCent | integer | 否/条件 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.items[].ruleSnapshot.rounding | string | 否/条件 | 业务数据字段；枚举：CEIL_MINUTE |
| data.items[].ruleSnapshot.timezone | string | 否/条件 | 业务时区；枚举：Asia/Shanghai |
| data.items[].ruleSnapshot.merchantName | string | 否/条件 | 商家名称 |
| data.items[].ruleSnapshot.storeName | string | 否/条件 | 门店名称 |
| data.items[].paymentMode | string | 否/条件 | 模拟支付标识；枚举：MOCK |
| data.items[].memberCardId | string / null | 否/条件 | 仅卡订单非空；完整契约新增字段；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.page | integer | 是 | 页码；最小 1；最大 9007199254740991；格式 int64 |
| data.pageSize | integer | 是 | 页大小；最小 1；最大 9007199254740991；格式 int64 |
| data.total | integer | 是 | 总数；最小 0；最大 9007199254740991；格式 int64 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "demo-value",
  "requestId": "101",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "items": [
      {
        "id": "101",
        "orderNo": "O202611150001",
        "merchantId": "101",
        "storeId": "101",
        "storeName": "演示门店",
        "slotId": "101",
        "startAt": "2026-11-15T10:00:00.000Z",
        "endAt": "2026-11-15T11:00:00.000Z",
        "benefitType": "TIME_FEE",
        "baseAmountCent": 1000,
        "payDeadline": "2026-11-15T09:55:00.000Z",
        "status": "PENDING_PAYMENT",
        "capacityHeld": true,
        "confirmedAt": null,
        "cancelStage": "NONE",
        "cancelRequestedAt": null,
        "cancelledAt": null,
        "cancelReason": null,
        "reviewRequired": false,
        "ruleSnapshot": {
          "version": "v1.0",
          "slotMinutes": 60,
          "entryGraceMinutes": 0,
          "exitGraceMinutes": 0,
          "overtimePriceCent": 100,
          "overtimeCapCent": 3000,
          "rounding": "CEIL_MINUTE",
          "timezone": "Asia/Shanghai",
          "merchantName": "演示商家",
          "storeName": "演示门店"
        },
        "paymentMode": "MOCK",
        "memberCardId": null
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1
  }
}
```

**业务处理与限制**

merchantId来自后台登录，storeId须属于本商家；需要ORDER_READ权限或ADMIN。

### 6.7 本商家订单详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/orders/{orderId}` |
| 请求方式 | GET |
| 权限 | 本商家后台 JWT；ADMIN 或 ORDER_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 沿用首闭环 v1.1 |
| 业务规则 | Q013 Q014 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| orderId | Path | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.orderNo | string | 是 | 服务端订单号 |
| data.merchantId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.storeId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.storeName | string | 是 | 门店名称 |
| data.slotId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.startAt | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.endAt | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.benefitType | string | 是 | 预约权益；枚举：TIME_FEE, COUNT_CARD, MONTH_CARD |
| data.baseAmountCent | integer | 是 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.payDeadline | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.status | string | 是 | 业务状态，枚举见本行；枚举：PENDING_PAYMENT, RESERVED, IN_USE, FINISHED, CANCEL_PENDING, CANCELLED, NO_SHOW_PENDING, NO_SHOW |
| data.capacityHeld | boolean | 是 | 此订单是否仍持有原时段承诺名额 |
| data.confirmedAt | string / null | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.cancelStage | string | 是 | 取消处理阶段；枚举：NONE, REVOKE_PENDING, RETURN_PENDING, COMPLETED |
| data.cancelRequestedAt | string / null | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.cancelledAt | string / null | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.cancelReason | string / null | 是 | 取消原因 |
| data.reviewRequired | boolean | 是 | 是否需要人工复核 |
| data.ruleSnapshot | object | 是 | 下单时冻结的营业/计费规则 |
| data.ruleSnapshot.version | string | 是 | 业务数据字段；枚举：v1.0 |
| data.ruleSnapshot.slotMinutes | integer | 是 | 固定60分钟；枚举：60 |
| data.ruleSnapshot.entryGraceMinutes | integer | 是 | 固定0，不支持提前入场；枚举：0 |
| data.ruleSnapshot.exitGraceMinutes | integer | 是 | 固定0，无免费延迟；枚举：0 |
| data.ruleSnapshot.overtimePriceCent | integer | 是 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.ruleSnapshot.overtimeCapCent | integer | 是 | 金额，单位分；最小 0；最大 9007199254740991；格式 int64 |
| data.ruleSnapshot.rounding | string | 是 | 业务数据字段；枚举：CEIL_MINUTE |
| data.ruleSnapshot.timezone | string | 是 | 业务时区；枚举：Asia/Shanghai |
| data.ruleSnapshot.merchantName | string | 是 | 商家名称 |
| data.ruleSnapshot.storeName | string | 是 | 门店名称 |
| data.paymentMode | string | 是 | 模拟支付标识；枚举：MOCK |
| data.memberCardId | string / null | 是 | 仅卡订单非空；完整契约新增字段；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "demo-value",
  "requestId": "101",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "orderNo": "O202611150001",
    "merchantId": "101",
    "storeId": "101",
    "storeName": "演示门店",
    "slotId": "101",
    "startAt": "2026-11-15T10:00:00.000Z",
    "endAt": "2026-11-15T11:00:00.000Z",
    "benefitType": "TIME_FEE",
    "baseAmountCent": 1000,
    "payDeadline": "2026-11-15T09:55:00.000Z",
    "status": "PENDING_PAYMENT",
    "capacityHeld": true,
    "confirmedAt": null,
    "cancelStage": "NONE",
    "cancelRequestedAt": null,
    "cancelledAt": null,
    "cancelReason": null,
    "reviewRequired": false,
    "ruleSnapshot": {
      "version": "v1.0",
      "slotMinutes": 60,
      "entryGraceMinutes": 0,
      "exitGraceMinutes": 0,
      "overtimePriceCent": 100,
      "overtimeCapCent": 3000,
      "rounding": "CEIL_MINUTE",
      "timezone": "Asia/Shanghai",
      "merchantName": "演示商家",
      "storeName": "演示门店"
    },
    "paymentMode": "MOCK",
    "memberCardId": null
  }
}
```

**业务处理与限制**

需要ORDER_READ或ADMIN，跨商家404。

## 7 卡产品、持有卡与权益

### 7.1 公开卡产品列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/merchants/{merchantId}/card-products` |
| 请求方式 | GET |
| 权限 | 公开 |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q079–Q084 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| merchantId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].merchantId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].name | string | 否/条件 | 产品名称 |
| data.items[].cardType | string | 否/条件 | 卡类型；枚举：COUNT_CARD, MONTH_CARD |
| data.items[].totalCount | integer / null | 否/条件 | 次卡次数；月卡 null；最小 1；最大 9007199254740991 |
| data.items[].priceCent | integer | 否/条件 | 模拟购买价；最小 0；最大 9007199254740991 |
| data.items[].validDays | integer | 否/条件 | 固定策略：次卡180天，月卡30天；枚举：180, 30；最小 0；最大 9007199254740991 |
| data.items[].status | string | 否/条件 | 售卖状态；枚举：ON_SALE, OFF_SALE |
| data.items[].etag | string | 否/条件 | 配置摘要；与 HTTP ETag 一致 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "merchantId": "101",
        "name": "10次卡",
        "cardType": "COUNT_CARD",
        "totalCount": null,
        "priceCent": 8000,
        "validDays": 180,
        "status": "ON_SALE",
        "etag": "\"cfg-card-01\""
      }
    ]
  }
}
```

**业务处理与限制**

仅启用商家ON_SALE产品；不返回运营备注。validDays由类型固定推导，不能客户端选择。

### 7.2 公开卡产品详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/merchants/{merchantId}/card-products/{productId}` |
| 请求方式 | GET |
| 权限 | 公开 |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q079–Q084 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| productId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| merchantId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.merchantId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.name | string | 是 | 产品名称 |
| data.cardType | string | 是 | 卡类型；枚举：COUNT_CARD, MONTH_CARD |
| data.totalCount | integer / null | 是 | 次卡次数；月卡 null；最小 1；最大 9007199254740991 |
| data.priceCent | integer | 是 | 模拟购买价；最小 0；最大 9007199254740991 |
| data.validDays | integer | 是 | 固定策略：次卡180天，月卡30天；枚举：180, 30；最小 0；最大 9007199254740991 |
| data.status | string | 是 | 售卖状态；枚举：ON_SALE, OFF_SALE |
| data.etag | string | 是 | 配置摘要；与 HTTP ETag 一致 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "merchantId": "101",
    "name": "10次卡",
    "cardType": "COUNT_CARD",
    "totalCount": null,
    "priceCent": 8000,
    "validDays": 180,
    "status": "ON_SALE",
    "etag": "\"cfg-card-01\""
  }
}
```

**业务处理与限制**

必须属于路径商家且在售；显示同商家门店通用、有效期和预约占容量规则。

### 7.4 本人持有卡列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/member-cards` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q079–Q084 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| merchantId | Query | string | 否 | 本人的该商家持有卡；本人的该商家持有卡；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| status | Query | string | 否 | 卡生命周期；过期用isExpired计算；卡生命周期；过期用isExpired计算 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].merchantId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].userId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].productId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].purchaseNo | string | 否/条件 | 购卡业务号 |
| data.items[].cardType | string | 否/条件 | 卡类型；枚举：COUNT_CARD, MONTH_CARD |
| data.items[].productSnapshot | object | 否/条件 | 业务数据字段 |
| data.items[].productSnapshot.name | string | 否/条件 | 购卡时名称 |
| data.items[].productSnapshot.cardType | string | 否/条件 | 购卡时类型；枚举：COUNT_CARD, MONTH_CARD |
| data.items[].productSnapshot.priceCent | integer | 否/条件 | 购卡时单价；最小 0；最大 9007199254740991 |
| data.items[].productSnapshot.totalCount | integer / null | 否/条件 | 月卡 null；最小 1；最大 9007199254740991 |
| data.items[].productSnapshot.validDays | integer | 否/条件 | 固定有效天数；最小 0；最大 9007199254740991 |
| data.items[].productSnapshot.ruleVersion | string | 否/条件 | 已售规则版本 |
| data.items[].purchaseAmountCent | integer | 否/条件 | 购买金额；最小 0；最大 9007199254740991 |
| data.items[].totalCount | integer / null | 否/条件 | 月卡 null；最小 1；最大 9007199254740991 |
| data.items[].remainingCount | integer / null | 否/条件 | 月卡 null；最小 0；最大 9007199254740991 |
| data.items[].validFrom | string / null | 否/条件 | 确认购买后生效；格式 date-time |
| data.items[].validTo | string / null | 否/条件 | 有效截止，不包含；格式 date-time |
| data.items[].status | string | 否/条件 | 卡生命周期；枚举：PENDING_PAYMENT, ACTIVE, FROZEN, REFUND_PENDING, REFUNDED, CANCELLED |
| data.items[].isExpired | boolean | 否/条件 | 到期是计算属性，不新增 EXPIRED 数据库存储状态 |
| data.items[].freezeReason | string / null | 否/条件 | 业务数据字段 |
| data.items[].refundRequestedAt | string / null | 否/条件 | 时间点，ISO 8601带时区；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "merchantId": "101",
        "userId": "101",
        "productId": "101",
        "purchaseNo": "CARD202611150001",
        "cardType": "COUNT_CARD",
        "productSnapshot": {
          "name": "10次卡",
          "cardType": "COUNT_CARD",
          "priceCent": 8000,
          "totalCount": null,
          "validDays": 180,
          "ruleVersion": "1"
        },
        "purchaseAmountCent": 8000,
        "totalCount": null,
        "remainingCount": null,
        "validFrom": null,
        "validTo": null,
        "status": "PENDING_PAYMENT",
        "isExpired": false,
        "freezeReason": null,
        "refundRequestedAt": null
      }
    ]
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

### 7.5 本人持有卡详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/member-cards/{cardId}` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q079–Q084 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| cardId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.merchantId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.userId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.productId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.purchaseNo | string | 是 | 购卡业务号 |
| data.cardType | string | 是 | 卡类型；枚举：COUNT_CARD, MONTH_CARD |
| data.productSnapshot | object | 是 | 业务数据字段 |
| data.productSnapshot.name | string | 是 | 购卡时名称 |
| data.productSnapshot.cardType | string | 是 | 购卡时类型；枚举：COUNT_CARD, MONTH_CARD |
| data.productSnapshot.priceCent | integer | 是 | 购卡时单价；最小 0；最大 9007199254740991 |
| data.productSnapshot.totalCount | integer / null | 是 | 月卡 null；最小 1；最大 9007199254740991 |
| data.productSnapshot.validDays | integer | 是 | 固定有效天数；最小 0；最大 9007199254740991 |
| data.productSnapshot.ruleVersion | string | 是 | 已售规则版本 |
| data.purchaseAmountCent | integer | 是 | 购买金额；最小 0；最大 9007199254740991 |
| data.totalCount | integer / null | 是 | 月卡 null；最小 1；最大 9007199254740991 |
| data.remainingCount | integer / null | 是 | 月卡 null；最小 0；最大 9007199254740991 |
| data.validFrom | string / null | 是 | 确认购买后生效；格式 date-time |
| data.validTo | string / null | 是 | 有效截止，不包含；格式 date-time |
| data.status | string | 是 | 卡生命周期；枚举：PENDING_PAYMENT, ACTIVE, FROZEN, REFUND_PENDING, REFUNDED, CANCELLED |
| data.isExpired | boolean | 是 | 到期是计算属性，不新增 EXPIRED 数据库存储状态 |
| data.freezeReason | string / null | 是 | 业务数据字段 |
| data.refundRequestedAt | string / null | 是 | 时间点，ISO 8601带时区；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "merchantId": "101",
    "userId": "101",
    "productId": "101",
    "purchaseNo": "CARD202611150001",
    "cardType": "COUNT_CARD",
    "productSnapshot": {
      "name": "10次卡",
      "cardType": "COUNT_CARD",
      "priceCent": 8000,
      "totalCount": null,
      "validDays": 180,
      "ruleVersion": "1"
    },
    "purchaseAmountCent": 8000,
    "totalCount": null,
    "remainingCount": null,
    "validFrom": null,
    "validTo": null,
    "status": "PENDING_PAYMENT",
    "isExpired": false,
    "freezeReason": null,
    "refundRequestedAt": null
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

### 7.11 本人卡权益流水


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/member-cards/{cardId}/ledger` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q080–Q084 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| cardId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].memberCardId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].orderId | string / null | 否/条件 | 人工补偿可空；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].kind | string | 否/条件 | 权益类型；枚举：DEBIT, RETURN, COVER, RELEASE, ADJUST |
| data.items[].countDelta | integer | 否/条件 | 扣次-1、返次+1，月卡覆盖/解除0；最小 -9007199254740991；最大 9007199254740991 |
| data.items[].reversalOfId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].reason | string | 否/条件 | 依据 |
| data.items[].createdAt | string | 否/条件 | 创建时间；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "memberCardId": "101",
        "orderId": null,
        "kind": "DEBIT",
        "countDelta": -1,
        "reversalOfId": null,
        "reason": "预约确认扣次",
        "createdAt": "2026-11-15T10:00:00.000Z"
      }
    ]
  }
}
```

**业务处理与限制**

按createdAt、id倒序；超时补缴不混入权益流水。

### 7.12 后台卡产品列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/card-products` |
| 请求方式 | GET |
| 权限 | ADMIN 或 CARD_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q079–Q084 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].merchantId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].name | string | 否/条件 | 产品名称 |
| data.items[].cardType | string | 否/条件 | 卡类型；枚举：COUNT_CARD, MONTH_CARD |
| data.items[].totalCount | integer / null | 否/条件 | 次卡次数；月卡 null；最小 1；最大 9007199254740991 |
| data.items[].priceCent | integer | 否/条件 | 模拟购买价；最小 0；最大 9007199254740991 |
| data.items[].validDays | integer | 否/条件 | 固定策略：次卡180天，月卡30天；枚举：180, 30；最小 0；最大 9007199254740991 |
| data.items[].status | string | 否/条件 | 售卖状态；枚举：ON_SALE, OFF_SALE |
| data.items[].etag | string | 否/条件 | 配置摘要；与 HTTP ETag 一致 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "merchantId": "101",
        "name": "10次卡",
        "cardType": "COUNT_CARD",
        "totalCount": null,
        "priceCent": 8000,
        "validDays": 180,
        "status": "ON_SALE",
        "etag": "\"cfg-card-01\""
      }
    ]
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

### 7.13 后台卡产品详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/card-products/{productId}` |
| 请求方式 | GET |
| 权限 | ADMIN 或 CARD_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q079–Q084 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| productId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.merchantId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.name | string | 是 | 产品名称 |
| data.cardType | string | 是 | 卡类型；枚举：COUNT_CARD, MONTH_CARD |
| data.totalCount | integer / null | 是 | 次卡次数；月卡 null；最小 1；最大 9007199254740991 |
| data.priceCent | integer | 是 | 模拟购买价；最小 0；最大 9007199254740991 |
| data.validDays | integer | 是 | 固定策略：次卡180天，月卡30天；枚举：180, 30；最小 0；最大 9007199254740991 |
| data.status | string | 是 | 售卖状态；枚举：ON_SALE, OFF_SALE |
| data.etag | string | 是 | 配置摘要；与 HTTP ETag 一致 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "merchantId": "101",
    "name": "10次卡",
    "cardType": "COUNT_CARD",
    "totalCount": null,
    "priceCent": 8000,
    "validDays": 180,
    "status": "ON_SALE",
    "etag": "\"cfg-card-01\""
  }
}
```

**业务处理与限制**

返回HTTP ETag。

### 7.14 创建卡产品


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/card-products` |
| 请求方式 | POST |
| 权限 | ADMIN |
| Content-Type | application/json |
| 成功/受理HTTP | 201 |
| 来源 | 新增设计 |
| 业务规则 | Q079–Q084 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| Idempotency-Key | Header | string | 是 | 同一次动作的网络重试复用；不同动作换键；8–64位字母数字、下划线或短横线；最短 8；最长 64 |
| name | Body | string | 是 | 名称，1–100字符；最短 1；最长 100 |
| cardType | Body | string | 是 | 次卡/月卡；枚举：COUNT_CARD, MONTH_CARD |
| totalCount | Body | integer / null | 是 | 次卡正数，月卡必须null；最小 1；最大 9007199254740991 |
| priceCent | Body | integer | 是 | 售价；最小 0；最大 9007199254740991 |
| reason | Body | string | 是 | 操作原因，非空；写入审计；最短 1；最长 500 |

请求JSON示例：

```json
{
  "name": "10次卡",
  "cardType": "COUNT_CARD",
  "totalCount": null,
  "priceCent": 8000,
  "reason": "课程演示设备故障核实"
}
```

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.merchantId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.name | string | 是 | 产品名称 |
| data.cardType | string | 是 | 卡类型；枚举：COUNT_CARD, MONTH_CARD |
| data.totalCount | integer / null | 是 | 次卡次数；月卡 null；最小 1；最大 9007199254740991 |
| data.priceCent | integer | 是 | 模拟购买价；最小 0；最大 9007199254740991 |
| data.validDays | integer | 是 | 固定策略：次卡180天，月卡30天；枚举：180, 30；最小 0；最大 9007199254740991 |
| data.status | string | 是 | 售卖状态；枚举：ON_SALE, OFF_SALE |
| data.etag | string | 是 | 配置摘要；与 HTTP ETag 一致 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "merchantId": "101",
    "name": "10次卡",
    "cardType": "COUNT_CARD",
    "totalCount": null,
    "priceCent": 8000,
    "validDays": 180,
    "status": "OFF_SALE",
    "etag": "\"cfg-card-01\""
  }
}
```

**业务处理与限制**

创建为OFF_SALE；COUNT_CARD180天、MONTH_CARD30天固定，拒绝自定义validDays。totalCount月卡必须null。

### 7.17 后台持有卡列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/member-cards` |
| 请求方式 | GET |
| 权限 | ADMIN 或 CARD_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q013 Q079–Q084 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| userId | Query | string | 否 | 本商家顾客；本商家顾客；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| status | Query | string | 否 | 卡状态；卡状态 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].merchantId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].userId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].productId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].purchaseNo | string | 否/条件 | 购卡业务号 |
| data.items[].cardType | string | 否/条件 | 卡类型；枚举：COUNT_CARD, MONTH_CARD |
| data.items[].productSnapshot | object | 否/条件 | 业务数据字段 |
| data.items[].productSnapshot.name | string | 否/条件 | 购卡时名称 |
| data.items[].productSnapshot.cardType | string | 否/条件 | 购卡时类型；枚举：COUNT_CARD, MONTH_CARD |
| data.items[].productSnapshot.priceCent | integer | 否/条件 | 购卡时单价；最小 0；最大 9007199254740991 |
| data.items[].productSnapshot.totalCount | integer / null | 否/条件 | 月卡 null；最小 1；最大 9007199254740991 |
| data.items[].productSnapshot.validDays | integer | 否/条件 | 固定有效天数；最小 0；最大 9007199254740991 |
| data.items[].productSnapshot.ruleVersion | string | 否/条件 | 已售规则版本 |
| data.items[].purchaseAmountCent | integer | 否/条件 | 购买金额；最小 0；最大 9007199254740991 |
| data.items[].totalCount | integer / null | 否/条件 | 月卡 null；最小 1；最大 9007199254740991 |
| data.items[].remainingCount | integer / null | 否/条件 | 月卡 null；最小 0；最大 9007199254740991 |
| data.items[].validFrom | string / null | 否/条件 | 确认购买后生效；格式 date-time |
| data.items[].validTo | string / null | 否/条件 | 有效截止，不包含；格式 date-time |
| data.items[].status | string | 否/条件 | 卡生命周期；枚举：PENDING_PAYMENT, ACTIVE, FROZEN, REFUND_PENDING, REFUNDED, CANCELLED |
| data.items[].isExpired | boolean | 否/条件 | 到期是计算属性，不新增 EXPIRED 数据库存储状态 |
| data.items[].freezeReason | string / null | 否/条件 | 业务数据字段 |
| data.items[].refundRequestedAt | string / null | 否/条件 | 时间点，ISO 8601带时区；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "merchantId": "101",
        "userId": "101",
        "productId": "101",
        "purchaseNo": "CARD202611150001",
        "cardType": "COUNT_CARD",
        "productSnapshot": {
          "name": "10次卡",
          "cardType": "COUNT_CARD",
          "priceCent": 8000,
          "totalCount": null,
          "validDays": 180,
          "ruleVersion": "1"
        },
        "purchaseAmountCent": 8000,
        "totalCount": null,
        "remainingCount": null,
        "validFrom": null,
        "validTo": null,
        "status": "PENDING_PAYMENT",
        "isExpired": false,
        "freezeReason": null,
        "refundRequestedAt": null
      }
    ]
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

### 7.18 后台持有卡详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/member-cards/{cardId}` |
| 请求方式 | GET |
| 权限 | ADMIN 或 CARD_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q013 Q079–Q084 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| cardId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.merchantId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.userId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.productId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.purchaseNo | string | 是 | 购卡业务号 |
| data.cardType | string | 是 | 卡类型；枚举：COUNT_CARD, MONTH_CARD |
| data.productSnapshot | object | 是 | 业务数据字段 |
| data.productSnapshot.name | string | 是 | 购卡时名称 |
| data.productSnapshot.cardType | string | 是 | 购卡时类型；枚举：COUNT_CARD, MONTH_CARD |
| data.productSnapshot.priceCent | integer | 是 | 购卡时单价；最小 0；最大 9007199254740991 |
| data.productSnapshot.totalCount | integer / null | 是 | 月卡 null；最小 1；最大 9007199254740991 |
| data.productSnapshot.validDays | integer | 是 | 固定有效天数；最小 0；最大 9007199254740991 |
| data.productSnapshot.ruleVersion | string | 是 | 已售规则版本 |
| data.purchaseAmountCent | integer | 是 | 购买金额；最小 0；最大 9007199254740991 |
| data.totalCount | integer / null | 是 | 月卡 null；最小 1；最大 9007199254740991 |
| data.remainingCount | integer / null | 是 | 月卡 null；最小 0；最大 9007199254740991 |
| data.validFrom | string / null | 是 | 确认购买后生效；格式 date-time |
| data.validTo | string / null | 是 | 有效截止，不包含；格式 date-time |
| data.status | string | 是 | 卡生命周期；枚举：PENDING_PAYMENT, ACTIVE, FROZEN, REFUND_PENDING, REFUNDED, CANCELLED |
| data.isExpired | boolean | 是 | 到期是计算属性，不新增 EXPIRED 数据库存储状态 |
| data.freezeReason | string / null | 是 | 业务数据字段 |
| data.refundRequestedAt | string / null | 是 | 时间点，ISO 8601带时区；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "merchantId": "101",
    "userId": "101",
    "productId": "101",
    "purchaseNo": "CARD202611150001",
    "cardType": "COUNT_CARD",
    "productSnapshot": {
      "name": "10次卡",
      "cardType": "COUNT_CARD",
      "priceCent": 8000,
      "totalCount": null,
      "validDays": 180,
      "ruleVersion": "1"
    },
    "purchaseAmountCent": 8000,
    "totalCount": null,
    "remainingCount": null,
    "validFrom": null,
    "validTo": null,
    "status": "PENDING_PAYMENT",
    "isExpired": false,
    "freezeReason": null,
    "refundRequestedAt": null
  }
}
```

**业务处理与限制**

返回HTTP ETag；不允许跨商家。

### 7.19 后台卡权益流水


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/member-cards/{cardId}/ledger` |
| 请求方式 | GET |
| 权限 | ADMIN 或 CARD_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q081 Q084 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| cardId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].memberCardId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].orderId | string / null | 否/条件 | 人工补偿可空；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].kind | string | 否/条件 | 权益类型；枚举：DEBIT, RETURN, COVER, RELEASE, ADJUST |
| data.items[].countDelta | integer | 否/条件 | 扣次-1、返次+1，月卡覆盖/解除0；最小 -9007199254740991；最大 9007199254740991 |
| data.items[].reversalOfId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].reason | string | 否/条件 | 依据 |
| data.items[].createdAt | string | 否/条件 | 创建时间；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "memberCardId": "101",
        "orderId": null,
        "kind": "DEBIT",
        "countDelta": -1,
        "reversalOfId": null,
        "reason": "预约确认扣次",
        "createdAt": "2026-11-15T10:00:00.000Z"
      }
    ]
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

## 8 通行凭证与实际到店

### 8.1 解析入口二维码


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/access-points/{scanCode}` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 沿用首闭环 v1.1 |
| 业务规则 | Q049–Q051 Q092 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| scanCode | Path | string | 是 | 入口定位码，不代表授权；最短 8；最长 64 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data | object | 是 | 业务数据 |
| data.deviceId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.storeId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.merchantId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.name | string | 是 | 名称 |
| data.direction | string | 是 | 设备进出方向/场景；枚举：ENTRY |
| data.provider | string | 是 | MOCK或真实设备来源；枚举：MOCK, EZVIZ |
| data.onlineStatus | string | 是 | ONLINE/OFFLINE/UNKNOWN；枚举：ONLINE, OFFLINE, UNKNOWN |
| data.supportsRemoteOpen | boolean | 是 | 业务数据字段 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "demo-value",
  "requestId": "101",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "deviceId": "101",
    "storeId": "101",
    "merchantId": "101",
    "name": "演示门店",
    "direction": "ENTRY",
    "provider": "MOCK",
    "onlineStatus": "ONLINE",
    "supportsRemoteOpen": false
  }
}
```

**业务处理与限制**

须登录，只解析启用门店的ENTRY设备；不返回供应商设备秘密。扫码只能定位设备，不能证明有入场权。

### 8.5 本人到店记录


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/visits` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 沿用首闭环 v1.1 |
| 业务规则 | Q048 Q102 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 当前页；最小 1；默认 1 |
| pageSize | Query | integer | 否 | 每页条数；最小 1；最大 100；默认 20 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data | object | 是 | 业务数据 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].visitNo | string | 否/条件 | 到店业务号 |
| data.items[].merchantId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].storeId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].userId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].status | string | 否/条件 | 业务状态，枚举见本行；枚举：IN_STORE, LEFT |
| data.items[].entryAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].exitAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].entrySource | string | 否/条件 | 入场来源DEVICE/SELF_CONFIRM/MERCHANT_REVIEW；枚举：DEVICE, SELF_CONFIRM, MERCHANT_REVIEW |
| data.items[].exitSource | string / null | 否/条件 | 离场确认来源；枚举：DEVICE, SELF_CONFIRM, MERCHANT_REVIEW |
| data.items[].authorizedEndAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].lastOrderId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].usedOrderIds | array<string> | 否/条件 | 本次到店已实际使用的订单，不提前含未来段 |
| data.items[].reviewRequired | boolean | 否/条件 | 是否需要人工复核 |
| data.page | integer | 是 | 页码；最小 1；最大 9007199254740991；格式 int64 |
| data.pageSize | integer | 是 | 页大小；最小 1；最大 9007199254740991；格式 int64 |
| data.total | integer | 是 | 总数；最小 0；最大 9007199254740991；格式 int64 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "demo-value",
  "requestId": "101",
  "serverTime": "2026-11-15T10:10:00.000Z",
  "data": {
    "items": [
      {
        "id": "101",
        "visitNo": "V202611150001",
        "merchantId": "101",
        "storeId": "101",
        "userId": "101",
        "status": "IN_STORE",
        "entryAt": "2026-11-15T10:10:00.000Z",
        "exitAt": null,
        "entrySource": "SELF_CONFIRM",
        "exitSource": null,
        "authorizedEndAt": "2026-11-15T11:00:00.000Z",
        "lastOrderId": "101",
        "usedOrderIds": [
          "101"
        ],
        "reviewRequired": false
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1
  }
}
```

**业务处理与限制**

本人跨店历史，按entry_at、id倒序。

### 8.6 查询指定门店当前到店


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/visits/current` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 沿用首闭环 v1.1 |
| 业务规则 | Q048 Q058 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| storeId | Query | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data | object | 是 | 业务数据 |
| data.visit | object / null | 是 | 实际到店记录；当前无在场时null |
| data.visit.id | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.visit.visitNo | string | 否/条件 | 到店业务号 |
| data.visit.merchantId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.visit.storeId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.visit.userId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.visit.status | string | 否/条件 | 业务状态，枚举见本行；枚举：IN_STORE, LEFT |
| data.visit.entryAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.visit.exitAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.visit.entrySource | string | 否/条件 | 入场来源DEVICE/SELF_CONFIRM/MERCHANT_REVIEW；枚举：DEVICE, SELF_CONFIRM, MERCHANT_REVIEW |
| data.visit.exitSource | string / null | 否/条件 | 离场确认来源；枚举：DEVICE, SELF_CONFIRM, MERCHANT_REVIEW |
| data.visit.authorizedEndAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.visit.lastOrderId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.visit.usedOrderIds | array<string> | 否/条件 | 本次到店已实际使用的订单，不提前含未来段 |
| data.visit.reviewRequired | boolean | 否/条件 | 是否需要人工复核 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "demo-value",
  "requestId": "101",
  "serverTime": "2026-11-15T10:10:00.000Z",
  "data": {
    "visit": null
  }
}
```

**业务处理与限制**

查当前用户该店的IN_STORE；没有则data.visit=null，不随意选一个跨店未决记录。

### 8.7 本人到店详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/visits/{visitId}` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 沿用首闭环 v1.1 |
| 业务规则 | Q040–Q048 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| visitId | Path | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.visitNo | string | 是 | 到店业务号 |
| data.merchantId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.storeId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.userId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.status | string | 是 | 业务状态，枚举见本行；枚举：IN_STORE, LEFT |
| data.entryAt | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.exitAt | string / null | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.entrySource | string | 是 | 入场来源DEVICE/SELF_CONFIRM/MERCHANT_REVIEW；枚举：DEVICE, SELF_CONFIRM, MERCHANT_REVIEW |
| data.exitSource | string / null | 是 | 离场确认来源；枚举：DEVICE, SELF_CONFIRM, MERCHANT_REVIEW |
| data.authorizedEndAt | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.lastOrderId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.usedOrderIds | array<string> | 是 | 本次到店已实际使用的订单，不提前含未来段 |
| data.reviewRequired | boolean | 是 | 是否需要人工复核 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "demo-value",
  "requestId": "101",
  "serverTime": "2026-11-15T10:10:00.000Z",
  "data": {
    "id": "101",
    "visitNo": "V202611150001",
    "merchantId": "101",
    "storeId": "101",
    "userId": "101",
    "status": "IN_STORE",
    "entryAt": "2026-11-15T10:10:00.000Z",
    "exitAt": null,
    "entrySource": "SELF_CONFIRM",
    "exitSource": null,
    "authorizedEndAt": "2026-11-15T11:00:00.000Z",
    "lastOrderId": "101",
    "usedOrderIds": [
      "101"
    ],
    "reviewRequired": false
  }
}
```

**业务处理与限制**

usedOrderIds仅含已实际覆盖时段的订单，未来段不提前标记已用。

### 8.9 本商家到店列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/visits` |
| 请求方式 | GET |
| 权限 | 本商家后台 JWT；ADMIN 或 VISIT_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 沿用首闭环 v1.1 |
| 业务规则 | Q013 Q014 Q102 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 当前页；最小 1；默认 1 |
| pageSize | Query | integer | 否 | 每页条数；最小 1；最大 100；默认 20 |
| storeId | Query | string | 否 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| status | Query | string | 否 | 业务状态，枚举见本行；枚举：IN_STORE, LEFT |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data | object | 是 | 业务数据 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].visitNo | string | 否/条件 | 到店业务号 |
| data.items[].merchantId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].storeId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].userId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].status | string | 否/条件 | 业务状态，枚举见本行；枚举：IN_STORE, LEFT |
| data.items[].entryAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].exitAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].entrySource | string | 否/条件 | 入场来源DEVICE/SELF_CONFIRM/MERCHANT_REVIEW；枚举：DEVICE, SELF_CONFIRM, MERCHANT_REVIEW |
| data.items[].exitSource | string / null | 否/条件 | 离场确认来源；枚举：DEVICE, SELF_CONFIRM, MERCHANT_REVIEW |
| data.items[].authorizedEndAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].lastOrderId | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].usedOrderIds | array<string> | 否/条件 | 本次到店已实际使用的订单，不提前含未来段 |
| data.items[].reviewRequired | boolean | 否/条件 | 是否需要人工复核 |
| data.page | integer | 是 | 页码；最小 1；最大 9007199254740991；格式 int64 |
| data.pageSize | integer | 是 | 页大小；最小 1；最大 9007199254740991；格式 int64 |
| data.total | integer | 是 | 总数；最小 0；最大 9007199254740991；格式 int64 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "demo-value",
  "requestId": "101",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "items": [
      {
        "id": "101",
        "visitNo": "V202611150001",
        "merchantId": "101",
        "storeId": "101",
        "userId": "101",
        "status": "IN_STORE",
        "entryAt": "2026-11-15T10:10:00.000Z",
        "exitAt": null,
        "entrySource": "SELF_CONFIRM",
        "exitSource": null,
        "authorizedEndAt": "2026-11-15T11:00:00.000Z",
        "lastOrderId": "101",
        "usedOrderIds": [
          "101"
        ],
        "reviewRequired": false
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1
  }
}
```

**业务处理与限制**

需要VISIT_READ或ADMIN；不提供跨商家查询。

### 8.12 后台到店详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/visits/{visitId}` |
| 请求方式 | GET |
| 权限 | ADMIN 或 VISIT_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q013 Q048 Q102 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| visitId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.visitNo | string | 是 | 到店业务号 |
| data.merchantId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.storeId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.userId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.status | string | 是 | 业务状态，枚举见本行；枚举：IN_STORE, LEFT |
| data.entryAt | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.exitAt | string / null | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.entrySource | string | 是 | 入场来源DEVICE/SELF_CONFIRM/MERCHANT_REVIEW；枚举：DEVICE, SELF_CONFIRM, MERCHANT_REVIEW |
| data.exitSource | string / null | 是 | 离场确认来源；枚举：DEVICE, SELF_CONFIRM, MERCHANT_REVIEW |
| data.authorizedEndAt | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.lastOrderId | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.usedOrderIds | array<string> | 是 | 本次到店已实际使用的订单，不提前含未来段 |
| data.reviewRequired | boolean | 是 | 是否需要人工复核 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "visitNo": "V202611150001",
    "merchantId": "101",
    "storeId": "101",
    "userId": "101",
    "status": "IN_STORE",
    "entryAt": "2026-11-15T10:10:00.000Z",
    "exitAt": null,
    "entrySource": "SELF_CONFIRM",
    "exitSource": null,
    "authorizedEndAt": "2026-11-15T11:00:00.000Z",
    "lastOrderId": "101",
    "usedOrderIds": [
      "101"
    ],
    "reviewRequired": false
  }
}
```

**业务处理与限制**

实际关联订单不重复计一次到店；时间修正必须走复核接口，不能PATCH原始时间。

## 9 账单与资金流水

### 9.4 本人收退款流水


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/payments` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q036 Q065 Q074 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| merchantId | Query | string | 否 | 商家筛选；商家筛选；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| kind | Query | string | 否 | RECEIPT/REFUND；RECEIPT/REFUND |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].paymentNo | string | 否/条件 | 收退款业务号 |
| data.items[].kind | string | 否/条件 | 流水类型；枚举：RECEIPT, REFUND |
| data.items[].channel | string | 否/条件 | 模拟收退款渠道；枚举：MOCK |
| data.items[].amountCent | integer | 否/条件 | 正数金额；最小 1；最大 9007199254740991；格式 int64 |
| data.items[].status | string | 否/条件 | 业务状态，枚举见本行；枚举：PENDING, SUCCEEDED, FAILED |
| data.items[].completedAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].orderId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].visitId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].memberCardId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].refundOfId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "paymentNo": "PAY202611150001",
        "kind": "RECEIPT",
        "channel": "MOCK",
        "amountCent": 1,
        "status": "SUCCEEDED",
        "completedAt": "2026-11-15T09:50:00.000Z",
        "orderId": null,
        "visitId": null,
        "memberCardId": null,
        "refundOfId": null
      }
    ]
  }
}
```

**业务处理与限制**

仅本人跨商家流水，按completedAt（空时createdAt）、id倒序；模拟单独标MOCK。

### 9.5 本人收退款详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/payments/{paymentId}` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q036 Q074 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| paymentId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.paymentNo | string | 是 | 收退款业务号 |
| data.kind | string | 是 | 流水类型；枚举：RECEIPT, REFUND |
| data.channel | string | 是 | 模拟收退款渠道；枚举：MOCK |
| data.amountCent | integer | 是 | 正数金额；最小 1；最大 9007199254740991；格式 int64 |
| data.status | string | 是 | 业务状态，枚举见本行；枚举：PENDING, SUCCEEDED, FAILED |
| data.completedAt | string / null | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.orderId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.visitId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.memberCardId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.refundOfId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "paymentNo": "PAY202611150001",
    "kind": "RECEIPT",
    "channel": "MOCK",
    "amountCent": 1,
    "status": "SUCCEEDED",
    "completedAt": "2026-11-15T09:50:00.000Z",
    "orderId": null,
    "visitId": null,
    "memberCardId": null,
    "refundOfId": null
  }
}
```

**业务处理与限制**

退款指向原收款；不暴露厂商账号、明文支付秘密。

### 9.6 后台收退款流水


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/payments` |
| 请求方式 | GET |
| 权限 | ADMIN 或 FINANCE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q074 Q101 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| storeId | Query | string | 否 | 门店；购卡无门店，传此参数时不含无归属购卡款；门店；购卡无门店，传此参数时不含无归属购卡款；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| userId | Query | string | 否 | 本商家顾客；本商家顾客；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| kind | Query | string | 否 | RECEIPT/REFUND；RECEIPT/REFUND |
| from | Query | string | 否 | 完成时刻起点，含；完成时刻起点，含；格式 date-time |
| to | Query | string | 否 | 完成时刻终点，不含；完成时刻终点，不含；格式 date-time |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].paymentNo | string | 否/条件 | 收退款业务号 |
| data.items[].kind | string | 否/条件 | 流水类型；枚举：RECEIPT, REFUND |
| data.items[].channel | string | 否/条件 | 模拟收退款渠道；枚举：MOCK |
| data.items[].amountCent | integer | 否/条件 | 正数金额；最小 1；最大 9007199254740991；格式 int64 |
| data.items[].status | string | 否/条件 | 业务状态，枚举见本行；枚举：PENDING, SUCCEEDED, FAILED |
| data.items[].completedAt | string / null | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].orderId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].visitId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].memberCardId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].refundOfId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "paymentNo": "PAY202611150001",
        "kind": "RECEIPT",
        "channel": "MOCK",
        "amountCent": 1,
        "status": "SUCCEEDED",
        "completedAt": "2026-11-15T09:50:00.000Z",
        "orderId": null,
        "visitId": null,
        "memberCardId": null,
        "refundOfId": null
      }
    ]
  }
}
```

**业务处理与限制**

from/to须同时提供，to>from且跨度<=31天；不传默认北京时间当日。成功事实只读，不提供删除或改金额。

### 9.7 后台收退款详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/payments/{paymentId}` |
| 请求方式 | GET |
| 权限 | ADMIN 或 FINANCE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q074 Q104 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| paymentId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.paymentNo | string | 是 | 收退款业务号 |
| data.kind | string | 是 | 流水类型；枚举：RECEIPT, REFUND |
| data.channel | string | 是 | 模拟收退款渠道；枚举：MOCK |
| data.amountCent | integer | 是 | 正数金额；最小 1；最大 9007199254740991；格式 int64 |
| data.status | string | 是 | 业务状态，枚举见本行；枚举：PENDING, SUCCEEDED, FAILED |
| data.completedAt | string / null | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.orderId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.visitId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.memberCardId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.refundOfId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "paymentNo": "PAY202611150001",
    "kind": "RECEIPT",
    "channel": "MOCK",
    "amountCent": 1,
    "status": "SUCCEEDED",
    "completedAt": "2026-11-15T09:50:00.000Z",
    "orderId": null,
    "visitId": null,
    "memberCardId": null,
    "refundOfId": null
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

### 9.8 后台账单修订历史


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/visits/{visitId}/settlement/revisions` |
| 请求方式 | GET |
| 权限 | ADMIN 或 FINANCE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q067 Q104 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| visitId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].settlementId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].version | integer | 否/条件 | 修订版本；最小 1；最大 9007199254740991 |
| data.items[].totalDueCent | integer | 否/条件 | 该版本应收；最小 0；最大 9007199254740991 |
| data.items[].reason | string | 否/条件 | 修订原因 |
| data.items[].adminId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].createdAt | string | 否/条件 | 创建时间；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "settlementId": "101",
        "version": 1,
        "totalDueCent": 300,
        "reason": "可靠出口证据修正",
        "adminId": null,
        "createdAt": "2026-11-15T10:00:00.000Z"
      }
    ]
  }
}
```

**业务处理与限制**

只追加版本按version倒序；不返回任意可编辑快照。

### 9.9 本人账单修订历史


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/visits/{visitId}/settlement/revisions` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q067 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| visitId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].settlementId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].version | integer | 否/条件 | 修订版本；最小 1；最大 9007199254740991 |
| data.items[].totalDueCent | integer | 否/条件 | 该版本应收；最小 0；最大 9007199254740991 |
| data.items[].reason | string | 否/条件 | 修订原因 |
| data.items[].adminId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].createdAt | string | 否/条件 | 创建时间；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "settlementId": "101",
        "version": 1,
        "totalDueCent": 300,
        "reason": "可靠出口证据修正",
        "adminId": null,
        "createdAt": "2026-11-15T10:00:00.000Z"
      }
    ]
  }
}
```

**业务处理与限制**

只本人账单，adminId在顾客响应中为null，原因仅返回适合顾客查看的结论。

## 10 设备、命令与事件

### 10.1 设备列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/devices` |
| 请求方式 | GET |
| 权限 | ADMIN 或 DEVICE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q085–Q092 Q098 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| storeId | Query | string | 否 | 本商家门店；本商家门店；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| deviceType | Query | string | 否 | ACCESS/LOCK/CAMERA/LIGHT；ACCESS/LOCK/CAMERA/LIGHT |
| onlineStatus | Query | string | 否 | ONLINE/OFFLINE/UNKNOWN；ONLINE/OFFLINE/UNKNOWN |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].storeId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].name | string | 否/条件 | 设备名称 |
| data.items[].deviceType | string | 否/条件 | 类型；枚举：ACCESS, LOCK, CAMERA, LIGHT |
| data.items[].direction | string | 否/条件 | 方向；枚举：ENTRY, EXIT, INDOOR, ENVIRONMENT |
| data.items[].provider | string | 否/条件 | 来源；枚举：MOCK, EZVIZ |
| data.items[].model | string / null | 否/条件 | 准确型号 |
| data.items[].status | string | 否/条件 | 登记状态；枚举：ACTIVE, DISABLED |
| data.items[].onlineStatus | string | 否/条件 | 未知不可按在线；枚举：ONLINE, OFFLINE, UNKNOWN |
| data.items[].batteryPercent | integer / null | 否/条件 | 不适用/未知 null；最小 0；最大 100 |
| data.items[].observedAt | string / null | 否/条件 | 时间点，ISO 8601带时区；格式 date-time |
| data.items[].scanCode | string / null | 否/条件 | 随机入口定位码；不是授权 |
| data.items[].capabilities | object | 否/条件 | 业务数据字段 |
| data.items[].capabilities.remoteOpen | boolean | 否/条件 | 已验证可远程开门 |
| data.items[].capabilities.temporaryPassword | boolean | 否/条件 | 已验证临时密码 |
| data.items[].capabilities.face | boolean | 否/条件 | 已验证人脸 |
| data.items[].capabilities.entryIdentity | boolean | 否/条件 | 可可靠关联入口身份 |
| data.items[].capabilities.exitIdentity | boolean | 否/条件 | 可可靠关联出口身份 |
| data.items[].capabilities.liveVideo | boolean | 否/条件 | 真实预览能力 |
| data.items[].capabilities.playback | boolean | 否/条件 | 录像回放能力 |
| data.items[].capabilities.humanAlarm | boolean | 否/条件 | 人形告警能力 |
| data.items[].capabilities.lightControl | boolean | 否/条件 | 灯控能力 |
| data.items[].capabilities.credentialLimit | integer / null | 否/条件 | 已实测容量；未知 null；最小 1；最大 9007199254740991 |
| data.items[].capabilities.verifiedAt | string / null | 否/条件 | 时间点，ISO 8601带时区；格式 date-time |
| data.items[].etag | string | 否/条件 | 可写配置摘要 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "storeId": "101",
        "name": "入口演示门锁",
        "deviceType": "LOCK",
        "direction": "ENTRY",
        "provider": "MOCK",
        "model": null,
        "status": "ACTIVE",
        "onlineStatus": "UNKNOWN",
        "batteryPercent": null,
        "observedAt": null,
        "scanCode": null,
        "capabilities": {
          "remoteOpen": false,
          "temporaryPassword": false,
          "face": false,
          "entryIdentity": false,
          "exitIdentity": false,
          "liveVideo": false,
          "playback": false,
          "humanAlarm": false,
          "lightControl": false,
          "credentialLimit": null,
          "verifiedAt": null
        },
        "etag": "\"cfg-device-01\""
      }
    ]
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

### 10.2 设备详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/devices/{deviceId}` |
| 请求方式 | GET |
| 权限 | ADMIN 或 DEVICE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q085–Q092 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| deviceId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.storeId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.name | string | 是 | 设备名称 |
| data.deviceType | string | 是 | 类型；枚举：ACCESS, LOCK, CAMERA, LIGHT |
| data.direction | string | 是 | 方向；枚举：ENTRY, EXIT, INDOOR, ENVIRONMENT |
| data.provider | string | 是 | 来源；枚举：MOCK, EZVIZ |
| data.model | string / null | 是 | 准确型号 |
| data.status | string | 是 | 登记状态；枚举：ACTIVE, DISABLED |
| data.onlineStatus | string | 是 | 未知不可按在线；枚举：ONLINE, OFFLINE, UNKNOWN |
| data.batteryPercent | integer / null | 是 | 不适用/未知 null；最小 0；最大 100 |
| data.observedAt | string / null | 是 | 时间点，ISO 8601带时区；格式 date-time |
| data.scanCode | string / null | 是 | 随机入口定位码；不是授权 |
| data.capabilities | object | 是 | 业务数据字段 |
| data.capabilities.remoteOpen | boolean | 是 | 已验证可远程开门 |
| data.capabilities.temporaryPassword | boolean | 是 | 已验证临时密码 |
| data.capabilities.face | boolean | 是 | 已验证人脸 |
| data.capabilities.entryIdentity | boolean | 是 | 可可靠关联入口身份 |
| data.capabilities.exitIdentity | boolean | 是 | 可可靠关联出口身份 |
| data.capabilities.liveVideo | boolean | 是 | 真实预览能力 |
| data.capabilities.playback | boolean | 是 | 录像回放能力 |
| data.capabilities.humanAlarm | boolean | 是 | 人形告警能力 |
| data.capabilities.lightControl | boolean | 是 | 灯控能力 |
| data.capabilities.credentialLimit | integer / null | 是 | 已实测容量；未知 null；最小 1；最大 9007199254740991 |
| data.capabilities.verifiedAt | string / null | 是 | 时间点，ISO 8601带时区；格式 date-time |
| data.etag | string | 是 | 可写配置摘要 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "storeId": "101",
    "name": "入口演示门锁",
    "deviceType": "LOCK",
    "direction": "ENTRY",
    "provider": "MOCK",
    "model": null,
    "status": "ACTIVE",
    "onlineStatus": "UNKNOWN",
    "batteryPercent": null,
    "observedAt": null,
    "scanCode": null,
    "capabilities": {
      "remoteOpen": false,
      "temporaryPassword": false,
      "face": false,
      "entryIdentity": false,
      "exitIdentity": false,
      "liveVideo": false,
      "playback": false,
      "humanAlarm": false,
      "lightControl": false,
      "credentialLimit": null,
      "verifiedAt": null
    },
    "etag": "\"cfg-device-01\""
  }
}
```

**业务处理与限制**

返回HTTP ETag。不回显providerAccountRef中的密钥内容、完整临时密码、validateCode或token。

### 10.4 修改设备展示配置


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/devices/{deviceId}` |
| 请求方式 | PATCH |
| 权限 | ADMIN |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q090 Q104 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| deviceId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| Idempotency-Key | Header | string | 是 | 同一次动作的网络重试复用；不同动作换键；8–64位字母数字、下划线或短横线；最短 8；最长 64 |
| If-Match | Header | string | 是 | 缺失428，资源配置变化412；锁内再次比对；读取资源时返回的 ETag |
| name | Body | string | 是 | 名称；最短 1；最长 100 |
| model | Body | string / null | 是 | 型号备注，不能借此宣称能力 |
| reason | Body | string | 是 | 操作原因，非空；写入审计；最短 1；最长 500 |

请求JSON示例：

```json
{
  "name": "入口演示门锁",
  "model": null,
  "reason": "课程演示设备故障核实"
}
```

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.storeId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.name | string | 是 | 设备名称 |
| data.deviceType | string | 是 | 类型；枚举：ACCESS, LOCK, CAMERA, LIGHT |
| data.direction | string | 是 | 方向；枚举：ENTRY, EXIT, INDOOR, ENVIRONMENT |
| data.provider | string | 是 | 来源；枚举：MOCK, EZVIZ |
| data.model | string / null | 是 | 准确型号 |
| data.status | string | 是 | 登记状态；枚举：ACTIVE, DISABLED |
| data.onlineStatus | string | 是 | 未知不可按在线；枚举：ONLINE, OFFLINE, UNKNOWN |
| data.batteryPercent | integer / null | 是 | 不适用/未知 null；最小 0；最大 100 |
| data.observedAt | string / null | 是 | 时间点，ISO 8601带时区；格式 date-time |
| data.scanCode | string / null | 是 | 随机入口定位码；不是授权 |
| data.capabilities | object | 是 | 业务数据字段 |
| data.capabilities.remoteOpen | boolean | 是 | 已验证可远程开门 |
| data.capabilities.temporaryPassword | boolean | 是 | 已验证临时密码 |
| data.capabilities.face | boolean | 是 | 已验证人脸 |
| data.capabilities.entryIdentity | boolean | 是 | 可可靠关联入口身份 |
| data.capabilities.exitIdentity | boolean | 是 | 可可靠关联出口身份 |
| data.capabilities.liveVideo | boolean | 是 | 真实预览能力 |
| data.capabilities.playback | boolean | 是 | 录像回放能力 |
| data.capabilities.humanAlarm | boolean | 是 | 人形告警能力 |
| data.capabilities.lightControl | boolean | 是 | 灯控能力 |
| data.capabilities.credentialLimit | integer / null | 是 | 已实测容量；未知 null；最小 1；最大 9007199254740991 |
| data.capabilities.verifiedAt | string / null | 是 | 时间点，ISO 8601带时区；格式 date-time |
| data.etag | string | 是 | 可写配置摘要 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "storeId": "101",
    "name": "入口演示门锁",
    "deviceType": "LOCK",
    "direction": "ENTRY",
    "provider": "MOCK",
    "model": null,
    "status": "ACTIVE",
    "onlineStatus": "UNKNOWN",
    "batteryPercent": null,
    "observedAt": null,
    "scanCode": null,
    "capabilities": {
      "remoteOpen": false,
      "temporaryPassword": false,
      "face": false,
      "entryIdentity": false,
      "exitIdentity": false,
      "liveVideo": false,
      "playback": false,
      "humanAlarm": false,
      "lightControl": false,
      "credentialLimit": null,
      "verifiedAt": null
    },
    "etag": "\"cfg-device-01\""
  }
}
```

**业务处理与限制**

不允许在此换商家、门店、provider、设备标识或方向；换设备须停用并处理原权限后重新登记。在线/电量/能力只由可信观察更新。

### 10.9 设备命令列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/device-operations` |
| 请求方式 | GET |
| 权限 | ADMIN 或 DEVICE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q085–Q089 Q098 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| storeId | Query | string | 否 | 门店；门店；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| deviceId | Query | string | 否 | 设备；设备；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| status | Query | string | 否 | PENDING/RUNNING/SUCCEEDED/FAILED/UNKNOWN/SUPERSEDED；PENDING/RUNNING/SUCCEEDED/FAILED/UNKNOWN/SUPERSEDED |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].operationNo | string | 否/条件 | 命令号 |
| data.items[].storeId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].deviceId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].userId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].orderId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].visitId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].credentialId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].action | string | 否/条件 | 操作；枚举：OPEN, SYNC_CREDENTIAL, LIGHT_ON, LIGHT_OFF |
| data.items[].status | string | 否/条件 | 命令状态；枚举：PENDING, RUNNING, SUCCEEDED, FAILED, UNKNOWN, SUPERSEDED |
| data.items[].generation | integer / null | 否/条件 | 目标版本；无版本 null；最小 1；最大 9007199254740991 |
| data.items[].provider | string | 否/条件 | 来源；枚举：MOCK, EZVIZ |
| data.items[].acceptedAt | string / null | 否/条件 | 平台仅受理的时刻，不等于执行；格式 date-time |
| data.items[].executedAt | string / null | 否/条件 | 可靠执行成功发生时刻；格式 date-time |
| data.items[].receivedAt | string / null | 否/条件 | 本系统收到设备结果时刻；格式 date-time |
| data.items[].errorCode | string / null | 否/条件 | 依赖或业务错误码 |
| data.items[].errorMessage | string / null | 否/条件 | 业务数据字段 |
| data.items[].retryAllowed | boolean | 否/条件 | 后端依据确定未执行等证据计算 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "operationNo": "OP202611150001",
        "storeId": "101",
        "deviceId": "101",
        "userId": null,
        "orderId": null,
        "visitId": null,
        "credentialId": null,
        "action": "SYNC_CREDENTIAL",
        "status": "PENDING",
        "generation": null,
        "provider": "MOCK",
        "acceptedAt": null,
        "executedAt": null,
        "receivedAt": null,
        "errorCode": null,
        "errorMessage": null,
        "retryAllowed": false
      }
    ]
  }
}
```

**业务处理与限制**

按createdAt、id倒序，不返回密文及原始平台报文。

### 10.10 设备命令详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/device-operations/{operationId}` |
| 请求方式 | GET |
| 权限 | ADMIN 或 DEVICE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q085–Q089 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| operationId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.operationNo | string | 是 | 命令号 |
| data.storeId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.deviceId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.userId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.orderId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.visitId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.credentialId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.action | string | 是 | 操作；枚举：OPEN, SYNC_CREDENTIAL, LIGHT_ON, LIGHT_OFF |
| data.status | string | 是 | 命令状态；枚举：PENDING, RUNNING, SUCCEEDED, FAILED, UNKNOWN, SUPERSEDED |
| data.generation | integer / null | 是 | 目标版本；无版本 null；最小 1；最大 9007199254740991 |
| data.provider | string | 是 | 来源；枚举：MOCK, EZVIZ |
| data.acceptedAt | string / null | 是 | 平台仅受理的时刻，不等于执行；格式 date-time |
| data.executedAt | string / null | 是 | 可靠执行成功发生时刻；格式 date-time |
| data.receivedAt | string / null | 是 | 本系统收到设备结果时刻；格式 date-time |
| data.errorCode | string / null | 是 | 依赖或业务错误码 |
| data.errorMessage | string / null | 是 | 业务数据字段 |
| data.retryAllowed | boolean | 是 | 后端依据确定未执行等证据计算 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "operationNo": "OP202611150001",
    "storeId": "101",
    "deviceId": "101",
    "userId": null,
    "orderId": null,
    "visitId": null,
    "credentialId": null,
    "action": "SYNC_CREDENTIAL",
    "status": "PENDING",
    "generation": null,
    "provider": "MOCK",
    "acceptedAt": null,
    "executedAt": null,
    "receivedAt": null,
    "errorCode": null,
    "errorMessage": null,
    "retryAllowed": false
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

### 10.13 设备事件列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/device-events` |
| 请求方式 | GET |
| 权限 | ADMIN 或 DEVICE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q049 Q052 Q058 Q098 Q103 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| storeId | Query | string | 否 | 门店；门店；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| deviceId | Query | string | 否 | 设备；设备；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| processingStatus | Query | string | 否 | PENDING/PROCESSING/PROCESSED/UNMATCHED/FAILED；PENDING/PROCESSING/PROCESSED/UNMATCHED/FAILED |
| from | Query | string | 否 | 发生时间起点；发生时间起点；格式 date-time |
| to | Query | string | 否 | 发生时间终点；发生时间终点；格式 date-time |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].deviceId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].eventType | string | 否/条件 | 归一化类型，不冒充平台原字段 |
| data.items[].occurredAt | string | 否/条件 | 原事实发生时间；格式 date-time |
| data.items[].receivedAt | string | 否/条件 | 本系统收到设备结果时刻；格式 date-time |
| data.items[].identityReliable | boolean | 否/条件 | 身份方向是否可靠 |
| data.items[].matchedUserId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].matchedOrderId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].operationId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].processingStatus | string | 否/条件 | 消费状态；枚举：PENDING, PROCESSING, PROCESSED, UNMATCHED, FAILED |
| data.items[].summary | string | 否/条件 | 白名单脱敏摘要 |
| data.items[].provider | string | 否/条件 | 来源；枚举：MOCK, EZVIZ |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "deviceId": "101",
        "eventType": "HUMAN_ALARM",
        "occurredAt": "2026-11-15T10:00:00.000Z",
        "receivedAt": "2026-11-15T10:00:00.000Z",
        "identityReliable": false,
        "matchedUserId": null,
        "matchedOrderId": null,
        "operationId": null,
        "processingStatus": "PENDING",
        "summary": "教学设备人形告警",
        "provider": "MOCK"
      }
    ]
  }
}
```

**业务处理与限制**

from/to同时提供且跨度<=31天，不传默认最近一天；按occurredAt、id倒序，区分发生和接收时刻。

### 10.14 设备事件详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/device-events/{eventId}` |
| 请求方式 | GET |
| 权限 | ADMIN 或 DEVICE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q058 Q092 Q098 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| eventId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.deviceId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.eventType | string | 是 | 归一化类型，不冒充平台原字段 |
| data.occurredAt | string | 是 | 原事实发生时间；格式 date-time |
| data.receivedAt | string | 是 | 本系统收到设备结果时刻；格式 date-time |
| data.identityReliable | boolean | 是 | 身份方向是否可靠 |
| data.matchedUserId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.matchedOrderId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.operationId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.processingStatus | string | 是 | 消费状态；枚举：PENDING, PROCESSING, PROCESSED, UNMATCHED, FAILED |
| data.summary | string | 是 | 白名单脱敏摘要 |
| data.provider | string | 是 | 来源；枚举：MOCK, EZVIZ |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "deviceId": "101",
    "eventType": "HUMAN_ALARM",
    "occurredAt": "2026-11-15T10:00:00.000Z",
    "receivedAt": "2026-11-15T10:00:00.000Z",
    "identityReliable": false,
    "matchedUserId": null,
    "matchedOrderId": null,
    "operationId": null,
    "processingStatus": "PENDING",
    "summary": "教学设备人形告警",
    "provider": "MOCK"
  }
}
```

**业务处理与限制**

只返回脱敏白名单摘要；平台解密材料、私密原始报文不交Web。

### 10.15 查询稳定设备人员映射


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/devices/{deviceId}/persons` |
| 请求方式 | GET |
| 权限 | ADMIN 或 DEVICE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q087 Q090 Q092 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| deviceId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].deviceId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].userId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].hasProviderMapping | boolean | 否/条件 | 已存在稳定映射，不返回设备秘密 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "deviceId": "101",
        "userId": "101",
        "hasProviderMapping": true
      }
    ]
  }
}
```

**业务处理与限制**

映射由凭证服务创建，不提供随意删除人员或改变userId的接口，以免撤掉同用户其他有效权限。

### 10.16 后台通行凭证列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/access-credentials` |
| 请求方式 | GET |
| 权限 | ADMIN 或 DEVICE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q085–Q089 Q092 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| deviceId | Query | string | 否 | 设备；设备；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| userId | Query | string | 否 | 本商家顾客；本商家顾客；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| observedStatus | Query | string | 否 | 当前观察状态；当前观察状态 |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].deviceId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].userId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].credentialType | string | 否/条件 | 凭证类型；枚举：TEMP_PASSWORD, FACE, QR |
| data.items[].generation | integer | 否/条件 | 当前目标版本；最小 1；最大 9007199254740991 |
| data.items[].observedGeneration | integer / null | 否/条件 | 确认版本；未知 null；最小 1；最大 9007199254740991 |
| data.items[].observedStatus | string | 否/条件 | 设备观察状态；枚举：NOT_GRANTED, GRANTING, ACTIVE, REVOKING, REVOKED, FAILED, UNKNOWN |
| data.items[].windows | array<object> | 否/条件 | 业务数据字段 |
| data.items[].windows[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].windows[].generation | integer | 否/条件 | 目标版本；最小 1；最大 9007199254740991 |
| data.items[].windows[].validFrom | string | 否/条件 | 业务数据字段；格式 date-time |
| data.items[].windows[].validTo | string | 否/条件 | 截止不含；格式 date-time |
| data.items[].windows[].basisOrderIds | array<string> | 否/条件 | 生成权限窗口的订单解释快照 |
| data.items[].lastError | string / null | 否/条件 | 脱敏失败原因 |
| data.items[].provider | string | 否/条件 | 来源；枚举：MOCK, EZVIZ |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "deviceId": "101",
        "userId": "101",
        "credentialType": "TEMP_PASSWORD",
        "generation": 1,
        "observedGeneration": null,
        "observedStatus": "GRANTING",
        "windows": [
          {
            "id": "101",
            "generation": 1,
            "validFrom": "2026-11-15T10:00:00.000Z",
            "validTo": "2026-11-15T11:00:00.000Z",
            "basisOrderIds": []
          }
        ],
        "lastError": null,
        "provider": "MOCK"
      }
    ]
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

### 10.17 后台通行凭证详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/access-credentials/{credentialId}` |
| 请求方式 | GET |
| 权限 | ADMIN 或 DEVICE_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q085–Q089 Q092 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| credentialId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.deviceId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.userId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.credentialType | string | 是 | 凭证类型；枚举：TEMP_PASSWORD, FACE, QR |
| data.generation | integer | 是 | 当前目标版本；最小 1；最大 9007199254740991 |
| data.observedGeneration | integer / null | 是 | 确认版本；未知 null；最小 1；最大 9007199254740991 |
| data.observedStatus | string | 是 | 设备观察状态；枚举：NOT_GRANTED, GRANTING, ACTIVE, REVOKING, REVOKED, FAILED, UNKNOWN |
| data.windows | array<object> | 是 | 业务数据字段 |
| data.windows[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.windows[].generation | integer | 否/条件 | 目标版本；最小 1；最大 9007199254740991 |
| data.windows[].validFrom | string | 否/条件 | 业务数据字段；格式 date-time |
| data.windows[].validTo | string | 否/条件 | 截止不含；格式 date-time |
| data.windows[].basisOrderIds | array<string> | 否/条件 | 生成权限窗口的订单解释快照 |
| data.lastError | string / null | 是 | 脱敏失败原因 |
| data.provider | string | 是 | 来源；枚举：MOCK, EZVIZ |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "deviceId": "101",
    "userId": "101",
    "credentialType": "TEMP_PASSWORD",
    "generation": 1,
    "observedGeneration": null,
    "observedStatus": "GRANTING",
    "windows": [
      {
        "id": "101",
        "generation": 1,
        "validFrom": "2026-11-15T10:00:00.000Z",
        "validTo": "2026-11-15T11:00:00.000Z",
        "basisOrderIds": []
      }
    ],
    "lastError": null,
    "provider": "MOCK"
  }
}
```

**业务处理与限制**

不返回密码；目标区间和已确认版本分别展示，不能以旧ACTIVE宣称新窗口已生效。

## 12 人脸授权与资料删除

### 12.2 本人已授权人脸资料列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/face-profiles` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q011 Q012 Q092 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| merchantId | Query | string | 否 | 本人的该商家资料；本人的该商家资料；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].merchantId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].consentVersion | string | 否/条件 | 已同意版本 |
| data.items[].consentAt | string | 否/条件 | 时间点，ISO 8601带时区；格式 date-time |
| data.items[].expiresAt | string | 否/条件 | 保存截止；格式 date-time |
| data.items[].status | string | 否/条件 | 状态；枚举：ACTIVE, DELETE_PENDING, DELETED |
| data.items[].deletedAt | string / null | 否/条件 | 时间点，ISO 8601带时区；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "merchantId": "101",
        "consentVersion": "face-2026-01",
        "consentAt": "2026-11-15T10:00:00.000Z",
        "expiresAt": "2026-12-15T10:00:00.000Z",
        "status": "ACTIVE",
        "deletedAt": null
      }
    ]
  }
}
```

**业务处理与限制**

只返回授权元数据，不给原图URL。

### 12.4 查看本人授权资料元数据


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/app/face-profiles/{profileId}` |
| 请求方式 | GET |
| 权限 | 顾客 JWT |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q012 Q092 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| profileId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.merchantId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.consentVersion | string | 是 | 已同意版本 |
| data.consentAt | string | 是 | 时间点，ISO 8601带时区；格式 date-time |
| data.expiresAt | string | 是 | 保存截止；格式 date-time |
| data.status | string | 是 | 状态；枚举：ACTIVE, DELETE_PENDING, DELETED |
| data.deletedAt | string / null | 是 | 时间点，ISO 8601带时区；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "merchantId": "101",
    "consentVersion": "face-2026-01",
    "consentAt": "2026-11-15T10:00:00.000Z",
    "expiresAt": "2026-12-15T10:00:00.000Z",
    "status": "ACTIVE",
    "deletedAt": null
  }
}
```

**业务处理与限制**

遵循公共身份、隔离、幂等及分页约定。

## 14 异常待办与人工复核

### 14.1 本商家异常待办列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/review-tasks` |
| 请求方式 | GET |
| 权限 | 本商家后台 JWT；ADMIN 或 REVIEW_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 沿用首闭环 v1.1 |
| 业务规则 | Q072 Q093–Q099 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 当前页；最小 1；默认 1 |
| pageSize | Query | integer | 否 | 每页条数；最小 1；最大 100；默认 20 |
| status | Query | string | 否 | 业务状态，枚举见本行；枚举：OPEN, PROCESSING, RESOLVED |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data | object | 是 | 业务数据 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].category | string | 否/条件 | 待办类别 |
| data.items[].storeId | string / null | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].orderId | string / null | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].visitId | string / null | 否/条件 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].status | string | 否/条件 | 业务状态，枚举见本行；枚举：OPEN, PROCESSING, RESOLVED |
| data.items[].createdAt | string | 否/条件 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.items[].summary | string | 否/条件 | 脱敏摘要，不含完整原始报文 |
| data.page | integer | 是 | 页码；最小 1；最大 9007199254740991；格式 int64 |
| data.pageSize | integer | 是 | 页大小；最小 1；最大 9007199254740991；格式 int64 |
| data.total | integer | 是 | 总数；最小 0；最大 9007199254740991；格式 int64 |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "demo-value",
  "requestId": "101",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "items": [
      {
        "id": "101",
        "category": "demo-value",
        "storeId": null,
        "orderId": null,
        "visitId": null,
        "status": "OPEN",
        "createdAt": "2026-11-12T10:00:00.000Z",
        "summary": "demo-value"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1
  }
}
```

**业务处理与限制**

需要REVIEW_READ或ADMIN。首闭环提供查看，人工修改金额、信用、卡退卡等交互另行定义，不能绕过已有规则处理。

### 14.2 待办详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/review-tasks/{taskId}` |
| 请求方式 | GET |
| 权限 | ADMIN 或 REVIEW_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q057 Q072 Q093–Q099 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| taskId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.category | string | 是 | 待办类别 |
| data.storeId | string / null | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.orderId | string / null | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.visitId | string / null | 是 | Java Long正整数ID字符串，范围1..9223372036854775807；后端需校验上界，不转换成JS Number；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.status | string | 是 | 业务状态，枚举见本行；枚举：OPEN, PROCESSING, RESOLVED |
| data.createdAt | string | 是 | 带时区的ISO8601时间点，UTC示例；格式 date-time |
| data.summary | string | 是 | 脱敏摘要，不含完整原始报文 |
| data.userId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.memberCardId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.resolution | string / null | 是 | 业务数据字段 |
| data.resolvedBy | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.resolvedAt | string / null | 是 | 时间点，ISO 8601带时区；格式 date-time |
| data.evidenceSummary | string | 是 | 白名单脱敏证据及时间可信性 |
| data.updatedAt | string | 是 | 最后更新时间；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "category": "demo-value",
    "storeId": null,
    "orderId": null,
    "visitId": null,
    "status": "OPEN",
    "createdAt": "2026-11-12T10:00:00.000Z",
    "summary": "demo-value",
    "userId": null,
    "memberCardId": null,
    "resolution": null,
    "resolvedBy": null,
    "resolvedAt": null,
    "evidenceSummary": "消息链路未知，待核验录像",
    "updatedAt": "2026-11-15T10:00:00.000Z"
  }
}
```

**业务处理与限制**

证据返回脱敏摘要，保留发生/接收时刻和可靠性；不返回人脸或秘密。

## 15 日报、统计与审计

### 15.7 操作审计列表


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/audit-events` |
| 请求方式 | GET |
| 权限 | ADMIN 或 AUDIT_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q104 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| page | Query | integer | 否 | 页码，默认1；页码，默认1；最小 1；最大 9007199254740991；默认 1 |
| pageSize | Query | integer | 否 | 默认20，最大100；默认20，最大100；最小 1；最大 100；默认 20 |
| targetType | Query | string | 否 | ORDER/VISIT/CARD/STORE/DEVICE/CUSTOMER等；ORDER/VISIT/CARD/STORE/DEVICE/CUSTOMER等 |
| targetId | Query | string | 否 | 与targetType同时传；与targetType同时传；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| from | Query | string | 否 | 发生时间起点；发生时间起点；格式 date-time |
| to | Query | string | 否 | 发生时间终点；发生时间终点；格式 date-time |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.page | integer | 是 | 当前页；最小 1；最大 9007199254740991 |
| data.pageSize | integer | 是 | 每页条数；最小 1；最大 100 |
| data.total | integer | 是 | 总条数；最小 0；最大 9007199254740991 |
| data.items | array<object> | 是 | 本页记录数组 |
| data.items[].id | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].eventType | string | 否/条件 | 审计事件类型 |
| data.items[].targetType | string | 否/条件 | 对象类型 |
| data.items[].targetId | string | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].actorType | string | 否/条件 | 操作来源；枚举：CUSTOMER, ADMIN, SYSTEM, DEVICE |
| data.items[].adminId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].userId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].deviceEventId | string / null | 否/条件 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.items[].reason | string / null | 否/条件 | 操作原因 |
| data.items[].summary | string | 否/条件 | 白名单脱敏变更摘要 |
| data.items[].occurredAt | string | 否/条件 | 原事实发生时间；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "id": "101",
        "eventType": "STORE_RULE_CHANGED",
        "targetType": "STORE",
        "targetId": "101",
        "actorType": "ADMIN",
        "adminId": null,
        "userId": null,
        "deviceEventId": null,
        "reason": null,
        "summary": "修改未来未售时段价格",
        "occurredAt": "2026-11-15T10:00:00.000Z"
      }
    ]
  }
}
```

**业务处理与限制**

时间同时传且跨度<=31天，不传默认最近一天；只读，按occurredAt、id倒序，不返回JWT、密码、主密钥、人脸原图。

### 15.8 操作审计详情


| 项目 | 定义 |
|---|---|
| 路由路径 | `/api/v1/admin/audit-events/{auditId}` |
| 请求方式 | GET |
| 权限 | ADMIN 或 AUDIT_READ |
| Content-Type | application/json |
| 成功/受理HTTP | 200 |
| 来源 | 新增设计 |
| 业务规则 | Q104 |

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明与约束 |
|---|---|---|---|---|
| Authorization | Header | string | 是 | Bearer JWT；主体及权限见上表 |
| auditId | Path | string | 是 | 资源归属须校验；正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| — | Body | — | 否 | 无请求体 |

**返回 JSON 字段**

| 字段 | 类型 | 必有 | 说明与约束 |
|---|---|---|---|
| code | string | 是 | 成功业务结果码，固定OK；枚举：OK |
| message | string | 是 | 人类可读说明 |
| requestId | string | 是 | 服务端链路标识 |
| serverTime | string | 是 | 服务端当前时间；格式 date-time |
| data | object | 是 | 业务数据 |
| data.id | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.eventType | string | 是 | 审计事件类型 |
| data.targetType | string | 是 | 对象类型 |
| data.targetId | string | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.actorType | string | 是 | 操作来源；枚举：CUSTOMER, ADMIN, SYSTEM, DEVICE |
| data.adminId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.userId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.deviceEventId | string / null | 是 | 正整数 ID 字符串，范围 1..9223372036854775807；最长 19；格式表达式 ^[1-9][0-9]{0,18}$ |
| data.reason | string / null | 是 | 操作原因 |
| data.summary | string | 是 | 白名单脱敏变更摘要 |
| data.occurredAt | string | 是 | 原事实发生时间；格式 date-time |

返回JSON示例：

```json
{
  "code": "OK",
  "message": "操作成功",
  "requestId": "req-demo-001",
  "serverTime": "2026-11-15T09:50:00.000Z",
  "data": {
    "id": "101",
    "eventType": "STORE_RULE_CHANGED",
    "targetType": "STORE",
    "targetId": "101",
    "actorType": "ADMIN",
    "adminId": null,
    "userId": null,
    "deviceEventId": null,
    "reason": null,
    "summary": "修改未来未售时段价格",
    "occurredAt": "2026-11-15T10:00:00.000Z"
  }
}
```

**业务处理与限制**

禁止删除或任意覆盖审计；详情也只返回服务端脱敏白名单摘要。
