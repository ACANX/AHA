#!/usr/bin/env python3
"""从 Logo.svg 生成桌面端用的 PNG（矢量 -> 位图，可复现）。

为什么需要它：JavaFX 的 Image 只接受位图（PNG/JPEG/GIF/BMP），不认 SVG；
所以把 Logo.svg 渲染一次、把结果作为资源入库，避免在运行时依赖 SVG 渲染库。

唯一的矢量来源仍是 `aha-core/src/main/resources/Logo.svg`——改标志请改它，然后重跑本脚本。

依赖：一个支持无头渲染的 Chromium 系浏览器（Windows 上是 Edge）。
  用法：
    python3 bin/GenLogoPng.py                # 自动找 Edge / Chrome
    python3 bin/GenLogoPng.py --size 256     # 指定逻辑尺寸（默认 256）

输出尺寸 = --size（用 --force-device-scale-factor=1 固定缩放比，结果与系统缩放无关）。

**为什么要「多给高度 + 自己裁边」**：无头 Edge 的**视口比窗口矮**（本机实测矮约 85px），
按 --size 开方窗会把 SVG 下部裁掉——第一版图标只剩上部（底部一片透明）。
所以这里开一个更高的窗口，渲染后用纯 Python 解码 PNG、按 alpha 求出内容包围盒、
裁掉多余透明边再写回。裁完还会自检「结果接近正方形且不小于 0.9 倍目标尺寸」，
被裁切会当场报错，而不是悄悄产出半张图标。

**为什么要包一层 HTML**：Logo.svg 自带 width/height=600，直接当页面打开时，
在 256x256 的视口里只会渲染出**左上四分之一**（第一版就是这个 bug：图标缺了 3/4）。
包一层 HTML 并把 svg 设成 100%x100%，才会按视口缩放。

入库的 PNG 由 `LogoImageTest` 钉住：尺寸精确匹配、且**右下角必须是背景色**
（这条能抓住「只截到左上角」的裁切问题——光验尺寸是抓不住的）。
"""

from __future__ import annotations

import argparse
import pathlib
import re
import struct
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SVG = ROOT / "aha-core" / "src" / "main" / "resources" / "Logo.svg"
OUT = ROOT / "aha-desktop" / "src" / "main" / "resources" / "com" / "acanx" / "module" / "aha" / "desktop" / "view" / "logo.png"

BROWSERS = (
    # 原生 Windows
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    # WSL：调用 Windows 侧的浏览器（WSL 里没有 Chromium 时仍能生成）
    "/mnt/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
    "/mnt/c/Program Files/Microsoft/Edge/Application/msedge.exe",
    "/mnt/c/Program Files/Google/Chrome/Application/chrome.exe",
    "/mnt/c/Program Files (x86)/Google/Chrome/Application/chrome.exe",
    # Linux 原生
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
    "/usr/bin/google-chrome",
)


def find_browser() -> str:
    """找一个可用的 Chromium 系浏览器。"""
    for path in BROWSERS:
        if pathlib.Path(path).exists():
            return path
    sys.exit("未找到可用的浏览器（Edge / Chrome / Chromium），无法栅格化 SVG")


def png_size(data: bytes) -> tuple[int, int]:
    """从 PNG 的 IHDR 读出宽高（不依赖任何图像库）。"""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        sys.exit("输出不是 PNG")
    return struct.unpack(">II", data[16:24])


def _decode_png(data: bytes):
    """解 PNG（仅支持 8 位，颜色类型 0/2/3/4/6），返回 (w, h, 通道数, 行字节列表)。

    自己解而不是装 Pillow：本项目只依赖 JDK 与 Python 标准库。
    """
    import zlib

    if data[:8] != b"\x89PNG\r\n\x1a\n":
        sys.exit("不是 PNG")
    pos, idat, meta = 8, b"", None
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            width, height, depth, color_type = struct.unpack(">IIBB", body[:10])
            if depth != 8:
                sys.exit("只支持 8 位 PNG")
            meta = (width, height, color_type)
        elif kind == b"IDAT":
            idat += body
        pos += 12 + length
    width, height, color_type = meta
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color_type]
    stride = width * channels
    raw = zlib.decompress(idat)
    rows, prev, p = [], bytearray(stride), 0
    for _ in range(height):
        filter_type = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        for i in range(stride):
            a = line[i - channels] if i >= channels else 0
            b = prev[i]
            c = prev[i - channels] if i >= channels else 0
            if filter_type == 1:
                line[i] = (line[i] + a) & 0xFF
            elif filter_type == 2:
                line[i] = (line[i] + b) & 0xFF
            elif filter_type == 3:
                line[i] = (line[i] + ((a + b) >> 1)) & 0xFF
            elif filter_type == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pred = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pred) & 0xFF
        rows.append(bytes(line))
        prev = line
    return width, height, channels, rows


