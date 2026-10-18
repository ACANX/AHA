# TD-00026 CliToolCallReadFileChunking

> 待办编号：TD-00026
> 标题：工具调用：浏览文件输出「命令 + 文件内容」分块，过长折叠
> 状态：☐ 未开始
> 跟踪 Issue：[#72](https://github.com/ACANX/AHA/issues/72)
> 所属主题：[TD-00001](TD-00001-CliToolCallOutputRendering.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/Dbsx.txt` §1

## 原文（照抄，未改写）

> - 浏览文件
>   需要终端中分块展示对应的命令及对应文件中的内容，
>   文件内容过长可以适当折叠下部分内容；

## 最小动作

读文件类输出：命令块 + 内容块；内容过长时按同一折叠规则处理（与 TD-00025 共用实现）。

## 验收标准

- [ ] 单测断言：读文件输出含命令块与内容块；过长时折叠。

## 关联

- 所属主题：[TD-00001](TD-00001-CliToolCallOutputRendering.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#72](https://github.com/ACANX/AHA/issues/72)
