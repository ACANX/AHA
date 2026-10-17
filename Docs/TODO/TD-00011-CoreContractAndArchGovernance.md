# TD-00011 CoreContractAndArchGovernance

> 待办编号：TD-00011
> 标题：核心健壮性与架构治理：AgentEngine 契约、ServiceLoader 双声明、依赖治理、jlink 解除条件
> 状态：☐ 未开始
> 跟踪 Issue：[82](https://github.com/ACANX/AHA/issues/82)
> 创建日期：2026-10-09
> 最后更新：2026-10-17（`A-07` 引用链已收口，见 §3）

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

## 3. 引用链核对（A-07，✅ 已收口 2026-10-17）

**核对结论（与立项时的猜测相反）**：那句「见设计文档第十二部分 3.3」**指向是准的** ——
`AHA-Design-V1.md` 的 `# 第十二部分：测试策略` 下确有 `### 3.3 覆盖率排除项`。
真正失准的是**口径**：那份表与 `TestingSpec.md` §3.2、根 POM 的 jacoco `excludes` 三者已经漂移
（设计文档里桌面端一行还写着「桌面端 0.2 实现」，而实际是 0.1 整体排除、0.2 起按 `D-05` 用单独
阈值 0.30 纳入；规范里的包路径也还是简写形态）。

**采用方案 1 + 补口径**（`TestingSpec.md` §3.2 为唯一权威登记处）：

| # | 落点 | 改动 |
|---|---|---|
| 1 | `TestingSpec.md` §3.2 | 声明为**唯一权威登记处**：根 POM 的 `jacoco` `excludes` 与表格逐条对应，**改 POM 必须同步本节**；表格改用 POM 的包路径形态，新增「何时解除 / 由什么替代覆盖」列 |
| 2 | `BuildSpec.md` §6 | 「排除项」引用改为 `TestingSpec.md` §3.2（v1.46.1） |
| 3 | `AHA-Design-V1.md` 第十二部分 3.3 | 修正漂移的桌面端行；注明权威登记处在规范、**不一致时改本节**（v3.74.0） |
| 4 | 技能 `test` 的 `references/coverage-guide.md` | 不再自持一份口径，改为速查 + 「以规范为准」（技能 2.0.1） |

验证：`grep` 全仓，对「覆盖率排除项」的引用只剩「指向 `TestingSpec.md` §3.2」与设计文档内的自指；
`python3 bin/CheckDocs.py` 通过。

## 验收标准

- [ ] `AgentResponse.content()` 与 `finishReason` 的契约写进 Javadoc，调用方统一判空，两条边界有测试；
- [ ] `C-02` 拍板并落到 `ModuleConvention.md`；
- [ ] `C-03` 给出「引入 / 不引入」结论与理由（若引入，同步 `Constitution.md` 第 5 条选型清单）；
- [ ] `C-04` 在 `BuildSpec.md` §7 补上解除条件；
- [x] `A-07` 的引用改到正确目标，全仓对覆盖率排除项的引用统一（2026-10-17，`TestingSpec.md` §3.2 为唯一权威登记处）。

## 关联

- `Docs/TODO.md` 第 2 / 3 节、`A-07`
- `Docs/DevSpec/Constitution.md`、`ModuleConvention.md`、`BuildSpec.md`、`TestingSpec.md`

## 原文摘录（来源文件已于 2026-10-17 删除）

### `Docs/TODO.md` 条目

| B-01 | `AgentEngine.run()` 空 choices 时返回 `null` 正文 | 风险 | ✅ | P1 | ☐ 未完成 | `AgentEngine`、`AgentServiceDesign.md` |
| B-02 | `AgentEngine.stream()` 的 `finishReason` 可能为 `null` | 风险 | ✅ | P1 | ☐ 未完成 | 同上 |
| C-02 | `ServiceLoader` 双声明的长期维护成本 | 待决策 | ✅ | P3 | ⏸ 待决策 | `ModuleConvention.md` |
| C-03 | 依赖治理补强（Enforcer / ArchUnit） | 调研 | ✅ | P2 | ☐ 未完成 | `BuildSpec.md` |
| C-04 | `jlink` 禁令的解除条件未登记 | 待决策 | ✅ | P3 | ☐ 未完成 | `BuildSpec.md` §7 |
| C-02 | `ModuleConvention.md` | 补充 `ServiceLoader` 声明要求 | ☐ 未完成（待决策） |
| C-03 | `BuildSpec.md` §7 | 补充依赖治理手段（若采纳） | ☐ 未完成 |
| C-04 | `BuildSpec.md` §7 | 补充 `jlink` 禁令解除条件 | ☐ 未完成 |

> **C-01**（原 `TODO.md` C-01）：`Constitution.md` 第 4 条、`ModuleConvention.md` 补充「为何 JPMS 存在 / 模块化优先」与复核点。
