#!/usr/bin/env bash
#
# AHA 一键取件（POSIX：Linux / macOS）
#
# 对标仓库 Dist\ 下的 Windows 启动器（Update*.cmd）与其背后的 PowerShell 实现：
#
#   UpdateDesktopJvm.sh          <-> Dist\UpdateDesktopJvm.cmd          + UpdateJvm.ps1 -Kind Desktop
#   UpdateCliJvm.sh              <-> Dist\UpdateCliJvm.cmd              + UpdateJvm.ps1 -Kind Cli
#   UpdateDesktopNative.sh       <-> Dist\UpdateDesktopNative.cmd       + UpdateNative.ps1 -Kind Desktop
#   UpdateCliNative.sh           <-> Dist\UpdateCliNative.cmd           + UpdateNative.ps1 -Kind Cli
#   UpdateDesktopJvmSnapshot.sh  <-> Dist\UpdateDesktopJvmSnapshot.cmd  + UpdateSnapshot.ps1
#   UpdateCliJvmSnapshot.sh      <-> Dist\UpdateCliJvmSnapshot.cmd      + UpdateSnapshot.ps1
#   UpdateDesktopNativeSnapshot.sh <-> Dist\UpdateDesktopNativeSnapshot.cmd + UpdateSnapshot.ps1
#   UpdateCliNativeSnapshot.sh   <-> Dist\UpdateCliNativeSnapshot.cmd   + UpdateSnapshot.ps1
#
# 那 8 个 .sh 都是本文件的薄壳（与 .cmd 一样只负责固定「通道 + 形态」），
# 逻辑只在这里一份，避免八处漂移。也可以直接调用：
#
#   ./AhaUpdate.sh dev      desktop-jvm
#   ./AhaUpdate.sh snapshot cli-native --pr 381
#
# 通道（channel）：
#   dev       dev 线预发行包（tag 形如 V0.1.5.00380-aha-desktop-jvm），即「最新构建包」
#   snapshot  PR 快照包（tag 形如 V0.1.5.00381.10191530-AHA-Desktop-JVM），即「快照包」
#
# 落地语义（与 PowerShell 版一致）：
#   JVM 便携包   解压到暂存目录 -> 把「去版本号」的入口**复制**到落地根
#                （aha-desktop.jar / AhaDesktop.sh / aha-cli.jar / AhaCli.sh…），
#                然后删掉暂存目录：命令因此与版本无关（issue #218 的平铺形态）。
#   原生包       解压后把可执行文件**覆盖**到落地根的同名文件，旧版留 <名字>.bak。
#   下载的 zip   默认**保留**在落地根（便于离线重装；不需要可删）；
#                加 --clean 则在整个更新确认完成后（落地 + 清暂存 + 写状态都成功）才删除它。
#
# 退出码：0 = 成功（含「已是最新」）；1 = 失败。
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UA="AHA-Update-sh"
STAGING_ROOT=".aha-updates"

CHANNEL=""
KIND=""
LIST=0
DRY_RUN=0
FORCE=0
NO_INSTALL=0
KEEP_EXTRACT=0
CLEAN=0
JDK="25"
PLATFORM=""
TAG=""
OUT=""
REPO="ACANX/AHA"
PR=""

say()  { printf '%s\n' "$*"; }
warn() { printf '[注意] %s\n' "$*" >&2; }
die()  { printf '[错误] %s\n' "$*" >&2; exit 1; }

