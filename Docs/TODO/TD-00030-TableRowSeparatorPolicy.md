# TD-00030 TableRowSeparatorPolicy

> 待办编号：TD-00030
> 标题：表格数据行之间是否补水平分割线（先拍板再实现）
> 状态：☐ 未开始
> 跟踪 Issue：[#73](https://github.com/ACANX/AHA/issues/73)
> 所属主题：[TD-00002](TD-00002-CliMarkdownRichRendering.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/PLAN.md` §7.1

## 原文（照抄，未改写）

> **要求**：数据行之间也加水平分割线（`├──┼──┤`），形成完整网格。
> **实现位置**：`TableRenderer#render`——在输出每一行之后判断是否需要补一条中间分隔线，
> 改动约 3 行（`if (i > 0) { out.append(分界线) }`）。
> **待定**：Markdown 表格本身并没有行分隔线的语义，加上之后视觉更重（每行都被框住）。
> 若确认要加，需一并决定**是否所有表格都加**（还是只在列数多、行数多时才加）。

## 最小动作

先拍板口径（全表加 / 仅宽表加 / 不加）；若加，改 `TableRenderer#render` 补中间分隔线；若不加，把结论写进本文件。

## 验收标准

- [ ] 本文件有明确结论；若实现，表格渲染单测同时覆盖「有分隔线」与「无分隔线」两种策略。

## 关联

- 所属主题：[TD-00002](TD-00002-CliMarkdownRichRendering.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#73](https://github.com/ACANX/AHA/issues/73)
