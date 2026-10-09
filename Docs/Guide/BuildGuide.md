# 构建指南

**文档版本**：v1.10.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-09
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 由 `Docs/DevSpec/BuildGuide.md` 拆分而来，承接其中的操作步骤；合并原排错指南中的构建期问题 | @ACANX |
| v1.1.0 | 2026-10-06 | 新增构建发行包（`Dist/`）说明 | @ACANX |
| v1.2.0 | 2026-10-06 | 新增「运行中的 AHA 锁定 `Dist/lib` 导致 assembly 失败」排错项 | @ACANX |
| v1.3.0 | 2026-10-07 | 新增「依赖自动升级」章节（Dependabot 每日检测，PR 先合入 `dependa` 分支） | @ACANX |
| v1.4.0 | 2026-10-07 | 新增「按变更范围选择检查」（`bin/CheckChanged.py`）与耗时量级参考 | @ACANX |
| v1.5.0 | 2026-10-07 | 排错项更正根因：最常见占用者是 **IDEA 的 Maven server**（非仅运行中的 AHA）；补三种占用者的识别与处置、定位命令，并说明不要用 `-Dassembly.skipAssembly` 绕过 | @ACANX |
| v1.6.0 | 2026-10-07 | 补记「IDEA 会在文件变化时自动重生 Maven server 并重新锁定 `Dist/lib`」，给出构建窗口期的处置建议 | @ACANX |
| v1.7.0 | 2026-10-07 | §3.2 补「完整门禁不在每次改动后跑」：列出合入前自查的五类命令与重复率口径 | @ACANX |
| v1.8.0 | 2026-10-07 | §3.2 补充慢检查的触发时机（含每周定期扫描）与 Maven 3.9.x 兼容验证（Compat.yml）及本地命令 | @ACANX |
| v1.9.0 | 2026-10-07 | §5 补终端查看覆盖率汇总的方式（`bin/ReportCoverage.py`）并说明门禁静默、脚本只报数不判定 | @ACANX |
| v1.10.0 | 2026-10-09 | §3.1.1 新增桌面端便携包的一键构建与更新脚本（`Script/Python/DesktopDistBuild.py`、`DesktopDistExtract.py`）与命令速查 `Docs/Guide/CommandCheatsheet.md`（落地为 `Dist/README.commands.md`，随 `mvn package` 重建） | @ACANX |

---

## 1. 适用范围

本指南说明 AHA 的**构建操作**，属于用户指南（`Docs/Guide/`）。

**强制要求**（版本锁定、双版本验证、覆盖率门禁）见 [BuildSpec.md](../DevSpec/BuildSpec.md)。

## 2. 前置准备

| 组件 | 版本 |
|---|---|
| JDK | 25 (LTS)；另有可选的 JDK 27 编译变体（见 `BuildSpec.md` §2，issue #65） |
| Maven 运行时 | 4.x（Maven Wrapper 固定，无需单独安装） |
| Maven 兼容基线 | 3.9.x（仅兼容验证时需要） |

确认环境：

```bash
java -version          # 应输出 25.x
./mvnw -v              # 应输出 Apache Maven 4.x
```

## 3. 构建命令

```bash
./mvnw clean verify          # Maven 4 运行时（推荐）
mvn clean verify             # Maven 3.9.x 兼容验证
```

`verify` 会依次执行：编译 → 测试 → 覆盖率报告 → 覆盖率门禁。

### 3.1 构建发行包

`package` 阶段会自动组装发行包到项目根 `Dist/`：

> 桌面端便携包（`aha-desktop-<版本>-<平台>.zip`）与两套原生镜像包
> （`aha-desktop-native-<版本>-<jdk>.zip`、`aha-cli-native-<版本>-<jdk>.zip`）
> 同样落在 `Dist/`——**仓库根不放构建物**，所有 `.zip` 产物统一进 `Dist/`。
>
> 原生镜像包**不在默认构建里**：它们分别需要 `-Pdesktop-native` / `-Pcli-native`
> 与 GraalVM 的 `native-image`，详见 [DesktopNativeDesign.md](../Design/DesktopNativeDesign.md)
> 与 [CliNativeDesign.md](../Design/CliNativeDesign.md)。

