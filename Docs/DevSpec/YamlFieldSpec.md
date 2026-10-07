# YAML 字段命名规范

**文档版本**：v1.1.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-07
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.1.0 | 2026-10-07 | 补充运行目录命名规则（大驼峰单数） | @ACANX |

---

## 1. 基本规则

AHA 自有 YAML 文件的字段名（键名）统一采用**大驼峰（PascalCase）**。字段值保持原样，不受此规范约束。

Java 记录通过 `@JsonProperty` 显式映射 PascalCase（内部代码使用 Java 命名规范）。

## 2. 边界规则

- 环境变量引用保持 `${AHA_XXX}` 全大写下划线形式
- 枚举值、标识符、工具名、扩展 ID 保持与代码一致
- 第三方框架（Log4j2、GitHub Actions、Docker Compose）的 YAML schema 字段遵循第三方规范
- `Extension.Settings` 下的扩展 ID 键不强制 PascalCase
- `Model.yml` / 主配置 `Llm.Providers` 下的供应商 ID 键建议但不强制 PascalCase
- 数据库（SQLite）表名（单数）与字段名使用 snake_case（如 `gmt_create`），不受本规范约束
- 用户级数据目录（`$AHA_HOME` 下）采用大驼峰（PascalCase）单数命名：`Data/`、`Key/`、`Log/`、`Extension/`

## 3. 校验

`ConfigLoader.validateFieldNames` 递归校验配置字段名，非 PascalCase 字段将抛出 `ConfigException`（`INVALID_FIELD_NAME`）。

CI 中通过 `InvalidFieldName.yaml` fixture 验证校验逻辑。
