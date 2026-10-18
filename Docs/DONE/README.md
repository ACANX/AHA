# 已完成待办（DONE）

**文档版本**：v1.0.0
**状态**：生效
**生效日期**：2026-10-18
**最后更新**：2026-10-18
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 0. 本目录的定位

**已完成待办的归档区**。`Docs/TODO/` 里的事项一旦标记完成，就移动到本目录：

```
Docs/TODO/TD-PPPPP-<标题>.md  ──完成──▶  Docs/DONE/DN-PPPPP-<标题>.md
```

- **目录变化、编号不变**：`TD-00030` → `DN-00030`，编号 `00030` 继续占位、不回收；
- 前缀 `TD-`（Todo）→ `DN-`（Done），只需改前缀与目录，标题不动；
- 归档文件**保留原文**（来源、需求、验收标准、实现记录），只更新头部状态与完成依据；
- 编号全局唯一由 `bin/CheckDocs.py` 的 `check_naming()` 钉住（TODO 与 DONE 共用编号空间）。

## 1. 归档 SOP（摘要）

规范正文：[`Docs/DevSpec/TodoLifecycleSpec.md`](../DevSpec/TodoLifecycleSpec.md) §3。

1. `git mv Docs/TODO/TD-PPPPP-<标题>.md Docs/DONE/DN-PPPPP-<标题>.md`；
2. 文件头：状态改 `✅ 已完成（yyyy-MM-dd）`，补「完成依据」（PR / commit / Issue）；
3. `Docs/TODO/README.md` 的清单行改为指向 `../DONE/DN-…`，状态 `✅ 已完成（已归档）`；
4. 本文件 §2 的清单追加一行；
5. 编号**不回收**：新待办从当前最大编号 + 1 继续。

## 2. 归档清单

| 编号 | 标题 | 完成日期 | 依据 |
|---|---|---|---|
| [DN-00030](DN-00030-TableRowSeparatorPolicy.md) | 表格数据行之间补水平分割线（A 全表加） | 2026-10-18 | PR [#290](https://github.com/ACANX/AHA/pull/290) / Issue [#273](https://github.com/ACANX/AHA/issues/273) |
