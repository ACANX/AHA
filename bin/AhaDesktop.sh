#!/usr/bin/env bash
#
# AHA 桌面端启动脚本（Linux / macOS）
#
# 支持两种布局：
#   1) 发行包：<包根>/lib 内含 aha-desktop 模块，脚本位于 <包根>/bin/；
#   2) 源码检出：仓库根没有 lib/ 时，自动把 Dist/aha-desktop-<版本>-<平台>.zip
#      解压到以 zip 名与时间戳命名的目录后再启动；重建产生的包会落到新目录，
#      不会被旧解压结果遮蔽（旧目录不删，可能正被已运行的实例占用）。
#
# 包内已含**本平台**的 OpenJFX 原生库，因此只需机器上有 JDK 25。
set -euo pipefail

BASE="$(cd "$(dirname "$0")/.." && pwd)"
LIB="$BASE/lib"

if [ -n "${JAVA_HOME:-}" ]; then
    JAVA="$JAVA_HOME/bin/java"
    JAR="$JAVA_HOME/bin/jar"
else
    JAVA="java"
    JAR="jar"
fi

# 当前平台对应的 JavaFX 分类器（见 DesktopDesign.md 第 5 节）。
os=""
case "$(uname -s)" in
    Linux)  os=linux ;;
    Darwin) os=mac ;;
esac
arch=""
case "$(uname -m)" in
    aarch64 | arm64) arch=-aarch64 ;;
esac
classifier=""
if [ -n "$os" ]; then
    classifier="$os$arch"
fi

# -- 1) 发行包布局 -------------------------------------------------------
if ! compgen -G "$LIB/aha-desktop-*.jar" >/dev/null; then
    if [ ! -f "$BASE/pom.xml" ]; then
        echo "错误：未找到桌面端模块目录 $LIB" >&2
        echo "请先构建便携包：./mvnw -pl aha-desktop -am package -DskipTests" >&2
        exit 1
    fi

    # 优先本平台包；命中不到再退回任意桌面端包（[0-9] 排除 native 包）。
    ZIP=""
    if [ -n "$classifier" ]; then
        for z in "$BASE"/Dist/aha-desktop-*-"$classifier".zip "$BASE"/dist/aha-desktop-*-"$classifier".zip; do
            [ -f "$z" ] || continue
            ZIP="$z"
        done
    fi
    if [ -z "$ZIP" ]; then
        for z in "$BASE"/Dist/aha-desktop-[0-9]*.zip "$BASE"/dist/aha-desktop-[0-9]*.zip; do
            [ -f "$z" ] || continue
            ZIP="$z"
        done
    fi
    if [ -z "$ZIP" ]; then
        echo "错误：未找到桌面端模块目录 $LIB" >&2
        echo "也未在 $BASE/Dist 下找到 aha-desktop-*.zip 便携包。" >&2
        echo "请先构建便携包：./mvnw -pl aha-desktop -am package -DskipTests" >&2
        exit 1
    fi

    if stat -c %Y "$ZIP" >/dev/null 2>&1; then
        stamp="$(stat -c %Y "$ZIP")"
    else
        stamp="$(stat -f %m "$ZIP")"
    fi
    BUNDLE="${ZIP%.zip}.${stamp}"

    if ! compgen -G "$BUNDLE/lib/aha-desktop-*.jar" >/dev/null; then
        echo "正在解压桌面端便携包：$ZIP"
        rm -rf "$BUNDLE"
        mkdir -p "$BUNDLE"
        ( cd "$BUNDLE" && "$JAR" --extract --file "$ZIP" )
    fi
    LIB="$BUNDLE/lib"
fi

if ! command -v "$JAVA" >/dev/null 2>&1; then
    echo "错误：未找到 java。请安装 JDK 25 或设置 JAVA_HOME。" >&2
    exit 1
fi

# --add-opens 是渲染诊断（issue #48）用的：字体实现工厂探针要反射读
# com.sun.javafx.font.PrismFontFactory。目标必须写应用的具名模块——以
# --module 启动时应用是具名模块，写 ALL-UNNAMED 对它无效。
exec "$JAVA" \
     --enable-native-access=org.xerial.sqlitejdbc \
     --add-opens javafx.graphics/com.sun.javafx.font=com.acanx.module.aha.desktop \
     --module-path "$LIB" \
     --module com.acanx.module.aha.desktop/com.acanx.module.aha.desktop.AhaDesktopApp "$@"
