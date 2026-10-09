#!/usr/bin/env python3
"""AHA 版本号读取与一致性校验（TD-00016 §B / §E；issue #93 / #94）。

版本号在仓库里有多个「出现位置」：人写的一处（根 POM），机器生成的多处
（资源过滤后的 `version.properties`、构建产物文件名、CI 打出的 tag）。
任何一处漏改都会**静默产出错误版本**——0.1.1 那次就是这样把旧版本的包发了
出去（见 `Docs/Troubleshooting/TS-202610-VersionBumpMissedModules.md`）。

本脚本同时承担两件事：

  * **唯一读取入口**（§B）：解析顺序为「显式输入 → `version` 文件 → 根 POM 的
    `<properties>/<revision>` → 根 POM 的 `<version>`」；各工作流都经它读版本，
    版本源将来怎么变（`<version>` ↔ `<revision>` ↔ `version` 文件）只改这一处。
  * **一致性校验**（§E）：把「版本漂移」从静默错误变成 CI 红灯，并在失败信息
    里点明是哪一处对不上，而不是只抛一句「版本不一致」。

`--verify` 校验五处（与 §E 一一对应）：

  1. 根目录 `version` 文件（P4 引入；尚不存在时以根 POM 为基准）
  2. 根 `pom.xml` 的版本（优先 `<properties>/<revision>`，回退根 `<version>`）
  3. 构建产物里的 `version.properties` 的 `version` 项
  4. 构建产物文件名中的版本段（各模块 `target/` 与根 `Dist/`）
  5. `Build.yml` 打出的 tag 名（约定为 `V<版本>`）

用法：

    python3 .github/Python/ProjectVersion.py --resolve [<版本>]  # 解析版本（显式输入优先）
    python3 .github/Python/ProjectVersion.py --verify            # 一致性校验（CI 用）
    python3 .github/Python/ProjectVersion.py                     # 裸调用：输出当前版本号

前置条件：`--verify` 的第 3、4 项依赖构建产物，故必须排在 `clean verify`
之后（见 `Gate.yml`：先完整构建，再校验）。`--resolve` 只读文本，无此限制。

退出码：0 = 通过；1 = 存在不一致或无法解析；2 = 用法错误。
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from xml.etree import ElementTree

# 仓库根：本脚本位于 .github/Python/ 下（与 MetaOpen 的组织方式一致）
ROOT = Path(__file__).resolve().parents[2]

# 基线版本号格式：a.b.c（AHA 的预发行用 a.b.c.PPPPP 构建号表达，
# 不含 SNAPSHOT，见 Docs/DevSpec/ReleaseProcess.md 第 2 节）
VERSION_RE = re.compile(r"\d+\.\d+\.\d+")

# 显式传入的完整版本号：a.b.c 或带构建号的 a.b.c.PPPPP
EXPLICIT_VERSION_RE = re.compile(r"\d+\.\d+\.\d+(?:\.\d+)?")

# 遍历时的剪枝目录：既不产出构建产物，也避免扫到海量无关文件
SKIP_DIRS = {".git", "node_modules", ".idea", ".venv", "__pycache__"}

# 构建产物所在目录（只在其中一层找 jar/zip，不深入 classes/ 等子目录）
ARTIFACT_DIRS = {"target", "Dist"}

# Java Properties 的一行：`key=value` 或 `key: value`
PROPERTY_LINE = re.compile(r"^\s*([^#!\s][^=:\s]*)\s*[=:]\s*(.*?)\s*$")


class VerifyError(Exception):
    """无法继续校验（根 POM 缺失 / 版本基准解析不出来）——与「发现不一致」区分。"""


def namespace_of(element: ElementTree.Element) -> str:
    """取元素命名空间前缀（如 `{http://maven.apache.org/POM/4.0.0}`）。"""
    tag = element.tag if isinstance(element.tag, str) else ""
    return tag[: tag.index("}") + 1] if "}" in tag else ""


def read_root_pom(root: Path) -> tuple[str, str]:
    """读取根 POM 的 `<version>` 与 `<properties>/<revision>`（可能为空串）。"""
    pom = root / "pom.xml"
    if not pom.is_file():
        raise VerifyError(f"根 POM 不存在：{pom}")
    try:
        top = ElementTree.parse(pom).getroot()
    except ElementTree.ParseError as exc:
        raise VerifyError(f"根 POM 不是良构 XML：{exc}") from exc
    ns = namespace_of(top)
    version = (top.findtext(ns + "version") or "").strip()
    properties = top.find(ns + "properties")
    revision = ""
    if properties is not None:
        revision = (properties.findtext(ns + "revision") or "").strip()
    return version, revision


def resolve_version(root: Path, override: str | None = None) -> tuple[str, str]:
    """解析版本号，返回 (版本号, 来源描述)。

    优先级（TD-00016 §H.1）：显式输入 → `version` 文件 → 根 POM 的 `<revision>`
    → 根 POM 的 `<version>`。

    `override` 为空串或 None 时忽略显式输入，继续按文件 / POM 解析。
    """
    if override is not None and override.strip():
        value = override.strip()
        if not EXPLICIT_VERSION_RE.fullmatch(value):
            raise VerifyError(
                f"显式版本号 {override!r} 不符合 a.b.c 或 a.b.c.PPPPP 格式"
            )
        return value, "显式输入（命令行 / workflow input）"
    version_file = root / "version"
    pom_version, revision = read_root_pom(root)
    if version_file.is_file():
        value = version_file.read_text(encoding="utf-8").strip()
        return value, "version 文件"
    if revision:
        return revision, "pom.xml 的 <properties>/<revision>"
    if pom_version.startswith("${"):
        raise VerifyError(
            f"根 POM 的 <version> 是占位符 {pom_version!r}，但 <properties> 里没有可用的 <revision>"
        )
    if not pom_version:
        raise VerifyError("根 POM 里既没有可用的 <revision>，也没有 <version>")
    return pom_version, "pom.xml 的 <version>"


def read_property(path: Path, key: str) -> str | None:
    """从 Java Properties 文件里取一个键值。"""
    for line in path.read_text(encoding="utf-8").splitlines():
        match = PROPERTY_LINE.match(line)
        if match and match.group(1) == key:
            return match.group(2)
    return None


def collect_artifact_ids(root: Path) -> list[str]:
    """收集本项目各模块的 artifactId（用于把项目产物与第三方依赖区分开）。

    只扫根 POM 与一级子目录的 POM，不进入 `.agents/skills` 里的模板。
    """
    ids: list[str] = []
    for pom in [root / "pom.xml", *sorted(root.glob("*/pom.xml"))]:
        if not pom.is_file():
            continue
        try:
            top = ElementTree.parse(pom).getroot()
        except ElementTree.ParseError:
            continue
        artifact_id = (top.findtext(namespace_of(top) + "artifactId") or "").strip()
        if artifact_id:
            ids.append(artifact_id)
    # 长的在前：`aha-cli-native` 必须先于 `aha-cli` 匹配，否则版本段会被截错
    return sorted(set(ids), key=len, reverse=True)


def collect_artifacts(root: Path) -> list[Path]:
    """收集 `target/` 与 `Dist/` 下的 jar / zip 产物。

    产物目录**只扫一层**（外加 `Dist/lib` 这样的一层子目录）：构建目录下还有
    `classes/`、`maven-status/` 等海量文件，深挖既慢又没有意义。
    """
    artifacts: list[Path] = []
    for dirpath, dirnames, _ in os.walk(root):
        dirnames[:] = sorted(name for name in dirnames if name not in SKIP_DIRS)
        base = Path(dirpath)
        if base.name in ARTIFACT_DIRS:
            for pattern in ("*.jar", "*.zip", "*/*.jar", "*/*.zip"):
                artifacts.extend(path for path in base.glob(pattern) if path.is_file())
            dirnames[:] = []  # 不再深入
    return sorted(set(artifacts))


def match_artifact_id(name: str, artifact_ids: list[str]) -> str | None:
    """判断文件名是否为本项目产物，返回匹配到的 artifactId。"""
    for artifact_id in artifact_ids:
        if name.startswith(artifact_id + "-"):
            return artifact_id
    return None


def extract_version(name: str, artifact_id: str) -> str | None:
    """从 `<artifactId>-<版本>[-classifier].<ext>` 里取出版本段。"""
    tail = name[len(artifact_id) + 1 :]
    match = VERSION_RE.search(tail)
    return match.group(0) if match else None


def check_tag_rule(root: Path, baseline: str) -> tuple[bool, str]:
    """校验 `Build.yml` 打出的 tag 名符合 `V<版本>` 约定。

    tag 名由工作流运行时拼出，本地文件里没有成品可读，因此校验的是**生成规则**：
    该行的值必须是「`V` 前缀 + 版本表达式」（不许写死版本号），或者恰好等于
    `V<基准版本>`。改写前缀、改成硬编码数字都会在这里被拦下。
    """
    workflow = root / ".github/workflows/Build.yml"
    if not workflow.is_file():
        return False, "未找到 .github/workflows/Build.yml"
    lines = [
        line.strip()
        for line in workflow.read_text(encoding="utf-8").splitlines()
        if re.match(r"\s*TAG\s*:", line)
    ]
    if not lines:
        return False, "Build.yml 里找不到 `TAG:`（tag 名的生成规则失去锚点，无法校验）"
    value = lines[0].split(":", 1)[1].strip()
    expected = "V" + baseline
    if "${" in value:
        if not value.startswith("V"):
            return False, f"tag 名模板 {value!r} 不以 `V` 开头，与 `V<版本>` 约定不符"
        if VERSION_RE.search(value):
            return False, f"tag 名模板 {value!r} 里写死了版本号，应由版本来源拼接"
        return True, f"`V<版本>`（当前为 {expected}）"
    if value == expected:
        return True, value
    return False, f"tag 名写死为 {value!r}，与 `V<版本>`（应为 {expected}）不一致"


def collect_findings(root: Path, baseline: str) -> list[tuple[str, bool, str]]:
    """执行五处校验，返回 [(检查项, 是否一致, 说明)]。"""
    findings: list[tuple[str, bool, str]] = []

    # ① 根目录 version 文件（P4 引入；暂无时以根 POM 为基准，不算不一致）
    version_file = root / "version"
    if version_file.is_file():
        value = version_file.read_text(encoding="utf-8").strip()
        if not VERSION_RE.fullmatch(value):
            findings.append(("根目录 version 文件", False, f"值 {value!r} 不符合 a.b.c 格式"))
        else:
            findings.append(
                ("根目录 version 文件", value == baseline, f"{value}")
            )
    else:
        findings.append(
            ("根目录 version 文件", True, "不存在——P4（#96）才引入，本次以根 POM 为基准")
        )

    # ② 根 POM 的版本
    pom_version, revision = read_root_pom(root)
    effective = revision or pom_version
    label = "根 POM 的 <properties>/<revision>" if revision else "根 POM 的 <version>"
    findings.append((label, effective == baseline, effective or "未找到"))

    # ③ version.properties 的 version 项
    property_files = sorted(root.glob("*/target/classes/**/version.properties"))
    if not property_files:
        findings.append(
            ("构建产物里的 version.properties", False, "未找到——请先执行 clean verify（本步骤必须排在构建之后）")
        )
    for path in property_files:
        value = read_property(path, "version")
        relative = path.relative_to(root)
        findings.append(
            (
                f"version.properties 的 version（{relative}）",
                value == baseline,
                value or "缺少 version 项",
            )
        )

    # ④ 产物文件名中的版本段
    artifacts = collect_artifacts(root)
    if not artifacts:
        findings.append(
            ("产物文件名中的版本段", False, "target/ 与 Dist/ 下没有 jar/zip——请先执行 clean verify")
        )
    else:
        artifact_ids = collect_artifact_ids(root)
        matched: list[Path] = []
        mismatched: list[str] = []
        for artifact in artifacts:
            artifact_id = match_artifact_id(artifact.name, artifact_ids)
            if artifact_id is None:
                continue  # 第三方依赖（如 Dist/lib 下的 jackson-*.jar），不属本项目版本
            matched.append(artifact)
            segment = extract_version(artifact.name, artifact_id)
            if segment != baseline:
                mismatched.append(
                    f"{artifact.relative_to(root)}（版本段 {segment or '未识别'}）"
                )
        if mismatched:
            findings.append(
                ("产物文件名中的版本段", False, "；".join(mismatched))
            )
        elif matched:
            sample = "、".join(str(path.relative_to(root)) for path in matched[:4])
            findings.append(
                ("产物文件名中的版本段", True, f"{len(matched)} 个产物一致（如 {sample}）")
            )
        else:
            findings.append(
                ("产物文件名中的版本段", False, "有 jar/zip 但无一本项目产物（artifactId 未匹配）")
            )

    # ⑤ Build.yml 的 tag 命名
    tag_ok, tag_detail = check_tag_rule(root, baseline)
    findings.append(("Build.yml 的 tag 名（V<版本>）", tag_ok, tag_detail))

    return findings


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="AHA 版本号读取与一致性校验（TD-00016 §B / §E）"
    )
    parser.add_argument(
        "--resolve",
        nargs="?",
        const="",
        metavar="VERSION",
        help="解析版本号（可给显式版本，优先于 version 文件与 POM）；输出到 stdout",
    )
    parser.add_argument(
        "--verify",
        action="store_true",
        help="校验 version / POM / version.properties / 产物名 / tag 名 五处一致",
    )
    args = parser.parse_args(argv)

    if args.verify and args.resolve is not None:
        parser.error("--verify 与 --resolve 不能同时使用")

    try:
        version, source = resolve_version(ROOT, args.resolve)
    except VerifyError as exc:
        print(f"[问题] 无法解析版本：{exc}", file=sys.stderr)
        return 1

    if not args.verify:
        # 裸调用 / --resolve：输出一行版本号（供脚本与工作流消费，见 §B）
        print(version)
        return 0

    baseline = version
    print(f"版本一致性校验：基准 {baseline}（来源：{source}）")
    findings = collect_findings(ROOT, baseline)

    inconsistent = [(name, detail) for name, ok, detail in findings if not ok]
    for name, ok, detail in findings:
        mark = "✅" if ok else "❌"
        print(f"  {mark} {name}：{detail}")

    if inconsistent:
        print(f"\n[问题] 发现 {len(inconsistent)} 处版本不一致（基准 {baseline}）：")
        for name, detail in inconsistent:
            print(f"  ❌ {name}：{detail}")
        print(
            "\n提示：改版本号时必须五处同步——根 POM（含各子模块的 <parent><version>）、"
            "version 文件、资源过滤产物 version.properties、产物文件名、tag 前缀。"
        )
        return 1

    print(f"\n✅ 五处一致，实测版本号：{baseline}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
