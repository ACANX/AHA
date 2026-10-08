# AHA 工具宪法

**文档版本**：v1.13.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-07
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.1.0 | 2026-10-06 | 新增 LLM 供应商适配条款 | @ACANX |
| v1.1.1 | 2026-10-06 | 修正 IR 独立性表述 | @ACANX |
| v1.2.0 | 2026-10-06 | 新增文档规范、AGENT 与技能体系 | @ACANX |
| v1.2.1 | 2026-10-06 | 新增 YAML 字段命名规范 | @ACANX |
| v1.3.0 | 2026-10-06 | 新增扩展架构条款 | @ACANX |
| v1.4.0 | 2026-10-06 | 技能命名改为 kebab-case，`Skill.md` 更名 `SKILL.md` 并强制 frontmatter | @ACANX |
| v1.5.0 | 2026-10-06 | 技能目录对齐 Agent Skills 规范（`references/` / `assets/`），补充 frontmatter 字段要求 | @ACANX |
| v1.6.0 | 2026-10-06 | 新增技能规范自动校验（`bin/CheckSkills.py`）与模板编译验证 | @ACANX |
| v1.7.0 | 2026-10-07 | 扩展机制统一为 Extension 命名：模块 `aha-extension-api`、包名、类名、配置段与描述符 | @ACANX |
| v1.8.0 | 2026-10-07 | 新增运行目录命名规范（`$AHA_HOME` 下按大驼峰单数命名） | @ACANX |
| v1.10.0 | 2026-10-07 | 日志文件名更正为 `AHA.log`；第 10 条新增第 8 项「日志文件名与切分命名」约定 | @ACANX |
| v1.11.0 | 2026-10-07 | 第 10 条第 5 项补「权威设计文档 → `Docs/AHA/`」 | @ACANX |
| v1.12.0 | 2026-10-07 | 第 5 条选型清单改为主版本线（Jackson 3.x），消除与父 POM 的版本漂移 | @ACANX |
| v1.13.0 | 2026-10-08 | JPMS 改为「优先启用（非强制）」、与 OpenJFX 冲突时让路（`C-01` 决策）；第 4 条补 `tool ← desktop` 边与规则（`D-09`）；第 5 条 GUI 选型改为「JavaFX 原生控件 + 进程内直调」（`D-07`） | @ACANX |

---

> 本部分为强制性规范。所有代码、配置、构建脚本、CI 流水线、文档必须符合本文件规定。违反本文件的贡献将被拒绝合入。

## 第 1 条：技术栈锁定

| 类别 | 锁定版本 | 说明 |
|---|---|---|
| JDK | **25 (LTS)** | 编译与运行目标，禁止降级 |
| Maven 运行时 | **4.x** | 仅作为构建运行时，通过 Maven Wrapper 固定 |
| Maven 兼容基线 | **3.9.x** | 所有 POM 修改必须通过 Maven 3.9.x 验证 |
| OpenJFX | **25** | 桌面端 UI 框架（0.1 不启用） |
| JPMS | **优先启用（非强制）** | 默认写 `module-info.java` 并走模块路径；与 OpenJFX 等需求冲突时可为它让路（`C-01` 决策，2026-10-08） |

**禁止事项**：

- 禁止在编译目标上使用低于 25 的 `release` 值
- 禁止在 POM 中使用 Maven 4 新增语法（自动模块发现、parent 版本推断、`modelVersion 4.1.0` 等）
- 非必要不使用 classpath 构建主代码：模块化是默认路径，仅在 JPMS 与 OpenJFX 等需求冲突
  且无法调和时例外，且必须在 `BuildSpec.md` 记明原因与影响面

## 第 2 条：命名体系

**顶级坐标**：

- 父 POM groupId: `com.acanx.module`
- 父 POM artifactId: `aha`

**子模块坐标**：

