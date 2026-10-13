# TS-2610-010 · 原生模块与工作流落地——5 个「不报错」的坑

> 日期：2026-10-08
> 作者：@ACANX
> 关联 PR：`feat/version-0.1.1`（新增 `aha-desktop-native` 试验模块与 `DesktopNative.yml`）
> 关联记录：`Docs/Design/DesktopNativeDesign.md`、`TODO.md` `N-01`~`N-07`、`Docs/Dbsx.txt`

## 1. 背景

用户要求：新建**试验性**模块 `aha-desktop-native`，把 `aha-desktop` 的 JVM 产物再编成
GraalVM native-image 二进制；并单独一条工作流，做到「仅 dev 合并即出包、版本按
`a.b.c.PPPPP`、可独立发版、失败不影响既有流程」；另加 JDK 27 编译分支用于对比
Leyden AOT / 原始类型 / GC 对启动与内存的影响（先只开 Windows，为 JDK 29 铺路）。

本轮把模块、工作流、JDK 双轴与全部文档落地，并**在本机把能验证的部分全验证了一遍**。

## 2. 排障过程与修复链（五个坑，全是「不报错的那种」）

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | `-pl aha-desktop-native -am` 的反应堆里**只有它自己** | profile 加入的模块**不会**被 `-am` 带上游（Maven 3.9.11 与 4 行为一致，实测对照过普通模块 `aha-cli` 是正常的） | 显式写成 `-pl aha-desktop-native,aha-desktop -am`；把这条写进 POM、设计文档与本日志 |
| 2 | 「在中央仓库找不到 `aha-desktop`」 | 各模块**覆盖**了父 POM 的 groupId：父是 `com.acanx.module`，模块是 `com.acanx.module.aha`。坐标写错时反应堆匹配不上，于是它去外网找了个不存在的坐标 | 依赖坐标改为 `com.acanx.module.aha:aha-desktop`，并把这条坑写进 POM 注释 |
| 3 | 修完仍失败，日志变成「failure was cached in the local repository」 | 第 2 次失败被写进本地仓库的**负缓存**，后续运行不再重试 | 清 `*.lastUpdated` 后复跑（CI 侧早有 `maven-run` action 做这件事，这次是本地踩到） |
| 4 | `lib/` 里多了两个 302 / 306 字节的 jar | 带分类器的 OpenJFX 工件与无分类器工件**共用同一份 POM**，那份 POM 声明的是无分类器依赖，于是 0 KB 空壳被传递进来；而且 `copy-dependencies` **不删**已不在依赖集里的旧文件，所以「依赖树已经干净、目录还不干净」 | ① 在 `javafx-controls` **和** `javafx-graphics` 上都排（只排前者不够，实测仍是 20 个 jar）；② 在 `initialize` 阶段加一次只清 `lib/` 的 `clean`（`excludeDefaultDirectories=true`，不会误删 `target/`）；③ 用「先塞一个假 jar 再跑」验证清理真的生效（21 → 18 个） |
| 5 | 工作流里的版本号会算成空的 | `ElementTree.findtext('version')` 取不到：POM 标签**带命名空间**（`{http://maven.apache.org/POM/4.0.0}version`），而它**不报错**，只是返回 None | 改成「第一个以 `version` 结尾的直接子节点」；本机用 INPUT_PR=21 / 7 / 空 三种输入实测出 `0.1.1.00021`、`0.1.1.00007`、`0.1.1.00000` |

另外两个小坑（顺手记下）：

- **XML 注释里不能出现 `--`**：我在 POM 注释里写了 `native-image --version`，Maven 直接报
  `String '--' not allowed in comment`；`--gc=` 同理。改写措辞即可（这类错误至少是**立刻报错**的）。
- **根 POM 已经有 `<profiles>`**：另起一个 `<profiles>` 块会被 Maven 判成
  `Duplicated tag: 'profiles'`；新 profile 要并进既有块里。

## 3. 最终验证结果

