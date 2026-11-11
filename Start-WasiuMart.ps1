param([switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
$backend = Join-Path $project 'backend'
$frontendIndex = Join-Path $project 'frontend\dist\index.html'
$runner = Join-Path $project 'Run-WasiuMart-Server.ps1'
$logDir = Join-Path $project 'logs'
$baseUrl = 'http://127.0.0.1:8080'

function Test-WasiuMart {
    try {
        $health = Invoke-RestMethod -Uri "$baseUrl/api/health" -TimeoutSec 2
        $page = Invoke-WebRequest -Uri "$baseUrl/" -UseBasicParsing -TimeoutSec 2
        return ($health.status -eq 'ok' -and $health.database -eq 'connected' -and $page.StatusCode -eq 200 -and $page.Content -match 'WasiuMart')
    } catch { return $false }
}
function Test-PortInUse {
    $tcp = New-Object System.Net.Sockets.TcpClient
    try { $wait = $tcp.BeginConnect('127.0.0.1',8080,$null,$null); return $wait.AsyncWaitHandle.WaitOne(400) -and $tcp.Connected } catch { return $false } finally { $tcp.Close() }
}
if (-not (Test-Path -LiteralPath $frontendIndex)) { throw "Frontend build not found at $frontendIndex. Build the frontend first; see README.md." }
if (-not (Test-Path -LiteralPath (Join-Path $backend 'wasiumart.db'))) { throw 'Database file not found in the backend folder. Restore your database backup before starting.' }
if (Test-WasiuMart) { Write-Host "WasiuMart is already running at $baseUrl" }
else {
    if (Test-PortInUse) { throw "Port 8080 is already in use, but it is not serving this WasiuMart app. Stop the other service and try again." }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java 21 not found. Install a JDK and restart this launcher.' }
    if (-not (Get-Command mvn.cmd -ErrorAction SilentlyContinue) -and -not (Test-Path -LiteralPath 'C:\Program Files\apache-maven-3.9.14\bin\mvn.cmd')) { throw 'Maven not found. Install Maven and restart this launcher.' }
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    $outLog = Join-Path $logDir 'server-output.log'
    $errLog = Join-Path $logDir 'server-error.log'
    $server = Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"{0}"' -f $runner)) -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru
    Write-Host 'Starting WasiuMart. Please wait...'
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        if (Test-WasiuMart) { $ready = $true; break }
        if ($server.HasExited) { break }
        Start-Sleep -Seconds 1
        $server.Refresh()
    }
    if (-not $ready) { throw "WasiuMart did not start. Check logs in $logDir. Your database has not been removed." }
    Write-Host "WasiuMart is ready at $baseUrl"
}
if (-not $NoBrowser) { Start-Process $baseUrl }
