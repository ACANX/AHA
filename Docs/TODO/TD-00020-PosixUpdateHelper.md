# TD-00020 POSIX 换装 helper（Linux / macOS 的应用内升级）

> 待办编号：TD-00020
> 标题：实现 POSIX 的换装 helper（`apply-update.sh`），让 Linux / macOS 也能应用内升级
> 状态：⏳ 待做（**触发条件**：有人要在 Linux / macOS 上用应用内升级）
> 跟踪 Issue 状态：**OPEN**
> 跟踪 Issue：[#250](https://github.com/ACANX/AHA/issues/250)
> 创建日期：2026-10-16
> 最后更新：2026-10-16（原载体 [#51](https://github.com/ACANX/AHA/issues/51) 已随 PR #243 交付后关闭；本单转由 [#250](https://github.com/ACANX/AHA/issues/250) 跟踪）

---

## 来源

- issue #51 §7 **P4**（2026-10-16 计划）：POSIX 换装脚本**后置**；
- 2026-10-16 代码审查（PR #243）**P1-7**：现状是「界面能走到最后一步才报『该包不完整』」——
  已修为**提前**置灰 + 如实说明（`UpdateLauncher.unsupportedReason()`），但功能本身仍未实现。

## 现状（已实现的部分，别再重做）

| 层 | 状态 |
|---|---|
| 服务层（`aha-core/update`） | ✅ 与平台无关：发现 / 挑资产 / 下载 / 校验 / 解包 / 计划 / 回执 / 启动收尾全部可用 |
| `SwapPlan.apply` / `rollback` | ✅ 参考实现（`Files.copy` 语义），单测覆盖「只动清单内文件」「备份可还原」 |
| 拉起方式 | ✅ `UpdateLauncher.launchCommand(…, windows=false)` 已给出 `sh -c 'nohup … &'` 形式 |
| 能力门 | ✅ `UpdateLauncher.unsupportedReason()` 在非 Windows 返回原因；界面下载前置灰 |
| 「已启动」握手 | ✅ Java 侧已实现并等待；**只差 POSIX 脚本去写这个标记** |
| POSIX 脚本本体 | ❌ **不存在**（`UpdateLauncher.POSIX_ENTRY = "apply-update.sh"` 目前是个占位常量） |

## 要做的事

1. 新增 `Dist/apply-update.sh`（LF、`#!/bin/sh`、可执行位；纯 POSIX，不依赖 bash 扩展）：
   - 唯一入参 = 计划文件路径；**不解析任何用户输入**；
   - **第一步**写 `helper-started` 标记（名字必须与 `UpdatePaths.HELPER_STARTED` 一致，
     `bin/CheckScripts.py::check_update_helper_packaging()` 会钉住）；
   - 等 App 退出（`kill -0 <pid>` 轮询，超时 60s）；
   - 按计划**备份 → 替换 → 逐文件 sha256 自证 → 重新拉起 → 写回执**；失败用备份回滚并**仍然拉起旧版本**；
   - 回滚自身也要包错误处理（参考 `Dist/AhaApplyUpdate.ps1` 的做法：主流程与自证分支各兜一次）；
   - 不执行包内任何脚本。
2. `dist-desktop.xml` / `dist-native.xml` 增加该文件（**必须被 git 跟踪**——`Dist/*` 的忽略规则要一并放行）；
3. `UpdateLauncher.unsupportedReason()` 在检测到 POSIX 脚本存在时返回 `null`（能力门自动打开）；
4. 测试：`Script/PowerShell/HelperSelfTest.ps1` 已有 Windows 版行为测试；
   POSIX 侧补一个等价的 shell 自测（失败路径 + 成功路径），接入 `Build.yml`；
5. 文档：`DesktopAutoUpdateDesign.md` §1 边界 / §5.4 / §8.2，`ReleaseProcess.md` §3.6，`CHANGELOG.md`；
6. 真机验证：Linux（Xvfb 或真桌面）与 macOS 各跑一次真实升级 + 一次失败注入（参见 §8.2 的口径）。

## 为什么值得做（而不是永远置灰）

- AHA 的发行形态是「便携包 + 原生单文件」，Linux / macOS 用户同样要手动解压覆盖；
- 服务层、计划、回执、握手、启动收尾都已平台无关，**增量只在一个 shell 脚本**；
- 反向风险：脚本动的是用户文件，必须在 CI 里有真实行为测试（照 `HelperSelfTest.ps1` 的路子），
  否则等于把「只有真机才能发现的错误」留到用户那里。
