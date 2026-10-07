# AHA 暂缓与受限事项

**文档版本**：v1.24.0
**状态**：草稿
**生效日期**：2026-10-07
**最后更新**：2026-10-07
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-07 | 初始版本：记录「状态行置于输入行上方」的两次尝试与否决依据、pi 式底部统计栏、跨环境项目共享、待真机验证项 | @ACANX |
| v1.1.0 | 2026-10-07 | 新增第 6 节「仍未做的关键事项」（首次提交、Maven 基线复验、0.1 发布流程、文档数据刷新） | @ACANX |
| v1.2.0 | 2026-10-07 | 新增第 7 节「TUI 渲染待补项」（表格数据行分隔线待支持）；§4 的「`Docs/TODO.md` 重复内容清理」已落实并标记完成，新增「`TODO.md` 复核结论未回灌」 | @ACANX |
| v1.3.0 | 2026-10-07 | 新增 §7.2「身份片段是否需要标明来源」 | @ACANX |
| v1.4.0 | 2026-10-07 | §7.2 标记已解决（改为片段独立，边界即来源信息） | @ACANX |
| v1.5.0 | 2026-10-07 | 新增 §8「0.1.0 发布前问题清单」（阻塞项 / 必须解决 / 建议 / 已确认无问题），含逐项状态 | @ACANX |
| v1.6.0 | 2026-10-07 | 新增 §8.5：修复「日志文件长期不生成」并记录根因、回归测试与真机验证 | @ACANX |
| v1.7.0 | 2026-10-07 | 新增 §8.6：日志命名与切分规则定稿（`AHA.log` / 10 MB / `AHA-yyyy-MM-dd-NN.log` / 序号递增）与取舍 | @ACANX |
| v1.8.0 | 2026-10-07 | §8.6：内置 `DEBUG` 级别由「待办」改为「开发阶段的有意设计」，并区分内置默认值与兜底值 | @ACANX |
| v1.9.0 | 2026-10-07 | 新增 §8.7「0.1.0 发布前复核」：19 项实查结果；§8.1.2/8.2.1 用例数更新为 578；§8.1.3 澄清 Maven 4 尚无 GA、本机已有 3.9.11；§8.2.6 改标为部分完成；§8.3 追加 8.3.7（目录树漂移已修）与 8.3.8（新增日志设计文档） | @ACANX |
| v1.10.0 | 2026-10-07 | §8.2 新增 8.2.7：`SystemPromptLoaderTest` 不具环境无关性（用户跑过一次 `aha chat` 后 7 个用例必红，`verify` 变红） | @ACANX |
| v1.11.0 | 2026-10-07 | §6.4 改为「已刷新」并记录实测数据；§8.1.2 与 §8.2.1 标记完成；§8.7 用例数更新为 604 | @ACANX |
| v1.12.0 | 2026-10-07 | §6.2 改为「Maven 3.9.x 已复验」（3.9.11 与 Maven 4 结果一致）；§8.1.3 标记完成；新增 §8.1.4 记录 `dist` 打包因文件被占用而跳过 | @ACANX |
| v1.13.0 | 2026-10-07 | §8.1.4 标记完成：根因是 IDEA 的 Maven server 占用 `dist`，完整 `clean verify` 已通过 | @ACANX |
| v1.14.0 | 2026-10-07 | 设计文档路径更新到 `Docs/AHA/`；「未勾验收项」的行号引用改为小节引用（行号已失效） | @ACANX |
| v1.15.0 | 2026-10-07 | §6.1 改写为「首次提交已完成（本地）」：14 个提交 / 371 个文件的批次表与排除项核对；§8.1.1 改为「本地已完成、待推送」 | @ACANX |
| v1.16.0 | 2026-10-07 | §8.2.7 标记已修（并指出它正是 PR #6 Windows 失败的主因）；新增 §8.2.8 记录 Windows 腿的 15 个失败与四类修法，以及 CI 矩阵改 `fail-fast: false` | @ACANX |
| v1.17.0 | 2026-10-07 | 记录决策：CI 增加 `macos-latest` 可选腿（仅演示，`continue-on-error` 不参与必需检查），规范落在 `BuildSpec.md` §4.1 | @ACANX |
| v1.18.0 | 2026-10-07 | 记录决策：慢检查（verify / 覆盖率 / 文档 / 重复率）从每次 push 的卡点中移出，集中到 `Gate.yml`（合入 main 前与发布前）；新增 PMD CPD + `bin/CheckDuplication.py` 重复率检查（阈值 2.0%，实测 0.40%） | @ACANX |
| v1.19.0 | 2026-10-07 | 记录决策：`Gate.yml` 增加每周定期扫描；新增 `Compat.yml`（Maven 3.9.x 兼容验证，固定补丁版本）；门禁显式断言 wrapper 固定的 Maven 版本；发布前置改为 gate + compat | @ACANX |
| v1.20.0 | 2026-10-07 | §8 落地去向表的「`TODO.md` 复核结论未回灌」改为 ◐ 进行中并刷新实况（B-03 已收口；新增 B-04~B-07、F-01~F-06 均已落地） | @ACANX |
| v1.21.0 | 2026-10-07 | 新增 §8.2.9（分支保护未配置）与 §8.2.10（定期扫描未验证）两条发布前阻塞项，均交叉引用 `TODO.md` 第 11 节的 `G-xx`；§8.1.1 改为指向 `G-01` | @ACANX |
| v1.22.0 | 2026-10-07 | §8.1.1 按实况更新：此前那批提交已推送（PR #6 已合入 dev），现仅剩 PR #8 冲突修复的合并提交 06c6121 待推送；交叉引用 TODO.md 的 G-01 / F-12 | @ACANX |
| v1.23.0 | 2026-10-07 | §8.7 补「当日实查快照、不随后续变动逐行回改」的标注，并指明现值出处（§6 / §8.1 / TODO.md） | @ACANX |
| v1.24.0 | 2026-10-07 | §8 编号与顺序修正（`A-09` / `A-10`）：§8.2 表补齐并归位 `8.2.6`、原重复的 `8.2.7`（日志文件不生成，与 §8.5 重复记录）改为 `8.2.11`；`### 8.4` 由 §8.7 之后挪回 `8.3` 之后；重复的 `## 8.` 标题（「相关文档」）改为文末 `## 9.` 并移到位 | @ACANX |

