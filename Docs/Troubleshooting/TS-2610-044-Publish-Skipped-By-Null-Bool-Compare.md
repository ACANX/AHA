# TS-2610-044 · `null != false` 是 **false**：dev 的 JVM 预发行包被静默跳过 4 天

**日期**：2026-10-15
**关联 Issue**：issue [#213](https://github.com/ACANX/AHA/issues/213)（本条即其修复记录）
**关联档案**：`TS-2610-040` / `TS-2610-041`（同一条 `BuildJVMArtifacts` 触发链路的相邻问题）
**关联设计**：`Docs/DevSpec/BuildSpec.md` §8.1

---

## 1. 现象

`BuildJVMArtifacts.yml` 在 **`push` 到 `dev`** 时，`build-publish` 作业**每次都被跳过**（`skipped`）⇒
dev 线的 JVM 预发行包（tag `V<版本>-aha-jvm`，即 `aha-desktop-<版本>-<平台>.zip` / `aha-cli-<版本>.zip`）
**不再发布到发布页**。

现场：ACANX 看 run [38696042073](https://github.com/ACANX/AHA/actions/runs/38696042073) 时发现
`build-publish` 为 `skipped`（整个 run 却报 `success`，所以从 run 结论上完全看不出来）。

## 2. 排查过程

| # | 动作 | 预期 | 实际 | 结论 |
|---|---|---|---|---|
| 1 | 看该 run 的作业结论 | 九个作业该跑的跑 | 六条构建腿全 `success`，`build-publish` **`skipped`** | 复现 |
| 2 | 逐个看更早的 dev push run | 只有本次异常 | **20 个 run 全部 `skipped`**（`10-14 11:10` → `10-15 07:01`）| **不是偶发，是长期** |
| 3 | 查发布页上最后一个 `-aha-jvm` | — | `V0.1.3.00178-aha-jvm`，`2026-10-14T06:52:20Z` | 停发时间点 |
| 4 | `git log -L` 追 `build-publish` 的 `if` | — | 该门由 **#179** 引入，合并于 `2026-10-14T09:32:48Z` | **正好在最后一个包之后** |
| 5 | 看 #179 的意图 | — | 原文：「`build-publish` 的门加 `inputs.publish != false && != 'false'`（**push / dispatch 默认仍发布**，只有显式 false 才跳过）」| 意图与实际相反 |
| 6 | 复核表达式语义 | `null != false` 应为 true | GitHub 宽松比较把 `null → 0`、`false → 0` ⇒ **`0 != 0` = false** | 根因确认 |

```bash
# 第 2 / 3 步的取证
for id in $(gh run list --workflow BuildJVMArtifacts.yml --event push --branch dev --limit 20 \
             --json databaseId --jq '.[].databaseId'); do
  gh run view "$id" --json createdAt,jobs \
    --jq '"\(.createdAt) \(.jobs[]|select(.name=="build-publish")|.conclusion)"'
done
# ⇒ 20 行全是 skipped

gh api "repos/ACANX/AHA/releases?per_page=40" \
  --jq '.[] | select(.tag_name|test("aha-jvm$")) | "\(.created_at) \(.tag_name)"' | head -1
# ⇒ 2026-10-14T06:52:20Z V0.1.3.00178-aha-jvm
```

被排除的假设：

| 已排除 | 依据 |
|---|---|
| 某条构建腿失败导致发布被跳过 | 六条腿全 `success`；且门里用的是 `always()`，腿失败也会发 |
| 制品没上传 | 制品正常上传到 Actions artifacts（只是没挂发布页）|
| 并发取消 | 不是 `cancelled`，是 `skipped`（作业根本没被调度）|
| 发布 API 报错 | 作业没有运行，谈不上报错 |

## 3. 根因

`build-publish` 的门（#179 引入）：

```yaml
if: >-
  always() && needs.build-version.result == 'success'
  && inputs.publish != false && inputs.publish != 'false'
```

`push` 事件**没有 `inputs` 上下文** ⇒ `inputs.publish` 是 `null`。GitHub 表达式的宽松比较会把两侧
折算成数字（官方换算表：`null → 0`、`false → 0`）⇒

```text
inputs.publish != false   ⇒   null != false   ⇒   0 != 0   ⇒   false
```

⇒ 作业被**静默跳过**。也就是说 #179 想表达的「push / dispatch 默认仍发布」**从来没有生效过**；
当时只验证了「`publish=false`」那条路（快照线 `workflow_call`），没验证「push 该发布」，所以漏掉了。

## 4. 修复（方案 B：把判断挪进脚本）

| 文件 | 改动 |
|---|---|
| `.github/workflows/BuildJVMArtifacts.yml` | ① `build-version` 新增步骤「归一『要不要挂发布页』」：`publish="${INPUT_PUBLISH:-true}"`，`case` 归一到 `true` / `false` 字符串后写 `$GITHUB_OUTPUT`；② `build-version.outputs` 增加 `publish`；③ `build-publish.if` 改为 `needs.build-version.outputs.publish == 'true'` |
| `bin/CheckScripts.py` | 新增 `check_yaml_input_bool_compare()` 守卫：workflow 里**不得**对 `inputs.*` 做 `== / != true|false`（剥注释后匹配），违规点名 |

理由：`null` / `false` / `'false'` 这堆折算坑只会在 YAML 表达式里咬人；落到 shell 就是普通字符串判断 ——
可读、可测、可静态守卫，也与仓库「判定逻辑不写进 YAML」的一贯做法一致。

```bash
# build-version 里新增的那一步（节选）
publish="${INPUT_PUBLISH:-true}"          # push 无 inputs ⇒ 为空 ⇒ 默认 true（照旧发布）
case "$publish" in false|False|FALSE) publish=false ;; *) publish=true ;; esac
echo "publish=${publish}" >> "$GITHUB_OUTPUT"
```

## 5. 验证

- **`push` 到 `dev`**：`build-publish` 不再 `skipped`，发布页新增 `V<版本>-aha-jvm`（本 PR 合并后由真机确认）；
- **快照线**（`workflow_call` 传 `publish: false`）：`build-publish` **仍保持 `skipped`**，没误往 dev tag 上写；
- **`workflow_dispatch`**：`publish` 留空 / `true` ⇒ 发布，显式 `false` ⇒ 跳过；
- **守卫反向验证**：把 `build-publish.if` 换回 `inputs.publish != false` ⇒ `CheckScripts` 点名该行并退出码 1；
  还原后复绿；
- `python3 bin/CheckScripts.py` 退出码 0；`actionlint` 对 `BuildJVMArtifacts.yml` 无输出。

## 6. 教训与沉淀

- **YAML 里的宽松比较是个坑**：`null`、`false`、`''` 都被折算成 `0` ⇒ `null == false` 为 **true**、
  `null != false` 为 **false**。凡是「没传就该走默认」的开关，**不要**在 `if` 里直接和布尔字面量比；
  归一成字符串再比（已落守卫）；
- **"没传"与"传了 false"必须能区分**：本单的意图正是这两者相反，而写法让它们变得相同；
- **验证要覆盖"默认路径"**：#179 只验了「显式 false」，没验「不传」——而缺陷恰好只在"不传"时出现；
- **`run=success` 不代表交付成功**：作业被 `skipped` 时整个 run 仍是绿色（这也是它藏了 4 天的原因）。
  凡「跳过 = 少交付」的门，应由守卫或摘要显式暴露。

## 7. 涉及文件

| 类型 | 路径 |
|---|---|
| 工作流 | `.github/workflows/BuildJVMArtifacts.yml`（`build-version` 新增步骤 + `build-publish.if`）|
| 脚本 / 守卫 | `bin/CheckScripts.py`（`check_yaml_input_bool_compare()`）|
| 文档 | `Docs/DevSpec/BuildSpec.md` §8.1、本篇 |

---

## 结论分层

### 已观测事实

- dev push 的 `build-publish` 在 `10-14 11:10` → `10-15 07:01` 的 20 个 run 里**全部 skipped**；
- 发布页最后一个 `-aha-jvm` 是 `2026-10-14T06:52:20Z`；
- 引入该门的 #179 合并于 `2026-10-14T09:32:48Z`（在最后一个包之后）。

### 机制 / 行为模型

- GitHub 表达式宽松比较的类型折算：`null → 0`、`false → 0`、`'' → 0`、`'false' → NaN`；
- `push` 事件没有 `inputs` 上下文 ⇒ 任何 `inputs.x` 读出来都是 `null`。

### 未定位 / 未复现

- 无技术未知项；修复的真机确认（push 后真出包）在合并后即可观察。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 20 个 run 的 build-publish | `gh run view <id> --json jobs`（循环 20 次）| 全部 `skipped` |
| 最后一个 JVM 预发行版 | `gh api repos/ACANX/AHA/releases` | `2026-10-14T06:52:20Z V0.1.3.00178-aha-jvm` |
| 引入缺陷的提交 | `gh pr view 179` + `git log -L 334,342:.github/workflows/BuildJVMArtifacts.yml` | `79e52e2`，`mergedAt=2026-10-14T09:32:48Z` |
| 守卫反向验证 | `CheckScripts.py`（把门换回旧写法）| `❌ .github/workflows/BuildJVMArtifacts.yml:… 对 inputs 做了宽松布尔比较`，退出码 1 |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 构建腿失败 | 六条腿全 `success`；门里有 `always()` |
| 制品未上传 | Actions artifacts 里有制品 |
| 并发取消 | 结论是 `skipped`（未调度），不是 `cancelled` |

## 未决问题

无。
