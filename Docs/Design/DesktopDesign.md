# 桌面端设计

**文档版本**：v1.2.0
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
| v1.2.0 | 2026-10-08 | §2 记录界面路线与目标平台决策（`D-07` 进程内直调优先、`D-06` Win+Linux 为承诺 / macOS 只打包不测）；§3 补 JPMS 让路约束；§4 落地清单改为表格并标注依据 | @ACANX |

---

## 1. 版本规划

0.1 不实现桌面端（`aha-desktop` 仅占位）。0.2 实现 OpenJFX WebView + 本地 HTTP 服务器 + FXML Controller。

## 2. 技术

- OpenJFX 25（父 POM：`javafx.version = 25`）
- 主类：`com.acanx.module.aha.desktop.AhaDesktopApp`
- 打包：`jpackage`（MSI / DEB / DMG）
- **界面路线（`D-07` 决策，2026-10-08）：进程内直调 + JavaFX 原生控件**（`GUIDesign.md` §8.1）；
  `WebView + 本地 HTTP`（§8.2）**仅在 8.1 无法满足需求时**才启用，不作默认
- **目标平台（`D-06` 决策，2026-10-08）**：支持承诺为 **Windows + Linux**；
  macOS **只要求能打出包**（`jpackage`，CI 分平台构建），**不要求跑测试**

> **界面形态与技术选型见 [GUIDesign.md](GUIDesign.md)**（草案）。
>
> ⚠️ 需要澄清：此前文档中「WebView + 本地 HTTP 服务器 + FXML Controller」这一组合
> **从未真正决策**——它只出现在 `AhaDesktopApp` 的一句 Javadoc 占位注释里，
> 且 WebView（HTML/CSS/JS）与 FXML（JavaFX 原生控件）本就是两条不同的 UI 路线，
> 不能同时成立。GUIDesign.md §8 给出两个方案的取舍与建议（**建议进程内直调 + JavaFX 原生控件**），
> 待评审后回填本节。

## 3. 约束

- `jpackage` 不能交叉编译，CI 必须分平台构建（含 macOS 打包 job，但不跑 macOS 测试）
- **JPMS 为默认而非门槛**：与 OpenJFX（TestFX / WebView 反射）冲突时为 OpenJFX 让路（`C-01` 决策）
- 桌面端 WebView 不支持 native-image，native-image 流水线仅覆盖 CLI + Core + Tools

## 4. 实现现状

| 项 | 状态 |
|---|---|
| `aha-desktop` 模块与 JPMS 声明 | ✅ 占位（`AhaDesktopApp`） |
| JavaFX 依赖 | ⛔ 0.1 不引入（`javafx.version = 25` 已在父 POM 声明） |
| 构建产物 | ✅ 空 JAR，不参与覆盖率门禁 |

0.2 落地时需补充：

| 待补内容 | 依据 |
|---|---|
| **线程模型小节**：单一桥接点 + 高频 `ContentEvent` 的 `runLater` 节流、取消与关窗联动、虚拟线程禁直触 FX 线程 | `D-07` 剩余项 |
| 目标平台清单与 §3、`Build.yml` matrix 三者对齐 | `D-06` 剩余项 |
| 打包小节（`jpackage` 分平台 + macOS 只打包不测） | `D-08` |
| 工具依赖：`aha-desktop` 声明 `aha-tool`（内置工具） | `D-09` 已决策 |
| TestFX 测试环境要求 | `D-04` |

界面细节以 [GUIDesign.md](GUIDesign.md) 为准（技术选型已于 2026-10-08 定稿）。
