#!/usr/bin/env python3
"""按平台裁剪发行包里的专属脚本（issue #381）。

问题：`aha-desktop` / `aha-desktop-native` 的 zip 由同一份 assembly 描述符产出，
`CliReleasePackageLeg` 也共用同一份入口清单 —— 于是 ubuntu / macOS 包里也带上了
`AhaCli.bat` / `AhaDesktop.bat` / `apply-update.cmd` / `AhaApplyUpdate.ps1`。
这些文件在 POSIX 上用不到（`UpdateLauncher.installHelpers()` 非 Windows 只找
`apply-update.sh`），属于纯冗余内容。

本脚本在**生成包内清单之前**把 POSIX 包里的 Windows 专属脚本删掉：

    * POSIX（ubuntu / macos）：删除 `.bat` / `.cmd` / `.ps1`；
    * Windows：原样保留（继续带 `.bat` 与换装 helper）。

Windows 包里的 `.sh` **不**对称剔除：ACANX 只要求 POSIX 包干净，且 `.sh` 体积很小、
不影响 Windows 自更新路径（issue #381 待拍板第 4 点，默认不做）。

用法：

    python3 .github/Python/PrunePlatformScripts.py --zip <zip> --platform ubuntu-amd64

退出码：0 = 完成（含「无需裁剪」）；1 = 参数或 zip 异常。
"""

from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path

# Windows 专属扩展名（POSIX 包里一律剔除）
WINDOWS_SUFFIXES = (".bat", ".cmd", ".ps1")

# 已知平台标签前缀 → 是否为 Windows 包
WINDOWS_LABEL_PREFIX = "windows-"
POSIX_LABEL_PREFIXES = ("ubuntu-", "macos-")


def is_windows_platform(label: str) -> bool:
    """判断平台标签是否属于 Windows 包。"""
    normalized = (label or "").strip().lower()
    if normalized.startswith(WINDOWS_LABEL_PREFIX):
        return True
    if normalized.startswith(POSIX_LABEL_PREFIXES):
        return False
    raise ValueError(f"未知平台标签：{label!r}（应为 windows-* / ubuntu-* / macos-*）")


def prune(zip_path: Path, platform: str) -> list[str]:
    """按平台裁剪 zip，返回被删除的条目名。

    重写而不是「删除条目」：`zipfile` 不支持原地删除。用临时文件 + `replace`，
    其余条目保留原来的 ZipInfo（时间戳 / 权限 / 压缩方式都不变），
    避免清单里的 sha256 之外还多出一层「重新打包改了所有条目」的噪声。
    """
    if is_windows_platform(platform):
        return []
    removed: list[str] = []
    temporary = zip_path.with_name(zip_path.name + ".tmp")
    try:
        with zipfile.ZipFile(zip_path) as source:
            with zipfile.ZipFile(temporary, "w") as target:
                for info in source.infolist():
                    if any(info.filename.lower().endswith(suffix) for suffix in WINDOWS_SUFFIXES):
                        removed.append(info.filename)
                        continue
                    target.writestr(info, source.read(info), compress_type=info.compress_type)
        temporary.replace(zip_path)
    finally:
        if temporary.exists():
            temporary.unlink()
    return removed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="按平台裁剪发行包里的 Windows 专属脚本（issue #381）")
    parser.add_argument("--zip", required=True, type=Path, help="发行包 zip 路径")
    parser.add_argument("--platform", required=True, help="平台标签（ubuntu-amd64 / windows-amd64 / macos-aarch64）")
    parser.add_argument("--check-only", action="store_true",
                        help="只检查：POSIX 包里若仍有 Windows 专属脚本则退出 1（不改文件）")
    args = parser.parse_args(argv)

    if not args.zip.is_file():
        print(f"::error::找不到发行包：{args.zip}", file=sys.stderr)
        return 1
    try:
        windows = is_windows_platform(args.platform)
    except ValueError as exc:
        print(f"::error::{exc}", file=sys.stderr)
        return 1

    if args.check_only:
        with zipfile.ZipFile(args.zip) as archive:
            leftover = [name for name in archive.namelist()
                        if any(name.lower().endswith(suffix) for suffix in WINDOWS_SUFFIXES)]
        if windows:
            print(f"{args.zip.name}：Windows 包，保留 {len(leftover)} 个 Windows 专属脚本")
            return 0
        if leftover:
            print(f"::error::{args.zip.name} 是 POSIX 包，但仍有 Windows 专属脚本：{leftover}",
                  file=sys.stderr)
            return 1
        print(f"{args.zip.name}：POSIX 包，无 Windows 专属脚本")
        return 0

    removed = prune(args.zip, args.platform)
    if windows:
        print(f"{args.zip.name}：Windows 包，不裁剪")
    elif removed:
        print(f"{args.zip.name}：POSIX 包，已裁剪 {len(removed)} 个 Windows 专属脚本：{removed}")
    else:
        print(f"{args.zip.name}：POSIX 包，无需裁剪（本来就没有 Windows 专属脚本）")
    return 0


def _force_utf8_stdout() -> None:
    """Windows 上 Python 的 stdout 默认按控制台代码页（cp1252）编码，打印中文会抛
    UnicodeEncodeError（issue #395）。workflow 里已加 PYTHONUTF8=1，这里再兜一层，
    保证脚本单独在 Windows 上跑也不会崩。"""
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure is not None:
            reconfigure(encoding="utf-8", errors="replace")


if __name__ == "__main__":
    _force_utf8_stdout()
    sys.exit(main())