---

## 0. 本文件的定位

记录三类事项，并写清完整上下文：

1. **做不到**：受平台 / 依赖 / API 限制，当前实现不了；
2. **已决定暂缓**：能做但时机未到；
3. **仍未做且有阻塞**：该做、也知道怎么做，但被前置条件卡住。

上下文要写全：期望是什么、试过什么、证据是什么、为什么受阻、将来在什么前提下可以再做、
有哪些备选路径与代价。

与其它文档的分工：

| 文档 | 记什么 |
|---|---|
| `Docs/TODO.md` | 待办与调整项（想法池，不保证已确认） |
| `Docs/PLAN.md`（本文件） | 上面三类事项，以及判断依据 |
| `Docs/Design/*` | 已定方案的详细设计 |
| `Docs/AHA/AHA-Design-V1.md` | 权威版本路线图与阶段计划 |

判断依据一律写明**实测数据或 API 事实**，不写“感觉不行”。

---

## 1. 状态行置于输入行上方（pi 形态）——做不到

### 1.1 期望形态

对标 pi-coding-agent：

```
──⠸ Working──────────────────────────────────────────────  ← 状态嵌在这条线里
 输入行
──────────────────────────────────────────────────────────
 ↑3.9M ↓2.3M R689M ⦿ 99.3% …            deepseek-flash • max        E:\…\AHA
```

即：状态信息与盲文旋转帧嵌在输入行**上方**的分隔线中。

### 1.2 现状（0.1 采用）

```
（正文区）
──────────────────────────────────────────────  ← 上分隔线（提示符）
（输入行）
───────────────────────────────────────────  ← 下分隔线（Status 边框）
⠋ 等待响应…                                     ← 状态行在最底部
```

状态行在输入行**下方**，只有转圈帧与阶段文案（耗时与规模下沉到工具区块末行）。

### 1.3 试过什么、结果如何

**第一次尝试**：把状态行作为多行提示符的第一行，用 `LineReaderImpl.setPrompt` +
`redisplay()` 刷新。

- 能显示 ✓，能实时刷新 ✓（探针里帧从 `⠋` 走到 `⠼`，秒数同步增长）
- 但**正文整段消失** ✗，且**没有任何报错** —— `PrintStream` 会吞掉异常，属于静默失败
- 隔离验证：关掉定时刷新后正文恢复正常 → 定位到「提示符重绘」与流式正文冲突

**第二次尝试**：加入不变量「**有正文在流时绝不重绘提示符**」——等待期刷新、正文一开始就停手、
工具结果写完再恢复。

- 仍然失败 ✗：两次实测分别只显示出 **6/12** 与 **3/12** 行正文
- 结论：问题不在刷新时机

### 1.4 根因（结构性，非实现缺陷）

`LineReader.printAbove()`（正文上抛）与**提示符重绘共享同一套显示状态**。提示符重绘若干次之后，
`printAbove` 计算出的位置就偏了，后续写入落在屏幕之外——而且因为 `PrintStream` 吞异常，全程无提示。

