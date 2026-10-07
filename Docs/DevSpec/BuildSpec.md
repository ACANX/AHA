# 构建规范

**文档版本**：v1.10.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-07
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 由 `BuildGuide.md` 拆分而来，承接其中的强制性约束 | @ACANX |
| v1.1.0 | 2026-10-06 | 新增发行包产物约束（禁止 fat JAR / jlink） | @ACANX |
| v1.2.0 | 2026-10-06 | 新增跨平台脚本编码与行尾约束 | @ACANX |
| v1.3.0 | 2026-10-07 | 新增依赖自动升级约束（Dependabot 每日检测 Maven 与 GitHub Actions，PR 先合入 `dependa` 分支） | @ACANX |
| v1.4.0 | 2026-10-07 | 第 8 节新增「按变更范围选择验证项」矩阵与完整 verify 的触发条件 | @ACANX |
| v1.5.0 | 2026-10-07 | §7 新增「版本单一来源」规则：确切版本以父 POM `<properties>` 为准，文档不得复制具体版本号 | @ACANX |
| v1.6.0 | 2026-10-07 | 「版本单一来源」补例外条款：实测记录（性能数据、覆盖率口径）必须写明确切版本，与选型声明区分 | @ACANX |
| v1.7.0 | 2026-10-07 | §4 新增 4.1「平台矩阵」：三条必需腿 + `macos-latest` 可选（演示）腿，以 `continue-on-error` 标注，不参与必需检查也不代表支持承诺 | @ACANX |
| v1.8.0 | 2026-10-07 | 新增 8.1「检查分层与门禁时机」：快检查（`Build.yml`）与门禁（`Gate.yml`，含 verify / 覆盖率 / 文档 / 重复率）分离；补重复率检查的口径、阈值与实测值；修正「CI 始终跑完整 verify（Build.yml）」的失效说法 | @ACANX |
| v1.9.0 | 2026-10-07 | §3 更正 wrapper 配置片段（补 `wrapperVersion` / `distributionType`，并说明以文件为准）；§4 明确两条流水线分别由 `Gate.yml` 与 `Compat.yml` 承担、兼容基线固定补丁版本且不使用 runner 预装 `mvn`、新增每周定期扫描；§8.1 分层表补「兼容性」层并把 `Compat` 一并列为必需检查 | @ACANX |
| v1.10.0 | 2026-10-07 | §8.1 新增「作业名是分支保护的契约」：必需腿不得增删矩阵键（附真实事故：`optional` 键使作业名多出 `, false`，必需检查永久停在 Expected）；`Gate` / `Compat` 的 `name:` 同样属契约 | @ACANX |

---

## 1. 适用范围

本文件规定 AHA 构建的**强制性要求**，属于开发规范（`Docs/DevSpec/`）。

**操作步骤**（命令、报告位置、排错）见 [BuildGuide.md](../Guide/BuildGuide.md)。

> 划分原则：**规范进 DevSpec，操作进 Guide**。
> 本文件回答"必须满足什么"，BuildGuide 回答"怎么做"。

## 2. 工具链锁定

| 类别 | 锁定版本 | 说明 |
|---|---|---|
| JDK | **25 (LTS)** | 编译与运行目标，禁止降级 |
| Maven 运行时 | **4.x** | 仅作为构建运行时，通过 Maven Wrapper 固定 |
| Maven 兼容基线 | **3.9.x** | 所有 POM 修改必须通过 Maven 3.9.x 验证 |
| JPMS | **强制启用** | 所有模块必须有 `module-info.java` |

**禁止事项**：

- 禁止在编译目标上使用低于 25 的 `release` 值
- 禁止在非模块化配置下构建主代码
- 禁止跳过双版本验证直接合入

## 3. Maven Wrapper 固定

Wrapper 配置位于 `.mvn/wrapper/maven-wrapper.properties`，`distributionUrl` **必须锁定到具体版本**：

```properties
wrapperVersion=3.3.4
distributionType=bin
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/4.0.0-rc-7/apache-maven-4.0.0-rc-7-bin.zip
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4.jar
```

> 上例中的补丁版本仅供参考，**以 `.mvn/wrapper/maven-wrapper.properties` 为准**——
> 该文件由 Dependabot 定期升级，抄进文档就会漂移。
> `Gate.yml` 的第一步会从该文件反推期望版本，再与 `./mvnw -v` 的实际输出比对，
> 因此「门禁究竟跑的是哪一版 Maven」不靠推断，而是每次构建都当场断言。

**禁止事项**：禁止使用 `LATEST` / `RELEASE` 等浮动版本标识。

