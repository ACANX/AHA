# TS-2610-027 · 让预发行版显示带构建号的版本（issue #46）

## 1. 背景

issue #46：GUI 与 CLI 都只能查到基线版本（如 `0.1.1`），而通过 PR 合并到 `dev` 后自动构建的包
其实带构建号（形如 `0.1.1.00046`）——这个构建号只出现在**产物文件名与包内 README**上，
程序内部查不到。排查问题时，用户报「0.1.1 有问题」，无法判断是哪一次构建。

要求：非正式版本（PR 构建）在 GUI「关于」与 CLI（启动页 / 会话内命令）显示带构建号的版本；
正式版本不做强制要求。

## 2. 排障过程与修复链

### 2.1 先厘清构建号目前只到哪一层

- 工作流 `DesktopNative.yml` / `CliNative.yml` 会算出 `version=<a.b.c.PPPPP>` 并传
  `-Daha.native.version=...`；
- 但它**只用于** `aha-*-native` 模块的 `finalName`（zip 名）与包内 `README.txt` 的替换，
  **没有进入** `aha-common` 的 `version.properties`——所以运行时 `AppVersion` 只看到
  POM 的 `<version>`（`0.1.1`）。

### 2.2 修复：版本分两层注入

1. 根 POM 新增属性 `<aha.build.version>${project.version}</aha.build.version>`；
2. `version.properties` 由 `version=${project.version}` 扩为两项：
   `version`（基线）+ `build=${aha.build.version}`（构建版本）；
3. `AppVersion` 相应拆分：
   - `version()` 基线版本（保持原语义，窗口标题等继续可用）；
   - `buildVersion()` 构建版本（正式构建时等于基线）；
   - `isPreRelease()` 是否带构建号；
   - `DISPLAY` 改为 `AHA ` + 构建版本；
4. 工作流在构建时加传 `-Daha.build.version=<a.b.c.PPPPP>`：
   `DesktopNative.yml`、`CliNative.yml`（正式发版 `Release.yml` 不传 → 基线版本）。

### 2.3 展示点

- **桌面端**「关于 AHA」：预发行版显示「版本：0.1.1.00046（基线 0.1.1）」；
  启动日志的版本字段改用 `buildVersion()`；窗口标题随 `DISPLAY` 一并带上构建号。
- **CLI**：启动页顶栏新增「版本」字段；`/help` 头部打印版本；
  `aha version` / `aha --version`（`CliVersionProvider`）随 `DISPLAY` 自动带上构建号。

## 3. 最终验证结果

- 按 ACANX 的要求，本地不跑构建与测试，编译与全量测试交由 PR 的持续集成执行；
- 单元测试：`AppVersionTest` 更新并新增——`DISPLAY` 用构建版本；构建版本在「正式构建」与
  「预发行构建」两种情形下都成立（前缀关系）的断言；
- 真机验收（合入后自动产出的 Windows 包）：① 桌面端「关于」显示 `0.1.1.000XX`；
  ② CLI 启动页与 `/help` 显示同一构建号；③ `aha --version` 同样显示构建号。

## 4. 关键教训

1. **版本号只有一个来源，但可以有两个层次**：基线版本属于发布节奏，构建号属于单次构建；
   两者都从 POM 派生，避免再写死一处。
2. **产物名上的信息不等于程序内的信息**：构建号此前只在 zip 名与 README 里，
   排查时拿不到——「构建时知道、运行时不知道」是排查类需求最常见的落差。
3. **注入属性的默认值必须与基线一致**：`aha.build.version` 默认 `${project.version}`，
   正式发版不传即退化为基线，避免强制正式版本也带构建号（issue 明确不要求）。
4. **YAML 折叠块里不能写 `#` 注释**（本次真机看不到构建号的直接原因）：第一次提交后构建号仍未生效——
   `command: >-` 块内那行 `# 构建版本：…` 被 YAML 当成**内容**（块标量里的 `#` 不是注释），
   折进命令串后在 shell 里把其后的 `-Daha.build.version`、`-Dnative.image.executable`、
   `-Dnative.skip=false` **全部注释掉**，构建号因此从未注入。教训：`#` 注释只能写在块标量
   **之外**；改完 workflow 必须在 CI 日志里核对「实际执行的命令串」，而不是只看 workflow 源码。

## 5. 涉及文件清单

