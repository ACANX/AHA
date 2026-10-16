# 登记：把发现结果写成 native-image 能读懂的元数据

发现之后就是登记。这一节讲**放哪里、用什么格式、字段怎么选、来源怎么排**。

## 1. 放哪里、叫什么

元数据随 jar 进 classpath，native-image **自动读取**，不需要任何命令行参数。
推荐路径（groupId / artifactId 目录不是必须，但便于人看、也便于工具按来源合并）：

```
src/main/resources/META-INF/native-image/<groupId>/<artifactId>/reachability-metadata.json
```

- 同一份元数据可以被多个模块各自携带，GraalVM 会**合并**；重复注册是允许的（不报错）；
- 想「这个二进制到底是靠哪些元数据编出来的」可事后对账，就把这些文件复制进构建产物目录；
- 旧格式（`reflect-config.json` / `jni-config.json` / `resource-config.json`）仍被支持，
  但**新项目一律用统一格式** `reachability-metadata.json`。

## 2. 统一格式 vs 旧格式

| | 统一格式 `reachability-metadata.json` | 旧格式（多文件） |
|---|---|---|
| 顶层 | 对象，含 `reflection` / `jni` / `resources` / `foreign` / `proxy` / `serialization` 段 | 每个文件一个数组：`reflect-config.json` 等 |
| 类型字段 | `"type"` | `"name"` |
| 来源 | 新版 GraalVM（21+ 推荐） | 兼容保留 |

**新写的一律用统一格式**；只有要复用旧工具链产物时才见旧格式。
两种混用没问题，GraalVM 合并处理。

## 3. `reflection` 段：字段与取舍

一条反射条目：

```json
{
  "type": "com.example.Foo",
  "allDeclaredConstructors": true,
  "allPublicConstructors": true,
  "allDeclaredMethods": true,
  "allPublicMethods": true,
  "allDeclaredFields": true,
  "allPublicFields": true,
  "unsafeAllocated": true,
  "methods": [
    {"name": "doThing", "parameterTypes": ["java.lang.String", "int"]}
  ],
  "fields": [
    {"name": "cache", "allowWrite": true}
  ]
}
```

| 字段 | 含义 | 什么时候用 |
|---|---|---|
| `allDeclaredConstructors` | 注册所有声明构造器 | 框架要 `newInstance()`，且构造器参数不确定 |
| `allPublicConstructors` | 只注册 public 构造器 | 同上的收窄版 |
| `allDeclaredMethods` / `allPublicMethods` | 注册所有方法 | 需按名字查找方法但不关心具体签名 |
| `allDeclaredFields` / `allPublicFields` | 注册所有字段 | 序列化 / 注入 / 结构体字段 |
| `methods[].parameterTypes` | **精确签名** | `getMethod(name, types...)` 的调用点 |
| `fields[].allowWrite` | 允许写该字段 | 反射赋值 |
| `unsafeAllocated` | 允许 `Unsafe.allocateInstance` | 序列化框架通常会用到 |

### 3.1 精确签名 vs 全量：怎么选

| 情况 | 选法 |
|---|---|
| 你知道确切的 `getMethod("m", A.class, B.class)` | 用 `methods` + 精确 `parameterTypes`（最小可达性） |
| 框架用 `getDeclaredMethods()` 遍历再挑 | 用 `allDeclaredMethods` |
| 类是"被原生库按名字查找"的 DTO / 回调 | 用 `jniAccessible` + 全量（见第 4 节） |
| 不确定调用方式 | **先全量**（`allDeclared*`），真机跑通后再按构建报告收窄 |

**精确签名是跨版本最脆的地方**：类名相对稳定，方法签名会变。
抄任何公开配置前，都要用自己版本的字节码核对：

```bash
javap -p com/example/Bar.class | grep ' doThing'
# 输出形如：public void doThing(java.lang.String, int);
```

### 3.2 记录类 / 不可变对象（Jackson、序列化）

反射构造 + 读 accessor，通常需要：

```json
{"type": "com.example.Record", "allDeclaredConstructors": true,
 "allDeclaredMethods": true, "allDeclaredFields": true}
```

> **AHA 实测**：Jackson 3 **不自带** native-image 元数据，
> 配置 POJO / 会话记录必须自己注册，否则"能启动、一读配置就炸"。

**`record` 还有一条单独的坑**：Jackson 反序列化 `record` 时要读 **record components**
（`Class#getRecordComponents()`），`allDeclaredMethods` / `allDeclaredFields` **都不覆盖它**。
症状是运行期抛 `UnsupportedFeatureError: Record components not available for record class …`
（或对应的 `MissingReflectionRegistrationError`），而 JVM 模式、单测、CI 冒烟**全绿** ——
因为它们都跑在 JVM 上，那里反射永远可用。

