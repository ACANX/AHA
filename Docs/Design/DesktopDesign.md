# 桌面端设计

**文档版本**：v1.13.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-09
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
| v1.10.0 | 2026-10-08 | 新增 §10「对话与供应商配置」：对话内核可测而界面薄（ChatController/ChatView/ToolSummary/DesktopToolApprover）、供应商为可编辑表单（含校验与保留未涉及字段）、以及 Windows 真机验证记录 | @ACANX |
| v1.11.0 | 2026-10-08 | §10.2 供应商配置补充：绿灯标识（启用项与其模型）、打开即选中启用项（纯函数 + 测试）、按预设新建与可编辑模型下拉 | @ACANX |
| v1.12.0 | 2026-10-09 | 新增 §5.7「本地构建便携包（JVM 模式）」：触发命令、Maven 阶段链路、assembly 的 POM 配置与描述符内容、版本号两层（包名 vs `aha.build.version`）、运行期解压消费、与 CI 的关系（dev 预发行线在独立工作流 `BuildJVMArtifacts.yml`，issue #63） | @ACANX |
| v1.13.0 | 2026-10-09 | 发行形态最终决策：§2「打包」由 `jpackage` 改为「JVM 便携包 + 原生镜像单文件」；§3 / §5 / §5.4 去掉 `jpackage`，「必须分平台构建」的依据改为 JavaFX 平台分类器；§5.6 注明 `jpackage` 已决策跳过（见 `ReleaseProcess.md` §3.3） | @ACANX / CNXNC |

---

## 1. 版本规划

0.1 不实现桌面端（`aha-desktop` 仅占位）。0.2 实现 OpenJFX WebView + 本地 HTTP 服务器 + FXML Controller。

## 2. 技术

- OpenJFX 25（父 POM：`javafx.version = 25`）
- 主类：`com.acanx.module.aha.desktop.AhaDesktopApp`
- 打包：**JVM 便携包**（`aha-desktop-<版本>-<系统>-<架构>.zip`，解压后脚本启动）与
  **原生镜像单文件**（`aha-desktop-native`，试验性）；`jpackage` 安装包**已决策跳过**（见 `ReleaseProcess.md` §3.3）
- **界面路线（`D-07` 决策，2026-10-08）：进程内直调 + JavaFX 原生控件**（`GUIDesign.md` §8.1）；
  `WebView + 本地 HTTP`（§8.2）**仅在 8.1 无法满足需求时**才启用，不作默认
- **目标平台（`D-06` 决策，2026-10-08）**：支持承诺为 **Windows + Linux**；
  macOS **只要求能打出包**（CI 分平台构建），**不要求跑测试**

> **界面形态与技术选型见 [GUIDesign.md](GUIDesign.md)**（草案）。
>
> ⚠️ 需要澄清：此前文档中「WebView + 本地 HTTP 服务器 + FXML Controller」这一组合
> **从未真正决策**——它只出现在 `AhaDesktopApp` 的一句 Javadoc 占位注释里，
> 且 WebView（HTML/CSS/JS）与 FXML（JavaFX 原生控件）本就是两条不同的 UI 路线，
> 不能同时成立。GUIDesign.md §8 给出两个方案的取舍与建议（**建议进程内直调 + JavaFX 原生控件**），
> 待评审后回填本节。

## 3. 约束

- **JavaFX 原生库按平台分类器发布**，CI 必须分平台构建（含 macOS 打包 job，但不跑 macOS 测试）
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

JavaFX 的原生库按平台拆成不同的**分类器工件**，构建时只能解析当前平台那一份，所以每个平台的包
必须由该平台的 runner 产出。机制与实测结论如下。

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
   `javafx-base:25 -- module javafx.baseEmpty [auto]`，而 `jlink` **不接受自动模块**。
   最终做法：三个工件都显式带分类器，并用 `<exclusions>` 排掉空壳——依赖树只剩本平台三个真 jar，
   模块名为 `javafx.controls` / `javafx.graphics` / `javafx.base`，均无 `[auto]`。

### 5.4 与打包的关系

