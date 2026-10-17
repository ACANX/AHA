# TS-2610-030 · issue #48 调研——原生镜像的字体渲染差异

- **日期**：2026-10-09（后续与缓解落地见第 6 节，追加于 2026-10-13）
- **关联 Issue**：issue #48（原生桌面端字体渲染 / 字体与 JVM 模式不一致）、issue #44（父级）
- **关联 PR**：PR #58（诊断铺路）
- **分支**：`feat/issue-48-render-diagnostics`（诊断）、`fix/issue-44-native-text-greyscale`（缓解）

---

## 1. 背景

ACANX 提出两个猜测，要求调研：

> 「这个问题有没有解决办法，是不是 DPI 缩放或者编译受的配置的问题？」

现象（#48 正文）：原生包的字**发虚、字形偏细**，肉眼明显不如 JVM 模式锐利；同机、同配置、同窗口尺寸。

**本次只做调研与铺路，不改渲染行为**——先把「原生包到底走的哪条路」变成可观测的事实。

## 2. 调研过程与证据

### 2.1 第一步：把原生包真跑起来

这是第一次能在本机验证（此前一直因为「开发机没有 Windows 图形环境」而无法复现）。
`V0.1.1.00055-aha-desktop-native` 除了 Windows 包，**还有 `ubuntu-amd64` 包**，
而本机 WSL 有 WSLg（`DISPLAY=:0` 可用），因此可以直接跑 Linux 原生包。

在 `/tmp/n48/` 解包后执行（注意用 `--repo`，`/tmp` 不是 git 仓库）：

```bash
DISPLAY=:0 timeout 20 ./aha-desktop-native -Dprism.verbose=true -Djavafx.verbose=true
```

**拿到的真实日志**（节选）：

```
Prism pipeline init order: es2 sw
Using Double Precision Marlin Rasterizer
Opting in for HiDPI pixel scaling
Prism pipeline name = com.sun.prism.es2.ES2Pipeline
Loading ES2 native library ... prism_es2
Loaded library /libprism_es2.so from resource
	succeeded.
GLFactory using com.sun.prism.es2.X11GLFactory
Failed Graphics Hardware Qualifier check.
System GPU doesn't meet the es2 pipe requirement
*** Fallback to Prism SW pipeline
Prism pipeline name = com.sun.prism.sw.SWPipeline
Loaded library /libprism_sw.so from resource
Loaded library /libglass.so from resource
Loaded library /libglassgtk3.so from resource
Loaded library /libjavafx_font.so from resource
Loaded library /libjavafx_font_freetype.so from resource
Loaded library /libjavafx_font_pango.so from resource
```

**已经能确认三件事**：

1. **原生镜像里 JavaFX 的原生库加载链路是通的**——`NativeLibLoader` 能从镜像资源里
   解出 `.so` 并成功加载（`Loaded library … from resource`）；
2. 本机回退到 `SWPipeline`，**是因为 WSL 没有 GPU**（`System GPU doesn't meet the es2 pipe
   requirement`），**属预期行为，不能据此推断 Windows**；
3. `Opting in for HiDPI pixel scaling` 说明 HiDPI 处理是**打开**的。

### 2.2 第二步：证伪「软件管线没有 LCD 文本」

最初假设：#48 是「回退到软件管线 → 没有次像素抗锯齿 → 发虚」。
这个假设**必须验证再采信**，于是写了一个最小探针（`/tmp/n48/lcd/LcdProbe.java`）：
同一段文字、同一管线（`-Dprism.order=sw`），只改 `prism.lcdtext`，快照成 PNG 比像素。

```bash
java --module-path /tmp/jfx25lib --add-modules javafx.controls \
  -Dprism.order=sw -Dprism.lcdtext=true  LcdProbe.java   # → sw-true.png
  -Dprism.order=sw -Dprism.lcdtext=false LcdProbe.java   # → sw-false.png
```

**结果**：

| 文件 | 大小 | md5 |
| --- | --- | --- |
| `sw-true.png`（lcdtext=true） | 4375 B | `d478d9708df4a35d8c72e7d37ff05b05` |
| `sw-false.png`（lcdtext=false） | 3025 B | `78668459b11627a6d302e9fb50e16739` |

**两者的渲染结果不同**，且 `lcdtext=true` 的产物更大（次像素渲染会给每个像素的
R/G/B 留下不同值，PNG 更难压）。

