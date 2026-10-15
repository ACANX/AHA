# 在 GitHub Actions 中使用 GraalVM：介绍、参数配置与权威查询指南

**文档版本**：v1.0.0
**状态**：冻结
**生效日期**：2026-10-15
**最后更新**：2026-10-15
**负责人**：@ACANX
**适用版本**：AHA 0.1.x
**关联**：issue #28；`Docs/Design/DesktopNativeDesign.md`、`Docs/Design/CliNativeDesign.md`；技能 [java-app-graalvm-native-image-compile](../../.agents/skills/java-app-graalvm-native-image-compile/SKILL.md)

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-15 | 初版：Action 输入速查（取自 `action.yml`）、发行版与版本选择、参数定法与「四类清单」闭环、权威查询入口（含本地自证命令）、症状到参数的对照表、本仓库落地位置索引（issue #28）| @ACANX / CNXNC |

---

## 0. 这份文档解决什么问题

issue #28 的原始诉求是一句话：**「下次要用的时候，知道去哪查、怎么快速把参数定下来，拿到的是权威信息。」**

本文件就写这三件事：

| 问题 | 本文件的回答 |
|---|---|
| 去哪查？ | §6「怎么查权威资料」—— 含**本地自证命令**（比翻文档更可靠）与逐个实测过的官方链接 |
| 怎么快速定参数？ | §5「参数怎么定」—— 唯一来源原则 + `build-report` 闭环 + **症状 → 参数**对照表 |
| 权威性怎么保证？ | 每张表都注明来源：Action 输入抄自 `action.yml`；参数语义以**你手上那份工具**的回答为准 |

**分工（避免和现有文档重复）**：

- 本文件：**查什么、去哪改、怎么自证**（面向「下次要用的人」）。
- [SKILL.md](../../.agents/skills/java-app-graalvm-native-image-compile/SKILL.md)：从 jar 到原生二进制的**工程化落地步骤**（含四隔离、参数 cookbook、排错）。
- [DesktopNativeDesign.md](../Design/DesktopNativeDesign.md) / [CliNativeDesign.md](../Design/CliNativeDesign.md)：**为什么本仓库这么定**（隔离方案、JDK 轴对比实验、实测数字）。
- `Docs/Troubleshooting/TS-*.md`：**一次事故一份档案**（症状 → 根因 → 处置）。

---

## 1. 一条原生腿上有六层可配置面

排查参数问题前先分清「在哪一层」—— 顺着这张表找，比从头读工作流快得多。

| # | 层 | 是什么 | 权威来源 | 本仓库落点 |
|---|---|---|---|---|
| 1 | **Action 输入层** | `graalvm/setup-graalvm` 的 `with:` | 该 action 的 `action.yml` / marketplace | `.github/workflows/CliNative.yml`、`DesktopNative.yml` |
| 2 | **发行版与版本层** | `distribution` / `version` / `java-version` | 同上 + 官方 release notes | 工作流的 `version: '25.4'` + 矩阵 `matrix.jdk` |
| 3 | **native-image 参数层** | 编译期开关 | 官方 Options 页 + **本地 `--help-extra`** | `aha-*-native/src/native/native-image-args*.txt`（**唯一来源**）|
| 4 | **元数据层** | 反射 / JNI / 资源 / 初始化时机 | 官方 metadata 页 + 姊妹技能 | `aha-*/src/main/resources/META-INF/native-image/**/*.json` |
| 5 | **构建编排层** | Maven profile、exec 调用 | 本仓库规范 | `pom.xml` 的 `desktop-native` / `cli-native` / `native-jdk27` |
| 6 | **运行器与产物层** | 每个平台各自编（**不能交叉编译**）| 官方文档 + `BuildSpec.md` | 三条腿的 `matrix`（Linux / Windows / macOS）|

---

## 2. Action 层：`graalvm/setup-graalvm`

### 2.1 本仓库的实际用法

