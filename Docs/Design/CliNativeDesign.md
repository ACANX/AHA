# AHA CLI 原生镜像设计（试验性）

> **文档版本**：v1.0.0
> **状态**：草案（试验性能力，随实验迭代）
> **生效日期**：2026-10-08
> **最后更新**：2026-10-09
> **负责人**：@ACANX
> **适用版本**：AHA 0.1.x / 0.2 开发期
> **定位**：`aha-cli-native` 模块与 `CliNative.yml` 工作流的设计、风险与迭代指南

## 0. 本文定位

这是 [`DesktopNativeDesign.md`](DesktopNativeDesign.md) 的**姊妹篇**：桌面端那套把
`aha-desktop` 编成原生 GUI，这里把 `aha-cli` 编成原生命令行程序。

**共享的方法论不在这里重复**（隔离的四个面、`a.b.c.PPPPP` 版本规则、产物自证、
构建报告作为交付物、JDK 轴对比实验的口径），请直接读桌面端那份。本文只写
**CLI 特有的部分**：输入与主类、反射元数据从哪来、与桌面端的差异、以及各自的风险。

三条边界（与桌面端完全一致）：

1. **正式交付仍是 JVM 模式**（`Dist/bin` + `Dist/lib` 的 CLI 发行目录、`bin/Aha.*`）。
   原生镜像是**旁路**，不替代它。
2. **失败必须被隔离**：CLI 原生镜像编不出来，不允许挡住任何既有流程（见第 2 节）。
3. **参数会反复改**：这是一份「起点」，不是终稿。

> **JVM 模式自己的 dev 出包线**见 `BuildJVMArtifacts.yml`（`push` → `dev` 产出
> `aha-cli-<版本>.zip` 与 `aha-desktop-<版本>-<平台>.zip` 的预发行版）——它与本工作流
> **并列**、互不牵连，共用同一套 `a.b.c.PPPPP` 版本规则。该 JVM 线还含一份 **JDK 27 编译产物**
> （包名带 `-jdk27`，字节码 release=27）：那是 JDK 27 的 **JVM 字节码**，与本工作流里用
> GraalVM JDK 27 编 **原生镜像**不是一回事（issue #65）。

## 1. 目标与非目标

### 1.1 目标

| # | 目标 | 判据 |
|---|---|---|
| G1 | 把 `aha-cli` 的 JVM 产物编成三平台原生可执行文件 | `CliNative.yml` 的三条基线腿各自产出二进制 |
| G2 | 每次合并到 `dev` 自动出一版，可在发布页直接下载 | 触发即发布 `V<版本>-aha-cli-native` 预发行版 |
| G3 | 版本号可追溯到具体 PR | 版本形如 `0.1.1.00021`（PR 段补零到 5 位），与桌面端同规则 |
| G4 | 实验失败不影响既有流程 | 与桌面端同四条隔离 |
| G5 | 与桌面端共用一套对比实验口径（Leyden / GC） | JDK 轴 + 每 JDK 一份参数文件 |

### 1.2 非目标

- 不做安装包 / 签名 / 公证；
- 不追求产物体积最小（先能跑，再谈瘦身）；
- 不改业务代码来迎合原生镜像（同桌面端：只调参数，不动源码）。

## 2. 隔离（与桌面端一致，独立成条）

CLI 原生镜像与桌面端原生镜像是**两条平行管线**，互不牵连：

| 面 | 桌面端 | CLI |
|---|---|---|
| 反应堆 | `desktop-native` profile → `aha-desktop-native` | `cli-native` profile → `aha-cli-native` |
| 工作流 | `DesktopNative.yml` | `CliNative.yml` |
| 作业名 | `native-version` / `native-image-*` / `native-publish` | `cli-native-version` / `cli-native-image-*` / `cli-native-publish` |
| Tag / 发版 | `V<版本>-aha-desktop-native` | `V<版本>-aha-cli-native` |
| 参数文件 | `aha-desktop-native/src/native/native-image-args*.txt` | `aha-cli-native/src/native/native-image-args*.txt` |
| 产物 | `AHA-Desktop-Native-…zip` | `AHA-Cli-Native-…zip` |

**作业名刻意与桌面端不同名**：GitHub 的必需检查按作业名匹配，两条可选管线若同名会互相干扰；
而且日志 / 摘要里一眼能分清是哪条管线。

**`native.skip` 仍是同一个开关**：默认值的唯一来源在根 POM（`true`），
两个 profile 各自覆盖为 `false`，CI 再显式传 `-Dnative.skip=false` 兜底。
规则与踩过的坑见 `DesktopNativeDesign.md` §2。

## 3. 构建管线

```
aha-cli（jar 模式产物）
        │  runtime 依赖全量拷贝（18 个 jar：5 个本项目模块 + picocli/JLine/sqlite/log4j/Jackson/snakeyaml/slf4j）
        ▼
aha-cli-native/target/native/lib/*.jar
        │  native-image -cp <全部 jar> -o target/native/aha-cli-native \
        │               com.acanx.module.aha.cli.AhaCli
        ▼
aha-cli-native/target/native/aha-cli-native[.exe]
        │  打包（二进制 + README + 两份参数文件 + 可达性元数据 + LICENSE/CHANGELOG）
        ▼
Dist/aha-cli-native-<版本>-<jdk>.zip  →  工作流按平台改名 →  V<版本>-aha-cli-native 预发行版
```

