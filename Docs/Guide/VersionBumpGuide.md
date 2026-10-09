# 版本切换指南

**文档版本**：v1.3.0
**状态**：生效
**生效日期**：2026-10-09
**最后更新**：2026-10-09
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-09 | 初始版本：操作步骤、改动清单、检查清单、常见问题快速处置、应急预案、历史教训与维护约定 | @ACANX / CNXNC |
| v1.1.0 | 2026-10-09 | §2.3 一次性命令改用 `versions:set-property -Dproperty=revision`；注明 P3（#95）后 POM 侧版本源收敛为 1 处、`AppVersion` 已解耦，§2.1 清单为历史口径 | @ACANX / CNXNC |
| v1.2.0 | 2026-10-09 | P5（#97）：§2 改动清单由 14 处降为 **1 处**（根目录 `version` 文件，其余由 `VersionBump` / `VersionDistribute.py` 分发）；§2.3 改为「一键分发 + 手工应急」；§3 步骤与 §4 检查清单按新口径重写（一致性校验项保留）；§5 第 1/2/3/8 项更新 | @ACANX / CNXNC |
| v1.3.0 | 2026-10-09 | §5 新增第 10 条：版本切换 PR 上为什么没有 `Gate` / `Compat` 的 checks（触发范围只覆盖 `main`，而 PR base 是 `dev`）与处置 | @ACANX / CNXNC |

---

## 0. 本指南的定位

把「**版本切换**」这一个动作的操作、自证、排障与应急集中到一处。

| 文档 | 记什么 |
|---|---|
| [ReleaseProcess.md](../DevSpec/ReleaseProcess.md) | **规范**：版本号唯一来源、tag 约定、发布步骤与硬性要求 |
| **本指南** | **操作**：怎么改、怎么验、出问题怎么处置（含应急预案） |
| [Troubleshooting/](../Troubleshooting/) | 每次踩坑的**过程记录**（`TS-*`）；本指南只保留结论与处置步骤 |

> 规范与操作分离是项目约定（见 `DocumentationSpec.md` §1）。两者冲突时以 `ReleaseProcess.md` 为准，并修正本指南。

## 1. 何时切、切到哪

| 问题 | 答案 |
|---|---|
| 何时切 | 每个 minor 开发开始前（在 `dev` 上）；**发布前另有一次**由 `release/x.y.z` 分支承载 |
| 切到哪 | 遵循语义化版本；本指南只切**基线版本**（不含构建号） |
| 谁决定 | **ACANX**。Agent **不得自行**切换版本号——版本号是对外承诺，不是实现细节 |
| 构建号 | 形如 `a.b.c.PPPPP`，由 CI 工作流注入（`-Daha.build.version=…`），**不写进 POM** |

## 2. 改动清单（1 处，其余自动分发）

> **P4（#96）起口径已变**：版本号只有**一个权威源**——根目录 `version` 文件（一行纯文本）。
> 其余位置全部由 `Script/Python/VersionDistribute.py` 分发（通常经 `VersionBump` workflow 触发）：

| 位置 | 谁改 |
|---|---|
| 根目录 `version` 文件 | **人**改一行，或 workflow 输入新版本号 |
| 根 `pom.xml` 的 `<properties>/<revision>` | 分发脚本 |
| 8 个子模块的 `<parent><version>` | 无需改——写作 `${revision}` 继承 |
| `AppVersion.FALLBACK_VERSION` | 无需改——已与版本解耦，固定 `"dev"` |
| 4 处文档版本声明（`README.md` / `AGENTS.md` / `AHA-Design-V1.md` / `ReferenceGuide.md`） | 分发脚本（按**声明行**精确匹配，不碰历史叙述） |

> 收敛前的历史口径（10 处版本号源 + 4 处文档声明，以及「只改根 POM 静默产出旧版本」的教训）
> 见 §7 与 [TS-202610-VersionBumpMissedModules.md](../Troubleshooting/TS-202610-VersionBumpMissedModules.md)。

### 2.1 权威源：根目录 `version`

```
version            # 一行纯文本，如 0.1.3（不带 SNAPSHOT；预发行用构建号 a.b.c.PPPPP 表达）
```

### 2.2 一键分发（推荐）

在 Actions 里触发 **`VersionBump`**（`workflow_dispatch`，输入新版本号）。它按
「解析目标版本 → 分发到根 POM 的 `<revision>` 与文档声明 → 自检 → 开 PR」执行，
PR 分支为 `chore/bump-<版本>`、base 为 `dev`。

本地等价命令（Agent 也用这条，不跑 Maven）：

```bash
python3 Script/Python/VersionDistribute.py --version x.y.z
python3 .github/Python/ProjectVersion.py --verify
```

### 2.3 手工应急（不走 workflow）

只需改 **两处**、且必须相同：

1. 根目录 `version` 文件；
2. 根 `pom.xml` 的 `<properties>/<revision>`。

文档声明可留待之后分发。允许联网取 `maven-versions-plugin` 时，第 2 条也可用：

