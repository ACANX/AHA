# TS-2610-019 · 修复 issue #35：native 桌面端 `ClassNotFoundException: com.sun.javafx.tk.quantum.QuantumToolkit`

> 日期：2026-10-08
> 作者：@ACANX（与 AI 助手协作）
> 关联 PR：待提交（分支 `fix/issue-35-native-quantum-toolkit`）
> 关联记录：issue #26（父）、issue #35（本项）、`Docs/Design/DesktopNativeDesign.md` §6.1、
> `.agents/skills/java-app-graalvm-native-image-compile/`、`TODO.md` `N-05`

## 1. 背景

修完 issue #26（注册 JavaFX 主类）后，发布页下载的 `aha-desktop-native.exe`
不再是「找不到主类」，而是换了一处崩：

```
WARNING: Unsupported JavaFX configuration: classes were loaded from 'unnamed module ...'
java.lang.ClassNotFoundException: com.sun.javafx.tk.quantum.QuantumToolkit
        at ... Class.forName0 ...
        at com.sun.javafx.tk.Toolkit.getToolkit(Toolkit.java:241)
        at com.sun.javafx.application.PlatformImpl.startup(PlatformImpl.java:279)
Exception in thread "main" java.lang.RuntimeException: No toolkit found
```

也就是说：**构建全绿、产物也在，但二进制一启动就退**。这正是 `BuildSpec.md` 里那句
「构建成功不是验收标准」的第二次现场。issue #26 是父任务，只挂了 #35 这一个子项，
目标是让原生桌面端真正能开窗。

## 2. 排障过程与修复链

### 2.1 先看清「要求」与「实际」，再动手

| 环节 | 要求 | 实际（反编译 JavaFX 25 字节码取证） |
|---|---|---|
| 工具包加载 | `Toolkit.getToolkit()` 拿到 `QuantumToolkit` 实例 | `Class.forName("...QuantumToolkit", false, loader)` + `getDeclaredConstructor().newInstance()`——closed-world 看不见 |
| Glass 工厂 | `PlatformFactory.getPlatformFactory()` 拿到平台工厂 | 类名用 `makeConcatWithConstants` 动态拼出，再 `Class.forName` + `getDeclaredConstructor().newInstance()` |
| Prism 管线 | `GraphicsPipeline.createPipeline()` 拿到渲染管线 | `Class.forName("com.sun.prism.<平台>.<X>Pipeline")` + `getMethod("getInstance").invoke()` |
| 字体 / 日志 / 反射辅助 | 各自拿到实例或方法 | 同样是 `Class.forName` 系列（`DWFactory`、`PrintLogger`、`Trampoline`、`control.skin.Utils` …） |
| Glass 原生回调 | 原生库能回调 Java、读写结构体字段 | 需要 JNI 可达（`jniAccessible`）+ 方法 / 字段注册，否则运行期 `MissingReflectionRegistrationError` |

取证手段：解压 `aha-desktop-native/target/native/lib/javafx-*-25-win.jar`，
对 `com/sun/{javafx,glass,prism,scenario}` 下 2145 个类批量 `javap -p -c`，
按 `Class.forName` 逐个回溯目标类名。

### 2.2 根因

**只注册一个类没有意义。** 启动链路上的反射是**链式**的：
`QuantumToolkit` 之后紧接着 `PlatformFactory`（平台手势）、`Prism`（渲染管线）、
`PulseLogger`、字体加载…… 每修一个就会出现下一个 `ClassNotFoundException`。
所以本次按「**启动链路**」一次性补齐，而不是只补报错里的那一个类。

### 2.3 修法

把启动链路的反射与 JNI 清单补进
`aha-desktop/src/main/resources/META-INF/native-image/com.acanx.module.aha/aha-desktop/reachability-metadata.json`，
按逻辑分组（共 288 条新条目，总计 293 条）：

