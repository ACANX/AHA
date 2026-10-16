# Windows 终端与 Shell 指南

**文档版本**：v1.0.0
**状态**：冻结
**生效日期**：2026-10-16
**最后更新**：2026-10-16
**负责人**：@ACANX / CNXNC
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-16 | 初版（issue #117 真机排查的沉淀）：三层模型、代码页与 UTF-8、环境判定命令、WT 会话还原、脚本选型，以及与 AHA 既有实践的对应 | @ACANX / CNXNC |

---

## 1. 这份文档解决什么

AHA 在 Windows 上有 CLI 原生包（`aha-cli-native.exe`）与一批 `.cmd` / `.ps1` 脚本。
真机使用中反复出现同一类困惑：

- 中文乱码；启动标志从像素风退化成纯 ASCII 线框；
- "右键打开终端却弹出两个窗口"；
- `.ps1` 双击不能运行；
- 分不清 Windows Terminal、cmd、PowerShell 各自是什么、谁管什么。

这些**都不是 AHA 的功能缺陷**，而是 Windows 的「终端 / Shell / 代码页」三层模型决定的。
本文把结论、判定命令与选型建议固定下来，供后续开发、发行与排障直接引用。

> 适用读者：在 Windows 上开发、打包、发版或排障 AHA 的人。

---

## 2. 三层模型：终端 / 桥 / Shell

很多人把 Windows Terminal、cmd、PowerShell 当成"同类三选一"，其实它们是**三层**：

| 层 | 职责 | Windows 上的成员 |
|---|---|---|
| **终端（Terminal）** | 开窗口、画字符、颜色、字体、键盘、标签页、分屏 | `conhost.exe`（传统）、**Windows Terminal**（新） |
| **桥（ConPTY）** | 让"以为连着传统控制台"的程序跑在新终端里 | Windows 10 1809 起的伪终端 API |
| **Shell** | 解析你敲的命令并执行 | `cmd`、Windows PowerShell 5.1、PowerShell 7、WSL bash、Git Bash… |

运行流程：终端启动 → 通过 **ConPTY** 拉起某个 **Shell**（如 `powershell.exe`）→
该程序以为自己连着传统控制台，实际画面由终端渲染。

由此得到三条常用结论：

1. **Windows Terminal 不是 Shell**，它是新的**终端窗口**；新的 Shell 是 **PowerShell 7**。
2. **一个终端可以同时跑多个 Shell**：WT 的"配置文件（profile）"就是"哪个 Shell + 起始目录 + 外观"，
   可以在标签页 / 分屏里各开一个（cmd、PS 5.1、PS 7、WSL、Git Bash）。
3. **终端不改变 Shell 与程序的行为**：换终端只改"显示与交互"，不改"程序写出什么字节"。

> 这在 Linux 上完全一样：`gnome-terminal` / `kitty` 是终端，`bash` / `zsh` 是 Shell。
> "终端与 Shell 分层"不是 Windows 特有，只是 Windows 的历史包袱更重、层数更多。

---

## 3. 终端：conhost 与 Windows Terminal

### 3.1 conhost（Windows 7 起）

所有 Console 程序（cmd、PowerShell、Python、以及其他 exe）**实际都在跟 `conhost.exe` 说话**，
不是直接跟屏幕说话。conhost 同时承担 API、输入与渲染，架构耦合深，因此难以做标签页、
分屏、好字体与 emoji 渲染。

### 3.2 Windows Terminal + ConPTY

Windows Terminal（2019 起，开源）不直接跑 Console 程序，而是通过 **ConPTY** 接入：
ConPTY 假扮成 conhost，程序**一行代码不改**即可运行；WT 只负责渲染与交互。
于是有了标签页、分屏、GPU 加速、主题与更好的 Unicode / emoji 支持。

Windows 11 内置 Windows Terminal，并可将其设为**默认终端应用**——这是微软"统一终端"的一步。

### 3.3 WT 能解决 / 不能解决

