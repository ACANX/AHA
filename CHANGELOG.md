# 变更日志

本文件记录 AHA 项目的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

> 0.1.0 是首个版本，即项目基线，因此**该段落**只包含「新增」——
> 所有能力均以最终形态描述，不记录开发过程中的调整。

## [0.1.1] - 2026-10-08

**无用户可见的功能变更**：版本号由 0.1.0 切到 0.1.1；本版集中修正版本号清单、发布流程与
**按平台出包的机制**，为 0.2 桌面端的发布做准备。

### 新增
- **桌面端原生镜像（试验性）**：新增 `aha-desktop-native` 模块与 `.github/workflows/DesktopNative.yml`，
  把 `aha-desktop` 的 JVM 产物再编译成 GraalVM native-image 二进制（win / linux / macos，解压即可双击运行）。
  定位是「验证线」：正式交付仍是 JVM 模式。
  - **四条隔离**：模块只在父 POM 的 `desktop-native` profile 里（默认不在反应堆）；
    独立工作流与作业名；tag 用 `native-v…`（不匹配 `V*`/`v*`，不触发正式发版）；
    `fail-fast: false` + 实验腿 `continue-on-error` + 发布作业 `if: always()`。
  - **每次合并到 dev 自动出包**：版本按 `a.b.c.PPPPP`（PR 号补零到 5 位，如 PR21 → `0.1.1.00021`），
    基线读根 POM、PR 号按提交反查 API；发布为预发行版，可在发布页直接下载。
  - **JDK 25 / JDK 27 两条轴**：`native-jdk27` profile 切换参数文件与产物名，
    **目前只开 Windows**；用于对比 Project Leyden 的 AOT、原始类型预览与 GC 策略对
    启动速度 / 内存占用的影响，为明年适配 JDK 29 铺路。
  - **产物自证**：工作流检查产物存在、体积下限、平台魔法数（PE / ELF / Mach-O）、
    classpath 恰好含 3 个带分类器的 OpenJFX jar；包内附带两份构建参数文件便于事后对账。
- **CLI 原生镜像（试验性）**：新增 `aha-cli-native` 模块与 `.github/workflows/CliNative.yml`，
  把 `aha-cli` 的 JVM 产物再编译成 GraalVM native-image 二进制（win / linux / macos，终端直接可跑）。
  与桌面端是**平行**关系：同一套隔离 / 版本 / 自证方法论，独立模块、独立工作流、独立参数文件，
  两者互不牵连。差异在：无 JavaFX、主类为 `AhaCli`、GC 默认用 serial（短命进程）。
  - **picocli 反射元数据用注解处理器生成**：picocli 不自带 native-image 元数据，本项目在 `aha-cli`
    编译期用 `picocli-codegen` 生成 `META-INF/native-image/picocli-generated/reflect-config.json`
    （实测 30 个类型），比手写清单可靠——子命令 / 选项一变，生成结果跟着源码走；
    `NativeImageMetadataTest` 直接断言生成结果，处理器失效时 Build 阶段即红。
  - **补 JLine 的元数据缺口**：JLine 4 自带元数据，但未覆盖 `org.jline.utils.Signals` 的
    `Class.forName("sun.misc.Signal")`，由本项目元数据补齐（另含 AHA 自身被 Jackson 读写的记录）。
  - **同批修复桌面端参数的一个潜在缺陷**：资源正则漏了 `yaml` / `yml` / `svg`，
    会导致原生镜像启动即报 `CONFIG_NOT_FOUND`（内置 `AhaDefault.yaml` / `ModelDefault.yml` 缺失）；
    两份桌面参数文件已一并修正。
  - 试验性 / 非交付物：真机验证见 `TODO.md` `N-19`。
- **可复用技能 `java-app-graalvm-native-image-compile`**（`.agents/skills/`）：把本项目在
  「JavaFX + JPMS + JNI + 反射 + 多平台分类器」这一复杂场景下编译原生镜像的经验沉淀成技能 ——
  隔离四条、四类清单（初始化时机 / 反射 / 资源 / JNI）、按症状排错、产物自证四项、测量口径，
  外加可拷贝的 POM 骨架、参数起点与工作流骨架。
  技能按「开工前建骨架、边做边改、达成目标才成熟」的方式维护：条目带**状态**（推断 / 已踩坑 /
  已验证 / 已定稿）与**来源**，未验证部分显式标注；当前 `0.1.0`（试验中），
  成熟判据见技能内「用法」一节（含「在别的项目复用过一次」）。
- **可复用技能 `graalvm-reachability-metadata`**（`.agents/skills/`）：把原生镜像的**元数据登记**
  （反射 / JNI / 文件资源 / 运行期初始化）从桌面端与 CLI 两轮实践里抽成**独立的纵向专精技能**：
  三条互补的发现路径（字节码静态审计 / tracing agent / 经验库对照）、来源优先级
  （依赖自带 > 框架生成 > 手写）、精确到方法签名的登记写法，以及四层守卫
  （单测断言 / 产物自证 / 反向验证 / 真机走查 → tracing agent 终局），
  并附常见框架经验库（JavaFX / picocli / JLine / Jackson / sqlite / log4j 等，区分「自带元数据 / 不带」）
  与可复制的元数据模板。与 `java-app-graalvm-native-image-compile`（工程化全流程）互补、互相引用，
  当前 `0.1.0`（试验中）。
  - **tracing agent 采集已制度化**：新增 `references/agent-collection.md`（何时必须跑、自检模式契约、
    多平台合并、过滤规则、CI 接入）与 `scripts/collect-metadata.sh`（`run` / `summarize` / `filter`）；
    并用它对 CLI 与桌面端各采一轮——桌面端据此补进 47 条应用栈元数据（293 → 340）。
- **桌面端 0.2 六项功能**（按用户指定顺序）：
  1. **工具卡片**：默认折叠；展开显示参数与带行号输出（前 200 行并写明总行数）；复制 / 查看全部；
     失败卡片红边、正文摊开并给「重试 / 改参数后重试」——重试交给模型判断，不在本地偷偷重放命令。
     结果行口径与 CLI 一致（不足 100ms 不显示耗时），耗时由注入的计时源保证可确定断言
  2. **会话列表**：左栏列出 SQLite 里的真实会话（标题取用户重命名或首条用户消息、消息数、
     相对时间），当前会话绿点加粗；点选即回放历史（只读，不重新推理）；
     右键重命名 / 导出 Markdown / 导出 JSON / 删除（不可恢复的操作二次确认）
  3. **日志面板**：往 log4j2 根记录器挂内存 appender，实时日志 + 级别过滤；
     过滤在读的时候做，把门槛调回 DEBUG 仍能看到之前的记录
  4. **输入区**：`/` 命令面板（命令名与 CLI 同名，只登记真能执行的命令；命令在本端执行、
     不发给模型）、`@` 文件引用补全（跳过 `.git`/`target`/`node_modules` 等噪音目录）、
     `↑`/`↓` 历史输入（不丢草稿）、`Tab`/`Enter` 接受候选
  5. **授权弹窗**：权限人话说明、完整参数（执行类即完整命令）、按钮上写出被放宽的权限名、
     默认焦点在「拒绝」且 `Esc` 即拒绝、列出本会话已自动允许的权限；
     新增「工具 → 全部撤销本会话授权」（`Ctrl+Shift+R`）
  6. **主题与设置**：暗色 / 亮色 / 跟随系统（换主题会把已建节点一起重刷，不是只换新控件）、
     字号；设置存 `AHA_HOME/desktop.properties`；三条切换路径（`/theme`、视图菜单、设置面板）等价
- **检查守卫两项**：`bin/CheckScripts.py` 新增「源码文件不得被 `.gitignore` 吃掉」；
  `bin/CheckDocs.py` 新增「残留合并冲突标记」。两者都做过反向验证（该拦时拦住、清理后全绿）

### 变更
- **修「全绿却没上传」的真因**：带 `continue-on-error` 的步骤一旦非零退出，
  它的 `outputs` **不会被发布**，下游 `if:` 静默变 false——macOS 腿的「改名 / 上传制品」
  因此被跳过（作业仍是绿的）。修法：诊断步骤开头先写兜底 `produced=false`、
  结尾强制 `exit 0`；执行证据判据从 `*.build_artifacts.txt`（macOS 不产出）改为 `*build-report.*`。
  同时修正构建报告通配——真实文件名是 `<可执行名>-build-report.html`（34~36 MB），
  按 `build-report*` 写会静默漏掉（报告因此一直没进制品）。
