# TD-00037 AgentEngineEmptyChoicesContent

> 待办编号：TD-00037
> 标题：`AgentEngine.run()`：空 `choices` 不得返回 `null` 正文
> 状态：☐ 未开始
> 跟踪 Issue：[#82](https://github.com/ACANX/AHA/issues/82)
> 所属主题：[TD-00011](TD-00011-CoreContractAndArchGovernance.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/TODO.md` `B-01`

## 原文（照抄，未改写）

> `AgentEngine.run()` 空 choices 时返回 `null` 正文。

## 最小动作

定语义并落地：返回空串 `content=""` 或抛领域异常，二选一写进 Javadoc。

## 验收标准

- [ ] 单测覆盖 `choices == null` 与 `choices.isEmpty()`；`AgentResponse.content()` 不再可能为 `null`。

## 关联

- 所属主题：[TD-00011](TD-00011-CoreContractAndArchGovernance.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#82](https://github.com/ACANX/AHA/issues/82)
