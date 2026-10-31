# TS-2610-017 · 修复 issue #26：native 桌面端启动即 `ClassNotFoundException`（JavaFX 入口的两处反射）

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

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- 双击运行发布页上的 `aha-desktop-native.exe` **立刻退出**，原文：

  ```text
  Exception in thread "main" java.lang.RuntimeException: java.lang.ClassNotFoundException: com.acanx.module.aha.desktop.AhaDesktopApp
          at javafx.application.Application.launch(Application.java:311)
          at com.acanx.module.aha.desktop.AhaDesktopApp.main(AhaDesktopApp.java:431)
  Caused by: java.lang.ClassNotFoundException: com.acanx.module.aha.desktop.AhaDesktopApp
          at java.lang.Class.forName(...)
          at javafx.application.Application.launch(Application.java:299)
  ```

- 构建**完全成功**、产物也编出来了，**一启动就崩**。
- JavaFX 25 源码（`openjdk/jfx` 的 `jfx25` 分支）里**入口有两处反射**：
  `Application.launch(String... args)` 用**栈帧推断调用类名**再
  `Class.forName(callingClassName, false, loader)`；
  `LauncherImpl.launchApplication` 用 `appClass.getConstructor()` + `newInstance()` 实例化。
- 当时 `reachability-metadata.json` **只注册了 4 个网络协议处理器**，**没有 `AhaDesktopApp`**。
- **第 2 处与 `launch` 的写法无关**：即使把 `main` 改成 `launch(AhaDesktopApp.class, args)`
  （去掉 `Class.forName`），`getConstructor().newInstance()` **仍然需要构造器注册**。
- 实测：`-pl aha-desktop -am test -Dtest=NativeImageMetadataTest` 通过；
  **反向验证**：临时移除主类注册 → 测试失败且错误信息**直接指向 issue #26**，恢复后通过；
  `Check{Docs,Skills,Scripts}` 与 workflow YAML 解析全绿。

### 机制 / 行为模型

- **「构建成功」不是验收标准**（这条已写进 `BuildSpec.md`）：issue #26 是最贴切的例子
  ——编译、打包、上传**全绿**，产物**一启动就崩**。
- **框架的入口常是反射的重灾区**：JavaFX 的 `launch` 是
  「**栈帧推断 + `Class.forName` + 反射构造**」三连，在 native-image 的 **closed-world 静态分析**
  之外，必须显式注册。
- 两处反射的**独立可证伪性**：改掉第 1 处**不能**解决第 2 处
  ——所以「改了 `launch` 的写法」不能当作修完的判据。
- **两层守卫缺一不可**：单测守住**源码里的元数据**；产物自证守住**真正进产物的元数据**
  ——中间还有拷贝 / 打包环节，「**工作目录里有**」不等于「**产物里有**」
  （与 issue #29 是同一类教训，见 `TS-2610-016`）。
- 测试断言的**强度**也要挑：必须断言主类**落在它自己的条目里**，
  否则会被「别的条目的构造器注册」蒙混过去。

### 未定位 / 未复现

- **本机未真跑原生二进制**（无 GraalVM）⇒「双击能启动」**未在本机验证**，
  修复效果由真机 / CI 确认。
- **未系统排查其它同类反射点**：本档案只处理了 JavaFX `Application` 入口的两处，
  没有穷举框架入口、序列化、资源加载、服务加载（`ServiceLoader`）等其它反射路径。
- **未在 CI 里做「启动冒烟」**：产物自证 ⑧ 只检查元数据是否**声明**了主类，
  并不能证明二进制**真的能起来**（见未决问题 Q4）。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 启动即崩的原文 | 双击运行 exe | `java.lang.ClassNotFoundException: com.acanx.module.aha.desktop.AhaDesktopApp` |
| 崩在哪两处 | 异常栈 | `Application.launch(Application.java:311)` / `Class.forName(...)` / `Application.launch(Application.java:299)` |
| 反射点 1 | JavaFX 25 源码（`jfx25` 分支） | `Application.launch(String... args)`：栈帧推断调用类名 + `Class.forName(callingClassName, false, loader)` |
| 反射点 2 | 同上 | `LauncherImpl.launchApplication`：`appClass.getConstructor()` + `newInstance()` |
| 当时的元数据覆盖 | `reachability-metadata.json` | 只注册了 **4 个网络协议处理器**，**无** `AhaDesktopApp` |
| 单测 | `-pl aha-desktop -am test -Dtest=NativeImageMetadataTest` | 通过 ✅ |
| 反向验证（**关键**） | 临时移除主类注册后重跑 | 测试**失败**，错误信息**直接指向 issue #26**；恢复后通过 ✅ |
| 检查与 YAML | `bin/Check{Docs,Skills,Scripts}.py` + YAML 解析 | 全绿 ✅ |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「构建 / 打包 / 上传失败了」 | 三者**全绿**，产物一启动才崩（「1、现象」） |
| 「元数据已覆盖入口」 | 当时只注册了 **4 个协议处理器**，没有 `AhaDesktopApp` 条目（「2、根因」） |
| 「改成 `launch(Class, args)` 就够了」 | 第 2 处 `getConstructor().newInstance()` **仍然需要构造器注册**（「2、根因」） |
| 「单测守住就够了」 | 中间还有拷贝 / 打包环节，「工作目录里有」≠「产物里有」⇒ 另加产物自证第 ⑧ 条（「4、守卫」） |
| 「注册了就算数（可能被别的条目蒙混）」 | 测试断言主类**落在它自己的条目里**（「4、守卫」） |

## 未决问题

**Q1 修复后的原生二进制真的能启动吗？**

现状：**本机未验证**（无 GraalVM，无法真跑）；须由**真机 / CI** 确认。
阻塞点：需要一次真实的 `DesktopNative` 运行 + 真机双击。

**Q2 还有哪些框架反射点需要注册？**

现状：只处理了 JavaFX `Application` 入口的**两处**；未系统排查
（如 `ServiceLoader`、序列化、资源加载、其它框架入口）。
阻塞点：需要一个「native-image 反射点清单」的排查方法，而非逐个踩。

**Q3 「源码元数据 = 产物元数据」能更普遍地守吗？**

现状：只对**主类**做了产物自证第 ⑧ 条；其它条目未做同类比对。
阻塞点：可考虑在自证里做一次「两份 JSON 的键集合比对」，尚未评估。

**Q4 能不能在 CI 里做一次「启动冒烟」？**

现状：**未做**——产物自证 ⑧ 只证明元数据**声明**了主类，不证明二进制**真的能起来**。
这正是「构建成功不是验收标准」这条教训留下的缺口。
> **后续进展（不在本档案范围）**：原生工作流后来增加了**正面断言式**的产物自证
> （以 `grep -F` 在**产物字节**里查找启动标记），把「声明了」推进到「确实写进了产物」；
> 但**「二进制真能起来」仍需真机**。
