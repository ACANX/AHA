# 构建排错

| 现象 | 排查方向 |
|---|---|
| `module not found` | `module-info.java` 的 requires / exports |
| `package is not visible` | 目标包未 exports，或缺少 requires |
| `release 25 not supported` | 确认 JDK 为 25 |
| `Maven 4 syntax error` | 移除 Maven 4 专有语法 |
| 自动模块报错 | 确认 Automatic-Module-Name |
