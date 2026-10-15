<#
    AHA JVM 便携包一键取件（Windows 原生实现，由 Dist 下的**专用启动器**调用）

        Dist\UpdateDesktopJvm.cmd   ->  -Kind Desktop
        Dist\UpdateCliJvm.cmd       ->  -Kind Cli

    dev 线的 JVM 便携包挂在发布页的**预发行版**上：

        tag   形如  V0.1.3.00178-aha-jvm
        资产  桌面端  aha-desktop-<版本>-<平台>[-jdk27].zip     （平台 windows-amd64 / ubuntu-amd64 / macos-arm64）
              CLI     aha-cli-<版本>[-jdk27].zip                （**平台无关**，只有一份）

    本脚本做五件事：

      1. 查发布页，找出 `-aha-jvm` 的 release（`-Tag` 可直接指定某个版本）；
      2. 挑出本机平台的资产（只有桌面端需要平台；CLI 没有平台维度）；
         **只看最新一版**：它缺本平台 / 本 JDK 段的资产时直接报错（并列出该版实际有什么），
         **不会**悄悄退到更旧的版本（见下方「为什么不静默降级」）；
      3. 下载 zip 到 `-Out`（已有同尺寸的包则复用；带 sha256 则校验）；
      4. 解压到 `<Out>\desktop-jvm\<版本>\` 或 `<Out>\cli-jvm\<版本>\`（**每版一个目录**，留档、可回滚）；
      5. 把**去版本号**的入口**复制**到 `<Out>` 根（`Dist\aha-desktop.jar`、`Dist\AhaDesktop.bat`…），
         让启动脚本与用户命令**与版本无关**；`-NoInstall` 可只做第 4 步。

    ⚠️ 为什么和 `UpdateNative.ps1` **分开**、且不复用它（issue #210）：

      * **落地语义不同**：`UpdateNative.ps1` 取的是原生包 —— 一个 exe，**摘出来覆盖**目标；
        JVM 包是「**单体 jar + 启动脚本**」（issue #218 起桌面端为**包根平铺**），
        做法是「解压到版本目录留档 + 把去版本号的入口**复制**到落地根」，
        不存在「替换某个可执行文件」这回事；
      * **资产名规则不同**：原生包是 `<前缀><版本>-<平台>-jdk<JDK>.zip`（平台段与 jdk 段**都必有**）；
        JVM 包是 `aha-desktop-<版本>-<平台>[-jdk27].zip` / `aha-cli-<版本>[-jdk27].zip`
        （平台段只有桌面端有、jdk 段只有 27 有）；
      * **平台词**：JVM 线与原生线**共用同一套**（`windows-amd64` / `ubuntu-amd64` / `macos-arm64`，
        2026-10-15 统一，issue #215）。

      把这两套规则塞进同一份脚本，会让它同时背两套分支 —— 得不偿失。

    ⚠️ 为什么不静默降级（ACANX 2026-10-15 实测踩到）：

      JVM 包的资产名带「平台 + JDK 段」，一次构建**可能缺某条腿**（实测
      `V0.1.3.00219-aha-jvm` 的 windows-amd64 / jdk25 那条腿因 Maven Central 403 失败）。
      早先的实现是「从新到旧遍历 release，取第一个有目标资产的」，于是默认取件一路退到
      `V0.1.3.00216` —— 那个包还是 `bin\` + `lib\` 的**旧布局、根本没有单体 jar**，
      用户却以为拿到的是最新版，看起来就像「新版本不支持 fat jar」。

      现在：最新一版缺目标资产就**响亮报错**并列出该版实际有哪些资产；确实想改用更旧的版本，
      必须显式说出口（`-AllowOlder` 退到最近一个可用的，或 `-Tag` 指定具体版本）。

    用法：

      UpdateJvm.ps1 -Kind Desktop                 取下最新的桌面端 JVM 便携包
      UpdateJvm.ps1 -Kind Cli                     取下最新的 CLI JVM 便携包
      UpdateJvm.ps1 -Kind Desktop -List           列出最近几版（版本 / 平台 / 资产）
      UpdateJvm.ps1 -Kind Desktop -DryRun         只预览，不下载
      UpdateJvm.ps1 -Kind Desktop -Force          已是最新也重下
      UpdateJvm.ps1 -Kind Desktop -AllowOlder     最新一版缺本平台资产时，退到最近一个可用的
      UpdateJvm.ps1 -Kind Desktop -NoInstall      只解压到版本目录，不平铺到落地根
      UpdateJvm.ps1 -Kind Desktop -Jdk 27         取 JDK 27 变体（默认 25）
      UpdateJvm.ps1 -Kind Desktop -Tag V0.1.3.00178-aha-jvm
      UpdateJvm.ps1 -Kind Desktop -Platform windows-amd64
#>
param(
    [ValidateSet('Desktop', 'Cli')][string]$Kind = 'Desktop',
    [switch]$List,
    [switch]$DryRun,
    [switch]$Force,
    #: 最新一版缺目标资产时，显式允许退到最近一个可用的旧版（默认**不**退，见上方说明）。
    [switch]$AllowOlder,
    #: 只解压到版本目录，不做「平铺到落地根」这一步（见上方第 5 件事）。
    [switch]$NoInstall,
    [string]$Jdk = '25',
    [string]$Tag = '',
    [string]$Platform = '',
    [string]$Repo = 'ACANX/AHA',
    [int]$Limit = 10,
    [string]$Out = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

Write-Host '正在查询 GitHub 发布页，请稍候……'
Write-Host ''

#: dev 线 JVM 包的 tag 后缀（见 BuildJVMArtifacts.yml 的 PreRelease-AHA-JVM 作业）。
$TagSuffix = '-aha-jvm'

#: 种类表：标题 / 是否按平台挑（CLI 平台无关）/ 落地子目录 / 状态文件 / 资产前缀 / 平铺入口。
#:
#: `Flat` = 解压后要**复制到 `<Out>` 根**的入口（issue #218）：名字**去版本号**，
#: 于是启动脚本与用户命令与版本无关（`Dist\AhaDesktop.bat`、`Dist\aha-desktop.jar` 直接可用）。
#: 同一 `Name` 有多个条目时**先命中的生效**（CLI 的新/旧两种 jar 名就靠这个兼容）。
#: 包根本来就没有的条目会被跳过（旧形态的包平铺不了），实测里会点名说明。
$kinds = @{
    'Desktop' = @{
        Title = '桌面端 JVM 便携包'; ByPlatform = $true; SubDir = 'desktop-jvm'
        State = '.aha-desktop-jvm.json'; Prefix = 'aha-desktop-'; Workflow = 'BuildJVMArtifacts.yml'
        Flat = @(
            @{ Glob = 'aha-desktop.jar'; Name = 'aha-desktop.jar' }
            @{ Glob = 'AhaDesktop.bat';  Name = 'AhaDesktop.bat' }
            @{ Glob = 'AhaDesktop.sh';   Name = 'AhaDesktop.sh' }
        )
    }
    'Cli'     = @{
        Title = 'CLI JVM 便携包'; ByPlatform = $false; SubDir = 'cli-jvm'
        State = '.aha-cli-jvm.json'; Prefix = 'aha-cli-'; Workflow = 'BuildJVMArtifacts.yml'
        Flat = @(
            @{ Glob = 'aha-cli.jar';   Name = 'aha-cli.jar' }
            @{ Glob = 'aha-cli-*.jar'; Name = 'aha-cli.jar' }   # 旧形态：包根那份带版本号的单体 jar
            @{ Glob = 'Aha.bat';       Name = 'Aha.bat' }       # 只有平铺形态的包才会出现在包根
            @{ Glob = 'Aha.sh';        Name = 'Aha.sh' }
        )
    }
}

$kindInfo = $kinds[$Kind]

# ── 输出编码：Windows PowerShell 5.1 下中文提示要显式设成 UTF-8 ──────────────────
$OutputEncoding = [Text.Encoding]::UTF8
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$OutDir = if ($Out) { $Out } else { $PSScriptRoot }
if (-not (Test-Path -LiteralPath $OutDir)) {
    New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
}
$InstallDir = Join-Path $OutDir $kindInfo.SubDir
$StateFile = Join-Path $OutDir $kindInfo.State

# 本机架构 → JVM 线的平台词（与原生线的 windows-amd64 不同）。
if (-not $Platform) {
    if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { $Platform = 'windows-arm64' } else { $Platform = 'windows-amd64' }
}
if (-not $Jdk) { $Jdk = '25' }

Write-Host ("仓库：{0}    种类：{1}    JDK：{2}" -f $Repo, $kindInfo.Title, $Jdk)
if ($kindInfo.ByPlatform) { Write-Host ("平台：{0}" -f $Platform) } else { Write-Host '平台：（CLI 包平台无关）' }

# API 请求才带 token（未认证的 60 次/小时容易撞上）；**下载请求不带**——
# 下载地址会 302 到 objects.githubusercontent.com，没必要把凭据交给另一个主机。
# 本脚本在完全没有 token 的环境里也必须能用。
$headers = @{ 'User-Agent' = 'AHA-UpdateJvm'; 'Accept' = 'application/vnd.github+json' }
$downloadHeaders = @{ 'User-Agent' = 'AHA-UpdateJvm' }
if ($env:GITHUB_TOKEN) { $headers['Authorization'] = "Bearer $env:GITHUB_TOKEN" }
elseif ($env:GH_TOKEN) { $headers['Authorization'] = "Bearer $env:GH_TOKEN" }

# ── tag → 版本号（`V<a.b.c.PPPPP>-aha-jvm`）────────────────────────────────────
function Get-TagVersion {
    param([string]$Name)
    if (-not $Name.EndsWith($TagSuffix)) { return $null }
    $body = $Name.Substring(0, $Name.Length - $TagSuffix.Length).TrimStart('V', 'v')
    if ($body -notmatch '^[0-9]+(\.[0-9]+)*$') { return $null }
    return $body
}

# ── 资产名：桌面端 `<前缀><版本>-<平台>[-jdk27].zip`；CLI `<前缀><版本>[-jdk27].zip` ──
function Get-AssetName {
    param([string]$Version)
    $suffix = if ($Jdk -eq '27') { '-jdk27' } else { '' }
    if ($kindInfo.ByPlatform) {
        return ("{0}{1}-{2}{3}.zip" -f $kindInfo.Prefix, $Version, $Platform, $suffix)
    }
    return ("{0}{1}{2}.zip" -f $kindInfo.Prefix, $Version, $suffix)
}

function Get-AssetFor {
    param($Release)
    $version = Get-TagVersion $Release.tag_name
    if (-not $version) { return $null }
    # 先按命名规则拼出目标资产名，再到清单里**精确匹配**：
    # JVM 包的 asset 名规则与原生包不同（jdk 段可选），精确匹配比前缀 + 后缀猜更稳。
    $wanted = Get-AssetName $version
    foreach ($a in $Release.assets) {
        if ($a.state -ne 'uploaded') { continue }
        if ($a.name -eq $wanted) { return $a }
    }
    return $null
}

# ── 列出 release（走 GitHub API）───────────────────────────────────────────────
function Get-Releases {
    $url = "https://api.github.com/repos/$Repo/releases?per_page=100"
    try {
        $all = Invoke-RestMethod -Uri $url -Headers $headers -TimeoutSec 30
    } catch {
        throw ("查询 GitHub 发布页失败：{0}`n" +
               "  提示：可能是网络问题，或未认证时的限流（60 次/小时）——" +
               "设置环境变量 GITHUB_TOKEN 可提高额度。") -f $_.Exception.Message
    }
    # 按发布时间降序；注意不能用 `??`（空合并是 PowerShell 7+ 语法，5.1 会报语法错）。
    return @($all | Where-Object { (Get-TagVersion $_.tag_name) } |
        Sort-Object -Property @{ Expression = {
            if ($_.published_at) { [datetime]$_.published_at } else { [datetime]$_.created_at }
        }; Descending = $true })
}

