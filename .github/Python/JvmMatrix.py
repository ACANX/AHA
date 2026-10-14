#!/usr/bin/env python3
"""校验并规范化 JVM 构建矩阵（`BuildJVMArtifacts.yml` 的 `matrix` 输入）。

矩阵 = JSON 数组，每条腿形如 `{"os":…,"label":…,"jdk":…,"upload_cli":…}`；留空 = 默认六条腿。

为什么是「选腿表达式 + 静态矩阵」而不是「动态矩阵」：本仓库实测，
在 `strategy.matrix` 里用 `fromJSON(needs.<job>.outputs.matrix)` **会让该作业被静默丢弃**
（workflow run 记为 failure，但那个作业根本没被创建，日志里也没有任何解释）。
改成静态矩阵（六条腿照旧写在 YAML 里）+ 腿级 `if` 过滤后，未选中的腿是 **skipped**——
既不耗机时，也保持可读、可本地校验。判别过程见 `Docs/DevSpec/BuildSpec.md`。

用法：

    python3 .github/Python/JvmMatrix.py --validate ''                 # 用默认六条腿
    python3 .github/Python/JvmMatrix.py --validate '[{"os":"ubuntu-latest",…}]'  # 用给定矩阵
    python3 .github/Python/JvmMatrix.py --self-test               # 自检（正 / 反例）

退出码：0 = 通过；1 = 选择不合法；2 = 用法错误。
"""

from __future__ import annotations

import argparse
import json
import sys

#: 默认矩阵 = 三平台 × JDK 25/27 六条腿（与改造前逐条一致；YAML 里是同一个 JSON 字面量）。
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
    """矩阵不合法（非法 JSON、空数组、字段缺失、取值越界）。"""


def parse_matrix(raw: str | None) -> list[dict]:
    """解析矩阵；空输入返回默认六条腿。

    自证的边界：合法 JSON、非空、字段齐全、`jdk` 只认 25/27、`label` 只认三种、`os` 非空。
    矩阵写错的表现是「run 红了却没有作业、也没有日志」——所以宁可在这里响亮失败。
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
            raise MatrixError(
                f"第 {index} 条腿字段不对：缺少 {sorted(LEG_FIELDS - set(leg))}、"
                f"多出 {sorted(set(leg) - LEG_FIELDS)}"
            )
        if not str(leg["os"]).strip():
            raise MatrixError(f"第 {index} 条腿的 os 为空")
        if leg["label"] not in PLATFORM_LABELS:
            raise MatrixError(
                f"第 {index} 条腿的 label {leg['label']!r} 不认识（只认 {list(PLATFORM_LABELS)}）"
            )
        if leg["jdk"] not in SUPPORTED_JDKS:
            raise MatrixError(f"第 {index} 条腿的 jdk {leg['jdk']!r} 不支持（只认 {list(SUPPORTED_JDKS)}）")
        if not isinstance(leg["upload_cli"], bool):
            raise MatrixError(f"第 {index} 条腿的 upload_cli 必须是布尔值")
    return data


def describe(matrix: list[dict]) -> str:
    """一行人类可读摘要（进日志与 run 摘要）。"""
    return (f"矩阵自证通过：{len(matrix)} 条腿 -> "
            + ", ".join(f"{leg['label']}/jdk{leg['jdk']}" for leg in matrix))


#: `--self-test` 用例：(输入, 是否应通过, 说明)
SELF_TEST_CASES: list[tuple[str, bool, str]] = [
    ("", True, "空输入 = 默认六条腿"),
    (json.dumps(DEFAULT_MATRIX), True, "显式给默认矩阵"),
    ('[{"os":"ubuntu-latest","label":"linux-x64","jdk":25,"upload_cli":true}]', True,
     "快照线的收窄矩阵（1 条腿）"),
    ('[{"os":"ubuntu-latest","label":"linux-x64","jdk":25,"upload_cli":true},'
     '{"os":"windows-latest","label":"windows-x64","jdk":25,"upload_cli":false}]', True,
     "JVM 线快照的两条腿（linux + windows，仅 JDK 25）"),
    ("[]", False, "空数组"),
    ("{}", False, "不是数组"),
    ("not json", False, "非法 JSON"),
    ('[{"os":"ubuntu-latest","label":"linux-x64","jdk":25}]', False, "缺 upload_cli 字段"),
    ('[{"os":"ubuntu-latest","label":"ubuntu-x64","jdk":25,"upload_cli":true}]', False,
     "label 写错（现存 JVM 线是 linux-x64，不是 ubuntu-x64）"),
    ('[{"os":"ubuntu-latest","label":"linux-x64","jdk":26,"upload_cli":true}]', False, "jdk 越界"),
    ('[{"os":"","label":"linux-x64","jdk":25,"upload_cli":true}]', False, "os 为空"),
]


def self_test() -> int:
    """自检解析边界。"""
    print("JVM 腿选择自检：")
    failed = 0
    for raw, should_pass, note in SELF_TEST_CASES:
        try:
            legs = parse_legs(raw)
            ok, detail = True, legs
        except LegsError as exc:
            ok, detail = False, str(exc)
        # `all` 与具体腿混用：本实现按「不认识」拦下，属于期望的失败
        good = ok == should_pass
        failed += 0 if good else 1
        print(
            f"  {'✅' if good else '❌'} {raw!r} → {'通过' if ok else '失败'}"
            f"（应为{'通过' if should_pass else '失败'}）—— {note}；{detail}"
        )
    if failed:
        print(f"\n[问题] {failed} / {len(SELF_TEST_CASES)} 条不符合预期")
        return 1
    print(f"\n✅ {len(SELF_TEST_CASES)} 条自检全部符合预期")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="校验并规范化 JVM 构建矩阵")
    parser.add_argument("--validate", metavar="JSON", help="校验矩阵（空 = 默认六条腿）")
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
    return 0


if __name__ == "__main__":
    sys.exit(main())
