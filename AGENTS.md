# AHA - Agent Harness

**项目代号**：AHA
**当前版本**：见仓库根目录 `version` 文件
**构建工具**：Maven 4（运行时）/ Maven 3.9.x（兼容基线）
**JDK**：25 (LTS)
**模块化**：JPMS 优先启用（非强制；与 OpenJFX 冲突时为它让路）

---

## ⛔ 推送前必须确认分支对应的 PR 是否已合并（强制，2026-10-18 起）

- **改代码前**与 **`git push` 前**，各查一次该分支对应的 PR 状态：
  - `gh pr list --head <分支名> --state all --json number,state,mergedAt`
  - `git fetch origin && git log --oneline origin/dev..HEAD`
- 若 PR 已 `MERGED` / `CLOSED`：**禁止继续改动与推送**，必须基于最新 `origin/dev` 新建分支，
  用 `git diff origin/dev <旧分支> | git apply` 迁移未合并改动，再开新 PR 并在正文注明前序 PR。
- 旧分支保留、不再推送（作为历史）。
- 完整流程、三个检查点与事故记录见
  [PullRequestWorkflowSpec.md](Docs/DevSpec/PullRequestWorkflowSpec.md)（**强制规范**）。
- 违反本条 = 改动不会进入主干且过程不可见，属**严重流程错误**；发现后立即停止、迁移、如实说明。

## ⛔ 本地不跑 Maven 编译与单元测试（强制，已多次重申，勿再违反）

**本机不执行任何 Maven 编译 / 测试 / verify 动作**——包括但不限于
`compile` / `test-compile` / `test` / `clean test` / `verify` / 覆盖率采集 /
重复率扫描 / javadoc。这些**全部由 CI 承担，一次都不许在本地跑**。

理由：本地跑一遍既慢又占机器，还会让 Agent 陷入长等待；CI（`Build.yml` 编译 + 单元测试，
`Gate.yml` verify / 覆盖率 / 文档，`Compat.yml` Maven 3.9.x，`BuildJVMArtifacts.yml` 出包）
会在每次推送 / PR 时自动跑，推送后在 PR 的 checks 里看结果即可。

| 工作流 | 承担 |
|---|---|
| `Build.yml` | 每次 push / PR：**自动编译 + 单元测试**（`clean test -Djacoco.skip=true`） |
| `BuildJVMArtifacts.yml` | `push` → `dev`：按「平台 × JDK（25 / 27）」构建 `aha-desktop` / `aha-cli` 便携包并发布 dev 预发行版 |
| `Gate.yml` | 合入 `main` 前 / 每周 / 发布前：完整 `clean verify`、覆盖率门禁、文档/技能/脚本/像素标志、重复率 |
| `Compat.yml` | Maven 3.9.x 兼容（`mvn clean verify`） |
| `Release.yml` | 发布：按平台出包并挂 release 页面 |

规则：

- **本机禁止**：`./mvnw ... compile`、`./mvnw ... test-compile`、`./mvnw ... test`、
  `./mvnw ... clean test`、`./mvnw ... verify`、`mvn ...`，以及任何等价的构建 / 测试动作
  （包括加 `-pl <模块> -am`、加 `-Djacoco.skip=true` 的「快检查」变体）；
- 提交 / 推送前本地**只允许**跑静态检查脚本：
  `python3 bin/Check{Changed,Docs,Skills,Scripts,Theme}.py`；
- **主题色只有一个手写来源**：改界面颜色请改
  `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/Palette.java`，
  再跑 `python3 bin/GenThemeCss.py` 重新生成两份补丁表
  （模板在 `aha-desktop/src/theme/aha-theme-*.template.css`，带 `%角色%` 占位符）。
  直接手改 `.../view/css/aha-theme-*.css` 会被 `bin/CheckTheme.py` 打回（issue #153）：
  那两份是**生成物**，不手改——理由（为何不用 CSS 变量）见两份 CSS 头部与 `TS-2610-035`；
- 需要实测数字（用例数、覆盖率）时，**从 CI 的 `Build` / `Gate` 日志取**，不要在本机重跑；
- **不要**因为「改了 POM / `module-info` / 影响覆盖率口径 / 想先确认能不能编译」
  就本地补跑——直接推送，看 CI 结果；
