# Add missing SolutionRepository imports

Write-Host "Adding missing SolutionRepository imports..." -ForegroundColor Cyan

$files = Get-ChildItem -Path "environment-service/src/test/java/org/sifunity/broker/environment/property" -Filter "*.java"

$filesFixed = 0

foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw
    
    # Check if file uses SolutionRepository but doesn't import it
    if ($content -match "SolutionRepository" -and $content -notmatch "import org\.sifunity\.broker\.environment\.repository\.SolutionRepository;") {
        
        # Find the last import line
        $lines = Get-Content $file.FullName
        $lastImportLine = -1
        
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -match "^import ") {
                $lastImportLine = $i
            }
        }
        
        if ($lastImportLine -ge 0) {
            # Insert the new import after the last import
            $newLines = @()
            for ($i = 0; $i -le $lastImportLine; $i++) {
                $newLines += $lines[$i]
            }
            $newLines += "import org.sifunity.broker.environment.repository.SolutionRepository;"
            for ($i = $lastImportLine + 1; $i -lt $lines.Count; $i++) {
                $newLines += $lines[$i]
            }
            
            $newLines | Set-Content $file.FullName
            $filesFixed++
            Write-Host "Added import to $($file.Name)" -ForegroundColor Green
        }
    }
}

Write-Host ""
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "  Files fixed: $filesFixed" -ForegroundColor White
