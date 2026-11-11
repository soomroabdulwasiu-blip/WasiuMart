$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Join-Path $PSScriptRoot 'frontend')
$vite = Join-Path (Get-Location) 'node_modules\vite\bin\vite.js'
if (-not (Test-Path -LiteralPath $vite)) { throw 'Frontend dependencies are missing. Install Node.js and run npm install in frontend first.' }
$node = Get-Command node.exe -ErrorAction SilentlyContinue
if ($node) { $nodePath = $node.Source }
else {
    $nodePath = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
    if (-not (Test-Path -LiteralPath $nodePath)) { throw 'Node.js is not available. Install Node.js and reopen VS Code.' }
}
& $nodePath $vite build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host 'Frontend built. Restart the running backend to load the updated build.'
