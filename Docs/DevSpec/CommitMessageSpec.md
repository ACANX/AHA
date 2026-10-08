# 提交信息规范

**文档版本**：v1.1.0
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
| v1.1.0 | 2026-10-06 | 新增自举期 `Harness:` 标注约定；修正 scope（`tools` → `tool`） | @ACANX |

---

## 1. 格式

采用 Conventional Commits：

```
<type>(<scope>): <subject>

<body>

<footer>
```

## 2. type

`feat`、`fix`、`docs`、`style`、`refactor`、`test`、`chore`、`build`、`ci`

## 3. scope

模块名：`common`、`extension-api`、`core`、`tool`、`cli`、`desktop`、`docs`

## 4. 示例

```
feat(core): 新增 Anthropic 适配器

实现 LlmProviderAdapter 接口，支持 Anthropic Messages 协议。
包含请求转换、响应转换、流式事件转换。

Closes #123
```

## 5. 提交前自证（强制）

提交前逐条核对，**不要**拿 `git status` 的「干净」当证据——它对被忽略的文件一无所知。

| # | 动作 | 为什么 |
|---|---|---|
| 1 | **断言分支**：`git rev-parse --abbrev-ref HEAD` 是预期分支 | 避免把提交落到别人的分支上 |
| 2 | **核对新增文件真的入库**：`git ls-files <新文件…>` 逐个点名 | `git add -A` 会**静默跳过**被 `.gitignore` 命中的文件 |
| 3 | **确认没有源码被忽略**：`python3 bin/CheckScripts.py` | 该脚本会问 git 要「被忽略且在 `src/` 下或本身就是 `.java`」的文件，有则失败 |
| 4 | 只在本地跑快检查（单模块 `test -Djacoco.skip=true` 与 `bin/Check*.py`） | 慢检查由 CI 承担，见 `BuildSpec.md` §8 |

> **真实事故（2026-10-08）**：`.gitignore` 里不带前导斜杠的 `Log/`（本意是仓库根的运行期
> 日志目录）在**任意层级**匹配，且 Windows / macOS 大小写不敏感，于是吃掉了
> `aha-desktop/src/main/java/.../desktop/log/` 的 8 个源文件。git 不报错、`git add -A`
> 静默跳过、`git status` 显示干净、本地测试全绿，**只有 CI 报 `cannot find symbol`**。
> 修复方式是把运行期目录锚定到仓库根（`/Log/`），并加上第 3 条守卫。

## 6. Harness 标注（自举期）

自举期间（0.2 ~ 1.0），由 AHA 产出的提交须在 footer 标注 `Harness:` 行：

```
feat(core): 新增 XX 能力

<正文>

Harness: aha/0.2.0
Session: <session-id>
```

- `Harness: <名称>/<版本>`：标识产出该提交的 Agent Harness
- `Session: <session-id>`：可选，便于回溯会话记录
- 人工提交不标注 `Harness` 行

该约定用于自举度量与审计（见 [SelfHostingDesign.md](../Design/SelfHostingDesign.md) 第 4 节），
不参与 CI 门禁。