| 能解决 | 不能解决 |
|---|---|
| 宽字符 / emoji / 框线渲染更好（DirectWrite + GPU + 字体回退） | **代码页**：WT 不提供"把控制台改成 UTF-8"的开关 |
| ANSI / VT 完整度更高（真彩色、bracketed paste、光标、鼠标事件） | **程序写出的字节**：程序在 1252 下把中文编成 `?`，终端还原不回来 |
| 标签页、分屏、主题、搜索、复制粘贴 | **AHA 的符号降级判定**：`symbols` 由 AHA 自己按字符集算，与终端无关 |
| GPU 加速 | — |

**一句话：WT 能让"对的字节"更好看，不能让"错的字节"变对。**

> 依赖 AHA 渲染的部分可另见
> [NativeRenderDiagnosticsGuide.md](NativeRenderDiagnosticsGuide.md) 与
> [TUIDesign.md](../Design/TUIDesign.md)。

---

## 4. Shell：cmd / Windows PowerShell 5.1 / PowerShell 7

| | `cmd.exe` | `powershell.exe` | `pwsh.exe` |
|---|---|---|---|
| 正式名 | 命令提示符 | Windows PowerShell **5.1** | **PowerShell 7.x** |
| 运行时 | 原生 | .NET **Framework** | .NET（Core，跨平台） |
| 交付 | Windows 内置 | **Windows 内置组件** | **独立产品**（自行安装） |
| 定位 | DOS 遗产，兼容保底 | 冻结的稳定层，只做安全维护 | 官方推荐的自动化工具，持续演进 |
| 跨平台 | 仅 Windows | 仅 Windows | Windows / Linux / macOS |

**为什么 5.1 不被替换、7 不内置**（微软的明确立场是 **side-by-side，不替换**）：

- 5.1 是 Windows 的组成部分：组策略、WinRM、系统模块、大量第三方安装器与老脚本都假设它存在且行为是 .NET Framework 那套；替换等于让整个生态做一次不兼容迁移。
- 7 是独立产品：几个月一个小版本，与 Windows 的年度节奏冲突；自带一份 .NET 运行时，内置进系统既大又难维护，还会剥夺企业的升级可控性。
- 所以 Windows 11 上看到的仍是 5.1，**这是设计，不是没跟上**。

安装 7：

```powershell
winget install --id Microsoft.PowerShell
```

之后 `pwsh` 启动 7，`powershell` 仍是 5.1。

---

## 5. 代码页与 UTF-8

### 5.1 机制

Windows 控制台（conhost）**按代码页解释字节**：中文系统默认 936（GBK），英文系统常为 437 / 1252。
程序写出的"字节"和控制台"按什么解码"是两件事——只要不一致，就会乱码。

- `chcp` 查看 / 切换当前控制台代码页；`65001` 即 UTF-8。
- JVM / GraalVM native 的 `System.out` 是**字节流 + Charset**：JDK 18+ 在 Windows 上把
  `stdout.encoding` 设为控制台代码页对应的编码。**它不走宽字符 API**，所以控制台是 1252 时，
  中文会在程序侧就被编码器替换成 `?`。
- CPython（`_WindowsConsoleIO`）、.NET（`Console.Write`）、Node.js（libuv）走的是
  **`WriteConsoleW` 宽字符 API**，绕过代码页，因此更稳。

### 5.2 乱码的两种形态

| 现象 | 成因 |
|---|---|
| 一堆 `?`（`????? AHA ?????`） | 程序按**窄字符编码器**写，目标字符编不出 → 替换为 `?`。**字节本身就是错的**，换终端也救不回 |
| 形如 `ä½ å¥½` 的乱码 | 程序写了 UTF-8 字节，但控制台按 1252/936 解码 |

### 5.3 两个容易混淆的"编码"

