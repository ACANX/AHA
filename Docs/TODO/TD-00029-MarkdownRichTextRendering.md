# TD-00029 MarkdownRichTextRendering

> 待办编号：TD-00029
> 标题：会话总结的 Markdown 渲染成富文本（彩色 / 分格式）
> 状态：☐ 未开始
> 跟踪 Issue：[#73](https://github.com/ACANX/AHA/issues/73)
> 所属主题：[TD-00002](TD-00002-CliMarkdownRichRendering.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/Dbsx.txt` §2

## 原文（照抄，未改写）

> 对于工具调用完成后的会话任务完成后的总结发言，目前输出的是原生的 markdown 格式的文档文本，
> 但是在终端中不会被渲染成对应的富文本格式，需要 Harness 侧对这部分的文本做识别，
> 并格式化 / 美化其展示，就像 pi / claude code 中处理成彩色 / 多色带格式区分的富文本。

## 最小动作

对会话总结正文走 Markdown 渲染管线（标题/列表/强调/代码块/表格分色），而非原样输出。

## 验收标准

- [ ] 至少 1 条端到端断言：总结正文里标题、列表、行内代码分别带上对应样式（快照或样式标记）。

## 关联

- 所属主题：[TD-00002](TD-00002-CliMarkdownRichRendering.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#73](https://github.com/ACANX/AHA/issues/73)
