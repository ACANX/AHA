# TS-2610-024 · 桌面端启动脚本在源码根误报「找不到 lib」

> 类型：排障（启动脚本可用性）
> 关联：`TODO.md` `D-02`（桌面端独立启动脚本）、`DesktopDesign.md` §5.6

## 1. 背景

在源码仓库根执行 `.\bin\AhaDesktop.bat`：

```
[ERROR] Module directory not found: "E:\...\AHA\lib"
[ERROR] Build the distribution first:  mvnw.cmd clean package
```

但按提示跑 `clean package` 也**永远不会**出现 `<仓库根>\lib`：桌面端的 Maven assembly
只产出 `Dist/aha-desktop-<版本>-<平台>.zip`，`lib/` 是解压之后才有的。脚本的报错把用户
引向了一条走不通的路。

## 2. 排障过程与修复链

### 2.1 取证：布局对照

| 脚本的假设 | 实际 |
|---|---|
| `<BASE>\lib\aha-desktop-*.jar` 存在 | 源码根没有 `lib/`；`Dist/lib` 是 **CLI** 的产物 |
| 脚本位于发行包的 `bin/` | 脚本被 git 跟踪在源码根 `bin/`，用户自然在源码根运行 |
| `clean package` 会产出 `lib/` | `clean package` 只产出 `Dist/aha-desktop-*-<平台>.zip` |

依据：`aha-desktop/src/assembly/dist-desktop.xml` 把 `bin/` + `lib/` 铺进 **zip**；
只有 CLI 的 assembly 才是解包到 `Dist/`。

### 2.2 修复：脚本识别两种布局

`bin/AhaDesktop.bat` 与 `bin/AhaDesktop.sh` 改为：

1. **发行包布局**：`<BASE>/lib` 内有 `aha-desktop-*.jar` → 原逻辑不变；
2. **源码检出布局**：没有 `lib/` 但存在 `pom.xml` → 在 `Dist/`（sh 兼容 `Dist/`）找
   `aha-desktop-<版本>-<平台>.zip`，解压到「zip 名 + 写入时间 + 大小」命名的目录后，
   以其 `lib/` 作为 JPMS 模块路径启动。

平台选择：bat 优先 `*-win.zip`；sh 用 `uname` 选 `linux[-aarch64]` / `mac[-aarch64]`，
命中不到再退回任意桌面端包（`[0-9]` 通配排除 `aha-desktop-native-*.zip`）。

关键设计：

- 解压目录名带**时间戳 + 大小**——同版本重建也会落到新目录，**不会被旧解压结果遮蔽**；
- 旧目录**不删**——可能正被已运行的实例占用（Windows 上删除会失败）；
- 解压用 JDK 自带的 `jar --extract`，不引入 `unzip` 依赖（JDK 25 本就是前置条件）。

## 3. 最终验证结果

- `python3 bin/CheckScripts.py`：全部脚本通过（`.bat` 纯 ASCII + CRLF，`.sh` LF）。
- **Windows**：`bin/AhaDesktop.bat` 命中 `Dist\aha-desktop-0.1.1-win.zip`，解压出 18 个 jar
  （含 `aha-desktop-0.1.1.jar` 与 3 个 `javafx-*-25-win.jar`）并进入启动；
  第二次运行**跳过**解压（复用同一目录）。
- **模块图自证**（CI 同款探针）：
  `java --module-path <解压后的 lib> --module com.acanx.module.aha.desktop/com.acanx.module.aha.desktop.__CompletenessProbe__`
  → 只报「找不到主类」，无 `FindException`，模块图完整。
- **Linux**：以假 `java` / `jar` 注入 `JAVA_HOME` 驱动 `AhaDesktop.sh`，确认按平台选中
  `aha-desktop-0.1.1-linux.zip`、解压路径与传给 JVM 的参数正确。

## 4. 关键教训

1. **报错里给出的「下一步命令」必须真能到达目标状态。** 原提示让用户 `clean package`，
   而该命令不会产生脚本要找的 `lib/`——这类「指向死路的提示」比没有提示更糟。
2. **同一个脚本同时存在于源码与发行包两个位置时，必须显式区分两种布局**，不能只按
   发行包假设。
3. **解压 / 缓存目录要带唯一键（时间 + 大小）**，否则「同名版本重建」会被旧产物遮蔽；
   同时不要删除可能正被占用的旧目录。