> **结论：软件管线同样支持 LCD 次像素抗锯齿。**
> 「管线回退 → 没有 LCD → 发虚」这条**不成立**，不能作为 #48 的解释。

顺带确认：`Font.getDefault().getFamily()` 在原生镜像里返回 **`System`**，
与 JVM 模式一致（至少 Linux 上如此），字体族**没有**在原生镜像里退化。

### 2.3 第三步：DPI 缩放

从 2.1 的日志看：

- `Opting in for HiDPI pixel scaling`（HiDPI 缩放已启用）；
- `AhaDesktopApp` 的窗口尺寸**已经**按 `Screen.getOutputScaleX/Y` 换算过
  （`StageLayout` 那段，见 `AhaDesktopApp` 第 25x 行）。

**所以「DPI 缩放没读到、按 100% 渲染再被系统放大」这条也缺少证据。**
真要说，唯一还没验证的是**字体栅格化那一侧的缩放**，而这需要真机数据。

### 2.4 顺手发现的真实缺陷（与 #48 未必相关，但要记）

每加载一个原生库之前，都会先报一次：

```
WARNING: java.lang.UnsatisfiedLinkError: Invalid URL for class:
    resource://app/17!/com/sun/glass/utils/NativeLibLoader.class
```

`NativeLibLoader` 会去读**自己的 `.class`**，而在原生镜像里资源的 URL 形如
`resource://app/17!/…`，**它按 `jar:file:` 解析失败**。随后会走另一条路径并成功
（`Loaded library … from resource`），**所以是良性警告、不是崩溃原因**；
但 `native-image-args.txt` 里那条 `-H:IncludeResources=com/sun/glass/utils/NativeLibLoader\.class`
显然没达到预期效果，值得后续单独收掉。

### 2.5 本地先把诊断代码跑一遍（避免真机拿到「不可用」）

诊断代码写完**先用 JVM 模式实跑**，结果发现字体工厂那一项打的是「不可用」——
`com.sun.javafx.font` 是 `javafx.graphics` 的**非导出包**，模块系统直接拒绝反射。
补上 `setAccessible(true)` 后实测（Linux / WSL，JVM 模式）：

| 启动参数 | 输出 |
| --- | --- |
| 无 | `渲染诊断：字体实现工厂=不可用（InaccessibleObjectException）` |
| `--add-opens javafx.graphics/com.sun.javafx.font=ALL-UNNAMED` | `渲染诊断：字体实现工厂=com.sun.javafx.font.freetype.FTFactory` |

**这一步顺带给出了本 Issue 最有希望的判据。** Windows 上 JavaFX 有**两条字体栅格化路径**：

- `com.sun.javafx.font.directwrite.DWFactory` —— 走系统 **DirectWrite**，字形与系统 UI 一致；
- `com.sun.javafx.font.freetype.FTFactory` —— 走 JavaFX **内置 FreeType**，自己的 hinting。

**若原生包在该机器上打出的是 `FTFactory` 而 JVM 模式是 `DWFactory`，那就是字形走了另一条路**
——「笔画偏细、发虚」就能直接解释，而且**可修**（让 DirectWrite 在原生镜像里可用，
或明确接受 FreeType 并调整参数）。

本地 JVM 模式下的完整输出（供对照格式）：

```
Prism pipeline name = com.sun.prism.sw.SWPipeline
渲染诊断：属性 prism.order=sw prism.lcdtext=<默认> prism.text=<默认> glass.platform=<默认>
渲染诊断：JavaFX 版本=25+29 JVM=Java HotSpot(TM) 64-Bit Server VM
渲染诊断：默认字体 族=System 名称=System Regular 字号=13.0 可用字体族数=11
渲染诊断：字体实现工厂=com.sun.javafx.font.freetype.FTFactory
渲染诊断：字形度量 14px 文本宽=263.04 高=16.3 基线=13.0
渲染诊断：屏幕 outputScale=1.0x1.0 dpi=96.0 视觉边界=Rectangle2D [minX=0.0, minY=0.0, maxX=2560.0, maxY=1600.0, width=2560.0, height=1600.0]
```