| 名字 | 管什么 | 默认值 |
|---|---|---|
| `$OutputEncoding` | PowerShell **把管道输入喂给外部程序 stdin** 时用的编码 | PS 5.1：**ASCII**；PS 7+：**UTF-8** |
| `[Console]::OutputEncoding` | PowerShell **读写控制台**、以及捕获 / 解码外部程序输出时用的编码 | 两个版本在 Windows 上**都跟随系统代码页** |

### 5.4 为什么 PowerShell 不默认 UTF-8

- `[Console]::OutputEncoding` 是 .NET API，必须与控制台代码页**保持一致**，否则 PowerShell 自己写的字与外部程序写的字会各说各话；
- 而**控制台代码页由 Windows 决定**（系统区域设置），不由 Shell 决定——PowerShell 只能"跟随"或"显式覆盖"；
- 把系统默认改成 UTF-8 会让大量仍按 ANSI 代码页工作的老程序错乱，因此微软只提供**可选**开关
  （区域设置里的 "Beta: Use Unicode UTF-8 for worldwide language support"），**至今仍标 Beta、不做默认**。

**结论：这件事的球在 Windows，不在 PowerShell。** PS 7 已在自身职责内尽量靠拢（`$OutputEncoding`），
但控制台读写那一环必须与系统保持一致。

### 5.5 四种解决路径

| 路径 | 做法 | 适用 / 代价 |
|---|---|---|
| **A. 用户临时** | `chcp 65001` 后再启动程序 | 只影响本会话；对 cmd 有效 |
| **B. 用户持久** | PowerShell profile 里设 `[Console]::OutputEncoding = [Text.Encoding]::UTF8`（以及 `$OutputEncoding`） | 一劳永逸；需两边 profile（5.1 / 7） |
| **C. 系统级** | 区域设置的 "Beta: Use Unicode UTF-8…" | 全系统生效；**有兼容风险**，微软仍标 Beta |
| **D. 程序侧** | 程序检测到自己是控制台且代码页非 UTF-8 时，调 `SetConsoleOutputCP(65001)` / `SetConsoleCP(65001)`，退出时恢复 | 对用户零配置；**注意"非控制台时绝不改"**，否则污染管道 / CI 会话 |

> **AHA 现状**：采用「探测 + 提示 + 降级」——`OutputEncoding` 探测到无法表示 CJK 时打印
> `[warn]`（并给出 CMD / PowerShell 两条切换命令），同时把装饰符号降级、启动标志回退纯 ASCII。
> 详见第 9 节。

---

## 6. 用命令判断当前环境

| 想知道 | 命令 | 判读 |
|---|---|---|
| 是否 **Windows Terminal** | `echo %WT_SESSION%`（cmd） / `$env:WT_SESSION`（PowerShell） / `echo $WT_SESSION`（bash） | 有值 = WT；空 = conhost |
| 是哪个 **PowerShell** | `$PSVersionTable.PSVersion` | `5.x` = Windows PowerShell；`7.x` = PowerShell 7 |
| 是否 **cmd** | `echo %COMSPEC%` | 输出 `…\cmd.exe` = cmd |
| 当前 **代码页** | `chcp` | `65001` = UTF-8 |
| **父 Shell 是 PowerShell（程序侧）** | 环境变量 `PSModulePath`；或进程树 | 启发式 |
| **PS 5.1 vs 7（程序侧）** | 父进程名（`powershell.exe` vs `pwsh.exe`）；或 `POWERSHELL_DISTRIBUTION_CHANNEL`（7 特有） | — |
| **stdout 是不是控制台（程序侧）** | `GetConsoleMode(GetStdHandle(STD_OUTPUT_HANDLE), …)`；或 Java 的 `System.console() != null` | 失败 = 管道 / 重定向 |

> 注意：WT 下 `GetConsoleMode` **也会成功**（ConPTY 假扮控制台），所以区分 WT / conhost
> 必须靠 `WT_SESSION`，不能靠 `GetConsoleMode`。

---

## 7. WT 的会话还原："为什么弹出两个窗口"

现象：右键"在终端中打开"后出现**两个** Windows PowerShell 窗口，其中一个带标记：