JLine 的行读取器必然把输入行放在**内容区底部**（它占滚动区域内最后一行）。
因此可控制的位置只有两个：

| 位置 | 能否原地重绘 | 说明 |
|---|---|---|
| 输入行**上方**（作为提示符的一部分） | ✗ | 一重绘就与 `printAbove` 冲突 |
| 输入行**下方**（`org.jline.utils.Status` 底部保留区） | ✅ | 实测转圈、秒数刷新与正文流式**可共存** |

pi 能做到是因为它**自己接管了整块底部渲染**（全屏 TUI），根本不使用行读取器库。

### 1.5 备选路径与代价

| 路径 | 效果 | 代价 |
|---|---|---|
| **A（0.1 已采用）** | 状态行在输入行下方，转圈 + 阶段**实时** | 位置与 pi 不同 |
| B | 状态行在输入行**上方** | 只能**静态**：不转圈、不更新（任何重绘都可能吃掉正文） |
| C | 完全复刻 pi：自研底部渲染 | 放弃 JLine `LineReader`，自己实现输入行、编辑、历史、补全、多行粘贴。工作量是前两者的量级差异，且要重走一遍目前已经验证过的输入路径 |

### 1.6 什么前提下可以再做 C

- 输入层需求收敛（确认不需要 JLine 的补全 / 历史 / 多行粘贴语义，或愿意自己实现一遍）
- 有真实终端矩阵可用于验证（至少 Windows Terminal / conhost / WSL / macOS）
- 接受把已通过验证的输入实现推倒重来

**在此之前不要重试 A/B 的“刷新式”方案**：两次实测都失败，失败模式还是静默的（正文消失而无报错），
排查成本极高。

---

## 2. pi 式底部统计栏 —— 暂不实施

### 2.1 期望

```
↑3.9M ↓2.3M  R689M  ⦿ 99.3% 67.1%/1.0M   [auto]   deepseek-flash • max   E:\GitRepo\GitHub\ACANX\AHA
```

### 2.2 两个前置条件都不具备

1. **渲染位置**：它位于输入行下方，可以借 `Status` 的底部保留区实现 ✓；但同一行还想放
   “当前状态 + 转圈”，就与 §1 的位置诉求纠缠。作为**独立一行**放在状态行之下是可行的。
2. **数据源不存在** ✗：上下行 token 数、缓存命中、上下文占用率这些都需要**把用量落库**。
   目前 `UsageEvent` 只在流式过程中发出（用于状态行一闪与 `[usage]` 块），**从不持久化**，
   SQLite 里也没有 usage 表。用量统计整体安排在 0.6（见 `Docs/AHA/AHA-Design-V1.md` 第十五部分 1.1）。

**结论**：等 0.6 的用量落库完成后再做，届时数据与渲染位置都具备。

---

## 3. 跨环境（Windows / WSL）项目 ID 共享 —— 暂不实施

### 3.1 现状

同一个项目在两个环境里得到两个不同的项目 ID：

```
E:\GitRepo\GitHub\ACANX\AHA        → E--GitRepo-GitHub-ACANX-AHA
/mnt/e/GitRepo/GitHub/ACANX/AHA    → -mnt-e-GitRepo-GitHub-ACANX-AHA
```

**默认视为两个独立项目**（各自独立的记忆与会话历史）。这是刻意保留的默认：`/mnt/*` 并不总等价于
Windows 盘符，隐式归一化会把「真的放在 WSL 内部 `/mnt/e` 普通目录」的项目错误合并。

### 3.2 待补机制

需要**显式配置**才能共享（方案与代价见
[MemoryStorageDesign.md](Design/MemoryStorageDesign.md) 8.6：项目 ID 覆盖 / 别名表 / 共享目录）。

**暂缓原因**：它不是 0.1 的问题，且与「记忆载体选型」一同推进更划算——载体未定之前，
共享机制的落点也会变。

---

## 4. 其它已记录但未定/未实施的项

| 项 | 位置 | 状态 |
|---|---|---|
| 记忆存储载体选型（表 / MD / 混合） | [MemoryStorageDesign.md](Design/MemoryStorageDesign.md) 第 7 节 | 未定，待 0.6 一并决定 |
| 记忆的记录与整理机制 | 同上 7.5 | 原则已定，实施待 0.6 |
| 会话历史是否也改为项目级 | 同上 8.6 | 未定（牵连会话列表与统计） |
| ~~`Docs/TODO.md` 的重复内容清理~~ | `Docs/TODO.md` 自身 | ✅ **已完成（2026-10-07）**：已删除重复副本与误粘贴的会话日志，`TODO.md` 升 v0.3.0 并逐条复核。**本行可从表中移除** |
| `TODO.md` 复核结论未回灌正式文档 | `Docs/TODO.md` §8、各目标文档 | ◐ 进行中：`B-03` 版本漂移已收口（`BuildSpec.md` §7 立「版本单一来源」规则）；本轮新增的 `B-04`~`B-07`（Windows 平台缺陷）与 `F-01`~`F-06`（CI 门禁与工程效能）均已同步落地到 `BuildSpec` / `TestingSpec` / `BuildGuide` / `AGENTS`；仍待回灌的是 `D-09` / `E-12` 等 0.2 及以后的条目 |
| `mvn clean` 无法删除被占用的文件 | `aha-cli/pom.xml` | 已缓解（`failOnError=false`）：被锁的必然是当前版本同名文件（assembly 覆盖），旧版本残留无人占用可正常删 |

