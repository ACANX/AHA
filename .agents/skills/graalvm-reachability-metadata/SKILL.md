---
name: graalvm-reachability-metadata
description: 为 GraalVM Native Image 系统性地发现、登记、验证「反射 / JNI / 文件资源 / 运行期初始化」四类可达性元数据，解决「构建成功、一启动就 ClassNotFoundException / MissingReflectionRegistrationError / 缺资源 / UnsatisfiedLinkError」的打地鼠问题，也覆盖两类不抛异常的静默缺口（native 按名回写 Java 字段导致「值全零」、类型在册但**用途**没在册导致反射构造失败）。覆盖三条互补的发现路径（字节码静态审计、tracing agent 采集、经验库对照）、元数据来源优先级（依赖自带 > 框架生成 > 手写）、精确到方法签名的登记写法、以及「单测断言 + 构建产物自证 + 反向验证 + 真机走查」的多层守卫。当需要为原生镜像补 reachability-metadata.json / reflect-config.json / jni-config.json / resource-config.json，或产物在运行期报找不到类、找不到方法、找不到资源时使用。
license: GPL-3.0-or-later
compatibility: 适用于 GraalVM Native Image（JDK 21+；本技能条目在 GraalVM for JDK 25 上验证）。不涉及 cross-compile —— native-image 不能交叉编译，元数据虽与平台无关，但验证必须在目标平台做。
metadata:
  version: "0.2.0"
  owner: ACANX
  source-project: AHA（两个真实目标：JavaFX 桌面端、picocli+JLine CLI；覆盖链式反射、手写与生成、依赖自带元数据三种形态）
  maturity: 可用（两个形态都已在真机跑过；元数据守卫已成 CI 步骤并在 Windows 腿真跑产物；仍保留明确标注的「不可达」条目——触摸/笔手势、LCD 次像素渲染，见「验证状态」）
---

# 原生镜像的可达性元数据登记（反射 / JNI / 文件资源 / 初始化）

这份技能只做一件事：**把「原生镜像运行期会因为缺少可达性元数据而崩」这类问题，
从「靠人记得」变成「有方法可查、有来源可依、有守卫可验」。**

它来自两个真实目标的两轮踩坑：

- **桌面端（JavaFX）**：元数据是**手写**的，启动反射是**链式**的，修一个换一个
  （`AhaDesktopApp` → `QuantumToolkit` → Glass 工厂 → Prism 管线 → 着色器 → 字体）；
- **CLI（picocli + JLine）**：元数据**大部分由框架生成或依赖自带**，手写面从几百条缩到十几条。

对比这两轮，才能提炼出真正可复用的东西：**别问「我漏了哪个类」，要问「我凭什么保证没漏」。**

## 何时使用

- 原生镜像**构建成功**，但一启动 / 一执行某功能就报下列之一：
  - `ClassNotFoundException` / `NoClassDefFoundError`
  - `MissingReflectionRegistrationError`（注册了类但没注册到那个方法 / 字段）
  - `NoSuchMethodException` / `InaccessibleObjectException`
  - `UnsatisfiedLinkError`（JNI 库或回调类未登记）
  - 资源 `null`（`getResourceAsStream` 返回 `null`，随后 NPE 或业务异常，如「配置未找到」）；
  - **完全不抛异常，只是值不对**：界面能开、命令能跑，但某些字段是 `0` / `null` / 空串
    （典型：`native` 侧按名回写 Java 对象的字段失败，见下方「静默缺口」）；
  - FXML / 反射实例化在**运行期**才炸（`LoadException: X is not a valid type.` /
    `Unable to coerce UNAVAILABLE to class …`）：编译与 JVM 冒烟都看不见。
- 要为新项目 / 新依赖引入原生镜像，需要建立元数据基线；
- 已有的元数据清单要**收窄**（体积优化）或**审计**（回答「会不会还漏」）；
- 想把「靠 tracing agent 采集」制度化，替代手工维护。

## 何时不要用

- 还没跑通原生镜像的**构建**（先把构建搞通，本技能不解决编译期问题）；
- 程序**重度依赖运行期动态类加载**（脚本引擎、热插拔、插件框架）：这类反射面无法静态穷举，
  要么接受 tracing agent + 大量人工走查，要么**放弃原生镜像**；
