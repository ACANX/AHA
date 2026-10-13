# AHA 桌面端原生镜像版本更新脚本设计（试验性）

> **文档版本**：v1.1.0
> **状态**：草案（试验性能力的配套工具，随实现迭代）
> **生效日期**：2026-10-09
> **最后更新**：2026-10-13
> **负责人**：@ACANX
> **适用版本**：AHA 0.1.x / 0.2 开发期
> **定位**：原生镜像取件工具的设计、参数、边界与验证记录——**桌面端与 CLI 两种包共用一套引擎**
> （`Script/Python/NativeVersionUpdate.py`）；用户入口是 `DesktopNativeVersionUpdate.py` /
> `CliNativeVersionUpdate.py` 两个薄壳，Windows 一键入口为 `Dist/Update*.cmd`

## 0. 本文定位

[DesktopNativeDesign.md](DesktopNativeDesign.md) 回答「原生镜像怎么**编**出来」；本文回答
「编出来的东西怎么**一键取到本机**」。

取一个验证包原本是四步手工操作：

1. 打开发布页，找到最新的 `V<版本>-aha-desktop-native` 预发行版；
2. 在这条 release 的资产里，按「系统-架构-jdk」挑对自己平台的那个 zip；
3. 下载并解压；
4. 把可执行文件放到 `Dist/`，再执行 `.\Dist\aha-desktop-native.exe`。

这四步里有三步要求「看仔细」（tag 名、平台标签、JDK 轴），看错一步就要重来；版本号还带
5 位构建号，肉眼比较新旧很容易出错。脚本把四步压成一条命令：

```bat
python3 Script\Python\DesktopNativeVersionUpdate.py
```

（POSIX 下把路径分隔符换成 `/`；个别环境里命令是 `python` 而非 `python3`。）
用法由 `bin/CheckScripts.py` 的脚本规约守卫（`.py` 需 shebang + LF），不需要另立检查。

**2026-10-13（issue #156 / TD-00015）：CLI 原生包并入同一套逻辑。** 引擎抽成
`Script/Python/NativeVersionUpdate.py`，两种包只在引擎的 `KINDS` 表里差五项——tag 后缀、
资产前缀、可执行文件名、状态文件名、帮助里展示的用法：

| 种类 | 入口脚本（薄壳） | Windows 一键入口（双击） |
|---|---|---|
| 桌面端 | `Script/Python/DesktopNativeVersionUpdate.py` | `Dist\UpdateDesktopNative.cmd` |
| CLI | `Script/Python/CliNativeVersionUpdate.py` | `Dist\UpdateCliNative.cmd` |

历史命令名 `DesktopNativeVersionUpdate.py` **保持可用且行为不变**（现在只是薄壳）。
曾考虑给单个入口加 `--kind` 参数，最终选「共享引擎 + 两个薄壳」：薄壳名就是文档名，
双击入口不必再记参数，而共享逻辑仍然只有一份（这是 TD-00015 明确要求的）。

三条边界先写清楚：

1. **只做取件与落盘**：不改构建、不改 CI、不参与发版，也不碰 `AHA_HOME` 下的用户数据；
2. **只服务旁路**：正式交付仍是 JVM 包（见 [DesktopNativeDesign.md](DesktopNativeDesign.md)
   第 1 节），原生镜像的定位是「下载即用」的验证包；
3. **网络是唯一外部依赖**：离线时只报错，不做任何「猜一个版本」的降级。

## 1. 目标与非目标

### 1.1 目标

| # | 目标 | 判据 |
|---|---|---|
| G1 | 一条命令从零到可运行 | 命令执行后 `Dist\aha-desktop-native.exe` 存在且为本平台最新可下载版本 |
| G2 | 版本号由机器解析，不靠人眼 | tag 解析出基线 `0.1.1` 与构建号 `00040`，下载链接按命名契约拼出 |
| G3 | 最新 tag 缺本平台包时不失败 | 自动回退到「次新且含本平台包」的版本 |
| G4 | 网络中断可续 | 重跑从 `.part` 续传，已下载部分不损失 |
| G5 | 装错可回退 | 旧可执行文件留 `.bak`；新文件先落 `.new` 再原子替换 |
| G6 | 下载与落盘可自证 | API 路径按 sha256 digest 校验；解压出的可执行文件记 sha256 |

