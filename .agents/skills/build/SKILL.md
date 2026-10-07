---
name: build
description: 构建、打包与 Maven Wrapper 操作：执行 clean verify、单模块构建、双 Maven 版本兼容验证，并排查构建失败。当需要编译、打包、验证构建，或定位构建报错时使用。
license: Apache-2.0
compatibility: 需要 JDK 25；推荐通过 ./mvnw 使用 Maven 4.x；提交前需验证 Maven 3.9.x 兼容；首次构建需可访问 Maven Central。
metadata:
  version: "1.0.0"
  owner: ACANX
---

# 构建与打包

AHA 使用 Maven 多模块 + JPMS。构建必须同时满足 Maven 4.x 运行时与 Maven 3.9.x 兼容基线。

## 何时使用

- 编译、打包、验证整个项目
- 只构建某个模块及其依赖
- 构建失败需要定位原因
- 发布前确认覆盖率门禁

## 前置条件

- JDK 25（`java -version` 应输出 25）
- 网络可访问 Maven Central
- 位于仓库根目录

## 步骤

### 1. 完整构建（含测试与覆盖率门禁）

```bash
./mvnw clean verify
```

预期末尾输出 `BUILD SUCCESS`。

### 2. 双 Maven 版本验证（提交前必做）

```bash
./mvnw clean verify     # Maven 4.x 运行时
mvn clean verify        # Maven 3.9.x 兼容基线
```

两条都必须通过。POM 中不得使用 Maven 4 独有语法。

### 3. 单模块构建

```bash
./mvnw -pl aha-core -am clean install
```

`-am` 会一并构建其依赖模块。

### 4. 跳过测试（仅供本地快速试跑）

```bash
./mvnw clean install -DskipTests -Djacoco.skip=true
```

`-DskipTests` 必须搭配 `-Djacoco.skip=true`，否则 `verify` 阶段会因缺少覆盖率数据而失败。

### 5. 查看覆盖率报告

```bash
./mvnw -pl aha-core jacoco:report
```

报告位于 `aha-core/target/site/jacoco/index.html`。

## 参考

本技能目录内的资源：

- [references/maven-profiles.md](references/maven-profiles.md) - Maven 配置与 profile
- [references/troubleshooting.md](references/troubleshooting.md) - 构建报错排查

仓库文档：

- [BuildSpec.md](../../../Docs/DevSpec/BuildSpec.md) - 构建规范（强制要求）
- [BuildGuide.md](../../../Docs/Guide/BuildGuide.md) - 构建指南（完整操作）

## 常见错误

| 现象 | 原因 | 处理 |
|---|---|---|
| `module not found` | JPMS 声明缺失 | 检查 `module-info.java` 的 `requires` |
| `Maven 4 syntax error` | 使用了 Maven 4 独有语法 | 回退为 Maven 3.9.x 兼容写法 |
| 覆盖率门禁失败 | 行覆盖率 < 70% | 补测试，或确认是否应加入 JaCoCo 排除 |
| `No tests were executed` | 同时跳过测试与 JaCoCo | 补 `-Djacoco.skip=true` |
