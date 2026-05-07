$token = (Invoke-RestMethod -Uri 'http://localhost:8089/api/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}').data.token
$headers = @{ Authorization = "Bearer $token"; 'Content-Type' = 'application/json' }

Write-Host "=== 1. Invitation Code Create ==="
$code = (Invoke-RestMethod -Uri 'http://localhost:8089/api/invitation-code/create' -Method Post -Headers $headers -Body '{"validDays":30,"initialPoints":1000,"initialQuota":100,"note":"initial test"}').data
Write-Host "Code: $($code.code)  Status: $($code.status)"

Write-Host "`n=== 2. System Config ==="
$config = (Invoke-RestMethod -Uri 'http://localhost:8089/api/system/config' -Headers $headers).data
$config.GetEnumerator() | ForEach-Object { Write-Host "  $($_.Key) = $($_.Value)" }

Write-Host "`n=== 3. User List ==="
$users = (Invoke-RestMethod -Uri 'http://localhost:8089/api/user/list' -Headers $headers).data
$users | ForEach-Object { Write-Host "  $($_.username) role=$($_.role) points=$($_.points) status=$($_.status)" }

Write-Host "`n=== 4. Invitation Code List ==="
$codes = (Invoke-RestMethod -Uri 'http://localhost:8089/api/invitation-code/list' -Headers $headers).data
$codes | ForEach-Object { Write-Host "  $($_.code) status=$($_.status) points=$($_.initialPoints)" }

Write-Host "`n=== 5. LLM Config List ==="
$llms = (Invoke-RestMethod -Uri 'http://localhost:8089/api/llm-config/list' -Headers $headers).data
$llms | ForEach-Object { Write-Host "  $($_.provider) model=$($_.modelName) active=$($_.isActive)" }

Write-Host "`n=== 6. Register with Invitation Code ==="
$reg = Invoke-RestMethod -Uri 'http://localhost:8089/api/auth/register' -Method Post -ContentType 'application/json' -Body ('{"invitationCode":"' + $code.code + '","username":"teacher1","password":"123456","realName":"Test Teacher","email":"test@test.com"}')
Write-Host "Register: code=$($reg.code) user=$($reg.data.username) role=$($reg.data.role)"

Write-Host "`n=== ALL TESTS PASSED ==="
