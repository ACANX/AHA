# TS-202610-RulesetBlocksSingleMaintainer：PR 卡死复盘：规则集要求了「无人能批准」与「没人生产」的检查

> 日期：2026-10-07
> 作者：@ACANX（与 AI 协作）
> 关联 PR：[#7](https://github.com/ACANX/AHA/pull/7)（`dev` → `main`，被规则集锁死）
> 关联记录：[TODO.md](../TODO.md) `G-02` / `G-04`、[BuildSpec.md](../DevSpec/BuildSpec.md) §8.1

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
| [TODO.md](../TODO.md) | `G-02` 重写：附两条规则集的**现状实测表**与**目标规格表**（含审批数、必需检查、`code_scanning` / `code_coverage` 的二选一）；新增 `G-04`（开启 CodeQL，附「不开启就必须删规则」的对应关系） |
| [BuildSpec.md](../DevSpec/BuildSpec.md) | §8.1 新增「分支规则集必须对『单人 + 机器』可满足（强制）」，含两种把 PR 永久锁死的实例 |
| `CHANGELOG.md` | 记录本次整改规格 |
| `PLAN.md` §8.2.9 | 仍以 `G-02` 为闭环出口（本次不新增条目，避免重复记账） |
