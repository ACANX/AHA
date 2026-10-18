# TD-00023 TuiGuiRealMachineChecklist

> 待办编号：TD-00023
> 标题：TUI / GUI 必须在真机或人工确认的项（WSL 内验不到）
> 状态：☐ 未开始
> 跟踪 Issue：[#122](https://github.com/ACANX/AHA/issues/122)（真机验证总单）
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/PLAN.md` §5「本环境无法自动验证、必须真机或人工确认的项」（该文件已于 2026-10-17 删除，
内容在此保留）。这些**不是「做不到」**，而是 WSL 内验证不到，每次改动仍应人工过一遍。

## 原文（照抄）

以下不是“做不到”，而是**此处（WSL）验证不到**，每次改动仍应人工过一遍：

| 项 | 为什么这里验不了 | 怎么验 |
|---|---|---|
| **0.1 界面整体观感**（分隔线、状态行、工具区块色带、区块间空行） | 伪终端能验“画出来了”，验不了“好不好看” | Windows 下 `Dist\bin\Aha.bat chat`，让它读一个文件 |
| 状态行在 **conhost（旧 CMD）** 下的表现 | 只有 Windows 有 conhost；能力不足时应自动降级为不显示 | Windows 下 `Dist\bin\Aha.bat chat`，看是否花屏；若无线则确认降级生效 |
| **`Aha.bat`** 启动路径 | WSL 只能通过 `cmd.exe` 间接调用 | Windows 下直接运行 |
| **滚动区域对原生滚动/复制的影响** | 这是选 A 时接受的代价，属于手感判断 | Windows Terminal / PowerShell 下滚动与复制 |
| **`/memory edit`** 的编辑器接管终端 | 需要真实交互式终端与编辑器 | Windows 下 `code -w` / `notepad` |
| **真实供应商 API Key** 端到端 | 需要外部凭据 | `aha chat` 连真实供应商跑一轮 |
| 窗口 resize 后的状态行重排 | 伪终端里 `TIOCSWINSZ` 行为与真机不同 | 拖动窗口大小 |

---

## 要做的事

逐项在 Windows / 真终端上过一遍，每项给「**通过 / 不通过 + 现象**」，结论写进本文件或对应 TS 档案。

## 验收标准

- [ ] 上表 **7 项**各有一行结论（通过 / 不通过 + 现象描述）；
- [ ] 不通过项登记为 Issue（带复现步骤与截图/日志）。

## 关联

- 原 `Docs/PLAN.md` §5（已删除）
- [`TD-00005`](TD-00005-Desktop02Remaining.md)（桌面端 0.2 功能项）、[`TD-00003`](TD-00003-CliInputAndStatusBar.md)（状态行 / 输入框）
- [#122](https://github.com/ACANX/AHA/issues/122)（真机验证总单）
