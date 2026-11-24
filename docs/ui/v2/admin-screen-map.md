# 小程序原型 · 屏幕与接口对照

自动生成。`命中` 表示契约里存在该路径；`待补` 表示原型需要但契约未覆盖，由后续补齐。

## overview · 概览

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/admin/dashboard` | 运营首页概览 | planned | 是 |
| GET | `/admin/reports/daily` | 查询自然日日报 | planned | 是 |
| GET | `/admin/reports/trends` | 日期区间营收与客流趋势 | planned | 是 |
| GET | `/admin/dashboard` | 运营首页概览 | planned | 是 |
| GET | `/admin/review-tasks` | 本商家异常待办列表 | planned | 是 |
| GET | `/admin/devices` |  |  | 暂缓 |
| GET | `/admin/device-events` |  |  | 暂缓 |
| GET | `/admin/stores` | 后台门店列表 | planned | 是 |
| GET | `/admin/reports/traffic` | 分时客流统计 | planned | 是 |
| GET | `/admin/stores/{storeId}/slots` | 后台时段列表 | planned | 是 |
| GET | `/admin/reports/occupancy` | 逐店逐小时在场 | planned | 是 |

## orders · 订单

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/admin/orders` | 本商家订单列表 | planned | 是 |
| GET | `/admin/stores` | 后台门店列表 | planned | 是 |
| GET | `/admin/orders/{orderId}` | 本商家订单详情 | planned | 是 |
| GET | `/admin/payments` | 查询后台收退款流水 | planned | 是 |
| GET | `/admin/visits` | 查询商家到店列表 | planned | 是 |
| GET | `/admin/visits/{visitId}/settlement` | 查询商家账单详情 | planned | 是 |
| GET | `/admin/customers/{userId}` | 商家顾客详情 | planned | 是 |
| POST | `/admin/orders/{orderId}/cancellation` | 商家处理故障取消 | planned | 是 |
| POST | `/admin/orders/{orderId}/manual-cancellation` | 后台人工取消预约 | planned | 是 |

## settlements · 结算

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/admin/review-tasks` | 本商家异常待办列表 | planned | 是 |
| GET | `/admin/orders/{orderId}` | 本商家订单详情 | planned | 是 |
| GET | `/admin/visits/{visitId}/settlement` | 查询商家账单详情 | planned | 是 |
| GET | `/admin/settlements` | 查询商家已结算账单 | planned | 是 |
| GET | `/admin/settlement-revisions` | 查询商家账单修订 | planned | 是 |
| GET | `/admin/review-tasks/{taskId}` | 待办详情 | planned | 是 |
| GET | `/admin/visits/{visitId}` | 查询后台到店详情 | planned | 是 |
| GET | `/admin/device-events` |  |  | 暂缓 |
| POST | `/admin/review-tasks/{taskId}/departure-confirmation` | 复核确认或修正离场时间 | planned | 是 |
| POST | `/admin/review-tasks/{taskId}/entry-confirmation` | 复核补记可靠的历史入场 | planned | 是 |
| POST | `/admin/review-tasks/{taskId}/settlement-decision` | 按证据快照复核结算 | planned | 是 |
| POST | `/admin/review-tasks/{taskId}/processing` | 开始复核处理 | planned | 是 |
| POST | `/admin/review-tasks/{taskId}/handoff` | 转交复核待办 | planned | 是 |

## stores · 门店

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/admin/stores` | 后台门店列表 | planned | 是 |
| GET | `/admin/stores/{storeId}` | 后台门店详情 | planned | 是 |
| GET | `/admin/stores/{storeId}/slots` | 后台时段列表 | planned | 是 |
| PATCH | `/admin/stores/{storeId}/slots/{slotId}` | 调整未售时段 | planned | 是 |
| PUT | `/admin/stores/{storeId}/capacity-schedule` | 替换门店小时容量模板 | planned | 是 |
| PUT | `/admin/stores/{storeId}/admission` | 暂停或恢复新准入 | planned | 是 |

## members · 会员

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/admin/customers` | 商家顾客列表 | planned | 是 |
| GET | `/admin/customers/{userId}` | 商家顾客详情 | planned | 是 |
| GET | `/admin/customers/{userId}/visits` | 某顾客在本商家的到店记录 | planned | 是 |
| GET | `/admin/member-cards` | 后台持有卡列表 | planned | 是 |
| GET | `/admin/member-cards/{cardId}/ledger` | 后台卡权益流水 | planned | 是 |

## products · 卡产品

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/admin/card-products` | 后台卡产品列表 | planned | 是 |
| GET | `/admin/card-products/{productId}` | 后台卡产品详情 | planned | 是 |
| POST | `/admin/card-products` | 创建卡产品 | planned | 是 |
| PUT | `/admin/card-products/{productId}` | 修改卡产品 | planned | 是 |
| PUT | `/admin/card-products/{productId}/status` | 卡产品上下架 | planned | 是 |

## devices · 设备

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/admin/devices` |  |  | 暂缓 |
| GET | `/admin/device-events` |  |  | 暂缓 |
| GET | `/admin/orders/{orderId}` | 本商家订单详情 | planned | 是 |

## credit · 信用

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/admin/merchant/credit` | 查询商家信用概况 | planned | 是 |
| GET | `/admin/merchant/credit-events` | 查询商家信用流水 | planned | 是 |

## settings · 设置

| 方法 | 路径 | 说明 | 状态 | 命中 |
|---|---|---|---|---|
| GET | `/admin/merchant` | 读取本商家信息 | implemented | 是 |
| GET | `/admin/staff` | 列出本商家员工 | planned | 是 |
| GET | `/admin/staff/{staffId}` | 读取员工详情 | planned | 是 |
| PUT | `/admin/staff/{staffId}/status` | 启停员工账号 | planned | 是 |
| DELETE | `/admin/staff/{staffId}` | 移除员工 | planned | 是 |
| PUT | `/admin/merchant/status` | 停用商家 | planned | 是 |

