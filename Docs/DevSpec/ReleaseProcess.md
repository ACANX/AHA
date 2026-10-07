# 发布流程

**文档版本**：v1.4.1
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
| v1.4.0 | 2026-10-07 | 新增 §4「分支流向与合并方式（强制）」：长期集成分支只能真合并、禁止把内容重新落地、squash/rebase 后必须删源分支，并给出已冲突时的比对步骤与 `-s ours` 的使用前提 | @ACANX |
| v1.4.1 | 2026-10-07 | §4.1 引用同步开发日志改名（`DevLog-20261007-21-2.md` → `DevLog-20261007-22.md`） | @ACANX |

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

---

## 4. 分支流向与合并方式（强制）

`dependa` 是 Dependabot 的 `target-branch`，也是长期存在的集成分支：它**持续**被合入、
又**持续**往 `dev` / `main` 合。这个双重身份决定了它的合并方式不能随便选。

### 4.1 规则

1. **长期集成分支只能真合并**。`dependa` 合入上游、以及上游合回 `dependa` 时，
   必须留下**真正的合并关系**——`git merge`（双父提交）或 GitHub 的
   `Create a merge commit`。
2. **禁止「把内容重新落一遍」**。例如 `git merge --squash` 后再手工提交、
   把分支上全部提交 cherry-pick 到目标分支等。这类做法会让上游收下内容却没有
   把源分支变成祖先，源分支之后的**每一个** PR 都会永久冲突
   （`mergeable_state=dirty`）。详见 [DevLog-20261007-22.md](../DevLog/DevLog-20261007-22.md)。
3. **用了 squash / rebase 就必须删源分支**。这两种合并的代价就是失去血缘、补不回来；
   若源分支还要继续用，就只能真合并。
4. **合并前后用树的逐字节比对确认没丢内容**：

   ```
   git rev-parse origin/<base>^{tree}
   git rev-list <head> | while read c; do \
     [ "$(git rev-parse $c^{tree})" = "$(git rev-parse origin/<base>^{tree})" ] && echo "等于 $c"; done
   ```

   若在 head 的历史里能找到 base 的树（说明 base 的内容已被包含），才可以用
   `git merge -s ours` 补血缘；**找不到就不能用**——那属于正常内容合并，
   必须逐条读懂冲突再取舍，绝不能一律选一边。

### 4.2 已经冲突了怎么办

先别猜，按顺序比对两边的提交与**树**：

```
git fetch --all --prune
git log --oneline origin/<base> --not origin/<head>
git log --oneline origin/<head> --not origin/<base>
git log -1 --format='%h %p %s' origin/<base>     # 只有一个父提交却写着 Merge 就是「假合并」
```

> 调试远端行为时一律用 `origin/<branch>` 显式引用。本地分支引用可能过期，
> 会给出「Already up to date」这种把人带偏的结论。
