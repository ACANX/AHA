#  UpdateInstall.ps1 — 把「解压出来的包」铺到落地根（`Dist/UpdateSnapshot.ps1` 的安装步骤）
#
#  为什么单独一个文件（issue #244）：
#    这段逻辑是**会动用户文件**的那种，必须能被**自测脚本**直接驱动
#    （`Script/PowerShell/SnapshotInstallSelfTest.ps1`：离线、临时目录里真跑）。
#    塞在 `UpdateSnapshot.ps1` 里就只能靠「下载 25 MB + 解压 + 铺」来测，还得联网 ⇒ 测不动。
#    所以：**只放纯函数、不读全局状态**，由调用方把目录传进来。
#
#  与 `UpdateJvm.ps1`（正式版取件）的关系：同一口径 —— JVM 便携包自 issue #218 起是**包根平铺**，
#    解压目录本身就是一个能直接启动的包；取件脚本要做的是「解压 → 把包根的一级内容铺到落地根」，
#    而不是把整个包丢在 `Dist\<资产名>\` 里让用户自己去翻、自己去搬。
#
#  编码：UTF-8 with BOM + 纯 LF（`bin/CheckScripts.py` 会检查 BOM；中文提示在 Windows PowerShell 5.1
#    下按 ANSI 解码会变乱码）。

function Install-JvmPackage {
    <#
    .SYNOPSIS
      把解压目录（JVM 便携包：包根平铺形态）的一级内容铺到落地根。

    .DESCRIPTION
      **两趟做：先探测、再动手。**
        第一趟：逐个看「目标是否被占用 / 不可写」——只要有一个不行就**直接返回失败**，
                落地根里**一个字节都不改**；
        第二趟：真的备份（`<名字>.bak`）+ 复制。
      为什么要这样：做一半才失败会留下「铺了一半的包」（新的 jar 配旧的脚本），
      比干脆没铺更糟；而分两趟的成本只是多一次 `Open`。

      目录条目（旧形态的 `lib\` 之类）不预探测，直接复制；失败会在第二趟如实报出来。

    .PARAMETER ExtractDir
      解压出来的包目录（包根平铺形态）。

    .PARAMETER OutDir
      落地根（`Dist\`）。必须已存在（调用方负责建）。

    .OUTPUTS
      hashtable：`ok`（全部成功）、`installed`（本次铺过去的一级条目名）、
      `backedUp`（被覆盖、已备份成 `.bak` 的名字）、`failed`（失败原因，人类可读）。
    #>
    param(
        [Parameter(Mandatory = $true)][string]$ExtractDir,
        [Parameter(Mandatory = $true)][string]$OutDir
    )

    $items = @(Get-ChildItem -LiteralPath $ExtractDir)
    $blocked = @()
    foreach ($item in $items) {
        $dst = Join-Path $OutDir $item.Name
        if ($item.PSIsContainer) { continue }
        if (-not (Test-Path -LiteralPath $dst)) { continue }
        try {
            # 独占方式打开：被别的进程占用（Windows 上最常见的是「有实例正在跑」）就会抛。
            $probe = [IO.File]::Open($dst, 'Open', 'ReadWrite', 'None')
            $probe.Close()
        } catch {
            $blocked += ("{0}（正在被占用或不可写）" -f $item.Name)
        }
    }
    if ($blocked.Count -gt 0) {
        return @{ ok = $false; installed = @(); backedUp = @(); failed = $blocked }
    }

    $installed = @()
    $backedUp = @()
    $failed = @()
    foreach ($item in $items) {
        $dst = Join-Path $OutDir $item.Name
        try {
            if (Test-Path -LiteralPath $dst) {
                $bak = $dst + '.bak'
                if (Test-Path -LiteralPath $bak) { Remove-Item -LiteralPath $bak -Recurse -Force }
                # 先改名再复制：这样「备份」是原子的一次改名，不会出现「复制到一半两个都不完整」。
                Move-Item -LiteralPath $dst -Destination $bak -Force
                $backedUp += $item.Name
            }
            if ($item.PSIsContainer) {
                Copy-Item -LiteralPath $item.FullName -Destination $dst -Recurse -Force
            } else {
                Copy-Item -LiteralPath $item.FullName -Destination $dst -Force
            }
            $installed += $item.Name
        } catch {
            $failed += ("{0}（{1}）" -f $item.Name, $_.Exception.Message)
        }
    }
    return @{ ok = ($failed.Count -eq 0); installed = $installed; backedUp = $backedUp; failed = $failed }
}
