#!/usr/bin/env python3
"""AHA 脚本文件规约检查。

跨平台脚本对编码与行尾极为敏感，Windows CMD 对格式错误的容忍度尤其低。
本脚本用于在提交前/CI 中守住以下约束：

  *.bat / *.cmd
    - 必须是纯 ASCII（禁止非 ASCII 字符）
    - 必须是 CRLF 行尾
    - 不得包含 UTF-8 BOM
    原因：CMD 按 ANSI 代码页解析批处理文件。UTF-8 中文字节会被误读并产生
    `&`、`|` 等元字符，导致注释或 echo 行被当作命令执行；LF-only 批处理
    在 `if (...)` 块中解析行为不可靠。

  *.sh
    - 必须以 `#!` shebang 开头
    - 必须是 LF 行尾（禁止 CRLF）
    - 应当有可执行位（可配置为提示而非错误）

  *.py
    - 应当以 `#!` shebang 开头
    - 必须是 LF 行尾

  *.ps1
    - 含非 ASCII 内容时**必须带 UTF-8 BOM**（纯 ASCII 不强制）
    原因：Windows PowerShell 5.1 读无 BOM 的文件时按 **ANSI（本机 GBK）** 解码，
    中文提示会直接变成乱码——不报错，只是用户看到一堆看不懂的字。
    （姊妦项目 LegionFanControl 的 `Dist\\UpdateNative.ps1` 就是无 BOM 的，属遗留坑。）

  Dist/ 下被跟踪的手写脚本
    - 同样按上面的规则检查（`Dist/` 整体是构建输出目录，已剪枝；
      所以用 `git ls-files` 把其中**被跟踪**的那几个手写入口单独捡回来）。

  原生取件：两套实现的一致性
    - `Dist/UpdateNative.ps1`（Windows，PowerShell）与
      `Script/Python/NativeVersionUpdate.py`（POSIX，Python）的
      tag 后缀 / 资产前缀 / 可执行文件名必须**逐字一致**。
    原因：两套实现刻意并存（Windows 不假设有 Python；POSIX 不假设有 PowerShell，
    且 Python 版功能更多），但这三个字面量一旦漂移，表现是「**某个平台上永远取不到包**」
    ——静默且难查。把它们钉在一起，漂移立刻可见。

  *.xml（含 pom.xml）
    - 必须是良构 XML（整体可被解析器解析）
    - 注释里不得出现 `--`，也不得嵌套 `<!--`
    原因：XML 规范禁止注释体出现 `--`，解析器直接报
    `String '--' not allowed in comment`。写命令行选项时极易踩到
    （例如在注释里写 native-image 的双短横参数），而**报错位置指向注释本身**，
    看起来像文件坏了。本仓库已踩两次，因此固化成检查。
    嵌套注释更阴：外层会被内层的 `-->` 提前闭合，剩下的文字变成正文，报错离原因很远。

用法：
    python3 bin/CheckScripts.py
    python3 bin/CheckScripts.py --verbose

退出码：0 = 通过；1 = 存在问题。
"""

from __future__ import annotations

import argparse
import re
import subprocess
import os
import sys
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parent.parent

SKIP_DIRS = {"target", ".git", "Dist", "dist", "node_modules", ".idea", ".vscode"}
BAT_SUFFIXES = {".bat", ".cmd"}
SH_SUFFIXES = {".sh", ".bash"}
PY_SUFFIXES = {".py"}
PS1_SUFFIXES = {".ps1"}

#: Dist/ 被 SKIP_DIRS 剪掉（里面是构建产物），但其中有几个**被 git 跟踪的手写入口**
#: 必须照检——用 git 问「哪些是跟踪的」最省事，生成物（Dist/bin/*.bat 等）不在跟踪列表里。
TRACKED_SCRIPT_DIRS = ("Dist",)

#: 原生取件的两套实现：命名契约必须一致（见模块 docstring）
NATIVE_PS1 = ROOT / "Dist" / "UpdateNative.ps1"
NATIVE_PY = ROOT / "Script" / "Python" / "NativeVersionUpdate.py"
PY_KIND_RE = re.compile(
    r'key="(\w+)".*?tag_suffix="([^"]+)".*?asset_prefix="([^"]+)".*?exe_base="([^"]+)"',
    re.S)
PS_KIND_RE = re.compile(
    r"^\s*(\w+)\s*=\s*@\{\s*Title\s*=\s*'[^']*';\s*TagSuffix\s*=\s*'([^']+)';"
    r"\s*AssetPrefix\s*=\s*'([^']+)';\s*ExeBase\s*=\s*'([^']+)'\s*\}",
    re.M)


