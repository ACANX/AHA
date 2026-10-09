# 问题排查记录（Troubleshooting）

**文档版本**：v1.1.0
**状态**：生效
**生效日期**：2026-10-09
**最后更新**：2026-10-09
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

## 1. 文件命名

```
TS-yyyyMM-大驼峰英文标题.md
```

| 片段 | 规则 | 示例 |
|---|---|---|
| `TS` | 固定前缀（Troubleshooting） | `TS` |
| `yyyyMM` | 事件发生的**年月**（本地时间） | `202610` |
| 标题 | **英文大驼峰**（PascalCase），概括问题，可含 2~5 个词 | `CiRequiredCheckHang` |

完整示例：

```
TS-202610-CiRequiredCheckHang.md
TS-202610-GraalvmNativeSilentSkip.md
TS-202610-NativeJniClassNotFound.md
```

> 同一月份内多篇按标题字母序排列即可，**不加序号后缀**；标题要求「见名知意」，
> 不要用 `Issue37`、`Bug1` 这类无法检索的名字。

模板见 [TS-Template.md](TS-Template.md)。

## 2. 索引

| 文件 | 主题 | 日期 | 关联 Issue |
|---|---|---|---|
| [TS-202610-CiRequiredCheckNameMismatch.md](TS-202610-CiRequiredCheckNameMismatch.md) | CI 排障复盘：必需检查因矩阵作业名变更而永久挂起 | 2026-10-07 20:00 |  |
| [TS-202610-CoverageGateInvisible.md](TS-202610-CoverageGateInvisible.md) | CI 排障复盘：一个「看得见才敢信」的覆盖率门禁 | 2026-10-07 21:00 |  |
| [TS-202610-FakeMergeDirtyPr.md](TS-202610-FakeMergeDirtyPr.md) | 上游冲突排障：PR #8 永久 `dirty` 的根因是一次「假合并」 | 2026-10-07 22:00 | #8 |
| [TS-202610-ArtifactNotFoundInCentral.md](TS-202610-ArtifactNotFoundInCentral.md) | CI 排障复盘：`Could not find artifact ... in central`，而该 artifact 确实存在 | 2026-10-07 23:00 |  |
| [TS-202610-RulesetBlocksSingleMaintainer.md](TS-202610-RulesetBlocksSingleMaintainer.md) | PR 卡死复盘：规则集要求了「无人能批准」与「没人生产」的检查 | 2026-10-07 24:00 |  |
| [TS-202610-VersionBumpMissedModules.md](TS-202610-VersionBumpMissedModules.md) | 版本号切换的三处坑：只改根 POM 会静默产出旧版本 | 2026-10-08 00:00 |  |
| [TS-202610-AtomicReplaceSilentFailure.md](TS-202610-AtomicReplaceSilentFailure.md) | 桌面端桥接契约落地——原子替换静默失效等 5 个坑 | 2026-10-08 01:00 |  |
| [TS-202610-ProviderConfigDialogTraps.md](TS-202610-ProviderConfigDialogTraps.md) | 供应商配置改造——对话框主题、脚本污染真实配置等 4 个问题 | 2026-10-08 02:00 |  |
| [TS-202610-DesktopFeatureBugs.md](TS-202610-DesktopFeatureBugs.md) | 桌面端六功能开发——测试与真机抓到的 5 个真 bug | 2026-10-08 03:00 |  |
| [TS-202610-NativeModuleSilentTraps.md](TS-202610-NativeModuleSilentTraps.md) | 原生模块与工作流落地——5 个「不报错」的坑 | 2026-10-08 04:00 |  |
| [TS-202610-MavenRetryIneffective.md](TS-202610-MavenRetryIneffective.md) | CI「重试了 3 次却等于没重试」：jacoco 插件解析失败的真因 | 2026-10-08 05:00 |  |
| [TS-202610-NativeToleranceStepLevel.md](TS-202610-NativeToleranceStepLevel.md) | 原生镜像管线做成真「可选」：容错从作业级下沉到步骤级 | 2026-10-08 06:00 |  |
| [TS-202610-Pr15StaleLocalRef.md](TS-202610-Pr15StaleLocalRef.md) | 「PR #15 合并存在问题」的排查：没有冲突，是本地引用陈旧 | 2026-10-08 07:00 | #15 |
| [TS-202610-NativeSkipSilentOverride.md](TS-202610-NativeSkipSilentOverride.md) | 「CI 全绿但零产物」：native.skip 被模块自身的属性静默压掉 | 2026-10-08 08:00 |  |
| [TS-202610-NativeImageArgsTuning.md](TS-202610-NativeImageArgsTuning.md) | 按首次真编日志调 native-image 参数：9 条警告与 5 条建议逐条处置 | 2026-10-08 09:00 |  |
| [TS-202610-NativeReportSeparateZip.md](TS-202610-NativeReportSeparateZip.md) | 修复 issue #29：原生镜像的构建报告改为独立发布包 | 2026-10-08 10:00 | #29 |
| [TS-202610-NativeMainClassNotFound.md](TS-202610-NativeMainClassNotFound.md) | 修复 issue #26：native 桌面端启动即 `ClassNotFoundException`（JavaFX 入口的两处反射） | 2026-10-08 11:00 | #26 |
| [TS-202610-CrossPlatformShellTraps.md](TS-202610-CrossPlatformShellTraps.md) | 修复 run 37744912968：macOS 丢镜像包 / Windows 工具链自证退出（跨平台 shell 两个坑） | 2026-10-08 12:00 |  |
| [TS-202610-QuantumToolkitMissing.md](TS-202610-QuantumToolkitMissing.md) | 修复 issue #35：native 桌面端 `ClassNotFoundException: com.sun.javafx.tk.quantum.QuantumToolkit` | 2026-10-08 13:00 | #35 |
| [TS-202610-CliNativeModuleSetup.md](TS-202610-CliNativeModuleSetup.md) | 建立 CLI 原生镜像模块（`aha-cli-native` + `CliNative.yml`） | 2026-10-08 14:00 |  |
| [TS-202610-JniFindClassSegfault.md](TS-202610-JniFindClassSegfault.md) | 原生桌面端 segfault —— JNI `FindClass` 未注册（issue #37） | 2026-10-08 15:00 | #37 |
| [TS-202610-WinWindowJniMemberMissing.md](TS-202610-WinWindowJniMemberMissing.md) | 原生桌面端 Windows 启动崩溃 —— 平台子类的 JNI 成员查找未登记（issue #39） | 2026-10-09 05:00 | #39 |
| [TS-202610-PrismEffectPeerMissing.md](TS-202610-PrismEffectPeerMissing.md) | 原生桌面端能开窗但控件画不出 —— Prism 效果 peer 的动态类名未登记（issue #41） | 2026-10-09 06:00 | #41 |
| [TS-202610-LauncherMissingLibOnSourceRoot.md](TS-202610-LauncherMissingLibOnSourceRoot.md) | 桌面端启动脚本在源码根误报「找不到 lib」 | 2026-10-09 07:00 |  |
| [TS-202610-ThemeNotFollowingSystem.md](TS-202610-ThemeNotFollowingSystem.md) | 原生桌面端主题不跟随系统、切换残留与暗色标题对比度 | 2026-10-09 08:00 |  |
| [TS-202610-DarkModeControlColors.md](TS-202610-DarkModeControlColors.md) | 暗色模式下控件背景与文字未适配（issue #49） | 2026-10-09 09:00 | #49 |
| [TS-202610-BuildVersionPreRelease.md](TS-202610-BuildVersionPreRelease.md) | 让预发行版显示带构建号的版本（issue #46） | 2026-10-09 10:00 | #46 |
| [TS-202610-ThemeLookupTableFailure.md](TS-202610-ThemeLookupTableFailure.md) | 原生桌面端主题残留：把隐式查表改成显式声明（#49） | 2026-10-09 11:00 | #49 |
| [TS-202610-ModenaCssFunctionFailure.md](TS-202610-ModenaCssFunctionFailure.md) | 原生镜像下控件背景发黑（modena 的 derive / linear-gradient 失效） | 2026-10-09 12:00 |  |
| [TS-202610-NativeFontRenderingDiff.md](TS-202610-NativeFontRenderingDiff.md) | issue #48 调研——原生镜像的字体渲染差异 | 2026-10-09 13:00 | #48 |
| [TS-202610-DarkTextCssPatchFailure.md](TS-202610-DarkTextCssPatchFailure.md) | 暗色下文字仍是黑色——CSS 补丁表不能再查表 | 2026-10-09 14:00 |  |

## 3. 维护规则

- 新增排查记录：复制 `TS-Template.md`，按第 1 节命名，并在上方索引表登记一行；
- 记录里必须写明**取证**（命令与输出、要求与实际的逐条对照），不写「感觉」「大概」；
- 问题若牵出未闭环的遗留项，**另开 Issue**（见 `Docs/TODO/README.md`），记录里只引用编号；
- 结论变化时更新原文并标注日期，不新开重复文件。
