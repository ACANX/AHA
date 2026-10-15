# TS-2610-038 · 切主题后输入框 / 对话框组件的颜色不跟着变

**日期**：2026-10-14
**关联 Issue**：issue [#153](https://github.com/ACANX/AHA/issues/153)（主题色口径；本轮是切换路径的缺口）
**关联档案**：`TS-2610-035`（底色角色拆分）、`TS-2610-025` / `TS-2610-026`（主题不跟随 / 暗色控件未适配）
**关联设计**：`Docs/Design/DesktopDesign.md` §11.6

---

## 1. 现象

ACANX 真机实测（2026-10-14）：**切换主题前后**，输入框（`aha.composer`）的底色**不跟着切**，
仍是切换前那个主题的颜色；「供应商」「日志」「工具」三类界面里**部分组件**的背景色同样如此。

初始渲染是对的（启动时就是当前主题的颜色）——问题只出在**切换**这一步，而且**重开对话框也一样**
（这一点是后面区分两类原因的关键）。

## 2. 排查过程

先看机制：颜色是**内联在样式串里**的（`Palette` 常量 → `ThemePaint.themed` → `node.setStyle`），
因此换色表（`Palette.setTheme`）**不会自动影响已建节点**。为此主窗口有一张登记表
`DesktopShell.restylers`：每个走 `themed(node, supplier)` 的节点都留下「怎么重新上样式」，
`applyTheme()` 时整屏重刷。

逐个核对「谁没被刷到」，查到**四类漏洞**（并存，不是单一原因）：

| # | 漏洞 | 取证 |
|---|---|---|
| A | **`static final String` 样式常量**：常量在**类加载时**求值，颜色被冻在那一刻 | `ProviderDialog.SECTION_TITLE_STYLE`、`SettingsDialog.MUTED_STYLE` / `NOTE_STYLE`、`ApprovalDialog.SECTION_STYLE` —— 这不是「没重刷」，而是**重刷也拿不到新值**（重开对话框仍是旧色） |
| B | **对话框内容用字符串一次性上色**：`ThemePaint.themed(root, Palette.dialogTheme())` —— 传的是**已求值的字符串**，不是「来源」 | `ProviderDialog` 的 root / content / scroll 三处 |
| C | **打开中的对话框场景不换样式表**：`install` 只装在主窗口场景上 | 主窗口之外还有独立 `Scene`（对话框 / 面板），切换时没人通知它们 |
| D | **`ThemePaint.apply()` 的 API 兜底缺 `-fx-control-inner-background`** | 该属性是输入类控件的底色来源（modena 的 `.text-input` 也用它）；缺了它，输入框在原生镜像下只剩 `setStyle` 一条路（而类注释早已写明这条路对 `TextArea` 的 `.content` 不可靠） |

## 3. 根因

一句话：**主窗口之外、以及「一次性上色」的路径，都不在重刷范围内。**

- 主窗口那一棵树有 `restylers` 兜着；对话框 / 面板 / 弹层**不在那棵树上**，
  它们自己也不会登记 ⇒ 换主题时它们停在旧配色（漏洞 B / C）；
- 更隐蔽的是漏洞 A：`static final` 常量把旧色**写死在类里**，
  连「重开一个对话框」都拿不到新颜色——所以「重开看看」这一步能把 A 和 C 区分开；
- 漏洞 D 让输入框类控件的兜底失效：`setStyle` 在原生镜像下未必传到 `TextArea` 的 `.content`，
  而 `apply()` 又不认 `-fx-control-inner-background` ⇒ API 侧也没有兜住。

## 4. 修复

| 环节 | 改动 |
|---|---|
| 通用重刷机制（`ThemePaint`） | 新增 `onThemeChange(listener)`、`refreshAll()`、对话框面板登记表（**弱引用**，窗口关掉自动消失）、`themedForever(node, supplier)`（= 立即上色 + 登记重刷，语义与 `DesktopShell.themed` 一致） |
| 主窗口（`DesktopShell.applyTheme`） | 重刷自己的 `restylers` 之后，调 `ThemePaint.refreshAll()`：把还开着的对话框重套主题 + 跑一遍登记过的重刷 |
| 漏洞 A | 4 个静态常量改成**按需求值**（方法 / lambda）：`ApprovalDialog.sectionStyle()`、`ProviderDialog.sectionTitleStyle()`、`SettingsDialog` 的 5 处、`LogPanel.stats` |
| 漏洞 B / C | `ProviderDialog` 三处与对话框内容改为 `themedForever(...)`；所有对话框经 `ThemePaint.dialog(pane)` 自动进登记表，切换时重算 `dialogTheme()` 并重装场景样式表 |
| 漏洞 D | `ThemePaint.apply()` 在没有 `-fx-background-color` 时也读 `-fx-control-inner-background` 并落到 `Region#setBackground` |
| 机器守卫（`bin/CheckTheme.py` 第 6 项） | **禁止** `static final String` 里出现 `Palette.`（逐文件解析初始化文本，跳过字符串字面量与文本块）——「写入规范而无人执行等于没写」 |

## 5. 验证

| 项 | 做法 | 结果 |
|---|---|---|
| 四道静态检查 | `CheckDocs` / `CheckSkills` / `CheckScripts` / `CheckTheme` | ✅ 全绿（`CheckTheme` 新提示语含「无静态常量冻色」） |
| 守卫反向验证 | 把 `LogPanel` 的 `stats` 样式改回 `static final BAD_STYLE = "…" + Palette.MUTED + "…"` | ❌ 如期失败：`LogPanel.java:69 的静态常量 BAD_STYLE 引用了 Palette（…）——常量在类加载时求值…`；还原后恢复 ✅ |
| 全仓核查 | 同逻辑扫 `aha-desktop/src/main/java/**/*.java` | ✅ 已无「静态常量 + `Palette.`」 |
| 编译与 GUI 冒烟 | CI 的 `ui-smoke`（GUI + Xvfb）+ `build` | 见 PR 运行记录 |
| **切换后观感** | 真机点「设置 → 主题」再对照输入框 / 三个对话框 | ⏳ 需 ACANX（本机无界面环境） |

## 6. 教训与沉淀

1. **「一次性上色」就是定时炸弹**：凡是与主题相关的颜色，要么来自 CSS（整份替换），
   要么**登记重刷**（`themed` / `themedForever`）；没有第三条路；
2. **主题色与 `static final` 互斥**：常量在类加载时求值 ⇒ 天然与「可切换」矛盾。
   现在这条由机器守卫拦住，不再依赖记忆；
3. **「重开一次」能区分两类原因**：重开还是旧色 ⇒ 被冻住（漏洞 A）；
   重开正常、开着切不对 ⇒ 没重刷（漏洞 B / C）。这次两类同时存在，
   只看现象会误判成「同一个 bug」；
4. **补丁表 ≠ 全量刷**：场景级（`Scene`）的样式表安装要跟到**每一个窗口**，
   不只是主窗口——对话框有自己的场景根。

## 7. 涉及文件

| 类型 | 路径 |
|---|---|
| 修改 | `aha-desktop/.../view/ThemePaint.java`（登记表 + `refreshAll` + `themedForever` + `-fx-control-inner-background` 兜底） |
| 修改 | `aha-desktop/.../view/DesktopShell.java`（`applyTheme` 调 `refreshAll`） |
| 修改 | `aha-desktop/.../view/ApprovalDialog.java`、`ProviderDialog.java`、`SettingsDialog.java`、`LogPanel.java`（静态常量 → 按需求值 / 登记重刷） |
| 修改 | `bin/CheckTheme.py`（第 6 项守卫） |

## 结论分层

### 已观测事实

- 启动时颜色正确、**切换后**不跟着变（真机反馈）；
- 重开对话框**仍然是旧色** ⇒ 至少存在「值被冻结」一类原因；
- `static final String` 常量在类加载时求值（Java 语义），因此必然冻结当时的 `Palette` 值；
- 全仓扫描（本轮）确认：`ProviderDialog` / `SettingsDialog` / `ApprovalDialog` 各有此类常量；
- 主窗口有 `restylers` 登记表，而对话框 / 面板 / 弹层**不在**该表的覆盖范围内。

### 机制 / 行为模型

- 颜色走「内联样式串」而非外部 CSS ⇒ 换色表不自动影响已建节点 ⇒ 必须有**显式的重刷动作**；
- 重刷有两级：**值**（从哪里取色）与**时机**（什么时候重新取）。本轮两类漏洞分别对应这两级；
- `install(Scene)` 决定**样式表**层面的主题，作用域是「每个场景」，不是「整个应用」。

### 未定位 / 未复现

- **未在本机复现**：无界面环境，无法观察切换前后像素；结论依据是代码路径与真机反馈；
- **未验证**：修复后真机的切换观感（输入框 + 三个对话框），以及原生镜像（native）下
  `-fx-control-inner-background` 兜底是否真的覆盖到 `TextArea` 的 `.content`——
  这一条只能由真机确认（本轮的兜底是在控件自身 `Region` 上设背景）；
- **未逐一核对**：是否还有第五类「一次性上色」路径（例如第三方控件内部的 CSS 查表）——
  本轮以「静态常量」+「对话框内容」+「场景安装」三类为准。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 静态常量冻结 | Java 语义（类加载时求值）+ 代码位置 | `ProviderDialog.java:71`、`SettingsDialog.java:52` / `55`、`ApprovalDialog.java:62`（修复前的行号） |
| 修复前全仓「静态常量 + Palette」 | 自写解析脚本（跳过字符串 / 文本块） | 命中上述 4 个常量 |
| 修复后同项扫描 | 同上 | **0** |
| 守卫反向验证 | 注入 `BAD_STYLE` 后 `python3 bin/CheckTheme.py` | `❌ LogPanel.java:69 的静态常量 BAD_STYLE 引用了 Palette…`（退出码 1） |
| 一次性字符串应用点 | `grep "ThemePaint.themed(.*Palette.dialogTheme()"` | 修复前 `ProviderDialog` 3 处 |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「色表没切」（`Palette.setTheme` 没生效） | `applyTheme` 里会记日志打印生效主题与实际色值；且初始渲染正确 ⇒ 色表本身是对的 |
| 「补丁样式表没换」 | `applyTheme` 会在主场景上整份替换；主窗口控件跟随正常 |
| 「同一类原因」 | 重开对话框仍旧色（值被冻）与开着切不对（时机缺失）是两类，本轮同时存在 |
| 「只需重刷输入框」 | 同类路径在三个对话框里都存在；只修一个必然复发 |
| 「写成常量更省事」 | 常量与「可切换主题」在语义上互斥，已由 `bin/CheckTheme.py` 第 6 项挡住 |

## 未决问题

**Q1 真机确认切换观感**

现状：代码与守卫已落地；CI 只验证到「能编译 + GUI 起得来」。
阻塞点：需 ACANX 真机切换主题，逐项确认输入框 / 供应商 / 日志 / 工具四处。

**Q2 原生镜像下输入框内层 `.content` 是否被兜底覆盖**

现状：本轮的 API 兜底设在控件自身（`Region#setBackground`）；
而 `TextArea` 的可见底色来自皮肤内部节点（`.content` / `.viewport`，由 CSS 补丁表覆盖）。
阻塞点：需要原生镜像真机确认；若仍不一致，下一步是给输入类控件补一条皮肤内部的兜底（属新工作）。

### 后续（2026-10-15）：ACANX 复现「#153 没修好」—— 定位到第五类，并补上那条兜底

ACANX 反馈：**这些区域的背景颜色在切换主题前后没变**，而**重启就正常**，只在切换时暴露。
按本文件「判别法」（重开对话框还是旧色 ⇒ 值被冻住；重开正常、开着切不对 ⇒ 时机缺失）复核：
**两者都不是** —— 是第五类：

| # | 漏洞 | 取证 |
|---|---|---|
| E | **可见底色不是控件本体画的，而是皮肤内部节点画的**：`TextArea` 的 `.content`、`ScrollPane` 的 `.viewport` / `.corner`。而 `ThemePaint.themed()` 只作用于**控件本体**（`setStyle` + 属性 API），内部节点此前**只依赖补丁样式表** | `grep -rn 'lookup(".content")\|.viewport\|skinProperty' view/*.java` ⇒ **0 处**；两张表里对应的规则确实是内部选择器（`.text-area > .content`、`.scroll-pane > .viewport`）|

**为什么「重启正常、切换才错」**：启动时样式表按当时主题装载、本体色按当时色表求值 ⇒ 两边一致；
切换时**本体被 `restylers` 重刷（新色）**，而内部基面只能等样式表重算 —— 在 CSS 传导不可靠的环境
（原生镜像，本文件与 `TS-2610-028` 都记着）下它不重算 ⇒ 内部基面停在旧色；重启则整棵重建 ⇒ 又对了。

**为什么套件是绿的**：`AhaDesktopSmokeTest` 那条断言只看控件本体的 `getStyle()`（本体确实被重刷），
**内部节点画出来的颜色它看不见** ⇒ 「绿而仍错」。

**修法**：`ThemePaint` 新增 `SKIN_SURFACE_SELECTORS`（`.content` / `.viewport` / `.corner`）与
`applySkinSurfaces()` —— 每次上色时把**同一份底色**用属性 API 再写到内部基面上
（延后一帧 + 先 `applyCss()`，因为皮肤那时才建好、`lookup` 也只在 CSS 处理之后有效）；
同时把 `install()` 的样式表清理从「按 URL 字符串删另一份」改为**按文件名**删掉任何非当前主题的补丁表
（URL 形式不一致时删不掉会留下两份，切回旧主题获胜的是旧那份）。
回归断言加在冒烟测试里：切亮/暗后直接读 `.content` 的实际背景色，与 `Palette.CONTROL_INNER` 比对。

**Q3 是否还有其他「一次性上色」路径**

现状：本轮覆盖静态常量、对话框内容、场景安装三类。
阻塞点：需要一次「所有颜色写入点」的普查（把 `setStyle` 的每个调用点按「是否登记重刷」分类），
可考虑做成静态检查的下一条规则。
