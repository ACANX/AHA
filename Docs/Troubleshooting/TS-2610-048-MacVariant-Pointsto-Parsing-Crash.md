# TS-2610-048 · macOS 桌面原生镜像在 GraalVM 25.x 下无法构建（JavaFX `MacVariant.toString` 解析崩溃）

> 事件日期：2026-10-18
> 作者：CNXNC
> 关联 Issue：#303（发布链路加固）、#321（上游兼容缺陷跟踪）
> 关联记录：PR #312 / #313 / #314 / #315 / #317 / #318 / #319；Release run `39119722445` / `39122205313` / `39123955806` / `39125441249`；dev 原生腿 run `39128627477` / `39129966929` / `39130903996` / `39132534613`

## 1. 现象

V0.1.5 正式发布（`Release.yml`）的 12 条腿中，**只有 macOS 桌面端原生腿**产不出二进制：

- `DesktopNativeMacOSAarch64 / NativeImageLeg` 的 job 结论是 **success**（`continue-on-error`），
  但 `BuildNativeImage` 的真实 `outcome` 是 failure；
- 由于 `VerifyNativeImageRan` 的条件是 `steps.native.outcome == 'success'`，它被 **skipped**，
  后续 `RenameToReleaseAsset` / `WritePackageManifest` / `UploadArtifact` 全部 skipped；
- 结果：`ReleaseChecksum` 报缺 `aha-desktop-native-<版本>-macos-aarch64-graalvm25.zip`，
  发布被 `MarkReleaseIncomplete` 标成 **pre-release**（#305 的硬门按预期工作）。

原始报错（逐字照抄，GraalVM 25.4.4.1）：

```
Failed generating 'aha-desktop-native' after 1m 41s.
The build process encountered an unexpected error:
	Suppressed: com.oracle.svm.shared.util.VMError$HostedError: Reflection metadata cannot be queried before analysis is complete.
cause 0com.oracle.graal.pointsto.util.AnalysisError$ParsingError: Error encountered while parsing com.sun.glass.ui.mac.MacVariant.toString(MacVariant.java:193)
	at org.graalvm.nativeimage.pointsto/com.oracle.graal.pointsto.util.AnalysisError.parsingError(AnalysisError.java:165)
Caused by: com.oracle.svm.shared.util.VMError$HostedError: failed building outlined SB method
Caused by: com.oracle.svm.shared.util.VMError$HostedError: should not reach here: unexpected input could not be handled: class java.lang.Object
[ERROR] Failed to execute goal org.codehaus.mojo:exec-maven-plugin:3.5.1:exec (native-image) on project aha-desktop-native: Command execution failed. Process exited with an error: 1
```

**影响面**：只有 `aha-desktop-native-*-macos-aarch64-graalvm25.zip` 缺失。同一版本下：

- `aha-desktop-jvm-*-macos-aarch64-jdk25.zip`（JVM 版）正常；
- `aha-cli-native-*-macos-aarch64-graalvm25.zip`（CLI 原生，不涉及 JavaFX）正常；
- Windows / Ubuntu 的 `desktop-native` 正常（不走 `com.sun.glass.ui.mac`）。

## 2. 排查过程（含取证）

| # | 动作 | 预期 | 实际 | 结论 |
|---|---|---|---|---|
| 1 | 降 GraalVM 25.4 → 25.3 | 缺陷是 25.4 回归 | 同处崩 | 排除「单版本回归」 |
| 2 | `-H:-OptimizeStringConcat` | 关掉 SB 优化，绕过 | `Unrecognized option ... Did you mean '-H:±OptStringConcat'`，exit 20 | 排除「选项名拼写」 |
| 3 | `-H:-OptStringConcat` | 同上（正确名字） | 选项被接受，仍崩 | 排除「SB 优化开关」 |
| 4 | `--initialize-at-build-time=…MacVariant` | 构建期初始化，避开运行时分析 | 仍崩 | 排除「类初始化时机」 |
| 5 | `-H:MethodFilter=…MacVariant.toString` | 排除该方法，不解析 | 选项被接受，parsing 仍崩 | 排除「方法过滤」（过滤作用于编译阶段，parsing 更早） |
| 6 | JavaFX 25 → 25.0.4 | patch 修了该写法 | 仍崩 | 排除「JavaFX patch」 |
| 7 | JavaFX 25 → 24.0.1 | 24 的 toString 写法不同 | 同一行 193、同一错误 | 排除「JavaFX 大版本」 |
| 8 | 降 GraalVM 25.3 → 25.1.3 | 缺陷是 25.2 引入的回归 | 仍崩 | 排除「GraalVM 25.x 区间」 |

被排除的假设都已写明**为什么能排除**（见上表），后人不必重走。

