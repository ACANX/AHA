# 桌面端设计

**文档版本**：v1.5.0
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

| 项 | 状态 |
|---|---|
| `aha-desktop` 模块与 JPMS 声明 | ✅ 占位（`AhaDesktopApp`） |
| JavaFX 依赖 | ⛔ 0.1 不引入（`javafx.version = 25` 已在父 POM 声明） |
| 构建产物 | ✅ 空 JAR，不参与覆盖率门禁 |

0.2 落地时需补充：

| 待补内容 | 依据 |
|---|---|
| **线程模型小节**：单一桥接点 + 高频 `ContentEvent` 的 `runLater` 节流、取消与关窗联动、虚拟线程禁直触 FX 线程 | `D-07` 剩余项 |
| 目标平台清单与 §3、`Build.yml` matrix 三者对齐 | `D-06` 剩余项 |
| 打包小节（`jpackage` 分平台 + macOS 只打包不测） | `D-08` |
| 工具依赖：`aha-desktop` 声明 `aha-tool`（内置工具） | `D-09` 已决策 |
| TestFX 测试环境要求 | `D-04` |

界面细节以 [GUIDesign.md](GUIDesign.md) 为准（技术选型已于 2026-10-08 定稿）。

---

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

**两处已知缺口（资源类，已登记 `TODO.md` `D-10`）**：`log4j2.xml` 与 `version.properties`
都在 `aha-cli` 模块里，因此**不在桌面端包内**——分别是「日志配置」与「版本号来源」，
0.2 需要把这两个资源下移到公共位置，或由桌面端自带等价实现。
（`AhaDefault.yaml` / `ModelDefault.yml` 在 `aha-core`、工具的 `META-INF/services` 在 `aha-tool`，
这两项随包分发，已在包内实测确认。）
