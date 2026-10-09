# TS-202610-NativeMainClassNotFound：修复 issue #26：native 桌面端启动即 `ClassNotFoundException`（JavaFX 入口的两处反射）

> 日期：2026-10-08
> 作者：@ACANX（与 AI 助手协作）
> 关联分支：`fix/issue-26-native-desktop-classnotfound`
> 关联记录：`Docs/Design/DesktopNativeDesign.md` §6.1（R2）、`.agents/skills/java-app-graalvm-native-image-compile/`、issue #26

## 1. 现象

双击运行发布页上的 `aha-desktop-native.exe`，立刻退出：

```
Exception in thread "main" java.lang.RuntimeException: java.lang.ClassNotFoundException: com.acanx.module.aha.desktop.AhaDesktopApp
        at javafx.application.Application.launch(Application.java:311)
        at com.acanx.module.aha.desktop.AhaDesktopApp.main(AhaDesktopApp.java:431)
Caused by: java.lang.ClassNotFoundException: com.acanx.module.aha.desktop.AhaDesktopApp
        at java.lang.Class.forName(...)
        at javafx.application.Application.launch(Application.java:299)
```

构建完全成功、产物也编出来了，**一启动就崩**——典型的「构建成功不是验收标准」。

## 2. 根因：JavaFX 入口有两处反射

查 JavaFX 25 源码（`openjdk/jfx` 的 `jfx25` 分支）：

| 位置 | 代码 | 说明 |
|---|---|---|
| `Application.launch(String... args)` | 用栈帧推断调用类名，再 `Class.forName(callingClassName, false, loader)` | 反射加载主类 |
| `LauncherImpl.launchApplication` | `Constructor<? extends Application> c = appClass.getConstructor(); app.set(c.newInstance());` | 反射实例化 `Application` 子类 |

这两处都在 native-image 的 **closed-world 静态分析之外**：
`reachability-metadata.json` 里当时只注册了 4 个网络协议处理器，没有 `AhaDesktopApp`，
于是 `Class.forName` 在镜像里找不到该类。

注意：**第 2 处与 `launch` 的写法无关**。即使把 `main` 改成
`launch(AhaDesktopApp.class, args)`（去掉 `Class.forName`），
`getConstructor().newInstance()` 仍然需要构造器注册。

## 3. 修法（两层）

1. **注册元数据**（根本修复）：`aha-desktop/src/main/resources/META-INF/native-image/.../reachability-metadata.json`
   增加
   ```json
   {
     "type": "com.acanx.module.aha.desktop.AhaDesktopApp",
     "allDeclaredConstructors": true,
     "allPublicConstructors": true
   }
   ```
2. **`main` 显式传 Class**：`launch(args)` → `launch(AhaDesktopApp.class, args)`，
   去掉第 1 处 `Class.forName`（第 2 处仍需上面的注册）。

## 4. 守卫（「构建成功」不算验收）

| 层 | 位置 | 作用 |
|---|---|---|
| Build / Gate | `aha-desktop/src/test/.../NativeImageMetadataTest` | 读 `reachability-metadata.json`，断言主类已注册且**落在它自己的条目里**（不被别的条目的构造器注册蒙混）；已做**反向验证**：删掉注册后测试失败并点名 issue #26 |
| 原生镜像管线 | `DesktopNative.yml` 产物自证新增第 ⑧ 条 | 检查构建产物 `target/native/reachability-metadata.json` 是否注册主类；缺了给 warning 并写进 Job Summary |

为什么两层都要：单测守住**源码里的元数据**，自证守住**真正进产物的元数据**——
中间还有拷贝/打包环节，「工作目录里有」不等于「产物里有」（与 issue #29 同一类教训）。

## 5. 实测

- `mvnw -pl aha-desktop -am test -Dtest=NativeImageMetadataTest` 通过；
- 反向验证：临时移除主类注册 → 测试失败，错误信息直接指向 issue #26；恢复后通过；
- `bin/CheckScripts.py` / `CheckDocs.py` / `CheckSkills.py` 与 workflow YAML 解析全绿。

## 6. 教训

1. **「构建成功」不是验收标准**：这条已写进 `BuildSpec.md`，issue #26 是最贴切的例子——
   编译、打包、上传全绿，产物一启动就崩。
2. **框架的入口常是反射的重灾区**：JavaFX 的 `launch` 是「栈帧 + `Class.forName` + 反射构造」三连，
   在 native-image 下必须显式注册；写这类入口时先把注册补齐，再谈优化。

## 7. 涉及文件清单

- `aha-desktop/src/main/java/com/acanx/module/aha/desktop/AhaDesktopApp.java`（`main` 改 `launch(Class, args)`）
- `aha-desktop/src/main/resources/META-INF/native-image/com.acanx.module.aha/aha-desktop/reachability-metadata.json`
- `aha-desktop/src/test/java/com/acanx/module/aha/desktop/NativeImageMetadataTest.java`（新增）
- `.github/workflows/DesktopNative.yml`（产物自证 ⑧）
- `Docs/Design/DesktopNativeDesign.md`（§6.1 R2 与反射坑说明）
- `CHANGELOG.md`
- `.agents/skills/java-app-graalvm-native-image-compile/references/troubleshooting.md`、`args-cookbook.md`、`SKILL.md`
