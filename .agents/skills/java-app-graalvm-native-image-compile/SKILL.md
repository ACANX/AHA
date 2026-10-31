---
name: java-app-graalvm-native-image-compile
description: 把已能跑的 Java jar 模式程序编译成多操作系统、多架构的 GraalVM Native Image 原生可执行文件。覆盖隔离式落地（profile + 独立工作流 + 独立 tag 命名空间）、依赖与 classpath 准备、反射/资源/JNI/初始化四类清单、JPMS 与 classpath 的取舍、平台分类器与空壳 jar 陷阱、平台适配（Windows exe 兼容清单与链接器参数）、产物自证与「在 CI 里真跑一次产物」、构建系统层与 CI 的静默跳过家族、按 PR 号的可追溯版本号、启动速度与内存占用的对比实验方法，以及「不崩但表现退化」这类只会在原生包里出现的差异。当需要为含 GUI、JPMS、JNI、反射的复杂项目引入原生镜像构建，或排查 native-image 构建与运行期失败时使用。
license: GPL-3.0-or-later
compatibility: 需要 GraalVM（含 native-image）；构建必须在目标平台上执行（native-image 不能交叉编译）；JavaFX 等带原生库的 GUI 框架需要对应平台的分类器工件；若项目启用 JPMS，classpath 模式可绕开模块约束。
metadata:
  version: "0.2.0"
  owner: ACANX
  source-project: AHA（复杂场景试验场：JavaFX + JPMS + JNI + 反射 + 多平台分类器）
  maturity: 可用（两个原生目标在真机跑过并持续出包；含明确标注的「不可达标」条目——LCD 次像素渲染、触摸手感，见「验证状态」）
---

# 把 Java 应用编成多平台原生镜像（jar 模式 → GraalVM Native Image）

这份技能来自一个「四难俱全」的真实验证场：**OpenJFX GUI + JPMS 模块化 + SQLite JDBC（JNI）
+ 大量反射与启动期不安全反射 API + Windows/Linux/macOS 三平台多分类器**。
这类组合正是 native-image 最容易被绊倒的地方，因此这里的条目大多带着「踩过才知道」的形态。

## 用法：**开工前就建，边做边改**

**不要把这份技能当成「做完之后写的总结」。** 原生镜像的知识几乎全是试出来的：
先建骨架（哪怕一半条目只是推断），然后在**每一次失败发生的那一刻**把它补进去，
最后在目标达成时它才自然成熟。

| 阶段 | 技能里应当有什么 | 标记 |
|---|---|---|
| 立项 / 开工前 | 目录骨架、目标与非目标、四类清单的**种子**（大量推断条目） | 草案 |
| 第一次真编 | 每次失败**立刻**补一条「症状 → 根因 → 修法」，并记下来源 | 试验中 |
| 跑通且能用 | 参数清单定稿、隔离与自证齐备、未验证条目清零 | 可用 |
| 在别的项目也用过 | 条目都带来源，迁移清单完整，反面模式齐全 | 成熟 |

**每次踩坑按这三步走（顺序不能反）**：

1. **先写进技能**：症状、根因、修法各一句 —— 趁记忆最清楚；
2. **再改代码/参数**：此时技能已经是「有据可查的下一步」；
3. **记来源**：哪次运行、哪条报错、哪个版本的工具链 —— 没有来源的条目，半年后无法判断能否删。

**本技能的达标判据（成熟 = 可以拿去做标杆参考）**：

1. 目标平台的真机产物**都能双击跑通主流程**（构建成功不算）；
2. 四类清单的每一条都标了「来源」与「验证状态」，未验证条目**清零**或明确保留为「实验位」；
3. 版本号与发布链路可追溯（哪一版对应哪个 PR、参数是哪两份文件的 diff）；
4. **在至少一个别的项目上照它跑通过一次** —— 这是「标杆参考」与「一个项目的笔记」的分界。

迭代方法与状态标记规则见 [skill-lifecycle](references/skill-lifecycle.md)。

## 何时使用

- 已有能跑通的 jar 模式产物，想再前进一步做成「解压即双击」的原生可执行文件；
- 需要把原生镜像做成**旁路验证线**（不替代正式发行），让「下载来试」变快；
- 原生镜像构建或运行期失败，需要按症状定位（见 [troubleshooting](references/troubleshooting.md)）；
- 想比较 JDK 版本 / AOT / GC 对启动速度与内存的影响（见 [measurement](references/measurement-and-experiments.md)）。

## 何时不要用

- **只想让用户免装 JDK**：`jpackage`（自包含 app image）通常更省事，且不改变运行方式；
- **需要交叉编译**：native-image **不能**交叉编译，每个平台必须由自己的 runner/机器产出；
- **程序重度依赖运行期动态类加载**（脚本引擎、热插拔插件、`Class.forName` 满天飞）：
  要么补大量配置（`reflect-config.json` 等），要么就不适合；
- **产物体积是硬指标**：原生镜像把 JDK 与依赖都编进去，通常几十 MB 起步。

## 第一原则：先隔离，再动手

**这是本技能最重要的一条。** 原生镜像构建比 JVM 构建脆得多（四类清单、平台工具链、
框架原生库），如果把它挂进常规反应堆，一次实验性失败会挡住开发、测试与发版 ——
那就等于用「验证便利」换「开发阻塞」，与引入它的目的正好相反。

四条隔离，逐条都要能被单独指认（缺一条，隔离就只是愿望）：

