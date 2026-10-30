# Start admin console pointing at a remote broker
# Loads GATEWAY_URL from ../aws.env (copy aws.env.example to aws.env and fill in values)

$envFile = Join-Path $PSScriptRoot "..\aws.env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*[^#]' } | ForEach-Object {
        $parts = $_ -split '=', 2
        if ($parts.Length -eq 2) { [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process") }
    }
}

if (-not $env:GATEWAY_URL) { Write-Host "ERROR: Set GATEWAY_URL in ../aws.env (copy aws.env.example)" -ForegroundColor Red; exit 1 }

Set-Content -Path .env.local -Value "VITE_GATEWAY_URL=$env:GATEWAY_URL"
Write-Host "Admin Console → $env:GATEWAY_URL" -ForegroundColor Cyan
npm run dev
