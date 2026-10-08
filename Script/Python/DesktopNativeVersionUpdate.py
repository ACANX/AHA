#!/usr/bin/env python3
r"""AHA 桌面端原生镜像一键更新（GitHub Releases -> Dist/）。

用途：把「去发布页找最新的 V*-aha-desktop-native 预发行版、下载本平台 zip、
解压、把可执行文件放到 Dist/」这一串手工步骤压成一条命令（Windows 为例）：

    python3 Script\Python\DesktopNativeVersionUpdate.py

（POSIX 下把路径分隔符换成 `/`：`python3 Script/Python/DesktopNativeVersionUpdate.py`；
若本机命令是 `python` 而非 `python3`，换成 `python` 即可。）

流程（每一步都会打印出来，可加 --dry-run 先预览）：

  1. 取最新版本 tag（形如 V0.1.1.00040-aha-desktop-native），解析出版本 0.1.1
     与构建号 00040；
  2. 按「系统-架构-jdk」挑出当前平台的包
     `AHA-Desktop-Native-<版本>-<系统>-<架构>-jdk<JDK>.zip`；
  3. 下载到 `Dist/`（断点续传 + sha256 校验）；
  4. 解压出可执行文件，覆盖 `Dist/aha-desktop-native.exe`（旧版留一份 `.bak`）。

为什么「取最新版本」不止一条路径：

  * GitHub API 最全：一次拿到全部 release、资产列表与 sha256 digest；但未认证时
    按 IP 限流（60 次/小时）。带 `GITHUB_TOKEN` / `GH_TOKEN` 环境变量可提高限额；
  * `releases.atom` 是纯 XML，限额宽松，但只有最近若干条且不含资产信息；
  * `releases` 页面 HTML 兜底。

  因此默认 auto 模式按 api -> atom -> page 依次尝试，任一成功即止。三条路径都能
  定位到同一个下载地址，差别只是「拿到资产清单的快慢」与「能否校验 sha256」。

为什么「最新 tag」不等于「能下载的那个」：

  * 原生镜像构建是三条腿（ubuntu / windows / macOS）并发，先编完的平台先上传，
    release 又是**先建后传**。于是在某个时刻「最新 tag 的 release 存在、但缺本平台
    的包」是常态。脚本因此按版本降序**逐个 release** 找「真的存在本平台资产的那个」，
    而不是只认第一个 tag——否则用户会在版本刚出来的几分钟里反复失败。

为什么可执行文件用「覆盖 + 备份」而不是「直接原地解压」：

  * 原地解压会先删旧文件，中途失败就两头落空；这里先解压到同目录的 `.new`，
    再用 os.replace 原子替换，失败时旧版仍可用；
  * Windows 上可执行文件正在运行时无法替换，脚本会明确提示「先关掉正在运行的程序」，
    而不是抛一段看不懂的 PermissionError。

退出码：0 = 已是最新或更新成功；1 = 失败（网络 / 找不到资产 / 替换被占用）；130 = 用户中断。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform as platform_module
import re
import shutil
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ElementTree
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

# ── 常量 ─────────────────────────────────────────────────────────────────────

#: 仓库根（本脚本位于 Script/Python/ 下）
ROOT = Path(__file__).resolve().parents[2]
DEFAULT_REPO = "ACANX/AHA"
DEFAULT_JDK = "25"
#: 已知的 JDK 分支：未显式指定 --jdk 时按此顺序兜底（见 DesktopNative.yml 的 matrix）
KNOWN_JDKS = ("25", "27")
#: 预发行版 tag 的后缀（见 DesktopNative.yml 的 native-publish 作业）
TAG_SUFFIX = "-aha-desktop-native"
#: 发布资产的前缀
ASSET_PREFIX = "AHA-Desktop-Native-"
#: 记录「本地装的是哪一版」的清单文件（落在 Dist/，该目录已被 .gitignore 忽略）
STATE_FILE = ".aha-desktop-native.json"
#: 可执行文件名（发布包内就是这个 basename）
EXECUTABLE_NAMES = ("aha-desktop-native.exe", "aha-desktop-native")
CHUNK = 1 << 20
USER_AGENT = "AHA-DesktopNativeVersionUpdate/1.0 (+https://github.com/ACANX/AHA)"

#: 文档 / 帮助里统一展示的用法（Windows 路径分隔符）。写成 raw 字符串，
#: 避免 `\P` 被当成转义序列（Python 会给 SyntaxWarning）。
USAGE_CMD = r"python3 Script\Python\DesktopNativeVersionUpdate.py"

#: tag -> 版本。构建号补零到 5 位（DesktopNative.yml 用 printf '%05d'）。
TAG_RE = re.compile(rf"^[Vv]?(\d+(?:\.\d+)*?)\.(\d+){TAG_SUFFIX}$")

#: 本机平台 -> 发布用的平台标签（顺序即优先级）。注意 Linux 对应的标签是 ubuntu-*，
#: 不是 linux-*；macOS 当前只发 arm64。
OS_LABELS = {
    "win32": ("windows-amd64", "windows-arm64"),
    "linux": ("ubuntu-amd64", "ubuntu-arm64"),
    "darwin": ("macos-arm64", "macos-amd64"),
}
ARCH_ALIASES = {
    "x86_64": "amd64",
    "amd64": "amd64",
    "i386": "x86",
    "i686": "x86",
    "aarch64": "arm64",
    "arm64": "arm64",
}

ATOM_NS = "{http://www.w3.org/2005/Atom}"


class UpdateError(Exception):
    """可预期的失败：网络不通、找不到资产、替换被占用等。"""


# ── 数据模型 ─────────────────────────────────────────────────────────────────


@dataclass(frozen=True)
class Version:
    """从 tag 解析出的版本：基线 + 构建号。"""

    base: str  # 0.1.1
    build: int  # 40
    text: str  # 0.1.1.00040

    @property
    def sort_key(self) -> tuple[tuple[int, ...], int]:
        return tuple(int(part) for part in self.base.split(".")), self.build


@dataclass
class Asset:
    """release 里的一个下载资产。"""

    name: str
    url: str
    size: int = 0
    digest: str = ""  # "sha256:..."（GitHub API 提供；atom/page 路径取不到）
    state: str = "uploaded"


@dataclass
class Release:
    """一个 release。assets 为空表示「资产清单未知」（atom/page 路径）。"""

    tag: str
    version: Version
    assets: list[Asset] = field(default_factory=list)
    source: str = "api"


# ── 小工具 ───────────────────────────────────────────────────────────────────


def configure_stdio() -> None:
    """让中文输出在 Windows 控制台不因编码炸掉。

    Windows 的默认控制台编码是 GBK/CP936，直接 print 中文以外的字符（如 ✅）会抛
    UnicodeEncodeError。这里改成 UTF-8 + replace：显示可能受终端代码页影响，
    但脚本本身绝不会因为「打了一行字」而失败。
    """
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[union-attr]
        except (AttributeError, ValueError):  # pragma: no cover - 非标准流
            pass


def format_size(num: float) -> str:
    """人类可读的字节数。"""
    for unit in ("B", "KiB", "MiB", "GiB"):
        if abs(num) < 1024 or unit == "GiB":
            return f"{num:.1f} {unit}" if unit != "B" else f"{int(num)} B"
        num /= 1024
    return f"{num:.1f} GiB"


def parse_tag(tag: str) -> Version | None:
    """`V0.1.1.00040-aha-desktop-native` -> Version(0.1.1, 40, 0.1.1.00040)。"""
    match = TAG_RE.match(tag.strip())
    if not match:
        return None
    base, build = match.group(1), match.group(2)
    return Version(base=base, build=int(build), text=f"{base}.{int(build):05d}")


def platform_labels() -> list[str]:
    """本机对应的发布平台标签候选，按优先级排序。"""
    labels = OS_LABELS.get(sys.platform)
    if not labels:
        return []
    machine = platform_module.machine().lower()
    arch = ARCH_ALIASES.get(machine, machine)
    preferred = [label for label in labels if label.endswith(f"-{arch}")]
    return preferred + [label for label in labels if label not in preferred]


def executable_name(label: str) -> str:
    """目标平台上的可执行文件名（由发布平台标签决定，而不是本机 OS）。"""
    return "aha-desktop-native.exe" if label.startswith("windows") else "aha-desktop-native"


def asset_name(version: Version, label: str, jdk: str) -> str:
    """发布资产名：`AHA-Desktop-Native-<版本>-<平台>-jdk<JDK>.zip`。"""
    return f"{ASSET_PREFIX}{version.text}-{label}-jdk{jdk}.zip"


ASSET_RE = re.compile(
    rf"^{re.escape(ASSET_PREFIX)}(?P<version>\d+(?:\.\d+)*)-(?P<label>[A-Za-z0-9_.-]+)-jdk(?P<jdk>\d+)\.zip$"
)


# ── 网络 ─────────────────────────────────────────────────────────────────────


def _headers(url: str, extra: dict[str, str] | None = None) -> dict[str, str]:
    headers = {"User-Agent": USER_AGENT}
    if "api.github.com" in url:
        headers["Accept"] = "application/vnd.github+json"
        # 限流是「按 IP」的：本机若配了 token 就带上，未认证的 60 次/小时实在不够用
        # （脚本本身只需要 1~2 次，但开发时反复跑很容易撞上）。
        token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
        if token:
            headers["Authorization"] = f"Bearer {token}"
    if extra:
        headers.update(extra)
    return headers


def http_open(url: str, timeout: float, extra_headers: dict[str, str] | None = None,
              method: str | None = None):
    """统一的 urlopen：注入 UA / token，并把 HTTPError 收敛成 UpdateError。"""
    request = urllib.request.Request(url, headers=_headers(url, extra_headers), method=method)
    return urllib.request.urlopen(request, timeout=timeout)


def http_get_text(url: str, timeout: float) -> str:
    try:
        with http_open(url, timeout) as response:
            return response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        raise UpdateError(f"请求 {url} 失败：HTTP {error.code} {error.reason}") from error
    except urllib.error.URLError as error:
        raise UpdateError(f"请求 {url} 失败：{error.reason}") from error
    except TimeoutError as error:
        raise UpdateError(f"请求 {url} 超时（{timeout:.0f}s）") from error


def http_head_ok(url: str, timeout: float) -> bool:
    """HEAD 探测资产是否存在（会跟随 302 到对象存储）。"""
    try:
        with http_open(url, timeout, method="HEAD") as response:
            return 200 <= response.status < 300
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError):
        return False


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(CHUNK), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_digest(path: Path, digest: str) -> bool:
    """按 `sha256:<hex>` 形式的 digest 校验；digest 为空视为「无从校验」。"""
    if not digest or not digest.startswith("sha256:"):
        return True
    expected = digest.split(":", 1)[1].strip().lower()
    return sha256_file(path) == expected


# ── 发现 release ─────────────────────────────────────────────────────────────


def api_releases(repo: str, timeout: float) -> list[Release]:
    """GitHub API：最全的一条路径（含资产清单与 digest）。"""
    url = f"https://api.github.com/repos/{repo}/releases?per_page=100"
    try:
        payload = json.loads(http_get_text(url, timeout))
    except json.JSONDecodeError as error:
        raise UpdateError(f"GitHub API 返回的不是 JSON（可能被限流或代理改写）：{error}") from error
    if not isinstance(payload, list):
        raise UpdateError(f"GitHub API 返回了非列表结构：{payload!r}")

    releases: list[Release] = []
    for item in payload:
        if item.get("draft"):
            continue
        tag = item.get("tag_name", "")
        version = parse_tag(tag)
        if not version:
            continue
        assets = [
            Asset(
                name=asset.get("name", ""),
                url=asset.get("browser_download_url", ""),
                size=int(asset.get("size") or 0),
                digest=asset.get("digest") or "",
                state=asset.get("state") or "uploaded",
            )
            for asset in item.get("assets", [])
        ]
        releases.append(Release(tag=tag, version=version, assets=assets, source="api"))

    releases.sort(key=lambda release: release.version.sort_key, reverse=True)
    if not releases:
        raise UpdateError(f"{repo} 的 release 里没有 `*{TAG_SUFFIX}` 形式的 tag")
    return releases


def atom_releases(repo: str, timeout: float) -> list[Release]:
    """releases.atom：纯 XML、限额宽松，但不含资产清单。"""
    text = http_get_text(f"https://github.com/{repo}/releases.atom", timeout)
    try:
        root = ElementTree.fromstring(text)
    except ElementTree.ParseError as error:
        raise UpdateError(f"解析 releases.atom 失败：{error}") from error

    seen: set[str] = set()
    releases: list[Release] = []
    for entry in root.findall(f"{ATOM_NS}entry"):
        link = entry.find(f"{ATOM_NS}link")
        href = link.get("href") if link is not None else ""
        tag = urllib.parse.unquote(href.rsplit("/", 1)[-1]) if href else ""
        version = parse_tag(tag)
        if not version or tag in seen:
            continue
        seen.add(tag)
        releases.append(Release(tag=tag, version=version, assets=[], source="atom"))

    releases.sort(key=lambda release: release.version.sort_key, reverse=True)
    if not releases:
        raise UpdateError(f"releases.atom 里没有 `*{TAG_SUFFIX}` 形式的 tag")
    return releases


def page_releases(repo: str, timeout: float) -> list[Release]:
    """releases 页面 HTML：最后的兜底路径。"""
    html = http_get_text(f"https://github.com/{repo}/releases", timeout)
    pattern = re.compile(r'href="/' + re.escape(repo) + r'/releases/tag/([^"?#]+)"')
    seen: set[str] = set()
    releases: list[Release] = []
    for raw in pattern.findall(html):
        tag = urllib.parse.unquote(raw)
        version = parse_tag(tag)
        if not version or tag in seen:
            continue
        seen.add(tag)
        releases.append(Release(tag=tag, version=version, assets=[], source="page"))

    releases.sort(key=lambda release: release.version.sort_key, reverse=True)
    if not releases:
        raise UpdateError(f"{repo} 的 releases 页面里没有找到 `*{TAG_SUFFIX}` 形式的 tag")
    return releases


def expanded_assets(repo: str, tag: str, timeout: float) -> list[Asset]:
    """从 `releases/expanded_assets/<tag>` 这一小块 HTML 里读资产清单。

    只在「明确指定了 --tag」时用来补 digest —— 正常发现路径不会为了补 digest
    对每个 tag 都发一次请求（那样一次运行要打十几枪）。
    """
    url = f"https://github.com/{repo}/releases/expanded_assets/{urllib.parse.quote(tag)}"
    html = http_get_text(url, timeout)
    assets: list[Asset] = []
    for href in re.findall(r'href="(/[^"]+/releases/download/[^"]+)"', html):
        assets.append(Asset(name=urllib.parse.unquote(href.rsplit("/", 1)[-1]),
                            url=f"https://github.com{href}"))
    return assets


def release_from_tag(repo: str, tag: str, timeout: float) -> Release:
    """--tag 显式指定：解析版本，并尽量补齐资产清单。"""
    version = parse_tag(tag)
    if not version:
        raise UpdateError(f"tag `{tag}` 不认识，期望形如 V0.1.1.00040{TAG_SUFFIX}")
    assets: list[Asset] = []
    try:
        assets = expanded_assets(repo, tag, timeout)
    except UpdateError:
        assets = []  # 补不到就靠按名字探测，不影响主流程
    return Release(tag=tag, version=version, assets=assets, source="tag")


def discover(args: argparse.Namespace) -> list[Release]:
    """按 --source 指定的策略发现候选 release（降序）。"""
    repo, timeout, source = args.repo, args.timeout, args.source
    if args.tag:
        return [release_from_tag(repo, args.tag, timeout)]

    errors: list[str] = []
    if source in ("auto", "api"):
        try:
            return api_releases(repo, timeout)
        except UpdateError as error:
            errors.append(f"API：{error}")
            if source == "api":
                raise
    if source in ("auto", "atom"):
        try:
            return atom_releases(repo, timeout)
        except UpdateError as error:
            errors.append(f"atom：{error}")
            if source == "atom":
                raise
    if source in ("auto", "page"):
        try:
            return page_releases(repo, timeout)
        except UpdateError as error:
            errors.append(f"页面：{error}")
            if source == "page":
                raise
    raise UpdateError("发现最新版本失败 —— " + "；".join(errors))


# ── 选资产 ───────────────────────────────────────────────────────────────────


def match_asset(release: Release, labels: list[str], jdk: str,
                jdk_explicit: bool) -> Asset | None:
    """在已知资产清单里挑选最合适的一个（平台优先，其次 JDK）。"""
    best: tuple[tuple[int, int], Asset] | None = None
    for asset in release.assets:
        if asset.state != "uploaded":
            continue
        match = ASSET_RE.match(asset.name)
        if not match:
            continue
        if match.group("version") != release.version.text:
            continue
        label, asset_jdk = match.group("label"), match.group("jdk")
        if label not in labels:
            continue
        if jdk_explicit and asset_jdk != jdk:
            continue
        score = (labels.index(label), 0 if asset_jdk == jdk else 1)
        if best is None or score < best[0]:
            best = (score, asset)
    return best[1] if best else None


def probe_asset(repo: str, release: Release, labels: list[str], jdk: str,
                jdk_explicit: bool, timeout: float) -> Asset | None:
    """资产清单未知时，按命名规则拼出下载链接并 HEAD 探测。

    链接形如：
      https://github.com/<repo>/releases/download/<tag>/<资产名>
    这正是「从最新 tag 拼出完整下载地址」的那一步。
    """
    jdks = [jdk] if jdk_explicit else [jdk] + [other for other in KNOWN_JDKS if other != jdk]
    for label in labels:
        for candidate_jdk in jdks:
            name = asset_name(release.version, label, candidate_jdk)
            url = f"https://github.com/{repo}/releases/download/{urllib.parse.quote(release.tag)}/{name}"
            if http_head_ok(url, timeout):
                return Asset(name=name, url=url)
    return None


def select(repo: str, releases: list[Release], labels: list[str], jdk: str,
           jdk_explicit: bool, timeout: float) -> tuple[Release, Asset]:
    """按版本降序找「真的存在本平台资产」的那个 release。

    不能只认第一个 tag：release 先建、三条腿的资产后传，最新 tag 缺本平台包是常态。
    """
    for release in releases:
        if release.assets:
            # 清单已知（API / --tag 补齐）：明确没有本平台资产就直接跳到下一个 release，
            # 不做无谓的 URL 猜测——猜一个不存在的链接只会多打一枪。
            asset = match_asset(release, labels, jdk, jdk_explicit)
        else:
            asset = probe_asset(repo, release, labels, jdk, jdk_explicit, timeout)
        if asset:
            return release, asset
    wanted = " / ".join(labels)
    if len(releases) == 1 and releases[0].source == "tag":
        raise UpdateError(f"{releases[0].tag} 里没有 {wanted}（jdk{jdk}）的包")
    raise UpdateError(
        f"最近 {len(releases)} 个 release 里都没有 {wanted}（jdk{jdk}）的包；"
        "可用 --list 查看有哪些平台可下载，或用 --platform / --jdk 指定"
    )


# ── 下载与安装 ───────────────────────────────────────────────────────────────


def download(asset: Asset, dest: Path, timeout: float, redownload: bool = False) -> None:
    """下载资产到 dest，支持断点续传；完成后按 digest 校验（若已知）。

    `redownload` 只影响「本地已有完整包」时的复用判断：置位则无视本地文件重下，
    用于本地包损坏但大小恰好相等的极端情形。
    """
    if dest.exists() and not redownload:
        if asset.size and dest.stat().st_size == asset.size:
            print(f"  复用已下载的包：{dest.name}（{format_size(asset.size)}）")
            return
        if asset.digest and verify_digest(dest, asset.digest):
            print(f"  复用已下载的包：{dest.name}（sha256 校验通过）")
            return

    part = dest.with_name(dest.name + ".part")
    if redownload and part.exists():
        part.unlink()

    start = part.stat().st_size if part.exists() else 0
    headers = {"Range": f"bytes={start}-"} if start else {}
    try:
        response = http_open(asset.url, timeout, headers)
    except urllib.error.HTTPError as error:
        if error.code == 416 and start:
            # Range 越界：多半是上次已经下完，只是没来得及改名
            print("  续传位置越界，重新完整下载")
            part.unlink(missing_ok=True)
            response = http_open(asset.url, timeout)
        else:
            raise UpdateError(
                f"下载失败：HTTP {error.code} {error.reason}（{asset.url}）"
            ) from error
    except urllib.error.URLError as error:
        raise UpdateError(f"下载失败：{error.reason}（{asset.url}）") from error

    with response:
        status = getattr(response, "status", 200)
        length = response.headers.get("Content-Length")
        if status == 206 and start:
            mode = "ab"
            total = start + int(length) if length else (asset.size or 0)
        else:
            if status == 200 and start:
                print("  服务器不支持续传，从头下载")
            mode = "wb"
            start = 0
            total = int(length) if length else (asset.size or 0)

        done = start
        began = time.monotonic()
        with part.open(mode) as handle:
            while True:
                chunk = response.read(CHUNK)
                if not chunk:
                    break
                handle.write(chunk)
                done += len(chunk)
                elapsed = max(time.monotonic() - began, 1e-6)
                speed = (done - start) / elapsed
                if total:
                    percent = done * 100 / total
                    sys.stderr.write(
                        f"\r  下载 {percent:5.1f}%  {format_size(done)}/{format_size(total)}"
                        f"  {format_size(speed)}/s   "
                    )
                else:
                    sys.stderr.write(f"\r  下载 {format_size(done)}  {format_size(speed)}/s   ")
                sys.stderr.flush()
    sys.stderr.write("\r" + " " * 60 + "\r")

    if asset.size and part.stat().st_size != asset.size:
        size = part.stat().st_size
        raise UpdateError(f"下载不完整：期望 {asset.size} 字节，实际 {size} 字节（{part}）")
    if asset.digest and not verify_digest(part, asset.digest):
        part.unlink(missing_ok=True)
        raise UpdateError(f"sha256 校验失败，文件已删除，请重试（{asset.name}）")

    os.replace(part, dest)
    print(f"  已下载：{dest}（{format_size(dest.stat().st_size)}）")


def ensure_replaceable(path: Path) -> None:
    """在下载前探测「目标可执行文件能不能被替换」。

    Windows 上正在运行的 exe 既不能改名也不能覆盖（镜像文件被独占映射）。不先探一下的话，
    用户会在下完 50+ MB 之后才吃到一个 PermissionError，白等一场。这里以「能否以写模式
    打开」为准：Linux 上对运行中的文件也总能打开，因此该检查不会误报。
    """
    if not path.exists():
        return
    try:
        with path.open("r+b"):
            pass
    except OSError as error:
        raise UpdateError(
            f"{path} 正在被占用（{error}）——请先关闭已打开的 AHA 桌面端，再重试"
        ) from error


def extract_executable(archive_path: Path, target: Path) -> None:
    """从发布包里解出可执行文件，原子替换到 target。

    只取包内的可执行文件，不整包解开：包内还有 README / CHANGELOG / 构建参数，
    它们属于「包」而不是「可运行的 exe」，铺到 Dist/ 只会造成混乱。
    """
    with zipfile.ZipFile(archive_path) as archive:
        entries = [
            name
            for name in archive.namelist()
            if not name.endswith("/")
            and Path(name).name.startswith("aha-desktop-native")
            and not Path(name).name.lower().endswith((".txt", ".json", ".md", ".html"))
        ]
        if not entries:
            raise UpdateError(
                f"{archive_path.name} 里没有 aha-desktop-native 可执行文件；"
                f"包内内容：{archive.namelist()}"
            )
        entry = sorted(entries, key=lambda name: (Path(name).name not in EXECUTABLE_NAMES,
                                                  len(name)))[0]
        staging = target.with_name(target.name + ".new")
        staging.unlink(missing_ok=True)
        with archive.open(entry) as source, staging.open("wb") as sink:
            shutil.copyfileobj(source, sink, CHUNK)

    if os.name != "nt" or not target.name.endswith(".exe"):
        try:
            staging.chmod(0o755)
        except OSError:  # pragma: no cover - 某些文件系统不支持
            pass
    try:
        os.replace(staging, target)
    except OSError as error:
        staging.unlink(missing_ok=True)
        raise UpdateError(
            f"替换 {target} 失败：{error}。"
            "最常见的原因是程序正在运行 —— 请先关闭已打开的 AHA 桌面端，再重试"
        ) from error


def read_state(dist: Path) -> dict:
    try:
        return json.loads((dist / STATE_FILE).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def write_state(dist: Path, payload: dict) -> None:
    (dist / STATE_FILE).write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


# ── 命令实现 ─────────────────────────────────────────────────────────────────


def print_list(repo: str, releases: list[Release], labels: list[str], jdk: str,
               jdk_explicit: bool, timeout: float, limit: int) -> None:
    print(f"{repo} 最近的 {TAG_SUFFIX} 版本（本平台优先：{' / '.join(labels)}）：")
    shown = 0
    for release in releases:
        if shown >= limit:
            break
        if release.assets:
            asset = match_asset(release, labels, jdk, jdk_explicit)
        else:
            asset = probe_asset(repo, release, labels, jdk, jdk_explicit, timeout)
        mark = "可下载" if asset else "缺本平台包"
        name = asset.name if asset else "-"
        print(f"  * {release.tag}  版本 {release.version.text}  [{mark}]  {name}")
        shown += 1
    source = releases[0].source if releases else "?"
    print(f"（候选来自 {source}；加 --tag 可指定版本，加 --source 可换发现路径）")


def run(args: argparse.Namespace) -> int:
    dist = Path(args.dist).expanduser().resolve() if args.dist else ROOT / "Dist"
    labels = [args.platform] if args.platform else platform_labels()
    if not labels:
        raise UpdateError(
            "无法识别当前平台，请用 --platform 显式指定（windows-amd64 / ubuntu-amd64 / macos-arm64）"
        )
    jdk = args.jdk or DEFAULT_JDK
    jdk_explicit = bool(args.jdk)

    releases = discover(args)
    if args.list:
        print_list(args.repo, releases, labels, jdk, jdk_explicit, args.timeout, args.limit)
        return 0

    release, asset = select(args.repo, releases, labels, jdk, jdk_explicit, args.timeout)
    exe = dist / executable_name(asset_label(asset, labels))
    state = read_state(dist)

    print(f"目标版本：{release.tag}（版本 {release.version.text}，来源 {release.source}）")
    print(f"下载资产：{asset.name}")
    print(f"下载地址：{asset.url}")
    print(f"安装位置：{exe}")

    if not args.force and state.get("tag") == release.tag and exe.exists():
        print(f"\n✅ 已经是 {release.version.text}，无需更新（加 --force 可强制重装）")
        return 0

    if args.dry_run:
        print("\n[dry-run] 只预览，不下载、不解压。去掉 --dry-run 即执行更新。")
        return 0

    dist.mkdir(parents=True, exist_ok=True)
    # 先把「能不能替换」探清楚：下载是分钟级的，别等包都下来了才说文件被占着。
    ensure_replaceable(exe)
    # 包名固定用发布资产名：缓存、断点续传、--no-keep-zip 的清理都以同一个文件为准。
    # （若为「不保留」另起一个临时名，反而会绕过复用、每次重下一遍。）
    archive = dist / asset.name
    download(asset, archive, args.download_timeout, args.redownload)

    if args.backup and exe.exists():
        backup = exe.with_name(exe.name + ".bak")
        try:
            shutil.copy2(exe, backup)
            print(f"  旧版已备份：{backup}（{format_size(backup.stat().st_size)}）")
        except OSError as error:  # 备份失败不该挡住更新
            print(f"  [提醒] 备份旧版失败（继续更新）：{error}", file=sys.stderr)

    extract_executable(archive, exe)
    size = exe.stat().st_size
    print(f"  已安装：{exe}（{format_size(size)}）")

    if not args.keep_zip:
        archive.unlink(missing_ok=True)

    write_state(dist, {
        "tag": release.tag,
        "version": release.version.text,
        "asset": asset.name,
        "url": asset.url,
        "executable": str(exe),
        "size": size,
        "sha256": sha256_file(exe),
        "installed_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    })

    print(f"\n✅ 已更新到 {release.version.text}")
    print(f"   运行：{exe}")
    return 0


def asset_label(asset: Asset, labels: list[str]) -> str:
    """从资产名反查平台标签，用于决定可执行文件名（要不要 .exe）。"""
    match = ASSET_RE.match(asset.name)
    if match and match.group("label") in labels:
        return match.group("label")
    return labels[0]


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="DesktopNativeVersionUpdate.py",
        description="从 GitHub Releases 取最新的 AHA 桌面端原生镜像，安装到 Dist/。",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "示例（Windows；POSIX 下把反斜杠换成 `/`）：\n"
            f"  {USAGE_CMD}            # 装最新版\n"
            f"  {USAGE_CMD} --list     # 看看有哪些版本\n"
            f"  {USAGE_CMD} --dry-run  # 只预览链接\n"
            f"  {USAGE_CMD} --tag V0.1.1.00038-aha-desktop-native\n"
        ),
    )
    parser.add_argument("--repo", default=DEFAULT_REPO, help=f"GitHub 仓库（默认 {DEFAULT_REPO}）")
    parser.add_argument("--tag", help="指定 release tag（默认自动取最新的可下载版本）")
    parser.add_argument("--platform", help="发布平台标签（默认按本机探测，如 windows-amd64）")
    parser.add_argument("--jdk", help=f"JDK 分支（默认 {DEFAULT_JDK}；无此分支时会退回可用的）")
    parser.add_argument("--dist", help="安装目录（默认仓库根的 Dist/）")
    parser.add_argument("--source", choices=("auto", "api", "atom", "page"), default="auto",
                        help="版本发现路径（默认 auto：api -> atom -> page）")
    parser.add_argument("--timeout", type=float, default=30.0,
                        help="元数据请求超时秒数（默认 30）")
    parser.add_argument("--download-timeout", type=float, default=600.0,
                        help="下载超时秒数（默认 600）")
    parser.add_argument("--force", action="store_true",
                        help="忽略「已是最新」，强制重新安装（已下载完整的包会复用）")
    parser.add_argument("--redownload", action="store_true",
                        help="强制重新下载发布包，不复用 Dist/ 里已有的 zip")
    parser.add_argument("--dry-run", action="store_true", help="只显示将要做的事，不下载")
    parser.add_argument("--list", action="store_true", help="列出最近的版本与可下载资产后退出")
    parser.add_argument("--limit", type=int, default=5, help="--list 显示的条数（默认 5）")
    parser.add_argument("--no-keep-zip", dest="keep_zip", action="store_false",
                        help="安装后删除 Dist/ 里的下载包（默认保留，便于离线重装）")
    parser.add_argument("--no-backup", dest="backup", action="store_false",
                        help="不备份旧的可执行文件（默认备份为 .bak）")
    parser.set_defaults(keep_zip=True, backup=True)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    configure_stdio()
    args = parse_args(argv)
    try:
        return run(args)
    except UpdateError as error:
        print(f"\n[错误] {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("\n[中断] 已取消；下次运行会自动从 .part 续传", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
