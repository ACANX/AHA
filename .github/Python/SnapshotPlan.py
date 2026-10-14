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
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

#: 快照有效期（天）。与 #165 §十「7 天有效期」一致；到期只影响提示与前缀清理，不阻止下载。
SNAPSHOT_TTL_DAYS = 7

#: 触发标签 → 出包目标（#165 §三 标签矩阵）。
#: 值是「目标名」：JVM 线出 JVM 便携包（Desktop / Cli），原生线出原生包（NativeDesktop / NativeCli）。
SNAPSHOT_LABELS: dict[str, str] = {
    "SNAPSHOT:Desktop": "Desktop",
    "SNAPSHOT:Cli": "Cli",
    "SNAPSHOT:NativeDesktop": "NativeDesktop",
    "SNAPSHOT:NativeCli": "NativeCli",
}

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
NATIVE_TARGETS = ("NativeDesktop", "NativeCli")

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
    """解析 `/snapshot <target> [os…]`（大小写不敏感），返回 (目标, 平台限定)。

    写法错误（目标不认识、平台不认识、缺参数）一律抛 `PlanError` —— 调用方回复用法、**不构建**。
    """
    match = COMMAND_RE.match(command.strip())
    if not match:
        raise PlanError("命令格式应为 `/snapshot <target> [os ...]`")
    raw_target = match.group(1)
    targets = [name for name in (*JVM_TARGETS, *NATIVE_TARGETS) if name.lower() == raw_target.lower()]
    if not targets:
        raise PlanError(
            f"不认识的目标 {raw_target!r}；合法取值："
            + " / ".join((*JVM_TARGETS, *NATIVE_TARGETS))
        )
    platforms: list[str] = []
    for item in match.group(2).split():
        matched = [key for key in OS_LABELS.values() if _os_word(key) == item.lower()]
        if not matched:
            matched = [key for word, key in (("win", "windows-x64"), ("macos", "macos-arm64"),
                                             ("ubuntu", "ubuntu-x64")) if word == item.lower()]
        if not matched:
            raise PlanError(f"不认识的平台 {item!r}；合法取值：Win / MacOS / Ubuntu")
        platforms.extend(matched)
    return targets, platforms


def _os_word(platform: str) -> str:
    """平台键 → `/snapshot` 命令里的词（windows-x64→win、macos-arm64→macos、ubuntu-x64→ubuntu）。"""
    return {"windows-x64": "win", "macos-arm64": "macos", "ubuntu-x64": "ubuntu"}[platform]


def build_version(baseline: str, pr: int, now: datetime) -> str:
    """快照版本号 `<基线>.<PR 补零 5 位>.<MMddHHmm>`（北京时间，见 #165 §九）。"""
    beijing = now.astimezone(timezone(timedelta(hours=8)))
    return f"{baseline}.{pr:05d}.{beijing:%m%d%H%M}"


def build_legs(targets: list[str], platforms: list[str]) -> list[dict]:
    """JVM 线的构建腿：只出 JDK 25，只出被选中的平台（未选则两个平台都出）。"""
    labels = platforms or ["ubuntu-x64", "windows-x64"]
    legs: list[dict] = []
    for label in labels:
        for runner, leg_label, jdk, upload_cli in JVM_LEGS.get(label, []):
            legs.append({"os": runner, "label": leg_label, "jdk": jdk, "upload_cli": upload_cli})
    return legs


def asset_names(targets: list[str], version: str, platforms: list[str]) -> list[str]:
    """资产名列表（#165 §八）：`<前缀>-<版本>-<平台>-jdk25.zip`。"""
    labels = platforms or ["ubuntu-x64", "windows-x64"]
    names: list[str] = []
    for target in targets:
        fmt = ASSET_FORMATS.get(target)
        if not fmt:
            continue
        for label in labels:
            names.append(fmt.format(version=version, label=label))
    return names


def release_groups(targets: list[str], version: str, platforms: list[str], pr: int) -> list[dict]:
    """按目标拆出发布条目（每个目标一条 tag / 一个 pre-release）。"""
    groups: list[dict] = []
    for target in targets:
        groups.append({
            "target": target,
            "tag": TAG_FORMAT.format(version=version, target=target),
            "name": RELEASE_NAME_FORMAT.format(version=version, target=target),
            "assets": asset_names([target], version, platforms),
        })
    return groups


