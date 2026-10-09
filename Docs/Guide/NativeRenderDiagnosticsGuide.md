# 原生渲染诊断操作手册

**文档版本**：v1.0.0
**状态**：冻结
**生效日期**：2026-10-09
**最后更新**：2026-10-09
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-09 | 初始版本：原生包与 JVM 模式各跑一次、收集渲染诊断并对照的步骤（issue #48） | @ACANX / CNXNC |

---

## 1. 这份手册解决什么

issue #48 的现象是「原生包的字发虚、字形偏细，而 JVM 模式锐利、笔画匀称」。
它在开发机上**无法复现**（没有 Windows 图形环境），只能**在真机上各跑一次**，
把**同样的几行日志**拿出来对照——有了数字和类名，才能把猜测换成结论。

本手册给出可照做的步骤；**拿到数据后怎么判**见第 6 节的判定表。

- 跟踪载体：issue #48
- 调研过程与证据链：[DevLog-20261009-13.md](../DevLog/DevLog-20261009-13.md)

## 2. 前提

1. **PR #58 已合入 `dev`** —— 诊断代码就在里面（`AhaDesktopApp.logRenderingDiagnostics()`）；
2. `DesktopNative.yml` 已据此重出 Windows 原生包，形如
   `AHA-Desktop-Native-<版本>-windows-amd64-jdk25.zip`；
3. 本机装有 **JDK 25**（跑 JVM 模式对照用）。

> 诊断代码只存在于 PR #58 之后的包。更早的包跑出来是空的，不必试。

## 3. 准备工作

| 项目 | 做法 |
|---|---|
| 原生包 | 从 Releases 下载 `AHA-Desktop-Native-<版本>-windows-amd64-jdk25.zip` |
| 解压位置 | 任意目录，例如 `E:\AHA-Native\`（**记住它**，日志就在它下面） |
| JVM 模式 | 源码检出的仓库根（`bin\AhaDesktop.bat` 会自动解压 `Dist\aha-desktop-*.zip`） |
| 收集脚本 | 仓库内 `Script/Python/CollectRenderDiagnostics.py`（见 4.3） |

**两个必须先知道的坑**：

1. **黑框里看不到诊断。** `LoggingSetup` 的 console appender **只输出 `ERROR`**
   （刻意如此：INFO / WARN 写入 stdout 会打断 CLI 的对话输出）。
   诊断是 **INFO 级**，因此**只在日志文件里**。
2. **日志位置随启动方式变化**（见 `LoggingSetup.resolveLogFile`）：

   | 情况 | 路径 |
   |---|---|
   | 未设 `AHA_HOME`（默认） | `<启动目录>\Log\AHA.log` |
   | 设了 `AHA_HOME` | `%AHA_HOME%\Log\AHA.log` |
   | `Aha.yaml` 配置了 `Aha.Logging.File` | 以配置为准 |

   双击 exe 时「启动目录」即解压目录，所以原生包的日志是
   `E:\AHA-Native\Log\AHA.log`。

## 4. 步骤

### 4.1 跑原生包

1. 解压原生包到 `E:\AHA-Native\`；
2. 双击 `aha-desktop.exe`，**等窗口出现、跑十几秒**，再正常关闭；
3. 日志在 `E:\AHA-Native\Log\AHA.log`。

### 4.2 跑 JVM 模式（对照组）

在源码检出的仓库根执行：

```bat
bin\AhaDesktop.bat
```

同样等窗口出现、跑十几秒再关闭；日志在**仓库根**的 `Log\AHA.log`。

> `AhaDesktop.bat` / `AhaDesktop.sh` 已经带上
> `--add-opens javafx.graphics/com.sun.javafx.font=ALL-UNNAMED`。
> 少了它，`com.sun.javafx.font` 这个**非导出包**会被模块系统拒绝，
> 「字体实现工厂」一项只会打「不可用」，对照就**少一条关键证据**。

### 4.3 收集

在仓库根执行下面两条（`--mode` 只影响报告头部的标记）：

```bash
python3 Script\Python\CollectRenderDiagnostics.py --mode native ^
    --log E:\AHA-Native\Log\AHA.log --out 诊断-原生.txt

