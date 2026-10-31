# TS-2610-007 · 桌面端桥接契约落地——原子替换静默失效等 5 个坑

> 日期：2026-10-08
> 作者：@ACANX
> 关联 PR：`feat/version-0.1.1`（第 6 个提交，待推送）
> 关联记录：`TODO.md` `D-07` / `D-10` / `D-11`、`DesktopDesign.md` §4 / §6、`TestingSpec.md` §3.3

## 1. 背景

按拍定的方案 C 推进 0.2 第一步：**先定桥接契约（`D-07` 线程模型 + `D-10` 资源下移），再落最小可启动窗口**。

预期是一次「机械搬运 + 小步新增」：把版本号与日志装配从 `aha-cli` 下移到公共模块，然后写
`Application` 子类、线程桥接与几个单测。实际踩了 5 个坑，其中 2 个是「我自己判断错」而不是代码错。

## 2. 排障过程与修复链

### 2.1 原子替换静默失效，而脚本还在报 ✅（最危险的一处）

**现象**：把 `LoggingSetup` 下移到 `aha-core` 后，`aha-core` 编译失败：

```
package org.apache.logging.log4j is not visible
  (package ... is declared in the unnamed module, but module com.acanx.module.aha.core does not read it)
```

**排查**：先怀疑「log4j 被放到 classpath 而不是 module path」，差点去调 surefire / 编译器配置。
改为**直接看文件原文**后真相大白：`module-info.java` 里根本没有那两行 `requires`。

**根因**：脚本用 8 个空格缩进去匹配模块体内的语句，而实际是 **4 个空格**。
`str.replace` 不匹配时不报错、原样返回，于是「什么都没改」被当成「改好了」——
脚本还打印了 ✅。这是本项目第 5 次栽在同一个坑上（前 4 次分别在文档编辑与代码编辑）。

**修复**：所有就地修改改用带断言的辅助函数：

```python
def must(path, old, new):
    t = load(path)
    assert old in t, f"未匹配 {path}: {old[:70]!r}"
    t2 = t.replace(old, new, 1)
    assert t2 != t          # ← 关键：断言「内容真的变了」
    save(path, t2)
```

**教训**：**判断「替换是否生效」不能看脚本的自我报告，要看替换后的内容**。
断言 `old in t` 只证明「找得到」，`t2 != t` 才证明「改到了」。

### 2.2 三处「先假设后动手」导致的返工

| 假设 | 实际 | 代价 |
|---|---|---|
| `shutil.move` 会自动建目标目录 | 目标目录不存在 → `FileNotFoundError`，整个脚本在第一步就死 | 一轮白跑（好在无副作用） |
| `aha-common/pom.xml` 有 `<build>` 段 | 没有 → 追加式的改动要单独处理 | 再一轮 |
| 「桌面端包缺 `log4j2.xml`」（写入 `D-10`） | **全仓原本就没有该文件**——装配一直是程序化的 | 文档结论错误，需更正 |

**教训**：对仓库的判断必须**先查再写**。第三条尤其典型：我把「包里没找到 `log4j2.xml`」当成了
「缺配置」，而真实原因是这个文件**本来就不该存在**（`LoggingSetup` 的 Javadoc 早就写明：
JPMS 下 `getResources` 不搜模块路径，所以程序化装配）。**缺失的证据不等于缺口的证据。**

### 2.3 测试失败，但错的是测试而不是代码

两个失败用例：

- `valueArrivingDuringRenderIsNotLost` 期望「第一帧只渲染 `first`」，实际渲染了两个值——
  因为测试替身 `drain()` 会**连带执行渲染期间新排的任务**（这正是 `runLater` 的真实语义）；
- `closedBridgeDropsLaterSubmissions` 期望 `close()` 前提交的值仍被渲染，实际为空——
  因为 `close()` 按设计**连已排程未渲染的值一起丢弃**（正在关窗，渲染无意义）。

**修复**：改测试期望，并**把语义写进 Javadoc**（`FxBridge.close()` 与 `RecordingFxDispatcher.drain()`），
另加 `drainCurrent()` 用于观察「一帧之内」的中间状态。

**教训**：测试红先问「谁错了」。这轮两处都是**期望**错，代码是对的——若反过来改代码去迎合错误期望，
就会把正确设计改坏。

### 2.4 测试日志污染构建日志（`F-11` 复发）

桌面端第一个记录日志的测试一跑，构建日志立刻出现：

```
[INFO] [stdout] 2026-10-07T17:16:30.833315436Z main ERROR UI 渲染失败
```

**根因**：`aha-desktop` 是唯一没有 `src/test/resources/log4j2-test.xml` 的模块（实测 6 个模块里只有 `aha-core` 有），
于是 log4j2 退回默认 console 配置。**修复**：补该文件（写文件 + 关 console），与 `aha-core` 同一口径。

