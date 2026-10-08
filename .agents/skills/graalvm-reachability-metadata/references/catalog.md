# 经验库：常见框架 / 依赖的已知元数据点

**用法**：动手前先查这张表。
- 「自带元数据」= 依赖 jar 里有 `META-INF/native-image/`，**不用手写**；
- 「不带」= 需要框架生成机制或手写；
- 状态列遵循 [skill-lifecycle](../../java-app-graalvm-native-image-compile/references/skill-lifecycle.md)
  的四档：**推断 / 已踩坑 / 已验证 / 已定稿**。带「来源」的条目优先相信。

> **警告**：本表是**线索**不是**权威**。即使标「已验证」，也要用自己的依赖版本核对
> （方法签名会跨版本变）。抄结论，不抄原文。

## A. 已在本项目（AHA）实测的依赖

| 依赖 | 自带元数据？ | 已知点 | 处理方式 | 状态 / 来源 |
|---|---|---|---|---|
| **sqlite-jdbc** 3.53.x | ✅ 自带（`native-image.properties`） | 静态初始化会解压 / 加载本地库 | 加 `--initialize-at-run-time=org.sqlite`；其余不用管 | 已验证（AHA） |
| **log4j-core / log4j-api** 2.26.x | ✅ 自带（`reflect-config.json` + `resource-config.json`） | 配置装配在运行期按文件系统解析 | 加 `--initialize-at-run-time=org.apache.logging.log4j.core.config` / `.core.util` | 已验证（AHA） |
| **Jackson 3**（databind / core / annotations） | ❌ 不带 | 所有被 `treeToValue` / `readValue` / `writeValueAsString` / `TypeReference` 触碰的 POJO / 记录 | 手写注册：`allDeclaredConstructors` + `allDeclaredMethods` + `allDeclaredFields` | 已验证（AHA：漏了会在读配置 / 会话时崩） |
| **jackson-dataformat-yaml** 3.x | ❌ 不带 | 同 Jackson | 同上 | 已验证（AHA） |
| **snakeyaml-engine** 3.x | ❌ 不带 | 若只解析到 `JsonNode` / `Map`，无 POJO 反射 | 视用法；有 POJO 绑定就要注册 | 已踩坑（AHA） |
| **picocli** 4.7.7 | ❌ 不带 | 子命令实例化、`@Option` / `@Parameters` 字段注入、类型转换器 | **用 `picocli-codegen` 注解处理器生成**（比手写可靠） | 已验证（AHA：生成 30 个类型） |
| **JLine** 4.4.6 | ✅ 自带（reflection / jni / resource / foreign） | 终端 provider（`Exec` / `Jni` / `Ffm` / `Dumb`）、`CLibrary` / `Kernel32` 结构体 | 直接用；**但要补它漏掉的 `org.jline.utils.Signals`**（见下） | 已验证（AHA） |
| **JLine** · `org.jline.utils.Signals` | ❌（上游元数据未覆盖） | `Class.forName("sun.misc.Signal")` / `"sun.misc.SignalHandler"` + `getMethod("handle", …)` + `getField("SIG_DFL"/"SIG_IGN")` | 手写注册这两个类型（含 public 字段 / 方法）；另注册 `java.lang.ProcessBuilder$RedirectPipeImpl` | 已验证（AHA：`javap` 反编译发现） |
| **JavaFX** 25 | ❌ 不带完整元数据 | 见下面「JavaFX 专表」；启动反射是**链式**的 | 按启动链路一次补齐 + `jniAccessible` + 资源（着色器 / 原生库） | 已验证到"编译通过"；**真机运行待验证**（AHA `N-16`） |
| **OpenJFX 分类器工件** | — | 带分类器与不带分类器的 jar 共用同一份 POM，会传递进 0 KB 空壳 | 在 POM 里排除无分类器传递依赖 | 已验证（AHA） |
| **ServiceLoader 的 provider 实现类**（业务自定义） | —（看 GraalVM 内建支持） | `ServiceLoader.load(X)` 返回的实现类会被反射实例化 | **通常无需手写**：GraalVM 对 `META-INF/services` 有内建支持，会自动注册 provider；前提是服务文件进镜像（资源清单含 `META-INF/services/.*`） | 已踩坑（AHA：agent 采到 6 个业务 provider；真机待确认） |
| **可选依赖的探测式反射** | — | `Class.forName("某可选库的类")`，类可能不在 classpath | **一般无需处理**：调用方已捕获异常；不要因为 agent 采到就登记 | 已踩坑（AHA：`com.fasterxml.jackson.databind.ObjectMapper` 出现在 agent 结果里，而项目用 Jackson 3） |

### A.1 JavaFX 25 专表（启动链路）

按「谁会 `Class.forName` / `getDeclaredConstructor` / `getMethod`」组织。**只注册一个没用**。