- 唯一例外：用户**明确要求**本地跑，或 CI 不可用且用户确认。

判定规则与阈值见 [BuildSpec.md](Docs/DevSpec/BuildSpec.md) 第 8 节。

---

## 🧱 Agent 执行域（能力剥离，issue #70）

上面那条不只靠约定，还有**执行域兜底**：`bin/AgentSandbox.sh` 用 Linux 命名空间（bubblewrap）
把命令放进一个**没有本地构建能力**的域里执行 —— 域内 `/mnt`（Windows 盘）不可见、宿主 JDK 目录
被盖成空目录、`mvn` / `mvnw` / `java` / `javac` / `gradle` 都是拒绝桩（命中即退出码 `97`，
并提示「去看 PR 的 CI checks」）。**工作区文件一个都不改**，所以 git 看不到任何虚假变更。

```bash
./bin/AgentSandbox.sh -- <命令>        # 在 Agent 域内执行（默认当前目录；--repo <目录> 可指定）
./bin/AgentSandbox.sh --print-plan     # 只打印生效的 bwrap 参数（取证 / 排障）
python3 bin/CheckAgentBoundary.py      # 自证：域内确实没构建能力，且隔离失效时它会变红
```

- **Agent 侧**：本地构建 / 测试不在域内做（也做不了）——改完推送，看 PR 的 checks；
- **人类开发者不受影响**：直接用 `./mvnw`，不需要进这个域，也不需要任何额外配置；
- **不覆盖**：Windows 侧原生启动的工具（OpenCode / DSH 等）在命名空间之外，见 issue #70 的 P2；
- **尚未启用 shell 自动进域**（PATH shim / `~/.bashrc` 包装）：属**仓库外**的机器配置，
  待 ACANX 决定后由人安装；在此之前用上面的命令显式进入；
- 设计见 [AgentExecutionBoundaryDesign.md](Docs/Design/AgentExecutionBoundaryDesign.md)；
  CI 卡点是 `Gate.yml` 的「Agent 执行边界自证」一步（隔离一被改坏，自证立刻变红）。

---

## 项目概览

AHA 是一个 Agent Harness 工具，支持 CLI 与桌面端双模式运行。
核心能力：LLM 供应商适配、工具调用、记忆管理、配置管理、安全存储、扩展能力。

## 快速开始

### 构建

> 下列命令是 **CI 与发布**用的，不要在本地执行（见文首「本地不跑慢检查」）。

```bash
./mvnw clean verify          # Maven 4 运行时（CI：Gate.yml）
mvn clean verify             # Maven 3.9.x 兼容验证（CI：Compat.yml）
```

本地验证只需要静态检查脚本（**不跑 Maven**）：

```bash
python3 bin/CheckChanged.py
```

### 运行 CLI

```bash
./mvnw -pl aha-cli exec:java
# 或（需先执行 ./mvnw clean package 生成 Dist/）
Dist/AhaCli.sh chat          # Linux / macOS
Dist\AhaCli.bat chat         # Windows
```

### 运行测试

> 单元测试一律由 CI 跑，**不要在本地执行**（见文首「本地不跑 Maven 编译与单元测试」）。

```bash
# CI：Build.yml 每次 push / PR 自动编译 + 跑单元测试
```

覆盖率门禁（≥ 70%）与报告由 CI 的 `Gate.yml` 产出，本地不跑。

### 检查分层

慢检查（覆盖率门禁、完整 `verify`、文档检查、重复率）**不再**跟每次改动一起跑——
它们集中在 CI 的 `Gate.yml`，只在合入 `main` 前与发布前执行。日常改动只需快检查：

| 场景 | 命令 |
|---|---|
| 日常改动（本地） | `python3 bin/CheckChanged.py`（**不跑 Maven**） |
| 按变更选择检查 | `python3 bin/CheckChanged.py` |
| 看覆盖率实测值 | 从 CI 的 `Build` / `Gate` 日志取 |
| 重复率（改到 PMD 配置 / 阈值时） | 由 CI 的 `Gate.yml` 跑 |
| 合入 `main` 前 | **由 CI 的 `Gate.yml` 跑**（本地不跑） |
| Maven 3.9.x 兼容（POM 改动时） | **由 CI 的 `Compat.yml` 承担**（本地不跑） |

