# 发布流程

**文档版本**：v1.19.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-09
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.1.0 | 2026-10-06 | 新增 1.0 发布的自举验收附加检查 | @ACANX |
| v1.2.0 | 2026-10-06 | 新增发行包构建与验证；制品改为 `dist.zip` | @ACANX |
| v1.3.0 | 2026-10-07 | 发布步骤明确「版本号只需改根 `pom.xml`」（CLI 版本由资源过滤注入） | @ACANX |
| v1.4.0 | 2026-10-07 | 新增 §4「分支流向与合并方式（强制）」：长期集成分支只能真合并、禁止把内容重新落地、squash/rebase 后必须删源分支，并给出已冲突时的比对步骤与 `-s ours` 的使用前提 | @ACANX |
| v1.4.1 | 2026-10-07 | §4.1 引用同步开发日志改名（`DevLog-20261007-21-2.md` → `DevLog-20261007-22.md`） | @ACANX |
| v1.5.0 | 2026-10-07 | §4.1 补两条：长期集成分支被误用 squash 后必须立刻用 `-s ours` 接回血缘（附本次实际复发）；更根本的预防是仓库设置关闭 squash/rebase、只留 merge commit（登记为 `G-05`） | @ACANX |
| v1.6.0 | 2026-10-07 | §4.1 补实测后果：源分支落后上游时，首次整合会在两侧都改过的文件上冲突（本次实测 6 个文档文件），取上游版本可解，但属纯人工重复劳动，正解是关闭 squash（`G-05`） | @ACANX |
| v1.7.0 | 2026-10-07 | §2 发布步骤改为「合入 main 后 tag 由 CI 自动打」（`Build.yml` 的 tag 作业，按父 POM 版本创建 `V<版本号>`，幂等）；新增 §4.1（tag 命名与手工补打）与 §4.2（用 `GITHUB_TOKEN` 推的 tag 不触发下游工作流，附两条补救路径） | @ACANX |
| v1.8.0 | 2026-10-08 | 第 2 节更正版本号清单（8 处：根 POM + 六子 POM 的 `<parent>` + `AppVersion.FALLBACK`），补 `versions:set` 命令与「只改根 POM 静默产出旧版本」的实测教训；§4.1 示例改 `V0.1.1` 并标注 0.1.0 的裸 tag 例外 | @ACANX |
| v1.9.0 | 2026-10-08 | §3 制品表改为「CLI 平台无关 / 桌面端按平台」；新增 §3.2「桌面端按平台出包」（命名、包布局、自证规则、如何新增平台）；CLI 资产改名 `aha-<版本>-cli.zip` | @ACANX |
| v1.10.0 | 2026-10-08 | §3.2 补包内依赖清单（18 个 jar 的分类构成）、不含项（JDK / 测试依赖）与「模块图完整性自证」（并记录 `--validate-modules` 不能当判据的实测） | @ACANX |
| v1.11.0 | 2026-10-08 | 第 2 节版本号清单更正枚举名与位置：`AppVersion.FALLBACK` → `AppVersion.FALLBACK_VERSION`（`aha-common`，D-10 下移） | @ACANX |
| v1.12.0 | 2026-10-09 | 第 2 节「构建版本（预发行）」补 `BuildJVMArtifacts.yml`：dev 的 JVM 便携包出包线同样传 `-Daha.build.version=<a.b.c.PPPPP>`（issue #63） | @ACANX |
| v1.13.0 | 2026-10-09 | §3 与 §3.1 的 CLI 资产名统一为 `aha-cli-<版本>.zip`（前缀 `aha-cli` 与桌面端 `aha-desktop-` 对称）；dev JVM 线的 JDK 27 变体为 `aha-cli-<版本>-jdk27.zip` | @ACANX |
| v1.14.0 | 2026-10-09 | §4.1 第 5 条「关闭 squash / rebase 合并」的登记处由 `TODO.md` `G-05` 改为 GitHub Issue（#85）：`TODO.md` 已冻结，待办统一走 Issue | @ACANX / CNXNC |
| v1.15.0 | 2026-10-09 | 第 2 节版本源口径：P3（#95）引入 `<revision>` 后 POM 侧收敛为 **1 处**（8 子模块写 `${revision}` 继承），`AppVersion.FALLBACK_VERSION` 解耦为 `"dev"`；一次性命令改用 `versions:set-property -Dproperty=revision`；§2 第 4 步与 §4.1 的版本来源改指 `<revision>` | @ACANX / CNXNC |
| v1.16.0 | 2026-10-09 | P5（#97，父 #92）文档口径同步：§2 版本切换改为「改 `version` 文件 / 触发 `VersionBump` workflow」；§4.1 与 §4.2 的版本号来源改指根目录 `version` 文件（权威），`<revision>` 降为机器位置；§1 检查清单与 §4.2 ④ 的「8 处」改为 `ProjectVersion.py --verify` 一键校对 | @ACANX / CNXNC |
| v1.17.0 | 2026-10-09 | 验证路径口径调整（F12 方案变更，父 #92）：§2 新增「验证时机」——版本切换 PR 上不跑 `Build` / `Gate` / `Compat`，验证由合并到 `dev` 后的 `Build` + 三条出包线承担；§4.2 去掉「改用 PAT」选项，明确手工重建 tag 为唯一处置（`Release.yml` 无 `workflow_dispatch`） | @ACANX / CNXNC |
| v1.18.0 | 2026-10-09 | **发行形态最终决策**：只保留「JVM JAR 聚合包（解压后脚本启动）」与「原生镜像二进制」两种，**`jpackage` 安装包跳过、不采用**；§3 制品表按新形态重写并新增 §3.3（决策与三条理由）；§3.1 / §3.2 去掉 `jpackage` 相关表述——「必须分平台构建」的依据改为 JavaFX 平台分类器 | @ACANX / CNXNC |
| v1.19.0 | 2026-10-09 | §4.1 的「0.1.0 裸 tag 历史例外」标记为**已处置**：补了指向**同一提交**（`9138847`）的别名 tag `V0.1.0`，`CHANGELOG` 的 `releases/tag/V0.1.0` 链接不再 404（Issue #85 的 `G-06`） | @ACANX / CNXNC |

