# TD-00010 NativePipelineReview

> 待办编号：TD-00010
> 标题：原生镜像管线：复核与优化（N-10 ~ N-17、N-19、N-21、N-22）
> 状态：☐ 未开始
> 跟踪 Issue：[81](https://github.com/ACANX/AHA/issues/81)
> 创建日期：2026-10-09
> 最后更新：2026-10-09

---

## 来源

`Docs/TODO.md` 第 11.1 节「原生镜像管线（DesktopNative）待观察项」与相关 `N-xx` 条目。

## 范围

### 1. 真机验证与数据采集

| 编号 | 事项 | 现状 |
| --- | --- | --- |
| N-05 | 首次真机验证**三条腿**（Windows / Linux / macOS）的下载产物：双击即用、能开窗、能对话 | 仅 Windows 经过多轮验证（`N-16` / `N-23`），Linux / macOS 未验证 |
| N-06 | 填写对比表真实数字（原生 JDK25 / JDK27 / JVM+Leyden AOT）：冷启动、驻留内存，每数字取 5 次中位数并记机器 | 待采集 |
| N-07 | JDK 27 分支扩展到 Linux / macOS；二进制内部版本号带 PR 段；体积瘦身 | 后续 |
| N-19 | 真机验证 CLI 原生镜像（`aha-cli-native`）：`--version` / `--help`、非交互 `run`、交互式 Ctrl+C、无缺类 | 未跑过真机 |

### 2. 管线复核（多为「确认已闭环」，若已闭环直接关闭本组子项）

| 编号 | 事项 |
| --- | --- |
| N-10 | 下次 `DesktopNative` 运行确认四条腿都绿、摘要逐腿写清结论；windows-x64 工具链自证已因 `cmd //c` 修好 |
| N-11 | 确认 `native-*` 作业**没有**被加进任何分支保护的必需检查 |
| N-12 | 确认「静默跳过」已根除（三条 jdk25 腿的「上传制品」不再 skipped；`native-publish` 真出预发行版） |
| N-13 | 把资源清单从「宽通配」收窄成 `resource-config.json` 精确清单（首次真编镜像里 27.69 MiB 是内嵌资源）。**前置**：先完成真机走查 |
| N-14 | 删除弃用的 `--enable-url-protocols`（改用 reachability metadata）。**前置**：原生二进制完成一次真实 HTTPS 对话 |
| N-15 | 复核「改名 / 上传制品」不再被静默跳过（带 `continue-on-error` 的步骤不发布 outputs 的坑） |
| N-17 | 用 tracing agent 采集元数据（手写清单的抄底方案），覆盖 CLI 与桌面端 |

### 3. 技能成熟度

| 编号 | 事项 |
| --- | --- |
| N-09 | 把 `java-app-graalvm-native-image-compile` 迭代到成熟：目标平台真机跑通 + 未验证条目清零 + **在别的项目复用过一次** |
| N-21 | 把 `graalvm-reachability-metadata` 迭代到成熟：两个形态（GUI + CLI）走通 + 推断条目清零 + tracing agent 至少启用过一次 |
| N-22 | 在 Windows / macOS 各跑一轮 tracing agent，补齐平台专属类（Win* / Mac* 的 Glass / Prism 实现） |

## 验收标准

- [ ] N-05 / N-19 的真机结论写进对应设计文档与 `Docs/Troubleshooting/`；
- [ ] N-06 的对比表填入真实数字（含机器与测量口径）；
- [ ] N-10 ~ N-15 逐条给出「已闭环 / 仍需动作」的结论，闭环的在本 Issue 勾除；
- [ ] N-09 / N-21 按技能内的四条成熟判据逐条核对。

## 关联

- `Docs/Design/DesktopNativeDesign.md`、`Docs/Design/CliNativeDesign.md`、`.agents/skills/`
- 相关已完成项：#35 / #37 / #39 / #41 / #46 / #63 / #65（均已关闭）
- 长期跟踪：#48（字体渲染）、#49（暗色适配）

