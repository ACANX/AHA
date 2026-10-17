# TD-00052 WorkflowYamlStaticCheck

> 待办编号：TD-00052
> 标题：`.github/**/*.yml` 是否纳入静态检查
> 状态：⏸ 待决策（[#280](https://github.com/ACANX/AHA/issues/280)）
> 跟踪 Issue：[#280](https://github.com/ACANX/AHA/issues/280)（本任务的决策单）
> 所属主题：[TD-00013](TD-00013-EngineeringAndDocHygiene.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/TODO.md` `F-14`

## 原文（照抄，未改写）

> `.github/**/*.yml` 没有任何本地检查：`bin/CheckScripts.py` 只覆盖 `.bat`/`.cmd`/`.sh`/`.py`，工作流语法写错只能等 GitHub 判。

## 最小动作

决定是否纳入（纳入则用 `yaml.safe_load` 全量解析，并覆盖 `actionlint` 不做的「字段拼错」类问题）。

## 验收标准

- [ ] 结论；若纳入，`bin/CheckScripts.py` 新增加一条并在 CI 日志可见。

## 关联

- 所属主题：[TD-00013](TD-00013-EngineeringAndDocHygiene.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#84](https://github.com/ACANX/AHA/issues/84)
- 决策单：[#280](https://github.com/ACANX/AHA/issues/280)（挂 V0.2.0）
