# AHA 桌面端原生镜像设计（试验性）

> **文档版本**：v1.0.0
> **状态**：草案（试验性能力，随实验迭代）
> **生效日期**：2026-10-08
> **最后更新**：2026-10-08
> **负责人**：@ACANX
> **适用版本**：AHA 0.1.x / 0.2 开发期
> **定位**：`aha-desktop-native` 模块与 `DesktopNative.yml` 工作流的设计、风险与迭代指南

## 0. 本文定位

这是一份**试验性能力**的设计文档：目标不是「把发行方式换成原生镜像」，而是
**让验证这件事变快** —— 下载一个二进制、双击、就能试，不必先配 JDK、找 JavaFX、跑脚本。

三条边界先写清楚：

1. **正式交付仍是 JVM 模式**（`aha-desktop-<版本>-<平台>.zip` + `bin/AhaDesktop.*`）。
   原生镜像是**旁路**，不替代它。
2. **失败必须被隔离**：原生镜像编不出来，不允许挡住任何既有流程（见第 2 节）。
3. **参数会反复改**：这是一份「起点」，不是终稿；迭代方式见第 6 节。

## 1. 目标与非目标

### 1.1 目标

| # | 目标 | 判据 |
|---|---|---|
| G1 | 把 `aha-desktop` 的 JVM 产物编成三平台原生可执行文件 | `DesktopNative.yml` 的 `native-build` 三条腿各自产出二进制 |
| G2 | 每次合并到 `dev` 自动出一版，可在发布页直接下载 | 触发即发布 `native-v<版本>` 预发行版 |
| G3 | 版本号可追溯到具体 PR | 版本形如 `0.1.1.00021`（PR 段补零到 5 位） |
| G4 | 实验失败不影响既有流程 | 见第 2 节的四条隔离 |
| G5 | 为 JDK 27 / Leyden / 原始类型 / GC 的对比实验留出位置 | JDK 轴 + 每 JDK 一份参数文件（见第 5 节） |
| G6 | 为明年适配 JDK 29 铺路 | 第 5.4 节的观察项与第 7 节的后续路线 |

### 1.2 非目标

- 不做安装包 / 签名 / 公证（那是 `jpackage` 与代码签名的事，见 `TODO.md` `D-08`）；
- 不追求产物体积最小（先能跑、能对比，再谈瘦身）；
- 不改任何业务代码来迎合原生镜像（**代码该不该为 AOT 让路，是另一场讨论**：
  目前只调参数，不动源码。真到了必须加反射配置或改写初始化顺序的地步，
  会先记进 `TODO.md` 再动手）。

## 2. 隔离的四个面（这是本设计的核心）

原生镜像构建比 JVM 构建脆得多。因此**每一个面都单独设了闸**：

| 面 | 做法 | 证据 |
|---|---|---|
| **反应堆** | `aha-desktop-native` 只在父 POM 的 `desktop-native` profile 里，默认不在 `<modules>` | `./mvnw validate` 的输出里没有该模块；带 `-Pdesktop-native` 才出现 |
| **检查** | 新工作流 `DesktopNative.yml`，作业名 `native-version` / `native-build` / `native-publish`，与 Build / Gate / Compat 全不同名 | 分支保护的必需检查列表不含它们 |
| **Tag 与发版** | tag 前缀 `native-v`，**不匹配** `Release.yml` 的 `V*` / `v*`；发布为**预发行版** | `native-v0.1.1.00021` 首字符是 `n`，不命中 `v*` |
| **失败传播** | `fail-fast: false`；实验腿 `continue-on-error: true`；发布作业 `if: always()` 且允许部分平台成功 | 某条腿红了，其余腿与发布照常 |

**为什么非要隔离**：如果原生镜像挂在常规反应堆上，一次实验性失败就会挡住
`./mvnw clean verify`、Gate、Compat 与正式发版 —— 那等于用「验证便利」换「开发阻塞」，
与引入它的目的正好相反。

## 3. 构建管线

```
aha-desktop（jar 模式产物）
        │  runtime 依赖全量拷贝（含本平台 OpenJFX 三件套）
        ▼
target/native/lib/*.jar（实测 18 个：5 个本项目模块 + 3 个 OpenJFX + 10 个第三方）
        │  native-image -cp <全部 jar> -H:Name=aha-desktop-native
        ▼
target/native/aha-desktop-native[.exe]
        │  打包（二进制 + README + 两份参数文件 + LICENSE/CHANGELOG）
        ▼
aha-desktop-native-<版本>-<jdk>.zip  →  工作流按平台改名 →  native-v<版本> 预发行版
```

### 3.1 为什么走 classpath 而不是 module-path

`C-01` 规定「JPMS 优先启用但非强制」。原生镜像正是「必要时让路」的场景：

