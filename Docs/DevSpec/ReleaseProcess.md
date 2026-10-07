# 发布流程

**文档版本**：v1.3.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-06
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.1.0 | 2026-10-06 | 新增 1.0 发布的自举验收附加检查 | @ACANX |
| v1.2.0 | 2026-10-06 | 新增发行包构建与验证；制品改为 `dist.zip` | @ACANX |
| v1.3.0 | 2026-10-07 | 发布步骤明确「版本号只需改根 `pom.xml`」（CLI 版本由资源过滤注入） | @ACANX |

---

## 1. 发布前检查

- [ ] `./mvnw clean verify` 通过（Maven 4）
- [ ] `mvn clean verify` 通过（Maven 3.9.x）
- [ ] 全部测试通过，覆盖率达标
- [ ] 发行包可构建且可运行（见 §3.1）
- [ ] `Docs/DevSpec/` 全部文档已审查
- [ ] `CHANGELOG.md` 已更新
- [ ] 版本号已更新（`pom.xml`）

### 1.1 1.0 发布附加检查（自举硬门槛）

1.0 正式版发布前，除上述通用检查外，还须逐项确认以下自举验收标准
（详见 [SelfHostingDesign.md](../Design/SelfHostingDesign.md)）：

- [ ] A1 工具完备：文件读写、命令执行、HTTP、记忆写入与检索、会话恢复均可用且权限受控
- [ ] A2 技能完备：`build` / `test` / `release` 技能能驱动完整交付闭环
- [ ] A3 持续性：连续 ≥ 4 周、≥ 30 次提交由 AHA 产出，期间未启动其他 Harness
- [ ] A4 质量不降级：评审一次通过率 ≥ 80%，CI 通过率与人工产出无显著差异
- [ ] A5 发布闭环：至少 1 个 minor 版本的完整发布由 AHA 驱动
- [ ] A6 可审计：自举期提交信息均标注 `Harness:` 行

> **未达成任一项，不得发布 1.0 正式版。**

## 2. 发布步骤

1. 从 `dev` 创建 `release/x.y.z` 分支
2. 更新版本号与 `CHANGELOG.md`
   - 版本号**只需改根 `pom.xml`**：CLI 的 `aha version` / `aha -V` 由资源过滤注入
     （`version.properties`），此前硬编码在两处、改 pom 不生效
3. 执行完整构建与验收
4. 合入 `main`，打 tag `vX.Y.Z`
5. 触发 `Release.yml`（构建发行包并发布 `dist.zip`）
6. 合回 `dev`，删除 `release/*` 分支

## 3. 制品

| 版本 | CLI | 桌面端 |
|---|---|---|
| 0.1 | `dist/` 目录（`bin/` + `lib/`），发布为 `aha-vX.Y.Z-dist.zip` | — |
| 0.2 | 同上 | jpackage (MSI/DEB) |
| 1.x | native-image | jpackage (MSI/DEB) |

### 3.1 发行包验证（发布前必做）

```bash
./mvnw clean package
./dist/bin/Aha.sh version     # 应输出版本号
./dist/bin/Aha.sh --help      # 应列出全部命令
```

`dist/` 结构：`bin/`（启动脚本）+ `lib/`（JPMS 模块路径：本项目模块 + 全部运行时依赖）。
发布流程将其打包为 `aha-<tag>-dist.zip` 并上传到 GitHub Release；`dist/` 不入库。

> **jpackage 不能交叉编译**，0.2 起的桌面端产物必须分平台构建。
> **桌面端 WebView 不支持 native-image**，1.x 的 native-image 流水线仅覆盖 CLI + Core + Tools。