**教训**：新增模块时，「测试日志出口」要和 `module-info`、POM 一样当成**必配项**。

### 2.5 用例数预估错，被脚本自校验拦下

我在正文里按「605 − 15 + 3」心算成 613，并从 surefire 报告实测到 **615**。
所幸写入文档前有一步：

```python
assert fails == 0 and total == 613, f"用例数不符预期：{total}"
```

断言先于任何写入 → 文档**一个字符都没被污染**，改用实测值重跑。

**教训**：文档里的实测数字必须**来自测量**，且写入前先自校验；心算只配当预言，不能当数据。

## 3. 最终验证结果

| 项 | 结果 |
|---|---|
| `./mvnw clean verify`（Maven 4.0.0-rc-7） | ✅ BUILD SUCCESS（2 分 44 秒） |
| `mvn clean verify`（Maven 3.9.11） | ✅ BUILD SUCCESS（3 分 14 秒） |
| 用例 | **615**（common 60 / extension-api 6 / core 207 / tool 25 / cli 309 / desktop 8，其中 1 例跳过） |
| 行覆盖率（合计） | **80.3%（4049/5040）**，门禁 `BUNDLE` ≥ 0.70 通过 |
| 桌面端测试 | `FxBridgeTest` 6 例 + `FxThreadContractTest` 1 例**默认运行**；窗口冒烟**默认跳过**（本机 WSL 无 GTK，实测 `libgtk-3` 命中数 0） |
| 版本号注入 | `aha-common-0.1.1.jar` 内 `version.properties` = `version=0.1.1`（资源过滤生效） |
| 文档检查 | `CheckDocs` / `CheckSkills` / `CheckScripts` 全过 |

## 4. 关键教训

1. **原子替换必须断言「内容变了」**（`t2 != t`），否则静默 no-op 会伪装成成功——本轮已是第 5 次。
2. **对仓库的判断先查再写**：`log4j2.xml`、「POM 有 `<build>`」、「目录会自动建」都是想当然。
3. **「找不到」不等于「缺」**：判断缺口前先问「这个东西本来应该存在吗」。
4. **测试红先分责**：是代码错还是期望错？本轮两次都是期望错。
5. **数字来自测量，且写入前自校验**：心算的 613 被实测的 615 纠正，文档零污染。
6. **无图形环境的模块，测试要分两层**：默认可跑的逻辑测试（桥接契约）+ 显式开关的冒烟测试
   ——既守住「测试环境无关」，又不把检查做成空转。

## 5. 涉及文件清单

**代码**

- `aha-common/src/main/java/com/acanx/module/aha/common/AppVersion.java`（新建，自 `aha-cli` 下移）
- `aha-common/src/main/resources/com/acanx/module/aha/common/version.properties`（移动）
- `aha-common/pom.xml`（新增 `<build><resources>` 资源过滤）
- `aha-cli/src/main/java/com/acanx/module/aha/cli/CliVersionProvider.java`（新建，承接 picocli 适配）
- `aha-core/src/main/java/com/acanx/module/aha/core/logging/LoggingSetup.java`（新建，自 `aha-cli` 下移并转 `public`）
- `aha-core/pom.xml`、`aha-core/src/main/java/module-info.java`（`log4j-core` 转 `compile`；补 requires 与 export）
- `aha-cli/pom.xml`、`aha-cli/src/main/java/module-info.java`（移除本模块资源过滤与 Log4j requires）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/AhaDesktopApp.java`（改为 `Application`）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/fx/{FxDispatcher,PlatformFxDispatcher,FxBridge}.java`（新建）
- `aha-desktop/src/main/java/module-info.java`、`aha-desktop/pom.xml`（补 JavaFX / common / slf4j requires 与测试依赖）

**测试**

- `aha-common/src/test/java/.../AppVersionTest.java`、`aha-cli/.../CliVersionProviderTest.java`（新建）
- `aha-core/src/test/java/.../logging/LoggingSetupTest.java`（移动）
- `aha-desktop/src/test/java/.../fx/{FxBridgeTest,FxThreadContractTest,RecordingFxDispatcher}.java`（新建）
- `aha-desktop/src/test/java/.../AhaDesktopSmokeTest.java`（新建，默认跳过）
- `aha-desktop/src/test/resources/log4j2-test.xml`（新建，补测试日志出口）

**文档**