---

## 1. 发布前检查

- [ ] CI 的 `Gate.yml`（`clean verify` + 覆盖率门禁）通过
- [ ] CI 的 `Compat.yml`（Maven 3.9.x）通过
- [ ] 全部测试通过，覆盖率达标（读 CI 日志，不在本地重跑）
- [ ] 发行包可构建且可运行（见 §3.1）
- [ ] `Docs/DevSpec/` 全部文档已审查
- [ ] `CHANGELOG.md` 已更新
- [ ] 版本号已更新（根目录 `version` 文件为权威源，机器位置根 POM 的 `<revision>` 与之一致）

### 1.1 1.0 发布附加检查（自举硬门槛）

1.0 正式版发布前，除上述通用检查外，还须逐项确认以下自举验收标准
（详见 [SelfHostingDesign.md](../Design/SelfHostingDesign.md)）：

- [ ] A1 工具完备：文件读写、命令执行、HTTP、记忆写入与检索、会话恢复均可用且权限受控
- [ ] A2 技能完备：`build` / `test` / `release` 技能能驱动完整交付闭环
- [ ] A3 持续性：连续 ≥ 4 周、≥ 30 次提交由 AHA 产出，期间未启动其他 Harness
- [ ] A4 质量不降级：评审一次通过率 ≥ 80%，CI 通过率与人工产出无显著差异
- [ ] A5 发布闭环：至少 1 个 minor 版本的完整发布由 AHA 驱动
- [ ] A6 可审计：自举期提交信息均标注 `Harness:` 行

> **未达成任一项，不得发布 1.0 正式版。**

## 2. 发布步骤

> 逐步操作、**检查清单**、常见问题快速处置与**应急预案**见
> [VersionBumpGuide.md](../Guide/VersionBumpGuide.md)；本节只列硬性要求。

