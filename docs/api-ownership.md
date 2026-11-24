# 接口归属表

按端分工，每人只改自己那摊。`状态` 为 `implemented` 表示 origin/main 已有 controller 映射。

共 132 个操作：mini-app 45，admin-web 76，backend 11。

## mini-app（45）

| 状态 | 方法 | 路径 | 说明 | 契约节 |
|---|---|---|---|---|
| planned | POST | `/api/v1/app/access-credentials/{credentialId}/secret` | 查看临时密码 | 8.11 |
| planned | GET | `/api/v1/app/access-points/{scanCode}` | 解析入口二维码 | 8.1 |
| planned | GET | `/api/v1/app/device-operations/{operationId}` | 查询本人开门结果 | 8.3 |
| implemented | GET | `/api/v1/app/me` | 读取本人资料 | 2.5 |
| implemented | PATCH | `/api/v1/app/me` | 修改本人昵称 | 2.6 |
| planned | GET | `/api/v1/app/me/billing-history` | 查询本人混合账单 | gap-11 |
| implemented | DELETE | `/api/v1/app/me/phone-binding` | 解除手机号绑定 | 2.8 |
| implemented | POST | `/api/v1/app/me/phone-binding` | 绑定已验证手机号 | 2.7 |
| planned | GET | `/api/v1/app/member-cards` | 本人持有卡列表 | 7.4 |
| planned | POST | `/api/v1/app/member-cards` | 创建购卡单 | 7.3 |
| planned | GET | `/api/v1/app/member-cards/{cardId}` | 本人持有卡详情 | 7.5 |
| planned | POST | `/api/v1/app/member-cards/{cardId}/cancellation` | 取消未付款购卡单 | 7.7 |
| planned | GET | `/api/v1/app/member-cards/{cardId}/ledger` | 本人卡权益流水 | 7.11 |
| planned | POST | `/api/v1/app/member-cards/{cardId}/mock-payment` | 模拟购卡支付 | 7.6 |
| planned | GET | `/api/v1/app/member-cards/{cardId}/refund-eligibility` | 查询退卡资格和依赖 | 7.8 |
| planned | GET | `/api/v1/app/member-cards/{cardId}/refund-request` | 查询退卡处理进度 | 7.10 |
| planned | POST | `/api/v1/app/member-cards/{cardId}/refund-request` | 提交退卡申请 | 7.9 |
| planned | GET | `/api/v1/app/merchant-relations` | 本人商家关系列表 | 5.1 |
| planned | GET | `/api/v1/app/merchants/{merchantId}/card-products` | 公开卡产品列表 | 7.1 |
| planned | GET | `/api/v1/app/merchants/{merchantId}/card-products/{productId}` | 公开卡产品详情 | 7.2 |
| planned | GET | `/api/v1/app/merchants/{merchantId}/credit-events` | 本人信用流水 | 5.3 |
| planned | GET | `/api/v1/app/merchants/{merchantId}/relationship` | 本人某商家的信用及欠费 | 5.2 |
| planned | GET | `/api/v1/app/orders` | 本人跨商家订单 | 6.2 |
| planned | POST | `/api/v1/app/orders` | 创建待支付预约 | 6.1 |
| planned | GET | `/api/v1/app/orders/{orderId}` | 本人订单详情 | 6.3 |
| planned | GET | `/api/v1/app/orders/{orderId}/access` | 查询订单连续通行状态 | 8.10 |
| planned | POST | `/api/v1/app/orders/{orderId}/benefit-confirmation` | 确认卡预约权益 | 6.8 |
| planned | POST | `/api/v1/app/orders/{orderId}/cancellation` | 申请取消预约 | 6.5 |
| planned | POST | `/api/v1/app/orders/{orderId}/mock-payment` | 模拟支付或余额支付预约费 | 6.4 |
| planned | POST | `/api/v1/app/orders/{orderId}/open-requests` | 申请订单开门 | 8.2 |
| planned | GET | `/api/v1/app/payments` | 查询本人收退款流水 | 9.4 |
| planned | GET | `/api/v1/app/payments/{paymentId}` | 查询本人收退款详情 | 9.5 |
| planned | GET | `/api/v1/app/stores` | 公开营业门店列表 | 4.1 |
| planned | GET | `/api/v1/app/stores/{storeId}` | 门店详情 | 4.2 |
| planned | GET | `/api/v1/app/stores/{storeId}/slots` | 查询某日时段 | 4.3 |
| planned | GET | `/api/v1/app/visits` | 查询本人到店记录 | 8.5 |
| planned | POST | `/api/v1/app/visits` | 本人确认入场 | 8.4 |
| planned | GET | `/api/v1/app/visits/current` | 查询门店当前到店 | 8.6 |
| planned | GET | `/api/v1/app/visits/{visitId}` | 查询本人到店详情 | 8.7 |
| planned | POST | `/api/v1/app/visits/{visitId}/finish` | 结束锻炼并结算 | 8.8 |
| planned | POST | `/api/v1/app/visits/{visitId}/mock-overtime-payment` | 模拟补缴超时欠费 | 9.2 |
| planned | GET | `/api/v1/app/visits/{visitId}/settlement` | 查询预估或最终账单 | 9.1 |
| planned | GET | `/api/v1/app/visits/{visitId}/settlement/revisions` | 查询本人账单修订历史 | 9.9 |
| planned | GET | `/api/v1/app/wallet` | 本人模拟钱包余额 | gap-5 |
| planned | GET | `/api/v1/app/wallet/ledger` | 本人模拟钱包流水 | gap-5 |

