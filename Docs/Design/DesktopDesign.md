# 桌面端设计

**文档版本**：v1.9.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-06
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.1.0 | 2026-10-07 | 第 2 节指向 GUIDesign.md；标明 WebView / FXML 的技术表述待评审 | @ACANX |
| v1.2.0 | 2026-10-08 | §2 记录界面路线与目标平台决策（`D-07` 进程内直调优先、`D-06` Win+Linux 为承诺 / macOS 只打包不测）；§3 补 JPMS 让路约束；§4 落地清单改为表格并标注依据 | @ACANX |
| v1.3.0 | 2026-10-08 | 新增第 5 节「平台依赖与打包」：JavaFX 分类器清单、per-OS profile 机制、三个实测坑（`unix` 禁用、`os.arch=amd64`、空壳 jar 的自动模块）、与 D-01/jpackage 的关系、CI 自证要求 | @ACANX |
| v1.4.0 | 2026-10-08 | 新增 §5.6「发布产物（便携包）」：描述符产出布局、文件名由构建期解析结果决定、用户下载与运行方式、jpackage 自包含包待评估 | @ACANX |
| v1.5.0 | 2026-10-08 | §5.6 补包内依赖构成（18 个 jar）、不含项，以及两处已知缺口（`log4j2.xml` 与 `version.properties` 在 `aha-cli`，不在桌面端包内 → `D-10`） | @ACANX |
| v1.6.0 | 2026-10-08 | 重写 §4 实现现状（依赖/打包、桥接代码、测试默认运行与冒烟默认跳过）；新增 §6 线程模型（唯一桥接、合并、关窗联动、虚拟线程约束）；§5.6 记资源缺口已解决并更正早期「缺 `log4j2.xml`」的误判 | @ACANX |
| v1.7.0 | 2026-10-08 | §4 记启动改走 `AhaBootstrap`、补配置引导测试；新增 §7「启动引导（与 CLI 共用）」（load/boot 分工、失败降级、窗口可见自证、CLI 端到端实测表）；§4「未做」移除已完成项 | @ACANX |
| v1.8.0 | 2026-10-08 | §4 记 `DesktopShell` 与测试构成（19 例默认运行）；新增 §8「界面骨架」（折叠三条路径与常驻窄条的理由、`FoldState` 为何抽成纯逻辑、可测边界、尚未接入 Agent 的诚实提示） | @ACANX |
| v1.9.0 | 2026-10-08 | 新增 §9「标志与窗口图标」：Logo.svg → PNG 的生成器与三条硬要求（写死像素尺寸 / 视口比窗口矮 / 按 alpha 自检包围盒），并记下「只验尺寸格式会放过裁切图」的教训 | @ACANX |

---

## 1. 版本规划

0.1 不实现桌面端（`aha-desktop` 仅占位）。0.2 实现 OpenJFX WebView + 本地 HTTP 服务器 + FXML Controller。

## 2. 技术

- OpenJFX 25（父 POM：`javafx.version = 25`）
- 主类：`com.acanx.module.aha.desktop.AhaDesktopApp`
- 打包：`jpackage`（MSI / DEB / DMG）
- **界面路线（`D-07` 决策，2026-10-08）：进程内直调 + JavaFX 原生控件**（`GUIDesign.md` §8.1）；
  `WebView + 本地 HTTP`（§8.2）**仅在 8.1 无法满足需求时**才启用，不作默认
- **目标平台（`D-06` 决策，2026-10-08）**：支持承诺为 **Windows + Linux**；
  macOS **只要求能打出包**（`jpackage`，CI 分平台构建），**不要求跑测试**

> **界面形态与技术选型见 [GUIDesign.md](GUIDesign.md)**（草案）。
>
> ⚠️ 需要澄清：此前文档中「WebView + 本地 HTTP 服务器 + FXML Controller」这一组合
> **从未真正决策**——它只出现在 `AhaDesktopApp` 的一句 Javadoc 占位注释里，
> 且 WebView（HTML/CSS/JS）与 FXML（JavaFX 原生控件）本就是两条不同的 UI 路线，
> 不能同时成立。GUIDesign.md §8 给出两个方案的取舍与建议（**建议进程内直调 + JavaFX 原生控件**），
> 待评审后回填本节。

## 3. 约束

- `jpackage` 不能交叉编译，CI 必须分平台构建（含 macOS 打包 job，但不跑 macOS 测试）
- **JPMS 为默认而非门槛**：与 OpenJFX（TestFX / WebView 反射）冲突时为 OpenJFX 让路（`C-01` 决策）
- 桌面端 WebView 不支持 native-image，native-image 流水线仅覆盖 CLI + Core + Tools

