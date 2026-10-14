#!/usr/bin/env python3
"""快照出包的「该不该出、出什么、叫什么版本」——全部判定逻辑（issue #165 §三/§四/§六/§八/§九）。

把逻辑放在仓库脚本里而不是工作流 YAML 里，有三个实在的理由：

1. YAML 块标量里嵌 Python 会被坑两次（代码顶格被当成新键；块标量 + 管道 + 重定向会吃掉
   行尾空格），而且**本地跑不了**、只能靠调度到 CI 才发现写错 —— 本轮就因此翻过车；
2. 判定规则（标签矩阵、变更分类 A/B/C、版本号、资产名）是本方案最容易写错的部分，
   必须能本地跑正 / 反例（`--self-test`）；
3. 同样的规则还要被 `Dist/UpdateSnapshot.ps1`（取件）与文档引用，集中一处才谈得上「单一来源」。

用法（输出 JSON，供工作流用 `--json` 消费）：

    python3 .github/Python/SnapshotPlan.py --labels 'SNAPSHOT:Desktop,OS:Win' \\
        --changed-files /tmp/files.txt --baseline 0.1.3 --pr 171
    python3 .github/Python/SnapshotPlan.py --self-test

退出码：0 = 通过；1 = 参数非法（如 `/snapshot` 命令写错 —— 此时**不构建**，只回复用法）；
2 = 用法错误。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

#: 快照有效期（天）。与 #165 §十「7 天有效期」一致；到期只影响提示与前缀清理，不阻止下载。
SNAPSHOT_TTL_DAYS = 7

#: 触发标签 → 出包目标（#165 §三 标签矩阵）。
#: 值是「目标名」：JVM 线出 JVM 便携包（Desktop / Cli），原生线出原生包（Desktop-Native / Cli-Native ——
#: 与资产前缀 `AHA-Desktop-Native-…` **同序**；触发标签名沿用既有的 `SNAPSHOT:NativeDesktop`，
#: 改标签名要动仓库上的标签本体，与 tag 命名是两件事）。
SNAPSHOT_LABELS: dict[str, str] = {
    "SNAPSHOT:Desktop": "Desktop",
    "SNAPSHOT:Cli": "Cli",
    "SNAPSHOT:NativeDesktop": "Desktop-Native",
    "SNAPSHOT:NativeCli": "Cli-Native",
}

#: `/snapshot` 命令里的平台词 → 平台限定标签（两条线共用一套词，各自映射成自己的腿标签）。
OS_WORDS: dict[str, str] = {"win": "OS:Win", "macos": "OS:MacOS", "ubuntu": "OS:Ubuntu"}

#: 平台限定标签 → 平台键（#165 §三）。键与 JVM 线的腿标签一致：Win→windows-x64，Ubuntu→ubuntu-x64。
OS_LABELS: dict[str, str] = {
    "OS:Win": "windows-x64",
    "OS:MacOS": "macos-arm64",
    "OS:Ubuntu": "ubuntu-x64",
}

#: PR 注释命令 `/snapshot <target> [os…]`（大小写不敏感，见 #165 §三「补充触发方式 ②」）。
COMMAND_RE = re.compile(r"^\s*/snapshot\s+(\S+)((?:\s+\S+)*)\s*$", re.IGNORECASE)

#: 本条工作流覆盖的目标（原生线由 DesktopNative.yml / CliNative.yml 负责，见 S4）。
JVM_TARGETS = ("Desktop", "Cli")
NATIVE_TARGETS = ("Desktop-Native", "Cli-Native")

#: 旧拼写的**命令**兼容别名（`/snapshot NativeDesktop …` 仍然可用）：命令是贴在 PR 上的文本，
#: 直接删旧拼法会让历史评论里的命令失效。只影响命令解析，tag / 标题 / 资产名一律用新拼写。
NATIVE_TARGET_ALIASES = {"nativedesktop": "Desktop-Native", "nativecli": "Cli-Native"}

#: JVM 快照线的腿：**没有 macOS**、**只有 JDK 25**（#165 验收 1 明写「不得出现 macOS / JDK27」）。
#: 注意这与 dev 线（BuildJVMArtifacts 默认六腿）不同 —— dev 线出 macOS/JDK27，快照线不出。
#: 因此 `OS:MacOS` + JVM 目标 = 没有可用的腿 ⇒ 判定为不出包并说明原因（自检里有这条用例）。
JVM_LEGS: dict[str, list[tuple[str, str, int, bool]]] = {
    # (runner, label, jdk, upload_cli)
    "ubuntu-x64": [("ubuntu-latest", "ubuntu-x64", 25, True)],
    "windows-x64": [("windows-latest", "windows-x64", 25, False)],
}

#: 快照的发布页 tag / 发行名（ACANX 定案，2026-10-14）：
#:   tag  = `V<快照版本>-AHA-<目标>-SNAPSHOT`
#:   名称 = tag + 「（PR #<N> 快照）」
#: 与 dev 线的 `V<版本>-aha-jvm` / 正式发版的 `V<版本>` **同为 `V*` 家族**，
#: 因此必须满足两件事（都已落实）：
#:   1. `Release.yml` 的 tag 过滤要**排除** `-SNAPSHOT`（否则每次出快照都会触发正式发版流程）；
#:   2. 保持 `prerelease: true`（不进 `releases/latest`）。
#: 每个目标一条 tag（一次运行若同时选中 Desktop 与 Cli，会得到两条并列的 pre-release）。
TAG_FORMAT = "V{version}-AHA-{target}-SNAPSHOT"
# 发行名与 tag **完全相同**：再加「（PR #N 快照）」只是重复（PR 号已经在版本段 `.<PR 补零 5 位>.` 里，
# 「快照」也已经在 `-SNAPSHOT` 后缀里）——ACANX 定案删掉。
RELEASE_NAME_FORMAT = TAG_FORMAT

#: 快照包资产名（#165 §八）。`{label}` 用平台标签；CLI 包平台无关，但按 §八 仍带平台段。
# 命名对称（ACANX 定案，2026-10-14）：桌面端与 CLI 都要带目标段，且与原生线对称——
#   原生：AHA-Desktop-Native-… / AHA-Cli-Native-…
#   JVM ：AHA-Desktop-JVM-…    / AHA-Cli-JVM-…
# 早先桌面端写成 `AHA-JVM-…`（不带 `Desktop`）：标签写的是 `…-AHA-Desktop-SNAPSHOT`，
# 资产却叫 `AHA-JVM-…`，两处对不上，也读不出「这是桌面包」。
ASSET_FORMATS = {
    "Desktop": "AHA-Desktop-JVM-{version}-{label}-jdk25.zip",
    "Cli": "AHA-Cli-JVM-{version}-{label}-jdk25.zip",
}

#: 原生线的范围标签 → 原生腿标签（#165 §三）：Win→windows-amd64，Ubuntu→ubuntu-amd64。
#: 与 JVM 线不同：JVM 线是 `windows-x64` / `ubuntu-x64`（x64 是 JDK 侧用词），
#: 原生线沿用**既有**的 `windows-amd64` / `macos-arm64` / `ubuntu-amd64`（dev 线的腿标签，
#: 见 DesktopNative.yml 的静态矩阵），避免同一台机器两套叫法。
OS_LABELS_NATIVE: dict[str, str] = {
    "OS:Win": "windows-amd64",
    "OS:MacOS": "macos-arm64",
    "OS:Ubuntu": "ubuntu-amd64",
}

#: 原生线「全部腿」（#165 §三：`ubuntu-amd64` / `windows-amd64` / `macos-arm64` × 现有 JDK）。
#: 与 DesktopNative.yml / CliNative.yml 的**静态矩阵逐条对应**；`OS:*` 只按平台收窄
#: （该平台的 jdk25 / jdk27 两条腿都在内）。
NATIVE_LEGS: dict[str, list[tuple[str, str, int]]] = {
    # (runner, label, jdk)
    "ubuntu-amd64": [("ubuntu-latest", "ubuntu-amd64", 25), ("ubuntu-latest", "ubuntu-amd64", 27)],
    "windows-amd64": [("windows-latest", "windows-amd64", 25), ("windows-latest", "windows-amd64", 27)],
    "macos-arm64": [("macos-latest", "macos-arm64", 25)],
}

#: 原生线资产名（#165 §八）：镜像包 + 同名族的独立报告包（报告包是 dev 线既有产物，快照一并带上）。
NATIVE_ASSET_FORMATS = {
    "Desktop-Native": "AHA-Desktop-Native-{version}-{label}-jdk{jdk}.zip",
    "Cli-Native": "AHA-Cli-Native-{version}-{label}-jdk{jdk}.zip",
}

#: 原生腿标签 → 平台中文名（写进 run 摘要，验收 12 要求摘要写明「限定平台：Windows」）。
NATIVE_PLATFORM_NAMES = {"windows-amd64": "Windows", "macos-arm64": "macOS", "ubuntu-amd64": "Ubuntu"}

#: 两条线各用各的评论标记：JVM 线用 `aha-snapshot`，原生线用 `aha-snapshot-native`。
#: **必须分开**：原生标签事件若不认领就罢了，一旦认领却写同一个标记，就会把另一条线的
#: 评论覆盖成「未出包」（S3 实测踩到：ACANX 打原生标签，JVM 评论被改写）。
LINE_MARKERS = {"jvm": "<!-- aha-snapshot -->", "native": "<!-- aha-snapshot-native -->"}

#: 允许发 `/snapshot` 命令的账号关联（#165 §三：仅对仓库有写权限的账号生效）。
COMMENT_ASSOCIATIONS = ("OWNER", "MEMBER", "COLLABORATOR")


class PlanError(Exception):
    """输入不合法（命令写错、清单读不出来等）——调用方应回复用法而**不构建**。"""


def read_manifest(path: Path) -> tuple[list[str], dict[str, list[str]]]:
    """读快照清单（`.github/snapshot-paths.yml`）的标签与 A/B/C 路径分组。

    只认该文件固定的形状（与 `bin/CheckScripts.py` 的断言一致）：顶层 `labels:` / `paths:`，
    其下 `组名:` 与 `- 值`。形状变了会解析为空，由调用方报错，而不是静默放行。
    """
    labels: list[str] = []
    groups: dict[str, list[str]] = {}
    section = group = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        body = raw.split("#", 1)[0].rstrip()
        if not body.strip():
            continue
        indent = len(body) - len(body.lstrip())
        stripped = body.strip()
        if indent == 0 and stripped.endswith(":"):
            section, group = stripped[:-1], None
            continue
        if stripped.startswith("- "):
            value = stripped[2:].strip().strip("'\"")
            if section == "labels":
                labels.append(value)
            elif section == "paths" and group:
                groups[group].append(value)
            continue
        if indent > 0 and stripped.endswith(":") and section == "paths":
            group = stripped[:-1]
            groups.setdefault(group, [])
    if not labels or set(groups) < {"a", "b", "c"}:
        raise PlanError(f"{path} 解析不出标签与 A/B/C 路径分组（形状变了？）")
    return labels, groups


def match_group(path: str, patterns: list[str]) -> bool:
    """路径是否命中通配模式（支持 `**`、`*`、`?`；`**` 跨目录，`*` 不跨）。"""
    import fnmatch

    for pattern in patterns:
        if pattern == "**":
            return True
        if fnmatch.fnmatch(path, pattern):
            return True
        # `aha-*/src/test/**` 这类模式：fnmatch 的 `*` 不跨 `/`，这里补一层前缀匹配
        if pattern.endswith("/**") and fnmatch.fnmatch(path, pattern[:-3] + "/*"):
            return True
        if pattern.startswith("**/") and fnmatch.fnmatch(path, "*" + pattern[2:]):
            return True
    return False


def classify(changed: list[str], groups: dict[str, list[str]]) -> tuple[bool, str]:
    """变更分类（#165 §四）：**B 优先**，其次 A，都不命中则按 A（C 组保守兜底）。"""
    if not changed:
        return True, "无法取得改动清单（保守兜底：按 A 组处理 ⇒ 出包）"
    hit_b = [p for p in changed if match_group(p, groups["b"])]
    if hit_b and len(hit_b) == len(changed):
        sample = "、".join(hit_b[:3])
        return False, f"B 组：本次只改了不影响产物的路径（如 `{sample}`）"
    hit_a = [p for p in changed if match_group(p, groups["a"])]
    if hit_a:
        sample = "、".join(hit_a[:3])
        return True, f"A 组：影响产物的路径被改动（如 `{sample}`）"
    sample = "、".join(changed[:3])
    return True, f"C 组：未列到的路径，按 A 组处理（保守兜底；如 `{sample}`）"


