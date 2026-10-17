# TS-2610-016 · 修复 issue #29：原生镜像的构建报告改为独立发布包

> 日期：2026-10-08
> 作者：@ACANX（与 AI 助手协作）
> 关联分支：`fix/issue-29-native-report-in-zip`
> 关联记录：`Docs/Design/DesktopNativeDesign.md` §5.4、`Docs/Troubleshooting/TS-2610-015-Native-Image-Args-Tuning.md`、issue #29

## 1. 背景

issue #29：打出的 `AHA-Desktop-Native-0.1.1.00025-windows-amd64-jdk25.zip` 里
**没有** `aha-desktop-native-build-report.html`。

而 `TS-2610-015-Native-Image-Args-Tuning.md` 与 `Docs/Dbsx.txt` 都把「随镜像包打包 build-report.\*」记为
**已完成**，`DesktopNativeDesign.md` §5.4 的「可对账设施」也写着「随包进 zip」——
**文档说做了、代码里却没有**：`dist-native.xml` 的 include 列表只有 `README.txt` 与
`reachability-metadata.json`，报告被写在注释里当成了「可选项」，实际被排除。

## 2. 定案（用户 2026-10-08）

报告**不打进镜像包**，而是打成**独立发布包**，与镜像包同批挂到发布页：

```
AHA-Desktop-Native-Report-<版本>-<系统>-<架构>-jdk<JDK>.zip
例：AHA-Desktop-Native-Report-0.1.1.00025-windows-amd64-jdk25.zip
```

- 镜像包与报告包各司其职：前者给人**运行**，后者给人**分析**；
- 文件名与镜像包**同族**：版本号、系统、架构、JDK 轴全在名里，下载时一眼对上同一版本；
- 报告包内含：构建报告 + 原生镜像参数 + 可达性元数据（一组对账材料）；
- 同时作为 CI 制品保留 **90 天**（比镜像包的 14 天长）——报告是长期资产。

## 3. 改动

| 文件 | 改动 |
|---|---|
| `aha-desktop-native/src/assembly/dist-native.xml` | 明确报告不进镜像包（注释写清去处与命名），include 列表保持不含报告 |
| `.github/workflows/DesktopNative.yml` | 新增「打独立报告包」「上传报告包制品」两步；产物自证新增「报告包是否生成」回归守卫；下载指引与 release body 同步 |
| `aha-desktop-native/src/native/README.txt` | 「构建报告」段改为说明独立发布包名 |
| `Docs/Design/DesktopNativeDesign.md` §5.4 | 报告作为独立发布物的定案与命名规范 |
| `CHANGELOG.md` | 「变更」「修复（issue #29）」两条 |
| `.agents/skills/java-app-graalvm-native-image-compile/references/args-cookbook.md`、`SKILL.md` | 技能同步：独立发布包 + 同族命名 + 回归守卫查发布物 |
| `Docs/Dbsx.txt` | 「随镜像包打包」改为独立发布包 |

## 4. 实测（本机，`-Dnative.skip=true`，不真编）

1. 构造假报告 `aha-desktop-native/target/native/aha-desktop-native-build-report.html`；
2. 跑 `-Pdesktop-native -pl aha-desktop-native,aha-desktop -am package -Dnative.skip=true`；
3. **镜像包** `Dist/aha-desktop-native-9.9.9.00099-jdk25.zip` 内**不含** build-report，
   证明排除正确；
4. **报告包**按工作流同一段 python 打成
   `Dist/AHA-Desktop-Native-Report-9.9.9.00099-windows-amd64-jdk25.zip`，
   含报告 + 两份参数 + 可达性元数据共 4 个文件，打包与命名均符合预期；
5. `bin/CheckScripts.py`、`bin/CheckDocs.py`、`bin/CheckSkills.py` 全绿。

## 5. 教训

1. **「文档说已完成」必须有可执行的自证**。这次是文档与代码两条线各走各的：
   文档写「已随包交付」，代码里却只留了一行注释做选项。
   回归守卫的口径也从「查 workdir」升级为「查发布物」——
   「workdir 里有报告」不等于「发布物里有报告」。
2. **不要把两类交付物硬塞进一个包**。镜像包给人运行、报告给人分析；
   分开发布让下载者按需取件，报告体积（34~36 MB）也不会再影响镜像包。

## 6. 涉及文件清单

- `aha-desktop-native/src/assembly/dist-native.xml`
- `.github/workflows/DesktopNative.yml`
- `aha-desktop-native/src/native/README.txt`
- `Docs/Design/DesktopNativeDesign.md`
- `Docs/Dbsx.txt`
- `CHANGELOG.md`
- `.agents/skills/java-app-graalvm-native-image-compile/references/args-cookbook.md`
- `.agents/skills/java-app-graalvm-native-image-compile/SKILL.md`

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- issue #29 的现象：打出的 `AHA-Desktop-Native-0.1.1.00025-windows-amd64-jdk25.zip` 里
  **没有** `aha-desktop-native-build-report.html`。
- 而文档口径是「已完成」：`TS-2610-015` 与 `Docs/Dbsx.txt` 都把「随镜像包打包 build-report.\*」
  记为**已完成**，`DesktopNativeDesign.md` §5.4 的「可对账设施」也写着「随包进 zip」。
- 代码实况：`dist-native.xml` 的 include 列表**只有** `README.txt` 与 `reachability-metadata.json`
  ——报告被写在注释里当成「可选项」，**实际被排除**。
  ⇒ **文档说做了，代码里却没有。**