---

## 5. 本环境无法自动验证、必须真机或人工确认的项

以下不是“做不到”，而是**此处（WSL）验证不到**，每次改动仍应人工过一遍：

| 项 | 为什么这里验不了 | 怎么验 |
|---|---|---|
| **0.1 界面整体观感**（分隔线、状态行、工具区块色带、区块间空行） | 伪终端能验“画出来了”，验不了“好不好看” | Windows 下 `dist\bin\Aha.bat chat`，让它读一个文件 |
| 状态行在 **conhost（旧 CMD）** 下的表现 | 只有 Windows 有 conhost；能力不足时应自动降级为不显示 | Windows 下 `dist\bin\Aha.bat chat`，看是否花屏；若无线则确认降级生效 |
| **`Aha.bat`** 启动路径 | WSL 只能通过 `cmd.exe` 间接调用 | Windows 下直接运行 |
| **滚动区域对原生滚动/复制的影响** | 这是选 A 时接受的代价，属于手感判断 | Windows Terminal / PowerShell 下滚动与复制 |
| **`/memory edit`** 的编辑器接管终端 | 需要真实交互式终端与编辑器 | Windows 下 `code -w` / `notepad` |
| **真实供应商 API Key** 端到端 | 需要外部凭据 | `aha chat` 连真实供应商跑一轮 |
| 窗口 resize 后的状态行重排 | 伪终端里 `TIOCSWINSZ` 行为与真机不同 | 拖动窗口大小 |

---

## 6. 仍未做的关键事项

这些不是“做不到”，而是**该做、也知道怎么做，但被前置条件卡住**。列在这里是为了不必反复口头提醒。

### 6.1 首次提交已完成（本地），推送待做

**已做（2026-10-07）**：0.1.0 基线按子系统拆成 **14 个提交**落在 `main` 上，
入库 **371 个文件**，工作区干净；提交信息全部符合 [CommitMessageSpec.md](DevSpec/CommitMessageSpec.md)
（`git log --format=%s` 校验不合规数为 0）。

| 批次 | 内容 | 文件数 |
|---|---|---|
| 1 | 仓库骨架与公共层 | 49 |
| 2 | 构建与发布工作流 | 3 |
| 3 | 扩展契约 | 19 |
| 4 | 推理核心、LLM 适配器、配置与存储 | 115 |
| 5 | 内置工具 | 12 |
| 6 | 交互式 CLI 与终端渲染 | 94 |
| 7 | 桌面端占位模块 | 3 |
| 8 | 启动脚本与自检脚本 | 8 |
| 9~13 | 开发规范 / 设计文档 / 用户指南 / 图资源 / 项目说明 | 51 |
| 14 | AGENT 技能 | 17 |

**提交前已核对**：`dist/`、`target/`、`Model.yml`、`Data/`、`Log/`、`*.db`、`.idea/`、`.xcodemap/`、
`pom.xml.upgraded` **均未入库**（逐项在索引层面验证过）。

**仍待做（需要远端凭据）**：

```bash
git push -u origin main
git switch -c dev && git push -u origin dev
git switch -c dependa && git push -u origin dependa
```

被它卡住的是：CI 从未触发、Dependabot 无法启用、无法打 tag，因此**正式发布仍不能执行**。

### 6.2 Maven 3.9.x 兼容基线已复验（2026-10-07）

**已做**：用本机 `Apache Maven 3.9.11` 跑通 `mvn clean verify`，与 wrapper（Maven 4.0.0-rc-7）
结果**完全一致**——同样 **604 用例**、同样 **合计行覆盖 80.4%**（4031/5016 行），门禁全模块通过。

两条工具链的实测结论：

| 工具链 | 命令 | 结果 |
|---|---|---|
| Maven 4（wrapper，固定 `4.0.0-rc-7`） | `./mvnw clean verify` | ✅ |
| Maven 3.9.x（本机 `3.9.11`，JDK 25） | `mvn clean verify` | ✅ |