```bash
./mvnw clean package
```

```
Dist/
├── bin/     Aha.sh、Aha.bat
├── lib/     JPMS 模块路径（本项目模块 + 全部运行时依赖）
├── README.md、LICENSE、CHANGELOG.md
```

验证：

```bash
./Dist/bin/Aha.sh version     # Linux / macOS
Dist\bin\Aha.bat version      # Windows
```

由 `maven-assembly-plugin` 与 `aha-cli/src/assembly/dist.xml` 驱动。
`Dist/` 已在 `.gitignore` 中忽略；`mvn clean` 会清空其中的构建产物（保留目录）。
这一步不可或缺：assembly 只覆盖同名文件，依赖升级后旧版本的 jar 会残留，
而 JPMS 下一个模块出现两个版本是致命错误（`java.lang.module.FindException`），
发行包会直接启不来。

### 3.1.1 一键构建与更新 Dist/（`Script/Python/`）

桌面端便携包解压后的 `bin/` + `lib/` 与 CLI 发行包同落在项目根 `Dist/`。手工出包要记住
`-pl aha-desktop -am package` 一串参数、再手动解压覆盖，容易漏；两个脚本各管一步：

```bat
python3 Script\Python\DesktopDistBuild.py      # 构建 Dist\aha-desktop-<版本>-<平台>.zip
python3 Script\Python\DesktopDistExtract.py    # 解压并把 lib/ 与 bin/ 更新进 Dist/
```

```bash
python3 Script/Python/DesktopDistBuild.py       # POSIX 下把路径分隔符换成 `/`
python3 Script/Python/DesktopDistExtract.py
```

- `DesktopDistBuild.py`：以 `./mvnw -pl aha-desktop -am package -DskipTests -Djacoco.skip=true`
  出包，并**自证产物**——包内必须有 aha-desktop 模块、**恰好 3 个**本平台 OpenJFX jar
  与两个启动脚本；“构建成功但包是空的 / 少了平台原生库”会当场失败。
  加 `--with-tests` 可先跑测试，加 `--clean` 可先清理。
- `DesktopDistExtract.py`：默认取**本平台最新**的包（也可用 `--zip` 指定），先解压到暂存目录、
  全部成功后再落地，并清掉 `lib/` 里上一版桌面端独占的 jar（`aha-desktop-*.jar` / `javafx-*.jar`）
  ——不清会让 JPMS 模块路径上出现同一模块的两个版本（`java.lang.module.FindException`）。
  共享依赖与仅属于 CLI 的 `aha-cli-*.jar` 不受影响，因此 `Dist/` 可同时保留 CLI 与桌面端用法。

更新后直接运行：

```bat
Dist\bin\AhaDesktop.bat          # Windows
Dist\bin\Aha.bat version          # CLI 仍可用
```

```bash
./Dist/bin/AhaDesktop.sh          # Linux / macOS
```

命令速查：`Dist/README.commands.md`（源文件 `Docs/Guide/CommandCheatsheet.md`）把编译 / 更新 /
解压 / 启动的常用命令集中到一处。它由 CLI assembly 随 `mvn package` 写回，也由
`DesktopDistExtract.py` 刷新——因此 `mvn clean` 删掉 `Dist/*.md` 后会自动重建，不需手动维护。

两个脚本都不做质量门禁：门禁（`clean verify`、覆盖率、CI）仍由 Maven 与 `Gate.yml` 负责，
它们只负责「把包做出来并铺到该在的位置」。同族的原生镜像取件脚本见
[DesktopNativeUpdateDesign.md](../Design/DesktopNativeUpdateDesign.md)。

### 3.2 按变更范围选择检查

三个 `Check*.py` 各覆盖一类文件，`verify` 则是分钟级。用 `bin/CheckChanged.py`
按实际改了什么决定跑什么（缺省读 git 工作区变更）：

```bash
python3 bin/CheckChanged.py                 # 按 git 变更自动判定
python3 bin/CheckChanged.py Docs/README.md  # 按给定路径判定
python3 bin/CheckChanged.py --dry-run       # 只打印计划
python3 bin/CheckChanged.py --all           # 无条件全部执行
```

