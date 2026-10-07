# 记忆存储设计

**文档版本**：v1.7.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-06
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.1.0 | 2026-10-06 | `message` 表新增 `tool_calls` / `tool_call_id`，支持多轮工具调用 | @ACANX |
| v1.2.0 | 2026-10-07 | 新增 `replaceHistory` 与 `updateSessionConfig`；修正 `loadHistory` 取最旧 N 条（应为最近 N 条） | @ACANX |
| v1.3.0 | 2026-10-07 | 新增第 6 节「记忆的写入、查看与编辑」：现状缺口、命令形态、编辑器载体选型（待定） | @ACANX |
| v1.4.0 | 2026-10-07 | 新增第 7 节「载体选型（未定）」：SQLite 与 MD 文件方案的实测数据、分层方案、记录与整理机制、待定项与判据 | @ACANX |
| v1.5.0 | 2026-10-07 | 新增第 8 节「项目级记忆」：`~/.aha/Project/<项目ID>/Memory/` 位置与项目 ID 规则、三级写入策略、`Memory.ModelWrite` 开关、先记录后利用原则 | @ACANX |
| v1.6.0 | 2026-10-07 | 新增 8.6「跨环境共享」：默认两个独立项目，待补显式配置共享机制（项目 ID 覆盖 / 别名表） | @ACANX |
| v1.7.0 | 2026-10-07 | 指向设计文档的链接改到新位置 `../AHA/AHA-Design-V1.md` | @ACANX |

---

## 1. 存储

- 引擎：SQLite（`org.xerial:sqlite-jdbc`）
- 数据库文件：`Aha.db`
- WAL 模式：`PRAGMA journal_mode=WAL`

## 2. 命名约定

YAML 配置字段使用 PascalCase；**SQLite 表名（单数）与字段名使用 snake_case**，时间字段沿用 `gmt_create`。

## 3. 表结构

```sql
CREATE TABLE IF NOT EXISTS session (
    id          TEXT PRIMARY KEY,
    gmt_create  INTEGER NOT NULL,
    config_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS message (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id   TEXT NOT NULL REFERENCES session(id),
    role         TEXT NOT NULL,
    content      TEXT NOT NULL,
    tool_calls   TEXT,
    tool_call_id TEXT,
    gmt_create   INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS memory (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  TEXT NOT NULL REFERENCES session(id),
    key         TEXT NOT NULL,
    value       TEXT NOT NULL,
    embedding   BLOB,
    gmt_create  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_message_session ON message(session_id, gmt_create);
CREATE INDEX IF NOT EXISTS idx_memory_session ON memory(session_id, key);
```

## 4. 接口

`MemoryStore` 定义 `init`、`appendMessage`、`loadHistory`、`clearHistory`、`replaceHistory`、
`storeMemory`、`recall`、`close`；实现为 `SqliteMemoryStore`。

0.1 新增：

- `void createSession(String sessionId, String configJson)`，由 `SessionManager` 调用
- `void updateSessionConfig(String sessionId, String configJson)`，默认复用 `createSession`；
  `SqliteMemoryStore` 覆写为 `UPDATE`，避免连带重写会话创建时间
- `int replaceHistory(String sessionId, int keepRecent, ChatMessage summary)`，供 `/compact` 使用

## 5. 实现细节（0.1.0）

| 项 | 实现 |
|---|---|
| 路径 | `Memory.Path`，默认 `$AHA_HOME/Data/Aha.db` |
| 目录创建 | `init()` 先 `Files.createDirectories(parent)` |
| 日志模式 | WAL（`PRAGMA journal_mode=WAL`） |
| 命名 | 表名（单数）/字段 `snake_case`；时间字段 `gmt_create`（epoch millis） |
| 历史加载 | `loadHistory` 先按时间**倒序**取最近 `limit` 条，再翻回正序 |
| 工具调用 | `tool_calls` 序列化为 JSON 存入同名列，`tool_call_id` 单独成列 |
| 检索 | `recall` 对 `key`/`value` 做 `LIKE` 匹配，按时间降序 |
| 关闭 | 实现 `AutoCloseable`，`LocalAgentService.shutdown()` 统一释放 |

### 5.1 持久化验证

`EndToEndTest` 覆盖：写入 4 条消息（user / assistant+tool_calls / tool / assistant）
→ `close()` → 重新打开同一文件 → 仍然读到 4 条；多会话数据相互隔离。

### 5.2 tool_calls 与 tool_call_id 必须持久化

多轮对话中，每轮请求都要把完整历史回传给 LLM（API 无状态）。若这两个字段不落库，
从历史恢复后：