- profile 一开，**classpath 上只有本平台的原生库** → 桌面端自己的 assembly 描述符输入天然只含
  一个平台，`D-01`（发行包混入多平台 native JAR）**不需要额外的 `<classifier>` 过滤**；
- CLI 的 `Dist` **不受影响**：`aha-cli` 与 `aha-desktop` 互不依赖（`Constitution.md` 第 4 条），
  实测 `Dist/lib` 的 18 个 jar 中 javafx 相关为 **0**；
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
- **自包含安装包（`jpackage`）已决策跳过**（2026-10-09）：发行形态只保留 JVM 便携包与原生镜像
  单文件两种，理由见 `ReleaseProcess.md` §3.3。

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

本地如何产出这个包（命令、assembly 配置、版本号两层、运行期消费）见 §5.7。

### 5.7 本地构建便携包（JVM 模式）

发布页面上的 `aha-desktop-<version>-<platform>.zip` 与本地构建出来的是**同一个东西**：
同一条 assembly 链路，只是触发者不同（CI runner 与开发机）。本节把本地侧的完整实现记下来，
避免「构建产物从哪来」只能靠读 POM 反推。

**触发**（在仓库根）：

```bash
./mvnw -pl aha-desktop -am package -DskipTests
# 需要程序内显示构建号时（issue #46）：
./mvnw -pl aha-desktop -am package -DskipTests -Daha.build.version=0.1.1.00061
```

`-am` 让 `aha-common` / `aha-extension-api` / `aha-core` / `aha-tool` 一并进入本次反应堆
——它们是 `aha-desktop` 的 **runtime** 依赖，缺一个包里就没有对应能力。

**`package` 阶段的三个动作**：

| 步骤 | 插件 | 产物 |
|---|---|---|
| 编译 | `compiler` | `aha-desktop/target/classes` |
| 打模块 jar | `jar:jar` | `aha-desktop-0.1.1.jar`（含 `module-info`） |
| 打便携包 | `maven-assembly-plugin` 的 `dist-desktop` execution（绑 `package`，goal `single`） | `Dist/aha-desktop-0.1.1-win.zip` |

**assembly 配置（`aha-desktop/pom.xml`）**：

- `descriptors = src/assembly/dist-desktop.xml`；
- `appendAssemblyId = false` —— 文件名不带 `-dist-desktop` 后缀；
- `attach = false` —— zip 不进入部署流程，不污染本地 / 远程仓库；
- `outputDirectory = ${maven.multiModuleProjectDirectory}/Dist` —— 落**仓库根 `Dist/`**
  （`Dist/` 在 `.gitignore` 内，同时也是 CLI 发行目录的解包位置）；
- `finalName = aha-desktop-${project.version}-${javafx.platform}` —— 文件名里的版本来自父 POM、
  平台来自 §5.2 的 per-OS profile，**没有手写常量**。

**描述符内容（`src/assembly/dist-desktop.xml`）**：

- `<includeBaseDirectory>false</includeBaseDirectory>` —— 内容直接铺在 zip 根，不套一层目录，
  与 `bin/AhaDesktop.sh` 里 `$DIR/../lib` 的布局假设一致；
- `dependencySet`：`outputDirectory=lib`、`useProjectArtifact=true`、`scope=runtime`、
  `unpack=false` —— 本项目 `aha-desktop` 工件 + 全部 runtime 依赖（含 §5.2 解析出的本平台三个
  OpenJFX 分类器 jar）平铺进 `lib/`；
- `fileSet`：父目录 `bin/` 的 `AhaDesktop.sh` / `AhaDesktop.bat` → zip 内 `bin/`（`fileMode 0755`）；
- `fileSet`：仓库根的 `README.md` / `LICENSE` / `CHANGELOG.md` → zip 根。

**版本号的两层（容易混淆）**：

- zip **文件名**用 `${project.version}`（如 `0.1.1`），**不受** `aha.build.version` 影响；
- `aha.build.version`（默认 `${project.version}`）由构建期写进 `version.properties`，只影响
  **程序内显示**（issue #46）。因此带 `-Daha.build.version=0.1.1.00061` 构建时，包名仍是
  `aha-desktop-0.1.1-win.zip`，而启动日志显示 `0.1.1.00061`。

