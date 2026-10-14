#!/usr/bin/env python3
"""AHA 主题色一致性检查。

检查项：

  1. 两份主题补丁表（`aha-theme-{dark,light}.css`）的**选择器集合一致**——
     某主题少一条 ⇒ 该主题下这些控件没有补丁，会回落到 modena 默认值
     （issue #49 的「暗色下一片黑字」、issue #153 的「底色不一致」都是这一类）；
  2. 两份表里**同一选择器声明的属性集合一致**（值当然不同）——
     防止「只在一边加了规则」，那是上一条的隐蔽变体；
  3. 每个十六进制色号都必须**属于该主题的色表**
     （`Palette.applyDark()` / `applyLight()`）——禁止「野生色号」。
     颜色只能有一个来源：`Palette` 常量；CSS 里的字面量是它的投影。
     一旦有人在 CSS 里手写一个色号，两份表就会开始各自漂移。
  4. 两份 CSS **能由模板 + `Palette` 逐字节重现**（issue #153 的「色源唯一」）：
     生成器 `bin/GenThemeCss.py` 拿 `aha-desktop/src/theme/*.template.css` 里的
     `%角色%` 代入色表；本项就是重新生成一遍再逐字节比对。
     手改 CSS（而不是改 `Palette.java` + 重跑生成器）会被这一项打回。
  5. 两份模板的**角色序列完全一致**：同一个控件在暗色 / 亮色下必须用同一个**角色**上色
     （值当然不同）。这是「同族控件在两主题下逐项一致」的机器证明——
     能挡住「暗色下用前景色、亮色下却用边框色」这类错位。
  6. **不得把颜色写进 `static final String`**：常量在**类加载时**求值，颜色会被冻在那一刻，
     之后切主题再也不变。2026-10-14 实测的「切了主题、输入框 / 供应商对话框的部分组件仍是旧色」
     就是这个：`ProviderDialog.SECTION_TITLE_STYLE`、`SettingsDialog.MUTED_STYLE`、
     `ApprovalDialog.SECTION_STYLE` 全是静态常量。要一次性上色就写成方法 / lambda，
     并用 `ThemePaint.themedForever(...)` 登记重刷。

用法：

    python3 bin/CheckTheme.py            # 从仓库根执行
    python3 bin/CheckTheme.py --verbose

退出码：0 = 全部通过；1 = 存在问题。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import GenThemeCss

ROOT = Path(__file__).resolve().parent.parent

PALETTE = (ROOT / "aha-desktop" / "src" / "main" / "java" / "com" / "acanx"
           / "module" / "aha" / "desktop" / "view" / "Palette.java")
CSS_DIR = (ROOT / "aha-desktop" / "src" / "main" / "resources" / "com" / "acanx"
           / "module" / "aha" / "desktop" / "view" / "css")
VIEW_DIR = (ROOT / "aha-desktop" / "src" / "main" / "java" / "com" / "acanx"
            / "module" / "aha" / "desktop" / "view")

# 静态样式常量（颜色被冻在类加载时——见检查项 6）
STYLE_CONST = re.compile(r"static final String\s+(\w+)\s*=")

# 主题 → Palette 里对应的方法（该主题的色表）
THEMES = {"dark": "applyDark", "light": "applyLight"}

HEX = re.compile(r"#[0-9A-Fa-f]{6}")
RULE = re.compile(r"([^{}]+)\{([^{}]*)\}")
COMMENT = re.compile(r"/\*.*?\*/", re.S)


def parse(path: Path) -> dict[str, dict[str, str]]:
    """解析补丁表：选择器 → {属性: 值}。"""
    text = COMMENT.sub("", path.read_text(encoding="utf-8"))
    rules: dict[str, dict[str, str]] = {}
    for match in RULE.finditer(text):
        selector = " ".join(match.group(1).split())
        decls: dict[str, str] = {}
        for declaration in match.group(2).split(";"):
            if ":" in declaration:
                key, value = declaration.split(":", 1)
                decls[key.strip()] = value.strip()
        rules.setdefault(selector, {}).update(decls)
    return rules


def theme_palette(method: str) -> set[str] | None:
    """取某个主题色表（`Palette.applyXxx()`）里出现过的色值集合。"""
    text = PALETTE.read_text(encoding="utf-8")
    match = re.search(r"private static void " + method + r"\(\)\s*\{(.*?)\n    \}", text, re.S)
    if match is None:
        return None
    return {color.upper() for color in HEX.findall(match.group(1))}


def check_selectors(rules: dict[str, dict[str, dict[str, str]]]) -> list[str]:
    problems: list[str] = []
    names = list(rules)
    for selector in sorted(set(rules[names[0]]) - set(rules[names[1]])):
        problems.append(f"选择器 {selector!r} 只存在于 aha-theme-{names[0]}.css，"
                        f"另一主题下这些控件没有补丁（会回落到 modena 默认值）")
    for selector in sorted(set(rules[names[1]]) - set(rules[names[0]])):
        problems.append(f"选择器 {selector!r} 只存在于 aha-theme-{names[1]}.css，"
                        f"另一主题下这些控件没有补丁（会回落到 modena 默认值）")
    return problems


def check_properties(rules: dict[str, dict[str, dict[str, str]]]) -> list[str]:
    problems: list[str] = []
    left, right = rules[list(rules)[0]], rules[list(rules)[1]]
    for selector in sorted(set(left) & set(right)):
        only_left = set(left[selector]) - set(right[selector])
        only_right = set(right[selector]) - set(left[selector])
        if only_left:
            problems.append(f"选择器 {selector!r} 的属性 {sorted(only_left)} 只在一个主题里声明")
        if only_right:
            problems.append(f"选择器 {selector!r} 的属性 {sorted(only_right)} 只在一个主题里声明")
    return problems


def check_colors(rules: dict[str, dict[str, dict[str, str]]],
                 palettes: dict[str, set[str]]) -> list[str]:
    problems: list[str] = []
    for theme, table in rules.items():
        palette = palettes[theme]
        used: set[str] = set()
        for decls in table.values():
            for value in decls.values():
                used.update(color.upper() for color in HEX.findall(value))
        for color in sorted(used - palette):
            problems.append(f"aha-theme-{theme}.css 用了色号 {color}，"
                            f"但它不在该主题的色表（Palette.{THEMES[theme]}()）里——"
                            f"颜色只能来自 Palette 常量，禁止在 CSS 里手写色号")
    return problems


def check_generated() -> list[str]:
    """两份 CSS 必须能由模板 + Palette 逐字节重现（色源唯一的落点）。"""
    try:
        products = GenThemeCss.build()
    except SystemExit as error:
        return [str(error)]
    problems: list[str] = list(GenThemeCss.check_parity())
    for path, expected in products.items():
        actual = path.read_text(encoding="utf-8") if path.exists() else ""
        if actual != expected:
            problems.append(
                f"{path.relative_to(ROOT)} 不能由模板 + Palette 重现——"
                f"改颜色请改 Palette.java，再执行 python3 bin/GenThemeCss.py")
    return problems


def _initializer(text: str, start: int) -> str:
    """取赋值语句的初始化文本：跳过字符串字面量，遇到顶层分号 / 右花括号结束。"""
    depth = 0
    index = start
    while index < len(text):
        if text.startswith('"""', index):
            index = text.find('"""', index + 3)
            index = len(text) if index < 0 else index + 3
            continue
        char = text[index]
        if char == '"':
            index += 1
            while index < len(text) and text[index] != '"':
                index += 2 if text[index] == "\\" else 1
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        elif (char == ";" or char == "}") and depth <= 0:
            break
        index += 1
    return text[start:index]


