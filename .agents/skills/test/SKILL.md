---
name: test
description: 测试编写与执行：单元测试 / 集成测试与覆盖率由 CI（Build.yml / Gate.yml）承担，Agent 不在本地运行 Maven 测试；当需要新增测试、根据 PR 的 CI 日志定位失败用例或分析覆盖率缺口时使用。
license: Apache-2.0
compatibility: 需要 JDK 25；使用 JUnit 5；测试运行于 module-path，故不可依赖 jdk.httpserver 等未声明模块；本地不执行 Maven。
metadata:
  version: "2.0.0"
  owner: ACANX
---

# 测试与覆盖率（由 CI 承担）

测试使用 JUnit 5，运行在 JPMS module-path 上。覆盖率门禁为 BUNDLE 级行覆盖率 ≥ 70%，绑定 `verify` 阶段。

> **硬规则：本地不运行任何 Maven 测试动作。**
> `test`、`clean test`、`verify`、`-Dtest=...` 一律由 CI 承担。
> 推送后**以 PR checks 里 `Build` / `Gate` 的结果为准**，失败再按 CI 日志排查修改。

## 何时使用

- 新增或修改测试代码
- PR 的 checks 失败，需要根据 CI 日志定位失败用例
- 分析覆盖率缺口（从 CI 的 `Gate` 报告读取）
- 为 LLM 适配器添加固定 fixture
- 用户**明确要求**在本地运行测试时（此时按用户指令执行，并说明这是例外）

## 步骤（Agent 工作流）

1. **写完测试代码**，本地**不执行**，只做静态检查：
   ```bash
   python3 bin/CheckChanged.py
   ```
2. **提交并推送**，由 CI 自动跑测试：
   - `Build.yml`：每次 push / PR，编译 + 单元测试；
   - `Gate.yml`：合入 `main` 前 / 每周 / 发布前，`clean verify` + 覆盖率门禁。
3. **在 PR checks 里看结果**：
   ```bash
   gh pr checks <PR号>
   ```
4. **失败才排查**：从 CI 日志取失败用例与堆栈，定位后在本地**只改代码**，不本地复现整套测试。
   ```bash
   gh run view <run-id> --log-failed
   ```
5. 修复后**再推送**，重新看 checks，直到通过。

## 设计新增测试的注意点

- 测试位于各模块 `src/test/java`，包名与主代码一致。
- 运行在 module-path 上：不要引用 `jdk.httpserver` 等未在 `module-info.java` 中 `requires`
  的 JDK 模块；需要本地 HTTP 服务时用自包含 `ServerSocket` 实现（参见 `DefaultLlmClientTest`）。
- LLM 适配器测试使用固定 JSON fixture，不做真实网络调用。
- 需要可注入的替代实现时，优先使用既有测试替身（如 `ScriptedLlmClient`、`InMemoryMemoryStore`）。
- **测试要能在 CI 上稳定通过**：不依赖本机路径、时间、网络与固定端口。

## 覆盖率

覆盖率门禁（≥ 70%）与报告由 CI 的 `Gate.yml` 产出。**不要在本机 `jacoco:report`**：
需要数字时读 CI 的 `Gate` 日志或下载其构建产物。

判断「这次是否需要补覆盖率」见
[BuildSpec.md](../../../Docs/DevSpec/BuildSpec.md) 第 8 节。

## 参考

本技能目录内的资源：

- [references/test-fixtures.md](references/test-fixtures.md) - fixture 组织方式
- [references/coverage-guide.md](references/coverage-guide.md) - 覆盖率门禁与排除项

仓库文档：

- [TestingSpec.md](../../../Docs/DevSpec/TestingSpec.md) - 测试规范（强制要求）
- [InternalProtocolSpec.md](../../../Docs/Design/InternalProtocolSpec.md) - 内部协议与 fixture 约定
- [AGENTS.md](../../../AGENTS.md) - 仓库约定（本地不跑 Maven）
