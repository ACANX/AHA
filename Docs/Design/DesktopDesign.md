# 桌面端设计

**文档版本**：v1.1.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-06
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.1.0 | 2026-10-07 | 第 2 节指向 GUIDesign.md；标明 WebView / FXML 的技术表述待评审 | @ACANX |

---

## 1. 版本规划

0.1 不实现桌面端（`aha-desktop` 仅占位）。0.2 实现 OpenJFX WebView + 本地 HTTP 服务器 + FXML Controller。

## 2. 技术

- OpenJFX 25（父 POM：`javafx.version = 25`）
- 主类：`com.acanx.module.aha.desktop.AhaDesktopApp`
- 打包：`jpackage`（MSI / DEB）

> **界面形态与技术选型见 [GUIDesign.md](GUIDesign.md)**（草案）。
>
> ⚠️ 需要澄清：此前文档中「WebView + 本地 HTTP 服务器 + FXML Controller」这一组合
> **从未真正决策**——它只出现在 `AhaDesktopApp` 的一句 Javadoc 占位注释里，
> 且 WebView（HTML/CSS/JS）与 FXML（JavaFX 原生控件）本就是两条不同的 UI 路线，
> 不能同时成立。GUIDesign.md §8 给出两个方案的取舍与建议（**建议进程内直调 + JavaFX 原生控件**），
> 待评审后回填本节。

## 3. 约束

- `jpackage` 不能交叉编译，CI 必须分平台构建
- 桌面端 WebView 不支持 native-image，native-image 流水线仅覆盖 CLI + Core + Tools

## 4. 实现现状

| 项 | 状态 |
|---|---|
| `aha-desktop` 模块与 JPMS 声明 | ✅ 占位（`AhaDesktopApp`） |
| JavaFX 依赖 | ⛔ 0.1 不引入（`javafx.version = 25` 已在父 POM 声明） |
| 构建产物 | ✅ 空 JAR，不参与覆盖率门禁 |

0.2 落地时需补充：FXML/Controller 结构、WebView 与 `AgentService` 的桥接、
TestFX 测试、`jpackage` 打包流程。