1. 从 `dev` 创建 `release/x.y.z` 分支
2. 更新版本号与 `CHANGELOG.md`
   - **版本源（P4 起）**：唯一权威源是**根目录 `version` 文件**（一行，如 `0.1.2`）；
     机器侧只保留根 `pom.xml` 的 `<properties>/<revision>` **一处**（8 个子模块写
     `<parent><version>${revision}</version>` 继承），`AppVersion.FALLBACK_VERSION`
     已与版本解耦（固定 `"dev"`）。
   - **改法（推荐）**：触发 **`VersionBump` workflow**（`workflow_dispatch`，输入新版本号），
     由 `Script/Python/VersionDistribute.py` 把 `version` 分发到根 POM 的 `<revision>`
     与 4 处文档版本声明，并自动开 PR（`chore/bump-<版本>` → `dev`）；也可直接改
     `version` 文件一行，再按常规 PR 流程提交。操作细节见
     [VersionBumpGuide.md](../Guide/VersionBumpGuide.md)。
   - **一致性校验（强制）**：`python3 .github/Python/ProjectVersion.py --verify` 断言
     `version` 文件 / 根 POM `<revision>` / `version.properties` 的 `version` /
     产物名版本段 / `Build.yml` 的 tag 规则五处一致（已接入 `Gate.yml`）。
   - **验证时机**：版本切换 PR 上**不跑** `Build` / `Gate` / `Compat`——`GITHUB_TOKEN` 推的
     提交不触发下游 workflow，且后两者的 `pull_request` 只覆盖 `main` / `release/**`。
     验证由**合并到 `dev` 后**自动触发的流水线承担：`Build.yml`（`on: [push, pull_request]`）
     与 `BuildJVMArtifacts` / `CliNative` / `DesktopNative`（`push: branches: [dev]`）。
     PR 侧的正确性由 `VersionBump` 自身的「自检」步骤把关。**本仓库不为此配置 PAT。**
   - **手工应急**（不走 workflow 时）：只需改 **`version` 文件与根 POM 的 `<revision>` 两处**，
     两者必须相同；子模块与文档声明由分发脚本负责。当初「为何要改 10 处」的历史口径见
     [VersionBumpGuide.md](../Guide/VersionBumpGuide.md) 第 7 节与
     [TS-202610-VersionBumpMissedModules.md](../Troubleshooting/TS-202610-VersionBumpMissedModules.md)。
   - CLI 的 `aha version` / `aha -V` 由资源过滤注入（`version.properties`），改 POM 即生效
   - **构建版本（预发行）**：`version.properties` 另有 `build=${aha.build.version}` 项；正式发版与
     本地构建不传，等于基线版本；PR 合并到 `dev` 后自动出包的工作流（`DesktopNative.yml` /
     `CliNative.yml` / `BuildJVMArtifacts.yml`）会传 `-Daha.build.version=<a.b.c.PPPPP>`（如 `0.1.1.00046`），
     供 GUI「关于」与 CLI 启动页 / `/help` 显示，便于按版本号排查（issue #46）
3. 执行完整构建与验收
4. 合入 `main` —— **tag 由 CI 自动打**：`Build.yml` 的 `tag` 作业在 `main` 上的构建成功后，
   按根 POM 的 `<revision>` 创建 `V<版本号>`（如 `V0.1.0`）并推送；同一版本已存在则跳过（幂等）。
   手工补打的方法见 4.1
5. 触发 `Release.yml`（产出 CLI 包 + 各平台桌面端包并上传到 release 页面）——注意 4.2 的限制
6. 合回 `dev`，删除 `release/*` 分支

### 4.1 tag 命名与手工补打

- 约定：**`V<版本号>`**（大写 `V`，版本号取自根目录 `version` 文件，如 `V0.1.1`）。
  **历史例外已处置（2026-10-09）**：0.1.0 发布时打的是裸 tag `0.1.0`；现已补一个指向
  **同一提交**（`9138847`）的别名 tag **`V0.1.0`**——两种写法都可用，`CHANGELOG` 的
  `releases/tag/V0.1.0` 链接不再 404（做法示例见 [VersionBumpGuide.md](../Guide/VersionBumpGuide.md) §6.5）。
  版本号的**唯一权威源是根目录 `version` 文件**（机器位置是根 POM 的 `<revision>`，
  由 `VersionDistribute.py` 写入），工作流与文档都不复制它。
- 自动打 tag 的触发条件：**push 到 `main`**（`dev` → `main` 的 PR 合并之后）且 `Build.yml` 的
  `build` 作业成功。
