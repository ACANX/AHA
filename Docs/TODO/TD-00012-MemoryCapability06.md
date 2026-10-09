# TD-00012 MemoryCapability06

> 待办编号：TD-00012
> 标题：记忆能力（0.6）：upsert、记忆工具与 /memory、写入策略接入、curate、载体选型
> 状态：☐ 未开始
> 跟踪 Issue：[83](https://github.com/ACANX/AHA/issues/83)
> 创建日期：2026-10-09
> 最后更新：2026-10-09

---

## 来源

- `Docs/TODO.md` 第 6 节「记忆能力（0.6 起）」
- `Docs/PLAN.md` §2（用量落库 → 底部统计栏）、§3（跨环境项目 ID 共享）、§4（记忆载体选型）

## 现状

`memory` 表已建、`storeMemory` / `recall` 接口已定义，但**全仓无调用方**——模型既不写也不读。项目级记忆位置（`~/.aha/Project/<项目ID>/Memory/`）与项目 ID 规则已实现。

## 范围

### 1. 推进顺序（与载体选型无关，可先做）

| 序 | 条目 | 状态 |
| --- | --- | --- |
| 1 | `storeMemory` 加 upsert（现为纯 `INSERT`，同一 key 写两次产生重复行） | ☐ |
| 3 | 记忆工具（模型侧）+ `/memory` 命令（用户侧）+ 候选区 | ☐（**建议从这里开始**） |
| 4 | `Memory.ModelWrite` 接入配置与权限（`off` / `candidate` / `direct`，受 `Tools.Enabled` 与 `PermissionPolicy` 双重管辖） | ☐ |
| 5 | 手动 `/memory curate`（去重合并、升降级、冲突检测，**必须可回滚**） | ☐ |
| 6 | 载体与向量（RAG）：表 / MD / 混合 | ⏸ 待决策 |

### 2. 用量落库（0.6，`PLAN.md` §2 的前置条件）

`UsageEvent` 目前只在流式过程中发出、**从不持久化**，SQLite 无 usage 表。上下行 token、缓存命中、上下文占比都要先落库才能做底部统计栏（见「CLI 输入与状态栏」Issue）。

### 3. 跨环境共享（`PLAN.md` §3，暂不实施）

Windows 与 WSL 的同一项目得到两个项目 ID，**默认视为两个独立项目**（刻意保留，不做隐式归一化）。需补**显式配置**共享机制（显式项目 ID / 别名表 / 共享目录），与载体选型一同推进。

### 4. 其它待定

用户级与项目级记忆的合并去重；候选区载体（`Candidate/` 子目录 vs 同一索引的 `status` 字段）；容量与淘汰策略；会话历史查看与统计（`aha session list`、`/stats`、`aha stat`）。

## 验收标准

- [ ] `storeMemory` 支持 upsert，重复写同一 key 不产生重复行；
- [ ] 模型能通过工具写入记忆、用户能通过 `/memory` 查看与操作、有候选区；
- [ ] `Memory.ModelWrite` 三档生效且受权限体系管辖；
- [ ] `/memory curate` 可执行且可回滚；
- [ ] 用量落库完成（token / 缓存 / 上下文）后，底部统计栏数据源可用；
- [ ] 载体选型拍板并写进 `MemoryStorageDesign.md`。

## 关联

- `Docs/Design/MemoryStorageDesign.md`、`Docs/AHA/AHA-Design-V1.md` 第十五部分

