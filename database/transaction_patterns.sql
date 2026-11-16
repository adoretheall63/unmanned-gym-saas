-- v1.1：供Service/Mapper绑定命名参数的SQL片段，不是可整文件导入的初始化脚本。
-- :参数 必须使用预编译绑定，不做字符串拼接。每个name区段只是一条SQL。
-- 写事务保留REPEATABLE READ；资格、冲突、余额及状态一律当前读。
-- 外部设备网络调用在事务提交后执行，不持有数据库锁等待网络。

-- name: lock_customer
SELECT id,status,token_version FROM customer_user WHERE id=:user_id FOR UPDATE;

-- name: lock_merchant_customer
SELECT id,blocked,booking_restricted_until FROM merchant_customer
WHERE merchant_id=:merchant_id AND user_id=:user_id FOR UPDATE;

-- name: lock_store_config
SELECT id,merchant_id,status,capacity,slot_price_cent,overtime_price_cent,overtime_cap_cent
FROM store WHERE id=:store_id AND merchant_id=:merchant_id FOR SHARE;

-- name: lock_store_admission
SELECT id,merchant_id,status,capacity,admission_paused,message_health
FROM store WHERE id=:store_id AND merchant_id=:merchant_id FOR UPDATE;

-- name: lock_slot
SELECT id,start_at,end_at,capacity,reserved_count,status,price_cent FROM store_slot
WHERE id=:slot_id AND merchant_id=:merchant_id AND store_id=:store_id FOR UPDATE;

-- name: locked_now
SELECT UTC_TIMESTAMP(3) AS now_utc;

-- name: find_overlapping_orders
SELECT id,start_at,end_at FROM booking_order
WHERE user_id=:user_id AND end_at>:start_at AND start_at<:end_at
  AND status IN ('PENDING_PAYMENT','RESERVED','IN_USE','CANCEL_PENDING')
ORDER BY id FOR UPDATE;

-- name: hold_capacity
UPDATE store_slot SET reserved_count=reserved_count+1
WHERE id=:slot_id AND merchant_id=:merchant_id AND store_id=:store_id
  AND status='OPEN' AND reserved_count<capacity;

-- name: release_capacity
UPDATE store_slot SET reserved_count=reserved_count-1
WHERE id=:slot_id AND merchant_id=:merchant_id AND store_id=:store_id
  AND reserved_count>0;

-- name: lock_order
SELECT id,status,capacity_held,confirmed_at,pay_deadline,start_at,end_at,base_amount_cent
FROM booking_order WHERE id=:order_id AND merchant_id=:merchant_id AND user_id=:user_id FOR UPDATE;

-- name: confirm_order
UPDATE booking_order SET status='RESERVED',confirmed_at=:now_utc
WHERE id=:order_id AND merchant_id=:merchant_id AND user_id=:user_id
  AND status='PENDING_PAYMENT' AND capacity_held=1 AND pay_deadline>:now_utc;

-- name: cancel_unpaid_order
UPDATE booking_order SET status='CANCELLED',capacity_held=0,cancel_stage='COMPLETED',
    cancel_requested_at=:now_utc,cancelled_at=:now_utc,cancel_reason=:reason
WHERE id=:order_id AND merchant_id=:merchant_id AND user_id=:user_id
  AND status='PENDING_PAYMENT' AND capacity_held=1;

-- name: lock_current_visits
SELECT id,user_id,entry_at,authorized_end_at FROM visit_session
WHERE merchant_id=:merchant_id AND store_id=:store_id AND status='IN_STORE'
ORDER BY id FOR UPDATE;

-- name: lock_settlement
SELECT id,visit_id,version,status,overtime_amount_cent,adjustment_cent,paid_cent
FROM visit_settlement WHERE merchant_id=:merchant_id AND user_id=:user_id AND visit_id=:visit_id FOR UPDATE;

-- name: lock_receipt
SELECT id,order_id,visit_id,member_card_id,kind,status,amount_cent FROM payment_record
WHERE id=:payment_id AND merchant_id=:merchant_id AND user_id=:user_id FOR UPDATE;

-- name: lock_refunds
SELECT id,amount_cent FROM payment_record
WHERE merchant_id=:merchant_id AND user_id=:user_id AND refund_of_id=:payment_id
  AND kind='REFUND' AND status IN ('PENDING','SUCCEEDED') ORDER BY id FOR UPDATE;

-- name: lock_card
SELECT id,card_type,status,valid_from,valid_to,remaining_count FROM member_card
WHERE id=:card_id AND merchant_id=:merchant_id AND user_id=:user_id FOR UPDATE;

-- name: lock_card_origin
SELECT id,member_card_id,order_id,kind,count_delta FROM card_ledger
WHERE id=:ledger_id AND merchant_id=:merchant_id AND user_id=:user_id FOR SHARE;

