<#
    AHA 快照包一键取件（Windows 原生实现，由 Dist 下的**专用启动器**调用）
        Dist\UpdateDesktopNativeSnapshot.cmd   -> -Kind desktop-native
        Dist\UpdateCliNativeSnapshot.cmd       -> -Kind cli-native
        Dist\UpdateDesktopJvmSnapshot.cmd      -> -Kind desktop-jvm
        Dist\UpdateCliJvmSnapshot.cmd          -> -Kind cli-jvm
    不带薄壳时也可直调：UpdateSnapshot.ps1 -Kind jvm -Pr <N>

    快照包 = 某个**未合并 PR** 的试包（issue #165 方案）。它挂在发布页的 pre-release 上：
        tag 形如  V0.1.3.00183.10141829-AHA-Desktop-Native（旧 tag 的 -SNAPSHOT 后缀仍兼容）
    本脚本做四件事：
      1. 查发布页，找出快照（可按 `-Pr` / `-Branch` / `-Run` / `-Kind` 过滤）；
      2. 挑出**本机平台**该取哪个资产（JVM 线与原生线自 2026-10-15 起**共用同一套**：`windows-amd64` / `ubuntu-amd64`）；
      3. 下载 zip 到 `Dist\`（已有同尺寸的包则复用；带 sha256 则校验）；
      4. 解压到**按资产命名的目录**里，再按种类**落成「取完就能就地启动」的形态**（issue #244）：
         · JVM 便携包：把包根的一级内容**平铺到 `-Out`**（默认 = 启动器所在目录，即 `Dist\`）——
           JVM 包自 issue #218 起就是包根平铺，解压目录本身就是一个能直接启动的包；
           只解压到子目录的话「取件」只完成一半（与 `UpdateJvm.ps1` 同一口径）；
         · 本机平台的原生包：只把 exe（含同目录的 dll）**替换到 `-Out`**，旧版留 `<名字>.exe.bak`；
         两种都**先把被覆盖的既有文件备份成 `<名字>.bak`**，铺好之后**把那份解压目录清掉**
         （东西已在落地根、zip 留着、旧版有 `.bak`，留着只是又一份副本；`-KeepExtract` 可保留）。
         `-NoInstall` 维持「只取件」：只解压到 `Dist\<资产名>\`，不平铺、不动任何既有文件。

    为什么和 `UpdateNative.ps1` 分开、且不复用它：

      * 语义不同：`UpdateNative.ps1` 走 dev 线、按「最新一版」替换；
        快照是**并排试包**——总是先落成独立目录，再按种类落地：JVM 包**平铺到落地根**、
        本机平台的原生包**替换 exe**（这样「取件后双击 `Dist\AhaDesktop.bat` /
        `Dist\aha-desktop-native.exe` 就是刚取的那版」，issue #244）。
        除了平铺 / 替换会覆盖到的那些名字（与它们的 `.bak`）之外不碰任何既有文件；
      * 状态不同：它用 `.aha-desktop-native.json` / `.aha-cli-native.json`（「已装哪一版」），
        本脚本用 `.aha-snapshot.json`（「取过哪一次快照」）。两者互不读写，
        因此**不会**把「已装版本」记错（issue #173 的隔离性要求）。

    零配置：不需要 `GH_TOKEN`、不需要 `gh`、不需要 Python —— 全程走 PowerShell / .NET（Schannel TLS）。
    与 Python 侧的对应实现：快照命名（tag / 资产前缀）的唯一来源是
    `.github/Python/SnapshotPlan.py`（`TAG_FORMAT` / `ASSET_FORMATS` / `NATIVE_ASSET_FORMATS`），
    `bin/CheckScripts.py` 会断言本文件里的字面量与它一致，防止漂移。

    ⚠️ 本文件必须保存为 **UTF-8 with BOM**：Windows PowerShell 5.1 对无 BOM 的文件按 ANSI 解码，
       中文提示会变乱码（`bin/CheckScripts.py` 会检查这一点）。

    参数：
      -List               只列出快照（版本号 / 目标 / 资产 / 剩余天数），不下载
      -Pr <N>             只取某个 PR 的快照（PR 号就在版本段里：`.<PR 补零 5 位>.`）
      -Branch <name>      按分支取：先查该分支对应的 PR，再按 PR 号过滤
      -Run <id>           按 Actions run 取：先查该 run 关联的 PR，再按 PR 号过滤
      -Kind <k>           jvm | desktop-native | cli-native（默认 = 不限，取最近一次）
      -Out <dir>          落地目录（默认本脚本所在目录，即 `Dist\`）
      -DryRun             只预览：会下载哪个包、解压到哪里
      -Force              即使本地已取过同一版，也重新下载
      -NoInstall          只取件：只解压到 `-Out\<资产名>\`，不平铺、不替换任何既有文件（纯并排试包）
      -KeepExtract        平铺 / 安装成功后**保留**解压目录（默认会清掉，见上方第 4 件事）
      -Repo <owner/repo>  仓库（默认 ACANX/AHA）

    退出码：0 成功；1 失败。
#>
[CmdletBinding()]
param(
    [switch]$List,
    [int]$Pr = 0,
    [string]$Branch = '',
    [string]$Run = '',
    [ValidateSet('', 'jvm', 'desktop-jvm', 'cli-jvm', 'desktop-native', 'cli-native')][string]$Kind = '',
    [string]$Out = '',
    [switch]$Force,
    [switch]$NoInstall,
    [switch]$KeepExtract,
    [switch]$DryRun,
    [string]$Repo = 'ACANX/AHA'
)

$ErrorActionPreference = 'Stop'
# 几十 MB 的下载配进度条会明显拖慢速度，且日志里没有意义（与 UpdateNative.ps1 同取舍）
$ProgressPreference = 'SilentlyContinue'
# Windows 控制台默认代码页是 GBK：不改成 UTF-8，中文提示与 ✅ 会变乱码
$OutputEncoding = [Text.Encoding]::UTF8
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
# Windows PowerShell 5.1 的默认 SecurityProtocol 可能不含 TLS 1.2，GitHub 会直接拒绝连接
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# ── 命名契约（唯一来源：.github/Python/SnapshotPlan.py）──────────────────────────
# 这三行字面量一旦与判定脚本漂移，就会变成「发布页上有包、本地却永远取不到」。
# bin/CheckScripts.py 会断言两边一致（与原生取件的 PS1/Python 契约同一思路）。
$TagPattern   = '^V(?<version>[0-9][0-9.]*)-AHA-(?<target>Desktop-Native|Cli-Native|Desktop-JVM|Cli-JVM|NativeDesktop|NativeCli|Desktop|Cli)(?:-SNAPSHOT)?$'
$TtlDays      = 7

#: 取件种类 → 目标 / 资产前缀 / 平台标签（JVM 线与原生线共用同一套，issue #215）
$kinds = @{
    'jvm'            = @{ Title = 'JVM 便携包';  Targets = @('Desktop-JVM', 'Cli-JVM', 'Desktop', 'Cli'); Prefixes = @('aha-desktop-jvm-', 'aha-cli-jvm-') }
    'desktop-jvm'    = @{ Title = 'JVM 桌面端便携包'; Targets = @('Desktop-JVM', 'Desktop'); Prefixes = @('aha-desktop-jvm-') }
    'cli-jvm'        = @{ Title = 'JVM CLI 便携包';   Targets = @('Cli-JVM', 'Cli');         Prefixes = @('aha-cli-jvm-') }
    'desktop-native' = @{ Title = '原生桌面包';  Targets = @('Desktop-Native', 'NativeDesktop'); Prefixes = @('aha-desktop-native-') }
    'cli-native'     = @{ Title = '原生 CLI 包'; Targets = @('Cli-Native', 'NativeCli');         Prefixes = @('aha-cli-native-') }
}

#: 目标 → 本机要替换的可执行文件基名。JVM 便携包没有单一 exe（是整包），不安装。
$installExeBase = @{
    'Desktop-Native' = 'aha-desktop-native'
    'NativeDesktop'  = 'aha-desktop-native'   # 旧拼写（清理 / 取件都兼容）
    'Cli-Native'     = 'aha-cli-native'
    'NativeCli'      = 'aha-cli-native'
}

# 平铺逻辑放在单独文件里，好让自测脚本**离线**直接驱动它（见该文件头部的理由）
. (Join-Path $PSScriptRoot 'UpdateInstall.ps1')

function Install-NativeBinary {
    # 与 `UpdateNative.ps1` 同一套规则：只装可执行文件本体（外加同目录的 *.dll，若有），
    # 旧版先备份成 `<名字>.exe.bak`。目标被占用时返回原因，由调用方决定怎么报。
    param([string]$ExtractDir, [string]$TargetExe)
    $exeName = [IO.Path]::GetFileName($TargetExe)
    $src = Get-ChildItem -LiteralPath $ExtractDir -Recurse -File -Filter $exeName |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $src) { return @{ ok = $false; reason = "解压目录里没有 $exeName" } }
    if (Test-Path -LiteralPath $TargetExe) {
        Copy-Item -LiteralPath $TargetExe -Destination ($TargetExe + '.bak') -Force
    }
    Copy-Item -LiteralPath $src -Destination $TargetExe -Force
    $sidecars = @()
    $srcDir = Split-Path -Parent $src
    $dstDir = Split-Path -Parent $TargetExe
    foreach ($dll in (Get-ChildItem -LiteralPath $srcDir -File -Filter '*.dll' -ErrorAction SilentlyContinue)) {
        Copy-Item -LiteralPath $dll.FullName -Destination (Join-Path $dstDir $dll.Name) -Force
        $sidecars += $dll.Name
    }
    return @{ ok = $true; src = $src; sidecars = $sidecars }
}

$OutDir = if ($Out) { $Out } else { $PSScriptRoot }
# `-Out` 指向还不存在的目录时必须先建出来：否则 `Invoke-WebRequest -OutFile` 会报
# 「Could not find a part of the path …」（实测在验收 10 的无 token 环境里踩到，
# 那次还连带把 curl 兜底也带崩了——见下面的 curl 定位）。
if (-not (Test-Path -LiteralPath $OutDir)) {
    New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
}
$StateFile = Join-Path $OutDir '.aha-snapshot.json'

# 本机架构 → 平台标签（两种命名都要给：JVM 线用 x64，原生线用 amd64）
$isArm = ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64')
# 两条线现在共用同一套平台词（issue #215）：JVM 线与原生线取同一个标签。
$jvmPlatform = if ($isArm) { 'windows-arm64' } else { 'windows-amd64' }
$nativePlatform = $jvmPlatform

# API 请求才带 token（未认证的 60 次/小时容易撞上）；**下载请求不带**——
# GitHub 的下载地址会 302 到 objects.githubusercontent.com，没必要把凭据交给另一个主机。
# 注意：本脚本在完全没有 token 的环境里也必须能用（issue #173 验收 10）。
$headers = @{ 'User-Agent' = 'AHA-UpdateSnapshot'; 'Accept' = 'application/vnd.github+json' }
$downloadHeaders = @{ 'User-Agent' = 'AHA-UpdateSnapshot' }
if ($env:GITHUB_TOKEN) { $headers['Authorization'] = "Bearer $env:GITHUB_TOKEN" }
elseif ($env:GH_TOKEN) { $headers['Authorization'] = "Bearer $env:GH_TOKEN" }

function Get-Json {
    param([string]$Uri)
    return Invoke-RestMethod -Uri $Uri -Headers $headers
}

function Get-AssetFor {
    # 一个快照 release 里可能有多个平台的资产（以及 `-Report.zip` 报告包）。
    # 判据：前缀属于该目标，且以 `-<平台>-jdk<JDK>.zip` 收尾；显式排除报告包。
    param($Release, [string]$Prefix, [string]$Platform)
    return $Release.assets | Where-Object {
        $_.name.StartsWith($Prefix) -and
        $_.name.EndsWith("-$Platform-jdk25.zip") -and
        -not $_.name.EndsWith('-Report.zip')
    } | Select-Object -First 1
}

function Get-Candidates {
    # 返回排序后的快照清单（新→旧）：排序键 = 版本段里的 `MMddHHmm`，并列时用创建时间。
    # 同一个函数同时服务 `-List` 与「无参数取最近一次」——验收 6 要求两者**首行一致**，
    # 各写一套排序是实现漂移的经典来源。
    param($Releases)
    $items = @()
    foreach ($r in $Releases) {
        if ($r.draft) { continue }
        if (-not ($r.tag_name -match $TagPattern)) { continue }
        $version = $Matches['version']
        $target = $Matches['target']
        if ($Kind) {
            if ($kinds[$Kind].Targets -notcontains $target) { continue }
        }
        # 版本段：<基线>.<PR 补零 5 位>.<MMddHHmm>
        $parts = $version.Split('.')
        $stamp = ''
        $prNum = 0
        if ($parts.Count -ge 2) {
            $prNum = [int]($parts[$parts.Count - 2])
            $stamp = $parts[$parts.Count - 1]
        }
        if ($Pr -gt 0 -and $prNum -ne $Pr) { continue }
        if ($PrFilter -gt 0 -and $prNum -ne $PrFilter) { continue }
        # 本平台该取哪个资产：先按目标找前缀（JVM 线的平台标签与原生线不同）
        $prefix = $null
        if ($target -in @('Desktop-JVM', 'Desktop')) { $prefix = 'aha-desktop-jvm-' }
        elseif ($target -in @('Cli-JVM', 'Cli')) { $prefix = 'aha-cli-jvm-' }
        elseif ($target -in @('Desktop-Native', 'NativeDesktop')) { $prefix = 'aha-desktop-native-' }
        else { $prefix = 'aha-cli-native-' }
        $platform = if ($target -in @('Desktop-JVM', 'Cli-JVM', 'Desktop', 'Cli')) { $jvmPlatform } else { $nativePlatform }
        $asset = Get-AssetFor -Release $r -Prefix $prefix -Platform $platform
        $published = if ($r.published_at) { [datetime]$r.published_at } else { [datetime]$r.created_at }
        $items += [pscustomobject]@{
            Tag       = $r.tag_name
            Version   = $version
            Target    = $target
            Pr        = $prNum
            Stamp     = $stamp
            Published = $published
            Remaining = [math]::Floor(($TtlDays - ((Get-Date).ToUniversalTime() - $published.ToUniversalTime()).TotalDays))
            Asset     = $asset
        }
    }
    return $items | Sort-Object -Property @{Expression = 'Stamp'; Descending = $true}, @{Expression = 'Published'; Descending = $true}
}

# ── 0. 把过滤条件归一成 PR 号 ────────────────────────────────────────────────────
$PrFilter = $Pr
if (-not $PrFilter -and $Branch) {
    Write-Host "按分支查 PR：$Branch"
    $pulls = Get-Json "https://api.github.com/repos/$Repo/pulls?state=all&head=ACANX:$Branch&per_page=5"
    if (-not $pulls) { $pulls = Get-Json "https://api.github.com/repos/$Repo/pulls?state=all&head=$Branch&per_page=5" }
    if ($pulls -and $pulls.Count -gt 0) { $PrFilter = [int]$pulls[0].number; Write-Host "  分支 $Branch ⇒ PR #$PrFilter" }
    else { Write-Host "  没有找到分支 $Branch 对应的 PR；改为不限 PR" -ForegroundColor Yellow }
}
if (-not $PrFilter -and $Run) {
    Write-Host "按 run 查 PR：$Run"
    try {
        $runInfo = Get-Json "https://api.github.com/repos/$Repo/actions/runs/$Run"
        if ($runInfo.pull_requests -and $runInfo.pull_requests.Count -gt 0) {
            $PrFilter = [int]$runInfo.pull_requests[0].number
            Write-Host "  run $Run ⇒ PR #$PrFilter"
        } else {
            Write-Host "  run $Run 没有关联 PR；改为不限 PR" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  查不到 run $Run（$($_.Exception.Message)）；改为不限 PR" -ForegroundColor Yellow
    }
}

Write-Host '===== AHA 快照包 · 一键取件 ====='
Write-Host "仓库：$Repo"
Write-Host ("筛选：{0}{1}{2}" -f `
    $(if ($Kind) { "种类=$Kind " } else { '' }), `
    $(if ($PrFilter) { "PR=#$PrFilter" } else { 'PR=不限' }), `
    $(if ($StateFile) { "  状态文件=$StateFile" } else { '' }))
