#!/usr/bin/env python3
"""AHA 部署前验证（非阻塞质量验证工作流，issue #166）。

把「**部署前应当自测、但还不宜直接进合并卡点**」的通用验证 / 断言集中到一处，
由 `.github/workflows/PreDeployVerify.yml` 在**每次 PR 推送后自动执行**，也可手动触发。

口径（与 issue #166 校准后的需求一致）：

  1. **自动可跑、0 token**：所有验证项只跑本地进程，不做任何模型往返
     （需要 provider 请求的动作不属本组）；
  2. **结论可复核**：每项的 id / 名称 / 结论 / 关键读数都进 Markdown 报告，
     由工作流转成 commit status 与 PR 留痕评论；
  3. **失败可见、不静默**：任何一项失败 ⇒ 退出码非 0（工作流据此打 `failure` 并留痕）；
  4. **非阻塞**：「卡点」是把工作流产出的 status 加进 required checks 这一步**显式动作**，
     本脚本与工作流都不碰分支保护（见 `ReleaseProcess.md` §2.3）；
  5. **可增删**：验证项就是下面的 `CHECKS` —— 加一项 = 加一个函数。

验证项（V 组，全部 0 token；跑在**真实构建产物**上，不是源码）：

  | id | 验什么 | 为什么值得在部署前验一遍 |
  |---|---|---|
  | V1 | 产物能起、`version` 打得开 | 包不可运行的回归（入口类 / 资源缺失 / 打包形态） |
  | V2 | `--help` 列得出全部子命令 | picocli 命令面回归；`--help` 曾因缺 JDK 文本断行资源而崩（issue #117） |
  | V3 | `tool list` 列得出工具 | 工具注册（内置工具集）回归 |
  | V4 | `tool invoke file-read` **真的读到文件** | 工具**真的能执行**：参数解析 → 权限判定 → 执行 → 输出 |
  | V5 | `provider list` / `model show` 打得开 | 配置读取面（`Model.yml` 解析 / 内置预设）回归 |
  | V6 | 全程未出现 `UnsatisfiedLinkError` | 原生库（sqlite 等）加载回归（issue #229 一族） |

用法：

    python3 bin/PreDeployVerify.py --jar Dist/aha-cli.jar            # 跑全部（CI 用）
    python3 bin/PreDeployVerify.py --jar … --only V4 --verbose        # 只跑一项并打印原始输出
    python3 bin/PreDeployVerify.py --list                            # 列出验证项
    python3 bin/PreDeployVerify.py --self-test                       # 只跑本脚本自带断言（不需要 jar）

退出码：0 = 全部通过；1 = 有未过项（或环境不满足）。工作流据此打 status。
"""

from __future__ import annotations

import argparse
import dataclasses
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Callable

#: 留痕评论的固定标记（工作流与 `bin/CheckScripts.py` 的守卫都认这个字面量）
MARKER = "<!-- predeploy-verify -->"

#: 单条命令的超时（秒）。CLI 的本地命令都在秒级，超过就是挂住了。
TIMEOUT = 180

#: ANSI 转义序列：CLI 在非 TTY 下本就不上色，但日志里偶尔会混进，比对前先剥掉。
ANSI_RE = re.compile(r"\x1b\[[0-9;?]*[ -/]*[@-~]")

#: `--help` 里应当出现的全部子命令名（与 `AhaCli` 的 `subcommands` 对齐）。
#: 少一个就说明「命令面被改小 / 渲染不出」——这正是 #117 那类问题的形态。
SUBCOMMANDS = (
    "init", "chat", "run", "tool", "provider", "model", "extension", "config", "secret", "version",
)

#: V4 的探针内容：写进临时文件，再由 `tool invoke file-read` 读回来逐字比对。
FILE_READ_PROBE = "AHA predeploy probe"

#: 原生库缺失的报错串（V6）。
NATIVE_ERROR = "UnsatisfiedLinkError"


def strip_ansi(text: str) -> str:
    """剥掉 ANSI 转义序列（比对与展示前统一处理）。"""
    return ANSI_RE.sub("", text or "")


def missing_items(text: str, needles: tuple[str, ...]) -> list[str]:
    """返回 `needles` 里**没有**出现在 `text` 中的项（顺序保持）。"""
    return [item for item in needles if item not in text]


