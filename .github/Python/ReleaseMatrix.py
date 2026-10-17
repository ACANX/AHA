#!/usr/bin/env python3
"""发布物矩阵（`.github/ReleaseMatrix.yml`）的唯一读取入口（issue #269）。

矩阵描述 **Stable 版本要发哪些资产** —— 「组合 × 平台 × 运行时」。同一份信息会被
`Release.yml`（出包与完整性自证）、三条构建工作流（腿与命名）、`bin/CheckScripts.py`
（守卫）与四篇文档消费；本脚本是**唯一**的解析入口，消费方不得再各写一套。

用法：

    python3 .github/Python/ReleaseMatrix.py --validate
    python3 .github/Python/ReleaseMatrix.py --asset-names --version 0.1.4
    python3 .github/Python/ReleaseMatrix.py --legs
    python3 .github/Python/ReleaseMatrix.py --self-test

退出码：0 = 通过；1 = 矩阵不合规；2 = 用法错误。

为什么自己解析 YAML 而不 import PyYAML：本文件在 GitHub runner（`ubuntu-latest`）、
开发机与守卫里都要能跑，多一个第三方依赖就多一处「环境不同结果不同」。
矩阵结构是固定的（两组缩进 + 行内映射），解析器只有三十来行，比引入依赖更稳。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

#: 矩阵文件（相对仓库根）。
MATRIX_FILE = Path(".github/ReleaseMatrix.yml")

#: 四个组合，顺序即发布页展示顺序。
COMBOS: tuple[str, ...] = ("desktop-jvm", "desktop-native", "cli-jvm", "cli-native")

#: 平台段（架构词统一 `aarch64`，见 issue #287）。
PLATFORMS: tuple[str, ...] = ("windows-amd64", "ubuntu-amd64", "macos-aarch64")

#: 形态 → 运行时段（JVM 线是 JDK 级别，原生线是 GraalVM 级别）。
RUNTIME_OF_FORM: dict[str, str] = {"jvm": "jdk25", "native": "graalvm25"}

#: 行内映射：`- {os: windows-amd64, runtime: jdk25}`。
LEG_RE = re.compile(r"^\s*-\s*\{\s*os:\s*([a-z0-9-]+)\s*,\s*runtime:\s*([a-z0-9]+)\s*\}\s*$")
#: 组合行：`  desktop-jvm:`。
COMBO_RE = re.compile(r"^  ([a-z0-9-]+):\s*$")


def load(path: Path = MATRIX_FILE) -> dict[str, list[dict[str, str]]]:
    """读矩阵文件，返回 `{组合: [{os, runtime}, …]}`。

    只认固定结构；结构被改坏时抛 `ValueError`（守卫要能响亮报错，不能静默跳过）。
    """
    text = path.read_text(encoding="utf-8")
    result: dict[str, list[dict[str, str]]] = {}
    current: str | None = None
    for lineno, raw in enumerate(text.splitlines(), start=1):
        line = raw.split("#", 1)[0].rstrip()
        if not line.strip():
            continue
        if line.startswith("stable:"):
            continue
        combo = COMBO_RE.match(line)
        if combo:
            current = combo.group(1)
            result[current] = []
            continue
        leg = LEG_RE.match(line)
        if leg:
            if current is None:
                raise ValueError(f"{path}:{lineno} 在组合之前出现了腿")
            result[current].append({"os": leg.group(1), "runtime": leg.group(2)})
            continue
        raise ValueError(f"{path}:{lineno} 无法解析：{raw!r}")
    return result


def validate(matrix: dict[str, list[dict[str, str]]]) -> list[str]:
    """校验矩阵：四组合齐全、每组合三平台齐全、运行时与形态匹配、无多余组合。"""
    problems: list[str] = []
    extra = sorted(set(matrix) - set(COMBOS))
    if extra:
        problems.append(f"矩阵里有未知组合：{extra}（只允许 {list(COMBOS)}）")
    for combo in COMBOS:
        if combo not in matrix:
            problems.append(f"矩阵缺少组合 {combo!r}")
            continue
        legs = matrix[combo]
        oses = [leg["os"] for leg in legs]
        if len(legs) != len(PLATFORMS):
            problems.append(f"{combo} 应有 {len(PLATFORMS)} 条腿，实际 {len(legs)} 条：{oses}")
        if len(set(oses)) != len(oses):
            problems.append(f"{combo} 的平台段有重复：{oses}")
        missing = [platform for platform in PLATFORMS if platform not in oses]
        if missing:
            problems.append(f"{combo} 缺少平台：{missing}")
        unknown = [platform for platform in oses if platform not in PLATFORMS]
        if unknown:
            problems.append(f"{combo} 出现未知平台：{unknown}")
        form = combo.rsplit("-", 1)[1]
        expected = RUNTIME_OF_FORM.get(form)
        for leg in legs:
            if expected and leg["runtime"] != expected:
                problems.append(
                    f"{combo}（{leg['os']}）的 runtime 应为 {expected}，实际 {leg['runtime']}"
                )
    return problems


def asset_name(combo: str, version: str, platform: str, runtime: str) -> str:
    """按文法生成资产名：`aha-<product>-<form>-<version>-<os>-<arch>-<runtime>.zip`。"""
    product, form = combo.split("-", 1)
    return f"aha-{product}-{form}-{version}-{platform}-{runtime}.zip"


def asset_names(version: str, matrix: dict[str, list[dict[str, str]]]) -> list[str]:
    """Stable 版本的全部资产名（顺序：组合 → 平台）。"""
    names: list[str] = []
    for combo in COMBOS:
        for leg in matrix.get(combo, []):
            names.append(asset_name(combo, version, leg["os"], leg["runtime"]))
    return names


def legs(matrix: dict[str, list[dict[str, str]]]) -> list[dict[str, str]]:
    """展开成逐腿清单（`combo` / `product` / `form` / `os` / `runtime`），供工作流与守卫消费。"""
    rows: list[dict[str, str]] = []
    for combo in COMBOS:
        product, form = combo.split("-", 1)
        for leg in matrix.get(combo, []):
            rows.append({"combo": combo, "product": product, "form": form,
                         "os": leg["os"], "runtime": leg["runtime"]})
    return rows


def _self_test() -> int:
    """自检：正例全过；三类反例必须判红。"""
    good = {combo: [{"os": platform, "runtime": RUNTIME_OF_FORM[combo.rsplit('-', 1)[1]]}
                    for platform in PLATFORMS]
            for combo in COMBOS}
    failures: list[str] = []

    if validate(good):
        failures.append(f"正例被判红：{validate(good)}")

    def copy(matrix: dict[str, list[dict[str, str]]]) -> dict[str, list[dict[str, str]]]:
        return {key: [dict(leg) for leg in value] for key, value in matrix.items()}

    missing_platform = copy(good)
    missing_platform["cli-jvm"] = missing_platform["cli-jvm"][:-1]
    if not validate(missing_platform):
        failures.append("反例（缺平台）没有判红")

    wrong_runtime = copy(good)
    wrong_runtime["desktop-native"][0]["runtime"] = "jdk25"
    if not validate(wrong_runtime):
        failures.append("反例（runtime 与形态不匹配）没有判红")

    extra_combo = copy(good)
    extra_combo["desktop-debug"] = extra_combo["desktop-jvm"]
    if not validate(extra_combo):
        failures.append("反例（未知组合）没有判红")

    expected = [
        "aha-desktop-jvm-0.1.4-windows-amd64-jdk25.zip",
        "aha-desktop-jvm-0.1.4-ubuntu-amd64-jdk25.zip",
        "aha-desktop-jvm-0.1.4-macos-aarch64-jdk25.zip",
        "aha-desktop-native-0.1.4-windows-amd64-graalvm25.zip",
        "aha-desktop-native-0.1.4-ubuntu-amd64-graalvm25.zip",
        "aha-desktop-native-0.1.4-macos-aarch64-graalvm25.zip",
        "aha-cli-jvm-0.1.4-windows-amd64-jdk25.zip",
        "aha-cli-jvm-0.1.4-ubuntu-amd64-jdk25.zip",
        "aha-cli-jvm-0.1.4-macos-aarch64-jdk25.zip",
        "aha-cli-native-0.1.4-windows-amd64-graalvm25.zip",
        "aha-cli-native-0.1.4-ubuntu-amd64-graalvm25.zip",
        "aha-cli-native-0.1.4-macos-aarch64-graalvm25.zip",
    ]
    if asset_names("0.1.4", good) != expected:
        failures.append(f"资产名与期望不符：{asset_names('0.1.4', good)}")

    if failures:
        print("\n".join(f"❌ {failure}" for failure in failures))
        return 1
    print("✅ 12 条自检全部符合预期")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="发布物矩阵读取与校验（issue #269）")
    parser.add_argument("--validate", action="store_true", help="校验矩阵结构")
    parser.add_argument("--asset-names", action="store_true", help="输出 Stable 的全部资产名")
    parser.add_argument("--legs", action="store_true", help="输出逐腿清单（JSON）")
    parser.add_argument("--version", default="", help="与 --asset-names 配合的版本号")
    parser.add_argument("--self-test", action="store_true", help="自检")
    args = parser.parse_args(argv)

    if args.self_test:
        return _self_test()

    try:
        matrix = load()
    except (OSError, ValueError) as error:
        print(f"❌ 读取 {MATRIX_FILE} 失败：{error}")
        return 1

    problems = validate(matrix)
    if problems:
        print("\n".join(f"❌ {problem}" for problem in problems))
        return 1
    if args.validate:
        print(f"✅ 矩阵合规：{len(COMBOS)} 个组合 × {len(PLATFORMS)} 个平台 = "
              f"{len(COMBOS) * len(PLATFORMS)} 份资产")
        return 0
    if args.asset_names:
        if not args.version:
            print("❌ --asset-names 必须配合 --version")
            return 2
        for name in asset_names(args.version, matrix):
            print(name)
        return 0
    if args.legs:
        import json
        print(json.dumps(legs(matrix), ensure_ascii=False))
        return 0
    parser.print_help()
    return 2


if __name__ == "__main__":
    sys.exit(main())
