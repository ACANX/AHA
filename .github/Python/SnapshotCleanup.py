#!/usr/bin/env python3
"""快照清理（S6 / #174）——「PR 关闭就回收、7 天过期、每目标只留最近 3 条」的全部判定与执行。

对应 #165 方案 §十「快照包生命周期与自动清理」。三个触发面共用这一份逻辑：

| 触发 | 传入 | 作用 |
|---|---|---|
| `pull_request: closed` | `--pr <N>` | 回收**这个** PR 的快照（合并关闭与直接关闭都覆盖） |
| 每日 `schedule` | 无 | 兜底清扫：补删「关闭事件」漏掉的残留（含 PR 已不存在的孤儿） |
| `workflow_dispatch` | 可选 `--pr` | 手动补跑 |

## 安全阀（最重要的一条）

清理是**不可逆**的删除动作，所以只处理**严格匹配**下面这个形状的 tag：

    ^V<基线 3 段>.<PR 补零 5 位>.<MMddHHmm>-AHA-<目标>-SNAPSHOT$      （目标 ∈ Desktop/Cli/Desktop-Native/Cli-Native；兼容旧拼写 NativeDesktop/NativeCli）

即：**必须**是 `V` 开头 + 5 段版本（末段 8 位北京时间戳）+ `-AHA-<目标>-SNAPSHOT` 后缀。
因此下面这些**永远不会**被碰到（`--self-test` 里有正反例）：

* 正式发版：`V0.1.3`、`V0.1.3.00165`…
* dev 线预发行：`V0.1.3.00180-aha-cli-native`、`V0.1.3.0021-aha-desktop-native`、`V0.1.3.00179-aha-jvm`
* 任何手工造的 tag（如验收 24 的 `V0.1.3.00165-test`）

## 判定规则（同一个 PR + 同一个目标算一组）

1. **PR 已关闭 / 已合并 / 不存在** ⇒ 删整条快照发布页（release + tag）；
2. 否则 **超过 7 天** ⇒ 删（有效期到了，取件脚本也会提示「已过期」）；
3. 否则同组里**只保留最近 3 条**（按版本时间戳新→旧），更早的删。

> 为什么「3 条」是**按发布页**而不是按资产：每次出快照都会新建一条发布页
> （tag 里带北京时间戳，天然唯一），同一条发布页里的资产属于**同一次**出包（镜像包 + 报告包）。
> 所以「保留最近 3 个」落到发布页粒度上才是「保留最近 3 次出包」——这正是取件场景想要的。

## 用法

    python3 .github/Python/SnapshotCleanup.py --self-test                # 判定边界自检（不起网络）
    python3 .github/Python/SnapshotCleanup.py --dry-run                  # 只看会删什么（本地可跑）
    python3 .github/Python/SnapshotCleanup.py --pr 165                   # 只清扫某个 PR
    python3 .github/Python/SnapshotCleanup.py                            # 真删（CI 里用）

退出码：**恒为 0**（单条删除失败只记 `::warning::`，留给下一次清扫补删——见 #165 §十「异步语义」）。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

#: 安全阀：只有这个形状的 tag 才会被清理（见模块 docstring）。
SAFETY_RE = re.compile(
    r"^V(?P<version>\d+\.\d+\.\d+\.(?P<pr>\d{5})\.(?P<stamp>\d{8}))"
    # 长拼写写在前面，避免 `Desktop` 抢在 `Desktop-Native` 之前（正则回溯能兜住，但顺序更清楚）。
    r"-AHA-(?P<target>Desktop-Native|Cli-Native|NativeDesktop|NativeCli|Desktop|Cli)-SNAPSHOT$"
)

#: 同一 PR + 同一目标最多保留几条（#165 §十）。
KEEP_PER_TARGET = 3
#: 有效期（天）。与 `.github/Python/SnapshotPlan.py` 的 `SNAPSHOT_TTL_DAYS` 一致。
TTL_DAYS = 7


def parse_tag(tag: str) -> dict | None:
    """严格解析快照 tag；不匹配（含任何非快照 tag）返回 None。"""
    match = SAFETY_RE.match(tag or "")
    if not match:
        return None
    return {
        "tag": tag,
        "version": match.group("version"),
        "pr": int(match.group("pr")),
        "stamp": match.group("stamp"),
        "target": match.group("target"),
    }


def classify(releases: list[dict], pr_states: dict[int, str], now: datetime,
             only_pr: int = 0) -> list[dict]:
    """纯判定：给定 release 列表与「PR → 状态」，返回每条快照「删 / 留」的决定。

    `pr_states` 的取值：`open` / `closed` / `merged` / `missing`（查不到 = 不存在）。
    参数里没有任何网络调用——自检与 `--dry-run` 都靠它。
    """
    parsed: list[dict] = []
    for release in releases:
        info = parse_tag(str(release.get("tag_name", "")))
        if not info:
            continue
        if only_pr and info["pr"] != only_pr:
            continue
        created = release.get("created_at") or release.get("published_at") or ""
        info["created_at"] = created
        info["assets"] = [a.get("name", "") for a in (release.get("assets") or [])]
        info["draft"] = bool(release.get("draft"))
        info["state"] = pr_states.get(info["pr"], "missing")
        parsed.append(info)

    # 同组（PR + 目标）内按「版本时间戳 → 创建时间」新到旧排序，组内序号决定保留还是删除。
    parsed.sort(key=lambda item: (item["pr"], item["target"], item["stamp"], item["created_at"]),
                reverse=True)
    seen: dict[tuple[int, str], int] = {}
    decisions: list[dict] = []
    for info in parsed:
        key = (info["pr"], info["target"])
        seen[key] = seen.get(key, 0)
        index = seen[key]
        seen[key] = index + 1

        age_days = None
        if info["created_at"]:
            try:
                created = datetime.fromisoformat(info["created_at"].replace("Z", "+00:00"))
                age_days = (now - created).total_seconds() / 86400
            except ValueError:
                age_days = None

        if info["draft"]:
            action, reason = "keep", "草稿（不处理）"
        elif info["state"] == "missing":
            action, reason = "delete", "PR 不存在（孤儿快照）"
        elif info["state"] == "closed":
            action, reason = "delete", f"PR #{info['pr']} 已关闭"
        elif info["state"] == "merged":
            action, reason = "delete", f"PR #{info['pr']} 已合并"
        elif age_days is not None and age_days > TTL_DAYS:
            action, reason = "delete", f"超过有效期（{age_days:.1f} 天 > {TTL_DAYS} 天）"
        elif index >= KEEP_PER_TARGET:
            action, reason = "delete", f"同目标只保留最近 {KEEP_PER_TARGET} 条（本条是第 {index + 1} 新）"
        else:
            action, reason = "keep", f"保留（第 {index + 1} 新，未过期，PR 仍在）"
        decisions.append({
            "tag": info["tag"], "pr": info["pr"], "target": info["target"],
            "version": info["version"], "state": info["state"],
            "age_days": None if age_days is None else round(age_days, 1),
            "action": action, "reason": reason, "assets": info["assets"],
        })
    return decisions


# ── gh 调用（只有 sweep 走网络；自检与 --dry-run 也只用读接口）────────────────────
#: 瞬时错误的重试（与 `.github/Python/SnapshotComment.py` 同一判定；那里有 --self-test 钉住）
TRANSIENT_MARKERS = ("http 5", "503", "502", "504", "500", "429",
                     "no server is currently available", "timeout", "timed out",
                     "temporarily unavailable", "connection reset", "server error")
ATTEMPTS = 3
BACKOFF = (3, 6)


def gh(*args: str) -> tuple[int, str]:
    """跑一次 `gh`（瞬时 5xx / 429 有界重试），返回 (退出码, 标准输出)。不抛异常。"""
    code, out = 1, ""
    for attempt in range(ATTEMPTS):
        try:
            done = subprocess.run(["gh", *args], capture_output=True, text=True, check=False)
        except FileNotFoundError:
            return 127, ""
        code, out = done.returncode, done.stdout
        if code == 0:
            return code, out
        low = ((done.stdout or "") + (done.stderr or "")).lower()
        transient = code in (429, 500, 502, 503, 504) or any(m in low for m in TRANSIENT_MARKERS)
        if not transient or attempt >= len(BACKOFF):
            return code, out
        wait = BACKOFF[attempt]
        print(f"::warning::{args[0]} 瞬时失败（第 {attempt + 1} 次，退出码 {code}），{wait} 秒后重试")
        time.sleep(wait)
    return code, out


def list_releases(repo: str) -> tuple[list[dict], list[str]]:
    problems: list[str] = []
    code, out = gh("api", "--paginate", f"repos/{repo}/releases?per_page=100",
                   "--jq", "[.[] | {tag_name, created_at, published_at, draft, assets: [.assets[] | {name, created_at}]}]")
    if code != 0 or not out.strip():
        problems.append(f"列不出发布页（gh 退出码 {code}）")
        return [], problems
    try:
        # --paginate 会把多页拼成多个 JSON 数组，这里逐个解析再合并
        releases: list[dict] = []
        for chunk in out.replace("]\n[", "]\n[").split("\n"):
            chunk = chunk.strip()
            if not chunk:
                continue
            releases.extend(json.loads(chunk))
        return releases, problems
    except json.JSONDecodeError as exc:
        problems.append(f"发布页 JSON 解析失败：{exc}")
        return [], problems


def pr_state(repo: str, number: int) -> str:
    """查 PR 状态：open / closed / merged；查不到（404）返回 missing。"""
    code, out = gh("api", f"repos/{repo}/pulls/{number}", "--jq", "[.state, (.merged_at // \"\")] | @tsv")
    if code != 0 or not out.strip():
        return "missing"
    parts = out.strip().split("\t")
    state = parts[0] if parts else ""
    if state == "open":
        return "open"
    if len(parts) > 1 and parts[1]:
        return "merged"
    return "closed"


def sweep(repo: str, only_pr: int, dry_run: bool, now: datetime) -> int:
    releases, problems = list_releases(repo)
    for problem in problems:
        print(f"::warning::{problem}")
    if not releases:
        print("没有取到发布页（或全部为空），本轮无可处理项")
        return 0

    # 只对**通过安全阀**的快照去查 PR 状态：非快照 release 连查都不查（防止误碰）
    numbers = sorted({info["pr"] for info in (parse_tag(str(r.get("tag_name", ""))) for r in releases) if info})
    states = {number: pr_state(repo, number) for number in numbers if not only_pr or number == only_pr}
    if only_pr:
        states.setdefault(only_pr, pr_state(repo, only_pr))

    decisions = classify(releases, states, now, only_pr=only_pr)
    if not decisions:
        print("没有匹配安全阀的快照 tag，本轮无可处理项（正式发版 / dev 线预发行都不会被碰）")
        return 0

    killed: list[dict] = []
    for item in decisions:
        line = f"{item['action']:6} {item['tag']}  ← {item['reason']}"
        if item["action"] == "keep":
            print(line)
            continue
        if dry_run:
            print(f"[dry-run] 将删除 {item['tag']}  ← {item['reason']}")
            continue
        code, out = gh("release", "delete", item["tag"], "--repo", repo, "--yes", "--cleanup-tag")
        if code == 0:
            print(f"已删除 {item['tag']}  ← {item['reason']}")
            killed.append(item)
        else:
            # 幂等：本来就不存在（404）也算成功；其它错误只记 warning，留给下次清扫补删
            print(f"::warning::删除 {item['tag']} 失败（gh 退出码 {code}）：{out.strip()[:200]}")
            if "not found" in out.lower() or "404" in out:
                print(f"（该发布页已不存在，视为幂等成功）{item['tag']}")
                killed.append(item)

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write("### 快照清扫\n\n")
            handle.write(f"- 仓库：`{repo}`；模式：{'dry-run' if dry_run else '真删'}"
                         f"{f'；只处理 PR #{only_pr}' if only_pr else ''}\n")
            planned = len([d for d in decisions if d["action"] == "delete"])
            # 不写成「相邻 f-string 拼接 + 内部再套 f-string」：本轮在 Python 3.14 上真踩到
            # SyntaxError: unterminated f-string literal。先算好再拼，最稳。
            tail = "（dry-run 未执行）" if dry_run else f"实际删除 {len(killed)} 条"
            handle.write(f"- 扫到快照发布页：{len(decisions)} 条；删除 {planned} 条；{tail}\n\n")
            handle.write("| tag | PR | 目标 | PR 状态 | 判定 | 原因 |\n|---|---|---|---|---|---|\n")
            for item in decisions:
                handle.write(f"| `{item['tag']}` | #{item['pr']} | {item['target']} | "
                             f"{item['state']} | {item['action']} | {item['reason']} |\n")
    print(f"\n合计：扫到 {len(decisions)} 条，删 {len([d for d in decisions if d['action'] == 'delete'])} 条"
          f"（{'dry-run' if dry_run else '实际删除 ' + str(len(killed)) + ' 条'}）")
    return 0


# ── 自检 ───────────────────────────────────────────────────────────────────────
SELF_TEST_TAGS_KEEP = [
    # 正式发版与 dev 线预发行：**永远不能**被安全阀放行（验收 24 的核心）
    "V0.1.3", "V0.1.3.00165", "V0.1.3.00165-test", "V0.1.3.00183",
    "V0.1.3.00180-aha-cli-native", "V0.1.3.0021-aha-desktop-native", "V0.1.3.00179-aha-jvm",
    "v0.1.3.00165", "snapshot-pr165", "V0.1.3.165.10141530-AHA-Desktop-SNAPSHOT",   # PR 段不足 5 位
    "V0.1.3.00165.1014153-AHA-Desktop-SNAPSHOT",                                   # 时间戳 7 位
    "V0.1.3.00165.10141530-AHA-Snapshot-SNAPSHOT",                                 # 目标名不对
    "V0.1.3.00165.10141530-AHA-Desktop-SNAPSHOTX",                                 # 后缀带尾巴
]
SELF_TEST_TAGS_HIT = [
    "V0.1.3.00165.10141530-AHA-Desktop-SNAPSHOT",
    "V0.1.3.00165.10141530-AHA-Cli-SNAPSHOT",
    "V0.1.3.00184.10141859-AHA-Desktop-Native-SNAPSHOT",
    "V0.1.3.00184.10141859-AHA-Cli-Native-SNAPSHOT",
    # 旧拼写的 tag 也要能被回收（否则改名期间产生的会变成永远清不掉的孤儿）
    "V0.1.3.00183.10141829-AHA-NativeDesktop-SNAPSHOT",
]


def self_test() -> int:
    problems = 0
    print("快照清理自检：")
    for tag in SELF_TEST_TAGS_KEEP:
        ok = parse_tag(tag) is None
        problems += 0 if ok else 1
        print(f"  {'✅' if ok else '❌'} 安全阀拒绝 {tag!r}")
    for tag in SELF_TEST_TAGS_HIT:
        ok = parse_tag(tag) is not None
        problems += 0 if ok else 1
        print(f"  {'✅' if ok else '❌'} 安全阀放行 {tag!r}")

    now = datetime(2026, 10, 14, 12, 0, tzinfo=timezone.utc)

    def release(version: str, target: str = "Desktop", created: str = "2026-10-14T10:00:00Z",
                pr: int = 165) -> dict:
        return {"tag_name": f"V{version}-AHA-{target}-SNAPSHOT", "created_at": created,
                "published_at": created, "draft": False, "assets": [{"name": "a.zip"}]}

    # ① PR 已关闭 ⇒ 删（验收 23）
    items = classify([release("0.1.3.00165.10141530")], {165: "closed"}, now)
    ok = len(items) == 1 and items[0]["action"] == "delete" and "已关闭" in items[0]["reason"]
    problems += 0 if ok else 1
    print(f"  {'✅' if ok else '❌'} PR 已关闭 ⇒ 删（验收 23）")

    # ② PR 不存在 ⇒ 删（孤儿）
    items = classify([release("0.1.3.00165.10141530")], {}, now)
    ok = items[0]["action"] == "delete" and "孤儿" in items[0]["reason"]
    problems += 0 if ok else 1
    print(f"  {'✅' if ok else '❌'} PR 不存在 ⇒ 删（孤儿快照）")

    # ③ PR 开着且未过期 ⇒ 保留
    items = classify([release("0.1.3.00165.10141530")], {165: "open"}, now)
    ok = items[0]["action"] == "keep"
    problems += 0 if ok else 1
    print(f"  {'✅' if ok else '❌'} PR 开着且未过期 ⇒ 保留")

    # ④ 同 PR 同目标 4 条 ⇒ 只留最近 3 条（验收 25）
    many = [release(f"0.1.3.00165.{stamp}") for stamp in
            ("10141000", "10141100", "10141200", "10141300")]
    items = classify(many, {165: "open"}, now)
    deletes = [i["tag"] for i in items if i["action"] == "delete"]
    keeps = [i["tag"] for i in items if i["action"] == "keep"]
    ok = len(keeps) == 3 and deletes == ["V0.1.3.00165.10141000-AHA-Desktop-SNAPSHOT"]
    problems += 0 if ok else 1
    print(f"  {'✅' if ok else '❌'} 同组 4 条 ⇒ 只留最近 3 条、删最旧那条（验收 25）")

    # ⑤ 超过 7 天 ⇒ 删（即便 PR 还开着）
    old = release("0.1.3.00165.10140100", created="2026-10-05T10:00:00Z")
    items = classify([old], {165: "open"}, now)
    ok = items[0]["action"] == "delete" and "有效期" in items[0]["reason"]
    problems += 0 if ok else 1
    print(f"  {'✅' if ok else '❌'} 超过 7 天 ⇒ 删（验收 3 / 25）")

    # ⑥ 不同目标各自成组：互不影响保留名额
    mixed = [release("0.1.3.00165.10141000"), release("0.1.3.00165.10140900"),
             release("0.1.3.00165.10141000", target="Cli"), release("0.1.3.00165.10140900", target="Cli"),
             release("0.1.3.00165.10141000", target="Cli-Native")]
    items = classify(mixed, {165: "open"}, now)
    summary = {(i["tag"], i["action"]) for i in items}
    ok = len([i for i in items if i["action"] == "keep"]) == 5
    problems += 0 if ok else 1
    print(f"  {'✅' if ok else '❌'} 不同目标分别计数（Desktop/Cli/Cli-Native 各留各的）")

    # ⑦ `--pr` 过滤：只处理指定 PR
    items = classify([release("0.1.3.00165.10141000"), release("0.1.3.00166.10141000", pr=166)],
                     {165: "open", 166: "open"}, now, only_pr=166)
    ok = len(items) == 1 and items[0]["pr"] == 166
    problems += 0 if ok else 1
    print(f"  {'✅' if ok else '❌'} `--pr` 只处理指定 PR")

    # ⑧ 非快照 tag 混进来：连查都不该查（classify 直接忽略）
    items = classify([{"tag_name": "V0.1.3.00183", "created_at": "2026-10-14T10:00:00Z",
                       "assets": []}, release("0.1.3.00165.10141000")], {165: "open"}, now)
    ok = len(items) == 1
    problems += 0 if ok else 1
    print(f"  {'✅' if ok else '❌'} 非快照 tag 被安全阀挡在外面（验收 24）")

    if problems:
        print(f"\n[问题] {problems} 条自检不符合预期")
        return 1
    print(f"\n✅ {len(SELF_TEST_TAGS_KEEP) + len(SELF_TEST_TAGS_HIT) + 8} 条自检全部符合预期")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="快照清理（S6 / #174）")
    parser.add_argument("--repo", default="ACANX/AHA")
    parser.add_argument("--pr", type=int, default=0, help="只清理这个 PR 的快照（关闭事件用）")
    parser.add_argument("--dry-run", action="store_true", help="只打印会删什么，不真删")
    parser.add_argument("--now", help="当前时间（ISO8601；默认取系统时间，仅便于自检）")
    parser.add_argument("--self-test", action="store_true", help="判定边界自检（不起网络）")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()
    now = datetime.fromisoformat(args.now) if args.now else datetime.now(timezone.utc)
    return sweep(args.repo, args.pr, args.dry_run, now)


if __name__ == "__main__":
    sys.exit(main())