python3 Script\Python\CollectRenderDiagnostics.py --mode jvm --out 诊断-JVM.txt
```

`--log` 不给时会自动查找：`AHA_HOME/Log/AHA.log` → 当前目录及其上溯各层 →
脚本所在仓库根。把生成的两个文件（或终端输出）交给维护者即可。

脚本只取**最后一次启动**的片段（日志是追加写的，多次启动会混在一起）。

## 5. 会看到什么

```text
Prism pipeline init order: es2 sw
Prism pipeline name = com.sun.prism.es2.ES2Pipeline
Loaded library /libprism_es2.so from resource
渲染诊断：属性 prism.order=<默认> prism.lcdtext=<默认> prism.text=<默认> glass.platform=<默认>
渲染诊断：JavaFX 版本=25+29 JVM=Substrate VM
渲染诊断：默认字体 族=System 名称=System Regular 字号=13.0 可用字体族数=11
渲染诊断：字体实现工厂=com.sun.javafx.font.directwrite.DWFactory
渲染诊断：字形度量 14px 文本宽=263.04 高=16.3 基线=13.0
渲染诊断：屏幕 outputScale=1.0x1.0 dpi=96.0 视觉边界=Rectangle2D [minX=0.0, ...]
```

各行的含义见第 7 节。

## 6. 判定表

| 观察 | 结论 | 下一步 |
|---|---|---|
| `java.vm.name` = `Substrate VM` | 这是原生包（对照时确认来源） | — |
| 原生 `字体实现工厂=…FTFactory`，JVM 是 `…DWFactory` | **字形栅格化走了两条路** | 让 DirectWrite 在原生镜像里可用（元数据 / 原生库） |
| 两边**字体族**不同 | 字体枚举不全 | 补 `WinFontFinder` 等元数据 |
| 原生管线是 `SWPipeline`、JVM 是 `D3DPipeline` | 硬件管线在原生包里没起来 | 查 D3D 原生库加载与可达性登记 |
| 管线 / 字体族 / 工厂**三项全同**，仅字形度量不同 | 只剩 hinting / 抗锯齿参数差异 | 试 `prism.lcdtext` / `prism.text` 并记录结论 |

**四种结果都有下一步动作**，所以这次数据不会白采。

## 7. 诊断行含义

| 行 | 含义 |
|---|---|
| `渲染诊断：属性` | 我们**显式设置**的渲染属性；打 `<默认>` 表示没插手 |
| `渲染诊断：JavaFX 版本 / JVM` | 先排除「两边版本不同」这一干扰项 |
| `渲染诊断：默认字体` | 族 / 全名 / 字号 / **可用字体族数**——字体枚举是否退化 |
| `渲染诊断：字体实现工厂` | `WinFontFactory`（DirectWrite）/ `FTFactory`（内置 FreeType）等 |
| `渲染诊断：字形度量` | 同一段文字在 14px 下的宽 / 高 / 基线，**给的是数字**，不靠肉眼比图 |
| `渲染诊断：屏幕` | `outputScale` / `dpi` —— DPI 缩放的直接证据 |

## 8. 常见问题

**报告里一行诊断都没有？**
这个包不含 PR #58 的诊断代码；或日志级别被 `Aha.Logging.Level` 调到了 `WARN` 以上
（诊断是 INFO 级）。

**`字体实现工厂=不可用（InaccessibleObjectException）`？**
JVM 模式下没带 `--add-opens`。用仓库的 `bin\AhaDesktop.bat`（已带），或自行加上
`--add-opens javafx.graphics/com.sun.javafx.font=ALL-UNNAMED`。
原生包里没有这层模块限制，正常能打出来。

**日志文件很大？**
不用管，脚本只取最后一次启动的片段。

**`Log\AHA.log` 不存在？**
先确认是从哪个目录启动的 —— 日志路径相对**启动目录**；再查是否设了 `AHA_HOME`
或 `Aha.yaml` 里的 `Aha.Logging.File`。
