#!/usr/bin/env bash
# 取下最新的桌面端原生快照包（PR 试包）
# 对标仓库 Dist\UpdateDesktopNativeSnapshot.cmd —— 逻辑在 AhaUpdate.sh，本文件只固定「通道 + 形态」。
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/AhaUpdate.sh" snapshot desktop-native "$@"