- `Docs/Design/DesktopDesign.md`（§4 重写、§6 线程模型新增、§5.6 更正）
- `Docs/Design/LoggingDesign.md`、`Docs/Design/ArchitectureOverview.md`
- `Docs/AHA/AHA-Design-V1.md`（模块表、目录树、§6 桌面端、§3.1 实测值）
- `Docs/DevSpec/ReleaseProcess.md`、`Docs/DevSpec/TestingSpec.md`
- `Docs/Guide/ConfigurationGuide.md`、`Docs/Guide/ReferenceGuide.md`
- `Docs/TODO.md`（`D-04`/`D-07`/`D-10` 结项、新增 `D-11`）、`Docs/PLAN.md`、`CHANGELOG.md`

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- `aha-core` 编译失败，原文：`package org.apache.logging.log4j is not visible (package … is declared in
  the unnamed module, but module com.acanx.module.aha.core does not read it)`。
  直接看文件后确认：`module-info.java` 里**根本没有那两行 `requires`**。
- 根因是脚本匹配失败：脚本按 **8 个空格**缩进匹配模块体语句，实际是 **4 个空格**；
  `str.replace` 不匹配时**原样返回、不报错**，脚本仍打印了 ✅。
  —— 这是本项目**第 5 次**栽在同一个坑上（前 4 次分别在文档编辑与代码编辑）。
- 三处「先假设后动手」的返工：`shutil.move` 目标是**不存在的目录** → `FileNotFoundError`；
  `aha-common/pom.xml` **没有** `<build>` 段；「桌面端缺 `log4j2.xml`」实际是**全仓原本就没有**该文件。
- 两个失败用例**都是期望错**：`valueArrivingDuringRenderIsNotLost` 期望「第一帧只渲染 `first`」，
  实际渲染了两个值（测试替身 `drain()` 会**连带执行渲染期间新排的任务**）；
  `closedBridgeDropsLaterSubmissions` 期望 `close()` 前提交的值仍被渲染，实际为空
  （`close()` 按设计**连已排程未渲染的值一起丢弃**）。
- 测试日志污染构建日志（`F-11` 复发）：`[INFO] [stdout] 2026-10-07T17:16:30.833315436Z main ERROR UI 渲染失败`；
  实测 6 个模块里**只有 `aha-core`** 有 `src/test/resources/log4j2-test.xml`。
- 用例数：正文里按「605 − 15 + 3」心算成 **613**，surefire 报告实测 **615**；
  写入前的自校验断言（`assert fails == 0 and total == 613`）先拦下 → **文档一个字符都没被污染**。
- 最终实测：`clean verify`（Maven 4.0.0-rc-7）BUILD SUCCESS **2 分 44 秒**；
  （Maven 3.9.11）BUILD SUCCESS **3 分 14 秒**；用例 **615**
  （common 60 / extension-api 6 / core 207 / tool 25 / cli 309 / desktop 8，其中 **1 例跳过**）；
  行覆盖率合计 **80.3%（4049 / 5040）**，门禁通过；
  `aha-common-0.1.1.jar` 内 `version.properties` = `version=0.1.1`（资源过滤生效）；
  `FxBridgeTest` 6 例 + `FxThreadContractTest` 1 例**默认运行**，
  **窗口冒烟默认跳过**（本机 WSL 无 GTK，实测 `libgtk-3` 命中数 **0**）。

### 机制 / 行为模型

- **`str.replace` 不匹配时静默返回原串** ⇒ 「脚本报 ✅」与「内容真的变了」是两件事。
  断言 `old in t` 只证明「**找得到**」，`t2 != t` 才证明「**改到了**」。
  依据：「2.1」的一次静默 no-op 与随后补上断言的修法。
- 「**缺失的证据不等于缺口的证据**」：包内没有 `log4j2.xml` ≠ 缺配置——
  JPMS 下 `getResources` 不搜模块路径，装配本来就是**程序化**的（`LoggingSetup` 的 Javadoc 早已写明）。
- `runLater` 的真实语义：**渲染期间新排的任务会被同一次 drain 执行**；
  `close()` 的真实语义：**连已排程未渲染的值一起丢弃**（正在关窗，渲染无意义）。
- **log4j2 找不到 `log4j2-test.xml` 时会退回默认 console 配置** ⇒ 测试日志污染构建日志。

### 未定位 / 未复现

- **本机 WSL 无 GTK**（`libgtk-3` 命中数 0）⇒ 「窗口能起来」这类问题在本机**无法复现、无法验证**，
  冒烟测试**默认跳过**。
  > 该阻塞点后来由 `TS-2610-032` 与 issue **#144** 处置：CI 用 **Xvfb** 跑完整 GUI 套件（`ui-smoke`），
  > 本机不再是唯一环境。
- 原子替换的「带断言」写法**是否已全仓推广**：档案只写「所有就地修改改用带断言的辅助函数」，
  没有给出全仓清单与残留核查；前 4 次同类坑的位置也未逐一列出。
