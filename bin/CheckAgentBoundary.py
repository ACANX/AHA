#!/usr/bin/env python3
"""Agent 执行边界自证（issue #70 / `Docs/Design/AgentExecutionBoundaryDesign.md` §6.6）。

`bin/AgentSandbox.sh` 建立的执行域「没有本地构建能力」这件事，本身必须被验证——
否则隔离被改坏（参数漏了、桩没建、bwrap 参数被删）也照样绿，等于没有边界（设计文档 F7）。

本脚本断言（对应设计文档 §6.6 的六条）：

  ① 域内 `./mvnw`、`sh ./mvnw`、`mvn`、`java`、`javac` 全部命中拒绝桩（退出码 97）；
  ② 域内 `/mnt` 不可见，`git` 照常可用、工作区不被改写；
  ③ `git` / `gh` / `python3 bin/Check*.py` 可用，能完成「改代码 → 推送 → 看 PR checks」闭环；
  ④ **反向用例**：`--allow-jdk` 时 `java` 可用、域外 `java` 可用 —— 证明上面的断言真的在起作用；
  ⑤ 环境边界与降级：仓库位于 `/mnt` 时拒绝启动、缺 `bwrap` 时给出明确退出码；
  ⑥ 平台不是 Linux 或没有 `bwrap` 时**明确跳过**（退出码 3），**不判绿**。

用法：
    python3 bin/CheckAgentBoundary.py
    python3 bin/CheckAgentBoundary.py --verbose

退出码：0 = 全部通过；1 = 存在失败；3 = 环境不支持（已明确标注跳过，不是通过）。
"""

from __future__ import annotations

import argparse
import os
import platform
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SANDBOX = ROOT / "bin" / "AgentSandbox.sh"

#: 命中拒绝桩的约定退出码（与 bin/AgentSandbox.sh 一致）
STUB_RC = 97
#: 拒绝桩输出里的标记（用来区分「被边界拦截」与「构建自己不干了」）
STUB_MARK = "已拦截"

EXIT_OK = 0
EXIT_FAIL = 1
EXIT_SKIP = 3


@dataclass
class Row:
    """一条断言的结果。"""

    name: str
    ok: bool
    detail: str


def sh(argv: list[object], *, cwd: Path = ROOT, env: dict[str, str] | None = None,
       timeout: int = 240) -> tuple[int, str, str]:
    """跑一个命令，返回 (退出码, stdout, stderr)；不做任何输出重定向/吞并。"""
    proc = subprocess.run(
        [str(a) for a in argv],
        cwd=str(cwd),
        capture_output=True,
        text=True,
        env=env,
        timeout=timeout,
    )
    return proc.returncode, proc.stdout, proc.stderr


def in_domain(*cmd: object, allow_jdk: bool = False, timeout: int = 240) -> tuple[int, str, str]:
    """在 Agent 执行域内跑命令。"""
    extra = ["--allow-jdk"] if allow_jdk else []
    return sh([SANDBOX, "--repo", ROOT, *extra, "--", *cmd], timeout=timeout)


def blocked(row_name: str, rc: int, stderr: str) -> Row:
    """断言「被拒绝桩拦住」：退出码为 97 且带指引。"""
    if rc != STUB_RC:
        return Row(row_name, False, f"期望 rc={STUB_RC}，实际 rc={rc}")
    if STUB_MARK not in stderr:
        return Row(row_name, False, f"rc={STUB_RC} 但 stderr 没有「{STUB_MARK}」指引")
    return Row(row_name, True, f"rc={STUB_RC}，指引在 stderr")


def usable(row_name: str, rc: int, stderr: str) -> Row:
    """断言「没有被拦」：退出码为 0 且不带拒绝桩的标记。"""
    if rc != 0:
        return Row(row_name, False, f"期望 rc=0，实际 rc={rc}：{stderr.strip()[:120]}")
    if STUB_MARK in stderr:
        return Row(row_name, False, "被拒绝桩拦截了（反向用例应当放开）")
    return Row(row_name, True, "rc=0，未被拦截")


def git_status_short() -> str:
    """工作区状态（用于「自证不改写工作区」的前后对比）。"""
    rc, out, err = sh(["git", "status", "--short"])
    if rc != 0:
        raise SystemExit(f"git status 失败：{err.strip()}")
    return out


def plan_text() -> str:
    rc, out, err = sh([SANDBOX, "--repo", ROOT, "--print-plan"])
    if rc != 0:
        raise SystemExit(f"--print-plan 失败（rc={rc}）：{err.strip()}")
    return out