| 面 | 做法 | 反例（会出事） |
|---|---|---|
| 反应堆 | 原生模块只在 profile 里，默认不在 `<modules>` | 直接列进 `<modules>`：`clean verify` 跟着它一起红 |
| 检查 | 独立工作流 + 独立作业名，不进分支保护的必需检查 | 复用 `build` 作业名：PR 被卡住 |
| Tag | 独立前缀（如 `native-v…`），**不匹配**正式发版的 tag 过滤（如 `V*`/`v*`） | 复用 `V<版本>`：一合并就触发正式发版 |
| 失败传播 | `fail-fast: false` + 实验腿 `continue-on-error` + 发布作业 `if: always()` | 默认行为：一条腿失败，其余产物一起丢 |

**总约定一句话**：默认构建只保证「能编译、能打 jar、不报错」，
原生编译只在专用 profile 下发生。两层都要设：
① 模块进 profile 的 `<modules>`（默认反应堆里没有它）；
② 内层开关给安全默认值（如 `native.skip=true`，由 profile 置 `false`）——
这样「模块误入反应堆」也不会要求环境装好 GraalVM。

理由是可诊断性：默认路径一旦依赖平台工具链，构建失败就从「代码问题」变成「环境问题」，
而后者极难在别人的机器上复现。

细节与可拷贝的骨架见 [isolation-and-ci](references/isolation-and-ci.md)。

## 落地步骤

1. **确认 jar 模式已经真的能跑**。原生镜像的失败会混进「本来就没跑通」的问题里，
   先有一个干净的 JVM 基线，后面每次失败都能二分。
2. **建一个只做打包的原生模块**（`packaging=pom`，无 Java 代码），声明 `runtime` 依赖指向
   主模块，让它把主模块的**全部运行期依赖**带进来。
3. **把依赖拷成目录，并抽出 classpath**：
   `maven-dependency-plugin` 的 `copy-dependencies`（`includeScope=runtime`）+
   `build-classpath` 的 `outputProperty`（属性里的整串 classpath 可直接作为一个 argv 传给 exec）。
4. **决定 classpath 还是 module-path**（见下方决策表）。含 GUI 框架与 JPMS 的项目，
   优先 **classpath**：少一类失败。
5. **把 native-image 参数集中到一份文本文件**（`native-image-args.txt`），
   用 `native-image @argfile` 传入。理由：原生镜像参数会反复改，
   集中一处才能「只改一处」；并且这份文件可以打进产物包，事后可查「这个二进制怎么编出来的」。
6. **在 `package` 阶段调用 native-image**（`exec-maven-plugin`），
   前面加一次「版本查询」预检，让「没装 GraalVM」变成一眼可懂的失败。
7. **打包产物**：二进制 + 说明 + 参数文件 + 许可证。二进制在 Linux/macOS 上必须 `0755`。
8. **加工作流**：矩阵按平台，每个 runner 只产自己平台的产物；
   装 GraalVM 的官方 action；**产物自证**（存在、体积下限、平台魔法数、关键依赖数量）。
9. **版本与可追溯**：产物名与 tag 里带上可追溯的版本（如 `<基线版本>.<PR 号补零>`），
   下载页上能一眼看出「这一版对应哪个 PR」。
10. **首次真机验证**：构建成功 ≠ 能跑。下载产物、双击、跑通主流程，
    再把结论写回文档（连同失败项）。

## classpath 还是 module-path

| 维度 | classpath | module-path |
|---|---|---|
| JPMS 检查 | 不参与，`requires`/`exports` 不再是约束 | 参与，模块图必须完整 |
| `ServiceLoader` | 走 `META-INF/services` | 走 `provides`/`uses` |
| 平台分类器空壳冲突 | 无影响 | 会与无分类器空壳的自动模块重名 |
| 反射配置 | 同样需要 | 同样需要 |
| 编译期模块边界 | 失去 | 保留 |

**建议**：先走 classpath 把它编出来并跑通，再评估是否值得切到 module-path。
「JPMS 优先启用但非强制」——原生镜像正是「必要时让路」的场景。
如果你确实要走 module-path，同时**必须**把无分类器的空壳依赖排干净（见下一条）。

## 五个必须显式处理的坑

> ⚠ 第 5 条（`native.skip` 被静默跳过）是**唯一会「全绿却零产物」**的坑，
> 单列在 [troubleshooting](references/troubleshooting.md) §3 的「静默跳过」小节。


1. **无分类器的空壳 jar**：带分类器的工件与无分类器工件**共用同一份 POM**，
   而那份 POM 声明的是无分类器依赖 → 0 KB 空壳（无类、无 `module-info`）会被传递进来。
   要**同时**在多个依赖上排除（实测：只排一个仍然漏）。
   症状：`lib/` 里的 jar 数量比预期多、或 module-path 上出现同名自动模块。
2. **拷贝步骤不删旧文件**：`copy-dependencies` 不会清理「已经不在依赖集里」的旧 jar，
   于是「依赖树干净、目录不干净」。要么在工作目录上绑一次只清该目录的 `clean`，
   要么每次构建都真 `clean`。
3. **profile 加入的模块不被 `-am` 带上游**（Maven 3.9.x 与 4.x 行为一致，已实测）：
   只写 `-pl native -am` 时反应堆里只有它自己。
   症状极像网络问题：**「在中央仓库找不到 <主模块>」** —— 实为反应堆匹配不上，
   Maven 就去外网找了个不存在的坐标。修法：显式写 `-pl native,主模块 -am`。
