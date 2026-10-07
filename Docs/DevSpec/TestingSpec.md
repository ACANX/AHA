# 测试规范

**文档版本**：v1.10.0
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
| v1.1.0 | 2026-10-07 | 覆盖率与用例数刷新为 0.1.0-SNAPSHOT 实测值；模块名统一为 `tool`；扩展目录统一为 `extension` | @ACANX |
| v1.2.0 | 2026-10-07 | 覆盖率与用例数刷新（新增上下文压缩与存储用例） | @ACANX |
| v1.3.0 | 2026-10-07 | 覆盖率与用例数刷新（新增 `/memory` 与编辑器探测用例） | @ACANX |
| v1.4.0 | 2026-10-07 | 覆盖率与用例数刷新（新增项目 ID 用例） | @ACANX |
| v1.5.0 | 2026-10-07 | 版本号改为构建期注入，补用例；覆盖率与用例数刷新 | @ACANX |
| v1.6.0 | 2026-10-07 | 覆盖率与用例数刷新（新增流式输入与取消用例） | @ACANX |
| v1.7.0 | 2026-10-07 | 覆盖率与用例数刷新（新增状态行用例；cli 略降因真实显示路径只能在伪终端验证） | @ACANX |
| v1.8.0 | 2026-10-07 | 覆盖率与用例数刷新（新增工具区块用例） | @ACANX |
| v1.9.0 | 2026-10-07 | 新增第 5 节「平台分支的验证要求」（含真实反面案例） | @ACANX |
| v1.10.0 | 2026-10-07 | §3.1 JaCoCo 版本更正为 0.8.15；§3.3 实测值按 `clean verify` 刷新（604 用例 / 合计行覆盖 80.4%），并写明统计口径与两条工具链一致性 | @ACANX |

---

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

## 2. Fixture 命名

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

### 3.1 工具与门禁

覆盖率由 `jacoco-maven-plugin 0.8.15` 采集，绑定于 `initialize` / `verify`：

```bash
./mvnw clean verify            # 生成报告 + 执行门禁
```

- 报告：`<module>/target/site/jacoco/index.html`
- 门禁：`BUNDLE` 级 `LINE COVEREDRATIO ≥ 0.70`，未达标 `verify` 失败

### 3.2 排除项

| 排除范围 | 理由 |
|---|---|
| `core/extension/**` | 扩展运行时属 0.3 |
| `core/mcp/**` | MCP 适配器为 0.2 占位 |
| `desktop/**` | 桌面端 0.2 实现 |
| `cli/ChatCommand*`、`cli/RunCommand*` | 依赖真实 TTY 与供应商 API Key |

### 3.3 实测值（0.1.0）

| 模块 | 行覆盖 | 分支覆盖 | 用例数 |
|---|---|---|---|
| `aha-common` | 90.2%（165/183） | 80.4% | 57 |
| `aha-extension-api` | 100.0%（23/23） | —（无分支） | 6 |
| `aha-core` | 74.6%（1449/1943） | 57.9% | 193 |
| `aha-tool` | 79.6%（148/186） | 77.8% | 25 |
| `aha-cli` | 83.8%（2246/2681） | 68.2% | 323 |
| **合计** | **80.4%（4031/5016）** | 64.8% | **604** |

数据取自 `./mvnw clean verify`；`mvn clean verify`（Maven 3.9.11）结果一致。
合计口径 = 各模块 `LINE_COVERED / (LINE_COVERED + LINE_MISSED)` 求和；
`aha-desktop` 为 0.1 占位模块，已从门禁排除，不计入合计。

## 4. LLM 适配器测试要求

- 使用固定 JSON fixture 验证转换正确性
- 不使用真实 API（CI 中通过环境变量控制，默认跳过）
- 测试覆盖：请求转换、响应转换、流式事件转换、错误处理

## 5. 平台分支的验证要求（强制）

凡涉及**平台分支**的行为与展示——shell 选择、路径分隔符、提示符形态、控制台编码、终端能力降级——
**必须在目标平台上验证**，不能用另一个平台的输出代替。

| 只能证明什么 | 不能证明什么 |
|---|---|
| 单元测试证明分支逻辑选对了 | 该平台上的**实际输出**是什么样 |
| 伪终端证明渲染序列发对了 | 该平台的 shell 是否真的这么执行 |
| 在另一个平台跑通 | 目标平台的路径 / 编码 / shell 语义 |

**反面案例（真实发生过）**：改动 `shell-exec` 的展示后，拿一条 **Windows 命令**
在 WSL 里跑 AHA 做验证——屏幕上出现的是 `/mnt/e/...$`（sh 提示符）与
`/bin/sh: 1: Syntax error`（sh 的报错），而用户在 Windows 上看到的是
`E:\...>` 与一次成功返回。**用错的环境当论据，等于证明了错的东西。**

正确做法：用目标平台的 JDK 直接调被测代码（本项目的 WSL ↔ Windows 互操作可用，
经 `/mnt/c/Windows/System32/cmd.exe /c` 即可驱动 Windows 侧），把原始输出
（含转义序列）落盘后再断言。探针自身也要注意：按字节累积再整体解码，
逐字节转 `char` 会把 UTF-8 多字节序列拆坏。

