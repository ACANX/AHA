#!/usr/bin/env python3
r"""AHA 桌面端便携包解压并更新 Dist/（把包内平铺的入口铺进 Dist）。

配套 `DesktopDistBuild.py` 使用。构建出来的便携包是自包含的（issue #218 起为**平铺**形态）：

    Dist/aha-desktop-<版本>-<平台>.zip
      ├── aha-desktop.jar   单体 jar（含本项目模块、全部运行时依赖与本平台 OpenJFX）
      ├── AhaDesktop.sh     启动脚本（平铺，不再有 bin/）
      └── AhaDesktop.bat

解压它就能直接跑，不必记 `--module-path`。本脚本把「选包 -> 解压 -> 更新 Dist/」压成
一条命令，并把这三个入口平铺进 `Dist/`：

    python3 Script\Python\DesktopDistExtract.py

（POSIX 下把路径分隔符换成 `/`；本机命令若是 `python` 而非 `python3`，换成 `python`。）

三个关键取舍：

1. **只铺运行入口**（`aha-desktop.jar` 与两个启动脚本），不碰 `README.md` /
   `CHANGELOG.md` / `LICENSE`：它们是「包」的说明，不是运行所需的文件，
   铺到 `Dist/` 只会造成混乱（与 `DesktopNativeVersionUpdate.py` 只取可执行文件同一条思路）。
2. **先解压到暂存目录，全部成功后再逐个 `os.replace` 落地**：中途失败时 `Dist/` 保持原样，
   不会落一个「删了一半」的发行目录。
3. **落盘前清掉上一版（`lib/` + `bin/` 形态）的遗留文件**：升级到平铺形态后，
   旧的 `lib/aha-desktop-*.jar`、`lib/javafx-*.jar` 与 `bin/AhaDesktop.*` 会与新入口并存，
   让「跑的到底是哪一份」不可读（旧形态下更糟：JPMS 模块路径上出现同一模块的两个版本，
   直接 `java.lang.module.FindException`）。共享依赖与属于 CLI 的 `aha-cli-*.jar` /
   `bin/Aha.*` 一律不碰，因此 `Dist/` 仍同时保留 CLI 用法。

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

#: 只搬运包内的这三个入口（issue #218 起的**平铺**形态）。
TRANSFER_ENTRIES = ("aha-desktop.jar", "AhaDesktop.sh", "AhaDesktop.bat")

#: 上一版（`lib/` + `bin/` 形态）遗留、需要清掉的文件。
#: 不碰共享依赖（core / common / tool / 第三方），也不碰属于 CLI 的 aha-cli-*.jar 与 bin/Aha.*。
LEGACY_PRUNE = ("lib/aha-desktop-*.jar", "lib/javafx-*.jar", "bin/AhaDesktop.sh", "bin/AhaDesktop.bat")

#: 单体 jar 在包内的落地名（去版本号，issue #218）。
FATJAR_ENTRY = "aha-desktop.jar"

#: 启动脚本（zip 内路径，平铺在包根）。
LAUNCHER_ENTRIES = ("AhaDesktop.sh", "AhaDesktop.bat")

#: OpenJFX 原生库的名字特征（Windows / Linux / macOS 三种命名都在内）。
#: 判据刻意不看「有任意 .dll」——sqlite-jdbc 等也带原生库，那样会漏掉 JavaFX 缺失。
FX_NATIVE_HINTS = ("glass", "prism", "javafx_font", "decora", "javafx_iio")


def fatjar_problems(archive, entry: str) -> list[str]:
    """检查单体 jar **内部**：含 JavaFX 类、含本平台 OpenJFX 原生库、不含 module-info。

    OpenJFX 的原生库就在 javafx-graphics 分类器 jar 的根部，运行期由 JavaFX 自己从 jar
    取出并缓存（`NativeLibLoader.loadLibraryFromResource`），所以「jar 里有没有它」是可判的：
    少了它，用户双击启动只会看到「JavaFX runtime components are missing」。
    """
    import io

    problems: list[str] = []
    with archive.open(entry) as handle:
        payload = io.BytesIO(handle.read())
    try:
        with zipfile.ZipFile(payload) as jar:
            inner = jar.namelist()
    except zipfile.BadZipFile:
        return [f"{entry} 不是有效的 jar"]

    if "javafx/application/Application.class" not in inner:
        problems.append(f"{entry} 内含不到 JavaFX 类（javafx/application/Application.class）")
    natives = [name for name in inner if name.endswith((".dll", ".so", ".dylib"))]
    fx_natives = [name for name in natives if any(hint in Path(name).name.lower() for hint in FX_NATIVE_HINTS)]
    if not fx_natives:
        problems.append(f"{entry} 内没有本平台 OpenJFX 原生库（glass / prism / javafx_font 等）")
    if "module-info.class" in inner:
        problems.append(f"{entry} 不应含 module-info.class")
    return problems


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

    判据与 `DesktopDistBuild.py` 对齐（同一个平铺形态），避免把原生镜像包、旧形态包
    或残缺包铺进 Dist/。
    """
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            problems: list[str] = []
            if FATJAR_ENTRY not in names:
                problems.append(
                    f"缺单体 jar {FATJAR_ENTRY}（包内应为平铺形态）"
                    f"{'——包里出现 lib/ 旧形态，请改用新包' if any(n.startswith('lib/') for n in names) else ''}"
                )
            else:
                problems.extend(fatjar_problems(archive, FATJAR_ENTRY))
            for entry in TRANSFER_ENTRIES[1:]:
                if entry not in names:
                    problems.append(f"缺启动脚本 {entry}")
    except zipfile.BadZipFile as error:
        raise ExtractError(f"{path.name} 不是有效的 zip：{error}") from error
    return problems


def stage(zip_path: Path, staging: Path) -> None:
    """把 zip 里平铺的三个入口解到暂存目录（保持可执行位）。"""
    with zipfile.ZipFile(zip_path) as archive:
        for name in archive.namelist():
            if name not in TRANSFER_ENTRIES:
                continue
            target = staging / name
            with archive.open(name) as source, target.open("wb") as sink:
                shutil.copyfileobj(source, sink)
            if name.endswith(".sh"):
                try:
                    target.chmod(0o755)
                except OSError:  # pragma: no cover - 某些文件系统不支持
                    pass


def apply(staging: Path, dist: Path, prune: bool) -> tuple[list[Path], list[Path], list[Path]]:
    """把暂存内容落地到 Dist/，并清掉上一版（lib/ + bin/ 形态）的遗留文件。

    返回 (新增, 覆盖, 删除)。清理只针对桌面端自己的旧入口，且跳过本次刚写入的文件
    ——共享依赖与属于 CLI 的 `aha-cli-*.jar` / `bin/Aha.*` 不受影响。
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
    if prune:
        keep = {path.relative_to(dist).as_posix() for path in (*added, *updated)}
        for pattern in LEGACY_PRUNE:
            for old in sorted(dist.glob(pattern)):
                if old.relative_to(dist).as_posix() in keep:
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
            entries = [name for name in archive.namelist() if name in TRANSFER_ENTRIES]
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
    print(r"   运行（Windows）：Dist\AhaDesktop.bat")
    print("   运行（POSIX）  ：./Dist/AhaDesktop.sh")
    return 0


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="DesktopDistExtract.py",
        description="解压 AHA 桌面端便携包，并把单体 jar 与启动脚本平铺更新到 Dist/。",
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
                        help="不清理上一版（lib/ + bin/ 形态）的遗留文件")
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