- 只是个别平台缺元数据导致的失败：先看是否是依赖配置问题，而不是元数据问题。

## 先分清在补什么：三类依赖 + 一类时机

失败信息不同，要补的东西就不同。先对号入座，不要一上来就写 JSON。

| 类别 | 典型症状 | 谁在"用"它 | 登记在元数据的哪一段 | 发现手段 |
|---|---|---|---|---|
| **反射** | `ClassNotFoundException` / `MissingReflectionRegistrationError` / `NoSuchMethodException` | `Class.forName`、`getDeclaredConstructor`、`getMethod`、框架的依赖注入 / 序列化 | `reflection`（旧：`reflect-config.json`） | 静态审计 + agent + 经验库 |
| **JNI** | `UnsatisfiedLinkError`、"找不到原生方法"、结构体字段读写错乱 | 原生库按名字**回调** Java 方法、读写字面量字段 / 结构体 | `jni`（旧：`jni-config.json`）+ 反射条目 | 反编译原生接口类 + agent |
| **文件资源** | `getResourceAsStream` 返回 `null`（随后 NPE / `CONFIG_NOT_FOUND` / 界面图标缺失 / 着色器加载失败） | 配置、图标、字体、着色器、`META-INF/services`、模板 | `resources`（旧：`resource-config.json`）或构建参数 `-H:IncludeResources` | 扫源码读取点 + 扫依赖 jar 内非 class 文件 |
| **运行期初始化时机** | 构建期报「类初始化触发了运行期才存在的东西」；或运行期静态状态错乱 | 静态初始化块里碰屏幕 / 数据库 / 文件系统 / 原生库的类 | 构建参数 `--initialize-at-run-time=...` | 按构建报错 + 经验库 |

> **最常见的误判**：把 JNI 失败当成反射失败。注册了类的反射坐标，**不等于**允许原生库回调它 ——
> 后者要 `jniAccessible`（见 [registration](references/registration.md)）。

### 两类「不抛异常」的静默缺口（最容易被当成别的问题）

| 缺口 | 表现 | 为什么难发现 | 归属 |
|---|---|---|---|
| **native 按名回写 Java 状态失败** | 值全是 `0` / `null` / 空串（如 OS 与驱动信息打印成 `Windows version 0.0 build 0`） | 进程不崩、界面照开，只在**打开 verbose 日志**时才看得见；很容易被归成「渲染/驱动问题」 | `jni` 段：**类能加载 ≠ 它的字段能被 native 访问**，两者是两套登记 |
| **类型在册，但「用途」没在册** | 反射**构造**某个类型时炸（FXML 直接实例化 `<Region/>`、`Class#getDeclaredConstructor()`） | 该类型在元数据里明明有条目（`methods` / `fields` 都有），看着「已登记」 | `reflection` 段的**构造器**登记：`allDeclaredMethods` 不等于允许反射 `new` |

> 这两条是 2026-10-13 在 AHA 各踩一次的实测结论（`TS-2610-034`、`TS-2610-032`），
> 判据与修法见 [registration](references/registration.md) §3.3 / §4.1。

## 三条铁律

1. **构建成功不是验收标准。** 四类缺失**全部**在运行期才炸，且不产生编译错误。
   验收必须包含「产物 + 真机跑过相关功能」。
2. **来源优先级：依赖自带 > 框架生成 > 手写。**
   每往前一级，人工穷举面就缩小一圈；手写是最后手段，不是默认手段。
3. **手工清单只能做到「已知缺口已闭」，不能证明「完整」。**
   想知道「还漏不漏」，只有两条路：tracing agent，或真机逐功能走查。
   **交付话术不要写成「一定不再报」，要写成「已知缺口已闭，真机待验证」。**

## 工作流：发现 → 登记 → 验证 → 守卫

```
① 先问「谁自带元数据」 ── 依赖 jar 里有 META-INF/native-image 吗？
        │ 有 → 直接用（最多确认版本对得上），结束
        │ 没有 ↓
② 三路发现（互补，不是三选一）
   A. 静态审计：反编译找 forName / getDeclaredMethod / getResourceAsStream / loadLibrary / registerNatives
   B. tracing agent：JVM 跑真实负载，自动采集
   C. 经验库对照：查 [catalog](references/catalog.md) 里同类框架的已知点
        ↓
③ 登记（选来源优先级最高的方式）
   - 框架有生成机制 → 用它的（如 picocli-codegen、Spring RuntimeHints）
   - 否则手写 → 见 [registration](references/registration.md)
        ↓
④ 验证（先单测，再产物自证，最后真机）
        ↓
⑤ 守卫（把「不许再漏」写成会失败的检查）
```

