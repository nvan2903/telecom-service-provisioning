# Local development

## Prerequisites

- JDK 25
- Docker Desktop with Linux containers

## Start PostgreSQL and Kafka

From the repository root, set a local database password in PowerShell; do not commit it:

```powershell
$env:POSTGRES_PASSWORD = Read-Host "Local PostgreSQL password"
$env:DB_PASSWORD = $env:POSTGRES_PASSWORD
docker compose up -d postgres kafka
```

For instructions on changing a password for an existing Compose volume, see the root
`README.md`.

## Run the application and tests

```powershell
.\mvnw.cmd spring-boot:run
```

The application uses Spring Security defaults. Copy the generated `user` password from
the application startup log. In another PowerShell window, seed a synthetic package and
exercise the authenticated API:

```powershell
docker exec telecom-postgres psql -U telecom_user -d telecom_db -c "INSERT INTO service_package (code, name, active) VALUES ('DEMO-MOBILE-10', 'Demo Mobile 10 GB', TRUE) ON CONFLICT (code) DO NOTHING;"

$baseUrl = "http://localhost:8080"
$credential = Get-Credential -UserName "user"
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$basicValue = [Convert]::ToBase64String(
    [Text.Encoding]::UTF8.GetBytes(
        "$($credential.UserName):$($credential.GetNetworkCredential().Password)"))
$headers = @{ Authorization = "Basic $basicValue" }

$loginPage = Invoke-WebRequest -Uri "$baseUrl/login" -WebSession $session -Headers $headers
$csrfMatch = [regex]::Match(
    $loginPage.Content,
    '<input\b(?=[^>]*\bname="_csrf")(?=[^>]*\bvalue="([^"]+)")[^>]*>')
if (-not $csrfMatch.Success) {
    throw "Could not read the CSRF token from the local login page."
}
$csrfParameter = [uri]::EscapeDataString($csrfMatch.Groups[1].Value)

$packages = Invoke-RestMethod -Uri "$baseUrl/api/service-packages" `
    -WebSession $session -Headers $headers
$package = $packages | Where-Object { $_.code -eq "DEMO-MOBILE-10" } |
    Select-Object -First 1
if ($null -eq $package) {
    throw "The demo package was not returned by the API."
}

$idempotencyKey = [guid]::NewGuid().ToString()
$requestBody = @{
    customerRef = "demo-customer-1"
    packageId = $package.id
} | ConvertTo-Json
$postHeaders = @{} + $headers
$postHeaders["Idempotency-Key"] = $idempotencyKey
$created = Invoke-WebRequest `
    -Uri ("{0}/api/subscriptions?_csrf={1}" -f $baseUrl, $csrfParameter) -Method Post `
    -WebSession $session -Headers $postHeaders -ContentType "application/json" `
    -Body $requestBody
$subscription = $created.Content | ConvertFrom-Json
$subscription

for ($attempt = 0; $attempt -lt 30; $attempt++) {
    Start-Sleep -Seconds 1
    $subscription = Invoke-RestMethod `
        -Uri "$baseUrl/api/subscriptions/$($subscription.id)" `
        -WebSession $session -Headers $headers
    if ($subscription.status -eq "ACTIVE") {
        break
    }
}
if ($subscription.status -ne "ACTIVE") {
    throw "Subscription did not become ACTIVE within 30 seconds."
}
$subscription
```

The API accepts the request with `202 Accepted` and initially returns `PENDING`; Kafka
publication and activation happen asynchronously. The default local CSRF form token is
passed as a request parameter for this demo only; production API clients should use a
deliberately configured security and CSRF integration, not copy this query-string pattern.
A new idempotency key is used for each demo run. Keep the generated password in memory
only and never commit it.

Run tests:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -Pintegration-tests verify
```

The integration profile starts disposable PostgreSQL and Kafka containers through
Testcontainers. Stop the Compose dependencies when finished:

```powershell
docker compose down
```
