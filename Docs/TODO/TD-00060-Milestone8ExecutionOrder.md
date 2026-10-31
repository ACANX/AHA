# TD-00060 Milestone8ExecutionOrder

> 待办编号：TD-00060
> 标题：Milestone 8（V0.1.5）未完成 Issue 的优先级与依赖排序
> 状态：◑ 进行中（批 2 部分完成：#330 状态栏、#342 状态位已合并；#326 工具块待合）
> 跟踪 Issue：[Milestone 8](https://github.com/ACANX/AHA/milestone/8)（各单仍由自身 Issue 跟踪，本文件只管「先做哪个」）
> 所属主题：—
> 创建日期：2026-10-19
> 最后更新：2026-10-19

---

## 来源

ACANX 2026-10-19 要求：分析 [Milestone 8（V0.1.5）](https://github.com/ACANX/AHA/milestone/8)
中未完成的 Issue，指出**优先项**与**拓扑依赖**，给出最优实现路径。

取数方式（可重跑复核）：

```bash
gh api repos/ACANX/AHA/milestones/8 --jq '{title,description,open_issues,closed_issues}'
gh issue list --milestone 8 --state open --limit 100 --json number,title,body
```

Milestone 描述：**「0.1.5 收尾：移除旧资产名别名（守卫版本门槛判红）、CLI 渲染与交互（#72 #73 #74 #75）」**，
当前 **21 open / 12 closed**。

## 结论摘要

1. **4 个其实已经实现**（#332、#322、#303 的门禁、#269 的 CHANGELOG 项）——先核实关单，避免重复开发；
2. 真正需要开发的关键路径只有两条：
   - **发布收尾**：#269 剩余（文档旧名）→ 0.1.5 发版 → #303 验收（其验收窗口只在发版后）；
   - **CLI 基础设施**：#330 状态栏 → #342 状态位 → #334 降级收口；#340 色彩矩阵是渲染子单的共同前置；
3. #339 / #327 可立即并行；#331 可先做占位、数据源等 0.6；#321 上游阻塞；#75 缺 Windows 真机。

---

## 进度更新（2026-10-19）

> 依据：`gh issue list --milestone 8 --state all`（原 21 open / 12 closed，含后续新增单）。

### 已完成（已关闭）

| Issue | 状态 | 依据 |
|---|---|---|
| [#330](https://github.com/ACANX/AHA/issues/330) 状态栏静态三项 | ✅ CLOSED | PR [#352](https://github.com/ACANX/AHA/pull/352) 合并 |
| [#335](https://github.com/ACANX/AHA/issues/335) Markdown 基线 | ✅ CLOSED | PR [#345](https://github.com/ACANX/AHA/pull/345) 合并 |
| [#333](https://github.com/ACANX/AHA/issues/333) 斜杠命令下拉 | ✅ CLOSED | — |
| [#328](https://github.com/ACANX/AHA/issues/328) 输入框外观 | ✅ CLOSED | — |
| [#341](https://github.com/ACANX/AHA/issues/341) ESC 中断 | ✅ CLOSED | — |
| [#357](https://github.com/ACANX/AHA/issues/357) Model.yml 重构 | ✅ CLOSED | PR [#360](https://github.com/ACANX/AHA/pull/360) 合并 |
| [#359](https://github.com/ACANX/AHA/issues/359) 中断独立状态 | ✅ CLOSED | PR [#362](https://github.com/ACANX/AHA/pull/362) 合并 |

### 已实现、待关单

| Issue | 状态 | 说明 |
|---|---|---|
| [#342](https://github.com/ACANX/AHA/issues/342) 运行状态位 | 🟡 已实现（PR [#356](https://github.com/ACANX/AHA/pull/356) 合并），Issue 仍 OPEN | 八态（含「已中断」）；核实后关单 |
| [#332](https://github.com/ACANX/AHA/issues/332) 斜杠命令 | 🟡 代码已注册，待逐条验收 | 见「一、盘点 A」 |
| [#322](https://github.com/ACANX/AHA/issues/322) 分块框架 | 🟡 框架已在用，待拍板折叠口径 | 见「四、待拍板」#1 |

### 进行中 / 待办（按批次）

| 批次 | 内容 | 状态 |
|---|---|---|
| 批 1 | #269 文档旧名 9 处；#321 决策 | ⏳ 未做 / 上游阻塞 |
| 批 2 | #330 ✅；#329、#340 | ⏳ #329 / #340 未做 |
| 批 3 | #342 ✅（待关单）；#336 / #337 / #338、#331、#339 | ⏳ 未做 |
| 批 4 | #323 / #324 / #325 / #326、#327 | 🟡 #326 已实现（PR [#363](https://github.com/ACANX/AHA/pull/363) 待合）；其余未做 |
| 批 5 | #334 → 0.1.5 发版 → #303 | ⏳ 未做 |

### 新增 Issue（原盘点之后创建）

| Issue | 说明 | 状态 |
|---|---|---|
| [#346](https://github.com/ACANX/AHA/issues/346) 表格块边界 | 空行应终止表格 | OPEN，独立 |
| [#354](https://github.com/ACANX/AHA/issues/354) TUI 自适应宽高与底部四行 | 含分块正文与 unified diff | OPEN |
| [#355](https://github.com/ACANX/AHA/issues/355) 状态栏后续项 | 思考强度数据源 / `--workspace` / 供应商显示名 | OPEN |
| [#358](https://github.com/ACANX/AHA/issues/358) 状态栏动态 / 静态分栏 | 目标「倒数第四行横线」受 JLine 限制，已记技术约束 | OPEN，阻塞 |
| [#364](https://github.com/ACANX/AHA/issues/364) 工具块背景渐变 | 需先调研 pi-code-agent 再定方案 | OPEN，待调研 |

### 关键路径（不变）

`#269 文档收尾 → 0.1.5 发版 → #303 验收`。

### 与初版盘点的差异

- 初版判断「#330 未做」→ 已由 PR #352 完成；
- 初版判断「#342 未做」→ 已由 PR #356 完成（Issue 待关）；
- #326 已实现块内底色统一与块间空行（PR #363，待合）；
- 新增 5 个 Issue（#346 / #354 / #355 / #358 / #364），其中 #358 受 JLine 限制阻塞、
  #364 待调研 pi-code-agent 后再定实现方式。

## 一、盘点

### A. 已实现 / 基本实现 —— 先核实，能关就关（零成本）

| Issue | 现状证据 | 判断 |
|---|---|---|
| [#332](https://github.com/ACANX/AHA/issues/332) 斜杠命令注册与本地执行 | `SessionCommandRegistry` 已注册 11 个命令（`AutoCompact` / `Clear` / `Compact` / `Config` / `Context` / `Exit` / `Help` / `Model` / `Memory` / `SessionInfo` / `Tool`）；`ChatCommand:113/166` 已接线 `SessionCommandRegistry` / `SlashMenuReader`；CHANGELOG 已记「会话内命令」 | **大概率已完成**，只差逐条对验收（尤其「请求里不含命令文本」需断言） |
| [#322](https://github.com/ACANX/AHA/issues/322) 工具调用分块渲染框架 | `ToolBlock` 已被 `StreamRenderer:88-112` 调用；`ToolBlock:205` 有「… 输出共 N 行」折叠提示；`PREVIEW_LINES=5` / `FAILURE_LINES=40` / 中间省略 | **框架已完成**；与验收「超 500 行折叠」的**阈值口径不同**，需拍板（见「待拍板」） |
| [#303](https://github.com/ACANX/AHA/issues/303) V0.1.4 发布复盘加固 | 8 个门禁项**全部已勾选**（`continue-on-error` 可见、报告包与主包解耦、完整性硬门等） | 门禁已落地；验收明文「**0.1.5 正式发布后**」才达标 → **发版后再关** |
| #269 的「CHANGELOG 0.1.4 条目」 | `CHANGELOG.md` 的 0.1.4 段已有「四种形态 × 三平台共 12 份」 | 该子项**已完成** |

### B. 发布阻塞 —— 0.1.5 发版前必须做

| 项 | 现状 |
|---|---|
| [#269](https://github.com/ACANX/AHA/issues/269) 剩余：**文档旧资产名** | 实测仍有残留：`CommandCheatsheet.md` 1 处、`DesktopNativeUpdateDesign.md` 1 处、`ReleaseProcess.md` 3 处、`BuildGuide.md` 2 处（#269 正文列了 9 个文件的完整清单） |
| #269 第 3 项：**0.1.5 移除旧名别名** | 守卫 `check_release_matrix()` 的「`version >= 0.1.5` 判红」**已就绪**且 CI 绿 → 代码/工作流侧已无旧名，**只剩文档同步** |
| [#321](https://github.com/ACANX/AHA/issues/321) macOS 桌面原生 | 上游修复（`oracle/graal#14545`）目标 25.5；工作流已提前统一到 `25.5`（分支 `fix/issue-321-graalvm-25-5`，draft PR），**待 25.5 GA 且 dev 原生腿验证通过后合并**——合并前 0.1.5 仍按「缺 macOS 原生」处置，#269 / #303 需写明「本版不完整」 |

> Milestone 描述里的「移除旧资产名别名（守卫版本门槛判红）」= #269 第 3 项：**守卫已备好，实际是文档收尾**。

### C. 基础设施 —— 做完解锁一批

| Issue | 未做部分（证据） | 解锁 |
|---|---|---|
| [#330](https://github.com/ACANX/AHA/issues/330) 状态栏静态三项 | 现有 `StatusLine` 只显示「等待 / 工具执行 + 耗时」，**没有**模型 / 工作目录 / 运行模式 | #342 |
| [#329](https://github.com/ACANX/AHA/issues/329) 输入框空闲动效 | 现有 `StatusLine` 是「**等待期间**转圈」，不是空闲态 | #334 |
| [#340](https://github.com/ACANX/AHA/issues/340) 色彩四档矩阵 | `TerminalCapabilities` 的四档**探测已有**（`NONE / ANSI16 / ANSI256 / TRUECOLOR`），但注释明确「渲染层只用基础 SGR，深度仅决定是否着色」→ **降级映射未做** | #336 / #337 / #338 / #334 |

### D. 可立即并行（依赖已满足）

| Issue | 依赖状态 |
|---|---|
| [#339](https://github.com/ACANX/AHA/issues/339) 表格单元格内换行 | #335（已关）+ #273（已关）+ `TableRenderer`（已有）→ **无阻塞** |
| [#327](https://github.com/ACANX/AHA/issues/327) DeepSeek 工具调用补齐 | issue 原文：「与渲染类子任务互相独立，可并行」 |
| [#331](https://github.com/ACANX/AHA/issues/331) 用量四项 | 验收允许「0.6 落库前显示占位」→ **可先做接口与占位**，数据源等 0.6 |

### E. 收口 / 外部阻塞

| Issue | 说明 |
|---|---|
| [#334](https://github.com/ACANX/AHA/issues/334) 低能力终端降级 | 依赖 #329 / #330 / #332 / #340 → **最后收口** |
| [#75](https://github.com/ACANX/AHA/issues/75) Windows 多行粘贴 | 需 Windows 真机复现 + 书面结论（接受降级写进 `TUIDesign.md`，或提供 `/paste`） |
| [#321](https://github.com/ACANX/AHA/issues/321) macOS 原生 | 上游阻塞，保持跟踪，不做 |
| #331 的数据源 | 0.6 用量落库（跨里程碑） |

---

## 二、依赖拓扑

```text
【发布线】 #269 剩余（文档/旧名） ──► 0.1.5 正式发布 ──► #303 验收关单
                                        ▲
                                        └── #321（macOS 原生，上游阻塞；可缺省但需写明）

【输入线】 #330 状态栏静态三项 ──► #342 运行状态位（7 态） ──┐
           #329 空闲动效 ───────────────────────────────────┼──► #334 降级收口
           #332 斜杠命令（已实现，待核实） ──────────────────┘
           #340 色彩矩阵 ──► #336 / #337 / #338 ────────────┘

【工具线】 #322 分块框架（已实现，待核实） ──► #323 / #324 / #325 / #326
           #327 独立

【渲染线】 #339 独立（依赖已满足）
```

---

## 三、推荐实现顺序（5 批）

| 批次 | 内容 | 为什么在这个位置 |
|---|---|---|
| **批 1（约半天）** | ① 核实 **#332 / #322** 并关单（或补差额）；② **#269 文档 9 处旧名**同步；③ 决定 0.1.5 是否含 macOS 原生（#321） | 零成本清噪声；文档旧名与「0.1.5 移除旧名」直接冲突，必须先收 |
| **批 2（1–2 天）** | **#330** 状态栏静态三项（同时抽出渲染入口）；并行 **#329**；并行 **#340**（补降级映射与单测） | #330 是 #342 的明文前置；#340 是三个渲染子单的软前置；三者互不依赖 |
| **批 3** | **#342**（走 #330 入口）、**#336 / #337 / #338**（走 #340 档位）、**#331 占位**；**#339** 可提前到批 2 并行 | 依赖已解锁；#331 只做接口 + 占位，不被 0.6 卡住 |
| **批 4** | **#323 / #324 / #325 / #326**（#322 若已确认完成则直接并行）、**#327** | 工具线独立于输入/渲染线，可穿插做 |
| **批 5** | **#334** 三档降级 → **0.1.5 发版** → **#303** 验收 → **#269** 关单 | #334 依赖前四批；#303 的验收窗口只在发版后 |
| 持续 | **#75** 真机结论、**#321** 上游跟踪 | 依赖外部环境 / 上游，不占开发关键路径 |

**关键路径**（决定 0.1.5 何时能发）：`#269 文档收尾 → 0.1.5 发版 → #303 验收`。

**并行泳道**（不阻塞发版）：

- `#330 → #342 → #334`
- `#340 → #336 / #337 / #338`
- `#322 → #323-326`
- `#339`、`#327`

---

## 四、待拍板

| # | 问题 | 选项与代价 |
|---|---|---|
| 1 | **#322 的折叠口径** | 现有实现是「预览 5 行 + 中间省略」，验收写的是「超 500 行折叠」。**按现状关单**（改验收，零代码）或**补 500 行阈值**（改实现 + 边界单测 499/500/501） |
| 2 | **0.1.5 是否等 macOS 原生** | #321 仍被 GraalVM 25.5 GA 阻塞（工作流已提前改好，见 B 段）。若不等，需在 #269 / #303 中明确「本版缺 macOS 原生」，并确认发布链路会把它标成「不完整」（#303 的门禁已具备该能力） |

---

## 五、复核方式（每条判断都能重跑）

| 判断 | 复核命令 / 位置 |
|---|---|
| #332 已实现 | `grep -n "register(new" aha-cli/src/main/java/com/acanx/module/aha/cli/session/SessionCommandRegistry.java` |
| #322 框架已用 | `grep -rn "ToolBlock\\." aha-cli/src/main/java/com/acanx/module/aha/cli/render/StreamRenderer.java` |
| #330 / #329 未做 | 读 `render/StatusLine.java`（只有等待/工具阶段）、`tty/StatusSurface.java`（只有 NONE） |
| #340 探测已有、映射未做 | 读 `tty/TerminalCapabilities.java` 的 `ColorDepth` 与「深度仅决定是否着色」注释 |
| #269 文档旧名残留 | `grep -c "aha-desktop-<版本>-<平台>\\|AHA-Desktop-Native-"` 对 #269 列出的 9 个文件 |
| #303 门禁已落地 | `gh issue view 303` 的 8 个 `[x]` 勾选项 |

---

## 六、关联

- Milestone：[Milestone 8（V0.1.5）](https://github.com/ACANX/AHA/milestone/8)
- 母单：[#72](https://github.com/ACANX/AHA/issues/72)（工具调用渲染，已关闭）/ [#73](https://github.com/ACANX/AHA/issues/73)（渲染缺口，已关闭）/ [#74](https://github.com/ACANX/AHA/issues/74)（输入交互，已关闭）
- 发布线：[#269](https://github.com/ACANX/AHA/issues/269) / [#303](https://github.com/ACANX/AHA/issues/303) / [#321](https://github.com/ACANX/AHA/issues/321)
- 规范：`Docs/DevSpec/BuildSpec.md` §11、`Docs/DevSpec/ReleaseProcess.md` §3.5、`Docs/Guide/SnapshotSelfTestGuide.md`