### 1.2 非目标

- 不做签名校验与来源认证：当前信任边界是 HTTPS + GitHub 仓库本身；
- 不解析可执行文件**内部**的版本号（那是
  [DesktopNativeDesign.md](DesktopNativeDesign.md) 第 7 节 `N-4` 的事）；
- 不覆盖 `cli-native`：命名同族（`AHA-Cli-Native-*`），留作后续（见第 11 节 `U-1`）；
- 不做常驻「检查更新」进程：按需执行，不引入后台任务。

## 2. 命名契约（唯一来源）

脚本不复制任何版本字面量，全部由 tag 与资产名推导。契约的每一段都在工作流里有出处：

| 名称 | 形态 | 出处 |
|---|---|---|
| release tag | `V<基线>.<构建号>-aha-desktop-native` | `DesktopNative.yml` 的 `native-publish` |
| 构建号 | PR 号补零到 5 位，如 `00040` | `DesktopNative.yml` 的 `native-version`（`printf '%05d'`） |
| 镜像包 | `AHA-Desktop-Native-<版本>-<平台标签>-jdk<JDK>.zip` | `DesktopNative.yml` 的「改名为发布用资产名」 |
| 报告包 | 同名 + `-Report` 后缀 | 同上的独立报告包（脚本**不取**） |
| 平台标签 | `windows-amd64` / `ubuntu-amd64` / `macos-arm64` | `DesktopNative.yml` 的 `native-image` matrix |
| JDK 轴 | `jdk25`（基线）/ `jdk27`（试验） | 同上的 matrix |

脚本侧的两个断言与之对应：

```text
tag    = ^[Vv]?(\d+(?:\.\d+)*?)\.(\d+)-aha-desktop-native$
资产    = ^AHA-Desktop-Native-(?P<version>\d+(?:\.\d+)*)-(?P<label>[A-Za-z0-9_.-]+)-jdk(?P<jdk>\d+)\.zip$
```

**只认各自后缀**这一条是有代价、也是有意为之的：正式版 tag（`V0.1.0`）、
历史实验 tag（`native-v0.1.1.00024`、`aha-desktop-native-v0.1.1.00030`）都不匹配。
宁可「看不见不认识的 tag」，也不要「把正式版当预发行版下载」——后者会静默覆盖
用户的本机验证环境。资产名里的 `<version>` 必须与 tag 解析出的版本**精确相等**，
这也顺手排除了 `-Report.zip`（版本相同但结尾不匹配）。

**CLI 原生包与之同构**（`CliNativeVersionUpdate.py`）：tag 为 `V<基线>.<构建号>-aha-cli-native`，
资产为 `AHA-Cli-Native-<版本>-<平台标签>-jdk<JDK>.zip`，可执行文件为 `aha-cli-native(.exe)`，
出处是同名的 `CliNative.yml`。两者正则形状完全一致，只有后缀 / 前缀不同——这正是
一张 `KINDS` 表能覆盖两种包的原因；也是「桌面端入口永远不会去取 CLI 的包」的原因
（互不串台：`cli-native` 的 tag 不匹配桌面端的后缀，反之亦然）。

## 3. 三条发现路径

| 路径 | 能拿到什么 | 限额 / 失效点 |
|---|---|---|
| `api`（GitHub REST） | 全部 release、完整资产清单、**sha256 digest** | 未认证按 IP 限流（60 次/小时）；需 `$HTTPS_PROXY` 可达 |
| `atom`（`releases.atom`） | 最近若干条 tag（含预发行版） | 无资产信息，需按命名契约拼链接再 HEAD 探测 |
| `page`（releases 页面 HTML） | 服务端渲染出来的 tag 链接 | 页面改版即失效，故只作最后兜底 |

