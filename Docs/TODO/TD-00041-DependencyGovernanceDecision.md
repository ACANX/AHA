# TD-00041 DependencyGovernanceDecision

> 待办编号：TD-00041
> 标题：依赖治理：Enforcer / ArchUnit 引入与否
> 状态：☐ 未开始
> 跟踪 Issue：[#82](https://github.com/ACANX/AHA/issues/82)
> 所属主题：[TD-00011](TD-00011-CoreContractAndArchGovernance.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/TODO.md` `C-03`

## 原文（照抄，未改写）

> 父 POM 无 `maven-enforcer-plugin`、无 ArchUnit。评估引入 `bannedDependencies` 或 ArchUnit 作为 JPMS 的**补充**，把 `BuildSpec.md` §7 的约定变成构建期失败。

## 最小动作

给出「引入 / 不引入」结论；若引入则选型 + 首条规则。

## 验收标准

- [ ] 结论写进 `BuildSpec.md` §7；若引入，`Constitution.md` 第 5 条选型清单同步。

## 关联

- 所属主题：[TD-00011](TD-00011-CoreContractAndArchGovernance.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#82](https://github.com/ACANX/AHA/issues/82)
