# HTTP end-to-end verification for module 4 part B (§4.8-4.12, §4.14, §4.15)
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:18080'
$results = New-Object System.Collections.ArrayList

function Sql([string]$sql) {
    $tmp = Join-Path $env:TEMP ('m03b-' + [guid]::NewGuid().ToString('N') + '.sql')
    [System.IO.File]::WriteAllBytes($tmp, [System.Text.Encoding]::UTF8.GetBytes($sql))
    $out = cmd /c "docker exec -i gym-mysql mysql --default-character-set=utf8mb4 -uroot -proot_dev_only < `"$tmp`" 2>&1" | Out-String
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    return $out
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
}

function Call {
    param([string]$Method, [string]$Path, $Body, [hashtable]$Headers)
    $p = @{ Uri = ($base + $Path); Method = $Method; Headers = @{}; UseBasicParsing = $true; TimeoutSec = 30 }
    if ($Headers) { foreach ($k in $Headers.Keys) { $p.Headers[$k] = $Headers[$k] } }
    $p.Headers['Content-Type'] = 'application/json'
    if ($null -ne $Body) { $json = ($Body | ConvertTo-Json -Compress -Depth 6); $p.Body = [System.Text.Encoding]::UTF8.GetBytes($json) }
    try {
        $resp = Invoke-WebRequest @p
        $etag = ''
        if ($resp.Headers['ETag']) { $etag = $resp.Headers['ETag'] }
        $text = $resp.Content
        try { $text = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()) } catch { }
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $text; ETag = $etag }
    } catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ Status = $status; Body = ''; ETag = '' }
    }
}

function Upload([string]$Path, [byte[]]$bytes, [hashtable]$Headers) {
    $boundary = [guid]::NewGuid().ToString('N')
    $head = "--$boundary`r`nContent-Disposition: form-data; name=`"file`"; filename=`"test.png`"`r`nContent-Type: image/png`r`n`r`n"
    $tail = "`r`n--$boundary--`r`n"
    $list = New-Object System.Collections.Generic.List[byte]
    $list.AddRange([System.Text.Encoding]::UTF8.GetBytes($head))
    $list.AddRange($bytes)
    $list.AddRange([System.Text.Encoding]::UTF8.GetBytes($tail))
    $p = @{ Uri = ($base + $Path); Method = 'POST'; Headers = @{ 'Content-Type' = "multipart/form-data; boundary=$boundary" }; UseBasicParsing = $true; TimeoutSec = 30; Body = $list.ToArray() }
    if ($Headers) { foreach ($k in $Headers.Keys) { $p.Headers[$k] = $Headers[$k] } }
    try {
        $resp = Invoke-WebRequest @p
        $text = $resp.Content
        try { $text = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()) } catch { }
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $text; ETag = '' }
    } catch {
        $st = 0; if ($_.Exception.Response) { $st = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ Status = $st; Body = ''; ETag = '' }
    }
}

function Check {
    param([string]$Name, [int]$Expected, $Result, [scriptblock]$Assert)
    $ok = ($Result.Status -eq $Expected)
    if ($ok -and $Assert) { $ok = [bool](& $Assert $Result) }
    $tag = if ($ok) { 'PASS' } else { "FAIL(status=$($Result.Status)/expected=$Expected)" }
    [void]$results.Add($tag)
    Write-Output ("$tag  $Name")
    if (-not $ok) { Write-Output ("     [debug] body=[" + $Result.Body + "]") }
}

$pnghash = '$2a$10$uVDp7Rc96u3WVLA60n/dA.0isYgG4CzlQ4RAYqJ4MaXn05UOXRnYe'
$storeOpHash = '$2a$10$Ry/Kd2NZihgPF7kt2NJxzeLh0TCBA0EMvx5tCoJYcuDwfMoEsHOqW'