默认 `--source auto`：**api → atom → page** 依次尝试，任一成功即止。`api` 一步就能拿到
「版本 + 资产 + digest」，因此它是常态路径；`atom` 与 `page` 存在的意义是「API 被限流时
仍然能用」，而不是「更快的路径」。

显式指定 `--source api|atom|page` 时**不回退**：失败即报错。这条是给排障用的——
要确认「到底是哪条路径坏了」，就必须让它能单独坏掉。

若环境里配了 `GITHUB_TOKEN` 或 `GH_TOKEN`，`api` 请求会带上 `Authorization`。脚本一次运行
只需要 1~2 次请求，但开发期反复试跑很容易撞上 60 次/小时的上限，因此支持但不强制。

## 4. 「最新 tag」不等于「能下载的那个」

这是本脚本最容易被忽略、也最容易让用户「刚看到新版本却下载失败」的一点：

- release **先建后传**：`native-publish` 先创建 release，再挂资产；
- 三条腿**并发**：`native-image` 的 ubuntu / windows / macOS 各自构建，先编完的先上传。

于是在新版本出现后的几分钟里，「最新 tag 存在，但缺本平台的包」是常态。脚本因此按
`(基线, 构建号)` 降序**逐个 release** 找第一个「真的存在本平台资产」的版本，而不是只认
第一个 tag。

选择资产分两条分支：

| 情形 | 做法 | 理由 |
|---|---|---|
| 资产清单已知（`api`，或 `--tag` 时的 `expanded_assets`） | 只做名字匹配 | 清单里没有就是没有，猜 URL 只是白打一枪 |
| 资产清单未知（`atom` / `page`） | 按命名契约拼 URL + `HEAD` 探测 | tag 已知、资产名可推导，一次 HEAD 即可定位 |

`HEAD` 探测的尝试顺序是「平台标签（按本机优先级）× JDK（默认 `25`，其次 `27`）」。
显式 `--jdk` 时只试该分支，不再兜底——用户说 27 就该是 27，拿 25 顶上属于「自作主张」。

找不到时的错误信息必须可操作，而不是只说「失败」：

```text
[错误] 最近 5 个 release 里都没有 windows-amd64（jdk27）的包；
       可用 --list 查看有哪些平台可下载，或用 --platform / --jdk 指定
```

## 5. 下载

- **目标文件名固定为发布资产名**（`Dist/<资产名>.zip`）。复用、续传、`--no-keep-zip`
  的清理都以同一个文件为准；若为「不保留」另起一个临时名，反而会绕过复用、每次重下一遍。
- **复用判定**：本地文件大小与资产的 `size` 相等，或按 `digest` 校验通过，即跳过下载。
- **断点续传**：若存在 `<目标名>.part`，以 `Range: bytes=<n>-` 续传。
  响应 `206` 追加写入，`200` 说明服务器不支持续传、从头写入，`416` 说明区间越界
  （通常是上次已下完但没来得及改名），删除 `.part` 重下。
- **校验**：下载完成后按 `sha256:<hex>` 校验（仅 `api` 路径有 digest），
  失败即删除文件并报错，不留下「看起来下完了」的坏包。`size` 作为第二道兜底。
- **进度输出到 `stderr`**，`stdout` 只留结果与计划，便于管道与日志分流。
- **下载器有两条路（2026-10-13，issue #156）**：默认 `urllib`（Python 自带的 TLS）；
  Windows 上 `auto` 会先用**子进程**探一次 HTTPS，不可用就改用系统 `curl.exe`（系统 TLS）。
  为什么非要多这一层：部分 Windows 机器上 Python 的 OpenSSL 运行时会**硬失败**
  （`OPENSSL_Uplink ... no OPENSSL_Applink`），而那种失败**在进程内捕捉不到**——进程被直接终结，
  连 traceback 都打不出来，只能靠「换一条不经过 Python TLS 的路」自救；
  探测之所以必须放在子进程，就是因为它连异常都来不及抛。详见 [`TS-2610-036`](../Troubleshooting/TS-2610-036-Windows-Python-OpenSSL-Applink-Failure.md)。
  curl 路径同样支持续传（`-C -`，服务器不认 Range 时自动退回完整下载）。