def parse_command(command: str) -> tuple[list[str], list[str]]:
    """解析 `/snapshot <target> [os…]`（大小写不敏感），返回 (目标, 平台**词**：win / macos / ubuntu)。

    写法错误（目标不认识、平台不认识、缺参数）一律抛 `PlanError` —— 调用方回复用法、**不构建**。
    """
    match = COMMAND_RE.match(command.strip())
    if not match:
        raise PlanError("命令格式应为 `/snapshot <target> [os ...]`")
    raw_target = match.group(1)
    canonical = NATIVE_TARGET_ALIASES.get(raw_target.lower(), raw_target)
    targets = [name for name in (*JVM_TARGETS, *NATIVE_TARGETS) if name.lower() == canonical.lower()]
    if not targets:
        raise PlanError(
            f"不认识的目标 {raw_target!r}；合法取值："
            + " / ".join((*JVM_TARGETS, *NATIVE_TARGETS))
        )
    # 返回**平台词**（win / macos / ubuntu）而不是腿标签：JVM 线叫 `windows-x64`、
    # 原生线叫 `windows-amd64`，同一个词要在**各自的线**里映射（这一步交给 decide）。
    # 早先在解析处直接映射成 JVM 标签，于是原生线的 `/snapshot Desktop-Native Win`
    # 会去找 `windows-amd64` 却拿到 `windows-x64` ⇒ 判定「没有对应的腿」（自检抓到的真 bug）。
    words: list[str] = []
    for item in match.group(2).split():
        word = item.lower()
        if word not in OS_WORDS:
            raise PlanError(f"不认识的平台 {item!r}；合法取值：Win / MacOS / Ubuntu")
        if word not in words:
            words.append(word)
    return targets, words


