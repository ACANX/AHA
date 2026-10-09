# AHA 待办与调整项（暂存区）

**文档版本**：v0.51.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-09
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

> ## ⛔ 本文件已冻结（2026-10-09）
>
> **不再追加新内容**：除非 ACANX 主动要求，任何 Agent / 流程**不得向本文件写入**。
>
> 遗留事项、待决策项与「需人工 / 平台执行」的事项，一律改用 **GitHub Issue** 跟踪
> （总览 [#86](https://github.com/ACANX/AHA/issues/86)，主题拆分到 #72~#85）。
>
> 现存条目**仅供追溯**：某条若实际已闭环，直接关闭对应 Issue 即可，**不必回改本文件**。
>
> 规则见 [`AGENTS.md`](../AGENTS.md) 与技能
> [`issue-tracking`](../.agents/skills/issue-tracking/SKILL.md)。
>
> **已完成条目已归档（2026-10-09）**：状态为「已完成 / 已解决 / 已决策 / 已修」**且整节无残留待办**
> 的条目，已迁入 [`Docs/DevLog/20261009-17.md`](DevLog/20261009-17.md) 并从本文件删除；
> **部分完成（◐）的条目一律保留在原处**（不迁移）。

---

## 变更日志

| 版本   | 日期       | 变更内容                                                     | 变更人 |
| ------ | ---------- | ------------------------------------------------------------ | ------ |
| v0.1.0 | 2026-10-06 | 初始版本：汇总 0.1 代码走查、JPMS 架构评估、桌面端与原生编译调研结论 | @ACANX |
| v0.2.0 | 2026-10-07 | 新增第 6 节「记忆能力（0.6 起）」：推进顺序、写入策略、载体选型实测依据、跨环境共享机制 | @ACANX |
| v0.3.0 | 2026-10-07 | **重排版**：清除重复副本与误粘贴的会话日志（见 `PLAN.md` §4）；全部条目表格新增「状态」列并逐条复核；新增第 9 节「复核结论汇总」 | @ACANX |
| v0.3.0 | 2026-10-07 | `Docs/` 清单更新：设计文档已移入 `Docs/AHA/` | @ACANX |
| v0.4.0 | 2026-10-07 | B-03 版本漂移结项 | @ACANX |
| v0.5.0 | 2026-10-07 | 新增第 10 节「CI、门禁与工程效能」（F-01~F-06，均已落地）：检查分层与每周定期扫描、门禁断言 Maven 版本、Maven 3.9.x 独立兼容工作流、重复代码率检查、矩阵不再 fail-fast、macOS 可选腿定位；§2 新增 B-04~B-07（Windows 暴露的四类缺陷，均已修）并**修正 B-03 的三处过期状态**（条目正文 ✅ 已解决，而 §2 汇总表与 §8 落地去向表仍写 ◐ 进行中） | @ACANX |
| v0.6.0 | 2026-10-07 | 新增第 11 节「待人工执行的动作」（G-01 推送提交 / G-02 分支保护配必需检查 / G-03 确认定期扫描生效），每条写明阻塞面与**验收标准**，并在 §0 立下「此类事项不得只写在对话里」的约定；新增第 12 节与 H-01（启动标志默认风格待拍板）；§1 新增 A-08（暂存区文件存放位置）、A-09（`PLAN.md` §8.2 存在两个 `8.2.7`）；§10 新增 F-07（`.ps1` 未纳入检查）；§7 补三条待决策项并重排序 | @ACANX |
| v0.7.0 | 2026-10-07 | 新增 `F-08`（✅ 已修）：给必需腿补 `optional` 矩阵键使作业名多出 `, false`，必需检查永久停在 `Expected — Waiting`——附取证对照表与教训；`G-02` 按实况重写（现已配置三条 `build (...)` 快速腿，另需补配 `Gate` / `Compat` 两条，给出与作业 `name:` 完全一致的字符串与维护约定） | @ACANX |
| v0.8.0 | 2026-10-07 | 新增 `F-09`（✅ 已修）：覆盖率门禁静默不可自证，附两步核实法（配置 + 抬阈值使其失败）与修法；`Gate.yml` 现打印实测值 | @ACANX |
| v0.9.0 | 2026-10-07 | 新增 `F-10`（✅ 规范偏差：工具层已知拒绝被记 ERROR 并附堆栈，违反 LoggingDesign §4）与 `F-11`（✅ 测试日志污染构建日志），附修法与实测验证数据 | @ACANX |
| v0.10.0 | 2026-10-07 | 新增 `A-10`（`PLAN.md` 有两个 `## 8.` 标题）。新增 `F-12`（✅ 已修：PR #6 以单父提交重新落地，使 PR #8 永久 `dirty`；已用 `-s ours` 补血缘并逐项验证）与对应的教训、规范落点；`G-01` 按实况更新（前半已完成，现待推送合并提交 `06c6121`，补 `mergeable_state` 验收标准） | @ACANX |
| v0.10.1 | 2026-10-07 | `F-12` 引用同步开发日志改名（`DevLog-20261007-21-2.md` → `DevLog-20261007-22.md`） | @ACANX |
| v0.11.0 | 2026-10-07 | 新增 `F-13`（✅ 已修：CI 插件依赖「当次要不到」的定性过程与修法，含两条 Maven 消息的判别）与 `F-14`（⏸ 待决策：`.github/**/*.yml` 无本地检查） | @ACANX |
| v0.11.1 | 2026-10-07 | `D-06` 修正失效断言：`Build.yml` 矩阵已含 macOS 腿（`F-06`，定位为演示与可选），而 `DesktopDesign.md` §2 缺平台清单一节经再次实查仍成立 | @ACANX |
| v0.11.2 | 2026-10-07 | `A-09` / `A-10` 结项：`PLAN.md` §8 编号与顺序整体重排（§8.2 表归位并新增 `8.2.11`、`### 8.4` 归位、重复的 `## 8.` 改为文末 `## 9.`），外部引用经核对零改动 | @ACANX |
| v0.11.3 | 2026-10-07 | 新增 `F-15`（⏸ 待决策：是否把「文档编号重复」纳入 `bin/CheckDocs.py`）；说明 `B-01`/`B-02` 的状态列经复核**不是**矛盾（该列为「证据」而非结果） | @ACANX |
| v0.11.4 | 2026-10-07 | §7 补「与 0.1 的关系」与建议表：13 项待决策中只有 `H-01` / `E-12` / `A-08` / `F-07` 与 0.1 相关，逐条给出建议与理由 | @ACANX |
| v0.12.0 | 2026-10-07 | `G-02` 重写为「分支规则集整改」：附现状实测表（dev/main 两条规则集逐条规则）与目标规格表（审批数、必需检查、`code_scanning`/`code_coverage` 二选一）；新增 `G-04`（开启 CodeQL，附「不开就必须删规则」的对应关系） | @ACANX |
| v0.13.0 | 2026-10-07 | `F-12` 补「同日复发」实测记录（PR #8 以 squash 合入，`dev` 树 == `dependa@1518056` 树，用 `-s ours` 接回血缘 `d1de175`）；新增 `G-05`（仓库设置关闭 squash/rebase 合并）；`G-01` 改为推送本次补血缘的合并提交 | @ACANX |
| v0.14.0 | 2026-10-07 | `G-01` 按方案 B 后的实际分支状态重写（`dev` 待推送 2 条、`dependa` 已复位无需推送、`dev` 直接推送可能被规则集拒绝的处置），并记录复位后的实测代价：首次 `dependa ← dev` 会在 6 个文档文件上冲突及解法 | @ACANX |
| v0.15.0 | 2026-10-07 | `G-04` 改写：新增 `.github/workflows/CodeQL.yml`（高级设置/工作流方式，显式覆盖 `pull_request → main`，手工构建走统一 Maven 入口），说明为何默认设置覆盖不到 `main`、与必需检查契约无关，并给出「不要扫描就删规则 + 删工作流」的备选 | @ACANX |
| v0.16.0 | 2026-10-07 | `G-04` 补上线实测：工作流 init/compile 三次全过、失败仅在 `analyze`；因两份分支树零差异判定为配置冲突，指向「默认设置与高级设置互斥」并给出二选一处置；顺手把 codeql-action 升到 v4，并注明 build-mode 那条提示为良性 | @ACANX |
| v0.17.0 | 2026-10-08 | `G-01` 结项（两条文档提交已随 PR #9/#10 进入 `origin/dev`）；新增 `G-06` 处置 0.1.0 的裸 tag 与 `V*` 约定的不一致 | @ACANX |
| v0.18.0 | 2026-10-08 | 五项决策拍板并落地：`C-01`（JPMS 非强制，OpenJFX 优先）、`D-05`（桌面端纳入门禁 + 单独阈值 0.30→0.70）、`D-06`（Win+Linux 为承诺，macOS 只打包不测）、`D-07`（进程内直调优先）、`D-09`（需要内置工具，依赖矩阵已改） | @ACANX |
| v0.19.0 | 2026-10-08 | `D-01` 机制落地并实测（父 POM 的 javafx-* per-OS profile + 分类器依赖 + 空壳排除），状态改为 ◐ 机制已落地；§4 现状陈述同步（JavaFX 依赖已实装） | @ACANX |
| v0.20.0 | 2026-10-08 | `D-01` 结项（机制 + 描述符落地）、`D-02` 完成（桌面端启动脚本）、`D-09` 落地（`aha-tool` 已声明）、`D-08` 改为「流水线已就位」（`Release.yml` 矩阵化 + 双向自证 + 上传）；§5 制品表补桌面端便携包 | @ACANX |
| v0.21.0 | 2026-10-08 | 新增 `D-10`：桌面端便携包缺 `log4j2.xml` 与 `version.properties`（二者在 `aha-cli`，而 `cli` 与 `desktop` 不得互相依赖），含实测证据、影响与建议动作 | @ACANX |
| v0.22.0 | 2026-10-08 | `D-07` 结项（线程模型小节 + 桥接契约 + 静态扫描 + 6 例单测）、`D-10` 结项（版本号下移 `aha-common`、日志装配下移 `aha-core`，并更正早期「缺 `log4j2.xml`」的误判）、`D-04` 记为「门禁方式已定」（冒烟默认跳过）、新增 `D-11`（桌面端不读 `Aha.yaml`） | @ACANX |
| v0.23.0 | 2026-10-08 | `D-11` 结项：抽出 `AhaBootstrap`（CLI 与桌面端共用读配置/装配日志/装密钥库），桌面端窗口显示配置摘要；含单测与 CLI 端到端实测证据 | @ACANX |
| v0.24.0 | 2026-10-08 | 新增并结项 `D-12`（桌面端三栏骨架 + 折叠三条路径 + 纯逻辑 `FoldState`），遗留项记明（工具卡片 / Agent 接线 / `ToolKind` 下移） | @ACANX |
| v0.25.0 | 2026-10-08 | 新增 `F-17`（`-Djavafx.platform` 触发 `recursive variable reference` 的构建日志噪音） | @ACANX |
| v0.26.0 | 2026-10-08 | `D-12` 遗留更新：Agent 接入与 `ToolKind` 下移、供应商配置已完成；列明尚未做项（工具卡片展开/输出预览、会话列表、记忆/扩展/日志面板、`/` `@`、主题、设置、授权弹窗样式） | @ACANX |
| v0.27.0 | 2026-10-08 | 0.2 六项功能完成（工具卡片 / 会话列表 / 日志面板 / 输入区增强 / 授权弹窗 / 主题与设置）；新增 `D-13`（剩余项）与 `F-18`（已入库的合并冲突标记，已修复并加守卫） | @ACANX |
| v0.28.0 | 2026-10-08 | 新增 `aha-desktop-native`（试验性原生镜像模块）与 `DesktopNative.yml`：`N-01`~`N-04` 落地（profile 隔离、独立出包、`a.b.c.PPPPP` 版本、JDK 27 实验分支），`N-05`~`N-07` 待跟踪 | @ACANX |
| v0.29.0 | 2026-10-08 | 新增 `N-08`/`N-09`：把原生镜像经验沉淀为可复用技能，并明确「先建骨架、边做边改、达成目标才成熟」的迭代方式与四条达标判据 | @ACANX |
| v0.30.0 | 2026-10-08 | 新增 `N-16`（真机验证 issue #35 补上的 JavaFX 启动链路反射 / JNI 元数据）、`N-17`（改用 tracing agent 采集元数据，手写清单的抄底方案）与 `G-08`（推送修复分支 `fix/issue-35-native-quantum-toolkit` 并提 PR，含验收标准） | @ACANX |
| v0.31.0 | 2026-10-08 | 新增 `N-18`（CLI 原生镜像 `aha-cli-native` + `CliNative.yml` 已落地）与 `N-19`（真机验证 CLI 原生镜像）、`G-09`（推送并提 PR）；`N-17` 范围扩到 CLI；同批修正客户端（桌面端）资源正则漏 `yaml` / `yml` 的潜在缺陷 | @ACANX |
| v0.32.0 | 2026-10-08 | 新增 `N-20`（新技能 `graalvm-reachability-metadata`：元数据登记方法论）与 `N-21`（其成熟度跟踪） | @ACANX |
| v0.33.0 | 2026-10-08 | `N-21` 推进：在 WSL + GraalVM 25.0.2 上首次真跑 tracing agent（CLI 端），采集 400 类型 / 55 资源，与手写清单完成交叉验证；结论回写技能（`discovery` §2.4、`catalog` §A.2） | @ACANX |
| v0.34.0 | 2026-10-08 | 把 agent 采集**制度化**进技能（新增 `references/agent-collection.md` + `scripts/collect-metadata.sh`）；GUI 端也跑了一轮，补进客户端原生镜像元数据 47 条（293→340）；新增 `N-22`（Win/mac 平台采集） | @ACANX |
| v0.35.0 | 2026-10-08 | 真机暴露 issue #37（Glass 初始化处 JNI `FindClass` 失败 → segfault）；新增 `jni-config.json`（62 类，扫描 openjfx 三平台 native 源码得到）；`N-16` 进入第二轮待验证 | @ACANX |
| v0.36.0 | 2026-10-09 | 真机（#37 修复后）暴露 issue #39（`WinWindow._initIDs` 查自己声明的 `notifyMoving` → `NoSuchMethodError`）；改扫 `Get*ID` 目标类，`jni-config.json` 由 62 → **85** 条（Win* / Mac* 平台实现类 + `EventLoop`）；新增 `G-10`；`N-16` 进入第三轮 | @ACANX / CNXNC |
| v0.37.0 | 2026-10-09 | 真机（#39 修复后）首次进到 GUI，但暴露 issue #41（效果 peer 动态类名未登记 → `Could not create peer`，控件画不出）；扫描 javafx-graphics 25 jar 登记 **99** 个具体 peer，`reachability-metadata.json` 由 340 → **439** 条；新增 `N-23`、`G-11` | @ACANX / CNXNC |
| v0.38.0 | 2026-10-09 | `D-02` 补记：`bin/AhaDesktop.{bat,sh}` 支持源码检出布局（无 `lib/` 时按平台解压 `Dist/aha-desktop-*.zip` 后启动），修「源码根运行报找不到 lib、而提示的 `clean package` 走不通」；新增 `TS-202610-LauncherMissingLibOnSourceRoot.md` | @ACANX |
| v0.39.0 | 2026-10-09 | 新增两个脚本：`Script/Python/DesktopDistBuild.py`（构建便携包并自证）与 `DesktopDistExtract.py`（解压并更新 `Dist/lib`、`Dist/bin`，清理旧桌面端独占 jar）；新增命令速查 `Docs/Guide/CommandCheatsheet.md`（落地为 `Dist/README.commands.md`，已纳入版本控制，由 CLI assembly 与 extract 脚本重建，且 `mvn clean` 不删）；`BuildGuide.md` §3.1.1 同步 | @ACANX |
| v0.40.0 | 2026-10-09 | 构建输出目录全仓统一为**大驼峰 `Dist`**（POM 的 outputDirectory / `finalName`、CI 工作流路径、启动脚本、忽略规则与文档）；`Dist/README.commands.md` 纳入版本控制（`.gitignore` 放行、clean 排除、随 assembly/extract 重建）；新增 `G-12`（推送本分支并提 PR） | @ACANX |
| v0.41.0 | 2026-10-09 | 真机暴露 issue #44（默认主题不跟随系统、切主题残留、暗色下标题对比度低、字体差异）；修掉前三类确定性缺陷（`SystemTheme` 三级回退 + 系统配色订阅、硬编码色改读 `Palette`、裸控件补主题登记、弹层与右键菜单重刷）；字体差异遗留，**另开 issue #48 长期跟踪**（本文件留索引 `N-24`），新增 `G-13` | @ACANX / CNXNC |
| v0.42.0 | 2026-10-09 | 真机暴露 issue #49（暗色下搜索框 / 会话列表 / 输入框仍是亮色底，导航按钮 / 菜单栏 / 状态栏文字仍是深色）；修掉两处成因：`Palette.theme()` 去缓存（原生镜像下样式串与色表不同步）、`applyTheme` 逐个节点异常隔离；搜索框 / 输入框 / 会话单元格 / 菜单栏 / 滚动区改为显式套主题；新增 `N-25` | @ACANX / CNXNC |
| v0.43.0 | 2026-10-09 | 落地 issue #46（预发行版显示带构建号的版本）：根 POM 新增 `aha.build.version`（默认 `${project.version}`）、`version.properties` 增加 `build` 项、`AppVersion` 提供 `buildVersion()` / `isPreRelease()`；`DesktopNative.yml` / `CliNative.yml` 构建时传 `-Daha.build.version=<a.b.c.PPPPP>`；展示点：桌面端「关于」与启动日志、CLI 启动页 / `/help` / `version`；新增 `N-26`、`G-14` | @ACANX / CNXNC |
| v0.44.0 | 2026-10-09 | 修正 issue #46 的构建号注入：`DesktopNative.yml` / `CliNative.yml` 的 `command: >-` 折叠块内写了 `#` 注释，块标量里的 `#` 不是 YAML 注释，被折进命令串后在 shell 里注释掉了其后全部参数（含 `-Daha.build.version`），导致构建号从未注入（真机看不到）；已把注释移出块外 | @ACANX / CNXNC |
| v0.45.0 | 2026-10-09 | issue #49 第二轮：真机仍报暗色下左栏文字深、亮色下对话框底色深。先取证（build-report 确认 `modena.css` 已打包；JVM 探针确认亮/暗两套下 `Label`/`Button`/`Menu`/`DialogPane` 均正确）→ 定位到原生镜像对 **looked-up color 查表 / `ladder()` 推导**不可靠；修法：`Palette.theme()` 显式钉死文字类颜色，新增 `Palette.dialogTheme()` 给 7 个对话框显式底色，`applyTheme` 加主题诊断日志；顺带补回漏合并的状态栏 `buildVersion()` | @ACANX / CNXNC |
| v0.48.0 | 2026-10-09 | issue #49 第四轮：真机反馈「亮色已好、暗色下文字仍是黑字」。根因是补丁表把文字色写成查表形式，而原生镜像下查表失败会丢掉整条声明（亮色恰好等于 modena 默认值所以看不出来）；改为 `aha-theme-{dark,light}.css` 两份纯字面量、按主题整份装载，`Palette` 删除 `-fx-aha-*` 定义，并补文字兜底规则（含 `Text` 节点的 `-fx-fill`）| @ACANX / CNXNC |
| v0.47.0 | 2026-10-09 | issue #49 第三轮（严重）：亮色与暗色下文本输入框 / 下拉框 / 「发送」按钮 / 滚动条背景全黑。查 modena.css 定案——这些控件的 `-fx-background-color` 写成 `derive()` / `linear-gradient()`，原生镜像下 CSS 函数求值不可靠，而 CSS 是「一个值无效就丢弃整条声明」，控件因此**没有背景**；修法：新增纯字面量补丁样式表 `aha-theme.css`（经 `Scene.getStylesheets()` 加载、排在 modena 之后）+ `Palette` 新增 4 个面色并把 modena 中间量全换字面量；新增 `G-16` | @ACANX / CNXNC |
| v0.46.0 | 2026-10-09 | 真机（版本 `0.1.1.00054`）确认原生桌面端的启动链路与渲染期元数据已完整：issue **#35**（`QuantumToolkit` 反射）、**#37**（JNI `FindClass`）、**#39**（平台类 JNI 成员）、**#41**（效果 peer）四个 issue 全部修复并关闭（均附验证备注）；同步 `N-16` / `N-23` 为已验证、`G-08` / `G-10` / `G-11` 为已完成 | @ACANX / CNXNC |
| v0.49.0 | 2026-10-09 | 新增独立工作流 `BuildJVMArtifacts.yml`（issue #63）：dev 的 JVM 便携包出包线（`aha-desktop` + `aha-cli` → 预发行），补齐 JVM 模式在 dev 上没有产物的缺口；`D-08` 复核表述同步更正 | @ACANX |
| v0.50.0 | 2026-10-09 | 在 `BuildJVMArtifacts.yml` 增加 **JDK 27 编译产物轴**（issue #65）：矩阵扩为「平台 × JDK（25 / 27）」，JDK 27 腿用 `-Dmaven.compiler.release=27` 编出 `-jdk27` 包；新增 `N-27` | @ACANX |
| v0.51.0 | 2026-10-09 | **例外更新（ACANX 主动要求，本文件虽已冻结）**：`D-04` 结项——UI 测试口径定案（CI 只做冒烟：CLI 硬断言 + Windows GUI 存活检查；完整 UI 测试留给本地 / 真机），见 `TestingSpec.md` §5.2 与 `BuildSpec.md` §10.3；`D-06` / `D-08` / `D-13` 与发行矩阵里的 `jpackage` 表述同步为「已决策跳过」（issue #78 / #80 / #112 / #114） | @ACANX / CNXNC |

---

## 0. 阅读说明

**本文件是暂存区，不是正式规范或设计文档。**

用途：先把「待办与调整项」集中记录、逐条确认，**确认后再拆分合并到各自的正式文档**
（`Docs/DevSpec/`、`Docs/Design/`、`Docs/Guide/`）中。全部条目消化完毕后，
本文件应标记为「已废弃」或删除。

与 `Docs/PLAN.md` 的分工见该文件第 0 节。

### 0.1 条目类型

| 标记       | 含义                                               |
| ---------- | -------------------------------------------------- |
| `缺陷`     | 已核实的实际错误，会造成功能或使用问题             |
| `文档缺陷` | 文档内容与代码/事实不符                            |
| `规范偏差` | 与 `Docs/DevSpec/` 已有规范不一致                  |
| `风险`     | 尚未爆发，但已知会构成阻碍                         |
| `调研`     | 结论来自静态分析，**需动手验证**后方可作为决策依据 |
| `待决策`   | 需要人来拍板，无技术上的唯一正解                   |

### 0.2 证据等级

| 标记      | 含义                               |
| --------- | ---------------------------------- |
| ✅ 已核实 | 已通过阅读代码/配置文件确认        |
| ⚠️ 待核实 | 推断或外部知识，**未在本仓库验证** |

### 0.3 状态标记

| 标记       | 含义                                                       |
| ---------- | ---------------------------------------------------------- |
| ✅ 已完成  | 已在本仓库核实修复，无残留动作                             |
| ◐ 进行中   | 部分完成，或已改动但未收口（残留动作见条目正文）           |
| ☐ 未完成   | 未开始；若依赖前置条件，在条目正文注明                     |
| ⏸ 待决策   | 阻塞于拍板，拍板后转为 ☐ 或 ✅（见第 7 节）                |

> **注意**：本文件名 `TODO.md` 不符合 `DocumentationSpec.md` 第 1 节的 PascalCase 命名规范
> （保留名单中仅含 `README`、`AGENTS`、`SKILL`、`LICENSE`、`CHANGELOG`、`CONTRIBUTING`、
> `CODE_OF_CONDUCT`、`SECURITY`，不含 `TODO`）。严格合规应为 `Todo.md`。
> 因暂存区文件生命周期短，暂按现行名保留（`AGENTS.md` 与 `PLAN.md` 均按此名引用），
> **正式化时需一并处理**（见 E-12）。

> **需要人工执行的动作**（推送、仓库 / 分支保护设置、平台侧配置、外部环境验收——即
> 自动化做不到、又必须有人做才能收口的事）单独记在**第 11 节** `G-xx`，每条都写明
> **验收标准**（怎么算做完）。这类事项**不得只写在对话里**：对话会滚走，漏掉之后
> 既没有闭环，也无从判断「到底做过没有」。

---

## 1. 文档与仓库卫生

| 编号 | 事项                                        | 类型     | 证据 | 优先级 | 状态      | 落地文档                                      |
| ---- | ------------------------------------------- | -------- | ---- | ------ | --------- | --------------------------------------------- |
| A-07 | `BuildSpec.md` §6 的覆盖率排除项引用失准    | 文档缺陷 | ⚠️    | P2     | ◐ 进行中  | `BuildSpec.md` §6                             |
| A-08 | `Docs/` 根下的 `PLAN.md` / `TODO.md` / `Dbsx.txt` 是否收进 `Docs/AHA/` | 待决策 | ✅ | P3 | ⏸ 待决策 | `DocumentationSpec.md` §2 |
| A-09 | `PLAN.md` §8.2 出现**两个 `8.2.7`**（环境无关性 / 日志文件不生成），编号重复易致误引 | 文档缺陷 | ✅ | P2 | ✅ 已完成 | `PLAN.md` §8.2 |
| A-10 | `PLAN.md` 有两个 `## 8.` 标题（「相关文档」与「0.1.0 发布前问题清单」），后者才是正文；`### 8.4` 亦排在 `8.7` 之后 | 文档 | ✅ | P2 | ✅ 已完成 | `Docs/PLAN.md` |

### A-07 ◐ 进行中

**原问题**：`TestingSpec.md` 覆盖率小节与跨文档引用的编号可能漂移。

**复核（✅ 已核实，2026-10-07）**：

- `TestingSpec.md` §3 内部编号正常：`3.1 工具与门禁` → `3.2 排除项` → `3.3 实测值`，
  **不存在重复**。原条目「小节编号重复」的定性不成立。
- 但 `BuildSpec.md` §6 仍写：「排除项 | 必须登记理由（见设计文档第十二部分 3.3）」。

**残留动作**：核对「设计文档第十二部分 3.3」的实际指向。`Docs/AHA/AHA-Design-V1.md` 为多文档拼接
（`v3.32.0`，存在多个同名 `## 1.` / `## 2.`），该引用**极可能已失准**。
两条出路，需择一：

1. 改引 `TestingSpec.md` §3.2（该内容确在此处），并统一全仓对覆盖率排除项的引用目标；
2. 若确实指向 `Docs/AHA/AHA-Design-V1.md`，修正部分号与小节号。

> 原条目标记为 ⚠️ 待核实，本次复核**已确认 TestingSpec 侧无缺陷**，问题范围收窄为
> 「`BuildSpec.md` §6 的单一引用链未核对」。

---

### A-09 ☐ 未完成

**现象（✅ 已核实）**：`PLAN.md` §8.2 的条目表里有两个 `8.2.7`——一个是
「`SystemPromptLoaderTest` 不具环境无关性」，另一个是「日志文件长期不生成（见 §8.5）」。

**处理（2026-10-07，✅）**：`PLAN.md` §8 整体重排——`8.2` 表补齐并归位 `8.2.6`，与 §8.5 重复记录的
「日志文件长期不生成」改为 `8.2.11`；`### 8.4` 挪回 `8.3` 之后；重复的 `## 8.`（「相关文档」）
改为文末 `## 9.`。**外部引用零改动**：`SystemPromptLoaderTest` 仍为 `8.2.7`、分支保护仍为 `8.2.9`、
日志命名规则仍为 `8.6`，故 `TODO.md`、`LoggingDesign.md` 等处的指向全部保持有效。

**影响**：`PLAN.md` 正文按编号互引（如 §8.5 处提到「见 8.2.7」），编号重复会让引用指向
错误条目，也让「这一条到底做没做」变得含糊。

**残留动作**：为后者另取编号并核对全部 `8.2.x` 引用；本条目由第 11 节的留痕约定顺带发现，
与既定工作无关。

---

## 2. 代码健壮性

| 编号 | 事项                                                   | 类型 | 证据 | 优先级 | 状态      | 落地文档                               |
| ---- | ------------------------------------------------------ | ---- | ---- | ------ | --------- | -------------------------------------- |
| B-01 | `AgentEngine.run()` 空 choices 时返回 `null` 正文      | 风险 | ✅    | P1     | ☐ 未完成  | `AgentEngine`、`AgentServiceDesign.md` |
| B-02 | `AgentEngine.stream()` 的 `finishReason` 可能为 `null` | 风险 | ✅    | P1     | ☐ 未完成  | 同上                                   |

### B-01 / B-02 ☐ 未完成

**复核（✅ 已核实，2026-10-07）**：`AgentEngine.java` 现状与原始记录一致，**未改动**：

```java
// run()，第 114 行
if (response.choices() == null || response.choices().isEmpty()) {
    break;   // content 保持 null
}
...
// 第 136 行
return new AgentResponse(sessionId, content, allToolCalls, usage, finishReason);
```

```java
// stream()，第 171 / 200 行
String[] roundFinishReason = {null};
...
finishReason = roundFinishReason[0];   // 仅在 DONE 时赋值
```

即：首轮空 `choices` → `content` 为 `null`；流异常中断 → `finishReason` 为 `null`。

**残留动作**（同原建议）：

1. 明确 `AgentResponse.content()` 契约（允许 `null` 还是返回空串），写入 Javadoc
2. 调用方（`LocalAgentService` / `ChatCommand` / `RunCommand`）统一判空
3. 补单元测试覆盖「首轮空 choices」「流中断无 DONE」两条边界

## 3. 架构与治理

| 编号 | 事项                                 | 类型   | 证据 | 优先级 | 状态     | 落地文档                                         |
| ---- | ------------------------------------ | ------ | ---- | ------ | -------- | ------------------------------------------------ |
| C-02 | `ServiceLoader` 双声明的长期维护成本 | 待决策 | ✅    | P3     | ⏸ 待决策 | `ModuleConvention.md`                            |
| C-03 | 依赖治理补强（Enforcer / ArchUnit）  | 调研   | ✅    | P2     | ☐ 未完成 | `BuildSpec.md`                                   |
| C-04 | `jlink` 禁令的解除条件未登记         | 待决策 | ✅    | P3     | ☐ 未完成 | `BuildSpec.md` §7                                |

### C-02 ⏸ 待决策

**复核（✅ 已核实，2026-10-07）**：`CHANGELOG.md` 仍记载
「工具与适配器服务同时在 `module-info` 的 `provides` 与 `META-INF/services` 中声明」，
双声明**仍在**；`ModuleConvention.md` 未登记该要求。

**新增交叉信息**：E-04 指出——双声明在 native-image 场景下**反而降低了配置成本**
（GraalVM 的 `ServiceLoaderFeature` 能扫 `META-INF/services`）。决策时应一并考虑。

**残留动作**（三选一，需决策）：

1. 维持现状，在 `ModuleConvention.md` 登记「双声明为强制要求及理由」
2. 放弃 classpath 兼容，删除 `META-INF/services`
3. 用脚本/注解处理器校验两处声明**一致**——建议至少落地本项

### C-03 ☐ 未完成

**复核（✅ 已核实，2026-10-07）**：父 `pom.xml` 中**无** `maven-enforcer-plugin`、
无 `archunit` 依赖。原条目未动。

**残留动作**：评估引入 Maven Enforcer（`bannedDependencies`）或 ArchUnit 作为 JPMS 的
**补充**（非替代），把 `BuildSpec.md` §7 的约定变成构建期失败；新增依赖需与
`Constitution.md` 第 5 条选型清单同步登记。

### C-04 ☐ 未完成

**复核（✅ 已核实，2026-10-07）**：`BuildSpec.md` §7 现状仍为：

> **禁止**在 0.1 使用 `jlink`——`sqlite-jdbc` 为自动模块，jlink 不支持

**未补记解除条件**。

**残留动作**：补记解除条件——若 `sqlite-jdbc` 未来提供 `module-info`（或改用提供
`module-info` 的 JDBC 驱动），`jlink` 可显著缩小发行包，届时应重新评估。
目前写法读起来像永久约束，实际是 0.1 的临时限制。

---

## 4. 桌面端（OpenJFX，规划 0.2）

> 现状：`aha-desktop` 仍为占位模块（`module-info` + 空 JAR），但**平台依赖机制已就位**——
> 父 POM 的 5 个 `javafx-*` profile + `dependencyManagement` 已能解析本平台的 JavaFX 分类器工件，
> `aha-desktop` 已声明 `javafx-base` / `javafx-graphics` / `javafx-controls`（2026-10-08 实装并实测）。

| 编号 | 事项                                    | 类型     | 证据 | 优先级 | 状态     | 落地文档                           |
| ---- | --------------------------------------- | -------- | ---- | ------ | -------- | ---------------------------------- |
| D-03 | FXML 反射需限定 `opens`                 | 风险     | ⚠️    | P2     | ☐ 未完成 | `aha-desktop/module-info.java`     |
| D-04 | TestFX 在 JPMS + 无显示 CI 下的配置     | 风险     | ⚠️    | P2     | ✅ 已定案（2026-10-09） | `TestingSpec.md` §5.2              |
| D-06 | 发行目标平台与 CI runner 平台不匹配     | 文档缺陷 | ✅    | P2     | ◐ 已决策，待回填 | `DesktopDesign.md` §2/§3           |
| D-08 | 打包流水线已就位；`jpackage` 已决策跳过 | 风险     | ✅    | P2     | ✅ 结项（2026-10-09） | `ReleaseProcess.md` §3.3           |
| D-13 | 0.2 桌面端剩余项（记忆面板 / 扩展面板 / 身份加载顺序展示 / 会话内搜索 / 覆盖率门禁上线） | 设计缺口 | ✅ | P2 | ◐ 见 `Docs/Dbsx.txt` | `aha-desktop`、`DesktopDesign.md` §11 |
| F-16 | Maven 4 下 verify 日志出现 10 行 `[stderr]` | 缺陷 | ⚠️ | P2 | ☐ 未完成 | `Gate.yml` 日志、`aha-core` 测试 |
| N-04 | 增加 JDK 27 编译分支（对比 Leyden AOT / 原始类型 / GC 对启动与内存的影响），**先只开 Windows**，为 JDK 29 铺路 | 需求 | ✅ | P2 | ◐ 机制已就位，数据待采集 | `aha-desktop-native/pom.xml`（`native-jdk27` profile）、`native-image-args-jdk27.txt`、`DesktopNativeDesign.md` §5 |
| N-05 | 首次真机验证三条腿的下载产物（双击即用、能开窗、能对话） | 验证 | ☐ | P1 | ☐ 待工作流首跑 | `DesktopNativeDesign.md` §7 N-1 |
| N-06 | 把 §5.3 的对比表填上真实数字（原生镜像 JDK25 / JDK27 / JVM+Leyden AOT 三种形态） | 实验 | ☐ | P2 | ☐ 待采集 | `DesktopNativeDesign.md` §5.3 |
| N-07 | JDK 27 分支扩展到 Linux / macOS；二进制内部版本号带上 PR 段；体积瘦身 | 规划 | ☐ | P3 | ☐ 后续 | `DesktopNativeDesign.md` §7 N-3/N-4/N-5 |
| N-08 | 沉淀可复用技能：`.agents/skills/java-app-graalvm-native-image-compile/`（先建骨架、边做边改、达成目标才成熟） | 需求 | ✅ | P1 | ◐ 已建（v0.1.0 试验中） | `.agents/skills/java-app-graalvm-native-image-compile/SKILL.md` |
| N-09 | 把该技能迭代到**成熟**：目标平台真机跑通 + 未验证条目清零 + **在别的项目复用过一次**（四条达标判据见技能「用法」一节） | 验证 | ☐ | P2 | ☐ 待跟踪 | `SKILL.md`（成熟度）、`references/skill-lifecycle.md` |
| N-21 | 把 `graalvm-reachability-metadata` 迭代到成熟：至少两个形态不同的目标走通（GUI + CLI 已具备）+ 推断条目清零 + **tracing agent 至少启用过一次** | 验证 | ☐ | P2 | ◐ 进行中（2026-10-08：CLI + GUI 两形态 agent 均已实跑；已输出「agent 采集制度」与编排脚本；Windows/mac 平台采集与真实 native 运行待做，见 N-22） | `.agents/skills/graalvm-reachability-metadata/SKILL.md`（成熟判据） |
| N-27 | dev JVM 出包线增加 **JDK 27 编译产物**（`-Dmaven.compiler.release=27`，包名带 `-jdk27`，字节码 major=71） | 需求 | ✅ | P2 | ◐ 机制已就位（等 dev 触发 CI 验证） | `.github/workflows/BuildJVMArtifacts.yml`、`BuildSpec.md` §2、[issue #65](https://github.com/ACANX/AHA/issues/65) |

### D-03 ☐ 未完成

**残留动作**：0.2 实施时在 `aha-desktop/module-info.java` 中按 `Constitution.md` 第 3 条
包结构冻结清单（已预留 `com.acanx.module.aha.desktop.controller`）添加**限定** `opens`。

> 注：`GUIDesign.md` §8.1 建议采用「进程内直调 + JavaFX 原生控件」，若最终仍用 FXML，
> 本条适用；若完全转为代码构建 UI 则本条可降级。

### D-04 ✅ 已定案（2026-10-09）：CI 只做冒烟，深度 UI 测试留给本地 / 真机

**要定的事**：UI 测试怎么跑、在哪里跑。

**已定**：需要图形环境的测试**默认不跑**。`AhaDesktopSmokeTest` 由
`@EnabledIfSystemProperty(named = "aha.ui.tests", matches = "true")` 门禁，显式加
`-Daha.ui.tests=true` 才执行。依据是项目自己的硬规则——测试必须环境无关
（`TestingSpec.md` §5.1）——而 CI 的 ubuntu runner 无显示、本机 WSL **连 GTK 都没有**
（实测 `libgtk-3` 命中数为 0），开成默认只会满地红。

**定案（2026-10-09）**：

1. **维持纯 JUnit 驱动**，不引入 TestFX——当前冒烟测试用 `Platform.startup` + `runLater` 断言 +
   轮询等待状态，零新依赖；TestFX 只在其「真实交互模拟」有明确需求时再评估；
2. **CI 不做完整 UI 测试**，改做**两层冒烟**（口径见 `BuildSpec.md` §10.3）：
   - CLI 原生镜像：`--version` 退出码 0 且输出含本次版本号、`--help` 退出码 0（三平台，硬断言）；
   - 桌面端原生镜像：Windows 腿启动进程、观察 20 秒存活（非阻塞，只证明「能起不崩」）；
3. 已按「独立可选 job、不进必需检查」落地：两条 native 工作流本就是可选管线，新增的冒烟步骤
   遵守 `BuildSpec` §7 的「可选管线不得进必需检查 + 步骤级容错」。

**验收标准（口径已调整）**：原定「至少一条平台在 CI 上真实跑过窗口冒烟测试」**不再追求**——
CI 无 GPU，Prism 回退到软件渲染 / WARP，覆盖不到真实 D3D 路径，跑完整 UI 测试性价比低且结论不可信。
`AhaDesktopSmokeTest`（JVM 下的真窗口冒烟）继续**只在本地 / 真机**按 `-Daha.ui.tests=true` 运行；
CI 侧的等价物是上面第 2 条的桌面端原生镜像冒烟。汇总口径见 `TestingSpec.md` §5.2。

### D-06 ◐ 已决策（2026-10-08），文档待回填

**决策**：**支持承诺只有 Windows + Linux 双平台**；macOS **要求 CI 能打出包**，
但**不要求跑测试**——开发资源有限，不在 macOS 上投入测试维护。

**复核（✅ 已核实）**：`DesktopDesign.md` v1.1.0 §2 已改为指向 `GUIDesign.md` 并标注
「WebView / FXML 组合从未真正决策，待评审」——**这是 D-07 范畴的进展，不是 D-06**。
§2 仍**未列目标平台清单**（2026-10-07 再次实查确认）；`Build.yml` 矩阵**已加 macOS 腿**
（`F-06`，定位是「演示与可选」，见 `BuildSpec.md` §4.1——原先「仍无 macOS runner」的记述已失效）。

**残留动作**：在 `DesktopDesign.md` §2 列出目标平台清单（Windows / Linux / macOS），
与 §3 的 CI 分平台策略、`Build.yml` 的 runner matrix 三者对齐。

### D-08 ✅ 结项（2026-10-09）：打包流水线已就位，jpackage 已决策跳过

**复核（✅ 已核实）**：`Build.yml` 仍无打包 job（dev 的 JVM 便携包已由独立工作流
`BuildJVMArtifacts.yml` 承担，见 issue #63）；`Release.yml` 仍只上传 `dist.zip`。

**残留动作**：0.2 在 `Build.yml` 新增按平台的打包 job，并扩展 `Release.yml` 的产物矩阵
（桌面端安装包需另设产物）。

**补充（`D-06` 决策，2026-10-08）**：打包 job **要覆盖 macOS**，但 macOS 腿**只打包、不跑测试**；
必需检查仍是 Windows + Linux。

**已落地（2026-10-08）**：`Release.yml` 新增 `desktop` 作业（矩阵 `ubuntu` / `windows` / `macos`，
`fail-fast: false`），每条腿跑 `./mvnw clean verify` 后由产物名 + 依赖树**双向自证**平台分类器，
再把 `aha-desktop-<版本>-<系统>-<架构>.zip` 上传到 release 页面；CLI 资产同步改名
`aha-<版本>-cli.zip`。自证脚本已本地实跑（正常路径重命名成功；喂错期望值被 `::error::` 拦住
且不动物件）。

**已决策跳过（2026-10-09）**：`jpackage` 自包含安装包（MSI / DEB / DMG）**不采用**——发行形态最终
只保留「JVM JAR 聚合包（解压后脚本启动）」与「原生镜像二进制」两种，「免装 JDK」由后者覆盖，
也不必再维护安装器 + 签名 / 公证链路。理由与口径见 `ReleaseProcess.md` §3.3（issue #78 / #112）。

### F-17 ☐ 新增（2026-10-08）：`-Djavafx.platform=win` 时构建输出 `recursive variable reference: javafx.platform`

**现象**：在 WSL 里用 `-Djavafx.platform=win` 打 Windows 便携包时，构建日志出现两行
`[ERROR] recursive variable reference: javafx.platform`；**构建仍然成功**，产物名与内容都正确。

**影响**：目前只是日志噪音；但它可能意味着某个 `${javafx.platform}` 的插值路径不健康，
值得在 Windows 原生构建（profile 自动生效、无 `-D`）时对照确认一次是否同样出现。

**验收标准**：在 Windows 原生 `./mvnw -pl aha-desktop -am package` 输出里确认有无该行；
若有则定位到具体插件/属性并消除。

---

### F-16 ☐ 新增（2026-10-08）：Maven 4 下 verify 日志出现 10 行 `[stderr]`

**现象**：一次 `./mvnw clean verify`（Maven 4.0.0-rc-7）中共出现 10 行 `[stderr]`，
而同一提交在 Maven 3.9.11 下为 0 行——与 `F-11` 治好的「测试输出污染构建日志」同类。

**已查明来源**（2026-10-08，据 Windows CI 日志）：这些 `[stderr]` 是 **JDK 的 native-access 警告**，
由 `sqlite-jdbc` 触发，出现在 `EndToEndTest` 等真正加载 SQLite 的用例里：

```
[stderr] WARNING: java.lang.System::load has been called by org.sqlite.SQLiteJDBCLoader
                  in module org.xerial.sqlitejdbc
[stderr] WARNING: Use --enable-native-access=org.xerial.sqlitejdbc to avoid a warning ...
```

不是测试输出污染，而是 JVM 提示缺 `--enable-native-access`。

**修法（待评估）**：给 surefire 加该参数。注意不能简单写死 `argLine`——JaCoCo 的
`prepare-agent` 也通过 `argLine` 注入探针，覆盖它会让覆盖率失效；正确写法是
`<argLine>@{argLine} --enable-native-access=org.xerial.sqlitejdbc</argLine>`，
但 `-Djacoco.skip=true`（`Build.yml` 的快检查）下该属性不存在，`@{argLine}` 会原样传入而报错。
需要先验证这两种情形都能跑通再改。**CLI 的启动脚本 `bin/Aha.sh` 早已带这个参数**，
所以最终应当一致。

**验收标准**：CI 日志（`Build` 与 `Gate`）中 `[stderr]` 行数为 0。
**不要为了复现它而在本地重跑 verify**（慢检查只在 CI 跑）。

---

## 5. GraalVM 原生编译（0.2+ 评估）

> **前置共识**：`DesktopDesign.md` §3 已确立
> 「桌面端 WebView 不支持 native-image，native-image 流水线仅覆盖 CLI + Core + Tools」。
> 本节条目**全部未开工**，多数结论来自外部知识（⚠️）。

| 编号 | 事项                                                   | 类型   | 证据 | 优先级 | 状态      | 落地文档                                |
| ---- | ------------------------------------------------------ | ------ | ---- | ------ | --------- | --------------------------------------- |
| E-01 | `ModuleLayer` 与 native-image 的 closed-world 根本冲突 | 待决策 | ✅    | P0     | ⏸ 待决策  | `ExtensionSystemDesign.md`、`BuildSpec.md` |
| E-02 | `sqlite-jdbc` 的 JNI native library 嵌入               | 风险   | ⚠️    | P1     | ☐ 未完成  | `BuildSpec.md`                          |
| E-03 | Jackson 3 反射配置                                     | 风险   | ⚠️    | P1     | ☐ 未完成  | `BuildSpec.md`                          |
| E-04 | `ServiceLoader` 需资源配置                             | 风险   | ⚠️    | P1     | ☐ 未完成  | `BuildSpec.md`                          |
| E-05 | picocli 反射配置                                       | 风险   | ⚠️    | P2     | ☐ 未完成  | `BuildSpec.md`                          |
| E-06 | JLine 终端能力探测依赖 native 组件                     | 风险   | ⚠️    | P2     | ☐ 未完成  | `BuildSpec.md`                          |
| E-07 | Log4j2 在 native-image 下的兼容性                      | 风险   | ⚠️    | P2     | ☐ 未完成  | `BuildSpec.md`                          |
| E-08 | `java.net.http` 的 TLS/SSL 配置                        | 风险   | ⚠️    | P2     | ☐ 未完成  | `BuildSpec.md`                          |
| E-09 | 虚拟线程在 native-image 的支持现状                     | 调研   | ⚠️    | P2     | ☐ 未完成  | `Constitution.md` 第 5 条               |
| E-10 | 覆盖率门禁在 native 产物下不适用                       | 待决策 | ✅    | P2     | ⏸ 待决策  | `BuildSpec.md` §6/§8、`TestingSpec.md`  |
| E-11 | 发行布局（bin+lib）与单文件产物冲突                    | 待决策 | ✅    | P2     | ⏸ 待决策  | `BuildSpec.md` §7                       |
| E-12 | `TODO.md` 文件名不合规（原 D-05 自身）                 | 规范偏差 | ✅  | P3     | ⏸ 待决策  | `DocumentationSpec.md` 第 1 节          |

### E-01 ⏸ 待决策（本节最关键）

**问题**：native-image 在**构建期**静态分析可达代码（closed-world），而 `ModuleLayer`
在**运行时**动态创建模块层。`Constitution.md` 第 5 条把「扩展运行时 = `ModuleLayer` +
`ExtensionRuntime`」列为**不可更换**（✅ 已核实，第 145 行）。

**冲突（✅ 架构事实已核实）**：

- `ModuleLayer` 定义的类在构建期不存在 → native-image 无法预编译
- 因此 **native 产物中扩展系统无法以 `ModuleLayer` 形态工作**

**难点**：`aha-core` **包含 `core.extension` 包**（`ExtensionManager`、`RegistrationTracker`），
只要在类路径上就会被可达性分析触及。

**残留动作（需拍板）**：

1. 明确 native 产物中**扩展功能的降级策略**：完全禁用／仅支持编译期静态注册／不发布
2. 若选择「禁用」，确认 `core.extension` 能否被 native-image 分析**排除**，
   或需在构建期以 profile 剔除
3. 在 `ExtensionSystemDesign.md` 中**显式记录**该限制
4. 评估 `Constitution.md` 第 5 条是否需补充 native-image 例外条款（涉及第 8 条修订程序）

> 若此冲突无法优雅解决，可能影响「native-image 是否值得做」的整体判断。
> **建议先做本条决策，再评估其余条目。**

### E-02 ~ E-09 ☐ 未完成

**复核（2026-10-07）**：全部处于调研/未开工状态，无仓库内变化。

**补充（E-03）**：父 POM `jackson.version` 已升至 `3.2.3`；`aha-core/module-info.java`
的 3 处 `opens ... to tools.jackson.databind` 仍存在（reflect 面未变）。
Jackson **3.x** 的 GraalVM metadata 成熟度仍需实测。

**补充（E-04）**：与 C-02 交叉——`META-INF/services` 双声明在 native-image 下
**由冗余变保险**，决策 C-02 时应一并考虑。

### E-10 ⏸ 待决策

**复核（✅ 已核实）**：`BuildSpec.md` §6 门禁与 §8「唯一验收标准」均未涵盖 native 产物。

**残留动作**：为 native 产物定义独立冒烟测试；在 §8 补记验收标准，
明确「JaCoCo 门禁不覆盖 native 产物」；并明确 native 构建**是否绑定 `verify`**
（建议独立流水线）。

### E-11 ⏸ 待决策

**复核（✅ 已核实）**：`BuildSpec.md` §7 仍规定「发行包固定为 JPMS 模块路径目录
（`Dist/bin` + `Dist/lib`）」；native-image 单文件产物与之不兼容。

**已落地（2026-10-09）**：发行矩阵已写入 `BuildSpec.md` §7「发行形态矩阵」，口径见
`ReleaseProcess.md` §3.3：

| 发行形态                        | 目标用户       | 产物            | 状态      |
| ------------------------------- | -------------- | --------------- | --------- |
| JPMS 模块路径目录（`Dist/`）    | 需 JVM、可调优 | `aha-cli-<版本>.zip` | ✅ 已实现 |
| 桌面端便携包（按平台）          | 需 JDK 25      | `aha-desktop-<版本>-<系统>-<架构>.zip` | ✅ 流水线已就位 |
| native-image 单文件             | 免 JVM、启动快 | 平台可执行文件  | ✅ 流水线已就位（试验性，随 `dev` push 出包） |
| ~~`jpackage` 安装包~~           | —             | —              | ❌ 已决策跳过（2026-10-09） |

### E-12 ⏸ 待决策（新增条目）

**现象**：文件名 `TODO.md` 与 `DocumentationSpec.md` 第 1 节 PascalCase 规范不符
（保留名单不含 `TODO`）。

**依据（✅ 已核实）**：`DocumentationSpec.md` 第 1 节保留名单；
`bin/CheckDocs.py` **不检查文件名**，因此**不阻塞 CI**。

**建议动作**：三选一——

1. 保持 `TODO.md`（暂存区生命周期短，消化完即删）——**当前选择**
2. 改名为 `Todo.md`，同步修订 `AGENTS.md`、`PLAN.md` 的链接与 `DocumentationSpec.md`
   的保留名单说明
3. 把 `TODO` 加入 `DocumentationSpec.md` 第 1 节保留名单（需走规范变更程序）

---

## 6. 记忆能力（0.6 起）

现状：`memory` 表已建、`storeMemory` / `recall` 接口已定义，但**全仓无调用方**——模型既不写也不读。
详见 [MemoryStorageDesign.md](Design/MemoryStorageDesign.md) 第 6~8 节。

### 6.1 推进顺序（与载体选型无关，可先做）

| 序 | 条目 | 说明 | 状态 |
|---|---|---|---|
| 1 | `storeMemory` 加 upsert | 现状为纯 `INSERT`，同一 key 写两次会产生重复行 | ☐ 未完成 |
| 2 | 作用域改为项目级 | **已定**：`~/.aha/Project/<项目ID>/Memory/`，项目 ID 规则已实现（`ProjectId`） | ✅ 已完成 |
| 3 | 记忆工具（模型侧）+ `/memory` 命令（用户侧）+ 候选区 | **建议从这里开始**：能立刻验证记录是否真的可用 | ☐ 未完成 |
| 4 | `Memory.ModelWrite` 接入配置与权限 | `off` / `candidate` / `direct`；受 `Tools.Enabled` 与 `PermissionPolicy` 双重管辖 | ☐ 未完成 |
| 5 | 手动 `/memory curate`（整理） | 去重合并、升降级、冲突检测，**必须可回滚** | ☐ 未完成 |
| 6 | 载体与向量（RAG） | 与 0.6 一起定；实测依据见 6.3 | ⏸ 待决策 |

**复核依据（✅ 已核实，2026-10-07）**：

- 序 1：`SqliteMemoryStore.java:229` 仍为 `INSERT INTO memory(...)`，**无 upsert**
  （对比 `session` 表已用 `INSERT OR REPLACE`）
- 序 2：`CHANGELOG.md` 已记载「项目级记忆位置与项目 ID：`~/.aha/Project/<项目ID>/Memory/`，
  项目 ID 由项目根绝对路径推导」→ **已完成**
- 序 3：CLI 现有斜杠命令为 `/help` `/config` `/context` `/session` `/model` `/clear`
  `/tool` `/compact` `/autocompact` `/exit` `/memory`，**无 `/memory`**

### 6.2 写入策略（已定）

| 级别 | 谁写 | 是否确认 |
|---|---|---|
| 项目记忆 | 模型可**直接写** | 默认不确认 |
| 候选区 | 模型可写，整理流程也可产生 | 按配置 |
| **长期记忆** | **只能由整理流程筛选提取生成** | 产出后 review |

原则：**先记录存档，后利用**。未经检验的召回比没有记忆更糟。

### 6.3 载体选型（未定，⏸ 待决策）

候选：表 / MD 文件 / 混合（MD 为真源 + 表为可重建派生索引）。实测依据：

| 场景 | 规模 | 实测 |
|---|---|---|
| 2,000,000 行文本，`LIKE` 全表扫 | 291 MB | 41 ms |
| 50,000 条 × 384 维向量，暴力相似度扫描 | 98 MB | **100 ms/次，且随条数线性增长** |
| 5,000 个小 MD 文件，逻辑 vs 实际占用 | 543 KB → 20 MB | **块浪费 37 倍** |

结论：瓶颈不在文本而在向量；MD 的代价不在读慢而在小文件管理。

### 6.4 跨环境共享（⏸ 待决策）

**默认行为**：Windows 与 WSL 的路径不同，会推导出两个项目 ID，
**默认视为两个独立项目**。这是刻意保留的默认，不做隐式归一化。

**待补机制**：允许**显式配置**共享或复用。三方案（显式项目 ID / 别名表 / 共享目录）
与代价见 [MemoryStorageDesign.md](Design/MemoryStorageDesign.md) 8.6；
**已由 [PLAN.md](PLAN.md) §3 登记为「暂不实施」**（与载体选型一同推进）。

### 6.5 其他待定

- 路径大小写：Windows 上 `E:\GitRepo` 与 `e:\gitrepo` 会得到不同 ID
  （不统一是为了与 Claude Code 一致）
- 用户级与项目级记忆的合并去重
- 候选区的载体：`Candidate/` 子目录，还是同一索引的 `status` 字段
- 容量与淘汰策略
- 会话历史的查看与统计（`aha session list`、`/stats`、`aha stat`）
  —— 已由 [PLAN.md](PLAN.md) §2 关联（需先完成用量落库）

---

## 7. 待决策项汇总

以下条目**需要人来决策**，技术上无唯一正解。建议按优先级依次拍板：

**只有 4 项与 0.1 相关**，其余属 0.2+、桌面端或 native-image 路线。

> **2026-10-08 已拍板 5 项**（详见各条目正文）：
>
> | 编号 | 决策 |
> |---|---|
> | `C-01` | JPMS **非强制**；优先 OpenJFX，必要时 JPMS 让路 |
> | `D-05` | 桌面端**纳入门禁 + 单独阈值**，0.2 初期 0.30 → 收尾向 0.70 对齐 |
> | `D-06` | 支持承诺 **Windows + Linux**；macOS **只打包、不测试** |
> | `D-07` | **进程内直调优先**，本地 HTTP + WebView 降为兜底 |
> | `D-09` | 桌面端**需要内置工具**（依赖矩阵已补 `tool ← desktop`） |
这 4 项的建议如下——**仅为建议，仍需拍板**：

| 编号 | 决策问题 | 建议 | 理由 |
| ---- | ---- | ---- | ---- |
| H-01 | 启动标志默认风格 | **维持像素风**（现状 `auto` = 像素风优先） | 它挂在能力探测之后，旧 CMD / 16 色会自动降级为线框风，降级路径已就位；像素风也正是本项目的辨识度所在 |
| E-12 | `TODO.md` 是否改名 | **维持 `TODO.md`** | 这是社区通用名；改名会让贡献者、脚本与 DevLog 的引用一起失配，收益只是「规范化」。更省事的做法是在 `DocumentationSpec.md` §1 为它记一条命名例外 |
| A-08 | `PLAN.md` / `TODO.md` / `Dbsx.txt` 是否收进 `Docs/AHA/` | **维持现状** | `Docs/AHA/` 的定位是「权威设计文档」；`PLAN.md`/`TODO.md` 是活的状态台账，放根目录更显眼；`Dbsx.txt` 是用户原始输入，宜先定去留（§8.3.4）再谈归档 |
| F-07 | `.ps1` 是否纳入检查、`Script/` 与 `bin/` 是否合并 | **纳入检查 + 合并进 `bin/`** | 脚本规约要么全查、要么别立；两个目录职责重叠会长期制造「改了这个忘那个」；合并后 `CheckScripts.py` 只需覆盖一个目录 |

| 顺序 | 编号 | 决策问题                                                     | 影响范围                     | 状态     |
| ---- | ---- | ------------------------------------------------------------ | ---------------------------- | -------- |
| 1    | E-01 | native 产物中扩展系统的降级策略？`ModuleLayer` 与 native-image 冲突如何处置？ | 决定 native-image 是否值得做 | ⏸ 待决策 |
| 2    | C-01 | JPMS 的投入产出错配是否接受？扩展路线图推迟时如何处理？      | 架构根基                     | ✅ 已决策（2026-10-08） |
| 3    | E-11 | 发行矩阵如何定义？三种形态是否并存？                         | 打包与文档全局               | ⏸ 待决策 |
| 4    | E-10 | native 产物的验收标准与 CI 归属？                            | 质量基线                     | ⏸ 待决策 |
| 5    | C-02 | `ServiceLoader` 双声明：维持／删除／加校验？                 | 维护成本（与 E-04 相关）     | ⏸ 待决策 |
| 6    | D-06 | 桌面端目标平台是否含 macOS？                                 | CI 与打包                    | ✅ 已决策（2026-10-08） |
| 7    | D-05 | 桌面端覆盖率：纳入门禁／单独阈值／永久排除？                 | 质量基线                     | ✅ 已决策（2026-10-08） |
| 8    | D-07 | 桌面端技术选型：进程内直调（8.1）vs 本地 HTTP + WebView（8.2）？ | 0.2 全部实现                 | ✅ 已决策（2026-10-08） |
| 9    | E-12 | `TODO.md` 文件名是否改名？                                   | 文档规范一致性               | ⏸ 待决策 |
| 10   | D-09 | 桌面端是否需要内置工具（引入 `aha-tool`）？                  | 依赖矩阵与 `Constitution.md` | ✅ 已决策（2026-10-08） |
| 11   | A-08 | `Docs/` 根下的 `PLAN.md` / `TODO.md` / `Dbsx.txt` 是否收进 `Docs/AHA/`？ | 文档存放规范 | ⏸ 待决策 |
| 12   | F-07 | `Script/PowerShell/CountJavaLoc.ps1` 是否纳入检查、`Script/` 与 `bin/` 是否合并？ | 检查覆盖面与目录约定 | ⏸ 待决策 |
| 13   | H-01 | 启动标志默认风格：像素风（现状）还是线框风？ | 首屏观感 | ⏸ 待决策 |
---

## 8. 落地去向映射

确认后，各条目应合并到以下文档。**建议按此顺序推进**（先易后难、先无争议后有争议）：

### 阶段一：无争议的缺陷修复

| 条目 | 目标文档                | 变更性质   | 状态     |
| ---- | ----------------------- | ---------- | -------- |
| A-07 | `BuildSpec.md` §6       | 核对引用链 | ◐ 进行中 |

### 阶段二：规范补充（需评审）

| 条目 | 目标文档                                 | 变更性质                      | 状态     |
| ---- | ---------------------------------------- | ----------------------------- | -------- |
| C-01 | `Constitution.md` 第 4 条、`ModuleConvention.md` | 补充 JPMS 存在理由与复核点 | ☐ 未完成（待决策） |
| C-02 | `ModuleConvention.md`              | 补充 `ServiceLoader` 声明要求 | ☐ 未完成（待决策） |
| C-03 | `BuildSpec.md` §7                  | 补充依赖治理手段（若采纳）    | ☐ 未完成 |
| C-04 | `BuildSpec.md` §7                  | 补充 `jlink` 禁令解除条件     | ☐ 未完成 |
| D-05 | `TestingSpec.md` §3                | 补充 Desktop 覆盖率策略       | ☐ 未完成（待决策） |
| E-10 | `BuildSpec.md` §6/§8               | 补充 native 产物验收标准      | ☐ 未完成（待决策） |
| A-08 | `DocumentationSpec.md` §2 | 明确 `Docs/` 根下暂存区文件的存放位置 | ⏸ 待决策 |
| F-07 | `BuildSpec.md` §8.1、`DocumentationSpec.md` §2 | 明确 `.ps1` 是否纳入检查、`Script/` 目录去留 | ⏸ 待决策 |
| E-12 | `DocumentationSpec.md` 第 1 节     | 文件名保留名单（若采纳）      | ☐ 未完成（待决策） |

### 阶段三：设计与实现（0.2 前）

| 条目                    | 目标文档                                                | 变更性质                     | 状态     |
| ----------------------- | ------------------------------------------------------- | ---------------------------- | -------- |
| B-01 / B-02             | `AgentEngine.java`、`AgentServiceDesign.md`             | 明确契约 + 补测试            | ☐ 未完成 |
| D-01 ~ D-04、D-08       | `DesktopDesign.md`、`DesktopDesign` 打包/测试小节、`Build.yml` | 新增线程模型、打包、测试小节 | ◐ 骨架已就位 |
| D-06                    | `DesktopDesign.md` §2/§3、`Build.yml`                   | 平台清单对齐                 | ◐ 已决策，待回填 |
| E-02 ~ E-09             | `BuildSpec.md`（新增 native 章节）                      | 待验证后登记                 | ☐ 未完成 |
| E-11                    | `BuildSpec.md` §7                                       | 发行矩阵                     | ☐ 未完成（待决策） |
| 6.1 序 1、3、4、5       | `MemoryStorageDesign.md`、`aha-core`、`aha-cli`         | 记忆写入与命令落地           | ☐ 未完成 |

### 阶段四：本文件处置

| 条目               | 动作                                                         | 状态     |
| ------------------ | ------------------------------------------------------------ | -------- |
| E-12（本文件自身） | 全部条目消化后，将本文件标记为「已废弃」或删除；若长期保留，改名 `Todo.md` 以符合 `DocumentationSpec.md` 第 1 节 | ⏸ 待决策 |

---

## 9. 复核结论汇总（2026-10-07）

本轮对全部条目逐条对照仓库现状复核，结论如下。

> **本小节是 2026-10-07 当日快照，不随后续变动更新。** 之后新增的条目
> （`B-04`~`B-07`、`F-01`~`F-06`）与状态变化见第 2 节汇总表与第 10 节；
> 例如 `B-03` 已于同日收口为 ✅ 已解决，本快照中的「◐ 进行中」是当时的记录。

### 9.1 统计

| 状态     | 条目数 | 编号                                                         |
| -------- | ------ | ------------------------------------------------------------ |
| ✅ 已完成 | **7**  | A-01、A-02、A-03、A-04、A-05、A-06、6.1-序 2                  |
| ◐ 进行中  | **4**  | A-07、B-03、D-07                                             |
| ⏸ 待决策  | **9**  | C-01、C-02、D-05、E-01、E-10、E-11、E-12、6.1-序 6、D-09（含决策前置） |
| ☐ 未完成  | **16** | B-01、B-02、C-03、C-04、D-01~D-04、D-06、D-08、D-09、E-02~E-09、6.1-序 1/3/4/5 |

### 9.2 本轮新发现

| 项 | 说明 |
|---|---|
| **D-09** | `aha-desktop` 未依赖 `aha-tool`，桌面端当前形态**不具备任何工具能力**；`Constitution.md` 第 4 条依赖矩阵亦未覆盖此边 |
| **B-03 版本漂移** | ✅ 已解决：文档改为主版本线，并在 `BuildSpec.md` §7 立「版本单一来源」规则防复发 |
| **A-07 定性修正** | `TestingSpec.md` 内部编号**无重复**，问题收窄为 `BuildSpec.md` §6 的单一引用链 |
| **E-12** | 原文档中「见 D-05」的文件名合规问题**编号错误**（D-05 是覆盖率排除项），现独立为 E-12 |

### 9.3 本轮已处理的文档卫生问题

| 问题 | 处理 |
|---|---|
| 文档正文后附有**完整重复副本** | 已删除，仅保留单一正文 |
| 文末误粘贴**会话日志与 `file-write` 权限报错**（含 `session=c1624777-…`、工具入参全文） | 已删除 |
| 全部条目表**无状态列**，已完成项与未开始项无法区分 | 已为所有表格新增「状态」列 |
| `PLAN.md` §4 登记的「`TODO.md` 重复内容清理」 | 本条即对该项的落实 |

---

## 10. CI、门禁与工程效能

> 本节于 2026-10-07 追加在文件末尾，而非按编号插在前部：§0.3 与 §7 之间已有
> 「见第 7 节」这类内部引用，插号会连带出错。

| 编号 | 事项 | 类型 | 证据 | 优先级 | 状态 | 落地文档 |
| ---- | ---- | ---- | ---- | ------ | ---- | -------- |
| F-07 | `Script/PowerShell/CountJavaLoc.ps1` 未纳入 `CheckScripts.py`（`.ps1` 不在检查范围）；`Script/` 与 `bin/` 目录职责重叠 | 待决策 | ✅ | P3 | ⏸ 待决策 | `BuildSpec.md` §8.1、`DocumentationSpec.md` §2 |
| F-14 | `.github/**/*.yml` 没有任何本地检查：`bin/CheckScripts.py` 只覆盖 `.bat`/`.cmd`/`.sh`/`.py`，工作流语法写错只能等 GitHub 判，反馈环路长 | 工程效能 | ⏸ | P2 | ⏸ 待决策 | `bin/CheckScripts.py` |
| F-15 | 文档标题/表行**编号重复**只能靠人工看：本轮 `PLAN.md` `A-09`/`A-10` 与两份 `Design/` 文档的重号都是事后肉眼发现 | 工程效能 | ⏸ | P3 | ⏸ 待决策 | `bin/CheckDocs.py` |

### F-07 ⏸ 待决策

**现象（✅ 已核实）**：`Script/PowerShell/CountJavaLoc.ps1` 是一段统计 Java 代码行数的
辅助脚本，但 `bin/CheckScripts.py` 的检查范围只含 `.bat`/`.cmd`/`.sh`/`.py`/`.gitattributes`/
`.gitignore`，**不含 `.ps1`**，因此它的编码与行尾没有任何保障；同时 `Script/` 与 `bin/`
两个目录都放脚本，职责重叠。

**待拍板**：两件事——① 是否把 `.ps1` 纳入检查（需要先定编码与行尾规约，`.ps1` 与
`.cmd` 的约束不同）；② `Script/` 是否并入 `bin/`。

**为什么记在这**：这是取舍问题（纳入检查会增加维护面），没有技术上的唯一正解。

---

## 11.1 原生镜像管线（DesktopNative）待观察项

- [ ] **N-10**：下一次 `DesktopNative` 运行确认「四条腿都绿、且摘要逐腿写清结论」；
      windows-x64 的工具链自证是否已因 `cmd //c` 修复；
      产物自证的诊断输出会揭示「构建成功但自证失败」的真正原因（此前不可见）。
- [ ] **N-15**：复核「改名 / 上传制品」不再被静默跳过。
      背景：2026-10-08 实测——**带 continue-on-error 的步骤一旦非零退出，它的 outputs
      不会被发布**，于是 `if: steps.verify.outputs.produced == 'true'` 静默变 false，
      「改名/上传」被跳过而作业仍为绿（macOS 腿就是这样丢掉产物的）。
      已修：产物自证开头先写 `produced=false` 兜底、结尾强制 `exit 0`；
      执行证据判据由 `*.build_artifacts.txt`（macOS 不产）改为 `*build-report.*`。
      复核判据：三条 jdk25 腿都出现「改名」与「上传制品」两个 ✓（不再是 skipped）。
- [ ] **N-13**：把资源清单从「宽通配」收窄成 `resource-config.json` 精确清单。
      背景：首次真编的镜像里 **27.69 MiB 是内嵌资源**（`byte[]`），
      来自 `.*\.(png|…|dll)$` 这种宽通配——把大量无关文件也吃了进去，
      是目前最大的一处体积优化余地（见 `DesktopNativeDesign.md` §5.4）。
      **前置条件**：先完成真机走查（N-05），否则漏一项资源的后果是「运行期缺文件」而不是构建失败。
- [ ] **N-14**：删除弃用的 `--enable-url-protocols`（改用 reachability metadata）。
      元数据已就位（`aha-desktop/src/main/resources/META-INF/native-image/...`），
      删除前必须满足：① 原生二进制完成一次真实对话（HTTPS 成功）；
      ② 构建报告里没有 http / https 协议处理器的未决条目。
      这是「静默坏掉」风险项——删掉不会构建失败，只会让产物发不出请求。
- [ ] **N-12**：确认「静默跳过」已根除。判据三条：① 各腿的「确认 native-image 真的执行过」
      步骤输出「执行证据：aha-desktop-native.build_artifacts.txt」；
      ② 三条 jdk25 腿的 `上传制品` 不再是 `skipped`；
      ③ `native-publish` 真正创建 `native-v<版本>` 预发行版并挂上 zip。
      背景：2026-10-08 事故——`native.skip` 的默认值写在模块自己的 `<properties>` 里，
      赢过了父 POM 中 profile 的覆盖，导致 native-image 被静默跳过，
      CI 全绿却零产物零报错（见 `Docs/Troubleshooting/TS-202610-NativeSkipSilentOverride.md`）。
- [ ] **N-11**：确认 `native-*` 作业**没有**被加进任何分支保护的必需检查
      （它现在即使失败也不会红，但契约上仍不该出现，见 `BuildSpec.md` §8.1）。
      背景：修完 #26（主类注册）后，二进制下一处报 `ClassNotFoundException:
      com.sun.javafx.tk.quantum.QuantumToolkit`。已按启动链路（工具包 / 三平台 Glass 工厂 /
      四条 Prism 管线 / 渲染器 / 全部 212 个 stock shader / Glass 原生回调 / 图片解码 / 字体 /
      AHA 自身被 Jackson 反射的记录）一次性补进
      `reachability-metadata.json`（2026-10-08 已用 tracing agent 在 Linux 上采集并补齐 47 条，总计 340 条；Windows / macOS 待采，见 `N-22`）。
      **第一轮真机结果（2026-10-08）**：#35 的反射修复生效，但暴露下一层——
      Glass 初始化处 `NoClassDefFoundError: java/lang/Runnable`（issue #37，JNI 可达类未注册）。
      已补 `jni-config.json`（62 条，静态扫描 openjfx 三平台 native 源码的 `FindClass`），
      待 CI 重出包后复跑；见 [TS-202610-JniFindClassSegfault.md](Troubleshooting/TS-202610-JniFindClassSegfault.md)。
      **第二轮真机结果（2026-10-09）**：JNI 可达类修好后，暴露下一层——
      `WinWindow.<clinit>` 报 `NoSuchMethodError: …WinWindow.notifyMoving(IIIIFFIIIIIII)[I`（issue #39）：
      平台实现类**自己声明**的成员没登记（#37 只扫了 `FindClass` 字面量，平台子类不经 `FindClass`）。
      已改扫 `Get*ID` 的目标类，`jni-config.json` 由 62 条补到 **85 条**（Win* / Mac* 平台实现类 +
      `EventLoop`），待 CI 重出包后复跑；见 [TS-202610-WinWindowJniMemberMissing.md](Troubleshooting/TS-202610-WinWindowJniMemberMissing.md)。
      **最终真机结果（2026-10-09，版本 `0.1.1.00054`）**：三层全部通过——原生包双击即可开窗，
      不再出现 `ClassNotFoundException` / `NoClassDefFoundError` / `NoSuchMethodError`，
      可正常进入 GUI 并完成交互；issue #35 / #37 / #39 均已关闭。
      验收标准：① 下载对应平台的原生包，双击能开窗、不报 `ClassNotFoundException` /
      `MissingReflectionRegistrationError` / `NoClassDefFoundError`；② 能完成一次真实对话（与 `N-14` 合并验证）；
      ③ 若仍缺类名，按同一格式补进元数据并回写 `DesktopNativeDesign.md` §6.1 与
      对应 `DevLog`。
      依据：[TS-202610-QuantumToolkitMissing.md](Troubleshooting/TS-202610-QuantumToolkitMissing.md)、
      `Docs/Design/DesktopNativeDesign.md` §6.1。
- [ ] **N-17**：改用 GraalVM tracing agent 采集可达性元数据（手写清单的「抄底」方案）。
      背景：`N-16` 与 issue #35 已证明——**手工枚举只能做到「已知缺口已闭」，无法证明完整**；
      本次就靠审计才发现 Jackson 3 不随附元数据。做法建议（任选一）：
      ① 在 `DesktopNative.yml` 的各腿先跑 JVM 产物 + `-agentlib:native-image-agent=...`
      （Linux 腿用 `xvfb-run`；需一个「启动后自动退出」的自检开关，正好与 `N-05` 合并），
      把生成的 `reachability-metadata.json` 作为 native-image 输入；
      ② 或在 GraalVM JDK 下跑现有单测（无 GUI）采集 core 侧反射，与手写的 JavaFX 清单合并。
      验收标准：原生产物能连续跑完「开窗 → 读配置 → 存配置 → 一轮对话 → 退出」
      且无 `MissingReflectionRegistrationError` / `ClassNotFoundException`。
      范围应覆盖 CLI（`aha-cli-native`）：CLI 的 picocli / JLine 元数据同样是手写 +
      推导，不能假定比桌面端更完整。
- [ ] **N-19**：真机验证 CLI 原生镜像（`aha-cli-native`）是否完整可用。
      背景：元数据来自三处——picocli 注解处理器（生成）、JLine Signals 补充（手写）、
      AHA 自身 Jackson 记录（手写）；均为推导，**未在真机跑过**。
      验收标准：① 下载三平台产物，`--version` / `--help` 正常；② 非交互 `run "..."`
      能完成一次真实对话（顺带验证 HTTPS 与配置读取）；③ 交互式进入后能输入、
      能 Ctrl+C 打断当前生成而不退出进程（验证 `sun.misc.Signal` 注册）；
      ④ 无 `ClassNotFoundException` / `MissingReflectionRegistrationError`；
      ⑤ 若仍缺项，按同一格式补 `CliNativeDesign.md` §4 与元数据。
      依据：`Docs/Design/CliNativeDesign.md` §6。

- [ ] **N-22**：在 Windows / macOS 上各跑一轮 tracing agent，补齐平台专属类。
      背景：agent 只采到**跑在哪个平台**的类。Linux（WSLg）采集出现
      `com.sun.glass.ui.gtk.GtkView` / `GtkWindow` / `GtkPixels`、`com.sun.prism.es2.X11GLFactory`；
      Windows / macOS 的对应实现类（`WinView` / `MacView` 等）尚未采集，元数据里缺失。
      做法：按技能 [agent-collection](../.agents/skills/graalvm-reachability-metadata/references/agent-collection.md)
      §3.3 在各自平台跑同一套命令清单，`config-merge-dir` 合并后补进元数据。
      验收标准：三平台元数据分别覆盖各自的 Glass / Prism 实现类；产物自证与
      `NativeImageMetadataTest` 仍绿。
      **部分闭环（2026-10-09）**：issue #39 的静态扫描（`Get*ID` 目标类）已把
      Win* / Mac* 平台实现类补进 `jni-config.json`（见 [TS-202610-WinWindowJniMemberMissing.md](Troubleshooting/TS-202610-WinWindowJniMemberMissing.md)）；
      但那是「已知缺口已闭」，agent 在两个平台上的实采仍待做，Prism 侧实现类同样待采。
      背景：issue #41——修完 #35/#37/#39 后原生桌面端首次进到 GUI，但渲染到第一个用阴影效果的
      控件时报 `Could not create peer LinearConvolveShadow`，控件画不出。已对 javafx-graphics 25 的 jar
      扫描 `com/sun/scenario/effect/impl/**/*Peer`，把 99 个具体 peer 全部登记进
      `reachability-metadata.json`（见 [TS-202610-PrismEffectPeerMissing.md](Troubleshooting/TS-202610-PrismEffectPeerMissing.md)）。
      验收标准：① 下载 Windows 原生包，双击能开窗、**控件正常绘制**（无 `Could not create peer`）
      ；② 走一遍窗口/控件/主题相关效果路径（阴影 / 颜色调整 / 混合等）；③ 若仍缺类，
      按同一格式补进元数据并回写 `DesktopNativeDesign.md` §6.1 与对应 `DevLog`。
      **真机结果（2026-10-09，版本 `0.1.1.00054`）**：控件正常绘制，不再出现
      `Could not create peer`；issue #41 已关闭。
      依赖：`G-11`（推送并提 PR）→ 合入 `dev` 后 `DesktopNative` 重出包。
- [ ] **N-24**：原生桌面端字体渲染与 JVM 模式不一致（issue #44 遗留）。
      长期跟踪载体：**issue #48**（现象、背景、待排查方向、验收标准均以该 Issue 为准）。
      本条目只作索引，不在这里维护细节；#48 关闭后把本条目改为「已完成」并回填结论。
      依赖：`G-13`（推送并提 PR）→ 合入 `dev` 后 `DesktopNative` 重出 Windows 包，供 #48 验证。

      **调研结论（2026-10-09，见 [TS-202610-NativeFontRenderingDiff.md](Troubleshooting/TS-202610-NativeFontRenderingDiff.md)）**：
      ① **DPI 缩放基本排除**——HiDPI 缩放已 opt-in，窗口尺寸已按 `outputScale` 换算；
      ② **「Prism 回退到软件管线导致发虚」已用像素实验证伪**——SW 管线下
      `prism.lcdtext=true/false` 的产物不同（4375 B / 3025 B，md5 不同），软件管线同样有次像素抗锯齿；
      ③ 剩余最可能方向是**字体枚举 / 字形栅格化路径**（Windows 的 `WinFontFinder` + GDI hinting
      在原生镜像里的行为），与「字形偏细」的描述更吻合，但需真机数据确认；
      ④ **铺路已完成**：`main` 默认开 `prism.verbose`（日志里有实际管线名），`start()` 打印默认字体族 /
      名称 / 字号 / 可用字体族数与屏幕 outputScale / dpi——原生与 JVM 两份日志可直接对照。
      下一步验收：拿到真机两份日志，即可区分「管线不同」与「字体族不同」。
- [ ] **N-25**：真机验证暗色模式下控件背景与文字的适配（issue #49）。

      第二轮（v0.45.0）：不再赌 looked-up color 查表，`Palette.theme()` 显式写死文字类颜色、
      `Palette.dialogTheme()` 给对话框显式底色；验收看三处：① 暗色下左栏导航按钮 / 分组标题 /
      菜单栏文字为浅色（更新后的包）；② 亮色下供应商对话框底色为浅色；③ 亮 ↔ 暗切换后再打开
      对话框，底色与主界面一致。日志里「主题应用」一行可直接对账色表。
      依赖：`G-15`（推送并提 PR）→ 合入 `dev` 后 `DesktopNative` 重出 Windows 包，供 #49 验证。
      长期跟踪载体：**issue #49**（现象、截图与验收标准以该 Issue 为准）；本条目只作索引。
      修复由 PR #50 提交：`Palette.theme()` 去缓存、逐个节点重刷异常隔离、搜索框 / 输入框 /
      会话单元格 / 菜单栏 / 滚动区显式套主题（见 [TS-202610-DarkModeControlColors.md](Troubleshooting/TS-202610-DarkModeControlColors.md)）。
      依赖：`G-13`（推送并提 PR）→ 合入 `dev` 后 `DesktopNative` 重出 Windows 包，供 #49 验证。

      第三轮（v0.47.0）：真机报「亮色 / 跟随系统下输入框、发送按钮、下拉框、滚动条背景全黑，
      且与主题切换无关」。查 `modena.css` 定案：这些控件的 `-fx-background-color` 形如
      `derive(-fx-box-border,30%), linear-gradient(...)`，原生镜像下 CSS 函数求值不可靠，
      而 **CSS 是一个值无效就丢弃整条声明**，控件因此根本没有背景（不是「设成了黑色」）。
      修法：新增 `aha-theme.css`（纯字面量，无 CSS 函数，经 `Scene.getStylesheets()` 加载、
      排在 modena 之后）+ `Palette` 定义 `-fx-aha-*` 变量并把 modena 中间量换成字面量。
      验收追加：① 亮色下输入框 / 下拉框底为浅色；② 「发送」按钮可见且有边框；
      ③ 会话列表中栏滚动条的轨道与滑块可见；④ 上述四项在亮 ↔ 暗切换后颜色同步变化。
      见 [TS-202610-ModenaCssFunctionFailure.md](Troubleshooting/TS-202610-ModenaCssFunctionFailure.md)。
- [ ] **N-26**：真机验证预发行包显示带构建号的版本（issue #46）。
      长期跟踪载体：**issue #46**；本条目只作索引。
      修复由本次 PR 提交（见 [TS-202610-BuildVersionPreRelease.md](Troubleshooting/TS-202610-BuildVersionPreRelease.md)）。
      验收标准：① 桌面端「关于」显示形如 `0.1.1.000XX` 的构建版本；② CLI 启动页顶栏与 `/help`
      显示同一构建号；③ `aha --version` 同样显示；④ 正式发版（`Release.yml`）仍显示基线版本。
      依赖：`G-14`（推送并提 PR）→ 合入 `dev` 后 `DesktopNative` / `CliNative` 重出包，供 #46 验证。

## 11. 待人工执行的动作（需仓库 / 平台权限）

- [ ] **G-07**：完成 PR #15（`dev → main`，`Release:V0.1.1`）的合并。
      现状（2026-10-08 实测）：**没有文件冲突**（`mergeable_state=clean`），
      dev head `214611f` 上 `Build`/`Gate`/`Compat`/`CodeQL`/5 条原生腿**全绿**，
      三个 approve 均落在 `214611ff` 且晚于最后一次 push → GitHub 侧**可以合并**。
      **两条路，任选一条**：
      1. 在 PR 页面点绿色 **Merge pull request**（推荐，PR 会被记为 merged）；
      2. 推送已备好的本地合并提交（无凭据时由人工执行）：
         ```
         cd E:\GitRepo\GitHub\ACANX\.aha-merge-tmp
         git log --oneline -1     # 221ccee Merge branch 'dev' into main（发布 V0.1.1）
         git push origin HEAD:main
         ```
         推完可用 `git worktree remove --force E:\GitRepo\GitHub\ACANX\.aha-merge-tmp` 清理。
      **若按钮是灰的**，按这个顺序查（详情见 `ReleaseProcess.md` §4.2）：
      ① `Settings → Branches → main` 里是否存在**没有任何东西会上报**的必需项
      （如 `code_scanning` / `code_coverage`）——那会造成永久阻塞，需删掉或补上产出；
      ② 是否开启了「Require approval of the most recent reviewable push」且最后一次 push
      之后**没有**新审批。
      合并后链路：推 `main` → `Build` 的 `tag（V<版本号>）` 作业打 `V0.1.1`
      → `Release.yml` 出 CLI 包 + 桌面端三平台包。

> **为什么单独成节**：这类动作需要有人在仓库或平台上执行（推送凭据、分支保护设置、
> 平台侧配置、外部环境验收），自动化流程做不到。它们此前**只出现在对话里**——
> 对话会滚走，一旦漏掉就没有闭环，事后连「有没有做过」都无从判断。
>
> **约定（自 2026-10-07 起）**：凡是我方无法执行、又必须由人完成才能收口的事项，
> 一律在此登记 `G-xx` 并写明**验收标准**，同时在 `PLAN.md` 的阻塞项中交叉引用。
> **不得只在对话里交代。**
>
> 与第 7 节的分工：第 7 节是「需要拍板」（想清楚就能推进），本节是「需要动手」
> （拍板了也得有人在平台上点下去）。

| 编号 | 事项 | 阻塞什么 | 验收标准 | 状态 |
| ---- | ---- | -------- | -------- | ---- |
| ~~G-01~~ | 推送 `dev` 上的两条文档提交（`abd5d85` 规则集整改规格、`ca80c5c` F-12 复发记录） | 这批文档不进上游就等于白做 | `git ls-remote origin refs/heads/dev` 与本地 `dev` 一致（或经 PR 合入 `dev`）；`dev → main` 的 PR 能带上它们 | ☐ 未完成 |
| ~~G-02~~ | ~~**分支规则集整改**：`main` 补配 `Gate` / `Compat` 两条必需检查，并把审批数从 1 改为 0；`dev` 同样把审批数改为 0。附现状实测表与目标规格表~~ | ~~① 该拦的门禁没拦；② 三条规则对「单人 + 机器」永远无法满足，PR 被锁死（见 [TS-202610-RulesetBlocksSingleMaintainer.md](Troubleshooting/TS-202610-RulesetBlocksSingleMaintainer.md)）~~ | ~~五项必需检查齐全，且**预期失败的 PR 合不进去、正常 PR 单人能合进去**~~ | ✗ **不做**（2026-10-10 决定） |
| G-03 | 确认每周定期扫描真的在跑 | 定期扫描静默失效无人知，漂移会持续积累 | 合入 `main` 后手动跑通一次 `Gate`；随后 Actions 出现 `schedule` 触发的运行记录 | ☐ 未完成 |
| G-04 | 为 `main` 规则集的 `code_scanning` 规则提供真结果：**开启 CodeQL**（推荐；若不开则必须删掉该规则） | `Waiting for Code Scanning results` 永不结束，PR #7 现在卡在这里 | Security → Code scanning 出现分析结果，PR 上该检查给出结论 | ☐ 未完成 |
| G-06 | 处置 0.1.0 的裸 tag：给同一提交补一个 `V0.1.0` 别名 tag（或明确「兼容两种写法」） | 已发布的 tag 是 `0.1.0`（无 `V` 前缀），而后来的约定与 `Release.yml` 的触发都是 `V*`；不处置则 `CHANGELOG` 的 `[0.1.0]` 链接与约定长期不一致 | `git ls-remote --tags origin` 能看到 `V0.1.0` 与 `0.1.0` 指向同一提交（`9138847`），或规范中明确写出兼容策略 |
| ~~G-05~~ | ~~仓库设置：**关闭 squash 与 rebase 合并**，只保留 `Create a merge commit`~~ | ~~长期集成分支 `dependa` 一旦被 squash，血缘就断了，下次 PR 必然 `dirty`——本次已实际复发（`F-12`）~~ | ~~设置生效后，`dependa → dev` 的合并提交是双父，`git merge-base --is-ancestor origin/dev dependa` 成立~~ | ✗ **不做**（2026-10-10 决定） |
| G-09 | 推送 CLI 原生镜像的变更并提 PR（`aha-cli-native` 模块 + `CliNative.yml` → `dev`） | 本地没有推送凭据（同 `G-01`）；不推上去，`CliNative` 不会首次运行，`N-19` 无法开工 | ① 分支推上去、PR 上 `Build` / `Gate` / `Compat` 绿；② 合入 `dev` 后 `CliNative` 自动跑，三条 jdk25 腿产物自证第 ⑦ 项输出「已注册 picocli 命令、JLine Signals 与 AHA 配置记录」；③ 发布页出现 `V<版本>-aha-cli-native` 预发行版 | ☐ 未完成 |
| G-12 | 推送本次变更分支并提 PR（`feat/desktop-dist-scripts` → `dev`）：桌面端便携包脚本、命令速查、构建输出目录统一为 `Dist` | 本地没有推送凭据（同 `G-01`）；不推上去，CI 的 `Build` / `Gate` / `Compat` 不会对本次改动跑一遗，改到工作流里的 `Dist/` 路径也得不到 Linux runner 的真实验证 | ① `git ls-remote origin refs/heads/feat/desktop-dist-scripts` 能看到该分支；② PR 上 `Build` / `Gate` / `Compat` 绿，尤其 `Dist/` 路径改动在 Linux 上被实际执行；③ 合入 `dev` 后再决定是否并入 `main` | ☐ 未完成 |
| G-13 | 推送 issue #44 的修复分支并提 PR（`fix/issue-44-native-theme-font` → `dev`） | 不推上去，CI 不会对本次改动做编译与全量测试，`N-24` 也拿不到合入后自动产出的原生包 | ① `git ls-remote origin refs/heads/fix/issue-44-native-theme-font` 能看到该分支；② PR 上 `Build` / `CodeQL` 绿；③ 合入 `dev` 后 `DesktopNative` 重出 Windows 包，供 `N-24` 验证 | ☐ 未完成 |
| G-14 | 推送 issue #46 的修复分支并提 PR（`feat/issue-46-build-version` → `dev`） | 不推上去，CI 不会对本次改动做编译与全量测试，`N-26` 也拿不到带构建号的包 | ① `git ls-remote origin refs/heads/feat/issue-46-build-version` 能看到该分支；② PR 上 `Build` / `CodeQL` 绿；③ 合入 `dev` 后 `DesktopNative` / `CliNative` 重出包，供 `N-26` 验证构建号显示 | ☐ 未完成 |
| G-16 | 推送 issue #49 第三轮修复分支并提 PR（`fix/issue-49-native-css-functions` → `dev`） | 不推上去，CI 不会对本次改动做编译与全量测试，`N-25` 也拿不到第三轮的原生包 | ① `git ls-remote origin refs/heads/fix/issue-49-native-css-functions` 能看到该分支；② PR 上 `Build` / `CodeQL` 绿；③ 合入 `dev` 后 `DesktopNative` 重出 Windows 包，供 `N-25` 第三轮验证 | ☐ 未完成 |

### G-01 ☐ 未完成

**内容**：把本地 `dev` 上尚未推送的两条文档提交推上去
（`abd5d85` 规则集整改规格、`ca80c5c` F-12 复发记录）。

**为什么必须人工**：① 本环境没有推送凭据（`GIT_TERMINAL_PROMPT=0 git push` 实测
`could not read Username`，exit 128）；② `dev` 的规则集带 `pull_request` 规则，
**直接推 `dev` 可能被拒**——能否绕过取决于规则集的 bypass 名单（本环境读 API 时被限流，
需在 Settings → Rules → 该规则集里确认）。不能绕过时，就从一个分支提 PR 合入 `dev`。

**分支现状（2026-10-07，方案 B 执行后）**：

| 分支 | SHA | 相对上游 |
| ---- | ---- | ---- |
| `dev` | `ca80c5c` | **ahead 2**（待推送） |
| `dependa` | `1518056` | 与 `origin/dependa` **一致**（已复位，无需推送） |
| `main` / `feat/local` | `4f12cef` | 一致 |

**已知代价（实测，务必记住）**：`dependa` 复位后，**第一次把 `dev` 合进 `dependa` 时会冲突 6 个文件**——
两侧相对分叉点 `5d938f3` 都改过它们，且改法的形状不同（这正是 squash 的后果，见 `F-12`）：

```
CHANGELOG.md
Docs/AHA/AHA-Design-V1.md
Docs/Troubleshooting/TS-202610-FakeMergeDirtyPr.md
Docs/DevSpec/BuildSpec.md
Docs/DevSpec/ReleaseProcess.md
Docs/TODO.md
```

解法：这些文件两侧除本次两条文档提交外**内容本就相同**，**取 `dev` 的版本**即可
（`git checkout --theirs -- <文件>` 后 `git add`）。想要彻底避免这类差异，见 `G-05`（关闭 squash）。

**验收标准**：`git ls-remote origin refs/heads/dev` 与本地 `dev` 一致（或对应 PR 已合入）；
`dev → main` 的 PR（#7）能带上这批文档。

**闭环后**：本条改 ✅，并在 `PLAN.md` §8.1.1 收口。

### ~~G-02~~ ✗ 不做（2026-10-10 决定）

**内容**：按下面的规格**一次性**配置两条分支规则集，让门禁真正拦人，同时**不把单人维护者锁死**。

**为什么必须人工**：规则集是仓库设置（Settings → Rules → Rulesets），工作流文件里写不了；
本环境也没有可写的凭据（`GIT_TERMINAL_PROMPT=0 git push` 实测 `could not read Username`）。

**现状（2026-10-07 API 实查）**：`GET /repos/ACANX/AHA/rulesets` → 两条仓库级规则集：

| 规则集 | id | 适用分支 | 现有规则 |
| ---- | ---- | ---- | ---- |
| `dev` | 24648482 | `refs/heads/dev` | `deletion`、`non_fast_forward`、`pull_request`（approvals=**1**）、`required_status_checks`（三条 `build (...)`） |
| `main` | 24648542 | `refs/heads/main` | 上述全部，外加 **`code_scanning`（CodeQL）**、**`code_coverage`**，且 `pull_request` 带 `last_push_approval`=**true** |

**已造成的实际阻塞（`PR #7` `dev` → `main`）**：三条规则对「单人 + 机器」**无法满足**：

1. `pull_request`（approvals=1 + `last_push_approval`）——只有一位协作者，GitHub 禁止自我批准
   → 提示「New changes require approval from someone other than ACANX because they were the last pusher」；
2. `code_scanning` 要求 CodeQL 结果，仓库却**没配任何 code scanning**
   → 提示「Waiting for Code Scanning results」；
3. `code_coverage` 需要把覆盖率上传给 GitHub 或其支持的覆盖率服务，本项目只有本地 JaCoCo 门禁
   （尚未报错，因为它排在其它条件之后）。

**方向相反的另一处**：`main` 的必需检查只有三条快检查，而 `BuildSpec.md` §8.1 要求
`Gate` 与 `Compat` 也必须是必需检查——**该拦的没拦，不该锁的锁死了**。

**目标规格（逐项照此设置）**：

| 项 | `main` | `dev` | 理由 |
| ---- | ---- | ---- | ---- |
| `deletion` / `non_fast_forward` | 保留 | 保留 | 禁止删除与强推，与人数无关 |
| 要求 PR | 保留 | 保留 | 改动走 PR 才挂得上必需检查 |
| required_approving_review_count | **0** | **0** | 单人仓库里「1 个批准」= 禁止合并；卡点交给必需检查 |
| require_last_push_approval | **false** | false | 同上 |
| required_review_thread_resolution | 保留 `true` | 不适用 | 要求先解决评论，单人也能满足 |
| required_status_checks | 三条 `build (...)` **+ `门禁（Maven 4 wrapper：verify + 覆盖率 + 文档 + 技能 + 脚本 + 重复率）` + `兼容性（Maven 3.9.x 完整 verify）`** | 三条 `build (...)`（保持） | 慢检查是「合入 `main` 前」的卡点（`BuildSpec.md` §8.1）；`dev` 是集成分支，保持快反馈 |
| `code_scanning` | **二选一**：① 开 CodeQL（推荐，见 `G-04`）并保留；② 不用就**删掉本规则** | 不适用 | 要求某工具的结果，就必须有人生产它 |
| `code_coverage` | **建议删除** | 不适用 | 覆盖率已由 `jacoco:check ≥ 0.70` + `bin/ReportCoverage.py` 在 `Gate` 里把关；再引外部服务属重复。若确实想要 PR 内可见覆盖率，需另行拍板（引入受支持的覆盖率服务） |

**验收标准**：五项必需检查（三条 `build (...)` + `Gate` + `Compat`）都出现在 `main` 的
必需检查里；用一个**预期失败的 PR** 验证确实合不进去；再用一个**正常 PR** 验证**单人也能合进去**
（不再出现「等待批准」「等待 Code Scanning」）。只勾选不验证，可能因名称未完全匹配而形同虚设。

**维护约定**：作业名或必需腿的矩阵键一旦变更，必需检查就会失配（见 `F-08`）；
改 `Build.yml` / `Gate.yml` / `Compat.yml` 或**规则集本身**时，必须同步刷新本条上方的两张表。

**闭环后**：本条改 ✅，并在 `PLAN.md` §8.2.9 收口。

### ~~G-05~~ ✗ 不做（2026-10-10 决定）

**内容**：在 Settings → General → Pull Requests 里**关闭 squash 与 rebase 合并**，
只保留 `Create a merge commit`。

**为什么必须人工**：这是仓库设置，工作流与规则集都写不了（规则集也管不了合并方式）。

**为什么必须做**：`dependa` 是长期集成分支（Dependabot 的 `target-branch`），
它既要被合入、又要持续往 `dev` 合。一旦某次用 squash 合入，血缘就断了——
上游拿到内容却没有拿到分支历史，**下一次 `dependa → dev` 的 PR 必然 `dirty`**。
2026-10-07 当天，`F-12` 记下的这个形态**已经复发过一次**（PR #8 的 `aeadec4` 是单父提交），
只能再用一次 `-s ours` 把血缘接回（`d1de175`）。靠人记得住，不如靠平台不让做。

**验收标准**：设置生效后做一次 `dependa → dev`，确认合并提交有**两个父**
（`git log -1 --format=%p <merge>`），且 `git merge-base --is-ancestor origin/dev dependa` 成立。
此后 `F-12` / `ReleaseProcess.md` §4 的手工补救不再需要。

**闭环后**：本条改 ✅。

### G-04 ◐ 已提供 CodeQL 工作流，待你确认最后一处设置

**现象**：PR #7（`dev` → `main`）停在

```
Waiting for Code Scanning results. Code Scanning may not be configured for the target branch.
```

`main` 规则集有一条 `code_scanning` 规则（tool=`CodeQL`，`security_alerts_threshold=high_or_higher`、
`alerts_threshold=errors`），而仓库此前**没有任何 code scanning 配置**——没有结果可等，于是永久等待。

**已做（2026-10-07）**：新增 `.github/workflows/CodeQL.yml`（**高级设置**，即工作流方式）：

| 设计点 | 取值 | 理由 |
| ---- | ---- | ---- |
| 触发分支 | `pull_request: branches: [main, dev]` + `push: [main, dev]` + 每周一 04:00 UTC + 手动 | **必须覆盖目标分支**：规则作用在 `main`，只扫默认分支（`dev`）满足不了它 |
| 权限 | `contents: read`、`security-events: write`、`actions: read` | 上传 SARIF 必需，其余不收 |
| 构建 | `build-mode: manual` + 经 `./.github/actions/maven-run` 跑 `./mvnw -B -DskipTests -Djacoco.skip=true compile` | 用仓库固定工具链（Wrapper 的 Maven 4.0.0-rc-7 + JDK 25），并享受失败标记清理与定向重试 |
| 与必需检查的关系 | **不进**必需检查 | 规则集用的是 `code_scanning`（按扫描结果判定），不是 `required_status_checks`，因此不改动作业名契约（`F-08`） |

> ⚠️ **未能在本环境验证**：CodeQL 只能在 GitHub 上跑。首次运行要盯一眼日志——若报
> 「`build-mode` 取值不合法」，删掉那一行即可（工作流里已写明）。若报权限不足，检查
> Settings → Actions → General → Workflow permissions。

**为什么不用 GitHub 的「默认设置」（Default setup）**：它由平台托管、配置更省心，但扫描范围是
**默认分支**（本仓库为 `dev`）及指向它的 PR，覆盖不到 `main`——规则照样等不到结果。
（此点未能在本环境核实：docs.github.com 与 raw 文档源均被限流。工作流方案不依赖它，故取工作流。）

**备选（若你不想要代码扫描）**：删掉 `main` 规则集的 `code_scanning` 规则，并删除本工作流。
规则与产出必须成对——**要么都留，要么都去**。

**上线实测（2026-10-07）**：工作流**能跑**——`init`（含 `build-mode: manual`，被接受）与
走仓库 Maven 的 `compile` 两步在三次运行里**全部成功**；三次运行里 **1 次成功、2 次失败**，
失败**只在 `analyze`（上传 SARIF）**这一步：

| 运行 | 事件 | 分支/方向 | 结果 |
| ---- | ---- | ---- | ---- |
| #1 | pull_request | `dev-ddd` → `dev` | **success** |
| #2 | push | `dev` | failure（analyze） |
| #3 | pull_request | `dev` → `main`（PR #7） | failure（analyze） |

`#2`/`#3` 与 `#1` 的**代码完全相同**（两份分支树零差异），故不是代码问题，而是仓库侧配置冲突。
**最可能的原因**：开启了 GitHub 的 Code scanning **默认设置**（Default setup）——它与高级设置（本工作流）
**互斥**，来自工作流的结果会被拒收（错误原文：`CodeQL analyses from advanced configurations cannot be
processed when the default setup is enabled.`）。**待你在 run #3 第 6 步的日志里确认原文**。

**处置（二选一）**：

| | 动作 | 后果 |
| ---- | ---- | ---- |
| ① **关掉默认设置，留工作流**（推荐） | Settings → Code security → Code scanning → Default setup → Disable，然后 Re-run 失败的那两次 | 本工作流显式覆盖 `pull_request → main`，PR #7 的规则能拿到结果 |
| ② 留默认设置，删工作流 | 删 `.github/workflows/CodeQL.yml` | 默认设置只覆盖默认分支（`dev`）及指向它的 PR，覆盖不到 `main` → **PR #7 仍会永久等待**；真要走这条必须同时删掉 `main` 规则集的 `code_scanning` 规则 |

**另已顺手修正**：`github/codeql-action` 由 `v3` 升到 **`v4`**（v3 计划 2026 年 12 月弃用，
且 v3 目标 Node.js 20、会被强制跑在 Node 24 上并报弃用警告）。
运行日志里那条 `Cannot build an overlay-base database because build-mode is set to "manual"…`
是**良性**提示（回退为建立完整数据库，分析更完整），已在工作流里注明「不要为消掉它改成 `none`」。

**验收标准**：Security → Code scanning 出现 java-kotlin 的分析结果；
PR #7 上「Code Scanning」由等待变为**给出结论**（按阈值：高危以上或存在错误才拦）。

**闭环后**：本条改 ✅。

**闭环后**：本条改 ✅。

### ~~G-05~~ ✗ 不做（2026-10-10 决定）

**内容**：在 Settings → General → Pull Requests 里**关闭 squash 与 rebase 合并**，
只保留 `Create a merge commit`。

**为什么必须人工**：这是仓库设置，工作流与规则集都写不了（规则集也管不了合并方式）。

**为什么必须做**：`dependa` 是长期集成分支（Dependabot 的 `target-branch`），
它既要被合入、又要持续往 `dev` 合。一旦某次用 squash 合入，血缘就断了——
上游拿到内容却没有拿到分支历史，**下一次 `dependa → dev` 的 PR 必然 `dirty`**。
2026-10-07 当天，`F-12` 记下的这个形态**已经复发过一次**（PR #8 的 `aeadec4` 是单父提交），
只能再用一次 `-s ours` 把血缘接回（`d1de175`）。靠人记得住，不如靠平台不让做。

**验收标准**：设置生效后做一次 `dependa → dev`，确认合并提交有**两个父**
（`git log -1 --format=%p <merge>`），且 `git merge-base --is-ancestor origin/dev dependa` 成立。
此后 `F-12` / `ReleaseProcess.md` §4 的手工补救不再需要。

**闭环后**：本条改 ✅。

### G-04 ☐ 未完成

**内容**：为 `main` 规则集的 `code_scanning` 规则提供真结果——**开启 CodeQL**。

**为什么必须人工**：需要管理员在 Settings → Code security → Code scanning 里开启。
推荐用 **Default setup**（默认设置）：由 GitHub 维护配置、仓库里不必放工作流，也不会随
Dependabot 的版本漂移而失修；仓库是 `public`，CodeQL 免费。

**对应关系**：**开了它就保留 `code_scanning` 规则；不开就必须删掉那条规则**（见 `G-02`），
否则 PR 会一直停在「Waiting for Code Scanning results」。

**验收标准**：Security → Code scanning 出现分析结果；PR 上该检查给出明确结论
（阈值 `high_or_higher` / `errors`，即高危以上或存在错误才拦）。

**闭环后**：本条改 ✅。

### G-03 ☐ 未完成

**内容**：确认 `Gate.yml` 的 `schedule`（每周一 03:00 UTC）真的生效。

**为什么必须人工**：GitHub 的 `schedule` **只在默认分支上生效**，必须等本次改动合入
`main` 之后才能验证；在此之前它不会触发，也无法用 PR 验证。

**验收标准**：合入 `main` 后先用 `workflow_dispatch` 手动跑通一次；随后在 Actions 页面
看到 `schedule` 触发的运行记录（时间戳应落在周一 03:00 UTC 附近）。

**闭环后**：本条改 ✅。

### G-08 ☐ 未完成

**内容**：把 issue #35 的修复分支 `fix/issue-35-native-quantum-toolkit` 推到 origin，
并提 PR 合入 `dev`。

**为什么必须人工**：本环境没有推送凭据（同 `G-01`，`GIT_TERMINAL_PROMPT=0 git push` 实测
`could not read Username`）。不推上去，改动只存在于本地：CI 不会跑，`DesktopNative` 的
可选腿不会重跑，`N-16`（真机验证启动链路的反射 / JNI 元数据是否完整）就无法开工。

**验收标准**：

1. `git ls-remote origin refs/heads/fix/issue-35-native-quantum-toolkit` 能看到该分支；
2. PR 上 `Build` / `Gate` / `Compat` 绿；
3. `DesktopNative` 三条 jdk25 腿的产物自证第 ⑧ 条输出
   「已注册主类与 JavaFX 启动链路（QuantumToolkit / Glass 工厂 / Prism 管线）」，
   而不是「可达性元数据不完整」的 warning；
4. 合入 `dev` 后自动出 `V<版本>-aha-desktop-native` 预发行版，供 `N-16` 下载验证。

**依赖**：与 `N-05` / `N-16` 同一条链路——先推上去，才有真机验证的对象。

**闭环后**：本条改 ✅。

---

## 12. 展示与终端体验

| 编号 | 事项 | 类型 | 证据 | 优先级 | 状态 | 落地文档 |
| ---- | ---- | ---- | ---- | ------ | ---- | -------- |
| H-01 | 启动标志的默认风格：像素风（现状）还是线框风？ | 待决策 | ✅ | P3 | ⏸ 待决策 | `PixelLogoDesign.md`、`TUIDesign.md` §3.2 |

### H-01 ⏸ 待决策

**背景**：`aha chat` 的启动横幅右侧会打印标志，`--logo` 支持 `auto|pixel|ascii|off`。
当前 `auto` 为**像素风优先**，受符号能力与 256 色门禁约束，不满足时降级为线框风。

**待拍板**：默认是否改用线框风。影响仅限首屏观感；改动量是把候选顺序对调一行
（`StartupBanner.compose` 的候选列表）。

**为什么记在这**：这是人对观感的偏好，技术上没有唯一正解。此前只在对话里提过，
按 §11 的约定需要留痕，避免漏掉。

---

## 附：核实方法备忘

部分条目需动手验证，建议的核实命令如下（**执行前请确认环境**）：

```bash
# B-03：确认 jackson 构件来源与版本
./mvnw dependency:tree -Dincludes='*jackson*'

# E-02/E-04：查看 native-image 相关的实际可达类与资源（需 GraalVM 环境）
native-image --version

# A-03/D-01：确认发行包实际内容
./mvnw clean package
ls -l Dist/lib/

# C-01：确认模块边界是否真的阻止了非法引用（可故意加一条 requires 验证）
jdeps --module-path Dist/lib --module com.acanx.module.aha.core
```
