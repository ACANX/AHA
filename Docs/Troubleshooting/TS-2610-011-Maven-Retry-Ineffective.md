# TS-2610-011 · CI「重试了 3 次却等于没重试」：jacoco 插件解析失败的真因

> 日期：2026-10-08
> 作者：@ACANX（与 AI 助手协作）
> 关联 PR：#15
> 关联记录：`Docs/Troubleshooting/TS-2610-012-Native-Tolerance-Step-Level.md`（同一轮 CI 里暴露的原生镜像「可选」修正）、
> `.github/actions/maven-run/action.yml`

## 1. 背景

PR #15 的 `Build` 第一条 Maven 调用直接失败：

```
[ERROR] Plugin org.jacoco:jacoco-maven-plugin:0.8.15 or one of its dependencies could not be resolved:
[ERROR]   Could not find artifact org.jacoco:jacoco-maven-plugin:jar:0.8.15 in central
```

第一反应是「版本号写错了」。但本地一直能过、而且这个版本号是从父 POM 的
`<jacoco.plugin.version>` 唯一来源来的，改它属于**改一个本来正确的东西**——所以先证伪。

## 2. 排障过程与修复链

### 2.1 三步证伪「版本写错」

| 核对 | 命令/依据 | 结果 |
|---|---|---|
| Central 上有没有这个版本 | 拉 `org/jacoco/jacoco-maven-plugin/maven-metadata.xml` | **有**：版本列表里含 `0.8.15` |
| 制品本身能不能取 | `curl -sI …/0.8.15/jacoco-maven-plugin-0.8.15.pom` | **HTTP 200** |
| 本地仓库有没有 | `ls ~/.m2/repository/org/jacoco/jacoco-maven-plugin/` | 有（`0.8.15`、`0.8.13`）——这解释了本地为何一直能过 |
| CI 那条命令本地复跑 | `./mvnw -B clean test -Djacoco.skip=true` | **BUILD SUCCESS**（桌面端 147 例通过） |

结论：**不是版本问题**，是**取不到**。

### 2.2 真因：负缓存 + 「伪重试」

`Build` 里的 Maven 调用统一走自建的 `./.github/actions/maven-run`，它的重试逻辑是：

```bash
# ① 清理失败标记（当时只清这一次）
find "${HOME}/.m2/repository" -name '*.lastUpdated' -delete
# ② 循环重试
for i in $(seq 1 "${MVN_ATTEMPTS}"); do
  bash -c "${MVN_COMMAND}" ...
done
```

问题在于 **Maven 的负缓存是「首次失败时写入」的**：

```
第 1 次：真的去 Central 取 → 失败 → 把失败写进 *.lastUpdated
第 2 次：命中负缓存 → 立刻失败（日志与第 1 次逐字相同）
第 3 次：同上
```

于是「重试了 3 次」**等于没有重试**——日志三次一模一样，正是这个形态的特征
（这也是本次排查最关键的一条判据：**重试若没改变任何前置条件，它的结果必然相同**）。

### 2.3 修复

1. 把清理抽成函数，**每一轮开跑前**都调用：

   ```bash
   clear_failure_cache() {
     find "${HOME}/.m2/repository" -name '*.lastUpdated' -delete 2>/dev/null || true
   }
   for i in $(seq 1 "${MVN_ATTEMPTS}"); do
     clear_failure_cache          # ← 关键：清理进循环体
     ...
   done
   ```

2. 顺带把**失败提示**按排查优先级重写（原来只有一句「请先看仓库/网络状况，再看依赖坐标」）：

   ```
   1) 该 artifact 在 Central 上是否存在（curl -sI <url> 看状态码）
      —— 每轮已清过负缓存，所以「存在却取不到」基本就是仓库侧问题；
   2) runner 的网络与代理；
   3) 最后才怀疑依赖坐标：坐标写错时反应堆匹配不上，也会报「中央仓库找不到」
   ```

   第 3 条不是凑数：本项目的模块 groupId 是 `com.acanx.module.aha`、父 POM 是
   `com.acanx.module`，**坐标写错时 Maven 同样报「在中央仓库找不到」**——
   这个坑同一轮里也踩过（见技能坑 4）。