# ---------- 准备数据 ----------
Sql @"
DELETE FROM gym_saas.api_request;
DELETE FROM gym_saas.business_event;
DELETE FROM gym_saas.booking_order;
DELETE FROM gym_saas.visit_session;
DELETE FROM gym_saas.device_operation;
DELETE FROM gym_saas.store_slot;
DELETE FROM gym_saas.merchant_customer;
DELETE FROM gym_saas.customer_user;
INSERT INTO gym_saas.customer_user (wx_app_id,openid,nickname) VALUES ('fixture','m03b-customer','m03b');
INSERT INTO gym_saas.merchant_customer (merchant_id,user_id)
SELECT 1, id FROM gym_saas.customer_user WHERE openid='m03b-customer';
DELETE FROM gym_saas.admin_user WHERE username <> 'admin';
UPDATE gym_saas.admin_user SET role='ADMIN', status='ACTIVE', token_version=0, permissions=JSON_ARRAY() WHERE username='admin';
INSERT INTO gym_saas.admin_user (merchant_id,username,password_hash,role,permissions,status,token_version)
VALUES (1,'storeop','$storeOpHash','OPERATOR',JSON_ARRAY('STORE_READ','STORE_CONTROL'),'ACTIVE',0),(1,'readop','$storeOpHash','OPERATOR',JSON_ARRAY('STORE_READ'),'ACTIVE',0);
DELETE FROM gym_saas.store;
INSERT INTO gym_saas.store (id, merchant_id, name, address, description, images, open_time, close_time,
 is_24h, ends_next_day, capacity, slot_price_cent, overtime_price_cent, overtime_cap_cent, status,
 admission_paused, pause_reason, light_mode, light_generation, message_health)
VALUES (1, 1, '演示门店', '学校教学楼', '演示', JSON_ARRAY(), '08:00:00', '23:00:00', 0, 0, 10, 1000, 100, 3000,
 'OPEN', 0, NULL, 'AUTO', 0, 'UNKNOWN');
"@

$r = Call 'POST' '/api/v1/admin/auth/login' @{ username = 'admin'; password = 'admin123456' }
Check 'admin login' 200 $r { param($x) $x.Body -like '*"accessToken"*' }
$adminToken = ($r.Body | ConvertFrom-Json).data.accessToken
$H = @{ Authorization = "Bearer $adminToken" }

$r = Call 'POST' '/api/v1/admin/auth/login' @{ username = 'storeop'; password = 'storeop123456' }
Check 'store operator login' 200 $r { param($x) $x.Body -like '*"accessToken"*' }
$opToken = ($r.Body | ConvertFrom-Json).data.accessToken
$OH = @{ Authorization = "Bearer $opToken" }

# ---------- 4.14 补齐时段 ----------
$r = Call 'POST' '/api/v1/admin/stores/1/slot-generation' @{ reason = 'm03b verify' } $H
Check '4.14 missing Idempotency-Key -> 400' 400 $r
$r = Call 'POST' '/api/v1/admin/stores/1/slot-generation' @{ reason = 'm03b verify' } @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-gen-0001' }
Check '4.14 generate slots 200 accepted' 200 $r { param($x) $x.Body -like '*"accepted":true*' }
$r = Call 'POST' '/api/v1/admin/stores/1/slot-generation' @{ reason = 'm03b verify' } @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-gen-0001' }
Check '4.14 replay same key -> same accepted' 200 $r { param($x) $x.Body -like '*"accepted":true*' }
$r = Call 'POST' '/api/v1/admin/stores/1/slot-generation' @{ } @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-gen-0002' }
Check '4.14 missing reason -> 400' 400 $r

$tomorrow = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
$r = Call 'GET' "/api/v1/admin/stores/1/slots?date=$tomorrow" $null $H
Check '4.13 generated slots exist with etag' 200 $r { param($x) (($x.Body | ConvertFrom-Json).data.items.Count -ge 10) -and ($x.Body -like '*"etag":"*') }
$slots = ($r.Body | ConvertFrom-Json).data.items
$firstSlot = $slots | Select-Object -First 1

