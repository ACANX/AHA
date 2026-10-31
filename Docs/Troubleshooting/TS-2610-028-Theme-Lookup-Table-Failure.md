# TS-2610-028 · 原生桌面端主题残留：把隐式查表改成显式声明（#49）

## 1. 背景

#49 的修复随 PR #50 合入 `dev` 后，真机复验仍然不合格：

- **暗色**模式：左栏导航按钮 / 分组标题 / 菜单栏的文字仍是深色，暗底上看不清；
- **亮色**模式：供应商配置对话框的底色仍是深色 / 灰色，与亮色主界面不一致。

上一轮（DevLog-20261009-09）的结论是「`Palette.theme()` 缓存 + looked-up color 传播不到位」，
那一轮改掉了缓存、给几个控件显式套了主题。**这次说明还有别的成分**，因此本轮不再顺着旧结论
加代码，而是先**取证**。

## 2. 排障过程与修复链

### 2.1 先把"是不是 CSS 根本没打进镜像"排除掉

从真机装的那个包（`V0.1.1.00052-aha-desktop-native`）取 `-Report.zip`（即 native-image 的
build-report），核对资源表：

- `com/sun/javafx/scene/control/skin/modena/modena.css` **在资源表里**，来源
  `~/.m2/.../javafx-controls-25-win.jar`；
- 资源表里的 `missing`（✗）条目没有 CSS 主体（只有 `caspian/embedded.css` 这类无关项）。

**结论：CSS 资源已随镜像打包，问题不在"没打进去"。**

### 2.2 再用 JavaFX 探针把"代码本身对不对"证伪

开发机（WSL）里有 WSLg，可以直接跑 JavaFX 25，于是写了两个探针：

| 探针 | 做法 | 结果 |
| --- | --- | --- |
| 控件文字色 | 只给根节点套 `Palette.theme()` 的样式串 | 暗色下 Label `0xE4E4E4`、Button/Menu `0xFFFFFFFF`；亮色下 Label `0x1F1F1F`、Button/Menu `0x333333` —— **都对** |
| 对话框 | 复刻 `ProviderDialog` 的结构（`DialogPane` 与 `content` 都 `setStyle(theme())`） | `DialogPane` 底色暗色 `0x1E1E1E`、亮色 `0xF4F4F4`；内部 Label / Button 文字色也随之正确 —— **也对** |

**结论：在 JVM 上，现有代码是自洽的**；modena 的
`ladder(-fx-base, 亮 45% / 暗 46%)` 会按 `-fx-base` 正确推出文字色。
所以真机上的偏差来自**原生镜像**——而原生镜像里最不可靠的，恰恰是
**looked-up color 的查表与 `ladder()` 推导**这类"运行时算出来的颜色"。

### 2.3 真正的定案：真机截图的像素取证

issue #49 里那张 2560×1600 的原始截图是可以直接量的。用 JDK 的 `ImageIO` 取关键区域的主色 / 最深色：

| 区域 | 实测 | 判读 |
| --- | --- | --- |
| 左栏「＋ 新建会话」行 | 底 `#1E1E1E`，最深 `#000000` | **暗底近黑字** ✗ |
| 左栏搜索框 | 主色 `#FFFFFF` | **纯白底** ✗ |
| 底部输入区（`TextArea`） | 主色 `#FFFFFF` | **纯白底** ✗ |
| 中栏、底栏、菜单栏 | `#1E1E1E` | 正常 ✓ |

也就是说：色表里 `CONTROL_INNER` 是 `#252526`，控件却露出 `#FFFFFF`；
色表里 `FOREGROUND` 是 `#E4E4E4`，按钮文字却是 `#000000`。
而靠**真正的属性**取色的地方（中栏 / 底栏 / 菜单栏的显式 `-fx-background-color`）全部正常。

**结论（定案）：原生镜像里，行内样式中的 looked-up color 查表与 `ladder()` 推导不可靠，
直接的属性值可靠。** 修复方向由此确定：把关键颜色从「查表」改成「属性」。

### 2.4 修复链：先把颜色落到属性 API 上

