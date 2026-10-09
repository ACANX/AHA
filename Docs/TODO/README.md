# 待办事项清单（TODO）

**文档版本**：v1.1.0
**状态**：生效
**生效日期**：2026-10-09
**最后更新**：2026-10-09
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 0. 本目录的定位

**待办事项的汇总清单与进度看板**。一个待办一个文件（`TD-PPPPP-*.md`），本 `README.md`
是清单索引；逐条的长期跟踪载体仍是 GitHub Issue。

| 载体 | 职责 |
|---|---|
| **本清单**（`Docs/TODO/README.md`） | 汇总全部待办、标记状态、定期更新进度；便于一眼看全貌 |
| **`TD-*.md`** | 单个待办的详情（来源、现状、需求、验收标准） |
| **GitHub Issue** | 单个事项的长期跟踪：评论、状态、关闭 |
| `Docs/Troubleshooting/` | 问题排查 / 事故复盘的**过程记录** |
| `Docs/DevLog/` | 已完成事项的**开发记录** |
| `Docs/PLAN.md`（已冻结） | 「做不到 / 已决定暂缓」的结论与依据 |

> `Docs/TODO.md` 为历史文件，**已冻结**，不再更新。

## 1. 文件命名

```
TD-PPPPP-大驼峰英文标题.md
```

| 片段 | 规则 | 示例 |
|---|---|---|
| `TD` | 固定前缀（Todo） | `TD` |
| `PPPPP` | **五位数字，从 `00001` 开始全局自增**，不复用、不跳号 | `00001`、`00002` |
| 标题 | **英文大驼峰**（PascalCase），概括事项，2~5 个词 | `CliToolCallOutputRendering` |

示例：

```
TD-00001-CliToolCallOutputRendering.md
TD-00004-CliWindowsMultilinePaste.md
TD-00014-ManualAndExternalActions.md
```

- `README.md` 是清单索引，**不走**本命名（它是目录约定的固定名）；
- 一个待办一个文件；**新文件取当前最大编号 + 1**（本清单 §4 最后一行即最新编号）；
- **内容格式不做强制要求**，建议参考对应 Issue 的结构：来源 / 现状 / 需求或待拍板 / 验收标准 / 关联；
- 文件头部用引用块写明「待办编号 / 标题 / 状态 / 跟踪 Issue / 创建日期 / 最后更新」。

## 2. 维护规则

**何时新增**：出现一个**本轮不做或做不完**的事项时——需要长期跟踪就**开 Issue**，
需要看进度就在本目录建 TD 文件；两者常一起用（Issue 跟踪进展，TD 登记进度）。
本轮就能改完的缺陷**不用**建 TD，直接改。

- **新增待办**：建 `TD-<下一个编号>-*.md`，并在 §4 清单加一行、在最近的分组里登记；
- **状态更新**：有进展或闭环时，**同一变更内**同时更新 TD 文件的头部状态与 §4 清单该行；
- **已完成**：状态写 `✅ 已完成`，并在备注里写清**依据**（CI 运行编号 / PR / 实测），不要只写「完成」；
- **定期维护**：每轮工作结束前扫一遍 §4，把状态过期（实际已闭环 / 已搁置）的行更新，
  并同步文件头的「最后更新」；
- **与 Issue 同步**：Issue 关闭后清单标 `✅ 已完成`；Issue 重开时同步回退状态。

### 状态标记

| 标记 | 含义 |
|---|---|
| `☐ 未开始` | 已登记，尚未动手 |
| `◐ 进行中` | 部分完成，或已改动未收口 |
| `⏸ 阻塞` | 依赖外部条件（人工 / 平台 / 外部环境），备注写明等什么 |
| `✅ 已完成` | 已闭环，备注里给依据 |
| `✖ 已取消` | 决定不做，备注里写明原因与替代方案 |

---

## 3. 当前编号

- 已用：`TD-00001` ~ `TD-00014`
- **下一个可用编号：`TD-00015`**

## 4. 清单

### 4.1 CLI / TUI