- `pom.xml`（新增 `aha.build.version`）
- `aha-common/src/main/resources/com/acanx/module/aha/common/version.properties`
- `aha-common/src/main/java/com/acanx/module/aha/common/AppVersion.java`
- `aha-common/src/test/java/com/acanx/module/aha/common/AppVersionTest.java`
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/DesktopShell.java`（「关于」）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/AhaDesktopApp.java`（启动日志）
- `aha-cli/src/main/java/com/acanx/module/aha/cli/ChatCommand.java`（启动页）
- `aha-cli/src/main/java/com/acanx/module/aha/cli/session/HelpSessionCommand.java`（`/help`）
- `.github/workflows/DesktopNative.yml`、`.github/workflows/CliNative.yml`
- `CHANGELOG.md`、`Docs/TODO.md`、`Docs/Guide/ReferenceGuide.md`、
  `Docs/DevSpec/ReleaseProcess.md`、`Docs/PLAN.md`

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- issue #46 的现象：GUI 与 CLI 都**只能查到基线版本**（如 `0.1.1`），
  而通过 PR 合并到 `dev` 后自动构建的包其实带构建号（形如 **`0.1.1.00046`**）
  ——这个构建号只出现在**产物文件名与包内 README** 上，**程序内部查不到**。
  ⇒ 用户报「0.1.1 有问题」时，**无法判断是哪一次构建**。
- **构建号当时只到哪一层**：工作流 `DesktopNative.yml` / `CliNative.yml` 算出
  `version=<a.b.c.PPPPP>` 并传 `-Daha.native.version=…`，但它**只用于**
  `aha-*-native` 模块的 `finalName`（zip 名）与包内 `README.txt` 的替换，
  **没有进入** `aha-common` 的 `version.properties` ⇒ 运行时 `AppVersion` 只看到
  POM 的 `<version>`（`0.1.1`）。
- 修复：① 根 POM 新增属性 `<aha.build.version>${project.version}</aha.build.version>`；
  ② `version.properties` 由一项扩为两项——`version`（基线）+ `build=${aha.build.version}`（构建版本）；
  ③ `AppVersion` 相应拆分 `version()` / `buildVersion()` / `isPreRelease()`，
  `DISPLAY` 改为 `AHA ` + **构建版本**；
  ④ 工作流构建时加传 `-Daha.build.version=<a.b.c.PPPPP>`
  （正式发版 `Release.yml` **不传** → 退化为基线版本）。
- 展示点：桌面端「关于 AHA」预发行版显示「版本：`0.1.1.00046`（基线 `0.1.1`）」，
  启动日志的版本字段与窗口标题一并带上构建号；CLI 启动页顶栏新增「版本」字段，
  `/help` 头部打印版本，`aha version` / `aha --version`（`CliVersionProvider`）随 `DISPLAY` 自动带上。
- **真机看不到构建号的直接原因**（第 4 条教训）：
  `command: >-` 块内那行 `# 构建版本：…` 被 YAML 当成**内容**
  （**块标量里的 `#` 不是注释**），折进命令串后在 shell 里把其后的
  `-Daha.build.version`、`-Dnative.image.executable`、`-Dnative.skip=false` **全部注释掉**，
  ⇒ 构建号**从未注入**。
- 验证：**按 ACANX 的要求本地不跑构建与测试**，编译与全量测试交由 PR 的持续集成执行；
  `AppVersionTest` 更新并新增（`DISPLAY` 用构建版本；构建版本在「正式构建」与「预发行构建」
  两种情形下都成立的前缀关系断言）；真机验收三项（桌面端「关于」显示 `0.1.1.000XX`；
  CLI 启动页与 `/help` 显示同一构建号；`aha --version` 同样显示）。

### 机制 / 行为模型

- **版本号只有一个来源，但可以有两个层次**：**基线版本**属于发布节奏，**构建号**属于单次构建；
  两者都从 POM 派生，避免再写死一处。
- **产物名上的信息 ≠ 程序内的信息**：「**构建时知道、运行时不知道**」是排查类需求
  最常见的落差——构建号此前只在 zip 名与 README 里。
- **注入属性的默认值必须与基线一致**：`aha.build.version` 默认 `${project.version}`，
  正式发版不传即退化，避免强制正式版本也带构建号（issue 明确**不要求**正式版带构建号）。
- **YAML 折叠块（`>-`）里的 `#` 是内容，不是注释**：注释只能写在块标量**之外**。
  更普适的操作要点：**改完 workflow 必须在 CI 日志里核对「实际执行的命令串」**，
  而不是只看 workflow 源码——本次正是「源码看着对、实际命令被截断」。