## 4. 实现现状

- **依赖与打包**（0.1.1 起）：per-OS profile 解析 JavaFX 分类器（§5）；`aha-desktop` 声明
  `aha-tool` 运行时依赖（`D-09`）；assembly 描述符产出便携包（§5.6）。
- **代码**（0.1.1 起，0.2 的第一步）：
  - `AhaDesktopApp`：`Application` 子类，装配启动引导后开窗；
  - `DesktopShell`：按 `GUIDesign.md` 第 2 节搭出菜单栏 + 三栏 + 底部状态栏（见 §8）；
  - `desktop/fx`：线程桥接契约（§6）——单元测试**无需图形环境**即可运行；
  - 启动时走 `aha-core` 的启动引导 `AhaBootstrap`（`D-10` + `D-11` 的成果）：读项目
    `./Aha.yaml` → 装配日志 → 装密钥库回退源，与 CLI **共用同一份实现**（见 §7）。
- **测试**：`FxBridgeTest`、`FxThreadContractTest`、`AhaDesktopAppConfigTest`、
  `FoldStateTest`、`ShellLayoutTest`、`PaletteTest` **默认运行**（共 19 例，另 1 例冒烟默认跳过）；
  **窗口冒烟测试默认跳过**（需图形环境 + GTK，CI 与无显示的 WSL 都没有），
  用 `-Daha.ui.tests=true` 显式开启。
- **未做**：对话流与工具卡片的真实数据、授权弹窗、`/` `@` 补全、主题与设置、
  Agent 接线（`TODO.md` `D-12` 的遗留项）；TestFX 选型与 CI 启用（`TODO.md` `D-04`）。

## 5. 平台依赖与打包：JavaFX 分类器 + per-OS profile（2026-10-08 实测）

JavaFX 的原生库按平台拆成不同的**分类器工件**，构建时只能解析当前平台那一份；而 `jpackage`
不能交叉编译（第 3 节），所以每个平台的包必须由该平台的 runner 产出。机制与实测结论如下。

### 5.1 分类器清单（Maven Central 实查，版本 `25`）

| 分类器 | 平台 |
|---|---|
| `win` | Windows x86_64 |
| `linux` / `linux-aarch64` | Linux x86_64 / ARM64 |
| `mac` / `mac-aarch64` | macOS Intel / Apple Silicon |

**没有 `win-aarch64`** —— Windows ARM 拿不到 JavaFX 原生库，不在支持范围（第 2 节的目标平台决策）。

### 5.2 机制

- 父 POM 加 5 个 profile（`javafx-windows` / `javafx-mac` / `javafx-mac-aarch64` /
  `javafx-linux` / `javafx-linux-aarch64`），各自把属性 `javafx.platform` 设为对应分类器；
- 父 POM 的 `dependencyManagement` 为 `javafx-base` / `javafx-graphics` / `javafx-controls`
  声明 `version = ${javafx.version}` + `classifier = ${javafx.platform}`；
- `aha-desktop` 只写 `groupId` / `artifactId` / `<classifier>${javafx.platform}</classifier>`，
  **不出现平台字面量**；版本与分类器都来自父 POM。

> 注意：`dependencyManagement` 的匹配**包含 classifier**——子模块若漏写 classifier，
> 匹配不到托管项而直接报「version is missing」（实测踩过）。

### 5.3 三个实测出来的坑

1. **激活条件不能用 `<family>unix</family>`**：它在本机 Linux 上命中，而 `unix` 在部分
   Maven/JDK 组合下对 macOS 同样成立，用它会让两个平台撞车。Linux 改判 `<name>Linux</name>`，
   macOS 用 `<family>mac</family>`。
2. **不要写 `<arch>x86_64</arch>`**：Maven 的 `os.arch` 在本机是 **`amd64`**（实测 `x86_64`
   条件不命中、`amd64` 命中）。故 x86 侧一律用取反 `<arch>!aarch64</arch>`（取反写法实测有效，
   Maven 4.0.0-rc-7 与 3.9.11 行为一致），与 JDK 的命名差异解耦。
3. **必须显式声明三个工件并排掉空壳传递依赖**：带分类器的工件在依赖树里还会拉进同名 GA 的
   **0 KB 空壳 jar**（`javafx-base-25.jar` 只有 `META-INF/MANIFEST.MF`，无类、无 `module-info`）。
   它在 classpath 上无害，但在 module path 上会变成**自动模块**：
   `javafx-base:25 -- module javafx.baseEmpty [auto]`，而 `jpackage` / `jlink` **不接受自动模块**。
   最终做法：三个工件都显式带分类器，并用 `<exclusions>` 排掉空壳——依赖树只剩本平台三个真 jar，
   模块名为 `javafx.controls` / `javafx.graphics` / `javafx.base`，均无 `[auto]`。

