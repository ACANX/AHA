#!/usr/bin/env bash
#
# GraalVM tracing agent 采集编排（把「跑一次」变成可重复的标准步骤）
#
# 用法：
#   collect-metadata.sh run <out-dir> init  <java> [应用参数...]   # 首次采集
#   collect-metadata.sh run <out-dir> merge <java> [应用参数...]   # 后续合并
#   collect-metadata.sh summarize <metadata.json>                  # 按包前缀分组统计
#   collect-metadata.sh filter <metadata.json> [排除前缀...]        # 过滤依赖自带/JDK，输出到 stdout
#
# 例：
#   OUT=/tmp/reachability
#   J=$JAVA_HOME/bin/java
#   collect-metadata.sh run "$OUT" init  "$J" --version
#   collect-metadata.sh run "$OUT" merge "$J" --help
#   collect-metadata.sh run "$OUT" merge "$J" config show
#   collect-metadata.sh summarize "$OUT/reachability-metadata.json"
#   collect-metadata.sh filter "$OUT/reachability-metadata.json" \
#       org.apache.logging.log4j org.sqlite org.jline java. javax. sun. \
#       com.sun.crypto com.sun.security log4j2 > "$OUT/filtered.json"
#
# 说明：
#   * agent 随 GraalVM 发布（$JAVA_HOME/lib/libnative-image-agent.so），普通 HotSpot JDK 没有；
#   * agent 在 JVM 退出时落盘：GUI / 交互式程序需能自动退出（自检模式或 timeout -s TERM）；
#   * 多平台：每个平台各跑一遍，再 merge 到同一 out-dir（缺席平台只产生 warning）。
set -euo pipefail

usage() {
    sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'
}

# 前缀匹配：带点的按 startswith；不带点的按「等于或后跟点」。
_filter_metadata() {
    python3 - "$@" <<'PY'
import json, sys

path, excludes = sys.argv[1], sys.argv[2:]

def hit(name):
    for p in excludes:
        if p.endswith('.'):
            if name.startswith(p):
                return True
        elif name == p or name.startswith(p + '.'):
            return True
    return False

def keep_entry(e):
    if not isinstance(e, dict):
        return True
    if 'type' in e and hit(e['type']):
        return False
    if 'module' in e and hit(e['module']):
        return False
    if 'glob' in e and hit(e['glob']):
        return False
    return True

with open(path, encoding='utf-8') as f:
    data = json.load(f)

out = {}
removed = 0
for section, value in data.items():
    if isinstance(value, list):
        kept = [e for e in value if keep_entry(e)]
        removed += len(value) - len(kept)
        out[section] = kept
    else:
        out[section] = value

json.dump(out, sys.stdout, ensure_ascii=False, indent=2)
sys.stdout.write('\n')
print(f"# 剔除 {removed} 条（排除前缀：{' '.join(excludes) or '<无>'}）", file=sys.stderr)
PY
}

_summarize_metadata() {
    python3 - "$1" <<'PY'
import collections, json, sys

with open(sys.argv[1], encoding='utf-8') as f:
    data = json.load(f)

refl = [e for e in data.get('reflection', []) if isinstance(e, dict) and 'type' in e]

def bucket(t):
    parts = t.split('.')
    return '.'.join(parts[:2]) if len(parts) >= 2 else t

print(f"reflection 条目: {len(refl)}   唯一类型: {len({e['type'] for e in refl})}")
print(f"resources: {len(data.get('resources', []))}   jni: {len(data.get('jni', []))}   "
      f"proxy: {len(data.get('proxy', []))}   serialization: {len(data.get('serialization', []))}")
print("按包前缀分组（前 40）：")
for name, count in collections.Counter(bucket(e['type']) for e in refl).most_common(40):
    print(f"  {count:5d}  {name}")
PY
}

cmd="${1:-}"
shift || true

case "$cmd" in
    run)
        out="${1:?需要 out-dir}"; mode="${2:?需要 init 或 merge}"; java="${3:?需要 java 可执行文件}"; shift 3
        case "$mode" in
            init)  opt="config-output-dir=$out" ;;
            merge) opt="config-merge-dir=$out" ;;
            *) echo "mode 必须是 init 或 merge（收到：$mode）" >&2; exit 2 ;;
        esac
        mkdir -p "$out"
        exec "$java" -agentlib:native-image-agent="$opt" "$@"
        ;;
    summarize)
        _summarize_metadata "${1:?需要 metadata.json}"
        ;;
    filter)
        json="${1:?需要 metadata.json}"; shift || true
        _filter_metadata "$json" "$@"
        ;;
    ""|-h|--help|help)
        usage
        ;;
    *)
        echo "未知子命令：$cmd" >&2
        usage >&2
        exit 2
        ;;
esac
