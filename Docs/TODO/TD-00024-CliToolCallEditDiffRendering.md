# TD-00024 CliToolCallEditDiffRendering

> 待办编号：TD-00024
> 标题：工具调用：编辑类输出「分块 + 前后差异」渲染
> 状态：☐ 未开始
> 跟踪 Issue：[#72](https://github.com/ACANX/AHA/issues/72)
> 所属主题：[TD-00001](TD-00001-CliToolCallOutputRendering.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/Dbsx.txt` §1（该文件已于 2026-10-17 删除）

## 原文（照抄，未改写）

> - 编辑文件
>   需要支持像 pi 那样分块输出编辑的命令，及对文件修改的部分的前后差异做展示；

## 最小动作

在工具卡片渲染器里为编辑类工具（write/replace）输出：① 分块标题（命令 + 目标文件）；② 变更前后的差异块（沿用 diff 语法）。

## 验收标准

- [ ] 冒烟/单测断言：编辑类工具的输出里同时出现「命令块」与「前后差异块」；三平台渲染一致。

## 关联

- 所属主题：[TD-00001](TD-00001-CliToolCallOutputRendering.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#72](https://github.com/ACANX/AHA/issues/72)