**第一步（定案后的直接措施）**：新增 `ThemePaint`——把样式串里的 `-fx-text-fill` /
`-fx-background-color` **用属性 API 再设一遍**（`Labeled.setTextFill`、`Region.setBackground`）。
属性 API 不经过 CSS 引擎，因此不受原生镜像的继承 / 查表问题影响。
`DesktopShell.themed()` 与 `SessionCell` 全部改走它：左栏 7 个导航按钮（新建会话 / 供应商 /
工具 / 记忆 / 扩展 / 日志 / 设置）、分组标题「会话」、搜索框、输入框、状态栏、菜单栏背景一并覆盖。
7 个对话框共 8 处 `DialogPane` 也从 `setStyle(dialogTheme())` 改为
`ThemePaint.themed(…, dialogTheme())`。

**第二步**：其余颜色仍按「显式优于查表」处理：

1. **`Palette.theme()` 显式钉死文字类 looked-up color**：新增
   `-fx-text-base-color`、`-fx-text-background-color`、`-fx-focused-text-base-color`、
   `-fx-mark-color`、`-fx-focused-mark-color`、`-fx-selection-bar`、
   `-fx-selection-bar-non-focused`、`-fx-selection-bar-text`、`-fx-control-inner-background-alt`。
   这条之后，`Menu` / `Button` / `Label` 的文字色不再依赖 `ladder()` 的推导结果。
2. **新增 `Palette.dialogTheme()`** = `theme()` + 显式 `-fx-background-color`。
   `DialogPane` 的底色在 modena 里来自规则
   `.dialog-pane { -fx-background-color: -fx-background; }`——那是一次查表；现在直接把底色写进
   内联样式。**7 个对话框文件、8 处 `DialogPane` 全部改用它**
   （`ProviderDialog` 的内容区 / 滚动区 / 根容器也一并换成它）。
3. **`DesktopShell.applyTheme` 增加主题诊断日志**：
   `主题应用：请求 X → 生效 Y | BASE=… FOREGROUND=… CONTROL_INNER=… | 根节点 …`。
   开发机复现不了原生镜像的行为，只能把现场值写进日志——真机跑一次就能判断是
   「色表没换」还是「节点没刷」。
4. **输入类控件显式给背景属性**：`search`（`TextField`）与 `composer`（`TextArea`）在
   `-fx-control-inner-background` 之外，再显式设 `-fx-background-color`——像素取证里
   `TextArea` 露出的白底正是「查表值没传到 `.content`」。
5. **构建完成后幂等重刷一次**：`AhaDesktopApp` 在 `shell.buildRoot()` 之后补一次
   `shell.applyTheme(settings.theme())`，把「构建期用哪一版色表」这个不确定性抹掉。
6. **顺带补一处漏合并**：状态栏版本标签改用 `AppVersion.buildVersion()`。
   该提交（`9503caf`）是在 PR #52 合并**之后**才推到已合并分支的，没进 `dev`——
   与 #49 当时踩的是同一个坑（见第 4 节）。

## 3. 最终验证结果

- **本地探针**（JavaFX 25，真实渲染）：亮 / 暗两套下 `Label` / `Button` / `Menu` / `DialogPane`
  的底色与文字色全部正确；
- **像素取证**：定案的直接依据（见 2.3），并确认修复方向是「属性 API」而非「再拼一遍 CSS」；
- **`PaletteTest` 新增两条守卫**：`theme()` 必须含显式文字色；`dialogTheme()` 必须含显式底色
  —— 以后谁把"显式"改回"查表"，测试会拦住；
- `python3 bin/CheckDocs.py` 通过；编译与全量测试交由持续集成；
- **真机验收标准**（合入 `dev` 出新包后）：
  1. 暗色下左栏导航按钮 / 分组标题 / 菜单栏文字为**浅色**；
  2. 亮色下供应商对话框底色为**浅色**、文字为**深色**；
  3. 亮 ↔ 暗来回切换后再打开对话框，底色与主界面一致；
  4. 日志里「主题应用」一行的 `BASE` / `FOREGROUND` 与界面所见一致。

## 4. 关键教训

1. **先证伪，再改代码**：本轮第一步不是改代码，而是用「构建报告排除资源问题 + JVM 探针排除
   代码问题」把范围收到"原生镜像特性"上。上一轮直接把现象归因给"缓存"，方向对了一半，
   但没把边界划清，于是同一条 issue 反复开门。
2. **looked-up color / `ladder()` 属"隐式颜色"，原生镜像下不完全可靠**：凡是重要到用户会截图来
   反馈的颜色，一律显式声明。代价是样式串长几行，收益是行为不再依赖运行时的推导。
