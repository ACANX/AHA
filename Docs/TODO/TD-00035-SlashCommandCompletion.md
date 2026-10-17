# TD-00035 SlashCommandCompletion

> 待办编号：TD-00035
> 标题：斜杠命令下拉补全（命令本体已支持）
> 状态：☐ 未开始
> 跟踪 Issue：[#74](https://github.com/ACANX/AHA/issues/74)
> 所属主题：[TD-00003](TD-00003-CliInputAndStatusBar.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/Dbsx.txt` §5

## 原文（照抄，未改写）

> 需要支持在输入行中输入以 / 开头的命令来进入，
> 并且要支持下拉选项，可以方便用户快速选择需要使用的命令，
> 就像 pi / claude code 中支持的那样。

## 最小动作

命令本体已实现（`/help`、`/config`、`/context`、`/compact`、`/memory`、`/paste` 等，见 `aha-cli/.../cli/session/`）；本任务只做**输入 `/` 时的候选下拉**与键盘选择。

## 验收标准

- [ ] 输入 `/` 弹候选；Tab / 方向键可选中并补全；候选来自真实命令表（不是硬编码副本）。

## 关联

- 所属主题：[TD-00003](TD-00003-CliInputAndStatusBar.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#74](https://github.com/ACANX/AHA/issues/74)