4. **坐标写错也报「中央仓库找不到」**：子模块可能**覆盖**了父 POM 的 groupId
   （父 `a.b`、子 `a.b.c` 这种）。核对依赖坐标时不要照着父 POM 抄。
   另：这类失败会被写进本地仓库的**负缓存**（`*.lastUpdated`），
   修好后仍可能继续失败 —— 清掉缓存再跑。

## 平台适配：Windows exe 与兼容清单

**症状**：原生 exe 里读到的系统版本是 `Windows 8.0 build 9200`，而机器明明是 Windows 10/11；
JVM 模式却打印真实版本。**这不是读错了字段** ——
`GetVersionEx` / `GetVersion` 家族在**没有 Windows 10 兼容清单**的可执行文件里一律返回
`6.2.9200`（= 8.0），是 Windows 的兼容性 shim 主动「说谎」；`java.exe` 自带清单，所以 JVM 模式看不到。

**做法**：写一份声明支持 Win10 的清单，交给 MSVC 链接器嵌入：

```xml
<!-- src/native/app.manifest -->
<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
  <compatibility xmlns="urn:schemas-microsoft-com:compatibility.v1">
    <application>
      <supportedOS Id="{8e0f7a12-bfb3-4fe8-b9a5-48fd50a15a9a}"/>   <!-- Windows 10 / 11 -->
    </application>
  </compatibility>
</assembly>
```

```text
# src/native/native-image-args-windows.txt（**只对 Windows 腿生效**）
-H:NativeLinkerOption=/MANIFEST:EMBED
```

```xml
<!-- POM：路径必须用 ${project.basedir} 展开成绝对路径 -->
<profile>
  <id>native-windows-manifest</id>
  <activation><os><family>windows</family></os></activation>
  <properties>
    <native.image.extra.argsfile>src/native/native-image-args-windows.txt</native.image.extra.argsfile>
    <native.manifest.input>-H:NativeLinkerOption=/MANIFESTINPUT:${project.basedir}/src/native/app.manifest</native.manifest.input>
  </properties>
</profile>
```

**五个必须记住的点**（都踩过）：

1. **清单路径必须是绝对路径**：`native-image` 以自己的构建临时目录（`%TEMP%\SVM-…`）为工作目录
   调链接器 —— 写相对路径会被解析到那里，`mt.exe` 报
   `c1010070: Failed to load and parse the manifest. The system cannot find the file specified.`
2. **参数位置在主类之前**：`native-image` 只把**主类之后**的参数当应用参数，写在后面会被静默当成程序参数。
3. **平台专属参数要单独成文件**：`NativeLinkerOption` 这类 MSVC 参数在 Linux / macOS 上会让链接直接失败 ⇒
   非 Windows 时让 `native.image.extra.argsfile` 指向一个**只有注释**的「空档」文件（等于不加参数）。
4. **只给 `/MANIFESTINPUT` 不够**：必须同时给 `/MANIFEST:EMBED`，否则合并后的清单会落到**单独的**
   `.manifest` 文件，而不是嵌进 exe。
5. **清单语义是「声明兼容哪些系统」，不是「能跑的最低版本」**：声明后 `GetVersionEx` 返回真实版本
   （我们要的），同时**不再有 shim 兜底** —— 低于声明版本的机器本就不在目标平台内。

**两条工程纪律**（同一批踩出来的）：

- **给旧式 MS 工具（`mt.exe`）的文件用纯 ASCII 字节**：编码赌注不值得下（`.ps1` 要 UTF-8 BOM 是同一类）；
- **有 `continue-on-error` 的步骤要看 `outcome`，不是 `conclusion`**：
  本技能刻意容忍原生构建失败（它是数据点、不是交付物），于是「加了参数但没生效」与「构建成功」
  在作业层面**长得一模一样** —— 这正是「产物自证」存在的理由（清单是否嵌进 exe，要在产物的字节里查）。
- **Windows 上往管道打中文 / emoji 要显式设编码**：CI 里 stdout 是**管道**（不是控制台），
  默认按本地代码页（cp1252）编码，`print("✅ …")` 会抛 `UnicodeEncodeError` ——
  断言其实通过了，是那一行字把步骤弄挂的。写法：`stream.reconfigure(encoding="utf-8", errors="replace")`
  （外加 `PYTHONIOENCODING=utf-8` 双保险）。

## 四类清单（原生镜像的全部难点都在这四类）

> **本节只给入口。** 元数据的**发现 → 登记 → 验证 → 守卫**这条纵向链路已单独抽成技能：
> [**graalvm-reachability-metadata**](../graalvm-reachability-metadata/SKILL.md)
> （三条发现路径、来源优先级、精确签名、JNI、资源、四层守卫）。
> 本节只给与你项目最相关的方向，具体做那一步时看那份技能。

| 类别 | 症状 | 处理方向 |
|---|---|---|
| **初始化时机** | 构建期报错「构建时初始化失败 / 触碰了运行期才有的东西」 | `--initialize-at-run-time=<类或包>`（GUI、驱动、日志框架是重灾区） |
| **反射** | 构建通过但**运行期** `ClassNotFoundException` / `NoSuchMethodException` | `reachability-metadata.json` / `reflect-config.json` / 改用显式注册 |
| **资源** | 运行期缺图标、模板、`properties`、`META-INF/services` | `resource-config.json` 精确清单 / `-H:IncludeResources=<正则>` |
| **JNI / 原生库** | 运行期 `UnsatisfiedLinkError`、原生回调失败 | 让原生库作为资源进镜像 + `--enable-native-access` + `jniAccessible` + 运行期初始化加载器类 |

