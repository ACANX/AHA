# 发布流程

**文档版本**：v1.11.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-06
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

---

## 1. 发布前检查

- [ ] `./mvnw clean verify` 通过（Maven 4）
- [ ] `mvn clean verify` 通过（Maven 3.9.x）
- [ ] 全部测试通过，覆盖率达标
- [ ] 发行包可构建且可运行（见 §3.1）
- [ ] `Docs/DevSpec/` 全部文档已审查
- [ ] `CHANGELOG.md` 已更新
- [ ] 版本号已更新（`pom.xml`）

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

1. 从 `dev` 创建 `release/x.y.z` 分支
2. 更新版本号与 `CHANGELOG.md`
   - 版本号要改 **8 处**（实测：只改根 POM 会 **BUILD SUCCESS 但产物仍是旧版本号**）：
     根部 `pom.xml` 的 `<version>` + **六个子模块** `aha-*/pom.xml` 里 `<parent>` 下的
     `<version>` + `AppVersion.FALLBACK_VERSION`（在 `aha-common`；只在 IDE 直接运行、资源未过滤时出现）
   - 可用一条命令统一改（需联网取 maven-versions-plugin）：

     ```
     ./mvnw versions:set -DnewVersion=0.1.1 -DgenerateBackupPoms=false
     ```

     **教训（2026-10-07 实测）**：只改根 POM 时六个子模块仍按 `<parent>` 声明的旧版本解析，
     反应堆显示 `Building AHA-Common 0.1.0`、产物名为 `aha-common-0.1.0.jar`、
     `aha --version` 仍报旧版本，而构建**不报错**——静默发出错版本的包。
   - CLI 的 `aha version` / `aha -V` 由资源过滤注入（`version.properties`），改 POM 即生效
3. 执行完整构建与验收
4. 合入 `main` —— **tag 由 CI 自动打**：`Build.yml` 的 `tag` 作业在 `main` 上的构建成功后，
   按父 POM 的 `<version>` 创建 `V<版本号>`（如 `V0.1.0`）并推送；同一版本已存在则跳过（幂等）。
   手工补打的方法见 4.1
5. 触发 `Release.yml`（产出 CLI 包 + 各平台桌面端包并上传到 release 页面）——注意 4.2 的限制
6. 合回 `dev`，删除 `release/*` 分支

### 4.1 tag 命名与手工补打

- 约定：**`V<版本号>`**（大写 `V`，版本号取自根 `pom.xml` 的 `<version>`，如 `V0.1.1`）。
  ⚠ 历史例外：0.1.0 那次发布的 tag 是 **`0.1.0`**（无 `V` 前缀），与约定不一致；
  处置见 [TODO.md](../TODO.md) `G-06`。
  版本号的**唯一来源是根 POM**，工作流与文档都不复制它。
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
不是配置错误。因此自动打出的 `V*` tag **不会**自动开跑 `Release.yml`。想让发布跟上，二选一：

| 做法 | 说明 |
| ---- | ---- |
| **手工触发发布** | tag 出来后，按 `Release.yml` 的触发条件手工发起（或本地 `git push` 一个 tag） |
| **改用 PAT** | 建一个细粒度 token（仅 `contents: write`）存为仓库机密，`Build.yml` 的 tag 作业用它推送；此时 tag 事件会正常触发 `Release.yml` |

## 3. 制品

| 版本 | CLI（平台无关，一份包通吃） | 桌面端（按平台出包） |
|---|---|---|
| 0.1 | `dist/` 目录（`bin/` + `lib/`），发布为 `aha-<版本>-cli.zip` | — |
| 0.2 | 同上 | `aha-desktop-<版本>-<系统>-<架构>.zip`（便携包，见 3.2）；jpackage 安装包待评估 |
| 1.x | native-image | jpackage (MSI/DEB/DMG) |

### 3.2 桌面端按平台出包（0.2 起）

**为什么必须分平台**：OpenJFX 的原生库按平台分类器发布，且 `jpackage` 不能交叉编译
（见 [DesktopDesign.md](../Design/DesktopDesign.md) 第 5 节）。所以**每个平台的包由该平台的 runner 产出**。

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

