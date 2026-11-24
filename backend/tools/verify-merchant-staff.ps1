# HTTP end-to-end verification for module 3 (merchant/staff, §3.1-3.8) against local app
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:18080'
$results = New-Object System.Collections.ArrayList

function Call {
    param([string]$Method, [string]$Path, $Body, [hashtable]$Headers)
    $p = @{ Uri = ($base + $Path); Method = $Method; Headers = @{}; UseBasicParsing = $true; TimeoutSec = 20 }
    if ($Headers) { foreach ($k in $Headers.Keys) { $p.Headers[$k] = $Headers[$k] } }
    $p.Headers['Content-Type'] = 'application/json'
    if ($null -ne $Body) { $json = ($Body | ConvertTo-Json -Compress -Depth 5); $p.Body = [System.Text.Encoding]::UTF8.GetBytes($json) }
    try {
        $resp = Invoke-WebRequest @p
        $etag = ''
        if ($resp.Headers['ETag']) { $etag = $resp.Headers['ETag'] }
        # JSON 响应未带 charset 时 PowerShell 5.1 会按 Latin-1 解码，这里显式按 UTF-8 还原
        $text = $resp.Content
        try { $text = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()) } catch { }
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $text; ETag = $etag }
    } catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ Status = $status; Body = ''; ETag = '' }
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

# ---------- 准备：管理员登录 ----------
$r = Call 'POST' '/api/v1/admin/auth/login' @{ username = 'admin'; password = 'admin123456' }
Check 'admin login for m02' 200 $r { param($x) $x.Body -like '*"accessToken"*' }
$adminToken = ($r.Body | ConvertFrom-Json).data.accessToken
$adminHeaders = @{ Authorization = "Bearer $adminToken" }

# ---------- 3.1 读取本商家信息 ----
$r = Call 'GET' '/api/v1/admin/merchant' $null $adminHeaders
Check '3.1 GET merchant 200 + ETag + id/name/status' 200 $r { param($x) ($x.Body -like '*"status":"ACTIVE"*') -and ($x.Body -like '*"name":*') -and ($x.ETag -like '"*') }
$merchantEtag = $r.ETag
Write-Output "     merchant ETag=$merchantEtag"

# ---------- 3.2 修改商家名称 ----
$payload = @{ name = '演示商家(改名)'; reason = 'm02 verification' }
$r = Call 'PATCH' '/api/v1/admin/merchant' $payload @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-merchant-0001' }
Check '3.2 PATCH merchant without If-Match -> 428' 428 $r
$r = Call 'PATCH' '/api/v1/admin/merchant' $payload @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-merchant-0002'; 'If-Match' = '"wrong-etag"' }
Check '3.2 PATCH merchant wrong If-Match -> 412' 412 $r
$r = Call 'PATCH' '/api/v1/admin/merchant' $payload @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-merchant-0003'; 'If-Match' = $merchantEtag }
Check '3.2 PATCH merchant correct If-Match -> 200 + new ETag' 200 $r { param($x) ($x.Body -like '*演示商家(改名)*') -and ($x.ETag -ne '') }
$newMerchantEtag = $r.ETag
$r = Call 'PATCH' '/api/v1/admin/merchant' $payload @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-merchant-0003'; 'If-Match' = $newMerchantEtag }
Check '3.2 same idempotency key replays stored result' 200 $r
$r = Call 'PATCH' '/api/v1/admin/merchant' @{ name = 'x'; reason = 'different' } @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-merchant-0003'; 'If-Match' = $newMerchantEtag }
Check '3.2 same key different body -> 409' 409 $r
$r = Call 'PATCH' '/api/v1/admin/merchant' $payload @{ Authorization = "Bearer $adminToken"; 'If-Match' = $newMerchantEtag }
Check '3.2 missing Idempotency-Key -> 400' 400 $r

# ---------- 3.3 员工列表 ----
$r = Call 'GET' '/api/v1/admin/staff?page=1&pageSize=20' $null $adminHeaders
Check '3.3 staff list 200 with page/pageSize/total/items' 200 $r { param($x) ($x.Body -like '*"page":1*') -and ($x.Body -like '*"pageSize":20*') -and ($x.Body -like '*"items":*') }

