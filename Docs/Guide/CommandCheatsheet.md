# AHA 常用命令速查

> **这是 `Dist/README.commands.md`**（`Docs/Guide/CommandCheatsheet.md` 的副本，已纳入版本控制）。
> 内容由 CLI assembly 随 `mvn package` 写回，或由 `Script/Python/DesktopDistExtract.py` 刷新；
> **要改内容请改源文件 `Docs/Guide/CommandCheatsheet.md`**，本文件会被覆盖，直接改会丢。
> `Dist/README.md` 是项目 README，不是本文件。
>
> 作用：把「编译 / 更新 / 解压 / 启动」的命令集中到一处，避免记岔脚本名与参数。
> 除特别说明外，命令一律在**仓库根**执行；Windows 路径用 `\`，Linux / macOS 用 `/`。

## 0. 前提

| 项 | 要求 |
|---|---|
| JDK | **25** |
| 构建工具 | Maven Wrapper（`mvnw.cmd` / `./mvnw`），无需另外安装 Maven |
| 平台 | 桌面端便携包按平台出包，包内含**本平台**的 OpenJFX 原生库 |
| 命令名 | 本机若命令是 `python` 而非 `python3`，替换即可 |

> **Agent 约束**：本速查里的 `mvnw` 命令供人类开发者构建发行包；Agent 不在本地执行
> 编译 / 测试，以 PR 的 CI checks 为准（见仓库根 `AGENTS.md`）。

## 1. 脚本一览（最常用）

| 想做的事 | Windows | Linux / macOS |
|---|---|---|
| 编译桌面端便携包 | `python3 Script\Python\DesktopDistBuild.py` | `python3 Script/Python/DesktopDistBuild.py` |
| 解压并更新 `Dist/` | `python3 Script\Python\DesktopDistExtract.py` | `python3 Script/Python/DesktopDistExtract.py` |
| 下载最新桌面端原生镜像 | `Dist\UpdateDesktopNative.cmd`（双击，无需 Python）或 `python3 Script\Python\DesktopNativeVersionUpdate.py` | `python3 Script/Python/DesktopNativeVersionUpdate.py` |
| 下载最新 CLI 原生镜像 | `Dist\UpdateCliNative.cmd`（双击，无需 Python）或 `python3 Script\Python\CliNativeVersionUpdate.py` | `python3 Script/Python/CliNativeVersionUpdate.py` |
| 下载最新**桌面端 JVM 便携包** | `Dist\UpdateDesktopJvm.cmd`（双击，无需 Python） | 手动从发布页取 `aha-desktop-<版本>-<平台>.zip` |
| 下载最新 **CLI JVM 便携包** | `Dist\UpdateCliJvm.cmd`（双击，无需 Python） | 手动从发布页取 `aha-cli-<版本>.zip` |
| 启动桌面端（Dist 版） | `Dist\AhaDesktop.bat` | `./Dist/AhaDesktop.sh` |
| 启动桌面端（源码根） | `bin\AhaDesktop.bat` | `./bin/AhaDesktop.sh` |
| 启动 CLI（Dist 版） | `Dist\AhaCli.bat` | `./Dist/AhaCli.sh` |
| 启动**桌面端原生镜像** | `Dist\aha-desktop-native.exe` | `./Dist/aha-desktop-native` |
| 启动 **CLI 原生镜像** | `Dist\aha-cli-native.exe` | `./Dist/aha-cli-native` |
| 按变更跑检查 | `python3 bin\CheckChanged.py` | `python3 bin/CheckChanged.py` |

> 桌面端自 issue #218 起是**包根平铺**形态（单体 jar `aha-desktop.jar` + 启动脚本），
> 不再有 `lib/` 与 `bin/`。`bin\AhaDesktop.*` / `bin/AhaDesktop.sh` 在源码根运行时，
> 若同目录（或上一级）找不到 `aha-desktop.jar`，会自动挑本平台的 `Dist/aha-desktop-*.zip`
> 解压后再启动；无需先手动解压。

## 2. 桌面端（JVM 便携包）

### 2.1 编译便携包

```bat
python3 Script\Python\DesktopDistBuild.py
python3 Script\Python\DesktopDistBuild.py --clean --with-tests
python3 Script\Python\DesktopDistBuild.py --javafx-platform linux
python3 Script\Python\DesktopDistBuild.py --dry-run
```

产物：`Dist\aha-desktop-<版本>-<平台>.zip`（默认跳过测试；构建后自动自证包内容）。

等价的 Maven 命令（不想用脚本时）：

```bat
mvnw.cmd -pl aha-desktop -am package -DskipTests -Djacoco.skip=true
```

### 2.2 解压并更新 `Dist/`

```bat
python3 Script\Python\DesktopDistExtract.py
python3 Script\Python\DesktopDistExtract.py --zip Dist\aha-desktop-0.1.1-win.zip
python3 Script\Python\DesktopDistExtract.py --dry-run
python3 Script\Python\DesktopDistExtract.py --no-prune
```

效果：把 zip 里**平铺**的 `aha-desktop.jar` 与两个启动脚本铺进 `Dist/`，并清掉上一版
（`lib/` + `bin/` 形态）的遗留文件（`lib/aha-desktop-*.jar`、`lib/javafx-*.jar`、
`bin/AhaDesktop.*`），完成后 `Dist\AhaDesktop.bat` 即可用。
属于 CLI 的 `aha-cli.jar` 与 `Aha.*` 不受影响，所以 `Dist/` 同时保留 CLI 用法。

### 2.3 启动桌面端

```bat
Dist\AhaDesktop.bat              :: 跑 Dist\aha-desktop.jar（需先执行 2.2 或 2.1+解压）
bin\AhaDesktop.bat               :: 源码根：找不到单体 jar 时自动解压便携包
```

```bash
./Dist/AhaDesktop.sh
./bin/AhaDesktop.sh
```

### 2.4 从发布页一键取件（dev 线，Windows）

```bat
Dist\UpdateDesktopJvm.cmd                :: 取下最新的桌面端 JVM 便携包（dev 线的 V<版本>-aha-jvm）
Dist\UpdateDesktopJvm.cmd -List          :: 列出最近几版
Dist\UpdateDesktopJvm.cmd -Jdk 27        :: 取 JDK 27 变体
Dist\UpdateDesktopJvm.cmd -DryRun        :: 只预览：会下载哪个包、解压到哪里
```

```bat
Dist\UpdateDesktopJvm.cmd -NoInstall     :: 只解压到版本目录，不平铺到 Dist\
Dist\UpdateDesktopJvm.cmd -KeepExtract   :: 平铺后保留暂存目录（默认自动清掉）
```

落地（**平铺到落地根**；暂存目录用完即清）：

| 位置 | 内容 |
|---|---|
| `Dist\`（落地根） | 该版的**去版本号**入口一份：`aha-desktop.jar`、`AhaDesktop.bat`、`AhaDesktop.sh`（**复制**，不是移动）|
| `Dist\desktop-jvm\` | 解压出来的**暂存**目录（含历次取件留下的旧版本子目录）。平铺成功后**整个删掉** —— 同一个包在 `Dist/` 里存两遍没意义（桌面端 25 MB、CLI 55 MB），旧版本子目录更是纯占地；要保留加 `-KeepExtract` |

于是「当前装的是哪一版」记在 `Dist\.aha-desktop-jvm.json`，而命令**与版本无关**：
`Dist\AhaDesktop.bat`（或 `java -jar Dist\aha-desktop.jar`）永远指向最近一次取件的那一版。

> 与 `UpdateDesktopNative.cmd` 的区别：那个取的是**原生包**（一个 exe，**覆盖安装**）；
> JVM 包是「**单体 jar + 启动脚本**」，做法是「解压到暂存目录 → 把入口**复制**到落地根
> → 删掉暂存目录」，不存在「替换某个文件」。
> 另外：**最新一版缺本平台资产时脚本会报错，不会悄悄给一个更旧的包** ——
> 需要退旧版请显式用 `-AllowOlder` 或 `-Tag`。
> 而**旧形态的包**（包根没有单体 jar、只有 `bin/` + `lib/`）**平铺不了**，脚本会明确提示。

## 3. CLI（JVM 发行目录）

### 3.1 编译发行目录

```bat
mvnw.cmd -pl aha-cli -am package -DskipTests     :: 只出 CLI
mvnw.cmd clean package                           :: 全量（CLI + 桌面端 zip）
```

产物：`Dist\aha-cli.jar` + `Dist\AhaCli.bat` / `Dist\AhaCli.sh`（**包根平铺**，无 `bin/`、无 `lib/`）。

### 3.2 启动 / 运行

```bat
Dist\AhaCli.bat                     :: 进入对话（等价 aha chat）
Dist\AhaCli.bat run 用一句话介绍 AHA
Dist\AhaCli.bat provider list
Dist\AhaCli.bat tool list
Dist\AhaCli.bat version
```

从源码直接跑（不用先出包）：

```bat
mvnw.cmd -pl aha-cli exec:java
mvnw.cmd -pl aha-cli exec:java -Dexec.args="version"
mvnw.cmd -pl aha-cli exec:java -Dexec.args="provider list"
```

### 3.3 从发布页一键取件（dev 线，Windows）

```bat
Dist\UpdateCliJvm.cmd                :: 取下最新的 CLI JVM 便携包（**平台无关**，只有一份）
Dist\UpdateCliJvm.cmd -Jdk 27        :: 取 JDK 27 变体
Dist\UpdateCliJvm.cmd -List          :: 列出最近几版
```

落地：`Dist\aha-cli.jar` + `Dist\AhaCli.sh` / `Dist\AhaCli.bat`（与桌面端同一机制：
先解压到暂存目录用于取入口，平铺成功后把 `Dist\cli-jvm\` 清掉；`-KeepExtract` 可保留）。

## 4. 原生镜像（试验性，免 JDK）

> Windows 一键入口是 **PowerShell 原生实现**（`Dist\UpdateNative.ps1`，由 `Dist\Update*.cmd` 调用），
> **不需要 Python**；POSIX 用 Python 版（`Script/Python/{Desktop,Cli}NativeVersionUpdate.py`，功能更多：
> 多源发现 / 断点续传）。两者的命名契约由 `bin/CheckScripts.py` 断言一致。

### 4.1 桌面端原生镜像

**取件（一键，Windows 双击）**：

```bat
Dist\UpdateDesktopNative.cmd            :: 双击即可（PowerShell 实现，不需要 Python）
Dist\UpdateDesktopNative.cmd -List      :: 列出最近版本（也可写 --list）
Dist\UpdateDesktopNative.cmd -DryRun    :: 只预览（也可写 --dry-run）
```

**取件（脚本）**：

```bat
python3 Script\Python\DesktopNativeVersionUpdate.py            :: 下载并装到 Dist\aha-desktop-native.exe
python3 Script\Python\DesktopNativeVersionUpdate.py --list     :: 看有哪些版本
python3 Script\Python\DesktopNativeVersionUpdate.py --dry-run  :: 只预览链接
```

**取件（手动：下载 zip → 解压出 exe → 放到 `Dist/`）**：

发布页资产名为 `AHA-Desktop-Native-<版本>-<系统>-<架构>-jdk<JDK>.zip`，包内是可执行文件
`aha-desktop-native.exe`（Windows）/ `aha-desktop-native`（Linux / macOS）。

```bat
:: Windows：解压后直接取出 exe 放到 Dist\（把 <zip> 换成实际文件名）
powershell -NoProfile -Command "Expand-Archive -Force '<zip>' Dist\_native; Move-Item -Force Dist\_native\aha-desktop-native.exe Dist\aha-desktop-native.exe"
```

```bash
# Linux / macOS
unzip -j -o '<zip>' aha-desktop-native -d Dist/
chmod +x Dist/aha-desktop-native
```

**启动**：

```bat
Dist\aha-desktop-native.exe                                     :: 双击或命令行运行
```

```bash
./Dist/aha-desktop-native
```

> 原生镜像由 CI 的 `DesktopNative.yml` 按平台产出并挂到发布页（预发行 tag `V<版本>-aha-desktop-native`）。
> 自己编需 GraalVM 与 `-Pdesktop-native`，不属于日常流程。

### 4.2 CLI 原生镜像

发布页资产名为 `AHA-Cli-Native-<版本>-<系统>-<架构>-jdk<JDK>.zip`，包内是可执行文件
`aha-cli-native.exe`（Windows）/ `aha-cli-native`（Linux / macOS）。

**取件（一键，Windows 双击 / 脚本）**（与桌面端同构；`-List` / `-DryRun` / `-Tag` / `-Jdk` 都可用）：

```bat
Dist\UpdateCliNative.cmd                                        :: 双击：装到 Dist\aha-cli-native.exe
python3 Script\Python\CliNativeVersionUpdate.py                 :: 等价命令行
python3 Script\Python\CliNativeVersionUpdate.py --list          :: 看有哪些版本
```

**取件（手动：下载 zip → 解压出 exe → 放到 `Dist/`）**：

```bat
:: Windows
powershell -NoProfile -Command "Expand-Archive -Force '<zip>' Dist\_native; Move-Item -Force Dist\_native\aha-cli-native.exe Dist\aha-cli-native.exe"
```

```bash
# Linux / macOS
unzip -j -o '<zip>' aha-cli-native -d Dist/
chmod +x Dist/aha-cli-native
```

**启动**（子命令与 `Dist\AhaCli.bat` 一致，无需 JDK）：

```bat
Dist\aha-cli-native.exe                                         :: 进入对话（等价 aha chat）
Dist\aha-cli-native.exe run 用一句话介绍 AHA
Dist\aha-cli-native.exe provider list
Dist\aha-cli-native.exe version
```

```bash
./Dist/aha-cli-native
./Dist/aha-cli-native run "用一句话介绍 AHA"
./Dist/aha-cli-native version
```

> CLI 原生镜像由 CI 的 `CliNative.yml` 产出（预发行 tag `V<版本>-aha-cli-native`）。
> 交互式模式下 `Ctrl+C` 只打断当前生成，不退出进程。

**dev 分支的 JVM 便携包**由 CI 的 `BuildJVMArtifacts.yml` 在每次合并到 `dev` 后产出，
挂到发布页（预发行 tag `V<版本>-aha-jvm`），每个平台出 **JDK 25** 与 **JDK 27** 两份：

- `aha-desktop-<版本>-<平台>.zip` / `aha-cli-<版本>.zip`：**JDK 25 基线**（Java 25 字节码，需 JDK 25）；
- `aha-desktop-<版本>-<平台>-jdk27.zip` / `aha-cli-<版本>-jdk27.zip`：**JDK 27**（Java 27 字节码，需 JDK 27）。

> 这是**验证 dev 最新合并**用的预发行产物；正式版本仍用 Release 页的 `V<版本号>` 资产。

## 5. 日常检查

```bat
python3 bin\CheckChanged.py            :: 按 git 变更自动决定跑哪些检查
python3 bin\CheckChanged.py --all      :: 全部检查
python3 bin\CheckChanged.py --dry-run  :: 只打印计划
```

慢检查（`clean verify`、覆盖率门禁、文档/技能/脚本检查、重复率）由 CI 的 `Gate.yml`
承担，本地不必每次跑；详见 `Docs/DevSpec/BuildSpec.md` 第 8 节。

## 6. `Dist/` 里都有什么

```
Dist/
├── aha-cli.jar             CLI 单体 jar（自 issue #218 起：包根平铺，名字不带版本号）
├── AhaCli.bat / .sh        CLI 启动脚本（平铺在 Dist 根，不再有 bin\ 子目录）
├── aha-desktop.jar         桌面端单体 jar（解压 / 取件后落在这里；脚本只认这个名字，与版本无关）
├── AhaDesktop.bat / .sh    桌面端启动脚本（同上，平铺在 Dist 根，不再有 bin\ 子目录）
├── desktop-jvm/            取件时的**暂存**目录（平铺成功后自动删掉，见 §2.4）
├── cli-jvm/                CLI 取件时的暂存目录（同上；`-KeepExtract` 才留）
├── .aha-desktop-jvm.json   桌面端 JVM 取件状态（当前是哪一版、平铺了哪些入口）
├── .aha-cli-jvm.json       CLI JVM 取件状态（同上）
├── .aha-snapshot.json      JVM **快照** 的取件状态（见 §7）
├── aha-desktop-<版本>-<平台>.zip      桌面端便携包
├── aha-desktop-native-*.zip           桌面端原生镜像包
├── aha-desktop-native.exe             桌面端原生镜像（更新脚本或手动解压落在这里）
├── aha-cli-native.exe                 CLI 原生镜像（一键脚本或手动从发布页取出）
├── UpdateDesktopNative.cmd            一键更新桌面端原生镜像（双击；调同目录的 UpdateNative.ps1，无需 Python）
├── UpdateCliNative.cmd                一键更新 CLI 原生镜像（同上）
├── UpdateNative.ps1                  上述两个入口的 PowerShell 实现（UTF-8 with BOM）
├── UpdateDesktopJvm.cmd               一键取件桌面端 JVM 便携包（dev 线；调同目录的 UpdateJvm.ps1）
├── UpdateCliJvm.cmd                   一键取件 CLI JVM 便携包（dev 线；同上）
├── UpdateJvm.ps1                     上述两个入口的 PowerShell 实现（UTF-8 with BOM）
├── UpdateDesktopJvmSnapshot.cmd       一键取件桌面端 JVM **快照**（= UpdateSnapshot.ps1 -Kind desktop-jvm）
├── UpdateCliJvmSnapshot.cmd           一键取件 CLI JVM **快照**（= -Kind cli-jvm）
├── README.md               项目 README（由 CLI assembly 复制）
└── README.commands.md      本速查（由 DesktopDistExtract.py 刷新）
```

- `Dist/` 是**本地构建输出目录**，已在 `.gitignore` 中忽略；其中 `.md`、`aha-cli.jar`、以及**旧形态遗留**的 `bin/*`、`lib/*.jar` 会被
  `mvnw clean` 删掉（见 `aha-cli/pom.xml` 的 `maven-clean-plugin` 配置），由 `mvnw clean package`
  重建；本速查会随之一并重新写回，不需要手动维护。
- 依赖升级后旧 jar 可能残留，JPMS 下同名模块出现两个版本会直接 `FindException`：
  桌面端独占 jar 由 `DesktopDistExtract.py` 清理，CLI 侧用 `mvnw clean package` 重新生成。

## 7. 快照包（PR 试包）

**快照包 = 给尚未合并的 PR 出的试包**（方案见 [issue #165](https://github.com/ACANX/AHA/issues/165)，
实现见 #171 / #172 / #173 / #174）。它们挂在发布页的 pre-release 上，tag 形如
`V0.1.3.00184.10141900-AHA-Cli-JVM-SNAPSHOT`，默认保留 **7 天**（PR 关闭或合并后由清理工作流删除）。

**自测留痕（合并前的可选检查，`snapshot-verified`）**：在 PR 里发评论即可打标（**默认启用、默认不卡点**）：

```text
/verified <版本> <平台> [jdk27] [+model]        :: 必测清单通过（打 success）——平台 Win / MacOS / Ubuntu
/snapshot-failed <版本> <平台> [jdk27] <原因>    :: 自测失败（打 failure，失败也要可见）
```

必测清单（A1–A5 / B1–B4 / C1–C3）**一律 0 token**：工具直调 + 配置/本地命令 + 开窗观感即可，
不含任何模型往返；需要真实 provider 请求的动作是**可选**（命令末尾加 `+model` 声明）。
清单原文与口径见 `Docs/DevSpec/ReleaseProcess.md` §2.2。

**取件（一键，Windows 双击 / 脚本）**：

```bat
Dist\UpdateDesktopNativeSnapshot.cmd           :: 原生桌面包快照：取最近一次（自动挑本机平台）
Dist\UpdateCliNativeSnapshot.cmd               :: 原生 CLI 包快照：取最近一次
Dist\UpdateDesktopNativeSnapshot.cmd -List     :: 列出快照（版本号 / 目标 / 剩余天数）
Dist\UpdateDesktopNativeSnapshot.cmd -Pr 188   :: 取某个 PR 的快照
Dist\UpdateDesktopNativeSnapshot.cmd -DryRun   :: 只预览：会下载哪个包、解压到哪里
Dist\UpdateDesktopNativeSnapshot.cmd -Force    :: 已取过同一版也重新下载
Dist\UpdateDesktopNativeSnapshot.cmd -NoInstall :: 只取件、不安装（原生桌面端默认会替换 exe）
Dist\UpdateDesktopJvmSnapshot.cmd           :: JVM 桌面包快照：取最近一次（= -Kind desktop-jvm）
Dist\UpdateCliJvmSnapshot.cmd               :: JVM CLI 包快照：取最近一次（= -Kind cli-jvm）
Dist\UpdateSnapshot.ps1 -Kind jvm              :: JVM 便携包快照（两种目标共用，引擎直调）
```

特点：

- **零配置**：不需要 `GH_TOKEN`、不需要 `gh`、不需要 Python（全程 PowerShell / .NET）；
- **只碰自己的包**：JVM 与「原生 CLI」目标解压到 `Dist\<资产名>\` 的**独立目录**；**原生桌面端**目标会把包里的 exe
  替换到启动器同目录（旧版先备份为 `<名字>.exe.bak`，下载前先探测占用），`-NoInstall` 可只取件——与 `UpdateNative.ps1` 语义一致；
- **独立状态**：记在 `Dist\.aha-snapshot.json`，不读写 `.aha-desktop-native.json` / `.aha-cli-native.json`；
- 平台自动识别：两条线**共用一套平台词**，取 `windows-amd64`（ARM64 机器取 `windows-arm64`）。

**让 PR 出快照包**（两种方式）：

| 方式 | 用法 | 特点 |
|---|---|---|
| 触发标签 | 给 PR 打 `SNAPSHOT:Desktop` / `SNAPSHOT:Cli` / `SNAPSHOT:NativeDesktop` / `SNAPSHOT:NativeCli` | **粘性**：标签在则每次 push 重出；移除即停 |
| PR 评论命令 | `/snapshot <target> [os ...]` | **一次性**；大小写不敏感 |

平台收窄：再打 `OS:Win` / `OS:MacOS` / `OS:Ubuntu`（**不能单独使用**）。
只改 `Docs/**` 的 PR 不会出包（属 B 组），除非**新打**触发标签（显式请求）。
