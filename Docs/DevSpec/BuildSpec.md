# 构建规范

**文档版本**：v1.27.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-09
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
| v1.11.0 | 2026-10-07 | §8.1 新增「门禁必须自证（强制）」：静默的检查要把实测值与标准写进日志，覆盖率由 `bin/ReportCoverage.py` 输出且阈值读自 pom；门禁内容补「并打印覆盖率实测值」 | @ACANX |
| v1.12.0 | 2026-10-07 | §8.1 新增「CI 必须容忍仓库侧瞬时失败（强制）」：所有 Maven 调用走 `.github/actions/maven-run`（清失败标记 + 重试），并给出两条 Maven「找不到」消息的判别与处理表；§8 的改动范围补 `.github/actions/` | @ACANX |
| v1.13.0 | 2026-10-07 | §8.1 新增「分支规则集必须对『单人 + 机器』可满足（强制）」：审批数按协作者数量设置、要求的结果必须有人生产，附两种把 PR 永久锁死的失效模式与 `G-02` 的回写约定 | @ACANX |
| v1.14.0 | 2026-10-07 | §8.1「分支规则集必须对『单人 + 机器』可满足」补第三条要点：要求的结果不仅要有人生产，还必须**覆盖规则所在的目标分支**（默认设置只扫默认分支会永久等待），并说明本仓库改用工作流方式显式声明分支范围 | @ACANX |
| v1.15.0 | 2026-10-07 | §8.1 补第四条要点「同一种扫描只能配一次」：GitHub 代码扫描的默认设置与高级设置互斥，同时存在会使工作流结果被拒收并表现为「检查一直等待」（附 2026-10-07 实测），取舍以能否覆盖目标分支为准 | @ACANX |
| v1.16.0 | 2026-10-07 | 新增「main 上的构建成功后自动打 tag」小节：`Build.yml` 的 tag 作业、版本号唯一来源为根 POM、幂等语义、`contents: write` 权限前提，以及 `GITHUB_TOKEN` 不触发下游工作流这一事实 | @ACANX |
| v1.17.0 | 2026-10-08 | 自动打 tag 小节的版本标注更正为「0.1.1 起」（该作业实际在 0.1.0 发布之后才合入） | @ACANX |
| v1.18.0 | 2026-10-08 | §7 技术栈 JPMS 改为「优先启用（非强制）」并补 classpath 例外的记录要求；fat JAR 禁用理由改述（不再依赖 JPMS 强制）；§4.1 补 macOS 打包决策（要打包、不加测试，`D-06`） | @ACANX |
| v1.19.0 | 2026-10-08 | 第 4.1 节补「JavaFX 平台分类器」规则：profile 设 `javafx.platform`、激活条件两条硬规则、空壳自动模块的排除要求、未覆盖平台的失败方式与应急覆盖开关 | @ACANX |
| v1.20.0 | 2026-10-08 | §4.1 补「发布时的平台出包」：矩阵在各平台 runner 上出包、产物命名、`expected` 双向自证、新增平台的方式 | @ACANX |
| v1.21.0 | 2026-10-09 | 新增独立工作流 `BuildJVMArtifacts.yml`（dev 的 JVM 构建线，与 `DesktopNative.yml` / `CliNative.yml` 同构）：`push` 到 `dev` 时构建 `aha-desktop` 与 `aha-cli` 便携包并发布预发行版（`build-mvn-artifact` / `build-publish`），补齐 JVM 模式在 dev 上的产物缺口（issue #63）；§8.1 分层表登记该层 | @ACANX |
| v1.22.0 | 2026-10-09 | §2 补「JDK 27 编译变体」（`-Dmaven.compiler.release=27`，仅 `BuildJVMArtifacts.yml` 的 jdk27 腿，包名带 `-jdk27`，issue #65）：不改变 JDK 25 基线与正式发版；§8.1 分层表同步补 JDK 轴 | @ACANX |
| v1.23.0 | 2026-10-09 | §8.1 的「必需检查与审批要求」登记处由 `TODO.md` `G-02` 改为 GitHub Issue（#85）：`TODO.md` 已冻结，待办统一走 Issue | @ACANX / CNXNC |
| v1.24.0 | 2026-10-09 | §7 新增「项目版本的单一来源」：权威源为根目录 `version` 文件，机器位置为根 POM 的 `<revision>`（由 `VersionDistribute.py` 写入），子模块写 `${revision}` 继承；一致性由 `ProjectVersion.py --verify` 在 `Gate.yml` 强制（P5 / #97） | @ACANX / CNXNC |
| v1.25.0 | 2026-10-09 | §4.1 与 §7 的「分平台构建」依据由 `jpackage` 改为 **JavaFX 平台分类器**——发行形态已决策只保留「JVM JAR 聚合包」与「原生镜像二进制」两种，`jpackage` 跳过不采用（见 `ReleaseProcess.md` §3.3） | @ACANX / CNXNC |
| v1.26.0 | 2026-10-09 | 新增第 10 节「原生镜像产物（试验性）」：把 E-02 ~ E-09 的可达性结论固化为规范、明确 E-10 的验收边界（门禁不覆盖 native、独立冒烟测试尚未定义）；§7 新增「发行形态矩阵」（E-11）；§6 注明门禁不覆盖 native 产物（issue #80 / TD-00009） | @ACANX / CNXNC |
| v1.27.0 | 2026-10-09 | §10.3 由「验收边界」扩为「验收边界与冒烟」：E-10 落地为**三层**（产物自证 → CLI 运行冒烟硬断言 → Windows GUI 存活冒烟非阻塞），并写明「GUI 冒烟只在 Windows、不阻塞」的理由（CI 无 GPU、覆盖不到 D3D；真实渲染与交互验证留在本地 / 真机） | @ACANX / CNXNC |

