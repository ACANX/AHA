# 待办事项清单（TODO）

**文档版本**：v1.3.1
**状态**：生效
**生效日期**：2026-10-09
**最后更新**：2026-10-31
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 0. 本目录的定位

**待办事项的汇总清单与进度看板**。一个待办一个文件（`TD-PPPPP-*.md`），本 `README.md`
是清单索引；逐条的长期跟踪载体仍是 GitHub Issue。

| 载体 | 职责 |
|---|---|
| **本清单**（`Docs/TODO/README.md`） | 汇总全部待办、标记状态、定期更新进度；便于一眼看全貌 |
| **`TD-*.md`** | 单个待办的详情（来源、现状、需求、验收标准）；完成后归档为 `Docs/DONE/DN-*.md`（规范：[TodoLifecycleSpec](../DevSpec/TodoLifecycleSpec.md) §3） |
| **GitHub Issue** | 单个事项的长期跟踪：评论、状态、关闭 |
| `Docs/Troubleshooting/` | 问题排查 / 事故复盘的**过程记录** |
| `Docs/DevLog/` | 已完成事项的**开发记录** |
| 各 `TD-*` 的「结论 / 已排除方案」小节 | 「做不到 / 已决定暂缓」的结论与依据 |

> `Docs/TODO.md` / `Docs/PLAN.md` / `Docs/Dbsx.txt` 已于 2026-10-17 **删除**：已完成 / 不做 / 过时的条目归档在
> [`Docs/DevLog/20261017-15.md`](../DevLog/20261017-15.md)，剩余任务已逐条折进本目录的 `TD-*`。

## 1. 文件命名

```
TD-PPPPP-大驼峰英文标题.md
```

| 片段 | 规则 | 示例 |
|---|---|---|
| `TD` | 固定前缀（Todo） | `TD` |
| `PPPPP` | **五位数字，从 `00001` 开始全局自增**，不复用、不跳号 | `00001`、`00002` |
| 标题 | **英文大驼峰**（PascalCase），概括事项，2~5 个词 | `CliToolCallOutputRendering` |

示例：

```
TD-00001-CliToolCallOutputRendering.md
TD-00004-CliWindowsMultilinePaste.md
TD-00014-ManualAndExternalActions.md
```

- `README.md` 是清单索引，**不走**本命名（它是目录约定的固定名）；
- 一个待办一个文件；**新文件取当前最大编号 + 1**（本清单 §4 最后一行即最新编号）；
- **内容格式不做强制要求**，建议参考对应 Issue 的结构：来源 / 现状 / 需求或待拍板 / 验收标准 / 关联；
- 文件头部用引用块写明「待办编号 / 标题 / 状态 / 跟踪 Issue / 创建日期 / 最后更新」。

## 2. 维护规则

**何时新增**：出现一个**本轮不做或做不完**的事项时——需要长期跟踪就**开 Issue**，
需要看进度就在本目录建 TD 文件；两者常一起用（Issue 跟踪进展，TD 登记进度）。
本轮就能改完的缺陷**不用**建 TD，直接改。

- **新增待办**：建 `TD-<下一个编号>-*.md`，并在 §4 清单加一行、在最近的分组里登记；
- **状态更新**：有进展或闭环时，**同一变更内**同时更新 TD 文件的头部状态与 §4 清单该行；
- **已完成**：状态写 `✅ 已完成`，并在备注里写清**依据**（CI 运行编号 / PR / 实测），不要只写「完成」；
- **定期维护**：每轮工作结束前扫一遍 §4，把状态过期（实际已闭环 / 已搁置）的行更新，
  并同步文件头的「最后更新」；
- **与 Issue 同步**：Issue 关闭后清单标 `✅ 已完成`；Issue 重开时同步回退状态。

### 状态标记

| 标记 | 含义 |
|---|---|
| `☐ 未开始` | 已登记，尚未动手 |
| `◐ 进行中` | 部分完成，或已改动未收口 |
| `⏸ 阻塞` | 依赖外部条件（人工 / 平台 / 外部环境），备注写明等什么 |
| `✅ 已完成` | 已闭环，备注里给依据 |
| `✖ 已取消` | 决定不做，备注里写明原因与替代方案 |

---

## 3. 当前编号

