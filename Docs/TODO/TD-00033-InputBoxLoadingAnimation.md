# TD-00033 InputBoxLoadingAnimation

> 待办编号：TD-00033
> 标题：输入框 loading 动效（旋转字符）
> 状态：☐ 未开始
> 跟踪 Issue：[#74](https://github.com/ACANX/AHA/issues/74)
> 所属主题：[TD-00003](TD-00003-CliInputAndStatusBar.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/Dbsx.txt` §3

## 原文（照抄，未改写）

> 如果能像 pi 那样能支持展示动态的 loading 旋转字符 / 加载的效果的话就更好了，
> 可以直观的告诉用户可以在此行输入对话的文本，下达后续的文本指令。

## 最小动作

在等待模型响应期间于输入区渲染旋转字符；响应开始/结束要能正确启停（不留残影）。

## 验收标准

- [ ] 冒烟断言：等待期间出现旋转帧、结束后输入区恢复且无残留行。

## 关联

- 所属主题：[TD-00003](TD-00003-CliInputAndStatusBar.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#74](https://github.com/ACANX/AHA/issues/74)
