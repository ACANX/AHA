# 日志设计

**文档版本**：v1.0.0
**状态**：冻结
**生效日期**：2026-10-07
**最后更新**：2026-10-07
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-07 | 初始版本：技术选型、程序化装配、分级与分流、文件命名与切分规则、已知限制、测试与验收 | @ACANX |

---

## 0. 定位与读者

本文记录**日志子系统的设计决策与取舍**，包括为什么这样做、踩过哪些坑、哪些做法被否决。

- 配置项清单与默认值 → [ConfigurationGuide.md](../Guide/ConfigurationGuide.md) §11
- 各配置键的完整参考 → [ReferenceGuide.md](../Guide/ReferenceGuide.md)
- 排查步骤 → [TroubleshootingGuide.md](../Guide/TroubleshootingGuide.md)

---

## 1. 目标与边界

| 目标 | 说明 |
|---|---|
| 可替换后端 | 业务代码只用 SLF4J API，换掉 Log4j2 不需要改业务模块 |
| 不干扰对话 | 日志与对话输出**分流**，stdout 专供对话内容 |
| 可定位 | 每条日志带**完整类名 + 方法名 + 行号** |
| 自动切分 | 单文件超阈值即切分，便于长期运行与事后取证 |
| 不阻断启动 | 日志装配失败只降级、绝不让 CLI 起不来 |

**不在范围内**：日志上报/集中采集、结构化（JSON）日志、审计日志的合规留存。

---

## 2. 技术选型

| 层 | 选择 | 位置 |
|---|---|---|
| API | SLF4J（`org.slf4j:slf4j-api`） | 各模块均可依赖 |
| 后端 | Log4j2（`log4j-slf4j2-impl` + `log4j-core`） | **只在 `aha-cli`** |

**依赖方向**：`aha-core` 等模块只依赖 `slf4j-api`，**不绑定具体实现**；`aha-cli` 是组合根，
显式引入 Log4j2 后端并在启动时装配。这样 Core 可脱离 CLI 被桌面端（0.2）或远程运行时复用，
不必继承 CLI 的日志实现。

`aha-cli/module-info.java` 因此需要 `requires org.slf4j;`、`requires log4j;`、`requires log4j.core;`。

---

## 3. 装配方式：程序化配置

### 3.1 为什么不用 `log4j2.xml`

JPMS 下 Log4j2 通过 `ClassLoader.getResources` 查找配置文件，而该方法**不搜索模块路径**，
jar 内的 `log4j2.xml` 可能根本不被发现——症状是「配置文件明明在，却不生效」。
因此改为在 CLI 启动时**按解析后的配置值程序化生成** Log4j2 配置。
项目内不再保留 `log4j2.xml`（历史上曾有一份，已移除）。

### 3.2 启动顺序

```java
// AhaCli.main
AhaConfig loaded = loadConfig();                                  // ① 读配置
LoggingSetup.apply(loaded == null ? null : loaded.logging());     // ② 装配日志
installSecretResolver(loaded);                                    // ③ 密钥库
CliContext.preload(loaded);                                       // ④ 复用配置
new CommandLine(new AhaCli()).execute(args);                       // ⑤ 交给 picocli
```

`apply` 带一次性开关（`APPLIED`），重复调用只生效一次。

### 3.3 必须配置「当前正在使用的」LoggerContext（关键坑）

**这是本子系统踩过的最隐蔽的坑**：装配代码看起来完全正确，日志却长期不落盘、且**无任何异常**。

原先写法：

```java
Configurator.initialize(null, new ConfigurationSource(...));   // ✗ 看起来像 initialize(String, ConfigurationSource)
```

它实际编译到的是 `Configurator.initialize(ClassLoader, ConfigurationSource)` 这一重载——
配置的是「按给定类加载器查到的」context，而 `LogManager.getContext(false)` 返回、
logger 实际绑定的那个**并不是同一个实例**。配置落到了别处，于是静默失效。

正确做法是显式取当前 context 再 `start` 新配置：