| 项 | 结果 |
|---|---|
| 默认构建不受影响 | 无 profile 时反应堆里**没有** `aha-desktop-native`；`-pl aha-cli,aha-desktop -am test-compile` 通过 |
| 模块进反应堆 | 带 `-Pdesktop-native` 时反应堆为 root + 5 个上游 + native（7 个） |
| 管线跑通（不真编） | `-Dnative.skip=true` → 依赖拷贝 18 个 jar（**恰好 3 个带分类器的 OpenJFX**）、README 版本过滤成功、zip 打包成功、文件权限正确（二进制 755 / 文档 644） |
| JDK 双轴 | 默认产出 `aha-desktop-native-0.1.1-jdk25.zip`；`-Pnative-jdk27` 产出 `…-jdk27.zip` 且包内附的是 JDK 27 参数文件 |
| 版本号规则 | 本地实测 `0.1.1` + PR21/7/空 → `0.1.1.00021` / `0.1.1.00007` / `0.1.1.00000` |
| Tag 隔离 | `native-v0.1.1.00021` 不匹配 `Release.yml` 的 `V*` / `v*`（首字符是 `n`），不会触发正式发版 |
| 工作流语法 | `yaml.safe_load` 通过；作业 `native-version` / `native-build`（4 条腿）/ `native-publish` |
| 仓库检查 | `bin/Check{Docs,Skills,Scripts}.py` 全绿（含「源码未被 .gitignore 吃掉」与「无残留冲突标记」） |
| **未验证（诚实说明）** | **真正的 native-image 编译**没在本机跑过：WSL 里没有 GraalVM，也没有 Linux 的 C 工具链。首跑由用户或 CI 给出答案——这也正是把失败隔离做扎实的原因 |

## 4. 关键教训

1. **「找不到依赖」先怀疑坐标，而不是网络**：反应堆匹配不上时，Maven 会去外网找一个
   不存在的坐标，报出的错误是「在中央仓库找不到」——听起来像网络问题，实际是坐标写错。
2. **`-am` 对 profile 加入的模块不带上游**：这是行为事实，不是配置疏漏；
   涉及时就显式列出上游模块，并把原因写进注释（否则下一个人会以为写多了）。
3. **构建脚本里的解析逻辑必须有输出**：`findtext` 命名空间取不到值却不报错，
   版本号会静默变空。凡是「解析出来的值」，都要在日志里打出来（本次就是靠本地实测发现的）。
4. **拷贝型步骤要么清目录，要么别信目录**：`copy-dependencies` 不删旧文件，
   于是「依赖树干净、目录不干净」；用「塞假文件再跑」这种反向验证比读文档可靠。
5. **隔离不是「加个 if」**：四条隔离（反应堆 / 检查 / tag 命名空间 / 失败传播）各自要能被
   单独指出来，否则「实验不影响正式流程」只是一句愿望。
6. **没验证的东西要写在最显眼处**：native-image 编译本身未在本机验证过，
   这一点写进了设计文档、TODO 与本日志——比让它睡在「看起来都做完了」里安全。

## 5. 涉及文件清单

**新增模块**

- `aha-desktop-native/pom.xml`（依赖拷贝 + classpath 抽取 + README 过滤 + native-image +
  打包；JDK 25/27 两条轴的 profile；工作目录清理）
- `aha-desktop-native/src/native/native-image-args.txt`（**JDK 25 基线的全部开关**）
- `aha-desktop-native/src/native/native-image-args-jdk27.txt`（JDK 27 实验分支开关 + 实验位）
- `aha-desktop-native/src/native/README.txt`（包内说明的模板，带版本号过滤）
- `aha-desktop-native/src/assembly/dist-native.xml`（打包描述符；二进制 755 / 文档 644）

**工作流**

- `.github/workflows/DesktopNative.yml`（版本推导 + 4 条腿 + 产物自证 + 预发行版发布）

**父 POM**

- `pom.xml`（`desktop-native` profile；`exec` / `dependency` / `resources` / `clean` 的插件版本；
  JDK 轴与产物名相关属性在模块内）

**文档**

- `Docs/Design/DesktopNativeDesign.md`（新增：目标与非目标、隔离的四个面、构建管线、
  JDK 25/27 对比实验与测量口径、风险清单 R1~R7、迭代指南、后续路线 N-1~N-7）
- `Docs/TODO.md`（`N-01`~`N-07` 与变更日志 v0.28.0）
- `Docs/Dbsx.txt`（需求、计划、已知风险、本机试法）
- `Docs/Design/ArchitectureOverview.md`、`Docs/AHA/AHA-Design-V1.md`（模块表与文档索引）
- `CHANGELOG.md`（新增条目）

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- **坑 1**：`-pl aha-desktop-native -am` 的反应堆里**只有它自己**（Maven 3.9.11 与 4 行为一致；
  用普通模块 `aha-cli` 做过对照，正常带上上游）。
