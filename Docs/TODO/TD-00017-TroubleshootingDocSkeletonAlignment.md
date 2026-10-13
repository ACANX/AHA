# TD-00017 TroubleshootingDocSkeletonAlignment

> 待办编号：TD-00017
> 标题：Troubleshooting 31 篇排查档案正文对齐 TroubleShootingDocSpec 的 12 节骨架
> 状态：⏸ 待拍板（A 全量 / B 只新档案 / C 补四节必备项）
> 跟踪 Issue：[#128](https://github.com/ACANX/AHA/issues/128)
> 创建日期：2026-10-13
> 最后更新：2026-10-13

---

## 来源

- 姊妹项目 LegionFanControl 的规范
  [`Docs/DevSpec/TroubleShootingDocSpec.md`](https://github.com/acnxo/LegionFanControl/blob/dev/Docs/DevSpec/TroubleShootingDocSpec.md)；
- 2026-10-13 的文档命名规范化（`Docs/DevLog/20261013-09.md`）：该轮**只对齐了命名**
  （`TS-yyMM-NNN-英文词序.md`），**正文骨架未对齐**，故建本条。

## 现状

`Docs/Troubleshooting/` 下 31 篇档案（`TS-2610-001` ~ `TS-2610-031`）的正文仍是本仓库早期写法，
与规范 §2.1 的 12 节骨架不一致。差距最大的是规范里**明令不得缺**的四节：

| 必备章节 | 现状 |
|---|---|
| 结论分层（已观测事实 / 机制模型 / 未定位） | ❌ 缺 |
| 观测数据（命令 + 原始输出、数字优先、好坏样本对照） | ⚠️ 部分 |
| 排除项（已排除的可能 + 依据，表格） | ⚠️ 部分 |
| 未决问题（Q1、Q2…，现状与阻塞点） | ❌ 缺 |

其余差异（头部信息表、影响范围「是否随发布包分发」的结论、验证分本地 / CI / 真机、预防）
见 issue #128 的逐项对照表。

## 待拍板

| 选项 | 代价 | 收益 |
|---|---|---|
| **A. 全量改造 31 篇** | 改动面大；需逐篇重读原文甄别「事实 / 机制 / 推测」，有改写失真的风险 | 全仓口径统一 |
| **B. 只对新增档案套用规范**，旧档案标注「历史格式」 | 几乎零成本 | 新档案合规；同一目录内长期两套格式并存 |
| **C. 只补齐四节必备项**，其余保持原样 | 中等 | 抓住最有价值的部分——防止后人把「推测」当「结论」读 |

**当前倾向 C**，待 ACANX 拍板。

## 验收标准

- [ ] 选定 A / B / C 之一；
- [ ] 若选 A 或 C：31 篇每篇均含「结论分层 / 观测数据 / 排除项 / 未决问题」四节；
- [ ] 若选 A：每篇头部补齐信息表，状态取值限于
      `待定位 / 修复中 / 已缓解 / 已修复 / 已机器强制 / 已归档`；
- [ ] `python3 bin/CheckDocs.py` 通过；
- [ ] （可选）把骨架要求写入 `DocumentationSpec.md` §4，避免下次又各写各的。

## 关联

- issue [#128](https://github.com/ACANX/AHA/issues/128)（跟踪）
- `Docs/DevLog/20261013-09.md`（命名规范化，本轮已完成）
- `Docs/DevSpec/DocumentationSpec.md` §1（命名规则，v1.22.0）
