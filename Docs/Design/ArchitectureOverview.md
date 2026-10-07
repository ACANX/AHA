# 架构总览

**文档版本**：v1.3.0
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
| v1.1.0 | 2026-10-07 | §6 质量基线按实测刷新（604 用例 / 合计行覆盖 80.4%） | @ACANX |
| v1.2.0 | 2026-10-07 | 覆盖率与用例数刷新（新增上下文压缩与存储用例） | @ACANX |
| v1.2.0 | 2026-10-07 | 构建行补 Maven 3.9.11 实测结论 | @ACANX |
| v1.3.0 | 2026-10-07 | 覆盖率与用例数刷新（新增 `/memory` 与编辑器探测用例） | @ACANX |
| v1.3.0 | 2026-10-07 | §6 质量基线按 Windows 修复后的实测刷新（604 用例 / 合计行覆盖 80.3%） | @ACANX |
| v1.4.0 | 2026-10-07 | 覆盖率与用例数刷新（新增项目 ID 用例） | @ACANX |
| v1.5.0 | 2026-10-07 | 版本号改为构建期注入，补用例；覆盖率与用例数刷新 | @ACANX |
| v1.6.0 | 2026-10-07 | 覆盖率与用例数刷新（新增流式输入与取消用例） | @ACANX |
| v1.7.0 | 2026-10-07 | 覆盖率与用例数刷新（新增状态行用例；cli 略降因真实显示路径只能在伪终端验证） | @ACANX |
| v1.8.0 | 2026-10-07 | 覆盖率与用例数刷新（新增工具区块用例） | @ACANX |

---

## 1. 模块拓扑

```
common ← extension-api ← core ← tool
common ← extension-api ← core ← cli
common ← extension-api ← core ← desktop
```

## 2. 分层

| 层 | 模块 | 职责 |
|---|---|---|
| 契约层 | `aha-common`、`aha-extension-api` | DTO、SPI、异常、扩展契约 |
| 内核层 | `aha-core` | Agent 引擎、LLM 适配、记忆、配置、安全、扩展运行时 |
| 实现层 | `aha-tool` | 内置工具（ServiceLoader） |
| 接入层 | `aha-cli`、`aha-desktop` | CLI / 桌面端 |

## 3. 关键设计

- **微内核 + 扩展**：核心稳定，边缘扩展化
- **IR 独立**：LLM 内部统一协议，适配器隔离外部协议
- **JPMS 强制**：类加载隔离与显式依赖
- **虚拟线程**：每个会话一个虚拟线程，禁止池化

## 4. 依赖矩阵（实现）

| 模块 | Maven artifactId | Java 模块名 | 依赖 |
|---|---|---|---|
| 契约层 | `aha-common` | `com.acanx.module.aha.common` | — |
| 契约层 | `aha-extension-api` | `com.acanx.module.aha.extension.api` | common |
| 内核层 | `aha-core` | `com.acanx.module.aha.core` | common, extension-api |
| 实现层 | `aha-tool` | `com.acanx.module.aha.tool` | common, core |
| 接入层 | `aha-cli` | `com.acanx.module.aha.cli` | core, tool |
| 接入层 | `aha-desktop` | `com.acanx.module.aha.desktop` | core |

代码包名与模块名一致（`com.acanx.module.aha.<module>`）。

## 5. 关键调用链（0.1.0 实测）

```
CLI（picocli）
  └─ CliContext（懒加载）
       └─ AgentServiceFactory.local(config)
            └─ LocalAgentService
                 ├─ ToolRegistry（ServiceLoader 发现 aha-tool 的 Provider）
                 ├─ SqliteMemoryStore（WAL）
                 ├─ SessionManager
                 ├─ DefaultLlmClient（HttpClient + SSE + 重试 + 限流）
                 └─ AgentEngine（推理循环 / 流式）
```

`ServiceLoader` 同时在 `module-info` 的 `provides` 与 `META-INF/services` 中声明，
兼容 JPMS 与 classpath 两种运行方式。

## 6. 质量基线（0.1.0）

| 指标 | 值 |
|---|---|
| 测试用例 | **604**（common 57 / extension-api 6 / core 193 / tool 25 / cli 323） |
| 行覆盖率（合计） | **80.3%**（4040/5033 行；JaCoCo 门禁 `BUNDLE` 行覆盖 ≥ 70%） |
| 构建 | `./mvnw clean verify`（Maven 4）与 `mvn clean verify`（Maven **3.9.11**）均通过，覆盖率数据一致 |
