# ============================================================================
# Touhou 插件 —— 无人值守联机实测
#
#   powershell -ExecutionPolicy Bypass -File .\tools\autotest.ps1
#
# 做什么：
#   1) 启动测试服，轮询服务端自己的 logs\latest.log 等 "Done ("
#   2) 用控制台命令真实搭出一座完整的 5x5x5 灵乌路空反应堆（46 格构件）
#   3) 验证本次新增功能：GUI 布局(新增槽 B) / 自动构建 / 手动构建 / 检测节流
#   4) 发 stop 优雅关服，打印关键日志与结论
#
# ★★ 为什么用 latest.log 而不是重定向 stdout：
#   .NET 的 BeginOutputReadLine() 的 DataReceived 回调需要事件循环泵，
#   在纯 PowerShell 脚本里等不到 —— 结果是"服务端明明起来了，脚本却
#   永远看不到 Done ("（本次实测踩过）。改用服务端自己写的日志文件后，
#   它是 UTF-8 的，中文不会乱码，也不需要事件循环。
#
# ★ 命令只用 ASCII（坐标/英文子命令）：命令通过 stdin 发给服务端，
#   而 Java 在 Windows 上按控制台代码页读 stdin，中文会变成乱码。
#
# ★ 本文件必须带 UTF-8 BOM：Windows PowerShell 5.1 读无 BOM 的 UTF-8
#   会把中文注释按 ANSI 解码，然后报一堆指向无辜花括号的语法错误。
# ============================================================================
param(
    [string]$ServerDir = "D:\MC\Server\paper",
    [string]$Jar       = "D:\MC\Server\paper\paper-1.20.4-499.jar",
    [int]$ReadyTimeoutSec  = 300,
    [int]$RunAfterReadySec = 60,
    [string]$OutLog    = "$PSScriptRoot\..\autotest-result.log",
    [switch]$KeepPluginConfig
)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------- 结构定义
# 核心 (cx,cy,cz) = (100,100,100)。层图见 src/main/resources/config.yml：
#   y=-2 满层：外圈 F，内部 3x3 = B
#   y=-1/+1 环层：角 F(4) + 边中点 S(12)，内部空气
#   y= 0 环层：角 F(4) + 边中点 S(11) + 核心 C(1)，内部空气
#   y=+2 满层：外圈 F，内部 3x3 = T
# 共 46 个构件（其余是必须为空气的格子）
$cx = 100; $cy = 100; $cz = 100

$F    = "TOUHOU_COMPLEX_MACHINE_REACTOR_FRAME"
$S    = "TOUHOU_COMPLEX_MACHINE_REACTOR_SHIELD"     # 是 SHIELD，不是 SHELL
$B    = "TOUHOU_COMPLEX_MACHINE_REACTOR_BASE"
$T    = "TOUHOU_COMPLEX_MACHINE_REACTOR_STABILIZER"
$CORE = "TOUHOU_COMPLEX_MACHINE_UTSUHO_REACTOR_CORE"
$IN   = "TOUHOU_COMPLEX_MACHINE_REACTOR_INPUT_PORT"
$OP   = "TOUHOU_COMPLEX_MACHINE_REACTOR_OUTPUT_PORT"

