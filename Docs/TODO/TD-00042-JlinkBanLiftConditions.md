# TD-00042 JlinkBanLiftConditions

> 待办编号：TD-00042
> 标题：`BuildSpec.md` §7 补 `jlink` 禁令的解除条件
> 状态：☐ 未开始
> 跟踪 Issue：[#82](https://github.com/ACANX/AHA/issues/82)
> 所属主题：[TD-00011](TD-00011-CoreContractAndArchGovernance.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/TODO.md` `C-04`

## 原文（照抄，未改写）

> `BuildSpec.md` §7 仍写「禁止在 0.1 使用 `jlink`——`sqlite-jdbc` 为自动模块」，未补解除条件，读起来像永久约束。若该驱动未来提供 `module-info`，`jlink` 可显著缩小发行包，应重新评估。

## 最小动作

补一段「解除条件」：触发条件（驱动提供 `module-info` 等）+ 重新评估动作。

## 验收标准

- [ ] `BuildSpec.md` §7 有该段；表述不再是永久禁令。

## 关联

- 所属主题：[TD-00011](TD-00011-CoreContractAndArchGovernance.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#82](https://github.com/ACANX/AHA/issues/82)
