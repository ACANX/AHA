# TS-2610-039 · 快照载体为何不用 Actions 制品（匿名下载 401 / 网页链接 307→404）

**日期**：2026-10-15
**关联 Issue**：issue [#165](https://github.com/ACANX/AHA/issues/165)（§七「载体与鉴权」路线取舍）
**关联档案**：`TS-2610-004`（**不是**同一回事：那条是「中央仓库找不到制品」）
**关联设计**：`Docs/DevSpec/BuildSpec.md` §11、`Docs/DevSpec/ReleaseProcess.md` §2.1

---

## 1. 现象

给**未合并**的 PR 出「试包」时，最初方案（路线 B）的载体是 **Actions 制品（artifact）**，
假设「运行页上人人可点、脚本可以匿名下载」。真机验证取件路径时发现**两条路都拿不到**：

- **脚本取件（匿名）**：制品下载接口返回 `401 Requires authentication`；
- **网页链接**：`…/actions/runs/<run>/artifacts/<id>` 返回 **307**，跟随后落到
  `…/suites/<suite>/artifacts/<id>`，那里是 **404**。

原始输出（逐字照抄，2026-10-15 本机实测）：

```text
$ curl -sS https://api.github.com/repos/ACANX/AHA/actions/artifacts/11915470096/zip
{
  "message": "Requires authentication",
  "documentation_url": "https://docs.github.com/rest",
  "status": "401"
}

$ curl -sS -w 'HTTP %{http_code}\n' https://github.com/ACANX/AHA/actions/runs/38602590454/artifacts/11915470096
HTTP 307
$ curl -sS -L -o /dev/null -w 'HTTP %{http_code}  %{url_effective}\n' https://github.com/ACANX/AHA/actions/runs/38602590454/artifacts/11915470096
HTTP 404  https://github.com/ACANX/AHA/suites/104605104794/artifacts/11915470096
```

**影响面**：取件脚本的**全部目标用户**。本仓库「零配置取件」的前提是取件端**没有** token / `gh` / Python
（见 `Dist/UpdateSnapshot.ps1` 的设计），而制品把「有凭据」变成了硬前提 ⇒ **路线 B 直接出局**。

## 2. 排查过程

| # | 动作 | 预期 | 实际 | 结论 |
|---|---|---|---|---|
| 1 | 匿名 `curl` 制品 zip 端点 | 200 或 302 | **401** `Requires authentication` | 制品下载**要求认证** |
| 2 | 同一端点带 `Authorization: Bearer <token>` | 200 | **303**（然后才是有效期很短的签名 URL）| 凭据是唯一变量 ⇒ 不是 URL 写错 |
| 3 | 匿名访问运行页里的制品链接 | 200 | **307 → `…/suites/…/artifacts/…` ⇒ 404** | 网页侧对匿名用户同样不可用 |
| 4 | 匿名下载**发布页资产**（对照） | 200 | **200**（1 次重定向）| 资产可匿名下载 ⇒ 可作载体 |
| 5 | 匿名 + `Range: bytes=0-1023` 取同一资产 | 206 | **206 / 1024 字节** | 支持断点续传，脚本可做校验 |

被排除的假设与依据：

| 已排除的可能 | 依据 |
|---|---|
| 「是本机网络 / 代理问题」 | 同一网络下发布页资产 200、GitHub 文档页面 200 |
| 「是仓库私有才要认证」 | 本仓库是**公开**仓库：匿名 `git clone`、匿名 release 资产下载均正常 |
| 「是 URL 粘错了」 | 第 1、2 步用**同一个** URL，仅凭据不同 ⇒ 结果不同 |
| 「是瞬时故障 / 限流」 | 401 / 307→404 稳定复现；且 401 的报文体是 `Requires authentication`，不是限流文案 |

## 3. 根因

**一句话**：Actions 制品是**面向 workflow 之间流转**的中间产物，其下载接口**始终要求认证**；
而「零配置取件」要求的是**面向人 / 脚本的匿名下载**能力 —— 两者目标不同，不能互换。

- **触发条件**：取件端无凭据（这正是我们要支持的场景）；
- **机理**：制品下载接口要求带 token（且需读权限）才返回签名 URL；网页侧的制品入口同样只服务于已登录用户
  （匿名走不下去，落点是 404）。发布页资产则不同：它是**发布**语义，公开仓库的资产默认允许匿名下载；
- **为什么没被及早发现**：制品在 CI 内部流转时始终身处「带 token」的环境，**没有人用匿名身份走过这条路**；
  方案评审时把「制品」与「资产」当成了同一类「可下载文件」。

## 4. 修复（决策与落地）

**改用路线 A：发布页 pre-release 资产 + 快照生命周期**（ACANX 定案，2026-10-14）：

```
载体   ：每个目标一条 pre-release（prerelease: true，不进 releases/latest）
tag    ：V<基线>.<PR 补零 5 位>.<北京时间戳>-AHA-<目标>-SNAPSHOT
取件   ：Dist\UpdateDesktopNativeSnapshot.cmd / Dist\UpdateCliNativeSnapshot.cmd / Dist\UpdateSnapshot.ps1 -Kind jvm
         （零配置：不需要 token / gh / Python；解压到独立目录；原生桌面端替换 exe，旧版留 .bak）
生命周期：同一 PR 同目标保留最近 3 条、有效期 7 天；PR 关闭 / 合并后由 SnapshotCleanup.yml 异步回收
```

**被否的替代方案**：① 给取件脚本发 token —— 等于把「零配置」作废，且要处理密钥分发；② 把制品改成
「公开仓库默认匿名可下」—— 上游行为，不由本仓库决定。

## 5. 验证

- 匿名下载发布页资产：`HTTP 200`（第 4、5 步原始输出见 §2）；
- 端到端取件（真机）：`Dist/UpdateSnapshot.ps1` 取快照 ⇒ 56 MB 下载 + **sha256 校验通过** + 解压 7 个文件，
  原生桌面端目标随后把 exe 替换到启动器同目录（旧版留 `.bak`）；
- 清理链路：定时 run [38645500766](https://github.com/ACANX/AHA/actions/runs/38645500766) 真机回收孤儿快照
  （`已删除 V0.1.3.00188.10142007-AHA-Desktop-Native-SNAPSHOT ← PR #188 已合并`）；
- 回归：三条守卫在册（前两条在 `bin/CheckScripts.py`、第三条在 `bin/CheckDocs.py`）：`check_snapshot_manifest()` /
  `check_snapshot_fetch_parity()` / `check_cheatsheet_copy()`，反向验证均如期失败并点名。

## 6. 教训与沉淀

- **最该记住的一条**：**载体选型要按「最终用户的身份」实测一遍下载路径**——「CI 里能下」不等于「用户能下」；
  匿名、无 token、无外部工具，这三条必须在选型时就当成验收条件跑一遍。
- 已落到规范：`Docs/DevSpec/BuildSpec.md` §11（载体与命名硬约定、单一来源、生命周期）、
  `Docs/DevSpec/ReleaseProcess.md` §2.1（与正式发布的隔离）、`Docs/Guide/CommandCheatsheet.md` §7（取件命令）；
- 遗留事项：**无**（载体已定案并落地；若将来要支持制品，须先解决匿名取件的凭据问题）。

## 7. 涉及文件

| 类型 | 路径 |
|---|---|
| 文档 | `Docs/DevSpec/BuildSpec.md` §11、`Docs/DevSpec/ReleaseProcess.md` §2.1、`Docs/Guide/BuildGuide.md` §3.3、`Docs/Guide/CommandCheatsheet.md` §7、本篇 |
| 工作流 / 脚本 | `.github/Python/SnapshotPlan.py`、`.github/Python/SnapshotCleanup.py`、`Dist/UpdateSnapshot.ps1`、`Dist/UpdateDesktopNativeSnapshot.cmd`、`Dist/UpdateCliNativeSnapshot.cmd`|

---

## 结论分层

### 已观测事实

- 匿名 `curl` 制品下载端点 ⇒ `401 Requires authentication`（原始报文体见 §1）；
- 同一 URL 带 token ⇒ `303`；匿名访问运行页制品链接 ⇒ `307` ⇒ 落地 `…/suites/…/artifacts/<id>` ⇒ `404`；
- 匿名下载发布页资产 ⇒ `200`；带 `Range` ⇒ `206`（1024 字节）；
- 快照取件端到端成功（56 MB + sha256 通过）。

### 机制 / 行为模型

- 制品 = workflow 间流转的中间产物，下载需凭据（**本次实测**）；
- 发布页资产 = 发布语义，公开仓库默认匿名可下（**本次实测**）；
- 因此「零配置取件」只能建在**资产**上。

### 未定位 / 未复现

- 制品签名 URL 的**有效期**与制品**保留期**的具体数值：属上游文档口径，**本次未逐条核验**（不影响选型结论）；
- 早期记录里写作「网页链接 404」而未记「307 → 404」：**以本次实测为准**（原现场输出未保留）。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 制品匿名下载 | `curl -sS https://api.github.com/repos/ACANX/AHA/actions/artifacts/11915470096/zip` | `{"message":"Requires authentication", …, "status":"401"}` |
| 制品带 token | `curl -sS -I -H "Authorization: Bearer <token>" <同上>` | `HTTP 303` |
| 网页制品链接 | `curl -sS -L -o /dev/null -w '%{http_code} %{url_effective}' https://github.com/ACANX/AHA/actions/runs/38602590454/artifacts/11915470096` | `404 …/suites/104605104794/artifacts/11915470096` |
| 资产匿名下载 | `curl -sS -o /dev/null -w '%{http_code}' -I -L https://github.com/ACANX/AHA/releases/download/V0.1.3.00192-aha-desktop-native/AHA-Desktop-Native-0.1.3.00192-windows-amd64-jdk25.zip` | `200`（重定向 1 次） |
| 资产 + Range | 同上 + `-r 0-1023` | `206`，下载 1024 字节 |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 本机网络 / 代理 | 同网络下资产 200、文档页 200 |
| 私有仓库导致 | 公开仓库；匿名 clone 与资产下载正常 |
| URL 写错 | 同 URL 换凭据即 303（第 1 / 2 步） |
| 瞬时故障 / 限流 | 稳定复现，报文体是 `Requires authentication` |

## 未决问题

**Q1 制品签名 URL 的有效期与制品默认保留期分别是多少？**

现状：上游文档口径，本次未逐条核验数值。
阻塞点：不影响选型（本仓库不采用制品载体）；若将来要用，需要先实测有效期是否够取件脚本完成下载。

**Q2 取件脚本能否在「有 token」时也支持制品？**

现状：不做——与「零配置」目标冲突（见 §4 被否方案 ①）。
阻塞点：无。
