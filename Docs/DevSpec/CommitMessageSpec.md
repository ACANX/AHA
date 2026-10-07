# 提交信息规范

**文档版本**：v1.1.0
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
| v1.1.0 | 2026-10-06 | 新增自举期 `Harness:` 标注约定；修正 scope（`tools` → `tool`） | @ACANX |

---

## 1. 格式

采用 Conventional Commits：

```
<type>(<scope>): <subject>

<body>

<footer>
```

## 2. type

`feat`、`fix`、`docs`、`style`、`refactor`、`test`、`chore`、`build`、`ci`

## 3. scope

模块名：`common`、`extension-api`、`core`、`tool`、`cli`、`desktop`、`docs`

## 4. 示例

```
feat(core): 新增 Anthropic 适配器

实现 LlmProviderAdapter 接口，支持 Anthropic Messages 协议。
包含请求转换、响应转换、流式事件转换。

Closes #123
```

## 5. Harness 标注（自举期）

自举期间（0.2 ~ 1.0），由 AHA 产出的提交须在 footer 标注 `Harness:` 行：

```
feat(core): 新增 XX 能力

<正文>

Harness: aha/0.2.0
Session: <session-id>
```

- `Harness: <名称>/<版本>`：标识产出该提交的 Agent Harness
- `Session: <session-id>`：可选，便于回溯会话记录
- 人工提交不标注 `Harness` 行

该约定用于自举度量与审计（见 [SelfHostingDesign.md](../Design/SelfHostingDesign.md) 第 4 节），
不参与 CI 门禁。