**顺序不能反**：先发现再登记；先能被单测拦住，再去真机跑。

## 三路发现法（互补）

### A. 静态审计：从字节码反推反射点

适合**没有显示环境 / 不方便跑真实负载**时，也适合审计第三方框架。

要搜的目标（按优先级）：

```bash
# 1) 谁在反射加载？
javap -p -c -v <class>.class | grep -E 'Class\.forName|getDeclaredConstructor|getDeclaredMethod|getMethod|getField|MethodHandles'
# 2) 谁在读资源？
grep -rn "getResourceAsStream\|getResource(" src/
# 3) 谁在碰原生库？
javap -p -c <class>.class | grep -E 'loadLibrary|load\(|registerNatives'
```

两个关键技巧：

- **动态拼出来的类名**：`Class.forName("com.x." + name)` 或 `invokedynamic` 拼字符串时，
  字面量搜不到。要看 `BootstrapMethods` 的 `makeConcatWithConstants` 配方，还原模板
  （如 `com.sun.prism.shader.<name>_Loader`），再决定登记「全部候选」还是「运行时白名单」。
- **穷举而不是抽样**：对框架包做**全量** `javap`（AHA 实测：4878 个 JavaFX 类里只有 28 个含
  `Class.forName`），比读源码猜可靠得多。

**第四个搜索目标：谁被 **native 侧**按名查找？** 这一路最容易整段漏掉，因为它的调用点在框架的
原生代码里（`.cpp` / `.dll` 源码），不在 Java 字节码里：

```bash
# 4) 框架的原生代码在按名查找哪些 Java 类 / 成员？
grep -rnE "FindClass|GetMethodID|GetFieldID|GetStaticMethodID|GetStaticFieldID" \
    <框架>-src/modules/*/src/main/native/ | grep -v test
```

**先判「查找发生在哪一侧」，再决定登记到哪份清单**：

| 查找发生在 | 例子 | 登记到 |
|---|---|---|
| **Java 侧**按名查 | `Class.forName`、`getDeclaredMethod`、Jackson / FXML | `reflection` 段 |
| **native 侧**按名查 | OpenJFX `glass.dll` 找 `WinGestureSupport`、`prism_d3d.dll` 回写 `D3DDriverInformation` 字段 | `jniAccessible` / `jni-config.json`（**不是**只补 reflection） |

**姊妹工程的结论不能直接搬**：别的项目复现了同族故障，**不等于**本仓库有同一个缺口。
AHA 实测过一次反例——排查结论是「查找发生在 native 侧、而本仓库 `jni` 段早已登记」，
照抄过来会补错清单（`TS-2610-033`）。正确动作：先在本仓库找**调用点**（`javap` / 常量池 / native 源码扫描），
再决定补哪一段。

**盲区**：条件分支才走到的路径、你自己业务代码里"未来才会用到"的反射，
静态审计**证明不了不存在**，只能证明"我查过这些"。

详见 [discovery](references/discovery.md)。

### B. tracing agent：让真实运行自己报告

最接近"完整"的一路。用 JVM 正常跑一遍，让 agent 记录实际发生过的反射 / 资源 / JNI：

```bash
java -agentlib:native-image-agent=config-output-dir=native-config -jar app.jar
# 多次运行合并（不同功能各跑一遍）：
java -agentlib:native-image-agent=config-merge-dir=native-config -jar app.jar
```

**要点与坑**：

- 必须跑到**所有**要走查的功能路径，否则就是"跑了一半以为没问题"；
- GUI 程序需要显示环境（Linux 用 `xvfb-run`），且要能**跑完自动退出**（否则关机钩子写不出元数据）——
  给程序加一个"自检 N 秒后退出"的开关，对真机验收同样有用；
- 输出的是**参考**不是终稿：agent 覆盖不到的路径不会出现，仍需静态审计补。

