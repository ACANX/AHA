# TD-00031 MarkdownRenderingGapsPolicy

> 待办编号：TD-00031
> 标题：渲染缺口逐项结论：图片 / 语法高亮 / OSC 8 / 单元格换行
> 状态：⏸ 待决策（[#274](https://github.com/ACANX/AHA/issues/274)）
> 跟踪 Issue：[#274](https://github.com/ACANX/AHA/issues/274)（本任务的决策单）
> 所属主题：[TD-00002](TD-00002-CliMarkdownRichRendering.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/PLAN.md` §7.3

## 原文（照抄，未改写）

> | 项 | 现状 |
> |---|---|
> | 图片（`![alt]` 加 `(url)`） | 不渲染，保留字面量（纯文本终端无法显示） |
> | 语法高亮 | 围栏代码原样输出 |
> | 链接的 OSC 8 超链接 | 不生成，只用「文本 + 弱化地址」（重定向到文件会变乱码） |
> | 表格单元格内换行 | 不支持，单元格内容按单行处理 |

## 最小动作

四项各给「做 / 明确不做 + 理由」结论；决定要做的项拆成实现任务。

## 验收标准

- [ ] 四项各有结论写在本文档；「要做的」项有对应实现任务单与验收。

## 关联

- 所属主题：[TD-00002](TD-00002-CliMarkdownRichRendering.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#73](https://github.com/ACANX/AHA/issues/73)
- 决策单：[#274](https://github.com/ACANX/AHA/issues/274)（挂 V0.2.0）
