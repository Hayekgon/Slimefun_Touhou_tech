# 只跑一条命令：IO 接口布局自检
param(
    [string]$ServerDir = "D:\MC\Server\paper",
    [string]$Jar = "D:\MC\Server\paper\paper-1.20.4-499.jar",
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
[void]$proc.Start()
$stdin = New-Object System.IO.StreamWriter($proc.StandardInput.BaseStream, [System.Text.Encoding]::ASCII)
$stdin.AutoFlush = $true
function Send-Cmd { param([string]$c) $stdin.WriteLine($c); $stdin.Flush() }
function Get-Log {
    if (-not (Test-Path $serverLog)) { return "" }
    try { return (Get-Content $serverLog -Raw -Encoding UTF8 -ErrorAction Stop) } catch { return "" }
}
try {
    $deadline = (Get-Date).AddSeconds($ReadyTimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if ((Get-Log) -match 'Done \(') { break }
        if ($proc.HasExited) { throw "服务端提前退出" }
        Start-Sleep -Milliseconds 700
    }
    Start-Sleep -Seconds 2
    Send-Cmd "say ===== LAYOUT CHECK ====="
    Start-Sleep -Seconds 1
    # 接口在上次测试里放在 98,100,99；结构仍在
    Send-Cmd "touhou reactor 98 100 99 io layout"
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
$lines = (Get-Log) -split "`r?`n"
$idx = 0
for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -match 'LAYOUT CHECK') { $idx = $i } }
Write-Host "===== 本次布局自检输出 ====="
$lines[$idx..($lines.Count - 1)] |
    Where-Object { $_ -match '合计=|无重叠|X 槽|OK 54|FAIL|实际 |期望 |占位|←|尺寸' } |
    ForEach-Object { Write-Host ($_ -replace '^\[[^\]]+\]\s*\[[^\]]+\]:\s*','') }