## 4. 双版本验证（强制）

CI 必须同时执行两条流水线，且结果一致：

| 流水线 | 命令 | 用途 | 由谁执行 |
|---|---|---|---|
| Maven 4（Wrapper 固定版本） | `./mvnw clean verify` | 目标运行时 | `Gate.yml` |
| Maven 3.9.x（CI 显式固定的补丁版本） | `mvn clean verify` | 兼容基线 | `Compat.yml` |

任一条失败即视为**构建失败**，PR 不得合入。两条都只在**卡点时机**执行
（合入 `main` / `release/**` 前、手动触发、发布前），不跟每次 `push` 一起跑；
每次改动的快速反馈由 `Build.yml`（编译 + 单元测试）承担。

**为什么拆成两个工作流**：目标运行时与兼容基线的失败原因不同——前者是 Maven 4
的运行时行为，后者是 POM 语法是否越界。混在一个矩阵里，一次失败要看半天才知道
是哪一版的问题；拆开后失败直接指向原因。

兼容基线**不使用 runner 预装的 `mvn`**：镜像会变，预装版本也跟着变，
「兼容性结论」就不可复现。`Compat.yml` 从 Maven Central 取固定的补丁版本，
并在跑验证前断言 `mvn -v` 确为 3.9.x。

**定期扫描**：`Gate.yml` 另带 `schedule`（每周一次）。这类检查（覆盖率、文档、
技能、脚本、重复率）适合**异步**执行——它们要拦的是「与开发动作无关的漂移」，
例如 Dependabot 升级依赖、runner 镜像变化、外部规范演进，不必也不该在每次
写完一个特性后就触发。

### 4.1 平台矩阵

| 腿 | 命令 | 性质 |
|---|---|---|
| `windows-latest`（wrapper） | `./mvnw clean verify` | **必需** |
| `ubuntu-latest`（wrapper） | `./mvnw clean verify` | **必需** |
| `ubuntu-latest`（system） | `mvn clean verify` | **必需**（Maven 3.9.x 兼容基线） |
| `macos-latest`（wrapper） | `./mvnw clean verify` | **可选（演示）** |

**macOS 腿是「演示与可选」，不是支持承诺**。它在 `Build.yml` 中以
`continue-on-error`（由矩阵的 `optional` 标记驱动）标注：不参与必需检查，
失败只体现在该腿自身，**不使整体构建失败**。第 4 节的「任一条失败即视为构建失败」
只约束上面三条必需腿。

这样安排的用意：提前暴露明显的跨平台退化（例如路径分隔符、大小写敏感的文件系统、
shell 语义），并留一个现成的落点，将来真要支持 macOS 时不必从零搭。同时明确边界——

- **不能用它的通过宣称已支持 macOS**；
- **不能用它的失败判定构建失败**，更不要为它临时放宽门禁。

`fail-fast` 必须为 `false`：一条腿失败就取消其余腿，会掩盖「另一个平台究竟是什么结果」
（见 `TestingSpec.md` §5.1）。

## 5. POM 语法兼容约束

POM 语法必须兼容 Maven 3.9.x：

- 禁止 `modelVersion 4.1.0`
- 禁止自动模块发现与 parent 版本推断
- 禁止使用仅 Maven 4 支持的语法

若 Maven 4 在 JPMS 多模块场景下出现阻塞性问题，可临时回退到 Maven 3.9.x 运行时，但 **POM 不得依赖 Maven 4 专有特性**。

## 6. 覆盖率门禁（强制）

| 项 | 要求 |
|---|---|
| 扩展 | `jacoco-maven-plugin`（版本见父 POM 属性 `jacoco.version`） |
| 绑定 | `prepare-agent` / `report` / `check`，均于 `verify` 阶段 |
| 门禁规则 | `BUNDLE` 级 `LINE COVEREDRATIO ≥ 0.70` |
| 未达标后果 | `verify` 失败，PR 不得合入 |
| 排除项 | 必须登记理由（见设计文档第十二部分 3.3） |

模块级参考阈值见 [TestingSpec.md](TestingSpec.md) 第 3 节。

## 7. 依赖与产物约束

**版本单一来源**：依赖的确切版本集中在**父 POM 的 `<properties>`**，是唯一来源。
文档（含 README、设计文档、选型清单）**不得复制具体版本号**，只写主版本线——
升级由 Dependabot 每日提出，抄进文档必然漂移（曾因此出现「文档写 3.1.3、POM 已是 3.2.3」）。
需要确切版本时看 `pom.xml`。

