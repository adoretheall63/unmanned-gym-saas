# 小程序原型 · 屏幕与接口对照

自动生成。`命中` 表示契约里存在该路径；`待补` 表示原型需要但契约未覆盖，由后续补齐。

## login · 登录

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| POST | `/app/auth/wechat-login` | 微信登录 | implemented | 是 |
| POST | `/app/auth/phone-login` | 手机号登录 | planned | 是 |

## today · 今天

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/app/orders` | 本人跨商家订单 | planned | 是 |
| GET | `/app/stores` | 公开营业门店列表 | planned | 是 |
| GET | `/app/stores/{storeId}/slots` | 查询某日时段 | planned | 是 |

## venues · 场 · 列表

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/app/stores` | 公开营业门店列表 | planned | 是 |
| GET | `/app/stores/{storeId}/slots` | 查询某日时段 | planned | 是 |

## venue · 场 · 选时段

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/app/stores/{storeId}` | 门店详情 | planned | 是 |
| GET | `/app/stores/{storeId}/slots` | 查询某日时段 | planned | 是 |

## confirm · 确认

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| POST | `/app/orders` | 创建待支付预约 | planned | 是 |
| POST | `/app/orders/{orderId}/mock-payment` | 模拟支付或余额支付预约费 | planned | 是 |
| POST | `/app/orders/{orderId}/benefit-confirmation` | 确认卡预约权益 | planned | 是 |
| GET | `/app/wallet` | 本人模拟钱包余额 | planned | 是 |

## paid · 预约成功

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/app/orders/{orderId}` | 本人订单详情 | planned | 是 |
| GET | `/app/orders/{orderId}/access` | 查询订单连续通行状态 | planned | 是 |

## cancel · 取消预约

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| POST | `/app/orders/{orderId}/cancellation` | 申请取消预约 | planned | 是 |
| GET | `/app/orders/{orderId}` | 本人订单详情 | planned | 是 |

## door · 到场 · 开门

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/app/orders/{orderId}/access` | 查询订单连续通行状态 | planned | 是 |
| GET | `/app/access-points/{scanCode}` | 解析入口二维码 | planned | 是 |
| POST | `/app/orders/{orderId}/open-requests` | 申请订单开门 | planned | 是 |
| GET | `/app/device-operations/{operationId}` | 查询本人开门结果 | planned | 是 |
| POST | `/app/visits` | 本人确认入场 | planned | 是 |

## session · 计时中

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/app/visits/{visitId}` | 查询本人到店详情 | planned | 是 |
| GET | `/app/visits/{visitId}/settlement` | 查询预估或最终账单 | planned | 是 |
| GET | `/app/wallet` | 本人模拟钱包余额 | planned | 是 |
| POST | `/app/visits/{visitId}/finish` | 结束锻炼并结算 | planned | 是 |

## bill · 账单

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/app/visits/{visitId}/settlement` | 查询预估或最终账单 | planned | 是 |
| GET | `/app/orders/{orderId}` | 本人订单详情 | planned | 是 |
| GET | `/app/member-cards/{cardId}` | 本人持有卡详情 | planned | 是 |

## bills · 全部账单

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/app/visits` | 查询本人到店记录 | planned | 是 |
| GET | `/app/visits/{visitId}/settlement` | 查询预估或最终账单 | planned | 是 |
| GET | `/app/orders/{orderId}` | 本人订单详情 | planned | 是 |

## cards · 卡

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/app/member-cards` | 本人持有卡列表 | planned | 是 |
| GET | `/app/wallet` | 本人模拟钱包余额 | planned | 是 |
| GET | `/app/member-cards/{cardId}/ledger` | 本人卡权益流水 | planned | 是 |
| GET | `/app/orders/{orderId}` | 本人订单详情 | planned | 是 |
| GET | `/app/visits` | 查询本人到店记录 | planned | 是 |
| GET | `/app/payments` | 查询本人收退款流水 | planned | 是 |
| GET | `/app/merchants/{merchantId}/card-products` | 公开卡产品列表 | planned | 是 |
| POST | `/app/member-cards` | 创建购卡单 | planned | 是 |
| POST | `/app/member-cards/{cardId}/mock-payment` | 模拟购卡支付 | planned | 是 |
| GET | `/app/member-cards/{cardId}/refund-eligibility` | 查询退卡资格和依赖 | planned | 是 |
| POST | `/app/member-cards/{cardId}/refund-request` | 提交退卡申请 | planned | 是 |
| GET | `/app/member-cards/{cardId}/refund-request` | 查询退卡处理进度 | planned | 是 |

## me · 我

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/app/me` | 读取本人资料 | implemented | 是 |
| GET | `/app/me` | 读取本人资料 | implemented | 是 |
| POST | `/app/auth/logout` | 退出顾客全部登录 | implemented | 是 |

## profile · 资料与隐私

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/app/me` | 读取本人资料 | implemented | 是 |
| GET | `/app/face-profiles` |  |  | 暂缓 |
| PATCH | `/app/me` | 修改本人昵称 | implemented | 是 |
| POST | `/app/auth/logout` | 退出顾客全部登录 | implemented | 是 |