3. **修复要留现场证据**：`applyTheme` 那行诊断日志是刻意留的——下次再有人说"颜色不对"，
   日志能直接回答"色表换没换"，不用再让用户口头描述。
4. **合并之后不要再往旧分支推**：`9503caf`（状态栏构建号）就是这么丢的。
   凡是「合并后才发现要补」的改动，一律**开新分支、走新 PR**。

## 5. 涉及文件清单

- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/ThemePaint.java`（新增）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/Palette.java`
  （显式文字色 + `dialogTheme()`）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/DesktopShell.java`
  （`applyTheme` 诊断日志；状态栏 `buildVersion()`；两处 `DialogPane` 改用 `dialogTheme()`；
  搜索框 / 输入框补显式 `-fx-background-color`）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/AhaDesktopApp.java`
  （`buildRoot()` 之后幂等重刷一次主题）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/ProviderDialog.java`
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/SettingsDialog.java`
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/LogPanel.java`
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/OutputDialog.java`
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/ApprovalDialog.java`
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/ToolListDialog.java`
- `aha-desktop/src/test/java/com/acanx/module/aha/desktop/view/PaletteTest.java`
- `CHANGELOG.md`、`Docs/TODO.md`、本文件

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- #49 的修复随 PR #50 合入 `dev` 后，**真机复验仍然不合格**：
  - **暗色**模式：左栏导航按钮 / 分组标题 / 菜单栏的文字仍是深色，暗底上看不清；
  - **亮色**模式：供应商配置对话框的底色仍是深色 / 灰色，与亮色主界面不一致。
- **排除「CSS 根本没打进镜像」**：从真机装的那个包（`V0.1.1.00052-aha-desktop-native`）
  取 `-Report.zip`（native-image 的 build-report）核对资源表 ⇒
  `com/sun/javafx/scene/control/skin/modena/modena.css` **在资源表里**
  （来源 `~/.m2/.../javafx-controls-25-win.jar`）；资源表里 `missing`（✗）条目**没有 CSS 主体**
  （只有 `caspian/embedded.css` 这类无关项）。
- **用 JavaFX 探针证伪「代码本身」**（开发机 WSL 有 WSLg，可直接跑 JavaFX 25）：

  | 探针 | 做法 | 结果 |
  | --- | --- | --- |
  | 控件文字色 | 只给根节点套 `Palette.theme()` 的样式串 | 暗色 Label `0xE4E4E4`、Button/Menu `0xFFFFFFFF`；亮色 Label `0x1F1F1F`、Button/Menu `0x333333` —— **都对** |
  | 对话框 | 复刻 `ProviderDialog` 结构（`DialogPane` 与 `content` 都 `setStyle(theme())`） | `DialogPane` 暗色 `0x1E1E1E`、亮色 `0xF4F4F4`，内部 Label / Button 文字色也随之正确 —— **也对** |

  ⇒ **在 JVM 上，现有代码是自洽的**（modena 的 `ladder(-fx-base, 亮 45% / 暗 46%)`
  会按 `-fx-base` 正确推出文字色），偏差来自**原生镜像**。
- **定案——真机截图的像素取证**（issue #49 那张 **2560×1600** 原始截图可直接量，
  用 JDK 的 `ImageIO` 取关键区域的主色 / 最深色）：

  | 区域 | 实测 | 判读 |
  | --- | --- | --- |
  | 左栏「＋ 新建会话」行 | 底 `#1E1E1E`，最深 `#000000` | **暗底近黑字** ✗ |
  | 左栏搜索框 | 主色 `#FFFFFF` | **纯白底** ✗ |
  | 底部输入区（`TextArea`） | 主色 `#FFFFFF` | **纯白底** ✗ |
  | 中栏、底栏、菜单栏 | `#1E1E1E` | 正常 ✓ |

  色表里 `CONTROL_INNER` 是 `#252526` 而控件露出 `#FFFFFF`；色表里 `FOREGROUND` 是 `#E4E4E4`
  而按钮文字是 `#000000`；而**靠真正的属性**取色的地方（中栏 / 底栏 / 菜单栏的
  **显式 `-fx-background-color`**）**全部正常**。
  ⇒ **定案：原生镜像里，行内样式中的 looked-up color 查表与 `ladder()` 推导不可靠，
  直接的属性值可靠。**