Write-Host "本机平台：JVM=$jvmPlatform  原生=$nativePlatform"

$api = "https://api.github.com/repos/$Repo/releases?per_page=50"
Write-Host "查询发布页：$api"
$releases = Get-Json $api
$candidates = Get-Candidates -Releases $releases

if (-not $candidates -or $candidates.Count -eq 0) {
    Write-Host ''
    Write-Host '没有找到符合条件的快照包。' -ForegroundColor Yellow
    Write-Host '  可能原因：还没有人为该 PR 打 `SNAPSHOT:*` 标签 / 发 `/snapshot` 命令；'
    Write-Host '           或者快照已随 PR 关闭被清理。'
    Write-Host '  提示：先用 -List 看全部快照（不加 -Pr / -Kind）。'
    exit 1
}

if ($List) {
    Write-Host ''
    Write-Host ("最近的快照（共 {0} 条，最多显示 10 条；有效期 {1} 天）：" -f $candidates.Count, $TtlDays)
    $shown = 0
    foreach ($c in $candidates) {
        if ($shown -ge 10) { break }
        $age = if ($c.Remaining -lt 0) { '已过期（仍可下载；建议让 PR 重出一版）' } else { "剩余 $($c.Remaining) 天" }
        $assetName = if ($c.Asset) { $c.Asset.name } else { '本平台暂无资产' }
        Write-Host ("  * {0}  [{1}]  PR #{2}  {3}" -f $c.Tag, $c.Target, $c.Pr, $age)
        Write-Host ("      资产：{0}" -f $assetName)
        $shown++
    }
    exit 0
}