---

## 1. 适用范围

本文件规定 AHA 构建的**强制性要求**，属于开发规范（`Docs/DevSpec/`）。

**操作步骤**（命令、报告位置、排错）见 [BuildGuide.md](../Guide/BuildGuide.md)。

> 划分原则：**规范进 DevSpec，操作进 Guide**。
> 本文件回答"必须满足什么"，BuildGuide 回答"怎么做"。

## 2. 工具链锁定

| 类别 | 锁定版本 | 说明 |
|---|---|---|
| JDK | **25 (LTS)** | 默认编译与运行目标（`release=25`），禁止降级；另有 JDK 27 编译变体（见下） |
| Maven 运行时 | **4.x** | 仅作为构建运行时，通过 Maven Wrapper 固定 |
| Maven 兼容基线 | **3.9.x** | 所有 POM 修改必须通过 Maven 3.9.x 验证 |
| JPMS | **优先启用（非强制）** | 默认写 `module-info.java` 并走模块路径；与 OpenJFX 等需求冲突时可为它让路（`C-01` 决策，2026-10-08） |

**JDK 27 编译变体（额外产物，非基线）**：dev 出包线（`BuildJVMArtifacts.yml`）在 JDK 25 之外
额外用 **JDK 27** 编译一份产物（`-Dmaven.compiler.release=27`，包名带 `-jdk27` 后缀，issue #65），
用于提前验证 JDK 27 下能否编译与运行。它**不改变上面的 JDK 25 基线**：默认构建仍是 `release=25`，
正式发版（`Release.yml`）仍只用 JDK 25；只有该工作流的 jdk27 腿显式覆盖 release。

**禁止事项**：

- 禁止在编译目标上使用低于 25 的 `release` 值
- 非必要不使用 classpath 构建主代码：模块化是默认路径，仅在 JPMS 与 OpenJFX 等需求冲突
  且无法调和时例外，且必须在本文档记明原因与影响面
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

**0.2 起（`D-06` 决策，2026-10-08）**：CI **要能产出 macOS 平台的包**（JavaFX 原生库按平台分类器
发布，桌面端产物必须**分平台构建**），但**不在 macOS 上加测试**——支持承诺仍只有 **Windows + Linux**
双平台，macOS 只到「能打包」为止。