def _os_word(platform: str) -> str:
    """平台键 → `/snapshot` 命令里的词（两种命名都在：JVM 线 `*-x64`，原生线 `*-amd64`）。"""
    return {
        "windows-x64": "win", "macos-arm64": "macos", "ubuntu-x64": "ubuntu",
        "windows-amd64": "win", "ubuntu-amd64": "ubuntu",
    }[platform]


def build_version(baseline: str, pr: int, now: datetime) -> str:
    """快照版本号 `<基线>.<PR 补零 5 位>.<MMddHHmm>`（北京时间，见 #165 §九）。"""
    beijing = now.astimezone(timezone(timedelta(hours=8)))
    return f"{baseline}.{pr:05d}.{beijing:%m%d%H%M}"


def build_legs(targets: list[str], platforms: list[str], line: str = "jvm") -> list[dict]:
    """构建腿清单。

    JVM 线：只出 JDK 25、只出被选中的平台（未选则两个平台都出，见 #165 验收 1）。
    原生线：平台 × 现有 JDK（jdk25 全平台、jdk27 只有 ubuntu/windows），未选平台则全部腿。
    """
    if line == "native":
        labels = platforms or list(NATIVE_LEGS)
        legs: list[dict] = []
        for label in labels:
            for runner, leg_label, jdk in NATIVE_LEGS.get(label, []):
                legs.append({"os": runner, "label": leg_label, "jdk": jdk})
        return legs
    labels = platforms or ["ubuntu-x64", "windows-x64"]
    legs = []
    for label in labels:
        for runner, leg_label, jdk, upload_cli in JVM_LEGS.get(label, []):
            legs.append({"os": runner, "label": leg_label, "jdk": jdk, "upload_cli": upload_cli})
    return legs


def asset_names(targets: list[str], version: str, platforms: list[str], line: str = "jvm") -> list[str]:
    """资产名列表（#165 §八）：JVM 线 `<前缀>-<版本>-<平台>-jdk25.zip`；原生线按**腿**（平台 × JDK）且带报告包。"""
    names: list[str] = []
    if line == "native":
        for target in targets:
            fmt = NATIVE_ASSET_FORMATS.get(target)
            if not fmt:
                continue
            for leg in build_legs([target], platforms, line="native"):
                names.append(fmt.format(version=version, label=leg["label"], jdk=leg["jdk"]))
                names.append(fmt.format(version=version, label=leg["label"], jdk=leg["jdk"]).replace(".zip", "-Report.zip"))
        return names
    labels = platforms or ["ubuntu-x64", "windows-x64"]
    for target in targets:
        fmt = ASSET_FORMATS.get(target)
        if not fmt:
            continue
        for label in labels:
            names.append(fmt.format(version=version, label=label))
    return names


def release_groups(targets: list[str], version: str, platforms: list[str], pr: int,
                   line: str = "jvm") -> list[dict]:
    """按目标拆出发布条目（每个目标一条 tag / 一个 pre-release）。"""
    groups: list[dict] = []
    for target in targets:
        groups.append({
            "target": target,
            "tag": TAG_FORMAT.format(version=version, target=target),
            "name": RELEASE_NAME_FORMAT.format(version=version, target=target),
            "assets": asset_names([target], version, platforms, line=line),
        })
    return groups