逐条的症状到修法对照，见 [troubleshooting](references/troubleshooting.md)；
参数清单与「哪些必须有、哪些是实验位」，见 [args-cookbook](references/args-cookbook.md)；
元数据的发现 / 登记 / 验证与常见框架经验库，见
[graalvm-reachability-metadata](../graalvm-reachability-metadata/SKILL.md)。

## 「不崩但表现退化」：第三类失败

除了「构建失败」与「运行期崩」，还有第三类：**程序一切正常，只是表现和 JVM 模式不一样**。
它既不会让构建红，也不会让任何守卫出声 —— 判据只能是**原生包的真机观感**。

AHA 实测（JavaFX 原生包）三例，可以用来校准预期：

| 例子 | 现象 | 机制 | 处置 |
|---|---|---|---|
| **字体渲染**（#44 / #48） | 文字发虚、偏细 | 原生下 LCD **次像素**抗锯齿退化为**灰度**（真机像素对照：彩色边缘像素相差 18.4 倍）；成因指向 DirectWrite 渲染参数在 closed-world 下读不到 | **缓解、未根治**：原生内默认写 `prism.lcdtext=false`；提供 `-Dprism.lcdtext=…` 与显式开关回退；把「这一版是灰度还是次像素、是谁决定的」**打印出来**，真机日志可读 |
| **CSS 函数失效**（#49 / `TS-2610-029`） | 控件背景发黑（看起来像「设了黑色」，其实是「没有背景」） | 原生下 modena 的 `derive` / `linear-gradient` 失效；JavaFX CSS 是**整条声明**粒度——一个值无效 ⇒ 整条 `-fx-*` 被丢弃 | 用一份**纯字面量**的补丁样式表；**能用属性 / 字面量表达的，就不要用 CSS 函数** |
| **边框丢失**（#229 / `TS-2610-046`） | 输入框「看不见」（与页底同色又没边） | 属性 API 的兜底只覆盖了背景与文字，**漏了边框** | 按「这个控件靠哪几样东西被看见」逐项清点：底色 / 文字 / 边框 / 焦点环 |

**三条通用做法**：

1. **判据只能是原生包真机观感**：「JVM 正常」推不出「原生也正常」（两侧机制不同）；
2. **缓解必须可回退、可解释**：给系统属性开关，并把决策写进日志/注释（不然下次改动要重新推理）；
3. **能静态拦住的别留给真机**：CSS 里禁用的函数、FXML 的 import / 构造器登记，都是毫秒级对账能查的。

## 产物自证（不要只看「构建成功」）

构建成功与「能跑」是两件事。CI 里至少自证四项：

1. 可执行文件存在（注意 Windows 上带 `.exe`，其它平台没后缀）；
2. **体积下限**（原生镜像把 JDK 编进去了，异常小说明只编出一个壳）；
3. **平台魔法数**（PE `4d5a` / ELF `7f454c46` / Mach-O `cffaedfe`）——
   确认拿到的确实是本平台的二进制，而不是错平台的产物；
4. **关键依赖数量**（例如「恰好 3 个带分类器的 GUI 框架 jar」），
   用来把「空壳混进来 / 依赖漏了」这两类问题挡在发布之前；
5. **在 CI 里真跑一次产物**：**命令行形态**三平台都跑得起 —— 断言退出码与输出内容
   （AHA 的 CLI 线：`--version` 退出码 0 且含本次版本号、`--help` 退出码 0；issue #117 就是这么发现的）；
   **GUI 形态**需要显示环境，只在能跑的平台上跑（AHA：Windows 腿），并断言**正面标记**
   （「进了 `main`」「界面骨架装载成功」），而不是「进程还活着 / 退出码为 0」，
   顺带断言一条**改前必然出现、改后必然消失**的指纹（`Windows version 0.0 build 0`，见 `TS-2610-034`）。
   跑不了的用静态对账补，并**如实写明盲区**（哪个平台的哪一层没有自动化覆盖）。

**还要防一类「产物少文件但构建全绿」**：打包描述符（assembly）里**匹配不到的 `<include>` 会被静默跳过**，
不报错。所以产物里应当有一个**清单**（AHA 用包内 `AHA-PACKAGE.json`：每个文件的 sha256 + 体积），
并让守卫断言「**描述符引用的每个手写产物都存在、且被 git 跟踪**」——
`Dist/` 这类「脚本 + 产物混居」的目录尤其危险（`.gitignore` 一条规则就能让入口脚本静默消失，
详见 [code-review](../code-review/references/ci-blind-spots.md) 的逃逸链清单）。

## 版本与可追溯

- **基线版本只有一个来源**（根 POM），不要在 CI 里复制字面量；
- 旁路产物用**独立前缀**打包与打 tag，避免与正式发版互相触发；
- 想让「每一版都能对上某个 PR」，用 `<基线版本>.<PR 号补零>` 这类方案，
  并在发布说明里写明基线、PR 号与构建参数（把参数文件附在包里最省事）；
- 解析 POM 时注意：XML 解析器的标签**带命名空间**，
  `findtext('version')` 会静默返回空 —— 版本号变空却没有任何报错。

## 迭代日志（每次实验/踩坑追加一行）