**JavaFX 平台分类器（0.2 桌面端）**：JavaFX 的原生库按平台拆成分类器工件，由父 POM 的
`javafx-*` profile 按当前 OS / 架构设定 `javafx.platform` 属性，模块只引用属性、不写平台字面量。
激活条件的两条硬规则——Linux 用 `<name>Linux</name>`（**不用** `<family>unix</family>`）、
架构用 `<arch>!aarch64</arch>`（**不写** `x86_64`，Maven 的 `os.arch` 是 `amd64`）——以及
「必须显式声明三个工件并排掉 0 KB 空壳 jar（否则 module path 上出现自动模块，
`jlink` 直接失败）」，详见 [DesktopDesign.md](../Design/DesktopDesign.md) 第 5 节。
未被 profile 覆盖的平台会以 `javafx-*-25-unsupported.jar` 明确失败，可用 `-Djavafx.platform=`
应急覆盖。

**发布时的平台出包（0.2 起）**：`Release.yml` 的 `desktop` 作业按矩阵在**各平台自己的 runner** 上跑
`./mvnw clean verify`，产出本平台便携包 `aha-desktop-<版本>-<系统>-<架构>.zip`，并挂到 release 页面。
矩阵每条腿声明 `expected`（本腿应当解析出的 `javafx.platform`），构建后由**产物名 + 依赖树**双向自证，
不一致即失败——runner 架构变更时不至于把错平台的包发出去。新增平台 = 加一条矩阵腿（先确认
Central 有对应分类器）。命名与包布局见 [ReleaseProcess.md](ReleaseProcess.md) §3.2。

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

> **门禁只覆盖 JVM 字节码，不覆盖 native 产物**——`native-image` 的产物没有 JaCoCo 插桩，
> 其质量保证另见第 10.3 节。

## 7. 依赖与产物约束

**项目版本的单一来源**：项目自身的版本号以**根目录 `version` 文件**为唯一权威源
（一行纯文本，如 `0.1.2`）；机器侧只保留根 POM 的 `<properties>/<revision>` 一处，
由 `Script/Python/VersionDistribute.py` 写入（规范见 `ReleaseProcess.md` §2，操作见
`VersionBumpGuide.md`）。8 个子模块写 `<parent><version>${revision}</version>` 继承，
`AppVersion.FALLBACK_VERSION` 与版本解耦（固定 `"dev"`）。五处一致性
（`version` 文件 / `<revision>` / `version.properties` / 产物名 / tag 规则）由
`python3 .github/Python/ProjectVersion.py --verify` 在 `Gate.yml` 强制校验。
文档**不得**复制项目版本号（同下「文档不得复制具体版本号」规则）——需要确切版本时看
`version` 文件或根 POM 的 `<revision>`；历史注记（`@since`、实测数据标题、`CHANGELOG`
历史段）不受此限。

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
- 发行包固定为 **JPMS 模块路径目录**（`Dist/bin` + `Dist/lib`），由 `maven-assembly-plugin`
  组装（`aha-cli/src/assembly/dist.xml`）：
  - **禁止**使用 `maven-shade-plugin` 打 fat JAR——合并后的单一 JAR 无法按模块追踪依赖与许可，
  也无法与 `Dist/{bin,lib}` 布局及 `bin/Aha.{sh,bat}` 保持一致
  - **禁止**在 0.1 使用 `jlink`——`sqlite-jdbc` 为自动模块，jlink 不支持
  - `bin/Aha.sh` / `bin/Aha.bat` 必须与 `Dist/` 布局保持一致（`$DIR/../lib`）
  - `Dist/` 不入库，须在 `.gitignore` 中保持忽略
- **构建产物一律输出到 `Dist/`（仓库根只放源码与文档）**：
  - 桌面端便携包、原生镜像包、CLI 发行 zip 全部落 `Dist/`，**不得**在仓库根
    生成或暂存任何 `.zip`（根目录被构建物污染后，`git status` 与「找产物」都变得不可靠）
  - 各模块的 `maven-assembly-plugin` 用 `<outputDirectory>${maven.multiModuleProjectDirectory}/Dist</outputDirectory>`
  - **例外**：CLI 的 assembly 输出的是 `Dist/` 内的**解包目录**（`bin/` + `lib/`），
    因此它的 `outputDirectory` 保持仓库根，由描述符自己铺出 `Dist/…`；
    CLI 的 zip 由工作流在临时目录打好后移入 `Dist/`（直接在 `Dist/` 内写会把归档自身收进去）