def _encode_png(width: int, height: int, channels: int, rows: list) -> bytes:
    """把行字节编回 PNG（统一用 filter 0，够小且实现简单）。"""
    import zlib

    raw = b"".join(b"\x00" + r for r in rows)
    color_type = {1: 0, 3: 2, 4: 6}[channels]

    def chunk(kind: bytes, body: bytes) -> bytes:
        return (struct.pack(">I", len(body)) + kind + body
                + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, color_type, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def trim_transparent(data: bytes) -> tuple:
    """按 alpha 裁掉透明边。

    Args:
        data: PNG 字节。

    Returns:
        (裁剪后的 PNG 字节, 宽, 高)；没有 alpha 通道时原样返回。
    """
    width, height, channels, rows = _decode_png(data)
    if channels not in (2, 4):
        return data, width, height
    alpha = channels - 1
    xs, ys = [], []
    for y, row in enumerate(rows):
        line = [x for x in range(width) if row[x * channels + alpha] > 16]
        if line:
            ys.append(y)
            xs.extend((line[0], line[-1]))
    if not ys:
        return data, width, height
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    cropped = [rows[y][x0 * channels:(x1 + 1) * channels] for y in range(y0, y1 + 1)]
    return _encode_png(x1 - x0 + 1, y1 - y0 + 1, channels, cropped), x1 - x0 + 1, y1 - y0 + 1


def background_probe(data: bytes) -> tuple[int, int, int]:
    """读 PNG 右下角像素（用于自检是否被裁切）。

    自己解 PNG 太重，这里只在 Windows / WSL 下借 Pillow；没有 Pillow 就跳过自检
    （产物仍由 Java 侧的 `LogoImageTest` 兜底）。

    Args:
        data: PNG 字节。

    Returns:
        RGB 三元组；无法探测时返回 (-1, -1, -1)。
    """
    try:
        from PIL import Image  # type: ignore
        import io as _io

        image = Image.open(_io.BytesIO(data)).convert("RGB")
        return image.getpixel((image.width - 3, image.height - 3))
    except Exception:  # noqa: BLE001 - 探测失败不应阻断生成
        return (-1, -1, -1)


def main() -> int:
    """入口。"""
    parser = argparse.ArgumentParser(description="从 Logo.svg 生成桌面端 PNG")
    parser.add_argument("--size", type=int, default=256, help="逻辑尺寸（默认 256）")
    args = parser.parse_args()

    if not SVG.exists():
        sys.exit("找不到 %s" % SVG)

    browser = find_browser()

    # 把根元素的 width/height 换成 100%：Logo.svg 自带 width=600 height=600，
    # 直接打开时在 256x256 视口里只会渲染出自然尺寸的一角（图标缺 3/4 的那个 bug）。
    # 只靠 CSS 选择器覆盖并不可靠——直接改属性最稳，viewBox 已保证按比例缩放。
    svg_text = SVG.read_text(encoding="utf-8")
    # 写死成目标像素数（不依赖 CSS/视口百分比——实测百分比在这套无头渲染下不生效，
    # 产物与未缩放时逐字节相同）。viewBox 保证内容按比例缩放。
    size_attr = str(args.size)
    svg_text = re.sub(r'(<svg\b[^>]*?)\s+width="[^"]*"',
                      r'\1 width="%s"' % size_attr, svg_text, count=1)
    svg_text = re.sub(r'(<svg\b[^>]*?)\s+height="[^"]*"',
                      r'\1 height="%s"' % size_attr, svg_text, count=1)
    assert 'width="%s"' % size_attr in svg_text, "没能改写 SVG 根元素的宽高"
    html = (
        "<!doctype html><meta charset=\"utf-8\">"
        "<style>html,body{margin:0;padding:0;width:100%;height:100%;"
        "background:transparent}svg{display:block}</style>" + svg_text
    )
    html_path = ROOT / "aha-desktop" / "target" / "logo-render.html"
    html_path.parent.mkdir(parents=True, exist_ok=True)
    html_path.write_text(html, encoding="utf-8")
    # 浏览器需要 Windows 路径；在 WSL 下把 /mnt/<drive>/... 还原成 <DRIVE>:\...
    def to_native(p: pathlib.Path) -> str:
        s = str(p)
        if s.startswith("/mnt/") and len(s) > 6 and s[6] == "/":
            return s[5].upper() + ":\\" + s[7:].replace("/", "\\")
        return s

    # 截图先落在 target/ 下：WSL 下浏览器是 Windows 程序，必须给它 Windows 形式的路径；
    # 同一份路径在两侧都能读写（/mnt/e/... <-> E:\...）
    tmp = ROOT / "aha-desktop" / "target" / "logo-render.png"
    tmp.parent.mkdir(parents=True, exist_ok=True)
    out_native = to_native(tmp)
    cmd = [
        browser, "--headless=new", "--disable-gpu", "--hide-scrollbars",
        "--default-background-color=00000000",
        # 固定缩放比：否则输出像素数会随生成机器的 DPI 变化
        "--force-device-scale-factor=1",
        # 窗口比目标高一截：无头 Edge 的视口比窗口矮，按正方形的窗口会把 SVG 下部裁掉
        "--window-size=%d,%d" % (args.size, args.size + 240),
        "--screenshot=" + out_native,
        "file:///" + to_native(html_path).replace("\\", "/"),
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if proc.returncode != 0 or not tmp.exists():
        sys.exit("渲染失败：%s %s" % (proc.returncode, proc.stderr[:200]))

    data = tmp.read_bytes()
    data, width, height = trim_transparent(data)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(data)
    print("已生成 %s（%dx%d，%d 字节）" % (OUT.relative_to(ROOT), width, height, len(data)))
    # 自检：裁完应当是正方形、且没被裁掉一角（被裁切时高度会明显小于目标尺寸）
    if abs(width - height) > 2:
        sys.exit("裁完不是正方形（%dx%d），标志可能被裁切" % (width, height))
    if width < args.size * 0.9:
        sys.exit("裁完只有 %d px，小于目标的 90%%（%d），说明渲染时被裁切了"
                 % (width, int(args.size * 0.9)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