| 日期 | 触发场景 | 结论（写进哪一节） | 来源 |
|---|---|---|---|
| 2026-10-08 | 建原生模块，默认反应堆里多了个脆弱模块 | 「先隔离」一节：profile 隔离 + 独立工作流 + tag 命名空间 + 失败传播 | 实测（本地 + 设计推演） |
| 2026-10-08 | `-pl 原生模块 -am` 反应堆里只有它自己 | 坑 3：profile 加入的模块不被 `-am` 带上游；显式列出主模块 | 实测（Maven 3.9.11 与 4.x 对照普通模块） |
| 2026-10-08 | CI 报 `Could not find artifact`，重试 3 次日志逐字相同 | §2 第 6 条：**重试必须每轮重建前置条件**（负缓存进循环体）+ 两个识别信号 | 实测（该 artifact 在 Central 上 HTTP 200；本地复跑命令成功） |
| 2026-10-08 | 报「在中央仓库找不到主模块」 | 坑 4：子模块覆盖父 POM 的 groupId → 坐标写错也报「找不到」；并记负缓存 | 实测（清 `*.lastUpdated` 后复现与恢复） |
| 2026-10-08 | classpath 里多出 302 字节的 jar | 坑 1：无分类器空壳；且 `copy-dependencies` 不删旧文件 → 加工作目录清理 | 实测（21 → 18 个 jar 反向验证） |
| 2026-10-08 | 工作流版本号算成空的 | 版本与可追溯一节：POM 解析命名空间坑；`findtext` 静默返回 None | 实测（三种输入验证 0.1.1.00021/00007/00000） |
| 2026-10-08 | **首次真编**（PR #15 / dev@192a10d，四条腿） | **好消息**：linux-x64 与 macos-arm64 的「构建原生镜像」**成功**——编译期管线（GraalVM + argfile + classpath + `-H:Path/-H:Name` + assembly）通了；**仅编译期**，运行期仍未验证 | 实测（GitHub Actions 作业与步骤结论） |
| 2026-10-08 | 同一轮里四条腿全红，被人问「不是可选吗」 | §1.4：**「可选」要落到步骤级**——作业级 `continue-on-error` 只保运行颜色，作业仍是红叉 | 实测（作业级 → 步骤级 对照） |
| 2026-10-08 | 产物自证红了但看不出原因 | §3：改成**诊断式**（先打现场 / 只留真不变式 / 结论进摘要）；并记三个坑：`set -e` 下 `ls` glob 失败、`恰好 3 个`会漂、Windows `.cmd` 不能直接执行 | 实测（本仓库 + 技能模板同步修正） |
| 2026-10-08 | 无产物时发布作业 `exit 1` 把整次运行染红 | §1.4 第 3 条：**无产物不发版也不失败** | 实测（`native-publish` 的 `整理资产` 步骤） |
| 2026-10-08 | **四条腿全绿，却零产物、零报错**（用户发现「二进制没编出来」） | `troubleshooting` §3「静默跳过」：默认值写在**模块自身**的 `<properties>` 里 → 模块属性赢过父 POM 的 profile 覆盖 → `native.skip` 一直是 `true` | 实测（同一指令：搬到聚合 POM 后本地立刻报 `Cannot run program "native-image"`，证明开关真的生效了） |
| 2026-10-08 | 首次真编报 9 条警告 + 5 条 Recommendations | `args-cookbook` §0：弃用项该删的删、实验性项要解锁、`-H:Path/-H:Name` 改 `-o`；采纳 G1 / future-defaults / 显式堆；**不采纳** PGO(native) / `-march=native` / 混淆，并写明理由 | 实测（真实构建日志逐条处置） |
| 2026-10-08 | 想「不装 GraalVM 也验证命令行」 | `args-cookbook` §0.4：把可执行文件换成 `echo`，构建会打印完整命令行 | 实测（本机验出 `-o` 已生效、classpath 正确） |
| 2026-10-08 | 技能模板 POM 拷过去直接解析失败 | `args-cookbook` §4.5：**模板必须自身良构**——尖括号占位符（三个尖括号包中文）会让整个文件不可解析，改成 `__UPPER_SNAKE__` 并在文件头列含义表；注释里也没有行内注释，别用括号冒充 | 实测（守卫升级为「解析所有 `*.xml`」后当场验证：探针 XML 被点名） |
| 2026-10-08 | 构建报告该不该只留在本地 | `args-cookbook` §0.3：**报告是交付物**——打成独立发布包 + 同传 CI 制品 + 摘要摘关键数字；包内连带参数文件与元数据 | 实测（AHA：`AHA-Desktop-Native-Report-<版本>-<系统>-<架构>-jdk<JDK>.zip`） |
| 2026-10-08 | 报告曾「文档说已交付、包里却没有」（issue #29） | `args-cookbook` §0.3：报告**单独成包、与镜像包同族命名**（版本 / 系统 / 架构 / JDK 轴全在名里）；「workdir 里有报告」不等于「发布物里有报告」——回归守卫要查**发布物** | 实测（AHA issue #29：新增报告包 + 产物自证检查报告包存在） |
| 2026-10-08 | XML 注释里写选项把 POM 改坏（两次） | `troubleshooting` §3 + `args-cookbook` §4.5：注释禁 `--`、禁嵌套；`bin/CheckScripts.py` 加正则守卫（反向验证过） | 实测（守卫当场抓出技能模板里的嵌套注释） |
| 2026-10-08 | 无法判断 native-image 跑没跑 | 用 `<name>.build_artifacts.txt` 作执行痕迹自证；「构建成功」不算验收 | 实测（本次工作流加了「确认 native-image 真的执行过」一步） |
| 2026-10-08 | native 二进制启动即 `ClassNotFoundException: <主类>`（issue #26） | `troubleshooting` §2.2 + `args-cookbook` §3.3：**JavaFX 入口的两处反射必须注册**（`launch(String...)` 的 `Class.forName` 与 `LauncherImpl` 的 `getConstructor().newInstance()`）；`main` 改用 `launch(Class, args)`，并用单测 + 产物自证双层守卫 | 实测（AHA：注册 `AhaDesktopApp` 构造器；`NativeImageMetadataTest` 反向验证过） |
| 2026-10-08 | macOS 腿「构建成功却没上传镜像包」（run 37744912968） | `isolation-and-ci` §3：**`$VAR` 后跟全角字符会被 bash 并进变量名**（`$bin（` → unbound），`set -u` 下退出并**丢掉 outputs**；修法：`${VAR}` + 命中产物立刻写 outputs + `trap 'exit 0' EXIT` | 实测（AHA：macos 镜像包丢失；`CheckScripts.py` 加 YAML 守卫并反向验证） |
| 2026-10-08 | Windows 腿工具链自证 `PLATFORM: unbound variable` | `isolation-and-ci` §3：runner 自带 VS 环境变量与注入名冲突 → 注入名加前缀 + `${VAR:-}` + 只做记录的步骤对失败加 `|| echo` | 实测（run 37744912968） |
| 2026-10-08 | native 二进制下一处 `ClassNotFoundException: com.sun.javafx.tk.quantum.QuantumToolkit`（issue #35） | `troubleshooting` §2.2 + `args-cookbook` §3.3：**JavaFX 启动反射是链式的，要按「启动链路」一次补齐**（工具包 → Glass 工厂 → Prism 管线 → 渲染器 / 着色器 → 字体 / 日志 / 辅助）；且**反射 ≠ JNI**，Glass / 字体还需 `jniAccessible` + 全量方法 / 字段 | 推导 + 同类工程对照（AHA：76 条元数据、单测已反向验证；**真机待验证**→`N-05`） |
| 2026-10-08 | 同类工程的公开配置直接照抄会错（issue #35） | `args-cookbook` §3.3：注册的是**方法签名**不是类名，跨版本后者稳、前者不一定（实测：`loadShader` 实为三参，参考配置写的是两参） | 实测（AHA：JavaFX 25 字节码 `javap` 比对） |
| 2026-10-08 | D3D shader 资源漏进镜像（issue #35 同批发现） | `args-cookbook` §3.1：**着色器扩展名**要进 `-H:IncludeResources`（D3D `.obj`、ES2 `.frag`/`.vert`）；漏掉不是构建失败，是首次绘制静默坏掉 | 推导 + JavaFX jar 扫描（AHA：262 个 `.obj` + 224 个 `.png`） |
| 2026-10-08 | 手写反射清单「看起来补完了」但实际不完整（issue #35） | `args-cookbook` §3.5：**审计要覆盖整个运行时依赖，不只框架**——`sqlite-jdbc` / `log4j-core` 自带 native-image 元数据，而 **Jackson 3 不带**，业务层的配置 / 会话记录会反射读写失败；「启动链路」只是反射的一部分 | 实测（AHA：293 条元数据；Jackson 缺口靠依赖 jar 扫描发现） |
| 2026-10-08 | 想回答「还会不会再报别的类找不到」 | `troubleshooting` §5：**手工清单只能做到「已知缺口已闭」，不能证明完整**；要抄底只能 tracing agent 或真机逐功能跑——把这句话写进交付说明，不要过度承诺 | 自述（据本次用户追问） |
| 2026-10-08 | 同一仓库要编第二个原生目标（CLI，`aha-cli-native`） | `isolation-and-ci` §6：**逐项复制并改名**（模块 / profile / 参数 / 工作流 / tag / 产物），不要合并成一个工作流；**元数据来源会变**——picocli 用 `picocli-codegen` 注解处理器生成（比手写可靠）、JLine 自带但 `Signals` 的 `sun.misc.Signal` 要补 | 实测（AHA：CLI 管线跑通 `native.skip=true`，picocli 生成 30 个类型，单测守卫生成结果） |
| 2026-10-08 | 建第二个目标时反查发现第一个目标漏了资源（yaml/yml） | `args-cookbook` §3.1：资源正则要**逐项对源码里的 `getResourceAsStream` 路径**，别只凭扩展名直觉；桌面端漏了 `yaml`/`yml` → 启动即 `CONFIG_NOT_FOUND` | 实测（AHA：`AhaDefault.yaml` / `ModelDefault.yml`；两份桌面参数已修） |
| ⚠️ 过程反思 | 这次**顺序反了**：先改代码、后补技能 | 违反「踩坑三步：先写技能 → 再改代码 → 记来源」。下一轮起先落条目（哪怕是先写「症状」一行） | 自述（据 `references/skill-lifecycle.md`） |
| 2026-10-09 | 原生桌面包一串「界面不对」：背景发黑、主题不跟随、文字发虚、输入框没轮廓（#48/#49/#153/#229） | 新增「**不崩但表现退化**」一节：判据只能是原生包真机观感；缓解要可回退、决策要能打印 | 实测（`TS-2610-029` / `TS-2610-030` / `TS-2610-046`） |
| 2026-10-13 | FXML 化之后原生包启动即崩，而构建 / 原生构建 / GUI 冒烟**三方全绿**（#133） | 产物自证升级：**在 CI 里真跑一次产物 + 断言正面标记**；并立两条纪律：「守卫必须能证明自己会执行」「能用静态手段拦的不要留给真机」 | 实测（`TS-2610-032`） |
| 2026-10-13 | 原生包 OS / 驱动信息全零（#60） | 产物自证加 `require_jni` 与**可判别指纹**（改前必然出现、改后必须消失）；元数据类问题指向姊妹技能 | 实测（`TS-2610-034`） |
| 2026-10-13 / 10-14 | 两个原生目标都要编，第二个怎么复用 | [isolation-and-ci](references/isolation-and-ci.md) §6 已有「逐项复制并改名」；本条补：**快照线**（谁在什么时候出包）也要按线独立，别共用触发与并发组 | 实测（#183 / #190：tag 词序、目标词与资产前缀同序） |
| 2026-10-14 | 原生 exe 报「Windows 8.0 build 9200」（#151） | 新增「**平台适配：Windows exe 与兼容清单**」一节：清单路径必须绝对（`mt.exe` c1010070）、参数必须在主类之前、平台专属参数单独成文件、`/MANIFEST:EMBED` 不能省 | 实测（`TS-2610-037`） |
| 2026-10-14 | 「加了清单参数但没生效」，CI 仍绿 | 纪律：**有 `continue-on-error` 的步骤要看 `outcome` 而不是 `conclusion`**；「加了参数没生效」与「构建成功」在作业层面长得一样 | 实测（`TS-2610-037`） |
| 2026-10-15 | 打了快照标签却**零运行**（#204 / #165） | 触发层 `on.pull_request.paths` 会把「只改文档 + 新打标签」的显式请求滤掉 ⇒ 门控**下沉到作业层**（并加守卫） | 实测（`TS-2610-040`） |
| 2026-10-15 | 显式请求被自动运行**取消**（#204 复验） | 并发组要**按事件类别分层**：`labeled` 与 `synchronize` 同组时后者会用 `cancel-in-progress` 把前者吞掉 | 实测（`TS-2610-041`） |
| 2026-10-15 | dev 的 JVM 预发行包**停发 4 天**，而每次 run 都报 success（#213） | **静默跳过家族**（`null != false` 恒为假）：门控条件要显式判 `inputs` 是否存在；同类问题一律用「产物存在性」自证兜住 | 实测（`TS-2610-044`） |
| 2026-10-15 | 快照评论跨线陈旧、两条除 marker 外逐字相同（#205） | 评论回写要有**线身份**与可见标题；跨线时写交接说明；回写实现**抽成单一来源** | 实测（`TS-2610-042` / `TS-2610-043`） |
| 2026-10-15 | 想用 Actions 制品做对外载体，匿名下载 401（#165） | 制品不作为对外载体（匿名下载受限、网页链接会失效）⇒ 改用发布页 pre-release 资产 | 实测（`TS-2610-039`） |
| 2026-10-16 | CLI 原生镜像 `--help` 三平台一致失败（#117） | 元数据技能 §5 规则 4：JDK 自带资源**无扩展名**，按扩展名的正则匹不上 | 实测（#233） |
| 2026-10-16 | 原生包在用户机器上「换装失败」：包里没有 helper（PR #243） | 打包描述符的 `<include>` **匹配不到会静默跳过** ⇒ 产物要自带清单（每文件 sha256），并加「描述符引用的手写产物必须存在且被 git 跟踪」的守卫 | 实测（PR #243） |
| 2026-10-16 | 我为一个「一次确认就够」的事加了每次构建都跑的自测门禁 | **纪律**：一次性验证做成真机/一次性确认；只有「**必然复发且复发时静默**」的缺陷才留毫秒级静态守卫 | ACANX 判定（PR #245；已在 PR 中整套撤掉） |