- **可选工作流的作业不得进必需检查（强制）**：
  试验性 / 非交付物管线（如 `DesktopNative.yml`、`CliNative.yml`）的作业名**不得**写进分支保护的必需检查，
  且其失败必须**在步骤级**容错（作业级 `continue-on-error` 只保住整次运行的颜色，
  作业本身仍显示红叉，观感上会被误读成「流程挂了」）。
  这类管线的正确形态是：步骤级容错 + Job Summary 留真相 + 无产物时不发版也不失败。
- **工具链 / 平台相关的东西一律进专用 profile（强制）**：
  - 默认构建（不带 `-P…`）的验收标准**只有一条**：**能编译、能打 jar、不报错**；
    程序能不能跑起来不属于默认构建的职责
  - 需要额外工具链（如 GraalVM `native-image`）或平台原生依赖（如某平台的 GUI 原生库）
    而产出的东西，必须**只在专用 profile 里**构建：
    模块本身也在 profile 的 `<modules>` 里，默认反应堆里根本没有它
  - 同时对内层开关设「安全默认值」：例如 `aha-desktop-native` 的 `native.skip` 默认为 `true`，
    由 profile 激活时置为 `false`。这样「模块误入反应堆」与「真的去编原生镜像」是两件事，
    误入也不会要求环境具备工具链
  - 理由：一旦默认路径依赖平台工具链，构建失败的原因会从「代码问题」变成「环境问题」，
    而后者极难在别人的机器上复现——这是把一个可诊断的失败换成不可诊断的失败
  - 已有实现：`aha-desktop-native`（桌面端原生镜像，`-Pdesktop-native[,native-jdk27]`）；
    详见 `Docs/Design/DesktopNativeDesign.md` 第 2 节；以及 `aha-cli-native`
    （CLI 原生镜像，`-Pcli-native[,native-jdk27]`）；详见 `Docs/Design/CliNativeDesign.md` 第 2 节
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

**发行形态矩阵（E-11，2026-10-09 决策）**：项目只发行两种形态，与平台轴正交（口径见
[ReleaseProcess.md](ReleaseProcess.md) §3.3，native 侧的规范见第 10 节）：

| 形态 | CLI | 桌面端 | 前提 |
|---|---|---|---|
| **JVM JAR 聚合包** | `aha-cli-<版本>.zip` | `aha-desktop-<版本>-<系统>-<架构>.zip` | 需自备 JDK 25 |
| **原生镜像二进制**（试验性） | `aha-cli-native` 的 `native-image` 单文件 | `aha-desktop-native` 的 `native-image` 单文件 | 无需 JVM |

> `jpackage` 安装包**已决策跳过、不采用**：它是第三种发行形态，而「免装 JDK」这一目标
> 已由原生镜像二进制覆盖（理由见 `ReleaseProcess.md` §3.3）。

## 8. 验收标准

**`mvn clean verify` 通过是唯一验收标准——由 CI 判定**（`Gate.yml` / `Compat.yml`），
其余检查（格式化、静态分析等）均为其前置补充。**本机不跑它**，见 8.1。

**按变更范围选择验证项（强制）**：全套检查在慢文件系统上可达分钟级，不得无条件重跑。
可用 `bin/CheckChanged.py` 自动判定。

| 变更 | 必须执行 | 可跳过 |
|---|---|---|
| 仅文档（`*.md`） | `bin/CheckDocs.py` | 构建、其他检查 |
| 仅技能（`.agents/skills/`） | `bin/CheckSkills.py`；技能内 Markdown 另需 `CheckDocs.py` | 构建 |
| 仅脚本（`*.bat`/`*.cmd`/`*.sh`/`*.py`、`.gitattributes`） | `bin/CheckScripts.py` | 构建 |
| 像素网格常量（`StartupPixelLogo.java` 与 `bin/GenPixelLogo.py`） | `bin/GenPixelLogo.py --verify` | 构建 |
| 实现代码（Java / POM / YAML） | `bin/Check*.py`（本地）；**编译 + 单元测试由 CI 的 `Build.yml` 判定** | 覆盖率门禁、文档检查 |
| 重复率相关（父 POM 的 PMD 配置、`bin/CheckDuplication.py`） | **由 CI 的 `Gate.yml` 判定** | 构建 |