def decide(labels: list[str], changed: list[str], baseline: str, pr: int,
           now: datetime, groups: dict[str, list[str]], command: str = "",
           label_event: bool = False, line: str = "jvm", native_target: str = "") -> dict:
    """核心判定：该不该出包、出哪些目标与平台、版本号与资产名。

    `line`：`jvm`（SnapshotJVM.yml）或 `native`（DesktopNative.yml / CliNative.yml）。
    `native_target`：原生线**本条工作流**负责的目标 —— 一个原生工作流只负责一个目标；
    请求里若不含它，本工作流「不构建、不动 PR 评论」（`owns_comment=False`），
    由对应的那条工作流认领。评论标记也按线区分（`LINE_MARKERS`），两条线互不覆盖。
    """
    marker = LINE_MARKERS[line]
    os_labels = OS_LABELS if line == "jvm" else OS_LABELS_NATIVE
    targets = [SNAPSHOT_LABELS[name] for name in labels if name in SNAPSHOT_LABELS]
    platforms = [os_labels[name] for name in labels if name in os_labels]
    explicit_command = bool(command.strip())

    def out(**kw) -> dict:
        base = {"owns_comment": True, "should_build": False, "pr": pr, "targets": [], "platforms": [],
                "releases": [], "version": build_version(baseline, pr, now), "legs": [], "matrix": [],
                "assets": [], "explicit": explicit_command, "marker": marker, "line": line, "reason": ""}
        base.update(kw)
        return base

    if explicit_command:
        cmd_targets, cmd_words = parse_command(command)
        targets = list(dict.fromkeys(cmd_targets))
        # 命令里的平台词 → 本线的腿标签（缺省时沿用标签给的平台范围）
        cmd_platforms = [os_labels[OS_WORDS[word]] for word in cmd_words]
        platforms = list(dict.fromkeys(cmd_platforms or platforms))
        reason_prefix = "显式请求：`/snapshot` 命令"
    elif not targets:
        return out(reason="未打任何 `SNAPSHOT:*` 触发标签（四类目标默认都不出包）")
    elif label_event:
        # 打标签这一动作本身就是「显式请求」：只改文档也照出（#165 验收 16）
        reason_prefix = "显式请求：新打了触发标签"
    else:
        reason_prefix = ""

    if line == "native" and native_target not in targets:
        # 本工作流只负责一个原生目标。命令 / 标签指向别的目标时：不构建，且**不动评论**
        # （动了就会把该目标的评论覆盖成「未出包」）。
        return out(targets=targets, platforms=platforms, explicit=bool(reason_prefix),
                   reason=f"本次请求的目标 {targets} 不含 {native_target or '(未指定)'} —— "
                          f"本工作流只负责 {native_target or '(未指定)'}，交由对应的原生工作流处理")

    if line == "jvm" and not [t for t in targets if t in JVM_TARGETS]:
        # 本工作流只管 JVM 线；纯原生目标交给 DesktopNative.yml / CliNative.yml（S4）。
        # `owns_comment=False`：**别去动 PR 评论**——否则原生线的标签事件会把 JVM 线的
        # 快照评论覆盖成「未出包」（实测踩到：ACANX 给 PR 打了原生标签，JVM 评论被改写）。
        return out(owns_comment=False, targets=targets, platforms=platforms,
                   explicit=explicit_command,
                   reason=f"目标 {targets} 属原生线，由 DesktopNative.yml / CliNative.yml 处理（本工作流不构建）")

    line_targets = [t for t in targets if t in (JVM_TARGETS if line == "jvm" else NATIVE_TARGETS)]

    needed, reason = classify(changed, groups)
    if not needed and not reason_prefix:
        return out(targets=line_targets, platforms=platforms, reason=reason)

    legs = build_legs(line_targets, platforms, line=line)
    full_reason = reason if not reason_prefix else f"{reason_prefix} ⇒ 忽略变更分类（{reason}）"
    if not legs:
        return out(targets=line_targets, platforms=platforms, explicit=bool(reason_prefix),
                   reason=f"选中的平台 {platforms} 在 {line} 线上没有对应的腿")
    version = build_version(baseline, pr, now)
    extra_reason = full_reason
    if line == "native" and platforms:
        # 验收 12 要求摘要写明「限定平台：Windows」——顺手把它放进判定依据里
        words = "、".join(NATIVE_PLATFORM_NAMES.get(p, p) for p in sorted(set(platforms)))
        extra_reason = f"{full_reason}；限定平台：{words}"
    return out(should_build=True, targets=line_targets,
               platforms=sorted({leg["label"] for leg in legs}),
               version=version, legs=[f"{leg['label']}/jdk{leg['jdk']}" for leg in legs],
               matrix=legs, assets=asset_names(line_targets, version, platforms, line=line),
               releases=release_groups(line_targets, version, platforms, pr, line=line),
               explicit=bool(reason_prefix) or explicit_command, reason=extra_reason)


def notice_body(reason: str, marker: str = "<!-- aha-snapshot -->") -> str:
    """「未构建」类评论正文（用法错误 / 无权限 / 未打标签都走这里）。

    放在脚本里而不是 YAML 里，是为了让工作流里只出现**单行**调用：
    在 YAML 块标量里写多行字符串，行首缩进一旦小于块基准，块会被提前结束
    （本轮已因此把 SnapshotJVM.yml 写坏三次）。
    """
    return (
        f"{marker}\n"
        "### 快照包：未构建\n\n"
        f"- 原因：{reason}\n"
        "- 用法：`/snapshot <target> [os ...]`；目标取 `Desktop` / `Cli`，"
        "平台取 `Win` / `MacOS` / `Ubuntu`（可多个，缺省 = 该目标全部平台）。\n"
        "- 也可以直接给本 PR 打触发标签（`SNAPSHOT:Desktop` / `SNAPSHOT:Cli`），不需要命令。\n"
    )


