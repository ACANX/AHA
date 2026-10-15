#!/usr/bin/env python3
"""AHA 脚本文件规约检查。

跨平台脚本对编码与行尾极为敏感，Windows CMD 对格式错误的容忍度尤其低。
本脚本用于在提交前/CI 中守住以下约束：

  *.bat / *.cmd
    - 必须是纯 ASCII（禁止非 ASCII 字符）
    - 必须是 CRLF 行尾
    - 不得包含 UTF-8 BOM
    原因：CMD 按 ANSI 代码页解析批处理文件。UTF-8 中文字节会被误读并产生
    `&`、`|` 等元字符，导致注释或 echo 行被当作命令执行；LF-only 批处理
    在 `if (...)` 块中解析行为不可靠。

  *.sh
    - 必须以 `#!` shebang 开头
    - 必须是 LF 行尾（禁止 CRLF）
    - 应当有可执行位（可配置为提示而非错误）

  *.py
    - 应当以 `#!` shebang 开头
    - 必须是 LF 行尾

  *.ps1
    - 含非 ASCII 内容时**必须带 UTF-8 BOM**（纯 ASCII 不强制）
    原因：Windows PowerShell 5.1 读无 BOM 的文件时按 **ANSI（本机 GBK）** 解码，
    中文提示会直接变成乱码——不报错，只是用户看到一堆看不懂的字。
    （姊妦项目 LegionFanControl 的 `Dist\\UpdateNative.ps1` 就是无 BOM 的，属遗留坑。）

  Dist/ 下被跟踪的手写脚本
    - 同样按上面的规则检查（`Dist/` 整体是构建输出目录，已剪枝；
      所以用 `git ls-files` 把其中**被跟踪**的那几个手写入口单独捡回来）。

  快照清单：单一来源与消费方一致（issue #165）
    - `.github/snapshot-paths.yml` 是「触发标签 + 变更分类路径」的**单一来源**；
      `SnapshotJVM.yml` / `DesktopNative.yml` 与文档都必须与它一致：
        * 全仓出现的标签字面量必须与清单**逐字一致（含大小写）**——写错的表现是
          「打了标签却毫无反应」，静默且难查；
        * 快照工作流必须**引用**该清单文件（不许自己再写一套路径），其 `paths:`
          字面量必须在清单的 A / B / C 组里。
    - 清单自身不存在时跳过本检查（例如 `main` / `release` 分支尚未引入）。

  原生取件：两套实现的一致性
    - `Dist/UpdateNative.ps1`（Windows，PowerShell）与
      `Script/Python/NativeVersionUpdate.py`（POSIX，Python）的
      tag 后缀 / 资产前缀 / 可执行文件名必须**逐字一致**。
    原因：两套实现刻意并存（Windows 不假设有 Python；POSIX 不假设有 PowerShell，
    且 Python 版功能更多），但这三个字面量一旦漂移，表现是「**某个平台上永远取不到包**」
    ——静默且难查。把它们钉在一起，漂移立刻可见。

  发行包形态：两条线都必须是「包根平铺」（issue #218 / #217）
    - 装配描述符里**不许**再有 `dependencySet`（依赖全在单体 jar 里）、
      `fileSet` 的输出目录**不许**是 `bin/` 或 `lib/`、单体 jar 的 `destName`
      **不许**带版本号或 classifier 占位；
    - 启动脚本**不许**再走 `--module-path`，且必须引用那个去版本号的 jar 名；
    - shade **必须**显式 `shadedArtifactAttached=true` + `shadedClassifierName`。
    原因：这四条各自对应一次真出过的问题 —— 去掉 `lib/` 是为了不再背两份同一个 jar
    （CLI 包 55 MB，issue #217）；`attached=false` 会让 fat jar **顶替主产物**，
    于是装配描述符与原生镜像线都拿到错的包。配置漂回去的表现是**静默**的
    （包照出、体积翻倍、某个链路拿到重复依赖），所以钉成断言。

  *.xml（含 pom.xml）
    - 必须是良构 XML（整体可被解析器解析）
    - 注释里不得出现 `--`，也不得嵌套 `<!--`
    原因：XML 规范禁止注释体出现 `--`，解析器直接报
    `String '--' not allowed in comment`。写命令行选项时极易踩到
    （例如在注释里写 native-image 的双短横参数），而**报错位置指向注释本身**，
    看起来像文件坏了。本仓库已踩两次，因此固化成检查。
    嵌套注释更阴：外层会被内层的 `-->` 提前闭合，剩下的文字变成正文，报错离原因很远。

用法：
    python3 bin/CheckScripts.py
    python3 bin/CheckScripts.py --verbose

退出码：0 = 通过；1 = 存在问题。
"""

from __future__ import annotations

import argparse
import re
import subprocess
import os
import sys
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parent.parent

SKIP_DIRS = {"target", ".git", "Dist", "dist", "node_modules", ".idea", ".vscode"}
BAT_SUFFIXES = {".bat", ".cmd"}
SH_SUFFIXES = {".sh", ".bash"}
PY_SUFFIXES = {".py"}
PS1_SUFFIXES = {".ps1"}

#: Dist/ 被 SKIP_DIRS 剪掉（里面是构建产物），但其中有几个**被 git 跟踪的手写入口**
#: 必须照检——用 git 问「哪些是跟踪的」最省事，生成物（Dist/bin/*.bat 等）不在跟踪列表里。
TRACKED_SCRIPT_DIRS = ("Dist",)

#: 原生取件的两套实现：命名契约必须一致（见模块 docstring）
NATIVE_PS1 = ROOT / "Dist" / "UpdateNative.ps1"
NATIVE_PY = ROOT / "Script" / "Python" / "NativeVersionUpdate.py"
#: 快照自测留痕（issue #166）：工作流与规范里的清单必须一致。
#: `context` / 命令前缀写错的表现是**静默**的（打了标对不上分支保护里的名字、命令没人认），所以钉成断言。
SNAPSHOT_VERIFIED_WORKFLOW = ROOT / ".github" / "workflows" / "SnapshotVerified.yml"
SNAPSHOT_VERIFIED_DOC = ROOT / "Docs" / "DevSpec" / "ReleaseProcess.md"
#: 清单行的形状（两处都用同一张表）：`| A1 | 启动 | … | 0 |`
CHECKLIST_ROW_RE = re.compile(r"^\s*\|\s*([ABC][0-9]+)\s*\|.*\|\s*([^|]*?)\s*\|\s*$", re.M)

