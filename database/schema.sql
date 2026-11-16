-- schema v1.1，2026-11-12；目标 MySQL 8.4。仅导入新空库，不是已有库迁移。
-- 保留必要的商家隔离冗余、历史快照和可重建投影，不声明物理模型严格3NF。
-- Java Long / SQL BIGINT；API ID为1..9223372036854775807的字符串。UTC连接；金额整数分。
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET time_zone = '+00:00';

-- 商家；Q001 Q013–Q018
CREATE TABLE `merchant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `name` VARCHAR(100) NOT NULL COMMENT '商家显示名',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE DISABLED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  CONSTRAINT ck_merchant_1 CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='商家';

-- 全局顾客；Q009 Q010 Q016
CREATE TABLE `customer_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `wx_app_id` VARCHAR(64) NOT NULL COMMENT '微信AppID；开发身份使用独立命名空间',
  `openid` VARCHAR(128) NOT NULL COMMENT '后端换取的openid，不接受客户端指定',
  `nickname` VARCHAR(64) NULL COMMENT '昵称',
  `phone_ciphertext` VARCHAR(255) NULL COMMENT '手机号密文',
  `phone_verified_at` DATETIME(3) NULL COMMENT '验证时间，未验证为空',
  `token_version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '退出或禁用时递增',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE DISABLED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_customer_wx (wx_app_id,openid),
  CONSTRAINT ck_customer_user_1 CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='全局顾客';

-- 后台员工；Q014–Q016
CREATE TABLE `admin_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `username` VARCHAR(64) NOT NULL COMMENT '全局唯一登录名',
  `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt哈希',
  `role` VARCHAR(32) NOT NULL COMMENT 'ADMIN OPERATOR',
  `permissions` JSON NOT NULL COMMENT '操作员业务权限码数组，不允许自行扩权',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE DISABLED',
  `token_version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '登录版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_username (username),
  UNIQUE KEY uk_admin_user_m (merchant_id,id),
  CONSTRAINT ck_admin_user_1 CHECK (role IN ('ADMIN','OPERATOR')),
  CONSTRAINT ck_admin_user_2 CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='后台员工';