量级参考（本项目在 WSL `/mnt/e` 下实测；慢文件系统上差异明显）：

| 场景 | 命令 | 量级 |
|---|---|---|
| 仅文档 | `CheckChanged.py <md>` | 亚秒 |
| 仅脚本 | `CheckChanged.py <py>` | 亚秒 |
| 实现代码 | `./mvnw -pl aha-cli -am test -Djacoco.skip=true` | 约 1 分钟 |

**完整门禁不在每次改动后跑**。覆盖率门禁、完整 `clean verify`、文档检查、技能检查、
脚本检查与重复率检查都属于慢检查，已集中到 CI 的 `Gate.yml`——它在**合入 `main` /
`release/**` 前**、**每周定期**、手动触发与发布前运行。这类检查适合异步：要拦的是
「与开发动作无关的漂移」（依赖被 Dependabot 升级、runner 镜像变化、外部规范演进），
不必在写完一个特性后就触发。完整设计见
[BuildSpec.md](../DevSpec/BuildSpec.md) 第 8.1 节。

Maven 3.9.x 兼容性由独立的 `Compat.yml` 验证（同属卡点，且是发布前置）：它固定一个
3.9.x 补丁版本，不使用 runner 预装的 `mvn`，以保证结论可复现。`Gate.yml` 的第一步会
断言实际使用的 Maven 版本与 `.mvn/wrapper/maven-wrapper.properties` 一致——
「门禁跑的到底是哪一版 Maven」不靠推断。

本地在合入前自查时按下面执行：

```bash
./mvnw -B clean verify                 # 构建 + 测试 + 覆盖率门禁（≥ 70%）
python3 bin/CheckDocs.py               # 文档围栏与链接
python3 bin/CheckSkills.py             # 技能符合 Agent Skills 规范
python3 bin/CheckScripts.py            # 脚本编码与行尾
python3 bin/GenPixelLogo.py --verify   # 像素标志与生成器一致
./mvnw -B pmd:cpd && python3 bin/CheckDuplication.py   # 重复率（默认阈值 2.0%）
```

重复率口径：重复行数 = Σ 每个 duplication 块 `(出现次数 − 1) × 块行数`，
总行数为各模块 `src/main/java` 下 `*.java` 的物理行数；只统计主源码。
报告缺失时 `CheckDuplication.py` 直接失败，不做静默跳过。

兼容基线（POM 语法不得越界；见 [BuildSpec.md](../DevSpec/BuildSpec.md) 第 4 节）：

```bash
mvn -B clean verify            # 用本机的 Maven 3.9.x；CI 由 Compat.yml 承担
```
| 完整验收 | `./mvnw clean verify` | 3~5 分钟 |

何时**必须**跑完整 `verify` 见 [BuildSpec.md](../DevSpec/BuildSpec.md) 第 8 节。
CI 不做这个裁剪——CI 机器上三个检查加起来不到 2 秒，没必要省。

## 4. 单模块构建

```bash
# 构建某模块及其依赖
./mvnw -pl aha-core -am clean install

# 仅跑某模块测试
./mvnw -pl aha-core test

# 跳过测试快速编译
./mvnw -q clean install -DskipTests -Djacoco.skip=true
```

> 提示：`-DskipTests` 会跳过测试，因而**不产出覆盖率数据**；
> 若同时触发 `verify`，需加 `-Djacoco.skip=true` 避免门禁误报。

## 5. 查看覆盖率报告

除了打开 HTML 报告，也可以直接在终端看汇总（数字与 `TestingSpec.md` §3.3 的口径一致）：

```bash
./mvnw clean verify                  # 先生成报告（并执行覆盖率门禁）
python3 bin/ReportCoverage.py        # 各模块 + 合计的行/分支覆盖率
```

> JaCoCo 的门禁（`jacoco:check`）**通过时不打印百分比**，日志里看不出是否生效；
> `ReportCoverage.py` 只报数、不判定，阈值从 `pom.xml` 读取。

`verify` 阶段自动生成报告并执行门禁检查：