-- name: lock_device
SELECT id,merchant_id,store_id,status,provider FROM device
WHERE id=:device_id AND merchant_id=:merchant_id AND store_id=:store_id FOR UPDATE;

-- name: lock_device_unresolved
SELECT id,action,status,attempts,lease_until FROM device_operation
WHERE device_id=:device_id AND status IN ('RUNNING','UNKNOWN') ORDER BY id FOR UPDATE;

-- name: lock_order_open_operations
SELECT id,status,executed_at,entry_visit_guard FROM device_operation
WHERE order_id=:order_id AND user_id=:user_id AND action='OPEN' ORDER BY id FOR UPDATE;

-- name: lock_ready_operation
SELECT id,action,credential_id,generation,attempts FROM device_operation
WHERE device_id=:device_id AND status='PENDING' AND next_attempt_at<=:now_utc
ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED;

-- name: claim_operation
UPDATE device_operation SET status='RUNNING',attempts=attempts+1,
    lease_until=TIMESTAMPADD(SECOND,:lease_seconds,:now_utc)
WHERE id=:operation_id AND device_id=:device_id AND status='PENDING' AND next_attempt_at<=:now_utc;

-- name: lock_credential_target
SELECT id,device_id,generation,observed_generation,observed_status FROM access_credential
WHERE id=:credential_id AND merchant_id=:merchant_id AND store_id=:store_id
  AND user_id=:user_id AND device_id=:device_id FOR UPDATE;

-- name: complete_operation
UPDATE device_operation SET status=:result_status,executed_at=:executed_at,received_at=:now_utc,
    result_summary=:result_summary,lease_until=NULL
WHERE id=:operation_id AND device_id=:device_id AND status='RUNNING'
  AND attempts=:claim_version AND generation<=>:expected_generation AND lease_until>:now_utc;

-- name: expire_operation_lease
UPDATE device_operation SET status='UNKNOWN',lease_until=NULL,error_code='LEASE_EXPIRED'
WHERE id=:operation_id AND status='RUNNING' AND attempts=:claim_version AND lease_until<=:now_utc;

-- name: claim_event
UPDATE device_event SET processing_status='PROCESSING',attempts=attempts+1,
    lease_until=TIMESTAMPADD(SECOND,:lease_seconds,:now_utc)
WHERE id=:event_id AND processing_status IN ('PENDING','FAILED') AND next_attempt_at<=:now_utc;

-- name: complete_event
UPDATE device_event SET processing_status='PROCESSED',processed_at=:now_utc,lease_until=NULL
WHERE id=:event_id AND processing_status='PROCESSING' AND attempts=:claim_version AND lease_until>:now_utc;

-- name: list_my_orders
SELECT id,order_no,merchant_id,store_id,start_at,end_at,status,base_amount_cent,created_at
FROM booking_order WHERE user_id=:user_id ORDER BY created_at DESC,id DESC LIMIT :page_size OFFSET :offset;

-- name: list_merchant_orders
SELECT id,order_no,user_id,store_id,start_at,end_at,status,base_amount_cent,created_at
FROM booking_order WHERE merchant_id=:merchant_id ORDER BY created_at DESC,id DESC LIMIT :page_size OFFSET :offset;

-- name: list_my_visits
SELECT id,visit_no,store_id,status,entry_at,exit_at FROM visit_session
WHERE user_id=:user_id ORDER BY entry_at DESC,id DESC LIMIT :page_size OFFSET :offset;

-- name: visit_net_payments
SELECT v.id,COALESCE(p.net_cent,0) AS overtime_paid_cent
FROM visit_session v LEFT JOIN (
    SELECT visit_id,SUM(CASE WHEN kind='RECEIPT' THEN amount_cent ELSE -amount_cent END) AS net_cent
    FROM payment_record WHERE merchant_id=:merchant_id AND visit_id IS NOT NULL AND status='SUCCEEDED'
    GROUP BY visit_id
) p ON p.visit_id=v.id WHERE v.merchant_id=:merchant_id AND v.id=:visit_id;

-- name: no_show_candidates
SELECT id,user_id,merchant_id,store_id FROM booking_order
WHERE status='NO_SHOW_PENDING' AND no_show_check_at<=:now_utc AND review_required=0
ORDER BY no_show_check_at,id LIMIT :batch_size;

-- name: cancel_pending_candidates
SELECT id,user_id,merchant_id,store_id FROM booking_order
WHERE status='CANCEL_PENDING' AND cancel_requested_at<=:escalation_before
ORDER BY cancel_requested_at,id LIMIT :batch_size;

-- name: expired_operation_candidates
SELECT id,device_id,attempts FROM device_operation
WHERE status='RUNNING' AND lease_until<=:now_utc ORDER BY lease_until,id LIMIT :batch_size;
