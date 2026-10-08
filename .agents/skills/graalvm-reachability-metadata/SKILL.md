---
name: graalvm-reachability-metadata
description: 为 GraalVM Native Image 系统性地发现、登记、验证「反射 / JNI / 文件资源 / 运行期初始化」四类可达性元数据，解决「构建成功、一启动就 ClassNotFoundException / MissingReflectionRegistrationError / 缺资源 / UnsatisfiedLinkError」的打地鼠问题。覆盖三条互补的发现路径（字节码静态审计、tracing agent 采集、经验库对照）、元数据来源优先级（依赖自带 > 框架生成 > 手写）、精确到方法签名的登记写法、以及「单测断言 + 构建产物自证 + 反向验证 + 真机走查」的多层守卫。当需要为原生镜像补 reachability-metadata.json / reflect-config.json / jni-config.json / resource-config.json，或产物在运行期报找不到类、找不到方法、找不到资源时使用。
license: Apache-2.0
compatibility: 适用于 GraalVM Native Image（JDK 21+；本技能条目在 GraalVM for JDK 25 上验证）。不涉及 cross-compile —— native-image 不能交叉编译，元数据虽与平台无关，但验证必须在目标平台做。
metadata:
  version: "0.1.0"
  owner: ACANX
  source-project: AHA（两个真实目标：JavaFX 桌面端、picocli+JLine CLI；覆盖链式反射、手写与生成、依赖自带元数据三种形态）
  maturity: 试验中（方法论已成型并两处复用；含标注为「推断待验证」的条目，达标判据见「成熟判据」一节）
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
  - 资源 `null`（`getResourceAsStream` 返回 `null`，随后 NPE 或业务异常，如「配置未找到」）
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

详见 [discovery](references/discovery.md)。

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

## 验证与守卫（四层，缺一不可）

| 层 | 作用 | 何时跑 | 失败意味着 |
|---|---|---|---|
| **单测断言** | 拦住「元数据被删 / 被改坏 / 生成器失效」 | 每次构建（Build/Gate） | 清单或生成结果不对 |
| **构建产物自证** | 在**打出来的产物**里再查一次元数据是否齐全 | 原生镜像工作流 | 打包过程中丢了元数据 |
| **反向验证** | 临时删掉一条 → 确认守卫**真的会红** | 建立守卫时做一次 | 守卫是摆设 |
| **真机走查 / agent** | 每个已知反射点对应一个功能动作，逐个走 | 发版前 / 初版验收 | 还有未发现的缺口 |

**最关键的一层是反向验证**：只读配置只能证明"应该会拦"，让它失败一次才能证明"真的会拦"。

详见 [verification](references/verification.md)。

## 反面模式（这些是打地鼠的成因）

- **只修报错里那个类**：反射常是链式的，修一个换一个。正确的单位是**一条链路 / 一类来源**。
- **把「注册了类」当成「注册了方法」**：`getMethod("m", A, B)` 要求精确签名；
  只写类名 + `allDeclaredMethods` 有时才行，有时不行——**运行期行为优先于直觉**。
- **把 JNI 当反射**：漏 `jniAccessible`，反射条目再多也没用。
- **资源只凭扩展名写正则**：一定要**对着源码的 `getResourceAsStream` 路径**逐项核
  （AHA 实测：桌面端正则漏了 `yaml` / `yml`，启动即 `CONFIG_NOT_FOUND`，不是构建失败）。
- **照抄同类工程的公开配置**：方法签名跨版本会变，类名相对稳定；抄完必须按自己版本的字节码核对。
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
- 同源技能：[java-app-graalvm-native-image-compile](../java-app-graalvm-native-image-compile/SKILL.md)
  —— 本技能只管元数据这条纵向链路；构建、隔离、CI、参数调优、体积与启动对比在那个技能里。
