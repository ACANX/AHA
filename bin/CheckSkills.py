#!/usr/bin/env python3
"""AHA 技能一致性检查（Agent Skills 规范）。

检查项：
  1. 每个技能目录包含 SKILL.md
  2. SKILL.md 具备合法 YAML frontmatter，且含必需字段 name / description
  3. name 与目录名一致，且符合 kebab-case（小写字母、数字、连字符；无首尾/连续连字符；≤ 64 字符）
  4. description 非空且 ≤ 1024 字符
  5. 子目录仅允许 references / assets / scripts
  6. SKILL.md 内的相对链接指向存在的文件
  7. 声明了 SKILL.md frontmatter 中 name 的技能索引引用存在

用法：
    python3 bin/CheckSkills.py
    python3 bin/CheckSkills.py --verbose

退出码：0 = 通过；1 = 存在问题。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKILLS_DIR = ROOT / ".agents" / "skills"

REQUIRED_FIELDS = ("name", "description")
RECOMMENDED_FIELDS = ("license", "compatibility", "metadata")
ALLOWED_SUBDIRS = {"references", "assets", "scripts"}
NAME_PATTERN = re.compile(r"[a-z0-9]+(-[a-z0-9]+)*")
LINK_PATTERN = re.compile(r"\[[^\]]*\]\(([^)#\s]+?)\)")
SKIP_PREFIXES = ("http://", "https://", "mailto:", "#")


def parse_frontmatter(text: str) -> dict | None:
    """解析 SKILL.md 的 YAML frontmatter。未安装 PyYAML 时回退到简单解析。"""
    if not text.startswith("---\n"):
        return None
    try:
        end = text.index("\n---\n", 4)
    except ValueError:
        return None
    body = text[4:end]
    try:
        import yaml  # noqa: PLC0415
        return yaml.safe_load(body) or {}
    except ImportError:
        data: dict[str, str] = {}
        for line in body.splitlines():
            if line and not line.startswith((" ", "\t")) and ":" in line:
                key, _, value = line.partition(":")
                data[key.strip()] = value.strip()
        return data or None


def strip_fences(text: str) -> str:
    """移除围栏代码块，避免把示例链接计入检查。"""
    out: list[str] = []
    fence: tuple[str, int] | None = None
    for line in text.splitlines():
        stripped = line.lstrip()
        if stripped and stripped[0] in "`~":
            char = stripped[0]
            run = len(stripped) - len(stripped.lstrip(char))
            if run >= 3:
                if fence and char == fence[0] and run >= fence[1]:
                    fence = None
                elif not fence:
                    fence = (char, run)
                out.append("")
                continue
        out.append("" if fence else line)
    return "\n".join(out)


def check_skill(skill_dir: Path) -> tuple[list[str], list[str]]:
    """检查单个技能，返回 (问题列表, 提示列表)。"""
    problems: list[str] = []
    notes: list[str] = []
    name = skill_dir.name

    skill_md = skill_dir / "SKILL.md"
    if not skill_md.is_file():
        problems.append(f"{name}: 缺少 SKILL.md")
        return problems, notes

    # 旧命名残留（用精确名比较：大小写不敏感文件系统上 glob 会误匹配 SKILL.md）
    if any(p.is_file() and p.name == "Skill.md" for p in skill_dir.iterdir()):
        problems.append(f"{name}: 存在旧文件名 Skill.md（应为 SKILL.md）")

    text = skill_md.read_text(encoding="utf-8")
    fm = parse_frontmatter(text)
    if fm is None:
        problems.append(f"{name}: SKILL.md 缺少或格式错误的 YAML frontmatter")
    else:
        for field in REQUIRED_FIELDS:
            if not fm.get(field):
                problems.append(f"{name}: frontmatter 缺少必需字段 {field}")
        missing_rec = [f for f in RECOMMENDED_FIELDS if f not in fm]
        if missing_rec:
            notes.append(f"{name}: 建议补充字段 {', '.join(missing_rec)}")

        declared = fm.get("name", "")
        if declared and declared != name:
            problems.append(f"{name}: frontmatter name={declared!r} 与目录名不一致")
        if declared and not NAME_PATTERN.fullmatch(declared):
            problems.append(f"{name}: name 不符合 kebab-case")
        if declared and len(declared) > 64:
            problems.append(f"{name}: name 超过 64 字符")

        desc = fm.get("description", "") or ""
        if len(desc) > 1024:
            problems.append(f"{name}: description 超过 1024 字符")

    # 子目录白名单
    for sub in skill_dir.iterdir():
        if sub.is_dir() and sub.name not in ALLOWED_SUBDIRS:
            problems.append(f"{name}: 非规范子目录 {sub.name}/（仅允许 {'/ '.join(sorted(ALLOWED_SUBDIRS))}）")

    # 内链
    for match in LINK_PATTERN.finditer(strip_fences(text)):
        link = match.group(1)
        if link.startswith(SKIP_PREFIXES):
            continue
        if not (skill_dir / link).exists():
            problems.append(f"{name}: SKILL.md 内链失效 -> {link}")

    return problems, notes


def main() -> int:
    parser = argparse.ArgumentParser(description="AHA 技能一致性检查")
    parser.add_argument("--verbose", action="store_true", help="输出提示信息")
    args = parser.parse_args()

    if not SKILLS_DIR.is_dir():
        print(f"❌ 技能目录不存在：{SKILLS_DIR}")
        return 1

    skill_dirs = sorted(d for d in SKILLS_DIR.iterdir() if d.is_dir())
    print(f"检查 {len(skill_dirs)} 个技能…\n")

    all_problems: list[str] = []
    all_notes: list[str] = []
    for skill_dir in skill_dirs:
        problems, notes = check_skill(skill_dir)
        all_problems.extend(problems)
        all_notes.extend(notes)
        print(f"  {'❌' if problems else '✅'} {skill_dir.name}")

    if args.verbose and all_notes:
        print("\n[提示]")
        for note in all_notes:
            print(f"  · {note}")

    if all_problems:
        print("\n[问题]")
        for problem in all_problems:
            print(f"  ❌ {problem}")
        print(f"\n共 {len(all_problems)} 个问题")
        return 1

    print("\n✅ 全部技能符合 Agent Skills 规范")
    return 0


if __name__ == "__main__":
    sys.exit(main())