## admin-web（76）

| 状态 | 方法 | 路径 | 说明 | 契约节 |
|---|---|---|---|---|
| planned | GET | `/api/v1/admin/card-products` | 后台卡产品列表 | 7.12 |
| planned | POST | `/api/v1/admin/card-products` | 创建卡产品 | 7.14 |
| planned | GET | `/api/v1/admin/card-products/{productId}` | 后台卡产品详情 | 7.13 |
| planned | PUT | `/api/v1/admin/card-products/{productId}` | 修改卡产品 | 7.15 |
| planned | PUT | `/api/v1/admin/card-products/{productId}/status` | 卡产品上下架 | 7.16 |
| planned | POST | `/api/v1/admin/credit-events/{creditEventId}/reversal` | 冲正误判的爽约信用事件 | 5.10 |
| planned | GET | `/api/v1/admin/customers` | 商家顾客列表 | 5.4 |
| planned | GET | `/api/v1/admin/customers/{userId}` | 商家顾客详情 | 5.5 |
| planned | PUT | `/api/v1/admin/customers/{userId}/blacklist` | 设置或解除商家黑名单 | 5.8 |
| planned | POST | `/api/v1/admin/customers/{userId}/booking-restriction-release` | 提前解除信用预约限制 | 5.11 |
| planned | GET | `/api/v1/admin/customers/{userId}/credit-events` | 商家顾客信用流水 | 5.9 |
| planned | GET | `/api/v1/admin/customers/{userId}/orders` | 某顾客在本商家的订单 | 5.6 |
| planned | GET | `/api/v1/admin/customers/{userId}/visits` | 某顾客在本商家的到店记录 | 5.7 |
| planned | GET | `/api/v1/admin/dashboard` | 运营首页概览 | 15.1 |
| planned | GET | `/api/v1/admin/member-cards` | 后台持有卡列表 | 7.17 |
| planned | GET | `/api/v1/admin/member-cards/{cardId}` | 后台持有卡详情 | 7.18 |
| planned | POST | `/api/v1/admin/member-cards/{cardId}/count-compensation` | 补偿次卡次数 | 7.23 |
| planned | POST | `/api/v1/admin/member-cards/{cardId}/freeze` | 冻结持有卡的新预约权益 | 7.20 |
| planned | GET | `/api/v1/admin/member-cards/{cardId}/ledger` | 后台卡权益流水 | 7.19 |
| planned | POST | `/api/v1/admin/member-cards/{cardId}/refund-decision` | 处理退卡复核 | 7.22 |
| planned | POST | `/api/v1/admin/member-cards/{cardId}/unfreeze` | 解除持有卡人工冻结 | 7.21 |
| implemented | GET | `/api/v1/admin/merchant` | 读取本商家信息 | 3.1 |
| implemented | PATCH | `/api/v1/admin/merchant` | 修改本商家显示名称 | 3.2 |
| planned | GET | `/api/v1/admin/merchant/credit` | 查询商家信用概况 | gap-22 |
| planned | GET | `/api/v1/admin/merchant/credit-events` | 查询商家信用流水 | gap-23 |
| planned | PUT | `/api/v1/admin/merchant/status` | 停用商家 | gap-25 |
| planned | GET | `/api/v1/admin/orders` | 本商家订单列表 | 6.6 |
| planned | GET | `/api/v1/admin/orders/{orderId}` | 本商家订单详情 | 6.7 |
| planned | POST | `/api/v1/admin/orders/{orderId}/cancellation` | 商家处理故障取消 | 6.9 |
| planned | POST | `/api/v1/admin/orders/{orderId}/manual-cancellation` | 后台人工取消预约 | gap-16 |
| planned | POST | `/api/v1/admin/orders/{orderId}/refunds` | 商家退还预约费 | 6.10 |
| planned | GET | `/api/v1/admin/payments` | 查询后台收退款流水 | 9.6 |
| planned | GET | `/api/v1/admin/payments/{paymentId}` | 查询后台收退款详情 | 9.7 |
| planned | GET | `/api/v1/admin/reports/daily` | 查询自然日日报 | 15.2 |
| planned | POST | `/api/v1/admin/reports/daily/refresh` | 手动刷新指定日报 | 15.3 |
| planned | GET | `/api/v1/admin/reports/device-health` | 设备健康与异常日报 | 15.6 |
| planned | GET | `/api/v1/admin/reports/occupancy` | 逐店逐小时在场 | gap-15 |
| planned | GET | `/api/v1/admin/reports/traffic` | 分时客流统计 | 15.5 |
| planned | GET | `/api/v1/admin/reports/trends` | 日期区间营收与客流趋势 | 15.4 |
| planned | GET | `/api/v1/admin/review-tasks` | 本商家异常待办列表 | 14.1 |
| planned | GET | `/api/v1/admin/review-tasks/{taskId}` | 待办详情 | 14.2 |
| planned | POST | `/api/v1/admin/review-tasks/{taskId}/departure-confirmation` | 复核确认或修正离场时间 | 14.4 |
| planned | POST | `/api/v1/admin/review-tasks/{taskId}/entry-confirmation` | 复核补记可靠的历史入场 | 14.5 |
| planned | POST | `/api/v1/admin/review-tasks/{taskId}/event-association` | 为未匹配设备事件保存人工关联 | 14.6 |
| planned | POST | `/api/v1/admin/review-tasks/{taskId}/handoff` | 转交复核待办 | gap-20 |
| planned | POST | `/api/v1/admin/review-tasks/{taskId}/processing` | 开始复核处理 | 14.3 |
| planned | POST | `/api/v1/admin/review-tasks/{taskId}/resolution` | 记录异常处置结论并关闭待办 | 14.7 |
| planned | POST | `/api/v1/admin/review-tasks/{taskId}/settlement-decision` | 按证据快照复核结算 | gap-19 |
| planned | GET | `/api/v1/admin/settlement-revisions` | 查询商家账单修订 | gap-18 |
| planned | GET | `/api/v1/admin/settlements` | 查询商家已结算账单 | gap-17 |
| planned | GET | `/api/v1/admin/staff` | 列出本商家员工 | 3.3 |
| planned | POST | `/api/v1/admin/staff` | 创建员工 | 3.5 |
| planned | DELETE | `/api/v1/admin/staff/{staffId}` | 移除员工 | gap-24 |
| planned | GET | `/api/v1/admin/staff/{staffId}` | 读取员工详情 | 3.4 |
| planned | PATCH | `/api/v1/admin/staff/{staffId}` | 调整员工角色权限 | 3.6 |
| planned | POST | `/api/v1/admin/staff/{staffId}/password-reset` | 重置员工密码 | 3.8 |
| planned | PUT | `/api/v1/admin/staff/{staffId}/status` | 启停员工账号 | 3.7 |
| planned | GET | `/api/v1/admin/stores` | 后台门店列表 | 4.4 |
| planned | POST | `/api/v1/admin/stores` | 创建门店 | 4.6 |
| planned | GET | `/api/v1/admin/stores/{storeId}` | 后台门店详情 | 4.5 |
| planned | PATCH | `/api/v1/admin/stores/{storeId}` | 修改门店展示资料 | 4.7 |
| planned | PUT | `/api/v1/admin/stores/{storeId}/admission` | 暂停或恢复新准入 | 4.12 |
| planned | PUT | `/api/v1/admin/stores/{storeId}/capacity-schedule` | 替换门店小时容量模板 | gap-21 |
| planned | POST | `/api/v1/admin/stores/{storeId}/images` | 上传门店展示图片 | 4.8 |
| planned | POST | `/api/v1/admin/stores/{storeId}/rule-preview` | 预览营业与计费规则变更 | 4.9 |
| planned | PUT | `/api/v1/admin/stores/{storeId}/rules` | 应用营业与计费规则变更 | 4.10 |
| planned | POST | `/api/v1/admin/stores/{storeId}/slot-generation` | 补齐未来七天时段 | 4.14 |
| planned | GET | `/api/v1/admin/stores/{storeId}/slots` | 后台时段列表 | 4.13 |
| planned | PATCH | `/api/v1/admin/stores/{storeId}/slots/{slotId}` | 调整未售时段 | 4.15 |
| planned | PUT | `/api/v1/admin/stores/{storeId}/status` | 修改门店营业状态 | 4.11 |
| planned | GET | `/api/v1/admin/visits` | 查询商家到店列表 | 8.9 |
| planned | GET | `/api/v1/admin/visits/{visitId}` | 查询后台到店详情 | 8.12 |
| planned | GET | `/api/v1/admin/visits/{visitId}/settlement` | 查询商家账单详情 | 9.3 |
| planned | POST | `/api/v1/admin/visits/{visitId}/settlement/adjustments` | 人工调整超时账单 | 9.10 |
| planned | POST | `/api/v1/admin/visits/{visitId}/settlement/refunds` | 退还多付超时款 | 9.11 |
| planned | GET | `/api/v1/admin/visits/{visitId}/settlement/revisions` | 查询后台账单修订历史 | 9.8 |

## backend（11）

| 状态 | 方法 | 路径 | 说明 | 契约节 |
|---|---|---|---|---|
| implemented | GET | `/api/v1/admin/audit-events` | 操作审计列表 | 15.7 |
| implemented | GET | `/api/v1/admin/audit-events/{auditId}` | 操作审计详情 | 15.8 |
| implemented | POST | `/api/v1/admin/auth/login` | 后台登录 | 2.2 |
| implemented | POST | `/api/v1/admin/auth/logout` | 退出后台全部登录 | 2.4 |
| implemented | GET | `/api/v1/admin/me` | 读取后台登录身份 | 2.9 |
| implemented | POST | `/api/v1/admin/me/password` | 修改本人后台密码 | 2.10 |
| implemented | POST | `/api/v1/app/auth/logout` | 退出顾客全部登录 | 2.3 |
| planned | POST | `/api/v1/app/auth/phone-challenges` | 发起短信登录挑战 | gap-1 |
| planned | POST | `/api/v1/app/auth/phone-login` | 手机号登录 | gap-1 |
| implemented | POST | `/api/v1/app/auth/wechat-login` | 微信登录 | 2.1 |
| implemented | POST | `/api/v1/dev/auth/customer-login` | 开发测试顾客登录 | 16.1 |