> JVM 模式要拿到「字体实现工厂」这一项，需加
> `--add-opens javafx.graphics/com.sun.javafx.font=ALL-UNNAMED`；
> **原生镜像里没有这层限制，默认就能打出来**——这一项本就是给原生包用的。
> 取不到不算失败：字体族、字形度量、管线三项已足够定位。

## 3. 结论（回答 ACANX 的两个猜测）

| 猜测 | 结论 | 依据 |
| --- | --- | --- |
| **DPI 缩放** | **基本排除** | HiDPI 缩放已 opt-in；窗口尺寸已按 `outputScale` 换算（2.1 / 2.3） |
| **编译配置** | **有可能，但不是「管线回退到 SW」这一条** | 软件管线照样有 LCD 文本，已用像素实验证伪（2.2） |

剩下的最可能方向是**字体枚举 / 字形栅格化路径**：
Windows 上 JavaFX 走 `WinFontFinder` + GDI/ClearType 的 hinting，
而原生镜像里若字体枚举不完整或 hinting 参数不同，就会表现出
**「字形偏细、发虚」**——这与 #48 的文字描述（「笔画偏细」）更像，而不是纯抗锯齿问题。

**但目前仍是推断。** 要把它变成结论，必须拿到原生包在**真机**上的两组数据：
**实际管线**与**默认字体族 / 字号 / 字体族数量**。

> **2026-10-13 更新**：真机数据与像素对照已到手，**根因锁定为「抗锯齿方式」而非字体枚举**
> —— 见下文第 6 节。本节的两条「待排查方向」中，方向②（字体枚举）已被排除。

## 4. 下一步（本 PR 做的事）

1. **让渲染管线自己说话**：`AhaDesktopApp.main` 里默认打开
   `prism.verbose`（未显式设置时），JavaFX 会在启动日志里打出
   `Prism pipeline name = …`——**用户交日志即可，不必再改代码**；
2. **补 JavaFX 不打的项**：`start(Stage)` 里新增 `logRenderingDiagnostics()`，
   一次打全下面这几行（都以 `渲染诊断：` 开头，方便 `grep` 与逐行对账）：

   | 行 | 内容 | 它为什么能定位 #48 |
   | --- | --- | --- |
   | 属性 | `prism.order` / `prism.lcdtext` / `prism.text` / `glass.platform` | 区分「没设」与「设成了别的值」 |
   | 版本 | JavaFX 版本 + JVM 名 | 先排除「两边版本不同」 |
   | 字体 | 默认字体族 / 名称 / 字号 / 可用字体族数 | 字体枚举是否退化 |
   | **字体工厂** | `PrismFontFactory.getFontFactory()` 的实现类（Windows 上应为 `WinFontFactory` 一类） | **两边不同即锁定根因** |
   | **字形度量** | 同一段文字在 14px 下的宽 / 高 / 基线 | 给出数字，不靠肉眼比图 |
   | 屏幕 | `outputScale` / `dpi` / 视觉边界 | DPI 缩放的直接证据 |

   其中字体工厂走反射（`com.sun.javafx.font` 是非导出包），**已登记进
   `reachability-metadata.json`（439 → 440 条）**，并由 `NativeImageMetadataTest`
   新增一条守卫（12 → 13 条）看住——否则原生镜像里反射会失败、日志只打「不可用」，
   诊断等于白做。
3. 拿到真机数据后二选一：
   - 若原生包的字体族与 JVM 不同 → 在元数据里补字体枚举相关登记（`WinFontFinder` 等）；
   - 若字体族相同而字形仍细 → 比较 hinting / 抗锯齿设置，必要时显式
     `-Dprism.lcdtext=…` 并把结论写进 `DesktopNativeDesign.md` §5.3。

4. **配套材料（已就绪）**：
   - `Docs/Guide/NativeRenderDiagnosticsGuide.md` —— 可照做的操作手册（前提、日志位置、
     步骤、判定表、诊断行含义、常见问题）；
   - `Script/Python/CollectRenderDiagnostics.py` —— 一键收集：自动定位 `Log/AHA.log`、
     **只取最后一次启动**的片段、输出可直接粘贴的 Markdown 报告（已用含两次启动的假日志实测，
     旧片段被正确排除）；
   - `bin/AhaDesktop.{bat,sh}` 补 `--add-opens javafx.graphics/com.sun.javafx.font=ALL-UNNAMED`
     —— 否则 JVM 模式下「字体实现工厂」会因非导出包被模块系统拒绝，只打「不可用」。