**制度化流程**（本节只管「怎么用」）：自检模式契约、多平台合并、过滤规则、
CI 接入与实测坑，见 [agent-collection](references/agent-collection.md)；
编排脚本 [scripts/collect-metadata.sh](scripts/collect-metadata.sh)（`run` / `summarize` / `filter`）。
三路发现法的细节见 [discovery](references/discovery.md) §2。

### C. 经验库对照：别重新发明已知答案

常见框架的反射 / JNI / 资源点在 [catalog](references/catalog.md) 里按"是否自带元数据"分类。
**先查表，再动手**：很多坑别人踩过，而且比抄某个项目的公开配置更可靠——
抄配置会连对方的错误一起抄（AHA 实测：某参考配置把 `loadShader` 写成两参，实际是三参）。

## 登记：来源优先级

| 优先级 | 来源 | 怎么用 | 代价 |
|---|---|---|---|
| 1 | **依赖自带** | jar 里有 `META-INF/native-image/` 就自动生效，**不用写** | 零；只需确认版本 |
| 2 | **框架生成** | 注解处理器 / 构建插件（picocli-codegen、Spring `RuntimeHints`、Quarkus 构建步） | 加一个编译期依赖 |
| 3 | **tracing agent** | 采集后作为输入；可作为初稿再人工收窄 | 需要真实负载 |
| 4 | **手写** | 最后手段 | 人工穷举，易漏，必须配守卫 |

**判定依赖是否自带（第一步永远先做这个）**：

```bash
unzip -l <dep>.jar | grep -iE 'META-INF/native-image|reachability-metadata|reflect-config|resource-config|jni-config'
```

登记的具体字段、精确签名的写法（**注册方法还是注册类名**、`allDeclared*` 与逐条 `methods` 的取舍、
`jniAccessible`、资源正则 vs 精确清单）见 [registration](references/registration.md)。

## 验证与守卫（五层，缺一不可）

| 层 | 作用 | 何时跑 | 失败意味着 |
|---|---|---|---|
| **单测断言** | 拦住「元数据被删 / 被改坏 / 生成器失效」 | 每次构建（Build） | 清单或生成结果不对 |
| **构建产物自证** | 在**打出来的产物**里再查一次（grep 元数据键、查启动标记） | 原生镜像工作流 | 打包过程中丢了元数据 |
| **CI 里真跑一次产物** | 让产物真的启动，断言**正面标记** | 原生镜像工作流（本仓库：Windows 腿） | 元数据齐全但程序仍然起不来 |
| **反向验证** | 临时删掉一条 → 确认守卫**真的会红** | 建立守卫时做一次 | 守卫是摆设 |
| **真机走查 / agent** | 每个已知反射点对应一个功能动作，逐个走 | 发版前 / 初版验收 | 还有未发现的缺口 |

AHA 的现状（可直接对照自己项目该有的样子）：

- **单测断言**：桌面端 `NativeImageMetadataTest` 16 个用例、CLI 4 个用例，按**族**组织
  （启动链 / 工具包 / Prism 着色器精确签名 / Jackson / FXML 装载链 / Windows 手势与拖放 /
  D3D 字段 / 无重复条目）；CLI 另有「picocli 生成结果覆盖命令与子命令」的用例。
- **产物自证**：工作流里一组 `require_meta <类名> "<理由>"` / `require_jni <类名> "<理由>"`，
  在**编出来的二进制**里对账（每条都带理由，失败时能直接看出「丢了谁、为什么在乎它」）；
  另有平台魔法数与体积下限。
- **CI 真跑**：两种形态各一套 —— **CLI 线在三平台**都执行产物并断言 `--version` 退出码 / 版本号与
  `--help` 退出码（issue #117 就是这么发现的）；**桌面线在 Windows 腿**启动 exe，断言**正面标记**
  （`[aha-version]`、`[ui-fxml-loaded]`）+ 「全零指纹」不得出现（`Windows version 0.0 build 0`）。
  **只断言"进程还活着"是不够的** —— 界面没装起来照样活着。
- **反向验证**：每次加守卫都删一条试红（见 [verification](references/verification.md) §3）。

两条纪律，都是踩出来的：