## 5. 涉及文件清单

- `bin/AhaDesktop.bat`
- `bin/AhaDesktop.sh`
- `Docs/TODO.md`（`D-02` 补记）
- `Docs/Troubleshooting/TS-2610-024-Launcher-Missing-Lib-On-Source-Root.md`（本文件）

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- 在**源码仓库根**执行 `.\bin\AhaDesktop.bat`，报：

  ```text
  [ERROR] Module directory not found: "E:\...\AHA\lib"
  [ERROR] Build the distribution first:  mvnw.cmd clean package
  ```

- 但按提示跑 `clean package` **永远不会**出现 `<仓库根>\lib`：桌面端的 Maven assembly
  只产出 `Dist/aha-desktop-<版本>-<平台>.zip`，`lib/` 是**解压之后**才有的
  ⇒ **脚本的报错把用户引向了一条走不通的路**。
- **布局对照（取证）**：

  | 脚本的假设 | 实际 |
  | --- | --- |
  | `<BASE>\lib\aha-desktop-*.jar` 存在 | 源码根**没有** `lib/`；`Dist/lib` 是 **CLI** 的产物 |
  | 脚本位于发行包的 `bin/` | 脚本被 git 跟踪在源码根 `bin/`，用户自然在**源码根**运行 |
  | `clean package` 会产出 `lib/` | `clean package` 只产出 `Dist/aha-desktop-*-<平台>.zip` |

  依据：`aha-desktop/src/assembly/dist-desktop.xml` 把 `bin/` + `lib/` 铺进 **zip**；
  只有 CLI 的 assembly 才是解包到 `Dist/`。

- **修复后的实测**：
  - `python3 bin/CheckScripts.py` → 全部脚本通过（`.bat` 纯 ASCII + CRLF，`.sh` LF）；
  - **Windows**：`bin/AhaDesktop.bat` 命中 `Dist\aha-desktop-0.1.1-win.zip`，
    解压出 **18 个 jar**（含 `aha-desktop-0.1.1.jar` 与 3 个 `javafx-*-25-win.jar`）并进入启动；
    **第二次运行跳过解压**（复用同一目录）；
  - **模块图自证**（CI 同款探针）：
    `java --module-path <解压后的 lib> --module com.acanx.module.aha.desktop/com.acanx.module.aha.desktop.__CompletenessProbe__`
    → 只报「找不到主类」，**无 `FindException`**，模块图完整；
  - **Linux**：以**假 `java` / `jar`** 注入 `JAVA_HOME` 驱动 `AhaDesktop.sh`，
    确认按平台选中 `aha-desktop-0.1.1-linux.zip`、解压路径与传给 JVM 的参数正确。

### 机制 / 行为模型

- **报错里给出的「下一步命令」必须真能到达目标状态**：原提示让用户 `clean package`，
  而该命令**不会**产生脚本要找的 `lib/`——这类「**指向死路的提示**」比没有提示更糟。
- **同一个脚本同时存在于源码与发行包两个位置时，必须显式区分两种布局**，
  不能只按发行包假设（两条路径：发行包布局 `<BASE>/lib` 有 jar；源码检出布局**没有** `lib/`
  但有 `pom.xml` → 去 `Dist/` 找 zip 解压）。
- **解压 / 缓存目录要带唯一键（时间 + 大小）**，否则「同名版本重建」会被旧产物遮蔽；
  同时**不要删除**可能正被占用的旧目录（Windows 上删除会失败）。
- **平台选择要有兜底**：bat 优先 `*-win.zip`；sh 用 `uname` 选 `linux[-aarch64]` /
  `mac[-aarch64]`，命中不到再退回任意桌面端包——并且用 `[0-9]` 通配
  **排除 `aha-desktop-native-*.zip`**（否则会误选原生包）。
- **依赖 JDK 自带能力**：解压用 `jar --extract`，不引入 `unzip` 依赖
  （JDK 25 本就是前置条件）。

### 未定位 / 未复现

- **macOS 的实际解压与启动未在真机验证**：只验证了「平台选择逻辑」——
  用**假 `java` / `jar`** 驱动脚本，看它选哪个包、传什么参数；真机上没跑过。
- **旧解压目录的清理策略未定**：「不删」是刻意的（可能被运行中的实例占用），
  但**磁盘增长没有上限**，也没有提供清理入口。
