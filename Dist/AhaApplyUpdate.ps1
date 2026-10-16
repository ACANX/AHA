#Requires -Version 5.1
<#
.SYNOPSIS
    AHA 换装 helper（Windows 实现，issue #51 §4.5）。

.DESCRIPTION
    App 写好 `update-plan.json`、拉起本脚本、然后自己退出；本脚本等它退出后按计划
    备份 → 替换 → 自证 → 重新拉起 App，并写下 `update-result.json` 供下次启动展示。

    口径（改这里等于改换装契约，必须同步 `Docs/DevSpec/ReleaseProcess.md` 与 Java 侧的
    `SwapPlan` / `UpdatePlan`）：

    * **唯一入参是计划文件路径**，不解析任何用户输入（防注入）；
    * 所有路径都来自计划（绝对路径），脚本不依赖当前工作目录；
    * **只动计划里的文件**：不在计划里的东西一律不碰（`Dist/` 里除了产物还有手写入口
      与用户自己放的东西，整个目录替换会误删）；
    * 替换前先把旧文件搬进备份目录；删除也走备份，所以失败可以直接回滚；
    * **绝不执行包里的任何脚本**：只按计划落文件；
    * 失败也要写回执，并且**仍然尝试拉起旧版本**（不让用户面对一个起不来的目录）；
    * 写日志失败不阻断换装（日志是排障用的，不是换装的前置条件）。

.PARAMETER Plan
    `update-plan.json` 的绝对路径。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File AhaApplyUpdate.ps1 -Plan "$env:USERPROFILE\.aha\update\update-plan.json"

.NOTES
    退出码：0 = 换装成功（或已回滚并拉起旧版本）；1 = 失败。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Plan
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$planFile = (Resolve-Path -LiteralPath $Plan).Path
$root = Split-Path -Parent $planFile
$logFile = Join-Path $root 'update.log'
$resultFile = Join-Path $root 'update-result.json'
# 名字必须与 Java 侧 UpdatePaths.HELPER_STARTED 一致（bin/CheckScripts.py 有守卫钉着）。
$startedFile = Join-Path $root 'helper-started'

# ── 先自报「我起来了」──────────────────────────────────────────────────────
# App 拉起本脚本后会**等这个标记出现**才退出（见 UpdateService.launchHelper）。
# 等不到就说明本脚本根本没跑起来（典型：机器级执行策略拦下 PowerShell）——那时 App
# 留在前台报错，而不是「App 关了、什么都没发生」。
try {
    Set-Content -LiteralPath $startedFile -Value (Get-Date).ToUniversalTime().ToString('o') -Encoding UTF8 -ErrorAction Stop
} catch {
    # 写不进标记不阻断换装：App 会等到超时并报错，这比静默好
}

function Write-UpdateLog {
    param([string]$Message)
    $line = ('{0} [helper] {1}' -f (Get-Date).ToUniversalTime().ToString('o'), $Message)
    try {
        Add-Content -LiteralPath $logFile -Value $line -Encoding UTF8 -ErrorAction Stop
    } catch {
        # best-effort：写不进日志不该阻断换装
    }
}

function Write-Result {
    param([bool]$Ok, [string]$Reason, [bool]$RolledBack, [string]$PreviousVersion)
    $payload = [ordered]@{
        ok              = $Ok
        version         = $Version
        previousVersion = $PreviousVersion
        reason          = $Reason
        rolledBack      = $RolledBack
        at              = (Get-Date).ToUniversalTime().ToString('o')
    }
    try {
        $payload | ConvertTo-Json | Set-Content -LiteralPath $resultFile -Encoding UTF8 -ErrorAction Stop
    } catch {
        Write-UpdateLog ("写入回执失败：" + $_.Exception.Message)
    }
    # 有了回执就不需要「我还活着」的标记了：留着它会让 App 下次启动以为换装还在跑
    # （Java 侧 UpdateService#swapMayBeInFlight 就是看这个标记）。best-effort。
    Remove-Item -LiteralPath $startedFile -Force -ErrorAction SilentlyContinue
}

