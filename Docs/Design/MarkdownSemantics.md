# Markdown 语义基线（issue #408 / 阶段 0）

> 本文件记录「CLI 与桌面端必须一致的 Markdown 语义」，以及当前实现与目标行为的差距。
> 可执行版本在 `aha-core/.../markdown/MarkdownSemanticsSamples.java`：
> CLI 侧 `MarkdownSemanticsTest` 直接断言，桌面端在 S1.1 起断言 AST。

## 为什么需要这份基线

CLI 的 `MarkdownStream` 与桌面端的 `MarkdownParser`（S1.1）是两套实现、同一个模型输出。
两边对「空行是否终止表格」「围栏内要不要解析」理解不一致时，用户会看到同一段回答在两端不一样。
把规则固化成**共享样本**，任何一端改语义都会先让测试变红。

样本放在 `aha-core` 的 main 源码里，而不是某个模块的测试资源——Maven 的测试类不跨模块可见，
放 test 里就必然要复制一份，而复制正是漂移的来源。

## 规则与样本

| 规则 | 样本 id | 断言（CLI 渲染结果） |
|---|---|---|
| `table-boundary` | `table-blank-line-splits` | 空行终止表格，两张表各自成块（`┌` / `┘` 各 2 次） |
| `table-boundary` | `table-blank-line-kept` | 表格与后文之间保留一个空行（正则 `└…\n\n段落`） |
| `table-boundary` | `table-direct-body` | 表格后直接接正文仍正常结算，后文不被吞 |
| `fence-literal` | `fence-keeps-emphasis` | 围栏内 `**not bold**` 按字面量输出 |
| `fence-literal` | `fence-keeps-table` | 围栏内 `\| a \| b \|` 不被当成表格 |
| `fence-literal` | `fence-keeps-strike` | 围栏内 `~~x~~` 按字面量输出 |
| `inline-flanking` | `multiplication-not-emphasis` | `2 * 3 * 4` 不构成强调 |
| `inline-flanking` | `snake-case-not-emphasis` | `snake_case_name` 不构成强调 |
| `inline-flanking` | `bold-drops-markers` | 粗体成立时去掉 `**` |
| `inline-flanking` | `four-delimiters-literal` | 连续 ≥4 个定界符按字面量输出（样本带前后缀，行首变体是另一个缺陷 #429） |
| `escape-literal`（**pending**） | `escaped-star` | `\*x\*` → `*x*`（目标行为） |
| `escape-literal`（**pending**） | `escaped-underscore` | `\_x\_` → `_x_`（目标行为） |
| `escape-literal`（**pending**） | `escaped-backtick` | `` \`x\` `` → `` `x` ``（目标行为） |

`pending` 样本只记录目标行为、不参与断言：`InlineScanner` 目前**不处理反斜杠转义**，
若现在把它当前的行为写成期望，等于把「已知未实现」固化成正确（PR #404 审查 P3-2 的同类教训）。
S1.2 实现转义后，把样本的 `pending` 置为 `false` 即可启用。

## 维护约定

1. 新增语义先加样本，再改实现（样本是契约，不是实现的副产品）；
2. 同一语义在 CLI 与桌面端的断言都要有——只在一边加，另一端就失去了约束；
3. 规则类不足 3 条时 `MarkdownSemanticsTest` 会失败（防止样本被删到名存实亡）。

## 阶段 3 之后的实现状态（2026-10-20）

| 项 | 状态 |
|---|---|
| 白名单 HTML（`br` / `sub` / `sup`） | 已实现；白名单外标签按字面量保留 |
| 裸 URL 自动链接 | 已实现；代码段与围栏内不识别 |
| 不支持项（脚注 / 定义列表 / 数学 / mermaid） | 按字面量展示，各有兜底测试（`MarkdownUnsupportedTest`） |
| 反斜杠转义 | **仍未实现**，3 条 `escape-literal` 样本保持 `pending` |

桌面端支持矩阵见 [MarkdownRenderingGuide.md](../Guide/MarkdownRenderingGuide.md)（实现与文档不一致时以该文为准）。

## 关联

- 阶段 0 子任务：#409（语义基线）、#412（性能基线与 xvfb 冒烟）
- 跟踪母 issue：#408
- CLI 实现：`aha-cli/.../render/MarkdownStream.java`、`InlineScanner.java`
- 桌面实现：`aha-desktop/.../markdown/MarkdownParser.java`、`MarkdownInlineParser.java`
- 桌面支持矩阵（阶段 3 起）：[MarkdownRenderingGuide.md](../Guide/MarkdownRenderingGuide.md)
