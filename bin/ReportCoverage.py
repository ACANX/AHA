#!/usr/bin/env python3
"""AHA 覆盖率实测值汇总（读 JaCoCo 的 CSV 报告）。

为什么需要它：JaCoCo 的 `check` 在**通过时什么都不打印**，日志里只有
`Loading execution data file` 与 `Analyzed bundle '...' with N classes` 两行——
**没有任何百分比**。于是「门禁生效」与「门禁没配」在日志上看起来完全一样，
只能靠翻 HTML 报告或临时改阈值去试。本脚本把实测值直接打到日志里，让每次门禁
都能自证：数字在，说明报告生成过；数字变了，一眼能看见。

用法：

    ./mvnw clean verify                 # 先生成报告（并执行门禁）
    python3 bin/ReportCoverage.py
    python3 bin/ReportCoverage.py --exclude aha-desktop aha-todo

统计口径（与文档一致）：

    合计行覆盖 = 各参与模块 LINE_COVERED / (LINE_COVERED + LINE_MISSED) 求和
    `aha-desktop` 为 0.1 占位模块，由 pom.xml 的 jacoco excludes 排除，不计入合计

门禁**判定**由 `jacoco-maven-plugin:check` 依 pom.xml 的规则执行（`BUNDLE` /
`LINE` / `COVEREDRATIO`）。本脚本只报数、不重复判定——单一判定来源，避免两处口径。
因此：

  - 在门禁之后运行本脚本：能跑到这里，就说明门禁已通过；
  - 在门禁之前运行：它只反映上次遗留的报告，别据此下结论。

退出码：0 = 正常输出；1 = 未找到报告（先跑 `./mvnw clean verify`）。
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPORT = Path("target/site/jacoco/jacoco.csv")
POM = ROOT / "pom.xml"

#: 从门禁排除的模块（pom.xml 的 jacoco `<excludes>` 覆盖其全部类）。
#: 改动 pom 的 excludes 时须同步此列表。
EXCLUDED_MODULES = ("aha-desktop",)


def threshold() -> str:
    """从 pom.xml 读取门禁阈值，避免在脚本里复制一份。"""
    try:
        text = POM.read_text(encoding="utf-8")
    except OSError:
        return "?"
    match = re.search(r"<minimum>([\d.]+)</minimum>", text)
    return match.group(1) if match else "?"


def module_stats(csv_path: Path) -> tuple[int, int, int, int]:
    """返回 (行覆盖, 行未覆盖, 分支覆盖, 分支未覆盖)。"""
    line_covered = line_missed = branch_covered = branch_missed = 0
    with csv_path.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            line_covered += int(row["LINE_COVERED"])
            line_missed += int(row["LINE_MISSED"])
            branch_covered += int(row["BRANCH_COVERED"])
            branch_missed += int(row["BRANCH_MISSED"])
    return line_covered, line_missed, branch_covered, branch_missed


def width(text: str) -> int:
    """显示宽度（CJK 字符按 2 列算）。"""
    import unicodedata
    return sum(2 if unicodedata.east_asian_width(c) in "WF" else 1 for c in text)


def pad(text: str, target: int, right: bool = False) -> str:
    """按显示宽度补空格。直接用 f-string 的 `:<n` 是按字符数补，CJK 会错列。"""
    fill = " " * max(0, target - width(text))
    return fill + text if right else text + fill


def main() -> int:
    parser = argparse.ArgumentParser(description="输出 JaCoCo 覆盖率实测值")
    parser.add_argument(
        "--exclude",
        nargs="*",
        default=list(EXCLUDED_MODULES),
        help="不计入合计的模块名，默认 aha-desktop",
    )
    args = parser.parse_args()

    reports = sorted(ROOT.glob(f"*/{REPORT}"))
    if not reports:
        print("❌ 未找到 JaCoCo 报告（期望 <模块>/target/site/jacoco/jacoco.csv）", file=sys.stderr)
        print("   先生成报告：./mvnw clean verify", file=sys.stderr)
        return 1

    excluded = set(args.exclude)
    rows = []
    for report in reports:
        module = report.relative_to(ROOT).parts[0]
        line_c, line_m, br_c, br_m = module_stats(report)
        if line_c + line_m == 0:
            continue
        rows.append((module, line_c, line_m, br_c, br_m))

    print("=== 覆盖率实测（JaCoCo） ===")
    print(pad("模块", 22) + pad("行覆盖", 12, True) + pad("分支覆盖", 12, True))
    total_c = total_m = total_bc = total_bm = 0
    for module, line_c, line_m, br_c, br_m in rows:
        ratio = line_c / (line_c + line_m) * 100
        branch = f"{br_c / (br_c + br_m) * 100:.1f}%" if br_c + br_m else "—"
        mark = "（门禁排除）" if module in excluded else ""
        print("  " + pad(module, 20) + pad(f"{ratio:.1f}%", 10, True)
              + pad(f"{line_c}/{line_c + line_m}", 14, True) + pad(branch, 10, True) + mark)
        if module not in excluded:
            total_c += line_c
            total_m += line_m
            total_bc += br_c
            total_bm += br_m

    if total_c + total_m:
        total = total_c / (total_c + total_m) * 100
        branch_total = f"{total_bc / (total_bc + total_bm) * 100:.1f}%" if total_bc + total_bm else "—"
        print("  " + pad("合计", 20) + pad(f"{total:.1f}%", 10, True)
              + pad(f"{total_c}/{total_c + total_m}", 14, True) + pad(branch_total, 10, True))
    if excluded:
        print(f"  合计不含：{'、'.join(sorted(excluded))}（0.1 占位模块，由 pom.xml 的 jacoco excludes 排除）")
    print(f"\n门禁阈值：{threshold()}（读自 pom.xml；判定由 jacoco:check 执行，本脚本只报数）")
    print("报告：<模块>/target/site/jacoco/index.html")
    return 0


if __name__ == "__main__":
    sys.exit(main())
