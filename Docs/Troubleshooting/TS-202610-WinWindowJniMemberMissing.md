# TS-202610-WinWindowJniMemberMissing：原生桌面端 Windows 启动崩溃 —— 平台子类的 JNI 成员查找未登记（issue #39）

> **日期**：2026-10-09
> **作者**：@ACANX / CNXNC
> **关联 PR**：待提（分支 `fix/issue-39-native-winwindow-jni` → `dev`）
> **关联记录**：[issue #39](https://github.com/ACANX/AHA/issues/39)、
> [TS-202610-JniFindClassSegfault.md](TS-202610-JniFindClassSegfault.md)（issue #37，JNI「类查找」层）、
> [TS-202610-QuantumToolkitMissing.md](TS-202610-QuantumToolkitMissing.md)（issue #35，反射层）

## 1. 背景

- 前情：issue #35（JavaFX 启动链路反射）与 issue #37（JNI `FindClass` 可达类）均已修，
  `DesktopNative` 产出三平台原生包。
- 2026-10-08，在 Windows 上跑修完 #37 的 `aha-desktop-native.exe`，仍在启动阶段崩：

```
Exception in Application start method
Caused by: java.lang.NoSuchMethodError:
    com.sun.glass.ui.win.WinWindow.notifyMoving(IIIIFFIIIIIII)[I
        at org.graalvm.nativeimage.builder/com.oracle.svm.core.jni.functions.JNIFunctions$Support.getMethodID(JNIFunctions.java:1948)
        at ...JNIFunctions.GetMethodID(JNIFunctions.java:461)
        at com.sun.glass.ui.win.WinWindow._initIDs(Native Method)
        at com.sun.glass.ui.win.WinWindow.<clinit>(WinWindow.java:52)
        at com.sun.glass.ui.win.WinApplication.createWindow(WinApplication.java:213)
        at com.sun.javafx.tk.quantum.WindowStage.initPlatformWindow(WindowStage.java:180)
        ...
```

**关键判断**：#35 / #37 的修复都**生效了**——工具包、Glass 工厂、JNI 类查找这一层都过了。
现在是**又一层、且性质不同的**缺口：不是「类查不到」，而是「native 拿到的 `jclass` 上，
那个成员没被登记」。

## 2. 排障过程与修复链

### 2.1 报错点在做什么（读 openjfx 25 源码）

`WinWindow.java` 的静态初始化块调用 `private native static void _initIDs()`。
对应的 native 实现 `Java_com_sun_glass_ui_win_WinWindow__1initIDs(JNIEnv *env, jclass cls)`
（`native-glass/win/GlassWindow.cpp`）在函数里逐个查方法 ID：

```cpp
midNotifyClose   = env->GetMethodID(cls, "notifyClose",   "()V");
midNotifyMoving  = env->GetMethodID(cls, "notifyMoving",  "(IIIIFFIIIIIII)[I");   // ← 报错点
midNotifyMove    = env->GetMethodID(cls, "notifyMove",    "(II)V");
midNotifyResize  = env->GetMethodID(cls, "notifyResize",  "(III)V");
midNotifyScaleChanged = env->GetMethodID(cls, "notifyScaleChanged", "(FFFF)V");
... notifyFocus / notifyFocusDisabled / notifyFocusUngrab / notifyMoveToAnotherScreen /
    notifyDestroy / notifyDelegatePtr / nonClientHitTest(...)
```

这里的 `cls` 是 Java 侧传进来的 `jclass`，即 **`com.sun.glass.ui.win.WinWindow` 本身**。

### 2.2 根因：成员声明在「子类」上，而登记的是「基类」

native-image 下 `GetMethodID` 要求目标成员被登记进可达性元数据。对照当时的两份清单：

| 方法 | 声明在 | 是否已登记 | 结果 |
|---|---|---|---|
| `notifyClose` / `notifyMove` / `notifyResize`(III) / `notifyFocus` … | 基类 `com.sun.glass.ui.Window` | ✅ `jni-config.json` 里 `Window` = `allDeclared*` | 查到 |
| `notifyMoving` / `nonClientHitTest` | **子类 `WinWindow` 自己** | ❌ `WinWindow` 从未登记 | `NoSuchMethodError` |
| `notifyScaleChanged` | 基类 `Window` | ✅（`Window` 全量） | （还没走到） |

issue #37 的静态扫描只提取了 `FindClass("...")` 的**字面量类名**——而 Windows 这条路径上，
native **没有 `FindClass`**，它是直接收 Java 传来的 `jclass`。于是 `WinWindow`、`WinView` 等
平台子类**从未出现在任何清单里**；只有它们的基类在册。

**取证对照（要求 vs 实际）**：

| 要求 | 实际 | 结论 |
|---|---|---|
| `WinWindow.<clinit>` 能初始化 | 在 `_initIDs` 的第二个 `GetMethodID` 处抛 `NoSuchMethodError` | 子类成员未登记 |
| `Window` 已 `allDeclared*` ⇒ 子类继承的方法也能查 | 是的（`notifyClose` 通过） | native-image 的成员查找会沿继承链找**已登记**的基类成员 |
| `WinWindow` 已登记 | 否，全清单无此类型 | 这就是缺口 |

### 2.3 为什么不「只补报错那一个」

这与 issue #37 的教训同源：**它是链式的，且跨平台**。`_initIDs` 是每个 Glass 平台实现类
各自的静态初始化块，一启动就会连续触发（`WinWindow` → `WinView` → `WinPixels` → `WinCursor`
→ …）。只补 `WinWindow` 只是把崩溃点往后推一格。

因此对 **openjfx 三平台全部 native 源码**再一次做静态扫描，但这次的目标不是 `FindClass`
而是 **`GetMethodID` / `GetStaticMethodID` / `GetFieldID` / `GetStaticFieldID`**：
凡是 native 以「本类 `jclass`」为参数查成员的类型，逐个登记。

### 2.4 修法

在既有的 `jni-config.json` 里补齐**平台实现类**（统一 `allDeclaredConstructors` /
`allDeclaredMethods` / `allDeclaredFields`，与文件既有风格一致）：

- **Windows（11 个）**：`WinWindow`、`WinView`、`WinPixels`、`WinCursor`、`WinSystemClipboard`、
  `WinDnDClipboard`、`WinMenuImpl`、`WinGestureSupport`、`WinCommonDialogs`、
  `WinAccessible`、`WinTextRangeProvider`；
- **macOS（11 个）**：`MacWindow`、`MacView`、`MacPixels`、`MacCursor`、`MacCommonDialogs`、
  `MacFileNSURL`、`MacGestureSupport`、`MacMenuDelegate`、`MacPasteboard`、`MacTimer`、`MacAccessible`；
- **三平台共用（1 个）**：`com.sun.glass.ui.EventLoop`（native 用 `Class.forName` + `GetMethodID` 查）。

`jni-config.json` 由 **62 条增至 85 条**。

**GTK 为什么不用补**：Linux 的 `glass_general.cpp` 是用 `FindClass` 拿到
`GtkApplication` / `GtkView` / `GtkWindow` / `GtkPixels` 的，issue #37 的扫描已经覆盖，
所以这三个类在 `jni-config.json` 里**早已在册**——这也正好反证了缺口的来源：
不是「GTK 特殊」，而是「Windows / macOS 不经 `FindClass`」。

**成员统一 `allDeclared*` 的理由**（沿用 issue #37 的结论）：native 按名字 + 签名查成员，
逐条列容易漏，且签名会跨版本漂移（例如 mac 的 `notifyMove(IIZ)` 与 base 的 `notifyMove(II)V`
就不同）。

### 2.5 顺带闭合的同类缺口

同一轮扫描还发现两个「native 用 `Class.forName` 现查」的类型，与 issue #39 同类（都在 JNI 层）：

- `com.sun.glass.ui.win.WinDnDClipboard`：`GlassDnD.cpp` 用
  `GlassApplication::ClassForName`（内部 `Class.forName`）+ `GetStaticMethodID("getInstance")`
  等查它；原先不在任何清单里。
- `com.sun.glass.ui.EventLoop`：`GlassDialogs.m` 用 `Class.forName` + `GetMethodID("<init>"/"enter"/"leave")`。

两者一并登记进 `jni-config.json`。另核对：`MacMenuBarDelegate` 虽然是 `Class.forName` 加载，
但 Java 侧 `MacPlatformFactory` 直接 `new MacMenuBarDelegate()` 引用它（静态可达），无需登记；
`com.sun.glass.ui.Menu` / `MenuItem$Callback` 早已在 `reachability-metadata.json`。

## 3. 最终验证结果

- **元数据**：`jni-config.json` 62 → **85 条**，JSON 合法、`"name"` 无重复（单测守护）。
- **反向验证**：临时把 `com.sun.glass.ui.win.WinWindow` 改名后，
  `NativeImageMetadataTest.jniConfigRegistersPlatformInitClasses` **如期失败**；
  恢复后重新全绿——证明守卫不是摆设。
- **单测**：`NativeImageMetadataTest` 10 → **11 条**；`./mvnw -pl aha-desktop -am test -Djacoco.skip=true`
  **158 个用例全绿**（1 skipped）。
- **产物自证**：`DesktopNative.yml` 第 ⑧b 条新增 4 项检查
  （`WinWindow` / `WinView` / `MacWindow` / `EventLoop`）。
- **快速检查**：`bin/CheckScripts.py`、`bin/CheckDocs.py`、`bin/CheckSkills.py` 全绿。
- **真机待验证**：本机是 Linux，无法运行 Windows 产物；需 CI 重出包后在 Windows 上复跑
  （`TODO` `N-16` 第三轮）。按项目的诚实口径：**已知缺口已闭，不等于证明完整**。

## 4. 关键教训

1. **「基类在册」不等于「子类在册」**。native-image 的成员查找会沿继承链找到**已登记**的
   基类成员，所以只登记基类时，子类**自己声明**的方法仍然查不到——而且报错只在用的那一刻才出现。
2. **一条 `FindClass` 扫描会漏掉「不经 `FindClass`」的路径**。Windows / macOS 的
   `_initIDs` 直接收 Java 传入的 `jclass`，类名不在 native 里以字面量出现；
   必须改为扫 **`Get*ID` 的目标类**，而不是只扫 `FindClass` 的字面量。
3. **修的是「一类」而不是「一个」**。`_initIDs` 在每个平台实现类上重复出现，
   只补 `WinWindow` 只是把崩溃点往后推一格。
4. **守卫要有反例**。反向验证（临时删一条 → 守卫变红）才是「守卫有效」的证据。
5. **平台无关的清单可共用一份**：三平台类写在同一份 `jni-config.json`，
   缺席平台只产生无害 warning，不失败。

## 5. 涉及文件清单

- `aha-desktop/src/main/resources/META-INF/native-image/com.acanx.module.aha/aha-desktop/jni-config.json`（62 → 85 条）
- `aha-desktop/src/test/java/com/acanx/module/aha/desktop/NativeImageMetadataTest.java`（10 → 11 条）
- `.github/workflows/DesktopNative.yml`（产物自证第 ⑧b 条补 4 项）
- `Docs/Design/DesktopNativeDesign.md`（§6.1 补 issue #39 一节）
- `Docs/TODO.md`（`N-16` 第三轮、`N-22` 进展、`G-10`）
- `CHANGELOG.md`
- `Docs/Troubleshooting/TS-202610-WinWindowJniMemberMissing.md`（本文件）
