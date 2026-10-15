# 问题排查记录（Troubleshooting）

**文档版本**：v1.2.0
**状态**：生效
**生效日期**：2026-10-09
**最后更新**：2026-10-13
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 0. 本目录的定位

记录**问题排查 / 缺陷定位 / 事故复盘**类事项：现象是什么、怎么查的、根因是什么、
怎么修的、最终验证结果、留下了什么教训。

与相邻目录的分工：

| 目录 / 载体 | 记什么 |
|---|---|
| `Docs/Troubleshooting/`（本目录） | **问题排查与事故复盘**：现象 → 排查过程 → 根因 → 修复 → 验证 → 教训 |
| `Docs/DevLog/` | **开发完成的事项**：做了什么、为什么做、如何验证、影响面（不写排查过程） |
| GitHub Issue | 需要长期跟踪的遗留问题（含验收标准）；`Troubleshooting` 记录可反向引用 Issue |
| `Docs/TODO/README.md` | 待办清单与进度看板 |

> 简记：**排查进 Troubleshooting，做完的功能进 DevLog，没闭环的进 Issue，待办看 TODO。**

### 0.1 何时记录

- 排查完一个可复现的问题（含事故）后；
- 结论与「本来以为的原因」不一致、值得留下取证过程时；
- **同一个坑第二次踩到时——必须补一篇**，否则还会第三次；
- 历史记录需要从 `Docs/DevLog/` 归入本目录时，按第 1 节命名并登入索引。

不记录的情形：一眼可改的笔误、本轮内顺手修掉且无留痕价值的小问题（写在开发日志的
「遇到的问题」表里即可）。

## 1. 文件命名

```
TS-<yyMM>-<三位序号>-<英文词序>.md
```

| 片段 | 规则 | 示例 |
|---|---|---|
| `TS` | 固定前缀（Troubleshooting） | `TS` |
| `yyMM` | 事件发生的**年月**（本地时间） | `2610` = 2026 年 10 月 |
| `三位序号` | 同期间内的**顺序号**，按登记时间递增（不复用、不跳号） | `001`、`002` |
| `英文词序` | 标题的**英文词序**，**单词之间用 `-` 连接** | `CI-Required-Check-Name-Mismatch` |

完整示例：

```
TS-2610-001-CI-Required-Check-Name-Mismatch.md
TS-2610-025-Theme-Not-Following-System.md
TS-2610-030-Native-Font-Rendering-Diff.md
```

> 词序按「**对象 → 现象 → 处置 / 范围**」排；缩写（`CI` / `PR` / `JNI` / `CSS` / `JVM` /
> `LCD` …）**整体大写**，产品名按原厂写法（如 `GraalVM`、`JavaFX`）；**不用下划线、不连写、
> 不出中文**。标题要求「见名知意」，不要用 `Issue37`、`Bug1` 这类无法检索的名字。
> 编号在重命名时**按原索引登记顺序重新分配**，保持「编号即时间线」。

模板见 [TS-Template.md](TS-Template.md)。

## 2. 索引

