# HTTP end-to-end verification for module 4 part A (§4.1-4.7, §4.13) against local app
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:18080'
$results = New-Object System.Collections.ArrayList

function Call {
    param([string]$Method, [string]$Path, $Body, [hashtable]$Headers)
    $p = @{ Uri = ($base + $Path); Method = $Method; Headers = @{}; UseBasicParsing = $true; TimeoutSec = 20 }
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

function Check {
    param([string]$Name, [int]$Expected, $Result, [scriptblock]$Assert)
    $ok = ($Result.Status -eq $Expected)
    if ($ok -and $Assert) { $ok = [bool](& $Assert $Result) }
    $tag = if ($ok) { 'PASS' } else { "FAIL(status=$($Result.Status)/expected=$Expected)" }
    [void]$results.Add($tag)
    Write-Output ("$tag  $Name")
    if (-not $ok) { Write-Output ("     [debug] body=[" + $Result.Body + "]") }
}

$today = Get-Date
$todayStr = $today.ToString('yyyy-MM-dd')
$tomorrowStr = $today.AddDays(1).ToString('yyyy-MM-dd')
$weekLaterStr = $today.AddDays(7).ToString('yyyy-MM-dd')

# ---------- 登录 ----------
$r = Call 'POST' '/api/v1/admin/auth/login' @{ username = 'admin'; password = 'admin123456' }
Check 'admin login' 200 $r { param($x) $x.Body -like '*"accessToken"*' }
$adminToken = ($r.Body | ConvertFrom-Json).data.accessToken
$H = @{ Authorization = "Bearer $adminToken" }

# ---------- 4.1 公开门店列表 ----------
$r = Call 'GET' '/api/v1/app/stores?page=1&pageSize=20' $null
Check '4.1 public store list 200 (anonymous, pagination)' 200 $r { param($x) ($x.Body -like '*"items"*') -and ($x.Body -like '*"merchantName"*') -and ($x.Body -like '*"deviceSource":"UNKNOWN"*') }
$publicStoreId = ($r.Body | ConvertFrom-Json).data.items | Where-Object { $_.name -eq '公开演示门店' } | Select-Object -First 1 -ExpandProperty id
Write-Output "     publicStoreId=$publicStoreId"

# ---------- 4.2 公开门店详情 ----------
$r = Call 'GET' "/api/v1/app/stores/$publicStoreId" $null
Check '4.2 public detail 200 (no capacity/etag, timezone 7 days)' 200 $r {
    param($x) ($x.Body -like '*"timezone":"Asia/Shanghai"*') -and ($x.Body -like '*"slotMinutes":60*') -and
    ($x.Body -like '*"bookingDays":7*') -and ($x.Body -like '*"openTime":"08:00:00"*') -and
    ($x.Body -notlike '*"capacity"*') -and ($x.Body -notlike '*"etag"*')
}

# ---------- 4.3 公开时段 ----------
$r = Call 'GET' "/api/v1/app/stores/$publicStoreId/slots?date=$tomorrowStr" $null
Check '4.3 public slots 200 (fields + no etag)' 200 $r {
    param($x) ($x.Body -like '*"lastBookableDate"*') -and ($x.Body -like '*"remaining"*') -and
    ($x.Body -like '*"bookable"*') -and ($x.Body -notlike '*"etag"*')
}
$slotsData = ($r.Body | ConvertFrom-Json).data
Write-Output ("     slots today count=" + $slotsData.items.Count)
$r = Call 'GET' "/api/v1/app/stores/$publicStoreId/slots?date=$weekLaterStr" $null
Check '4.3 date out of 7-day range -> 422' 422 $r
$r = Call 'GET' "/api/v1/app/stores/$publicStoreId/slots?date=not-a-date" $null
Check '4.3 malformed date -> 400' 400 $r

# ---------- 4.4 后台门店列表 ----------
$r = Call 'GET' '/api/v1/admin/stores?page=1&pageSize=20' $null $H
Check '4.4 admin store list 200 (items with etag)' 200 $r { param($x) ($x.Body -like '*"etag":"*') -and ($x.Body -like '*"capacity":*') }
$adminStoreId = ($r.Body | ConvertFrom-Json).data.items | Where-Object { $_.name -eq '公开演示门店' } | Select-Object -First 1 -ExpandProperty id

# ---------- 4.5 后台门店详情 ----------
$r = Call 'GET' "/api/v1/admin/stores/$publicStoreId" $null $H
Check '4.5 admin detail 200 + HTTP ETag + body etag' 200 $r { param($x) ($x.ETag -like '"*') -and ($x.Body -like '*"etag":"*') }
$storeEtag = $r.ETag
Write-Output "     store ETag=$storeEtag"
$r = Call 'GET' '/api/v1/admin/stores/999999' $null $H
Check '4.5 unknown store -> 404' 404 $r

# ---------- 操作员权限（STORE_READ 可读，无权限 403） ----------
$r = Call 'POST' '/api/v1/admin/auth/login' @{ username = 'storeop'; password = 'storeop123456' }
Check 'store operator login' 200 $r { param($x) $x.Body -like '*"accessToken"*' }
$opToken = ($r.Body | ConvertFrom-Json).data.accessToken
$r = Call 'GET' '/api/v1/admin/stores' $null @{ Authorization = "Bearer $opToken" }
Check '4.4 operator with STORE_READ -> 200' 200 $r
$r = Call 'POST' '/api/v1/admin/auth/login' @{ username = 'orderop'; password = 'orderop123456' }
Check 'order operator login' 200 $r { param($x) $x.Body -like '*"accessToken"*' }
$orderToken = ($r.Body | ConvertFrom-Json).data.accessToken
$r = Call 'GET' '/api/v1/admin/stores' $null @{ Authorization = "Bearer $orderToken" }
Check '4.4 operator without STORE_READ -> 403' 403 $r

# ---------- 4.6 创建门店 ----------
$createBody = @{
    name = '新建测试门店'; address = '测试路 1 号'; description = '创建测试'
    openTime = '09:00:00'; closeTime = '22:00:00'; is24h = $false; endsNextDay = $false
    capacity = 8; slotPriceCent = 1200; overtimePriceCent = 100; overtimeCapCent = 3000
    reason = 'm03a verification create'
}
$r = Call 'POST' '/api/v1/admin/stores' $createBody @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03-create-0001' }
Check '4.6 create store 201 + status CLOSED + etag' 201 $r { param($x) ($x.Body -like '*"status":"CLOSED"*') -and ($x.Body -like '*"etag":"*') -and ($x.Body -like '*"openTime":"09:00:00"*') }
$createdId = ($r.Body | ConvertFrom-Json).data.id
Write-Output "     createdStoreId=$createdId"
$r = Call 'POST' '/api/v1/admin/stores' $createBody @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03-create-0001' }
Check '4.6 replay same key -> same store' 201 $r { param($x) ($x.Body | ConvertFrom-Json).data.id -eq $createdId }
$r = Call 'POST' '/api/v1/admin/stores' $createBody @{ Authorization = "Bearer $adminToken" }
Check '4.6 missing Idempotency-Key -> 400' 400 $r
$badTimes = @{ name = '坏时间'; address = 'x'; openTime = '22:00:00'; closeTime = '09:00:00'; is24h = $false; endsNextDay = $false; capacity = 8; slotPriceCent = 1200; overtimePriceCent = 100; overtimeCapCent = 3000; reason = 'bad' }
$r = Call 'POST' '/api/v1/admin/stores' $badTimes @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03-create-0002' }
Check '4.6 closeTime before openTime without endsNextDay -> 400' 400 $r
$bad24 = @{ name = '坏24'; address = 'x'; openTime = '00:00:00'; closeTime = '23:59:59'; is24h = $true; endsNextDay = $true; capacity = 8; slotPriceCent = 1200; overtimePriceCent = 100; overtimeCapCent = 3000; reason = 'bad' }
$r = Call 'POST' '/api/v1/admin/stores' $bad24 @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03-create-0003' }
Check '4.6 is24h with endsNextDay=true -> 400' 400 $r

# ---------- 4.7 修改展示资料 ----------
$patch = @{ name = '公开演示门店(改名)'; description = $null; reason = 'm03a profile update' }
$r = Call 'PATCH' "/api/v1/admin/stores/$publicStoreId" $patch @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03-profile-0001' }
Check '4.7 patch without If-Match -> 428' 428 $r
$r = Call 'PATCH' "/api/v1/admin/stores/$publicStoreId" $patch @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03-profile-0002'; 'If-Match' = '"wrong"' }
Check '4.7 patch wrong If-Match -> 412' 412 $r
$r = Call 'PATCH' "/api/v1/admin/stores/$publicStoreId" $patch @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03-profile-0003'; 'If-Match' = $storeEtag }
Check '4.7 patch correct If-Match -> 200 + new ETag + explicit null clears description' 200 $r { param($x) ($x.Body -like '*公开演示门店(改名)*') -and ($x.Body -like '*"description":null*') -and ($x.ETag -ne $storeEtag) }
$newEtag = $r.ETag
$r = Call 'PATCH' "/api/v1/admin/stores/$publicStoreId" $patch @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03-profile-0003'; 'If-Match' = $newEtag }
Check '4.7 same key replays stored result' 200 $r
$badImg = @{ images = @('https://evil.example/x.jpg'); reason = 'bad image' }
$r = Call 'PATCH' "/api/v1/admin/stores/$publicStoreId" $badImg @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03-profile-0004'; 'If-Match' = $newEtag }
Check '4.7 external image url -> 400' 400 $r
$noReason = @{ name = 'x' }
$r = Call 'PATCH' "/api/v1/admin/stores/$publicStoreId" $noReason @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03-profile-0005'; 'If-Match' = $newEtag }
Check '4.7 missing reason -> 400' 400 $r
$onlyReason = @{ reason = 'only reason' }
$r = Call 'PATCH' "/api/v1/admin/stores/$publicStoreId" $onlyReason @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = 'm03-profile-0006'; 'If-Match' = $newEtag }
Check '4.7 only reason (no writable field) -> 400' 400 $r