```bash
./mvnw versions:set-property -Dproperty=revision -DnewVersion=x.y.z -DgenerateBackupPoms=false
```

> 按项目约定，**Agent 不在本地跑 Maven**（见 `AGENTS.md`）；Agent 执行版本切换时改
> `version` 文件 + 根 POM 的 `<revision>`（或直接跑上面的分发脚本）。

## 3. 操作步骤

1. `git fetch origin` → 从 **`origin/dev`** 新建分支：`chore/bump-x.y.z`；
2. 分发：触发 `VersionBump` workflow，或本地跑
   `python3 Script/Python/VersionDistribute.py --version x.y.z`
   （workflow 会自己开 PR；手工跑则继续第 3 步）；
3. 自证：`python3 .github/Python/ProjectVersion.py --verify`（五处一致）
   + `python3 bin/CheckDocs.py`（围栏 / 链接 / 冲突标记 / 命名规范）；
4. 提交，信息形如：

   ```
   chore(release): 基线版本由 a.b.c 切换到 x.y.z
   ```

   正文写清：权威源改了什么、分发脚本改了哪些位置、**有意不改**的位置及原因
   （如历史叙述里的旧版本号）；
5. 推送并开 PR（`→ dev`），标题同提交主题；
6. 编译与测试以 **PR 的 CI checks** 为准（本地不跑 Maven）；
7. 合并后确认**产物侧**对得上：`dev` 出包线的产物名应为 `x.y.z.PPPPP`。

## 4. 检查清单

### 4.1 改之前

- [ ] 已在 `origin/dev` 最新提交上新建分支（不在过时基线上改）
- [ ] 确认当前基线版本（`python3 .github/Python/ProjectVersion.py` 或 `cat version`）
- [ ] 确认目标版本号（由 ACANX 指定）

### 4.2 改完之后（自证）

- [ ] `python3 .github/Python/ProjectVersion.py --verify` 通过（`version` 文件 / `<revision>` /
      `version.properties` / 产物名 / tag 规则五处一致）
- [ ] 8 个子模块的 `<parent><version>` 均为 `${revision}`（未被写死）
- [ ] `AppVersion.FALLBACK_VERSION == "dev"`（与版本解耦）
- [ ] 4 处文档版本声明已更新（分发脚本按声明行精确匹配，不碰历史叙述）
- [ ] **历史标注未被误改**：`@since a.b.c`、实测数据标题（如「实测覆盖率（a.b.c）」）、
      `CHANGELOG` 的历史版本段、`PLAN` / `TODO` / `Troubleshooting` / `DevLog` 中的历史版本号
- [ ] `CHANGELOG.md` 的 `[Unreleased]` **保持不动**（待发布时再归入 `x.y.z`）
- [ ] `python3 bin/CheckDocs.py` 通过

### 4.3 合并之后

- [ ] `dev` 出包线（`BuildJVMArtifacts` / `DesktopNative` / `CliNative`）产物名带新版本
- [ ] CLI `version` 输出新版本（`Dist/bin/Aha.sh version`）
- [ ] 若本次是**发布**，另按 `ReleaseProcess.md` §1 的发布前检查执行

## 5. 常见问题快速处置

| # | 症状 | 原因 | 处置 |
|---|---|---|---|
| 1 | 构建 **BUILD SUCCESS**，但产物名 / 反应堆仍是旧版本 | `version` 文件与根 POM 的 `<revision>` 不一致；或子模块 `<parent><version>` 被写死 | 跑 `python3 .github/Python/ProjectVersion.py --verify` 定位，再按 §2.2 补跑分发脚本 |
| 2 | `aha --version` 仍报旧版本 | 同上；`version.properties` 取的是**模块**的 `project.version` | 同 1 |
| 3 | 文档里还能搜到旧版本 | 声明行未更新；或它本来就是**历史标注** | 先按 §4.2 判断：历史叙述里的旧版本号是合法内容；确属声明行未更新时，补跑分发脚本 |
| 4 | `CHANGELOG` 的版本链接 404 | tag 实际写法与文档约定不符（历史 0.1.0 是裸 `0.1.0`，约定是 `V*`） | 用 `git ls-remote --tags origin` 核实真实 tag 再改链接；别名 tag 见 Issue #85 |
| 5 | 推送 tag 时提示已存在 | 同版本 tag 已打过 | `Build.yml` 的 tag 作业**幂等跳过**，属正常；需重打见 `ReleaseProcess.md` §4.1 |
| 6 | 自动打的 `V*` tag 没触发发布 | `GITHUB_TOKEN` 推送的 tag 不触发下游工作流（GitHub 防递归） | 手工触发 `Release.yml`，或改用 PAT（`ReleaseProcess.md` §4.2） |
| 7 | 发版 PR 卡在必需检查 / 审批 | 分支规则集要求（审批数、`Gate`/`Compat` 必需检查、`last_push_approval`） | 见 `ReleaseProcess.md` §4.2 与 Issue #85 |
| 8 | 两个原生模块版本没跟上 | 历史问题：旧清单漏了后加模块 | 已消除——子模块统一写 `${revision}`，分发脚本**动态扫描 POM** 并断言数量 ≥ 9（新增模块自动覆盖） |
| 9 | 构建号没注入（GUI 只显示基线版本） | 工作流的 `command: >-` 折叠块里写了 `#` 注释，把参数吞掉 | 注释移出折叠块（`ReleaseProcess.md` 第 2 节「构建版本」） |
| 10 | 版本切换 PR 上**没有** `Gate` / `Compat` 的 checks | 这两个工作流的 `pull_request` 触发范围只覆盖 `main`，而版本切换 PR 的 base 是 `dev`——这是既定触发范围，不是配置错误 | `Build` / `CodeQL` 会正常跑（前提：已配 PAT，否则需人工 close/reopen 或补一次 push，见 §2.2）；若本次切换也要过门禁，就把 PR 指向 `main`，或在两个工作流里显式扩大触发范围（口径问题登记在 issue #96 的 F12） |

