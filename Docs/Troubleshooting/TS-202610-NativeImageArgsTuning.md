# TS-202610-NativeImageArgsTuning：按首次真编日志调 native-image 参数：9 条警告与 5 条建议逐条处置

> 日期：2026-10-08
> 作者：@ACANX（与 AI 助手协作）
> 关联分支：`ACANX-patch-3`（PR #15 之后的参数调优分支）
> 关联记录：`Docs/Design/DesktopNativeDesign.md` §5.4、`Docs/Troubleshooting/TS-202610-NativeSkipSilentOverride.md`

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
