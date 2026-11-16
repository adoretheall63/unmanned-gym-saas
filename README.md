# 无人健身房 SaaS

面向校企合作课程的多商家、多门店无人健身房项目：顾客小程序预约、模拟支付、设备通行与到店结算，商家Web管理设备、卡权益、异常复核和日报。

当前是设计与SQL验证阶段，尚未建立Java后端、小程序或Web业务工程。静态界面不是可运行应用；Mock完成也不代表真机联调通过。

## 文档入口

| 文档 | 内容 |
|---|---|
| [业务规则](docs/业务规则.md) | Q009–Q104组内已确认，保留Q001–Q008原状态及课程验收差异 |
| [系统设计](docs/系统设计.md) | 三端技术栈、模块化单体、订单/到店/卡/设备状态与验收场景 |
| [数据库设计](docs/数据库设计.md) | 26表字段字典、陈氏ER总图和分图、索引、并发/事务及验证方法 |
| [完整接口文档](docs/接口文档.md) | 15模块、149个业务/管理/开发接口与1个厂商保留入口；参数、JSON、权限及实现分类 |
| [接口实现分类](docs/接口文档.md#implementation-classification) | 62基础CRUD、21复杂查询、46业务事务、17外部异步、4测试/回调入口 |
| [基础CRUD分工视图](docs/基础CRUD接口文档.md) | 62个基础接口的独立阅读视图，与完整接口编号一致 |
| [设备与部署](docs/设备与部署.md) | 资源盘点、四人分工、小程序AppID/体验版、HTTPS、Mock和萤石适配 |
| [界面设计](docs/ui/README.md) · [图稿目录](docs/ui/index.html) | 8张Web/小程序静态设计板及生成/修订提示词 |
| [课程原始方案](docs/reference/课程原始方案.md) · [规则确认快照](docs/reference/业务规则确认记录.docx) | 保留原始依据与已确认Word记录，勿与可编辑规范混用 |

## 技术选型

- 小程序：uni-app、Vue 3、JavaScript；商家Web：Vue 3、Vite、Element Plus。
- 后端：Java 21、Spring Boot模块化单体、Spring Security/JWT/BCrypt、MyBatis-Plus。
- 数据：MySQL 8.4/InnoDB，Redis只用于短期数据/限流等，不代替预约、资金和权益事实。
- 任务：Spring Scheduling + 数据库持久任务；设备使用Mock/真实适配器，视频后台按受限授权接入。
- 部署：Nginx、HTTPS、Docker Compose。暂不使用Spring Cloud或MQ，具体依赖版本建项时锁定。

## 目录

```text
api/                 首闭环OpenAPI v1.1与两份验证样例
database/            建表SQL、事务SQL、只追加权限及校验脚本
docs/                业务、系统、数据库、接口、设备与部署五份核心文档
  assets/er/         陈氏图PNG/SVG/DOT及关系核对数据
  ui/                静态设计板、说明、图片浏览目录和AI提示词证据
  reference/         课程原文及组内确认快照
  verification/      历史数据库验证记录
```

## 实现与验证边界

`database/schema.sql`仅初始化新空库，不是既有库迁移脚本。历史MySQL 8.4 SQL原型验证记录为26表、369列、55外键、141索引，见[记录](docs/verification/database-v1.1.json)；不能当作Java端到端已通过。复现命令见[数据库设计](docs/数据库设计.md#database-validation)。

`api/first-loop.openapi.yaml`仍只有首闭环30个操作，不是完整150入口的机器可读镜像。完整目标及卡预约扩展以接口文档为准，实施模块前同步OpenAPI。历史`visit_order`误关联的可追溯作废设计、真实设备回调和受限视频凭据仍待补齐，详见接口文档17.2–17.3。

后续先实现真实数据库的登录→门店时段→预约→模拟支付→Mock开门→到店→离场结算闭环，随后接卡权益和异常流程；设备账号、团队分工、真实AppID、服务器和最终验收以实际证据推进。

## 协作约定

默认分支`main`。按模块创建分支并通过PR合并；接口变更同步参数/响应、规则编号和受影响用例。不要提交.env、密钥、人脸、设备验证码、运行数据库或IDE个人配置；测试数据不得使用真实身份和设备秘密。个人AGENTS.md保留本机。

文档以本目录入口为准，不在文件名反复复制多个历史版；必要修订通过Git历史追踪。2026-11-16整理时的旧稿已在本机项目外备份，不纳入远程仓库；保留最新原始SQL验证记录，不改写成新的实测结果。