| 分组 | 类型 |
|---|---|
| 工具包与启动辅助 | `QuantumToolkit`、`PrintLogger`、`JFRPulseLogger`、`Trampoline`、`control.skin.Utils`、`GraphicsPipeline`、`J2DImageLoaderFactory`、`JavaBeanQuickAccessor` |
| Glass 平台工厂 | `WinPlatformFactory`、`GtkPlatformFactory`、`MacPlatformFactory`（构造器） |
| Prism 管线 | `D3DPipeline`、`ES2Pipeline`、`SWPipeline`、`J2DPipeline`、`PrismPrintPipeline`（`getInstance`） |
| 效果渲染器 | `PrRenderer` / `PPSRenderer` / `PSWRenderer`（`createRenderer`）、`JSWRendererDelegate` / `SSERendererDelegate`（构造器） |
| 着色器 | `D3DShaderSource` / `ES2ShaderSource`（构造器）；**全部 212 个** stock shader `*_Loader`（`loadShader(ShaderFactory, String, InputStream)`） |
| Glass 原生回调 | `Application`、`Cursor`、`Menu`、`MenuItem$Callback`、`Screen`、`Size`、`View`、三平台 `*Application`、`NativeLibLoader`（`jniAccessible` + 全量方法 / 字段） |
| 图片解码 | `ImageLoaderImpl`、`JPEGImageLoader`（`jniAccessible`） |
| 字体 | DirectWrite（Windows）、CoreText（macOS）、FreeType（Linux）的工厂与结构体 |
| **AHA 自身的 Jackson 记录** | 13 个配置记录（`AhaConfig` / `ModelConfig` / `ProviderConfig` …）+ `TaskRequest` / `TaskResult` / `ToolCall`（`allDeclaredConstructors/Methods/Fields`） |
| 其它 | `PrismSettings` 字段、`javafx.scene.paint.Color` |

**为什么用 `allDeclaredMethods` / `allDeclaredFields` 而不是逐条列方法**：
这些类的成员是**原生库按名字查找**的，逐条列极易漏；类数量有限，全量注册的代价可控，
换来的是一次到位。而入口处的反射点（`<init>` / `getInstance` / `createRenderer`）仍精确到签名，
避免顺手把用不上的大块代码拉进镜像。

同时补了**着色器资源**：D3D 的内建 shader 是 `.obj`（HLSL 预编译字节码，
`D3DShaderSource` 用 `getResourceAsStream` 读），ES2 的是 `.frag` / `.vert`，
原先的 `-H:IncludeResources` 只认 `png|css|...|dll`，**漏掉了 `.obj`**——
症状不是构建失败，而是首次绘制时 shader 加载不到（又一次「静默坏掉」）。
已在两份参数文件（`native-image-args.txt` / `-jdk27.txt`）的资源正则里补上
`obj|frag|vert|glsl|hlsl` 与 `bss`。

### 2.4 完整性审计（这一节就是回答「还会不会又报别的类找不到」）

先把「已知的全部 `Class.forName` 点」穷举一遗再动手：对 4878 个 JavaFX 类逐个
`javap`，提取每个反射点的目标类（含 `invokedynamic` 动态拼出的名字）。结果：

