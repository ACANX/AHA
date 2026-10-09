# Maven Profile 速查（CI 侧）

> **本地不执行这些命令。** 下表是各工作流**内部**使用的 Maven 入口，
> 供 Agent 理解「CI 到底在跑什么」以及排查 CI 日志时对照，不是本地执行清单。
> 本地只允许 `python3 bin/Check*.py`。

| 场景 | 命令（CI 使用） | 承担工作流 |
|---|---|---|
| 全量构建 + 测试 + 覆盖率 | `./mvnw clean verify` | `Gate.yml` |
| Maven 3.9.x 兼容 | `mvn clean verify` | `Compat.yml` |
| 编译 + 单元测试 | `./mvnw clean test -Djacoco.skip=true` | `Build.yml` |
| 单模块 + 依赖 | `./mvnw -pl aha-core -am clean install` | 按需 |
| 依赖树 | `./mvnw dependency:tree` | 按需 |
| Javadoc | `./mvnw javadoc:javadoc` | `Gate.yml` |

原生镜像相关 profile（`desktop-native` / `cli-native`）仅由
`DesktopNative.yml` / `CliNative.yml` 激活，见
[java-app-graalvm-native-image-compile 技能](../../java-app-graalvm-native-image-compile/SKILL.md)。