## 3. 根因

- **触发条件**：macOS aarch64 的**桌面端**原生构建（会加载 `javafx-graphics` 的 mac 分类器，其中含
  `com.sun.glass.ui.mac.MacVariant`），GraalVM 25.x，JDK 25；
- **机理**：JavaFX 24/25 的 `MacVariant` 是 enum，`toString()` 用 **switch 表达式**实现，javac 把它编译成
  `invokedynamic`（`StringConcatFactory.makeConcatWithConstants`）。GraalVM 的 pointsto 在**解析**这条
  `invokedynamic` 时，其「outlined StringBuilder（SB）方法」构建器拿到一个 `java.lang.Object` 形态的实参，
  走到 `VMError.shouldNotReachHere`：`unexpected input could not be handled: class java.lang.Object`；
- **为什么没被及早发现**：原生腿是 job 级 `continue-on-error`（整条管线可选），腿失败时 workflow 仍是绿色。
  V0.1.5 之前，`dev` 快照线的 `DesktopNative.yml` 也跑过原生腿并失败，但没人看得到（详见 #303 的原因 D/F）。

## 4. 影响面与恢复条件

**影响面**：仅 `aha-desktop-native-*-macos-aarch64-graalvm25.zip`。JVM 桌面 macOS 包、CLI 原生 macOS 包、
Windows / Ubuntu 的两种原生包均不受影响。

**恢复条件**：GraalVM 上游修复后，代码无需任何改动即可恢复（`-H` 参数已全部回滚，
macOS 仍走 `native-image-args-extra.txt` 的空档文件）。

**若上游短期不修**：需要在「矩阵里把 `desktop-native/macos-aarch64` 从 12 份暂时移除（12 → 11）」
与「继续等待」之间做决定，见 #320。

## 上游跟踪（2026-10-18 补齐）