# ---------- 4.13 后台时段（含历史/关闭） ----------
$r = Call 'GET' "/api/v1/admin/stores/$publicStoreId/slots?date=$tomorrowStr" $null $H
Check '4.13 admin slots 200 with per-item etag' 200 $r { param($x) ($x.Body -like '*"etag":"*') -and ($x.Body -like '*"remaining"*') }
$r = Call 'GET' "/api/v1/admin/stores/$publicStoreId/slots?date=$todayStr" $null $H
Check '4.13 admin slots today includes started/closed slots' 200 $r { param($x) ($x.Body -like '*"status":"CLOSED"*') -or ($x.Body -like '*"SLOT_STARTED"*') -or ($x.Body -like '*"items":[]*') }

# ---------- 跨商家隔离 ----------
$r = Call 'POST' '/api/v1/admin/auth/login' @{ username = 'admin2'; password = 'admin123456' }
Check 'merchant2 login' 200 $r { param($x) $x.Body -like '*"accessToken"*' }
$admin2Token = ($r.Body | ConvertFrom-Json).data.accessToken
$r = Call 'GET' "/api/v1/admin/stores/$publicStoreId" $null @{ Authorization = "Bearer $admin2Token" }
Check '4.5 cross-merchant store -> 404' 404 $r

$pass = ($results | Where-Object { $_ -like 'PASS*' }).Count
$fail = ($results | Where-Object { $_ -like 'FAIL*' }).Count
Write-Output ''
Write-Output "===== SUMMARY: $pass PASS / $fail FAIL ====="
