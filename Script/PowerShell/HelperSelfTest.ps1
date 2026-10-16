#Requires -Version 5.1
<#
.SYNOPSIS
    换装 helper（`Dist/AhaApplyUpdate.ps1`）的自测：在临时目录里真跑一遍。

.DESCRIPTION
    为什么要这个自测（2026-10-16 代码审查 P0/P1）：

      * helper 是整个升级链路里**唯一直接动用户文件**的东西，此前却只有「写没写出来」
        这种静态检查——真正的替换 / 备份 / 自证 / 回执逻辑，本地和 CI 都没跑过；
      * 「helper 到底起没起来」曾经无人知晓：App 先退出、helper 才干活，helper 若被
        安全策略拦下，表现就是「App 关了但什么都没发生」。现在 App 等
        `helper-started` 标记才退出，所以这个标记必须被真实验证。

    两个用例，都在临时目录里做，**不碰真实安装**：

      1. 失败路径：新包缺 `AHA-PACKAGE.json` ⇒ 写回执 ok=false、写「已启动」标记、退出码 1；
      2. 成功路径：清单齐、哈希对 ⇒ 备份旧文件 → 落新文件 → 自证通过 → 回执 ok=true、
         退出码 0，且备份里能找回旧内容（可回滚）。

    范围说明：本自测跑在 pwsh（CI 的 ubuntu / 本机 POSIX）上，验的是**计划语义**；
    「Windows 上被占用的 exe 能否替换」「cmd /c start 拉起的 helper 会不会随 App 退出」
    这类平台行为仍属真实机验证项（见 `Docs/Design/DesktopAutoUpdateDesign.md` §8.2）。

.EXAMPLE
    pwsh -NoProfile -File Script/PowerShell/HelperSelfTest.ps1
#>
[CmdletBinding()]
param(
    # helper 脚本路径（默认取仓库里的 Dist/AhaApplyUpdate.ps1）
    [string]$Helper = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $scriptDir '..' '..')).Path
if (-not $Helper) {
    $Helper = Join-Path $repoRoot 'Dist/AhaApplyUpdate.ps1'
}
$Helper = (Resolve-Path -LiteralPath $Helper).Path

$failures = 0

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if ($Condition) {
        Write-Host "  [PASS] $Message"
    } else {
        Write-Host "  [FAIL] $Message"
        $script:failures++
    }
}

