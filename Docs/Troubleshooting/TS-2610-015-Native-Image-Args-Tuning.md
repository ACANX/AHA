# TS-2610-015 · 按首次真编日志调 native-image 参数：9 条警告与 5 条建议逐条处置

> 日期：2026-10-08
> 作者：@ACANX（与 AI 助手协作）
> 关联分支：`ACANX-patch-3`（PR #15 之后的参数调优分支）
> 关联记录：`Docs/Design/DesktopNativeDesign.md` §5.4、`Docs/Troubleshooting/TS-2610-014-Native-Skip-Silent-Override.md`

## 1. 背景

首次真编的日志（Windows + jdk25）证明管线是通的：

```
GraalVM Native Image: Generating 'aha-desktop-native.exe' (executable)...
Build artifacts: ...\target\native\aha-desktop-native.exe (executable, 77.75MiB)
Finished generating 'aha-desktop-native' in 3m 16s.
The build process encountered 9 warnings.
```

但输出里有 **9 条警告、5 条 Recommendations、1 条 Security 报告**。
用户要求：加 `emit build report`、修警告与过时参数、把建议里「能继续优化编译与运行」的采纳。

本轮只做**参数与配套设施**，不动业务代码。

## 2. 排障过程与修复链

### 2.1 先把日志里的三类输出分开看

| 类别 | 含义 | 处置原则 |
|---|---|---|
| Warning | 参数已弃用 / 属实验性 | 能立刻改的立刻改；改不了的写清退出路径 |
| Recommendations | 官方给的更优选项 | 能预期的采纳，不能预期的写明理由 |
| Security report | 可达性结论（如「含 Java 反序列化」） | **不是开关**，不猜选项名，按报告定位 |

把三类混在一起处理，最容易犯的错是「为了让警告清零而删掉功能开关」——
所以本轮明确了一条优先级：**「静默坏掉」优先于「警告清零」**。

### 2.2 逐条处置（结论表）

| 日志里的说法 | 处置 | 理由 |
|---|---|---|
| `--no-fallback` 已弃用且**无效果** | **删** | 它没有任何作用，还顺带引出 `FallbackThreshold` 的弃用警告 |
| `-H:IncludeResources` 属实验性 | **加 `-H:+UnlockExperimentalVMOptions`** | 不解锁，将来版本直接失败；解锁项必须排在实验性选项之前 |
| `-H:Path` / `-H:Name` 属实验性，建议用 `-o` | **改用 `-o <路径>`** | 官方明确给出的替代；POM 里传参 |
| `--enable-url-protocols` 已弃用（建议 reachability metadata） | **暂留**（唯一不立刻改的） | 它是 HTTPS 开关：删掉后构建仍成功、进程仍启动，只是**发不出请求**——静默坏掉远比一条警告严重。退出路径见 2.3 |
| sqlite-jdbc 自带同款弃用项 | **记录，不改** | 来自第三方 jar 的 `native-image.properties`，本项目无法修改 |
| Recommendations：GC 用 G1 | **采纳 `--gc=G1`** | 桌面 GUI 关心停顿；serial 是默认值、epsilon 无回收，都不适合长驻会话 |
| Recommendations：`--future-defaults=all` | **采纳** | 提前用上未来默认值 = 给「为 JDK 29 铺路」做早期预警：哪天编不过就是升级信号 |
| Recommendations：设置最大堆 | **采纳 `-R:MaxHeapSize=1g`** | 默认堆按机器内存百分比算，同一二进制在不同机器行为不同 |
| Recommendations：`--pgo` | **不采纳** | 日志的 builder configuration 已显示 `PGO: ML-inferred`，推测式 PGO 已在生效；手工 PGO 要两阶段（插桩 → 跑负载 → 重编），得先有负载样本 |
| Recommendations：`-march=native` | **不采纳** | 会把产物绑死构建机 CPU 特性，分发出去可能非法指令崩溃；要提性能应写 `-march=x86-64-v3` 这类可预期目标 |
| Recommendations：`-H:AdvancedObfuscation=""` | **不采纳**（列入实验位） | 会污染栈与诊断信息，与「可诊断优先」冲突 |
| Security：含 Java 反序列化 | **记录可达性，不猜选项** | 先确认业务不对不可信数据反序列化，再按构建报告定位可达路径 |

### 2.3 `--enable-url-protocols` 的退出路径（写清才会有人敢动）

