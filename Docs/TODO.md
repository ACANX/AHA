# AHA 待办与调整项（暂存区）

**文档版本**：v0.5.0
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
| C-01 | JPMS 收益与成本的时间错配            | 待决策 | ✅    | P2     | ⏸ 待决策 | `Constitution.md` 第 4 条、`ModuleConvention.md` |
| C-02 | `ServiceLoader` 双声明的长期维护成本 | 待决策 | ✅    | P3     | ⏸ 待决策 | `ModuleConvention.md`                            |
| C-03 | 依赖治理补强（Enforcer / ArchUnit）  | 调研   | ✅    | P2     | ☐ 未完成 | `BuildSpec.md`                                   |
| C-04 | `jlink` 禁令的解除条件未登记         | 待决策 | ✅    | P3     | ☐ 未完成 | `BuildSpec.md` §7                                |

### C-01 ⏸ 待决策

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

> 现状：`aha-desktop` 为占位模块（`module-info` + 空 JAR）。
> 父 POM 已声明 `javafx.version = 25`，但仍**未引入任何 JavaFX 依赖**。✅ 已核实

| 编号 | 事项                                    | 类型     | 证据 | 优先级 | 状态     | 落地文档                           |
| ---- | --------------------------------------- | -------- | ---- | ------ | -------- | ---------------------------------- |
| D-01 | 发行包会混入多平台 JavaFX native JAR    | 风险     | ⚠️    | P1     | ☐ 未完成 | `dist.xml`、`BuildSpec.md` §7      |
| D-02 | 启动脚本需按模块路径分叉                | 风险     | ✅    | P2     | ☐ 未完成 | `bin/Aha.sh`、`bin/Aha.bat`        |
| D-03 | FXML 反射需限定 `opens`                 | 风险     | ⚠️    | P2     | ☐ 未完成 | `aha-desktop/module-info.java`     |
| D-04 | TestFX 在 JPMS + 无显示 CI 下的配置     | 风险     | ⚠️    | P2     | ☐ 未完成 | `TestingSpec.md` §1                |
| D-05 | 覆盖率排除项的长期归属未定              | 待决策   | ✅    | P2     | ⏸ 待决策 | `TestingSpec.md` §3.2              |
| D-06 | 发行目标平台与 CI runner 平台不匹配     | 文档缺陷 | ✅    | P2     | ☐ 未完成 | `DesktopDesign.md` §2/§3           |
| D-07 | JavaFX 线程模型与虚拟线程的桥接未设计   | 设计缺口 | ✅    | P2     | ◐ 进行中 | `DesktopDesign.md`、`GUIDesign.md` §8 |
| D-08 | `jpackage` 不可交叉编译 → CI 需分平台   | 风险     | ✅    | P2     | ☐ 未完成 | `Build.yml`、`DesktopDesign.md` §3 |
| D-09 | 桌面端无内置工具（未依赖 `aha-tool`）   | 设计缺口 | ✅    | P1     | ☐ 未完成 | `aha-desktop/pom.xml`、`DesktopDesign.md` |

### D-01 ☐ 未完成（0.2 前置）

**复核（✅ / ⚠️）**：`aha-cli/src/assembly/dist.xml` 的 `<dependencySet>` **仍无 classifier 过滤**
（✅ 已核实）；JavaFX 的平台 classifier 机制属外部知识（⚠️），需在引入 JavaFX 后实测。

**残留动作**：为桌面端另建 assembly 描述符（如 `dist-desktop.xml`），用 `<classifier>` 或
profile 按平台筛选；或改用 `jpackage` 产出自包含运行时，不再依赖 `dist/lib` 布局。

### D-02 ☐ 未完成

**复核（✅ 已核实）**：`bin/Aha.sh` 仍为单一 CLI 启动路径（`--module com...AhaCli`）；
`bin/` 下**无** `AhaDesktop.sh` / `.bat`。

**残留动作**：新增独立桌面端启动脚本，而非在现有脚本中加分支。
`.bat` 必须**纯 ASCII + CRLF**，并由 `bin/CheckScripts.py` 校验。

### D-03 ☐ 未完成

