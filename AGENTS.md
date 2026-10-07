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
# 或（需先执行 ./mvnw clean package 生成 dist/）
dist/bin/Aha.sh chat         # Linux / macOS
dist\bin\Aha.bat chat        # Windows
```

### 运行测试

```bash
./mvnw test                  # 全部测试
./mvnw -pl aha-core test     # 单模块测试
./mvnw clean verify          # 构建 + 测试 + 覆盖率门禁（≥ 70%）
```

覆盖率报告：`<module>/target/site/jacoco/index.html`。

### 检查分层

慢检查（覆盖率门禁、完整 `verify`、文档检查、重复率）**不再**跟每次改动一起跑——
它们集中在 CI 的 `Gate.yml`，只在合入 `main` 前与发布前执行。日常改动只需快检查：

| 场景 | 命令 |
|---|---|
| 日常改动（快） | `./mvnw -B clean test -Djacoco.skip=true` |
| 按变更选择检查 | `python3 bin/CheckChanged.py` |
| 重复率（改到 PMD 配置 / 阈值时） | `./mvnw -B pmd:cpd && python3 bin/CheckDuplication.py` |
| 合入 `main` 前（完整门禁，CI 亦会跑） | `./mvnw -B clean verify` + 四个 `Check*.py` + `GenPixelLogo.py --verify` |
| Maven 3.9.x 兼容（POM 改动时） | `mvn -B clean verify`（CI 由 `Compat.yml` 承担） |

CI 侧的分工：`Build.yml` 每次 push/PR 只做编译与单元测试；`Gate.yml` 承载
verify / 覆盖率 / 文档 / 技能 / 脚本 / 重复率，并在**合入前、每周定期、发布前**运行；
`Compat.yml` 承载 Maven 3.9.x 兼容验证。`Gate` 与 `Compat` 都应在 main 的
分支保护里设为必需检查。

判定规则与阈值见 [BuildSpec.md](Docs/DevSpec/BuildSpec.md) 第 8.1 节。

### 待人工执行的动作必须留痕

凡是**自动化做不到、必须由人完成**才能收口的事项——推送提交、仓库 / 分支保护设置、
平台侧配置、需要外部环境的验收——一律登记到
[TODO.md](Docs/TODO.md) 第 11 节（`G-xx`），写明**验收标准**（怎么算做完），
并在 [PLAN.md](Docs/PLAN.md) 的阻塞项中交叉引用。

**不得只在对话里交代。** 对话会滚走：漏掉之后既没有闭环，也无从判断「到底做过没有」，
事后连责任边界都说不清。同理，需要拍板的取舍项记在第 7 节并排优先级。

排障 / 事故类事项另有留痕去处：写一篇 [Docs/DevLog/](Docs/DevLog/) 下的
`DevLog-YYYYmmdd-HH.md`，必备「背景 / 排障过程与修复链 / 最终验证结果 / 关键教训 /
涉及文件清单」五个小节，并在 `TODO.md` 的相关条目里交叉引用（见 `DocumentationSpec.md` §4）。

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
- [BuildSpec.md](Docs/DevSpec/BuildSpec.md) - 构建规范
- [TestingSpec.md](Docs/DevSpec/TestingSpec.md) - 测试规范
- [YamlFieldSpec.md](Docs/DevSpec/YamlFieldSpec.md) - YAML 字段规范
- [CommitMessageSpec.md](Docs/DevSpec/CommitMessageSpec.md) - 提交规范
- [BranchStrategy.md](Docs/DevSpec/BranchStrategy.md) - 分支策略
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

## 用户指南

详见 `Docs/Guide/`：

- [GettingStarted.md](Docs/Guide/GettingStarted.md) - 快速开始
- [BuildGuide.md](Docs/Guide/BuildGuide.md) - 构建指南
- [ConfigurationGuide.md](Docs/Guide/ConfigurationGuide.md) - 配置指南
- [ProviderSetupGuide.md](Docs/Guide/ProviderSetupGuide.md) - 供应商设置
- [ToolUsageGuide.md](Docs/Guide/ToolUsageGuide.md) - 工具使用
- [ExtensionAuthoringGuide.md](Docs/Guide/ExtensionAuthoringGuide.md) - 扩展开发
- [TroubleshootingGuide.md](Docs/Guide/TroubleshootingGuide.md) - 排错指南

## 待办与计划

- [TODO.md](Docs/TODO.md) - 待办与调整项（暂存区）
- [DevLog/](Docs/DevLog/) - 排障复盘与事故记录（`DevLog-YYYYmmdd-HH.md`）
- [PLAN.md](Docs/PLAN.md) - 做不到 / 已决定暂缓 / 仍未做且有阻塞的事项，含判断依据

## 技能索引

详见 `.agents/skills/`：

| 技能（`name`） | 用途 | 入口 |
|---|---|---|
| `build` | 构建、打包、Maven Wrapper 操作 | [SKILL.md](.agents/skills/build/SKILL.md) |
| `test` | 单元测试、集成测试、覆盖率 | [SKILL.md](.agents/skills/test/SKILL.md) |
| `llm-adapter` | 新增 LLM 供应商适配器 | [SKILL.md](.agents/skills/llm-adapter/SKILL.md) |
| `tool-authoring` | 新增工具 | [SKILL.md](.agents/skills/tool-authoring/SKILL.md) |
| `extension-authoring` | 新增扩展 | [SKILL.md](.agents/skills/extension-authoring/SKILL.md) |
| `release` | 版本发布流程 | [SKILL.md](.agents/skills/release/SKILL.md) |

## 常用命令

| 命令 | 说明 |
|---|---|
| `./mvnw clean verify` | 完整构建 + 测试 + 覆盖率门禁 |
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

AHA中默认优先使用简体中文作为对话、思考、思维链、输出展示的语言；使用UTF-8作为缺省或默认的文本编码；