#: 发行包形态的单一来源（issue #218 / #217）：装配描述符、shade 所在 POM、
#: 单体 jar 的落地名、启动脚本。四条一起断言，才不会出现「描述符改了、脚本没改」这种半吊子。
ASSEMBLY_DESCRIPTORS = {
    "cli": ROOT / "aha-cli" / "src" / "assembly" / "dist.xml",
    "desktop": ROOT / "aha-desktop" / "src" / "assembly" / "dist-desktop.xml",
}
FATJAR_POMS = {
    "cli": ROOT / "aha-cli" / "pom.xml",
    "desktop": ROOT / "aha-desktop" / "pom.xml",
}
#: 单体 jar 的落地名：**去版本号**，启动脚本与用户命令都写死这个名字（换版本不用改）
FLAT_JAR_NAME = {"cli": "aha-cli.jar", "desktop": "aha-desktop.jar"}
#: 启动脚本：包根平铺形态下它们是 `java -jar <去版本号的 jar>`，不再有模块路径
FLAT_LAUNCHERS = {
    "cli": (ROOT / "bin" / "AhaCli.sh", ROOT / "bin" / "AhaCli.bat"),
    "desktop": (ROOT / "bin" / "AhaDesktop.sh", ROOT / "bin" / "AhaDesktop.bat"),
}
#: 装配描述符的命名空间（assembly 2.2.0）
ASSEMBLY_NS = {"a": "http://maven.apache.org/ASSEMBLY/2.2.0"}

#: 快照出包的单一来源（issue #165）：触发标签与变更分类路径清单。
#: 消费方是 `SnapshotJVM.yml` / `DesktopNative.yml` 与文档——三者一旦漂移，表现是**静默**的
#: （某个改动悄悄不出包 / 打错标签毫无反应），因此在这里钉成断言。
SNAPSHOT_MANIFEST = ROOT / ".github" / "snapshot-paths.yml"

#: 仓库里**真实存在**的 7 个标签（`gh label list` 可查）。这是全仓唯一一处把标签名写死的地方——
#: 名字必须与 GitHub 上的既有事实对齐，不能由清单自定义；清单与所有消费方都要与它逐字一致。
SNAPSHOT_LABELS = (
    "SNAPSHOT:Desktop",
    "SNAPSHOT:Cli",
    "SNAPSHOT:NativeDesktop",
    "SNAPSHOT:NativeCli",
    "OS:Win",
    "OS:MacOS",
    "OS:Ubuntu",
)

#: 标签字面量。**故意不区分大小写地粗扫**，再与清单逐字比对——这样才能抓出
#: 「平台名写成小写」这类大小写漂移（GitHub 的标签匹配是区分大小写的）。
SNAPSHOT_LABEL_RE = re.compile(r"\b(SNAPSHOT|OS):([A-Za-z]+)\b", re.I)

#: 扫描标签字面量时只读这些文本类型（二进制与压缩包不读）
SCANNABLE_SUFFIXES = {
    ".md", ".markdown", ".yml", ".yaml", ".py", ".ps1", ".psm1", ".cmd", ".bat",
    ".sh", ".bash", ".java", ".xml", ".json", ".txt",
}

#: 快照工作流必须出现的清单引用（不许自己再写一套路径）
SNAPSHOT_MANIFEST_REF = "snapshot-paths.yml"

PY_KIND_RE = re.compile(
    r'key="(\w+)".*?tag_suffix="([^"]+)".*?asset_prefix="([^"]+)".*?exe_base="([^"]+)"',
    re.S)
PS_KIND_RE = re.compile(
    r"^\s*(\w+)\s*=\s*@\{\s*Title\s*=\s*'[^']*';\s*TagSuffix\s*=\s*'([^']+)';"
    r"\s*AssetPrefix\s*=\s*'([^']+)';\s*ExeBase\s*=\s*'([^']+)'\s*\}",
    re.M)


def collect() -> dict[str, list[Path]]:
    """单次遍历收集待检查的脚本文件，按类型分类。

    在慢文件系统（如 WSL 下的 /mnt/e）上遍历代价很高：既要避免同一棵树
    扫多遍，也要对 SKIP_DIRS 直接剪枝而不要走进去了再逐条判定。
    """
    found: dict[str, list[Path]] = {"bat": [], "sh": [], "py": [], "ps1": [], "xml": [], "yml": []}
    for dirpath, dirnames, filenames in os.walk(ROOT):
        # 就地改写 dirnames 实现剪枝，os.walk 不会进入被移除的子目录
        dirnames[:] = sorted(name for name in dirnames if name not in SKIP_DIRS)
        base = Path(dirpath)
        for name in filenames:
            suffix = Path(name).suffix.lower()
            if suffix in BAT_SUFFIXES:
                found["bat"].append(base / name)
            elif suffix in SH_SUFFIXES:
                found["sh"].append(base / name)
            elif suffix in PY_SUFFIXES:
                found["py"].append(base / name)
            elif suffix in (".yml", ".yaml"):
                # GitHub Actions 的 shell 片段藏在这里：它们由各平台的 bash 执行，
                # 跨平台差异（macOS bash 3.2 的变量名规则）就靠这一组守住。
                found["yml"].append(base / name)
            elif suffix == ".xml":
                # 只关心仓库自己的 XML（pom.xml、assembly 描述符等）。
                # target/ 与 Dist/ 已在 SKIP_DIRS 里剪掉，不会扫到生成物。
                found["xml"].append(base / name)
    # Dist/ 整体被剪掉，但其中的手写入口要捡回来：问 git 哪些属于「源码」
    # （--cached 跟踪的 + --others 未跟踪但未被忽略的），生成物（Dist/bin/*.bat、
    # 解压出来的 lib/ 等）都在 .gitignore 里，因此不会被误判；新加的文件不 add 也能查到。
    for directory in TRACKED_SCRIPT_DIRS:
        tracked = subprocess.run(["git", "ls-files", "--cached", "--others", "--exclude-standard",
                                  "--", directory],
                                 cwd=ROOT, capture_output=True, text=True)
        if tracked.returncode != 0:
            continue
        for name in tracked.stdout.splitlines():
            if not name.strip():
                continue
            suffix = Path(name).suffix.lower()
            if suffix in BAT_SUFFIXES:
                found["bat"].append(ROOT / name)
            elif suffix in PS1_SUFFIXES:
                found["ps1"].append(ROOT / name)
    return {group: sorted(paths) for group, paths in found.items()}


