#!/usr/bin/env bash
#
# Agent 执行边界（Agent Execution Boundary）—— 启动器
#
# 设计见 Docs/Design/AgentExecutionBoundaryDesign.md（§6 详细设计）。
# 自证见 bin/CheckAgentBoundary.py，CI 卡点在 Gate.yml 的「Agent 执行边界自证」一步。
#
# 目标：让「Agent 不在本地跑 Maven 编译 / 测试」从文字约定变成**执行域能力约束**，
#       且不依赖任何 Agent 工具的插件、不改写工作区文件。
#
# 做法（能力剥离，而非规则禁止）：
#   用 Linux 命名空间（bubblewrap）在启动时构造一个受限执行域，域内：
#     1. 不挂载 /mnt（Windows 盘不可见，同时落实 AGENTS.md 第二节的环境边界）；
#     2. 不暴露宿主 JDK 目录（静态常见位置 + 从 `java` 真实路径反推，逐条 tmpfs 盖掉）；
#     3. JAVA_HOME 与 PATH 前置到一组拒绝桩。
#   于是 ./mvnw、sh ./mvnw、mvn、java、javac 都会命中拒绝桩并给出去 CI 的指引。
#
# 为什么不用「把 mvnw 换成桩」：那会改写工作区，git 会把 mvnw / mvnw.cmd 判为已修改
# （inode 与内容都变了），存在被误提交的风险；`sh ./mvnw` 也能绕过。见设计文档 F1 / F2。
#
# 用法：
#   AgentSandbox.sh [--repo <目录>] [--allow-mnt] [--allow-jdk] -- <命令...>
#   AgentSandbox.sh [--repo <目录>] --print-plan
#
# 例：
#   ./bin/AgentSandbox.sh -- pi
#   ./bin/AgentSandbox.sh -- claude
#   ./bin/AgentSandbox.sh --repo "$PWD" -- codex
#   ./bin/AgentSandbox.sh --print-plan          # 打印实际生效的 bwrap 参数（取证用）

set -euo pipefail

PROG="agent-sandbox"

#: 命中拒绝桩的退出码（约定值，自证脚本与上层工具都按它识别「被边界拦截」）
readonly STUB_RC=97

repo="${AGENT_SANDBOX_REPO:-$PWD}"
allow_mnt=0
allow_jdk=0
print_plan=0

usage() {
  cat <<'USAGE'
用法: AgentSandbox.sh [--repo <目录>] [--allow-mnt] [--allow-jdk] -- <命令...>
      AgentSandbox.sh [--repo <目录>] --print-plan

  --repo <目录>   在指定目录建立执行域（默认 $PWD，可用 AGENT_SANDBOX_REPO 指定）；
                  该目录不在域内时立刻报用法错误，且不得位于 /mnt（Windows 盘）
  --allow-mnt     允许看到 /mnt（默认隐藏，用于落实 AGENTS.md 第二节的环境边界）
  --allow-jdk     允许使用宿主 JDK（仅人排障；会破坏「本地无法构建」的保证）
  --print-plan    只打印将要执行的 bwrap 参数与命令，不执行（取证 / 自证用）
  -h, --help      显示本帮助

退出码: 0 正常；64 用法错误（含仓库位于 /mnt）；69 缺少 bwrap；
        70 命名空间不可用；97 命中拒绝桩。
USAGE
}

while [ $# -gt 0 ]; do
  case "$1" in
    --repo)
      if [ $# -lt 2 ]; then
        echo "[$PROG] --repo 需要一个目录参数。" >&2
        exit 64
      fi
      repo="$2"
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
    --print-plan)
      print_plan=1
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

