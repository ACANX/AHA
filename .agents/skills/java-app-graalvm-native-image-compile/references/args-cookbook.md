# native-image 参数速查

按「必须有 / 建议有 / 实验位」三档排列，并标注**本项目是否已验证**。
参数清单请放在**一份文本文件**里，用 `native-image @argfile` 传入 —— 集中一处才谈得上迭代。

## 零、先读日志：一次真编能告诉你九成该改什么

2026-10-08 在 AHA 上首次真编（GraalVM 25 + Windows），构建成功，但输出里有
**9 条警告 + 5 条 Recommendations + 1 条 Security 报告**——它们几乎就是
「参数清单该怎么改」的待办列表。**别忽略它们，逐条处置，并把「不采纳的理由」也写下来**，
否则半年后没人敢动这份清单。

### 0.1 弃用与实验性的处置（照这个顺序做）

| 日志里的说法 | 正确处置 | 说明 |
|---|---|---|
| 某选项 "deprecated ... **No effect**, no replacement available" | **删除**（如 `- -no-fallback`） | 它没有作用，还常常牵出别的弃用警告（`FallbackThreshold`） |
| 某选项 "is experimental and must be enabled via `-H:+UnlockExperimentalVMOptions`" | 加 `-H:+UnlockExperimentalVMOptions`，**且必须排在实验性选项之前** | 不解锁，将来版本直接失败 |
| "Use the `-o` option instead"（针对 `-H:Path` / `-H:Name`） | 输出改用 `-o <路径>` | 官方给的替代品；POM 侧传参即可 |
| "deprecated ... Use reachability metadata instead"（如 `- -enable-url-protocols`） | **先别删**，先落元数据并真机验证，再删 | 见下条：这类删除是「静默坏掉」风险 |

> ⚠ **「静默坏掉」优先于「警告清零」**：`- -enable-url-protocols` 是 HTTPS 的开关。
> 删了它，构建照样成功、进程照样启动，只是**发不出网络请求**——比一条警告严重得多。
> 正确顺序：① 把元数据放进 `src/main/resources/META-INF/native-image/<groupId>/<artifactId>/`
> （随 jar 进 classpath，native-image 自动读取，不需要任何参数）；
> ② 真机跑一次真实请求；③ 确认建报告里没有相关未决条目；④ 才删弃用项。

### 0.2 Recommendations 的处置（哪些该采纳、哪些不该）

| 建议 | 处置 | 理由 |
|---|---|---|
| `- -gc=G1` | **采纳**（长驻 GUI / 服务） | serial 是默认值、epsilon 无回收；G1 换延迟，代价是体积与构建时间 |
| `- -future-defaults=all` | **采纳**（打算长期跟进的库） | 提前用上未来默认值，等于给升级做早期预警：编不过就是信号 |
| 设置最大堆（`-R:MaxHeapSize=`） | **采纳** | 默认堆按机器内存百分比算，同一二进制在不同机器行为不同 |
| `- -pgo` | **先别加** | 日志里 builder configuration 若已写 `PGO: ML-inferred`，说明推测式 PGO 已在生效；手工 PGO 需要两阶段（插桩编译 → 跑典型负载 → 用 profile 重编） |
| `-march=native` | **对外分发的产物不要用** | 绑死构建机 CPU 特性，别人可能非法指令崩溃；要提性能写可预期目标（如 `-march=x86-64-v3`） |
| `-H:AdvancedObfuscation=""` | **默认不加** | 会污染栈与诊断信息；与「可诊断优先」冲突，只作数据点 |
| Security: "Binary includes Java deserialization" | **不要猜选项名** | 这是可达性结论而非开关：先确认业务不对不可信数据反序列化，再按 build report 定位可达路径 |

### 0.3 让报告与工作目录配合起来

- 加 `emit build report`（native-image 的参数）：产出机器可读报告，**它是「下一步加什么参数」的唯一依据**；
- 把 exec 的**工作目录设成产物目录**（如 `target/native/`），报告就与可执行文件同处一地，
  既能被打进 zip、也能被 CI 的自证步骤直接检查。安全性前提：参数文件、`-cp`、`-o` 都用绝对路径。

### 0.4 本机怎么「不装 GraalVM 也能验证命令行」

把可执行文件换成 `echo`（例如 Maven 的 `-Dnative.image.executable=/bin/echo`），
构建就会把**完整的 native-image 命令行**打印出来。用于验证：
参数文件路径、`-o` 的形式、classpath 是否含预期依赖。零成本，不需要工具链。

## 一、必须有（缺了通常直接失败或明显不对）

| 参数 | 作用 | 说明 |
|---|---|---|
| `--no-fallback` | 不允许退回 JVM 模式 | **强烈建议**：允许回退会让人误以为「编出原生镜像了」。失败就让它失败 |
| `-cp <classpath>` / `--module-path <dir>` | 输入 | classpath 整串作为**一个** argv 传入（`exec` 的 `<argument>` 不做词分割） |
| `-H:Name=<name>` + `-H:Path=<dir>` | 产物名与输出目录 | 可执行文件名；Windows 上会自动加 `.exe` |
| 主类（位置参数） | 入口 | 或 `-jar app.jar` |
| `--no-fallback` 之外还要**明确堆上限**（宿主 JVM） | `-J-Xmx4g` | 构建 native-image 很吃内存；CI 小内存 runner 上不加容易 OOM |