usage() {
    cat <<'USAGE'
用法：AhaUpdate.sh <通道> <形态> [选项]

  通道   dev | snapshot
  形态   desktop-jvm | cli-jvm | desktop-native | cli-native

选项：
  --list             只列出可选版本，不下载
  --dry-run          只预览：会下载哪个包、落到哪里
  --force            忽略「本地已是最新」，强制重下
  --jdk 25|27        JDK 段（JVM 线 jdk<N>；原生线 graalvm<N>），默认 25
  --platform <标签>  平台标签（默认按本机：ubuntu-amd64 / macos-aarch64）
  --tag <tag>        指定 release tag（默认取最新）
  --out <目录>       落地目录（默认本脚本所在目录）
  --repo <o/r>       仓库（默认 ACANX/AHA）
  --no-install       只下载并解压，不平铺 / 不覆盖任何既有文件
  --keep-extract     落地后保留解压目录（默认删掉，入口已在落地根）
  --clean            更新确认完成后删除下载的 zip（默认保留，便于离线重装）
  --pr <N>           快照专用：只取某个 PR 的快照（PR 号就在版本段里）
  -h, --help         本帮助

例：
  ./UpdateDesktopJvm.sh --list
  ./UpdateDesktopJvm.sh                 # 取最新一版桌面端 JVM 便携包
  ./UpdateCliNative.sh --dry-run
  ./UpdateDesktopJvmSnapshot.sh --pr 381
USAGE
    exit 0
}

# ── 形态表：把 kind 拆成各处要用的名字 ────────────────────────────────────────
# PRODUCT/FORM : desktop|cli / jvm|native
# DEV_TAG      : dev 线 tag 后缀（小写，与 ChannelContract.tag(BETA, …) 一致）
# ASSET_PRE    : 资产名前缀（issue #269 新文法）
# EXE          : 包内可执行文件名（解压后的源名，Linux / macOS 无后缀）
# ALIAS        : 落地名 —— 解压出来的二进制**重命名**成它再存进落地根（cli-native → aha,
#                desktop-native → AHADesktop）。落地根不再保留包内原名，一个程序只有一个名字。
# FLAT         : JVM 包要平铺到落地根的入口（去版本号）
# SNAP_TARGETS : 快照 tag 里的目标拼写（含旧拼写，见 SnapshotPlan.py）
kind_meta() {
    PRODUCT=""; FORM=""; DEV_TAG=""; ASSET_PRE=""; EXE=""; ALIAS=""; FLAT=(); SNAP_TARGETS=()
    case "$1" in
        desktop-jvm)
            PRODUCT=desktop; FORM=jvm; DEV_TAG="-aha-desktop-jvm"
            ASSET_PRE="aha-desktop-jvm-"
            FLAT=(aha-desktop.jar AhaDesktop.sh AhaDesktop.bat)
            SNAP_TARGETS=("Desktop-JVM" "Desktop") ;;
        cli-jvm)
            PRODUCT=cli; FORM=jvm; DEV_TAG="-aha-cli-jvm"
            ASSET_PRE="aha-cli-jvm-"
            FLAT=(aha-cli.jar AhaCli.sh AhaCli.bat)
            SNAP_TARGETS=("Cli-JVM" "Cli") ;;
        desktop-native)
            PRODUCT=desktop; FORM=native; DEV_TAG="-aha-desktop-native"
            ASSET_PRE="aha-desktop-native-"; EXE="aha-desktop-native"; ALIAS="AHADesktop"
            SNAP_TARGETS=("Desktop-Native" "NativeDesktop") ;;
        cli-native)
            PRODUCT=cli; FORM=native; DEV_TAG="-aha-cli-native"
            ASSET_PRE="aha-cli-native-"; EXE="aha-cli-native"; ALIAS="aha"
            SNAP_TARGETS=("Cli-Native" "NativeCli") ;;
        *) die "未知形态：$1（可选 desktop-jvm / cli-jvm / desktop-native / cli-native）" ;;
    esac
    if [ "$FORM" = jvm ]; then RUNTIME="jdk${JDK}"; else RUNTIME="graalvm${JDK}"; fi
}

# ── 本机平台标签（与发行矩阵同名）─────────────────────────────────────────────
detect_platform() {
    local os arch
    os="$(uname -s)"; arch="$(uname -m)"
    case "$os" in
        Linux)
            case "$arch" in
                x86_64 | amd64) PLATFORM="${PLATFORM:-ubuntu-amd64}" ;;
                aarch64 | arm64) PLATFORM="${PLATFORM:-ubuntu-aarch64}" ;;
                *) die "不认识的 Linux 架构：$arch（请用 --platform 指定）" ;;
            esac ;;
        Darwin)
            case "$arch" in
                arm64 | aarch64) PLATFORM="${PLATFORM:-macos-aarch64}" ;;
                x86_64) PLATFORM="${PLATFORM:-macos-x64}" ;;
                *) die "不认识的 macOS 架构：$arch（请用 --platform 指定）" ;;
            esac ;;
        *) die "本脚本只支持 Linux / macOS；Windows 请用 Dist\\Update*.cmd（当前：$os）" ;;
    esac
}

