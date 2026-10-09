# TS-202610-CliNativeModuleSetup：建立 CLI 原生镜像模块（`aha-cli-native` + `CliNative.yml`）

> 类型：功能落地（含一处潜伏缺陷的同批修复）
> 关联：`TODO.md` `N-18`（本批落地）、`N-19`（真机验证）、`G-09`（推送并提 PR）；
> `CliNativeDesign.md`、`DesktopNativeDesign.md` §5.4

## 1. 背景

桌面端已有 `aha-desktop-native` + `DesktopNative.yml`：把 `aha-desktop` 的 JVM 产物编成
三平台原生二进制，每次合并 `dev` 自动出包（试验性 / 可选管线）。
现在要**照着同一套做法**给 CLI 建一份：把 `aha-cli` 的产物编成原生二进制，
并新建 `CliNative.yml` 工作流，合并 `dev` 后自动出包。

预期风险不在 POM（可以逐项照抄），而在两处：

1. **反射元数据的来源完全不同**：桌面端是一长串 JavaFX `Class.forName`；
   CLI 的依赖是 picocli + JLine + Jackson + sqlite + log4j，谁自带元数据、谁不带，得逐个查；
2. **「照着抄」容易把桌面端的错误一起抄过来**。

## 2. 排障过程与修复链

### 2.1 先查每个运行时依赖是否自带 native-image 元数据

| 依赖 | 自带？ | 处置 |
|---|---|---|
| picocli 4.7.7 | ❌ | 用 `picocli-codegen` 注解处理器**生成**反射元数据（见 2.2） |
| JLine 4.4.6 | ✅（reflection / jni / resource / native-image.properties） | 直接用；但 `org.jline.utils.Signals` 未被覆盖（见 2.3） |
| Jackson 3 | ❌ | 与桌面端同：手写注册 13 个配置记录 + `TaskRequest` / `TaskResult` / `ToolCall` |
| log4j-core / sqlite-jdbc | ✅ | 不处理 |

### 2.2 picocli：用注解处理器而不是手写清单

picocli 的反射面跟着注解走（子命令实例化、`@Option` 字段注入、类型转换器）。
手写清单会「打地鼠」——新增一个子命令就漏一条，且症状在运行期。
因此改在 `aha-cli/pom.xml` 配置 `maven-compiler-plugin` 的 `annotationProcessorPaths`
（`info.picocli:picocli-codegen:${picocli.version}`），编译期生成
`META-INF/native-image/picocli-generated/reflect-config.json`。

**实测**：JDK 25 + JPMS 下处理器工作正常，生成 **30** 个类型
（`AhaCli` + 9 个子命令 + 各嵌套 `*Sub` + `picocli.CommandLine$AutoHelpMixin`），
以及空的 `proxy-config.json` / `resource-config.json`。生成物随 `aha-cli.jar` 进 classpath。

守卫：`aha-cli/src/test/.../NativeImageMetadataTest` 直接断言生成结果含
`AhaCli` 与 `ConfigCommand$SetSub`——处理器被删 / 失效时 Build 阶段就红。

### 2.3 JLine：自带元数据，但漏了 `Signals`

`javap` 反编译 `org.jline.utils.Signals` 发现它用
`Class.forName("sun.misc.Signal")` / `Class.forName("sun.misc.SignalHandler")` +
`getMethod("handle", …)` / `getField("SIG_DFL" / "SIG_IGN")` 处理 Ctrl+C，
而 JLine 自带的元数据**只注册了终端 provider**，没有这两个类型。
因此在 `aha-cli` 的 `reachability-metadata.json` 里补上这两个类型
（以及 `ExecTerminalProvider` 反射构造的 `java.lang.ProcessBuilder$RedirectPipeImpl`）。

### 2.4 同批发现并修复桌面端的潜伏缺陷：资源正则漏 `yaml` / `yml` / `svg`

建立 CLI 的资源清单时，逐项对源码里的 `getResourceAsStream` 路径核对：

| 资源 | 读取方 |
|---|---|
| `/AhaDefault.yaml` | `ConfigLoader.readDefaultConfig()`（缺失时显式抛 `CONFIG_NOT_FOUND`） |
| `/ModelDefault.yml` | `ProviderPresets` |
| `com/acanx/module/aha/common/version.properties` | `AppVersion` |

回查桌面端 `native-image-args*.txt` 的资源正则，发现**只列了 `properties|txt|json|…`，
没有 `yaml` / `yml` / `svg`** —— 意味着桌面端原生镜像启动时读不到内置配置，
直接报 `CONFIG_NOT_FOUND`（不是构建失败，是运行期崩）。
已在两份桌面参数文件（基线 + jdk27）同批补上，并同步到 `DesktopNativeDesign.md` §5.4。

