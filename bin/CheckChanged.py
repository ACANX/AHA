#!/usr/bin/env python3
"""按变更范围选择要执行的检查。

三个 `Check*.py` 各自只覆盖一类文件，跑全套在慢文件系统（如 WSL 下的 /mnt/e）上
要几十秒，而 `mvn verify` 要分钟级。因此按实际改了什么来决定跑什么：

    文档（*.md / Docs/）        -> CheckDocs.py
    技能（.agents/skills/）     -> CheckSkills.py（这些文件也是 Markdown，故同时跑 CheckDocs）
    脚本（*.bat/*.cmd/*.sh/*.py/.gitattributes） -> CheckScripts.py
    其它（Java / POM / YAML）   -> 需要 Maven 验证（本脚本只提示，不代为执行）

Maven 验证分两档——改的是普通实现代码时不要顺手跑完整 verify：

    ./mvnw -pl <模块> -am test -Djacoco.skip=true    快速：只跑测试
    ./mvnw clean verify                              完整：含覆盖率门禁

需要跑完整 verify 的时机见 Docs/DevSpec/BuildSpec.md 第 8 节。

注意：覆盖率门禁、完整 verify、文档检查与重复率属于「慢检查」，已集中到 CI 的
Gate.yml（合入 main 前 / 发布前），不再作为每次改动的卡点；本脚本只服务于
开发过程中的快速自查。

用法：
    python3 bin/CheckChanged.py                # 按 git 工作区变更判定
    python3 bin/CheckChanged.py <路径>...      # 按给定路径判定
    python3 bin/CheckChanged.py --all          # 无条件全部检查
    python3 bin/CheckChanged.py --dry-run      # 只打印将执行什么，不执行

退出码：0 = 通过或无需检查；1 = 检查未通过；2 = 用法错误。
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

DOC_CHECK = "CheckDocs.py"
SKILL_CHECK = "CheckSkills.py"
SCRIPT_CHECK = "CheckScripts.py"

# 重复率检查不在本脚本内执行：它要先由 Maven 生成 CPD 报告，这里只做提示
DUP_HINTS = ("CheckDuplication.py", "maven-pmd-plugin", "pmd.")

DOC_SUFFIXES = {".md", ".markdown"}
SCRIPT_SUFFIXES = {".bat", ".cmd", ".sh", ".bash", ".py"}
BUILD_SUFFIXES = {".java", ".xml", ".yml", ".yaml", ".properties"}

# 这些改动会影响构建 / 覆盖率口径，必须完整验证
FULL_VERIFY_HINTS = ("pom.xml", "module-info.java", "assembly", ".github/workflows/", "AhaDefault.yaml")


def normalize(paths: list[str]) -> list[str]:
    """统一成相对仓库根的 POSIX 风格路径。"""
    result = []
    for raw in paths:
        text = raw.strip().replace("\\", "/")
        if not text:
            continue
        candidate = Path(text)
        if candidate.is_absolute():
            try:
                text = candidate.resolve().relative_to(ROOT).as_posix()
            except ValueError:
                text = candidate.name
        if text.startswith("./"):
            text = text[2:]
        result.append(text)
    return result


def changed_from_git() -> list[str]:
    """从 git 工作区收集变更路径。

    仓库尚无提交时 `git diff HEAD` 会失败，此时退回未跟踪文件列表
    （等于「全部文件」），宁可多跑也不错漏。
    """
    names: set[str] = set()
    commands = [
        ["git", "diff", "--name-only", "HEAD"],
        ["git", "diff", "--name-only", "--cached"],
        ["git", "ls-files", "--others", "--exclude-standard"],
    ]
    available = True
    for command in commands:
        try:
            completed = subprocess.run(
                command, cwd=ROOT, capture_output=True, text=True, check=False
            )
        except OSError:
            available = False
            break
        if completed.returncode == 0:
            names.update(line.strip() for line in completed.stdout.splitlines() if line.strip())
    if not available:
        print("  未找到 git，请显式传入路径或使用 --all。", file=sys.stderr)
    return sorted(normalize(sorted(names)))


def select(paths: list[str]) -> list[str]:
    """按路径选择要执行的检查脚本。"""
    checks: set[str] = set()
    for path in paths:
        lower = path.lower()
        suffix = Path(lower).suffix
        if path.startswith(".agents/skills/"):
            # 技能目录内的 Markdown 同样是文档，两套规则都适用
            checks.add(SKILL_CHECK)
            if suffix in DOC_SUFFIXES:
                checks.add(DOC_CHECK)
        elif suffix in DOC_SUFFIXES:
            checks.add(DOC_CHECK)
        if suffix in SCRIPT_SUFFIXES or lower.endswith(".gitattributes") or lower.endswith(".gitignore"):
            checks.add(SCRIPT_CHECK)
    return [name for name in (DOC_CHECK, SKILL_CHECK, SCRIPT_CHECK) if name in checks]


def duplication_relevant(paths: list[str]) -> bool:
    """是否触及重复率检查（PMD 插件配置或阈值脚本）。"""
    return any(any(hint in path for hint in DUP_HINTS) for path in paths)


def needs_maven(paths: list[str]) -> bool:
    """判断是否需要 Maven 验证。"""
    for path in paths:
        lower = path.lower()
        if lower.endswith("module-info.java"):
            return True
        if Path(lower).suffix in BUILD_SUFFIXES:
            return True
    return False


def full_verify_required(paths: list[str]) -> bool:
    """判断是否必须跑完整 verify（而非只跑测试）。"""
    for path in paths:
        lower = path.lower()
        if any(hint in lower for hint in FULL_VERIFY_HINTS):
            return True
    return False


def modules(paths: list[str]) -> list[str]:
    """从路径推断涉及的 Maven 模块。"""
    found: set[str] = set()
    for path in paths:
        head = path.split("/", 1)[0]
        if head.startswith("aha-") and (ROOT / head / "pom.xml").exists():
            found.add(head)
    return sorted(found)


def run(name: str) -> int:
    """执行单个检查脚本。"""
    script = ROOT / "bin" / name
    print(f"  -> {name}")
    completed = subprocess.run([sys.executable, str(script)], cwd=ROOT, check=False)
    return completed.returncode


def main() -> int:
    # 检查脚本直接写 stdout，行缓冲才能保证输出顺序与预期一致
    sys.stdout.reconfigure(line_buffering=True)

    parser = argparse.ArgumentParser(description="按变更范围选择检查项")
    parser.add_argument("paths", nargs="*", help="变更路径；留空则按 git 工作区判定")
    parser.add_argument("--all", action="store_true", help="无条件执行全部检查")
    parser.add_argument("--dry-run", action="store_true", help="只打印计划，不执行")
    args = parser.parse_args()

    if args.all:
        paths = ["<all>"]
        checks = [DOC_CHECK, SKILL_CHECK, SCRIPT_CHECK]
    else:
        paths = normalize(args.paths) if args.paths else changed_from_git()
        checks = select(paths)
        if not paths:
            print("未检测到变更（或仓库无提交且无未跟踪文件）。")
            return 0

    print(f"变更 {len(paths)} 个路径：")
    for path in paths[:10]:
        print(f"  {path}")
    if len(paths) > 10:
        print(f"  ...（其余 {len(paths) - 10} 个）")

    if not checks:
        print("\n无需执行检查（未触及文档 / 技能 / 脚本）。")
    else:
        print(f"\n执行 {len(checks)} 项检查：")

    failed = 0
    for name in checks:
        if args.dry_run:
            print(f"  -> {name}（--dry-run，跳过）")
            continue
        if run(name) != 0:
            failed += 1

    if needs_maven(paths) or args.all:
        module_args = " ".join(modules(paths))
        scope = f" -pl {module_args} -am" if module_args else ""
        print("\n需要 Maven 验证（本脚本不代为执行）：")
        if full_verify_required(paths) or args.all:
            print("  ./mvnw clean verify                          完整：含覆盖率门禁")
            print("  涉及构建 / 模块结构 / 覆盖率口径，不要只跑测试。")
        else:
            print(f"  ./mvnw{scope} test -Djacoco.skip=true    快速：只跑测试")
            print("  ./mvnw clean verify                          完整：含覆盖率门禁")
            print("  仅当需要刷新文档里的实测值、或交付验收时才跑完整 verify。")

    if duplication_relevant(paths) or args.all:
        print("\n需要重复率检查（本脚本不代为执行，需先生成报告）：")
        print("  ./mvnw -B pmd:cpd && python3 bin/CheckDuplication.py")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
