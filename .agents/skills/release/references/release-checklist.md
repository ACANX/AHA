# 发布检查清单

> 构建 / 测试 / 覆盖率一律以 **CI** 为准，**不在本地跑 `mvn`**。
> 下列「通过」均指对应 CI 工作流的结果为绿。

- [ ] CI 的 `Gate.yml`（`clean verify` + 覆盖率门禁）通过
- [ ] CI 的 `Compat.yml`（Maven 3.9.x 兼容）通过
- [ ] 全部测试通过，覆盖率达标（读 `Gate` 日志 / 产物）
- [ ] `Docs/DevSpec/` 已审查
- [ ] `CHANGELOG.md` 已更新
- [ ] 版本号已更新
- [ ] tag 已创建
