#!/usr/bin/env bash
# 取下最新的桌面端 JVM 便携包（dev 线预发行）
# 对标仓库 Dist\UpdateDesktopJvm.cmd —— 逻辑在 AhaUpdate.sh，本文件只固定「通道 + 形态」。
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/AhaUpdate.sh" dev desktop-jvm "$@"
