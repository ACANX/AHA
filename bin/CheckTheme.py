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

#: 主题兜底的实现类：里面有「皮肤内部基面」清单（见检查项 7）
THEME_PAINT = VIEW_DIR / "ThemePaint.java"
#: 兜底清单的声明形状：`SKIN_SURFACE_SELECTORS = List.of(".content", ".viewport", ".corner")`
SKIN_SELECTOR_CONST = re.compile(r"SKIN_SURFACE_SELECTORS\s*=\s*List\.of\(([^)]*)\)", re.S)
#: 选择器里的类名（`.content` ⇒ `content`）
SELECTOR_CLASS = re.compile(r"\.([a-z][a-z0-9-]*)")

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


def check_skin_surface_fallback(rules: dict[str, dict[str, dict[str, str]]]) -> list[str]:
    """ThemePaint 的「皮肤内部基面」清单必须与两份表对得上（见检查项 7）。

    <p>为什么需要：输入类控件的**可见底色是皮肤内部节点画的**（{@code .text-area > .content}、
    {@code ScrollPane} 的 {@code .viewport} / {@code .corner}），而 {@code ThemePaint.themed} 只作用于
    **控件本体**。内部节点由 {@code SKIN_SURFACE_SELECTORS} 用属性 API 兜底 —— 两边一旦漂移
    （改了 CSS 选择器名 / 删了规则 / 清单写错类名），症状是**静默**的：切主题后日志区与输入框
    的底色停在旧色，而重启正常（issue #153，2026-10-15 实查）。</p>

    <p>方向刻意是**单向**的（代码清单 ⇒ 两份表）：两份表里还有大量其它内部节点
    （滚动条 {@code .thumb}、菜单 {@code .label}、{@code .tab-container} …），它们不是「基面」，
    不该被这份清单要求兜底。</p>

    :param rules: 两份表解析结果（主题 ⇒ 选择器 ⇒ 属性）
    :return: 问题清单
    """
    problems: list[str] = []
    text = THEME_PAINT.read_text(encoding="utf-8")
    match = SKIN_SELECTOR_CONST.search(text)
    if match is None:
        problems.append(
            f"{THEME_PAINT.name}：找不到 SKIN_SURFACE_SELECTORS —— 皮肤内部基面就没有兜底了"
            "（issue #153：切主题后这些区域会停在旧色）")
        return problems

    classes = sorted(set(SELECTOR_CLASS.findall(match.group(1))))
    if not classes:
        problems.append(f"{THEME_PAINT.name}：SKIN_SURFACE_SELECTORS 为空，内部基面无人兜底")
        return problems

    for theme, table in rules.items():
        for name in classes:
            hits = [selector for selector in table if ">" in selector and f".{name}" in selector]
            if not hits:
                problems.append(
                    f"aha-theme-{theme}.css：内部基面 .{name} 在表里没有对应选择器 —— "
                    f"ThemePaint.SKIN_SURFACE_SELECTORS 与样式表已经漂移（issue #153）")
                continue
            if not any("-fx-background-color" in table[selector] for selector in hits):
                problems.append(
                    f"aha-theme-{theme}.css：内部基面 .{name} 的选择器没有设 -fx-background-color —— "
                    f"兜底会把代码里的值盖上去，与样式表的本意不一致")
    return problems


def _single_selectors(table: dict[str, dict[str, str]]) -> list[tuple[str, dict[str, str]]]:
    """把「逗号并列」的选择器拆成单条，便于逐条比较权重。"""
    pairs: list[tuple[str, dict[str, str]]] = []
    for selector, decls in table.items():
        for part in selector.split(","):
            part = part.strip()
            if part:
                pairs.append((part, decls))
    return pairs


def _selector_classes(selector: str) -> tuple[str, ...]:
    """取选择器里的类名序列（忽略伪类 / 伪元素 / 组合符）。"""
    return tuple(re.findall(r"\.([A-Za-z][A-Za-z0-9_-]*)", selector))


def check_state_rules_not_shadowed(rules: dict[str, dict[str, dict[str, str]]]) -> list[str]:
    """列表「悬停 / 选中」态的背景规则必须压得过基面规则（见检查项 8）。

    <p>为什么需要（issue #227，2026-10-15 真机截图）：基面那条是 **5 个类**的选择器
    （{@code .list-view > .virtual-flow > .clipped-container > .sheet > .list-cell}），
    而 {@code .list-cell:filled:selected} 只有 1 类 + 2 伪类 ⇒ **背景色盖不上去**；
    偏偏选中态的**文字**色来自另一条更具体的选择器
    （{@code .list-cell:filled:selected .text}，1 类 + 2 伪类 + 1 类）却能生效。
    两者叠加 ⇒ 亮色主题下就是**白底白字**：ComboBox 展开后当前那一项「看不见，也没完全隐藏」。</p>

    <p>为什么没早发现：会话列表单元格是**在 Java 里显式画背景与文字色**的
    （{@code DesktopShell.SessionCell} 用 {@code ThemePaint.themed}），根本不过 CSS；
    下拉列表是第一个「颜色完全交给样式表」的 ListView，于是把这条潜伏已久的特异性问题暴露了出来。</p>

    <p>判据刻意严格（不只是「有一条深选择器」）：对**每一条**基面规则，都要求存在一条
    <b>类路径完全相同</b>、又设了 {@code -fx-background-color} 的状态规则。
    加前缀更深的规则（如 {@code .combo-box-popup > …}）只对弹出层生效，**不能**代表普通列表
    也被覆盖到 —— 这正是第一版判据放过去的那一类假阴性。</p>

    :param rules: 两份表解析结果（主题 ⇒ 选择器 ⇒ 属性）
    :return: 问题清单
    """
    problems: list[str] = []
    for theme, table in rules.items():
        singles = _single_selectors(table)
        bases: dict[tuple[str, ...], tuple[int, str]] = {}
        for selector, decls in singles:
            if ".list-cell" not in selector or ":" in selector:
                continue
            if "-fx-background-color" not in decls:
                continue
            classes = _selector_classes(selector)
            weight = len(classes)
            if classes not in bases or weight > bases[classes][0]:
                bases[classes] = (weight, selector)
        if not bases:
            continue  # 这份表没有列表基面规则，不需要检查状态覆盖
        for classes, (weight, base_selector) in sorted(bases.items()):
            for state in (":filled:hover", ":filled:selected"):
                hit = False
                for selector, decls in singles:
                    if state not in selector or "-fx-background-color" not in decls:
                        continue
                    if _selector_classes(selector) == classes:
                        hit = True
                        break
                if not hit:
                    problems.append(
                        f"aha-theme-{theme}.css：基面规则 {base_selector!r}（{weight} 个类）"
                        f"没有对应的 `{state}` 同路径规则——背景色盖不上去（特异性不足），"
                        f"文字色却另行钉死 ⇒ 「文字与背景同色」（issue #227）；"
                        f"修法：给状态规则加上与基面**完全相同**的选择器前缀")
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
                + check_generated() + check_frozen_style_constants()
                + check_skin_surface_fallback(rules) + check_state_rules_not_shadowed(rules))

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
          f"且两份 CSS 都能由模板 + Palette 逐字节重现、角色序列一致、无静态常量冻色，"
          f"皮肤内部基面兜底清单与两份表对得上）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