与桌面端的三处不同：

1. **没有 JavaFX**：classpath 里没有 `javafx-*`（产物自证会检查这一点——CLI 不该依赖 GUI 栈）；
2. **主类**是 `com.acanx.module.aha.cli.AhaCli`（与 `bin/Aha.sh` 走的是同一个入口）；
3. **GC 默认用 serial**（桌面端用 G1）：CLI 是「启动 → 干活 → 退出」的短命进程，
   serial 启动更快、体积更小；GUI 才关心长驻停顿。取舍不同，所以参数不同。

## 4. 反射 / JNI 元数据从哪来（CLI 的核心差异）

CLI 没有 JavaFX 那一长串 `Class.forName` 链，但有**三处**反射来源，各有各的处置：

| 来源 | 是否自带 native-image 元数据 | 本项目怎么办 |
|---|---|---|
| **picocli** 4.7.7 | ❌ 不带 | 编译期用 `picocli-codegen` 注解处理器生成 `META-INF/native-image/picocli-generated/reflect-config.json`（见 `aha-cli/pom.xml`），随 `aha-cli.jar` 进 classpath |
| **JLine** 4.4.6 | ✅ 自带（reflection / jni / resource / native-image.properties） | 直接用；**但它没有覆盖 `org.jline.utils.Signals`**（`Class.forName("sun.misc.Signal")`），这块由本项目的 `reachability-metadata.json` 补 |
| **AHA 自身**（Jackson 读写配置 / 会话） | Jackson 3 ❌ 不带 | `aha-cli/src/main/resources/META-INF/native-image/com.acanx.module.aha/aha-cli/reachability-metadata.json` 注册 13 个配置记录 + `TaskRequest` / `TaskResult` / `ToolCall` |
| log4j-core / sqlite-jdbc | ✅ 自带 | 不处理 |

### 4.1 为什么用注解处理器而不是手写 picocli 清单

picocli 的反射面**跟着注解走**：子命令靠 `@Command(subcommands=…)` 反射实例化、
`@Option` / `@Parameters` 字段靠反射注入、类型转换器也要靠反射找。
手写一份清单，一旦新增 / 重命名子命令或选项就漏一条，而症状是**运行期**才有的
（构建成功、一执行就崩）——正是 `DesktopNativeDesign.md` 说的「打地鼠」。

`picocli-codegen` 直接扫编译单元，生成结果与源码同步（实测生成 **30** 个类型：
主命令 + 9 个子命令 + 各子命令的嵌套 `*Sub` + `picocli.CommandLine$AutoHelpMixin`）。
守卫放在 `aha-cli/src/test/.../NativeImageMetadataTest`：**直接断言生成结果里含
`AhaCli` 与 `ConfigCommand$SetSub`**——处理器被删 / 失效时 Build 阶段就红。

> 代价：`aha-cli` 的编译多了一个注解处理器依赖（`info.picocli:picocli-codegen`，仅编译期）。
> 它不生成业务字节码，只写一份 JSON；JDK 25 + JPMS 下实测工作正常。

### 4.2 JLine 的 `Signals` 缺口（实测）

JLine 自带元数据只注册了终端 provider（`ExecTerminalProvider` / `JniTerminalProvider` /
`FfmTerminalProvider` / `CLibrary` 等），**没有**注册 `sun.misc.Signal` / `sun.misc.SignalHandler`。
而 `org.jline.utils.Signals` 会：

```java
Class.forName("sun.misc.SignalHandler");
Class.forName("sun.misc.Signal").getMethod("handle", Signal.class, SignalHandler.class);
// 以及 getField("SIG_DFL" / "SIG_IGN")
```

不注册的话，Ctrl+C 的处理会在原生镜像里失败。因此本项目的元数据补了这两个类型 +
`java.lang.ProcessBuilder$RedirectPipeImpl`（`ExecTerminalProvider` 反射构造它）。

### 4.3 资源清单：`yaml` / `yml` 的坑（同批修了桌面端）

`ConfigLoader` 用 `getResourceAsStream("/AhaDefault.yaml")` 读内置配置，
`ProviderPresets` 读 `/ModelDefault.yml`。**漏掉的症状是启动即报 `CONFIG_NOT_FOUND`**
（`ConfigLoader.readDefaultConfig` 显式抛），不是构建失败。
桌面端早期那份参数文件的正则里**没有 `yaml|yml`** —— 建立 CLI 这份时同批发现，
已一并修正两份桌面参数文件。这是一个「不查资源清单就必踩」的坑。

## 5. 工作流

与 `DesktopNative.yml` 同构，差异只在命名与自证内容。要点：