def check_bat(path: Path) -> list[str]:
    """批处理：纯 ASCII + CRLF + 无 BOM。"""
    problems: list[str] = []
    raw = path.read_bytes()
    rel = path.relative_to(ROOT)

    if raw.startswith(b"\xef\xbb\xbf"):
        problems.append(f"{rel}: 含 UTF-8 BOM，CMD 会把它当作命令的一部分")

    non_ascii = [(i, b) for i, b in enumerate(raw) if b > 127]
    if non_ascii:
        offset, byte = non_ascii[0]
        line = raw[:offset].count(b"\n") + 1
        problems.append(
            f"{rel}:{line} 含非 ASCII 字节 0x{byte:02x}（共 {len(non_ascii)} 处）。"
            "批处理必须为纯 ASCII；中文说明请放到 README"
        )

    lf_only = raw.count(b"\n") - raw.count(b"\r\n")
    if lf_only:
        problems.append(
            f"{rel}: 存在 {lf_only} 处裸 LF 行尾，批处理必须使用 CRLF"
        )

    # `^(` / `^)` 与 `(...)` 语句块混用时，CMD 会把转义后的括号当成**未闭合的块**，
    # 实测报 `. was unexpected at this time.`（本仓库的 Dist/Update*.cmd 踩过一次）。
    # 这里的取舍：宁可禁用这种转义写法，改用标签 + goto，或让 echo 的文本不含括号。
    for number, line in enumerate(raw.split(b"\r\n"), 1):
        if b"^(" in line or b"^)" in line:
            problems.append(
                f"{rel}:{number} 使用了 `^(` / `^)` 转义括号——与 `(...)` 语句块混用时 CMD "
                f"会把它当成未闭合的块（实测报 `. was unexpected at this time.`）。"
                f"请改用标签 + goto，或让 echo 的文本不含括号"
            )
    return problems


def check_sh(path: Path) -> list[str]:
    """Shell：shebang + LF。"""
    problems: list[str] = []
    rel = path.relative_to(ROOT)
    raw = path.read_bytes()

    if not raw.startswith(b"#!"):
        problems.append(f"{rel}: 缺少 `#!` shebang")

    crlf = raw.count(b"\r\n")
    if crlf:
        problems.append(f"{rel}: 含 CRLF 行尾（{crlf} 处），shell 脚本必须使用 LF")
    return problems