**残留动作**：0.2 实施时在 `aha-desktop/module-info.java` 中按 `Constitution.md` 第 3 条
包结构冻结清单（已预留 `com.acanx.module.aha.desktop.controller`）添加**限定** `opens`。

> 注：`GUIDesign.md` §8.1 建议采用「进程内直调 + JavaFX 原生控件」，若最终仍用 FXML，
> 本条适用；若完全转为代码构建 UI 则本条可降级。

### D-04 ☐ 未完成

**复核（✅ 已核实）**：`TestingSpec.md` §1 仍写「JavaFX 测试 | TestFX（0.2+）」，
**无**「JavaFX 测试的环境要求」小节。

**残留动作**：0.2 前在 CI 中先跑通一个 TestFX 最小样例，再纳入 `Build.yml`；
同步补充环境要求小节（`--add-opens`、`xvfb-run` / Monocle、`--patch-module`）。

### D-05 ⏸ 待决策

**复核（✅ 已核实）**：父 POM JaCoCo 仍泛排除 `com/acanx/module/aha/desktop/**`；
`TestingSpec.md` §3 仍**未给 Desktop 阈值**，§3.2 仍登记「桌面端 0.2 实现」。

**残留动作**：在 `TestingSpec.md` §3 中为 Desktop 模块**显式设定阈值或明确永久排除**，
并说明 `BUNDLE` 级聚合门禁下桌面端低覆盖对整体的影响。

### D-06 ☐ 未完成

**复核（✅ 已核实）**：`DesktopDesign.md` v1.1.0 §2 已改为指向 `GUIDesign.md` 并标注
「WebView / FXML 组合从未真正决策，待评审」——**这是 D-07 范畴的进展，不是 D-06**。
§2 仍**未列目标平台清单**；`.github/workflows/Build.yml` 矩阵仍无 macOS runner。

**残留动作**：在 `DesktopDesign.md` §2 列出目标平台清单（Windows / Linux / macOS），
与 §3 的 CI 分平台策略、`Build.yml` 的 runner matrix 三者对齐。

### D-07 ◐ 进行中

**复核（✅ 已核实）**：新增的 `GUIDesign.md` §8.1 已给出**单一桥接点**的表述：

> `AgentService.streamChat(...)` 的 `AgentEventListener` 直接回调到 UI 线程（经 `Platform.runLater`）

并选择「进程内直调」取消本地 HTTP 层。**方向已定，但三项细节仍缺**：

1. 高频 `ContentEvent` 的 `runLater` 积压与节流策略
2. 取消（`CancellationToken`）与窗口关闭的联动
3. 虚拟线程 → FX 线程的**禁止直触**约束未成文

**残留动作**：在 `DesktopDesign.md` 新增「线程模型」小节（或由 `GUIDesign.md` §8 升格），
明确单一桥接点与上述三条。

### D-08 ☐ 未完成

**复核（✅ 已核实）**：`Build.yml` 仍无打包 job；`Release.yml` 仍只上传 `dist.zip`。

**残留动作**：0.2 在 `Build.yml` 新增按平台的打包 job，并扩展 `Release.yml` 的产物矩阵
（桌面端安装包需另设产物）。

### D-09 ☐ 未完成（新增条目）

**现象**：`aha-desktop/pom.xml` 仅依赖 `aha-core`，**未声明 `aha-tool`**。
内置工具（file / http / shell）全在 `aha-tool` 且经 `ServiceLoader` 发现，
因此桌面端当前形态下**不具备任何工具能力**。`Constitution.md` 第 4 条的依赖矩阵中，
desktop 亦未列 tool 依赖。

**依据（✅ 已核实，2026-10-07）**：`aha-desktop/pom.xml` 与 `Constitution.md` 第 4 条。

