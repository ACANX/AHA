#!/usr/bin/env python3
"""AHA 版本分发：把新版本号落到所有「版本出现位置」（TD-00016 §H.3 / issue #96）。

背景：版本号曾散落在 10 处、靠人记清单，已经漂移过一次
（见 `Docs/Troubleshooting/TS-202610-VersionBumpMissedModules.md`）。P3（#95）之后，
POM 侧收敛为根 `pom.xml` 的 `<properties>/<revision>` 一处，其余位置由本脚本分发：

  1. 根 `pom.xml` 的 `<revision>`（8 个子模块经 `<parent><version>${revision}</version>` 继承）
  2. 根目录 `version` 文件（人读；也是 `ProjectVersion.py --resolve` 的优先来源）
  3. `AppVersion.FALLBACK_VERSION`（已与版本解耦则跳过，见 §C）
  4. 4 处文档版本声明（已改成不写死则跳过，见 §D / #97）

用法：

    python3 Script/Python/VersionDistribute.py --version 0.1.3

设计约束：

  * **动态扫描**：POM 清单来自 `git ls-files '*/pom.xml'`（不硬编码），并断言总数 ≥ 9，
    新增模块自动覆盖、漏扫立即失败（§H.3-2）；
  * **正则只动 `<properties>` 块**：保留注释与格式，不依赖 Maven 的 versions 插件
    （项目约定：Agent 不在本地跑 Maven，见 `AGENTS.md`）；
  * **先算后写**：所有改动先在内存算好并校验，全部成功才落盘，避免留下半成品；
  * **幂等拒绝**：新版本与当前相同即非零退出，不产生空提交；
  * **只动声明行**：文档的版本声明按「声明行」精确匹配，**不做全文件替换、也不全仓搜
    旧版本号**——历史叙述里的旧版本号（如「0.1.1 起落地」「实测覆盖率（0.1.1）」）
    是合法内容，全仓扫描会成片误报（实测 60+ 处），全文件替换会把历史改错；
  * **自证**：分发后正向断言受管位置（根 POM `<revision>`、`version` 文件、
    `AppVersion` 兜底常量）已等于新版本。

退出码：0 = 分发完成；1 = 校验失败；2 = 用法错误。
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

# 仓库根：本脚本位于 Script/Python/ 下
ROOT = Path(__file__).resolve().parents[2]

# 新版本号格式：a.b.c（不含 SNAPSHOT；预发行用 a.b.c.PPPPP 构建号表达，见 ReleaseProcess.md §2）
VERSION_RE = re.compile(r"\d+\.\d+\.\d+\Z")

# 受管 POM 下限：根 + 8 个子模块。新增模块后只会更多，少了说明扫描漏了（§H.3-2）
MIN_POM_COUNT = 9

VERSION_FILE = ROOT / "version"
PROJECT_VERSION_SCRIPT = ROOT / ".github/Python/ProjectVersion.py"
APP_VERSION_FILE = ROOT / "aha-common/src/main/java/com/acanx/module/aha/common/AppVersion.java"

# 文档版本声明（§D）：只按「声明行」精确匹配，**绝不整文件替换**。
#
# 为什么必须精确：历史文档里合法地存在旧版本号——`AHA-Design-V1.md` 的
# 「0.1.1 起落地」「实测覆盖率（0.1.1）」、修订记录表等。整文件替换会把历史改错，
# 全仓搜旧版本号则会把它们全报成「残留」（实测 60+ 处误报）。
# 每个 pattern 只负责定位**声明行**；替换时只改该行里第一个 a.b.c。
# 注意：POM 示例行必须带上旧版本号——设计文档里还有**插件 / 依赖版本**示例，
# 不加限定会把 `<version>3.16.0</version>` 一并改成项目版本（实测踩到过）。
# #97 把文档改成不写死后，这些 pattern 匹配 0 次，脚本自动跳过。
def doc_declaration_patterns(old: str) -> dict[str, tuple[re.Pattern[str], ...]]:
    return {
        "README.md": (
            re.compile(r"(?m)^当前版本 \*\*[\d.]+\*\*"),
        ),
        "AGENTS.md": (
            re.compile(r"(?m)^\*\*当前版本\*\*：[\d.]+"),
        ),
        "Docs/AHA/AHA-Design-V1.md": (
            re.compile(r"(?m)^\*\*目标版本\*\*：AHA [\d.]+"),
            re.compile(r"(?m)^\*\*当前版本\*\*：[\d.]+"),
            re.compile(rf"(?m)^\s*<version>{re.escape(old)}</version>"),
        ),
        "Docs/Guide/ReferenceGuide.md": (
            re.compile(r"(?m)dev` 上工作在 `[\d.]+`"),
        ),
    }

# 声明行里的版本 token
VERSION_TOKEN_RE = re.compile(r"\d+\.\d+\.\d+")

PROPERTIES_BLOCK_RE = re.compile(r"<properties>.*?</properties>", re.S)
REVISION_RE = re.compile(r"<revision>\s*([^<\s]+)\s*</revision>")
PARENT_BLOCK_RE = re.compile(r"<parent>.*?</parent>", re.S)
PARENT_VERSION_RE = re.compile(r"<version>([^<]+)</version>")
FALLBACK_RE = re.compile(r'(FALLBACK_VERSION\s*=\s*")([^"]+)(")')


class DistributeError(Exception):
    """分发前的校验失败——不改任何文件。"""


def git_ls_files(*patterns: str) -> list[str]:
    """`git ls-files` 的封装（返回仓库相对路径）。"""
    result = subprocess.run(
        ["git", "ls-files", "-z", *patterns],
        cwd=ROOT,
        capture_output=True,
        check=True,
    )
    return [name for name in result.stdout.decode("utf-8").split("\0") if name]


def current_version() -> str:
    """当前版本号——复用唯一读取入口，不在本脚本里重复实现版本解析。"""
    result = subprocess.run(
        [sys.executable, str(PROJECT_VERSION_SCRIPT), "--resolve"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    value = result.stdout.strip()
    if not value:
        raise DistributeError("ProjectVersion.py --resolve 没有输出当前版本号")
    return value


def find_poms() -> list[str]:
    """动态扫描 POM：根 `pom.xml` + `git ls-files '*/pom.xml'`（排除 `target/`）。"""
    poms = ["pom.xml"]
    poms.extend(
        name
        for name in git_ls_files("*/pom.xml")
        if name != "pom.xml" and not name.startswith("target/")
    )
    return sorted(set(poms))


def update_declarations(
    text: str, patterns: tuple[re.Pattern[str], ...], new: str
) -> tuple[str, int]:
    """更新声明行里的版本号，返回 (新文本, 替换次数)。

    只在**声明行**内替换第一个 a.b.c，因此不会碰历史叙述里的旧版本号。
    """
    total = 0
    for pattern in patterns:

        def replace(match: re.Match[str]) -> str:
            nonlocal total
            updated, count = VERSION_TOKEN_RE.subn(new, match.group(0), count=1)
            total += count
            return updated

        text = pattern.sub(replace, text)
    return text, total


def plan_distribution(old: str, new: str) -> tuple[dict[Path, str], list[str]]:
    """算出所有待写文件（不落盘），返回 (文件→新内容, 说明清单)。"""
    planned: dict[Path, str] = {}
    notes: list[str] = []

    # ① 动态扫描 POM，并断言子模块都引用 ${revision}
    poms = find_poms()
    if len(poms) < MIN_POM_COUNT:
        raise DistributeError(
            f"只扫描到 {len(poms)} 个 POM（下限 {MIN_POM_COUNT}）——扫描逻辑或仓库结构异常"
        )
    for rel in poms:
        text = (ROOT / rel).read_text(encoding="utf-8")
        parent = PARENT_BLOCK_RE.search(text)
        if parent is None:
            if rel != "pom.xml":
                raise DistributeError(f"{rel} 没有 <parent> 块——是否新增了非子模块 POM？")
            continue
        version = PARENT_VERSION_RE.search(parent.group(0))
        if version is None:
            raise DistributeError(f"{rel} 的 <parent> 里没有 <version>")
        if version.group(1).strip() != "${revision}":
            raise DistributeError(
                f"{rel} 的 <parent><version> 是 {version.group(1).strip()!r}，应为 ${{revision}}（P3/#95）"
            )
    notes.append(f"动态扫描到 {len(poms)} 个 POM（下限 {MIN_POM_COUNT}），子模块均引用 ${{revision}}")

    # ② 根 POM 的 <properties>/<revision>：正则只动 <properties> 块，保留注释与格式
    pom = ROOT / "pom.xml"
    text = pom.read_text(encoding="utf-8")
    block_match = PROPERTIES_BLOCK_RE.search(text)
    if block_match is None:
        raise DistributeError("根 pom.xml 里找不到 <properties> 块")
    block = block_match.group(0)
    revision = REVISION_RE.search(block)
    if revision is None:
        raise DistributeError("根 pom.xml 的 <properties> 里找不到 <revision>（P3/#95 未落地？）")
    if revision.group(1) != old:
        raise DistributeError(
            f"根 pom.xml 的 <revision> 是 {revision.group(1)}，与当前版本 {old} 不一致——先修一致再分发"
        )
    new_block = REVISION_RE.sub(lambda _: f"<revision>{new}</revision>", block, count=1)
    planned[pom] = text[: block_match.start()] + new_block + text[block_match.end() :]
    notes.append(f"根 pom.xml 的 <revision>：{old} → {new}")

    # ③ 根目录 version 文件（权威源，人读）
    planned[VERSION_FILE] = new + "\n"
    notes.append(f"根目录 version 文件：{old} → {new}")

    # ④ AppVersion.FALLBACK_VERSION：已解耦（§C）则跳过
    if APP_VERSION_FILE.is_file():
        content = APP_VERSION_FILE.read_text(encoding="utf-8")
        fallback = FALLBACK_RE.search(content)
        if fallback is None:
            raise DistributeError("AppVersion.java 里找不到 FALLBACK_VERSION")
        value = fallback.group(2)
        if value == "dev":
            notes.append('AppVersion.FALLBACK_VERSION 已与版本解耦（"dev"），跳过')
        elif value == f"{old}-dev":
            planned[APP_VERSION_FILE] = FALLBACK_RE.sub(
                lambda match: f"{match.group(1)}{new}-dev{match.group(3)}", content, count=1
            )
            notes.append(f"AppVersion.FALLBACK_VERSION：{old}-dev → {new}-dev")
        else:
            raise DistributeError(f"AppVersion.FALLBACK_VERSION 的值意外：{value!r}")

    # ⑤ 文档版本声明：按「声明行」精确更新；匹配 0 次说明已改为不写死（§D / #97 之后的常态）
    for rel, patterns in doc_declaration_patterns(old).items():
        path = ROOT / rel
        if not path.is_file():
            continue
        content = path.read_text(encoding="utf-8")
        updated, count = update_declarations(content, patterns, new)
        if count:
            planned[path] = updated
            notes.append(f"{rel}：{count} 处版本声明 {old} → {new}")
        else:
            notes.append(f"{rel}：无版本声明行（已改为不写死？），跳过")

    return planned, notes


def verify_managed(new: str) -> list[str]:
    """正向断言：受管位置必须已是新版本。

    刻意**不做全仓搜旧版本号**：历史文档 / 示例 / 依赖版本里的旧版本号都是合法内容，
    全仓扫描会成片误报；受管位置的正向断言已足以证明分发到位。
    """
    problems: list[str] = []

    version_file = VERSION_FILE.read_text(encoding="utf-8").strip()
    if version_file != new:
        problems.append(f"version 文件是 {version_file}，应为 {new}")

    text = (ROOT / "pom.xml").read_text(encoding="utf-8")
    block = PROPERTIES_BLOCK_RE.search(text)
    revision = REVISION_RE.search(block.group(0)).group(1) if block else None
    if revision != new:
        problems.append(f"根 pom.xml 的 <revision> 是 {revision}，应为 {new}")

    if APP_VERSION_FILE.is_file():
        fallback = FALLBACK_RE.search(APP_VERSION_FILE.read_text(encoding="utf-8"))
        value = fallback.group(2) if fallback else None
        if value not in ("dev", f"{new}-dev"):
            problems.append(
                f'AppVersion.FALLBACK_VERSION 是 {value!r}（应为 "dev" 或 "{new}-dev"）'
            )

    return problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="AHA 版本分发（TD-00016 §H.3）")
    parser.add_argument("--version", required=True, metavar="X.Y.Z", help="新版本号（如 0.1.3）")
    args = parser.parse_args(argv)

    new = args.version.strip()
    if not VERSION_RE.fullmatch(new):
        print(f"[问题] 版本号 {new!r} 不符合 a.b.c 格式（不含 SNAPSHOT）", file=sys.stderr)
        return 2

    try:
        old = current_version()
    except (DistributeError, subprocess.SubprocessError) as exc:
        print(f"[问题] 无法确定当前版本：{exc}", file=sys.stderr)
        return 1

    if old == new:
        print(f"[问题] 当前版本已是 {new}，无需分发（拒绝空提交）", file=sys.stderr)
        return 1

    try:
        planned, notes = plan_distribution(old, new)
    except DistributeError as exc:
        print(f"[问题] {exc}", file=sys.stderr)
        return 1

    # 先算后写：上面的校验全部通过后才落盘
    for path, text in planned.items():
        path.write_text(text, encoding="utf-8")

    problems = verify_managed(new)
    if problems:
        print("[问题] 分发后自证失败：", file=sys.stderr)
        for item in problems:
            print(f"  ❌ {item}", file=sys.stderr)
        return 1

    print(f"版本分发完成：{old} → {new}")
    for note in notes:
        print(f"  ✅ {note}")
    print(f"  ✅ 自证：根 pom.xml 的 <revision> 与 version 文件均为 {new}")
    print(f"  ✅ 共写入 {len(planned)} 个文件")

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        lines = "\n".join(f"- `{path.relative_to(ROOT)}`" for path in sorted(planned))
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(f"### 版本分发 {old} → {new}\n\n{lines}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
