# 桌面端应用内升级设计

> **文档版本**：v1.2.2
> **状态**：生效（Windows 侧已实现；Linux / macOS 的应用内升级**后置**，见 §1 边界）
> **生效日期**：2026-10-16
> **最后更新**：2026-10-16
> **负责人**：@ACANX
> **适用版本**：AHA 0.2 起
> **跟踪 Issue**：[#51](https://github.com/ACANX/AHA/issues/51)

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.2.2 | 2026-10-16 | 第三轮审查：① **能力门接到 CLI 上**（`--update-to --yes` 原本绕过门，在「只剩单体 jar」的布局上会换装却不重启）；② §5.4 补「两层门 + 界面与 CLI 都要过」；③ §8.2 增第 12/13 项验收口径；④ `UpdateCommand.run` 拆出「注入当前版本」的重载，让能力门这类逻辑能在本地验（版本资源是 Maven 过滤出来的，单测里拿不到真值） | @ACANX / CNXNC |
| v1.2.1 | 2026-10-16 | 修一个 CI（Windows 腿）暴露的**产品**问题：helper 里 `Get-FileHash` 依赖调用者的 PowerShell 环境（pwsh 7 的 `PSModulePath` 会让 5.1 加载不到 Utility 模块）⇒ 自证失败并回滚；改用 .NET 算哈希，并把这条约束记入 §4.4（自测用例 4 就是它的回归测试） | @ACANX / CNXNC |
| v1.2.0 | 2026-10-16 | 按**第二轮**审查优化：① **启动收尾不再碰在飞的换装**（计划在 + 标记新 + 无回执 ⇒ 跳过清理，窗口 10 分钟；第二轮 P1-②）；② **`restart` 契约与 jar 兜底**：不再给 `java -jar` 兜底，改成安装形态门提前拦住（第二轮 P1-①）；③ 清理计数**如实**（`StagingArea.clear` 返回是否真删，第二轮 P2-⑤）；④ 换装互斥（`prepareSwap` 拒绝第二个 helper）；⑤ helper 用完消失（`.cmd` 由启动收尾补刀，§8.2 第 5 项口径随之明确）；⑥ 文档口径修正：`.part` 不是「可续传」 | @ACANX / CNXNC |
| v1.1.0 | 2026-10-16 | 按代码审查结论补三处契约与三处接线：① **helper「已启动」握手**（helper 第一件事写 `helper-started`，App 等它出现才退出，等不到就留在前台报错 —— 原来「helper 没起来」是静默的）；② **启动收尾**（读回执 + 清理暂存与旧备份 —— 原来这两件事在生产代码里没有调用点）；③ **能力门**（按「本平台有没有换装脚本」置灰，而不是下完 55 MB 才报「该包不完整」）；§8.1 补齐新增证据、§8.2 增第 10/11 项 | @ACANX / CNXNC |
| v1.0.0 | 2026-10-16 | 初版：需求映射、通道与契约、服务层职责、换装协议（计划文件 / helper）、界面与配置键、安全模型、**验证记录**（哪些已验、哪些待真人验） | @ACANX / CNXNC |

---

## 0. 本文定位

| 文档 | 回答什么 |
|---|---|
| [DesktopNativeUpdateDesign.md](DesktopNativeUpdateDesign.md) | 「原生镜像包怎么**手工一键取到本机**」（脚本工具，0.1.x 的既有能力） |
| **本文** | 「**程序自己**怎么发现、下载、校验、替换并重启」（0.2 起的应用内升级） |
| [ReleaseProcess.md](../DevSpec/ReleaseProcess.md) §3.4 / §3.5 | 发布资产与**通道契约**（tag / 资产名 / 清单 / 校验和）——本文只引用，不重复定义 |
| [BuildSpec.md](../DevSpec/BuildSpec.md) §7.1 | 四条出包线怎么写清单与校验和 |

两者是**互补**关系：脚本工具在「程序坏到起不来」时仍然能用（它只依赖 PowerShell / Python），
应用内升级在「程序能起来」时更省事。任一方都不能替代另一方。

## 1. 需求与边界

需求（R1–R8）与决策（D1–D10）的完整记录在 issue [#51](https://github.com/ACANX/AHA/issues/51)，
此处只列**边界**——凡是被明确排除的，都不要在实现里顺手加回来：

- **不做增量更新**：只下整包（25–55 MB）。diff 的复杂度与失败面远大于收益；
- **不做后台常驻 / 静默安装**：只在用户点「检查更新」或（可选）启动时**检查**；
- **不做形态切换**（JVM 便携包 ↔ 原生单文件）：跨形态要处理运行时依赖差异，另立方案；
- **不做系统级安装**（`jpackage` / 系统包管理器）：发行形态已定「便携包 + 原生单文件」（`ReleaseProcess.md` §3.3）；
- **不提权、不改 PATH / 注册表 / 快捷方式**；
- **Linux / macOS 的应用内升级本期不实现、不验证**：代码保持可编译、服务层跨平台无差别，
  界面必须**明确写清**并给手动取件指引（§5.4）；
- **不把 Alpha 当交付物**：快照包 7 天有效期，界面上必须写明。

## 2. 通道与契约

三通道（`stable` / `beta` / `alpha`）的 tag 形态、资产名、清单与校验和的定义**全部在**
[ReleaseProcess.md](../DevSpec/ReleaseProcess.md) §3.5；三处实现（PowerShell / Python / Java）
的一致性由 `bin/CheckScripts.py` 的 `check_release_channel_contract()` 钉住。

**默认通道暂定 `Beta`**（D9）：Stable 当前没有可下包。这条默认值的落实机制见 §6 的「缺键不写盘」，
配套待办 [TD-00019](../TODO/TD-00019-DefaultChannelBackToStable.md)。

## 3. 服务层（`aha-core`，无 JavaFX 依赖）

落在 `com.acanx.module.aha.core.update`，桌面端与将来的 CLI 共用同一套实现（不做两套）：

| 类 | 职责 | 关键约束 |
|---|---|---|
| `AhaVersion` / `ReleaseChannel` / `UpdateKind` / `ChannelContract` | 版本模型与通道契约 | 命名规则**只此一处**，双向（生成 + 解析） |
| `PlatformTag` | `os.name` / `os.arch` → 平台标签 | 与「应用内升级是否实现」是**两个维度**，别混 |
| `GitHubReleaseClient` / `ReleaseInfo` / `AssetInfo` / `ReleaseFilter` | 读发布页、按通道过滤 | 翻页上限 2 页；识别限流；**不重试**（重试放大限流） |
| `AssetSelector` | 在**某一个版本自己的**资产里按「形态 → 平台 → JDK 轴」挑 | 挑不到**响亮报错并列出该版实际资产**，绝不退到旧版（issue #210 的教训） |
| `PackageDownloader` / `PackageUnpacker` / `Checksums` | 下载、解包、校验和 | 先写 `.part` 再改名；只跟随白名单跳转；zip-slip 在解包处拦；**校验和不符不落地** |
| `PackageManifest` | 包内清单解析与逐项核对 | 路径逃逸、大小或哈希不符一律拒绝 |
| `UpdateService` | 把上面串成「发现 → 挑资产 → 下载 → 校验 → 解包 → 落暂存 → 写计划」 | **只落暂存、只写计划**，绝不自己替换文件 |
| `UpdatePaths` / `StagingArea` / `UpdateLog` / `UpdatePlan` / `PathSafety` | 暂存、日志、计划 | 一切都在 `~/.aha/update/` 之下；计划里的路径必须绝对、动作路径必须落在安装根内 |
| `InstallLayout` / `SwapPlan` | 安装根与形态判定、动作列表与备份/回滚 | **只动清单内文件**；两份清单都没有的一律不碰 |
| `UpdateLauncher` / `UpdateResult` / `UpdateCommand` | 拉起 helper、回执、无界面入口 | helper 放 `~/.aha/update/`（用完就该消失、也不该出现在被替换的目录里：`.ps1` 自己删自己，`.cmd` 由 App 启动收尾补刀） |

## 4. 换装协议

### 4.1 为什么必须由外部 helper 完成

正在运行的进程**不能可靠地替换自己的文件**：Windows 上被使用的 `.exe` / `.jar` 通常被占用；
即便改名成功，也只剩「下次启动才生效」的半程。所以顺序固定为：

| 步 | 谁做 | 做什么 |
|---|---|---|
| 1 | App | 备好暂存、算动作、写 `update-plan.json`、把 helper 复制到 `~/.aha/update/` |
| 2 | App | **脱离式**拉起 helper（Windows `cmd /c start "" /b …`；POSIX `sh -c 'nohup … &'`） |
| 3 | App | 界面显示「正在替换并重启…」后**主动退出** |
| 2.5 | helper | 起来的**第一件事**：写 `~/.aha/update/helper-started`（「已启动」标记）—— App 等它出现才退出 |
| 3.5 | App | 等到标记（默认 20s）才真正退出；**等不到** ⇒ 留在前台报错（`UPDATE_HELPER_NOT_STARTED`），安装目录未被改动 |
| 4 | helper | 等 App 退出（轮询 pid，超时 60s）→ **备份 → 替换 → 逐文件按清单哈希自证** → 重新拉起 → 写回执 → 自删（best-effort） |
| 5 | helper | 任一步失败 ⇒ 用备份**回滚**，写 `ok:false` 回执，**仍然尝试拉起旧版本**（回滚本身也包了兜底：脚本不会因为回滚抛异常而崩掉） |
| 6 | 下次启动的 App | **启动收尾**：读回执（**读完即消费**）+ 清理暂存（本次运行版本与失败残留）+ 按 `Update.BackupKeep` 裁剪旧备份，然后把结果告诉用户（成功 / 失败 / 已回滚 / helper 没起来） |

### 4.2 `update-plan.json` 字段（App 与 helper 之间唯一的接口）

| 字段 | 含义 |
|---|---|
| `version` | 目标版本 |
| `installRoot` | 安装根（绝对路径） |
| `packageRoot` | 解压后的新包目录（绝对路径）—— helper 从这里拷文件 |
| `backupDir` | 备份目录（绝对路径） |
| `pid` | App 进程号（helper 据此等它退出） |
| `restart` | 换装成功后拉起 App 的命令（绝对路径） |
| `actions[]` | `{path, kind}`；`kind` ∈ `replace` / `delete`（delete 也是**移入备份**，不真删） |
| `createdAt` | 计划生成时间（ISO-8601） |

写计划前会校验：安装根 / 新包目录 / 备份目录 / 拉起命令必须绝对；动作路径必须落在安装根内
（`PathSafety`，与包内清单共用同一套底线）。

### 4.4 「已启动」握手与启动收尾（2026-10-16 审查补）

这两条都是为了让**失败不静默**、**磁盘不无限涨**：

| 契约 | 内容 | 为什么必须 |
|---|---|---|
| 「已启动」标记 | helper 第一件事写 `~/.aha/update/helper-started`；App 拉起 helper 后**等它出现**（默认 20s，`UpdateService.HELPER_START_TIMEOUT`）才退出；超时抛 `UPDATE_HELPER_NOT_STARTED`，App 继续运行并如实说明 | 以前 App 先退出、helper 后干活：helper 若被机器级执行策略拦下、或被启动器塞进随父进程退出的作业对象，用户看到的是「App 关了、什么都没发生」。名字由 `bin/CheckScripts.py` 的守卫钉住 Java 与 PowerShell 两侧逐字一致 |
| 拉起前清标记 | `launchHelper` 先删旧标记，再拉起，再等 | 残留标记会让等待「秒过」，等于没有握手 |
| 启动收尾 | `UpdateService.onStartup(运行版本, 保留份数)`：消费回执 + 清本次运行版本与**其它版本**的暂存 + 裁剪旧备份；失败只记日志不抛 | 这两件事以前在**生产代码里没有调用点**：回执从不展示；`pkg.zip` 与解包副本（每份 50–110 MB）永不清理；`Update.BackupKeep` 成了死配置 |
| 计划与 helper 的先后 | 先 `installHelpers` 成功，**再**写 `update-plan.json` | 反过来的话，helper 缺失时会留下一个永远不会执行的计划文件，下次启动还会把它当成「上次换装没做完」报告 |
| 换装中不做收尾 | 启动收尾先看「有没有换装在飞」（计划在 + 启动标记是 10 分钟内写的 + 还没有回执）⇒ **什么都不删、什么都不删计划**，只把这件事说出来（`UpdateService.SWAP_IN_FLIGHT_WINDOW`） | 换装那几秒里用户再启动一次 App 时，helper 正要从 `<版本>/new` 拷文件、回滚还要用 `backup/`；以前这里无条件清，会把本次升级毁掉，而回执把锅甩给包（第二轮审查 P1-②） |
| 清理计数如实 | `UpdateService.Cleanup` 分别报「暂存目录数 / 旧备份份数」（`StagingArea.clear` 返回「真的删了没有」） | 以前固定 `+1`，什么都没清也会说「清理 1 个暂存目录」（第二轮审查 P2-⑤） |
| 换装互斥 | `prepareSwap` 发现换装还在飞 ⇒ 直接拒绝再写一份计划 | 两个 helper 同时换装会把安装目录搅成两半 |
| 安装形态门 | `UpdateLauncher.unsupportedReason(layout)`：启动命令为空（只剩单体 jar）也提前拦住 | `java -jar` 是「命令 + 参数」，塞不进计划里那个「每个元素 = 一个可执行文件」的 `restart` 列表（第二轮审查 P1-①）；宁可不换，也不换完不回来 |
| helper 不许依赖调用者的 PowerShell 环境 | helper（`cmd → powershell 5.1`）里算哈希用 .NET（`SHA256.Create()` + 流），**不用 `Get-FileHash`**；计划自检也只用最朴素的判断（`IsPathRooted` / 非空 / 不含 `..`） | 2026-10-16 CI（Windows 腿）实测：自测跑在 pwsh 7 里 ⇒ 子进程继承 pwsh 7 的 `PSModulePath` ⇒ 5.1 加载不到 `Microsoft.PowerShell.Utility` ⇒ `Get-FileHash` 报「不是 cmdlet」⇒ 自证失败 ⇒ 回滚（**回滚把这次失败兜住了**，但一次本该成功的换装变成了失败）。用户从 pwsh 7 控制台起 App 是同样的路径 |

### 4.3 只动清单里的文件

判定只看两份包内清单：新包清单里有 ⇒ `replace`；已装清单里有而新包没有 ⇒ `delete`；
**两份都没有 ⇒ 一律不碰**。原因：`Dist/` 里除了构建产物还有**被跟踪的手写入口**
（`UpdateJvm.ps1`、`Update*.cmd`、`README.commands.md`）以及用户自己放的东西，
整个目录替换会误删。

## 5. 界面

### 5.1 设置改 TabPane

设置面板分「界面」与「升级」两个 tab；「界面」tab 的内容是原有三块（主题 / 字号 / 默认模型），
行为不变。「升级」tab 由 `UpgradePane` 程序化构建。

### 5.2 控件 id（冒烟测试按它断言）

`aha.settings.tab.interface` / `aha.settings.tab.upgrade` / `aha.upgrade.channel` /
`aha.upgrade.current` / `aha.upgrade.check` / `aha.upgrade.list` / `aha.upgrade.status`，
另有 `aha.upgrade.action`（升级 / 降级按钮）与 `aha.upgrade.progress`（进度条）。

### 5.3 状态机

```
IDLE ──检查──▶ CHECKING ──▶ LISTED ──选中──▶ READY
                                              │升级 / 降级
                                              ▼
                                        CONFIRM（降级必确认）
                                              ▼
                            DOWNLOADING ─▶ VERIFYING ─▶ STAGING ─▶ SWAPPING（退出中）
                                  │
                                  └─ 失败 ⇒ 可读错误 + 下一步（限流说「稍后再试」）
```

版本列表每一行回答：**这是什么版本**（标记 + 版本 + 时间）、**相对当前是升还是降**、
**能不能用在本机**（缺本平台资产 ⇒ 灰显 + 悬浮说明原因）、**点下去会发生什么**（按钮文案）。
空列表要分清三种原因：本通道没发过包 / 该平台形态没资产 / Alpha 已过期 —— **不能都写成「已是最新」**。

### 5.4 平台在界面上的表达

| 平台 | 检查更新 | 升级 / 降级 |
|---|---|---|
| Windows | ✅ | ✅（本期实现并验证） |
| Linux / macOS | ✅（只读发布页，没有理由禁掉） | **置灰** + 手动取件指引（可手动下 `aha-desktop-<版本>-<平台>.zip`，或用 `Dist/` 的同族脚本） |

原则：**不让用户点了才发现不行**；置灰必须配「怎么办」的替代动作。

判定依据是「**本平台有没有换装脚本**」（`UpdateLauncher.unsupportedReason()`），不是把平台名写死：
POSIX 的换装脚本一落地，这条门自动就开了（现在返回「只在 Windows 上实现（POSIX 版本尚未实现，
issue #51 §7 P4）」）。**置灰必须发生在下载之前** —— v1.0.0 的实现让用户走完
「检查 → 下载 → 解包」才报「该包不完整」，那是把锅甩给包（2026-10-16 审查 P1-7 已修）。

**门有两层**（v1.2.2 起，第三轮审查补齐）：平台（上面那张表）+ **安装形态**
（`UpdateLauncher.unsupportedReason(layout)`）—— 安装根里没有启动脚本、也没有原生单文件
（只剩一个单体 jar）时同样拦下：那种形态的计划里 `restart` 是空的，换装会成功却**没有人把程序
拉回来**。两层门**界面与 CLI 都要过**：`--update-to --yes` 以前绕过它（换完不重启），
现在同样在下载前拒绝并返回非 0；`--check` 不受限。

> 验收口径见 §8.2 第 12 项（只剩单体 jar）与第 13 项（换装中再启动一次 App）。

## 6. 配置键（`~/.aha/desktop.properties`）

| 键 | 取值 | 默认 | 说明 |
|---|---|---|---|
| `Update.Channel` | `stable` / `beta` / `alpha` | `beta`（**暂定**） | 缺键取默认、**不主动写盘** ⇒ 将来改回 `stable` 会惠及「从没选过该键」的安装 |
| `Update.CheckOnStartup` | `true` / `false` | `false` | 启动只**检查**，不自动下载 |
| `Update.AlphaAcknowledged` | `true` / `false` | `false` | 首次切到 Alpha 的一次性确认 |
| `Update.BackupKeep` | 整数 | `2` | 保留备份份数（0–10）；**启动收尾时生效**（v1.1.0 起真的会被读，此前是死配置） |
| `Update.LastCheckAt` | ISO 时间 | — | 上次检查时间，**仅展示用，不参与决策** |

实现要点（`DesktopSettings`）：只有**用户显式设过**的 `Update.*` 键才落盘；值写坏了不算「设过」。
`ReleaseChannel.of` 对不认识的值返回默认通道而不是 `null`，所以读盘与命令行解析都必须**回头比一次 key**。

## 7. 安全模型

| 项 | 约定 |
|---|---|
| 白名单主机 | 只允许 `api.github.com`、`github.com`，以及资产 302 之后的 `objects.githubusercontent.com` |
| 资产地址 | 初始地址必须匹配本仓库发布页前缀 |
| 重定向 | **逐跳**校验主机（不满足即拒，不发出第二次请求） |
| 不执行包内脚本 | helper 只按计划落文件；包里的任何脚本都不执行 |
| 不接受任意 URL | 不支持用户自定义下载地址 |
| 不提权 | 不请求管理员 / `sudo`；写不进去就报错并说明 |
| 留痕 | 检查与换装写 `~/.aha/update/update.log`（版本、来源 tag、资产名、校验结果） |
| 无遥测 | 除 GitHub 发布页外不发任何请求 |

## 8. 验证记录

> 口径：**未验证的不得写成已验证**。本节分两块——Agent 侧（可机器判别、离线）与
> ACANX 侧（必须真人真机跑）。ACANX 侧的表格**留空待填**，填完这一节才算闭环。

### 8.1 Agent 侧（已完成，可复跑）

| 项 | 证据 |
|---|---|
| 版本模型与通道契约 | `AhaVersionTest` / `ChannelContractTest`（`./mvnw -pl aha-core test`） |
| 发布页解析、限流识别、不重试 | `GitHubReleaseClientTest`（本机 mock 服务器；fixture 为真实响应投影） |
| 挑资产与 #210 反例（缺本平台腿必须报错、不得退版） | `AssetSelectorTest` |
| 下载 / 校验 / 解包 / 清单核对 | `PackageDownloaderTest`、`ChecksumsTest`、`PackageUnpackerTest`、`PackageManifestTest` |
| 只动清单内文件、备份可还原 | `SwapPlanTest`（关键两条：手写文件不被删、回滚回到换装前） |
| 计划与暂存 | `UpdatePlanTest`、`StagingAreaTest`、`PathSafety` 相关用例 |
| 端到端（离线） | `UpdateServiceTest`：发现 → 挑资产 → 下载 → 校验 → 解包 → 清单核对；校验和不符**不落地** |
| 命令行入口解析 | `UpdateCommandTest`（含「`--channel` 拼错必须报错」） |
| 界面契约（控件 id / 两个 tab） | `AhaDesktopSmokeTest#settingsDialogHasBothTabsAndTheUpgradeControls`（`-Daha.ui.tests=true`） |
| 配置键与「缺键不写盘」 | `DesktopSettingsTest` |
| 出包契约（清单 / 校验和 / 三处一致） | `.github/Python/PackageManifest.py --self-test`（正 5 反 11）、`check_release_channel_contract()` |
| **换装 helper 真的在包里、也在仓库里** | `check_update_helper_packaging()`：两条发行线的装配描述符都要收 helper，且 helper 必须存在**且被 git 跟踪** —— v1.0.0 时 `Dist/apply-update.cmd` 被 `.gitignore` 吃掉，包里没有它，功能全废而门禁全绿 |
| **helper 逻辑真跑过**（不只是「写没写」） | `Script/PowerShell/HelperSelfTest.ps1`（在 `Build.yml` 的 `native-update-script` 作业里跑）：临时目录里跑失败路径（缺清单 ⇒ 拒绝换装 + 写回执 + 写「已启动」标记 + 退出码 1）与成功路径（替换 + 删除 + 自证 + 回执 + 备份可还原） |
| **调用仓库脚本的作业必须先 checkout** | `check_repo_script_steps_have_checkout()`（CI 实测：`native-publish` 没有 checkout 却调 `PackageManifest.py`，报 `No such file or directory`） |
| **启动收尾与握手** | `UpdateServiceTest`：回执消费 + 暂存/备份清理 + `helperNeverRan` 判定 + 标记超时/出现 + 真拉起假 helper 的握手 |
| **「被忽略却被引用」的文件** | `check_ignored_sources()` 扩展：`Dist/`、`Script/`、`bin/` 下本该手写的文件被 `.gitignore` 忽略、却被装配描述符或工作流按名字引用 ⇒ 直接红 |
| 启动收尾在界面上真的接线 | `AhaDesktopSmokeTest#startupShowsTheLastSwapResultAndCleansStaging` |
| 能力门在界面上提前生效 | `AhaDesktopSmokeTest#upgradeTabGatesInAppUpdateWhereNoHelperExists` |
| 兜底拉起命令是绝对路径 | `InstallLayoutTest#restartFallsBackToAnAbsoluteJavaPath` |
| 拒绝协议降级 | `PackageDownloaderTest#refusesToDowngradeFromHttpsToPlainHttp` |

### 8.2 ACANX 侧（**待真人真机验**，逐条填结论）

| # | 项 | 怎么做 | 期望 | 结论 |
|---|---|---|---|---|
| 1 | Windows 上真跑一次升级 | 装一个旧版（如 `V0.1.3.00241` 的包），设置 → 升级 → 选新版本 → 升级 | 程序自己退出、几十秒后重新起来，`关于`/启动横幅里的版本号变了 | ⏳ |
| 2 | 失败注入①：校验和不符 | 把 `~/.aha/update/<版本>/pkg.zip` 手动改几个字节（或在网络中间截断），再点升级 | 报「校验和不符，已拒绝落地」，**正式文件不出现**，程序版本不变 | ⏳ |
| 3 | 失败注入②：替换中途失败 | 让 helper 在替换时失败（例如把 `~/.aha/update/` 设成只读，或临时改计划里的动作路径） | 自动回滚，**旧版本仍能起来**，`update-result.json` 里 `ok:false` 且 `rolledBack:true` | ⏳ |
| 4 | 运行中改名 / 覆盖的真实行为 | 在 App 运行时手工对 `aha-desktop.jar` / 原生 exe 做改名与覆盖 | 记录真实结果（决定 helper 是否必须等进程退出） | ⏳ |
| 5 | helper 从 `~/.aha/update/` 自删是否可靠 | 升级完看目录 | **两个 helper 文件都消失**（`.ps1` 自删、`.cmd` 由下次启动的收尾删）；`helper-started` 标记也不该在；若留下，记录现象 | ⏳ |
| 6 | 重启后用户看到什么 | 升级成功与失败各看一次 | 成功静默（只写日志）；失败弹窗并指向 `update.log` | ⏳ |
| 7 | 用户数据未被触碰 | 升级前后比对 `~/.aha/` 下非 `update/` 目录的 mtime | 无变化 | ⏳ |
| 8 | 备份与回滚可用 | 升级后看 `~/.aha/update/backup/<旧版本>/`，再按 §4.1 手工回滚一次 | 备份含旧文件（含被新包删掉的文件），回滚后旧版本能起来 | ⏳ |
| 9 | Windows 原生形态自更新 | 用 `AHA-Desktop-Native-…` 包走同一条路径 | 替换后能起来、版本正确、备份可回滚（v1.1.0 起原生包也带 helper，之前这条路**注定失败**） | ⏳ |
| 10 | helper 起不来时不许「静默退出」 | 把安装根里的 `AhaApplyUpdate.ps1` 换成「什么都不做」的空脚本，再点升级 | App 等 20 秒后**留在前台**报「换装程序没有启动」，安装目录未改动，`update.log` 有留痕 | ⏳ |
| 11 | 启动时清理真的生效 | 升级（或制造一次失败换装）后正常关闭再启动 | `~/.aha/update/<版本>/` 消失（不再白占 50–110 MB）；`backup/` 只留 `Update.BackupKeep` 份 | ⏳ |
| 12 | 安装形态门（只剩单体 jar） | 直接 `java -jar aha-desktop.jar` 起，点「设置 → 升级」；再试一次 CLI `--update-to <版本> --yes` | 界面按钮**提前置灰**并说明「没有可用的启动脚本」；CLI **不下载**、明确拒绝、返回非 0 | ⏳ |
| 13 | 换装中再启动一次 App 不许毁掉升级 | 点升级、App 退出后**立刻**再启动一次（趁 helper 还在换装） | 消息流出现「上次换装可能还在进行…」，`~/.aha/update/<新版本>/` 与 `backup/` **都没被删**；换装结束后下次启动给出回执 | ⏳ |

采集方式（把结论与原始输出贴进 PR 评论）：

```bash
# 日志（含检查 / 下载 / 解包 / 计划 / 回落结论）
tail -50 ~/.aha/update/update.log
# 回执（读完即消费：再启动一次就没了，先复制一份）
cat ~/.aha/update/update-result.json
# 备份内容
find ~/.aha/update/backup -maxdepth 3 -type f | head -20
```

## 9. 与其它文档的关系

- 通道 ↔ tag ↔ 资产名：**只有** [ReleaseProcess.md](../DevSpec/ReleaseProcess.md) §3.5 定义；
- 出包线怎么写清单与校验和：**只有** [BuildSpec.md](../DevSpec/BuildSpec.md) §7.1 说明流程；
- 工作流命名 / 权限 / 触发：**只有** [GitHubActionsSpec.md](../DevSpec/GitHubActionsSpec.md) 定义；
- 手工取件脚本（另一条路）：[DesktopNativeUpdateDesign.md](DesktopNativeUpdateDesign.md)；
- 用户怎么用：[AppUpdateGuide.md](../Guide/AppUpdateGuide.md)。