- 需要手工补打的情形：那条**可选**的 macOS 腿没有通过、连带 `build` 作业未算成功；
  或推送 tag 时权限不足。手工命令：

  ```
  git fetch origin main && git switch --detach origin/main
  git tag -a V0.1.1 -m "AHA V0.1.1" && git push origin V0.1.1
  ```

### 4.2 自动打 tag 不触发发布（务必知晓）

**用 `GITHUB_TOKEN` 推送的 tag 不会触发下游工作流**——这是 GitHub 防递归的既定行为，
不是配置错误。因此自动打出的 `V*` tag **不会**自动开跑 `Release.yml`。

`Release.yml` 只由 `push: tags: ['V*', 'v*']` 触发、**没有 `workflow_dispatch`**，所以唯一的
处置是**手工重建这个 tag**，让 tag 事件真实发生一次（人推的 tag 不受防递归限制）：

```
git push origin --delete V0.1.2                                # 删掉自动打的 tag（指向同一提交，不动提交历史）
git tag -a V0.1.2 -m "AHA V0.1.2" <发布提交>                    # 重新打同一个 tag
git push origin V0.1.2                                         # 这次由人推送 → 触发 Release.yml
```

> **本仓库不为此配置 PAT**。备选做法是建一个细粒度 token（仅 `contents: write`）存为
> 仓库机密、让 `Build.yml` 的 tag 作业用它推送，tag 事件便会自动触发发布——**未采用**，
> 统一走上面的手工重建。

## 3. 制品

| 版本 | CLI（平台无关，一份包通吃） | 桌面端（按平台出包） |
|---|---|---|
| 0.1 | `Dist/` 目录（`bin/` + `lib/`），发布为 `aha-cli-<版本>.zip` | — |
| 0.2 | 同上 | `aha-desktop-<版本>-<系统>-<架构>.zip`（便携包，见 3.2） |
| 1.x | 同上 + `native-image` 单文件 | 同上 + `native-image` 单文件 |

> 发行形态只保留哪两种、为什么跳过 `jpackage`，见 3.3。

### 3.2 桌面端按平台出包（0.2 起）

**为什么必须分平台**：OpenJFX 的原生库按平台分类器发布，**每个平台的包只能由该平台的 runner 产出**
（见 [DesktopDesign.md](../Design/DesktopDesign.md) 第 5 节）。

`Release.yml` 的 `desktop` 作业按矩阵出包，用户按自己的系统与架构下载对应文件：

| 平台 | 下载文件 |
|---|---|
| Windows x86_64 | `aha-desktop-<版本>-windows-x64.zip` |
| Linux x86_64 | `aha-desktop-<版本>-linux-x64.zip` |
| macOS Apple Silicon | `aha-desktop-<版本>-macos-arm64.zip` |

包内布局：`bin/`（`AhaDesktop.sh` / `AhaDesktop.bat`）+ `lib/`（本项目模块 + 全部运行时依赖 +
**本平台**三个 OpenJFX jar）+ `README.md` / `CHANGELOG.md` / `LICENSE`。
解包后只需机器上有 JDK 25，运行 `bin/AhaDesktop.sh` 或 `bin/AhaDesktop.bat` 即可。

**自证（强制）**：矩阵每条腿声明本腿**应当**解析出的平台分类器；构建后脚本从**产物名**
（`finalName` 由 profile 决定，含版本与分类器）解析实际值并断言两者一致，同时打印依赖树里
的 OpenJFX 工件。不一致即失败——GitHub 若改了 runner 架构（如 `macos-latest` 换架构），
会立刻报错，而不是把错平台的包静静挂上 release 页面。

**包内含的依赖（实测 Linux 包，`lib/` 共 18 个 jar）**：

| 类别 | 数量 | 内容 |
|---|---|---|
| 本项目模块 | 5 | `aha-desktop` / `aha-core` / `aha-common` / `aha-extension-api` / `aha-tool` |
| 本平台 OpenJFX | 3 | `javafx-base` / `javafx-graphics` / `javafx-controls`（带平台分类器，且**只有本平台**） |
| 第三方 | 10 | Jackson 4 件、Log4j2 3 件 + SLF4J、snakeyaml-engine、sqlite-jdbc |

**不含**：JDK 运行时（JVM 便携包要求用户自备 JDK 25；免 JDK 的场景走原生镜像单文件，见 3.3）、
测试与构建期依赖（`test` / `provided` scope）。

