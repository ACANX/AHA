# TD-00015 CliNativeVersionUpdateScript

> 待办编号：TD-00015
> 标题：补 CLI 原生镜像的自动取件脚本（`CliNativeVersionUpdate.py`）
> 状态：✅ 已完成（2026-10-13）
> 跟踪 Issue：[#156](https://github.com/ACANX/AHA/issues/156)（承接单，含「一键入口」一并交付）
> 创建日期：2026-10-09
> 最后更新：2026-10-13

---

## 来源

补充 `Docs/Guide/CommandCheatsheet.md` §4.2（PR #88）时发现：**桌面端原生镜像有自动取件脚本，
CLI 原生镜像没有**，速查里只能如实写「需手动下载解压」，用户拿包要多走手工步骤。

## 现状（收尾时）

| 项 | 桌面端原生 | CLI 原生 |
|---|---|---|
| CI 产出 | `DesktopNative.yml` → `AHA-Desktop-Native-<版本>-<系统>-<架构>-jdk<JDK>.zip` | `CliNative.yml` → `AHA-Cli-Native-<版本>-<系统>-<架构>-jdk<JDK>.zip` |
| 可执行文件 | `aha-desktop-native(.exe)` | `aha-cli-native(.exe)` |
| 自动取件脚本 | ✅ `Script/Python/DesktopNativeVersionUpdate.py` | ✅ **`Script/Python/CliNativeVersionUpdate.py`（本轮补齐）** |
| Windows 一键入口 | ✅ `Dist\UpdateDesktopNative.cmd` | ✅ `Dist\UpdateCliNative.cmd` |
| 速查里的取件方式 | 一键 + 脚本 + 手动 | 一键 + 脚本 + 手动 |

## 实现（第 5 条要求：「尽量共享实现」怎么落）

按本待办的要求**没有写第二份逻辑**，而是抽共享引擎 + 两个薄壳：

| 文件 | 角色 |
|---|---|
| `Script/Python/NativeVersionUpdate.py` | **共享引擎**：发现（api→atom→page）/ 选包 / 下载（断点续传 + sha256）/ 解压 / 覆盖 + `.bak` / 状态文件 / 全部参数 |
| `Script/Python/DesktopNativeVersionUpdate.py` | 薄壳：`configure("desktop")` → 引擎（**历史命令名与行为不变**） |
| `Script/Python/CliNativeVersionUpdate.py` | 薄壳：`configure("cli")` → 引擎，参数与桌面端完全同构 |
| `Dist/UpdateDesktopNative.cmd` / `Dist/UpdateCliNative.cmd` | Windows 一键入口（双击）；2026-10-13 晚改为调用同目录的 `UpdateNative.ps1`，**不需要 Python** |
| `Dist/UpdateNative.ps1` | 上述两个入口的 PowerShell 实现（`Invoke-RestMethod` / `Invoke-WebRequest`，走 .NET Schannel TLS） |

两种包在引擎里只差 `KINDS` 表里的五项：tag 后缀、资产前缀、可执行文件名、状态文件名、
帮助里展示的用法。**新增第三种原生包 = 在表里加一行**。

## 验收标准

- [x] `python3 Script/Python/CliNativeVersionUpdate.py` 能下载并装好最新 CLI 原生镜像到 `Dist/`
      ——真机实测见下方「实测证据」；
- [x] `--list` / `--dry-run` / `--tag` / `--platform` / `--jdk` 行为与桌面端脚本一致
      ——同一份代码，参数表就是同一张；
- [x] 覆盖前旧文件留 `.bak` ——引擎既有行为，未改；
- [x] 装好后 `Dist/aha-cli-native(.exe)` 的 `version` 可正常运行 ——`AHA 0.1.3.00154` ✓；
      **`--help` 仍然失败**：这不是本次交付引入的，而是暴露了 issue
      [#117](https://github.com/ACANX/AHA/issues/117) 的根因（见下），已把证据补到那条 Issue；
- [x] `Docs/Guide/CommandCheatsheet.md` §4.2 与 `Dist/README.commands.md` 同步改为「一键 + 脚本 + 手动」；
- [x] `python3 bin/CheckScripts.py` 通过（`.py` 为 UTF-8 + LF；`.cmd` 为纯 ASCII + CRLF；
      `.ps1` 含中文时带 UTF-8 BOM）；
- [x] **Windows 侧不需要 Python**（2026-10-13 晚，按 ACANX 的要求对齐 LegionFanControl 的做法）：
      `Dist/UpdateNative.ps1` 直接走 .NET / Schannel TLS 取件；两套实现的命名契约
      （tag 后缀 / 资产前缀 / 可执行文件名）由 `bin/CheckScripts.py` **断言逐字一致**
      （反向验证：改错一个字母 / 去掉 BOM / `.cmd` 里写中文，三项都如期失败）。
      背景与排查见 `TS-2610-036`。

## 实测证据（2026-10-13，WSL Ubuntu 26.04）

```text
$ python3 Script/Python/CliNativeVersionUpdate.py --list
ACANX/AHA 最近的 CLI 原生镜像（-aha-cli-native）版本（本平台优先：ubuntu-amd64 / ubuntu-arm64）：
  * V0.1.3.00154-aha-cli-native  版本 0.1.3.00154  [可下载]  AHA-Cli-Native-0.1.3.00154-ubuntu-amd64-jdk25.zip
  * V0.1.3.00150-aha-cli-native  版本 0.1.3.00150  [可下载]  AHA-Cli-Native-0.1.3.00150-ubuntu-amd64-jdk25.zip
  （候选来自 api；加 --tag 可指定版本，加 --source 可换发现路径）

$ python3 Script/Python/CliNativeVersionUpdate.py
目标版本：V0.1.3.00154-aha-cli-native（版本 0.1.3.00154，来源 api）
下载资产：AHA-Cli-Native-0.1.3.00154-ubuntu-amd64-jdk25.zip
已下载：…（41.3 MiB）
已安装：/…/AHA/Dist/aha-cli-native（92.6 MiB）
✅ 已更新到 0.1.3.00154

$ ./Dist/aha-cli-native version
AHA 0.1.3.00154
```

另外两处可判别性：`--list` 自动跳过 `V*-aha-jvm` 系列（不是原生包）与
`-Report.zip`（报告包，与镜像包同版本号、只差结尾）；状态文件为 `.aha-cli-native.json`，
与桌面端 `.aha-desktop-native.json` 各自独立，不会互相误判「已是最新」。

## 顺带发现（转跟踪，不在本待办内）

`./Dist/aha-cli-native --help` 在本机 **崩溃**：

```text
Exception in thread "main" java.lang.InternalError: Can't load sun/text/resources/LineBreakIteratorData
	at …BreakIteratorResourceBundle.handleGetObject(BreakIteratorResourceBundle.java:75)
	at …picocli.CommandLine$Help$TextTable.putValue(CommandLine.java:17422)
```

**根因**：这是原生镜像的资源清单缺口——桌面端 `aha-desktop-native/src/native/native-image-args.txt`
里有 `-H:IncludeResources=sun/text/resources/LineBreakIteratorData`（第 127 行），
**CLI 的对应文件里没有**（`aha-cli-native/src/native/native-image-args.txt` 只有两条通配规则）。
⇒ 已把栈与根因补到 issue #117（该 Issue 的「待排查方向 1」正是「help 渲染所需的资源」）。

## 关联

- `Script/Python/NativeVersionUpdate.py`（共享引擎）、`Script/Python/DesktopNativeVersionUpdate.py`（蓝本，现为薄壳）
- `.github/workflows/CliNative.yml` —— 产物与命名
- `Docs/Design/CliNativeDesign.md` §7.1、`Docs/Design/DesktopNativeUpdateDesign.md`（引擎与两种包的契约）
- `Docs/Guide/CommandCheatsheet.md` §4.1 / §4.2、`Dist/README.commands.md`
- Issue #156（交付单）、issue #117（顺带发现的 CLI `--help` 资源缺口）
