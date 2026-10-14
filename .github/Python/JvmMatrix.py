#!/usr/bin/env python3
"""解析并自证 JVM 构建矩阵（`BuildJVMArtifacts.yml` 的 `matrix` 输入）。

为什么单独一个脚本，而不是写在 YAML 的 `run:` 里：工作流块标量里嵌 Python
有两重坑——① 代码行顶格会被 YAML 当成新的键（`could not find expected ':'`）；
② YAML 块标量 + 管道 + 重定向叠加时行尾空格与缩进会被吃掉，代码被当成 shell 命令执行。
两坑都已实际踩到（见 `Docs/Troubleshooting/`）。放成仓库脚本后可以本地跑、被
`bin/CheckScripts.py` 检查，也能被快照线复用。

用法（`--validate` 输出两行：第 1 行给人看，第 2 行是压缩后的 JSON）：

    python3 .github/Python/JvmMatrix.py --validate ''                 # 用默认六条腿
    python3 .github/Python/JvmMatrix.py --validate '[{"os":"ubuntu-latest",...}]'
    python3 .github/Python/JvmMatrix.py --self-test                   # 自检（正 / 反例）

退出码：0 = 通过；1 = 矩阵不合法；2 = 用法错误。
"""

from __future__ import annotations

import argparse
import json
import sys

#: 默认矩阵 = 三平台 × JDK 25/27 六条腿（与改造前的 `include:` 逐条一致）。
#: 改这里就等于改 dev 线的出包范围，改动应当同步 `Docs/DevSpec/BuildSpec.md`。
DEFAULT_MATRIX: list[dict] = [
    {"os": "ubuntu-latest", "label": "linux-x64", "jdk": 25, "upload_cli": True},
    {"os": "windows-latest", "label": "windows-x64", "jdk": 25, "upload_cli": False},
    {"os": "macos-latest", "label": "macos-arm64", "jdk": 25, "upload_cli": False},
    {"os": "ubuntu-latest", "label": "linux-x64", "jdk": 27, "upload_cli": True},
    {"os": "windows-latest", "label": "windows-x64", "jdk": 27, "upload_cli": False},
    {"os": "macos-latest", "label": "macos-arm64", "jdk": 27, "upload_cli": False},
]

LEG_FIELDS = {"os", "label", "jdk", "upload_cli"}
PLATFORM_LABELS = ("linux-x64", "windows-x64", "macos-arm64")
SUPPORTED_JDKS = (25, 27)


class MatrixError(Exception):
    """矩阵不合法（含空输入、字段缺失、取值越界）。"""


def parse_matrix(raw: str | None) -> list[dict]:
    """解析矩阵；空输入返回默认矩阵。

    自证的内容是**能在运行前判定的部分**：非空、字段齐全、`jdk` 与 `label` 取值在认识范围内。
    矩阵写错的表现是「某个平台静默没出包」，所以宁可在这里失败，也不要等发布页少了资产才发现。
    """
    text = (raw or "").strip()
    if not text:
        return list(DEFAULT_MATRIX)
    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:
        raise MatrixError(f"矩阵不是合法 JSON：{exc}") from exc
    if not isinstance(data, list) or not data:
        raise MatrixError("矩阵必须是非空 JSON 数组")
    for index, leg in enumerate(data, 1):
        if not isinstance(leg, dict):
            raise MatrixError(f"第 {index} 条腿不是对象：{leg!r}")
        if set(leg) != LEG_FIELDS:
            missing = LEG_FIELDS - set(leg)
            extra = set(leg) - LEG_FIELDS
            raise MatrixError(
                f"第 {index} 条腿的字段不对：缺少 {sorted(missing)}、多出 {sorted(extra)}"
            )
        if not str(leg["os"]).strip():
            raise MatrixError(f"第 {index} 条腿的 os 为空")
        if leg["label"] not in PLATFORM_LABELS:
            raise MatrixError(
                f"第 {index} 条腿的 label {leg['label']!r} 不认识（只认 {list(PLATFORM_LABELS)}）"
            )
        if leg["jdk"] not in SUPPORTED_JDKS:
            raise MatrixError(
                f"第 {index} 条腿的 jdk {leg['jdk']!r} 不支持（只认 {list(SUPPORTED_JDKS)}）"
            )
        if not isinstance(leg["upload_cli"], bool):
            raise MatrixError(f"第 {index} 条腿的 upload_cli 必须是布尔值")
    return data


def describe(matrix: list[dict]) -> str:
    """给一行人类可读的摘要（进 run 摘要与日志）。"""
    legs = ", ".join(f"{leg['label']}/jdk{leg['jdk']}" for leg in matrix)
    return f"矩阵自证通过：{len(matrix)} 条腿 -> {legs}"


#: `--self-test` 用例：(输入, 是否应通过, 说明)
SELF_TEST_CASES: list[tuple[str, bool, str]] = [
    ("", True, "空输入 = 默认六条腿"),
    (json.dumps(DEFAULT_MATRIX), True, "显式给默认矩阵"),
    ('[{"os":"ubuntu-latest","label":"linux-x64","jdk":25,"upload_cli":true}]', True,
     "快照线的收窄矩阵（1 条腿）"),
    ('[]', False, "空数组"),
    ('{}', False, "不是数组"),
    ('not json', False, "非法 JSON"),
    ('[{"os":"ubuntu-latest","label":"linux-x64","jdk":25}]', False, "缺 upload_cli 字段"),
    ('[{"os":"ubuntu-latest","label":"ubuntu-x64","jdk":25,"upload_cli":true}]', False,
     "label 写错（ubuntu-x64 不是现有 JVM 线的平台标签）"),
    ('[{"os":"ubuntu-latest","label":"linux-x64","jdk":26,"upload_cli":true}]', False,
     "jdk 越界"),
    ('[{"os":"","label":"linux-x64","jdk":25,"upload_cli":true}]', False, "os 为空"),
]


def self_test() -> int:
    """自检解析边界。"""
    print("JVM 构建矩阵自检：")
    failed = 0
    for raw, should_pass, note in SELF_TEST_CASES:
        try:
            matrix = parse_matrix(raw)
            ok, detail = True, describe(matrix)
        except MatrixError as exc:
            ok, detail = False, str(exc)
        good = ok == should_pass
        failed += 0 if good else 1
        shown = raw if len(raw) <= 58 else raw[:55] + "..."
        print(
            f"  {'✅' if good else '❌'} {shown!r} → {'通过' if ok else '失败'}"
            f"（应为{'通过' if should_pass else '失败'}）—— {note}；{detail}"
        )
    if failed:
        print(f"\n[问题] {failed} / {len(SELF_TEST_CASES)} 条不符合预期")
        return 1
    print(f"\n✅ {len(SELF_TEST_CASES)} 条自检全部符合预期")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="解析并自证 JVM 构建矩阵")
    parser.add_argument("--validate", metavar="JSON", help="校验矩阵并输出（空 = 默认六条腿）")
    parser.add_argument("--self-test", action="store_true", help="自检解析边界")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()
    if args.validate is None:
        parser.error("需要 --validate <JSON> 或 --self-test")
    try:
        matrix = parse_matrix(args.validate)
    except MatrixError as exc:
        print(f"[问题] {exc}", file=sys.stderr)
        return 1
    print(describe(matrix))
    print(json.dumps(matrix, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
