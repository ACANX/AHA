# TS-2610-031 · 暗色下文字仍是黑色——CSS 补丁表不能再查表

- **日期**：2026-10-09
- **作者**：CNXNC
- **关联 Issue**：issue #49（第四轮）
- **关联 PR**：PR #59
- **分支**：`fix/issue-49-native-theme-css-literals`

---

## 1. 背景

PR #57（`b70c7f4`）出了 `V0.1.1.00057` 之后，ACANX 的真机反馈是**半好半坏**：

> 「亮色模式下的问题基本解决了，暗色模式下的字体需要变成亮色的，这个还是没有支持呀，
> 很多显示文字的区域的字体还是黑色的字体呀」

**这句「亮色好了、暗色没好」本身就是最强的线索**——它说明**不是颜色算错，而是颜色没被应用**。

## 2. 排障过程

### 2.1 先定位：#57 到底交付了什么

`V0.1.1.00057` 里含 PR #57 的全部内容，其中 `aha-theme.css` 的写法是：

```css
.label {
    -fx-text-fill: -fx-aha-text;      /* ← 问题在这 */
}
```

而 `-fx-aha-text` 这类自定义 color **定义在根节点的内联样式里**（`Palette.buildThemeStyle()`）。

### 2.2 为什么「亮色好了、暗色没好」

**原生镜像下，CSS 的 looked-up color 查表不可靠**——这是本项目在 #49 前三轮反复验证过的结论
（`Docs/Troubleshooting/TS-2610-028-Theme-Lookup-Table-Failure.md`）。而 CSS 的规则是：

> 声明的**值无法求值 → 丢弃整条声明**。

于是 `.label { -fx-text-fill: -fx-aha-text; }` 整条失效，文字色回落到 modena 默认的**黑**。于是：

| 主题 | 期望文字色 | 查表失败后的实际值 | 观感 |
|---|---|---|---|
| **亮色** | `#1F1F1F`（深） | modena 默认黑 | **碰巧正确** ✅ |
| **暗色** | `#E4E4E4`（浅） | modena 默认黑 | **一片黑字** ✗ |

**这就是「亮色基本解决、暗色仍黑」的全部原因。**
换句话说：上一轮我把背景类颜色改成了字面量，却**在文字类颜色上又依赖了一次查表**——
同一个坑，在同一份文件里踩了第二次。

### 2.3 修复：两份纯字面量样式表

1. **拆成两份**：`aha-theme-dark.css` / `aha-theme-light.css`，**每一处颜色都是字面量**，
   文件里**不允许出现任何变量查表 / `derive()` / `linear-gradient()` / `ladder()`**；
2. **`ThemePaint` 按主题整份装载**：`install(scene)` 取当前主题那份、并**移除另一份**
   （主题切换时整份替换，所以不需要任何「变量重新绑定」机制）；
3. **`DesktopShell.applyTheme` 末尾调用 `install`**：切主题即换表；
4. **删掉 `Palette` 里的 `-fx-aha-*` 定义**：不再需要，留着只会诱使后来者再去查表；
5. **补一层「文字兜底」**：把 `.label` / `.cell` / `.list-cell` / `.menu-item > .label` /
   `.menu-bar .label` / `.text-input` 等**所有承载文字的样式类**逐个显式钉死；
   另外单独处理 **`Text` 节点（走 `-fx-fill`，不是 `-fx-text-fill`）**——写错一处就是一片黑字。

**生成方式**：以原 `aha-theme.css` 为模板，把 55 处变量按两份色表替换、并做「无残留」断言；
产物入库，生成脚本不入库（一次性）。

## 3. 最终验证结果

### 3.1 结构校验（两表声明里都不含查表与函数）

| 文件 | `derive(` | `linear-gradient(` | `ladder(` | 变量 | `-fx-text-fill` | `-fx-fill` |
|---|---|---|---|---|---|---|
| `aha-theme-dark.css` | 0 | 0 | 0 | **0** | 13 | 2 |
| `aha-theme-light.css` | 0 | 0 | 0 | **0** | 13 | 2 |

### 3.2 真渲染校验（JVM 探针：装载 CSS → `applyCss()` → 读属性值）