# ── 1. 选一个（无参数 = 最近一次；有参数时上面已经过滤过）──────────────────────
$pick = $candidates[0]
foreach ($c in $candidates) {
    if ($c.Asset) { $pick = $c; break }
}
if (-not $pick.Asset) {
    Write-Host ''
    Write-Host ("找到了快照 {0}，但它没有本平台（{1}）的资产。" -f $pick.Tag, $nativePlatform) -ForegroundColor Yellow
    Write-Host '  用 -List 看看有哪些资产，或换一台机器 / 换 -Kind。'
    exit 1
}

Write-Host ''
Write-Host ("选中快照：{0}" -f $pick.Tag)
Write-Host ("  目标：{0}   PR：#{1}   发布时间：{2}   {3}" -f $pick.Target, $pick.Pr, $pick.Published.ToString('u'), $(if ($pick.Remaining -lt 0) { '已过期' } else { "剩余 $($pick.Remaining) 天" }))
Write-Host ("  资产：{0}（{1} MB）" -f $pick.Asset.name, [math]::Round($pick.Asset.size / 1MB, 1))

$zip = Join-Path $OutDir $pick.Asset.name
$extractDir = Join-Path $OutDir ([IO.Path]::GetFileNameWithoutExtension($pick.Asset.name))

if ($DryRun) {
    Write-Host '[dry-run] 只预览，不下载、不解压：'
    Write-Host ("  下载：{0}" -f $pick.Asset.browser_download_url)
    Write-Host ("  解压：{0}" -f $extractDir)
    if ($NoInstall) {
        Write-Host '  安装：（-NoInstall：只取件）'
    } elseif ($installExeBase.ContainsKey($pick.Target)) {
        Write-Host ("  安装：{0}" -f (Join-Path $OutDir ($installExeBase[$pick.Target] + '.exe')))
    } else {
        Write-Host '  安装：（该目标没有单一可执行文件）'
    }
    Write-Host ("  状态：{0}" -f $StateFile)
    exit 0
}

