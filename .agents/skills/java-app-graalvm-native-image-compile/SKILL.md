---
name: java-app-graalvm-native-image-compile
description: 把已能跑的 Java jar 模式程序编译成多操作系统、多架构的 GraalVM Native Image 原生可执行文件。覆盖隔离式落地（profile + 独立工作流 + 独立 tag 命名空间）、依赖与 classpath 准备、反射/资源/JNI/初始化四类清单、JPMS 与 classpath 的取舍、平台分类器与空壳 jar 陷阱、产物自证、按 PR 号的可追溯版本号，以及启动速度与内存占用的对比实验方法。当需要为含 GUI、JPMS、JNI、反射的复杂项目引入原生镜像构建，或排查 native-image 构建与运行期失败时使用。
license: Apache-2.0
compatibility: 需要 GraalVM（含 native-image）；构建必须在目标平台上执行（native-image 不能交叉编译）；JavaFX 等带原生库的 GUI 框架需要对应平台的分类器工件；若项目启用 JPMS，classpath 模式可绕开模块约束。
metadata:
  version: "0.1.0"
  owner: ACANX
  source-project: AHA（复杂场景试验场：JavaFX + JPMS + JNI + 反射 + 多平台分类器）
  maturity: 试验中（含「推断待验证」条目；达标判据见「用法：开工前就建，边做边改」一节）
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

## 四类清单（原生镜像的全部难点都在这四类）

| 类别 | 症状 | 处理方向 |
|---|---|---|
| **初始化时机** | 构建期报错「构建时初始化失败 / 触碰了运行期才有的东西」 | `--initialize-at-run-time=<类或包>`（GUI、驱动、日志框架是重灾区） |
| **反射** | 构建通过但**运行期** `ClassNotFoundException` / `NoSuchMethodException` | `reflect-config.json` / `--initialize-at-run-time` / 改用显式注册 |
| **资源** | 运行期缺图标、模板、`properties`、`META-INF/services` | `-H:IncludeResources=<正则>` |
| **JNI / 原生库** | 运行期 `UnsatisfiedLinkError` | 让原生库作为资源进镜像 + `--enable-native-access` + 运行期初始化本地加载器类 |

逐条的症状到修法对照，见 [troubleshooting](references/troubleshooting.md)；
参数清单与「哪些必须有、哪些是实验位」，见 [args-cookbook](references/args-cookbook.md)。

## 产物自证（不要只看「构建成功」）

构建成功与「能跑」是两件事。CI 里至少自证四项：

1. 可执行文件存在（注意 Windows 上带 `.exe`，其它平台没后缀）；
2. **体积下限**（原生镜像把 JDK 编进去了，异常小说明只编出一个壳）；
3. **平台魔法数**（PE `4d5a` / ELF `7f454c46` / Mach-O `cffaedfe`）——
   确认拿到的确实是本平台的二进制，而不是错平台的产物；
4. **关键依赖数量**（例如「恰好 3 个带分类器的 GUI 框架 jar」），
   用来把「空壳混进来 / 依赖漏了」这两类问题挡在发布之前。

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
| ⚠️ 过程反思 | 这次**顺序反了**：先改代码、后补技能 | 违反「踩坑三步：先写技能 → 再改代码 → 记来源」。下一轮起先落条目（哪怕是先写「症状」一行） | 自述（据 `references/skill-lifecycle.md`） |
| 待补 | 三平台首次真机运行（双击可开窗 / 能对话） | 运行期清单（`--initialize-at-run-time`、JavaFX 原生库、SQLite） | 待做（`N-05`/`N-06`） |

## 验证状态（用这份技能前先看这里）

| 条目 | 状态 |
|---|---|
| 四条隔离（profile / 独立工作流 / tag 命名空间 / 失败传播） | **已在 AHA 实测** |
| `-am` 不带 profile 模块上游、坐标误报「中央仓库找不到」、负缓存 | **已在 AHA 实测** |
| 空壳 jar 排除、拷贝不删旧文件、POM 命名空间解析为空 | **已在 AHA 实测** |
| 版本号 `<基线>.<PR 补零>` 推导 | **已在 AHA 实测** |
| 依赖拷贝 → classpath 抽取 → 参数文件 → 打包的整条管线 | **已在 AHA 实测**（`-Dnative.skip=true`） |
| **native-image 能编出二进制**（编译期） | **已踩坑→已验证（部分平台）**：2026-10-08 在 AHA 首次真跑，**linux-x64 与 macos-arm64 编译成功**（win-x64 卡在工具链/`.cmd`，见 `references/troubleshooting.md`）。 |
| Windows 腿的工具链自证（`native-image.cmd`） | **已踩坑**：bash 不能直接执行 `.cmd`，要 `cmd //c`。修法已合入，**待下一次运行确认** |
| 容器/依赖清单的「真不变式」 | **已踩坑**：`恰好 N 个` 会随传递依赖漂移（不代表坏）；真正的不变式是「三件套齐全 + 无 0 KB 空壳」 |
| **运行期行为**（能开窗、能对话、GUI 原生库是否完整进镜像、体积与启动表现） | **尚未验证**：还需在真机上下载二进制跑一遍。把这部分当成**起点**，不要当成结论 |
| 首次真编 → 三平台真机可运行 → 在别的项目复用一次 | **部分完成**：首次真编已在 Linux/macOS 达成；剩「三平台真机可运行」与「在别的项目复用一次」（见 metadata.maturity） |

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

## 模板资产

- [assets/module-pom.xml](assets/module-pom.xml)：原生模块 POM 骨架（隔离 + 拷贝 + exec + 打包）
- [assets/native-image-args.txt](assets/native-image-args.txt)：参数起点（含四类清单的种子）
- [assets/workflow-native.yml](assets/workflow-native.yml)：多平台原生镜像工作流骨架
