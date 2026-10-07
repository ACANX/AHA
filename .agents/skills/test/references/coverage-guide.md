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

以下包不参与门禁统计（多为 0.1 尚未实现的占位实现）：

| 排除模式 | 原因 |
|---|---|
| `com/acanx/module/aha/desktop/**` | 桌面端 0.1 为占位 |
| `com/acanx/module/aha/core/extension/**` | 扩展运行时 0.1 为占位 |
| `com/acanx/module/aha/core/mcp/**` | MCP 0.1 为占位 |
| `com/acanx/module/aha/cli/ChatCommand*` | 交互式 TTY 逻辑，需真实终端 |
| `com/acanx/module/aha/cli/RunCommand*` | 同上 |

## 实测覆盖率（0.1）

| 模块 | 行覆盖率 |
|---|---|
| aha-common | 90.9% |
| aha-extension-api | 100.0% |
| aha-core | 73.9% |
| aha-tool | 87.3% |
| aha-cli | 90.9% |

## 命令

```bash
./mvnw -pl aha-core jacoco:report    # 生成单模块报告
./mvnw verify                        # 生成报告并执行门禁检查
```

报告位置：`<module>/target/site/jacoco/index.html`

## 跳过测试时

`-DskipTests` 会导致无覆盖率数据，必须同时跳过门禁，否则 `verify` 失败：

```bash
./mvnw clean install -DskipTests -Djacoco.skip=true
```
