# TS-2610-046 · 原生镜像 exe 里输入框的轮廓线丢失：modena 的 CSS 函数整条被丢弃

> 事件日期：2026-10-15
> 作者：@cnxnc
> 关联 Issue：[#229](https://github.com/ACANX/AHA/issues/229)（切主题后对话输入框轮廓线丢失，纯色界面下分不出范围）
> 关联记录：`TS-2610-029`（modena CSS 函数失效 ⇒ 整条声明被丢弃）、`TS-2610-028`（颜色必须落到属性 API）、`TS-2610-035`（内容面与页底同色）、同族 `025` / `026` / `031` / `038`

## 1. 现象

**只在原生镜像（GraalVM native-image）编出的 exe 里**：切换主题后，对话输入框（`aha.composer`）的**轮廓线不见了**。
因为按 `TS-2610-035` 的口径，内容面（输入框底色）与页底是**同色**（`Palette.CONTROL_INNER`），
轮廓线是唯一能标出输入框范围的东西 —— 它一没，纯色界面下就分不出输入框在哪。

JVM 模式（`java -jar`）下**正常**：同一份样式表、同一段代码，差别只在「原生镜像里 CSS 怎么被处理」。

## 2. 排查过程

| 步 | 动作 | 结论 |
|---|---|---|
| 1 | 读输入框的内联样式（`DesktopShell.wireComposer`） | 只给了 `-fx-control-inner-background` / `-fx-background-color` / `-fx-text-fill` / `-fx-prompt-text-fill` —— **没有边框** |
| 2 | 找那圈线从哪来 | 补丁样式表里 `.text-input, .text-field, .text-area … { -fx-border-color: %BORDER%; -fx-border-radius: 3; }`（纯字面量），另有 modena 自己也给输入类控件画边 |
| 3 | 读 `TS-2610-029` | 原生镜像下 **modena 的 CSS 函数（`derive()` / `linear-gradient()`）会让整条声明被丢弃** —— modena 画输入框边的那几条正是这种写法 |
| 4 | 读 `TS-2610-028` 的修复链 | 这个仓库对「原生镜像下 CSS 不生效」的既定解法是**把颜色落到属性 API 上**（`ThemePaint`） |
| 5 | 看 `ThemePaint.apply` 覆盖了什么 | 只同步 **文字色** 与 **背景色**（`-fx-text-fill` / `-fx-background-color` / `-fx-control-inner-background`）—— **边框从来没同步过** |
| 6 | 合起来 | 原生下：modena 的边被丢弃 + 补丁表那条对 `TextArea` 不生效 + 内联样式里根本没有边框 + 底色与页底同色 ⇒ **整块融入背景** |

## 3. 根因

**轮廓线此前完全依赖 CSS，而原生镜像下 CSS 对输入类复合控件不可靠。**

- modena 那条带 `derive()` / `linear-gradient()` 的边在原生镜像下**整条被丢弃**（`TS-2610-029` 已定案）；
- 补丁样式表虽然给了纯字面量的 `-fx-border-color`，但对 `TextArea` 这类复合控件，声明落到哪个内部节点上
  在原生镜像里并不可靠（与底色同一个理由，见 `TS-2610-028` §2.4）；
- 而 `ThemePaint` 的属性 API 兜底**只覆盖背景与文字**，边框漏在覆盖范围之外 —— 这是本单的缺口。

## 4. 修复

| 环节 | 改动 |
|---|---|
| `ThemePaint` | 新增 `paintBorder(Region, String)`：读样式串里的 `-fx-border-color`（+ `-fx-border-width` / `-fx-border-radius`，缺省 1 / 3），用 `region.setBorder(new Border(new BorderStroke(…)))` 落到属性 API；`apply()` 里在背景之后调用。约定：样式串**没写边框**就不动现有边框（不抹掉 CSS 给的），写 `transparent` / `none` 才清空 |
| 输入类控件 | 给**三处**同类控件的内联样式补上轮廓线（值取自补丁样式表，JVM 下外观不变）：对话输入框 `composer`、会话搜索框 `sessionSearch`、日志面板文本域（后者改用 `ThemePaint.themedForever`，顺便把底色/文字也显式给上） |
| 运行期断言 | `AhaDesktopSmokeTest`：读 `composer` **实际生效的** `Region.getBorder()`，断言其颜色为 `Palette.BORDER`，且**切主题后跟着换**（亮 / 暗各验一次） |

## 5. 验证

| 项 | 做法 | 结果 |
|---|---|---|
| 本地静态检查 | `CheckDocs` / `CheckScripts` / `CheckSkills` / `CheckTheme` / `CheckChanged` | ✅ 全绿 |
| 生成物一致性 | `python3 bin/GenThemeCss.py --check` | ✅（本轮未改样式表，确认无漂移） |
| GUI 冒烟 | CI 的 `ui-smoke`（含本轮新增的边框断言） | 见 PR 运行记录 |
| **原生 exe 观感** | 真机：切换主题后看输入框是否仍有轮廓线（亮 / 暗各一次） | ⏳ **需 ACANX**（本机无界面环境，且只有原生 exe 才复现） |

## 6. 教训与沉淀

1. **「JVM 正常」不能推出「原生也正常」**：同一个 bug 只在原生镜像里出现，是因为**两边处理 CSS 的机制不同**
   （modena 的函数在原生下整条丢弃）。这类问题的判据只能是**原生包真机观感**。
2. **属性 API 的覆盖范围要与「外观要素」对齐**：背景、文字都兜了，**边框漏了** —— 补兜底时要按
   「这个控件靠哪几样东西被看见」逐项清点（底色 / 文字 / 边框 / 焦点环）。
3. **把口径写进注释**：`paintBorder` 的注释里写清了「为什么走属性 API」「缺省值为何是 1 / 3」
   「没写边框就不动」，下次改动不必重新推理。

## 7. 涉及文件

| 类型 | 路径 |
|---|---|
| 修改 | `aha-desktop/.../view/ThemePaint.java`（`paintBorder` + `number` + `apply` 调用 + 导入 `Border` / `BorderStroke` / `BorderStrokeStyle` / `BorderWidths`） |
| 修改 | `aha-desktop/.../view/DesktopShell.java`（`composer` 与 `sessionSearch` 的内联样式补 `-fx-border-color`） |
| 修改 | `aha-desktop/.../view/LogPanel.java`（文本域改用 `themedForever` 并显式给底色 / 文字 / 轮廓线） |
| 修改 | `aha-desktop/src/test/java/.../AhaDesktopSmokeTest.java`（边框断言 + `borderColor` 辅助） |

## 结论分层

### 已观测事实

- ACANX 真机：**原生镜像 exe** 里切主题后输入框轮廓线丢失；JVM 模式正常；
- 代码事实：`wireComposer` 的内联样式里**没有**任何 `-fx-border-*`；
- 代码事实：`ThemePaint.apply` 此前只同步文字色与背景色，**从不同步边框**；
- 样式表事实：`.text-input` / `.text-area` 那组规则确实给了 `-fx-border-color`（纯字面量）；
- 同族事实：原生镜像下 modena 的 `derive()` / `linear-gradient()` 会让**整条声明被丢弃**（`TS-2610-029`）。

### 机制 / 行为模型

- JavaFX 的可见外观由两类来源叠加：**CSS**（含默认的 modena 表）与**属性 API**（`setBackground` / `setBorder` / …）；
- 原生镜像里 modena 的 CSS 被削（函数失效 ⇒ 声明丢弃），于是「默认外观」这一层不存在了；
- 因此凡是**只靠 modena 画出**的外观要素（输入框边、焦点环、滚动条细节），在原生下都必须由**补丁样式表**或
  **属性 API** 重新提供 —— 而补丁表对复合控件的内部节点仍不可靠，最终落点还是属性 API。

### 未定位 / 未复现

- **未在本机复现**（无界面环境 + 需要原生 exe）：本轮结论依据是真机反馈 + 代码/样式表事实 + 同族档案；
- **未逐一清点**其它「只靠 modena 画出」的外观要素：焦点环、滚动条滑块、下拉箭头的渐变等，
  可能同样在原生 exe 里缺失（本轮只修了用户报的输入框轮廓线）；
- **未验证**其它输入类控件（补全弹层、对话框里的输入框）在原生 exe 下的轮廓线。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 内联样式缺边框 | 读 `DesktopShell.wireComposer()`（修复前） | 只有 `-fx-control-inner-background` / `-fx-background-color` / `-fx-text-fill` / `-fx-prompt-text-fill` |
| `apply` 覆盖面 | `grep -n "value(style" ThemePaint.java`（修复前） | 命中 `-fx-text-fill`、`-fx-background-color`、`-fx-control-inner-background`；**无** `-fx-border-*` |
| 样式表里的边框 | `grep -n "fx-border" aha-theme-dark.css` | `.text-input, .text-field, .text-area … { -fx-border-color: #3A3A3A; -fx-border-radius: 3 }` |
| 本地检查 | 五道 `bin/Check*.py` | ✅ 全绿 |
| 运行期断言 | CI `ui-smoke` 新增的 `borderColor(...)` 断言 | 见 PR 运行记录（JVM + Xvfb） |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「色表没切 / 补丁表没换」 | 同一界面里其余控件的颜色都跟着主题变；`ThemePaint.install` 整份替换样式表 |
| 「属性 API 兜底把边框抹掉了」 | `paint()` 只调 `setBackground`，**不碰** `Border`；`apply` 此前根本没设过边框 |
| 「JVM 下也丢，只是没注意」 | ACANX 明确：只有原生镜像 exe 复现；且 JVM 下 modena 正常解析，边由它提供 |
| 「只是颜色太接近看不出来」 | 用户反馈是「轮廓线**丢失**」；而修复前后 `Region.getBorder()` 也有无之别（断言可判） |

## 未决问题

**Q1 原生 exe 真机复验**

需要 ACANX 在原生包上：切换主题（亮 ↔ 暗）后确认输入框仍有轮廓线，且会话搜索框、日志面板同样有。
若仍有某个控件没有，按本档案 §4 的模式给它的内联样式补 `-fx-border-color` 即可（属性 API 会自动落）。

**Q2 其它只靠 modena 的外观要素**

焦点环、滚动条滑块、下拉箭头等在原生 exe 下是否完整，本轮未清点。建议下次真机走查时顺手记录
（可作为一条独立的「原生外观要素清点」任务）。
