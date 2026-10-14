#!/usr/bin/env python3
"""AHA 主题 CSS 生成器：把 `Palette.java` 的色表代入模板，产出租两份主题补丁表。

为什么要有它（issue #153 的实质要求「收敛为一处色源」）
------------------------------------------------------

在这之前，颜色有**两处手写来源**：`Palette.java` 的常量，以及两份 CSS 里手抄的字面色号。
两者靠 `bin/CheckTheme.py` 的「色号必须属于色表」兜着，但那只是**事后核对**：
想改一个颜色，仍要记得改两个地方，而且没人能一眼看出 CSS 里第 37 行的 `#E4E4E4`
到底是「前景色」还是别的什么。

现在：**手写色值只允许出现在 `Palette.java`**；两份 CSS 变成派生产物，
由本脚本从 `aha-desktop/src/theme/*.template.css`（含 `%ROLE%` 占位符）生成。
`bin/CheckTheme.py` 会重新生成一遍并**逐字节比对**，改色不改生成物会被打回。

为什么不直接用 CSS 变量 / looked-up color「只用一处」
----------------------------------------------------

那是本 issue 最初写的 A 方案，但被仓库里的既有证据否掉了：issue #49 第四轮，
真机 + 原生镜像下**自定义 color 查表不可靠**——声明里查不到值，CSS 会**丢掉整条声明**，
文字色回落成 modena 默认黑（暗色下就是一片黑字）。两份 CSS 的头部注释记着这条结论：
「这份表里不允许出现任何变量查表 / derive() / linear-gradient() / ladder()」。
于是保留字面量、把「唯一色源」挪到生成这一步来实现。

用法
----

    python3 bin/GenThemeCss.py            # 生成（覆盖两份 CSS）
    python3 bin/GenThemeCss.py --check    # 只校验，不一致时退出 1（CheckTheme / CI 用）

退出码：0 = 生成完成或校验通过；1 = 模板有问题或与生成物不一致。
"""

from __future__ import annotations

import argparse
import difflib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

PALETTE = (ROOT / "aha-desktop" / "src" / "main" / "java" / "com" / "acanx"
           / "module" / "aha" / "desktop" / "view" / "Palette.java")
CSS_DIR = (ROOT / "aha-desktop" / "src" / "main" / "resources" / "com" / "acanx"
           / "module" / "aha" / "desktop" / "view" / "css")
TEMPLATE_DIR = ROOT / "aha-desktop" / "src" / "theme"

# 主题 →（Palette 里的色表方法, 模板文件名, 生成物文件名）
THEMES = {
    "dark": ("applyDark", "aha-theme-dark.template.css", "aha-theme-dark.css"),
    "light": ("applyLight", "aha-theme-light.template.css", "aha-theme-light.css"),
}

# Palette 色表方法体里的赋值，例如：FOREGROUND = "#E4E4E4";
ASSIGN = re.compile(r"^\s*([A-Z][A-Z0-9_]*)\s*=\s*\"(#[0-9A-Fa-f]{6})\"\s*;", re.M)
METHOD = "private static void {name}() {{"
TOKEN = re.compile(r"%([A-Z][A-Z0-9_]*)%")
HEX = re.compile(r"#[0-9A-Fa-f]{6}")
COMMENT = re.compile(r"/\*.*?\*/", re.S)

# 生成物顶部声明。刻意**不带时间戳**：否则每次生成都有 diff，比对就废了。
NOTICE = ("/*\n"
          " * ⚠ 本文件由 `bin/GenThemeCss.py` 从 `Palette.java` **生成**，不要手改。\n"
          " *\n"
          " *   手写色源只有一处：aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/Palette.java\n"
          " *   模板（含 %ROLE% 占位符）：{template}\n"
          " *   校验：python3 bin/CheckTheme.py（重新生成并逐字节比对）\n"
          " *\n"
          " * 为什么保留字面量、不用 CSS 变量查表：见本文件下方头部注释（issue #49 第四轮）。\n"
          " */\n"
          "\n")


def theme_values(method: str) -> dict[str, str]:
    """取某个主题色表：角色名 → 十六进制值。"""
    text = PALETTE.read_text(encoding="utf-8")
    start = text.find(METHOD.format(name=method))
    if start < 0:
        raise SystemExit(f"✗ 在 Palette.java 里找不到色表方法 {method}()")
    body = text[start:]
    end = body.find("\n    }")
    if end < 0:
        raise SystemExit(f"✗ 色表方法 {method}() 的结尾没找到")
    return {name: value.upper() for name, value in ASSIGN.findall(body[:end])}


