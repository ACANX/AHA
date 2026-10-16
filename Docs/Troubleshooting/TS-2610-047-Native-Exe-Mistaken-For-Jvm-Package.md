# TS-2610-047 原生镜像的 exe 把自己认成 JVM 便携包，点升级去下了 JVM 包

> 编号：`TS-2610-047`
> 日期：2026-10-16
> 关联 Issue / PR：真机自测发现；修复落在 PR [#243](https://github.com/ACANX/AHA/pull/243)（合并前）；
> 被修掉的另一半（原生自更新本身）跟踪 [#247](https://github.com/ACANX/AHA/issues/247) / TD-00022
> 状态：**已修**（形态判定 + 能力门 + `prepareSwap` 兜底；真机复验项见 §5）

## 1. 现象

桌面端**原生镜像**包（`AhaDesktopNative.exe`）在设置面板里点「检查更新」：列出来的版本列表是
**JVM 便携包**；点「升级」后会去下那个 JVM 包 —— 而用户手里的 **exe 一个字节都不会变**。

触发条件很关键：**安装根里同时存在两种形态的文件**（用户先前把 JVM 快照取到 `Dist\`，
后来又在那儿放了/取了原生 exe）——也就是「同目录既有 `aha-desktop.jar`，又有 `AhaDesktopNative.exe`」。

## 2. 排查过程（含取证）

1. 读形态判定：`InstallLayout.detect()` 的注释写着「JVM 便携包 = code source 指向那个 jar；
   拿不到（**native-image 下为 null**）再退回进程自身的可执行文件路径」。⇒ 先怀疑这条前提。
2. 该前提**不成立**：原生镜像里 `getProtectionDomain().getCodeSource()` **不是 null**，
   它指向**运行中的 exe 自己**（`file:/…/AhaDesktopNative.exe`）。
3. 于是 `Files.isRegularFile(codeSource)` 为真 ⇒ `jarBased = true`：exe 把自己认成了 JVM 便携包。
4. 为什么没被能力门拦住：当时的门只看「安装根里有没有启动脚本 / 原生单文件」。
   而触发条件恰恰是**两者都有**（jar 有、exe 有、启动脚本也有）⇒ 门放行。
5. 为什么「下错包」是必然：形态决定资产前缀（JVM 线 `AHA-Desktop-JVM-…` / 原生线
   `AHA-Desktop-Native-…`）⇒ 形态判错，资产就一定挑错。

## 3. 根因

**判定依据选错**：用「code source 是不是普通文件」代替了「code source 是不是 **`.jar`**」。
原生镜像恰好满足前者（指向 exe）而不满足后者 —— 一个只在原生镜像里出现的差集。

## 4. 修复

| 层 | 改动 |
|---|---|
| 形态判定 | `InstallLayout`：① 先看 GraalVM 运行期属性 `org.graalvm.nativeimage.imagecode`（非空 = 在原生镜像里，直接按「进程自身的可执行文件」判）；② `of(Path)` 只把 **`.jar`** 文件认成 JVM 便携包 |
| 能力门 | `UpdateLauncher.unsupportedReason(layout, nativeImage)` 新增**原生镜像**一条，且**排在平台那条之前**（原生这条更具体：在 Linux/macOS 上不该只被告知「只在 Windows 上实现」）—— 界面 / CLI 在**下载前**置灰并说明「原生形态的应用内自更新尚未实现，请到发布页取原生包手动覆盖」 |
| 兜底 | `UpdateService.prepareSwap` 加同一道门（只挡原生：POSIX 的平台能力不在这里判，`prepareSwap` 在非 Windows 上仍有单测在跑） |
| 顺带 | 修掉 `restartCommand()` 里过时的注释（`java -jar` 兜底早已删除，注释还留着） |

## 5. 验证

- 本地真 JUnit `com.acanx.module.aha.core.update.*`：**158/158**（新增 4 条：
  `executableCodeSourceIsNotJarBased`、`detectTreatsANativeImageAsTheExeForm`、
  `unsupportedReasonBlocksNativeImagesOutright`、`prepareSwapRefusesWhileRunningInsideANativeImage`）；
- 桌面模块全量 javac（含测试源码）✅；五道静态门禁 ✅；
- **真机复验（ACANX）**：在 `Dist\`（同时放着 exe 与 jar）双击 `AhaDesktopNative.exe` →
  设置面板里「检查更新 / 升级」应**置灰**并给出「原生镜像的应用内自更新尚未实现…手动覆盖」的说明，
  **不再下载 JVM 包**。

## 6. 教训与沉淀

1. **「拿不到」「是 null」这类前提要当场验，不能写进注释就当事实**：原注释把「native-image 下 code source 为 null」
   当已知条件，实际是错的 —— 判据本身错了，后面所有推导都跟着错。
2. **判定要用「最窄的那个特征」**：判断「是不是 jar 形态」就直接判 `.jar`，不要用「是不是文件」这种**更宽**的近似 ——
   近似条件会在某个环境下悄悄为真（这里就是原生镜像）。
3. **「两者都存在」的机器最容易踩形态类坑**：凡是「按形态选资产 / 按形态换装」的逻辑，
   都要在「同目录两种形态并存」下验一次（用 `Dist\` 这种混放目录最省事）。
4. 能力门的**文案要指出下一步**：这次不只是拦住，还写清「去发布页取对应平台的原生包手动覆盖」，
   并把「真正实现原生自更新」立成 [#247](https://github.com/ACANX/AHA/issues/247) / TD-00022 ——
   拦住不等于解决，别让用户以为「这功能就是没有了」。

## 结论分层

### 已观测事实

- ACANX 真机自述（2026-10-16）：在「`Dist\` 里既放/取过 JVM 包、又放了原生 exe」的环境下，
  点「检查更新」列出的是 **JVM 线资产**；点升级会去下 JVM 包，而手里的 exe 一个字节没变。
- 源码事实：形态由 `InstallLayout#jarBased` 决定；资产前缀由形态决定；当时的判定是
  「code source 指向的文件**存在**即为 JVM 便携包」；当时的形态门只看「安装根里有没有启动脚本 /
  原生单文件」。
- 本机复现（单测，非真机）：`InstallLayout.of(<目录>/AhaDesktopNative.exe).jarBased()`
  修复前为 `true`、修复后为 `false`。

### 机制 / 行为模型

1. 原生镜像运行时，`InstallLayout.class.getProtectionDomain().getCodeSource()` **不为 null**，
   其 location 指向**运行中的 exe 自身** —— **这一条属推断**（GraalVM 的已知行为，
   本机没有原生 exe 可取证，见「未定位 / 未复现」），但修复**不依赖它**：
   `org.graalvm.nativeimage.imagecode` 这条判据独立成立。
2. 由 1 得 `Files.isRegularFile(codeSource) == true` ⇒ `jarBased = true`（exe 被认成 JVM 便携包）。
3. 形态 → 资产前缀（JVM 线 `AHA-Desktop-JVM-…` / 原生线 `AHA-Desktop-Native-…`）⇒ 形态判错
   ⇒ 资产必然挑错（这一步纯代码链路，可读）。
4. 形态门之所以放行：触发环境是「**两种形态的文件并存**」（jar 有、exe 有、启动脚本也有），
   门只看「有没有」，因此看不出形态判错。

### 未定位 / 未复现

- **本机无该环境**：没有 GraalVM 原生 exe 可运行取证，§1 里「code source 指向 exe 自己」是**推断**
  （依据是 GraalVM 的公开行为），不是本机实测值；
- **未被真机复验**：修复后的界面表现（置灰 + 文案）需 ACANX 在原生包上复验（见 §5）；
- **原生自更新本身未实现**（功能空缺，不是 bug）：见 [#247](https://github.com/ACANX/AHA/issues/247) / TD-00022。

## 观测数据

| 观测 | 值 |
|---|---|
| 触发环境 | 安装根（`Dist\`）里同时有 `aha-desktop.jar`（JVM 包）与 `AhaDesktopNative.exe`（原生包） |
| 用户看到的列表 | JVM 便携包资产（而非原生包） |
| 本机复现（单测，非真机） | `InstallLayout.of(<目录>/AhaDesktopNative.exe).jarBased() == false`（修复后）；修复前该输入会得到 `true` |

## 排除项

- **不是资产选择器 / 通道契约写错**：两者都按「形态 → 资产前缀」工作，形态判错它们只会忠实地挑错包；
- **不是「原生包没被发布」**：原生线确实出包（`V<版本>-AHA-Desktop-Native-Image`），
  只是它的 tag 形态**不在升级通道的过滤范围内** ⇒ 哪怕形态判对，原生形态也**没有可升的资产** ——
  这正是「必须拦住而不是硬升」的原因之一（另一半见 [#247](https://github.com/ACANX/AHA/issues/247)）；
- **不是「换了文件但没重启」**：这次根本走不到换装 —— 下的是 JVM 包，与安装根里已有的 jar 同族，
  换装只会替换那些 JVM 文件，exe 自然没变（用户的观测与此一致）。

## 未决问题

1. **真机取证**：ACANX 复验时若想坐实 §结论分层里那条推断，可在原生包里跑一次
   `-Dorg.graalvm.nativeimage.imagecode`（或打印 `InstallLayout` 的判定结果）留档；修复本身不依赖它；
2. **原生形态的应用内自更新**：拦住只是「不再误导」，功能本身见 [#247](https://github.com/ACANX/AHA/issues/247)（TD-00022）——
   需要先定「原生包如何进入升级通道」，再验「替换正在使用的 exe + 回滚」；
3. **是否需要把「同目录两种形态并存」做成显式告警**：现在靠形态判定取其一（正确），但用户可能自己都忘了
   那个目录里有第二形态的文件；要不要在界面上提示一次，待定。

## 7. 涉及文件

| 文件 | 说明 |
|---|---|
| `aha-core/src/main/java/com/acanx/module/aha/core/update/InstallLayout.java` | 形态判定（属性 + `.jar` 要求） |
| `aha-core/src/main/java/com/acanx/module/aha/core/update/UpdateLauncher.java` | 能力门新增原生一条，顺序在平台之前 |
| `aha-core/src/main/java/com/acanx/module/aha/core/update/UpdateService.java` | `prepareSwap` 兜底 |
| `aha-core/src/test/java/com/acanx/module/aha/core/update/{InstallLayoutTest,UpdateLauncherTest,UpdateServiceTest}.java` | 4 条新用例 |
| `Docs/Design/DesktopAutoUpdateDesign.md` / `Docs/Guide/AppUpdateGuide.md` / `CHANGELOG.md` | 契约与用户可见说明 |