@dataclasses.dataclass
class Ctx:
    """一次验证运行的上下文。"""

    jar: Path
    home: Path
    verbose: bool = False
    #: 已跑过的命令：(check_id, command, rc, output)
    runs: list[tuple[str, str, int, str]] = dataclasses.field(default_factory=list)


def run_cli(ctx: Ctx, check_id: str, args: list[str]) -> tuple[int, str]:
    """跑一条 CLI 命令，返回 `(退出码, 输出)`；超时按 124 处理（与 `timeout(1)` 同号）。"""
    cmd = ["java", "-jar", str(ctx.jar), *args]
    env = dict(os.environ)
    # 隔离配置目录：验证不读也不写运行者的 `~/.aha`（工作流也会设，这里兜底）
    env["AHA_HOME"] = str(ctx.home)
    display = " ".join(cmd)
    if ctx.verbose:
        print(f"  $ {display}")
    try:
        proc = subprocess.run(
            cmd,
            env=env,
            # stdin 接 /dev/null：任何交互式提示都会立刻拿到 EOF，不会把 CI 挂住
            stdin=subprocess.DEVNULL,
            capture_output=True,
            text=True,
            timeout=TIMEOUT,
        )
        rc, out = proc.returncode, strip_ansi(proc.stdout + proc.stderr)
    except subprocess.TimeoutExpired:
        rc, out = 124, f"命令超时（>{TIMEOUT}s）"
    except OSError as exc:                       # java 不存在、jar 不可读……
        rc, out = 127, f"无法执行：{exc}"
    ctx.runs.append((check_id, display, rc, out))
    if ctx.verbose:
        print(out.rstrip())
    return rc, out


# ── 验证项 ──────────────────────────────────────────────────────────────────


def check_version(ctx: Ctx) -> tuple[bool, str]:
    """V1：产物能起，且 `version` 打得开。"""
    rc, out = run_cli(ctx, "V1", ["version"])
    if rc != 0:
        first = out.strip().splitlines()[:1]
        return False, f"`version` 退出码 {rc}：{first[0] if first else '（无输出）'}"
    found = re.search(r"\d+\.\d+\.\d+", out)
    if not found:
        return False, "`version` 输出里找不到版本号"
    return True, f"`version` 退出码 0，版本读数 `{found.group(0)}`"


def check_help_surface(ctx: Ctx) -> tuple[bool, str]:
    """V2：`--help` 打得开，且列得出全部子命令。"""
    rc, out = run_cli(ctx, "V2", ["--help"])
    if rc != 0:
        return False, f"`--help` 退出码 {rc}（缺资源 / 渲染失败都长这样）"
    absent = missing_items(out, SUBCOMMANDS)
    if absent:
        return False, f"`--help` 里缺子命令：{', '.join(absent)}"
    return True, f"`--help` 退出码 0，{len(SUBCOMMANDS)} 个子命令齐全"


def check_tool_registry(ctx: Ctx) -> tuple[bool, str]:
    """V3：工具注册表打得开，且内置工具在。"""
    rc, out = run_cli(ctx, "V3", ["tool", "list"])
    if rc != 0:
        return False, f"`tool list` 退出码 {rc}"
    # 只点名**读**类工具：它是下面 V4 的靶子，也是工具集里最稳定存在的一个
    if "file-read" not in out:
        return False, "`tool list` 里没有 `file-read`（工具注册可能断了）"
    listed = len(re.findall(r"^\s*\S", out))
    return True, f"`tool list` 退出码 0，含 `file-read`（输出 {listed} 行）"


def check_tool_invoke(ctx: Ctx) -> tuple[bool, str]:
    """V4：工具**真的能执行** —— 写探针 → `tool invoke file-read` → 逐字比对。"""
    probe = ctx.home / "predeploy-probe.txt"
    probe.write_text(f"{FILE_READ_PROBE}\n", encoding="utf-8")
    rc, out = run_cli(ctx, "V4", ["tool", "invoke", "file-read", "-p", f"path={probe}"])
    if rc != 0:
        return False, f"`tool invoke file-read` 退出码 {rc}"
    if FILE_READ_PROBE not in out:
        return False, "工具跑完了，但没有读回探针内容（参数 / 输出链路有问题）"
    return True, "`tool invoke file-read` 真的读回了探针文件内容"


