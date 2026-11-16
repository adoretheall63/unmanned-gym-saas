"""Verify artifacts and import schema into a fresh disposable MySQL database.

Run with uv --with pyyaml --with openapi-spec-validator.
The selected Docker container must be a disposable MySQL 8.4 test instance.
No existing database is read or modified; only this script's random database is dropped.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import uuid
import hashlib

import yaml
from openapi_spec_validator import validate_spec
from openapi_schema_validator import OAS30Validator

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--container', required=True, help='Disposable MySQL 8.4 container')
    parser.add_argument('--report', type=Path, help='Optional JSON validation evidence')
    args = parser.parse_args()
    spec = yaml.safe_load((ROOT / 'api/first-loop.openapi.yaml').read_text())
    validate_spec(spec)
    operations = [op for path in spec['paths'].values() for op in path.values()]
    assert len({op['operationId'] for op in operations}) == len(operations)
    assert all(op.get('x-business-rules') for op in operations)
    for op in operations:
        if op['x-idempotent']:
            assert any(p['name'] == 'Idempotency-Key' and p['required'] for p in op['parameters'])
    examples = json.loads((ROOT / 'api/first-loop.examples.json').read_text())
    for example in examples:
        schema = spec['components']['schemas']['Order' if 'orderNo' in example else 'Error']
        # Fixture schemas resolve local references before validation.
        def expand(node):
            if isinstance(node, dict):
                if '$ref' in node:
                    return expand(spec['components']['schemas'][node['$ref'].split('/')[-1]])
                return {k: expand(v) for k, v in node.items()}
            if isinstance(node, list):
                return [expand(v) for v in node]
            return node
        OAS30Validator(expand(schema)).validate(example)
    print(f'PASS OpenAPI: {len(operations)} operations; {len(examples)} JSON fixture examples')

    db = 'hiking_contract_' + uuid.uuid4().hex[:12]
    cli = ['docker', 'exec', '-i', args.container, 'mysql', '-uroot', '--batch', '--skip-column-names']

    def run(sql, expected_error=None, database=True):
        cmd = cli + ([db] if database else [])
        result = subprocess.run(cmd, input="SET time_zone='+00:00';\n" + sql,
                                text=True, capture_output=True, timeout=30)
        if expected_error is None:
            if result.returncode:
                raise AssertionError(result.stderr)
        else:
            assert result.returncode and f'ERROR {expected_error} ' in result.stderr, result.stderr
        return result.stdout.strip()

    version = run('SELECT VERSION();', database=False)
    assert version.startswith('8.4.'), f'Expected MySQL 8.4, got {version}'
    run(f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;', database=False)
    try:
        ddl = (ROOT / 'database/schema.sql').read_text()
        run(ddl)
        names = re.findall(r'CREATE TABLE `([^`]+)`', ddl)
        actual = run('SHOW TABLES;').splitlines()
        assert sorted(names) == sorted(actual)
        dictionary = (ROOT / 'docs/数据库设计.md').read_text()
        for table in names:
            part = re.search(rf'### 1\.\d+ {table} .*?(?=\n### |\n## |\Z)', dictionary, re.S).group()
            fields = re.findall(r'^\| `([^`]+)` \|', part, re.M)
            actual_fields = [x.split('\t')[0] for x in run(f'SHOW COLUMNS FROM `{table}`;').splitlines()]
            assert fields == actual_fields, table
        er = (ROOT / 'docs/数据库设计.md').read_text()
        assert all(name in er for name in names), 'ER missing an entity or its explanatory note'
        print(f'PASS DDL: MySQL {version}; {len(names)} tables; dictionary column parity')

        run("""
