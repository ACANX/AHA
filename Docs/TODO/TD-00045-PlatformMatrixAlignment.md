# TD-00045 PlatformMatrixAlignment

> 待办编号：TD-00045
> 标题：平台清单三处对齐（设计 / Build / Release）
> 状态：☐ 未开始
> 跟踪 Issue：[#76](https://github.com/ACANX/AHA/issues/76)
> 所属主题：[TD-00005](TD-00005-Desktop02Remaining.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/TODO.md` `D-06`

## 原文（照抄，未改写）

> 发行目标平台与 CI runner 平台不匹配。

## 最小动作

把 `DesktopDesign.md` §2/§3、`Build.yml`、`Release.yml` 的平台集合对齐（ubuntu-amd64 / windows-amd64 / macos-aarch64；架构词已在 issue #287 统一）。

## 验收标准

- [ ] 三处平台集合逐字一致（可用 `grep` 核对）。

## 关联

- 所属主题：[TD-00005](TD-00005-Desktop02Remaining.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#76](https://github.com/ACANX/AHA/issues/76)