- **坑 2**：「在中央仓库找不到 `aha-desktop`」——各模块**覆盖**了父 POM 的 groupId
  （父为 `com.acanx.module`，模块为 `com.acanx.module.aha`）。
- **坑 3**：坐标改对后仍失败，日志变成
  `failure was cached in the local repository`（**负缓存**）。
- **坑 4**：`lib/` 里多了两个 **302 / 306 字节**的 jar；只排 `javafx-controls` **不够**
  （实测依赖目录仍是 **20 个 jar**），要在 `javafx-controls` **和** `javafx-graphics` 上都排；
  清理用「先塞一个假 jar 再跑」反向验证 → **21 → 18** 个。
- **坑 5**：`ElementTree.findtext('version')` 取不到值：POM 标签**带命名空间**
  （`{http://maven.apache.org/POM/4.0.0}version`），而且它**不报错、只返回 `None`** → 版本号算成空。
  本机用 `INPUT_PR` = `21` / `7` / 空 三种输入实测出 **`0.1.1.00021` / `0.1.1.00007` / `0.1.1.00000`**。
- 两个「立刻报错」的小坑：XML 注释里不能出现 `--`（`native-image --version` → `String '--' not allowed in comment`，
  `--gc=` 同理）；根 POM 已有 `<profiles>`，另起一块会判 `Duplicated tag: 'profiles'`。
- 验证结果：无 profile 时反应堆里**没有** `aha-desktop-native`；
  `-pl aha-cli,aha-desktop -am test-compile` 通过；带 `-Pdesktop-native` 时反应堆为
  root + 5 个上游 + native（**7 个**）；`-Dnative.skip=true` 下依赖拷贝 **18 个 jar**
  （**恰好 3 个带分类器的 OpenJFX**）、README 版本过滤成功、zip 打包成功、
  文件权限正确（二进制 **755** / 文档 **644**）；JDK 双轴产出 `…-jdk25.zip` 与 `…-jdk27.zip`；
  tag `native-v0.1.1.00021` 不匹配 `Release.yml` 的 `V*` / `v*`（首字符是 `n`）；
  工作流 `yaml.safe_load` 通过，作业为 `native-version` / `native-build`（4 条腿）/ `native-publish`；
  `Check{Docs,Skills,Scripts}` 全绿。
- **未验证（档案自己标注）**：**真正的 native-image 编译没在本机跑过**——
  WSL 里没有 GraalVM，也没有 Linux 的 C 工具链。

### 机制 / 行为模型

- **`-am` 对「profile 加入的模块」不带上游**：这是**行为事实**，不是配置疏漏。
  依据：实测 + 普通模块对照。
- **反应堆匹配不上坐标时，Maven 会去外网找一个不存在的坐标**，于是报出「在中央仓库找不到」
  ——听起来像网络问题，**实际是坐标写错**。
- **负缓存**（与 `TS-2610-004` 同源）：失败被写进本地仓库，后续运行**不再重试**，
  直到清 `*.lastUpdated` / 强制更新或间隔过期。
- **`copy-dependencies` 不删已不在依赖集里的旧文件** ⇒ 「**依赖树**干净」不等于「**目录**干净」。
- **`findtext('version')` 对带命名空间的标签静默返回 `None`** ⇒ 凡「解析出来的值」都必须在日志里打出来。
- **隔离不是「加个 if」**：必须能逐个指出来——反应堆 / 检查 / tag 命名空间 / 失败传播，
  四个面各自独立成立，「实验不影响正式流程」才不是愿望。

### 未定位 / 未复现

- **真正的 native-image 编译未在本机验证**：无 GraalVM、无 Linux C 工具链，
  首跑结论当时**未知**，只能由用户或 CI 给出。
- 未在本机验证 CI 上 4 条腿的**实际产物**：只验证了 `-Dnative.skip=true` 下的管线骨架
  （依赖拷贝 / 资源过滤 / 打包 / 权限）与本地 profile 切换。