CI 侧的分工：`Build.yml` 每次 push/PR 只做编译与单元测试；`Gate.yml` 承载
verify / 覆盖率 / 文档 / 技能 / 脚本 / 重复率，并在**合入前、每周定期、发布前**运行；
`Compat.yml` 承载 Maven 3.9.x 兼容验证；`BuildJVMArtifacts.yml` 在 `push` 到 `dev` 时
构建 JVM 便携包并发布预发行版（不影响 Build 快检查）。`Gate` 与 `Compat` 都应在 main 的
分支保护里设为必需检查。

判定规则与阈值见 [BuildSpec.md](Docs/DevSpec/BuildSpec.md) 第 8.1 节。

### ⛔ 暂存区三份文件已删除（强制，2026-10-17）

`Docs/TODO.md`（暂存区清单）、`Docs/PLAN.md`（做不到 / 已暂缓的结论）、`Docs/Dbsx.txt`（用户原始待办）
**已于 2026-10-17 删除**，内容去向：

| 内容 | 现在在哪 |
|---|---|
| 已完成 / 明确不做 / 已过时 | [`Docs/DevLog/20261017-15.md`](Docs/DevLog/20261017-15.md)（逐条附证据） |
| 仍然要做的条目 | 逐条折进 `Docs/TODO/` 的对应 `TD-*` 文件（**需求原文一并保留**） |
| 三份文件的历史全文 | `git log` / 归档记录（不再有工作副本） |

因此：**不要再向这三份文件写入**（它们不存在了），也不要在别处重建同类「暂存区」。

待办改为：每项一个 `Docs/TODO/TD-PPPPP-大驼峰英文标题.md`（五位数字从 `00001` 全局自增），
并在 [`Docs/TODO/README.md`](Docs/TODO/README.md) 的清单里登记一行（状态、跟踪 Issue）：
状态变化时同变更内更新 TD 文件头部与清单，已完成需在备注写明依据；详规见该文件与
`DocumentationSpec.md` §4。

新的遗留事项一律走下面两条路径，不再有「暂存区」。

### 遗留事项一律开 Issue 跟踪（强制）

凡是**本次未能闭环**的问题（查不动、需外部环境、需后续迭代）、**需要拍板**的取舍项、
以及**自动化做不到、必须由人完成**才能收口的事项（推送提交、仓库 / 分支保护设置、
平台侧配置、外部环境验收），一律在仓库 Issue 区开一条对应 Issue 作为长期跟踪载体，
写明**来源、现象、背景、待排查方向与验收标准**。

**不得只在对话里交代。** 对话会滚走：漏掉之后既没有闭环，也无从判断「到底做过没有」，
事后连责任边界都说不清。判定标准：一个遗留问题如果**不适合下一轮立即动手**，
就应该是一条 Issue。写法与命令见技能
[issue-tracking](.agents/skills/issue-tracking/SKILL.md)。

**关闭时（强制）**：先补一条关闭评论，写明**相关修复 PR / 提交**、**关闭理由**
（现象是否消除；是根治还是缓解；若为缓解，指名根因由**哪条 Issue** 继续跟踪）与
**可复核的验收依据**（命令与输出、测试运行编号 / 用例数、日志行、产物）——三要素缺一不可，
**不得只写「已完成」**；仍需跟踪的后续项必须先把承接的 Issue 开好，不允许「关了就没下文」。
依赖人工判断的项（真机观感、平台行为）要明说「以 ACANX 的判断为准」。
关闭须经 ACANX 授权；细则与评论模板见技能 [issue-tracking](.agents/skills/issue-tracking/SKILL.md)。

**理由**：`TODO.md` 定位是「暂存区」，条目消化完即标记废弃或删除，长期问题放进去
会被后续条目淹没而遗忘；Issue 有编号、状态、可被引用与检索，才能让「直到解决」有闭环。

实例：遗留事项总览 #86（并逐条拆分到 #72~#85）。

