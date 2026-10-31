# TD-00025 CliToolCallAddFileChunking

> 待办编号：TD-00025
> 标题：工具调用：新增文件输出「路径 + 内容」分块，超 500 行折叠
> 状态：☐ 未开始
> 跟踪 Issue：[#72](https://github.com/ACANX/AHA/issues/72)
> 所属主题：[TD-00001](TD-00001-CliToolCallOutputRendering.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/Dbsx.txt` §1

## 原文（照抄，未改写）

> - 添加文件
>   则需要终端中分块展示文件的路径及添加的内容，
>   如果内容过长（超过 500 行），需要适当的做下折叠；

## 最小动作

新增文件类输出：先打一行「路径」块，再按行分块展示内容；超过 500 行时自动折叠并提示省略行数。

## 验收标准

- [ ] 单测断言：≤500 行不折叠、>500 行折叠且给出「省略 N 行」提示。

## 关联

- 所属主题：[TD-00001](TD-00001-CliToolCallOutputRendering.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#72](https://github.com/ACANX/AHA/issues/72)
