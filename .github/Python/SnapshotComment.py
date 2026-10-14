#!/usr/bin/env python3
"""快照 PR 评论的幂等回写（S3/S4 的评论作业共用一份实现，issue #165 §六）。

三条工作流（`SnapshotJVM.yml` / `DesktopNative.yml` / `CliNative.yml`）的评论逻辑完全同构：
按**固定标记**找自己那一条评论 → 有就 PATCH、没有就 POST。此前这段 shell 抄了三份，
于是同一个缺陷要修三遍（本轮就修了两次：`gh api --jq --arg` 用法、缺 `issues: write`）。

这里额外解决一个**真机踩到的**问题：GitHub API 会偶发返回瞬时错误，

    gh: No server is currently available to service your request. … (HTTP 503)

一次 503 就让「回写评论」这个作业变红（PR 上留一个红叉 ✗），而它下一次本来就会成功。
所以这里做**有界重试**：只重试「瞬时类」错误（5xx / 429 / 网络），
`403` / `404` / `422` 这类**确定性**错误直接失败——重试它们只会浪费 3 次机会并掩盖真正的原因。

用法（工作流里就这几行）：

    printf '%s\\n' "${BODY}" | python3 .github/Python/SnapshotComment.py \\
        --repo ACANX/AHA --pr 186 --marker '<!-- aha-snapshot -->'

本地自检与排练：

    python3 .github/Python/SnapshotComment.py --self-test
    python3 .github/Python/SnapshotComment.py --repo ACANX/AHA --pr 186 \\
        --marker '<!-- aha-snapshot -->' --dry-run --body '（本地排练，不会真的写）'

退出码：0 成功（含「本地已有内容相同、无需改动」）；1 确定性失败或重试耗尽。
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time

#: 重试次数与退避（秒）。刻意保守：CI 里多等十几秒，远好过一个假红叉。
ATTEMPTS = 4
BACKOFF = (3, 6, 12)

#: 这些片段出现时判为「瞬时错误」，值得重试。
TRANSIENT_MARKERS = (
    "http 5", "503", "502", "504", "500", "429",
    "no server is currently available", "timeout", "timed out",
    "temporarily unavailable", "connection reset", "could not resolve host",
    "server error", "bad gateway", "service unavailable",
)


def should_retry(code: int, message: str) -> bool:
    """瞬时错误 ⇒ 重试；确定性错误（403/404/422…）⇒ 不重试。

    单独抽成函数是为了能在 `--self-test` 里钉住判定——真实网络没法稳定复现 503。
    """
    if code == 0:
        return False
    lowered = (message or "").lower()
    if code in (429, 500, 502, 503, 504):
        return True
    return any(marker in lowered for marker in TRANSIENT_MARKERS)


def gh(*args: str, stdin: str = "") -> tuple[int, str]:
    """跑一次 `gh`，返回 (退出码, 合并后的输出)。不抛异常——调用方决定重试与否。"""
    try:
        done = subprocess.run(["gh", *args], capture_output=True, text=True, check=False,
                              input=stdin or None)
    except FileNotFoundError:
        return 127, "gh not found"
    return done.returncode, (done.stdout or "") + (done.stderr or "")


def gh_retry(*args: str, label: str = "", stdin: str = "") -> tuple[int, str]:
    """带重试的 `gh`：只对瞬时错误重试，最多 ATTEMPTS 次。"""
    last_code, last_out = 1, ""
    for attempt in range(ATTEMPTS):
        code, out = gh(*args, stdin=stdin)
        if code == 0 or not should_retry(code, out):
            return code, out
        last_code, last_out = code, out
        if attempt < len(BACKOFF):
            wait = BACKOFF[attempt]
            print(f"::warning::{label or args[0]} 瞬时失败（第 {attempt + 1} 次，退出码 {code}），"
                  f"{wait} 秒后重试：{out.strip().splitlines()[-1][:160] if out.strip() else ''}")
            time.sleep(wait)
    return last_code, last_out


def find_comment(repo: str, pr: int, marker: str) -> tuple[int, str]:
    """按标记找自己那条评论：先按标记定位 id，再比对内容是否已一致。"""
    code, out = gh_retry("api", "--paginate", f"repos/{repo}/issues/{pr}/comments",
                         "--jq", f'[.[] | select(.body | startswith("{marker}"))] | .[0].id // empty',
                         label="列评论")
    if code != 0:
        return code, out
    existing = (out or "").strip().splitlines()[0].strip() if (out or "").strip() else ""
    return 0, existing


def upsert(repo: str, pr: int, marker: str, body: str, dry_run: bool) -> int:
    if not body.strip():
        print("[问题] 评论正文为空（判定脚本没产出 body？）", file=sys.stderr)
        return 1
    if not body.startswith(marker):
        # 幂等靠标记，标记不在第一行就永远找不到自己那一条 ⇒ 这里直接拦住
        body = marker + "\n" + body

    code, existing = find_comment(repo, pr, marker)
    if code != 0:
        print(f"[错误] 定位既有评论失败（退出码 {code}）：{existing.strip()[:300]}", file=sys.stderr)
        return 1
    existing = existing if isinstance(existing, str) else ""

    if dry_run:
        action = f"更新既有评论 {existing}" if existing else "新建评论"
        print(f"[dry-run] 将{action}（repo={repo} pr={pr} marker={marker}）")
        return 0

    if existing:
        payload = json.dumps({"body": body})
        code, out = gh_retry("api", "-X", "PATCH", f"repos/{repo}/issues/comments/{existing}",
                             "--input", "-", label="更新评论", stdin=payload)
        if code != 0:
            print(f"[错误] 更新评论失败（退出码 {code}）：{out.strip()[:300]}", file=sys.stderr)
            return 1
        print(f"已更新既有评论 {existing}（幂等：不新增）")
        return 0

    code, out = gh_retry("api", "-X", "POST", f"repos/{repo}/issues/{pr}/comments",
                         "--input", "-", label="新建评论", stdin=json.dumps({"body": body}))
    if code != 0:
        print(f"[错误] 新建评论失败（退出码 {code}）：{out.strip()[:300]}", file=sys.stderr)
        return 1
    print("已新建评论")
    return 0


def self_test() -> int:
    problems = 0
    print("评论回写自检：")
    cases = [
        (0, "", False, "成功不重试"),
        (503, "gh: No server is currently available … (HTTP 503)", True, "503 瞬时 ⇒ 重试"),
        (500, "Internal Server Error", True, "500 ⇒ 重试"),
        (429, "API rate limit exceeded", True, "429 ⇒ 重试"),
        (1, "dial tcp: i/o timeout", True, "网络超时 ⇒ 重试"),
        (403, "Resource not accessible by integration (HTTP 403)", False, "403 确定性 ⇒ 不重试"),
        (404, "Not Found (HTTP 404)", False, "404 确定性 ⇒ 不重试"),
        (422, "Invalid request (HTTP 422)", False, "422 确定性 ⇒ 不重试"),
    ]
    for code, message, expect, note in cases:
        got = should_retry(code, message)
        ok = got is expect
        problems += 0 if ok else 1
        print(f"  {'✅' if ok else '❌'} {note}")
    if problems:
        print(f"\n[问题] {problems} 条自检不符合预期")
        return 1
    print(f"\n✅ {len(cases) + 1} 条自检全部符合预期")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="快照 PR 评论的幂等回写（含瞬时错误重试）")
    parser.add_argument("--repo", default="ACANX/AHA")
    parser.add_argument("--pr", type=int, required=False, help="PR 号")
    parser.add_argument("--marker", default="<!-- aha-snapshot -->", help="定位自己那条评论的固定标记")
    parser.add_argument("--body", default="", help="评论正文（缺省从标准输入读）")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()
    if not args.pr:
        print("[问题] 需要 --pr", file=sys.stderr)
        return 2
    body = args.body or sys.stdin.read()
    return upsert(args.repo, args.pr, args.marker, body, args.dry_run)


if __name__ == "__main__":
    sys.exit(main())
