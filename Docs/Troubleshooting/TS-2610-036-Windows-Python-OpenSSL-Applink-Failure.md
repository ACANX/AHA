# TS-2610-036 · Windows 上 Python 的 HTTPS 硬失败：`no OPENSSL_Applink`

> 事件日期：2026-10-13
> 作者：@cnxnc
> 关联 Issue：[#156](https://github.com/ACANX/AHA/issues/156)（一键更新入口；本档案是它在真机上暴露的环境问题）
> 关联记录：`DesktopNativeUpdateDesign.md` §5 / §10、`TD-00015`
> 同族参考：`TS-2610-010`（原生模块静默陷阱）、`TS-2610-011`（Maven 重试无效）——均为「环境层的硬失败」

## 1. 现象

ACANX 在 Windows 上双击 `Dist\UpdateDesktopNative.cmd`，输出：

```text
Using: py -3 Script\Python\DesktopNativeVersionUpdate.py

OPENSSL_Uplink(00007FFC93DDCB00,08): no OPENSSL_Applink

[FAILED] exit code 1 - see the output above for the reason
         (network unreachable, GitHub rate limit, or a running program
         holding the executable file).
```

三个关键点：

1. **没有 Python traceback**——连 `Traceback (most recent call last)` 都没有；
2. 报错在**第一次网络访问**就打出来（脚本还没打印「目标版本：…」）；
3. 同一台机器的 WSL 侧（Ubuntu 26.04 + Python 3.14.4）跑同一个脚本**一路正常**。

## 2. 排查过程

| # | 动作 | 结论 |
|---|---|---|
| 1 | 通读引擎网络层（`http_get_text` / `http_open` / `_fetch_urllib`） | 全程是最普通的 `urllib.request.urlopen`，**没有**自定义 SSL context、**没有**加载 CA 文件、没碰 `ssl` 私有接口 ⇒ 不是「脚本用错了 API」 |
| 2 | 看报错形态：没有 traceback、进程直接退出 | Python 层异常会打 traceback；这里没有 ⇒ 失败发生在 **DLL / C 运行时层**，异常来不及抛 |
| 3 | 认报错来源 | `OPENSSL_Uplink(...)` / `no OPENSSL_Applink` 是 OpenSSL 在 **Windows** 上的 applink 机制：某个 DLL 里的 OpenSSL 被调用（如 `BIO_new_file` 一类走 `FILE*` 的接口），但进程没有 `OPENSSL_Applink` 表 ⇒ OpenSSL 打印该行并终止进程 |
| 4 | 在 WSL 侧对照 | 同一份代码、同一个版本、同一条链路完全正常 ⇒ 与脚本逻辑无关，是 Windows 侧 Python 的 OpenSSL 运行时问题 |

## 3. 根因

**本机 Windows 上 `py -3` 那个 Python 的 OpenSSL 运行时不可用**，任何 HTTPS 都会以
`no OPENSSL_Applink` 收场；而这条路是**硬失败**（进程被直接终结）⇒ 脚本内无论怎么
`try/except` 都救不回来。

**候选成因**（未在本档案内定论，见「未决问题」）：PATH 上存在其它软件自带的
`libcrypto-3-x64.dll` / `libssl-3-x64.dll`（Git for Windows、conda、另一个 Python 都会带），
被优先加载；或该 Python 的 OpenSSL 打包本身有问题。

## 4. 修复与对策

**工具侧（本轮已做）**：把「取网络数据」做成两条路，并让 Windows 上默认自动兜底：

| 下载器 | 走谁 | 说明 |
|---|---|---|
| `urllib` | Python 自带的 TLS | 默认；WSL / CI / 多数机器上的既有路径 |
| `curl` | 系统 TLS（Windows 10 1803+ 自带 `curl.exe`） | 不经过 Python 的 OpenSSL ⇒ 免疫本问题 |

- `--downloader auto`（默认）：**只在 Windows 上**先用**子进程**探一次 HTTPS；
  探测失败且有 curl ⇒ 打印一行提示并改用 curl；其他平台 `auto` 即 `urllib`（applink 是
  Windows 专有机制，不值得为它每次多付一次探测）；
- **探测必须在子进程里**：这正是本档案最有价值的一条教训——失败会终结进程，
  进程内 `try/except` 抓不到，只有「另起一个进程看退出码」才看得见；
- curl 路径同样支持续传（`-C -`），服务器不认 Range 时自动退回完整下载。

**环境侧（ACANX 可任选）**：换一个能用的 Python（如 `python` 而不是 `py -3`）；
把 PATH 上其它软件的 OpenSSL DLL 目录移开；或重装该 Python。

## 5. 验证

- **curl 路径端到端**（本机 WSL，`--dist /tmp/...`）：下载 41.2 MiB ⇒ 安装 92.6 MiB ⇒
  `version` 输出 `AHA 0.1.3.00155`；
- **断点续传**：把已下包截断成 20 MB 并改名为 `.part`、删掉已装可执行文件后重跑 ⇒
  `-C -` 续传补齐，`size` 与 `sha256` 校验均通过；
- **选择逻辑**（模拟 Windows）：HTTPS 坏 + 有 curl ⇒ 选 `curl`；HTTPS 正常 ⇒ 选 `urllib`；
  `os.name != "nt"` ⇒ 不探测、直接用 `urllib`；
- **未验证**：ACANX 的 Windows 真机上「双击 ⇒ 自动改用 curl ⇒ 下载成功」这一条走通与否
  （需其复测，见未决问题 Q1）。

## 6. 涉及文件

| 类型 | 路径 |
|---|---|
| 代码 | `Script/Python/NativeVersionUpdate.py`（`--downloader`、`select_downloader()`、`_urllib_https_broken()`、`_fetch_curl()`、`_curl_text()`、`_curl_head_ok()`） |
| 文档 | `Docs/Design/DesktopNativeUpdateDesign.md` §5 / §8 / §10、`Docs/Guide/CommandCheatsheet.md` §4、本档案、索引、`CHANGELOG.md`、`Docs/DevLog/20261013-21.md` |

---

## 结论分层

### 已观测事实

- Windows 侧（`py -3`）双击一键入口：输出 `OPENSSL_Uplink(00007FFC93DDCB00,08): no OPENSSL_Applink`
  后以退出码 1 结束，**无 Python traceback**，且发生在第一次网络访问之前（脚本还没打印计划）；
- WSL 侧（Ubuntu 26.04 + Python 3.14.4）同一份脚本、同一个版本：`--list` / 下载 / 安装 / 断点续传全部正常；
- 引擎网络层只用 `urllib.request.urlopen` + 标准头，无自定义 TLS 配置（已逐行核对）。

### 机制 / 行为模型

- `no OPENSSL_Applink` 属 **Windows 专有**的 OpenSSL applink 机制问题（applink.c 只在 Windows 上生效）；
- 它是**硬失败**：OpenSSL 打印一行后终止进程 ⇒ 进程内的异常处理天然无用
  ⇒ 可靠的检测手段只有「另起一个子进程、看退出码」（本轮的实现即如此）；
- 因此对策必然是**换执行路径**（不放 Python 的 TLS 里做），而不是「加重试 / 加 try」。

### 未定位 / 未复现

- **未复现**：本机没有 Windows 环境，无法就地复现该失败，也无法确认到底是哪一个
  `libcrypto-3-x64.dll` / `libssl-3-x64.dll` 被加载；
- **未定位**：是「PATH 上的同名 DLL 抢先」还是「该 Python 打包本身有问题」——需要 Windows 侧
  `py -3 -c "import ssl;print(ssl.OPENSSL_VERSION)"`、`where libcrypto-3-x64.dll`、
  以及 `py -3 -V` 的实际输出才能定论；
- 未验证非 Windows 平台是否存在同类硬失败（理论上不涉及 applink，故未处理）。

## 观测数据

| 数据 | 来源 | 原始输出 / 取值 |
| --- | --- | --- |
| 真机失败输出 | ACANX 在 Windows 双击 `Dist\UpdateDesktopNative.cmd` | `OPENSSL_Uplink(00007FFC93DDCB00,08): no OPENSSL_Applink`，退出码 1，无 traceback |
| 引擎网络层 | `Script/Python/NativeVersionUpdate.py` | 仅 `urllib.request.urlopen`；无 `ssl.` 接口调用 |
| curl 完整下载 | `--downloader curl --dist /tmp/...`（WSL） | 41.2 MiB ⇒ 安装 92.6 MiB；`version` → `AHA 0.1.3.00155` |
| curl 断点续传 | 截断为 20 MB 的 `.part` + 删除已装 exe 后重跑 | 续传补齐；`size` 与 `sha256` 校验通过 |
| 选择逻辑 | 模拟 `os.name='nt'` + 探测结果 | 坏 + 有 curl ⇒ `curl`；正常 ⇒ `urllib`；POSIX ⇒ 不探测 |

## 排除项

| 已排除 | 依据 |
| --- | --- |
| 「脚本的 TLS 用法有问题」 | 逐行核对：只有 `urllib.request.urlopen`，没有自定义 context / CA 文件 / `ssl` 私有接口 |
| 「网络不通 / 限流」 | WSL 侧同一版本同一链路正常；且报错形态是 DLL 级终止，不是 `URLError` |
| 「GitHub 端问题」 | 失败发生在首次请求前，WSL 侧对照成功 |
| 「占用/权限（脚本提示里的第三种原因）」 | 报错与该原因无关；该提示文案本轮已按实际情形重写 |

## 未决问题

**Q1 真机兜底是否生效**

现状：工具侧已能自动改走系统 `curl.exe`，但**未在 Windows 真机复测**。
阻塞点：需 ACANX 再双击一次 `UpdateDesktopNative.cmd`（预期输出里会有一行
「本机 Python 的 HTTPS 不可用…本轮改用系统 curl 下载」）。若仍失败，请把完整输出贴回。

**Q2 该 Python 到底哪里坏了**

现状：只知「HTTPS 不可用」，不知是 DLL 抢占还是打包问题。
阻塞点：需 Windows 侧执行 `py -3 -V`、`py -3 -c "import ssl;print(ssl.OPENSSL_VERSION)"`、
`where libcrypto-3-x64.dll` / `where libssl-3-x64.dll` 的输出。

**Q3 是否值得把 curl 兜底推广到 POSIX**

现状：`auto` 只在 Windows 探测（applink 是 Windows 专有）。
阻塞点：若将来出现非 Windows 的同类硬失败，再把探测条件放宽；目前按「不为假想问题付成本」处理。