# `-Tag` 直接指定时资产清单未知，补齐它（`-List` / 取件都需要资产名）。
function Complete-Assets {
    param($Release)
    if ($Release.assets -and $Release.assets.Count -gt 0) { return $Release }
    try {
        $detail = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/tags/$($Release.tag_name)" -Headers $headers -TimeoutSec 30
        $Release.assets = @($detail.assets)
    } catch { }
    return $Release
}

$releases = @()
if ($Tag) {
    $releases = @(Complete-Assets ([pscustomobject]@{ tag_name = $Tag; assets = @(); published_at = $null; created_at = $null }))
    if (-not (Get-TagVersion $Tag)) {
        Write-Host ("[错误] {0} 不是 {1} 的 tag（后缀必须是 {2}）" -f $Tag, $kindInfo.Title, $TagSuffix) -ForegroundColor Red
        exit 1
    }
} else {
    $releases = @(Get-Releases)
    if ($releases.Count -eq 0) {
        Write-Host ("[错误] 发布页上没有任何 {0} 的 release（tag 后缀 {1}）" -f $kindInfo.Title, $TagSuffix) -ForegroundColor Red
        exit 1
    }
}

# ── -List：列出最近几版 ─────────────────────────────────────────────────────────
if ($List) {
    Write-Host ''
    Write-Host ("{0} 最近的版本（{1} / jdk{2}）：" -f $kindInfo.Title, $Repo, $Jdk)
    $shown = 0
    foreach ($r in $releases) {
        $r = Complete-Assets $r
        $asset = Get-AssetFor $r
        if (-not $asset) { continue }
        Write-Host ("  {0}   {1}   {2}" -f $r.tag_name, $asset.name, ('{0:N1} MB' -f ($asset.size / 1MB)))
        $shown++
        if ($shown -ge $Limit) { break }
    }
    if ($shown -eq 0) { Write-Host '  （没有匹配的资产：换个 -Jdk / -Platform 试试）' }
    exit 0
}