| 反射点 | 目标 | 处置 |
|---|---|---|
| `Toolkit.getToolkit` | `QuantumToolkit` | ✅ 已注册 |
| `PlatformFactory` | `com.sun.glass.ui.<平台>.<平台>PlatformFactory` | ✅ 已注册三平台 |
| `GraphicsPipeline.createPipeline` | `com.sun.prism.<管线>.<X>Pipeline` | ✅ 已注册四条 |
| `PrismFontFactory.getFontFactory` | `DWFactory` / `CTFactory` / `FTFactory` | ✅ 已注册 `getFactory()` |
| `PrRenderer` / `PPSRenderer` | `PPS*Renderer` / `PSW*Renderer` / `D3DShaderSource` / `ES2ShaderSource` | ✅ 已注册 |
| `D3DResourceFactory.createStockShader` | `com.sun.prism.shader.<name>_Loader`（**212 个**） | ✅ 全部注册 |
| `PulseLogger` / `MethodUtil` / `StyleManager` / `ImageStorage` | `PrintLogger` / `JFRPulseLogger` / `Trampoline` / `control.skin.Utils` / `J2DImageLoaderFactory` | ✅ 已注册 |
| `Control.loadClass`（`-fx-skin`） | 皮肤类 | ⚪ **不需要**：本应用所有 `Control` 都重写了 `createDefaultSkin()`（静态引用），modena 也没有 `-fx-skin` 规则 |
| `PPSRenderer` 的 `<effect>Peer` | `PPS*Peer` / `PSW*Peer` 等 | ⚪ **不需要**：本应用不调用任何 effect（无 `DropShadow` / `-fx-effect`） |
| `PlatformImpl` 的可选模块探测 | Swing / Web / Media / FXML | ⚪ **不需要**：本应用不引这些模块，探测失败已被捕获 |
| `J2DFontFactory` / `J2DPrinterJob` | `sun.font.*` / `javax.print.*` | ⚪ 仅打印路径，本应用不打印 |
| `QuantumClipboard` 反序列化 / `LauncherImpl` preloader | 剪贴板载荷类 / 预加载器 | ⚪ 本应用不涉及 |

**关键发现：JavaFX 之外还有一大块反射，与「启动链路」无关但同样会炸**：

| 依赖 | native-image 元数据 | 结论 |
|---|---|---|
| `sqlite-jdbc` | 自带 `native-image.properties` | ✅ 不需要本项目处理 |
| `log4j-core` / `log4j-api` | 自带 `reflect-config.json`（61 KB）与 `resource-config.json` | ✅ 不需要本项目处理 |
| **Jackson 3（databind / core / annotations）** | **不随附任何元数据** | ⚠️ AHA 的配置记录与会话记录靠 Jackson 反射读写，**必须自己注册**——已把 13 个配置记录 + `TaskRequest` / `TaskResult` / `ToolCall` 注册 |
| `snakeyaml-engine` | 无元数据（但实际只解析到 `JsonNode`） | ⚪ 当前路径无 POJO 反射 |

**仍不能声称「完整」**（这是诚实的结论）：

1. **AHA 自身还会新增反射点**：Jackson 记录清单是**手写**的，新增一个带
   `@JsonProperty` 的记录、或某个功能用上 effect / 自定义 skin，就需要补一条；
2. **扩展系统本来就不能在原生镜像下跑**（`ModuleLayer` 与 closed-world 根本冲突，
   见 `TODO` `E-01`）——这是设计层面的已知非目标，不是本次回归；
3. **手工清单无法证明完整**：唯一能「抄底」的做法是用 GraalVM 的 tracing agent
   在真实运行时采集元数据（已登记为 `N-17`）。

因此：**不能把这一版当作「一定不再报 ClassNotFound」**；它把「启动链路 + 已知动态集合
+ Jackson 记录」这三类确定性缺口一次补齐，可真机运行仍必须走 `N-05` / `N-16`。

### 2.5 元数据来源（诚实说明）

这份清单**不是**本机 agent 采集的：本机没有 GraalVM，也没有能跑 JavaFX 的显示环境。
它是「静态分析 JavaFX 25 字节码」+「对照已跑通的同类工程公开配置（Gluon Substrate 等）」
推导出来的。因此仍可能不完整。这是刻意的取舍：**先把链路上确定要用的补上**，
真机运行若还报缺，再按同一格式补——这正是 `N-05` 要收的信号。

## 3. 最终验证结果

| 验证 | 结果 |
|---|---|
| `NativeImageMetadataTest` 单测（7 条） | ✅ 通过（`./mvnw -pl aha-desktop -am test -Dtest=NativeImageMetadataTest`） |
| 反向验证：临时删掉 `QuantumToolkit` 注册 | ✅ 两条断言失败并点名 issue #35；恢复后通过 |
| 反向验证：把 `loadShader` 改成两参 | ✅ 签名断言失败并点名 `MissingReflectionRegistrationError` |
| JSON 合法性 | ✅ 用 GraalVM 官方 `reachability-metadata-schema-v1.2.0.json` 校验通过（293 条、类型唯一） |
| `reachability-metadata.json` 读回一致 | ✅ 备份 / 恢复后 `diff` 无差异 |
| 三平台原生镜像真机启动 | ⏳ **未验证**——按 `AGENTS.md`，分钟级原生构建由 CI（`DesktopNative.yml`，可选腿）承担；本机只跑快检查 |
| 真机「双击能开窗、能对话」 | ⏳ 仍由 `N-05` 跟踪 |