# ---------- 4.9 规则预览 ----------
$preview = @{ openTime = '09:00:00'; closeTime = '21:30:00'; is24h = $false; endsNextDay = $false; capacity = 8; slotPriceCent = 1200; overtimePriceCent = 150; overtimeCapCent = 4000; reason = 'm03b preview' }
$r = Call 'POST' '/api/v1/admin/stores/1/rule-preview' $preview $H
Check '4.9 preview 200 (hash/affected/uncovered/canApply)' 200 $r { param($x) ($x.Body -like '*"previewHash":"*') -and ($x.Body -like '*"affectedUnsoldSlots":*') -and ($x.Body -like '*"uncoveredMinutes":30*') -and ($x.Body -like '*"canApply":true*') }
$previewHash = ($r.Body | ConvertFrom-Json).data.previewHash
$affected = ($r.Body | ConvertFrom-Json).data.affectedUnsoldSlots
Write-Output "     previewHash=$previewHash affected=$affected"
$r = Call 'POST' '/api/v1/admin/stores/999999/rule-preview' $preview $H
Check '4.9 preview unknown store -> 404' 404 $r

# ---------- 4.10 应用规则 ----------
$apply = @{ openTime = '09:00:00'; closeTime = '21:30:00'; is24h = $false; endsNextDay = $false; capacity = 8; slotPriceCent = 1200; overtimePriceCent = 150; overtimeCapCent = 4000; previewHash = $previewHash; reason = 'm03b apply' }
$r = Call 'GET' '/api/v1/admin/stores/1' $null $H
$storeEtag = $r.ETag
$r = Call 'PUT' '/api/v1/admin/stores/1/rules' $apply @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-rules-0001' }
Check '4.10 apply without If-Match -> 428' 428 $r
$r = Call 'PUT' '/api/v1/admin/stores/1/rules' $apply @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-rules-0002'; 'If-Match' = '"wrong"' }
Check '4.10 apply wrong If-Match -> 412' 412 $r
$stale = $apply.Clone(); $stale.previewHash = 'stale-hash'
$r = Call 'PUT' '/api/v1/admin/stores/1/rules' $stale @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-rules-0003'; 'If-Match' = $storeEtag }
Check '4.10 stale previewHash -> 409 PREVIEW_STALE' 409 $r
$r = Call 'PUT' '/api/v1/admin/stores/1/rules' $apply @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-rules-0004'; 'If-Match' = $storeEtag }
Check '4.10 apply rules 200 (capacity/price updated + ETag)' 200 $r { param($x) ($x.Body -like '*"capacity":8*') -and ($x.Body -like '*"slotPriceCent":1200*') -and ($x.ETag -ne '') }
$newStoreEtag = $r.ETag

# ---------- 4.15 调整时段 ----------
$r = Call 'GET' "/api/v1/admin/stores/1/slots?date=$tomorrow" $null $H
$slot = ($r.Body | ConvertFrom-Json).data.items | Select-Object -First 1
$adjust = @{ capacity = 5; priceCent = 900; status = 'CLOSED'; reason = 'm03b slot adjust' }
$r = Call 'PATCH' ("/api/v1/admin/stores/1/slots/" + $slot.id) $adjust @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-slot-0001' }
Check '4.15 adjust without If-Match -> 428' 428 $r
$r = Call 'PATCH' ("/api/v1/admin/stores/1/slots/" + $slot.id) $adjust @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-slot-0002'; 'If-Match' = '"wrong"' }
Check '4.15 adjust wrong If-Match -> 412' 412 $r
$r = Call 'PATCH' ("/api/v1/admin/stores/1/slots/" + $slot.id) $adjust @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-slot-0003'; 'If-Match' = $slot.etag }
Check '4.15 adjust 200 (capacity 5 price 900 CLOSED)' 200 $r { param($x) ($x.Body -like '*"capacity":5*') -and ($x.Body -like '*"priceCent":900*') -and ($x.Body -like '*"status":"CLOSED"*') }
# 已承诺时段不可改
Sql @"
UPDATE gym_saas.store_slot SET reserved_count = 1 WHERE id = $($slot.id);
"@
$r = Call 'GET' "/api/v1/admin/stores/1/slots?date=$tomorrow" $null $H
$slot2 = ($r.Body | ConvertFrom-Json).data.items | Where-Object { $_.id -eq $slot.id } | Select-Object -First 1
$r = Call 'PATCH' ("/api/v1/admin/stores/1/slots/" + $slot.id) $adjust @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-slot-0004'; 'If-Match' = $slot2.etag }
Check '4.15 reserved slot -> 409 SLOT_COMMITTED' 409 $r
Sql "UPDATE gym_saas.store_slot SET reserved_count = 0 WHERE id = $($slot.id);"

