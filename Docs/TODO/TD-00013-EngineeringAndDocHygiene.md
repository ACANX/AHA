# TD-00013 EngineeringAndDocHygiene

> 待办编号：TD-00013
> 标题：工程效能与文档卫生：CI 日志噪音、工作流/文档检查、脚本目录与暂存区文档去留
> 状态：☐ 未开始
> 跟踪 Issue：[84](https://github.com/ACANX/AHA/issues/84)
> 创建日期：2026-10-09
> 最后更新：2026-10-09

---

## 来源

`Docs/TODO.md` 第 1 / 7 / 10 节与 `Docs/PLAN.md` §4 / §8.3。

## 1. 工程效能（CI / 检查）

| 编号 | 事项 | 状态 |
| --- | --- | --- |
| F-14 | `.github/**/*.yml` 无本地检查（`bin/CheckScripts.py` 只覆盖 `.bat`/`.cmd`/`.sh`/`.py`），工作流语法写错只能等 GitHub 判 | ⏸ 待决策 |
| F-15 | 文档标题 / 表行**编号重复**只能人工看（`PLAN.md` `A-09`/`A-10` 与两份 `Design/` 文档的重号都是事后肉眼发现） | ⏸ 待决策 |
| F-16 | Maven 4 下 `verify` 日志出现 10 行 `[stderr]`（`sqlite-jdbc` 触发的 JDK native-access 警告），Maven 3.9.11 下为 0 | ☐ |
| F-17 | `-Djavafx.platform=win` 时构建输出 `recursive variable reference: javafx.platform`（构建仍成功，疑似插值路径不健康） | ☐ |
| F-07 | `Script/PowerShell/CountJavaLoc.ps1` 未纳入 `CheckScripts.py`；`Script/` 与 `bin/` 职责重叠 | ⏸ 待决策 |

**F-16 已知陷阱**：不能简单写死 `argLine`（JaCoCo `prepare-agent` 也经它注入）；正确写法是 `@{argLine} --enable-native-access=org.xerial.sqlitejdbc`，但 `-Djacoco.skip=true` 下该属性不存在会报错，需先验证两种情形。

## 2. 文档与仓库卫生

| 编号 | 事项 | 状态 |
| --- | --- | --- |
| A-08 | `Docs/` 根下的 `PLAN.md` / `TODO.md` / `Dbsx.txt` 是否收进 `Docs/AHA/` | ⏸ 待决策 |
| E-12 | `TODO.md` 文件名不符合 `DocumentationSpec.md` 第 1 节 PascalCase（保留名单不含 `TODO`） | ⏸ 待决策 |
| H-01 | 启动标志默认风格：像素风（现状）还是线框风 | ⏸ 待决策 |
| — | `Docs/Dbsx.txt`（用户原始待办）去留（`PLAN.md` §8.3.4） | ⏸ 待决策 |
| — | `.editorconfig` 缺失（`PLAN.md` §8.3.6） | ⏸ 可选 |
| — | `Docs/DevSpec/` 有 6 个文件不以 `Spec.md` 结尾（`PLAN.md` §8.3.3） | ⏸ 待定 |

## 验收标准

- [ ] F-14 / F-15 拍板：纳入检查或明确不纳入（若纳入，`CheckScripts.py` / `CheckDocs.py` 有对应实现）；
- [ ] F-16 修好后 CI 的 `Build` 与 `Gate` 日志中 `[stderr]` 行数为 0；
- [ ] F-17 在 Windows 原生 `./mvnw -pl aha-desktop -am package` 输出中确认有无该行，有则定位并消除；
- [ ] F-07 拍板 `.ps1` 检查范围与 `Script/` 去留；
- [ ] A-08 / E-12 / H-01 / Dbsx 去留 / `.editorconfig` / DevSpec 命名逐项给出结论。

## 与本次流程调整的关系

按新的流程约定（见「遗留事项总览与流程调整」Issue），**`TODO.md` 不再自动追加内容**；本节条目在 Issue 里跟踪，`TODO.md` 只保留历史记录。