**顺带澄清**：wrapper 固定 `4.0.0-rc-7` 并**不是**疏忽。实测 Maven 4 尚无 GA——
`apache-maven-4.0.0-bin.zip` 返回 404，官方 `maven-metadata.xml` 的 `release` 就是 `4.0.0-rc-7`。

### 6.3 0.1 发布流程未执行

按 [ReleaseProcess.md](DevSpec/ReleaseProcess.md)：开 `release/0.1.0` 分支 → 合入 `main` →
打 tag `v0.1.0` → 由 `Release.yml` 产出 `dist.zip`。

**依赖**：6.1（无提交就没有 tag 可打）。

**顺带**：`CHANGELOG.md` 里 `[0.1.0] - 2026-10-06` 的日期需改成实际发布日。

### 6.4 文档中的测试数据已刷新（2026-10-07）

**已做**：跑通完整 `./mvnw clean verify`（含覆盖率门禁），把三处口径统一为同一套实测数据：
**604 用例**、**合计行覆盖 80.4%**（4031/5016 行）。

| 位置 | 处理 |
|---|---|
| `AHA/AHA-Design-V1.md` §3.1 | 模块表 + 合计 + 用例数全部换成实测值，并写明统计口径 |
| `ArchitectureOverview.md` §6 | 同上 |
| `PLAN.md` §8.1.2 / §8.2.1 | 标记完成 |

**注意口径**：合计 = 各模块 `LINE_COVERED / (LINE_COVERED + LINE_MISSED)` 求和；
`aha-desktop` 为 0.1 占位模块，已从门禁排除，不计入合计。用例数以 Surefire 的
`Tests run` 为准（41 个测试类）。

---

## 7. TUI 渲染待补项 —— 后续支持

由使用反馈记录，尚未实施。

### 7.1 表格数据行之间的水平分割线

**现状**：表格只有三条横线——顶边、表头与表体之间、底边。数据行之间是空白的。

```
┌──────────────┬──────────┐
│ 字段         │ 说明     │
├──────────────┼──────────┤   ← 只有这一条内部分隔线
│ ModelWrite   │ 写入策略 │
│ KeepMessages │ 保留条数 │       ← 数据行之间没有
└──────────────┴──────────┘
```

**要求**：数据行之间也加水平分割线（`├──┼──┤`），形成完整网格。

**实现位置**：`TableRenderer#render`——在输出每一行之后判断是否需要补一条中间分隔线，
改动约 3 行（`if (i > 0) { out.append(分界线) }`）。

**待定**：Markdown 表格本身并没有行分隔线的语义，加上之后视觉更重（每行都被框住）。
若确认要加，需一并决定**是否所有表格都加**（还是只在列数多、行数多时才加）。

### 7.2 身份片段是否需要标明来源 —— 已解决

**结论（2026-10-07）**：改为**一个来源一条 `system` 消息**（不再拼成一整段），
片段边界本身就承载了来源信息，模型可直接区分用户级 / 项目级。
因此不再需要加小标题。见 `AHA/AHA-Design-V1.md` §5.1.5 / §5.1.7。

### 7.3 其它已知渲染缺口

| 项 | 现状 |
|---|---|
| 图片（`![alt]` 加 `(url)`） | 不渲染，保留字面量（纯文本终端无法显示） |
| 语法高亮 | 围栏代码原样输出 |
| 链接的 OSC 8 超链接 | 不生成，只用「文本 + 弱化地址」（重定向到文件会变乱码） |
| 表格单元格内换行 | 不支持，单元格内容按单行处理 |

## 8. 0.1.0 发布前问题清单

核查日期 2026-10-07。结论：**0.1 的功能无缺口**（设计文档中 0.1 阶段仅余「真实 API Key 验收」一项未勾），
卡住发布的是**可交付性**。下面的状态会随处理进度更新。

### 8.1 阻塞项（不解决发不了版）

| # | 问题 | 状态 |
|---|---|---|
| 8.1.1 | 仓库零提交（打不了 tag 就发不了版） | 🟡 `dev` / `dependa` 已建，此前那批提交**已推送**（PR #6 已合入 `dev`）；现仅剩 PR #8 冲突修复的合并提交 `06c6121` **待推送**——见 [TODO.md](TODO.md) `G-01`、`F-12` |
| 8.1.2 | **覆盖率门禁未在最终代码上验证** | ✅ 已完成：`./mvnw clean verify` 全模块通过（604 用例 / 合计行覆盖 80.4%） |
| 8.1.3 | Maven 3.9.x 基线未在本地验证 | ✅ 已完成：`3.9.11` 跑通，与 Maven 4 结果一致（604 用例 / 80.4%） |
| 8.1.4 | `dist` 打包被跳过 | ✅ 已完成：定位并结束占用者（**IDEA 的 Maven server**，而非运行中的 AHA——先前判断有误），完整 `./mvnw clean verify` 通过，`dist/` 已重建（18 个 jar，含像素标志）；处置方法见 BuildGuide 的「关于 `dist/`」 |

