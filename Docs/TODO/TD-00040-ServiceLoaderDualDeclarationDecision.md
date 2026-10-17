# TD-00040 ServiceLoaderDualDeclarationDecision

> 待办编号：TD-00040
> 标题：`ServiceLoader` 双声明：拍板三选一并落地
> 状态：⏸ 待决策（[#276](https://github.com/ACANX/AHA/issues/276)）
> 跟踪 Issue：[#276](https://github.com/ACANX/AHA/issues/276)（本任务的决策单）
> 所属主题：[TD-00011](TD-00011-CoreContractAndArchGovernance.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/TODO.md` `C-02`

## 原文（照抄，未改写）

> 工具与适配器同时在 `module-info` 的 `provides` 与 `META-INF/services` 声明。三选一：维持并登记理由 / 删除 classpath 兼容 / 加脚本或注解处理器校验两处一致。

## 最小动作

拍板并写进 `ModuleConvention.md`（决策时一并考虑 `E-04`：双声明在 native-image 下反而降低配置成本）。

## 验收标准

- [ ] 结论 + 理由 + 若「维持」则写明复核条件；若有校验则含反例自检。

## 关联

- 所属主题：[TD-00011](TD-00011-CoreContractAndArchGovernance.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#82](https://github.com/ACANX/AHA/issues/82)
- 决策单：[#276](https://github.com/ACANX/AHA/issues/276)（挂 V0.2.0）