def check_ignored_sources() -> list[str]:
    """检查有没有源码文件被 .gitignore 吃掉。

    这个检查是有来历的：`.gitignore` 里曾经写着不带前导斜杠的 `Log/`（本意是仓库根的
    运行期日志目录），于是它在**任意层级**匹配，在 Windows / macOS（大小写不敏感）上
    把 `aha-desktop/src/main/java/.../desktop/log/` 整个吃掉了——

      * git 不报错；
      * `git add -A` 静默跳过；
      * `git status` 显示"干净"；
      * 本地测试照样全绿（文件在磁盘上）；
      * 只有 CI 会告诉你 `cannot find symbol: class LogLevel`。

    因此这里直接问 git：有哪些被忽略的文件落在源码目录里（或本身就是 .java）。
    """
    try:
        result = subprocess.run(
            ["git", "ls-files", "--others", "--ignored", "--exclude-standard"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=60,
            check=False,
        )
    except (OSError, subprocess.SubprocessError) as error:  # pragma: no cover - 环境问题
        return [f"调用 git 失败，无法检查被忽略的源码文件：{error}"]

    if result.returncode != 0:
        return [f"调用 git 失败（exit={result.returncode}）：{result.stderr.strip()}"]

    offenders: list[str] = []
    for line in result.stdout.splitlines():
        entry = line.strip()
        if not entry:
            continue
        if "/src/" in entry or entry.endswith(".java"):
            offenders.append(entry)
    return [f"源码文件被 .gitignore 忽略了（git 不会报错，只有 CI 会发现）：{path}"
            for path in offenders]


XML_COMMENT = re.compile(r"<!--(.*?)-->", re.S)


def check_xml(path: Path) -> list[str]:
    """XML 的三条硬约束：整体良构、注释里不能有 `--`、不能嵌套 `<!--`。

    前两条与「嵌套」都会让 XML 直接不可解析，而且报错位置离真正的原因往往很远
    （`--` 报在注释那一行；嵌套报在内层 `-->` 之后；尖括号占位符报成「无效记号」）。

    为什么要查良构性：**模板类文件**（如技能 assets 下的 POM 骨架）如果本身不是合法
    XML，别人拷贝过去会在解析阶段就失败，而报错信息看起来像文件损坏、不像「有东西没替换」。
    实测踩过：占位符写成三个尖括号包中文，整个模板不可解析。
    """
    problems: list[str] = []

    # ① 良构性：整体解析一次（这也能挡住非 UTF-8 与尖括号占位符）
    try:
        ElementTree.parse(path)
    except ElementTree.ParseError as exc:
        problems.append(f"{path.relative_to(ROOT)} 不是良构 XML：{exc}")
    except Exception as exc:  # 权限、编码等
        problems.append(f"{path.relative_to(ROOT)} 无法解析为 XML：{exc}")
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return [f"{path.relative_to(ROOT)} 不是 UTF-8，无法检查 XML 注释"]

    rel = path.relative_to(ROOT)
    for match in XML_COMMENT.finditer(text):
        body = match.group(1)
        line = text[: match.start()].count("\n") + 1
        if "--" in body:
            problems.append(
                f"{rel}:{line} XML 注释里出现 `--`；解析器会报 "
                f"String '--' not allowed in comment（改写措辞，不要直接写双短横选项）"
            )
        if "<!--" in body:
            problems.append(
                f"{rel}:{line} XML 注释里嵌套了 `<!--`；外层注释会被提前闭合，"
                f"内层改用普通文字"
            )
    return problems


def check_py(path: Path) -> list[str]:
    """Python：shebang + LF。"""
    problems: list[str] = []
    rel = path.relative_to(ROOT)
    raw = path.read_bytes()

    if not raw.startswith(b"#!"):
        problems.append(f"{rel}: 缺少 `#!` shebang")
    if raw.count(b"\r\n"):
        problems.append(f"{rel}: 含 CRLF 行尾，Python 脚本应使用 LF")
    return problems


def check_ps1(path: Path) -> list[str]:
    """PowerShell：含非 ASCII 时必须带 UTF-8 BOM。

    无 BOM 的 UTF-8 脚本在 Windows PowerShell 5.1 下会被按 ANSI（GBK）解码：
    中文提示变乱码，而且**不报错**——只是用户看不懂。加 BOM 是唯一能让 5.1 正确解码的办法。
    """
    rel = path.relative_to(ROOT)
    raw = path.read_bytes()
    has_bom = raw.startswith(b"\xef\xbb\xbf")
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        return [f"{rel}: 不是 UTF-8，无法检查"]
    if text.isascii() or has_bom:
        return []
    return [
        f"{rel}: 含非 ASCII（中文提示）但没有 UTF-8 BOM——"
        f"Windows PowerShell 5.1 会按 ANSI 解码，中文变乱码（且不报错）"
    ]


SNAPSHOT_PLAN_PY = ROOT / ".github" / "Python" / "SnapshotPlan.py"
SNAPSHOT_FETCH_PS1 = ROOT / "Dist" / "UpdateSnapshot.ps1"


def check_snapshot_fetch_parity() -> list[str]:
    """快照取件（PowerShell）与快照判定（Python）的命名契约必须一致。

    为什么需要它：tag 与资产名由 `.github/Python/SnapshotPlan.py` **生成**（发布页上长什么样），
    由 `Dist/UpdateSnapshot.ps1` **识别**（本地去发布页找什么）。两边漂移的后果是**静默**的：
    发布页上明明有包，本地却报「没有找到符合条件的快照包」——没有任何报错指向真正的原因。
    与原生取件的 PS1/Python 契约同一思路（见 `check_native_update_parity`）。
    """
    if not (SNAPSHOT_PLAN_PY.exists() and SNAPSHOT_FETCH_PS1.exists()):
        return []
    py = SNAPSHOT_PLAN_PY.read_text(encoding="utf-8")
    ps = SNAPSHOT_FETCH_PS1.read_text(encoding="utf-8-sig")
    problems: list[str] = []

    # ① tag 形如 `V<版本>-AHA-<目标>-SNAPSHOT`
    tag_fmt = re.search(r'TAG_FORMAT = "([^"]+)"', py)
    if not tag_fmt:
        return ["快照命名契约：没能从 SnapshotPlan.py 解析出 TAG_FORMAT（写法变了？）"]
    # 取 TAG_FORMAT 里的**字面量片段**（去掉 `{version}` / `{target}` 这类占位符）：
    # PowerShell 侧用命名捕获组写正则（`V(?<version>…)-AHA-(?<target>…)`），
    # 因此不能拿整个 head 去比对（`V-AHA-` 在那里本来就不连续）——只查长度 ≥ 2 的片段。
    literals = [run for run in re.split(r"\{[^}]+\}", tag_fmt.group(1)) if len(run) >= 2]
    pattern = re.search(r"\$TagPattern\s*=\s*'([^']+)'", ps)
    if not pattern:
        problems.append("快照命名契约：没能从 Dist/UpdateSnapshot.ps1 解析出 $TagPattern")
    else:
        for literal in literals:
            # 右侧要带边界：`'-SNAPSHOT' in '-SNAPSHOTS'` 是**真**（子串），
            # 只用 `in` 会漏掉「把后缀写长一截」这类漂移（本轮反向验证抓到的假通过）。
            if not re.search(re.escape(literal) + r"(?![A-Za-z0-9-])", pattern.group(1)):
                problems.append(
                    f"快照命名契约：tag 的字面量 {literal!r}（来自 SnapshotPlan.py 的 TAG_FORMAT）"
                    f"没有出现在 UpdateSnapshot.ps1 的 $TagPattern 里（或其右侧被改动过）"
                )
        for target in ("Desktop-JVM", "Cli-JVM", "Desktop-Native", "Cli-Native",
                       "Desktop", "Cli", "NativeDesktop", "NativeCli"):
            if target not in pattern.group(1):
                problems.append(f"快照命名契约：$TagPattern 里没有目标 {target!r}——该目标的快照本地取不到")

    # ② 资产前缀：Python 的两张表必须与 PS1 的 $kinds 表逐一对应
    py_prefixes = set(re.findall(r'"(AHA-[A-Za-z-]+-)\{version\}', py))
    ps_prefixes = set(re.findall(r"'(AHA-[A-Za-z-]+-)'", ps))
    missing = sorted(py_prefixes - ps_prefixes)
    extra = sorted(ps_prefixes - py_prefixes)
    if missing:
        problems.append(
            "快照命名契约：这些资产前缀在 SnapshotPlan.py 里有、UpdateSnapshot.ps1 里没有——"
            f"{missing}；本地会永远找不到这类包"
        )
    if extra:
        problems.append(f"快照命名契约：UpdateSnapshot.ps1 里的这些资产前缀在 SnapshotPlan.py 里没有——{extra}")

    # ③ 有效期天数
    ttl_py = re.search(r"SNAPSHOT_TTL_DAYS = (\d+)", py)
    ttl_ps = re.search(r"\$TtlDays\s*=\s*(\d+)", ps)
    if ttl_py and ttl_ps and ttl_py.group(1) != ttl_ps.group(1):
        problems.append(
            f"快照命名契约：有效期天数不一致——Python {ttl_py.group(1)} 天，"
            f"PowerShell {ttl_ps.group(1)} 天；本地提示会与清理规则对不上"
        )
    return problems


def check_native_update_parity() -> list[str]:
    """两套原生取件实现的命名契约必须逐字一致（见模块 docstring）。"""
    if not (NATIVE_PS1.exists() and NATIVE_PY.exists()):
        return []
    py = {m.group(1).lower(): m.groups()[1:]
          for m in PY_KIND_RE.finditer(NATIVE_PY.read_text(encoding="utf-8"))}
    ps = {m.group(1).lower(): m.groups()[1:]
          for m in PS_KIND_RE.finditer(NATIVE_PS1.read_text(encoding="utf-8-sig"))}
    if not py or not ps:
        return [
            "原生取件实现的一致性检查没能解析出契约："
            f"Python 侧 {sorted(py)} / PowerShell 侧 {sorted(ps)}——"
            "请确认 KINDS 表（Python）与 $kinds 表（PowerShell）仍是脚本可识别的写法"
        ]
    problems: list[str] = []
    for key in sorted(set(py) | set(ps)):
        if key not in ps:
            problems.append(f"原生取件：Python 侧有种类 {key!r}，PowerShell 侧没有")
            continue
        if key not in py:
            problems.append(f"原生取件：PowerShell 侧有种类 {key!r}，Python 侧没有")
            continue
        for field, a, b in zip(("tag 后缀", "资产前缀", "可执行文件名"), py[key], ps[key]):
            if a != b:
                problems.append(
                    f"原生取件：种类 {key!r} 的{field}两套实现不一致——"
                    f"Python 侧 {a!r}，PowerShell 侧 {b!r}；"
                    f"这会让某个平台上永远取不到包"
                )
    return problems


SHELL_VAR = re.compile(r"(?<!\\)\$([A-Za-z_][A-Za-z0-9_]*)")


def maybe_scannable_files():
    """遍历被 git 跟踪（或未跟踪但未被忽略）的文本文件，产出 (路径, 内容)。

    用 git 问「哪些是源码」而不是自己 walk：`Dist/` 等目录里既有生成物又有手写入口，
    `--exclude-standard` 已经把生成物排除了（与 `collect()` 同一套判断）。
    """
    listed = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
        cwd=ROOT, capture_output=True, text=True)
    if listed.returncode != 0:
        return
    for name in listed.stdout.splitlines():
        if not name.strip():
            continue
        path = ROOT / name
        if path.suffix.lower() not in SCANNABLE_SUFFIXES or not path.is_file():
            continue
        try:
            yield path, path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue


