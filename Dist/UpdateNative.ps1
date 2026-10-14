<#
    AHA 原生镜像一键更新（Windows 原生实现，由 Dist\Update*.cmd 调用）

    做三件事（与 LegionFanControl 的 Dist\UpdateNative.cmd 同一思路，按本仓库的命名落地）：
      1. 查 GitHub 发布页，找出最新的、带**本平台**资产的 release（桌面端或 CLI，由 -Kind 决定）；
      2. 下载该 zip（保留在 Dist\，便于离线重装）；
      3. 解压出可执行文件，覆盖 Dist\aha-desktop-native.exe / aha-cli-native.exe（旧版留 .bak）。

    为什么这里是 PowerShell 而不是复用仓库里那个 Python 脚本：

      * Windows 上**不能假设机器装了 Python**（一键入口的受众是克隆仓库的人）；
      * 这次真机反馈的失败恰好就是「本机 Python 的 OpenSSL 不可用」
        （`OPENSSL_Uplink ... no OPENSSL_Applink`）。`Invoke-WebRequest` 走 .NET / Schannel，
        **完全不碰 Python 的 OpenSSL**，因此不受其影响。

    POSIX（WSL / Linux / macOS）继续用 Python 版：`Script/Python/DesktopNativeVersionUpdate.py`
    与 `Script/Python/CliNativeVersionUpdate.py`（功能更多：多源发现、断点续传）。
    两套实现共用同一份「命名契约」，由 `bin/CheckScripts.py` 断言一致，防止漂移。

    ⚠️ 本文件必须保存为 **UTF-8 with BOM**：Windows PowerShell 5.1 对无 BOM 的文件按 ANSI 解码，
       中文提示会变乱码（`bin/CheckScripts.py` 会检查这一点）。

    参数：
      -Kind Desktop|Cli   取哪种原生包（默认 Desktop；两个 .cmd 分别传入）
      -List               只列出最近的版本与可下载资产
      -DryRun             只预览：会下载哪个包、装到哪里
      -Force              忽略「已是最新」，强制重装
      -Tag <tag>          指定 release tag（默认取最新的可下载版本）
      -Jdk 25|27          JDK 分支（默认 25；无该分支的 release 会被跳过）
      -Platform <标签>    平台标签（默认按本机架构：windows-amd64 / windows-arm64）
      -Repo <owner/repo>  仓库（默认 ACANX/AHA）

    退出码：0 成功（含「已是最新」）；1 失败。
#>
[CmdletBinding()]
param(
    [ValidateSet('Desktop', 'Cli')][string]$Kind = 'Desktop',
    [string]$Repo = 'ACANX/AHA',
    [string]$Tag = '',
    [string]$Jdk = '25',
    [string]$Platform = '',
    [switch]$Force,
    [switch]$DryRun,
    [switch]$List
)

$ErrorActionPreference = 'Stop'
# 50+ MB 的下载配进度条会明显拖慢速度，且输出对日志无意义（与 LegionFanControl 那份同取舍）
$ProgressPreference = 'SilentlyContinue'
# Windows 控制台默认代码页是 GBK：不改成 UTF-8，中文提示与 ✅ 会变乱码/try 里兼容旧版
$OutputEncoding = [Text.Encoding]::UTF8
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
# Windows PowerShell 5.1 的默认 SecurityProtocol 可能不含 TLS 1.2，GitHub 会直接拒绝连接
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# ── 命名契约（唯一来源：Script/Python/NativeVersionUpdate.py 的 KINDS 表）────────────
# 两套实现刻意并存（Windows 不假设有 Python、POSIX 不假设有 PowerShell），但这三个字面量
# 一旦漂移就会变成「某个平台上永远取不到包」——bin/CheckScripts.py 会断言两边一致。
$kinds = @{
    Desktop = @{ Title = '桌面端原生镜像'; TagSuffix = '-aha-desktop-native'; AssetPrefix = 'AHA-Desktop-Native-'; ExeBase = 'aha-desktop-native' }
    Cli     = @{ Title = 'CLI 原生镜像';   TagSuffix = '-aha-cli-native';     AssetPrefix = 'AHA-Cli-Native-';     ExeBase = 'aha-cli-native' }
}
$kindInfo = $kinds[$Kind]

$DistDir = $PSScriptRoot
$TargetExe = Join-Path $DistDir ($kindInfo.ExeBase + '.exe')
$StateFile = Join-Path $DistDir ('.' + $kindInfo.ExeBase + '.json')

if (-not $Platform) {
    if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { $Platform = 'windows-arm64' } else { $Platform = 'windows-amd64' }
}

Write-Host ("===== AHA {0} · 一键更新 =====" -f $kindInfo.Title)
Write-Host "仓库：$Repo"
Write-Host "平台：$Platform    JDK：$Jdk"
Write-Host "安装位置：$TargetExe"
Write-Host ''

