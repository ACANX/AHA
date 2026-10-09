---
name: build
description: 构建、打包与 Maven 版本兼容：由 CI（Build.yml / Gate.yml / Compat.yml / BuildJVMArtifacts.yml）承担，Agent 不在本地执行 Maven；当需要了解改动会触发哪些构建、或需要根据 PR 的 CI 日志排查构建失败时使用。
license: Apache-2.0
compatibility: 需要 JDK 25；CI 通过 ./mvnw 使用 Maven 4.x，并由 Compat.yml 验证 Maven 3.9.x 兼容；本地不执行 Maven。
metadata:
  version: "2.0.0"
  owner: ACANX
---

# 构建与打包（由 CI 承担）

AHA 使用 Maven 多模块 + JPMS。构建必须同时满足 Maven 4.x 运行时与 Maven 3.9.x 兼容基线。

> **硬规则：本地不执行任何 Maven 编译 / 测试 / 打包动作。**
> `compile`、`test-compile`、`test`、`clean test`、`verify`、`package`、`install`
> 一律由 CI 承担。推送后**以 PR 的 checks 结果为准**，失败再按 CI 日志排查修改。

## 何时使用

- 想知道「这次改动会触发哪些 CI 构建」
- PR 的 checks 失败，需要根据 CI 日志定位构建问题
- 需要确认 Maven 4.x / 3.9.x 兼容、覆盖率门禁等要求
- 用户**明确要求**在本地执行构建时（此时按用户指令执行，并说明这是例外）

## 步骤（Agent 工作流）

1. **本地只跑静态检查**（不跑 Maven）：
   ```bash
   python3 bin/CheckChanged.py
   ```
2. **提交并推送**到特性分支，发起 / 更新 PR。
3. **等 CI**：在 PR 的 checks 里看 `Build` / `Gate` / `Compat` 等结果。
4. **失败才排查**：用 CI 日志定位，不在本地复现整套构建。
   ```bash
   gh pr checks <PR号>
   gh run view <run-id> --log-failed
   ```
5. 修复后**再推送**，重新看 checks，直到全部通过。

## CI 承担矩阵

| 工作流 | 触发 | 承担 |
|---|---|---|
| `Build.yml` | 每次 push / PR | 编译 + 单元测试（`clean test -Djacoco.skip=true`） |
| `BuildJVMArtifacts.yml` | `push` → `dev` | 按「平台 × JDK（25 / 27）」构建 `aha-desktop` / `aha-cli` 便携包并发布 dev 预发行版 |
| `Gate.yml` | 合入 `main` 前 / 每周 / 发布前 | 完整 `clean verify`、覆盖率门禁、文档 / 技能 / 脚本 / 像素标志、重复率 |
| `Compat.yml` | POM / `module-info` 等变更 | Maven 3.9.x 兼容（`mvn clean verify`） |
| `Release.yml` | 发布 | 按平台出包并挂 release 页面 |

> 上表中的命令是 **CI 内部执行**的内容，不是本地执行清单。
> 需要实测数字（用例数、覆盖率）时，从 CI 的 `Build` / `Gate` 日志取。

## 参考

本技能目录内的资源：

- [references/maven-profiles.md](references/maven-profiles.md) - Maven 配置与 profile（CI 使用）
- [references/troubleshooting.md](references/troubleshooting.md) - 构建报错排查

仓库文档：

- [BuildSpec.md](../../../Docs/DevSpec/BuildSpec.md) - 构建规范（强制要求）
- [BuildGuide.md](../../../Docs/Guide/BuildGuide.md) - 构建指南（完整操作）
- [AGENTS.md](../../../AGENTS.md) - 仓库约定（本地不跑 Maven）

## 常见错误（在 CI 日志里看到时的处理）

| 现象 | 原因 | 处理 |
|---|---|---|
| `module not found` | JPMS 声明缺失 | 检查 `module-info.java` 的 `requires` |
| `cannot find symbol` | 源文件未被提交 / 被 `.gitignore` 误吞 | `git ls-files <文件>` 核对；跑 `python3 bin/CheckScripts.py` |
| `Maven 4 syntax error` | 使用了 Maven 4 独有语法 | 回退为 Maven 3.9.x 兼容写法 |
| 覆盖率门禁失败 | 行覆盖率 < 70% | 补测试，或确认是否应加入 JaCoCo 排除 |
| `No tests were executed` | 同时跳过测试与 JaCoCo | CI 需补 `-Djacoco.skip=true`（仅 CI 侧调整） |
