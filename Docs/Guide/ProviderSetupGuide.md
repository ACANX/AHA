# 供应商设置指南

**文档版本**：v1.3.0
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
| v1.1.0 | 2026-10-06 | 供应商配置迁移至 `Model.yml`；补充一键切换与增删流程 | @ACANX |
| v1.2.0 | 2026-10-06 | `Model.yml` 默认位置改为用户级目录（`~/.aha`），不再依赖工作目录 | @ACANX |
| v1.3.0 | 2026-10-06 | 补充 `aha init` 初始化流程 | @ACANX |

---

## 1. 配置文件

供应商明细位于独立的 `Model.yml`（与主配置 `Aha.yaml` 分离），默认位置：

```
$AHA_HOME/Model.yml        已设置 AHA_HOME
~/.aha/Model.yml           未设置 AHA_HOME（默认）
```

查看当前实际生效的路径：`aha config get Llm.ModelFile`。
因该文件包含 API Key，采用业界通行做法放在**用户级目录**而非工作目录（如同 `~/.aws/credentials`）。

推荐用初始化命令生成，而非手工编写：

```bash
aha init                              # 交互式：列出内置供应商供选择，可选写入 API Key
aha init --provider DeepSeek          # 指定默认供应商（非交互）
```

```yaml
Model:
  Default: DeepSeek          # 当前默认供应商

  Providers:
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
```

未创建该文件时，使用 classpath 的 `ModelDefault.yml`（内置 6 个供应商）。
主配置中只需声明兜底模型：

```yaml
Aha:
  Llm:
    Fallback:
      Provider: OpenAI
      Model: gpt-4o
    ModelFile: "${AHA_HOME:-~/.aha}/Model.yml"
```

需要项目级配置时显式覆盖（该文件含密钥，须加入 `.gitignore`）：

```yaml
    ModelFile: "./Model.yml"
```

## 2. 内置供应商

| 键名 | Adapter | BaseUrl | 默认模型 | API Key 环境变量 |
|---|---|---|---|---|
| `OpenAI` | `openai-compatible` | `https://api.openai.com/v1` | `gpt-4o` | `AHA_API_KEY_OPENAI` |
| `Anthropic` | `anthropic` | `https://api.anthropic.com` | `claude-sonnet-5-1` | `AHA_API_KEY_ANTHROPIC` |
| `Gemini` | `gemini` | `https://generativelanguage.googleapis.com` | `gemini-2.5-flash` | `AHA_API_KEY_GEMINI` |
| `DeepSeek` | `openai-compatible` | `https://api.deepseek.com/v1` | `deepseek-chat` | `AHA_API_KEY_DEEPSEEK` |
| `BigModelCN` | `openai-compatible` | `https://open.bigmodel.cn/api/paas/v4` | `glm-4.6` | `AHA_API_KEY_BIG_MODEL_CN` |
| `Qwen` | `openai-compatible` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-max` | `AHA_API_KEY_QWEN` |

## 3. 一键切换供应商

```bash
aha provider list             # 列出全部供应商，标记当前默认
aha provider use DeepSeek     # 一键切换
aha provider list             # DeepSeek 现在标记为 <== 当前默认
```

输出示例：

```
配置文件: /home/me/.aha/Model.yml
NAME             ADAPTER              MODEL                    REMARK
OpenAI           openai-compatible    gpt-4o
DeepSeek         openai-compatible    deepseek-chat            <== 当前默认
BigModelCN       openai-compatible    glm-4.6
```

切换只更新 `Model.yml` 的 `Default` 字段，主配置不受影响。

## 4. 新增供应商

```bash
# 内置预设已覆盖常见厂商；接入私有部署或中转站时新增
aha provider add MyProxy \
    --adapter openai-compatible \
    --base-url https://proxy.example/v1 \
    --model gpt-4o-mini \
    --api-key '${AHA_API_KEY_MY_PROXY}' \
    --rpm 60 \
    --default
```

- 供应商 ID 必须为 PascalCase
- 省略 `--api-key` 时自动写入 `${AHA_API_KEY_<ID>}` 占位
- `--default` 可同时将其设为默认供应商

## 5. 查看与校验

```bash
aha provider test MyProxy
```

```
供应商: MyProxy
适配器: openai-compatible
BaseUrl: https://proxy.example/v1
模型: gpt-4o-mini
API Key: 未配置（请设置对应环境变量）
所需环境变量: AHA_API_KEY_MY_PROXY
```

该命令**不发起真实请求**，仅校验配置可解析并提示所需环境变量。

## 6. 删除供应商

```bash
aha provider remove MyProxy       # 提示需要确认
aha provider remove MyProxy -y    # 确认删除
```

若删除的是当前默认供应商，`Default` 会被清空，运行时按兜底规则回退。

## 7. 中转站（sub2api / new-api）

作为普通 `openai-compatible` provider 接入，`Extra` 可自定义鉴权：

```yaml
Model:
  Default: NewApi
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

适配器已做的兼容处理：

- 流式响应缺失 `[DONE]` 时，依据 `finish_reason` 收尾
- `tool_calls` 增量按 `index` 聚合
- 非标准 JSON 行记录后跳过，不中断整条流
- 缺失 `usage` 时降级为 0 值

## 8. 环境变量

`ApiKey` 支持 `${AHA_XXX}` 语法：

```bash
export AHA_API_KEY_DEEPSEEK=sk-...
```

未设置时占位符会**保留原文**，`aha provider test` 会提示所需变量名。
环境变量优先级高于加密密钥库。

## 9. 降级回退

当默认供应商不存在或不可用时，运行时回退到主配置的兜底模型：

```yaml
Aha:
  Llm:
    Fallback:
      Provider: OpenAI
      Model: gpt-4o
```
