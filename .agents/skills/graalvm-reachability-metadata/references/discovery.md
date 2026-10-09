# 发现：怎么把「散落在各处的可达性依赖」找出来

发现是整条链路的地基。**漏发现 = 后面登记得再漂亮也没用。**
本文给三条互补路径，各自能发现什么、盲区在哪、什么时候用。

## 0. 先做的一件事：扫依赖是否自带元数据

在写任何 JSON、跑任何 agent 之前，先问：「这个依赖是不是已经自己声明了？」

```bash
for j in lib/*.jar; do
  hits=$(unzip -l "$j" 2>/dev/null | grep -icE 'META-INF/native-image|reachability-metadata|reflect-config|resource-config|jni-config|native-image\.properties')
  [ "$hits" -gt 0 ] && echo "自带元数据: $(basename "$j") ($hits 项)"
done
```

- **有** → 直接用，最多确认版本对得上；**不要**再手写一遍（会重复，且手写往往不如上游全）。
- **没有** → 进入静态审计 / agent / 经验库三条路。

> **实测结论（AHA）**：`sqlite-jdbc`、`log4j-core/api`、`JLine 4` **自带**；
> `Jackson 3`、`snakeyaml-engine`、`picocli 4.7.7`、`JavaFX 25` **不带**。
> 详见 [catalog](catalog.md)。

## 1. 静态审计：从字节码与源码反推

适合：没有显示环境、不方便跑真实负载、审计第三方框架、以及"先建基线再真机验证"。

### 1.1 找反射点（字节码层）

```bash
# 对框架包做全量反编译（别抽样；实测成本可接受）
find ~/.m2 -name 'foo-*.jar' -exec sh -c 'unzip -o -q "$1" -d /tmp/scan' _ {} \;
cd /tmp/scan && find . -name '*.class' | while read -r c; do
  javap -p -c "$c" 2>/dev/null | grep -qE 'Class\.forName|getDeclaredConstructor|getDeclaredMethod|getMethod|getField|MethodHandles' \
    && echo "反射点: $c"
done
```

要重点关注的反射 API：

| API | 常见于 |
|---|---|
| `Class.forName(String)` | 框架的插件 / 扩展 / 平台实现选择 |
| `Class.getDeclaredConstructor(...).newInstance()` | 工厂、SPI 实现实例化 |
| `getDeclaredMethod` / `getMethod` / `invoke` | 方法调用、代理、序列化 |
| `getDeclaredField` / `getField` | 依赖注入、序列化、字段拷贝 |
| `MethodHandles.Lookup` + `unreflect...` | 高性能反射、记录类访问 |
| `ServiceLoader.load` | SPI（GraalVM 对 `META-INF/services` 有专门支持，但**服务实现类**要可达） |
| `Proxy.newProxyInstance` | 动态代理（需 `proxy-config`） |

**动态拼出来的类名**是最容易漏的。形如：

```java
Class.forName("com.example." + name + ".Impl");
```

字节码里 `ldc` 只有片段，看不到完整类名。做法：

1. 用 `javap -v` 看 `BootstrapMethods` 里的 `makeConcatWithConstants` 配方，
   还原字符串模板（例如 `com.sun.prism.shader.<name>_Loader`）；
2. 再用另一路证据（目录里实际存在哪些 `*_Loader` 类）把候选集补全，
   登记**全部候选**（缺席只产生无害警告）而不是赌一个。

### 1.2 找资源读取点（源码层）

```bash
grep -rn "getResourceAsStream\|getResource(" src/
```

**关键：把完整路径写下来逐项核对**，不要凭扩展名直觉。

- `SomeClass.class.getResourceAsStream("x.properties")` 是**相对于该类的包路径**，
  真正路径是 `com/example/pkg/x.properties`；
- `getResourceAsStream("/x.yaml")` 是**类路径根**；
- `ClassLoader.getResourceAsStream("x.yaml")` 总是根（不带前导 `/`）。

把每个读取点的实际路径列成表，再确认资源清单（`-H:IncludeResources` 或
`resource-config.json`）能匹配到。**AHA 实测**：桌面端正则里有 `properties|txt|json`，
却漏了 `yaml` / `yml`，而 `ConfigLoader` 读的是 `/AhaDefault.yaml` ——
症状是**启动即 `CONFIG_NOT_FOUND`**，不是构建失败。