**例外：实测记录必须写明版本**。性能数据、覆盖率口径这类**证据**要写清「用什么版本测的」
（如「用 `sqlite-jdbc 3.53.2.0` 量得…」「JaCoCo 0.8.15 口径」）——那是复现条件，不是选型声明，
即便日后版本升级也不删改。判据：**选型声明写主版本线，证据写确切版本**。

- 内部模块版本统一由父 POM `dependencyManagement` 管理，子模块不得硬编码版本
- `aha-common` 保持**零外部依赖**（仅 JDK），禁止引入 Jackson 等第三方库
- 内核模块（`aha-core`）禁止依赖任何传输 / 协议库
- 发行包固定为 **JPMS 模块路径目录**（`dist/bin` + `dist/lib`），由 `maven-assembly-plugin`
  组装（`aha-cli/src/assembly/dist.xml`）：
  - **禁止**使用 `maven-shade-plugin` 打 fat JAR——合并产物无 `module-info`，破坏 JPMS 强制启用原则
  - **禁止**在 0.1 使用 `jlink`——`sqlite-jdbc` 为自动模块，jlink 不支持
  - `bin/Aha.sh` / `bin/Aha.bat` 必须与 `dist/` 布局保持一致（`$DIR/../lib`）
  - `dist/` 不入库，须在 `.gitignore` 中保持忽略
- 跨平台脚本编码与行尾约束（由 `bin/CheckScripts.py` 在 CI 中校验）：
  - `*.bat` / `*.cmd`：**纯 ASCII + CRLF + 无 BOM**——CMD 按 ANSI 代码页解析批处理，
    非 ASCII 字节会产生 `&`、`|` 等元字符并导致注释 / echo 行被当作命令执行
  - `*.sh` / `*.py`：**LF**——CRLF 会让 shebang 失效
- 依赖升级由 `.github/dependabot.yml` 自动检测与提交（**每日一次**）：
  - 覆盖 **Maven 依赖**（`pom.xml`）与 **GitHub Actions 组件**（`.github/workflows/*.yml` 中 `uses:` 引用的版本）
  - **合入路径**：Dependabot PR 一律先合入 **`dependa` 分支**（`target-branch`），
    **不得直接指向默认分支**；`dependa` 稳定后再由 `dependa` → 默认分支单独提 PR
  - `minor` / `patch` 按生态合并为单个 PR；`major` 逐个单独提交，必须人工评估
  - Dependabot PR 与其他 PR 同等对待，须通过第 4 节双版本验证与第 6 节覆盖率门禁
  - 升级若触及第 2 节工具链锁定或第 5 节 POM 兼容约束，按第 9 节规范变更程序处理
  - 被 Maven Wrapper 固定的 Maven 版本不在自动升级范围内，见第 3 节
  - `dependa` 分支必须长期保留（Dependabot 读取的是该分支上的清单文件），不得随版本发布删除

## 8. 验收标准

**`mvn clean verify` 通过是唯一验收标准**，其余检查（格式化、静态分析等）均为其前置补充。

**按变更范围选择验证项（强制）**：全套检查在慢文件系统上可达分钟级，不得无条件重跑。
可用 `bin/CheckChanged.py` 自动判定。

| 变更 | 必须执行 | 可跳过 |
|---|---|---|
| 仅文档（`*.md`） | `bin/CheckDocs.py` | 构建、其他检查 |
| 仅技能（`.agents/skills/`） | `bin/CheckSkills.py`；技能内 Markdown 另需 `CheckDocs.py` | 构建 |
| 仅脚本（`*.bat`/`*.cmd`/`*.sh`/`*.py`、`.gitattributes`） | `bin/CheckScripts.py` | 构建 |
| 像素网格常量（`StartupPixelLogo.java` 与 `bin/GenPixelLogo.py`） | `bin/GenPixelLogo.py --verify` | 构建 |
| 实现代码（Java / POM / YAML） | `./mvnw -pl <模块> -am test -Djacoco.skip=true` | 覆盖率门禁、文档检查 |
| 重复率相关（父 POM 的 PMD 配置、`bin/CheckDuplication.py`） | `./mvnw -B pmd:cpd && bin/CheckDuplication.py` | 构建 |

**必须跑完整 `./mvnw clean verify` 的情形**：

- 改动触及构建定义：`pom.xml`、`module-info.java`、`aha-cli/src/assembly/dist.xml`、`.github/workflows/`
- 改动可能影响覆盖率口径：JaCoCo 排除项、模块结构、包名
- 需要刷新文档中的实测覆盖率 / 用例数
- 发布前验收（见 [ReleaseProcess.md](ReleaseProcess.md)）

