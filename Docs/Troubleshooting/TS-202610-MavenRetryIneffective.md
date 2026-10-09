# TS-202610-MavenRetryIneffective：CI「重试了 3 次却等于没重试」：jacoco 插件解析失败的真因

> 日期：2026-10-08
> 作者：@ACANX（与 AI 助手协作）
> 关联 PR：#15
> 关联记录：`Docs/Troubleshooting/TS-202610-NativeToleranceStepLevel.md`（同一轮 CI 里暴露的原生镜像「可选」修正）、
> `.github/actions/maven-run/action.yml`

## 1. 背景

PR #15 的 `Build` 第一条 Maven 调用直接失败：

```
[ERROR] Plugin org.jacoco:jacoco-maven-plugin:0.8.15 or one of its dependencies could not be resolved:
[ERROR]   Could not find artifact org.jacoco:jacoco-maven-plugin:jar:0.8.15 in central
```

第一反应是「版本号写错了」。但本地一直能过、而且这个版本号是从父 POM 的
`<jacoco.version>` 唯一来源来的，改它属于**改一个本来正确的东西**——所以先证伪。

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