function Get-Sha256 {
    param([string]$Path)
    # 【为什么不用 Get-FileHash（2026-10-16 真机实测）】
    # helper 是 `apply-update.cmd` 用 `cmd → powershell`（Windows PowerShell 5.1）拉起来的。
    # 如果调用者（App / 自测 / 用户控制台）跑在 pwsh 7 里，环境变量 PSModulePath 就是 pwsh 7 的
    # —— 5.1 子进程加载不到自己的 Utility 模块，Get-FileHash 直接报「不是 cmdlet」：
    # 于是逐文件自证失败 → 回滚（本次真机上就是这么兜住的，但也把一次成功换装变成了失败）。
    # 改用 .NET 直接算：没有模块依赖，也没有自动加载开销。
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try {
            $hash = $sha.ComputeHash($stream)
        } finally {
            $sha.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
    $builder = New-Object System.Text.StringBuilder
    foreach ($byte in $hash) {
        [void]$builder.Append($byte.ToString('x2'))
    }
    return $builder.ToString()
}

# ── 读计划 ──────────────────────────────────────────────────────────────────
$p = Get-Content -LiteralPath $planFile -Raw -Encoding UTF8 | ConvertFrom-Json
$Version = [string]$p.version
$appPid = [int]$p.pid
$installRoot = [string]$p.installRoot
$packageRoot = [string]$p.packageRoot
$backupDir = [string]$p.backupDir
$actions = @($p.actions)
# 没有可用拉起命令时（例如布局里连启动脚本都没有），回执里要如实说，别让用户以为会自动重启
$restartNote = if (@($p.restart).Count -eq 0) { '计划里没有可用的拉起命令，请手动启动应用' } else { '' }
Write-UpdateLog ("开始换装：目标 {0}，安装根 {1}，动作 {2} 项" -f $Version, $installRoot, $actions.Count)

function Invoke-Relaunch {
    if (@($p.restart).Count -eq 0) {
        Write-UpdateLog '计划里没有可用的拉起命令，请手动启动应用'
        return
    }
    foreach ($command in @($p.restart)) {
        if ([string]::IsNullOrWhiteSpace($command)) { continue }
        try {
            Start-Process -FilePath $command -ErrorAction Stop | Out-Null
            Write-UpdateLog ("已拉起：" + $command)
        } catch {
            Write-UpdateLog ("拉起失败（不影响换装结论）：{0} —— {1}" -f $command, $_.Exception.Message)
        }
    }
}

# ── 等 App 退出（它必须退出，否则文件被占用）────────────────────────────────
$deadline = (Get-Date).AddSeconds(60)
while ($true) {
    $running = Get-Process -Id $appPid -ErrorAction SilentlyContinue
    if ($null -eq $running) { break }
    if ((Get-Date) -gt $deadline) {
        Write-UpdateLog ("等待应用退出超时（pid {0}）—— 未做任何改动" -f $appPid)
        Write-Result -Ok $false -Reason ("等待应用退出超时（pid " + $appPid + "）") -RolledBack $false -PreviousVersion ''
        exit 1
    }
    Start-Sleep -Milliseconds 500
}
Write-UpdateLog ("应用已退出（pid {0}）" -f $appPid)

# ── 计划自检：路径必须落在该在的地方 ─────────────────────────────────────────
# helper 只认计划文件（唯一入参），所以**它自己也必须校验**：Java 侧写计划时有
# PathSafety，但 helper 读的是 JSON、不经过 Java（2026-10-16 第二轮审查 P2-⑦）。
#
# 规则只有三条：三个根路径必须绝对；动作路径必须非空、相对、不含 `..`。
# 刻意只用最朴素的判断（不做 GetFullPath / 符号链接解析）：权威校验在 Java 侧，
# 这里要的是「绝不出圈的粗筛」，调用面越小越不会因平台差异出岔子。
# 位置放在「等 App 退出」之后：拒绝的姿势与其它拒绝（缺清单 / 版本不符）完全一致 ——
# 写回执 + 拉起旧版本，不会把用户留在一个起不来的目录前。
$pathsOk = $true
if (-not [System.IO.Path]::IsPathRooted($installRoot) -or
    -not [System.IO.Path]::IsPathRooted($packageRoot) -or
    -not [System.IO.Path]::IsPathRooted($backupDir)) {
    $pathsOk = $false
    Write-UpdateLog ("计划里的根路径不全是绝对路径：" + $installRoot + " / " + $packageRoot + " / " + $backupDir)
}
foreach ($action in $actions) {
    $relative = [string]$action.path
    if ([string]::IsNullOrWhiteSpace($relative) -or
        [System.IO.Path]::IsPathRooted($relative) -or
        $relative.Contains('..')) {
        $pathsOk = $false
        Write-UpdateLog ("计划里的动作路径不合法（逃出安装根）：" + $relative)
        break
    }
}
if (-not $pathsOk) {
    Write-Result -Ok $false -Reason '计划里的路径不合法（逃出安装根或不是绝对路径）——未做任何改动' -RolledBack $false -PreviousVersion ''
    Invoke-Relaunch
    exit 1
}

# ── 包自证：清单版本必须与计划一致（挂错包 / 版本对不上就此打住）─────────────
$manifestPath = Join-Path $packageRoot 'AHA-PACKAGE.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    Write-UpdateLog ("新包里没有 AHA-PACKAGE.json：" + $manifestPath)
    Write-Result -Ok $false -Reason '新包里没有 AHA-PACKAGE.json（该包不合格）' -RolledBack $false -PreviousVersion ''
    Invoke-Relaunch
    exit 1
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ([string]$manifest.version -ne $Version) {
    Write-UpdateLog ("清单版本与计划不一致：清单 {0}，计划 {1}" -f $manifest.version, $Version)
    Write-Result -Ok $false -Reason ("清单版本与计划不一致：清单 " + $manifest.version) -RolledBack $false -PreviousVersion ''
    Invoke-Relaunch
    exit 1
}
# 旧版本从换装前的已装清单里读（新包清单不带这个字段；老版本可能根本没有清单）
$previousVersion = ''
$installedManifest = Join-Path $installRoot 'AHA-PACKAGE.json'
if (Test-Path -LiteralPath $installedManifest -PathType Leaf) {
    try {
        $previousVersion = [string]((Get-Content -LiteralPath $installedManifest -Raw -Encoding UTF8 | ConvertFrom-Json).version)
    } catch {
        Write-UpdateLog ("读已装清单失败（旧版本号留空）：" + $_.Exception.Message)
    }
}

# ── 换装（失败即回滚）──────────────────────────────────────────────────────
$copied = New-Object System.Collections.ArrayList
try {
    foreach ($action in $actions) {
        $relative = [string]$action.path
        $target = Join-Path $installRoot $relative
        $backup = Join-Path $backupDir $relative
        $kind = ([string]$action.kind).ToLowerInvariant()

        if (Test-Path -LiteralPath $target -PathType Leaf) {
            $backupParent = Split-Path -Parent $backup
            if (-not (Test-Path -LiteralPath $backupParent)) {
                New-Item -ItemType Directory -Path $backupParent -Force | Out-Null
            }
            Copy-Item -LiteralPath $target -Destination $backup -Force
        }

        if ($kind -eq 'delete') {
            if (Test-Path -LiteralPath $target -PathType Leaf) {
                Remove-Item -LiteralPath $target -Force
                Write-UpdateLog ("已删除（备份在 {0}）：{1}" -f $backup, $relative)
            }
            continue
        }

        $source = Join-Path $packageRoot $relative
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            throw ("新包里没有这个文件（清单与包不一致）：" + $relative)
        }
        $targetParent = Split-Path -Parent $target
        if (-not (Test-Path -LiteralPath $targetParent)) {
            New-Item -ItemType Directory -Path $targetParent -Force | Out-Null
        }
        Copy-Item -LiteralPath $source -Destination $target -Force
        [void]$copied.Add($relative)
        Write-UpdateLog ("已替换：" + $relative)
    }
} catch {
    $reason = $_.Exception.Message
    Write-UpdateLog ("换装失败：" + $reason + " —— 开始回滚")
    $rolled = $true
    try {
        foreach ($action in $actions) {
            $relative = [string]$action.path
            $target = Join-Path $installRoot $relative
            $backup = Join-Path $backupDir $relative
            if (Test-Path -LiteralPath $backup -PathType Leaf) {
                Copy-Item -LiteralPath $backup -Destination $target -Force
            } elseif ($copied.Contains($relative)) {
                # 备份里没有 ⇒ 这个文件是本次新增的，回滚就等于删掉它
                Remove-Item -LiteralPath $target -Force -ErrorAction SilentlyContinue
            }
        }
    } catch {
        $rolled = $false
        Write-UpdateLog ("回滚也失败了：" + $_.Exception.Message)
    }
    Write-UpdateLog ("回滚结论：" + $(if ($rolled) { '成功' } else { '失败' }))
    Write-Result -Ok $false -Reason $(if ($restartNote) { $reason + '；' + $restartNote } else { $reason }) -RolledBack $rolled -PreviousVersion $previousVersion
    # 失败也要尽量把旧版本拉起来，而不是留一个起不来的目录
    Invoke-Relaunch
    exit 1
}

