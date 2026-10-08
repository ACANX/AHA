# AHA 待办与调整项（暂存区）

**文档版本**：v0.29.0
**状态**：草稿
**生效日期**：2026-10-06
**最后更新**：2026-10-07
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本   | 日期       | 变更内容                                                     | 变更人 |
| ------ | ---------- | ------------------------------------------------------------ | ------ |

---

## 0. 阅读说明

**本文件是暂存区，不是正式规范或设计文档。**

用途：先把「待办与调整项」集中记录、逐条确认，**确认后再拆分合并到各自的正式文档**
（`Docs/DevSpec/`、`Docs/Design/`、`Docs/Guide/`）中。全部条目消化完毕后，
本文件应标记为「已废弃」或删除。

与 `Docs/PLAN.md` 的分工见该文件第 0 节。

### 0.1 条目类型

| 标记       | 含义                                               |
| ---------- | -------------------------------------------------- |
| `缺陷`     | 已核实的实际错误，会造成功能或使用问题             |
| `文档缺陷` | 文档内容与代码/事实不符                            |
| `规范偏差` | 与 `Docs/DevSpec/` 已有规范不一致                  |
| `风险`     | 尚未爆发，但已知会构成阻碍                         |
| `调研`     | 结论来自静态分析，**需动手验证**后方可作为决策依据 |
| `待决策`   | 需要人来拍板，无技术上的唯一正解                   |

### 0.2 证据等级

| 标记      | 含义                               |
| --------- | ---------------------------------- |
| ✅ 已核实 | 已通过阅读代码/配置文件确认        |
| ⚠️ 待核实 | 推断或外部知识，**未在本仓库验证** |

### 0.3 状态标记

| 标记       | 含义                                                       |
| ---------- | ---------------------------------------------------------- |
| ✅ 已完成  | 已在本仓库核实修复，无残留动作                             |
| ◐ 进行中   | 部分完成，或已改动但未收口（残留动作见条目正文）           |
| ☐ 未完成   | 未开始；若依赖前置条件，在条目正文注明                     |
| ⏸ 待决策   | 阻塞于拍板，拍板后转为 ☐ 或 ✅（见第 7 节）                |

> **注意**：本文件名 `TODO.md` 不符合 `DocumentationSpec.md` 第 1 节的 PascalCase 命名规范
> （保留名单中仅含 `README`、`AGENTS`、`SKILL`、`LICENSE`、`CHANGELOG`、`CONTRIBUTING`、
> `CODE_OF_CONDUCT`、`SECURITY`，不含 `TODO`）。严格合规应为 `Todo.md`。
> 因暂存区文件生命周期短，暂按现行名保留（`AGENTS.md` 与 `PLAN.md` 均按此名引用），
> **正式化时需一并处理**（见 E-12）。

> **需要人工执行的动作**（推送、仓库 / 分支保护设置、平台侧配置、外部环境验收——即
> 自动化做不到、又必须有人做才能收口的事）单独记在**第 11 节** `G-xx`，每条都写明
> **验收标准**（怎么算做完）。这类事项**不得只写在对话里**：对话会滚走，漏掉之后
> 既没有闭环，也无从判断「到底做过没有」。

---

## 1. 文档与仓库卫生

| 编号 | 事项                                        | 类型     | 证据 | 优先级 | 状态      | 落地文档                                      |
| ---- | ------------------------------------------- | -------- | ---- | ------ | --------- | --------------------------------------------- |
| A-01 | `AGENTS.md` 启动命令必然失败                | 文档缺陷 | ✅    | P0     | ✅ 已完成 | `AGENTS.md`                                   |
| A-02 | `CHANGELOG.md` 出现两个 `### 修复`          | 文档缺陷 | ✅    | P1     | ✅ 已完成 | `CHANGELOG.md`                                |
| A-03 | `aha-cli/aha-cli/` 嵌套残留目录             | 缺陷     | ✅    | P1     | ✅ 已完成 | 仓库清理                                      |
| A-04 | `Docs/AHA/` 空目录                          | 缺陷     | ✅    | P2     | ✅ 已完成 | 仓库清理                                      |
| A-05 | `ExtensionSystemDesign.md` 有两个「## 5.」  | 文档缺陷 | ✅    | P1     | ✅ 已完成 | `ExtensionSystemDesign.md`                    |
| A-06 | 扩展阶段编号「阶段八」与「阶段一」冲突      | 文档缺陷 | ✅    | P1     | ✅ 已完成 | `ExtensionSystemDesign.md`、`TestingSpec.md`、`ExtensionManager.java` |
| A-07 | `BuildSpec.md` §6 的覆盖率排除项引用失准    | 文档缺陷 | ⚠️    | P2     | ◐ 进行中  | `BuildSpec.md` §6                             |
| A-08 | `Docs/` 根下的 `PLAN.md` / `TODO.md` / `Dbsx.txt` 是否收进 `Docs/AHA/` | 待决策 | ✅ | P3 | ⏸ 待决策 | `DocumentationSpec.md` §2 |
| A-09 | `PLAN.md` §8.2 出现**两个 `8.2.7`**（环境无关性 / 日志文件不生成），编号重复易致误引 | 文档缺陷 | ✅ | P2 | ✅ 已完成 | `PLAN.md` §8.2 |
| A-10 | `PLAN.md` 有两个 `## 8.` 标题（「相关文档」与「0.1.0 发布前问题清单」），后者才是正文；`### 8.4` 亦排在 `8.7` 之后 | 文档 | ✅ | P2 | ✅ 已完成 | `Docs/PLAN.md` |

### A-01 ✅ 已完成

**原问题**：`AGENTS.md`「运行 CLI」写 `./bin/Aha.sh chat`，而 `bin/Aha.sh` 内
`LIB="$DIR/../lib"` 解析为仓库根 `<repo>/lib`（不存在），必然 `exit 1`。

**复核（✅ 已核实，2026-10-07）**：`AGENTS.md` 现为

```bash
./mvnw -pl aha-cli exec:java
# 或（需先执行 ./mvnw clean package 生成 dist/）
dist/bin/Aha.sh chat         # Linux / macOS
dist\bin\Aha.bat chat        # Windows
```

与顶层 `README.md` 一致，且已注明前置条件。**无残留动作。**

### A-02 ✅ 已完成

**原问题**：`[0.1.0]` 段落内 `### 修复` 出现两次，结构为 `新增 → 修复 → 变更 → 修复`。

**复核（✅ 已核实，2026-10-07）**：`CHANGELOG.md` 现为分组唯一的 `### 新增`
（0.1.0 是首个版本即基线，文件头部已说明「本节只包含新增」）。
分组唯一性与文件自述一致。**无残留动作。**

### A-03 ✅ 已完成

**复核（✅ 已核实，2026-10-07）**：`aha-cli/` 下现为 `log/`、`pom.xml`、`src/`、`target/`，
嵌套的 `aha-cli/aha-cli/` 已不存在。**无残留动作。**

### A-04 ✅ 已完成

**复核（✅ 已核实，2026-10-07）**：`Docs/` 下现为 `AHA/`（内含 `AHA-Design-V1.md`）、`Dbsx.txt`、
`Design/`、`DevSpec/`、`Diagrams/`、`Guide/`、`PLAN.md`、`TODO.md`，无空的 `AHA/` 目录。
**无残留动作。**

### A-05 ✅ 已完成

**复核（✅ 已核实，2026-10-07）**：`ExtensionSystemDesign.md` 章节现为
`1 → 2 → 3 → 4 → 5 隔离与权限 → 6 分阶段实施 → 7 实现现状（0.1.0）`，
顺序与编号均正常。**无残留动作。**

### A-06 ✅ 已完成

**复核（✅ 已核实，2026-10-07）**：

| 位置                            | 现状                                   |
| ------------------------------- | -------------------------------------- |
| `ExtensionManager.java:28`      | `// TODO(0.3): 通过 ExtensionLoader 加载并启动扩展` |
| `TestingSpec.md` §3.2           | 「扩展运行时属 0.3」                   |
| `ExtensionSystemDesign.md` §6   | 以版本号（0.3 / 0.4 / 0.5 / 1.0）表述  |

三处已统一为**版本号体系**，双轨编号（阶段序号）已消除。**无残留动作。**

### A-07 ◐ 进行中

**原问题**：`TestingSpec.md` 覆盖率小节与跨文档引用的编号可能漂移。

**复核（✅ 已核实，2026-10-07）**：

- `TestingSpec.md` §3 内部编号正常：`3.1 工具与门禁` → `3.2 排除项` → `3.3 实测值`，
  **不存在重复**。原条目「小节编号重复」的定性不成立。
- 但 `BuildSpec.md` §6 仍写：「排除项 | 必须登记理由（见设计文档第十二部分 3.3）」。

**残留动作**：核对「设计文档第十二部分 3.3」的实际指向。`Docs/AHA/AHA-Design-V1.md` 为多文档拼接
（`v3.32.0`，存在多个同名 `## 1.` / `## 2.`），该引用**极可能已失准**。
两条出路，需择一：

1. 改引 `TestingSpec.md` §3.2（该内容确在此处），并统一全仓对覆盖率排除项的引用目标；
2. 若确实指向 `Docs/AHA/AHA-Design-V1.md`，修正部分号与小节号。

> 原条目标记为 ⚠️ 待核实，本次复核**已确认 TestingSpec 侧无缺陷**，问题范围收窄为
> 「`BuildSpec.md` §6 的单一引用链未核对」。

---

### A-09 ☐ 未完成

**现象（✅ 已核实）**：`PLAN.md` §8.2 的条目表里有两个 `8.2.7`——一个是
「`SystemPromptLoaderTest` 不具环境无关性」，另一个是「日志文件长期不生成（见 §8.5）」。

**处理（2026-10-07，✅）**：`PLAN.md` §8 整体重排——`8.2` 表补齐并归位 `8.2.6`，与 §8.5 重复记录的
「日志文件长期不生成」改为 `8.2.11`；`### 8.4` 挪回 `8.3` 之后；重复的 `## 8.`（「相关文档」）
改为文末 `## 9.`。**外部引用零改动**：`SystemPromptLoaderTest` 仍为 `8.2.7`、分支保护仍为 `8.2.9`、
日志命名规则仍为 `8.6`，故 `TODO.md`、`LoggingDesign.md` 等处的指向全部保持有效。

**影响**：`PLAN.md` 正文按编号互引（如 §8.5 处提到「见 8.2.7」），编号重复会让引用指向
错误条目，也让「这一条到底做没做」变得含糊。

**残留动作**：为后者另取编号并核对全部 `8.2.x` 引用；本条目由第 11 节的留痕约定顺带发现，
与既定工作无关。

---

## 2. 代码健壮性

| 编号 | 事项                                                   | 类型 | 证据 | 优先级 | 状态      | 落地文档                               |
| ---- | ------------------------------------------------------ | ---- | ---- | ------ | --------- | -------------------------------------- |
| B-01 | `AgentEngine.run()` 空 choices 时返回 `null` 正文      | 风险 | ✅    | P1     | ☐ 未完成  | `AgentEngine`、`AgentServiceDesign.md` |
| B-02 | `AgentEngine.stream()` 的 `finishReason` 可能为 `null` | 风险 | ✅    | P1     | ☐ 未完成  | 同上                                   |
| B-03 | `jackson-annotations` 版本与 Jackson 3 是否对齐        | 调研 | ✅    | P2     | ✅ 已解决  | `Constitution.md` 第 5 条、`BuildSpec.md` §7 |
| B-04 | `resolveModelPath` 把未展开的 `${AHA_HOME:-~/.aha}` 当路径（Windows 抛 `InvalidPathException`，Linux 静默得到相对路径） | 缺陷 | ✅ | P1 | ✅ 已完成 | `ConfigLoader`、`BuildSpec.md` §8.1 |
| B-05 | 项目级身份查找无上界，会走到用户主目录并写入（Windows 临时目录位于主目录之下，故仅在 Windows 暴露） | 缺陷 | ✅ | P1 | ✅ 已完成 | `SystemPromptLoader`、`AHA-Design-V1.md` §5.1.1 |
| B-06 | `--help` 在非交互场景输出 ANSI 转义序列（picocli `Ansi.AUTO` 把 Windows 一律视为支持 ANSI） | 缺陷 | ✅ | P1 | ✅ 已完成 | `AhaCli.commandLine()` |
| B-07 | 测试依赖真实用户目录、且假设「环境里没有身份文件」，`verify` 在开发机与 CI 均不可复现 | 风险 | ✅ | P1 | ✅ 已完成 | `TestingSpec.md` §5.1 |

### B-01 / B-02 ☐ 未完成

**复核（✅ 已核实，2026-10-07）**：`AgentEngine.java` 现状与原始记录一致，**未改动**：

```java
// run()，第 114 行
if (response.choices() == null || response.choices().isEmpty()) {
    break;   // content 保持 null
}
...
// 第 136 行
return new AgentResponse(sessionId, content, allToolCalls, usage, finishReason);
```

```java
// stream()，第 171 / 200 行
String[] roundFinishReason = {null};
...
finishReason = roundFinishReason[0];   // 仅在 DONE 时赋值
```

即：首轮空 `choices` → `content` 为 `null`；流异常中断 → `finishReason` 为 `null`。

**残留动作**（同原建议）：

1. 明确 `AgentResponse.content()` 契约（允许 `null` 还是返回空串），写入 Javadoc
2. 调用方（`LocalAgentService` / `ChatCommand` / `RunCommand`）统一判空
3. 补单元测试覆盖「首轮空 choices」「流中断无 DONE」两条边界

### B-03 ✅ 已解决（2026-10-07）

**原问题**：`jackson-annotations` 版本（`2.21`）与 Jackson 核心（`3.1.3`）不同步，疑为版本冲突。

**复核（✅ 已核实，2026-10-07）**：

| 项                              | 原记录        | 现状                              |
| ------------------------------- | ------------- | --------------------------------- |
| 父 POM `jackson.version`        | `3.1.3`       | **`3.2.3`**（已升级）             |
| `dist/lib/jackson-annotations`  | `2.21`        | **`2.22`**                        |
| `dist/lib/jackson-core`         | `3.1.3`       | `3.2.3`                           |
| `dist/lib/jackson-databind`     | `3.1.3`       | `3.2.3`                           |

**结论**：原分析成立——`jackson-annotations` 保留 `com.fasterxml.jackson.annotation`
坐标、版本号与核心不同步属**预期行为**，不构成缺陷；且两者均随升级同步前进。
`Constitution.md` 第 5 条选型清单仍写 **「Jackson 3.1.3（groupId `tools.jackson`）」**，
与父 POM 的 `3.2.3` **已漂移**。

**残留动作（已完成）**：`Constitution.md`、`AHA-Design-V1.md`、`README.md` 的选型清单已由
具体版本号改为**主版本线**（`3.x`）；`BuildSpec.md` §7 新增「版本单一来源」规则：
确切版本以父 POM 的 `<properties>` 为唯一来源，文档不得复制。

---

### B-04 ~ B-07 ✅ 已完成（2026-10-07）：Windows 平台暴露的四类缺陷

**发现方式（✅ 已核实）**：PR #6 在 CI 上始终卡在 `build (windows-latest, wrapper)`，
但 Linux 侧三处都过。用 Windows 侧 Git Bash 执行与 CI 完全相同的
`./mvnw clean verify` 复现，得到 **15 个失败 / 错误**，归为四类。修完后两个平台
（Windows Git Bash 与 Linux）均为 BUILD SUCCESS。