def read_snapshot_manifest(path: Path) -> tuple[list[str], dict[str, list[str]]]:
    """解析快照清单，返回 (标签列表, {组名: [路径]})。

    只认本文件固定的形状（顶层 `labels:` / `paths:`，其下 `组名:` 与 `- 值`）——
    形状变了会解析成空，由调用方报「无法解析」而**不是**静默放行。
    不依赖 PyYAML：CI 与本地都不假设装了第三方库。
    """
    labels: list[str] = []
    groups: dict[str, list[str]] = {}
    section: str | None = None
    group: str | None = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        body = raw.split("#", 1)[0].rstrip()  # 去注释与行尾空白
        if not body.strip():
            continue
        indent = len(body) - len(body.lstrip())
        stripped = body.strip()
        if indent == 0 and stripped.endswith(":"):
            section, group = stripped[:-1], None
            continue
        if stripped.startswith("- "):
            value = stripped[2:].strip().strip("'\"")
            if section == "labels":
                labels.append(value)
            elif section == "paths" and group:
                groups[group].append(value)
            continue
        if indent > 0 and stripped.endswith(":") and section == "paths":
            group = stripped[:-1]
            groups.setdefault(group, [])
    return labels, groups


def paths_block_literals(text: str) -> list[tuple[int, str]]:
    """取工作流 `paths:` / `paths-ignore:` 块下的字面量（如 `- 'aha-cli/**'`）与行号。"""
    literals: list[tuple[int, str]] = []
    inside = False
    for number, line in enumerate(text.splitlines(), 1):
        body = line.split("#", 1)[0].rstrip()
        stripped = body.strip()
        if not stripped:
            continue
        if stripped.startswith(("paths:", "paths-ignore:")):
            inside = True
            continue
        if inside:
            if stripped.startswith("- "):
                value = stripped[2:].strip().strip("'\"")
                if value:
                    literals.append((number, value))
                continue
            inside = False  # 块结束（同级或更浅的键）
    return literals


def pull_request_paths_filters(text: str) -> list[tuple[int, str]]:
    """取 `on.pull_request` 下的 `paths:` / `paths-ignore:` 键（行号 + 键名）。

    为什么单独查这个：`paths:` 工作在**触发层**——事件根本进不来，判定脚本就没机会走
    「显式请求」那条路。2026-10-15 实测（PR #203）：只改 `Docs/` 的 PR 新打了
    `SNAPSHOT:NativeDesktop`，JVM 线正常判定后退出，**原生线被 `paths:` 挡在门外、毫无反应**，
    与 #165 验收 16「只改文档时新打标签 ⇒ 照出包并写明『显式请求』」冲突。
    """
    hits: list[tuple[int, str]] = []
    in_on = in_pr = False
    on_indent = pr_indent = 0
    for number, line in enumerate(text.splitlines(), 1):
        body = line.split("#", 1)[0].rstrip()
        if not body.strip():
            continue
        indent = len(body) - len(body.lstrip())
        stripped = body.strip()
        if stripped == "on:":
            in_on, on_indent = True, indent
            continue
        if in_on and indent <= on_indent:
            in_on = False          # 离开 on 段（同级 / 更浅的顶层键）
        if not in_on:
            continue
        if stripped == "pull_request:":
            in_pr, pr_indent = True, indent
            continue
        if in_pr:
            if indent <= pr_indent:
                in_pr = False      # 离开 pull_request 段
                continue
            if stripped.startswith(("paths:", "paths-ignore:")):
                hits.append((number, stripped.split(":", 1)[0]))
    return hits


def concurrency_group(text: str) -> str | None:
    """取工作流 `concurrency.group` 的值（支持 `>-` / `>` 折叠多行）。

    为什么需要它：并发组是「哪些运行会互相取消」的唯一依据。只按 `github.ref` / PR 号
    分组时，`pull_request` 的 `labeled` 与 `synchronize` 落在同一组 ⇒ `cancel-in-progress`
    让后到的自动事件取消先到的显式请求（2026-10-15 实测 PR #203）。

    只认 `concurrency:` 段下的 `group:`：值可能是单行，也可能是 `>-` 折叠加若干缩进行。
    """
    lines = text.splitlines()
    for index, raw in enumerate(lines):
        body = raw.split("#", 1)[0].rstrip()
        if body.strip() != "concurrency:":
            continue
        base_indent = len(body) - len(body.lstrip())
        parts: list[str] = []
        group_indent = -1
        for sub in lines[index + 1:]:
            sb = sub.split("#", 1)[0].rstrip()
            if not sb.strip():
                continue
            indent = len(sb) - len(sb.lstrip())
            if indent <= base_indent:
                break                      # 离开 concurrency 段
            stripped = sb.strip()
            if group_indent < 0:
                if not stripped.startswith("group:"):
                    continue
                group_indent = indent
                rest = stripped[len("group:"):].strip()
                if rest and rest not in (">-", ">", "|", "|-"):
                    parts.append(rest)
                continue
            if indent <= group_indent:
                break                      # 同级键（如 cancel-in-progress）
            parts.append(stripped)
        return " ".join(parts) if parts else None
    return None