# API 请求才带 token（未认证的 60 次/小时很容易撞上）；
# 下载请求不带——GitHub 的下载地址会 302 到 objects.githubusercontent.com，
# 没必要把凭据交到另一个主机上（Python 侧也是这么做的）。
$headers = @{ 'User-Agent' = 'AHA-UpdateNative'; 'Accept' = 'application/vnd.github+json' }
$downloadHeaders = @{ 'User-Agent' = 'AHA-UpdateNative' }
if ($env:GITHUB_TOKEN) { $headers['Authorization'] = "Bearer $env:GITHUB_TOKEN" }
elseif ($env:GH_TOKEN) { $headers['Authorization'] = "Bearer $env:GH_TOKEN" }

function Get-MatchingAsset {
    param($Release)
    # 同一族资产里有「报告包」（AHA-*-…-jdk25-Report.zip）：它与镜像包同版本号，只差结尾。
    # 这里靠「必须以 -$Platform-jdk$Jdk.zip 收尾」直接排除，并额外显式排除 -Report.zip。
    $Release.assets | Where-Object {
        $_.name.StartsWith($kindInfo.AssetPrefix) -and
        $_.name.EndsWith("-$Platform-jdk$Jdk.zip") -and
        -not $_.name.EndsWith('-Report.zip')
    } | Select-Object -First 1
}

function Get-InstalledTag {
    if (-not (Test-Path -LiteralPath $StateFile)) { return $null }
    try { return (Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json).tag } catch { return $null }
}

# ── 第 1 步：查发布页 ────────────────────────────────────────────────────────────
# 不走 /releases/latest：那个接口只认「非预发行」，而本仓库的原生包**全部**是预发行（会 404）。
# 改为拉一页列表，按时间从新到旧扫，取第一个「带本平台资产」的。
$api = "https://api.github.com/repos/$Repo/releases?per_page=30"
Write-Host "查询发布页：$api"
$releases = Invoke-RestMethod -Uri $api -Headers $headers

if ($List) {
    Write-Host ''
    Write-Host ("{0} 最近的 {1}（{2} / jdk{3}）：" -f $Repo, $kindInfo.Title, $Platform, $Jdk)
    $shown = 0
    foreach ($r in $releases) {
        if ($shown -ge 5) { break }
        if ($r.draft) { continue }
        if (-not $r.tag_name.EndsWith($kindInfo.TagSuffix)) { continue }
        $asset = Get-MatchingAsset $r
        $mark = if ($asset) { '可下载' } else { '缺本平台包' }
        $name = if ($asset) { $asset.name } else { '-' }
        Write-Host ("  * {0}  [{1}]  {2}" -f $r.tag_name, $mark, $name)
        $shown++
    }
    exit 0
}

$pick = $null
foreach ($r in $releases) {
    if ($r.draft) { continue }
    if ($Tag) {
        if ($r.tag_name -ne $Tag) { continue }
    } elseif (-not $r.tag_name.EndsWith($kindInfo.TagSuffix)) {
        continue
    }
    $asset = Get-MatchingAsset $r
    if ($asset) { $pick = @{ Release = $r; Asset = $asset }; break }
}

if (-not $pick) {
    if ($Tag) {
        throw ("tag $Tag 里没有 $Platform（jdk$Jdk）的包；去掉 -Tag 让脚本自己找，或用 -List 看看有哪些版本")
    }
    throw ("没有找到带 $Platform（jdk$Jdk）资产的 {0} release；可用 -List 查看，或用 -Tag / -Jdk / -Platform 指定" -f $kindInfo.Title)
}

$picked = $pick.Release.tag_name
$channel = if ($pick.Release.prerelease) { '预发行' } else { '正式版' }
$installed = Get-InstalledTag
Write-Host ("最新发布：{0}（{1}）" -f $picked, $channel)
Write-Host ("资产：{0}（{1} MB）" -f $pick.Asset.name, [math]::Round($pick.Asset.size / 1MB, 1))
Write-Host ("本地已装：{0}" -f $(if ($installed) { $installed } else { '（无记录）' }))
Write-Host ''

if (-not $Force -and $installed -eq $picked -and (Test-Path -LiteralPath $TargetExe)) {
    Write-Host "✅ 已经是 $picked，无需更新（加 -Force 可强制重装）" -ForegroundColor Green
    exit 0
}

if ($DryRun) {
    Write-Host '[dry-run] 只预览，不下载、不解压：'
    Write-Host ("  下载：{0}" -f $pick.Asset.browser_download_url)
    Write-Host ("  安装：{0}" -f $TargetExe)
    exit 0
}

