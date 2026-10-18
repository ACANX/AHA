# TD-00036 ConhostPasteRealMachineVerification

> 待办编号：TD-00036
> 标题：conhost 粘贴侧门真机验证（Alt+V / `/paste`）
> 状态：☐ 未开始
> 跟踪 Issue：[#75](https://github.com/ACANX/AHA/issues/75)
> 所属主题：[TD-00004](TD-00004-CliWindowsMultilinePaste.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/Dbsx.txt` §6

## 原文（照抄，未改写）

> Windows 终端多行粘贴兼容性（conhost 下仍会逐行提交）。

## 最小动作

真机验 PR #254 的两条侧门：① conhost 下 `Alt+V` 插入整块并一次发送；② conhost 下 `/paste` 一次发送；③ Windows Terminal 下 `Ctrl+V` 不再逐行提交；④ `AHA_INPUT_TRACE=1` 的 `input-trace.log` 结果回传。

## 验收标准

- [ ] 四项各有「通过 / 不通过 + 现象」；结论写回 `TD-00004` 与 #75。

## 关联

- 所属主题：[TD-00004](TD-00004-CliWindowsMultilinePaste.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#75](https://github.com/ACANX/AHA/issues/75)
