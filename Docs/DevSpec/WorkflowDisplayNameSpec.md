# 工作流显示名规范

**文档版本**：v1.0.0
**状态**：生效
**生效日期**：2026-10-17
**最后更新**：2026-10-17
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-17 | 初始版本：显示名 / 作业 id / commit status context / 平台标签的字符集与风格（issue [#287](https://github.com/ACANX/AHA/issues/287)）；含存量统一映射表 | @cnxnc |

---

## 1. 适用范围

| 对象 | 在哪 | 由谁消费 |
|---|---|---|
| 显示名 | `name:`（工作流 / 作业 / 步骤）、`run-name:` | Actions 页面、`gh run list`、Job Summary、PR 检查列表 |
| 作业 id | `jobs.<id>:` | `needs:`、`if: needs.<id>`、日志 |
| commit status context | `gh api ... -f context=…` | 分支保护、升级路径（加进 required） |
| 平台标签 | 矩阵 `label`、资产名 | 出包命名、升级选择器、文档 |

## 2. 规则

### 2.1 显示名（强制）

- 一律 **ASCII 英文大驼峰**，整体匹配 `^[A-Z][A-Za-z0-9]*$`；
- **零表达式**：不许 `${ … }`；参数只允许出现在 `runs-on` / `with` / `env` / 步骤 `if` 里；
- **零标点**：不许 `-`、`(`、`)`、空格、`/`、`:`、中文、全角标点；
- **用单数**：`StaticGuard`（不是 `StaticGuards`）、`ReleaseChecksum`（不是 `ReleaseChecksums`）；
- **缩写整体大写**：`JDK25`、`MacOSAarch64`、`UISmoke`、`CLI`、`PR`、`JVM`、`ID`；
- **原生镜像腿的版本段写作 `GraalVM25` / `GraalVM27`**（GraalVM 版本对应）：如
  `NativeImageWindowsAmd64GraalVM25`；JVM 线仍是 `JDK25` / `JDK27`（两条线的版本含义不同）；
- 语义靠词本身表达，细节留给 `run:` 与注释。

### 2.2 作业 id（强制）

- 与显示名同规则（`^[A-Z][A-Za-z0-9]*$`）；
- 矩阵作业**展开为逐腿静态作业**：显示名与作业 id 一致（如 `NativeImageWindowsAmd64GraalVM25`），
  重复的 steps 放进可复用工作流（`*Leg.yml`）；
- 收窄（「这次只出哪几条腿」）用 **job 级 `if`**，不再靠步骤级遮挡。
- **调用可复用工作流的 job 只允许**：`name` / `uses` / `with` / `secrets` / `strategy` / `needs` / `if` / `permissions` / `concurrency`；
  **`runs-on` / `steps` / `continue-on-error` 一律不许** —— 写了会让**整个工作流文件解析失败**
  （GitHub 的报错不指向具体键，只显示「This run likely failed because of a workflow file issue」）。
  「可选腿」的容错放进可复用工作流内部的 job（如 `BuildLeg.yml` 的 `continue-on-error: ${{ inputs.optional }}`）。

### 2.3 commit status context（强制）

- 与显示名同规则（`^[A-Z][A-Za-z0-9]*$`）；
- 当前两个：`SnapshotAssert`、`PreDeployVerify`；
- 改名必须同步：工作流 `-f context=`、守卫脚本、文档的「加进 required」升级路径。

### 2.4 平台标签（强制）

- 架构词统一 **`aarch64`**：`macos-aarch64`（新产出一律）；
- **读取兼容**历史 `macos-arm64`（旧资产名仍能被升级选择器选中）；
- 标签本身允许连字符（它是资产名的一段，不是显示名）。

### 2.5 `run-name`（强制）

- 当前仓库未使用；将来启用时同 2.1 规则（静态英文大驼峰、零表达式）。

## 3. 映射表（2026-10-17 存量统一）

### 3.1 作业 id（一行一项）

| 文件 | 旧 | 新 |
|---|---|---|
| `Build.yml` | `build` | `BuildWindowsLatestWrapper` |
| `Build.yml` | `build` | `BuildUbuntuLatestWrapper` |
| `Build.yml` | `build` | `BuildUbuntuLatestSystem` |
| `Build.yml` | `build` | `BuildMacOSLatestWrapper` |
| `Build.yml` | `ui-smoke` | `UISmoke` |
| `Build.yml` | `native-update-script` | `NativeUpdateScript` |
| `Build.yml` | `static-guards` | `StaticGuard` |
| `Build.yml` | `tag` | `Tag` |
| `BuildJVMArtifacts.yml` | `build-version` | `BuildVersion` |
| `BuildJVMArtifacts.yml` | `build-mvn-artifact` | `BuildMvnArtifactUbuntuAmd64JDK25` |
| `BuildJVMArtifacts.yml` | `build-mvn-artifact` | `BuildMvnArtifactWindowsAmd64JDK25` |
| `BuildJVMArtifacts.yml` | `build-mvn-artifact` | `BuildMvnArtifactMacOSAarch64JDK25` |
| `BuildJVMArtifacts.yml` | `build-mvn-artifact` | `BuildMvnArtifactUbuntuAmd64JDK27` |
| `BuildJVMArtifacts.yml` | `build-mvn-artifact` | `BuildMvnArtifactWindowsAmd64JDK27` |
| `BuildJVMArtifacts.yml` | `build-mvn-artifact` | `BuildMvnArtifactMacOSAarch64JDK27` |
| `BuildJVMArtifacts.yml` | `build-publish` | `BuildPublish` |
| `CliNative.yml` | `plan` | `Plan` |
| `CliNative.yml` | `cli-native-version` | `CliNativeVersion` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageUbuntuAmd64GraalVM25` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageWindowsAmd64GraalVM25` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageMacOSAarch64GraalVM25` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageWindowsAmd64GraalVM27` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageUbuntuAmd64GraalVM27` |
| `CliNative.yml` | `cli-native-publish` | `CliNativePublish` |
| `CliNative.yml` | `comment` | `Comment` |
| `CodeQL.yml` | `analyze` | `Analyze` |
| `Compat.yml` | `compat` | `Compat` |
| `DesktopNative.yml` | `plan` | `Plan` |
| `DesktopNative.yml` | `native-version` | `NativeVersion` |
| `DesktopNative.yml` | `native-image` | `NativeImageUbuntuAmd64GraalVM25` |
| `DesktopNative.yml` | `native-image` | `NativeImageWindowsAmd64GraalVM25` |
| `DesktopNative.yml` | `native-image` | `NativeImageMacOSAarch64GraalVM25` |
| `DesktopNative.yml` | `native-image` | `NativeImageWindowsAmd64GraalVM27` |
| `DesktopNative.yml` | `native-image` | `NativeImageUbuntuAmd64GraalVM27` |
| `DesktopNative.yml` | `native-publish` | `NativePublish` |
| `DesktopNative.yml` | `comment` | `Comment` |
| `Gate.yml` | `gate` | `Gate` |
| `PreDeployVerify.yml` | `predeploy-verify` | `PreDeployVerify` |
| `Release.yml` | `gate` | `Gate` |
| `Release.yml` | `compat` | `Compat` |
| `Release.yml` | `release` | `CliReleasePackage` |
| `Release.yml` | `desktop` | `DesktopPackageUbuntuAmd64` |
| `Release.yml` | `desktop` | `DesktopPackageWindowsAmd64` |
| `Release.yml` | `desktop` | `DesktopPackageMacOSAarch64` |
| `Release.yml` | `checksums` | `Checksums` |
| `SnapshotAssert.yml` | `snapshot-assert` | `SnapshotAssert` |
| `SnapshotCleanup.yml` | `sweep` | `Sweep` |
| `SnapshotJVM.yml` | `plan` | `Plan` |
| `SnapshotJVM.yml` | `build` | `Build` |
| `SnapshotJVM.yml` | `publish` | `Publish` |
| `SnapshotJVM.yml` | `comment` | `Comment` |
| `VersionBump.yml` | `bump` | `Bump` |

### 3.2 步骤显示名（一行一项）

> 矩阵作业的 steps 已搬进可复用工作流，故「旧作业」列写的是原作业 id（`build` / `ui-smoke` …），
> 新名所在文件见可复用工作流（`BuildLeg.yml` 等）。逐条对应关系如下。

| 文件（旧） | 旧作业 | 文件（新） | 新作业 | 旧显示名 | 新显示名 |
|---|---|---|---|---|---|
| `Build.yml` | `build` | `BuildLeg.yml` | `BuildLeg` | `换装 helper 自测（Windows：真跑一键入口 apply-update.cmd）` | `RunHelperSelfTestOnWindows` |
| `Build.yml` | `build` | `BuildLeg.yml` | `BuildLeg` | `Test (Wrapper / Maven 4)` | `TestWithWrapperMaven4` |
| `Build.yml` | `build` | `BuildLeg.yml` | `BuildLeg` | `Test (System / Maven 3.9.x)` | `TestWithSystemMaven` |
| `Build.yml` | `build` | `BuildLeg.yml` | `BuildLeg` | `Compile skill templates` | `CompileSkillTemplates` |
| `Build.yml` | `ui-smoke` | `Build.yml` | `UISmoke` | `装 Xvfb 与 JavaFX 需要的系统库` | `InstallXvfbAndJavaFxLibs` |
| `Build.yml` | `ui-smoke` | `Build.yml` | `UISmoke` | `预装依赖模块（只编译，不跑它们的测试）` | `InstallDependenciesOnly` |
| `Build.yml` | `ui-smoke` | `Build.yml` | `UISmoke` | `跑 GUI 冒烟（xvfb-run，只测 aha-desktop）` | `RunGuiSmokeWithXvfb` |
| `Build.yml` | `ui-smoke` | `Build.yml` | `UISmoke` | `结论写进摘要` | `WriteSummary` |
| `Build.yml` | `native-update-script` | `Build.yml` | `NativeUpdateScript` | `跑 Dist/UpdateNative.ps1 -List` | `RunNativeUpdateList` |
| `Build.yml` | `native-update-script` | `Build.yml` | `NativeUpdateScript` | `换装 helper 自测（失败路径 + 成功路径 + 启动标记 + 备份）` | `RunHelperSelfTest` |
| `Build.yml` | `static-guards` | `Build.yml` | `StaticGuard` | `跑 bin/Check*.py` | `RunBinChecks` |
| `Build.yml` | `static-guards` | `Build.yml` | `StaticGuard` | `版本号格式自检（ProjectVersion.py --self-test）` | `SelfTestProjectVersion` |
| `Build.yml` | `static-guards` | `Build.yml` | `StaticGuard` | `包清单工具自检（PackageManifest.py --self-test）` | `SelfTestPackageManifest` |
| `Build.yml` | `tag` | `Build.yml` | `Tag` | `读取项目版本（唯一入口：.github/Python/ProjectVersion.py）` | `ReadProjectVersion` |
| `Build.yml` | `tag` | `Build.yml` | `Tag` | `打 tag 并推送（已存在则跳过）` | `CreateAndPushTag` |
| `Build.yml` | `tag` | `Build.yml` | `Tag` | `派发 Release.yml（GITHUB_TOKEN 的官方例外：workflow_dispatch）` | `DispatchRelease` |
| `BuildJVMArtifacts.yml` | `build-version` | `BuildJVMArtifacts.yml` | `BuildVersion` | `归一「要不要挂发布页」（publish）` | `NormalizePublishFlag` |
| `BuildJVMArtifacts.yml` | `build-version` | `BuildJVMArtifacts.yml` | `BuildVersion` | `自证构建矩阵并算出要出的腿（非法就响亮失败）` | `ValidateMatrixAndSelectLegs` |
| `BuildJVMArtifacts.yml` | `build-version` | `BuildJVMArtifacts.yml` | `BuildVersion` | `计算版本（a.b.c.PPPPP，与原生线同规则）` | `ComputeVersion` |
| `BuildJVMArtifacts.yml` | `build-mvn-artifact` | `BuildMvnArtifactLeg.yml` | `BuildMvnArtifactLeg` | `构建 aha-desktop 与 aha-cli（含本平台 OpenJFX 分类器）` | `BuildDesktopAndCli` |
| `BuildJVMArtifacts.yml` | `build-mvn-artifact` | `BuildMvnArtifactLeg.yml` | `BuildMvnArtifactLeg` | `产物自证（class 文件确为 JDK ${{ matrix.jdk }} 字节码）` | `VerifyClassFileVersion` |
| `BuildJVMArtifacts.yml` | `build-mvn-artifact` | `BuildMvnArtifactLeg.yml` | `BuildMvnArtifactLeg` | `规范桌面端包名（加构建版本与平台标签；JDK 27 另带 -jdk27）` | `NormalizeDesktopPackageName` |
| `BuildJVMArtifacts.yml` | `build-mvn-artifact` | `BuildMvnArtifactLeg.yml` | `BuildMvnArtifactLeg` | `打 CLI 便携包（平台无关，只在一个腿产出）` | `PackageCliDistribution` |
| `BuildJVMArtifacts.yml` | `build-mvn-artifact` | `BuildMvnArtifactLeg.yml` | `BuildMvnArtifactLeg` | `上传制品（Actions 制品仓库）` | `UploadArtifacts` |
| `BuildJVMArtifacts.yml` | `build-publish` | `BuildJVMArtifacts.yml` | `BuildPublish` | `下载全部制品` | `DownloadAllArtifacts` |
| `BuildJVMArtifacts.yml` | `build-publish` | `BuildJVMArtifacts.yml` | `BuildPublish` | `整理资产（没有产物也算正常，不失败）` | `CollectAssets` |
| `BuildJVMArtifacts.yml` | `build-publish` | `BuildJVMArtifacts.yml` | `BuildPublish` | `生成发布页校验和（SHA256SUMS）` | `GenerateChecksums` |
| `BuildJVMArtifacts.yml` | `build-publish` | `BuildJVMArtifacts.yml` | `BuildPublish` | `PreRelease-AHA-JVM` | `PublishPreRelease` |
| `CliNative.yml` | `plan` | `CliNative.yml` | `Plan` | `判定（取上下文 + 单行调用脚本产出 outputs）` | `ResolveSnapshotPlan` |
| `CliNative.yml` | `cli-native-version` | `CliNative.yml` | `CliNativeVersion` | `计算版本（a.b.c.PPPPP）` | `ComputeVersion` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageLeg.yml` | `CliNativeImageLeg` | `安装 GraalVM（JDK ${{ matrix.jdk }}）` | `InstallGraalVM` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageLeg.yml` | `CliNativeImageLeg` | `工具链不可用时的说明（本腿的其余步骤都会跳过）` | `ExplainToolchainUnavailable` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageLeg.yml` | `CliNativeImageLeg` | `计算本腿的 Maven 参数` | `ComputeMavenArgs` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageLeg.yml` | `CliNativeImageLeg` | `工具链自证（只记录，不阻塞）` | `VerifyToolchain` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageLeg.yml` | `CliNativeImageLeg` | `构建原生镜像` | `BuildNativeImage` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageLeg.yml` | `CliNativeImageLeg` | `确认 native-image 真的执行过（防「静默跳过」）` | `VerifyNativeImageRan` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageLeg.yml` | `CliNativeImageLeg` | `打独立报告包（发布物，与镜像包同族命名）` | `PackageBuildReport` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageLeg.yml` | `CliNativeImageLeg` | `上传报告包制品（与镜像包同路收集、同批发布）` | `UploadReportArtifact` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageLeg.yml` | `CliNativeImageLeg` | `产物自证（诊断式；判不出来也不阻塞）` | `VerifyArtifactDiagnostic` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageLeg.yml` | `CliNativeImageLeg` | `运行冒烟（产物能不能真跑；结论写摘要，不判死）` | `RunGuiSmoke` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageLeg.yml` | `CliNativeImageLeg` | `改名为发布用资产名` | `RenameToReleaseAsset` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageLeg.yml` | `CliNativeImageLeg` | `打包内清单（AHA-PACKAGE.json，issue #51 §6）` | `WritePackageManifest` |
| `CliNative.yml` | `cli-native-image` | `CliNativeImageLeg.yml` | `CliNativeImageLeg` | `上传制品` | `UploadArtifact` |
| `CliNative.yml` | `cli-native-publish` | `CliNative.yml` | `CliNativePublish` | `下载全部制品` | `DownloadAllArtifacts` |
| `CliNative.yml` | `cli-native-publish` | `CliNative.yml` | `CliNativePublish` | `整理资产（没有产物也算正常，不失败）` | `CollectAssets` |
| `CliNative.yml` | `cli-native-publish` | `CliNative.yml` | `CliNativePublish` | `决定发布载体（快照 ⇒ `<快照版本>-AHA-Cli-Native`；dev ⇒ `native-v` 族）` | `ResolveReleaseCarrier` |
| `CliNative.yml` | `cli-native-publish` | `CliNative.yml` | `CliNativePublish` | `生成发布页校验和（SHA256SUMS）` | `GenerateChecksums` |
| `CliNative.yml` | `cli-native-publish` | `CliNative.yml` | `CliNativePublish` | `PreRelease-AHA-Cli-Native-VPR` | `PublishPreRelease` |
| `CliNative.yml` | `comment` | `CliNative.yml` | `Comment` | `回写 PR 评论（幂等；逻辑与瞬时重试都在 .github/Python/SnapshotComment.py）` | `UpsertPullRequestComment` |
| `CodeQL.yml` | `analyze` | `CodeQL.yml` | `Analyze` | `初始化 CodeQL` | `InitCodeQL` |
| `CodeQL.yml` | `analyze` | `CodeQL.yml` | `Analyze` | `编译（CodeQL 需要真实编译产物）` | `CompileForCodeQL` |
| `CodeQL.yml` | `analyze` | `CodeQL.yml` | `Analyze` | `分析并上传结果` | `AnalyzeAndUpload` |
| `Compat.yml` | `compat` | `Compat.yml` | `Compat` | `安装 Maven 3.9.x（基线补丁版本）` | `InstallMavenCompat` |
| `Compat.yml` | `compat` | `Compat.yml` | `Compat` | `确认 Maven 版本为 3.9.x` | `AssertMavenVersion` |
| `Compat.yml` | `compat` | `Compat.yml` | `Compat` | `兼容基线完整 verify（Maven 3.9.x）` | `VerifyWithCompatMaven` |
| `DesktopNative.yml` | `plan` | `DesktopNative.yml` | `Plan` | `判定（取上下文 + 单行调用脚本产出 outputs）` | `ResolveSnapshotPlan` |
| `DesktopNative.yml` | `native-version` | `DesktopNative.yml` | `NativeVersion` | `计算版本（a.b.c.PPPPP）` | `ComputeVersion` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `安装 GraalVM（JDK ${{ matrix.jdk }}）` | `InstallGraalVM` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `工具链不可用时的说明（本腿的其余步骤都会跳过）` | `ExplainToolchainUnavailable` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `计算本腿的 Maven 参数` | `ComputeMavenArgs` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `工具链自证（门禁必须自证）` | `VerifyToolchain` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `构建原生镜像` | `BuildNativeImage` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `确认 native-image 真的执行过（防「静默跳过」）` | `VerifyNativeImageRan` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `打独立报告包（发布物，与镜像包同族命名）` | `PackageBuildReport` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `上传报告包制品（与镜像包同路收集、同批发布）` | `UploadReportArtifact` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `从构建报告里摘关键数字（写进摘要）` | `ExtractReportMetrics` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `产物自证（诊断式；判不出来也不阻塞）` | `VerifyArtifactDiagnostic` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `运行冒烟（GUI：Windows 腿真启动，验证「界面真的装起来了」）` | `RunGuiSmoke` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `改名为发布用资产名` | `RenameToReleaseAsset` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `打包内清单（AHA-PACKAGE.json，issue #51 §6）` | `WritePackageManifest` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `自证：Windows 10 兼容清单已嵌进 exe（issue #151）` | `VerifyWindows10Manifest` |
| `DesktopNative.yml` | `native-image` | `NativeImageLeg.yml` | `NativeImageLeg` | `上传制品` | `UploadArtifact` |
| `DesktopNative.yml` | `native-publish` | `DesktopNative.yml` | `NativePublish` | `下载全部制品` | `DownloadAllArtifacts` |
| `DesktopNative.yml` | `native-publish` | `DesktopNative.yml` | `NativePublish` | `整理资产（没有产物也算正常，不失败）` | `CollectAssets` |
| `DesktopNative.yml` | `native-publish` | `DesktopNative.yml` | `NativePublish` | `决定发布载体（快照 ⇒ `<快照版本>-AHA-Desktop-Native`；dev ⇒ `native-v` 族）` | `ResolveReleaseCarrier` |
| `DesktopNative.yml` | `native-publish` | `DesktopNative.yml` | `NativePublish` | `生成发布页校验和（SHA256SUMS）` | `GenerateChecksums` |
| `DesktopNative.yml` | `native-publish` | `DesktopNative.yml` | `NativePublish` | `PreRelease-AHA-Desktop-Native-VPR` | `PublishPreRelease` |
| `DesktopNative.yml` | `comment` | `DesktopNative.yml` | `Comment` | `回写 PR 评论（幂等；逻辑与瞬时重试都在 .github/Python/SnapshotComment.py）` | `UpsertPullRequestComment` |
| `Gate.yml` | `gate` | `Gate.yml` | `Gate` | `校验构建用的是 Wrapper 固定的 Maven 版本` | `AssertWrapperMavenVersion` |
| `Gate.yml` | `gate` | `Gate.yml` | `Gate` | `安装 bubblewrap（Agent 执行边界自证用）` | `InstallBubblewrap` |
| `Gate.yml` | `gate` | `Gate.yml` | `Gate` | `Agent 执行边界自证（域内无 Maven/JDK、/mnt 不可见；隔离一失效自证即红）` | `VerifyAgentBoundary` |
| `Gate.yml` | `gate` | `Gate.yml` | `Gate` | `完整构建与覆盖率门禁（LINE COVEREDRATIO ≥ 0.70）` | `VerifyWithCoverageGate` |
| `Gate.yml` | `gate` | `Gate.yml` | `Gate` | `版本号一致性（version / POM / version.properties / 产物名 / tag 名）` | `VerifyVersionConsistency` |
| `Gate.yml` | `gate` | `Gate.yml` | `Gate` | `覆盖率实测值（门禁已由上一步判定，这里把数字打进日志）` | `ReportCoverageValues` |
| `Gate.yml` | `gate` | `Gate.yml` | `Gate` | `文档围栏与链接` | `CheckDocs` |
| `Gate.yml` | `gate` | `Gate.yml` | `Gate` | `技能符合 Agent Skills 规范` | `CheckSkills` |
| `Gate.yml` | `gate` | `Gate.yml` | `Gate` | `脚本编码与行尾` | `CheckScripts` |
| `Gate.yml` | `gate` | `Gate.yml` | `Gate` | `主题色号来自色表` | `CheckTheme` |
| `Gate.yml` | `gate` | `Gate.yml` | `Gate` | `像素标志与生成器一致` | `VerifyPixelLogo` |
| `Gate.yml` | `gate` | `Gate.yml` | `Gate` | `重复代码率：生成 CPD 报告` | `GenerateCpdReport` |
| `Gate.yml` | `gate` | `Gate.yml` | `Gate` | `重复代码率：判定阈值` | `CheckDuplicationRate` |
| `PreDeployVerify.yml` | `predeploy-verify` | `PreDeployVerify.yml` | `PreDeployVerify` | `验证脚本的自检（脚本自带断言，不需要产物）` | `SelfTestVerifyScript` |
| `PreDeployVerify.yml` | `predeploy-verify` | `PreDeployVerify.yml` | `PreDeployVerify` | `构建 CLI 产物（验证跑在**真实产物**上，不是源码）` | `BuildCliArtifact` |
| `PreDeployVerify.yml` | `predeploy-verify` | `PreDeployVerify.yml` | `PreDeployVerify` | `跑部署前验证` | `RunPreDeployVerify` |
| `PreDeployVerify.yml` | `predeploy-verify` | `PreDeployVerify.yml` | `PreDeployVerify` | `打 commit status（context=predeploy-verify）` | `PostCommitStatus` |
| `PreDeployVerify.yml` | `predeploy-verify` | `PreDeployVerify.yml` | `PreDeployVerify` | `幂等回写留痕评论（对象 / 结论 / 执行人 / 时间 / 提交）` | `UpsertTraceComment` |
| `PreDeployVerify.yml` | `predeploy-verify` | `PreDeployVerify.yml` | `PreDeployVerify` | `未过时让作业变红（非必需检查，不影响合并）` | `FailJobWhenNotVerified` |
| `Release.yml` | `release` | `Release.yml` | `CliReleasePackage` | `Build and verify` | `BuildAndVerify` |
| `Release.yml` | `release` | `Release.yml` | `CliReleasePackage` | `Package CLI distribution` | `PackageCliDistribution` |
| `Release.yml` | `release` | `Release.yml` | `CliReleasePackage` | `Upload release assets` | `UploadReleaseAssets` |
| `Release.yml` | `desktop` | `DesktopPackageLeg.yml` | `DesktopPackageLeg` | `Build and verify` | `BuildAndVerify` |
| `Release.yml` | `desktop` | `DesktopPackageLeg.yml` | `DesktopPackageLeg` | `自证平台分类器（包名 → 下载文件名）` | `VerifyPlatformClassifier` |
| `Release.yml` | `desktop` | `DesktopPackageLeg.yml` | `DesktopPackageLeg` | `便携包完整性自证（平铺入口 + 单体 jar + 本平台原生库）` | `VerifyPackageIntegrity` |
| `Release.yml` | `desktop` | `DesktopPackageLeg.yml` | `DesktopPackageLeg` | `打印本平台解析到的 OpenJFX 工件（自证）` | `PrintOpenJfxArtifacts` |
| `Release.yml` | `desktop` | `DesktopPackageLeg.yml` | `DesktopPackageLeg` | `Upload release assets` | `UploadReleaseAssets` |
| `Release.yml` | `checksums` | `Release.yml` | `Checksums` | `下载本 tag 已发布的资产` | `DownloadReleasedAssets` |
| `Release.yml` | `checksums` | `Release.yml` | `Checksums` | `资产齐全自证（缺任何一个平台的包就判失败，不允许「部分发布」静默变绿）` | `VerifyAllAssetsPresent` |
| `Release.yml` | `checksums` | `Release.yml` | `Checksums` | `生成并挂上 SHA256SUMS` | `GenerateAndUploadChecksums` |
| `SnapshotAssert.yml` | `snapshot-assert` | `SnapshotAssert.yml` | `SnapshotAssert` | `解析命令 / 校验权限与版本归属（任何拒绝都只回复用法，不打 status）` | `ParseCommandAndCheckPermissions` |
| `SnapshotAssert.yml` | `snapshot-assert` | `SnapshotAssert.yml` | `SnapshotAssert` | `打 commit status（context=snapshot-assert）` | `PostCommitStatus` |
| `SnapshotAssert.yml` | `snapshot-assert` | `SnapshotAssert.yml` | `SnapshotAssert` | `幂等回写留痕评论（marker + 瞬时重试都在 SnapshotComment.py 里）` | `UpsertTraceComment` |
| `SnapshotCleanup.yml` | `sweep` | `SnapshotCleanup.yml` | `Sweep` | `清扫（判定与执行都在 .github/Python/SnapshotCleanup.py）` | `RunSnapshotCleanup` |
| `SnapshotJVM.yml` | `plan` | `SnapshotJVM.yml` | `Plan` | `算出 PR 号（三种触发方式各自取法）` | `ResolvePullRequestNumber` |
| `SnapshotJVM.yml` | `plan` | `SnapshotJVM.yml` | `Plan` | `取本 PR 的改动清单` | `FetchChangedFiles` |
| `SnapshotJVM.yml` | `plan` | `SnapshotJVM.yml` | `Plan` | `判定（逻辑在 .github/Python/SnapshotPlan.py）` | `RunSnapshotPlan` |
| `SnapshotJVM.yml` | `publish` | `SnapshotJVM.yml` | `Publish` | `下载构建制品` | `DownloadArtifacts` |
| `SnapshotJVM.yml` | `publish` | `SnapshotJVM.yml` | `Publish` | `规范资产名（#165 §八：AHA-JVM-<版本>-<平台>-jdk25.zip）` | `NormalizeAssetNames` |
| `SnapshotJVM.yml` | `publish` | `SnapshotJVM.yml` | `Publish` | `按目标创建 / 更新 pre-release（tag = V<版本>-AHA-<目标>-SNAPSHOT）` | `UpsertPreRelease` |
| `SnapshotJVM.yml` | `comment` | `SnapshotJVM.yml` | `Comment` | `回写 PR 评论（幂等；逻辑与瞬时重试都在 .github/Python/SnapshotComment.py）` | `UpsertPullRequestComment` |
| `VersionBump.yml` | `bump` | `VersionBump.yml` | `Bump` | `解析目标版本（唯一读取入口）` | `ResolveTargetVersion` |
| `VersionBump.yml` | `bump` | `VersionBump.yml` | `Bump` | `分发到所有版本位置（POM / version 文件 / 代码常量 / 文档声明）` | `DistributeVersionEverywhere` |
| `VersionBump.yml` | `bump` | `VersionBump.yml` | `Bump` | `自检（文档围栏 / 脚本编码行尾 / 版本读取）` | `RunSelfChecks` |
| `VersionBump.yml` | `bump` | `VersionBump.yml` | `Bump` | `说明验证时机（PR 上不跑 checks，合并到 dev 后自动验证）` | `ExplainVerificationTiming` |
| `VersionBump.yml` | `bump` | `VersionBump.yml` | `Bump` | `开 PR（分支 chore/bump-<版本>，base dev）` | `OpenPullRequest` |

### 3.3 commit status context（一行一项）

| 旧 | 新 |
|---|---|
| `snapshot-assert` | `SnapshotAssert` |
| `predeploy-verify` | `PreDeployVerify` |

### 3.4 平台标签（一行一项）

| 位置 | 旧 | 新 |
|---|---|---|
| 矩阵 `label`（`DesktopNative.yml`） | `macos-arm64` | `macos-aarch64` |
| 矩阵 `label`（`CliNative.yml`） | `macos-arm64` | `macos-aarch64` |
| 矩阵 `label`（`BuildJVMArtifacts.yml`） | `macos-arm64` | `macos-aarch64` |
| 矩阵 `label`（`Release.yml`） | `macos-arm64` | `macos-aarch64` |
| 资产名 | `…-macos-arm64.zip` | `…-macos-aarch64.zip` |
| 读取兼容 | — | 旧名 `macos-arm64` 仍被接受 |

## 4. 守卫

| 守卫 | 断言 |
|---|---|
| `bin/CheckScripts.py::check_workflow_names()` | 文件名 ↔ 顶层 `name:` 大驼峰且一一对应 |
| `bin/CheckScripts.py::check_workflow_display_names()` | 所有 `name:` / `run-name:` 静态英文大驼峰（本规范 §2.1） |
| `bin/CheckScripts.py::check_release_channel_contract()` | 平台标签与资产名在 PowerShell / Python / Java 三处一致 |
| `bin/CheckScripts.py::check_snapshot_assert_contract()` | `SnapshotAssert` context 与守卫、文档一致 |
| `bin/CheckScripts.py::check_pre_deploy_verify_contract()` | `PreDeployVerify` context 与升级路径一致 |

反向验证要求（改守卫时必须做）：

- 临时把一个 `name:` 改成中文 ⇒ 必须判红；
- 临时把一个 `name:` 改成 `Foo${ matrix.os }` ⇒ 必须判红；
- 两轮都恢复后转绿。

## 5. 关联

- `Docs/DevSpec/GitHubActionsSpec.md` §2（文件命名与显示名）、§5（context）
- `Docs/DevSpec/BuildSpec.md` §8.1（作业名是分支保护的契约）
- `Docs/DevSpec/DocumentationSpec.md` §6（清单与映射的书写体例）
- issue [#287](https://github.com/ACANX/AHA/issues/287)（本规范的来源）
