# TS-2610-041 · 快照「显式请求」被随后的自动事件取消：复验 #204 仍不出包

**日期**：2026-10-15
**关联 Issue**：issue [#165](https://github.com/ACANX/AHA/issues/165)（验收第 16 条「只改文档时新打触发标签 ⇒ 照出包并写明『显式请求』」）
**关联档案**：`TS-2610-040`（触发层 `paths:` 过滤吃掉显式请求 —— 本条的上游）、`TS-2610-012`（原生线的步骤级容错）
**关联设计**：`Docs/DevSpec/BuildSpec.md` §11.2

---

## 1. 现象

[#204](https://github.com/ACANX/AHA/pull/204)（`7aca775`，去掉快照工作流触发层的 `paths:` 过滤）合入 `dev` 后，
ACANX 在**只改文档**的 PR [#203](https://github.com/ACANX/AHA/pull/203) 上**重打**
`SNAPSHOT:NativeDesktop` + `OS:Win` 做真机复验 —— **仍未出包**：

- **原生线两条工作流（`DesktopNative.yml` / `CliNative.yml`）零运行**；
- **JVM 线**起来了（`03:07:47` 建了两个 run），却全部 `cancelled`；
- `03:08:04` 的 `pull_request: synchronize` 判定为
  「B 组：本次只改了不影响产物的路径（如 `Docs/Dbsx.txt`）」⇒ 不出包。

即：`#204` 修好的那条路（触发层不再过滤）**在那次操作里没有生效**；而且即使生效，
另一个机制（并发取消）也会把这次显式请求吞掉。

`#203` 事后已按 ACANX 指示先行合入 `dev`（`f988a04`），不再用于复验。

## 2. 排查过程

| # | 动作 | 预期 | 实际 | 结论 |
|---|---|---|---|---|
| 1 | `gh pr checks 203` | 应有原生线的 Plan / 构建腿 | 原生线只有 `03:08:04` 那次 `synchronize` 的 Plan（判 B 组）；`03:07:45` 打标签后**零 run** | 事件没进原生线 |
| 2 | 查 `03:00–03:20` 全部 run | 打标签应各线各起一次 | `03:07:47` 只有 **SnapshotJVM** 两个 run（均 `cancelled`） | JVM 线能进、原生线不能 |
| 3 | 对比三条线的 `on:` 块 | 修复后都不该有 `paths:` | 当前 `dev` 都无 `paths:`；但 `5bf741d`（`#204` 之前的 `dev`）**原生线两条都有** | 旧 workflow 仍在起作用 |
| 4 | 取 `38674668504`（`03:08:04` 的 DesktopNative）plan 日志 | — | `ACTION: synchronize`、`should_build: false`、reason「B 组：…`Docs/Dbsx.txt`」 | 该 run 用的是新 workflow，但按 B 组主动不出包 |
| 5 | 看 `03:07:45` 之后是否还有 push | — | `03:08:02` 有 `Merge branch 'dev' into docs/issue-121-annotation-followup`（run `38674666423`） | 它触发了 `03:08:04` 的 `synchronize` |
| 6 | 核 `plan` 在 `push: dev` 上的表现 | 注释说「dev/push 时被跳过」 | `38674606947` 的 Plan `failure`，日志 `EVENT: push` / `::error::没能确定 PR 号` | 附带发现：`if` 与注释不符 |

时间线（UTC）：

```text
03:07:18  #204（7aca775）合入 dev
03:07:45  ACANX 打 SNAPSHOT:NativeDesktop + OS:Win
03:07:47  SnapshotJVM 起两个 run ..................（随后均 cancelled）
          DesktopNative / CliNative ................（零 run）
03:08:02  push：Merge branch 'dev' into docs/issue-121-annotation-followup
03:08:04  pull_request: synchronize 触发一批 run，其中 DesktopNative 判 B 组 ⇒ 不出包
```

被排除的假设：

| 已排除 | 依据 |
|---|---|
| `#204` 的改动本身写错 | 第 3 / 4 步：新 workflow 里确实没有 `paths:`，判定也正常走到「B 组」 |
| 标签名写错 / 事件没订阅 | 标签是清单里的 4 个之一，两条原生线的 `types` 都含 `labeled`，且 JVM 线同事件确实有 run |
| 判定脚本对「文档 + 标签」判断错 | `SnapshotPlan.py --self-test` 与 `#204` 的本地仿真均为 `should_build: true` |

## 3. 根因

三个机制叠加，各自独立：

**A. `pull_request` 事件用的是 PR ref 上的工作流定义，base 更新后不会立刻重建。**
GitHub 对 `pull_request`（含 `labeled`）用的是 `refs/pull/<N>/merge` 上的文件。
`#204` 合入 `dev` 后，`#203` 的 merge ref **仍是旧 base（`5bf741d`）与 head 的合并** ——
那份 `DesktopNative.yml` **带 `paths:` 白名单**（不含 `Docs/**`）。于是 `03:07:45` 的
`labeled` 事件被**事件层**丢掉：原生线零 run（`TS-2610-040` 的现象原样重现）。
直到 `03:08:02` 把 `dev` 合进 head，merge ref 才重建为含 `#204` 的版本。

> 旁证：`SnapshotJVM.yml` 本来就没有 `paths:`，同一秒、同一事件下它有 run —— 两条线唯一的差别就是 paths 过滤。

**B. 并发组把「显式请求」与「自动事件」放在同一组，后到的自动事件取消了显式请求。**
`DesktopNative.yml` 用的是 `group: desktop-native-${{ github.ref }}`，
`SnapshotJVM.yml` 用的是 `group: snapshot-jvm-<PR 号>`；两者的 `labeled` 与 `synchronize`
落在**同一组**，`cancel-in-progress: true` 便让 `03:08:04` 的 `synchronize`
取消了 `03:07:47` 的 `labeled` run。**「打完标签紧接着 push / 同步 base」必然触发。**

**C（附带发现）`plan` 没有排除 `push`，合入 `dev` 时恒报错。**
`push: dev` 时 plan 取不到 PR 号 ⇒ `::error::没能确定 PR 号` 退出 1 ⇒ run 变红。
但构建腿的 `if` 含 `github.event_name == 'push'`、又有 `always()`，照跑不误，
`native-publish` 也照发 ⇒ 红叉是纯噪声。文件里 `native-image` 的注释已写着
「dev/push 时它被跳过」，`if` 却与注释不符；自 `#183` 起长期如此。

**一句话**：显式请求的两次失效 —— 一次在事件进工作流**之前**（merge ref 未刷新 + 旧 `paths:`），
一次在事件进来**之后**（并发组被自动事件取消）；外加 `plan` 在 `push` 上的无谓报错。

## 4. 修复

| 文件 | 改动 |
|---|---|
| `.github/workflows/DesktopNative.yml`、`.github/workflows/CliNative.yml`、`.github/workflows/SnapshotJVM.yml` | `concurrency.group` 增加**事件类别**维度：显式请求（`labeled` / `unlabeled` / `issue_comment` / `workflow_dispatch`）⇒ `-explicit`，自动事件（`opened` / `synchronize` / `reopened` / `push`）⇒ `-auto`。两组互不取消，组内仍只留最新一次。分组键同时改用 **PR 号**（`issue_comment` / `workflow_dispatch` 的 `github.ref` 是默认分支，多个 PR 会共享同一组而互相取消） |
| `.github/workflows/DesktopNative.yml`、`.github/workflows/CliNative.yml` | `plan` 的 `if` 增加 `github.event_name != 'push'`，与注释、与构建腿门控对齐 |
| `bin/CheckScripts.py` | 新增 `concurrency_group()` 解析器与守卫第 ⑥ 条：快照工作流的 `concurrency.group` 必须区分事件类别，违规点名 |
| `Docs/DevSpec/BuildSpec.md` | §11.2 补两条硬约定（并发组按事件类别分层；`plan` 遇 `push` 跳过）+ 变更日志 v1.32.0 |

## 5. 验证

- `python3 bin/CheckScripts.py` 通过；
- **守卫反向验证**：把 `DesktopNative.yml` 的 group 改回 `${{ github.ref }}` ⇒
  `❌ .github/workflows/DesktopNative.yml 的 concurrency.group 没有区分事件类别（'desktop-native-${{ github.ref }}'）…`，
  退出码 1；还原后复绿；
- **解析器自证**：三条线的 `concurrency.group` 都能被 `concurrency_group()` 正确抽出（含折叠多行）；
- **真机验证**（合并后执行）：新开一个只改文档的 PR，**先确认 PR 已同步 base** 再打触发标签 ⇒
  期望原生线启动、plan 写明「显式请求…」并出包。（本轮未做，是 `#204` §5 第三条与 `#165` 验收 16 的共同欠账。）

## 6. 教训与沉淀

- **显式请求不能被自动事件取消**：并发组必须按「事件类别」分层 —— 只按 ref / PR 号分组时，
  「打完触发标签紧接着 push / 同步 base」会把请求吞掉，且完全静默。已落到守卫第 ⑥ 条；
- **`pull_request` 的工作流定义有「代际」**：base 更新后 merge ref 若不重建，事件仍按**旧定义**过滤。
  复验「触发层」类修复时，**先确认 PR 已同步 base**，否则测的是旧代码；
- **注释与实现要对齐**：`plan` 的注释写着「dev/push 时被跳过」，`if` 却没跳 —— 这类「说了没做」
  的偏差要靠守卫兜住，不能只靠读注释。

## 7. 涉及文件

| 类型 | 路径 |
|---|---|
| 工作流 | `.github/workflows/DesktopNative.yml`、`.github/workflows/CliNative.yml`、`.github/workflows/SnapshotJVM.yml` |
| 脚本 / 守卫 | `bin/CheckScripts.py`（`concurrency_group()` + 守卫第 ⑥ 条）|
| 文档 | `Docs/DevSpec/BuildSpec.md` §11.2、本篇 |

---

## 结论分层

### 已观测事实

- `03:07:45` 打标签 ⇒ 原生线零 run、JVM 线两个 run 均 `cancelled`（`gh api .../actions/runs` 为证）；
- `03:08:02` 有一次 head push（merge dev），`03:08:04` 的 DesktopNative plan 日志为
  `ACTION: synchronize` / `should_build: false` / reason「B 组：…`Docs/Dbsx.txt`」；
- `#204` 之前的 `dev`（`5bf741d`）里，两条原生线的 `pull_request` 都带 `paths:` 白名单；
- `push: dev` 上 `plan` 恒 `failure`，日志为 `EVENT: push` + `::error::没能确定 PR 号`。

### 机制 / 行为模型

- `pull_request` 事件用 `refs/pull/<N>/merge` 上的工作流定义；base 更新后该 ref 的重建**不是即时的**
  ⇒ 用旧定义做的过滤（`paths:`）会继续生效；
- `concurrency` 的 `group` 若不含事件类别，`cancel-in-progress` 会让后到的自动事件取消先到的显式请求；
- `github.ref` 在 `issue_comment` / `workflow_dispatch` 上是**默认分支**，不宜单独作为「本 PR」的分组键。

### 未定位 / 未复现

- 修复后的**真机**证据尚未取得：需在新 PR 上、且**已同步 base** 的前提下重打触发标签。
  阻塞点只是「需要一次真机操作」，无技术未知。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 原生线零运行 | `gh pr checks 203` / `gh api .../actions/runs?created=2026-10-15T03:00:00Z..03:20:00Z` | `03:07:47` 仅有 `SnapshotJVM`；无 `DesktopNative` / `CliNative` |
| JVM 线 labeled run 被取消 | 同上 | `38674644342`、`38674644655`（均 `cancelled`） |
| synchronize 判 B 组 | `gh run view 38674668504 --log` | `ACTION: synchronize`；`"should_build": false`；`reason: B 组：…Docs/Dbsx.txt` |
| 旧 base 带 paths | `git show 5bf741d:.github/workflows/DesktopNative.yml` | `pull_request: types: [...]` 下有 `paths:` 白名单（不含 `Docs/**`） |
| `push` 上 plan 报错 | `gh run view 38674606947 --log` | `EVENT: push` → `##[error]没能确定 PR 号`；`Plan` 作业 `failure` |
| 守卫反向验证 | 改回 `group: desktop-native-${{ github.ref }}` 后 `python3 bin/CheckScripts.py` | `❌ … 没有区分事件类别 …`，退出码 1 |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| `#204` 改错 / 新 workflow 仍有 `paths:` | 新 workflow 无 `paths:`，且 plan 正常判到「B 组」 |
| 判定脚本对「文档 + 标签」判断错误 | `--self-test` 与 `#204` 仿真均为 `should_build: true` |
| 标签名错 / 事件未订阅 | 标签在清单内，`types` 含 `labeled`，JVM 线同事件确有 run |

## 未决问题

无（本次三条修复各自闭环；真机复验只差一次人工操作，不构成未决技术项）。
