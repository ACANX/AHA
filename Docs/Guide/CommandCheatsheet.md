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

## 1. 脚本一览（最常用）

| 想做的事 | Windows | Linux / macOS |
|---|---|---|
| 编译桌面端便携包 | `python3 Script\Python\DesktopDistBuild.py` | `python3 Script/Python/DesktopDistBuild.py` |
| 解压并更新 `Dist/` | `python3 Script\Python\DesktopDistExtract.py` | `python3 Script/Python/DesktopDistExtract.py` |
| 下载最新桌面端原生镜像 | `python3 Script\Python\DesktopNativeVersionUpdate.py` | `python3 Script/Python/DesktopNativeVersionUpdate.py` |
| 启动桌面端（Dist 版） | `Dist\bin\AhaDesktop.bat` | `./Dist/bin/AhaDesktop.sh` |
| 启动桌面端（源码根） | `bin\AhaDesktop.bat` | `./bin/AhaDesktop.sh` |
| 启动 CLI（Dist 版） | `Dist\bin\Aha.bat` | `./Dist/bin/Aha.sh` |
| 按变更跑检查 | `python3 bin\CheckChanged.py` | `python3 bin/CheckChanged.py` |

> `bin\AhaDesktop.*` / `bin/AhaDesktop.sh` 在源码根运行时，若没有 `lib/`，会自动挑本平台的
> `Dist/aha-desktop-*.zip` 解压后再启动；无需先手动解压。

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

效果：把 zip 里的 `lib/`、`bin/` 铺进 `Dist/`，清掉上一版桌面端独占 jar
（`aha-desktop-*.jar` / `javafx-*.jar`），完成后 `Dist\bin\AhaDesktop.bat` 即可用。
共享依赖与 `aha-cli-*.jar` 不受影响，所以 `Dist/` 同时保留 CLI 用法。

### 2.3 启动桌面端

```bat
Dist\bin\AhaDesktop.bat          :: 用 Dist\lib（需先执行 2.2 或 2.1+解压）
bin\AhaDesktop.bat               :: 源码根：没有 lib 时自动解压便携包
```

```bash
./Dist/bin/AhaDesktop.sh
./bin/AhaDesktop.sh
```

## 3. CLI（JVM 发行目录）

### 3.1 编译发行目录

```bat
mvnw.cmd -pl aha-cli -am package -DskipTests     :: 只出 CLI
mvnw.cmd clean package                           :: 全量（CLI + 桌面端 zip）
```

产物：`Dist\bin\` + `Dist\lib\`（JPMS 模块路径）。

### 3.2 启动 / 运行

```bat
Dist\bin\Aha.bat                    :: 进入对话（等价 aha chat）
Dist\bin\Aha.bat run 用一句话介绍 AHA
Dist\bin\Aha.bat provider list
Dist\bin\Aha.bat tool list
Dist\bin\Aha.bat version
```

从源码直接跑（不用先出包）：

```bat
mvnw.cmd -pl aha-cli exec:java
mvnw.cmd -pl aha-cli exec:java -Dexec.args="version"
mvnw.cmd -pl aha-cli exec:java -Dexec.args="provider list"
```

## 4. 桌面端原生镜像（试验性）

```bat
python3 Script\Python\DesktopNativeVersionUpdate.py            :: 装最新版到 Dist\aha-desktop-native.exe
python3 Script\Python\DesktopNativeVersionUpdate.py --list     :: 看有哪些版本
python3 Script\Python\DesktopNativeVersionUpdate.py --dry-run  :: 只预览链接
Dist\aha-desktop-native.exe                                     :: 运行
```

> 原生镜像由 CI 的 `DesktopNative.yml` 按平台产出并挂到发布页，本脚本只负责取件。
> 自己编需 GraalVM 与 `-Pdesktop-native`，不属于日常流程。

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
├── bin/                    Aha.bat / Aha.sh、AhaDesktop.bat / AhaDesktop.sh
├── lib/                    JPMS 模块路径（CLI 与桌面端共用）
├── aha-desktop-<版本>-<平台>.zip      桌面端便携包
├── aha-desktop-native-*.zip           桌面端原生镜像包
├── aha-desktop-native.exe             原生镜像（更新脚本落在这里）
├── README.md               项目 README（由 CLI assembly 复制）
└── README.commands.md      本速查（由 DesktopDistExtract.py 刷新）
```

- `Dist/` 是**本地构建输出目录**，已在 `.gitignore` 中忽略；其中 `.md`、`bin/*`、`lib/*.jar` 会被
  `mvnw clean` 删掉（见 `aha-cli/pom.xml` 的 `maven-clean-plugin` 配置），由 `mvnw clean package`
  重建；本速查会随之一并重新写回，不需要手动维护。
- 依赖升级后旧 jar 可能残留，JPMS 下同名模块出现两个版本会直接 `FindException`：
  桌面端独占 jar 由 `DesktopDistExtract.py` 清理，CLI 侧用 `mvnw clean package` 重新生成。
