param(
  [string]$CloudBaseUrl = "https://lianghua-quant-trading.pages.dev",
  [string]$SyncToken = $env:LIANGHUA_SYNC_TOKEN,
  [string]$MysqlExe = "D:\mysql-8.0.15-winx64\bin\mysql.exe",
  [string]$MysqlHost = "127.0.0.1",
  [int]$MysqlPort = 3306,
  [string]$MysqlUser = "root",
  [string]$MysqlPassword = $env:LIANGHUA_MYSQL_PASSWORD,
  [string]$MysqlDatabase = "stock_trading",
  [string]$SmtpHost = "smtp.qq.com",
  [int]$SmtpPort = 587,
  [string]$SmtpUser = $env:LIANGHUA_SMTP_USER,
  [string]$SmtpAuthCode = $env:LIANGHUA_SMTP_AUTH_CODE
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

function Get-RankText([string]$Rank) {
  if ($Rank -eq "king") { return "王者" }
  if ($Rank -eq "gold") { return "黄金" }
  if ($Rank -eq "platinum") { return "铂金" }
  if ($Rank -eq "silver") { return "白银" }
  return "青铜"
}

function Format-PercentText($Value) {
  return ("{0:N2}%" -f [double](Get-Value $Value "value" $Value))
}

function Get-AlertSettings([object]$State) {
  $settings = Get-Value $State "alertSettings" $null
  if ($null -eq $settings) { return [pscustomobject]@{ email = ""; emailEnabled = $false; includeTopFive = $true } }
  return [pscustomobject]@{
    email = [string](Get-Value $settings "email" "")
    emailEnabled = [bool](Get-Value $settings "emailEnabled" $false)
    includeTopFive = [bool](Get-Value $settings "includeTopFive" $true)
  }
}

function Format-TopFiveText([object]$State) {
  $lines = @()
  $topFive = @($State.experiments | Sort-Object -Property score, returnPct -Descending | Select-Object -First 5)
  for ($i = 0; $i -lt $topFive.Count; $i++) {
    $item = $topFive[$i]
    $lines += ("{0}. {1}｜{2}｜{3}｜动作：{4}｜仓位：{5}%｜收益：{6:N2}%｜回撤：{7:N2}%｜综合分：{8}" -f
      ($i + 1),
      (Get-Value $item "assetName" ""),
      (Get-Value $item "strategyName" ""),
      (Get-RankText ([string](Get-Value $item "rank" ""))),
      (Get-SignalText ([string](Get-Value $item "signal" ""))),
      [double](Get-Value $item "position" 0),
      [double](Get-Value $item "returnPct" 0),
      [double](Get-Value $item "drawdownPct" 0),
      [int](Get-Value $item "score" 0)
    )
  }
  return ($lines -join "`r`n")
}

function Send-LabAlertEmail([string]$To, [string]$Subject, [string]$Body) {
  if ([string]::IsNullOrWhiteSpace($To)) { return $false }
  if ([string]::IsNullOrWhiteSpace($SmtpUser) -or [string]::IsNullOrWhiteSpace($SmtpAuthCode)) {
    Write-Log "邮箱告警跳过：未配置发件邮箱环境变量"
    return $false
  }
  try {
    $message = New-Object System.Net.Mail.MailMessage
    $message.From = New-Object System.Net.Mail.MailAddress($SmtpUser, "量化实验室")
    $message.To.Add($To)
    $message.Subject = $Subject
    $message.Body = $Body
    $message.BodyEncoding = [System.Text.Encoding]::UTF8
    $message.SubjectEncoding = [System.Text.Encoding]::UTF8
    $client = New-Object System.Net.Mail.SmtpClient($SmtpHost, $SmtpPort)
    $client.EnableSsl = $true
    $client.Credentials = New-Object System.Net.NetworkCredential($SmtpUser, $SmtpAuthCode)
    $client.Send($message)
    Write-Log "邮箱告警已发送：$To"
    return $true
  } catch {
    Write-Log "邮箱告警失败：$($_.Exception.Message)"
    return $false
  }
}

function Send-KingTradeAlerts([object]$State, [array]$Alerts) {
  if (!$Alerts -or $Alerts.Count -eq 0) { return }
  $settings = Get-AlertSettings $State
  if (!$settings.emailEnabled -or [string]::IsNullOrWhiteSpace($settings.email)) { return }
  $topFiveText = if ($settings.includeTopFive) { Format-TopFiveText $State } else { "未开启前五名状态附带。" }
  $ledger = Get-Value $State "tradeAlertLedger" @{}
  if ($null -eq $ledger -or $ledger -is [array]) { $ledger = @{} }
  foreach ($alert in $Alerts) {
    $actionText = Get-Value $alert "action" ""
    $alertKey = [string](Get-Value $alert "key" "")
    if (![string]::IsNullOrWhiteSpace($alertKey) -and $ledger.PSObject.Properties.Name -contains $alertKey) { continue }
    $body = @"
智能实验室王者策略触发交易动作

触发动作：$actionText
标的：$(Get-Value $alert "assetName" "")
策略：$(Get-Value $alert "strategyName" "")
段位：王者
价格：$(Get-Value $alert "price" 0)
仓位：$(Get-Value $alert "position" 0)%
模拟金额：$(Get-Value $alert "amount" 0)
触发原因：$(Get-Value $alert "reason" "策略达到买卖条件")
周期状态：$(Get-Value $alert "holdingPeriod" "按策略周期继续复盘")
本代：第 $(Get-Value $State "generation" 0) 代
时间：$((Get-Date).ToString("yyyy-MM-dd HH:mm:ss"))

前五名状态：
$topFiveText

说明：邮件只提醒买入或卖出时间点；持仓期间系统只统计收益和复盘经验，不重复发送买入提醒。本提醒不构成投资建议。
"@
    if (Send-LabAlertEmail -To $settings.email -Subject "量化实验室王者策略$actionText提醒" -Body $body) {
      if (![string]::IsNullOrWhiteSpace($alertKey)) {
        $ledger | Add-Member -NotePropertyName $alertKey -NotePropertyValue (Get-Date).ToUniversalTime().ToString("o") -Force
      }
    }
  }
  $State | Add-Member -NotePropertyName tradeAlertLedger -NotePropertyValue $ledger -Force
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
      alertSettings = Get-Value $State "alertSettings" $null
    })
  }
  if ($null -eq $State.assets) { $State | Add-Member -NotePropertyName assets -NotePropertyValue @() -Force }
  if ($null -eq $State.experiments) { $State | Add-Member -NotePropertyName experiments -NotePropertyValue @() -Force }
  if ($null -eq $State.evolutionLog) { $State | Add-Member -NotePropertyName evolutionLog -NotePropertyValue @() -Force }
  if ($null -eq $State.customStrategies) { $State | Add-Member -NotePropertyName customStrategies -NotePropertyValue @() -Force }
  if ($null -eq $State.simulatedTrades) { $State | Add-Member -NotePropertyName simulatedTrades -NotePropertyValue @() -Force }
  if ($null -eq $State.alertSettings) { $State | Add-Member -NotePropertyName alertSettings -NotePropertyValue $null -Force }
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
  foreach ($code in @("600519", "600036", "601318", "600276", "000858", "002594", "300033", "300059", "300274", "300308", "300750", "300760", "688008", "688036", "688111", "688223", "688599", "688981")) {
    try {
      $quote = Get-ApiData "/api/stock/realtime/$code"
      $currentPrice = [double](Get-Value $quote "currentPrice" (Get-Value $quote "current" 0))
      if ($quote -and $currentPrice -gt 0) {
        $assets += New-LabAsset "stock" $code ([string](Get-Value $quote "name" $code)) $currentPrice ([double](Get-Value $quote "changePercent" 0)) "新浪公开股票行情"
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
  return @($assets | Sort-Object -Property aiScore -Descending | Select-Object -First 24)
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
  $assets = @($State.assets)
  $freshAssets = @(Get-SeedAssets)
  if ($freshAssets -and $freshAssets.Count -gt 0) {
    $assetMap = @{}
    foreach ($asset in $assets) {
      $id = [string](Get-Value $asset "id" "")
      if ($id) { $assetMap[$id] = $asset }
    }
    foreach ($asset in $freshAssets) {
      $id = [string](Get-Value $asset "id" "")
      if ($id) { $assetMap[$id] = $asset }
    }
    $assets = @($assetMap.Values | Sort-Object -Property aiScore -Descending | Select-Object -First 24)
    $State.assets = $assets
  }
  if (!$assets -or $assets.Count -eq 0) { return $State }
  $generation = [int](Get-Value $State "generation" 0)
  $created = @()
  $experiments = @($State.experiments)
  $existingAssetIds = @{}
  foreach ($experiment in $experiments) {
    $assetId = [string](Get-Value $experiment "assetId" "")
    if ($assetId) { $existingAssetIds[$assetId] = $true }
  }
  foreach ($asset in $assets) {
    $assetId = [string](Get-Value $asset "id" "")
    if ($existingAssetIds.ContainsKey($assetId)) { continue }
    $created += New-Experiment $asset "智能趋势突破" "trend" 1 $false $generation
    $created += New-Experiment $asset "智能回撤低吸" "mean-reversion" 2 $false $generation
    $created += New-Experiment $asset "新闻舆情融合" "sentiment" 3 $false $generation
    foreach ($custom in @($State.customStrategies)) {
      $created += New-Experiment $asset ([string](Get-Value $custom "name" "自定义策略")) ([string](Get-Value $custom "style" "custom")) 4 $true $generation
    }
  }
  if ($created.Count -gt 0) {
    $State.experiments = @(@($experiments) + @($created) | Sort-Object -Property score, returnPct -Descending | Select-Object -First 72)
    $State.evolutionLog = @([pscustomobject]@{ id = "$(Get-Date -UFormat %s)-seed"; title = "本地定时任务已补充策略池"; detail = "已合并 $($assets.Count) 个真实行情资产，新增 $($created.Count) 个策略实验。" }) + @($State.evolutionLog) | Select-Object -First 14
  } else {
    $State.experiments = @($experiments | Sort-Object -Property score, returnPct -Descending | Select-Object -First 72)
  }
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

function Get-StrategyHoldingPeriod([object]$Experiment) {
  $style = [string](Get-Value $Experiment "style" "")
  $name = [string](Get-Value $Experiment "strategyName" "")
  $entryRule = [string](Get-Value $Experiment "entryRule" "")
  $exitRule = [string](Get-Value $Experiment "exitRule" "")
  $rule = "$name $entryRule $exitRule"
  if ($style -eq "sentiment" -or $rule -match "新闻|舆情|公告|事件") {
    return [pscustomobject]@{ min = 1; target = 4; max = 8; label = "事件周期，1到4代跟踪催化，最多8代" }
  }
  if ($style -eq "mean-reversion" -or $rule -match "低吸|反弹|短线") {
    return [pscustomobject]@{ min = 2; target = 6; max = 10; label = "短周期，2到6代重点观察，最多10代" }
  }
  if ($style -eq "custom") {
    return [pscustomobject]@{ min = 2; target = 8; max = 14; label = "自定义周期，2到8代验证，最多14代" }
  }
  return [pscustomobject]@{ min = 3; target = 10; max = 18; label = "趋势周期，3到10代持有验证，最多18代" }
}

function Add-TradeExperience([object]$Experiment, [object]$Trade, [string]$CloseReason) {
  $profit = [double](Get-Value $Trade "profit" 0)
  $floatingPct = [double](Get-Value $Trade "floatingProfitPct" 0)
  $holdingGenerations = [int](Get-Value $Trade "holdingGenerations" 0)
  $resultText = if ($profit -gt 0) { "盈利" } elseif ($profit -lt 0) { "亏损" } else { "持平" }
  $summary = "$resultText 经验：持有 $holdingGenerations 代，收益率 $floatingPct%，卖出原因：$CloseReason。"
  $existing = @((Get-Value $Experiment "tradeLessons" @()))
  $lessons = @($summary) + $existing | Select-Object -First 5
  $Experiment | Add-Member -NotePropertyName tradeLessons -NotePropertyValue $lessons -Force
  $Experiment | Add-Member -NotePropertyName lastTradeResult -NotePropertyValue $summary -Force
  if ($profit -gt 0) {
    $Experiment.winRate = [int]([math]::Min(100, [double](Get-Value $Experiment "winRate" 50) + 1))
    $Experiment.score = [int]([math]::Min(100, [double](Get-Value $Experiment "score" 50) + 1))
    $Experiment.mutation = "吸收盈利经验：保留本次入场条件，下一代继续验证周期节奏。"
  } elseif ($profit -lt 0) {
    $Experiment.winRate = [int]([math]::Max(0, [double](Get-Value $Experiment "winRate" 50) - 1))
    $Experiment.score = [int]([math]::Max(0, [double](Get-Value $Experiment "score" 50) - 2))
    $Experiment.mutation = "吸收亏损经验：降低仓位或延后买入确认，下一代收紧卖出风控。"
  }
  return $Experiment
}

function Get-ExperimentBucketName([object]$Experiment) {
  $assetType = [string](Get-Value $Experiment "assetType" "")
  $style = [string](Get-Value $Experiment "style" "")
  $drawdownPct = [double](Get-Value $Experiment "drawdownPct" 0)
  if ($assetType -eq "gold") { return "避险产品" }
  if ($assetType -eq "fund" -or ($drawdownPct -le 3.5 -and $style -ne "trend")) { return "稳健产品" }
  return "激进产品"
}

function Get-PortfolioBuckets() {
  return @(
    [pscustomobject]@{ name = "稳健产品"; ratio = 30; maxPositions = 2; description = "优先基金和低回撤策略，用来稳定组合净值。" },
    [pscustomobject]@{ name = "激进产品"; ratio = 50; maxPositions = 2; description = "优先股票和高分高收益策略，用来争取超额收益。" },
    [pscustomobject]@{ name = "避险产品"; ratio = 20; maxPositions = 1; description = "优先黄金、白银等金属，用来对冲波动。" }
  )
}

function Build-PortfolioPlan([object]$State) {
  $capital = [double](Get-Value $State "capital" 100000)
  $experiments = @($State.experiments | Sort-Object -Property score, returnPct -Descending)
  $buckets = @()
  foreach ($bucket in Get-PortfolioBuckets) {
    $candidatePool = @($experiments | Where-Object {
      (Get-ExperimentBucketName $_) -eq $bucket.name
    })
    $seenAssetCodes = @{}
    $candidates = @()
    foreach ($candidate in $candidatePool) {
      $assetCode = [string](Get-Value $candidate "assetCode" "")
      if ([string]::IsNullOrWhiteSpace($assetCode) -or $seenAssetCodes.ContainsKey($assetCode)) { continue }
      $seenAssetCodes[$assetCode] = $true
      $candidates += $candidate
      if ($candidates.Count -ge [int]$bucket.maxPositions) { break }
    }
    $targetAmount = [math]::Round($capital * [double]$bucket.ratio / 100, 2)
    $perAmount = if ($candidates.Count -gt 0) { [math]::Round($targetAmount / $candidates.Count, 2) } else { 0 }
    $items = @()
    foreach ($candidate in $candidates) {
      $items += [pscustomobject]@{
        experimentId = Get-Value $candidate "id" ""
        assetCode = Get-Value $candidate "assetCode" ""
        assetName = Get-Value $candidate "assetName" ""
        strategyName = Get-Value $candidate "strategyName" ""
        score = Get-Value $candidate "score" 0
        rank = Get-Value $candidate "rank" ""
        signal = Get-Value $candidate "signal" "HOLD"
        targetAmount = $perAmount
        decision = if ([string](Get-Value $candidate "signal" "") -eq "BUY") { "按 $($bucket.name) 预算纳入买入组合" } else { "未触发买入，先保留为本组观察候选" }
      }
    }
    $buckets += [pscustomobject]@{
      name = $bucket.name
      ratio = $bucket.ratio
      targetAmount = $targetAmount
      maxPositions = $bucket.maxPositions
      description = $bucket.description
      items = @($items)
    }
  }
  return [pscustomobject]@{
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    capital = $capital
    buckets = @($buckets)
    summary = "组合先按稳健、激进、避险三块分配资金，再在每块内部挑选冠军和候选；新冠军先比较老持仓收益、回撤和周期，再决定替换、部分保留或继续观察。"
  }
}

function Write-LabMemory([object]$State) {
  $memoryDir = Join-Path (Get-Location) "memory"
  if (!(Test-Path $memoryDir)) { New-Item -ItemType Directory -Path $memoryDir | Out-Null }
  $path = Join-Path $memoryDir "ai-lab-memory.md"
  $champion = Get-Value $State "champion" $null
  $portfolio = Get-Value $State "portfolioPlan" $null
  $openTrades = @((Get-Value $State "simulatedTrades" @()) | Where-Object { [string](Get-Value $_ "status" "") -eq "持仓中" })
  $lessons = @()
  foreach ($experiment in @((Get-Value $State "experiments" @()) | Sort-Object -Property score, returnPct -Descending | Select-Object -First 12)) {
    foreach ($lesson in @((Get-Value $experiment "tradeLessons" @()))) {
      if ($lesson) { $lessons += " - $([string]$lesson)" }
    }
  }
  if ($lessons.Count -eq 0) { $lessons = @(" - 暂无完整卖出复盘，继续等待持仓周期完成。") }
  $bucketLines = @()
  foreach ($bucket in @((Get-Value $portfolio "buckets" @()))) {
    $bucketLines += "### $($bucket.name) $($bucket.ratio)%"
    $bucketLines += "- 目标金额：$($bucket.targetAmount)"
    $bucketLines += "- 说明：$($bucket.description)"
    foreach ($item in @($bucket.items)) {
      $bucketLines += "  - $($item.assetName) / $($item.strategyName)：目标 $($item.targetAmount)，分数 $($item.score)，$($item.decision)"
    }
  }
  $tradeLines = @()
  foreach ($trade in $openTrades) {
    $tradeBucketName = [string](Get-Value $trade "bucketName" "未分组")
    $tradeAssetName = [string](Get-Value $trade "assetName" "")
    $tradeAmount = [double](Get-Value $trade "amount" 0)
    $tradeHoldingGenerations = [int](Get-Value $trade "holdingGenerations" 0)
    $tradeFloatingProfit = [double](Get-Value $trade "floatingProfit" 0)
    $tradeLines += "- ${tradeBucketName}：${tradeAssetName}，投入 ${tradeAmount}，已持有 ${tradeHoldingGenerations} 代，浮盈 ${tradeFloatingProfit}"
  }
  if ($tradeLines.Count -eq 0) { $tradeLines = @("- 当前没有持仓，等待组合计划触发买入。") }
  $content = @(
    "# AI实验室长期记忆",
    "",
    "更新时间：$((Get-Date).ToString("yyyy-MM-dd HH:mm:ss"))",
    "",
    "## 当前冠军",
    "- 标的：$([string](Get-Value $champion "assetName" "暂无"))",
    "- 策略：$([string](Get-Value $champion "strategyName" "暂无"))",
    "- 分数：$([string](Get-Value $champion "score" "暂无"))",
    "",
    "## 组合方案",
    $bucketLines,
    "",
    "## 当前持仓",
    $tradeLines,
    "",
    "## 交易经验",
    $lessons,
    "",
    "## 下一轮学习要求",
    "- 每个标的建立自己的历史性格：波动、新闻敏感点、技术形态、资金偏好、适合周期。",
    "- 新冠军出现时先判断是否替换同组老持仓、部分调仓，还是共存观察。",
    "- 所有买入必须受总资金和分组比例约束，不能重复开仓。"
  ) | ForEach-Object {
    if ($_ -is [array]) { $_ } else { $_ }
  }
  [System.IO.File]::WriteAllLines($path, [string[]]$content, [System.Text.Encoding]::UTF8)
}

function Repair-OverAllocatedTrades([array]$Trades, [double]$Capital, [int]$Generation, [string]$Now) {
  $openTrades = @($Trades | Where-Object { [string](Get-Value $_ "status" "") -eq "持仓中" })
  $usedCapital = 0.0
  foreach ($trade in $openTrades) { $usedCapital += [double](Get-Value $trade "amount" 0) }
  if ($usedCapital -le $Capital) { return $Trades }

  $keptCapital = 0.0
  $orderedOpenIds = @{}
  foreach ($trade in @($openTrades | Sort-Object -Property createdAt)) {
    $tradeId = [string](Get-Value $trade "id" "")
    $amount = [double](Get-Value $trade "amount" 0)
    if ($keptCapital + $amount -le $Capital) {
      $keptCapital += $amount
      $orderedOpenIds[$tradeId] = $true
    }
  }

  foreach ($trade in $Trades) {
    if ([string](Get-Value $trade "status" "") -ne "持仓中") { continue }
    $tradeId = [string](Get-Value $trade "id" "")
    if ($orderedOpenIds.ContainsKey($tradeId)) { continue }
    $trade | Add-Member -NotePropertyName status -NotePropertyValue "资金校准撤销" -Force
    $trade | Add-Member -NotePropertyName action -NotePropertyValue "撤销" -Force
    $trade | Add-Member -NotePropertyName closedAt -NotePropertyValue $Now -Force
    $trade | Add-Member -NotePropertyName closedGeneration -NotePropertyValue $Generation -Force
    $trade | Add-Member -NotePropertyName closeReason -NotePropertyValue "历史重复买入导致资金超限，系统按有限资金规则撤销该模拟持仓，不发送买卖邮件。" -Force
  }
  return $Trades
}

function Repair-BucketAllocationTrades([array]$Trades, [object]$State, [object]$PortfolioPlan, [int]$Generation, [string]$Now) {
  foreach ($bucket in @($PortfolioPlan.buckets)) {
    $bucketName = [string](Get-Value $bucket "name" "")
    $targetAmount = [double](Get-Value $bucket "targetAmount" 0)
    if ([string]::IsNullOrWhiteSpace($bucketName) -or $targetAmount -le 0) { continue }
    $openTrades = @($Trades | Where-Object {
      [string](Get-Value $_ "status" "") -eq "持仓中" -and [string](Get-Value $_ "bucketName" "") -eq $bucketName
    } | Sort-Object -Property floatingProfit, createdAt)
    $usedAmount = 0.0
    foreach ($trade in $openTrades) { $usedAmount += [double](Get-Value $trade "amount" 0) }
    if ($usedAmount -le ($targetAmount * 1.15)) { continue }
    foreach ($trade in $openTrades) {
      if ($usedAmount -le $targetAmount) { break }
      $experimentId = [string](Get-Value $trade "experimentId" "")
      $experiment = @($State.experiments | Where-Object { [string](Get-Value $_ "id" "") -eq $experimentId } | Select-Object -First 1)
      $price = if ($experiment.Count -gt 0) { Get-AssetPriceFor $State $experiment[0] } else { [double](Get-Value $trade "currentPrice" (Get-Value $trade "buyPrice" 0)) }
      if ($price -le 0) { $price = [double](Get-Value $trade "buyPrice" 0) }
      $buyPrice = [double](Get-Value $trade "buyPrice" 0)
      $quantity = [double](Get-Value $trade "quantity" 0)
      $trade | Add-Member -NotePropertyName status -NotePropertyValue "组合调仓卖出" -Force
      $trade | Add-Member -NotePropertyName action -NotePropertyValue "卖出" -Force
      $trade | Add-Member -NotePropertyName sellPrice -NotePropertyValue $price -Force
      $trade | Add-Member -NotePropertyName closedAt -NotePropertyValue $Now -Force
      $trade | Add-Member -NotePropertyName closedGeneration -NotePropertyValue $Generation -Force
      $trade | Add-Member -NotePropertyName profit -NotePropertyValue ([math]::Round(($price - $buyPrice) * $quantity, 2)) -Force
      $trade | Add-Member -NotePropertyName closeReason -NotePropertyValue "该分组资金超过目标比例，系统执行组合再平衡，释放资金给其他冠军组合。" -Force
      $usedAmount -= [double](Get-Value $trade "amount" 0)
    }
  }
  return $Trades
}

function Invoke-SimulatedTrades([object]$State, [object]$PreviousChampion) {
  $now = (Get-Date).ToUniversalTime().ToString("o")
  $capital = [double](Get-Value $State "capital" 100000)
  $generation = [int](Get-Value $State "generation" 0)
  $portfolioPlan = Build-PortfolioPlan $State
  $State | Add-Member -NotePropertyName portfolioPlan -NotePropertyValue $portfolioPlan -Force
  $planItems = @()
  foreach ($bucket in @($portfolioPlan.buckets)) {
    foreach ($planItem in @($bucket.items)) {
      $planItems += [pscustomobject]@{
        bucketName = $bucket.name
        bucketRatio = $bucket.ratio
        experimentId = $planItem.experimentId
        assetCode = $planItem.assetCode
        targetAmount = $planItem.targetAmount
        score = $planItem.score
      }
    }
  }
  $trades = @($State.simulatedTrades)
  $alerts = @()
  $closedThisRun = @{}
  $maxOpenPositions = 5

  foreach ($trade in $trades) {
    if ([string](Get-Value $trade "status" "") -ne "持仓中") { continue }
    $experimentId = [string](Get-Value $trade "experimentId" "")
    $experiment = @($State.experiments | Where-Object { [string](Get-Value $_ "id" "") -eq $experimentId } | Select-Object -First 1)
    $shouldClose = $false
    $closeReason = ""
    if (!$experiment -or $experiment.Count -eq 0) {
      $shouldClose = $true
      $closeReason = "策略已被淘汰"
    } else {
      $item = $experiment[0]
      $price = Get-AssetPriceFor $State $item
      $buyPrice = [double](Get-Value $trade "buyPrice" 0)
      $quantity = [double](Get-Value $trade "quantity" 0)
      $plannedSellPrice = [double](Get-Value $trade "plannedSellPrice" 0)
      $score = [double](Get-Value $item "score" 0)
      $drawdownPct = [double](Get-Value $item "drawdownPct" 0)
      $returnPct = [double](Get-Value $item "returnPct" 0)
      $cycle = Get-StrategyHoldingPeriod $item
      $computedBucketName = Get-ExperimentBucketName $item
      $bucketName = [string](Get-Value $trade "bucketName" $computedBucketName)
      if ($computedBucketName -ne $bucketName) { $bucketName = $computedBucketName }
      $holdingGenerations = [math]::Max(0, $generation - [int](Get-Value $trade "generation" $generation))
      $trade | Add-Member -NotePropertyName holdingPeriod -NotePropertyValue $cycle.label -Force
      $trade | Add-Member -NotePropertyName bucketName -NotePropertyValue $bucketName -Force
      if ($price -gt 0 -and $buyPrice -gt 0 -and $quantity -gt 0) {
        $floatingProfit = [math]::Round(($price - $buyPrice) * $quantity, 2)
        $floatingPct = [math]::Round((($price - $buyPrice) / $buyPrice) * 100, 2)
        $trade | Add-Member -NotePropertyName currentPrice -NotePropertyValue $price -Force
        $trade | Add-Member -NotePropertyName floatingProfit -NotePropertyValue $floatingProfit -Force
        $trade | Add-Member -NotePropertyName floatingProfitPct -NotePropertyValue $floatingPct -Force
        $trade | Add-Member -NotePropertyName holdingGenerations -NotePropertyValue $holdingGenerations -Force
        $trade | Add-Member -NotePropertyName lastEvaluatedAt -NotePropertyValue $now -Force
      }
      if ([string](Get-Value $item "signal" "") -eq "SELL" -and $holdingGenerations -ge $cycle.min) {
        $shouldClose = $true
        $closeReason = "模型触发卖出信号"
      } elseif ($price -gt 0 -and $plannedSellPrice -gt 0 -and $price -ge $plannedSellPrice) {
        $shouldClose = $true
        $closeReason = "达到计划卖出价"
      } elseif ($holdingGenerations -ge $cycle.target -and $score -lt 55 -and $returnPct -lt 1) {
        $shouldClose = $true
        $closeReason = "到达策略周期后综合分转弱"
      } elseif ($holdingGenerations -ge $cycle.max) {
        $shouldClose = $true
        $closeReason = "达到策略最长周期，落袋复盘"
      } elseif ($drawdownPct -ge 8 -and $holdingGenerations -ge $cycle.min) {
        $shouldClose = $true
        $closeReason = "回撤扩大触发风控"
      } else {
        $sameBucketNewChampion = @($planItems | Where-Object {
          [string](Get-Value $_ "bucketName" "") -eq $bucketName -and
          [string](Get-Value $_ "assetCode" "") -ne [string](Get-Value $trade "assetCode" "") -and
          [double](Get-Value $_ "score" 0) -ge ($score + 8)
        } | Select-Object -First 1)
        if ($sameBucketNewChampion.Count -gt 0 -and $holdingGenerations -ge $cycle.min) {
          $shouldClose = $true
          $closeReason = "同组新冠军优势明显，调仓释放资金"
        }
      }
    }
    if ($shouldClose -and $experiment -and $experiment.Count -gt 0) {
      $item = $experiment[0]
      $price = Get-AssetPriceFor $State $item
      if ($price -gt 0) {
        $rank = [string](Get-Value $item "rank" (Get-Value $trade "rank" ""))
        $trade | Add-Member -NotePropertyName status -NotePropertyValue "已卖出" -Force
        $trade | Add-Member -NotePropertyName action -NotePropertyValue "卖出" -Force
        $trade | Add-Member -NotePropertyName sellPrice -NotePropertyValue $price -Force
        $trade | Add-Member -NotePropertyName closedAt -NotePropertyValue $now -Force
        $trade | Add-Member -NotePropertyName closedGeneration -NotePropertyValue $generation -Force
        $trade | Add-Member -NotePropertyName profit -NotePropertyValue ([math]::Round(($price - [double](Get-Value $trade "buyPrice" 0)) * [double](Get-Value $trade "quantity" 0), 2)) -Force
        $trade | Add-Member -NotePropertyName closeReason -NotePropertyValue $closeReason -Force
        $closedAssetCode = [string](Get-Value $trade "assetCode" "")
        if (![string]::IsNullOrWhiteSpace($closedAssetCode)) { $closedThisRun[$closedAssetCode] = $true }
        $item = Add-TradeExperience $item $trade $closeReason
        if ($rank -eq "king") {
          $sellTradeId = [string](Get-Value $trade "id" "")
          $alerts += [pscustomobject]@{
            key = "SELL-$sellTradeId"
            action = "卖出"
            assetCode = Get-Value $trade "assetCode" ""
            assetName = Get-Value $item "assetName" ""
            strategyName = Get-Value $item "strategyName" ""
            price = $price
            position = Get-Value $item "position" 0
            amount = Get-Value $trade "amount" 0
            reason = $closeReason
            holdingPeriod = Get-Value $trade "holdingPeriod" ""
          }
        }
      }
    }
  }

  $trades = @(Repair-OverAllocatedTrades $trades $capital $generation $now)
  $trades = @(Repair-BucketAllocationTrades $trades $State $portfolioPlan $generation $now)
  $openTrades = @($trades | Where-Object { [string](Get-Value $_ "status" "") -eq "持仓中" })
  $reservedCapital = 0.0
  foreach ($openTrade in $openTrades) { $reservedCapital += [double](Get-Value $openTrade "amount" 0) }
  $availableCapital = [math]::Max(0, $capital - $reservedCapital)

  $championId = [string](Get-Value (Get-Value $State "champion" $null) "id" "")
  $previousChampionId = [string](Get-Value $PreviousChampion "id" "")
  foreach ($target in $planItems) {
    $item = @($State.experiments | Where-Object { [string](Get-Value $_ "id" "") -eq [string](Get-Value $target "experimentId" "") } | Select-Object -First 1)
    if (!$item -or $item.Count -eq 0) { continue }
    $item = $item[0]
    if ([string](Get-Value $item "signal" "") -ne "BUY") { continue }
    $position = [double](Get-Value $item "position" 0)
    if ($position -le 0) { continue }
    $currentOpenCount = @($trades | Where-Object { [string](Get-Value $_ "status" "") -eq "持仓中" }).Count
    if ($currentOpenCount -ge $maxOpenPositions) { continue }
    $experimentId = [string](Get-Value $item "id" "")
    $assetCode = [string](Get-Value $item "assetCode" "")
    if ($closedThisRun.ContainsKey($assetCode)) { continue }
    $openExists = @($trades | Where-Object { [string](Get-Value $_ "experimentId" "") -eq $experimentId -and [string](Get-Value $_ "status" "") -eq "持仓中" })
    if ($openExists.Count -gt 0) { continue }
    $assetOpenExists = @($trades | Where-Object { [string](Get-Value $_ "assetCode" "") -eq $assetCode -and [string](Get-Value $_ "status" "") -eq "持仓中" })
    if ($assetOpenExists.Count -gt 0) { continue }
    $recentClosed = @($trades | Where-Object {
      [string](Get-Value $_ "assetCode" "") -eq $assetCode -and
      [string](Get-Value $_ "status" "") -ne "持仓中" -and
      ($generation - [int](Get-Value $_ "closedGeneration" (Get-Value $_ "generation" 0))) -lt 3
    })
    if ($recentClosed.Count -gt 0) { continue }
    $price = Get-AssetPriceFor $State $item
    if ($price -le 0) { continue }
    $targetAmount = [double](Get-Value $target "targetAmount" 0)
    if ($targetAmount -le 0) { $targetAmount = $capital * $position / 100 }
    $targetBucketName = [string](Get-Value $target "bucketName" (Get-ExperimentBucketName $item))
    $targetBucket = @($portfolioPlan.buckets | Where-Object { [string](Get-Value $_ "name" "") -eq $targetBucketName } | Select-Object -First 1)
    $bucketTargetAmount = if ($targetBucket.Count -gt 0) { [double](Get-Value $targetBucket[0] "targetAmount" 0) } else { 0 }
    $bucketUsedAmount = 0.0
    foreach ($openTrade in @($trades | Where-Object { [string](Get-Value $_ "status" "") -eq "持仓中" -and [string](Get-Value $_ "bucketName" "") -eq $targetBucketName })) {
      $bucketUsedAmount += [double](Get-Value $openTrade "amount" 0)
    }
    $bucketAvailableAmount = if ($bucketTargetAmount -gt 0) { [math]::Max(0, $bucketTargetAmount - $bucketUsedAmount) } else { $availableCapital }
    $amount = [math]::Round([math]::Min($targetAmount, [math]::Min($availableCapital, $bucketAvailableAmount)), 2)
    if ($amount -lt 1000) { continue }
    $quantity = $amount / $price
    $cycle = Get-StrategyHoldingPeriod $item
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
      currentPrice = $price
      floatingProfit = 0
      floatingProfitPct = 0
      holdingGenerations = 0
      holdingPeriod = $cycle.label
      bucketName = $targetBucketName
      bucketRatio = Get-Value $target "bucketRatio" 0
      closeReason = ""
      rank = Get-Value $item "rank" ""
      createdAt = $now
      lastEvaluatedAt = $now
    }) + $trades
    $availableCapital = [math]::Max(0, $availableCapital - $amount)
    $isNewChampionBuy = ([string](Get-Value $item "id" "") -eq $championId) -and ($championId -ne $previousChampionId)
    if ([string](Get-Value $item "rank" "") -eq "king" -and $isNewChampionBuy) {
      $buyAssetCode = [string](Get-Value $item "assetCode" "")
      $alerts += [pscustomobject]@{
        key = "BUY-$buyAssetCode-$generation"
        action = "买入"
        assetCode = Get-Value $item "assetCode" ""
        assetName = Get-Value $item "assetName" ""
        strategyName = Get-Value $item "strategyName" ""
        price = $price
        position = $position
        amount = $amount
        reason = "新标的首次进入王者买入条件"
        holdingPeriod = $cycle.label
      }
    }
  }

  $State.simulatedTrades = @($trades | Select-Object -First 40)
  Write-LabMemory $State
  Send-KingTradeAlerts $State $alerts
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