**完整性自证（强制）**：打包后解包，用「一个不存在的主类」触发 JVM 的模块图解析——
依赖齐全时只会报 `Could not find or load main class …__CompletenessProbe__`，
缺依赖则启动阶段报 `FindException`，后者即失败。另核对本平台 OpenJFX 恰为 3 个、
启动脚本与主工件存在。
> 注意：**不能**用 `java --validate-modules` 当这个判据——实测抽掉 `aha-core` 后它照样退出 0、一字不说。

**新增平台**：加一条矩阵腿，并确认 Maven Central 有对应分类器。当前 Central 上 OpenJFX 只有
`win` / `linux` / `linux-aarch64` / `mac` / `mac-aarch64`——**没有 `win-aarch64`**，故 Windows ARM 不在范围。

### 3.1 发行包验证（发布前必做）

> 发行包由 CI 构建；下面的命令**不在本地执行 Maven**。验证在 CI 或用户明确要求的发布场景中完成。

```bash
./mvnw clean package
./Dist/bin/Aha.sh version     # 应输出版本号
./Dist/bin/Aha.sh --help      # 应列出全部命令
```

`Dist/` 结构：`bin/`（启动脚本）+ `lib/`（JPMS 模块路径：本项目模块 + 全部运行时依赖）。
发布流程将其打包为 `aha-cli-<版本>.zip` 并上传到 GitHub Release；`Dist/` 不入库。
桌面端另有按平台命名的便携包，见 3.2。

> **OpenJFX 原生库按平台分类器发布**，0.2 起的桌面端产物必须分平台构建。
> **桌面端 WebView 不支持 native-image**：原生镜像的桌面端走「JavaFX 原生控件」路线
> （见 [DesktopNativeDesign.md](../Design/DesktopNativeDesign.md)）。

### 3.3 发行形态（最终决策，2026-10-09）

本项目**只支持两种发行形态**，与「平台」这条轴正交：

| 形态 | 产物 | 启动方式 | 前提 |
|---|---|---|---|
| **JVM JAR 聚合包** | `aha-cli-<版本>.zip`；`aha-desktop-<版本>-<系统>-<架构>.zip`（见 3.2） | 解压后跑 `bin/Aha.sh`、`bin/AhaDesktop.sh` / `.bat` | 机器需自备 JDK 25 |
| **原生镜像二进制** | `native-image` 单文件（`aha-cli-native` / `aha-desktop-native`，试验性） | 直接执行二进制 | 无需 JVM |

**`jpackage` 安装包（MSI / DEB / DMG）已决策跳过，不采用**。理由：

1. 它是**第三种**发行形态，会额外引入安装器矩阵与代码签名 / 公证的长期维护面；
2. 「免装 JDK」这一目标已由**原生镜像二进制**覆盖，而且更彻底（连 JVM 都不需要）；
3. 现有两种形态都沿用同一套**产物名自证 + 完整性自证**链路（见 3.2），再加安装器等于多维护一条独立校验链。

> 备注：`jlink` 与 `jpackage` 的**技术限制**（不接受自动模块、不能交叉编译）仍保留在设计文档里——
> 它们是「桌面端产物为何必须分平台构建」的依据，不因本决策而删除。

---

## 4. 分支流向与合并方式（强制）

`dependa` 是 Dependabot 的 `target-branch`，也是长期存在的集成分支：它**持续**被合入、
又**持续**往 `dev` / `main` 合。这个双重身份决定了它的合并方式不能随便选。

### 4.1 规则

1. **长期集成分支只能真合并**。`dependa` 合入上游、以及上游合回 `dependa` 时，
   必须留下**真正的合并关系**——`git merge`（双父提交）或 GitHub 的
   `Create a merge commit`。
2. **禁止「把内容重新落一遍」**。例如 `git merge --squash` 后再手工提交、
   把分支上全部提交 cherry-pick 到目标分支等。这类做法会让上游收下内容却没有
   把源分支变成祖先，源分支之后的**每一个** PR 都会永久冲突
   （`mergeable_state=dirty`）。详见 [TS-202610-FakeMergeDirtyPr.md](../Troubleshooting/TS-202610-FakeMergeDirtyPr.md)。