- 已用：`TD-00001` ~ `TD-00061`
- **下一个可用编号：`TD-00062`**

> 订正（2026-10-19）：本行此前停在 `TD-00023`（实际早已用到 `TD-00058`，与之矛盾）。
> 补记（2026-10-31）：新增 `TD-00061`（账号封禁遗留事项台账）。

## 4. 清单

### 4.1 CLI / TUI

| TD | 标题 | 状态 | Issue | 备注 |
|---|---|---|---|---|
| [TD-00001](TD-00001-CliToolCallOutputRendering.md) | 工具调用输出的分块展示与差异渲染 + DeepSeek 工具调用补齐 | ☐ 未开始 | [#72](https://github.com/ACANX/AHA/issues/72) | 来自 `Dbsx.txt` §1 |
| [TD-00002](TD-00002-CliMarkdownRichRendering.md) | Markdown 富文本渲染与渲染缺口（表格分割线 / 图片 / 语法高亮 / OSC 8） | ☐ 未开始 | [#73](https://github.com/ACANX/AHA/issues/73) | |
| [TD-00003](TD-00003-CliInputAndStatusBar.md) | 输入框样式与 loading 动效、底部状态栏、斜杠命令下拉补全 | ☐ 未开始 | [#74](https://github.com/ACANX/AHA/issues/74) | 状态栏用量部分依赖 0.6 用量落库 |
| [TD-00004](TD-00004-CliWindowsMultilinePaste.md) | Windows 终端多行粘贴兼容性（conhost 限制） | ◐ 进行中 | [#75](https://github.com/ACANX/AHA/issues/75) | 已有两项加固，conhost 行为待定 |
| [TD-00059](TD-00059-TableBlankLineBoundary.md) | 表格块边界：空行应终止表格，且表格与正文之间保留空行 | ☐ 未开始 | [#346](https://github.com/ACANX/AHA/issues/346) | 验收 #345 时由 93 行组合样例实测发现；伴生修复见 `Docs/DevLog/20261019-11.md` |
| [TD-00060](TD-00060-Milestone8ExecutionOrder.md) | Milestone 8（V0.1.5）未完成 Issue 的优先级与依赖排序 | ◐ 进行中 | [Milestone 8](https://github.com/ACANX/AHA/milestone/8) | 21 个 open 的盘点 / 拓扑 / 5 批执行顺序；含 2 个待拍板项 |

### 4.2 桌面端

| TD | 标题 | 状态 | Issue | 备注 |
|---|---|---|---|---|
| [TD-00005](TD-00005-Desktop02Remaining.md) | 0.2 收尾：工具面板开关、上下文压缩入口、会话内搜索与 F6 | ☐ 未开始 | [#76](https://github.com/ACANX/AHA/issues/76) | |
| [TD-00006](TD-00006-DesktopProviderEnhancement.md) | 供应商连接测试 + 密钥写入密钥库 | ☐ 未开始 | [#77](https://github.com/ACANX/AHA/issues/77) | |
| [TD-00007](TD-00007-DesktopPackagingAndCoverage.md) | 覆盖率门禁上线（`jpackage` 部分已决策跳过） | ☐ 未开始 | [#78](https://github.com/ACANX/AHA/issues/78) | |

### 4.3 原生镜像

| TD | 标题 | 状态 | Issue | 备注 |
|---|---|---|---|---|
| [TD-00008](TD-00008-NativeExtensionModuleLayerConflict.md) | 扩展系统与 native-image 的根本冲突与降级策略（E-01） | ☐ 未开始 | [#79](https://github.com/ACANX/AHA/issues/79) | 前置决策，建议先拍板 |
| [TD-00009](TD-00009-NativeRiskAndReleaseMatrix.md) | E-02 ~ E-11 风险登记、验收标准与发行矩阵 | ☐ 未开始 | [#80](https://github.com/ACANX/AHA/issues/80) | |
| [TD-00010](TD-00010-NativePipelineReview.md) | 管线复核与优化（N-10 ~ N-17、N-19、N-21、N-22） | ☐ 未开始 | [#81](https://github.com/ACANX/AHA/issues/81) | |
| [TD-00015](TD-00015-CliNativeVersionUpdateScript.md) | 补 CLI 原生镜像的自动取件脚本（`CliNativeVersionUpdate.py`） | ✅ 已完成（2026-10-13） | [#156](https://github.com/ACANX/AHA/issues/156) | 抽共享引擎 + 两个薄壳；顺手补了 `Dist/Update*.cmd` 一键入口 |

### 4.4 核心 / 架构 / 记忆

| TD | 标题 | 状态 | Issue | 备注 |
|---|---|---|---|---|
| [TD-00011](TD-00011-CoreContractAndArchGovernance.md) | AgentEngine 契约、ServiceLoader 双声明、依赖治理、jlink 解除条件 | ◐ 进行中（`A-07` 覆盖率排除项引用链已收口 2026-10-17） | [#82](https://github.com/ACANX/AHA/issues/82) | |
| [TD-00012](TD-00012-MemoryCapability06.md) | 记忆能力（0.6）：upsert / 工具与 `/memory` / 策略接入 / curate / 载体选型 | ☐ 未开始 | [#83](https://github.com/ACANX/AHA/issues/83) | |

### 4.5 工程效能 / 文档 / 人工

| TD | 标题 | 状态 | Issue | 备注 |
|---|---|---|---|---|
| [TD-00013](TD-00013-EngineeringAndDocHygiene.md) | CI 日志噪音、工作流与文档检查、脚本目录、暂存区文档去留 | ☐ 未开始 | [#84](https://github.com/ACANX/AHA/issues/84) | |
| [TD-00014](TD-00014-ManualAndExternalActions.md) | 分支规则集整改、定期扫描、合并方式、裸 tag、0.1.1 发布、真机走查 | ✅ 已完成（2026-10-16） | [#85](https://github.com/ACANX/AHA/issues/85)（**已关闭**） | 六项均有结论（G-02 / G-05 不做、G-03 已验、G-06 完成、0.1.1 结项、真机 7 项通过）；「tag 不触发 `Release.yml`」转 [#238](https://github.com/ACANX/AHA/issues/238)（TD-00018） |
| [TD-00016](TD-00016-SingleSourceVersionBump.md) | 版本切换收敛为「改一行」：`${revision}` + 单一读取入口 + 一致性校验 | ☐ 未开始 | [#92](https://github.com/ACANX/AHA/issues/92)（父；子 [#93](https://github.com/ACANX/AHA/issues/93)~[#97](https://github.com/ACANX/AHA/issues/97)） | 含根因反思；分 5 阶段（P1 校验 → P2 读取收敛 → P3 `${revision}` → P4 分发器 → P5 文档） |
| [TD-00017](TD-00017-TroubleshootingDocSkeletonAlignment.md) | Troubleshooting 排查档案正文对齐 TroubleShootingDocSpec 的**四节必备项** | ✅ 已完成（2026-10-13） | [#128](https://github.com/ACANX/AHA/issues/128)（**已关闭**） | 取**选项 C**：32 篇 + 模板补齐四节；规范新增 §5（+ §4 入口）；`CheckDocs.py` 新增机器检查 |
| [TD-00019](TD-00019-DefaultChannelBackToStable.md) | 默认升级通道从 `Beta` 改回 `Stable` | ✅ **已完成**（2026-10-17，PR [#255](https://github.com/ACANX/AHA/pull/255) → `1f10666`）；「`Stable` 通道能列出版本」依赖种子发版，转 [#238](https://github.com/ACANX/AHA/issues/238) 跟踪 | [#252](https://github.com/ACANX/AHA/issues/252) | D9 的临时默认值（Stable 当时无可下包）；机制已就绪：`Update.Channel` **缺键不写盘**，所以改回默认值会惠及「从没选过该键」的安装；原载体 #51 已关闭，本单转由 #252 跟踪 |
| [TD-00022](TD-00022-NativeImageSelfUpdate.md) | 原生镜像（`AhaDesktopNative`）的应用内自更新 | ⏳ 待真机确认（唯一剩余项已登记 #122） | [#122](https://github.com/ACANX/AHA/issues/122) | 通道契约 / 资产选择 / 形态判定 / 包内清单与 helper / 换装计划 / 重启命令**逐层核对均已就绪**；我曾误判为「未实现」并多拦一层（表现为「升级按钮永远是灰的」），门已撤、正向用例已补；剩下的是**真机验一次「替换正在运行的 exe」**（`TS-2610-047` §4.1 记录了这次误判的更正）；#247 已随 PR #243 交付后关闭 |
| [TD-00023](TD-00023-TuiGuiRealMachineChecklist.md) | TUI / GUI **必须在真机或人工确认**的 7 项（WSL 内验不到） | ☐ 未开始 | [#122](https://github.com/ACANX/AHA/issues/122) | 原 `Docs/PLAN.md` §5，该文件已于 2026-10-17 删除，原文保存在本文档 |
| [TD-00024](TD-00024-CliToolCallEditDiffRendering.md) | 工具调用：编辑类输出「分块 + 前后差异」渲染 | ☐ 未开始 | [#72](https://github.com/ACANX/AHA/issues/72) | 主题 [TD-00001](TD-00001-CliToolCallOutputRendering.md) |
| [TD-00025](TD-00025-CliToolCallAddFileChunking.md) | 工具调用：新增文件输出「路径 + 内容」分块，超 500 行折叠 | ☐ 未开始 | [#72](https://github.com/ACANX/AHA/issues/72) | 主题 [TD-00001](TD-00001-CliToolCallOutputRendering.md) |
| [TD-00026](TD-00026-CliToolCallReadFileChunking.md) | 工具调用：浏览文件输出「命令 + 文件内容」分块，过长折叠 | ☐ 未开始 | [#72](https://github.com/ACANX/AHA/issues/72) | 主题 [TD-00001](TD-00001-CliToolCallOutputRendering.md) |
| [TD-00027](TD-00027-CliToolCallExecOutputChunking.md) | 工具调用：执行命令输出「命令 + 结果」分块 | ☐ 未开始 | [#72](https://github.com/ACANX/AHA/issues/72) | 主题 [TD-00001](TD-00001-CliToolCallOutputRendering.md) |
| [TD-00028](TD-00028-DeepSeekToolCallApiParity.md) | 补齐 DeepSeek 工具调用缺失的命令 / API | ☐ 未开始 | [#72](https://github.com/ACANX/AHA/issues/72) | 主题 [TD-00001](TD-00001-CliToolCallOutputRendering.md) |
| [TD-00029](TD-00029-MarkdownRichTextRendering.md) | 会话总结的 Markdown 渲染成富文本（彩色 / 分格式） | ☐ 未开始 | [#73](https://github.com/ACANX/AHA/issues/73) | 主题 [TD-00002](TD-00002-CliMarkdownRichRendering.md) |
| [DN-00030](../DONE/DN-00030-TableRowSeparatorPolicy.md) | 表格数据行之间补水平分割线（A 全表加）—— **已归档到 `Docs/DONE/`** | ✅ 已完成（已归档，#273 / #290） | [#273](https://github.com/ACANX/AHA/issues/273) | 主题 [TD-00002](TD-00002-CliMarkdownRichRendering.md) |
| [TD-00031](TD-00031-MarkdownRenderingGapsPolicy.md) | 渲染缺口逐项结论：图片 / 语法高亮 / OSC 8 / 单元格换行 | ⏸ 待决策（#274） | [#274](https://github.com/ACANX/AHA/issues/274) | 主题 [TD-00002](TD-00002-CliMarkdownRichRendering.md) |
| [TD-00032](TD-00032-InputBoxBorderStyle.md) | 输入框样式：用上下制表符包裹，去掉 `aha>` 前缀 | ☐ 未开始 | [#74](https://github.com/ACANX/AHA/issues/74) | 主题 [TD-00003](TD-00003-CliInputAndStatusBar.md) |
| [TD-00033](TD-00033-InputBoxLoadingAnimation.md) | 输入框 loading 动效（旋转字符） | ☐ 未开始 | [#74](https://github.com/ACANX/AHA/issues/74) | 主题 [TD-00003](TD-00003-CliInputAndStatusBar.md) |
| [TD-00034](TD-00034-CliStatusBarInfo.md) | 状态栏信息：模型/目录/token/上下文占比/运行模式（口径待拍板） | ⏸ 待决策（#275） | [#275](https://github.com/ACANX/AHA/issues/275) | 主题 [TD-00003](TD-00003-CliInputAndStatusBar.md) |
| [TD-00035](TD-00035-SlashCommandCompletion.md) | 斜杠命令下拉补全（命令本体已支持） | ☐ 未开始 | [#74](https://github.com/ACANX/AHA/issues/74) | 主题 [TD-00003](TD-00003-CliInputAndStatusBar.md) |
| [TD-00036](TD-00036-ConhostPasteRealMachineVerification.md) | conhost 粘贴侧门真机验证（Alt+V / `/paste`） | ☐ 未开始 | [#75](https://github.com/ACANX/AHA/issues/75) | 主题 [TD-00004](TD-00004-CliWindowsMultilinePaste.md) |
| [TD-00037](TD-00037-AgentEngineEmptyChoicesContent.md) | `AgentEngine.run()`：空 `choices` 不得返回 `null` 正文 | ☐ 未开始 | [#82](https://github.com/ACANX/AHA/issues/82) | 主题 [TD-00011](TD-00011-CoreContractAndArchGovernance.md) |
| [TD-00038](TD-00038-AgentEngineStreamFinishReason.md) | `AgentEngine.stream()`：`finishReason` 的兜底值 | ☐ 未开始 | [#82](https://github.com/ACANX/AHA/issues/82) | 主题 [TD-00011](TD-00011-CoreContractAndArchGovernance.md) |
| [TD-00039](TD-00039-ConstitutionJpmsRationale.md) | `Constitution.md` / `ModuleConvention.md` 补「为何模块化优先」 | ☐ 未开始 | [#82](https://github.com/ACANX/AHA/issues/82) | 主题 [TD-00011](TD-00011-CoreContractAndArchGovernance.md) |
| [TD-00040](TD-00040-ServiceLoaderDualDeclarationDecision.md) | `ServiceLoader` 双声明：拍板三选一并落地 | ⏸ 待决策（#276） | [#276](https://github.com/ACANX/AHA/issues/276) | 主题 [TD-00011](TD-00011-CoreContractAndArchGovernance.md) |
| [TD-00041](TD-00041-DependencyGovernanceDecision.md) | 依赖治理：Enforcer / ArchUnit 引入与否 | ⏸ 待决策（#277） | [#277](https://github.com/ACANX/AHA/issues/277) | 主题 [TD-00011](TD-00011-CoreContractAndArchGovernance.md) |
| [TD-00042](TD-00042-JlinkBanLiftConditions.md) | `BuildSpec.md` §7 补 `jlink` 禁令的解除条件 | ☐ 未开始 | [#82](https://github.com/ACANX/AHA/issues/82) | 主题 [TD-00011](TD-00011-CoreContractAndArchGovernance.md) |
| [TD-00043](TD-00043-NativeModuleLayerConflictDecision.md) | `ModuleLayer` 与 native-image closed-world 冲突：给结论 | ⏸ 待决策（#278） | [#278](https://github.com/ACANX/AHA/issues/278) | 主题 [TD-00008](TD-00008-NativeExtensionModuleLayerConflict.md) |
| [TD-00044](TD-00044-DesktopFxmlOpensNarrowing.md) | `module-info` 收紧 FXML 的 `opens` | ☐ 未开始 | [#76](https://github.com/ACANX/AHA/issues/76) | 主题 [TD-00005](TD-00005-Desktop02Remaining.md) |
| [TD-00045](TD-00045-PlatformMatrixAlignment.md) | 平台清单三处对齐（设计 / Build / Release） | ☐ 未开始 | [#76](https://github.com/ACANX/AHA/issues/76) | 主题 [TD-00005](TD-00005-Desktop02Remaining.md) |
| [TD-00046](TD-00046-DesktopMemoryPanel.md) | 桌面端 0.2：记忆面板 | ☐ 未开始 | [#76](https://github.com/ACANX/AHA/issues/76) | 主题 [TD-00005](TD-00005-Desktop02Remaining.md) |
| [TD-00047](TD-00047-DesktopExtensionPanel.md) | 桌面端 0.2：扩展面板 | ☐ 未开始 | [#76](https://github.com/ACANX/AHA/issues/76) | 主题 [TD-00005](TD-00005-Desktop02Remaining.md) |
| [TD-00048](TD-00048-DesktopIdentityLoadOrderDisplay.md) | 桌面端 0.2：身份加载顺序展示 | ☐ 未开始 | [#76](https://github.com/ACANX/AHA/issues/76) | 主题 [TD-00005](TD-00005-Desktop02Remaining.md) |
| [TD-00049](TD-00049-DesktopSessionSearch.md) | 桌面端 0.2：会话内搜索（`Ctrl+F`） | ☐ 未开始 | [#76](https://github.com/ACANX/AHA/issues/76) | 主题 [TD-00005](TD-00005-Desktop02Remaining.md) |
| [TD-00050](TD-00050-DesktopCoverageGateEnablement.md) | 桌面端覆盖率门禁上线（0.30 → 0.70 两步走） | ☐ 未开始 | [#78](https://github.com/ACANX/AHA/issues/78) | 主题 [TD-00007](TD-00007-DesktopPackagingAndCoverage.md) |
| [TD-00051](TD-00051-ScriptDirectoryConsolidation.md) | `Script/` 与 `bin/` 是否合并（`.ps1` 已纳入检查） | ⏸ 待决策（#279） | [#279](https://github.com/ACANX/AHA/issues/279) | 主题 [TD-00013](TD-00013-EngineeringAndDocHygiene.md) |
| [TD-00052](TD-00052-WorkflowYamlStaticCheck.md) | `.github/**/*.yml` 是否纳入静态检查 | ⏸ 待决策（#280） | [#280](https://github.com/ACANX/AHA/issues/280) | 主题 [TD-00013](TD-00013-EngineeringAndDocHygiene.md) |
| [TD-00053](TD-00053-DocDuplicateNumberCheck.md) | 文档标题 / 表行编号重复是否纳入检查 | ⏸ 待决策（#281） | [#281](https://github.com/ACANX/AHA/issues/281) | 主题 [TD-00013](TD-00013-EngineeringAndDocHygiene.md) |
| [TD-00054](TD-00054-Maven4StderrNoise.md) | Maven 4 `verify` 的 10 行 `[stderr]` 噪音 | ⏸ 待决策（#282） | [#282](https://github.com/ACANX/AHA/issues/282) | 主题 [TD-00013](TD-00013-EngineeringAndDocHygiene.md) |
| [TD-00055](TD-00055-MemoryBackendSelection.md) | 记忆存储载体选型（表 / MD / 混合） | ⏸ 待决策（#283） | [#283](https://github.com/ACANX/AHA/issues/283) | 主题 [TD-00012](TD-00012-MemoryCapability06.md) |
| [TD-00056](TD-00056-SessionHistoryProjectScope.md) | 会话历史是否也改为项目级 | ⏸ 待决策（#284） | [#284](https://github.com/ACANX/AHA/issues/284) | 主题 [TD-00012](TD-00012-MemoryCapability06.md) |
| [TD-00057](TD-00057-TodoReviewBackfill.md) | 复核结论回灌正式文档（原 `TODO.md` §8 的遗留） | ☐ 未开始 | [#84](https://github.com/ACANX/AHA/issues/84) | 主题 [TD-00013](TD-00013-EngineeringAndDocHygiene.md) |
| [TD-00058](TD-00058-MvnCleanLockedFileHandling.md) | `mvn clean` 无法删除被占用文件（Windows） | ⏸ 待决策（#285） | [#285](https://github.com/ACANX/AHA/issues/285) | 主题 [TD-00013](TD-00013-EngineeringAndDocHygiene.md) |
| [TD-00020](TD-00020-PosixUpdateHelper.md) | POSIX 换装 helper（Linux / macOS 的应用内升级） | ⏳ 待做 | [#250](https://github.com/ACANX/AHA/issues/250) | 服务层 / 计划 / 回执 / 握手都已平台无关，增量只在一个 `apply-update.sh`；现状是界面**提前置灰**并说明原因（2026-10-16 审查 P1-7 已修），功能本身仍缺；原载体 #51 已关闭，本单转由 #250 跟踪 |
| [TD-00021](TD-00021-RollbackToPreviousVersion.md) | 回滚到上一版（界面入口） | ⏳ 待做 | [#251](https://github.com/ACANX/AHA/issues/251) | 备份与 `SwapPlan.rollback` 都已就绪，缺的是「让用户看得见、点得到」；入口必须照实说明 `Update.BackupKeep` 决定了能切回的范围（2026-10-16 第二轮审查 P3-⑤）；原载体 #51 已关闭，本单转由 #251 跟踪 |
| [TD-00018](TD-00018-ReleaseTagAutoTrigger.md) | 自动打的 `V*` tag 不触发 `Release.yml`：不配 PAT 打通自动出包 | ✅ **已完成**（2026-10-17） | [#238](https://github.com/ACANX/AHA/issues/238)（**已关闭**） | **方案 B 已实现并闭环**：`Build.yml` 新建出 tag 后 `gh workflow run Release.yml --ref main` 派发（PR [#240](https://github.com/ACANX/AHA/pull/240)）。**首次真发版验收**（`V0.1.3`，2026-10-17）：`main` 构建成功 → tag 自动创建 → 自动派发 `Release.yml` → 4 份 zip + `SHA256SUMS`，7 个作业全 success；实物核对（`sha256sum -c` 4/4、包根 `AHA-PACKAGE.json` 逐项一致、jar 无构建号）与客户端契约审计（真实资产名 4/4）见 [#238 评论](https://github.com/ACANX/AHA/issues/238#issuecomment-6230970801) |

### 4.6 历史遗留（账号封禁数据回归）

| TD | 标题 | 状态 | 来源 | 备注 |
|---|---|---|---|---|
| [TD-00061](TD-00061-SuspendedBacklogRetracking.md) | 被 suspended 账户遗留事项（#75–#335）回归跟踪 | ◐ 进行中 | 原 issue #437（现已 404）；本地存档 | 261 个编号的 ISSUE/PR 台账：未完成 PR 复核后 **0 条**（24 条均已落地）、非 PR 编号 108 条中 27 条已由现有 TD 承载；截图确认 **40 条 open / 92 条 closed**（真正未落地 5 项）；261 个标题已由 autolink 恢复，剩 33 行归属待核；数据来源见文内 §2 / §8 |

## 5. 已由既有 Issue 跟踪（未建 TD 文件）

> 订正（2026-10-16）：本节里**由既有 Issue 跟踪、不单建 TD 文件**的事项，上一批
> （#28 / #48 / #49 / #51 / #60）已全部关闭，保留作**去向索引**（写清改动落在哪、后续项去了哪）；
> 仍在进行的事项照常跟踪。新产生的遗留事项一律建 TD 文件 + 开 Issue（见上方清单）。

| Issue | 事项 | 状态与去向 |
|---|---|---|
| [#48](https://github.com/ACANX/AHA/issues/48) | 原生桌面端字体渲染与 JVM 模式不一致 | ✅ 已关闭；**已临时缓解**（原生内默认走灰度、可回退），根因未根治，见 `TS-2610-030` |
| [#49](https://github.com/ACANX/AHA/issues/49) | 暗色模式下标题文字对比度 / 控件适配 | ✅ 已关闭；见 `TS-2610-025` / `TS-2610-026` / `TS-2610-028` |
| [#51](https://github.com/ACANX/AHA/issues/51) | GUI 从发布页检测最新 tag 并完成本地升级 | ✅ **已交付并关闭**（PR #243 → 合并 `cd3be16`）；后续项转 TD-00019 / TD-00020 / TD-00021 |
| [#60](https://github.com/ACANX/AHA/issues/60) | 原生桌面镜像读不到系统与驱动信息 | ✅ 已关闭；见 `TS-2610-034`（改后真机取到真实驱动值） |
| [#28](https://github.com/ACANX/AHA/issues/28) | GraalVM GitHub Action 参数配置参考文档 | ✅ 已关闭；交付物为 `Docs/Guide/GraalVMInActionsGuide.md` |
| [#67](https://github.com/ACANX/AHA/issues/67) | 供应商内 N 档模型（0.2 候选） | ☐ 打开中（未建 TD 文件） |
| [#68](https://github.com/ACANX/AHA/issues/68) | 对话中粘贴图片 / 文件的多模态输入 | ☐ 打开中（先做需求调研） |
| [#70](https://github.com/ACANX/AHA/issues/70) | Agent 执行边界：把「本地不跑 Maven」升级为执行域约束 | ☐ 打开中（未建 TD 文件） |