# ---------- 4.11 营业状态 ----------
$r = Call 'GET' '/api/v1/admin/stores/1' $null $H
$etag1 = $r.ETag
$r = Call 'PUT' '/api/v1/admin/stores/1/status' @{ status = 'DISABLED'; reason = 'm03b disable' } @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-status-0001'; 'If-Match' = $etag1 }
Check '4.11 disable store 200 DISABLED' 200 $r { param($x) $x.Body -like '*"status":"DISABLED"*' }
$etag2 = $r.ETag
# 有未履约订单时禁止停用
Sql @"
INSERT INTO gym_saas.booking_order (merchant_id,store_id,user_id,slot_id,order_no,benefit_type,start_at,end_at,pay_deadline,base_amount_cent,overtime_price_cent,overtime_cap_cent,rule_snapshot,status)
VALUES (1,1,(SELECT id FROM gym_saas.customer_user WHERE openid='m03b-customer'),(SELECT id FROM gym_saas.store_slot WHERE store_id=1 AND start_at > UTC_TIMESTAMP(3) ORDER BY start_at LIMIT 1),'M03B-ORDER-1','TIME_FEE', UTC_TIMESTAMP(3), DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 HOUR), UTC_TIMESTAMP(3), 1000, 100, 3000, JSON_OBJECT(), 'RESERVED');
"@
$r = Call 'PUT' '/api/v1/admin/stores/1/status' @{ status = 'OPEN'; reason = 'm03b open' } @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-status-0002'; 'If-Match' = $etag2 }
Check '4.11 open store 200 OPEN' 200 $r { param($x) $x.Body -like '*"status":"OPEN"*' }
$etag3 = $r.ETag
Write-Output ('     [debug] unfulfilled orders = ' + (Sql "SELECT COUNT(*) FROM gym_saas.booking_order WHERE store_id=1 AND status IN ('PENDING_PAYMENT','RESERVED','IN_USE','CANCEL_PENDING','NO_SHOW_PENDING') AND end_at > UTC_TIMESTAMP(3);"))
$r = Call 'PUT' '/api/v1/admin/stores/1/status' @{ status = 'DISABLED'; reason = 'm03b disable blocked' } @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-status-0003'; 'If-Match' = $etag3 }
Check '4.11 disable with unfulfilled order -> 409' 409 $r
$r = Call 'PUT' '/api/v1/admin/stores/1/status' @{ status = 'OPEN'; reason = 'm03b op forbidden' } @{ Authorization = "Bearer $opToken"; 'Idempotency-Key' = 'm03b-status-0004'; 'If-Match' = $etag3 }
Check '4.11 operator (not ADMIN) -> 403' 403 $r
Sql "DELETE FROM gym_saas.booking_order;"

# ---------- 4.12 准入暂停/恢复 ----------
$r = Call 'POST' '/api/v1/admin/auth/login' @{ username = 'readop'; password = 'storeop123456' }
Check 'read-only operator login' 200 $r { param($x) $x.Body -like '*"accessToken"*' }
$readToken = ($r.Body | ConvertFrom-Json).data.accessToken
Sql "UPDATE gym_saas.store SET status='OPEN' WHERE id=1;"