1. 元数据已备好：`aha-desktop/src/main/resources/META-INF/native-image/.../reachability-metadata.json`
   （随 `aha-desktop.jar` 进 classpath，native-image 自动读取，不需要任何参数）；
2. 真机验证：原生二进制能完成一次真实对话（即 HTTPS 成功）；
3. 构建报告里没有 http / https 协议处理器的未决条目；
4. 三条都满足后才删除弃用项（`TODO.md` N-14）。

### 2.4 两份参数文件一起改（不能只改一份）

包内附带的 `native-image-args.txt`（JDK 25 基线）与 `native-image-args-jdk27.txt` 是
**逐条对应**的关系，它们的 diff 就是「JDK 轴真正的差异」。因此：

- 两份都同步了 2.2 里的全部改动；
- 且**保持行序完全一致**，避免无关的顺序变化污染 diff；
- 逐条的理由只写在基线那份里，jdk27 那份加一行指向它。

### 2.5 新增两个「可对账」设施

1. **构建报告**：加 `emit build report`（native-image 参数）。报告落在 `target/native/`，
   随包一起进 zip，并在工作流的产物自证里检查「有 / 无」——它是「下一步该加什么参数」的唯一依据；
2. **工作目录改到产物目录**：exec 的 `workingDirectory` 由模块根改为 `target/native/`，
   报告与可执行文件同处一地。安全性前提是参数文件、`-cp`、`-o` 全部使用绝对路径。

### 2.6 顺手修掉一个把 POM 改坏的坑（本轮踩的）

改 `pom.xml` 注释时写了 `--emit build-report`，直接导致 `not well-formed (invalid token)`：
**XML 注释体禁止出现 `--`**。同一轮还发现技能模板资产里**嵌套了注释**
（`<!--` 套 `<!--`），外层会被内层的 `-->` 提前闭合——谁抄谁坏。

两次都踩在同一条规则上，因此把它固化成仓库守卫：`bin/CheckScripts.py` 新增 `check_xml`，
扫每个 `*.xml` 的注释体，出现 `--` 或嵌套 `<!--` 即失败。反向验证过（塞探针 → 当场点名），
并且它**当场抓出了技能模板里的那处嵌套注释**。

### 2.7 本机验证手段：用 `echo` 替身看命令行

不装 GraalVM 也能验证 POM 传参是否正确：把可执行文件换成 `echo`
（`-Dnative.image.executable=/bin/echo`），构建就会把完整命令行打印出来。实测输出：

```
@.../native-image-args.txt -cp <完整 classpath> -o .../target/native/aha-desktop-native com.acanx.module.aha.desktop.AhaDesktopApp
```

`-H:Path` / `-H:Name` 已消失、`-o` 生效、classpath 里三个 `javafx-*-25-linux.jar` 正确。

### 2.8 量出来但本轮不动的优化

镜像里有 **27.69 MiB 的 `byte[]` 内嵌资源**，来自过宽的资源通配
（`.*\.(png|…|dll)$` 会把大量无关文件吃进去）。应迁移成 `resource-config.json` 的精确清单，
**这是目前最大的一处体积优化余地**。本轮不做，因为资源清单漏一项的后果是**运行期缺文件**，
必须先有真机走查兜住再收窄（`TODO.md` N-13）。

## 3. 最终验证结果

- 两份参数文件的 XML/文本合法；`pom.xml` 与 assembly 描述符可被 XML 解析器解析；
- 用 `echo` 替身跑通整条 Maven 管线：命令行符合预期（`-o` 生效、无 `-H:Path/-H:Name`）；
- `-Dnative.skip=true` 自检模式仍 BUILD SUCCESS（本机无 GraalVM 时不影响常规构建）；
- `bin/Check{Docs,Skills,Scripts}.py` 全绿（其中 `CheckScripts` 新增 XML 注释守卫，
  检查范围从 27 个脚本扩展到「27 个脚本 + 14 个 XML」）；
- 真正的效果（警告是否清零、G1 / future-defaults / 堆上限对启动与内存的影响）
  由下一次 `DesktopNative` 运行给出，实测数字填进 `DesktopNativeDesign.md` §5.3。

## 4. 关键教训

1. **把「警告」「建议」「安全报告」分开处置**：混在一起最容易出现「为了清零警告而删掉功能开关」。
2. **「静默坏掉」优先于「警告清零」**：`--enable-url-protocols` 这类开关，删了不会构建失败，
   只会让产物发不出请求。允许它带着弃用警告存在，但必须写下退出路径与验证条件。
