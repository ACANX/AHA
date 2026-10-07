#!/usr/bin/env python3
"""AHA 文档一致性检查。

检查项：
  1. Markdown 代码围栏是否正确闭合（含外层加长围栏的嵌套场景）
  2. 文档内相对链接是否指向存在的文件（排除代码块内的示例链接）
  3. 文本是否可解码且不含 NUL（防止内容被写坏后静默混进仓库）

用法：
    python3 bin/CheckDocs.py            # 从仓库根执行
    python3 bin/CheckDocs.py --verbose

退出码：0 = 全部通过；1 = 存在问题。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

SCAN_DIRS = ("Docs", ".agents")
SCAN_FILES = ("AGENTS.md", "README.md", "CHANGELOG.md")

FENCE_CHARS = "`~"
MIN_FENCE_RUN = 3
LINK_PATTERN = re.compile(r"\[[^\]]*\]\(([^)#\s]+?)\)")
SKIP_PREFIXES = ("http://", "https://", "mailto:", "#")


def collect_markdown() -> list[Path]:
    """收集待检查的 Markdown 文档。"""
    files: list[Path] = []
    for directory in SCAN_DIRS:
        base = ROOT / directory
        if base.is_dir():
            files.extend(base.rglob("*.md"))
    for name in SCAN_FILES:
        path = ROOT / name
        if path.is_file():
            files.append(path)
    return sorted(set(files))


def fence_token(line: str) -> tuple[str, int, str] | None:
    """解析围栏标记，返回 (字符, 长度, 剩余文本)；非围栏返回 None。"""
    stripped = line.lstrip()
    if not stripped or stripped[0] not in FENCE_CHARS:
        return None
    char = stripped[0]
    run = len(stripped) - len(stripped.lstrip(char))
    if run < MIN_FENCE_RUN:
        return None  # 行内代码，如 `code`
    return char, run, stripped[run:]


def scan(text: str) -> tuple[list[int], bool]:
    """扫描围栏。

    返回 (未闭合围栏的起始行号列表, 是否含嵌套)。

    规则（CommonMark）：
      - 闭围栏必须由同种字符构成、长度不短于开围栏，且其后只能有空白
      - 其余情况一律视为代码块内容（含嵌套的开围栏写法）
    """
    stack: list[tuple[str, int, int]] = []
    nested = False
    for lineno, line in enumerate(text.splitlines(), 1):
        token = fence_token(line)
        if token is None:
            continue
        char, run, rest = token
        if not stack:
            stack.append((char, run, lineno))
            continue
        fchar, frun, _open_line = stack[-1]
        if char == fchar and run >= frun and rest.strip() == "":
            stack.pop()
        else:
            nested = True
    return [open_line for _c, _r, open_line in stack], nested


def strip_fences(text: str) -> str:
    """移除围栏代码块内容（保留行数以维持行号语义）。"""
    out: list[str] = []
    stack: list[tuple[str, int]] = []
    for line in text.splitlines():
        token = fence_token(line)
        if token is None:
            out.append("" if stack else line)
            continue
        char, run, rest = token
        if stack:
            fchar, frun = stack[-1]
            if char == fchar and run >= frun and rest.strip() == "":
                stack.pop()
        else:
            stack.append((char, run))
        out.append("")
    return "\n".join(out)


def read_text(path: Path) -> tuple[str | None, list[str]]:
    """读取文档文本，同时报告编码与 NUL 问题。

    围栏与链接检查都先要读出文本，而写坏的文件（如编辑过程中误写入的 NUL）
    仍能通过围栏检查、grep 又只会提示“binary file matches”，很容易静默混进仓库。

    :param path: 文档路径
    :return: (文本或 None, 问题列表)
    """
    raw = path.read_bytes()
    rel = path.relative_to(ROOT)
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as e:
        return None, [f"{rel}: 不是合法 UTF-8（字节 {e.start}: {e.reason}）"]
    if "\x00" in text:
        count = text.count("\x00")
        line = text.count("\n", 0, text.index("\x00")) + 1
        return text, [f"{rel}:{line} 含 {count} 个 NUL 字节，文件已被写坏"]
    return text, []


def check_fences(path: Path) -> tuple[list[str], bool]:
    """检查围栏闭合；返回 (问题列表, 是否含嵌套)。"""
    text, problems = read_text(path)
    if text is None:
        return problems, False
    unclosed, nested = scan(text)
    problems.extend(f"{path.relative_to(ROOT)}:{line} 围栏未闭合" for line in unclosed)
    return problems, nested


def check_links(path: Path) -> list[str]:
    """检查相对链接；返回失效链接列表。"""
    problems: list[str] = []
    text, _ = read_text(path)
    if text is None:
        return problems
    for match in LINK_PATTERN.finditer(strip_fences(text)):
        link = match.group(1)
        if link.startswith(SKIP_PREFIXES):
            continue
        if not (path.parent / link).exists():
            problems.append(f"{path.relative_to(ROOT)} -> {link}")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description="AHA 文档一致性检查")
    parser.add_argument("--verbose", action="store_true", help="输出检查详情")
    args = parser.parse_args()

    files = collect_markdown()
    print(f"检查 {len(files)} 份 Markdown 文档…")

    fence_problems: list[str] = []
    link_problems: list[str] = []
    nested_files: list[str] = []
    for path in files:
        problems, nested = check_fences(path)
        fence_problems.extend(problems)
        if nested:
            nested_files.append(str(path.relative_to(ROOT)))
        link_problems.extend(check_links(path))

    print("\n[围栏检查]")
    print("\n".join(f"  ❌ {p}" for p in fence_problems) if fence_problems else "  ✅ 全部闭合")
    if nested_files and args.verbose:
        print("  [info] 含嵌套围栏（已用更长围栏，合法）：" + "、".join(nested_files))

    print("\n[链接检查]")
    print("\n".join(f"  ❌ {p}" for p in link_problems) if link_problems else "  ✅ 全部有效")

    if fence_problems or link_problems:
        print(f"\n共 {len(fence_problems) + len(link_problems)} 个问题")
        return 1
    print("\n✅ 文档一致性检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