- 定案（用户 2026-10-08）：报告**不打进镜像包**，打成**独立发布包**，与镜像包同批挂到发布页：

  ```text
  AHA-Desktop-Native-Report-<版本>-<系统>-<架构>-jdk<JDK>.zip
  例：AHA-Desktop-Native-Report-0.1.1.00025-windows-amd64-jdk25.zip
  ```

- 本机实测（`-Dnative.skip=true`，**不真编**）：
  ① 构造假报告 `aha-desktop-native/target/native/aha-desktop-native-build-report.html`；
  ② 跑 `-Pdesktop-native -pl aha-desktop-native,aha-desktop -am package -Dnative.skip=true`；
  ③ **镜像包** `Dist/aha-desktop-native-9.9.9.00099-jdk25.zip` 内**不含** build-report（排除正确 ✅）；
  ④ **报告包** `Dist/AHA-Desktop-Native-Report-9.9.9.00099-windows-amd64-jdk25.zip` 含
  报告 + 两份参数 + 可达性元数据共 **4 个文件**（打包与命名符合预期 ✅）；
  ⑤ `Check{Docs,Skills,Scripts}` 全绿。
- 报告包同时作为 CI 制品保留 **90 天**（比镜像包的 **14 天**长）——报告是长期资产。
- 报告体积 **34~36 MB**（「5、教训」第 2 条给出）。

### 机制 / 行为模型

- **「文档说已完成」必须有可执行的自证**：本次是文档与代码**两条线各走各的**
  ——文档写「已随包交付」，代码里只留一行注释当选项。
- **回归守卫的口径要从「查 workdir」升级为「查发布物」**：
  「workdir 里有报告」**不等于**「发布物里有报告」。
  据此工作流的产物自证新增了「报告包是否生成」这条守卫。
- **不要把两类交付物硬塞进一个包**：镜像包给人**运行**、报告给人**分析**；
  分开发布让下载者按需取件，报告的 34~36 MB 也不再拖累镜像包。
- 命名**同族**（版本号 / 系统 / 架构 / JDK 轴全在名里）的意义：下载时**一眼对上同一版本**。

### 未定位 / 未复现

- **未在真实 CI 上验证**报告包的产出：本机是用**假报告 + `-Dnative.skip=true`** 模拟的，
  CI 侧的实际行为**当时未观测**（只加了「报告包是否生成」的守卫）。
- 报告包的 **90 天保留策略**未观测（是否按预期到期，无从验证）。
- **已发布的旧版本（0.1.1.00025）是否需要补发报告包**：档案**未提及、未处理**。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 现象 | issue #29 与下载到的释放包 | `AHA-Desktop-Native-0.1.1.00025-windows-amd64-jdk25.zip` 内**没有** build-report |
| 文档与代码的落差 | `dist-native.xml` 的 include 列表 | 只有 `README.txt` + `reachability-metadata.json`；报告**只写在注释里** |
| 独立报告包的命名 | 定案 + 实测产物 | `AHA-Desktop-Native-Report-<版本>-<系统>-<架构>-jdk<JDK>.zip` |
| 实测①：造样本 | 手工构造 | `aha-desktop-native/target/native/aha-desktop-native-build-report.html` |
| 实测②：跑打包 | `-Pdesktop-native -pl aha-desktop-native,aha-desktop -am package -Dnative.skip=true` | 通过（不真编） |
| 实测③：**镜像包排除正确** | 解包 `Dist/aha-desktop-native-9.9.9.00099-jdk25.zip` | **不含** build-report ✅ |
| 实测④：**报告包内容** | 解包 `Dist/AHA-Desktop-Native-Report-9.9.9.00099-windows-amd64-jdk25.zip` | 报告 + 两份参数 + 可达性元数据 = **4 个文件** ✅ |
| 报告包体积 | 「教训」段 | **34~36 MB** |
| 制品保留期 | 定案 | 报告包 **90 天**；镜像包 **14 天** |
| 检查 | `bin/Check{Docs,Skills,Scripts}.py` | 全绿 ✅ |

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「报告已随包交付」（文档口径） | `dist-native.xml` 的 include 列表里没有它，只在**注释**中被当可选项（「1、背景」「3、改动」） |
| 「workdir 里有报告 ⇒ 发布物里有报告」 | 守卫口径已升级为「**查发布物**」（「5、教训」第 1 条） |
| 「报告应与镜像包同包」 | 定案为**独立包**（含同族命名规范）；报告 34~36 MB 会拖累镜像包（「2、定案」「5、教训」第 2 条） |
| 「本机无法验证打包」 | 用**假报告 + `-Dnative.skip=true`** 即可验证「排除正确」与「打包命名」（「4、实测」） |

## 未决问题

**Q1 真实 CI 上报告包真的产出了吗？**

现状：本机用**假报告**模拟验证；CI 侧行为**当时未观测**——工作流新增了
「报告包是否生成」的回归守卫，但还没有一次运行来证明它。
阻塞点：需要一次真实的 `DesktopNative` 运行（与 `TS-2610-014` 的 N-12 同批）。

**Q2 已发布的旧版本要不要补发报告包？**

现状：**未提及、未处理**（例如 `0.1.1.00025` 那次）。
阻塞点：需明确「报告包从哪一版开始提供」的口径。

**Q3 报告包的长期归档策略？**

现状：随发布页挂出 + 作为 CI 制品保留 **90 天**。
阻塞点：90 天之后是否需要长期归档（例如放 Release 资产或对象存储），未议。

**Q4 「文档说已完成 = 实际没有」这类不一致能机器拦吗？**

现状：靠**人工发现**（本条是用户报的 issue #29）。
阻塞点：可考虑断言「文档中声称随包交付的每一项，都必须在 assembly 描述符 / 工作流里能找到」，
尚未评估。