> 这是本次「建第二个目标」最有价值的产出：**复用一次，暴露了第一个目标漏掉的项**。

### 2.5 模块与工作流落地

照 `aha-desktop-native` 逐项复制并改名：

- 模块 `aha-cli-native`（`packaging=pom`，依赖 `aha-cli` runtime，无 JavaFX 依赖）；
- 参数文件 `native-image-args.txt` / `-jdk27.txt`：桌面端选项减去 JavaFX 初始化，
  GC **刻意不指定**（CLI 短命进程用默认 serial 更合适，桌面端才用 G1）；
- assembly `dist-native.xml`；
- 根 POM 新增 `cli-native` profile（`<modules>` + `native.skip=false`）；
- 工作流 `CliNative.yml`：与 `DesktopNative.yml` 同构，作业名加 `cli-` 前缀
  （避免与桌面端同名），产物 `AHA-Cli-Native-…`，tag `V<版本>-aha-cli-native`。

**踩到的两个小坑**：

1. XML 注释里禁止出现 `--`：`aha-cli-native/pom.xml` 的注释写了
   `` `native-image --version` ``，Maven 直接报 `String '--' not allowed in comment`；
2. 产物自证的不变式要换：桌面端查「OpenJFX 三件套」，CLI 应查
   「picocli / jline / sqlite / log4j / snakeyaml 齐全 + 没有误入的 JavaFX + 没有 0 KB 空壳」。

## 3. 最终验证结果

| 验证 | 结果 |
|---|---|
| `aha-cli` 编译（含 picocli 注解处理器） | ✅ 生成 30 个类型的 `reflect-config.json` |
| `NativeImageMetadataTest`（4 条断言） | ✅ 通过 |
| 管线联调（`-Pcli-native … -Dnative.skip=true`） | ✅ 依赖拷贝 18 个 jar（无 JavaFX）、README + 元数据落 workdir、zip 组装成功 |
| 元数据 JSON schema 校验 | ✅ 19 条，通过官方 schema |
| `CliNative.yml` YAML 解析 + `bin/CheckScripts.py` | ✅ 通过（45 个脚本） |
| 真编原生镜像（本机） | ⏳ **未做**——本机无 GraalVM、按 `AGENTS.md` 不在本地跑分钟级任务；由 CI 的 `CliNative` 承担 |
| 真机运行（下载产物能跑） | ⏳ 由 `N-19` 跟踪 |

> 与桌面端同样的诚实说明：**构建成功不是验收标准**。元数据是推导 + 生成的结果，
> 真机跑过才算数。

## 4. 关键教训

1. **换目标要重查元数据来源，不要照抄清单**：桌面端手写，CLI 能用注解处理器生成；
   「谁自带、谁不带」逐个依赖查一遍，比抄一份长清单可靠得多。
2. **终端库也要查反射**：JLine 自带元数据很全，但仍漏了 `sun.misc.Signal`——
   「自带元数据」不等于「没有缺口」。
3. **资源清单要对着源码的 `getResourceAsStream` 逐项核**：凭扩展名直觉写正则，
   就会漏掉内置配置。本次正是复用第二目标时才反查出桌面端的 `yaml` / `yml` 缺口。
4. **复用是最好的审计**：第二个目标天然会走一遍第一个目标的清单，
   差异处往往就是第一个目标的遗漏。
5. **XML 注释禁止 `--`**：写命令行选项时（`--version` 这类）要绕开。

## 5. 涉及文件清单

**新增**

- `aha-cli-native/pom.xml`
- `aha-cli-native/src/native/native-image-args.txt`、`native-image-args-jdk27.txt`、`README.txt`
- `aha-cli-native/src/assembly/dist-native.xml`
- `aha-cli/src/main/resources/META-INF/native-image/com.acanx.module.aha/aha-cli/reachability-metadata.json`
- `aha-cli/src/test/java/com/acanx/module/aha/cli/NativeImageMetadataTest.java`
- `.github/workflows/CliNative.yml`
- `Docs/Design/CliNativeDesign.md`、本文件

**修改**

- `pom.xml`（`cli-native` profile + `native.skip` 注释）
- `aha-cli/pom.xml`（picocli 注解处理器）
- `aha-desktop-native/src/native/native-image-args.txt`、`native-image-args-jdk27.txt`（补 `yaml|yml|svg`）
- `CHANGELOG.md`、`AGENTS.md`、`Docs/Design/ArchitectureOverview.md`、
  `Docs/Design/DesktopNativeDesign.md`、`Docs/AHA/AHA-Design-V1.md`、
  `Docs/DevSpec/BuildSpec.md`、`Docs/Guide/BuildGuide.md`、`Docs/TODO.md`、`Docs/PLAN.md`
- `.agents/skills/java-app-graalvm-native-image-compile/{SKILL.md,references/isolation-and-ci.md}`