## 6. 安装

1. **先探测可替换性**：Windows 上正在运行的 exe 既不能改名也不能覆盖（镜像文件被独占
   映射）。下载前先以「能否以写模式打开」探测一次——下载是分钟级的，不能等下完 50+ MB
   才说「文件被占着」。Linux 上运行中的文件也总能打开，因此这条检查不会误报。
2. **只取可执行文件**：包内还有 `README.txt`、`CHANGELOG.md`、`native-image-args*.txt`、
   `reachability-metadata.json`，它们属于「包」而不是「可运行的 exe」，铺到 `Dist/`
   只会造成混乱。
3. **原子替换**：先解压为同目录的 `<可执行文件>.new`，再 `os.replace` 覆盖目标；
   失败时旧版仍可用。非 Windows 目标会补上可执行位。
4. **旧版备份**：目标已存在时复制一份 `<可执行文件>.bak`。备份失败只提醒、不阻塞
   （备份是便利，不是正确性前提）。

## 7. 状态与幂等

安装成功后写 `Dist/.aha-desktop-native.json`（`Dist/` 已被 `.gitignore` 忽略）：

| 字段 | 含义 |
|---|---|
| `tag` | 本次安装的 release tag |
| `version` | 解析出的版本，如 `0.1.1.00040` |
| `asset` | 使用的资产名 |
| `url` | 实际下载地址 |
| `executable` | 安装后的绝对路径 |
| `size` | 可执行文件字节数 |
| `sha256` | 可执行文件摘要（落盘后的自证） |
| `installed_at` | 本地时间 |

判定「已是最新」的条件是 `state.tag == 选中的 tag` **且**目标可执行文件存在。
两条都满足就直接退出，避免重复下载 50+ MB。

`--force` 与 `--redownload` 是两件事，刻意分开：

| 参数 | 作用 |
|---|---|
| `--force` | 忽略「已是最新」，重新安装；**完整可用的本地 zip 会复用** |
| `--redownload` | 强制重新下载，不复用本地 zip（本地包损坏但大小恰好相等时用） |

## 8. CLI 参数

| 参数 | 默认 | 说明 |
|---|---|---|
| `--repo` | `ACANX/AHA` | 仓库 |
| `--tag` | 自动 | 指定 release tag，跳过「找最新」 |
| `--platform` | 按本机探测 | 发布平台标签（`windows-amd64` 等） |
| `--jdk` | `25` | JDK 分支；无该分支时报错而非静默替换 |
| `--dist` | 仓库根 `Dist/` | 安装目录 |
| `--source` | `auto` | `auto` / `api` / `atom` / `page` |
| `--downloader` | `auto` | `auto` / `urllib` / `curl`；Windows 的 `auto` 先探 Python 的 HTTPS，失败则用系统 curl |
| `--timeout` | `30` | 元数据请求超时（秒） |
| `--download-timeout` | `600` | 下载超时（秒） |
| `--force` | 关 | 忽略「已是最新」 |
| `--redownload` | 关 | 强制重新下载 |
| `--dry-run` | 关 | 只打印计划与链接 |
| `--list` / `--limit` | 关 / `5` | 列出最近版本与可下载资产 |
| `--no-keep-zip` | 保留 | 安装后删除 `Dist/` 里的 zip |
| `--no-backup` | 备份 | 不生成 `.bak` |

## 9. 验证记录（2026-10-09）

环境：WSL2 + Python 3.10.12，链路约 10.7 MiB/s；运行环境为 Linux，目标平台用
`--platform windows-amd64` 显式指定（目的是验证 Windows 包这条真实路径）。

