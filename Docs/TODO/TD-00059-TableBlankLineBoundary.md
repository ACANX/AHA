# TD-00059 TableBlankLineBoundary

> 待办编号：TD-00059
> 标题：表格块边界——空行应终止表格，且表格与正文之间保留空行
> 状态：☐ 未开始（[#346](https://github.com/ACANX/AHA/issues/346)）
> 跟踪 Issue：[#346](https://github.com/ACANX/AHA/issues/346)（本任务的跟踪单）
> 所属主题：[TD-00002](TD-00002-CliMarkdownRichRendering.md)
> 创建日期：2026-10-19
> 最后更新：2026-10-19

---

## 来源

验收 PR [#345](https://github.com/ACANX/AHA/pull/345) 时，用一份 93 行的「标题 + 列表 + 删除线 + 表格」
组合样例实测发现。同轮已修的另一处（表格后**第一行**丢掉行首判定）见
`Docs/DevLog/20261019-11.md`；本单跟踪**未修的第二处**。

## 现状

两个缺陷同源（详见 [#346](https://github.com/ACANX/AHA/issues/346) 的「现状」一节，含输出对照）：

1. **表格后的空行被吞掉**：源文档里表格与后文之间的空行不输出，表格前有空行、表格后没有，观感不对称；
2. **空行不终止表格**：两张以空行分隔的表格被合并成一张，列宽取并集，第二张表的单元格错位到第一列。

代码层原因：

| 位置 | 现状 |
|---|---|
| `MarkdownStream.stepTable()` | 收到 `\n` 时无条件 `tableRows.add(...)`，不区分数据行与空行 |
| `MarkdownStream.stepTable()` 结束判定 | 只有「行首首字符不是 `\|`」才结算表格；空行先被当空行收进 `tableRows` |
| `TableRenderer.render()` | 跳过全空行，所以那个空行也不会被打印 |

## 最小动作

按 #346 的方案 A 落地：

1. `stepTable()` 收到 `\n` 且 `tableLine` 为空（空行）时：先 `flushTable()`，再把该行按普通空行输出（走 `endLine()`）；
2. 补单测：空行终止表格、双表不合并、表格前后空行对称；
3. 同步 `Docs/Guide/ReferenceGuide.md` §2.5 与 `Docs/Design/TUIDesign.md` §3.5 的表格示例（若输出行距变化）；
4. 因输出会多一个空行，顺手确认 #345 的 4 条相邻组合用例不退化。

## 验收标准

- [ ] 空行之后的 `|` 行开启新表格，不与前表合并，列宽独立；
- [ ] 表格块与后文之间保留一个空行，与表格前空行对称；
- [ ] 表格后紧邻标题 / 列表 / 引用 / 围栏仍正常（#345 的 4 条用例不退化）；
- [ ] `MarkdownStreamTest` 新增 ≥3 条用例，CI `Build` 全绿；
- [ ] 用快照包 + 93 行样例做真机复验，观感以 ACANX 的判断为准。

## 关联

- 跟踪 Issue：[#346](https://github.com/ACANX/AHA/issues/346)
- 渲染基线：[#335](https://github.com/ACANX/AHA/issues/335)；伴生修复 PR [#345](https://github.com/ACANX/AHA/pull/345)
- 相邻但不同：[#339](https://github.com/ACANX/AHA/issues/339)（单元格**内**换行）、[#273](https://github.com/ACANX/AHA/issues/273)（数据行分割线，已关闭）
- 代码：`MarkdownStream.stepTable()` / `flushTable()`、`TableRenderer.render()`
