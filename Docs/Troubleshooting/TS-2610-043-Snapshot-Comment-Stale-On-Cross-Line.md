# TS-2610-043 · 快照评论跨线陈旧：JVM 线仍写「未打任何 `SNAPSHOT:*` 触发标签」

**日期**：2026-10-15
**关联 Issue**：issue [#205](https://github.com/ACANX/AHA/issues/205)（同名问题；本条即其修复记录）
**关联档案**：`TS-2610-040`（触发层 `paths` 过滤）、`TS-2610-042`（两条评论无法辨识）
**关联设计**：`Docs/DevSpec/BuildSpec.md` §11.4

---

## 1. 现象

PR 上**只打了原生线标签**（`SNAPSHOT:NativeDesktop` + `OS:Win`）时，JVM 线工作流运行后按设计不构建，
但它的 PR 评论**一直写着与事实不符的结论**：

> ### 快照包（JVM 线 · Desktop-JVM / Cli-JVM）：本次未出包
> - 原因：**未打任何 `SNAPSHOT:*` 触发标签**（四类目标默认都不出包）

而 PR 上**明明有** `SNAPSHOT:NativeDesktop` —— 读者会误判成「标签没打上 / 标签无效」
（issue #205 的原始现场是 PR #203，见 `TS-2610-040` 的「未决问题 Q1」）。

## 2. 排查过程

| # | 动作 | 预期 | 实际 | 结论 |
|---|---|---|---|---|
| 1 | 看 `decide()` 的分支顺序 | — | 「未打标签」分支在前、跨线分支在后 | 无标签时 JVM 线确实会写一条 |
| 2 | 看 JVM 跨线分支的 `owns_comment` | 应更新自己那条 | `owns_comment=False`（**不碰评论**）| 那条评论从此无人更新 |
| 3 | 追这条评论的生命周期 | 应与当前标签一致 | 无标签时写成「未打任何标签」⇒ 之后打原生标签 ⇒ JVM 线跨线但不更新 | **停在过时文案** |
| 4 | 看原生线跨线分支 | — | 未显式设 `owns_comment`（默认 `True`）⇒ **已经会写**「只负责 X，交由…处理」| JVM 线是唯一的缺口 |

被排除的假设：

| 已排除 | 依据 |
|---|---|
| 评论内容生成时不知道请求归属 | `decide()` 手上有 `targets`，完全知道归属，只是没写出来 |
| 两条线「共用 marker」导致覆盖 | 自检「两条线使用不同的评论标记（互不覆盖）」✅ |

## 3. 根因

JVM 跨线分支早先的策略是 **`owns_comment=False`（干脆不碰评论）**，注释里写明理由是
「否则原生线的标签事件会把 JVM 线的快照评论覆盖成『未出包』」——为了**避免覆盖**，
结果把「更新为准确文案」这件事也一并放弃了 ⇒ 旧文案永久残留。

（原生线跨线分支没有这个约束，默认 `owns_comment=True`，一直会写交接说明——所以只有 JVM 线有缺口。）

## 4. 修复

| 文件 | 改动 |
|---|---|
| `.github/Python/SnapshotPlan.py` | JVM 跨线分支改为 `owns_comment=True`，理由文案改为**交接说明**：「本次请求的目标 X 属**原生线**（交由 `DesktopNative.yml` / `CliNative.yml` 处理）——本工作流是 JVM 线，不出包」；原生线跨线分支的文案也补上「交给谁」（JVM 目标 ⇒ 交 `SnapshotJVM.yml`；另一原生目标 ⇒ 交对应工作流）；新增常量 `NATIVE_WORKFLOW`；`--self-test` 增断言 |
| `Docs/DevSpec/BuildSpec.md` | §11.4 新增硬约定「跨线写交接说明」+ 变更日志 v1.34.0 |

改后（本地预览）：

```text
JVM 线收到 SNAPSHOT:NativeDesktop
  ⇒ ### 快照包（JVM 线 · Desktop-JVM / Cli-JVM）：本次未出包
     原因：本次请求的目标 ['Desktop-Native'] 属**原生线**（交由 DesktopNative.yml 处理）——本工作流是 JVM 线，不出包

DesktopNative 收到 SNAPSHOT:Desktop
  ⇒ ### 快照包（原生线 · Desktop-Native）：本次未出包
     原因：本次请求的目标 ['Desktop-JVM'] 不含 Desktop-Native —— 本工作流只负责 Desktop-Native，交由 **JVM 线**（SnapshotJVM.yml） 处理
```

**为什么可以「覆盖」自己那条旧评论**：跨线意味着本次请求**不含本线目标**；把自己那条从
「未打任何标签」（**错误**）改成「属 X 线，交由 X 处理」（**正确**）是净改善。
若它此前写着「本线包已出」，那也属于更早的标签状态，已被移除/不再适用——文案跟着当前状态走才是对的。

## 5. 验证

- `python3 .github/Python/SnapshotPlan.py --self-test` ⇒ **30 条全部符合预期**，其中新增：
  「JVM 线跨线：不构建，但写「交接说明」（`owns_comment=True`，交由原生线处理）」✓；
- 本地预览三种跨线场景（JVM←原生标签、DesktopNative←JVM 标签、DesktopNative←Cli-Native 标签），文案均指向正确的承接方 ✓；
- 四道静态检查（`CheckDocs` / `CheckSkills` / `CheckScripts` / `CheckTheme`）退出码 0 ✓。

## 6. 教训与沉淀

- **「不碰」不等于「不错」**：为避免覆盖而选择「不动评论」，代价是**过时文案长期残留**；
  正确做法是「写上准确的内容」（只动自己的 marker 并不违反两条线分离的约束）；
- **每条写评论的路径都要能回答「当前状态变了，这条文案还成立吗」**：不成立的，要么更新、要么在
  设计上不写。已落到 `BuildSpec.md` §11.4 与自检断言。

## 7. 涉及文件

| 类型 | 路径 |
|---|---|
| 脚本 | `.github/Python/SnapshotPlan.py`（跨线分支 ×2、`NATIVE_WORKFLOW`、`--self-test`）|
| 文档 | `Docs/DevSpec/BuildSpec.md` §11.4、本篇 |

---

## 结论分层

### 已观测事实

- JVM 跨线分支 `owns_comment=False`（修复前），原生线跨线分支默认 `owns_comment=True`；
- 「未打标签」分支在跨线分支**之前**执行，因此 JVM 线在无标签时会写下那条后来过时的评论。

### 机制 / 行为模型

- 评论内容只在「本线认领（`owns_comment=True`）」时才被更新；`False` 会让它冻结在最后一次写入的状态。

### 未定位 / 未复现

- 无（本次为根治；真机复验在 PR 上打一次原生标签即可看到交接文案）。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 自检 | `python3 .github/Python/SnapshotPlan.py --self-test` | `✅ 30 条自检全部符合预期`（含「JVM 线跨线…交接说明」）|
| 文案预览 | 本地 `comment_body()` 生成 | 「属**原生线**（交由 DesktopNative.yml 处理）——本工作流是 JVM 线，不出包」|
| 四道检查 | `CheckDocs` / `CheckSkills` / `CheckScripts` / `CheckTheme` | 退出码全部 0 |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 两条线共用 marker | 自检断言「两条线使用不同的评论标记（互不覆盖）」✅ |
| 判定脚本不知道请求归属 | `decide()` 有 `targets`；修复只是把它写进理由文案 |

## 未决问题

无（#205 的 4 条验收均由本次修复 + `TS-2610-042` 的既有改动覆盖；详见 #205 的关闭评论）。