| 文件 | 主题 | 日期 | 关联 Issue |
|---|---|---|---|
| [TS-2610-001-CI-Required-Check-Name-Mismatch.md](TS-2610-001-CI-Required-Check-Name-Mismatch.md) | CI 排障复盘：必需检查因矩阵作业名变更而永久挂起 | 2026-10-07 20:00 |  |
| [TS-2610-002-Coverage-Gate-Invisible.md](TS-2610-002-Coverage-Gate-Invisible.md) | CI 排障复盘：一个「看得见才敢信」的覆盖率门禁 | 2026-10-07 21:00 |  |
| [TS-2610-003-Fake-Merge-Dirty-PR.md](TS-2610-003-Fake-Merge-Dirty-PR.md) | 上游冲突排障：PR #8 永久 `dirty` 的根因是一次「假合并」 | 2026-10-07 22:00 | #8 |
| [TS-2610-004-Artifact-Not-Found-In-Central.md](TS-2610-004-Artifact-Not-Found-In-Central.md) | CI 排障复盘：`Could not find artifact ... in central`，而该 artifact 确实存在 | 2026-10-07 23:00 |  |
| [TS-2610-005-Ruleset-Blocks-Single-Maintainer.md](TS-2610-005-Ruleset-Blocks-Single-Maintainer.md) | PR 卡死复盘：规则集要求了「无人能批准」与「没人生产」的检查 | 2026-10-07 24:00 |  |
| [TS-2610-006-Version-Bump-Missed-Modules.md](TS-2610-006-Version-Bump-Missed-Modules.md) | 版本号切换的三处坑：只改根 POM 会静默产出旧版本 | 2026-10-08 00:00 |  |
| [TS-2610-007-Atomic-Replace-Silent-Failure.md](TS-2610-007-Atomic-Replace-Silent-Failure.md) | 桌面端桥接契约落地——原子替换静默失效等 5 个坑 | 2026-10-08 01:00 |  |
| [TS-2610-008-Provider-Config-Dialog-Traps.md](TS-2610-008-Provider-Config-Dialog-Traps.md) | 供应商配置改造——对话框主题、脚本污染真实配置等 4 个问题 | 2026-10-08 02:00 |  |
| [TS-2610-009-Desktop-Feature-Bugs.md](TS-2610-009-Desktop-Feature-Bugs.md) | 桌面端六功能开发——测试与真机抓到的 5 个真 bug | 2026-10-08 03:00 |  |
| [TS-2610-010-Native-Module-Silent-Traps.md](TS-2610-010-Native-Module-Silent-Traps.md) | 原生模块与工作流落地——5 个「不报错」的坑 | 2026-10-08 04:00 |  |
| [TS-2610-011-Maven-Retry-Ineffective.md](TS-2610-011-Maven-Retry-Ineffective.md) | CI「重试了 3 次却等于没重试」：jacoco 插件解析失败的真因 | 2026-10-08 05:00 |  |
| [TS-2610-012-Native-Tolerance-Step-Level.md](TS-2610-012-Native-Tolerance-Step-Level.md) | 原生镜像管线做成真「可选」：容错从作业级下沉到步骤级 | 2026-10-08 06:00 |  |
| [TS-2610-013-PR-15-Stale-Local-Ref.md](TS-2610-013-PR-15-Stale-Local-Ref.md) | 「PR #15 合并存在问题」的排查：没有冲突，是本地引用陈旧 | 2026-10-08 07:00 | #15 |
| [TS-2610-014-Native-Skip-Silent-Override.md](TS-2610-014-Native-Skip-Silent-Override.md) | 「CI 全绿但零产物」：native.skip 被模块自身的属性静默压掉 | 2026-10-08 08:00 |  |
| [TS-2610-015-Native-Image-Args-Tuning.md](TS-2610-015-Native-Image-Args-Tuning.md) | 按首次真编日志调 native-image 参数：9 条警告与 5 条建议逐条处置 | 2026-10-08 09:00 |  |
| [TS-2610-016-Native-Report-Separate-Zip.md](TS-2610-016-Native-Report-Separate-Zip.md) | 修复 issue #29：原生镜像的构建报告改为独立发布包 | 2026-10-08 10:00 | #29 |
| [TS-2610-017-Native-Main-Class-Not-Found.md](TS-2610-017-Native-Main-Class-Not-Found.md) | 修复 issue #26：native 桌面端启动即 `ClassNotFoundException`（JavaFX 入口的两处反射） | 2026-10-08 11:00 | #26 |
| [TS-2610-018-Cross-Platform-Shell-Traps.md](TS-2610-018-Cross-Platform-Shell-Traps.md) | 修复 run 37744912968：macOS 丢镜像包 / Windows 工具链自证退出（跨平台 shell 两个坑） | 2026-10-08 12:00 |  |
| [TS-2610-019-Quantum-Toolkit-Missing.md](TS-2610-019-Quantum-Toolkit-Missing.md) | 修复 issue #35：native 桌面端 `ClassNotFoundException: com.sun.javafx.tk.quantum.QuantumToolkit` | 2026-10-08 13:00 | #35 |
| [TS-2610-020-CLI-Native-Module-Setup.md](TS-2610-020-CLI-Native-Module-Setup.md) | 建立 CLI 原生镜像模块（`aha-cli-native` + `CliNative.yml`） | 2026-10-08 14:00 |  |
| [TS-2610-021-JNI-Find-Class-Segfault.md](TS-2610-021-JNI-Find-Class-Segfault.md) | 原生桌面端 segfault —— JNI `FindClass` 未注册（issue #37） | 2026-10-08 15:00 | #37 |
| [TS-2610-022-Win-Window-JNI-Member-Missing.md](TS-2610-022-Win-Window-JNI-Member-Missing.md) | 原生桌面端 Windows 启动崩溃 —— 平台子类的 JNI 成员查找未登记（issue #39） | 2026-10-09 05:00 | #39 |
| [TS-2610-023-Prism-Effect-Peer-Missing.md](TS-2610-023-Prism-Effect-Peer-Missing.md) | 原生桌面端能开窗但控件画不出 —— Prism 效果 peer 的动态类名未登记（issue #41） | 2026-10-09 06:00 | #41 |
| [TS-2610-024-Launcher-Missing-Lib-On-Source-Root.md](TS-2610-024-Launcher-Missing-Lib-On-Source-Root.md) | 桌面端启动脚本在源码根误报「找不到 lib」 | 2026-10-09 07:00 |  |
| [TS-2610-025-Theme-Not-Following-System.md](TS-2610-025-Theme-Not-Following-System.md) | 原生桌面端主题不跟随系统、切换残留与暗色标题对比度 | 2026-10-09 08:00 |  |
| [TS-2610-026-Dark-Mode-Control-Colors.md](TS-2610-026-Dark-Mode-Control-Colors.md) | 暗色模式下控件背景与文字未适配（issue #49） | 2026-10-09 09:00 | #49 |
| [TS-2610-027-Build-Version-Pre-Release.md](TS-2610-027-Build-Version-Pre-Release.md) | 让预发行版显示带构建号的版本（issue #46） | 2026-10-09 10:00 | #46 |
| [TS-2610-028-Theme-Lookup-Table-Failure.md](TS-2610-028-Theme-Lookup-Table-Failure.md) | 原生桌面端主题残留：把隐式查表改成显式声明（#49） | 2026-10-09 11:00 | #49 |
| [TS-2610-029-Modena-CSS-Function-Failure.md](TS-2610-029-Modena-CSS-Function-Failure.md) | 原生镜像下控件背景发黑（modena 的 derive / linear-gradient 失效） | 2026-10-09 12:00 |  |
| [TS-2610-030-Native-Font-Rendering-Diff.md](TS-2610-030-Native-Font-Rendering-Diff.md) | issue #48 调研——原生镜像的字体渲染差异 | 2026-10-09 13:00 | #48 |
| [TS-2610-031-Dark-Text-CSS-Patch-Failure.md](TS-2610-031-Dark-Text-CSS-Patch-Failure.md) | 暗色下文字仍是黑色——CSS 补丁表不能再查表 | 2026-10-09 14:00 |  |
| [TS-2610-032-FXML-Missing-Import-And-Smoke-Blind-Spot.md](TS-2610-032-FXML-Missing-Import-And-Smoke-Blind-Spot.md) | FXML 忘了写 import：原生包启动即崩，而构建与冒烟都判了「通过」 | 2026-10-13 13:00 | 见 issue #133 |
| [TS-2610-033-Sibling-Metadata-Gap-Not-Reproducible.md](TS-2610-033-Sibling-Metadata-Gap-Not-Reproducible.md) | 姊妹工程复现的同族缺口，在本仓库不成立：查找在 native 侧、jni 早已登记（issue #125） | 2026-10-13 19:00 | 见 issue #125 |
| [TS-2610-034-Prism-D3d-Driver-Info-Zero-Values.md](TS-2610-034-Prism-D3d-Driver-Info-Zero-Values.md) | 原生包 OS / 驱动信息全零：native 按名回写字段，而字段没登记（issue #60） | 2026-10-13 20:30 | 见 issue #60 |
| [TS-2610-035-Theme-Content-Surface-Color-Roles.md](TS-2610-035-Theme-Content-Surface-Color-Roles.md) | 暗色下内容面「亮一档」被放大感知：按角色拆分底色（issue #153） | 2026-10-13 21:00 | 见 issue #153 |
| [TS-2610-036-Windows-Python-OpenSSL-Applink-Failure.md](TS-2610-036-Windows-Python-OpenSSL-Applink-Failure.md) | Windows 上 Python 的 HTTPS 硬失败：`no OPENSSL_Applink`（一键入口的 curl 兜底） | 2026-10-13 22:00 | 见 issue #156 |
| [TS-2610-037-Windows-Exe-Manifest-For-OS-Version.md](TS-2610-037-Windows-Exe-Manifest-For-OS-Version.md) | 原生桌面 exe 的 `OS Information:` 报 `Windows 8.0 build 9200`（Win10 兼容清单；链接器工作目录之坑） | 2026-10-14 10:00 | 见 issue #151 |
| [TS-2610-038-Theme-Switch-Stale-Inline-Colors.md](TS-2610-038-Theme-Switch-Stale-Inline-Colors.md) | 切主题后输入框 / 对话框组件的颜色不跟着变（静态常量冻色 + 未登记重刷 + 场景未换表） | 2026-10-14 12:00 | 见 issue #153 |
| [TS-2610-039-Artifact-Anonymous-Download-401.md](TS-2610-039-Artifact-Anonymous-Download-401.md) | 载体检证：Actions 制品匿名下载 `401` / 网页链接 `307→404`，改用发布页 pre-release 资产（#165 方案 §七 的取舍依据） |
| [TS-2610-040-Trigger-Level-Paths-Filter-Blocks-Explicit-Requests.md](TS-2610-040-Trigger-Level-Paths-Filter-Blocks-Explicit-Requests.md) | 打快照标签却毫无反应：`on.pull_request.paths` 在**触发层**把「只改文档 + 新打标签」的显式请求滤掉（原生线零运行）⇒ 改为作业层门控 + 新增守卫 |
| [TS-2610-041-Snapshot-Explicit-Request-Cancelled-By-Auto-Run.md](TS-2610-041-Snapshot-Explicit-Request-Cancelled-By-Auto-Run.md) | 复验 #204 仍不出包：`labeled` 与 `synchronize` 同并发组时后者用 `cancel-in-progress` 取消前者（显式请求被吞）；另发现 base 更新后 merge ref 未重建会照旧 `paths:` 过滤、`plan` 在 `push` 上恒报错 | 2026-10-15 11:00 | 见 issue #165 |

## 3. 维护规则

- 新增排查记录：复制 `TS-Template.md`，按第 1 节命名，并在上方索引表登记一行；
- 记录里必须写明**取证**（命令与输出、要求与实际的逐条对照），不写「感觉」「大概」；
- 问题若牵出未闭环的遗留项，**另开 Issue**（见 `Docs/TODO/README.md`），记录里只引用编号；
- 结论变化时更新原文并标注日期，不新开重复文件。