- 原生镜像在 classpath 模式下不启用 JPMS 检查，`requires` / `exports` 不再是约束，
  `ServiceLoader` 走 `META-INF/services` 即可（本项目本来就同时提供这份文件）；
- module-path 模式还要额外保证「三个 JavaFX 模块名不与无分类器空壳重名」，
  而空壳恰恰是分类器依赖的已知副作用 —— 少一个变量，少一类失败。

代价：失去了编译期模块边界检查。但这是**试验产物**，而模块边界已由 JVM 模式的
构建与测试把关（`Build.yml` 走 module-path）。

### 3.2 空壳 jar 的坑（实测）

带分类器的 OpenJFX 工件与无分类器工件**共用同一份 POM**，而那份 POM 声明的是
无分类器的依赖 —— 于是 `javafx-base-25.jar`、`javafx-graphics-25.jar`
（各 302 / 306 字节、无类、无 `module-info`）会被传递进 classpath。

本项目在 `aha-desktop` 里已经排除了它们（见其 POM 注释）。原生模块里**必须同样排除**，
而且要注意：只在 `javafx-controls` 上排除是不够的，`javafx-graphics` 也要排
（实测：只排前者时 `lib/` 里仍有 20 个 jar，排完两者才是 18 个）。

### 3.3 工作目录必须先清（实测）

`maven-dependency-plugin:copy-dependencies` **不会**删除已经不在依赖集里的旧文件。
第一次排除空壳后，`lib/` 里仍留着那两个旧文件 —— 依赖树已经干净、目录却不干净，
两边对不上账。因此本模块在 `initialize` 阶段绑定了一次 `clean`（只清 `lib/`，
`excludeDefaultDirectories=true` 保证不会顺手把整个 `target/` 删掉）。

## 4. 工作流

### 4.1 触发

| 事件 | 说明 |
|---|---|
| `push` → `dev` | **每次合并都出包**：这是「开发线与验证线并行」的关键 —— 合完就能下载来试 |
| `workflow_dispatch` | 手工补跑；`pr` 输入用于订阅正确的版本号，`version` 输入用于重打某一版 |

刻意**不加** `paths` 过滤：只有「合并到 dev 必然产出一版」才谈得上可预期；
文档改动顺带编一版是浪费，但换来的是「要验证时一定拿得到包」。

### 4.2 作业

| 作业 | 作用 | 关键点 |
|---|---|---|
| `native-version` | 算版本号（`a.b.c.PPPPP`） | 基线读根 POM（唯一来源）；PR 号优先取输入，其次按提交 SHA 反查 API；取不到记 `00000` 并告警（不猜） |
| `native-build` | 矩阵四条腿（linux-x64 / windows-x64 / macos-arm64 用 JDK 25；windows-x64 另加 JDK 27 实验腿） | 每腿自证四项：产物存在、体积 ≥ 5 MB、**二进制魔法数**（PE/ELF/Mach-O）、classpath 恰好 3 个 OpenJFX jar |
| `native-publish` | 汇总制品 → 发布 `native-v<版本>` 预发行版 | 单写者（三条腿并发写同一个 release 会互相打架）；`if: always()` 让部分平台成功也照发 |

### 4.3 版本号规则（`a.b.c.PPPPP`）

```
基线（根 pom.xml 的 <version>）  + '.'  +  PR 号补零到 5 位
0.1.1                                21  →  0.1.1.00021
```

- **不复制字面量**：基线与 PR 号都在运行时推导（`pom.xml` 与 GitHub API）；
- **不用 `help:evaluate`**：Maven 4 下带 `-q` 时输出为空（本项目踩过，见 `TODO.md`）；
- **Python 解析 POM 要命名空间无关**：`findtext('version')` 永远取不到
  （标签是 `{http://maven.apache.org/POM/4.0.0}version`），而它**不报错**，
  只会让版本号变成空的 —— 本地实测踩到过一次。正确写法见工作流注释。
- 已知代价：`AppVersion` 读的是 `version.properties`（构建期由 POM 版本过滤而来），
  因此**二进制内的版本号是 `0.1.1.00021`**（工作流用 `versions` 语义的
  `-Daha.native.version` 覆盖的是产物名与包名；若需要二进制内也带 PR 段，
  见第 7 节的「后续」）。

## 5. JDK 轴与对比实验（Leyden / 原始类型 / GC）

### 5.1 为什么要这条轴

同一份代码、同一批依赖，只换编译用 JDK，就能观察 JDK 演进对**启动速度与内存占用**的影响。
这正是明年适配 JDK 29 需要提前攒的数据。