> 说明：原生镜像管线是**可选**的，CI 即便某条腿失败也不挡合并。因此这里的「未验证」
> 必须在真机上补一次，不能把「CI 编译通过」当成「能跑」。

## 4. 关键教训

1. **框架的启动入口是「链式反射」，修一个不够**：只注册报错里那个类，下一次运行会换一个类继续崩。
   正确的单位是「启动链路」，不是「某一行报错」。
2. **JNI 与反射是两件事**：`Class.forName` 注册只解决「找得到类」；原生库回调 Java、
   读写结构体字段还需要 `jniAccessible` + 字段 / 方法注册，否则是运行期错误而不是构建错误。
3. **平台差异要一起写**：同一份元数据在 win / linux / mac 三条腿上共用，
   平台专属类缺席只会在对应那条腿上炸。三个平台工厂、四条管线都写上，哪怕某平台用不到。
4. **能自动验证的尽量自动**：把「启动链路必须注册」写成单测 + 工作流产物自证双层守卫，
   不靠人记得。反向验证（删掉注册 → 测试必红）才算这条守卫真的有效。
5. **别照抄同类工程的公开配置，签名要按本版本字节码核对**：参考配置里的
   `loadShader(ShaderFactory, InputStream)` 实际是**三参**（`ShaderFactory, String, InputStream`），
   直接照抄会让 `getMethod` 在运行期找不到方法（`MissingReflectionRegistrationError`）。
   注册的是「方法签名」，不是「类名」，跨版本时后者稳定、前者不一定。
6. **诚实标注清单的成熟度**：推导出来的清单要写明「按静态分析得出、真机待验证」，
   否则下一个人会把「我猜这样能行」当成「我试过这样能行」。
7. **「启动链路」只是反射的一部分**：真正会炸的还有**业务层**——本次就靠审计发现
   Jackson 3 **不随附 native-image 元数据**，而 AHA 的配置与会话记录正是 Jackson 读写；
   不查这一层，就会「窗口开得起来、一存配置就炸」。审计要覆盖运行时依赖，不只框架。
8. **手工清单不叫「完整」，叫「已知缺口已闭」**：要「证明完整」只能靠 tracing agent
   或真机逐功能跑（`N-17` / `N-05`）。

## 5. 涉及文件清单

- `aha-desktop/src/main/resources/META-INF/native-image/com.acanx.module.aha/aha-desktop/reachability-metadata.json`（293 条，新增 288 条）
- `aha-desktop/src/test/java/com/acanx/module/aha/desktop/NativeImageMetadataTest.java`（扩到 5 条断言）
- `.github/workflows/DesktopNative.yml`（产物自证第 ⑧ 条改为逐类检查启动链路）
- `Docs/Design/DesktopNativeDesign.md`（R2 更新 + 「已踩的反射坑：JavaFX 启动链路」小节）
- `CHANGELOG.md`
- `aha-desktop-native/src/native/native-image-args.txt`、`native-image-args-jdk27.txt`（资源正则补 `obj|frag|vert|glsl|hlsl|bss`）
- `.agents/skills/java-app-graalvm-native-image-compile/references/troubleshooting.md`、`args-cookbook.md`、`SKILL.md`（技能同步）
- `Docs/Troubleshooting/TS-2610-019-Quantum-Toolkit-Missing.md`（本文件）

## 6. 后续补充（2026-10-08，tracing agent 采集）