> 注意：诊断代码**只加日志，不改渲染行为**——在拿到真机数据之前改渲染参数属于猜测，
> 与项目「先证伪再改」的做法不符。
> （**2026-10-13 更新**：真机数据已拿到，第 6 节据此落地了缓解措施；该注记只适用于本轮。）

## 5. 涉及文件清单

- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/AhaDesktopApp.java`
  （`main` 默认开 `prism.verbose`；新增 `logRenderingDiagnostics()`）
- `CHANGELOG.md`、`Docs/TODO.md`
- 调研产物（不入库，留在 `/tmp/n48/`）：`ubuntu.zip`、`aha-desktop-native`、
  `run.log`、`lcd/LcdProbe.java`、`lcd/sw-{true,false}.png`

## 6. 后续（2026-10-10 ~ 10-13）：根因锁定与临时缓解落地

### 6.1 真机数据：字体枚举、实现、管线三项全部排除

ACANX 在 `0.1.1.00061` 上给出原生与 JVM 的对照截图（两张 2560×1600、亮色，可直接逐像素对齐）。
诊断日志与像素统计合并后的结论：

| 项 | 原生实测 | 结论 |
| --- | --- | --- |
| Prism 管线 | `com.sun.prism.d3d.D3DPipeline`（D3D9Ex） | 正常，排除 |
| 字体实现 | `com.sun.javafx.font.directwrite.DWFactory` | 与系统同路径，排除 |
| 字体枚举 | 可用字体族 **248** | 完整，排除（第 3 节方向②被否掉） |
| **抗锯齿方式** | **无彩色边缘 ⇒ 灰度 AA** | **← 根因** |

像素统计（同一坐标系下直接比对）：

| 指标 | JVM 模式 | native | 比值 |
| --- | --- | --- | --- |
| **彩色边缘像素**（R/G/B 通道明显不等 ⇒ 次像素抗锯齿的证据） | **165,783** | **9,025** | **18.4×** |
| 暗像素（笔画覆盖量） | 122,662 | 204,260 | 0.60 |
| 亮度梯度总量 | 44,808,100 | 52,384,420 | 0.86 |
| 两图不同像素 | — | 648,241（15.9%） | — |

**判读**：JVM 的文字边缘有大量彩色像素（次像素/ClearType 的必然产物），native 几乎没有；
native 的暗像素反而多 67% —— 灰度抗锯齿把「部分覆盖」的像素均匀变暗，笔画边缘因此扩散，
这正是「发虚、偏细」的成因。**不是字体族、不是字体实现、不是管线、不是 DPI 缩放，
而是「抗锯齿方式」的差异：JVM = 次像素，native = 灰度。**

### 6.2 成因指向：DirectWrite 渲染参数在 closed-world 下读不到

`DWFactory` 走 DirectWrite，ClearType 取决于 `IDWriteRenderingParams`（由系统 / 显示器
ClearType 设置解析而来）。原生镜像里若这条读取路径失败，DirectWrite 退回默认渲染参数
（无 ClearType、gamma 1.0）⇒ 灰度抗锯齿。这与 **issue #60**（原生镜像读不到系统与驱动信息：
`Windows version 0.0 build 0`、D3D 驱动信息全 `null`）很可能是**同一类成因**：
运行期对系统状态的访问在 closed-world 编译下缺失。

### 6.3 本轮落地：临时缓解（不是根治）

根治要么补上渲染参数的读取（首选）、要么等上游修复，都不在本轮范围。
参照姊妹项目 LegionFanControl 已验证的做法（`NativeImageRenderDefaults`）：
**原生镜像内默认关闭 LCD 次像素合成、改走灰度抗锯齿**
（该项目的对照实验结论是「灰度路径在原生与 JVM 两个构建下逐项一致」），
并保留不改代码即可回退的开关。

- 实现：`aha-desktop/.../NativeRenderDefaults.java`，在 `AhaDesktopApp.main` 第一条语句调用
  （`prism.lcdtext` 只在 `PrismFontFactory` 静态初始化时读取一次，必须早于工具包启动）；
- 优先级：`-Dprism.lcdtext=…`（用户直接决定）＞ `-Daha.desktop.nativeTextGreyscale=false`
  （关闭本默认值）＞ 原生内程序默认写 `false`；JVM 版不受影响；
- 诊断：`logRenderingDiagnostics()` 增打「原生文本灰度默认 …；开关 …=…」，
  与既有的 `属性 prism.lcdtext=…` 一行配合，同时给出「值是多少」与「是谁设的」；
- 回退：`-Dprism.lcdtext=true`（单次）、`-Daha.desktop.nativeTextGreyscale=false`（关掉默认值）、
  删调用点或 `git revert`（永久）。

> **仍未修好根因**：原生的灰度与 JVM 的次像素不是同一观感；本措施消除的是「发虚、发糊」，
> 不是「与 JVM 逐像素一致」。上游修复或补上渲染参数读取后，**应删除该类与调用点**。
> 守卫与回退口径见 `NativeRenderDefaults` 类注释、`NativeRenderDiagnosticsGuide.md`
> 与 `DesktopNativeDesign.md` §6.1。

### 6.4 本轮涉及文件

- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/NativeRenderDefaults.java`（新增）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/AhaDesktopApp.java`（调用点 + 诊断行）
- `aha-desktop/src/test/java/com/acanx/module/aha/desktop/NativeRenderDefaultsTest.java`（新增）
- `Docs/Guide/NativeRenderDiagnosticsGuide.md`、`Docs/Design/DesktopNativeDesign.md` §6.1、
  `Docs/DevLog/20261013-08.md`、`CHANGELOG.md`

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- **本机首次具备复现条件**：`V0.1.1.00055-aha-desktop-native` 除了 Windows 包，
  **还有 `ubuntu-amd64` 包**，而本机 WSL 有 WSLg（`DISPLAY=:0` 可用）⇒ 可直接跑 Linux 原生包。
- **原生包的启动日志（原文节选）**：

  ```text
  Prism pipeline init order: es2 sw
  Using Double Precision Marlin Rasterizer
  Opting in for HiDPI pixel scaling
  Prism pipeline name = com.sun.prism.es2.ES2Pipeline
  Loading ES2 native library ... prism_es2
  Loaded library /libprism_es2.so from resource
  	succeeded.
  GLFactory using com.sun.prism.es2.X11GLFactory
  Failed Graphics Hardware Qualifier check.
  System GPU doesn't meet the es2 pipe requirement
  *** Fallback to Prism SW pipeline
  Prism pipeline name = com.sun.prism.sw.SWPipeline
  Loaded library /libprism_sw.so from resource
  Loaded library /libglass.so from resource
  Loaded library /libglassgtk3.so from resource
  Loaded library /libjavafx_font.so from resource
  Loaded library /libjavafx_font_freetype.so from resource
  Loaded library /libjavafx_font_pango.so from resource
  ```

  由此**确认三件事**：① 原生镜像里 JavaFX 的**原生库加载链路是通的**
  （`Loaded library … from resource`）；② 本机回退 `SWPipeline` 是因为 **WSL 没有 GPU**
  （`System GPU doesn't meet the es2 pipe requirement`），**属预期行为，不能据此推断 Windows**；
  ③ `Opting in for HiDPI pixel scaling` 说明 **HiDPI 处理是打开**的。
