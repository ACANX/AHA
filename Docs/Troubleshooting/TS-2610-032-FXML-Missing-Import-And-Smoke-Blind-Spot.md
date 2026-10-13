# TS-2610-032 · FXML 忘了写 import：原生包启动即崩，而构建与冒烟都判了「通过」

- **日期**：2026-10-13
- **作者**：CNXNC
- **关联 Issue**：issue #133（桌面端 FXML 化总览）
- **关联 PR**：PR #134
- **分支**：`feat/desktop-fxml`

---

## 1. 现象

PR #134 把 `DesktopShell` 的界面骨架迁到 `desktop-shell.fxml`。合并前的三道机器验证**全部通过**：

| 验证 | 结果 |
|---|---|
| `Build.yml`（3 平台 × wrapper/system + CodeQL） | ✅ 全绿 |
| `DesktopNative.yml`（5 条腿构建 + 发布） | ✅ 全绿 |
| 该工作流的 GUI 冒烟（Windows 腿真启动 exe） | ✅ 判为「存活 20 秒，启动期未崩溃」 |

ACANX 下载 `V0.1.3.00134-aha-desktop-native` 的 Windows 包真机双击后，**窗口没有出现**，控制台留下：

```text
Exception in thread "main" java.lang.RuntimeException: Exception in Application start method
Caused by: java.lang.IllegalStateException: 桌面端界面骨架装载失败：desktop-shell.fxml
        at com.acanx.module.aha.desktop.view.DesktopShell.loadView(DesktopShell.java:509)
        at com.acanx.module.aha.desktop.AhaDesktopApp.start(AhaDesktopApp.java:232)
Caused by: javafx.fxml.LoadException: BorderPane is not a valid type.
/16!/com/acanx/module/aha/desktop/view/desktop-shell.fxml
```

`/16!/` 是 `desktop-shell.fxml` 的第 16 行——就是那个 `<BorderPane …>` 根元素。

补上 import 之后**又崩了一次**，是同一大类、不同一层——反射登记**漏了构造器**：

```text
Caused by: org.graalvm.nativeimage.MissingReflectionRegistrationError:
  Cannot reflectively invoke constructor 'public javafx.scene.layout.Region()'
  To allow this operation, add the following to the 'reflection' section of
  'reachability-metadata.json' and rebuild the native image
```

而这一轮之所以能立刻定位，靠的是上一步刚改成正面断言的冒烟把 stdout / stderr 都抓了下来
（见 §4 的第 4 条）——**判据变强之后，它自己就成了排查工具**。

## 2. 排查过程

**取证①（真机）**：上面那段堆栈。`loadView()` 里我们自己的异常包装（`IllegalStateException("桌面端界面骨架装载失败：" + FXML_VIEW)`）把 `LoadException` 带了出来，链路上每一跳都在栈上，不需要额外加日志就定位到了行号。

**取证②（源文件）**：`git show` 出改前形态，用「元素类型名 → import 表」的规则跑一遍：

```text
改前 import 条数：0 → 缺 import：['BorderPane','Button','CheckMenuItem','HBox','Label',
'ListView','Menu','MenuBar','MenuItem','Region','ScrollPane','SeparatorMenuItem',
'TextArea','TextField','VBox']
```

**15 个类型，一个都没 import。**

**取证③（为什么四道验证都没拦住）**：

| 验证 | 为什么看不见 |
|---|---|
| `Build.yml` 的编译 | FXML 是**资源**，不是 Java 源码——编译器根本不看它 |
| `DesktopShellFxmlTest`（本 PR 新增，5 条对账） | 它只做**文本级**对账：`fx:id` ↔ 字段、`id` ↔ 常量、资源白名单。既不解析 FXML，也就不知道 `BorderPane` 能否解析 |
| `AhaDesktopSmokeTest` | **CI 上不跑**：它带 `@EnabledIfSystemProperty(named = "aha.ui.tests", matches = "true")`，需要图形环境 |
| `DesktopNative.yml` 的 GUI 冒烟 | 旧判据是「进程存活 20 秒 / 退出码非 0」，见第 3 节——它对「窗口没出来」是盲的 |

## 3. 根因

**两层，缺一层都不会造成「全绿 + 崩溃」这个组合。**

### 3.1 直接原因：FXML 的解析规则不是「猜包」

FXML 里无前缀的元素名**不走 `Class.forName` 补全包名**，而是查**本文件的 `<?import?>` 表**。
`<BorderPane>` 没有对应 import 时，`FXMLLoader` 直接抛 `LoadException: BorderPane is not a valid type.`

这与 Java 的直觉相反：Java 里 `BorderPane` 能靠 import 或同包解析，而 FXML 的「import」是**另一套表**，
且**漏了不会编译报错**——它只在装载那一刻才炸。

#### 3.2 第二层直接原因：反射登记「按声明类」还要「按用途」