| 编号 | 现象 | 性质 | 处理 |
| ---- | ---- | ---- | ---- |
| B-04 | `ConfigLoader.resolveModelPath` 把未展开的 `${AHA_HOME:-~/.aha}/Model.yml` 直接交给 `Path.of` | **生产缺陷**。Windows 因 `:` 抛 `InvalidPathException`；Linux 因 `:` 合法而静默变成一个名为 `${AHA_HOME:-~/.aha}` 的相对路径——**两个平台都不对，只是 Linux 不报错** | 先展开占位符；仍含 `${` 或含平台非法字符时回退默认位置 |
| B-05 | 项目级身份查找沿目录向上**没有边界** | **生产缺陷**。会一路走到用户主目录，把 `~/AHA.md` 当成项目级身份：既绕过用户级目录 `~/.aha`，又让来源标注失真。Windows 的临时目录位于 `%LOCALAPPDATA%`（主目录之下），因此只在 Windows 暴露；Linux 的 `/tmp` 不在 `/root` 之下，侥幸通过 | 查找止步于用户主目录（`user.home`，并参考 `USERPROFILE` / `HOME`）；`resolve` 与 `findProjectRoot` 共用该边界 |
| B-06 | `--help` 输出混入 ANSI 转义序列 | **生产缺陷**。picocli 的 `Ansi.AUTO` 把 Windows 一律视为支持 ANSI，于是 `aha --help > help.txt` 也会把转义序列写进文件 | 新增 `AhaCli.commandLine()` 统一按 `System.console()` 决定 `Help.Ansi`，`main`、无参数分支与测试共用同一入口 |
| B-07 | 测试读写开发机真实用户目录、并假设「环境里没有身份文件」 | **测试可靠性缺陷**。`SystemPromptLoaderTest` 一度在用户写过 `~/.aha/AHA.md` 后 7 个用例全红（此前已登记于 `PLAN.md` §8.2.7，本轮方知它正是 CI 失败主因）；`ProjectIdTest` 用 `Path.of("/home/me/proj")` 造期望值，在 Windows 上是「当前盘下的绝对路径」 | 测试类隔离 `AHA_HOME` / `user.home`；断言改用平台自身路径；需要身份内容时自己写一份已知内容的文件 |

**规范落点**：`TestingSpec.md` 新增 §5.1「测试的环境无关性（强制）」——
隔离用户目录、不假设环境干净、用平台自身路径、只写合法文件名的特殊字符，
并约定「向上查找须有边界」「CI 矩阵不得 fail-fast」。

> **注**：B-05 的边界同时修正了「`~/.aha` 之外的用户级文件被当成项目级」这一
> 语义含混；若用户确实需要全局身份，正确位置是 `~/.aha/AHA.md`（用户级目录）。

---

## 3. 架构与治理

| 编号 | 事项                                 | 类型   | 证据 | 优先级 | 状态     | 落地文档                                         |
| ---- | ------------------------------------ | ------ | ---- | ------ | -------- | ------------------------------------------------ |
| C-01 | JPMS 收益与成本的时间错配 | 待决策 | ✅ | P2 | ✅ 已决策 | `Constitution.md` 第 4 条、`ModuleConvention.md` |
| C-02 | `ServiceLoader` 双声明的长期维护成本 | 待决策 | ✅    | P3     | ⏸ 待决策 | `ModuleConvention.md`                            |
| C-03 | 依赖治理补强（Enforcer / ArchUnit）  | 调研   | ✅    | P2     | ☐ 未完成 | `BuildSpec.md`                                   |
| C-04 | `jlink` 禁令的解除条件未登记         | 待决策 | ✅    | P3     | ☐ 未完成 | `BuildSpec.md` §7                                |

### C-01 ✅ 已决策（2026-10-08）：JPMS 非强制，OpenJFX 优先

**决策**：**JPMS 不再要求强制使用**。优先尝试采用 OpenJFX；必要时 **JPMS 为 OpenJFX 让路**
（可退到 classpath 构建，须在 `BuildSpec.md` 记明原因与影响面）。

**连带修正（本轮已落地）**：`Constitution.md` 第 1 条、`BuildSpec.md` §7、`AHA-Design-V1.md`
第 1 条与头部、`README.md`、`AGENTS.md`、`ArchitectureOverview.md` 的「JPMS 强制启用」
全部改为「**优先启用（非强制）**」；fat JAR 的禁用理由改述为「无法按模块追踪依赖与许可、
无法与 `dist/{bin,lib}` 布局一致」，**不再挂在 JPMS 上**；`TestingSpec.md` 的 TestFX 行补注
「与 JPMS 冲突时可在 classpath 下运行」。

**残留动作**：OpenJFX 真正引入后，若发现模块化阻碍（TestFX、WebView 反射、`--add-opens`），
按「OpenJFX 优先」原则退到 classpath，并在 `BuildSpec.md` 记明原因。

**结论（复核 ✅ 已核实，2026-10-07）**：`ModuleConvention.md` v1.1.0 §1 现为三行简短规则
（必须有 `module-info`、必须显式导出、`opens` 必须限定），**仍未写明 JPMS 的存在理由与复核点**。
原分析全部成立，无变化。

**残留动作**（同原建议）：

1. 在 `ModuleConvention.md` 中**显式写明 JPMS 的存在理由**为「`ModuleLayer` 扩展隔离 +
   依赖方向不可违反」，而非泛指「架构清晰」
2. 若扩展路线图被推迟或砍掉，**必须触发 JPMS 保留决策的重新评估**
3. 在 0.5 实施 `ModuleLayer` 后，回顾并量化 JPMS 的实际收益

### C-02 ⏸ 待决策

**复核（✅ 已核实，2026-10-07）**：`CHANGELOG.md` 仍记载
「工具与适配器服务同时在 `module-info` 的 `provides` 与 `META-INF/services` 中声明」，
双声明**仍在**；`ModuleConvention.md` 未登记该要求。

**新增交叉信息**：E-04 指出——双声明在 native-image 场景下**反而降低了配置成本**
（GraalVM 的 `ServiceLoaderFeature` 能扫 `META-INF/services`）。决策时应一并考虑。

**残留动作**（三选一，需决策）：

1. 维持现状，在 `ModuleConvention.md` 登记「双声明为强制要求及理由」
2. 放弃 classpath 兼容，删除 `META-INF/services`
3. 用脚本/注解处理器校验两处声明**一致**——建议至少落地本项

### C-03 ☐ 未完成

**复核（✅ 已核实，2026-10-07）**：父 `pom.xml` 中**无** `maven-enforcer-plugin`、
无 `archunit` 依赖。原条目未动。

**残留动作**：评估引入 Maven Enforcer（`bannedDependencies`）或 ArchUnit 作为 JPMS 的
**补充**（非替代），把 `BuildSpec.md` §7 的约定变成构建期失败；新增依赖需与
`Constitution.md` 第 5 条选型清单同步登记。

### C-04 ☐ 未完成

**复核（✅ 已核实，2026-10-07）**：`BuildSpec.md` §7 现状仍为：

> **禁止**在 0.1 使用 `jlink`——`sqlite-jdbc` 为自动模块，jlink 不支持

**未补记解除条件**。

**残留动作**：补记解除条件——若 `sqlite-jdbc` 未来提供 `module-info`（或改用提供
`module-info` 的 JDBC 驱动），`jlink` 可显著缩小发行包，届时应重新评估。
目前写法读起来像永久约束，实际是 0.1 的临时限制。

---

## 4. 桌面端（OpenJFX，规划 0.2）

> 现状：`aha-desktop` 仍为占位模块（`module-info` + 空 JAR），但**平台依赖机制已就位**——
> 父 POM 的 5 个 `javafx-*` profile + `dependencyManagement` 已能解析本平台的 JavaFX 分类器工件，
> `aha-desktop` 已声明 `javafx-base` / `javafx-graphics` / `javafx-controls`（2026-10-08 实装并实测）。

| 编号 | 事项                                    | 类型     | 证据 | 优先级 | 状态     | 落地文档                           |
| ---- | --------------------------------------- | -------- | ---- | ------ | -------- | ---------------------------------- |
| D-01 | 发行包会混入多平台 JavaFX native JAR    | 风险     | ⚠️    | P1     | ✅ 已解决 | `dist.xml`、`BuildSpec.md` §7      |
| D-02 | 启动脚本需按模块路径分叉                | 风险     | ✅    | P2     | ✅ 已完成 | `bin/Aha.sh`、`bin/Aha.bat`        |
| D-03 | FXML 反射需限定 `opens`                 | 风险     | ⚠️    | P2     | ☐ 未完成 | `aha-desktop/module-info.java`     |
| D-04 | TestFX 在 JPMS + 无显示 CI 下的配置     | 风险     | ⚠️    | P2     | ◐ 门禁方式已定 | `TestingSpec.md` §1                |
| D-05 | 覆盖率排除项的长期归属未定              | 待决策   | ✅    | P2     | ✅ 已决策 | `TestingSpec.md` §3.2              |
| D-06 | 发行目标平台与 CI runner 平台不匹配     | 文档缺陷 | ✅    | P2     | ◐ 已决策，待回填 | `DesktopDesign.md` §2/§3           |
| D-07 | JavaFX 线程模型与虚拟线程的桥接未设计   | 设计缺口 | ✅    | P2     | ✅ 已完成 | `DesktopDesign.md`、`GUIDesign.md` §8 |
| D-08 | `jpackage` 不可交叉编译 → CI 需分平台   | 风险     | ✅    | P2     | ◐ 流水线已就位 | `Build.yml`、`DesktopDesign.md` §3 |
| D-09 | 桌面端无内置工具（未依赖 `aha-tool`）   | 设计缺口 | ✅    | P1     | ✅ 已落地（pom 已声明） | `aha-desktop/pom.xml`、`DesktopDesign.md` |
| D-10 | 桌面端包缺 `log4j2.xml` 与 `version.properties`（二者在 `aha-cli`） | 设计缺口 | ✅ | P2 | ✅ 已完成 | `aha-common`、`aha-core` |
| D-11 | 桌面端不读 `Aha.yaml`（只用默认日志配置） | 设计缺口 | ✅ | P2 | ✅ 已完成 | `aha-core/boot`、`aha-desktop` |
| D-12 | 桌面端界面仍是 0.1 占位（无三栏） | 设计缺口 | ✅ | P1 | ✅ 已完成 | `aha-desktop/view` |
| D-13 | 0.2 桌面端剩余项（记忆面板 / 扩展面板 / 身份加载顺序展示 / 会话内搜索 / 覆盖率门禁上线 / `jpackage`） | 设计缺口 | ✅ | P2 | ◐ 见 `Docs/Dbsx.txt` | `aha-desktop`、`DesktopDesign.md` §11 |
| F-16 | Maven 4 下 verify 日志出现 10 行 `[stderr]` | 缺陷 | ⚠️ | P2 | ☐ 未完成 | `Gate.yml` 日志、`aha-core` 测试 |
| F-18 | 三个文件里残留合并冲突标记且已入库（`CodeQL.yml` / `BuildSpec.md` / `TODO.md`，共 11 处） | 缺陷 | ✅ | P1 | ✅ 已修复 | `.github/workflows/CodeQL.yml`、`bin/CheckDocs.py` |
| N-01 | 需要「下载即用」的验证包：JVM 模式要先配 JDK 与 JavaFX，无法一键试 | 需求 | ✅ | P1 | ✅ 已完成 | `aha-desktop-native`、`DesktopNativeDesign.md` |
| N-02 | 原生镜像构建失败不得影响既有构建与发版 | 需求 | ✅ | P1 | ✅ 已完成（profile 隔离 + 独立工作流 + `native-v` tag 命名空间 + `continue-on-error`） | `pom.xml`、`.github/workflows/DesktopNative.yml` |
| N-03 | 每次合并到 dev 独立出包，版本可追溯到 PR | 需求 | ✅ | P1 | ✅ 已完成（`a.b.c.PPPPP`，PR 段补零到 5 位；本地实测 `0.1.1.00021`） | `DesktopNative.yml`、`DesktopNativeDesign.md` §4.3 |
| N-04 | 增加 JDK 27 编译分支（对比 Leyden AOT / 原始类型 / GC 对启动与内存的影响），**先只开 Windows**，为 JDK 29 铺路 | 需求 | ✅ | P2 | ◐ 机制已就位，数据待采集 | `aha-desktop-native/pom.xml`（`native-jdk27` profile）、`native-image-args-jdk27.txt`、`DesktopNativeDesign.md` §5 |
| N-05 | 首次真机验证三条腿的下载产物（双击即用、能开窗、能对话） | 验证 | ☐ | P1 | ☐ 待工作流首跑 | `DesktopNativeDesign.md` §7 N-1 |
| N-06 | 把 §5.3 的对比表填上真实数字（原生镜像 JDK25 / JDK27 / JVM+Leyden AOT 三种形态） | 实验 | ☐ | P2 | ☐ 待采集 | `DesktopNativeDesign.md` §5.3 |
| N-07 | JDK 27 分支扩展到 Linux / macOS；二进制内部版本号带上 PR 段；体积瘦身 | 规划 | ☐ | P3 | ☐ 后续 | `DesktopNativeDesign.md` §7 N-3/N-4/N-5 |
| N-08 | 沉淀可复用技能：`.agents/skills/java-app-graalvm-native-image-compile/`（先建骨架、边做边改、达成目标才成熟） | 需求 | ✅ | P1 | ◐ 已建（v0.1.0 试验中） | `.agents/skills/java-app-graalvm-native-image-compile/SKILL.md` |
| N-09 | 把该技能迭代到**成熟**：目标平台真机跑通 + 未验证条目清零 + **在别的项目复用过一次**（四条达标判据见技能「用法」一节） | 验证 | ☐ | P2 | ☐ 待跟踪 | `SKILL.md`（成熟度）、`references/skill-lifecycle.md` |

### D-01 ✅ 已解决（2026-10-08）：机制 + 描述符均已落地

**已落地**：父 POM 的 `javafx-*` per-OS profile（设 `javafx.platform`）+
`dependencyManagement`（三个工件带分类器）+ `aha-desktop` 显式声明与空壳排除。
实测依赖树只剩本平台三个真 jar（`javafx-base` / `javafx-graphics` / `javafx-controls` 的
`linux` 分类器），模块名均无 `[auto]`；`dist/lib` 的 18 个 jar 中 javafx 相关为 0（CLI 不受影响）。
详见 `DesktopDesign.md` 第 5 节。

**描述符已加**：`aha-desktop/src/assembly/dist-desktop.xml` 产出便携包
`aha-desktop-<版本>-<平台分类器>.zip`，文件名由构建期解析结果决定（实测 Linux 产出 24 MB 包，
内含且仅含 `linux` 分类器的 3 个 OpenJFX jar）。

**残留动作**：自包含安装包（`jpackage`，内置运行时）仍待评估，见 `D-08`。

**复核（✅ / ⚠️）**：`aha-cli/src/assembly/dist.xml` 的 `<dependencySet>` **仍无 classifier 过滤**
（✅ 已核实）；JavaFX 的平台 classifier 机制属外部知识（⚠️），需在引入 JavaFX 后实测。

**残留动作**：为桌面端另建 assembly 描述符（如 `dist-desktop.xml`），用 `<classifier>` 或
profile 按平台筛选；或改用 `jpackage` 产出自包含运行时，不再依赖 `dist/lib` 布局。

### D-02 ✅ 已完成（2026-10-08）：桌面端独立启动脚本

**复核（✅ 已核实）**：`bin/Aha.sh` 仍为单一 CLI 启动路径（`--module com...AhaCli`）；
`bin/` 下**无** `AhaDesktop.sh` / `.bat`。

**已实现**：新增 `bin/AhaDesktop.sh`（LF）与 `bin/AhaDesktop.bat`（纯 ASCII + CRLF，经
`bin/CheckScripts.py` 校验），**未改动** `bin/Aha.sh` / `bin/Aha.bat`（不在现有脚本里加分支）。
两者以 JPMS 模块路径启动 `com.acanx.module.aha.desktop.AhaDesktopApp`，并随桌面端便携包一起分发。

### D-03 ☐ 未完成

