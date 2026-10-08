# native-image 参数速查

按「必须有 / 建议有 / 实验位」三档排列，并标注**本项目是否已验证**。
参数清单请放在**一份文本文件**里，用 `native-image @argfile` 传入 —— 集中一处才谈得上迭代。

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

## 五、把参数当资产

- 参数文件**原样打进产物包**：事后能对上「这个二进制是怎么编出来的」；
- 按 JDK 轴分文件（`…-jdk25.txt` / `…-jdk27.txt`），两次实验的差异就是两份文件的 diff；
- 参数里的每一行都写过「为什么」，半年后回来看还能判断能不能删。