- 「测试日志出口」**未纳入机器检查**：只在档案里写成教训（增模块时当必配项），没有脚本断言。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 编译失败原文 | 构建日志 | `package org.apache.logging.log4j is not visible (package … is declared in the unnamed module, but module com.acanx.module.aha.core does not read it)` |
| 「替换未生效」的直接证据 | 直接读 `aha-core` 的 `module-info.java` | 文件里**没有**那两行 `requires`（脚本却打印了 ✅） |
| 静默失败的机制 | 脚本与原文缩进比对 | 脚本匹配 **8 个空格**，实际 **4 个空格** |
| 假设错误 1 | 运行脚本 | `FileNotFoundError`（`shutil.move` 目标目录不存在） |
| 假设错误 2 | 查 `aha-common/pom.xml` | **无** `<build>` 段，追加式改动需单独处理 |
| 假设错误 3 | 全仓查该文件 | `log4j2.xml` **全仓原本就没有**（装配是程序化的） |
| 测试日志污染 | 构建日志 | `[INFO] [stdout] 2026-10-07T17:16:30.833315436Z main ERROR UI 渲染失败` |
| 缺测试日志配置的模块 | `find` 各模块 `src/test/resources` | 6 个模块里**只有 `aha-core`** 有 `log4j2-test.xml` |
| 用例数（**心算 vs 实测**） | 心算 613 / surefire 报告 | 实测 **615**（自校验断言拦下，文档零污染） |
| 双 Maven 版本构建 | `clean verify` | Maven 4.0.0-rc-7：SUCCESS / **2 分 44 秒**；Maven 3.9.11：SUCCESS / **3 分 14 秒** |
| 用例分布 | surefire 报告 | **615**（common 60 / extension-api 6 / core 207 / tool 25 / cli 309 / desktop 8，1 例跳过） |
| 覆盖率 | 构建报告 | 合计 **80.3%（4049 / 5040）**，门禁 `BUNDLE` ≥ 0.70 通过 |
| 无图形环境的证据 | 本机探测 | `libgtk-3` 命中数 **0** ⇒ 窗口冒烟默认跳过 |
| 版本资源注入 | 解包 `aha-common-0.1.1.jar` | `version.properties` = `version=0.1.1` |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「log4j 被放到 classpath 而非 module path」（最初的怀疑，差点去调 surefire / 编译器） | 直接读文件原文：`module-info.java` 里**没有**那两行 `requires`（「2.1」） |
| 「原子替换已生效」 | 脚本打印的 ✅ 是**自我报告**；文件实际未被改动（「2.1」） |
| 「桌面端缺 `log4j2.xml` 配置」 | 全仓原本**没有**该文件，装配一直是程序化的（「2.2」，且 `LoggingSetup` 的 Javadoc 已写明原因） |
| 「`shutil.move` 会自动建目标目录」 | 实测 `FileNotFoundError`（「2.2」） |
| 「`aha-common/pom.xml` 有 `<build>` 段」 | 实际没有，追加式改动需单独处理（「2.2」） |
| 「测试红 ⇒ 代码错」 | 两处都是**期望错**：`drain()` 与 `close()` 的行为正是设计语义（「2.3」） |
| 「用例数是 613」 | surefire 实测 **615**；自校验断言先于写入，文档未被污染（「2.5」） |

## 未决问题

**Q1 原子替换的「带断言」写法，全仓推广到位了吗？**

现状：本轮改成了 `must()` 辅助函数（`assert old in t` + `assert t2 != t`）；
没有全仓清单，也没有残留核查——**前 4 次同类坑**是否都已加断言，档案未逐一列出。
阻塞点：需要一次全仓扫描（找出所有就地修改脚本），尚未做。

**Q2 无图形环境下，「窗口能起来」怎么守？**

现状：**默认跳过**冒烟测试，因此本机对这类缺陷没有回归保护。
阻塞点：本机 WSL 无 GTK。
> **进展（2026-10-13）**：已由 issue **#144** / PR #145 处置——CI 新增非阻塞作业
> `ui-smoke`，在 **Xvfb** 下用 `-Daha.ui.tests=true` 执行完整 GUI 套件；
> 详见 `TS-2610-032` 与 `Docs/DevLog/20261013-16.md`。

**Q3 「测试日志出口」纳入机器检查了吗？**

现状：只在档案里写成教训（新增模块时与 `module-info`、POM 并列为必配项）。
阻塞点：可写成脚本断言「每个含测试的模块都有 `log4j2-test.xml`」，尚未评估。

**Q4 `F-11`（测试日志污染构建日志）会再复发吗？**

现状：本轮补了 `aha-desktop` 的测试日志配置，但**新增模块时仍可能忘记**（Q3 未落地）。
阻塞点：与 Q3 同一件事。