**本地不跑完整 `verify`（强制）**：完整 `verify`（覆盖率采集 + 打包 + javadoc + 覆盖率门禁）
**只在 CI 跑**。即使改动触及下列内容，也一样**推送后看 CI**，不要在本机补跑：

- 构建定义：`pom.xml`、`module-info.java`、`aha-cli/src/assembly/dist.xml`、`.github/workflows/`、`.github/actions/`
- 可能影响覆盖率口径：JaCoCo 排除项、模块结构、包名
- 需要刷新文档中的实测覆盖率 / 用例数（数字从 CI 的 `Gate` 日志或
  `python3 bin/ReportCoverage.py` 在 CI 的输出里取）
- 发布前验收（见 [ReleaseProcess.md](ReleaseProcess.md)）

> 这条是**用户多次重申的硬要求**：本地重复跑分钟级任务既慢、又不产生新信息，
> 而且会让 Agent 陷入长等待、掩盖「哪里才是权威验证」这一事实。
> **本地只允许跑 `bin/Check*.py` 静态检查；所有 Maven 编译 / 测试 / verify 一律由 CI 承担。**
> 推送后**以 PR 的 checks 结果为准**，失败再按 CI 日志排查并推送修复。

### 8.1 检查分层与门禁时机

检查按**耗时**分两层，各自绑定不同的触发时机。这不是降低标准，而是把慢检查放到
它真正起作用的位置——合入前与发布前：

| 层 | 工作流 | 触发 | 内容 |
|---|---|---|---|
| **快检查** | `Build.yml` | 每次 `push` / `pull_request` | 编译 + 单元测试（`clean test -Djacoco.skip=true`）；矩阵含 Windows 与 Linux（wrapper 与 system），外加一条**可选**的 macOS 腿 |
| **dev JVM 构建** | `BuildJVMArtifacts.yml` | `push` → `dev` | 独立的 JVM 构建线（与 `DesktopNative.yml` / `CliNative.yml` 同构）：按「平台 × JDK」矩阵构建 `aha-desktop` 与 `aha-cli` 便携包并发布预发行版（`build-mvn-artifact` / `build-publish`）；JDK 轴为 25（基线，release=25）与 27（`-jdk27` 后缀，release=27，issue #65）；不影响 Build 快检查 |
| **门禁** | `Gate.yml` | `pull_request` → `main` / `release/**`、**每周定期**、手动触发、发布前（`workflow_call`） | 先断言 Maven 版本与 Wrapper 配置一致，再跑完整 `./mvnw clean verify`（含覆盖率门禁 ≥ 70%）、文档检查、技能检查、脚本检查、像素标志一致性、重复率检查，并打印覆盖率实测值（`bin/ReportCoverage.py`） |
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

**「构建成功」不是验收标准（强制）**：凡是产出物给用户 / 下游用的步骤，
验收条件必须包含「产物存在 + 自证通过」，不能以「命令退出码为 0」收尾。
尤其是带「开关」的管线：开关被静默跳过时构建照样成功，而且**不留任何错误**。
做法：① 开关的默认值单一来源（见 §7 的 profile 契约）；② 检查工具链的**执行痕迹**
（如 native-image 的 `<name>.build_artifacts.txt`）；③ 产物缺失时必须 `::warning::` 双写
（step annotation + Job Summary），把「被跳过」与「早退」两种可能都点名。
2026-10-08 实测事故：`native.skip` 默认值写在子模块 → profile 覆盖失效 →
CI 全绿却零产物零报错（见 `Docs/Troubleshooting/TS-202610-NativeSkipSilentOverride.md`）。

**门禁必须自证（强制）**：JaCoCo 的 `check` 通过时不打印百分比，日志上与「没配门禁」
无法区分（真实发生过：`./mvnw clean verify` 日志里只有 `Analyzed bundle`，被合理质疑
"何来的门禁"）。因此凡是不出声的检查，都必须把实测值与判定标准写进日志：