### 5.4 与打包的关系

- profile 一开，**classpath 上只有本平台的原生库** → 桌面端自己的 assembly 描述符 / `jpackage`
  输入天然只含一个平台，`D-01`（发行包混入多平台 native JAR）**不需要额外的 `<classifier>` 过滤**；
- CLI 的 `dist` **不受影响**：`aha-cli` 与 `aha-desktop` 互不依赖（`Constitution.md` 第 4 条），
  实测 `dist/lib` 的 18 个 jar 中 javafx 相关为 **0**；
- 未被任何 profile 覆盖的平台（如 Windows ARM）会以默认值 `unsupported` 解析失败——失败信息
  直接点名 `javafx-*-25-unsupported.jar`，不会静默拿到错平台的原生库；
  应急覆盖：`-Djavafx.platform=<win|linux|mac|linux-aarch64|mac-aarch64>`。

### 5.5 CI 自证

分类器只在**当前 runner** 上生效，本机无法替 Windows / macOS 验证。故各平台 runner 的打包 job
第一步应打印生效值（`help:evaluate -Dexpression=javafx.platform`；`dependency:list
-DincludeGroupIds=org.openjfx`），把「解析到了哪个平台的原生库」写进日志——与 `BuildSpec.md`
第 8.1 节「门禁必须自证」同一原则。

### 5.6 发布产物（便携包）

`aha-desktop` 的 assembly 描述符 `src/assembly/dist-desktop.xml` 产出便携包：

- 布局：`bin/`（`AhaDesktop.sh` / `AhaDesktop.bat`）+ `lib/`（本项目模块 + 全部运行时依赖 +
  本平台三个 OpenJFX jar）+ `README.md` / `CHANGELOG.md` / `LICENSE`；
- 文件名由构建期**真实解析结果**决定：`finalName = aha-desktop-${project.version}-${javafx.platform}`
  ——版本来自父 POM、平台来自 profile，没有手写常量；
- 用户在 release 页面按「系统 + 架构」下载对应包，解包后只需 JDK 25 即可运行
  `bin/AhaDesktop.sh`（Linux / macOS）或 `bin/AhaDesktop.bat`（Windows）；
- **自包含安装包（jpackage，内置运行时）仍待评估**——见 `TODO.md` `D-08`。

> 0.1 阶段这里的产物是「机制已就绪」：`AhaDesktopApp.main` 仍是占位实现，
> 正式版（0.2）落地后同一条流水线直接产出可用包。

包内依赖（实测 Linux 包 `lib/` 共 18 个 jar）：5 个项目模块（含 `aha-tool`）、
3 个本平台 OpenJFX 分类器工件、10 个第三方 jar（Jackson / Log4j2 / SLF4J / snakeyaml-engine /
sqlite-jdbc）。**不含** JDK 运行时与测试期依赖。

**资源缺口已解决（0.1.1）**：`version.properties` 与 `AppVersion` 已下移到 `aha-common`，
日志装配 `LoggingSetup` 已下移到 `aha-core`（`log4j-core` 在 core 为 `compile` scope）。
桌面端因此既能拿到构建期注入的版本号，也能配置日志，且**不必依赖 `aha-cli`**——依赖矩阵仍然成立。
（`AhaDefault.yaml` / `ModelDefault.yml` 在 `aha-core`、工具的 `META-INF/services` 在 `aha-tool`，
这两项随包分发，已实测确认。）

> 更正一则早期的误判：先前记为「桌面端缺 `log4j2.xml`」。实际上**全仓原本就没有**
> 该文件——装配一直是程序化的（理由见 `LoggingDesign.md`：JPMS 下 `getResources` 不搜模块路径）。
> 真正缺的是**类**（`LoggingSetup` 在 `aha-cli`），现已下移。

## 6. 线程模型（0.2 实装，2026-10-08）

界面只在 **JavaFX Application Thread**（下称 UI 线程）上改动；Agent 推理、LLM 流式读取、工具执行
全部跑在**虚拟线程**上。两者之间只有一条通道，而这条通道在代码里是一个**可测的契约**。

### 6.1 唯一桥接：`FxDispatcher`

| 元素 | 职责 |
|---|---|
| `FxDispatcher` | 接口：`onUiThread()` / `dispatch(Runnable)` |
| `PlatformFxDispatcher` | 唯一允许调用 `Platform.runLater` 的实现 |
| `FxBridge<T>` | 合流器：只保留最新值，至多一次在途 UI 任务 |

