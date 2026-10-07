#Requires -Version 5.1
<#
.SYNOPSIS
    统计 AHA 项目中 Java 源码的有效行数（LOC）。

.DESCRIPTION
    按「模块 × main/test」分组，统计三类行：
      Code    —— 有效代码行（不含空行、注释）
      Comment —— 注释行（// 行注释、/* */ 块注释）
      Blank   —— 空行

    判定规则：整行内容均落在注释内（含独立的 `*/` 闭合行、块注释内的空行）
    记为 Comment；行内出现首个非注释字符即记为 Code；其余为 Blank。

    默认排除 target/、dist/、bin/、.agents/ 等非产品目录，避免把构建产物与
    技能模板（.agents/skills/*/assets/*.java）计入产品代码。

.PARAMETER Root
    仓库根目录，默认为本脚本的上溯两级目录。

.PARAMETER Detailed
    额外输出每个文件的明细统计。

.NOTES
    行统计为启发式实现，已知局限：无法识别字符串字面量，Java 文本块
    （""" ... """）内以 `//` 或 `/*` 开头的行会被误判为注释行。

.EXAMPLE
    powershell -NoProfile -File Script/Powershell/CountJavaLoc.ps1

.EXAMPLE
    powershell -NoProfile -File Script/Powershell/CountJavaLoc.ps1 -Detailed
#>
[CmdletBinding()]
param(
    [string]$Root,
    [switch]$Detailed
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# 非产品代码目录：构建产物、IDE 配置、技能模板、启动脚本
$SkipDirs = @('target', 'dist', 'bin', '.git', '.idea', '.vscode', 'node_modules', '.agents')

function Get-JavaLineStats {
    <#
    .SYNOPSIS
        统计单个 Java 文件的 代码/注释/空行 数量。

    .DESCRIPTION
        逐字符扫描，遇到首个代码字符即停止（因此行尾的 `//` 说明不会被误判）。
        块注释状态跨行保持。
    #>
    param([Parameter(Mandatory)][string]$Path)

    $code = 0
    $comment = 0
    $blank = 0
    $inBlock = $false

    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        $hasCode = $false
        $hasComment = $false
        $i = 0
        $len = $line.Length

        while ($i -lt $len) {
            if ($inBlock) {
                $hasComment = $true
                $end = $line.IndexOf('*/', $i)
                if ($end -lt 0) { $i = $len; break }
                $i = $end + 2
                $inBlock = $false
                continue
            }

            $ch = $line[$i]

            if ([char]::IsWhiteSpace($ch)) { $i++; continue }

            if ($ch -eq '/') {
                if ($i + 1 -lt $len -and $line[$i + 1] -eq '/') {
                    $hasComment = $true
                    break
                }
                if ($i + 1 -lt $len -and $line[$i + 1] -eq '*') {
                    $hasComment = $true
                    $i += 2
                    $inBlock = $true
                    continue
                }
            }

            # 首个非注释字符：本行余下内容按代码处理
            $hasCode = $true
            break
        }

        if ($hasCode) { $code++ }
        elseif ($hasComment) { $comment++ }
        else { $blank++ }
    }

    [PSCustomObject]@{ Code = $code; Comment = $comment; Blank = $blank }
}

# ── 解析仓库根目录 ──────────────────────────────────────────
# 注意：$PSScriptRoot 在 param() 默认值中不可靠（PS 5.1 可能为空），故在脚本体内解析
$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
if (-not $Root) {
    $Root = Join-Path $scriptDir '..\..'
}
$rootFull = [System.IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
$homeLen = $rootFull.Length

# ── 收集 Java 源文件 ────────────────────────────────────────
$files = Get-ChildItem -LiteralPath $rootFull -Recurse -File -Filter '*.java' | Where-Object {
    $rel = $_.FullName.Substring($homeLen).TrimStart('\', '/')
    $segs = $rel -split '[\\/]'
    $skipped = $false
    for ($i = 0; $i -lt $segs.Length - 1; $i++) {
        if ($SkipDirs -contains $segs[$i]) { $skipped = $true; break }
    }
    -not $skipped
}

$rows = foreach ($f in $files) {
    $rel = $f.FullName.Substring($homeLen).TrimStart('\', '/')
    $segs = $rel -split '[\\/]'
    $module = if ($segs.Length -gt 1) { $segs[0] } else { '.' }
    $kind = if ($rel -match 'src[\\/]test[\\/]') { 'test' } else { 'main' }
    $stats = Get-JavaLineStats -Path $f.FullName

    [PSCustomObject]@{
        Module  = $module
        Kind    = $kind
        File    = $rel
        Code    = $stats.Code
        Comment = $stats.Comment
        Blank   = $stats.Blank
    }
}

$rows = @($rows)
if ($rows.Count -eq 0) {
    Write-Warning "未在 $rootFull 下找到 Java 源文件"
    return
}

# ── 按 模块 × main/test 汇总 ────────────────────────────────
$summary = $rows |
    Group-Object Module, Kind |
    ForEach-Object {
        [PSCustomObject]@{
            Module  = $_.Group[0].Module
            Kind    = $_.Group[0].Kind
            Files   = $_.Count
            Code    = ($_.Group | Measure-Object Code -Sum).Sum
            Comment = ($_.Group | Measure-Object Comment -Sum).Sum
            Blank   = ($_.Group | Measure-Object Blank -Sum).Sum
        }
    } | Sort-Object Module, Kind

if ($Detailed) {
    $rows | Sort-Object Module, Kind, File |
        Format-Table Module, Kind, Code, Comment, Blank, File -AutoSize
}

$summary | Format-Table Module, Kind, Files, Code, Comment, Blank -AutoSize

$mainRows = @($rows | Where-Object Kind -eq 'main')
$testRows = @($rows | Where-Object Kind -eq 'test')

foreach ($group in @($mainRows, $testRows)) {
    if ($group.Count -eq 0) { continue }
    $c = ($group | Measure-Object Code -Sum).Sum
    $m = ($group | Measure-Object Comment -Sum).Sum
    $b = ($group | Measure-Object Blank -Sum).Sum
    '{0,-4} files={1,4}  code={2,6}  comment={3,6}  blank={4,6}  total={5,6}' -f `
        $group[0].Kind, $group.Count, $c, $m, $b, ($c + $m + $b)
}

$totalCode = ($rows | Measure-Object Code -Sum).Sum
$totalComment = ($rows | Measure-Object Comment -Sum).Sum
$totalBlank = ($rows | Measure-Object Blank -Sum).Sum
'{0,-4} files={1,4}  code={2,6}  comment={3,6}  blank={4,6}  total={5,6}' -f `
    'ALL', $rows.Count, $totalCode, $totalComment, $totalBlank, ($totalCode + $totalComment + $totalBlank)

'注释率 comment / (code + comment) = {0:N1}%' -f (100 * $totalComment / ($totalCode + $totalComment))

$mainCode = ($mainRows | Measure-Object Code -Sum).Sum
if ($mainCode -gt 0) {
    '测试代码 / 主代码 = {0:N2}' -f (($testRows | Measure-Object Code -Sum).Sum / $mainCode)
}
