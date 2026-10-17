# TS-2610-042 · 快照评论出现两条「逐字相同」的条目：读者分不清是哪条线

**日期**：2026-10-15
**关联 Issue**：issue [#205](https://github.com/ACANX/AHA/issues/205)（快照评论陈旧：标签属另一条线时不更新）
**关联档案**：`TS-2610-040`、`TS-2610-041`（同一条触发链路的前两次修复）
**关联设计**：`Docs/DevSpec/BuildSpec.md` §11.4

---

## 1. 现象

PR [#206](https://github.com/ACANX/AHA/pull/206) 上出现了**两条快照评论**：

| 时间（UTC）| 可见标题 | marker（不可见）| 当时的正文 |
|---|---|---|---|
| 04:36:22（updated 05:04）| 快照包：本次未出包 → 后改为「快照包：`0.1.3.00206.10151255`」 | `<!-- aha-snapshot-native -->` | 先「未打任何 `SNAPSHOT:*` 触发标签」，后改为出包 |
| 04:36:47（未再更新）| 快照包：本次未出包 | `<!-- aha-snapshot -->` | 「未打任何 `SNAPSHOT:*` 触发标签」 |

在 `04:36:22`–`05:04` 这段时间里，两条评论**除 marker 外逐字相同**（标题与三行正文完全一致）。
而 marker 只是**不可见的 HTML 注释** —— 读者在 PR 页面上看到两条一模一样、却又说不出差别的「未出包」，
无从判断哪条是 JVM 线的、哪条是原生线的。

ACANX 的判断：**"要么有差异，要么合成一条，不允许出现两条近乎一样、看不出差别的评论。"**

## 2. 排查过程

| # | 动作 | 预期 | 实际 | 结论 |
|---|---|---|---|---|
| 1 | 取 #206 的机器人评论 | 期望一条 | 两条（`aha-snapshot-native`、`aha-snapshot`）| 两条线各写了一条 |
| 2 | 比 `created_at` / `updated_at` | — | `native`: created 04:36:22 / updated 05:04:34；`jvm`: created=updated 04:36:47 | 「逐字相同」发生在 04:36 的短暂窗口内 |
| 3 | 查 `labeled` 事件时间 | — | 04:40:07 才打标签 | 04:36 时 **PR 上还没有任何标签** |
| 4 | 查 04:35:30（`synchronize`）两条线的 plan | — | JVM / 原生都 `targets=[]`、`should_build=false`、`owns_comment=true` | **两条线都落评论**，内容同源 ⇒ 逐字相同 |
| 5 | 查 04:40:07 之后各线的 plan | — | JVM 线 `owns_comment=false`（目标属原生线）；原生线 `owns_comment=true` | JVM 那条再无人更新，停在旧文案 |

原始取证（节选）：

```text
$ gh api repos/ACANX/AHA/actions/runs/38682269152 --jq ... | grep 判定完成   # 04:35:30 synchronize，JVM 线
{"owns_comment": true, "should_build": false, "targets": [], ... "marker": "<!-- aha-snapshot -->",
 "reason": "未打任何 `SNAPSHOT:*` 触发标签（四类目标默认都不出包）"}

# 同一批事件里原生线的那条，除 marker 与 line 外与上面完全一致 ⇒ 两条评论逐字相同
```

被排除的假设：

| 已排除 | 依据 |
|---|---|
| 「两条」本身是缺陷 | 两条线各有 marker、各自幂等回写是**刻意设计**（共用会互相覆盖成「未出包」）|
| 评论内容生成时带了线信息、只是没显示 | `notice_body()` 的标题是固定的 `### 快照包：未构建`，不含任何线 / 目标 |

## 3. 根因

两个因素叠加：

1. **「未打任何标签」时，三条快照工作流（JVM + 两个原生）都会落评论** ——
   `decide()` 里 `elif not targets:` 的返回默认 `owns_comment=True`，与 `line` 无关；
2. **评论正文里没有任何「是哪条线」的信息** —— 区分靠 marker，而 marker 是**不可见的**注释。

⇒ 同一时刻出现两条（或多条）**除 marker 外逐字相同**的评论，读者无法分辨。

## 4. 修复

| 文件 | 改动 |
|---|---|
| `.github/Python/SnapshotPlan.py` | ① 新增 `line_label()`：评论标题统一带「线 · 本线目标范围」（`JVM 线 · Desktop-JVM / Cli-JVM` / `原生线 · <本工作流目标>`）；`notice_body()` / `comment_body()` 的标题启用它；② `elif not targets:` 分支改为 `owns_comment=(line == "jvm")` —— **「未打任何标签」时只由 JVM 线落一条**；③ `out()` 增记 `native_target` 供标题使用；④ `--self-test` 增两条断言 |
| `Docs/DevSpec/BuildSpec.md` | §11.4 新增硬约定（评论必须可辨识；未打标签只 JVM 线落一条）+ 变更日志 v1.33.0 |

改后的两条评论（场景预览）：

```text
无标签        ⇒ 只有一条：### 快照包（JVM 线 · Desktop-JVM / Cli-JVM）：本次未出包
只打原生标签  ⇒ 只有一条：### 快照包（原生线 · Desktop-Native）：`<版本>`
两条线都请求  ⇒ 两条，但标题分别是「JVM 线…」与「原生线…」，一眼可辨
```

## 5. 验证

- `python3 .github/Python/SnapshotPlan.py --self-test` ⇒ **30 条全部符合预期**，其中新增：
  - 「未打任何标签」时只由 JVM 线落评论（原生线 `owns_comment=False`）✓
  - 两条线的评论标题各自标明「JVM 线」/「原生线」✓
- 四道静态检查（`CheckDocs` / `CheckSkills` / `CheckScripts` / `CheckTheme`）退出码 0；
- 场景预览（本地生成评论正文）见 §4 末。

## 6. 教训与沉淀

- **不可见的区分信息等于没有区分信息**：marker 用于机器幂等，不能代替**给人看**的标题；
  凡是「同一 PR 上可能出现多条同类评论」的机制，标题必须自带身份；
- **默认写评论的路径要问「同一时刻会有几条」**：`not targets` 是三条工作流都会走到的分支，
  默认 `owns_comment=True` 就等于「一次事件发三条同义评论」；
- 已落到规范与自检：`BuildSpec.md` §11.4 + `SnapshotPlan.py --self-test` 两条断言。

## 7. 涉及文件

| 类型 | 路径 |
|---|---|
| 脚本 | `.github/Python/SnapshotPlan.py`（`line_label()`、`notice_body()`、`comment_body()`、`decide()`、`--self-test`）|
| 文档 | `Docs/DevSpec/BuildSpec.md` §11.4、本篇 |

---

## 结论分层

### 已观测事实

- #206 上存在两条快照评论，`04:36:22`–`05:04` 之间**除 marker 外逐字相同**（`gh api .../issues/206/comments` 为证）；
- 两条评论均由 `04:35:30` 的 `synchronize` 事件创建，当时 PR 尚无标签（`targets=[]`、`owns_comment=true`）；
- `labeled` 事件在 `04:40:07` 才出现，此后 JVM 线 `owns_comment=false`，不再更新它那条。

### 机制 / 行为模型

- 评论的「身份」此前只由 marker 承载，而 marker 是不可见注释；
- `not targets` 分支对三条工作流一视同仁地 `owns_comment=true`。

### 未定位 / 未复现

- #205 的**另一半**（JVM 线那条「未出包」在请求转属原生线后仍不被纠正）本次**未修**：
  它需要「读取现有评论内容再决定改写 / 删除」，属独立设计，仍由 [#205](https://github.com/ACANX/AHA/issues/205) 跟踪。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 两条评论的时间与 marker | `gh api repos/ACANX/AHA/issues/206/comments` | native: created 04:36:22 / updated 05:04:34；jvm: created=updated 04:36:47 |
| 无标签时三条线都落评论 | `gh run view 38682269152 --log` | `"targets": []`、`"owns_comment": true`、`"marker": "<!-- aha-snapshot -->"` |
| 标签事件时刻 | `gh api repos/ACANX/AHA/issues/206/timeline` | `2026-10-15T04:40:07Z labeled SNAPSHOT:NativeDesktop` / `OS:Win` |
| 自检 | `python3 .github/Python/SnapshotPlan.py --self-test` | `✅ 30 条自检全部符合预期` |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 两条线共用一个 marker | 自检「两条线使用不同的评论标记（互不覆盖）」✅ |
| 评论标题已带线信息、只是页面没渲染 | `notice_body()` 原标题为固定的 `### 快照包：未构建`，不含线字段 |

## 未决问题

**Q1（= #205 的另一半）** 请求从「无」转为「属原生线」后，JVM 线那条陈旧的「未出包」该**改写**为
「本轮请求属原生线」还是**删除**？两种都要先读现有评论再决定（`SnapshotComment.py` 目前是整条 upsert），
且「改写」会与原生线的出包评论并存、「删除」则要新增删除能力 —— 取舍待定，见 issue #205。
