# Maven Profile 速查

| 场景 | 命令 |
|---|---|
| 全量构建 | `./mvnw clean verify` |
| 单模块 + 依赖 | `./mvnw -pl aha-core -am clean install` |
| 仅测试 | `./mvnw test` |
| 依赖树 | `./mvnw dependency:tree` |
| Javadoc | `./mvnw javadoc:javadoc` |