- 修复第一步：新增 **`ThemePaint`**——把样式串里的 `-fx-text-fill` / `-fx-background-color`
  **用属性 API 再设一遍**（`Labeled.setTextFill`、`Region.setBackground`）；
  `DesktopShell.themed()` 与 `SessionCell` 全部改走它（左栏 **7 个**导航按钮、分组标题「会话」、
  搜索框、输入框、状态栏、菜单栏背景）；**7 个对话框共 8 处** `DialogPane` 也改用它。
- 修复第二步：① **`Palette.theme()` 显式钉死文字类 looked-up color**——新增
  `-fx-text-base-color`、`-fx-text-background-color`、`-fx-focused-text-base-color`、`-fx-mark-color`、
  `-fx-focused-mark-color`、`-fx-selection-bar`、`-fx-selection-bar-non-focused`、
  `-fx-selection-bar-text`、`-fx-control-inner-background-alt`（此后 `Menu` / `Button` / `Label`
  的文字色**不再依赖 `ladder()`**）；② 新增 **`Palette.dialogTheme()`** = `theme()` +
  显式 `-fx-background-color`（modena 的 `.dialog-pane { -fx-background-color: -fx-background; }`
  就是**一次查表**）；③ `applyTheme` 增加**主题诊断日志**
  （`主题应用：请求 X → 生效 Y | BASE=… FOREGROUND=… CONTROL_INNER=… | 根节点 …`）；
  ④ 输入类控件**显式给背景属性**（`search` 与 `composer` 在 `-fx-control-inner-background` 之外
  再显式设 `-fx-background-color`）；⑤ `AhaDesktopApp` 在 `shell.buildRoot()` 之后
  **幂等重刷一次**主题；⑥ 顺带补一处**漏合并**（状态栏版本标签改用 `AppVersion.buildVersion()`，
  该提交 `9503caf` 是在 PR #52 合并**之后**才推到已合并分支的，**没进 `dev`**）。
- 验证：本地探针（JavaFX 25 真实渲染）亮 / 暗两套下 `Label` / `Button` / `Menu` / `DialogPane`
  的底色与文字色**全部正确**；`PaletteTest` 新增**两条守卫**
  （`theme()` 必须含显式文字色；`dialogTheme()` 必须含显式底色）；`CheckDocs.py` 通过；
  编译与全量测试交 CI；真机验收标准 **4 条**。

### 机制 / 行为模型

- **先证伪，再改代码**：本轮第一步不是改代码，而是用
  「**构建报告排除资源问题** + **JVM 探针排除代码问题**」把范围**收到「原生镜像特性」上**。
  上一轮直接把现象归因给「缓存」，方向对了一半，但**没把边界划清**，于是同一条 issue 反复开门。
- **looked-up color / `ladder()` 属「隐式颜色」，原生镜像下不完全可靠**：
  凡是**重要到用户会截图来反馈**的颜色，一律**显式声明**。代价是样式串长几行，
  收益是行为不再依赖运行时的推导。
- **修复要留现场证据**：`applyTheme` 那行诊断日志是**刻意留的**——
  下次再有人说「颜色不对」，日志能直接回答「色表换没换」，不用再让用户口头描述。
- **合并之后不要再往旧分支推**：`9503caf` 就是这么丢的。凡是「合并后才发现要补」的改动，
  一律**开新分支、走新 PR**。

### 未定位 / 未复现

- **真机验收当时未做**：4 条验收标准写在档案里，但要等「合入 `dev` 出新包」，
  ⇒ 修复效果在写作时**尚未确认**。
- **「属性 API 再设一遍」的覆盖范围未穷举**：本轮覆盖了左栏 7 个导航按钮、分组标题、搜索框、
  输入框、状态栏、菜单栏、7 个对话框的 8 处 `DialogPane`；
  其它控件的隐式颜色**未逐一核对**。
