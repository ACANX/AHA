# TS-2610-029 · 原生镜像下控件背景发黑（modena 的 derive / linear-gradient 失效）

- **日期**：2026-10-09
- **关联 Issue**：issue #49（第三轮）
- **关联 PR**：PR #57
- **分支**：`fix/issue-49-native-css-functions`
- **验证版本**：待 `DesktopNative.yml` 重出包

---

## 1. 背景

ACANX 在真机上报：**亮色 / 跟随系统模式下，文本输入框、「发送」按钮、会话列表的滚动条、
下拉选择框，背景全是黑的**；而且**无论切换哪个主题都是黑的**，属严重缺陷。

这一轮与前面的问题不同：前面是「颜色表没落到控件上」，这次是**这些控件连背景都没有**。

## 2. 排障过程与修复链

### 2.1 先看 modena 怎么给这些控件上色

从 `javafx-controls-25-linux.jar` 里解出 `com/sun/javafx/scene/control/skin/modena/modena.css`，
直接查这几类控件的规则：

```css
.text-input {
    -fx-text-fill: -fx-text-inner-color;
    -fx-background-color: linear-gradient(to bottom, derive(-fx-text-box-border, -10%), -fx-text-box-border),
                          linear-gradient(to bottom, derive(-fx-control-inner-background, -2%), ...);
}

.scroll-bar:vertical {
    -fx-background-color: derive(-fx-box-border,30%),
                          linear-gradient(to right, derive(-fx-base,-3%), derive(-fx-base,5%) 50%, derive(-fx-base,-3%));
}

.scroll-bar > .thumb {
    -fx-background-color: -fx-outer-border, -fx-inner-border, -fx-body-color;
}
```

其中 `-fx-inner-border` / `-fx-body-color` 在 modena 的 `.root` 里也是 `linear-gradient(...)`。

### 2.2 定案：CSS 函数失效 → **整条声明被丢弃**

GraalVM 原生镜像里，JavaFX CSS 引擎对 **`derive()` / `ladder()` / `linear-gradient()`** 的求值不可靠。
而 CSS 的规则是：**`-fx-background-color` 的值里只要有一个无法求值，整条声明就被丢弃**。

于是这些控件**根本没有背景**——不是「背景被设成了黑色」，而是**没有背景画上去**，
下面的内容透出来，看起来就是黑的。这也解释了 ACANX 观察到的两个特征：

- **只影响「一类组件」**：凡是在 modena 里靠 CSS 函数上色的（文本框 / 下拉框 / 按钮 / 滚动条）；
- **与主题切换无关**：这些规则本来就引用 `-fx-base` 等中间量，而不是主题色，
  所以换主题也修不好。

### 2.3 修复：一份纯字面量的补丁样式表

1. **新增 `aha-theme.css`**（`aha-desktop/src/main/resources/.../view/`）：
   把 `-fx-background-color` 全部改写成 **`-fx-aha-*` 字面量变量**，不使用任何 CSS 函数；
   覆盖输入类（`.text-input` / `.text-area` / `.combo-box-base` / `.choice-box`）、
   按钮类（`.button` 等）、滚动条（轨道 / 滑块 / 箭头 / 角）、滚动容器、列表树表格、
   下拉弹出层、菜单、分隔线、进度条。
   同时**显式把 `-fx-background-insets` 归零**——modena 的渐变规则常配多值 insets，
   只换颜色不改 insets 会画出被裁掉一角的色块。

2. **`Palette`**：新增语义色 `HOVER_BACKGROUND` / `SCROLL_TRACK` / `SCROLL_THUMB` / `ON_ACCENT`；
   `buildThemeStyle()` 里除了定义 `-fx-aha-*` 变量，还把 modena 依赖的中间量
   （`-fx-color`、`-fx-outer-border`、`-fx-inner-border`、`-fx-body-color`、`-fx-body-color-to-right`、
   `-fx-box-border`、`-fx-text-box-border`、`-fx-text-inner-color`、`-fx-focus-color`、
   `-fx-faint-focus-color`、`-fx-shadow-highlight-color`、`-fx-mark-highlight-color`、
   `-fx-hover-base`、`-fx-pressed-base`、`-fx-default-button`）**全部换成字面量纯色**。
   双保险：能救活的 modena 规则自然生效，救不活的由补丁表覆盖。

