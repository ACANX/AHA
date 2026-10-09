# TD-00016 SingleSourceVersionBump

> 待办编号：TD-00016
> 标题：版本切换收敛为「改一行」——引入 `${revision}` + 单一读取入口 + 一致性校验
> 状态：✅ 已完成（2026-10-09）
> 跟踪 Issue：父 [#92](https://github.com/ACANX/AHA/issues/92)；子任务 [#93](https://github.com/ACANX/AHA/issues/93)（P1 校验）/ [#94](https://github.com/ACANX/AHA/issues/94)（P2 读取收敛）/ [#95](https://github.com/ACANX/AHA/issues/95)（P3 `${revision}`）/ [#96](https://github.com/ACANX/AHA/issues/96)（P4 分发器）/ [#97](https://github.com/ACANX/AHA/issues/97)（P5 文档）
> 创建日期：2026-10-09
> 最后更新：2026-10-09

---

## 来源

来自 ACANX 的批评与要求：**改版本号本该是一行参数的事**（Spring 项目即如此），
当前却要手改多处，且清单靠人记——已经漂移过一次
（`ReleaseProcess.md` 写「8 处 / 六个子模块」，实际 10 处 / 八个；PR #89 修正 §2 时还漏了 §4.2 第 249 行）。

> 本文描述的是**改造前**的状态与目标；落地的最终口径见文末「落地结果」，
> 以及 `ReleaseProcess.md` §2 / `VersionBumpGuide.md` §2。

## 根因（如实记录）

0.1.0 基线是在 IDE 默认生成的 POM 结构上起步的（默认即「根 `<version>` 硬编码 + 子模块
`<parent><version>` 硬编码」），**当时没有把「版本号」当成一项配置来设计**。
之后的所有改进都是补丁：

| 时间 | 补丁 | 没解决什么 |
|---|---|---|
| 0.1.1 切换 | 发现「只改根 POM 静默产出旧版本」→ 把清单从 1 处补到 8 处 | 仍是多处、仍靠人记 |
| 同轮 | 补 `./mvnw versions:set` 一条命令 | 不覆盖 `AppVersion` 与文档声明 |
| 同轮 | 写教训进 `Troubleshooting` / `ReleaseProcess` | 记录教训，未改设计 |
| 本次 | 清单 8 处 → 10 处（新增两个原生模块后被漂移） | 漂移的是"清单"，不是"机制" |

**结论**：一直在优化「怎么少踩坑」，没有回到「版本号本该只有一处」这个根问题。

## 目标状态

**权威源 = 根目录 `version` 文件**（人只改这里，或在 workflow 里输入新版本号）：

```
version            # 一行纯文本，如 0.1.3
```

其余全部由 **VersionBump workflow** 分发（§H）；机器侧版本只保留根 POM 的 `<revision>` 一处：

```xml
<!-- pom.xml（由 workflow 写入，不需人改） -->
<properties>
  <revision>0.1.3</revision>
</properties>
```

> Maven **不能**直接读 `version` 文件（POM 模型阶段不支持外部文件注入，见 §H.4），
> 所以「`version` 文件 → 根 POM 的 `<revision>`」由 workflow / 脚本完成，
> 再由 §E 的一致性校验保证两者不漂移。

## 改造清单

### A. POM 层（9 个文件）

1. 根 `pom.xml`：`<version>${revision}</version>` + `<properties><revision>x.y.z</revision></properties>`；
2. 八个子模块：`<parent><version>${revision}</version></parent>`（`<relativePath>` 保留）；
3. 根 POM 引入 `flatten-maven-plugin`（版本固定在父 POM `<properties>`，交 Dependabot 跟踪）：
   - `flattenMode=resolveCiFriendliesOnly`、`updatePomFile=true`；
   - `flatten` 绑 `process-resources`、`flatten.clean` 绑 `clean`；
   - **⚠️ 必要性**：不 flatten 时 `install`/`deploy` 的 POM 里是字面量 `${revision}`，下游解析失败；
   - **与 Maven 4 的关系（重要）**：Maven 4 在 `install`/`deploy` 时会**自动生成 consumer POM**
     （CI-friendly 占位符已解析、build 段与 profiles 被剥离），其「解引用版本」的职责**已内置**，
     故 Maven 4 下 `flatten-maven-plugin` **不再必需**；但本仓库**同时保留 Maven 3.9.x 兼容基线**
     （`Compat.yml`），3.9.x 没有 consumer POM——**只要兼容线还在，flatten 就得留**。
4. `.gitignore` 加 `.flattened-pom.xml`。

### B. 版本读取收敛为单一入口（1 个脚本 + 4 处调用）

5. 新增 `.github/Python/ProjectVersion.py`：**唯一读取入口** —— 根目录 `version` 文件优先，缺失则回退
   根 POM 的 `<properties>/<revision>`（再回退老式的 `<version>`）；另提供 `--resolve` 与
   `--verify` 两个子命令（见 §H.1）；
6. 四个工作流的内联解析（各一行 `ET.parse("pom.xml")`）改为调用该脚本：
   `Build.yml:89`（打 tag）、`BuildJVMArtifacts.yml:89`、`CliNative.yml:91`、`DesktopNative.yml:106`；
   - **目的**：版本**源**将来怎么变（`<version>` ↔ `<revision>`），只改这一个脚本。

### C. Java 侧消除第 10 处手改

7. `AppVersion.FALLBACK_VERSION` 改为与版本无关的常量（如 `"dev"`），并更新其 Javadoc——
   它只在「IDE 直跑、资源未过滤」时出现，**不应随版本走**；
8. 同步 `AppVersionTest`（若有格式断言）与相关文档描述。

### D. 文档声明（4 处）

9. 方案二选一（建议 D1）：
   - **D1（推荐）**：`README.md` / `AGENTS.md` / `AHA-Design-V1.md` / `ReferenceGuide.md`
     不再写死具体数字，改为「版本号唯一来源：根 `pom.xml` 的 `<revision>`」+ 一行查询命令；
   - D2：保留数字，由 §E 的校验兜底（不一致即 CI 红）。

### E. 一致性校验（独立价值最高，可先行）

10. 新增校验：断言 **根 POM `<revision>` == `version.properties` 的 `version` == 产物文件名 == tag 名**；
    位置：`Build.yml`（或 `Gate.yml`）一个步骤；
    - **即使 A~D 不做，这一条也应当先做**——它把「静默错误」变成红灯（当前的真正短板）。

### F. 验证矩阵（改的是构建根基，必须逐项验）

| # | 验证点 | 判据 |
|---|---|---|
| F1 | Maven 4（wrapper `4.0.0-rc-7`）完整构建 | `Build` / `Gate` 绿 |
| F2 | Maven 3.9.x（`Compat.yml`）完整构建 | 绿 |
| F3 | 资源过滤 | `version.properties` 内 `version=x.y.z`（非 `${revision}`） |
| F4 | 产物名 | `aha-*-x.y.z.jar`、`aha-desktop-x.y.z-<平台>.zip` |
| F5 | install/deploy 的 POM 已解析 | 本地 `~/.m2` 里该 POM 的头 `<version>` 是实际值 |
| F6 | 四个工作流的版本读取 | tag 名 / 预发行版本号均为 `x.y.z` 或 `x.y.z.PPPPP` |
| F7 | 原生镜像线 | `DesktopNative` / `CliNative` 产物名正确 |
| F8 | IDE（IDEA）导入与运行 | 无 `${revision}` 未解析报错 |
| F9 | `versions:set` 语义 | 改用 `versions:set-property -Dproperty=revision`，并更新文档 |
| F10 | **Maven 4 下 flatten 与 consumer POM 是否冲突** | 无报错、无重复产物；`.m2` 里 POM 已解析为实际版本 |
| F11 | **Maven 4 GA 后移除 flatten 的可行性** | 移除后构建通过，且 `.m2` / 远端 POM 仍已解析 |
| F12 | **版本切换的验证路径可自证** | 方案已调整（**不配 PAT**）：PR 侧不跑 checks，验证由合并到 `dev` 后自动触发的 `Build` + `BuildJVMArtifacts` / `CliNative` / `DesktopNative` 承担（走 `push` 事件，不受 `GITHUB_TOKEN` 限制） |
| F13 | 分发脚本的幂等与防漏 | 同版本重跑被拒；动态扫描到的 POM 数 ≥ 9 |

### G. 文档与规范同步

11. `ReleaseProcess.md` §2：版本切换改为「改 `version` 文件一行 / 触发 `VersionBump` workflow」；§4.2 的「8 处」修正；
12. `VersionBumpGuide.md`：§2 改动清单由 14 处降为 **1 处**（`version` 文件）或「一键分发」，
    §4 检查清单相应简化（校验项保留）；
13. `BuildSpec.md` §7「版本单一来源」：来源改为「`version` 文件（权威）+ 根 POM `<revision>`（机器位置）」；
14. `AHA-Design-V1.md` 的 POM 示例与变更日志；
15. `CHANGELOG.md` 记录本次改造。

### H. 版本分发 workflow（具体技术方案）

参考已实现案例：[`ACANX/MetaOpen` 的 `UpdateProjectVersion.yml`](https://github.com/ACANX/MetaOpen/blob/dev/.github/workflows/UpdateProjectVersion.yml)。
其骨架为：**`version` 文件作权威源 → `workflow_dispatch` 触发 → 正则改 POM 的 `<properties>`
→ 写回 `version` → 开 PR**（配套脚本 `UpdatMeavenProperties.py` 只动 `<properties>` 块、保留注释与格式）。

AHA 采用同一骨架，但**有三处必须改造**（见 H.4）。

#### H.1 权威源与读取入口

1. 新增根目录 `version`：纯文本一行，如 `0.1.3`（**不带 `SNAPSHOT`**——AHA 的预发行用
   `a.b.c.PPPPP` 构建号表达，见 `ReleaseProcess.md` §2）；
2. 新增 `.github/Python/ProjectVersion.py` 作为**唯一读取入口**（§B）：
   - `--resolve [<版本>]`：输入优先，否则读 `version` 文件，再回退根 POM 的 `<revision>`；
   - `--verify`：一致性校验（§E）；
   - 裸调用：输出当前版本号（供脚本 / 本地使用）。
3. 四个工作流的内联 `ET.parse("pom.xml")`（`Build.yml:89` / `BuildJVMArtifacts.yml:89` /
   `CliNative.yml:91` / `DesktopNative.yml:106`）全部改为调用它。

#### H.2 workflow：`.github/workflows/VersionBump.yml`

```yaml
name: VersionBump
on:
  workflow_dispatch:
    inputs:
      version:
        description: '新版本号（如 0.1.3）；留空则取 version 文件当前值'
        required: false
permissions:
  contents: write
  pull-requests: write
concurrency:
  group: version-bump
  cancel-in-progress: true
jobs:
  bump:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '25' }
      - name: 解析并校验版本
        id: v
        run: python3 .github/Python/ProjectVersion.py --resolve "${{ inputs.version }}"
      - name: 分发（POM / version / 代码常量 / 文档）
        run: python3 Script/Python/VersionDistribute.py --version "${{ steps.v.outputs.version }}"
      - name: 自检
        run: python3 bin/CheckDocs.py && python3 .github/Python/ProjectVersion.py --verify
      - uses: peter-evans/create-pull-request@v6
        with:
          token: ${{ github.token }}                   # 默认 token；本仓库不配 PAT，见下
          branch: chore/bump-${{ steps.v.outputs.version }}
          base: dev
          title: "chore(release): 基线版本切换到 ${{ steps.v.outputs.version }}"
          commit-message: "chore(release): 基线版本切换到 ${{ steps.v.outputs.version }}"
```

两个要点：

- **`token` 用默认 `GITHUB_TOKEN`**（`github.token`）——**本仓库不配置 PAT**。代价是版本切换 PR 上
  **不会**自动跑 checks：`GITHUB_TOKEN` 推的提交不触发下游 workflow（本仓库已在
  `ReleaseProcess.md` §4.2 记过这个坑），且 `Gate` / `Compat` 的 `pull_request` 只覆盖
  `main` / `release/**`，base 为 `dev` 的 PR 本来也不会跑。
  **验证时机因此改为「合并到 `dev` 之后」**：`push` 事件会触发 `Build.yml`（`on: [push, pull_request]`）
  与 `BuildJVMArtifacts` / `CliNative` / `DesktopNative`（`push: branches: [dev]`），不受 token 限制；
  PR 侧的正确性由 workflow 自身的「自检」步骤（`CheckDocs` / `CheckScripts` / `ProjectVersion.py`）把关；
- `inputs.version` 可留空 → 取 `version` 文件当前值（幂等重放用）。

#### H.3 分发脚本：`Script/Python/VersionDistribute.py`

职责（**动态扫描，不硬编码清单**）：

1. 校验格式 `^\d+\.\d+\.\d+$`；值与当前相同则**拒绝**（非零退出，不产生空提交）；
2. **动态发现 POM**：根 `pom.xml` + `git ls-files '*/pom.xml'`（排除 `target/`）；
   断言总数 ≥ **9**（防漏，新增模块时自动覆盖）；
3. 改根 POM 的 `<properties><revision>`：**正则只动 `<properties>` 块**、保留注释与格式
   （照 MetaOpen 的 `UpdatMeavenProperties.py`）；子 POM 无需改（引用 `${revision}`）；
4. 写回根目录 `version` 文件；
5. `AppVersion.FALLBACK_VERSION`：若按 §C 解耦则**不动**；否则同步为 `"<版本>-dev"`；
6. 文档声明 4 处（§D）：若已改为不写死则**不动**；否则正则替换；
7. **收尾断言**：全仓搜旧版本号，除白名单（`@since`、`CHANGELOG` 历史段、
   `Troubleshooting` / `DevLog` 记录、构建号 `a.b.c.PPPPP`）外不得残留；
8. 打印改动清单（供 PR 描述用）。

> 脚本遵守项目规约：UTF-8 + LF（`bin/CheckScripts.py` 会校验）；
> Agent 不在本地跑 Maven，因此分发**用正则改 POM**，而不是调 `mvn versions:set`。

#### H.4 与 MetaOpen 的差异（为何不能照抄）

| 维度 | MetaOpen | AHA | AHA 的处置 |
|---|---|---|---|
| 子模块版本写法 | `<properties><revision>`，子模块继承 | **各子 POM 的 `<parent><version>` 硬编码** | 引入 `${revision}`（§A） |
| POM 清单 | **硬编码 4 个 DIRS** | 9 个 POM，且会继续加模块 | **动态扫描 + 数量断言**（H.3-2） |
| 代码里的版本字面量 | — | `AppVersion.FALLBACK_VERSION` | 解耦（§C）或纳入分发（H.3-5） |
| 文档版本声明 | — | 4 处 | 改为不写死（§D）或纳入分发（H.3-6） |
| 流水线读版本 | 无（不打 tag） | **4 个工作流读 POM** | 收敛到 `.github/Python/ProjectVersion.py`（§B） |
| Maven 兼容线 | 单版本 | Maven 4 + **3.9.x** | 需 `flatten-maven-plugin`（§A） |
| PR 触发下游 | 用默认 `GITHUB_TOKEN` | 有必需检查与 `Gate` | **不配 PAT**：PR 侧不跑 checks，验证归口到合并后的 `dev` 流水线（H.2） |
| 一致性校验 | 无 | 无 | 新增（§E）——“守门人” |

#### H.5 幂等与失败处置

- 重复触发同版本：H.3-1 直接拒绝，不产生空提交；
- 并发触发：`concurrency` 串行化（H.2）；
- 版本写错、PR 未合并：关掉 PR 重跑 workflow（分支名不同，互不影响）；
- workflow 失败：Job Summary 写明「人工按 `VersionBumpGuide.md` §3 手工分发」。

## 与 Maven 4 的分期决策

| 阶段 | 条件 | `flatten-maven-plugin` |
|---|---|---|
| **阶段一（现在）** | 双 Maven 并存：wrapper `4.0.0-rc-7` + `Compat.yml` 的 3.9.x | **保留**（3.9.x 无 consumer POM，不 flatten 会装出含占位符的 POM） |
| **阶段二** | Maven 4 **GA** 且 Maven 3.9.x 兼容线退役 | 可**移除**，改由 Maven 4 的 consumer POM 承担（比 flatten 更彻底：连 build 段都剥离） |

> **关键澄清**：无论 Maven 3 还是 4，**`${revision}` 都必须保留**——Maven 4 的 consumer POM
> 解引用的正是 `${revision}`；它不是「为 Maven 3 引入的历史包袱」。两件事不能一起**摘**。
>
> **不确定性说明**：Maven 4 目前仍是 rc（本仓库记录过 `apache-maven-4.0.0-bin.zip` 404、
> metadata 的 `release` 就是 `4.0.0-rc-7`）。rc 阶段行为可能微调，阶段二的结论应以 Maven 4 GA
> 的官方文档为准，并用 F10 / F11 实测后再执行。

## 落地顺序与依赖

1. ~~先合并 PR #89（0.1.1 → 0.1.2）~~ ✅ **已完成**（`6e329bd`，2026-10-09）
   ——两者同改 POM，故当时必须串行；
2. **P1：§E 一致性校验**（独立 PR，风险最低、收益最高）；
3. **P2：§B 读取收敛**（`.github/Python/ProjectVersion.py` 作为唯一入口）；
4. **P3：§A / §C**（`${revision}` + flatten + fallback 解耦）；
5. **P4：§H 分发器**（`version` 文件 + `VersionBump.yml` + `VersionDistribute.py`）
   ——**必须先有 P1~P3**，否则分发器仍要维护 9 处 POM 的清单；
6. **P5：§D / §G 文档**；
7. **每步都过 §F 的验证矩阵**（编译/测试由 CI 承担，本地只跑 `bin/Check*.py`）。

> 可先做的最小闭环：**P1 + P4 的只读部分**（`version` 文件 + `.github/Python/ProjectVersion.py --verify`）
> ——即使暂不动 POM 结构，也能把「版本号漂移」变成红灯。

## 验收标准

- [x] 切版本只需改 **1 行**（根目录 `version` 文件），其余由 workflow 分发，无人工改动；
- [x] 不存在写死的版本字面量（`AppVersion.FALLBACK_VERSION` 已与版本解耦）；
- [x] 版本读取只有 **1 处实现**（`.github/Python/ProjectVersion.py`），四个工作流统一调用；
- [x] 一致性校验上线：`version` 文件 / `<revision>` / `version.properties` / 产物名 / tag 名
      不一致即 CI 失败；
- [x] **VersionBump workflow** 可一键分发并开出 PR（分支名 `chore/bump-<版本>`），
      验证路径为「PR 侧自检 + 合并到 `dev` 后的自动流水线」（F12 方案已调整，见「落地结果」）；
- [x] 分发脚本**动态扫描 POM**（不硬编码清单），新增模块无需改脚本（F13）；
- [x] Maven 4 与 3.9.x 双版本构建通过；原生镜像线产物名正确；
- [x] `ReleaseProcess` / `VersionBumpGuide` / `BuildSpec` 口径更新为「1 处」（或「1 处 + 一键分发」）；
- [x] 切一次真实版本（0.1.1 → 0.1.2）验证全链路，并把结果回填本文件。

## 落地结果（回填，2026-10-09）

### 各阶段与 PR

| 阶段 | Issue | PR | 状态 |
|---|---|---|---|
| P1 一致性校验 | #93 | #98 | ✅ 已合并 |
| P2 读取收敛 | #94 | #99 | ✅ 已合并 |
| P3 `${revision}` + flatten | #95 | #100 | ✅ 已合并 |
| P4 分发器（`version` + `VersionBump` + 脚本） | #96 | #103（落地）/ #105（修断言范围） | ✅ 已合并 |
| P5 文档与规范同步 | #97 | 本次 PR | ✅ 本次交付 |

### §F 验证矩阵实测

| # | 验证点 | 结果 |
|---|---|---|
| F1 | Maven 4（wrapper）完整构建 | ✅ `Build` 绿（4 腿） |
| F2 | Maven 3.9.x（`Compat.yml`） | ✅ `Compat` 绿 |
| F3 | 资源过滤 | ✅ `version.properties` 得到实体版本（`Gate` 的 `--verify` 覆盖） |
| F4 | 产物名 | ✅ `0.1.2` 出包线产物名带版本 |
| F5 | install/deploy 的 POM 已解析 | ⚠️ 无自动验证（需本地 `.m2` 或远端仓库抽查） |
| F6 | 四个工作流的版本读取 | ✅ 统一走 `ProjectVersion.py --resolve` |
| F7 | 原生镜像线 | ✅ `DesktopNative` / `CliNative` 产物名正确 |
| F8 | IDE（IDEA）导入与运行 | ⚠️ 待人工确认（不阻塞） |
| F9 | `versions:set` 语义 | ✅ 文档改用 `versions:set-property -Dproperty=revision` |
| F10 | Maven 4 下 flatten 与 consumer POM | ✅ 无报错、无重复产物 |
| F11 | Maven 4 GA 后移除 flatten 的可行性 | ⏸ 阶段二，待 GA 后实测 |
| F12 | 版本切换的验证路径 | ✅ 方案调整（**不配 PAT**）：PR 侧不跑 checks；验证由合并到 `dev` 后自动触发的 `Build` + 三条出包线承担（`push` 事件，不受 token 限制） |
| F13 | 分发脚本幂等与防漏 | ✅ 同版本重跑被拒；动态扫描 9 个 POM（下限 9） |

### F12 方案变更（2026-10-09）

原方案要求「版本切换 PR 上 `Build` / `Gate` / `Compat` 的 checks 被触发」，隐含前提是配置
`secrets.VERSION_BUMP_TOKEN`（PAT）。**该方案未采纳**，改为：

- PR 侧只依赖 workflow 自身的「自检」步骤（`CheckDocs` / `CheckScripts` / `ProjectVersion.py`）；
- 验证归口到**合并到 `dev` 之后**由 `push` 事件自动触发的流水线：`Build` +
  `BuildJVMArtifacts` / `CliNative` / `DesktopNative`；
- **理由**：`push` 事件不受 `GITHUB_TOKEN` 防递归限制，无需任何额外凭据；而 `Gate` / `Compat`
  的 `pull_request` 只覆盖 `main` / `release/**`，即使配了 PAT 也不会在 base 为 `dev` 的 PR 上跑
  ——原口径本身无法满足。

同步更新：`ReleaseProcess.md` §2/§4.2、`VersionBumpGuide.md` §2.2/§5（第 6、10 条）、
`.github/workflows/VersionBump.yml`（删掉 PAT 检查步骤与 `VERSION_BUMP_TOKEN` 引用）、
`.github/workflows/Build.yml` 的注释。

### 真实切换验证

- **0.1.1 → 0.1.2**：由 `VersionBump` workflow 分发并开 PR（#106），CI 全绿后合并 ✓；
- 期间修掉一个真实缺陷：收尾断言曾**全仓搜旧版本号**，导致历史文档里的旧版本号
  成片误报（实测 60+ 处）——#105 改为「只动声明行 + 受控自证」；
- 演练中出现的版本回退（先切到 0.1.1 再升回 0.1.2）已记入 #104 / #106，属人工操作。

## 关联

- **参考实现**：[`ACANX/MetaOpen` 的 `UpdateProjectVersion.yml`](https://github.com/ACANX/MetaOpen/blob/dev/.github/workflows/UpdateProjectVersion.yml)
  与配套的 `.github/Python/UpdatMeavenProperties.py`（正则只改 `<properties>` 块）；
  MetaOpen 根目录也有一个 `version` 文件（内容如 `0.9.2-SNAPSHOT`）；
- `Docs/Guide/VersionBumpGuide.md`（操作）、`Docs/DevSpec/ReleaseProcess.md`（规范）
- `Docs/Troubleshooting/TS-202610-VersionBumpMissedModules.md`（当初的教训）
- 四个工作流的版本读取：`Build.yml` / `BuildJVMArtifacts.yml` / `CliNative.yml` / `DesktopNative.yml`
- `aha-common` 的 `AppVersion` 与 `version.properties`