- **构建报告作为独立发布物**：native-image 的构建报告**不打进镜像包**
  （镜像是给人运行的，报告是给人分析的），由工作流单独打成
  `AHA-Desktop-Native-Report-<版本>-<系统>-<架构>-jdk<JDK>.zip`，
  与镜像包**同族命名、同批挂到发布页**，并作为 CI 制品保留 90 天。
  包内含报告 + 镜像参数 + 可达性元数据；产物自证会检查报告包是否真的生成，
  并把报告里的关键数字摘进 Job Summary。
- **按首次真编日志调优 native-image 参数**：删除已弃用且无效的 `no fallback` 开关；
  解锁实验性选项；输出由 `-H:Path` + `-H:Name` 改为 `-o`；
  采纳日志建议的 `--gc=G1`、`--future-defaults=all`、`-R:MaxHeapSize=1g`；
  新增 `emit build report`（打成独立报告包交付，作为后续调参依据）；
  两份参数文件（JDK 25 / JDK 27）逐条同步且保持行序一致。
  `--enable-url-protocols` 属「静默坏掉」风险项，保留但写明退出路径（`TODO.md` N-14）。
- **XML 注释守卫**：`bin/CheckScripts.py` 新增 `check_xml`——注释体禁 `--`、禁嵌套注释
  （本轮两次踩到，且守卫当场抓出技能模板里的嵌套注释）。
- **修复原生镜像「静默跳过」**：`native.skip` 的默认值原先写在模块自身的 `<properties>` 里，
  会**赢过**父 POM 中 profile 的覆盖，导致 `native-image` 被静默跳过——
  构建成功、零产物、零报错、CI 全绿。现改为：默认值唯一来源放聚合 POM 的 `<properties>`，
  同一 POM 的 profile 覆盖为 `false`，CI 再显式传 `-Dnative.skip=false` 兜底，
  并新增「确认 `native-image` 真的执行过」（用 `<name>.build_artifacts.txt` 作执行痕迹）
  与产物缺失时的点名告警。规则写进 `BuildSpec.md`（「构建成功不是验收标准」）与技能。
- **原生镜像管线改为真「可选」**：容错从作业级下沉到**步骤级**
  （作业级 `continue-on-error` 只保证整次运行不变红，作业本身仍显示失败），
  产物自证改为**诊断式**（先打现场、判据只留真不变式、结论进 Job Summary），
  无产物时发布作业既不发布也不失败。修三个具体缺陷：Windows 上 `.cmd` 不能由 bash 直接执行、
  GraalVM 的 JDK 27 尚未发布、无制品时 `exit 1`。实测同轮 linux-x64 与 macos-arm64 的
  原生编译**成功**，说明整条管线是通的。
- **构建契约明确化**：默认构建（不带 profile）只保证「能编译、能打 jar、不报错」；
  工具链 / 平台相关的产物（原生镜像）**只在专用 profile 下**构建，
  且内层开关默认关闭（`native.skip` 默认 `true`，由 `-Pdesktop-native` 置为 `false`）。
  实测默认 `clean package`：反应堆 7 个模块、native 相关日志 0 行、原生模块 `target/` 未被触碰。
  规则写进 `BuildSpec.md` §7 与 `DesktopNativeDesign.md` §2.0。
- **构建产物统一落 `Dist/`**：桌面端便携包与原生镜像包不再输出到仓库根，
  与 CLI 发行包一起放进 `Dist/`（已在 `.gitignore` 里）；
  CLI 的 zip 改由工作流在临时目录打好后移入，避免把正在写入的归档自身收进去。
  规则写进 `BuildSpec.md` §7。

### 修复
- **原生桌面镜像主题不跟随系统、切主题有残留、暗色标题对比度不足（issue #44）**：原生桌面端能开窗后暴露四类界面问题，本次修掉三类确定性缺陷。
  ① **默认主题恒为暗色**：`SystemTheme.prefersDark()` 在系统配色读取失败或尚未就绪时一律回退暗色；
  改成三级回退（明确配色 → 系统背景色亮度 → 亮色），并新增 `SystemTheme.onColorSchemeChanged`，
  让「跟随系统」在用户切换系统深色 / 浅色皮肤时实时生效。
  ② **切换到亮色后部分组件仍是暗色**：3 处边框硬编码 `#3A3A3A`、1 处背景硬编码 `#1E1E1E`
  改为读 `Palette.BORDER` / `Palette.BASE`；常驻的候选弹层（`CompletionPopup`）与会话右键菜单
  （`ContextMenu`）补上主题重刷（弹层有独立的场景根，不继承主窗口样式）。
  ③ **暗色下标题文字对比度不足**：右栏「本轮」标题与左右折叠按钮此前未登记主题，
  改用默认深色文字；现统一走 `themed(...)` 登记。
  遗留：**原生镜像与 JVM 模式的字体清晰度 / 字体差异**未解决（见 `Docs/TODO.md` 的 `N-24`），
  需真机对比渲染管线后才能定位。详见 `Docs/DevLog/DevLog-20261009-08.md`。
- **原生桌面镜像能开窗但控件画不出：Prism 效果 peer 的动态类名未登记（issue #41）**：修完 #35/#37/#39 后原生桌面端首次进到 GUI，
  但渲染到第一个用阴影效果的控件时反复报 `Could not create peer LinearConvolveShadow for renderer
  com.sun.scenario.effect.impl.prism.ps.PPSRenderer`，界面无控件可画。根因与 #35 同源：
  `Renderer.getPeerInstance` 用**动态拼接的类名**反射加载效果 peer
  （`Class.forName(rootPkg + ".impl.prism.ps.PPS" + name + "Peer")`，另含 `prism.Pr*` 与软件回退
  `sw.java.JSW*` / `sw.sse.SSE*`），closed-world 静态分析看不到，而之前只登记了渲染器工厂与 stock shader，
  **未登记 peer 本身**。修法：对 javafx-graphics 25 的 jar 扫 `com/sun/scenario/effect/impl/**/*Peer`，
  过滤 abstract 后把 **99 个具体 peer** 全部登记进 `reachability-metadata.json`（340 → 439 条）。
  守卫 `NativeImageMetadataTest` 11 → 12 条（已反向验证），产物自证第 ⑧ 条补 peer 检查。
  详见 `Docs/DevLog/DevLog-20261009-06.md`。
- **原生桌面镜像 Windows 启动即崩：平台子类的 JNI 成员查找未登记（issue #39）**：修完 #37（JNI 可达**类**）后，
  真机在 `WinWindow.<clinit>` 报 `NoSuchMethodError:
  com.sun.glass.ui.win.WinWindow.notifyMoving(IIIIFFIIIIIII)[I`。根因：`WinWindow._initIDs` 用
  `GetMethodID(cls, …)` 查的是「Java 传入的 `jclass`」——即平台子类**自己声明**的方法；
  而 #37 的 `FindClass` 扫描只会看到 native 里写成字面量的类名，平台子类从不经 `FindClass`，
  因此从未入册（只在册的基类 `Window` 与它继承的方法能查到）。修法：改扫 openjfx 三平台
  native 源码里所有 `Get*ID` 的**目标类**，把 Windows / macOS 的平台实现类逐类补进
  `jni-config.json`（62 → 85 条），并顺带补上 `Class.forName` 现查的 `WinDnDClipboard` / `EventLoop`。
  守卫 `NativeImageMetadataTest` 10 → 11 条（已反向验证），产物自证第 ⑧b 条补 4 项。
  详见 `Docs/DevLog/DevLog-20261009-05.md`。
- **原生桌面镜像启动即 segfault：JNI 可达类未注册（issue #37）**：修完 #35（JavaFX 启动链路反射）后，
  真机在 Glass 初始化处报 `NoClassDefFoundError: java/lang/Runnable`，随后段错误。
  根因是 native-image 只允许「JNI accessible」的类被 `FindClass` 查到——反射元数据解决「类可达」，
  不解决「JNI 可达」。修法：新增 `jni-config.json`（62 个类，来自对 openjfx 三平台 native 源码
  506 个文件里所有 `FindClass` 的静态扫描），并在单测与产物自证里加守卫。
  详见 `Docs/DevLog/DevLog-20261008-15.md`。
