#!/usr/bin/env python3
r"""AHA 桌面端原生镜像一键更新（用户入口 · 薄壳）。

实现全部在共享引擎 `NativeVersionUpdate.py` 里——两种原生包（桌面端 / CLI）
共用同一套取件逻辑，差别只在引擎里的 `KINDS` 表（见 TD-00015）；本文件只做一件事：
选定「桌面端」这一种类，**保持历史命令名与用法不变**。

用法（Windows 为例）：

    python3 Script\Python\DesktopNativeVersionUpdate.py            # 装最新版
    python3 Script\Python\DesktopNativeVersionUpdate.py --list     # 看看有哪些版本
    python3 Script\Python\DesktopNativeVersionUpdate.py --dry-run  # 只预览链接

（POSIX 下把反斜杠换成 `/`；本机命令若是 `python` 而非 `python3`，换成 `python` 即可。）

Windows 一键入口（双击）：`Dist\UpdateDesktopNative.cmd`——内部就是调用本文件，
额外参数原样透传，所以 `UpdateDesktopNative.cmd --list` 同样可用。

装到哪：默认仓库根的 `Dist/aha-desktop-native.exe`（旧版留一份 `.bak`）。
退出码：0 = 已是最新或更新成功；1 = 失败；130 = 用户中断。
"""

from __future__ import annotations

import sys
from pathlib import Path

# 引擎与本文件同目录；直接跑脚本时 Python 已把它加入 sys.path，
# 但被 `python -c` / 其它脚本按路径导入时要靠自己（Script/Python 下无同级导入先例，
# 这里显式加一次，两种调用方式都能工作）。
sys.path.insert(0, str(Path(__file__).resolve().parent))

import NativeVersionUpdate as engine  # noqa: E402  （必须在 sys.path 调整之后）


def main(argv: list[str] | None = None) -> int:
    """按「桌面端原生包」跑一次更新。"""
    engine.configure("desktop")
    return engine.main(argv)


if __name__ == "__main__":
    sys.exit(main())