def comment_body(plan: dict, run_url: str = "") -> str:
    """PR 汇总评论（幂等标记 + 版本 + 到期日 + 资产 + run 链接 + 取件命令，见 #165 §六）。"""
    marker = plan.get("marker", "<!-- aha-snapshot -->")
    if not plan["should_build"]:
        return notice_body(plan["reason"], marker=marker).replace("未构建", "本次未出包")
    expiry = (datetime.now(timezone.utc).date()
              + timedelta(days=SNAPSHOT_TTL_DAYS)).isoformat()
    release_blocks = "\n".join(
        f"#### `{group['tag']}`\n"
        + "".join(f"- 资产：`{name}`\n" for name in group["assets"])
        + f"- 发布页：https://github.com/ACANX/AHA/releases/tag/{group['tag']}\n"
        for group in plan.get("releases", [])
    )
    # 平台名直接用**发行版名**（`ubuntu-x64`）而不是 `linux-x64`：后者会被读成
    # 「所有 Linux 发行版都支持」，而我们只在 Ubuntu 上构建与验证过。
    # 这层事实仍然显式写一次，避免读者自行放大范围。
    platform_note = (
        "\n**平台验证范围**（不是「全 Linux 都支持」的意思）：\n"
        "- `ubuntu-x64`：构建与冒烟环境为 **Ubuntu 24.04**（GitHub runner 镜像 `ubuntu-24.04`）；"
        "CentOS / RHEL 等其他发行版**未验证**；\n"
        "- `windows-x64`：构建与冒烟环境为 GitHub Actions 的 Windows runner（`windows-latest`，本 run 的镜像是 `windows-2025-vs2026`）。\n"
    ) if any(name.startswith(("AHA-Desktop-JVM", "AHA-Cli-JVM")) for name in plan["assets"]) else ""
    native_note = (
        "\n> **原生包是试验性产物**（GraalVM native-image），不是正式发行包；"
        "同一平台的镜像包与 `-Report.zip` 报告包一一对应。\n"
    ) if any(name.startswith(("AHA-Desktop-Native", "AHA-Cli-Native")) for name in plan["assets"]) else ""
    return (
        f"{marker}\n"
        f"### 快照包：`{plan['version']}`\n\n"
        f"- 目标：{', '.join(plan['targets'])}（平台：{', '.join(plan['platforms'])}）\n"
        f"- 判定：{plan['reason']}\n"
        f"\n{release_blocks}\n"
        f"- 有效期至：**{expiry}**（{SNAPSHOT_TTL_DAYS} 天；过期后仍可下载，只作提示）\n"
        f"{platform_note}{native_note}"
        f"- 构建：{run_url or '（见本 PR 的 Actions 运行）'}\n\n"
        "取件（在仓库的 `Dist/` 下运行，无需 token / 无需 gh）：\n"
        "```\n"
        f"Dist\\UpdateSnapshot.cmd -Pr {plan['pr']}\n"
        "```\n"
    )


#: `--self-test` 用例：覆盖 #165 验收 14 / 15 / 16 / 17 / 26 的判定边界。
SELF_TEST_CASES: list[tuple[str, dict, bool, str]] = [
    # (说明, 输入, 是否应出包, 期望要点)
    ("未打任何标签 ⇒ 不出包（验收 14）",
     {"labels": [], "changed": ["aha-common/src/main/java/A.java"]}, False, "未打任何"),
    ("只改文档 ⇒ 不出包（验收 15）",
     {"labels": ["SNAPSHOT:Desktop"], "changed": ["Docs/Guide/BuildGuide.md"]}, False, "B 组"),
    ("只改文档 + 新打标签 ⇒ 出包（显式请求，验收 16）",
     {"labels": ["SNAPSHOT:Desktop"], "changed": ["Docs/Guide/BuildGuide.md"], "label_event": True},
     True, "显式请求"),
    ("改 A 组路径 ⇒ 出包（验收 17）",
     {"labels": ["SNAPSHOT:Desktop"], "changed": ["aha-common/pom.xml"]}, True, "A 组"),
    ("未列到的路径 ⇒ 按 A 出包（C 组兜底）",
     {"labels": ["SNAPSHOT:Desktop"], "changed": ["something/odd.txt"]}, True, "C 组"),
    ("测试目录属 B 组（B 优先于 A）",
     {"labels": ["SNAPSHOT:Desktop"], "changed": ["aha-desktop/src/test/java/T.java"]}, False, "B 组"),
    ("命令全小写等效（验收 26）",
     {"labels": [], "changed": ["aha-common/pom.xml"], "command": "/snapshot desktop win"},
     True, "windows-x64"),
    ("命令带多个平台 ⇒ 多腿",
     {"labels": [], "changed": ["aha-common/pom.xml"],
      "command": "/snapshot Desktop Win Ubuntu"}, True, "ubuntu-x64"),
    ("纯原生目标 ⇒ 本工作流不构建（交给原生线）",
     {"labels": ["SNAPSHOT:NativeDesktop"], "changed": ["aha-common/pom.xml"]}, False, "原生线"),
    ("JVM 目标 + OS:MacOS ⇒ JVM 线没有该平台的腿",
     {"labels": ["SNAPSHOT:Desktop", "OS:MacOS"], "changed": ["aha-common/pom.xml"]}, False, "没有对应的腿"),
]

#: 原生线（S4 / #172）的判定用例：line="native" + 本工作流负责的目标 Desktop-Native。
SELF_TEST_CASES_NATIVE: list[tuple[str, dict, bool, str]] = [
    # (说明, 输入, 是否出包, 期望要点)
    ("原生线：SNAPSHOT:NativeDesktop ⇒ 全部腿（3 平台 5 腿，含 jdk27）",
     {"labels": ["SNAPSHOT:NativeDesktop"], "changed": ["aha-desktop-native/pom.xml"]},
     True, "windows-amd64/jdk27"),
    ("原生线：+ OS:Win ⇒ 只出 Windows 的两条腿（验收 12）",
     {"labels": ["SNAPSHOT:NativeDesktop", "OS:Win"], "changed": ["aha-desktop-native/pom.xml"]},
     True, "限定平台：Windows"),
    ("原生线：只打 OS:Win（无触发标签）⇒ 不出包（反向验证）",
     {"labels": ["OS:Win"], "changed": ["aha-desktop-native/pom.xml"]}, False, "未打任何"),
    ("原生线：命令指向 CLI 目标 ⇒ 桌面工作流不认领、不构建（S4）",
     {"labels": [], "changed": ["aha-desktop-native/pom.xml"], "command": "/snapshot Cli-Native MacOS"},
     False, "只负责"),
    ("原生线：命令全小写 + 两平台 ⇒ 四条腿（验收 26 的等价行为）",
     {"labels": [], "changed": ["aha-desktop-native/pom.xml"], "command": "/snapshot desktop-native win ubuntu"},
     True, "ubuntu-amd64/jdk27"),
    # 旧拼写的命令别名必须仍然可用（否则贴在 PR 上的历史命令会「不认识的目标」）
    ("原生线：旧拼写 `/snapshot NativeDesktop` 等价可用（命令别名，不影响 tag 命名）",
     {"labels": [], "changed": ["aha-desktop-native/pom.xml"], "command": "/snapshot NativeDesktop Win"},
     True, "windows-amd64/jdk27"),
    ("原生线：只改文档 ⇒ 不出包（B 组，验收 15 的等价行为）",
     {"labels": ["SNAPSHOT:NativeDesktop"], "changed": ["Docs/Guide/BuildGuide.md"]}, False, "B 组"),
    ("原生线：JVM 目标 ⇒ 原生工作流不认领",
     {"labels": ["SNAPSHOT:Desktop"], "changed": ["aha-desktop-native/pom.xml"]}, False, "只负责"),
]