### 排障 / 事故类事项的留痕

排障 / 事故类事项写一篇 [Docs/Troubleshooting/](Docs/Troubleshooting/) 下的
`TS-yyMM-NNN-英文词序.md`（模板：[TS-Template.md](Docs/Troubleshooting/TS-Template.md)），
必备「现象 / 排查过程（含取证）/ 根因 / 修复 / 验证 / 教训 / 涉及文件」各节
（见 `DocumentationSpec.md` §4）。需要长期跟踪时另开 Issue。

[Docs/DevLog/](Docs/DevLog/) 只记**已完成事项**的开发记录（做了什么、如何验证、影响面），
**不写排查过程**；命名 `yyyyMMdd-HH.md`，规范与模板见 [README.md](Docs/DevLog/README.md)。

### 「做不到 / 已暂缓」的结论去哪

「做不到 / 已暂缓」的**结论与依据**（期望是什么、试过什么、证据是什么、为什么受阻、
将来在什么前提下可以再做）写进**对应 `Docs/TODO/TD-*` 的「结论 / 已排除方案」小节**（跨主题的进 Issue），
**不追加待办**；待办一律走 `Docs/TODO/` + Issue。

## 文档记录去向（遇到 X 写哪里）

| 何时写 | 写哪里 | 命名与规范入口 |
|---|---|---|
| 完成一个功能 / 改动 / 修复 | `Docs/DevLog/` | `yyyyMMdd-HH.md` → [README](Docs/DevLog/README.md)（含模板） |
| 排查完一个问题 / 事故复盘 | `Docs/Troubleshooting/` | `TS-yyMM-NNN-英文词序.md` → [README](Docs/Troubleshooting/README.md) + [TS-Template.md](Docs/Troubleshooting/TS-Template.md) |
| 产生一个本轮不做的待办 | `Docs/TODO/`（+ 长期跟踪开 Issue） | `TD-PPPPP-大驼峰英文标题.md` → [README](Docs/TODO/README.md) |
| 需要长期跟踪的遗留问题 / 待决策 / 需人工执行 | **GitHub Issue**（并在 `Docs/TODO/` 登记） | 技能 [issue-tracking](.agents/skills/issue-tracking/SKILL.md) |
| 认定「做不到 / 已暂缓」 | 写进**对应 `Docs/TODO/TD-*` 的「结论 / 已排除方案」小节**（跨主题的进 Issue） | `DocumentationSpec.md` §4 |
| 文档 / 设计稿 / 脚本不再维护 | `Archive/` | 保持原名 + 头注「已废弃」 → [README](Archive/README.md) |
| 已定方案的详细设计 / 规范 / 指南 | `Docs/Design/` / `Docs/DevSpec/` / `Docs/Guide/` | 按 `DocumentationSpec.md` |

**记录纪律（强制）**：

1. 记录与代码改动**同一变更内**完成，不要「下次补」；
2. **不写进对话或临时笔记** —— 待办一律落 `Docs/TODO/`（README 索引 + `TD-*`）或 Issue；
3. 待办不得只留在对话里；排查过程与开发日志**不混用**（过程进 Troubleshooting，完成进 DevLog）；
4. 命名不符 `DocumentationSpec.md` §1 会被 `bin/CheckDocs.py` 判失败；收尾前跑一次它。

> 三类记录的**写法、时机与自检清单**见技能 [doc-recording](.agents/skills/doc-recording/SKILL.md)。

## 模块结构

| 模块 | 说明 | 依赖 |
|---|---|---|
| aha-common | 共享 DTO、SPI、异常 | 无 |
| aha-extension-api | 扩展契约 | common |
| aha-core | 推理引擎、LLM 适配、存储、配置、扩展运行时 | common, extension-api |
| aha-tool | 工具实现（ServiceLoader） | common, core |
| aha-cli | 命令行入口（picocli + JLine） | core, tool |
| aha-desktop | 桌面端（OpenJFX，0.1 占位） | core |

## 核心约定

