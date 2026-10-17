# TD-00051 ScriptDirectoryConsolidation

> 待办编号：TD-00051
> 标题：`Script/` 与 `bin/` 是否合并（`.ps1` 已纳入检查）
> 状态：⏸ 待决策（[#279](https://github.com/ACANX/AHA/issues/279)）
> 跟踪 Issue：[#279](https://github.com/ACANX/AHA/issues/279)（本任务的决策单）
> 所属主题：[TD-00013](TD-00013-EngineeringAndDocHygiene.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/TODO.md` `F-07`

## 原文（照抄，未改写）

> `Script/PowerShell/CountJavaLoc.ps1` 未纳入 `CheckScripts.py`（`.ps1` 不在检查范围）；`Script/` 与 `bin/` 目录职责重叠。

## 最小动作

`.ps1` 已被 `CheckScripts.py` 覆盖（前半已完成）；本任务只决定 `Script/` 与 `bin/` 是否合并成一个脚本目录。

## 验收标准

- [ ] 结论 + 若合并则全仓引用同步、`CheckScripts` 仍绿。

## 关联

- 所属主题：[TD-00013](TD-00013-EngineeringAndDocHygiene.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#84](https://github.com/ACANX/AHA/issues/84)
- 决策单：[#279](https://github.com/ACANX/AHA/issues/279)（挂 V0.2.0）
