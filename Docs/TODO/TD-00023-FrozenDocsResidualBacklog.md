# TD-00023 FrozenDocsResidualBacklog

> 待办编号：TD-00023
> 标题：三份冻结文档（`TODO.md` / `PLAN.md` / `Dbsx.txt`）的**遗留任务索引与最小任务拆解**
> 状态：◐ 进行中（索引已建；各条的执行仍在各自的 TD / Issue 上）
> 跟踪 Issue：无独立单（按条目分散跟踪，见「执行载体」列；总入口 [#86](https://github.com/ACANX/AHA/issues/86)）
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

`Docs/TODO.md` / `Docs/PLAN.md` / `Docs/Dbsx.txt` 三份文件**已冻结**（2026-10-09 起只读）。

- 「已完成 / 明确不做 / 已过时」的条目已归档到 [`Docs/DevLog/20261017-15.md`](../DevLog/20261017-15.md)（PR #271 / #272）；
- **本文档接管剩下那些「仍然要做」的条目**：把它们**拆成最小可执行任务**、编号（`T-01`…），
  并给出**执行载体**——执行的真相仍在各自的 `TD-*` / Issue 上，本文档只做索引与拆解，不另开第二条跟踪线。

> **为什么拆**：原条目普遍是「议题粒度」（例如「依赖治理补强（Enforcer / ArchUnit）」），
> 既无法验收也无法估算。这里统一拆到「一次提交能做完 + 有明确判据」的粒度。

## 用法

| 字段 | 含义 |
|---|---|
| **T-xx** | 最小可执行任务编号（本文档内唯一，稳定不复用） |
| **来源** | 原始出处（`TODO.md` 的 `A-01…` / `PLAN.md` 的 `§x` / `Dbsx.txt` 的第 N 项） |
| **最小动作** | 一次提交能完成的事（不含无关清理） |
| **验收** | 机器可判或人能一眼核对的判据 |
| **执行载体** | 真正跟踪状态的 `TD-*` 或 Issue —— **状态以载体为准** |

---

## 1. 代码缺陷（可直接开工）

| 编号 | 来源 | 最小动作 | 验收 | 执行载体 |
|---|---|---|---|---|
| **T-01** | `TODO.md` B-01 | 给 `AgentEngine.run()` 的空 `choices` 定语义并落地：返回空串（`content=""`）或抛领域异常，二选一写进 Javadoc | 单测覆盖 `choices == null` 与 `choices.isEmpty()` 两条路径；`AgentResponse.content()` 不再可能为 `null` | TD-00011 / [#82](https://github.com/ACANX/AHA/issues/82) |
| **T-02** | `TODO.md` B-02 | 给 `AgentEngine.stream()` 的 `finishReason` 定兜底值（流中断 / 无 `DONE` 事件）并写进 Javadoc | 单测断言「无 DONE 事件时 `finishReason` 为约定的兜底值」 | TD-00011 / [#82](https://github.com/ACANX/AHA/issues/82) |

> 现状核实（2026-10-17）：`AgentEngine.java:107` 的 `content` 初值为 `null`，`:118` 空 `choices` 直接
> `break`，`:208` 的 `finishReason` 仅在 `DONE` 事件赋值 ⇒ 两条都**真的没做**。

## 2. 架构与规范回填

| 编号 | 来源 | 最小动作 | 验收 | 执行载体 |
|---|---|---|---|---|
| **T-03** | `TODO.md` C-01 | `Constitution.md` 第 4 条 / `ModuleConvention.md` 补「为何模块化优先」与复核点 | 两处各一节，写明理由 + 何时重新评估 | TD-00011 / #82 |
| **T-04** | `TODO.md` C-02 | `ServiceLoader` 双声明（`provides` + `META-INF/services`）**拍板三选一**并落 `ModuleConvention.md` | 结论 + 理由 + 若「维持」则写清复核条件 | TD-00011 / #82 |
| **T-05** | `TODO.md` C-03 | 依赖治理给出「引入 / 不引入」Enforcer 或 ArchUnit 的结论 | 结论写进 `BuildSpec.md` §7；若引入则同步 `Constitution.md` 第 5 条 | TD-00011 / #82 |
| **T-06** | `TODO.md` C-04 | `BuildSpec.md` §7 补 `jlink` 禁令的**解除条件** | 写明触发条件（如 `sqlite-jdbc` 提供 `module-info` 后重新评估） | TD-00011 / #82 |
| **T-07** | `TODO.md` D-03 | `aha-desktop/module-info.java` 把 FXML 的 `opens` 收紧到实际用到的包/类 | `AhaDesktopSmokeTest` 仍绿（FXML 加载不破）；`opens` 范围与 `DialogsFxmlTest` 清单一致 | TD-00005 / [#76](https://github.com/ACANX/AHA/issues/76) |
| **T-08** | `TODO.md` D-06 | 平台清单对齐：`DesktopDesign.md` §2/§3 与 `Build.yml` / `Release.yml` 的矩阵口径 | 三处平台集合逐字一致（可用 `grep` 核对） | TD-00005 / #76 |
| **T-09** | `TODO.md` E-01 | `ModuleLayer` 与 native-image closed-world 冲突给出结论（禁用 / 静态注册 / 不发布） | 结论写进 `BuildSpec.md` §10；与 T-04 的双声明结论相互印证 | TD-00008 / [#79](https://github.com/ACANX/AHA/issues/79) |

## 3. 工程效能

| 编号 | 来源 | 最小动作 | 验收 | 执行载体 |
|---|---|---|---|---|
| **T-10** | `TODO.md` F-14 | 决定 `.github/**/*.yml` 是否纳入本地检查（如纳入，用 `yaml.safe_load` 全量解析即可） | 给出结论；若纳入则 `bin/CheckScripts.py` 新增一条并在本文档记 CI 运行编号 | TD-00013 / [#84](https://github.com/ACANX/AHA/issues/84) |
| **T-11** | `TODO.md` F-15 | 决定「文档标题 / 表行编号重复」是否纳入 `bin/CheckDocs.py` | 结论 + 若纳入则含反例自检（临时造重号必须判红） | TD-00013 / #84 |
| **T-12** | `TODO.md` F-16 | 定位 Maven 4 `verify` 的 10 行 `[stderr]`（`sqlite-jdbc` 的 native-access 警告）并给出消除或接受结论 | 结论写进 `BuildSpec.md` §8；若消除，`Gate` 日志该段消失 | TD-00013 / #84 |
| **T-13** | `TODO.md` F-07（剩余部分） | `Script/` 与 `bin/` 是否合并只留一个脚本目录（`.ps1` 已纳入检查，只剩目录归并） | 结论 + 若合并则引用全仓同步、`CheckScripts` 仍绿 | TD-00013 / #84 |

## 4. CLI 功能（与既有 TD 去重后的最小口径）

> 这五条**已有 TD 与 Issue**，此处只把「议题粒度」拆到可执行粒度；状态以各 TD / Issue 为准。

| 编号 | 来源 | 最小动作 | 验收 | 执行载体 |
|---|---|---|---|---|
| **T-14** | `Dbsx.txt` 第 1 项 | 工具调用输出：编辑类按「分块 + 前后差异」渲染，新增类分块展示路径与内容（> 500 行折叠） | 三平台快照或单测断言输出结构 | TD-00001 / [#72](https://github.com/ACANX/AHA/issues/72) |
| **T-15a** | `PLAN.md` §7.1 | 表格数据行之间是否补水平分割线：**先拍板**（全表 / 仅宽表 / 不加） | 结论写进 `PLAN.md` 或 `TUIDesign.md`；若加，`TableRenderer#render` 有对应断言 | TD-00002 / [#73](https://github.com/ACANX/AHA/issues/73) |
| **T-15b** | `PLAN.md` §7.3 | 图片 / 语法高亮 / OSC 8 超链接 / 单元格换行：逐项给「做 / 明确不做」结论 | 四项各一句结论 + 理由 | TD-00002 / #73 |
| **T-15c** | `Dbsx.txt` 第 2 项 | Markdown 富文本渲染缺口（与 T-15a/b 合并口径） | 同上 | TD-00002 / #73 |
| **T-16** | `Dbsx.txt` 第 3 项 | 输入框样式与 loading 动效 | 有可见产物 + 冒烟断言 | TD-00003 / [#74](https://github.com/ACANX/AHA/issues/74) |
| **T-17** | `Dbsx.txt` 第 4 项 | 状态栏信息（模型 / 目录 / token / 上下文占比 / 模式）—— 🚩**口径冲突待 ACANX 拍板**：`PLAN.md` §2 说「pi 式底部统计栏暂不实施」，而 #74 又列着「底部状态栏」 | 拍板后：做 ⇒ 落实现与断言；不做 ⇒ 在 #74 与 `PLAN.md` 之间统一口径 | TD-00003 / #74 |
| **T-18** | `Dbsx.txt` 第 5 项（剩余） | `/` 命令的**下拉补全**（命令本身已支持：`/help` `/config` `/context` `/compact` `/memory` `/paste`） | 输入 `/` 弹出候选、Tab/方向键可选 | TD-00003 / #74 |
| **T-19** | `Dbsx.txt` 第 6 项 | Windows conhost 多行粘贴：真机验证 PR #254 的侧门（`Alt+V` 剪贴板插入 / `/paste`） | 真机三条判据见 #75 的验收评论 | TD-00004 / [#75](https://github.com/ACANX/AHA/issues/75) |

## 5. 0.2 桌面端剩余项（原 `D-13` 一行的拆解）

| 编号 | 来源 | 最小动作 | 执行载体 |
|---|---|---|---|
| **T-20** | `TODO.md` D-13 | 记忆面板（0.2） | TD-00005 / #76 |
| **T-21** | 同上 | 扩展面板（0.2） | TD-00005 / #76 |
| **T-22** | 同上 | 身份加载顺序展示 | TD-00005 / #76 |
| **T-23** | 同上 | 会话内搜索 | TD-00005 / #76 |
| **T-24** | 同上 | 桌面覆盖率门禁上线（0.30 → 0.70 的两步走，见 `TestingSpec.md` §3.4） | TD-00007 / [#78](https://github.com/ACANX/AHA/issues/78) |

## 6. 必须真机 / 人工确认的项

| 编号 | 来源 | 最小动作 | 执行载体 |
|---|---|---|---|
| **T-25** | `PLAN.md` §5 | 逐项真机过一遍：界面整体观感、conhost 下的状态行、`Aha.bat` 启动、滚动区对原生复制的影响、`/memory edit` 接管终端、真实供应商 API Key 端到端、窗口 resize 后重排 —— **每项给「通过 / 不通过 + 现象」** | [#122](https://github.com/ACANX/AHA/issues/122)（真机验证总单） |

## 7. 归档动作（本单自身）

| 编号 | 来源 | 最小动作 | 验收 |
|---|---|---|---|
| **T-26** | `TODO.md` A-08 | 三份冻结文件是否整体移入 `Archive/`（决策已定「维持现状」，此项只是**可选收尾**） | 结论 + 若移动则按 `DocumentationSpec` 的 Archive 规则加「已废弃」头与替代物 | 本单（TD-00023） |

---

## 验收标准（针对本索引）

- [x] 三份文件里**剩余**的每一条都能在本文档找到对应的 `T-xx`（可反查：`grep` 原条目编号）；
- [x] 每条都有**最小动作 + 验收 + 执行载体**，没有「再去研究一下」这种空条目；
- [ ] 执行载体里的状态与本文档不冲突（状态以载体为准，本文档不重复维护进度）；
- [ ] 待 ACANX 拍板的两项（T-17 状态栏口径、T-26 是否整体归档）有明确结论。

## 与其他跟踪载体的关系（避免双重维护）

| 载体 | 职责 |
|---|---|
| 本文档（TD-00023） | 三份冻结文档遗留任务的**唯一索引**与最小任务拆解 |
| `TD-00001`～`TD-00022` | 各主题的**执行**与状态（含验收标准） |
| GitHub Issue（#72~#85 / #86 总览） | 长期跟踪与对外可见的状态 |
| `Docs/DevLog/20261017-15.md` | 已归档的「已完成 / 明确不做 / 已过时」条目（历史，不再跟踪） |

## 关联

- 归档记录：[`Docs/DevLog/20261017-15.md`](../DevLog/20261017-15.md)
- 来源文件：`Docs/TODO.md`（冻结）、`Docs/PLAN.md`（冻结）、`Docs/Dbsx.txt`（冻结）
- 规范：`DocumentationSpec.md` §1（TD-PPPPP-大驼峰英文标题）、`IssueTrackingSpec.md`
