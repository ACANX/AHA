#!/usr/bin/env python3
"""AHA 重复代码率检查（基于 PMD CPD 报告）。

重复代码是维护成本的直接来源：改一处忘一处，行为就会分叉。本项目用 PMD CPD
按「最小 token 数」找出成段重复，再由本脚本折算成**重复率**并卡阈值。

用法：

    # 1) 先由 Maven 生成报告（各模块 target/cpd.xml）
    ./mvnw -B pmd:cpd

    # 2) 再检查（默认阈值见 DEFAULT_THRESHOLD）
    python3 bin/CheckDuplication.py
    python3 bin/CheckDuplication.py --threshold 3.0 --list 10
    python3 bin/CheckDuplication.py --quiet

统计口径（必须与文档一致）：

    重复行数 = 每个 duplication 块的 (出现次数 - 1) × 块行数，逐块累加
    总行数   = 各模块 src/main/java 下 *.java 的物理行数合计
    重复率   = 重复行数 / 总行数

  - 只统计**首次出现之外**的副本，与常见重复率工具的语义一致
  - 块之间若互相重叠会重复计数（偏保守，宁可高估）
  - 只统计主源码（CPD 默认不含测试源码）
  - 报告缺失时直接失败，不做静默跳过——否则 CI 上「没跑」会被误读成「通过」

退出码：0 = 通过；1 = 超阈值或报告缺失。
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

#: 默认阈值（百分点），卡的是**合计**重复率。
#: 0.1.0 实测 0.40%（74/18633 行，最高模块 aha-core 1.05%），留出 5 倍余量，
#: 既能吸收正常重构，又能拦住成规模的复制粘贴。
DEFAULT_THRESHOLD = 2.0

#: CPD 报告相对于模块根的位置。
REPORT_NAME = "cpd.xml"

#: 参与统计的源码目录（相对模块根）。
SOURCE_DIR = Path("src/main/java")


def module_reports() -> list[Path]:
    """收集各模块的 CPD 报告（按模块名排序）。"""
    reports = []
    for module in sorted(ROOT.iterdir()):
        if not module.is_dir() or not (module / "pom.xml").is_file():
            continue
        report = module / "target" / REPORT_NAME
        if report.is_file():
            reports.append(report)
    return reports


def local_name(tag: str) -> str:
    """去掉 XML 命名空间前缀。"""
    return tag.rsplit("}", 1)[-1]


def parse_report(report: Path) -> list[tuple[int, int]]:
    """解析一份报告，返回 [(块行数, token 数)]。"""
    blocks = []
    root = ET.parse(report).getroot()
    for node in root:
        if local_name(node.tag) != "duplication":
            continue
        lines = int(node.get("lines") or 0)
        tokens = int(node.get("tokens") or 0)
        occurrences = sum(1 for child in node if local_name(child.tag) == "file")
        if lines > 0 and occurrences > 1:
            # 只有副本计入重复行数（首次出现不算重复）
            blocks.append((lines * (occurrences - 1), tokens))
    return blocks


def source_lines(module_dir: Path) -> int:
    """模块主源码的物理行数。"""
    total = 0
    source_root = module_dir / SOURCE_DIR
    if not source_root.is_dir():
        return 0
    for java in sorted(source_root.rglob("*.java")):
        try:
            total += len(java.read_text(encoding="utf-8", errors="replace").splitlines())
        except OSError:
            continue
    return total


def main() -> int:
    parser = argparse.ArgumentParser(description="检查重复代码率（基于 PMD CPD 报告）")
    parser.add_argument(
        "--threshold",
        type=float,
        default=DEFAULT_THRESHOLD,
        help=f"允许的最大重复率（%%），默认 {DEFAULT_THRESHOLD}",
    )
    parser.add_argument("--list", type=int, default=5, help="列出重复最多的前 N 个模块，默认 5")
    parser.add_argument("--quiet", action="store_true", help="只输出结论")
    args = parser.parse_args()

    reports = module_reports()
    if not reports:
        print("❌ 未找到任何 CPD 报告（期望 <模块>/target/cpd.xml）", file=sys.stderr)
        print("   先生成报告：./mvnw -B pmd:cpd", file=sys.stderr)
        return 1

    rows = []
    for report in reports:
        module = report.parent.parent
        blocks = parse_report(report)
        duplicated = sum(lines for lines, _ in blocks)
        total = source_lines(module)
        if total == 0:
            continue
        rows.append((module.name, len(blocks), duplicated, total, duplicated / total * 100))

    duplicated_all = sum(r[2] for r in rows)
    total_all = sum(r[3] for r in rows)
    ratio = duplicated_all / total_all * 100 if total_all else 0.0

    if not args.quiet:
        print("模块                  重复块   重复行 /   总行      重复率")
        for name, blocks, duplicated, total, percent in rows:
            print(f"  {name:<18} {blocks:>4}  {duplicated:>6} / {total:>6}     {percent:>5.2f}%")
        print(f"  {'合计':<18} {'':>4}  {duplicated_all:>6} / {total_all:>6}     {ratio:>5.2f}%")
        if args.list > 0:
            hot = sorted(rows, key=lambda r: r[4], reverse=True)[: args.list]
            hot = [r for r in hot if r[1] > 0]
            if hot:
                print("\n重复率最高的模块：")
                for name, blocks, duplicated, _total, percent in hot:
                    print(f"  {name:<18} {percent:>5.2f}%（{blocks} 块，{duplicated} 行）")

    if ratio > args.threshold:
        print(f"\n❌ 重复率 {ratio:.2f}% 超过阈值 {args.threshold:.2f}%")
        return 1
    print(f"\n✅ 重复率 {ratio:.2f}% 未超过阈值 {args.threshold:.2f}%")
    return 0


if __name__ == "__main__":
    sys.exit(main())