### 8.2 发版前必须解决

| # | 问题 | 状态 |
|---|---|---|
| 8.2.1 | 测试数据三处口径不一致 | ✅ 已刷新为 **604 用例 / 80.4%** |
| 8.2.2 | `CHANGELOG.md` 发布日 `2026-10-06` 需改为实际发布日 | ⬜ 待做 |
| 8.2.3 | CLI 文案残留「插件」 | ✅ 已修（代码 23 处 + 文档 2 处，全部改为「扩展」） |
| 8.2.4 | `CliDesign.md` 未按命名约定改名 | ✅ 已修（→ `CLIDesign.md`，7 处引用同步） |
| 8.2.5 | 真实供应商 API Key 端到端验收 | ⬜ 待做（需外部环境） |
| 8.2.6 | Windows 真机走查（整体观感、旧 CMD 降级、滚动复制、`/memory` 分支） | 🟡 部分：日志落盘 / `Aha.bat` / `dist` 冒烟已验证；观感与降级待走查 |
| 8.2.7 | `SystemPromptLoaderTest` 不具环境无关性（用户级身份文件一存在就 7 个用例全红） | ✅ 已修（2026-10-07）：测试类在 `@BeforeEach` 隔离 `AHA_HOME` / `user.home`；**该缺陷正是 PR #6 在 Windows 上失败的主因**，详见 8.2.8 |
| 8.2.8 | **Windows 腿的真实失败（PR #6 之前在 wrapper 处就断了，从未暴露）**：`build (windows-latest, wrapper)` 退出码 1，实为 15 个用例失败 | ✅ 已修（2026-10-07）：见下 |
| 8.2.9 | **`main` 分支保护未配置**：`Gate` 与 `Compat` 尚未设为必需检查，门禁形同虚设 | ☐ 待做（步骤与验收标准见 [TODO.md](TODO.md) `G-02`） |
| 8.2.10 | **定期扫描未经验证**：`Gate.yml` 的每周 `schedule` 只在默认分支生效，合入前无法确认其真的会跑 | ☐ 待做（见 [TODO.md](TODO.md) `G-03`） |
| 8.2.11 | **日志文件长期不生成** —— 见 §8.5 | ✅ 已修 |

在 Windows 上用 Git Bash 跑 `./mvnw clean verify` 复现（`D:\Dev\Git\bin\bash.exe`），
共 15 个失败 / 错误，归为四类：

| 类别 | 现象 | 修法 |
|---|---|---|
| 生产缺陷 | `--help` 混入 ANSI 转义序列（picocli 的 `Ansi.AUTO` 把 Windows 一律当支持 ANSI，重定向到文件也会写转义） | `AhaCli.commandLine()` 统一按 `System.console()` 决定 `Help.Ansi`，非交互一律关闭 |
| 生产缺陷 | `ConfigLoader.resolveModelPath` 把未展开的 `${AHA_HOME:-~/.aha}/Model.yml` 直接交给 `Path.of`，Windows 上 `Illegal char <:>`；Linux 上则静默变成名为 `${AHA_HOME:-~/.aha}` 的相对路径 | 先展开占位符；仍含 `${` 或含平台非法字符时回退默认位置 |
| 生产缺陷 | 项目级向上查找**没有边界**，会一路走到用户主目录，把 `~/AHA.md` 当成项目级身份（Windows 临时目录位于主目录之下，故只在 Windows 暴露） | 查找止步于用户主目录（`user.home`，并参考 `USERPROFILE` / `HOME`）；`resolve` 与 `findProjectRoot` 同用此边界 |
| 测试写法 | 断言硬编码 `/` 分隔符、路径里用 `<` `>`（Windows 非法）、假定「环境里没有任何身份文件」 | 用平台自身路径构造期望值；特殊字符改用纯字符串断言；前提自己造 |

**顺带修复（CI 配置）**：`Build.yml` 的矩阵加 `fail-fast: false`。此前 Windows 腿一失败，
GitHub 就取消 Linux 两条腿，页面上只看到「第一条红」，掩盖了「两个平台到底是什么结果」——
这次排查为此多绕了很久。

**验收**：Windows（Git Bash + `./mvnw clean verify`）与 Linux（`./mvnw clean verify`）均 BUILD SUCCESS。

### 8.3 建议项

