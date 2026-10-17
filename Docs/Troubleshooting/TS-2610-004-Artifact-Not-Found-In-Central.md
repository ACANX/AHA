# TS-2610-004 · CI 排障复盘：`Could not find artifact ... in central`，而该 artifact 确实存在

> 日期：2026-10-07
> 作者：@ACANX（与 AI 协作）
> 关联 PR：[#8](https://github.com/ACANX/AHA/pull/8)（该轮 CI 失败）
> 关联记录：[BuildSpec.md](../DevSpec/BuildSpec.md) §8.1、[TODO.md](../TODO.md) `F-13`、`.github/actions/maven-run/action.yml`

---

## 一、背景

CI 在 JaCoCo 的 `prepare-agent` 上失败，报的是插件自身的依赖解析不出来：

```
[ERROR] Failed to execute goal org.jacoco:jacoco-maven-plugin:0.8.15:prepare-agent (prepare-agent) on project aha:
Execution prepare-agent of goal org.jacoco:jacoco-maven-plugin:0.8.15:prepare-agent failed:
Plugin org.jacoco:jacoco-maven-plugin:0.8.15 or one of its dependencies could not be resolved:
[ERROR]     Could not find artifact org.slf4j:slf4j-api:jar:1.7.36 in central (https://repo.maven.apache.org/maven2)
[ERROR]     Could not find artifact org.ow2.asm:asm-commons:jar:9.10.1 in central (https://repo.maven.apache.org/maven2)
[ERROR]     Could not find artifact org.ow2.asm:asm-tree:jar:9.10.1 in central (https://repo.maven.apache.org/maven2)
```

表面上像「依赖写错了」，但注意三点，它们把这三种解释逐个排除掉：

1. 这三个版本**不是**本项目 POM 写的——`slf4j-api` 在本项目被管到 2.0.20（`<slf4j.version>`），
   报错里的 1.7.36 是 JaCoCo 自己声明的；项目里根本没有 ASM 的版本声明。
2. 失败发生在**插件依赖**解析上，不是项目依赖。
3. 本地 `./mvnw clean verify` 完全正常。

---

## 二、排障过程与修复链

### 2.1 先问 Central：这三个 artifact 到底在不在

```
$ curl -s -o /dev/null -w '%{http_code}\n' \
    https://repo.maven.apache.org/maven2/org/ow2/asm/asm-commons/9.10.1/asm-commons-9.10.1.pom
200
```

`asm-commons:9.10.1`、`asm-tree:9.10.1`、`slf4j-api:1.7.36` **全部 200**，而且 9.10.1 正是
`asm-commons` 的**最新版**。⇒ 不是「版本不存在」。

### 2.2 再排除「项目配置把解析引偏了」

- 根 POM 无 `<repositories>` / `<pluginRepositories>`；
- `.mvn/` 下只有 `wrapper/`，**没有** `settings.xml` / `maven.config`；
- 全仓无镜像或私服地址（`aliyun` / `nexus` / `mirrorOf` 全无命中，仅 IDEA 的本地配置里有）；
- 四个工作流**都没有**启用 `actions/setup-java` 的 `cache:`，runner 的 `~/.m2` 不会被 Actions 缓存恢复。

⇒ 不是「解析源被改」。剩下两种可能：**本地仓库负缓存**，或**当次就没要下来**。

### 2.3 复现一：负缓存是**另一种**消息（因此排除）

在本地制造「artifact 不存在 + `*.lastUpdated` 失败标记」的状态，再跑同一个 goal：

```
[ERROR] 	org.ow2.asm:asm-commons:jar:9.10.1 was not found in https://repo.maven.apache.org/maven2
	during a previous attempt. This failure was cached in the local repository and resolution
	is not reattempted until the update interval of central has elapsed or updates are forced
```

关键：**消息形态不同**——负缓存会把话说清「上次就失败了、这次不再试」。CI 那条没有这些字，
所以 CI 不是负缓存。

### 2.4 复现二：真正拿不到时，消息形态与 CI 一致（因此定性）

```
$ ./mvnw -B -N dependency:get -Dartifact=org.ow2.asm:asm-commons:9.99.9
[ERROR] ... Could not find artifact org.ow2.asm:asm-commons:jar:9.99.9 in central (https://repo.maven.apache.org/maven2)
```

与 CI 日志**同一形态**。⇒ CI 那次是**当次就没要下来**——属于仓库侧 / 网络侧的瞬时故障，
不是版本错、不是项目少配仓库。

> 两条消息必须分清，因为处理方式不同：
>
> | 消息 | 含义 | 处理 |
> | ---- | ---- | ---- |
> | `... was not found in <url> during a previous attempt. This failure was **cached** in the local repository ...` | 本地仓库记了失败标记，**更新间隔内不会重试** | 清掉 `*.lastUpdated`（或 `-U`） |
> | `Could not find artifact ... in central (<url>)` | 当次解析就失败（要不到） | 重试；并核实版本是否真实存在 |

### 2.5 事后取证（GitHub API）：确认是瞬时，且定位到具体哪条腿

推送后可以查到 9900e55 这一次提交的检查结果，**同一个提交跑了两轮**：

| 腿 | push 运行 | PR 运行 |
| ---- | ---- | ---- |
| `build (ubuntu-latest, system)` | **success** | **failure** |
| `build (ubuntu-latest, wrapper)` | success | success |
| `build (windows-latest, wrapper)` | success | success |
| `build (macos-latest, wrapper)` | success | success |

同一个提交、同一条腿，一轮成功一轮失败——**代码不可能这样**，这就是瞬时故障的铁证。
失败那条腿只跑了 **0.2 分钟**（成功的腿 0.7–1.1 分钟），说明它在依赖解析处就断了。

顺带确认了另一件事：该 PR 的目标分支是 `dev`，而 `Gate` / `Compat` 只在
`pull_request → main / release/**` 时触发，所以这次**只跑了 Build 的快检查**——
与检查分层的设计一致（慢检查是「合入 main 前」的卡点）。

### 2.6 修法：把「清标记 + 定向重试」做成单一来源

新增复合 action `.github/actions/maven-run/action.yml`，四处工作流的**所有** Maven 调用都走它：

```yaml
- uses: ./.github/actions/maven-run
  with:
    command: ./mvnw clean verify
```

它做两件事：

1. 先 `find "${HOME}/.m2/repository" -name '*.lastUpdated' -delete`（覆盖负缓存那条路）；
2. 失败时**先判断这次失败值不值得重试**：只有命中「依赖解析不到 / 传输中断 / 远端 5xx /
   本地负缓存」这几类特征时才重试（最多 3 次、间隔 20 秒），其余**立刻失败**。

**为什么不做无差别重试**：编译错误、测试失败重跑三遍，只会把反馈时间拉长三倍——
而「改一次就跑」的快反馈正是这个项目刻意保护的东西。定向重试两头都要：瞬时故障能自动
恢复，真失败不浪费时间。

**为什么要重试而不是「手动重跑」**：`Gate` 是分支保护的必需检查，一次与代码无关的
瞬时故障就会把 PR 卡红，而人重跑一次的成本远高于机器自动重试。

**实现上的一个坑**：最初把 `${{ inputs.command }}` 直接拼进脚本体，
`if ${{ inputs.command }}; then` ——命令里一旦有引号就会被 shell 的**词分割**拆坏
（实测 `bash -c "echo a b"` 会被拆成 `bash` `-c` `"echo` `a` `b`）。
改成用 `env:` 传参 + `bash -c "${MVN_COMMAND}"`，既避开词分割，也避开把输入拼进脚本。

---

## 三、最终验证结果

| 验证 | 方法 | 结果 |
| ---- | ---- | ---- |
| 三个 artifact 在 Central 存在 | `curl -o /dev/null -w '%{http_code}'` | 全部 **200** |
| 项目未改解析源 | 查 `<repositories>` / `.mvn/` / 镜像关键字 / `cache:` | 全部为空 ✅ |
| 负缓存消息形态 | 本地造标记后跑 goal | 得到 `... cached in the local repository ...`（与 CI 不同） |
| 当次失败消息形态 | 本地跑一个不存在的版本 | 得到 `Could not find artifact ... in central (<url>)`（与 CI **一致**） |
| 重试脚本逻辑 | 抽出 action 的脚本、注入假命令跑 5 种情形 | ①成功→1 次 ②瞬时故障×2 后成功→3 次成功 ③一直瞬时故障→3 次后失败 ④**真失败（编译错）→只跑 1 次** ⑤`attempts=1`→不重试，**全过** |
| 端到端 | 用真实命令 `./mvnw -B -N ... prepare-agent` 走该脚本 | `BUILD SUCCESS`；本地 4 个失败标记被清为 **0** |
| YAML 合法性 | 解析 `.github/**/*.yml` + 校验 composite 结构（`runs.using` / 每步 `shell` / 必填输入） | 全部通过 |

---

## 四、关键教训

1. **「Could not find artifact X in central」不等于「X 不存在」**。先花 10 秒 `curl` 一下
   Central；能 200 就不是版本问题，别再翻 POM。
2. **Maven 的两条「找不到」消息必须分清**：带 `cached in the local repository` 的是负缓存
   （清标记 / `-U`），不带的是当次失败（重试 / 查网络）。看错方向就会修错地方。
3. **必需检查必须对「与代码无关的瞬时故障」有容忍度**。门禁卡人是有价值的，
   因为网络抖动卡人不是——重试是最便宜的容忍方式。
4. **别把用户输入拼进脚本体**。`if ${{ inputs.command }}` 这种写法遇到引号就被词分割拆坏；
   传参走 `env:`、执行走 `bash -c "${VAR}"`，是复合 action 里的正确姿势。
5. **结论要靠「最小复现实验」拿到，不能靠记忆**。本次真正定性的那一步，是分别造出
   「负缓存」与「真拿不到」两种状态，比对消息形态——而不是回忆「Maven 大概会怎么报」。
6. **不要无差别重试**。重试的边界应该由「失败的性质」决定，而不是由「命令是否失败」决定：
   与代码无关的失败值得重试，代码问题重试只是把红灯往后拖。
7. 顺带发现一处**遗留项**：`.github/**/*.yml` 目前没有任何本地检查
   （`bin/CheckScripts.py` 只覆盖 `.bat`/`.cmd`/`.sh`/`.py`），语法错了只能等 GitHub 判。
   已登记为 `TODO.md` 的 `F-14` 待决策。

---

## 五、涉及文件清单

| 文件 | 改动 |
| ---- | ---- |
| `.github/actions/maven-run/action.yml` | **新增**：清 `*.lastUpdated` + 按失败性质定向重试（最多 3 次），`env` 传参 + `bash -c` 执行 |
| `.github/workflows/Build.yml` | 两条测试腿改走复合 action |
| `.github/workflows/Gate.yml` | `clean verify` 与 `pmd:cpd` 改走复合 action（并把「判定重复率」拆成独立步骤，只让 Maven 那步享受重试） |
| `.github/workflows/Compat.yml` | 版本断言与 `verify` 拆开；`verify` 走复合 action |
| `.github/workflows/Release.yml` | `Build and verify` 走复合 action |
| [BuildSpec.md](../DevSpec/BuildSpec.md) | §8.1 新增「CI 必须容忍仓库侧瞬时失败（强制）」；§8 的改动范围补 `.github/actions/` |
| [AHA-Design-V1.md](../AHA/AHA-Design-V1.md) | 附录 A 目录树补齐 `.github/workflows/`（原先只列了 Build 与 Release）并加 `actions/` |
| [TODO.md](../TODO.md) | 新增 `F-13`（✅ 已修）与 `F-14`（⏸ 待决策） |
| `CHANGELOG.md` | 记录本次修复 |

---

## 附：自查（可粘贴）

遇到「解析不到依赖」时按这个顺序走，基本能一次定性：

```
# 1) 这个版本真的存在吗（能 200 就排除「写错版本」）
curl -s -o /dev/null -w '%{http_code}\n' \
  https://repo.maven.apache.org/maven2/<groupId 斜杠化>/<artifactId>/<version>/<artifactId>-<version>.pom

# 2) 本地是不是记了失败标记（负缓存）
find ~/.m2/repository -name '*.lastUpdated' | head

# 3) 清掉标记并强制重新尝试
./mvnw -B -U <goal>
```

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「一、二、…」编号冲突。 -->

## 结论分层

### 已观测事实

- CI 在 JaCoCo 的 `prepare-agent` 上失败，报的是**插件自身依赖**解析不出来：
  `Could not find artifact org.slf4j:slf4j-api:jar:1.7.36 in central (…)`、
  `org.ow2.asm:asm-commons:jar:9.10.1`、`org.ow2.asm:asm-tree:jar:9.10.1`（三条并列）。
- 这三个版本**不是本项目声明的**：`slf4j-api` 在本项目被管到 `2.0.20`（`<slf4j.version>`），
  项目里根本没有 ASM 的版本声明。
- 直接问 Central：三个 artifact 的 `.pom` **全部 `200`**，且 `9.10.1` 正是 `asm-commons` 的**最新版**。
- 解析源未被改：根 POM 无 `<repositories>` / `<pluginRepositories>`；`.mvn/` 下只有 `wrapper/`，
  无 `settings.xml` / `maven.config`；全仓无镜像或私服地址（`aliyun` / `nexus` / `mirrorOf` 全无命中）；
  四个工作流**都没有**启用 `actions/setup-java` 的 `cache:`。
- **两种「找不到」的消息形态都实测到了**：
  - 负缓存 → `… was not found in <url> **during a previous attempt. This failure was cached in the local repository** …`；
  - 当次要不到 → `Could not find artifact … in central (<url>)`（与 CI 日志**同一形态**）。
- 事后取证：同一个提交 `9900e55` **跑了两轮**——`build (ubuntu-latest, system)` 在 push 运行 **success**、
  在 PR 运行 **failure**，其余三条腿两轮都 success；失败那条腿只跑了 **0.2 分钟**
  （成功腿 0.7–1.1 分钟）。
- 重试脚本注入假命令的 **5 种情形**全过：①成功→1 次 ②瞬时故障×2 后成功→3 次成功
  ③一直瞬时故障→3 次后失败 ④**真失败（编译错）→只跑 1 次** ⑤`attempts=1`→不重试。
- 端到端：真实命令 `-B -N … prepare-agent` 走该脚本 → `BUILD SUCCESS`；
  本地 4 个 `*.lastUpdated` 失败标记被清为 **0**。

### 机制 / 行为模型

- **Maven 有两条「找不到」消息，性质完全不同，处理方式也不同**：

  | 消息 | 含义 | 处理 |
  | --- | --- | --- |
  | `… during a previous attempt. This failure was **cached** in the local repository …` | 本地记了失败标记，更新间隔内**不会重试** | 清 `*.lastUpdated`（或 `-U`） |
  | `Could not find artifact … in central (<url>)` | **当次**解析就失败（要不到） | 重试；并核实版本是否真实存在 |

  依据：分别**造出**这两种状态后比对消息形态（「二、2.3」「二、2.4」）。
- CI 那次是**仓库侧 / 网络侧的瞬时故障**。判据有二：同一提交、同一条腿**一轮成功一轮失败**
  （代码不可能这样）；失败发生在 **0.2 分钟**内的依赖解析阶段。
- 复合 action 的两条设计原则（本档案的修法）：
  **先删 `*.lastUpdated`**——覆盖负缓存那条路；
  **按失败性质定向重试**——只有命中「依赖解析不到 / 传输中断 / 远端 5xx / 本地负缓存」才重试
  （最多 3 次、间隔 20 秒），其余立刻失败。因为**无差别重试会把编译错、测试失败的反馈时间拉长三倍**。
- **不要把用户输入拼进脚本体**：`if ${{ inputs.command }}` 遇到引号会被 shell **词分割**拆坏
  （实测 `bash -c "echo a b"` 被拆成 `bash` `-c` `"echo` `a` `b``）；
  正确姿势是 `env:` 传参 + `bash -c "${MVN_COMMAND}"`。

### 未定位 / 未复现

- **未定位 CI 那次瞬时故障的具体上游原因**：档案只证明「**当次要不到**」（消息形态与本地复现一致），
  没有证明「**为什么**要不到」——没有拿到仓库侧的 5xx / 超时记录。
- 未复现「Actions 缓存恢复 `~/.m2` 导致污染」这条路径，因为四个工作流**都没开 `cache:`**，该路径不存在。
- `.github/**/*.yml` 至今**没有本地检查**（`bin/CheckScripts.py` 只覆盖 `.bat` / `.cmd` / `.sh` / `.py`），
  语法错了只能等 GitHub 判；已登记 `F-14`，**待决策**。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| CI 失败原文 | Actions 日志 | `[ERROR] Failed to execute goal org.jacoco:jacoco-maven-plugin:0.8.15:prepare-agent … or one of its dependencies could not be resolved` |
| 三个 artifact 是否存在 | `curl -s -o /dev/null -w '%{http_code}\n' https://repo.maven.apache.org/maven2/org/ow2/asm/asm-commons/9.10.1/asm-commons-9.10.1.pom` | **`200`**（另两个同为 200；`9.10.1` 是该 artifact 最新版） |
| 负缓存的消息形态（**对照样本 A**） | 本地造 `*.lastUpdated` 后跑同一 goal | `… was not found in <url> during a previous attempt. This failure was cached in the local repository and resolution is not reattempted until the update interval of central has elapsed or updates are forced` |
| 当次失败的消息形态（**对照样本 B，与 CI 一致**） | `-B -N dependency:get -Dartifact=org.ow2.asm:asm-commons:9.99.9` | `[ERROR] … Could not find artifact org.ow2.asm:asm-commons:jar:9.99.9 in central (https://repo.maven.apache.org/maven2)` |
| 同一提交两轮的差异（**决定性**） | GitHub API 查 `9900e55` 的检查结果 | `build (ubuntu-latest, system)`：push **success** / PR **failure**；其余三条腿两轮 success |
| 失败腿耗时（**佐证：断在解析阶段**） | 同上 | 失败腿 **0.2 分钟**；成功腿 0.7–1.1 分钟 |
| 重试脚本逻辑 | 抽出 action 脚本 + 注入假命令跑 5 种情形 | ①1 次 ②3 次成功 ③3 次后失败 ④**真失败只跑 1 次** ⑤`attempts=1` 不重试——全过 |
| 端到端与标记清理 | 真实 `prepare-agent` 走该脚本 | `BUILD SUCCESS`；本地 4 个失败标记 → **0** |
| 工作流语法 | 解析 `.github/**/*.yml` + 校验 composite 结构 | 全部通过 |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 版本写错 / 该 artifact 不存在 | `curl` Central 三个 artifact **全部 200**，且 `9.10.1` 是最新版（「二、2.1」） |
| 项目少配仓库 / 解析源被改（镜像、私服） | 根 POM 无 `<repositories>`、`.mvn/` 无 `settings.xml`、全仓无镜像关键字、四工作流未开 `cache:`（「二、2.2」） |
| 本地负缓存（`*.lastUpdated`） | 负缓存的消息形态**带** `cached in the local repository`，与 CI 日志**不同**（「二、2.3」） |
| 项目依赖解析失败（而非插件依赖） | 失败目标是 `jacoco:prepare-agent` 的**插件依赖**；且报错版本非本项目声明（`slf4j` 项目管 2.0.20） |
| 代码问题（重跑还会失败） | 同一提交、同一条腿**一轮成功一轮失败**，代码不可能这样（「二、2.5」） |
| 慢检查（`Gate` / `Compat`）也在卡人 | 该 PR 目标是 `dev`，而 `Gate` / `Compat` 只在 `pull_request → main / release/**` 触发，本次**只跑了 Build 快检查**（「二、2.5」） |

## 未决问题

**Q1 CI 那次的**具体**原因是什么？**

现状：只定性到「当次要不到」（仓库侧 / 网络侧瞬时故障），**未定位**上游返回码或超时记录。
阻塞点：Actions 运行日志不含仓库侧响应细节，且故障瞬时、无法原样复现。

**Q2 `F-14`：`.github/**/*.yml` 要不要本地检查？**

现状：`bin/CheckScripts.py` 只覆盖 `.bat` / `.cmd` / `.sh` / `.py`，YAML 语法错了只能等 GitHub 判。
阻塞点：需决定是否引入 YAML 解析 / composite 结构校验，以及是否纳入 CI 或本地检查。

**Q3 重试「最多 3 次、间隔 20 秒」的依据？**

现状：取值来自经验，**没有统计过瞬时故障的恢复分布**。若恢复分布长尾，3 次可能偏少；
若几乎瞬时恢复，间隔 20 秒又偏保守。

**Q4 「定向重试」的特征匹配够全吗？**

现状：只覆盖「依赖解析不到 / 传输中断 / 远端 5xx / 本地负缓存」四类。
未穷举的情形（如哈希校验失败、连接重置的具体文案）会被判为「真失败」而**不重试**——
这是有意保守，但没有做过「漏判率」的核算。