- **原生镜像管线两个跨平台缺陷（run 37744912968）**：
  ① macOS 腿的产物自证里 `$bin（` 被 bash 当成变量名 → `set -u` 下
  `bin（: unbound variable` 退出，步骤 outputs 丢失，下游改名/上传被静默跳过——
  **macos 镜像包因此没传上发布页**。修法：变量后跟全角字符一律写 `${VAR}`；
  命中产物后**立刻**写 `produced=true`，并加 `trap 'exit 0' EXIT` 兜底。
  ② Windows 腿的工具链自证里 `PLATFORM` 与 runner 自带的 VS 环境变量冲突 →
  `unbound variable` 退出。修法：注入名改 `LEG_PLATFORM` / `LEG_LABEL` / `LEG_VERSION`，
  一律用 `${VAR:-}` 读取，只做记录的命令失败不判死。
  顺带修：魔法数只比前 2 字节（原先 Windows 期望 `4d5a`、实际 `4d5a9000`，永远不匹配）、
  去掉 `find -maxdepth`（macOS BSD find 不支持），并把 `Release.yml` 同写法一并修正。
- **跨平台 shell 守卫**：`bin/CheckScripts.py` 新增 YAML 检查——`$VAR` 后紧跟非 ASCII
  且未用 `${}` 就报错（已反向验证）；同一条规则不再靠人盯。
- **原生镜像桌面端启动链路反射 / JNI 缺失（issue #35）**：修完 #26 后，二进制下一处报
  `ClassNotFoundException: com.sun.javafx.tk.quantum.QuantumToolkit`——JavaFX 的工具包、
  Glass 平台工厂、Prism 渲染管线都用 `Class.forName` + `getDeclaredConstructor().newInstance()`
  这类反射加载，native-image 的 closed-world 看不到。经对 4878 个 JavaFX 类逐个反编译审计后，
  把反射与 JNI 清单一次性补进 `reachability-metadata.json`（后经 tracing agent 采集补齐至 **340 条**）：工具包与日志 / 反射辅助类、
  三平台 Glass 工厂、四条 Prism 管线、效果渲染器、ShaderSource 与**全部 212 个** stock shader 加载器、
  Glass 原生回调（`jniAccessible`）、图片解码、字体（DirectWrite / CoreText / FreeType）；
  审计同时发现 **Jackson 3 不随附 native-image 元数据**，而配置与会话记录靠它反射读写，
  因此一并注册了 13 个配置记录与 `TaskRequest` / `TaskResult` / `ToolCall`。
  另把 `-H:IncludeResources` 补上 D3D 的 `.obj` 与 ES2 的 `.frag` / `.vert` 着色器资源
  （漏掉不会构建失败，而是首次绘制时静默坏掉）。
  `NativeImageMetadataTest` 扩到 7 条断言（已反向验证），DesktopNative 产物自证第 ⑧ 条改为
  逐类检查启动链路。**诚实说明**：这是手工推导的「已知缺口已闭」，不等于「证明完整」——
  真机逐功能验证仍待 `N-05` / `N-16`，抄底方案（tracing agent）登记为 `N-17`。
- **原生镜像桌面端启动即崩（issue #26）**：`aha-desktop-native.exe` 一启动就报
  `ClassNotFoundException: com.acanx.module.aha.desktop.AhaDesktopApp`——JavaFX 入口有两处反射：
  `Application.launch(String...)` 用 `Class.forName` 加载主类，`LauncherImpl` 又用
  `getConstructor().newInstance()` 实例化它，而 native-image 的 closed-world 看不到。
  现补上 `reachability-metadata.json` 的主类构造器注册，`main` 改为显式
  `launch(AhaDesktopApp.class, args)`；新增 `NativeImageMetadataTest` 在 Build / Gate 阶段守住
  注册，DesktopNative 产物自证再查一次构建产物里的元数据。
- **原生镜像缺独立构建报告（issue #29）**：`dist-native.xml` 原先排除
  `<可执行名>-build-report.html`，而文档已把它记为「已随包交付」，两边对不上——
  发布页上既看不到包内报告，也没有独立的报告发布物。现定案：报告**不进镜像包**，
  改由工作流打成独立发布包
  `AHA-Desktop-Native-Report-<版本>-<系统>-<架构>-jdk<JDK>.zip`（版本、系统、
  架构、JDK 轴全在文件名里，与镜像包一一对应），并在产物自证里新增
  「报告包是否真的生成」的回归守卫——「workdir 里有报告」不等于「发布物里有报告」。
- **`.gitignore` 静默吃掉源码**：不带前导斜杠的 `Log/` 在任意层级匹配，且在 Windows / macOS
  大小写不敏感，于是 `aha-desktop/.../desktop/log/` 的 8 个源文件从未进入版本控制——
  git 不报错、`git add -A` 静默跳过、`git status` 显示干净、本地测试全绿，
  只有 CI 报 `cannot find symbol`。运行期目录已锚定到仓库根（`/Data/` `/Key/` `/Log/` `/Model.yml`）
- **残留的合并冲突标记**：`.github/workflows/CodeQL.yml`、`Docs/DevSpec/BuildSpec.md`、
  `Docs/TODO.md` 里共 11 处标记已入库，其中 `CodeQL.yml` 因此一直是**无效工作流**；
  已逐处解析（CodeQL 行动升到 v4，文档版本取新）
- `InputHistory.next()` 复位顺序错误会丢掉用户正在输入的草稿
- `FileMentions.insert()` 在光标位于片段之后时会替换错位置
- `LogCapture` 卸载后重新安装会静默失效（判重条件问错了对象）
- **主题应用顺序**：主题原来在界面建好之后才应用，只能靠逐个重刷已建节点；一旦漏掉某个控件
  就会出现「日志说生效亮色、界面还是暗的」。改为启动时**先定色表再建界面**，
  并把生效主题与实际色值写进日志自证
- **自动打 tag**：`Build.yml` 新增 `tag` 作业——`dev` → `main` 的 PR 合并、且构建成功后，
  按根 `pom.xml` 的 `<version>` 创建 `V<版本号>` 附注 tag 并推送（同名已存在则跳过）；
  版本号只从 POM 读，工作流与文档不复制。注意：`GITHUB_TOKEN` 推的 tag 不会触发下游工作流，
  故 `Release.yml` 需手工触发或改用 PAT（见 [ReleaseProcess.md](Docs/DevSpec/ReleaseProcess.md) §4.2）
- **桌面端按平台出包**：新增 `aha-desktop/src/assembly/dist-desktop.xml`，产出便携包
  `aha-desktop-<版本>-<系统>-<架构>.zip`（`bin/` 启动脚本 + `lib/` 本项目模块与本平台 OpenJFX
  原生库），文件名由构建期真实解析结果决定；`Release.yml` 新增 `desktop` 作业按
  `ubuntu` / `windows` / `macos` 矩阵出包，由产物名 + 依赖树**双向自证**平台分类器后上传到
  release 页面。桌面端启动脚本 `bin/AhaDesktop.{sh,bat}` 随包分发；`aha-desktop` 另声明
  `aha-tool`，桌面端由此具备内置工具
- **桌面端三栏骨架**：`DesktopShell` 搭出菜单栏 + 左 220 / 中弹性 / 右 280 + 底部 24px 状态栏；
  左右两栏可折叠，菜单（视图）、快捷键（`Ctrl+B` / `Ctrl+J`）与栏边**常驻窄条按钮**三条路径等价
  ——折叠后窄条仍在，鼠标用户随时能把栏展回来；折叠状态抽成不依赖 JavaFX 的 `FoldState`，
  使「折叠→展开」的往返在无图形环境下也能被测试钉住；输入区支持 Enter 发送 /
  Shift+Enter 换行，发送只落到本地消息流并明确提示「尚未接入 Agent」
- **桌面端窗口骨架与线程桥接契约**（0.2 的第一步）：`AhaDesktopApp` 改为 `Application` 子类，
  开窗并显示版本号；新增 `desktop/fx` 桥接契约（`FxDispatcher` / `PlatformFxDispatcher` /
  `FxBridge`），把高频后台回调合并成每帧至多一次界面更新；`FxThreadContractTest` 扫描主源码
  钉住「只有 `PlatformFxDispatcher` 可触碰 JavaFX 线程 API」；窗口冒烟测试默认跳过
  （需图形环境），用 `-Daha.ui.tests=true` 显式开启
- **构建**：`.gitignore` 补充本地工具的项目索引 `.xcodemap/` 与 `versions-maven-plugin` 的备份产物 `pom.xml.upgraded`，二者不入库