**建议动作**：0.2 明确桌面端是否需要内置工具；若需要，修订 `Constitution.md` 第 4 条
依赖矩阵（新增 `core ← tool → desktop` 边）并同步 `ModuleConvention.md` §2。

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
| JPMS 模块路径目录（`dist/`）    | 需 JVM、可调优 | `dist.zip`      | ✅ 已实现 |
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
| v0.1.0 | 2026-10-06 | 初始版本：汇总 0.1 代码走查、JPMS 架构评估、桌面端与原生编译调研结论 | @ACANX |
| v0.2.0 | 2026-10-07 | 新增第 6 节「记忆能力（0.6 起）」：推进顺序、写入策略、载体选型实测依据、跨环境共享机制 | @ACANX |
| v0.3.0 | 2026-10-07 | **重排版**：清除重复副本与误粘贴的会话日志（见 `PLAN.md` §4）；全部条目表格新增「状态」列并逐条复核；新增第 9 节「复核结论汇总」 | @ACANX |
| v0.3.0 | 2026-10-07 | `Docs/` 清单更新：设计文档已移入 `Docs/AHA/` | @ACANX |
| v0.4.0 | 2026-10-07 | B-03 版本漂移结项 | @ACANX |
| v0.5.0 | 2026-10-07 | 新增第 10 节「CI、门禁与工程效能」（F-01~F-06，均已落地）：检查分层与每周定期扫描、门禁断言 Maven 版本、Maven 3.9.x 独立兼容工作流、重复代码率检查、矩阵不再 fail-fast、macOS 可选腿定位；§2 新增 B-04~B-07（Windows 暴露的四类缺陷，均已修）并**修正 B-03 的三处过期状态**（条目正文 ✅ 已解决，而 §2 汇总表与 §8 落地去向表仍写 ◐ 进行中） | @ACANX |
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

| 顺序 | 编号 | 决策问题                                                     | 影响范围                     | 状态     |
| ---- | ---- | ------------------------------------------------------------ | ---------------------------- | -------- |
| 1    | E-01 | native 产物中扩展系统的降级策略？`ModuleLayer` 与 native-image 冲突如何处置？ | 决定 native-image 是否值得做 | ⏸ 待决策 |
| 2    | C-01 | JPMS 的投入产出错配是否接受？扩展路线图推迟时如何处理？      | 架构根基                     | ⏸ 待决策 |
| 3    | E-11 | 发行矩阵如何定义？三种形态是否并存？                         | 打包与文档全局               | ⏸ 待决策 |
| 4    | E-10 | native 产物的验收标准与 CI 归属？                            | 质量基线                     | ⏸ 待决策 |
| 5    | C-02 | `ServiceLoader` 双声明：维持／删除／加校验？                 | 维护成本（与 E-04 相关）     | ⏸ 待决策 |
| 6    | D-06 | 桌面端目标平台是否含 macOS？                                 | CI 与打包                    | ⏸ 待决策 |
| 7    | D-05 | 桌面端覆盖率：纳入门禁／单独阈值／永久排除？                 | 质量基线                     | ⏸ 待决策 |
| 8    | D-07 | 桌面端技术选型：进程内直调（8.1）vs 本地 HTTP + WebView（8.2）？ | 0.2 全部实现                 | ⏸ 待决策 |
| 9    | E-12 | `TODO.md` 文件名是否改名？                                   | 文档规范一致性               | ⏸ 待决策 |
| 10   | D-09 | 桌面端是否需要内置工具（引入 `aha-tool`）？                  | 依赖矩阵与 `Constitution.md` | ⏸ 待决策 |

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
| E-12 | `DocumentationSpec.md` 第 1 节     | 文件名保留名单（若采纳）      | ☐ 未完成（待决策） |

### 阶段三：设计与实现（0.2 前）

| 条目                    | 目标文档                                                | 变更性质                     | 状态     |
| ----------------------- | ------------------------------------------------------- | ---------------------------- | -------- |
| B-01 / B-02             | `AgentEngine.java`、`AgentServiceDesign.md`             | 明确契约 + 补测试            | ☐ 未完成 |
| D-01 ~ D-04、D-08       | `DesktopDesign.md`、`DesktopDesign` 打包/测试小节、`Build.yml` | 新增线程模型、打包、测试小节 | ☐ 未完成 |
| D-06                    | `DesktopDesign.md` §2/§3、`Build.yml`                   | 平台清单对齐                 | ☐ 未完成 |
| D-07                    | `DesktopDesign.md`（或 `GUIDesign.md` §8 升格）         | 线程模型小节                 | ◐ 进行中 |
| D-09                    | `Constitution.md` 第 4 条、`ModuleConvention.md` §2     | 依赖矩阵补 tool 边           | ☐ 未完成（待决策） |
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