def check_config_surface(ctx: Ctx) -> tuple[bool, str]:
    """V5：配置读取面（供应商 / 模型档位）打得开。"""
    rc_list, out_list = run_cli(ctx, "V5", ["provider", "list"])
    if rc_list != 0:
        return False, f"`provider list` 退出码 {rc_list}"
    rc_model, out_model = run_cli(ctx, "V5", ["model", "show"])
    if rc_model != 0:
        return False, f"`model show` 退出码 {rc_model}"
    if not out_list.strip() or not out_model.strip():
        return False, "`provider list` / `model show` 输出为空"
    return True, "`provider list` 与 `model show` 都打得开且有内容"


def check_no_native_errors(ctx: Ctx) -> tuple[bool, str]:
    """V6：全程没有 `UnsatisfiedLinkError`（原生库加载回归）。"""
    hit = [cid for cid, _cmd, _rc, out in ctx.runs if NATIVE_ERROR in out]
    if hit:
        return False, f"{NATIVE_ERROR}：出现在 {', '.join(sorted(set(hit)))}"
    return True, f"{len(ctx.runs)} 条命令的输出里都没有 {NATIVE_ERROR}"


#: 验证项顺序 = 执行顺序（V6 依赖前面各项的累计输出，必须最后跑）。
CHECKS: tuple[tuple[str, str, Callable[[Ctx], tuple[bool, str]]], ...] = (
    ("V1", "启动与版本（产物能起、`version` 打得开）", check_version),
    ("V2", "帮助面（`--help` 列得出全部子命令）", check_help_surface),
    ("V3", "工具注册（`tool list` 含内置工具）", check_tool_registry),
    ("V4", "工具直调（`tool invoke file-read` 真的读到文件）", check_tool_invoke),
    ("V5", "配置面（`provider list` / `model show`）", check_config_surface),
    ("V6", "原生库（全程无 `UnsatisfiedLinkError`）", check_no_native_errors),
)


# ── 报告 ────────────────────────────────────────────────────────────────────


def render_report(rows: list[tuple[str, str, bool, str]], prep: tuple[bool, str] | None) -> str:
    """渲染 Markdown 报告（工作流会把它拼进 PR 留痕评论）。"""
    lines = [
        "#### 验证项",
        "",
        "| 项 | 结论 | 读数 |",
        "|---|---|---|",
    ]
    for cid, title, ok, detail in rows:
        lines.append(f"| {cid} {title} | {'✅ 通过' if ok else '❌ 未通过'} | {detail} |")
    if prep is not None:
        ok, detail = prep
        lines.append(f"| 准备：`init --no-input` | {'✅' if ok else '❌'} | {detail} |")
    failed = [cid for cid, _t, ok, _d in rows if not ok]
    passed = len(rows) - len(failed)
    lines += ["", f"**结论**：{'✅ 全部通过' if not failed else '❌ 未通过'}（{passed} / {len(rows)}）"]
    if failed:
        lines.append("")
        lines.append(f"未过项：{', '.join(failed)} —— 逐条读数见上表；本工作流**不进**必需检查，"
                     "失败不拦合并，但必须被看见（status + 本评论）。")
    return "\n".join(lines)


# ── 入口 ────────────────────────────────────────────────────────────────────


def run_checks(ctx: Ctx, only: str | None) -> tuple[list[tuple[str, str, bool, str]], tuple[bool, str] | None]:
    """执行验证项，返回 `(逐项结果, 准备步骤结果)`。"""
    selected = [c for c in CHECKS if only is None or c[0] == only]
    if only is not None and not selected:
        raise SystemExit(f"没有编号为 {only} 的验证项（--list 可看全部）")

    # 准备：生成一份内置全量配置。验证的是「有配置时还能不能跑」，
    # 所以用 `--force` 重建，避免 runner 上的残留状态影响结论。
    prep: tuple[bool, str] | None = None
    if only is None:
        rc, out = run_cli(ctx, "PREP", ["init", "--no-input", "--force"])
        prep = (
            rc == 0,
            "生成内置全量配置" if rc == 0 else f"退出码 {rc}（后续验证项的结论可能连带受影响）",
        )

    rows: list[tuple[str, str, bool, str]] = []
    for cid, title, func in selected:
        try:
            ok, detail = func(ctx)
        except Exception as exc:                  # 单项异常不能带走整轮报告
            ok, detail = False, f"验证项内部错误：{exc.__class__.__name__}: {exc}"
        print(f"  {'✅' if ok else '❌'} {cid} {title} —— {detail}")
        rows.append((cid, title, ok, detail))
    return rows, prep


