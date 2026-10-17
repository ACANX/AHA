# TS-2610-023 · 原生桌面端能开窗但控件画不出 —— Prism 效果 peer 的动态类名未登记（issue #41）

> **日期**：2026-10-09
> **作者**：@ACANX / CNXNC
> **关联 PR**：待提（分支 `fix/issue-41-native-effect-peers` → `dev`）
> **关联记录**：[issue #41](https://github.com/ACANX/AHA/issues/41)、
> [TS-2610-022-Win-Window-JNI-Member-Missing.md](TS-2610-022-Win-Window-JNI-Member-Missing.md)（issue #39，JNI 成员层）、
> [TS-2610-019-Quantum-Toolkit-Missing.md](TS-2610-019-Quantum-Toolkit-Missing.md)（issue #35，反射层）

## 1. 背景

- 前情：issue #35 / #37 / #39 相继修好后，Windows 真机上的原生桌面端**首次进到 GUI**。
- 但命令行的错误输出里反复刷：

```
Error: Prism peer not found for: LinearConvolveShadow due to error:
    com.sun.scenario.effect.impl.prism.ps.PPSLinearConvolveShadowPeer
java.lang.RuntimeException: Could not create peer  LinearConvolveShadow
    for renderer com.sun.scenario.effect.impl.prism.ps.PPSRenderer@6b85f401
        at com.sun.scenario.effect.impl.Renderer.getPeerInstance(Renderer.java:258)
        at com.sun.scenario.effect.impl.state.BoxRenderState.getPassPeer(BoxRenderState.java:312)
        at com.sun.scenario.effect.LinearConvolveCoreEffect.filterImageDatas(LinearConvolveCoreEffect.java:82)
        ...
```

界面本身出来了，**但没有任何控件被绘制**——渲染在第一个用到阴影效果的控件上就整段失败。

## 2. 排障过程与修复链

### 2.1 报错点在做什么（读 openjfx 25 源码）

`Renderer.getPeerInstance(fctx, name, unrollCount)` 是效果 peer 的延迟创建入口；
真正的加载在 `PPSRenderer.createPeer`：

```java
// 内在 peer（Prism 自己实现的效果）
klass = Class.forName(rootPkg + ".impl.prism.Pr" + name + "Peer");          // createIntrinsicPeer

// 平台（着色器）peer
Class klass = Class.forName(rootPkg + ".impl.prism.ps.PPS" + name + "Peer"); // createPlatformPeer
```

软件回退（`PSWRenderer`）走 `RendererDelegate.getPlatformPeerName(...)`，拼出
`…impl.sw.java.JSW<name>Peer` 或 `…impl.sw.sse.SSE<name>Peer`。
`rootPkg` = `com.sun.scenario.effect`。异常被 `catch (Exception e)` 吞下，只打印
`Error: … peer not found for: <name> due to error: <类名>`，然后返回 `null`，
最终由 `getPeerInstance` 抛 `RuntimeException: Could not create peer <name>`。

**取证对照（要求 vs 实际）**：

| 要求 | 实际 | 结论 |
|---|---|---|
| `Class.forName("…prism.ps.PPSLinearConvolveShadowPeer")` 能成功 | `ClassNotFoundException`，消息就是类名 | 类不在镜像里 |
| 渲染器（`PPSRenderer` 等）已登记 | 在册（#35 补的） | 渲染器 ≠ 效果 peer，是**两层** |
| stock shader 加载器已登记（212 个） | 在册 | shader ≠ peer，是**三层** |

### 2.2 根因：动态拼接的类名

与 issue #35 **同源**：`PPS<name>Peer` 的类名是运行期用效果名拼出来的，
native-image 的 closed-world 静态分析**看不到**；而效果名取决于界面里实际出现的效果
（`DropShadow` / `InnerShadow` / `BoxShadow` 等 → `LinearConvolveShadow`），
不是「启动链路」能固定的那一小撮类。之前只登记了渲染器工厂与着色器加载器，
**没有登记效果 peer 本身**。

### 2.3 为什么不「只补报错那一个」

同一套动态类名会按效果逐个触发：`LinearConvolve`、`Blend_*`、`ColorAdjust`、
`PhongLighting_*`、`SepiaTone`、`Brightpass`、`PerspectiveTransform` …
补一个只是把崩溃点换到下一个控件。这与 `N-16` 的教训一致——修的是**一类**。

### 2.4 修法（静态全量扫描 jar）

对 javafx-graphics 25 的 jar 扫描 `com/sun/scenario/effect/impl/**/*Peer.class`，得到 106 个；
用 `javap` 过滤掉 7 个 `abstract` 基类（`EffectPeer` / `PPSEffectPeer` / `PPSOneSamplerPeer` /
`PPSTwoSamplerPeer` / `PPSZeroSamplerPeer` / `JSWEffectPeer` / `SSEEffectPeer`），
剩 **99 个具体 peer**，全部登记进 `reachability-metadata.json`：

```json
{"type": "com.sun.scenario.effect.impl.prism.ps.PPSLinearConvolveShadowPeer", "allDeclaredConstructors": true}
```

- **登记位置**：`Class.forName` + `getConstructor(...).newInstance(...)` 属**反射**，
  故进 `reachability-metadata.json`（不是 `jni-config.json`）。
- **只登记构造器**：peer 实例化后按接口虚调用其方法，方法名不反射，`allDeclaredConstructors` 足够。
- 覆盖四个包 / 三种回退：`prism.Pr*`（4）、`prism.ps.PPS*`（35）、
  `sw.java.JSW*`（31）、`sw.sse.SSE*`（29）。

元数据由 **340 条增至 439 条**（+99，无重复）。

## 3. 最终验证结果

- **元数据**：`reachability-metadata.json` 340 → **439 条**，`"type"` 无重复，JSON 合法。
- **反向验证**：临时把 `PPSLinearConvolveShadowPeer` 改名 →
  `NativeImageMetadataTest.reachabilityMetadataRegistersPrismEffectPeers` **如期失败**；
  恢复后全绿。
- **单测**：`NativeImageMetadataTest` 11 → **12 条**；
  `./mvnw -pl aha-desktop -am test -Djacoco.skip=true` → **159 用例全绿**（1 skipped）。
- **产物自证**：`DesktopNative.yml` 第 ⑧ 条新增 `PPSLinearConvolveShadowPeer` 检查。
- **快速检查**：`bin/CheckDocs.py`、`bin/CheckScripts.py`、`bin/CheckSkills.py` 全绿。
- **真机待验证**：本机是 Linux，无法运行 Windows 产物；需 CI 重出包后复跑
  （`TODO` `N-23`）。诚实口径：**已知缺口已闭，不等于证明完整**。

## 4. 关键教训

1. **「动态类名」是 native-image 的固定盲区**。凡是 `Class.forName(前缀 + name + 后缀)`，
   静态分析都看不到；发现办法是**扫源码里的拼接模板 + 扫 jar 里的候选类**，而不是只补报错点。
2. **渲染链路是分层的**：渲染器工厂 → 效果 peer → 着色器加载器，
   三层各自反射，登记了一层不代表覆盖下一层（#35 登记了前两者中的渲染器，本次补中间一层）。
3. **同类清单放同一处**：`Class.forName` → 反射清单；`FindClass`/JNI 成员 → `jni-config.json`。
   混淆位置会产出「能跑但不合规 / 找错文件」的维护负担。
4. **守卫仍要有反例**：反向验证才是守卫有效的证据。

## 5. 涉及文件清单

- `aha-desktop/src/main/resources/META-INF/native-image/com.acanx.module.aha/aha-desktop/reachability-metadata.json`（340 → 439 条）
- `aha-desktop/src/test/java/com/acanx/module/aha/desktop/NativeImageMetadataTest.java`（11 → 12 条）
- `.github/workflows/DesktopNative.yml`（产物自证第 ⑧ 条补 peer 检查）
- `Docs/Design/DesktopNativeDesign.md`（§6.1 补 issue #41 一节、R2）
- `Docs/TODO.md`（`N-23`、`G-11`）
- `CHANGELOG.md`
- `Docs/Troubleshooting/TS-2610-023-Prism-Effect-Peer-Missing.md`（本文件）

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- 前情：issue #35 / #37 / #39 相继修好后，Windows 真机上的原生桌面端**首次进到 GUI**。
- 但命令行的错误输出里**反复刷**：

  ```text
  Error: Prism peer not found for: LinearConvolveShadow due to error:
      com.sun.scenario.effect.impl.prism.ps.PPSLinearConvolveShadowPeer
  java.lang.RuntimeException: Could not create peer  LinearConvolveShadow
      for renderer com.sun.scenario.effect.impl.prism.ps.PPSRenderer@6b85f401
          at com.sun.scenario.effect.impl.Renderer.getPeerInstance(Renderer.java:258)
          at com.sun.scenario.effect.impl.state.BoxRenderState.getPassPeer(BoxRenderState.java:312)
          at com.sun.scenario.effect.LinearConvolveCoreEffect.filterImageDatas(LinearConvolveCoreEffect.java:82)
  ```

- **界面本身出来了，但没有任何控件被绘制**——渲染在第一个用到阴影效果的控件上就整段失败。
- **读源码定位机制**：`PPSRenderer.createPeer` 用
  `Class.forName(rootPkg + ".impl.prism.Pr" + name + "Peer")`（内在 peer）与
  `Class.forName(rootPkg + ".impl.prism.ps.PPS" + name + "Peer")`（平台 peer）；
  软件回退走 `RendererDelegate.getPlatformPeerName(...)` 拼出 `…impl.sw.java.JSW<name>Peer`
  或 `…impl.sw.sse.SSE<name>Peer`，`rootPkg` = `com.sun.scenario.effect`。
  异常被 `catch (Exception e)` **吞下**，只打印一行，然后返回 `null`，
  最终由 `getPeerInstance` 抛 `RuntimeException: Could not create peer <name>`。
- **取证对照（要求 vs 实际）**：

  | 要求 | 实际 | 结论 |
  | --- | --- | --- |
  | `Class.forName("…prism.ps.PPSLinearConvolveShadowPeer")` 能成功 | `ClassNotFoundException`，消息**就是类名** | 类不在镜像里 |
  | 渲染器（`PPSRenderer` 等）已登记 | 在册（#35 补的） | **渲染器 ≠ 效果 peer**，是**两层** |
  | stock shader 加载器已登记（212 个） | 在册 | **shader ≠ peer**，是**三层** |

- **修复规模**：扫 javafx-graphics 25 的 jar 里 `com/sun/scenario/effect/impl/**/*Peer.class`
  得到 **106 个**；用 `javap` 过滤掉 **7 个 `abstract` 基类**（`EffectPeer`、`PPSEffectPeer`、
  `PPSOneSamplerPeer`、`PPSTwoSamplerPeer`、`PPSZeroSamplerPeer`、`JSWEffectPeer`、`SSEEffectPeer`），
  剩 **99 个具体 peer** 全部登记；覆盖面为四包 / 三种回退：
  `prism.Pr*`（**4**）、`prism.ps.PPS*`（**35**）、`sw.java.JSW*`（**31**）、`sw.sse.SSE*`（**29**）。
  元数据由 **340 条增至 439 条**（+99，无重复）。
- 只登记**构造器**（`allDeclaredConstructors`）：peer 实例化后按接口**虚调用**其方法，
  方法名不反射。
- **验证**：`"type"` 无重复、JSON 合法；
  **反向验证**：临时把 `PPSLinearConvolveShadowPeer` 改名 →
  `reachabilityMetadataRegistersPrismEffectPeers` **如期失败**，恢复后全绿；
  `NativeImageMetadataTest` 11 → **12 条**；
  `-pl aha-desktop -am test -Djacoco.skip=true` → **159 用例全绿**（1 skipped）；
  产物自证第 ⑧ 条新增该 peer 检查；三个检查脚本全绿。
- **真机待验证**：本机是 Linux，无法运行 Windows 产物；需 CI 重出包后复跑（`TODO` **N-23**）。

### 机制 / 行为模型

- **「动态类名」是 native-image 的固定盲区**：凡是 `Class.forName(前缀 + name + 后缀)`，
  静态分析都看不到。发现办法是**扫源码里的拼接模板** + **扫 jar 里的候选类**，
  而不是只补报错点。
- **渲染链路是分层的**：渲染器工厂 → **效果 peer** → 着色器加载器，
  **三层各自反射**，登记了一层不代表覆盖下一层
  （#35 登记了渲染器与 shader 加载器，本次补的是**中间那层**）。
- **同类清单放同一处**：`Class.forName` 系列 → `reachability-metadata.json`；
  `FindClass` / JNI 成员 → `jni-config.json`。混淆位置会产出
  「能跑但不合规 / 找错文件」的维护负担。
- **只登记构造器就够**的前提是「实例化后按接口虚调用」——判断依据要写清，
  否则下一个人会以为漏了方法。
- **守卫仍要有反例**：反向验证才是守卫有效的证据。

### 未定位 / 未复现

- **真机验证未做**：本机是 Linux，**无法运行 Windows 产物** ⇒「控件真的画出来了」**当时只是推断**，
  需 CI 重出包后复跑（`N-23`）。
- **99 个 peer 是否覆盖所有会被用到的效果**：本次是**按 jar 全量登记**（宁可多、不可少），
  没有统计界面实际用到了哪些效果，也没有做「遗漏检测」。
- **macOS / Linux 上的同类问题未真机验证**。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 错误输出 | 原生 exe 的控制台 | `Error: Prism peer not found for: LinearConvolveShadow due to error: …PPSLinearConvolveShadowPeer` |
| 最终异常 | 同上 | `RuntimeException: Could not create peer LinearConvolveShadow for renderer …PPSRenderer@6b85f401` |
| 界面表现 | 真机 | 界面出来了，**但没有任何控件被绘制** |
| 动态拼接的模板 | openjfx 25 源码 `PPSRenderer.createPeer` | `Class.forName(rootPkg + ".impl.prism.ps.PPS" + name + "Peer")`（另有 `Pr…Peer` 与软件回退 `JSW…` / `SSE…`） |
| 异常为何只是「一行」 | 源码 | `catch (Exception e)` 吞掉后 `return null`，最终由 `getPeerInstance` 抛 `RuntimeException` |
| 扫描对象与结果 | jar 里 `com/sun/scenario/effect/impl/**/*Peer.class` | **106 个** → 过滤 **7 个 abstract** → **99 个**具体 peer |
| 覆盖分布 | 同上 | `prism.Pr*` **4** / `prism.ps.PPS*` **35** / `sw.java.JSW*` **31** / `sw.sse.SSE*` **29** |
| 元数据规模 | `reachability-metadata.json` | **340 → 439 条**（+99），`"type"` 无重复 ✅ |
| **反向验证** | 临时改名 `PPSLinearConvolveShadowPeer` | `reachabilityMetadataRegistersPrismEffectPeers` **如期失败**；恢复后全绿 ✅ |
| 单测 | `NativeImageMetadataTest` | 11 → **12 条**；整模块 **159 用例全绿**（1 skipped） |
| 产物自证 | `DesktopNative.yml` 第 ⑧ 条 | 新增 `PPSLinearConvolveShadowPeer` 检查 |
| **未验证** | — | 真机（本机 Linux 跑不了 Windows 产物）⇒ `N-23` |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「#35 / #37 / #39 的修复没生效」 | 原生端**首次进到 GUI**（前三层的缺口都闭上了）；本次是**渲染层**（「1、背景」） |
| 「渲染器没登记」 | 渲染器（`PPSRenderer` 等）**在册**（#35 补的）——缺口在**效果 peer** 这一层（「2.1」） |
| 「着色器加载器没登记」 | 212 个 stock shader `*_Loader` **在册**——shader 与 peer 是**两层**（「2.1」） |
| 「只补 `LinearConvolveShadow` 那一个」 | 同一套动态类名会按效果逐个触发（`Blend_*`、`ColorAdjust`、`PhongLighting_*`、`SepiaTone` …），补一个只是把崩溃点换到下一个控件（「2.3」） |
| 「该进 `jni-config.json`」 | 这里是 `Class.forName` + 构造器实例化，属**反射** ⇒ 进 `reachability-metadata.json`（「2.4」） |
| 「必须连方法一起登记」 | peer 实例化后按**接口虚调用**，方法名不反射 ⇒ `allDeclaredConstructors` 足够（「2.4」） |
| 「守卫加了就等于有效」 | 反向验证（临时改名 → 守卫**如期变红**）才证明它不是摆设（「3、最终验证」） |

## 未决问题

**Q1 Windows 上控件真的画出来了吗？**

现状：**未验证**——本机 Linux 跑不了 Windows 产物；需 CI 重出包后复跑（`N-23`）。
阻塞点：需要真机截图或「渲染无错误输出」的证据。

**Q2 99 个 peer 之外还会不会有缺口？**

现状：**按 jar 全量登记**（`impl/**/*Peer.class` 里除 abstract 基类全收），
理论上覆盖了 Prism 25 提供的全部 peer；但**没有**验证「界面用到的效果集合」是否都被覆盖，
也没有遗漏检测手段。
阻塞点：可考虑在真机上把所有 `-fx-effect` 走一遍（GUI 走查），或读渲染日志确认无 `peer not found`。

**Q3 三层之外的第四层还有吗？**

现状：本档案明确了「渲染器 → 效果 peer → shader 加载器」三层；
**是否还有别的动态类名层未排查**。三轮（#35 / #39 / #41）都是「修完才发现下一层」，
说明**没有系统性的排查方法**。
阻塞点：需要一个「扫源码里所有 `Class.forName` 拼接模板」的清单式做法，尚未沉淀成工具。

**Q4 macOS / Linux 上有同类缺口吗？**

现状：**未验证**（清单是跨平台共用的，但只在 Windows 上观测过问题）。
