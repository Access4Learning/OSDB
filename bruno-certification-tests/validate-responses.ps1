# validate-responses.ps1
# Validates Bruno test JSON results against SIF 3.7 OpenAPI schema expectations
# Checks: wrapper keys, field names, collection structures, error shapes

$ErrorActionPreference = "Continue"

# SIF 3.7 expected response schemas
$sifSchemas = @{
    # Singular object wrappers
    "environment" = @("id","type","fingerprint","sessionToken","solutionId","defaultZone","consumerName","applicationInfo","infrastructureServices","provisionedZones","authenticationMethod","instanceId","userToken")
    "queue" = @("id","polling","ownerId","name","queueUri","ownerUri","idleTimeout","minWaitTime","maxConcurrentConnections","created","lastAccessed","lastModified","messageCount")
    "subscription" = @("id","zoneId","contextId","serviceType","serviceSubType","messageType","serviceName","queueId")
    "zone" = @("id","description","properties")
    "alert" = @("id","reporter","cause","exchange","level","description","messageID","body","error","xpath","category","code","internal")
    "error" = @("id","code","subCode","message","scope","description","timestamp")
    "xquery" = @("id","type","status","description","queryStatement","serviceObjectPairs")
    "codeSet" = @("id","zone","timestamp","version","codeItems","description","source","url")
    "namespace" = @("id","url","prefix","description")
    "provider" = @("id","serviceName","serviceType","endpointUrl","contextServiceUuid")
}

# Collection wrappers: SIF uses {plural}.{singular}[] pattern
$sifCollections = @{
    "environments" = "environment"
    "queues" = "queue"
    "subscriptions" = "subscription"
    "zones" = "zone"
    "alerts" = "alert"
    "errors" = "error"
    "xquerys" = "xquery"
    "codeSets" = "codeSet"
    "namespaces" = "namespace"
    "providers" = "provider"
}

$issues = @()
$totalResponses = 0
$validatedResponses = 0

# Get all result files
$resultFiles = Get-ChildItem -Path "." -Name "results-*.json"

