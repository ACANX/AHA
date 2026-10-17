# TS-2610-037 · 原生桌面 exe 的 `OS Information:` 报 `Windows 8.0 build 9200`

**日期**：2026-10-14
**关联 Issue**：issue [#151](https://github.com/ACANX/AHA/issues/151)（承接 issue #60 的 Q3）
**关联档案**：`TS-2610-034`（Prism D3D 驱动 / OS 信息全零，本轮的前置）
**关联设计**：`Docs/Design/DesktopNativeDesign.md`（原生镜像参数与平台腿）

---

## 1. 现象

真机（Windows，2026-10-13）与 CI 的 Windows 腿都能看到：原生桌面 exe 以 `prism.verbose` 启动后
打印的 OS 信息是

```
OS Information: Windows 8.0 build 9200
```

而机器与 runner 都是 **Windows 10 / 11**（10.0 家族）。同一台机器上 **JVM 模式**打印的是真实版本。

驱动 / 设备信息（`ven_*` / `dev_*`）由 issue #60 修好，**只有 OS 版本这一项是错的**。

影响不止「显示难看」：JavaFX / Prism 内部按 OS 版本走的分支（DPI 与部分渲染路径）看到的也是 8.0。

## 2. 排查过程

| # | 假设 | 做法 | 结果 |
|---|---|---|---|
| 1 | 「仓库里已有清单实现，只是没生效」 | 全仓搜 `manifest` / `supportedOS` / `MANIFESTINPUT` / GUID | ❌ 推翻：**0 处**（`aha-desktop-native/`、`.github/workflows/`、`Desktop` 全无）——这条从未实现过 |
| 2 | 「GraalVM 有专门的清单选项」 | 扫 `svm-25.0.0.jar` 里所有含 `anifest` 的选项类 | ❌ 没有专用选项 |
| 3 | 「用 `-H:NativeLinkerOption` 透传给 MSVC 链接器」 | 读 `com.oracle.svm.hosted.image.CCLinkerInvocation$Options` 等 8 个类 | ✅ `NativeLinkerOption` 存在，且是**可累加的多值**选项（可重复两次）；`WindowsCCLinkerInvocation` 会把它交给链接器 |
| 4 | 「把参数写进共享的 `native-image-args.txt`」 | 看 `aha-desktop-native/pom.xml` 的 exec 参数与 profile | ❌ 那份是**跨平台共享**的；`/MANIFEST*` 是 MSVC 链接器参数，Linux / macOS 腿会**直接链接失败** |
| 5 | 「清单用相对名 `app.manifest`，链接器会按工作目录解析」 | Windows 腿真跑（`%TEMP%` 路径出现在错误里） | ❌ 推翻：链接器的工作目录是 **native-image 自己的构建临时目录**，不是 Maven 的 `workingDirectory` |

## 3. 根因

**`GetVersionEx` / `GetVersion` 家族在「没有 Windows 10 兼容清单」的可执行文件里一律返回
`6.2.9200`（= Windows 8.0）**——这是 Windows 的既定兼容性行为，**与真实系统版本无关**。

- `java.exe` 自带清单 ⇒ **JVM 模式**打印真实版本；
- GraalVM `native-image` 生成的 exe **默认不带**清单 ⇒ 原生模式被 shim 成 8.0。

也就是说：不是我们读错了字段，而是 Windows 主动对「未声明兼容性」的进程说了谎。

## 4. 修复

| 环节 | 落点 |
|---|---|
| 清单本体 | `aha-desktop-native/src/native/app.manifest`（**纯 ASCII**，只声明 Win10/11 的 `supportedOS`；刻意**不带** `assemblyIdentity`——它要与链接器自动生成的那份**合并**，重复声明会冲突） |
| Windows 专属参数 | `.../src/native/native-image-args-windows.txt`：`-H:NativeLinkerOption=/MANIFEST:EMBED` |
| 平台专属的表达 | Maven：`native-windows-manifest` profile（按 `<os><family>windows</family>` 激活）只改**一个属性**；非 Windows 指向只有注释的空档文件 `native-image-args-extra.txt` |
| 清单路径 | 由 Maven 用 `${project.basedir}` 展开成**绝对路径**，作为**主类之前**的命令行参数传入（`native.manifest.input`）；非 Windows 的默认值是无害且幂等的 `-H:+UnlockExperimentalVMOptions` |

三个硬约束（都踩过或差点踩）：

1. **参数必须在主类之前**：native-image 只把主类之后的参数当**应用参数**，写在后面会被静默吞掉；
2. **清单路径必须绝对**（见第 5 节坑 A）；
3. **`/MANIFEST:EMBED` 不能省**：只给 `/MANIFESTINPUT` 时，合并后的清单会落到**单独的**
   `.manifest` 文件里，而不是嵌进 exe。

## 5. 验证

### 坑 A：相对路径让 Windows 原生构建整条失败

第一次实现用了相对名 `app.manifest`，Windows 腿报：

```
/MANIFEST:EMBED
/MANIFESTINPUT:app.manifest
\\?\C:\Users\RUNNER~1\AppData\Local\Temp\SVM-13403134599471227632\app.manifest :
    general error c1010070: Failed to load and parse the manifest.
    The system cannot find the file specified.
LINK : fatal error LNK1327: failure during running mt.exe
[ERROR] AHA Desktop Native ................................. FAILURE [05:12 min]
```

⇒ 链接器的工作目录是 **native-image 自己的构建临时目录** `%TEMP%\SVM-…`（不是 Maven 的
`workingDirectory`），相对路径自然解析到那里。改成绝对路径后通过。

### 坑 B：这次失败**没有让 CI 变红**

本工作流刻意把容错放在**步骤级**（`continue-on-error: true`，见文件头「关于「可选」」一节），
于是：

| 观测 | 值 |
|---|---|
| `构建原生镜像` 步骤 **conclusion** | `success` |
| 同一步骤 **outcome** | `failure` |
| 作业 / 整次运行 | 绿色 |
| 「产物自证」步骤的 note | `没有产出原生可执行文件（构建步骤结论：failure）` |
| 下游「清单自证」「运行冒烟（GUI）」 | **skipped**（`produced != true`） |

⇒ **「CI 绿」在这条管线里不等于「构建成功」**。排查方法（记下来，下次先看这个）：

```bash
gh api "/repos/ACANX/AHA/actions/jobs/<job-id>" --jq '.steps[] | "\(.conclusion)\t\(.name)"'
```

对应地，「产物自证」步骤把构建步骤的**结论原文**写进摘要，就是为了让这种情况有字可读。

### 已完成的验证

| 项 | 做法 | 结果 |
|---|---|---|
| 清单真的在包里 | Windows 腿新增步骤：读 `Dist/AHA-Desktop-Native-*.zip` 里的 exe，搜 Win10 GUID | ✅（该步骤自身因下一个坑挂了，但断言已跑到打印那一步） |
| **OS 版本断言（关键证据）** | 冒烟（GUI 真启动）捕获的 stdout | `Windows version 10.0 build 26100`（不再是 `8.0 build 9200`）+ `✅ GUI 冒烟：通过：命中 [aha-version] 与 [ui-fxml-loaded]` |
| JVM 同机对照 | 验收标准 3 | ⏳ 需 ACANX 真机 |
| 真机原生 exe | 验收标准 1 | ⏳ 需 ACANX 真机 |

## 6. 教训与沉淀

1. **链接器的工作目录不等于 Maven 的 `workingDirectory`**：`native-image` 会在自己的构建临时目录
   里调链接器 ⇒ 任何以「相对路径 + 工作目录」为基础的假设都要改成**传绝对路径**；
2. **给旧式 MS 工具（mt.exe）的文件用纯 ASCII**：编码赌注不值得下（`.ps1` 需要 UTF-8 BOM 是同一类教训，
   这次干脆不含非 ASCII 字节）；
3. **有 `continue-on-error` 的步骤必须看 `outcome` 而不是 `conclusion`**：
   本仓库刻意容忍原生构建失败（它是数据点、不是交付物），因此更要主动看摘要；
   「加了参数但没生效」与「构建成功」在作业层面**长得一模一样**——这正是两道自证门禁存在的理由；
4. **清单声明的语义是「这个 exe 声明兼容哪些系统」，不是「能跑的最低版本」**：
   声明后 `GetVersionEx` 返回真实版本（我们要的）；同时**不再有 shim 兜底**——
   低于 Win10 的系统本就不在目标平台内（见 `DesktopDesign.md` 的目标平台清单）；
5. **在 Windows 上往管道打中文 / emoji 要显式设编码**（坑 C）：自证步骤的
   `print("✅ 清单已嵌入：…")` 报 `UnicodeEncodeError: 'charmap' codec can't encode character '\u2705'`
   ——因为 stdout 在 CI 里是**管道**（不是控制台），按 cp1252 编码。断言本身其实是**通过**的，
   是那一行字把步骤弄挂了。写法照 `Script/Python/*.py`：
   `stream.reconfigure(encoding="utf-8", errors="replace")`（再加 `PYTHONIOENCODING=utf-8` 双保险）。
   **教训**：日志输出也是代码，会自己造出「看起来像断言失败」的红。

## 7. 涉及文件

| 类型 | 路径 |
|---|---|
| 新增 | `aha-desktop-native/src/native/app.manifest`、`aha-desktop-native/src/native/native-image-args-windows.txt`、`aha-desktop-native/src/native/native-image-args-extra.txt` |
| 修改 | `aha-desktop-native/pom.xml`（多传一个 `@参数文件` + `${native.manifest.input}`；新增 `native-windows-manifest` profile） |
| 修改 | `.github/workflows/DesktopNative.yml`（新增「清单已嵌进 exe」自证步骤；冒烟新增两条 OS 版本断言 + 摘要行） |

## 结论分层

### 已观测事实

- 原生模式打印 `Windows 8.0 build 9200`；**同机** JVM 模式打印真实版本；
- 仓库此前**没有**任何清单实现（全仓 0 处）；
- GraalVM 25 的 `NativeLinkerOption` 是**可累加多值**选项，`WindowsCCLinkerInvocation` 会把它交给链接器；
- 用相对路径时，mt.exe 报 `c1010070 … The system cannot find the file specified`，
  且错误信息里出现的路径是 `%TEMP%\SVM-…`（native-image 的构建临时目录）；
- 改用绝对路径后，Windows 腿构建通过（CI 记录）；
- 「构建原生镜像」步骤 `conclusion=success` 而 `outcome=failure`，作业仍是绿的。

### 机制 / 行为模型

- `GetVersionEx` 家族对「未声明 Win10 兼容性」的进程返回 `6.2.9200`——这是 OS 的兼容层行为，
  与真实版本无关；`supportedOS` 声明会关掉这个 shim；
- `native-image` 把 `-H:NativeLinkerOption=<arg>` 原样追加到 MSVC 链接器命令行；
  链接器的工作目录是 `%TEMP%\SVM-<随机>`；
- 参数文件（`@file`）在主类之前被读取，因此**平台专属参数只能从这个位置进入**；
- 工作流的步骤级容错把「失败」降级为「作业绿 + 摘要里的一句话」。

### 未定位 / 未复现

- **未在本机复现**：没有 Windows 环境，无法本地跑 `mt.exe` / 看真实 `OS Information:`；结论依据是
  CI 日志（原始输出已抄录）与 GraalVM 类文件的选项定义；
- **未验证**：清单嵌进去之后，**真机**原生 exe 的 `OS Information:` 是否变成真实版本
  （CI runner 是 Windows Server，属 10.0 家族，只能证明「不是 8.0」）；
- **未评估**：JavaFX / Prism 里依赖 OS 版本的分支（DPI 等）在修复后的实际行为变化——
  本轮只保证「字段变对」，未做渲染差异对照。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 仓库内清单实现数 | `grep -rn "manifest\|supportedOS\|MANIFESTINPUT"` | **0** |
| GraalVM 选项类 | `svm-25.0.0.jar` 内 `CCLinkerInvocation$Options` | `NativeLinkerOption` = `HostedOptionKey<AccumulatingLocatableMultiOptionValue$Strings>`（可累加） |
| 专用清单选项 | 扫全部含 `anifest` 的选项类 | 无 |
| 失败时的链接器错误 | CI（`native-image-windows-amd64-jdk25`，PR #161） | `c1010070 … Failed to load and parse the manifest. The system cannot find the file specified.` + `LNK1327` |
| 失败时的解析路径 | 同上 | `\\?\C:\Users\RUNNER~1\AppData\Local\Temp\SVM-13403134599471227632\app.manifest` |
| 失败被隐藏 | `gh api .../jobs/<id> --jq '.steps[]'` | `success 构建原生镜像`（而「产物自证」note 写 `构建步骤结论：failure`） |
| 清单非 ASCII 字节 | `python3` 读字节计数 | **0**（纯 ASCII） |
| **修复后 CI 实测的 OS 版本** | CI 冒烟捕获的原生 exe stdout（Windows Server 2025） | `Windows version 10.0 build 26100` |
| 冒烟判定 | 同上 | `✅ GUI 冒烟：通过：命中 [aha-version] 与 [ui-fxml-loaded]，且启动期未崩` |
| 自证步骤的失败（坑 C） | 同一 run 的步骤级结论 + 日志 | `UnicodeEncodeError: 'charmap' codec can't encode character '\u2705'…`（退出码 1） |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「Prism 字段没登记 / 回写失败」（issue #60 那类） | 驱动与设备信息已正确；全零指纹（`Windows version 0.0 build 0`）未出现 |
| 「GraalVM 有专门选项，只是没用」 | 扫类文件确认无专用清单选项 |
| 「JVM 模式也错」 | 真机 JVM 模式打印真实版本 ⇒ 差异只在「exe 有没有清单」 |
| 「写进共享参数文件即可」 | `/MANIFEST*` 是 MSVC 链接器参数，Linux / macOS 腿会链接失败 |
| 「相对路径够用（链接器会按 Maven 工作目录解析）」 | 失败日志里的 `%TEMP%\SVM-…` 路径直接推翻 |
| 「CI 绿就说明构建成功」 | 步骤 `outcome=failure` / `conclusion=success`，且下游步骤全被跳过 |

## 未决问题

**Q1 真机确认（验收标准 1 / 3）**

现状：CI 只能证明「清单嵌进去了」且 CI runner 上版本不是 8.0。
阻塞点：需 ACANX 在 Windows 上跑原生 exe 看 `OS Information:`，并与同机 JVM 模式对照。

**Q2 CLI 原生包要不要同样处理**

现状：CLI 原生 exe 也没清单（同一机制）。它的可见症状目前**没有**——`version` / `--help` 不打印 OS 版本。
阻塞点：属「是否主动暴露 OS 版本」的产品取舍；若要处理，做法与桌面端完全一致（多一个参数文件 + 一个属性）。

**Q3 JavaFX / Prism 按 OS 版本分支的行为变化**

现状：本轮只保证字段正确，未做渲染 / DPI 对照。
阻塞点：需要能看界面的环境（ACANX）与可复现的对照项。