1. **守卫必须能证明自己会执行。** 曾经有个「失败标记」写进了 `catch` 分支，但那条分支在产物里
   根本达不到（守卫成了死代码）；也曾经只断言「进程存活 / 退出码」，于是「窗口没出来」被判成通过。
   **凡是守卫，都要问一句：它在真实失败路径上真的会被触发吗？**
2. **能用静态手段拦住的，不要留给真机。** FXML 的元素类型是否 import、实例化类型是否登记构造器、
   元数据是否有重复条目 —— 这些都是毫秒级静态对账能拦的，别攒到真机验收才发现。

详见 [verification](references/verification.md)。

## 反面模式（这些是打地鼠的成因）

- **只修报错里那个类**：反射常是链式的，修一个换一个。正确的单位是**一条链路 / 一类来源**。
- **把「注册了类」当成「注册了方法」**：`getMethod("m", A, B)` 要求精确签名；
  只写类名 + `allDeclaredMethods` 有时才行，有时不行——**运行期行为优先于直觉**。
- **把 JNI 当反射**：漏 `jniAccessible`，反射条目再多也没用。
- **资源只凭扩展名写正则**：一定要**对着源码的 `getResourceAsStream` 路径**逐项核
  （AHA 实测：桌面端正则漏了 `yaml` / `yml`，启动即 `CONFIG_NOT_FOUND`，不是构建失败）。
- **照抄同类工程的公开配置**：方法签名跨版本会变，类名相对稳定；抄完必须按自己版本的字节码核对。
- **把「姊妹工程的结论」直接搬过来**：别的工程复现了同族故障 ≠ 本仓库有同一缺口。
  先在本仓库找**调用点**，判清「查找在 Java 侧还是 native 侧」，再决定补 `reflection` 还是 `jni`
  （实测反例见 `TS-2610-033`）。
- **「值全零」当成渲染 / 驱动问题**：那是 native 回写字段失败的指纹，去补 `jni` 段的字段访问。
- **把「类型在册」当成「用途在册」**：反射**构造**要求单独登记构造器；
  `allDeclaredMethods` + `fields` 齐全，也拦不住一次 `getDeclaredConstructor()`。
- **资源只按扩展名写正则**：除了自己的资源，**JDK 自带的资源也要显式列**
  —— 有的是无扩展名的（`sun/text/resources/LineBreakIteratorData`、`.*\.icu$`）；
  漏了它，`--help` 这类功能会抛 `InternalError`，而构建全绿（issue #117）。
- **手工清单当成完整证明**：把"我查过"说成"没有了"。
- **有守卫却不反向验证**：等于没有守卫。

## 迭代日志（每次踩坑 / 复用追加一行）