# ── 自证：落地的每个文件都必须与新包清单里的哈希一致 ─────────────────────────
# 注意：本分支的回滚**必须**自己也包 try/catch。主流程的回滚包了（见上），这里是第二次
# 机会——回滚里 Copy-Item 一旦抛（文件被占），脚本会整个崩掉：不写回执、不重启，
# 用户面对一个起不来的目录。2026-10-16 审查发现并修正。
try {
    $entries = @{}
    foreach ($entry in @($manifest.files)) { $entries[[string]$entry.path] = [string]$entry.sha256 }
    foreach ($relative in $copied) {
        $target = Join-Path $installRoot $relative
        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
            throw ("自证失败：文件不存在 " + $relative)
        }
        if ($entries.ContainsKey($relative)) {
            $actual = Get-Sha256 -Path $target
            if ($actual -ne $entries[$relative].ToLowerInvariant()) {
                throw ("自证失败：{0} 的哈希与清单不一致（清单 {1}，实际 {2}）" -f $relative, $entries[$relative], $actual)
            }
        }
    }
} catch {
    $reason = $_.Exception.Message
    Write-UpdateLog ("自证失败：" + $reason + " —— 开始回滚")
    $rolled = $true
    try {
        foreach ($action in $actions) {
            $relative = [string]$action.path
            $target = Join-Path $installRoot $relative
            $backup = Join-Path $backupDir $relative
            if (Test-Path -LiteralPath $backup -PathType Leaf) {
                Copy-Item -LiteralPath $backup -Destination $target -Force
            } elseif ($copied.Contains($relative)) {
                Remove-Item -LiteralPath $target -Force -ErrorAction SilentlyContinue
            }
        }
    } catch {
        $rolled = $false
        Write-UpdateLog ("回滚也失败了：" + $_.Exception.Message)
    }
    Write-UpdateLog ("回滚结论：" + $(if ($rolled) { '成功' } else { '失败' }))
    Write-Result -Ok $false -Reason $reason -RolledBack $rolled -PreviousVersion $previousVersion
    Invoke-Relaunch
    exit 1
}

Write-UpdateLog ("换装成功：{0}" -f $Version)
Write-Result -Ok $true -Reason $restartNote -RolledBack $false -PreviousVersion $previousVersion
Invoke-Relaunch

# ── 自删（best-effort）──────────────────────────────────────────────────────
# 自己删自己；一键入口 `apply-update.cmd` 是正在运行的批处理，删不掉，由 App 下次
# 启动的收尾一并删（见 UpdateService#onStartup）。删不掉只是留下两个小文件，不影响结论。
try {
    Remove-Item -LiteralPath $MyInvocation.MyCommand.Path -Force -ErrorAction SilentlyContinue
} catch {
    Write-UpdateLog ("helper 自删失败（无碍）：" + $_.Exception.Message)
}
exit 0