## 3. 最终验证结果

- `./mvnw -B clean test -Djacoco.skip=true`（默认 profile，本机无 GraalVM）→
  **BUILD SUCCESS**；桌面端 **147 例**通过（1 例窗口冒烟默认跳过）。
- `maven-run/action.yml` 仍是合法 YAML；`bin/Check{Docs,Skills,Scripts}.py` 全绿。
- 该 action 的行为修正在 CI 上的最终确认，留给下一次 `Build` 运行。

## 4. 关键教训

1. **重试必须重建前置条件**：凡是「失败会留下痕迹」的操作，清理动作都要放进**循环体**，
   而不是循环之前。只清一次的重试，本质上是「一次真重试 + 两次读缓存」。
2. **三次同样的日志就是证据**：重试的日志若逐字相同，先怀疑「重试根本没生效」，
   再怀疑远端。这条判据比读失败信息本身更快定位。
3. **先证伪最容易的假设**：「版本号写错」用三条命令（metadata / HTTP 200 / 本地有）
   就排除了，避免了去改一个本来正确的版本号。
4. **错误提示要按排查优先级写**：把「最可能且最容易验证」的放在第一条，
   把「历史上也踩过但更隐蔽」的（坐标写错也报「找不到」）放在最后并说明原因。

## 5. 涉及文件清单

- `.github/actions/maven-run/action.yml`（清理进循环体 + 失败提示重写）
- `.agents/skills/java-app-graalvm-native-image-compile/references/isolation-and-ci.md`
  （新增「重试必须每轮重建前置条件」一条及其判据）
- `.agents/skills/java-app-graalvm-native-image-compile/SKILL.md`（迭代日志入账）

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- PR #15 的 `Build` 第一条 Maven 调用直接失败，原文：
  `Plugin org.jacoco:jacoco-maven-plugin:0.8.15 or one of its dependencies could not be resolved:` /
  `Could not find artifact org.jacoco:jacoco-maven-plugin:jar:0.8.15 in central`。
- **三步证伪「版本号写错」**：Central 的 `maven-metadata.xml` 版本列表里**含 `0.8.15`**；
  `curl -sI …/0.8.15/jacoco-maven-plugin-0.8.15.pom` → **HTTP 200**；
  本地仓库有 `0.8.15` 与 `0.8.13`；本地复跑同一条命令 → **BUILD SUCCESS**（桌面端 **147 例**通过）。
- **真因**：`maven-run` 里的清理 `find "${HOME}/.m2/repository" -name '*.lastUpdated' -delete`
  **只在循环之前执行一次**，而 Maven 的负缓存是**首次失败时写入**的 ⇒
  第 2、3 次命中负缓存**立刻失败**，**日志三次逐字相同**。
- 修复：把清理抽成 `clear_failure_cache()`，在**每一轮开跑前**调用（**清理进循环体**）；
  并把失败提示按排查优先级重写（① Central 上是否存在 → ② runner 的网络与代理 → ③ 最后才怀疑坐标）。
- 验证：本地 `clean test -Djacoco.skip=true` → **BUILD SUCCESS**，桌面端 **147 例**通过
  （1 例窗口冒烟默认跳过）；`maven-run/action.yml` 仍是合法 YAML；
  `Check{Docs,Skills,Scripts}` 全绿。
- **CI 上的最终确认当时未做**：档案原文写明「该 action 的行为修正在 CI 上的最终确认，
  留给下一次 `Build` 运行」。

### 机制 / 行为模型

- **Maven 的负缓存是「首次失败时写入」的**：一次失败会留下 `*.lastUpdated`，
  后续在该更新间隔内**直接失败、不再去取**。
  ⇒ **「重试」若不重建前置条件，就等于没重试**；「重试了 3 次」实际是
  「一次真重试 + 两次读缓存」。