- **重复运行的行为只验证了「第二次跳过解压」**：没有验证「解压目录被占用时去新目录」
  以及多次重建后的目录堆积情况。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 误报原文 | 源码根执行 `.\bin\AhaDesktop.bat` | `[ERROR] Module directory not found: "E:\...\AHA\lib"` |
| 误导性的「下一步」 | 同上 | `[ERROR] Build the distribution first:  mvnw.cmd clean package` |
| 该命令的真实产出 | `aha-desktop/src/assembly/dist-desktop.xml` | 只产出 `Dist/aha-desktop-<版本>-<平台>.zip`；`bin/` + `lib/` 在 **zip 里面** |
| 「`Dist/lib` 是谁的」 | 对照 CLI 的 assembly | CLI 才是解包到 `Dist/`；桌面端的 `lib/` 只在 zip 内 |
| Windows 实测 | 运行 `bin/AhaDesktop.bat` | 命中 `Dist\aha-desktop-0.1.1-win.zip`；解压 **18 个 jar**（含 3 个 `javafx-*-25-win.jar`）；进入启动 ✅ |
| 缓存复用 | 第二次运行 | **跳过解压**，复用同一目录 ✅ |
| **模块图自证** | `--module-path <lib> --module …__CompletenessProbe__` | 只报「找不到主类」，**无 `FindException`** ⇒ 模块图完整 ✅ |
| Linux 实测 | 假 `java` / `jar` 注入 `JAVA_HOME` 驱动 `AhaDesktop.sh` | 按平台选中 `aha-desktop-0.1.1-linux.zip`；解压路径与 JVM 参数正确 ✅ |
| 脚本规范 | `python3 bin/CheckScripts.py` | 全过（`.bat` 纯 ASCII + CRLF；`.sh` LF）✅ |
| 解压目录命名 | 实现 | 「zip 名 + **写入时间 + 大小**」⇒ 同版本重建落到新目录 |
| **未验证** | — | macOS 真机解压 / 启动 |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「用户没先构建」 | 提示让跑 `clean package`，而该命令**不会**产生脚本要找的 `lib/`——是**脚本的假设错了**（「2.1」） |
| 「`Dist/lib` 就是桌面端的 lib」 | `Dist/lib` 是 **CLI** 的产物；桌面端的 `lib/` 只在 `Dist/aha-desktop-*.zip` **内部**（「2.1」） |
| 「脚本只会在发行包目录里被运行」 | 脚本被 git 跟踪在源码根 `bin/`，用户自然在源码根运行（「2.1」） |
| 「解压目录用版本号命名就够了」 | 「同名版本重建」会被旧解压结果**遮蔽** ⇒ 目录名加时间戳 + 大小（「2.2」关键设计） |
| 「旧目录该删掉省空间」 | 可能正被已运行的实例占用（Windows 上删除会失败）⇒ **不删**（「2.2」） |
| 「退回任意桌面端包就行」 | 会用 `[0-9]` 通配**排除 `aha-desktop-native-*.zip`**，否则误选原生包（「2.2」） |
| 「解压要依赖 `unzip`」 | 用 JDK 自带的 `jar --extract`——JDK 25 本就是前置条件（「2.2」） |

## 未决问题

**Q1 macOS 上真能解压并启动吗？**

现状：**未验证**——只以**假 `java` / `jar`** 驱动脚本验证了「平台选择与传参」，
真机没跑过。
阻塞点：需要 macOS 环境；注意 `uname` 分支里 `mac[-aarch64]` 的匹配与
`jar --extract` 在 macOS 上的行为都要实测。

**Q2 解压目录堆积怎么清？**

现状：**刻意不删**旧目录（避免占用冲突），但**没有清理入口**，磁盘会持续增长。
阻塞点：可考虑加一个「清理 N 天前的解压目录」的开关或独立脚本，需先确认 Windows 上
「占用即跳过」的行为。

**Q3 「报到死路的提示」这类问题怎么防？**

现状：本条是**人工发现**的（用户按提示操作走不通）。
阻塞点：可考虑让脚本的提示只写「它自己验证过的路径」（例如直接打印它实际查找的候选位置），
而不是给一条未必产生该产物的命令——尚未形成规范。

**Q4 重复运行与并发的情况？**

现状：只验证了「第二次跳过解压（复用同一目录）」。
未验证：解压目录被占用时的行为、同版本重建后新旧目录并存的取舍。
