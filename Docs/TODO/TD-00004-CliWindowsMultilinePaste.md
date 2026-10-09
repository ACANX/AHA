# TD-00004 CliWindowsMultilinePaste

> 待办编号：TD-00004
> 标题：CLI：Windows 终端多行粘贴兼容性（conhost 下仍会逐行提交）
> 状态：◐ 进行中
> 跟踪 Issue：[75](https://github.com/ACANX/AHA/issues/75)
> 创建日期：2026-10-09
> 最后更新：2026-10-09

---

## 来源

`Docs/Dbsx.txt` 第 6 节「Windows 终端多行粘贴兼容性」。

## 现状（部分完成）

已做两项加固：

1. 读取输入的异常由「只捕获 Ctrl+C / Ctrl+D」改为宽幅兜底——任何读取失败都只忽略本次输入并继续对话（此前粘贴触发的异常会冒泡到外层直接结束会话，这可能就是「报错」的实际表现）；
2. 显式启用 `BRACKETED_PASTE`，且 `exit` / `quit` 仅在「单行且无内嵌换行」时生效。

## 剩余限制（需明确处置）

仍受终端能力约束：Windows Terminal 支持 bracketed paste，**传统 conhost 不支持**，此时多行粘贴仍会被逐行提交——Harness 侧无法改变。

## 待定

- [ ] 确认「报错」是否已随宽幅兜底消失（需 Windows 真机复现原操作）；
- [ ] 对 conhost 的逐行提交行为给出明确结论：接受降级并写入 `TUIDesign.md` 的降级矩阵，或提供替代粘贴入口（如 `/paste` 或读剪贴板）；
- [ ] 若存在可在 Harness 侧规避的路径（如探测终端能力后提示），补上提示。

## 验收标准

- [ ] Windows Terminal 下粘贴含换行的长文本不再报错、不被逐行提交；
- [ ] conhost 下的行为有明确书面结论（降级说明或替代方案），不留「已知但未定」。

## 关联

- `Docs/Dbsx.txt` 第 6 节
- `Docs/Design/TUIDesign.md`（终端能力与降级矩阵）