**规则（强制）**：主源码中除 `PlatformFxDispatcher` 外，**不得**出现
`Platform.runLater(` / `Platform.startup(` / `Platform.isFxApplicationThread(`。
该规则由 `FxThreadContractTest` 扫描主源码钉住——不靠约定，而是让违规变成一次失败。
把 `FxDispatcher` 抽成接口的收益正在这里：单测注入同步执行的替身，就能在**没有图形环境**的机器
（CI、无显示的 WSL）上验证线程模型，无需启动 JavaFX 运行时。

### 6.2 高频流式输出的合并（coalescing）

流式响应每个 token 都会触发一次回调；若每次都投递 UI 任务，队列会被塞满、界面反而更卡。
`FxBridge` 的策略是：`submit(v)` 只写 `pending` 并置「已排程」标记，**只有第一个到达者**真正投递一次
任务；渲染时取走最新值并清标记。因此每帧至多一次界面更新，且**最后一个值一定会被渲染**。

被合并掉的是**中间态**而非数据：累积正文由渲染侧自己的缓冲负责（与 CLI 侧同一思路）。
渲染过程中到达的新值会被**重新排程**而不丢失
（`FxBridgeTest#valueArrivingDuringRenderIsNotLost` 钉住这一点）。

### 6.3 取消与关窗联动

- 关窗（`Stage.setOnCloseRequest`）时调用 `FxBridge.close()`：之后的后台更新**一律丢弃**，
  连「已排程但尚未渲染」的值也一并丢弃——正在关窗，渲染已无意义。
- 后台任务持有 `CancellationToken`：关窗时取消在途会话，避免任务在工具箱停止后继续投递。
  JavaFX 工具箱停止后再调 `runLater` 会抛异常，这条规则就是它的护栏。

### 6.4 虚拟线程不得直接触碰界面

- 编译期：`desktop/fx` 不对外导出——模块外拿不到 `PlatformFxDispatcher`；主源码扫描见 6.1。
- 运行期：渲染动作只在 UI 线程执行；**同样禁止在 UI 线程做阻塞工作**（会冻结界面），
  需要阻塞时用虚拟线程 + 桥接。

### 6.5 现状与后续

已实装：窗口骨架 + 桥接契约 + 一条状态探针虚拟线程（`AhaDesktopApp.startStatusProbe`）。
后续（0.2 内）：会话管理、流式正文渲染、工具卡片、授权弹窗、输入区——全部按本节契约接入。

## 7. 启动引导（0.1.1 起，与 CLI 共用）

桌面端与 CLI 的启动前三步完全一致：**读主配置 → 装配日志 → 装密钥库回退源**。
这三步抽在 `aha-core` 的 `AhaBootstrap`，因为两个入口**不得互相依赖**
（`Constitution.md` 第 4 条），而各写一遍的代价不只是重复，更是口径分叉——
桌面端曾长期写 `LoggingSetup.apply(null)`，导致 `Aha.Logging.*` 在桌面端**完全失效**
（`TODO.md` `D-11`）。

| 方法 | 副作用 | 用途 |
|---|---|---|
| `AhaBootstrap.load(Path)` | 无 | 纯解析；测试与「先看配置再决定」的场景 |
| `AhaBootstrap.boot()` | **有**（替换进程级日志配置、注册静态回退源） | 入口启动：按项目 `./Aha.yaml` 引导 |
| `AhaBootstrap.boot(Path)` | 同上 | 同上，但配置路径由调用方给定（测试用） |

**读什么**：项目级 `./Aha.yaml`；不存在则用 classpath 的 `AhaDefault.yaml`
（权威口径见 `AHA-Design-V1.md` 的配置文件一节）。读不到、YAML 破损、密钥库不可用
都**不阻断启动**，只记 warning，由调用方呈现（CLI 打 stderr，桌面端记日志）。

**桌面端的可见自证**：窗口里有一行配置摘要（`configSummary()`，id `#aha.config`）：

```
配置：/path/to/Aha.yaml · 日志级别：DEBUG
```

改项目 `Aha.yaml` 的 `Aha.Logging.Level` 后这一行必须跟着变——这就是 `D-11` 的真机验收方式。

**CLI 侧的端到端实测（同一份代码路径）**：

| `Aha.yaml` 的 `Logging` | 结果 |
|---|---|
| `Level: WARN` + 绝对路径 | 日志生成在**指定路径**，其中 DEBUG 行 **0** 条 |
| `Level: DEBUG` + 同一路径 | 同一路径，DEBUG 行 **1** 条（级别确实生效） |
| `File: './Log/custom.log'` | 相对路径按**当前工作目录**解析并生成 |
| 无 `Aha.yaml` | 走内置默认，落到 `~/.aha/Log/AHA.log` |

