# TS-202610-CoverageGateInvisible：CI 排障复盘：一个「看得见才敢信」的覆盖率门禁

> 日期：2026-10-07
> 作者：@ACANX（与 AI 协作）
> 关联 PR：[#6](https://github.com/ACANX/AHA/pull/6)
> 关联记录：[TODO.md](../TODO.md) `F-09`、[TestingSpec.md](../DevSpec/TestingSpec.md) §3.1、`bin/ReportCoverage.py`

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