-- 商家顾客关系；Q013 Q017 Q018 Q066 Q077 Q078
CREATE TABLE `merchant_customer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `blocked` TINYINT NOT NULL DEFAULT 0 COMMENT '商家黑名单',
  `block_reason` VARCHAR(255) NULL COMMENT '最近拉黑或解除原因',
  `credit_score` INT NOT NULL DEFAULT 100 COMMENT '信用余额投影，可由信用流水重算',
  `booking_restricted_until` DATETIME(3) NULL COMMENT '爽约限制截止，黑名单另判断',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_mc_user (merchant_id,user_id),
  KEY ix_merchant_customer_fk_2 (user_id),
  CONSTRAINT ck_merchant_customer_1 CHECK (blocked IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='商家顾客关系';

-- 门店与运营规则；Q019–Q029 Q037 Q096 Q097
CREATE TABLE `store` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `name` VARCHAR(100) NOT NULL COMMENT '门店名称',
  `address` VARCHAR(255) NOT NULL COMMENT '地址',
  `latitude` DECIMAL(10,7) NULL COMMENT '纬度',
  `longitude` DECIMAL(10,7) NULL COMMENT '经度',
  `description` TEXT NULL COMMENT '器械与门店介绍',
  `images` JSON NOT NULL COMMENT '公开展示图片地址或对象键数组',
  `open_time` TIME NOT NULL COMMENT '北京时间营业起点',
  `close_time` TIME NOT NULL COMMENT '北京时间结束',
  `is_24h` TINYINT NOT NULL DEFAULT 0 COMMENT '全天时按自然日整点划段',
  `ends_next_day` TINYINT NOT NULL DEFAULT 0 COMMENT '结束在次日',
  `capacity` INT NOT NULL COMMENT '默认时段容量',
  `slot_price_cent` BIGINT NOT NULL DEFAULT 1000 COMMENT '每六十分钟默认售价，整数分',
  `overtime_price_cent` BIGINT NOT NULL DEFAULT 100 COMMENT '超时每分钟单价，整数分',
  `overtime_cap_cent` BIGINT NOT NULL DEFAULT 3000 COMMENT '连续使用超时封顶，整数分',
  `status` VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN CLOSED DISABLED',
  `admission_paused` TINYINT NOT NULL DEFAULT 0 COMMENT '暂停新入场',
  `pause_reason` VARCHAR(255) NULL COMMENT '暂停原因',
  `light_mode` VARCHAR(32) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO MANUAL',
  `light_generation` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '目标版本，新人或告警使旧关灯任务失效',
  `message_health` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'HEALTHY UNHEALTHY UNKNOWN',
  `last_human_alarm_at` DATETIME(3) NULL COMMENT '最后可靠人形告警',
  `last_message_observed_at` DATETIME(3) NULL COMMENT '消息链路最近观察时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_m (merchant_id,id),
  CONSTRAINT ck_store_1 CHECK (capacity>0),
  CONSTRAINT ck_store_2 CHECK (slot_price_cent>=0 AND overtime_price_cent>=0 AND overtime_cap_cent>=0),
  CONSTRAINT ck_store_3 CHECK (is_24h IN (0,1) AND ends_next_day IN (0,1) AND admission_paused IN (0,1)),
  CONSTRAINT ck_store_4 CHECK ((is_24h=1 AND ends_next_day=0) OR (is_24h=0 AND ((ends_next_day=0 AND close_time>open_time) OR (ends_next_day=1 AND close_time<open_time)))),
  CONSTRAINT ck_store_5 CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
  CONSTRAINT ck_store_6 CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
  CONSTRAINT ck_store_7 CHECK (status IN ('OPEN','CLOSED','DISABLED')),
  CONSTRAINT ck_store_8 CHECK (light_mode IN ('AUTO','MANUAL')),
  CONSTRAINT ck_store_9 CHECK (message_health IN ('HEALTHY','UNHEALTHY','UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='门店与运营规则';

-- 可预约时段；Q020–Q025 Q029 Q033 Q035
CREATE TABLE `store_slot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `store_id` BIGINT NOT NULL COMMENT '门店',
  `start_at` DATETIME(3) NOT NULL COMMENT 'UTC起点，包含',
  `end_at` DATETIME(3) NOT NULL COMMENT 'UTC终点，不含',
  `capacity` INT NOT NULL COMMENT '时段容量快照',
  `reserved_count` INT NOT NULL DEFAULT 0 COMMENT '未释放的承诺量，完成或爽约不释放',
  `price_cent` BIGINT NOT NULL COMMENT '时段售价快照，整数分',
  `status` VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN CLOSED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_slot_time (store_id,start_at,end_at),
  KEY ix_slot_sale (store_id,status,start_at),
  UNIQUE KEY uk_store_slot_s (merchant_id,store_id,id),
  CONSTRAINT ck_store_slot_1 CHECK (TIMESTAMPDIFF(MICROSECOND,start_at,end_at)=3600000000),
  CONSTRAINT ck_store_slot_2 CHECK (capacity>0 AND reserved_count>=0 AND reserved_count<=capacity),
  CONSTRAINT ck_store_slot_3 CHECK (price_cent>=0),
  CONSTRAINT ck_store_slot_4 CHECK (status IN ('OPEN','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='可预约时段';

-- 商家卡产品；Q079–Q084
CREATE TABLE `card_product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `name` VARCHAR(100) NOT NULL COMMENT '产品名称',
  `card_type` VARCHAR(32) NOT NULL COMMENT 'COUNT_CARD MONTH_CARD',
  `total_count` INT NULL COMMENT '次卡次数，月卡为空',
  `price_cent` BIGINT NOT NULL COMMENT '售价，整数分',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE OFF_SALE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_product_m (merchant_id,id),
  CONSTRAINT ck_card_product_1 CHECK (price_cent>=0),
  CONSTRAINT ck_card_product_2 CHECK ((card_type='COUNT_CARD' AND total_count IS NOT NULL AND total_count>0) OR (card_type='MONTH_CARD' AND total_count IS NULL)),
  CONSTRAINT ck_card_product_3 CHECK (status IN ('ON_SALE','OFF_SALE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='商家卡产品';

-- 用户持有卡与购买单；Q079–Q084
CREATE TABLE `member_card` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `product_id` BIGINT NOT NULL COMMENT '卡产品',
  `purchase_no` VARCHAR(64) NOT NULL COMMENT '卡购买业务号',
  `card_type` VARCHAR(32) NOT NULL COMMENT 'COUNT_CARD MONTH_CARD，已售快照',
  `product_snapshot` JSON NOT NULL COMMENT '名称、次数、售价、有效天数、规则版本',
  `purchase_amount_cent` BIGINT NOT NULL COMMENT '购买金额，整数分',
  `total_count` INT NULL COMMENT '初始次数，月卡为空',
  `remaining_count` INT NULL COMMENT '剩余次数，月卡为空',
  `valid_from` DATETIME(3) NULL COMMENT '确认购买立即生效',
  `valid_to` DATETIME(3) NULL COMMENT '有效截止，半开区间',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT 'PENDING_PAYMENT ACTIVE FROZEN REFUND_PENDING REFUNDED CANCELLED',
  `refund_requested_at` DATETIME(3) NULL COMMENT '退卡申请时间',
  `freeze_reason` VARCHAR(255) NULL COMMENT '冻结原因',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_purchase (purchase_no),
  KEY ix_card_owner (merchant_id,user_id,status,valid_to),
  UNIQUE KEY uk_member_card_mu (merchant_id,user_id,id),
  KEY ix_member_card_fk_4 (merchant_id,product_id),
  CONSTRAINT ck_member_card_1 CHECK (purchase_amount_cent>=0),
  CONSTRAINT ck_member_card_2 CHECK ((card_type='COUNT_CARD' AND total_count IS NOT NULL AND remaining_count IS NOT NULL AND total_count>0 AND remaining_count BETWEEN 0 AND total_count) OR (card_type='MONTH_CARD' AND total_count IS NULL AND remaining_count IS NULL)),
  CONSTRAINT ck_member_card_3 CHECK ((valid_from IS NULL AND valid_to IS NULL) OR (valid_from IS NOT NULL AND valid_to IS NOT NULL AND valid_to>valid_from)),
  CONSTRAINT ck_member_card_4 CHECK (status NOT IN ('ACTIVE','FROZEN','REFUND_PENDING','REFUNDED') OR (valid_from IS NOT NULL AND valid_to IS NOT NULL)),
  CONSTRAINT ck_member_card_5 CHECK (status IN ('PENDING_PAYMENT','ACTIVE','FROZEN','REFUND_PENDING','REFUNDED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='用户持有卡与购买单';

-- 预约订单；Q025 Q030–Q048 Q068–Q076
CREATE TABLE `booking_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `store_id` BIGINT NOT NULL COMMENT '门店',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `slot_id` BIGINT NOT NULL COMMENT '预约时段',
  `order_no` VARCHAR(64) NOT NULL COMMENT '业务订单号',
  `benefit_type` VARCHAR(32) NOT NULL COMMENT 'TIME_FEE COUNT_CARD MONTH_CARD',
  `member_card_id` BIGINT NULL COMMENT '卡权益引用，现金为空',
  `start_at` DATETIME(3) NOT NULL COMMENT '预约开始快照',
  `end_at` DATETIME(3) NOT NULL COMMENT '预约结束快照',
  `pay_deadline` DATETIME(3) NOT NULL COMMENT '严格早于截止才能确认',
  `base_amount_cent` BIGINT NOT NULL COMMENT '现金时段费，卡订单零，整数分',
  `overtime_price_cent` BIGINT NOT NULL COMMENT '分钟单价快照，整数分',
  `overtime_cap_cent` BIGINT NOT NULL COMMENT '封顶快照，整数分',
  `rule_snapshot` JSON NOT NULL COMMENT '版本、门店名、所选权益及其他规则',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT 'PENDING_PAYMENT RESERVED IN_USE FINISHED CANCEL_PENDING CANCELLED NO_SHOW_PENDING NO_SHOW',
  `capacity_held` TINYINT NOT NULL DEFAULT 1 COMMENT '承诺容量尚未释放',
  `confirmed_at` DATETIME(3) NULL COMMENT '支付或卡权益成功确认',
  `cancel_stage` VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'NONE REVOKE_PENDING RETURN_PENDING COMPLETED',
  `cancel_requested_at` DATETIME(3) NULL COMMENT '接受取消资格时间',
  `cancelled_at` DATETIME(3) NULL COMMENT '取消完成',
  `cancel_reason` VARCHAR(255) NULL COMMENT '取消原因',
  `no_show_check_at` DATETIME(3) NULL COMMENT '爽约补推检查时间',
  `review_required` TINYINT NOT NULL DEFAULT 0 COMMENT '未决证据暂停自动处理',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY ix_order_overlap (user_id,end_at,start_at,status),
  KEY ix_order_expire (status,pay_deadline),
  KEY ix_order_merchant (merchant_id,store_id,status,created_at),
  UNIQUE KEY uk_booking_order_mu (merchant_id,user_id,id),
  UNIQUE KEY uk_booking_order_su (merchant_id,store_id,user_id,id),
  KEY ix_order_user_created (user_id,created_at,id),
  KEY ix_order_merchant_created (merchant_id,created_at,id),
  KEY ix_order_cancel (status,cancel_requested_at,id),
  KEY ix_order_no_show (status,no_show_check_at,id),
  KEY ix_booking_order_fk_11 (merchant_id,store_id,slot_id),
  KEY ix_booking_order_fk_12 (merchant_id,user_id,member_card_id),
  CONSTRAINT ck_booking_order_1 CHECK (end_at>start_at AND pay_deadline<=start_at),
  CONSTRAINT ck_booking_order_2 CHECK (base_amount_cent>=0 AND overtime_price_cent>=0 AND overtime_cap_cent>=0),
  CONSTRAINT ck_booking_order_3 CHECK (capacity_held IN (0,1) AND review_required IN (0,1)),
  CONSTRAINT ck_booking_order_4 CHECK ((benefit_type='TIME_FEE' AND member_card_id IS NULL) OR (benefit_type IN ('COUNT_CARD','MONTH_CARD') AND member_card_id IS NOT NULL AND base_amount_cent=0)),
  CONSTRAINT ck_booking_order_5 CHECK (status<>'CANCELLED' OR capacity_held=0),
  CONSTRAINT ck_booking_order_6 CHECK (status IN ('PENDING_PAYMENT','RESERVED','IN_USE','FINISHED','CANCEL_PENDING','CANCELLED','NO_SHOW_PENDING','NO_SHOW')),
  CONSTRAINT ck_booking_order_7 CHECK (cancel_stage IN ('NONE','REVOKE_PENDING','RETURN_PENDING','COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='预约订单';

-- 一次实际到店；Q040–Q064 Q093–Q095 Q102
CREATE TABLE `visit_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `store_id` BIGINT NOT NULL COMMENT '门店',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `visit_no` VARCHAR(64) NOT NULL COMMENT '到店业务号',
  `entry_at` DATETIME(3) NOT NULL COMMENT '确认入场时间',
  `exit_at` DATETIME(3) NULL COMMENT '确认离场时间',
  `entry_source` VARCHAR(32) NOT NULL COMMENT 'DEVICE SELF_CONFIRM MERCHANT_REVIEW',
  `exit_source` VARCHAR(32) NULL COMMENT 'DEVICE SELF_CONFIRM MERCHANT_REVIEW',
  `authorized_end_at` DATETIME(3) NOT NULL COMMENT '连续通行末端快照',
  `last_order_id` BIGINT NOT NULL COMMENT '末段订单，决定超时规则',
  `status` VARCHAR(32) NOT NULL DEFAULT 'IN_STORE' COMMENT 'IN_STORE LEFT',
  `review_required` TINYINT NOT NULL DEFAULT 0 COMMENT '证据待复核',
  `active_user_guard` BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'IN_STORE' THEN user_id ELSE NULL END) STORED COMMENT '生成列，不写入；同店同用户至多一条在场',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_visit_no (visit_no),
  UNIQUE KEY uk_visit_active (store_id,active_user_guard),
  KEY ix_visit_occupancy (merchant_id,store_id,status),
  KEY ix_visit_user (user_id,entry_at,id),
  UNIQUE KEY uk_visit_session_mu (merchant_id,user_id,id),
  UNIQUE KEY uk_visit_session_su (merchant_id,store_id,user_id,id),
  KEY ix_visit_merchant_entry (merchant_id,entry_at,id),
  KEY ix_visit_session_fk_8 (merchant_id,store_id,user_id,last_order_id),
  CONSTRAINT ck_visit_session_1 CHECK ((status='IN_STORE' AND exit_at IS NULL AND exit_source IS NULL) OR (status='LEFT' AND exit_at IS NOT NULL AND exit_at>=entry_at AND exit_source IS NOT NULL)),
  CONSTRAINT ck_visit_session_2 CHECK (entry_source IN ('DEVICE','SELF_CONFIRM','MERCHANT_REVIEW')),
  CONSTRAINT ck_visit_session_3 CHECK (exit_source IS NULL OR exit_source IN ('DEVICE','SELF_CONFIRM','MERCHANT_REVIEW')),
  CONSTRAINT ck_visit_session_4 CHECK (authorized_end_at>entry_at),
  CONSTRAINT ck_visit_session_5 CHECK (review_required IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='一次实际到店';

-- 到店实际使用订单；Q040–Q048 Q102
CREATE TABLE `visit_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `store_id` BIGINT NOT NULL COMMENT '门店',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `visit_id` BIGINT NOT NULL COMMENT '一次到店',
  `order_id` BIGINT NOT NULL COMMENT '实际使用的订单',
  `used_from` DATETIME(3) NOT NULL COMMENT '实际覆盖该时段起点',
  `used_to` DATETIME(3) NULL COMMENT '实际覆盖终点，在用为空',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_visit_order_once (order_id),
  KEY ix_visit_order_fk_2 (merchant_id,store_id,user_id,visit_id),
  KEY ix_visit_order_fk_3 (merchant_id,store_id,user_id,order_id),
  CONSTRAINT ck_visit_order_1 CHECK (used_to IS NULL OR used_to>=used_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='到店实际使用订单';

-- 到店结算当前投影；Q061–Q067
CREATE TABLE `visit_settlement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `store_id` BIGINT NOT NULL COMMENT '门店',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `visit_id` BIGINT NOT NULL COMMENT '唯一对应到店',
  `version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '金额修订版本',
  `status` VARCHAR(32) NOT NULL DEFAULT 'NOT_READY' COMMENT 'NOT_READY REVIEW_REQUIRED PAYMENT_DUE SETTLED',
  `overtime_minutes` INT NOT NULL DEFAULT 0 COMMENT '向上取整分钟',
  `overtime_amount_cent` BIGINT NOT NULL DEFAULT 0 COMMENT '最终或预估超时费，整数分',
  `adjustment_cent` BIGINT NOT NULL DEFAULT 0 COMMENT '人工调整，可正可负，整数分',
  `paid_cent` BIGINT NOT NULL DEFAULT 0 COMMENT '超时净已付投影，整数分',
  `calculation_snapshot` JSON NOT NULL COMMENT '入场、离场、授权末端、末单规则、计算版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_settlement_visit (visit_id),
  UNIQUE KEY uk_visit_settlement_su (merchant_id,store_id,user_id,id),
  KEY ix_settlement_debt (merchant_id,user_id,status,visit_id),
  KEY ix_visit_settlement_fk_4 (merchant_id,store_id,user_id,visit_id),
  CONSTRAINT ck_visit_settlement_1 CHECK (overtime_minutes>=0 AND overtime_amount_cent>=0 AND paid_cent>=0),
  CONSTRAINT ck_visit_settlement_2 CHECK (overtime_amount_cent+adjustment_cent>=0),
  CONSTRAINT ck_visit_settlement_3 CHECK (status IN ('NOT_READY','REVIEW_REQUIRED','PAYMENT_DUE','SETTLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='到店结算当前投影';

-- 结算不可变修订；Q057 Q067 Q104
CREATE TABLE `settlement_revision` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `store_id` BIGINT NOT NULL COMMENT '门店',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `settlement_id` BIGINT NOT NULL COMMENT '结算',
  `version` INT UNSIGNED NOT NULL COMMENT '修订版本',
  `total_due_cent` BIGINT NOT NULL COMMENT '本版超时总应收，整数分',
  `calculation_snapshot` JSON NOT NULL COMMENT '完整计算参数和证据',
  `reason` VARCHAR(500) NOT NULL COMMENT '首次结算或修订原因',
  `admin_id` BIGINT NULL COMMENT '人工操作员，自动为空',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_settlement_revision (settlement_id,version),
  KEY ix_settlement_revision_fk_2 (merchant_id,store_id,user_id,settlement_id),
  KEY ix_settlement_revision_fk_3 (merchant_id,admin_id),
  CONSTRAINT ck_settlement_revision_1 CHECK (total_due_cent>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='结算不可变修订';

-- 现金收退款流水；Q036 Q065 Q067 Q074 Q083 Q101
CREATE TABLE `payment_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `payment_no` VARCHAR(64) NOT NULL COMMENT '流水业务号',
  `order_id` BIGINT NULL COMMENT '预约费目标',
  `visit_id` BIGINT NULL COMMENT '超时费目标',
  `member_card_id` BIGINT NULL COMMENT '卡购买或退款目标',
  `refund_of_id` BIGINT NULL COMMENT '退款关联原成功收款',
  `business_key` VARCHAR(128) NOT NULL COMMENT '同一收费项语义唯一键，不只是HTTP请求号',
  `kind` VARCHAR(32) NOT NULL COMMENT 'RECEIPT REFUND',
  `channel` VARCHAR(32) NOT NULL DEFAULT 'MOCK' COMMENT '首版仅MOCK',
  `amount_cent` BIGINT NOT NULL COMMENT '本笔正金额，方向由kind确定，整数分',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING SUCCEEDED FAILED',
  `completed_at` DATETIME(3) NULL COMMENT '成功时间，收退款报表依据',
  `failure_reason` VARCHAR(255) NULL COMMENT '失败原因',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_no (payment_no),
  UNIQUE KEY uk_payment_business (merchant_id,business_key),
  KEY ix_payment_report (merchant_id,status,completed_at),
  UNIQUE KEY uk_payment_record_mu (merchant_id,user_id,id),
  KEY ix_payment_record_fk_5 (merchant_id,user_id,order_id),
  KEY ix_payment_record_fk_6 (merchant_id,user_id,visit_id),
  KEY ix_payment_record_fk_7 (merchant_id,user_id,member_card_id),
  KEY ix_payment_record_fk_8 (merchant_id,user_id,refund_of_id),
  CONSTRAINT ck_payment_record_1 CHECK (amount_cent>0),
  CONSTRAINT ck_payment_record_2 CHECK ((order_id IS NOT NULL)+(visit_id IS NOT NULL)+(member_card_id IS NOT NULL)=1),
  CONSTRAINT ck_payment_record_3 CHECK ((kind='REFUND' AND refund_of_id IS NOT NULL) OR (kind='RECEIPT' AND refund_of_id IS NULL)),
  CONSTRAINT ck_payment_record_4 CHECK (status<>'SUCCEEDED' OR completed_at IS NOT NULL),
  CONSTRAINT ck_payment_record_5 CHECK (channel IN ('MOCK')),
  CONSTRAINT ck_payment_record_6 CHECK (kind IN ('RECEIPT','REFUND')),
  CONSTRAINT ck_payment_record_7 CHECK (status IN ('PENDING','SUCCEEDED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='现金收退款流水';

-- 卡权益流水；Q080–Q084
CREATE TABLE `card_ledger` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `member_card_id` BIGINT NOT NULL COMMENT '持有卡',
  `order_id` BIGINT NULL COMMENT '扣次或覆盖目标',
  `reversal_of_id` BIGINT NULL COMMENT '返次或释放的原记录',
  `business_key` VARCHAR(128) NOT NULL COMMENT '权益事件唯一键',
  `kind` VARCHAR(32) NOT NULL COMMENT 'DEBIT RETURN COVER RELEASE ADJUST',
  `count_delta` INT NOT NULL DEFAULT 0 COMMENT '扣次负一、返次正一、月卡零',
  `reason` VARCHAR(500) NOT NULL COMMENT '变动原因',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_ledger_business (merchant_id,business_key),
  UNIQUE KEY uk_card_ledger_reverse (reversal_of_id),
  UNIQUE KEY uk_card_ledger_ref_3 (merchant_id,user_id,member_card_id,order_id,id),
  KEY ix_card_ledger_fk_4 (merchant_id,user_id,member_card_id,order_id,reversal_of_id),
  KEY ix_card_ledger_fk_5 (merchant_id,user_id,order_id),
  CONSTRAINT ck_card_ledger_1 CHECK ((kind='DEBIT' AND count_delta=-1 AND order_id IS NOT NULL AND reversal_of_id IS NULL) OR (kind='RETURN' AND count_delta=1 AND order_id IS NOT NULL AND reversal_of_id IS NOT NULL) OR (kind='COVER' AND count_delta=0 AND order_id IS NOT NULL AND reversal_of_id IS NULL) OR (kind='RELEASE' AND count_delta=0 AND order_id IS NOT NULL AND reversal_of_id IS NOT NULL) OR (kind='ADJUST' AND count_delta<>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='卡权益流水';

-- 设备注册和观察状态；Q027 Q085–Q092 Q098 Q103
CREATE TABLE `device` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `store_id` BIGINT NOT NULL COMMENT '门店',
  `name` VARCHAR(100) NOT NULL COMMENT '设备名称',
  `device_type` VARCHAR(32) NOT NULL COMMENT 'ACCESS LOCK CAMERA LIGHT',
  `direction` VARCHAR(32) NOT NULL COMMENT 'ENTRY EXIT INDOOR ENVIRONMENT',
  `provider` VARCHAR(32) NOT NULL COMMENT 'MOCK EZVIZ',
  `provider_account_ref` VARCHAR(64) NOT NULL COMMENT '后端配置账号引用，不存平台密钥',
  `provider_device_id` VARCHAR(128) NOT NULL COMMENT '平台设备标识',
  `model` VARCHAR(128) NULL COMMENT '准确型号',
  `channel_no` INT NULL COMMENT '视频通道，按实测填写',
  `capabilities` JSON NOT NULL COMMENT '已验证能力及来源',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE DISABLED',
  `online_status` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'ONLINE OFFLINE UNKNOWN',
  `battery_percent` INT NULL COMMENT '不支持则为空',
  `observed_at` DATETIME(3) NULL COMMENT '最近观察时间',
  `scan_code` VARCHAR(64) NULL COMMENT '随机公开定位码，不是开门授权',
  `mock_scenario` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '仅MOCK设备使用的测试场景，不宣称真实设备能力',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_provider (provider,provider_account_ref,provider_device_id),
  UNIQUE KEY uk_device_scan (scan_code),
  UNIQUE KEY uk_device_s (merchant_id,store_id,id),
  CONSTRAINT ck_device_1 CHECK (battery_percent IS NULL OR battery_percent BETWEEN 0 AND 100),
  CONSTRAINT ck_device_2 CHECK (device_type IN ('ACCESS','LOCK','CAMERA','LIGHT')),
  CONSTRAINT ck_device_3 CHECK (direction IN ('ENTRY','EXIT','INDOOR','ENVIRONMENT')),
  CONSTRAINT ck_device_4 CHECK (provider IN ('MOCK','EZVIZ')),
  CONSTRAINT ck_device_5 CHECK (status IN ('ACTIVE','DISABLED')),
  CONSTRAINT ck_device_6 CHECK (online_status IN ('ONLINE','OFFLINE','UNKNOWN')),
  CONSTRAINT ck_device_7 CHECK (mock_scenario IN ('SUCCESS','FAILURE','EXECUTED_TIMEOUT','DUPLICATE_EVENT','DELAYED_EVENT','OFFLINE_RECOVERY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='设备注册和观察状态';

-- 设备稳定人员映射；Q087
CREATE TABLE `device_person` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `store_id` BIGINT NOT NULL COMMENT '门店',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `device_id` BIGINT NOT NULL COMMENT '设备',
  `provider_person_id` VARCHAR(128) NOT NULL COMMENT '设备内稳定人员标识',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_person_user (device_id,user_id),
  UNIQUE KEY uk_device_person_provider (device_id,provider_person_id),
  UNIQUE KEY uk_device_person_ref_3 (merchant_id,store_id,user_id,device_id,id),
  KEY ix_device_person_fk_4 (merchant_id,store_id,device_id),
  KEY ix_device_person_fk_5 (merchant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='设备稳定人员映射';

-- 共享通行凭证；Q047 Q071 Q085–Q089
CREATE TABLE `access_credential` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `store_id` BIGINT NOT NULL COMMENT '门店',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `device_id` BIGINT NOT NULL COMMENT '设备',
  `device_person_id` BIGINT NULL COMMENT '需人员注册时引用',
  `credential_type` VARCHAR(32) NOT NULL COMMENT 'TEMP_PASSWORD FACE QR',
  `generation` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '当前目标版本',
  `observed_generation` INT UNSIGNED NULL COMMENT '实际结果确认的版本',
  `observed_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_GRANTED' COMMENT 'NOT_GRANTED GRANTING ACTIVE REVOKING REVOKED FAILED UNKNOWN',
  `provider_credential_id` VARCHAR(128) NULL COMMENT '平台凭证标识',
  `secret_ciphertext` TEXT NULL COMMENT '临时密码密文，响应不缓存',
  `last_error` VARCHAR(255) NULL COMMENT '失败或未知原因',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_credential_user_type (device_id,user_id,credential_type),
  UNIQUE KEY uk_access_credential_su (merchant_id,store_id,user_id,id),
  UNIQUE KEY uk_access_credential_ref_3 (merchant_id,store_id,user_id,device_id,id),
  KEY ix_access_credential_fk_4 (merchant_id,store_id,user_id,device_id,device_person_id),
  KEY ix_access_credential_fk_5 (merchant_id,store_id,device_id),
  KEY ix_access_credential_fk_6 (merchant_id,user_id),
  CONSTRAINT ck_access_credential_1 CHECK (observed_generation IS NULL OR observed_generation<=generation),
  CONSTRAINT ck_access_credential_2 CHECK (credential_type IN ('TEMP_PASSWORD','FACE','QR')),
  CONSTRAINT ck_access_credential_3 CHECK (observed_status IN ('NOT_GRANTED','GRANTING','ACTIVE','REVOKING','REVOKED','FAILED','UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='共享通行凭证';

-- 凭证版本与有效区间；Q040 Q047 Q071 Q087
CREATE TABLE `credential_window` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `store_id` BIGINT NOT NULL COMMENT '门店',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `credential_id` BIGINT NOT NULL COMMENT '共享凭证',
  `generation` INT UNSIGNED NOT NULL COMMENT '目标版本',
  `valid_from` DATETIME(3) NOT NULL COMMENT '有效起点',
  `valid_to` DATETIME(3) NOT NULL COMMENT '有效终点，不含',
  `basis_order_ids` JSON NOT NULL COMMENT '依据订单ID字符串数组，解释快照，不代替订单事实',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_credential_window (credential_id,generation,valid_from,valid_to),
  KEY ix_credential_window_fk_2 (merchant_id,store_id,user_id,credential_id),
  CONSTRAINT ck_credential_window_1 CHECK (valid_to>valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='凭证版本与有效区间';

-- 设备命令与恢复任务；Q049–Q059 Q071 Q085–Q089 Q096
CREATE TABLE `device_operation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `store_id` BIGINT NOT NULL COMMENT '门店',
  `device_id` BIGINT NOT NULL COMMENT '设备',
  `user_id` BIGINT NULL COMMENT '目标用户，灯控可空',
  `order_id` BIGINT NULL COMMENT '关联订单',
  `visit_id` BIGINT NULL COMMENT '关联到店',
  `credential_id` BIGINT NULL COMMENT '关联凭证',
  `operation_no` VARCHAR(64) NOT NULL COMMENT '唯一命令号',
  `action` VARCHAR(32) NOT NULL COMMENT 'OPEN SYNC_CREDENTIAL LIGHT_ON LIGHT_OFF',
  `generation` INT UNSIGNED NULL COMMENT '凭证或灯控目标版本',
  `business_key` VARCHAR(128) NOT NULL COMMENT '语义幂等键',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING RUNNING SUCCEEDED FAILED UNKNOWN SUPERSEDED',
  `accepted_at` DATETIME(3) NULL COMMENT '平台仅接收，不代表执行',
  `executed_at` DATETIME(3) NULL COMMENT '可靠执行成功发生时间',
  `received_at` DATETIME(3) NULL COMMENT '本地收到结果',
  `attempts` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '每次领取递增的fencing版本，旧工作者写回必须匹配',
  `next_attempt_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次立即可执行；未来任务显式覆盖时间，UTC',
  `lease_until` DATETIME(3) NULL COMMENT '领取租约截止',
  `provider_command_id` VARCHAR(128) NULL COMMENT '厂商命令号',
  `request_summary` JSON NOT NULL COMMENT '脱敏摘要及Mock场景',
  `result_summary` JSON NULL COMMENT '脱敏结果',
  `error_code` VARCHAR(64) NULL COMMENT '错误码',
  `error_message` VARCHAR(255) NULL COMMENT '错误说明',
  `entry_visit_guard` BIGINT NULL COMMENT '该开门记录入场确认后关联到店，锁任务防重用',
  `open_order_guard` BIGINT GENERATED ALWAYS AS (CASE WHEN action='OPEN' AND status IN ('PENDING','RUNNING','UNKNOWN') THEN order_id ELSE NULL END) STORED COMMENT '生成列：同订单最多一个未决开门任务',
  `running_device_guard` BIGINT GENERATED ALWAYS AS (CASE WHEN status='RUNNING' THEN device_id ELSE NULL END) STORED COMMENT '生成列：同设备最多一个运行任务；未知结果仍需先核对',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_operation_no (operation_no),
  UNIQUE KEY uk_operation_business (merchant_id,business_key),
  KEY ix_operation_schedule (status,next_attempt_at,id),
  UNIQUE KEY uk_open_order_unresolved (open_order_guard),
  UNIQUE KEY uk_device_running (running_device_guard),
  KEY ix_operation_lease (status,lease_until,id),
  KEY ix_operation_device (device_id,status,id),
  KEY ix_operation_order (order_id,action,status,id),
  UNIQUE KEY uk_device_operation_ref_5 (merchant_id,store_id,device_id,id),
  KEY ix_device_operation_fk_10 (merchant_id,store_id,user_id,device_id,credential_id),
  KEY ix_device_operation_fk_11 (merchant_id,store_id,user_id,order_id),
  KEY ix_device_operation_fk_12 (merchant_id,store_id,user_id,visit_id),
  KEY ix_device_operation_fk_13 (merchant_id,store_id,user_id,entry_visit_guard),
  KEY ix_device_operation_fk_14 (merchant_id,user_id),
  CONSTRAINT ck_device_operation_1 CHECK (action IN ('OPEN','SYNC_CREDENTIAL','LIGHT_ON','LIGHT_OFF')),
  CONSTRAINT ck_device_operation_2 CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','UNKNOWN','SUPERSEDED')),
  CONSTRAINT ck_device_operation_3 CHECK (action<>'OPEN' OR (user_id IS NOT NULL AND order_id IS NOT NULL)),
  CONSTRAINT ck_device_operation_4 CHECK (status<>'SUCCEEDED' OR executed_at IS NOT NULL),
  CONSTRAINT ck_device_operation_5 CHECK ((order_id IS NULL AND visit_id IS NULL AND credential_id IS NULL AND entry_visit_guard IS NULL) OR user_id IS NOT NULL),
  CONSTRAINT ck_device_operation_6 CHECK (status<>'RUNNING' OR (lease_until IS NOT NULL AND attempts>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='设备命令与恢复任务';

-- 设备事件收件箱；Q049 Q052 Q057–Q059 Q075 Q098 Q103
CREATE TABLE `device_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `store_id` BIGINT NOT NULL COMMENT '门店',
  `device_id` BIGINT NOT NULL COMMENT '登记设备',
  `dedup_key` VARCHAR(191) NULL COMMENT '可靠ID或经过验证的指纹，无可靠依据可空',
  `event_type` VARCHAR(64) NOT NULL COMMENT '入口、出口、人形、在线、执行结果等',
  `occurred_at` DATETIME(3) NOT NULL COMMENT '原事件发生时间，可信程度另记录',
  `received_at` DATETIME(3) NOT NULL COMMENT '本地接收时间',
  `identity_reliable` TINYINT NOT NULL DEFAULT 0 COMMENT '身份和方向是否可靠',
  `payload_summary` JSON NOT NULL COMMENT '脱敏事件及时间可信性',
  `matched_user_id` BIGINT NULL COMMENT '匹配顾客',
  `matched_order_id` BIGINT NULL COMMENT '匹配订单',
  `operation_id` BIGINT NULL COMMENT '匹配命令',
  `processing_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING PROCESSING PROCESSED UNMATCHED FAILED',
  `attempts` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '每次领取递增的fencing版本，旧工作者写回必须匹配',
  `next_attempt_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次立即可执行；未来任务显式覆盖时间，UTC',
  `lease_until` DATETIME(3) NULL COMMENT '处理租约',
  `processed_at` DATETIME(3) NULL COMMENT '处理完成',
  `error_message` VARCHAR(255) NULL COMMENT '无法匹配或失败说明',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_event (device_id,dedup_key),
  KEY ix_event_schedule (processing_status,next_attempt_at,id),
  KEY ix_event_report (store_id,occurred_at),
  UNIQUE KEY uk_device_event_m (merchant_id,id),
  KEY ix_event_lease (processing_status,lease_until,id),
  KEY ix_device_event_fk_6 (merchant_id,store_id,matched_user_id,matched_order_id),
  KEY ix_device_event_fk_7 (merchant_id,store_id,device_id,operation_id),
  KEY ix_device_event_fk_8 (merchant_id,matched_user_id),
  CONSTRAINT ck_device_event_1 CHECK (identity_reliable IN (0,1)),
  CONSTRAINT ck_device_event_2 CHECK (matched_order_id IS NULL OR matched_user_id IS NOT NULL),
  CONSTRAINT ck_device_event_3 CHECK (processing_status IN ('PENDING','PROCESSING','PROCESSED','UNMATCHED','FAILED')),
  CONSTRAINT ck_device_event_4 CHECK (processing_status<>'PROCESSING' OR (lease_until IS NOT NULL AND attempts>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='设备事件收件箱';

-- 业务时间线与审计；Q057 Q067 Q078 Q104
CREATE TABLE `business_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `event_type` VARCHAR(64) NOT NULL COMMENT '业务事件类型',
  `target_type` VARCHAR(32) NOT NULL COMMENT 'ORDER VISIT CARD STORE DEVICE等',
  `target_id` BIGINT NOT NULL COMMENT '多态目标，服务端校验归属',
  `user_id` BIGINT NULL COMMENT '相关顾客',
  `admin_id` BIGINT NULL COMMENT '操作员工',
  `device_event_id` BIGINT NULL COMMENT '原设备证据',
  `actor_type` VARCHAR(32) NOT NULL COMMENT 'CUSTOMER ADMIN SYSTEM DEVICE',
  `request_id` VARCHAR(64) NULL COMMENT '链路追踪标识',
  `reason` VARCHAR(500) NULL COMMENT '人工操作必填原因',
  `detail` JSON NOT NULL COMMENT '脱敏前后状态、版本与证据',
  `occurred_at` DATETIME(3) NOT NULL COMMENT '事实发生时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  PRIMARY KEY (id),
  KEY ix_timeline (merchant_id,target_type,target_id,occurred_at),
  KEY ix_business_event_fk_2 (merchant_id,user_id),
  KEY ix_business_event_fk_3 (merchant_id,admin_id),
  KEY ix_business_event_fk_4 (merchant_id,device_event_id),
  CONSTRAINT ck_business_event_1 CHECK (actor_type IN ('CUSTOMER','ADMIN','SYSTEM','DEVICE')),
  CONSTRAINT ck_business_event_2 CHECK (actor_type<>'ADMIN' OR (admin_id IS NOT NULL AND reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='业务时间线与审计';

-- 异常待办和复核；Q057 Q060 Q072 Q073 Q078 Q093–Q099
CREATE TABLE `review_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `store_id` BIGINT NULL COMMENT '相关门店',
  `user_id` BIGINT NULL COMMENT '相关用户',
  `order_id` BIGINT NULL COMMENT '相关订单',
  `visit_id` BIGINT NULL COMMENT '相关到店',
  `member_card_id` BIGINT NULL COMMENT '相关卡',
  `category` VARCHAR(64) NOT NULL COMMENT '撤权未知、疑似离场、闯入、灯控、退款等',
  `business_key` VARCHAR(128) NOT NULL COMMENT '同一异常实例唯一键',
  `status` VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN PROCESSING RESOLVED',
  `evidence` JSON NOT NULL COMMENT '证据、告警、观察区间',
  `resolution` VARCHAR(500) NULL COMMENT '处理结论和理由',
  `resolved_by` BIGINT NULL COMMENT '处理员工',
  `resolved_at` DATETIME(3) NULL COMMENT '处理时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_business (merchant_id,business_key),
  KEY ix_review_queue (merchant_id,status,created_at),
  KEY ix_review_created (merchant_id,created_at,id),
  KEY ix_review_task_fk_4 (merchant_id,user_id,order_id),
  KEY ix_review_task_fk_5 (merchant_id,user_id,visit_id),
  KEY ix_review_task_fk_6 (merchant_id,user_id,member_card_id),
  KEY ix_review_task_fk_7 (merchant_id,store_id),
  KEY ix_review_task_fk_8 (merchant_id,resolved_by),
  CONSTRAINT ck_review_task_1 CHECK ((order_id IS NULL AND visit_id IS NULL AND member_card_id IS NULL) OR user_id IS NOT NULL),
  CONSTRAINT ck_review_task_2 CHECK (status<>'RESOLVED' OR (resolution IS NOT NULL AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)),
  CONSTRAINT ck_review_task_3 CHECK (status IN ('OPEN','PROCESSING','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='异常待办和复核';

-- 爽约信用和冲正；Q075–Q078
CREATE TABLE `credit_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `order_id` BIGINT NOT NULL COMMENT '相关订单',
  `business_key` VARCHAR(128) NOT NULL COMMENT '确认或冲正唯一键',
  `score_delta` INT NOT NULL COMMENT '扣分负数，冲正正数',
  `reversal_of_id` BIGINT NULL COMMENT '原信用流水',
  `reason` VARCHAR(500) NOT NULL COMMENT '原因',
  `occurred_at` DATETIME(3) NOT NULL COMMENT '最近三十天确认窗口依据',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_credit_business (merchant_id,business_key),
  UNIQUE KEY uk_credit_reverse (reversal_of_id),
  KEY ix_credit_window (merchant_id,user_id,occurred_at),
  UNIQUE KEY uk_credit_event_ref_3 (merchant_id,user_id,order_id,id),
  KEY ix_credit_event_fk_5 (merchant_id,user_id,order_id,reversal_of_id),
  CONSTRAINT ck_credit_event_1 CHECK (score_delta<>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='爽约信用和冲正';

-- 持久化写请求幂等；Q035 Q059 Q074
CREATE TABLE `api_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `principal_type` VARCHAR(16) NOT NULL COMMENT 'CUSTOMER ADMIN或受控DEV',
  `principal_id` BIGINT NOT NULL COMMENT '已验证主体ID',
  `operation` VARCHAR(128) NOT NULL COMMENT 'operationId与目标资源ID组合',
  `idempotency_key` VARCHAR(64) NOT NULL COMMENT '每次逻辑动作唯一键',
  `request_hash` CHAR(64) NOT NULL COMMENT '规范化方法、路径及请求体SHA256',
  `resource_type` VARCHAR(32) NULL COMMENT '结果资源类型',
  `resource_id` BIGINT NULL COMMENT '结果资源ID',
  `http_status` SMALLINT UNSIGNED NULL COMMENT '首次成功响应码',
  `result_snapshot` JSON NULL COMMENT '可重放脱敏结果，不存JWT或临时密码',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_api_idempotency (principal_type,principal_id,operation,idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='持久化写请求幂等';

-- 可选人脸资料；Q011 Q012 Q092
CREATE TABLE `face_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；API返回字符串',
  `merchant_id` BIGINT NOT NULL COMMENT '商家',
  `user_id` BIGINT NOT NULL COMMENT '全局顾客',
  `object_key` VARCHAR(255) NOT NULL COMMENT '私有存储键，不是公开URL',
  `consent_version` VARCHAR(64) NOT NULL COMMENT '单独授权版本',
  `consent_at` DATETIME(3) NOT NULL COMMENT '同意时间',
  `expires_at` DATETIME(3) NOT NULL COMMENT '保存截止',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE DELETE_PENDING DELETED',
  `deleted_at` DATETIME(3) NULL COMMENT '先撤权后完成文件删除',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC时间',
  PRIMARY KEY (id),
  KEY ix_face_expire (status,expires_at),
  KEY ix_face_profile_fk_2 (merchant_id,user_id),
  CONSTRAINT ck_face_profile_1 CHECK (status IN ('ACTIVE','DELETE_PENDING','DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='可选人脸资料';

ALTER TABLE `admin_user` ADD CONSTRAINT fk_admin_user_1 FOREIGN KEY (merchant_id) REFERENCES `merchant` (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `merchant_customer` ADD CONSTRAINT fk_merchant_customer_1 FOREIGN KEY (merchant_id) REFERENCES `merchant` (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `merchant_customer` ADD CONSTRAINT fk_merchant_customer_2 FOREIGN KEY (user_id) REFERENCES `customer_user` (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `store` ADD CONSTRAINT fk_store_1 FOREIGN KEY (merchant_id) REFERENCES `merchant` (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `store_slot` ADD CONSTRAINT fk_store_slot_1 FOREIGN KEY (merchant_id,store_id) REFERENCES `store` (merchant_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `card_product` ADD CONSTRAINT fk_card_product_1 FOREIGN KEY (merchant_id) REFERENCES `merchant` (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `member_card` ADD CONSTRAINT fk_member_card_1 FOREIGN KEY (merchant_id,product_id) REFERENCES `card_product` (merchant_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `member_card` ADD CONSTRAINT fk_member_card_2 FOREIGN KEY (merchant_id,user_id) REFERENCES `merchant_customer` (merchant_id,user_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `booking_order` ADD CONSTRAINT fk_booking_order_1 FOREIGN KEY (merchant_id,store_id,slot_id) REFERENCES `store_slot` (merchant_id,store_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `booking_order` ADD CONSTRAINT fk_booking_order_2 FOREIGN KEY (merchant_id,user_id,member_card_id) REFERENCES `member_card` (merchant_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `booking_order` ADD CONSTRAINT fk_booking_order_3 FOREIGN KEY (merchant_id,user_id) REFERENCES `merchant_customer` (merchant_id,user_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `visit_session` ADD CONSTRAINT fk_visit_session_1 FOREIGN KEY (merchant_id,store_id,user_id,last_order_id) REFERENCES `booking_order` (merchant_id,store_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `visit_order` ADD CONSTRAINT fk_visit_order_1 FOREIGN KEY (merchant_id,store_id,user_id,visit_id) REFERENCES `visit_session` (merchant_id,store_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `visit_order` ADD CONSTRAINT fk_visit_order_2 FOREIGN KEY (merchant_id,store_id,user_id,order_id) REFERENCES `booking_order` (merchant_id,store_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `visit_settlement` ADD CONSTRAINT fk_visit_settlement_1 FOREIGN KEY (merchant_id,store_id,user_id,visit_id) REFERENCES `visit_session` (merchant_id,store_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `settlement_revision` ADD CONSTRAINT fk_settlement_revision_1 FOREIGN KEY (merchant_id,store_id,user_id,settlement_id) REFERENCES `visit_settlement` (merchant_id,store_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `settlement_revision` ADD CONSTRAINT fk_settlement_revision_2 FOREIGN KEY (merchant_id,admin_id) REFERENCES `admin_user` (merchant_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `payment_record` ADD CONSTRAINT fk_payment_record_1 FOREIGN KEY (merchant_id,user_id,order_id) REFERENCES `booking_order` (merchant_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `payment_record` ADD CONSTRAINT fk_payment_record_2 FOREIGN KEY (merchant_id,user_id,visit_id) REFERENCES `visit_session` (merchant_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `payment_record` ADD CONSTRAINT fk_payment_record_3 FOREIGN KEY (merchant_id,user_id,member_card_id) REFERENCES `member_card` (merchant_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `payment_record` ADD CONSTRAINT fk_payment_record_4 FOREIGN KEY (merchant_id,user_id,refund_of_id) REFERENCES `payment_record` (merchant_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `card_ledger` ADD CONSTRAINT fk_card_ledger_1 FOREIGN KEY (merchant_id,user_id,member_card_id) REFERENCES `member_card` (merchant_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `card_ledger` ADD CONSTRAINT fk_card_ledger_2 FOREIGN KEY (merchant_id,user_id,order_id) REFERENCES `booking_order` (merchant_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `card_ledger` ADD CONSTRAINT fk_card_ledger_3 FOREIGN KEY (merchant_id,user_id,member_card_id,order_id,reversal_of_id) REFERENCES `card_ledger` (merchant_id,user_id,member_card_id,order_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `device` ADD CONSTRAINT fk_device_1 FOREIGN KEY (merchant_id,store_id) REFERENCES `store` (merchant_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `device_person` ADD CONSTRAINT fk_device_person_1 FOREIGN KEY (merchant_id,store_id,device_id) REFERENCES `device` (merchant_id,store_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `device_person` ADD CONSTRAINT fk_device_person_2 FOREIGN KEY (merchant_id,user_id) REFERENCES `merchant_customer` (merchant_id,user_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `access_credential` ADD CONSTRAINT fk_access_credential_1 FOREIGN KEY (merchant_id,store_id,device_id) REFERENCES `device` (merchant_id,store_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `access_credential` ADD CONSTRAINT fk_access_credential_2 FOREIGN KEY (merchant_id,store_id,user_id,device_id,device_person_id) REFERENCES `device_person` (merchant_id,store_id,user_id,device_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `access_credential` ADD CONSTRAINT fk_access_credential_3 FOREIGN KEY (merchant_id,user_id) REFERENCES `merchant_customer` (merchant_id,user_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `credential_window` ADD CONSTRAINT fk_credential_window_1 FOREIGN KEY (merchant_id,store_id,user_id,credential_id) REFERENCES `access_credential` (merchant_id,store_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `device_operation` ADD CONSTRAINT fk_device_operation_1 FOREIGN KEY (merchant_id,user_id) REFERENCES `merchant_customer` (merchant_id,user_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `device_operation` ADD CONSTRAINT fk_device_operation_2 FOREIGN KEY (merchant_id,store_id,device_id) REFERENCES `device` (merchant_id,store_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `device_operation` ADD CONSTRAINT fk_device_operation_3 FOREIGN KEY (merchant_id,store_id,user_id,order_id) REFERENCES `booking_order` (merchant_id,store_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `device_operation` ADD CONSTRAINT fk_device_operation_4 FOREIGN KEY (merchant_id,store_id,user_id,visit_id) REFERENCES `visit_session` (merchant_id,store_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `device_operation` ADD CONSTRAINT fk_device_operation_5 FOREIGN KEY (merchant_id,store_id,user_id,device_id,credential_id) REFERENCES `access_credential` (merchant_id,store_id,user_id,device_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `device_operation` ADD CONSTRAINT fk_device_operation_6 FOREIGN KEY (merchant_id,store_id,user_id,entry_visit_guard) REFERENCES `visit_session` (merchant_id,store_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `device_event` ADD CONSTRAINT fk_device_event_1 FOREIGN KEY (merchant_id,store_id,device_id) REFERENCES `device` (merchant_id,store_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `device_event` ADD CONSTRAINT fk_device_event_2 FOREIGN KEY (merchant_id,matched_user_id) REFERENCES `merchant_customer` (merchant_id,user_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `device_event` ADD CONSTRAINT fk_device_event_3 FOREIGN KEY (merchant_id,store_id,matched_user_id,matched_order_id) REFERENCES `booking_order` (merchant_id,store_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `device_event` ADD CONSTRAINT fk_device_event_4 FOREIGN KEY (merchant_id,store_id,device_id,operation_id) REFERENCES `device_operation` (merchant_id,store_id,device_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `business_event` ADD CONSTRAINT fk_business_event_1 FOREIGN KEY (merchant_id,user_id) REFERENCES `merchant_customer` (merchant_id,user_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `business_event` ADD CONSTRAINT fk_business_event_2 FOREIGN KEY (merchant_id,admin_id) REFERENCES `admin_user` (merchant_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `business_event` ADD CONSTRAINT fk_business_event_3 FOREIGN KEY (merchant_id,device_event_id) REFERENCES `device_event` (merchant_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `business_event` ADD CONSTRAINT fk_business_event_4 FOREIGN KEY (merchant_id) REFERENCES `merchant` (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `review_task` ADD CONSTRAINT fk_review_task_1 FOREIGN KEY (merchant_id,store_id) REFERENCES `store` (merchant_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `review_task` ADD CONSTRAINT fk_review_task_2 FOREIGN KEY (merchant_id,user_id) REFERENCES `merchant_customer` (merchant_id,user_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `review_task` ADD CONSTRAINT fk_review_task_3 FOREIGN KEY (merchant_id,user_id,order_id) REFERENCES `booking_order` (merchant_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `review_task` ADD CONSTRAINT fk_review_task_4 FOREIGN KEY (merchant_id,user_id,visit_id) REFERENCES `visit_session` (merchant_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `review_task` ADD CONSTRAINT fk_review_task_5 FOREIGN KEY (merchant_id,user_id,member_card_id) REFERENCES `member_card` (merchant_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `review_task` ADD CONSTRAINT fk_review_task_6 FOREIGN KEY (merchant_id,resolved_by) REFERENCES `admin_user` (merchant_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `review_task` ADD CONSTRAINT fk_review_task_7 FOREIGN KEY (merchant_id) REFERENCES `merchant` (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `credit_event` ADD CONSTRAINT fk_credit_event_1 FOREIGN KEY (merchant_id,user_id,order_id) REFERENCES `booking_order` (merchant_id,user_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `credit_event` ADD CONSTRAINT fk_credit_event_2 FOREIGN KEY (merchant_id,user_id,order_id,reversal_of_id) REFERENCES `credit_event` (merchant_id,user_id,order_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `face_profile` ADD CONSTRAINT fk_face_profile_1 FOREIGN KEY (merchant_id,user_id) REFERENCES `merchant_customer` (merchant_id,user_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