本节记于第 1~5 节之后：在 WSL + GraalVM 25.0.2 上对 GUI 真实跑了一轮 tracing agent，
采集到 **422 个反射 + 69 条资源**，过滤后补进 **47 条**应用栈条目（元数据总计 340 条）——
包括 JavaFX 运行期反射（`javafx.scene.*` 等）、Linux 平台实现类（`GtkView` / `GtkWindow` /
`GtkPixels` / `X11GLFactory`）、ServiceLoader provider，以及 3 条按扩展名正则覆盖不到的
资源（`LineBreakIteratorData` / `*.icu` / `NativeLibLoader.class`）。

- 方法论与工具已沉淀为技能 `.agents/skills/graalvm-reachability-metadata/`
  （agent 采集制度见其 `references/agent-collection.md`，编排脚本 `scripts/collect-metadata.sh`）；
- 细节与「仍不构成完整」的边界见 `Docs/Design/DesktopNativeDesign.md` §6.1；
- Windows / macOS 平台的采集待做（`TODO.md` `N-22`）。

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- 修完 issue #26 之后，发布页的 `aha-desktop-native.exe` **换了一处崩**：

  ```text
  WARNING: Unsupported JavaFX configuration: classes were loaded from 'unnamed module ...'
  java.lang.ClassNotFoundException: com.sun.javafx.tk.quantum.QuantumToolkit
  Exception in thread "main" java.lang.RuntimeException: No toolkit found
  ```

  即：**构建全绿、产物也在，但二进制一启动就退**。
- **取证规模**：解压 `aha-desktop-native/target/native/lib/javafx-*-25-win.jar`，
  对 `com/sun/{javafx,glass,prism,scenario}` 下 **2145 个类**批量 `javap -p -c`，
  按 `Class.forName` 逐个回溯目标类名；完整性审计时进一步对 **4878 个** JavaFX 类逐个 `javap`
  （含 `invokedynamic` 动态拼出的名字）。
- **修复规模**：新增 **288 条**注册（总计 **293 条**），分 10 组——工具包与启动辅助 / Glass 平台工厂 /
  Prism 管线 / 效果渲染器 / 着色器（**全部 212 个** stock shader `*_Loader`）/
  Glass 原生回调（`jniAccessible` + 全量方法字段）/ 图片解码 / 字体（DirectWrite、CoreText、FreeType）/
  **AHA 自身 Jackson 的 13 个配置记录 + `TaskRequest` / `TaskResult` / `ToolCall`** / 其它。
- **同时补了着色器资源**：D3D 的内建 shader 是 `.obj`（HLSL 预编译字节码，`D3DShaderSource`
  用 `getResourceAsStream` 读），ES2 的是 `.frag` / `.vert`；而原 `-H:IncludeResources`
  只认 `png|css|…|dll`，**漏掉了 `.obj`** ⇒ 症状不是构建失败，而是**首次绘制时 shader 加载不到**。
  已在两份参数文件里补 `obj|frag|vert|glsl|hlsl` 与 `bss`。
- **依赖元数据审计**：`sqlite-jdbc` ✅ 自带；`log4j-core` / `log4j-api` ✅ 自带
  （`reflect-config.json` **61 KB** + `resource-config.json`）；
  **Jackson 3（databind / core / annotations）不随附任何元数据** ⚠️ ⇒ 必须自己注册；
  `snakeyaml-engine` 无元数据但当前路径只解析到 `JsonNode` ⚪。
- **验证**：`NativeImageMetadataTest`（**7 条**断言）通过；
  反向验证①删除 `QuantumToolkit` 注册 → 两条断言失败并**点名 issue #35**；
  反向验证②把 `loadShader` 改成两参 → 签名断言失败并**点名 `MissingReflectionRegistrationError`**；
  JSON 用官方 `reachability-metadata-schema-v1.2.0.json` 校验通过（293 条、类型唯一）；
  备份 / 恢复后 `diff` 无差异。
- **三平台原生镜像真机启动当时未验证**（按项目约定，分钟级原生构建由 CI 承担；
  本机只跑快检查）；真机「双击能开窗、能对话」由 `N-05` 跟踪。