**残留动作**：0.2 实施时在 `aha-desktop/module-info.java` 中按 `Constitution.md` 第 3 条
包结构冻结清单（已预留 `com.acanx.module.aha.desktop.controller`）添加**限定** `opens`。

> 注：`GUIDesign.md` §8.1 建议采用「进程内直调 + JavaFX 原生控件」，若最终仍用 FXML，
> 本条适用；若完全转为代码构建 UI 则本条可降级。

### D-04 ◐ 门禁方式已定（2026-10-08），TestFX 选型与 CI 启用待定

**要定的事**：UI 测试怎么跑、在哪里跑。

**已定**：需要图形环境的测试**默认不跑**。`AhaDesktopSmokeTest` 由
`@EnabledIfSystemProperty(named = "aha.ui.tests", matches = "true")` 门禁，显式加
`-Daha.ui.tests=true` 才执行。依据是项目自己的硬规则——测试必须环境无关
（`TestingSpec.md` §5.1）——而 CI 的 ubuntu runner 无显示、本机 WSL **连 GTK 都没有**
（实测 `libgtk-3` 命中数为 0），开成默认只会满地红。

**待定**：

1. 用 TestFX 还是纯 JUnit 驱动？当前冒烟测试是**纯 JUnit**（`Platform.startup` + `runLater`
   断言 + 轮询等待状态更新），**没有引入任何新依赖**；
2. 何时在 CI 打开：Linux 需 Xvfb 并装 GTK 依赖，Windows / macOS runner 通常有会话；
3. 打开的开关应是**独立可选 job**，不进必需检查——否则又是一次「必需检查永久挂起」。

**验收标准**：至少一条平台（建议 Windows）在 CI 上**真实跑过**窗口冒烟测试并留下日志。

### D-05 ✅ 已决策（2026-10-08）：纳入门禁 + 单独阈值，分两步走

**决策**：桌面端**纳入覆盖率门禁**，但用**单独阈值**——0.2 开发期 **0.30**（适度调低便于开发），
0.2 收尾评审后再向其他模块的 **0.70** 对齐。细则已写入 `TestingSpec.md` §3.4。

**复核（✅ 已核实）**：父 POM JaCoCo 仍泛排除 `com/acanx/module/aha/desktop/**`；
`TestingSpec.md` §3 仍**未给 Desktop 阈值**，§3.2 仍登记「桌面端 0.2 实现」。

**残留动作**：在 `TestingSpec.md` §3 中为 Desktop 模块**显式设定阈值或明确永久排除**，
并说明 `BUNDLE` 级聚合门禁下桌面端低覆盖对整体的影响。

### D-06 ◐ 已决策（2026-10-08），文档待回填

**决策**：**支持承诺只有 Windows + Linux 双平台**；macOS **要求 CI 能打出包**（`jpackage`），
但**不要求跑测试**——开发资源有限，不在 macOS 上投入测试维护。

**复核（✅ 已核实）**：`DesktopDesign.md` v1.1.0 §2 已改为指向 `GUIDesign.md` 并标注
「WebView / FXML 组合从未真正决策，待评审」——**这是 D-07 范畴的进展，不是 D-06**。
§2 仍**未列目标平台清单**（2026-10-07 再次实查确认）；`Build.yml` 矩阵**已加 macOS 腿**
（`F-06`，定位是「演示与可选」，见 `BuildSpec.md` §4.1——原先「仍无 macOS runner」的记述已失效）。

**残留动作**：在 `DesktopDesign.md` §2 列出目标平台清单（Windows / Linux / macOS），
与 §3 的 CI 分平台策略、`Build.yml` 的 runner matrix 三者对齐。

### D-07 ✅ 已完成（2026-10-08）：选型 + 线程模型 + 可执行契约

**决策**：进程内直调 + JavaFX 原生控件优先（`GUIDesign.md` §8.1）；本地 HTTP + WebView 降为兜底。

**已完成**：

- `DesktopDesign.md` 新增 **§6 线程模型**，含三项决策：高频回调的**合并**、**取消与关窗联动**、
  **虚拟线程不得直接触碰界面**；
- 契约落到代码：`FxDispatcher` / `PlatformFxDispatcher` / `FxBridge`
  （`aha-desktop/src/main/java/com/acanx/module/aha/desktop/fx/`）；
- **规则可自证**：`FxThreadContractTest` 扫描主源码，除 `PlatformFxDispatcher` 外出现
  `Platform.runLater(` / `Platform.startup(` / `Platform.isFxApplicationThread(` 即失败，
  并断言白名单文件里确实存在调用（避免扫描范围写错导致假通过）；
- **可测性**：`FxBridgeTest`（6 例）在**无图形环境**下验证合并、终值不丢、
  渲染期间到达的值不丢、关窗丢弃、渲染异常不致命。

### D-08 ◐ 打包流水线已就位（2026-10-08），jpackage 安装包待评估

**复核（✅ 已核实）**：`Build.yml` 仍无打包 job；`Release.yml` 仍只上传 `dist.zip`。

**残留动作**：0.2 在 `Build.yml` 新增按平台的打包 job，并扩展 `Release.yml` 的产物矩阵
（桌面端安装包需另设产物）。

**补充（`D-06` 决策，2026-10-08）**：打包 job **要覆盖 macOS**，但 macOS 腿**只打包、不跑测试**；
必需检查仍是 Windows + Linux。

**已落地（2026-10-08）**：`Release.yml` 新增 `desktop` 作业（矩阵 `ubuntu` / `windows` / `macos`，
`fail-fast: false`），每条腿跑 `./mvnw clean verify` 后由产物名 + 依赖树**双向自证**平台分类器，
再把 `aha-desktop-<版本>-<系统>-<架构>.zip` 上传到 release 页面；CLI 资产同步改名
`aha-<版本>-cli.zip`。自证脚本已本地实跑（正常路径重命名成功；喂错期望值被 `::error::` 拦住
且不动物件）。

**残留动作**：`jpackage` 自包含安装包（内置运行时，用户无需自备 JDK）仍需评估——
`jlink` 受 `sqlite-jdbc`（自动模块）限制，可行路径是非模块化 app image。

### D-09 ✅ 已决策（2026-10-08）：桌面端需要内置工具

**决策**：桌面端**需要内置工具**（引入 `aha-tool`）。依赖矩阵已在
`Constitution.md` 第 4 条与 `ModuleConvention.md` §2 补上 `core ← tool ← desktop` 边与对应规则。
剩余实现动作：`aha-desktop/pom.xml` 声明 `aha-tool`（0.2 实施时）。

**现象**：`aha-desktop/pom.xml` 仅依赖 `aha-core`，**未声明 `aha-tool`**。
内置工具（file / http / shell）全在 `aha-tool` 且经 `ServiceLoader` 发现，
因此桌面端当前形态下**不具备任何工具能力**。`Constitution.md` 第 4 条的依赖矩阵中，
desktop 亦未列 tool 依赖。

**依据（✅ 已核实，2026-10-07）**：`aha-desktop/pom.xml` 与 `Constitution.md` 第 4 条。

**建议动作**：0.2 明确桌面端是否需要内置工具；若需要，修订 `Constitution.md` 第 4 条
依赖矩阵（新增 `core ← tool → desktop` 边）并同步 `ModuleConvention.md` §2。

### D-10 ✅ 已完成（2026-10-08）：版本号与日志装配下移公共模块

| 内容 | 原位置 | 现位置 |
|---|---|---|
| v0.1.0 | 2026-10-06 | 初始版本：汇总 0.1 代码走查、JPMS 架构评估、桌面端与原生编译调研结论 | @ACANX |
| v0.2.0 | 2026-10-07 | 新增第 6 节「记忆能力（0.6 起）」：推进顺序、写入策略、载体选型实测依据、跨环境共享机制 | @ACANX |
| v0.3.0 | 2026-10-07 | **重排版**：清除重复副本与误粘贴的会话日志（见 `PLAN.md` §4）；全部条目表格新增「状态」列并逐条复核；新增第 9 节「复核结论汇总」 | @ACANX |
| v0.3.0 | 2026-10-07 | `Docs/` 清单更新：设计文档已移入 `Docs/AHA/` | @ACANX |
| v0.4.0 | 2026-10-07 | B-03 版本漂移结项 | @ACANX |
| v0.5.0 | 2026-10-07 | 新增第 10 节「CI、门禁与工程效能」（F-01~F-06，均已落地）：检查分层与每周定期扫描、门禁断言 Maven 版本、Maven 3.9.x 独立兼容工作流、重复代码率检查、矩阵不再 fail-fast、macOS 可选腿定位；§2 新增 B-04~B-07（Windows 暴露的四类缺陷，均已修）并**修正 B-03 的三处过期状态**（条目正文 ✅ 已解决，而 §2 汇总表与 §8 落地去向表仍写 ◐ 进行中） | @ACANX |
| v0.6.0 | 2026-10-07 | 新增第 11 节「待人工执行的动作」（G-01 推送提交 / G-02 分支保护配必需检查 / G-03 确认定期扫描生效），每条写明阻塞面与**验收标准**，并在 §0 立下「此类事项不得只写在对话里」的约定；新增第 12 节与 H-01（启动标志默认风格待拍板）；§1 新增 A-08（暂存区文件存放位置）、A-09（`PLAN.md` §8.2 存在两个 `8.2.7`）；§10 新增 F-07（`.ps1` 未纳入检查）；§7 补三条待决策项并重排序 | @ACANX |
| v0.7.0 | 2026-10-07 | 新增 `F-08`（✅ 已修）：给必需腿补 `optional` 矩阵键使作业名多出 `, false`，必需检查永久停在 `Expected — Waiting`——附取证对照表与教训；`G-02` 按实况重写（现已配置三条 `build (...)` 快速腿，另需补配 `Gate` / `Compat` 两条，给出与作业 `name:` 完全一致的字符串与维护约定） | @ACANX |
| v0.8.0 | 2026-10-07 | 新增 `F-09`（✅ 已修）：覆盖率门禁静默不可自证，附两步核实法（配置 + 抬阈值使其失败）与修法；`Gate.yml` 现打印实测值 | @ACANX |
| v0.9.0 | 2026-10-07 | 新增 `F-10`（✅ 规范偏差：工具层已知拒绝被记 ERROR 并附堆栈，违反 LoggingDesign §4）与 `F-11`（✅ 测试日志污染构建日志），附修法与实测验证数据 | @ACANX |
| v0.10.0 | 2026-10-07 | 新增 `A-10`（`PLAN.md` 有两个 `## 8.` 标题）。新增 `F-12`（✅ 已修：PR #6 以单父提交重新落地，使 PR #8 永久 `dirty`；已用 `-s ours` 补血缘并逐项验证）与对应的教训、规范落点；`G-01` 按实况更新（前半已完成，现待推送合并提交 `06c6121`，补 `mergeable_state` 验收标准） | @ACANX |
| v0.10.1 | 2026-10-07 | `F-12` 引用同步开发日志改名（`DevLog-20261007-21-2.md` → `DevLog-20261007-22.md`） | @ACANX |
| v0.11.0 | 2026-10-07 | 新增 `F-13`（✅ 已修：CI 插件依赖「当次要不到」的定性过程与修法，含两条 Maven 消息的判别）与 `F-14`（⏸ 待决策：`.github/**/*.yml` 无本地检查） | @ACANX |
| v0.11.1 | 2026-10-07 | `D-06` 修正失效断言：`Build.yml` 矩阵已含 macOS 腿（`F-06`，定位为演示与可选），而 `DesktopDesign.md` §2 缺平台清单一节经再次实查仍成立 | @ACANX |
| v0.11.2 | 2026-10-07 | `A-09` / `A-10` 结项：`PLAN.md` §8 编号与顺序整体重排（§8.2 表归位并新增 `8.2.11`、`### 8.4` 归位、重复的 `## 8.` 改为文末 `## 9.`），外部引用经核对零改动 | @ACANX |
| v0.11.3 | 2026-10-07 | 新增 `F-15`（⏸ 待决策：是否把「文档编号重复」纳入 `bin/CheckDocs.py`）；说明 `B-01`/`B-02` 的状态列经复核**不是**矛盾（该列为「证据」而非结果） | @ACANX |
| v0.11.4 | 2026-10-07 | §7 补「与 0.1 的关系」与建议表：13 项待决策中只有 `H-01` / `E-12` / `A-08` / `F-07` 与 0.1 相关，逐条给出建议与理由 | @ACANX |
| v0.12.0 | 2026-10-07 | `G-02` 重写为「分支规则集整改」：附现状实测表（dev/main 两条规则集逐条规则）与目标规格表（审批数、必需检查、`code_scanning`/`code_coverage` 二选一）；新增 `G-04`（开启 CodeQL，附「不开就必须删规则」的对应关系） | @ACANX |
| v0.13.0 | 2026-10-07 | `F-12` 补「同日复发」实测记录（PR #8 以 squash 合入，`dev` 树 == `dependa@1518056` 树，用 `-s ours` 接回血缘 `d1de175`）；新增 `G-05`（仓库设置关闭 squash/rebase 合并）；`G-01` 改为推送本次补血缘的合并提交 | @ACANX |
| v0.14.0 | 2026-10-07 | `G-01` 按方案 B 后的实际分支状态重写（`dev` 待推送 2 条、`dependa` 已复位无需推送、`dev` 直接推送可能被规则集拒绝的处置），并记录复位后的实测代价：首次 `dependa ← dev` 会在 6 个文档文件上冲突及解法 | @ACANX |
| v0.15.0 | 2026-10-07 | `G-04` 改写：新增 `.github/workflows/CodeQL.yml`（高级设置/工作流方式，显式覆盖 `pull_request → main`，手工构建走统一 Maven 入口），说明为何默认设置覆盖不到 `main`、与必需检查契约无关，并给出「不要扫描就删规则 + 删工作流」的备选 | @ACANX |
| v0.16.0 | 2026-10-07 | `G-04` 补上线实测：工作流 init/compile 三次全过、失败仅在 `analyze`；因两份分支树零差异判定为配置冲突，指向「默认设置与高级设置互斥」并给出二选一处置；顺手把 codeql-action 升到 v4，并注明 build-mode 那条提示为良性 | @ACANX |
| v0.17.0 | 2026-10-08 | `G-01` 结项（两条文档提交已随 PR #9/#10 进入 `origin/dev`）；新增 `G-06` 处置 0.1.0 的裸 tag 与 `V*` 约定的不一致 | @ACANX |
| v0.18.0 | 2026-10-08 | 五项决策拍板并落地：`C-01`（JPMS 非强制，OpenJFX 优先）、`D-05`（桌面端纳入门禁 + 单独阈值 0.30→0.70）、`D-06`（Win+Linux 为承诺，macOS 只打包不测）、`D-07`（进程内直调优先）、`D-09`（需要内置工具，依赖矩阵已改） | @ACANX |
| v0.19.0 | 2026-10-08 | `D-01` 机制落地并实测（父 POM 的 javafx-* per-OS profile + 分类器依赖 + 空壳排除），状态改为 ◐ 机制已落地；§4 现状陈述同步（JavaFX 依赖已实装） | @ACANX |
| v0.20.0 | 2026-10-08 | `D-01` 结项（机制 + 描述符落地）、`D-02` 完成（桌面端启动脚本）、`D-09` 落地（`aha-tool` 已声明）、`D-08` 改为「流水线已就位」（`Release.yml` 矩阵化 + 双向自证 + 上传）；§5 制品表补桌面端便携包 | @ACANX |
| v0.21.0 | 2026-10-08 | 新增 `D-10`：桌面端便携包缺 `log4j2.xml` 与 `version.properties`（二者在 `aha-cli`，而 `cli` 与 `desktop` 不得互相依赖），含实测证据、影响与建议动作 | @ACANX |
| v0.22.0 | 2026-10-08 | `D-07` 结项（线程模型小节 + 桥接契约 + 静态扫描 + 6 例单测）、`D-10` 结项（版本号下移 `aha-common`、日志装配下移 `aha-core`，并更正早期「缺 `log4j2.xml`」的误判）、`D-04` 记为「门禁方式已定」（冒烟默认跳过）、新增 `D-11`（桌面端不读 `Aha.yaml`） | @ACANX |
| v0.23.0 | 2026-10-08 | `D-11` 结项：抽出 `AhaBootstrap`（CLI 与桌面端共用读配置/装配日志/装密钥库），桌面端窗口显示配置摘要；含单测与 CLI 端到端实测证据 | @ACANX |
| v0.24.0 | 2026-10-08 | 新增并结项 `D-12`（桌面端三栏骨架 + 折叠三条路径 + 纯逻辑 `FoldState`），遗留项记明（工具卡片 / Agent 接线 / `ToolKind` 下移） | @ACANX |
| v0.25.0 | 2026-10-08 | 新增 `F-17`（`-Djavafx.platform` 触发 `recursive variable reference` 的构建日志噪音） | @ACANX |
| v0.26.0 | 2026-10-08 | `D-12` 遗留更新：Agent 接入与 `ToolKind` 下移、供应商配置已完成；列明尚未做项（工具卡片展开/输出预览、会话列表、记忆/扩展/日志面板、`/` `@`、主题、设置、授权弹窗样式） | @ACANX |
| v0.27.0 | 2026-10-08 | 0.2 六项功能完成（工具卡片 / 会话列表 / 日志面板 / 输入区增强 / 授权弹窗 / 主题与设置）；新增 `D-13`（剩余项）与 `F-18`（已入库的合并冲突标记，已修复并加守卫） | @ACANX |
| v0.28.0 | 2026-10-08 | 新增 `aha-desktop-native`（试验性原生镜像模块）与 `DesktopNative.yml`：`N-01`~`N-04` 落地（profile 隔离、独立出包、`a.b.c.PPPPP` 版本、JDK 27 实验分支），`N-05`~`N-07` 待跟踪 | @ACANX |
| v0.29.0 | 2026-10-08 | 新增 `N-08`/`N-09`：把原生镜像经验沉淀为可复用技能，并明确「先建骨架、边做边改、达成目标才成熟」的迭代方式与四条达标判据 | @ACANX |
| `version.properties` + `AppVersion` | `aha-cli` | `aha-common`（根包；该模块「零外部依赖」约定不变） |
| picocli 版本适配 | `AppVersion.VersionProvider`（嵌套类） | `CliVersionProvider`（**仍在 cli**，避免把 picocli 带进 common） |
| 日志装配 `LoggingSetup` | `aha-cli` | `aha-core`（`log4j-core` 在该模块改 `compile` scope） |