| 日期 | 触发场景 | 结论（写到哪一节） | 来源 |
|---|---|---|---|
| 2026-10-08 | JavaFX 桌面端启动链式缺类（issue #26 → #35） | [discovery](references/discovery.md)：链式反射要按「启动链路」一次补齐；先查依赖自带的元数据与框架生成机制 | 实测（AHA：对 4878 个类全量 `javap`；`QuantumToolkit` 直接报错点） |
| 2026-10-08 | picocli CLI 反射面随注解变化 | [registration](references/registration.md)：**优先用框架的注解处理器生成**（`picocli-codegen`），手写清单会随子命令增删而漏 | 实测（AHA：生成 30 个类型，单测守卫生成结果） |
| 2026-10-08 | "自带元数据"的 JLine 仍缺 `Signals` | [catalog](references/catalog.md)：**自带 ≠ 无缺口**，终端库的 `sun.misc.Signal` 反射要单独补 | 实测（AHA：`javap org.jline.utils.Signals`） |
| 2026-10-08 | Jackson 3 不带 native-image 元数据，业务记录读配置即崩 | [registration](references/registration.md)：**业务层也要审计**，不只看框架；JSON/ORM 库常不带 | 实测（AHA：依赖 jar 扫描 + 记录注册） |
| 2026-10-08 | 资源正则漏 `yaml`/`yml` | [verification](references/verification.md)：资源清单要配「启动即读全部内置资源」的自检 | 实测（AHA：`AhaDefault.yaml` / `ModelDefault.yml`） |
| 2026-10-08 | 首次真跑 tracing agent（WSL + GraalVM 25.0.2） | [discovery](references/discovery.md) §2.4：实测采集 **400 个反射类型 + 55 条资源**；**关键动作是按包前缀分组过滤**（绝大多数是 log4j / JDK / JLine / sqlite，自带或自动处理），剩下才是要手写的；手写清单被交叉验证，agent 盲区靠静态审计补 | 实测（AHA：手写 14/19 命中；`AhaDefault.yaml` 等资源全被采到） |
| 2026-10-08 | 桌面端（GUI）也跑了一遍 agent | [agent-collection](references/agent-collection.md)：把采集制度化（自检/超时退路、多平台合并、按前缀过滤、输出独立存放）；**单平台会漏平台专属类**——Linux 采集出现 `GtkView`/`GtkWindow`/`GtkPixels`/`X11GLFactory`，手写清单里没有 | 实测（AHA：422 反射 + 69 资源；资源里有无扩展名的 `LineBreakIteratorData`、`.icu`、以 `.class` 形式读取的 `NativeLibLoader`） |
| 2026-10-13 | 原生包 OS / 驱动信息打印成 `Windows version 0.0 build 0`（issue #60） | **静默缺口①**：native 侧 `GetFieldID` 按名回写字段，字段没登记 ⇒ 拿不到成员 ID ⇒ 保持 Java 默认值（**值全零，不报错**）；`prism_d3d` 整段被「按 native 源码扫清单」漏掉 ⇒ 扫描要覆盖 `prism_d3d` / `prism_common` / `decora_sse` | 实测（`TS-2610-034`；改后驱动取到真实值 `igd10iumd64.dll 31.0.101.4502`） |
| 2026-10-13 | FXML 漏写 `<?import?>` ⇒ 原生包启动即 `LoadException: BorderPane is not a valid type.`，而构建 / 原生构建 / GUI 冒烟**三方全绿**（issue #133） | **静默缺口②**：类型在册 ≠ 用途在册（FXML 实例化走**反射构造**，`Region` 有 `allDeclaredMethods` 却没登记构造器）；另两条：**守卫必须能证明自己会执行**（失败标记曾在死分支里）、冒烟判据要从「存活 / 退出码」改成**正面标记** | 实测（`TS-2610-032`；新增 FXML 静态对账 + `FxmlViewChecks`） |
| 2026-10-13 | 姊妹工程（LegionFanControl）复现的同族缺口，在本仓库查不到（issue #125） | 三路发现法 A 新增「**先判查找发生在哪一侧**」：Java 侧按名查 → `reflection`；native 侧按名查 → `jni-config`。跨工程移植结论前先在本仓库找调用点 | 实测（`TS-2610-033`） |
| 2026-10-13 | Windows 手势 / 拖放类型要不要登记 | 登记了（类可达性已在每次开窗时验证过）；但**「手感」在本环境不可验收**——把「不可达」如实写成条目，而不是假装通过 | 实测（issue #125 / R12） |
| 2026-10-16 | CLI 原生镜像 `--help` 三平台一致退出码非 0（issue #117） | 资源清单要**显式列 JDK 自带的无扩展名资源**（`sun/text/resources/LineBreakIteratorData`、`.*\.icu$`）；「三平台一致失败」优先怀疑资源 / 逻辑，而不是平台差异 | 实测（`TS-2610-037` 同批；CLI 两份参数文件补齐） |
| 2026-10-16 | 原生镜像里打开「设置」面板必崩：`Unable to coerce UNAVAILABLE to class javafx.scene.control.TabPane$TabClosingPolicy`（PR #243） | **FXML 属性里的枚举值走 `BeanAdapter` 反射强转**，原生镜像缺该枚举常量元数据即抛。修法：**能用 Java 属性 API 表达的，就不要写在 FXML 里**；守卫：FXML 中不许出现「大写枚举值」属性 | 实测（PR #243；JVM / 单测 / GUI 冒烟三方都覆盖不到原生这条路径） |
| 2026-10-16 | 原生包一点「检查更新」必崩：`Record components not available for record class …`（PR #243） | **Jackson 反序列化 `record` 要读 record components**，`allDeclaredMethods` 不够；把业务层 21 个 record / enum 全量登记，并加**扫源码 ↔ 两份元数据文件**的静态守卫（反向注入验证过） | 实测（PR #243） |
| 2026-10-09 ~ 10-15 | 原生下主题 / CSS 一串退化（背景发黑、边框丢失、文字发虚）（issue #48/#49/#153/#229） | 归**编译技能**的「不崩但表现退化」一节：判据只能是**原生包真机观感**；缓解要可回退、决策要能打印出来 | 实测（`TS-2610-029` / `TS-2610-046` / `TS-2610-030`） |