## 二、建议有（按平台/场景）

| 参数 | 适用 | 说明 |
|---|---|---|
| `--enable-native-access=ALL-UNNAMED` | classpath 模式 + 有 JNI | JNI 调用方在未命名模块里；module-path 模式改成具体模块名 |
| `--enable-native-access=<模块名>` | module-path 模式（如 GUI 工具包） | 与上一行二选一 |
| `-H:+ReportExceptionStackTraces` | 总是 | 构建期异常带栈，否则只有一个类名 |
| `-H:+AddAllCharsets` | 有非 UTF-8 文本/控制台 | 字符集不全时运行期会抛 `UnsupportedCharsetException` |
| `--enable-url-protocols=http,https` | 需要联网 | 不含在默认集合里的协议要显式加 |
| `-O2` | 总是 | 构建慢一点，产物快一点；原生镜像里产物质量比构建时长重要 |
| `-H:IncludeResources=<正则>` | 有资源 | 见下方「资源清单」 |
| `-H:IncludeResources=META-INF/services/.*` | 用 `ServiceLoader` | 服务文件必须进镜像，否则「实现类找不到」 |

## 三、四类清单的种子

### 3.1 资源清单（`-H:IncludeResources`）

按项目实际类型收窄，越窄体积越省：

```
-H:IncludeResources=.*\.(png|jpg|jpeg|gif|css|xml|properties|txt|json|so|dylib|dll|dat|bin)$
-H:IncludeResources=META-INF/services/.*
```

注意把 **原生库扩展名**（`.so` / `.dylib` / `.dll`）纳进来：GUI 工具包与 JNI 驱动的本地库
通常就在它们的 jar 里，运行期由加载器解压 —— 不放进镜像就会 `UnsatisfiedLinkError`。

### 3.2 运行期初始化清单（`--initialize-at-run-time`）

判据一句话：**构建期初始化就会去碰「只有运行期才存在的东西」**（屏幕、窗口、原生库、
系统主题、文件系统路径、驱动环境）。典型重灾区：

| 领域 | 典型类/包 |
|---|---|
| GUI 工具包（以 JavaFX 为例） | `com.sun.javafx.application.*`、`com.sun.glass.ui*`、`com.sun.glass.utils.NativeLibLoader`、`com.sun.prism*`、`com.sun.scenario.animation*` |
| JDBC 驱动（以 SQLite JDBC 为例） | 驱动主包（静态初始化会解压/加载本地库） |
| 日志框架（以 log4j2 为例） | 配置装配与工具类（构建期会读到构建机的路径） |

**这些条目要按你自己的报错补**：构建期报「某个类必须运行期初始化」时，把那个类加进去即可。

### 3.3 反射清单

- 优先用**框架已有的**注册机制（如运行时提供的 `RuntimeHints`、配置类）而不是手写 json；
- 手写时用 `-H:ReflectionConfigurationFiles=reflect-config.json`，条目形如
  `{"name":"a.b.C","methods":[{"name":"m","parameterTypes":[]}]}`；
- 只出现在**运行期**的失败（构建通过、启动就炸）多半是反射或资源，优先补这两类。

### 3.4 GC 与堆（实验位，按场景选）

| 参数 | 取舍 |
|---|---|
| `--gc=serial`（默认） | 单线程回收，停顿可预期；体积最小 |
| `--gc=G1` | 堆大时更稳；启动与静态体积更大 |
| `--gc=epsilon` | **没有运行时回收**，只适合短命进程（长驻 GUI 会 OOM） |
| `-R:MaxHeapSize=512m` | 镜像内运行期堆上限；长驻程序建议显式给 |
| `-H:-IncludeAllTimeZones` / `-H:-IncludeAllLocales` | 体积瘦身，按需 |

## 四、体积与启动的常见杠杆

- 收窄 `-H:IncludeResources`（最大杠杆）；
- `-H:-IncludeAllTimeZones`、`-H:-IncludeAllLocales`；
- 减少反射注册范围；
- 换 GC（epsilon 最小最快，但语义要能接受）；
- 优化等级（`-O2` 与更激进的优化换构建时长）。

## 四点五、XML 里写参数时的一个硬坑

用 Maven 时难免要在 `pom.xml` / assembly 描述符的**注释**里写选项。注意：

- XML 注释体**禁止出现 `--`**，否则解析器直接报
  `String '--' not allowed in comment`。写双短横选项（`--gc=G1`、`--no-fallback`、
  `emit build report` 之类）时极易踩到；
- XML 注释**不能嵌套**：外层会被内层的 `-->` 提前闭合，剩下的文字变成正文，
  报错位置离真正的原因很远（实测踩过：模板里嵌了一层 `<!-- ... -->`，谁抄谁坏）。

**两个都可以用一条正则检查掉**（AHA 的 `bin/CheckScripts.py` 里已固化）：
扫 `<!--(.*?)-->`，体内含 `--` 或 `<!--` 即失败。

## 五、把参数当资产

- 参数文件**原样打进产物包**：事后能对上「这个二进制是怎么编出来的」；
- 按 JDK 轴分文件（`…-jdk25.txt` / `…-jdk27.txt`），两次实验的差异就是两份文件的 diff；
- 参数里的每一行都写过「为什么」，半年后回来看还能判断能不能删。