def collect() -> dict[str, list[Path]]:
    """单次遍历收集待检查的脚本文件，按类型分类。

    在慢文件系统（如 WSL 下的 /mnt/e）上遍历代价很高：既要避免同一棵树
    扫多遍，也要对 SKIP_DIRS 直接剪枝而不要走进去了再逐条判定。
    """
    found: dict[str, list[Path]] = {"bat": [], "sh": [], "py": [], "ps1": [], "xml": [], "yml": []}
    for dirpath, dirnames, filenames in os.walk(ROOT):
        # 就地改写 dirnames 实现剪枝，os.walk 不会进入被移除的子目录
        dirnames[:] = sorted(name for name in dirnames if name not in SKIP_DIRS)
        base = Path(dirpath)
        for name in filenames:
            suffix = Path(name).suffix.lower()
            if suffix in BAT_SUFFIXES:
                found["bat"].append(base / name)
            elif suffix in SH_SUFFIXES:
                found["sh"].append(base / name)
            elif suffix in PY_SUFFIXES:
                found["py"].append(base / name)
            elif suffix in (".yml", ".yaml"):
                # GitHub Actions 的 shell 片段藏在这里：它们由各平台的 bash 执行，
                # 跨平台差异（macOS bash 3.2 的变量名规则）就靠这一组守住。
                found["yml"].append(base / name)
            elif suffix == ".xml":
                # 只关心仓库自己的 XML（pom.xml、assembly 描述符等）。
                # target/ 与 Dist/ 已在 SKIP_DIRS 里剪掉，不会扫到生成物。
                found["xml"].append(base / name)
    # Dist/ 整体被剪掉，但其中的手写入口要捡回来：问 git 哪些属于「源码」
    # （--cached 跟踪的 + --others 未跟踪但未被忽略的），生成物（Dist/bin/*.bat、
    # 解压出来的 lib/ 等）都在 .gitignore 里，因此不会被误判；新加的文件不 add 也能查到。
    for directory in TRACKED_SCRIPT_DIRS:
        tracked = subprocess.run(["git", "ls-files", "--cached", "--others", "--exclude-standard",
                                  "--", directory],
                                 cwd=ROOT, capture_output=True, text=True)
        if tracked.returncode != 0:
            continue
        for name in tracked.stdout.splitlines():
            if not name.strip():
                continue
            suffix = Path(name).suffix.lower()
            if suffix in BAT_SUFFIXES:
                found["bat"].append(ROOT / name)
            elif suffix in PS1_SUFFIXES:
                found["ps1"].append(ROOT / name)
    return {group: sorted(paths) for group, paths in found.items()}


def check_bat(path: Path) -> list[str]:
    """批处理：纯 ASCII + CRLF + 无 BOM。"""
    problems: list[str] = []
    raw = path.read_bytes()
    rel = path.relative_to(ROOT)

    if raw.startswith(b"\xef\xbb\xbf"):
        problems.append(f"{rel}: 含 UTF-8 BOM，CMD 会把它当作命令的一部分")

    non_ascii = [(i, b) for i, b in enumerate(raw) if b > 127]
    if non_ascii:
        offset, byte = non_ascii[0]
        line = raw[:offset].count(b"\n") + 1
        problems.append(
            f"{rel}:{line} 含非 ASCII 字节 0x{byte:02x}（共 {len(non_ascii)} 处）。"
            "批处理必须为纯 ASCII；中文说明请放到 README"
        )

    lf_only = raw.count(b"\n") - raw.count(b"\r\n")
    if lf_only:
        problems.append(
            f"{rel}: 存在 {lf_only} 处裸 LF 行尾，批处理必须使用 CRLF"
        )
    return problems


def check_sh(path: Path) -> list[str]:
    """Shell：shebang + LF。"""
    problems: list[str] = []
    rel = path.relative_to(ROOT)
    raw = path.read_bytes()

    if not raw.startswith(b"#!"):
        problems.append(f"{rel}: 缺少 `#!` shebang")

    crlf = raw.count(b"\r\n")
    if crlf:
        problems.append(f"{rel}: 含 CRLF 行尾（{crlf} 处），shell 脚本必须使用 LF")
    return problems