#: 必须报错（不构建）的命令写法（验收 26）
SELF_TEST_BAD_COMMANDS = [
    "/snapshot desktopx", "/snapshot Desktop Win Bogus", "/snapshot", "/snapsho Desktop",
    # #165 §三 的示例把 `NativeDesktop` 写成了 `nativdesktop`（少一个 e）：那是**拼写错误**，
    # 不是大小写差异 ⇒ 必须按「不认识的目标」拒绝（方案里的例子已在本轮报给 #165 更正）。
    "/snapshot nativdesktop win",
]


def self_test() -> int:
    """自检判定边界。"""
    manifest = Path(__file__).resolve().parents[2] / ".github" / "snapshot-paths.yml"
    labels, groups = read_manifest(manifest)
    now = datetime(2026, 10, 14, 10, 14, 15, tzinfo=timezone.utc)
    problems = 0
    print("快照判定自检：")
    for note, kwargs, should_build, expect in SELF_TEST_CASES:
        plan = decide(labels=kwargs.get("labels", []), changed=kwargs.get("changed", []),
                      baseline="0.1.3", pr=171, now=now, groups=groups,
                      command=kwargs.get("command", ""), label_event=kwargs.get("label_event", False))
        ok = plan["should_build"] == should_build and (expect in plan["reason"] or expect in str(plan["legs"]))
        problems += 0 if ok else 1
        print(f"  {'✅' if ok else '❌'} {note}"
              f"（应{'出包' if should_build else '不出包'}；实际{'出包' if plan['should_build'] else '不出包'}）"
              f" —— {plan['reason'][:70]}")
    for command in SELF_TEST_BAD_COMMANDS:
        try:
            decide(labels=[], changed=["aha-common/pom.xml"], baseline="0.1.3", pr=171,
                   now=now, groups=groups, command=command)
            problems += 1
            print(f"  ❌ 命令 {command!r} 应被拒，实际通过")
        except PlanError as exc:
            print(f"  ✅ 命令 {command!r} 被拒：{str(exc)[:66]}")
    # 版本号格式（验收 2：北京时间 ≠ UTC）
    version = build_version("0.1.3", 171, now)
    utc = now.strftime("%m%d%H%M")
    beijing = now.astimezone(timezone(timedelta(hours=8))).strftime("%m%d%H%M")
    ok = version == f"0.1.3.00171.{beijing}" and beijing != utc
    problems += 0 if ok else 1
    print(f"  {'✅' if ok else '❌'} 版本号 {version}（UTC 段 {utc}，北京时间段 {beijing}，二者不同）")
    # 纯原生目标：本工作流既不构建，也**不得**认领评论（否则会覆盖 JVM 线的快照评论）
    native_plan = decide(labels=["SNAPSHOT:NativeDesktop"], changed=["aha-common/pom.xml"],
                         baseline="0.1.3", pr=171, now=now, groups=groups)
    owns_ok = native_plan["owns_comment"] is False and native_plan["should_build"] is False
    problems += 0 if owns_ok else 1
    print(f"  {'✅' if owns_ok else '❌'} 纯原生目标：本工作流不构建且不认领评论（owns_comment="
          f"{native_plan['owns_comment']}）")

    # tag / 发行名形状（ACANX 定案）：`V<版本>-AHA-<目标>-SNAPSHOT`
    # 注意：这里必须**重新判定一次**，不能复用上面循环里的 plan（那是最后一个用例，
    # 可能正落在「不出包」分支 ⇒ releases 为空 ⇒ 断言永远失败）。
    plan = decide(labels=["SNAPSHOT:Desktop", "SNAPSHOT:Cli"], changed=["aha-common/pom.xml"],
                  baseline="0.1.3", pr=171, now=now, groups=groups)
    tags_ok = all(
        group["tag"].startswith("V" + plan["version"]) and group["tag"].endswith("-SNAPSHOT")
        and f"-AHA-{group['target']}-SNAPSHOT" in group["tag"]
        for group in plan["releases"]
    ) and len(plan["releases"]) == len(plan["targets"]) and plan["releases"]
    problems += 0 if tags_ok else 1
    print(f"  {'✅' if tags_ok else '❌'} 每个目标一条 tag："
          + "、".join(g["tag"] for g in plan["releases"]))
    prefix_ok = all(
        all(name.startswith(f"AHA-{group['target']}-JVM-") for name in group["assets"])
        for group in plan["releases"]
    )
    problems += 0 if prefix_ok else 1
    print(f"  {'✅' if prefix_ok else '❌'} 资产前缀与目标对称（AHA-<目标>-JVM-…）："
          + "；".join(f"{g['target']}→{g['assets'][0]}" for g in plan["releases"]))
    name_ok = all(g["name"] == g["tag"] for g in plan["releases"])
    problems += 0 if name_ok else 1
    print(f"  {'✅' if name_ok else '❌'} 发行名与 tag 完全相同（无多余后缀）："
          + "、".join(g["name"] for g in plan["releases"]))
    old_tag_ok = "snapshot-pr" not in comment_body(plan, "https://example/run")
    problems += 0 if old_tag_ok else 1
    print(f"  {'✅' if old_tag_ok else '❌'} 评论正文不再出现旧 tag 形式（`snapshot-pr<N>`）")

    # 评论正文必须写清平台名的语义边界（本轮实测：该腿的验证环境只有 Ubuntu）
    plan = decide(labels=["SNAPSHOT:Desktop"], changed=["aha-common/pom.xml"], baseline="0.1.3",
                  pr=171, now=now, groups=groups)
    body = comment_body(plan, "https://example/run")
    ok_note = "平台验证范围" in body and "Ubuntu 24.04" in body and "未验证" in body
    problems += 0 if ok_note else 1
    print(f"  {'✅' if ok_note else '❌'} 评论正文写明平台验证范围（Ubuntu 24.04 / 其他发行版未验证）")
    checks = len(SELF_TEST_CASES) + len(SELF_TEST_BAD_COMMANDS) + 1
    for note, kwargs, should_build, expect in SELF_TEST_CASES_NATIVE:
        plan = decide(labels=kwargs.get("labels", []), changed=kwargs.get("changed", []),
                      baseline="0.1.3", pr=172, now=now, groups=groups,
                      command=kwargs.get("command", ""), line="native",
                      native_target="Desktop-Native")
        text = json.dumps(plan, ensure_ascii=False)
        ok = plan["should_build"] is should_build and expect in text
        problems += 0 if ok else 1
        print(f"  {'✅' if ok else '❌'} {note}")
        checks += 1

    # 原生线：全腿 = 5 条（3 平台 × jdk25 + ubuntu/windows × jdk27）；OS:Win ⇒ 2 条
    full = decide(labels=["SNAPSHOT:NativeDesktop"], changed=["aha-desktop-native/pom.xml"],
                  baseline="0.1.3", pr=172, now=now, groups=groups, line="native",
                  native_target="Desktop-Native")
    ok_full = len(full["legs"]) == 5 and len(full["releases"]) == 1 \
        and full["releases"][0]["tag"] == f"V{full['version']}-AHA-Desktop-Native-SNAPSHOT" \
        and full["version"].endswith("10141814") \
        and all(a.startswith("AHA-Desktop-Native-") for a in full["assets"])
    problems += 0 if ok_full else 1
    print(f"  {'✅' if ok_full else '❌'} 原生线：全部腿 = 5 条，且每个目标一条 `V<版本>-AHA-<目标>-SNAPSHOT`")
    checks += 1

    win = decide(labels=["SNAPSHOT:NativeDesktop", "OS:Win"], changed=["aha-desktop-native/pom.xml"],
                 baseline="0.1.3", pr=172, now=now, groups=groups, line="native",
                 native_target="Desktop-Native")
    ok_win = win["legs"] == ["windows-amd64/jdk25", "windows-amd64/jdk27"] and win["platforms"] == ["windows-amd64"]
    problems += 0 if ok_win else 1
    print(f"  {'✅' if ok_win else '❌'} 原生线：`OS:Win` ⇒ 恰好 windows-amd64 的 jdk25 + jdk27 两条腿")
    checks += 1

    # 两条线的评论标记必须不同：否则原生线会把 JVM 线的评论覆盖掉
    ok_marker = full["marker"] == "<!-- aha-snapshot-native -->" \
        and decide(labels=["SNAPSHOT:Desktop"], changed=["aha-common/pom.xml"], baseline="0.1.3",
                   pr=172, now=now, groups=groups)["marker"] == "<!-- aha-snapshot -->"
    problems += 0 if ok_marker else 1
    print(f"  {'✅' if ok_marker else '❌'} 两条线使用不同的评论标记（互不覆盖）")
    checks += 1

    if problems:
        print(f"\n[问题] {problems} 条自检不符合预期")
        return 1
    print(f"\n✅ {checks} 条自检全部符合预期")
    return 0