$r = Call 'GET' '/api/v1/admin/stores/1' $null $H
$etag4 = $r.ETag
$r = Call 'PUT' '/api/v1/admin/stores/1/admission' @{ paused = $true; reason = 'm03b pause' } @{ Authorization = "Bearer $opToken"; 'Idempotency-Key' = 'm03b-admission-0001'; 'If-Match' = $etag4 }
Check '4.12 operator STORE_CONTROL pauses admission 200' 200 $r { param($x) ($x.Body -like '*"admissionPaused":true*') -and ($x.Body -like '*"pauseReason":"m03b pause"*') }
$etag5 = $r.ETag
$r = Call 'PUT' '/api/v1/admin/stores/1/admission' @{ paused = $false; reason = 'm03b resume' } @{ Authorization = "Bearer $opToken"; 'Idempotency-Key' = 'm03b-admission-0002'; 'If-Match' = $etag5 }
Check '4.12 resume 200 admissionPaused false' 200 $r { param($x) $x.Body -like '*"admissionPaused":false*' }
$etag6 = $r.ETag
# 仅有 STORE_READ 的操作员不能改准入
$r = Call 'GET' '/api/v1/admin/stores/1' $null $H
$etag8b = $r.ETag
$r = Call 'PUT' '/api/v1/admin/stores/1/admission' @{ paused = $true; reason = 'm03b forbidden' } @{ Authorization = "Bearer $readToken"; 'Idempotency-Key' = 'm03b-admission-0004'; 'If-Match' = $etag8b }
Check '4.12 operator without STORE_CONTROL -> 403' 403 $r
# ---------- 4.8 图片上传 ----------
$png = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==')
$r = Upload '/api/v1/admin/stores/1/images' $png @{ Authorization = "Bearer $adminToken" }
Check '4.8 upload without Idempotency-Key -> 400' 400 $r
$r = Upload '/api/v1/admin/stores/1/images' $png @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-image-0001' }
Check '4.8 upload png -> 201 objectKey/url/contentType' 201 $r { param($x) ($x.Body -like '*"objectKey":"stores/1/*') -and ($x.Body -like '*"contentType":"image/png"*') -and ($x.Body -like '*"sizeBytes"*') }
$objectKey = ($r.Body | ConvertFrom-Json).data.objectKey
Write-Output "     objectKey=$objectKey"
$r = Upload '/api/v1/admin/stores/1/images' $png @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-image-0001' }
Check '4.8 replay same key -> same objectKey' 201 $r { param($x) ($x.Body | ConvertFrom-Json).data.objectKey -eq $objectKey }
$bad = [System.Text.Encoding]::UTF8.GetBytes('not-an-image')
$r = Upload '/api/v1/admin/stores/1/images' $bad @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-image-0002' }
Check '4.8 non-image -> 415' 415 $r
# 4.7 引用刚上传的图片
$r = Call 'GET' '/api/v1/admin/stores/1' $null $H
$etag8 = $r.ETag
$r = Call 'PATCH' '/api/v1/admin/stores/1' @{ images = @($objectKey); reason = 'm03b reference image' } @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-image-ref-0001'; 'If-Match' = $etag8 }
Check '4.7 reference uploaded image -> 200' 200 $r { param($x) $x.Body -like "*$objectKey*" }

# ---------- 恢复演示规则 ----------
$r = Call 'GET' '/api/v1/admin/stores/1' $null $H
$etag9 = $r.ETag
Call 'PUT' '/api/v1/admin/stores/1/status' @{ status = 'OPEN'; reason = 'restore demo' } @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03b-restore-0001'; 'If-Match' = $etag9 } | Out-Null

$pass = ($results | Where-Object { $_ -like 'PASS*' }).Count
$fail = ($results | Where-Object { $_ -like 'FAIL*' }).Count
Write-Output ''
Write-Output "===== SUMMARY: $pass PASS / $fail FAIL ====="
