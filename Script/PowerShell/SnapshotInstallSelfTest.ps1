#Requires -Version 5.1
<#
.SYNOPSIS
    「把解压出来的包铺到落地根」的自测（issue #244，`Dist/UpdateInstall.ps1`）。

.DESCRIPTION
    为什么要有它：`Install-JvmPackage` 会**动用户 `Dist\` 里的文件**，而快照取件此前根本没走到这一步
    —— 真机上就是这么发现的：双击 `Dist\UpdateDesktopJvmSnapshot.cmd`，包下下来了、也解开了，
    但 `aha-desktop.jar` 与启动脚本躺在 `Dist\<资产名>\` 子目录里，`Dist\` 根下什么都没有，
    还得手工搬一遍。这段逻辑此后由本脚本在**临时目录**里离线真跑（不联网、不碰仓库里的任何文件）。

    覆盖：
      1. 平铺到空的落地根：一级文件 + 子目录都到位；
      2. 覆盖既有文件：先备份成 `<名字>.bak`（旧内容留在 .bak 里）；
      3. 连续跑两次：.bak 是**上一版**（不是更早的），不会越铺越乱；
      4. 空包目录：不报错，且不动落地根里已有的东西；
      5. 目标被独占占用：**落地根一个字节都不改**（先探测再动手）—— 独占打开的语义只在
         Windows 成立，Linux 上同一条用例会跳过（那台机器上第二次打开照样成功）。

    不覆盖（需要网络 / 真机，别把这里当成「全链路已验证」）：
      下载、sha256 校验、解压、状态文件 `.aha-snapshot.json`、清理暂存目录、
      `-NoInstall` / `-KeepExtract` 的取件流程、GitHub 发布页交互。

.NOTES
    退出码：0 = 全部通过；1 = 有失败。
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$library = Join-Path $repoRoot 'Dist/UpdateInstall.ps1'
if (-not (Test-Path -LiteralPath $library)) {
    Write-Host ("[FAIL] 找不到被自测的库：{0}" -f $library) -ForegroundColor Red
    exit 1
}
# 自测只驱动**函数**，所以点源它不会触发任何下载 / 解压
. $library

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param([string]$What, $Expected, $Actual)
    if ("$Expected" -eq "$Actual") {
        $script:passed++
        Write-Host ("  [OK]   {0}" -f $What) -ForegroundColor Green
    } else {
        $script:failed++
        Write-Host ("  [FAIL] {0}：期望「{1}」，实际「{2}」" -f $What, $Expected, $Actual) -ForegroundColor Red
    }
}

function Assert-True {
    param([string]$What, $Condition)
    Assert-Equal -What $What -Expected 'True' -Actual ([bool]$Condition)
}

function New-FakePackage {
    # 造一个「包根平铺」形态的 JVM 便携包：jar + 启动脚本 + 清单，外加一个子目录
    param([string]$Dir)
    New-Item -ItemType Directory -Path (Join-Path $Dir 'lib') -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $Dir 'aha-desktop.jar'), 'new-jar')
    [IO.File]::WriteAllText((Join-Path $Dir 'AhaDesktop.bat'), 'new-bat')
    [IO.File]::WriteAllText((Join-Path $Dir 'AhaDesktop.sh'), 'new-sh')
    [IO.File]::WriteAllText((Join-Path $Dir 'lib/dep.jar'), 'new-dep')
}

function New-TempRoot {
    $dir = Join-Path ([IO.Path]::GetTempPath()) ("aha-snapshot-install-{0}" -f ([guid]::NewGuid().ToString('N')))
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    return $dir
}

Write-Host '快照取件 · 「平铺到落地根」自测（离线；只在临时目录里跑）'

# ── 用例 1：平铺到空的落地根 ───────────────────────────────────────────────
$root = New-TempRoot
try {
    $pkg = Join-Path $root 'package'
    $out = Join-Path $root 'dist'
    New-FakePackage -Dir $pkg
    New-Item -ItemType Directory -Path $out -Force | Out-Null

    Write-Host ''
    Write-Host '用例 1：平铺到空的落地根'
    $res = Install-JvmPackage -ExtractDir $pkg -OutDir $out
    Assert-True  -What '整体成功' -Condition $res.ok
    Assert-Equal -What '铺过去的项数' -Expected 4 -Actual $res.installed.Count
    Assert-Equal -What 'jar 内容' -Expected 'new-jar' -Actual ([IO.File]::ReadAllText((Join-Path $out 'aha-desktop.jar')))
    Assert-Equal -What '启动脚本（.bat）内容' -Expected 'new-bat' -Actual ([IO.File]::ReadAllText((Join-Path $out 'AhaDesktop.bat')))
    Assert-Equal -What '启动脚本（.sh）内容' -Expected 'new-sh' -Actual ([IO.File]::ReadAllText((Join-Path $out 'AhaDesktop.sh')))
    Assert-Equal -What '子目录里的依赖' -Expected 'new-dep' -Actual ([IO.File]::ReadAllText((Join-Path $out 'lib/dep.jar')))
    Assert-Equal -What '没有凭空造出 .bak' -Expected 0 -Actual @(Get-ChildItem -LiteralPath $out -Filter '*.bak').Count
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

# ── 用例 2：覆盖既有文件要先备份 ────────────────────────────────────────────
$root = New-TempRoot
try {
    $pkg = Join-Path $root 'package'
    $out = Join-Path $root 'dist'
    New-FakePackage -Dir $pkg
    New-Item -ItemType Directory -Path $out -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $out 'aha-desktop.jar'), 'old-jar')
    [IO.File]::WriteAllText((Join-Path $out 'AhaDesktop.bat'), 'old-bat')

    Write-Host ''
    Write-Host '用例 2：覆盖既有文件（旧版必须留下 .bak）'
    $res = Install-JvmPackage -ExtractDir $pkg -OutDir $out
    Assert-True  -What '整体成功' -Condition $res.ok
    Assert-Equal -What '被备份的名字数' -Expected 2 -Actual $res.backedUp.Count
    Assert-Equal -What '根上已是新版 jar' -Expected 'new-jar' -Actual ([IO.File]::ReadAllText((Join-Path $out 'aha-desktop.jar')))
    Assert-Equal -What '旧 jar 留在 .bak' -Expected 'old-jar' -Actual ([IO.File]::ReadAllText((Join-Path $out 'aha-desktop.jar.bak')))
    Assert-Equal -What '旧脚本留在 .bak' -Expected 'old-bat' -Actual ([IO.File]::ReadAllText((Join-Path $out 'AhaDesktop.bat.bak')))
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

# ── 用例 3：连续跑两次（.bak 应停在「上一版」）──────────────────────────────
$root = New-TempRoot
try {
    $pkg = Join-Path $root 'package'
    $out = Join-Path $root 'dist'
    New-FakePackage -Dir $pkg
    New-Item -ItemType Directory -Path $out -Force | Out-Null

    Write-Host ''
    Write-Host '用例 3：同一版连续铺两次'
    $first = Install-JvmPackage -ExtractDir $pkg -OutDir $out
    [IO.File]::WriteAllText((Join-Path $pkg 'aha-desktop.jar'), 'second-jar')
    $second = Install-JvmPackage -ExtractDir $pkg -OutDir $out
    Assert-True  -What '两次都成功' -Condition ($first.ok -and $second.ok)
    Assert-Equal -What '根上是最新内容' -Expected 'second-jar' -Actual ([IO.File]::ReadAllText((Join-Path $out 'aha-desktop.jar')))
    Assert-Equal -What '.bak 是上一版（不是首版）' -Expected 'new-jar' -Actual ([IO.File]::ReadAllText((Join-Path $out 'aha-desktop.jar.bak')))
    Assert-Equal -What '落地根里没有多余的 .bak.bak' -Expected 0 -Actual @(Get-ChildItem -LiteralPath $out -Filter '*.bak.bak').Count
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

# ── 用例 4：空包目录不动既有文件 ───────────────────────────────────────────
$root = New-TempRoot
try {
    $pkg = Join-Path $root 'package'
    $out = Join-Path $root 'dist'
    New-Item -ItemType Directory -Path $pkg -Force | Out-Null
    New-Item -ItemType Directory -Path $out -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $out 'aha-desktop.jar'), 'untouched')

    Write-Host ''
    Write-Host '用例 4：空包目录（不该报错，也不该动落地根）'
    $res = Install-JvmPackage -ExtractDir $pkg -OutDir $out
    Assert-True  -What '不报错' -Condition $res.ok
    Assert-Equal -What '没铺任何项' -Expected 0 -Actual $res.installed.Count
    Assert-Equal -What '落地根原样' -Expected 'untouched' -Actual ([IO.File]::ReadAllText((Join-Path $out 'aha-desktop.jar')))
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

# ── 用例 5：目标被占用 ⇒ 落地根一个字节都不改（Windows 独占语义）────────────
if ($env:OS -ne 'Windows_NT') {
    Write-Host ''
    Write-Host '用例 5：跳过 —— 独占打开只在 Windows 上会失败（Linux 上第二次打开照样成功），' -ForegroundColor Yellow
    Write-Host '        这条要在 Windows 腿（Build.yml 的 windows-latest）上才算数。'
    $script:passed++
} else {
    $root = New-TempRoot
    try {
        $pkg = Join-Path $root 'package'
        $out = Join-Path $root 'dist'
        New-FakePackage -Dir $pkg
        New-Item -ItemType Directory -Path $out -Force | Out-Null
        [IO.File]::WriteAllText((Join-Path $out 'aha-desktop.jar'), 'running-jar')
        [IO.File]::WriteAllText((Join-Path $out 'AhaDesktop.bat'), 'untouched-bat')

        Write-Host ''
        Write-Host '用例 5：jar 被独占占用（模拟「有实例正在跑」）'
        $handle = [IO.File]::Open((Join-Path $out 'aha-desktop.jar'), 'Open', 'ReadWrite', 'None')
        try {
            $res = Install-JvmPackage -ExtractDir $pkg -OutDir $out
        } finally {
            $handle.Close()
        }
        Assert-Equal -What '整体判为失败' -Expected 'False' -Actual ([bool]$res.ok)
        Assert-True  -What '给出失败原因' -Condition ($res.failed.Count -ge 1)
        Assert-Equal -What '被占用的 jar 没被改' -Expected 'running-jar' -Actual ([IO.File]::ReadAllText((Join-Path $out 'aha-desktop.jar')))
        Assert-Equal -What '同批的脚本也没被铺过去（不是做一半）' -Expected 'untouched-bat' -Actual ([IO.File]::ReadAllText((Join-Path $out 'AhaDesktop.bat')))
        Assert-Equal -What '没留下 .bak' -Expected 0 -Actual @(Get-ChildItem -LiteralPath $out -Filter '*.bak').Count
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ''
if ($script:failed -eq 0) {
    Write-Host ("✅ 快照平铺自测全部通过（{0} 项断言）" -f $script:passed) -ForegroundColor Green
    exit 0
}
Write-Host ("❌ 快照平铺自测失败：{0} 项断言未通过（通过 {1} 项）" -f $script:failed, $script:passed) -ForegroundColor Red
exit 1