| 反射点 | 目标 | 登记形态 | 状态 |
|---|---|---|---|
| `Toolkit.getToolkit()` | `com.sun.javafx.tk.quantum.QuantumToolkit` | 无参构造器 | 已踩坑（AHA #35 直接报错点） |
| `PlatformFactory.getPlatformFactory()` | `com.sun.glass.ui.{win,gtk,mac}.{Win,Gtk,Mac}PlatformFactory` | 构造器（三平台都写） | 已踩坑 |
| `GraphicsPipeline.createPipeline()` | `com.sun.prism.{d3d,es2,sw,j2d}.{D3D,ES2,SW,J2D}Pipeline` | 静态 `getInstance()` | 已踩坑 |
| `D3DResourceFactory.createStockShader` | `com.sun.prism.shader.<name>_Loader`（**全部候选**，实测 212 个） | `loadShader(ShaderFactory, String, InputStream)` **三参** | 已踩坑（参考配置写成两参，错的） |
| `PrRenderer` / `PPSRenderer` / `PSWRenderer` | `PPS*Renderer` / `PSW*Renderer` / `D3DShaderSource` / `ES2ShaderSource` | `createRenderer(FilterContext)` / 构造器 | 已踩坑 |
| `PrismFontFactory` | `DWFactory` / `CTFactory` / `FTFactory` | `getFactory()` | 已踩坑 |
| `PulseLogger` / `MethodUtil` / `StyleManager` / `ImageStorage` | `PrintLogger` / `JFRPulseLogger` / `Trampoline` / `control.skin.Utils` / `J2DImageLoaderFactory` | `createInstance()` / `invoke(...)` / `getResource(...)` / 构造器 | 已踩坑 |
| Glass / 字体原生回调 | `Application`、`View`、`Screen`、`*Application`、`NativeLibLoader`、DirectWrite/CoreText/FreeType 结构体 | **`jniAccessible` + 全量方法 / 字段** | 已踩坑 |
| 条件不触发（**不要**盲目加） | `Control.loadClass` 的 `-fx-skin`、`PPSRenderer` 的 `<effect>Peer`、`java.lang.ProcessBuilder$RedirectPipeImpl`（非 JLine 场景）、Swing/Web/Media 可选模块 | 用到了才加 | 已踩坑（AHA：用 `createDefaultSkin()` 静态可达，无需注册） |

JavaFX 资源：着色器 `.obj`（D3D）/ `.frag` `.vert`（ES2）、平台原生库 `.so`/`.dylib`/`.dll`、`.bss`。

### A.2 agent 交叉验证结论（2026-10-08 首次实跑）

用 tracing agent 跑一遍 CLI 的完整命令序列（`--version` / `--help` / `init` /
`config` / `provider` / `tool` / `secret` / `extension` / 交互式 `chat`），得到
**400 个反射类型 + 55 条资源**。把它们与手写清单对比：

| 观察 | 结论 |
|---|---|
| 手写的 13 个配置记录 + `ProcessBuilder$RedirectPipeImpl` 被 agent **全部采到** | 手写审计**准确**（确实需要） |
| `sun.misc.Signal` / `SignalHandler`、`TaskRequest` / `TaskResult` 未被采到 | 因为本次没走到那条路径 → **静态审计能补 agent 的盲区**；这些条目应标「未验证」而非删掉 |
| 资源 `AhaDefault.yaml` / `ModelDefault.yml` / `version.properties` / `META-INF/services/*` / `*.so` 全部被采到 | 证实资源正则包含 `yaml\|yml\|properties\|so` 是**真实需求**（漏了就是运行期缺文件） |
| 400 条中绝大多数是 log4j / JDK 内部 / JLine / sqlite | 这些**依赖自带或自动处理**，不该手写 → 印证「来源优先级」 |

**用法**：agent 输出不要逐条看，先**按包前缀分组计数**（脚本见
[discovery](discovery.md) §2.4），把「自带元数据的依赖」整组划掉，
剩下的才是你要处理的。

## B. 常见框架（**推断**，未在本项目实测）

> 下列条目来自公开资料与通用经验，**未经本技能验证**，使用前请按自己的版本核对。

| 框架 | 自带元数据？ | 已知点 / 做法 | 状态 |
|---|---|---|---|
| Spring Boot 3 | 部分（AOT 引擎 / `RuntimeHints`） | `@ImportRuntimeHints`、`RuntimeHintsRegistrar`；反射型 Bean 需 hint | 推断 |
| Quarkus / Micronaut | ✅（构建期生成） | 用其原生构建步，无需手写 | 推断 |
| Hibernate ORM | ❌（需注册） | 实体类构造器 / 方法 / 字段；字节码增强 | 推断 |
| MyBatis | ❌ | Mapper 接口、结果类 | 推断 |
| Groovy / JRuby / Nashorn | 极难 | 运行期动态生成类，closed-world 冲突，通常不适合原生镜像 | 推断 |
| Netty | 部分 | 原生传输、`PlatformDependent` 探测 | 推断 |
| Apache POI | ❌ | OOXML 类反射、资源 | 推断 |
| AWS SDK / gRPC / Protobuf | 多数自带或需少量补充 | 用其官方 native-image 文档 | 推断 |
| 日志（slf4j + logback） | logback 自带部分 | 配置装配运行期初始化 | 推断 |
| FFM（JDK 22+） | ❌ | 需 `foreign` 段（downcall/upcall 描述符） | 推断（JLine 4 自带，可参考其写法） |

## C. 经验条目模板（贡献新条目时按这个填）

```markdown
| **<依赖名> <版本>** | ✅自带 / ❌不带 / 部分 | <已知反射 / JNI / 资源点> | <处理方式> | <状态>（<来源：哪次运行/报错/版本>） |
```

**要求**：

- 写**具体版本**（选型声明写主版本线，证据写确切版本）；
- 写**来源**：哪次运行、哪条报错、工具链版本；
- **被证伪的条目不要删**，改成「不成立 + 反例」——被证伪的经验比成功的经验值钱；
- 推断条目必须显式标「推断」，不要冒充已验证。

## D. 怎么把这张表用成"越用越准"

```
查表 → 按条目处理 → 真机验证 → 把新发现/被证伪的条目回写本表
```

每复用一个项目，这张表就更准一次。这也是本技能「成熟判据」里
「在别的项目复用过」的来源。