**为什么拆成两半**：`AppVersion` 原本依赖 picocli，整体搬进 `aha-common` 会破坏其零外部依赖的约定
——所以**资源与读取逻辑**下移、**picocli 适配**留在 CLI。日志装配放 `aha-core`，让 CLI 与桌面端
共用同一份实现，而谁都不必依赖对方（`Constitution.md` 第 4 条）。

**实测证据**：`aha-common-0.1.1.jar` 内 `com/acanx/module/aha/common/version.properties` 内容为
`version=0.1.1`（资源过滤生效，非占位符）；桌面端 `init()` 即调用 `LoggingSetup.apply(null)` 并能落日志。

> **更正一则早期误判**：本条目先前写作「桌面端包缺 `log4j2.xml`」。实际上**全仓原本就没有**该文件
> ——日志装配一直是程序化的（理由见 `LoggingDesign.md`：JPMS 下 `getResources` 不搜模块路径）。
> 真正缺的是**类**，现已下移。

### D-12 ✅ 已完成（2026-10-08）：桌面端三栏骨架

**落地内容**：`DesktopShell`（菜单栏 + 左 220 / 中弹性 / 右 280 + 底 24px 状态栏）、
`ShellLayout`（尺寸与可见性规则）、`Palette`（语义色值）、`FoldState`（折叠状态机）；
`AhaDesktopApp.start` 改为装配该骨架，底栏承载状态桥接与配置摘要。

**折叠**：菜单（视图 → 折叠左栏 / 折叠右栏）、快捷键（`Ctrl+B` / `Ctrl+J`）、
栏边**常驻窄条按钮**三条路径等价；折叠后窄条仍在，鼠标用户随时能展回来
（只留快捷键会让鼠标用户折叠后无法展开）。

**测试**：`FoldStateTest`（4，含折叠→展开往返与提示文案）、`ShellLayoutTest`（4）、
`PaletteTest`（2）默认运行；冒烟测试扩展为**走真实按钮**，覆盖折叠→展开、右栏默认收起、
Enter 发送 / Shift+Enter 换行（默认跳过，需图形环境）。

**遗留**：

1. ~~接入 Agent~~ ✅ 已完成（`ChatController` + `ChatView`；Windows 真机已验证流式往返）；
2. ~~`ToolKind` 下移~~ ✅ 已完成（现位于 `aha-common`，两端共用语义）；
3. 供应商配置 ✅ 已完成（可查看/修改/保存，含校验）；
4. **尚未做**：工具卡片的折叠展开与输出预览（当前只有首行 + 结果行）、
   会话列表持久化展示、记忆 / 扩展 / 日志面板、`/` `@` 补全、暗/亮主题切换、设置面板、
   授权弹窗的自定义样式（现为系统默认 Alert）。

---

### F-17 ☐ 新增（2026-10-08）：`-Djavafx.platform=win` 时构建输出 `recursive variable reference: javafx.platform`

**现象**：在 WSL 里用 `-Djavafx.platform=win` 打 Windows 便携包时，构建日志出现两行
`[ERROR] recursive variable reference: javafx.platform`；**构建仍然成功**，产物名与内容都正确。

**影响**：目前只是日志噪音；但它可能意味着某个 `${javafx.platform}` 的插值路径不健康，
值得在 Windows 原生构建（profile 自动生效、无 `-D`）时对照确认一次是否同样出现。

**验收标准**：在 Windows 原生 `./mvnw -pl aha-desktop -am package` 输出里确认有无该行；
若有则定位到具体插件/属性并消除。

---

### F-16 ☐ 新增（2026-10-08）：Maven 4 下 verify 日志出现 10 行 `[stderr]`

**现象**：一次 `./mvnw clean verify`（Maven 4.0.0-rc-7）中共出现 10 行 `[stderr]`，
而同一提交在 Maven 3.9.11 下为 0 行——与 `F-11` 治好的「测试输出污染构建日志」同类。

**已查明来源**（2026-10-08，据 Windows CI 日志）：这些 `[stderr]` 是 **JDK 的 native-access 警告**，
由 `sqlite-jdbc` 触发，出现在 `EndToEndTest` 等真正加载 SQLite 的用例里：

```
[stderr] WARNING: java.lang.System::load has been called by org.sqlite.SQLiteJDBCLoader
                  in module org.xerial.sqlitejdbc
[stderr] WARNING: Use --enable-native-access=org.xerial.sqlitejdbc to avoid a warning ...
```

不是测试输出污染，而是 JVM 提示缺 `--enable-native-access`。

**修法（待评估）**：给 surefire 加该参数。注意不能简单写死 `argLine`——JaCoCo 的
`prepare-agent` 也通过 `argLine` 注入探针，覆盖它会让覆盖率失效；正确写法是
`<argLine>@{argLine} --enable-native-access=org.xerial.sqlitejdbc</argLine>`，
但 `-Djacoco.skip=true`（`Build.yml` 的快检查）下该属性不存在，`@{argLine}` 会原样传入而报错。
需要先验证这两种情形都能跑通再改。**CLI 的启动脚本 `bin/Aha.sh` 早已带这个参数**，
所以最终应当一致。

**验收标准**：CI 日志（`Build` 与 `Gate`）中 `[stderr]` 行数为 0。
**不要为了复现它而在本地重跑 verify**（慢检查只在 CI 跑）。

---

### D-11 ✅ 已完成（2026-10-08）：桌面端真读 `Aha.yaml`

**做法**：把「读主配置 → 装配日志 → 装密钥库回退源」抽成 `aha-core` 的 `AhaBootstrap`，
CLI 与桌面端共用（两者不得互相依赖，各写一遍必然分叉）。

| 方法 | 副作用 | 用途 |
|---|---|---|
| `load(Path)` | 无 | 纯解析，便于测试 |
| `boot()` / `boot(Path)` | **有**（替换进程级日志配置、注册静态回退源） | 入口启动 |

**失败降级**：配置不存在 → 用内置默认（不算错误，不刷警告）；YAML 破损 / 密钥库不可用 →
记 warning 继续启动（CLI 打 stderr，桌面端记日志）。顺带修掉一处旧行为：CLI 以前在读配置
失败时传 `null` 给后续流程，会让 `CliContext` 二次读取时抛异常。

**桌面端可见自证**：窗口新增一行配置摘要 `配置：<路径> · 日志级别：<级别>`（id `#aha.config`）。

**实测证据**：

- 单测（无图形环境）：`AhaBootstrapTest` 断言「配置里的 `WARN` 真的成了 log4j2 的生效级别」；
  `AhaDesktopAppConfigTest` 断言桌面端引导读到配置且摘要随之变化；
- 端到端（CLI 同一代码路径，真跑发行包）：`Level: WARN` → 指定路径日志里 DEBUG 行 **0** 条；
  `Level: DEBUG` → 同一路径 DEBUG 行 **1** 条；`File: './Log/custom.log'` → 按 CWD 解析生成；
  无 `Aha.yaml` → 落到 `~/.aha/Log/AHA.log`。

**遗留**：桌面端尚未把配置用于界面行为（会话模型、工具开关等随真实界面接入）。

---

## 5. GraalVM 原生编译（0.2+ 评估）

> **前置共识**：`DesktopDesign.md` §3 已确立
> 「桌面端 WebView 不支持 native-image，native-image 流水线仅覆盖 CLI + Core + Tools」。
> 本节条目**全部未开工**，多数结论来自外部知识（⚠️）。

| 编号 | 事项                                                   | 类型   | 证据 | 优先级 | 状态      | 落地文档                                |
| ---- | ------------------------------------------------------ | ------ | ---- | ------ | --------- | --------------------------------------- |
| E-01 | `ModuleLayer` 与 native-image 的 closed-world 根本冲突 | 待决策 | ✅    | P0     | ⏸ 待决策  | `ExtensionSystemDesign.md`、`BuildSpec.md` |
| E-02 | `sqlite-jdbc` 的 JNI native library 嵌入               | 风险   | ⚠️    | P1     | ☐ 未完成  | `BuildSpec.md`                          |
| E-03 | Jackson 3 反射配置                                     | 风险   | ⚠️    | P1     | ☐ 未完成  | `BuildSpec.md`                          |
| E-04 | `ServiceLoader` 需资源配置                             | 风险   | ⚠️    | P1     | ☐ 未完成  | `BuildSpec.md`                          |
| E-05 | picocli 反射配置                                       | 风险   | ⚠️    | P2     | ☐ 未完成  | `BuildSpec.md`                          |
| E-06 | JLine 终端能力探测依赖 native 组件                     | 风险   | ⚠️    | P2     | ☐ 未完成  | `BuildSpec.md`                          |
| E-07 | Log4j2 在 native-image 下的兼容性                      | 风险   | ⚠️    | P2     | ☐ 未完成  | `BuildSpec.md`                          |
| E-08 | `java.net.http` 的 TLS/SSL 配置                        | 风险   | ⚠️    | P2     | ☐ 未完成  | `BuildSpec.md`                          |
| E-09 | 虚拟线程在 native-image 的支持现状                     | 调研   | ⚠️    | P2     | ☐ 未完成  | `Constitution.md` 第 5 条               |
| E-10 | 覆盖率门禁在 native 产物下不适用                       | 待决策 | ✅    | P2     | ⏸ 待决策  | `BuildSpec.md` §6/§8、`TestingSpec.md`  |
| E-11 | 发行布局（bin+lib）与单文件产物冲突                    | 待决策 | ✅    | P2     | ⏸ 待决策  | `BuildSpec.md` §7                       |
| E-12 | `TODO.md` 文件名不合规（原 D-05 自身）                 | 规范偏差 | ✅  | P3     | ⏸ 待决策  | `DocumentationSpec.md` 第 1 节          |

### E-01 ⏸ 待决策（本节最关键）

**问题**：native-image 在**构建期**静态分析可达代码（closed-world），而 `ModuleLayer`
在**运行时**动态创建模块层。`Constitution.md` 第 5 条把「扩展运行时 = `ModuleLayer` +
`ExtensionRuntime`」列为**不可更换**（✅ 已核实，第 145 行）。

**冲突（✅ 架构事实已核实）**：

- `ModuleLayer` 定义的类在构建期不存在 → native-image 无法预编译
- 因此 **native 产物中扩展系统无法以 `ModuleLayer` 形态工作**

**难点**：`aha-core` **包含 `core.extension` 包**（`ExtensionManager`、`RegistrationTracker`），
只要在类路径上就会被可达性分析触及。

**残留动作（需拍板）**：

1. 明确 native 产物中**扩展功能的降级策略**：完全禁用／仅支持编译期静态注册／不发布
2. 若选择「禁用」，确认 `core.extension` 能否被 native-image 分析**排除**，
   或需在构建期以 profile 剔除
3. 在 `ExtensionSystemDesign.md` 中**显式记录**该限制
4. 评估 `Constitution.md` 第 5 条是否需补充 native-image 例外条款（涉及第 8 条修订程序）

> 若此冲突无法优雅解决，可能影响「native-image 是否值得做」的整体判断。
> **建议先做本条决策，再评估其余条目。**

### E-02 ~ E-09 ☐ 未完成

**复核（2026-10-07）**：全部处于调研/未开工状态，无仓库内变化。

**补充（E-03）**：父 POM `jackson.version` 已升至 `3.2.3`；`aha-core/module-info.java`
的 3 处 `opens ... to tools.jackson.databind` 仍存在（reflect 面未变）。
Jackson **3.x** 的 GraalVM metadata 成熟度仍需实测。

**补充（E-04）**：与 C-02 交叉——`META-INF/services` 双声明在 native-image 下
**由冗余变保险**，决策 C-02 时应一并考虑。

### E-10 ⏸ 待决策

**复核（✅ 已核实）**：`BuildSpec.md` §6 门禁与 §8「唯一验收标准」均未涵盖 native 产物。

**残留动作**：为 native 产物定义独立冒烟测试；在 §8 补记验收标准，
明确「JaCoCo 门禁不覆盖 native 产物」；并明确 native 构建**是否绑定 `verify`**
（建议独立流水线）。

### E-11 ⏸ 待决策

**复核（✅ 已核实）**：`BuildSpec.md` §7 仍规定「发行包固定为 JPMS 模块路径目录
（`dist/bin` + `dist/lib`）」；native-image 单文件产物与之不兼容。

**残留动作**：定义**发行矩阵**并写入 `BuildSpec.md` §7：

| 发行形态                        | 目标用户       | 产物            | 状态      |
| ------------------------------- | -------------- | --------------- | --------- |
| JPMS 模块路径目录（`dist/`）    | 需 JVM、可调优 | `aha-<版本>-cli.zip` | ✅ 已实现 |
| 桌面端便携包（按平台）          | 需 JDK 25      | `aha-desktop-<版本>-<系统>-<架构>.zip` | ✅ 流水线已就位 |
| native-image 单文件             | 免 JVM、启动快 | 平台可执行文件  | ☐ 未开工  |
| `jpackage` 安装包（0.2 桌面端） | 普通用户       | MSI / DEB / DMG | ☐ 未开工  |

### E-12 ⏸ 待决策（新增条目）

