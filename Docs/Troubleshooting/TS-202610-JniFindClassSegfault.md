# TS-202610-JniFindClassSegfault：原生桌面端 segfault —— JNI `FindClass` 未注册（issue #37）

## 1. 背景

- 前情：issue #35（`ClassNotFoundException: com.sun.javafx.tk.quantum.QuantumToolkit`）已修，
  PR #36 合入 `dev`，`DesktopNative` 产出三平台原生包。
- 2026-10-08，在 Windows 上跑 `aha-desktop-native.exe`，报：

```
Exception in thread "JavaFX-Launcher" java.lang.NoClassDefFoundError: java/lang/Runnable
        at org.graalvm.nativeimage.builder/com.oracle.svm.core.jni.functions.JNIFunctions$Support.findClassInClassRegistries(JNIFunctions.java:2040)
        at org.graalvm.nativeimage.builder/com.oracle.svm.core.jni.functions.JNIFunctions.FindClass(JNIFunctions.java:384)
        at com.sun.glass.ui.win.WinApplication.initIDs(Native Method)
        at com.sun.glass.ui.win.WinApplication.<clinit>(WinApplication.java:102)
        ...
Exception in thread "WindowsNativeRunloopThread" java.lang.NoClassDefFoundError: java/lang/Object
        at ...JNIFunctions.FindClass(...)
        at com.sun.glass.ui.win.WinApplication._init(Native Method)
[ SegfaultHandler caught a segfault ... EXCEPTION_ACCESS_VIOLATION ]
```

**关键判断**：#35 的修复**生效了**——工具包找到了、Glass 工厂建起来了。
现在是**下一层**问题，且性质不同：不是「类不在镜像里」，而是「类在，但没被标记为 JNI 可达」。

## 2. 排障过程与修复链

### 2.1 为什么 `FindClass` 会失败（读 GraalVM 源码）

`JNIFunctions.FindClass`：

```java
if (!LibGraalSupport.inLibGraalRuntime() && Support.useClassRegistriesInFindClass()) {
    Class<?> callerClass = StackTraceUtils.getCallerClass(...);
    clazz = Support.findClassInClassRegistries(cname, callerClass);
} else {
    clazz = Support.findClassInReflectionDictionary(cname);
}
```

`findClassInClassRegistries`：

```java
clazz = ClassRegistries.forName(ClassNameSupport.jniNameToReflectionName(name), getClassLoader(callerClass));
boolean hubInaccessible = clazz != null && !DynamicHub.fromClass(clazz).isJNIAccessible();
...
if (clazz == null || hubInaccessible) {
    throw new NoClassDefFoundError(name);   // ← 报的就是这里
}
```

结论：**native-image 只允许「JNI accessible」的类被 `FindClass` 查到**。
`java.lang.Runnable` / `java.lang.Object` 在镜像里当然存在，但没注册为 JNI 可达 → 抛
`NoClassDefFoundError`；native 层拿到空引用继续跑 → segfault。

### 2.2 为什么不能「只补报错那两个类」

这是链式的、且**跨平台的**：native 库在初始化时成批查类。只补 `Runnable` / `Object`，
下一批（字体、对话框、无障碍）还会炸。

于是对 **openjfx 三平台全部 native 源码**做了一次 `FindClass` 静态扫描
（`modules/javafx.graphics/src/main/native-*/`，506 个 C / C++ / ObjC 文件），
提取出 **62 个类**：

- **JDK（24）**：`java.lang.{Object,Runnable,String,Class,Boolean,Throwable,…}`、
  `java.util.{Map,HashMap,HashSet,Set,List,ArrayList,Collections,Iterator}`、
  `java.nio.ByteBuffer`、`java.io.{File,IOException}`、`java.lang.IllegalAccessException` …；
- **Glass 公共（11）**：`Application` / `Window` / `View` / `Screen` / `Pixels` / `Size` /
  `Cursor` / `Clipboard` / `CommonDialogs` + 两个内部类；
- **Glass 平台实现（7）**：`gtk.Gtk{Application,Pixels,View,Window}` + `gtk.screencast.TokenStorage`、
  `win.WinVariant`、`mac.MacVariant`；
- **字体（18）**：DirectWrite 的 `DWRITE_*` / `D2D1_*` / `RECT`、FreeType 的 `FT_GlyphSlotRec` /
  `PangoGlyphString`、CoreText 的 `CG{AffineTransform,Point,Size,Rect}`、
  `FontConfigManager${FcCompFont,FontConfigFont}`（后两个是 `fontpath_linux.c` 里的**动态名**）；
