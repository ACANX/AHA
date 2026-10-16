# TD-00019 默认升级通道改回 Stable（种子发版落地后）

> 待办编号：TD-00019
> 标题：首个带资产的正式发版落地后，把应用内升级的默认通道从 `Beta` 改回 `Stable`
> 状态：⏳ 待做（**触发条件明确**：release 页出现带资产的 `V<x.y.z>`）
> 跟踪 Issue 状态：**OPEN**
> 跟踪 Issue：[#51](https://github.com/ACANX/AHA/issues/51)
> 创建日期：2026-10-16
> 最后更新：2026-10-16

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

## 顺带验收

这次种子发版**同时是 #238（TD-00018）的验收项**：要看的正是「tag 自动创建 → 自动派发 → release 页
自动出现 CLI 包与三平台桌面包 + `AHA-PACKAGE.json` + `SHA256SUMS`」。两边一次跑完，别分两次。