- **Windows 平台**：修复三处只在 Windows 暴露的缺陷——`--help` 在非交互场景混入 ANSI 转义序列；
  `Llm.ModelFile` 的未展开占位符被直接当作路径（`Illegal char <:>`）；项目级身份查找没有边界，
  会一路走到用户主目录把 `~/AHA.md` 当成项目级身份（临时目录位于主目录之下，故仅在 Windows 触发）
- **协作留痕**：需要人工或平台权限才能完成的事项（推送提交、分支保护设为必需检查、
  定期扫描生效验证）登记进 `TODO.md` 第 11 节并写明**验收标准**，`PLAN.md` 交叉引用；
  约定此类事项不得只写在对话里
- **修复 CI 必需检查失配**：给必需腿补的 `optional` 矩阵键会改变作业名
  （`build (windows-latest, wrapper)` → `…, false)`），分支保护的必需检查再也匹配不上，
  PR 永久停在 `Expected — Waiting for status to be reported`；改为按 `matrix.os` 判定
  可选腿，不额外增删矩阵键
- **CI 卡点组合**：`Gate.yml` 增加每周**定期扫描**（`schedule`），并新增
  `Compat.yml` 做 Maven 3.9.x 兼容性验证（固定补丁版本，不用 runner 预装 `mvn`）；
  门禁第一步显式断言 `./mvnw` 实际使用的 Maven 版本与 Wrapper 配置一致，
  发布前置为 `gate + compat`
- **检查分层**：慢检查（覆盖率门禁、完整 `clean verify`、文档检查、重复率）集中到
  新的 `Gate.yml`，只在合入 `main` / `release/**` 前、手动触发与发布前运行；
  每次 push 的 `Build.yml` 只做编译与单元测试，反馈环路显著缩短
- **重复代码率检查**：新增 PMD CPD 报告（`./mvnw pmd:cpd`）与 `bin/CheckDuplication.py`
  阈值判定（默认 2.0%，0.1.0 实测 0.40%）

- **CodeQL 工作流上线实测与升级**：`init`（`build-mode: manual`）与 `compile` 三步运行全过，
  失败集中在 `analyze`（上传结果）；已把 `github/codeql-action` 升到 `v4`（v3 将于 2026-12 弃用），
  并注明 `build-mode` 那条提示为良性。`main` 规则集要求 CodeQL，而默认设置与高级设置互斥——
  处置与验收标准见 `TODO.md` `G-04`

- **代码扫描（CodeQL）**：新增 `.github/workflows/CodeQL.yml`——`main` 的规则集要求 CodeQL 结果，
  而仓库此前没有任何 code scanning 配置，PR #7 因此永久停在「Waiting for Code Scanning results」。
  工作流显式覆盖 `pull_request → main`（默认设置只扫默认分支，覆盖不到），不参与必需检查
- **文档提交改落在 `dev`**：规则集整改规格与 `F-12` 复发记录两条文档提交现位于 `dev`
  （`abd5d85` / `ca80c5c`），`dependa` 已复位到 `origin/dependa`；复位后的实测代价
  （首次 `dependa ← dev` 会在 6 个文档文件上冲突及解法）已记入 `TODO.md` `G-01`
- **补回 `dev` 的血缘**：PR #8 以 squash 合入，使 `dev` 拿到内容却没拿到分支历史——
  与 `F-12` 同一形态并在同日复发。已在 `dependa` 上用 `-s ours` 接回（树不变、零内容改动），
  并按 `ReleaseProcess.md` §4.1 立下「被误用 squash 后必须立刻接回血缘」；
  更根本的预防（关闭 squash/rebase 合并）登记为 `TODO.md` `G-05`
- **分支规则集整改规格**：`main` / `dev` 的规则集里存在三条对「单人 + 机器」无法满足的要求
  （要求他人批准、要求 CodeQL 结果却未配置扫描、要求覆盖率数据却无上传），会把 PR 永久锁死；
  整改规格与验收标准已写入 `TODO.md` `G-02` / `G-04`，规范写入 `BuildSpec.md` §8.1
- **CI 对仓库侧瞬时故障有容忍度**：新增复合 action `.github/actions/maven-run`，
  依赖解析类 Maven 调用统一经它——先清本地仓库的失败标记，失败时只在「与代码无关」的
  特征（解析不到 / 传输中断 / 远端 5xx）下重试，其余立刻失败，不给真失败乘以三倍时间
- **分支合并规范**：`ReleaseProcess.md` §4 明确长期集成分支（`dependa`）只能真合并，
  禁止「把内容重新落地一遍」；`dependa` 已用 `-s ours` 补回与 `dev` 缺失的合并关系，
  使 PR #8 从永久 `dirty` 恢复为可合并（不含任何内容改动）
- **工具失败的日志语义**：工具层的已知拒绝（未知工具 / 未启用 / 未获授权）改记 `INFO`
  且不带堆栈——它们与「用户拒绝授权」同类，属预期业务结果；Console 阈值是 ERROR，
  原先记 ERROR 会让模型偶尔叫错工具名就在终端刷出堆栈。未预期异常仍记 `ERROR` + 堆栈
- **测试输出出口**：新增 `aha-core/src/test/resources/log4j2-test.xml` 把测试日志写入
  `target/test-logs/` 并关闭 console；`ConsoleToolApproverTest` 捕获 stdout/stderr
  并顺势断言授权提示内容。构建日志里的测试输出从约 130 行降为 0
- **覆盖率门禁自证**：新增 `bin/ReportCoverage.py` 并在 `Gate.yml` 的 verify 之后执行，
  把各模块与合计覆盖率写进日志；此前 JaCoCo 的 `check` 通过时不出声，日志上与「没配门禁」
  无法区分（判定仍由 `jacoco:check` 独家执行，脚本只报数、阈值读自 `pom.xml`）
- **开发日志**：新增 `Docs/DevLog/DevLog-20261007-21.md`，记录门禁静默这一问题的核实方法
  （配置检查 + 抬阈值使其失败一次）与结论
- **开发日志目录**：新增 `Docs/DevLog/`，排障与事故按 `DevLog-YYYYmmdd-HH.md` 留痕
  （必备背景 / 排障过程与修复链 / 最终验证结果 / 关键教训 / 涉及文件清单五节）；
  首篇记录 CI 必需检查因矩阵作业名变更而永久挂起
- **CI 平台矩阵**：新增 `macos-latest` 可选腿，以 `continue-on-error` 标注，
  仅作演示与提前暴露跨平台退化，不参与必需检查、也不代表已支持 macOS（见 `BuildSpec.md` §4.1）
- **测试与 CI**：测试类隔离 `AHA_HOME` / `user.home` 并不再假定「环境里没有身份文件」；
  断言改用平台自身路径；构建矩阵改为 `fail-fast: false`，避免一条腿失败即取消其余腿而掩盖平台差异
- **文档**：选型清单改为只写主版本线（README / 设计文档 / Constitution），`BuildSpec.md` §7 新增
  「版本单一来源」规则，消除文档与父 POM 之间的依赖版本漂移