| 关注点 | 在原生镜像语境下的含义 |
|---|---|
| **Project Leyden**（AOT 类装载与链接、AOT 方法剖析） | native-image 本身已是「全量 AOT」；Leyden 的 AOT 缓存主要在 **JVM 模式**发力。因此对比要包含「JVM + AOT 缓存」这条腿（见 5.3） |
| **原始类型**（Valhalla，预览） | 价值在于去掉装箱与指针追逐 —— 但它需要**源码**使用值类型，光换 JDK 没有效果。当前只验证「预览开关与 native-image 是否兼容」并记录结论 |
| **GC 策略** | native-image 侧默认 serial；`epsilon` 没有运行时回收，只适合短命进程；`G1` 在堆大时更稳但静态体积更大。取舍要按「一次会话可能很久」这个事实来定 |

### 5.2 两个分支怎么落地

| 分支 | 触发方式 | 参数文件 | 产物名 |
|---|---|---|---|
| JDK 25（基线） | 默认 | `src/native/native-image-args.txt` | `aha-desktop-native-<版本>-jdk25-<平台>.zip` |
| JDK 27（实验） | `-Pdesktop-native,native-jdk27`；工作流里**只开 Windows** | `src/native/native-image-args-jdk27.txt` | `aha-desktop-native-<版本>-jdk27-<平台>.zip` |

**范围先只开 Windows**：各平台的 AOT 与原始类型支持进度并不一致，
而 Windows 是当前的主力验证环境；试验成功后再评估 Linux / macOS。

**实验腿的性质**：`continue-on-error: true` —— 它是数据点，不是交付物。
它红了不影响其余三条腿，也不影响发布。

### 5.3 对比实验表（每次实验在这里留一行）

形态：**A** = 原生镜像（JDK25 编）、**B** = 原生镜像（JDK27 编）、
**C** = JVM 模式 + Leyden AOT 缓存（jar 包，`-XX:AOTCache` 系列开关；这条腿在
`aha-desktop` 的 jar 包上单独测，不在本模块里做）。

| 日期 | 形态 | JDK | GC | 冷启动到窗口可见 | 空闲驻留内存 | 二进制/包体积 | 备注 |
|---|---|---|---|---|---|---|---|
| 2026-10-08 | A | 25 | serial（默认） | 待测 | 待测 | 待测 | 基线，待首次真机测量 |
| 2026-10-08 | B | 27 | serial（默认） | 待测 | 待测 | 待测 | 实验腿，待首次测量 |

> 测量口径（写死在这里，避免每次各测各的）：
> **冷启动** = 进程启动到主窗口可见（可用 `DesktopShell` 的日志时间戳之差，
> 或外部计时工具）；**空闲驻留内存** = 窗口出现后静置 60 秒的工作集 / RSS；
> 每个数字取 5 次的中位数，并记下机器与是否插电。

### 5.4 给 JDK 29 铺路（观察项）

- 原生镜像对**预览特性**的支持窗口（原始类型、结构化并发等）：哪些能编、哪些要开关；
- AOT 相关开关在 native-image 与 JVM 两条路径上的**命名与语义差异**；
- GC 选型对 GUI 长驻进程的实际影响（停顿 vs 内存）；
- 各平台支持的**时间差**：Windows 先行的策略是否仍然成立。

## 6. 已知风险与迭代指南

### 6.1 风险清单

| # | 风险 | 现状 | 应对 |
|---|---|---|---|
| R1 | JavaFX + native-image 需要一长串「运行期初始化」清单，首次跑很可能在某个类上失败 | 参数文件里已放了一批已知点（来自 Gluon Substrate 的同款处理方式），但**未验证到位** | 按失败信息把类名加进 `--initialize-at-run-time`；只改参数文件 |
| R2 | 反射 / 资源缺失导致运行期才炸（构建成功 ≠ 能跑） | `-H:IncludeResources` 已覆盖常见资源类型 | 真机双击验证；必要时上 `-H:ReflectionConfigurationFiles` |
| R3 | JavaFX 平台原生库未打进镜像 | 已把 `.so/.dylib/.dll` 纳入资源清单 | 首次真机运行若是 `UnsatisfiedLinkError`，据此调整 |
| R4 | GraalVM 是否有对应 JDK 版本（尤其 JDK 27） | 工作流用 `graalvm-community` 的对应版本号 | 该腿失败即是答案（隔离，不影响其余）；可先降到 JDK 26 或等发布 |
| R5 | 产物体积大（把 JDK 与全部依赖编进去了） | 预期几十 MB | 后续再谈瘦身（`-H:-IncludeAllTimeZones` 等），先保证能跑 |
| R6 | 未签名 → macOS Gatekeeper 拦截、Windows SmartScreen 提示 | 已知 | `README.txt` 里写明放行方式；签名是另一件事 |
| R7 | 三个平台的关键差异被「只在 Windows 试」掩盖 | 已知 | 每次改参数后,至少在本机（Windows）+ 一条 Linux 腿上看结论 |

### 6.2 迭代时改哪里（只改一处）