def check_snapshot_verified_contract() -> list[str]:
    """快照自测留痕的契约（issue #166）：context / 命令 / 权限 / 清单编号 / 必测项 0 token。

    五条都对应「改坏了也照样跑、只在真用时才发现」的情形，所以在这里钉成断言。
    """
    problems: list[str] = []

    wf = SNAPSHOT_VERIFIED_WORKFLOW
    rel = wf.relative_to(ROOT)
    if not wf.exists():
        problems.append(f"缺快照自测留痕工作流 {rel}（issue #166）")
        return problems
    wf_text = wf.read_text(encoding="utf-8")

    # ① status 的 context：分支保护里认的就是这个字符串（必须出现在**打标那一行**）
    if not re.search(r"-f context=snapshot-verified(\s|$)", wf_text, re.M):
        problems.append(
            f"{rel}：找不到 `-f context=snapshot-verified` —— 打出来的标不会指向分支保护里那个名字"
        )

    # ② 两个命令前缀必须出现在**触发守卫**里（只看 `if:` 那一块）
    #    早先写成「文件里某处出现过就算过」，反向测试立刻抓到洞：把守卫里的命令换掉，工作流永远不触发，而守卫不报。
    guard = re.search(r"^    if: >-\n(?:      .*\n)+", wf_text, re.M)
    guard_text = guard.group(0) if guard else ""
    for cmd in ("/verified", "/snapshot-failed"):
        if f"startsWith(github.event.comment.body, '{cmd}')" not in guard_text:
            problems.append(
                f"{rel}：触发守卫里没有 `startsWith(github.event.comment.body, '{cmd}')`"
                "（实测：命令发了也起不了运行）"
            )

    # ③ 顶层 permissions：漏 statuses: write 就是 403
    if not re.search(r"^  statuses: write\b", wf_text, re.M):
        problems.append(f"{rel}：顶层 permissions 缺 `statuses: write`（打标会 403）")

    # ④⑤ 清单：工作流与规范两处编号一致；必测行的 token 列必须为 0
    def rows(text: str) -> list[tuple[str, str]]:
        return [(m.group(1), m.group(2)) for m in CHECKLIST_ROW_RE.finditer(text)]

    wf_rows = rows(wf_text)
    doc = SNAPSHOT_VERIFIED_DOC
    if not doc.exists():
        problems.append(f"缺规范 {doc.relative_to(ROOT)}（清单的原文在这里）")
        return problems
    doc_rows = rows(doc.read_text(encoding="utf-8"))

    wf_ids = {i for i, _ in wf_rows}
    doc_ids = {i for i, _ in doc_rows}
    if not wf_ids:
        problems.append(f"{rel}：没解析到自测清单行（形状应为 `| A1 | … | … | 0 |`）")
    if wf_ids != doc_ids:
        only_wf = sorted(wf_ids - doc_ids)
        only_doc = sorted(doc_ids - wf_ids)
        problems.append(
            f"自测清单编号不一致：{rel} 独有 {only_wf or '无'}，"
            f"{doc.relative_to(ROOT)} 独有 {only_doc or '无'}"
            "（清单是这条检查的实质，两处必须逐条对齐）"
        )
    for src, rws in ((rel, wf_rows), (doc.relative_to(ROOT), doc_rows)):
        bad = [i for i, token in rws if token.strip() != "0"]
        if bad:
            problems.append(
                f"{src}：必测项 {', '.join(bad)} 的 token 开销不是 0 —— "
                "「必测项不许烧 token」是硬约定（需 provider 的动作用 +model 声明，不进必测）"
            )

    return problems


def check_package_layout() -> list[str]:
    """发行包必须仍是「包根平铺」：无 lib/、无 bin/、jar 名不带版本号（issue #218 第 7 条）。

    只看**配置的形状**：产物内容由工作流在打包后拆包自证（那才是用户真正拿到的东西）。
    """
    problems: list[str] = []

    for kind, path in ASSEMBLY_DESCRIPTORS.items():
        rel = path.relative_to(ROOT)
        if not path.exists():
            problems.append(f"缺装配描述符 {rel}（{kind} 线的发行包由它组装）")
            continue
        try:
            tree = ElementTree.parse(path)
        except ElementTree.ParseError as exc:
            problems.append(f"{rel}：XML 解析失败（{exc}）")
            continue

        # ① 依赖不能再收进 lib/（依赖已在单体 jar 里，再收一份就是同一个类存两遍）
        if tree.findall(".//a:dependencySet", ASSEMBLY_NS):
            problems.append(
                f"{rel}：仍有 <dependencySet> —— 依赖会再收进 lib/，"
                "与单体 jar 里的重复（issue #218 起两条线都是包根平铺）"
            )

        # ② 脚本不能再铺到 bin/（或任何 lib/ 形态的目录）
        for file_set in tree.findall(".//a:fileSet", ASSEMBLY_NS):
            out = (file_set.findtext("a:outputDirectory", default="", namespaces=ASSEMBLY_NS) or "").strip()
            if out in {"bin", "lib"}:
                problems.append(
                    f"{rel}：fileSet 仍输出到 {out}/（应为 `.`；启动脚本要平铺在包根）"
                )

        # ③ 单体 jar 的落地名：必须是去版本号的那一个，且铺在包根
        want = FLAT_JAR_NAME[kind]
        names = [(f.findtext("a:destName", default="", namespaces=ASSEMBLY_NS) or "").strip()
                 for f in tree.findall(".//a:file", ASSEMBLY_NS)]
        if want not in names:
            problems.append(
                f"{rel}：没有把单体 jar 落地为 {want}（实测到的是 {names or '无 <file> 条目'}）"
            )
        for name in names:
            if name and name.endswith(".jar") and name != want:
                problems.append(
                    f"{rel}：单体 jar 落地名 {name!r} 不是 {want!r}"
                    "（带版本号或 classifier 会让启动脚本与文档跟着版本变，issue #218）"
                )

    for kind, paths in FLAT_LAUNCHERS.items():
        want = FLAT_JAR_NAME[kind]
        for path in paths:
            rel = path.relative_to(ROOT)
            if not path.exists():
                problems.append(f"缺启动脚本 {rel}（{kind} 线的包根入口）")
                continue
            text = path.read_text(encoding="utf-8")
            if "--module-path" in text or "module com.acanx.module.aha" in text:
                problems.append(
                    f"{rel}：仍按模块路径启动（出现 `--module-path` / `module com.acanx.module.aha`）"
                    "—— 发行包已改为单体 jar，模块路径在包里根本不存在（issue #218）"
                )
            if want not in text:
                problems.append(
                    f"{rel}：没有引用 {want}（启动脚本必须认这个去版本号的名字）"
                )

    return problems