- **启动横幅**：交互式会话启动时在右侧打印 `Logo.svg` 对应的**纯 ASCII 标志**（两档尺寸，按终端宽度自动换档或隐藏），顶栏字段与标志并排、其余细节整宽在下；字段对齐改为按显示宽度补位，修正中文标签错列
- **启动横幅**：标志提供**像素风**（48x48 / 24x24 半块字符，粗像素、硬边界）与**线框风**（纯 ASCII）两种，`aha chat --logo auto|pixel|ascii|off` 切换；宽度不足时逐档降级，非 TTY 不着色不出标志
- **文档**：权威设计文档移至 `Docs/AHA/AHA-Design-V1.md`（原 `Docs/AHA-Design-V1.md`）；正文相对链接、两处目录树、附录 A 索引、7 个 SVG 占位说明与全部外部引用同步更新
- **文档**：新增 [PixelLogoDesign.md](Docs/Design/PixelLogoDesign.md)（像素风启动标志设计）与生成器 `bin/GenPixelLogo.py`（`--verify` 检测像素网格漂移，已接入 CI）
- **文档**：新增 [LoggingDesign.md](Docs/Design/LoggingDesign.md)（日志设计）——装配方式与踩坑（为何必须配置当前 `LoggerContext`）、分级分流、文件命名与切分规则、取舍与已知限制
- **文档**：设计文档新增「双向消息结构（JSON 层面）」——内部中间表示、三家请求/响应（非流式与流式）的真实报文、字段映射总表与复现步骤；示例全部使用真实模型名
- **术语统一为「扩展」**：代码注释与 CLI 帮助文案中残留的「插件」全部改为「扩展」（`aha extension --help` 此前显示「插件管理」，与命令名不一致）
- **文档命名统一**：`CliDesign.md` 改名为 `CLIDesign.md`，与 `TUIDesign.md` / `GUIDesign.md` 一致
- **身份来源各自成条 `system` 消息**：系统提示词占第一个片段，之后每个身份文件各占一个，运行环境块单独一个；不再拼成一整段，模型因此能区分内容来自哪个文件。Anthropic 改用 `system` 块数组、Gemini 改用多 `parts` 以保住片段边界
- **默认候选补入 `CLAUDE.md`**（Claude Code 兼容）：开箱即可读取项目的 `CLAUDE.md`，无需改配置
- **未列出的兼容文件不再静默忽略**：项目里存在 `GEMINI.md` / `.cursorrules` 等已知兼容文件但未列入候选时，启动信息会提示并给出加入方法
- **内容相同的身份文件只注入一次**：同一份约定被复制成 `AGENTS.md` 与 `CLAUDE.md`时不会重复注入
- **同一层级内的身份文件全部生效**：用户级 `AHA.md` 与 `AGENTS.md` 并存时都注入（此前只取第一个），层级顺序仍为「用户级 → 项目级」
- **启动信息打印身份加载顺序**：逐个列出「层级 + 路径 + 字符数」，并说明内联兜底是否使用，身份组装不再是黑箱
- **身份文件候选名与写入文件名统一由 `Agent.PromptFiles` 决定**：新增兼容文件（如 `CLAUDE.md`）只改配置即可，读路径与 `/memory` 写路径同步跟随
- **全局记忆文件自动创建**：`aha chat` 启动时若用户级 `AHA.md` 不存在则建骨架（只动用户级目录），全局记忆因此有确定落点；启动信息新增「全局记忆」一行
- **`/memory view` 分节展示全部身份文件**（全局在前）；来源不再只报优先级最高那份（多份为 `files:<用户级>;<项目级>`），避免「全局记忆看起来没生效」
- **AHA 默认系统提示词**：内置身份声明补入 `AhaDefault.yaml` 的 `Agent.SystemPrompt`（此前为空串，`/config` 里显示「（未设置）」）；`/config` 对该栏给首行 + 总字数，完整正文用 `/memory view`
- **会话内命令补全与实时着色**：`/` 开头时 Tab 弹出候选菜单（含用法说明、支持模糊匹配），命令名实时染绿（已支持）或红（未知）——不必等回车才知道拼得对不对
- **`/memory`（原 `/prompt`）**：查看与编辑 Agent 身份文件。默认写**用户级** `AHA.md`，可用 `--project` / `--file` / `--name` 指定目标；文件不存在时新建
- **用户级与项目级身份叠加生效**：此前是「首个命中即返回」，项目里存在 `AGENTS.md` 会让用户级身份整段失效
- **工具区块改为终端转录形态**：首行 `类别 + 工具名 + 程序名`（加粗、鲜艳类型色），命令行改为 `<目录><提示符> <命令全文>` 并高亮白；类别标签改 4 字（读取文件 / 执行命令 …）；
  错误正文不再回显命令原文（命令行已完整展示）
- **输入框上下边框改为高亮紫**：下边线改由状态区首行承担（JLine 的 `Status.setBorder` 无法上色）
- **标题六级各自可辨**：一级「粗+下划线+亮青」逐级递减到六级「弱化+斜体+青」；有色时去掉 `#` 标记（颜色已说明级别），无色时保留（唯一的层级信号）
- **表格单元格解析行内样式**：`` `代码` `` / `**粗体**` / `*斜体*` / 链接；列宽按解析后的宽度算；撑宽时退回纯文本以保对齐
- **Markdown 表格渲染**：缓冲整块后按显示宽度对齐输出框线表（支持列对齐、缺列补齐、超宽压缩列宽）
- **链接渲染**：`[文本]` 加 `(地址)` 显示为「文本 + 弱化地址」，不生成 OSC 8（重定向后会变乱码）
- **章节标题分级配色**：保留 `#` 标记并弱化、正文按级别区分字重与颜色
- **运行环境注入**：系统提示词在内置身份后追加操作系统、命令执行方式（`cmd.exe /c` 或 `/bin/sh -c`）、可用命令与工作目录 —— 避免模型发出 Unix 风格命令后
  在 `cmd.exe` 下失败，且报错看不出是平台不匹配

#### 模块结构

- 项目骨架：父 POM + 6 个子模块 —— `aha-common`、`aha-extension-api`、`aha-core`、
  `aha-tool`、`aha-cli`、`aha-desktop`，全部启用 JPMS
- `aha-common`：共享模型、`Tool` SPI、事件模型、异常层次；**零外部依赖**（仅 JDK）
- `aha-extension-api`：扩展契约（`AhaExtension` / `ExtensionContext` / `Registration`）、
  扩展描述符、事件总线（`EventBus` / `EventKey` / `DispatchMode`）、扩展点接口
- `aha-core`：LLM 适配体系、配置模型与加载、安全存储、记忆存储、Agent 引擎、扩展运行时
- `aha-tool`：内置工具（file / http / shell），经 `ServiceLoader` 发现
- `aha-cli`：picocli + JLine 命令行入口
- `aha-desktop`：桌面端占位模块（0.2 实现）
- Maven Wrapper（固定 Maven 4 运行时，兼容 Maven 3.9.x）
- 工具与适配器服务同时在 `module-info` 的 `provides` 与 `META-INF/services` 中声明，
  兼容 JPMS 与 classpath 两种运行方式
- `LocalAgentService` 提供可注入构造器（`LlmClient` / `ToolRegistry` / `dbPath`），
  支持测试与嵌入

#### 命名与配置约定

- **YAML 字段**用 PascalCase；**SQLite 表名（单数）与字段名**用 snake_case，
  时间字段沿用 `gmt_create`；表为 `session` / `message` / `memory`，
  索引为 `idx_message_session` / `idx_memory_session`
- **统一的用户级目录布局**：`$AHA_HOME`（默认 `~/.aha`）下按大驼峰（PascalCase）单数命名 ——
  `Data/`（数据库）、`Key/`（密钥库）、`Log/`（日志）、`Extension/`（扩展）；
  `aha init` 会创建这四个目录
- **默认路径落在用户级目录**：未设置 `AHA_HOME` 时默认 `~/.aha`，不使用当前工作目录
- `ConfigLoader.expandHome` 支持 `~` / `~/x` / `~\x` 展开
  （`Path.of` 不会展开 `~`，否则会创建出名为 `~` 的字面目录）
- **配置模板用单引号包裹占位符**：YAML 单引号不做转义处理，避免 Windows 路径
  `C:\Users\x\.aha` 中的 `\U` 被当作非法转义而启动失败；
  `ConfigTemplateTest` 扫描资源守住该约束
- 配置解析优先级：命令行 > 环境变量 > 配置文件 > 内置默认；
  占位符支持 `${ENV}` 与 `${ENV:-默认值}`，未解析时保留原样而非替换为空串

#### 模型与供应商

- 模型供应商配置与主配置分离：供应商明细集中于 `Model.yml`，
  主配置只保留兜底模型（`Llm.Fallback`）与模型文件路径（`Llm.ModelFile`）
- **`Model.yml` 默认位于用户级目录**：`$AHA_HOME/Model.yml`（未设 `AHA_HOME` 时为
  `~/.aha/Model.yml`），与 `~/.aws/credentials`、`~/.docker/config.json`、`~/.npmrc`
  同属凭据类配置的用户级惯例；需要项目级配置时在主配置中**显式**设置 `Llm.ModelFile`
- classpath 内置模型配置 `ModelDefault.yml`（6 个供应商），未创建 `Model.yml` 时生效
- `ModelConfigStore`：读写 `Model.yml`，支持 `setDefault` / `putProvider` / `removeProvider`
- CLI 一键切换与增删：`aha provider use <id>`（切换）、`add`（新增，默认保存到 `Model.yml`）、
  `remove <id> -y`（删除）
- 降级回退：默认供应商不可用时回退到 `Llm.Fallback.Provider`
- `ConfigLoader.loadModel` / `loadModelDefault` / `merge` / `resolveModelPath` / `toYaml`
- OpenAI 兼容适配器正确拼接端点路径（`BaseUrl` + `/chat/completions`），
  并兼容已含端点后缀的写法