def check_plan(plan: str, host_has_java: bool) -> list[Row]:
    """隔离参数必须真的出现在生效计划里（防止「参数被删了但脚本照跑」）。"""
    rows = [
        Row("计划含 `--tmpfs /mnt`（Windows 盘不可见）", "--tmpfs /mnt" in plan,
            "已包含" if "--tmpfs /mnt" in plan else "缺失：/mnt 会暴露给 Agent"),
        Row("计划含 `--chdir <仓库>`（在指定仓库内工作）", "--chdir" in plan,
            "已包含" if "--chdir" in plan else "缺失：不会切到 --repo 指定的目录"),
        Row("计划含 `JAVA_HOME` 指向桩 JDK", "setenv JAVA_HOME" in plan,
            "已包含" if "setenv JAVA_HOME" in plan else "缺失：mvnw 会去找宿主 JDK"),
        Row("计划含桩目录前置的 PATH", "setenv PATH" in plan and "/bin:" in plan,
            "已包含" if "setenv PATH" in plan else "缺失：直接敲 mvn / java 不会被拦"),
    ]
    if host_has_java:
        hidden = [line.strip()[2:] for line in plan.splitlines() if line.strip().startswith("- /")]
        rows.append(Row(
            "计划含至少一个被隐藏的 JDK 根目录",
            bool(hidden),
            f"隐藏 {len(hidden)} 个：{', '.join(hidden[:3])}" if hidden else "一个都没有：宿主 JDK 仍可见",
        ))
    return rows


