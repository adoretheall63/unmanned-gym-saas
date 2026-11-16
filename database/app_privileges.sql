-- v1.1：由数据库管理员在初始化后执行。默认库名gym_saas，改库名须同步替换所有限定符。
-- 创建新专用角色，不给已有应用账号隐式提权；不在本脚本创建用户、口令或绑定角色。
CREATE ROLE IF NOT EXISTS 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`merchant` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`customer_user` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`admin_user` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`merchant_customer` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`store` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`store_slot` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`card_product` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`member_card` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`booking_order` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`visit_session` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`visit_order` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`visit_settlement` TO 'gym_app';
GRANT SELECT, INSERT ON `gym_saas`.`settlement_revision` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`payment_record` TO 'gym_app';
GRANT SELECT, INSERT ON `gym_saas`.`card_ledger` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`device` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`device_person` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`access_credential` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`credential_window` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`device_operation` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`device_event` TO 'gym_app';
GRANT SELECT, INSERT ON `gym_saas`.`business_event` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`review_task` TO 'gym_app';
GRANT SELECT, INSERT ON `gym_saas`.`credit_event` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`api_request` TO 'gym_app';
GRANT SELECT, INSERT, UPDATE ON `gym_saas`.`face_profile` TO 'gym_app';
-- 管理员另行将此角色绑定专用应用用户并设置默认角色；勿再赋予库级UPDATE/DELETE或其他高权限角色。
