#!/usr/bin/env python3
r"""AHA 桌面端 JVM 便携包一键构建（源码 -> Dist/aha-desktop-<版本>-<平台>.zip）。

桌面端的 Maven assembly（`aha-desktop/src/assembly/dist-desktop.xml`）在 `package`
阶段把 `bin/` + `lib/` 打成 zip 落到项目根的 `Dist/`。手工敲要记住一串参数：

    ./mvnw -pl aha-desktop -am package -DskipTests -Djacoco.skip=true

本脚本把这条命令固定下来，并在构建后**自证产物**——zip 里必须含 aha-desktop 模块、
**3 个本平台 OpenJFX jar** 与两个启动脚本（`bin/AhaDesktop.sh` / `.bat`）。
「构建成功但包是空的 / 少装了一个平台原生库」会在这一步当场失败，
而不是等到用户双击启动才暴露（与 `Release.yml` 的便携包自证同一判据）。

用法（Windows）：

    python3 Script\Python\DesktopDistBuild.py
    python3 Script\Python\DesktopDistBuild.py --clean --with-tests
    python3 Script\Python\DesktopDistBuild.py --dry-run

（POSIX 下把路径分隔符换成 `/`；本机命令若是 `python` 而非 `python3`，换成 `python`。）

为什么默认跳过测试：本脚本只负责「出包」，测试属于 `test` / `verify` 的职责；
每次打包都跑一遍测试会拖到分钟级，与「本地只跑快检查」的约定冲突。
确需在打包前跑测试时用 `--with-tests` 显式打开。

退出码：0 = 构建成功且产物自证通过；1 = 构建失败或产物不合格；130 = 用户中断。
"""

from __future__ import annotations

import argparse
import os
import platform
import subprocess
import sys
import time
import zipfile
from pathlib import Path

#: 仓库根（本脚本位于 Script/Python/ 下）
ROOT = Path(__file__).resolve().parents[2]

#: 桌面端便携包的命名；`[0-9]` 用于排除 `aha-desktop-native-*.zip`（原生镜像包）。
ZIP_GLOB = "aha-desktop-[0-9]*.zip"

#: 文档 / 帮助里统一展示的用法（Windows 路径分隔符）。
USAGE_CMD = r"python3 Script\Python\DesktopDistBuild.py"

#: 便携包里必须存在的 OpenJFX 工件个数（本平台三件套：base / graphics / controls）。
EXPECTED_JAVAFX_JARS = 3

#: 启动脚本（zip 内路径）。
LAUNCHER_ENTRIES = ("bin/AhaDesktop.sh", "bin/AhaDesktop.bat")


class BuildError(Exception):
    """可预期的失败：找不到 Wrapper、Maven 非零退出、产物缺失或不合格。"""