# ---------- 3.4 员工详情 ----
$adminId = ($r.Body | ConvertFrom-Json).data.items | Where-Object { $_.username -eq 'admin' } | Select-Object -First 1 -ExpandProperty id
$r = Call 'GET' "/api/v1/admin/staff/$adminId" $null $adminHeaders
Check '3.4 staff detail 200 + ETag + no password hash' 200 $r { param($x) ($x.ETag -ne '') -and ($x.Body -notlike '*passwordHash*') }
$adminStaffEtag = $r.ETag

# ---------- 3.5 创建员工 ----
$create = @{ username = 'operator01'; initialPassword = 'operator123456'; role = 'OPERATOR'; permissions = @('ORDER_READ'); reason = 'm02 verification create' }
$r = Call 'POST' '/api/v1/admin/staff' $create @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-staff-create-0001' }
Check '3.5 create operator -> 201' 201 $r { param($x) $x.Body -like '*"username":"operator01"*' }
$operatorId = ($r.Body | ConvertFrom-Json).data.id
Write-Output "     operatorId=$operatorId"
$r = Call 'POST' '/api/v1/admin/staff' $create @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-staff-create-0002' }
Check '3.5 duplicate username -> 409' 409 $r
$badAdmin = @{ username = 'admin02'; initialPassword = 'admin02123456'; role = 'ADMIN'; permissions = @('ORDER_READ'); reason = 'should fail' }
$r = Call 'POST' '/api/v1/admin/staff' $badAdmin @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-staff-create-0003' }
Check '3.5 ADMIN with non-empty permissions -> 400' 400 $r
$badPerm = @{ username = 'operator02'; initialPassword = 'operator123456'; role = 'OPERATOR'; permissions = @('NOT_A_PERMISSION'); reason = 'should fail' }
$r = Call 'POST' '/api/v1/admin/staff' $badPerm @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-staff-create-0004' }
Check '3.5 invalid permission code -> 400' 400 $r

# ---------- 3.4 操作员视角 + 权限边界 ----
$r = Call 'POST' '/api/v1/admin/auth/login' @{ username = 'operator01'; password = 'operator123456' }
Check 'operator login 200' 200 $r { param($x) $x.Body -like '*"accessToken"*' }
$operatorToken = ($r.Body | ConvertFrom-Json).data.accessToken
$r = Call 'GET' '/api/v1/admin/merchant' $null @{ Authorization = "Bearer $operatorToken" }
Check '3.1 operator can read own merchant (any authenticated admin)' 200 $r
$r = Call 'GET' '/api/v1/admin/staff?page=1&pageSize=10' $null @{ Authorization = "Bearer $operatorToken" }
Check '3.3 operator forbidden on staff list -> 403' 403 $r

# ---------- 3.6 调整角色权限 ----
$promote = @{ role = 'ADMIN'; permissions = @(); reason = 'promote to admin for test' }
$r = Call 'PATCH' "/api/v1/admin/staff/$operatorId" $promote @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-staff-role-0001' }
Check '3.6 promote requires If-Match -> 428' 428 $r
$r = Call 'GET' "/api/v1/admin/staff/$operatorId" $null $adminHeaders
$opEtag = $r.ETag
Write-Output "     operator ETag=$opEtag"
$r = Call 'PATCH' "/api/v1/admin/staff/$operatorId" $promote @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-staff-role-0002'; 'If-Match' = $opEtag }
Check '3.6 promote operator -> ADMIN 200' 200 $r { param($x) $x.Body -like '*"role":"ADMIN"*' }
$promotedEtag = $r.ETag
$demote = @{ role = 'OPERATOR'; permissions = @('ORDER_READ', 'CUSTOMER_READ'); reason = 'demote back' }
$r = Call 'PATCH' "/api/v1/admin/staff/$operatorId" $demote @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-staff-role-0003'; 'If-Match' = $promotedEtag }
Check '3.6 demote operator with valid perms 200' 200 $r { param($x) $x.Body -like '*"role":"OPERATOR"*' -and $x.Body -like '*CUSTOMER_READ*' }
$demotedEtag = $r.ETag
$selfDemote = @{ role = 'OPERATOR'; permissions = @('ORDER_READ'); reason = 'should be blocked' }
$r = Call 'PATCH' "/api/v1/admin/staff/$adminId" $selfDemote @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-staff-role-0004'; 'If-Match' = $adminStaffEtag }
Check '3.6 demote last active admin -> 409' 409 $r