### 1.3 找 JNI 点

```bash
javap -p -c <class>.class | grep -E 'loadLibrary|load\(|registerNatives|native '
```

JNI 有两类，**都要登记**：

1. **Java 调用原生库**：`System.loadLibrary(...)` 加载的库必须进资源（`.so`/`.dylib`/`.dll`）；
2. **原生库回调 Java**：原生代码按名字查找 Java 方法 / 字段。
   这些类要标 `jniAccessible`（见 [registration](registration.md)），
   **光有反射条目不够**。

识别"会被原生回调"的类：看它是否有 `native` 方法、是否成组出现结构体风格字段
（`SIZEOF`、`sizeof`、`ws_row`…）、是否是某个原生绑定的 DTO。

### 1.4 扫 `META-INF/services`

```bash
find . -path '*/META-INF/services/*' -type f | while read -r f; do echo "== $f"; cat "$f"; done
```

GraalVM 对 `META-INF/services` 有**内建**支持，但两个前提常被忽略：

- 资源本身要进镜像（`-H:IncludeResources=META-INF/services/.*`）；
- 文件里列出的**实现类要可达**（被显式注册，或在某处被静态 new）。

### 盲区

- 条件分支才走的路径发现不了；
- 业务代码里"以后才用到"的反射发现不了；
- `MethodHandles` 动态构造的调用点很难静态还原。
- **结论：静态审计只能证明"我查过这些"，不能证明"不存在别的"。**

## 2. tracing agent：让真实运行自己报告

> **完整制度**（何时必须跑、自检模式契约、多平台合并、过滤规则、CI 接入）见
> [agent-collection](agent-collection.md)；本节是速览与实测记录。

最接近“完整”的一路。JVM 正常跑一遍,agent 把发生过的反射 / 资源 / JNI / 代理 / 序列化记下来。

### 2.1 最小用法

```bash
# 单次
java -agentlib:native-image-agent=config-output-dir=native-config -jar app.jar ...

# 多次运行合并（不同功能各跑一遍，这是推荐做法）
java -agentlib:native-image-agent=config-merge-dir=native-config -jar app.jar --mode=a
java -agentlib:native-image-agent=config-merge-dir=native-config -jar app.jar --mode=b
```

产物（GraalVM 新版统一为一份）：`reachability-metadata.json`
（旧版会拆成 `reflect-config.json` / `resource-config.json` / `jni-config.json` / `proxy-config.json` / `serialization-config.json`）。

### 2.2 怎么"跑到全部路径"

这是 agent 真正的难点，不是命令本身：

- **CLI**：把每个子命令、`--help`、交互式会话、错误路径都跑一遍；用脚本驱动。
- **GUI**：需要显示环境（Linux 用 `xvfb-run`）；更要命的是**进程必须正常退出**
  （agent 在关机钩子里写文件）。给程序加一个"自检 N 秒后自动退出"的开关最省事，
  这个开关对真机验收同样有用。
- **服务端**：发一轮代表性请求（含冷启动、首次懒加载、异常分支）。

> **AHA 经验**：把"启动 → 读配置 → 存配置 → 一轮对话 → 退出"做成一个自检模式，
> 一条命令就能让 agent 覆盖主干路径；比手工点一遍可靠得多。

### 2.3 注意事项

- agent 输出是**参考不是终稿**：没跑到的路径不会出现，仍需静态审计补；
- agent 会把**反射到的类名**记下来，但不一定记全方法签名（取决于调用方式），
  关键条目要回看是否精确；
- 采集现场最好与构建环境一致（依赖版本一致）；版本漂移后要重采。

### 盲区

未执行到的代码路径 = 未采集。**“agent 没报” ≠ “没有缺口”。**

### 2.4 实跑经验（AHA 首次采集，2026-10-08）

命令序列（关键是**把命令类型跑全**，不要只跑一个）：

```bash
# 首次用 config-output-dir，后续全部用 config-merge-dir 合并
AGENT="-agentlib:native-image-agent=config-merge-dir=$OUT"
java $AGENT -jar app.jar --version      # 版本（会读配置）
java $AGENT -jar app.jar --help         # 触发子命令注册
java $AGENT -jar app.jar init           # 初始化 / 写配置
java $AGENT -jar app.jar config show    # 读配置（JSON/YAML 反序列化）
java $AGENT -jar app.jar provider list  # 读内置预设
java $AGENT -jar app.jar tool list      # ServiceLoader
java $AGENT -jar app.jar secret list    # 安全存储 / 加密
# 交互式：用管道喂退出命令，非 TTY 也能跑完
printf '/exit\n' | java $AGENT -jar app.jar chat
```