```
dark  | label.textFill=0xe4e4e4ff | button.textFill=0xe4e4e4ff | field.bg=0x252526ff
light | label.textFill=0x1f1f1fff | button.textFill=0x1f1f1fff | field.bg=0xffffffff
```

**暗色下文字为浅色、亮色下为深色**，两份表都真的生效。

### 3.3 测试守卫（`PaletteTest`）

- `patchStylesheetsAreLiteralAndPackaged`：两份表都必须在 classpath 上，且**声明里**
  不得出现 `-fx-aha-` / `derive(` / `ladder(` / `linear-gradient(`；
- `patchStylesheetsDifferBetweenThemes`：两份表必须**真的不同**，且暗色含 `#E4E4E4`、
  亮色含 `#1F1F1F`——相同就说明渲染没区分主题。

## 4. 关键教训

1. **「一半好一半坏」是最有价值的反馈**：亮色正常、暗色不正常，直接把范围从
   「颜色算错了」缩到「颜色没被应用」。修之前先问「哪个现象能证伪我的假设」。
2. **不要在同一份文件里又用变量、又声称绕开了查表**。#57 已经证明 looked-up color
   在原生镜像里不可靠，我却把文字色继续写成变量——**结论必须贯彻到同一处代码**，
   而不是只用在刚踩过坑的那几行。
3. **CSS 是「整条声明」粒度的**：一个值查不到 → 整条 `-fx-text-fill` 丢弃 →
   回落默认值。所以「部分生效」在 CSS 里不存在，只有「这条生效 / 这条没生效」。
4. **亮色下看不出问题 ≠ 正确**：亮色的期望值恰好等于 modena 的默认值，
   这种「巧合正确」会掩盖缺陷，**必须在暗色下验收**。
5. **`Text` 节点用 `-fx-fill`**，`Labeled` 用 `-fx-text-fill`——两套属性，
   兜底规则要各写一份。

## 5. 涉及文件清单

- `aha-desktop/src/main/resources/com/acanx/module/aha/desktop/view/aha-theme-dark.css`（新增）
- `aha-desktop/src/main/resources/com/acanx/module/aha/desktop/view/aha-theme-light.css`（新增）
- `aha-desktop/src/main/resources/com/acanx/module/aha/desktop/view/aha-theme.css`（删除）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/ThemePaint.java`
  （两份表常量 + `install` 整份替换 + `external` 辅助）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/DesktopShell.java`
  （`applyTheme` 末尾整份换表）
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/Palette.java`
  （删除 `-fx-aha-*` 变量定义与相关注释）
- `aha-desktop/src/test/java/com/acanx/module/aha/desktop/view/PaletteTest.java`
  （守卫：两表纯字面量 + 两表必须不同）
- `CHANGELOG.md`、`Docs/TODO.md`

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- PR #57（`b70c7f4`）出了 `V0.1.1.00057` 之后，ACANX 的真机反馈是**半好半坏**：

  > 「亮色模式下的问题基本解决了，暗色模式下的字体需要变成亮色的，这个还是没有支持呀，
  > 很多显示文字的区域的字体还是黑色的字体呀」

- `V0.1.1.00057` 里 `aha-theme.css` 的写法是：

  ```css
  .label {
      -fx-text-fill: -fx-aha-text;      /* ← 问题在这 */
  }
  ```

  而 `-fx-aha-text` 这类自定义 color **定义在根节点的内联样式里**（`Palette.buildThemeStyle()`）。
- **「亮色好了、暗色没好」的完整解释**（原生镜像下 looked-up color 查表不可靠 + CSS 规则
  「值无法求值 → 丢弃整条声明」）：`.label { -fx-text-fill: -fx-aha-text; }` **整条失效**，
  文字色回落到 modena 默认的**黑**：

  | 主题 | 期望文字色 | 查表失败后的实际值 | 观感 |
  | --- | --- | --- | --- |
  | **亮色** | `#1F1F1F`（深） | modena 默认黑 | **碰巧正确** ✅ |
  | **暗色** | `#E4E4E4`（浅） | modena 默认黑 | **一片黑字** ✗ |

  ⇒ 上一轮把**背景类**颜色改成了字面量，却**在文字类颜色上又依赖了一次查表**
  ——同一个坑，在**同一份文件**里踩了第二次。