```json
{"role": "assistant", "content": ""}          // tool_calls 丢失
{"role": "tool", "content": "[...]"}         // tool_call_id 丢失
```

这违反 OpenAI 协议（`tool` 消息必须带 `tool_call_id`，且必须紧跟带 `tool_calls` 的 assistant 消息），
**服务端返回 422**。因此：

- `message` 表必须有 `tool_calls`（JSON）与 `tool_call_id` 两列
- 旧库通过 `ALTER TABLE ... ADD COLUMN` 迁移（`CREATE TABLE IF NOT EXISTS` 对已存在的表不生效）
- `loadHistory` 丢弃**无法完整重建**的消息（无 `tool_call_id` 的 `tool`、无 `tool_calls` 且无正文的
  `assistant`），避免发送非法请求体——这会让攻击面最小的降级方式，而不是整会话不可用

> `reasoningContent` **不**持久化，也**不**回传：DeepSeek 等推理模型明确不接受在后续请求中
> 传入 `reasoning_content`。

### 5.3 上下文压缩的存储支持

`/compact` 需要「删一批、写一条」，而 `clearHistory` 只能整段清空，因此新增
`replaceHistory(sessionId, keepRecent, summary)`：保留最近 `keepRecent` 条，其余由一条摘要替代。

- 默认实现为「读出 → 清空 → 重写」，写入顺序为「摘要在前、保留的在后」，
  因此摘要天然位于保留消息之前，不靠时间戳技巧
- 代价：**无法被 `loadHistory` 重建的不完整消息会被一并丢弃**。它们本就未进入模型上下文，
  压缩时顺手清掉是合理的
- `SqliteMemoryStore` 未覆写：压缩是低频操作，整段重写换来的简洁性比省几次 INSERT 更值

### 5.4 `loadHistory` 必须取最近的 N 条

修复前的 SQL 是 `ORDER BY gmt_create ASC LIMIT ?`——取的是**最早**的 N 条。会话一旦超过
`Memory.MaxContextEntries`，模型就永远只能看到最早的 100 条，新发生的对话再也进不了上下文。
这既让长会话不可用，也让「压缩上下文」失去意义。

修正后先按 `gmt_create DESC, id DESC` 取最近 N 条，外层再翻成正序返回，
因此返回值仍是「时间正序」，调用方（`AgentEngine.prepare`）无需改动。

## 6. 待办：记忆的写入、查看与编辑（1.0 前必补）

0.1 只交付了**存储层**：表建好了、接口定义了、单测通过了，但 `storeMemory` 与 `recall`
**没有任何调用方**——模型既不写也不读。「有表无功能」不算支持，因此下面三项已列入
[AHA-Design-V1.md](../AHA/AHA-Design-V1.md) 第十五部分 1.1 的必补清单（目标版本 0.6）。

### 6.1 模型侧：记忆工具

把 `storeMemory` / `recall` 接成可被模型调用的工具（如 `memory-write` / `memory-recall`），
纳入 `ToolRegistry` 与 `PermissionPolicy` 管辖。这是自举验收 A1 的直接要求：
「记忆写入与检索均可用且权限受控」。

### 6.2 用户侧：记忆命令

| 命令 | 作用 |
|---|---|
| `/memory` | 列出本会话记忆（key / value 摘要 / 时间） |
| `/memory get <key>` | 查看单条 |
| `/memory set <key> <value>` | 写入 |
| `/memory delete <key>` | 删除 |
| `/memory edit [key]` | 唤起外部编辑器 |
| `aha memory` | 进程级：跨会话列表 / 查看 / 写入 / 删除 / 检索 |

记忆本身是跨会话的，只在会话内可见并不合理，因此两者都要有。

### 6.3 存储载体（未定）

记忆采用「可编辑的文档」还是「表」，关系到编辑器如何介入：
**不能让编辑器直接打开 SQLite 文件**（二进制，改一下可能损坏整个库）。

- 载体为**文档**时，编辑器改的就是真源，天然成立
- 载体为**表**时，只能「导出 → 编辑 → 回写」，需要一套行格式约定，
  且解析失败时必须**放弃回写**而不是部分写入

选型未定，完整分析、实测数据与判据见第 7 节。

唤起方式已落地（见 `Docs/Design/CLIDesign.md` 第 7 节）：`-Daha.editor` → `$VISUAL`
→ `$EDITOR` → 平台默认，只选阻塞式命令；难点不在唤起而在**让出终端**
（JLine `pause()` / `resume()`）。

### 6.4 检索质量

`recall` 目前是对 `key` / `value` 做 `LIKE` 匹配，按 0.6 计划升级为向量检索 + RAG。
`memory` 表已预留 `embedding BLOB` 列（见第 3 节），无需再改表结构。

