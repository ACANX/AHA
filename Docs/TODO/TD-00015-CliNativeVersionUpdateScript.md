# TD-00015 CliNativeVersionUpdateScript

> 待办编号：TD-00015
> 标题：补 CLI 原生镜像的自动取件脚本（`CliNativeVersionUpdate.py`）
> 状态：☐ 未开始
> 跟踪 Issue：暂未开（如需长期跟踪再补）
> 创建日期：2026-10-09
> 最后更新：2026-10-09

---

## 来源

补充 `Docs/Guide/CommandCheatsheet.md` §4.2（PR #88）时发现：**桌面端原生镜像有自动取件脚本，
CLI 原生镜像没有**，速查里只能如实写「需手动下载解压」，用户拿包要多走手工步骤。

## 现状

| 项 | 桌面端原生 | CLI 原生 |
|---|---|---|
| CI 产出 | `DesktopNative.yml` → `AHA-Desktop-Native-<版本>-<系统>-<架构>-jdk<JDK>.zip` | `CliNative.yml` → `AHA-Cli-Native-<版本>-<系统>-<架构>-jdk<JDK>.zip` |
| 可执行文件 | `aha-desktop-native(.exe)` | `aha-cli-native(.exe)` |
| 自动取件脚本 | ✅ `Script/Python/DesktopNativeVersionUpdate.py` | ❌ **无** |
| 速查里的取件方式 | 脚本 + 手动 | 仅手动 |

`DesktopNativeVersionUpdate.py` 已具备的能力（可直接做蓝本）：

- 取最新 `V*-aha-desktop-native` 预发行 tag 并解析版本（形如 `V0.1.1.00040-aha-desktop-native`）；
- 按本机平台选 zip、下载并解压；
- 覆盖 `Dist/aha-desktop-native(.exe)`，旧版留一份 `.bak`；
- 参数：`--list` / `--dry-run` / `--tag` / `--platform` / `--jdk`；
- 状态文件记录，避免重复下载。

## 需求

新增 `Script/Python/CliNativeVersionUpdate.py`，与桌面端脚本**同构**：

1. 默认取最新 `V*-aha-cli-native` 预发行版（`--tag` 可指定）；
2. 按平台选 `AHA-Cli-Native-*.zip`，解压出 `aha-cli-native(.exe)`；
3. 覆盖 `Dist/aha-cli-native(.exe)`，旧版留 `.bak`；
4. 支持 `--list` / `--dry-run` / `--tag` / `--platform` / `--jdk`；
5. **尽量与桌面端脚本共享实现**（抽公共模块或复用函数），避免两套逻辑各自漂移。

## 验收标准

- [ ] `python3 Script/Python/CliNativeVersionUpdate.py` 能下载并装好最新 CLI 原生镜像到 `Dist/`；
- [ ] `--list` / `--dry-run` / `--tag` / `--platform` / `--jdk` 行为与桌面端脚本一致；
- [ ] 覆盖前旧文件留 `.bak`；
- [ ] 装好后 `Dist/aha-cli-native(.exe)` 的 `--help` / `version` / `run "…"` 可正常运行；
- [ ] `Docs/Guide/CommandCheatsheet.md` §4.2 与 `Dist/README.commands.md` 同步改为「脚本 + 手动」；
- [ ] `python3 bin/CheckScripts.py` 通过（`.py` 为 UTF-8 + LF）。

## 关联

- `Script/Python/DesktopNativeVersionUpdate.py` —— 蓝本
- `.github/workflows/CliNative.yml` —— 产物与命名
- `Docs/Design/CliNativeDesign.md`
- `Docs/Guide/CommandCheatsheet.md` §4.2、`Dist/README.commands.md`