# 先探「能不能替换」：Windows 上正在运行的 exe 既不能覆盖也不能改名。
# 不先探的话，用户会在下完 50+ MB 之后才吃到一个错误（与 UpdateNative.ps1 同思路）。
$targetExe = ''
if (-not $NoInstall -and $installExeBase.ContainsKey($pick.Target)) {
    $targetExe = Join-Path $OutDir ($installExeBase[$pick.Target] + '.exe')
    if (Test-Path -LiteralPath $targetExe) {
        try {
            $probe = [IO.File]::Open($targetExe, 'Open', 'ReadWrite', 'None')
            $probe.Close()
        } catch {
            Write-Host ''
            Write-Host '[错误] 目标可执行文件正在运行（被占用），无法替换：' -ForegroundColor Red
            Write-Host ("        {0}" -f $targetExe)
            Write-Host '        请先关闭它（含后台 / 托盘进程）再重试；不需要替换就加 -NoInstall。'
            exit 1
        }
    }
}

# 复用判据是「zip + 解压目录」都在。原生快照装好后解压目录会被清掉，因此那种情况下不会命中
# 这一支，会走下面的「已有同尺寸的包」分支：**不重新下载**，只重新解压一遍（本地操作）。
$reuse = $false
if ((-not $Force) -and (Test-Path -LiteralPath $zip) -and (Test-Path -LiteralPath $extractDir)) {
    Write-Host ''
    Write-Host ("✅ 本地已有这一版：{0}" -f $extractDir) -ForegroundColor Green
    Write-Host '   （加 -Force 可重新下载；仍会执行安装步骤）'
    $reuse = $true
}

