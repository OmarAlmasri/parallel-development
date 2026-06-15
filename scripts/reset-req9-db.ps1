param(
    [string] $ComposeFile = "compose.yaml",
    [switch] $Force,
    [switch] $SkipRedisFlush,
    [switch] $SkipRabbitPurge
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..")
$SqlFile = Join-Path $ScriptDir "req9-seed.sql"

Set-Location $RepoRoot

if (-not [System.IO.Path]::IsPathRooted($ComposeFile)) {
    $ComposeFile = Join-Path $RepoRoot $ComposeFile
}

function Assert-LastExitCode {
    param([string] $Action)

    if ($LASTEXITCODE -ne 0) {
        throw "$Action failed with exit code $LASTEXITCODE."
    }
}

if (-not (Test-Path $ComposeFile)) {
    throw "Compose file was not found: $ComposeFile"
}

if (-not (Test-Path $SqlFile)) {
    throw "Seed SQL file was not found: $SqlFile"
}

if (-not $Force) {
    Write-Host "This will DELETE Requirement 9 test data from the Postgres database and reseed it."
    Write-Host "It will also flush Redis and purge RabbitMQ application queues when those services exist."
    Write-Host "Rerun with -Force to execute:"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts/reset-req9-db.ps1 -Force"
    exit 1
}

$composeServices = docker compose -f $ComposeFile config --services
Assert-LastExitCode "Reading Docker Compose services"

$hasRabbitMq = $composeServices -contains "rabbitmq"
$dependencyServices = @("postgres", "redis")
if ($hasRabbitMq -and -not $SkipRabbitPurge) {
    $dependencyServices += "rabbitmq"
}

Write-Host "Starting Requirement 9 dependencies: $($dependencyServices -join ', ')..."
docker compose -f $ComposeFile up -d $dependencyServices
Assert-LastExitCode "Starting Docker Compose dependencies"

Write-Host "Waiting for Postgres to accept connections..."
$ready = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    docker compose -f $ComposeFile exec -T postgres pg_isready -U ecommerce_user -d ecommerce | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $ready = $true
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $ready) {
    throw "Postgres did not become ready after 60 seconds."
}

Write-Host "Resetting and seeding Postgres for Requirement 9..."
Get-Content -Raw $SqlFile | docker compose -f $ComposeFile exec -T postgres psql -U ecommerce_user -d ecommerce -v ON_ERROR_STOP=1
Assert-LastExitCode "Applying Requirement 9 seed SQL"

if (-not $SkipRedisFlush) {
    Write-Host "Flushing Redis cache so stale products/categories/orders do not survive the DB reset..."
    docker compose -f $ComposeFile exec -T redis redis-cli FLUSHALL
    Assert-LastExitCode "Flushing Redis"
}

if ($hasRabbitMq -and -not $SkipRabbitPurge) {
    Write-Host "Purging RabbitMQ application queues so stale async messages do not affect fresh test results..."
    $queues = docker compose -f $ComposeFile exec -T rabbitmq rabbitmqctl list_queues name --silent
    Assert-LastExitCode "Reading RabbitMQ queues"

    foreach ($queue in @("order.placed.queue", "report.requested.queue", "deposit.completed.queue")) {
        if ($queues -contains $queue) {
            docker compose -f $ComposeFile exec -T rabbitmq rabbitmqctl purge_queue $queue
            Assert-LastExitCode "Purging RabbitMQ queue $queue"
        } else {
            Write-Host "RabbitMQ queue not present, skipping: $queue"
        }
    }
}

Write-Host ""
Write-Host "Requirement 9 database seed completed."
Write-Host "Seeded credentials:"
Write-Host "  admin@example.com / admin123"
Write-Host "  shopper1@example.com ... shopper100@example.com / shopper123"
Write-Host ""
Write-Host "Seeded JMeter IDs:"
Write-Host "  categoryId = 1"
Write-Host "  productId  = JMeter thread number, 1 through 100"
Write-Host "  product initial stock = 100 each; total product stock = 10000"