**不含**：JDK 运行时（用户需自备 JDK 25；自包含安装包见 `TODO.md` `D-08`）、
测试与构建期依赖（`test` / `provided` scope）。

**完整性自证（强制）**：打包后解包，用「一个不存在的主类」触发 JVM 的模块图解析——
依赖齐全时只会报 `Could not find or load main class …__CompletenessProbe__`，
缺依赖则启动阶段报 `FindException`，后者即失败。另核对本平台 OpenJFX 恰为 3 个、
启动脚本与主工件存在。
> 注意：**不能**用 `java --validate-modules` 当这个判据——实测抽掉 `aha-core` 后它照样退出 0、一字不说。

**新增平台**：加一条矩阵腿，并确认 Maven Central 有对应分类器。当前 Central 上 OpenJFX 只有
`win` / `linux` / `linux-aarch64` / `mac` / `mac-aarch64`——**没有 `win-aarch64`**，故 Windows ARM 不在范围。

### 3.1 发行包验证（发布前必做）

```bash
./mvnw clean package
./dist/bin/Aha.sh version     # 应输出版本号
./dist/bin/Aha.sh --help      # 应列出全部命令
```

`dist/` 结构：`bin/`（启动脚本）+ `lib/`（JPMS 模块路径：本项目模块 + 全部运行时依赖）。
发布流程将其打包为 `aha-<版本>-cli.zip` 并上传到 GitHub Release；`dist/` 不入库。
桌面端另有按平台命名的便携包，见 3.2。

> **jpackage 不能交叉编译**，0.2 起的桌面端产物必须分平台构建。
> **桌面端 WebView 不支持 native-image**，1.x 的 native-image 流水线仅覆盖 CLI + Core + Tools。

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
   （`mergeable_state=dirty`）。详见 [DevLog-20261007-22.md](../DevLog/DevLog-20261007-22.md)。
3. **用了 squash / rebase 就必须删源分支**。这两种合并的代价就是失去血缘、补不回来；
   若源分支还要继续用，就只能真合并。
4. **长期集成分支被误用 squash 之后，必须立刻接回血缘**：在源分支上
   `git merge -s ours <上游>`（先按第 6 条的办法证明上游内容是源分支的子集），
   把上游记为父提交、树保持不变。**不接回的后果是必然的**——下一次 `dependa → dev`
   的 PR 又会 `dirty`，本次已实际复发过一次（见 `DevLog/DevLog-20261007-22.md` 补记）。
   若选择**不复位也不接回**、任源分支落后于上游，则第一次整合时的实测后果是：
   两侧相对分叉点都改过的文件会冲突（本次实测 6 个文档文件），虽然取上游版本即可解决，
   但那是一次纯人工的重复劳动——所以正解是第 5 条。
5. **更根本的预防：别让 squash 对长期集成分支可用**。仓库设置里关闭 squash 与 rebase
   合并、只留 `Create a merge commit`，血缘由平台保证，不再依赖人记得住——
   这件事与分支规则集同属仓库设置，已登记在 [TODO.md](../TODO.md) `G-05`。
6. **合并前后用树的逐字节比对确认没丢内容**：

   ```
   git rev-parse origin/<base>^{tree}
   git rev-list <head> | while read c; do \
     [ "$(git rev-parse $c^{tree})" = "$(git rev-parse origin/<base>^{tree})" ] && echo "等于 $c"; done
   ```

   若在 head 的历史里能找到 base 的树（说明 base 的内容已被包含），才可以用
   `git merge -s ours` 补血缘；**找不到就不能用**——那属于正常内容合并，
   必须逐条读懂冲突再取舍，绝不能一律选一边。

### 4.2 已经冲突了怎么办

先别猜，按顺序比对两边的提交与**树**：

```
git fetch --all --prune
git log --oneline origin/<base> --not origin/<head>
git log --oneline origin/<head> --not origin/<base>
git log -1 --format='%h %p %s' origin/<base>     # 只有一个父提交却写着 Merge 就是「假合并」
```

> 调试远端行为时一律用 `origin/<branch>` 显式引用。本地分支引用可能过期，
> 会给出「Already up to date」这种把人带偏的结论。