```java
static void install(LoggerContext context, String xml) throws IOException {
    ConfigurationSource source = new ConfigurationSource(
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    context.start(new XmlConfiguration(context, source));
}
// apply: install((LoggerContext) LogManager.getContext(false), xml(level, file))
```

### 3.4 「必须在首个 logger 之前调用」并不成立

`ConfigLoader` 自身有 4 处 `INFO`（「已加载内置默认配置」「已加载主配置」「已加载模型配置」），
因此读配置时 Log4j2 **已经**按默认配置（仅 console、ERROR）初始化过一次。
这不影响正确性——`install` 是**替换**该 context 的配置，而不是指望它尚未创建。

**代价**：那几行发生在 `apply` 之前，**只出现在控制台，不会进日志文件**（见 §7）。

---

## 4. 分级与分流

| Appender | 目标 | 级别 | 说明 |
|---|---|---|---|
| File | `Logging.File` | `Logging.Level` | 完整记录 |
| Console | **stderr** | 固定 `ERROR` | 不干扰对话输出 |

**为什么 Console 是 stderr 且只输出 ERROR**：CLI 是交互式的，stdout 专供对话内容与
提示符重绘；若把 INFO/WARN 写进 stdout，会打断流式输出与输入行。级别常量
`CONSOLE_LEVEL` 在 `LoggingSetup` 中集中定义。

**为什么预期业务结果记 `INFO` 而非 `WARN`**：用户拒绝工具授权、参数不合法等属于
**预期内的业务结果**，不是故障。它们记 `INFO`，既能进日志文件供追溯，
又不会因为 Console 的 ERROR 阈值而打断对话。

**内置级别默认 `DEBUG`**：`AhaDefault.yaml` 中 `Aha.Logging.Level` 的默认值是 `DEBUG`，
**开发阶段有意如此**——需要看到完整的工具参数、输出长度与装配过程，日志文件同时是
主要的排障依据。用户级 `Aha.yaml` 可随时覆盖为 `INFO`。

> 注意区分两个「默认」：`AhaDefault.yaml` 的 `DEBUG` 是**内置默认值**（未经用户配置时的取值）；
> `LoggingSetup.DEFAULT_LEVEL = "INFO"` 是**兜底**——仅当配置缺失或级别名非法（如 `VERBOSE`）时使用。

---

## 5. 文件命名与切分

| 项 | 规则 |
|---|---|
| 主文件 | `AHA.log` |
| 位置 | `Logging.File`，默认 `${AHA_HOME:-~/.aha}/Log/AHA.log` |
| 切分阈值 | 单文件超过 **10 MB** |
| 切分文件 | `<基名>-yyyy-MM-dd-NN.log`，如 `AHA-2026-10-07-01.log` |
| 序号 | `NN` **补零**两位，按天从 `01` 递增 |
| 压缩 | **不压缩** |
| 历史清理 | **不自动清理** |

切分文件与主文件同目录；`yyyy-MM-dd` 取**切分发生时刻**的日期（非记录时间），
序号按天独立编号。

### 5.1 两个必须知道的实现细节

**① 补零必须写 `%02i`**。写成 `%i` 会得到 `AHA-2026-10-07-1.log`（个位数不补零）。实测对比：

| `filePattern` 中的写法 | 实际文件名 |
|---|---|
| `%i` | `AHA-2026-10-07-1.log`、`-2.log` ✗ |
| `%02i` | `AHA-2026-10-07-01.log`、`-02.log` ✓ |

**② 为什么不压缩**：压缩包无法直接用编辑器打开，排查问题时反而多一步解压。
日志文件本身有 10 MB 上限，占用可控。

### 5.2 取舍：序号递增 vs 保留上限

Log4j2 的 `DefaultRolloverStrategy` 达到 `max` 后，会**删除最旧的切分文件并复用它的序号**——
序号会「回到 01」而不是继续递增。**两者不可兼得**：

| 选择 | 后果 |
|---|---|
| `max` 小（如 5） | 占用可控，但序号回绕、文件被覆盖 ✗ |
| **`max` 大到用不满（当前取 1000）** | **序号只增不回绕** ✓，代价是历史文件不自动清理 |