| # | 场景 | 结果 |
|---|---|---|
| V1 | `--dry-run` | 解析到 `V0.1.1.00040-aha-desktop-native` 与 `AHA-Desktop-Native-0.1.1.00040-windows-amd64-jdk25.zip`，链接与发布页一致 |
| V2 | 完整安装 | `57,392,030` 字节 zip → `146,735,104` 字节 exe，耗时约 7.2 s |
| V3 | 落盘自证 | exe 的 sha256 `1bc3edd0…e2368` 与 zip 内条目一致 |
| V4 | 断点续传 | 预置 `10,000,000` 字节 `.part`，从 19.3% 续传到 100% |
| V5 | 三条发现路径 | `api` / `atom` / `page` 均得到同一 tag 与同一资产 |
| V6 | 幂等与复用 | 重复运行提示「已是最新」；`--force` 复用已下载的 zip |
| V7 | `--no-keep-zip` | 安装完成后 `Dist/` 内不再保留 zip |
| V8 | 不存在的分支 | `--jdk 27`（该版本无此分支）给出明确错误与可用手段 |
| V9 | 脚本规约 | `python3 bin/CheckScripts.py` 通过（shebang + LF） |

## 10. 已知限制

- `atom` / `page` 路径没有 sha256 digest，只能靠 `size` 与 zip 自身的 CRC 兜底；
- 未在 macOS 上实测（选择逻辑与平台无关，但 `macos-arm64` 标签未验证）；
- 可执行文件**内部**的版本号与文件名/包名无关（`DesktopNativeDesign.md` 第 7 节 `N-4`），
  脚本只能校验「装的是哪个包」，不能校验「跑起来自报哪个版本」；
- 未做限流后的重试与退避：`auto` 会退到 `atom`，对当前使用频率足够；
- `.gitignore` 里的 `Dist/` 在大小写不敏感的文件系统上同时命中 `Dist/`，
  因此状态文件与 zip 不会被误提交——这是依赖既有规则，不是本脚本额外做的保护。
- **Windows 上 Python 自身的 HTTPS 可能不可用**（OpenSSL `no OPENSSL_Applink`）：
  工具已自动改走系统 `curl.exe` 兜底（§5），但**下载器救不了 Python 完全坏掉**的情形——
  `bin/Check*.py`、`DesktopDistBuild.py` 等仍需可用的 Python。

## 11. 后续路线

| # | 事项 | 触发条件 |
|---|---|---|
| U-1 | ✅ **已完成**（2026-10-13，issue #156）：与 `cli-native` 共用同一套逻辑——共享引擎 + 两个薄壳（见第 0 节） | — |
| U-2 | `--json` / `--check` 输出，便于 CI 或桌面端「检查更新」复用 | 有第二个调用方时 |
| U-3 | macOS 真机验证 | 拿到 macOS 环境后与 `DesktopNativeDesign.md` 第 7 节 `N-1` 一起做 |
| U-4 | 目录归属：按 `TODO.md` `F-07` 的结论决定是否并入 `bin/` | `F-07` 有结论时 |
| U-5 | 下载源可配置（企业代理 / 镜像站） | 出现明确的网络受限场景时 |

## 12. 相关文档

- [DesktopNativeDesign.md](DesktopNativeDesign.md)：原生镜像的构建管线、隔离与迭代指南
- [CliNativeDesign.md](CliNativeDesign.md)：CLI 原生镜像的构建管线（取件由本工具覆盖）
- [BuildSpec.md](../DevSpec/BuildSpec.md)：构建与检查分层（含跨平台脚本规约）
- [TD-00015](../TODO/TD-00015-CliNativeVersionUpdateScript.md)：CLI 原生镜像自动取件脚本（已收尾）
- [TODO.md](../TODO.md)：`F-07`（`Script/` 目录归属）、`N-04`（JDK 轴）
- Issue #156：一键入口与 CLI 取件的来源单
- 实现：`Script/Python/NativeVersionUpdate.py`（共享引擎）、
  `DesktopNativeVersionUpdate.py` / `CliNativeVersionUpdate.py`（两个薄壳）、
  `Dist/UpdateDesktopNative.cmd` / `Dist/UpdateCliNative.cmd`（Windows 一键入口）
