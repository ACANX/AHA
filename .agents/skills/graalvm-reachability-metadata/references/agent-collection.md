# tracing agent 采集制度（把「跑一次」变成「可重复的标准步骤」）

[discovery](discovery.md) 把 agent 列为三条发现路径之一。**本文把它制度化**：
什么条件下必须跑、怎么组织命令、输出放哪里、怎么过滤、怎么与手写清单合并、
怎么接进 CI。目标是让任何一个项目都能**重复地**产出同一份采集结果，
而不是「某次有人手工跑过一遍」。

> **为什么值得制度化**：手工维护的元数据清单只能做到「已知缺口已闭」。
> agent 是唯一能逼近「我没想到的也查到了」的手段——但如果它只是某次临时操作，
> 下次依赖升级、走查路径变了，就再也没人跑，清单又开始腐烂。

## 1. 什么时候**必须**跑

| 触发条件 | 原因 |
|---|---|
| 首次为该项目引入原生镜像 | 建基线，避免从零手写 |
| 新增 / 升级一个**带反射**的依赖（JSON、ORM、CLI、终端、DI、脚本） | 反射面随版本变 |
| 框架大版本升级（JavaFX / Spring / …） | 内部反射点会变 |
| 元数据清单里还有「未验证」条目 | agent 是把它变「已验证」的最快路径 |
| 新增一个功能路径（新子命令、新界面） | 新路径引入新反射点 |

**不需要每次构建都跑**：它是「基线 / 升级 / 验收」动作，不是日常动作。

## 2. 两个前置条件

### 2.1 GraalVM（agent 库）

agent 不是 JDK 自带的，随 **GraalVM** 发布：`$JAVA_HOME/lib/libnative-image-agent.so`。
确认：

```bash
ls "$JAVA_HOME/lib" | grep native-image-agent   # 有 → 可直接用
```

普通 HotSpot JDK **没有**这个库。项目若平时用普通 JDK，采集时临时切 GraalVM 即可
（两者都是 JDK 25，跑出来的反射点一致）。

### 2.2 能「启动 → 走到路径 → 自动退出」

agent **在 JVM 退出时**才把采集结果落盘（shutdown hook）。所以关键是让程序能正常退出：

**首选：给程序加一个自检模式（collector mode）** —— 这是制度化的关键使能项。

```
契约（建议统一成这个形态）：
  * 触发：环境变量 <APP>_SELF_CHECK=1（或 --self-check 参数；环境变量对 GUI / 双击场景更友好）
  * 行为：顺序走一遍主干路径，每步记录成功 / 失败
  * 结束：全部走完（或失败点）后主动 exit(0)；不要只开窗口等人
  * 输出：可选写一份 self-check 报告，便于 CI 与用户诊断
主干路径至少覆盖：
  读配置 → 写配置 → 建会话 → 加载插件 / 工具 / 服务 → 一次真实网络调用（含 TLS）
  → 交互 / 终端探测 → 退出
```

自检模式的价值不止 agent：它同时是**真机验收**和**CI 冒烟**的负载。

**退路（不便改代码时）**：用超时命令发 SIGTERM，触发 shutdown hook：

```bash
timeout -s TERM -k 10 30 "$JAVA" <正常启动命令>   # 启动 30s 后终止 → agent 落盘
```

实测（AHA）：GUI 程序在 WSLg 下用这条退路成功采集到 422 个反射类型 + 69 条资源。

**GUI 需要显示环境**：Linux 用 `xvfb-run` 或 WSLg（`DISPLAY`）；否则 JavaFX / Swing 起不来，
采不到界面相关反射。**CLI 不需要显示环境**。

## 3. 标准采集流程

### 3.1 先写「命令清单」（把路径走全）

采集质量取决于**跑到多少路径**。先列出命令 / 操作，逐条跑：

```bash
# CLI 形态
--version  --help  init  config show  provider list  tool list  secret list  extension list
printf '/exit\n' | app chat        # 交互式：管道喂养退出命令

# GUI 形态：用自检模式（或超时退路）走 启动 → 各界面 → 退出
```

**这一步不能省**：只跑一个命令，agent 就只覆盖那一条路径。

### 3.2 采集：首次 output，后续 merge

```bash
AGENT_LIB="$JAVA_HOME/lib/libnative-image-agent.so"     # 或 -agentlib:
OUT=/tmp/reachability

# 第一次：建目录
"$JAVA" -agentlib:native-image-agent=config-output-dir="$OUT" <正常启动命令> <第一条路径>

# 后续每次：合并
"$JAVA" -agentlib:native-image-agent=config-merge-dir="$OUT"  <正常启动命令> <下一条路径>
```

产出（新版统一为一份）：`reachability-metadata.json`（含 `reflection` / `resources` /
`jni` / `proxy` / `serialization` / `foreign` 各段）。

### 3.3 多平台：每平台各跑一次，合并到同一目录

元数据里含**平台专属**类（GUI 的 `GtkView` / `WinWindow` / `MacView`，字体后端，
原生库路径）与平台专属资源（`.so` / `.dylib` / `.dll`）。
**在哪个平台跑，就只采到哪个平台的。**

做法：

1. 每个目标平台各跑一遍 §3.2（同一 `$OUT` 语义）；
2. 把各平台产物拷到一起，用 `config-merge-dir` 再并一次（或直接按类型求并集）；
3. 合并后**缺席平台只产生 warning，不构建失败**，所以并集是安全的。

