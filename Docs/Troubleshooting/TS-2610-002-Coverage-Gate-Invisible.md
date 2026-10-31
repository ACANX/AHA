# TS-2610-002 · CI 排障复盘：一个「看得见才敢信」的覆盖率门禁

> 日期：2026-10-07
> 作者：@ACANX（与 AI 协作）
> 关联 PR：[#6](https://github.com/ACANX/AHA/pull/6)
> 关联记录：[TODO.md](../TODO/README.md) `F-09`、[TestingSpec.md](../DevSpec/TestingSpec.md) §3.1、`bin/ReportCoverage.py`

---

## 一、背景

`Gate.yml` 里写着「完整构建与覆盖率门禁（LINE COVEREDRATIO ≥ 0.70）」，但 CI 日志中
**看不到任何覆盖率数字**——只有每模块两行：

```
[INFO] --- jacoco:0.8.15:report (report) @ aha-common ---
[INFO] Loading execution data file .../aha-common/target/jacoco.exec
[INFO] Analyzed bundle 'AHA-Common' with 27 classes
[INFO] --- jacoco:0.8.15:check (check) @ aha-common ---
[INFO] Analyzed bundle 'aha-common' with 27 classes
```

于是出现一个合理的质疑：**看不到数据，凭什么说有门禁？**

这个质疑必须被认真对待：**日志里无法区分「门禁在拦」与「门禁没配」**。

## 二、核实过程

### 2.1 先确认配置属于「会拦人」的那种

| 检查点 | 结果 |
| --- | --- |
| 规则挂在哪个 goal | `check`（**不是** `report`）——规则挂到 `report` 上只会出报告、不拦人，是常见配错 |
| 绑定阶段 | `verify` |
| 规则内容 | `BUNDLE` / `LINE` / `COVEREDRATIO`，`minimum = 0.70` |
| 声明位置 | `<build><plugins>`（`pom.xml` 第 226 行起），**不是**仅 `pluginManagement` |

配置本身正确。

### 2.2 再证明它真的会拦（关键一步）

只读配置只能证明「应该会拦」。把它**弄失败一次**才是证明：临时把阈值改成 `0.99`，
只跑一个模块：

```bash
./mvnw -pl aha-common verify
```

```
[INFO] --- jacoco:0.8.15:check (check) @ aha-common ---
[WARNING] Rule violated for bundle aha-common: lines covered ratio is 0.90, but expected minimum is 0.99
[ERROR] Failed to execute goal org.jacoco:jacoco-maven-plugin:0.8.15:check (check) on project aha-common:
        Coverage checks have not been met. See log for details.
```

结论：**门禁在，而且会拦**。随后恢复 `pom.xml` 并逐字节核对（无残留改动）。

### 2.3 那为什么日志里没有数字

JaCoCo 的 `check` **通过时不打印任何百分比**，`report` 也只报「分析了哪个 bundle」。
数字只存在于 `target/site/jacoco/` 下的 HTML / CSV / XML 报告中。

也就是说：**一个能拦人的门禁，在日志上与「没配」看起来一模一样。**

## 三、修复：让门禁自证

- 新增 `bin/ReportCoverage.py`：读各模块的 `jacoco.csv`，打印每模块与合计的行/分支覆盖率，
  并从 `pom.xml` 读取阈值一并打印（**不在脚本里复制阈值**）。
- `Gate.yml` 在 `clean verify` **之后**加一步执行该脚本。因此：
  **能跑到这一步，就说明门禁已通过**；数字同时留在了日志里。

输出示例：

```
=== 覆盖率实测（JaCoCo） ===
  模块                        行覆盖    分支覆盖
    aha-cli                  83.7%     2246/2684     68.0%
    aha-common               90.2%       165/183     80.4%
    aha-core                 74.5%     1458/1957     57.7%
    aha-extension-api       100.0%         23/23         —
    aha-tool                 79.6%       148/186     77.8%
    合计                     80.3%     4040/5033     64.5%
    合计不含：aha-desktop（0.1 占位模块，由 pom.xml 的 jacoco excludes 排除）

门禁阈值：0.70（读自 pom.xml；判定由 jacoco:check 执行，本脚本只报数）
```

## 四、最终验证结果

| 项 | 结果 |
| --- | --- |
| `./mvnw clean verify`（全量） | BUILD SUCCESS ✅（604 用例：57 / 6 / 193 / 25 / 323） |
| 覆盖率实测 | 合计 **80.3%**（4040 / 5033 行），最高分支 80.4% |
| 门禁可拦性 | 阈值抬到 0.99 时 `aha-common`（0.90）被拦，`BUILD FAILURE` ✅ |
| `pom.xml` 还原 | 与备份逐字节一致 ✅ |
| 文档口径一致性 | 脚本输出与 `TestingSpec.md` / `AHA-Design-V1.md` / `ArchitectureOverview.md` 记录的实测值一致 ✅ |

## 五、关键教训

1. **静默的门禁无法自证。** 通过时不出声，与「没配」在日志上不可区分。门禁必须把
   「我在、我按什么标准、实测多少」写进日志——否则每次都要靠人工翻报告或改配置去试。
2. **验证门禁的方式是让它失败一次。** 只读配置只能证明「应该会拦」；临时抬高阈值、
   看到 `Rule violated`、再还原，是最短的证明路径，成本约一分钟。
3. **`report` 不判定，`check` 才判定。** 规则 `<rules>` 挂到 `report` 执行上，会得到一个
   「只出报告、不拦人」的假门禁——这类错误不会报错，只是永远不失败。
4. **判定保持单一来源。** 新增的汇总脚本只**报数**、不重复判定，阈值从 `pom.xml` 读；
   两处各写一份阈值，迟早不一致。
5. **实测值要与文档口径对得上。** 脚本输出（80.3% / 4040 / 5033）与文档记录一致，
   才说明口径没漂；不一致时优先怀疑统计口径而非代码。

## 六、涉及文件清单

| 文件 | 变更 |
| --- | --- |
| `bin/ReportCoverage.py` | 新增：读 JaCoCo CSV 输出各模块与合计覆盖率（含 CJK 对齐、阈值读自 pom） |
| `.github/workflows/Gate.yml` | `clean verify` 之后新增「覆盖率实测值」步骤 |
| `Docs/DevSpec/TestingSpec.md` | §3.1 说明门禁通过时不打印百分比，改看汇总脚本输出 |
| `Docs/DevSpec/BuildSpec.md` | §8.1 门禁内容补「并打印覆盖率实测值」 |
| `Docs/Guide/BuildGuide.md` | §5 补本地查看覆盖率的两种方式 |
| `Docs/TODO.md` | 新增 `F-09`（门禁静默、无法自证）并记录本次验证方法与证据 |
| `CHANGELOG.md` | 补记 |

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「一、二、…」编号冲突。 -->

## 结论分层

### 已观测事实

- `Gate.yml` 写着门禁 `LINE COVEREDRATIO ≥ 0.70`，但 CI 日志中**看不到任何覆盖率数字**，
  每模块只有两行（`jacoco:report` 与 `jacoco:check` 各一行 `Analyzed bundle`）。
- 配置逐项核查**均正确**：规则挂在 `check`（不是 `report`）、绑定 `verify`、
  `BUNDLE` / `LINE` / `COVEREDRATIO`、`minimum = 0.70`、声明在 `<build><plugins>`（`pom.xml` 第 226 行起）。
- **门禁确会拦人**（把阈值临时抬到 `0.99` 后实测）：
  `[WARNING] Rule violated for bundle aha-common: lines covered ratio is 0.90, but expected minimum is 0.99`，
  以及 `[ERROR] Failed to execute goal org.jacoco:jacoco-maven-plugin:0.8.15:check (check) … Coverage checks have not been met.`
- `pom.xml` 还原后与备份**逐字节一致**。
- 全量 `clean verify` → **BUILD SUCCESS**，604 用例（57 / 6 / 193 / 25 / 323）。
- 覆盖率实测：合计 **80.3%（4040 / 5033 行）**、分支 64.5%；最低 `aha-core` 74.5%，
  最高 `aha-extension-api` 100.0%（23 / 23 行，分支 `—`）。

### 机制 / 行为模型

- JaCoCo 的 `check` **通过时不打印任何百分比**，`report` 也只报「分析了哪个 bundle」；
  数字只存在于 `target/site/jacoco/` 下的 HTML / CSV / XML。
  → 一个**能拦人**的门禁，在日志上与「**根本没配**」看起来一模一样。依据：上列两端原始输出。
- 「规则挂到 `report` 上」会得到一个只出报告、不拦人的**假门禁**，而且不会报错。
  → 这是由 Maven 插件 goal 语义推出的模型（`report` 不判定、`check` 才判定），**本档案未实测该错配**。
- 自证的办法：把汇总脚本放在 `clean verify` **之后**——**能跑到这一步就说明门禁已通过**，
  数字同时落进日志。依据：本档案的修复设计（「三、修复」）。

### 未定位 / 未复现

- **未实测**「把 `<rules>` 挂到 `report` 执行上」的假门禁形态（属常见配错，来自推理而非本机复现）。
- **未测** `bin/ReportCoverage.py` 在「跳过测试 / 缺 `jacoco.csv`」时的行为（可能静默报 0，也可能抛错）。
- **未评估**阈值 `0.70` 本身是否合理：本档案只证明它「在、且会拦」，没有讨论是否该抬高或分模块设阈值。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 门禁通过时的日志（**好样本：没有任何数字**） | `Gate.yml` 的 CI 日志 | `[INFO] --- jacoco:0.8.15:check (check) @ aha-common ---` / `[INFO] Analyzed bundle 'aha-common' with 27 classes` |
| 门禁失败时的日志（**坏样本**） | `-pl aha-common verify`（阈值临时改 `0.99`） | `[WARNING] Rule violated for bundle aha-common: lines covered ratio is 0.90, but expected minimum is 0.99` |
| 失败时的构建结局 | 同上 | `[ERROR] Failed to execute goal org.jacoco:jacoco-maven-plugin:0.8.15:check (check) on project aha-common: Coverage checks have not been met.` |
| 实测覆盖率（逐模块） | `python3 bin/ReportCoverage.py` | `aha-cli 83.7%` / `aha-common 90.2%` / `aha-core 74.5%` / `aha-extension-api 100.0%` / `aha-tool 79.6%` / **合计 80.3%（4040 / 5033）** |
| 门禁阈值（单一来源） | 读自 `pom.xml`（脚本不复制阈值） | `0.70` |
| 全量构建与用例数 | `clean verify` | `BUILD SUCCESS`；604 用例（57 / 6 / 193 / 25 / 323） |
| 配置还原 | 与备份逐字节比对 | 一致 ✅ |

> 对照关系已满足规范要求：**坏样本**（抬阈值 → `Rule violated`，实测 0.90 对阈值 0.99）
> 与**好样本**（正常通过 → 日志无数字）并列给出。

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 门禁根本没配 / 规则挂在 `report` 上（假门禁） | 逐项核查：挂在 `check`、绑定 `verify`、声明在 `<build><plugins>` 而非仅 `pluginManagement`（「二、2.1」） |
| 门禁配了但不生效（`<rules>` 为空等） | 抬阈值到 `0.99` 后 `aha-common`（实测 0.90）被拦、`BUILD FAILURE`（「二、2.2」） |
| 门禁其实一直在失败、只是没看日志 | 全量 `clean verify` 为 `BUILD SUCCESS`，604 用例通过；实测 80.3% 高于阈值 0.70 |
| `jacoco:check` 没有执行（只是没输出） | 日志中 `jacoco:0.8.15:check (check)` 确实执行，且有 `Analyzed bundle` 行 |
| 验证过程改坏了 `pom.xml` | 还原后与备份逐字节一致 ✅ |

## 未决问题

**Q1 阈值 `0.70` 该不该抬？**

现状：合计 80.3%、最低模块 `aha-core` 74.5%，门禁 0.70 实际上「拦不到什么人」。
阻塞点：抬高会卡住低覆盖模块的日常提交，需要先有补齐计划；本档案只做「门禁是否生效」的核实，
**不做阈值决策**。

**Q2 「假门禁」（把规则挂到 `report`）能不能机器拦？**

现状：只写进 `TestingSpec.md` §3.1 的文字说明，**没有自动检查**。
阻塞点：需要在 CI 或 `CheckDocs.py` 一类脚本里断言「jacoco `check` 的 `<rules>` 非空」，尚未落地。

**Q3 汇总脚本在「跳过测试 / 缺 `jacoco.csv`」时怎么办？**

现状：**未测**。若静默报 0 或抛栈，都会让「能跑到这一步就说明门禁已过」这条自证链条失效。
阻塞点：需要构造该场景并补一次实测。

**Q4 `aha-desktop` 何时纳入门禁？**

现状：脚本输出中如实标注「合计不含：aha-desktop（0.1 占位模块，由 `pom.xml` 的 jacoco excludes 排除）」。
阻塞点：该模块为占位态，纳入时机未定。
