# TD-00018 ReleaseTagAutoTrigger

> 待办编号：TD-00018
> 标题：自动打的 `V*` tag 不触发 `Release.yml` —— 不配 PAT 前提下打通自动出包
> 状态：☐ 未开始（**待拍板**：方案 A ~ E）
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

## 需求 / 待拍板

**目标**：**不引入 PAT / GitHub App** 的前提下，让「自动打的 tag」也能自动产出并上传发行包。

**前提（官方规则，方案成立的关键）**：防递归规则**有例外**——

> events triggered by the `GITHUB_TOKEN` will not create a new workflow run, **with the following
> exceptions: `workflow_dispatch` and `repository_dispatch` events always create workflow runs.**
> —— [GitHub Docs · Triggering a workflow](https://docs.github.com/en/actions/how-tos/writing-workflows/choosing-when-your-workflow-runs/triggering-a-workflow)

即：**`GITHUB_TOKEN` 可以触发 `workflow_dispatch` / `repository_dispatch`**；另外 `workflow_call`
（复用工作流）与 `workflow_run`（工作流完成触发）也不经「事件」这一层，同样不受防递归限制。

| 方案 | 做法 | 代价 / 风险 |
| --- | --- | --- |
| **A. `repository_dispatch`**（倾向：解耦好） | `Build.yml` 打 tag 成功后 `gh api repos/$REPO/dispatches -f event_type=release-tag -f client_payload[tag]=$TAG`；`Release.yml` 加 `repository_dispatch: types: [release-tag]`，按 payload 里的 tag 检出 | 需 `contents: write`（tag 作业已有）；`repository_dispatch` 在**默认分支**跑 workflow，故 `Release.yml` 必须显式 `checkout ref=<payload.tag>`，并把「tag 来源」从 `github.ref_name` 改为输入 |
| **B. `workflow_dispatch`** | `Build.yml` 打 tag 后 `gh workflow run Release.yml --ref main -f tag=...` | 需给 tag 作业加 `actions: write`；API 触发要求 workflow 存在于默认分支；同样要把 tag 改成入参 |
| **C. `workflow_call`**（最直白，但耦合） | `Release.yml` 加 `workflow_call`，`Build.yml` 的 tag 作业之后 `uses: ./.github/workflows/Release.yml` | 不经事件，实现最简单；但 Release 变成 Build 的一次 run（日志 / 状态并入、失败耦合），且要支持「被调用」入参 |
| **D. `workflow_run`** | `Release.yml` 改监听 `Build` 的 `completed`，条件 `head_branch == 'main'` 且确实打了 tag | 绕；要判「本次是否真的打了 tag」；只在默认分支生效 |
| **E. 改由 `push: branches: [main]` 触发 Release** | tag 由 `Release.yml` 自己打 | 合并到 main 的 `push` 由「人」产生 → 正常触发；但与 `Build.yml` 职责重叠，需判版本是否变化 |
| **F. 维持现状 + 手工重建 tag** | 删 tag → 人重推 | 现状；**不自动化**，与目标冲突，仅作兜底 |

**任一方案都需要的同一处小改**：`Release.yml` 目前从 `github.ref_name` 取 tag；改为从
「事件 payload / 入参」取，并显式检出该 tag（tag 指向 main head，`GITHUB_SHA` 不一定等于它）。

## 验收标准

- [ ] 选定 A ~ E 之一，并记录取舍理由；
- [ ] `dev → main` 合并后**无需人工介入**，Release 页面自动出现 `aha-cli-<版本>.zip` 与三平台 `aha-desktop-<版本>-<系统>-<架构>.zip`；
- [ ] 全流程**不使用 PAT / GitHub App token**（只用 `GITHUB_TOKEN`）；
- [ ] 人工推 tag 的既有路径（`push: tags`）不被破坏；
- [ ] `ReleaseProcess.md` §2 / §4.2 与 `VersionBumpGuide.md` §5 第 6 条同步为「已自动化」口径。

## 关联

- 来源：issue #85 复核评论 §6（“待决”项，ACANX 接受另开本单承接）
- `Docs/DevSpec/ReleaseProcess.md` §2 / §4.2；`Docs/Guide/VersionBumpGuide.md` §5 第 6 条
- 相关：#96（version 分发 workflow，含既有 PAT 取舍）
