# TD-00019 默认升级通道改回 Stable（种子发版落地后）

> 待办编号：TD-00019
> 标题：首个带资产的正式发版落地后，把应用内升级的默认通道从 `Beta` 改回 `Stable`
> 状态：🚧 已成文（2026-10-16，PR [#255](https://github.com/ACANX/AHA/pull/255)）—— **触发条件尚未达成**，合并时机与种子发版对齐（见下「本轮落实」）
> 跟踪 Issue 状态：**OPEN**
> 跟踪 Issue：[#252](https://github.com/ACANX/AHA/issues/252)
> 创建日期：2026-10-16
> 最后更新：2026-10-16（原载体 [#51](https://github.com/ACANX/AHA/issues/51) 已随 PR #243 交付后关闭；本单转由 [#252](https://github.com/ACANX/AHA/issues/252) 跟踪）

---

## 来源

- issue #51 §10 **D9**（2026-10-16 拍板）：默认通道**暂定 `Beta`**，因为 Stable 通道当前**没有可下包**；
- issue #51 §8 第 2 条（前置条件）：Stable 通道要先有一次**带资产的正式发版**（俗称「种子发版」）。

## 为什么会有这条待办

`ReleaseChannel.DEFAULT = BETA` 是一个**被现实逼出来的临时值**，不是产品判断：

- 正式 release `0.1.0` / `V0.1.1` / `V0.1.2` **资产均为 0**（实查 2026-10-16）⇒ Stable 通道点「检查更新」
  只会得到「本通道暂无可列出的版本」；
- 若默认取 Stable，用户第一次打开升级面板看到的就是空列表，会被理解成「坏了」。

配套机制（已实现，见 `DesktopSettings` 的 `explicitKeys`）：

- **缺键取默认、不主动写盘**：`Update.Channel` 只有**用户显式选过**才会写进 `desktop.properties`；
- 因此把代码默认值改回 `Stable` 时，**从没改过该键的安装会跟着切换**，用户显式选过的不受影响。

## 触发条件（可机器判别）

```bash
# 出现任意一条 prerelease=false 且 assets 非空的 release，即条件达成
gh api "repos/ACANX/AHA/releases?per_page=100" \
  --jq '.[] | select(.prerelease==false and (.assets|length>0)) | "\(.tag_name) assets=\(.assets|length)"'
```

## 要做的事

1. 把 `aha-core` 的 `ReleaseChannel.DEFAULT` 从 `BETA` 改为 `STABLE`；
2. 同步 `DesktopSettings.DEFAULT_UPDATE_CHANNEL`（它引用 `ReleaseChannel.DEFAULT`，通常只是确认）；
3. 单测：`DesktopSettingsTest.defaultsMatchTheDecisions` 与 `UpdateServiceTest` 相关断言按新默认值更新；
4. 文档：issue #51 §10 D9 标注「已切换」；`Docs/Guide/` 里若有「默认通道」的表述一并同步
   （`CheckDocs` 会拦引用不一致）；
5. `CHANGELOG.md` 的 `[Unreleased]` 记一条（用户可见的行为变化）。

## 本轮落实（2026-10-16）

改动已成文（PR [#255](https://github.com/ACANX/AHA/pull/255)），但**触发条件当时并未达成**，如实记录：

```bash
$ gh api "repos/ACANX/AHA/releases?per_page=100" --paginate \
    --jq '.[] | select(.prerelease==false) | "\(.tag_name) assets=\(.assets|length)"'
V0.1.2  assets=0
V0.1.1  assets=0
0.1.0   assets=0
```

即：**没有任何 `prerelease=false` 且 assets 非空的 release**（最近的 `V0.1.3.002xx-*` 全是
`prerelease=true` 的快照/预发行）。因此本单的验收第 3 条「该通道此时确实能列出版本」**尚未成立** ——
若在种子发版之前合并默认值改动，升级面板首屏会是空列表。

据此本轮做了两件事：

1. **按单子实现默认值切换**（`ReleaseChannel.DEFAULT = STABLE`、文档、单测），并在 PR 正文里标明
   「合并时机建议与种子发版对齐」；
2. **把「空列表」这条路改成可行动**，让提前合并也不会误导：
   - 升级面板空列表追加「也可以切到「…」或「…」通道再看看」；
   - `AssetSelector` 的「本通道目前没有 <形态> 的包」不再只写「见 issue #51 §2.1」，而是按
     `ChannelContract.supports` **动态列出**可用通道 —— 默认值落到 Stable 之后，**原生形态**装机
     首屏就会碰到这条（形态边界见 `ReleaseProcess.md` §3.5 口径 6）。

改动清单：

| # | 文件 | 改动 |
|---|---|---|
| 1 | `ReleaseChannel` | `DEFAULT` → `STABLE`；类与字段注释补「为什么曾暂定 Beta」「靠缺键不写盘惠及老安装」「形态边界」 |
| 2 | `DesktopSettings` | 默认值注释与 `explicitKeys` 注释同步（引用 `ReleaseChannel.DEFAULT`，值不用改） |
| 3 | `UpgradePane` | 启动状态行改说「当前通道 X（改过就按改过的，没改过跟随默认值 Y）」；空列表补可切换通道 |
| 4 | `AssetSelector` | 不支持的组合改为动态列出可用通道 |
| 5 | 单测 | `ChannelContractTest`（默认值 = STABLE、默认通道仍不支持原生）+ 不支持组合消息含可用通道、`DesktopSettingsTest`（+2 条 #252 验收：老安装跟随默认、显式选过 Beta 不受影响） |

验证：本地 `javac` 预检（core + desktop）通过；本地真 JUnit `com.acanx.module.aha.core.update.*`
**160/160**、`DesktopSettingsTest` **9/9**；`bin/Check{Docs,Scripts,Skills,Theme}.py` 全绿。

## 顺带验收

这次种子发版**同时是 #238（TD-00018）的验收项**：要看的正是「tag 自动创建 → 自动派发 → release 页
自动出现 CLI 包与三平台桌面包 + `AHA-PACKAGE.json` + `SHA256SUMS`」。两边一次跑完，别分两次。