**运行期怎么消费这个包（源码检出场景）**：`bin/AhaDesktop.bat` / `.sh` 先找
`Dist/aha-desktop-*-win.zip`，把它解压到以「zip 名 + 写入时间 + 大小」命名的目录
（`aha-desktop-0.1.1-win.<stamp>`），再以该目录的 `lib/` 作为 module path 启动。
用时间戳 / 大小做键，是为了让**重建的包落到新目录**、不被上一次的解压结果遮蔽；
旧目录刻意不删（可能正被运行中的实例占用）。

**与 CI 的关系**：`Build.yml` 每次 push / PR 只做编译与单元测试（见 [BuildSpec.md](../DevSpec/BuildSpec.md)
第 8 节）；另有一条**独立的 dev JVM 构建线**（`BuildJVMArtifacts.yml`，与 `DesktopNative.yml` /
`CliNative.yml` 同构）——`push` 到 `dev` 时由 `build-mvn-artifact` / `build-publish` 用**同一条命令**
产出便携包并发布预发行版（JDK 25 基线与 JDK 27 两轴，issue #63 / #65）。正式发版仍由
`Release.yml` 在 `V*` tag 上产出。原生镜像另走 `aha-desktop-native` 与 `DesktopNative.yml`
（见 [DesktopNativeDesign.md](DesktopNativeDesign.md)），不在本节范围内。

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

## 10. 对话与供应商配置（0.1.1 起）

### 10.1 对话内核可测、界面很薄

`ChatController` 把 `AgentService` 的事件流翻译成界面动作，**不引用任何 JavaFX 类型**：

| 元素 | 职责 |
|---|---|
| `ChatController` | 发送（每轮一条虚拟线程）、事件分发、取消、会话生命周期 |
| `ChatView` | 界面对外暴露的动作（追加消息、卡片、错误、用量、忙碌） |
| `ToolSummary` | 工具卡片两行文字（类别 + 工具名 + 目标 / 状态 + 规模），纯函数 |
| `DesktopToolApprover` | 授权语义：本次允许 / 本会话始终允许 / 拒绝（与 CLI 一致），决策逻辑纯 |

线程契约不变（第 6 节）：除 `appendAssistant`（视图内部用 `FxBridge` 合流，可从任意线程调用）
外，其余界面动作一律经 UI 线程投递器；测试注入「同步执行器 + 同步投递」，
于是**整条对话链路在没有图形环境的机器上可测**（`ChatControllerTest` 11 例）。

### 10.2 供应商配置：查看 + 修改 + 保存

左栏「供应商」打开的是**可编辑表单**，不是只让你挑一个：

- 左列表（`Model.yml` 里的全部供应商）+ 右表单（适配器 / 基础地址 / 模型 / API Key / 超时 / 重试）；
- 保存 / 设为默认 / 新增 / 删除，写回 `Model.yml`（与 CLI `aha provider` 同一份文件、同一个
  `ModelConfigStore`，两边互通）；
- API Key 默认打码，点「显示」才明文；对话框底部写明三种配置方式，并明确
  **直接写文件是明文**，推荐环境变量 `AHA_API_KEY_<ID>` 或 `aha secret set`；
- **绿灯标识**：列表里正在启用的那家用绿色加粗的 `●`（附其模型名），其余为 `○`，
  状态行再复述一遍「● 已启用：<供应商>  <模型>  密钥 <打码>」——一眼看清「现在用的是谁家的哪个模型」；
  打开时**默认选中当前启用的那家**（规则为纯函数 `ProviderForm.selectedOnOpen`，有测试）；
- **新增供应商与模型**：「新增」配合「按预设新建」（六家已知供应商的适配器 / 基础地址 / 默认模型
  一键填好）与可编辑的模型下拉（候选来自 `ModelDefault.yml` 的真实模型名，也允许自己填），
  保存即新增；模型名一律不编造，否则用户照着填会拿到 404；