- **其它**：`javafx.scene.paint.Color`、`com.sun.javafx.geom.Path2D`。

### 2.3 成员访问统一用全量

逐个核对 `GetMethodID` / `GetFieldID` / `GetStaticMethodID` / `GetStaticFieldID` 后发现：
**成员都声明在注册类自己身上**（`Object.equals`、`HashMap.put`、`Boolean.TRUE`、`View.ptr` …），
没有跨继承层查找。因此统一用 `allDeclaredMethods` / `allDeclaredFields` / `allDeclaredConstructors`
——native 按名字 + 签名查成员，逐条列容易漏，而这几条配置本来就该「宁可多、不可少」。

### 2.4 放哪儿：`jni-config.json` 而不是 `reachability-metadata.json`

GraalVM 的**解析器**其实支持 `reachability-metadata.json` 里的 `jni` 键
（`ConfigurationParser.checkAttributes(..., List.of("reflection", "jni", …))`），
但**官方 schema `reachability-metadata-schema-v1.2.0.json` 还没有 `jni`**
（顶层 `additionalProperties: false`）。为不产出「能跑但不合规」的文件，
改用独立的 `jni-config.json`（有官方 `jni-config-schema-v1.1.0.json`），放在同一目录，
native-image 同样会自动读取。

`jni-config.json` 的条目是 `additionalProperties: false`，**不能写注释**，
所以「为什么是这些类」记录在本文件与 `DesktopNativeDesign.md` §6.1。

### 2.5 缺席平台只是警告

`ReflectionConfigurationParser` 对解析不到的类走 `handleMissingElement` → `LogUtils.warning`。
三平台共用一份清单时，某平台缺的类只会产生无害 warning，不会构建失败
（与 `reachability-metadata.json` 里多平台条目共存的既有结论一致）。

## 3. 最终验证结果

- 元数据：`jni-config.json` **62 条**，字段对照官方 schema 合法、无重复。
- 守卫：`NativeImageMetadataTest` 由 8 条扩到 **10 条**（新增「关键类必须注册」与
  「数组无重复条目」两条）→ 全绿。
- 产物自证：`DesktopNative.yml` 新增第 ⑧b 条，检查 `jni-config.json` 里
  `java.lang.Runnable` / `java.lang.Object` / `WinVariant` / `DWRITE_MATRIX` /
  `FontConfigManager$FcCompFont`。
- **真机待验证**：本机是 Linux，无法运行 Windows 产物；需 CI 重出包后在 Windows 上复跑
  （`TODO` `N-16`）。

## 4. 关键教训

1. **反射与 JNI 是两套清单**。issue #35 修的是「类可达」（反射元数据），
   issue #37 是「类 JNI 可达」——类在镜像里也会 `NoClassDefFoundError`。
   在 GraalVM 里这两个判断是独立的（`isReachable` vs `isJNIAccessible`）。
2. **native 库用名字查类，就必须有 JNI 元数据**。这是 JNI 规范在 native-image 下的直接后果，
   不是 bug；Gluon 这类发行版也是这么配的。
3. **依然是链式的**。要么做**全量静态扫描**（本次：506 个 native 源文件 → 62 个类），
   要么用 tracing agent / 真机逐功能跑。只补报错那一个是下策。
4. **配置格式要跟着 schema 走**：解析器支持 ≠ schema 支持；不一致时选有 schema 的那个。
5. **平台专属类可以共用一份清单**：缺席平台只警告不失败。

## 5. 涉及文件清单

- `aha-desktop/src/main/resources/META-INF/native-image/com.acanx.module.aha/aha-desktop/jni-config.json`（新增，62 条）
- `aha-desktop/src/test/java/com/acanx/module/aha/desktop/NativeImageMetadataTest.java`（8 → 10 条）
- `aha-desktop-native/pom.xml`（元数据复制步骤加 `jni-config.json`）
- `.github/workflows/DesktopNative.yml`（产物自证第 ⑧b 条）
- `Docs/Design/DesktopNativeDesign.md`（§6.1 补 JNI 一节）
- `Docs/TODO.md`（`N-16` 进展）
- `CHANGELOG.md`
- `Docs/Troubleshooting/TS-202610-JniFindClassSegfault.md`（本文件）
