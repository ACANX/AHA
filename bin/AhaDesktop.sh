#!/usr/bin/env bash
#
# AHA 桌面端启动脚本（Linux / macOS）
#
# 依赖同级目录下的 lib/（JPMS 模块路径），由 Maven 发行包生成：
#     ./mvnw clean package       # 产物 aha-desktop-<版本>-<平台>.zip
#
# 包里已含**本平台**的 OpenJFX 原生库，因此只需机器上有 JDK 25。
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
LIB="$DIR/lib"

if [ ! -d "$LIB" ]; then
    echo "错误：未找到模块目录 $LIB" >&2
    echo "请先构建发行包：./mvnw clean package（产物为 aha-desktop-<版本>-<平台>.zip）。" >&2
    exit 1
fi

if [ -n "${JAVA_HOME:-}" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA="java"
fi

if ! command -v "$JAVA" >/dev/null 2>&1; then
    echo "错误：未找到 java。请安装 JDK 25 或设置 JAVA_HOME。" >&2
    exit 1
fi

exec "$JAVA" \
     --enable-native-access=org.xerial.sqlitejdbc \
     --module-path "$LIB" \
     --module com.acanx.module.aha.desktop/com.acanx.module.aha.desktop.AhaDesktopApp "$@"
