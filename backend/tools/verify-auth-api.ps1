# HTTP end-to-end verification for module 2 (auth/profile) against local app on 18080
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:18080'
$results = New-Object System.Collections.ArrayList

function Call {
    param([string]$Method, [string]$Path, $Body, [hashtable]$Headers)
    $p = @{ Uri = ($base + $Path); Method = $Method; Headers = @{}; UseBasicParsing = $true; TimeoutSec = 15 }
    if ($Headers) { foreach ($k in $Headers.Keys) { $p.Headers[$k] = $Headers[$k] } }
    $p.Headers['Content-Type'] = 'application/json'
    if ($null -ne $Body) { $p.Body = ($Body | ConvertTo-Json -Compress) }
    try {
        $resp = Invoke-WebRequest @p
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $resp.Content }
    } catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ Status = $status; Body = '' }
    }
}

function Check {
    param([string]$Name, [int]$Expected, $Result, [scriptblock]$Assert)
    $ok = ($Result.Status -eq $Expected)
    if ($ok -and $Assert) { $ok = [bool](& $Assert $Result.Body) }
    if (-not $ok) {
        Write-Output ("     [debug] body=[" + $Result.Body + "]")
        Write-Output ("     [debug] assert=" + $Assert.ToString().Trim())
        Write-Output ("     [debug] raw=" + [bool](& $Assert $Result.Body))
    }
    $tag = if ($ok) { 'PASS' } else { "FAIL(status=$($Result.Status)/expected=$Expected)" }
    [void]$results.Add($tag)
    Write-Output ("$tag  $Name")
}

# 2.2 admin login
$r = Call 'POST' '/api/v1/admin/auth/login' @{ username = 'admin'; password = 'admin123456' }
Check '2.2 admin login 200 + token' 200 $r { param($b) $b -like '*"accessToken"*' -and $b -like '*"tokenType":"Bearer"*' }
$adminToken = ($r.Body | ConvertFrom-Json).data.accessToken

# 2.9 admin me
$r = Call 'GET' '/api/v1/admin/me' $null @{ Authorization = "Bearer $adminToken" }
Check '2.9 admin me 200 (string ids)' 200 $r { param($b) $b -like '*"role":"ADMIN"*' -and $b -like '*"merchantId":"*' }

# 2.1 wechat login (M1 stub)
$r = Call 'POST' '/api/v1/app/auth/wechat-login' @{ code = 'dev-code-1' }
Check '2.1 wechat login 200 (field customer, id string)' 200 $r { param($b) $b -like '*"customer":{"id":"*' -and $b -like '*"nickname":null*' -and $b -like '*"expiresIn":7200*' }
$customerToken = ($r.Body | ConvertFrom-Json).data.accessToken
$customerId = ($r.Body | ConvertFrom-Json).data.customer.id
Write-Output "     customerId=$customerId"

# 2.5 app me
$r = Call 'GET' '/api/v1/app/me' $null @{ Authorization = "Bearer $customerToken" }
Check '2.5 app me 200, explicit nulls, no sensitive fields' 200 $r { param($b) ($b -notlike '*openid*') -and ($b -notlike '*ciphertext*') -and ($b -like '*"phoneMasked":null*') }

# 2.6 nickname + idempotency
$h = @{ Authorization = "Bearer $customerToken"; 'Idempotency-Key' = 'test-nickname-0001' }
$r = Call 'PATCH' '/api/v1/app/me' @{ nickname = 'Ming' } $h
Check '2.6 patch nickname 200' 200 $r { param($b) $b -like '*"nickname":"Ming"*' }
$firstData = (($r.Body | ConvertFrom-Json).data | ConvertTo-Json -Compress)
$r = Call 'PATCH' '/api/v1/app/me' @{ nickname = 'Ming' } $h
$replayData = (($r.Body | ConvertFrom-Json).data | ConvertTo-Json -Compress)
Check '2.6 replay same key+body -> same data' 200 $r { param($b) $replayData -eq $firstData }
$r = Call 'PATCH' '/api/v1/app/me' @{ nickname = 'Hong' } $h
Check '2.6 same key different body -> 409' 409 $r
$r = Call 'PATCH' '/api/v1/app/me' @{ nickname = 'Hong' } @{ Authorization = "Bearer $customerToken" }
Check '2.6 missing Idempotency-Key -> 400' 400 $r

# 2.7 bind phone
$h = @{ Authorization = "Bearer $customerToken"; 'Idempotency-Key' = 'test-phone-bind-0001' }
$r = Call 'POST' '/api/v1/app/me/phone-binding' @{ phoneCode = 'phone-code-1' } $h
Check '2.7 bind phone 200 masked' 200 $r { param($b) $b -like '*"phoneVerified":true*' -and $b -like '*"phoneMasked":"138*****' }
Write-Output ("     bindBody=" + $r.Body)

# 2.8 unbind phone
$h = @{ Authorization = "Bearer $customerToken"; 'Idempotency-Key' = 'test-phone-unbind-0001' }
$r = Call 'DELETE' '/api/v1/app/me/phone-binding' $null $h
Check '2.8 unbind -> phoneMasked null, phoneVerified false' 200 $r { param($b) $b -like '*"phoneMasked":null*' -and $b -like '*"phoneVerified":false*' }

# 2.3 customer logout -> old token 401
$r = Call 'POST' '/api/v1/app/auth/logout' @{} @{ Authorization = "Bearer $customerToken" }
Check '2.3 customer logout 200' 200 $r { param($b) $b -like '*"accepted":true*' }
$r = Call 'GET' '/api/v1/app/me' $null @{ Authorization = "Bearer $customerToken" }
Check '2.3 old customer JWT -> 401' 401 $r

# 2.10 change admin password
$r = Call 'POST' '/api/v1/admin/me/password' @{ oldPassword = 'wrong-password'; newPassword = 'admin123456789' } @{ Authorization = "Bearer $adminToken" }
Check '2.10 wrong old password -> 401' 401 $r
$r = Call 'POST' '/api/v1/admin/me/password' @{ oldPassword = 'admin123456'; newPassword = 'admin123456789' } @{ Authorization = "Bearer $adminToken" }
Check '2.10 change password 200' 200 $r { param($b) $b -like '*"accepted":true*' }
$r = Call 'GET' '/api/v1/admin/me' $null @{ Authorization = "Bearer $adminToken" }
Check '2.10 old admin JWT after change -> 401' 401 $r
$r = Call 'POST' '/api/v1/admin/auth/login' @{ username = 'admin'; password = 'admin123456789' }
Check '2.10 login with new password 200' 200 $r { param($b) $b -like '*"accessToken"*' }
$newAdminToken = ($r.Body | ConvertFrom-Json).data.accessToken

# 2.4 admin logout
$r = Call 'POST' '/api/v1/admin/auth/logout' @{} @{ Authorization = "Bearer $newAdminToken" }
Check '2.4 admin logout 200' 200 $r { param($b) $b -like '*"accepted":true*' }
$r = Call 'GET' '/api/v1/admin/me' $null @{ Authorization = "Bearer $newAdminToken" }
Check '2.4 old admin JWT after logout -> 401' 401 $r

$pass = ($results | Where-Object { $_ -like 'PASS*' }).Count
$fail = ($results | Where-Object { $_ -like 'FAIL*' }).Count
Write-Output ''
Write-Output "===== SUMMARY: $pass PASS / $fail FAIL ====="