**现象**：文件名 `TODO.md` 与 `DocumentationSpec.md` 第 1 节 PascalCase 规范不符
（保留名单不含 `TODO`）。

**依据（✅ 已核实）**：`DocumentationSpec.md` 第 1 节保留名单；
`bin/CheckDocs.py` **不检查文件名**，因此**不阻塞 CI**。

**建议动作**：三选一——

1. 保持 `TODO.md`（暂存区生命周期短，消化完即删）——**当前选择**
2. 改名为 `Todo.md`，同步修订 `AGENTS.md`、`PLAN.md` 的链接与 `DocumentationSpec.md`
   的保留名单说明
3. 把 `TODO` 加入 `DocumentationSpec.md` 第 1 节保留名单（需走规范变更程序）

---

## 6. 记忆能力（0.6 起）

现状：`memory` 表已建、`storeMemory` / `recall` 接口已定义，但**全仓无调用方**——模型既不写也不读。
详见 [MemoryStorageDesign.md](Design/MemoryStorageDesign.md) 第 6~8 节。

### 6.1 推进顺序（与载体选型无关，可先做）

| 序 | 条目 | 说明 | 状态 |
|---|---|---|---|
| 1 | `storeMemory` 加 upsert | 现状为纯 `INSERT`，同一 key 写两次会产生重复行 | ☐ 未完成 |
| 2 | 作用域改为项目级 | **已定**：`~/.aha/Project/<项目ID>/Memory/`，项目 ID 规则已实现（`ProjectId`） | ✅ 已完成 |
| 3 | 记忆工具（模型侧）+ `/memory` 命令（用户侧）+ 候选区 | **建议从这里开始**：能立刻验证记录是否真的可用 | ☐ 未完成 |
| 4 | `Memory.ModelWrite` 接入配置与权限 | `off` / `candidate` / `direct`；受 `Tools.Enabled` 与 `PermissionPolicy` 双重管辖 | ☐ 未完成 |
| 5 | 手动 `/memory curate`（整理） | 去重合并、升降级、冲突检测，**必须可回滚** | ☐ 未完成 |
| 6 | 载体与向量（RAG） | 与 0.6 一起定；实测依据见 6.3 | ⏸ 待决策 |

**复核依据（✅ 已核实，2026-10-07）**：

- 序 1：`SqliteMemoryStore.java:229` 仍为 `INSERT INTO memory(...)`，**无 upsert**
  （对比 `session` 表已用 `INSERT OR REPLACE`）
- 序 2：`CHANGELOG.md` 已记载「项目级记忆位置与项目 ID：`~/.aha/Project/<项目ID>/Memory/`，
  项目 ID 由项目根绝对路径推导」→ **已完成**
- 序 3：CLI 现有斜杠命令为 `/help` `/config` `/context` `/session` `/model` `/clear`
  `/tool` `/compact` `/autocompact` `/exit` `/memory`，**无 `/memory`**

### 6.2 写入策略（已定）

| 级别 | 谁写 | 是否确认 |
|---|---|---|
| 项目记忆 | 模型可**直接写** | 默认不确认 |
| 候选区 | 模型可写，整理流程也可产生 | 按配置 |
| **长期记忆** | **只能由整理流程筛选提取生成** | 产出后 review |

原则：**先记录存档，后利用**。未经检验的召回比没有记忆更糟。

### 6.3 载体选型（未定，⏸ 待决策）

候选：表 / MD 文件 / 混合（MD 为真源 + 表为可重建派生索引）。实测依据：

| 场景 | 规模 | 实测 |
|---|---|---|
| 2,000,000 行文本，`LIKE` 全表扫 | 291 MB | 41 ms |
| 50,000 条 × 384 维向量，暴力相似度扫描 | 98 MB | **100 ms/次，且随条数线性增长** |
| 5,000 个小 MD 文件，逻辑 vs 实际占用 | 543 KB → 20 MB | **块浪费 37 倍** |

结论：瓶颈不在文本而在向量；MD 的代价不在读慢而在小文件管理。

### 6.4 跨环境共享（⏸ 待决策）

**默认行为**：Windows 与 WSL 的路径不同，会推导出两个项目 ID，
**默认视为两个独立项目**。这是刻意保留的默认，不做隐式归一化。

**待补机制**：允许**显式配置**共享或复用。三方案（显式项目 ID / 别名表 / 共享目录）
与代价见 [MemoryStorageDesign.md](Design/MemoryStorageDesign.md) 8.6；
**已由 [PLAN.md](PLAN.md) §3 登记为「暂不实施」**（与载体选型一同推进）。

### 6.5 其他待定

- 路径大小写：Windows 上 `E:\GitRepo` 与 `e:\gitrepo` 会得到不同 ID
  （不统一是为了与 Claude Code 一致）
- 用户级与项目级记忆的合并去重
- 候选区的载体：`Candidate/` 子目录，还是同一索引的 `status` 字段
- 容量与淘汰策略
- 会话历史的查看与统计（`aha session list`、`/stats`、`aha stat`）
  —— 已由 [PLAN.md](PLAN.md) §2 关联（需先完成用量落库）

---

## 7. 待决策项汇总

以下条目**需要人来决策**，技术上无唯一正解。建议按优先级依次拍板：

**只有 4 项与 0.1 相关**，其余属 0.2+、桌面端或 native-image 路线。

> **2026-10-08 已拍板 5 项**（详见各条目正文）：
>
> | 编号 | 决策 |
> |---|---|
> | `C-01` | JPMS **非强制**；优先 OpenJFX，必要时 JPMS 让路 |
> | `D-05` | 桌面端**纳入门禁 + 单独阈值**，0.2 初期 0.30 → 收尾向 0.70 对齐 |
> | `D-06` | 支持承诺 **Windows + Linux**；macOS **只打包、不测试** |
> | `D-07` | **进程内直调优先**，本地 HTTP + WebView 降为兜底 |
> | `D-09` | 桌面端**需要内置工具**（依赖矩阵已补 `tool ← desktop`） |
这 4 项的建议如下——**仅为建议，仍需拍板**：

| 编号 | 决策问题 | 建议 | 理由 |
| ---- | ---- | ---- | ---- |
| H-01 | 启动标志默认风格 | **维持像素风**（现状 `auto` = 像素风优先） | 它挂在能力探测之后，旧 CMD / 16 色会自动降级为线框风，降级路径已就位；像素风也正是本项目的辨识度所在 |
| E-12 | `TODO.md` 是否改名 | **维持 `TODO.md`** | 这是社区通用名；改名会让贡献者、脚本与 DevLog 的引用一起失配，收益只是「规范化」。更省事的做法是在 `DocumentationSpec.md` §1 为它记一条命名例外 |
| A-08 | `PLAN.md` / `TODO.md` / `Dbsx.txt` 是否收进 `Docs/AHA/` | **维持现状** | `Docs/AHA/` 的定位是「权威设计文档」；`PLAN.md`/`TODO.md` 是活的状态台账，放根目录更显眼；`Dbsx.txt` 是用户原始输入，宜先定去留（§8.3.4）再谈归档 |
| F-07 | `.ps1` 是否纳入检查、`Script/` 与 `bin/` 是否合并 | **纳入检查 + 合并进 `bin/`** | 脚本规约要么全查、要么别立；两个目录职责重叠会长期制造「改了这个忘那个」；合并后 `CheckScripts.py` 只需覆盖一个目录 |

| 顺序 | 编号 | 决策问题                                                     | 影响范围                     | 状态     |
| ---- | ---- | ------------------------------------------------------------ | ---------------------------- | -------- |
| 1    | E-01 | native 产物中扩展系统的降级策略？`ModuleLayer` 与 native-image 冲突如何处置？ | 决定 native-image 是否值得做 | ⏸ 待决策 |
| 2    | C-01 | JPMS 的投入产出错配是否接受？扩展路线图推迟时如何处理？      | 架构根基                     | ✅ 已决策（2026-10-08） |
| 3    | E-11 | 发行矩阵如何定义？三种形态是否并存？                         | 打包与文档全局               | ⏸ 待决策 |
| 4    | E-10 | native 产物的验收标准与 CI 归属？                            | 质量基线                     | ⏸ 待决策 |
| 5    | C-02 | `ServiceLoader` 双声明：维持／删除／加校验？                 | 维护成本（与 E-04 相关）     | ⏸ 待决策 |
| 6    | D-06 | 桌面端目标平台是否含 macOS？                                 | CI 与打包                    | ✅ 已决策（2026-10-08） |
| 7    | D-05 | 桌面端覆盖率：纳入门禁／单独阈值／永久排除？                 | 质量基线                     | ✅ 已决策（2026-10-08） |
| 8    | D-07 | 桌面端技术选型：进程内直调（8.1）vs 本地 HTTP + WebView（8.2）？ | 0.2 全部实现                 | ✅ 已决策（2026-10-08） |
| 9    | E-12 | `TODO.md` 文件名是否改名？                                   | 文档规范一致性               | ⏸ 待决策 |
| 10   | D-09 | 桌面端是否需要内置工具（引入 `aha-tool`）？                  | 依赖矩阵与 `Constitution.md` | ✅ 已决策（2026-10-08） |
| 11   | A-08 | `Docs/` 根下的 `PLAN.md` / `TODO.md` / `Dbsx.txt` 是否收进 `Docs/AHA/`？ | 文档存放规范 | ⏸ 待决策 |
| 12   | F-07 | `Script/PowerShell/CountJavaLoc.ps1` 是否纳入检查、`Script/` 与 `bin/` 是否合并？ | 检查覆盖面与目录约定 | ⏸ 待决策 |
| 13   | H-01 | 启动标志默认风格：像素风（现状）还是线框风？ | 首屏观感 | ⏸ 待决策 |
---

## 8. 落地去向映射

确认后，各条目应合并到以下文档。**建议按此顺序推进**（先易后难、先无争议后有争议）：

### 阶段一：无争议的缺陷修复

| 条目 | 目标文档                | 变更性质   | 状态     |
| ---- | ----------------------- | ---------- | -------- |
| A-01 | `AGENTS.md`             | 修正命令   | ✅ 已完成 |
| A-02 | `CHANGELOG.md`          | 合并段落   | ✅ 已完成 |
| A-05 | `ExtensionSystemDesign.md` | 章节重编号 | ✅ 已完成 |
| A-06 | `ExtensionSystemDesign.md`、`TestingSpec.md`、`ExtensionManager.java` | 统一阶段编号 | ✅ 已完成 |
| A-07 | `BuildSpec.md` §6       | 核对引用链 | ◐ 进行中 |
| F-08 | `Build.yml`、`BuildSpec.md` §8.1、[DevLog-20261007-20.md](DevLog/DevLog-20261007-20.md) | 恢复矩阵作业名；立「作业名是分支保护的契约」 | ✅ 已修 |

### 阶段二：规范补充（需评审）

| 条目 | 目标文档                                 | 变更性质                      | 状态     |
| ---- | ---------------------------------------- | ----------------------------- | -------- |
| C-01 | `Constitution.md` 第 4 条、`ModuleConvention.md` | 补充 JPMS 存在理由与复核点 | ☐ 未完成（待决策） |
| C-02 | `ModuleConvention.md`              | 补充 `ServiceLoader` 声明要求 | ☐ 未完成（待决策） |
| C-03 | `BuildSpec.md` §7                  | 补充依赖治理手段（若采纳）    | ☐ 未完成 |
| C-04 | `BuildSpec.md` §7                  | 补充 `jlink` 禁令解除条件     | ☐ 未完成 |
| D-05 | `TestingSpec.md` §3                | 补充 Desktop 覆盖率策略       | ☐ 未完成（待决策） |
| E-10 | `BuildSpec.md` §6/§8               | 补充 native 产物验收标准      | ☐ 未完成（待决策） |
| B-03 | `Constitution.md` 第 5 条、`BuildSpec.md` §7 | 选型清单改为主版本线；立「版本单一来源」规则（实测记录除外） | ✅ 已完成 |
| A-08 | `DocumentationSpec.md` §2 | 明确 `Docs/` 根下暂存区文件的存放位置 | ⏸ 待决策 |
| F-07 | `BuildSpec.md` §8.1、`DocumentationSpec.md` §2 | 明确 `.ps1` 是否纳入检查、`Script/` 目录去留 | ⏸ 待决策 |
| E-12 | `DocumentationSpec.md` 第 1 节     | 文件名保留名单（若采纳）      | ☐ 未完成（待决策） |

### 阶段三：设计与实现（0.2 前）

| 条目                    | 目标文档                                                | 变更性质                     | 状态     |
| ----------------------- | ------------------------------------------------------- | ---------------------------- | -------- |
| B-01 / B-02             | `AgentEngine.java`、`AgentServiceDesign.md`             | 明确契约 + 补测试            | ☐ 未完成 |
| D-01 ~ D-04、D-08       | `DesktopDesign.md`、`DesktopDesign` 打包/测试小节、`Build.yml` | 新增线程模型、打包、测试小节 | ◐ 骨架已就位 |
| D-06                    | `DesktopDesign.md` §2/§3、`Build.yml`                   | 平台清单对齐                 | ◐ 已决策，待回填 |
| D-07                    | `DesktopDesign.md`（或 `GUIDesign.md` §8 升格）         | 线程模型小节                 | ✅ 已完成（`DesktopDesign.md` §6） |
| D-09                    | `Constitution.md` 第 4 条、`ModuleConvention.md` §2     | 依赖矩阵补 tool 边           | ✅ 已落地（矩阵 + pom 均已改） |
| E-02 ~ E-09             | `BuildSpec.md`（新增 native 章节）                      | 待验证后登记                 | ☐ 未完成 |
| E-11                    | `BuildSpec.md` §7                                       | 发行矩阵                     | ☐ 未完成（待决策） |
| 6.1 序 1、3、4、5       | `MemoryStorageDesign.md`、`aha-core`、`aha-cli`         | 记忆写入与命令落地           | ☐ 未完成 |

### 阶段四：本文件处置

| 条目               | 动作                                                         | 状态     |
| ------------------ | ------------------------------------------------------------ | -------- |
| E-12（本文件自身） | 全部条目消化后，将本文件标记为「已废弃」或删除；若长期保留，改名 `Todo.md` 以符合 `DocumentationSpec.md` 第 1 节 | ⏸ 待决策 |

---

## 9. 复核结论汇总（2026-10-07）

本轮对全部条目逐条对照仓库现状复核，结论如下。

> **本小节是 2026-10-07 当日快照，不随后续变动更新。** 之后新增的条目
> （`B-04`~`B-07`、`F-01`~`F-06`）与状态变化见第 2 节汇总表与第 10 节；
> 例如 `B-03` 已于同日收口为 ✅ 已解决，本快照中的「◐ 进行中」是当时的记录。

### 9.1 统计

| 状态     | 条目数 | 编号                                                         |
| -------- | ------ | ------------------------------------------------------------ |
| ✅ 已完成 | **7**  | A-01、A-02、A-03、A-04、A-05、A-06、6.1-序 2                  |
| ◐ 进行中  | **4**  | A-07、B-03、D-07                                             |
| ⏸ 待决策  | **9**  | C-01、C-02、D-05、E-01、E-10、E-11、E-12、6.1-序 6、D-09（含决策前置） |
| ☐ 未完成  | **16** | B-01、B-02、C-03、C-04、D-01~D-04、D-06、D-08、D-09、E-02~E-09、6.1-序 1/3/4/5 |

### 9.2 本轮新发现

| 项 | 说明 |
|---|---|
| **D-09** | `aha-desktop` 未依赖 `aha-tool`，桌面端当前形态**不具备任何工具能力**；`Constitution.md` 第 4 条依赖矩阵亦未覆盖此边 |
| **B-03 版本漂移** | ✅ 已解决：文档改为主版本线，并在 `BuildSpec.md` §7 立「版本单一来源」规则防复发 |
| **A-07 定性修正** | `TestingSpec.md` 内部编号**无重复**，问题收窄为 `BuildSpec.md` §6 的单一引用链 |
| **E-12** | 原文档中「见 D-05」的文件名合规问题**编号错误**（D-05 是覆盖率排除项），现独立为 E-12 |

