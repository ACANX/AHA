# YAML 字段命名规范

**文档版本**：v1.2.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-16
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.1.0 | 2026-10-07 | 补充运行目录命名规则（大驼峰单数） | @ACANX |
| v1.2.0 | 2026-10-16 | 新增 §4：GitHub Actions 工作流顶层 `name:` 的值统一用**大驼峰单数**（与文件名主干对应，不带空格 / 连字符）；并接入 `bin/CheckScripts.py::check_workflow_names()` | @ACANX / CNXNC |

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

---

## 4. GitHub Actions 工作流的 `name`

工作流文件（`.github/workflows/*.yml`）顶层 `name:` 的**值**统一采用**大驼峰（PascalCase）单数**，
与文件名主干一一对应 —— **不带空格、不带连字符、不写句子**：

| 工作流文件 | `name:` |
|---|---|
| `Build.yml` | `Build` |
| `BuildJVMArtifacts.yml` | `BuildJVMArtifacts` |
| `CliNative.yml` | `CliNative` |
| `CodeQL.yml` | `CodeQL` |
| `Compat.yml` | `Compat` |
| `DesktopNative.yml` | `DesktopNative` |
| `Gate.yml` | `Gate` |
| `PreDeployVerify.yml` | `PreDeployVerify` |
| `Release.yml` | `Release` |
| `SnapshotAssert.yml` | `SnapshotAssert` |
| `SnapshotJVM.yml` | `SnapshotJVM` |
| `VersionBump.yml` | `VersionBump` |
| `snapshot-cleanup.yml` | `SnapshotCleanup` |

**为什么要统一**：这个字符串到处都是——Actions 页面、运行列表、PR 上的检查分组、`gh run list` 的输出列。
写成 `Snapshot Assert` 这种带空格的写法时，「文件名」与「显示名」就成了**两套写法**，检索、对照、
贴给别人看都要在脑子里换算一次；大驼峰单数在日志与列表里是一体的，可复制、可 grep 到。
文件名用 kebab-case（`snapshot-cleanup.yml`）、`name:` 用大驼峰（`SnapshotCleanup`），两者一眼能对应上。

**不影响分支保护**：必需检查的契约是**作业名**（见 `BuildSpec.md` §8.1），不是工作流显示名——
改 `name:` 不会动必需检查；反过来，也**不要**把工作流的显示名当卡点标识写进分支保护。

**机械校验**：`bin/CheckScripts.py::check_workflow_names()` 逐个工作流断言本规则（自动生成 / 托管的工作流也在内），
不满足即红。GitHub Actions 的 **schema 字段名**（`on` / `jobs` / `runs-on`…）归第三方规范（见 §2），
本条只约束我们自己起的名字。