```
aha-desktop-native/src/native/native-image-args.txt        ← JDK 25 基线的全部开关
aha-desktop-native/src/native/native-image-args-jdk27.txt  ← JDK 27 实验分支的开关
```

POM 与工作流都**不需要动**：参数文件是单一来源，且会原样打进 zip（事后可查）。

新增一类平台的差异（比如某平台要额外的 `--initialize-at-run-time`）时，
也可以按同样思路再加一份参数文件 + 一个 profile，而不是在 POM 里堆条件。

### 6.3 本机怎么试（Windows 为例）

```powershell
# 1) 装 GraalVM（含 native-image），或指定已有的：
#    -Dnative.image.executable=D:\path\to\graalvm\bin\native-image.cmd
# 2) 真编（约几分钟到十几分钟）
.\mvnw -Pdesktop-native -pl aha-desktop-native,aha-desktop -am package -DskipTests
# 3) 只验证管线、不真编（十几秒）
.\mvnw -Pdesktop-native -pl aha-desktop-native,aha-desktop -am package -DskipTests -Dnative.skip=true
# 4) JDK 27 实验分支
.\mvnw -Pdesktop-native,native-jdk27 -pl aha-desktop-native,aha-desktop -am package -DskipTests
```

**注意 `-pl aha-desktop-native,aha-desktop -am` 这个写法**：profile 加入的模块
**不会**被 `-am` 带上游（实测 Maven 3.9.11 与 Maven 4 行为一致：只写 `-pl aha-desktop-native -am`
时反应堆里只有它自己），所以要显式把 `aha-desktop` 也列出来，让 `-am` 把五个上游一起拉进来。
症状很好认：**「在中央仓库找不到 aha-desktop」** —— 因为反应堆匹配不上，它就去外网找了个不存在的坐标。

## 6.4 技能边做边改（本项目约定的工作方式）

本项目同时产出一份可复用的技能：`.agents/skills/java-app-graalvm-native-image-compile/`。
它的用法被刻意定成**活文档**，而不是做完之后的总结：

- **开工前建骨架**：目标、非目标、四类清单的种子（允许大量「推断待验证」条目）；
- **每次失败立刻补一条**：格式固定「症状 → 根因 → 修法」，并记来源（哪次运行、哪条报错）；
- **顺序不能反**：先写技能，再改代码或参数 —— 这样技能始终是「已知什么、下一步试什么」的清单；
- **状态只许向上**：「推断 → 已踩坑 → 已验证 → 已定稿」；
  工具链升级或有推翻性证据时，已定稿条目要退回「已验证」重新确认；
- **成熟判据**（达到才算标杆参考，见技能的「用法」一节）：
  目标平台真机可跑通、未验证条目清零、版本可追溯、**且在别的项目复用过一次**。

当前的技能版本是 `0.1.0`（试验中）：隔离、构建系统层的坑与版本号规则已实测，
**真正的 native-image 编译与运行期行为尚未验证** —— 这一点写在技能的「验证状态」表里，
并由 `TODO.md` 的 `N-09` 跟踪到成熟为止。

## 7. 后续路线

| # | 事项 | 触发条件 |
|---|---|---|
| N-1 | 首次真机验证三条腿的下载产物（双击、能开窗、能对话） | 工作流首次跑通后立刻做 |
| N-2 | 把 5.3 的对比表填上真实数字（A / B / C 三种形态） | 需要 GraalVM 与 JDK 27 环境 |
| N-3 | JDK 27 分支扩展到 Linux / macOS | Windows 上试验成功之后 |
| N-4 | 让二进制**内部**的版本号也带 PR 段（当前只有包名与 tag 有） | 与 N-1 一起评估（做法：把 `aha-common` 的版本资源也按 `aha.native.version` 过滤，或让 `AppVersion` 支持系统属性覆盖） |
| N-5 | 体积瘦身（时区/语料裁剪、`-H:-IncludeAllLocales` 等） | 对比表有了基线之后 |
| N-6 | 评估 GluonFX（`gluonfx-maven-plugin`）作为备选路径 | 若 R1/R3 反复失败、手工清单维护成本过高 |
| N-7 | JDK 29 适配预演 | 明年 JDK 29 进入 EA 之后 |

## 8. 相关文档

- [DesktopDesign.md](DesktopDesign.md)：桌面端（JVM 模式）的形态、线程模型与骨架设计
- [GUIDesign.md](GUIDesign.md)：界面设计（原生镜像与 JVM 版共用同一套界面）
- [ArchitectureOverview.md](ArchitectureOverview.md)：模块与依赖方向
- [BuildSpec.md](../DevSpec/BuildSpec.md)：构建与检查分层（原生镜像属于「慢检查」之外的新工作流）
- [ReleaseProcess.md](../DevSpec/ReleaseProcess.md)：正式发版流程（本工作流刻意不参与）
