# TS-202610-QuantumToolkitMissing：修复 issue #35：native 桌面端 `ClassNotFoundException: com.sun.javafx.tk.quantum.QuantumToolkit`

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
- `Docs/Troubleshooting/TS-202610-QuantumToolkitMissing.md`（本文件）

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