## 验证状态（用这份技能前先看这里）

| 条目 | 状态 |
|---|---|
| 三路发现法（静态审计 / agent / 经验库） | **已在两个形态实测**（JavaFX 桌面端 + picocli CLI） |
| 「CI 里真跑产物」这一层 | **已落地**：CLI 线三平台跑 `--version` / `--help`（#117 即由此发现）；桌面线 Windows 腿跑 GUI 冒烟（断言正面标记 + 全零指纹） |
| 「先扫依赖是否自带元数据」+ 来源优先级 | **已实测**（JLine 自带但仍有缺口；Jackson 3 不带 ⇒ 业务层要自己登记） |
| 四层守卫（单测 / 产物自证 / 反向验证 / 真机） | **已实测并进 CI**；本仓库另加了第五层「CI 里真跑一次产物」 |
| 「值全零」这类静默缺口 | **已实测**（`TS-2610-034`：#60 修好且真机取到真实驱动值） |
| 「类型在册 ≠ 用途在册」 | **已实测**（`TS-2610-032`：FXML 反射构造） |
| FXML 属性里的枚举强转 | **已实测**（PR #243：设置面板必崩；修法 + 静态守卫） |
| Jackson 反序列化 `record` | **已实测**（PR #243：21 个 record / enum 登记 + 扫源码守卫） |
| 触摸 / 笔手势的「手感」 | **不可达**：本环境无触摸屏 / 数位板；只能验「类可达」，不能验功能（issue #125） |
| LCD 次像素渲染 | **不可达标**：原生下退化为灰度是既定差异，属渲染实现（见编译技能的「不崩但表现退化」） |

## 成熟判据（达到才算标杆参考）

1. 至少**两个形态不同**的真实目标照它走通（如 GUI + CLI，或手写 + 生成），且都有来源记录；
2. 四类清单里「推断待验证」条目清零，或明确保留为「实验位」；
3. 每条结论都带**来源**（哪次运行 / 哪条报错 / 哪个版本的工具链）；
4. tracer agent 这条路径被至少一个项目实际启用过（否则"完整"永远是口号）。

## 跨项目复用清单（搬过去要改哪几处）

1. **先跑 ①**：`unzip` 扫一遍所有依赖，把「自带元数据」的挑出来划掉，别手写；
2. **查 [catalog](references/catalog.md)**：命中的框架直接抄对应处理方式；
3. **再静态审计自己的代码**：`getResourceAsStream` 逐个列、`Class.forName` 逐个查；
4. **业务层别忘**：JSON / ORM / 配置绑定的 POJO 是手写重灾区（Jackson 3、snakeyaml-engine 都不带元数据）；
5. **配守卫**：单测 + 产物自证 + 反向验证，缺一不可；
6. **真机走查**：每个已知反射点 → 一个功能动作，跑一遍，没跑过的标"未验证"。

## 参考

- [discovery](references/discovery.md)：三路发现法的具体做法（静态审计脚本、agent 用法、盲区）
- [registration](references/registration.md)：元数据格式、来源优先级、精确签名、JNI、资源、初始化
- [verification](references/verification.md)：四层守卫、反向验证、诚实边界
- [catalog](references/catalog.md)：常见框架 / 依赖的已知元数据点与「是否自带」
- [agent-collection](references/agent-collection.md)：**tracing agent 采集制度**（何时必须跑、自检模式契约、多平台合并、过滤规则、CI 接入、实测坑）
- [scripts/collect-metadata.sh](scripts/collect-metadata.sh)：采集编排脚本（`run` / `summarize` / `filter`）
- 同源技能：[java-app-graalvm-native-image-compile](../java-app-graalvm-native-image-compile/SKILL.md)
  —— 本技能只管元数据这条纵向链路；构建、隔离、CI、参数调优、体积与启动对比在那个技能里。
