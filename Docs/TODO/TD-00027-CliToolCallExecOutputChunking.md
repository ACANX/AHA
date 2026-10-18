# TD-00027 CliToolCallExecOutputChunking

> 待办编号：TD-00027
> 标题：工具调用：执行命令输出「命令 + 结果」分块
> 状态：☐ 未开始
> 跟踪 Issue：[#72](https://github.com/ACANX/AHA/issues/72)
> 所属主题：[TD-00001](TD-00001-CliToolCallOutputRendering.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/Dbsx.txt` §1

## 原文（照抄，未改写）

> - 执行脚本 / 命令
>   需要分块展示执行的命令及执行的结果或关键的输出；

## 最小动作

执行类工具输出：命令块 + 结果块；结果过长时折叠，并保留退出码与错误摘要。

## 验收标准

- [ ] 单测断言：执行类输出含命令块、结果块与退出码；过长折叠。

## 关联

- 所属主题：[TD-00001](TD-00001-CliToolCallOutputRendering.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#72](https://github.com/ACANX/AHA/issues/72)
