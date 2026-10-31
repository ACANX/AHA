# TD-00017 TroubleshootingDocSkeletonAlignment

> 待办编号：TD-00017
> 标题：Troubleshooting 32 篇排查档案正文对齐 TroubleShootingDocSpec 的四节必备项
> 状态：✅ 已完成（2026-10-13，选项 C）
> 跟踪 Issue 状态：**已关闭**（`completed`，2026-10-13 11:28Z，经 ACANX 授权）
> 跟踪 Issue：[#128](https://github.com/ACANX/AHA/issues/128)
> 创建日期：2026-10-13
> 最后更新：2026-10-13

---

## 来源

- 姊妹项目 LegionFanControl 的规范
  [`Docs/DevSpec/TroubleShootingDocSpec.md`](https://github.com/acnxo/LegionFanControl/blob/dev/Docs/DevSpec/TroubleShootingDocSpec.md)；
- 2026-10-13 的文档命名规范化（`Docs/DevLog/20261013-09.md`）：该轮**只对齐了命名**
  （`TS-yyMM-NNN-英文词序.md`），**正文骨架未对齐**，故建本条。

## 拍板结论（ACANX，2026-10-13）

**取选项 C：只补齐规范列明的四节必备项**（结论分层 / 观测数据 / 排除项 / 未决问题），
其余正文保持原样。

不取 A（全量改写 12 节骨架）的理由：逐篇重写要重走一遍「事实 / 机制 / 推测」的甄别，
改动面大而**改写失真风险**高；不取 B（只对新增档案套用）的理由：同一目录长期两套格式并存，
而四节恰恰是「最该统一」的部分。

## 完成内容

- **32 篇档案**（`TS-2610-001` ~ `TS-2610-032`）逐篇补入四节，形式统一为**文末补入块**：
  以一行 HTML 注释开头写明来源，四节**不带章序号**（避开各档案既有的「一、二、…」或「1. …」编号）；
- **`TS-Template.md`** 同步补入四节（带写作指引）——新档案照着模板写，模板不合规会复制缺陷；
- **`Docs/DevSpec/DocumentationSpec.md` 新增 §5「TS 档案正文骨架」**（v1.23.0）：
  写清四节的写法、补入块形式与「不回溯改写」的取舍；
- **`bin/CheckDocs.py` 新增 `[TS 档案必备章节检查]`**：逐篇断言四节存在，
  且「结论分层」必须有 `已观测事实` / `机制 / 行为模型` / `未定位 / 未复现` 三个子节。

## 两处口径订正

| 项 | 原口径 | 订正 |
| --- | --- | --- |
| 篇数 | 31 篇（`001`~`031`） | **32 篇**——命名规范化之后又新增了 `TS-2610-032` |
| 规范落点 | `DocumentationSpec.md` **§4** | **§5**——`§4` 已是「更新制度」，且被 `AGENTS.md` 与 `IssueTrackingSpec.md` 引用，不得重编号 |

## 内容来源的口径（重要）

补入块里的内容**全部来自档案原文**（数字、命令、日志原文、结论、遗留项逐条搬运并分类），
**没有新增任何未经档案记录的「事实」**。凡档案本身没有的内容，一律**如实写成缺口**，例如：

- 「原档案未保留原始输出 / 未记录 run id」→ 写在 `## 观测数据` 的取舍说明里；
- 「本机无法复现 / 由 CI 或真机给出答案」→ 落在 `### 未定位 / 未复现`；
- 「当时未验证」→ 落在 `## 未决问题` 的「现状」里。

这样做的意义：四节里的「**未定位**」与「**未决问题**」不是形式，它们把每篇档案
**本来就没做完的部分显式留痕**，避免被读成「已解决」。

## 验收标准

- [x] 选定 A / B / C 之一 → **C**（ACANX 拍板）；
- [x] 32 篇档案每篇均含「结论分层 / 观测数据 / 排除项 / 未决问题」四节
      （逐篇复查 + `CheckDocs.py` 断言）；
- [x] （不适用）若选 A：每篇头部补齐信息表；
- [x] `python3 bin/CheckDocs.py` 通过——**且它现在真的会检查这四节**（此前通过不能作为验收依据）；
- [x] 把骨架要求写入 `DocumentationSpec.md` → 新增 §5（v1.23.0）；
- [x] 守卫有反例：临时改掉某篇的「排除项」标题 → 检查**如期失败**并点名该篇。

## 关联

- issue [#128](https://github.com/ACANX/AHA/issues/128)（**已关闭 / completed**）——
  [关闭评论](https://github.com/ACANX/AHA/issues/128#issuecomment-6144331163)（三要素：PR #146 + #147、标准 / 根治、可复核验收依据）
- PR [#146](https://github.com/ACANX/AHA/pull/146)（`8f03c97`）、PR [#147](https://github.com/ACANX/AHA/pull/147)（`c20eb86`）
- `Docs/DevLog/20261013-09.md`（命名规范化，已完成）
- `Docs/DevSpec/DocumentationSpec.md` §1（命名规则）、**§5（正文骨架，本项新增）**
- `Docs/Troubleshooting/TS-Template.md`（模板已带四节）