function New-StructureCommands {
    param([int]$cx, [int]$cy, [int]$cz)
    $cmds = New-Object System.Collections.Generic.List[string]
    # 先铺 5x5 石基座（结构最底层 y-2 的下方），保证底下是实心地面
    for ($x = $cx - 2; $x -le $cx + 2; $x++) {
        for ($z = $cz - 2; $z -le $cz + 2; $z++) {
            $cmds.Add("setblock $x $($cy - 3) $z minecraft:stone")
        }
    }
    # 核心先放：其余构件的检测以它为中心
    $cmds.Add("touhou place $cx $cy $cz $CORE --force")

    for ($dy = -2; $dy -le 2; $dy++) {
        $y = $cy + $dy
        $fullLayer = ($dy -eq -2) -or ($dy -eq 2)
        for ($dx = -2; $dx -le 2; $dx++) {
            for ($dz = -2; $dz -le 2; $dz++) {
                if ($dx -eq 0 -and $dy -eq 0 -and $dz -eq 0) { continue }   # 核心已放
                $x = $cx + $dx; $z = $cz + $dz
                if ($fullLayer) {
                    $inner = ([math]::Abs($dx) -le 1) -and ([math]::Abs($dz) -le 1)
                    if ($inner) {
                        $id = if ($dy -eq 2) { $T } else { $B }
                    } else {
                        $id = $F
                    }
                } else {
                    $dist = [math]::Max([math]::Abs($dx), [math]::Abs($dz))
                    if ($dist -ne 2) { continue }        # 内部是空气，不下命令
                    # 角 = F，边中点 = S
                    $id = if (([math]::Abs($dx) -eq 2) -and ([math]::Abs($dz) -eq 2)) { $F } else { $S }
                }
                $cmds.Add("touhou place $x $y $z $id --force")
            }
        }
    }
    return $cmds
}

# ---------------------------------------------------------------- 准备
$serverLog = Join-Path $ServerDir "logs\latest.log"
$out = [System.IO.Path]::GetFullPath($OutLog)

if (-not $KeepPluginConfig) {
    $cfg = Join-Path $ServerDir "plugins\Touhou\config.yml"
    if (Test-Path $cfg) {
        Remove-Item $cfg -Force
        Write-Host "[test] 已删除旧的 plugins\Touhou\config.yml（让新配置项生效）"
    }
}