```text
[已还原 2026/10/16 10:17:42]
```

`[已还原 <时间>]` 表示**这是被"还原"出来的旧会话**，不是新开的那一个。来源通常是：

1. **Windows Terminal 的"启动时还原会话"**：
   打开 WT 设置（`Ctrl` + `,`）→ 左侧 **"启动"（Startup）** → "启动时 / When Terminal starts"
   改为 **"从默认配置文件启动"**（不要选"还原上一个会话"）。
   界面里找不到时，直接改 `settings.json`（`Ctrl` + `Shift` + `,` 打开），
   搜索 `firstWindowPreference`，把值设为 `"defaultProfile"`（原值多为 `"persistedWindowLayout"`）。
2. **PowerShell profile 里被加了脚本**：若 `$PROFILE` 中确实有打印"已还原"之类的逻辑，那是脚本行为，不是终端。

若怀疑是脚本：检查两处 profile（5.1 与 7 各一份），并直接搜关键字。

```powershell
Get-Content $PROFILE -ErrorAction SilentlyContinue
Get-Content $PROFILE.CurrentUserAllHosts -ErrorAction SilentlyContinue
Get-ChildItem "$env:USERPROFILE\Documents\WindowsPowerShell","$env:USERPROFILE\Documents\PowerShell" `
  -Recurse -Include *.ps1 -ErrorAction SilentlyContinue |
  Select-String -Pattern "已还原"
```

---

## 8. 脚本选型：`.bat` / `.cmd` / `.ps1`

### 8.1 本质

- `.bat` 与 `.cmd`：**同一种东西**，都由 `cmd.exe` 解释；
- `.ps1`：PowerShell 脚本，由 `powershell.exe` 或 `pwsh.exe` 解释，是另一套语言。

### 8.2 微软立场与场景对照

官方已不再把批处理作为新自动化的推荐；**新写的 Windows 自动化推荐 PowerShell**。
但批处理不会消失，因为极简启动器、老环境、以及"给 `.ps1` 做双击入口"都离不开它。

| 场景 | 建议 | 原因 |
|---|---|---|
| 系统管理 / 自动化 / 配置 / CI | **`.ps1`** | 对象管道、模块、try/catch、JSON/XML/CSV、WMI/CIM、远程 |
| 跨平台脚本 | **PowerShell 7（`pwsh`）** | 同一份脚本跑 Win / Linux / macOS |
| **双击运行的入口 / 包装器** | **`.cmd`** | `.cmd` 有"运行"关联；`.ps1` 双击只是打开编辑器 |
| 调用一个 `.ps1` | **`.cmd` 包一层** | 顺带解决执行策略：`powershell -ExecutionPolicy Bypass -File …` |
| 最小环境 / 恢复模式 / 无 PowerShell | **`.bat` / `.cmd`** | 不依赖 PowerShell 运行时 |
| 复杂参数解析、错误处理、字符串处理 | **`.ps1`** | cmd 语法做这些非常痛苦 |
| 极简、零依赖、启动最快 | **`.cmd`** | 不加载 PowerShell 引擎 |

### 8.3 `.bat` 还是 `.cmd`

功能上在 Win10/11/Server 里**基本等价**。实务建议：

- **新写就用 `.cmd`**（语义上明确是 NT / cmd 脚本）；
- **同一目录不要 `.bat` / `.cmd` 同名并存**：`%PATHEXT%` 里 `.BAT` 排在 `.CMD` 之前，
  `call foo`（不带扩展）会先命中 `.bat`，于是"改了 `.cmd` 却没生效"——常见坑；
- 只有要兼容 DOS / 极老环境时才用 `.bat`。

### 8.4 两个必须记住的坑

**① 编码**

- `.bat` / `.cmd`：cmd 按 **OEM 代码页**解释（中文 936）。AHA 的约定是**纯 ASCII + CRLF + 无 BOM**，
  中文说明放到 README；需要 UTF-8 时先 `chcp 65001`。
- `.ps1`：PS 5.1 对**无 BOM** 的文件按 **ANSI** 解析，PS 7 按 UTF-8。
  因此**含非 ASCII 的 `.ps1` 必须带 UTF-8 BOM**（AHA 用 `bin/CheckScripts.py` 强制）。

**② 执行策略与双击**

- `.cmd` / `.bat`：**不受执行策略限制**，双击即跑（既是优点也是风险点，杀软 / App Control 会盯）。
- `.ps1`：受 **Execution Policy**（客户端默认 `Restricted`）+ AMSI 扫描；双击 `.ps1` 默认关联是**记事本**，
  即"打开编辑"而非"运行"。想运行：

```powershell
# 右键脚本 → "使用 PowerShell 运行"（进程级 Bypass）
# 或
powershell -ExecutionPolicy Bypass -File .\script.ps1
# 或放开当前用户（不建议设 Unrestricted）
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

