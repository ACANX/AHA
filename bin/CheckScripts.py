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

SKIP_DIRS = {"target", ".git", "dist", "node_modules", ".idea", ".vscode"}
BAT_SUFFIXES = {".bat", ".cmd"}
SH_SUFFIXES = {".sh", ".bash"}
PY_SUFFIXES = {".py"}


def collect() -> dict[str, list[Path]]:
    """单次遍历收集待检查的脚本文件，按类型分类。

    在慢文件系统（如 WSL 下的 /mnt/e）上遍历代价很高：既要避免同一棵树
    扫多遍，也要对 SKIP_DIRS 直接剪枝而不要走进去了再逐条判定。
    """
    found: dict[str, list[Path]] = {"bat": [], "sh": [], "py": [], "xml": []}
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
            elif suffix == ".xml":
                # 只关心仓库自己的 XML（pom.xml、assembly 描述符等）。
                # target/ 与 dist/ 已在 SKIP_DIRS 里剪掉，不会扫到生成物。
                found["xml"].append(base / name)
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
        ("xml", check_xml),
    ):
        for path in groups[group]:
            checked += 1
            found = checker(path)
            problems.extend(found)
            if args.verbose:
                print(f"  {'❌' if found else '✅'} {path.relative_to(ROOT)}")

    ignored = check_ignored_sources()
    problems.extend(ignored)

    print(f"\n检查 {checked} 个脚本文件（含 {len(groups['xml'])} 个 XML）")
    print(f"检查被 .gitignore 忽略的源码文件：{'❌' if ignored else '✅'}")
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
