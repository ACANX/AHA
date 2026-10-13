# TS-2610-034 · 原生包 OS / 驱动信息全零：native 按名回写字段，而字段没登记

> 事件日期：2026-10-13
> 作者：@cnxnc
> 关联 Issue：[#60](https://github.com/ACANX/AHA/issues/60)（本条修复）；同族 #26 / #35 / #37 / #39 / #41 / #125
> 关联记录：`Docs/Design/DesktopNativeDesign.md` §6.1 `R11`、`TS-2610-021` / `TS-2610-022`（同为 native 按名访问 Java 成员）

## 1. 现象

原生桌面镜像启动时（`prism.verbose` 由 `AhaDesktopApp` 自己开启），**OS 与 D3D 驱动信息全是零值**，
但**管线与设备创建是成功的**：

```
Prism pipeline name = com.sun.prism.d3d.D3DPipeline
Direct3D initialization succeeded
D3DPipelineManager: Created D3D9Ex device
OS Information:
	Windows version 0.0 build 0
D3D Driver Information:
	Driver null, version 0.0.0.0
	Pixel Shader version 0.0
	Device : ven_0000, dev_0000, subsys_00000000
	Max Multisamples supported: 0
```

不报错、不影响窗口打开 ⇒ **静默**。issue #60 当时把它归到「运行期环境访问（注册表 / WMI / D3D 枚举）
在 closed-world 读不到」这一类，属**待查假设**。

## 2. 排查过程

| # | 动作 | 预期（若「读不到系统信息」成立） | 实际 | 结论 |
|---|---|---|---|---|
| 1 | 确认症状是否仍在 | — | 2026-10-13 两条原生构建（run `38389103313`、`38392225804`）**逐字相同** | 稳定复现，不是偶发 |
| 2 | 找这些值的产生路径 | 某处调注册表 / WMI / `GetVersionEx` | `javap -p -c com/sun/prism/d3d/D3DPipeline.class`：`printDriverInformation()` 只做一件事——new `D3DDriverInformation`，交给 **native** `nGetDriverInformation(int, D3DDriverInformation)` | 值来自 **native 回写**，不是 Java 侧读系统 |
| 3 | 看 native 回写什么 | — | `javap -p com/sun/prism/d3d/D3DDriverInformation.class`：全是**公有字段**（`osMajorVersion` / `osMinorVersion` / `osBuildNumber` / `driverName` / `vendorID` / `deviceID` / `subSysId` / `psVersionMajor` / `psVersionMinor` / `maxSamples`） | native 要按名填这些字段 |
| 4 | 确认 native 是按名访问 | — | `grep -rla osMajorVersion *.dll` → **命中 `prism_d3d.dll`**（`osBuildNumber` / `driverName` / `vendorID` / `psVersionMajor` / `maxSamples` / `nGetDriverInformation` 同样命中） | native 用 `GetFieldID` **按名**查字段 |
| 5 | 查登记 | 应该有 | `reachability-metadata.json`：**0 条**；`jni-config.json`：**0 条**（`D3DPipeline` 只登记了 `getInstance`） | **缺口定位** |
| 6 | 与输出对照（指纹） | — | `getOsVersion()` 按 `osMajorVersion` 分支拼接 + `osBuildNumber` ⇒ 全零时正好是 `Windows version 0.0 build 0`；`driverName=null` ⇒ `Driver null`；`vendorID/deviceID/subSysId=0` ⇒ `ven_0000, dev_0000, subsys_00000000` | **逐项吻合**，机制成立 |

**被排除的假设**见「排除项」一节。

## 3. 根因

一句话：**native 代码用 `GetFieldID` 按名回写 `D3DDriverInformation` 的字段，而该类和这些字段在元数据里一条都没登记**；
closed-world 下查不到成员 ID，字段就保持 Java 默认值（`0` / `null`）。

- **触发条件**：Windows 原生镜像 + `prism.verbose`（即 `D3DPipeline.printDriverInformation()` 被调用）；
- **机理**：native 拿 Java 侧传进来的对象（`GetObjectClass`），再按**字段名**查 ID 回写；
  未登记 ⇒ 拿不到 ID ⇒ 回写不发生。注意：**类能被加载 ≠ 它的字段能被 native 访问**，
  两者是两套登记（`objects/classes` 与 `fields/methods`）；
- **为什么没被及早发现**：失败表现为**值全零**而不是抛异常（native 侧拿不到 ID 并不让进程崩，
  窗口照开、渲染照跑），`prism.verbose` 只在排查时才看；而且 ⑧b 那份清单**整段漏掉了 `prism_d3d`**
  （清单是按 openjfx native 源码扫 `FindClass` / `GetMethodID` / `GetFieldID` 得来的，
  glass / font 路径覆盖到了，prism 的字段访问没覆盖）。

## 4. 修复

```json
// jni-config.json（85 → 86 条）
{
  "name": "com.sun.prism.d3d.D3DDriverInformation",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true,
  "allDeclaredFields": true
}
```

配套三道守卫：

| 守卫 | 位置 | 守什么 |
|---|---|---|
| `jniConfigRegistersPrismD3dDriverInformationFields` | `NativeImageMetadataTest`（15 → 16 条） | 该条目存在且含 `allDeclaredFields` |
| `require_jni "…D3DDriverInformation"` | `DesktopNative.yml` 步骤 ⑧b | **生效中**的 jni 清单里仍有它 |
| 冒烟断言（两条） | `DesktopNative.yml` Windows 腿 | ①必须打印 `OS Information:`；②**不得**出现 `Windows version 0.0 build 0` |

> **替代方案取舍**：也可以不改元数据，改为在 Java 侧显式调 `nGetMaxSampleSupport` /
> 自读系统信息——那要改产品代码路径、还得自己维护一套 Windows 版本/驱动读取，成本与风险都更高。
> **登记字段**是与 #37 / #39 完全一致的做法（那些也是 native 按名访问成员），口径统一。

## 5. 验证

- **反向验证（改前）**：改前该串**必然出现**——2026-10-13 两条原生构建的 Windows 腿日志都含
  `Windows version 0.0 build 0` ⇒ 这条断言**不是空过**；
- **正向验证（改后，已实测 2026-10-13）**：修复 PR [#150](https://github.com/ACANX/AHA/pull/150) 的 Windows 原生腿
  （job `115292084727`）冒烟 `✅ 通过`（`$ok` 要求 `OS Information:` 命中**且**全零串消失），stdout 变为：

  ```
  OS Information:
  	Windows 8.0 build 9200            ← 不再是 0.0 build 0（但仍是兼容性 shim，见 Q3）
  D3D Driver Information:
  	Microsoft Basic Render Driver
  	\\.\DISPLAY1
  	Driver d3d10warp.dll, version 10.0.26100.33438
  	Pixel Shader version 3.0
  	Device : ven_1414, dev_008C, subsys_00000000
  	Max Multisamples supported: 4
  ```

  驱动名 / 厂商 ID / PS 版本 / 多重采样**全部变成真实值**：`ven_1414` 是 **Microsoft**、
  `d3d10warp.dll` 是 **WARP 软件光栅化器** ⇒ 既印证了 runner 无硬件 GPU（与本档预判一致），
  也证明**驱动那一组字段的回写同样成功了**（不是只有 OS 那一组生效）；
- **回归**：`NativeImageMetadataTest` 随 `Build` / `Gate` 跑；
- **真机**：D3D **驱动名 / 厂商 ID / Pixel Shader / Max Multisamples** 这几项依赖真实 GPU，
  CI runner 是无 GPU 的虚拟机 ⇒ **需 ACANX 真机对照**（本机日志里这几项仍可能是零/`null`，不算未修好）。

## 6. 教训与沉淀

- **最该记住的一条**：**「值全零」是一种静默失败形态**——它比抛异常更难发现，
  因为进程不崩、界面照开。凡是「native 回写 Java 状态」的路径，都要问一句
  「**这些成员登记了吗**」（类能加载 ≠ 成员能被 native 访问）。
- **清单不完备**：⑧b 的清单按 native 源码扫描得出，**整段漏了 `prism_d3d.dll`**；
  同类审查应覆盖 `prism_d3d` / `prism_common` / `decora_sse`（已记入 `R11` 的应对列）。
- 落到：`jni-config.json`、`NativeImageMetadataTest`、`DesktopNative.yml`（⑧b + 冒烟断言）、
  `DesktopNativeDesign.md` §6.1 `R11`；
- **与 issue #48 的关系**：#48 的候选解释之一是「驱动能力探测全空 ⇒ 走保守分支 ⇒ 字形偏细」；
  修完本条后**可以**直接检验——若字体仍发虚，该候选被**排除**（详见 `TS-2610-030` 的关联段落）。

## 7. 涉及文件

| 类型 | 路径 |
|---|---|
| 元数据 | `aha-desktop/src/main/resources/META-INF/native-image/com.acanx.module.aha/aha-desktop/jni-config.json` |
| 测试 | `aha-desktop/src/test/java/com/acanx/module/aha/desktop/NativeImageMetadataTest.java` |
| 工作流 | `.github/workflows/DesktopNative.yml`（步骤 ⑧b + Windows GUI 冒烟） |
| 文档 | `Docs/Design/DesktopNativeDesign.md` §6.1、本文件、`Docs/Troubleshooting/README.md`、`CHANGELOG.md`、`Docs/DevLog/20261013-18.md` |
| 外部（只读） | `javafx-graphics-25-win.jar`（`javap`）、`prism_d3d.dll`（字段名字符串扫描） |

---

## 结论分层

### 已观测事实

- 原生包 `prism.verbose` 输出中 OS 与 D3D 驱动信息**全为零值 / null**，
  且 `Direct3D initialization succeeded` 同时成立（run `38389103313`、`38392225804` 两条 Windows 腿）。
- `D3DPipeline.printDriverInformation(int)` 调 **native** `nGetDriverInformation(int, D3DDriverInformation)`
  （`javap -p -c`）。
- `D3DDriverInformation` 的成员全是公有字段：`osMajorVersion`、`osMinorVersion`、`osBuildNumber`、
  `driverName`、`product`、`version`、`subVersion`、`buildID`、`psVersionMajor`、`psVersionMinor`、
  `maxSamples`、`vendorID`、`deviceID`、`subSysId`（`javap -p`）。
- `prism_d3d.dll` 里**含** `osMajorVersion` / `osBuildNumber` / `driverName` / `vendorID` /
  `psVersionMajor` / `maxSamples` / `nGetDriverInformation` 这些**名字**。
- 两份元数据里 `com.sun.prism.d3d.D3DDriverInformation` 及其字段：**各 0 条**（改前）。
- **修复后（PR [#150](https://github.com/ACANX/AHA/pull/150) 的 Windows 腿，job `115292084727`）**：
  driver / 厂商 / PS 版本 / 多重采样**全部变为真实值**；OS 版本不再是 `0.0 build 0`，
  但取到的是 `Windows 8.0 build 9200`（兼容性 shim，见 Q3）。
- 冒烟判定 `通过`（要求 `OS Information:` 命中**且**全零串消失 ⇒ 两条断言都真跑了）。

### 机制 / 行为模型

- native 侧按**字段名**取 ID（`GetFieldID`）→ 需要该字段在 jni 清单里可达；
  未登记时取不到 ID，**回写不发生**，字段保持 Java 默认值 `0` / `null`。
- 打印出来的字符串是对这些字段的**纯拼接**（`getOsVersion()` 按 `osMajorVersion` 分支 + `osBuildNumber`），
  故全零字段 ⇒ 全零输出——**与日志逐字吻合**（`Windows version 0.0 build 0`、
  `Driver null, version 0.0.0.0`、`ven_0000, dev_0000, subsys_00000000`、`Max Multisamples supported: 0`）。
- **推定（未实测）**：⑧b 清单漏掉 prism 段，是因为扫描只覆盖了 glass / font 路径；
  同族是否还有别的漏项，需按同一方法重扫邻接 dll（记入「未决问题 Q2」）。

### 未定位 / 未复现

- **未验证**：修复后的真实值——本档案写作时修复 PR 的 CI 尚未跑完（见「未决问题 Q1」）。
- **未验证（需真机）**：D3D **驱动名 / 厂商 ID / Pixel Shader / Max Multisamples** 在真机上的真实取值；
  CI runner 无真实 GPU，这几项**本来就可能为零**。
- **未定位**：native 取不到字段 ID 时的**具体行为**（是否抛异常被吞、还是直接跳过）——
  本次只确认「字段保持默认值」这一可观测结果。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| OS 信息（改前） | 原生包 stdout（run `38392225804` Windows 腿） | `Windows version 0.0 build 0` |
| 驱动信息（改前） | 同上 | `Driver null, version 0.0.0.0` / `Pixel Shader version 0.0` / `Device : ven_0000, dev_0000, subsys_00000000` / `Max Multisamples supported: 0` |
| 值的来源 | `javap -p -c com/sun/prism/d3d/D3DPipeline.class` | `invokestatic nGetDriverInformation:(ILcom/sun/prism/d3d/D3DDriverInformation;)...` |
| native 声明 | 同上 | `private static native com.sun.prism.d3d.D3DDriverInformation nGetDriverInformation(int, com.sun.prism.d3d.D3DDriverInformation)` |
| 字段名在 dll 里 | `grep -rla <字段名> --include=*.dll .` | `osMajorVersion` / `osBuildNumber` / `driverName` / `vendorID` / `psVersionMajor` / `maxSamples` → 均命中 **`prism_d3d.dll`** |
| 登记条数（改前） | `grep -c D3DDriverInformation <两份元数据>` | `reachability-metadata.json` = **0**；`jni-config.json` = **0** |
| jni 条数（改后） | `json.load(jni-config.json)` | **85 → 86** |
| 断言条数（改后） | `grep -c "@Test" NativeImageMetadataTest.java` | **15 → 16** |
| 输出全文（改前） | 原生包 stdout（run `38392225804`） | `Windows version 0.0 build 0` / `Driver null, version 0.0.0.0` / `ven_0000, dev_0000` / `Max Multisamples supported: 0` |
| 输出全文（改后） | 原生包 stdout（job `115292084727`） | `Windows 8.0 build 9200` / `Microsoft Basic Render Driver` / `Driver d3d10warp.dll, version 10.0.26100.33438` / `Pixel Shader version 3.0` / `ven_1414, dev_008C` / `Max Multisamples supported: 4` |
| 冒烟判定（改后） | 同上 | `✅ GUI 冒烟：通过：命中 [aha-version] 与 [ui-fxml-loaded]，且启动期未崩`（`$ok` 含「`OS Information:` 命中且无全零串」两个条件） |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「Java 侧读注册表 / WMI / `GetVersionEx` 失败」 | 值的唯一来源是 native `nGetDriverInformation`；Java 侧只做字符串拼接 |
| 「D3D 初始化本身失败」 | 同一份日志里 `Direct3D initialization succeeded`、`Created D3D9Ex device` |
| 「`D3DPipeline` 没登记」 | 它**已**登记（`getInstance`）——缺的是**被回写的那个数据类**及其字段 |
| 「native 里字段名对不上（拼写不一致）」 | `prism_d3d.dll` 里的字段名与 Java 类的字段名**逐个一致** |
| 「CI runner 上本来就这样，不算问题」 | `Windows version` 与 GPU 无关；它在真机（issue #60 的原始报告）上同样为零 |
| 「加登记就能保证全好」 | **不成立**：驱动相关字段依赖真实 GPU，CI 上仍可能为零 ⇒ 必须真机对照（见「未决问题 Q1」） |

## 未决问题

**Q1 修复后的真实值验证到哪一步**

现状：**已在 CI 上验证通过**（2026-10-13，job `115292084727`）：driver 那一组全部为真实值，
OS 版本不再是 `0.0`；唯一遗留是 OS 版本仍为兼容性 shim 值（**已单列为 Q3**）。
阻塞点（仅剩真机对照）：D3D **驱动名 / 厂商 ID / 多重采样**在**真实 GPU** 机器上的取值——
CI runner 是虚拟机（`Microsoft Basic Render Driver`），拿到的是 WARP 软件设备。
按 `IssueTrackingSpec.md` §4，这类人工判断项以 ACANX 的判断为准。

**Q2 同类漏项还有多少（`prism_d3d` 之外）**

现状：已确认 ⑧b 清单**整段漏了 `prism_d3d` 的字段访问**；未逐一核对 `prism_common` / `decora_sse` / `glass.dll` 之外的原生库。
阻塞点：需要一次系统性重扫（对每个 dll 抽字符串 + 与 Java 侧字段/方法名求交集），不在本次修复范围内。

**Q3 原生包读到的 Windows 版本仍是兼容性 shim 值（`Windows 8.0 build 9200`）**

现状：字段回写已生效（不再是 `0.0 build 0`），但值是 `8.0 build 9200`——
这是 `GetVersionEx` 家族在没有「Windows 10 兼容清单」时的经典**兼容性撒谎**。
**交叉证据**：同一份日志里 D3D 驱动版本是 `10.0.26100.33438`，而 **26100 正是 Windows 11 24H2 的构建号**
⇒ 系统真实版本远高于 9200，所以这条是**清单问题**（原生 exe 缺 Win10 兼容清单，GraalVM 生成的 exe 默认没有），
不是「读不到」。
阻塞点：① 需给原生 exe 补清单；② issue #60 验收标准第 1 条的 **JVM 同机对照**仍未做
（预期 JVM 侧打印真实版本，因 `java.exe` 自带清单）——这条正好能验证上述机理。
