# TD-00034 CliStatusBarInfo

> 待办编号：TD-00034
> 标题：状态栏信息：模型/目录/token/上下文占比/运行模式（口径待拍板）
> 状态：⏸ 待决策（[#275](https://github.com/ACANX/AHA/issues/275)）
> 跟踪 Issue：[#275](https://github.com/ACANX/AHA/issues/275)（本任务的决策单）
> 所属主题：[TD-00003](TD-00003-CliInputAndStatusBar.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/Dbsx.txt` §4

## 原文（照抄，未改写）

> 输入框下面要支持展示：
> - 当前使用的模型及思考强度；
> - 当前工作的目录；
> - 此次会话任务上行消耗的 token，下行消耗的 token，总消耗的 token 数量，缓存命中率；
> - 当前会话的当前的上下文占比，模型支持的最大的上下文长度；
> - 当前 agent 运行的模式（自动模式，完全授权模式，严格模式）。

## 最小动作

**先拍板**：`Dbsx.txt` §4 要全量信息，`PLAN.md` §2 说「pi 式底部统计栏暂不实施」，#74 只写「底部状态栏」——三处口径不一致。拍板后按结论实现或明确不做。

## 验收标准

- [ ] 本文件记录拍板结论；若实现，状态栏字段清单逐项有断言。

## 关联

- 所属主题：[TD-00003](TD-00003-CliInputAndStatusBar.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#74](https://github.com/ACANX/AHA/issues/74)
- 决策单：[#275](https://github.com/ACANX/AHA/issues/275)（挂 V0.2.0）
