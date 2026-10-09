---
name: issue-tracking
description: 遗留事项的跟踪载体与写法：把未闭环的问题、待决策项、需人工/平台执行的动作用 GitHub Issue 跟踪，写明现象、背景、待排查方向与验收标准。当发现一个不适合本轮立即解决、或本轮未能闭环的问题时使用。
license: Apache-2.0
compatibility: 需要已登录的 `gh` CLI；对仓库 Issue 的写权限。无需构建工具链。
metadata:
  version: "1.0.0"
  owner: ACANX
---

# 遗留事项用 Issue 跟踪

## 何时使用

出现下列任一情况时：

- 本轮**未能闭环**的问题（查不动、需外部环境、需后续迭代）；
- 需要**人拍板**的取舍项（技术上无唯一正解）；
- **自动化做不到、必须由人完成**才能收口的事（推送、仓库 / 分支保护设置、平台侧配置、外部环境验收）；
- 从设计文档 / 规范里读出的、暂时不动的已知缺口。

**判定标准**：一个遗留问题如果**不适合下一轮立即动手**，就应该是一条 Issue。

## 硬性约定

1. **不得把上述内容写进 `Docs/TODO.md`**——除非 ACANX 明确要求。
   `TODO.md` 已冻结为历史记录，不再是待办的存储位置。
2. **不得只在对话里交代**。对话会滚走：漏掉之后既没有闭环，也无从判断「到底做过没有」。
3. 开 Issue 前先**搜索是否已有同类 Issue**（`gh issue list --search`），避免重复；已有则补充评论而不是新开。
4. 关闭 Issue **需要 ACANX 授权**；Agent 不自行关闭，除非用户明确要求。

## 何时不用

| 场景 | 去处 |
|---|---|
| 本轮就能改完的缺陷 | 直接改，不必开 Issue |
| 排障 / 事故复盘 | `Docs/Troubleshooting/`（`TS-yyyyMM-PascalCaseTitle.md`），必要时再开 Issue 长期跟踪 |
| 已完成事项的记录 | `Docs/DevLog/`（`yyyyMMdd-HH.md`，规范与模板见该目录 `README.md`） |
| 「做不到 / 已暂缓」的**结论**及其依据 | `Docs/PLAN.md`（只追加结论，不追加待办） |
| 已定方案的详细设计 | `Docs/Design/` 对应文档 |

## 写法

### 标题

`模块/主题：一句话说清要解决什么`。例：

- `CLI：Windows 终端多行粘贴兼容性（conhost 下仍会逐行提交）`
- `原生镜像：扩展系统与 native-image 的根本冲突与降级策略（E-01）`

避免「优化一下」「看看这个」这类无法检索的标题。

### 正文结构

```markdown
## 来源
（设计文档 / 规范 / TODO 编号 / 对话结论——可追溯）

## 现状
（实测到的现象与证据，尽量给命令与输出，不给「感觉」）

## 需求 / 待拍板
（要做成什么样；若需拍板，列出可选项与各自代价）

## 验收标准
- [ ] 可判定的条件，逐条列出

## 关联
（相关文档、Issue、PR、依赖项）
```

### 标签

用仓库既有标签：`bug` / `enhancement` / `feature` / `documentation` / `docs` / `accessibility`。
**不要为了贴标签新建标签。**

## 命令

```bash
# 先查重
gh issue list --repo ACANX/AHA --state all --search "关键词"

# 新建（正文用文件，避免 shell 转义问题）
gh issue create --repo ACANX/AHA \
  --title "模块：一句话" \
  --body-file /tmp/issue.md \
  --label "enhancement,feature"

# 补一条进展评论
gh issue comment <编号> --repo ACANX/AHA --body-file /tmp/comment.md
```

## 收尾

- 问题解决后：在对应 PR 描述里写 `Closes #<编号>`，或在 Issue 里补验证依据后请 ACANX 确认关闭；
- 关闭时写明**验收依据**（跑过的命令、看过的日志、实测数据），不要只写「已完成」；
- 若某项长期不动，保持开启并在评论里记录最新判断——**不要静默丢弃**。
