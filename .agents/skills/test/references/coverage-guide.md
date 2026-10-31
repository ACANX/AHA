# 覆盖率指南

## 门禁

JaCoCo 在 `verify` 阶段执行 `check`，规则为 **BUNDLE 级 LINE COVEREDRATIO ≥ 0.70**。

```xml
<rule>
    <element>BUNDLE</element>
    <limits>
        <limit>
            <counter>LINE</counter>
            <value>COVEREDRATIO</value>
            <minimum>0.70</minimum>
        </limit>
    </limits>
</rule>
```

注意是 BUNDLE 级而非按模块分别设限：Maven 多模块下每个模块各自是一个 bundle，因此**每个模块**的行覆盖率都需 ≥ 0.70。

## 排除项

**权威登记处是 `Docs/DevSpec/TestingSpec.md` §3.2**（与根 `pom.xml` 的 jacoco `excludes` 逐条对应）
—— 本节的表只是速查，**两边不一致时以规范为准**，并且不要在这里补新口径。

| 排除模式 | 原因（详见规范 §3.2） |
|---|---|
| `com/acanx/module/aha/desktop/**` | 0.1 为占位、整体排除；0.2 起按单独阈值 0.30 纳入（`D-05`） |
| `com/acanx/module/aha/core/extension/**` | 扩展运行时属 0.3（阶段八） |
| `com/acanx/module/aha/core/mcp/**` | MCP 适配器为 0.2 占位 |
| `com/acanx/module/aha/cli/ChatCommand*` | 交互式 TTY 逻辑，需真实终端 / 手工端到端验收 |
| `com/acanx/module/aha/cli/RunCommand*` | 同上 |

> 排除是**度量口径**，不是「不用测」：上表各类仍有各自的验收方式（手工端到端 / 真机验收 /
> 0.2 起纳入门禁）。新增排除项必须写清「何时解除」或「由什么替代覆盖」。

## 实测覆盖率（0.1）

| 模块 | 行覆盖率 |
|---|---|
| aha-common | 90.9% |
| aha-extension-api | 100.0% |
| aha-core | 73.9% |
| aha-tool | 87.3% |
| aha-cli | 90.9% |

## 命令（由 CI 执行，本地不跑）

覆盖率报告与门禁由 CI 的 `Gate.yml` 产出：

```bash
./mvnw verify                        # CI：生成报告并执行门禁检查
./mvnw -pl aha-core jacoco:report    # CI：生成单模块报告
```

报告位置：`<module>/target/site/jacoco/index.html`

**Agent 需要覆盖率数字时，从 CI 的 `Gate` 日志或其构建产物读取，不在本机执行这些命令。**

## 跳过测试时（仅 CI 侧）

`-DskipTests` 会导致无覆盖率数据，必须同时跳过门禁，否则 `verify` 失败：

```bash
./mvnw clean install -DskipTests -Djacoco.skip=true
```