| 作业 | 关键点 |
|---|---|
| `cli-native-version` | 同桌面端：基线读根 POM，PR 号按输入或提交 SHA 反查，取不到记 `00000` 并告警 |
| `cli-native-image` | 矩阵三条基线腿（linux-x64 / windows-x64 / macos-arm64，JDK 25）+ 两条 JDK 27 实验腿；产物自证七项 |
| `cli-native-publish` | 单写者汇总制品，发 `V<版本>-aha-cli-native` **预发行版**；无产物时不发版也不失败 |

**产物自证的七项**（与桌面端同形，判据换成 CLI 的不变式）：

1. 可执行文件存在（先看约定位置，再整个 `target/` 兜一遍）；
2. 体积 ≥ 5 MB（小于它基本是「编译没完成却留下壳」）；
3. 平台魔法数（PE / ELF / Mach-O）；
4. classpath 输入：**关键依赖齐全**（picocli / jline / sqlite-jdbc / log4j-core /
   snakeyaml-engine）+ **没有 0 KB 空壳** + **没有误入的 JavaFX**（CLI 不该依赖 GUI 栈）；
5. 构建报告存在；
6. 独立报告包真的生成；
7. 元数据完整：picocli 生成的命令反射 + `sun.misc.Signal` + `AhaConfig` 三类齐全。

## 6. 已知风险与迭代指南

| # | 风险 | 现状 | 应对 |
|---|---|---|---|
| R1 | 终端能力探测依赖原生后端（FFM / JNI），首次跑可能在某个 provider 上失败 | JLine 自带元数据，但**未真机验证** | 按失败信息补 `--initialize-at-run-time` 或元数据；只改参数文件 / 元数据 |
| R2 | 反射 / 资源缺失导致运行期才炸（构建成功 ≠ 能跑） | picocli 走注解处理器、JLine 与 Jackson 已补；但**未真机验证** | 真机逐命令走查（`N-19`）；`NativeImageMetadataTest` 在 Build 阶段先守一层 |
| R3 | 资源清单漏项（yaml / yml 就是实例） | 已修正，含桌面端 | 新增资源读取点时必须同步更新参数文件 |
| R4 | GraalVM 是否有对应 JDK 版本（尤其 JDK 27） | 与桌面端相同 | 该腿失败即是答案（隔离，不影响其余） |
| R5 | 交互式终端（JLine）在原生镜像下行为与 JVM 版不一致 | 未验证 | 先用非交互子命令（`--version` / `run`）验收，再验收交互式 |
| R6 | 未签名 → macOS Gatekeeper 拦截 | 已知 | `README.txt` 写明放行方式 |

**诚实说明**：元数据（picocli 生成结果、JLine Signals 补充、Jackson 记录）都是
**按字节码与依赖审计推导**的，不是本机 tracing agent 采集的。手工清单只能做到
「已知缺口已闭」，不等于「证明完整」——真机验证仍是 `N-19`。

## 7. 迭代时改哪里（只改一处）

```
aha-cli-native/src/native/native-image-args.txt        ← JDK 25 基线的全部开关
aha-cli-native/src/native/native-image-args-jdk27.txt  ← JDK 27 实验分支的开关
aha-cli/src/main/resources/.../reachability-metadata.json  ← 手写元数据（Jackson / JLine Signals）
aha-cli/pom.xml                                         ← picocli 注解处理器（唯一生成来源）
```

POM 与工作流**不需要动**：参数文件是单一来源，且会原样打进 zip（事后可查）。

## 7.1 本机怎么试（Windows 为例）

```powershell
# 只验证依赖拷贝与打包管线，不真的编原生镜像
.\mvnw -Pcli-native -pl aha-cli-native,aha-cli -am package -DskipTests -Dnative.skip=true

# 真编（需要 GraalVM 的 native-image；或 -Dnative.image.executable= 指定）
.\mvnw -Pcli-native -pl aha-cli-native,aha-cli -am package -DskipTests

# JDK 27 实验分支
.\mvnw -Pcli-native,native-jdk27 -pl aha-cli-native,aha-cli -am package -DskipTests
```

注意 `-pl aha-cli-native,aha-cli -am`：profile 加入的模块**不会**被 `-am` 带上游，
必须显式把 `aha-cli` 也列出来，让 `-am` 把五个上游一起拉进来。

## 8. 相关文档

- [DesktopNativeDesign.md](DesktopNativeDesign.md)：**共享方法论**（隔离四面、版本规则、
  产物自证、构建报告、JDK 轴对比实验）——本文不重复
- [CLIDesign.md](CLIDesign.md)：CLI（JVM 模式）的设计与备选方案
- 技能 [`graalvm-reachability-metadata`](../../.agents/skills/graalvm-reachability-metadata/SKILL.md)：
  **元数据登记**的方法论（三路发现法、来源优先级、四层守卫、常见框架经验库）——
  本模块的元数据决策（picocli 用注解处理器、JLine Signals 补齐、资源逐项核）都遵循它
- [ArchitectureOverview.md](ArchitectureOverview.md)：模块与依赖方向
- [BuildSpec.md](../DevSpec/BuildSpec.md)：构建与检查分层
- [ReleaseProcess.md](../DevSpec/ReleaseProcess.md)：正式发版流程（本工作流刻意不参与）
- [TODO.md](../TODO.md)：`N-19`（真机验证 CLI 原生镜像）等