- `aha init` / `provider add` **优先沿用配置中已有的占位符**，仅自定义供应商才按 ID 推导
- `aha config get` 支持 `Llm.ModelFile` / `Llm.Fallback.Provider` / `Llm.Fallback.Model`；
  其中 `Llm.ModelFile` 输出解析后的**绝对路径**，文件不存在时附提示

#### CLI

- **`aha init` 初始化命令**：首次使用无需手工编写配置文件。交互模式列出内置供应商供选择，
  并可选择直接写入 API Key（输入不回显，落盘后权限为 600）；非交互模式支持
  `--provider` / `--api-key` / `--no-input` / `--force`，供脚本与 CI 使用
- **无参数默认进入交互式对话**：直接执行 `aha` 等价于 `aha chat`（与 `node` / `python` /
  `claude` / `aider` 惯例一致）；非交互环境（管道 / 脚本 / CI）仍打印帮助，避免挂起等待输入
- **`chat` 启动时显示运行时信息**：供应商、模型、端点、工作目录、项目根、会话 ID。
  模型对「你是什么模型」的自述并不可靠，这一信息让用户能直接确认请求发往何处；
  端点取自适配器的 `buildUrl`，与传输层一致
- `LocalAgentService.runtime()` 在 `RuntimeDescriptor.attributes` 中暴露
  `Provider` / `Model` / `Adapter` / `Endpoint`，无需改动 `AgentService` 接口
- 项目根探测：从当前目录向上查找 `.git` / `.aha` / `Aha.yaml`
- **全部子命令支持 `--help` / `--version`**（含嵌套的 `provider add` / `config get` 等）
- CLI `tool invoke` 的 `--param`（`key=value`）与 `--yes`（显式确认）
- `provider list` 在无配置文件时给出初始化引导，并提示当前默认供应商所需的环境变量
- **命令与配置参考**：新增 [`Docs/Guide/ReferenceGuide.md`](Docs/Guide/ReferenceGuide.md)，
  集中整理 CLI 命令参数、配置项全表、环境变量与解析优先级
- **会话内命令（斜杠命令）**：`/help`、`/config`、`/context`、`/session`、`/model [名称]`、
  `/clear`、`/tool [名称]`、`/compact [保留条数]`、`/autocompact [阈值|off]`、`/exit`。
  命令名后必须是空白或行尾，因此 `/usr/bin/ls` 不会被误判；未注册的 `/xxx` 只报错并列出可用命令，
  不静默发给模型
- **终端渲染层**：内联 REPL（保留原生 scrollback，不做全屏 TUI）。探测的是**能力而非平台**：
  `TerminalCapabilities` 判定颜色深度与列宽，`OutputEncoding` 用 `Charset.newEncoder().canEncode()`
  实测控制台能否表示 `❯` `✓` `•` 等符号，不支持时降级为 ASCII（`> `、`[ok]`、`- `）。
  **不自动 `chcp`**——PowerShell 5.1 会缓存 `[Console]::OutputEncoding`，子进程改码页反而更乱
- 流式输出按**显示宽度**折行（East Asian Width，含 emoji 与代理对），不再用 `String.length()`；
  内置轻量 Markdown 渲染（标题、列表、引用、围栏代码、行内代码、粗体 / 斜体），
  且**不牺牲流式**：块级构造只依赖行首 8 字符，行内构造缓冲上限 200 字符
- 等待期显示瞬时状态行（`⠋ 等待响应… 1.2s  deepseek-chat  · 512 tok`），
  只在输出处于行首时绘制，非 TTY 或宽度未知时全为空操作
- **`/memory`：Agent 身份文件的查看与编辑**（等价于 Claude Code 的 `CLAUDE.md`）。
  此前只能看到身份来源路径、看不到正文，改文件对已有会话也不生效。
  子命令 `view`（默认，看正文）/ `files`（候选与查找顺序）/ `edit`（唤起编辑器）/ `append <文本>` /
  `reload`；改完**立即回写会话配置，下一轮即生效**。
  这组命令只由用户输入触发，模型无法自行改写自己的指令
- **终端让位（`TerminalHandover`）与外部编辑器探测（`ExternalEditor`）**：
  `/memory edit` 要把终端交给外部编辑器，而 JLine 处于 raw mode 时外部程序读不到按键，
  因此让位前 `terminal.pause()`、完成后 `resume()`。编辑器按 `-Daha.editor` → `$VISUAL`
  → `$EDITOR` → 平台默认（Windows `notepad`、macOS `open -W -t`、其余 `nano`/`vim`/`vi`）解析，
  只选阻塞式命令
- **修复 Windows 上多行命令只执行第一行**：改为写入临时批处理文件后执行 —— 此前后续行被
  静默丢弃（不报错、退出码 0，表现为「命令成功但什么都没发生」）；顺带消除嵌套 `cmd /c` 多出引号的问题
- **工具区块重排**：首行只留「类别 + 工具名」，新增「终端 / 目录 / 命令」明细与「结果」区；
  命令与输出按显示宽度**折行不截断**（仅超长命令中间省略），末行给耗时（失败同样给）
- **工具执行实时耗时**：状态行原地重绘（区块末行只能给最终值）
- **异常带堆栈**：工具异常与回合异常都打印完整堆栈，便于事后排查
- **工具失败展示完整错误**：最多 40 行、不弱化颜色；`shell-exec` 失败时附上命令原文。
  此前只有 80 字符摘要且不做输出预览，用户看不到完整命令与完整错误
- **输入行分隔线与工具区块**：输入行由上下两条分隔线框住、行首不再有 `>` 标记；
  每次工具调用展开为带背景色带的区块，配色按操作的**后果**分类（读取 / 写入 / 执行 / 网络），
  结果行按成败着色，输出只给前 3 行并标注「还有 N 行」。
  为此 `ToolResultEvent` 新增 `success` 字段（不再从 `"ERROR: "` 前缀反推），
  并修复 `aha-tool` 只声明 `provides` 而未声明 `uses` 导致 `ServiceLoader` 抛
  `ServiceConfigurationError` 的问题
- **常驻状态行**：终端底部保留一行，等待期间实时显示**转圈帧与当前阶段**（耗时与输出规模下沉到
  工具区块的末行；模型名与用量不再挤在这一行）
  （`StatusSurface` / `JLineStatusSurface`，基于 JLine `org.jline.utils.Status` 的滚动区域）。
  按**能力**门禁而非「创建成功即可用」：类型非 `dumb`、具备 `change_scroll_region`、行数 > 0
  三者同时成立才启用，否则安静退化为不显示（`StatusSurface.NONE`）——
  `Status.getStatus()` 在 dumb 终端上也会返回对象，但占不住底部区域且 `close()` 会 NPE。
  状态行走独立通道、不写输出流，终端让位前先隐藏
- **流式期间保持输入可用**：模型还在输出时仍可敲下一条消息（排队后执行），
  `Ctrl+C` 只打断本轮而不再杀掉整个会话，已生成的部分仍会落库。
  做法是主线程常驻 `readLine`、回合跑在虚拟线程上、输出经 `LineReader.printAbove`
  打到输入行上方（`AboveStream` 负责行缓冲）。此前流式与输入严格串行，期间无人读键盘
- `AgentService.streamChat` 新增带 `CancellationToken` 的重载（默认实现委托旧签名，不破坏已有实现）；
  `ChatCommand` 每轮新建令牌，取消后**不丢已生成内容**
- **修复模块路径下 Ctrl+C 杀进程**：JLine 用反射调 `sun.misc.Signal`，而 `sun.misc` 只由
  `jdk.unsupported` 导出；`aha-cli` 未声明该依赖，于是 Ctrl+C 走默认行为直接结束 JVM。
  独立探针跑在 classpath 上不会暴露这个问题，只在模块路径下出现

#### Agent 身份与会话

- **Agent 身份与系统提示词**：可在 `AHA.md` / `AGENTS.md` 中定义 Agent 的角色与行为约定，
  文件内容作为 system 消息注入。查找顺序为「当前目录→项目根逐级向上」→ 用户级 `~/.aha/`
  → `Aha.Agent.SystemPrompt` 内联 → 内置默认身份；支持 `--system` / `--system-file` 临时覆盖
- `chat` 启动信息新增「身份来源」一行，显示实际生效的提示词文件及字符数
- **会话配置持久化**：`SessionConfig`（含系统提示词与模型）写入 `session.config_json`，
  同会话内不再重复解析；进程重启后仍可通过相同 ID 恢复；`MemoryStore` 新增 `loadSession`