3. **`ThemePaint`**：新增 `install(Scene)`（幂等挂样式表）与 `dialog(DialogPane)`
   （对话框的 Scene 要等窗口建好才有，故挂一次 `sceneProperty` 监听补装）；
   9 处对话框调用点由 `themed(…, dialogTheme())` 改为 `dialog(…)`。

4. **`AhaDesktopApp`**：主场景创建后 `ThemePaint.install(scene)`。

### 2.4 为什么这样能彻底

- 补丁表里**没有任何 CSS 函数**，原生镜像下不存在「求值失败」这一环；
- 补丁表通过 `Scene.getStylesheets()` 加载，**排在 modena 之后**，同优先级下可以覆盖它；
- 节点自己的内联样式（`node.setStyle(...)`）优先级更高，已有主题登记不受影响。

## 3. 最终验证结果

- **新增 3 条守卫**（`PaletteTest`）：
  ① `theme()` 在亮 / 暗下都**不得含** `derive(` / `ladder(` / `linear-gradient(`；
  ② 亮色下 `CONTROL_INNER` / `SCROLL_TRACK` / `SCROLL_THUMB` 必须是浅色，且样式串含
     `-fx-aha-control-bg: #FFFFFF` / `-fx-text-box-border: #C8C8C8` / `-fx-body-color: #F4F4F4`；
  ③ `aha-theme.css` **必须在 classpath 上**，且内容同样不得含 CSS 函数
     ——这条同时防「资源忘了打包」与「有人又写回 derive/gradient」。
- 本地只跑 `CheckDocs.py`；编译与测试交 CI。
- **真机复验待新包**（本机无 Windows 图形环境）。

## 4. 关键教训

1. **「背景是黑的」不一定是「设了黑色」，很可能是「没有背景」** —— 排查时先问
   「这个控件在 modena 里是靠什么上色的」，而不是先怀疑色表。
2. **JavaFX 的 CSS 是「整条声明」粒度的**：一个值无效 → 整条 `-fx-*` 声明被丢弃。
   `-fx-background-color: a, b` 里 `b` 失败，`a` 也不会被画出来。
3. **原生镜像下「能用属性/字面量表达的，就不要用 CSS 函数」**。
   这条从「文字色」扩到了「背景色」，现在**扩到整个样式表**。
4. **修完要在正确的分支上**：这一轮的前两次尝试（PR #54 之后推的提交）因为推在已合并分支上
   而没进 `dev`，白跑一轮——见 `TS-2610-028-Theme-Lookup-Table-Failure.md` 的同名教训。

## 5. 涉及文件清单