need_tools() {
    local missing=() t
    for t in curl unzip python3; do
        command -v "$t" >/dev/null 2>&1 || missing+=("$t")
    done
    [ ${#missing[@]} -eq 0 ] || \
        die "缺少依赖：${missing[*]}（Ubuntu: sudo apt install curl unzip python3）"
}

# ── GitHub API（未认证 60 次/小时；有 GITHUB_TOKEN / GH_TOKEN 则带上）──────────
fetch_releases() {
    local url="https://api.github.com/repos/${REPO}/releases?per_page=100"
    local -a hdrs=(-H "User-Agent: ${UA}" -H "Accept: application/vnd.github+json")
    if [ -n "${GITHUB_TOKEN:-}" ]; then
        hdrs+=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
    elif [ -n "${GH_TOKEN:-}" ]; then
        hdrs+=(-H "Authorization: Bearer ${GH_TOKEN}")
    fi
    curl -fL -sS --max-time 60 "${hdrs[@]}" "$url" || die \
        "查询 GitHub 发布页失败：网络不通，或未认证限流（60 次/小时，设置 GITHUB_TOKEN 可提高额度）"
}

# ── dev 线选版 ────────────────────────────────────────────────────────────────
# 只看「全局最新一版」；该版缺本平台资产就响亮失败，不静默降级（与 UpdateJvm.ps1 同一口径）。
# 输出 TSV：OK <tag> <version> <asset> <size> <url> <digest>
#           MISSING <version> <说明>
dev_pick() {
    local mode="$1" pyf rc
    pyf="$(mktemp "${TMPDIR:-/tmp}/aha-dev-pick.XXXXXX")" || die "无法创建临时文件"
    cat > "$pyf" <<'PY'
import json, sys

kind, jdk, platform, tag_arg, mode = sys.argv[1:6]
releases = json.load(sys.stdin)

SUFFIX = {
    "desktop-jvm": ["-aha-desktop-jvm", "-aha-jvm"],
    "cli-jvm": ["-aha-cli-jvm", "-aha-jvm"],
    "desktop-native": ["-aha-desktop-native"],
    "cli-native": ["-aha-cli-native"],
}[kind]
ANY = ["-aha-desktop-jvm", "-aha-cli-jvm", "-aha-jvm",
       "-aha-desktop-native", "-aha-cli-native"]
PLATFORMS_ALL = ["ubuntu-amd64", "windows-amd64", "macos-aarch64"]


def version_of(tag, suffixes):
    for s in suffixes:
        if tag.endswith(s):
            body = tag[: -len(s)]
            if body[:1] in ("V", "v"):
                body = body[1:]
            if body and all(ch.isdigit() or ch == "." for ch in body):
                return body
    return None


def asset_names(version):
    """按命名规则拼出候选名，再精确匹配：比「前缀 + 后缀」猜更稳，旧名也在这里兜底。"""
    legacy = "-jdk27" if jdk == "27" else ""
    names = []
    if kind == "cli-jvm":
        for p in [platform] + [x for x in PLATFORMS_ALL if x != platform]:
            names.append(f"aha-cli-jvm-{version}-{p}-jdk{jdk}.zip")
        names.append(f"aha-cli-{version}{legacy}.zip")
    elif kind == "desktop-jvm":
        names.append(f"aha-desktop-jvm-{version}-{platform}-jdk{jdk}.zip")
        names.append(f"aha-desktop-{version}-{platform}{legacy}.zip")
        if platform == "macos-aarch64":  # 2026-10-17 之前架构段写作 arm64（issue #287）
            names.append(f"aha-desktop-{version}-macos-arm64{legacy}.zip")
    elif kind == "desktop-native":
        names.append(f"aha-desktop-native-{version}-{platform}-graalvm{jdk}.zip")
    else:
        for p in [platform] + [x for x in PLATFORMS_ALL if x != platform]:
            names.append(f"aha-cli-native-{version}-{p}-graalvm{jdk}.zip")
    return names


def published(r):
    return r.get("published_at") or r.get("created_at") or ""


def emit(tag, version, a):
    print("\t".join(["OK", tag, version, a["name"], str(a.get("size", 0)),
                     a["browser_download_url"], a.get("digest") or ""]))


rows = [r for r in releases if not r.get("draft")]
rows.sort(key=published, reverse=True)

mine = [(version_of(r.get("tag_name", ""), SUFFIX), r) for r in rows]
mine = [(v, r) for v, r in mine if v]

if tag_arg:
    hit = next(((v, r) for v, r in mine if r["tag_name"] == tag_arg), None)
    if not hit:
        die = f"{tag_arg} 不是本形态的 dev 线 tag（后缀应为 {' 或 '.join(SUFFIX)}）"
        if version_of(tag_arg, SUFFIX):
            # 该 tag 合法但不在列表里（可能被 --out 以外的方式指定）：按它拼资产名，别处无从校验
            die = f"发布页上找不到 {tag_arg}"
        print(f"MISSING\t-\t{die}")
        sys.exit(0)
    version, rel = hit
    assets = {a["name"]: a for a in rel.get("assets", []) if a.get("state") == "uploaded"}
    for want in asset_names(version):
        if want in assets:
            emit(rel["tag_name"], version, assets[want])
            sys.exit(0)
    print(f"MISSING\t{version}\t{rel['tag_name']} 里没有 {asset_names(version)[0]}")
    sys.exit(0)

if not mine:
    print(f"MISSING\t-\t发布页上没有任何 dev 线 release（tag 后缀 {' 或 '.join(SUFFIX)}）")
    sys.exit(0)

if mode == "list":
    shown = 0
    for v, r in mine:
        assets = {a["name"]: a for a in r.get("assets", []) if a.get("state") == "uploaded"}
        hit = next((assets[w] for w in asset_names(v) if w in assets), None)
        if hit:
            emit(r["tag_name"], v, hit)
            shown += 1
        if shown >= 10:
            break
    if shown == 0:
        print(f"MISSING\t-\t没有匹配 jdk{jdk} / {platform} 的资产")
    sys.exit(0)

# 默认：全局最新版本（任一形态）——本形态在该版本里必须真有资产
latest = version_of(rows[0].get("tag_name", ""), ANY) if rows else None
latest = latest or mine[0][0]
for v, r in mine:
    if v != latest:
        continue
    assets = {a["name"]: a for a in r.get("assets", []) if a.get("state") == "uploaded"}
    hit = next((assets[w] for w in asset_names(v) if w in assets), None)
    if hit:
        emit(r["tag_name"], v, hit)
        sys.exit(0)

actual = []
for v, r in mine:
    if v == latest:
        actual += [a["name"] for a in r.get("assets", [])]
print("\t".join(["MISSING", latest,
                 f"最新一版 {latest} 里没有本平台资产（要的是 {asset_names(latest)[0]}）；"
                 f"该版实际有：{', '.join(actual) if actual else '（没有任何资产）'}。"
                 f"可试 --jdk 27 / --list / --tag <tag>"]))
PY
    python3 "$pyf" "$KIND" "$JDK" "$PLATFORM" "$TAG" "$mode"
    rc=$?
    rm -f "$pyf"
    return $rc
}

# ── 快照选版 ──────────────────────────────────────────────────────────────────
# tag = V<版本>-AHA-<Target>[-SNAPSHOT]；版本段 <基线>.<PR 补零 5 位>.<MMddHHmm>，
# 按末尾时间戳（再按发布时间）从新到旧排序（与 UpdateSnapshot.ps1 同一口径）。
snapshot_pick() {
    local mode="$1" pyf rc
    pyf="$(mktemp "${TMPDIR:-/tmp}/aha-snap-pick.XXXXXX")" || die "无法创建临时文件"
    cat > "$pyf" <<'PY'
import json, re, sys

kind, jdk, platform, pr_arg, mode = sys.argv[1:6]
targets = sys.argv[6:]
releases = json.load(sys.stdin)

TAG_RE = re.compile(r"^V(?P<version>[0-9][0-9.]*)-AHA-(?P<target>[A-Za-z]+(?:-[A-Za-z]+)*)(?:-SNAPSHOT)?$")
PREFIX = {
    "desktop-jvm": "aha-desktop-jvm-",
    "cli-jvm": "aha-cli-jvm-",
    "desktop-native": "aha-desktop-native-",
    "cli-native": "aha-cli-native-",
}[kind]
PLATFORMS_ALL = ["ubuntu-amd64", "windows-amd64", "macos-aarch64"]
# CLI-JVM 内容平台无关：本平台优先，再回退（与 dev 线同一口径）
PLATFORMS = ([platform] + [x for x in PLATFORMS_ALL if x != platform]
             if kind == "cli-jvm" else [platform])


def published(r):
    return r.get("published_at") or r.get("created_at") or ""


def pick_asset(rel):
    for p in PLATFORMS:
        pat = re.compile(rf"-{re.escape(p)}-(?:jdk|graalvm){jdk}\.zip$")
        for a in rel.get("assets", []):
            name = a.get("name", "")
            if (a.get("state") == "uploaded" and name.startswith(PREFIX)
                    and pat.search(name)
                    and not name.endswith(("-report.zip", "-Report.zip"))):
                return a
    return None


def emit(item, a):
    print("\t".join(["OK", item["rel"]["tag_name"], item["version"], a["name"],
                     str(a.get("size", 0)), a["browser_download_url"],
                     a.get("digest") or ""]))


items = []
for r in releases:
    if r.get("draft"):
        continue
    m = TAG_RE.match(r.get("tag_name", ""))
    if not m or m.group("target") not in targets:
        continue
    version = m.group("version")
    parts = version.split(".")
    pr_num = int(parts[-2]) if len(parts) >= 2 and parts[-2].isdigit() else 0
    if pr_arg and pr_num != int(pr_arg):
        continue
    items.append({"rel": r, "version": version, "pr": pr_num,
                  "stamp": parts[-1], "asset": pick_asset(r)})

items.sort(key=lambda i: (i["stamp"], published(i["rel"])), reverse=True)

if not items:
    hint = f"PR #{pr_arg} 的" if pr_arg else ""
    print(f"MISSING\t-\t发布页上没有{hint}{' 或 '.join(targets)} 快照包（jdk{jdk} / {platform}）")
    sys.exit(0)

if mode == "list":
    shown = 0
    for i in items:
        if i["asset"]:
            emit(i, i["asset"])
            shown += 1
        if shown >= 10:
            break
    if shown == 0:
        print(f"MISSING\t-\t有快照 release，但没有 jdk{jdk} / {platform} 的可用资产")
    sys.exit(0)

first = next((i for i in items if i["asset"]), None)
if not first:
    print(f"MISSING\t{items[0]['version']}\t有快照 release，但没有 jdk{jdk} / {platform} 的可用资产")
    sys.exit(0)
emit(first, first["asset"])
PY
    python3 "$pyf" "$KIND" "$JDK" "$PLATFORM" "$PR" "$mode" "${SNAP_TARGETS[@]}"
    rc=$?
    rm -f "$pyf"
    return $rc
}

# ── 下载（同尺寸复用；有 digest 则校验 sha256）────────────────────────────────
download() {
    local asset="$1" size="$2" url="$3" digest="$4" zip="$5"
    if [ -f "$zip" ] && [ "$(stat -c %s "$zip" 2>/dev/null || stat -f %z "$zip")" = "$size" ]; then
        say "复用已下载的包：$asset"
    else
        say "下载中：$url"
        local part="${zip}.part"
        if ! curl -fL -sS --max-time 1800 -A "$UA" -o "$part" "$url"; then
            rm -f "$part"
            die "下载失败（网络问题，或 GitHub 限流——设置 GITHUB_TOKEN 可提高额度）"
        fi
        mv -f "$part" "$zip"
        say "已下载：$(du -h "$zip" 2>/dev/null | cut -f1)（$asset）"
    fi
    case "$digest" in
        sha256:*)
            local want got
            want="${digest#sha256:}"
            got="$(sha256sum "$zip" | cut -d' ' -f1)"
            [ "$want" = "$got" ] || { rm -f "$zip"; die "sha256 校验失败，文件已删除，请重试（$asset）"; }
            say "  sha256 校验通过" ;;
    esac
}

# ── 解压到暂存目录 ────────────────────────────────────────────────────────────
extract_to_staging() {
    local zip="$1" staging="$2"
    rm -rf "$staging"
    mkdir -p "$staging" || die "无法创建暂存目录：$staging"
    python3 - "$zip" "$staging" <<'PY' || die "解压失败：$zip"
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    z.extractall(sys.argv[2])
PY
    say "解压完成：$(find "$staging" -type f | wc -l | tr -d ' ') 个文件 -> $staging"
}

# ── 落地：JVM 平铺入口 / 原生覆盖可执行文件（旧版留 .bak）─────────────────────
install_from() {
    local staging="$1"
    local -a done_list=() missing_list=()

    if [ "$FORM" = jvm ]; then
        local entry src
        for entry in "${FLAT[@]}"; do
            # 只看包根（不加 -R）：旧形态的包把脚本放在 bin/ 下，那不是平铺入口。
            src="$staging/$entry"
            if [ ! -f "$src" ]; then
                missing_list+=("$entry")
                continue
            fi
            cp -f "$src" "$OUT/$entry" || die \
                "平铺 $entry 到 $OUT 失败（最常见原因：该文件正被运行中的实例占用——请先关闭再重试）"
            case "$entry" in
                *.sh) chmod +x "$OUT/$entry" 2>/dev/null || true ;;
            esac
            done_list+=("$entry")
        done
        if [ ${#done_list[@]} -gt 0 ]; then
            say "已平铺到 $OUT：${done_list[*]}"
            [ ${#missing_list[@]} -eq 0 ] || \
                say "      （包根没有 ${missing_list[*]}，未平铺）"
        else
            warn "未平铺：包根没有 ${missing_list[*]}"
            say  '       旧形态的包（包根没有单体 jar 与 .sh）平铺不了；请取 0.1.3.00219 及以后的版本。'
        fi
    else
        # 原生包：解压出来的二进制**重命名**为 ALIAS 再落地（aha / AHADesktop），
        # 落地根不再出现包内原名（aha-cli-native / aha-desktop-native）。
        local src="$staging/$EXE" target="${ALIAS:-$EXE}"
        [ -f "$src" ] || die "包里没有可执行文件 $EXE（资产名与包内容不符？）"
        if [ "$NO_INSTALL" -eq 1 ]; then
            return 0
        fi
        local backed=0
        if [ -f "$OUT/$target" ]; then
            if cp -f "$OUT/$target" "$OUT/$target.bak"; then
                backed=1
            else
                warn "旧版备份失败：$OUT/$target.bak"
            fi
        fi
        cp -f "$src" "$OUT/$target" || die \
            "落地 $target 失败（最常见原因：该程序正在运行——请先关闭再重试）"
        chmod 755 "$OUT/$target" || true
        # 只有真的备份过才提 .bak —— 首次安装时说「旧版留存」是假话。
        local kept=""
        [ "$backed" -eq 1 ] && kept="（旧版留存：$target.bak）"
        if [ "$target" = "$EXE" ]; then
            say "已覆盖：$OUT/$target$kept"
        else
            say "已落地（重命名 $EXE → $target）：$OUT/$target$kept"
        fi
    fi
}

read_state_tag() {
    local f="$OUT/$STATE_FILE"
    [ -f "$f" ] || return 0
    python3 - "$f" <<'PY' 2>/dev/null || true
import json, sys
try:
    with open(sys.argv[1], encoding="utf-8") as f:
        print(json.load(f).get("tag", ""))
except Exception:
    print("")
PY
}

write_state() {
    TAG_S="$1" VERSION_S="$2" ASSET_S="$3" KIND_S="$KIND" \
    STATE_PATH="$OUT/$STATE_FILE" KEPT="$([ -d "$STAGING_DIR" ] && echo 1 || echo 0)" \
    python3 <<'PY' || warn "状态文件写入失败（不影响使用）：$OUT/$STATE_FILE"
import json, os, time
with open(os.environ["STATE_PATH"], "w", encoding="utf-8") as f:
    json.dump({
        "tag": os.environ["TAG_S"],
        "kind": os.environ["KIND_S"],
        "version": os.environ["VERSION_S"],
        "asset": os.environ["ASSET_S"],
        "installedAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "kept": os.environ["KEPT"] == "1",
    }, f, ensure_ascii=False, indent=2)
PY
}

# ── 主流程 ────────────────────────────────────────────────────────────────────
main() {
    local channel="" kind=""
    while [ $# -gt 0 ]; do
        case "$1" in
            dev | snapshot)
                [ -z "$channel" ] || die "重复的通道：$1"; channel="$1"; shift ;;
            desktop-jvm | cli-jvm | desktop-native | cli-native)
                [ -z "$kind" ] || die "重复的形态：$1"; kind="$1"; shift ;;
            -h | --help) usage ;;
            --list) LIST=1; shift ;;
            --dry-run) DRY_RUN=1; shift ;;
            --force) FORCE=1; shift ;;
            --no-install) NO_INSTALL=1; shift ;;
            --keep-extract) KEEP_EXTRACT=1; shift ;;
            --clean) CLEAN=1; shift ;;
            --jdk) JDK="${2:-}"; [ -n "$JDK" ] || die "--jdk 需要值（25 / 27）"; shift 2 ;;
            --jdk=*) JDK="${1#*=}"; shift ;;
            --platform) PLATFORM="${2:-}"; [ -n "$PLATFORM" ] || die "--platform 需要值"; shift 2 ;;
            --platform=*) PLATFORM="${1#*=}"; shift ;;
            --tag) TAG="${2:-}"; [ -n "$TAG" ] || die "--tag 需要值"; shift 2 ;;
            --tag=*) TAG="${1#*=}"; shift ;;
            --out) OUT="${2:-}"; [ -n "$OUT" ] || die "--out 需要值"; shift 2 ;;
            --out=*) OUT="${1#*=}"; shift ;;
            --repo) REPO="${2:-}"; [ -n "$REPO" ] || die "--repo 需要值"; shift 2 ;;
            --repo=*) REPO="${1#*=}"; shift ;;
            --pr) PR="${2:-}"; [ -n "$PR" ] || die "--pr 需要值"; shift 2 ;;
            --pr=*) PR="${1#*=}"; shift ;;
            *) die "不认识的参数：$1（用 --help 看用法）" ;;
        esac
    done

    [ -n "$channel" ] || die "缺少通道（dev / snapshot）。用 --help 看用法"
    [ -n "$kind" ] || die "缺少形态（desktop-jvm / cli-jvm / desktop-native / cli-native）"
    if [ "$channel" != snapshot ] && [ -n "$PR" ]; then
        die "--pr 只对 snapshot 通道有效"
    fi

    CHANNEL="$channel"; KIND="$kind"
    need_tools
    detect_platform
    kind_meta "$KIND"
    OUT="${OUT:-$SCRIPT_DIR}"
    mkdir -p "$OUT" || die "落地目录不可写：$OUT"
    STAGING_DIR="$OUT/$STAGING_ROOT"
    STATE_FILE=".aha-${KIND}.json"
    [ "$CHANNEL" = snapshot ] && STATE_FILE=".aha-snapshot-${KIND}.json"

    say "仓库：$REPO    通道：$CHANNEL    形态：$KIND    JDK：$JDK    平台：$PLATFORM"
    say '正在查询 GitHub 发布页，请稍候……'

    local json pick
    json="$(fetch_releases)" || exit 1
    if [ "$CHANNEL" = dev ]; then
        pick="$(printf '%s' "$json" | dev_pick "$([ "$LIST" -eq 1 ] && echo list || echo pick)")"
    else
        pick="$(printf '%s' "$json" | snapshot_pick "$([ "$LIST" -eq 1 ] && echo list || echo pick)")"
    fi
    [ -n "$pick" ] || die "查询结果为空（API 返回了无法解析的内容）"

    local status="${pick%%$'\t'*}"
    if [ "$LIST" -eq 1 ]; then
        if [ "$status" = MISSING ]; then
            say ''
            say "没有可用的包：$(printf '%s' "$pick" | cut -f3)"
            exit 0
        fi
        say ''
        say "$KIND 最近的可用版本（$CHANNEL 线 / jdk$JDK / $PLATFORM）："
        printf '%s\n' "$pick" | while IFS=$'\t' read -r st tag version asset size url _d; do
            [ "$st" = OK ] || continue
            printf '  %-46s %-54s %6s MB\n' "$tag" "$asset" \
                "$(awk -v s="$size" 'BEGIN{printf "%.1f", s/1048576}')"
        done
        exit 0
    fi

    if [ "$status" = MISSING ]; then
        die "$(printf '%s' "$pick" | cut -f3)"
    fi

    local tag version asset size url digest
    IFS=$'\t' read -r _ tag version asset size url digest <<<"$pick"

    say ''
    say "目标版本：$tag（$version）"
    say "资产：$asset（$(awk -v s="$size" 'BEGIN{printf "%.1f", s/1048576}') MB）"

    if [ "$DRY_RUN" -eq 1 ]; then
        say ''
        say '[dry-run] 只预览，不下载、不解压：'
        say "  下载：$url"
        say "  落地：$OUT"
        exit 0
    fi

    # 已是最新？判据落在「落地根真有的东西」上，而不是只看状态文件。
    if [ "$FORCE" -eq 0 ] && [ "$(read_state_tag)" = "$tag" ] && [ "$NO_INSTALL" -eq 0 ]; then
        if [ "$FORM" = jvm ]; then
            local missing=0 e
            for e in "${FLAT[@]}"; do [ -f "$OUT/$e" ] || missing=1; done
        elif [ -f "$OUT/${ALIAS:-$EXE}" ]; then
            # 原生包只认落地名（aha / AHADesktop）；包内原名不再存在于落地根
            local missing=0
        else
            local missing=1
        fi
        if [ "$missing" -eq 0 ]; then
            say "✅ 本地已经是 $version，无需更新（加 --force 可强制重下）"
            exit 0
        fi
        warn "状态说已装 $version，但落地根缺入口，重新取一次"
    fi

    local zip="$OUT/$asset"
    download "$asset" "$size" "$url" "$digest" "$zip"

    local staging="$STAGING_DIR/${KIND}-${version}"
    extract_to_staging "$zip" "$staging"

    if [ "$NO_INSTALL" -eq 1 ]; then
        say "（--no-install：只解压到 $staging，未改动落地根）"
    else
        install_from "$staging"
        if [ "$KEEP_EXTRACT" -eq 0 ]; then
            rm -rf "$staging"
            say "已清掉解压目录：$staging"
            rmdir "$STAGING_DIR" 2>/dev/null || true
        fi
    fi

    write_state "$tag" "$version" "$asset"

    say ''
    say "✅ 已取件：$version"
    # 删 zip 放在**最后**：走到这里意味着下载、校验、解压、落地、清暂存、写状态都已成功。
    # 失败路径在各自的位置 die 掉，不会执行到这里，所以不会出现「包删了但没装成」。
    if [ "$CLEAN" -eq 1 ]; then
        if rm -f "$zip"; then
            say "已删除下载包：$zip"
        else
            warn "下载包删除失败（不影响已完成的更新）：$zip"
        fi
    else
        say "下载包保留在：$zip（便于离线重装；不需要可删；加 --clean 可自动删除）"
    fi
    if [ "$NO_INSTALL" -eq 0 ]; then
        case "$KIND" in
            desktop-jvm) say "运行：$OUT/AhaDesktop.sh（已平铺到落地根，换版本不必改命令）" ;;
            cli-jvm) say "运行：$OUT/AhaCli.sh（已平铺到落地根，换版本不必改命令）" ;;
            desktop-native | cli-native) say "运行：$OUT/${ALIAS:-$EXE}" ;;
        esac
    fi
    exit 0
}

main "$@"