- **证伪「软件管线没有 LCD 文本」**（最小探针 `LcdProbe.java`：同一段文字、同一管线
  `-Dprism.order=sw`，只改 `prism.lcdtext`，快照成 PNG 比像素）：

  | 文件 | 大小 | md5 |
  | --- | --- | --- |
  | `sw-true.png`（`lcdtext=true`） | **4375 B** | `d478d9708df4a35d8c72e7d37ff05b05` |
  | `sw-false.png`（`lcdtext=false`） | **3025 B** | `78668459b11627a6d302e9fb50e16739` |

  两者**渲染结果不同**，且 `lcdtext=true` 的产物**更大**（次像素渲染会给每个像素的 R/G/B
  留下不同值，PNG 更难压）⇒ **软件管线同样支持 LCD 次像素抗锯齿**。
- 顺带确认：`Font.getDefault().getFamily()` 在原生镜像里返回 **`System`**，与 JVM 模式一致
  （至少 Linux 上如此）⇒ **字体族没有在原生镜像里退化**。
- DPI 方面：HiDPI 已 opt-in；`AhaDesktopApp` 的窗口尺寸**已经**按 `Screen.getOutputScaleX/Y`
  换算过 ⇒ 「DPI 缩放没读到」**也缺少证据**。
- 顺手发现的真实缺陷：每加载一个原生库之前都会先报一次
  `WARNING: java.lang.UnsatisfiedLinkError: Invalid URL for class: resource://app/17!/com/sun/glass/utils/NativeLibLoader.class`
  ——`NativeLibLoader` 会去读**自己的 `.class`**，而原生镜像里资源的 URL 形如 `resource://app/17!/…`，
  它按 `jar:file:` 解析失败；随后走另一条路径并成功 ⇒ **良性警告、不是崩溃原因**，
  但 `native-image-args.txt` 里那条 `-H:IncludeResources=com.sun.glass.utils.NativeLibLoader\.class`
  **显然没达到预期效果**。
