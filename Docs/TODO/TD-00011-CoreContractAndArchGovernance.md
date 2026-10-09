# TD-00011 CoreContractAndArchGovernance

> 待办编号：TD-00011
> 标题：核心健壮性与架构治理：AgentEngine 契约、ServiceLoader 双声明、依赖治理、jlink 解除条件
> 状态：☐ 未开始
> 跟踪 Issue：[82](https://github.com/ACANX/AHA/issues/82)
> 创建日期：2026-10-09
> 最后更新：2026-10-09

---

## 来源

`Docs/TODO.md` 第 2 节（代码健壮性）、第 3 节（架构与治理）、`A-07`。

## 1. 核心契约（B-01 / B-02，☐ 未完成）

`AgentEngine` 现状（已核实未改动）：

- `run()`：首轮 `response.choices()` 为空时 `break`，`content` 保持 `null`；
- `stream()`：`roundFinishReason` 初始为 `null`，仅 `DONE` 时赋值——流异常中断则 `finishReason` 为 `null`。

待办：

1. 明确 `AgentResponse.content()` 契约（允许 `null` 还是返回空串），写入 Javadoc；
2. 调用方（`LocalAgentService` / `ChatCommand` / `RunCommand`）统一判空；
3. 补测试覆盖「首轮空 choices」「流中断无 DONE」两条边界。

## 2. 架构治理（C-02 / C-03 / C-04）

- **C-02 `ServiceLoader` 双声明**（⏸ 待决策）：工具与适配器同时在 `module-info` 的 `provides` 与 `META-INF/services` 声明。三选一：维持并登记理由 / 删除 classpath 兼容 / 加脚本或注解处理器校验两处一致。决策时一并考虑 `E-04`（双声明在 native-image 下反而降低配置成本）。
- **C-03 依赖治理**（☐）：父 POM 无 `maven-enforcer-plugin`、无 ArchUnit。评估引入 `bannedDependencies` 或 ArchUnit 作为 JPMS 的**补充**，把 `BuildSpec.md` §7 的约定变成构建期失败。
- **C-04 `jlink` 解除条件**（☐）：`BuildSpec.md` §7 仍写「禁止在 0.1 使用 `jlink`——`sqlite-jdbc` 为自动模块」，未补解除条件，读起来像永久约束。补记：若该驱动未来提供 `module-info`，`jlink` 可显著缩小发行包，应重新评估。

## 3. 引用链核对（A-07，◐ 进行中）

`BuildSpec.md` §6 仍写「排除项 | 必须登记理由（见设计文档第十二部分 3.3）」。`Docs/AHA/AHA-Design-V1.md` 为多文档拼接、存在多个同名 `## 1.` / `## 2.`，该引用**极可能已失准**。二选一：改引 `TestingSpec.md` §3.2，或修正部分号与小节号。

## 验收标准

- [ ] `AgentResponse.content()` 与 `finishReason` 的契约写进 Javadoc，调用方统一判空，两条边界有测试；
- [ ] `C-02` 拍板并落到 `ModuleConvention.md`；
- [ ] `C-03` 给出「引入 / 不引入」结论与理由（若引入，同步 `Constitution.md` 第 5 条选型清单）；
- [ ] `C-04` 在 `BuildSpec.md` §7 补上解除条件；
- [ ] `A-07` 的引用改到正确目标，全仓对覆盖率排除项的引用统一。

## 关联

- `Docs/TODO.md` 第 2 / 3 节、`A-07`
- `Docs/DevSpec/Constitution.md`、`ModuleConvention.md`、`BuildSpec.md`、`TestingSpec.md`

