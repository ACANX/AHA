# TS-202610-PrismEffectPeerMissing：原生桌面端能开窗但控件画不出 —— Prism 效果 peer 的动态类名未登记（issue #41）

> **日期**：2026-10-09
> **作者**：@ACANX / CNXNC
> **关联 PR**：待提（分支 `fix/issue-41-native-effect-peers` → `dev`）
> **关联记录**：[issue #41](https://github.com/ACANX/AHA/issues/41)、
> [TS-202610-WinWindowJniMemberMissing.md](TS-202610-WinWindowJniMemberMissing.md)（issue #39，JNI 成员层）、
> [TS-202610-QuantumToolkitMissing.md](TS-202610-QuantumToolkitMissing.md)（issue #35，反射层）

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
- `Docs/Troubleshooting/TS-202610-PrismEffectPeerMissing.md`（本文件）
