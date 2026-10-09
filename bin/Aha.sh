#!/usr/bin/env bash
#
# AHA CLI 启动脚本（Linux / macOS）
#
# 依赖同级目录下的 lib/（JPMS 模块路径），由 Maven 发行包生成：
#     ./mvnw clean package       # 产物位于项目根 Dist/
#
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)"
LIB="$DIR/lib"

if [ ! -d "$LIB" ]; then
    echo "错误：未找到模块目录 $LIB" >&2
    echo "请先构建发行包：./mvnw clean package（产物位于 Dist/）。" >&2
    echo "若从源码运行，可直接使用：./mvnw -pl aha-cli exec:java" >&2
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
     --enable-native-access=org.xerial.sqlitejdbc,org.jline \
     --module-path "$LIB" \
     --module com.acanx.module.aha.cli/com.acanx.module.aha.cli.AhaCli "$@"