- 校验与转换在 `ProviderForm`（纯逻辑，`ProviderFormTest` 8 例）：非法 ID / 空模型 /
  非 http 地址 / 非法超时都会当场报错，不会写出坏配置；保存时**保留表单未涉及的字段**
  （限流、扩展参数），不会「改一个字段丢一片」。

### 10.3 真机验证（Windows，2026-10-08）

- 界面：三栏骨架 + 折叠 + 标志 + 底部状态栏可见，输入框启动即获得焦点；
- 对话：自动化输入一句英文后，日志出现
  `DefaultLlmClient.streamChat - LLM stream -> https://api.deepseek.com/v1/chat/completions`，
  会话创建、流式推理、用量回填均正常；
- 供应商：对话框列出 6 个供应商，默认项与密钥打码状态正确（`默认供应商：DeepSeek · 密钥：sk-e…d2c9`）。

## 11. 本轮新增：卡片 / 会话列表 / 日志 / 输入 / 授权 / 主题

### 11.1 工具卡片（`GUIDesign` 第 4.2 节逐条）

卡片是会话里出现最频繁的组件，因此它的文字、截断与折叠口径全部抽到 `chat/ToolCard`
（纯函数，`ToolCardTest` 14 例）：

| 规则 | 实现 |
|---|---|
| 默认折叠 | 正文（参数 + 输出）默认隐藏；首行常驻 |
| 首行按类别着色 | `Palette.forToolKind`，与 CLI 同一套 `ToolKind` 判定 |
| 结果行「状态 · 耗时 · 规模」 | 顺序按设计稿；**不足 100ms 不显示耗时**（与 CLI 同规则） |
| 截断必须说出来 | 标题写「输出（前 200 行，共 412 行）」，不是悄悄截断 |
| 行号 | 按总行数对齐宽度，便于与正文区分 |
| 复制 / 全部 | 复制写系统剪贴板；「全部」打开 `OutputDialog` 看完整内容 |
| 失败可重试 | 整卡红边 + 正文摊开 + 首行给「重试」与「改参数后重试」 |

**「重试」刻意不在本地重放这次工具调用**：重放会绕过模型对当前上下文的判断。
卡片把失败事实与原参数交回模型（`ToolCard.retryMessage`），「改参数后重试」把原参数填进
输入框让用户改——两者都不偷偷执行命令。

耗时由 `ChatController` 用注入的 `LongSupplier` 计时，因此「调用 → 结果」的耗时可以被
确定断言（测试里让每次读时钟前进 125ms），界面只负责摆控件。

### 11.2 会话列表

- 标题是**展示层概念**：用户重命名过就用它，否则取该会话的**首条用户消息**
  （`SqliteMemoryStore.listSessions` 用一条 SQL 取全，含消息数与首条用户消息，避免 N+1）；
- `session` 表新增 `title` 列，靠既有 `ensureColumn` 迁移——`CREATE TABLE IF NOT EXISTS`
  对已存在的表不生效；
- 当前会话用「绿点 + 加粗」表示，**与列表选中态分开**：选中态会被鼠标点击改变，
  两者混用会让用户分不清「我点的是哪个」与「消息会发到哪个」；
- 切换会话 = 关掉旧会话 + 清空对话流 + 回放历史（**只读**，不重新推理）；
  工具消息回放成一行系统提示——它们是给模型的中间产物，不是对话内容；
- 导出两种格式：Markdown 给人看、JSON 给脚本。JSON 是**手写转义**的：为一个导出功能
  给桌面模块加 Jackson 的 JPMS 依赖不值得，转义规则用测试钉住更划算。

### 11.3 日志面板

`LogCapture` 往 log4j2 **根记录器**挂一个内存 appender（只收 `com.acanx.module.aha`），
`LogBuffer` 是**有上限**的环形缓冲，且**过滤在读的时候做**——把门槛从 WARN 调回 DEBUG 时
之前的行还在，不需要「复现一次才看得到」。挂不上（无 log4j2 实现）只是没有实时数据，
面板照常可打开：排错工具不该自己变成故障源。

面板头部写明当前配置级别，并说明「低于它的事件根本不会被产生」——这是事实，
不写出来就会被当成 bug。数据源由端到端测试自证（装 appender → 写日志 → 断言进缓冲）。