- **结构校验**（两份表的声明里都不含查表与函数）：

  | 文件 | `derive(` | `linear-gradient(` | `ladder(` | 变量 | `-fx-text-fill` | `-fx-fill` |
  | --- | --- | --- | --- | --- | --- | --- |
  | `aha-theme-dark.css` | 0 | 0 | 0 | **0** | **13** | **2** |
  | `aha-theme-light.css` | 0 | 0 | 0 | **0** | **13** | **2** |

- **真渲染校验**（JVM 探针：装载 CSS → `applyCss()` → 读属性值）：

  ```text
  dark  | label.textFill=0xe4e4e4ff | button.textFill=0xe4e4e4ff | field.bg=0x252526ff
  light | label.textFill=0x1f1f1fff | button.textFill=0x1f1f1fff | field.bg=0xffffffff
  ```

  **暗色下文字为浅色、亮色下为深色**，两份表都真的生效。
- 生成方式：以原 `aha-theme.css` 为模板，把 **55 处**变量按两份色表替换，并做「无残留」断言；
  产物入库，**生成脚本不入库**（一次性）。
- 两条测试守卫（`PaletteTest`）：`patchStylesheetsAreLiteralAndPackaged`（两表须在 classpath
  且声明里不得出现 `-fx-aha-` / `derive(` / `ladder(` / `linear-gradient(`）、
  `patchStylesheetsDifferBetweenThemes`（两表必须**真的不同**，且暗色含 `#E4E4E4`、
  亮色含 `#1F1F1F`——**相同就说明渲染没区分主题**）。

### 机制 / 行为模型

- **「一半好一半坏」是最有价值的反馈**：亮色正常、暗色不正常，
  直接把范围从「颜色**算错**了」缩到「颜色**没被应用**」。
  ⇒ **修之前先问「哪个现象能证伪我的假设」**。
- **不要在同一份文件里又用变量、又声称绕开了查表**：#57 已经证明 looked-up color
  在原生镜像里不可靠，本次却把文字色继续写成变量——
  **结论必须贯彻到同一处代码**，而不是只用在刚踩过坑的那几行。
- **CSS 是「整条声明」粒度的**：一个值查不到 → 整条 `-fx-text-fill` 丢弃 → 回落默认值。
  ⇒ 「部分生效」在 CSS 里**不存在**，只有「这条生效 / 这条没生效」。
- **亮色下看不出问题 ≠ 正确**：亮色的期望值**恰好等于** modena 的默认值，
  这种「**巧合正确**」会掩盖缺陷 ⇒ **必须在暗色下验收**。
- **`Text` 节点用 `-fx-fill`，`Labeled` 用 `-fx-text-fill`**——两套属性，兜底规则要**各写一份**
  （写错一处就是一片黑字）。
- **整份表替换比「变量重绑定」简单**：`install(scene)` 取当前主题那份、**移除另一份**，
  切主题即整份替换，因此不需要任何变量重绑定机制。

### 未定位 / 未复现

- **真机复验未做**：本机只能在 JVM 侧用探针验证（已实测亮 / 暗两套都正确），
  **原生包上的效果待新包确认**。
- **生成脚本未入库**（一次性）：后续若色表变更，需**重做一次替换**（或手工改两份表），
  没有可复用的生成路径。
- **modena 未被补丁覆盖的规则**：两表只钉了**承载文字**的样式类
  （`.label` / `.cell` / `.list-cell` / `.menu-item > .label` / `.menu-bar .label` / `.text-input` 等），
  是否还有别的地方会因查表失败而回落到默认值，**未穷举**。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 真机反馈（**关键线索**） | ACANX（`V0.1.1.00057` 之后） | 「亮色基本解决、暗色下很多文字还是黑的」 |