`Region` 在元数据里**本来就有条目**（很早就被 tracing agent 采集到），登记里也有
`allDeclaredMethods` / `allDeclaredFields`——于是「这个类型在册」看起来是满足的。
但 FXML 实例化元素走的是**反射构造**，native-image 要求**单独登记构造器**：
`type` / `methods` / `fields` 都不等于「允许反射 new」。

`Region` 恰好是 fxml 里唯一被直接实例化的基类（`<Region fx:id="leftSpacer"/>`、`<Region fx:id="statusSpacer"/>`），
于是它成了唯一暴露这个缺口的类型——`Pane` / `Parent` / `Node` 只是「暂时没人 new」。

> 教训：**「类型在册」不等于「用途在册」**。登记要按**声明类**（§TS-2610-003）之外，
> 还要按**用途**（构造 / 方法调用 / 字段读写）逐项覆盖。

## 3.3 结构性原因：冒烟用的是**负面/弱判据**

旧判据（改前原文）：

```pwsh
$p = Start-Process -FilePath $bin -PassThru
Start-Sleep -Seconds 20
if ($p.HasExited) { … 退出码非 0 → 只打 ::warning:: … } else { '存活 20 秒' }
```

三个漏洞叠在一起：

1. 判据是**存活**而非「窗口出来了」——`HasExited == false` 也可能是「已经抛异常、进程还没回收」；
2. 崩溃只写 `::warning::`，**步骤仍然 success**，于是「构建成功」的观感与「应用可用」被混为一谈；
3. 不看 stdout / stderr——应用到底打印了什么，CI 完全没采。

这正是姊妹项目 LegionFanControl 在 `TS-2610-003` 里写下的那句：

> **负面断言只能证明「没看到坏现象」，不能证明「程序真的走到了好状态」。**

而本仓库此前只有 `DesktopNative.yml` 这一条能**真正执行界面路径**的验证（`AhaDesktopSmokeTest` 在 CI 上关闭），
它的判据又是弱的——于是「界面路径」实际上是**无人验证**的。

## 4. 修复

| # | 修复 | 位置 |
|---|---|---|
| 1 | 补 15 个 `<?import?>`，并在文件头写明「为什么不 import 就是运行期爆炸」 | `aha-desktop/src/main/resources/.../desktop-shell.fxml` |
| 2 | **新增静态对账**：元素类型名（大写开头）必须能在 `<?import?>` 表里找到。实测**改前的 15 个全部命中** | `DesktopShellFxmlTest#everyElementTypeIsImported` |
| 3 | **启动即打印全量版本**（含构建号）：`[aha-version] version=0.1.3.00134 baseline=0.1.3 prerelease=true`（纯 ASCII + flush） | `AhaDesktopApp#main` |
| 4 | **冒烟改成正面断言**：抓 stdout/stderr，断言 ①命中 `[aha-version]` ②命中 `[ui-fxml-loaded]` ③不得出现 `[ui-fxml-load-failed]` ④不得 20 秒内非 0 退出；失败写 `::error::` + 摘要打 ❌ + 附日志尾部 | `.github/workflows/DesktopNative.yml` |

| 5 | 给 `Region`（与 `Pane`）补 `allDeclaredConstructors` | `reachability-metadata.json` |
| 6 | **新增交叉校验**：fxml 里实例化的每个类型，都必须在元数据里登记 `allDeclaredConstructors` | `DesktopShellFxmlTest#everyFxmlInstantiatedTypeRegistersItsConstructor` |

第 6 条是关键：它把「fxml 用到的类型」与「元数据登记」对起来，于是这类漏登记
**不必再等一轮原生构建才发现**（实测：对改前的元数据跑同一条规则，`Region` 被精确点名）。

第 3 条同时是第 4 条的**断言落点**：一个「启动就能看到、且带构建号」的标记，既服务于排障（第一句要问
「你跑的是哪一版」），又让「程序真的进到 main 了」变成机器可判定的事实。它走 `System.out` 而非 log4j2——
后者的 console appender 级别是 ERROR，INFO 级的版本行在真机控制台上根本看不见（真机输出里确实只有 JavaFX 的 JUL 日志）。

> 附带修掉一个同类疏漏：`javafx-fxml` 的 POM 声明了 base / graphics / controls 三个**无分类器**依赖，
> 当初只排除了前两个，于是 classpath 上多了一个 **306 字节**的 `javafx-controls-25.jar` 空壳。
> 工作流的空壳判据写的是 `-size 0`，**正好漏掉这种几百字节的空壳**，已一并改为 `-size -2k`。

## 5. 验证

| 项 | 证据 |
|---|---|
| 静态对账能拦住本次缺陷 | 对**改前**的 fxml 跑同一规则 → 15 个类型全部报缺（第 2 节取证②） |
| 静态对账不误报 | 对改后的 fxml 跑 → 0 缺；`<top>` / `<children>` / `<items>` 等**属性标签**按「首字母大写」规则被正确排除 |
| 修复后的原生包 | 重新触发 `DesktopNative.yml`，冒烟须命中 `[aha-version]` 与 `[ui-fxml-loaded]` |
| 交叉校验能拦住第二轮缺陷 | 对**改前**的元数据跑同一规则 → `javafx.scene.layout.Region` 被精确点名 |
| 真机 | 由 ACANX 双击 `V0.1.3.00134-aha-desktop-native` 的 Windows 包确认窗口正常出现 |

