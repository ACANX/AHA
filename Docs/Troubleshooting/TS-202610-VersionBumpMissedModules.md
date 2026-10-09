# TS-202610-VersionBumpMissedModules：版本号切换的三处坑：只改根 POM 会静默产出旧版本

> 日期：2026-10-08
> 作者：@ACANX（与 AI 协作）
> 关联 PR：[#12](https://github.com/ACANX/AHA/pull/12) / [#13](https://github.com/ACANX/AHA/pull/13)（自动打 tag 与 CHANGELOG 修订）之后的版本号切换
> 关联记录：[ReleaseProcess.md](../DevSpec/ReleaseProcess.md) 第 2 节、[TODO.md](../TODO.md) `G-06`、[CHANGELOG.md](../../CHANGELOG.md) `[0.1.1]`

---

## 一、背景

在最新的 `dev` 上开特性分支，把项目版本号从 `0.1.0` 切到 `0.1.1`，为下一次发布做准备。

版本号切换看起来是最没有技术含量的一类改动，实际却在三处踩到了坑：一处会**静默产出错版本的包**，
一处让文档链接 **404**，一处把条目记到了错误的版本段。

---

## 二、排障过程与修复链

### 2.1 远端 tag 与文档不一致（链接 404）

上一轮把「自动打 tag」的约定定为 `V<版本号>`，并据此把 `CHANGELOG.md` 里
`[0.1.0]` 的链接从 `releases/tag/v0.1.0` 改成了 `releases/tag/V0.1.0`。

本轮先 `git ls-remote --tags origin` 核实远端：

```
580afe3...  refs/tags/0.1.0        ← 实际存在的 tag 是裸 `0.1.0`，没有 V 前缀
```

即：**约定是 `V*`，而 0.1.0 那次发布的 tag 是 `0.1.0`**（那次发布发生在自动打 tag 作业合入之前，
由人工推送）。于是 `CHANGELOG.md` 里那个链接指向了不存在的 tag。

处置：链接改回真实存在的 `0.1.0`，新增的 `[0.1.1]` 用约定写法 `V0.1.1`；
约定与实际的不一致登记为 `TODO.md` `G-06`（补一个指向同一提交的 `V0.1.0` 别名 tag，或明确兼容策略）。

### 2.2 只改根 POM：BUILD SUCCESS，但产物是旧版本（核心）

`ReleaseProcess.md` 第 2 节原文写着：

> 版本号**只需改根 `pom.xml`**

照此操作——只把根 POM 改成 `0.1.1`，六个子模块的 `<parent><version>` 仍写 `0.1.0`——然后实测：

```
$ ./mvnw -B validate
[INFO] Building AHA 0.1.1                                                 [1/7]
[INFO] Building AHA-Common 0.1.0                                          [2/7]
[INFO] Building AHA-Extension-API 0.1.0                                   [3/7]
[INFO] Building AHA-Core 0.1.0                                            [4/7]
[INFO] Building AHA-Tool 0.1.0                                            [5/7]
[INFO] Building AHA-CLI 0.1.0                                             [6/7]
[INFO] Building AHA-Desktop 0.1.0                                         [7/7]
[INFO] BUILD SUCCESS
```

**不报错**，但六个子模块全部按 `<parent>` 里声明的旧版本解析。继续 `package`：

```
aha-cli/target/aha-cli-0.1.0.jar
aha-common/target/aha-common-0.1.0.jar
...
```

产物名仍是 `0.1.0`，`aha --version` 也仍报 `0.1.0`（`version.properties` 取的是**模块**的
`project.version`）。也就是说：照原文操作会**发出一包版本号错误的发行包，而流水线一路绿灯**。
这类问题比直接失败危险得多——失败会被 CI 拦住，静默的错误版本会一直流到用户手里。

处置：

1. 六个子 POM 的 `<parent><version>` 一并改到 `0.1.1`（共 7 处 POM）；
2. `AppVersion.FALLBACK`（唯一写死的版本字面量，仅在 IDE 直接运行、资源未过滤时出现）同步；
3. `ReleaseProcess.md` 第 2 节的清单更正为 **8 处**，并给出 `./mvnw versions:set -DnewVersion=…`
   这条一次性改全的命令；把上面的实测输出作为教训留在该节。

### 2.3 自动打 tag 的条目记错了版本段

`CHANGELOG.md` 的 `[0.1.0]` 段里被我上一轮加进了「自动打 tag」条目。核对时间线：
`0.1.0` 的发布提交（`main` 上的 `9138847`）早于自动打 tag 作业合入 `dev`——该能力**不属于 0.1.0**。

处置：条目搬到新增的 `[0.1.1]` 段；`[0.1.0]` 段的发布日同时由 `2026-10-06` 更正为实际发布日
`2026-10-07`（`9138847` 的提交时间 `2026-10-07 23:39:11 +0800`，`PLAN.md` §8.2.2 就此结项）。

---

## 三、最终验证结果

| 验证点 | 命令 | 结果 |
| ------ | ---- | ---- |
| 反应堆版本 | `./mvnw -B validate` | ✅ 7 个模块全部 `0.1.1` |
| 注入的版本资源 | `cat aha-cli/target/classes/.../version.properties` | ✅ `version=0.1.1` |
| 产物名 | `ls */target/*.jar` | ✅ 全部 `-0.1.1.jar` |
| CLI 实际输出 | `bash Dist/bin/Aha.sh version` | ✅ `AHA 0.1.1` |
| 完整构建 | `./mvnw clean verify` | ✅ BUILD SUCCESS，2 分 24 秒 |
| 用例 | 同上 | ✅ 605（57 + 6 + 194 + 25 + 323），0 失败 |
| 覆盖率 | `bin/ReportCoverage.py` | ✅ 合计 80.3%（4045/5038），门禁 0.70 通过 |
| 文档口径 | `grep -rn '当前版本\|目标版本'` | ✅ README / AGENTS / AHA-Design / ReferenceGuide 均为 0.1.1 |
| 文档 / 技能 / 脚本检查 | `bin/Check{Docs,Skills,Scripts}.py` | ✅ 全过 |

`@since 0.1.0` 一类的**历史标注**、实测数据标题（如「实测覆盖率（0.1.0）」）与
`TODO.md` 的版本历史行**均未改动**——它们记录的是「哪一版的事实」，不是「当前版本是多少」。

---

## 四、关键教训

1. **「只改一处」的规范必须实测一遍再相信。** 原文那句「只需改根 POM」写得很笃定，实际会静默
   产出旧版本；判定依据不是读 POM，而是看反应堆的 `Building <模块> <版本>` 行与产物名。
2. **静默错误比失败更危险。** 版本号不一致时 Maven **不报错**（父子 POM 走 `relativePath` 解析，
   版本差异被容忍），所以「构建绿灯」不能证明版本号改对了——必须查产物名与 `--version` 输出。
3. **软件包的版本号要顺着链路验到最末端。** 根 POM → 子 POM 的 `<parent>` → 资源过滤 →
   `version.properties` → `AHA x.y.z`，中间任何一环断了都不报错，只有端到端看一眼才知道。
4. **文档里写「当前版本」的地方与历史标注必须分开处理。** 前者随版本走，后者是事实记录；
   一刀切替换会把「0.1.0 的实测值」改成假数据。
5. **发布物与发布记录要对得上账。** tag 叫 `0.1.0` 而文档写 `V0.1.0`，链接就会 404；
   `CHANGELOG` 的日期与实际发布提交的日期要对齐——两边一致才叫可追溯。
6. **能力属于哪个版本，看它合入的时间线。** 0.1.0 发布之后才合入 `dev` 的东西，
   不能写进 `[0.1.0]` 段；`git log` 的提交时间是唯一的判据。

---

## 五、涉及文件清单

**代码 / 构建定义**

- `pom.xml`、`aha-common/pom.xml`、`aha-extension-api/pom.xml`、`aha-core/pom.xml`、
  `aha-tool/pom.xml`、`aha-cli/pom.xml`、`aha-desktop/pom.xml` —— 版本号 `0.1.0` → `0.1.1`
- `aha-cli/src/main/java/com/acanx/module/aha/cli/AppVersion.java` —— `FALLBACK` → `AHA 0.1.1-dev`，
  并注明这是全仓唯一的版本字面量

**文档**

- `CHANGELOG.md` —— 新增 `[0.1.1]` 段、搬走误落的条目、修 `[0.1.0]` 的发布日与链接
- `Docs/DevSpec/ReleaseProcess.md` —— 版本号清单更正为 8 处 + `versions:set` + 实测教训；§4.1 示例改 `V0.1.1`
- `Docs/DevSpec/BuildSpec.md` —— 自动打 tag 小节的版本标注改为「0.1.1 起」
- `Docs/AHA/AHA-Design-V1.md` —— 目标版本、当前版本、两处 POM 示例
- `Docs/Guide/ReferenceGuide.md` —— 版本号说明改为「已发行版本 / dev 上版本 / 改版本号不止根 POM」
- `README.md`、`AGENTS.md` —— 当前版本
- `Docs/PLAN.md` —— §6.3 改为「0.1.0 已发布 / 0.1.1 待执行」、§8.2.2 结项
- `Docs/TODO.md` —— `G-01` 结项、新增 `G-06`
