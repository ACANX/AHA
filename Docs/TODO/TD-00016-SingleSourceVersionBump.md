# TD-00016 SingleSourceVersionBump

> 待办编号：TD-00016
> 标题：版本切换收敛为「改一行」——引入 `${revision}` + 单一读取入口 + 一致性校验
> 状态：☐ 未开始
> 跟踪 Issue：待确认（建议开 Issue）
> 创建日期：2026-10-09
> 最后更新：2026-10-09

---

## 来源

来自 ACANX 的批评与要求：**改版本号本该是一行参数的事**（Spring 项目即如此），
当前却要手改 **10 处版本号源 + 4 处文档声明**，且清单靠人记——已经漂移过一次
（`ReleaseProcess.md` 写「8 处 / 六个子模块」，实际 10 处 / 八个；PR #89 修正 §2 时还漏了 §4.2 第 249 行）。

## 根因（如实记录）

0.1.0 基线是在 IDE 默认生成的 POM 结构上起步的（默认即「根 `<version>` 硬编码 + 子模块
`<parent><version>` 硬编码」），**当时没有把「版本号」当成一项配置来设计**。
之后的所有改进都是补丁：

| 时间 | 补丁 | 没解决什么 |
|---|---|---|
| 0.1.1 切换 | 发现「只改根 POM 静默产出旧版本」→ 把清单从 1 处补到 8 处 | 仍是多处、仍靠人记 |
| 同轮 | 补 `./mvnw versions:set` 一条命令 | 不覆盖 `AppVersion` 与文档声明 |
| 同轮 | 写教训进 `Troubleshooting` / `ReleaseProcess` | 记录教训，未改设计 |
| 本次 | 清单 8 处 → 10 处（新增两个原生模块后被漂移） | 漂移的是"清单"，不是"机制" |

**结论**：一直在优化「怎么少踩坑」，没有回到「版本号本该只有一处」这个根问题。

## 目标状态

```bash
# 切版本 = 改这一行（其余全自动）
# pom.xml
<properties>
  <revision>0.1.3</revision>
</properties>
```

## 改造清单

### A. POM 层（9 个文件）

1. 根 `pom.xml`：`<version>${revision}</version>` + `<properties><revision>x.y.z</revision></properties>`；
2. 八个子模块：`<parent><version>${revision}</version></parent>`（`<relativePath>` 保留）；
3. 根 POM 引入 `flatten-maven-plugin`（版本固定在父 POM `<properties>`，交 Dependabot 跟踪）：
   - `flattenMode=resolveCiFriendliesOnly`、`updatePomFile=true`；
   - `flatten` 绑 `process-resources`、`flatten.clean` 绑 `clean`；
   - **⚠️ 必要性**：不 flatten 时 `install`/`deploy` 的 POM 里是字面量 `${revision}`，下游解析失败；
   - **与 Maven 4 的关系（重要）**：Maven 4 在 `install`/`deploy` 时会**自动生成 consumer POM**
     （CI-friendly 占位符已解析、build 段与 profiles 被剥离），其「解引用版本」的职责**已内置**，
     故 Maven 4 下 `flatten-maven-plugin` **不再必需**；但本仓库**同时保留 Maven 3.9.x 兼容基线**
     （`Compat.yml`），3.9.x 没有 consumer POM——**只要兼容线还在，flatten 就得留**。
4. `.gitignore` 加 `.flattened-pom.xml`。

### B. 版本读取收敛为单一入口（1 个脚本 + 4 处调用）

5. 新增 `bin/ProjectVersion.py`：输出根 POM 的 `<properties>/<revision>`（兼容回退读 `<version>`）；
6. 四个工作流的内联解析（各一行 `ET.parse("pom.xml")`）改为调用该脚本：
   `Build.yml:89`（打 tag）、`BuildJVMArtifacts.yml:89`、`CliNative.yml:91`、`DesktopNative.yml:106`；
   - **目的**：版本**源**将来怎么变（`<version>` ↔ `<revision>`），只改这一个脚本。

### C. Java 侧消除第 10 处手改

7. `AppVersion.FALLBACK_VERSION` 改为与版本无关的常量（如 `"dev"`），并更新其 Javadoc——
   它只在「IDE 直跑、资源未过滤」时出现，**不应随版本走**；
8. 同步 `AppVersionTest`（若有格式断言）与相关文档描述。

### D. 文档声明（4 处）

9. 方案二选一（建议 D1）：
   - **D1（推荐）**：`README.md` / `AGENTS.md` / `AHA-Design-V1.md` / `ReferenceGuide.md`
     不再写死具体数字，改为「版本号唯一来源：根 `pom.xml` 的 `<revision>`」+ 一行查询命令；
   - D2：保留数字，由 §E 的校验兜底（不一致即 CI 红）。

### E. 一致性校验（独立价值最高，可先行）

10. 新增校验：断言 **根 POM `<revision>` == `version.properties` 的 `version` == 产物文件名 == tag 名**；
    位置：`Build.yml`（或 `Gate.yml`）一个步骤；
    - **即使 A~D 不做，这一条也应当先做**——它把「静默错误」变成红灯（当前的真正短板）。

