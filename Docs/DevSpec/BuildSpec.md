# 构建规范

**文档版本**：v1.7.0
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
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/4.0.0-rc-7/apache-maven-4.0.0-rc-7-bin.zip
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
```

**禁止事项**：禁止使用 `LATEST` / `RELEASE` 等浮动版本标识。

## 4. 双版本验证（强制）

CI 必须同时执行两条流水线，且结果一致：

| 流水线 | 命令 | 用途 |
|---|---|---|
| Maven 4 | `./mvnw clean verify` | 目标运行时 |
| Maven 3.9.x | `mvn clean verify` | 兼容基线 |

任一条失败即视为**构建失败**，PR 不得合入。

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

**必须跑完整 `./mvnw clean verify` 的情形**：

- 改动触及构建定义：`pom.xml`、`module-info.java`、`aha-cli/src/assembly/dist.xml`、`.github/workflows/`
- 改动可能影响覆盖率口径：JaCoCo 排除项、模块结构、包名
- 需要刷新文档中的实测覆盖率 / 用例数
- 发布前验收（见 [ReleaseProcess.md](ReleaseProcess.md)）

> 局部验证**不能替代**合入前的完整流水线：CI 始终跑完整 `verify`（`Build.yml`）。
> 本节规定的是**开发过程中的最小验证**，用于避免每次改动都付分钟级代价。

## 9. 规范变更程序

修改本文件需：

1. 提出修订理由与影响范围
2. 至少一名核心维护者批准
3. 更新版本号与变更日志
4. 同 PR 内同步更新 [BuildGuide.md](../Guide/BuildGuide.md)（若操作方式随之变化）