**实测数字**（一次跑完上面的序列）：编译采集到 **400 个唯一反射类型 + 55 条资源**。

**最重要的操作：按包前缀分组，再逐组决定要不要手写。**

```bash
python3 - <<'PY'
import json, collections
ag = json.load(open('reachability-metadata.json'))
buckets = collections.Counter(t['type'].split('.')[0] + '.' + (t['type'].split('.')[1] if '.' in t['type'] else '')
                              for t in ag.get('reflection', []))
for k, v in buckets.most_common(20):
    print(f"{v:5d}  {k}")
PY
```

AHA 实测分组：`org.apache.logging.log4j.core` 100+（log4j **自带**元数据）、
`sun.security` / `com.sun.crypto` / `java.time` / `java.sql`（JDK 内部，大多自动处理）、
`org.jline` + `org.sqlite`（依赖**自带**）。**真正需要项目手写的只有几十条。**

**三条实测结论：**

1. **交叉验证手写清单**：手写的 13 个配置记录 + `ProcessBuilder$RedirectPipeImpl`
   **被 agent 全部独立采到** → 说明这些登记确实需要；
   而 `sun.misc.Signal` / `TaskRequest` 等** agent 没采到**（没走到那条路径）→
   说明**静态审计确实能补 agent 的盲区**，三路互补不是口号。
2. **资源清单被反证**：`AhaDefault.yaml` / `ModelDefault.yml` /
   `com/.../version.properties` / `META-INF/services/*` / `*.so` 全部被 agent 采到 →
   证实「资源正则需要 `yaml|yml|properties|so`」这条结论是真实需求。
3. **ServiceLoader 实现类**：agent 采到 6 个业务 provider（LLM 适配器 / 工具 Provider）。
   GraalVM 对 `META-INF/services` 有**内建支持**（会自动把 provider 注册为可达），
   前提是服务文件本身在镜像里（资源清单覆盖 `META-INF/services/*`）。因此**通常无需手写**；
   若真机仍失败，再补。

> 另：agent 会记录**探测式反射**（`Class.forName` 试试可选依赖，如
> `com.fasterxml.jackson.databind.ObjectMapper`）——这些类可能根本不在 classpath，
> 业务代码已捕获异常，**一般无害**；不要因为 agent 采到它们就无脑登记。

## 3. 经验库对照：先查表，再动手

常见框架的已知元数据点在 [catalog](catalog.md)。

**先查表有两个好处**：

1. 很多坑别人踩过，能直接得到"该登记什么、该怎么登记"；
2. 更重要的是知道**"该框架是否自带元数据"** —— 自带就别手写。

**但不要照抄某个项目的公开配置**：抄配置会把对方的错误一起抄过来。
**AHA 实测**：某参考配置把 `loadShader` 写成两参，实际是三参 `(ShaderFactory, String, InputStream)`；
照抄会让 `getMethod` 在运行期抛 `MissingReflectionRegistrationError`。
**抄结论，不抄原文；登记前按自己版本的字节码核对签名。**

## 4. 三路对比与推荐组合

| 路径 | 覆盖度 | 成本 | 盲区 | 最适合 |
|---|---|---|---|---|
| 静态审计 | 中（查到"存在"很可靠） | 中（可脚本化） | 条件分支、动态构造 | 无显示环境、审计框架、建基线 |
| tracing agent | 高（跑到的路径全覆盖） | 中高（要设计负载） | 未执行的路径 | 真实功能走查、终局验证、替代手工维护 |
| 经验库 | 低（只覆盖已知框架） | 低 | 项目自身业务代码 | 起步、快速排除常见坑 |

**推荐组合**（也是 AHA 实际用的）：

```
① 扫依赖自带元数据（划掉能划掉的）
② 查经验库（拿到框架层的已知点）
③ 静态审计自己的代码与框架（补经验库没有的）
④ 手写/生成登记
⑤ 真机或 agent 跑一遍，把没覆盖到的补上
⑥ 把新发现回写经验库
```

第 ⑥ 步是跨项目价值的来源：**每用一次，经验库就更准一次**。