# ── 选版：**只看最新一版**；缺目标资产就响亮失败，不静默降级 ──────────────────────
#
# 静默降级的代价（ACANX 2026-10-15 实测）：最新一版缺本平台/本 JDK 段的资产时，
# 旧实现会一路退到更旧的 release —— 而更旧的包可能是**完全不同的形态**
# （V0.1.3.00216 还是 `bin\` + `lib\`，没有单体 jar），用户却以为拿的是最新版。
# 所以：降级必须是用户显式说出口的（`-AllowOlder` / `-Tag`）。
$newest = Complete-Assets $releases[0]
$pick = $null
$asset = Get-AssetFor $newest
if ($asset) { $pick = @{ Release = $newest; Asset = $asset } }

if (-not $pick -and $AllowOlder) {
    foreach ($r in $releases) {
        $r = Complete-Assets $r
        $asset = Get-AssetFor $r
        if ($asset) {
            Write-Host ("[注意] {0} 缺目标资产，按 -AllowOlder 退到 {1}。" -f $newest.tag_name, $r.tag_name) -ForegroundColor Yellow
            $pick = @{ Release = $r; Asset = $asset }
            break
        }
    }
}

if (-not $pick) {
    $platformNote = if ($kindInfo.ByPlatform) { $Platform } else { '平台无关' }
    $wanted = Get-AssetName (Get-TagVersion $newest.tag_name)
    Write-Host ("[错误] 最新一版 {0} 里没有{1}（jdk{2} / {3}）的资产。" -f $newest.tag_name, $kindInfo.Title, $Jdk, $platformNote) -ForegroundColor Red
    Write-Host ("        要的是：{0}" -f $wanted)
    $siblings = @($newest.assets | Where-Object { $_.name -like ($kindInfo.Prefix + '*') } | ForEach-Object { $_.name })
    if ($siblings.Count -gt 0) {
        Write-Host '        这一版实际有：'
        foreach ($name in $siblings) { Write-Host ("          - {0}" -f $name) }
    } else {
        Write-Host '        这一版没有任何本种类的资产（该次构建的相关腿可能整体失败了）。'
    }
    Write-Host '        怎么办：'
    Write-Host '          ① 只是 JDK 段不同？用 -Jdk 27'
    Write-Host '          ② 先看看最近几版都有什么？用 -List'
    Write-Host ("          ③ 确认改用更旧的版本：-Tag V<版本>{0}，或 -AllowOlder 退到最近一个可用的" -f $TagSuffix)
    exit 1
}

