param(
    [Parameter(Mandatory = $true)]
    [string] $JtlPath,

    [string] $OutputCsv,

    [switch] $IncludeWarmup
)

$ErrorActionPreference = "Stop"

function Get-Percentile {
    param(
        [int[]] $Values,
        [double] $Percentile
    )

    if ($Values.Count -eq 0) {
        return 0
    }

    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling(($Percentile / 100) * $sorted.Count) - 1
    $index = [Math]::Max(0, [Math]::Min($index, $sorted.Count - 1))
    return [int] $sorted[$index]
}

function New-JMeterSummary {
    param(
        [string] $Label,
        [array] $Rows
    )

    $elapsed = @($Rows | ForEach-Object { [int] $_.elapsed })
    $stats = $elapsed | Measure-Object -Average -Minimum -Maximum
    $errorCount = @($Rows | Where-Object { $_.success -ne "true" }).Count
    $timestamps = @($Rows | ForEach-Object { [int64] $_.timeStamp })
    $durationSeconds = 0

    if ($timestamps.Count -gt 1) {
        $durationSeconds = (($timestamps | Measure-Object -Maximum).Maximum - ($timestamps | Measure-Object -Minimum).Minimum) / 1000
    }

    $throughput = 0
    if ($durationSeconds -gt 0) {
        $throughput = $Rows.Count / $durationSeconds
    }

    [PSCustomObject]@{
        Label = $Label
        Samples = $Rows.Count
        Errors = $errorCount
        ErrorRatePercent = [Math]::Round(($errorCount / [Math]::Max($Rows.Count, 1)) * 100, 2)
        MinMs = [int] $stats.Minimum
        AvgMs = [Math]::Round($stats.Average, 2)
        P50Ms = Get-Percentile $elapsed 50
        P90Ms = Get-Percentile $elapsed 90
        P95Ms = Get-Percentile $elapsed 95
        P99Ms = Get-Percentile $elapsed 99
        MaxMs = [int] $stats.Maximum
        ThroughputPerSec = [Math]::Round($throughput, 2)
    }
}

if (-not (Test-Path $JtlPath)) {
    throw "JTL file was not found: $JtlPath"
}

$rows = @(Import-Csv $JtlPath)

if (-not $IncludeWarmup) {
    $rows = @($rows | Where-Object { $_.label -notlike "WARMUP*" -and $_.label -notlike "SETUP*" })
}

if ($rows.Count -eq 0) {
    throw "No JMeter samples were found after filtering."
}

$summaries = @()
$summaries += New-JMeterSummary "ALL" $rows

$rows |
    Group-Object label |
    Sort-Object Name |
    ForEach-Object {
        $summaries += New-JMeterSummary $_.Name @($_.Group)
    }

$summaries | Format-Table -AutoSize | Out-String -Width 240 | Write-Host

if ($OutputCsv) {
    $outputDirectory = Split-Path -Parent $OutputCsv
    if ($outputDirectory -and -not (Test-Path $outputDirectory)) {
        New-Item -ItemType Directory -Path $outputDirectory | Out-Null
    }

    $summaries | Export-Csv -Path $OutputCsv -NoTypeInformation
    Write-Host "Summary written to $OutputCsv"
}