- 覆盖率由 `bin/ReportCoverage.py` 输出（阈值从 `pom.xml` 读取，**不在脚本里复制**）；
- 判定仍由 `jacoco:check` 独家执行，脚本只报数——单一判定来源；
- 验证一条门禁是否真的会拦，唯一可靠办法是**让它失败一次**（临时抬高阈值 → 看到
  `Rule violated` → 还原），只读配置只能证明「应该会拦」。

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
- 确需改名时，同一变更内必须同步更新分支保护，并在对应 Issue（当前
  [#85](https://github.com/ACANX/AHA/issues/85)）中刷新验收清单。

**CI 必须容忍仓库侧瞬时失败（强制）**：门禁要拦的是**代码与配置的问题**，不是网络抖动。
GitHub 上出现过 `Could not find artifact ... in central (https://repo.maven.apache.org/maven2)`，
而该 artifact 在 Central 上实测 HTTP 200——即**当次就没要下来**。`Gate` 是必需检查，
一次与代码无关的瞬时故障就会把 PR 卡红。

因此：

- **凡会解析依赖的 Maven 调用一律走 `.github/actions/maven-run`**（复合 action，单一来源）：
  先清本地仓库的 `*.lastUpdated` 失败标记；失败时**先判断性质**，只有命中「依赖解析不到 /
  传输中断 / 远端 5xx / 本地负缓存」这类与代码无关的特征才重试（最多 3 次），
  **其余立刻失败**——不给真失败白白乘以三倍时间；
- 新增此类调用时**不要**直接写 `run: ./mvnw ...`，否则会绕过重试与清理；
  例外是只做本地查询与 wrapper 自举的调用（如 `./mvnw -v`），它不解析项目依赖；
- 分清两条 Maven 消息，它们的处理方式不同：

  | 消息 | 含义 | 处理 |
  | ---- | ---- | ---- |
  | `... was not found in <url> during a previous attempt. This failure was **cached** in the local repository ...` | 本地仓库记了失败标记，更新间隔内不再重试 | 清 `*.lastUpdated`，或加 `-U` |
  | `Could not find artifact ... in central (<url>)` | 当次解析就失败 | 重试；先核实版本是否真实存在（`curl` 一下 Central） |

- 完整复盘见 [TS-202610-ArtifactNotFoundInCentral.md](../Troubleshooting/TS-202610-ArtifactNotFoundInCentral.md)。

**分支规则集必须对「单人 + 机器」可满足（强制）**：规则集（Ruleset）与门禁是**同一份契约的两端**，
两端必须自洽。已经出现过两种把 PR 永久锁死的情形（比红色更麻烦——红色至少告诉你哪里错）：

| 失效模式 | 实例 | 后果 |
| ---- | ---- | ---- |
| 审批要求与协作者数量不匹配 | 单人仓库设 `approvals=1` + `require_last_push_approval` | GitHub 禁止自我批准 → 永远凑不齐，提示「New changes require approval from someone other than …」 |
| 要求某工具的结果，却没人生产 | `code_scanning` 要求 CodeQL，仓库却未配置 code scanning | 永久停在「Waiting for Code Scanning results」 |

因此：

- **审批数按实际协作者数量设置**：单人仓库设为 `0`，卡点交给**必需检查**（机器执行，不看人数）；
- **要求什么结果，就必须有人生产，而且必须覆盖目标分支**：要么配置该工具，要么删掉那条规则。
  「有人生产」还不够——若扫描器的范围只覆盖默认分支（如 GitHub CodeQL 的默认设置只扫默认分支
  及指向它的 PR），而规则作用在另一个分支上，PR 依然会永久停在「Waiting for code scanning results」。
  因此配置扫描时必须**显式声明分支范围**（本仓库用工作流方式，写死 `pull_request: branches: [main, dev]`）；
- **同一种扫描只能配一次**：GitHub 的代码扫描「默认设置」与「高级设置（工作流）」**互斥**——
  两者同时存在时，工作流上传的结果会被拒收（`CodeQL analyses from advanced configurations
  cannot be processed when the default setup is enabled.`），表现为「检查一直等待」。
  本仓库 2026-10-07 实测到这一现象：同一份代码，工作流先成功一次、开启默认设置后连续失败两次。
  取舍时以**能否覆盖目标分支**为准（本例选工作流，因为默认设置覆盖不到 `main`）；
  覆盖率同理——本项目已由 `jacoco:check ≥ 0.70` + `bin/ReportCoverage.py` 在 `Gate` 里把关，
  是否再把覆盖率上传给外部服务属于**待拍板**事项；
- **必需检查与审批要求一律写进对应 Issue**（当前 [#85](https://github.com/ACANX/AHA/issues/85)，
  含现状 + 目标规格两张表；`TODO.md` 已冻结，不再作为登记处），
  每次变更规则集或作业名后同步回写——这与「作业名是分支保护的契约」是同一条约束的两面
  （那次是作业名失配停在 `Expected`，这次是规则要的东西不存在停在 `Waiting`）。

完整复盘见 [TS-202610-RulesetBlocksSingleMaintainer.md](../Troubleshooting/TS-202610-RulesetBlocksSingleMaintainer.md)。

**main 上的构建成功后自动打 tag（0.1.1 起）**：`Build.yml` 里有一个 `tag` 作业，
条件为「`push` 到 `main` 且 `build` 作业成功」。它：

- **从根 POM 读版本号**（`<version>`），创建 `V<版本号>` 的附注 tag 并推送——
  工作流与文档都不写版本字面量，避免版本升了 tag 不跟的两处口径；
- **幂等**：同名 tag 已存在则跳过（只改文档的合并也会 push 到 `main`，不该因此报错）；
- 需要 `contents: write`；若仓库把 Actions 默认权限设为只读，该作业会失败，
  处置同第 8.1 节的权限类问题。

两个必须知道的事实：

1. **`GITHUB_TOKEN` 推的 tag 不会触发下游工作流**（GitHub 防递归），所以 `Release.yml`
   不会因这个 tag 自动开跑——补救见 [ReleaseProcess.md](ReleaseProcess.md) §4.2；
2. 新增 `tag` 作业**不改变**任何既有作业名，因此不影响分支保护的必需检查契约（第 8.1 节）。

## 9. 规范变更程序

修改本文件需：

1. 提出修订理由与影响范围
2. 至少一名核心维护者批准
3. 更新版本号与变更日志
4. 同 PR 内同步更新 [BuildGuide.md](../Guide/BuildGuide.md)（若操作方式随之变化）

---

## 10. 原生镜像产物（试验性）

> 本节把 GraalVM `native-image` 产物的**已知结论**固化为规范（issue #80 的 E-02 ~ E-11），
> 供后续改动与排障引用。模块与工作流的实现见
> [CliNativeDesign.md](../Design/CliNativeDesign.md) 与 [DesktopNativeDesign.md](../Design/DesktopNativeDesign.md)；
> 依赖经验库见技能 `graalvm-reachability-metadata/references/catalog.md`。

### 10.1 定位与流水线

- 原生镜像是**旁路产物**（试验性），**不替代** JVM 形态；
- 流水线：`CliNative.yml` / `DesktopNative.yml`，随 `dev` push 自动出包并发布**预发行**；
- **不进默认构建**：默认构建的验收标准仍是「能编译、能打 jar、不报错」（第 7 节），
  原生镜像只在专用 profile（`-Pcli-native` / `-Pdesktop-native`）里构建；
- **不进必需检查**：见 10.3。

### 10.2 依赖可达性结论（E-02 ~ E-09，2026-10-09 核对）

| 编号 | 依赖 / 能力 | 结论 | 处理方式 |
|---|---|---|---|
| E-02 | `sqlite-jdbc` 的 JNI native library 嵌入 | ✅ 已实测 | 依赖自带 `native-image.properties`；加 `--initialize-at-run-time=org.sqlite` |
| E-03 | Jackson 3 反射配置 | ✅ 已实测 | **不带** GraalVM 元数据；手写注册被 `readValue` / `treeToValue` 触碰的 POJO 与记录（漏了会在读配置、开会话时崩） |
| E-04 | `ServiceLoader` 的 provider | ✅ 已实测 | GraalVM 对 `META-INF/services` **有内建支持**，通常无需手写；前提是服务文件进镜像（资源清单含 `META-INF/services/.*`） |
| E-05 | picocli 反射配置 | ✅ 已实测 | 用 `picocli-codegen` 注解处理器生成（比手写可靠） |
| E-06 | JLine 终端能力探测 | ✅ 已实测 | JLine 自带元数据；另补它**未覆盖**的 `org.jline.utils.Signals` 与 `java.lang.ProcessBuilder$RedirectPipeImpl` |
| E-07 | Log4j2 兼容性 | ✅ 已实测 | 自带 `reflect-config.json` + `resource-config.json`；加 `--initialize-at-run-time=org.apache.logging.log4j.core.config` / `.core.util` |
| E-08 | `java.net.http` 的 TLS / SSL | ⚠️ 实际在用、无独立结论 | `aha-core` 的 `DefaultLlmClient` 使用 `HttpClient` 且随产物发布；**未观察到**需要额外 TLS / 证书参数，但缺参数登记与结论——新增网络能力时须补测 |
| E-09 | 虚拟线程 | ⚠️ 实际在用、无独立结论 | `LocalAgentService` / `DefaultLlmClient` / `ChatCommand` / `ChatController` / `AhaDesktopApp` 均使用，且随产物构建通过；但**支持现状未系统调研** |

> 参数实体在 `aha-cli-native/src/native/native-image-args*.txt` 与
> `aha-desktop-native/src/native/native-image-args*.txt`（含 `-jdk27` 变体）；
> 本节只记**结论**，不复制参数全文——参数以文件为准。

### 10.3 验收边界与冒烟（E-10）

**JaCoCo 覆盖率门禁不覆盖 native 产物**（门禁只对 JVM 字节码插桩，见第 6 节）。
native 侧的质量保证分**三层**：

| 层 | 位置 | 判据 | 阻塞？ |
|---|---|---|---|
| ① 产物自证 | 两条 native 工作流的「产物自证」步骤 | 存在 / 体积下限 / 平台魔法数 / 依赖齐全 / 元数据完整 | 否（诊断式，写摘要） |
| ② **CLI 运行冒烟** | `CliNative.yml` 的「运行冒烟」步骤 | 真的执行产物：`--version` 退出码 0 且输出含本次版本号；`--help` 退出码 0 | 是（本工作流内硬断言） |
| ③ **GUI 运行冒烟** | `DesktopNative.yml` 的「运行冒烟」步骤（**仅 Windows 腿**） | 启动进程并观察 20 秒：进程存活，或 20 秒内以 **0** 退出；启动期以非 0 退出即告警 | 否（`continue-on-error`，写摘要） |

**为什么 GUI 冒烟只在 Windows、且不阻塞**：

- GitHub 的 `windows-latest` runner 有**交互式桌面会话**，能创建窗口；Linux runner 无 X（需 `Xvfb`），
  macOS 的 GUI 访问受限——所以只有 Windows 腿能做这件事；
- CI 环境**没有 GPU**：Prism 会回退到软件渲染 / WARP，**覆盖不到真实 D3D 路径**——而桌面端原生镜像
  的渲染坑恰恰集中在 D3D（`D3DResourceFactory.createStockShader`、`D3DShaderSource`、
  `LinearConvolveShadow` 等）。因此这一步只证明「**进程能起来、启动期不崩**」，**不做**像素对比、
  点击交互与窗口内容断言（脆弱，且在无 GPU 环境下的结论不可信）；
- GUI 测试抖动大：按本节与第 7 节的要求，**可选管线不得进必需检查**，失败必须在步骤级容错。

> **渲染与交互的真实验证仍留在本地 / 真机**：`aha-desktop` 的 `AhaDesktopSmokeTest`（真开 `Stage`）
> 默认不跑，需 `-Daha.ui.tests=true` 且要求图形环境（Linux 上需 `Xvfb`）——见 `TestingSpec.md` 与
> `TODO.md` 的 `D-04`（「何时在 CI 打开 UI 测试」与本节是同一个问题的两面）。