3. **用了 squash / rebase 就必须删源分支**。这两种合并的代价就是失去血缘、补不回来；
   若源分支还要继续用，就只能真合并。
4. **长期集成分支被误用 squash 之后，必须立刻接回血缘**：在源分支上
   `git merge -s ours <上游>`（先按第 6 条的办法证明上游内容是源分支的子集），
   把上游记为父提交、树保持不变。**不接回的后果是必然的**——下一次 `dependa → dev`
   的 PR 又会 `dirty`，本次已实际复发过一次（见 `Troubleshooting/TS-202610-FakeMergeDirtyPr.md` 补记）。
   若选择**不复位也不接回**、任源分支落后于上游，则第一次整合时的实测后果是：
   两侧相对分叉点都改过的文件会冲突（本次实测 6 个文档文件），虽然取上游版本即可解决，
   但那是一次纯人工的重复劳动——所以正解是第 5 条。
5. **更根本的预防：别让 squash 对长期集成分支可用**。仓库设置里关闭 squash 与 rebase
   合并、只留 `Create a merge commit`，血缘由平台保证，不再依赖人记得住——
   这件事与分支规则集同属仓库设置，已登记在
   [Issue #85](https://github.com/ACANX/AHA/issues/85)。
6. **合并前后用树的逐字节比对确认没丢内容**：

   ```
   git rev-parse origin/<base>^{tree}
   git rev-list <head> | while read c; do \
     [ "$(git rev-parse $c^{tree})" = "$(git rev-parse origin/<base>^{tree})" ] && echo "等于 $c"; done
   ```

   若在 head 的历史里能找到 base 的树（说明 base 的内容已被包含），才可以用
   `git merge -s ours` 补血缘；**找不到就不能用**——那属于正常内容合并，
   必须逐条读懂冲突再取舍，绝不能一律选一边。

### 4.2 合并发版 PR 前的三分钟核对（强制）

> **判断「能不能合」看 `mergeable_state`，不看「有没有红叉」。**
> `clean` = 可以合；`blocked` = 缺审批或缺必需检查；`dirty` = 有冲突。
> 红叉可能来自**旧提交**上已被重跑覆盖的作业，肉眼会误判。

合并 `dev → main` 之类的发版 PR 前，逐条走完（每条都是一条命令）：

```bash
# ① 有没有冲突、能不能合（不看页面上的红叉）
curl -s https://api.github.com/repos/<owner>/<repo>/pulls/<n> \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['mergeable'], d['mergeable_state'])"

# ② 基线上有没有 head 缺的提交 —— 有就必须逐个看，尤其会不会「带内容回退」
git fetch --all --prune
git log --oneline origin/<head>..origin/<base>     # 例如 origin/dev..origin/main
#   典型陷阱：main 上那个 "Release:V0.1.0" 提交把根 POM 带成 0.1.0，
#   若合并结果取到它，就会「发布回退版本」——而这不产生任何冲突，很容易漏

# ③ 真正合并一次，然后**证明合并结果就是待发布分支的内容**
git worktree add --detach /tmp/merge-check origin/<base>
cd /tmp/merge-check && git merge --no-ff origin/<head>
git diff --stat origin/<head> HEAD      # 输出为空 = 复议面为零（最有力的结论）
git diff --quiet origin/<head> HEAD || echo '⚠ 合并结果与 head 不一致，逐条看过再推'

# ④ 版本号一致性核对（version 文件 / <revision> / version.properties / 产物名 / tag 规则）
python3 .github/Python/ProjectVersion.py --verify
```

**复议面为零**（③ 输出为空）是发布前最强的自证：它同时排除了「冲突解错」、「旧内容覆盖新内容」、
「CI 配置被回退」这三类最难事后发现的合并事故。

### 4.3 已经冲突了怎么办

先别猜，按顺序比对两边的提交与**树**：

```
git fetch --all --prune
git log --oneline origin/<base> --not origin/<head>
git log --oneline origin/<head> --not origin/<base>
git log -1 --format='%h %p %s' origin/<base>     # 只有一个父提交却写着 Merge 就是「假合并」
```

> 调试远端行为时一律用 `origin/<branch>` 显式引用。本地分支引用可能过期，
> 会给出「Already up to date」这种把人带偏的结论。