# ---------- 3.7 启停账号 ----
$disable = @{ status = 'DISABLED'; reason = 'm02 disable test' }
$r = Call 'GET' "/api/v1/admin/staff/$operatorId" $null $adminHeaders
$opEtag2 = $r.ETag
$r = Call 'PUT' "/api/v1/admin/staff/$operatorId/status" $disable @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-staff-status-0001'; 'If-Match' = $opEtag2 }
Check '3.7 disable operator 200 status DISABLED' 200 $r { param($x) $x.Body -like '*"status":"DISABLED"*' }
$r = Call 'GET' "/api/v1/admin/staff/$operatorId" $null @{ Authorization = "Bearer $operatorToken" }
Check '3.7 disabled operator old JWT -> 401' 401 $r
$r = Call 'PUT' "/api/v1/admin/staff/$adminId/status" $disable @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-staff-status-0002'; 'If-Match' = $adminStaffEtag }
Check '3.7 disable last active admin -> 409' 409 $r
$enable = @{ status = 'ACTIVE'; reason = 'm02 enable test' }
$r = Call 'GET' "/api/v1/admin/staff/$operatorId" $null $adminHeaders
$opEtag3 = $r.ETag
$r = Call 'PUT' "/api/v1/admin/staff/$operatorId/status" $enable @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-staff-status-0003'; 'If-Match' = $opEtag3 }
Check '3.7 re-enable operator 200 status ACTIVE' 200 $r { param($x) $x.Body -like '*"status":"ACTIVE"*' }

# ---------- 3.8 重置密码 ----
$reset = @{ newPassword = 'operator654321'; reason = 'm02 reset test' }
$r = Call 'POST' "/api/v1/admin/staff/$operatorId/password-reset" $reset @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm02-staff-reset-0001' }
Check '3.8 reset password -> accepted only' 200 $r { param($x) ($x.Body -like '*"accepted":true*') -and ($x.Body -notlike '*password*') }
$r = Call 'GET' "/api/v1/admin/staff/$operatorId" $null @{ Authorization = "Bearer $operatorToken" }
Check '3.8 after reset operator old JWT -> 401' 401 $r
$r = Call 'POST' '/api/v1/admin/auth/login' @{ username = 'operator01'; password = 'operator654321' }
Check '3.8 operator login with new password -> 200' 200 $r { param($x) $x.Body -like '*"accessToken"*' }

# ---------- 商家隔离（第二个商家） ----------
$r = Call 'POST' '/api/v1/admin/auth/login' @{ username = 'admin2'; password = 'admin123456' }
Check 'merchant2 admin login -> 200' 200 $r { param($x) $x.Body -like '*"accessToken"*' }
$admin2Token = ($r.Body | ConvertFrom-Json).data.accessToken
$r = Call 'GET' "/api/v1/admin/staff/$operatorId" $null @{ Authorization = "Bearer $admin2Token" }
Check 'isolation: other merchant staff detail -> 404' 404 $r
$r = Call 'GET' '/api/v1/admin/merchant' $null @{ Authorization = "Bearer $admin2Token" }
Check 'isolation: admin2 sees own merchant id=2' 200 $r { param($x) $x.Body -like '*"id":"2"*' }

$pass = ($results | Where-Object { $_ -like 'PASS*' }).Count
$fail = ($results | Where-Object { $_ -like 'FAIL*' }).Count
Write-Output ''
Write-Output "===== SUMMARY: $pass PASS / $fail FAIL ====="
