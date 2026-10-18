# PR 合并操作规范

**文档版本**：v1.0.0
**状态**：冻结（强制）
**生效日期**：2026-10-18
**最后更新**：2026-10-18
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-18 | 首版：把「改代码前 / 推送前必须确认分支对应的 PR 是否已合并」定为强制检查点，并固化「已合并 ⇒ 必须新开 PR」的处置流程 | @ACANX / CNXNC |

---

## 1. 为什么要有这份规范

Agent 在本仓库操作时，出现过**同一个错误反复发生**：

- PR 已经合并到 `dev`，Agent 仍继续在原来的特性 / fix 分支上改代码、提交、推送；
- 推送后 CI 照跑、PR 页面照显示，看起来「一切正常」，**实际改动永远不会进主干**；
- 等到下一次开 PR 或合并时才发现：那些提交挂在一条已经合并的分支上，需要重新迁移；
- 更糟的是：若原分支的 PR 已被关闭 / 合并，GitHub 的「reopen」语义与 squash 合并后的历史都不支持直接续用，只能重建。

**根因不是技术问题，是流程缺失**：没有「推送前先确认 PR 状态」这个强制动作。

## 2. 适用范围

- 本仓库（`ACANX/AHA`）内一切**会改动代码 / 文档并最终要合入 `dev` 或 `main`** 的操作；
- 执行者包括 Agent 与人类协作者；Agent **不得**以「这次只是小改动」为由跳过。

## 3. 三个强制检查点

> 三个检查点**缺一不可**。任何一个检查点发现「PR 已合并」，都必须按 §4 处置。

### 检查点 1：改代码之前

在编辑任何被 git 跟踪的文件之前：

1. `git fetch origin`；
2. 确认当前分支：`git branch --show-current`；
3. 查该分支对应的 PR（含已合并 / 已关闭）：
   - `gh pr list --head <分支名> --state all --json number,state,mergedAt`
4. 查该分支相对主干的差异：
   - `git log --oneline origin/dev..HEAD`

**判定**：

- 分支对应的 PR 是 `MERGED` 或 `CLOSED` ⇒ **停止改动**，按 §4 新建分支后再动手；
- 分支没有 PR，但 `origin/dev..HEAD` 为空（分支已落后 / 已包含全部内容）⇒ 先与 `origin/dev` 对齐，再判断是否要新分支；
- 分支没有 PR 且差异正常 ⇒ 可以继续。

### 检查点 2：提交 / 推送之前

在 `git push` 之前**再查一次**（PR 可能在检查点 1 之后被合并）：

1. `gh pr list --head <分支名> --state all --json number,state,mergedAt`；
2. `git fetch origin && git log --oneline origin/dev..HEAD`。

**判定**：

- PR 已是 `MERGED` ⇒ **禁止推送**（推送不会进入主干，只会制造「已合并分支又有新提交」的假象）；
- PR 仍是 `OPEN` ⇒ 可以推送；
- PR 不存在 ⇒ 推送后立刻开 PR。

### 检查点 3：合并之前（人类操作，Agent 只做提醒）

- PR 的 base 是最新 `dev`（`gh pr view <N> --json baseRefName,mergeable`）；
- PR 状态 `OPEN`、`mergeable=MERGEABLE`；
- CI 通过（或失败项已确认为外部抖动）。

## 4. 发现「PR 已合并」时的处置

**不允许**：继续在原分支上提交 / 推送、reopen 原 PR、把新提交塞进已合并的分支。

**必须**按下面顺序执行：

1. **停止并说明**：立即停止推送，向用户说明「原 PR #N 已合并，后续改动需要新 PR」；
2. **基于最新主干新建分支**：
   - `git fetch origin`
   - `git checkout -b <新分支名> origin/dev`（分支名前缀按 `BranchStrategy.md`：`feat/` / `fix/` / `docs/` / `chore/`）；
3. **迁移未合并的改动**（二选一，优先用差异应用，保证只带「主干还没有的部分」）：
   - `git diff origin/dev <旧分支> | git apply`；
   - 或对指定文件 `git checkout <旧分支> -- <路径>`（仅当确认该文件的旧分支版本就是要的最终内容）；
4. **本地验证**：`python3 bin/CheckScripts.py` / `CheckDocs.py` 等（按改动范围）；Java 编译与单测交给 CI；
5. **开新 PR**，正文里注明：
   - 前序 PR 编号与合并状态（如「#289 已合并」）；
   - 本 PR 承接哪些未合并内容；
   - 分支基于哪个 `dev` 提交；
6. **旧分支保留、不再推送**（作为历史；不删除，避免丢失上下文）。

## 5. 检查命令速查（一行一项）

- `git fetch origin` —— 先同步远端，避免基于过期信息判断
- `git branch --show-current` —— 当前分支名
- `gh pr list --head <分支名> --state all --json number,state,mergedAt` —— 该分支对应的全部 PR 及状态
- `gh pr view <N> --json state,mergedAt,mergeable,baseRefName` —— 单个 PR 的权威状态
- `git log --oneline origin/dev..HEAD` —— 本分支相对主干的未合并提交
- `git log --oneline origin/dev -1` —— 主干最新提交（用于确认基线）
- `git diff --stat origin/dev <分支名>` —— 与主干的净差异（判断是否还有未合并内容）

## 6. 与其他规范的关系

| 文档 | 关系 |
|---|---|
| [BranchStrategy.md](BranchStrategy.md) | 分支命名、base 选择；本文补充「分支生命周期已结束时怎么办」 |
| [CommitMessageSpec.md](CommitMessageSpec.md) | 提交信息格式；本文只约束「何时可以提交 / 推送」 |
| [IssueTrackingSpec.md](IssueTrackingSpec.md) | Issue 关闭需要 PR 与验收证据；本文保证「PR 确实把改动带进了主干」 |
| [GitHubActionsSpec.md](GitHubActionsSpec.md) | CI 与工作流约定；本文不重复其内容 |

## 7. 事故记录

### 2026-10-18：#289 合并后仍向 `feat/release-matrix-269` 推送

- **事实**：`#289` 于 `53f7e2f` 合并到 `dev`；此后仍在 `feat/release-matrix-269` 上提交并推送了 10 余次（CLI 三平台腿、报告包改名、tag 去 `-SNAPSHOT`、启动脚本等）；
- **后果**：这些提交全部不在 `dev` 上；需要在新分支 `fix/cli-jvm-legs` 上用 `git diff origin/dev feat/release-matrix-269 | git apply` 重新迁移，并开 `#291`；
- **根因**：推送前没有查「该分支的 PR 是否已合并」；
- **整改**：本文档（§3 三个检查点 + §4 处置流程 + §5 命令速查），并写入 `AGENTS.md` 作为强制约定。

## 8. 违反时的处理

- 一旦发现「向已合并分支推送」：
  1. 立即停止后续推送；
  2. 按 §4 迁移到新分支并开新 PR；
  3. 在当次回复中如实说明：发现时间、影响了哪些提交、如何迁移、新 PR 编号；
  4. **不得**用「反正在分支上，没影响主干」搪塞 —— 问题是改动**没有**进主干，且过程不可见。