- 本地先跑诊断代码（避免真机拿到「不可用」）：JVM 模式下字体工厂那一项打「不可用」——
  `com.sun.javafx.font` 是 `javafx.graphics` 的**非导出包**；补 `--add-opens
  javafx.graphics/com.sun.javafx.font=ALL-UNNAMED` 后打出
  `com.sun.javafx.font.freetype.FTFactory`。
- **真机数据（`0.1.1.00061`，ACANX 提供原生与 JVM 对照截图，两张 2560×1600、亮色，可逐像素对齐）**：

  | 项 | 原生实测 | 结论 |
  | --- | --- | --- |
  | Prism 管线 | `com.sun.prism.d3d.D3DPipeline`（D3D9Ex） | 正常，**排除** |
  | 字体实现 | `com.sun.javafx.font.directwrite.DWFactory` | 与系统同路径，**排除** |
  | 字体枚举 | 可用字体族 **248** | 完整，**排除**（本档案「3、」的方向 ② 被否掉） |
  | **抗锯齿方式** | **无彩色边缘 ⇒ 灰度 AA** | **← 根因** |

- **像素统计**（同一坐标系直接比对）：

  | 指标 | JVM 模式 | native | 比值 |
  | --- | --- | --- | --- |
  | **彩色边缘像素**（R/G/B 明显不等 ⇒ 次像素抗锯齿的证据） | **165,783** | **9,025** | **18.4×** |
  | 暗像素（笔画覆盖量） | 122,662 | 204,260 | 0.60 |
  | 亮度梯度总量 | 44,808,100 | 52,384,420 | 0.86 |
  | 两图不同像素 | — | 648,241（15.9%） | — |

- **缓解已落地**：`NativeRenderDefaults`（原生内默认写 `prism.lcdtext=false`，
  在 `AhaDesktopApp.main` **第一条语句**调用，因 `prism.lcdtext` 只在 `PrismFontFactory`
  静态初始化时读取一次），优先级 `-Dprism.lcdtext=…` ＞
  `-Daha.desktop.nativeTextGreyscale=false` ＞ 原生内程序默认；JVM 版不受影响；
  诊断增打「原生文本灰度默认 …；开关 …=…」；**回退三条**（单次 `-Dprism.lcdtext=true` /
  关默认值 / 删调用点或 `git revert`）。

### 机制 / 行为模型

- **诊断先行**：在拿到真机数据之前，先把「原生包到底走的哪条路」变成**可观测事实**
  （`main` 默认开 `prism.verbose`；`start(Stage)` 新增 `logRenderingDiagnostics()` 打 6 行：
  属性 / 版本 / 字体 / 字体工厂 / 字形度量 / 屏幕）。
  **诊断代码只加日志、不改渲染行为**——在拿到数据前改参数属于猜测。
- **最小探针证伪优先于采信假设**：「软件管线 → 没有 LCD → 发虚」用一次像素实验就被否掉
  （两 PNG 的 md5 不同且大小差 44%）。
- **像素统计优于肉眼比图**：**彩色边缘像素的数量**是「是否次像素抗锯齿」的客观判据
  （JVM 165,783 vs native 9,025）。
- **判据要设计成「两边不同即锁定根因」**：最初把希望押在「字体实现工厂两边不同」
  （Windows 上 JavaFX 有 `DWFactory`（DirectWrite）与 `FTFactory`（内置 FreeType）两条路径）；
  实测**两边相同**（都是 `DWFactory`）⇒ 该假设被否，根因转到**抗锯齿方式**。
  **能证伪的判据本身就是有价值的产出。**
