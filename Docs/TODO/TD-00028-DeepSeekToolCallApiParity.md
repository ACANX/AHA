# TD-00028 DeepSeekToolCallApiParity

> 待办编号：TD-00028
> 标题：补齐 DeepSeek 工具调用缺失的命令 / API
> 状态：☐ 未开始
> 跟踪 Issue：[#72](https://github.com/ACANX/AHA/issues/72)
> 所属主题：[TD-00001](TD-00001-CliToolCallOutputRendering.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/Dbsx.txt` §1

## 原文（照抄，未改写）

> - 对于以 DeepSeek 大模型的工具调用中需要支持的调用相关的命令如果有缺失的，
>   需要补齐对应的工具处理 API，否则工具调用功能不完善，
>   可能导致循环调用中某一步骤调用失败使得工具无法继续执行下去，
>   需要补齐对应的工具调用的能力。

## 最小动作

对照 DeepSeek 的工具调用协议与现有工具集，列出缺失项并逐个补齐（含解析、执行、回填消息三段）。

## 验收标准

- [ ] 缺失项清单为空（或有明确「不补」结论 + 理由）；多轮工具调用链能连续跑通的用例至少 1 条。

## 关联

- 所属主题：[TD-00001](TD-00001-CliToolCallOutputRendering.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#72](https://github.com/ACANX/AHA/issues/72)
