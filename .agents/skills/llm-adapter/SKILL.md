---
name: llm-adapter
description: 新增 LLM 供应商适配器：实现 LlmProviderAdapter、在 module-info 与 META-INF/services 中声明、补充 ModelDefault.yml 预设与固定 JSON fixture 测试。当需要接入新供应商、适配新 API 形态或修改统一 IR 时使用。
license: Apache-2.0
compatibility: 需要 JDK 25；仅依赖 JDK HttpClient，不引入 HTTP 客户端库；适配器必须离线可测（固定 fixture，无真实网络调用）。
metadata:
  version: "1.0.0"
  owner: ACANX
---

# 新增 LLM 供应商适配器

适配器负责在**供应商原生协议**与 AHA **统一中间表示（IR）**之间双向转换。Core 不感知具体供应商。

## 何时使用

- 接入新的 LLM 供应商
- 新增供应商 API 形态（如非 OpenAI 兼容的流式协议）
- 修改统一 IR 或工具调用表示

## 步骤

1. 在 `aha-core/src/main/java/com/acanx/module/aha/core/llm/<provider>/` 新建实现 `LlmProviderAdapter` 的类
2. 在 `aha-core/src/main/java/module-info.java` 的 `provides` 中声明该实现
3. 在 `aha-core/src/main/resources/META-INF/services/` 下同步声明（保证 classpath 模式也可发现）
4. 在 `aha-core/src/main/resources/ModelDefault.yml` 增加供应商预设
5. 添加固定 JSON fixture 与单元测试，覆盖请求构造与流式响应解析

## 约束

- 适配器不得被 Core 反向依赖：Core 只通过 SPI 发现实现
- 不引入额外 HTTP 客户端依赖，统一使用 JDK `HttpClient`（配合 `DefaultLlmClient` 的重试、限流、取消）
- 供应商的鉴权信息通过环境变量占位符声明（如 `${AHA_API_KEY_<ID>}`），不写入配置文件明文
- 流式解析必须处理分片不完整、多字节字符跨片、`[DONE]` 终止等边界

## 参考

本技能目录内的资源：

- [assets/MyProviderAdapter.java](assets/MyProviderAdapter.java) - 适配器骨架（复制后重命名类与包名）
- [references/fixtures.md](references/fixtures.md) - 固定 fixture 的位置与约定

仓库文档：

- [LlmAdapterDesign.md](../../../Docs/Design/LlmAdapterDesign.md) - 适配器设计
- [ProviderSetupGuide.md](../../../Docs/Guide/ProviderSetupGuide.md) - 供应商配置指南
- [yaml-field 规范](../../../Docs/DevSpec/YamlFieldSpec.md) - YAML 字段命名（AHA 自有字段用 PascalCase）