- **观感差异的量化解释**：native 的**暗像素反而多 67%**——灰度抗锯齿把「部分覆盖」的像素
  **均匀变暗**，笔画边缘因此扩散，这正是「发虚、偏细」的成因。
- **成因指向 closed-world**：`DWFactory` 的 ClearType 取决于 `IDWriteRenderingParams`
  （由系统 / 显示器 ClearType 设置解析）；原生镜像里若这条**运行期读系统状态**的路径失败，
  DirectWrite 会退回默认渲染参数（无 ClearType、gamma 1.0）⇒ 灰度 AA。
  这与 **issue #60**（原生读不到系统与驱动信息：`Windows version 0.0 build 0`、
  D3D 驱动信息全 `null`）**很可能是同一类成因**。
- **良性警告要记下但不必追**：`NativeLibLoader` 的 URL 解析失败后**走了另一条成功路径**
  （`Loaded library … from resource`），故不是崩溃原因；但其对应的资源条目确实**没起作用**。

### 未定位 / 未复现

- **本机无法复现 Windows 的渲染路径**：WSL **没有 GPU**，只能复现 `SWPipeline` 分支；
  Windows 的 D3D 路径**只能靠真机数据**。
- **第 3 节的两条待排查方向中，方向 ② 已被真机数据否掉**（字体枚举完整：248 个字体族）；
  方向 ①（DPI 缩放）基本排除。**新的根因方向是「抗锯齿方式」**。
- **根因未修**：本档案只落地**临时缓解**（原生内默认灰度抗锯齿）。
  「原生灰度」与「JVM 次像素」**不是同一观感**：本措施消除的是「**发虚、发糊**」，
  **不是**「与 JVM 逐像素一致」。根治（补上渲染参数读取或等上游修复）**转 issue #60**；
  修复后**应删除 `NativeRenderDefaults` 与其调用点**。
- **`NativeLibLoader` 的资源条目未修**：`-H:IncludeResources=…NativeLibLoader\.class`
  显然没达到预期效果，值得后续单独收掉。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 原生包可跑 | `DISPLAY=:0 timeout 20 ./aha-desktop-native -Dprism.verbose=true -Djavafx.verbose=true` | 正常启动并打印完整管线日志 |