- **判据（本次最快定位的一条）**：**重试的日志若逐字相同，先怀疑「重试根本没生效」，再怀疑远端**。
- 操作原则：凡「**失败会留下痕迹**」的操作，清理动作都要放进**循环体**，而不是循环之前。
- 坐标写错时 Maven **同样**报「在中央仓库找不到」（反应堆匹配不上就往外网找）
  ——同源坑见 `TS-2610-010` 的坑 2，故把它写进失败提示的最后一条并说明原因。

### 未定位 / 未复现

- **修复在真实 CI 环境的效果当时未验证**：档案明确把确认「留给下一次 `Build` 运行」。
- **未量化**负缓存的更新间隔（`updatePolicy` 默认值）对重试的影响：每轮清理让间隔不再成为变量，
  但没有测过「不同重试次数下的成功率」。
- 「三次日志逐字相同」这条判据**未机器化**：它只被写进技能参考文档，靠人眼看。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| CI 失败原文 | `Build` 日志 | `Could not find artifact org.jacoco:jacoco-maven-plugin:jar:0.8.15 in central` |
| ① 版本是否存在 | 拉 `org/jacoco/jacoco-maven-plugin/maven-metadata.xml` | 版本列表里**有 `0.8.15`** ✅ |
| ② 制品能否取到 | `curl -sI …/0.8.15/jacoco-maven-plugin-0.8.15.pom` | **HTTP 200** ✅ |
| ③ 本地仓库 | `ls ~/.m2/repository/org/jacoco/jacoco-maven-plugin/` | 有 `0.8.15`、`0.8.13`（解释了本地为何一直能过） |
| ④ 本地复跑同命令 | `-B clean test -Djacoco.skip=true` | **BUILD SUCCESS**；桌面端 **147 例**通过 |
| 伪重试的直接证据 | 三次失败日志比对 | **逐字相同** ⇒ 第 2、3 次命中负缓存、没有新的取件动作 |
| 修复前后置条件 | `action.yml` 的清理位置 | 修复前：循环**之前**只清一次；修复后：`clear_failure_cache()` 在**循环体内**每轮调用 |
| 修复后的配置合法性 | YAML 解析 + `bin/Check{Docs,Skills,Scripts}.py` | 合法 ✅ / 全绿 ✅ |

> **对照样本（规范要求）**：同一 artifact 的「本地能过」与「CI 取不到」并列，
> 加上「中央 200 / metadata 含该版本 / 本地有该版本」三条旁证，
> 共同把根因从「版本错」推到了「**取不到**」。

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 版本号写错（最初的假设） | metadata 含 `0.8.15` + 制品 HTTP **200** + 本地仓库有该版本（「2.1」三条） |
| 本地环境差异导致误判 | 本地复跑**同一条命令** → `BUILD SUCCESS`（「2.1」第 4 行） |
| 「CI 就是真取不到、与重试无关」 | 修复方向是**清理时机**而非网络；且三次日志逐字相同说明后续两次没做任何取件动作（「2.2」） |
| 「重试逻辑在跑就说明重试有效」 | 三次日志**逐字相同** ⇒ 后续两次命中负缓存（「2.2」） |
| 「原来的错误提示已经够用」 | 未涵盖「坐标写错也报『找不到』」这一历史坑，故按排查优先级重写（「2.3」） |

## 未决问题

**Q1 修复在 CI 上确实生效了吗？**

现状：**当时未确认**——档案把确认留给下一次 `Build` 运行。
阻塞点：需要一次真实的 CI 运行来比对「修复前后同一故障的重试形态」。

**Q2 重试次数与负缓存更新间隔的关系？**

现状：**未量化**。每轮清理使更新间隔不再成为变量，但没有测过不同 `MVN_ATTEMPTS` 下的成功率，
也没有依据说明「3 次」是否足够。

**Q3 「重试日志逐字相同 ⇒ 重试未生效」能机器化吗？**

现状：只写进
`.agents/skills/java-app-graalvm-native-image-compile/references/isolation-and-ci.md` 作为**人工判据**。
阻塞点：要机器判「逐字相同」需要在 action 里保存并比对上一轮日志，尚未评估。
