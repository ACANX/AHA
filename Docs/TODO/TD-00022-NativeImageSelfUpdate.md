# TD-00022 原生镜像（AhaDesktopNative）的应用内自更新

> 待办编号：TD-00022
> 标题：让原生镜像形态也能在应用内升级（原生包进升级通道 + 替换 exe + 回滚 + 真机验）
> 状态：⏳ 待做（**触发条件**：ACANX 决定排期；或出现「原生包用户明显增多、手动覆盖成本变高」）
> 跟踪 Issue 状态：**OPEN**
> 跟踪 Issue：[#247](https://github.com/ACANX/AHA/issues/247)
> 创建日期：2026-10-16
> 最后更新：2026-10-16

---

## 来源

- 2026-10-16 真机自测（ACANX）：原生 exe 被**认成 JVM 便携包**，点升级去下了 JVM 包、exe 没变。
  该缺陷本身已在 PR #243 内修掉（形态判定 + 能力门 + `prepareSwap` 兜底，见
  [`TS-2610-047`](../Troubleshooting/TS-2610-047-Native-Exe-Mistaken-For-Jvm-Package.md)）；
- 但**功能空缺仍在**：原生形态现在被如实拦住并提示「到发布页取原生包手动覆盖」——
  拦住不是解决，故立此单。

## 现状（已实现的部分，别再重做）

| 层 | 状态 |
|---|---|
| 服务层（发现 / 挑资产 / 下载 / 校验 / 解包 / 计划 / 回执 / 启动收尾） | ✅ 与形态、平台无关 |
| 形态判定 | ✅ `InstallLayout` 已能正确区分「JVM 便携包」与「原生镜像」（GraalVM 属性 + `.jar` 要求） |
| 重启命令 | ✅ `restartCommand()` 在非 jar 形态下返回 `aha-desktop-native[.exe]`（绝对路径，过计划校验） |
| 换装 helper | ✅ 随原生包分发（`dist-native.xml` 已铺到包根）；helper 能替换任意文件并等 App 退出 |
| 能力门 | ✅ 已拦住原生形态并给出路（PR #243） |
| **原生包进升级通道** | ❌ 未定：通道过滤只认 `V<版本>` 形态 tag，而原生线是 `V<版本>-AHA-Desktop-Native-Image` |
| **替换正在使用的 exe** | ❌ 未验：Windows 上「正在运行的 exe 被替换 + 杀软介入 + 重启后回到新版本」全无实测 |
| **原生包的自证与回滚** | ❌ 未定：manifest（`AHA-PACKAGE.json`）里 exe 的 sha256、失败回滚到旧 exe 的路径 |

## 要做的事

- [ ] **发布流程**：决定原生包如何进入升级通道（新 tag 形态？独立通道？还是「原生线专用通道」），
      并把契约写进 `ChannelContract` / `ReleaseProcess.md`（同时保证 **JVM 形态看不到原生资产、反之亦然**）；
- [ ] **包内容**：原生包里需要哪些文件（exe / dll / README / 参数 / `AHA-PACKAGE.json`），
      生成侧（`.github/Python/PackageManifest.py`）与消费侧（`PackageManifest`）对齐；
- [ ] **换装与自证**：exe 的 sha256 校验、替换后回读版本号确认「真的跑起来了」；
- [ ] **回滚**：替换 exe 失败时回到旧 exe（`Update.BackupKeep` 语义、`SwapPlan.rollback` 复用）；
- [ ] **真机矩阵**：Windows（含杀软 / 文件占用）、Linux / macOS（与 TD-00020 的 POSIX helper 一起排）；
- [ ] **解除拦截**：能力门那条原生说明改为「已支持」，`AppUpdateGuide` / 设计文档同步。

## 关联

- 上游边界：`DesktopAutoUpdateDesign.md` §1（**不做形态切换**）、§2.3（形态判定）、§5.4（能力门）
- 同族待办：TD-00020（POSIX 换装 helper）、TD-00021（回滚入口）
- 原生线设计：`DesktopNativeDesign.md`（R13 反射登记 / R14 原生形态的更新现状）