def decide(labels: list[str], changed: list[str], baseline: str, pr: int,
           now: datetime, groups: dict[str, list[str]], command: str = "",
           label_event: bool = False) -> dict:
    """核心判定：该不该出包、出哪些目标与平台、版本号与资产名。"""
    targets = [SNAPSHOT_LABELS[name] for name in labels if name in SNAPSHOT_LABELS]
    platforms = [OS_LABELS[name] for name in labels if name in OS_LABELS]
    explicit_command = bool(command.strip())

    if explicit_command:
        cmd_targets, cmd_platforms = parse_command(command)
        targets = list(dict.fromkeys(cmd_targets))
        platforms = list(dict.fromkeys(cmd_platforms or platforms))
        reason_prefix = "显式请求：`/snapshot` 命令"
    elif not targets:
        return {
            "owns_comment": True,
            "should_build": False, "pr": pr, "targets": [], "platforms": [], "releases": [],
            "version": build_version(baseline, pr, now), "legs": [], "matrix": [],
            "assets": [], "explicit": False,
            "reason": "未打任何 `SNAPSHOT:*` 触发标签（四类目标默认都不出包）",
        }
    elif label_event:
        # 打标签这一动作本身就是「显式请求」：只改文档也照出（#165 验收 16）
        reason_prefix = "显式请求：新打了触发标签"
    else:
        reason_prefix = ""

    needed, reason = classify(changed, groups)
    if not needed and not reason_prefix:
        return {
            "owns_comment": True, "should_build": False, "pr": pr, "targets": targets, "platforms": platforms, "releases": [],
            "version": build_version(baseline, pr, now), "legs": [], "matrix": [],
            "assets": [], "explicit": False, "reason": reason,
        }

    native = [t for t in targets if t in NATIVE_TARGETS]
    if native and not [t for t in targets if t in JVM_TARGETS]:
        # 本工作流只管 JVM 线；纯原生目标交给 DesktopNative.yml / CliNative.yml（S4）。
        # `owns_comment=False`：**别去动 PR 评论**——否则原生线的标签事件会把 JVM 线的
        # 快照评论覆盖成「未出包」（实测踩到：ACANX 给 PR 打了原生标签，JVM 评论被改写）。
        # 原生线将来用自己的标记维护自己的评论（见 S4）。
        return {
            "owns_comment": False,
            "should_build": False, "pr": pr, "targets": native, "platforms": platforms, "releases": [],
            "version": build_version(baseline, pr, now), "legs": [], "matrix": [],
            "assets": [], "explicit": explicit_command,
            "reason": f"目标 {native} 属原生线，由 DesktopNative.yml / CliNative.yml 处理（本工作流不构建）",
        }

    jvm = [t for t in targets if t in JVM_TARGETS]
    legs = build_legs(jvm, platforms)
    full_reason = reason if not reason_prefix else f"{reason_prefix} ⇒ 忽略变更分类（{reason}）"
    if not legs:
        return {
            "owns_comment": True, "should_build": False, "pr": pr, "targets": jvm, "platforms": platforms, "releases": [],
            "version": build_version(baseline, pr, now), "legs": [], "matrix": [],
            "assets": [], "explicit": explicit_command,
            "reason": f"选中的平台 {platforms} 在 JVM 线上没有对应的腿（JVM 线只有 ubuntu-x64 / windows-x64）",
        }
    version = build_version(baseline, pr, now)
    return {
        "owns_comment": True,
        "should_build": True, "pr": pr, "targets": jvm,
        "platforms": sorted({leg["label"] for leg in legs}),
        "version": version, "legs": [f"{leg['label']}/jdk{leg['jdk']}" for leg in legs],
        "matrix": legs, "assets": asset_names(jvm, version, platforms),
        "releases": release_groups(jvm, version, platforms, pr),
        "explicit": bool(reason_prefix) or explicit_command, "reason": full_reason,
    }


def notice_body(reason: str) -> str:
    """「未构建」类评论正文（用法错误 / 无权限 / 未打标签都走这里）。

    放在脚本里而不是 YAML 里，是为了让工作流里只出现**单行**调用：
    在 YAML 块标量里写多行字符串，行首缩进一旦小于块基准，块会被提前结束
    （本轮已因此把 SnapshotJVM.yml 写坏三次）。
    """
    return (
        "<!-- aha-snapshot -->\n"
        "### 快照包：未构建\n\n"
        f"- 原因：{reason}\n"
        "- 用法：`/snapshot <target> [os ...]`；目标取 `Desktop` / `Cli`，"
        "平台取 `Win` / `MacOS` / `Ubuntu`（可多个，缺省 = 该目标全部平台）。\n"
        "- 也可以直接给本 PR 打触发标签（`SNAPSHOT:Desktop` / `SNAPSHOT:Cli`），不需要命令。\n"
    )


def comment_body(plan: dict, run_url: str = "") -> str:
    """PR 汇总评论（幂等标记 + 版本 + 到期日 + 资产 + run 链接 + 取件命令，见 #165 §六）。"""
    if not plan["should_build"]:
        return notice_body(plan["reason"]).replace("未构建", "本次未出包")
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
    return (
        "<!-- aha-snapshot -->\n"
        f"### 快照包：`{plan['version']}`\n\n"
        f"- 目标：{', '.join(plan['targets'])}（平台：{', '.join(plan['platforms'])}）\n"
        f"- 判定：{plan['reason']}\n"
        f"\n{release_blocks}\n"
        f"- 有效期至：**{expiry}**（{SNAPSHOT_TTL_DAYS} 天；过期后仍可下载，只作提示）\n"
        f"{platform_note}"
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

#: 必须报错（不构建）的命令写法（验收 26）
SELF_TEST_BAD_COMMANDS = ["/snapshot desktopx", "/snapshot Desktop Win Bogus", "/snapshot", "/snapsho Desktop"]


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
    if problems:
        print(f"\n[问题] {problems} 条自检不符合预期")
        return 1
    print(f"\n✅ {len(SELF_TEST_CASES) + len(SELF_TEST_BAD_COMMANDS) + 1} 条自检全部符合预期")
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
    parser.add_argument("--self-test", action="store_true", help="自检判定边界")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()
    if args.notice is not None:
        print(notice_body(args.notice))
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
                      label_event=args.label_event)
    except PlanError as exc:
        print(f"[问题] {exc}", file=sys.stderr)
        return 1
    print(comment_body(plan, args.run_url) if args.comment else json.dumps(plan, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