| # | 项 | 状态 |
|---|---|---|
| 8.3.1 | `CHANGELOG.md` 两条重叠的「文档」条目 | ✅ 已合并 |
| 8.3.2 | 示例模型名与内置预设不一致 | ✅ 已对齐（`claude-sonnet-5-1` / `gemini-3.5-flash`） |
| 8.3.3 | `Docs/DevSpec/` 有 6 个文件不以 `Spec.md` 结尾 | ⬜ 待定（改名会牵动多处引用） |
| 8.3.4 | `Docs/Dbsx.txt`（用户原始待办）去留 | ⬜ 待定 |
| 8.3.5 | `PLAN.md` §7.1 表格数据行分割线 | ⬜ 待定 |
| 8.3.6 | `.editorconfig` 缺失 | ⬜ 可选 |
| 8.3.7 | 设计文档目录树索引漂移（`Docs/Design/` 实 15 份、树只列 12 份） | ✅ 已修（补齐 3 份 + `LoggingDesign.md`；四棵树已全量比对一致） |
| 8.3.8 | 缺日志子系统设计文档 | ✅ 已修（新增 `Docs/Design/LoggingDesign.md` v1.0.0） |

### 8.4 已确认不是问题

`aha-plugin-api` / `aha-tools` 目录已删净；`.idea/` 已被忽略；`CheckDocs` / `CheckSkills` / `CheckScripts` 全过；
`TODO.md` 已是干净的 656 行；CLI 冒烟（`version` / `provider list` / `tool list` / `init --no-input`）正常；
`dist/` 新鲜且包内默认配置正确（含 `CLAUDE.md` 候选与 `Agent.SystemPrompt`）。

### 8.5 已修复：日志文件长期不生成（2026-10-07）

**现象**：`$AHA_HOME/Log/Aha.log` 始终为空，`Log/` 目录下一个文件都没有。

**排查**：`config get Logging.File` 返回的路径**正确**，`resolveLogFile` **正确**创建目录，
全程无异常、无告警——属于「静默不生效」。

**根因**：`LoggingSetup.apply` 里写的是

```java
Configurator.initialize(null, new ConfigurationSource(...));
```

它**并不是** `initialize(String name, ConfigurationSource)`，而是被编译到
`initialize(ClassLoader, ConfigurationSource)` 重载上。该重载配置的是「按类加载器查到的」context，
与 `LogManager.getContext(false)` 返回、logger 实际绑定的那个**不是同一个实例**——配置落到了别处，
所以既不报错也永远不落盘。

根因能藏这么久，是因为两点都错得很像对的：

1. 注释写着「必须在首个 logger 创建之前调用；`ConfigLoader` 自身不记录日志」——
   实际上 `ConfigLoader` 有 4 处 `INFO`，读配置时 log4j2 **已经**按默认配置初始化过一次，
   于是 `initialize` 面对的是一个已存在的 context，而不是「还没创建」。
2. 单测只验 `resolveLevel` / `resolveLogFile` / `xml` 字符串等**纯函数**，
   没有任何测试调用过 `apply()`，**落盘行为从未被验证**。

**修复**：显式对当前 context 应用配置，并把可测内核抽出来：

```java
static void install(LoggerContext context, String xml) throws IOException {
    ConfigurationSource source = new ConfigurationSource(
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    context.start(new XmlConfiguration(context, source));
}
// apply: install((LoggerContext) LogManager.getContext(false), xml(level, file))
```

**回归测试**：`LoggingSetupTest#installWritesLogFileToDisk`——真写文件、真读回、断言标记文本。
刻意不用 `@TempDir`（log4j2 持有文件句柄，Windows 上会删除失败），写在 `target/` 下由 `clean` 清理。

**真机验证**：Windows 上 `Aha.bat version` 后 `C:\Users\ACANX\.aha\Log\Aha.log` 生成 ✓。

**顺带暴露的已知限制**：`ConfigLoader` 那几行「已加载 xx 配置」发生在 `apply` 之前，
因此**只出现在控制台，不会进日志文件**。已在该处注释说明。

### 8.6 日志文件命名与切分规则（2026-10-07 定稿）

按用户要求定稿并实现：

| 项 | 规则 |
|---|---|
| 主文件 | `AHA.log`（此前为 `Aha.log`，已统一为全大写 `AHA`） |
| 切分阈值 | 单文件超过 **10 MB** |
| 切分文件命名 | `<基名>-yyyy-MM-dd-NN.log`，如 `AHA-2026-10-07-01.log` |
| 序号 | `NN` 补零两位，按天从 `01` 递增 |
| 压缩 | **不压缩**（压缩包无法直接用编辑器查看，排查时多一步解压） |
| 历史清理 | **不自动清理**（见下方取舍） |