本项目按「序号递增」优先，故取 `max=1000`。实测 `max=3` 滚 17 次后只剩 `-01/-02/-03`，
序号完全不递增 ✗。

**若日后要限制占用**，改用按时间删除（`Delete` + `IfLastModified age="30d"`）即可——
那种方式序号仍能递增，且占用可控。

---

## 6. 日志行格式

```
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%t] %logger.%method:%line - %msg%n
```

`%method` / `%line` 需要遍历栈获取调用者信息，有性能开销；已确认在高频路径上可接受，
换来的是「一眼看到调用点」。早期使用 `%logger{1.}` 只输出每包首字母缩写，根本无法定位，
已弃用。

---

## 7. 已知限制

| 限制 | 说明 | 处理 |
|---|---|---|
| 配置加载日志不进文件 | 4 行「已加载 xx 配置」发生在 `apply` 之前 | 代码注释说明；如需入库需两阶段装配 |
| 历史文件不自动清理 | §5.2 的直接后果 | 记入 [PLAN.md](../PLAN.md) §8.6，可改按时间删除 |
| 未做日志脱敏扫描 | 依赖「不记录敏感信息」的编码约定 | 见 [CodingStandard.md](../DevSpec/CodingStandard.md) |

---

## 8. 源码索引

| 文件 | 职责 |
|---|---|
| `aha-cli/.../cli/LoggingSetup.java` | 装配：级别/路径解析、XML 生成、`install` 内核 |
| `aha-core/.../config/LoggingConfig.java` | 配置记录（`Level` / `File`） |
| `aha-core/src/main/resources/AhaDefault.yaml` | 默认值（`Aha.Logging`） |
| `aha-cli/.../cli/session/ConfigView.java` | `/config` 展示 `Logging.Level` / `Logging.File` |

---

## 9. 测试与验收

| 测试 | 钉住什么 |
|---|---|
| `LoggingSetupTest#defaultsToInfoWhenUnset` 等 | 级别解析与非法值回退 |
| `LoggingSetupTest#expandsTildeInConfiguredPath` | `~` 展开、路径转绝对 |
| `LoggingSetupTest#buildConfigWithFileContainsRollingFileAppender` | XML 含 File appender、切分命名契约、不压缩 |
| `LoggingSetupTest#installWritesLogFileToDisk` | **日志真的落盘**（真写文件、真读回、断言标记文本） |
| `LoggingSetupTest#rolledPathInsertsDateAndPaddedIndexBeforeExtension` | 命名推导（含无扩展名、其它扩展名边界） |
| `LoggingSetupTest#rollsIntoNamesWithPaddedIncrementingIndex` | **真滚动**：2 KB 阈值滚 300 行，序号从 `01` 递增且补零 |

**验收要求**：

1. 日志装配的**落盘行为必须有测试**，不能只测纯函数（`xml` 字符串、路径解析）——
   本子系统正是因为「只测纯函数、没有任何测试调用 `apply`」，让 §3.3 的坑长期未被发现。
2. **平台相关行为必须在目标平台验证**：反斜杠路径下的 `filePattern` 只在 Windows 上真机验证过才作数
   （见 [TestingSpec.md](../DevSpec/TestingSpec.md) §5）。

---

## 10. 相关文档

| 文档 | 关系 |
|---|---|
| [ArchitectureOverview.md](ArchitectureOverview.md) | 模块依赖方向 |
| [CLIDesign.md](CLIDesign.md) | CLI 组合根与终端交互 |
| [TUIDesign.md](TUIDesign.md) | stdout 专供对话的版面约束 |
| [SecurityDesign.md](SecurityDesign.md) | 密钥库、密钥不得入日志 |
| [CodingStandard.md](../DevSpec/CodingStandard.md) | 日志编码约定 |
| [ConfigurationGuide.md](../Guide/ConfigurationGuide.md) | `Aha.Logging` 配置与切分规则 |
| [TroubleshootingGuide.md](../Guide/TroubleshootingGuide.md) | 日志相关排查 |
| [PLAN.md](../PLAN.md) | 遗留取舍与待办 |