| 项 | 内容 |
|---|---|
| 同题历史 issue | [`oracle/graal#9017`](https://github.com/oracle/graal/issues/9017)（2024-05-31 提出，2024-06-04 以「改用 GitHub Actions 后消失」关闭） |
| 本次复现 issue | [`oracle/graal#14533`](https://github.com/oracle/graal/issues/14533)（2026-09-26 提出，`[GR-80008]`，2026-09-28 **completed**） |
| 修复 PR | [`oracle/graal#14545`](https://github.com/oracle/graal/pull/14545)（2026-09-28 合入 master；改动仅 1 文件 3 行，见 §机制 / 行为模型「上游修复」） |
| 目标版本 | **GraalVM 25.5**，官方回复「will be backported as well」 |
| 版本节奏（实测） | 25.1.3 / 25.2.4 / 25.3.4.1 / 25.4.4.1 分别发布于 2026-06-30 / 07-28 / 08-25 / 09-22 —— **每 4 周一个 patch（周二）**，据此推断 25.5 约在 **2026-10-20** |
| 已发布版本 | 最新 `graal-25.4.4.1.1`（**2026-09-22**）**早于修复 6 天，必然不含** |

**恢复动作**：25.5（或含 backport 的 25.4.x）发布后，更新 `NativeImageLeg.yml` 的 `version` 并验证
`VerifyNativeImageRan == success`；代码其余部分无需改动（`-H` 参数已全部回滚）。

## 5. 复盘

### 5.1 时间线（每个决策与代价）

| 轮次 | 动作 | 结果 | 代价 |
|---|---|---|---|
| R3 | `release/0.1.5` 从**本地过期的** `origin/dev` 切出，漏了 #309 | CLI macOS 腿失败 | 一次完整发布（约 40 分钟） |
| #311 | 把 #309 cherry-pick 到 main | 正确，但走了「直接进 main」的违规路径 | — |
| #312 | macOS 桌面腿降 GraalVM 25.3 | 无效 | 一次发布 |
| #313 | 加 `-H:-OptimizeStringConcat` | 选项名错，exit 20 | 一次发布 |
| #314 | 改成 `-H:-OptStringConcat` | 参数正确但无效 | 一次发布 |
| #315 | JavaFX 25.0.4 + `--initialize-at-build-time` | 无效 | 一次 dev 原生跑批 |
| #317 | `-H:MethodFilter` | 无效 | 一次 dev 原生跑批 |
| #318 | JavaFX 24.0.1 | 无效（同一行） | 一次 dev 原生跑批 |
| #319 | GraalVM 25.1 | 无效 | 一次 dev 原生跑批 |

### 5.2 四个错误（按严重度）

1. **用正式发布当试验场**：发布开始后仍直接对 `main` 开修复 PR（#311–#314），绕过了「dev 上先用
   `push: dev` 的原生腿验证」这一免费试错通道。正确顺序应是：dev 修 → dev 原生腿验证 → 再进 release。
2. **把 `continue-on-error` 的 success 当成「通过」**：我在 `2f1a24b` 后宣布「6 条原生腿全绿」，
   实际 `BuildNativeImage` 的真实 outcome 早就是 failure。正确信号是 `VerifyNativeImageRan`（R5 才看懂）。
3. **未先确认「这个问题是否可修」就连续试参数**：8 次尝试里 6 次是「换一个开关试试」，
   而真正该先做的是：读 `MacVariant.java:193` 的写法 → 判断是「解析器缺陷」还是「配置问题」→
   再决定要不要试参数。
4. **选项名未核对**：`OptimizeStringConcat` vs `OptStringConcat`，一次发布白跑。

### 5.3 改进措施

| # | 措施 | 状态 |
|---|---|---|
| 1 | 发布期间的所有修复先走 `dev`，用 dev 原生腿验证后再进 `release/*` | 已执行（#315 起） |
| 2 | 原生腿「真的编出来了」的判据固定为 `VerifyNativeImageRan == success` | 已执行（见 §结论分层） |
| 3 | `SummarizeNativeLegs` 当前读 `needs.*.result`，会被 `continue-on-error` 污染，应改读每条腿的 `produced` / `VerifyNativeImageRan` | 待做（#303） |
| 4 | `release/*` 分支必须从**刚 fetch 过**的 `origin/dev` 切出，并在 PR 描述里写明 `dev` head | 已执行（本次事故的第二个原因） |
| 5 | 发布前跑 `bin/CheckReleaseAssets.py --tag <快照 tag> --allow-partial`，先看「形态 × 平台」缺口 | 已落地（#307） |

## 结论分层

### 已观测事实

1. 崩溃点固定在 `com.sun.glass.ui.mac.MacVariant.toString(MacVariant.java:193)`；
2. 在 **GraalVM 25.1.3 / 25.3.4.1 / 25.4.4.1** 三个版本上**完全一致**；
3. 在 **JavaFX 25.0.4 与 24.0.1** 上**完全一致**（两者 `MacVariant.java:193` 代码相同）；
4. 只发生在 macOS aarch64 的**桌面端**；CLI 原生与 Windows/Linux 桌面原生均正常；
5. `-H:-OptStringConcat`、`--initialize-at-build-time`、`-H:MethodFilter` 三个参数都被 native-image
   **正常接受**（无 `Unrecognized option`），但崩溃仍在**同一处**；
6. `BuildNativeImage` 的真实失败被 `continue-on-error` 掩盖成 job success —— 这是「发布页缺 1 份却看不到红」的直接原因。

### 机制 / 行为模型

- JavaFX 24/25 的 `MacVariant` 是 enum，`toString()` 用 **switch 表达式**实现；
- javac 编译成 `invokedynamic`，bootstrap 为 `StringConcatFactory.makeConcatWithConstants`；
- GraalVM 的 pointsto（静态分析）在**解析**这条 `invokedynamic` 时，其「outlined StringBuilder（SB）方法」
  构建器拿到一个 `java.lang.Object` 形态的实参，走到 `VMError.shouldNotReachHere`：
  `unexpected input could not be handled: class java.lang.Object`；
- 这属于 **GraalVM 对该字节码形态的兼容缺陷**，与 JavaFX 具体版本、与本项目代码无关；
- `MacVariant` 只存在于 macOS 分类器的 `javafx-graphics`，所以只有 macOS 桌面端触发。

### 上游修复（`oracle/graal#14545`，2026-09-28 合入 master）

文件：`substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/sboutlining/OutlinedSBMethod.java`

```diff
-            if (methodType.returnType().equals(String.class)) {
+            ResolvedJavaType returnType = providers.getMetaAccess().lookupJavaType(methodType.returnType());
+            ResolvedJavaType stringType = providers.getMetaAccess().lookupJavaType(String.class);
+            if (returnType.isAssignableFrom(stringType)) {
                 graphBuilderMH = new SubstrateStringConcatFactory(kit).makeConcat(methodType);
             } else {
                 graphBuilderMH = new SubstrateSBConcatFactory(kit).makeConcat(methodType.dropParameterTypes(0, 1));
```

**这两行的差别，正是本缺陷的全部**：

- 旧判定只认「返回类型 **等于** `String`」。而 `Object v = "a"; v += "b";` 生成的 `indy`
  返回类型是 **`Object`**，于是掉进 `SubstrateSBConcatFactory`（outlined SB 拼接）分支，
  `OutlinedSBMethod.buildGraph` 拿到 `Object` 就在 `VMError.shouldNotReachHere` 崩溃；
- 新判定改为「`String` **可赋给** 返回类型」（`returnType.isAssignableFrom(String.class)`），
  `Object` / `CharSequence` / `Serializable` 等超类型都改走 `SubstrateStringConcatFactory`，
  从根上不再进入 SB 分支；
- 因此本项目能想到的任何 native-image 参数都不可能绕过 —— 问题在**上游分支选择的类型判定**，
  与优化开关、初始化时机、方法过滤、JavaFX 版本都无关。

**为什么 JavaFX 命中**：`MacVariant.toString()` 是 switch 表达式，其合成结果被当作 `Object`
使用（switch 的多分支常量类型推断），javac 生成的 `indy` 因此返回 `Object`。

### 未定位 / 未复现

- ~~未确认该缺陷在 GraalVM 的哪个 commit / issue 被记录~~ → **已定位**，见 §上游跟踪；
- 未确认是否存在「GraalVM 22/23 + JavaFX 21」这类旧组合可用（项目基线要求 JDK 25，未验证旧组合的可行性）；
- 未验证「给 JavaFX 打字节码补丁（把 switch 表达式改回 if/else）」的可行性与维护成本（已不需要：上游已修）。

## 观测数据

### 版本矩阵（全部失败，同一处崩溃）

| GraalVM | JavaFX | 结果 | 证据 |
|---|---|---|---|
| 25.4.4.1 | 25 | 崩 | Release run `39119722445` |
| 25.3.4.1 | 25.0.4 | 崩 | Release run `39122205313` |
| 25.3.4.1 | 25.0.4 + `-H:-OptStringConcat` | 崩 | Release run `39125441249` |
| 25.3.4.1 | 25.0.4 + `--initialize-at-build-time` | 崩 | dev run `39128627477` |
| 25.3.4.1 | 25.0.4 + `-H:MethodFilter` | 崩 | dev run `39129966929` |
| 25.3.4.1 | 24.0.1 | 崩（同一行 193） | dev run `39130903996` |
| **25.1.3** | 25.0.4 | 崩 | dev run `39132534613` |

### 「绿但零产物」的判定方法（本次沉淀）

`continue-on-error` 的 job，`conclusion` 恒为 success，`outcome` 才是真实结果。
可靠的判据是看依赖 `steps.native.outcome == 'success'` 的那一步：

| 步骤 | 结论 | 含义 |
|---|---|---|
| `BuildNativeImage` | `success` / `skipped` | **不可信**（被 continue-on-error 归一） |
| `VerifyNativeImageRan` | `success` | 确实编出了二进制 |
| `VerifyNativeImageRan` | `skipped` | `BuildNativeImage` 的真实 outcome 是 failure |

## 排除项

| # | 动作 | 预期 | 实际 | 结论 |
|---|---|---|---|---|
| 1 | 降 GraalVM 25.3 | 缺陷是 25.4 回归 | 同处崩 | 排除「单版本回归」 |
| 2 | `-H:-OptimizeStringConcat` | 关掉 SB 优化，绕过 | 选项名不存在，exit 20 | 排除「选项名拼写」 |
| 3 | `-H:-OptStringConcat` | 同上（正确名字） | 选项被接受，仍崩 | 排除「SB 优化开关」 |
| 4 | `--initialize-at-build-time=…MacVariant` | 构建期初始化，避开运行时分析 | 仍崩 | 排除「类初始化时机」 |
| 5 | `-H:MethodFilter=…MacVariant.toString` | 排除该方法，不解析 | 选项被接受，parsing 仍崩 | 排除「方法过滤」 |
| 6 | JavaFX 25 → 25.0.4 | patch 修了该写法 | 仍崩 | 排除「JavaFX patch」 |
| 7 | JavaFX 25 → 24.0.1 | 24 的 toString 写法不同 | 同一行 193、同一错误 | 排除「JavaFX 大版本」 |
| 8 | 降 GraalVM 25.3 → 25.1.3 | 缺陷是 25.2 引入的回归 | 仍崩 | 排除「GraalVM 25.x 区间」 |

**结论**：在 GraalVM 25.x 范围内，无任何参数或依赖版本能绕开该缺陷。

## 未决问题

1. ~~本缺陷的上游链接~~ → 已定位并补齐，见「上游跟踪」；
2. **等修复版本**：25.5（按 4 周节奏约 2026-10-20）或含 backport 的 25.4.x 发布前，macOS 桌面原生腿无法产出；
3. **是否需要「矩阵豁免」**：若上游短期不修，需决定是否把 `desktop-native/macos-aarch64` 从 12 份里
   暂时移除（并同步 `DesktopNative.yml` / `Release.yml` / `check_release_matrix()`），见 #320 的选项 A；
4. **是否尝试旧组合**（GraalVM 22/23 + JavaFX 21）作为替代构建链：未验证，成本高。
