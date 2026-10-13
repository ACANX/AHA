# TS-2610-033 · 姊妹工程复现的同族缺口，在本仓库不成立

> 事件日期：2026-10-13
> 作者：@cnxnc
> 关联 Issue：[#125](https://github.com/ACANX/AHA/issues/125)（本体）；同族 #26 / #35 / #37 / #39 / #41
> 关联记录：`Docs/Design/DesktopNativeDesign.md` §R2、本目录 `TS-2610-021` / `TS-2610-022`（同族排查）

## 1. 现象

Issue #125 的认定是：「本仓库原生元数据的 **`reflection` 段**缺
`com.sun.glass.ui.win.WinGestureSupport` 与 `com.sun.glass.ui.win.WinDnDClipboard`
（`jni-config.json` 里有），所以在 Windows 原生 exe 上抛两条
`ClassNotFoundException`，**手势与拖放静默失效**（窗口照开、只刷栈）。"
证据来自姊妹工程 `acnxo/LegionFanControl` 的最小复现，它按本仓库清单生成元数据。

本条记录的是**核验这条认定**的过程，以及一个**反转**：缺口的**事实**（反射段没这两条）成立，
但**成因与机理判断错了**——在本仓库里它并不是缺陷，Issue 里那句「JNI 登记覆盖不到」
也不成立。

## 2. 排查过程

| # | 动作 | 预期（若 Issue 的认定成立） | 实际 | 结论 |
|---|---|---|---|---|
| 1 | 数本仓库两份元数据里这两条 | 反射段 0 / jni 段 1 | 反射段 **0** / jni 段 **1** | 与 Issue 描述一致，缺口事实成立 |
| 2 | 全仓搜 `WinGestureSupport` | 找到 Java 侧 `Class.forName` 调用点 | 只有元数据自身与 `jni-config.json`，**无任何 Java 调用点** | 反射路径存疑 |
| 3 | 解 JavaFX **25** 的 `javafx-graphics-25-win.jar`，对 `WinWindow` / `WinApplication` 跑 `javap -p -c` | 有 `Class.forName("…WinGestureSupport")` | **0 处命中**（两个类都不引用它们） | **Java 侧不存在按名查找** |
| 4 | 全 jar 常量池扫描「谁引用这两个名字」 | 找到 Java 引用类 | 只有自身、`WinClipboardDelegate`（`new`），以及 **`glass.dll`** | 查找发生在 **native 侧** → 属 `FindClass` / `GetMethodID` 一族 |
| 5 | 查这两个类在 jni 清单里的状态 | 只有登记、没有守卫 | 自 **issue #39** 起就在 `PLATFORM_JNI_INIT_CLASSES` 里，且有测试断言 + `allDeclared*` 要求 | **早已登记且有守卫** |
| 6 | 去本仓库**真机**（CI Windows 腿真启动 exe）日志里找这两条异常 | 出现两条 `ClassNotFoundException` | run `38389103313`（`push: dev`，head `8f03c97`）的 `native-image-windows-amd64-jdk25` 腿**真的启动了 exe**（`Start-Process`，观察 20 秒），stdout 929 B / stderr 315 B，**两条都 0 次出现**，冒烟判 `通过` | **本仓库未复现** |
| 7 | 查 `WinDnDClipboard` 的静态可达性 | — | `WinClipboardDelegate` 里有 `new WinDnDClipboard(String)` | 它另有一条静态可达链兜底；`WinGestureSupport` 则**完全没有 Java 侧引用** |

被排除的假设见「排除项」一节（含**为什么能排除**）。

## 3. 根因

一句话：**把「姊妹工程的故障」当成了「本仓库的元数据缺口」——错在没先判「查找发生在哪一侧」。**

- **触发条件**：JavaFX 25 + 本仓库元数据；姊妹工程用**自己的管道**按本仓库清单生成配置。
- **机理**：这两个类的查找发生在 **native（`glass.dll`）**，走 `FindClass` / `GetMethodID`
  一族；这一族的**正确归宿是 `jni-config.json`**（本仓库早已登记，见 §2 第 5 行）。
  而 `reachability-metadata.json` 的 **`reflection` 段对应的是 Java 侧 `Class.forName`**——
  JavaFX 25 的 win jar 里根本**没有**这样的调用（§2 第 3 行）。
  姊妹工程之所以真机报错，最可能是**其清单生成只吃了反射段**，于是这两条在它的配置里丢了；
  在那里「补进反射侧即消失」是**结果正确、机理误判**——补进去让类进了镜像，native 查找自然能成。
- **为什么没被及早发现**：CI 的 Windows GUI 冒烟**只启动界面、不做手势 / 拖放交互**，
  而「没看到报错」在旧判据下无法与「这段路径真的没问题」区分——这正是本项目
  已在 `TS-2610-003` 与 `TS-2610-032` 反复记下的那条教训。

## 4. 修复

**没有把它当本仓库的缺陷去修**（它在本仓库不成立），但做了两件有实际收益的事：

① **补登记两条**（`reachability-metadata.json` 反射段，473 → **475** 条），理由如实写清是**两条**：
   让这两个类**确定进镜像**、不依赖静态可达链（`WinGestureSupport` 在 Java 侧零引用）；
   以及**对齐姊妹工程按反射段生成的口径**（那里缺这两条，其真机上确认会抛异常）。

```json
{"type": "com.sun.glass.ui.win.WinGestureSupport", "jniAccessible": true,
 "allDeclaredConstructors": true, "allDeclaredMethods": true, "allDeclaredFields": true},
{"type": "com.sun.glass.ui.win.WinDnDClipboard", "jniAccessible": true,
 "allDeclaredConstructors": true, "allDeclaredMethods": true, "allDeclaredFields": true}
```

② **补两道守卫**，把「别让它悄悄回退」变成机器可见：

| 守卫 | 位置 | 守什么 |
|---|---|---|
| `reachabilityMetadataRegistersWindowsGlassGestureAndDndTypes` | `NativeImageMetadataTest` | 反射段这两条仍在（且 `allDeclared*`） |
| `require_meta` ×2 | `DesktopNative.yml` 步骤⑧ | **生效中**的可达性元数据里有这两条（三平台腿都跑） |
| `require_jni` ×2 | `DesktopNative.yml` 步骤⑧b | **真正的查找清单** `jni-config.json` 里仍有这两条 |

> **取舍**：也可以「只加 ⑧b 的 jni 守卫、不碰反射段」——那更贴合机理，但姊妹工程那条链仍会断。
> 两条都加是**双保险**，代价是镜像里多一点反射元数据（这两个类本就已进镜像）。

## 5. 验证

- **静态**：新增测试断言随 `Build` / `Gate` 跑（本 PR 的 CI）；
- **工作流守卫**：⑧ / ⑧b 两条守卫在本 PR 触发的 `DesktopNative` 三条平台上跑（`require_*` 失败只写
  `::warning::` + 摘要，属可选管线的既有口径）；
- **真机观感**：手势（拖动窗口边缘）与拖放（把文件拖到窗口上）是否正常——
  **未做，须以 ACANX 的判断为准**（见「未决问题 Q1」）；
- **反向验证**：本条**没有**可用的「改前失败 / 改后通过」闭环——因为本仓库**改前也不报错**。
  这一点必须写明白，不能拿「CI 绿了」冒充本条的验收依据。

## 6. 教训与沉淀

- **最该记住的一条**：**先判「查找发生在哪一侧」，再决定「登记到哪份清单」**。
  Java 侧按名查找 → `reflection` 段；native 侧按名查找 → `jni-config.json`。
  拿「姊妹工程补了反射段就好了」直接推到本仓库，是**结果对、因果错**。
- **同族故障在别的工程复现 ≠ 本仓库有缺口**；跨工程移植结论前，先在本仓库找**调用点**（`javap` / 常量池扫描）。
- 已落到：`NativeImageMetadataTest`（新断言）、`DesktopNative.yml` 步骤 ⑧ / ⑧b（`require_meta` / `require_jni`）、
  `Docs/Design/DesktopNativeDesign.md` §R2 的数值与结论；
- 遗留事项：真机手势 / 拖放验收（Q1）→ 仍挂在 issue **#125** 上。

## 7. 涉及文件

| 类型 | 路径 |
|---|---|
| 元数据 | `aha-desktop/src/main/resources/META-INF/native-image/com.acanx.module.aha/aha-desktop/reachability-metadata.json` |
| 测试 | `aha-desktop/src/test/java/com/acanx/module/aha/desktop/NativeImageMetadataTest.java` |
| 工作流 | `.github/workflows/DesktopNative.yml`（步骤 ⑧ / ⑧b） |
| 文档 | `Docs/Design/DesktopNativeDesign.md`、本文件、`Docs/Troubleshooting/README.md`、`CHANGELOG.md`、`Docs/DevLog/20261013-17.md` |
| 外部（只读） | `javafx-graphics-25-win.jar`（`javap` / 常量池扫描） |

---

## 结论分层

> **必备**（`TroubleShootingDocSpec.md` §2.2）。三层逐条标注依据。

### 已观测事实

- 本仓库反射段这两条：**改前 0 条**、改后各 1 条（`python3 -c json.load`，反射段总条目 473 → 475）；
  `jni-config.json` 里**一直是各 1 条**。
- JavaFX 25 的 `javafx-graphics-25-win.jar` 里两个类**都存在**（`WinGestureSupport.class` 2500 B、
  `WinDnDClipboard.class` 1574 B）。
- 对 `WinWindow` / `WinApplication` 跑 `javap -p -c`，**检索 `Class.forName` 命中 0 处**；
  全 jar 常量池扫描，引用这两个名字的只有它们自身、`WinClipboardDelegate`（`new`）与 **`glass.dll`**。
- `WinClipboardDelegate` 字节码里有 `new com/sun/glass/ui/win/WinDnDClipboard` +
  `invokespecial <init>(Ljava/lang/String;)V`。
- CI run `38389103313`（Windows 腿）**真的启动了原生 exe**：`[aha-version]`、`[ui-fxml-loaded]` 均命中，
  进程存活 20 秒，stderr **315 字节**且**不含**这两个类名（全日志 0 次）。
- 这两个类自 **issue #39** 起就在 `NativeImageMetadataTest.PLATFORM_JNI_INIT_CLASSES` 名单中，
  且被断言要求 `allDeclaredMethods`。

### 机制 / 行为模型

- JavaFX 的 Glass 层按「谁在查」分成两条路径：**Java 侧 `Class.forName`**（对应
  `reachability-metadata.json` 的 `reflection` 段）与 **native 侧 `FindClass` / `GetMethodID`**
  （对应 `jni-config.json`，工作流注释亦写明「`reachability-metadata` 的 schema v1.2.0 不含 jni 段」）。
- `WinGestureSupport` / `WinDnDClipboard` 属于**后者**：调用点在 `glass.dll` 里。
- 因此「补进反射段」并不纠正查找路径，而是**让类进镜像**；姊妹工程的异常消失，正是这个副作用。
- **推定（未实测）**：姊妹工程的元数据生成管道只消费了本清单的反射段，故这两条在其配置里缺失。
  本条只做到「最可能的解释」，未去对方仓库核对生成脚本（见「未决问题 Q2」）。

### 未定位 / 未复现

- **未复现**：本仓库在 Windows 原生 exe 上**没有**观测到这两条异常（CI 真启动，见上）。
- **未验证**：手势与拖放**是否真的可用**——CI 冒烟不做交互，本机无 Windows 环境。
- **未定位**：`WinGestureSupport` 的 native 查找具体发生在哪一次调用（
  `_createWindow` 内的哪条分支）——JavaFX 25 里该方法本身是 `native`，无 Java 侧调用点可读；
  本次只确认「启动 + 运行循环期间未抛异常」。
- **未核对**：姊妹工程的清单生成脚本。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 反射段总条目 | `python3 -c "json.load(...)['reflection']"` | 改前 **473** → 改后 **475** |
| 反射段中这两条 | `grep -c "WinGestureSupport" reachability-metadata.json` | 改前 **0** → 改后 **1**（两条同理） |
| jni 段中这两条 | `grep -c "WinGestureSupport" jni-config.json` | **1**（改前改后一致） |
| 类是否存在于 JavaFX 25 | `unzip -l javafx-graphics-25-win.jar \| grep Win` | `WinGestureSupport.class` 2500 B、`WinDnDClipboard.class` 1574 B |
| Java 侧是否有 forName | `javap -p -c com/sun/glass/ui/win/WinWindow.class` | `Class.forName` 命中 **0** 处 |
| 谁引用这两个名字 | `grep -rla <名字> <解包目录>` | 自身、`WinClipboardDelegate.class`、**`glass.dll`** |
| 本仓库真机启动结果 | CI run `38389103313` / job `115268204774` | 冒烟 `通过`；`out=929 B`、`err=315 B`；`WinGestureSupport`/`WinDnDClipboard` 各 **0** 次 |
| 同族历史先例 | `TS-2610-021` / `TS-2610-022` / `#37` / `#39` | JNI 侧漏登记的真实症状是 `NoClassDefFoundError` / `NoSuchMethodError` + 随后 segfault |

> 本条的「反证」不是一条命令的输出，而是**多份材料的交叉**（字节码 + 常量池 + 真机日志）。
> 单看任何一份都不足以定案。

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「反射段缺这两条 ⇒ 本仓库启动即抛 `ClassNotFoundException`」 | Windows 腿真启动 exe 20 秒，stderr 315 B 内无这两条；窗口正常装起来（`[ui-fxml-loaded]`） |
| 「这两个类在 Java 侧被 `Class.forName` 加载」 | `javap -p -c` 对 `WinWindow` / `WinApplication` 检索 `Class.forName`：**0 处命中**；全 jar 常量池里没有 Java 引用方 |
| 「只在 JNI 侧登记覆盖不到」 | native 侧查找本就对应 `jni-config.json`；且这两个类**自 #39 起已在册并有测试断言** |
| 「`jni-config.json` 压根没被 native-image 采用」 | 同一份清单的 `WinWindow` 条目在 0.1.2 已证明生效（#39 修复后 Windows 启动不再抛 `NoSuchMethodError`，见 `TS-2610-022`） |
| 「`WinDnDClipboard` 会因缺登记而查不到」 | 它另有静态可达链：`WinClipboardDelegate` 直接 `new` 它 |
| 「CI 绿了 ⇒ 手势 / 拖放没问题」 | **不能这样反推**：冒烟只启动界面、不做交互；本条因此**没有**给出手势可用的结论（见「未决问题 Q1」） |

## 未决问题

**Q1 真机上手势与拖放是否真的可用**

现状：未验证。CI 的 Windows 腿只做「界面装起来」的正面断言，不做交互；本机无 Windows 环境。
阻塞点：需要 ACANX 在真机上试「拖动窗口边缘 / 把文件拖到窗口上」——
按 `IssueTrackingSpec.md` §4，这类**人工判断项以 ACANX 的判断为准**，不得写成机器结论。

**Q2 姊妹工程（`acnxo/LegionFanControl`）的清单生成为何只吃反射段**

现状：未核对。本条仅给出「最可能的解释」（推定，见「机制 / 行为模型」）。
阻塞点：另一仓库、另一条清单管道，不在本轮回合范围内；若确认，应当在那边的记录里订正，
避免它日后再按同一口径漏掉别的 JNI-only 类。