| 问题写法 | `V0.1.1.00057` 的 `aha-theme.css` | `.label { -fx-text-fill: -fx-aha-text; }`（变量定义在**根节点内联样式**里） |
| 亮 / 暗差异的解释 | 查表失败 → 回落 modena 默认黑 | 亮色期望 `#1F1F1F` ≈ 默认黑 ⇒ **碰巧正确**；暗色期望 `#E4E4E4` ⇒ **一片黑** |
| 变量替换规模 | 生成过程 | **55 处**变量按两份色表替换 + 「无残留」断言 |
| 结构校验（两表） | 静态检查 | `derive(` = 0、`linear-gradient(` = 0、`ladder(` = 0、变量 = **0**；`-fx-text-fill` = **13**、`-fx-fill` = **2**（两表相同） |
| **真渲染校验（暗）** | JVM 探针 `applyCss()` 后读属性 | `label.textFill=0xe4e4e4ff`、`button.textFill=0xe4e4e4ff`、`field.bg=0x252526ff` ✅ |
| **真渲染校验（亮）** | 同上 | `label.textFill=0x1f1f1fff`、`button.textFill=0x1f1f1fff`、`field.bg=0xffffffff` ✅ |
| 守卫 1 | `patchStylesheetsAreLiteralAndPackaged` | 两表在 classpath，声明里不含 `-fx-aha-` / `derive(` / `ladder(` / `linear-gradient(` ✅ |
| 守卫 2 | `patchStylesheetsDifferBetweenThemes` | 两表**真的不同**；暗含 `#E4E4E4`、亮含 `#1F1F1F` ✅ |
| 兜底覆盖面 | 两表 | `.label` / `.cell` / `.list-cell` / `.menu-item > .label` / `.menu-bar .label` / `.text-input` 等；**`Text` 节点单独用 `-fx-fill`** |
| **未验证** | — | 原生包上的暗色文字色（需新包真机复验） |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「颜色算错了」 | 「亮色对、暗色不对」说明**不是算错**，而是**没被应用**（「1、背景」的引述） |
| 「是 #57 引入的问题」 | #57 之前背景类已改字面量，**文字类仍写变量** ⇒ 是**上一轮遗漏**的同类问题（「2.2」） |
| 「两份表内容一样也没关系」 | 守卫断言两表**必须真的不同**——相同就说明**渲染没区分主题**（「3.3」） |
| 「亮色通过就算过了」 | 亮色的期望值**恰好等于** modena 默认值 ⇒ **巧合正确**，必须在暗色下验收（「4、教训」第 4 条） |
| 「只钉 `.label` 就够」 | `Text` 节点走的是 **`-fx-fill`**，与 `Labeled` 的 `-fx-text-fill` 是两套属性（「2.3」⑤） |
| 「本地探针通过就等于真机通过」 | 探针只能证明「表本身生效」；原生镜像的行为差异需**真机复验**（本机无 Windows 图形环境） |

## 未决问题

**Q1 原生包上暗色文字真的变浅了吗？**

现状：**未验证**——JVM 探针已通过，但原生镜像的行为差异正是本条的主题
⇒ 需新包真机复验（与 `TS-2610-029` 的真机复验同一批）。

**Q2 两份表的维护成本怎么降？**

现状：**55 处**替换是**一次性脚本**做的，**脚本未入库**。
阻塞点：色表若再变，要么重做替换、要么手工改两份表——可考虑把「从单一色表生成两份表」
做成常驻脚本（并纳入检查），尚未评估。

**Q3 还有哪些样式类会因查表失败而回落默认值？**

现状：两表只覆盖了**承载文字**的样式类；其余（边框、图标、选中态等）**未穷举**。
阻塞点：可用「在原生包上逐控件截图比对」或「把 modena 相关规则全部字面量化」来收敛，
后者成本较高。

**Q4 「结论要贯彻到同一处代码」怎么防复发？**

现状：本轮是**人工发现**（ACANX 复验反馈）。
阻塞点：可考虑一条**静态检查**：样式表里出现 `-fx-aha-*` 一类自定义变量即失败
（`patchStylesheetsAreLiteralAndPackaged` 已经这么做了）——
但检查只覆盖这两份表，其它地方（如内联样式）仍可能重蹈覆辙。
