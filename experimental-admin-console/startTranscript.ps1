# Run the admin console dev server against an already-deployed broker
# (for example one behind CloudFront on AWS ECS), rather than localhost.
#
# Set GATEWAY_URL in aws.env (see aws.env.example) or pass -GatewayUrl.
param(
    [string]$GatewayUrl = $env:GATEWAY_URL
)

if (-not $GatewayUrl) {
    Write-Host "ERROR: GATEWAY_URL is not set. Copy aws.env.example to aws.env and fill it in, or pass -GatewayUrl." -ForegroundColor Red
    exit 1
}

Set-Content -Path .env.local -Value "VITE_GATEWAY_URL=$GatewayUrl"
Write-Host "Admin Console -> $GatewayUrl" -ForegroundColor Cyan
npm run dev