def check_missing_bwrap() -> Row:
    """缺 bwrap 时必须明确报错（退出码 69），而不是「悄悄不加沙箱照跑」。"""
    tmp = tempfile.mkdtemp(prefix="aha-boundary-nobwrap-")
    try:
        needed = ["sh", "bash", "env", "mktemp", "mkdir", "ln", "chmod", "readlink", "rm", "cat"]
        for name in needed:
            real = shutil.which(name)
            if not real:
                return Row("缺 bwrap 时明确报错（rc=69）", False, f"环境里连 {name} 都没有，无法构造该用例")
            os.symlink(real, os.path.join(tmp, name))
        env = {
            "PATH": tmp,
            "HOME": os.environ.get("HOME", "/root"),
        }
        rc, _out, err = sh([SANDBOX, "--repo", ROOT, "--", "true"], env=env)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    if rc == 69 and "bwrap" in err:
        return Row("缺 bwrap 时明确报错（rc=69）", True, "rc=69，提示指向容器化方案")
    return Row("缺 bwrap 时明确报错（rc=69）", False, f"期望 rc=69，实际 rc={rc}：{err.strip()[:100]}")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Agent 执行边界自证：断言域内没有本地构建能力，且隔离失效时该自证会失败。")
    parser.add_argument("--verbose", action="store_true", help="打印每条断言的原始输出")
    args = parser.parse_args(argv)

    print("Agent 执行边界自证（bin/CheckAgentBoundary.py）")
    print(f"  仓库：{ROOT}")
    print(f"  平台：{platform.system()} {platform.machine()}")

    # ── ⑥ 环境不支持时明确跳过，不判绿 ──────────────────────────────────
    if platform.system() != "Linux":
        print()
        print("⚠️  跳过：Agent 执行域基于 Linux 命名空间（bwrap），当前平台无法验证。")
        print("    这是**跳过**，不是通过——需要该保证请在 Linux（WSL）或 CI 上跑本脚本。")
        return EXIT_SKIP
    bwrap = shutil.which("bwrap")
    if not bwrap:
        print()
        print("⚠️  跳过：本机没有 bwrap（bubblewrap），无法建立执行域。")
        print("    安装：sudo apt-get install -y bubblewrap（CI 的 Gate.yml 已装）；")
        print("    长期方案见 Docs/Design/AgentExecutionBoundaryDesign.md §4.5（容器化）。")
        return EXIT_SKIP
    if not SANDBOX.is_file():
        print(f"❌ 缺少启动器：{SANDBOX}")
        return EXIT_FAIL

    _rc, version_out, _err = sh(["bwrap", "--version"])
    print(f"  bwrap：{bwrap}（{version_out.strip() or '版本未知'}）")

    host_java = shutil.which("java")
    print(f"  宿主 JDK：{host_java or '（无）'}")
    if not host_java:
        print()
        print("⚠️  跳过：宿主没有 java，无法做「放开 JDK 后应当可用」的反向用例（F7 的前提）。")
        return EXIT_SKIP

    baseline = git_status_short()
    rows: list[Row] = []

    # ── ① 域内构建 / JDK 入口全部被拦 ───────────────────────────────────
    section_1: list[Row] = []
    for label, cmd in (
        ("./mvnw -v", ["./mvnw", "-v"]),
        ("sh ./mvnw -v（绕过执行位）", ["sh", "./mvnw", "-v"]),
        ("mvn -v", ["mvn", "-v"]),
        ("java -version", ["java", "-version"]),
        ("javac -version", ["javac", "-version"]),
    ):
        rc, out, err = in_domain(*cmd)
        section_1.append(blocked(label, rc, err))
        if args.verbose:
            print(f"    [{label}] rc={rc}\n{out}{err}")

    # ── ② 域内 /mnt 不可见、git 可用、工作区不被改写 ────────────────────
    section_2: list[Row] = []
    rc, out, err = in_domain("sh", "-c", "ls -A /mnt 2>/dev/null | wc -l")
    entries = out.strip()
    section_2.append(Row("/mnt 不可见（Windows 盘）", rc == 0 and entries == "0",
                         f"条目数={entries or '?'}（期望 0）"))

    rc, out, err = in_domain("git", "rev-parse", "--is-inside-work-tree")
    section_2.append(Row("git 在域内可用", rc == 0 and out.strip() == "true",
                         f"rc={rc}，输出={out.strip() or err.strip()[:60]}"))

    rc, out, err = in_domain("git", "status", "--short")
    section_2.append(Row("域内 git status 与自证前一致（未改写工作区）",
                         rc == 0 and out == baseline,
                         "一致" if out == baseline else "不一致：自证过程改动了工作区（F1）"))

    # ── ③ 闭环可用：git / gh / python3 与静态检查 ───────────────────────
    section_3: list[Row] = []
    rc, out, err = in_domain(
        "sh", "-c", "command -v git && command -v gh && command -v python3")
    missing = [line for line in ("git", "gh", "python3") if line not in out]
    section_3.append(Row("域内可解析 git / gh / python3", not missing,
                         f"缺失：{', '.join(missing)}" if missing else "均可解析"))

    rc, out, err = in_domain("python3", "bin/CheckSkills.py", timeout=300)
    section_3.append(Row("域内能跑静态检查（python3 bin/CheckSkills.py）", rc == 0,
                         "rc=0" if rc == 0 else f"rc={rc}：{err.strip()[:100]}"))

    # ── ④ 反向用例：证明断言真的在起作用（F7） ──────────────────────────
    section_4: list[Row] = []
    rc, out, err = in_domain("java", "-version", allow_jdk=True)
    section_4.append(usable("--allow-jdk 时 java 可用（放开就该能用）", rc, err))

    rc, out, err = sh(["java", "-version"])
    section_4.append(usable("域外 java 可用（对照：域内被拦是真拦）", rc, err))

    # ── ⑤ 环境边界与降级 ───────────────────────────────────────────────
    section_5: list[Row] = []
    if Path("/mnt").is_dir():
        rc, out, err = sh([SANDBOX, "--repo", "/mnt", "--", "true"])
        section_5.append(Row("仓库位于 /mnt 时拒绝启动（rc=64）",
                             rc == 64 and "Windows 盘" in err,
                             "rc=64，提示环境边界" if rc == 64 else f"期望 rc=64，实际 rc={rc}"))
    else:
        section_5.append(Row("仓库位于 /mnt 时拒绝启动（rc=64）", True,
                             "本机无 /mnt，跳过该用例（不判失败）"))
    section_5.extend(check_plan(plan_text(), host_has_java=True))
    section_5.append(check_missing_bwrap())

    # ── 收尾：自证之后工作区仍然不被改写 ────────────────────────────────
    after = git_status_short()
    rows_extra = [Row("自证结束后工作区仍未被改写", after == baseline,
                      "一致" if after == baseline else "不一致")]

    for title, group in (
        ("① 域内构建 / JDK 入口全部被拦", section_1),
        ("② 域内无 /mnt、git 可用、不改写工作区", section_2),
        ("③ 域内闭环可用（git / gh / python3 / 静态检查）", section_3),
        ("④ 反向用例：放开隔离时必须「不被拦」", section_4),
        ("⑤ 环境边界与降级路径", section_5),
        ("⑥ 收尾复检", rows_extra),
    ):
        print()
        print(title)
        for row in group:
            rows.append(row)
            mark = "✅" if row.ok else "❌"
            print(f"  {mark} {row.name}　—　{row.detail}")

    passed = sum(1 for row in rows if row.ok)
    total = len(rows)
    print()
    failed = [row for row in rows if not row.ok]
    if failed:
        print(f"结果：{passed}/{total} 通过 —— ❌ {len(failed)} 条失败")
        for row in failed:
            print(f"  · {row.name}：{row.detail}")
        print()
        print("失败含义：Agent 执行域没有按设计生效。先看 bin/AgentSandbox.sh 的隔离参数，")
        print("再对照 Docs/Design/AgentExecutionBoundaryDesign.md §6.6 的断言清单。")
        return EXIT_FAIL

    print(f"结果：{passed}/{total} 通过 —— 域内没有本地构建能力，且隔离失效时本自证会失败。")
    return EXIT_OK


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