- **包名**：`com.acanx.module.aha.*`
- **JPMS 模块名**：`com.acanx.module.aha.*`
- **CLI 命令**：`aha`
- **配置文件**：`Aha.yaml`（主配置）、`Model.yml`（模型供应商）
- **YAML 字段**：大驼峰（PascalCase）；**SQL 字段**：snake_case
- **环境变量前缀**：`AHA_`
- **数据库**：`Aha.db`（SQLite）
- **扩展描述符**：`AhaExtension.yaml`
- **扩展目录**：`AHA_HOME/Extension/`

## 开发规范

详见 `Docs/DevSpec/`：

- [Constitution.md](Docs/DevSpec/Constitution.md) - 技术宪法
- [CodingStandard.md](Docs/DevSpec/CodingStandard.md) - 编码规范
- [ModuleConvention.md](Docs/DevSpec/ModuleConvention.md) - 模块约定
- [DocumentationSpec.md](Docs/DevSpec/DocumentationSpec.md) - 文档规范
- [TodoLifecycleSpec.md](Docs/DevSpec/TodoLifecycleSpec.md) - 待办生命周期规范（完成后的归档 SOP：TD- → DN-，编号不变）
- [BuildSpec.md](Docs/DevSpec/BuildSpec.md) - 构建规范
- [GitHubActionsSpec.md](Docs/DevSpec/GitHubActionsSpec.md) - GitHub Actions 规范（工作流命名 / 触发 / 权限 / 留痕）
- [TestingSpec.md](Docs/DevSpec/TestingSpec.md) - 测试规范
- [YamlFieldSpec.md](Docs/DevSpec/YamlFieldSpec.md) - YAML 字段规范
- [CommitMessageSpec.md](Docs/DevSpec/CommitMessageSpec.md) - 提交规范
- [IssueTrackingSpec.md](Docs/DevSpec/IssueTrackingSpec.md) - Issue 管理规范（开单 / 状态 / 关闭）
- [BranchStrategy.md](Docs/DevSpec/BranchStrategy.md) - 分支策略
- [PullRequestWorkflowSpec.md](Docs/DevSpec/PullRequestWorkflowSpec.md) - PR 合并操作规范（推送前必查 PR 状态，已合并须新开 PR）
- [CodeReviewChecklist.md](Docs/DevSpec/CodeReviewChecklist.md) - 评审清单
- [ReleaseProcess.md](Docs/DevSpec/ReleaseProcess.md) - 发布流程

## 设计文档

**唯一权威文档**：[AHA-Design-V1.md](Docs/AHA/AHA-Design-V1.md) —— 设计蓝图、版本路线图与阶段计划。
冲突时以它为准。

子系统设计详见 `Docs/Design/`：

- [ArchitectureOverview.md](Docs/Design/ArchitectureOverview.md)
- [AgentServiceDesign.md](Docs/Design/AgentServiceDesign.md)
- [LlmAdapterDesign.md](Docs/Design/LlmAdapterDesign.md)
- [InternalProtocolSpec.md](Docs/Design/InternalProtocolSpec.md)
- [RemoteAndProtocolDesign.md](Docs/Design/RemoteAndProtocolDesign.md) - 远程接入与协议演进（1.0+）
- [ToolSystemDesign.md](Docs/Design/ToolSystemDesign.md)
- [ExtensionSystemDesign.md](Docs/Design/ExtensionSystemDesign.md)
- [SelfHostingDesign.md](Docs/Design/SelfHostingDesign.md) - AHA 自举里程碑（1.0 硬门槛）
- [LoggingDesign.md](Docs/Design/LoggingDesign.md) - 日志设计（装配方式、分级分流、命名与切分、已知限制）
- [PixelLogoDesign.md](Docs/Design/PixelLogoDesign.md) - 像素风启动标志设计（画法、调色板、复现与漂移检测）
- [CLIDesign.md](Docs/Design/CLIDesign.md) - CLI 设计（决策过程与备选方案）
- [TUIDesign.md](Docs/Design/TUIDesign.md) - 终端界面当前实现：支持范围、版面样式示例、降级矩阵
- [GUIDesign.md](Docs/Design/GUIDesign.md) - 桌面端界面方案：布局、视觉语言、菜单与交互流程（0.2）
- [DesktopNativeDesign.md](Docs/Design/DesktopNativeDesign.md) - 桌面端原生镜像（试验性，`aha-desktop-native` + `DesktopNative.yml`）
- [DesktopNativeUpdateDesign.md](Docs/Design/DesktopNativeUpdateDesign.md) - 原生镜像版本更新脚本（`Script/Python/DesktopNativeVersionUpdate.py`）
- [CliNativeDesign.md](Docs/Design/CliNativeDesign.md) - CLI 原生镜像（试验性，`aha-cli-native` + `CliNative.yml`）
- [DesktopAutoUpdateDesign.md](Docs/Design/DesktopAutoUpdateDesign.md) - **桌面端应用内升级**（通道契约、服务层职责、换装协议与计划文件、界面与配置键、安全模型、**验证记录**）

