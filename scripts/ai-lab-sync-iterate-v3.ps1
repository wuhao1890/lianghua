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
  try { return "'$([DateTime]::Parse([string]$Value).ToUniversalTime().ToString('yyyy-MM-dd HH:mm:ss'))'" } catch { return "NULL" }
}

function Convert-LabText([string]$Text) {
  if ([string]::IsNullOrWhiteSpace($Text)) { return $Text }
  $pairs = @(
    @("AI Trend Breakout", "智能趋势突破"),
    @("AI Pullback Buy", "智能回撤低吸"),
    @("News Sentiment Fusion", "新闻舆情融合"),
    @("Custom Strategy", "自定义策略"),
    @("Local scheduler repaired corrupted cloud state", "本地定时任务已修复云端异常状态"),
    @("Rebuilt object arrays from real public quote data before continuing iteration.", "已基于真实公开行情重建资产和策略数据，并继续迭代。"),
    @("Seeded by local scheduler from live public quote API.", "由本地定时任务基于实时公开报价纳入资产池。"),
    @("Live quote plus technical momentum, sentiment weight and risk control confirmation.", "结合实时报价、技术动量、舆情权重和风控确认。"),
    @("Exit when score drops below 50, drawdown expands or live quote momentum reverses.", "当综合分低于50、回撤扩大或实时动量反转时退出。"),
    @("Generated by local scheduler from real public market data.", "由本地定时任务基于真实公开市场数据生成。"),
    @("Initial local scheduled strategy seed.", "本地定时任务初始建池。"),
    @("Technical", "技术面"),
    @("Sentiment", "舆情面"),
    @("Risk", "风险控制"),
    @("Based on live quote change percent.", "根据实时涨跌幅判断。"),
    @("Scheduler sentiment weight for public news/sentiment integration.", "定时任务为新闻和舆情融合预留权重。"),
    @("Lower drawdown receives higher risk score.", "回撤越低，风险分越高。"),
    @("Local scheduler seeded strategy pool", "本地定时任务已建立策略池"),
    @("Local MySQL scheduled iteration: profit-first tuning, lower drawdown, keep high-score strategy variants.", "本地数据库定时迭代：以盈利为主，压低回撤，保留高分策略变体。"),
    @("local scheduled iteration", "本地定时迭代"),
    @("sina-public-stock", "新浪公开股票行情"),
    @("sina-public-metal", "新浪公开贵金属行情"),
    @("eastmoney-public-fund", "东方财富公开基金数据"),
    @("local-mysql-scheduler", "本地数据库定时任务")
  )
  foreach ($pair in $pairs) {
    $Text = $Text.Replace([string]$pair[0], [string]$pair[1])
  }
  return $Text
}

function Convert-LabObjectText($Value) {
  if ($null -eq $Value) { return $null }
  if ($Value -is [string]) { return Convert-LabText $Value }
  if ($Value -is [System.Array]) {
    $items = @()
    foreach ($item in @($Value)) { $items += Convert-LabObjectText $item }
    return $items
  }
  if ($Value -is [psobject]) {
    foreach ($prop in @($Value.PSObject.Properties)) {
      if ($prop.IsSettable) {
        $prop.Value = Convert-LabObjectText $prop.Value
      }
    }
  }
  return $Value
}

function Get-SignalText([string]$Signal) {
  if ($Signal -eq "BUY") { return "买入" }
  if ($Signal -eq "SELL") { return "卖出" }
  return "观望"
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
  $tradesOk = Test-ObjectArray (Get-Value $State "simulatedTrades" @())
  $champion = Get-Value $State "champion" $null
  if ($champion -is [string]) { $champion = $null }

  if (!$assetsOk -or !$experimentsOk -or !$logsOk -or !$customOk -or !$tradesOk) {
    Write-Log "正在修复异常实验室状态，代数=$generation"
    return Convert-LabObjectText ([pscustomobject]@{
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
      simulatedTrades = @()
    })
  }
  if ($null -eq $State.assets) { $State | Add-Member -NotePropertyName assets -NotePropertyValue @() -Force }
  if ($null -eq $State.experiments) { $State | Add-Member -NotePropertyName experiments -NotePropertyValue @() -Force }
  if ($null -eq $State.evolutionLog) { $State | Add-Member -NotePropertyName evolutionLog -NotePropertyValue @() -Force }
  if ($null -eq $State.customStrategies) { $State | Add-Member -NotePropertyName customStrategies -NotePropertyValue @() -Force }
  if ($null -eq $State.simulatedTrades) { $State | Add-Member -NotePropertyName simulatedTrades -NotePropertyValue @() -Force }
  if ($champion -ne (Get-Value $State "champion" $null)) { $State.champion = $champion }
  return Convert-LabObjectText $State
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
  if ($Score -ge 76 -and $ReturnPct -ge 3) { return "gold" }
  if ($Score -ge 62) { return "platinum" }
  return "bronze"
}

