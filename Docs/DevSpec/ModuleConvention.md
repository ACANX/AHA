# 模块约定

**文档版本**：v1.1.0
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
| v1.1.0 | 2026-10-07 | 扩展机制统一为 Extension 命名：模块 `aha-extension-api`、包名、类名、配置段与描述符 | @ACANX |

---

## 1. JPMS

- 所有模块必须有 `module-info.java`
- 必须显式导出包
- `opens` 必须限定到具体模块（如 `opens ... to tools.jackson.databind`）

## 2. 依赖方向

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
- `cli` 与 `desktop` 不得互相依赖
- `core` 不得引用 JavaFX、picocli、JLine

## 3. 包结构

包结构已冻结，见 `Constitution.md` 第 3 条。
