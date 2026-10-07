#!/usr/bin/env python3
"""AHA 启动标志（像素风）生成器。

设计文档：Docs/Design/PixelLogoDesign.md

像素风不是把 Logo.svg 缩小采样出来的，而是**按格摆**出来的：
光线走 Bresenham、灯泡用整数圆、AHA 与笑脸直接贴点阵，全部硬边界、无渐变。
采样会把 5x7 点阵的字糊掉、把 1 像素宽的光线抹平。

用法：
    python3 bin/GenPixelLogo.py --art      # 打印 48x48 与 24x24 网格（肉眼校形）
    python3 bin/GenPixelLogo.py --java     # 打印可粘贴回 StartupPixelLogo.java 的数组
    python3 bin/GenPixelLogo.py --verify   # 校验 Java 常量与生成器一致（漂移检测）

退出码：0 = 正常；1 = 校验不一致。
"""

from __future__ import annotations

import argparse
import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "aha-cli/src/main/java/com/acanx/module/aha/cli/render/StartupPixelLogo.java"

N = 48                      # 画布边长（像素）
CX, CY, R = 23.5, 24.0, 13.5   # 灯泡中心与半径
RAY_INNER, RAY_OUTER = R + 3, R + 9

# 9 条光线：顶部 1 条 + 两侧各 4 条（角度制，逆时针为正）
RAYS = (90, 61, 32, 0, -32, 148, 119, 180, 212)

# 调色板：字符 -> (语义, RGB)。取自 Logo.svg，去渐变、去中间过渡色。
PALETTE = {
    ".": ("transparent", None),
    "a": ("灯泡轮廓", (0xB4, 0x53, 0x09)),
    "b": ("灯泡下半", (0xF5, 0x9E, 0x0B)),
    "c": ("灯泡上半", (0xFD, 0xE6, 0x8A)),
    "e": ("光线", (0xFB, 0xBF, 0x24)),
    "g": ("AHA 字样", (0x7C, 0x2D, 0x12)),
    "f": ("笑脸", (0x5B, 0x21, 0x07)),
    "l": ("灯座亮", (0xE5, 0xE7, 0xEB)),
    "n": ("灯座中", (0x9C, 0xA3, 0xAF)),
    "k": ("灯座暗", (0x4B, 0x55, 0x63)),
}

# 5x7 点阵：只有 A、H 两个字
FONT = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
}
WORD = [" ".join(FONT[ch][row] for ch in "AHA") for row in range(7)]   # 17 x 7


def blank() -> list[list[str]]:
    """空白网格。"""
    return [["."] * N for _ in range(N)]


def put(grid: list[list[str]], x: int, y: int, ch: str) -> None:
    """落笔（越界忽略）。"""
    if 0 <= x < N and 0 <= y < N:
        grid[y][x] = ch


def draw_ray(grid: list[list[str]], degrees: float) -> None:
    """一条 1 像素宽的光线。

    沿角度取两端点后走 Bresenham：按半径逐步取整会在同一列上重叠，
    画出虚线状的光线。
    """
    angle = math.radians(degrees)
    x0 = round(CX + RAY_INNER * math.cos(angle))
    y0 = round(CY - RAY_INNER * math.sin(angle))
    x1 = round(CX + RAY_OUTER * math.cos(angle))
    y1 = round(CY - RAY_OUTER * math.sin(angle))
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        put(grid, x0, y0, "e")
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def draw_bulb(grid: list[list[str]]) -> None:
    """灯泡：整数圆 + 上下两段平色 + 1 像素轮廓。"""
    for y in range(N):
        for x in range(N):
            dist = math.hypot(x + 0.5 - CX, y + 0.5 - CY)
            if dist > R:
                continue
            if dist > R - 1.6:
                put(grid, x, y, "a")                        # 轮廓
            elif y + 0.5 < CY - 3:
                put(grid, x, y, "c")                        # 上半：亮
            else:
                put(grid, x, y, "b")                        # 下半：主色


def stamp(grid: list[list[str]], art: list[str], x0: int, y0: int, ch: str) -> None:
    """贴点阵（只画 '1' 的格子）。"""
    for dy, row in enumerate(art):
        for dx, cell in enumerate(row):
            if cell == "1":
                put(grid, x0 + dx, y0 + dy, ch)


def draw_face(grid: list[list[str]]) -> None:
    """笑脸：两只 3x3 眼睛、眉毛、一条 13 像素的微笑。"""
    ey = int(CY) + 4
    for ex in (int(CX) - 7, int(CX) + 3):
        for dy in range(3):
            for dx in range(3):
                put(grid, ex + dx, ey + dy, "f")
    for dx, dy in ((-8, -3), (-7, -4), (6, -4), (7, -3)):
        put(grid, int(CX) + dx, ey + dy, "f")
    for dx, dy in ((-6, 7), (-5, 8), (-4, 9), (-3, 9), (-2, 10), (-1, 10),
                   (0, 10), (1, 10), (2, 10), (3, 9), (4, 9), (5, 8), (6, 7)):
        put(grid, int(CX) + dx, ey + dy, "f")


