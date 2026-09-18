# 查询真实注册的物品组内容（/touhou groups 是按注册表反查的）
param(
    [string]$ServerDir = "D:\MC\Server\paper",
    [string]$Jar       = "D:\MC\Server\paper\paper-1.20.4-499.jar",
    [int]$ReadyTimeoutSec = 300
)
$ErrorActionPreference = "Stop"
$serverLog = Join-Path $ServerDir "logs\latest.log"

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "java"
$psi.Arguments = "-Xmx4096M -Xms1024M -jar `"$Jar`" nogui"
$psi.WorkingDirectory = $ServerDir
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.CreateNoWindow = $true
$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi
Write-Host "[probe] 启动服务端..."
[void]$proc.Start()
$stdin = New-Object System.IO.StreamWriter($proc.StandardInput.BaseStream, [System.Text.Encoding]::ASCII)
$stdin.AutoFlush = $true

function Send-Cmd { param([string]$c) Write-Host "  > $c"; $stdin.WriteLine($c); $stdin.Flush() }
function Get-ServerLog {
    if (-not (Test-Path $serverLog)) { return "" }
    try { return (Get-Content $serverLog -Raw -Encoding UTF8 -ErrorAction Stop) } catch { return "" }
}

try {
    $deadline = (Get-Date).AddSeconds($ReadyTimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if ((Get-ServerLog) -match 'Done \(') { break }
        if ($proc.HasExited) { throw "服务端提前退出" }
        Start-Sleep -Milliseconds 700
    }
    Write-Host "[probe] 已就绪"
    Start-Sleep -Seconds 2
    Send-Cmd "say ===== PROBE2 BEGIN ====="
    Start-Sleep -Seconds 1
    Send-Cmd "touhou groups"
    Start-Sleep -Seconds 3
}
finally {
    if (-not $proc.HasExited) {
        try { Send-Cmd "stop" } catch { }
        if (-not $proc.WaitForExit(180000)) { try { $proc.Kill() } catch { } }
    }
    try { $stdin.Close() } catch { }
    Start-Sleep -Seconds 2
}

$lines = (Get-ServerLog) -split "`r?`n"
# 取最后一个 PROBE2 BEGIN
$idx = 0
for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -match 'PROBE2 BEGIN') { $idx = $i } }
Write-Host ""
Write-Host "==================== /touhou groups 输出 ===================="
$lines[$idx..($lines.Count - 1)] | Where-Object { $_ -match 'TOUHOU|物品组|层级|OK|未注册|项|槽|TH_TECH|MATERIAL|MACHINE|PARTY|INFO' } |
    ForEach-Object { Write-Host $_ }
