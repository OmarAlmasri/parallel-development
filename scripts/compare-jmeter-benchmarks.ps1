param(
    [Parameter(Mandatory = $true)]
    [string] $BaselineJtl,

    [Parameter(Mandatory = $true)]
    [string] $OptimizedJtl,

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

function Read-JMeterRows {
    param([string] $Path)

    if (-not (Test-Path $Path)) {
        throw "JTL file was not found: $Path"
    }

    $rows = @(Import-Csv $Path)

    if (-not $IncludeWarmup) {
        $rows = @($rows | Where-Object { $_.label -notlike "WARMUP*" -and $_.label -notlike "SETUP*" })
    }

    if ($rows.Count -eq 0) {
        throw "No JMeter samples were found after filtering: $Path"
    }

    return $rows
}

function Get-SummaryByLabel {
    param([array] $Rows)

    $result = @{}

    $Rows |
        Group-Object label |
        ForEach-Object {
            $elapsed = @($_.Group | ForEach-Object { [int] $_.elapsed })
            $stats = $elapsed | Measure-Object -Average
            $errorCount = @($_.Group | Where-Object { $_.success -ne "true" }).Count

            $result[$_.Name] = [PSCustomObject]@{
                Samples = $_.Count
                Errors = $errorCount
                ErrorRatePercent = [Math]::Round(($errorCount / [Math]::Max($_.Count, 1)) * 100, 2)
                AvgMs = [Math]::Round($stats.Average, 2)
                P95Ms = Get-Percentile $elapsed 95
                P99Ms = Get-Percentile $elapsed 99
            }
        }

    return $result
}

function Get-ImprovementPercent {
    param(
        [double] $Before,
        [double] $After
    )

    if ($Before -le 0) {
        return 0
    }

    return [Math]::Round((($Before - $After) / $Before) * 100, 2)
}

$baselineRows = Read-JMeterRows $BaselineJtl
$optimizedRows = Read-JMeterRows $OptimizedJtl

$baseline = Get-SummaryByLabel $baselineRows
$optimized = Get-SummaryByLabel $optimizedRows

$labels = @($baseline.Keys + $optimized.Keys | Sort-Object -Unique)
$comparison = foreach ($label in $labels) {
    $before = $baseline[$label]
    $after = $optimized[$label]

    if (-not $before -or -not $after) {
        continue
    }

    [PSCustomObject]@{
        Label = $label
        BaselineSamples = $before.Samples
        OptimizedSamples = $after.Samples
        BaselineErrors = $before.Errors
        OptimizedErrors = $after.Errors
        BaselineAvgMs = $before.AvgMs
        OptimizedAvgMs = $after.AvgMs
        AvgImprovementPercent = Get-ImprovementPercent $before.AvgMs $after.AvgMs
        BaselineP95Ms = $before.P95Ms
        OptimizedP95Ms = $after.P95Ms
        P95ImprovementPercent = Get-ImprovementPercent $before.P95Ms $after.P95Ms
        BaselineP99Ms = $before.P99Ms
        OptimizedP99Ms = $after.P99Ms
        P99ImprovementPercent = Get-ImprovementPercent $before.P99Ms $after.P99Ms
    }
}

$comparison | Sort-Object Label | Format-Table -AutoSize | Out-String -Width 260 | Write-Host

if ($OutputCsv) {
    $outputDirectory = Split-Path -Parent $OutputCsv
    if ($outputDirectory -and -not (Test-Path $outputDirectory)) {
        New-Item -ItemType Directory -Path $outputDirectory | Out-Null
    }

    $comparison | Export-Csv -Path $OutputCsv -NoTypeInformation
    Write-Host "Comparison written to $OutputCsv"
}
