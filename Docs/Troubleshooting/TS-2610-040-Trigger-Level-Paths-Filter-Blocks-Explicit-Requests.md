# TS-2610-040 · 触发层 `paths:` 过滤吃掉「显式请求」：打了快照标签却毫无反应

**日期**：2026-10-15
**关联 Issue**：issue [#165](https://github.com/ACANX/AHA/issues/165)（验收第 16 条「只改文档时新打触发标签 ⇒ 照出包并写明『显式请求』」）
**关联档案**：`TS-2610-012`（原生线的步骤级容错）、`TS-2610-014`（原生跳过静默覆盖）
**关联设计**：`Docs/DevSpec/BuildSpec.md` §11.2、`Docs/Guide/BuildGuide.md` §3.3

---

## 1. 现象

ACANX 在 **只改文档** 的 PR [#203](https://github.com/ACANX/AHA/pull/203)（`Docs/Dbsx.txt`）上
新打了 **`SNAPSHOT:NativeDesktop` + `OS:Win`**（原生线的显式请求），**什么都没有发生**：

- **JVM 线正常跑了一次**并按设计主动退出（目标不属于它）；
- **原生线两条工作流（`DesktopNative.yml` / `CliNative.yml`）根本没有启动** —— 运行列表里查无此 run。

原始取证：

```text
$ gh run list --branch docs/issue-121-annotation-followup --limit 15
38669560392 2026-10-15T02:10:09Z pull_request SnapshotJVM → completed/success
386695574- ... （其余为 Build / CodeQL）
# 没有任何 DesktopNative / CliNative 运行

$ gh api .../actions/runs/38669560392/jobs ... | grep 判定完成
判定完成：{"owns_comment": false, "should_build": false, "pr": 203,
           "targets": ["Desktop-Native"], "platforms": ["windows-x64"],
           "line": "jvm", "reason": "…"}      ← JVM 线：目标不归我，正确退出
```

## 2. 排查过程

| # | 动作 | 预期 | 实际 | 结论 |
|---|---|---|---|---|
| 1 | 看 PR 标签 | 应是原生线触发标签 | `SNAPSHOT:NativeDesktop, OS:Win` ✓ | 请求本身没错 |
| 2 | 查该分支全部运行 | 两条原生工作流应各跑一次 | **零次** | 事件没进工作流 |
| 3 | 对比三条线的 `on:` 块 | 应一致 | JVM 线**无** `paths:`；原生线**有** `paths:` 白名单（不含 `Docs/**`）| 触发层被过滤 |
| 4 | **本地仿真**：把 #203 的输入直接喂给判定脚本 | 应出包 | `should_build: true`、`explicit: true`、`reason: 显式请求…（B 组：…）；限定平台：Windows` | **判定逻辑没问题，是事件进不来** |
| 5 | 对照：同一输入但不打标签 | 不出包 | `should_build: false`、`reason: 未打任何 SNAPSHOT:* 触发标签` | 判定逻辑两条路都对 |

```text
$ python3 .github/Python/SnapshotPlan.py --line native --native-target Desktop-Native \
    --labels "SNAPSHOT:NativeDesktop,OS:Win" --changed-files /tmp/cf203.txt \
    --pr 203 --baseline 0.1.3 --label-event --json
{"owns_comment": true, "should_build": true, "targets": ["Desktop-Native"],
 "platforms": ["windows-amd64"], "explicit": true, "line": "native",
 "reason": "显式请求：新打了触发标签 ⇒ 忽略变更分类（B 组：本次只改了不影响产物的路径（如 `Docs/Dbsx.txt`））；限定平台：Windows"}
```

被排除的假设：

| 已排除 | 依据 |
|---|---|
| 判定脚本对「文档 PR + 标签」判断错误 | 第 4 步仿真：同输入直接 `should_build: true` ✓ |
| 标签名写错 / 不是触发标签 | 标签是 `SNAPSHOT:NativeDesktop`（清单里的 4 个之一）✓ |
| 事件类型没订阅（缺 `labeled`）| 两条原生工作流的 `types` 都含 `labeled` ✓（且 JVM 线同类事件确实触发了 ✓）|
| 并发取消 / 权限不足 | 不是「跑了一半被取消」，而是**从未创建 run** ✓ |

## 3. 根因

**一句话**：`on.pull_request.paths` 工作在**触发层**——事件不匹配路径白名单就**不会创建运行**，
判定脚本（承担「显式请求」语义的地方）**根本没有机会跑**。

- **触发条件**：PR 的改动文件全部落在白名单之外（例如只改 `Docs/**`）**且**用「新打触发标签」表达显式请求；
- **机理**：`paths` 是 GitHub 的**事件级**过滤器，与工作流内部的任何 `if` / 判定都无关；
  原生线当初加它是为了省成本（免得每个文档 PR 都烧 5 条腿的原生构建，约 10 分钟）；
- **为什么没被及早发现**：验收第 16 条当初只用 **JVM 线**的一次真机 run 取证
  （run 38546076180：只改文档 + 新打 `SNAPSHOT:Desktop` ⇒ 出包 ✓），而 JVM 线**没有** `paths` 过滤
  ⇒ 那条证据无法暴露原生线的差异。**教训：跨线的一致性条目必须逐线取证。**

## 4. 修复

**思路**：成本控制从**事件层**挪到**作业层** —— 任何 PR 事件都让轻量的 `plan` 先跑（约 10 秒、不编译不发包），
昂贵的腿由 `needs.plan.outputs.should_build` 门控（原生线本来就是这样门控的，见 `native-image` 的 `if`）。

| 文件 | 改动 |
|---|---|
| `.github/workflows/DesktopNative.yml` | 删除 `pull_request.paths` 白名单，并写明「为什么不再用 paths 过滤」 |
| `.github/workflows/CliNative.yml` | 同上 |
| `bin/CheckScripts.py` | 新增助手 `pull_request_paths_filters()` 与守卫第 ⑤ 条：**快照工作流不得在 `pull_request` 上用 `paths:` / `paths-ignore:`**（违规即点名）|
| `Docs/DevSpec/BuildSpec.md` | §11.2 新增同一条硬约定 + 变更日志 v1.31.0 |

## 5. 验证

- **本地仿真**（第 2 节第 4 / 5 步）：同输入 ⇒ `should_build: true` / 无标签 ⇒ `false` ✓；
- **守卫反向验证**：把 `paths:` 加回 `CliNative.yml` ⇒ `CheckScripts` 如期失败并点名
  （`❌ .github/workflows/CliNative.yml:46 的 pull_request.paths 在**触发层**过滤事件…`，退出码 1）；
  还原后四道检查复绿 ✓；
- **真机验证**（合并后执行）：在同一类「只改文档」的 PR 上**重新打一次**原生触发标签 ⇒
  期望原生线工作流启动、`plan` 给出 `should_build: true`（「显式请求…」），并真的产出快照包。

## 6. 教训与沉淀

- **触发层过滤会吃掉「显式请求」**：凡是「用户显式动作」（打标签、发命令）要生效的能力，
  都不能用 `paths` / `branches` 之类的事件级过滤挡在前面 —— 过滤要放在**作业层**；
- **一致性条目要逐线取证**：同一份规范覆盖多条流水线时，一条线的真机 run 不能代表全部；
- 已落到规范与守卫：`Docs/DevSpec/BuildSpec.md` §11.2 + `bin/CheckScripts.py` 第 ⑤ 条断言；
- 遗留事项：当标签属于「另一条线」时，JVM 线因 `owns_comment=false` 不更新评论，
  评论会停留在「未打任何 `SNAPSHOT:*` 触发标签」的旧文案（误导）⇒ 另开 Issue 跟踪。

## 7. 涉及文件

| 类型 | 路径 |
|---|---|
| 工作流 | `.github/workflows/DesktopNative.yml`、`.github/workflows/CliNative.yml` |
| 脚本 / 守卫 | `bin/CheckScripts.py`（`pull_request_paths_filters()` + 第 ⑤ 条）|
| 文档 | `Docs/DevSpec/BuildSpec.md` §11.2、本篇 |

---

## 结论分层

### 已观测事实

- 只改文档 + 新打 `SNAPSHOT:NativeDesktop` ⇒ **原生线零运行**（`gh run list` 为证）；
- JVM 线同事件有运行，且判定 JSON 显示「目标不归我」后正常退出；
- 同输入直接喂判定脚本 ⇒ `should_build: true`（「显式请求…」）。

### 机制 / 行为模型

- `on.pull_request.paths` 属**事件级**过滤：不匹配 ⇒ 不创建运行 ⇒ 工作流内的一切逻辑都不执行；
- 原生线的成本控制因此从「作业层门控」被提前到了「事件层」，把显式请求一并滤掉。

### 未定位 / 未复现

- 修复后的**真机**证据（重打标签 ⇒ 原生线出包）尚未取得 —— 需在合并后于同类 PR 上执行（见 §5 第三条）。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 原生线零运行 | `gh run list --branch docs/issue-121-annotation-followup --limit 15` | 只有 `SnapshotJVM` / `Build` / `CodeQL`，无 `DesktopNative` / `CliNative` |
| JVM 线判定 | `gh api .../actions/runs/38669560392/jobs` 日志 | `{"owns_comment": false, "should_build": false, "targets": ["Desktop-Native"], "line": "jvm", …}` |
| 本地仿真（有标签）| `SnapshotPlan.py --line native --native-target Desktop-Native --labels "SNAPSHOT:NativeDesktop,OS:Win" --label-event` | `should_build: true`、`explicit: true`、`reason: 显式请求：新打了触发标签…` |
| 本地仿真（无标签）| 同上去掉 `--labels` / `--label-event` | `should_build: false`、`reason: 未打任何 SNAPSHOT:* 触发标签` |
| 守卫反向验证 | 加回 `paths:` 后 `python3 bin/CheckScripts.py` | `❌ .github/workflows/CliNative.yml:46 的 pull_request.paths 在**触发层**过滤事件…`，退出码 1 |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 判定脚本判断错 | 本地仿真同输入 ⇒ `should_build: true` |
| 标签名不对 | `SNAPSHOT:NativeDesktop` 是清单里的 4 个触发标签之一 |
| 事件类型未订阅 | `types` 含 `labeled`，且 JVM 线同事件确实触发了运行 |
| 运行被取消 / 权限问题 | 从未创建 run（不是创建后被取消）|

## 未决问题

**Q1 标签属于另一条线时，谁负责把「未出包」评论更新准确？**

现状：JVM 线在 `owns_comment=false`（目标属于原生线）时不碰评论，于是评论停留在旧文案。
阻塞点：跨线协作语义未定义（是让「被请求线」负责，还是让「当前线」把原因更新为『交给 X 线处理』）。
