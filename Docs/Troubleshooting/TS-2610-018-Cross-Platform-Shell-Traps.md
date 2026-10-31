# TS-2610-018 · 修复 run 37744912968：macOS 丢镜像包 / Windows 工具链自证退出（跨平台 shell 两个坑）

> 日期：2026-10-08
> 作者：@ACANX（与 AI 助手协作）
> 关联分支：`fix/native-ci-selfcheck`
> 关联记录：`Docs/Design/DesktopNativeDesign.md` §6.1（R8）、`.agents/skills/java-app-graalvm-native-image-compile/references/isolation-and-ci.md` §3

## 1. 现象

`DesktopNative` 第 15 次运行（`37744912968`）整体结论是 **success**（失败被
`continue-on-error` 吞掉），但两个腿各有一条红：

| 腿 | 位置 | 报错 |
|---|---|---|
| macos-arm64-jdk25 | 产物自证 | `Process completed with exit code 1` |
| windows-amd64-jdk25 | 工具链自证 | `Process completed with exit code 1` |

更严重的后果在制品列表里：**没有 `Native-macos-arm64-jdk25`**——
macos 的镜像包根本没上传（报告包在，镜像包缺）。用户要的就是把 macos 构件补回来。

## 2. 根因（拿到 job 日志后一目了然）

### 2.1 macOS：bash 把全角括号并进了变量名

```
/Users/runner/work/_temp/xxxx.sh: line 39: bin（: unbound variable
##[error]Process completed with exit code 1.
```

对应脚本行是：

```bash
echo "找到可执行文件：$bin（$((size / 1024)) KB）"
```

`$bin` 后紧跟全角括号 `（`。**macOS 的 bash 3.2 把非 ASCII 字符也当成变量名的一部分**，
于是它去找名为 `bin（` 的变量；`set -u` 下直接退出。

后果链：脚本在 `produced=true` **之前**退出 → 步骤的 `outputs` 没有被发布 →
下游 `if: steps.verify.outputs.produced == 'true'` 静默变 false →
「改名」「上传制品」两步 `skipped` → macos 镜像包丢失。

### 2.2 Windows：注入的 `PLATFORM` 与宿主环境打架

```
D:\a\_temp\xxxx.sh: line 3: PLATFORM: unbound variable
```

日志的 step env 列表里只有 `LABEL`、`VERSION`，**没有 `PLATFORM`**。
Windows runner 的 shell 还带着 Visual Studio 开发者命令提示的环境
（`CommandPromptType`、`DevEnvDir`、`EXTERNAL_INCLUDE`…），其中本就有 `PLATFORM`
这类变量，与 step 注入互相打架。`set -euo pipefail` 下直接退出。

### 2.3 附带：魔法数比较一直不匹配

```
warning | 魔法数不匹配（期望 4d5a，实际 4d5a9000）
```

`head -c 4` 读 4 字节，Windows 的 PE 前 4 字节是 `4d 5a 90 00`，
而期望写的是 2 字节的 `4d5a`——**永远不可能匹配**。其余平台的期望值恰好是 4 字节，
所以只有 Windows 报。

## 3. 修法

### 3.1 自证脚本（`DesktopNative.yml` 产物自证）

- **变量后跟全角字符一律写 `${VAR}`**（`${bin}（`、`${note}；`、`${rpt_note}——`…）；
- 命中有效产物就**立刻**写 `produced=true`，不再等脚本末尾：
  outputs 的生死不能押在「后面一长串诊断都不出错」上；
- 加 `trap 'exit 0' EXIT` 兜底：任何意外（unbound、平台差异命令失败）都以 0 退出，
  outputs 保留已写值；
- 去掉 `find -maxdepth`：macOS 的 BSD find 不支持（unknown primary）；
- 魔法数**只比前 2 字节**（Windows `4d5a`、Linux `7f45`、macOS `cffe`，另兼容大端 `feed`）。

### 3.2 工具链自证

- 注入名加前缀，避开宿主环境：`LEG_PLATFORM` / `LEG_LABEL` / `LEG_VERSION`；
- 全部用 `${VAR:-}` 读取；
- 该步只做记录，`set -uo pipefail`（去掉 `-e`），版本查询失败加 `|| echo` 不判死。

### 3.3 同类写法一并修

`Release.yml` 的 `$LABEL）`、`$target（`、`$out）`、`$classifier。`，
以及技能模板 `assets/workflow-native.yml` 与 `references/isolation-and-ci.md` §3 的同类写法。

## 4. 守卫（同一条规则不再靠人盯）

`bin/CheckScripts.py` 新增 YAML 检查：扫描 `*.yml` / `*.yaml`，凡是
`$VAR` 后紧跟非 ASCII 字符且未用 `${}` 就报错；`${{ ... }}` 表达式与 `\$` 转义不误报。