正式分发建议给 PowerShell 脚本**签名**，不要把 `-ExecutionPolicy Bypass` 当万能药。

---

## 9. AHA 的实践与约定

| 事项 | 位置 / 做法 |
|---|---|
| 编码探测与降级 | `aha-cli` 的 `tty/OutputEncoding.java`（实测字符集能否编码 CJK 与装饰符号）、`tty/TerminalCapabilities.java`（tty / 色彩深度 / 宽度） |
| 中文无法表示时的提示 | `ChatCommand` 打印 `[warn]`，并给出 `chcp 65001` 与 PowerShell 两条命令；**提示本身用纯 ASCII**，否则提示也读不了 |
| 启动标志两档 | `render/StartupPixelLogo.java`（像素风，依赖 `▀` 与 256 色）与 `render/StartupLogo.java`（纯 ASCII 线框风，兜底）；`symbols && colored` 才给像素风机会 |
| 脚本规约 | `bin/CheckScripts.py`：`.bat`/`.cmd` 纯 ASCII + CRLF + 无 BOM；`.ps1` 含非 ASCII 必须带 UTF-8 BOM |
| 双击入口 | `Dist/*.cmd` 包一层同名的 `Dist/*.ps1`（`.ps1` 不能双击，`.cmd` 能） |
| 原生包参数清单 | `aha-cli-native/src/native/native-image-args*.txt`，结论登记在 [BuildSpec.md](../DevSpec/BuildSpec.md) §10 |

**为什么不自动 `chcp 65001`**：见 `OutputEncoding` 的 Javadoc——启动脚本里自动 `chcp` 时，
宿主 Shell 已缓存解码器，子进程改代码页后宿主仍按旧编码解释，反而更乱。
因此只做「探测 + 提示 + 降级」。若要进一步改善，第 5.5 节的**路径 D**（程序侧设 CP + 退出恢复）
是后续可选项，需先在真机上用 cmd / PS 5.1 / PS 7 / WT 四种组合验证。

脚本编写与提交前检查见 [CommandCheatsheet.md](CommandCheatsheet.md)、
[GraalVMInActionsGuide.md](GraalVMInActionsGuide.md)。

---

## 10. 相关文档

- [TUIDesign.md](../Design/TUIDesign.md)：终端渲染与降级矩阵
- [CliNativeDesign.md](../Design/CliNativeDesign.md)：CLI 原生镜像的资源与参数清单
- [NativeRenderDiagnosticsGuide.md](NativeRenderDiagnosticsGuide.md)：原生渲染的真机取证
- [GraalVMInActionsGuide.md](GraalVMInActionsGuide.md)：GraalVM / GitHub Actions 参考
- [BuildSpec.md](../DevSpec/BuildSpec.md)：构建规范（含原生镜像 §10）
- [DocumentationSpec.md](../DevSpec/DocumentationSpec.md)：文档规范
- [Troubleshooting/README.md](../Troubleshooting/README.md)：问题排查档案索引
- [AGENTS.md](../../AGENTS.md)：项目约定（含「本地不跑 Maven」等强制项）