$version = Get-TagVersion $pick.Release.tag_name
$targetDir = Join-Path $InstallDir $version   # 每版一个目录，互不覆盖
$flat = @()            # 本次平铺到 <Out> 根的入口名
$missingFlat = @()     # 包根没有、无法平铺的入口名（旧形态的包会命中）
$doneFlat = @{}        # 已平铺的名字 → 避免多个条目落到同一个名字时重复复制
$state = @{}
if (Test-Path -LiteralPath $StateFile) {
    try { $state = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json } catch { $state = @{} }
}

Write-Host ''
Write-Host ("目标版本：{0}（{1}）" -f $pick.Release.tag_name, $version)
Write-Host ("资产：{0}（{1} MB）" -f $pick.Asset.name, [math]::Round($pick.Asset.size / 1MB, 1))
Write-Host ("落地：{0}" -f $targetDir)

$stateFlat = @($state.flat | Where-Object { $_ })
$canSkip = (-not $Force) -and ($state.tag -eq $pick.Release.tag_name) -and (Test-Path -LiteralPath $targetDir)
if ($canSkip -and (-not $NoInstall) -and ($stateFlat.Count -gt 0)) {
    # 上次平铺出去的入口被删了（或改了指 -Out）⇒ 不算「已是最新」，重解压补齐。
    if (@($stateFlat | Where-Object { -not (Test-Path -LiteralPath (Join-Path $OutDir $_)) }).Count -gt 0) {
        $canSkip = $false
    }
}
if ($canSkip) {
    Write-Host ("`n✅ 本地已经是 {0}，无需更新（加 -Force 可强制重下）" -f $version) -ForegroundColor Green
    exit 0
}