| 模块 | groupId | artifactId | JPMS 模块名 | 包前缀 |
|---|---|---|---|---|
| Common | `com.acanx.module.aha` | `aha-common` | `com.acanx.module.aha.common` | `com.acanx.module.aha.common` |
| ExtensionApi | `com.acanx.module.aha` | `aha-extension-api` | `com.acanx.module.aha.extension.api` | `com.acanx.module.aha.extension.api` |
| Core | `com.acanx.module.aha` | `aha-core` | `com.acanx.module.aha.core` | `com.acanx.module.aha.core` |
| Tools | `com.acanx.module.aha` | `aha-tool` | `com.acanx.module.aha.tool` | `com.acanx.module.aha.tool` |
| CLI | `com.acanx.module.aha` | `aha-cli` | `com.acanx.module.aha.cli` | `com.acanx.module.aha.cli` |
| Desktop | `com.acanx.module.aha` | `aha-desktop` | `com.acanx.module.aha.desktop` | `com.acanx.module.aha.desktop` |

**运行时命名**：

- CLI 命令：`aha`
- CLI 主类：`com.acanx.module.aha.cli.AhaCli`
- 桌面主类：`com.acanx.module.aha.desktop.AhaDesktopApp`
- 默认配置文件：`Aha.yaml`
- 默认配置：`AhaDefault.yaml`
- 模型配置文件：`Model.yml`（默认位于用户级目录：`$AHA_HOME/Model.yml` 或 `~/.aha/Model.yml`）
- 模型默认配置：`ModelDefault.yml`
- 扩展描述符：`AhaExtension.yaml`
- 环境变量前缀：`AHA_`
- SQLite 数据库：`Aha.db`
- 密钥库：`Aha.keystore`
- 日志文件：`AHA.log`

## 第 3 条：包结构冻结

```
com.acanx.module.aha.common.model
com.acanx.module.aha.common.tool
com.acanx.module.aha.common.event
com.acanx.module.aha.common.exception
com.acanx.module.aha.extension.api
com.acanx.module.aha.extension.api.event
com.acanx.module.aha.extension.api.point
com.acanx.module.aha.core.agent
com.acanx.module.aha.core.service
com.acanx.module.aha.core.memory
com.acanx.module.aha.core.config
com.acanx.module.aha.core.security
com.acanx.module.aha.core.mcp
com.acanx.module.aha.core.extension
com.acanx.module.aha.core.extension.loader
com.acanx.module.aha.core.extension.event
com.acanx.module.aha.core.llm
com.acanx.module.aha.core.llm.protocol
com.acanx.module.aha.core.llm.adapter
com.acanx.module.aha.core.llm.openai
com.acanx.module.aha.core.llm.anthropic
com.acanx.module.aha.core.llm.gemini
com.acanx.module.aha.core.llm.registry
com.acanx.module.aha.tool
com.acanx.module.aha.cli
com.acanx.module.aha.desktop
com.acanx.module.aha.desktop.controller
```

## 第 4 条：模块依赖方向

```
common ← extension-api ← core ← tool
common ← extension-api ← core ← cli
common ← extension-api ← core ← desktop
common ← extension-api ← core ← tool ← desktop   # 桌面端需要内置工具（D-09 决策，2026-10-08）
```

**硬性规则**：

- `common` 不得依赖任何其他 AHA 模块
- `extension-api` 仅依赖 `common`
- `core` 不得依赖 `tools`、`cli`、`desktop`
- `tool` 不得依赖 `cli`、`desktop`
- `cli` 和 `desktop` 之间不得互相依赖
- `desktop` 需要内置工具（file / http / shell）时依赖 `tool`；仍不得依赖 `cli`（`D-09` 决策，2026-10-08）
- `core` 不得引用 JavaFX、picocli、JLine
- 第三方扩展仅依赖 `extension-api` 和 `common`，不依赖 `core` 内部实现

## 第 5 条：技术选型清单

