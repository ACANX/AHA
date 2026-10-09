# TS-202610-DarkModeControlColors：暗色模式下控件背景与文字未适配（issue #49）

## 1. 背景

issue #49（真机，Windows 11 + 原生镜像）：切换到暗色模式后，一批控件没有跟着换色——

- 侧边栏**搜索框**、**会话列表**、中部**输入框**仍是亮色（白）底；
- 侧边栏**导航按钮**、顶部**菜单栏**、底部**状态栏**文字仍是深色，在深色底上几乎不可读。

截图为证，见 issue #49 正文。

## 2. 排障过程与修复链

### 2.1 两个层面的问题

把截图放大逐块比对后，问题分成两类，成因不同：

1. **控件沿用 JavaFX 默认（modena）背景**：搜索框、会话列表、输入框、滚动区这些控件的底色
   来自 modena 的 looked-up color（`-fx-control-inner-background` 等），只靠根节点上的
   inline 样式不一定能传到位，暗色下就露出白底；
2. **样式串与色表不同步**：`Palette.theme()` 早先返回一个**缓存的静态字段** `themeStyle`，
   而 `Palette.FOREGROUND` 等字段是运行期由 `setTheme` 更新的。原生镜像下会出现
   「样式串仍是初始主题、字段已按新主题更新」的混合状态——表现为**主窗口底色是暗的、
   控件文字却是亮色主题的深色**，与截图完全一致。

### 2.2 修复

1. **`Palette.theme()` 去掉缓存**：删除 `themeStyle` 静态字段，改为每次 `buildThemeStyle()`
   现算。样式串与色表因此永远一致，也消除了「构建期初始化 + 缓存字段」在原生镜像下的隐患。
2. **`applyTheme` 逐个节点异常隔离**：一个节点的重刷动作抛异常时，只记录日志并跳过，
   不再中断整个重刷循环（原先一个失败会导致其后所有节点停在旧主题）。
3. **补齐显式主题**（不再依赖 looked-up color 的传播）：
   - 搜索框（`TextField`）与输入框（`TextArea`）：显式设 `-fx-control-inner-background`、
     `-fx-text-fill`、`-fx-prompt-text-fill`；
   - 会话列表单元格（`SessionCell`）：显式设背景与文字色，选中态单独覆盖；
   - 菜单栏（`MenuBar`）与中栏滚动区（`ScrollPane`）：显式设底色。
4. **守卫测试**：`PaletteTest` 新增 `themeStyleIsRecomputedFromCurrentFields`，
   钉住「切换主题后样式串立即反映新色表」。

## 3. 最终验证结果

- 按 ACANX 的要求，本地不跑构建与测试，编译与全量测试交由 PR 的持续集成执行；
- 真机验收（下载合入后自动产出的 Windows 原生包）：
  ① 暗色下搜索框 / 会话列表 / 输入框为深色底、浅色字；
  ② 导航按钮、菜单栏、状态栏文字为浅色；
  ③ 亮色 ↔ 暗色来回切换，不再出现混合状态。

## 4. 关键教训

1. **可变静态状态 + 缓存 = 原生镜像下的定时炸弹**：缓存字段看似只是省几个字符串拼接，
   却让「同一份色表」出现了两个来源。凡是能从权威状态推导的东西，就不要另存一份。
2. **不要依赖 JavaFX looked-up color 的隐式传播**：`-fx-control-inner-background` 之类
   在根节点上设一次，子控件未必都能取到；关键控件必须显式设色。
3. **重刷循环要能容错**：批量重刷几十个节点时，一个异常不该让整屏停在旧主题。

## 5. 涉及文件清单

- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/Palette.java`
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/DesktopShell.java`
- `aha-desktop/src/test/java/com/acanx/module/aha/desktop/view/PaletteTest.java`
- `CHANGELOG.md`、`Docs/TODO.md`
