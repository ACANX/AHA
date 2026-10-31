#!/usr/bin/env bash
#
# AHA CLI 启动脚本（Linux / macOS）
#
# 包内形态（issue #218 起）：**平铺**——单体 jar `aha-cli.jar` 与本脚本同在包根，
# 既没有 `lib/`，也没有单独的 `bin/`。jar 里已带全部依赖，因此只要机器上有 JDK 25。
#
# 源码检出处也能用：先在本目录、上一级与 `Dist/` 里找 `aha-cli.jar`；都没有时，
# 自动把 `Dist/aha-cli-jvm-<版本>-<平台>-jdk25.zip` 解压到以 zip 名与时间戳命名的目录后再启动
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

# 单体 jar 可能在：脚本同目录（发行包、或取件平铺到 Dist/ 后的布局）、上一级（历史 bin/ 布局）、
# 上一级或同级的 Dist/（源码检出处 `bin/` 与 `Dist/`）。
JARDIR=""
for candidate in "$DIR" "$DIR/.." "$DIR/../Dist" "$DIR/Dist"; do
    if [ -f "$candidate/aha-cli.jar" ]; then
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

# 兜底：源码检出且尚未构建、也没取过件时，就地把便携包解压一份再启动。
# 用 [0-9] 而不是 * 选包：这样不会把 aha-cli-native-<版本>-<jdk>.zip 误当成 JVM 便携包。
if [ -z "$JARDIR" ] && [ -n "$ROOT" ]; then
    ZIP=""
    # issue #269 新文法优先：aha-cli-jvm-<版本>-<平台>-jdk25.zip
    for z in "$ROOT"/Dist/aha-cli-jvm-*.zip "$ROOT"/dist/aha-cli-jvm-*.zip; do
        [ -f "$z" ] || continue
        ZIP="$z"
    done
    # 旧名兼容（[0-9] 跳过 aha-cli-native-<版本>-<jdk>.zip）
    if [ -z "$ZIP" ]; then
        for z in "$ROOT"/Dist/aha-cli-[0-9]*.zip "$ROOT"/dist/aha-cli-[0-9]*.zip; do
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

        if [ ! -f "$BUNDLE/aha-cli.jar" ]; then
            echo "正在解压 CLI 便携包：$ZIP"
            rm -rf "$BUNDLE"
            mkdir -p "$BUNDLE"
            ( cd "$BUNDLE" && "$JAR" --extract --file "$ZIP" )
        fi
        if [ ! -f "$BUNDLE/aha-cli.jar" ]; then
            echo "错误：$ZIP 里没有 aha-cli.jar。" >&2
            echo "      这多半是 issue #218 之前的旧形态包（包内是 bin/ + lib/，没有单体 jar）。" >&2
            echo "      请改用 ./mvnw clean package 重新构建，或取 0.1.3.00221 及以后的便携包。" >&2
            exit 1
        fi
        JARDIR="$BUNDLE"
    fi
fi

if [ -z "$JARDIR" ]; then
    echo "错误：未找到 aha-cli.jar（在本目录、上一级与 Dist/ 都找过）" >&2
    echo "请先构建发行包：./mvnw -pl aha-cli -am package -DskipTests（产物位于 Dist/）。" >&2
    echo "若从源码运行，可直接使用：./mvnw -pl aha-cli exec:java" >&2
    exit 1
fi

if ! command -v "$JAVA" >/dev/null 2>&1; then
    echo "错误：未找到 java。请安装 JDK 25 或设置 JAVA_HOME。" >&2
    exit 1
fi

# 单体 jar 以 **classpath** 启动（不是模块路径），因此两个开关与模块路径时代不同：
#   * `--enable-native-access` 只能写 `ALL-UNNAMED`，写具名模块（如 sqlite-jdbc / jline）
#     会因「找不到该模块」启动失败；
#   * `--add-opens` 不需要：未命名模块本来就不受限。
exec "$JAVA" --enable-native-access=ALL-UNNAMED -jar "$JARDIR/aha-cli.jar" "$@"