## 验证状态（用这份技能前先看这里）

| 条目 | 状态 |
|---|---|
| 四条隔离（profile / 独立工作流 / tag 命名空间 / 失败传播） | **已在 AHA 实测** |
| `-am` 不带 profile 模块上游、坐标误报「中央仓库找不到」、负缓存 | **已在 AHA 实测** |
| 空壳 jar 排除、拷贝不删旧文件、POM 命名空间解析为空 | **已在 AHA 实测** |
| 版本号 `<基线>.<PR 补零>` 推导 | **已在 AHA 实测** |
| 依赖拷贝 → classpath 抽取 → 参数文件 → 打包的整条管线 | **已在 AHA 实测**（`-Dnative.skip=true`） |
| **native-image 能编出二进制**（编译期） | **已验证**：2026-10-08 首次真跑（linux-x64 / macos-arm64 成功），此后 Windows 腿也跑通并常态化出包 |
| Windows 腿的工具链自证（`native-image.cmd`） | **已验证**：bash 不能直接执行 `.cmd`（要 `cmd //c`）；修法已合入并在后续运行中确认 |
| 容器/依赖清单的「真不变式」 | **已踩坑**：`恰好 N 个` 会随传递依赖漂移（不代表坏）；真正的不变式是「三件套齐全 + 无 0 KB 空壳」 |
| **运行期行为**（能开窗、能启动、元数据是否齐） | **已验证（真机 + CI）**：原生包在真机被反复使用（#46/#48/#49/#60/#133/#151/#153/#227/#229 都是真机反馈）；`DesktopNative.yml` 的 Windows 腿另在 CI 里**真跑一次产物**并断言正面标记 |
| **运行期行为 · 真跑覆盖到哪一层** | **已在 CI**：CLI 线**三平台**都执行产物（`--version` / `--help`）；桌面线**Windows 腿**启动 GUI exe 并断言正面标记 |
| **运行期行为 · 仍缺自动化的部分** | **明细**：① 原生包里的**交互**仍无自动化覆盖（`ui-smoke` 跑的是 JVM 模式，且是**非必需检查**）；② 桌面线的 Linux / macOS 原生包只做产物自证，**没有真跑**（GUI 需要显示环境）；③ 触摸 / 笔手势的「手感」在本环境**不可达**；④ LCD 次像素渲染**不可达标**（原生退化为灰度是既定差异） |
| 平台适配（Windows 兼容清单） | **已验证**：#151 修好后，exe 的 `GetVersionEx` 返回真实版本，CI 的 Windows 原生冒烟有可判别断言（`Windows version 10.` 必须命中、`8.0 build 9200` 不得出现） |
| 「不崩但表现退化」这一类 | **已知且部分缓解**：字体渲染（灰度兜底，未根治）、CSS 函数失效（字面量补丁表）、边框丢失（属性 API 补兜底）——见「不崩但表现退化」一节 |
| 首次真编 → 真机可运行 → 在别的项目复用一次 | **前两项完成**（真编 + 本仓库真机长期在用）；「**在别的项目复用一次**」仍未做 ⇒ 这决定它能否算「标杆参考」（见 metadata.maturity） |

