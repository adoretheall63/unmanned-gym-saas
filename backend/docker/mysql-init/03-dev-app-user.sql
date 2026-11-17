-- 仅开发/测试环境：为 database/app_privileges.sql 创建的 gym_app 角色绑定本地开发账号。
-- 生产环境由数据库管理员按实际账号体系创建用户并绑定角色（app_privileges.sql 注释：不在该脚本创建用户/口令）。
--
-- 两个账号，故意分开：
--   gym_dev  绑定 gym_app 角色：普通表 SELECT/INSERT/UPDATE；
--            settlement_revision / card_ledger / credit_event / business_event 只有 SELECT/INSERT
--            —— 应用与集成测试用它，才能真的验证"只追加表不可改"。
--   gym_test 本地测试夹具账号：ALL PRIVILEGES，用于建库/清数据/重置 schema；
--            绝不能配置给应用（application.yml 只配 gym_dev）。
--
-- 连接必须带 time_zone=+00:00；库名固定 gym_saas（改库名需同步替换 app_privileges.sql 全部限定符）。

-- 应用账号：只拿角色，不给库级权限
CREATE USER IF NOT EXISTS 'gym_dev'@'%' IDENTIFIED BY 'dev_only_password';
CREATE USER IF NOT EXISTS 'gym_dev'@'localhost' IDENTIFIED BY 'dev_only_password';
GRANT 'gym_app' TO 'gym_dev'@'%';
GRANT 'gym_app' TO 'gym_dev'@'localhost';
SET DEFAULT ROLE 'gym_app' TO 'gym_dev'@'%';
SET DEFAULT ROLE 'gym_app' TO 'gym_dev'@'localhost';

-- 测试夹具账号：仅本机开发/测试使用
CREATE USER IF NOT EXISTS 'gym_test'@'%' IDENTIFIED BY 'test_only_password';
CREATE USER IF NOT EXISTS 'gym_test'@'localhost' IDENTIFIED BY 'test_only_password';
GRANT ALL PRIVILEGES ON `gym_saas`.* TO 'gym_test'@'%';
GRANT ALL PRIVILEGES ON `gym_saas`.* TO 'gym_test'@'localhost';

FLUSH PRIVILEGES;
