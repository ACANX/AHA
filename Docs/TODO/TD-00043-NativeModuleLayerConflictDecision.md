# TD-00043 NativeModuleLayerConflictDecision

> 待办编号：TD-00043
> 标题：`ModuleLayer` 与 native-image closed-world 冲突：给结论
> 状态：⏸ 待决策（[#278](https://github.com/ACANX/AHA/issues/278)）
> 跟踪 Issue：[#278](https://github.com/ACANX/AHA/issues/278)（本任务的决策单）
> 所属主题：[TD-00008](TD-00008-NativeExtensionModuleLayerConflict.md)
> 创建日期：2026-10-17
> 最后更新：2026-10-17

---

## 来源

原 `Docs/TODO.md` `E-01`

## 原文（照抄，未改写）

> `ModuleLayer` 与 native-image 的 closed-world 根本冲突。

## 最小动作

三选一并落地：完全禁用 / 仅支持编译期静态注册 / 不发布扩展的 native 包。

## 验收标准

- [ ] 结论写进 `BuildSpec.md` §10；与 `TD-00040` 的双声明结论相互印证。

## 关联

- 所属主题：[TD-00008](TD-00008-NativeExtensionModuleLayerConflict.md)（该主题下的其余任务与总体验收）
- 跟踪 Issue：[#79](https://github.com/ACANX/AHA/issues/79)
- 决策单：[#278](https://github.com/ACANX/AHA/issues/278)（挂 V0.2.0）
