# TS-202610-ThemeNotFollowingSystem：原生桌面端主题不跟随系统、切换残留与暗色标题对比度

## 1. 背景

issue #44（真机，Windows 11 + 原生镜像）：原生桌面端已能正常开窗并加载界面，但暴露出四类界面问题：

1. **默认主题恒为暗黑**，与系统当前的浅色皮肤不一致；
2. **从暗黑切到亮色后，部分组件背景仍是暗色**（切换不彻底）；
3. **暗色模式下部分标题文字是深色系**，与深色背景对比度不足，肉眼难以分辨；
4. **字体清晰度 / 字体本身与 JVM（jar）模式差距明显**。

JVM 模式下的截图作为对照：同一台机器、同一份配置，JVM 模式是浅色且字体清晰。

## 2. 排障过程与修复链

### 2.1 先分清「四类问题里哪些是确定性缺陷」

第 4 类（字体）只在原生镜像里出现，属于渲染管线 / 字体枚举层面的差异；
本机（WSL，无 Windows 图形环境）无法复现，因此本次不动它，转入 `N-24` 跟踪。
其余三类都能在源码里定位到确定原因，逐条修。

### 2.2 第 1 类：默认主题恒暗

`DesktopSettings` 的默认主题本就是 `SYSTEM`，问题出在 `SystemTheme.prefersDark()`：
读不到系统配色（`getColorScheme()` 为 `null`、或系统偏好尚未就绪、或原生镜像里读取失败）
时，**一律返回暗色**。JVM 模式能读到 `LIGHT`，原生镜像读到空，于是同一台机器上
两种模式一浅一深。

修法：三级回退——

1. 配色明确为 `DARK` / `LIGHT` → 直接采用；
2. 配色未定 → 用系统**背景色**的亮度判断（同一份系统偏好数据，能兜住第一种的空白）；
3. 都取不到 → **亮色**（Windows 与主流桌面的出厂默认；旧实现猜暗色的代价已在真机上被证实）。

另加 `SystemTheme.onColorSchemeChanged(Runnable)`：订阅 `colorSchemeProperty()`，
在「跟随系统」模式下用户于系统设置里切换深色 / 浅色皮肤时实时重刷界面
（回调在界面线程触发；仅当主题仍为 `SYSTEM` 时应用，不覆盖用户手动选择）。

### 2.3 第 2 类：切换亮色后部分组件仍是暗色

两处原因：

- **硬编码颜色**：`DesktopShell` 3 处边框写死 `#3A3A3A`、`ProviderDialog` 1 处背景写死 `#1E1E1E`。
  它们虽然走了 `themed(...)` 登记，但每次重刷写回的还是暗色常量，切到亮色自然「没变」。
  改为读 `Palette.BORDER` / `Palette.BASE`。
- **未登记 / 未重刷的常驻节点**：
  - `CompletionPopup` 是常驻实例，样式在构造时一次性写入，换主题后不会重建 →
    新增 `refreshTheme()`，由 `DesktopShell.applyTheme` 调用；
  - 会话右键菜单（`ContextMenu`）有独立的场景根，不继承主窗口主题 →
    改为在 `setOnShowing` 时套上当前配色，保证每次弹出都是当前主题。

### 2.4 第 3 类：暗色下标题文字对比度不足

逐个排查后发现：右栏「本轮」标题、左右两条折叠按钮是**仅有的没有走 `themed(...)` 登记**
的裸控件，因此一直用 JavaFX 默认文字色（深色系），在深色底上几乎看不见。
现统一登记主题：标题用 `Palette.FOREGROUND` 加粗，折叠按钮改为透明底 + `Palette.MUTED`。

### 2.5 第 4 类：字体差异（本次不修）

字体清晰度 / 字体本身与 JVM 模式的差距，只在原生镜像里出现。可能的方向有三条，
但都需要真机对照才能定位：

- Prism 渲染管线是否回退到软件管线（软件管线没有次像素抗锯齿，字会明显发虚）；
- 原生镜像下的字体枚举 / 默认字体解析是否与 JVM 模式不同；
- 高分辨率缩放比的读取是否一致（窗口尺寸计算已经处理过，但字体栅格化是另一条路径）。

已另开 **issue #48** 长期跟踪（现象、背景、待排查方向与验收标准均写在该 Issue 正文里），
`Docs/TODO.md` 的 `N-24` 只作索引。

## 3. 最终验证结果

- 本次改动只涉及桌面端界面层，未触碰反射 / JNI 元数据与构建参数；
- 按 ACANX 的要求，本地不跑构建与测试，编译与全量测试交由 PR 的持续集成执行
  （`Build` 六条腿 + `CodeQL`）；
- 真机验收（下载合入后自动产出的 Windows 原生包）：
  ① 默认启动主题与系统一致；
  ② 切到亮色后三栏边框、工具卡片、候选弹层、右键菜单全部跟着变；
  ③ 深色模式下「本轮」标题与折叠按钮清晰可读；
  ④ 字体差异仍在，转 issue #48。

## 4. 关键教训

1. **「兜底值」也是一种产品决策**：`prefersDark()` 回退暗色，当初的理由是「猜错代价最小」，
   但真机上它就是「每次启动都猜错」。兜底应当选**出厂默认**，并留一条能自我纠正的订阅。
2. **内联样式 + 逐节点登记的主题方案，漏一个节点就是一个永久缺陷**：裸控件（不登记）
   与硬编码常量是同一个病的两种表现。本次把仅剩的裸控件补齐；后续新增控件必须走 `themed(...)`。
3. **弹层是独立的场景根**：`Dialog` 早已处理，但 `Popup`（候选弹层）与 `ContextMenu`（右键菜单）
   同样不会继承主窗口样式——这类「看不见的场景根」是主题缺陷的高发区。
4. **遗留问题要开 Issue，不能只写进 TODO**：`TODO.md` 是暂存区，长期条目会被后续条目淹没；
   本次字体问题已单开 issue #48 长期跟踪，并把「未闭环问题必须开 Issue」写进项目 `AGENTS.md`。

## 5. 涉及文件清单

- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/SystemTheme.java`
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/DesktopShell.java`
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/CompletionPopup.java`
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/view/ProviderDialog.java`
- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/AhaDesktopApp.java`
- `CHANGELOG.md`、`Docs/TODO.md`、`Docs/Design/DesktopDesign.md`
