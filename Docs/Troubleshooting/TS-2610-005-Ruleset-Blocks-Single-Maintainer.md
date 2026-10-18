# TS-2610-005 · PR 卡死复盘：规则集要求了「无人能批准」与「没人生产」的检查

> 日期：2026-10-07
> 作者：@ACANX（与 AI 协作）
> 关联 PR：[#7](https://github.com/ACANX/AHA/pull/7)（`dev` → `main`，被规则集锁死）
> 关联记录：[TODO.md](../TODO/README.md) `G-02` / `G-04`、[BuildSpec.md](../DevSpec/BuildSpec.md) §8.1

---

## 一、背景

PR #7（`dev` → `main`）的合并框上挂着两条消息，看起来都不像代码问题：

```
Waiting for Code Scanning results. Code Scanning may not be configured for the target branch.
New changes require approval from someone other than ACANX because they were the last pusher.
```

两条都不是「检查失败」——它们是**永远等不到结果**。这类状态比红色更麻烦：
红色至少告诉你哪里错了。

---

## 二、排障过程与修复链

### 2.1 先分清是「规则集（Ruleset）」还是「经典分支保护」

消息里的措辞（`someone other than ...`）是**规则集**的口径，不是经典分支保护。规则集对
**仓库级**对象无需管理员权限即可读取，于是直接问 API：

```
GET /repos/ACANX/AHA/rulesets                 → 两条：dev(24648482)、main(24648542)
GET /repos/ACANX/AHA/rules/branches/{dev,main}
```

实测结果（逐条规则）：

| 规则集 | 适用分支 | 规则 |
| ---- | ---- | ---- |
| `dev` | `refs/heads/dev` | `deletion`、`non_fast_forward`、`pull_request`（approvals=**1**，`last_push_approval=false`）、`required_status_checks`（`build (ubuntu-latest, system)`、`build (ubuntu-latest, wrapper)`、`build (windows-latest, wrapper)`） |
| `main` | `refs/heads/main` | `deletion`、`non_fast_forward`、**`code_scanning`（CodeQL，high_or_higher / errors）**、**`code_coverage`**、`pull_request`（approvals=**1**、`last_push_approval`=**true**、`review_threads=true`）、`required_status_checks`（同上三条） |

⇒ 用户看到的两条消息**都对应 `main` 的规则**（`last_push_approval` 与 `code_scanning`）。
而当前打开的 PR 正是 **#7 `dev` → `main`**，对得上。

### 2.2 「无人能批准」：审批规则与实际协作者数量不匹配

`approvals=1` 且 `last_push_approval=true`：仓库只有 ACANX 一位协作者，而 GitHub
**不允许自我批准**。于是任何改动都凑不齐那 1 个批准——不是「暂时没批」，是**永远批不了**。

### 2.3 「没人生产」：要求了某工具的结果，却没配那个工具

`code_scanning` 要求 **CodeQL** 的结果，但仓库里只有 4 个工作流（`Build` / `Gate` /
`Compat` / `Release`），**没有任何 code scanning 配置**，也从没上传过 SARIF。
没有结果可等，于是永久停在 `Waiting for Code Scanning results`。

同一类问题还有一条 **`code_coverage`**：它需要把覆盖率数据上传给 GitHub 或其支持的
覆盖率服务，而本项目的覆盖率只有**本地** JaCoCo 门禁（`jacoco:check` ≥ 0.70 +
`bin/ReportCoverage.py` 把实测值打进日志）。用户还没看到这条消息，只是因为它排在其它
条件之后——**三条里最容易被漏掉的就是它**。

### 2.4 方向相反的另一处：该拦的没拦

`main` 的必需检查只有三条 `build (...)`（快检查），而 `BuildSpec.md` §8.1 明文要求
`Gate` 与 `Compat` 也必须是**必需检查**——这正是早就登记在 `TODO.md` 的 `G-02`。
也就是说当前状态是两头都不对：

- 真正该守门的 `Gate`（完整 verify + 覆盖率 + 文档 + 技能 + 脚本 + 重复率）与
  `Compat`（Maven 3.9.x）**没在拦**；
- 而三条谁也满足不了的规则**把人锁死了**。

### 2.5 修复：一次性给出规则集规格（落在 `TODO.md` `G-02`）

既然是同一次编辑，就一次配到位，规格写进 `G-02`（逐字段的目标值 + 理由）。取舍的核心是两条：

1. **审批数按实际协作者数量设置**：单人仓库设 `0`，把卡点交给**必需检查**——
   检查是机器执行的，不看人数。
2. **要求什么结果，就必须有谁生产**：`code_scanning` 要么开 CodeQL（见 `G-04`）、
   要么删掉这条规则；`code_coverage` 建议删掉（本地已有 `jacoco:check` + 实测值入日志，
   再引外部服务属重复把关）。

---

## 三、最终验证结果

**本次无法在本环境验证**：改动目标是仓库规则集，需要管理员权限，本环境没有可写凭据
（`GIT_TERMINAL_PROMPT=0 git push` 实测 `could not read Username`，exit 128）。
所以把**验收标准**写进 `G-02`，由人工执行时逐条核对：

| 验证 | 期望 |
| ---- | ---- |
| 五项必需检查 | 三条 `build (...)` + `门禁（Maven 4 wrapper：verify + 覆盖率 + 文档 + 技能 + 脚本 + 重复率）` + `兼容性（Maven 3.9.x 完整 verify）` 全部出现在 `main` 的必需检查里 |
| 预期失败的 PR | 确实**合不进去**（证明检查真的在拦） |
| 正常 PR | **单人也能合进去**——不再出现「等待批准」「等待 Code Scanning」 |

本次**已核实的事实**（API 实测，作为后续对照基线）：仓库 `public`（CodeQL 免费可用）、
`default_branch=dev`、仓库级规则集两条、`dependa` 上无规则、`Build.yml` 的 macOS 腿
**不在**必需检查里（与 `BuildSpec.md` §4.1 的「演示与可选」定位一致）。

---

## 四、关键教训

1. **规则集与门禁是同一份契约的两端，必须成对设计**。要求一个检查，就要保证**有人生产**它；
   设置一条审批要求，就要保证**有人能满足**它。否则不是卡点，是死锁。
2. **审批数取决于协作者数量，不取决于「严格程度」**。单人仓库里「1 个批准」等价于禁止合并；
   想更严应该加检查，而不是加人。
3. **「等不到结果」比「失败」更危险**：失败会告诉你哪里错，等待不会。看到 `Waiting ...`
   先问一句：**这个结果由谁生产？**
4. **读规则集不一定要管理员权限**：仓库级 ruleset 可直接用 API 读到（`/rulesets`、
   `/rules/branches/<branch>`），本次正是靠它把定性做完，才没有在「是不是 GitHub 抽风」上打转。
5. **与 `F-08` 同源**：那次是**作业名**失配（必需检查永远停在 `Expected`），
   这次是**规则要的东西不存在**（永远停在 `Waiting`）。两次的根因都是
   「规则、作业名、实际产出」三者没有对齐——所以维护约定必须写进 `G-02` 并每次回写。
6. **漏看一条规则的代价**：`code_coverage` 一直没被注意到，因为消息排在后面。
   规则集要**整体读一遍**，不能只看眼前那条红字。

---

## 五、涉及文件清单

| 文件 | 改动 |
| ---- | ---- |
| [TODO.md](../TODO/README.md) | `G-02` 重写：附两条规则集的**现状实测表**与**目标规格表**（含审批数、必需检查、`code_scanning` / `code_coverage` 的二选一）；新增 `G-04`（开启 CodeQL，附「不开启就必须删规则」的对应关系） |
| [BuildSpec.md](../DevSpec/BuildSpec.md) | §8.1 新增「分支规则集必须对『单人 + 机器』可满足（强制）」，含两种把 PR 永久锁死的实例 |
| `CHANGELOG.md` | 记录本次整改规格 |
| `PLAN.md` §8.2.9 | 仍以 `G-02` 为闭环出口（本次不新增条目，避免重复记账） |

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「一、二、…」编号冲突。 -->

## 结论分层

### 已观测事实

- PR #7（`dev` → `main`）的合并框上挂着两条消息：
  `Waiting for Code Scanning results. Code Scanning may not be configured for the target branch.`
  与 `New changes require approval from someone other than ACANX because they were the last pusher.`
  两者**都不是检查失败**，而是**永远等不到结果**。
- 直读规则集 API：`GET /repos/ACANX/AHA/rulesets` → 两条（`dev` = `24648482`、`main` = `24648542`）。
  逐条规则实测：
  - `dev`：`deletion`、`non_fast_forward`、`pull_request`（`approvals=1`、`last_push_approval=false`）、
    `required_status_checks`（三条 `build (…)`）；
  - `main`：上述之外**再加** `code_scanning`（CodeQL，`high_or_higher` / `errors`）、`code_coverage`，
    且 `pull_request` 为 `approvals=1`、`last_push_approval=true`、`review_threads=true`。
- 用户看到的两条消息**都对应 `main` 的规则**，而当时打开的 PR 正是 `dev` → `main`，对得上。
- 仓库**只有 ACANX 一位协作者**；GitHub **不允许自我批准**。
- 仓库只有 4 个工作流（`Build` / `Gate` / `Compat` / `Release`），**没有任何 code scanning 配置**,
  也从未上传过 SARIF。
- `main` 的必需检查**只有三条 `build (…)`**（快检查），`Gate` 与 `Compat` **未纳入**。
- 旁证（已核实事实）：仓库为 `public`（CodeQL 免费可用）、`default_branch=dev`、
  `dependa` 上无规则、`Build.yml` 的 macOS 腿**不在**必需检查里。
- 本环境**无管理员凭据**：`GIT_TERMINAL_PROMPT=0 git push` → `could not read Username`，exit `128`。

### 机制 / 行为模型

- **「等不到结果」比「失败」更危险**：失败会指出哪里错，等待不会。看到 `Waiting …`
  先问一句：**这个结果由谁生产？**
- **审批数取决于协作者数量，而非「严格程度」**：单人仓库里 `approvals=1`（叠加
  `last_push_approval=true`）**等价于禁止合并**；想更严应该加检查，而不是加人。
- **要求什么结果，就必须有谁生产**：`code_scanning` 需要有人上传 SARIF（CodeQL）；
  `code_coverage` 需要有人把覆盖率数据上传给 GitHub 或其支持的覆盖率服务。
  本项目两者都没有生产方，因此这两条规则只会永久停在等待。
- **与 `F-08`（`TS-2610-001`）同源**：那次是**作业名**失配（永远停在 `Expected`），
  这次是**规则要的东西不存在**（永远停在 `Waiting`）；根因都是
  「规则、作业名、实际产出」三者没有对齐。
- 依据：上列规则集 API 实测（属可直接读到的**事实**）+ 由消息语义推出的因果链。

### 未定位 / 未复现

- **本次无法在本环境验证**：改动目标是仓库规则集，需管理员权限，本环境没有可写凭据
  （`could not read Username`，exit 128）。因此「验收标准」只是写进 `G-02`，**尚未执行、尚未核对**。
- 「**预期失败的 PR 确实合不进去**」与「**正常 PR 单人也能合进去**」两条验收，
  均**待人工执行时核对**，本档案未观测。
- `code_coverage` 这条规则**从未被观测到实际弹给用户**（消息排在其它条件之后），
  其真实触发时序属于**未复现**。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 两条「等不到」的消息 | GitHub PR #7 合并框原文 | `Waiting for Code Scanning results. Code Scanning may not be configured for the target branch.` / `New changes require approval from someone other than ACANX because they were the last pusher.` |
| 规则集清单 | `GET /repos/ACANX/AHA/rulesets` | 两条：`dev(24648482)`、`main(24648542)` |
| `dev` 的规则 | `GET /repos/ACANX/AHA/rules/branches/dev` | `deletion` / `non_fast_forward` / `pull_request`（`approvals=1`，`last_push_approval=false`）/ `required_status_checks`（`build (ubuntu-latest, system)`、`build (ubuntu-latest, wrapper)`、`build (windows-latest, wrapper)`） |
| `main` 的规则 | `GET /repos/ACANX/AHA/rules/branches/main` | 同上，**另加** `code_scanning`（CodeQL，`high_or_higher` / `errors`）、`code_coverage`；`pull_request` 为 `approvals=1`、`last_push_approval=true`、`review_threads=true` |
| 协作者数量 | 仓库设置 | **1 人**（ACANX）；GitHub 不允许自我批准 |
| code scanning 的生产方 | 查 `.github/workflows/` | 4 个工作流（`Build` / `Gate` / `Compat` / `Release`），**无** code scanning 配置，**无** SARIF 上传 |
| `main` 的实际必需检查 | 同上规则集 | 只有三条 `build (…)`——`Gate` / `Compat` **未纳入** |
| 本环境的权限边界 | `GIT_TERMINAL_PROMPT=0 git push` | `could not read Username`，exit **128** |

> **对照关系（规范要求）**：`dev` 与 `main` 两条规则集的**逐条差异**即为对照——
> `main` 比 `dev` 多出的 `code_scanning` / `code_coverage` 正是「没人生产」的两条。

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「GitHub 抽风 / 平台故障」 | 直读规则集 API，两条消息均能逐条对应到 `main` 的具体规则（「二、2.1」） |
| 「是经典分支保护，不是规则集」 | 消息措辞（`someone other than …`）是**规则集**口径，且 API 确实读到 ruleset 对象（「二、2.1」） |
| 「检查失败了（红色）」 | 两条消息都**不是失败**，而是等待（「一、背景」） |
| 「审批只是暂时没批」 | 仓库仅 1 位协作者 + GitHub 不允许自我批准 ⇒ `approvals=1` **永远**无法满足（「二、2.2」） |
| 「Code Scanning 只是还没出结果」 | 仓库根本没有 code scanning 配置，也从未上传 SARIF ⇒ 没有结果可等（「二、2.3」） |
| 「只需修眼前那条红字」 | 还有 `code_coverage` 同属「没人生产」，只是消息排在后面（「二、2.3」） |
| 「读规则集需要管理员权限」 | 仓库级 ruleset 用 API 即可读，本次正是靠它把定性做完（「四、关键教训」第 4 条） |

## 未决问题

**Q1（`G-02`）规则集整改执行了吗？**

现状：规格已写入 `TODO.md` `G-02`（逐字段目标值 + 理由），**本次未执行**。
阻塞点：需管理员权限，本环境没有可写凭据 → **需 ACANX 以管理员身份逐条核对**。

**Q2（`G-04`）CodeQL 是「开启」还是「删掉 `code_scanning` 规则」？**

现状：**二选一未定**。规格里写明「要么开 CodeQL、要么删规则」，未拍板。

**Q3 `Gate` / `Compat` 何时纳入必需检查？**

现状：`BuildSpec.md` §8.1 已明文要求二者为必需检查，规则集里**尚未落地**。
阻塞点：需先保证两条 job 名稳定——否则重演 `F-08`（`TS-2610-001`）的作业名失配。

**Q4 要不要 GitHub 侧的 `code_coverage`？**

现状：档案建议**删掉**（本地已有 `jacoco:check` ≥ 0.70 + `bin/ReportCoverage.py` 把实测值打进日志，
再引外部覆盖率服务属重复把关），**未拍板**。
