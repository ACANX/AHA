# DN-00030 TableRowSeparatorPolicy

> 待办编号：**DN-00030**（原 `TD-00030`，完成后归档）
> 标题：表格数据行之间补水平分割线（已拍板：全表加）
> 状态：✅ 已完成（2026-10-18）
> 完成依据：PR [#290](https://github.com/ACANX/AHA/pull/290)（squash commit `d14da0b`）/ Issue [#273](https://github.com/ACANX/AHA/issues/273)（已关闭）
> 跟踪 Issue：[#273](https://github.com/ACANX/AHA/issues/273)（本任务的决策单）
> 所属主题：[TD-00002](../TODO/TD-00002-CliMarkdownRichRendering.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-18

---

## 来源

原 `Docs/PLAN.md` §7.1

## 原文（照抄，未改写）

> **要求**：数据行之间也加水平分割线（`├──┼──┤`），形成完整网格。
> **实现位置**：`TableRenderer#render`——在输出每一行之后判断是否需要补一条中间分隔线，
> 改动约 3 行（`if (i > 0) { out.append(分界线) }`）。
> **待定**：Markdown 表格本身并没有行分隔线的语义，加上之后视觉更重（每行都被框住）。
> 若确认要加，需一并决定**是否所有表格都加**（还是只在列数多、行数多时才加）。

## 结论

**2026-10-17，ACANX 拍板：选 A —— 全表加。**

- 所有 `TableRenderer` 渲染的表格，数据行之间**一律**补 `├──┼──┤`；
- **不设判据**：不看列数、不看行数、不看是否「宽表」；B（仅宽表加）不采用；
- **无例外**：单行数据表、两列小表也按同一规则（只有一行时自然没有中间线）；
- 视觉更重这点接受：完整网格换来的是「横向对得齐」，大表收益更大；
- 最后一行数据之后不补中间线，仍以底边收尾。

## 实现

- `aha-cli/src/main/java/com/acanx/module/aha/cli/render/TableRenderer.java`
  - 主循环改为 `if (i < body.size() - 1) { 补中间线 }`：表头之后、每两个数据行之间都补，
    最后一行不补；
  - 中间线与顶边 / 表头分隔使用同一字符集（`├` `┼` `┤` `─`，ASCII 退化为 `+` `-`），
    列宽与当前表格一致。
- 单测 `TableRendererTest`：
  - `addsSeparatorBetweenEveryDataRow`：表头 + 3 行 ⇒ 3 条中间线、9 行、末行是底边；
  - `singleRowTableHasNoMiddleSeparator`：单行（无表头）⇒ 3 行；带表头的单行 ⇒ 5 行；
  - `separatorWidthMatchesCjkColumns`：CJK 宽字符表的每行显示宽度一致，中间线含 `┼`；
  - `ignoresEmptyInput`：空表 / 全空单元格 ⇒ 空串（无中间线）。

## 验收标准

- [x] 本文件有明确结论（A 全表加）；
      ✅ 证据：本文件「结论」一节 + [#273](https://github.com/ACANX/AHA/issues/273) 的「结论」一节。
- [x] 实现：`TableRenderer#render` 按「全表加」补中间线。
      ✅ 证据：`TableRenderer.java` 主循环 `if (i < body.size() - 1)`。
- [x] 单测覆盖：单行 / 多行 / 宽字符 / 空表四类。
      ✅ 证据：`TableRendererTest` 的 4 个用例（见「实现」一节）。

## 关联

- 所属主题：[TD-00002](../TODO/TD-00002-CliMarkdownRichRendering.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#73](https://github.com/ACANX/AHA/issues/73)
- 决策单：[#273](https://github.com/ACANX/AHA/issues/273)（挂 V0.2.0）