- **「6、后续补充（2026-10-08）」**：在 WSL + GraalVM 25.0.2 上对 GUI 真实跑了 tracing agent，
  采集 **422 个反射 + 69 条资源**，过滤后补进 **47 条**应用栈条目（元数据总计 **340 条**），
  含 Linux 平台实现类（`GtkView` / `GtkWindow` / `GtkPixels` / `X11GLFactory`）、
  ServiceLoader provider，以及 **3 条按扩展名正则覆盖不到的资源**
  （`LineBreakIteratorData` / `*.icu` / `NativeLibLoader.class`）；Windows / macOS 采集待做（`N-22`）。

### 机制 / 行为模型

- **框架的启动入口是「链式反射」，修一个不够**：只注册报错里那个类，下一次运行会**换一个类继续崩**。
  正确的单位是「**启动链路**」，不是「某一行报错」。
- **JNI 与反射是两件事**：`Class.forName` 注册只解决「找得到类」；
  原生库回调 Java、读写结构体字段还需要 `jniAccessible` + 字段 / 方法注册，
  否则是**运行期**错误而不是构建错误。
- **平台差异要一起写**：同一份元数据在三平台共用，平台专属类缺席只会**在对应那条腿上炸**。
- **别照抄同类工程的公开配置，签名要按本版本字节码核对**：参考配置里的
  `loadShader(ShaderFactory, InputStream)` 实际是**三参**（`ShaderFactory, String, InputStream`），
  照抄会让 `getMethod` 在运行期找不到方法。注册的是「**方法签名**」而不是「类名」，
  跨版本时后者稳定、前者不一定。
- **「启动链路」只是反射的一部分**：真正会炸的还有**业务层**——本次靠审计才发现 Jackson 3
  不随附元数据，而 AHA 的配置与会话记录正是 Jackson 读写；不查这一层，就会
  「**窗口开得起来、一存配置就炸**」。
- **手工清单不叫「完整」，叫「已知缺口已闭」**：要「证明完整」只能靠 tracing agent
  或真机逐功能跑。

### 未定位 / 未复现

- **三平台真机启动未验证**：本机没有 GraalVM，也没有能跑 JavaFX 的显示环境
  ⇒ 「双击能开窗」**未在本机复现**，由 `N-05` / `N-16` 跟踪。
- **清单完整性无法证明**：本档案自己写明三条理由——
  ① AHA 自身还会新增反射点（Jackson 记录清单是**手写**的）；
  ② 扩展系统本来就不能在原生镜像下跑（`ModuleLayer` 与 closed-world 冲突，`TODO` `E-01`）；
  ③ 手工清单无法证明完整，唯一「抄底」手段是 tracing agent（`N-17`）。
- **元数据不是本机 agent 采集的**（诚实说明）：它是「静态分析 JavaFX 25 字节码」+
  「对照已跑通的同类工程公开配置（Gluon Substrate 等）」**推导**出来的，因此仍可能不完整。
