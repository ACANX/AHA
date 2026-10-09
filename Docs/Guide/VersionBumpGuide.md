# 版本切换指南

**文档版本**：v1.1.0
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

## 2. 改动清单（硬性 14 处）

> **P3（#95）起口径已变**：POM 侧版本源收敛为根 `pom.xml` 的 `<properties>/<revision>` **1 处**
> （8 个子模块写 `<parent><version>${revision}</version>` 继承），`AppVersion.FALLBACK_VERSION`
> 与版本解耦（固定 `"dev"`）。下面的 §2.1 / §2.2 清单是**收敛之前的历史口径**，
> §3~§6 的操作与检查步骤仍适用；完整口径更新见 TD-00016 §G（#97）。

### 2.1 版本号源（10 处，必须全改）

| # | 文件 | 位置 |
|---|---|---|
| 1 | `pom.xml` | 根 `<version>` |
| 2 | `aha-common/pom.xml` | `<parent>` 下 `<version>` |
| 3 | `aha-extension-api/pom.xml` | 同上 |
| 4 | `aha-core/pom.xml` | 同上 |
| 5 | `aha-tool/pom.xml` | 同上 |
| 6 | `aha-cli/pom.xml` | 同上 |
| 7 | `aha-desktop/pom.xml` | 同上 |
| 8 | `aha-cli-native/pom.xml` | 同上（**常被遗漏**） |
| 9 | `aha-desktop-native/pom.xml` | 同上（**常被遗漏**） |
| 10 | `aha-common/src/main/java/com/acanx/module/aha/common/AppVersion.java` | `FALLBACK_VERSION = "x.y.z-dev"` |

> **为什么必须全改**：子模块按 `<parent><version>` 解析；漏改时 Maven **不报错**（BUILD SUCCESS），
> 但反应堆与产物名仍是旧版本——静默发出错版本号的包。详见 §7 与
> [TS-202610-VersionBumpMissedModules.md](../Troubleshooting/TS-202610-VersionBumpMissedModules.md)。

### 2.2 版本声明（4 处，同步更新）

| # | 文件 | 位置 |
|---|---|---|
| 11 | `README.md` | 「当前版本」 |
| 12 | `AGENTS.md` | 「**当前版本**」 |
| 13 | `Docs/AHA/AHA-Design-V1.md` | 头部「目标版本」与「当前版本」 |
| 14 | `Docs/Guide/ReferenceGuide.md` | 「`dev` 上工作在 `x.y.z`」 |

### 2.3 一次性命令（可选）

允许联网取 `maven-versions-plugin` 时，可用一条命令改根 POM 的 `<revision>`：

```bash
./mvnw versions:set-property -Dproperty=revision -DnewVersion=x.y.z -DgenerateBackupPoms=false
```

> **P3 起（已引入 `<revision>`）**：POM 侧版本只有根 POM 的 `<revision>` **一处**，8 个子模块
> 写 `<parent><version>${revision}</version>` 继承——上面的命令改这一处即可，**不再需要**
> 逐个改 9 个 POM（§2.1 的清单是引入 `<revision>` 之前的历史口径；完整口径更新见 TD-00016 §G）。
>
> ⚠️ 该命令仍**不覆盖** §2.2 的 4 处文档声明；`AppVersion.FALLBACK_VERSION` 已与版本解耦
> （P3 起固定为 `"dev"`），无需同步。
> 另按项目约定，**Agent 不在本地跑 Maven**（见 `AGENTS.md`），因此 Agent 执行版本切换时改
> 根 POM 的 `<revision>` + §2.2 的 4 处文档声明。

## 3. 操作步骤

1. `git fetch origin` → 从 **`origin/dev`** 新建分支：`chore/bump-x.y.z`；
2. 按 §2 改 **10 处版本号源**；
3. 按 §2.2 改 **4 处版本声明**；
4. 按 §4.1 / §4.2 逐条自证（尤其确认**没有误改历史标注**）；
5. `python3 bin/CheckDocs.py`（围栏 / 链接 / 冲突标记 / 命名规范）；
6. 提交，信息形如：

   ```
   chore(release): 基线版本由 a.b.c 切换到 x.y.z
   ```

   正文写清：改了哪 10 处 + 哪 4 处、顺带修正了什么、**有意不改**的位置及原因；
7. 推送并开 PR（`→ dev`），标题同提交主题；
8. 编译与测试以 **PR 的 CI checks** 为准（本地不跑 Maven）；
9. 合并后确认**产物侧**对得上：`dev` 出包线的产物名应为 `x.y.z.PPPPP`。

## 4. 检查清单

### 4.1 改之前

- [ ] 已在 `origin/dev` 最新提交上新建分支（不在过时基线上改）
- [ ] 确认当前基线版本（`grep -m1 '<version>' pom.xml`）
- [ ] 确认目标版本号（由 ACANX 指定）

### 4.2 改完之后（自证）

- [ ] 9 个 `pom.xml` 的**首个** `<version>` 均为 `x.y.z`
- [ ] `AppVersion.FALLBACK_VERSION == "x.y.z-dev"`
- [ ] §2.2 的 4 处版本声明均已更新
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
| 1 | 构建 **BUILD SUCCESS**，但产物名 / 反应堆仍是旧版本 | 子模块 `<parent><version>` 未改（Maven 容忍父子版本不一致，不报错） | 按 §2.1 全改 10 处，再核对产物名 |
| 2 | `aha --version` 仍报旧版本 | 同上；`version.properties` 取的是**模块**的 `project.version` | 同 1 |
| 3 | 文档里还能搜到旧版本 | 漏改 §2.2 的 4 处声明 | 补改；但先确认它不是**历史标注**（见 §4.2） |
| 4 | `CHANGELOG` 的版本链接 404 | tag 实际写法与文档约定不符（历史 0.1.0 是裸 `0.1.0`，约定是 `V*`） | 用 `git ls-remote --tags origin` 核实真实 tag 再改链接；别名 tag 见 Issue #85 |
| 5 | 推送 tag 时提示已存在 | 同版本 tag 已打过 | `Build.yml` 的 tag 作业**幂等跳过**，属正常；需重打见 `ReleaseProcess.md` §4.1 |
| 6 | 自动打的 `V*` tag 没触发发布 | `GITHUB_TOKEN` 推送的 tag 不触发下游工作流（GitHub 防递归） | 手工触发 `Release.yml`，或改用 PAT（`ReleaseProcess.md` §4.2） |
| 7 | 发版 PR 卡在必需检查 / 审批 | 分支规则集要求（审批数、`Gate`/`Compat` 必需检查、`last_push_approval`） | 见 `ReleaseProcess.md` §4.2 与 Issue #85 |
| 8 | 两个原生模块版本没跟上 | `aha-cli-native` / `aha-desktop-native` 是后加模块，容易被旧清单漏掉 | 按 §2.1 的第 8、9 项核对 |
| 9 | 构建号没注入（GUI 只显示基线版本） | 工作流的 `command: >-` 折叠块里写了 `#` 注释，把参数吞掉 | 注释移出折叠块（`ReleaseProcess.md` 第 2 节「构建版本」） |

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
- 版本切换完成后，若本次引入了新的「必须改」位置（如新增模块、新增版本声明），
  必须同步更新 §2 的清单与总数——**清单漏项正是本指南要防的问题**。