## 用户指南

详见 `Docs/Guide/`：

- [GettingStarted.md](Docs/Guide/GettingStarted.md) - 快速开始
- [BuildGuide.md](Docs/Guide/BuildGuide.md) - 构建指南
- [VersionBumpGuide.md](Docs/Guide/VersionBumpGuide.md) - **版本切换指南**（操作步骤 / 检查清单 / 常见问题快速处置 / 应急预案；规范见 `ReleaseProcess.md`）
- [CommandCheatsheet.md](Docs/Guide/CommandCheatsheet.md) - 常用命令速查（编译 / 更新 / 解压 / 启动；落地为 `Dist/README.commands.md`）
- [AppUpdateGuide.md](Docs/Guide/AppUpdateGuide.md) - **应用内升级使用指南**（界面步骤 / 命令行入口 / 失败排查 / 平台支持范围；设计见 `DesktopAutoUpdateDesign.md`）
- [SnapshotSelfTestGuide.md](Docs/Guide/SnapshotSelfTestGuide.md) - **快照自测留痕使用指南**（`/snapshot-assert` 的完整用法与端到端示例；规范见 `ReleaseProcess.md` §2.2）
- [ConfigurationGuide.md](Docs/Guide/ConfigurationGuide.md) - 配置指南
- [ProviderSetupGuide.md](Docs/Guide/ProviderSetupGuide.md) - 供应商设置
- [ToolUsageGuide.md](Docs/Guide/ToolUsageGuide.md) - 工具使用
- [ExtensionAuthoringGuide.md](Docs/Guide/ExtensionAuthoringGuide.md) - 扩展开发
- [TroubleshootingGuide.md](Docs/Guide/TroubleshootingGuide.md) - 排错指南
- [NativeRenderDiagnosticsGuide.md](Docs/Guide/NativeRenderDiagnosticsGuide.md) - 原生渲染诊断手册（真机取证步骤：跑原生包与 JVM 模式、收集诊断日志、对照判据）
- [GraalVMInActionsGuide.md](Docs/Guide/GraalVMInActionsGuide.md) - **GitHub Actions 里的 GraalVM 参考**（Action 输入速查、发行版与版本选择、native-image 参数定法与「四类清单」闭环、权威查询入口含 `native-image --help-extra` 等本地自证命令、症状→参数对照表）
- [PlanWritingGuide.md](Docs/Guide/PlanWritingGuide.md) - **方案文档编写参考**（较大方案变更 / 新特性的文档体例；参考性，非强制）

## 待办与计划

> **`TODO.md` / `PLAN.md` / `Dbsx.txt` 均已冻结为只读历史**：新的待办与进展一律走
> GitHub Issue（总览 #86），不再写入这三个文件。

- **GitHub Issue** — 遗留事项、待决策项、需人工 / 平台执行的事项（总览 #86）
- [Docs/TODO/README.md](Docs/TODO/README.md) — 待办清单与进度看板；待办详情为同目录的 `TD-PPPPP-*.md`（每轮工作结束前扫一遍）
- [Docs/Troubleshooting/](Docs/Troubleshooting/) — 问题排查 / 事故复盘（`TS-yyMM-NNN-英文词序.md`）
- [Docs/DevLog/](Docs/DevLog/) — 已完成事项的开发记录（`yyyyMMdd-HH.md`，规范见该目录 README）
- [TODO.md](Docs/TODO/README.md) — 历史待办与调整项（冻结，仅供追溯）
- [PLAN.md（已删除，内容归档于 DevLog 20261017-15）](Docs/DevLog/20261017-15.md) — 做不到 / 已决定暂缓 / 仍未做且有阻塞的事项，含判断依据（冻结）
- [Dbsx.txt（已删除，需求原文折进 Docs/TODO/ 的 TD）](Docs/TODO/README.md) — 用户原始待办（冻结，已完成项已归档）
- [Archive/](Archive/) — 已废弃的历史资料（不再维护）