**反向验证**：造一个 `bin/_probe.yml`，其中 `$bin（` 被抓住、`${bin}（` 与 `${{ ... }}`
通过、`\$zzz（` 不误报；删除探针后全绿。

## 5. 验证

- `bin/CheckScripts.py`：42 个脚本（14 XML、15 YAML）全绿，并反向验证；
- `bin/CheckDocs.py` / `bin/CheckSkills.py` 全绿；
- 三个 YAML（DesktopNative / Release / 技能模板）解析通过；
- 守卫抓出并修复了 4 处历史遗留（含技能模板与 `Release.yml`）。

## 6. 教训

1. **跨平台 shell 的差异不止是命令，还有变量名规则**：`$VAR` 后跟中文/全角字符在
   macOS 上会被并进变量名。写 shell 时「变量边界」要靠 `${}` 显式划定。
2. **诊断步骤的 outputs 必须尽早落袋**：诊断越多，中途出错的机会越多；
   先写结论、再用 `trap` 兜底，才能保证「诊断失败 ≠ 交付物丢失」。
3. **同一条规则踩第二次就写守卫**：这次把「`$VAR` + 非 ASCII」固化进 `CheckScripts.py`，
   并且守卫当场又抓出 4 处。

## 7. 涉及文件清单

- `.github/workflows/DesktopNative.yml`
- `.github/workflows/Release.yml`
- `bin/CheckScripts.py`
- `.agents/skills/java-app-graalvm-native-image-compile/assets/workflow-native.yml`
- `.agents/skills/java-app-graalvm-native-image-compile/references/isolation-and-ci.md`
- `.agents/skills/java-app-graalvm-native-image-compile/SKILL.md`
- `Docs/Design/DesktopNativeDesign.md`（§6.1 R8）
- `CHANGELOG.md`

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- `DesktopNative` 第 **15** 次运行（`37744912968`）**整体结论是 `success`**
  （失败被 `continue-on-error` 吞掉），但两条腿各有一条红：

  | 腿 | 位置 | 报错 |
  | --- | --- | --- |
  | `macos-arm64-jdk25` | 产物自证 | `Process completed with exit code 1` |
  | `windows-amd64-jdk25` | 工具链自证 | `Process completed with exit code 1` |

- **更严重的后果在制品列表里**：**没有 `Native-macos-arm64-jdk25`**——
  macos 的**镜像包根本没上传**（报告包在，镜像包缺）。
- **macOS 根因**：`/Users/runner/work/_temp/xxxx.sh: line 39: bin（: unbound variable`；
  对应脚本行是 `echo "找到可执行文件：$bin（$((size / 1024)) KB）"`。
  **macOS 的 bash 3.2 把非 ASCII 字符也当成变量名的一部分**，于是它去找名为 `bin（` 的变量，
  `set -u` 下直接退出。
- **后果链**：脚本在 `produced=true` **之前**退出 → 步骤 `outputs` **没有被发布** →
  下游 `if: steps.verify.outputs.produced == 'true'` **静默变 false** →
  「改名」「上传制品」两步 `skipped` → **macos 镜像包丢失**。
- **Windows 根因**：`D:\a\_temp\xxxx.sh: line 3: PLATFORM: unbound variable`；
  日志的 step env 列表里只有 `LABEL`、`VERSION`，**没有 `PLATFORM`**。
  Windows runner 的 shell 还带着 Visual Studio 开发者命令提示的环境
  （`CommandPromptType`、`DevEnvDir`、`EXTERNAL_INCLUDE`…），其中本就有 `PLATFORM` 这类变量，
  与 step 注入**互相打架**；`set -euo pipefail` 下直接退出。
- **附带问题**：`warning | 魔法数不匹配（期望 4d5a，实际 4d5a9000）`
  ——`head -c 4` 读 **4** 字节，而期望写的是 **2** 字节的 `4d5a`，**永远不可能匹配**；
  其余平台的期望值恰好是 4 字节，所以**只有 Windows 报**。
- 守卫的反向验证：造 `bin/_probe.yml`，其中 `$bin（` 被抓住、`${bin}（` 与 `${{ ... }}` 通过、
  `\$zzz（` 不误报；删除探针后全绿。
- 守卫**当场抓出并修复了 4 处历史遗留**（含技能模板与 `Release.yml`）；
  最终 `CheckScripts.py` 覆盖 **42 个脚本（14 XML、15 YAML）** 全绿，
  `CheckDocs` / `CheckSkills` 全绿，三个 YAML 解析通过。

### 机制 / 行为模型

- **跨平台 shell 的差异不止是命令，还有「变量名规则」**：`$VAR` 后跟中文 / 全角字符
  在 macOS（bash 3.2）上会被**并进变量名**。⇒ 写 shell 时「变量边界」要靠 `${}` **显式划定**。
