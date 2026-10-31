# GitHub Actions 规范

**文档版本**：v1.1.0
**状态**：生效
**生效日期**：2026-10-16
**最后更新**：2026-10-17
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.1.0 | 2026-10-17 | §2.1 补齐 5 个可复用工作流；新增 §2.3「显示名 / 作业 id / commit status context / 平台标签」并指向 `WorkflowDisplayNameSpec.md`（issue [#287](https://github.com/ACANX/AHA/issues/287)）—— 显示名必须是**静态**英文大驼峰（零表达式、零标点），矩阵因此展开为逐腿静态作业 | @cnxnc |
| v1.0.0 | 2026-10-16 | 初版：工作流文件的**命名**（`name:` 大驼峰单数、与文件名一一对应）、触发层不过滤、权限白名单与 PR 评论所需的 `pull-requests: write`、结论（commit status）与幂等留痕（`if: always()`）、守卫清单 | @ACANX / CNXNC |

---

## 1. 定位

约束 **`.github/workflows/*.yml` 的写法** —— 命名、触发、权限、留痕、校验这一类「每个工作流都要遵守、
且改坏时**不会报错**、只在真用时才发现」的约定。

| 主题 | 去哪儿看 |
|---|---|
| 门禁分层（哪些检查在哪儿跑、必需 / 可选管线、**作业名**与分支保护的契约）| [`BuildSpec.md`](BuildSpec.md) §7 / §8.1 |
| 发布链路（tag、`Release.yml`、防递归与派发）| [`ReleaseProcess.md`](ReleaseProcess.md) §2 / §4.2 |
| 快照产物与命令式工作流的单一来源 | [`BuildSpec.md`](BuildSpec.md) §11 |
| `run:` 里写 shell 的跨平台坑 | [`DocumentationSpec.md`](DocumentationSpec.md) §4 |

> 本文件只写**写法**约定；构建内容、门禁分层、发布步骤都不在这里重复。

## 2. 命名（强制）

### 2.1 工作流的显示名 `name:`

工作流**顶层** `name:` 的**值**统一采用**大驼峰（PascalCase）单数** —— **不带空格、不带连字符、不写句子**：

| 工作流文件 | `name:` |
|---|---|
| `Build.yml` | `Build` |
| `BuildJVMArtifacts.yml` | `BuildJVMArtifacts` |
| `CliNative.yml` | `CliNative` |
| `CodeQL.yml` | `CodeQL` |
| `Compat.yml` | `Compat` |
| `DesktopNative.yml` | `DesktopNative` |
| `Gate.yml` | `Gate` |
| `PreDeployVerify.yml` | `PreDeployVerify` |
| `Release.yml` | `Release` |
| `SnapshotAssert.yml` | `SnapshotAssert` |
| `SnapshotCleanup.yml` | `SnapshotCleanup` |
| `SnapshotJVM.yml` | `SnapshotJVM` |
| `VersionBump.yml` | `VersionBump` |
| `BuildLeg.yml` | `BuildLeg` |
| `BuildMvnArtifactLeg.yml` | `BuildMvnArtifactLeg` |
| `CliNativeImageLeg.yml` | `CliNativeImageLeg` |
| `DesktopPackageLeg.yml` | `DesktopPackageLeg` |
| `NativeImageLeg.yml` | `NativeImageLeg` |

**为什么**：这个字符串到处都是 —— Actions 页面、运行列表、PR 上的检查分组、`gh run list` 的输出列。
写成 `Snapshot Assert` 这类带空格的写法时，「文件名」与「显示名」就成了**两套写法**，检索、对照、
贴给别人看都要在脑子里换算一次；而这种漂移**不会报错**，只会一直错下去。

**它不影响分支保护**：必需检查的契约是**作业名**（见 [`BuildSpec.md`](BuildSpec.md) §8.1），
不是工作流显示名 —— 改 `name:` 不动必需检查；反过来，也**不要**把显示名当卡点标识写进分支保护。

**机械校验**：`bin/CheckScripts.py::check_workflow_names()` 逐个工作流断言本规则（文件名 + `name:` + 两者一一对应），
不满足即红。

**`*Leg.yml` 是可复用工作流**（`on: workflow_call`）：它们只被主工作流 `uses:` 调用，不产生独立运行条目；
文件名与 `name:` 同样一一对应，规则不因「不直接触发」而放宽。

### 2.2 文件名

工作流文件名与本仓库其余 Markdown 一致，采用**大驼峰**，并**与 `name:` 一一对应**
（`SnapshotCleanup.yml` ↔ `name: SnapshotCleanup`）——两者一旦分开写，
在 Actions 列表、运行日志、`grep` 结果之间对照就要在脑子里换算一次。

**不允许 kebab-case / snake_case 文件名**（`snapshot-cleanup.yml` 已在 2026-10-16 更名为
`SnapshotCleanup.yml`，本条不再有例外）。

### 2.3 显示名、作业 id、context 与平台标签

| 对象 | 规则 | 细则 |
|---|---|---|
| 显示名（`name:` / `run-name:`，含**作业与步骤**）| **静态**英文大驼峰 `^[A-Z][A-Za-z0-9]*$`；**零表达式、零标点、零中文** | [WorkflowDisplayNameSpec.md](WorkflowDisplayNameSpec.md) §2.1 |
| 作业 id（`jobs.<id>`）| 与显示名同规则；矩阵作业**展开为逐腿静态作业** | 同上 §2.2 |
| commit status context | 与显示名同规则（`SnapshotAssert` / `PreDeployVerify`）| 同上 §2.3 |
| 平台标签 | 架构词统一 `aarch64`；读取兼容历史 `macos-arm64` | 同上 §2.4 |

**为什么显示名必须静态**：`${{ … }}` 进显示名会让 PR 检查列表的名字随矩阵值变化 ——
分支保护按作业名匹配（`BuildSpec.md` §8.1），一改矩阵键就悄悄改了检查名；而这类漂移**不会报错**。
因此矩阵一律展开为逐腿静态作业，重复的 steps 放进 `*Leg.yml` 可复用工作流（逻辑只有一份）。

**机械校验**：`bin/CheckScripts.py::check_workflow_display_names()` 扫描全部 `name:` / `run-name:`
（顶层、作业、步骤三层），命中 `${{`、中文、标点或非大驼峰即红。

## 3. 触发

- **不在触发层做过滤**（`on.pull_request.paths` / `paths-ignore`）：那是在**事件层**丢事件 ——
  「只改文档 + 新打触发标签」这类显式请求会进不来，现象是「打了标签**毫无反应**」且**没有报错**
  （2026-10-15 实测 PR #203）。要按路径取舍，就把判断**挪进作业里**，让 plan 类步骤先跑。
- **`GITHUB_TOKEN` 推的提交 / tag 不触发下游工作流**（GitHub 防递归）—— 这是既定行为，不是配置错误。
  例外是 `workflow_dispatch` 与 `repository_dispatch`（`workflow_call` / `workflow_run` 也不经事件层）。
- **派发要显式指定 ref**：`workflow_dispatch` 的 `GITHUB_SHA` 是**所派发 ref 的最新提交**，
  默认分支与目标分支不是同一个提交时，会在别的提交上跑完整个流程。完整口径见
  [`ReleaseProcess.md`](ReleaseProcess.md) §4.2。

## 4. 权限

- 顶层 `permissions:` 是**白名单**：一旦写了它，**没有列出的作用域一律为 `none`**。漏一项的后果是
  运行期 **403**，而不是写文件时报错 —— 所以「跑通了就以为写对了」在这里不成立。
- **写 PR 评论**走的是 issues 接口（`POST /repos/{}/issues/{comments}`），但**只给 `issues: write`
  实测是 403**（`Resource not accessible by integration`，2026-10-16 实测）：`GITHUB_TOKEN` 对
  **PR 对话**的写权限落在 **`pull-requests: write`** 上。两者都要给，缺一个就在「回写留痕」这一步红。
- 只读的作业**显式**写 `contents: read`，不要靠默认值（默认值会随仓库设置变化）。

## 5. 结论与留痕

- **结论优先落成 commit status**：`-f context=<小写连字符名>`，它绑 commit、可被分支保护引用 ——
  「要卡点」就是把该 context 加进必需检查，机制本身不用改。
- **留痕用幂等评论**：固定 marker 定位自己那一条（[`SnapshotComment.py`](../../.github/Python/SnapshotComment.py)），
  重复运行**只更新、不新增**。
- **失败不得静默**：打标与留痕两步都要 `if: always()` —— 否则验证未过时它们被跳过，
  现象是「作业红了，但 PR 上一个字都没有」。
- **可选管线不进必需检查**：可选 / 试验性管线的作业名不得写进分支保护，容错层级的选择见
  [`BuildSpec.md`](BuildSpec.md) §7。

## 6. 校验

改完任何工作流，**本地先跑** `python3 bin/CheckScripts.py`（它涵盖全部 YAML 相关断言）：

| 守卫 | 断言 |
|---|---|
| `check_workflow_names()` | 顶层 `name:` 为大驼峰单数（本文 §2.1）|
| `check_workflow_display_names()` | 所有 `name:` / `run-name:` 为**静态**英文大驼峰（本文 §2.3）|
| `check_yaml_shell_vars()` | `run:` 里 `$VAR` 后不得紧跟非 ASCII（macOS bash 3.2 会把中文字符并进变量名 ⇒ unbound variable）|
| `check_yaml_input_bool_compare()` | 不得对 `inputs.*` 做宽松布尔比较（`push` 事件下 `inputs` 为 `null`，`null != false` 求值为 false ⇒ 作业被**静默跳过**，issue #213）|
| `check_snapshot_assert_contract()` | 快照自测留痕的 context / 命令 / 权限 / 清单编号一致 |
| `check_pre_deploy_verify_contract()` | 部署前验证的触发、权限、**失败也留痕**、升级路径写进规范 |

> 这些守卫的存在理由都一样：**写错了不会报错**，只会在某一天以「命令没反应」「包没出来」
> 「PR 上什么都没有」的形式暴露出来。凡属同类的约定，都应在 `bin/CheckScripts.py` 里落成断言。