- `aha-desktop/src/main/resources/com/acanx/module/aha/desktop/view/aha-theme.css`（新增）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/Palette.java`
  （新增 4 个语义色 + `-fx-aha-*` 变量 + modena 中间量字面量）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/ThemePaint.java`
  （`install(Scene)` / `dialog(DialogPane)`）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/AhaDesktopApp.java`（主场景挂表）
- 9 处对话框调用点：`ProviderDialog` / `SettingsDialog` / `LogPanel` / `OutputDialog` /
  `ApprovalDialog` / `ToolListDialog` / `DesktopShell`
- `aha-desktop/src/test/java/com/acanx/module/aha/desktop/view/PaletteTest.java`（新增 3 条守卫）
- `CHANGELOG.md`、`Docs/TODO.md`

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- ACANX 真机报（issue #49 第三轮）：**亮色 / 跟随系统模式下，文本输入框、「发送」按钮、
  会话列表的滚动条、下拉选择框的背景全是黑的**；而且**无论切换哪个主题都是黑的**。
- **modena 源码取证**（从 `javafx-controls-25-linux.jar` 解出 `modena.css`）：

  ```css
  .text-input {
      -fx-background-color: linear-gradient(to bottom, derive(-fx-text-box-border, -10%), -fx-text-box-border),
                            linear-gradient(to bottom, derive(-fx-control-inner-background, -2%), ...);
  }
  .scroll-bar:vertical {
      -fx-background-color: derive(-fx-box-border,30%),
                            linear-gradient(to right, derive(-fx-base,-3%), derive(-fx-base,5%) 50%, derive(-fx-base,-3%));
  }
  .scroll-bar > .thumb { -fx-background-color: -fx-outer-border, -fx-inner-border, -fx-body-color; }
  ```

  其中 `-fx-inner-border` / `-fx-body-color` 在 modena 的 `.root` 里**也是 `linear-gradient(...)`**。
- **两个特征与定案自洽**：
  - **只影响「一类组件」**——凡是在 modena 里靠 CSS 函数上色的（文本框 / 下拉框 / 按钮 / 滚动条）；
  - **与主题切换无关**——这些规则引用的是 `-fx-base` 等**中间量**，而不是主题色，所以换主题也修不好。
- 修复覆盖面：新增 `aha-theme.css` 覆盖输入类（`.text-input` / `.text-area` / `.combo-box-base` /
  `.choice-box`）、按钮类、滚动条（**轨道 / 滑块 / 箭头 / 角**）、滚动容器、列表树表格、
  下拉弹出层、菜单、分隔线、进度条；`Palette` 新增 **4 个**语义色
  （`HOVER_BACKGROUND` / `SCROLL_TRACK` / `SCROLL_THUMB` / `ON_ACCENT`）并把 **15 个** modena
  中间量（`-fx-color`、`-fx-outer-border`、`-fx-inner-border`、`-fx-body-color`、
  `-fx-body-color-to-right`、`-fx-box-border`、`-fx-text-box-border`、`-fx-text-inner-color`、
  `-fx-focus-color`、`-fx-faint-focus-color`、`-fx-shadow-highlight-color`、
  `-fx-mark-highlight-color`、`-fx-hover-base`、`-fx-pressed-base`、`-fx-default-button`）
  **全部换成字面量纯色**；`ThemePaint` 新增 `install(Scene)` 与 `dialog(DialogPane)`；
  **9 处**对话框调用点改用 `dialog(…)`；`AhaDesktopApp` 在主场景创建后
  `ThemePaint.install(scene)`。
- 新增 **3 条**守卫（`PaletteTest`）：① `theme()` 在亮 / 暗下**都不得含**
  `derive(` / `ladder(` / `linear-gradient(`；② 亮色下 `CONTROL_INNER` / `SCROLL_TRACK` /
  `SCROLL_THUMB` 必须是浅色，且样式串含 `-fx-aha-control-bg: #FFFFFF` /
  `-fx-text-box-border: #C8C8C8` / `-fx-body-color: #F4F4F4`；③ `aha-theme.css`
  **必须在 classpath 上**且内容同样不得含 CSS 函数——**同时防「资源忘了打包」与
  「有人又写回 derive/gradient」**。
- 本地只跑 `CheckDocs.py`；编译与测试交 CI；**真机复验待新包**（本机无 Windows 图形环境）。

### 机制 / 行为模型

- **「背景是黑的」不一定是「设了黑色」，很可能是「没有背景」** ⇒ 排查时先问
  「**这个控件在 modena 里是靠什么上色的**」，而不是先怀疑色表。
  依据：这些控件**根本没有背景**被画上去，下面的内容透出来才显得是黑的。
- **JavaFX 的 CSS 是「整条声明」粒度的**：**一个值无效 → 整条 `-fx-*` 声明被丢弃**；
  `-fx-background-color: a, b` 里 `b` 失败，`a` 也不会被画出来。
- **原生镜像下「能用属性 / 字面量表达的，就不要用 CSS 函数」**——
  这条从「文字色」（`TS-2610-028`）扩到「背景色」（本档案），**现在扩到整个样式表**。
- 补丁表能覆盖的机制：它**不含任何 CSS 函数**；经 `Scene.getStylesheets()` 加载、
  **排在 modena 之后**（同优先级可覆盖）；节点**内联样式优先级更高**，已有主题登记不受影响。
- 还有一个易漏细节：**`-fx-background-insets` 要一并归零**——modena 的渐变规则常配多值 insets，
  只换颜色不改 insets 会画出**被裁掉一角**的色块。
- **修完要在正确的分支上**：本轮前两次尝试（PR #54 之后推的提交）因为推在**已合并分支**上
  而没进 `dev`，白跑一轮（与 `TS-2610-028` 同一条教训）。

### 未定位 / 未复现

- **真机复验待新包**：本机无 Windows 图形环境 ⇒「背景不再是黑的」**当时未确认**。
- **「CSS 函数在原生镜像下不可靠」的边界未系统普查**：只验证了 `derive` / `ladder` /
  `linear-gradient` 这三个函数在**本项目用到的几处**上会失败；
  其它 CSS 函数与其它上下文**没有清单**。
- **「推错分支」没有机器拦截**：只在教训里写明。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 真机现象 | issue #49 第三轮（ACANX 报告） | 亮色 / 跟随系统下**文本框、发送按钮、滚动条、下拉框背景全黑**；**切哪个主题都黑** |
| modena 的上色方式 | 解出 `modena.css` 查规则 | `.text-input` 用 `linear-gradient(… derive(…))`；`.scroll-bar:vertical` 用 `derive(`+`linear-gradient`；`.thumb` 用 `-fx-inner-border`/`-fx-body-color`（本身也是渐变） |
| **定案机制** | CSS 规则 + 原生镜像特性 | 值无法求值 ⇒ **丢弃整条声明** ⇒ 控件**没有背景**（不是设成黑色） |
| 与主题无关的原因 | 同上 | modena 这些规则引用 `-fx-base` 等**中间量**，不是主题色 |
| 修复覆盖面 | `aha-theme.css` | 输入类 / 按钮类 / 滚动条（轨道 / 滑块 / 箭头 / 角）/ 滚动容器 / 列表树表格 / 下拉弹出层 / 菜单 / 分隔线 / 进度条 |
| 语义色与中间量 | `Palette` | 新增 **4** 个语义色；**15** 个 modena 中间量换成字面量纯色（双保险） |
| 装载与优先级 | `ThemePaint.install` + Scene | 经 `Scene.getStylesheets()` 加载、**排在 modena 之后**；内联样式优先级更高 |
| insets 的细节 | 实现 | **显式把 `-fx-background-insets` 归零**，否则画出被裁一角的色块 |
| 新增守卫 1 | `PaletteTest` | 亮 / 暗下 `theme()` **不得含** `derive(` / `ladder(` / `linear-gradient(` |
| 新增守卫 2 | 同上 | 亮色下三个控件色必须浅色，且含 `#FFFFFF` / `#C8C8C8` / `#F4F4F4` |
| 新增守卫 3 | 同上 | `aha-theme.css` **在 classpath** 且不得含 CSS 函数（防「忘打包」与「写回函数」） |
| **未验证** | — | 真机复验（本机无 Windows 图形环境） |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「色表把背景设成了黑色」 | 这些控件**根本没有背景**（整条声明被丢弃），透出下层内容才显黑（「2.2」） |
| 「主题没切过去」 | **切哪个主题都黑**——modena 的规则引用的是 `-fx-base` 等中间量，与主题色无关（「2.2」） |
| 「所有控件都坏了」 | 只影响**在 modena 里靠 CSS 函数上色**的那一类（文本 / 下拉 / 按钮 / 滚动条）（「2.2」） |
| 「只换颜色就够了」 | modena 的渐变规则常配**多值 `-fx-background-insets`**，不改 insets 会画出被裁一角的色块（「2.3」） |
| 「只改一处就全覆盖」 | 需同时改补丁表 + `Palette` 中间量（双保险），单改一边仍可能漏（「2.3」①②） |
| 「本地能验证真机效果」 | 本机**无 Windows 图形环境**，只能跑 `CheckDocs.py` 与静态守卫 ⇒ 真机复验待新包（「3、最终验证结果」） |

## 未决问题

**Q1 真机上背景真的不黑了吗？**

现状：**未验证**——本机无 Windows 图形环境，真机复验**待 `DesktopNative.yml` 重出包**。
阻塞点：需要一次真实运行 + 真机截图（亮 / 暗两模式都要看）。

**Q2 原生镜像下「哪些 CSS 函数不可靠」有清单吗？**

现状：**没有**。本档案与 `TS-2610-028` 证明了
`derive()` / `ladder()` / `linear-gradient()` 在本项目用到的几处会失败，
但既没穷举函数，也没测其它上下文。
阻塞点：需要在原生包上做一次「CSS 特性可用性」普查；目前策略是**整个样式表去函数化**，
所以短期不影响交付。

**Q3 还有别的样式表 / 别的加载路径吗？**

现状：本轮把主表换成了去函数的补丁表；`modena.css` 本体**仍在镜像里**（未替换）。
⇒ 凡是补丁表**没覆盖到**的 modena 规则，仍可能因函数失败而失效。
阻塞点：需要一份「modena 规则 → 是否已被补丁覆盖」的核对，尚未做。

**Q4 「推错分支导致白跑」能拦吗？**

现状：**没有拦截**（本轮因此白跑一轮）。
阻塞点：可考虑用仓库规则集限制「已合并 PR 的分支不再接受推送」，
或至少在做完一轮修改后**先核对当前分支是否已合并**（见 `TS-2610-005` 的规则集现状）。
