---
name: release
description: 版本发布流程：创建 release 分支、更新版本号与 CHANGELOG、双 Maven 版本验证、打 tag 并触发发布流水线。当需要准备发布、更新变更日志或核对发布清单时使用。
license: Apache-2.0
compatibility: 需要 JDK 25 与 Maven 4.x/3.9.x；需要对 main/dev 分支与 CI 的写权限；需可访问 Maven Central。
metadata:
  version: "1.0.0"
  owner: ACANX
---

# 版本发布

版本号遵循语义化版本。发布以 `release/x.y.z` 分支为起点，经验证后合入 `main` 并打 tag。

## 何时使用

- 准备新的 release
- 更新 `CHANGELOG.md`
- 核对发布前检查项

## 步骤

1. 从 `dev` 创建 `release/x.y.z` 分支
2. 更新各 `pom.xml` 版本号（父 POM 与全部子模块）
3. 更新 `CHANGELOG.md`：把 `[未发布]` 内容归入新版本小节，并补日期
4. 运行双 Maven 版本验证，两者都必须通过：

   ```bash
   ./mvnw clean verify     # Maven 4.x 运行时
   mvn clean verify        # Maven 3.9.x 兼容基线
   ```

5. 合入 `main` —— tag 由 CI 自动打（`Build.yml` 的 tag 作业按根 POM 版本创建 `VX.Y.Z`；
   若被可选腿影响未打，按 ReleaseProcess.md §4.1 手工补打）
6. 触发 `Release.yml` 流水线

## 发布前检查

- [ ] 版本号在父 POM 与全部子模块中一致
- [ ] `CHANGELOG.md` 已更新且无 `[未发布]` 残留
- [ ] `./mvnw clean verify` 通过
- [ ] `mvn clean verify` 通过
- [ ] 覆盖率门禁通过（BUNDLE 行覆盖率 ≥ 70%）
- [ ] 文档已同步（设计文档、用户指南、`AGENTS.md` 技能索引）
- [ ] 无 SNAPSHOT 依赖残留

## 参考

本技能目录内的资源：

- [references/release-checklist.md](references/release-checklist.md) - 完整发布清单
- [assets/ChangelogTemplate.md](assets/ChangelogTemplate.md) - CHANGELOG 模板

仓库文档：

- [ReleaseProcess.md](../../../Docs/DevSpec/ReleaseProcess.md) - 发布流程规范
- [CommitMessageSpec.md](../../../Docs/DevSpec/CommitMessageSpec.md) - 提交信息规范
- [BranchStrategy.md](../../../Docs/DevSpec/BranchStrategy.md) - 分支策略