## 7. 载体选型（未定）

**方案尚未定下来**，本节记录的是实测依据、候选方案、判据与待定项，供后续决策使用。
已定的是**原则**：记忆必须能「记录」与「整理」，两者都要做。

### 7.1 先纠正一个前提：瓶颈不在文本，在向量

用 `sqlite-jdbc 3.53.2.0` 量了一组数据（WSL 的 `/tmp`，Linux 文件系统、page cache 热；
热缓存下数字偏乐观，但**量级关系不随介质改变**）：

| 场景 | 规模 | 实测 |
|---|---|---|
| 批量插入（事务内） | 200,000 行 | 515 ms |
| 按 `(session_id, key)` 精确查 | 2,000,000 行 / 291 MB | **0 ~ 1 ms** |
| `LIKE '%关键字%'` 全表扫（现状 `recall`） | 200,000 行 | **4 ms** |
| `LIKE '%关键字%'` 全表扫 | 2,000,000 行 | **41 ms** |
| FTS5 全文索引 | — | **sqlite-jdbc 中可用** |
| **向量暴力相似度扫描** | **50,000 条 × 384 维 / 98 MB** | **100 ms，每次查询都要重来** |

换算（按实测 201 字节/行文本、384 维 = 1536 字节/条向量）：

| 规模 | 文本记忆 | 384 维向量 | 暴力扫向量 |
|---|---|---|---|
| 10,000 条 | 1.9 MB | 14.6 MB | ~20 ms |
| 100,000 条 | 19.2 MB | 146 MB | ~200 ms |
| 1,000,000 条 | 192 MB | 1.4 GB | ~2 s |

**结论**：200 万行文本的全表扫只要 41 ms，SQLite 在读文本上根本不是瓶颈；
真正会疼的是**向量**——50k 条已达 100 ms/次，且随条数线性增长，50 万条就是 1 秒级、
1.4 GB 库文件。所以「量大了怎么办」的答案不是换掉 SQLite，
而是**把向量从主表里拿出来单独设计**。

### 7.2 MD 方案的代价：不是读慢，是小文件管理

同样量了一组：

| 场景 | 规模 | 实测 |
|---|---|---|
| 建 5,000 个小 MD 文件 | — | 97 ms（0.02 ms/个） |
| 全量读取扫描找关键字 | 5,000 个文件 | 49 ms |
| 逻辑大小 / **实际占用** | 543 KB / **20 MB** | **块浪费 37 倍** |
| inode | 5,000 个 | — |

两点结论：

- **读取速度不是问题**（5,000 个文件全扫 49 ms），无索引时的检索速度可以接受
- **真正的问题是文件系统开销**：543 KB 内容占 20 MB，37 倍块浪费；再叠上 inode 压力与
  git index 膨胀。因此 **不要一条记忆一个文件**，应按主题 / 项目 / 时间**分片**

MD 的独有收益：

- 可读、可手改——编辑器改的就是真源，不需要「导出→回写」（见 6.3）
- **git diff 友好**：SQLite 库文件每次改动都是整文件二进制变更，无法 diff、无法 review；
  文本记忆天然可 diff、可追溯
- 跨工具通用：其他 Agent Harness 可直接读取，利于迁移

### 7.3 候选方案

| 方案 | 真源 | 检索 | 一致性 | 适用形态 |
|---|---|---|---|---|
| A. 表（0.1 现状） | 表 | 索引 + FTS5 + 向量 | 事务保证 | 机器读写为主、高基数 |
| B. 纯 MD | 文件 | 需自建索引 | 无事务 | 人读写为主、量小 |
| C. 混合 | **MD** | **表（派生索引）** | **索引可重建，无需双写一致性** | 人可读 + 可检索 |

**C 能否成立，取决于一个前提**：表是**派生索引**而不是并列的真源。

只要保证「索引可以从文件完全重建」，就**不存在双写一致性问题**——不需要分布式事务、
不需要两阶段提交，索引丢了（甚至删库）重建即可。反过来，如果表里存了文件里没有的东西，
表就成了第二个真源，一致性问题立刻出现。这是 C 方案的分水岭。

派生索引需要记录的内容：文件路径、定位（标题 / 锚点 / 行号）、`key`、标签、内容哈希、
`mtime`、`embedding`、状态（候选 / 正式 / 归档）、引用计数。

### 7.4 分层：文档层与事件层

方案可以未定，但有一件事现在就该分清——记忆天然是两类东西：

