# Start admin console pointing at local broker (localhost:8080)
Set-Content -Path .env.local -Value "VITE_GATEWAY_URL=http://localhost:8080"
Write-Host "Admin Console → LOCAL (http://localhost:8080)" -ForegroundColor Green
npm run dev