- **会话可恢复**：`closeSession` 结束活跃会话并释放资源，但保留持久化记录，
  同一 ID 仍可继续对话——这是会话持久化的价值所在
- **退出时输出会话 ID**：`chat` / `run` 结束（含异常路径）后打印
  `会话已保存：<id>` 与 `续接本次会话：aha chat --session <id>`；
  `chat` 另注册 shutdown hook，覆盖 Ctrl+C / kill 等强制退出
- **多轮对话保留工具调用上下文**：`message` 表含 `tool_calls`（JSON）与 `tool_call_id` 两列，
  启动时自动迁移旧库；`loadHistory` 丢弃无法完整重建的旧消息
- **流式与非流式行为一致**：`AgentEngine.stream()` 与 `run()` 同样执行
  「推理 → 工具 → 回灌 → 再推理」循环，上限为 `MAX_TOOL_ITERATIONS = 8`
- **多行粘贴安全**：启用 `BRACKETED_PASTE`；读取异常时宽幅兜底（忽略本次输入并继续）；
  `exit` / `quit` 仅在单行且无内嵌换行时生效，避免粘贴含 `exit` 的文本意外退出
- **上下文压缩**：`/compact` 把较早的对话交给模型摘要并用摘要替换那批消息
  （默认保留最近 10 条，`/compact N` 可指定），切割点不落在工具调用与其结果之间；
  `/autocompact 285k` 设定阈值后每轮自动触发，阈值存在会话配置里，`--session` 续接后仍生效。
  摘要生成失败或返回为空时**绝不改动历史**——否则用户会既丢上下文又拿不到摘要
- `MemoryStore` 新增 `replaceHistory`（压缩）与 `updateSessionConfig`（`UPDATE` 语义，
  不重写会话创建时间）
- **`loadHistory` 取最近的 N 条**：此前 SQL 为 `ORDER BY gmt_create ASC LIMIT ?`，
  会话一旦超过 `Memory.MaxContextEntries`，模型就永远只能看到最早的 100 条，
  新发生的对话再也进不了上下文
- **项目级记忆位置与项目 ID**：`~/.aha/Project/<项目ID>/Memory/`，项目 ID 由项目根绝对路径
  推导（分隔符转 `-`、Windows 盘符大写），与 Claude Code 的 `~/.claude/projects/` 规则一致；
  `/session` 展示当前项目 ID 与记忆目录
- **CLI 版本号由构建期注入**：此前硬编码在两处（`@Command(version = ...)` 与 `VersionCommand`），
  改 `pom.xml` 不会生效，发行包会报出旧版本；现以 `pom.xml` 为唯一来源，
  经资源过滤写入 `version.properties`，picocli 侧改用 `IVersionProvider` 运行时求值。
  顺带修掉 `bin/__pycache__` 等会被误提交的产物，并补齐 `.gitignore` 的大小写规则
  （`Data/` / `Key/` / `Log/` / `Model.yml` —— 小写的 `data/` 在 Linux 上匹配不到 `Data/`）

#### 权限与安全

- 权限策略体系：`PermissionPolicy`（READ/NETWORK 默认放行，WRITE/EXECUTE/ADMIN 需授权）
- `Tools.AutoApprove` 配置项，在默认策略之上追加自动放行权限
- **交互式工具授权**：`chat` 中遇到需 `WRITE`/`EXECUTE`/`ADMIN` 权限的工具时
  **暂停并询问**（`y` 允许本次 / `n` 拒绝 / `a` 本会话内始终允许该权限）；
  新增 `ToolApprover` 契约（`aha-common`）、`ConsoleToolApprover`（CLI）与
  `ToolRegistry.setApprover`
- `chat` / `run` 的 `--yes` 选项与 `tool invoke -y` 语义对齐；
  显式确认只跳过权限裁决，**不跳过** `Tools.Enabled` 过滤
- **`Security.KeyStore` 密钥库**：按配置创建 `EncryptedFileSecretStore`；
  占位符未命中环境变量时回退读取密钥库；解析发生在**使用点**（`DefaultLlmClient`）——
  `Security` 段自身是明文，配置加载必然早于密钥库就绪
- `aha secret set|get|delete|list` 密钥管理命令；主密码支持系统属性回退

#### 工具

- **工具配置落地**：`ToolSettings`（`aha-common` 的中立配置视图）+ `Tool.configure(...)` +
  `ToolRegistry.configureAll(...)`，使 `Tools.Enabled`、Shell 超时与命令白名单全部生效
- **`shell-exec` 超时与取消**：默认 30 秒超时；超时或取消时终止**整个进程树**
  （仅终止直接子进程会留下孤儿）；在虚拟线程中读取输出，避免输出超过管道缓冲时子进程阻塞
- `shell-exec` 命令白名单仅接受「单一命令 + 无 shell 元字符 + 首个子命令在名单内」——
  只判首 token 不安全：`echo hi; whoami` 的首 token 是允许的 `echo`，但 `whoami` 照样会执行

#### 日志

- **日志系统按配置程序化装配**：由 CLI 启动时应用 `Aha.Logging.Level` / `Aha.Logging.File`
  （`LoggingSetup`）；切分文件与主文件同目录、命名为 `<基名>-yyyy-MM-dd-NN.log`（超 10 MB 即切分），console 写 stderr
  —— CLI 是组合根，`aha-core` 只依赖 `slf4j-api`，不绑定具体实现
- **日志分流**：console 仅输出 `ERROR` 以免打断对话；
  用户拒绝授权等预期业务结果记为 `INFO`，完整日志仍写入文件
- **日志格式含调用点**：`%logger.%method:%line`，输出完整类名、方法名与行号
- 补齐 `INFO` 级日志点：配置来源、会话创建 / 关闭、推理开始、工具调用与结果

#### 错误处理

- **错误信息包含服务端响应体**：流式与非流式统一输出响应体（截断至 800 字符），
  便于直接看到「模型不存在」「额度不足」等具体原因，而非只有 `HTTP 422`
- `Exceptions.unwrap` / `message`（`aha-common`）解包 `CompletionException` 等包装异常，
  CLI 错误输出不带 `com.acanx...` 类名前缀

#### 用例与质量

- 测试补齐至 **302 个用例**，覆盖 `PermissionPolicyTest`、`ToolRegistryTest`、
  `SessionManagerTest`、`ProviderPresetsTest`、`DefaultLlmClientTest`（自包含 Mock HTTP）、
  `EndToEndTest`、`LocalAgentServiceTest`、`AgentEngineStreamTest`、`BuiltinToolsTest`、
  `CliCommandTest` 等
- JaCoCo 覆盖率报告与 `verify` 门禁（BUNDLE 行覆盖 ≥ 70%）
- Javadoc 构建配置（`maven-javadoc-plugin`，`doclint=none`、zh_CN）

#### 发行与发布

- 发行包（`mvnw clean package` 产出 `Dist/`）：`maven-assembly-plugin` 将 `aha-cli` 自身、
  全部模块与第三方依赖收集到 `lib/`，并附带 `bin/Aha.sh` / `bin/Aha.bat` 启动脚本；
  分发方式为 JPMS 模块路径目录，与启动脚本的 `--module-path lib` 一致
- **跨平台启动脚本**：`Aha.sh` 支持 `JAVA_HOME`、校验 `lib/` 存在并给出可读错误提示；
  `Aha.bat` 为**纯 ASCII + CRLF**（CMD 按 ANSI 代码页解析批处理），提示信息用英文；
  `bin/CheckScripts.py` 在 CI 中守住该约束
- 启动脚本传递 `--enable-native-access`，消除 JDK 25 下 `sqlite-jdbc` 与 `jline`
  的受限方法警告
- `Release.yml` 发布完整的 `dist.zip`
- 父 POM 补充发布元数据：`<url>`、`<licenses>`（Apache-2.0）、`<developers>`、`<scm>`
- `mvn clean` 一并清空 `Dist/` 中的构建产物（保留目录）：assembly 只覆盖同名文件，
  旧版本的依赖 jar 会残留，而 JPMS 下一个模块出现两个版本是致命错误
  （`java.lang.module.FindException: Two versions of module ...`），
  依赖升级后重新构建出的发行包会直接启不来

#### 前向兼容契约（1.0+ 实现形态）

