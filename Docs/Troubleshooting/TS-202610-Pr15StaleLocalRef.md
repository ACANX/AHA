# TS-202610-Pr15StaleLocalRef：「PR #15 合并存在问题」的排查：没有冲突，是本地引用陈旧

> 日期：2026-10-08
> 作者：@ACANX（与 AI 助手协作）
> 关联 PR：#15（`dev → main`，标题 `Release:V0.1.1`）
> 关联记录：`Docs/DevSpec/ReleaseProcess.md` §4.2（据本次新增）、`Docs/TODO.md` G-07

## 1. 背景

用户反馈：「PR #15 合并存在问题，请帮我重新拉取并合并掉。」

先取证再动手，结论与直觉相反：**远端没有任何冲突，GitHub 判定可以直接合并**；
「问题」出在本地引用陈旧 + 对「红叉」的误读。

## 2. 排障过程与修复链

### 2.1 先读远端状态，而不是先怀疑冲突

```
GET /repos/ACANX/AHA/pulls/15
  head=214611ff  base=91388474  commits=41  changed_files=159
  mergeable=True  mergeable_state=clean      ← 关键：clean = 可以合
```

`mergeable_state` 的取值是有区分度的：`clean` 可合、`blocked` 缺审批或缺必需检查、
`dirty` 有冲突。**判断「能不能合」要看它，而不是看页面上有没有红叉**——
红叉可能来自**旧提交**上已被重跑覆盖的作业。

### 2.2 本地演练：干净

用**临时 worktree**（不切用户当前分支、不留残缺工作区）：

```
git worktree add --detach <临时目录> origin/main
git merge --no-commit --no-ff origin/dev      →  159 files changed, 0 conflicts
```

### 2.3 真正的隐患：基线独有的那个提交会不会「带内容回退」

```
git log --oneline origin/dev..origin/main
  9138847 Release:V0.1.0          ← main 独有
git show origin/main:pom.xml 的 aha 版本 → 0.1.0
git show origin/dev:pom.xml  的 aha 版本 → 0.1.1
```

**基线（main）上的版本号比待发布分支（dev）旧**——若不看清，合并可能把版本**退回 0.1.0**，
而这**不产生任何冲突**，属于最难事后发现的合并事故。演练证明合并结果取的是 **0.1.1**：

```
合并后 pom.xml       → 0.1.1
合并后 CHANGELOG 首行 → ## [0.1.1] - 2026-10-08
```

### 2.4 执行合并并证明「复议面为零」

在临时 worktree 里做真合并，然后用**一条命令**证明合并结果就是待发布分支的内容：

```
git merge --no-ff -m "Merge branch 'dev' into main（发布 V0.1.1）" origin/dev
  合并后 HEAD = 221ccee  第一父 = 9138847(main)  第二父 = 214611f(dev)
git diff --stat origin/dev HEAD        →  （空）
```

空 diff 说明：合并**只是把 dev 的内容搬到 main**，没有夹带任何基线侧的旧内容。
另核对关键文件与 dev 完全一致：`Build.yml` / `Gate.yml` / `DesktopNative.yml` / `.gitignore`，
版本号 8 处（根 POM + 六个模块 `<parent><version>` + `AppVersion.FALLBACK_VERSION`）全为 `0.1.1`。

### 2.5 检查与审批：都不构成阻塞

- dev head `214611f` 上：`build`（三平台 ×wrapper + system）、`门禁`、`兼容性`、`CodeQL`
  以及**5 条原生镜像腿**（ubuntu-x64 jdk25/jdk27、windows-x64 jdk25/jdk27、macos-arm64 jdk25）
  **全部 success**（原生管线的「可选」修正生效了）；
- 三个 `APPROVED` 的 `commit_id` 都是 `214611ff`，且时间（01:34Z / 01:58Z / 02:19Z）
  **晚于**最后一次 push（dev head 提交时间 01:29:41Z）→ 不会触发
  「Require approval of the most recent reviewable push」的失效。

### 2.6 本地引用陈旧（这才是「问题」的来源）

```
本地 dev    : 落后 origin/dev 36 个提交
本地 main   : 根本不存在（从未 checkout 过）
```

在这两个条件下做本地合并，必然「不对劲」：拿的是**36 个提交之前**的 dev，
而且没有 main 可合。处理：只对**纯落后**的分支做快进（`git fetch origin dev:dev`），
当前分支 `feat/version-0.1.1` 原样不动。

## 3. 最终验证结果

- 远端：`mergeable_state=clean`，检查全绿，审批有效 → **可以合并**；
- 本地：合并提交 `221ccee` 已在临时 worktree 里就绪
  （`E:\\GitRepo\\GitHub\\ACANX\\.aha-merge-tmp`），
  `git diff origin/dev HEAD` 为空、版本号 8 处一致、CI 关键文件未被回退；
- 后续链路（合并后自动发生）：推 `main` → `Build` 的 `tag（V<版本号>）` 作业打 `V0.1.1`
  → `Release.yml` 出 CLI 包 + 桌面端三平台包；
- 本地 `dev` 已快进到 `214611f`，`feat/dev` 快进到 `8cda4e2`，当前分支未改动。

## 4. 关键教训

1. **「能不能合」看 `mergeable_state`，不看红叉**：`clean` / `blocked` / `dirty` 三者含义不同，
   而红叉常来自旧提交上已被覆盖的作业。
2. **基线独有的提交必须逐个看**：`git log origin/<head>..origin/<base>` 非空时，
   要确认它会不会**带内容回退**（版本号、CI 配置、生成文件都是高发区）。
   这类事故**不产生冲突**，只有「合并结果比对」才能发现。
3. **合并前用 `git diff --quiet <待发布分支> <合并结果>` 证明复议面为零**：
   一条命令同时排除「冲突解错」「旧内容覆盖新内容」「CI 配置被回退」三类事故，
   比读 diff 可靠得多。已写进 `ReleaseProcess.md` §4.2。
4. **本地合并要用临时 worktree**：不切用户当前分支、失败可弃、成功可直接推。
5. **「合并/重试失败」先问「我是不是在拿陈旧引用做事」**：本地 dev 落后 36 个提交、
   本地没有 main 分支——这两条足以让一次本该干净的操作看起来「有问题」。

## 5. 涉及文件清单

- `Docs/DevSpec/ReleaseProcess.md`（新增 §4.2「合并发版 PR 前的三分钟核对」，原 §4.2 顺延为 §4.3）
- `Docs/TODO.md`（新增 G-07：合并动作待人工完成，含两条路径与灰按钮时的排查顺序）
- 临时 worktree：`E:\GitRepo\GitHub\ACANX\.aha-merge-tmp`（合并提交 `221ccee`，供人工推送）