def self_test() -> int:
    """脚本自带断言（不需要 jar、不需要网络）。"""
    problems: list[str] = []

    # ① ANSI 剥离
    plain = strip_ansi("\x1b[31mAHA\x1b[0m 0.1.3")
    if plain != "AHA 0.1.3":
        problems.append(f"strip_ansi 结果不符：{plain!r}")

    # ② 缺失项判定
    if missing_items("a b c", ("a", "c")) != []:
        problems.append("missing_items 漏报：都在里面却报缺")
    if missing_items("a b c", ("a", "z")) != ["z"]:
        problems.append("missing_items 漏报真缺项")

    # ③ 编号唯一、顺序稳定（V6 必须最后 —— 它读的是前面各项的累计输出）
    ids = [cid for cid, _t, _f in CHECKS]
    if len(set(ids)) != len(ids):
        problems.append(f"验证项编号重复：{ids}")
    if ids[-1] != "V6":
        problems.append(f"依赖累计输出的 V6 不在最后：{ids}")

    # ④ 报告渲染：每项一行 + 结论行；未过项要列出来
    rows = [
        ("V1", "标题一", True, "读数一"),
        ("V2", "标题二", False, "读数二"),
    ]
    report = render_report(rows, (True, "准备读数"))
    for needle in ("| V1 标题一 | ✅ 通过 | 读数一 |", "| V2 标题二 | ❌ 未通过 | 读数二 |",
                   "（1 / 2）", "未过项：V2", "准备读数"):
        if needle not in report:
            problems.append(f"报告里缺：{needle!r}")
    all_pass = render_report([("V1", "标题一", True, "读数一")], None)
    if "未过项" in all_pass or "全部通过" not in all_pass:
        problems.append("全过时不该出现「未过项」")

    # ⑤ 标记字面量（工作流与守卫都认它）
    if MARKER != "<!-- predeploy-verify -->":
        problems.append(f"MARKER 被改动：{MARKER!r}")

    for problem in problems:
        print(f"  ❌ {problem}")
    if problems:
        print(f"\n❌ 自检未通过（{len(problems)} 项）")
        return 1
    print("✅ 自检通过（5 组断言）")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="AHA 部署前验证（非阻塞质量验证工作流，issue #166）")
    parser.add_argument("--jar", help="CLI 构建产物路径（如 Dist/aha-cli.jar）")
    parser.add_argument("--out", help="Markdown 报告输出路径（工作流用它拼留痕评论）")
    parser.add_argument("--summary", help="另外追加到该文件（工作流的 Job Summary）")
    parser.add_argument("--only", help="只跑某一项（编号，如 V4）")
    parser.add_argument("--verbose", action="store_true", help="打印每条命令与原始输出")
    parser.add_argument("--list", action="store_true", help="列出验证项后退出")
    parser.add_argument("--self-test", action="store_true", help="只跑脚本自带断言后退出")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()

    if args.list:
        for cid, title, _func in CHECKS:
            print(f"{cid}\t{title}")
        return 0

    if not args.jar:
        parser.error("需要 --jar（或用 --list / --self-test）")
    jar = Path(args.jar)
    if not jar.exists():
        # 产物缺失本身就是「部署前验证未通过」，不是脚本用法错误：
        # 照常出报告、退非 0，让工作流打出 failure 并留痕。
        detail = f"找不到构建产物 `{args.jar}` —— 构建步骤可能失败了"
        report = render_report([(cid, title, False, detail) for cid, title, _f in CHECKS], None)
        print(report)
        if args.out:
            Path(args.out).write_text(report + "\n", encoding="utf-8")
        if args.summary:
            with open(args.summary, "a", encoding="utf-8") as handle:
                handle.write(report + "\n")
        return 1

    if shutil.which("java") is None:
        print("❌ 找不到 java（需要 JDK 25）")
        return 1

    home = Path(tempfile.mkdtemp(prefix="aha-predeploy-"))
    print(f"部署前验证：jar={jar} home={home}")
    rows, prep = run_checks(Ctx(jar=jar, home=home, verbose=args.verbose), args.only)
    report = render_report(rows, prep)
    print()
    print(report)

    if args.out:
        Path(args.out).write_text(report + "\n", encoding="utf-8")
    if args.summary:
        with open(args.summary, "a", encoding="utf-8") as handle:
            handle.write(report + "\n")

    failed = [cid for cid, _t, ok, _d in rows if not ok]
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