> 本节规定的是**开发过程中的最小验证**，用于避免每次改动都付分钟级代价。
> 它**不能替代**合入前的门禁：合入 `main` 与发布前一律跑完整检查（见 8.1）。

### 8.1 检查分层与门禁时机

检查按**耗时**分两层，各自绑定不同的触发时机。这不是降低标准，而是把慢检查放到
它真正起作用的位置——合入前与发布前：

| 层 | 工作流 | 触发 | 内容 |
|---|---|---|---|
| **快检查** | `Build.yml` | 每次 `push` / `pull_request` | 编译 + 单元测试（`clean test -Djacoco.skip=true`）；矩阵含 Windows 与 Linux（wrapper 与 system），外加一条**可选**的 macOS 腿 |
| **门禁** | `Gate.yml` | `pull_request` → `main` / `release/**`、**每周定期**、手动触发、发布前（`workflow_call`） | 先断言 Maven 版本与 Wrapper 配置一致，再跑完整 `./mvnw clean verify`（含覆盖率门禁 ≥ 70%）、文档检查、技能检查、脚本检查、像素标志一致性、重复率检查 |
| **兼容性** | `Compat.yml` | 与门禁相同（不含定期） | 固定补丁版本的 Maven 3.9.x 跑完整 `mvn clean verify` |

**合入 `main` 的前置条件**：仓库分支保护规则必须把 `Gate` 与 `Compat` 都设为**必需检查**
（这一项在 GitHub 仓库设置里配置，工作流文件里写不了）；否则它只是「跑给人看」，
起不到卡点作用。`Release.yml` 以 `workflow_call` 复用同一道门禁，因此
「发布前」与「合入前」是同一套标准，不存在两套口径。

**为什么分开**：完整 `verify`（覆盖率采集 + 打包 + javadoc）在本项目约需数分钟，
再叠加文档与重复率检查，每次改动都要付这个代价，反馈环路过长、频繁阻塞开发。
慢检查的价值在「拦住不合格的合入」，不在「每次改动都跑一遍」。快速反馈由
`Build.yml` 的编译 + 单元测试承担，两者互补。

**重复率检查（0.1.0 起）**：

| 项 | 规定 |
|---|---|
| 工具 | PMD CPD，`./mvnw pmd:cpd`；版本由父 POM 的 `pmd.plugin.version` 固定 |
| 最小 token 数 | 100（父 POM 的 `<minimumTokens>`，短于此时不计为重复） |
| 判定 | `bin/CheckDuplication.py`，默认阈值 **2.0%** |
| 统计口径 | 重复行数 = Σ 每个 duplication 块 `(出现次数 − 1) × 块行数`；总行数 = 各模块 `src/main/java` 下 `*.java` 的物理行数；重复率 = 两者之比。只统计主源码（CPD 的 `includeTests=false`） |
| 实测（0.1.0） | 合计 **0.40%**（74 / 18633 行），最高模块 `aha-core` 1.05% |

报告缺失时 `CheckDuplication.py` **直接失败**，不做静默跳过——否则 CI 上「没跑」
会被误读成「通过」。

**作业名是分支保护的契约（强制）**：GitHub 的必需检查按**作业名**匹配，而矩阵作业的名字
会把矩阵的**全部键值**拼进去（`<作业名> (值1, 值2, …)`）。因此：

- **必需腿的矩阵键不得增删**。曾经的一个真实事故：给每条腿补一个 `optional` 字段以求
  「语义显式」，作业名随即从 `build (windows-latest, wrapper)` 变成
  `build (windows-latest, wrapper, false)`，分支保护再也匹配不上，PR 页面上三条必需检查
  永久停在 `Expected — Waiting for status to be reported`——看起来像 CI 卡住，实际是
  名字对不上（见 `TODO.md` `F-08`）。
- **可选腿的开关从已有键推导**（如按 `matrix.os` 判定），不新增专用键。
- **`Gate.yml` / `Compat.yml` 的 `name:` 同样是契约**：job 级 `name:` 就是上报的检查名，
  改动它等于改必需检查的名字。
- 确需改名时，同一变更内必须同步更新分支保护，并在 `TODO.md` 的 `G-02` 中刷新验收清单。

## 9. 规范变更程序

修改本文件需：

1. 提出修订理由与影响范围
2. 至少一名核心维护者批准
3. 更新版本号与变更日志
4. 同 PR 内同步更新 [BuildGuide.md](../Guide/BuildGuide.md)（若操作方式随之变化）
