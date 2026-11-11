$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
Set-Location -LiteralPath (Join-Path $project 'backend')
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($maven) { $mavenPath = $maven.Source } else { $mavenPath = 'C:\Program Files\apache-maven-3.9.14\bin\mvn.cmd' }
$cache = Join-Path $project '.maven-cache'
if (Test-Path -LiteralPath $cache) { & $mavenPath '-B' ("-Dmaven.repo.local=$cache") 'spring-boot:run' }
else { & $mavenPath '-B' 'spring-boot:run' }
exit $LASTEXITCODE
