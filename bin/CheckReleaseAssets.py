#!/usr/bin/env python3
"""发布资产完整性核对（issue #269 的验收第 1 条 / issue #303 的预演要求）。

两种用法：

    # ① 发布前预演：对着一个已有 release（快照 tag 也行）核对「矩阵要求的资产缺哪些」
    python3 bin/CheckReleaseAssets.py --tag V0.1.4 --version 0.1.4

    # ② 发布作业里：对着本地已下载的资产目录核对
    python3 bin/CheckReleaseAssets.py --dir assets --version 0.1.4

「缺什么」按 **形态 × 平台** 摊成表输出，便于直接判断「本版能不能发」。
缺失（或多余）时退出码为 1；`--allow-partial` 只报告不判红（预演用）。
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / ".github" / "Python"))

import ReleaseMatrix  # noqa: E402

#: 矩阵里的组合顺序（与 ReleaseMatrix.COMBOS 一致）。
COMBOS = ("desktop-jvm", "desktop-native", "cli-jvm", "cli-native")


def matrix_names(version: str) -> list[str]:
    matrix = ReleaseMatrix.load()
    return ReleaseMatrix.asset_names(version, matrix)


def from_release(tag: str) -> list[str]:
    """从 GitHub release 拉资产名（只取 zip）。"""
    out = subprocess.run(
        ["gh", "api", f"repos/ACANX/AHA/releases/tags/{tag}", "--jq", ".assets[].name"],
        capture_output=True, text=True, check=False)
    if out.returncode != 0:
        raise SystemExit(f"❌ 取 release 资产失败：{out.stderr.strip()}")
    return sorted(line.strip() for line in out.stdout.splitlines() if line.strip().endswith(".zip"))


def from_dir(directory: Path) -> list[str]:
    return sorted(p.name for p in directory.glob("*.zip"))


def report(expected: list[str], actual: list[str], label: str) -> int:
    missing = [name for name in expected if name not in actual]
    # 报告包（`-report.zip`）是合法附加物，不属于 12 份升级契约，不算多余
    extra = [name for name in actual if name not in expected and not name.endswith("-report.zip")]

    print(f"## {label}")
    print(f"期望 {len(expected)} 份 / 实际 {len(actual)} 份")
    print()
    print("| 形态 | 平台 | 状态 |")
    print("|---|---|---|")
    for name in expected:
        parts = name.removesuffix(".zip").split("-")
        # aha-<product>-<form>-<version...>-<os>-<arch>-<runtime>
        os_index = len(parts) - 3
        combo = f"{parts[1]}-{parts[2]}"
        platform = f"{parts[os_index]}-{parts[os_index + 1]}"
        print(f"| {combo} | {platform} | {'✅ 在' if name in actual else '❌ 缺'} |")
    if extra:
        print()
        print("多余（不在矩阵里）：")
        for name in extra:
            print(f"- `{name}`")
    print()
    print(f"缺失 {len(missing)} 份 / 多余 {len(extra)} 份")
    return 1 if (missing or extra) else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="发布资产完整性核对（issue #269 / #303）")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--tag", help="GitHub release tag（发布前预演可用快照 tag）")
    source.add_argument("--dir", help="本地已下载的资产目录")
    parser.add_argument("--version", required=True, help="版本（如 0.1.4）")
    parser.add_argument("--allow-partial", action="store_true",
                        help="只报告不判红（预演用：快照 tag 本来就不含 Stable 的 12 份）")
    args = parser.parse_args(argv)

    expected = matrix_names(args.version)
    actual = from_release(args.tag) if args.tag else from_dir(Path(args.dir))
    code = report(expected, actual, args.tag or args.dir)
    return 0 if (code == 0 or args.allow_partial) else code


if __name__ == "__main__":
    sys.exit(main())