> **AHA 实测（2026-10-16，PR #243）**：原生包一点「检查更新」必崩，
> 因为更新模块的 **21 个 `record` / `enum` 一条都没登记**。
> 修法：全量登记 + 加一道**扫源码 ↔ 两份元数据文件**的静态守卫
> （新增 record 忘了登记就会红；反向注入验证过）。

### 3.3 「类型在册」≠「用途在册」

登记是**按用途**生效的：`methods` 不等于允许反射 `new`，`allDeclaredMethods` 也不等于
「可以 `getDeclaredConstructor()`」。AHA 实测：`Region` 在元数据里早就有条目
（`allDeclaredMethods` / `allDeclaredFields` 都在），但因为 FXML 里有一处
`<Region fx:id="leftSpacer"/>` 需要**反射实例化**，而它**没有登记构造器** ⇒
原生包启动即 `LoadException: Region is not a valid type.`（`TS-2610-032`）。

| 用途 | 需要的登记 |
|---|---|
| `Class.forName("X")` + `getDeclaredMethod` | `methods` / `allDeclaredMethods` |
| `getDeclaredConstructor(...)` / FXML 实例化元素 | **`allDeclaredConstructors` / `constructors`** |
| 静态字段读写 | `fields` / `allDeclaredFields` |
| native 按名查找 | `jniAccessible`（另一段） |

**审查动作**：新增界面 / 新增 `Class.forName` 的代码时，对着「这个类型将来会被怎么用」逐项核对，
而不是看到「类型在册」就放过。

## 4. `jni` 段：反射之外的另一半

`jniAccessible` 解决的是「**原生库按名字找 Java 成员**」，与「Java 反射加载类」是两件事：

```json
{
  "jni": [
    {
      "type": "com.example.NativeCallback",
      "allDeclaredConstructors": true,
      "allDeclaredMethods": true,
      "allDeclaredFields": true,
      "fields": [
        {"name": "structFieldA"},
        {"name": "SIZEOF"}
      ]
    }
  ]
}
```

判断一个类是否需要 `jniAccessible`：

- 它有 `native` 方法，且原生侧会回调它？
- 它是原生绑定的**结构体 / 常量 DTO**（字段名与原生一致，常见 `SIZEOF` / `sizeof`）？
- 它是被 `System.loadLibrary` 加载的库所绑定的类？

**排查顺序**：先看是不是 JNI 问题（`UnsatisfiedLinkError`、原生回调失败），
是的话光加反射条目无效，必须补 `jniAccessible`。

### 4.1 「值全零」：JNI 缺口最隐蔽的一种表现

JNI 缺口**不一定抛异常**。当原生代码按**字段名**回写 Java 对象（`GetFieldID`）时，
字段没登记 ⇒ 拿不到成员 ID ⇒ **回写不发生** ⇒ 字段保持 Java 默认值，
表现为 `0` / `0.0` / `null` / 空串。进程不崩、界面照开，只有打开 verbose 日志才看得见。

> **AHA 实测（2026-10-13，issue #60）**：`prism.verbose` 打印的 OS / D3D 驱动信息
> 全是零值（`Windows version 0.0 build 0`、`ven_0000`）。根因是 `prism_d3d.dll` 的
> `nGetDriverInformation` 用 `GetFieldID` 按名回写 `D3DDriverInformation` 的字段，
> 而该类及其字段在**两份元数据里都是 0 条**。修法是登记到 `jni-config.json`
> （`allDeclared*`），并在**产物自证**里加 `require_jni`、在 Windows 原生冒烟里加
> 「不得出现全零指纹」的可判别断言。

**识别信号**：某个诊断 / 状态输出**恰好是默认值**，而对应的 native 路径确实会回写它。
这类问题在审查清单里要单列一问：**「这些成员登记了吗？」**

## 5. `resources` 段：别只写一行正则

两种写法：

```json
{
  "resources": [
    {"pattern": ".*\\.(png|jpg|svg|css|properties|yaml|yml|json|txt)$"},
    {"glob": "META-INF/services/.*"},
    {"glob": "com/example/messages.properties"}
  ]
}
```

或用构建参数 `-H:IncludeResources=<regex>`（等价，但参数文件不便于按来源分组）。

**规则**：

1. 用**精确清单**（`glob`）优先，正则是懒办法——正则会吃进大量无关文件（AHA 实测：
   宽正则会带进 27+ MiB 无关 `byte[]`）；