### F. 验证矩阵（改的是构建根基，必须逐项验）

| # | 验证点 | 判据 |
|---|---|---|
| F1 | Maven 4（wrapper `4.0.0-rc-7`）完整构建 | `Build` / `Gate` 绿 |
| F2 | Maven 3.9.x（`Compat.yml`）完整构建 | 绿 |
| F3 | 资源过滤 | `version.properties` 内 `version=x.y.z`（非 `${revision}`） |
| F4 | 产物名 | `aha-*-x.y.z.jar`、`aha-desktop-x.y.z-<平台>.zip` |
| F5 | install/deploy 的 POM 已解析 | 本地 `~/.m2` 里该 POM 的头 `<version>` 是实际值 |
| F6 | 四个工作流的版本读取 | tag 名 / 预发行版本号均为 `x.y.z` 或 `x.y.z.PPPPP` |
| F7 | 原生镜像线 | `DesktopNative` / `CliNative` 产物名正确 |
| F8 | IDE（IDEA）导入与运行 | 无 `${revision}` 未解析报错 |
| F9 | `versions:set` 语义 | 改用 `versions:set-property -Dproperty=revision`，并更新文档 |
| F10 | **Maven 4 下 flatten 与 consumer POM 是否冲突** | 无报错、无重复产物；`.m2` 里 POM 已解析为实际版本 |
| F11 | **Maven 4 GA 后移除 flatten 的可行性** | 移除后构建通过，且 `.m2` / 远端 POM 仍已解析 |

## 与 Maven 4 的分期决策

| 阶段 | 条件 | `flatten-maven-plugin` |
|---|---|---|
| **阶段一（现在）** | 双 Maven 并存：wrapper `4.0.0-rc-7` + `Compat.yml` 的 3.9.x | **保留**（3.9.x 无 consumer POM，不 flatten 会装出含占位符的 POM） |
| **阶段二** | Maven 4 **GA** 且 Maven 3.9.x 兼容线退役 | 可**移除**，改由 Maven 4 的 consumer POM 承担（比 flatten 更彻底：连 build 段都剥离） |

> **关键澄清**：无论 Maven 3 还是 4，**`${revision}` 都必须保留**——Maven 4 的 consumer POM
> 解引用的正是 `${revision}`；它不是「为 Maven 3 引入的历史包袱」。两件事不能一起**摘**。
>
> **不确定性说明**：Maven 4 目前仍是 rc（本仓库记录过 `apache-maven-4.0.0-bin.zip` 404、
> metadata 的 `release` 就是 `4.0.0-rc-7`）。rc 阶段行为可能微调，阶段二的结论应以 Maven 4 GA
> 的官方文档为准，并用 F10 / F11 实测后再执行。

### G. 文档与规范同步

11. `ReleaseProcess.md` §2：版本切换改为「改根 POM 的 `<revision>` 一行」；§4.2 的「8 处」修正；
12. `VersionBumpGuide.md`：§2 改动清单由 14 处降为 **1 处**，§4 检查清单相应简化（校验项保留）；
13. `BuildSpec.md` §7「版本单一来源」：来源由 `<version>` 改为 `<revision>`；
14. `AHA-Design-V1.md` 的 POM 示例与变更日志；
15. `CHANGELOG.md` 记录本次改造。

## 落地顺序与依赖

1. **先合并 PR #89**（0.1.1 → 0.1.2）——两者同改 POM，必须串行；
2. **P1：§E 校验**（独立 PR，风险最低、收益最高）；
3. **P2：§B 读取收敛**（独立 PR，为后续铺路）；
4. **P3：§A / §C**（`${revision}` + flatten + fallback 改造）；
5. **P4：§D / §G 文档**；
6. **每步都过 §F 的验证矩阵**（编译/测试由 CI 承担，本地只跑 `bin/Check*.py`）。

## 验收标准

- [ ] 切版本只需改 **1 行**（根 POM 的 `<revision>`），其余无人工改动；
- [ ] 不存在写死的版本字面量（`AppVersion.FALLBACK_VERSION` 已与版本解耦）；
- [ ] 版本读取只有 **1 处实现**（`bin/ProjectVersion.py`），四个工作流统一调用；
- [ ] 一致性校验上线：`<revision>` / `version.properties` / 产物名 / tag 名不一致即 CI 失败；
- [ ] Maven 4 与 3.9.x 双版本构建通过；原生镜像线产物名正确；
- [ ] `ReleaseProcess` / `VersionBumpGuide` / `BuildSpec` 口径更新为「1 处」；
- [ ] 切一次真实版本（如 0.1.3）验证全链路，并把结果回填本文件。

## 关联

- `Docs/Guide/VersionBumpGuide.md`（操作）、`Docs/DevSpec/ReleaseProcess.md`（规范）
- `Docs/Troubleshooting/TS-202610-VersionBumpMissedModules.md`（当初的教训）
- 四个工作流的版本读取：`Build.yml` / `BuildJVMArtifacts.yml` / `CliNative.yml` / `DesktopNative.yml`
- `aha-common` 的 `AppVersion` 与 `version.properties`
