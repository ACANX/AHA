#!/usr/bin/env bash
#
# Agent 执行边界（Agent Execution Boundary）
#
# 设计见 Docs/Design/AgentExecutionBoundaryDesign.md。
#
# 目标：让「Agent 不在本地跑 Maven 编译 / 测试」从文字约定变成**执行域能力约束**，
#       且不依赖任何 Agent 工具的插件、不改写工作区文件。
#
# 做法（能力剥离，而非规则禁止）：
#   用 Linux 命名空间（bubblewrap）在启动时构造一个受限执行域，域内：
#     1. 不挂载 /mnt（Windows 盘不可见，同时落实 AGENTS.md 第二节的环境边界）；
#     2. 不暴露宿主 JDK 目录；
#     3. JAVA_HOME 与 PATH 前置到一组拒绝桩。
#   于是 ./mvnw、sh ./mvnw、mvn、java、javac 都会命中拒绝桩并给出去 CI 的指引。
#
# 用法：
#   AgentSandbox.sh [--repo <目录>] [--allow-mnt] [--allow-jdk] -- <命令...>
#
# 例：
#   ./bin/AgentSandbox.sh -- pi
#   ./bin/AgentSandbox.sh -- claude
#   ./bin/AgentSandbox.sh --repo "$PWD" -- codex

set -euo pipefail

repo="${AGENT_SANDBOX_REPO:-$PWD}"
allow_mnt=0
allow_jdk=0

usage() {
  cat <<'USAGE'
用法: AgentSandbox.sh [--repo <目录>] [--allow-mnt] [--allow-jdk] -- <命令...>

  --repo <目录>   指定工作仓库（预留；默认 $PWD 或 $AGENT_SANDBOX_REPO）
  --allow-mnt     允许看到 /mnt（默认隐藏，用于落实环境边界）
  --allow-jdk     允许使用宿主 JDK（仅排障；会破坏「本地无法构建」的保证）
  -h, --help      显示本帮助

退出码: 0 正常；64 用法错误；69 缺少 bwrap；97 命中拒绝桩。
USAGE
}

while [ $# -gt 0 ]; do
  case "$1" in
    --repo)
      repo="${2:-}"
      shift 2
      ;;
    --allow-mnt)
      allow_mnt=1
      shift
      ;;
    --allow-jdk)
      allow_jdk=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    *)
      break
      ;;
  esac
done

if [ $# -eq 0 ]; then
  usage >&2
  exit 64
fi

if ! command -v bwrap >/dev/null 2>&1; then
  echo "[agent-sandbox] 未找到 bwrap（bubblewrap），无法建立执行边界。" >&2
  echo "                 请改用容器化方案（见 Docs/Design/AgentExecutionBoundaryDesign.md 第 4.5 节）。" >&2
  exit 69
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# 拒绝桩：命中即给出去 CI 的指引并以固定退出码结束
cat > "$work/stub" <<'STUB'
#!/bin/sh
echo "[agent-sandbox] 已拦截：Agent 执行域不提供本地 Maven / JDK 构建工具链。" >&2
echo "                 请推送分支后看 PR 的 CI checks（Build.yml / Gate.yml），失败再按 CI 日志排查。" >&2
exit 97
STUB
chmod +x "$work/stub"

mkdir -p "$work/bin" "$work/jdk/bin"
# 构建工具始终纳入拒绍桩；JDK 类仅在未放宽时纳入（--allow-jdk 用于排障）
stub_names=(mvn mvnw mvnw.cmd gradle)
if [ "$allow_jdk" -eq 0 ]; then
  stub_names+=(java javac jar)
fi
for name in "${stub_names[@]}"; do
  cp "$work/stub" "$work/bin/$name"
done
cp "$work/stub" "$work/jdk/bin/java"

bwrap_args=(--dev-bind / /)

# 1) Windows 盘（/mnt）默认不可见
if [ "$allow_mnt" -eq 0 ]; then
  bwrap_args+=(--tmpfs /mnt)
fi

# 2) 隐藏宿主 JDK 目录（常见安装位置）
if [ "$allow_jdk" -eq 0 ]; then
  for jdk in /usr/lib/jvm /usr/local/java /opt/java /opt/jdk; do
    if [ -d "$jdk" ]; then
      bwrap_args+=(--tmpfs "$jdk")
    fi
  done
  bwrap_args+=(--setenv JAVA_HOME "$work/jdk")
fi

# 3) PATH 前置拒绝桩
bwrap_args+=(--setenv PATH "$work/bin:${PATH}")

exec bwrap "${bwrap_args[@]}" -- "$@"