def draw_base(grid: list[list[str]]) -> None:
    """灯座：向下收窄的梯形 + 交替螺纹 + 底盖。"""
    top = int(CY + R) + 1
    for i in range(N - top):
        y = top + i
        half = 9.5 - i * 0.28
        for x in range(N):
            if abs(x + 0.5 - CX) <= half:
                if abs(x + 0.5 - CX) > half - 1.6:
                    put(grid, x, y, "a")                    # 侧壁
                else:
                    put(grid, x, y, "l" if (i // 2) % 2 == 0 else "n")
    for y in range(N - 3, N):
        for x in range(N):
            if abs(x + 0.5 - CX) <= 8:
                put(grid, x, y, "k")


def build() -> list[str]:
    """生成 48x48 网格。"""
    grid = blank()
    for degrees in RAYS:
        draw_ray(grid, degrees)
    draw_bulb(grid)
    stamp(grid, WORD, int(CX) - 8, int(CY) - 10, "g")
    draw_face(grid)
    draw_base(grid)
    return ["".join(row) for row in grid]


# 降采样时的固定优先级：票数相同时谁胜出。
# 不能让 Python 的 set 迭代顺序决定——字符串哈希默认随机化，同一份输入跨进程会得到不同网格。
TIE_BREAK = ("e", "g", "f", "a", "c", "b", "l", "n", "k")


def downsample(grid: list[str], factor: int = 2) -> list[str]:
    """按 factor 精确降采样（多数非透明像素胜出，平票按 TIE_BREAK），供窄终端使用。"""
    out = []
    for y in range(0, N, factor):
        row = ""
        for x in range(0, N, factor):
            block = {c: 0 for c in TIE_BREAK}
            for dy in range(factor):
                for dx in range(factor):
                    cell = grid[y + dy][x + dx]
                    if cell != ".":
                        block[cell] += 1
            best, votes = ".", 0
            for ch in TIE_BREAK:
                if block[ch] > votes:
                    best, votes = ch, block[ch]
            row += best
        out.append(row)
    return out


def to256(ch: str) -> int:
    """RGB -> xterm-256 色号（与 StartupPixelLogo 的换算保持一致）。"""
    r, g, b = PALETTE[ch][1]
    level = lambda v: min(5, max(0, round(v / 255 * 5)))     # noqa: E731
    r6, g6, b6 = level(r), level(g), level(b)
    if r6 == g6 == b6:
        return 232 + min(23, max(0, round((r + g + b) / 3 / 255 * 23)))
    return 16 + 36 * r6 + 6 * g6 + b6


def render_art(grid: list[str], scale: int = 1) -> str:
    """给肉眼看的网格（每像素 scale 列，缺省一列一像素）。"""
    density = {"e": "*", "c": "#", "b": "+", "a": ".", "g": "@", "f": "o",
               "l": "=", "n": "-", "k": ",", ".": " "}
    return "\n".join("".join(density[c] * scale for c in row) for row in grid)


def java_array(name: str, grid: list[str]) -> str:
    """打印可直接粘贴的 Java 数组字面量。"""
    rows = ",\n".join('            "%s"' % row for row in grid)
    return f"    private static final String[] {name} = {{\n{rows}\n    }};"


def java_constant(name: str) -> list[str] | None:
    """从 Java 源里读回一个数组常量。"""
    text = JAVA.read_text(encoding="utf-8")
    match = re.search(re.escape(name) + r"\s*=\s*\{(.*?)\n    \};", text, re.S)
    if match is None:
        return None
    return re.findall(r'"([^"]*)"', match.group(1))


def verify() -> int:
    """校验 Java 常量与生成器输出一致。"""
    grid48 = build()
    grid24 = downsample(grid48)
    problems = []
    for name, expected in (("PIXELS_48", grid48), ("PIXELS_24", grid24)):
        actual = java_constant(name)
        if actual is None:
            problems.append(f"{name}: 未在 {JAVA.name} 中找到")
        elif actual != expected:
            diff = next((i for i, (a, b) in enumerate(zip(actual, expected)) if a != b), None)
            problems.append(f"{name}: 与生成器不一致（首个不同行 {diff}）")
    if problems:
        print("❌ 像素网格已漂移：")
        for problem in problems:
            print("  " + problem)
        print("\n修法：python3 bin/GenPixelLogo.py --java，把输出贴回 StartupPixelLogo.java")
        return 1
    print(f"✅ 像素网格与生成器一致（48x48 + 24x24，各 {len(grid48)} / {len(grid24)} 行）")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="AHA 启动标志（像素风）生成器")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--art", action="store_true", help="打印网格（肉眼校形）")
    group.add_argument("--java", action="store_true", help="打印 Java 数组字面量")
    group.add_argument("--verify", action="store_true", help="校验 Java 常量与生成器一致")
    args = parser.parse_args()

    if args.verify:
        return verify()

    grid48 = build()
    grid24 = downsample(grid48)
    if args.art:
        print(f"=== 48x48（{len(grid48)} 行）===")
        print(render_art(grid48))
        print(f"\n=== 24x24（{len(grid24)} 行，由 48x48 降采样）===")
        print(render_art(grid24))
        return 0

    print(java_array("PIXELS_48", grid48))
    print()
    print(java_array("PIXELS_24", grid24))
    return 0


if __name__ == "__main__":
    sys.exit(main())
