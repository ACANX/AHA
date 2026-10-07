<p align="center">
  <img src="aha-core/src/main/resources/Logo.svg" width="128" height="128" alt="AHA Logo">
</p>

<h1 align="center">AHA · Agent Harness for ACANX</h1>

<p align="center">
  <a href="https://github.com/ACANX/AHA/actions/workflows/Build.yml"><img src="https://github.com/ACANX/AHA/actions/workflows/Build.yml/badge.svg" alt="Build"></a>
  <img src="https://img.shields.io/badge/JDK-25%20LTS-blue.svg" alt="JDK 25 LTS">
  <img src="https://img.shields.io/badge/Maven-4.x%20%7C%203.9.x-orange.svg" alt="Maven 4.x / 3.9.x">
  <img src="https://img.shields.io/badge/JPMS-required-green.svg" alt="JPMS">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="Apache License 2.0"></a>
</p>

<p align="center">支持 CLI 与桌面端双模式运行的 Agent Harness。</p>

---

## 项目目标

AHA 的长期目标是成为**可独立承担研发工作的 Agent Harness**。

**1.0 硬门槛：AHA 自举。** 发布 1.0 正式版之前，本项目的开发、测试、构建、发布
以及后续功能的迭代升级，必须全部由 AHA 工具链完成，不再依赖其他 Agent Harness。
详见 [SelfHostingDesign.md](Docs/Design/SelfHostingDesign.md)。

## 项目状态

当前版本 **0.1.1**：Core 与 CLI 可运行，桌面端为占位模块。

逐版本的路线图（0.2 桌面端、0.3 扩展基础、……、1.0 自举达标）与能力缺口，
以 [AHA-Design-V1.md](Docs/AHA/AHA-Design-V1.md) 为准 —— 本文件不复制该清单，避免两处漂移。

## 核心能力

| 能力 | 说明 |
|---|---|
| LLM 适配 | OpenAI、Anthropic、Gemini 三类协议，可接入兼容中转站 |
| 工具调用 | 文件读写、Shell 执行、HTTP 请求；按后果分级授权 |
| 记忆管理 | SQLite 持久化、按项目隔离、会话与上下文压缩 |
| 配置与凭据 | 用户级 `~/.aha` 目录、环境变量、加密密钥库 |
| 交互体验 | 流式输出、Markdown 渲染、会话内命令、Tab 补全、右侧像素风标志 |
| 扩展契约 | SPI 契约已定（运行时自 0.3 起提供） |

## 快速开始

需要 **JDK 25**。构建发行包并初始化（首次使用会引导选择供应商与写入 API Key）：

```bash
./mvnw clean package                  # Linux / macOS
mvnw.cmd clean package                # Windows

dist/bin/Aha.sh init                  # 交互式初始化
dist\bin\Aha.bat init
```

非交互（脚本 / CI）：

```bash
dist\bin\Aha.bat init --provider DeepSeek --api-key sk-xxx
dist\bin\Aha.bat init --no-input
```

初始化生成 `~/.aha/Model.yml` 与数据目录，无需手工编写配置文件。
完整步骤见 [GettingStarted.md](Docs/Guide/GettingStarted.md)。

## 运行

在工作目录下直接运行即可进入交互（无需子命令）：

```bash
dist/bin/Aha.sh              # Linux / macOS
dist\bin\Aha.bat             # Windows
```

启动时在右侧打印 Logo（宽终端为像素风，窄终端自动降级为线框风，可用 `--logo` 指定），
并显示供应商、模型、端点、工作目录、项目根与会话 ID。也可显式指定子命令：

```bash
dist/bin/Aha.sh chat
dist\bin/Aha.bat run "用一句话介绍 AHA"
```

会话内可用 `/help`、`/model`、`/memory`、`/context`、`/compact` 等命令，
完整清单见 [ReferenceGuide.md](Docs/Guide/ReferenceGuide.md)。

发行包结构：

```
dist/
├── bin/     Aha.sh、Aha.bat
└── lib/     JPMS 模块路径（本项目模块 + 全部运行时依赖）
```

## 技术栈

| 类别 | 版本 |
|---|---|
| JDK | **25 (LTS)** |
| Maven 运行时 | **4.x**（Maven Wrapper 固定） |
| Maven 兼容基线 | **3.9.x** |
| OpenJFX | **25**（桌面端，0.2 启用） |
| JPMS | **优先启用（非强制）** |
| JSON/YAML | Jackson **3.x**（groupId `tools.jackson`） |

> 依赖的确切版本集中在父 `pom.xml` 的 `<properties>`，由 Dependabot 每日升级；
> 本表只写主版本线，避免与升级脱节。

## 模块

| 模块 | 说明 | 依赖 |
|---|---|---|
| `aha-common` | 共享 DTO、SPI、异常 | 无 |
| `aha-extension-api` | 扩展契约 | common |
| `aha-core` | 推理引擎、LLM 适配、存储、配置、扩展运行时 | common, extension-api |
| `aha-tool` | 工具实现（ServiceLoader） | common, core |
| `aha-cli` | 命令行入口（picocli + JLine） | core, tool |
| `aha-desktop` | 桌面端（OpenJFX，0.1 占位） | core |

## 构建

```bash
./mvnw clean verify          # Maven 4 运行时（含测试与覆盖率门禁）
mvn clean verify             # Maven 3.9.x 兼容验证
```

## 文档

| 文档 | 内容 |
|---|---|
| [AHA-Design-V1.md](Docs/AHA/AHA-Design-V1.md) | 设计蓝图与技术实现方案（唯一权威文档） |
| [GettingStarted.md](Docs/Guide/GettingStarted.md) | 安装、初始化与首次对话 |
| [ReferenceGuide.md](Docs/Guide/ReferenceGuide.md) | 命令、配置键与会话内命令参考 |
| [ConfigurationGuide.md](Docs/Guide/ConfigurationGuide.md) | 配置文件、目录结构与日志 |
| [ProviderSetupGuide.md](Docs/Guide/ProviderSetupGuide.md) | 各供应商接入方式 |
| [ToolUsageGuide.md](Docs/Guide/ToolUsageGuide.md) | 工具使用与权限规则 |
| [TroubleshootingGuide.md](Docs/Guide/TroubleshootingGuide.md) | 常见问题排查 |
| [CHANGELOG.md](CHANGELOG.md) | 版本变更记录 |

其余目录：`Docs/AHA/`（唯一权威设计文档）、`Docs/DevSpec/`（开发规范）、
`Docs/Design/`（子系统设计）、`Docs/Diagrams/`（图资源）、`Docs/PLAN.md`（开发计划与待办）。

## 许可证

[Apache License 2.0](LICENSE)
