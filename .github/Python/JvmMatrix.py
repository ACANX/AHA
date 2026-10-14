#!/usr/bin/env python3
"""解析并自证 JVM 构建的「腿」选择表达式（`BuildJVMArtifacts.yml` 的 `legs` 输入）。

腿 = `<平台标签>/jdk<JDK>`，例如 `linux-x64/jdk25`；`all`（或留空）= 六条腿全出。

为什么是「选腿表达式 + 静态矩阵」而不是「动态矩阵」：本仓库实测，
在 `strategy.matrix` 里用 `fromJSON(needs.<job>.outputs.matrix)` **会让该作业被静默丢弃**
（workflow run 记为 failure，但那个作业根本没被创建，日志里也没有任何解释）。
改成静态矩阵（六条腿照旧写在 YAML 里）+ 腿级 `if` 过滤后，未选中的腿是 **skipped**——
既不耗机时，也保持可读、可本地校验。判别过程见 `Docs/DevSpec/BuildSpec.md`。

用法：

    python3 .github/Python/JvmMatrix.py --legs ''                 # 输出 all
    python3 .github/Python/JvmMatrix.py --legs 'linux-x64/jdk25'  # 输出规范化后的选择
    python3 .github/Python/JvmMatrix.py --self-test               # 自检（正 / 反例）

退出码：0 = 通过；1 = 选择不合法；2 = 用法错误。
"""

from __future__ import annotations

import argparse
import sys

#: 六条腿（与 YAML 里静态矩阵逐条一致）。改这里就要同步改 YAML 与 BuildSpec。
ALL_LEGS: tuple[str, ...] = (
    "linux-x64/jdk25",
    "windows-x64/jdk25",
    "macos-arm64/jdk25",
    "linux-x64/jdk27",
    "windows-x64/jdk27",
    "macos-arm64/jdk27",
)
PLATFORM_LABELS = ("linux-x64", "windows-x64", "macos-arm64")
SUPPORTED_JDKS = (25, 27)
ALL_KEYWORD = "all"


class LegsError(Exception):
    """选腿表达式不合法。"""


def parse_legs(raw: str | None) -> str:
    """把 `legs` 输入规范化为 `all` 或逗号分隔的腿列表（按 ALL_LEGS 顺序去重）。"""
    text = (raw or "").strip()
    if not text or text == ALL_KEYWORD:
        return ALL_KEYWORD
    requested = [item.strip() for item in text.split(",") if item.strip()]
    if not requested:
        return ALL_KEYWORD
    unknown = [item for item in requested if item not in ALL_LEGS]
    if unknown:
        raise LegsError(
            f"不认识的腿 {unknown}——合法取值是 {list(ALL_LEGS)} 或 `{ALL_KEYWORD}`"
        )
    return ",".join(leg for leg in ALL_LEGS if leg in requested)


def gate_expression(legs: str) -> str:
    """给出腿级 `if` 的判断依据（供文档与自检展示，实际写在 YAML 里）。"""
    if legs == ALL_KEYWORD:
        return "全部六条腿"
    return f"包含 {legs}"


#: `--self-test` 用例：(输入, 是否应通过, 期望规范化结果或错误说明)
SELF_TEST_CASES: list[tuple[str, bool, str]] = [
    ("", True, ALL_KEYWORD),
    (ALL_KEYWORD, True, ALL_KEYWORD),
    ("linux-x64/jdk25", True, "linux-x64/jdk25"),
    ("windows-x64/jdk25", True, "windows-x64/jdk25"),
    ("windows-x64/jdk25,linux-x64/jdk25", True, "linux-x64/jdk25,windows-x64/jdk25"),
    ("macos-arm64/jdk25,linux-x64/jdk25,macos-arm64/jdk25", True,
     "linux-x64/jdk25,macos-arm64/jdk25"),
    ("ubuntu-x64/jdk25", False, "平台标签写错（现存 JVM 线是 linux-x64，不是 ubuntu-x64）"),
    ("linux-x64/jdk26", False, "JDK 越界"),
    ("linux-x64", False, "缺 JDK 段"),
    ("all,jdk25", False, "`all` 不能与具体腿混用"),
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
    parser = argparse.ArgumentParser(description="解析并自证 JVM 构建的腿选择")
    parser.add_argument("--legs", metavar="LEGS", help="腿选择（`all` 或逗号分隔；留空 = all）")
    parser.add_argument("--self-test", action="store_true", help="自检解析边界")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()
    if args.legs is None:
        parser.error("需要 --legs <选择> 或 --self-test")
    try:
        legs = parse_legs(args.legs)
    except LegsError as exc:
        print(f"[问题] {exc}", file=sys.stderr)
        return 1
    print(legs)
    return 0


if __name__ == "__main__":
    sys.exit(main())