- `native-publish` 的**预发行版发布行为**未实测。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 坑 1：上游没被带上 | `-pl aha-desktop-native -am` 的反应堆 | 反应堆里**只有它自己**；对照普通模块 `aha-cli` 正常 |
| 坑 2：找不到依赖的原文 | 构建日志 | 「在中央仓库找不到 `aha-desktop`」——实为 groupId 被模块覆盖（`com.acanx.module` → `com.acanx.module.aha`） |
| 坑 3：负缓存原文 | 第二次构建日志 | `failure was cached in the local repository` |
| 坑 4：空壳 jar 的体积 | `ls -l lib/` | **302 / 306 字节**两个 jar |
| 坑 4：只排一处不够 | 依 `dependency:copy-dependencies` 后清点 | **20 个 jar**（排 `javafx-controls` 后）→ 两处都排 + 清 `lib/` |
| 坑 4：清理生效的反向验证 | 先塞一个假 jar 再跑 | **21 → 18** 个 ✅ |
| 坑 5：版本号三种输入 | 本机 Python 解析 + 三种 `INPUT_PR` | `0.1.1.00021` / `0.1.1.00007` / `0.1.1.00000` |
| 默认构建不受影响 | 无 profile 的反应堆 | **没有** `aha-desktop-native`；`-pl aha-cli,aha-desktop -am test-compile` 通过 |
| 模块进反应堆 | 带 `-Pdesktop-native` | root + 5 个上游 + native = **7 个** |
| 管线跑通（不真编） | `-Dnative.skip=true` | 依赖 **18 个 jar**（恰好 **3 个**带分类器 OpenJFX）；README 版本过滤成功；zip 打包成功 |
| 文件权限 | 打包结果 | 二进制 **755** / 文档 **644** ✅ |
| JDK 双轴 | 两种 profile | `aha-desktop-native-0.1.1-jdk25.zip` / `…-jdk27.zip`（包内附对应 JDK 的参数文件） |
| Tag 隔离 | 比对 `Release.yml` 的触发式样 | `native-v0.1.1.00021` 首字符是 `n`，**不匹配** `V*` / `v*` |
| 工作流语法 | `yaml.safe_load` | 通过；作业 `native-version` / `native-build`（4 腿）/ `native-publish` |
| 仓库检查 | `bin/Check{Docs,Skills,Scripts}.py` | 全绿（含「源码未被 `.gitignore` 吃掉」「无残留冲突标记」） |
| **未验证项** | — | native-image 编译**未在本机执行**（无 GraalVM / 无 Linux C 工具链） |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「找不到依赖是网络问题」 | 实为**坐标写错**（模块覆盖了父 POM 的 groupId），改正后即通（坑 2） |
| 「`-am` 会带上 profile 模块的上游」 | 实测反应堆里只有 native 自己；用普通模块 `aha-cli` 作对照（坑 1） |
| 「第二次失败是真的依赖问题」 | 日志为 `failure was cached in the local repository`（负缓存，坑 3） |
| 「只排 `javafx-controls` 就够了」 | 实测依赖目录仍 **20 个 jar**，需两处都排（坑 4） |
| 「依赖树干净 ⇒ 目录干净」 | `copy-dependencies` 不删旧文件；塞假 jar 反向验证 **21 → 18**（坑 4） |
| 「版本号解析失败会报错」 | `findtext` 对带命名空间标签返回 `None` 且**不报错** ⇒ 版本号静默变空（坑 5） |
| 「实验模块会污染正式流程」 | 四项隔离（反应堆 / 检查 / tag 命名空间 / 失败传播）逐项验证（「三、」） |

## 未决问题

**Q1 真正的 native-image 编译能通过吗？**

现状：**本机无法验证**（WSL 无 GraalVM、无 Linux C 工具链）——首跑由用户或 CI 给出答案，
这也是把失败隔离做扎实的原因。
阻塞点：需要 GraalVM + Linux C 工具链；**由 `DesktopNative.yml` 承担**。

**Q2 产物「能启动」到什么程度算验过？**

现状：管线、依赖集合、资源过滤、打包、文件权限均已验证；**二进制是否真能启动未验证**（与 Q1 同一阻塞点）。

**Q3 `N-01`~`N-07` 里的 JDK 27 对比实验何时产出数据？**

现状：只开了 Windows 腿（为 JDK 29 铺路），测量口径写进设计文档，**未产出测量数据**。
阻塞点：需要真机 / CI 跑通后再采集启动与内存指标。

**Q4 本地也做一次「清 `*.lastUpdated`」吗？**

现状：CI 侧有 `maven-run` action 负责，本地只能手工 `find … -delete`
（`TS-2610-004` 给出了命令）。是否提供本地脚本，**未定**。
