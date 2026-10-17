# TS-2610-001 · CI 排障复盘：必需检查因矩阵作业名变更而永久挂起

> 日期：2026-10-07
> 作者：@ACANX（与 AI 协作）
> 关联 PR：[#6](https://github.com/ACANX/AHA/pull/6)（bump: Dependa deps → main）
> 关联记录：[TODO.md](../TODO.md) `F-08`、[BuildSpec.md](../DevSpec/BuildSpec.md) §8.1

---

## 一、背景

本轮把 CI 检查按耗时分层：慢检查（完整 `clean verify`、覆盖率门禁、文档 / 技能 / 脚本检查、
重复代码率）从「每次 push」拆到独立的 `Gate.yml`（合入 `main` 前、每周定期、发布前），
并新增 `Compat.yml` 验证 Maven 3.9.x 兼容基线；`Build.yml` 只保留编译 + 单元测试的快速反馈。

改造后，PR 的合并框里三条必需检查一直停在：

```
build (ubuntu-latest, system)    Expected — Waiting for status to be reported   Required
build (ubuntu-latest, wrapper)   Expected — Waiting for status to be reported   Required
build (windows-latest, wrapper)  Expected — Waiting for status to be reported   Required
```

现象很像「CI 卡住」或「runner 排队堵塞」：重跑无效，重开 PR 无效，Workflow 页面里也看不到
任何失败——**没有任何一步变红，只是一直在等**。

## 二、排障过程与修复链

### 2.1 先排除「workflow 根本没跑」

抓取 Actions 运行页，确认四个作业都**真实执行过**（`build (windows-latest, wrapper, false)`、
`build (ubuntu-latest, wrapper, false)`、`build (ubuntu-latest, system, false)`、
`build (macos-latest, wrapper, true)`），且 step 级状态正常。

> 结论：不是「没触发」，也不是「被跳过」——是**上报的名字与期望的名字不一致**。

### 2.2 取证：把「要求」与「实际上报」逐条对照

`Expected — Waiting for status to be reported` 的语义是：**分支保护挂着一个必需检查，
但从没有任何 check run 以这个名字上报过**。于是直接取作业标签做对照：

| 分支保护要求（等待中的名字） | 实际上报的作业名 |
| --- | --- |
| `build (windows-latest, wrapper)` | `build (windows-latest, wrapper, **false**)` |
| `build (ubuntu-latest, wrapper)` | `build (ubuntu-latest, wrapper, **false**)` |
| `build (ubuntu-latest, system)` | `build (ubuntu-latest, system, **false**)` |

三条全部多出一个 `, false`——对应的正是本轮为「消除未定义矩阵键的歧义」而给**每条腿**
补上的 `optional: false`。

### 2.3 根因

GitHub Actions 的**矩阵作业名会把矩阵的全部键值拼进去**：

```
<作业名> (<值1>, <值2>, …)
```

键值集合一变，作业名就变：

```
matrix: { os, maven }                  →  build (windows-latest, wrapper)
matrix: { os, maven, optional }        →  build (windows-latest, wrapper, false)
```

而**分支保护的必需检查严格按名字匹配**。名字对不上 → 检查永远处于「等待上报」→
PR 永久不可合并，且**不会报错、不会超时、页面上不会给出任何提示**。

这与「CI 失败」是两种完全不同的状态：失败至少会变红、会指出哪一步；**失配只会安静地等**。

### 2.4 修复

不给必需腿增删矩阵键，可选腿的开关**从已有键推导**：

```yaml
      matrix:
        include:
          - os: windows-latest
            maven: wrapper
          - os: ubuntu-latest
            maven: wrapper
          - os: ubuntu-latest
            maven: system
          - os: macos-latest
            maven: wrapper
    # 判定依据直接取自 os，不额外增加矩阵键
    continue-on-error: ${{ matrix.os == 'macos-latest' }}
```

三条必需腿的作业名恢复原样，**无需改动分支保护**；`macOS` 腿此前的标签
`build (macos-latest, wrapper, true)` 也一并收敛为 `build (macos-latest, wrapper)`。

### 2.5 自查方法（复现取证）

不必登录也能拿到作业标签：抓 Actions 运行页 HTML，抽出所有以 `build (` 开头的文本即为作业名。

```bash
# 1) 取最近一次运行的 run id
curl -sS -H "User-Agent: Mozilla/5.0" \
  "https://github.com/ACANX/AHA/actions?query=branch%3Adependa" \
  | grep -oE '/ACANX/AHA/actions/runs/[0-9]+' | head -3

# 2) 取该次运行的作业标签，与分支保护要求的名字对照
curl -sS -H "User-Agent: Mozilla/5.0" \
  "https://github.com/ACANX/AHA/actions/runs/<run-id>" \
  | sed 's/<script[^>]*>/\n/g' | grep -oE 'build \([^)]+\)' | sort -u
```

## 三、最终验证结果

本地比对矩阵键与预期作业名（作业名由 `os` + `maven` 决定）：

```
矩阵键: ['maven', 'os']                        ← 已无 optional
作业名 → build (windows-latest, wrapper)        ✅ 与分支保护一致
作业名 → build (ubuntu-latest, wrapper)         ✅ 与分支保护一致
作业名 → build (ubuntu-latest, system)          ✅ 与分支保护一致
作业名 → build (macos-latest, wrapper)          ← 可选腿，非必需检查
continue-on-error: ${{ matrix.os == 'macos-latest' }}
```

推送后三条必需检查即可正常上报（本文件写作时提交尚未推送）。

## 四、关键教训

1. **必需检查按「作业名」匹配，而矩阵作业名包含矩阵的全部键值。**
   给必需腿增删任何一个矩阵键，都等于改了必需检查的名字——哪怕那个键在语义上完全无关。
2. **「永远 Expected」不会失败，只是等待。** 它比失败更隐蔽：不报错、不超时、
   页面无提示，很容易被误判为 runner 排队或 CI 卡死，从而在错误的方向上重试。
3. **可选腿的开关要从已有键推导**（`matrix.os`），不新增专用键；语义显式的收益远小于
   作业名漂移的风险。
4. **job 级 `name:` 同样是检查名**（`Gate.yml` / `Compat.yml` 都用了它），
   改 `name:` 等于改必需检查的名字。
5. **改名必须成对操作**：工作流里的作业名 / 必需腿矩阵键一变，就要同步更新分支保护，
   并刷新台账（[TODO.md](../TODO.md) `G-02` 的验收清单），否则下一次失配同样无人知道。
6. **排障要拿到原始证据再下结论。** 本轮先取得作业标签做逐条对照，才在两分钟内定位；
   若停在「像是卡住」的猜测上，会一直在 runner、配额、网络这些方向上绕。

## 五、涉及文件清单

| 文件 | 变更 |
| --- | --- |
| `.github/workflows/Build.yml` | 删除 `optional` 矩阵键；可选腿改为按 `matrix.os` 判定；补「作业名是契约」的注释 |
| `Docs/DevSpec/BuildSpec.md` | §8.1 新增「作业名是分支保护的契约（强制）」，含本次事故实例 |
| `Docs/TODO.md` | 新增 `F-08`（含取证对照表）；`G-02` 按分支保护实况重写并补维护约定 |
| `CHANGELOG.md` | 补记该修复 |

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「一、二、…」编号冲突。 -->

## 结论分层

### 已观测事实

- 分支保护的三条必需检查停在 `Expected — Waiting for status to be reported`，**重跑无效、重开 PR 无效**，且 Workflow 页面上**没有任何失败步骤**。
- 四个作业**都真实执行过**，step 级状态正常，上报的名字是：
  `build (windows-latest, wrapper, false)`、`build (ubuntu-latest, wrapper, false)`、
  `build (ubuntu-latest, system, false)`、`build (macos-latest, wrapper, true)`。
- 把「分支保护要求」与「实际上报」逐条对照，三条必需腿**全部多出一个 `, false`**（对照表见「二、2.2」）。
- 本地比对修复后的矩阵键与作业名：`矩阵键: ['maven', 'os']` →
  `build (windows-latest, wrapper)` / `build (ubuntu-latest, wrapper)` /
  `build (ubuntu-latest, system)` / `build (macos-latest, wrapper)`。

### 机制 / 行为模型

- GitHub Actions 的**矩阵作业名会把矩阵的全部键值拼进去**：`<作业名> (<值1>, <值2>, …)`；
  键的集合一变，作业名就变——`{os, maven}` 得到 `build (windows-latest, wrapper)`，
  再加一个 `optional` 键就变成 `build (windows-latest, wrapper, false)`。
- **分支保护的必需检查严格按名字匹配**。名字对不上时，检查停在「等待上报」——
  它**不是失败**：不报错、不超时、页面无提示，只让 PR 永久不可合并。
- 依据：`Expected — Waiting for status to be reported` 的语义（「挂着必需检查，但没有任何 check run 以此名上报过」）
  与上表的逐条对照结果。此处是**由实测对照推出的行为模型**，未引用上游源码。

### 未定位 / 未复现

- 本档案写作时**修复尚未推送**，因此「作业名恢复后必需检查立即正常上报」在当时**只是预期，未实测**
  （原文：「推送后三条必需检查即可正常上报（本文件写作时提交尚未推送）」）。
- **未做对照实验**：矩阵键变更后，**已在途 PR** 的必需检查是否会自行恢复，或必须重新触发一次。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 分支保护等待状态 | GitHub PR 合并框原文 | `build (ubuntu-latest, system)    Expected — Waiting for status to be reported   Required` |
| 实际上报的作业名（4 条） | 抓 Actions 运行页 HTML，`grep -oE 'build \([^)]+\)'` | `build (windows-latest, wrapper, false)` / `build (ubuntu-latest, wrapper, false)` / `build (ubuntu-latest, system, false)` / `build (macos-latest, wrapper, true)` |
| 要求 vs 实际差异 | 同上逐条对照 | 三条必需腿**均多出 `, false`**；根因指向本轮新增的 `optional: false` |
| 本地比对矩阵键 | 读 `Build.yml` 与作业名推导 | `矩阵键: ['maven', 'os']`（已无 `optional`） |
| 复现取证的命令 | 见「二、2.5」 | `curl -sS -H "User-Agent: Mozilla/5.0" "https://github.com/ACANX/AHA/actions?query=branch%3Adependa" \| grep -oE '/ACANX/AHA/actions/runs/[0-9]+' \| head -3` |

> **可复现性缺口（如实记录）**：原档案**未保留具体的 run id / run 链接**，也没记录取证时的绝对时间；
> 上表第 2、3 行只能凭「抓运行页」的方法重取当次运行来对照。

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| workflow 根本没触发 / 被 `if` 跳过 | 抓运行页确认四个作业**都真实执行过**，step 级状态正常（「二、2.1」） |
| runner 排队堵塞 / 配额不足 | 作业已执行完毕（有 step 级状态），不是排队中；且页面上无排队提示 |
| CI 真的失败、只是没看红叉 | Workflow 页面**无任何失败步骤**，只有「等待上报」（「一、背景」） |
| 分支保护挂的检查指向了别的工作流 / 别的作业 | 三条等待中的名字与实际作业名**只差 `, false`**，说明要求指向的正是这些作业（「二、2.2」） |
| 重跑 / 重开 PR 可以自愈 | 实测**重跑无效、重开 PR 无效**（「一、背景」） |

## 未决问题

**Q1 矩阵键一改就改掉必需检查的名字——能不能上机器强制？**

现状：本档案只落了**人工约定**（「改名必须成对操作」，见「四、关键教训」第 5 条），
没有任何 CI 断言去比对「必需腿的作业名集合」与分支保护配置是否一致。
阻塞点：读分支保护配置需要 GitHub API + 相应 token 权限，尚未评估可行性与放置位置（`Gate.yml`？）。

**Q2 修复推送后必需检查是否立即恢复上报？**

现状：写作时提交未推送，**未实测**。若未立即恢复，需要补一次「推送前后的上报状态」对照。

**Q3 改名之后，已在途 PR 要不要重新触发？**

现状：**未做对照实验**——「重跑无效」是在**名字还没修好**的前提下测的，不能推出「名字修好后无需重新触发」。
