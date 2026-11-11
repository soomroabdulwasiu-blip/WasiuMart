$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Join-Path $PSScriptRoot 'backend')
$mvn = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($mvn) { $mavenPath = $mvn.Source }
else { $mavenPath = 'C:\Program Files\apache-maven-3.9.14\bin\mvn.cmd' }
if (-not (Test-Path -LiteralPath $mavenPath)) { throw 'Maven not found. Install Maven and reopen VS Code.' }
$cache = Join-Path $PSScriptRoot '.maven-cache'
if (Test-Path -LiteralPath $cache) { & $mavenPath '-B' ("-Dmaven.repo.local=$cache") 'test' }
else { & $mavenPath '-B' 'test' }
exit $LASTEXITCODE