| 领域 | 选定方案 | 不可替换性 |
|---|---|---|
| CLI 框架 | picocli + JLine | 除非有重大安全或性能问题 |
| GUI | OpenJFX 25，**JavaFX 原生控件 + 进程内直调**（WebView + 本地 HTTP 为备选） | 除非有重大安全或性能问题 |
| JSON/YAML | Jackson 3.x（groupId `tools.jackson`） | 除非有重大安全或性能问题 |
| 日志 | SLF4J + Log4j2 | 可替换 Log4j2 后端 |
| HTTP 客户端 | JDK HttpClient 或 OkHttp | 二选一，由 Core 层适配器隔离 |
| 持久化 | SQLite (xerial) + Jackson | 除非有重大安全或性能问题 |
| DI | 手动工厂 + ServiceLoader + ExtensionRuntime | 禁止引入 Spring/Guice |
| 并发 | 虚拟线程 + 自定义 CancellationToken | 结构化并发仅限预览评估 |
| 工具协议 | 自定义 Tool 接口 + MCP 适配器预留 | 内部协议不可更换 |
| LLM 协议适配 | 内部 IR + 适配器 SPI | 不可更换 |
| 扩展运行时 | ModuleLayer + ExtensionRuntime | 不可更换 |
| 安全存储 | 加密文件 + 主密码 | 预留系统密钥链适配器 |
| 测试 | JUnit 6 + AssertJ + Mockito | 不可更换 |

## 第 6 条：编码规范

- 禁止使用 `System.out` / `System.err`（CLI 适配层除外）
- 禁止在 Core 中直接使用 `System.exit()`
- 所有公开 API 必须有 Javadoc（`@since`、`@param`、`@return`）
- `module-info.java` 必须显式导出包
- `opens` 必须限定到具体模块
- 异常必须被处理或包装为 `AhaException`
- 虚拟线程不得被池化
- IR 字段通过 `@JsonProperty` 显式映射 PascalCase（内部代码使用 Java 命名规范）

## 第 7 条：构建约束

- POM 语法兼容 Maven 3.9.x
- Maven Wrapper 固定 Maven 4 运行时
- 双版本验证：CI 必须同时跑 Maven 4 和 Maven 3.9.x
- `mvn clean verify` 是唯一验收标准

## 第 8 条：宪法修订程序

1. 提出修订提案（GitHub Issue，标签 `constitution`）
2. 说明修订理由、影响范围、迁移方案
3. 至少一名核心维护者批准
4. 更新本文件版本号并记录变更日志
5. 修订后的宪法立即生效

## 第 9 条：LLM 供应商适配

### 9.1 IR 独立性

- IR 是 AHA 自定义的独立标准，拥有独立的命名空间、序列化格式和版本号
- IR 的设计参考 OpenAI Chat Completions、Anthropic Messages、Gemini generateContent 的共同抽象，但不等同于其中任何一个
- IR 的字段名、类型名、枚举值由 AHA 定义，不继承任何供应商的命名
- IR 有独立的版本号，演进节奏由 AHA 控制，不因任何供应商的 API 变更而触发

### 9.2 适配器契约

- 适配器是 IR 与外部协议之间的唯一桥梁
- 适配器不得反向影响 IR 的设计
- 供应商特有参数通过 IR 的 `extensions` 字段透传
- IR 需要新语义时，通过版本演进添加

### 9.3 OpenAI 兼容适配器

- 内置适配器，处理所有 OpenAI 兼容端点
- 内置 DeepSeek、智谱 GLM、通义千问（Qwen）等国产大模型默认预设
- 内置 sub2api、new-api（newapi）等中转站的兼容处理
- 兼容供应商通过配置接入，无需编写代码
- 转换逻辑是显式转换，不是直接序列化

## 第 10 条：文档规范

