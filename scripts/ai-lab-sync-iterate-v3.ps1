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
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

if ([string]::IsNullOrWhiteSpace($SyncToken)) { throw "LIANGHUA_SYNC_TOKEN is required" }
if ([string]::IsNullOrWhiteSpace($MysqlPassword)) { throw "LIANGHUA_MYSQL_PASSWORD is required" }

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$logDir = Join-Path $repoRoot "logs"
if (!(Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
$logPath = Join-Path $logDir "ai-lab-sync.log"

function Write-Log([string]$Message) {
  Add-Content -Path $logPath -Value ("[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message) -Encoding UTF8
}

function Invoke-MysqlText([string]$Sql) {
  $tempSqlPath = $null
  $executeArg = "--execute=$Sql"
  if ($Sql.Length -gt 7000) {
    $tempSqlPath = Join-Path $logDir ("mysql-ai-lab-{0}.sql" -f ([Guid]::NewGuid().ToString("N")))
    [System.IO.File]::WriteAllText($tempSqlPath, $Sql, [System.Text.Encoding]::UTF8)
    $executeArg = "--execute=source $($tempSqlPath.Replace('\', '/'))"
  }
  $args = @(
    "--host=$MysqlHost", "--port=$MysqlPort", "--user=$MysqlUser", "--password=$MysqlPassword",
    "--database=$MysqlDatabase", "--default-character-set=utf8mb4", "--batch", "--raw", "--skip-column-names", $executeArg
  )
  try {
    & $MysqlExe @args
    if ($LASTEXITCODE -ne 0) { throw "mysql exited with code $LASTEXITCODE" }
  } finally {
    if ($tempSqlPath -and (Test-Path -LiteralPath $tempSqlPath)) { Remove-Item -LiteralPath $tempSqlPath }
  }
}

function Invoke-JsonGetUtf8([string]$Uri, [hashtable]$Headers) {
  Add-Type -AssemblyName System.Net.Http
  $client = New-Object System.Net.Http.HttpClient
  foreach ($key in $Headers.Keys) { $client.DefaultRequestHeaders.Add($key, [string]$Headers[$key]) }
  $response = $client.GetAsync($Uri).Result
  $text = [System.Text.Encoding]::UTF8.GetString($response.Content.ReadAsByteArrayAsync().Result)
  if (!$response.IsSuccessStatusCode) { throw "HTTP GET failed: $([int]$response.StatusCode) $text" }
  return $text | ConvertFrom-Json
}

function Invoke-JsonPostUtf8([string]$Uri, [hashtable]$Headers, [string]$Body) {
  Add-Type -AssemblyName System.Net.Http
  $client = New-Object System.Net.Http.HttpClient
  foreach ($key in $Headers.Keys) { $client.DefaultRequestHeaders.Add($key, [string]$Headers[$key]) }
  $content = New-Object System.Net.Http.StringContent($Body, [System.Text.Encoding]::UTF8, "application/json")
  $response = $client.PostAsync($Uri, $content).Result
  $text = [System.Text.Encoding]::UTF8.GetString($response.Content.ReadAsByteArrayAsync().Result)
  if (!$response.IsSuccessStatusCode) { throw "HTTP POST failed: $([int]$response.StatusCode) $text" }
  return $text | ConvertFrom-Json
}

function Get-Value($Object, [string]$Property, $Fallback) {
  if ($null -eq $Object) { return $Fallback }
  $prop = $Object.PSObject.Properties[$Property]
  if ($null -eq $prop -or $null -eq $prop.Value -or $prop.Value -eq "") { return $Fallback }
  return $prop.Value
}

function ConvertTo-JsonText($Value) { return ($Value | ConvertTo-Json -Depth 100 -Compress) }

function ConvertFrom-Base64Utf8([string]$Value) {
  if ([string]::IsNullOrWhiteSpace($Value)) { return "" }
  return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($Value))
}

function ConvertTo-SqlUtf8HexLiteral([string]$Value) {
  $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
  $hex = -join ($bytes | ForEach-Object { $_.ToString("x2") })
  return "CONVERT(0x$hex USING utf8mb4)"
}

function Format-MysqlDate($Value) {
  if (!$Value) { return "NULL" }
  try { return "'$([DateTime]::Parse([string]$Value).ToUniversalTime().ToString("yyyy-MM-dd HH:mm:ss"))'" } catch { return "NULL" }
}

function Ensure-MysqlSchema {
  Invoke-MysqlText @"
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
"@ | Out-Null
}

function Save-StateToMysql([long]$UserId, [object]$State) {
  $State = Normalize-LabState $State
  $jsonSql = ConvertTo-SqlUtf8HexLiteral (ConvertTo-JsonText $State)
  $generation = [int](Get-Value $State "generation" 0)
  $iterationCount = [int](Get-Value $State "iterationCount" $generation)
  $capital = [decimal](Get-Value $State "capital" 100000)
  $intervalMinutes = [int](Get-Value $State "intervalMinutes" 5)
  $lastRun = Format-MysqlDate (Get-Value $State "lastRunAt" $null)
  Invoke-MysqlText @"
INSERT INTO ai_lab_state (user_id, generation, iteration_count, capital, interval_minutes, state_json, last_run_at)
VALUES ($UserId, $generation, $iterationCount, $capital, $intervalMinutes, $jsonSql, $lastRun)
ON DUPLICATE KEY UPDATE
  generation = VALUES(generation),
  iteration_count = VALUES(iteration_count),
  capital = VALUES(capital),
  interval_minutes = VALUES(interval_minutes),
  state_json = VALUES(state_json),
  last_run_at = VALUES(last_run_at),
  updated_at = CURRENT_TIMESTAMP;
"@ | Out-Null
}

function Save-IterationToMysql([long]$UserId, [object]$State, [object]$Record) {
  $championJson = ConvertTo-SqlUtf8HexLiteral (ConvertTo-JsonText (Get-Value $Record "champion" $null))
  $experimentsJson = ConvertTo-SqlUtf8HexLiteral (ConvertTo-JsonText (Get-Value $Record "experiments" @()))
  $generation = [int](Get-Value $Record "generation" (Get-Value $State "generation" 0))
  $capital = [decimal](Get-Value $Record "capital" (Get-Value $State "capital" 100000))
  $intervalMinutes = [int](Get-Value $Record "intervalMinutes" (Get-Value $State "intervalMinutes" 5))
  Invoke-MysqlText "INSERT INTO ai_lab_iteration (user_id, generation, champion_json, experiments_json, capital, interval_minutes) VALUES ($UserId, $generation, $championJson, $experimentsJson, $capital, $intervalMinutes);" | Out-Null
}

function Get-LocalStates {
  $rows = Invoke-MysqlText "SELECT user_id, REPLACE(REPLACE(TO_BASE64(state_json), CHAR(10), ''), CHAR(13), '') FROM ai_lab_state WHERE user_id > 0;"
  $states = @()
  foreach ($row in $rows) {
    if ([string]::IsNullOrWhiteSpace($row)) { continue }
    $parts = $row -split "`t", 2
    if ($parts.Count -lt 2) { continue }
    try {
      $userId = [long]$parts[0]
      if ($userId -le 0) { continue }
      $json = ConvertFrom-Base64Utf8 $parts[1]
      $states += [pscustomobject]@{ userId = $userId; state = Normalize-LabState ($json | ConvertFrom-Json) }
    }
    catch { Write-Log "skip invalid local state for user $($parts[0]): $($_.Exception.Message)" }
  }
  return $states
}

function Test-ObjectArray($Value) {
  if ($null -eq $Value) { return $true }
  if (!($Value -is [System.Array])) { return $false }
  foreach ($item in @($Value)) {
    if ($null -eq $item) { continue }
    if ($item -is [string]) { return $false }
  }
  return $true
}

function Normalize-LabState([object]$State) {
  if ($null -eq $State) {
    $State = [pscustomobject]@{}
  }
  $generation = [int](Get-Value $State "generation" 0)
  $iterationCount = [int](Get-Value $State "iterationCount" $generation)
  $capital = [decimal](Get-Value $State "capital" 100000)
  $intervalMinutes = [int](Get-Value $State "intervalMinutes" 5)
  $assetsOk = Test-ObjectArray (Get-Value $State "assets" @())
  $experimentsOk = Test-ObjectArray (Get-Value $State "experiments" @())
  $logsOk = Test-ObjectArray (Get-Value $State "evolutionLog" @())
  $customOk = Test-ObjectArray (Get-Value $State "customStrategies" @())
  $champion = Get-Value $State "champion" $null
  if ($champion -is [string]) { $champion = $null }

  if (!$assetsOk -or !$experimentsOk -or !$logsOk -or !$customOk) {
    Write-Log "normalizing corrupted lab state at generation=$generation"
    return [pscustomobject]@{
      generation = $generation
      iterationCount = $iterationCount
      capital = $capital
      intervalMinutes = $intervalMinutes
      assets = @(Get-SeedAssets)
      experiments = @()
      evolutionLog = @([pscustomobject]@{
        id = "$(Get-Date -UFormat %s)-repair"
        title = "Local scheduler repaired corrupted cloud state"
        detail = "Rebuilt object arrays from real public quote data before continuing iteration."
      })
      champion = $null
      lastRunAt = Get-Value $State "lastRunAt" $null
      updatedAt = Get-Value $State "updatedAt" $null
      customStrategies = @()
    }
  }
  if ($null -eq $State.assets) { $State | Add-Member -NotePropertyName assets -NotePropertyValue @() -Force }
  if ($null -eq $State.experiments) { $State | Add-Member -NotePropertyName experiments -NotePropertyValue @() -Force }
  if ($null -eq $State.evolutionLog) { $State | Add-Member -NotePropertyName evolutionLog -NotePropertyValue @() -Force }
  if ($null -eq $State.customStrategies) { $State | Add-Member -NotePropertyName customStrategies -NotePropertyValue @() -Force }
  if ($champion -ne (Get-Value $State "champion" $null)) { $State.champion = $champion }
  return $State
}

function Score-Experiment([object]$Experiment) {
  $score = [double](Get-Value $Experiment "score" 50)
  $returnPct = [double](Get-Value $Experiment "returnPct" 0)
  $drawdownPct = [double](Get-Value $Experiment "drawdownPct" 0)
  return [math]::Max(0, [math]::Min(100, [math]::Round($score * 0.58 + $returnPct * 2.2 - $drawdownPct * 1.3 + 18)))
}

function Get-ApiData([string]$Path) {
  return (Invoke-JsonGetUtf8 -Uri "$CloudBaseUrl$Path" -Headers @{ "X-User-Id" = "1" }).data
}

function Get-MarketScore([double]$ChangePct) { return [int][math]::Max(0, [math]::Min(100, [math]::Round(55 + $ChangePct * 5))) }

function Get-Rank([int]$Score, [double]$ReturnPct, [double]$DrawdownPct) {
  if ($Score -ge 88 -and $ReturnPct -ge 6 -and $DrawdownPct -le 4) { return "king" }
  if ($Score -ge 76 -and $ReturnPct -ge 3) { return "platinum" }
  if ($Score -ge 62) { return "gold" }
  return "bronze"
}

function New-LabAsset([string]$Type, [string]$Code, [string]$Name, [double]$Price, [double]$ChangePct, [string]$Source) {
  $techScore = Get-MarketScore $ChangePct
  $sentimentScore = [int][math]::Max(35, [math]::Min(85, 52 + $ChangePct * 3))
  $aiScore = [int][math]::Round($techScore * 0.56 + $sentimentScore * 0.28 + 12)
  return [pscustomobject]@{
    id = "$Type-$Code"; type = $Type; code = $Code; name = $Name; price = $Price; changePct = $ChangePct
    aiScore = [int][math]::Max(0, [math]::Min(100, $aiScore)); sentimentScore = $sentimentScore; techScore = $techScore
    source = $Source; reason = "Seeded by local scheduler from live public quote API."
  }
}

function Get-SeedAssets {
  $assets = @()
  foreach ($code in @("600519", "300750", "000858", "600036", "300308", "600030")) {
    try {
      $quote = Get-ApiData "/api/stock/realtime/$code"
      if ($quote -and [double](Get-Value $quote "current" 0) -gt 0) {
        $assets += New-LabAsset "stock" $code ([string](Get-Value $quote "name" $code)) ([double](Get-Value $quote "current" 0)) ([double](Get-Value $quote "changePercent" 0)) "sina-public-stock"
      }
    } catch { Write-Log "seed stock $code skipped: $($_.Exception.Message)" }
  }
  foreach ($code in @("hf_GC", "hf_XAG")) {
    try {
      $quote = Get-ApiData "/api/stock/gold/latest?code=$code"
      if ($quote -and [double](Get-Value $quote "price" 0) -gt 0) {
        $assets += New-LabAsset "gold" $code ([string](Get-Value $quote "productName" $code)) ([double](Get-Value $quote "price" 0)) ([double](Get-Value $quote "changePercent" 0)) "sina-public-metal"
      }
    } catch { Write-Log "seed metal $code skipped: $($_.Exception.Message)" }
  }
  try {
    $fundPage = Get-ApiData "/api/stock/fund/list?page=1&pageSize=5"
    foreach ($fund in @($fundPage.list)) {
      if ($fund -and [double](Get-Value $fund "nav" 0) -gt 0) {
        $assets += New-LabAsset "fund" ([string](Get-Value $fund "code" "")) ([string](Get-Value $fund "name" (Get-Value $fund "code" ""))) ([double](Get-Value $fund "nav" 0)) ([double](Get-Value $fund "changePercent" 0)) "eastmoney-public-fund"
      }
    }
  } catch { Write-Log "seed fund list skipped: $($_.Exception.Message)" }
  return @($assets | Sort-Object -Property aiScore -Descending | Select-Object -First 10)
}

function New-Experiment([object]$Asset, [string]$StrategyName, [string]$Style, [int]$Index, [bool]$Custom, [int]$Generation) {
  $baseScore = [int](Get-Value $Asset "aiScore" 55)
  if ($Style -eq "mean-reversion") { $baseScore = [int][math]::Max(0, [math]::Min(100, $baseScore - 3)) }
  if ($Style -eq "sentiment") { $baseScore = [int][math]::Max(0, [math]::Min(100, $baseScore + 4)) }
  if ($Custom) { $baseScore = [int][math]::Max(0, [math]::Min(100, $baseScore + 2)) }
  $changePct = [double](Get-Value $Asset "changePct" 0)
  $returnPct = [math]::Round($changePct * 0.35 + ($baseScore - 50) * 0.09 + ($Index % 3) * 0.22, 2)
  $drawdownPct = [math]::Round([math]::Max(0.3, 6.8 - $baseScore * 0.045 + [math]::Abs($changePct) * 0.28), 2)
  $score = [int](Score-Experiment ([pscustomobject]@{ score = $baseScore; returnPct = $returnPct; drawdownPct = $drawdownPct }))
  $signal = if ($score -ge 65) { "BUY" } elseif ($score -le 42) { "SELL" } else { "HOLD" }
  return [pscustomobject]@{
    id = "local-$Style-$($Asset.id)-$Index"; assetId = $Asset.id; assetType = $Asset.type; assetCode = $Asset.code; assetName = $Asset.name
    strategyName = $StrategyName; style = $Style; signal = $signal; score = $score; returnPct = $returnPct; drawdownPct = $drawdownPct
    winRate = [int][math]::Max(0, [math]::Min(100, [math]::Round(50 + $returnPct * 3 - $drawdownPct)))
    position = if ($signal -eq "BUY") { 50 } elseif ($signal -eq "SELL") { 0 } else { 20 }
    rank = Get-Rank $score $returnPct $drawdownPct; generation = $Generation
    entryRule = "Live quote plus technical momentum, sentiment weight and risk control confirmation."
    exitRule = "Exit when score drops below 50, drawdown expands or live quote momentum reverses."
    reason = "Generated by local scheduler from real public market data."
    mutation = "Initial local scheduled strategy seed."; custom = $Custom
    factorScores = @(
      [pscustomobject]@{ name = "Technical"; score = [int](Get-Value $Asset "techScore" 50); reason = "Based on live quote change percent." },
      [pscustomobject]@{ name = "Sentiment"; score = [int](Get-Value $Asset "sentimentScore" 50); reason = "Scheduler sentiment weight for public news/sentiment integration." },
      [pscustomobject]@{ name = "Risk"; score = [int][math]::Max(0, [math]::Min(100, 90 - $drawdownPct * 8)); reason = "Lower drawdown receives higher risk score." }
    )
  }
}

function Ensure-ExperimentPool([object]$State) {
  $experiments = @($State.experiments)
  if ($experiments -and $experiments.Count -gt 0) { return $State }
  $assets = @($State.assets)
  if (!$assets -or $assets.Count -eq 0) { $assets = @(Get-SeedAssets); $State.assets = $assets }
  if (!$assets -or $assets.Count -eq 0) { return $State }
  $generation = [int](Get-Value $State "generation" 0)
  $created = @()
  foreach ($asset in $assets) {
    $created += New-Experiment $asset "AI Trend Breakout" "trend" 1 $false $generation
    $created += New-Experiment $asset "AI Pullback Buy" "mean-reversion" 2 $false $generation
    $created += New-Experiment $asset "News Sentiment Fusion" "sentiment" 3 $false $generation
    foreach ($custom in @($State.customStrategies)) {
      $created += New-Experiment $asset ([string](Get-Value $custom "name" "Custom Strategy")) ([string](Get-Value $custom "style" "custom")) 4 $true $generation
    }
  }
  $State.experiments = @($created | Sort-Object -Property score, returnPct -Descending | Select-Object -First 40)
  $State.evolutionLog = @([pscustomobject]@{ id = "$(Get-Date -UFormat %s)-seed"; title = "Local scheduler seeded strategy pool"; detail = "Created $($State.experiments.Count) experiments from $($assets.Count) real quote assets." }) + @($State.evolutionLog) | Select-Object -First 14
  return $State
}

function Iterate-State([object]$State) {
  $State = Ensure-ExperimentPool $State
  $generation = [int](Get-Value $State "generation" 0) + 1
  $experiments = @($State.experiments)
  if (!$experiments -or $experiments.Count -eq 0) {
    $State.generation = $generation; $State.iterationCount = $generation; $State.lastRunAt = (Get-Date).ToUniversalTime().ToString("o")
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
    $item.returnPct = $nextReturn; $item.drawdownPct = $nextDrawdown; $item.score = [int](Score-Experiment $item); $item.generation = $generation
    $item.signal = if ($item.score -ge 65) { "BUY" } elseif ($item.score -le 42) { "SELL" } else { "HOLD" }
    $item.position = if ($item.signal -eq "BUY") { if ($item.score -ge 88) { 70 } else { 50 } } elseif ($item.signal -eq "SELL") { 0 } else { 20 }
    $item.mutation = "Local MySQL scheduled iteration: profit-first tuning, lower drawdown, keep high-score strategy variants."
    $nextExperiments += $item
  }
  $champion = $nextExperiments | Sort-Object -Property score, returnPct -Descending | Select-Object -First 1
  $logEntry = [pscustomobject]@{ id = "$(Get-Date -UFormat %s)-$generation"; title = "$($champion.strategyName) local scheduled iteration"; detail = "$($champion.assetName) generation $generation return $($champion.returnPct)%, drawdown $($champion.drawdownPct)%, signal $($champion.signal)." }
  $State.experiments = $nextExperiments; $State.champion = $champion; $State.generation = $generation; $State.iterationCount = $generation
  $State.lastRunAt = (Get-Date).ToUniversalTime().ToString("o")
  $State.evolutionLog = @($logEntry) + @($State.evolutionLog) | Select-Object -First 14
  $record = [pscustomobject]@{ id = [int64]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()); generation = $generation; champion = $champion; experiments = $nextExperiments; capital = [decimal](Get-Value $State "capital" 100000); intervalMinutes = [int](Get-Value $State "intervalMinutes" 5); createdAt = (Get-Date).ToUniversalTime().ToString("o") }
  return [pscustomobject]@{ state = $State; record = $record }
}

try {
  Write-Log "sync started"
  Ensure-MysqlSchema
  $headers = @{ "X-Sync-Token" = $SyncToken }
  $cloudExport = Invoke-JsonGetUtf8 -Uri "$CloudBaseUrl/api/sync/ai-lab/export" -Headers $headers
  $cloudStates = @($cloudExport.data.states)
  foreach ($item in $cloudStates) {
    $cloudUserId = [long](Get-Value $item "userId" 0)
    if ($cloudUserId -le 0) { continue }
    Save-StateToMysql -UserId $cloudUserId -State $item.state
  }
  Write-Log "cloud states pulled: $($cloudStates.Count)"
  $localStates = @(Get-LocalStates)
  $importStates = @(); $importIterations = @()
  foreach ($item in $localStates) {
    $result = Iterate-State $item.state
    Save-StateToMysql -UserId ([long]$item.userId) -State $result.state
    if ($result.record) {
      Save-IterationToMysql -UserId ([long]$item.userId) -State $result.state -Record $result.record
      $importIterations += [pscustomobject]@{ userId = [long]$item.userId; record = $result.record }
    }
    $importStates += [pscustomobject]@{ userId = [long]$item.userId; state = $result.state }
  }
  $payload = ConvertTo-JsonText ([pscustomobject]@{ states = $importStates; iterations = $importIterations; processedUsers = @($cloudExport.data.dirtyUsers); source = "local-mysql-scheduler"; syncedAt = (Get-Date).ToUniversalTime().ToString("o") })
  Invoke-JsonPostUtf8 -Uri "$CloudBaseUrl/api/sync/ai-lab/import" -Headers $headers -Body $payload | Out-Null
  Write-Log "sync finished: localStates=$($localStates.Count), iterations=$($importIterations.Count)"
} catch {
  Write-Log "sync failed: $($_.Exception.Message)"
  throw
}
