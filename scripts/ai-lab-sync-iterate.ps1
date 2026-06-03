param(
  [string]$CloudBaseUrl = "https://lianghua-quant-trading.pages.dev",
  [string]$SyncToken = $env:LIANGHUA_SYNC_TOKEN,
  [string]$MysqlExe = "D:\mysql-8.0.15-winx64\bin\mysql.exe",
  [string]$MysqlHost = "127.0.0.1",
  [int]$MysqlPort = 3306,
  [string]$MysqlUser = "root",
  [string]$MysqlPassword = $env:LIANGHUA_MYSQL_PASSWORD,
  [string]$MysqlDatabase = "stock_trading"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

if ([string]::IsNullOrWhiteSpace($SyncToken)) { throw "LIANGHUA_SYNC_TOKEN is required" }
if ([string]::IsNullOrWhiteSpace($MysqlPassword)) { throw "LIANGHUA_MYSQL_PASSWORD is required" }

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$logDir = Join-Path $repoRoot "logs"
if (!(Test-Path $logDir)) {
  New-Item -ItemType Directory -Path $logDir | Out-Null
}
$logPath = Join-Path $logDir "ai-lab-sync.log"

function Write-Log([string]$Message) {
  $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
  Add-Content -Path $logPath -Value $line -Encoding UTF8
}

function Invoke-MysqlText([string]$Sql) {
  $args = @(
    "--host=$MysqlHost",
    "--port=$MysqlPort",
    "--user=$MysqlUser",
    "--password=$MysqlPassword",
    "--database=$MysqlDatabase",
    "--default-character-set=utf8mb4",
    "--batch",
    "--raw",
    "--skip-column-names",
    "--execute=$Sql"
  )
  & $MysqlExe @args
  if ($LASTEXITCODE -ne 0) {
    throw "mysql exited with code $LASTEXITCODE"
  }
}

function Escape-Sql([string]$Value) {
  if ($null -eq $Value) { return "" }
  return $Value.Replace("\", "\\").Replace("'", "''")
}

function Get-Value($Object, [string]$Property, $Fallback) {
  if ($null -eq $Object) { return $Fallback }
  $prop = $Object.PSObject.Properties[$Property]
  if ($null -eq $prop -or $null -eq $prop.Value -or $prop.Value -eq "") { return $Fallback }
  return $prop.Value
}

function Save-StateToMysql([long]$UserId, [object]$State) {
  $json = $State | ConvertTo-Json -Depth 100 -Compress
  $generation = [int](Get-Value $State "generation" 0)
  $iterationCount = [int](Get-Value $State "iterationCount" $generation)
  $capital = [decimal](Get-Value $State "capital" 100000)
  $intervalMinutes = [int](Get-Value $State "intervalMinutes" 5)
  $lastRun = if ($State.lastRunAt) { "'$(Escape-Sql ([string]$State.lastRunAt))'" } else { "NULL" }
  $escapedJson = Escape-Sql $json
  $sql = @"
INSERT INTO ai_lab_state (user_id, generation, iteration_count, capital, interval_minutes, state_json, last_run_at)
VALUES ($UserId, $generation, $iterationCount, $capital, $intervalMinutes, '$escapedJson', $lastRun)
ON DUPLICATE KEY UPDATE
  generation = VALUES(generation),
  iteration_count = VALUES(iteration_count),
  capital = VALUES(capital),
  interval_minutes = VALUES(interval_minutes),
  state_json = VALUES(state_json),
  last_run_at = VALUES(last_run_at),
  updated_at = CURRENT_TIMESTAMP;
"@
  Invoke-MysqlText $sql | Out-Null
}

function Save-IterationToMysql([long]$UserId, [object]$State, [object]$Record) {
  $championJsonValue = if ($null -ne (Get-Value $Record "champion" $null)) { Get-Value $Record "champion" $null | ConvertTo-Json -Depth 100 -Compress } else { "null" }
  $experimentsJsonValue = if ($null -ne (Get-Value $Record "experiments" $null)) { Get-Value $Record "experiments" $null | ConvertTo-Json -Depth 100 -Compress } else { "[]" }
  $championJson = Escape-Sql $championJsonValue
  $experimentsJson = Escape-Sql $experimentsJsonValue
  $generation = [int](Get-Value $Record "generation" (Get-Value $State "generation" 0))
  $capital = [decimal](Get-Value $Record "capital" (Get-Value $State "capital" 100000))
  $intervalMinutes = [int](Get-Value $Record "intervalMinutes" (Get-Value $State "intervalMinutes" 5))
  $sql = @"
INSERT INTO ai_lab_iteration (user_id, generation, champion_json, experiments_json, capital, interval_minutes)
VALUES ($UserId, $generation, '$championJson', '$experimentsJson', $capital, $intervalMinutes);
"@
  Invoke-MysqlText $sql | Out-Null
}

function Ensure-MysqlSchema {
  $schema = @"
CREATE TABLE IF NOT EXISTS ai_lab_state (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL UNIQUE,
  generation INT NOT NULL DEFAULT 0,
  iteration_count INT NOT NULL DEFAULT 0,
  capital DECIMAL(18,2) NOT NULL DEFAULT 100000,
  interval_minutes INT NOT NULL DEFAULT 5,
  state_json LONGTEXT NOT NULL,
  last_run_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_lab_iteration (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  generation INT NOT NULL DEFAULT 0,
  champion_json LONGTEXT NULL,
  experiments_json LONGTEXT NULL,
  capital DECIMAL(18,2) NOT NULL DEFAULT 100000,
  interval_minutes INT NOT NULL DEFAULT 5,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ai_lab_iteration_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"@
  Invoke-MysqlText $schema | Out-Null
}

function Get-LocalStates {
  $rows = Invoke-MysqlText "SELECT user_id, state_json FROM ai_lab_state;"
  $states = @()
  foreach ($row in $rows) {
    if ([string]::IsNullOrWhiteSpace($row)) { continue }
    $parts = $row -split "`t", 2
    if ($parts.Count -lt 2) { continue }
    try {
      $states += [pscustomobject]@{
        userId = [long]$parts[0]
        state = $parts[1] | ConvertFrom-Json
      }
    } catch {
      Write-Log "skip invalid local state for user $($parts[0]): $($_.Exception.Message)"
    }
  }
  return $states
}

function Score-Experiment([object]$Experiment) {
  $score = [double](Get-Value $Experiment "score" 50)
  $returnPct = [double](Get-Value $Experiment "returnPct" 0)
  $drawdownPct = [double](Get-Value $Experiment "drawdownPct" 0)
  return [math]::Max(0, [math]::Min(100, [math]::Round($score * 0.58 + $returnPct * 2.2 - $drawdownPct * 1.3 + 18)))
}

function Iterate-State([object]$State) {
  $generation = [int](Get-Value $State "generation" 0) + 1
  $experiments = @($State.experiments)
  if (!$experiments -or $experiments.Count -eq 0) {
    $State.generation = $generation
    $State.iterationCount = $generation
    $State.lastRunAt = (Get-Date).ToUniversalTime().ToString("o")
    return [pscustomobject]@{ state = $State; record = $null }
  }

  $nextExperiments = @()
  for ($i = 0; $i -lt $experiments.Count; $i++) {
    $item = $experiments[$i]
    $returnPct = [double](Get-Value $item "returnPct" 0)
    $drawdownPct = [double](Get-Value $item "drawdownPct" 0)
    $change = (($generation + $i) % 5 - 2) * 0.18
    $learningBoost = if ($returnPct -ge 0) { 0.72 } else { -0.42 }
    $nextReturn = [math]::Round($returnPct + $learningBoost - $drawdownPct * 0.05 + $change, 2)
    $nextDrawdown = [math]::Round([math]::Max(0.2, $drawdownPct * $(if ($nextReturn -ge 0) { 0.94 } else { 1.08 })), 2)
    $item.returnPct = $nextReturn
    $item.drawdownPct = $nextDrawdown
    $item.score = [int](Score-Experiment $item)
    $item.generation = $generation
    $item.signal = if ($item.score -ge 65) { "BUY" } elseif ($item.score -le 42) { "SELL" } else { "HOLD" }
    $item.position = if ($item.signal -eq "BUY") { if ($item.score -ge 88) { 70 } else { 50 } } elseif ($item.signal -eq "SELL") { 0 } else { 20 }
    $item.mutation = "本地MySQL定时迭代：以盈利优先，压低回撤，保留高分策略"
    $nextExperiments += $item
  }

  $champion = $nextExperiments | Sort-Object -Property score, returnPct -Descending | Select-Object -First 1
  $log = @($State.evolutionLog)
  $logEntry = [pscustomobject]@{
    id = "$(Get-Date -UFormat %s)-$generation"
    title = "$($champion.strategyName) 本地定时迭代"
    detail = "$($champion.assetName) 第 $generation 代收益 $($champion.returnPct)%，回撤 $($champion.drawdownPct)%，动作 $($champion.signal)。"
  }
  $State.experiments = $nextExperiments
  $State.champion = $champion
  $State.generation = $generation
  $State.iterationCount = $generation
  $State.lastRunAt = (Get-Date).ToUniversalTime().ToString("o")
  $State.evolutionLog = @($logEntry) + $log | Select-Object -First 14

  $record = [pscustomobject]@{
    id = [int64]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
    generation = $generation
    champion = $champion
    experiments = $nextExperiments
    capital = [decimal](Get-Value $State "capital" 100000)
    intervalMinutes = [int](Get-Value $State "intervalMinutes" 5)
    createdAt = (Get-Date).ToUniversalTime().ToString("o")
  }
  return [pscustomobject]@{ state = $State; record = $record }
}

try {
  Write-Log "sync started"
  Ensure-MysqlSchema

  $headers = @{ "X-Sync-Token" = $SyncToken }
  $cloudExport = Invoke-RestMethod -Uri "$CloudBaseUrl/api/sync/ai-lab/export" -Headers $headers -Method Get -TimeoutSec 60
  $cloudStates = @($cloudExport.data.states)
  foreach ($item in $cloudStates) {
    Save-StateToMysql -UserId ([long]$item.userId) -State $item.state
  }
  Write-Log "cloud states pulled: $($cloudStates.Count)"

  $localStates = @(Get-LocalStates)
  $importStates = @()
  $importIterations = @()
  foreach ($item in $localStates) {
    $result = Iterate-State $item.state
    Save-StateToMysql -UserId ([long]$item.userId) -State $result.state
    if ($result.record) {
      Save-IterationToMysql -UserId ([long]$item.userId) -State $result.state -Record $result.record
      $importIterations += [pscustomobject]@{ userId = [long]$item.userId; record = $result.record }
    }
    $importStates += [pscustomobject]@{ userId = [long]$item.userId; state = $result.state }
  }

  $payload = [pscustomobject]@{
    states = $importStates
    iterations = $importIterations
    processedUsers = @($cloudExport.data.dirtyUsers)
    source = "local-mysql-scheduler"
    syncedAt = (Get-Date).ToUniversalTime().ToString("o")
  } | ConvertTo-Json -Depth 100 -Compress
  Invoke-RestMethod -Uri "$CloudBaseUrl/api/sync/ai-lab/import" -Headers $headers -Method Post -ContentType "application/json" -Body $payload -TimeoutSec 60 | Out-Null
  Write-Log "sync finished: localStates=$($localStates.Count), iterations=$($importIterations.Count)"
} catch {
  Write-Log "sync failed: $($_.Exception.Message)"
  throw
}
