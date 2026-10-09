#!/usr/bin/env python3
r"""收集 AHA 桌面端的渲染诊断信息（issue #48，字体渲染差异排查）。

为什么需要它：要定案必须拿到「原生包」与「JVM 模式」两份日志里的**同几行**做对照，
而日志默认落在 `Log/AHA.log`（见 `LoggingSetup.resolveLogFile`），位置随启动方式变化：
便携包在解压目录、源码检出在仓库根、设了 `AHA_HOME` 又在别处。
本脚本把「找日志 → 抽诊断行 → 生成可粘贴的报告」压成一条命令：

    python3 Script/Python/CollectRenderDiagnostics.py
    python3 Script/Python/CollectRenderDiagnostics.py --log D:\AHA\Log\AHA.log --mode native

诊断行由 `AhaDesktopApp.logRenderingDiagnostics()` 打印（以 `渲染诊断：` 开头）；
渲染管线那几行由 JavaFX 在 `prism.verbose` 下打印（`Prism pipeline init order` /
`Prism pipeline name =` / `Loaded library` / `Fallback to Prism`）。
`LoggingSetup` 的 console appender **只输出 ERROR**，所以这些 INFO 级内容**只在文件里**，
双击 exe 看到的黑框不会显示它们。

三个关键取舍：

1. **不猜日志位置，按已知规则逐个找**：命令行参数 → `$AHA_HOME/Log/AHA.log` →
   当前目录及其上溯各层的 `Log/AHA.log`。找到的第一个存在的即用，并把候选清单打出来，
   免得「找不到」时还要问一遍。
2. **只取最后一次启动**：日志是追加写的，多次启动的片段都在。以 JavaFX 每次启动都会
   打印的 `Prism pipeline init order` 作分界，只保留最后一段——否则原生包与 JVM 模式
   的两次启动会混在一份报告里（那两个文件通常不同，但同一台机器重复启动很常见）。
3. **报告写成可直接粘贴的 Markdown**：排查是异步的（ACANX 在真机跑、维护者看报告），
   格式固定能省掉一轮「你贴的是什么」；`java.vm.name` 本身还是「原生包还是 JVM 模式」
   的铁证——原生镜像打印的是 `Substrate VM`。

用法：

    python3 Script/Python/CollectRenderDiagnostics.py [--log PATH] [--mode MODE] [--out FILE]

（POSIX 下把路径分隔符换成 `/`；本机命令若是 `python` 而非 `python3`，换成 `python`。）
"""

from __future__ import annotations

import argparse
import os
import platform
import re
import sys
from datetime import datetime
from pathlib import Path

#: 需要的行：渲染诊断 + JavaFX 的管线输出 + 原生库加载（#48 的关键旁证）
KEY = re.compile(
    r"渲染诊断：|Prism pipeline|Loaded library|Fallback to Prism|"
    r"Unsupported JavaFX configuration"
)

#: 一次启动的分界行：JavaFX 每次启动都会先打印它
LAUNCH_MARK = "Prism pipeline init order"

#: 默认日志文件名（与 LoggingSetup.LOG_FILE_NAME 一致）
LOG_FILE_NAME = "AHA.log"


class Report:
    """收集结果：来源、元信息与抽出的行。"""

    def __init__(self, source: Path, mode: str) -> None:
        self.source = source
        self.mode = mode
        self.total_lines = 0
        self.rows: list[str] = []


def candidates(explicit: Path | None) -> list[Path]:
    """列出候选日志路径，按优先级排列。

    @param explicit 命令行显式指定的路径（可为空）
    @return 候选路径列表
    """
    found: list[Path] = []
    if explicit is not None:
        found.append(explicit)

    home = os.environ.get("AHA_HOME")
    if home:
        found.append(Path(home) / "Log" / LOG_FILE_NAME)

    # 当前目录与其上溯各层：便携包从解压目录启动、源码检出从仓库根启动
    here = Path.cwd().resolve()
    for base in [here, *here.parents]:
        found.append(base / "Log" / LOG_FILE_NAME)

    # 脚本自身所在仓库根（用户可能在别处调用本脚本）
    script_root = Path(__file__).resolve().parent.parent.parent
    found.append(script_root / "Log" / LOG_FILE_NAME)
    return found


def collect(path: Path, mode: str) -> Report:
    """读取日志并抽出诊断行。

    @param path 日志文件
    @param mode 采集模式标记（native / jvm / 空）
    @return 报告
    """
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()
    report = Report(path, mode)
    report.total_lines = len(lines)

    # 只保留最后一次启动：以 LAUNCH_MARK 为界
    starts = [i for i, line in enumerate(lines) if LAUNCH_MARK in line]
    if starts:
        lines = lines[starts[-1]:]

    for line in lines:
        if KEY.search(line):
            report.rows.append(line.rstrip())
    return report


def render(report: Report) -> str:
    """把报告渲染成可直接粘贴的 Markdown。

    @param report 报告
    @return Markdown 文本
    """
    mode = report.mode or "未指定"
    out = [
        "# AHA 渲染诊断报告（issue #48）",
        "",
        f"- 生成时间：{datetime.now():%Y-%m-%d %H:%M:%S}",
        f"- 采集模式（--mode）：{mode}",
        f"- 来源日志：{report.source}",
        f"- 日志总行数：{report.total_lines}",
        f"- 采集机：{platform.platform()} · Python {platform.python_version()}",
        "",
        "> 判定要点：`java.vm.name` 为 `Substrate VM` 说明是**原生包**，",
        "> 为 HotSpot 说明是 **JVM 模式**；`渲染诊断：字体实现工厂=` 两边若不同",
        "> （`DWFactory` vs `FTFactory`），即命中 issue #48 的方向。",
        "",
        "## 诊断行",
        "",
        "```text",
    ]
    out.extend(report.rows or ["（没有抽到诊断行：确认这个包是否已含 PR #58 的诊断代码，"
                               "以及日志级别是否为 INFO）"])
    out.extend(["```", ""])
    return "\n".join(out)


def main(argv: list[str] | None = None) -> int:
    """入口。

    @param argv 命令行参数；为空时取 sys.argv
    @return 进程退出码
    """
    parser = argparse.ArgumentParser(
        description="收集 AHA 桌面端的渲染诊断信息（issue #48）")
    parser.add_argument("--log", type=Path, default=None,
                        help="日志文件路径；不给则按已知规则自动查找")
    parser.add_argument("--mode", default="", choices=["", "native", "jvm"],
                        help="采集模式标记，只影响报告头部")
    parser.add_argument("--out", type=Path, default=None,
                        help="报告输出文件；不给则只打印到终端")
    args = parser.parse_args(argv)

    tried = candidates(args.log)
    source = None
    for path in tried:
        if path.is_file() and path.stat().st_size > 0:
            source = path
            break
    if source is None:
        print("找不到日志文件。已尝试：", file=sys.stderr)
        for path in tried:
            print(f"  - {path}", file=sys.stderr)
        print("\n提示：日志位于启动目录（或 AHA_HOME）下的 Log/AHA.log；"
              "也可以用 --log 显式指定。", file=sys.stderr)
        return 1

    report = collect(source, args.mode)
    text = render(report)
    print(text)

    if args.out is not None:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(text, encoding="utf-8")
        print(f"已写入：{args.out}")
    if not report.rows:
        # 抽不到不是脚本错误，但要让调用方知道「这份报告没用」
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
