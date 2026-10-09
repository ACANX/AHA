# TD-00009 NativeRiskAndReleaseMatrix

> 待办编号：TD-00009
> 标题：原生镜像：E-02 ~ E-11 风险登记、验收标准与发行矩阵
> 状态：☐ 未开始
> 跟踪 Issue：[80](https://github.com/ACANX/AHA/issues/80)
> 创建日期：2026-10-09
> 最后更新：2026-10-09

---

## 来源

`Docs/TODO.md` 第 5 节「GraalVM 原生编译（0.2+ 评估）」的 `E-02` ~ `E-11`，全部为 ⚠️ 外部知识或待决策，多数**尚未开工**。

## 范围

### 风险登记（E-02 ~ E-09，☐ 未完成）

| 编号 | 事项 | 现状 |
| --- | --- | --- |
| E-02 | `sqlite-jdbc` 的 JNI native library 嵌入 | 未验证 |
| E-03 | Jackson 3 反射配置 | 父 POM 已升 `3.2.3`；Jackson 3.x 的 GraalVM metadata 成熟度仍未实测 |
| E-04 | `ServiceLoader` 需资源配置 | 与 `C-02` 双声明交叉 |
| E-05 | picocli 反射配置 | 部分由注解处理器覆盖，未系统登记 |
| E-06 | JLine 终端能力探测依赖 native 组件 | 未验证 |
| E-07 | Log4j2 在 native-image 下的兼容性 | 未验证 |
| E-08 | `java.net.http` 的 TLS/SSL 配置 | 未验证 |
| E-09 | 虚拟线程在 native-image 的支持现状 | 未调研 |

### 验收标准与 CI 归属（E-10，⏸ 待决策）

`BuildSpec.md` §6 门禁与 §8「唯一验收标准」均未涵盖 native 产物。需为 native 产物定义独立冒烟测试，明确「JaCoCo 门禁不覆盖 native 产物」，并确定 native 构建是否绑定 `verify`（建议独立流水线）。

### 发行矩阵（E-11，⏸ 待决策）

| 发行形态 | 目标用户 | 产物 | 现状 |
| --- | --- | --- | --- |
| JPMS 模块路径目录（`Dist/`） | 需 JVM、可调优 | `aha-cli-<版本>.zip` | ✅ 已实现 |
| 桌面端便携包（按平台） | 需 JDK 25 | `aha-desktop-<版本>-<系统>-<架构>.zip` | ✅ 流水线已就位 |
| native-image 单文件 | 免 JVM、启动快 | 平台可执行文件 | ☐ 未开工 |
| `jpackage` 安装包 | 普通用户 | MSI / DEB / DMG | ☐ 见桌面端交付 Issue |

## 验收标准

- [ ] E-02 ~ E-09 逐条给出「已实测 / 不适用 / 需要规避」的结论，并写进 `BuildSpec.md` 的 native 章节；
- [ ] native 产物有独立冒烟测试与验收标准（E-10 决策落地）；
- [ ] `BuildSpec.md` §7 写明发行矩阵（E-11 决策落地）。

## 关联

- `Docs/TODO.md` 第 5 节、`Docs/DevSpec/BuildSpec.md` §6 / §7 / §8
- 依赖 `E-01`（扩展降级决策，见对应 Issue）先有结论。

