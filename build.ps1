# ============================================================================
# Touhou（TH Tech）构建脚本（无 Maven 的 javac 直编路线）
#
#   powershell -ExecutionPolicy Bypass -File .\build.ps1
#   powershell -ExecutionPolicy Bypass -File .\build.ps1 -SkipPackage
#
# 依赖：18 个 jar 在 -Lib 指定的目录（默认 ..\mods\hakurei_gohei\_lib）
#   paper-api / Slimefun4 / annotations / javax.annotation-api / guava / gson /
#   snakeyaml / commons-lang3 / fastutil / bungeecord-chat / adventure-* / examination-*
#
# 产出：out\  （classes，供 CLI 直接 -cp 使用）
#       Touhou-<Version>.jar （可直接丢进 plugins\）
#
# ★ 版本号只有一个出处：下面 param 里的 $Version 默认值。
#   它会被替换进打进 jar 的 plugin.yml 的 ${project.version}（本工程没有 Maven，
#   所以不能用 pom 那一套 —— 见本文件后面的 .Replace('${project.version}', $Version)）。
# ============================================================================
param(
    [string]$Root = $PSScriptRoot,
    [string]$Lib = "",
    [string]$ExtraLib = "",
    [string]$Version = "1.0.1",
    [switch]$SkipPackage
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Lib)) {
    $Lib = Join-Path $Root "..\mods\hakurei_gohei\_lib"
}
if ([string]::IsNullOrWhiteSpace($ExtraLib)) {
    $ExtraLib = Join-Path $Root "..\_fromscratch\_lib_extra"
}
$Lib = (Resolve-Path $Lib).Path

$src   = Join-Path $Root "src\main\java"
$res   = Join-Path $Root "src\main\resources"
$out   = Join-Path $Root "out"
$stage = Join-Path $Root "build"
$jar   = Join-Path $Root "Touhou-$Version.jar"

$jars = @((Get-ChildItem (Join-Path $Lib "*.jar")).FullName)
if ($jars.Count -eq 0) { throw "依赖目录里没有 jar: $Lib" }

# _lib_extra：同名 artifact 的"对齐运行时的版本"覆盖（例如服务端用 adventure-api 4.16，
# 而 _lib 里是 4.14 —— 4.14 缺 ComponentDecoder，会让 javac 在解析 Paper API 的
# @NotNull 注解时报 CompletionFailure）。默认存在就用，不存在就跳过。
if (Test-Path $ExtraLib) {
    $extra = @((Get-ChildItem (Join-Path $ExtraLib "*.jar")).FullName)
    if ($extra.Count -gt 0) {
        $extraArtifacts = @()
        foreach ($e in $extra) {
            $extraArtifacts += [System.IO.Path]::GetFileName($e).Split("-")[0]
        }
        $keep = @()
        foreach ($j in $jars) {
            $artifact = [System.IO.Path]::GetFileName($j).Split("-")[0]
            if ($extraArtifacts -notcontains $artifact) { $keep += $j }
        }
        $jars = $keep + $extra
        Write-Host "[build] version overrides from _lib_extra = $($extra.Count)"
    }
}
Write-Host "[build] lib jars = $($jars.Count)  ($Lib)"

# ---- 编译 -------------------------------------------------------------------
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null

$files = Get-ChildItem $src -Recurse -Filter *.java | ForEach-Object FullName
Write-Host "[build] sources  = $($files.Count)"

$cp = $jars -join ";"
# javac/jar 会把 "Note: ... 使用了过时的 API" 写到 stderr；
# 在 $ErrorActionPreference = "Stop" 下 native stderr 会被当成终止性错误，
# 所以这两个调用前后临时放宽。真正的失败仍由 $LASTEXITCODE 判定。
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& javac --release 21 -encoding UTF-8 -proc:none -nowarn -cp $cp -d $out $files
$javacExit = $LASTEXITCODE
$ErrorActionPreference = $prevEap
if ($javacExit -ne 0) { throw "javac 失败 (exit $javacExit)" }
Write-Host "[build] javac OK -> $out"

if ($SkipPackage) { return }

# ---- 打包 -------------------------------------------------------------------
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force $stage | Out-Null
Copy-Item "$out\*" $stage -Recurse -Force

# Maven 的 resources filtering 在这里手工替代
(Get-Content (Join-Path $res "plugin.yml") -Raw -Encoding UTF8).Replace('${project.version}', $Version) |
    Set-Content (Join-Path $stage "plugin.yml") -Encoding UTF8 -NoNewline
Copy-Item (Join-Path $res "config.yml") $stage -Force

if (Test-Path $jar) { Remove-Item $jar -Force }
Push-Location $stage
try {
    $ErrorActionPreference = "Continue"
    & jar cf $jar .
    $jarExit = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($jarExit -ne 0) { throw "jar 打包失败 (exit $jarExit)" }
} finally {
    Pop-Location
}

# ---- 自检 -------------------------------------------------------------------
$ErrorActionPreference = "Continue"
$list = & jar tf $jar
$ErrorActionPreference = "Stop"
$sfClasses = ($list | Where-Object { $_ -like 'io/github/thebusybiscuit*' }).Count
$bukkit    = ($list | Where-Object { $_ -like 'org/bukkit*' }).Count
Write-Host "[build] $([System.IO.Path]::GetFileName($jar))  entries=$($list.Count)  slimefun-classes=$sfClasses  bukkit-classes=$bukkit"
if ($sfClasses -ne 0 -or $bukkit -ne 0) {
    throw "jar 里混进了 Slimefun/Paper 的类，必须剔除！"
}
if (-not ($list -contains "plugin.yml")) { throw "jar 里没有 plugin.yml" }
Write-Host "[build] package OK -> $jar"