def check_frozen_style_constants() -> list[str]:
    """禁止把颜色放进 `static final String`（见检查项 6）。"""
    problems: list[str] = []
    for path in sorted(VIEW_DIR.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        for match in STYLE_CONST.finditer(text):
            initializer = _initializer(text, match.end())
            if "Palette." in initializer:
                line = text.count("\n", 0, match.start()) + 1
                problems.append(
                    f"{path.name}:{line} 的静态常量 {match.group(1)} 引用了 Palette（"
                    f"{initializer.strip()[:50]}）——常量在**类加载时**求值，颜色会被冻在那一刻，"
                    f"切主题后不再变；请改成方法 / lambda 并用 ThemePaint.themedForever 登记重刷")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description="AHA 主题色一致性检查")
    parser.add_argument("--verbose", action="store_true", help="打印逐项统计")
    args = parser.parse_args()

    missing = [p for p in (PALETTE, *(CSS_DIR / f"aha-theme-{t}.css" for t in THEMES)) if not p.exists()]
    if missing:
        for path in missing:
            print(f"❌ 缺少文件：{path}")
        return 1

    rules = {theme: parse(CSS_DIR / f"aha-theme-{theme}.css") for theme in THEMES}
    palettes: dict[str, set[str]] = {}
    for theme, method in THEMES.items():
        palette = theme_palette(method)
        if palette is None:
            print(f"❌ 在 Palette.java 里找不到色表方法 {method}()")
            return 1
        palettes[theme] = palette

    problems = (check_selectors(rules) + check_properties(rules) + check_colors(rules, palettes)
                + check_generated() + check_frozen_style_constants())

    if args.verbose:
        for theme, table in rules.items():
            colors = {c.upper() for decls in table.values()
                      for value in decls.values() for c in HEX.findall(value)}
            print(f"  {theme:5} 规则 {len(table):>2} 条，用色 {len(colors):>2} 个，"
                  f"色表 {len(palettes[theme])} 个")

    if problems:
        print(f"❌ 主题色一致性检查发现 {len(problems)} 处问题：")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print("✅ 主题色一致性检查通过"
          f"（两份表的选择器 / 属性集合一致，色号全部来自各自主题的 Palette 色表，"
          f"且两份 CSS 都能由模板 + Palette 逐字节重现、角色序列一致、无静态常量冻色）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