- **诊断步骤的 `outputs` 必须尽早落袋**：诊断越多，中途出错的机会越多。
  先写结论、再用 `trap 'exit 0' EXIT` 兜底，才能保证「**诊断失败 ≠ 交付物丢失**」。
  本档案的后果链（outputs 未发布 → 下游静默 skipped → 包丢失）正是这条的反例。
- **宿主环境变量会与 step env 打架**：Windows runner 自带 VS 开发者命令提示的环境，
  其中存在 `PLATFORM` 这类常见名 ⇒ 注入名要加**项目前缀**规避。
- **魔法数比较的字节数写错会「永远不匹配」**，而且只在字节数恰好不等的那个平台暴露
  （`4d5a` 对 `4d5a9000`）。
- **同一条规则踩第二次就写守卫**：本次把「`$VAR` + 非 ASCII」固化进 `CheckScripts.py`。

### 未定位 / 未复现

- **本机是 Linux（bash 5），无法复现 macOS bash 3.2 的行为** ⇒ 该跨平台差异**只能靠 CI / 真机暴露**。
- **未穷举其它平台差异**：只修了已知几处（BSD find 的 `-maxdepth`、宿主环境变量）；
  没有系统排查 BSD 与 GNU 工具集的其它分歧。
- **未验证修复后 macos 镜像包是否真的回到制品列表**：档案写作时该运行未重跑。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 运行结论与红叉 | run `37744912968` | 整体 **`success`**（被 `continue-on-error` 吞掉）；macos 产物自证、windows 工具链自证各 exit 1 |
| **交付物损失** | 制品列表 | **缺 `Native-macos-arm64-jdk25`**（报告包在、镜像包缺） |
| macOS 报错原文 | `build` 日志 | `/Users/…/xxxx.sh: line 39: bin（: unbound variable` |
| 触发该报错的那一行 | 脚本原文 | `echo "找到可执行文件：$bin（$((size / 1024)) KB）"` |
| Windows 报错原文 | `build` 日志 | `D:\a\_temp\xxxx.sh: line 3: PLATFORM: unbound variable` |
| step env 实况 | 日志的 env 列表 | 只有 `LABEL`、`VERSION`，**没有 `PLATFORM`** |
| **魔法数不匹配** | 产物自证输出 | `warning \| 魔法数不匹配（期望 4d5a，实际 4d5a9000）` |
| 守卫反向验证 | `bin/_probe.yml` | `$bin（` **被抓住**；`${bin}（`、`${{ ... }}` 通过；`\$zzz（` 不误报 |
| 守卫战果 | 加守卫后重扫 | 抓出并修复 **4 处**历史遗留（技能模板、`Release.yml` 等） |
| 检查覆盖面 | `bin/CheckScripts.py` | **42 个脚本（14 XML、15 YAML）** 全绿 |
| 其它检查 | `CheckDocs` / `CheckSkills` / YAML 解析 | 全绿 / 三个 YAML 通过 |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「整体 `success` 就说明没问题」 | 失败被 `continue-on-error` 吞掉；**制品列表缺 macos 镜像包**（「1、现象」） |
| 「脚本逻辑没错，是上传环节的问题」 | 脚本在 `produced=true` **之前**就因 `bin（` 退出，`outputs` 从未发布（「2.1」） |
| 「Windows 的 `PLATFORM` 是 step 注入的」 | step env 里**根本没有它**，来自 VS 开发者命令提示的宿主环境（「2.2」） |
| 「魔法数不匹配 ⇒ 产物坏了」 | 是**比较写错**（读 4 字节比 2 字节），**永远不匹配**（「2.3」） |
| 「同一条规则靠人盯就行」 | 已固化为 `CheckScripts.py` 的 YAML 检查，并**当场抓出 4 处**（「4、守卫」「6、教训」第 3 条） |

## 未决问题

**Q1 修复后 macos 镜像包回到制品列表了吗？**

现状：**未验证**——档案写作时未重跑该运行。
阻塞点：需要一次新的 `DesktopNative` 运行（与 `TS-2610-014` 的 N-12、`TS-2610-016` 的报告包同批）。

**Q2 还有别的平台差异没覆盖吗？**

现状：只修了已知几处（BSD `find -maxdepth`、宿主环境变量）。
阻塞点：需要一个「macOS 用 bash 3.2 + BSD 工具集」的差异清单式排查，尚未做。

**Q3 宿主环境变量与 step env 的冲突能预防吗？**

现状：本次靠**加前缀**（`LEG_*`）规避。
阻塞点：是否该立一条「工作流里注入的 env 名一律加项目前缀」的规范并机器检查，尚未评估。

**Q4 本机复现不了 macOS 的问题，怎么办？**

现状：Linux（bash 5）与 macOS（bash 3.2）行为不同，这类差异**只能靠 CI 暴露**；
目前只有静态检查（`CheckScripts.py` 的「`$VAR` + 非 ASCII」规则）拦一部分。
阻塞点：是否值得在 CI 里跑一遍 shell 语法/兼容性检查（如 `shellcheck`），未评估。