INSERT INTO merchant(id,name) VALUES(1,'A'),(2,'B');
INSERT INTO customer_user(id,wx_app_id,openid) VALUES(1,'fixture','A'),(2,'fixture','B');
INSERT INTO merchant_customer(id,merchant_id,user_id) VALUES(1,1,1),(2,1,2),(3,2,1);
INSERT INTO store(id,merchant_id,name,address,images,open_time,close_time,capacity)
VALUES(11,1,'A','A',JSON_ARRAY(),'08:00','23:00',1),(12,2,'B','B',JSON_ARRAY(),'08:00','23:00',1);
INSERT INTO store_slot(id,merchant_id,store_id,start_at,end_at,capacity,price_cent)
VALUES(101,1,11,'2026-11-12 10:00','2026-11-12 11:00',1,1000),
(102,2,12,'2026-11-12 10:00','2026-11-12 11:00',1,1000);
INSERT INTO booking_order(id,merchant_id,store_id,user_id,slot_id,order_no,benefit_type,
start_at,end_at,pay_deadline,base_amount_cent,overtime_price_cent,overtime_cap_cent,rule_snapshot,status)
VALUES(201,1,11,1,101,'O1','TIME_FEE','2026-11-12 10:00','2026-11-12 11:00','2026-11-12 10:00',1000,100,3000,JSON_OBJECT(),'RESERVED');
INSERT INTO visit_session(id,merchant_id,store_id,user_id,visit_no,entry_at,entry_source,authorized_end_at,last_order_id)
VALUES(401,1,11,1,'V1','2026-11-12 10:10','SELF_CONFIRM','2026-11-12 11:00',201);
INSERT INTO visit_order(merchant_id,store_id,user_id,visit_id,order_id,used_from) VALUES(1,11,1,401,201,'2026-11-12 10:10');
INSERT INTO device(id,merchant_id,store_id,name,device_type,direction,provider,provider_account_ref,provider_device_id,capabilities)
VALUES(301,1,11,'mock','ACCESS','ENTRY','MOCK','fixture','D1',JSON_OBJECT());
INSERT INTO card_product(id,merchant_id,name,card_type,total_count,price_cent) VALUES(501,1,'10次','COUNT_CARD',10,10000);
INSERT INTO member_card(id,merchant_id,user_id,product_id,purchase_no,card_type,product_snapshot,purchase_amount_cent,total_count,remaining_count)
VALUES(601,1,1,501,'C1','COUNT_CARD',JSON_OBJECT(),10000,10,9);
INSERT INTO card_ledger(id,merchant_id,user_id,member_card_id,order_id,business_key,kind,count_delta,reason)
VALUES(701,1,1,601,201,'DEBIT:201','DEBIT',-1,'test');
INSERT INTO card_ledger(id,merchant_id,user_id,member_card_id,order_id,reversal_of_id,business_key,kind,count_delta,reason)
VALUES(702,1,1,601,201,701,'RETURN:201','RETURN',1,'test');
INSERT INTO payment_record(id,merchant_id,user_id,payment_no,order_id,business_key,kind,amount_cent,status,completed_at)
VALUES(801,1,1,'P1',201,'BOOKING:201','RECEIPT',1000,'SUCCEEDED','2026-11-12 09:55');
INSERT INTO device_event(id,merchant_id,store_id,device_id,dedup_key,event_type,occurred_at,received_at,payload_summary)
VALUES(901,1,11,301,'EVENT1','OPEN_RESULT','2026-11-12 10:10','2026-11-12 10:10',JSON_OBJECT());
""")
        cases = [
            ('capacity ceiling', 'UPDATE store_slot SET reserved_count=2 WHERE id=101;', 3819),
            ('capacity floor', 'UPDATE store_slot SET reserved_count=-1 WHERE id=101;', 3819),
            ('negative money', 'UPDATE store_slot SET price_cent=-1 WHERE id=101;', 3819),
            ('cross merchant slot', 'UPDATE booking_order SET slot_id=102 WHERE id=201;', 1452),
            ('cross user visit order', 'UPDATE visit_order SET user_id=2 WHERE order_id=201;', 1452),
            ('one active visit', "INSERT INTO visit_session(merchant_id,store_id,user_id,visit_no,entry_at,entry_source,authorized_end_at,last_order_id) VALUES(1,11,1,'V2','2026-11-12 10:20','SELF_CONFIRM','2026-11-12 11:00',201);", 1062),
            ('used order once', "INSERT INTO visit_order(merchant_id,store_id,user_id,visit_id,order_id,used_from) VALUES(1,11,1,401,201,'2026-11-12 10:20');", 1062),
            ('payment target exclusive', 'UPDATE payment_record SET visit_id=401 WHERE id=801;', 3819),
            ('duplicate payment item', "INSERT INTO payment_record(merchant_id,user_id,payment_no,order_id,business_key,kind,amount_cent,status,completed_at) VALUES(1,1,'P2',201,'BOOKING:201','RECEIPT',1000,'SUCCEEDED','2026-11-12 09:55');", 1062),
            ('duplicate refund of count', "INSERT INTO card_ledger(merchant_id,user_id,member_card_id,order_id,reversal_of_id,business_key,kind,count_delta,reason) VALUES(1,1,601,201,701,'RETURN-again','RETURN',1,'test');", 1062),
            ('card needs count', 'UPDATE member_card SET remaining_count=NULL WHERE id=601;', 3819),
            ('cancel must release', "UPDATE booking_order SET status='CANCELLED' WHERE id=201;", 3819),
            ('known states', "UPDATE booking_order SET status='anything' WHERE id=201;", 3819),
            ('event dedup', "INSERT INTO device_event(merchant_id,store_id,device_id,dedup_key,event_type,occurred_at,received_at,payload_summary) VALUES(1,11,301,'EVENT1','OPEN_RESULT',NOW(3),NOW(3),JSON_OBJECT());", 1062),
        ]
        for name, sql, code in cases:
            run(sql, code)
            print('PASS constraint:', name)
        # The conditional update example must never take reserved_count beyond capacity.
        result = run('UPDATE store_slot SET reserved_count=reserved_count+1 WHERE id=101 AND reserved_count<capacity; SELECT ROW_COUNT(); UPDATE store_slot SET reserved_count=reserved_count+1 WHERE id=101 AND reserved_count<capacity; SELECT ROW_COUNT();')
        assert result.splitlines() == ['1', '0'], result
        print('PASS conditional capacity update: first=1, second=0')
        from verify_transactions import verify
        evidence = verify(run, cli, db)
        evidence.update(mysql_version=version, table_count=len(names),
                        column_count=sum(len(re.findall(r'^  `', block, re.M)) for block in re.findall(r'CREATE TABLE.*?\n\) ENGINE', ddl, re.S)),
                        constraint_checks=len(cases), openapi_operations=len(operations),
                        openapi_schemas=len(spec['components']['schemas']),
                        schema_sha256=hashlib.sha256(ddl.encode()).hexdigest())
        if args.report:
            args.report.write_text(json.dumps(evidence, ensure_ascii=False, indent=2)+'\n')
    finally:
        run(f'DROP DATABASE `{db}`;', database=False)
        print('Cleaned disposable database:', db)


if __name__ == '__main__':
    main()
