# TD-00050 DesktopCoverageGateEnablement

> 待办编号：TD-00050
> 标题：桌面端覆盖率门禁上线（0.30 → 0.70 两步走）
> 状态：☐ 未开始
> 跟踪 Issue：[#78](https://github.com/ACANX/AHA/issues/78)
> 所属主题：[TD-00007](TD-00007-DesktopPackagingAndCoverage.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/TODO.md` `D-13`

## 原文（照抄，未改写）

> 0.2 桌面端剩余项：覆盖率门禁上线。

## 最小动作

按 `TestingSpec.md` §3.4 三步走：父 POM 移除 `desktop/**` 排除 → `aha-desktop/pom.xml` 覆盖 `minimum=0.30` → 数值写回规范。

## 验收标准

- [ ] `Gate` 出现 desktop 覆盖率且 ≥0.30；收尾评审后向 0.70 靠拢的记录在案。

## 关联

- 所属主题：[TD-00007](TD-00007-DesktopPackagingAndCoverage.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#78](https://github.com/ACANX/AHA/issues/78)
