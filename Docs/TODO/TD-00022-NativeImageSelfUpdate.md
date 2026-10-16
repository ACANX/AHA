# TD-00022 原生镜像（AhaDesktopNative）的应用内自更新

> 待办编号：TD-00022
> 标题：原生形态的应用内升级：机制已打通，剩**真机验收**与边界收尾
> 状态：⏳ 待真机确认（**触发条件**：ACANX 在真机上跑一次「原生包换装」）
> 跟踪 Issue 状态：**CLOSED**（[#247](https://github.com/ACANX/AHA/issues/247) 已随 PR #243 交付后关闭）
> 活跃载体：[#122](https://github.com/ACANX/AHA/issues/122)（native 产物真机验证，唯一剩余项已登记在那里）
> 创建日期：2026-10-16
> 最后更新：2026-10-16（同日**重新定级**：从「未实现」改为「机制已在，缺真机验收」；#247 关闭后把待验项转入 #122）

---

## 来源与更正

- 2026-10-16 真机：原生 exe 被**认成 JVM 便携包** ⇒ 点升级去下 JVM 包、exe 没变。
  该缺陷已修（形态判定 + 排障档案 [`TS-2610-047`](../Troubleshooting/TS-2610-047-Native-Exe-Mistaken-For-Jvm-Package.md)）；
- **同日更正**：我当时据此又加了一层「原生形态不支持自更新」的门 —— **这是误判**。
  逐条实测后确认原生线**本来就具备**升级所需的全部机制（见下表），那层门拦掉的是用户本来就能用的功能，
  表现是「升级按钮永远是灰的」。门已撤，正向用例已补（`prepareSwapPlansToReplaceTheNativeExecutable`）。

## 现状（逐层核对过）

| 层 | 状态 |
|---|---|
| 通道契约 | ✅ 原生线 tag `V<版本>-aha-desktop-native`、资产 `AHA-Desktop-Native-<版本>-<平台>-jdk<JDK>.zip` 都在 `ChannelContract`；`supports()` 仅排除「原生包 + **Stable** 通道」（原生线还没发正式版）|
| 资产选择 | ✅ `AssetSelector` 按「形态 + 平台」选，原生走 jdk25 默认变体 |
| 形态判定 | ✅ `InstallLayout` 双保险（GraalVM 运行期属性 + code source 必须是 `.jar`）|
| 包内容 | ✅ `dist-native.xml` 收 exe / README / 参数 / LICENSE / **两个 helper**；`DesktopNative.yml` 已生成 `AHA-PACKAGE.json` 与 `SHA256SUMS`（#243）|
| 换装计划 | ✅ `SwapPlan.actions` 对新清单每个路径发 REPLACE；装清单缺失（旧安装）按「只新增」处理 |
| 重启命令 | ✅ `restartCommand()` 非 jar 形态返回 `aha-desktop-native[.exe]`（绝对路径，过计划校验）|
| 能力门 | ✅ 只剩「平台」与「安装形态」两层（原生不再被拦）|
| **真机验收** | ❌ **未做**：换装替换的是**正在运行的 exe** —— helper 等 App 退出后动手、再拉起新 exe，这条链路只有真机能验 |
| Stable 通道 | ❌ 原生线尚未发正式版 ⇒ `Stable` 通道里没有原生资产（此时界面给的是「该通道目前没有 X 的包」，属数据形态，不是 bug）|

## 要做的事

- [ ] **真机验收（ACANX）**：原生包上走一次「检查更新 → 选中版本 → 升级 → 自动重启」，确认重启后的 `version` 是新版本、`Dist\` 里的 exe 已被替换、旧 exe 有备份；顺带看被占用 / 杀软介入时的表现 —— **已登记到 [#122](https://github.com/ACANX/AHA/issues/122)**（判据与「为什么只能真机验」都在那条评论里）；
- [ ] 若验收通过：把这层结论写回 `DesktopAutoUpdateDesign.md` §5.4 / `DesktopNativeDesign.md` R14，并把本单收口；
- [ ] 若验收失败：按现象定位（helper 等待时机 / exe 替换失败 / 重启命令），修完再验；
- [ ] 排期决定：**原生线何时进 Stable 通道**（决定原生包用户在默认通道能否升到正式版）—— 与 [#252](https://github.com/ACANX/AHA/issues/252)（TD-00019）同批决策。

## 关联

- 上游边界：`DesktopAutoUpdateDesign.md` §1（不做形态切换）、§5.4（能力门与通道限制）、§2.3（形态判定）
- 排障：`TS-2610-047`（含同日「更正」一节）
- 同族待办：TD-00020（POSIX 换装 helper，[#250](https://github.com/ACANX/AHA/issues/250)）、TD-00021（回滚入口，[#251](https://github.com/ACANX/AHA/issues/251)）