# 先把「能不能替换」探清楚：Windows 上正在运行的 exe 既不能覆盖也不能改名，
# 不先探一下的话，用户会在下完 50+ MB 之后才吃到一个错误（与 Python 版 ensure_replaceable 同思路）。
if (Test-Path -LiteralPath $TargetExe) {
    try {
        $probe = [IO.File]::Open($TargetExe, 'Open', 'ReadWrite', 'None')
        $probe.Close()
    } catch {
        Write-Host ''
        Write-Host '[错误] 目标可执行文件正在运行（被占用），无法替换。' -ForegroundColor Red
        Write-Host '        请先关闭它（含后台 / 托盘进程），再重新运行本脚本。'
        exit 1
    }
}

$zip = Join-Path $DistDir $pick.Asset.name

try {
    # ── 第 2 步：下载（已有同尺寸的包则复用）────────────────────────────────────
    if ((Test-Path -LiteralPath $zip) -and (Get-Item -LiteralPath $zip).Length -eq $pick.Asset.size) {
        Write-Host ("复用已下载的包：{0}" -f $pick.Asset.name)
    } else {
        Write-Host ("下载中：{0}" -f $pick.Asset.browser_download_url)
        Invoke-WebRequest -Uri $pick.Asset.browser_download_url -Headers $downloadHeaders -OutFile $zip
        Write-Host ("已下载：{0} MB" -f [math]::Round((Get-Item -LiteralPath $zip).Length / 1MB, 1))
    }

    # sha256 校验（API 提供了才校验；失败即删掉，避免下次被「复用」）
    if ($pick.Asset.digest -and $pick.Asset.digest.StartsWith('sha256:')) {
        $expected = $pick.Asset.digest.Substring(7).ToUpper()
        $actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash
        if ($actual -ne $expected) {
            Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue
            throw "sha256 校验失败，文件已删除，请重试（$($pick.Asset.name)）"
        }
        Write-Host '  sha256 校验通过'
    }

    # ── 第 3 步：解压出可执行文件并覆盖 ──────────────────────────────────────────
    # [IO.Path]::GetTempPath() 而不是 $env:TEMP：后者在非 Windows 上是空的，
    # 而 Join-Path 拿到 null 会报 "parameter 'Path' because it is null"（实测到过）。
    $work = Join-Path ([IO.Path]::GetTempPath()) ('aha-update-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $work | Out-Null
    try {
        # 用 .NET 的 ZipFile 而不是 Expand-Archive：后者依赖 Microsoft.PowerShell.Archive 模块，
        # 实测在某些安装下会报 “module could not be loaded”（本机 pwsh 上就撞到）。
        # ZipFile 自 Windows PowerShell 5.1 起可用（先 Add-Type 载入程序集），行为与 Expand-Archive 一致。
        Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
        [IO.Compression.ZipFile]::ExtractToDirectory($zip, $work)
        # 包内是平铺的（无顶层目录），但仍递归找：将来若加了顶层目录也不用改这里
        $src = Get-ChildItem -LiteralPath $work -Recurse -File -Filter ($kindInfo.ExeBase + '.exe') |
            Select-Object -First 1 -ExpandProperty FullName
        if (-not $src) {
            throw "zip 内没有 $($kindInfo.ExeBase).exe：$($pick.Asset.name)"
        }
        if (Test-Path -LiteralPath $TargetExe) {
            Copy-Item -LiteralPath $TargetExe -Destination ($TargetExe + '.bak') -Force
        }
        Copy-Item -LiteralPath $src -Destination $TargetExe -Force
    } finally {
        Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
    }
} catch {
    Write-Host ''
    Write-Host ("[错误] {0}" -f $_.Exception.Message) -ForegroundColor Red
    exit 1
}

# 状态文件与 Python 版同名同义（互不混淆：两边都只认自己写的 tag）。
# 用 [IO.File]::WriteAllText 而不是 Set-Content -Encoding UTF8：后者在 5.1 下会写 BOM，
# 而 Python 侧的 json.loads 不认 BOM。
$state = @{ tag = $picked; asset = $pick.Asset.name; installedAt = (Get-Date).ToString('s') } | ConvertTo-Json
[IO.File]::WriteAllText($StateFile, $state)

$fi = Get-Item -LiteralPath $TargetExe
Write-Host ''
Write-Host "✅ 已更新到 $picked" -ForegroundColor Green
Write-Host ("文件：{0}" -f $fi.FullName)
Write-Host ("大小：{0} MB   时间：{1}" -f [math]::Round($fi.Length / 1MB, 1), $fi.LastWriteTime)
Write-Host ("下载包保留在：{0}（便于离线重装；不需要可删）" -f $zip)
Write-Host ("运行：{0}" -f $TargetExe)
exit 0
