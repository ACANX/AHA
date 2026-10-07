# 文档规范

**文档版本**：v1.11.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-06
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

---

## 1. 文件命名

- Markdown、SVG、图片统一采用**大驼峰（PascalCase）**
- `Docs/DevSpec/` 下的规范约束类文档统一以 `Spec.md` 结尾（如 `TestingSpec.md`、`YamlFieldSpec.md`、`CommitMessageSpec.md`、`DocumentationSpec.md`、`BuildSpec.md`）；禁止使用 `Rule.md` 结尾
- 同一主题的「规范」与「操作」拆分存放：规范进 `Docs/DevSpec/`（`Spec.md`），操作进 `Docs/Guide/`（`Guide.md`）。例：`BuildSpec.md`（构建要求）↔ `BuildGuide.md`（构建步骤）
- 变更日志**升序**排列（旧的在上，新版本追加在表末）
- 首字母缩写**整体大写**，不与后面的词拼接成小驼峰：`TUIDesign.md`、`GUIDesign.md`、`CLIDesign.md`；反例 `TuiDesign.md`、`GuiDesign.md`、`CliDesign.md`
- 业界通用名保留原样：`README.md`、`AGENTS.md`、`SKILL.md`、`LICENSE`、`CHANGELOG.md`、`CONTRIBUTING.md`、`CODE_OF_CONDUCT.md`、`SECURITY.md`
- `.agents/skills/` 下的技能目录与 `SKILL.md` frontmatter 的 `name` 采用 **kebab-case**（小写字母 + 连字符），二者一致；技能目录内的 Markdown 资源（`references/` 下）同样使用 kebab-case，不受本文档 PascalCase 规则约束
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
- CI 中执行 `python3 bin/CheckDocs.py`（`Build.yml` 的 `docs` job），检查 Markdown 相对链接有效性与代码围栏闭合
- CI 中执行 `python3 bin/CheckSkills.py`，检查技能是否符合 Agent Skills 规范（目录结构、frontmatter、命名、内链）；技能内的 Java 模板另由 `build` job 编译验证
- CI 中执行 `python3 bin/CheckScripts.py`，检查跨平台脚本规约

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