function Get-DowngradedRank([string]$Rank) {
  if ($Rank -eq "king") { return "gold" }
  if ($Rank -eq "gold") { return "platinum" }
  if ($Rank -eq "platinum") { return "bronze" }
  return "bronze"
}

function New-LabAsset([string]$Type, [string]$Code, [string]$Name, [double]$Price, [double]$ChangePct, [string]$Source) {
  $techScore = Get-MarketScore $ChangePct
  $sentimentScore = [int][math]::Max(35, [math]::Min(85, 52 + $ChangePct * 3))
  $aiScore = [int][math]::Round($techScore * 0.56 + $sentimentScore * 0.28 + 12)
  return [pscustomobject]@{
    id = "$Type-$Code"; type = $Type; code = $Code; name = $Name; price = $Price; changePct = $ChangePct
    aiScore = [int][math]::Max(0, [math]::Min(100, $aiScore)); sentimentScore = $sentimentScore; techScore = $techScore
    source = $Source; reason = "由本地定时任务基于实时公开报价纳入资产池。"
  }
}

function Get-SeedAssets {
  $assets = @()
  foreach ($code in @("600519", "300750", "000858", "600036", "300308", "600030")) {
    try {
      $quote = Get-ApiData "/api/stock/realtime/$code"
      if ($quote -and [double](Get-Value $quote "current" 0) -gt 0) {
        $assets += New-LabAsset "stock" $code ([string](Get-Value $quote "name" $code)) ([double](Get-Value $quote "current" 0)) ([double](Get-Value $quote "changePercent" 0)) "新浪公开股票行情"
      }
    } catch { Write-Log "seed stock $code skipped: $($_.Exception.Message)" }
  }
  foreach ($code in @("hf_GC", "hf_XAG")) {
    try {
      $quote = Get-ApiData "/api/stock/gold/latest?code=$code"
      if ($quote -and [double](Get-Value $quote "price" 0) -gt 0) {
        $assets += New-LabAsset "gold" $code ([string](Get-Value $quote "productName" $code)) ([double](Get-Value $quote "price" 0)) ([double](Get-Value $quote "changePercent" 0)) "新浪公开贵金属行情"
      }
    } catch { Write-Log "seed metal $code skipped: $($_.Exception.Message)" }
  }
  try {
    $fundPage = Get-ApiData "/api/stock/fund/list?page=1&pageSize=5"
    foreach ($fund in @($fundPage.list)) {
      if ($fund -and [double](Get-Value $fund "nav" 0) -gt 0) {
        $assets += New-LabAsset "fund" ([string](Get-Value $fund "code" "")) ([string](Get-Value $fund "name" (Get-Value $fund "code" ""))) ([double](Get-Value $fund "nav" 0)) ([double](Get-Value $fund "changePercent" 0)) "东方财富公开基金数据"
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
    entryRule = "结合实时报价、技术动量、舆情权重和风控确认。"
    exitRule = "当综合分低于50、回撤扩大或实时动量反转时退出。"
    reason = "由本地定时任务基于真实公开市场数据生成。"
    mutation = "本地定时任务初始建池。"; custom = $Custom
    factorScores = @(
      [pscustomobject]@{ name = "技术面"; score = [int](Get-Value $Asset "techScore" 50); reason = "根据实时涨跌幅判断。" },
      [pscustomobject]@{ name = "舆情面"; score = [int](Get-Value $Asset "sentimentScore" 50); reason = "定时任务为新闻和舆情融合预留权重。" },
      [pscustomobject]@{ name = "风险控制"; score = [int][math]::Max(0, [math]::Min(100, 90 - $drawdownPct * 8)); reason = "回撤越低，风险分越高。" }
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
    $created += New-Experiment $asset "智能趋势突破" "trend" 1 $false $generation
    $created += New-Experiment $asset "智能回撤低吸" "mean-reversion" 2 $false $generation
    $created += New-Experiment $asset "新闻舆情融合" "sentiment" 3 $false $generation
    foreach ($custom in @($State.customStrategies)) {
      $created += New-Experiment $asset ([string](Get-Value $custom "name" "自定义策略")) ([string](Get-Value $custom "style" "custom")) 4 $true $generation
    }
  }
  $State.experiments = @($created | Sort-Object -Property score, returnPct -Descending | Select-Object -First 40)
  $State.evolutionLog = @([pscustomobject]@{ id = "$(Get-Date -UFormat %s)-seed"; title = "本地定时任务已建立策略池"; detail = "已从 $($assets.Count) 个真实行情资产生成 $($State.experiments.Count) 个策略实验。" }) + @($State.evolutionLog) | Select-Object -First 14
  return $State
}

function Get-AssetPriceFor([object]$State, [object]$Experiment) {
  $assetId = [string](Get-Value $Experiment "assetId" "")
  $assetCode = [string](Get-Value $Experiment "assetCode" "")
  foreach ($asset in @($State.assets)) {
    if ([string](Get-Value $asset "id" "") -eq $assetId -or [string](Get-Value $asset "code" "") -eq $assetCode) {
      return [double](Get-Value $asset "price" 0)
    }
  }
  return 0
}

function Invoke-SimulatedTrades([object]$State, [object]$PreviousChampion) {
  $now = (Get-Date).ToUniversalTime().ToString("o")
  $capital = [double](Get-Value $State "capital" 100000)
  $generation = [int](Get-Value $State "generation" 0)
  $topFive = @($State.experiments | Sort-Object -Property score, returnPct -Descending | Select-Object -First 5)
  $topIds = @{}
  foreach ($item in $topFive) { $topIds[[string](Get-Value $item "id" "")] = $true }
  $trades = @($State.simulatedTrades)

  foreach ($trade in $trades) {
    if ([string](Get-Value $trade "status" "") -ne "持仓中") { continue }
    $experimentId = [string](Get-Value $trade "experimentId" "")
    $experiment = @($State.experiments | Where-Object { [string](Get-Value $_ "id" "") -eq $experimentId } | Select-Object -First 1)
    $shouldClose = $false
    if (!$experiment -or $experiment.Count -eq 0) {
      $shouldClose = $true
    } else {
      $item = $experiment[0]
      $previousId = [string](Get-Value $PreviousChampion "id" "")
      $previousReturn = [double](Get-Value $PreviousChampion "returnPct" 0)
      $shouldClose = ([string](Get-Value $item "signal" "") -eq "SELL") -or (!$topIds.ContainsKey($experimentId)) -or ($previousId -eq $experimentId -and [double](Get-Value $item "returnPct" 0) -lt $previousReturn)
    }
    if ($shouldClose -and $experiment -and $experiment.Count -gt 0) {
      $item = $experiment[0]
      $price = Get-AssetPriceFor $State $item
      if ($price -gt 0) {
        $trade | Add-Member -NotePropertyName status -NotePropertyValue "已卖出" -Force
        $trade | Add-Member -NotePropertyName action -NotePropertyValue "卖出" -Force
        $trade | Add-Member -NotePropertyName sellPrice -NotePropertyValue $price -Force
        $trade | Add-Member -NotePropertyName closedAt -NotePropertyValue $now -Force
        $trade | Add-Member -NotePropertyName profit -NotePropertyValue ([math]::Round(($price - [double](Get-Value $trade "buyPrice" 0)) * [double](Get-Value $trade "quantity" 0), 2)) -Force
      }
    }
  }

  foreach ($item in $topFive) {
    if ([string](Get-Value $item "signal" "") -ne "BUY") { continue }
    $position = [double](Get-Value $item "position" 0)
    if ($position -le 0) { continue }
    $experimentId = [string](Get-Value $item "id" "")
    $openExists = @($trades | Where-Object { [string](Get-Value $_ "experimentId" "") -eq $experimentId -and [string](Get-Value $_ "status" "") -eq "持仓中" })
    if ($openExists.Count -gt 0) { continue }
    $price = Get-AssetPriceFor $State $item
    if ($price -le 0) { continue }
    $amount = [math]::Round($capital * $position / 100, 2)
    $quantity = $amount / $price
    $plannedSellPrice = [math]::Round($price * (1 + [math]::Max([double](Get-Value $item "returnPct" 0), 1.2) / 100), 4)
    $trades = @([pscustomobject]@{
      id = "$(Get-Date -UFormat %s)-$experimentId"
      experimentId = $experimentId
      assetCode = Get-Value $item "assetCode" ""
      assetName = Get-Value $item "assetName" ""
      strategyName = Get-Value $item "strategyName" ""
      action = "买入"
      status = "持仓中"
      generation = $generation
      buyPrice = $price
      plannedSellPrice = $plannedSellPrice
      sellPrice = 0
      amount = $amount
      quantity = $quantity
      profit = 0
      createdAt = $now
    }) + $trades
  }

  $State.simulatedTrades = @($trades | Select-Object -First 40)
  return $State
}

function Iterate-State([object]$State) {
  $State = Ensure-ExperimentPool $State
  $previousChampion = Get-Value $State "champion" $null
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
    $oldReturn = $returnPct
    $item.returnPct = $nextReturn; $item.drawdownPct = $nextDrawdown; $item.score = [int](Score-Experiment $item); $item.generation = $generation
    $item.signal = if ($item.score -ge 65) { "BUY" } elseif ($item.score -le 42) { "SELL" } else { "HOLD" }
    $item.position = if ($item.signal -eq "BUY") { if ($item.score -ge 88) { 70 } else { 50 } } elseif ($item.signal -eq "SELL") { 0 } else { 20 }
    $rank = Get-Rank $item.score $item.returnPct $item.drawdownPct
    if ($nextReturn -lt $oldReturn) { $rank = Get-DowngradedRank $rank }
    $item.rank = $rank
    $item.mutation = "本地数据库定时迭代：以盈利为主，压低回撤，保留高分策略变体。"
    $nextExperiments += $item
  }
  $champion = $nextExperiments | Sort-Object -Property score, returnPct -Descending | Select-Object -First 1
  $logEntry = [pscustomobject]@{ id = "$(Get-Date -UFormat %s)-$generation"; title = "$($champion.strategyName) 本地定时迭代"; detail = "$($champion.assetName) 第 $generation 代收益 $($champion.returnPct)%，回撤 $($champion.drawdownPct)%，动作 $(Get-SignalText $champion.signal)。" }
  $State.experiments = $nextExperiments; $State.champion = $champion; $State.generation = $generation; $State.iterationCount = $generation
  $State.lastRunAt = (Get-Date).ToUniversalTime().ToString("o")
  $State.evolutionLog = @($logEntry) + @($State.evolutionLog) | Select-Object -First 14
  $State = Invoke-SimulatedTrades $State $previousChampion
  $record = [pscustomobject]@{ id = [int64]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()); generation = $generation; champion = $champion; experiments = $nextExperiments; capital = [decimal](Get-Value $State "capital" 100000); intervalMinutes = [int](Get-Value $State "intervalMinutes" 5); createdAt = (Get-Date).ToUniversalTime().ToString("o") }
  return [pscustomobject]@{ state = $State; record = $record }
}

try {
  Write-Log "同步开始"
  Ensure-MysqlSchema
  $headers = @{ "X-Sync-Token" = $SyncToken }
  $cloudExport = Invoke-JsonGetUtf8 -Uri "$CloudBaseUrl/api/sync/ai-lab/export" -Headers $headers
  $cloudStates = @($cloudExport.data.states)
  foreach ($item in $cloudStates) {
    $cloudUserId = [long](Get-Value $item "userId" 0)
    if ($cloudUserId -le 0) { continue }
    Save-StateToMysql -UserId $cloudUserId -State $item.state
  }
  Write-Log "已拉取云端状态：$($cloudStates.Count)"
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
  $payload = ConvertTo-JsonText ([pscustomobject]@{ states = $importStates; iterations = $importIterations; processedUsers = @($cloudExport.data.dirtyUsers); source = "本地数据库定时任务"; syncedAt = (Get-Date).ToUniversalTime().ToString("o") })
  Invoke-JsonPostUtf8 -Uri "$CloudBaseUrl/api/sync/ai-lab/import" -Headers $headers -Body $payload | Out-Null
  Write-Log "同步完成：本地状态=$($localStates.Count)，新增迭代=$($importIterations.Count)"
} catch {
  Write-Log "同步失败：$($_.Exception.Message)"
  throw
}
