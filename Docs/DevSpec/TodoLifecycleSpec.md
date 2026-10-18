# 待办生命周期规范

**文档版本**：v1.0.0
**状态**：冻结（强制）
**生效日期**：2026-10-18
**最后更新**：2026-10-18
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-18 | 首版：待办的创建 / 跟踪 / 完成 / 归档（`Docs/TODO/TD-…` → `Docs/DONE/DN-…`，目录变、编号不变） | @ACANX / CNXNC |

---

## 1. 范围

本规范约束 `Docs/TODO/` 下待办的**全生命周期**：

```
创建（TD-PPPPP）
   │
   ├─ 跟踪中（TODO，状态：⏸ 待决策 / 🚧 实施中 / ⛔ 阻塞）
   │
   └─ 完成 ──▶ 归档（DONE，DN-PPPPP，状态：✅ 已完成）
```

- **创建 / 跟踪**：文件名形态与目录见 [DocumentationSpec.md](DocumentationSpec.md) §1；
  单个待办的详情格式见 [`Docs/TODO/README.md`](../TODO/README.md)；
- **完成 / 归档**：本文档 §3 / §4。

## 2. 编号规则

- 编号 `PPPPP` 为**五位数字，从 `00001` 起全局自增**，不复用、不跳号；
- **TODO 与 DONE 共用同一编号空间**：归档不改编号，只换目录与前缀；
- 新待办取「TODO + DONE 中出现过的最大编号 + 1」。

## 3. 完成后的归档 SOP（强制）

**原则：目录调整、编号不变。**

### 3.1 操作步骤（按顺序执行）

1. `git mv Docs/TODO/TD-PPPPP-<标题>.md Docs/DONE/DN-PPPPP-<标题>.md`
   —— 用 `git mv` 保留历史；`Docs/DONE/` 不存在时先建目录；
2. 改文件头：
   - 标题行 `# TD-PPPPP …` → `# DN-PPPPP …`；
   - `> 待办编号` 改为 `**DN-PPPPP**（原 TD-PPPPP，完成后归档）`；
   - `> 状态` 改为 `✅ 已完成（YYYY-MM-DD）`；
   - 新增 `> 完成依据`：PR 编号 + squash commit + 关联 Issue（已关闭）；
3. `Docs/TODO/README.md`：该行改为指向 `../DONE/DN-PPPPP-….md`，状态写 `✅ 已完成（已归档）`；
4. `Docs/DONE/README.md` 的「归档清单」追加一行（编号 / 标题 / 完成日期 / 依据）；
5. 修正**双向引用**：其他文档里指向该待办的链接改到新路径（`CheckDocs` 会拦下断链）。

### 3.2 为什么「编号不变」

- 编号是**检索钥匙**：Issue、PR、DevLog、Troubleshooting 里都可能引用 `TD-00030`；
  改号会让这些引用全部失配；
- 归档只是「状态与位置」的变化，不是「另一件事」，编号必须稳定。

### 3.3 机器检查

`bin/CheckDocs.py` 的 `check_naming()` 钉住两条：

- `Docs/DONE/` 下文件名必须匹配 `^DN-\d{5}-[A-Za-z0-9]+\.md$`（`README.md` 例外）；
- **TODO 与 DONE 共用编号空间**：同一编号在两边各出现一次即判红
  （防止「归档时又新建了一份 TD」）；
- 链接检查（同脚本）会拦住移动后未修正的引用。

### 3.4 反例（禁止）

- 直接 `rm` 掉 TD 文件（丢失来源、验收与实现记录）；
- 改编号（如 `TD-00030` → `DN-00031`）；
- 留在 `Docs/TODO/` 只把状态改成 ✅（TODO 目录只放**未完成**事项）；
- 只改 `Docs/DONE/README.md` 清单、不移动文件；
- 移动后不改其他文档里的链接（断链会被 `CheckDocs` 判红）。

## 4. 与其他文档的关系

| 文档 | 关系 |
|---|---|
| [DocumentationSpec.md](DocumentationSpec.md) | 文件名形态与存放目录；§7 指向本文档 |
| [`Docs/TODO/README.md`](../TODO/README.md) | 待办清单索引与状态看板 |
| [`Docs/DONE/README.md`](../DONE/README.md) | 已归档待办的清单索引 |
| [IssueTrackingSpec.md](IssueTrackingSpec.md) | Issue 侧的状态机与关闭规范；待办与 Issue 可互相引用，但归档只针对 `TD-` 文件 |

## 5. 实例

- `Docs/TODO/TD-00030-TableRowSeparatorPolicy.md` → `Docs/DONE/DN-00030-TableRowSeparatorPolicy.md`
  （2026-10-18，PR [#290](https://github.com/ACANX/AHA/pull/290)，Issue [#273](https://github.com/ACANX/AHA/issues/273)）