```bash
./mvnw clean verify
# 报告：<module>/target/site/jacoco/index.html
# 门禁：BUNDLE 级 LINE COVEREDRATIO ≥ 0.70
```

无测试数据的模块（如 `aha-desktop`）自动跳过，不参与门禁。

## 6. 生成 Javadoc

```bash
./mvnw javadoc:javadoc           # 单模块
./mvnw javadoc:aggregate         # 全量聚合（需先 install）

# 输出：<module>/target/reports/apidocs/index.html
```

配置：`maven-javadoc-plugin (javadoc.plugin.version)`，`doclint=none`、`failOnError=false`、`locale=zh_CN`；
不绑定生命周期，避免拖慢日常 `verify`。

## 7. 构建期常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| `release version 25 not supported` | JDK 版本过低 | 升级到 JDK 25（LTS） |
| `module not found` / `Module ... not found` | JPMS 配置错误 | 检查 `module-info.java` 的 `requires` 与运行参数 `--module-path` |
| `Unable to make field ... accessible`（picocli） | JPMS 下反射受限 | `module-info` 添加 `opens <pkg> to info.picocli;` |
| `package com.sun.net.httpserver is not visible` | 测试在 module-path 上编译，未读取 `jdk.httpserver` | 改用自包含 `MiniHttpServer`（见 `DefaultLlmClientTest`），不向 `module-info` 添加测试专用依赖 |
| `ServiceLoader` 找不到工具 / 适配器 | classpath 模式下 `module-info` 的 `provides` 不生效 | 同时提供 `META-INF/services` 文件（JPMS 与 classpath 双兼容） |
| `FAIL_ON_NULL_FOR_PRIMITIVES` | Jackson 3 默认拒绝 `null` → `int` | `ConfigLoader` 显式 `disable`，或契约字段改用包装类型 |
| `SQLITE_CANTOPEN` | 数据库父目录不存在 | `SqliteMemoryStore.init()` 先 `Files.createDirectories` |
| `Unsupported class file major version` | JaCoCo 版本落后于 JDK | 升级父 POM 的 `jacoco.version` |
| `Coverage checks have not been met` | 覆盖率低于门禁阈值 | 补测试；确有必要排除时需登记理由（见 `BuildSpec.md` 第 6 节） |
| Maven 4 报语法错误 | 使用了 Maven 4 专有语法 | 改回 Maven 3.9.x 兼容写法（见 `BuildSpec.md` 第 5 节） |
| `Failed to create assembly: Problem copying files : .../Dist/lib/xxx.jar` | **有进程占着 `Dist/lib` 下的 jar**（或 `Dist/bin` 目录）。Windows 不允许覆盖被占用的文件；Linux 可以 unlink 已打开文件，故只在 Windows / WSL 访问 Windows 盘时出现 | 见下方「关于 `Dist/`」的占用者清单与处置 |