## 6. 应急预案

> **危险操作前置提醒**：`git push --force`、删除 / 改写远程 tag、删除已发布的 Release 资产
> 都属**改写远程历史或破坏已发布产物**。按身份约定，**必须先取得 ACANX 明确授权**，Agent 不自行执行。

### 6.1 改错了，尚未提交

```bash
git checkout -- .          # 丢弃工作区改动，重新按 §2 执行
```

### 6.2 已提交并推送，但 PR 未合并

**首选**：在同一分支追加一个修正提交（保持可追溯），不要把错误抹掉再重来。

### 6.3 已合并到 `dev`，但尚未发布

按 §2 重新切一次（新分支 + 新提交 + 新 PR），在 PR 描述里说明「修正上一次切换的遗漏」。
**不要**为了让历史好看而 `force push`。

### 6.4 已打 tag / 已发布，才发现版本号错了

判断依据（三选一，按影响面从大到小）：

| 情况 | 处置 |
|---|---|
| 只是**内部**版本号写错，对外无影响 | 保留该版本，**下一个版本修正**；在 `CHANGELOG` 注明 |
| 版本号与 `CHANGELOG` 段/文档对不上 | 修文档与 `CHANGELOG`，**tag 与资产不动**（已下载的包不应被替换） |
| 错到了无法接受（如版本号与制品内容不符） | 需 ACANX 决策：删除 Release 资产并重打 tag，或**发一个修补版本**（推荐，可追溯） |

**原则**：**已发布的产物不覆盖**。宁可发一个修正版，也不静默替换已发布的文件——
替换会让「下载到的包到底是什么」失去可追溯性。

### 6.5 tag 与约定不符（历史遗留）

例：0.1.0 的实际 tag 是裸 `0.1.0`，而约定是 `V*`。

处置选项：① 补一个指向**同一提交**的 `V0.1.0` 别名 tag（最省事，两写法都可用）；
② 在 `CHANGELOG` 中明确写出兼容策略。**不要**删除原 tag——已发布的引用会失效。

### 6.6 需要回滚一个已合并的版本切换

```bash
git revert <切换提交的 SHA>      # 生成反向提交，保留历史
```

再用常规 PR 流程合入。若已发布，回滚版本号**不等于**撤回已发布的包，仍需按 §6.4 处置制品侧。

## 7. 历史教训（必读）

来自 [TS-202610-VersionBumpMissedModules.md](../Troubleshooting/TS-202610-VersionBumpMissedModules.md)，
每一条都对应上面的某处处置：

1. **「只改一处」的规范必须实测一遍再相信**——判定依据是反应堆的 `Building <模块> <版本>` 行与产物名，不是读 POM；
2. **静默错误比失败更危险**——Maven 容忍父子版本不一致，「构建绿灯」不能证明版本号改对了；
3. **版本号要顺着链路验到最末端**：根 POM → 子 POM `<parent>` → 资源过滤 → `version.properties` → `AHA x.y.z`；
4. **「当前版本」与「历史标注」必须分开处理**——一刀切替换会把历史实测数据改成假数据；
5. **发布物与发布记录要对得上账**——tag 名与文档写法不一致就会 404；
6. **能力属于哪个版本看合入时间线**——发布之后才合入的改动不能写进上一版 `CHANGELOG` 段。

## 8. 维护约定（本指南必须持续更新）

- **每次版本切换后**：把新踩的坑回填到 §5（症状 → 原因 → 处置）或 §6（应急预案），
  并在上方变更日志记一行；
- 排查**过程**写 `Docs/Troubleshooting/`（`TS-*`），本指南只保留**结论与处置步骤**；
- 若发现 `ReleaseProcess.md`（规范）与本指南不一致，**以规范为准并修正本指南**；
- 版本切换完成后，若本次引入了新的「必须改」位置（新增文档版本声明文件、新的代码常量等），
  必须同步更新 §2 的表格与 `Script/Python/VersionDistribute.py` 的分发清单——**清单漏项正是本指南要防的问题**
  （POM 已由动态扫描覆盖，新增模块无需改脚本）。