> **AHA 实测**：Linux 采集出现 `com.sun.glass.ui.gtk.GtkView` / `GtkWindow` / `GtkPixels` /
> `com.sun.prism.es2.X11GLFactory` —— 手写清单只有三平台 `*PlatformFactory`，没有这些实现类。
> **只在单平台跑，就永远发现不了其他平台缺什么。**

### 3.4 输出放在哪：**独立目录，不直接进 classpath**

```text
src/native/agent/reachability-metadata.json     # 采集产物（对账材料 + 合并来源）
src/main/resources/META-INF/native-image/.../reachability-metadata.json   # 人工审核后的正式清单
```

**不要把 agent 输出直接放进 `src/main/resources/META-INF/native-image/`**：

- 它每次重跑都会被覆盖，会把人工补充的精确条目冲掉；
- 它含大量依赖自带 / JDK 内部的噪声条目（见 §3.5），直接进 classpath 既臃肿又难解释；
- 正式清单应当**可审、可解释、每条有理由**。

采集产物进仓库时放在 `src/native/agent/`（或 CI 制品），作为「这次采到了什么」的证据。

### 3.5 过滤：**先按包前缀分组，再决定**

agent 输出动辄几百条，逐条看是浪费。先分组计数：

```bash
bash scripts/collect-metadata.sh summarize /tmp/reachability/reachability-metadata.json
```

然后用 `filter` 剔除**依赖自带元数据**与**纯 JDK 内部**：

```bash
bash scripts/collect-metadata.sh filter /tmp/reachability/reachability-metadata.json \
     org.apache.logging.log4j org.sqlite org.jline java. javax. sun. com.sun.crypto com.sun.security \
     > /tmp/reachability/filtered.json
```

判据：

| 类别 | 处理 |
|---|---|
| 依赖自带 `META-INF/native-image/`（log4j / sqlite / JLine…） | **剔除**（重复登记是噪声） |
| JDK 内部（`java.*` / `sun.*` / `javax.*` / `com.sun.crypto` / `com.sun.security`） | 通常**剔除**（GraalVM 自动处理） |
| 应用自身技术栈（框架运行时、业务类、GUI 工具包） | **保留** |
| 探测式反射（`Class.forName` 试探一个**不存在**的可选依赖，如 `com.fasterxml.jackson.databind.ObjectMapper`） | 一般**剔除**（调用方已捕获异常） |
| 数组类型（`boolean[]`、`Xxx[]`） | **保留**（JNI / 反射会用到） |

**实测数字（AHA 桌面端）**：422 条里 248 条 log4j + 68 条 JDK + 10 条 sqlite → 只留约 47 条应用栈条目。

### 3.6 与手写清单合并

```
最终清单 = 人工审核过的（手写 / 框架生成 / 依赖自带）∪ agent 采集（过滤后）
```

- **手写条目优先保留**（往往精确到签名、带「为什么」）；
- agent 条目补齐手写没覆盖的（框架运行期反射、平台实现类）；
- 合并后仍要过 §4 的守卫。

## 4. 与守卫的衔接（采完不是结束）

1. **单测**：把新增的关键条目写进断言（见 [verification](verification.md)）；
2. **产物自证**：在原生镜像工作流里查构建产物中的元数据；
3. **反向验证**：删一条 → 测试必须红；
4. **标注状态**：未被 agent 采到的条目（`sun.misc.Signal` 这类）标「静态审计得出、未验证」，
   **不要因为 agent 没采到就删掉**——agent 只覆盖「跑到的路径」。

## 5. 接进 CI（可选腿）

把采集做成**可选、非阻塞**的一条腿（与构建腿同一套隔离约定）：

```
输入：能跑的 JVM 产物 + 自检模式（或超时退路）
步骤：装 GraalVM → 按命令清单跑 agent → 过滤 → 与现有清单做差集 → 产出「采集报告」
产物：reachability-metadata.json（原始）+ filtered.json + 差集报告
性质：数据点，不是交付物；失败不挡合并
```

**差集报告才是 CI 里最值钱的部分**：它直接回答「这一版比上一版多了哪些反射点」，
人一眼就能看出「是不是新依赖 / 新路径引入的」。

## 6. 常见坑（实测汇总）

| 坑 | 症状 | 处置 |
|---|---|---|
| 进程不退出 | agent 没落盘、目录空 | 加自检模式，或 `timeout -s TERM` |
| 没跑全路径 | 采到的条目远少于预期 | 先写命令清单，逐条跑 |
| 只用单平台 | 其他平台缺专属类（Gtk/Win/Mac） | 每平台各跑一次再合并 |
| 结果直接进 classpath | 覆盖人工条目、臃肿 | 放独立目录，人工审核后合并 |
| 没过滤 | 几百条噪声，无法审 | 按前缀分组 + `filter` |
| 把 agent 当完整证明 | 未执行路径永远采不到 | 与静态审计、真机走查组合 |
| 交互式程序卡住 | 采集不到会话相关反射 | 管道喂养退出命令（`printf '/exit\n' \| app chat`） |
| 资源清单只按扩展名 | 无扩展名 / 特殊扩展漏掉（`LineBreakIteratorData`、`.icu`、`.class`） | 以 agent 的 `resources` 段为准逐项核对（见下） |

**资源段的最后一公里**：agent 会列出**实际读过的每个资源**（含无扩展名 / 特殊扩展 / 以 `.class` 形式读的）。
把它与 `-H:IncludeResources` 或 `resource-config.json` 逐项对齐——
**正则匹配不到的就是运行期会缺的**（如 JDK 的断行数据、JavaFX 自带的 `.class` 探测）。