3. **不采纳的建议也要写理由**：否则下一个人会把它们再试一遍。PGO 就是典型——
   日志里 `PGO: ML-inferred` 已经说明它在生效，再加 `--pgo` 是重复劳动。
4. **不要用不可预期的方式换性能**：`-march=native` 在「给自己编」时很香，
   在「给别人分发」时会让对方崩溃。分发产物只写可预期目标。
5. **参数文件是成对资产**：两份文件保持行序一致，它们的 diff 才有信息量；
   只改一份等于把对比实验作废。
6. **同一条规则踩两次就该写守卫**：XML 注释禁 `--` 这条，本轮之前已经踩过，
   这次又踩（还连带了嵌套注释），于是固化进 `bin/CheckScripts.py`——守卫当场抓出第三处。
7. **环境差异是免费的验证器**：换 `echo` 替身就能在没有工具链的机器上核对命令行，
   与上一轮「没有 GraalVM 反而成了判别器」是同一类思路。

## 5. 涉及文件清单

- `aha-desktop-native/src/native/native-image-args.txt`（基线：处置表 + 采纳的 4 项 + 实验位）
- `aha-desktop-native/src/native/native-image-args-jdk27.txt`（同步，保持行序一致）
- `aha-desktop-native/pom.xml`（输出改 `-o`；工作目录改 `target/native/`；复制元数据进包里）
- `aha-desktop-native/src/assembly/dist-native.xml`（带上构建报告与元数据）
- `aha-desktop/src/main/resources/META-INF/native-image/com.acanx.module.aha/aha-desktop/reachability-metadata.json`（新增）
- `.github/workflows/DesktopNative.yml`（产物自证里输出构建报告的有 / 无）
- `bin/CheckScripts.py`（新增 `check_xml`：XML 注释禁 `--`、禁嵌套）
- `.agents/skills/java-app-graalvm-native-image-compile/references/args-cookbook.md`
  （新增 §0「先读日志」与 §4.5「XML 硬坑」）
- `.agents/skills/java-app-graalvm-native-image-compile/references/troubleshooting.md`（XML 注释两行）
- `.agents/skills/java-app-graalvm-native-image-compile/assets/module-pom.xml`（去掉嵌套注释）
- `.agents/skills/java-app-graalvm-native-image-compile/SKILL.md`（迭代日志 3 行）
- `Docs/Design/DesktopNativeDesign.md`（§5.4 参数调优记录）
- `Docs/TODO.md`（N-13 资源清单收窄、N-14 删弃用协议开关）
- `CHANGELOG.md`

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- **首次真编日志（Windows + jdk25）证明管线是通的**：
  `GraalVM Native Image: Generating 'aha-desktop-native.exe' (executable)...`、
  `Build artifacts: …\target\native\aha-desktop-native.exe (executable, **77.75MiB**)`、
  `Finished generating 'aha-desktop-native' in **3m 16s**.`、
  `The build process encountered **9 warnings**.`
- 输出里共有 **9 条警告、5 条 Recommendations、1 条 Security 报告**，逐条处置结论如下：

  | 日志里的说法 | 处置 | 理由 |
  | --- | --- | --- |
  | `--no-fallback` 已弃用且**无效果** | **删** | 无作用，还引出 `FallbackThreshold` 的弃用警告 |
  | `-H:IncludeResources` 属实验性 | **加 `-H:+UnlockExperimentalVMOptions`** | 不解锁，将来版本直接失败；**解锁项必须排在实验性选项之前** |
  | `-H:Path` / `-H:Name` 属实验性，建议用 `-o` | **改 `-o <路径>`** | 官方明确替代；POM 里传参 |
  | `--enable-url-protocols` 已弃用 | **暂留**（唯一不立刻改的） | 它是 HTTPS 开关，删掉后构建仍成功、进程仍启动，**只是发不出请求** |
  | sqlite-jdbc 自带同款弃用项 | **记录，不改** | 来自第三方 jar 的 `native-image.properties` |
  | GC 建议 G1 | **采纳 `--gc=G1`** | 桌面 GUI 关心停顿；serial 是默认、epsilon 无回收 |
  | `--future-defaults=all` | **采纳** | 提前用上未来默认值，为「JDK 29 铺路」做早期预警 |
  | 设置最大堆 | **采纳 `-R:MaxHeapSize=1g`** | 默认堆按机器内存百分比算，同一二进制在不同机器行为不同 |
  | `--pgo` | **不采纳** | 日志的 builder configuration 已显示 **`PGO: ML-inferred`**，推测式 PGO 已在生效；手工 PGO 需两阶段且要负载样本 |
  | `-march=native` | **不采纳** | 会把产物绑死构建机 CPU 特性，分发出去可能非法指令崩溃 |
  | `-H:AdvancedObfuscation=""` | **不采纳**（列入实验位） | 会污染栈与诊断信息，与「可诊断优先」冲突 |
  | Security：含 Java 反序列化 | **记录可达性，不猜选项** | 先确认业务不对不可信数据反序列化，再按构建报告定位 |