## 8. 界面骨架（0.1.1 起）

`DesktopShell` 搭出 `GUIDesign.md` 第 2 节的形态：菜单栏 + 三栏（左 220px / 中栏弹性 /
右 280px）+ 底部 24px 状态栏。它只负责**搭骨架与接线**，不碰 Agent。

### 8.1 折叠：三条路径，一个状态机

左右两栏都能折叠，且三条路径等价——菜单项（视图）、快捷键（`Ctrl+B` / `Ctrl+J`）、
栏边**常驻窄条按钮**。三者最终都调用同一个 `FoldState`。

**为什么保留常驻窄条**：若折叠就把整列藏没，鼠标用户在折叠后将**没有任何办法展开**
（只剩键盘快捷键）；留一条 28px 窄条与其中的按钮，展开入口永远可见，
按钮提示写明「展开左栏」而不只是给一个含义不明的箭头。

**为什么把状态抽成 `FoldState`**：桌面端界面在 CI 与无显示的 WSL 上都跑不起来
（实测本机 WSL 无 `libgtk-3`）。折叠逻辑若写在视图里，「折叠后展不回来」这类事故
只能在用户机器上被发现。`FoldState` 不引用任何 JavaFX 类型，于是折叠→展开的往返
（`FoldStateTest#collapsedThenExpandedReturnsToInitialState`）、按钮字符与提示文案、
右栏「无本轮数据则默认收起」都能在**无图形环境**下被测试钉住。

这是本项目对「界面不可本地运行」这一约束的固定应对：**把可判定的部分从视图里抽出来，
剩下的薄壳交给冒烟测试与真机走查。**

### 8.2 可测与不可测的边界

| 层 | 内容 | 本机可测 |
|---|---|---|
| 纯逻辑 | `FoldState`（折叠状态）、`ShellLayout`（尺寸与可见性规则）、`Palette`（语义色值） | ✅ 默认运行 |
| 构建 | `DesktopShell`（节点组装、菜单、事件、Enter 发送） | ⛔ 需图形环境：冒烟测试，默认跳过 |

尺寸与色值都有测试对齐 `GUIDesign.md`（`ShellLayoutTest` / `PaletteTest`），
改设计不改测试会立刻失败——目的是避免「文档一套、界面一套」悄悄发生。

### 8.3 尚未接入 Agent

输入区支持 Enter 发送 / Shift+Enter 换行，但发送**只把消息落到本地消息流**，
并追加一条明确说明（`DesktopShell.NOT_WIRED_HINT`），**不假装已经发给模型**。
接线 Agent 时替换的正是这一处。

## 9. 标志与窗口图标（0.1.1 起）

矢量来源是 `aha-core/src/main/resources/Logo.svg`（README 用的同一份）。
JavaFX 的 `Image` **只接受位图**，不认 SVG，所以把矢量栅格化一次、把 PNG 作为资源入库：
`aha-desktop/src/main/resources/com/acanx/module/aha/desktop/view/logo.png`，
由 `bin/GenLogoPng.py` 生成（与 `bin/GenPixelLogo.py` 同一模式：生成器入库、产物入库）。
同一张图两处使用：**窗口 / 任务栏图标**（`stage.getIcons()`）与**空会话状态的展示**（`LogoImage`）。

### 9.1 生成器的三条硬要求（都是踩坑换来的）

1. **把 SVG 根元素的宽高写成目标像素数**——`Logo.svg` 自带 `width="600" height="600"`，
   直接开页面时在 256 的视口里只会渲染出自然尺寸的一角；
2. **窗口比目标高 240px**——无头 Edge 的**视口比窗口矮**（本机实测矮约 85px），
   按正方形开窗会把标志下部裁掉；
3. **渲染后自己解码 PNG、按 alpha 裁掉透明边，并自检**：
   结果必须近似正方形，且不小于目标尺寸的 90%。这三条合起来才让「图标只剩一角 / 只剩上半」
   这类问题**当场失败**，而不是悄悄产出半张图。

> 第一次实现时只验了「是 PNG、是正方形、够大」——裁切产物全部通过，
> 直到在 Windows 上真机启动才被看出来。**尺寸/格式正确 ≠ 图像内容正确**，
> 所以第 3 条的自检必须落在内容上（包围盒），不能只看头部字段。

`LogoImageTest` 在无图形环境下校验资源存在、是 PNG、为正方形且不小于 128px，
并把它作为「资源随模块打包」的回归防线（`stage.getIcons()` 用的是同一份资源）。