**关键取舍：序号递增 ≠ 保留上限**。log4j2 的 `DefaultRolloverStrategy` 达到 `max` 后会
**删除最旧的切分文件并复用其序号**——序号会「回到 01」而不是继续递增 ✗。
两者不可兼得，本次按用户要求选**序号递增**，故 `max` 取 1000（实际用不满），
代价是历史文件不自动清理 ✗。若后续要限制占用，可改用按时间删除
（`Delete` + `IfLastModified age="30d"`）——那样序号仍能递增，且占用可控。

**验证**：`LoggingSetupTest#rollsIntoNamesWithPaddedIncrementingIndex`（2 KB 阈值真滚 300 行）
+ Windows 真机（反斜杠路径下同样正确切分）。

**级别默认值**：内置默认 `DEBUG` 属**开发阶段的有意设计**（需要全量日志），不作为待办；
若日后要降低记录量，用户级 `Aha.yaml` 覆盖为 `INFO` 即可。

### 8.7 0.1.0 发布前复核（2026-10-07，逐项实查）

不凭记忆、逐条实测的结果：

> **本表是 2026-10-07 的当日实查快照，不随后续变动逐行回改。** 仓库状态此后已变化
> （提交与分支已建、用例数已刷新、文档/技能/脚本三项检查已从 `Build.yml` 移入
> `Gate.yml`），**现值以本文件 §6、§8.1 与 [TODO.md](TODO.md) 为准**。

| 项 | 实测 | 结论 |
|---|---|---|
| `git rev-parse HEAD` | `fatal: ambiguous argument 'HEAD'` | ✗ 零提交 |
| `git ls-files` | `0`（磁盘 373 个文件） | ✗ 未入索引 |
| `git branch -a` / `rev-parse` | 无任何分支 / tag | ✗ 无法打 tag 发版 |
| `git remote -v` | `origin` 已配置 | ✓ 只差提交推送 |
| 覆盖率门禁 | `pom.xml:260` `COVEREDRATIO ≥ 0.70` | ✓ 配置在 |
| 用例数（Surefire） | **604**（common 57 / extension-api 6 / core 193 / tool 25 / cli 323） | ✅ 与文档一致（§3.1 已刷新） |
| Maven 4 GA | `apache-maven-4.0.0-bin.zip` → **HTTP 404**；metadata `release=4.0.0-rc-7` | rc-7 是**当前最新**，钉版本非疏忽 ✓ |
| Maven 3.9.x | `/root/apache-maven-3.9.11` 可用（JDK 25 下可跑） | 待跑一次 ⬜ |
| CI | `Build.yml` 含 wrapper(**Maven 4**) 与 system(**Maven 3.9.x**) 双腿 + 三项检查 | ✓ 推送后自动覆盖基线 |
| 未勾的 0.1 验收项 | 仅验收清单里「LLM 供应商接入测试」（需真实 API Key），见 `AHA/AHA-Design-V1.md` §0.1 功能核查 | 唯一遗留 ⬜ |
| `Aha.bat` / `dist/bin/Aha.bat` | `file`: ASCII + CRLF | ✓ |
| `dist/` | 18 个 jar；`Aha.sh version` → `AHA 0.1.0`；`aha-core` jar 内含 `AhaDefault.yaml` / `ModelDefault.yml` | ✓ 新鲜可用 |
| 核心模块 `System.out/err` | 0 处（CLI 除外） | ✓ 验收项可判 |
| `TODO` 标记 | 5 处，全部 `TODO(0.3)`（扩展系统） | ✓ 非发布阻塞 |
| `Docs/` 四棵目录树 | Design 16 / DevSpec 11 / Guide 8 / Diagrams 7，与磁盘**逐一比对一致** | ✓（此前 Design 缺 3 份，已修） |
| `version.properties` | `version=${project.version}` + 资源过滤 | ✓ |

**本次因此改动的文档**：`LoggingDesign.md`（新建）、`ConfigurationGuide` / `ReferenceGuide`（`Logging.Level` 默认值 `INFO` → `DEBUG`，与 `AhaDefault.yaml` 一致）、`TroubleshootingGuide`、`Constitution`（第 10 条第 8 项）、`AHA-Design-V1`（目录树 + 清单表 + 日志小节指针）、`CHANGELOG`（滚动口径更正）。


---

## 9. 相关文档

- [AHA-Design-V1.md](AHA/AHA-Design-V1.md)：权威版本路线图与阶段计划
- [TODO.md](TODO.md)：待办与调整项
- [DevLog/](DevLog/)：排障复盘与事故记录（`DevLog-YYYYmmdd-HH.md`）
- [CLIDesign.md](Design/CLIDesign.md)：终端与渲染决策（含 §7.1 状态行、§7.2 输入行构成、§7.3 工具区块）
- [MemoryStorageDesign.md](Design/MemoryStorageDesign.md)：记忆存储与载体选型

---
