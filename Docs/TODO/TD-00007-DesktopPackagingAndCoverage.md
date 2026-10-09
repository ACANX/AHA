# TD-00007 DesktopPackagingAndCoverage

> 待办编号：TD-00007
> 标题：桌面端交付与质量：覆盖率门禁上线（0.30 → 0.70）（`jpackage` 部分已决策跳过）
> 状态：☐ 未开始
> 跟踪 Issue：[78](https://github.com/ACANX/AHA/issues/78)
> 创建日期：2026-10-09
> 最后更新：2026-10-09

---

## 来源

- `Docs/Dbsx.txt`「0.2 桌面端 · 下一步待办」第 10 项（覆盖率门禁上线）；第 11 项（`jpackage` 自包含安装包）**已决策跳过**
- `Docs/TODO.md` `D-05`（已决策：纳入门禁 + 单独阈值）、`D-08`（流水线已就位）

## 范围

### 1. 覆盖率门禁上线（`D-05`）

已决策：桌面端**纳入覆盖率门禁**，用**单独阈值**——0.2 开发期 **0.30**，0.2 收尾评审后向其它模块的 **0.70** 对齐。现状是父 POM 仍泛排除 `com/acanx/module/aha/desktop/**`。

### 2. ~~`jpackage` 自包含安装包~~（**已决策跳过，2026-10-09**）

`Dbsx` 第 11 项的 `jpackage` 安装包（MSI / DEB / DMG）**已决策跳过、不采用**：发行形态最终只保留
「JVM JAR 聚合包（解压后脚本启动）」与「原生镜像二进制」两种——「免装 JDK」由后者覆盖，
也不必再维护安装器 + 签名 / 公证链路。理由与口径见 `ReleaseProcess.md` §3.3。

> 因此本 TD 的范围只剩第 1 节（覆盖率门禁上线）。

## 验收标准

- [ ] `TestingSpec.md` §3 为 Desktop 明确写出阈值（0.2 期 0.30），`jacoco:check` 实际生效（抬阈值能失败）；
- [ ] 0.2 收尾时评估是否提升到 0.70，并记录结论；
- [ ] macOS 保持「只打包、不跑测试」（`D-06` 决策）。
- ~~CI 按平台产出 MSI / DEB / DMG，`Release.yml` 资产矩阵覆盖~~（`jpackage` 已决策跳过）
- ~~安装版在无 JDK 的机器上可安装并启动~~（`jpackage` 已决策跳过）

## 关联

- `Docs/TODO.md` `D-05` / `D-08`、`Docs/DevSpec/TestingSpec.md` §3、`Docs/DevSpec/ReleaseProcess.md`