### 未定位 / 未复现

- **真机三项验收当时未做**：要等「合入后自动产出的 Windows 包」。
- 「构建号从未注入」这件事是**第一次提交后**发现「构建号仍未生效」才查出来的
  ⇒ 说明**只看源码**的做法**确实漏过一次**（已作为教训固化）。
- **是否还有别的 workflow 在块标量里写 `#` 注释**：**未穷举**（本次只修了发现的那处）。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 现象 | issue #46 | GUI / CLI 只能查到 `0.1.1`；构建号只在 zip 名与 README 上 |
| 构建号当时的去向 | 查 workflow 与 POM | 只用于 `aha-*-native` 的 `finalName` 与 `README.txt` 替换，**未进** `version.properties` |
| 运行时实际取值 | `AppVersion` | 只看到 POM 的 `<version>` = `0.1.1` |
| 修复 1 | 根 POM | 新增 `<aha.build.version>${project.version}</aha.build.version>` |
| 修复 2 | `version.properties` | 两项：`version`（基线）+ `build=${aha.build.version}` |
| 修复 3 | `AppVersion` | `version()` / `buildVersion()` / `isPreRelease()`；`DISPLAY` = `AHA ` + 构建版本 |
| 修复 4 | 两个原生工作流 | 构建时加传 `-Daha.build.version=<a.b.c.PPPPP>`；`Release.yml` **不传** |
| 展示点（桌面端） | 「关于 AHA」 | 「版本：`0.1.1.00046`（基线 `0.1.1`）」 |
| 展示点（CLI） | 启动页 / `/help` / `--version` | 顶栏「版本」字段、`/help` 头部、`CliVersionProvider` 随 `DISPLAY` |
| **真机看不到构建号的原因** | workflow 源码 + CI 日志 | `>-` 块内 `#` 被当**内容** ⇒ 折成命令行后把 `-Daha.build.version` 等**全部注释掉** |
| 测试 | `AppVersionTest` | 更新 + 新增：`DISPLAY` 用构建版本；两种构建情形下的前缀关系断言 |
| 本地验证边界 | 项目约定 | **本地不跑构建与测试**，交 CI |
| **未验证** | — | 真机三项验收（待 Windows 包产出） |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「构建号没算出来」 | 工作流确实算出了 `version=<a.b.c.PPPPP>`，只是**用途受限**（只进 zip 名与 README）（「2.1」） |
| 「`AppVersion` 读取方式错了」 | 它读的是 `version.properties`，而该文件里**只有基线一项**——缺的是数据源（「2.1」） |
| 「正式发版也要带构建号」 | issue 明确**不要求**；`aha.build.version` 默认 `${project.version}`，不传即退化（「4、教训」第 3 条） |
| 「workflow 源码看着对就没问题」 | 块标量里的 `#` 把后续参数**整段注释掉**，源码层面看不出 ⇒ 必须核对 CI 日志里的**实际命令串**（「4、教训」第 4 条） |
| 「本地没跑构建所以是本地问题」 | 按项目约定本地**不跑**构建与测试；本档案的验证口径就是「交 CI + 真机验收」（「3、最终验证结果」） |

## 未决问题

**Q1 真机三项验收做了吗？**

现状：**当时未做**——需等「合入后自动产出的 Windows 包」。
三项：桌面端「关于」显示 `0.1.1.000XX`；CLI 启动页与 `/help` 显示同一构建号；`aha --version` 同样显示。

**Q2 别的 workflow 里还有块标量 `#` 注释吗？**

现状：**未穷举**（本次只修了发现的那一处）。
阻塞点：可考虑把「`>-` / `|` 块标量内不得有 `#`」做成静态检查
（本项目的 `bin/CheckScripts.py` 已有 YAML 检查的前例），尚未评估。

**Q3 「构建号」还要不要出现在更多地方？**

现状：本次只覆盖了「关于」「启动日志」「窗口标题」「CLI 启动页 / `/help` / `--version`」。
其它可能出现版本号的地方（帮助文本、导出文件头、日志前缀）**未逐一核对**。

**Q4 CI 日志里的「实际命令串」能自动核对吗？**

现状：靠人工看日志（本次正是这么发现的）。
阻塞点：可考虑在 workflow 里把最终命令串 `echo` 到 Job Summary 并断言包含关键 `-D` 参数，
尚未落地。