def check_ignored_sources() -> list[str]:
    """检查有没有源码文件被 .gitignore 吃掉。

    这个检查是有来历的：`.gitignore` 里曾经写着不带前导斜杠的 `Log/`（本意是仓库根的
    运行期日志目录），于是它在**任意层级**匹配，在 Windows / macOS（大小写不敏感）上
    把 `aha-desktop/src/main/java/.../desktop/log/` 整个吃掉了——

      * git 不报错；
      * `git add -A` 静默跳过；
      * `git status` 显示"干净"；
      * 本地测试照样全绿（文件在磁盘上）；
      * 只有 CI 会告诉你 `cannot find symbol: class LogLevel`。

    因此这里直接问 git：有哪些被忽略的文件落在源码目录里（或本身就是 .java）。
    """
    try:
        result = subprocess.run(
            ["git", "ls-files", "--others", "--ignored", "--exclude-standard"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=60,
            check=False,
        )
    except (OSError, subprocess.SubprocessError) as error:  # pragma: no cover - 环境问题
        return [f"调用 git 失败，无法检查被忽略的源码文件：{error}"]

    if result.returncode != 0:
        return [f"调用 git 失败（exit={result.returncode}）：{result.stderr.strip()}"]

    offenders: list[str] = []
    for line in result.stdout.splitlines():
        entry = line.strip()
        if not entry:
            continue
        if "/src/" in entry or entry.endswith(".java"):
            offenders.append(entry)
    return [f"源码文件被 .gitignore 忽略了（git 不会报错，只有 CI 会发现）：{path}"
            for path in offenders]


XML_COMMENT = re.compile(r"<!--(.*?)-->", re.S)


def check_xml(path: Path) -> list[str]:
    """XML 的三条硬约束：整体良构、注释里不能有 `--`、不能嵌套 `<!--`。

    前两条与「嵌套」都会让 XML 直接不可解析，而且报错位置离真正的原因往往很远
    （`--` 报在注释那一行；嵌套报在内层 `-->` 之后；尖括号占位符报成「无效记号」）。

    为什么要查良构性：**模板类文件**（如技能 assets 下的 POM 骨架）如果本身不是合法
    XML，别人拷贝过去会在解析阶段就失败，而报错信息看起来像文件损坏、不像「有东西没替换」。
    实测踩过：占位符写成三个尖括号包中文，整个模板不可解析。
    """
    problems: list[str] = []

    # ① 良构性：整体解析一次（这也能挡住非 UTF-8 与尖括号占位符）
    try:
        ElementTree.parse(path)
    except ElementTree.ParseError as exc:
        problems.append(f"{path.relative_to(ROOT)} 不是良构 XML：{exc}")
    except Exception as exc:  # 权限、编码等
        problems.append(f"{path.relative_to(ROOT)} 无法解析为 XML：{exc}")
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return [f"{path.relative_to(ROOT)} 不是 UTF-8，无法检查 XML 注释"]

    rel = path.relative_to(ROOT)
    for match in XML_COMMENT.finditer(text):
        body = match.group(1)
        line = text[: match.start()].count("\n") + 1
        if "--" in body:
            problems.append(
                f"{rel}:{line} XML 注释里出现 `--`；解析器会报 "
                f"String '--' not allowed in comment（改写措辞，不要直接写双短横选项）"
            )
        if "<!--" in body:
            problems.append(
                f"{rel}:{line} XML 注释里嵌套了 `<!--`；外层注释会被提前闭合，"
                f"内层改用普通文字"
            )
    return problems


def check_py(path: Path) -> list[str]:
    """Python：shebang + LF。"""
    problems: list[str] = []
    rel = path.relative_to(ROOT)
    raw = path.read_bytes()

    if not raw.startswith(b"#!"):
        problems.append(f"{rel}: 缺少 `#!` shebang")
    if raw.count(b"\r\n"):
        problems.append(f"{rel}: 含 CRLF 行尾，Python 脚本应使用 LF")
    return problems


def check_ps1(path: Path) -> list[str]:
    """PowerShell：含非 ASCII 时必须带 UTF-8 BOM。

    无 BOM 的 UTF-8 脚本在 Windows PowerShell 5.1 下会被按 ANSI（GBK）解码：
    中文提示变乱码，而且**不报错**——只是用户看不懂。加 BOM 是唯一能让 5.1 正确解码的办法。
    """
    rel = path.relative_to(ROOT)
    raw = path.read_bytes()
    has_bom = raw.startswith(b"\xef\xbb\xbf")
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        return [f"{rel}: 不是 UTF-8，无法检查"]
    if text.isascii() or has_bom:
        return []
    return [
        f"{rel}: 含非 ASCII（中文提示）但没有 UTF-8 BOM——"
        f"Windows PowerShell 5.1 会按 ANSI 解码，中文变乱码（且不报错）"
    ]


def check_native_update_parity() -> list[str]:
    """两套原生取件实现的命名契约必须逐字一致（见模块 docstring）。"""
    if not (NATIVE_PS1.exists() and NATIVE_PY.exists()):
        return []
    py = {m.group(1).lower(): m.groups()[1:]
          for m in PY_KIND_RE.finditer(NATIVE_PY.read_text(encoding="utf-8"))}
    ps = {m.group(1).lower(): m.groups()[1:]
          for m in PS_KIND_RE.finditer(NATIVE_PS1.read_text(encoding="utf-8-sig"))}
    if not py or not ps:
        return [
            "原生取件实现的一致性检查没能解析出契约："
            f"Python 侧 {sorted(py)} / PowerShell 侧 {sorted(ps)}——"
            "请确认 KINDS 表（Python）与 $kinds 表（PowerShell）仍是脚本可识别的写法"
        ]
    problems: list[str] = []
    for key in sorted(set(py) | set(ps)):
        if key not in ps:
            problems.append(f"原生取件：Python 侧有种类 {key!r}，PowerShell 侧没有")
            continue
        if key not in py:
            problems.append(f"原生取件：PowerShell 侧有种类 {key!r}，Python 侧没有")
            continue
        for field, a, b in zip(("tag 后缀", "资产前缀", "可执行文件名"), py[key], ps[key]):
            if a != b:
                problems.append(
                    f"原生取件：种类 {key!r} 的{field}两套实现不一致——"
                    f"Python 侧 {a!r}，PowerShell 侧 {b!r}；"
                    f"这会让某个平台上永远取不到包"
                )
    return problems


SHELL_VAR = re.compile(r"(?<!\\)\$([A-Za-z_][A-Za-z0-9_]*)")


def check_yaml_shell_vars(path: Path) -> list[str]:
    """YAML（主要是 GitHub Actions 的 shell 片段）：`$VAR` 后不得紧跟非 ASCII。

    来历（2026-10-08 实测，同一条规则踩了两次）：

      * macOS runner 的 bash 把 `$bin（` 解析成变量名 `bin（`，`set -u` 下直接
        `bin（: unbound variable` 退出——步骤的 outputs 随之丢失，macos 镜像包没上传；
      * `Release.yml` 里 `$LABEL）、$target（` 等同一写法。

    规避方式只有一个：变量后跟中文 / 全角字符时写 `${VAR}`。
    GitHub 的 `${{ ... }}` 表达式与 `\\$` 转义不会命中本规则。
    """
    problems: list[str] = []
    rel = path.relative_to(ROOT)
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return [f"{rel} 不是 UTF-8，无法检查 shell 变量写法"]

    for number, line in enumerate(text.splitlines(), 1):
        for match in SHELL_VAR.finditer(line):
            end = match.end()
            if end < len(line) and ord(line[end]) > 127:
                name = match.group(1)
                problems.append(
                    f"{rel}:{number} 变量 `{name}` 后紧跟非 ASCII 字符 `{line[end]}`；"
                    f"部分平台（macOS bash 3.2）会把它并进变量名 → unbound variable。"
                    f"请写成 `${{{name}}}`"
                )
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description="AHA 脚本文件规约检查")
    parser.add_argument("--verbose", action="store_true", help="输出每个文件的检查结果")
    args = parser.parse_args()

    problems: list[str] = []
    checked = 0

    groups = collect()
    for group, checker in (
        ("bat", check_bat),
        ("sh", check_sh),
        ("py", check_py),
        ("ps1", check_ps1),
        ("xml", check_xml),
        ("yml", check_yaml_shell_vars),
    ):
        for path in groups[group]:
            checked += 1
            found = checker(path)
            problems.extend(found)
            if args.verbose:
                print(f"  {'❌' if found else '✅'} {path.relative_to(ROOT)}")

    ignored = check_ignored_sources()
    problems.extend(ignored)
    parity = check_native_update_parity()
    problems.extend(parity)

    print(f"\n检查 {checked} 个脚本文件（含 {len(groups['ps1'])} 个 PowerShell、"
          f"{len(groups['xml'])} 个 XML、{len(groups['yml'])} 个 YAML）")
    print(f"检查被 .gitignore 忽略的源码文件：{'❌' if ignored else '✅'}")
    print(f"检查原生取件两套实现的命名契约：{'❌' if parity else '✅'}")
    if problems:
        print("\n[问题]")
        for problem in problems:
            print(f"  ❌ {problem}")
        print(f"\n共 {len(problems)} 个问题")
        return 1
    print("\n✅ 全部脚本符合跨平台规约")
    return 0


if __name__ == "__main__":
    sys.exit(main())