foreach ($file in $resultFiles) {
    $data = Get-Content $file -Raw | ConvertFrom-Json
    
    foreach ($iteration in $data) {
        foreach ($result in $iteration.results) {
            $totalResponses++
            $status = $result.response.status
            $responseData = $result.response.data
            $testName = $result.test.filename
            $url = $result.request.url
            
            # Skip if no response data (204s, etc.)
            if ($null -eq $responseData) { continue }
            
            # Check response is an object
            if ($responseData -is [System.Array]) {
                $issues += [PSCustomObject]@{
                    File = $file
                    Test = $testName
                    Status = $status
                    Issue = "Response is an array instead of object wrapper"
                    Detail = "SIF 3.7 responses should be wrapped in a named object"
                }
                continue
            }
            
            $validatedResponses++
            $topKeys = @()
            if ($responseData.PSObject -and $responseData.PSObject.Properties) {
                $topKeys = $responseData.PSObject.Properties.Name
            }
            
            # --- Check 1: Validate collection wrapper pattern ---
            foreach ($key in $topKeys) {
                if ($sifCollections.ContainsKey($key)) {
                    $expectedInnerKey = $sifCollections[$key]
                    $collectionObj = $responseData.$key
                    if ($null -ne $collectionObj -and $collectionObj.PSObject -and $collectionObj.PSObject.Properties) {
                        $innerKeys = $collectionObj.PSObject.Properties.Name
                        if ($expectedInnerKey -notin $innerKeys) {
                            $issues += [PSCustomObject]@{
                                File = $file
                                Test = $testName
                                Status = $status
                                Issue = "Collection wrapper '$key' missing inner array key '$expectedInnerKey'"
                                Detail = "Found keys: $($innerKeys -join ', ')"
                            }
                        }
                    }
                }
            }
            
            # --- Check 2: Validate singular object fields ---
            foreach ($key in $topKeys) {
                if ($sifSchemas.ContainsKey($key)) {
                    $obj = $responseData.$key
                    if ($null -ne $obj -and $obj.PSObject -and $obj.PSObject.Properties) {
                        $actualFields = $obj.PSObject.Properties.Name
                        # Check for fields NOT in the spec (potential misconfigurations)
                        $specFields = $sifSchemas[$key]
                        $unknownFields = $actualFields | Where-Object { $_ -notin $specFields }
                        if ($unknownFields.Count -gt 0) {
                            $issues += [PSCustomObject]@{
                                File = $file
                                Test = $testName
                                Status = $status
                                Issue = "Object '$key' has fields not in SIF 3.7 spec"
                                Detail = "Extra fields: $($unknownFields -join ', '). Spec fields: $($specFields -join ', ')"
                            }
                        }
                    }
                }
            }
            
            # --- Check 3: Error responses should have standard shape ---
            if ($status -ge 400 -and $status -lt 600) {
                if ("error" -in $topKeys) {
                    $errorObj = $responseData.error
                    if ($null -ne $errorObj -and $errorObj.PSObject -and $errorObj.PSObject.Properties) {
                        $errorFields = $errorObj.PSObject.Properties.Name
                        $requiredErrorFields = @("id","code","message")
                        $missingFields = $requiredErrorFields | Where-Object { $_ -notin $errorFields }
                        if ($missingFields.Count -gt 0) {
                            $issues += [PSCustomObject]@{
                                File = $file
                                Test = $testName
                                Status = $status
                                Issue = "Error response missing required fields"
                                Detail = "Missing: $($missingFields -join ', '). Has: $($errorFields -join ', ')"
                            }
                        }
                    }
                } elseif ("errors" -notin $topKeys) {
                    # Some error responses may not use the error wrapper (provider-forwarded responses)
                    # Only flag if it's a broker-generated error (not forwarded)
                    if ($url -notmatch "requests|services|events") {
                        $issues += [PSCustomObject]@{
                            File = $file
                            Test = $testName
                            Status = $status
                            Issue = "Error response ($status) not wrapped in 'error' or 'errors' key"
                            Detail = "Top-level keys: $($topKeys -join ', ')"
                        }
                    }
                }
            }
            
            # --- Check 4: Check collection items have expected fields ---
            foreach ($key in $topKeys) {
                if ($sifCollections.ContainsKey($key)) {
                    $innerKey = $sifCollections[$key]
                    $collectionObj = $responseData.$key
                    if ($null -ne $collectionObj -and $collectionObj.PSObject -and $collectionObj.PSObject.Properties) {
                        $items = $collectionObj.$innerKey
                        if ($items -is [System.Array] -and $items.Count -gt 0 -and $sifSchemas.ContainsKey($innerKey)) {
                            $specFields = $sifSchemas[$innerKey]
                            $firstItem = $items[0]
                            if ($firstItem.PSObject -and $firstItem.PSObject.Properties) {
                                $itemFields = $firstItem.PSObject.Properties.Name
                                $unknownFields = $itemFields | Where-Object { $_ -notin $specFields }
                                if ($unknownFields.Count -gt 0) {
                                    $issues += [PSCustomObject]@{
                                        File = $file
                                        Test = $testName
                                        Status = $status
                                        Issue = "Collection item '$innerKey' has fields not in SIF 3.7 spec"
                                        Detail = "Extra fields: $($unknownFields -join ', ')"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

# Output results
Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " SIF 3.7 OpenAPI Response Validation" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Total responses analyzed: $totalResponses"
Write-Host " Responses with body validated: $validatedResponses"
Write-Host " Issues found: $($issues.Count)"
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

if ($issues.Count -eq 0) {
    Write-Host "No misconfigurations detected." -ForegroundColor Green
} else {
    $grouped = $issues | Group-Object -Property Issue
    foreach ($group in $grouped) {
        Write-Host "ISSUE: $($group.Name)" -ForegroundColor Yellow
        Write-Host "  Occurrences: $($group.Count)" -ForegroundColor DarkGray
        foreach ($item in $group.Group | Select-Object -First 3) {
            Write-Host "  - [$($item.Status)] $($item.Test)" -ForegroundColor White
            Write-Host "    $($item.Detail)" -ForegroundColor DarkGray
        }
        if ($group.Count -gt 3) {
            Write-Host "  ... and $($group.Count - 3) more" -ForegroundColor DarkGray
        }
        Write-Host ""
    }
}
