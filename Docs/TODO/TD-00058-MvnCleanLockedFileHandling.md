# TD-00058 MvnCleanLockedFileHandling

> 待办编号：TD-00058
> 标题：`mvn clean` 无法删除被占用文件（Windows）
> 状态：☐ 未开始
> 跟踪 Issue：[#84](https://github.com/ACANX/AHA/issues/84)
> 所属主题：[TD-00013](TD-00013-EngineeringAndDocHygiene.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/PLAN.md` §4

## 原文（照抄，未改写）

> | `mvn clean` 无法删除被占用的文件 | `aha-cli/pom.xml` | 已缓解（`failOnError=false`）：被锁的必然… |

## 最小动作

给出彻底处置：明确「哪些文件可能被锁 + 清理策略（重试/跳过/提示）」，或把现状写成正式约定。

## 验收标准

- [ ] 结论写进 `BuildSpec.md`（或 POM 注释）；CI 上无因锁文件导致的假红。

## 关联

- 所属主题：[TD-00013](TD-00013-EngineeringAndDocHygiene.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#84](https://github.com/ACANX/AHA/issues/84)