if ($DryRun) {
    Write-Host ''
    Write-Host '[dry-run] 只预览，不下载、不解压：'
    Write-Host ("  下载：{0}" -f $pick.Asset.browser_download_url)
    Write-Host ("  解压：{0}" -f $targetDir)
    Write-Host ("  状态：{0}" -f $StateFile)
    exit 0
}

$zip = Join-Path $OutDir $pick.Asset.name

try {
    # ── 下载（同尺寸的包直接复用；带 sha256 则校验）──────────────────────────────
    if ((Test-Path -LiteralPath $zip) -and (Get-Item -LiteralPath $zip).Length -eq $pick.Asset.size) {
        Write-Host ("复用已下载的包：{0}" -f $pick.Asset.name)
    } else {
        Write-Host ("下载中：{0}" -f $pick.Asset.browser_download_url)
        try {
            Invoke-WebRequest -Uri $pick.Asset.browser_download_url -Headers $downloadHeaders -OutFile $zip
        } catch {
            # 兜底：Windows 10/11 自带 curl.exe。某些环境的 .NET TLS 被策略改动过，
            # 这时 curl 往往还能用。
            Write-Host ("  Invoke-WebRequest 失败（{0}），改用 curl 兜底" -f $_.Exception.Message) -ForegroundColor Yellow
            $curlCmd = Get-Command curl.exe, curl -ErrorAction SilentlyContinue |
                Select-Object -First 1 -ExpandProperty Source
            if (-not $curlCmd) { throw '没有 curl 兜底可用（PATH 里找不到 curl / curl.exe）' }
            & $curlCmd -fsSL -A 'AHA-UpdateJvm' -o $zip $pick.Asset.browser_download_url
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
        Write-Host '  sha256 校验通过'
    }

    # ── 解压到 `<落地根>\<种类>\<版本>\` ─────────────────────────────────────────
    # 用 .NET 的 ZipFile 而不是 Expand-Archive：后者依赖 Microsoft.PowerShell.Archive 模块，
    # 实测在某些安装下会报 "module could not be loaded"。ZipFile 自 5.1 起可用。
    Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $targetDir) { Remove-Item -LiteralPath $targetDir -Recurse -Force }
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    [IO.Compression.ZipFile]::ExtractToDirectory($zip, $targetDir)

    # ── 平铺到 `<Out>` 根（issue #218）─────────────────────────────────────────
    #
    # 版本目录 `...\<种类>\<版本>\` 留档（多版并存、可回滚）；同时把**去版本号**的入口
    # **复制**一份到 `<Out>` 根 —— 启动脚本与用户命令因此与版本无关：
    #   `Dist\AhaDesktop.bat`、`java -jar Dist\aha-desktop.jar`、`Dist\Aha.bat` 都能直接用。
    # 只复制，不动版本目录里的那一份。
    if (-not $NoInstall) {
        foreach ($item in $kindInfo.Flat) {
            if ($doneFlat.ContainsKey($item.Name)) { continue }
            # 只看**包根**（不加 -Recurse）：旧形态的包把脚本放在 bin\ 下，那不是平铺入口。
            $src = @(Get-ChildItem -LiteralPath $targetDir -Filter $item.Glob -File -ErrorAction SilentlyContinue)
            if ($src.Count -eq 0) { $missingFlat += $item.Name; continue }
            try {
                Copy-Item -LiteralPath $src[0].FullName -Destination (Join-Path $OutDir $item.Name) -Force
            } catch {
                throw ("平铺 {0} 到 {1} 失败：{2}`n  最常见的原因是它正被运行中的实例占用 —— 请先关闭，再重试。" -f $item.Name, $OutDir, $_.Exception.Message)
            }
            $flat += $item.Name
            $doneFlat[$item.Name] = $true
        }
    }

    # 列出关键入口做一次自证，并据此给出「怎么运行」的提示。
    #
    # 包内形态有两代（issue #218 起为**平铺**）：
    #   新：单体 jar + 启动脚本 + 文档，全在包根（无 bin/、无 lib/）；
    #   旧：bin/（启动脚本）+ lib/（模块与依赖）[+ 包根单体 jar]。
    # 两代都要提示到，否则取到旧包就没有运行入口提示。
    $entries = @(Get-ChildItem -LiteralPath $targetDir -Recurse -File | Select-Object -First 2000)
    $jars = @($entries | Where-Object { $_.Name.EndsWith('.jar') })
    $hasBin = Test-Path -LiteralPath (Join-Path $targetDir 'bin')
    $launcherName = if ($Kind -eq 'Cli') { 'Aha.bat' } else { 'AhaDesktop.bat' }
    $launcher = if ($hasBin) { Join-Path $targetDir ("bin\{0}" -f $launcherName) } else { Join-Path $targetDir $launcherName }
    if (-not (Test-Path -LiteralPath $launcher)) { $launcher = $null }
    Write-Host ''
    Write-Host ("解压完成：{0} 个文件（jar {1} 个，启动脚本 {2}）" -f $entries.Count, $jars.Count, $(if ($launcher) { '有' } else { '无' }))
    if ($NoInstall) {
        Write-Host '（-NoInstall：只解压，未平铺到落地根）'
    } elseif ($flat.Count -gt 0) {
        Write-Host ("已平铺到 {0}：{1}" -f $OutDir, ($flat -join '、'))
        if ($missingFlat.Count -gt 0) {
            Write-Host ("       （包根没有 {0}，未平铺 —— 该形态的包还没带启动脚本）" -f ($missingFlat -join '、'))
        }
    } else {
        Write-Host ("[注意] 未平铺：包根没有 {0}。" -f ($missingFlat -join '、')) -ForegroundColor Yellow
        Write-Host '       旧形态的包（包根没有单体 jar）平铺不了；请取 0.1.3.00219 及以后的版本。'
    }
} catch {
    Write-Host ''
    Write-Host ("[错误] {0}" -f $_.Exception.Message) -ForegroundColor Red
    exit 1
}

$stateOut = @{ tag = $pick.Release.tag_name; version = $version; asset = $pick.Asset.name; dir = $targetDir; flat = @($flat); installedAt = (Get-Date).ToString('s') } | ConvertTo-Json
[IO.File]::WriteAllText($StateFile, $stateOut)

Write-Host ''
Write-Host ("✅ 已取件到 {0}" -f $version) -ForegroundColor Green
Write-Host ("目录：{0}" -f $targetDir)
Write-Host ("下载包保留在：{0}（便于离线重装；不需要可删）" -f $zip)
if ($launcher) {
    $leaf = Split-Path -Path $launcher -Leaf
    $flatLauncher = Join-Path $OutDir $leaf
    if (Test-Path -LiteralPath $flatLauncher) {
        Write-Host ("运行：{0}（已平铺到落地根；换版本不必改命令）" -f $flatLauncher)
    } else {
        Write-Host ("运行：{0}" -f $launcher)
    }
}
exit 0