| 原生库加载 | 同上 | `Loaded library /libprism_sw.so from resource`（另有 glass / gtk3 / javafx_font / freetype / pango） |
| 本机回退 SW 的原因 | 同上 | `Failed Graphics Hardware Qualifier check.` + `System GPU doesn't meet the es2 pipe requirement`（WSL 无 GPU） |
| HiDPI | 同上 | `Opting in for HiDPI pixel scaling` |
| **证伪「软件管线无 LCD」** | `LcdProbe.java`（`-Dprism.order=sw`） | `sw-true.png` **4375 B** / md5 `d478d9708df4a35d8c72e7d37ff05b05`；`sw-false.png` **3025 B** / md5 `78668459b11627a6d302e9fb50e16739` ⇒ **不同** |
| 默认字体族 | 原生包诊断 | `System`（与 JVM 一致） |
| 良性警告原文 | 原生包日志 | `WARNING: java.lang.UnsatisfiedLinkError: Invalid URL for class: resource://app/17!/com/sun/glass/utils/NativeLibLoader.class` |
| JVM 侧字体工厂 | `--add-opens javafx.graphics/com.sun.javafx.font=ALL-UNNAMED` 后实测 | `渲染诊断：字体实现工厂=com.sun.javafx.font.freetype.FTFactory` |
| — | 不 `--add-opens` 时 | `渲染诊断：字体实现工厂=不可用（InaccessibleObjectException）` |
| **真机：管线** | `0.1.1.00061` 诊断日志 | `com.sun.prism.d3d.D3DPipeline`（D3D9Ex）✅ |
| **真机：字体实现** | 同上 | `com.sun.javafx.font.directwrite.DWFactory` ✅（与系统同路径） |
| **真机：字体枚举** | 同上 | 可用字体族 **248** ✅ |
| **真机：抗锯齿方式** | 对照截图逐像素 | **无彩色边缘 ⇒ 灰度 AA** ← 根因 |
| **像素统计** | 两张 2560×1600 截图比对 | 彩色边缘 **165,783 : 9,025 = 18.4×**；暗像素 122,662 : 204,260 = 0.60；梯度总量 44,808,100 : 52,384,420 = 0.86；不同像素 648,241（15.9%） |
| 缓解的优先级 | 实现 + 文档 | `-Dprism.lcdtext` ＞ `-Daha.desktop.nativeTextGreyscale=false` ＞ 原生内默认 |
| 元数据登记 | `reachability-metadata.json` | 439 → **440 条**（字体工厂反射）；`NativeImageMetadataTest` 12 → **13 条** |
| 配套材料 | 仓库 | `NativeRenderDiagnosticsGuide.md`；`Script/Python/CollectRenderDiagnostics.py`（已用含两次启动的假日志实测）；`bin/AhaDesktop.{bat,sh}` 补 `--add-opens` |
| **仍未闭环** | — | 根治（DirectWrite 渲染参数读取 / 上游修复）⇒ **issue #60** |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「DPI 缩放没读到」（ACANX 猜测①） | HiDPI 已 opt-in；窗口尺寸已按 `outputScale` 换算（「2.1」「2.3」） |
| 「编译配置导致管线回退 SW、SW 没有 LCD」（猜测②） | 像素实验：SW 管线**照样**有 LCD 次像素（两 PNG 的 md5/大小不同）（「2.2」） |
| 「本机回退 SW ⇒ Windows 也回退 SW」 | 本机回退的原因是 **WSL 无 GPU**（日志原文），**不能据此推断 Windows**（「2.1」） |
| 「字体族在原生镜像里退化了」 | `Font.getDefault().getFamily()` 返回 `System`，与 JVM 一致（「2.2」） |
| 「字体枚举不完整」（本档案第 3 节的待排查方向②） | 真机诊断：可用字体族 **248**，完整（「6.1」） |
| 「字体实现走了另一条路（`FTFactory`）」（最初的判据假设） | 真机实测两边**都是 `DWFactory`** ⇒ 该假设被否，根因转为**抗锯齿方式**（「6.1」） |
| 「Prism 管线不对」 | 真机为 `D3DPipeline`（D3D9Ex），正常（「6.1」） |
| 「`NativeLibLoader` 的警告是崩溃原因」 | 警告后**走了另一条路径并成功**（`Loaded library … from resource`）⇒ 良性（「2.4」） |

## 未决问题

**Q1 根因（原生镜像读不到 DirectWrite 渲染参数）何时修？**

现状：**未修**，本轮只落地**临时缓解**（原生内默认写 `prism.lcdtext=false`，走灰度 AA）。
根治方向：补上渲染参数的读取（首选）或等上游修复；与 **issue #60**
（原生读不到系统与驱动信息）**同源**。
阻塞点：closed-world 下运行期读系统状态的能力缺失，需要具体定位读取路径。
> **口径**：本措施消除的是「发虚、发糊」，**不是**「与 JVM 逐像素一致」；
> 根治后**应删除 `NativeRenderDefaults` 与其调用点**。

**Q2 「原生灰度」与「JVM 次像素」的观感差异能接受吗？**

现状：这是**产品取舍**（临时缓解换来的是「不发虚」，代价是与 JVM 观感不同）。
阻塞点：需要 ACANX 在真机上确认缓解后的观感可接受（`N-05` / issue #44 的验收）。

**Q3 `NativeLibLoader` 的资源条目为什么没生效？**

现状：**未修**——`-H:IncludeResources=com.sun.glass.utils.NativeLibLoader\.class`
显然没达到预期（仍报 `Invalid URL for class: resource://app/17!/…`）。
阻塞点：属性本身是良性的，优先级低，但**这条配置是「写了不起作用」的典型**，
值得单独收掉以免误导后来者。

**Q4 Windows 的渲染路径今后怎么持续观测？**

现状：本机（WSL 无 GPU）**无法复现 Windows 的 D3D 路径**，
只能靠真机跑诊断（默认开 `prism.verbose` + `logRenderingDiagnostics()` + 一键收集脚本）。
阻塞点：**每次都需要真机配合**；若能在 CI 里跑「原生包启动 + 抓日志」可部分缓解
（本项目的 `ui-smoke` 已证明 Xvfb 方案可行，但那是 JVM 端）。
