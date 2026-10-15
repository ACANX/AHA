# TS-2610-045 · 下拉列表里当前那一项「看不见，也没完全隐藏」：选中态背景被基面规则压掉

> 事件日期：2026-10-15
> 作者：@cnxnc
> 关联 Issue：[#227](https://github.com/ACANX/AHA/issues/227)（从亮色切到暗色时，下拉列表里「亮色」选项不可见）
> 关联记录：同族 `TS-2610-025` / `026` / `028` / `029` / `031` / `035` / `038`（主题一族的历次排查）

## 1. 现象

设置对话框里展开「主题」下拉列表（`ComboBox`），**当前生效的那一项是空白** —— 看不见，但也没完全隐藏
（ACANX 真机截图：三个选项里，中间那一行只有背景、没有文字）。列表里其余两项正常。

截图里的关键信息：**下拉列表处于亮色主题**（对话框正文是浅色），当前值是「亮色」，
而空白的那一行恰好就是「亮色」——也就是**被选中**的那一行。

## 2. 排查过程

| 步 | 动作 | 结论 |
|---|---|---|
| 1 | 读 `Theme` 枚举与 ComboBox 的填充 | 三个常量 `DARK("暗色")` / `LIGHT("亮色")` / `SYSTEM("跟随系统")`，`theme.getItems().setAll(Theme.values())`、`setConverter(…label())` —— **没有空项**，列表里的项数是 3 |
| 2 | 对照截图数项 | 按钮区显示「亮色」（= 当前值），弹出层里是「暗色 / 空白 / 跟随系统」⇒ 空白那行 = `LIGHT` = **当前值 = 被选中项** |
| 3 | 读两份补丁表的选择器 | 基面规则 `.list-view > .virtual-flow > .clipped-container > .sheet > .list-cell`（**5 个类**）设了 `-fx-background-color: %CONTROL_INNER%` |
| 4 | 找选中态规则 | `.list-cell:filled:selected`（**1 类 + 2 伪类**）设了 `-fx-background-color: %FOCUS_BORDER%`（亮色 `#6A3FD0`）、`-fx-text-fill: %ON_ACCENT%` |
| 5 | 比特异性 | 5 个类 > 1 类 + 2 伪类 ⇒ **选中态的紫色背景永远盖不上去**，行背景停在基面底色 |
| 6 | 找文字色为何生效 | 文件末尾还有一条 `.list-cell:filled:selected .text { -fx-fill: %ON_ACCENT% }`（1 类 + 2 伪类 + **1 类** = 4）——它压得过 `.text { -fx-fill: %FOREGROUND% }`（1）⇒ **文字确实是白的** |
| 7 | 合起来看 | 亮色主题下：**背景 = 基面 `#FFFFFF`（白）+ 文字 = `%ON_ACCENT%` `#FFFFFF`（白）** ⇒ 白底白字 ⇒ 「看不见，也没完全隐藏」 |
| 8 | 问「为什么只有下拉列表暴露」 | 会话列表的单元格**在 Java 里显式画背景与文字色**（`DesktopShell.SessionCell` 用 `ThemePaint.themed`），根本不过 CSS；下拉列表是第一个「颜色完全交给样式表」的 `ListView` |

## 3. 根因

**CSS 特异性失衡：状态规则被基面规则压掉，而状态相关的文字色却生效。**

- 背景：基面 5 个类（胜） vs `.list-cell:filled:selected` 1 类 + 2 伪类（负）⇒ 选中行背景不变；
- 文字：`.list-cell:filled:selected .text` 4（胜） vs `.text` 1（负）⇒ 选中行文字变白；
- 二者相乘 ⇒ 在**基面底色与 `ON_ACCENT` 相同的主题**下，选中项与背景同色，肉眼即「空白」。

亮色主题的基面底色是 `#FFFFFF`，`ON_ACCENT` 也是 `#FFFFFF`，因此**亮色下必然复现**；
暗色主题的基面是 `#1E1E1E`，白字尚可辨 —— 但**这不能算「暗色下是好的」**：
选中行同样没有拿到强调色背景，只是碰巧没到「看不清」的程度。

## 4. 修复

| 环节 | 改动 |
|---|---|
| 两份主题模板（唯一色源） | 三条状态规则（`:filled:hover` / `:filled:selected`）**加上与基面完全相同的选择器前缀**，靠「同特异性时后者胜」拿到背景覆盖权；另加一层 `.combo-box-popup > .list-view > …`（6 个类）给弹出层留余量。原有的通用规则**保留**，供不在 virtual-flow 里的单元格（如 ComboBox 按钮格）继续使用 |
| 生成物 | `python3 bin/GenThemeCss.py` 重新生成两份 CSS（模板 ⇒ 生成物，逐字节可重现） |
| 机器守卫 | `bin/CheckTheme.py` 新增第 8 项 `check_state_rules_not_shadowed()`：对**每一条**「设了背景色的基面规则」，要求存在一条**类路径完全相同**、也设了 `-fx-background-color` 的状态规则 |
| 运行期断言 | `AhaDesktopSmokeTest#listSelectionSurfaceIsReadableInBothThemes`：真开一个 `ListView`，读**选中格实际画出来的背景色**与它里面 `.text` 的填充色，断言分别是 `FOCUS_BORDER` 与 `ON_ACCENT`，且两者**不相同**（亮 / 暗各验一次） |

## 5. 验证

| 项 | 做法 | 结果 |
|---|---|---|
| 四道静态检查 | `CheckDocs` / `CheckSkills` / `CheckScripts` / `CheckTheme` | ✅ 全绿 |
| 生成物一致性 | `python3 bin/GenThemeCss.py --check` | ✅ 两份 CSS 均可由模板 + `Palette` 逐字节重现 |
| 两份表对称性 | 选择器集合 / 属性集合 / 色号来源三项 | ✅ 0 差异（新增选择器两份都加了） |
| 守卫反向验证 | ① 删掉同路径状态规则（模拟修前）；② 只留弹出层那条更深的规则 | ❌ ① 报 2 处、② 报 1 处，均为如期失败；还原后 ✅ |
| GUI 冒烟 | CI 的 `ui-smoke`（GUI + Xvfb，含本轮新增断言） | 见 PR 运行记录 |
| **真机观感** | 真机展开主题下拉列表，亮 / 暗各看一次 | ⏳ 需 ACANX（本机无界面环境） |

## 6. 教训与沉淀

1. **「文字色生效」不等于「这一行的配色对」**：背景与文字来自**两条不同特异性的规则**时，
   只看其中一条会得出「样式写对了」的错误结论。判据必须落到**画出来的两个颜色不相同**。
2. **特异性是隐性契约**：`> ` 链越长越强，而「更短的选择器更通用」这种直觉在覆盖场景下正好相反。
   现在由守卫强制「状态规则必须与基面同路径」。
3. **手写上色会掩盖样式表的问题**：会话列表因为用 `ThemePaint.themed` 显式上色，
   一直没暴露这条 CSS 缺陷；凡是**完全交给 CSS** 的控件，都要按最坏情况检查特异性。
4. **修一处要问「同类还有几处」**：本轮把「每一条基面规则」都纳入检查，而不是只补 ComboBox 那一条。

## 7. 涉及文件

| 类型 | 路径 |
|---|---|
| 修改 | `aha-desktop/src/theme/aha-theme-dark.template.css`、`aha-theme-light.template.css`（同路径状态规则 + 弹出层规则） |
| 生成 | `aha-desktop/src/main/resources/.../view/css/aha-theme-{dark,light}.css`（由 `bin/GenThemeCss.py` 产出，不手改） |
| 修改 | `bin/CheckTheme.py`（新增第 8 项守卫 `check_state_rules_not_shadowed`） |
| 修改 | `aha-desktop/src/test/java/.../AhaDesktopSmokeTest.java`（新增选中行配色断言 + 5 个读取辅助函数） |

## 结论分层

### 已观测事实

- 真机截图：ComboBox 展开后，**当前值那一行只有背景、没有文字**，其余两项正常；
- 三份客观读数：基面规则 5 个类、`.list-cell:filled:selected` 1 类 + 2 伪类、
  `.list-cell:filled:selected .text` 4 —— 背景与文字的特异性**一负一胜**；
- 亮色主题的基面底色与 `ON_ACCENT` **同为 `#FFFFFF`**（`Palette.java` 可查）；
- 会话列表单元格的颜色走 `ThemePaint.themed`（Java 显式上色），不经过 CSS。

### 机制 / 行为模型

- JavaFX CSS 按**特异性**决定同一属性谁生效；特异性相同才轮到**书写顺序**（后者胜）；
- 一条规则里并列多个选择器（逗号）时，**每条各自计算**特异性，不叠加；
- `Labeled` 的可见文字是皮肤内部的 `Text` 节点，吃 `-fx-text-fill`（经皮肤绑定）或直接的
  `.text { -fx-fill }` —— 后者是**另一条规则**，可以独立胜出（本 bug 的关键）。

### 未定位 / 未复现

- **未在本机复现**（无界面环境）：结论依据是真机截图 + 两份样式表 + JavaFX CSS 语义；
- **未逐一体检**：其它「完全交给 CSS」的控件（`TreeView` / `TableView` 的选中态、
  `Menu` 的 hover、`Tab` 的选中）是否也存在同类的状态覆盖问题 —— 本轮守卫只覆盖
  `.list-cell` / `.tree-cell` 的基面与状态；
- **未验证原生镜像**：原生镜像下 CSS 传导历来有差异（同族 `TS-2610-028` / `029` / `031`），
  本轮修复是纯样式表改动，**需要真机在原生包上再确认一次**。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 基面规则 | `grep -n "virtual-flow" aha-theme-dark.css` | `.list-view > .virtual-flow > .clipped-container > .sheet > .list-cell`（5 个类） |
| 选中态背景规则 | 同上文件 | `.list-cell:filled:selected { -fx-background-color: #AF87FF }`（3） |
| 选中态文字规则 | 同上文件末尾 | `.list-cell:filled:selected .text { -fx-fill: #FFFFFF }`（4） |
| 亮色基面与 `ON_ACCENT` | `Palette.java` / `aha-theme-light.css` | `CONTROL_INNER = #FFFFFF`、`ON_ACCENT = #FFFFFF` ⇒ 白底白字 |
| 守卫反向验证 ① | 删掉同路径状态规则后 `python3 bin/CheckTheme.py` | `❌ 基面规则 '.list-view > …' （5 个类）没有对应的 ':filled:hover' 同路径规则…`（2 处） |
| 守卫反向验证 ② | 只保留 `.combo-box-popup > …` 那条深规则 | `❌ … 没有对应的 ':filled:selected' 同路径规则…`（1 处）⇒ 更深的规则不能代表普通列表 |
| 生成物一致性 | `python3 bin/GenThemeCss.py --check` | `✅ 两份文件都能由模板 + Palette 逐字节重现` |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「列表里真有个空项」 | `Theme` 只有 3 个常量，`setAll(Theme.values())` + 转换器都给非空文案 |
| 「色表没切 / 补丁表没换」 | 同一张表里其余两项颜色正常；`ThemePaint.install` 按文件名整份替换 |
| 「回到 modena 默认色」 | 空白行的**背景**是本主题的基面色（`#FFFFFF`），不是 modena 的白底默认样式 |
| 「只是暗色下才有的问题」 | 截图即亮色；亮色下基面与 `ON_ACCENT` 同为白 ⇒ 必然看不清；暗色只是「碰巧能辨」 |
| 「只影响 ComboBox」 | 同一 `.list-cell` 基面规则作用于所有 ListView / TreeView 的单元格，只是别处被 Java 显式上色掩盖了 |

## 未决问题

**Q1 真机复验（亮 / 暗各一次，原生包也要看）**

现状：修复与断言已落地，CI 只能验证到「能编译 + GUI 起得来（含新增断言的读数）」。
需要 ACANX 在真机上：展开主题下拉列表 → 确认当前项是**强调色底 + 白字**；
切一次主题（亮↔暗）后立刻再展开一次（顺带覆盖同族的切换问题），
并在**原生镜像包**上做同样的确认。