- 两份参数文件（`native-image-args.txt` / `native-image-args-jdk27.txt`）**同步了全部改动**，
  且**保持行序完全一致**；逐条理由只写在基线那份里，jdk27 那份加一行指向它。
- 新增两个可对账设施：**`emit build report`**（报告落 `target/native/`、随包进 zip、
  工作流自证检查有 / 无）；**exec 工作目录**由模块根改为 `target/native/`
  （前提：参数文件、`-cp`、`-o` **全部用绝对路径**）。
- 本机验证（不装 GraalVM）：用 `-Dnative.image.executable=/bin/echo` 替身，实测打印出
  `@…/native-image-args.txt -cp <完整 classpath> -o …/target/native/aha-desktop-native com.acanx.module.aha.desktop.AhaDesktopApp`
  ——`-H:Path` / `-H:Name` 已消失、`-o` 生效、classpath 里三个 `javafx-*-25-linux.jar` 正确。
- 量出但本轮不动：镜像里有 **27.69 MiB 的 `byte[]` 内嵌资源**，来自过宽的资源通配
  （`.*\.(png|…|dll)$`）；应迁移成 `resource-config.json` 的精确清单（`TODO.md` **N-13**）。
- 本轮踩的坑：POM 注释里写 `--emit build-report` → `not well-formed (invalid token)`
  （**XML 注释体禁止出现 `--`**）；同轮还发现技能模板资产里**嵌套了注释**（外层被内层 `-->` 提前闭合）。
  已固化为守卫：`bin/CheckScripts.py` 新增 `check_xml`（反向验证塞探针即点名，
  并**当场抓出技能模板里的那处嵌套注释**）；检查范围从 **27 个脚本**扩到「27 个脚本 + **14 个 XML**」。
- `Check{Docs,Skills,Scripts}` 全绿；`-Dnative.skip=true` 自检模式仍 `BUILD SUCCESS`。
- **真正的效果（警告是否清零、G1 / `future-defaults` / 堆上限对启动与内存的影响）当时未验证**，
  由下一次 `DesktopNative` 运行给出，实测数字填进 `DesktopNativeDesign.md` §5.3。

### 机制 / 行为模型

- **三类输出必须分开处置**（Warning / Recommendations / Security report）。
  混在一起最容易犯的错是「**为了让警告清零而删掉功能开关**」。
  本档案据此立了一条优先级：**「静默坏掉」优先于「警告清零」**。
- 弃用项允许**带着警告存在**，但必须**写下退出路径与验证条件**（见「2.3」的四条）——
  否则没人敢动它，也没人知道什么时候能删。
- **不采纳的建议也要写理由**，否则下一个人会把它们再试一遍。
- **不要用不可预期的方式换性能**：`-march=native` 在「给自己编」时很香，
  在「给别人分发」时会让对方崩溃；分发产物只写可预期目标。
- **参数文件是成对资产**：两份文件保持行序一致，**它们的 diff 才有信息量**；
  只改一份等于把对比实验作废。
- **同一条规则踩两次就该写守卫**：XML 注释禁 `--` 已踩过一次、本轮又踩（还连带嵌套注释），
  于是固化进 `bin/CheckScripts.py`。
- **环境差异是免费的验证器**：换 `echo` 替身就能在没有工具链的机器上核对命令行。

### 未定位 / 未复现

- **采纳项的实际效果未验证**：警告是否清零、G1 / `future-defaults` / `MaxHeapSize` 对
  **启动时间与内存**的影响，要等下一次 `DesktopNative` 运行，实测数字填入
  `DesktopNativeDesign.md` §5.3。