| 层 | 内容 | 量级 | 读写者 | 适合的载体 |
|---|---|---|---|---|
| **文档层** | 事实与约定：「项目用 Maven 4」「用户要求中文回复」「这个坑踩过」 | 几十 ~ 几百条 | 人 | MD |
| **事件层** | 会话摘要、每次结论、工具调用收获 | 数千 ~ 数十万 | 机器 | 表（带索引与向量） |

把两类塞进同一个载体，无论选哪个都会别扭：文档层被机器写入淹没，事件层无法手改。
「**整理**」的职责就是**两层之间的搬运**——把事件层里反复命中的东西提炼进文档层。

### 7.5 记录与整理（原则已定，必须实现）

**记录**：

- 时机：会话结束 / `/memory save` 显式触发 / 模型调用记忆工具
- 方式：模型提炼 → 先落**候选区**，不直接进正式记忆（避免把模型幻觉写进长期记忆）
- 可复用 `/compact` 的骨架（`ContextCompactor`）：LLM 调用 + **失败绝不动数据**

**整理**：

- 去重合并：同一事实的多种表述 → 向量相似度粗筛 + 模型裁决
- 升降级：反复命中 → 升为核心；长期未命中 → 降级 / 归档
- 冲突检测：「用 Gradle」与「用 Maven」并存 → 保留新的、标注旧的失效
- **必须可回滚**：每次整理产出 diff 供 review（文档层天然可 diff）
- 触发：`/memory curate` 手动 / 会话数阈值 / `aha stat` 提示

### 7.6 待定项与判据

| 待定 | 选项 | 判据 |
|---|---|---|
| 真源 | 表 / MD / 混合 | MD 的收益（git 可版本化、可手改、跨工具）是否值得付出索引重建的成本 |
| 作用域 | 用户级 / 项目级 / 跨会话 | 项目特定知识宜随仓库提交（团队共享）；通用偏好留用户级。**注意现状是按 `session_id` 过滤的，与「长期」定位矛盾**（见第 6 节） |
| 分片粒度 | 一条一文 / 按主题 / 按月 / 按项目 | 小文件块浪费 37 倍（7.2 已实测） |
| 向量存放 | 表 BLOB / 独立索引文件 / sqlite-vec | 50k 条已 100 ms/次，须上 ANN 或分层粗筛 |
| 容量与淘汰 | 条数上限 / TTL / 引用计数 | 记忆无限增长是必然，`Memory.*` 目前没有对应配置 |
| 写入者 | 仅用户 / 用户 + 模型 / 自动提炼 | 决定是否需要候选区与审核流 |

### 7.7 不受选型影响的推进顺序

下面几步无论最终选哪个载体都要做，可以先推进：

1. 补表结构缺口：`storeMemory` 加 upsert（现状纯 `INSERT`，同 key 会写重复行）
2. 作用域参数化：把「按会话 / 按项目 / 全局」做成配置项，而不是写死 `session_id`
3. 做**记录**：记忆工具 + `/memory` 命令 + 候选区
4. 做**整理**：先跑通手动的 `/memory curate`，自动触发后置
5. 向量与载体选型一起放到 0.6——那时才有真实语料判断分片策略

## 8. 项目级记忆：位置、项目 ID 与写入策略

本节记录的是**已确定的决策**。存储载体仍未定（见第 7 节），但下面这些与载体无关。

### 8.1 位置：按项目，不按会话

```
~/.aha/Project/<项目ID>/Memory/
```

现状按 `session_id` 过滤（第 6 节），与「长期」的定位矛盾：换个会话就看不到了。
改为按项目存放后，知识跟着代码走，且跨会话可见。

项目 ID 由项目根的绝对路径推导：