try {
    if (-not $reuse) {
        # ── 2. 下载（同尺寸的包直接复用；带 sha256 则校验）──────────────────────────
        if ((Test-Path -LiteralPath $zip) -and (Get-Item -LiteralPath $zip).Length -eq $pick.Asset.size) {
            Write-Host ("复用已下载的包：{0}" -f $pick.Asset.name)
        } else {
            Write-Host ("下载中：{0}" -f $pick.Asset.browser_download_url)
            try {
                Invoke-WebRequest -Uri $pick.Asset.browser_download_url -Headers $downloadHeaders -OutFile $zip
            } catch {
                # 兜底：Windows 10/11 自带 curl.exe。某些环境的 .NET TLS 被策略改动过，
                # 这时 curl 往往还能用（issue #173 要求「下载走 curl 兜底」）。
                Write-Host ("  Invoke-WebRequest 失败（{0}），改用 curl 兜底" -f $_.Exception.Message) -ForegroundColor Yellow
                # 不能写死 `$env:SystemRoot\System32\curl.exe`：`$env:SystemRoot` 在非 Windows 上是空的，
                # `Join-Path` 拿到 null 会直接抛参数绑定错误（实测把兜底本身弄崩）。
                # 用 PATH 查找，并同时兼容 `curl.exe`（Windows 10/11 自带）与 `curl`。
                $curlCmd = Get-Command curl.exe, curl -ErrorAction SilentlyContinue |
                    Select-Object -First 1 -ExpandProperty Source
                if (-not $curlCmd) { throw '没有 curl 兜底可用（PATH 里找不到 curl / curl.exe）' }
                & $curlCmd -fsSL -A 'AHA-UpdateSnapshot' -o $zip $pick.Asset.browser_download_url
                if ($LASTEXITCODE -ne 0) { throw "curl 下载失败（退出码 $LASTEXITCODE）" }
            }
            Write-Host ("已下载：{0} MB" -f [math]::Round((Get-Item -LiteralPath $zip).Length / 1MB, 1))
        }

        if ($pick.Asset.digest -and $pick.Asset.digest.StartsWith('sha256:')) {
            $expected = $pick.Asset.digest.Substring(7).ToUpper()
            $actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash
            if ($actual -ne $expected) {
                Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue
                throw "sha256 校验失败，文件已删除，请重试（$($pick.Asset.name)）"
            }
            Write-Host ("  sha256 校验通过：{0}" -f $actual.Substring(0, 16) + '…')
        } else {
            Write-Host '  （该资产没有提供 sha256，跳过校验）'
        }

        # ── 3. 解压到**按资产命名**的目录（不覆盖任何既有文件）──────────────────────
        if (Test-Path -LiteralPath $extractDir) { Remove-Item -LiteralPath $extractDir -Recurse -Force }
        Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
        [IO.Compression.ZipFile]::ExtractToDirectory($zip, $extractDir)
    }
    $files = Get-ChildItem -LiteralPath $extractDir -Recurse -File
    Write-Host ''
    Write-Host ("✅ 已取件：{0}" -f $pick.Tag) -ForegroundColor Green
    Write-Host ("  解压目录：{0}" -f $extractDir)
    Write-Host ("  文件数：{0}（合计 {1} MB）" -f $files.Count, [math]::Round(($files | Measure-Object -Property Length -Sum).Sum / 1MB, 1))
    $files | Select-Object -First 6 | ForEach-Object { Write-Host ("    - {0}" -f $_.FullName.Substring($extractDir.Length + 1)) }
    if ($files.Count -gt 6) { Write-Host ("    …（另有 {0} 个文件）" -f ($files.Count - 6)) }
    Write-Host ("  zip 保留在：{0}（便于离线重装；不需要可删）" -f $zip)

    # ── 4. 落地：JVM 包平铺到落地根；本机平台的原生包替换 exe（issue #244）──────────
    $flat = @()        # 本次铺 / 装到落地根的名字（也写进状态文件，便于事后对账）
    $cleanNote = ''
    if ($NoInstall) {
        Write-Host '  （-NoInstall：只取件，不平铺、不替换任何既有文件）'
        # 指出「跑哪个文件」：JVM 便携包自 issue #218 起是**包根平铺**，解压目录本身就是能启动的包，
        # 比只说一句「没落地」有用得多。
        $flatLauncher = $null
        foreach ($name in @('AhaDesktop.bat', 'AhaCli.bat', 'AhaDesktop.sh', 'AhaCli.sh')) {
            $cand = Join-Path $extractDir $name
            if (Test-Path -LiteralPath $cand) { $flatLauncher = $cand; break }
        }
        if ($flatLauncher) {
            Write-Host ("        运行：{0}" -f $flatLauncher) -ForegroundColor Green
        } elseif (Test-Path -LiteralPath (Join-Path $extractDir 'bin')) {
            Write-Host '        （这是旧形态（bin/ + lib/）的包，从 bin\ 下启动）'
        }
    } elseif ($targetExe) {
        $res = Install-NativeBinary -ExtractDir $extractDir -TargetExe $targetExe
        if (-not $res.ok) {
            Write-Host ("  [警告] 没能替换 {0}：{1}" -f $targetExe, $res.reason) -ForegroundColor Yellow
        } else {
            $fi = Get-Item -LiteralPath $targetExe
            Write-Host ''
            Write-Host ("✅ 已替换：{0}" -f $fi.FullName) -ForegroundColor Green
            Write-Host ("   大小 {0} MB   时间 {1}" -f [math]::Round($fi.Length / 1MB, 1), $fi.LastWriteTime)
            if ($res.sidecars.Count -gt 0) {
                Write-Host ("   附带 {0} 个 DLL：{1}" -f $res.sidecars.Count, ($res.sidecars -join ', '))
            }
            if (Test-Path -LiteralPath ($targetExe + '.bak')) {
                Write-Host ("   旧版备份：{0}.bak" -f $targetExe)
            }
            Write-Host ("   双击运行：{0}" -f $targetExe)
            $flat = @([IO.Path]::GetFileName($targetExe))
        }
    } else {
        # JVM 便携包**没有单一 exe**（是一整包）：平铺到落地根，取完件就能就地双击启动
        # （与 `UpdateJvm.ps1` 同一口径，issue #244 —— 以前只解压到 `Dist\<资产名>\` 就结束了）。
        $res = Install-JvmPackage -ExtractDir $extractDir -OutDir $OutDir
        if (-not $res.ok) {
            Write-Host ''
            Write-Host ("  [警告] 没能平铺到 {0}：" -f $OutDir) -ForegroundColor Yellow
            foreach ($why in $res.failed) { Write-Host ("        - {0}" -f $why) }
            Write-Host '        落地根里**没有改动任何文件**（这一步先探测再动手）。常见原因两条：'
            Write-Host '          1) 有实例正跑在这份包上（jar 被占用）⇒ 关掉它再重试；'
            Write-Host '          2) 被杀毒软件 / 索引服务短暂锁住 ⇒ 稍等一会儿重试。'
            Write-Host ("        包已经解压在：{0}（也可以直接跑里面的启动器）" -f $extractDir)
        } else {
            Write-Host ''
            Write-Host ("✅ 已平铺到：{0}" -f $OutDir) -ForegroundColor Green
            Write-Host ("   本次铺过去 {0} 项：{1}" -f $res.installed.Count, ($res.installed -join '、'))
            if ($res.backedUp.Count -gt 0) {
                Write-Host ("   覆盖前已备份为 *.bak：{0}" -f ($res.backedUp -join '、'))
            }
            $flat = $res.installed
            foreach ($name in @('AhaDesktop.bat', 'AhaCli.bat')) {
                if ($res.installed -contains $name) {
                    Write-Host ("   双击运行：{0}" -f (Join-Path $OutDir $name))
                    break
                }
            }
        }
    }

    # 铺好 / 装好之后解压目录就只是又一份副本（与 `UpdateJvm.ps1` 同一口径）：删掉，
    # 别让 `Dist\` 为同一个包占两份地方。zip 仍留着，要再解一份随时可解。
    # 判据带上 `$flat`：**平铺失败时绝不清理解压目录** —— 否则用户连包都没了。
    if (-not $NoInstall -and -not $KeepExtract -and $flat.Count -gt 0 -and (Test-Path -LiteralPath $extractDir)) {
        try {
            Remove-Item -LiteralPath $extractDir -Recurse -Force
            $cleanNote = '已清理解压目录（zip 仍留着，需要可再解；加 -KeepExtract 可保留）'
        } catch {
            $cleanNote = "解压目录没删掉：$($_.Exception.Message)"
        }
        Write-Host ("   {0}" -f $cleanNote)
    }
} catch {
    Write-Host ''
    Write-Host ("[错误] {0}" -f $_.Exception.Message) -ForegroundColor Red
    if ($pick.Remaining -lt 0) {
        Write-Host '        该快照已过期；下载失败时最省事的做法是让 PR 重出一版（再发一次 /snapshot 命令）。'
    }
    exit 1
}

# 状态文件与原生取件**不共用**（不读也不写 .aha-desktop-native.json / .aha-cli-native.json）：
# 本文件只回答「我取过哪一次快照」。用 [IO.File]::WriteAllText 避免 5.1 的 Set-Content 写 BOM。
# 落地结果：原生包 = 换了哪个 exe；JVM 包 = 平铺到了哪个目录；-NoInstall / 失败 = 没落地
$installedTo = '(未落地：-NoInstall 或落地失败)'
if ($flat.Count -gt 0) {
    $installedTo = $(if ($targetExe) { $targetExe } else { $OutDir })
}
$state = @{
    tag         = $pick.Tag
    version     = $pick.Version
    target      = $pick.Target
    pr          = $pick.Pr
    asset       = $pick.Asset.name
    extractedTo = $(if (Test-Path -LiteralPath $extractDir) { $extractDir } else { '(铺好后已清理)' })
    installedTo = $installedTo
    flat        = $flat
    fetchedAt   = (Get-Date).ToString('s')
} | ConvertTo-Json
[IO.File]::WriteAllText($StateFile, $state)
Write-Host ("  状态：{0}" -f $StateFile)
exit 0
