# 文档规范

**文档版本**：v1.26.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-17
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.0.0 | 2026-10-06 | 初始版本 | @username |
| v1.1.0 | 2026-10-06 | 明确 DevSpec 规范类文档以 `Spec.md` 结尾 | @ACANX |
| v1.2.0 | 2026-10-06 | 增加「规范与操作分离」原则（`BuildSpec.md` ↔ `BuildGuide.md`） | @ACANX |
| v1.3.0 | 2026-10-06 | 链接检查脚本化（`bin/CheckDocs.py`），补充嵌套代码块约定 | @ACANX |
| v1.4.0 | 2026-10-06 | 技能目录改为 kebab-case，登记 `SKILL.md` 为保留名 | @ACANX |
| v1.5.0 | 2026-10-06 | 技能目录结构对齐 Agent Skills 规范，技能内 Markdown 资源豁免 PascalCase | @ACANX |
| v1.6.0 | 2026-10-06 | 新增技能规范校验脚本 `bin/CheckSkills.py` 并接入 CI | @ACANX |
| v1.7.0 | 2026-10-06 | 新增跨平台脚本规约（`.bat` 纯 ASCII + CRLF）与 `bin/CheckScripts.py` | @ACANX |
| v1.8.0 | 2026-10-07 | 第 1 节补首字母缩写命名规则（`TUIDesign.md` 而非 `TuiDesign.md`） | @ACANX |
| v1.9.0 | 2026-10-07 | 第 1 节补变更日志排序约定（升序） | @ACANX |
| v1.11.0 | 2026-10-07 | 分类表新增「权威设计文档（`AHA-Design-V1.md`）→ `Docs/AHA/`」 | @ACANX |
| v1.12.0 | 2026-10-07 | §1 新增开发日志命名例外（`DevLog-YYYYmmdd-HH.md`）；§2 分类表补「开发日志 → `Docs/DevLog/`」；§4 新增开发日志的必备小节与文件头约定，并修正三项检查「由 `Build.yml` 执行」的失效说法（已移入 `Gate.yml`） | @ACANX |
| v1.13.0 | 2026-10-07 | §1 修正开发日志的命名规则：同一小时内的第二件独立事项不再用 `-2` 后缀，改为顺延 `HH`（如 21 时的第二件记为 `DevLog-20261007-22.md`），保证目录内只有一种文件名形态 | @ACANX |
| v1.14.0 | 2026-10-09 | §4 新增「遗留事项的跟踪载体」：未闭环问题 / 待决策项 / 需人工执行的事项一律用 GitHub Issue 跟踪，`Docs/TODO.md` 冻结为历史记录（不再追加内容） | @ACANX / CNXNC |
| v1.15.0 | 2026-10-09 | 新增两个目录：`Docs/Troubleshooting/`（问题排查与事故复盘，命名 `TS-yyyyMM-大驼峰英文标题.md`）与 `Docs/TODO/`（待办清单看板）；`Docs/DevLog/` 重新定位为**开发完成记录**，排查过程改入 Troubleshooting；§1 / §2 / §4 同步，并新增两份模板 | @ACANX / CNXNC |
| v1.16.0 | 2026-10-09 | §1 新增待办详情命名 `TD-PPPPP-大驼峰英文标题.md`（五位数字从 `00001` 全局自增）；§2 / §4 同步：`Docs/TODO/README.md` 为清单索引，一个待办一个 TD 文件 | @ACANX / CNXNC |
| v1.17.0 | 2026-10-09 | §4 开发日志的必备小节对齐现行模板（任务概述 / 完成内容 / 技术要点 / 修改文件清单 / 验证 / 遇到的问题 / 待办事项 / 工时统计）；`Docs/DevLog/` 新增规范与模板 | @ACANX / CNXNC |
| v1.18.0 | 2026-10-09 | 开发日志命名由 `DevLog-YYYYmmdd-HH.md` 改为 **`yyyyMMdd-HH.md`**（与参考格式一致）；`Docs/DevLog/` 下 31 篇排查记录**全部 1:1 迁移**到 `Docs/Troubleshooting/` 并改名 `TS-yyyyMM-PascalCaseTitle.md`；全仓引用同步；模板改由 `Docs/DevLog/README.md` 承载 | @ACANX / CNXNC |
| v1.19.0 | 2026-10-09 | §1 / §2 / §4 登记 **`Archive/`**（已废弃历史资料的归档目录）：文件名保持原样、头部注明「已废弃」、`git mv` 保留 Git 历史 | @ACANX / CNXNC |
| v1.20.0 | 2026-10-09 | §4 补登 `bin/CheckDocs.py` 的检查项：新增**目录命名规范检查**（`TD-PPPPP-*` / `TS-yyyyMM-*` / `yyyyMMdd-HH`）与 `TD-` 编号重复检查；明确 `Archive/` 不在扫描范围内 | @ACANX / CNXNC |
| v1.21.0 | 2026-10-09 | §4 新增**记录纪律（强制）**：代码与记录同变更内完成；三类记录（待办 / 开发日志 / 排查记录）的时机与写法统一由 `AGENTS.md`「文档记录去向」与技能 `doc-recording` 承接 | @ACANX / CNXNC |
| v1.22.0 | 2026-10-13 | §1 问题排查记录命名由 `TS-yyyyMM-大驼峰标题.md` 改为 **`TS-yyMM-NNN-英文词序.md`**（年月 + 三位顺序号 + 逐词连字符标题，对齐 LegionFanControl 的 `TroubleShootingDocSpec.md`）；`Docs/Troubleshooting/` 下 31 篇档案**全部改名并重编序号**（`001`~`031`，按原索引登记顺序），全仓引用与 H1 编号同步；`bin/CheckDocs.py` 命名正则同步 | @ACANX / CNXNC |
| v1.23.0 | 2026-10-13 | 新增 **§5「TS 档案正文骨架」**：明确四节必备项（结论分层 / 观测数据 / 排除项 / 未决问题）的写法与「补入块」形式；`TS-Template.md` 与 32 篇档案同步补齐；`bin/CheckDocs.py` 新增对应的机器检查（issue #128） | @ACANX / CNXNC |
| v1.23.1 | 2026-10-13 | §4「问题排查记录」一条**加指向 §5 的引用**：issue #128 的验收标准按当时写法点名「写入 §4」，而 §4 已被「更新制度」占用且被 `AGENTS.md` / `IssueTrackingSpec.md` 引用，**不得重编号** ⇒ 在 §4 留下入口，避免读者按 §4 去找而找不到（issue #128） | @ACANX / CNXNC |
| v1.25.0 | 2026-10-17 | 新增 §6「清单与映射的书写体例（强制）」：一行一项，禁用 `、` / `/` / `；` 串联（issue [#287](https://github.com/ACANX/AHA/issues/287)） | @cnxnc |
| v1.24.0 | 2026-10-14 | §2 末尾加指向 `Docs/Guide/PlanWritingGuide.md` 的引用（方案类文档体例参考，非强制） | @cnxnc |

---

## 1. 文件命名

- Markdown、SVG、图片统一采用**大驼峰（PascalCase）**
- `Docs/DevSpec/` 下的规范约束类文档统一以 `Spec.md` 结尾（如 `TestingSpec.md`、`YamlFieldSpec.md`、`CommitMessageSpec.md`、`DocumentationSpec.md`、`BuildSpec.md`）；禁止使用 `Rule.md` 结尾
- 同一主题的「规范」与「操作」拆分存放：规范进 `Docs/DevSpec/`（`Spec.md`），操作进 `Docs/Guide/`（`Guide.md`）。例：`BuildSpec.md`（构建要求）↔ `BuildGuide.md`（构建步骤）
- 变更日志**升序**排列（旧的在上，新版本追加在表末）
- 首字母缩写**整体大写**，不与后面的词拼接成小驼峰：`TUIDesign.md`、`GUIDesign.md`、`CLIDesign.md`；反例 `TuiDesign.md`、`GuiDesign.md`、`CliDesign.md`
- 业界通用名保留原样：`README.md`、`AGENTS.md`、`SKILL.md`、`LICENSE`、`CHANGELOG.md`、`CONTRIBUTING.md`、`CODE_OF_CONDUCT.md`、`SECURITY.md`
- `.agents/skills/` 下的技能目录与 `SKILL.md` frontmatter 的 `name` 采用 **kebab-case**（小写字母 + 连字符），二者一致；技能目录内的 Markdown 资源（`references/` 下）同样使用 kebab-case，不受本文档 PascalCase 规则约束
- **开发日志**（`Docs/DevLog/`）按 **`yyyyMMdd-HH.md`** 命名：`yyyyMMdd` 为事件日期，`HH` 为 24 小时制的**小时**（本地时间，取事件处理时点）。这是为「按时间线排序、一眼看出何时发生」而设的**显式例外**。同一小时内若确有第二件独立事项，**把 `HH` 顺延一位**（21 时的第二件记为 `20261007-22.md`），**不得加 `-2` 之类的后缀**——同一目录里只保留一种文件名形态，纯字符串排序即时间线顺序；当日编号单调递增，不回头复用。
- **开发日志规范与模板**：`Docs/DevLog/README.md`（目录内除它以外，只应有 `yyyyMMdd-HH.md` 形态的文件）。
- **问题排查记录**（`Docs/Troubleshooting/`）按 **`TS-yyMM-NNN-英文词序.md`** 命名：`TS` 为固定前缀，`yyMM` 为事件发生的年月（`2610` = 2026 年 10 月），`NNN` 为同期间内的**三位顺序号**（按登记时间递增，从 `001` 起，不复用、不跳号），标题为**英文词序**且**逐词用 `-` 连接**（如 `CI-Required-Check-Name-Mismatch`）；缩写（`CI` / `PR` / `JNI` / `CSS` / `JVM` / `LCD` …）整体大写，产品名按原厂写法（如 `GraalVM`、`JavaFX`），不用下划线、不连写、不出中文。模板为 `Docs/Troubleshooting/TS-Template.md`（同样是显式例外）。
- **已完成待办**（`Docs/DONE/`）按 **`DN-PPPPP-大驼峰英文标题.md`** 命名：`DN` 为固定前缀（Done），`PPPPP` 与归档前的 `TD-` **完全相同**（编号不变、不复用）；标题不变。详见 §7 的归档 SOP。
- **待办详情**（`Docs/TODO/`）按 **`TD-PPPPP-大驼峰英文标题.md`** 命名：`TD` 为固定前缀，`PPPPP` 为**五位数字，从 `00001` 开始全局自增**（不复用、不跳号），标题为英文大驼峰。`Docs/TODO/README.md` 是清单索引，为目录固定的保留名，**不走**该规则；新文件取当前最大编号 + 1。
- **归档资料**（`Archive/`）：文件名**保持原样**（不强制改名），但必须在文件头部注明「**已废弃（YYYY-MM-DD）**」与替代物（如有）；`Archive/README.md` 为目录说明，按保留名处理。
- 反例（禁止）：`agent-service-design.md`、`agent_service_design.md`、`AgentServiceDesign.MD`、`module-architecture.svg`、`TestingRule.md`、`Docs/DevSpec/BuildGuide.md`（构建规范应置于 DevSpec 并以 `Spec.md` 结尾）

## 2. 文档分类与存放

| 文档类型 | 存放位置 |
|---|---|
| 宪法 | `Docs/DevSpec/Constitution.md` |
| 开发规范 | `Docs/DevSpec/` |
| **权威设计文档**（`AHA-Design-V1.md`） | `Docs/AHA/` |
| 设计文档 | `Docs/Design/` |
| 用户指南 | `Docs/Guide/` |
| 图资源 | `Docs/Diagrams/` |
| 开发日志（开发完成记录） | `Docs/DevLog/` |
| 问题排查与事故复盘 | `Docs/Troubleshooting/` |
| 待办清单（`README.md`）与待办详情（`TD-*.md`） | `Docs/TODO/` |
| 已废弃的历史资料（归档，不再维护） | `Archive/` |
| AGENT 技能 | `.agents/skills/` |

> **方案类文档**（较大方案变更 / 新特性）的体例与写法参考：
> [`Docs/Guide/PlanWritingGuide.md`](../Guide/PlanWritingGuide.md)（**参考性，非强制**；命名与骨架仍以本规范为准）。

## 3. 文档头部

```markdown
# 文档标题

**文档版本**：v1.0.1
**状态**：草稿 / 评审中 / 冻结 / 已废弃
**生效日期**：2026-10-06
**最后更新**：2026-10-06
**负责人**：@username
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
```

## 4. 更新制度

- 代码变更导致接口、配置、行为变化时，同一 PR 内更新相关文档
- 每个 minor release 前审查 `Docs/DevSpec/` 全部文档
- 文档不再适用时标记为"已废弃"，不直接删除
- **开发日志**（`Docs/DevLog/`）：记录**已完成的事项**。格式对齐现行模板，必备小节：
  **📋 任务概述**、**✅ 完成内容**、**🔧 技术要点**、**📄 修改文件清单**、**🧪 验证**、
  **⚠️ 遇到的问题**（只写结论）、**📝 待办事项**、**🕐 工时统计**。文件头用「关联 PR / Issue、
  关联记录、排查记录」引用块，**不套用第 3 节的版本头**——日志是时间线记录，不按版本演进。
  **不写排查过程**（排查进 `Docs/Troubleshooting/`），需要时引用其文件名或 Issue 编号。
  模板见 `Docs/DevLog/README.md`。
- **问题排查记录**（`Docs/Troubleshooting/`）：记录**问题排查 / 缺陷定位 / 事故复盘**——
  现象、排查过程（含取证：「要求」与「实际」逐条对照）、根因、修复、验证、教训、涉及文件。
  命名见第 1 节；文件头同样用引用块而非版本头；模板见 `Docs/Troubleshooting/TS-Template.md`。
  排查中牵出的未闭环遗留项**另开 Issue**，记录里只引用编号。
  **正文骨架（四节必备项）见 §5**——本节只讲「什么时候记、记去哪儿」，不重复骨架要求。
- **待办清单**（`Docs/TODO/README.md`）：待办事项的汇总清单与进度看板。新增待办时建一个
  `TD-PPPPP-大驼峰英文标题.md`（取当前最大编号 + 1），并在清单加一行、登记跟踪 Issue 编号；
  有进展或闭环时**同一变更内**更新 TD 文件的头部状态与清单该行及文件头的「最后更新」；
  已完成的行在备注里写清**依据**（CI 运行编号 / PR / 实测）。TD 文件的内容格式不做强制要求，
  建议参考对应 Issue 的结构（来源 / 现状 / 需求或待拍板 / 验收标准 / 关联）。
  （`Docs/TODO.md` / `Docs/PLAN.md` / `Docs/Dbsx.txt` 已于 2026-10-17 删除。）
- **记录纪律（强制）**：代码改动与对应记录（`Docs/TODO/` / `Docs/DevLog/` / `Docs/Troubleshooting/`）
  **在同一变更内完成**，不得「下次补」；待办不得只留在对话里；排查过程与开发日志不混用。
  三类记录的时机、写法与收尾自检见 `AGENTS.md` 的「文档记录去向」与
  技能 `.agents/skills/doc-recording/SKILL.md`。
- **归档资料**（`Archive/`）：不再维护的历史资料放这里（已废弃文档、旧设计稿、被取代的脚本与配置）。
  移入时用 `git mv` **保留 Git 历史**；文件头部注明「已废弃（YYYY-MM-DD）」与替代物（如有）。
  放入本目录即表示**不再维护**；需要恢复时从 Git 历史取回，不要直接重建副本。
- 下列检查由 CI 执行：`python3 bin/CheckDocs.py`（Markdown 相对链接有效性、代码围栏闭合、
  残留合并冲突标记、以及 `Docs/TODO` / `Docs/Troubleshooting` / `Docs/DevLog` 的**目录命名规范**）、
  `python3 bin/CheckSkills.py`（技能是否符合 Agent Skills 规范：目录结构、frontmatter、命名、内链）、
  `python3 bin/CheckScripts.py`（跨平台脚本规约）。**它们已随门禁分层移入 `Gate.yml`**
  （合入 `main` 前、每周定期、发布前），不再随每次 `push` 执行；技能内的 Java 模板仍由
  `Build.yml` 的 `build` job 编译验证（见 `BuildSpec.md` §8.1）
  （`Archive/` **不在**扫描范围内：归档资料保持原名、不再维护）
- **遗留事项的跟踪载体**：未闭环的问题、待决策项、需人工 / 平台执行的事项，一律用
  **GitHub Issue** 跟踪（写明来源、现象、背景、待排查方向与**验收标准**），
  **不得写入已删除的暂存区文件**（`Docs/TODO.md` / `Docs/PLAN.md` / `Docs/Dbsx.txt`，2026-10-17 删除：
  已完成/不做/过时的内容见 `Docs/DevLog/20261017-15.md`，剩余任务见 `Docs/TODO/` 的 `TD-*`）；
  「做不到 / 已暂缓」的**结论与依据**写进对应 `TD-*` 的「结论 / 已排除方案」小节，不追加待办。
  判定、写法与命令见技能 `.agents/skills/issue-tracking/SKILL.md`。

### 跨平台脚本编码（强制）

| 类型 | 编码 | 行尾 | 原因 |
|---|---|---|---|
| `*.bat` / `*.cmd` | **纯 ASCII** | **CRLF** | CMD 按 ANSI 代码页解析批处理：UTF-8 中文字节会被误读并产生 `&`、`|` 等元字符，使 `rem` / `echo` 行被当作命令执行；LF-only 批处理的 `if (...)` 块解析不可靠 |
| `*.sh` | UTF-8 | LF | CRLF 会让 shebang 因 `\r` 失效，报 `bad interpreter` |
| `*.py` | UTF-8 | LF | 同上 |

因此 **`.bat` 中的用户提示一律使用英文**，中文说明放到 `README.md` / `Docs/Guide/`。
行尾由 `.gitattributes` 声明并由 `bin/CheckScripts.py` 在 CI 中校验。
- 示例代码块内含三反引号围栏时，外层必须使用四个反引号包裹，
  否则围栏提前闭合会导致后续正文被渲染为代码

---

## 5. TS 档案正文骨架

**适用对象**：`Docs/Troubleshooting/TS-*.md`（含模板 `TS-Template.md`）。

完整的 12 节骨架见姊妹项目
[`TroubleShootingDocSpec.md`](https://github.com/acnxo/LegionFanControl/blob/dev/Docs/DevSpec/TroubleShootingDocSpec.md) §2.1。
本仓库的现实是：`TS-2610-001` ~ `TS-2610-032` 沿用**自有五段式**（背景 / 排障过程与修复链 /
最终验证结果 / 关键教训 / 涉及文件清单）。**不回溯改写**——逐篇重写要重走一遍「事实 / 机制 /
推测」的甄别，改动面大而失真风险高；但**四节必备项一律不得缺**，以「补入块」形式追加。

| 章节 | 要求 | 为什么必备 |
| --- | --- | --- |
| `## 结论分层` | 分三层写：`### 已观测事实`（可复现的数字 / 日志 / 命令输出）、`### 机制 / 行为模型`（由事实推出的因果链；**未实测的推断要标注「属推断」**）、`### 未定位 / 未复现`（明确写「尚未定位」「本机无该环境」「未验证」） | 防止后人把**推测**当**结论**读 |
| `## 观测数据` | 表格给出「数据 / 来源或命令 / 原始输出」；**数字优先**；有坏样本与好样本就并列；没保留原始输出就**如实写「原档案未保留」** | 让结论**可复核**，并如实标出证据等级 |
| `## 排除项` | 表格：已排除的可能 + 依据 | 避免后人重走一遍已排除的弯路 |
| `## 未决问题` | `Q1`、`Q2`…，每条写清**现状**与**阻塞点** | 让「没做完」显式留痕，而不是被读成「已解决」 |

规则：

- 四节**按上表顺序**追加在正文末尾；**不带章序号**，以免与各档案既有的
  「一、二、…」或「1. …」编号冲突；
- 补入块以一行 HTML 注释开头，写明来源（如「按 issue #128『选项 C：补齐四节必备项』补入」），
  便于与原文区分；
- 新增档案同样适用：`TS-Template.md` 已带上这四节；
- **机器强制**：`bin/CheckDocs.py` 的 `[TS 档案必备章节检查]` 逐篇断言这四节存在，
  且「结论分层」必须有那三个子节；不合规即失败（写入规范而无人执行，等于没写）。

---

## 6. 清单与映射的书写体例（强制）

> 来源：ACANX 2026-10-17 在 issue [#287](https://github.com/ACANX/AHA/issues/287) 上的明确要求。

### 6.1 一行一项

- **清单 / 映射 / 待办一律「一行一项」**；
- **禁止**用 `、`、`/`、`；` 把多个条目串在一行；
- 表格同理：**一行只放一个条目**，不要把「A / B / C」塞进同一格。

### 6.2 为什么

| 原因 | 具体后果 |
|---|---|
| 无法单独勾选 | `- [ ]` 只认整行，挤在一行时只能整体勾选 |
| 无法单独核对 | 逐项对照时要人脑拆分，漏改一项不会被发现 |
| diff 不可读 | 一项改动会让整行重写，review 看不见「改了哪一项」 |
| 无法机械检查 | 守卫只能按行断言，串行写法必须写正则去拆 |

### 6.3 适用对象

| 对象 | 例子 |
|---|---|
| 映射表 | `旧名 → 新名`、`文件 → 规则` |
| 待办清单 | `TD-*` 的验收项、issue 的验收标准 |
| 命令与路径清单 | 「逐文件核对」类清单 |
| 例外清单 | 「只允许出现在以下位置」 |

### 6.4 示例

反例（禁止）：

```markdown
- 矩阵 label 4 处：`DesktopNative.yml` / `CliNative.yml` / `BuildJVMArtifacts.yml` / `Release.yml`
```

正例：

```markdown
- 矩阵 `label`（4 处，一行一项）：
  - `DesktopNative.yml`
  - `CliNative.yml`
  - `BuildJVMArtifacts.yml`
  - `Release.yml`
```

## 7. 待办的生命周期

待办的创建、跟踪、**完成后的归档**（`Docs/TODO/TD-…` → `Docs/DONE/DN-…`，目录变、编号不变）
统一见 [TodoLifecycleSpec.md](TodoLifecycleSpec.md)；本文档只约束文件名形态与存放目录。