- `Endpoint` / `RuntimeKind` / `RuntimeDescriptor`（`aha-common.runtime`，寻址与运行时描述）；
  `RuntimeKind` 预留 `WSL` / `CONTAINER` / `SSH` / `SERVERLESS` / `DAEMON`
- `TaskRequest` / `TaskResult` / `TaskSource`（`aha-core.task`，跨进程 / 跨云任务契约，
  PascalCase JSON，包装类型 + 忽略未知字段以兼容混合版本）
- `AgentRuntime`（`aha-core.runtime`，远程运行时抽象，含默认流式语义）
- `AgentServiceProvider` SPI + `AgentServiceFactory.connect(...)`（新增形态只需新增模块）
- `AgentService.submit(TaskRequest, CancellationToken)` 统一任务入口
- 设计文档 `Docs/Design/RemoteAndProtocolDesign.md`：ACP / MCP / A2A 辨析、1.0~2.0 路线、
  配置 schema 预留、跨边界安全模型、测试策略与风险

#### 文档与工程

- `AGENTS.md`、技能体系、开发规范 / 设计 / 用户指南文档骨架
- **1.0 硬门槛：AHA 自举里程碑**。发布 1.0 正式版前，项目自身的开发、测试、构建、发布
  以及后续 AHA 功能的迭代升级，必须全部由 AHA 工具链完成，不再依赖其他 Agent Harness；
  定义 L0~L4 自举层级与 A1~A6 验收标准
- 设计文档 `Docs/Design/SelfHostingDesign.md`：自举层级、反向指标、缺口与补齐路径、
  度量方法、风险与开工清单
- `ReleaseProcess.md` 新增 1.0 发布的自举附加检查（A1~A6）
- `CommitMessageSpec.md` 新增自举期 `Harness:` 标注约定（用于度量与审计，不参与 CI 门禁）
- 构建文档按「规范进 DevSpec、操作进 Guide」拆分：
  `Docs/DevSpec/BuildGuide.md` → `Docs/DevSpec/BuildSpec.md`（强制要求：工具链锁定、
  双版本验证、覆盖率门禁）+ `Docs/Guide/BuildGuide.md`（操作：命令、报告、构建期排错）；
  `TroubleshootingGuide.md` 的构建期问题归并至 BuildGuide，本文件专注运行时与配置排错
- 文档规范增加「规范与操作分离」原则，并修正后缀约定表中 `Guide.md` 跨目录歧义
- `Docs/DevSpec/` 下 4 份规范文档以 `Spec.md` 结尾：
  `TestingSpec.md`、`YamlFieldSpec.md`、`CommitMessageSpec.md`、`DocumentationSpec.md`；
  命名规范中明确禁止 `Rule.md` 结尾
- **技能符合 Agent Skills 规范**：目录与 `name` 采用 kebab-case，入口为 `SKILL.md`
  并含 `name` / `description` / `license` / `compatibility` / `metadata` frontmatter；
  资源目录拆分为 `references/`（参考文档）与 `assets/`（模板与数据），
  资源文件名用 kebab-case；主体按渐进披露组织（何时使用 → 前置条件 → 步骤 → 参考 → 常见错误）
- `bin/CheckSkills.py`：校验技能目录结构、frontmatter、命名与内链，接入 CI `docs` job；
  技能内 Java 模板另由 `build` job 编译验证
- `bin/CheckDocs.py`：文档围栏闭合与相对链接有效性检查，接入 CI `docs` job
- `bin/CheckChanged.py`：按变更路径选择要跑的检查（文档 → `CheckDocs`、技能 → `CheckSkills`、
  脚本 → `CheckScripts`，实现代码则给出该跑的 Maven 命令），避免改一段文档也付分钟级验证代价；
  `CheckScripts.py` 的目录遍历改为单次 `os.walk` 剪枝，在慢文件系统上从 25 秒降到亚秒
- **依赖自动升级**：`.github/dependabot.yml` 每日检测 Maven 与 GitHub Actions 依赖，
  PR 先合入 `dependa` 分支再人工合并；minor 与 patch 分组，major 单独成单

[0.1.0]: https://github.com/ACANX/AHA/releases/tag/V0.1.0

### 变更
- **供应商配置更好用**：对话框宽度翻倍；列表用**绿灯**标出当前启用的供应商并附其模型
  （状态行复述「● 已启用：<供应商> <模型> 密钥 <打码>」）；打开时默认选中当前启用项；
  新增供应商支持「按预设新建」（六家已知供应商一键填好适配器 / 基础地址 / 默认模型），
  模型字段是可编辑下拉（候选取自 `ModelDefault.yml` 的真实模型名，也允许自己填）
- **桌面端可用了（最小可用版）**：接入 Agent，能真的对话——流式正文、工具卡片（类别配色 + 结果行）、
  错误展示、用量回填、`Esc` 中断；左栏导航可用（新建会话 / 供应商 / 工具）；
  输入框启动即获得焦点；供应商配置改为**可查看与修改的表单**（适配器 / 基础地址 / 模型 /
  API Key（打码，可显示）/ 超时 / 重试 + 保存 / 设为默认 / 新增 / 删除，写回 `Model.yml`）。
  对话内核与表单校验都抽成不依赖 JavaFX 的类（`ChatController` / `ChatView` / `ToolSummary` /
  `DesktopToolApprover` / `ProviderForm`），因此整条链路在无图形环境下有 49 例测试覆盖
- **桌面端窗口图标与标志**：把 `Logo.svg` 栅格化为 PNG 入库（新增 `bin/GenLogoPng.py`），
  用作窗口 / 任务栏图标与空会话展示；生成器把 SVG 根元素宽高写成目标像素数、
  开一个更高的窗口，再自己解码 PNG 按 alpha 裁掉透明边并**自检包围盒**
  （第一版只验「是正方形、够大」，于是「只渲染出四分之一 / 只保留上半」的裁切图全数通过，
  到真机启动才被发现）
- **窗口尺寸按 DPI 缩放计算**：`Screen` 报的是物理像素而场景尺寸是逻辑像素，
  之前在高 DPI（本机 150%）下按 800 逻辑像素开窗会变成 1200 物理像素，把输入区与底部状态栏
  顶出屏幕；同时给消息区 `setMinHeight(0)`，避免 `ScrollPane` 把输入区挤出窗口
- **桌面端正式读取配置**：新增 `aha-core` 的启动引导 `AhaBootstrap`（读项目 `./Aha.yaml`
  → 装配日志 → 装密钥库回退源），CLI 与桌面端共用；桌面端窗口新增一行配置摘要
  「配置：<路径> · 日志级别：<级别>」。此前桌面端固定用默认日志配置，
  `Aha.Logging.*` 在桌面端完全不生效
- **版本号与日志装配下移到公共模块**：`version.properties` 与 `AppVersion` 迁到 `aha-common`
  （picocli 适配留在 `aha-cli` 的 `CliVersionProvider`，避免把 picocli 带进零依赖模块）；
  `LoggingSetup` 迁到 `aha-core`（该模块的 `log4j-core` 改 `compile`）。桌面端因此能取到版本号、
  配置日志，且无需依赖 `aha-cli`
- **release 资产改名**：CLI 发行包由 `aha-<tag>-dist.zip` 改为 `aha-<版本>-cli.zip`
  （版本从产物名读，不再用含 `V` 前缀的 tag 名），与桌面端的
  `aha-desktop-<版本>-<系统>-<架构>.zip` 命名保持一致

### 修复
- **主配置不可用时不再让后续流程抛异常**：启动引导会退回内置默认并记一条警告；
  此前 CLI 会把 `null` 传给后续流程，`CliContext` 二次读取配置时会抛错
- **版本号清单不完整（静默错版本）**：`ReleaseProcess.md` 原文称「版本号只需改根 `pom.xml`」，
  实测只改根 POM 时六个子模块仍按 `<parent>` 声明的旧版本解析——反应堆显示
  `Building AHA-Common 0.1.0`、产物名 `aha-common-0.1.0.jar`、`aha --version` 仍报旧版本，
  而构建**不报错**。清单更正为 8 处（根 POM + 六个子 POM 的 `<parent>` + `AppVersion.FALLBACK`）
- **CHANGELOG 链接 404**：`[0.1.0]` 指向 `releases/tag/V0.1.0`，而实际 tag 是 `0.1.0`
- **发布日**：`[0.1.0]` 由 `2026-10-06` 更正为实际发布日 `2026-10-07`（发布提交 `9138847`）

## [0.1.0] - 2026-10-07

首个可用版本：Core + CLI 可运行。

### 新增
