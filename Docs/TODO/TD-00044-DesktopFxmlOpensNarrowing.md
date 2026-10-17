# TD-00044 DesktopFxmlOpensNarrowing

> 待办编号：TD-00044
> 标题：`module-info` 收紧 FXML 的 `opens`
> 状态：☐ 未开始
> 跟踪 Issue：[#76](https://github.com/ACANX/AHA/issues/76)
> 所属主题：[TD-00005](TD-00005-Desktop02Remaining.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/TODO.md` `D-03`

## 原文（照抄，未改写）

> FXML 反射需限定 `opens`。

## 最小动作

把 `aha-desktop/module-info.java` 里 FXML 相关的 `opens` 收紧到实际用到的包/类。

## 验收标准

- [ ] `AhaDesktopSmokeTest` 仍绿（FXML 加载不破）；`opens` 范围与 `DialogsFxmlTest` 的清单一致。

## 关联

- 所属主题：[TD-00005](TD-00005-Desktop02Remaining.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#76](https://github.com/ACANX/AHA/issues/76)
