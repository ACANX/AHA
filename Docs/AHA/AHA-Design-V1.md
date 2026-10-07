# AHA 设计蓝图与技术实现方案

**文档版本**：v3.56.0
**状态**：冻结
**生效日期**：2026-10-06
**适用宪法版本**：v1.6.0
**目标版本**：AHA 0.1.0
**文档命名规范**：Markdown、SVG、图片统一大驼峰（PascalCase）；`.agents/skills/` 下技能目录及 `SKILL.md` 的 `name` 采用 kebab-case
**YAML 字段命名规范**：AHA 自有字段统一大驼峰（PascalCase）
**SQL 字段命名规范**：SQLite 表名（单数）与字段名统一 snake_case

---

# 目录

1. [工具宪法 v1.6.0](#第一部分工具宪法-v160)
2. [文档规范](#第二部分文档规范)
3. [AGENT 入口与技能体系](#第三部分agent-入口与技能体系)
4. [项目结构](#第四部分项目结构)
5. [模块设计](#第五部分模块设计)
6. [LLM 供应商适配体系](#第六部分llm-供应商适配体系)
7. [Agent 核心设计](#第七部分agent-核心设计)
8. [工具系统](#第八部分工具系统)
9. [配置与安全](#第九部分配置与安全)
10. [扩展化扩展体系](#第十部分扩展化扩展体系)
11. [构建、打包与 CI/CD](#第十一部分构建打包与-cicd)
12. [测试策略](#第十二部分测试策略)
13. [开发规范](#第十三部分开发规范)
14. [开发任务清单](#第十四部分开发任务清单)
15. [版本路线图与风险](#第十五部分版本路线图与风险)
16. [附录](#附录)

---

# 第一部分：工具宪法 v1.6.0

> 本部分为强制性规范。所有代码、配置、构建脚本、CI 流水线、文档必须符合本文件规定。违反本文件的贡献将被拒绝合入。

## 第 1 条：技术栈锁定

| 类别 | 锁定版本 | 说明 |
|---|---|---|
| JDK | **25 (LTS)** | 编译与运行目标，禁止降级 |
| Maven 运行时 | **4.x** | 仅作为构建运行时，通过 Maven Wrapper 固定 |
| Maven 兼容基线 | **3.9.x** | 所有 POM 修改必须通过 Maven 3.9.x 验证 |
| OpenJFX | **25** | 桌面端 UI 框架（0.1 不启用） |
| JPMS | **强制启用** | 所有模块必须有 `module-info.java` |

**禁止事项**：

- 禁止在编译目标上使用低于 25 的 `release` 值
- 禁止在 POM 中使用 Maven 4 新增语法（自动模块发现、parent 版本推断、`modelVersion 4.1.0` 等）
- 禁止在非模块化配置下构建主代码

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
- 默认配置文件：`Aha.yaml`（主配置）
- 默认配置：`AhaDefault.yaml`（classpath 主配置默认值）
- 模型配置文件：`Model.yml`（供应商明细与当前默认供应商，位于用户级目录 `~/.aha/`）
- 模型默认配置：`ModelDefault.yml`（classpath 内置供应商）
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
```

**硬性规则**：

- `common` 不得依赖任何其他 AHA 模块
- `extension-api` 仅依赖 `common`
- `core` 不得依赖 `tools`、`cli`、`desktop`
- `tool` 不得依赖 `cli`、`desktop`
- `cli` 和 `desktop` 之间不得互相依赖
- `core` 不得引用 JavaFX、picocli、JLine
- 第三方扩展仅依赖 `extension-api` 和 `common`，不依赖 `core` 内部实现

## 第 5 条：技术选型清单

| 领域 | 选定方案 | 不可替换性 |
|---|---|---|
| CLI 框架 | picocli + JLine | 除非有重大安全或性能问题 |
| GUI | OpenJFX 25，WebView + FXML | 除非有重大安全或性能问题 |
| JSON/YAML | Jackson 3.x（groupId `tools.jackson`） | 除非有重大安全或性能问题 |
| 日志 | SLF4J + Log4j2 | 可替换 Log4j2 后端 |
| HTTP 客户端 | JDK HttpClient 或 OkHttp | 二选一，由 Core 层适配器隔离 |
| 持久化 | SQLite (xerial) + Jackson | 除非有重大安全或性能问题 |
| DI | 手动工厂 + ServiceLoader + ExtensionRuntime | 禁止引入 Spring/Guice |

> 本清单只写**主版本线**。依赖的确切版本以父 POM 的 `<properties>` 为唯一来源——
> 升级由 Dependabot 每日提出，把具体版本号抄进文档必然漂移。
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
   - `Docs/DevSpec/` 下的规范约束类文档统一以 `Spec.md` 结尾（`TestingSpec.md`、`YamlFieldSpec.md`、`CommitMessageSpec.md`、`DocumentationSpec.md`、`BuildSpec.md`），禁止以 `Rule.md` 结尾
   - 其余 DevSpec 文档按语义命名（`Constitution.md`、`CodingStandard.md`、`ModuleConvention.md`、`BranchStrategy.md`、`CodeReviewChecklist.md`、`ReleaseProcess.md`）
   - 同一主题的「规范」与「操作」拆分存放：规范进 `Docs/DevSpec/`（`Spec.md`），操作进 `Docs/Guide/`（`Guide.md`），如 `BuildSpec.md` ↔ `BuildGuide.md`
2. **例外**：`README.md`、`AGENTS.md`、`LICENSE`、`CHANGELOG.md`、`CONTRIBUTING.md` 等业界通用文件名保留原样
3. **YAML 字段命名**：所有 AHA 项目自有的 YAML 文件，其字段名（键名）统一采用大驼峰（PascalCase）命名。字段值保持原样，不受此规范约束。第三方框架（如 Log4j2、GitHub Actions、Docker Compose）的 YAML schema 字段遵循第三方规范
4. **文档存放**：
   - 开发规范 → `Docs/DevSpec/`
   - 设计文档 → `Docs/Design/`
   - 用户指南 → `Docs/Guide/`
   - 权威设计文档（本文件）→ `Docs/AHA/`
   - 图资源 → `Docs/Diagrams/`
5. **文档头部**：每个文档必须有版本号、状态、生效日期、变更日志
6. **更新制度**：代码变更导致接口、配置、行为变化时，同一 PR 内更新相关文档

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
- 技能目录名与 `SKILL.md` frontmatter 中的 `name` 均采用 kebab-case（小写字母 + 连字符），二者必须一致
- 技能目录内的 Markdown 资源同样使用 kebab-case，与技能整体保持一致
- `SKILL.md` 必须包含 `name` 与 `description` frontmatter（缺失则不会被 Agent 加载），建议补充 `license`、`compatibility`、`metadata`
- `SKILL.md` 主体保持精简，细节下沉到 `references/`（渐进披露）
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

---

# 第二部分：文档规范

## 1. 文档目录结构

```
aha/
├── AGENTS.md                              ← AGENT 入口
├── README.md                              ← 项目说明（业界通用名）
├── LICENSE                                ← 许可证（业界通用名）
├── CHANGELOG.md                           ← 变更日志（业界通用名）
├── .agents/
│   └── skills/
│       ├── build/
│       │   ├── SKILL.md
│       │   └── references/
│       ├── test/
│       ├── llm-adapter/
│       ├── tool-authoring/
│       ├── extension-authoring/
│       └── release/
└── Docs/
    ├── AHA/
    │   └── AHA-Design-V1.md
    ├── DevSpec/
    │   ├── Constitution.md
    │   ├── BuildSpec.md
    │   ├── CodingStandard.md
    │   ├── ModuleConvention.md
    │   ├── DocumentationSpec.md
    │   ├── TestingSpec.md
    │   ├── CommitMessageSpec.md
    │   ├── BranchStrategy.md
    │   ├── CodeReviewChecklist.md
    │   ├── ReleaseProcess.md
    │   └── YamlFieldSpec.md
    ├── Design/
    │   ├── ArchitectureOverview.md
    │   ├── AgentServiceDesign.md
    │   ├── LlmAdapterDesign.md
    │   ├── InternalProtocolSpec.md
    │   ├── ToolSystemDesign.md
    │   ├── MemoryStorageDesign.md
    │   ├── SecurityDesign.md
    │   ├── LoggingDesign.md
    │   ├── PixelLogoDesign.md
    │   ├── CLIDesign.md
    │   ├── TUIDesign.md
    │   ├── GUIDesign.md
    │   ├── DesktopDesign.md
    │   ├── RemoteAndProtocolDesign.md
    │   ├── ExtensionSystemDesign.md
    │   ├── SelfHostingDesign.md
    │   └── EventBusDesign.md
    ├── Guide/
    │   ├── GettingStarted.md
    │   ├── ReferenceGuide.md
    │   ├── BuildGuide.md
    │   ├── ConfigurationGuide.md
    │   ├── ProviderSetupGuide.md
    │   ├── ToolUsageGuide.md
    │   ├── ExtensionAuthoringGuide.md
    │   └── TroubleshootingGuide.md
    ├── Diagrams/
        ├── ModuleArchitecture.svg
        ├── AgentFlow.svg
        ├── LlmAdapterFlow.svg
        ├── ToolInvocationSequence.svg
        ├── ConfigLoadingFlow.svg
        ├── ExtensionArchitecture.svg
        └── ExtensionLifecycle.svg
    └── DevLog/
        ├── DevLog-20261007-20.md
        ├── DevLog-20261007-21.md
        ├── DevLog-20261007-22.md
        ├── DevLog-20261007-23.md
        └── DevLog-20261007-24.md
```

## 2. 命名规范

**采用大驼峰命名的文件类型**：

| 类型 | 示例 | 说明 |
|---|---|---|
| Markdown 文档 | `AgentServiceDesign.md` | 除业界通用名外 |
| SVG 图形 | `ModuleArchitecture.svg` | 所有 SVG |
| PNG/JPG 图片 | `AgentFlowDiagram.png` | 所有图片 |
| 目录名 | `DevSpec`、`Design` | 除 `.agents/skills/` 下的技能目录采用 kebab-case |

**保留业界通用名**：

| 文件名 | 原因 |
|---|---|
| `README.md` | GitHub 默认渲染入口 |
| `AGENTS.md` | AGENT 约定入口 |
| `LICENSE` | 法律通用名 |
| `CHANGELOG.md` | 业界通用名 |
| `CONTRIBUTING.md` | GitHub 约定 |
| `CODE_OF_CONDUCT.md` | 业界通用名 |
| `SECURITY.md` | 业界通用名 |

**文档后缀约定**：

| 目录 | 后缀 | 示例 |
|---|---|---|
| `Docs/DevSpec/`（规范约束类） | `Spec.md` | `TestingSpec.md`、`YamlFieldSpec.md`、`CommitMessageSpec.md`、`DocumentationSpec.md`、`BuildSpec.md` |
| `Docs/DevSpec/`（其余） | 按语义 | `Constitution.md`、`BranchStrategy.md`、`ReleaseProcess.md`、`CodeReviewChecklist.md`、`CodingStandard.md`、`ModuleConvention.md` |
| `Docs/Design/` | `Design.md` / `Spec.md` / `Overview.md` | `AgentServiceDesign.md`、`InternalProtocolSpec.md`、`ArchitectureOverview.md` |
| `Docs/Guide/` | `Guide.md` / `GettingStarted.md` | `ConfigurationGuide.md`、`ReferenceGuide.md`、`BuildGuide.md` |

> 禁止使用 `Rule.md` 结尾（历史命名，已统一为 `Spec.md`）。

**反例**（禁止）：

- `agent-service-design.md`
- `agent_service_design.md`
- `AgentServiceDesign.MD`
- `module-architecture.svg`

## 3. 文档头部模板

```markdown
# 文档标题

**文档版本**：v1.0.0
**状态**：草稿 / 评审中 / 冻结 / 已废弃
**生效日期**：2026-10-06
**最后更新**：2026-10-07
**负责人**：@username
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @username |
| v1.0.0 | 2026-10-06 | 初始版本 |
| v1.0.0 | 2026-10-06 | 初始版本 |
| v1.1.0 | 2026-10-06 | 新增 LLM 供应商适配条款 |
| v1.1.1 | 2026-10-06 | 修正 IR 独立性表述 |
| v1.2.0 | 2026-10-06 | 新增文档规范、AGENT 与技能体系 |
| v1.2.1 | 2026-10-06 | 新增 YAML 字段命名规范 |
| v1.3.0 | 2026-10-06 | 新增扩展架构条款 |
| v3.19.0 | 2026-10-07 | 覆盖率与用例数刷新为 0.1.0-SNAPSHOT 实测值；模块名统一为 `tool`；扩展目录统一为 `extension` | @ACANX |
| v3.20.0 | 2026-10-07 | 上下文压缩（`/compact`、`/autocompact`）纳入 0.1；修正 `loadHistory` 窗口语义 | @ACANX |
| v3.21.0 | 2026-10-07 | 新增第十五部分 1.1「1.0 前必须补齐的能力缺口」（记忆工具与命令、编辑器唤起、用量统计）与 A7 验收 | @ACANX |
| v3.22.0 | 2026-10-07 | Agent 身份文件（`AGENTS.md` / `AHA.md`）的查看与编辑提升到 0.1 交付（`/memory`） | @ACANX |
| v3.23.0 | 2026-10-07 | 缺口表新增「记忆的记录与整理」；注明记忆载体未定及其判据去向 | @ACANX |
| v3.24.0 | 2026-10-07 | 补记已定决策：记忆按项目存放与项目 ID 规则、三级写入策略、`Memory.ModelWrite` 开关、先记录后利用原则 | @ACANX |
| v3.25.0 | 2026-10-07 | 补记「Windows 与 WSL 默认视为两个独立项目，待补显式共享机制」 | @ACANX |
| v3.26.0 | 2026-10-07 | 版本号统一为 0.1.0，并改为构建期注入 | @ACANX |
| v3.27.0 | 2026-10-07 | 交付「流式期间保持输入可用」（含 Ctrl+C 只打断本轮）；修复模块路径下 Ctrl+C 杀进程 | @ACANX |
| v3.28.0 | 2026-10-07 | 交付常驻状态行（`StatusSurface`，终端底部保留一行，按能力降级） | @ACANX |
| v3.29.0 | 2026-10-07 | 交付输入行分隔线与工具区块化展示（含 `ToolResultEvent.success`、修复 `aha-tool` 缺 `uses`） | @ACANX |
| v3.30.0 | 2026-10-07 | 新增 `Docs/PLAN.md`（暂缓与受限事项）；状态行文案收敛为「转圈帧 + 阶段」 | @ACANX |
| v3.31.0 | 2026-10-07 | 新增 `Docs/Design/TUIDesign.md`（TUI 当前实现说明） | @ACANX |
| v3.32.0 | 2026-10-07 | 新增 `Docs/Design/GUIDesign.md`（桌面端界面方案） | @ACANX |
| v3.33.0 | 2026-10-07 | §5.4 新增「运行环境注入」（模型必须知道平台与 shell） | @ACANX |
| v3.34.0 | 2026-10-07 | `/prompt` 改名 `/memory`；身份文件改为「用户级 + 项目级叠加」；新增命令补全与实时着色 | @ACANX |
| v3.35.0 | 2026-10-07 | `Agent.SystemPrompt` 默认补入 AHA 身份声明（此前为空串，`/config` 看不到） | @ACANX |
| v3.36.0 | 2026-10-07 | 全局身份文件自动创建；来源改为 `files:` 列全，`/memory view` 分节展示 | @ACANX |
| v3.37.0 | 2026-10-07 | §5.1 补实际拼装顺序（实测）与「只改配置支持新兼容文件」 | @ACANX |
| v3.38.0 | 2026-10-07 | §5.1 重写为「组装顺序与优先级」唯一权威说明：同层全部候选、层级叠加、可见性、来源格式 | @ACANX |
| v3.39.0 | 2026-10-07 | 默认候选补入 `CLAUDE.md`；补「相同内容只注入一次」与「未列出的兼容文件必须提示」 | @ACANX |
| v3.40.0 | 2026-10-07 | §5.1 新增 5.1.5「消息级结构」与 5.1.7「已知取舍」（当时为拼接为一条 system，**已被 v3.41.0 取代**） | @ACANX |
| v3.41.0 | 2026-10-07 | §5.1.5 改为「一个来源一条 system 消息」（不再拼接），含三家适配器的落地形态；§5.1.7 改为「来源对模型可见」 | @ACANX |
| v3.42.0 | 2026-10-07 | 新增 §5.5「双向消息结构（JSON 层面）」：内部模型、请求方向、响应方向（非流式/流式）、字段映射与复现步骤；§5.1.6 收敛为指针；示例改用真实模型名并对齐内置预设 | @ACANX |
| v3.43.0 | 2026-10-07 | 目录树补回漏列的 TUIDesign / GUIDesign / RemoteAndProtocolDesign，并加入 LoggingDesign；文档清单登记 LoggingDesign；编码约定的日志小节指向 LoggingDesign | @ACANX |
| v3.44.0 | 2026-10-07 | §5.1.4 的启动信息实测样本按新格式更正（字段按显示宽度补位、子项单空格分隔）；启动横幅新增右侧 ASCII 标志（见 TUIDesign 3.2 / 5.3） | @ACANX |
| v3.45.0 | 2026-10-07 | §3.1 覆盖率与用例数按 `./mvnw clean verify` 实测刷新（604 用例 / 合计行覆盖 80.4%），并写明统计口径与复现命令 | @ACANX |
| v3.46.0 | 2026-10-07 | §3.2 更正 JaCoCo 版本为 0.8.15（与 `pom.xml` 一致） | @ACANX |
| v3.47.0 | 2026-10-07 | 本文件移至 `Docs/AHA/AHA-Design-V1.md`：正文相对链接改为 `../`，两处目录树与附录 A 索引登记新位置 | @ACANX |
| v3.48.0 | 2026-10-07 | 第 10 条补「权威设计文档（本文件）→ `Docs/AHA/`」 | @ACANX |
| v3.49.0 | 2026-10-07 | 第 5 条选型清单改为主版本线并说明版本单一来源；POM 片段不再复制具体版本号 | @ACANX |
| v3.50.0 | 2026-10-07 | §5.1.1 补两条边界约定：`AHA_HOME` 取值顺序（系统属性优先于环境变量）、项目级向上查找止步于用户主目录 | @ACANX |
| v3.51.0 | 2026-10-07 | §3.1 实测值按 Windows 平台缺陷修复后的 `clean verify` 刷新（604 用例 / 合计行覆盖 80.3%，4040/5033） | @ACANX |
| v3.52.0 | 2026-10-07 | 目录树与附录 A 文档索引补 `Docs/DevLog/`（排障复盘按时间线命名） | @ACANX |
| v3.53.0 | 2026-10-07 | 目录树与附录 A 补第二篇 DevLog（覆盖率门禁自证） | @ACANX |
| v3.54.0 | 2026-10-07 | §3.1 实测值刷新（605 用例 / 合计行覆盖 80.3%，4045/5038） | @ACANX |
| v3.55.0 | 2026-10-07 | 附录 A 目录树同步开发日志改名（`DevLog-20261007-21-2.md` → `DevLog-20261007-22.md`，命名规则见 DocumentationSpec §1） | @ACANX |
| v3.56.0 | 2026-10-07 | 附录 A 目录树补齐 `.github/`：原先只列 Build 与 Release，现列四个工作流（Build / Gate / Compat / Release）并新增 `actions/maven-run/` | @ACANX |

---
```

## 4. 文档分类与存放规则

| 文档类型 | 存放位置 | 内容 |
|---|---|---|
| 宪法 | `Docs/DevSpec/Constitution.md` | 技术宪法全文 |
| 开发规范 | `Docs/DevSpec/` | 编码、测试、提交、分支、发布规范 |
| 设计文档 | `Docs/Design/` | 架构、模块、接口、协议设计 |
| 用户指南 | `Docs/Guide/` | 安装、构建、配置、使用、排错 |
| 图资源 | `Docs/Diagrams/` | SVG 优先，PNG 仅用于位图 |
| AGENT 技能 | `.agents/skills/` | 技能定义与资源 |

> **规范与操作分离**：同一主题若同时包含强制要求与操作步骤，拆为两份文档
> ——规范进 `Docs/DevSpec/`（`Spec.md`），操作进 `Docs/Guide/`（`Guide.md`）。
> 例如构建：`Docs/DevSpec/BuildSpec.md`（要求） ↔ `Docs/Guide/BuildGuide.md`（步骤）。

## 5. 文档更新制度

- **即时更新**：代码变更导致接口、配置、行为变化时，同一 PR 内更新相关文档
- **定期更新**：每个 minor release 前，审查 `Docs/DevSpec/` 全部文档
- **宪法修订**：按宪法第 8 条流程执行
- **过期标记**：文档不再适用时，标记为"已废弃"，不直接删除
- **链接检查**：CI 中定期执行 Markdown 链接有效性检查

## 6. 图资源规范

- **格式优先**：SVG（矢量）> PNG（位图）> JPG（照片）
- **命名**：大驼峰，如 `ModuleArchitecture.svg`
- **尺寸**：SVG 不限制；PNG 宽度不超过 1920px
- **源文件**：如使用 draw.io / Figma，源文件存放于 `Docs/Diagrams/Source/` 目录
- **嵌入**：Markdown 中通过相对路径引用

---

# 第三部分：AGENT 入口与技能体系

## 1. AGENTS.md

`AGENTS.md` 是 AI Agent（Claude Code、Cursor、GitHub Copilot Workspace）进入项目时的第一入口。

````markdown
# AHA - Agent Harness

**项目代号**：AHA
**当前版本**：0.1.0
**构建工具**：Maven 4（运行时）/ Maven 3.9.x（兼容基线）
**JDK**：25 (LTS)
**模块化**：JPMS 强制启用

---

## 项目概览

AHA 是一个 Agent Harness 工具，支持 CLI 与桌面端双模式运行。
核心能力：LLM 供应商适配、工具调用、记忆管理、配置管理、安全存储、扩展能力。

## 快速开始

### 构建

```bash
./mvnw clean verify          # Maven 4 运行时
mvn clean verify             # Maven 3.9.x 兼容验证
```

### 运行 CLI

```bash
./mvnw -pl aha-cli exec:java
# 或
./bin/Aha.bat chat           # Windows
./bin/Aha.sh chat            # Linux
```

### 运行测试

```bash
./mvnw test                  # 全部测试
./mvnw -pl aha-core test     # 单模块测试
```

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
- **配置文件**：`Aha.yaml`
- **YAML 字段**：大驼峰（PascalCase）；**SQL 表名/字段**：snake_case（表名单数）
- **环境变量前缀**：`AHA_`
- **数据库**：`Aha.db`（SQLite）
- **扩展描述符**：`AhaExtension.yaml`
- **扩展目录**：`AHA_HOME/Extension/`

## 开发规范

详见 `Docs/DevSpec/`：

- [Constitution.md](Docs/DevSpec/Constitution.md) - 技术宪法
- [CodingStandard.md](Docs/DevSpec/CodingStandard.md) - 编码规范
- [BuildSpec.md](Docs/DevSpec/BuildSpec.md) - 构建规范
- [TestingSpec.md](Docs/DevSpec/TestingSpec.md) - 测试规范
- [YamlFieldSpec.md](Docs/DevSpec/YamlFieldSpec.md) - YAML 字段规范
- [CommitMessageSpec.md](Docs/DevSpec/CommitMessageSpec.md) - 提交规范
- [BranchStrategy.md](Docs/DevSpec/BranchStrategy.md) - 分支策略
- [CodeReviewChecklist.md](Docs/DevSpec/CodeReviewChecklist.md) - 评审清单
- [ReleaseProcess.md](Docs/DevSpec/ReleaseProcess.md) - 发布流程

## 设计文档

详见 `Docs/Design/`：

- [ArchitectureOverview.md](Docs/Design/ArchitectureOverview.md)
- [AgentServiceDesign.md](Docs/Design/AgentServiceDesign.md)
- [LlmAdapterDesign.md](Docs/Design/LlmAdapterDesign.md)
- [InternalProtocolSpec.md](Docs/Design/InternalProtocolSpec.md)
- [ToolSystemDesign.md](Docs/Design/ToolSystemDesign.md)
- [ExtensionSystemDesign.md](Docs/Design/ExtensionSystemDesign.md)
- [SelfHostingDesign.md](Docs/Design/SelfHostingDesign.md) - AHA 自举里程碑（1.0 硬门槛）

## 技能索引

详见 `.agents/skills/`：

| 技能 | 用途 | 入口 |
|---|---|---|
| `build` | 构建、打包、Maven Wrapper 操作 | [SKILL.md](.agents/skills/build/SKILL.md) |
| `test` | 单元测试、集成测试、覆盖率 | [SKILL.md](.agents/skills/test/SKILL.md) |
| `llm-adapter` | 新增 LLM 供应商适配器 | [SKILL.md](.agents/skills/llm-adapter/SKILL.md) |
| `tool-authoring` | 新增工具 | [SKILL.md](.agents/skills/tool-authoring/SKILL.md) |
| `extension-authoring` | 新增扩展 | [SKILL.md](.agents/skills/extension-authoring/SKILL.md) |
| `release` | 版本发布流程 | [SKILL.md](.agents/skills/release/SKILL.md) |

## 常用命令

> CLI 命令参数、配置项、环境变量的完整参考见 `Docs/Guide/ReferenceGuide.md`。

| 命令 | 说明 |
|---|---|
| `./mvnw clean verify` | 完整构建 + 测试 |
| `./mvnw -pl aha-core test` | 单模块测试 |
| `./mvnw -pl aha-cli exec:java` | 运行 CLI |
| `./mvnw dependency:tree` | 查看依赖树 |
| `./mvnw javadoc:javadoc` | 生成 Javadoc |

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
````

## 2. 技能目录结构

每个技能是一个独立目录，遵循 Agent Skills 规范：`SKILL.md` 入口，配 `references/`（参考文档）、`assets/`（模板与数据）、`scripts/`（可执行脚本）。

```
.agents/skills/
├── build/
│   ├── SKILL.md
│   └── references/
│       ├── maven-profiles.md
│       └── troubleshooting.md
├── test/
│   ├── SKILL.md
│   └── references/
│       ├── test-fixtures.md
│       └── coverage-guide.md
├── llm-adapter/
│   ├── SKILL.md
│   ├── references/
│   │   └── fixtures.md
│   └── assets/
│       └── MyProviderAdapter.java
├── tool-authoring/
│   ├── SKILL.md
│   └── assets/
│       ├── MyTool.java
│       └── MyToolProvider.java
├── extension-authoring/
│   ├── SKILL.md
│   └── assets/
│       └── MyExtension.java
└── release/
    ├── SKILL.md
    ├── references/
    │   └── release-checklist.md
    └── assets/
        └── ChangelogTemplate.md
```

### frontmatter 字段

`SKILL.md` 以 YAML frontmatter 开头：

| 字段 | 必需 | 说明 |
|---|---|---|
| `name` | 是 | 命令与显示名。小写字母、数字、连字符；无首尾/连续连字符；≤ 64 字符；必须与目录名一致 |
| `description` | 是 | 路由描述，模型据此判断何时加载。需同时说明「做什么」与「何时用」；≤ 1024 字符 |
| `license` | 否 | 许可证名或内置许可证文件 |
| `compatibility` | 否 | 环境要求（JDK、Maven、网络、权限等） |
| `metadata` | 否 | 附加键值对，本项目用于 `version` / `owner` |
| `allowed-tools` | 否 | 实验性：预批准工具列表 |
| `disable-model-invocation` | 否 | 置 `true` 则仅可通过 `/skill:<name>` 显式调用 |

缺失 `name` 或 `description` 的 `SKILL.md` 不会被加载；`name` 与目录名不一致时 pi 不报错，但其他 Agent Skills 实现可能强制校验，因此必须保持一致以保证可移植性。

### 编写原则

- **渐进披露**：`SKILL.md` 主体只保留「何时使用 → 前置条件 → 步骤 → 参考 → 常见错误」，细节下沉到 `references/`
- **相对路径**：引用技能内资源一律使用相对技能目录的路径，便于整体迁移
- **不写变更日志**：技能的版本历史由 Git 管理，`SKILL.md` 不内嵌变更日志表

## 3. 技能清单（0.1 版本）

| 技能（`name`） | 用途 | 触发条件 |
|---|---|---|
| `build` | 构建、打包、Maven 操作 | 需要编译、打包、排查构建问题 |
| `test` | 测试编写与执行 | 新增测试、覆盖率分析 |
| `llm-adapter` | 新增 LLM 供应商适配器 | 接入新供应商、修改 IR |
| `tool-authoring` | 新增工具 | 实现新工具、注册 ToolProvider |
| `extension-authoring` | 新增扩展 | 实现 AhaExtension、注册扩展点 |
| `release` | 版本发布 | 准备 release、更新 changelog |

## 4. SKILL.md 模板

````markdown
---
name: build
description: 构建、打包与 Maven Wrapper 操作：执行 clean verify、单模块构建、双 Maven 版本兼容验证，并排查构建失败。当需要编译、打包、验证构建，或定位构建报错时使用。
---

# 构建与打包技能（build）

**版本**：v1.0.0
**适用场景**：项目构建、打包、Maven Wrapper 操作

---

## 触发条件

当需要执行以下操作时使用本技能：

- 构建项目
- 运行测试
- 打包分发
- 排查构建失败

## 前置条件

- JDK 25 已安装
- 网络可访问 Maven Central
- 项目已克隆到本地

## 操作步骤

### 1. 完整构建

```bash
./mvnw clean verify
```

预期输出：`BUILD SUCCESS`

### 2. 单模块构建

```bash
./mvnw -pl aha-core -am clean install
```

### 3. 兼容性验证

```bash
mvn clean verify
```

## 常见问题

| 问题 | 原因 | 解决 |
|---|---|---|
| `module not found` | JPMS 配置错误 | 检查 `module-info.java` |
| `Maven 4 syntax error` | 使用了 Maven 4 新语法 | 回退到 Maven 3 兼容语法 |

## 相关资源

- [BuildGuide.md](../../../Docs/Guide/BuildGuide.md)

## 变更日志

| 版本 | 日期 | 变更 |
|---|---|---|
````

---

# 第四部分：项目结构

## 1. 完整目录结构

```
aha/
├── AGENTS.md
├── README.md
├── CHANGELOG.md
├── LICENSE
├── .gitignore
├── .gitattributes
├── mvnw
├── mvnw.cmd
├── pom.xml
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── .agents/
│   └── skills/
│       ├── build/
│       ├── test/
│       ├── llm-adapter/
│       ├── tool-authoring/
│       ├── extension-authoring/
│       └── release/
├── .github/
│   ├── actions/
│   │   └── maven-run/          ← Maven 调用统一入口：清失败标记 + 重试
│   │       └── action.yml
│   └── workflows/
│       ├── Build.yml           ← 快检查（每次 push / PR）
│       ├── Gate.yml            ← 门禁 + 每周定期扫描
│       ├── Compat.yml          ← Maven 3.9.x 兼容基线
│       └── Release.yml
├── Docs/
│   ├── AHA/
│   ├── DevSpec/
│   ├── Design/
│   ├── Guide/
│   └── Diagrams/
├── bin/
│   ├── Aha.bat
│   └── Aha.sh
├── aha-common/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       │   ├── module-info.java
│       │   └── com/acanx/module/aha/common/
│       │       ├── model/
│       │       ├── tool/
│       │       ├── event/
│       │       └── exception/
│       └── test/java/
├── aha-extension-api/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       │   ├── module-info.java
│       │   └── com/acanx/module/aha/extension/api/
│       │       ├── AhaExtension.java
│       │       ├── ExtensionContext.java
│       │       ├── Registration.java
│       │       ├── ExtensionDescriptor.java
│       │       ├── event/
│       │       └── extension/
│       └── test/java/
├── aha-core/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       │   ├── module-info.java
│       │   └── com/acanx/module/aha/core/
│       │       ├── agent/
│       │       ├── service/
│       │       ├── memory/
│       │       ├── config/
│       │       ├── security/
│       │       ├── mcp/
│       │       ├── extension/
│       │       │   ├── loader/
│       │       │   └── event/
│       │       └── llm/
│       │           ├── protocol/
│       │           ├── adapter/
│       │           ├── openai/
│       │           ├── anthropic/
│       │           ├── gemini/
│       │           └── registry/
│       └── test/
│           ├── java/
│           └── resources/
│               ├── Configs/
│               └── Fixtures/
├── aha-tool/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       │   ├── module-info.java
│       │   └── com/acanx/module/aha/tool/
│       └── test/java/
├── aha-cli/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       │   ├── module-info.java
│       │   └── com/acanx/module/aha/cli/
│       └── test/java/
└── aha-desktop/
    └── pom.xml
```

## 2. 父 POM

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.acanx.module</groupId>
    <artifactId>aha</artifactId>
    <version>0.1.0</version>
    <packaging>pom</packaging>

    <name>AHA</name>
    <description>AHA Agent Harness</description>

    <modules>
        <module>aha-common</module>
        <module>aha-extension-api</module>
        <module>aha-core</module>
        <module>aha-tool</module>
        <module>aha-cli</module>
        <module>aha-desktop</module>
    </modules>

    <properties>
        <maven.compiler.release>25</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.parameters>true</maven.compiler.parameters>

        <!-- 依赖版本集中在这里，是本仓库的唯一来源。此处刻意不复制具体版本号：
             升级由 Dependabot 每日提出，抄进文档必然漂移。属性名如下——
             javafx / jackson / picocli / jline / sqlite / slf4j / log4j /
             junit / assertj / mockito / testfx / jacoco 及若干插件版本 -->
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- AHA 内部模块 -->
            <dependency>
                <groupId>com.acanx.module.aha</groupId>
                <artifactId>aha-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.acanx.module.aha</groupId>
                <artifactId>aha-extension-api</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.acanx.module.aha</groupId>
                <artifactId>aha-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.acanx.module.aha</groupId>
                <artifactId>aha-tool</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.acanx.module.aha</groupId>
                <artifactId>aha-cli</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.acanx.module.aha</groupId>
                <artifactId>aha-desktop</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- 第三方 -->
            <dependency>
                <groupId>info.picocli</groupId>
                <artifactId>picocli</artifactId>
                <version>${picocli.version}</version>
            </dependency>
            <dependency>
                <groupId>info.picocli</groupId>
                <artifactId>picocli-shell-jline3</artifactId>
                <version>${picocli.version}</version>
            </dependency>
            <dependency>
                <groupId>org.jline</groupId>
                <artifactId>jline</artifactId>
                <version>${jline.version}</version>
            </dependency>
            <dependency>
                <groupId>tools.jackson.core</groupId>
                <artifactId>jackson-databind</artifactId>
                <version>${jackson.version}</version>
            </dependency>
            <dependency>
                <groupId>tools.jackson.dataformat</groupId>
                <artifactId>jackson-dataformat-yaml</artifactId>
                <version>${jackson.version}</version>
            </dependency>
            <dependency>
                <groupId>org.xerial</groupId>
                <artifactId>sqlite-jdbc</artifactId>
                <version>${sqlite.version}</version>
            </dependency>
            <dependency>
                <groupId>org.slf4j</groupId>
                <artifactId>slf4j-api</artifactId>
                <version>${slf4j.version}</version>
            </dependency>
            <dependency>
                <groupId>org.apache.logging.log4j</groupId>
                <artifactId>log4j-slf4j2-impl</artifactId>
                <version>${log4j.version}</version>
            </dependency>
            <dependency>
                <groupId>org.apache.logging.log4j</groupId>
                <artifactId>log4j-core</artifactId>
                <version>${log4j.version}</version>
            </dependency>

            <!-- 测试 -->
            <dependency>
                <groupId>org.junit.jupiter</groupId>
                <artifactId>junit-jupiter</artifactId>
                <version>${junit.version}</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>org.assertj</groupId>
                <artifactId>assertj-core</artifactId>
                <version>${assertj.version}</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>org.mockito</groupId>
                <artifactId>mockito-core</artifactId>
                <version>${mockito.version}</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <extensions>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.16.0</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.5.2</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-jar-plugin</artifactId>
                    <version>3.4.2</version>
                </plugin>
            </extensions>
        </pluginManagement>
    </build>
</project>
```

## 3. 子模块 POM 模板

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.acanx.module</groupId>
        <artifactId>aha</artifactId>
        <version>0.1.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <groupId>com.acanx.module.aha</groupId>
    <artifactId>aha-core</artifactId>
    <name>AHA Core</name>

    <dependencies>
        <dependency>
            <groupId>com.acanx.module.aha</groupId>
            <artifactId>aha-common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.acanx.module.aha</groupId>
            <artifactId>aha-extension-api</artifactId>
        </dependency>
        <!-- 其他依赖 -->
    </dependencies>
</project>
```

---

# 第五部分：模块设计

## 1. aha-common

**职责**：零外部依赖（仅 JDK）。定义共享 DTO、`Tool` 接口、`ToolProvider` SPI、事件模型、异常层次。

**module-info.java**：

```java
module com.acanx.module.aha.common {
    exports com.acanx.module.aha.common.model;
    exports com.acanx.module.aha.common.tool;
    exports com.acanx.module.aha.common.event;
    exports com.acanx.module.aha.common.exception;
}
```

**核心类型**：

**异常层次**：

```java
package com.acanx.module.aha.common.exception;

public class AhaException extends RuntimeException {
    private final String code;
    public AhaException(String code, String message) { ... }
    public AhaException(String code, String message, Throwable cause) { ... }
    public String code() { return code; }
}

public class ConfigException extends AhaException { ... }
public class ToolExecutionException extends AhaException { ... }
public class LlmException extends AhaException { ... }
public class SecurityException extends AhaException { ... }
public class ExtensionException extends AhaException { ... }
```

**事件模型**：

```java
package com.acanx.module.aha.common.event;

public sealed interface AgentEvent
    permits ContentEvent, ToolCallEvent, ToolResultEvent,
            UsageEvent, ErrorEvent, DoneEvent {}

public record ContentEvent(String sessionId, String delta) implements AgentEvent {}
public record ToolCallEvent(String sessionId, String toolName, Map<String, Object> args) implements AgentEvent {}
public record ToolResultEvent(String sessionId, String toolName, Object result) implements AgentEvent {}
public record UsageEvent(String sessionId, int promptTokens, int completionTokens) implements AgentEvent {}
public record ErrorEvent(String sessionId, String code, String message) implements AgentEvent {}
public record DoneEvent(String sessionId, String finishReason) implements AgentEvent {}
```

**共享模型**：

```java
package com.acanx.module.aha.common.model;

public record SessionConfig(String model, String systemPrompt, Map<String, Object> extras) {}
public record ToolDescriptor(String name, String description, JsonSchema parameters, ToolPermission permission) {}
public record MemoryEntry(String key, String value, long createdAt) {}
public record ToolResult(boolean success, Object output, String error) {}
```

**Tool SPI**：

```java
package com.acanx.module.aha.common.tool;

public interface Tool {
    String name();
    String description();
    JsonSchema parameters();
    ToolResult execute(Map<String, Object> params, CancellationToken token);
    ToolPermission requiredPermission();
}

public interface ToolProvider {
    List<Tool> tools();
}

public enum ToolPermission {
    READ, WRITE, NETWORK, EXECUTE, ADMIN
}

public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            listeners.forEach(Runnable::run);
        }
    }
    public boolean isCancelled() { return cancelled.get(); }
    public void onCancel(Runnable listener) {
        if (cancelled.get()) { listener.run(); }
        else { listeners.add(listener); }
    }
}

public record JsonSchema(
    String type,
    Map<String, JsonSchema> properties,
    List<String> required,
    String description
) {
    public static JsonSchema object(Map<String, JsonSchema> props, List<String> required) {
        return new JsonSchema("object", props, required, null);
    }
    public static JsonSchema string(String desc) {
        return new JsonSchema("string", Map.of(), List.of(), desc);
    }
    public static JsonSchema integer(String desc) {
        return new JsonSchema("integer", Map.of(), List.of(), desc);
    }
}
```

## 2. aha-extension-api

**职责**：定义扩展契约。仅依赖 `aha-common`。第三方扩展只需依赖此模块。

**module-info.java**：

```java
module com.acanx.module.aha.extension.api {
    requires transitive com.acanx.module.aha.common;

    exports com.acanx.module.aha.extension.api;
    exports com.acanx.module.aha.extension.api.event;
    exports com.acanx.module.aha.extension.api.point;
}
```

**核心类型**：

```java
package com.acanx.module.aha.extension.api;

public interface AhaExtension {
    ExtensionDescriptor descriptor();
    void onStart(ExtensionContext context);
    void onStop();
}

public record ExtensionDescriptor(
    String id,
    String name,
    String version,
    String apiVersion,
    String mainModule,
    String mainClass,
    List<ExtensionPermission> permissions,
    List<ExtensionDependency> dependencies
) {}

public record ExtensionDependency(String id, String version, boolean optional) {}

public enum ExtensionPermission {
    READ, WRITE, NETWORK, EXECUTE, ADMIN
}

public interface ExtensionContext {
    <T> Registration register(Class<T> extensionPoint, T implementation);
    EventBus events();
    ExtensionConfig config();
    Logger logger();
    Path dataDir();
}

@FunctionalInterface
public interface Registration extends AutoCloseable {
    void close();
}

public interface ExtensionConfig {
    Optional<String> getString(String key);
    Optional<Integer> getInt(String key);
    Optional<Boolean> getBoolean(String key);
    Map<String, Object> asMap();
}
```

**事件总线（extension-api 层）** ：

```java
package com.acanx.module.aha.extension.api.event;

public interface EventBus {
    <E extends ExtensionEvent> void emit(EventKey<E> key, E event);
    <E extends ExtensionEvent> void on(EventKey<E> key, ExtensionEventListener<E> listener);
    <E extends ExtensionEvent> E waterfall(EventKey<E> key, E event);
}

@FunctionalInterface
public interface ExtensionEventListener<E extends ExtensionEvent> {
    void onEvent(E event);
}

public interface ExtensionEvent {}

public final class EventKey<E extends ExtensionEvent> {
    private final String key;
    private final Class<E> type;
    private final DispatchMode mode;

    public static <E extends ExtensionEvent> EventKey<E> of(
            String key, Class<E> type, DispatchMode mode) {
        return new EventKey<>(key, type, mode);
    }
    public String key() { return key; }
    public Class<E> type() { return type; }
    public DispatchMode mode() { return mode; }
}

public enum DispatchMode {
    EMIT, WATERFALL, SERIAL, PARALLEL
}
```

**扩展点接口（extension-api 层）** ：

```java
package com.acanx.module.aha.extension.api.point;

public interface CommandProvider {
    List<CommandDescriptor> commands();
}

public interface EventListenerProvider {
    void onAgentEvent(AgentEvent event);
}

public interface ConfigSource {
    Map<String, Object> load();
    int priority();
}
```

## 3. aha-core

**职责**：Agent 推理引擎、LLM 适配器体系、SQLite 记忆存储、YAML 配置加载、加密密钥管理、MCP 适配器接口、扩展运行时。

**module-info.java**：

```java
module com.acanx.module.aha.core {
    requires transitive com.acanx.module.aha.common;
    requires transitive com.acanx.module.aha.extension.api;
    requires org.xerial.sqlitejdbc;
    requires tools.jackson.databind;
    requires tools.jackson.dataformat.yaml;
    requires org.slf4j;
    requires java.net.http;

    exports com.acanx.module.aha.core.agent;
    exports com.acanx.module.aha.core.service;
    exports com.acanx.module.aha.core.memory;
    exports com.acanx.module.aha.core.config;
    exports com.acanx.module.aha.core.security;
    exports com.acanx.module.aha.core.mcp;
    exports com.acanx.module.aha.core.extension;
    exports com.acanx.module.aha.core.llm;
    exports com.acanx.module.aha.core.llm.protocol;

    uses com.acanx.module.aha.common.tool.ToolProvider;
    uses com.acanx.module.aha.core.llm.LlmProviderAdapter;
    uses com.acanx.module.aha.extension.api.AhaExtension;

    provides com.acanx.module.aha.core.llm.LlmProviderAdapter
        with com.acanx.module.aha.core.llm.openai.OpenAiAdapter,
             com.acanx.module.aha.core.llm.anthropic.AnthropicAdapter,
             com.acanx.module.aha.core.llm.gemini.GeminiAdapter;

    opens com.acanx.module.aha.core.config to tools.jackson.databind;
    opens com.acanx.module.aha.core.llm.protocol to tools.jackson.databind;
}
```

## 4. aha-tool

**module-info.java**：

```java
module com.acanx.module.aha.tool {
    requires com.acanx.module.aha.common;
    requires com.acanx.module.aha.core;
    requires org.slf4j;

    provides com.acanx.module.aha.common.tool.ToolProvider
        with com.acanx.module.aha.tool.FileToolProvider,
             com.acanx.module.aha.tool.HttpToolProvider,
             com.acanx.module.aha.tool.ShellToolProvider;
}
```

**0.1 内置工具**：

| 工具名 | 权限 | 说明 |
|---|---|---|
| `file-read` | READ | 读取文件内容，支持路径白名单 |
| `file-write` | WRITE | 写入文件，需用户确认 |
| `file-list` | READ | 列出目录内容 |
| `http-get` | NETWORK | HTTP GET 请求 |
| `shell-exec` | EXECUTE | 执行 Shell 命令，需显式确认 |

## 5. aha-cli

**module-info.java**：

```java
module com.acanx.module.aha.cli {
    requires com.acanx.module.aha.core;
    requires info.picocli;
    requires org.jline;

    exports com.acanx.module.aha.cli;
}
```

**命令结构**：

```
aha
├── chat                      启动交互式对话
│   ├── --provider <id>
│   ├── --model <name>
│   └── --session <id>
├── run <input>               单次推理
├── tool                      工具管理
│   ├── list
│   └── invoke <name>
├── provider                  供应商管理
│   ├── list
│   └── test <id>
├── extension                    扩展管理
│   ├── list
│   ├── enable <id>
│   ├── disable <id>
│   └── info <id>
├── config                    配置管理
│   ├── get <key>
│   ├── set <key> <value>
│   └── edit
└── version                   版本信息
```

## 6. aha-desktop（0.1 仅占位）

0.1 版本不实现桌面端。0.2 版本实现 OpenJFX WebView + 本地 HTTP 服务器 + FXML Controller。

---

# 第六部分：LLM 供应商适配体系

## 1. 设计原则

**IR 是 AHA 的内部标准，不是任何外部协议的从属物。**

- 生态兼容性是**首要目标**，但兼容性通过**适配器层**实现
- IR 的字段名、类型名、枚举值由 AHA 定义
- IR 有独立的版本号，演进节奏由 AHA 控制
- 适配器是 IR 与外部协议之间的唯一桥梁

## 2. 内部统一协议（IR）

```java
package com.acanx.module.aha.core.llm.protocol;

public enum Role {
    SYSTEM, USER, ASSISTANT, TOOL
}

public record ChatRequest(
    String model,
    List<ChatMessage> messages,
    List<ToolDefinition> tools,
    double temperature,
    int maxTokens,
    boolean stream,
    Map<String, Object> extensions
) {}

public record ChatMessage(
    Role role,
    String content,
    List<ToolCall> toolCalls,
    String toolCallId,
    String reasoningContent
) {}

public record ToolCall(
    String id,
    String name,
    Map<String, Object> arguments
) {}

public record ChatResponse(
    String id,
    String model,
    List<Choice> choices,
    Usage usage,
    Map<String, Object> extensions
) {}

public record Choice(int index, ChatMessage message, String finishReason) {}

public record Usage(int promptTokens, int completionTokens, int totalTokens,
                    Integer reasoningTokens) {}

public record StreamEvent(
    StreamEventType type,
    String contentDelta,
    String reasoningDelta,
    ToolCallDelta toolCallDelta,
    Usage usage,
    String finishReason,
    String errorCode,
    String errorMessage
) {}

public enum StreamEventType {
    CONTENT_DELTA, REASONING_DELTA, TOOL_CALL_DELTA,
    USAGE, DONE, ERROR
}

public final class IrVersion {
    public static final String CURRENT = "1.0";
    private IrVersion() {}
}
```

### 2.1 IR 版本管理

| 变更类型 | 版本号变更 | 迁移要求 |
|---|---|---|
| 新增可选字段 | 次版本号 +1 | 现有适配器无需修改 |
| 新增枚举值 | 次版本号 +1 | 适配器需处理新值 |
| 修改字段类型 | 主版本号 +1 | 所有适配器必须修改 |
| 删除字段 | 主版本号 +1 | 所有适配器必须修改 |

## 3. 适配器 SPI

```java
package com.acanx.module.aha.core.llm;

public interface LlmProviderAdapter {
    String providerId();
    String supportedIrVersion();
    String convertRequest(ChatRequest irRequest, ProviderConfig config);
    ChatResponse convertResponse(String rawResponse, ProviderConfig config);
    StreamEvent convertStreamEvent(String rawEvent, ProviderConfig config);
    String buildUrl(ProviderConfig config);
    Map<String, String> buildHeaders(ProviderConfig config);
}
```

## 4. 内置适配器

### 4.1 OpenAiAdapter

IR 到 OpenAI 格式的**显式转换**：

- `maxTokens` → `max_tokens`
- `toolCalls` → `tool_calls`
- `Role.SYSTEM` → `"system"`
- `Role.TOOL` → `"tool"`

**兼容供应商（部分）** ：

| 供应商 | Base URL |
|---|---|
| OpenAI | `https://api.openai.com/v1` |
| DeepSeek | `https://api.deepseek.com/v1` |
| 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4` |
| Kimi | `https://api.moonshot.cn/v1` |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| Groq | `https://api.groq.com/openai/v1` |
| Ollama | `http://localhost:11434/v1` |
| vLLM | `http://localhost:8000/v1` |

### 4.2 AnthropicAdapter

关键转换：

- `system` 消息提取为顶层字段
- `content` 字符串 → block 数组
- `tool_calls` → `tool_use` block
- `role=tool` → `tool_result` block（放在 `user` content 数组**最前面**）
- 流式：`content_block_delta` / `delta.text` → `CONTENT_DELTA`

### 4.3 GeminiAdapter

关键转换：

- `messages[]` → `contents[]`，`role` 映射为 `user` / `model`
- `system` → `systemInstruction`
- `tools[]` → `functionDeclarations[]`
- 流式：`candidates[0].content.parts[0].text` → `CONTENT_DELTA`

### 4.4 国产大模型默认适配（DeepSeek / 智谱 GLM / 通义千问）

DeepSeek、智谱 GLM、通义千问（Qwen）均对外提供 OpenAI 兼容端点，因此统一由 `OpenAiAdapter` 处理，并在 Core 层内置以下开箱即用的预设（可被用户配置覆盖）：

| 预设 ID | 供应商 | 默认 Base URL | 默认模型 | 关键差异 |
|---|---|---|---|---|
| `DeepSeek` | DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` | 推理模型 `deepseek-reasoner` 返回 `reasoning_content`，映射到 IR 的 `reasoningContent` / `REASONING_DELTA` |
| `BigModelCN` | 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4` | `glm-4.6` | `tool_calls` 与标准 OpenAI 基本一致 |
| `Qwen` | 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-max` | `enable_thinking` 等参数通过 `Extra` 透传 |

**适配要点**：

- 三者均使用 `Authorization: Bearer <api-key>` 鉴权
- 统一的 IR 归一化：`maxTokens`、`toolCalls`、`reasoningContent`、`usage` 均由 `OpenAiAdapter` 转换
- 思维链增量（`reasoning_content`）已并入 IR 的 `REASONING_DELTA`
- 供应商特有参数通过 `Extra` 字段透传，无需修改 IR

### 4.5 中转站兼容（sub2api / new-api）

sub2api、new-api（newapi）等订阅 / 网关中转程序对外暴露 OpenAI 兼容 API，常被用于聚合多个上游供应商。此类端点存在以下差异：

- Base URL 路径不统一，可能带 `/v1`，也可能是自定义前缀
- 部分实现缺失 `usage`、`system_fingerprint` 等字段
- 流式响应末尾可能缺少 `data: [DONE]` 终止标记
- `tool_calls` 增量（delta）聚合方式与标准实现存在差异
- `model` 名称可能被改写为中转站自定义别名
- 可能不支持 `stream_options.include_usage`
- 鉴权信息可能位于 header 或 query 参数

**兼容措施**：

- `OpenAiAdapter` 对未知字段宽松解析（`FAIL_ON_UNKNOWN_PROPERTIES = false`）
- 缺失 `usage` 时以 0 值降级并记录 `WARN`
- 流式解析器容忍 `[DONE]` 缺失，依据连接结束或 `finish_reason` 收尾
- `tool_calls` 增量按 `index` 健壮聚合
- Base URL 由配置完整指定（含路径前缀），适配器不做路径拼接假设
- 鉴权 header / scheme 可通过 `Extra` 自定义

中转站作为普通 `openai-compatible` provider 接入即可（建议写在 `Model.yml`）：

```yaml
Model:
  Default: NewApi          # aha provider use NewApi
  Providers:
    NewApi:
      Adapter: openai-compatible
      BaseUrl: https://my-relay.example.com/v1
      ApiKey: "${AHA_API_KEY_RELAY}"
      Model: gpt-4o
      Extra:
        AuthHeader: Authorization
        AuthScheme: Bearer
```

## 5. LlmClient

```java
package com.acanx.module.aha.core.llm;

public interface LlmClient {
    CompletableFuture<ChatResponse> chat(ChatRequest request);
    void streamChat(ChatRequest request, StreamEventListener listener, CancellationToken token);
}

@FunctionalInterface
public interface StreamEventListener {
    void onEvent(StreamEvent event);
}
```

**DefaultLlmClient 职责**：

- 根据 `ProviderConfig` 选择适配器
- 指数退避重试（默认 3 次，仅 5xx 和超时）
- 连接超时 10s，读取超时按配置（默认 120s）
- 令牌桶速率限制（RPM/TPM）
- SSE 事件解析与状态机
- `CancellationToken` 传播

## 6. 自定义适配器

第三方适配器只需实现 `LlmProviderAdapter` 并在 `module-info.java` 中声明：

```java
provides com.acanx.module.aha.core.llm.LlmProviderAdapter
    with com.example.MyCustomAdapter;
```

---

# 第七部分：Agent 核心设计

## 1. AgentService 接口

```java
package com.acanx.module.aha.core.service;

public interface AgentService {
    String createSession(SessionConfig config);
    void closeSession(String sessionId);

    CompletableFuture<AgentResponse> chat(String sessionId, String input);
    void streamChat(String sessionId, String input, AgentEventListener listener);

    List<ToolDescriptor> listTools();
    ToolResult invokeTool(String toolName, Map<String, Object> params);

    List<MemoryEntry> recall(String sessionId, String query, int limit);

    void shutdown();
}

public record AgentResponse(
    String sessionId,
    String content,
    List<ToolCall> toolCalls,
    Usage usage,
    String finishReason
) {}

@FunctionalInterface
public interface AgentEventListener {
    void onEvent(AgentEvent event);
}
```

## 2. AgentServiceFactory

```java
package com.acanx.module.aha.core.service;

public final class AgentServiceFactory {
    private AgentServiceFactory() {}

    public static AgentService local(AhaConfig config) {
        return new LocalAgentService(config);
    }

    // 未来 daemon 模式预留
    // public static AgentService remote(String endpoint) {
    //     return new RemoteAgentService(endpoint);
    // }
}
```

## 3. AgentEngine 推理循环

```java
package com.acanx.module.aha.core.agent;

public final class AgentEngine {
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final MemoryStore memoryStore;
    private final EventBus eventBus;

    public AgentResponse run(String sessionId, String input, CancellationToken token) {
        // 1. 触发 PreStep 事件
        // 2. 加载会话历史
        // 3. 构建 ChatRequest
        // 4. 触发 Request 事件（waterfall，可修改）
        // 5. 调用 LLM（非流式）
        // 6. 循环处理工具调用（触发 ToolPreExecute / ToolPostExecute 事件）
        // 7. 持久化消息
        // 8. 触发 SessionFlush 事件
        // 9. 返回 AgentResponse
    }

    public void stream(String sessionId, String input,
                       AgentEventListener listener, CancellationToken token) {
        // 流式版本，通过 listener 逐步回调
        // LLM 流式事件触发 LlmStream 事件（waterfall，可变换）
    }
}
```

## 4. 线程模型

- **每个会话一个虚拟线程**：Agent 推理循环在独立虚拟线程中运行
- **工具并行调用**：`Executors.newVirtualThreadPerTaskExecutor()`
- **禁止池化虚拟线程**
- **结构化并发**：JDK 25 中 `StructuredTaskScope` 仍为预览特性，0.1 不使用

## 5. Agent 身份与系统提示词

Agent 的“角色身份”由 **system 消息**承载，来源可配置且可持久化。

### 5.1 组装顺序与优先级（唯一权威说明）

身份文本由**多个来源叠加**而成。顺序即优先级：**越靠后越具体**，冲突时以后者为准。

#### 5.1.1 组装规则

| 层 | 取哪些文件 | 顺序 |
|---|---|---|
| 1 | **用户级目录**（`AHA_HOME`，未设置时 `~/.aha`） | 该目录下**全部**候选，按 `Agent.PromptFiles` 顺序 |
| 2 | **项目级**：从工作目录逐级向上，**第一个有命中的目录**；**止步于用户主目录** | 该目录下**全部**候选，按 `Agent.PromptFiles` 顺序 |
| 3 | `Aha.Agent.SystemPrompt`（配置内联） | **兜底**：只有 1、2 都没命中时才用 |
| 4 | `SystemPromptLoader.BUILTIN_IDENTITY` | 最后一道：配置整个加载失败时 |
| 5 | 运行环境块（操作系统 / shell / 工作目录） | 由 `AgentEngine.prepare` 每轮追加，**永远在最后** |

两条容易踩的规则：

- **同一层级内全部候选都生效**（不是只取第一个）。`AHA.md` 与 `AGENTS.md` 并存时都注入，
  顺序按 `Agent.PromptFiles`；只取一个会让用户以为写了却没生效。
- **层级之间叠加**。个人全局偏好（「一律用简体中文」这类）不该因为项目里恰好有个
  `AGENTS.md` 就整段失效。

`Agent.PromptFiles` 默认 `[AHA.md, AGENTS.md, CLAUDE.md]`。

两条与「层次」相关的边界约定：

- **`AHA_HOME` 的取值顺序是 `-DAHA_HOME` → 环境变量 `AHA_HOME` → `~/.aha`**（系统属性在前）。
  显式传入的 JVM 属性属于「本次调用」，比环境里长期存在的变量更具体；这也让测试与集成场景
  能确定性地覆盖用户目录，而不是读写开发机真实的主目录。
  （`${AHA_HOME:-~/.aha}` 这类占位符的展开同序。）
- **向上的项目级查找止步于用户主目录**（`user.home`，同时参考 `USERPROFILE` / `HOME`）。
  到这一级就停，不再把更上层当作「项目级」——否则 `~/AHA.md` 会被当成项目级身份，
  它既绕过用户级目录（`~/.aha`），又会让「用户级 / 项目级」的来源标注失真。
  该边界在 Windows 上尤其关键：`%LOCALAPPDATA%\Temp` 位于主目录之下，
  没有边界时临时目录里的任何运行都会沿路读到主目录里的身份文件。

#### 5.1.2 完整示例

假设下面这些文件全部存在（`Agent.PromptFiles` 为默认值）：

| 位置 | 文件 | 是否注入 | 原因 |
|---|---|---|---|
| 用户级 | `~/.aha/AHA.md` | ✅ 第 1 段 | 候选顺序第一 |
| 用户级 | `~/.aha/AGENTS.md` | ✅ 第 2 段 | 同层候选，排 `AHA.md` 之后 |
| 项目级 | `<项目>/AHA.md` | 若存在则排最前 | 同层内按候选名顺序，`AHA.md` 优先级最高 |
| 项目级 | `<项目>/AGENTS.md` | ✅ 第 3 段 | 就近命中目录中的候选 |
| 项目级 | `<项目>/CLAUDE.md` | ✅ 第 4 段 | 已在默认候选列表里（Claude Code 兼容） |
| 配置 | `Aha.Agent.SystemPrompt` | ❌ | 已有身份文件，兜底不参与 |
| — | `GEMINI.md` / `.cursorrules` 等 | ❌ | 不在候选列表，**但会在启动时提示** |

**内容完全相同的文件只注入一次**：同一份约定被复制成 `AGENTS.md` 与 `CLAUDE.md`
（或软链）是常见情况，不去重会让同样的指令出现两遍。

实测的 system 消息片段顺序：

```
1. 【用户级 AHA.md】
2. 【用户级 AGENTS.md】
3. 【项目级 AGENTS.md】
4. 【项目级 CLAUDE.md】
5. 运行环境（以下均为事实，不必再向用户确认）：…
```

#### 5.1.3 新增兼容文件：只改配置

`Agent.PromptFiles` **同时决定读路径（候选名）与写路径**（`/memory` 的默认文件名），
因此新增一个兼容文件（`GEMINI.md`、`.cursorrules` 等）**不需要改代码**：

```yaml
Aha:
  Agent:
    PromptFiles:
      - AHA.md
      - AGENTS.md
      - CLAUDE.md
      - GEMINI.md      # 追加即生效；排得越后优先级越低
```

**未列入候选但确实存在的已知兼容文件会在启动时提示**（`GEMINI.md`、`.cursorrules`、
`CODEX.md`、`CLAUDE.md`、`CLAUDE.local.md`），做法是列出文件并指明如何加入候选——
静默忽略是最糟的处理方式，用户会以为已经生效。

代价：候选列表追加一行。同一层级内的优先级由列表顺序决定。

#### 5.1.4 顺序必须可见（不做黑箱）

顺序决定谁覆盖谁，用户看不到就无从判断自己写的那份有没有生效。因此三处都展示：

| 位置 | 展示内容 |
|---|---|
| `aha chat` 启动信息 | `身份顺序` 块：逐个列出「层级 + 路径 + 字符数」，并说明兜底未使用 |
| `/memory view` | 按文件分节打印正文，用户级在前 |
| `/memory files` | 全部候选（含不存在者）与命中标记 |

启动信息实测：

```
  身份来源 用户级 + 项目级（叠加，80 字符）
  身份顺序 后加载者优先级更高；同一层级按候选名顺序
    1. 用户级 /home/x/.aha/AHA.md（25 字符）
    2. 用户级 /home/x/.aha/AGENTS.md（25 字符）
    3. 项目级 /repo/AGENTS.md（29 字符）
            Aha.Agent.SystemPrompt（配置内联）仅在无身份文件时兜底，本次未使用
```

> **桌面端要求**：0.2 的桌面端必须同样展示这块信息（启动页或设置页），
> 不能只在 CLI 可见。见 `GUIDesign.md`。

#### 5.1.5 AHA 侧的组装流水线（六步，可复现）

**结论：系统提示词占第一个片段，之后每个身份文件各占一个片段，运行环境块单独一个片段；
按 §5.1.1 的顺序排在会话历史最前面，互不拼接。**

| 步骤 | 位置 | 做什么 |
|---|---|---|
| 1 | `AhaDefault.yaml` / 用户 `Aha.yaml` | 读 `Agent.PromptFiles`（候选名，顺序即优先级）与 `Agent.SystemPrompt`（内联兜底） |
| 2 | `SystemPromptLoader.resolve(explicitText, explicitFile, workingDir, promptFiles, inlineFallback)` | 按 §5.1.1 收集来源；显式 `--system` / `--system-file` **独占**，不再叠加文件 |
| 3 | 同上，内部 `readAllNonBlank(dir, names)` | 每个层级读该目录下**全部**候选（按候选名顺序），空文件跳过 |
| 4 | 同上，内部 `dedupe(parts)` | 内容完全相同的文件只保留第一份 |
| 5 | `CliSession.open` | 把片段固化到会话配置：`SessionConfig.extras["PromptSegments"] = List<String>`（`SystemPromptLoader.SEGMENTS_KEY`） |
| 6 | `AgentEngine.prepare` | 逐片段生成 `system` 消息，再追加运行环境块，最后才是历史与本轮 user 消息 |

各步骤的产物：

```java
// 步骤 2–4 的产物
ResolvedPrompt{
    text      = "片段1\n\n片段2\n\n片段3"   // 仅供展示与 /context 计字数
    source    = "files:/home/x/.aha/AHA.md;/repo/AGENTS.md"   // 单份时 "file:<路径>"
    segments  = ["片段1", "片段2", "片段3"]     // 真正发给模型的东西
}
```

```java
// 步骤 6 的产物（prepare 方法逐行对应）
for (String segment : identitySegments(config)) {
    messages.add(ChatMessage.text(Role.SYSTEM, segment));      // ← 一个片段一条
}
messages.add(ChatMessage.text(Role.SYSTEM, SystemPromptLoader.environmentBlock()));
messages.addAll(memoryStore.loadHistory(sessionId, maxContextEntries));
messages.add(ChatMessage.text(Role.USER, input));
```

实测（用户级 `AHA.md` + `AGENTS.md`，项目级 `AGENTS.md` + `CLAUDE.md`）：

```
0. system   【用户级 AHA.md】个人偏好：简体中文
1. system   【用户级 AGENTS.md】个人偏好：回答简洁
2. system   【项目级 AGENTS.md】项目约定：Maven
3. system   【项目级 CLAUDE.md】Claude 兼容
4. system   运行环境（以下均为事实，不必再向用户确认）：…
5. user     你好
```

**边界与兼容**：

| 场景 | 行为 |
|---|---|
| 身份文件为空 | 跳过该文件，不生成空片段 |
| 多份内容完全相同 | 只保留第一份（同一约定被复制成 `AGENTS.md` 与 `CLAUDE.md` 是常见情况） |
| 一份身份文件都没有 | 用 `Agent.SystemPrompt`；再空则用 `SystemPromptLoader.BUILTIN_IDENTITY` |
| 运行环境块 | **永远存在**，即使没有任何身份文件 |
| **旧会话**（`extras` 无 `PromptSegments`） | 退回单条 `system`（`config.systemPrompt()`），保证向后兼容 |
| 续接会话（`--session <id>`） | 片段从会话配置恢复，仍按片段发送 |

**为什么必须保边界**：拼接成一段后，模型再也无法区分「哪句话来自哪个文件」，也就无法理解
「用户级偏好 vs 项目约定」这层语义。独立成条后，每个片段在协议上都是有边界的对象。

#### 5.1.6 三家协议的线上形态

三种协议对 system 的承载方式完全不同，**双向的完整 JSON 结构见 §5.5**。这里的要点：

| 提供方 | 内在模型里的 `Role.SYSTEM` 消息 | 线上落点 |
|---|---|---|
| OpenAI 兼容 | 保持为消息 | `messages[].role = "system"` |
| Anthropic | 从消息序列中**取出** | 顶层 `system` 的 text 块数组 |
| Gemini | 从消息序列中**取出** | 顶层 `systemInstruction.parts[]` |

**关键点**：Anthropic 与 Gemini 的 `system` 是**顶层单字段**，不是消息序列里的一员。
若像早期实现那样把多段拼成一个字符串塞进去，片段边界在**线上就永久丢失**了。

#### 5.1.7 如何复现上述结论

**验证线上请求体**（三家一次跑完，输出即上面三段 JSON）：

```java
// 用 dist/lib 作为 classpath，构造同样的 ChatRequest 再调各适配器
ChatRequest request = new ChatRequest("gpt-4o",
        List.of(ChatMessage.text(Role.SYSTEM, "片段1"),
                ChatMessage.text(Role.SYSTEM, "片段2"),
                ChatMessage.text(Role.USER, "你好")),
        List.of(), 0.0, 256, false, Map.of());
System.out.println(new OpenAiAdapter().convertRequest(request, null));
System.out.println(new AnthropicAdapter().convertRequest(request, null));
System.out.println(new GeminiAdapter().convertRequest(request, null));
```

**验证 AHA 的组装结果**：

| 手段 | 看到什么 |
|---|---|
| `aha chat` 启动信息 | 「身份顺序」块 = 步骤 2–4 的产物（层级 + 路径 + 字符数，按注入顺序） |
| `/memory view` | 按文件分节打印正文，用户级在前 |
| `/memory files` | 全部候选（含不存在者）与命中标记 |
| **本地 mock / 代理** | 真实请求体：应看到 N 条 `system` 消息而非一条 |

> 注意：AHA 自身的日志只记录**请求 URL 与提供方**（`DefaultLlmClient` 的 DEBUG 行），
> **不记录请求体**，所以想核对线上形态要挂一个本地 mock 或抓包代理。

**钉住这些行为的单元测试**：

| 测试 | 钉住什么 |
|---|---|
| `SystemPromptLoaderTest#userLevelKeepsAllCandidatesBeforeProjectLevel` | 层级顺序与同层全部生效 |
| `SystemPromptLoaderTest#sameLevelFilesAreAllInjectedInCandidateOrder` | 同层全部候选 + 候选顺序 |
| `AgentEngineTest#eachIdentitySegmentBecomesItsOwnSystemMessage` | 一段一条 `system` 消息 |
| `AgentEngineTest#systemMessageCarriesRuntimeEnvironment` | 运行环境块独立成条 |
| `AnthropicAdapterTest#eachSystemSegmentBecomesItsOwnBlock` | Anthropic 块数组 |
| `GeminiAdapterTest#eachSystemSegmentBecomesItsOwnPart` | Gemini 多 parts |

#### 5.1.8 来源描述格式

| 命中文件数 | `source` 格式 |
|---|---|
| 1 | `file:<路径>` |
| ≥2 | `files:<低优先级>;<高优先级>`（顺序即注入顺序） |

单份内联 / 内置分别是 `Aha.Agent.SystemPrompt` 与 `builtin`。
展示层一律用 `SystemPromptLoader.filePaths(source)` 解析，**不要只报优先级最高那份**。

#### 5.1.9 来源对模型是可见的

因为每个来源独立成条 `system` 消息（§5.1.5），**模型能够区分**某段内容来自用户级还是项目级。
配合「越靠后越具体」的顺序，冲突时模型有明确依据——比如用户级写「一律用简体中文」、
项目级写「提交信息用英文」，模型能看出这是两个不同层级的约定，而不是同一段互相矛盾的指令。

当前**不**在每个片段前加「以下来自 `AGENTS.md`」这类小标题：片段边界已经承载了这个信息，
再加标题属于重复注入元数据，也让提示词内容不再与用户写的文件逐字对应。

### 5.2 注入与“不重复”语义

```mermaid
flowchart LR
    A[创建会话] --> B[解析提示词来源]
    B --> C[固化到 SessionConfig]
    C --> D[写入 session.config_json]
    D --> E[每轮请求前按片段逐条注入 system 消息]
```

- **会话创建时**解析一次并固化（含来源判定与文件读取）
- **同一会话内**每轮直接复用 `SessionConfig.systemPrompt()`，不重复读文件、不重复解析
- **每轮请求仍需携带 system 消息**：LLM API 是无状态的，不发 system 就会失去身份设定。
  所谓“不重复注入”指的是**不重复配置**，而非不发送
- **复用会话**（`--session <id>`）时沿用已固化的身份，不会重新解析
- **进程重启后**可从 `session.config_json` 恢复

### 5.3 会话持久化

| 项 | 说明 |
|---|---|
| 存储位置 | SQLite `session.config_json` |
| 序列化 | `SessionManager` 手动构造 PascalCase JSON（`Model` / `SystemPrompt` / `Extras`） |
| 恢复 | `SessionManager.get` 内存未命中时从存储回填缓存 |
| `closeSession` | 结束活跃会话、释放资源，**保留持久化记录**，仍可恢复继续 |

> **契约说明**：`closeSession` 不删除记录，而是结束活跃状态。会话持久化的价值就在于可续，
> 若关闭即删除，`--session <id>` 恢复将失去意义。需要彻底删除会话时应显式调用存储层删除。

### 5.4 运行环境注入

内置身份声明之后**总是**追加一段「运行环境」，由 `SystemPromptLoader.environmentBlock()` 生成，
在 `AgentEngine.prepare` 每轮拼装 system 消息时加上：

```
运行环境（以下均为事实，不必再向用户确认）：
- 操作系统：Windows 11 (amd64)
- 命令执行方式：cmd.exe /c（不是 bash / sh）
- 可用命令：dir / type / findstr / where / powershell；没有 head、grep、sed、awk、/dev/null
- 路径分隔符：\（反斜杠）
- 需要更强能力时显式调用：powershell -NoProfile -Command "..."
- 工作目录：E:\GitRepo\GitHub\ACANX\AHA
```

非 Windows 时对应为 `命令执行方式：/bin/sh -c` 与 `路径分隔符：/`。

**为什么必须注入**：身份声明里没有平台信息，模型只能靠猜。实测它会发出
`grep -rn ... 2>/dev/null | head -200` 这类 Unix 写法，在 Windows 的 `cmd.exe` 下必然失败，
而报错是 `'X' is not recognized as an internal or external command` —— 从报错**完全看不出**
是「平台不匹配」，排查成本极高。

| 设计点 | 理由 |
|---|---|
| 追加在**身份声明之后**，而非并入 `BUILTIN_IDENTITY` | 用户自定义的 `AGENTS.md` 不必自己维护这些事实；换成自定义身份后环境信息依然在 |
| 内容**每轮重新生成**（不固化进 `SessionConfig`） | 环境是客观事实且几乎不变，但重新生成可避免把「旧机器的路径」带进新会话 |
| 用户未配身份时也注入 | 缺身份不能连带缺环境信息 |
| 工作目录取 `user.dir` 而非项目根 | 与工具执行 shell 命令时的实际目录一致（两者可能不同） |

### 5.5 双向消息结构（JSON 层面，实测）

这一节把「内部模型 ↔ 三家协议」的双向映射摊开成 JSON，便于对照与复现。
所有报文均取自真实转换结果与测试 fixture（`aha-core/src/test/resources/Fixtures/`）。

#### 5.5.1 AHA 的内部中间表示

三家协议先统一成一套内部模型，适配器只做**两个方向**的转换。

```jsonc
// ChatRequest —— 发出去之前的样子
{
  "model": "gpt-4o",                  // 由 Model.yml 决定
  "messages": [                        // 身份片段在前，历史在中，本轮 user 在最后
    {"role": "system", "content": "【用户级 AHA.md】个人偏好：简体中文"},
    {"role": "system", "content": "【项目级 AGENTS.md】项目约定：Maven"},
    {"role": "system", "content": "运行环境：Linux (amd64)；/bin/sh -c"},
    {"role": "user",   "content": "你好"}
  ],
  "tools": [],                         // ToolDefinition：name / description / parameters
  "temperature": 0.0,
  "maxTokens": 256,
  "stream": false,
  "extensions": {}
}

// ChatMessage —— 一条消息的五个字段
{
  "role": "assistant",                 // SYSTEM / USER / ASSISTANT / TOOL
  "content": "…",
  "toolCalls": [],                     // ToolCall{id, name, arguments}
  "toolCallId": null,                  // 仅 TOOL 角色：回填哪个工具调用
  "reasoningContent": null             // 思维链，不持久化也不回传
}

// ChatResponse —— 非流式统一结果
{
  "id": "chatcmpl-x", "model": "gpt-4o",
  "choices": [{"index": 0, "message": {…ChatMessage…}, "finishReason": "stop"}],
  "usage": {"promptTokens": 10, "completionTokens": 5, "totalTokens": 15, "reasoningTokens": null},
  "extensions": {}
}

// StreamEvent —— 流式统一事件（type 决定其余字段谁有值）
{
  "type": "CONTENT_DELTA",             // CONTENT_DELTA / REASONING_DELTA / TOOL_CALL_DELTA / USAGE / DONE / ERROR
  "contentDelta": "Hello",
  "reasoningDelta": null,
  "toolCallDelta": null,               // ToolCallDelta：流式分片拼装的工具调用
  "usage": null,
  "finishReason": null
}
```

#### 5.5.2 请求方向：内部 → 三家

`ChatRequest` 经 `convertRequest` 转换成各家的请求体。三家的请求体结构差异如下——
**注意模型名位置不同**：

| 提供方 | 端点（`BaseUrl` 之后拼接） | 模型名位置 | 鉴权头 |
|---|---|---|---|
| OpenAI 兼容 | `/chat/completions` | **请求体** `model` | `Authorization: Bearer <key>` |
| Anthropic | `/v1/messages` | **请求体** `model` | `x-api-key` + `anthropic-version` |
| Gemini | `/v1beta/models/{model}:generateContent` | **URL 路径**，请求体内没有 `model` | `x-goog-api-key` |

> **流式与端点无关**：三家都用**同一个端点**，流式与否由请求体 / 头决定——
> OpenAI 用 `"stream": true`，Anthropic 用 `"stream": true`，Gemini 用 `Accept: text/event-stream`
> （不是 `:streamGenerateContent`）。请求体示例均为非流式形态。

**OpenAI 兼容** —— 一条 `system` 消息一个片段：

```json
{
 "model": "gpt-4o",
 "messages": [
  {"role": "system", "content": "【用户级 AHA.md】个人偏好：简体中文"},
  {"role": "system", "content": "【项目级 AGENTS.md】项目约定：Maven"},
  {"role": "system", "content": "运行环境：Linux (amd64)；/bin/sh -c"},
  {"role": "user",   "content": "你好"}
 ],
 "max_tokens": 256,
 "stream": false
}
```

**Anthropic** —— `system` 顶层字段，片段是**块数组**；`messages` 里不放 system：

```json
{
 "model": "claude-sonnet-5-1",
 "messages": [
  {"role": "user", "content": [{"type": "text", "text": "你好"}]}
 ],
 "system": [
  {"type": "text", "text": "【用户级 AHA.md】个人偏好：简体中文"},
  {"type": "text", "text": "【项目级 AGENTS.md】项目约定：Maven"},
  {"type": "text", "text": "运行环境：Linux (amd64)；/bin/sh -c"}
 ],
 "max_tokens": 256
}
```

**Gemini** —— `systemInstruction` 顶层字段，片段是多个 `parts`；模型名在 URL：

```
POST {BaseUrl}/v1beta/models/gemini-3.5-flash:generateContent
```

```json
{
 "contents": [
  {"role": "user", "parts": [{"text": "你好"}]}
 ],
 "systemInstruction": {
  "parts": [
   {"text": "【用户级 AHA.md】个人偏好：简体中文"},
   {"text": "【项目级 AGENTS.md】项目约定：Maven"},
   {"text": "运行环境：Linux (amd64)；/bin/sh -c"}
  ]
 },
 "generationConfig": {"maxOutputTokens": 256}
}
```

#### 5.5.3 响应方向（非流式）：三家 → 内部

三家响应报文（测试 fixture 原样）：

**OpenAI 兼容**

```json
{
 "id": "chatcmpl-test", "object": "chat.completion", "model": "gpt-4o",
 "choices": [{"index": 0,
   "message": {"role": "assistant", "content": "Hello from OpenAI"},
   "finish_reason": "stop"}],
 "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
}
```

**Anthropic**

```json
{
 "id": "msg_test", "type": "message", "role": "assistant",
 "model": "claude-sonnet-5-1",
 "content": [{"type": "text", "text": "Hello from Anthropic"}],
 "stop_reason": "end_turn",
 "usage": {"input_tokens": 10, "output_tokens": 5}
}
```

**Gemini**

```json
{
 "candidates": [{
   "content": {"role": "model", "parts": [{"text": "Hello from Gemini"}]},
   "finishReason": "STOP"}],
 "usageMetadata": {"promptTokenCount": 10, "candidatesTokenCount": 5, "totalTokenCount": 15}
}
```

字段映射（→ 内部 `ChatResponse`）：

| 内部字段 | OpenAI | Anthropic | Gemini |
|---|---|---|---|
| `id` | `id` | `id` | 无（置空） |
| `model` | `model` | `model` | 无（置空） |
| 正文 | `choices[].message.content` | `content[].text`（拼接所有 text 块） | `candidates[].content.parts[].text` |
| `finishReason` | `choices[].finish_reason` | `stop_reason` | `candidates[].finishReason` |
| `usage.promptTokens` | `usage.prompt_tokens` | `usage.input_tokens` | `usageMetadata.promptTokenCount` |
| `usage.completionTokens` | `usage.completion_tokens` | `usage.output_tokens` | `usageMetadata.candidatesTokenCount` |
| `usage.totalTokens` | `usage.total_tokens` | 无（由 `input + output` 相加） | `usageMetadata.totalTokenCount` |
| `usage.reasoningTokens` | `usage.completion_tokens_details.reasoning_tokens` | 无 | 无 |
| 工具调用 | `choices[].message.tool_calls[]` | `content[].type == "tool_use"` | `candidates[].content.parts[].functionCall` |

#### 5.5.4 响应方向（流式）：三家 → 内部

各家流式都是 SSE，但**载荷结构各不相同**（fixture 里是逐条 `data:` 的内容）：

**OpenAI 兼容** —— `delta` 增量，末尾 `[DONE]`：

```
data: {"choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
data: {"choices":[{"index":0,"delta":{"content":" world"},"finish_reason":null}]}
data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}
data: {"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
data: [DONE]
```

**Anthropic** —— 以载荷里的 `type` 区分（另有 `event:` 行，AHA 只看载荷）：

```
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}
```

**Gemini** —— 每片是一个完整 `candidates` 结构，不是 `delta`：

```
data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Hello"}]}}]}
data: {"candidates":[{"content":{"role":"model","parts":[{"text":" world"}]},"finishReason":"STOP"}]}
```

映射到内部 `StreamEvent`：

| 内部 `type` | OpenAI | Anthropic | Gemini |
|---|---|---|---|
| `CONTENT_DELTA` | `choices[].delta.content` | `content_block_delta` + `delta.text` | `candidates[].content.parts[].text` |
| `REASONING_DELTA` | `choices[].delta.reasoning_content` | `content_block_delta` + `delta.thinking` | **未映射**（Gemini 的 thinking 尚未接入） |
| `TOOL_CALL_DELTA` | `choices[].delta.tool_calls[]` | `content_block_start` / `input_json_delta` | `parts[].functionCall` |
| `USAGE` | 独立的 usage 片 | `message_delta.usage` | `usageMetadata` |
| `DONE` | `[DONE]` 或 `finish_reason` | `message_stop` | `finishReason` |
| `ERROR` | HTTP 状态码 / `error` 对象 | 同左 | 同左 |

#### 5.5.5 如何复现

**请求方向**：构造内部 `ChatRequest` 后直接调三家适配器，打印即得上文请求体：

```java
ChatRequest request = new ChatRequest("gpt-4o",
        List.of(ChatMessage.text(Role.SYSTEM, "片段1"),
                ChatMessage.text(Role.SYSTEM, "片段2"),
                ChatMessage.text(Role.USER, "你好")),
        List.of(), 0.0, 256, false, Map.of());
System.out.println(new OpenAiAdapter().convertRequest(request, null));
System.out.println(new AnthropicAdapter().convertRequest(request, null));
System.out.println(new GeminiAdapter().convertRequest(request, null));
```

**响应方向**：直接读 fixture，走 `convertResponse` / `convertStreamEvent`：

```
aha-core/src/test/resources/Fixtures/
  OpenAiChatResponse.json      OpenAiStreamEvents.json
  AnthropicChatResponse.json   AnthropicStreamEvents.json
  GeminiChatResponse.json      GeminiStreamEvents.json
```

**钉住这些结构的测试**：`OpenAiAdapterTest`、`AnthropicAdapterTest`、`GeminiAdapterTest`
（含 `eachSystemSegmentBecomesItsOwnBlock` / `eachSystemSegmentBecomesItsOwnPart` 等）。


## 6. 取消传播

```java
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            listeners.forEach(Runnable::run);
        }
    }
    public boolean isCancelled() { return cancelled.get(); }
    public void onCancel(Runnable listener) {
        if (cancelled.get()) { listener.run(); }
        else { listeners.add(listener); }
    }
}
```

---

# 第八部分：工具系统

## 1. 权限模型

| 权限级别 | 说明 | 默认行为 |
|---|---|---|
| `READ` | 读取文件、环境变量 | 自动批准 |
| `WRITE` | 写入文件、修改配置 | CLI 提示确认；桌面端弹窗 |
| `NETWORK` | HTTP 请求 | 自动批准（可配置白名单） |
| `EXECUTE` | 执行外部命令 | 必须显式确认，不可默认批准 |
| `ADMIN` | 修改系统配置、安装工具 | 必须显式确认 |

## 2. 工具发现

```java
public final class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        ServiceLoader.load(ToolProvider.class)
            .forEach(p -> p.tools().forEach(t -> tools.put(t.name(), t)));
    }

    public ToolResult invoke(String name, Map<String, Object> params, CancellationToken token) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new ToolExecutionException("UNKNOWN_TOOL", "Unknown tool: " + name);
        }
        // 权限检查
        return tool.execute(params, token);
    }
}
```

## 3. MCP 适配器（接口预留）

```java
package com.acanx.module.aha.core.mcp;

public interface McpAdapter {
    void connect(String serverUrl);
    List<ToolDescriptor> discoverTools();
    ToolResult invokeTool(String toolName, Map<String, Object> params);
    void disconnect();
}
```

0.1 版本不提供实现，仅定义接口。

---

# 第九部分：配置与安全

## 1. 配置文件分离（自 0.1.0）

配置分为两份文件，职责分离：

| 文件 | 职责 | 位置 |
|---|---|---|
| `Aha.yaml` | 运行参数 + 兜底（降级回退）模型 | 项目 `./Aha.yaml`，缺省 classpath `AhaDefault.yaml` |
| `Model.yml` | 模型供应商明细 + 当前默认供应商 | `./Model.yml`，其次 `$AHA_HOME/Model.yml`，缺省 classpath `ModelDefault.yml` |

主配置不再承载供应商明细，只声明兜底模型与模型文件路径：

```yaml
Aha:
  Llm:
    Fallback:                                  # 兜底（降级回退）模型
      Provider: OpenAI
      Model: gpt-4o
    ModelFile: "${AHA_HOME:-~/.aha}/Model.yml"       # 模型配置文件路径
```

### 1.1 合并优先级

```
内置预设（classpath:ModelDefault.yml）
    ↓ 覆盖
主配置内联 Llm.Providers（兼容用，可选）
    ↓ 覆盖
Model.yml
    ↓ 覆盖
环境变量（${ENV} / ${ENV:-default}）
```

默认供应商选取顺序：`Model.yml.Default` → 主配置 `Llm.DefaultProvider` → `OpenAI`。

### 1.2 兜底与降级回退

- 默认供应商不存在（或不可用）→ 回退到 `Llm.Fallback.Provider`
- `Llm.Fallback.Model` 非空时覆盖兜底供应商自身配置的模型名
- 未解析的环境变量占位符（如 `${AHA_API_KEY_XXX}`）保留原文，便于诊断与提示

### 1.3 首次初始化与供应商管理

```bash
# 首次使用：生成 Model.yml 与目录结构（无需手工编写配置）
aha init                           # 交互式：列出内置供应商供选择，可写入 API Key
aha init --provider DeepSeek       # 非交互：指定默认供应商
aha init --no-input                # 非交互：仅生成内置全量配置

# 日常管理
aha provider list                 # 列出供应商，标记当前默认
aha provider use DeepSeek         # 一键切换默认供应商
aha provider add MyProxy --adapter openai-compatible \
    --base-url https://proxy.example/v1 --model gpt-4o-mini [--default]
aha provider remove MyProxy -y    # 删除（需显式确认）
```

新增与切换均写回 `Model.yml`，不修改主配置。

### 1.4 运行时覆盖顺序

```
命令行参数
    ↓ 覆盖
环境变量（AHA_*）
    ↓ 覆盖
Model.yml / Aha.yaml
    ↓ 覆盖
内置默认（ModelDefault.yml / AhaDefault.yaml）
```

## 2. 配置模型

```java
package com.acanx.module.aha.core.config;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AhaConfig(
    @JsonProperty("Llm") LlmConfig llm,
    @JsonProperty("Memory") MemoryConfig memory,
    @JsonProperty("Tools") ToolsConfig tools,
    @JsonProperty("Security") SecurityConfig security,
    @JsonProperty("Logging") LoggingConfig logging,
    @JsonProperty("Extensions") ExtensionsConfig extensions
) {}

// 主配置中的 LLM 段：只保留兜底模型与文件路径
public record LlmConfig(
    @JsonProperty("DefaultProvider") String defaultProvider,   // 内联兼容，可选
    @JsonProperty("Providers") Map<String, ProviderConfig> providers, // 内联兼容，可选
    @JsonProperty("Fallback") FallbackConfig fallback,
    @JsonProperty("ModelFile") String modelFile
) {}

public record FallbackConfig(
    @JsonProperty("Provider") String provider,
    @JsonProperty("Model") String model
) {}

// Model.yml 的根模型
public record ModelConfig(
    @JsonProperty("Default") String defaultProvider,
    @JsonProperty("Providers") Map<String, ProviderConfig> providers
) {}

public record ProviderConfig(
    @JsonProperty("Adapter") String adapter,
    @JsonProperty("BaseUrl") String baseUrl,
    @JsonProperty("ApiKey") String apiKey,
    @JsonProperty("Model") String model,
    @JsonProperty("TimeoutSeconds") int timeoutSeconds,
    @JsonProperty("MaxRetries") int maxRetries,
    @JsonProperty("RateLimit") RateLimitConfig rateLimit,
    @JsonProperty("Extra") Map<String, Object> extra
) {}

public record RateLimitConfig(
    @JsonProperty("Rpm") int rpm,
    @JsonProperty("Tpm") int tpm
) {}

public record MemoryConfig(
    @JsonProperty("Storage") String storage,
    @JsonProperty("Path") String path,
    @JsonProperty("MaxContextEntries") int maxContextEntries
) {}

public record ToolsConfig(
    @JsonProperty("Enabled") List<String> enabled,
    @JsonProperty("Shell") ShellConfig shell
) {}

public record ShellConfig(
    @JsonProperty("AllowedCommands") List<String> allowedCommands,
    @JsonProperty("TimeoutSeconds") int timeoutSeconds
) {}

public record SecurityConfig(
    @JsonProperty("KeyStore") String keyStore,
    @JsonProperty("KeyStorePath") String keyStorePath
) {}

public record LoggingConfig(
    @JsonProperty("Level") String level,
    @JsonProperty("File") String file
) {}

public record ExtensionsConfig(
    @JsonProperty("Enabled") boolean enabled,
    @JsonProperty("Path") String path,
    @JsonProperty("AutoLoad") boolean autoLoad,
    @JsonProperty("Disabled") List<String> disabled,
    @JsonProperty("Settings") Map<String, Map<String, Object>> settings
) {}
```

## 3. 配置文件示例

### 3.1 主配置 `Aha.yaml`

```yaml
Aha:
  Llm:
    # 兜底（降级回退）模型
    Fallback:
      Provider: OpenAI
      Model: gpt-4o
    # 模型供应商配置路径（用户级目录：含 API Key，属凭据类配置）
    ModelFile: "${AHA_HOME:-~/.aha}/Model.yml"

  Memory:
    Storage: sqlite
    
    Path: '${AHA_HOME:-~/.aha}/Data/Aha.db'
    MaxContextEntries: 100

  Tools:
    Enabled:
      - file-read
      - file-write
      - http-get
    AutoApprove: []
    Shell:
      AllowedCommands: []
      TimeoutSeconds: 30

  Security:
    KeyStore: encrypted-file
    KeyStorePath: '${AHA_HOME:-~/.aha}/Key/Aha.keystore'

  Logging:
    Level: INFO
    File: '${AHA_HOME:-~/.aha}/Log/AHA.log'

  Extensions:
    Enabled: true
    Path: '${AHA_HOME:-~/.aha}/Extension'
    AutoLoad: true
    Disabled:
      - com.example.legacy
    Settings:
      com.example.weather:
        ApiKey: "${AHA_API_KEY_WEATHER}"
        City: Beijing
```

### 3.2 模型配置 `Model.yml`

> 供应商明细与当前默认供应商集中在此文件；`aha provider use/add/remove` 均写回本文件。
> 默认位置为 `$AHA_HOME/Model.yml`，未设 `AHA_HOME` 时为 `~/.aha/Model.yml`；
> 不读取工作目录下的 `./Model.yml`（模型配置含 API Key，且相对路径会随 CWD 变化），
> 需项目级配置时在主配置中显式设置 `Llm.ModelFile`。
> 若文件不存在，使用 classpath 的 `ModelDefault.yml`（内置 6 个供应商）。

```yaml
Model:
  # 当前默认（选中）的供应商：aha provider use DeepSeek
  Default: DeepSeek

  Providers:
    OpenAI:
      Adapter: openai-compatible
      BaseUrl: "https://api.openai.com/v1"
      ApiKey: "${AHA_API_KEY_OPENAI}"
      Model: gpt-4o
      TimeoutSeconds: 120
      MaxRetries: 3
      RateLimit:
        Rpm: 60
        Tpm: 100000

    DeepSeek:
      Adapter: openai-compatible
      BaseUrl: https://api.deepseek.com/v1
      ApiKey: "${AHA_API_KEY_DEEPSEEK}"
      Model: deepseek-chat
      TimeoutSeconds: 120
      MaxRetries: 3
      RateLimit:
        Rpm: 60
        Tpm: 100000

    # 中转站 / 自建代理
    MyProxy:
      Adapter: openai-compatible
      BaseUrl: https://proxy.example/v1
      ApiKey: "${AHA_API_KEY_MY_PROXY}"
      Model: gpt-4o-mini
      Extra:
        AuthHeader: Authorization
        AuthScheme: Bearer
```

## 4. 配置加载与字段验证

```java
package com.acanx.module.aha.core.config;

public final class ConfigLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
        new YAMLFactory()
    ).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private static final Pattern PASCAL_CASE = Pattern.compile("^[A-Z][a-zA-Z0-9]*$");

    public static AhaConfig load(Path configPath) {
        // 1. 加载 YAML
        // 2. 验证字段名符合 PascalCase
        // 3. 验证必需字段
        // 4. 应用环境变量覆盖
        // 5. 返回 AhaConfig
    }

    private static void validateFieldNames(JsonNode root) {
        root.fields().forEachRemaining(entry -> {
            if (!PASCAL_CASE.matcher(entry.getKey()).matches()) {
                throw new ConfigException("INVALID_FIELD_NAME",
                    "YAML field must be PascalCase: " + entry.getKey());
            }
            if (entry.getValue().isObject()) {
                validateFieldNames(entry.getValue());
            }
        });
    }
}
```

## 5. SQLite 表结构

> 命名分场景：YAML 配置字段使用 PascalCase（见宪法第 10 条），而 SQLite 表名与字段名使用 snake_case（表名统一使用单数），时间字段沿用 `gmt_create`。

```sql
CREATE TABLE IF NOT EXISTS session (
    id          TEXT PRIMARY KEY,
    gmt_create  INTEGER NOT NULL,
    config_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS message (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  TEXT NOT NULL REFERENCES session(id),
    role        TEXT NOT NULL,
    content     TEXT NOT NULL,
    gmt_create  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS memory (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  TEXT NOT NULL REFERENCES session(id),
    key         TEXT NOT NULL,
    value       TEXT NOT NULL,
    embedding   BLOB,
    gmt_create  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_message_session ON message(session_id, gmt_create);
CREATE INDEX IF NOT EXISTS idx_memory_session ON memory(session_id, key);
```

## 6. 安全存储

```java
package com.acanx.module.aha.core.security;

public interface SecretStore {
    void store(String key, char[] secret);
    char[] retrieve(String key);
    void delete(String key);
}

public final class EncryptedFileSecretStore implements SecretStore {
    // AES-256-GCM
    // PBKDF2WithHmacSHA256 派生密钥
    // 文件格式：JSON，字段加密后 Base64 编码
    // 路径：%APPDATA%\AHA\Key\Aha.keystore 或 ~/.config/aha/Key/Aha.keystore
}
```

**环境变量优先**：`ApiKey: "${AHA_API_KEY_OPENAI}"` 语法，如果环境变量存在则优先使用，否则从加密密钥库读取。

---

# 第十部分：扩展化扩展体系

## 1. 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     AHA Host (启动器)                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              ExtensionRuntime (微内核)                    │  │
│  │  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐  │  │
│  │  │ ModuleLayer │  │ ServiceBus   │  │ Registration│  │  │
│  │  │ Manager     │  │ (事件总线)    │  │ Tracker     │  │  │
│  │  └─────────────┘  └──────────────┘  └─────────────┘  │  │
│  └───────────────────────────────────────────────────────┘  │
│                            │                                 │
│         ┌──────────────────┼──────────────────┐             │
│         ▼                  ▼                  ▼             │
│  ┌────────────┐    ┌────────────┐    ┌────────────┐        │
│  │ CoreExtension │    │ ToolExtension │    │ LlmExtension  │        │
│  │ (内置)      │    │ (内置)      │    │ (内置)      │        │
│  └────────────┘    └────────────┘    └────────────┘        │
│         │                  │                  │             │
│         └──────────────────┼──────────────────┘             │
│                            ▼                                 │
│                   ┌────────────────┐                        │
│                   │  Extension Layer  │  ← 第三方扩展          │
│                   │  (动态加载)     │                        │
│                   └────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

## 2. 扩展目录

```
AHA_HOME/
├── extension/
│   ├── WeatherExtension/
│   │   ├── WeatherExtension.jar
│   │   └── AhaExtension.yaml
│   └── CustomLlmExtension/
│       ├── CustomLlmExtension.jar
│       └── AhaExtension.yaml
```

## 3. 扩展描述符

```yaml
Id: com.example.weather
Name: Weather Extension
Version: 1.0.0
ApiVersion: "1.0"
Description: Provides weather query tools
Author: example@github
MainModule: com.example.weather
MainClass: com.example.weather.WeatherExtension
Permissions:
  - NETWORK
Dependencies:
  - Id: com.acanx.module.aha.extension.api
    Version: ">=0.3.0"
  - Id: com.example.geo
    Version: ">=1.0.0"
    Optional: true
```

## 4. 最小扩展示例

```java
package com.example;

import com.acanx.module.aha.extension.api.*;
import com.acanx.module.aha.common.tool.*;

public class WeatherExtension implements AhaExtension {

    @Override
    public ExtensionDescriptor descriptor() {
        return new ExtensionDescriptor(
            "com.example.weather",
            "Weather Extension",
            "1.0.0",
            "1.0",
            "com.example.weather",
            "com.example.weather.WeatherExtension",
            List.of(ExtensionPermission.NETWORK),
            List.of()
        );
    }

    @Override
    public void onStart(ExtensionContext ctx) {
        ctx.register(ToolProvider.class, () -> List.of(new WeatherTool()));
    }

    @Override
    public void onStop() {
        // 资源由 Registration 自动回滚
    }
}
```

## 5. 事件监听扩展示例

```java
public class AuditExtension implements AhaExtension {

    @Override
    public ExtensionDescriptor descriptor() { /* ... */ }

    @Override
    public void onStart(ExtensionContext ctx) {
        ctx.events().on(AgentEvents.TOOL_PRE_EXECUTE, event -> {
            ctx.logger().info("Tool invoked: {}", event.toolName());
        });

        ctx.events().on(AgentEvents.LIFECYCLE, event -> {
            ctx.logger().info("Lifecycle: {}", event.type());
        });
    }

    @Override
    public void onStop() { }
}
```

## 6. ModuleLayer 加载

```java
package com.acanx.module.aha.core.extension.loader;

public final class ExtensionLoader {

    public ExtensionLayer loadAll(Path extensionDir, ModuleLayer parentLayer) throws IOException {
        // 1. 扫描扩展目录，读取 AhaExtension.yaml
        List<ExtensionDescriptor> descriptors = scanExtensionDir(extensionDir);

        // 2. 依赖解析与拓扑排序
        List<ExtensionDescriptor> ordered = resolveDependencies(descriptors);

        // 3. 为每个扩展创建独立的 ModuleLayer
        ModuleLayer extensionLayer = parentLayer;
        for (ExtensionDescriptor desc : ordered) {
            extensionLayer = loadExtensionIntoNewLayer(extensionLayer, desc);
        }

        return new ExtensionLayer(extensionLayer, ordered);
    }

    private ModuleLayer loadExtensionIntoNewLayer(
            ModuleLayer parent, ExtensionDescriptor desc) throws IOException {

        ModuleFinder finder = ModuleFinder.of(desc.jarPath());

        Configuration cf = parent.configuration()
            .resolve(finder, ModuleFinder.of(), Set.of(desc.mainModule()));

        ClassLoader scl = ClassLoader.getSystemClassLoader();
        return parent.defineModulesWithOneLoader(cf, scl);
    }
}
```

## 7. 生命周期

```
DISCOVERED → LOADED → STARTED → STOPPED → UNLOADED
```

`ExtensionContext.register` 返回 `Registration`，`ExtensionManager` 在 `onStop` 后自动关闭所有注册。

**约束**：

- 扩展不得直接修改全局静态状态
- 扩展不得直接调用 `System.exit()`
- 扩展注册的资源必须通过 `Registration` 可逆注销
- 扩展启动失败时，已注册资源必须回滚

## 8. 隔离、依赖与权限

**类加载隔离**：

- 每个扩展使用独立 `ModuleLayer`
- 扩展只读取 `aha-extension-api` 和 `aha-common`
- 扩展不得访问 `aha-core` 内部包
- 扩展间默认不可见，需通过 `ExtensionContext` 服务发现

**依赖声明**：扩展在 `AhaExtension.yaml` 中声明依赖，`ExtensionManager` 解析依赖顺序，缺失必需依赖时拒绝加载。

**权限模型**：扩展权限声明在 `AhaExtension.yaml` 的 `Permissions` 字段。运行时：

- `READ` / `NETWORK` 默认允许
- `WRITE` / `EXECUTE` / `ADMIN` 需要用户确认
- CLI 通过终端提示，桌面端通过弹窗
- 权限决策记录到日志

## 9. 事件总线

```java
public interface EventBus {
    <E extends ExtensionEvent> void emit(EventKey<E> key, E event);
    <E extends ExtensionEvent> void on(EventKey<E> key, ExtensionEventListener<E> listener);
    <E extends ExtensionEvent> E waterfall(EventKey<E> key, E event);
}
```

**Waterfall 语义**：监听器接收 `(event, next)`，可变换、短路、包装。不调用 `next()` 直接返回则短路。仅做观察的监听器必须委托。

**预定义事件**：

| 事件键 | 类型 | 分发模式 |
|---|---|---|
| `agent/pre-step` | `PreStepEvent` | WATERFALL |
| `agent/request` | `RequestEvent` | WATERFALL |
| `tools/pre-execute` | `ToolPreExecuteEvent` | WATERFALL |
| `tools/post-execute` | `ToolPostExecuteEvent` | WATERFALL |
| `llm/stream` | `LlmStreamEvent` | WATERFALL |
| `system-prompt/assemble` | `SystemPromptEvent` | WATERFALL |
| `session/flush` | `SessionFlushEvent` | PARALLEL |
| `lifecycle` | `LifecycleEvent` | EMIT |

## 10. 分阶段实施

| 版本 | 内容 |
|---|---|
| 0.3 | 引入 `aha-extension-api`，定义 `AhaExtension` / `ExtensionContext` / `Registration`；实现 `ExtensionRuntime` 基础版（静态加载 + Registration 追踪）；现有 `ToolProvider` / `LlmProviderAdapter` 保持兼容 |
| 0.4 | 实现 `ServiceBus` 事件总线；定义 Agent 生命周期事件；内置组件迁移为扩展（Memory、LlmClient、CLI 命令） |
| 0.5 | 实现扩展 `ModuleLayer` 隔离；扩展权限模型；热重载探索 |
| 1.0 | 扩展生态稳定；官方扩展仓库；扩展开发文档与模板 |

---

# 第十一部分：构建、打包与 CI/CD

## 1. Maven Wrapper

`.mvn/wrapper/maven-wrapper.properties`：

```properties
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/4.0.0-rc-7/apache-maven-4.0.0-rc-7-bin.zip
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
```

## 2. 打包方式

| 版本 | CLI | 桌面端 |
|---|---|---|
| 0.1 | JPMS 模块路径目录（`dist/`）+ 启动脚本 | — |
| 0.2 | 同上 | jpackage (MSI/DEB) |
| 1.x | native-image | jpackage (MSI/DEB) |

### 2.1 0.1 发行包

`./mvnw clean package` 在项目根生成 `dist/`：

```
dist/
├── bin/
│   ├── Aha.sh
│   └── Aha.bat
├── lib/                  ← JPMS 模块路径：本项目模块 + 全部 runtime 依赖
├── README.md
├── LICENSE
└── CHANGELOG.md
```

由 `maven-assembly-plugin` 组装，描述符为 `aha-cli/src/assembly/dist.xml`：

- `dependencySet` 以 `useProjectArtifact=true` 收集 `aha-cli` 自身与全部 runtime 依赖到 `lib/`
- `fileSet` 附带 `bin/` 启动脚本与项目文档
- `bin/Aha.sh` 取 `$DIR/../lib`，与平铺布局一致（`outputDirectory` 指向项目根，`finalName=dist`）

`dist/` 已在 `.gitignore` 中忽略，不作为源码提交。

**为何不用 fat JAR**：shade 会合并出无 `module-info` 的单一 JAR，破坏 JPMS 强制启用原则。
**为何不用 jlink**：`sqlite-jdbc` 为自动模块，jlink 不支持。
**为何不用 jpackage（0.1）**：0.1 仅需 CLI + `bin/` 脚本，jpackage 主要用于 0.2 的桌面端。

## 3. 启动脚本

| 文件 | 说明 |
|---|---|
| `bin/Aha.sh` | Linux / macOS； `$DIR/../lib` 作为模块路径 |
| `bin/Aha.bat` | Windows； `%~dp0..\lib` 作为模块路径 |

两个脚本均：

- 优先使用 `JAVA_HOME`，未设置时回退到 `PATH` 中的 `java`
- 在 `lib/` 缺失时给出可读错误，并提示构建命令与源码运行方式（`./mvnw -pl aha-cli exec:java`）
- 以 `exec` 启动（Linux），不额外派生进程，保证信号与退出码透传
- 传递 `--enable-native-access=org.xerial.sqlitejdbc,org.jline`，消除 JDK 25 的受限方法警告

**脚本编码约束（强制）**：

| 文件 | 编码 | 行尾 |
|---|---|---|
| `bin/Aha.bat`（及一切 `*.bat`/`*.cmd`） | 纯 ASCII | CRLF |
| `bin/Aha.sh`（及一切 `*.sh`/`*.py`） | UTF-8 | LF |

`*.bat` 必须为纯 ASCII：CMD 按 ANSI 代码页解析批处理，UTF-8 中文字节会被误读并产生 `&`、`|`
等元字符，使 `rem` / `echo` 行被当作命令执行；LF-only 批处理的 `if (...)` 块解析也不可靠。
因此批处理中的用户提示一律用英文，中文说明放到文档。此约束由 `bin/CheckScripts.py` 在 CI 中校验。

实际内容以仓库中的 `bin/Aha.sh` / `bin/Aha.bat` 为准，不在此处重复维护。

## 4. GitHub Actions

`.github/workflows/Build.yml`：

```yaml
name: Build

on: [push, pull_request]

jobs:
  build:
    strategy:
      matrix:
        include:
          - os: windows-latest
            maven: wrapper
          - os: ubuntu-latest
            maven: wrapper
          - os: ubuntu-latest
            maven: system
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 25
      - name: Build (Wrapper / Maven 4)
        if: matrix.maven == 'wrapper'
        run: ./mvnw clean verify
        shell: bash
      - name: Build (System / Maven 3.9.x)
        if: matrix.maven == 'system'
        run: mvn clean verify
```

## 5. 验收标准

- `./mvnw clean verify` 通过（Maven 4 运行时）
- `mvn clean verify` 通过（Maven 3.9.x 运行时）
- 两条流水线结果一致
- 所有测试通过
- 覆盖率报告生成

---

# 第十二部分：测试策略

## 1. 测试层级

| 层级 | 框架 | 覆盖范围 |
|---|---|---|
| 单元测试 | JUnit 6 + AssertJ + Mockito | Core 逻辑、工具实现、配置解析 |
| LLM 适配器测试 | JUnit 6 + 固定 JSON fixture | 请求/响应/流式事件转换 |
| 中转站兼容测试 | JUnit 6 + 固定 JSON fixture | sub2api / new-api 响应与流式差异 |
| LlmClient 集成测试 | Mock HTTP 服务器 | SSE 流、重试、超时、取消 |
| CLI 测试 | picocli test | 命令解析、参数验证、输出格式 |
| 扩展测试 | 独立测试扩展 JAR | 加载、注册、注销、权限 |
| JavaFX 测试 | TestFX（0.2+） | Controller 交互、WebView 加载 |
| 集成测试 | Testcontainers（可选） | 外部服务依赖 |

## 2. 测试 Fixture 命名

```
aha-core/src/test/resources/
├── Configs/
│   ├── ValidConfig.yaml
│   ├── InvalidFieldName.yaml
│   └── MissingRequiredField.yaml
└── Fixtures/
    ├── OpenAiChatResponse.json
    ├── OpenAiStreamEvents.json
    ├── AnthropicChatResponse.json
    ├── AnthropicStreamEvents.json
    ├── GeminiChatResponse.json
    └── GeminiStreamEvents.json
```

## 3. 覆盖率要求

- Core 模块 ≥ 70%
- Common 模块 ≥ 80%
- ExtensionApi 模块 ≥ 80%
- CLI 模块 ≥ 60%
- Tool 模块 ≥ 70%

### 3.1 实测覆盖率（0.1.0）

| 模块 | 行覆盖 | 达标 |
|---|---|---|
| `aha-extension-api` | 100.0%（23/23） | ✅ |
| `aha-common` | 90.2%（165/183） | ✅ |
| `aha-cli` | 83.8%（2246/2681） | ✅ |
| `aha-tool` | 79.6%（148/186） | ✅ |
| `aha-core` | 74.6%（1449/1943） | ✅ |

合计行覆盖 **80.3%**（4045/5038 行），共 **605** 个测试用例
（`aha-common` 57 / `aha-extension-api` 6 / `aha-core` 193 / `aha-tool` 25 / `aha-cli` 323）。

> **口径与复现**：数据取自 `./mvnw clean verify`（Maven 4 wrapper；JaCoCo 0.8.15；
> 门禁 `BUNDLE` 行覆盖 ≥ 0.70）。合计 = 各模块 `LINE_COVERED / (LINE_COVERED + LINE_MISSED)`
> 求和；`aha-desktop` 是 0.1 占位模块，已从门禁排除，不计入合计。
>
> 覆盖率随功能增长而下降属正常：`aha-common` 与 `aha-cli` 在补充 `Exceptions`、
> `ToolApprover`、`LoggingSetup`、`ConsoleToolApprover` 后，由原先的 100% / 88.1% 回落。

### 3.2 JaCoCo 配置

- 扩展：`jacoco-maven-plugin 0.8.15`（`pom.xml` 的 `jacoco.version`）
- 目标绑定：`prepare-agent`（initialize）、`report`（verify）、`check`（verify）
- 门禁规则：`BUNDLE` 级 `LINE COVEREDRATIO ≥ 0.70`，未达标则 `verify` 失败
- 报告路径：各模块 `target/site/jacoco/index.html`

### 3.3 覆盖率排除项

以下范围不计入门禁，属后续版本或需真实运行环境，由手工验收覆盖：

| 排除范围 | 理由 |
|---|---|
| `core/extension/**` | 扩展运行时属 0.3 |
| `core/mcp/**` | MCP 适配器为 0.2 占位 |
| `desktop/**` | 桌面端 0.2 实现 |
| `cli/ChatCommand*`、`cli/RunCommand*` | 依赖真实 TTY 与供应商 API Key |

## 4. LLM 适配器测试要求

- 使用固定 JSON fixture 验证转换正确性
- 不使用真实 API（CI 中通过环境变量控制，默认跳过）
- 测试覆盖：请求转换、响应转换、流式事件转换、错误处理

---

# 第十三部分：开发规范

## 1. 编码规范

**命名**：

| 元素 | 规范 | 示例 |
|---|---|---|
| 类名 | 大驼峰 | `AgentEngine` |
| 方法名 | 小驼峰 | `createSession` |
| 常量 | 全大写下划线 | `MAX_RETRY_COUNT` |
| 包名 | 全小写 | `com.acanx.module.aha.core` |
| 记录类 | 大驼峰 | `ChatRequest` |
| 枚举值 | 全大写 | `CONTENT_DELTA` |
| YAML 字段 | 大驼峰 | `DefaultProvider` |

**异常处理**：

- 所有异常必须被处理或包装为 `AhaException`
- 禁止空 catch 块
- 禁止在 Core 中调用 `System.exit()`
- 异常必须携带 `code` 字段

**日志**：

> 子系统设计与取舍（装配方式、分级分流、文件命名与切分、已知限制）见
> [LoggingDesign.md](../Design/LoggingDesign.md)。

- 使用 SLF4J API
- 禁止使用 `System.out` / `System.err`
- 敏感信息（API Key、密码）不得记录
- 日志级别：ERROR / WARN / INFO / DEBUG / TRACE

**并发**：

- 使用虚拟线程处理 I/O 密集型任务
- 禁止池化虚拟线程
- 使用 `CancellationToken` 传播取消
- 禁止使用 `Thread.stop()` / `Thread.suspend()`

## 2. 提交规范

采用 Conventional Commits：

```
<type>(<scope>): <subject>

<body>

<footer>
```

**type**：`feat`、`fix`、`docs`、`style`、`refactor`、`test`、`chore`、`build`、`ci`

**scope**：模块名（`common`、`extension-api`、`core`、`tool`、`cli`、`desktop`、`docs`）

**示例**：

```
feat(core): 新增 Anthropic 适配器

实现 LlmProviderAdapter 接口，支持 Anthropic Messages 协议。
包含请求转换、响应转换、流式事件转换。

Closes #123
```

## 3. 分支策略

| 分支 | 用途 | 生命周期 |
|---|---|---|
| `main` | 稳定版本 | 永久 |
| `dev` | 开发主线 | 永久 |
| `feat/*` | 新功能 | 合并后删除 |
| `fix/*` | 修复 | 合并后删除 |
| `release/*` | 发布准备 | 发布后删除 |
| `hotfix/*` | 紧急修复 | 合并后删除 |

## 4. 评审清单

- [ ] 代码符合编码规范
- [ ] 所有公开 API 有 Javadoc
- [ ] 测试覆盖新增代码
- [ ] 无 `System.out` / `System.err`（CLI 除外）
- [ ] 无 Maven 4 新语法
- [ ] 无违反模块依赖方向
- [ ] YAML 字段符合 PascalCase
- [ ] 相关文档已更新
- [ ] 提交信息符合规范

## 5. 文档更新规范

- 开发规范变更后 **3 个工作日内** 更新 `Docs/DevSpec/`
- 每个 minor release 前审查 `Docs/DevSpec/` 全部文档
- 文档变更与代码变更在同一个 PR 中提交
- 文档头部必须有版本号、状态、生效日期
- 文档命名采用大驼峰

---

# 第十四部分：开发任务清单

## 阶段一：项目骨架（1 周）

- [x] 创建父 POM + 6 个子模块 POM（含 `aha-extension-api`）
- [x] 配置 Maven Wrapper（Maven 4）
- [x] 创建 6 个 `module-info.java`
- [x] 验证 `./mvnw clean compile` 和 `mvn clean compile` 均通过
- [x] 创建 `AGENTS.md`
- [x] 创建 `.agents/skills/` 目录及 6 个技能骨架
- [x] 创建 `Docs/DevSpec/` 目录及 11 个规范文档
- [x] 创建 `Docs/Design/` 目录及设计文档骨架
- [x] 创建 `Docs/Guide/` 目录及用户指南骨架
- [x] 创建 `Docs/Diagrams/` 目录

## 阶段二：aha-common（1 周）

- [x] 异常层次
- [x] 事件模型
- [x] 共享模型
- [x] Tool SPI
- [x] JsonSchema 最小实现
- [x] 单元测试（54 个用例）

## 阶段三：aha-extension-api（1 周）

- [x] `AhaExtension` 接口
- [x] `ExtensionDescriptor`
- [x] `ExtensionContext`
- [x] `Registration`
- [x] `EventBus` 接口
- [x] `ExtensionPermission`
- [x] 扩展点接口
- [x] 单元测试（6 个用例，行覆盖 100%）

## 阶段四：LLM 适配器体系（2 周）

- [x] IR DTO
- [x] `IrVersion`
- [x] `LlmProviderAdapter` SPI
- [x] `OpenAiAdapter`
- [x] `AnthropicAdapter`
- [x] `GeminiAdapter`
- [x] 国产模型预设（DeepSeek / BigModelCN / Qwen）
- [x] 中转站兼容处理（sub2api / new-api）
- [x] `LlmAdapterRegistry`
- [x] `LlmClient` 接口与 `DefaultLlmClient`
- [x] 适配器单元测试（固定 JSON fixture，14 个用例）
- [x] LlmClient 集成测试（Mock HTTP 服务器，7 个用例）

## 阶段五：配置与安全（1 周）

- [x] 配置模型（PascalCase 映射）
- [x] `ConfigLoader`（含字段名验证、`${ENV:-default}` 解析、双文件合并）
- [x] `AhaDefault.yaml`（主配置，仅保留兜底模型）
- [x] 模型供应商配置分离：`Model.yml` + classpath `ModelDefault.yml` + `ModelConfigStore`
- [x] 一键切换供应商（`aha provider use` / `add` / `remove`）
- [x] 降级回退（`Llm.Fallback` → `DefaultLlmClient` 供应商解析）
- [x] `SecretStore` 接口与 `EncryptedFileSecretStore`（AES-256-GCM + PBKDF2）
- [x] 单元测试

## 阶段六：记忆存储（1 周）

- [x] SQLite 表结构初始化
- [x] `MemoryStore` 接口与 `SqliteMemoryStore`
- [x] WAL 模式配置
- [x] 单元测试

> **0.1 只交付了存储层。** 下面四项缺失，已列入 1.0 前的必补清单（计划 0.6，见第十五部分 1.1）：
>
> - [ ] **记忆工具（模型侧）**：把 `storeMemory` / `recall` 接成可被模型调用的工具。
>       当前 `memory` 表在任何入口下都无调用方——存储层完成了、单测通过了，
>       但模型既不写也不读，功能实际不可用
> - [ ] **记忆命令（用户侧）**：`/memory`（会话内）与 `aha memory`（进程级：列表 / 查看 / 写入 / 删除 / 检索）
> - [ ] **记忆编辑**：唤起外部编辑器修改记忆并回写存储（载体选型见
>       [MemoryStorageDesign.md](../Design/MemoryStorageDesign.md)）
> - [ ] **检索质量**：当前 `recall` 仅为 `LIKE` 匹配，按 0.6 计划升级为向量检索 + RAG

## 阶段七：AgentEngine 与 AgentService（2 周）

- [x] `AgentEngine` 推理循环
- [x] `AgentEngine` 流式版本
- [x] `AgentService` 接口
- [x] `LocalAgentService` 实现（支持依赖注入）
- [x] `AgentServiceFactory`
- [x] `SessionManager`
- [x] `ToolRegistry`
- [x] 单元测试

## 阶段八：扩展运行时（0.3 起，2 周）

- [ ] `ExtensionLoader`（静态加载）
- [ ] `ExtensionManager`
- [ ] `ExtensionContextImpl`
- [ ] `RegistrationTracker`
- [ ] `EventBusImpl`
- [ ] 单元测试

> 阶段八随 0.3 交付，0.1 不纳入覆盖率门禁（见第十二部分 3.3）。

## 阶段九：aha-tool（1 周）

- [x] `FileToolProvider`
- [x] `HttpToolProvider`
- [x] `ShellToolProvider`
- [x] 权限检查集成（`PermissionPolicy` + `ToolRegistry` 门控 + `Tools.AutoApprove`）
- [x] 单元测试（22 个用例）

## 阶段十：aha-cli（2 周）

- [x] `AhaCli` 主类
- [x] `InitCommand`（交互式 / 非交互式初始化，生成 `Model.yml` 与目录结构）
- [x] `ChatCommand`（JLine 交互式）
- [x] `RunCommand`
- [x] `ToolCommand`（含 `--param` / `--yes`）
- [x] `ProviderCommand`
- [x] `ExtensionCommand`
- [x] `ConfigCommand`
- [x] `VersionCommand`
- [x] 无参数默认进入交互式对话（非交互环境打印帮助）
- [x] `chat` 启动展示供应商 / 模型 / 端点 / 工作目录 / 会话
- [x] `SystemPromptLoader`（`AHA.md` / `AGENTS.md` 身份加载）与 `--system` / `--system-file`
- [x] 会话配置持久化（`session.config_json`）与恢复
- [x] 启动脚本
- [x] CLI 测试（62 个用例）
- [x] 命令与配置参考（`Docs/Guide/ReferenceGuide.md`）

## 阶段十一：CI/CD 与文档（1 周）

- [x] GitHub Actions 配置
- [x] Maven 3.9.x 兼容性验证
- [x] 完善 `AGENTS.md`
- [x] 完善 `Docs/DevSpec/` 全部文档
- [x] 完善 `Docs/Design/` 全部文档
- [x] 完善 `Docs/Guide/` 全部文档
- [x] 生成 Javadoc（`maven-javadoc-plugin`，`doclint=none`）

## 阶段十二：验收（1 周）

- [x] `./mvnw clean verify` 通过
- [x] `mvn clean verify` 通过
- [x] 发行包构建与启动（`mvnw clean package` → `dist/bin/Aha.sh version|--help` 实测通过）
- [x] CLI 端到端测试（伪终端下验证 `chat` 的启动、提示符与 `exit` 退出；非交互命令已全量验证）
- [ ] LLM 供应商接入测试（需真实 API Key，已通过 Mock HTTP 与 fixture 验证协议转换）
- [x] 会话持久化测试（`EndToEndTest`：真实 SQLite 重建后读取）
- [x] 密钥加密测试
- [x] YAML 字段验证测试
- [x] 覆盖率报告 ≥ 70%（JaCoCo `check` 门禁已生效）

**总计：约 16 周**（含扩展运行时基础版）

> 实现进度（0.1.0）：阶段一至七、九至十一已完成；阶段八属 0.3；
> 阶段十二仅余需真实 API Key 的供应商接入验收项。

### 0.1 功能核查（发布于 0.1.0 前复核）

`[x]` 仅表示「代码已写」，不代表「配置已生效」。以下为逐字段核查生产引用的结果。

**已发现并修复**：

| 项 | 问题 | 处理 |
|---|---|---|
| `Aha.Logging.Level` / `File` | 只被 `config get` 读取展示，从未配置日志系统（无 log4j2 配置，退回默认 console+ERROR），`Log/` 始终为空；且全项目仅 6 处 `debug` 调用 | 启动时程序化配置；补齐 INFO 日志点 |
| 日志格式 | `%logger{1.}` 输出每包首字母缩写，无法定位调用点 | 改为 `%logger.%method:%line` |
| 默认路径 | `${AHA_HOME:-.}` 的 fallback 为 `.`（当前工作目录），数据库/日志/密钥库默认写进项目目录 | fallback 改为 `~/.aha`；路径字段补 `~` 展开 |
| `keys/` 目录 | 命名风格不一致 | 改为 `Key/`（单数 + 大驼峰） |
| `Tools.Shell.TimeoutSeconds` | `ShellExecTool` 用无参 `waitFor()`，**无限等待**；一条卡住的命令挂死整个对话循环 | 默认 30 秒超时；超时杀**整个进程树**（否则 `sh` 被 kill 后 `sleep` 成孤儿并持有管道） |
| `shell-exec` 取消 | 未检查 `CancellationToken` | 轮询取消并在取消时终止进程树 |
| `Security.KeyStore` | 生产代码零引用 | 见下方「仍缺失」 |
| `FileReadTool.description` | 声称「支持路径白名单」，实际未实现 | 修正描述 |

**根因**：`Tool` 与 `ToolProvider` 接口**没有配置注入口**，工具（ServiceLoader 创建）
无法访问 `Tools.*` 配置。这是 `Shell` 与文件类工具配置全部失效的架构原因。

**已补齐（配置注入机制）**：

- `ToolSettings`（`aha-common`）：中立的配置视图。`aha-common` 不依赖 `aha-core`，
  故以点分键（如 `Shell.TimeoutSeconds`）传递，避免配置类型反向依赖
- `Tool.configure(ToolSettings)`：默认空实现，需要配置的工具覆盖
- `ToolRegistry.configureAll(...)`：注册完成后统一注入
- `ToolRegistry` 新增 `setEnabled`：`Tools.Enabled` 生效（`list()` 过滤 + `invoke()` 拒绝）
- `invokeApproved(...)`：显式确认路径**不再绕过**启用过滤
  （此前 `invokeTool(confirmed=true)` 直接调 `tool.execute()`，未启用的工具仍可调用）
- `ShellExecTool`：`Shell.TimeoutSeconds` 与 `Shell.AllowedCommands` 生效。
  白名单仅接受「单一命令 + 无 shell 元字符 + 首个子命令在名单内」——
  只判首 token 是不安全的：`echo hi; whoami` 的首 token 是允许的 `echo`，
  但 `whoami` 照样会被 sh 执行
- `SecretStore` 接入：按 `Security.KeyStore` / `KeyStorePath` 创建；
  占位符未命中环境变量时回退到加密密钥库（`ConfigLoader.resolveSecrets`）。
  解析发生在**使用点**而非加载点——`Security` 段自身是明文，
  配置加载必然早于密钥库就绪
- `aha secret set|get|delete|list`：密钥库管理命令

**其余未实现项（属计划内）**：

| 项 | 计划版本 |
|---|---|
| `Extensions.*`（扩展运行时） | 0.3 |
| `aha-desktop` / `McpAdapter` | 0.2 |

## 阶段十三：AHA 自举（0.2 ~ 1.0，持续）

> 1.0 正式版的**硬门槛**：从依赖其他 Agent Harness 切换到用 AHA 独立完成项目开发与后续迭代。
> 完整方案见 [SelfHostingDesign.md](../Design/SelfHostingDesign.md)。

- [ ] L1 可读（0.2）：交互式 `chat` 真实 TTY 联调，只读任务可在单会话内完成且无需回退
- [ ] L2 可改（0.3 ~ 0.4）：连续 10 个 PR 由 AHA 产出且 CI 通过
- [ ] L3 可交付（0.5 ~ 0.7）：一个完整 minor 版本的全部提交由 AHA 产出
- [ ] L4 全自举（0.8 ~ 1.0）：连续 ≥ 4 周零外部 Harness
- [ ] A1 工具完备（文件 / 命令 / HTTP / 记忆 / 会话恢复，权限受控）
- [ ] A2 技能完备（`build` / `test` / `release` 驱动完整交付闭环）
- [ ] A3 持续性（连续 ≥ 4 周、≥ 30 次提交）
- [ ] A4 质量不降级（评审一次通过率 ≥ 80%）
- [ ] A5 发布闭环（至少 1 个 minor 版本的完整发布由 AHA 驱动）
- [ ] A6 可审计（提交信息标注 `Harness:` 行）

> 本阶段跨越多个版本，不占用 0.1 的交付周期；每个 minor 版本更新自举进度记录。

---

# 第十五部分：版本路线图与风险

## 1. 版本路线图

| 版本 | 内容 | 关键里程碑 |
|---|---|---|
| **0.1** | Core + CLI 可运行 | AgentService + picocli + SQLite + LLM 适配器 + 远程/协议前向兼容契约 |
| **0.2** | 桌面端 | OpenJFX WebView + FXML + jpackage |
| **0.3** | 扩展基础 | aha-extension-api + ExtensionRuntime + Registration |
| 0.4 | 事件总线 | EventBus + 生命周期事件 + CLI extension 命令 |
| 0.5 | 隔离与权限 | ModuleLayer 隔离 + 扩展权限 + 热重载探索 |
| 0.6 | 记忆与用量 | 记忆工具与命令（`/memory`、`aha memory`）+ 编辑器唤起 + 向量存储 + RAG + 用量统计（`/stats`、`aha stat`） |
| 0.7 | 安全增强 | 系统密钥链适配器 |
| 0.8 | 多运行时前置 | `AgentServiceProvider` 生产化 + `aha doctor` 预检 + 契约兼容性矩阵测试 |
| 1.0 | 稳定版 | API 冻结 + 完整测试覆盖 + 扩展生态 + **AHA 自举达标（硬门槛）** |
| 1.1 | 远程与调度 | `aha-remote`（SSH Linux）+ `aha-wsl`（Win11 WSL）+ `aha-scheduler`（cron / 远程下发） |
| 1.2 | 协议接入 | `aha-acp`（Server/Client）+ `aha-mcp`（工具消费）+ HTTP/WebSocket 传输 |
| 1.3 | 云原生 | `aha-cloudfn`（Lambda / 阿里云 FC / 腾讯云 SCF）+ 容器镜像 + K8s Job |
| 1.4 | 分布式运行时 | 任务编排（DAG）+ 多实例调度 + 结果持久化 |
| 2.0 | 可观测与生态 | OpenTelemetry + 官方扩展仓库 + 跨厂商 Agent 协作（A2A） |
| 1.x | native-image | CLI native binary |

### 1.1 1.0 前必须补齐的能力缺口

下面这些能力的**底层已就绪、用户可见入口缺失**。不补齐则 1.0 的 A1 / A7 不达标，
因此均已列入计划，目标版本 0.6（机器侧模型工具与用户侧命令一并交付）。

| 能力 | 现状（0.1） | 目标版本 | 完成标准 |
|---|---|---|---|
| 记忆写入与检索（模型侧） | `memory` 表已建、`storeMemory` / `recall` 已实现，但**全仓无调用方** | 0.6 | 模型可通过工具写入与检索记忆，权限受控 |
| 记忆查看与编辑（用户侧） | 无任何命令，也无唤起编辑器的能力 | 0.6 | `/memory` 与 `aha memory` 支持列表 / 查看 / 写入 / 删除 / 检索；可唤起外部编辑器编辑并回写 |
| 记忆的记录与整理 | 无记录入口，也无整理机制 | 0.6 | 「记录」：会话结束 / 显式触发 / 模型调用三种入口，先落候选区；「整理」：去重合并、升降级、冲突检测，且可回滚 |
| 用量统计 | `UsageEvent` 已发出，但**不落库**，无 usage 表；仅 `/context` 有手算估算 | 0.6 | 真实用量落库；`/stats` 看本会话，`aha stat` 看全局（会话数、消息数、记忆数、Token 累计、供应商分布、近 7 天活跃） |

> 「有表无功能」是本项目最容易漏掉的一类缺口：存储层完成了、接口定义了、单测也通过了，
> 但没有任何调用方，功能实际不可用。A1 / A7 的验收必须由**可操作的入口**证明，
> 而不是由接口存在证明——检验方式是「用户和模型真的能把它用起来」。

> 已提前到 0.1 交付：「Agent 身份文件（`AGENTS.md` / `AHA.md`）的查看与编辑」——
> 它本不在缺口表里（而是**根本没进计划**），现由 `/memory` 提供查看正文、
> 唤起编辑器、追加、重载。顺带落地的 `TerminalHandover`（终端让位）与
> `ExternalEditor`（跨平台编辑器探测）正是下表中 0.6 记忆编辑所要复用的基础设施。

> **记忆的存储载体尚未定下来**（表 / MD 文件 / 混合），候选方案、实测数据与判据见
> [MemoryStorageDesign.md](../Design/MemoryStorageDesign.md) 第 7 节。但「**记录与整理**」是
> 已定的原则：无论最终选哪个载体都要做。实测已表明瓶颈不在文本而在向量
> （200 万行文本全表扫 41 ms，而 5 万条 384 维向量暴力扫已达 100 ms/次），
> 因此向量应独立于主表设计。

> 已定且与载体无关的部分（见 [MemoryStorageDesign.md](../Design/MemoryStorageDesign.md) 第 8 节）：
> 记忆**按项目**存放于 `~/.aha/Project/<项目ID>/Memory/`（项目 ID 由项目根绝对路径推导，
> 与 Claude Code 的 `~/.claude/projects/` 规则一致）；写入分三级——项目记忆可**直接写**、
> 可进**候选区**、**长期记忆只能由整理流程筛选提取生成**；开关 `Memory.ModelWrite`
> 取 `off` / `candidate` / `direct`。指导原则是「**先记录存档，后利用**」。
>
> Windows 与 WSL 路径不同，**默认视为两个独立项目**；后续需补「显式配置共享记忆与会话历史」的机制
> （后续工具优化方向，见同文档 8.6）。

> 远程 / 协议 / 调度 / 云函数 / WSL 能力的完整演进方案、前向兼容契约、
> 配置 schema 与安全模型，见 [RemoteAndProtocolDesign.md](../Design/RemoteAndProtocolDesign.md)。

## 2. 1.0 前置里程碑：AHA 自举

**目标**：发布 1.0 正式版之前，AHA 必须完成自举（Self-Hosting）—— 项目自身的开发、测试、
构建、发布，以及后续 AHA 功能的迭代升级，全部由 AHA 工具链（CLI + 技能体系 + 内置工具 +
记忆与会话）完成，不再依赖 Claude Code、Cursor、GitHub Copilot Workspace 等其他 Agent Harness。

这是 1.0 的**硬门槛**，未达成不得发布正式版。

### 2.1 自举层级

| 层级 | 名称 | 版本区间 | 自举范围 | 升级门禁 |
|---|---|---|---|---|
| L0 | 手工 | 0.1（现状） | 完全依赖其他 Harness | — |
| L1 | 可读 | 0.2 | 读代码、检索、解释结构、起草文档 | 单次会话内完成只读任务且无需回退 |
| L2 | 可改 | 0.3 ~ 0.4 | 小范围代码修改并跑通测试 | 连续 10 个 PR 由 AHA 产出且 CI 通过 |
| L3 | 可交付 | 0.5 ~ 0.7 | 完整功能（代码 + 测试 + 文档 + CHANGELOG） | 一个完整 minor 版本的全部提交由 AHA 产出 |
| L4 | 全自举 | 0.8 ~ 1.0 | AHA 自身的迭代与发布 | 连续 ≥ 4 周零外部 Harness |

层级不可跳跃：未达成 L2 就宣称 L4，等价于没有自举。

### 2.2 验收标准（1.0 发布前须全部满足）

| 编号 | 标准 |
|---|---|
| A1 | 工具完备：文件读写、命令执行、HTTP、记忆写入与检索、会话恢复均可用且权限受控 |
| A2 | 技能完备：`build` / `test` / `release` 技能能驱动完整交付闭环 |
| A3 | 持续性：连续 ≥ 4 周、≥ 30 次提交由 AHA 产出，期间不启动其他 Harness |
| A4 | 质量不降级：评审一次通过率 ≥ 80%，CI 通过率与人工产出无显著差异 |
| A5 | 发布闭环：至少 1 个 minor 版本的完整发布流程由 AHA 驱动 |
| A6 | 可审计：提交信息标注 `Harness:` 行，可区分产出方 |
| A7 | 数据自省：会话、消息、记忆可查看与编辑（含唤起外部编辑器），用量统计可查询 |

**范围限定**：1.0 的自举仅限**本地 runtime**；远程执行、WSL、调度、云函数能力计划于 1.1 之后交付，
不得作为 1.0 自举的前置依赖。

### 2.3 为何是硬门槛

自举是能力完备性的最严格检验：工具不足、技能缺失、记忆检索不到既往决策、权限确认频繁打断、
会话无法恢复——任何一项缺口都会在自举过程中立刻暴露。同时，自举使每日开发成为强制回归，
破坏可用性的改动会在次日被发现。

> 完整方案（自举层级、反向指标、缺口与补齐路径、度量方法、风险与开工清单）见
> [SelfHostingDesign.md](../Design/SelfHostingDesign.md)。

## 3. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| sqlite-jdbc 为自动模块，无法 jlink | 0.2 打包受限 | 0.1 使用 JAR 分发，0.2 使用 jpackage |
| Maven 4 在 JPMS 多模块下有阻塞性问题 | 构建失败 | POM 语法兼容 Maven 3.9.x，可临时回退 |
| JUnit 6 生态适配不完整 | 测试框架问题 | JUnit 6 已 GA，核心功能稳定 |
| Anthropic / Gemini 协议变更 | 适配器失效 | 适配器隔离，仅需修改适配器实现 |
| OpenAI 兼容供应商行为差异 | 兼容性问题 | 适配器支持 `extensions` 透传 |
| 中转站（sub2api / new-api）实现差异 | 兼容性问题 | 宽松解析 + 增量聚合 + 可配置鉴权 |
| 虚拟线程与 SQLite JDBC 兼容性 | 性能或稳定性问题 | SQLite 操作在平台线程池中执行 |
| JPMS 动态加载复杂 | 扩展实现难度高 | 0.3 先静态加载，0.5 引入 ModuleLayer |
| 扩展类加载冲突 | 运行时错误 | 独立 ModuleLayer + 依赖声明 |
| 扩展权限绕过 | 安全风险 | 权限声明 + 运行时检查 + 日志 |
| YAML 字段规范迁移遗漏 | 配置解析失败 | CI 中增加字段名验证 |
| 文档滞后于代码 | 开发者上手困难 | 文档变更与代码变更同 PR 提交 |
| 远程执行引入 RCE 面 | 安全事故 | 跨边界默认 deny + 白名单 + 审计 + 密钥鉴权（见 `RemoteAndProtocolDesign.md` 第 6 节） |
| ACP / MCP / A2A 标准未定稿 | 过早抽象导致返工 | 0.1 不做协议抽象，只做 `AgentServiceProvider`；协议模块独立演进 |
| 自举门槛被事后放宽 | 1.0 名不副实 | 验收标准写入 `SelfHostingDesign.md`，并纳入发布检查清单强制勾选 |
| 云函数冷启动超时 | 无法满足平台限制 | 提供 lite 产物 + 延迟初始化 + 预热实例 |
| 混合版本集群字段不兼容 | 载荷解析失败 | `TaskRequest`/`TaskResult` 使用包装类型 + 忽略未知字段（0.1 已落地） |
| WSL 路径与权限差异 | 任务失败 | 路径映射 + 显式 `WorkDir` + `wsl -l -q` 预检 |

---

# 附录

## 附录 A：文档索引

### Docs/AHA/

| 文档 | 说明 |
|---|---|
| `AHA-Design-V1.md` | 本文件：完整设计文档（唯一权威文档） |

### Docs/（根级）

| 文档 | 说明 |
|---|---|
| `TODO.md` | 待办与调整项（暂存区） |
| `PLAN.md` | 暂缓与受限事项，含「做不到」的判断依据与实测证据 |

### Docs/DevSpec/

| 文档 | 说明 |
|---|---|
| `Constitution.md` | 技术宪法全文 |
| `BuildSpec.md` | 构建规范（工具链锁定、双版本验证、覆盖率门禁） |
| `CodingStandard.md` | 编码规范（命名、异常、日志、并发） |
| `ModuleConvention.md` | 模块约定（JPMS、依赖方向、包结构） |
| `DocumentationSpec.md` | 文档规范（命名、存放、更新制度） |
| `TestingSpec.md` | 测试规范（层级、覆盖率、Fixture） |
| `YamlFieldSpec.md` | YAML 字段命名规范 |
| `CommitMessageSpec.md` | 提交信息规范 |
| `BranchStrategy.md` | 分支策略 |
| `CodeReviewChecklist.md` | 评审清单 |
| `ReleaseProcess.md` | 发布流程 |

### Docs/Design/

| 文档 | 说明 |
|---|---|
| `ArchitectureOverview.md` | 架构总览 |
| `AgentServiceDesign.md` | AgentService 接口设计 |
| `LlmAdapterDesign.md` | LLM 适配器设计 |
| `InternalProtocolSpec.md` | 内部统一协议规范 |
| `RemoteAndProtocolDesign.md` | 远程接入与协议演进（1.0+ 规划与前向兼容契约） |
| `ToolSystemDesign.md` | 工具系统设计 |
| `MemoryStorageDesign.md` | 记忆存储设计 |
| `SecurityDesign.md` | 安全设计 |
| `LoggingDesign.md` | 日志设计（装配方式、分级分流、命名与切分、已知限制） |
| `PixelLogoDesign.md` | 像素风启动标志设计（原型几何、像素画法、调色板、复现与漂移检测） |
| `CLIDesign.md` | CLI 设计 |
| `TUIDesign.md` | 终端界面（TUI）当前实现说明：版面样式、降级矩阵、流式输入机制 |
| `GUIDesign.md` | 桌面端界面方案：布局、视觉语言、菜单与交互流程（0.2 提案） |
| `DesktopDesign.md` | 桌面端设计（0.2） |
| `ExtensionSystemDesign.md` | 扩展系统设计 |
| `SelfHostingDesign.md` | AHA 自举里程碑（1.0 硬门槛：从依赖其他 Harness 切换到独立自举） |
| `EventBusDesign.md` | 事件总线设计 |

### Docs/Guide/

| 文档 | 说明 |
|---|---|
| `GettingStarted.md` | 快速开始 |
| `BuildGuide.md` | 构建指南（命令、报告、构建期排错） |
| `ConfigurationGuide.md` | 配置指南 |
| `ProviderSetupGuide.md` | 供应商设置指南 |
| `ToolUsageGuide.md` | 工具使用指南 |
| `ExtensionAuthoringGuide.md` | 扩展开发指南 |
| `TroubleshootingGuide.md` | 排错指南 |

### Docs/Diagrams/

| 文件 | 说明 |
|---|---|
| `ModuleArchitecture.svg` | 模块架构图 |
| `AgentFlow.svg` | Agent 推理流程图 |
| `LlmAdapterFlow.svg` | LLM 适配器流程图 |
| `ToolInvocationSequence.svg` | 工具调用时序图 |
| `ConfigLoadingFlow.svg` | 配置加载流程图 |
| `ExtensionArchitecture.svg` | 扩展架构图 |
| `ExtensionLifecycle.svg` | 扩展生命周期图 |

### Docs/DevLog/

排障复盘与事故记录，按时间线命名（`DevLog-YYYYmmdd-HH.md`，见 `DocumentationSpec.md` §1）。

| 文件 | 说明 |
|---|---|
| `DevLog-20261007-20.md` | CI 必需检查因矩阵作业名变更而永久挂起（`TODO.md` `F-08`） |
| `DevLog-20261007-21.md` | 覆盖率门禁静默不可自证（`TODO.md` `F-09`） |

### .agents/skills/

| 技能 | 说明 |
|---|---|
| `build/SKILL.md` | 构建、打包、Maven 操作 |
| `test/SKILL.md` | 测试编写与执行 |
| `llm-adapter/SKILL.md` | 新增 LLM 供应商适配器 |
| `tool-authoring/SKILL.md` | 新增工具 |
| `extension-authoring/SKILL.md` | 新增扩展 |
| `release/SKILL.md` | 版本发布流程 |

## 附录 B：宪法修订记录

| 版本 | 日期 | 变更内容 |
|---|---|---|

---

**本文档为 AHA 项目的完整设计蓝图与技术实现方案，与工具宪法 v1.3.0 一致。所有开发活动必须符合本文档规定。文档命名、YAML 字段命名、扩展能力必须符合相应规范。**