# ---------------------------------------------------------------- 启动
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "java"
$psi.Arguments = "-Xmx8192M -Xms2048M -jar `"$Jar`" nogui"
$psi.WorkingDirectory = $ServerDir
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.CreateNoWindow = $true

$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi
Write-Host "[test] 启动测试服: $Jar"
[void]$proc.Start()
$stdin = New-Object System.IO.StreamWriter($proc.StandardInput.BaseStream, [System.Text.Encoding]::ASCII)
$stdin.AutoFlush = $true

# ---------------------------------------------------------------- 工具函数
function Send-Cmd {
    param([string]$c)
    Write-Host "  > $c"
    $stdin.WriteLine($c)
    $stdin.Flush()
}

# 读服务端日志（UTF-8；文件被服务端占用也允许共享读）
function Get-ServerLog {
    if (-not (Test-Path $serverLog)) { return "" }
    try { return (Get-Content $serverLog -Raw -Encoding UTF8 -ErrorAction Stop) } catch { return "" }
}

function Wait-Log {
    param([string]$Pattern, [int]$TimeoutSec, [string]$What)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if ((Get-ServerLog) -match $Pattern) { return $true }
        if ($proc.HasExited) { return $false }
        Start-Sleep -Milliseconds 700
    }
    Write-Warning "[test] 等待超时: $What (pattern=$Pattern)"
    return $false
}

try {
    Write-Host "[test] 等待服务端就绪（最多 $ReadyTimeoutSec 秒，轮询 logs\latest.log）..."
    if (-not (Wait-Log -Pattern 'Done \(' -TimeoutSec $ReadyTimeoutSec -What "服务端就绪")) {
        throw "服务端没有在 $ReadyTimeoutSec 秒内就绪"
    }
    Write-Host "[test] 服务端已就绪"
    Start-Sleep -Seconds 2

    # 记一个可搜索的分隔标记（会出现在服务端日志里）
    Send-Cmd "say ===== AUTOTEST BEGIN ====="
    Start-Sleep -Seconds 1

    # 强制加载结构所在区块，保证核心那格的方块数据是"已加载"状态
    # （否则 BlockTicker 不会 tick 它，只有 /touhou 命令能读到数据）
    Send-Cmd "forceload add 96 96 104 104"
    Start-Sleep -Seconds 2

    # ---------------------------------------------------------- 搭结构
    $cmds = New-StructureCommands -cx $cx -cy $cy -cz $cz
    Write-Host "[test] 搭建结构，共 $($cmds.Count) 条命令"
    foreach ($c in $cmds) { Send-Cmd $c }
    Start-Sleep -Seconds 3

    # ---------------------------------------------------------- 验证
    Send-Cmd "touhou layout"
    Start-Sleep -Seconds 1
    # 物品组层级：新增的 1 级组 POWER 必须出现在注册表里，且指南主菜单仍只有 1 个大类
    Send-Cmd "touhou groups"
    Start-Sleep -Seconds 2
    Send-Cmd "touhou structure $cx $cy $cz"
    Start-Sleep -Seconds 1

    # 1) 手动构建：结构完整也必须停在"未激活"
    Send-Cmd "touhou autobuild $cx $cy $cz manual 20"
    Start-Sleep -Seconds 3

    # 2) 自动构建：应该自动激活
    Send-Cmd "touhou autobuild $cx $cy $cz auto 20"
    Start-Sleep -Seconds 3

    # 2b) 结构变动触发检测（现在【唯一】的常规触发途径：放置 / 破坏 / 爆炸）
    #     需求：「仅当核心与其他结构方块被放置/破坏时进行一次检测；
    #            构件被放置时对周围核心静默检测一次；找不到核心就停止检测；
    #            找到核心则构件停手，带动核心做一次整套结构检测」。
    #     ★ 必须先把结构弄【不完整】，否则检测出来就是"完整"，看不出差别。
    #       做法：把核心正上方 (cx, cy+2, cz-1) 那格稳定器删掉。
    #     ★ 必须用 /touhou remove 而不是 setblock：
    #       ① setblock air 只改世界方块，不清 Slimefun 方块数据，结构检测走 checkID，仍认为构件还在；
    #       ② Slimefun 也没有 sf-remove 这个子命令（只有 /sf cleardata 之类），照抄会静默失败。
    #     ★ 模拟事件用 /touhou edit（不是右键 —— 右键触发已经在这次改动里删掉了）。
    $bx = $cx; $by = $cy + 2; $bz = $cz - 2      # 顶面外圈框架，仍然存在
    Send-Cmd "touhou reactor $cx $cy $cz buildmode auto"
    Start-Sleep -Seconds 2
    Send-Cmd "touhou remove $cx $($cy + 2) $($cz - 1)"
    Start-Sleep -Seconds 2
    Send-Cmd "touhou structure $cx $cy $cz"
    Start-Sleep -Seconds 2
    Send-Cmd "touhou reactor $cx $cy $cz abort"
    Start-Sleep -Seconds 1
    #     ① 把一个【仍在的】构件当作"刚被放置" -> 构件找核心 -> 核心整套检测 -> 报不完整
    Send-Cmd "touhou edit $bx $by $bz placed"
    Start-Sleep -Seconds 2
    #     ② 补回那一格，并把核心当作"刚被放置" -> 核心整套检测 -> 自动激活
    Send-Cmd "touhou place $cx $($cy + 2) $($cz - 1) $T --force"
    Start-Sleep -Seconds 1
    Send-Cmd "touhou edit $cx $cy $cz placed"
    Start-Sleep -Seconds 2
    #     ③ 拆掉一个构件（broken）-> 走同一套双向逻辑 -> 应报不完整 + 撤销登记
    Send-Cmd "touhou edit $bx $by $bz broken"
    Start-Sleep -Seconds 2
    #     ④ 再当作放置补回 -> 应重新完整并自动激活
    Send-Cmd "touhou edit $cx $cy $cz placed"
    Start-Sleep -Seconds 2
    #     ⑤ 让一个构件模拟"核心还没放下"：找核心失败应停止检测、标记等候
    Send-Cmd "touhou edit $($cx + 40) $cy $cz placed"
    Start-Sleep -Seconds 2
    #     ⑥ 最终状态 + 归属登记 + 检测计数
    Send-Cmd "touhou reactor $cx $cy $cz info"
    Start-Sleep -Seconds 2
    Send-Cmd "touhou reactor $cx $cy $cz scan"
    Start-Sleep -Seconds 2

    # 3) 检测状态 / 设置入口 / 总览
    Send-Cmd "touhou reactor $cx $cy $cz scan"
    Start-Sleep -Seconds 1
    Send-Cmd "touhou reactor $cx $cy $cz buildmode auto"
    Start-Sleep -Seconds 1
    Send-Cmd "touhou reactor $cx $cy $cz info"
    Start-Sleep -Seconds 1

    # 4) 7 格内放能源调节器 -> 发电模式的电网门控应能通过
    Send-Cmd "touhou place $($cx + 3) $cy $cz ENERGY_REGULATOR --force"
    Start-Sleep -Seconds 1
    Send-Cmd "touhou reactor $cx $cy $cz gate"
    Start-Sleep -Seconds 2

    # ---------------------------------------------------------- IO 接口
    # 两个接口各取代一格保护罩（三者共用标签，结构仍应完整）
    # 输入接口放左边中点、输出接口放右边中点，都在外壳那一圈
    $px = $cx - 2; $py = $cy; $pz = $cz - 1
    $qx = $cx + 2; $qy = $cy; $qz = $cz - 1
    Write-Host "[test] 输入接口 @ ($px,$py,$pz)，输出接口 @ ($qx,$qy,$qz)"
    Send-Cmd "touhou place $px $py $pz $IN --force"
    Start-Sleep -Seconds 1
    Send-Cmd "touhou place $qx $qy $qz $OP --force"
    Start-Sleep -Seconds 2

    # 结构应仍然完整（接口与保护罩共用 touhou:reactor_shell 标签）
    Send-Cmd "touhou structure $cx $cy $cz"
    Start-Sleep -Seconds 1
    # 四向强制探测：无视对称性优化，四个方向各检测一遍
    Send-Cmd "touhou structure $cx $cy $cz alldirs"
    Start-Sleep -Seconds 2

    # 1) 两个接口的 GUI 布局自检 + 占位符来源
    Send-Cmd "touhou reactor $px $py $pz io layout"
    Start-Sleep -Seconds 2
    Send-Cmd "touhou reactor $qx $qy $qz io layout"
    Start-Sleep -Seconds 2
    # 锁槽自检：除主槽区外每一格都必须已锁死
    Send-Cmd "touhou reactor $px $py $pz io guard"
    Start-Sleep -Seconds 1
    Send-Cmd "touhou reactor $qx $qy $qz io guard"
    Start-Sleep -Seconds 1
    # 核心界面也走同一套 GuiLock（输出槽=只出不进）
    Send-Cmd "touhou gui $cx $cy $cz"
    Start-Sleep -Seconds 1
    # 全局一次看全部
    Send-Cmd "touhou gui"
    Start-Sleep -Seconds 2

    # 2) 识别核心 + 容器归属（决定性判据：接口与核心【不能】是同一个容器对象）
    Send-Cmd "touhou reactor $px $py $pz io"
    Start-Sleep -Seconds 2
    Send-Cmd "touhou reactor $qx $qy $qz io"
    Start-Sleep -Seconds 2

    # 3) 中止按钮链路（此刻本来没有进程，应报"本来就没有进程"）
    Send-Cmd "touhou reactor $px $py $pz io abort"
    Start-Sleep -Seconds 2

    # 4) 搬运验证：只在本接口"负责的那一侧"塞标记物
    #    输入接口自有槽 = 10；输出接口自有槽 = 13
    Send-Cmd "touhou reactor $px $py $pz io seed port 10"
    Start-Sleep -Seconds 1
    Send-Cmd "touhou reactor $px $py $pz io count"
    Start-Sleep -Seconds 1
    Send-Cmd "touhou reactor $px $py $pz io"
    Start-Sleep -Seconds 2
    Send-Cmd "touhou reactor $px $py $pz io count"
    Start-Sleep -Seconds 1
    # 输出接口：往核心输出槽塞，应被搬到输出接口
    Send-Cmd "touhou reactor $qx $qy $qz io seed core 14"
    Start-Sleep -Seconds 1
    Send-Cmd "touhou reactor $qx $qy $qz io count"
    Start-Sleep -Seconds 1
    Send-Cmd "touhou reactor $qx $qy $qz io"
    Start-Sleep -Seconds 2
    Send-Cmd "touhou reactor $qx $qy $qz io count"
    Start-Sleep -Seconds 2

    # 4b) 消息栏档位（本轮：默认只推重要事件 + warning/error）
    Send-Cmd "touhou messages"
    Start-Sleep -Seconds 1
    # 5) 「孤立接口不要每 tick 重扫 125 格」的证据：隔 8 秒取两次扫描计数
    #    世界上本来就有玩家留下的孤立接口（启动后日志里那些"没找到反应堆核心"），
    #    所以这里不需要自己再造一个。
    #    期望：每秒扫描次数 ≈ 孤立接口数 × (1000 / core-scan-retry-ms)
    #          若接近「接口数 × tick 速率（10/秒）」说明负结果缓存没生效。
    Send-Cmd "touhou reactor $px $py $pz io scans"
    Start-Sleep -Seconds 8
    Send-Cmd "touhou reactor $px $py $pz io scans"
    Start-Sleep -Seconds 2

    Write-Host "[test] 验证命令发完，再运行 $RunAfterReadySec 秒后关服"
    Start-Sleep -Seconds $RunAfterReadySec
}
catch {
    Write-Warning "[test] 中途出错: $_"
}
finally {
    if (-not $proc.HasExited) {
        Write-Host "[test] 发送 stop（优雅关服，让世界正常保存）"
        try { Send-Cmd "stop" } catch { }
        if (-not $proc.WaitForExit(180000)) {
            Write-Warning "[test] stop 后 180 秒仍在运行，强制结束"
            try { $proc.Kill() } catch { }
        }
    }
    try { $stdin.Close() } catch { }
    Start-Sleep -Seconds 2
}

# ---------------------------------------------------------------- 结论
$logText = Get-ServerLog
Set-Content -Path $out -Value $logText -Encoding UTF8
$lines = $logText -split "`r?`n"

Write-Host ""
Write-Host "==================== 插件加载 ===================="
$lines | Where-Object { $_ -match '\[Touhou\]' } | Select-Object -First 40 | ForEach-Object { Write-Host $_ }

Write-Host ""
Write-Host "==================== 本次验证输出 ===================="
# 只取 AUTOTEST BEGIN 之后的部分，防旧日志混入
$idx = 0
for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -match 'AUTOTEST BEGIN') { $idx = $i } }
$tail = if ($lines.Count -gt 0) { $lines[$idx..($lines.Count - 1)] } else { @() }
$tail | Where-Object { $_ -match 'TOUHOU|OK 54|FAIL|✔|✘|缺 |错 |状态|激活|构建|节流|合计|计数器|槽|结构|模式|接口|代理|搬运|红石|中止|占位' } |
    Select-Object -First 200 | ForEach-Object { Write-Host $_ }

Write-Host ""
Write-Host "==================== 异常检查 ===================="
$errs = $lines | Where-Object { $_ -match 'ERROR|Exception|Could not pass event|SEVERE' -and $_ -notmatch 'SILVER|NTWEXPANSION' }
if ($errs) {
    $errs | Select-Object -First 40 | ForEach-Object { Write-Host $_ }
} else {
    Write-Host "（没有 ERROR / Exception —— 已知的 SILVER 与 NTWEXPANSION 两条固有告警已排除）"
}

Write-Host ""
Write-Host "==================== 关服检查 ===================="
$lines | Where-Object { $_ -match 'Closing Server|Disabling Touhou|Saving worlds|All chunks are saved|Disabling LogiTech' } |
    Select-Object -First 10 | ForEach-Object { Write-Host $_ }

Write-Host ""
Write-Host "[test] 服务端日志副本: $out"
Write-Host "[test] 服务端原日志  : $serverLog"