def check_shade_attached() -> list[str]:
    """shade 必须显式声明附带产物，否则 fat jar 顶替主产物（issue #217 第 3 条）。

    取默认值 `false` 的后果实测过：`lib/` 里收进的是 fat jar、包体从 18 MB 涨到 55 MB，
    而装配描述符与 `aha-cli-native` 那条线都会拿到错的包 —— 静默，只在体积和运行时才暴露。
    """
    problems: list[str] = []

    for kind, path in FATJAR_POMS.items():
        rel = path.relative_to(ROOT)
        if not path.exists():
            problems.append(f"缺 POM {rel}（{kind} 线的单体 jar 由它产出）")
            continue
        text = path.read_text(encoding="utf-8")
        if "maven-shade-plugin" not in text:
            problems.append(f"{rel}：找不到 maven-shade-plugin（{kind} 线应有单体 jar）")
            continue
        for tag, want in (("shadedArtifactAttached", "true"), ("shadedClassifierName", "standalone")):
            found = re.search(rf"<{tag}>([^<]*)</{tag}>", text)
            if not found:
                problems.append(
                    f"{rel}：shade 未显式声明 <{tag}>（默认值下 fat jar 会顶替项目主产物，issue #217）"
                )
            elif found.group(1).strip() != want:
                problems.append(
                    f"{rel}：<{tag}> 应为 {want}，实际 {found.group(1).strip()!r}（issue #217）"
                )

    return problems


def check_snapshot_manifest() -> list[str]:
    """快照清单与它的消费方必须一致（见模块 docstring）。"""
    if not SNAPSHOT_MANIFEST.exists():
        return []  # 尚未引入（如 main / release 分支）⇒ 本检查随之跳过
    rel = SNAPSHOT_MANIFEST.relative_to(ROOT)
    labels, groups = read_snapshot_manifest(SNAPSHOT_MANIFEST)
    if not labels or set(groups) != {"a", "b", "c"} or not all(groups.values()):
        return [
            f"{rel} 无法解析出「标签 + A/B/C 三组路径」——"
            "清单形状变了，请同步 bin/CheckScripts.py 的 self-check 逻辑"
        ]

    problems: list[str] = []

    # ① 标签集合：与仓库里真实存在的 7 个逐字一致（含大小写）
    missing = [name for name in SNAPSHOT_LABELS if name not in labels]
    extra = [name for name in labels if name not in SNAPSHOT_LABELS]
    if missing or extra:
        problems.append(
            f"{rel} 的标签与仓库里真实存在的 7 个不一致："
            f"缺少 {missing}、多出 {extra}（GitHub 上的标签是既有事实，清单必须照抄）"
        )

    # ② C 组必须含兜底项 `**`，否则「未列到的一切」没有落点
    if "**" not in groups["c"]:
        problems.append(f"{rel} 的 C 组必须包含 `**`（未列到的一切按 A 组处理，保守兜底）")

    # ③ 全仓标签字面量：任何文件里出现的标签都必须与清单逐字一致
    listed = set(labels)
    # 豁免「权威侧」的两个文件：清单本身是来源，本脚本的 SNAPSHOT_LABELS 是「GitHub 上的既有事实」
    # ——它们**必须**写着标签名，若一并扫描就会自判违规（本检查初版就踩了这一脚，反向验证抓出来的）。
    authorities = {SNAPSHOT_MANIFEST, Path(__file__).resolve()}
    for path, text in maybe_scannable_files():
        if path.resolve() in authorities:
            continue
        for number, line in enumerate(text.splitlines(), 1):
            for match in SNAPSHOT_LABEL_RE.finditer(line):
                literal = match.group(0)
                if literal not in listed:
                    problems.append(
                        f"{path.relative_to(ROOT)}:{number} 出现清单之外的标签 {literal!r}——"
                        f"标签名必须与 {rel} 逐字一致（含大小写）；"
                        "写错的表现是「打了标签却毫无反应」，不报错、只是没反应"
                    )

    # ④ 快照工作流：必须引用清单，且 `paths:` 字面量必须在清单里
    allowed = set(groups["a"]) | set(groups["b"]) | set(groups["c"])
    for workflow in sorted((ROOT / ".github" / "workflows").glob("*.yml")):
        text = workflow.read_text(encoding="utf-8")
        if not (SNAPSHOT_LABEL_RE.search(text) or SNAPSHOT_MANIFEST_REF in text):
            continue
        wrel = workflow.relative_to(ROOT)
        if SNAPSHOT_MANIFEST_REF not in text:
            problems.append(
                f"{wrel} 像是快照工作流，但没有引用 `{SNAPSHOT_MANIFEST_REF}`——"
                "路径清单是单一来源，工作流里不许再写一套（否则两处必然漂移）"
            )
        for number, literal in paths_block_literals(text):
            if literal not in allowed:
                problems.append(
                    f"{wrel}:{number} 的 paths 字面量 {literal!r} 不在 {rel} 里——"
                    "清单是单一来源：要么把它写进清单（并说明归 A 还是 B），要么改这里"
                )
        # ⑤ 快照工作流**不得**在 `pull_request` 上用 paths/paths-ignore：
        #    那是在触发层丢事件，「只改文档 + 新打触发标签」的显式请求会进不来（实测 #203）。
        for number, key in pull_request_paths_filters(text):
            problems.append(
                f"{wrel}:{number} 的 `pull_request.{key}` 在**触发层**过滤事件——"
                "只改文档的 PR 就算新打了触发标签（显式请求）也进不来，"
                "现象是「打了标签毫无反应」（2026-10-15 实测 PR #203）。"
                "快照工作流一律去掉该过滤：让 plan 作业先跑，昂贵的步骤按 should_build 门控"
            )

        # ⑥ 快照工作流的并发组必须按「事件类别」分开：只按 ref / PR 号分组时，
        #    `cancel-in-progress` 会让后到的 synchronize 取消先到的 labeled
        #    （显式请求被吞，2026-10-15 实测 PR #203）。
        expr = concurrency_group(text)
        divides_events = "github.event_name" in (expr or "") or "github.event.action" in (expr or "")
        if expr is not None and not divides_events:
            problems.append(
                f"{wrel} 的 `concurrency.group` 没有区分事件类别（{expr!r}）——"
                "labeled 与 synchronize 会落在同一个并发组，后到的自动事件会取消先到的显式请求；"
                "请按事件类别分出 `'explicit'` / `'auto'` 两组（见 BuildSpec §11.2）"
            )

    return problems