def render(template: str, values: dict[str, str], *, where: str) -> str:
    """把模板里的 %ROLE% 换成色表中的值。"""
    unknown = sorted({token for token in TOKEN.findall(template) if token not in values})
    if unknown:
        raise SystemExit(f"✗ {where} 用了色表里没有的角色：{unknown}")

    # 模板里不允许直接出现色号——那等于在「唯一色源」之外又开了一处手写来源。
    leftover = sorted(set(HEX.findall(COMMENT.sub("", template))))
    if leftover:
        raise SystemExit(f"✗ {where} 的模板里有字面色号（应当写成 %角色%）：{leftover}")

    rendered = TOKEN.sub(lambda m: values[m.group(1)], template)
    return NOTICE.format(template=where) + rendered


def role_stream(template: str) -> list[str]:
    """模板里的角色序列（按出现顺序，忽略注释）。两份模板必须完全一致。"""
    return TOKEN.findall(COMMENT.sub("", template))


def build() -> dict[Path, str]:
    """生成全部产物：目标路径 → 内容。"""
    products: dict[Path, str] = {}
    for theme, (method, template_name, output_name) in THEMES.items():
        template_path = TEMPLATE_DIR / template_name
        if not template_path.exists():
            raise SystemExit(f"✗ 缺少模板：{template_path}")
        products[CSS_DIR / output_name] = render(
            template_path.read_text(encoding="utf-8"),
            theme_values(method),
            where=f"aha-desktop/src/theme/{template_name}",
        )
    return products


def check_parity() -> list[str]:
    """跨主题角色流比对：两份模板必须**用同样的角色、按同样的顺序**上色。

    这一条是「同族控件在两种主题下逐项一致」的机器证明：只要角色流一致，
    就不可能出现「某控件在暗色下用前景色、在亮色下用边框色」这种错位。
    """
    streams = {}
    for theme, (_method, template_name, _output) in THEMES.items():
        path = TEMPLATE_DIR / template_name
        if not path.exists():
            return [f"缺少模板：{path}"]
        streams[theme] = role_stream(path.read_text(encoding="utf-8"))
    dark, light = streams["dark"], streams["light"]
    if dark == light:
        return []
    problems = [f"两份模板的角色序列不一致（暗色 {len(dark)} 个 / 亮色 {len(light)} 个）"]
    for index, (a, b) in enumerate(zip(dark, light), start=1):
        if a != b:
            problems.append(f"  第 {index} 个角色：暗色 {a} ≠ 亮色 {b}")
            break
    if len(dark) != len(light):
        longer = "暗色" if len(dark) > len(light) else "亮色"
        problems.append(f"  {longer}多出：{(dark if longer == '暗色' else light)[min(len(dark), len(light)):]}")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description="从 Palette.java 生成两份主题 CSS")
    parser.add_argument("--check", action="store_true", help="只校验（不写文件）")
    args = parser.parse_args()

    problems = check_parity()
    products = build()

    if args.check:
        for path, expected in products.items():
            actual = path.read_text(encoding="utf-8") if path.exists() else ""
            if actual != expected:
                problems.append(f"{path.relative_to(ROOT)} 与模板 / Palette 不一致，"
                                f"请执行：python3 bin/GenThemeCss.py")
                diff = list(difflib.unified_diff(
                    actual.splitlines(), expected.splitlines(),
                    fromfile=f"{path.name}（当前）", tofile=f"{path.name}（应当生成）", lineterm=""))
                problems.extend(f"  {line}" for line in diff[:20])
        if problems:
            print(f"❌ 主题 CSS 生成检查发现 {len(problems)} 处问题：")
            for problem in problems:
                print(f"  - {problem}")
            return 1
        print("✅ 主题 CSS 生成检查通过（两份文件都能由模板 + Palette 逐字节重现）")
        return 0

    if problems:
        print(f"❌ 无法生成，先修模板：")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    for path, content in products.items():
        path.write_text(content, encoding="utf-8")
        print(f"  已生成 {path.relative_to(ROOT)}（{len(content.splitlines())} 行）")
    print("✅ 生成完成。色源唯一：改颜色请改 Palette.java，然后重跑本脚本。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
