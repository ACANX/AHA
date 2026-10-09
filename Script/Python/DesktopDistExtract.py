#!/usr/bin/env python3
r"""AHA 桌面端便携包解压并更新 Dist/（把 zip 里的 lib/ 与 bin/ 铺进 Dist）。

配套 `DesktopDistBuild.py` 使用。构建出来的便携包是自包含的：

    Dist/aha-desktop-<版本>-<平台>.zip
      ├── bin/   AhaDesktop.sh、AhaDesktop.bat
      └── lib/   JPMS 模块路径（本项目模块 + 全部运行时依赖 + 本平台 OpenJFX）

解压它就能直接跑，不必记 `--module-path`。本脚本把「选包 -> 解压 -> 更新 Dist/」压成
一条命令，并把 `lib/` 与 `bin/` 覆盖到 `Dist/`：

    python3 Script\Python\DesktopDistExtract.py

（POSIX 下把路径分隔符换成 `/`；本机命令若是 `python` 而非 `python3`，换成 `python`。）

三个关键取舍：

1. **只铺 `lib/` 与 `bin/`**，不碰 `README.md` / `CHANGELOG.md` / `LICENSE`：
   它们是「包」的说明，不是运行所需的文件，铺到 `Dist/` 只会造成混乱
   （与 `DesktopNativeVersionUpdate.py` 只取可执行文件同一条思路）。
2. **先解压到暂存目录，全部成功后再逐个 `os.replace` 落地**：中途失败时 `Dist/` 保持原样，
   不会落一个「删了一半」的发行目录。
3. **落盘前清掉上一版桌面端独占的 jar**（`aha-desktop-*.jar`、`javafx-*.jar`）：
   版本升级后旧 jar 残留会让 JPMS 模块路径上出现同一模块的两个版本，
   直接 `java.lang.module.FindException`。共享依赖（core / common / tool / 第三方）原地覆盖，
   不动仅属于 CLI 的 `aha-cli-*.jar`，因此 `Dist/` 同时保留 CLI 用法。

另外：每次更新都会把命令速查 `Docs/Guide/CommandCheatsheet.md` 刷新为
`Dist/README.commands.md`，让「怎么编 / 怎么启动」在发行目录里随手可见。

退出码：0 = 更新成功；1 = 找不到包 / 包不合格 / 写入被占用；130 = 用户中断。
"""

from __future__ import annotations

import argparse
import os
import platform
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path

#: 仓库根（本脚本位于 Script/Python/ 下）
ROOT = Path(__file__).resolve().parents[2]

#: 桌面端便携包的命名；`[0-9]` 用于排除 `aha-desktop-native-*.zip`（原生镜像包）。
ZIP_GLOB = "aha-desktop-[0-9]*.zip"

#: 只搬运包内的这两个目录。
TRANSFER_PREFIXES = ("lib/", "bin/")

#: 上一版桌面端独占、需要先清掉的 jar（不碰共享依赖，也不碰 aha-cli）。
PRUNE_PATTERNS = ("aha-desktop-*.jar", "javafx-*.jar")

#: 便携包里必须存在的 OpenJFX 工件个数（本平台三件套）。
EXPECTED_JAVAFX_JARS = 3

#: 文档 / 帮助里统一展示的用法（Windows 路径分隔符）。
USAGE_CMD = r"python3 Script\Python\DesktopDistExtract.py"

#: 命令速查的权威文件与在 Dist/ 里的落地名。
CHEATSHEET_SOURCE = ROOT / "Docs/Guide/CommandCheatsheet.md"
CHEATSHEET_NAME = "README.commands.md"


class ExtractError(Exception):
    """可预期的失败：找不到包、包不合格、目标被占用等。"""


