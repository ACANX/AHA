# TD-00008 NativeExtensionModuleLayerConflict

> 待办编号：TD-00008
> 标题：原生镜像：扩展系统与 native-image 的根本冲突与降级策略（E-01）
> 状态：☐ 未开始
> 跟踪 Issue：[79](https://github.com/ACANX/AHA/issues/79)
> 创建日期：2026-10-09
> 最后更新：2026-10-09

---

## 来源

`Docs/TODO.md` `E-01`（原生编译章节**最关键**的一条，⏸ 待决策）。

## 问题

native-image 在**构建期**静态分析可达代码（closed-world），而扩展运行时依赖 `ModuleLayer` 在**运行时**动态创建模块层。`Constitution.md` 第 5 条把「扩展运行时 = `ModuleLayer` + `ExtensionRuntime`」列为**不可更换**。

两者根本冲突：

- `ModuleLayer` 定义的类在构建期不存在 → native-image 无法预编译；
- 因此 **native 产物中扩展系统无法以 `ModuleLayer` 形态工作**。

难点：`aha-core` 包含 `core.extension` 包（`ExtensionManager`、`RegistrationTracker`），只要在类路径上就会被可达性分析触及。

## 待拍板

1. native 产物中**扩展功能的降级策略**：完全禁用 / 仅支持编译期静态注册 / 不发布；
2. 若选「禁用」，确认 `core.extension` 能否被 native-image 分析**排除**，或需在构建期以 profile 剔除；
3. 在 `ExtensionSystemDesign.md` **显式记录**该限制；
4. 评估 `Constitution.md` 第 5 条是否需补 native-image 例外条款（涉及第 8 条修订程序）。

## 验收标准

- [ ] 上述四项各有明确结论并落到对应文档；
- [ ] 结论与桌面端 / CLI 原生镜像的实际发布形态一致（不出现「文档说支持、产物里不可用」）。

## 关联

- `Docs/TODO.md` `E-01`、`Docs/Design/ExtensionSystemDesign.md`、`Docs/DevSpec/Constitution.md` 第 5 / 8 条
- 建议**先做本条决策**，再评估其余原生镜像条目。

