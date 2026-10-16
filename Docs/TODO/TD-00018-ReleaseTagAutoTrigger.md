# TD-00018 ReleaseTagAutoTrigger

> 待办编号：TD-00018
> 标题：自动打的 `V*` tag 不触发 `Release.yml` —— 不配 PAT 前提下打通自动出包
> 状态：◐ 进行中（实现：PR [#240](https://github.com/ACANX/AHA/pull/240)；闭环验收需一次真实发版）
> 跟踪 Issue 状态：**OPEN**
> 跟踪 Issue：[#238](https://github.com/ACANX/AHA/issues/238)
> 创建日期：2026-10-16
> 最后更新：2026-10-16

---

## 来源

- issue #85 复核评论 §6「待决：『tag 不触发 `Release.yml`』这条留意项」（cnxnc，2026-10-13）；
- 现象已在文档留痕：`Docs/DevSpec/ReleaseProcess.md` §4.2、`Docs/Guide/VersionBumpGuide.md` §5 第 6 条；
- 现行处置：手工删 tag 后由**人**重推（产生一次真实 tag 事件）；本仓库**明确不配 PAT**。

## 现状

`Build.yml` 的 `tag` 作业在 `push` 到 `main` 时，用**默认 `GITHUB_TOKEN`** 打并推送 `V<版本>` tag：

```bash
git tag -a "${TAG}" -m "AHA ${TAG}" "${GITHUB_SHA}"
git push origin "refs/tags/${TAG}"     # 用的是 GITHUB_TOKEN
```

GitHub 规定：**由 `GITHUB_TOKEN` 执行的动作所触发的事件，不会创建新的 workflow run**（防递归）。
而 `Release.yml` **只**由 tag 事件触发（`on.push.tags: ['V*', 'v*']`，**没有 `workflow_dispatch`**）。

⇒ 自动打的 tag **不会**开跑 `Release.yml`，正式 Release 页面**只有 tag、没有发行包**
（`aha-cli-*.zip` / `aha-desktop-*.zip`）。0.1.1 以「发行包不强制」收口，故当时未阻塞；
但**发版在即**，若 0.1.3 期望有发行包，就会落空。

## 需求

**目标**：**不引入 PAT / GitHub App** 的前提下，让「自动打的 tag」也能自动产出并上传发行包。

**前提（官方规则，方案成立的关键）**：防递归规则**有例外**——

> events triggered by the `GITHUB_TOKEN` will not create a new workflow run, **with the following
> exceptions: `workflow_dispatch` and `repository_dispatch` events always create workflow runs.**
> —— [GitHub Docs · Triggering a workflow](https://docs.github.com/en/actions/how-tos/writing-workflows/choosing-when-your-workflow-runs/triggering-a-workflow)

即 **`GITHUB_TOKEN` 可以触发 `workflow_dispatch` / `repository_dispatch`**；`workflow_call`（复用工作流）
与 `workflow_run`（工作流完成触发）也不经「事件」这一层，同样不受防递归限制。

## 拍板结论（ACANX，2026-10-16）：采用**方案 B**

**`Build.yml` 打 tag 成功后，用 `GITHUB_TOKEN` 执行 `gh workflow run Release.yml --ref main -f tag=<TAG>`。**

**理由**：方案 A（`repository_dispatch`）的运行上下文是**默认分支**——本仓库默认分支是 `dev`，
而 release tag 指向 `main`。A 会把 `gate` / `compat` 与出包都落到 `dev`，与 `Release.yml` 里既有的
硬约束「**发行包必须由『自身跑过门禁的构建』产出**」直接冲突；要修对得再改 **4** 个文件
（含共享的 `Gate.yml` / `Compat.yml`）。**B 只改 2 个文件，且整个 run 落在 `main`。**

| `GITHUB_SHA` | `GITHUB_REF`（`repository_dispatch`） |
| --- | --- |
| Last commit on **default branch** | **Default branch** |

### 方案对比（决策依据）

| 方案 | 机制 | 结论 |
| --- | --- | --- |
| A. `repository_dispatch` | GITHUB_TOKEN 官方例外 | ✗ **否决**：run 上下文 = 默认分支 `dev`，gate/compat 与出包都会落到 dev |
| **B. `workflow_dispatch` + `--ref main`** | GITHUB_TOKEN 官方例外，**可指定 ref** | ✅ **采用**：run 落在 `main`（= tag 指向的提交），改动最小（2 文件） |
| C. `workflow_call` | 复用工作流，不经事件 | 备选：与 Build 的 run 耦合（日志 / 状态并入、失败联动） |
| D. `workflow_run` | 工作流完成触发 | 备选：需判「本次是否真的打了 tag」 |
| E. `push: branches: [main]` 触发 Release | 人合并产生的 `push` | 备选：与 `Build.yml` 职责重叠、需判版本变化 |
| F. 维持现状 + 手工重建 tag | 删 tag → 人重推 | 兜底：不自动化 |

### 实施要点（B）

| 文件 | 改动 |
| --- | --- |
| `Build.yml` 的 `tag` 作业 | `permissions` 加 `actions: write`；在**确实创建了 tag** 之后 `gh workflow run Release.yml --ref main -f tag=$TAG`（幂等跳过分支不派发） |
| `Release.yml` | `on` 加 `workflow_dispatch`（`inputs.tag`）；tag / 版本来源从 `github.ref_name` 改为 `inputs.tag`（dispatch 时）；两处 `softprops/action-gh-release` 加 `tag_name` |
| `ReleaseProcess.md` §2 / §4.2、`VersionBumpGuide.md` §5 第 6 条 | 口径改为「已自动化」 |

> **无需改** `gate` / `compat` 的 checkout：`--ref main` 使整个 run 的 `GITHUB_SHA` = `main` head
> （= tag 指向的提交），所有 `actions/checkout` 默认检出的就是它。

### 实施记录（2026-10-16）

已按上表落地，实现见 **PR [#240](https://github.com/ACANX/AHA/pull/240)**（分支 `ci/issue-238-release-dispatch`，
commit `32a8557`）。除上表外多做了两处：

- 两条出包线**显式 `checkout` 发布 tag**（`ref: ${{ inputs.tag || github.ref_name }}`）——
  `workflow_dispatch` 下 `github.ref` 是分支，靠默认值会取到分支头部；
- 发布 tag 用 `inputs.tag || github.ref_name` 取，**不用** `!= ''` 之类的比较——`push` 事件下
  `inputs` 整体是 `null`，宽松比较会折算成数字并静默给出错误结论（issue #213 的教训，
  `bin/CheckScripts.py` 有对应守卫）。

## 验收标准

- [x] 选定方案 → **B**（2026-10-16 已拍板）；
- [x] 代码与文档同步修改（`Release.yml` / `Build.yml` / `ReleaseProcess.md` / `VersionBumpGuide.md` / `BuildSpec.md` / `CHANGELOG.md`）—— PR [#240](https://github.com/ACANX/AHA/pull/240)；
- [ ] `dev → main` 合并后**无需人工介入**，Release 页面自动出现 `aha-cli-<版本>.zip` 与三平台 `aha-desktop-<版本>-<系统>-<架构>.zip`；
- [ ] 全流程**不使用 PAT / GitHub App token**（只用 `GITHUB_TOKEN`）；
- [ ] 人工推 tag 的既有路径（`push: tags`）不被破坏；
- [ ] **幂等**：tag 已存在（重复合入 `main`）时不重复派发；
- [ ] `ReleaseProcess.md` §2 / §4.2 与 `VersionBumpGuide.md` §5 第 6 条同步为「已自动化」口径。

> 前三项以外的验收都需要一次**真实发版**（合并到 `main`）才能判定，因此本单在 PR #240 合并、
> 且首次自动发版出包后再关闭。

## 关联

- 来源：issue #85 复核评论 §6（“待决”项，ACANX 接受另开本单承接）
- 拍板记录：[#238 评论](https://github.com/ACANX/AHA/issues/238)（方案 A 运行上下文分析 + 改用 B 的决定）
- `Docs/DevSpec/ReleaseProcess.md` §2 / §4.2；`Docs/Guide/VersionBumpGuide.md` §5 第 6 条
- 相关：#96（version 分发 workflow，含既有 PAT 取舍）
