# ============================================================================
# 查询实际注册的物品：对新旧 id 分别执行 /sf give，看哪个能取到。
#   powershell -ExecutionPolicy Bypass -File .\tools\probe-ids.ps1
# （纯查询，不改任何东西；用完自动关服）
# ============================================================================
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
    Send-Cmd "say ===== PROBE BEGIN ====="
    Start-Sleep -Seconds 1
    # 逐个取。成功/失败都会在日志里留下痕迹
    Send-Cmd "sf give Ning_Meng__ TOUHOU_INFO_MODESHIFT"
    Start-Sleep -Milliseconds 800
    Send-Cmd "sf give Ning_Meng__ TOUHOU_PHD_MODESHIFT"
    Start-Sleep -Milliseconds 800
    Send-Cmd "sf give Ning_Meng__ TOUHOU_COMPLEX_MACHINE_REACTOR_SHIELD"
    Start-Sleep -Milliseconds 800
    Send-Cmd "sf give Ning_Meng__ TOUHOU_COMPLEX_MACHINE_REACTOR_SHELL"
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
$idx = 0
for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -match 'PROBE BEGIN') { $idx = $i } }
Write-Host ""
Write-Host "==================== /sf give 结果 ===================="
$lines[$idx..($lines.Count - 1)] | Where-Object { $_ -match 'give|Unknown|unknown|not exist|不存在|已给予|PROBE|§|&' } |
    ForEach-Object { Write-Host $_ }