| TD | 标题 | 状态 | Issue | 备注 |
|---|---|---|---|---|
| [TD-00001](TD-00001-CliToolCallOutputRendering.md) | 工具调用输出的分块展示与差异渲染 + DeepSeek 工具调用补齐 | ☐ 未开始 | [#72](https://github.com/ACANX/AHA/issues/72) | 来自 `Dbsx.txt` §1 |
| [TD-00002](TD-00002-CliMarkdownRichRendering.md) | Markdown 富文本渲染与渲染缺口（表格分割线 / 图片 / 语法高亮 / OSC 8） | ☐ 未开始 | [#73](https://github.com/ACANX/AHA/issues/73) | |
| [TD-00003](TD-00003-CliInputAndStatusBar.md) | 输入框样式与 loading 动效、底部状态栏、斜杠命令下拉补全 | ☐ 未开始 | [#74](https://github.com/ACANX/AHA/issues/74) | 状态栏用量部分依赖 0.6 用量落库 |
| [TD-00004](TD-00004-CliWindowsMultilinePaste.md) | Windows 终端多行粘贴兼容性（conhost 限制） | ◐ 进行中 | [#75](https://github.com/ACANX/AHA/issues/75) | 已有两项加固，conhost 行为待定 |

### 4.2 桌面端

| TD | 标题 | 状态 | Issue | 备注 |
|---|---|---|---|---|
| [TD-00005](TD-00005-Desktop02Remaining.md) | 0.2 收尾：工具面板开关、上下文压缩入口、会话内搜索与 F6 | ☐ 未开始 | [#76](https://github.com/ACANX/AHA/issues/76) | |
| [TD-00006](TD-00006-DesktopProviderEnhancement.md) | 供应商连接测试 + 密钥写入密钥库 | ☐ 未开始 | [#77](https://github.com/ACANX/AHA/issues/77) | |
| [TD-00007](TD-00007-DesktopPackagingAndCoverage.md) | jpackage 自包含安装包 + 覆盖率门禁上线 | ☐ 未开始 | [#78](https://github.com/ACANX/AHA/issues/78) | |

### 4.3 原生镜像

| TD | 标题 | 状态 | Issue | 备注 |
|---|---|---|---|---|
| [TD-00008](TD-00008-NativeExtensionModuleLayerConflict.md) | 扩展系统与 native-image 的根本冲突与降级策略（E-01） | ☐ 未开始 | [#79](https://github.com/ACANX/AHA/issues/79) | 前置决策，建议先拍板 |
| [TD-00009](TD-00009-NativeRiskAndReleaseMatrix.md) | E-02 ~ E-11 风险登记、验收标准与发行矩阵 | ☐ 未开始 | [#80](https://github.com/ACANX/AHA/issues/80) | |
| [TD-00010](TD-00010-NativePipelineReview.md) | 管线复核与优化（N-10 ~ N-17、N-19、N-21、N-22） | ☐ 未开始 | [#81](https://github.com/ACANX/AHA/issues/81) | |

### 4.4 核心 / 架构 / 记忆

| TD | 标题 | 状态 | Issue | 备注 |
|---|---|---|---|---|
| [TD-00011](TD-00011-CoreContractAndArchGovernance.md) | AgentEngine 契约、ServiceLoader 双声明、依赖治理、jlink 解除条件 | ☐ 未开始 | [#82](https://github.com/ACANX/AHA/issues/82) | |
| [TD-00012](TD-00012-MemoryCapability06.md) | 记忆能力（0.6）：upsert / 工具与 `/memory` / 策略接入 / curate / 载体选型 | ☐ 未开始 | [#83](https://github.com/ACANX/AHA/issues/83) | |

### 4.5 工程效能 / 文档 / 人工

| TD | 标题 | 状态 | Issue | 备注 |
|---|---|---|---|---|
| [TD-00013](TD-00013-EngineeringAndDocHygiene.md) | CI 日志噪音、工作流与文档检查、脚本目录、暂存区文档去留 | ☐ 未开始 | [#84](https://github.com/ACANX/AHA/issues/84) | |
| [TD-00014](TD-00014-ManualAndExternalActions.md) | 分支规则集整改、定期扫描、合并方式、裸 tag、0.1.1 发布、真机走查 | ⏸ 阻塞 | [#85](https://github.com/ACANX/AHA/issues/85) | 需仓库 / 平台权限或外部环境 |

## 5. 已由既有 Issue 跟踪（未建 TD 文件）

| Issue | 事项 |
|---|---|
| [#48](https://github.com/ACANX/AHA/issues/48) | 原生桌面端字体渲染与 JVM 模式不一致 |
| [#49](https://github.com/ACANX/AHA/issues/49) | 暗色模式下标题文字对比度 / 控件适配 |
| [#51](https://github.com/ACANX/AHA/issues/51) | GUI 从发布页检测最新 tag 并完成本地升级 |
| [#60](https://github.com/ACANX/AHA/issues/60) | 原生桌面镜像读不到系统与驱动信息 |
| [#28](https://github.com/ACANX/AHA/issues/28) | GraalVM GitHub Action 参数配置参考文档 |
| [#67](https://github.com/ACANX/AHA/issues/67) | 供应商内 N 档模型 |
| [#68](https://github.com/ACANX/AHA/issues/68) | 对话中粘贴图片 / 文件的多模态输入 |
| [#70](https://github.com/ACANX/AHA/issues/70) | Agent 执行边界：能力约束 |