def emit_outputs(args) -> int:
    """把判定结果写成 `$GITHUB_OUTPUT` 片段 —— 工作流里只留**单行调用**。

    为什么整段放脚本：三条工作流（JVM / 原生桌面 / 原生 CLI）需要同一套「取 PR 号 → 取改动清单 →
    判定 → 产出 outputs」的逻辑。抄三份 shell 的话，改一处漏两处；而且 YAML 块标量写多行字符串
    有两个实测坑：① 行首缩进小于块基准会让块提前结束；② `comment=多行` 会报 `Invalid format`。
    这里统一用 heredoc 形式输出多行值，三条工作流共用。

    需要的环境变量：`GITHUB_OUTPUT`（必需）、`EVENT` / `ACTION` / `PR` / `LABELS` / `COMMENT` /
    `ASSOCIATION` / `BASELINE` / `CHANGED_FILES` / `RUN_URL` / `INPUT_TARGET` / `INPUT_OS`。
    """
    target = os.environ.get("GITHUB_OUTPUT", "")
    if not target:
        print("[问题] --emit-outputs 需要 GITHUB_OUTPUT 环境变量", file=sys.stderr)
        return 2
    marker = LINE_MARKERS[args.line]
    summary = os.environ.get("GITHUB_STEP_SUMMARY", "")

    def put(key: str, value: str) -> None:
        with open(target, "a", encoding="utf-8") as fh:
            fh.write(f"{key}={value}\n")

    def put_block(key: str, value: str) -> None:
        with open(target, "a", encoding="utf-8") as fh:
            fh.write(f"{key}<<AHA_SNAPSHOT_EOF\n{value}\nAHA_SNAPSHOT_EOF\n")

    def note(text: str) -> None:
        if summary:
            with open(summary, "a", encoding="utf-8") as fh:
                fh.write(text + "\n")

    event = os.environ.get("EVENT", "")
    action = os.environ.get("ACTION", "")
    association = os.environ.get("ASSOCIATION", "")
    pr = (os.environ.get("PR") or "").strip()
    labels = [x.strip() for x in (os.environ.get("LABELS") or "").split(",") if x.strip()]
    command = os.environ.get("COMMENT") or ""
    now = datetime.fromisoformat(args.now) if args.now else datetime.now(timezone.utc)

    # `pr` 在**任何分支**都要产出：评论作业的门依赖它
    # （早先只在成功分支写 ⇒「非法命令回复用法」这条路径根本不回写评论，真机实测抓到）。
    put("pr", pr)

    # 权限：`/snapshot` 只对仓库有写权限的账号生效（其他人发 ⇒ 回复说明、不构建）
    if event == "issue_comment" and association not in COMMENT_ASSOCIATIONS:
        put("should_build", "false")
        put("owns_comment", "true")
        put("marker", marker)
        put_block("comment", notice_body(
            f"`/snapshot` 命令只对**本仓库有写权限**的账号生效（当前账号关联：{association or 'NONE'}）",
            marker=marker))
        print(f"权限不足（{association or 'NONE'}）⇒ 回复说明，不构建")
        return 0

    if event == "workflow_dispatch":
        command = f"/snapshot {os.environ.get('INPUT_TARGET') or 'Desktop'} {os.environ.get('INPUT_OS') or ''}"

    changed: list[str] = []
    files = os.environ.get("CHANGED_FILES")
    if files and Path(files).is_file():
        changed = [x.strip() for x in Path(files).read_text(encoding="utf-8").splitlines() if x.strip()]

    try:
        _, groups = read_manifest(Path(args.manifest))
        plan = decide(labels=labels, changed=changed, baseline=os.environ.get("BASELINE", ""),
                      pr=int(pr or 0), now=now, groups=groups, command=command,
                      label_event=(event == "pull_request" and action == "labeled"),
                      line=args.line, native_target=args.native_target or "")
    except PlanError as exc:
        # 参数非法 ⇒ 回复用法、不构建（#165 验收 26）
        put("should_build", "false")
        put("owns_comment", "true")
        put("marker", marker)
        put_block("comment", notice_body(str(exc), marker=marker))
        print(f"[问题] {exc} ⇒ 回复用法，不构建")
        return 0

    put("should_build", str(plan["should_build"]).lower())
    put("version", plan["version"])
    put("targets", ",".join(plan["targets"]))
    put("platforms", ",".join(plan["platforms"]))
    put("legs", json.dumps(plan["legs"], ensure_ascii=False, separators=(",", ":")))
    put("matrix", json.dumps(plan["matrix"], ensure_ascii=False, separators=(",", ":")))
    put("releases", json.dumps(plan["releases"], ensure_ascii=False, separators=(",", ":")))
    put("assets", json.dumps(plan["assets"], ensure_ascii=False, separators=(",", ":")))
    put("owns_comment", str(plan.get("owns_comment", True)).lower())
    put("marker", plan.get("marker", marker))
    put("comment", "")   # 占位，随后用 heredoc 覆盖成真正的多行正文
    put_block("comment", comment_body(plan, os.environ.get("RUN_URL", "")))
    print("判定完成：" + json.dumps(plan, ensure_ascii=False))
    note("### 快照判定（" + args.line + " 线）\n\n"
         f"- 是否出包：**{plan['should_build']}**\n"
         f"- 版本：`{plan['version']}`\n"
         f"- 目标：{', '.join(plan['targets']) or '（无）'}\n"
         f"- 平台：{', '.join(plan['platforms']) or '（无）'}\n"
         f"- 腿：{', '.join(plan['legs']) or '（无）'}\n"
         f"- 依据：{plan['reason']}")
    return 0

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="快照出包判定（issue #165）")
    parser.add_argument("--labels", default="", help="PR 标签（逗号分隔）")
    parser.add_argument("--changed-files", help="改动文件清单（每行一个路径）")
    parser.add_argument("--baseline", default="", help="基线版本（如 0.1.3）")
    parser.add_argument("--pr", type=int, default=0, help="PR 号")
    parser.add_argument("--command", default="", help="PR 评论里的 `/snapshot …` 命令")
    parser.add_argument("--label-event", action="store_true", help="本次事件是「新打标签」（= 显式请求）")
    parser.add_argument("--now", help="当前时间（ISO8601；默认取系统时间，仅便于自检）")
    parser.add_argument("--run-url", default="", help="本次 run 的链接（写进 PR 评论）")
    parser.add_argument("--manifest", default=str(Path(__file__).resolve().parents[2] / ".github" / "snapshot-paths.yml"))
    parser.add_argument("--json", action="store_true", help="输出 JSON")
    parser.add_argument("--comment", action="store_true", help="输出 PR 评论正文")
    parser.add_argument("--notice", metavar="REASON", help="输出「未构建」评论正文（用法错误 / 无权限等）")
    parser.add_argument("--line", choices=("jvm", "native"), default="jvm",
                        help="工作流所属的线：jvm（SnapshotJVM.yml）或 native（原生线）")
    parser.add_argument("--native-target", default="", choices=("", "Desktop-Native", "Cli-Native"),
                        help="原生线：本工作流负责的目标（一个工作流只负责一个）")
    parser.add_argument("--emit-outputs", action="store_true",
                        help="写成 $GITHUB_OUTPUT 片段（工作流里的单行调用模式）")
    parser.add_argument("--self-test", action="store_true", help="自检判定边界")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()
    if args.line == "native" and not args.native_target:
        print("[问题] --line native 必须同时给 --native-target", file=sys.stderr)
        return 2
    if args.emit_outputs:
        return emit_outputs(args)
    if args.notice is not None:
        print(notice_body(args.notice, marker=LINE_MARKERS[args.line]))
        return 0
    try:
        _, groups = read_manifest(Path(args.manifest))
        changed: list[str] = []
        if args.changed_files:
            path = Path(args.changed_files)
            if path.is_file():
                changed = [line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
        now = datetime.fromisoformat(args.now) if args.now else datetime.now(timezone.utc)
        labels = [item.strip() for item in args.labels.split(",") if item.strip()]
        plan = decide(labels=labels, changed=changed, baseline=args.baseline,
                      pr=args.pr, now=now, groups=groups, command=args.command,
                      label_event=args.label_event, line=args.line,
                      native_target=args.native_target or "")
    except PlanError as exc:
        print(f"[问题] {exc}", file=sys.stderr)
        return 1
    print(comment_body(plan, args.run_url) if args.comment else json.dumps(plan, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