这条「验证状态」是技能的一部分，不是免责声明：原生镜像的参数清单与平台行为随
GraalVM 版本变化很快，**每一份参数清单都应当在你的项目上重新验证一次**。

## 复用清单（搬到别的项目要改哪几处）

1. 模块名与坐标（父 POM 与子模块的 groupId 可能不同，见坑 4）；
2. 主类名与主模块 artifactId；
3. 依赖里的「框架三件套」名称（JavaFX → 其它 GUI 框架、或数据库驱动、或 Web 框架）；
4. 参数文件里的 `--initialize-at-run-time` 与 `-H:IncludeResources`（**必须**按本项目重新试）；
5. 工作流的矩阵（本机架构、是否包含实验腿）与 tag 前缀；
6. 产物自证里的「关键依赖数量」阈值。

## 升级路线

- GraalVM 升级后：先只换 JDK/工具链版本跑一遍，**参数不动**，看哪些条目失效；
- 参数清单按「JDK 轴」分文件（如 `native-image-args-jdk25.txt` / `…-jdk27.txt`），
  两份文件的 diff 就是实验记录；
- 每次实验把结论写回 [measurement](references/measurement-and-experiments.md) 的对比表，
  避免下次重新猜。

## 参考

- [args-cookbook](references/args-cookbook.md)：native-image 参数速查与逐条解释
- [troubleshooting](references/troubleshooting.md)：按症状定位（构建期 / 运行期）
- [isolation-and-ci](references/isolation-and-ci.md)：四隔离的落地骨架与 CI 编排
- [measurement-and-experiments](references/measurement-and-experiments.md)：启动速度与内存的测量口径与实验设计
- [skill-lifecycle](references/skill-lifecycle.md)：这份技能自身的迭代方法、状态标记与迁移清单
- [GraalVMInActionsGuide.md](../../../Docs/Guide/GraalVMInActionsGuide.md)：**本仓库的「GraalVM + GitHub Actions」参考**——Action 输入速查（抄自 `action.yml`）、权威查询入口与本地自证命令（`native-image --help-extra` 等）、症状→参数对照表
- 本仓库的排障档案（读症状比读参数快）：
  [Windows exe 兼容清单](../../../Docs/Troubleshooting/TS-2610-037-Windows-Exe-Manifest-For-OS-Version.md)、
  [原生下 CSS 函数失效](../../../Docs/Troubleshooting/TS-2610-029-Modena-CSS-Function-Failure.md)、
  [字体渲染差异](../../../Docs/Troubleshooting/TS-2610-030-Native-Font-Rendering-Diff.md)、
  [边框丢失](../../../Docs/Troubleshooting/TS-2610-046-Input-Outline-Lost-In-Native.md)、
  [FXML 漏 import 而三方全绿](../../../Docs/Troubleshooting/TS-2610-032-FXML-Missing-Import-And-Smoke-Blind-Spot.md)、
  [D3D 驱动信息全零](../../../Docs/Troubleshooting/TS-2610-034-Prism-D3d-Driver-Info-Zero-Values.md)、
  [快照标签零运行](../../../Docs/Troubleshooting/TS-2610-040-Trigger-Level-Paths-Filter-Blocks-Explicit-Requests.md)、
  [发布被静默跳过](../../../Docs/Troubleshooting/TS-2610-044-Publish-Skipped-By-Null-Bool-Compare.md)
- **[graalvm-reachability-metadata](../graalvm-reachability-metadata/SKILL.md)**（姊妹技能）：
  元数据的**发现 / 登记 / 验证 / 守卫**专精（反射 / JNI / 资源 / 初始化），
  含三路发现法、来源优先级与常见框架经验库 —— 本技能只管「从 jar 到原生二进制」的工程化

## 模板资产

- [assets/module-pom.xml](assets/module-pom.xml)：原生模块 POM 骨架（隔离 + 拷贝 + exec + 打包）
- [assets/native-image-args.txt](assets/native-image-args.txt)：参数起点（含四类清单的种子）
- [assets/workflow-native.yml](assets/workflow-native.yml)：多平台原生镜像工作流骨架