def check_yaml_shell_vars(path: Path) -> list[str]:
    """YAML（主要是 GitHub Actions 的 shell 片段）：`$VAR` 后不得紧跟非 ASCII。

    来历（2026-10-08 实测，同一条规则踩了两次）：

      * macOS runner 的 bash 把 `$bin（` 解析成变量名 `bin（`，`set -u` 下直接
        `bin（: unbound variable` 退出——步骤的 outputs 随之丢失，macos 镜像包没上传；
      * `Release.yml` 里 `$LABEL）、$target（` 等同一写法。

    规避方式只有一个：变量后跟中文 / 全角字符时写 `${VAR}`。
    GitHub 的 `${{ ... }}` 表达式与 `\\$` 转义不会命中本规则。
    """
    problems: list[str] = []
    rel = path.relative_to(ROOT)
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return [f"{rel} 不是 UTF-8，无法检查 shell 变量写法"]

    for number, line in enumerate(text.splitlines(), 1):
        for match in SHELL_VAR.finditer(line):
            end = match.end()
            if end < len(line) and ord(line[end]) > 127:
                name = match.group(1)
                problems.append(
                    f"{rel}:{number} 变量 `{name}` 后紧跟非 ASCII 字符 `{line[end]}`；"
                    f"部分平台（macOS bash 3.2）会把它并进变量名 → unbound variable。"
                    f"请写成 `${{{name}}}`"
                )
    return problems


#: 对 `inputs.*` 做宽松布尔比较 —— `null != false` 在 GitHub 表达式里是 **false**（issue #213）。
INPUT_BOOL_COMPARE_RE = re.compile(
    r"\binputs\.[A-Za-z_][A-Za-z0-9_-]*\s*(?:!==|===|!=|==)\s*(?:true|false)\b"
)


def check_yaml_input_bool_compare(path: Path) -> list[str]:
    """工作流里禁止对 `inputs.*` 做 `== / != true|false` 的宽松比较。

    来历（2026-10-15 实测，issue #213）：`build-publish` 的门写成
    `inputs.publish != false && inputs.publish != 'false'`，意图是「push / dispatch 默认发布，
    只有显式 false 才跳过」。但 `push` 事件**没有 `inputs`** ⇒ `inputs.publish` 为 `null`；
    GitHub 表达式做宽松比较时把两侧折算成数字（`null → 0`、`false → 0`）
    ⇒ `null != false` 求值为 `0 != 0` = **false** ⇒ 作业被**静默跳过**：
    dev 的 JVM 预发行包自 2026-10-14 06:52 起再没发布过（#179 引入，4 天后才被发现）。

    正确做法：把判断挪进脚本，归一成 `true` / `false` 字符串，再用
    `needs.<job>.outputs.<x> == 'true'` —— 字符串比较没有这个折算坑。
    """
    problems: list[str] = []
    rel = path.relative_to(ROOT)
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return [f"{rel} 不是 UTF-8，无法检查 inputs 布尔比较"]
    for number, raw in enumerate(text.splitlines(), 1):
        # 剥掉注释再匹配（与 paths_block_literals / pull_request_paths_filters / concurrency_group 同一惯例）：
        # 说明性注释里会**引用** `inputs.x != false` 这种反面例子，那是文档不是代码（本守卫初版就误报了它）。
        line = raw.split("#", 1)[0]
        for match in INPUT_BOOL_COMPARE_RE.finditer(line):
            problems.append(
                f"{rel}:{number} 对 `inputs` 做了宽松布尔比较（`{match.group(0)}`）——"
                "`push` 事件没有 inputs、`inputs.x` 为 null，而 GitHub 把 null 与 false 都折算成 0，"
                "`null != false` 求值为 false、作业被**静默跳过**（issue #213 实测：dev 预发行包停发 4 天）。"
                "请把判断挪进脚本归一成 `true` / `false` 字符串，再用 `needs.<job>.outputs.<x> == 'true'`"
            )
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description="AHA 脚本文件规约检查")
    parser.add_argument("--verbose", action="store_true", help="输出每个文件的检查结果")
    args = parser.parse_args()

    problems: list[str] = []
    checked = 0

    groups = collect()
    for group, checker in (
        ("bat", check_bat),
        ("sh", check_sh),
        ("py", check_py),
        ("ps1", check_ps1),
        ("xml", check_xml),
        ("yml", check_yaml_shell_vars),
    ):
        for path in groups[group]:
            checked += 1
            found = checker(path)
            problems.extend(found)
            if args.verbose:
                print(f"  {'❌' if found else '✅'} {path.relative_to(ROOT)}")

    snap_verified = check_snapshot_verified_contract()
    problems.extend(snap_verified)
    layout = check_package_layout()
    problems.extend(layout)
    shade = check_shade_attached()
    problems.extend(shade)
    ignored = check_ignored_sources()
    problems.extend(ignored)
    parity = check_native_update_parity()
    problems.extend(parity)
    snap_parity = check_snapshot_fetch_parity()
    problems.extend(snap_parity)
    snapshot = check_snapshot_manifest()
    problems.extend(snapshot)
    input_compare: list[str] = []
    for path in groups["yml"]:
        input_compare.extend(check_yaml_input_bool_compare(path))
    problems.extend(input_compare)

    print(f"\n检查 {checked} 个脚本文件（含 {len(groups['ps1'])} 个 PowerShell、"
          f"{len(groups['xml'])} 个 XML、{len(groups['yml'])} 个 YAML）")
    print(f"检查被 .gitignore 忽略的源码文件：{'❌' if ignored else '✅'}")
    print(f"检查快照自测留痕契约（context/命令/权限/清单编号/必测项 0 token）：{'❌' if snap_verified else '✅'}")
    print(f"检查发行包形态（包根平铺、无 lib//bin/、jar 去版本号）：{'❌' if layout else '✅'}")
    print(f"检查 shade 附带产物声明（防 fat jar 顶替主产物）：{'❌' if shade else '✅'}")
    print(f"检查原生取件两套实现的命名契约：{'❌' if parity else '✅'}")
    print(f"检查快照取件与判定的命名契约：{'❌' if snap_parity else '✅'}")
    print(f"检查快照清单与消费方一致性：{'❌' if snapshot else '✅'}")
    print(f"检查工作流的 inputs 宽松布尔比较：{'❌' if input_compare else '✅'}")
    if problems:
        print("\n[问题]")
        for problem in problems:
            print(f"  ❌ {problem}")
        print(f"\n共 {len(problems)} 个问题")
        return 1
    print("\n✅ 全部脚本符合跨平台规约")
    return 0


if __name__ == "__main__":
    sys.exit(main())