### 9.3 本轮已处理的文档卫生问题

| 问题 | 处理 |
|---|---|
| 文档正文后附有**完整重复副本** | 已删除，仅保留单一正文 |
| 文末误粘贴**会话日志与 `file-write` 权限报错**（含 `session=c1624777-…`、工具入参全文） | 已删除 |
| 全部条目表**无状态列**，已完成项与未开始项无法区分 | 已为所有表格新增「状态」列 |
| `PLAN.md` §4 登记的「`TODO.md` 重复内容清理」 | 本条即对该项的落实 |

---

## 10. CI、门禁与工程效能

> 本节于 2026-10-07 追加在文件末尾，而非按编号插在前部：§0.3 与 §7 之间已有
> 「见第 7 节」这类内部引用，插号会连带出错。

| 编号 | 事项 | 类型 | 证据 | 优先级 | 状态 | 落地文档 |
| ---- | ---- | ---- | ---- | ------ | ---- | -------- |
| F-01 | 慢检查（完整 verify、覆盖率、文档、技能、脚本、重复率）作为每次 push 的卡点，反馈环路达分钟级且频繁阻塞 | 风险 | ✅ | P1 | ✅ 已完成 | `BuildSpec.md` §8.1、`Build.yml`、`Gate.yml` |
| F-02 | 门禁未校验「实际使用的 Maven 版本」是否等于 Wrapper 固定版本（结论靠推断） | 风险 | ✅ | P1 | ✅ 已完成 | `BuildSpec.md` §3、`Gate.yml` |
| F-03 | 无 Maven 3.9.x 独立兼容验证（基线混在快速矩阵里，失败原因不可辨；且依赖 runner 预装版本） | 风险 | ✅ | P1 | ✅ 已完成 | `BuildSpec.md` §4、`Compat.yml` |
| F-04 | 无重复代码率度量与阈值 | 风险 | ✅ | P2 | ✅ 已完成 | `BuildSpec.md` §8.1、`bin/CheckDuplication.py` |
| F-05 | CI 矩阵 `fail-fast` 在一条腿失败时取消其余腿，掩盖平台差异 | 缺陷 | ✅ | P1 | ✅ 已完成 | `Build.yml`、`TestingSpec.md` §5.1 |
| F-06 | macOS 无支持边界声明，容易被误读为「已支持」 | 风险 | ✅ | P3 | ✅ 已完成（已拍板） | `BuildSpec.md` §4.1、`Build.yml` |
| F-07 | `Script/PowerShell/CountJavaLoc.ps1` 未纳入 `CheckScripts.py`（`.ps1` 不在检查范围）；`Script/` 与 `bin/` 目录职责重叠 | 待决策 | ✅ | P3 | ⏸ 待决策 | `BuildSpec.md` §8.1、`DocumentationSpec.md` §2 |
| F-08 | 给必需腿补 `optional` 矩阵键改变了作业名（`build (windows-latest, wrapper)` → `…, false)`），分支保护的必需检查再也匹配不上，PR 永久停在 `Expected — Waiting for status to be reported` | 缺陷 | ✅ | P1 | ✅ 已修 | `Build.yml`、`BuildSpec.md` §8.1、[DevLog-20261007-20.md](DevLog/DevLog-20261007-20.md) |
| F-09 | 覆盖率门禁**静默**：`jacoco:check` 通过时不打印任何百分比，日志上与「没配门禁」无法区分，被质疑「何来的门禁」 | 缺陷 | ✅ | P2 | ✅ 已修 | `Gate.yml`、`bin/ReportCoverage.py`、`TestingSpec.md` §3.1、[DevLog-20261007-21.md](DevLog/DevLog-20261007-21.md) |
| F-10 | 工具层的**已知拒绝**（`UNKNOWN_TOOL` / `TOOL_DISABLED` / `PERMISSION_DENIED`）被记成 `ERROR` 并附完整堆栈，违反 `LoggingDesign` §4「预期业务结果记 INFO」；Console 阈值是 ERROR，于是模型偶尔叫错工具名就会在终端刷出堆栈 | 规范偏差 | ✅ | P1 | ✅ 已修 | `AgentEngine.executeTool`、`LoggingDesign.md` §4、`ToolSystemDesign.md` |
| F-11 | 测试自身产生的输出把构建日志打满（CI 上 80 余行 `[stdout] ... at com.acanx...` 堆栈 + 50 行授权提示），真正的失败被淹没 | 缺陷 | ✅ | P2 | ✅ 已修 | `aha-core/src/test/resources/log4j2-test.xml`、`ConsoleToolApproverTest`、`TestingSpec.md` §5.1 |
| F-12 | PR #6 被以**单父提交**重新落地（内容重放、不是真合并），使 `dependa` 与 `dev` 成为内容重叠的两条平行线，PR #8 永久 `mergeable_state=dirty` | 工程效能 | ✅ | P1 | ✅ 已修 | `dependa` 合并提交 `06c6121`、`ReleaseProcess.md` §4、`DevLog/DevLog-20261007-22.md` |
| F-13 | CI 在 JaCoCo 插件依赖解析上失败（`Could not find artifact ... in central`），而三个 artifact 在 Central 实测 200——当次就没要下来；`Gate` 是必需检查，网络抖动即把 PR 卡红 | 工程效能 | ✅ | P1 | ✅ 已修 | `.github/actions/maven-run/action.yml`、四个工作流、`BuildSpec.md` §8.1、`DevLog-20261007-23.md` |
| F-14 | `.github/**/*.yml` 没有任何本地检查：`bin/CheckScripts.py` 只覆盖 `.bat`/`.cmd`/`.sh`/`.py`，工作流语法写错只能等 GitHub 判，反馈环路长 | 工程效能 | ⏸ | P2 | ⏸ 待决策 | `bin/CheckScripts.py` |
| F-15 | 文档标题/表行**编号重复**只能靠人工看：本轮 `PLAN.md` `A-09`/`A-10` 与两份 `Design/` 文档的重号都是事后肉眼发现 | 工程效能 | ⏸ | P3 | ⏸ 待决策 | `bin/CheckDocs.py` |

### F-01 ✅ 已完成（2026-10-07）：检查分层 + 定期扫描

**现象（✅ 已核实）**：完整 `clean verify`（含覆盖率门禁与打包）在本项目约需数分钟，
再叠加文档、技能、脚本与重复率检查，每次写完一个特性都要付这个代价；
这类检查的价值在「合入前拦住」，而非「每次改动都跑一遍」。

**处理**：按耗时分层，慢检查整体移出「每次改动」路径。

| 层 | 工作流 | 触发 | 内容 |
| --- | --- | --- | --- |
| 快检查 | `Build.yml` | 每次 `push` / `pull_request` | 编译 + 单元测试（`clean test -Djacoco.skip=true`） |
| 门禁 | `Gate.yml` | 合入 `main` / `release/**`、**每周定期**、手动、发布前 | 完整 verify（覆盖率 ≥ 70%）+ 文档 / 技能 / 脚本 / 像素标志 / 重复率 |
| 兼容性 | `Compat.yml` | 与门禁相同时机 | Maven 3.9.x 完整 verify |

**定期扫描（异步）**：`Gate.yml` 增加 `schedule`（每周一 03:00 UTC）。要拦的是
「与开发动作无关的漂移」——Dependabot 升级依赖、runner 镜像变化、外部规范演进。

**残留动作**：`main` 分支保护需把 `Gate` 与 `Compat` 设为**必需检查**
（GitHub 仓库设置项，工作流文件里写不了）。未配置时它们只是「跑给人看」。
`schedule` 仅在默认分支生效，合入 `main` 后才会开始定期触发。

### F-02 ✅ 已完成（2026-10-07）：门禁断言实际使用的 Maven 版本

**现象**：`Gate.yml` 跑 `./mvnw`，wrapper 的 `distributionUrl` 确实固定为 4.0.0-rc-7，
但这条保证只存在于「读一眼配置文件」的推断里——runner 上恰好存在别的 `mvn`、
或脚本被改走系统 Maven，日志里都看不出来。

**处理**：门禁第一步从 `.mvn/wrapper/maven-wrapper.properties` **反推期望版本**，
与 `./mvnw -v` 的实际输出比对，不一致即失败。刻意不在工作流里写版本字面量：
Dependabot 升级 wrapper 后自动跟随，不形成两处口径。

### F-03 ✅ 已完成（2026-10-07）：Maven 3.9.x 兼容性独立工作流

**处理**：新增 `Compat.yml`。三条设计决定：

1. **独立工作流**——目标运行时（Maven 4）与兼容基线（3.9.x）失败原因不同，
   混在一个矩阵里一次失败要花时间判断是哪一版的问题；
2. **显式固定补丁版本**并从 Maven Central 获取，不用 runner 预装的 `mvn`
   （镜像会变，兼容性结论就不可复现），跑之前先断言 `mvn -v` 确为 3.9.x；
3. **跑完整 `clean verify`**——兼容性要验的是 POM 解析、插件解析与打包全链路。

`Release.yml` 的发布前置改为 `needs: [gate, compat]`。

### F-04 ✅ 已完成（2026-10-07）：重复代码率检查

**处理**：此前**没有任何重复率度量**。补上工具链：

| 项 | 规定 |
| --- | --- |
| 工具 | PMD CPD（`./mvnw pmd:cpd`），版本由父 POM 的 `pmd.plugin.version` 固定，交由 Dependabot 跟踪 |
| 最小 token 数 | 100（`<minimumTokens>`，短于此时不计为重复） |
| 判定 | `bin/CheckDuplication.py`，默认阈值 **2.0%** |
| 口径 | 重复行数 = Σ 每个 duplication 块 `(出现次数 − 1) × 块行数`；总行数 = 各模块 `src/main/java` 下 `*.java` 的物理行数；只统计主源码 |
| 实测（0.1.0） | 合计 **0.40%**（74 / 18633 行），最高模块 `aha-core` 1.05% |

报告缺失时脚本**直接失败**而非静默跳过，否则 CI 上「没跑」会被误读成「通过」。

### F-05 ✅ 已完成（2026-10-07）：矩阵不再 fail-fast

**现象（✅ 已核实）**：PR #6 的 Windows 腿一失败，GitHub 立即取消 Linux 两条腿，
页面上只看到「第一条红」，掩盖了「另一个平台究竟是什么结果」——排查因此多绕很久。

**处理**：`Build.yml` 的矩阵加 `fail-fast: false`，并把该教训写入 `TestingSpec.md` §5.1。

### F-06 ✅ 已完成（2026-10-07，已拍板）：macOS 仅作演示与可选

**处理**：新增 `macos-latest` 腿，以矩阵的 `optional` 标记驱动 `continue-on-error`：
失败只标注该腿自身，不使整体构建失败。三条必需腿（Windows、Linux wrapper、
Linux system）不变。规范写明两条边界：**不能用它的通过宣称已支持 macOS**，
**不能用它的失败判定构建失败**。将来真要支持时，只需把 `optional` 改为 `false`。

---

### F-08 ✅ 已修（2026-10-07）

**现象（✅ 已核实）**：PR 的合并框里三条必需检查显示
`Expected — Waiting for status to be reported`，看起来像 CI 卡住，实际是**名字对不上**。

**取证**：抓取 Actions 运行页的作业标签，与分支保护要求的名字逐条比对：

| 分支保护要求 | 实际上报 |
| --- | --- |
| `build (windows-latest, wrapper)` | `build (windows-latest, wrapper, false)` |
| `build (ubuntu-latest, wrapper)` | `build (ubuntu-latest, wrapper, false)` |
| `build (ubuntu-latest, system)` | `build (ubuntu-latest, system, false)` |

**根因**：为消除「未定义矩阵键」的歧义，给每条腿都补了 `optional: false`。GitHub 会把矩阵的
**全部键值**拼进作业名，于是名字多出 `, false`；而必需检查严格按名字匹配，匹配不上就永远
处于 Expected。

**处理**：删掉 `optional` 键，可选腿改为按 `matrix.os == 'macos-latest'` 判定
（不新增矩阵键），作业名恢复原样。**无需改动分支保护**。

**详细复盘**：见 [DevLog-20261007-20.md](DevLog/DevLog-20261007-20.md)（含取证对照表与自查命令）。

**教训**：作业名是**对外契约**，不是内部细节。已写入 `BuildSpec.md` §8.1：
必需腿不得增删矩阵键；`Gate.yml` / `Compat.yml` 的 job 级 `name:` 同样是检查名，
改名必须同步更新分支保护与本文件 `G-02`。

---

### F-09 ✅ 已修（2026-10-07）

**现象**：`Gate.yml` 声称含覆盖率门禁，但 `./mvnw clean verify` 日志里只有
`Loading execution data file` 与 `Analyzed bundle '…' with N classes`，**没有任何百分比**，
因此被合理质疑「看不到数据，何来的门禁」。

**核实（两步，缺一不可）**：

1. **配置**：规则挂在 `check` goal（不是 `report`——挂错 goal 会得到「只出报告不拦人」的假门禁）、
   绑定 `verify`、`BUNDLE`/`LINE`/`COVEREDRATIO` = 0.70，且声明在 `<build><plugins>`
   而非仅 `pluginManagement`；
2. **行为**：临时把阈值改成 `0.99` 跑 `./mvnw -pl aha-common verify`，得到
   `Rule violated for bundle aha-common: lines covered ratio is 0.90, but expected minimum is 0.99`
   → `BUILD FAILURE`。随后恢复 `pom.xml` 并逐字节核对。

**结论**：门禁在且会拦，问题在**不可自证**。

**处理**：新增 `bin/ReportCoverage.py`（读 CSV 输出各模块与合计；阈值读自 `pom.xml`，
不在脚本里复制），`Gate.yml` 在 `verify` 之后执行它——**能跑到该步即门禁已通过**，
数字同时留在日志里。判定仍归 `jacoco:check` 独家所有，脚本只报数。

**教训**：静默的门禁与不存在的门禁不可区分；凡不出声的检查都要把实测值与标准写进日志。
已写入 `BuildSpec.md` §8.1「门禁必须自证（强制）」。

---

### F-10 / F-11 ✅ 已修（2026-10-07）

**现象**：CI 日志里 `AgentEngineStreamTest.streamHandlesToolFailureGracefully` 打出
`工具执行异常 session=s1 tool=missing-tool` 与 80 余行堆栈，看起来像失败，实际该用例通过。
追问「为何依然会报错」时定位到两个独立问题。

**F-10（规范偏差）**：`AgentEngine.executeTool` 的 `catch` 分支对所有 `RuntimeException`
一律记 `ERROR` 并附堆栈。但 `ToolRegistry` 抛出的三个错误码——`UNKNOWN_TOOL`、
`TOOL_DISABLED`、`PERMISSION_DENIED`——都属于**预期内的业务结果**，
`LoggingDesign.md` §4 早已规定这类情况记 `INFO`（理由：Console 阈值是 ERROR，
记 ERROR 会打断对话）。而且回灌给模型的文本也带上了堆栈，白占 token。

**修法**：按类型分流——`ToolExecutionException`（工具层的已知拒绝）走 `INFO` 且不带堆栈；
其余未预期异常维持 `ERROR` + 堆栈。结果文本同样分流：已知拒绝只给原因。