| 规则 | 说明 |
|---|---|
| 分隔符替换 | `/`、`\`、`:` 一律替换为 `-` |
| 盘符大写 | Windows 的 `C:` / `E:` 转为大写 |
| 规范化 | 先 `toAbsolutePath().normalize()`，尾部分隔符与 `./`、`../` 被消除 |

实测（格式由 `ProjectIdTest` 钉死）：

| 路径 | 项目 ID |
|---|---|
| `/home/me/proj` | `-home-me-proj` |
| `/Users/alice/my-project` | `-Users-alice-my-project` |
| `E:\GitRepo\GitHub\ACANX\AHA` | `E--GitRepo-GitHub-ACANX-AHA` |
| `/mnt/e/GitRepo/GitHub/ACANX/AHA` | `-mnt-e-GitRepo-GitHub-ACANX-AHA` |

**与 Claude Code 对齐**：`~/.claude/projects/<项目ID>/` 用的是同一套规则，便于两边并存与迁移。
因此不额外做路径归一化（见 8.5 的待定项）。

项目根沿用 `SystemPromptLoader.findProjectRoot`
（`.git` / `.aha` / `Aha.yaml` / `AHA.md` / `AGENTS.md`），找不到时退回当前工作目录。

> 项目 ID 一旦定下就不能随意变动，否则历史记忆会「找不到」——这是 `ProjectIdTest`
> 把格式钉死的原因。

### 8.2 写入策略：三级

| 级别 | 谁写 | 是否确认 | 落点 |
|---|---|---|---|
| **项目记忆** | 模型可**直接写** | 默认不确认 | 项目 `Memory/` 正式区 |
| **候选区** | 模型可写，整理流程也可产生 | 按配置 | 候选条目（`status = candidate`） |
| **长期记忆** | **只能由整理流程从记录中筛选提取生成** | 产出后 review | 正式区，带来源引用 |

关键约束：**长期记忆不允许直接写**。它必须是「筛选提取」的产物——否则等于把模型的即时输出
直接升格为长期结论。行业对记忆的共识尚未形成，这样做的风险是污染而非收益。

### 8.3 配置开关

```yaml
Memory:
  ModelWrite: direct     # off | candidate | direct
```

| 取值 | 行为 |
|---|---|
| `off` | 不向模型提供记忆写入工具 |
| `candidate` | 写入候选区，待整理流程筛选 |
| `direct` | 项目级直接写正式区（当前默认） |

记忆写入本身是一个**工具**，因此同时受 `Tools.Enabled` 与 `PermissionPolicy` 管辖——
`ModelWrite` 决定的是「该工具是否自动放行」，而不是绕过权限体系。

### 8.4 指导原则：先记录存档，后利用

行业尚无共识，因此顺序上先做「记录」、后做「利用」：

1. **先记录存档**：全量、低门槛地记下来（含候选区），保证可读、可整理、可回溯
2. **后利用**：召回与注入 prompt 的策略，留到有真实语料之后再定

理由很直接：**未经检验的召回比没有记忆更糟**。把不相关或已过期的记忆注入上下文，
会持续污染推理，而用户很难察觉根因在记忆。先积累、再观察命中质量，最后才谈自动注入。

### 8.5 待定项

| 待定 | 说明 |
|---|---|
| 路径大小写 | Windows 上 `E:\GitRepo` 与 `e:\gitrepo` 会得到不同 ID。不强制统一是为了与 Claude Code 保持一致 |
| 用户级与项目级合并 | 通用偏好（「回复用中文」）宜放用户级；整理流程需要处理两层的合并与去重 |
| 候选区的载体 | 与第 7 节的载体选型一起定：同目录下的 `Candidate/` 子目录，还是同一索引的 `status` 字段 |
| 容量与淘汰 | 记录期的增长速度与归档策略 |

### 8.6 跨环境共享（后续工具优化方向）

**默认行为**：Windows 与 WSL 的文件路径不同，同一个目录在两个环境中会推导出两个不同的项目 ID：

```
E:\GitRepo\GitHub\ACANX\AHA        → E--GitRepo-GitHub-ACANX-AHA
/mnt/e/GitRepo/GitHub/ACANX/AHA    → -mnt-e-GitRepo-GitHub-ACANX-AHA
```

因此**默认视为两个独立项目**（各自独立的记忆与会话历史）。这是**刻意保留的默认**：
不隐式归一化，因为 `/mnt/*` 并不总等价于 Windows 盘——它也可能是 WSL 内部的普通目录。

**待补机制**：当两个环境的差异不影响工作时，应允许**显式配置**把两个项目的记忆与会话历史
共享或复用。候选做法：

| 方案 | 做法 | 代价 |
|---|---|---|
| A. 显式项目 ID | 配置中直接指定项目 ID（覆盖路径推导），两个环境配同一个 | 最小可用；但项目配置会随环境不同而不同 |
| B. 别名表 | 用户级 `Project/Aliases.yml` 把多个自动 ID 映射到一个共享 ID | 不动项目文件；多一层间接 |
| C. 共享目录 | 让两个环境的 `Project/<id>/` 指向同一目录 | Windows 与 WSL 的链接语义不同，脆弱 |

倾向 A（简单、可审计、写在项目里可随仓库共享），B 作为补充。

**附加待定**：会话历史目前是**用户级单库**（`AHA_HOME/Data/Aha.db`），与「记忆按项目」不一致。
「共享会话历史」这件事要先答复一个问题：会话历史是否也改成项目级？
（两环境的 `AHA_HOME` 不同时，即便项目 ID 统一，会话历史仍各存各的。）
