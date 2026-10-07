---
name: test
description: 测试编写与执行：运行单元测试与集成测试、生成并分析覆盖率报告、维护 LLM 适配器 fixture。当需要新增测试、执行测试、检查覆盖率门禁或验证 fixture 时使用。
license: Apache-2.0
compatibility: 需要 JDK 25；使用 JUnit 5；测试运行于 module-path，故不可依赖 jdk.httpserver 等未声明模块。
metadata:
  version: "1.1.0"
  owner: ACANX
---

# 测试与覆盖率

测试使用 JUnit 5，运行在 JPMS module-path 上。覆盖率门禁为 BUNDLE 级行覆盖率 ≥ 70%，绑定 `verify` 阶段。

## 何时使用

- 新增或修改测试
- 执行测试、定位失败用例
- 分析覆盖率缺口
- 为 LLM 适配器添加固定 fixture

## 步骤

### 1. 运行全部测试

```bash
./mvnw test
```

### 2. 运行单模块测试

```bash
./mvnw -pl aha-core test
```

### 3. 运行单个测试类

```bash
./mvnw -pl aha-core test -Dtest=AgentEngineTest
```

### 4. 生成覆盖率报告

```bash
./mvnw -pl aha-core jacoco:report
```

报告：`aha-core/target/site/jacoco/index.html`

### 5. 检查门禁是否通过

```bash
./mvnw verify
```

覆盖率不足时 `jacoco:check` 会使构建失败。

### 6. 按变更范围选择检查

不要每次都跑全套——三个 `Check*.py` 各覆盖一类文件，`verify` 是分钟级。
用 `bin/CheckChanged.py` 按实际改了什么决定跑什么：

```bash
python3 bin/CheckChanged.py            # 按 git 变更自动判定
python3 bin/CheckChanged.py <路径>...  # 按给定路径判定
```

仅改文档时只会跑 `CheckDocs.py`（亚秒级）；仅改实现代码时用
`./mvnw -pl <模块> -am test -Djacoco.skip=true`，不必跑覆盖率门禁。
**何时必须跑完整 `verify`** 见 [BuildSpec.md](../../../Docs/DevSpec/BuildSpec.md) 第 8 节。

## 编写测试的注意点

- 测试位于各模块 `src/test/java`，包名与主代码一致
- 运行在 module-path 上：不要引用 `jdk.httpserver` 等未在 `module-info.java` 中 `requires` 的 JDK 模块，需要本地 HTTP 服务时用自包含 `ServerSocket` 实现（参见 `DefaultLlmClientTest`）
- LLM 适配器测试使用固定 JSON fixture，不做真实网络调用
- 需要可注入的替代实现时，优先使用既有测试替身（如 `ScriptedLlmClient`、`InMemoryMemoryStore`）

## 参考

本技能目录内的资源：

- [references/test-fixtures.md](references/test-fixtures.md) - fixture 组织方式
- [references/coverage-guide.md](references/coverage-guide.md) - 覆盖率门禁与排除项

仓库文档：

- [TestingSpec.md](../../../Docs/DevSpec/TestingSpec.md) - 测试规范（强制要求）
- [InternalProtocolSpec.md](../../../Docs/Design/InternalProtocolSpec.md) - 内部协议与 fixture 约定