def configure_stdio() -> None:
    """让中文输出在 Windows 控制台不因编码炸掉（同 DesktopNativeVersionUpdate.py）。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[union-attr]
        except (AttributeError, ValueError):  # pragma: no cover - 非标准流
            pass


def dist_dir() -> Path:
    """项目根的发行目录（优先沿用已存在的名字，见 DesktopDistBuild.py）。"""
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


def format_size(num: int) -> str:
    """人类可读的字节数。"""
    value = float(num)
    for unit in ("B", "KiB", "MiB", "GiB"):
        if abs(value) < 1024 or unit == "GiB":
            return f"{int(value)} B" if unit == "B" else f"{value:.1f} {unit}"
        value /= 1024
    return f"{value:.1f} GiB"


def print_zips(zips: list[Path]) -> None:
    for path in zips:
        print(f"  * {path.name}（{format_size(path.stat().st_size)}）")


def pick_zip(dist: Path, classifier: str, explicit: str | None) -> Path:
    """挑选要解压的包：显式 --zip > 本平台最新 > 报错并列出现有包。

    不做「没有本平台包就随便拿一个」的降级：装错平台的 JavaFX 原生库启动即崩，
    与其让用户排半天，不如在这里点名现有包。
    """
    if explicit:
        candidate = Path(explicit).expanduser()
        if not candidate.is_absolute():
            candidate = Path.cwd() / candidate
        if not candidate.is_file():
            raise ExtractError(f"--zip 指向的文件不存在：{candidate}")
        if candidate.suffix.lower() != ".zip":
            raise ExtractError(f"--zip 要求一个 zip 文件，实际是：{candidate}")
        return candidate.resolve()

    candidates = [path for path in dist.glob(ZIP_GLOB) if path.is_file()]
    if not candidates:
        raise ExtractError(
            f"在 {dist} 下没有找到 {ZIP_GLOB}；请先运行："
            r"python3 Script\Python\DesktopDistBuild.py"
        )
    if classifier:
        matching = [path for path in candidates if path.name.endswith(f"-{classifier}.zip")]
        if not matching:
            print(f"未找到本平台（{classifier}）的包，现有：", file=sys.stderr)
            print_zips(candidates)
            raise ExtractError("请用 --zip 显式指定，或先用 DesktopDistBuild.py 构建本平台包")
        candidates = matching
    return max(candidates, key=lambda path: path.stat().st_mtime)


def verify_zip(path: Path) -> list[str]:
    """解压前先看清 zip 内容；返回问题列表（空表示合格）。

    判据与 `Release.yml` / `DesktopDistBuild.py` 对齐，避免把一个原生镜像包或残缺包
    铺进 Dist/。用「恰好 3 个 OpenJFX jar」而不是「至少 3 个」，理由见构建脚本。
    """
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
    except zipfile.BadZipFile as error:
        raise ExtractError(f"{path.name} 不是有效的 zip：{error}") from error

    jars = [name for name in names if name.startswith("lib/") and name.endswith(".jar")]
    problems: list[str] = []
    if not any(Path(name).name.startswith("aha-desktop-") for name in jars):
        problems.append("缺 aha-desktop 模块 jar")
    javafx = [name for name in jars if Path(name).name.startswith("javafx-")]
    if len(javafx) != EXPECTED_JAVAFX_JARS:
        problems.append(f"本平台 OpenJFX jar 应为 {EXPECTED_JAVAFX_JARS} 个，实际 {len(javafx)} 个")
    for entry in ("bin/AhaDesktop.sh", "bin/AhaDesktop.bat"):
        if entry not in names:
            problems.append(f"缺启动脚本 {entry}")
    return problems


def stage(zip_path: Path, staging: Path) -> None:
    """把 zip 里的 lib/ 与 bin/ 解到暂存目录（保持可执行位）。"""
    with zipfile.ZipFile(zip_path) as archive:
        for name in archive.namelist():
            if name.endswith("/") or not name.startswith(TRANSFER_PREFIXES):
                continue
            target = staging / name
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(name) as source, target.open("wb") as sink:
                shutil.copyfileobj(source, sink)
            if name.endswith(".sh"):
                try:
                    target.chmod(0o755)
                except OSError:  # pragma: no cover - 某些文件系统不支持
                    pass


def apply(staging: Path, dist: Path, prune: bool) -> tuple[list[Path], list[Path], list[Path]]:
    """把暂存内容落地到 Dist/，并清掉上一版独占 jar。

    返回 (新增, 覆盖, 删除)。清理只针对 `lib/` 下的 desktop 独占 jar，且跳过本次刚写入的
    同名文件——共享依赖与 `aha-cli-*.jar` 不受影响。
    """
    added: list[Path] = []
    updated: list[Path] = []

    for staged in sorted(path for path in staging.rglob("*") if path.is_file()):
        target = dist / staged.relative_to(staging)
        target.parent.mkdir(parents=True, exist_ok=True)
        existed = target.exists()
        try:
            os.replace(staged, target)
        except OSError as error:
            raise ExtractError(
                f"写入 {target} 失败：{error}。"
                "最常见的原因是桌面端仍在运行 —— 请先关闭，再重试"
            ) from error
        (updated if existed else added).append(target)

    removed: list[Path] = []
    lib = dist / "lib"
    if prune and lib.is_dir():
        keep = {path.name for path in (*added, *updated) if path.parent == lib}
        for pattern in PRUNE_PATTERNS:
            for old in sorted(lib.glob(pattern)):
                if old.name in keep:
                    continue
                try:
                    old.unlink()
                except OSError as error:
                    raise ExtractError(f"删除旧文件 {old} 失败：{error}") from error
                removed.append(old)
    return added, updated, removed


def refresh_cheatsheet(dist: Path) -> Path | None:
    """把命令速查刷新到 Dist/README.commands.md；源文件不存在时返回 None。

    不叫 `README.md`：那是 CLI assembly 每次 `mvn package` 覆盖的项目 README，
    两者同名会互相覆盖。速查是开发向的，放在另一个固定名字下更稳。
    """
    if not CHEATSHEET_SOURCE.is_file():
        return None
    target = dist / CHEATSHEET_NAME
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(CHEATSHEET_SOURCE, target)
    return target


def run(args: argparse.Namespace) -> int:
    dist = Path(args.dist).expanduser().resolve() if args.dist else dist_dir()
    classifier = host_classifier()
    zip_path = pick_zip(dist, classifier, args.zip_path or args.zip)

    print(f"项目根：{ROOT}")
    print(f"发行目录：{dist}")
    print(f"安装包：{zip_path}（{format_size(zip_path.stat().st_size)}）")

    problems = verify_zip(zip_path)
    if problems:
        for problem in problems:
            print(f"  [不合格] {problem}", file=sys.stderr)
        raise ExtractError(f"{zip_path.name} 不是合格的桌面端便携包")

    if args.dry_run:
        with zipfile.ZipFile(zip_path) as archive:
            entries = [
                name
                for name in archive.namelist()
                if not name.endswith("/") and name.startswith(TRANSFER_PREFIXES)
            ]
        print(f"\n[dry-run] 将写入 {len(entries)} 个文件到 {dist}：")
        for name in sorted(entries):
            print(f"  {name}")
        if CHEATSHEET_SOURCE.is_file():
            print(f"  ({CHEATSHEET_NAME}：刷新自 {CHEATSHEET_SOURCE.name})")
        print("去掉 --dry-run 即执行。")
        return 0

    dist.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".aha-desktop-stage-", dir=dist) as tmp:
        staging = Path(tmp)
        stage(zip_path, staging)
        added, updated, removed = apply(staging, dist, prune=not args.no_prune)
    cheatsheet = refresh_cheatsheet(dist)

    print(f"\n新增 {len(added)} 个，覆盖 {len(updated)} 个"
          f"{'，清理旧桌面端 jar ' + str(len(removed)) + ' 个' if removed else ''}：")
    for path in (*added, *updated):
        print(f"  {'+' if path in added else '~'} {path.relative_to(dist)}")
    for path in removed:
        print(f"  - {path.relative_to(dist)}")
    if cheatsheet is not None:
        print(f"  ~ {cheatsheet.relative_to(dist)}（命令速查）")

    print("\n✅ Dist/ 已更新为便携包内容")
    print(r"   运行（Windows）：Dist\bin\AhaDesktop.bat")
    print("   运行（POSIX）  ：./Dist/bin/AhaDesktop.sh")
    return 0


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="DesktopDistExtract.py",
        description="解压 AHA 桌面端便携包，并把 lib/ 与 bin/ 更新到 Dist/。",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "示例（Windows；POSIX 下把反斜杠换成 `/`）：\n"
            f"  {USAGE_CMD}                                  # 用本平台最新的包\n"
            f"  {USAGE_CMD} --zip Dist\\aha-desktop-0.1.1-win.zip  # 指定包\n"
            f"  {USAGE_CMD} --dry-run                        # 只列出将写入的文件\n"
            f"  {USAGE_CMD} --no-prune                       # 不清理旧桌面端 jar\n"
        ),
    )
    parser.add_argument("zip", nargs="?", help="要解压的 zip（默认取本平台最新的包）")
    parser.add_argument("--zip", dest="zip_path", help="同位置参数，显式指定 zip")
    parser.add_argument("--dist", help="发行目录（默认仓库根的 Dist/；兼容小写 dist/）")
    parser.add_argument("--no-prune", action="store_true",
                        help="不清理 lib/ 里上一版桌面端独占的 jar")
    parser.add_argument("--dry-run", action="store_true", help="只列出将写入的文件，不落地")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    configure_stdio()
    args = parse_args(argv)
    try:
        return run(args)
    except ExtractError as error:
        print(f"\n[错误] {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("\n[中断] 已取消", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
