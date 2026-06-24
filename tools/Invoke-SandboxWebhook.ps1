<#
.SYNOPSIS
Posts a safe Azure DevOps Work Item Updated test payload to the local sandbox webhook endpoint.

.DESCRIPTION
This helper is intended for local sandbox validation only. It does not read or require
ADO_PERSONAL_ACCESS_TOKEN, does not modify application config, and does not switch dry-run
on or off.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $ProjectName,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $Organization,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long] $WorkItemId,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [int]::MaxValue)]
    [int] $Revision,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $OldState,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $NewState,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $ChangedByEmail,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $ChangedByDisplayName,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $SharedSecret,

    [Parameter(Mandatory = $false)]
    [ValidateNotNullOrEmpty()]
    [string] $EndpointUrl = "http://localhost:8080/api/ado/webhooks/work-item-updated"
)

$ErrorActionPreference = "Stop"

$payload = [ordered]@{
    eventType    = "workitem.updated"
    organization = $Organization
    resource     = [ordered]@{
        id        = $WorkItemId
        rev       = $Revision
        revisedBy = [ordered]@{
            displayName = $ChangedByDisplayName
            uniqueName  = $ChangedByEmail
        }
        revision  = [ordered]@{
            rev    = $Revision
            fields = [ordered]@{
                "System.TeamProject"  = $ProjectName
                "System.WorkItemType" = "Test Case"
                "System.State"        = $NewState
                "System.ChangedBy"    = [ordered]@{
                    displayName = $ChangedByDisplayName
                    uniqueName  = $ChangedByEmail
                }
            }
        }
        fields    = [ordered]@{
            "System.State" = [ordered]@{
                oldValue = $OldState
                newValue = $NewState
            }
        }
    }
}

$headers = @{
    "X-ADO-Webhook-Secret" = $SharedSecret
}

$body = $payload | ConvertTo-Json -Depth 20

try {
    $response = Invoke-WebRequest `
        -Uri $EndpointUrl `
        -Method Post `
        -ContentType "application/json" `
        -Headers $headers `
        -Body $body

    $statusDescription = $response.StatusDescription
    if ([string]::IsNullOrWhiteSpace($statusDescription)) {
        $statusDescription = "OK"
    }

    Write-Host ("Webhook POST completed: HTTP {0} {1}" -f $response.StatusCode, $statusDescription)
    if (-not [string]::IsNullOrWhiteSpace($response.Content)) {
        Write-Host $response.Content
    }
} catch {
    $exception = $_.Exception
    $statusCode = $null
    $responseBody = $null

    if ($exception.Response -ne $null) {
        try {
            $statusCode = [int]$exception.Response.StatusCode
        } catch {
            $statusCode = $exception.Response.StatusCode
        }

        if (-not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
            $responseBody = $_.ErrorDetails.Message
        } else {
            try {
                $reader = [System.IO.StreamReader]::new($exception.Response.GetResponseStream())
                $responseBody = $reader.ReadToEnd()
                $reader.Dispose()
            } catch {
                $responseBody = $null
            }
        }
    }

    if ($statusCode -ne $null) {
        [Console]::Error.WriteLine(("Webhook POST failed: HTTP {0}" -f $statusCode))
    } else {
        [Console]::Error.WriteLine(("Webhook POST failed: {0}" -f $exception.Message))
    }

    if (-not [string]::IsNullOrWhiteSpace($responseBody)) {
        [Console]::Error.WriteLine($responseBody)
    }

    exit 1
}