两个原生工作流里的写法完全一致（出处：`.github/workflows/CliNative.yml` / `DesktopNative.yml`）：

```yaml
- name: 安装 GraalVM（JDK ${{ matrix.jdk }}）
  id: graal
  uses: graalvm/setup-graalvm@v1
  with:
    version: '25.4'                            # GraalVM 发行号；具体对应哪个 JDK 以该版 release notes 为准
    java-version: ${{ matrix.jdk }}             # JDK 大版本：矩阵取 25 / 27
    distribution: graalvm                       # Oracle GraalVM（GFTC 许可，可免费用于生产）
    github-token: ${{ secrets.GITHUB_TOKEN }}   # 走认证，降低 API 限流
    cache: maven                                # 顺带缓存 Maven 依赖（等价于 actions/setup-java 的缓存）

- name: 工具链不可用时的说明（本腿的其余步骤都会跳过）
  if: steps.graal.outcome != 'success'
  run: |
    {
      echo "### ${{ matrix.label }}（jdk${{ matrix.jdk }}）"
      echo ""
      echo "- **本腿跳过**：GraalVM（JDK ${{ matrix.jdk }}）安装失败（结论：${{ steps.graal.outcome }}）。"
      echo "  常见原因是该 JDK 大版本的 GraalVM 尚未发布（实测 JDK 27 就是如此），属于上游发布节奏。"
      echo "- 本腿是数据点，不是交付物：跳过不影响其它腿，也不影响发布。"
    } >> "$GITHUB_STEP_SUMMARY"
```

**最后那段「跳过也要解释自己」是本仓库的一条固定写法**：跳过的步骤不会自己解释自己，不写摘要，看运行页的人会以为漏跑了。

### 2.2 输入速查

下表是 `action.yml`（`ref=v1`）的**完整输入清单**，逐项抄录；「本仓库」列标出实际用法：

| 输入 | 默认 | 作用 | 本仓库 |
|---|---|---|---|
| `java-version` | `''` | JDK 版本（大版本 `25` / 具体 `25.0.3` / EA `25i5-ea`、`latest-ea` / `dev`）| ✅ `${{ matrix.jdk }}` |
| `distribution` | `''` | 发行版关键字（见 §3）| ✅ `graalvm` |
| `version` | `''` | GraalVM 版本（见 §3.2 语法）| ✅ `'25.4'` |
| `github-token` | `${{ github.token }}` | 与 GitHub API 通信，降低限流 | ✅ `secrets.GITHUB_TOKEN` |
| `cache` | `''` | 依赖缓存：`maven` / `gradle` / `sbt` | ✅ `maven` |
| `set-java-home` | `'true'` | 把 `JAVA_HOME` 指向这份 GraalVM | 未显式传（用默认）|
| `java-package` | `'jdk'` | `jdk` 或 `jdk+fx`（**目前仅对 Liberica 生效**）| 未传 |
| `components` | `''` | 额外 GraalVM 组件（`native-image`、`ruby,nodejs` …）| 未传（25.x 发行已含 `native-image`）|
| `check-for-updates` | `'true'` | 有新版本时在作业上挂提示注解 | 未传（用默认）|
| `native-image-musl` | `'false'` | 装 musl，编**静态**二进制（仅 Linux）| 未传 |
| `native-image-job-reports` | `'false'` | **把 build-report 贴成作业摘要** | ⚠️ 未启用（本仓库自己 `--emit build-report` + 上传制品）|
| `native-image-pr-reports` | `'false'` | **把 build-report 贴成 PR 评论**（需 `pull-requests: write`）| ⚠️ 未启用（同上）|
| `native-image-pr-reports-update-existing` | `'false'` | 改为**更新**已有评论，而非再发一条 | 未传 |
| `native-image-enable-sbom` | `'false'` | 生成最小 SBOM 并提交依赖图（需 `contents: write`；仅 Oracle GraalVM **JDK 24+**）| 未传 |
| `gds-token` | `''` | 通过 GraalVM Download Service 取 Oracle GraalVM | 未传 |