def configure_stdio() -> None:
    """让中文输出在 Windows 控制台不因编码炸掉（同 DesktopNativeVersionUpdate.py）。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[union-attr]
        except (AttributeError, ValueError):  # pragma: no cover - 非标准流
            pass


def dist_dir() -> Path:
    """项目根的发行目录。

    项目约定用大驼峰 `Dist`（Maven 的 assembly 也输出到这里）；同时兼容旧的小写 `dist`，
    优先沿用已存在的那个名字。
    """
    for name in ("Dist", "dist"):
        candidate = ROOT / name
        if candidate.is_dir():
            return candidate
    return ROOT / "Dist"


def host_classifier() -> str:
    """本机对应的 JavaFX 分类器（见 DesktopDesign.md 第 5 节）；未知平台返回空串。"""
    machine = platform.machine().lower()
    arm = machine in ("aarch64", "arm64")
    if sys.platform.startswith("win"):
        return "win"
    if sys.platform == "darwin":
        return "mac-aarch64" if arm else "mac"
    if sys.platform.startswith("linux"):
        return "linux-aarch64" if arm else "linux"
    return ""


def maven_command(args: argparse.Namespace) -> list[str]:
    """拼出 Maven 调用：Wrapper 前缀 + 项目参数。"""
    if os.name == "nt":
        # Windows 上 CreateProcess 不能直接执行 .cmd，必须经 cmd。
        wrapper = ROOT / "mvnw.cmd"
        prefix = ["cmd", "/c", str(wrapper)]
    else:
        wrapper = ROOT / "mvnw"
        prefix = [str(wrapper)]
    if not wrapper.exists():
        raise BuildError(f"未找到 Maven Wrapper：{wrapper}")

    command = ["-pl", "aha-desktop", "-am"]
    if args.clean:
        command.append("clean")
    command.append("package")
    # 只出包：跳过测试与覆盖率仪表（本脚本不做质量门禁，门禁在 verify / CI）。
    if not args.with_tests:
        command.append("-DskipTests")
    command.append("-Djacoco.skip=true")
    if args.javafx_platform:
        # 应急覆盖：正常由父 POM 的 javafx-* profile 按本机解析，此项只用于交叉出包。
        command.append(f"-Djavafx.platform={args.javafx_platform}")
    return prefix + command


def run_maven(command: list[str], dry_run: bool) -> None:
    printable = " ".join(command)
    print(f"执行：{printable}")
    if dry_run:
        print("\n[dry-run] 只预览，不真正构建。去掉 --dry-run 即执行。")
        return
    try:
        completed = subprocess.run(command, cwd=ROOT, check=False)
    except OSError as error:
        raise BuildError(f"启动 Maven 失败：{error}") from error
    if completed.returncode != 0:
        raise BuildError(f"Maven 退出码 {completed.returncode}（见上方日志）")


def built_zip(dist: Path, started_at: float, classifier: str) -> Path:
    """定位刚构建出的便携包。

    先按「本次构建时间之后」筛，避免 `Dist/` 里并存的其他平台旧包被误认；
    文件系统时间戳精度有限，故留 1 秒余量。取不到新包时退回「本平台最新」。
    """
    candidates = [path for path in dist.glob(ZIP_GLOB) if path.is_file()]
    if not candidates:
        raise BuildError(
            f"构建结束但 {dist} 下没有 {ZIP_GLOB}；请检查 Maven 日志里的 assembly 阶段"
        )

    def mtime(path: Path) -> float:
        return path.stat().st_mtime

    fresh = [path for path in candidates if mtime(path) >= started_at - 1.0]
    pool = fresh or candidates
    if classifier:
        matching = [path for path in pool if path.name.endswith(f"-{classifier}.zip")]
        if matching:
            return max(matching, key=mtime)
    if not fresh:
        print(f"[提醒] 没有发现本次新增的包，改用现有最新包（可能来自上一次构建）", file=sys.stderr)
    return max(pool, key=mtime)


def verify_zip(path: Path) -> tuple[list[str], list[str]]:
    """打开 zip 自证内容；返回 (jar 列表, 问题列表)。

    判据与 `Release.yml` 的便携包完整性自证对齐：项目模块齐全、本平台 OpenJFX 恰好 3 个、
    两个启动脚本都在。多一个 OpenJFX jar 通常意味着空壳 jar 混进了包（module path 上会
    变成重名自动模块），因此这里用「恰好 3 个」而不是「至少 3 个」。
    """
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
    jars = [name for name in names if name.startswith("lib/") and name.endswith(".jar")]
    desktop = [name for name in jars if Path(name).name.startswith("aha-desktop-")]
    javafx = [name for name in jars if Path(name).name.startswith("javafx-")]
    problems: list[str] = []
    if not desktop:
        problems.append("缺 aha-desktop 模块 jar")
    if len(javafx) != EXPECTED_JAVAFX_JARS:
        problems.append(f"本平台 OpenJFX jar 应为 {EXPECTED_JAVAFX_JARS} 个，实际 {len(javafx)} 个")
    for entry in LAUNCHER_ENTRIES:
        if entry not in names:
            problems.append(f"缺启动脚本 {entry}")
    return jars, problems


def format_size(num: int) -> str:
    """人类可读的字节数。"""
    value = float(num)
    for unit in ("B", "KiB", "MiB", "GiB"):
        if abs(value) < 1024 or unit == "GiB":
            return f"{int(value)} B" if unit == "B" else f"{value:.1f} {unit}"
        value /= 1024
    return f"{value:.1f} GiB"


def run(args: argparse.Namespace) -> int:
    dist = Path(args.dist).expanduser().resolve() if args.dist else dist_dir()
    classifier = args.javafx_platform or host_classifier()
    print(f"项目根：{ROOT}")
    print(f"发行目录：{dist}")
    print(f"平台分类器：{classifier or '<未知，交给父 POM profile 解析>'}")

    command = maven_command(args)
    started_at = time.time()
    run_maven(command, args.dry_run)
    if args.dry_run:
        return 0

    archive = built_zip(dist, started_at, classifier)
    jars, problems = verify_zip(archive)
    print(f"\n产物：{archive}")
    print(f"大小：{format_size(archive.stat().st_size)}")
    print(f"包内 jar：{len(jars)} 个（含本平台 OpenJFX {sum('javafx-' in Path(n).name for n in jars)} 个）")
    if problems:
        for problem in problems:
            print(f"  [不合格] {problem}", file=sys.stderr)
        raise BuildError(f"产物自证未通过（{archive.name}）")

    print("\n✅ 便携包构建完成且自证通过")
    print(f"   解压并更新 Dist/：{USAGE_CMD.replace('DesktopDistBuild', 'DesktopDistExtract')}")
    return 0


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="DesktopDistBuild.py",
        description="构建 AHA 桌面端 JVM 便携包（Dist/aha-desktop-<版本>-<平台>.zip）。",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "示例（Windows；POSIX 下把反斜杠换成 `/`）：\n"
            f"  {USAGE_CMD}                     # 默认：跳过测试，出包并自证\n"
            f"  {USAGE_CMD} --clean             # 先 clean 再出包\n"
            f"  {USAGE_CMD} --with-tests        # 出包前跑一遍测试\n"
            f"  {USAGE_CMD} --dry-run           # 只打印将执行的 Maven 命令\n"
        ),
    )
    parser.add_argument("--dist", help="发行目录（默认仓库根的 Dist/；兼容小写 dist/）")
    parser.add_argument("--clean", action="store_true", help="先执行 clean 再 package")
    parser.add_argument("--with-tests", action="store_true",
                        help="不跳过测试（默认为出包而跳过）")
    parser.add_argument("--javafx-platform",
                        help="覆盖 JavaFX 平台分类器（应急，正常由父 POM profile 解析）")
    parser.add_argument("--dry-run", action="store_true", help="只打印 Maven 命令，不执行")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    configure_stdio()
    args = parse_args(argv)
    try:
        return run(args)
    except BuildError as error:
        print(f"\n[错误] {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("\n[中断] 已取消", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