function Get-Sha256 {
    param([string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function New-Scenario {
    # 造一个「已装目录 + 新包目录 + 备份目录」，返回三者的路径与文件哈希
    param(
        [string]$Name,
        [bool]$WithManifest
    )
    $base = Join-Path ([System.IO.Path]::GetTempPath()) ("aha-helper-selftest-" + [guid]::NewGuid().ToString('N'))
    $installRoot = Join-Path $base 'install'
    $packageRoot = Join-Path $base ('pkg-' + $Name)
    $backupDir = Join-Path $base 'backup'
    $updateRoot = Join-Path $base 'update'
    New-Item -ItemType Directory -Path $installRoot, $packageRoot, $updateRoot -Force | Out-Null

    # 已装：一个 exe + 一个将被删除的旧文件
    Set-Content -LiteralPath (Join-Path $installRoot 'aha-desktop-native') -Value 'OLD-EXE' -NoNewline
    Set-Content -LiteralPath (Join-Path $installRoot 'old-file.txt') -Value 'REMOVE-ME' -NoNewline
    # 新包：新 exe（且不含 old-file.txt）
    Set-Content -LiteralPath (Join-Path $packageRoot 'aha-desktop-native') -Value 'NEW-EXE' -NoNewline
    $exeHash = Get-Sha256 -Path (Join-Path $packageRoot 'aha-desktop-native')

    if ($WithManifest) {
        # 清单里的 sha256 必须是真的——helper 会逐项自证
        $manifest = [ordered]@{
            kind     = 'desktop-native'
            version  = '0.1.3.00243'
            platform = 'windows-amd64'
            jdk      = 25
            files    = @(
                [ordered]@{ path = 'aha-desktop-native'; sha256 = $exeHash; size = 7 }
            )
        }
        ($manifest | ConvertTo-Json -Depth 6) |
            Set-Content -LiteralPath (Join-Path $packageRoot 'AHA-PACKAGE.json') -Encoding UTF8
    }

    # 计划：pid 用一个**不存在**的进程号，等待循环会立刻通过
    $plan = [ordered]@{
        version      = '0.1.3.00243'
        installRoot  = $installRoot
        packageRoot  = $packageRoot
        backupDir    = $backupDir
        pid          = 2147483000
        restart      = @()
        actions      = @(
            [ordered]@{ path = 'aha-desktop-native'; kind = 'replace' },
            [ordered]@{ path = 'old-file.txt'; kind = 'delete' }
        )
        createdAt    = (Get-Date).ToUniversalTime().ToString('o')
    }
    $planFile = Join-Path $updateRoot 'update-plan.json'
    ($plan | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $planFile -Encoding UTF8

    return [pscustomobject]@{
        Base        = $base
        InstallRoot = $installRoot
        PackageRoot = $packageRoot
        BackupDir   = $backupDir
        PlanFile    = $planFile
        UpdateRoot  = $updateRoot
        ExeHash     = $exeHash
    }
}

#: 用**子进程**跑 helper：① 与真实拉起方式一致（helper 本来就是独立进程）；
#: ② helper 里的 `exit` / 终止性错误不会影响本自测的进程与退出码。
$pwshExe = Join-Path $PSHOME 'pwsh'
if (-not (Test-Path -LiteralPath $pwshExe)) { $pwshExe = Join-Path $PSHOME 'pwsh.exe' }
if (-not (Test-Path -LiteralPath $pwshExe)) { $pwshExe = '' }

function Invoke-Helper {
    param([string]$PlanFile)
    if (-not $pwshExe) {
        throw "找不到 pwsh 可执行文件（PSHOME=$PSHOME），无法在子进程里跑换装 helper"
    }
    # 子进程的输出**不能吞掉**：helper 拒绍或崩掉时，那几行就是唯一的线索
    # （2026-10-16：用例 3 在 CI 上只看到「退出码 64」，因为输出被 *> $null 吞了）。
    $output = & $pwshExe -NoProfile -NonInteractive -File $Helper -Plan $PlanFile 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        Write-Host "  [helper 输出] 退出码 $exitCode"
        foreach ($line in @($output)) {
            Write-Host ("    " + [string]$line)
        }
    }
    return $exitCode
}

Write-Host "换装 helper 自测：$Helper"

# ── 用例 1：失败路径（新包不合格）──────────────────────────────────────────
Write-Host ''
Write-Host '用例 1：新包缺 AHA-PACKAGE.json ⇒ 拒绝换装、留回执、写「已启动」标记'
$bad = New-Scenario -Name 'bad' -WithManifest $false
$code = Invoke-Helper -PlanFile $bad.PlanFile
Assert-True ($code -eq 1) "退出码为 1（实际 $code）"
# 标记是「我还活着」用的：有了回执就不需要它了（否则 App 下次启动会以为换装还在跑）。
# 「标记是在做任何慢活之前写的」这条握手契约由用例 5 专门验。
Assert-True (-not (Test-Path -LiteralPath (Join-Path $bad.UpdateRoot 'helper-started'))) `
    '写了回执后把「已启动」标记清掉（否则下次启动会以为换装还在跑）'
$resultPath = Join-Path $bad.UpdateRoot 'update-result.json'
Assert-True (Test-Path -LiteralPath $resultPath) '写了回执 update-result.json'
if (Test-Path -LiteralPath $resultPath) {
    $result = Get-Content -LiteralPath $resultPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-True (-not [bool]$result.ok) '回执 ok=false'
    Assert-True ([string]$result.reason -like '*AHA-PACKAGE.json*') '回执说明了原因（缺 AHA-PACKAGE.json）'
}
Assert-True ((Get-Content -LiteralPath (Join-Path $bad.InstallRoot 'aha-desktop-native') -Raw) -eq 'OLD-EXE') `
    '拒绝换装时安装目录里的文件一个都没动'
Remove-Item -LiteralPath $bad.Base -Recurse -Force -ErrorAction SilentlyContinue

# ── 用例 2：成功路径（清单齐、哈希对）──────────────────────────────────────
Write-Host ''
Write-Host '用例 2：清单齐、哈希对 ⇒ 替换 + 删除 + 自证 + 回执'
$good = New-Scenario -Name 'good' -WithManifest $true
$code = Invoke-Helper -PlanFile $good.PlanFile
Assert-True ($code -eq 0) "退出码为 0（实际 $code）"
$resultPath = Join-Path $good.UpdateRoot 'update-result.json'
Assert-True (Test-Path -LiteralPath $resultPath) '写了回执 update-result.json'
if (Test-Path -LiteralPath $resultPath) {
    $result = Get-Content -LiteralPath $resultPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-True ([bool]$result.ok) '回执 ok=true'
    Assert-True ([string]$result.version -eq '0.1.3.00243') '回执里的目标版本正确'
}
Assert-True ((Get-Content -LiteralPath (Join-Path $good.InstallRoot 'aha-desktop-native') -Raw) -eq 'NEW-EXE') `
    '新文件已落地'
Assert-True ((Get-Sha256 -Path (Join-Path $good.InstallRoot 'aha-desktop-native')) -eq $good.ExeHash) `
    '落地文件哈希与清单一致（自证通过）'
Assert-True (-not (Test-Path -LiteralPath (Join-Path $good.InstallRoot 'old-file.txt'))) `
    '计划里的 delete 动作真的删掉了旧文件'
Assert-True ((Get-Content -LiteralPath (Join-Path $good.BackupDir 'aha-desktop-native') -Raw) -eq 'OLD-EXE') `
    '备份里能找回旧 exe（可回滚）'
Assert-True ((Get-Content -LiteralPath (Join-Path $good.BackupDir 'old-file.txt') -Raw) -eq 'REMOVE-ME') `
    '被删的文件也在备份里（删除走备份，不直接丢）'
Remove-Item -LiteralPath $good.Base -Recurse -Force -ErrorAction SilentlyContinue

# ── 用例 3：计划里的动作路径逃出安装根 ⇒ 拒绝 ───────────────────────────────
# 为什么 helper 要自己校验：Java 侧写计划时有 PathSafety，但 helper 读的是 JSON、
# 不经过 Java；把校验放在两侧才叫「防注入」（2026-10-16 第二轮审查 P2-⑦）。
Write-Host ''
Write-Host '用例 3：动作路径逃出安装根 ⇒ 拒绝换装、留回执、退出码 1'
$escape = New-Scenario -Name 'escape' -WithManifest $true
$escapePlan = Get-Content -LiteralPath $escape.PlanFile -Raw -Encoding UTF8 | ConvertFrom-Json
$escapePlan.actions[0].path = '../escape.txt'
$escapePlan.actions[1].path = '../escape2.txt'
($escapePlan | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $escape.PlanFile -Encoding UTF8
$code = Invoke-Helper -PlanFile $escape.PlanFile
Assert-True ($code -eq 1) "退出码为 1（实际 $code）"
$resultPath = Join-Path $escape.UpdateRoot 'update-result.json'
Assert-True (Test-Path -LiteralPath $resultPath) '写了回执 update-result.json'
if (Test-Path -LiteralPath $resultPath) {
    $result = Get-Content -LiteralPath $resultPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-True (-not [bool]$result.ok) '回执 ok=false'
    Assert-True ([string]$result.reason -like '*不合法*') '回执说明了原因（路径不合法）'
}
Assert-True ((Get-Content -LiteralPath (Join-Path $escape.InstallRoot 'aha-desktop-native') -Raw) -eq 'OLD-EXE') `
    '拒绝换装时安装目录没被动过'
Assert-True (-not (Test-Path -LiteralPath (Join-Path $escape.Base 'escape.txt'))) `
    '安装根外面没有被写入文件'
Remove-Item -LiteralPath $escape.Base -Recurse -Force -ErrorAction SilentlyContinue

# ── 用例 4（仅 Windows）：经「一键入口」apply-update.cmd 真跑一遍 ─────────────
# 为什么单列：一键入口是 P0-1 的主角（曾经根本没进仓库），而 native-update-script
# 作业跑在 ubuntu 的 pwsh 上 —— `%~dp0` / `%~1` / 退出码传递从来没被执行过
# （2026-10-16 第二轮审查 P2-③）。
if ($env:OS -eq 'Windows_NT') {
    Write-Host ''
    Write-Host '用例 4：经 Dist/apply-update.cmd（一键入口）换装'
    $entry = Join-Path $repoRoot 'Dist/apply-update.cmd'
    $viaCmd = New-Scenario -Name 'entry' -WithManifest $true
    $entryCommand = '"' + $entry + '" "' + $viaCmd.PlanFile + '"'
    cmd.exe /c $entryCommand | Out-Null
    $code = $LASTEXITCODE
    Assert-True ($code -eq 0) "一键入口退出码为 0（实际 $code）"
    $resultPath = Join-Path $viaCmd.UpdateRoot 'update-result.json'
    Assert-True (Test-Path -LiteralPath $resultPath) '一键入口也写了回执'
    if (Test-Path -LiteralPath $resultPath) {
        $result = Get-Content -LiteralPath $resultPath -Raw -Encoding UTF8 | ConvertFrom-Json
        Assert-True ([bool]$result.ok) '回执 ok=true'
    }
    Assert-True ((Get-Content -LiteralPath (Join-Path $viaCmd.InstallRoot 'aha-desktop-native') -Raw) -eq 'NEW-EXE') `
        '一键入口真的把文件换了'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $viaCmd.UpdateRoot 'helper-started'))) `
        '一键入口跑完后「已启动」标记被清掉（否则 App 下次会以为换装还在跑）'
    Remove-Item -LiteralPath $viaCmd.Base -Recurse -Force -ErrorAction SilentlyContinue
} else {
    Write-Host ''
    Write-Host '用例 4 跳过：一键入口 apply-update.cmd 只在 Windows 上跑（本机不是 Windows）'
}

# ── 用例 5：计划文件坏了 ⇒ 「已启动」标记已经写下、但写不出回执 ───────────────
# 它验的是握手契约里最关键的一点：**标记是在做任何慢活之前写的**（App 等它出现才退出）。
# 计划坏掉时 helper 还没写回执就退出了，标记会留下 —— 这正是 Java 侧
# `UpdateService#swapMayBeInFlight` 那个 10 分钟窗口存在的理由（超时后按残留清掉）。
# 权威校验在 Java 侧（计划是它写的），这里只是把已有行为钉住。
Write-Host ''
Write-Host '用例 5：计划不是合法 JSON ⇒ 标记已写、无回执、退出码非 0'
$broken = New-Scenario -Name 'broken' -WithManifest $true
Set-Content -LiteralPath $broken.PlanFile -Value '{ 这不是 JSON' -Encoding UTF8
$code = Invoke-Helper -PlanFile $broken.PlanFile
Assert-True ($code -ne 0) "退出码非 0（实际 $code）"
Assert-True (Test-Path -LiteralPath (Join-Path $broken.UpdateRoot 'helper-started')) `
    '标记是在解析计划**之前**写的（App 靠它判断 helper 是否真的起来了）'
Assert-True (-not (Test-Path -LiteralPath (Join-Path $broken.UpdateRoot 'update-result.json'))) `
    '计划坏掉时写不出回执（这正是「启动时按残留清理」那条分支的来由）'
Remove-Item -LiteralPath $broken.Base -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ''
if ($failures -gt 0) {
    Write-Host "❌ 换装 helper 自测失败：$failures 项"
    exit 1
}
Write-Host '✅ 换装 helper 自测通过（失败路径 + 成功路径 + 标记 + 备份）'
exit 0