## 技能索引

详见 `.agents/skills/`：

| 技能（`name`） | 用途 | 入口 |
|---|---|---|
| `build` | 构建由 CI 承担；按 PR 的 CI 日志排查构建失败（本地不跑 Maven） | [SKILL.md](.agents/skills/build/SKILL.md) |
| `test` | 测试与覆盖率由 CI 承担；新增测试、按 CI 日志排查失败用例 | [SKILL.md](.agents/skills/test/SKILL.md) |
| `llm-adapter` | 新增 LLM 供应商适配器 | [SKILL.md](.agents/skills/llm-adapter/SKILL.md) |
| `tool-authoring` | 新增工具 | [SKILL.md](.agents/skills/tool-authoring/SKILL.md) |
| `extension-authoring` | 新增扩展 | [SKILL.md](.agents/skills/extension-authoring/SKILL.md) |
| `release` | 版本发布流程 | [SKILL.md](.agents/skills/release/SKILL.md) |
| `java-app-graalvm-native-image-compile` | 把 jar 模式程序编成多平台原生镜像（隔离、构建、CI、参数、测量） | [SKILL.md](.agents/skills/java-app-graalvm-native-image-compile/SKILL.md) |
| `graalvm-reachability-metadata` | 原生镜像的元数据登记（反射 / JNI / 资源 / 初始化）：发现 → 登记 → 验证 → 守卫 | [SKILL.md](.agents/skills/graalvm-reachability-metadata/SKILL.md) |
| `issue-tracking` | 遗留事项 / 待决策 / 需人工执行的事项用 Issue 跟踪：判定、写法与命令 | [SKILL.md](.agents/skills/issue-tracking/SKILL.md) |
| `doc-recording` | 三类过程记录的时机与写法（待办 TD / 开发日志 DevLog / 排查记录 TS）：写什么、怎么命名、登记与自检 | [SKILL.md](.agents/skills/doc-recording/SKILL.md) |
| `code-review` | 代码审查（含 PR 自审、多轮复核）：命令取证替代读代码印象、P0–P3 判级、逃逸链（为什么 CI 全绿而功能不可用）、复核「已修」、残余归属与真机验收项 | [SKILL.md](.agents/skills/code-review/SKILL.md) |

## 常用命令

| 命令 | 说明 |
|---|---|
| `./mvnw clean verify` | 完整构建 + 测试 + 覆盖率门禁（**仅 CI / 发布**） |
| `./mvnw -pl aha-core test` | 单模块测试（**仅 CI**） |
| `./mvnw -pl aha-cli exec:java` | 运行 CLI（**仅 CI / 用户明确要求时**） |
| `./mvnw dependency:tree` | 查看依赖树（**仅 CI**） |
| `./mvnw javadoc:javadoc` | 生成 Javadoc（**仅 CI**） |

## 禁止事项

- 禁止使用 `System.out` / `System.err`（CLI 适配层除外）
- 禁止在 Core 中直接使用 `System.exit()`
- 禁止在 POM 中使用 Maven 4 新语法
- 禁止在 Core 中引用 JavaFX、picocli、JLine
- 禁止使用 `newFixedThreadPool` 用于 Agent 任务
- 禁止吞掉异常（空 catch 块）
- 禁止 YAML 字段使用 kebab-case 或 snake_case

## 联系方式

- Issue: https://github.com/your-org/aha/issues
- 讨论: https://github.com/your-org/aha/discussions

AHA中默认优先使用简体中文作为对话、思考、思维链、输出展示的语言；使用UTF-8作为缺省或默认的文本编码；
