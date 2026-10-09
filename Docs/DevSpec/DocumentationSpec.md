# 文档规范

**文档版本**：v1.18.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-09
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
- **问题排查记录**（`Docs/Troubleshooting/`）按 **`TS-yyyyMM-大驼峰英文标题.md`** 命名：`TS` 为固定前缀，`yyyyMM` 为事件发生的年月，标题为概括问题的**英文大驼峰**（2~5 个词，如 `CiRequiredCheckHang`）；同月多篇按标题字母序排列，**不加序号**。模板为 `Docs/Troubleshooting/TS-Template.md`（同样是显式例外）。
- **待办详情**（`Docs/TODO/`）按 **`TD-PPPPP-大驼峰英文标题.md`** 命名：`TD` 为固定前缀，`PPPPP` 为**五位数字，从 `00001` 开始全局自增**（不复用、不跳号），标题为英文大驼峰。`Docs/TODO/README.md` 是清单索引，为目录固定的保留名，**不走**该规则；新文件取当前最大编号 + 1。
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
| AGENT 技能 | `.agents/skills/` |

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
- **待办清单**（`Docs/TODO/README.md`）：待办事项的汇总清单与进度看板。新增待办时建一个
  `TD-PPPPP-大驼峰英文标题.md`（取当前最大编号 + 1），并在清单加一行、登记跟踪 Issue 编号；
  有进展或闭环时**同一变更内**更新 TD 文件的头部状态与清单该行及文件头的「最后更新」；
  已完成的行在备注里写清**依据**（CI 运行编号 / PR / 实测）。TD 文件的内容格式不做强制要求，
  建议参考对应 Issue 的结构（来源 / 现状 / 需求或待拍板 / 验收标准 / 关联）。
  （`Docs/TODO.md` 为历史文件，已冻结，不再更新。）
- 下列检查由 CI 执行：`python3 bin/CheckDocs.py`（Markdown 相对链接有效性与代码围栏闭合）、
  `python3 bin/CheckSkills.py`（技能是否符合 Agent Skills 规范：目录结构、frontmatter、命名、内链）、
  `python3 bin/CheckScripts.py`（跨平台脚本规约）。**它们已随门禁分层移入 `Gate.yml`**
  （合入 `main` 前、每周定期、发布前），不再随每次 `push` 执行；技能内的 Java 模板仍由
  `Build.yml` 的 `build` job 编译验证（见 `BuildSpec.md` §8.1）
- **遗留事项的跟踪载体**：未闭环的问题、待决策项、需人工 / 平台执行的事项，一律用
  **GitHub Issue** 跟踪（写明来源、现象、背景、待排查方向与**验收标准**），
  **不得写入 `Docs/TODO.md`** —— 该文件已冻结为历史记录，仅作追溯，不再追加内容。
  `Docs/PLAN.md` 只追加「做不到 / 已暂缓」的**结论与依据**，不追加待办。
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