**F-11（测试输出污染）**：测试产生两类输出，都会灌进构建日志——
① **日志**：测试故意触发错误路径，生产代码按规范记 ERROR + 堆栈；
② **直接写 stdout 的交互提示**：`ConsoleToolApprover` 的授权询问（50 行）。
分别处理：`aha-core` 新增 `src/test/resources/log4j2-test.xml`（console 关闭、写
`target/test-logs/AHA-test.log`）；`ConsoleToolApproverTest` 捕获 `System.out/err`
并顺势断言提示内容（原先只是把提示喷到日志，什么也没验证）。这是**出口**的调整，不是级别调整。
于是构建日志被刷成堆栈墙。新增 `aha-core/src/test/resources/log4j2-test.xml`：
console 关闭、日志写 `target/test-logs/AHA-test.log`。这是**出口**的调整，不是级别调整。

**验证（实测）**：构建日志 `[stdout]` 行 **0** / 堆栈行 **0** / 授权提示 **0**（修复前约 130 行）；
日志文件里「未知工具」16 次且为 `INFO`，另有 8 条 `ERROR`（新测试故意触发的未预期异常，
堆栈保留）。新增对照测试 `streamKeepsStackTraceForUnexpectedToolBug` 钉住另一侧。

---

### F-12 ✅ 已修（2026-10-07）

**现象**：PR #8（`dependa` → `dev`）始终 `mergeable=false`、`mergeable_state=dirty`，
28 个提交、40 文件却合不进去；本地看两边都没动过。

**根因**：`origin/dev` 的 `5d938f3` 消息写着 "Merge pull request #6 from ACANX/dependa"，
但**只有一个父提交**——它是把 `dependa @ cea8cce` 的内容**重新落了一遍**，
不是真合并。于是 `dev` 与 `dependa` 成了两条平行线、改同一批文件的不同版本：
`dev` 的树与 `dependa` 的祖先提交 `cea8cce` 的树**逐字节相同**，而 `dev` 不是祖先，
Git 无法自动合并（实测 9 个冲突，含 `add/add`）。

**修法**：既然 `dev` 的内容是 `dependa` 的真子集，修复的不是内容而是**血缘**——
在 `dependa` 上 `git merge -s ours origin/dev`，把 `dev` 记为父提交、树保持不变
（合并提交 `06c6121`，树 `92e697b…` 前后逐字节一致）。

**验证（实测）**：`git diff HEAD~1 HEAD` 为空；`git merge-base --is-ancestor origin/dev HEAD`
退出码 0；在 `origin/dev` 上模拟合并 `dependa` 得到 `Automatic merge went well`、0 冲突
（修复前 9 个）；PR 净 diff 收敛为 17 文件 +553/−23。

**排障中的教训**：第一次用**本地** `dev`（`4f12cef`，已过期）做合并试探，得到
「Already up to date」，差点把结论带偏——远端行为必须用 `origin/<branch>` 引用。

**复发（2026-10-07，同日）**：PR #8 最终是以 **squash** 合入 `dev` 的
（`aeadec4「Dependa (#8)」` 只有单父 `5d938f3`），于是**同一形态立刻重现**：`dev` 拿到内容、
没拿到血缘。实测 `dev` 的树 == `dependa@1518056` 的树（逐字节相同）⇒ 内容相等，
遂在 `dependa` 上 `git merge -s ours origin/dev`（合并提交 `d1de175`，树 `555e596…` 前后一致）
接回血缘，模拟合并 `dev ← dependa` 得 0 冲突。**这条教训不是理论——它在同一天被真实验证了一遍。**
为此在 `ReleaseProcess.md` §4.1 增加「被误用 squash 后必须立刻接回血缘」，
并把更根本的预防（关闭 squash/rebase 合并）登记为 `G-05`。

**立的规矩**：[ReleaseProcess.md](DevSpec/ReleaseProcess.md) §4「分支流向与合并方式（强制）」
——`dependa` 这类长期集成分支**只能真合并**；禁止 `git merge --squash` 加手工提交这类
「重新落地」；用了 squash/rebase 就必须删源分支；`-s ours` 只允许在能证明
「对方内容已被包含」时使用。

---

### F-13 ✅ 已修（2026-10-07）

**现象**：CI 在 `jacoco:0.8.15:prepare-agent` 上失败——`Could not find artifact
org.slf4j:slf4j-api:jar:1.7.36 / org.ow2.asm:asm-commons:jar:9.10.1 / org.ow2.asm:asm-tree:jar:9.10.1
in central (https://repo.maven.apache.org/maven2)`。本地 `clean verify` 却正常。

**定性（两处实验）**：①三个 artifact 在 Central `curl` 实测 **200**（且 9.10.1 是 asm-commons
最新版），排除「版本写错」；项目无 `<repositories>` / `.mvn/settings.xml` / 镜像，工作流也未启用
`cache:`，排除「解析源被改」；②在本地分别造出「负缓存」与「真拿不到」两种状态，
前者报 `... this failure was cached in the local repository ...`，后者报 `Could not find artifact ...
in central (<url>)`——**与 CI 一致的是后者**。⇒ CI 是当次就没要下来，属仓库侧 / 网络侧瞬时故障。

**事后取证**：同一提交 `9900e55` 的两轮运行里，`build (ubuntu-latest, system)` 在 push 运行
成功、在 PR 运行失败，且失败那条腿只跑了 0.2 分钟（成功的 0.7–1.1 分钟）——**瞬时故障确证**。
（该 PR 目标是 `dev`，`Gate`/`Compat` 只在 → `main`/`release/**` 时触发，故本次只跑了 Build。）

**修法**：新增复合 action `.github/actions/maven-run/action.yml`（单一来源），
四处工作流的依赖解析类调用改走它：先清 `*.lastUpdated`（覆盖负缓存）；失败时**先判断性质**，
只有命中「依赖解析不到 / 传输中断 / 远端 5xx / 负缓存」等与代码无关的特征才重试
（最多 3 次、间隔 20 秒），**其余立刻失败**——不做无差别重试，避免把真失败的时间乘以三。
实现上用 `env:` 传参 + `bash -c "${MVN_COMMAND}"`——最初把 `${{ inputs.command }}`
直接拼进脚本，命令含引号会被词分割拆坏。

**验证（实测）**：重试脚本 5 种情形（成功→1 次 / 瞬时故障×2 后成功→3 次 / 一直瞬时故障→3 次后失败 /
**真失败（编译错）→只跑 1 次** / `attempts=1`→不重试）全部符合预期；端到端用真实命令走该脚本得 `BUILD SUCCESS` 并把本地 4 个失败标记清为 0；
`.github/**/*.yml` 解析与 composite 结构校验通过。

**规范落点**：[BuildSpec.md](DevSpec/BuildSpec.md) §8.1「CI 必须容忍仓库侧瞬时失败（强制）」。

---

### F-07 ⏸ 待决策

**现象（✅ 已核实）**：`Script/PowerShell/CountJavaLoc.ps1` 是一段统计 Java 代码行数的
辅助脚本，但 `bin/CheckScripts.py` 的检查范围只含 `.bat`/`.cmd`/`.sh`/`.py`/`.gitattributes`/
`.gitignore`，**不含 `.ps1`**，因此它的编码与行尾没有任何保障；同时 `Script/` 与 `bin/`
两个目录都放脚本，职责重叠。

**待拍板**：两件事——① 是否把 `.ps1` 纳入检查（需要先定编码与行尾规约，`.ps1` 与
`.cmd` 的约束不同）；② `Script/` 是否并入 `bin/`。

**为什么记在这**：这是取舍问题（纳入检查会增加维护面），没有技术上的唯一正解。

---

## 11.1 原生镜像管线（DesktopNative）待观察项

- [ ] **N-10**：下一次 `DesktopNative` 运行确认「四条腿都绿、且摘要逐腿写清结论」；
      windows-x64 的工具链自证是否已因 `cmd //c` 修复；
      产物自证的诊断输出会揭示「构建成功但自证失败」的真正原因（此前不可见）。
- [ ] **N-15**：复核「改名 / 上传制品」不再被静默跳过。
      背景：2026-10-08 实测——**带 continue-on-error 的步骤一旦非零退出，它的 outputs
      不会被发布**，于是 `if: steps.verify.outputs.produced == 'true'` 静默变 false，
      「改名/上传」被跳过而作业仍为绿（macOS 腿就是这样丢掉产物的）。
      已修：产物自证开头先写 `produced=false` 兜底、结尾强制 `exit 0`；
      执行证据判据由 `*.build_artifacts.txt`（macOS 不产）改为 `*build-report.*`。
      复核判据：三条 jdk25 腿都出现「改名」与「上传制品」两个 ✓（不再是 skipped）。
- [ ] **N-13**：把资源清单从「宽通配」收窄成 `resource-config.json` 精确清单。
      背景：首次真编的镜像里 **27.69 MiB 是内嵌资源**（`byte[]`），
      来自 `.*\.(png|…|dll)$` 这种宽通配——把大量无关文件也吃了进去，
      是目前最大的一处体积优化余地（见 `DesktopNativeDesign.md` §5.4）。
      **前置条件**：先完成真机走查（N-05），否则漏一项资源的后果是「运行期缺文件」而不是构建失败。
- [ ] **N-14**：删除弃用的 `--enable-url-protocols`（改用 reachability metadata）。
      元数据已就位（`aha-desktop/src/main/resources/META-INF/native-image/...`），
      删除前必须满足：① 原生二进制完成一次真实对话（HTTPS 成功）；
      ② 构建报告里没有 http / https 协议处理器的未决条目。
      这是「静默坏掉」风险项——删掉不会构建失败，只会让产物发不出请求。
- [ ] **N-12**：确认「静默跳过」已根除。判据三条：① 各腿的「确认 native-image 真的执行过」
      步骤输出「执行证据：aha-desktop-native.build_artifacts.txt」；
      ② 三条 jdk25 腿的 `上传制品` 不再是 `skipped`；
      ③ `native-publish` 真正创建 `native-v<版本>` 预发行版并挂上 zip。
      背景：2026-10-08 事故——`native.skip` 的默认值写在模块自己的 `<properties>` 里，
      赢过了父 POM 中 profile 的覆盖，导致 native-image 被静默跳过，
      CI 全绿却零产物零报错（见 `Docs/DevLog/DevLog-20261008-08.md`）。
- [ ] **N-11**：确认 `native-*` 作业**没有**被加进任何分支保护的必需检查
      （它现在即使失败也不会红，但契约上仍不该出现，见 `BuildSpec.md` §8.1）。

## 11. 待人工执行的动作（需仓库 / 平台权限）

- [ ] **G-07**：完成 PR #15（`dev → main`，`Release:V0.1.1`）的合并。
      现状（2026-10-08 实测）：**没有文件冲突**（`mergeable_state=clean`），
      dev head `214611f` 上 `Build`/`Gate`/`Compat`/`CodeQL`/5 条原生腿**全绿**，
      三个 approve 均落在 `214611ff` 且晚于最后一次 push → GitHub 侧**可以合并**。
      **两条路，任选一条**：
      1. 在 PR 页面点绿色 **Merge pull request**（推荐，PR 会被记为 merged）；
      2. 推送已备好的本地合并提交（无凭据时由人工执行）：
         ```
         cd E:\GitRepo\GitHub\ACANX\.aha-merge-tmp
         git log --oneline -1     # 221ccee Merge branch 'dev' into main（发布 V0.1.1）
         git push origin HEAD:main
         ```
         推完可用 `git worktree remove --force E:\GitRepo\GitHub\ACANX\.aha-merge-tmp` 清理。
      **若按钮是灰的**，按这个顺序查（详情见 `ReleaseProcess.md` §4.2）：
      ① `Settings → Branches → main` 里是否存在**没有任何东西会上报**的必需项
      （如 `code_scanning` / `code_coverage`）——那会造成永久阻塞，需删掉或补上产出；
      ② 是否开启了「Require approval of the most recent reviewable push」且最后一次 push
      之后**没有**新审批。
      合并后链路：推 `main` → `Build` 的 `tag（V<版本号>）` 作业打 `V0.1.1`
      → `Release.yml` 出 CLI 包 + 桌面端三平台包。

> **为什么单独成节**：这类动作需要有人在仓库或平台上执行（推送凭据、分支保护设置、
> 平台侧配置、外部环境验收），自动化流程做不到。它们此前**只出现在对话里**——
> 对话会滚走，一旦漏掉就没有闭环，事后连「有没有做过」都无从判断。
>
> **约定（自 2026-10-07 起）**：凡是我方无法执行、又必须由人完成才能收口的事项，
> 一律在此登记 `G-xx` 并写明**验收标准**，同时在 `PLAN.md` 的阻塞项中交叉引用。
> **不得只在对话里交代。**
>
> 与第 7 节的分工：第 7 节是「需要拍板」（想清楚就能推进），本节是「需要动手」
> （拍板了也得有人在平台上点下去）。

| 编号 | 事项 | 阻塞什么 | 验收标准 | 状态 |
| ---- | ---- | -------- | -------- | ---- |
| ~~G-01~~ | 推送 `dev` 上的两条文档提交（`abd5d85` 规则集整改规格、`ca80c5c` F-12 复发记录） | 这批文档不进上游就等于白做 | `git ls-remote origin refs/heads/dev` 与本地 `dev` 一致（或经 PR 合入 `dev`）；`dev → main` 的 PR 能带上它们 | ☐ 未完成 |
| G-02 | **分支规则集整改**：`main` 补配 `Gate` / `Compat` 两条必需检查，并把审批数从 1 改为 0；`dev` 同样把审批数改为 0。附现状实测表与目标规格表 | ① 该拦的门禁没拦；② 三条规则对「单人 + 机器」永远无法满足，PR 被锁死（见 [DevLog-20261007-24.md](DevLog/DevLog-20261007-24.md)） | 五项必需检查齐全，且**预期失败的 PR 合不进去、正常 PR 单人能合进去** | ☐ 未完成 |
| G-03 | 确认每周定期扫描真的在跑 | 定期扫描静默失效无人知，漂移会持续积累 | 合入 `main` 后手动跑通一次 `Gate`；随后 Actions 出现 `schedule` 触发的运行记录 | ☐ 未完成 |
| G-04 | 为 `main` 规则集的 `code_scanning` 规则提供真结果：**开启 CodeQL**（推荐；若不开则必须删掉该规则） | `Waiting for Code Scanning results` 永不结束，PR #7 现在卡在这里 | Security → Code scanning 出现分析结果，PR 上该检查给出结论 | ☐ 未完成 |
| G-06 | 处置 0.1.0 的裸 tag：给同一提交补一个 `V0.1.0` 别名 tag（或明确「兼容两种写法」） | 已发布的 tag 是 `0.1.0`（无 `V` 前缀），而后来的约定与 `Release.yml` 的触发都是 `V*`；不处置则 `CHANGELOG` 的 `[0.1.0]` 链接与约定长期不一致 | `git ls-remote --tags origin` 能看到 `V0.1.0` 与 `0.1.0` 指向同一提交（`9138847`），或规范中明确写出兼容策略 |
| G-05 | 仓库设置：**关闭 squash 与 rebase 合并**，只保留 `Create a merge commit` | 长期集成分支 `dependa` 一旦被 squash，血缘就断了，下次 PR 必然 `dirty`——本次已实际复发（`F-12`） | 设置生效后，`dependa → dev` 的合并提交是双父，`git merge-base --is-ancestor origin/dev dependa` 成立 | ☐ 未完成 |

### G-01 ☐ 未完成

**内容**：把本地 `dev` 上尚未推送的两条文档提交推上去
（`abd5d85` 规则集整改规格、`ca80c5c` F-12 复发记录）。

**为什么必须人工**：① 本环境没有推送凭据（`GIT_TERMINAL_PROMPT=0 git push` 实测
`could not read Username`，exit 128）；② `dev` 的规则集带 `pull_request` 规则，
**直接推 `dev` 可能被拒**——能否绕过取决于规则集的 bypass 名单（本环境读 API 时被限流，
需在 Settings → Rules → 该规则集里确认）。不能绕过时，就从一个分支提 PR 合入 `dev`。