2. **必带**：`META-INF/services/.*`；框架的原生库扩展名（`.so` / `.dylib` / `.dll`）；
   框架自身的资源（着色器 `.obj`/`.frag`/`.vert`、字体、样式）；
3. **必做**：把源码里每个 `getResourceAsStream` 的实际路径列出来逐项核对
   （见 [discovery](discovery.md) §1.2）——凭扩展名直觉一定会漏；
4. **别忘了 JDK 自带的资源**：它们既不在你的源码里，也常常**没有扩展名**，
   按扩展名写的正则一条都匹不上。AHA 实测（issue #117）：CLI 原生镜像 `--help`
   在三平台一致退出码非 0 —— picocli 渲染用法表格时经 `BreakIterator` 读
   `sun/text/resources/LineBreakIteratorData`（**无扩展名**），缺了它直接抛 `InternalError`：
   ```text
   -H:IncludeResources=sun/text/resources/LineBreakIteratorData
   -H:IncludeResources=.*\.icu$
   ```
   **判据**：`--version` 正常、`--help` 崩，且三平台表现一致 ⇒ 优先怀疑资源 / 逻辑，
   而不是平台差异。

> **收窄与完整的关系**：先把该有的补齐（漏 = 运行期缺文件），再谈收窄（体积）。
> 顺序反了就是"用体积优化换运行期崩溃"。

## 6. 其它段（按需）

| 段 | 用途 | 触发场景 |
|---|---|---|
| `serialization` | 允许 `ObjectInputStream` 反序列化的类 | 有可达的 Java 反序列化 |
| `proxy` | `Proxy.newProxyInstance` 的接口组合 | 动态代理框架 |
| `foreign` | FFM `Linker` 的 downcall / upcall 描述符 | JDK 22+ FFM（JLine 4 的 FFM 后端就用它） |

这些能自动采集就自动采集（tracing agent）；手写容易漏且难维护。

## 7. 来源优先级怎么落地

| 优先级 | 做法 | 落地动作 |
|---|---|---|
| 1 依赖自带 | 什么都不用做 | `unzip` 确认有 `META-INF/native-image/` |
| 2 框架生成 | 加注解处理器 / 构建插件 | 例：picocli → `picocli-codegen`；Spring → `RuntimeHints`；Quarkus → 构建步 |
| 3 tracing agent | 采集后作为输入 | `config-output-dir` / `config-merge-dir` |
| 4 手写 | 放 `META-INF/native-image/.../reachability-metadata.json` | 必须配守卫（见 [verification](verification.md)） |

**框架生成的例子（picocli）**：picocli **不带**元数据，但在 `maven-compiler-plugin`
里加 `annotationProcessorPaths` 指向 `info.picocli:picocli-codegen`，
编译期扫源码生成 `META-INF/native-image/picocli-generated/reflect-config.json`。
它跟着源码走——子命令 / 选项一变就跟着变，**比手写清单可靠**。

## 8. 跨平台条目共用

同一份元数据通常在多平台共用：

- 平台专属类（如某平台的原生工厂 / 管线）在**缺席平台上只产生 warning，不会构建失败**
  （GraalVM 的元数据解析器对找不到的元素只记 warning）；
- 但**反过来要小心**：某类只在 A 平台存在，在 B 平台注册它就是噪声；
  真正跑起来仍缺的失败只会在对应平台暴露，所以**每个平台的腿都要真机跑**。

## 9. 初始化时机（严格说不是"元数据"，但常一起处理）

```bash
--initialize-at-run-time=com.example.NativeBacked
```

**判据**：**构建期初始化就会去碰"只有运行期才存在的东西"**（屏幕、窗口、数据库、
文件系统路径、原生库、系统主题、驱动环境）。典型：GUI 工具包、JDBC 驱动、日志框架的配置装配。

- 构建期报「某类必须在运行期初始化」→ 把类加进去；
- 反过来 `--initialize-at-build-time` 是加速/固化，**不要乱用**（会把运行期状态冻结）。

## 10. 写完必做

1. 用官方 schema 校验（避免手写 JSON 的低级错误）：

   ```bash
   # reachability-metadata-schema-v1.2.0.json 随 GraalVM 发布，或从官方仓库取
   python3 -c "import json,jsonschema; jsonschema.validate(json.load(open('reachability-metadata.json')), json.load(open('schema.json')))"
   ```

2. 跑守卫（[verification](verification.md)）：单测 + 产物自证 + 反向验证；
3. 真机或 agent 验证；
4. 把新发现的点回写 [catalog](catalog.md)。
