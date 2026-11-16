"""Multi-connection SQL contract tests; no business server or real device calls."""
import concurrent.futures
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path
import re
import subprocess
import threading
import uuid

ROOT = Path(__file__).resolve().parents[1]


def patterns():
    source = (ROOT / 'database/transaction_patterns.sql').read_text()
    return {name: body.strip() for name, body in re.findall(
        r'^-- name: (\w+)\n(.*?)(?=\n-- name: |\Z)', source, re.M | re.S)}


def bind(source, **params):
    # Tests use constants only; production must use prepared bindings instead.
    def literal(match):
        value = params[match[1]]
        if value is None:
            return 'NULL'
        if isinstance(value, int):
            return str(value)
        return "'" + str(value).replace("'", "''") + "'"
    return re.sub(r':([a-z_]+)', literal, source)


@contextmanager
def session(cli, db):
    proc = subprocess.Popen(cli + ['--unbuffered', db], stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1)
    def send(query):
        marker = 'done_' + uuid.uuid4().hex
        proc.stdin.write(query + f"\nSELECT '{marker}';\n")
        proc.stdin.flush()
        rows = []
        while True:
            line = proc.stdout.readline()
            if not line:
                raise AssertionError(proc.stderr.read())
            if line.strip() == marker:
                return rows
            rows.append(line.rstrip('\n'))
    try:
        send("SET time_zone='+00:00'; SET SESSION innodb_lock_wait_timeout=5;")
        yield send
    finally:
        if proc.poll() is None:
            proc.stdin.write('ROLLBACK;\n')
            proc.stdin.flush()
            _, error = proc.communicate(timeout=10)
            assert proc.returncode == 0, error