**分支现状（2026-10-07，方案 B 执行后）**：

| 分支 | SHA | 相对上游 |
| ---- | ---- | ---- |
| `dev` | `ca80c5c` | **ahead 2**（待推送） |
| `dependa` | `1518056` | 与 `origin/dependa` **一致**（已复位，无需推送） |
| `main` / `feat/local` | `4f12cef` | 一致 |

**已知代价（实测，务必记住）**：`dependa` 复位后，**第一次把 `dev` 合进 `dependa` 时会冲突 6 个文件**——
两侧相对分叉点 `5d938f3` 都改过它们，且改法的形状不同（这正是 squash 的后果，见 `F-12`）：

```
CHANGELOG.md
Docs/AHA/AHA-Design-V1.md
Docs/DevLog/DevLog-20261007-22.md
Docs/DevSpec/BuildSpec.md
Docs/DevSpec/ReleaseProcess.md
Docs/TODO.md
```

解法：这些文件两侧除本次两条文档提交外**内容本就相同**，**取 `dev` 的版本**即可
（`git checkout --theirs -- <文件>` 后 `git add`）。想要彻底避免这类差异，见 `G-05`（关闭 squash）。

**验收标准**：`git ls-remote origin refs/heads/dev` 与本地 `dev` 一致（或对应 PR 已合入）；
`dev → main` 的 PR（#7）能带上这批文档。

**闭环后**：本条改 ✅，并在 `PLAN.md` §8.1.1 收口。

### G-02 ☐ 未完成

**内容**：按下面的规格**一次性**配置两条分支规则集，让门禁真正拦人，同时**不把单人维护者锁死**。

**为什么必须人工**：规则集是仓库设置（Settings → Rules → Rulesets），工作流文件里写不了；
本环境也没有可写的凭据（`GIT_TERMINAL_PROMPT=0 git push` 实测 `could not read Username`）。

**现状（2026-10-07 API 实查）**：`GET /repos/ACANX/AHA/rulesets` → 两条仓库级规则集：

| 规则集 | id | 适用分支 | 现有规则 |
| ---- | ---- | ---- | ---- |
| `dev` | 24648482 | `refs/heads/dev` | `deletion`、`non_fast_forward`、`pull_request`（approvals=**1**）、`required_status_checks`（三条 `build (...)`） |
| `main` | 24648542 | `refs/heads/main` | 上述全部，外加 **`code_scanning`（CodeQL）**、**`code_coverage`**，且 `pull_request` 带 `last_push_approval`=**true** |

**已造成的实际阻塞（`PR #7` `dev` → `main`）**：三条规则对「单人 + 机器」**无法满足**：

1. `pull_request`（approvals=1 + `last_push_approval`）——只有一位协作者，GitHub 禁止自我批准
   → 提示「New changes require approval from someone other than ACANX because they were the last pusher」；
2. `code_scanning` 要求 CodeQL 结果，仓库却**没配任何 code scanning**
   → 提示「Waiting for Code Scanning results」；
3. `code_coverage` 需要把覆盖率上传给 GitHub 或其支持的覆盖率服务，本项目只有本地 JaCoCo 门禁
   （尚未报错，因为它排在其它条件之后）。

**方向相反的另一处**：`main` 的必需检查只有三条快检查，而 `BuildSpec.md` §8.1 要求
`Gate` 与 `Compat` 也必须是必需检查——**该拦的没拦，不该锁的锁死了**。

**目标规格（逐项照此设置）**：

| 项 | `main` | `dev` | 理由 |
| ---- | ---- | ---- | ---- |
| `deletion` / `non_fast_forward` | 保留 | 保留 | 禁止删除与强推，与人数无关 |
| 要求 PR | 保留 | 保留 | 改动走 PR 才挂得上必需检查 |
| required_approving_review_count | **0** | **0** | 单人仓库里「1 个批准」= 禁止合并；卡点交给必需检查 |
| require_last_push_approval | **false** | false | 同上 |
| required_review_thread_resolution | 保留 `true` | 不适用 | 要求先解决评论，单人也能满足 |
| required_status_checks | 三条 `build (...)` **+ `门禁（Maven 4 wrapper：verify + 覆盖率 + 文档 + 技能 + 脚本 + 重复率）` + `兼容性（Maven 3.9.x 完整 verify）`** | 三条 `build (...)`（保持） | 慢检查是「合入 `main` 前」的卡点（`BuildSpec.md` §8.1）；`dev` 是集成分支，保持快反馈 |
| `code_scanning` | **二选一**：① 开 CodeQL（推荐，见 `G-04`）并保留；② 不用就**删掉本规则** | 不适用 | 要求某工具的结果，就必须有人生产它 |
| `code_coverage` | **建议删除** | 不适用 | 覆盖率已由 `jacoco:check ≥ 0.70` + `bin/ReportCoverage.py` 在 `Gate` 里把关；再引外部服务属重复。若确实想要 PR 内可见覆盖率，需另行拍板（引入受支持的覆盖率服务） |

**验收标准**：五项必需检查（三条 `build (...)` + `Gate` + `Compat`）都出现在 `main` 的
必需检查里；用一个**预期失败的 PR** 验证确实合不进去；再用一个**正常 PR** 验证**单人也能合进去**
（不再出现「等待批准」「等待 Code Scanning」）。只勾选不验证，可能因名称未完全匹配而形同虚设。

**维护约定**：作业名或必需腿的矩阵键一旦变更，必需检查就会失配（见 `F-08`）；
改 `Build.yml` / `Gate.yml` / `Compat.yml` 或**规则集本身**时，必须同步刷新本条上方的两张表。

**闭环后**：本条改 ✅，并在 `PLAN.md` §8.2.9 收口。

### G-05 ☐ 未完成

**内容**：在 Settings → General → Pull Requests 里**关闭 squash 与 rebase 合并**，
只保留 `Create a merge commit`。

**为什么必须人工**：这是仓库设置，工作流与规则集都写不了（规则集也管不了合并方式）。

**为什么必须做**：`dependa` 是长期集成分支（Dependabot 的 `target-branch`），
它既要被合入、又要持续往 `dev` 合。一旦某次用 squash 合入，血缘就断了——
上游拿到内容却没有拿到分支历史，**下一次 `dependa → dev` 的 PR 必然 `dirty`**。
2026-10-07 当天，`F-12` 记下的这个形态**已经复发过一次**（PR #8 的 `aeadec4` 是单父提交），
只能再用一次 `-s ours` 把血缘接回（`d1de175`）。靠人记得住，不如靠平台不让做。

**验收标准**：设置生效后做一次 `dependa → dev`，确认合并提交有**两个父**
（`git log -1 --format=%p <merge>`），且 `git merge-base --is-ancestor origin/dev dependa` 成立。
此后 `F-12` / `ReleaseProcess.md` §4 的手工补救不再需要。

**闭环后**：本条改 ✅。

### G-04 ◐ 已提供 CodeQL 工作流，待你确认最后一处设置

**现象**：PR #7（`dev` → `main`）停在

```
Waiting for Code Scanning results. Code Scanning may not be configured for the target branch.
```

`main` 规则集有一条 `code_scanning` 规则（tool=`CodeQL`，`security_alerts_threshold=high_or_higher`、
`alerts_threshold=errors`），而仓库此前**没有任何 code scanning 配置**——没有结果可等，于是永久等待。

**已做（2026-10-07）**：新增 `.github/workflows/CodeQL.yml`（**高级设置**，即工作流方式）：

| 设计点 | 取值 | 理由 |
| ---- | ---- | ---- |
| 触发分支 | `pull_request: branches: [main, dev]` + `push: [main, dev]` + 每周一 04:00 UTC + 手动 | **必须覆盖目标分支**：规则作用在 `main`，只扫默认分支（`dev`）满足不了它 |
| 权限 | `contents: read`、`security-events: write`、`actions: read` | 上传 SARIF 必需，其余不收 |
| 构建 | `build-mode: manual` + 经 `./.github/actions/maven-run` 跑 `./mvnw -B -DskipTests -Djacoco.skip=true compile` | 用仓库固定工具链（Wrapper 的 Maven 4.0.0-rc-7 + JDK 25），并享受失败标记清理与定向重试 |
| 与必需检查的关系 | **不进**必需检查 | 规则集用的是 `code_scanning`（按扫描结果判定），不是 `required_status_checks`，因此不改动作业名契约（`F-08`） |

> ⚠️ **未能在本环境验证**：CodeQL 只能在 GitHub 上跑。首次运行要盯一眼日志——若报
> 「`build-mode` 取值不合法」，删掉那一行即可（工作流里已写明）。若报权限不足，检查
> Settings → Actions → General → Workflow permissions。

**为什么不用 GitHub 的「默认设置」（Default setup）**：它由平台托管、配置更省心，但扫描范围是
**默认分支**（本仓库为 `dev`）及指向它的 PR，覆盖不到 `main`——规则照样等不到结果。
（此点未能在本环境核实：docs.github.com 与 raw 文档源均被限流。工作流方案不依赖它，故取工作流。）

**备选（若你不想要代码扫描）**：删掉 `main` 规则集的 `code_scanning` 规则，并删除本工作流。
规则与产出必须成对——**要么都留，要么都去**。

**上线实测（2026-10-07）**：工作流**能跑**——`init`（含 `build-mode: manual`，被接受）与
走仓库 Maven 的 `compile` 两步在三次运行里**全部成功**；三次运行里 **1 次成功、2 次失败**，
失败**只在 `analyze`（上传 SARIF）**这一步：

| 运行 | 事件 | 分支/方向 | 结果 |
| ---- | ---- | ---- | ---- |
| #1 | pull_request | `dev-ddd` → `dev` | **success** |
| #2 | push | `dev` | failure（analyze） |
| #3 | pull_request | `dev` → `main`（PR #7） | failure（analyze） |

`#2`/`#3` 与 `#1` 的**代码完全相同**（两份分支树零差异），故不是代码问题，而是仓库侧配置冲突。
**最可能的原因**：开启了 GitHub 的 Code scanning **默认设置**（Default setup）——它与高级设置（本工作流）
**互斥**，来自工作流的结果会被拒收（错误原文：`CodeQL analyses from advanced configurations cannot be
processed when the default setup is enabled.`）。**待你在 run #3 第 6 步的日志里确认原文**。

**处置（二选一）**：

| | 动作 | 后果 |
| ---- | ---- | ---- |
| ① **关掉默认设置，留工作流**（推荐） | Settings → Code security → Code scanning → Default setup → Disable，然后 Re-run 失败的那两次 | 本工作流显式覆盖 `pull_request → main`，PR #7 的规则能拿到结果 |
| ② 留默认设置，删工作流 | 删 `.github/workflows/CodeQL.yml` | 默认设置只覆盖默认分支（`dev`）及指向它的 PR，覆盖不到 `main` → **PR #7 仍会永久等待**；真要走这条必须同时删掉 `main` 规则集的 `code_scanning` 规则 |

**另已顺手修正**：`github/codeql-action` 由 `v3` 升到 **`v4`**（v3 计划 2026 年 12 月弃用，
且 v3 目标 Node.js 20、会被强制跑在 Node 24 上并报弃用警告）。
运行日志里那条 `Cannot build an overlay-base database because build-mode is set to "manual"…`
是**良性**提示（回退为建立完整数据库，分析更完整），已在工作流里注明「不要为消掉它改成 `none`」。

**验收标准**：Security → Code scanning 出现 java-kotlin 的分析结果；
PR #7 上「Code Scanning」由等待变为**给出结论**（按阈值：高危以上或存在错误才拦）。

**闭环后**：本条改 ✅。

**闭环后**：本条改 ✅。

### G-05 ☐ 未完成

**内容**：在 Settings → General → Pull Requests 里**关闭 squash 与 rebase 合并**，
只保留 `Create a merge commit`。

**为什么必须人工**：这是仓库设置，工作流与规则集都写不了（规则集也管不了合并方式）。

**为什么必须做**：`dependa` 是长期集成分支（Dependabot 的 `target-branch`），
它既要被合入、又要持续往 `dev` 合。一旦某次用 squash 合入，血缘就断了——
上游拿到内容却没有拿到分支历史，**下一次 `dependa → dev` 的 PR 必然 `dirty`**。
2026-10-07 当天，`F-12` 记下的这个形态**已经复发过一次**（PR #8 的 `aeadec4` 是单父提交），
只能再用一次 `-s ours` 把血缘接回（`d1de175`）。靠人记得住，不如靠平台不让做。

**验收标准**：设置生效后做一次 `dependa → dev`，确认合并提交有**两个父**
（`git log -1 --format=%p <merge>`），且 `git merge-base --is-ancestor origin/dev dependa` 成立。
此后 `F-12` / `ReleaseProcess.md` §4 的手工补救不再需要。

**闭环后**：本条改 ✅。

### G-04 ☐ 未完成

**内容**：为 `main` 规则集的 `code_scanning` 规则提供真结果——**开启 CodeQL**。

**为什么必须人工**：需要管理员在 Settings → Code security → Code scanning 里开启。
推荐用 **Default setup**（默认设置）：由 GitHub 维护配置、仓库里不必放工作流，也不会随
Dependabot 的版本漂移而失修；仓库是 `public`，CodeQL 免费。

**对应关系**：**开了它就保留 `code_scanning` 规则；不开就必须删掉那条规则**（见 `G-02`），
否则 PR 会一直停在「Waiting for Code Scanning results」。

**验收标准**：Security → Code scanning 出现分析结果；PR 上该检查给出明确结论
（阈值 `high_or_higher` / `errors`，即高危以上或存在错误才拦）。

**闭环后**：本条改 ✅。

### G-03 ☐ 未完成

**内容**：确认 `Gate.yml` 的 `schedule`（每周一 03:00 UTC）真的生效。

**为什么必须人工**：GitHub 的 `schedule` **只在默认分支上生效**，必须等本次改动合入
`main` 之后才能验证；在此之前它不会触发，也无法用 PR 验证。

**验收标准**：合入 `main` 后先用 `workflow_dispatch` 手动跑通一次；随后在 Actions 页面
看到 `schedule` 触发的运行记录（时间戳应落在周一 03:00 UTC 附近）。

**闭环后**：本条改 ✅。

---

## 12. 展示与终端体验

| 编号 | 事项 | 类型 | 证据 | 优先级 | 状态 | 落地文档 |
| ---- | ---- | ---- | ---- | ------ | ---- | -------- |
| H-01 | 启动标志的默认风格：像素风（现状）还是线框风？ | 待决策 | ✅ | P3 | ⏸ 待决策 | `PixelLogoDesign.md`、`TUIDesign.md` §3.2 |

### H-01 ⏸ 待决策

**背景**：`aha chat` 的启动横幅右侧会打印标志，`--logo` 支持 `auto|pixel|ascii|off`。
当前 `auto` 为**像素风优先**，受符号能力与 256 色门禁约束，不满足时降级为线框风。

**待拍板**：默认是否改用线框风。影响仅限首屏观感；改动量是把候选顺序对调一行
（`StartupBanner.compose` 的候选列表）。

**为什么记在这**：这是人对观感的偏好，技术上没有唯一正解。此前只在对话里提过，
按 §11 的约定需要留痕，避免漏掉。

---

## 附：核实方法备忘

部分条目需动手验证，建议的核实命令如下（**执行前请确认环境**）：

```bash
# B-03：确认 jackson 构件来源与版本
./mvnw dependency:tree -Dincludes='*jackson*'

# E-02/E-04：查看 native-image 相关的实际可达类与资源（需 GraalVM 环境）
native-image --version

# A-03/D-01：确认发行包实际内容
./mvnw clean package
ls -l dist/lib/

# C-01：确认模块边界是否真的阻止了非法引用（可故意加一条 requires 验证）
jdeps --module-path dist/lib --module com.acanx.module.aha.core
```