## 5.1 第三轮：失败标记**根本不在二进制里**（守卫成了死代码）

第二轮修复后，本机把 Release 上的 exe 拖出来做了两件事：`strings` 搜标记、对比 sha256。结果是：

```text
aha-version              1
ui-fxml-loaded           1
ui-fxml-load-failed      0      ← 找不到
desktop-shell.fxml       5
```

`[ui-fxml-load-failed]` 在**源码里明明写着**（`AhaDesktopApp.start` 的 catch 块），却没进产物。原因：

```java
Parent root;
try {
    root = shell.buildRoot();     // ← 现在只是 `return rootNode;`
} catch (RuntimeException | Error e) {
    System.err.println("[ui-fxml-load-failed] " + e);   // ← 这行被删了
    ...
}
```

FXML 的装载在本 PR 里已经**挪进构造器**（`DesktopShell.loadView()`），而宿主里这圈 `try/catch`
包的是一个「只读字段」的方法——**静态分析能证明它不可能抛异常**，于是整个 catch handler
连同那个字符串常量一起被当成死代码消除。真机崩溃的栈也确实不停在这一层，而是：
`DesktopShell.<init>` → `loadView` → 包装后的 `IllegalStateException`。

**教训：守卫写在「证不出会抛」的位置，等于没有守卫。** 更糟的是它看起来像个守卫——
排障时会先去找那个标记，找不到再怀疑人生。

修复：

| 项 | 做法 |
|---|---|
| 标记挪到真正会执行的地方 | 印在 `DesktopShell.loadView()` 的 `catch (IOException)` 里（真机崩溃的栈就停在这一层） |
| 删掉宿主的死代码 catch | `AhaDesktopApp.start` 不再包一圈——**留着它比没有更坏** |
| **把「标记真的在产物里」变成机器自证** | `DesktopNative.yml` 的产物自证步骤新增：exe 里必须能 grep 到 `[aha-version]` / `[ui-fxml-loaded]` / `[ui-fxml-load-failed]` / `desktop-shell.fxml` |

最后一条是本次最有价值的沉淀：它把「标记存在性」从「靠人想得到」变成「每轮构建都查」，
而且**成本几乎为零**（一次 grep）。这也是 LegionFanControl 那边 `CheckExeFxmlView` 的同款思路。

## 6. 教训与沉淀

| 教训 | 沉淀 |
|---|---|
| **「构建成功」与「冒烟通过」都不等于「界面装起来了」** | 冒烟判据从「存活 / 退出码」改为「必须命中正面标记」——本条已机器强制 |
| FXML 的 import 是**另一套表**，漏了只在运行期炸 | 新增静态对账（无需图形环境，几毫秒）；`DesktopShellFxmlTest` 成为后续 FXML 化各批次的**必过门** |
| 只看「进程活着」会漏掉「窗口没出来」 | 正面标记 + 失败时把日志尾部写进摘要 |
| 排障第一句要问「你跑的是哪一版」 | 启动即打印 `[aha-version]`（含构建号），并成为冒烟断言 |
| **守卫必须证明自己会执行** | 产物自证新增「启动标记必须能在 exe 里 grep 到」；死代码 catch 已删除 |
| **能用静态手段拦住的，不要留给真机** | 本档案的两条新守卫都在 `Build.yml` 里跑（秒级），不依赖 GPU、不依赖显示 |

> **仍存在的盲区（诚实记录）**：`AhaDesktopSmokeTest`（真开窗口、验三栏折叠、Enter 发送等）在 CI 上**仍然不跑**
> ——它要图形环境。本轮新增的静态对账只能拦住「fxml 自身写错」这一类，**拦不住**「装载成功但布局 / 交互出错」。
> 后者仍然只能靠原生冒烟（正面标记）与真机。是否给 CI 加 Xvfb 跑完整 GUI 测试，见 issue #133 的待拍板项。

## 7. 涉及文件

| 文件 | 性质 |
|---|---|
| `aha-desktop/src/main/resources/com/acanx/module/aha/desktop/view/desktop-shell.fxml` | 补 15 个 `<?import?>` + 头部说明 |
| `aha-desktop/src/test/java/com/acanx/module/aha/desktop/view/DesktopShellFxmlTest.java` | 新增 `everyElementTypeIsImported` |
| `aha-desktop/src/main/java/com/acanx/module/aha/desktop/AhaDesktopApp.java` | `main` 打印 `[aha-version]` |
| `.github/workflows/DesktopNative.yml` | 冒烟改正面断言 + 空壳判据收紧 |
| `pom.xml` / `aha-desktop/pom.xml` / `aha-desktop-native/pom.xml` | 补排 `javafx-controls` 无分类器空壳 |
| `Docs/Design/DesktopNativeDesign.md` | 风险清单 R10 补记本次事故 |