- **27.69 MiB 内嵌资源的收窄本轮不做**：资源清单**漏一项的后果是运行期缺文件**，
  必须先有真机走查兜住再收窄（`TODO.md` N-13）。
- **Security 报告里的反序列化可达路径未定位**：档案只写了「先确认业务不对不可信数据反序列化」，
  没有给出可达路径。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 首次真编成功 | native-image 日志（Windows + jdk25） | `Finished generating 'aha-desktop-native' in **3m 16s**` |
| 产物大小 | 同上 | `Build artifacts: …\aha-desktop-native.exe (executable, **77.75MiB**)` |
| 警告与建议的规模 | 同上 | **9 warnings** + **5 Recommendations** + **1** Security 报告 |
| PGO 已在生效的判据 | 日志的 builder configuration | **`PGO: ML-inferred`** |
| 传参正确性的替身验证 | `-Dnative.image.executable=/bin/echo` | 打印：`@…/native-image-args.txt -cp <完整 classpath> -o …/target/native/aha-desktop-native com.acanx.module.aha.desktop.AhaDesktopApp` |
| classpath 正确性 | 同上 | 含三个 `javafx-*-25-linux.jar` ✅ |
| 体积优化余地（**量出未动**） | 构建报告的 `byte[]` 类目 | **27.69 MiB** 内嵌资源（过宽通配 `.*\.(png\|…\|dll)$`） |
| XML 注释踩坑 | `mvn` 解析报错 | `not well-formed (invalid token)`（注释体里写了 `--emit build-report`） |
| 守卫的反向验证 | 塞探针后跑 `bin/CheckScripts.py` | 当场点名；且**抓出技能模板里的嵌套注释** |
| 守卫覆盖面 | `check_xml` 的扫描范围 | **27 个脚本 + 14 个 XML** |
| 自检模式 | `-Dnative.skip=true` | `BUILD SUCCESS`（不影响常规构建） |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「警告必须清零（哪怕删功能开关）」 | 明确优先级：**静默坏掉优先于警告清零**；`--enable-url-protocols` 暂留并写退出路径（「2.1」「2.2」） |
| 「`--no-fallback` 还有用」 | 日志指出它已弃用且**无效果**（「2.2」） |
| 「再手动加一次 `--pgo` 会更快」 | 日志显示 `PGO: ML-inferred` 已在生效，手工 PGO 是重复劳动（「2.2」） |
| 「`-march=native` 白拿性能」 | 会绑死构建机 CPU 特性，分发出去可能**非法指令崩溃**（「2.2」） |
| 「只改基线那份参数文件即可」 | 两份是**逐条对应**的资产，只改一份会把对比实验作废（「2.4」） |
| 「XML 注释里写 `--` 无所谓」 | 实测 `not well-formed (invalid token)`；技能模板里还有嵌套注释（「2.6」） |
| 「本机没 GraalVM 就无法验证传参」 | `echo` 替身能打印完整命令行（「2.7」） |

## 未决问题

**Q1（`TODO.md` N-14）什么时候能删掉 `--enable-url-protocols`？**

现状：退出路径已写明**四条**——① 元数据已备好（`reachability-metadata.json` 随 jar 进 classpath）；
② 真机验证原生二进制能完成一次真实对话（HTTPS 成功）；
③ 构建报告里没有 http / https 协议处理器的未决条目；④ 三条都满足后才删。**目前未满足**。

**Q2（`TODO.md` N-13）资源清单何时收窄？**

现状：**27.69 MiB** 的内嵌 `byte[]` 是目前最大的体积优化余地（过宽通配所致）。
阻塞点：清单**漏一项的后果是运行期缺文件**，必须先有真机走查兜住，再迁移到
`resource-config.json` 的精确清单。

**Q3 采纳项（G1 / `future-defaults` / `MaxHeapSize`）的实际收益是多少？**

现状：**未测**——启动时间与内存的实测数字待下一次 `DesktopNative` 运行后填入
`DesktopNativeDesign.md` §5.3。
阻塞点：需要真机采集数据，本机无 GraalVM。

**Q4 Security 报告里的「含 Java 反序列化」可达路径在哪？**

现状：**未定位**——只记录了「先确认业务不对不可信数据反序列化，再按构建报告定位可达路径」。
阻塞点：需要读构建报告的可达性条目 + 业务侧的数据来源梳理。