- **Windows / macOS 平台的 tracing agent 采集未做**（`N-22`）。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 启动即崩的原文 | 运行原生 exe | `ClassNotFoundException: com.sun.javafx.tk.quantum.QuantumToolkit` + `RuntimeException: No toolkit found` |
| 取证规模 1 | 解压 `javafx-*-25-win.jar` + 批量 `javap -p -c` | **2145 个类**（`com/sun/{javafx,glass,prism,scenario}`） |
| 取证规模 2（完整性审计） | 逐个 `javap` 4878 个 JavaFX 类 | 提取每个反射点目标（含 `invokedynamic` 拼出的名字） |
| 修复规模 | `reachability-metadata.json` | 新增 **288 条**，总计 **293 条**，分 **10 组** |
| stock shader 数量 | 同上 | **212 个** `*_Loader` 全部注册 |
| 漏掉的资源类型 | 两份参数文件的资源正则 | 原为 `png|css|…|dll`，**漏 `.obj`** ⇒ 首次绘制时 shader 加载不到 |
| 依赖自带元数据 | 查各 jar | `sqlite-jdbc` ✅ / `log4j-core`+`log4j-api` ✅（`reflect-config.json` **61 KB**）/ **Jackson 3 ❌** / `snakeyaml-engine` ⚪ |
| 单测 | `-pl aha-desktop -am test -Dtest=NativeImageMetadataTest` | **7 条**断言通过 ✅ |
| 反向验证① | 删掉 `QuantumToolkit` 注册 | 两条断言失败并**点名 issue #35**；恢复后通过 ✅ |
| 反向验证② | 把 `loadShader` 改成两参 | 签名断言失败并点名 `MissingReflectionRegistrationError` ✅ |
| JSON 合规性 | 官方 `reachability-metadata-schema-v1.2.0.json` | 293 条、类型唯一，校验通过 ✅ |
| **tracing agent 采集（后续）** | WSL + GraalVM 25.0.2 上真跑 GUI | 采到 **422 个反射 + 69 条资源**；过滤后补 **47 条**（总计 **340 条**） |
| 采集补到的资源 | 同上 | `LineBreakIteratorData` / `*.icu` / `NativeLibLoader.class`（**按扩展名正则覆盖不到**） |
| 仍未验证 | — | **三平台真机启动**（`N-05`）与 **Windows / macOS 采集**（`N-22`） |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「注册主类就够了」（issue #26 的延续） | 启动链路是**链式**的：修完主类就换成 `QuantumToolkit` 崩（「1、背景」） |
| 「只注册报错里那个类就行」 | 审计出 `PlatformFactory` / Prism 管线 / 字体工厂等**一串**同类反射点（「2.1」「2.4」） |
| 「反射注册能顺带解决 JNI」 | JNI 回调与结构体字段读写另需 `jniAccessible` + 字段 / 方法注册（「4、教训」第 2 条） |
| 「模板里没有的就都不用注册」 | 逐项审计后明确排除的只有：`-fx-skin` 皮肤类（本应用 Control 都重写 `createDefaultSkin()`）、effect Peer（不调用任何 effect）、Swing / Web / Media / FXML 可选模块探测、打印路径、剪贴板反序列化与 preloader（「2.4」表） |
| 「照抄同类工程的公开配置即可」 | 参考配置的 `loadShader` 签名是**两参**、本版本实际是**三参**，照抄会让 `getMethod` 运行期失败（「4、教训」第 5 条） |
| 「框架查完就完事」 | 审计发现 **Jackson 3 不随附元数据**，而配置与会话记录靠它读写（「2.4」末表） |
| 「手工清单 = 完整」 | 档案自己写明三条不完整理由（AHA 自身会新增 / 扩展系统不可用 / 手写无法证明）（「2.4」末段） |

## 未决问题

**Q1 三平台原生二进制真能开窗、能对话吗？**

现状：**未验证**（本机无 GraalVM、无显示环境）。由 `TODO.md` **N-05**（真机）与 **N-16** 跟踪。
阻塞点：需要真机 + 已发布的原生包。

**Q2 手工清单的完整性怎么「抄底」？**

现状：唯一手段是用 GraalVM 的 **tracing agent** 在真实运行时采集元数据（登记为 **N-17**）。
> **进展**：本档案「6、后续补充」已在 WSL + GraalVM 25.0.2 上对 **GUI** 跑过一轮，
> 补入 47 条；**Windows / macOS 平台仍待做**（`N-22`）。
阻塞点：采集需要能真跑界面的环境，各平台都要单独采。

**Q3 AHA 自身的反射点还会增长，怎么跟？**

现状：Jackson 记录清单是**手写**的——新增一个带 `@JsonProperty` 的记录、
或某功能用上 effect / 自定义 skin，就要补一条。
阻塞点：能否用「注解处理器生成 + 单测断言」把这块也自动化（CLI 侧的 picocli 已是这么做的，
见 `TS-2610-020`），尚未评估。

**Q4 扩展系统在原生镜像下能不能用？**

现状：**不能**——`ModuleLayer` 与 closed-world 根本冲突，属**设计层面的已知非目标**
（`TODO` `E-01`），不是本次回归。
阻塞点：需要产品层面确认原生包是否需要扩展能力。
