-- 本地开发/演示种子数据（仅课程演示；不得写入真实身份、真实设备码或生产口令）
-- 执行：docker exec -i gym-mysql mysql -uroot -p<本地root口令> gym_saas < docker/dev-seed.sql
-- 演示后台账号：admin / admin123456（BCrypt 哈希，仅本地演示；生产禁止使用）

INSERT INTO merchant (name, status)
SELECT '演示商家', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM merchant WHERE name = '演示商家');

INSERT INTO admin_user (merchant_id, username, password_hash, role, permissions, status, token_version)
SELECT m.id, 'admin', '$2a$10$uVDp7Rc96u3WVLA60n/dA.0isYgG4CzlQ4RAYqJ4MaXn05UOXRnYe', 'ADMIN',
       JSON_ARRAY('REPORT_READ', 'REPORT_REFRESH', 'AUDIT_READ'), 'ACTIVE', 0
FROM merchant m
WHERE m.name = '演示商家'
  AND NOT EXISTS (SELECT 1 FROM admin_user WHERE username = 'admin');