if [ "$print_plan" -eq 0 ] && [ $# -eq 0 ]; then
  usage >&2
  exit 64
fi

if [ ! -d "$repo" ]; then
  echo "[$PROG] --repo 指向的目录不存在：$repo" >&2
  exit 64
fi
repo="$(cd "$repo" && pwd -P)"

# 环境边界（AGENTS.md 第二节）：Agent 的工作范围限于 WSL 文件系统，
# 因此仓库落在 /mnt 时直接拒绝——先于任何挂载参数判定，避免「藏了 /mnt 又说要在里面干活」。
case "$repo" in
  /mnt|/mnt/*)
    if [ "$allow_mnt" -eq 0 ]; then
      echo "[$PROG] 拒绝在该目录建立执行域：$repo" >&2
      echo "                 Agent 工作范围限于 WSL 文件系统（AGENTS.md 第二节），仓库不得位于 Windows 盘。" >&2
      echo "                 仅人工排障时才用 --allow-mnt 显式放宽。" >&2
      exit 64
    fi
    ;;
esac

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# ── 拒绝桩 ──────────────────────────────────────────────────────────────
# 输出契约见设计文档 §6.3：stderr、固定退出码 97、只给指引不做判断。
cat > "$work/stub" <<STUB
#!/bin/sh
echo "[$PROG] 已拦截：Agent 执行域不提供本地 Maven / JDK 构建工具链。" >&2
echo "                 请推送分支后看 PR 的 CI checks（Build.yml / Gate.yml），失败再按 CI 日志排查。" >&2
exit $STUB_RC
STUB
chmod 755 "$work/stub"

mkdir -p "$work/bin" "$work/jdk/bin"

# 构建工具：任何档位都拦截（--allow-jdk 只是放开 JDK，不放开构建入口）。
build_tools=(mvn mvnw mvnw.cmd gradle gradlew gradlew.bat)
# JDK 工具：默认也拦截；--allow-jdk 时不建桩（供人排障）。
jdk_tools=(java javac jar jshell javadoc jlink jpackage)
for name in "${build_tools[@]}"; do
  ln -f "$work/stub" "$work/bin/$name"
done
if [ "$allow_jdk" -eq 0 ]; then
  for name in "${jdk_tools[@]}"; do
    ln -f "$work/stub" "$work/bin/$name"
  done
  ln -f "$work/stub" "$work/jdk/bin/java"
fi

# ── 要 tmpfs 盖掉的 JDK 根目录 ───────────────────────────────────────────
# 只盖「真的装着 bin/java 的目录」，并且不盖过浅的目录（/usr、$HOME 之类）——
# 盖错一层会把整个系统藏掉，比不盖危险得多。
jdk_roots=()

is_too_shallow() {
  case "$1" in
    /|/bin|/usr|/usr/bin|/usr/lib|/usr/local|/opt|/opt/java|/home|/tmp|"$HOME") return 0 ;;
  esac
  return 1
}

add_jdk_root() {
  local candidate="$1" existing
  [ -n "$candidate" ] || return 0
  [ -d "$candidate" ] || return 0
  # 必须真的是 JDK 根（有 bin/java 或 bin/java.exe），否则可能盖掉无关目录
  [ -x "$candidate/bin/java" ] || [ -x "$candidate/bin/java.exe" ] || return 0
  is_too_shallow "$candidate" && return 0
  for existing in "${jdk_roots[@]}"; do
    [ "$existing" = "$candidate" ] && return 0
  done
  jdk_roots+=("$candidate")
}

# 1) 常见安装位置（含各版本管理器的候选目录）
for dir in \
  /usr/lib/jvm /usr/lib64/jvm /usr/local/java /usr/local/jdk /usr/local/lib/jvm \
  /opt/java /opt/jdk \
  "$HOME/.jdks" "$HOME/.sdkman/candidates/java" "$HOME/.asdf/installs/java"; do
  add_jdk_root "$dir"
done

# 2) 动态反推：从 `java` 的真实路径回溯到发行版根
#    例：/usr/local/java/25/bin/java → /usr/local/java/25（静态清单里没有这一层的环境也能挡住）
if javabin="$(command -v java 2>/dev/null)"; then
  real="$(readlink -f -- "$javabin" 2>/dev/null || printf '%s' "$javabin")"
  case "$real" in
    */bin/java) add_jdk_root "${real%/bin/java}" ;;
    */bin/java.exe) add_jdk_root "${real%/bin/java.exe}" ;;
  esac
fi

# 3) 显式声明的 JAVA_HOME
if [ -n "${JAVA_HOME:-}" ]; then
  add_jdk_root "$JAVA_HOME"
fi

# ── 组装执行域 ──────────────────────────────────────────────────────────
bwrap_args=(--dev-bind / / --chdir "$repo")

# 1) Windows 盘（/mnt）默认不可见
if [ "$allow_mnt" -eq 0 ]; then
  bwrap_args+=(--tmpfs /mnt)
fi

# 2) 隐藏宿主 JDK（逐个盖成空目录），并把 JAVA_HOME 指向只有 java 桩的假 JDK，
#    让 mvnw 这类「先找 JAVA_HOME、再退 PATH」的入口也落在桩上。
if [ "$allow_jdk" -eq 0 ]; then
  for dir in "${jdk_roots[@]}"; do
    bwrap_args+=(--tmpfs "$dir")
  done
  bwrap_args+=(--setenv JAVA_HOME "$work/jdk")
fi

# 3) PATH 前置拒绝桩
bwrap_args+=(--setenv PATH "$work/bin:${PATH}")

if [ "$print_plan" -eq 1 ]; then
  echo "# 执行域参数（bwrap）"
  printf '%q ' bwrap "${bwrap_args[@]}" -- "$@"
  printf '\n'
  if [ "${#jdk_roots[@]}" -gt 0 ]; then
    echo "# 被隐藏的 JDK 根目录"
    printf '  - %s\n' "${jdk_roots[@]}"
  else
    echo "# 未发现可隐藏的 JDK 根目录（宿主机没有 JDK，或装在非常规位置）"
  fi
  exit 0
fi

if ! command -v bwrap >/dev/null 2>&1; then
  echo "[$PROG] 未找到 bwrap（bubblewrap），无法建立执行边界。" >&2
  echo "                 请改用容器化方案（见 Docs/Design/AgentExecutionBoundaryDesign.md 第 4.5 节）。" >&2
  exit 69
fi

# 内核能力探测（设计文档 F5）：不静默降级——不可用就明确报错。
if ! bwrap --dev-bind / / true >/dev/null 2>&1; then
  echo "[$PROG] 无法建立命名空间：bwrap 已安装，但内核不允许非特权 user namespace。" >&2
  echo "                 可用 --print-plan 取出生效参数；长期方案见设计文档 §4.5（容器化）。" >&2
  echo "                 自查：sysctl kernel.unprivileged_userns_clone（或" >&2
  echo "                 kernel.apparmor_restrict_unprivileged_userns）是否放开了 userns。" >&2
  exit 70
fi

exec bwrap "${bwrap_args[@]}" -- "$@"