**怎么自己看最新清单（比翻文档快，且不会过期）**：

```bash
# 看 v1 的输入/输出原文；把 ref 换成任意 tag / 分支名即可对比版本差异
gh api repos/graalvm/setup-graalvm/contents/action.yml?ref=v1 --jq '.content' | base64 -d
```

> 上面这道命令也是本文件的取材方式：**表里的东西是抄来的，不是回忆出来的。**

### 2.3 输出与环境

- 显式 outputs 只有一个 `cache-hit`（缓存是否精确命中）。
- 真正有用的是它设置的**环境**：`set-java-home: true` 时 `JAVA_HOME` 指向这份 GraalVM，另有 `GRAALVM_HOME`，`native-image` 进入 `PATH`。
- **自证**（本仓库原生模块的预检步骤就是这个思路）：`java --version` 与 `native-image --version` 各跑一次 —— 让「没装工具链」变成一眼可懂的失败，而不是编译到一半报奇怪的错。
- **Windows 差异**：可执行文件叫 `native-image.cmd`，exec 插件不会自动补后缀 ⇒ 本仓库由工作流传 `-Dnative.image.executable=native-image.cmd`。

### 2.4 版本与 JDK 的组合

| 现象 | 原因 | 处置 |
|---|---|---|
| 安装失败 / 腿被跳过 | `version` 与 `java-version` 对不上，或该 JDK 尚无发行 | 腿级跳过 + 摘要说明（本仓库做法）；矩阵里放未来 JDK **不会**让 CI 变红 |
| `native-image: command not found` | 版本较老时它不是默认组件 | 加 `components: native-image`，或换新版本 |
| 想少写上传步骤 | 自己 `--emit build-report` 再传制品 | 也可改用 `native-image-job-reports` / `native-image-pr-reports`；两条路等价，按需选 |
| action 大版本变更 | 上游发布节奏 | 本仓库固定 `@v1`；升级前先看 [releases](https://github.com/graalvm/setup-graalvm/releases) |

**实测记录**：JDK 27 目前没有对应的 GraalVM 发行（`java-version: '27'` 的腿因此跳过），这是上游发布节奏，不是本仓库的问题。

---

## 3. 发行版与版本怎么选

### 3.1 发行版（`distribution`）

| 关键字 | 发行版 | 许可 | 备注 |
|---|---|---|---|
| `graalvm` | Oracle GraalVM | [GraalVM Free Terms and Conditions](https://www.oracle.com/downloads/licenses/graal-free-license.html) | **本仓库选用**：免费用于生产，`native-image` 开箱可用 |
| `graalvm-community` | GraalVM Community Edition | GPLv2 + CPE | 社区版；`java-version: dev` 只对它有效 |
| `mandrel` | Mandrel | GPLv2 + CPE | Red Hat 的下游构建（Quarkus 生态常用）|
| `liberica` | Liberica NIK（BellSoft）| 自有 EULA | 唯一支持 `java-package: jdk+fx`（内置 JavaFX）|

换发行版**只改一个字符串**；但要注意 `jdk+fx` 这类特例只对某些发行版生效 —— 本仓库用的是 Maven 依赖里的 JavaFX，不依赖发行版内置。

### 3.2 版本语法（`version`）

- `X.Y`（如 `25.0`、`25.4`）：**25.0 起的 GraalVM 发行**采用这种形式（`X` 与 JDK 大版本一致）；
- `X.Y.Z`（如 `22.3.0`）：22.3.2 及更早的具体发行；
- `latest` / `mandrel-latest` / `mandrel-X.Y.Z.W`：Mandrel 系列；
- EA 与 dev：由 `java-version` 表达（`25i5-ea` / `latest-ea` / `dev`），并限定发行版。

> 具体某个版本对应哪个 JDK、是否 LTS，**以该版本的 release notes 为准**（例：[25.4](https://www.graalvm.org/release-notes/25.4/)）。

### 3.3 升级时的正确姿势

**先只换 JDK / 工具链版本，参数一个都不动，编一遍** —— 看哪些参数失效、哪些告警新增。参数按「JDK 轴」分文件存放（`native-image-args-jdk27.txt` 与基线逐条对应），两份文件的 **diff 正好就是实验记录**。

---

## 4. native-image 参数怎么定

### 4.1 唯一来源原则

```text
aha-desktop-native/src/native/native-image-args.txt              # 基线（JDK 25）
aha-desktop-native/src/native/native-image-args-jdk27.txt        # JDK 轴对比
aha-desktop-native/src/native/native-image-args-extra.txt        # 平台专属「空档」
aha-desktop-native/src/native/native-image-args-windows.txt      # Windows 专属（链接器参数）
aha-cli-native/src/native/native-image-args.txt                  # CLI 基线
aha-cli-native/src/native/native-image-args-jdk27.txt            # CLI JDK 轴对比
```

- POM 用 `@<参数文件>` 传给 `native-image`（属性 `native.image.argsfile`，可用 `-D` 覆盖）；
- **不要在 POM 或工作流里散写开关** —— 只改参数文件，POM 与工作流都不用动；
- 参数文件会**原样打进发布包**：半年后仍能回答「这个二进制是怎么编出来的」。

### 4.2 三步闭环：编 → 读报告 → 补清单

```text
① 先编（--emit build-report 产出机器可读报告）
② 读 target/native/ 下的 build-report：未决的可达性条目（反射 / 资源 / 协议）就是「下一步该加什么」
③ 补进「四类清单」→ 再编 → 直到报告里没有未决项
```

**四类清单**（原生镜像的全部难点都在这四类）：

| 类别 | 判据 | 表达方式 |
|---|---|---|
| **初始化时机** | 构建期初始化就会碰「运行期才存在的东西」（屏幕、窗口、原生库、系统主题、驱动、DB 文件、终端）| `--initialize-at-run-time=<包/类>` |
| **反射** | 运行期按名字找类 / 方法 / 字段 | `reflect-config.json`（或 `reachability-metadata.json`）|
| **资源** | 运行期按路径读文件（配置、图标、`META-INF/services`、着色器、原生库）| `-H:IncludeResources=<正则>`（漏掉的症状多为**静默坏掉**，不是构建失败）|
| **原生库** | JNI 要加载 `.so` / `.dylib` / `.dll` | 资源正则包含这些扩展名 + 必要时 `jni-config.json` |

### 4.3 症状 → 参数（快速定位用）

| 症状 | 先怀疑 | 去处 |
|---|---|---|
| 构建成功，**启动即崩** `ClassNotFoundException`（栈顶在框架里）| 反射未注册 | 补元数据；见 TS-2610-017 |
| 启动即报 `CONFIG_NOT_FOUND` / 读不到内置 yaml | 资源正则漏扩展名 | `-H:IncludeResources`（本仓库已含 `yaml|yml|properties`）|
| 首次绘制 / 打开某界面才崩 | 着色器、FXML 等资源缺失 | 资源清单；见 TS-2610-032 |
| HTTPS 请求发不出去（**不报错**）| 协议处理器未注册 | `--enable-url-protocols=http,https` 或元数据 |
| 构建期报「运行期才知道的东西」 | 初始化时机 | `--initialize-at-run-time=<包>`；官方 ClassInitialization 页 |
| 样式表查表失败 / 主题不生效 | 原生镜像下 CSS 传导差异 | TS-2610-028、TS-2610-029、TS-2610-038 |
| 字体观感与 JVM 不同 | 灰阶 vs 次像素渲染 | TS-2610-030 |
| Windows 上链接器报 `c1010070`（清单找不到）| 相对路径被解析到构建临时目录 | TS-2610-037（用绝对路径 + `-H:NativeLinkerOption=/MANIFEST:EMBED`）|
| 体积偏大 | 时区 / 区域 / GC / 调试信息 | `-H:-IncludeAllTimeZones`、`-H:-IncludeAllLocales`、GC 选择 |
| 启动慢 | GC / PGO | `--gc=`、`--pgo=`（需负载样本）|

### 4.4 本仓库当前采用的高频参数

（逐条摘自 `native-image-args.txt`；「为什么」写在文件里，这里只列要点）

| 参数 | 作用 | 本仓库取值要点 |
|---|---|---|
| `-H:+UnlockExperimentalVMOptions` | 解锁实验性选项 | **必须排在**所有实验性选项之前 |
| `-O2` | 优化等级 | 产物质量优先于构建时长 |
| `-H:+ReportExceptionStackTraces` | 构建期异常带栈 | 否则只给一个类名 |
| `-J-Xmx4g` | **宿主** JVM 堆上限 | runner 上默认可能吃太多内存 |
| `--enable-native-access=ALL-UNNAMED` | 允许运行期访问原生内存 | classpath 模式下 JNI / FFM 都在未命名模块 |
| `--enable-url-protocols=http,https` | 带上协议处理器 | **已弃用但保留**（删掉会静默发不出请求）；退出路径写在参数文件里 |
| `-H:+AddAllCharsets` | 非 UTF-8 字符集 | Windows 控制台与编码探测依赖 |
| `--emit build-report` | 机器可读构建报告 | **下一步该加什么参数的唯一依据** |
| `--gc=G1`（桌面端）/ 不指定（CLI）| GC 策略 | GUI 长驻关心停顿 → G1；CLI 短命 → 默认 serial |
| `--future-defaults=all` | 提前用未来默认行为 | 编不过就是升级信号（为后续 JDK 铺路）|
| `-R:MaxHeapSize=1g` | **镜像内**运行期最大堆 | 避免「按机器内存百分比」的不可预期行为 |
| `-H:IncludeResources=…` / `META-INF/services/.*` | 资源清单 | 含配置、版本号、服务文件、原生库、FXML、着色器 |
| `-H:+PrintClassInitialization` / `-H:Log=registerResource:off` | 诊断 / 降噪 | JDK 27 对比腿；要用它生成元数据时把后者注释掉 |
| `-H:NativeLinkerOption=/MANIFEST:EMBED` | Windows exe 嵌兼容清单 | 仅 Windows 腿（放在平台专属参数文件里）|

### 4.5 实验位怎么用

一次只开一个，改完把**实测数字**记进设计文档的对比表（`DesktopNativeDesign.md` / `CliNativeDesign.md` 第 5 节），别让它只活在提交记录里。实验位包括：`--pgo=`、`--gc=epsilon`、`-march=x86-64-v3`、`-H:-IncludeAllTimeZones`、`-H:AdvancedObfuscation=""` 等。

### 4.6 两条硬约束

1. **不能交叉编译**：每个平台必须由该平台的 runner / 机器产出自己的二进制。
2. **对外分发的产物不要 `-march=native`**：它把产物绑死构建机的 CPU 特性，换台机器可能直接非法指令崩溃；要提性能就写可预期的目标（如 `x86-64-v3`）。

---

## 5. 怎么查权威资料（本节是 issue #28 的核心）

### 5.1 第一顺位：问工具，而不是问文档

文档会跟版本脱节，**你手上这份工具不会**：

```bash
native-image --version                 # 当前工具链到底是哪一版
native-image --help                    # 常用选项
native-image --help-extra              # 进阶选项
native-image --expert-options          # 全部「专家选项」清单
native-image --expert-options-all      # 连隐藏的一起给
native-image --expert-options-detail   # 带说明（输出很长）
```

> 这组命令本身取自官方 Options 页 —— 也就是说：**「选项清单」这件事的权威来源，确实就是工具本身。**

### 5.2 官方文档地图

（以下链接于 2026-10-15 逐个实测可访问；官方改版后若某页 404，按 §5.1 用工具自证，并顺手更新本表）

| 想知道什么 | 去哪一页 |
|---|---|
| Native Image 总览与入门 | <https://www.graalvm.org/latest/reference-manual/native-image/> · [basics](https://www.graalvm.org/latest/reference-manual/native-image/basics/) |
| **全部命令行选项** | [Options](https://www.graalvm.org/latest/reference-manual/native-image/overview/Options/) |
| 构建配置（参数文件、元数据位置）| [BuildConfiguration](https://www.graalvm.org/latest/reference-manual/native-image/overview/BuildConfiguration/) |
| 构建输出与产物结构 | [BuildOutput](https://www.graalvm.org/latest/reference-manual/native-image/overview/BuildOutput/) |
| **build-report 怎么读** | [build-report](https://www.graalvm.org/latest/reference-manual/native-image/overview/build-report/) |
| **初始化时机**（`--initialize-at-run-time`）| [ClassInitialization](https://www.graalvm.org/latest/reference-manual/native-image/optimizations-and-performance/ClassInitialization/) |
| 性能与优化取舍 | [optimizations-and-performance](https://www.graalvm.org/latest/reference-manual/native-image/optimizations-and-performance/) |
| 元数据（反射 / JNI / 资源）| [metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) · [Compatibility](https://www.graalvm.org/latest/reference-manual/native-image/metadata/Compatibility/) · [自动采集](https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/) |
| 动态特性（JNI / 代理 / 资源）| [dynamic-features](https://www.graalvm.org/latest/reference-manual/native-image/dynamic-features/) |
| 原生代码互操作（JNI / FFM）| [native-code-interoperability](https://www.graalvm.org/latest/reference-manual/native-image/native-code-interoperability/) |
| 构建产物 / 运行期排错 | [debugging-and-diagnostics](https://www.graalvm.org/latest/reference-manual/native-image/debugging-and-diagnostics/) |
| 从 jar 编一个可执行文件（最小示例）| [build-native-executable-from-jar](https://www.graalvm.org/latest/reference-manual/native-image/guides/build-native-executable-from-jar/) |
| 版本与发布节奏 | [release notes](https://www.graalvm.org/release-notes/) · [下载页](https://www.graalvm.org/downloads/) |
| 工具链源码（选项/行为的最底层）| <https://github.com/oracle/graal> |
| Maven / Gradle 原生插件 | [Native Build Tools](https://graalvm.github.io/native-build-tools/latest/index.html) · [Maven 插件](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html) |
| 「别人踩过的坑」的元数据库 | <https://github.com/oracle/graalvm-reachability-metadata> |

### 5.3 Action 自己的资料

| 资料 | 地址 |
|---|---|
| 仓库与 README（发行版清单、版本语法、模板）| <https://github.com/graalvm/setup-graalvm> |
| Marketplace 页面（用户视角的简介与用法）| <https://github.com/marketplace/actions/github-action-for-graalvm> |
| 版本列表（含 EA / dev）| <https://github.com/graalvm/setup-graalvm/releases> |
| **输入/输出的最终真相** | `action.yml`（用 §2.2 的 `gh api` 命令读原文）|
| 工作流语法 / 令牌权限 | [workflow syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax) · [authenticate with GITHUB_TOKEN](https://docs.github.com/en/actions/tutorials/authenticate-with-github_token) |

### 5.4 元数据这块的专精入口

元数据（反射 / JNI / 资源 / 初始化）是原生镜像里最容易反复踩的部分，本仓库另有专门技能：**[graalvm-reachability-metadata](../../.agents/skills/graalvm-reachability-metadata/SKILL.md)**（三路发现法、来源优先级、四层守卫、常见框架经验库）。要写元数据时先看它，再看官方 metadata 页。

### 5.5 本仓库自己的「权威来源」

| 想知道什么 | 去哪 |
|---|---|
| 参数为什么长这样（逐条「为什么」）| `aha-*-native/src/native/native-image-args*.txt` 的注释 |
| 隔离为什么这么做、实测数字 | `Docs/Design/DesktopNativeDesign.md` / `CliNativeDesign.md` |
| 某次事故根因 | `Docs/Troubleshooting/TS-2610-010/012/014/015/017/028/029/030/032/033/037/038.md` |
| 工程化步骤（从零把项目做进原生镜像）| `.agents/skills/java-app-graalvm-native-image-compile/SKILL.md` |
| 构建与检查分层、快照产物 | `Docs/DevSpec/BuildSpec.md` |

---

## 6. 快速决策树

| 我想要… | 改哪里 | 怎么自证 |
|---|---|---|
| 换 GraalVM 版本 / JDK | 工作流矩阵 `matrix.jdk` 或 `version:` | 腿跑通 + 产物 `--version` |
| 换发行版 | 工作流 `distribution:` | 同上 |
| 加 / 删一个编译参数 | `src/native/native-image-args*.txt`（**唯一来源**）| 本地/CI 编一次 + 对比 build-report 与体积 |
| 修「启动即崩 / 某个类找不到」 | 元数据文件 `META-INF/native-image/**` 或 `--initialize-at-run-time` | 真机跑一次主流程（**构建成功不算**）|
| 修「资源读不到」 | `-H:IncludeResources` 正则 | 真机跑到那个功能点 |
| 修「HTTPS 发不出去」 | `--enable-url-protocols` / 元数据 | 真机完成一次真实请求 |
| 加一条原生腿（平台/JDK）| 工作流的 `matrix` | 该腿能出包并能跑 |
| 做性能对比实验 | 参数文件的「实验位」+ 设计文档对比表 | 记录**数字**，不只记结论 |
| 查某个参数的准确含义 | 先 `native-image --help-extra`，再对照 §5.2 的 Options 页 | — |

---

## 7. 未决与维护约定

- **JDK 27 腿**：当前是「上游尚无发行 ⇒ 跳过」，不是失败；上游发布后会自动变绿，无需改矩阵。
- **`native-image-pr-reports` / `native-image-enable-sbom`**：本仓库暂未启用（现在是自己 `--emit build-report` 再上传制品）。若哪天想让 PR 上直接看到构建报告，属于**一处输入即可开启**的改动，需要相应 `permissions`。
- **实验位**（PGO / epsilon GC / `-march` / 时区瘦身 / 混淆）一律不进基线，只作为数据点。
- **外部链接会过期**：某页 404 时，按 §5.1 用工具自证，并**顺手改本表**（改链接时在 PR 描述里说明一句即可）。
- 本文件只维护「怎么查、怎么定、去哪改」；**一次实测数字进设计文档，一次事故进 TS 档案**，三者不要互相抄。

---

## 8. 相关文档

| 文档 | 用途 |
|---|---|
| [SKILL.md](../../.agents/skills/java-app-graalvm-native-image-compile/SKILL.md) | 从 jar 到原生二进制的落地步骤（工程化）|
| [graalvm-reachability-metadata](../../.agents/skills/graalvm-reachability-metadata/SKILL.md) | 元数据的专精技能 |
| [DesktopNativeDesign.md](../Design/DesktopNativeDesign.md) | 桌面端原生线的设计（隔离、版本规则、对比实验）|
| [CliNativeDesign.md](../Design/CliNativeDesign.md) | CLI 原生线的设计 |
| [BuildSpec.md](../DevSpec/BuildSpec.md) | 构建与检查分层（原生属独立工作流）|
| [BuildGuide.md](BuildGuide.md) | 构建命令与构建期排错 |
| [NativeRenderDiagnosticsGuide.md](NativeRenderDiagnosticsGuide.md) | 原生渲染问题的真机取证步骤 |
