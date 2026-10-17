# TD-00038 AgentEngineStreamFinishReason

> 待办编号：TD-00038
> 标题：`AgentEngine.stream()`：`finishReason` 的兜底值
> 状态：☐ 未开始
> 跟踪 Issue：[#82](https://github.com/ACANX/AHA/issues/82)
> 所属主题：[TD-00011](TD-00011-CoreContractAndArchGovernance.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/TODO.md` `B-02`

## 原文（照抄，未改写）

> `AgentEngine.stream()` 的 `finishReason` 可能为 `null`。

## 最小动作

为「流中断 / 无 `DONE` 事件」定义兜底值并写进 Javadoc。

## 验收标准

- [ ] 单测断言：无 DONE 事件时 `finishReason` 等于约定的兜底值。

## 关联

- 所属主题：[TD-00011](TD-00011-CoreContractAndArchGovernance.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#82](https://github.com/ACANX/AHA/issues/82)