- **「合并后往旧分支推」的坑没有机器拦截**：只在教训里写明（第 4 条），无流程强制。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 真机复验不合格 | 真机（暗 / 亮两模式） | 暗色下左栏按钮 / 分组标题 / 菜单栏文字仍深色；亮色下供应商对话框底色仍深 |
| **排除资源问题** | 真机包的 `-Report.zip` 资源表 | `modena.css` **在表内**（来源 `javafx-controls-25-win.jar`）；`missing` 条目**无 CSS 主体** |
| **排除代码问题**（探针 1） | 只给根节点套主题 | 暗色 Label `0xE4E4E4`、Button/Menu `0xFFFFFFFF`；亮色 Label `0x1F1F1F`、Button/Menu `0x333333` —— **都对** |
| **排除代码问题**（探针 2） | 复刻 `ProviderDialog` 结构 | `DialogPane` 暗 `0x1E1E1E` / 亮 `0xF4F4F4`，内部文字色也正确 —— **都对** |
| **像素取证（定案）** | `ImageIO` 量 issue #49 的 2560×1600 截图 | 导航行底 `#1E1E1E` / 最深 `#000000`；搜索框 `#FFFFFF`；`TextArea` `#FFFFFF`；中栏 / 底栏 / 菜单栏 `#1E1E1E` |
| 色表 vs 实测的落差 | 对照 `Palette` | `CONTROL_INNER` = `#252526` 而控件露 `#FFFFFF`；`FOREGROUND` = `#E4E4E4` 而按钮文字 `#000000` |
| **关键对照** | 同上 | **显式 `-fx-background-color` 的地方全部正常** ⇒ 属性值可靠、查表不可靠 |
| 修复覆盖面 | `ThemePaint` 接入点 | 左栏 **7** 个导航按钮 + 分组标题 + 搜索框 + 输入框 + 状态栏 + 菜单栏；**7 个对话框的 8 处** `DialogPane` |
| 新增守卫 | `PaletteTest` | `theme()` 必含显式文字色；`dialogTheme()` 必含显式底色 |
| 诊断日志 | `DesktopShell.applyTheme` | `主题应用：请求 X → 生效 Y \| BASE=… FOREGROUND=… CONTROL_INNER=… \| 根节点 …` |
| 漏合并的证据 | git | 提交 `9503caf` 推到**已合并分支** ⇒ 未进 `dev` |
| **未验证** | — | 真机 4 条验收（待 `dev` 出新包） |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「CSS 没打进镜像」 | 真机包 build-report 的资源表里 `modena.css` **在册**，`missing` 无 CSS 主体（「2.1」） |
| 「代码写错了」（上一轮的结论方向） | JVM 探针实测亮 / 暗两套颜色**全对**，`ladder()` 推导正常（「2.2」） |
| 「缓存没删干净」（上一轮的结论） | 缓存上一轮已删；本轮**像素取证**才定位到真因是**查表不可靠**（「2.3」） |
| 「按截图描述猜颜色就行」 | 用 `ImageIO` **量**出具体色值（`#FFFFFF` / `#000000`），与色表对照才定案（「2.3」） |
| 「所有颜色都得显式声明」 | 定案的说法是「**重要到用户会截图反馈**的颜色一律显式」——不是无差别重写（「4、教训」第 2 条） |
| 「合并后直接往原分支补提交」 | `9503caf` 因此丢失 ⇒ 明确一律**开新分支、走新 PR**（「2.4」⑥、「4、教训」第 4 条） |

## 未决问题

**Q1 真机 4 条验收做了吗？**

现状：**当时未做**（等合入 `dev` 出新包）。4 条：① 暗色下左栏导航按钮 / 分组标题 / 菜单栏文字为**浅色**；
② 亮色下供应商对话框底色为**浅色**、文字为**深色**；③ 亮 ↔ 暗来回切换后再打开对话框，
底色与主界面一致；④ 日志里「主题应用」一行的 `BASE` / `FOREGROUND` 与界面所见一致。

**Q2 还有哪些控件依赖隐式颜色？**

现状：**未穷举**（只按截图暴露的区域覆盖）。
阻塞点：可用「临时改色表 → 断言所有关键节点色值都变」的冒烟测试来兜
（`PaletteTest` 现在只守「样式串里有显式色」，不守「每个控件都吃到」）。

**Q3 「合并后往旧分支推」能机器拦吗？**

现状：**没有拦截**，只有文字教训。
阻塞点：可考虑用分支保护 / 规则集限制「已合并 PR 的分支不接受新推送」
（见 `TS-2610-005` 的规则集现状），尚未评估。

**Q4 「原生镜像里 CSS 查表不可靠」的边界在哪？**

现状：定案基于**一次像素取证**（暗 / 亮两模式、若干区域）。
**哪些 looked-up color / 哪些 CSS 函数在原生下可靠**，没有系统清单。
阻塞点：需要在原生包上做一次「CSS 特性可用性」的小规模普查，尚未安排。