> **关于 `Dist/`**：发行包输出到**项目根**的 `Dist/`（而非 `target/`），因此 `./mvnw clean`
> **不会**删除它。已存在的 `Dist/` 本身**不影响构建**（assembly 会覆盖同名文件）；
> 只有其中的文件（或 `Dist/bin` 目录）被别的进程占用时才会失败。
>
> 实测过的三种占用者，**第一种最常见，也最容易被误判成「AHA 还开着」**：
>
> | 占用者 | 怎么认 | 怎么放 |
> |---|---|---|
> | **IDEA 的 Maven server** | Windows 侧命令行含 `org.jetbrains.idea.maven.server.RemoteMavenServer36`；长期驻留，不会自己退出 | `taskkill /PID <pid> /F`。IDEA 需要时会自动重启该辅助进程；必要时在 IDEA 里执行一次「Reload Maven Project」 |
> | 运行中的 AHA 会话 | `java.exe` 命令行含 `com.acanx.module.aha.cli` | 关闭 `chat` 会话（会话已持久化，可用 `aha chat --session <id>` 续接） |
> | 把 `Dist/bin` 当工作目录的终端窗口 | 文件都能改，但删**目录**报 `The process cannot access the file` | 关闭该终端窗口 |
>
> 定位（Windows 侧，从 WSL 同样可执行）：
>
> ```bat
> tasklist | findstr /I java
> powershell -NoProfile -Command "(Get-CimInstance Win32_Process -Filter 'ProcessId=<pid>').CommandLine"
> ```
>
> 处置：结束占用者后**直接重跑 `mvn clean verify` 即可**——assembly 会覆盖同名文件，
> 不必先删 `Dist/`。确实要清空时用 Windows 侧命令删目录：
> `cmd.exe /c "rmdir /s /q E:\GitRepo\GitHub\ACANX\AHA\Dist"`；
> WSL 的 `rm -rf` / `mv` 对 9p 上的目录常报 `Permission denied`，属已知限制。
>
> **注意 IDEA 会自动重生这个进程**：项目里有文件变化（尤其 `pom.xml`）时，IDEA 会重新导入
> Maven 项目，于是辅助进程又起来、又把 `Dist/lib` 锁上。实测连续两轮都出现这种情况。
> 因此**打包 / 发版前先结束它、并避免在 IDE 里触发重新导入**；结束后的窗口期内完成构建最省事。
>
> **不要**用 `-Dassembly.skipAssembly=true` 绕过：那会跳过发行包组装，`Dist/` 停在旧版本，
> 而 JPMS 下一个模块出现两个版本会让发行包直接启不来。

## 8. 依赖自动升级

依赖更新由 Dependabot 自动检测并提交 PR，配置见 `.github/dependabot.yml`。

| 生态 | 覆盖范围 | 检测频率 |
|---|---|---|
| `maven` | `pom.xml`（版本集中在父 POM 的 `<properties>`） | 每日 09:00（Asia/Shanghai） |
| `github-actions` | `.github/workflows/*.yml` 中 `uses:` 引用的 action | 每日 09:00（Asia/Shanghai） |

**合入路径**：

Dependabot 的 PR **只指向 `dependa` 分支**（配置里的 `target-branch`），不会直接打到默认分支：

```
dependabot/xxx ──PR──▶ dependa ──PR（人工发起）──▶ 默认分支
```

**处理方式**：

1. PR 打开后先看 CI 是否全绿（双 Maven 版本 + 覆盖率门禁），这与人工 PR 的要求一致
2. `minor` / `patch` 已按生态合并为单个 PR，确认无破坏性变更即可合入 `dependa`
3. `major` 不并入分组，逐个单独提交，需先阅读上游变更说明再决定
4. `dependa` 上积累若干升级并验证稳定后，按正常流程提 PR 合入默认分支
5. 手动触发检测：仓库 **Insights → Dependency graph → Dependabot**，点 **Check for updates**

**首次启用前需准备**：

1. `.github/dependabot.yml` 必须位于**默认分支**（Dependabot 只从默认分支读取配置）
2. 创建并推送 `dependa` 分支，且其中包含 `pom.xml` 与 `.github/workflows/`
   —— 指定 `target-branch` 后，Dependabot 读取的是**该分支**上的清单文件
3. `dependa` 需长期保留，不要随版本发布删除

```bash
git switch -c dependa          # 从默认分支创建
git push -u origin dependa
```

**不在覆盖范围内**：

- **Maven Wrapper 版本**（`.mvn/wrapper/maven-wrapper.properties`）由 [BuildSpec.md](../DevSpec/BuildSpec.md)
  第 3 节锁定，需手工升级并同步更新该节示例
- **JDK 版本**（第 2 节工具链锁定），JDK 25 为 LTS，升级需走规范变更程序

> 前提：Dependabot 只在**默认分支**上生效，`.github/dependabot.yml` 必须先合入默认分支。

## 9. 相关文档

| 文档 | 用途 |
|---|---|
| [BuildSpec.md](../DevSpec/BuildSpec.md) | 构建的强制要求 |
| [TestingSpec.md](../DevSpec/TestingSpec.md) | 测试层级与覆盖率要求 |
| [TroubleshootingGuide.md](TroubleshootingGuide.md) | 运行时与配置问题排错 |
| [GettingStarted.md](GettingStarted.md) | 环境准备与首次运行 |