### 11.4 输入区

- `SlashCommands`：命令名与 CLI 同名，但**只登记真的能执行的命令**；
  斜杠命令在本端执行、不发给模型（把 `/help` 丢给模型既浪费一次调用又得到无关回复）；
- `FileMentions`：`@` 候选跳过 `.git`/`target`/`node_modules` 等噪音目录（不跳的话候选里
  全是构建产物）、遍历与结果都有上限、只给相对路径、光标越过片段就不再补全；
- `InputHistory`：空白不记、连续重复只记一条、有上限，翻历史时**不丢草稿**；
- 候选弹层用 `Popup` + `ListView` 而不是 `ContextMenu`：后者拿到焦点后会吞掉方向键与
  Enter，用户没法「打字 → 选 → 回车确认」自然过渡。弹层**从不抢焦点**，按键统一由输入框
  的过滤器处理。

### 11.5 授权弹窗

比系统默认 `Alert` 多出来的都是「不看就会点错」的：类别着色首行、权限**人话说明**、
完整参数（执行类是完整命令，`rm -rf build` 与 `rm -rf /` 必须一眼可分）、
按钮上写出被放宽的权限名、默认焦点在**拒绝**且 `Esc` 即拒绝、
列出「本会话已自动允许」的其它权限。

会话内「始终允许」是个便利但危险的状态，因此「工具 → 全部撤销本会话授权」（`Ctrl+Shift+R`）
是必备入口——CLI 端只能靠重开会话，GUI 端可以做得更好。

### 11.6 主题与设置

- `Theme` 三选一；系统偏好的**读取**在 `SystemTheme`，**解析**在 `Theme.resolve(boolean)`
  （纯函数，可测）；
- `Palette` 维护两套色表。亮色不是把暗色调亮：`#FF5F5F` 在白底上对比度不足，
  失败 / 成功 / 强调色都取了更深的版本；
- **换主题必须重刷已建节点**：颜色内联在样式串里，不会自动跟随。为此把 29 处 `setStyle`
  统一改成 `themed(node, () -> ...)`，把「怎么重新上样式」登记下来（`DesktopShell.restylers`）；
  否则会出现「新控件是亮色、旧消息还是暗色」，看起来就像没换成功。冒烟测试专门断言了
  「已建好的控件也被重刷」；
- **先定色表、再建界面**（顺序是硬约束）：颜色内联在样式串里，界面建完再换主题只能靠逐个重刷，
  那条路径一旦漏了某个控件，就会出现「日志说生效亮色、界面还是暗的」——真机上撞到过。
  正确顺序是启动时**先** `Palette.setTheme(...)` 再 `new DesktopShell(...)`：新节点天生就是对的，
  重刷只是换主题时的补充。日志里会打印生效主题与实际色值（前景 / 底色），便于自证；
- 设置存 `AHA_HOME/desktop.properties`，**刻意不用 YAML**：这是程序自己写的偏好文件，
  不该混进需要按大驼峰校验的配置体系；读坏了退回默认值；
- 设置面板改动立即生效并立即保存（没有「应用」按钮）；记忆策略与身份文件编辑**故意不放**：
  还没实现，放一个点了没反应的项比少一个功能更糟。
- **系统偏好读取三级回退（2026-10-09，issue #44）**：`getColorScheme()` → 系统背景色亮度 →
  亮色。旧实现一律回退暗色，导致原生镜像（系统偏好读取失败）永远以暗黑主题启动。
  同时订阅 `colorSchemeProperty()`，在「跟随系统」模式下实时跟随系统深浅皮肤切换；
- **弹层也要登记主题**：候选弹层（`Popup`）与会话右键菜单（`ContextMenu`）有独立的场景根，
  不继承主窗口样式；前者换主题时调 `refreshTheme()`，后者在 `setOnShowing` 里套当前配色；
- **不允许硬编码颜色**：样式里出现的颜色一律取自 `Palette`，否则切换主题会留下「没变」的暗色块
  （真机上撞到过：3 处边框与 1 处背景写死了十六进制值）。
