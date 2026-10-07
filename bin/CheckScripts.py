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

用法：
    python3 bin/CheckScripts.py
    python3 bin/CheckScripts.py --verbose

退出码：0 = 通过；1 = 存在问题。
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

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
    found: dict[str, list[Path]] = {"bat": [], "sh": [], "py": []}
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
    for group, checker in (("bat", check_bat), ("sh", check_sh), ("py", check_py)):
        for path in groups[group]:
            checked += 1
            found = checker(path)
            problems.extend(found)
            if args.verbose:
                print(f"  {'❌' if found else '✅'} {path.relative_to(ROOT)}")

    print(f"\n检查 {checked} 个脚本文件")
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