def verify(run, cli, db):
    q = patterns()
    report = {'checks': [], 'query_plans': {}}
    def passed(name):
        report['checks'].append(name)
        print('PASS transaction:', name)
    def stmt(name, **params):
        return bind(q[name], **params)

    run("""
INSERT INTO device(id,merchant_id,store_id,name,device_type,direction,provider,provider_account_ref,provider_device_id,capabilities)
VALUES(302,1,11,'mock2','ACCESS','ENTRY','MOCK','fixture','D2',JSON_OBJECT());
INSERT INTO device_person(id,merchant_id,store_id,user_id,device_id,provider_person_id)
VALUES(501,1,11,1,301,'P301'),(502,1,11,1,302,'P302');
""")
    run("INSERT INTO access_credential(id,merchant_id,store_id,user_id,device_id,device_person_id,credential_type) VALUES(601,1,11,1,302,501,'FACE');", 1452)
    run("INSERT INTO access_credential(id,merchant_id,store_id,user_id,device_id,device_person_id,credential_type) VALUES(601,1,11,1,302,502,'FACE');")
    run("INSERT INTO device_operation(merchant_id,store_id,user_id,device_id,credential_id,operation_no,action,business_key,request_summary) VALUES(1,11,1,301,601,'wrong-device','SYNC_CREDENTIAL','WRONGDEVICE',JSON_OBJECT());", 1452)
    passed('same-device foreign keys reject mismatched person and credential')

    with session(cli, db) as b:
        assert b("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION; SELECT COUNT(*) FROM booking_order WHERE user_id=2;") == ['0']
        run("""START TRANSACTION;
SELECT id FROM customer_user WHERE id=2 FOR UPDATE;
INSERT INTO booking_order(id,merchant_id,store_id,user_id,slot_id,order_no,benefit_type,start_at,end_at,pay_deadline,base_amount_cent,overtime_price_cent,overtime_cap_cent,rule_snapshot,status)
SELECT 9001,merchant_id,store_id,2,slot_id,'CURRENT-READ','TIME_FEE',start_at,end_at,pay_deadline,1000,100,3000,JSON_OBJECT(),'RESERVED' FROM booking_order WHERE id=201;
COMMIT;""")
        b(stmt('lock_customer', user_id=2))
        assert b('SELECT COUNT(*) FROM booking_order WHERE user_id=2;') == ['0']
        rows = b(stmt('find_overlapping_orders', user_id=2, start_at='2026-11-12 10:00', end_at='2026-11-12 11:00'))
        assert len(rows) == 1 and rows[0].split('\t')[0] == '9001', rows
        b('ROLLBACK;')
    passed('current read sees committed order despite earlier RR snapshot')

    run("""
INSERT INTO customer_user(id,wx_app_id,openid) VALUES(3,'fixture','C'),(4,'fixture','D');
INSERT INTO merchant_customer(merchant_id,user_id) VALUES(1,3),(2,3),(1,4);
INSERT INTO store_slot(id,merchant_id,store_id,start_at,end_at,capacity,price_cent)
VALUES(1001,1,11,'2026-12-01 10:00','2026-12-01 11:00',1,1000),
(1002,2,12,'2026-12-01 10:00','2026-12-01 11:00',1,1000);
""")
    barrier = threading.Barrier(2)
    def reserve(item):
        merchant, store, slot, order = item
        with session(cli, db) as s:
            barrier.wait(timeout=10)
            s('START TRANSACTION;')
            s(stmt('lock_customer', user_id=3))
            s(stmt('lock_merchant_customer', merchant_id=merchant, user_id=3))
            s(stmt('lock_store_config', merchant_id=merchant, store_id=store))
            s(stmt('lock_slot', merchant_id=merchant, store_id=store, slot_id=slot))
            conflicts = s(stmt('find_overlapping_orders', user_id=3, start_at='2026-12-01 10:00', end_at='2026-12-01 11:00'))
            if conflicts:
                s('ROLLBACK;')
                return 'CONFLICT'
            changed = s(stmt('hold_capacity', merchant_id=merchant, store_id=store, slot_id=slot) + ' SELECT ROW_COUNT();')
            assert changed == ['1'], changed
            s(f"INSERT INTO booking_order(id,merchant_id,store_id,user_id,slot_id,order_no,benefit_type,start_at,end_at,pay_deadline,base_amount_cent,overtime_price_cent,overtime_cap_cent,rule_snapshot) VALUES({order},{merchant},{store},3,{slot},'CONCURRENT-{order}','TIME_FEE','2026-12-01 10:00','2026-12-01 11:00','2026-12-01 10:00',1000,100,3000,JSON_OBJECT()); COMMIT;")
            return 'CREATED'
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        results = list(pool.map(reserve, [(1,11,1001,9101),(2,12,1002,9102)]))
    assert sorted(results) == ['CONFLICT','CREATED'], results
    assert run('SELECT COUNT(*) FROM booking_order WHERE user_id=3;') == '1'
    assert run('SELECT SUM(reserved_count) FROM store_slot WHERE id IN (1001,1002);') == '1'
    passed('two-client cross-store overlap creates one order and one capacity hold')

    run('UPDATE store_slot SET reserved_count=0 WHERE id=101;')
    gate = threading.Barrier(2)
    def capacity(_):
        gate.wait(timeout=10)
        return run('START TRANSACTION; ' + stmt('hold_capacity', merchant_id=1, store_id=11, slot_id=101) + ' SELECT ROW_COUNT(); DO SLEEP(0.05); COMMIT;')
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        results = list(pool.map(capacity, [1,2]))
    assert sorted(results) == ['0','1'], results
    passed('two-client final capacity cannot oversell')
    run('UPDATE store_slot SET reserved_count=0 WHERE id=101;')
    run("START TRANSACTION; UPDATE store_slot SET reserved_count=1 WHERE id=101; INSERT INTO merchant(id,name) VALUES(1,'duplicate'); COMMIT;",1062)
    assert run('SELECT reserved_count FROM store_slot WHERE id=101;') == '0'
    passed('failed transaction rolls capacity back')

    run("""INSERT INTO store_slot(id,merchant_id,store_id,start_at,end_at,capacity,reserved_count,price_cent)
VALUES(1003,1,11,TIMESTAMPADD(DAY,1,UTC_TIMESTAMP(3)),TIMESTAMPADD(HOUR,25,UTC_TIMESTAMP(3)),1,1,1000);
INSERT INTO booking_order(id,merchant_id,store_id,user_id,slot_id,order_no,benefit_type,start_at,end_at,pay_deadline,base_amount_cent,overtime_price_cent,overtime_cap_cent,rule_snapshot)
SELECT 9201,1,11,4,1003,'EXPIRY','TIME_FEE',start_at,end_at,TIMESTAMPADD(MICROSECOND,200000,UTC_TIMESTAMP(3)),1000,100,3000,JSON_OBJECT() FROM store_slot WHERE id=1003;""")
    # A held user lock takes the payer past the deadline; locked_now is a separate statement.
    expiry_gate = threading.Barrier(2)
    def expire_or_pay(kind):
        with session(cli, db) as s:
            expiry_gate.wait(timeout=10)
            s('START TRANSACTION;')
            s(stmt('lock_customer',user_id=4))
            s(stmt('lock_merchant_customer',merchant_id=1,user_id=4))
            s(stmt('lock_store_config',merchant_id=1,store_id=11))
            s(stmt('lock_slot',merchant_id=1,store_id=11,slot_id=1003))
            order = s(stmt('lock_order',merchant_id=1,user_id=4,order_id=9201))[0].split('\t')
            now = s(stmt('locked_now'))[0]
            if kind == 'pay':
                changed = s(stmt('confirm_order',merchant_id=1,user_id=4,order_id=9201,now_utc=now)+' SELECT ROW_COUNT();')[0]
            else:
                assert datetime.fromisoformat(now) >= datetime.fromisoformat(order[4])
                changed = s(stmt('cancel_unpaid_order',merchant_id=1,user_id=4,order_id=9201,now_utc=now,reason='EXPIRED')+' SELECT ROW_COUNT();')[0]
                if changed == '1':
                    assert s(stmt('release_capacity',merchant_id=1,store_id=11,slot_id=1003)+' SELECT ROW_COUNT();') == ['1']
            s('COMMIT;')
            return kind,changed
    with session(cli, db) as holder:
        holder('START TRANSACTION; '+stmt('lock_customer',user_id=4))
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            jobs = [pool.submit(expire_or_pay,x) for x in ['pay','expire']]
            holder('DO SLEEP(0.4); COMMIT;')
            results = dict(f.result(timeout=15) for f in jobs)
    assert results == {'pay':'0','expire':'1'}, results
    assert run('SELECT status,capacity_held FROM booking_order WHERE id=9201;') == 'CANCELLED\t0'
    assert run('SELECT reserved_count FROM store_slot WHERE id=1003;') == '0'
    passed('lock wait past deadline rejects payment and expires capacity once')

    # Two valid new reservations compete with an existing overstayer for real occupancy.
    run("""INSERT INTO customer_user(id,wx_app_id,openid) VALUES(5,'fixture','E'),(6,'fixture','F');
INSERT INTO merchant_customer(merchant_id,user_id) VALUES(1,5),(1,6);
INSERT INTO store(id,merchant_id,name,address,images,open_time,close_time,capacity)
VALUES(13,1,'admission','admission',JSON_ARRAY(),'08:00','23:00',2);
INSERT INTO store_slot(id,merchant_id,store_id,start_at,end_at,capacity,reserved_count,price_cent)
VALUES(1100,1,13,'2026-12-03 09:00','2026-12-03 10:00',2,1,1000),
(1101,1,13,'2026-12-03 10:00','2026-12-03 11:00',2,2,1000);
INSERT INTO booking_order(id,merchant_id,store_id,user_id,slot_id,order_no,benefit_type,start_at,end_at,pay_deadline,base_amount_cent,overtime_price_cent,overtime_cap_cent,rule_snapshot,status)
VALUES(9400,1,13,3,1100,'OVERSTAYER','TIME_FEE','2026-12-03 09:00','2026-12-03 10:00','2026-12-03 09:00',1000,100,3000,JSON_OBJECT(),'FINISHED'),
(9401,1,13,5,1101,'ADMIT-A','TIME_FEE','2026-12-03 10:00','2026-12-03 11:00','2026-12-03 10:00',1000,100,3000,JSON_OBJECT(),'RESERVED'),
(9402,1,13,6,1101,'ADMIT-B','TIME_FEE','2026-12-03 10:00','2026-12-03 11:00','2026-12-03 10:00',1000,100,3000,JSON_OBJECT(),'RESERVED');
INSERT INTO visit_session(id,merchant_id,store_id,user_id,visit_no,entry_at,entry_source,authorized_end_at,last_order_id)
VALUES(500,1,13,3,'OVERSTAYER-V','2026-12-03 09:30','SELF_CONFIRM','2026-12-03 10:00',9400);
""")
    admission_gate=threading.Barrier(2)
    def admit(item):
        user,order=item
        with session(cli,db) as s:
            admission_gate.wait(timeout=10)
            s('START TRANSACTION; '+stmt('lock_customer',user_id=user))
            s(stmt('lock_merchant_customer',merchant_id=1,user_id=user))
            config=s(stmt('lock_store_admission',merchant_id=1,store_id=13))[0].split('\t')
            current=s(stmt('lock_current_visits',merchant_id=1,store_id=13))
            if len(current)>=int(config[3]):
                s('ROLLBACK;');return 'FULL'
            s(f"INSERT INTO visit_session(merchant_id,store_id,user_id,visit_no,entry_at,entry_source,authorized_end_at,last_order_id) VALUES(1,13,{user},'ADMIT-V-{user}','2026-12-03 10:15','SELF_CONFIRM','2026-12-03 11:00',{order}); COMMIT;")
            return 'ADMITTED'
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        results=list(pool.map(admit,[(5,9401),(6,9402)]))
    assert sorted(results)==['ADMITTED','FULL'],results
    assert run("SELECT COUNT(*) FROM visit_session WHERE store_id=13 AND status='IN_STORE';")=='2'
    passed('store-level current-read admission serializes different users with an overstayer')

    refunds_gate = threading.Barrier(2)
    def refund(attempt):
        with session(cli, db) as s:
            refunds_gate.wait(timeout=10)
            s('START TRANSACTION; '+stmt('lock_customer',user_id=1))
            s(stmt('lock_order',merchant_id=1,user_id=1,order_id=201))
            original = s(stmt('lock_receipt',merchant_id=1,user_id=1,payment_id=801))[0].split('\t')
            assert original[1:6] == ['201','NULL','NULL','RECEIPT','SUCCEEDED']
            reserved = sum(int(row.split('\t')[1]) for row in s(stmt('lock_refunds',merchant_id=1,user_id=1,payment_id=801)))
            if reserved+700>int(original[6]):
                s('ROLLBACK;')
                return 'REJECTED'
            s(f"INSERT INTO payment_record(merchant_id,user_id,payment_no,order_id,refund_of_id,business_key,kind,amount_cent,status,completed_at) VALUES(1,1,'REFUND-{attempt}',201,801,'REFUND-{attempt}','REFUND',700,'SUCCEEDED',UTC_TIMESTAMP(3)); COMMIT;")
            return 'REFUNDED'
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        results = list(pool.map(refund,[1,2]))
    assert sorted(results)==['REFUNDED','REJECTED'],results
    assert run('SELECT SUM(amount_cent) FROM payment_record WHERE refund_of_id=801;')=='700'
    passed('concurrent refunds serialize on receipt and cannot exceed original amount')

    run("""INSERT INTO member_card(id,merchant_id,user_id,product_id,purchase_no,card_type,product_snapshot,purchase_amount_cent,total_count,remaining_count)
VALUES(602,1,1,501,'C2','COUNT_CARD',JSON_OBJECT(),10000,10,9);
INSERT INTO card_ledger(id,merchant_id,user_id,member_card_id,order_id,business_key,kind,count_delta,reason)
VALUES(703,1,1,601,201,'TEST-DEBIT','DEBIT',-1,'relational constraint fixture');""")
    run("INSERT INTO card_ledger(merchant_id,user_id,member_card_id,order_id,reversal_of_id,business_key,kind,count_delta,reason) VALUES(1,1,602,201,703,'WRONG-CARD-RETURN','RETURN',1,'test');",1452)
    passed('reversal foreign key rejects returning a debit to a different card')

    run("""INSERT INTO device_operation(id,merchant_id,store_id,user_id,device_id,order_id,operation_no,action,business_key,request_summary)
VALUES(1001,1,11,1,301,201,'OPEN-A','OPEN','OPEN:201:1',JSON_OBJECT()),
(1002,1,11,2,301,9001,'OPEN-B','OPEN','OPEN:9001:1',JSON_OBJECT());""")
    run("INSERT INTO device_operation(merchant_id,store_id,user_id,device_id,order_id,operation_no,action,business_key,request_summary) VALUES(1,11,1,301,201,'DUP-OPEN','OPEN','OPEN:201:2',JSON_OBJECT());",1062)
    run("UPDATE device_operation SET status='RUNNING' WHERE id=1001;",3819)
    run('UPDATE device_operation SET next_attempt_at=NULL WHERE id=1001;',1048)
    passed('unresolved open uniqueness and runnable lease constraints')
    claim_gate=threading.Barrier(2)
    def claim(_):
        with session(cli,db) as s:
            claim_gate.wait(timeout=10)
            s('START TRANSACTION; '+stmt('lock_device',device_id=301,merchant_id=1,store_id=11))
            if s(stmt('lock_device_unresolved',device_id=301)):
                s('ROLLBACK;');return None
            now=s(stmt('locked_now'))[0]
            candidate=s(stmt('lock_ready_operation',device_id=301,now_utc=now))
            assert candidate
            oid=int(candidate[0].split('\t')[0])
            assert s(stmt('claim_operation',operation_id=oid,device_id=301,lease_seconds=60,now_utc=now)+' SELECT ROW_COUNT();')==['1']
            s('COMMIT;');return oid
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        claimed=list(pool.map(claim,[1,2]))
    assert sum(x is not None for x in claimed)==1,claimed
    oid=next(x for x in claimed if x is not None)
    run(f"UPDATE device_operation SET status='RUNNING',attempts=1,lease_until=TIMESTAMPADD(SECOND,60,UTC_TIMESTAMP(3)) WHERE id={1002 if oid==1001 else 1001};",1062)
    passed('two workers cannot run two commands on same device')
    now=run('SELECT UTC_TIMESTAMP(3);')
    run(f"UPDATE device_operation SET lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE id={oid};")
    run(stmt('expire_operation_lease',operation_id=oid,claim_version=1,now_utc=now))
    assert run(f'SELECT status FROM device_operation WHERE id={oid};')=='UNKNOWN'
    # Simulated authoritative reconciliation: confirmed NOT executed; no blind UNKNOWN retry.
    run(f"UPDATE device_operation SET status='FAILED' WHERE id={oid}; UPDATE device_operation SET status='PENDING',next_attempt_at=UTC_TIMESTAMP(3) WHERE id={oid};")
    now=run('SELECT UTC_TIMESTAMP(3);')
    run(stmt('claim_operation',operation_id=oid,device_id=301,lease_seconds=60,now_utc=now))
    common=dict(operation_id=oid,device_id=301,result_status='SUCCEEDED',executed_at=now,now_utc=now,expected_generation=None,result_summary='{}')
    assert run(stmt('complete_operation',claim_version=1,**common)+' SELECT ROW_COUNT();')=='0'
    assert run(stmt('complete_operation',claim_version=2,**common)+' SELECT ROW_COUNT();')=='1'
    passed('expired lease becomes UNKNOWN and old claim cannot overwrite new worker')

    now=run('SELECT UTC_TIMESTAMP(3);')
    run(stmt('claim_event',event_id=901,lease_seconds=60,now_utc=now))
    run("UPDATE device_event SET processing_status='FAILED',lease_until=NULL WHERE id=901;")
    run(stmt('claim_event',event_id=901,lease_seconds=60,now_utc=now))
    assert run(stmt('complete_event',event_id=901,claim_version=1,now_utc=now)+' SELECT ROW_COUNT();')=='0'
    assert run(stmt('complete_event',event_id=901,claim_version=2,now_utc=now)+' SELECT ROW_COUNT();')=='1'
    passed('event processing fencing also rejects an old claim version')

    run("""INSERT INTO store_slot(id,merchant_id,store_id,start_at,end_at,capacity,price_cent) VALUES(1004,1,11,'2026-11-12 11:00','2026-11-12 12:00',5,1000);
INSERT INTO booking_order(id,merchant_id,store_id,user_id,slot_id,order_no,benefit_type,start_at,end_at,pay_deadline,base_amount_cent,overtime_price_cent,overtime_cap_cent,rule_snapshot,status)
VALUES(9301,1,11,1,1004,'SECOND-SEGMENT','TIME_FEE','2026-11-12 11:00','2026-11-12 12:00','2026-11-12 11:00',1000,100,3000,JSON_OBJECT(),'IN_USE');
INSERT INTO visit_order(merchant_id,store_id,user_id,visit_id,order_id,used_from) VALUES(1,11,1,401,9301,'2026-11-12 11:00');
INSERT INTO payment_record(merchant_id,user_id,payment_no,visit_id,business_key,kind,amount_cent,status,completed_at)
VALUES(1,1,'VP1',401,'OVERTIME:401:1','RECEIPT',100,'SUCCEEDED',UTC_TIMESTAMP(3)),(1,1,'VP2',401,'OVERTIME:401:2','RECEIPT',100,'SUCCEEDED',UTC_TIMESTAMP(3));""")
    assert run(stmt('visit_net_payments',merchant_id=1,visit_id=401))=='401\t200'
    passed('preaggregated visit cash stays 200 with two used orders and two payments')

    role='role_'+uuid.uuid4().hex[:10];user='user_'+uuid.uuid4().hex[:10]
    try:
        grants=(ROOT/'database/app_privileges.sql').read_text().replace('`gym_saas`','`'+db+'`').replace("'gym_app'","'"+role+"'")
        run(grants,database=False)
        run(f"CREATE USER '{user}'@'localhost'; GRANT '{role}' TO '{user}'@'localhost'; SET DEFAULT ROLE '{role}' TO '{user}'@'localhost';",database=False)
        app_cli=[a if a!='-uroot' else '-u'+user for a in cli]+[db]
        def app(query):return subprocess.run(app_cli,input=query,text=True,capture_output=True,timeout=10)
        assert app('SELECT id FROM merchant;').returncode==0
        assert app('START TRANSACTION; SELECT id FROM card_ledger WHERE id=701 FOR SHARE; COMMIT;').returncode==0
        assert app('UPDATE store_slot SET price_cent=price_cent WHERE id=101;').returncode==0
        for table in ['settlement_revision','card_ledger','credit_event','business_event']:
            result=app(f'UPDATE {table} SET id=id WHERE id=0;')
            assert result.returncode and 'ERROR 1142 ' in result.stderr,result.stderr
        assert app('DELETE FROM booking_order WHERE id=0;').returncode!=0
        passed('application role cannot update append-only records or delete orders')
    finally:
        run(f"DROP USER IF EXISTS '{user}'@'localhost'; DROP ROLE IF EXISTS '{role}';",database=False)

    # Reproducible plans under historical data, not a production performance benchmark.
    for offset in range(0,3000,500):
        slots=[];orders=[]
        for i in range(offset,offset+500):
            start=f"DATE_ADD('2020-01-01',INTERVAL {i} HOUR)";end=f"DATE_ADD('2020-01-01 01:00',INTERVAL {i} HOUR)"
            slots.append(f'({20000+i},1,11,{start},{end},1,1000)')
            orders.append(f"({30000+i},1,11,1,{20000+i},'H-{i}','TIME_FEE',{start},{end},{start},1000,100,3000,JSON_OBJECT(),'FINISHED',{start})")
        run('INSERT INTO store_slot(id,merchant_id,store_id,start_at,end_at,capacity,price_cent) VALUES '+','.join(slots)+'; INSERT INTO booking_order(id,merchant_id,store_id,user_id,slot_id,order_no,benefit_type,start_at,end_at,pay_deadline,base_amount_cent,overtime_price_cent,overtime_cap_cent,rule_snapshot,status,created_at) VALUES '+','.join(orders)+';')
    run('ANALYZE TABLE booking_order;')
    for name,params in [('list_my_orders',dict(user_id=1,page_size=20,offset=0)),('list_merchant_orders',dict(merchant_id=1,page_size=20,offset=0))]:
        plan=run('EXPLAIN ANALYZE '+stmt(name,**params))
        assert 'Sort:' not in plan,plan
        assert ('ix_order_user_created' if name=='list_my_orders' else 'ix_order_merchant_created') in plan,plan
        report['query_plans'][name]=plan
    report['index_count']=int(run('SELECT COUNT(*) FROM (SELECT table_name,index_name FROM information_schema.statistics WHERE table_schema=DATABASE() GROUP BY table_name,index_name) t;'))
    report['foreign_key_count']=int(run("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND constraint_type='FOREIGN KEY';"))
    assert report['index_count']<167
    assert report['foreign_key_count']==55
    passed('order lists use time indexes without Sort and total indexes are reduced')
    return report
