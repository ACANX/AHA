#!/usr/bin/env bash
#
# AHA 桌面端启动脚本（Linux / macOS）
#
# 包内形态（issue #218 起）：**平铺**——单体 jar `aha-desktop.jar` 与本脚本同在包根，
# 既没有 `lib/`，也没有单独的 `bin/`。jar 里已带**本平台**的 OpenJFX（类与原生库都在里面，
# 运行期由 JavaFX 自己从 jar 取出并缓存），因此只要机器上有 JDK 25。
#
# 源码检出处也能用：包根找不到 `aha-desktop.jar` 时，自动把
# `Dist/aha-desktop-<版本>-<平台>.zip` 解压到以 zip 名与时间戳命名的目录后再启动
# （旧目录不删，可能正被已运行的实例占用）。
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"

if [ -n "${JAVA_HOME:-}" ]; then
    JAVA="$JAVA_HOME/bin/java"
    JAR="$JAVA_HOME/bin/jar"
else
    JAVA="java"
    JAR="jar"
fi

# 单体 jar 可能在脚本同目录（发行包、或已解压到 Dist/ 的布局），也可能在上一级（历史 bin/ 布局）。
JARDIR=""
for candidate in "$DIR" "$DIR/.."; do
    if [ -f "$candidate/aha-desktop.jar" ]; then
        JARDIR="$(cd "$candidate" && pwd)"
        break
    fi
done

# 源码检出的仓库根（包里没有 pom.xml，因此这段在发行包中不会命中）。
ROOT=""
for candidate in "$DIR" "$DIR/.."; do
    if [ -f "$candidate/pom.xml" ]; then
        ROOT="$(cd "$candidate" && pwd)"
        break
    fi
done

# 兜底：源码检出且尚未把便携包解压到 Dist/ 时，就地解压一份再启动
# （旧目录不删：可能正被已运行的实例占用）。
if [ -z "$JARDIR" ] && [ -n "$ROOT" ]; then
    # 本平台分类器（见 DesktopDesign.md 第 5 节），用于优先挑本平台的包。
    classifier=""
    case "$(uname -s)" in
        Linux)  classifier="linux" ;;
        Darwin) classifier="mac" ;;
    esac
    case "$(uname -m)" in
        aarch64 | arm64) classifier="$classifier-aarch64" ;;
    esac

    ZIP=""
    if [ -n "$classifier" ]; then
        for z in "$ROOT"/Dist/aha-desktop-*-"$classifier".zip "$ROOT"/dist/aha-desktop-*-"$classifier".zip; do
            [ -f "$z" ] || continue
            ZIP="$z"
        done
    fi
    if [ -z "$ZIP" ]; then
        for z in "$ROOT"/Dist/aha-desktop-[0-9]*.zip "$ROOT"/dist/aha-desktop-[0-9]*.zip; do
            [ -f "$z" ] || continue
            ZIP="$z"
        done
    fi

    if [ -n "$ZIP" ]; then
        if stat -c %Y "$ZIP" >/dev/null 2>&1; then
            stamp="$(stat -c %Y "$ZIP")"
        else
            stamp="$(stat -f %m "$ZIP")"
        fi
        BUNDLE="${ZIP%.zip}.${stamp}"

        if [ ! -f "$BUNDLE/aha-desktop.jar" ]; then
            echo "正在解压桌面端便携包：$ZIP"
            rm -rf "$BUNDLE"
            mkdir -p "$BUNDLE"
            ( cd "$BUNDLE" && "$JAR" --extract --file "$ZIP" )
        fi
        if [ ! -f "$BUNDLE/aha-desktop.jar" ]; then
            echo "错误：$ZIP 里没有 aha-desktop.jar。" >&2
            echo "      这多半是 issue #218 之前的旧形态包（包内是 bin/ + lib/，versioned jar）。" >&2
            echo "      请改用 ./mvnw clean package 重新构建，或取 0.1.3.00219 及以后的便携包。" >&2
            exit 1
        fi
        JARDIR="$BUNDLE"
    fi
fi

if [ -z "$JARDIR" ]; then
    echo "错误：未找到 aha-desktop.jar（在 $DIR 与其上一级都找过）" >&2
    echo "请先构建便携包：./mvnw -pl aha-desktop -am package -DskipTests" >&2
    exit 1
fi

if ! command -v "$JAVA" >/dev/null 2>&1; then
    echo "错误：未找到 java。请安装 JDK 25 或设置 JAVA_HOME。" >&2
    exit 1
fi

# 单体 jar 以 **classpath** 启动（不是模块路径），因此两个开关与模块路径时代不同：
#   * 反射进 `com.sun.javafx.font`（issue #48 的渲染诊断探针）在未命名模块里本来就不受限，
#     不需要 `--add-opens`；写 `javafx.graphics` 反而会因「找不到该模块」启动失败；
#   * `--enable-native-access` 只能写 `ALL-UNNAMED`，写具名模块（如 sqlite-jdbc）同样会失败。
exec "$JAVA" --enable-native-access=ALL-UNNAMED -jar "$JARDIR/aha-desktop.jar" "$@"