1. **命名规范**：所有 Markdown、SVG、图片文件采用**大驼峰（PascalCase）** 命名
2. **例外**：`README.md`、`AGENTS.md`、`LICENSE`、`CHANGELOG.md`、`CONTRIBUTING.md` 等业界通用文件名保留原样
3. **YAML 字段命名**：所有 AHA 项目自有的 YAML 文件，其字段名（键名）统一采用大驼峰（PascalCase）命名。字段值保持原样，不受此规范约束。第三方框架（如 Log4j2、GitHub Actions、Docker Compose）的 YAML schema 字段遵循第三方规范
4. **运行目录命名**：`$AHA_HOME`（默认 `~/.aha`）下的数据目录采用**大驼峰（PascalCase）单数**命名 —— `Data/`（数据库）、`Key/`（密钥库）、`Log/`（日志）、`Extension/`（扩展）
5. **文档存放**：
   - 开发规范 → `Docs/DevSpec/`
   - 权威设计文档（`AHA-Design-V1.md`）→ `Docs/AHA/`
   - 设计文档 → `Docs/Design/`
   - 用户指南 → `Docs/Guide/`
   - 图资源 → `Docs/Diagrams/`
6. **文档头部**：每个文档必须有版本号、状态、生效日期、变更日志
7. **更新制度**：代码变更导致接口、配置、行为变化时，同一 PR 内更新相关文档
8. **日志文件命名**：主文件 `AHA.log`；单文件超 10 MB 切分为同目录的 `<基名>-yyyy-MM-dd-NN.log`
   （`NN` 为补零两位序号，按天从 `01` 递增），切分文件不压缩。
   装配方式与取舍见 [LoggingDesign.md](../Design/LoggingDesign.md)

**YAML 字段命名边界规则**：

- 环境变量引用保持 `${AHA_XXX}` 全大写下划线形式
- 枚举值、标识符、工具名、扩展 ID 保持与代码一致
- 第三方框架的 YAML schema 字段不强制修改
- `Extension.Settings` 下的扩展 ID 键不强制 PascalCase
- `Model.yml` / 主配置 `Llm.Providers` 下的供应商 ID 键建议但不强制 PascalCase
- 数据库（SQLite）表名（单数）与字段名使用 snake_case（如 `gmt_create`），不受本规范约束

## 第 11 条：AGENT 与技能体系

- AGENT 入口文件为根目录 `AGENTS.md`
- 所有技能统一存放于 `.agents/skills/` 目录
- 每个技能为一个独立目录，包含 `SKILL.md`，以及可选的 `references/`（参考文档）、`assets/`（模板与数据）、`scripts/`（可执行脚本）子目录
- 技能目录名与 `SKILL.md` frontmatter 中的 `name` 均采用 kebab-case（小写字母 + 连字符），二者必须一致；技能目录内的 Markdown 资源同样使用 kebab-case
- `SKILL.md` 必须包含 `name` 与 `description` frontmatter，否则不会被 Agent 加载；建议补充 `license`、`compatibility`、`metadata`
- 新增技能必须更新 `AGENTS.md` 的技能索引
- 技能规范由 `bin/CheckSkills.py` 自动校验（CI `docs` job）；技能内 Java 模板由 CI `build` job 编译验证

## 第 12 条：文档更新制度

- 开发规范变更后，必须在 **3 个工作日内** 同步更新 `Docs/DevSpec/`
- 每个 minor release 前，必须审查并更新 `Docs/DevSpec/` 全部文档
- 文档变更与代码变更在同一个 PR 中提交

## 第 13 条：扩展架构

- AHA 采用"微内核 + ModuleLayer + 事件总线"的扩展架构
- 新增 `aha-extension-api` 模块，定义扩展契约
- 扩展通过 `AhaExtension.yaml` 描述
- 扩展注册的所有资源必须通过 `Registration` 追踪，卸载时自动回滚
- 扩展点通过类型化事件暴露，事件分发模式包括 `EMIT` / `WATERFALL` / `SERIAL` / `PARALLEL`
- 扩展运行在独立的 `ModuleLayer` 中，类加载隔离
- 扩展权限在安装时由用户确认
- 核心稳定，边缘扩展化：`AgentEngine` 在 1.0.0 前不开放为扩展点
