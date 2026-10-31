# TD-00032 InputBoxBorderStyle

> 待办编号：TD-00032
> 标题：输入框样式：用上下制表符包裹，去掉 `aha>` 前缀
> 状态：☐ 未开始
> 跟踪 Issue：[#74](https://github.com/ACANX/AHA/issues/74)
> 所属主题：[TD-00003](TD-00003-CliInputAndStatusBar.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/Dbsx.txt` §3

## 原文（照抄，未改写）

> 对于终端界面中输入会话文本的那一行，后续不需要在行首位置加上 "aha>" 了，
> 只需要像 claude code / pi 那样在其上一行 / 下一行分别加上制表符，
> 将该行包裹成一个输入框就可以了，

## 最小动作

改提示符：去掉 `aha>` 文本前缀，改为输入行上方/下方各一条制表符边框。

## 验收标准

- [ ] 冒烟断言：输入区首尾各有一条边框行、且不含 `aha>` 字面量。

## 关联

- 所属主题：[TD-00003](TD-00003-CliInputAndStatusBar.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#74](https://github.com/ACANX/AHA/issues/74)
