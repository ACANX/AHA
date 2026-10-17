# TD-00054 Maven4StderrNoise

> 待办编号：TD-00054
> 标题：Maven 4 `verify` 的 10 行 `[stderr]` 噪音
> 状态：☐ 未开始
> 跟踪 Issue：[#84](https://github.com/ACANX/AHA/issues/84)
> 所属主题：[TD-00013](TD-00013-EngineeringAndDocHygiene.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/TODO.md` `F-16`

## 原文（照抄，未改写）

> Maven 4 下 `verify` 日志出现 10 行 `[stderr]`（`sqlite-jdbc` 触发的 JDK native-access 警告），Maven 3.9.11 下为 0。

## 最小动作

定位来源并给出「消除 / 接受」结论（注意陷阱：不能简单写死 `argLine`，JaCoCo 也经它注入；`@{argLine}` 在 `-Djacoco.skip=true` 下会报错）。

## 验收标准

- [ ] 结论写进 `BuildSpec.md` §8；若消除，`Gate` 日志该段消失。

## 关联

- 所属主题：[TD-00013](TD-00013-EngineeringAndDocHygiene.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#84](https://github.com/ACANX/AHA/issues/84)
