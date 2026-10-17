# TD-00004 CliWindowsMultilinePaste

> 待办编号：TD-00004
> 标题：CLI：Windows 终端多行粘贴兼容性（conhost 下仍会逐行提交）
> 状态：◐ 进行中
> 跟踪 Issue：[75](https://github.com/ACANX/AHA/issues/75)
> 创建日期：2026-10-09
> 最后更新：2026-10-09

---

## 来源

`Docs/Dbsx.txt` 第 6 节「Windows 终端多行粘贴兼容性」。

## 现状（部分完成）

已做两项加固：

1. 读取输入的异常由「只捕获 Ctrl+C / Ctrl+D」改为宽幅兜底——任何读取失败都只忽略本次输入并继续对话（此前粘贴触发的异常会冒泡到外层直接结束会话，这可能就是「报错」的实际表现）；
2. 显式启用 `BRACKETED_PASTE`，且 `exit` / `quit` 仅在「单行且无内嵌换行」时生效。

## 剩余限制（需明确处置）

仍受终端能力约束：Windows Terminal 支持 bracketed paste，**传统 conhost 不支持**，此时多行粘贴仍会被逐行提交——Harness 侧无法改变。

## 待定

- [ ] 确认「报错」是否已随宽幅兜底消失（需 Windows 真机复现原操作）；
- [ ] 对 conhost 的逐行提交行为给出明确结论：接受降级并写入 `TUIDesign.md` 的降级矩阵，或提供替代粘贴入口（如 `/paste` 或读剪贴板）；
- [ ] 若存在可在 Harness 侧规避的路径（如探测终端能力后提示），补上提示。

## 验收标准

- [ ] Windows Terminal 下粘贴含换行的长文本不再报错、不被逐行提交；
- [ ] conhost 下的行为有明确书面结论（降级说明或替代方案），不留「已知但未定」。

## 关联

- `Docs/Dbsx.txt` 第 6 节
- `Docs/Design/TUIDesign.md`（终端能力与降级矩阵）

## 原文摘录（`Docs/Dbsx.txt` 第 6 节，该文件已于 2026-10-17 删除）

> 6. Windows 终端多行粘贴兼容性
>
> 现在输入框中不支持复制带换行的长文本或者直接粘贴多行文本，
> 在 Windows 的终端中会报错，请解决这个兼容性问题。
>
> [部分完成] 已做两项加固（详见 Docs/DevLog/20261009-16.md）：
>         1. 读取输入时的异常由「只捕获 Ctrl+C / Ctrl+D」改为宽幅兜底，
>            任何读取失败都只忽略本次输入并继续对话，不再终止整个会话；
>         2. 显式启用 BRACKETED_PASTE，且 exit/quit 仅在「单行且无内嵌换行」时生效。
>
> [待办] 仍受限于终端能力：Windows Terminal 支持 bracketed paste，
>         传统 conhost 不支持，此时多行粘贴仍会被逐行提交（Harness 侧无法改变）。
>         待明确处置：接受降级并写入 TUIDesign 的降级矩阵，或提供替代粘贴入口。
>
>
> 尚未实现的项（保留为待办）
> ==========================
>
> - 工具调用的分块输出展示（编辑差异、添加折叠、浏览折叠、命令结果）
>   【已失效（2026-10-15，复审 issue #121）】工具卡片已实装：默认折叠、展开显示参数与**带行号输出**
>   （前 200 行并写明总行数）、复制 / 查看全部（见 `CHANGELOG.md` 的工具卡片段；代码：
>   `aha-desktop/.../chat/ToolCard.java`、`view/FoldState.java`、`view/ShellLayout.java`）。
> - DeepSeek 工具调用能力的补齐
>   【部分失效（2026-10-15，复审 issue #121）】DeepSeek 由 OpenAI 兼容适配器统一处理
>   （`aha-core/.../config/ProviderPresets.java` L19-26；`ModelDefault.yml` 的 DeepSeek 预设），
>   该适配器**支持工具调用**（请求侧 `ir.tools()`、解析侧 `ToolCall` / `ToolCallDelta`）；
>   但**未找到 DeepSeek 真机工具调用的验证记录** ⇒ 若原缺口指的是「真机验证」，该项仍成立。
> - Markdown 富文本渲染
>   【仍成立（2026-10-15 复核，issue #121）】当前只有**导出 Markdown**（`chat/SessionExport.markdown`、
>   `ChatController.export`），对话区**未**做富文本渲染 ⇒ 该项仍然有效，保留为待办。
> - 输入框样式（无 aha> 前缀、上下制表符包裹、loading 动效）
>   【已失效（2026-10-15，复审 issue #121）】CLI 输入区已是**框线提示**：
>   `aha-cli/.../cli/ChatCommand.java` L194 用 `style.frame()` 生成上下框线、L383-390 按终端
>   制表符能力降级（`OutputEncoding` 里列了 `─│├└` 等字符）；**未找到独立的 loading 动效实现**
>   （该半句仍成立）。
> - 状态栏（模型/思考强度、工作目录、token 统计、上下文占比、运行模式）
>   【已失效（2026-10-15，复审 issue #121）】状态栏已实装并显示「供应商 · 档位 · 模型」
>   （`CHANGELOG.md` 的桌面端段；代码：`aha-desktop/.../view/ShellLayout.java`、`DesktopShell.java`）。
> - 斜杠命令与下拉补全
>   【已失效（2026-10-15，复审 issue #121）】斜杠命令与补全均已实装：桌面端 `DesktopShell.java` L821/L843
>   处理 `/` 开头输入 + `view/CompletionPopup.java` 下拉；CLI 端
>   `session/SessionCommandCompleter.java`、`session/SessionCommandHighlighter.java`；
>   另 `@` 文件引用补全（跳过 `.git`/`target`/`node_modules`）见 `CHANGELOG.md`。
> - 注意：Security.KeyStorePath 与 Security.KeyStore 目前同样是「装饰性配置」，
>   EncryptedFileSecretStore 仅在测试中被构造，生产路径尚未接入。
>
>   【已失效（2026-10-15，复审 issue #121）】生产路径**已接入**，反证：
>     - `aha-core` 的 `AhaBootstrap` 启动时调用 `installSecretResolver()`（`AhaBootstrap.java` L102），
>       其内部执行 `SecretStores.create(config)` 并把 `store::retrieve` 注册进
>       `ConfigLoader.setSecretResolver(...)`（同文件 L151-155）；
>     - `EncryptedFileSecretStore` 是**真实实现**（`aha-core/.../security/EncryptedFileSecretStore.java` L35），
>       并非「仅在测试中构造」；
>     - CLI 的 `aha secret set|get|delete|list` 走同一入口；
>     - 仍缺的是**桌面端「保存供应商配置时写密钥库」**这一步 ⇒ 见 issue #77（范围据此校正）。
>
>
> 0.1 里程碑核查结论（发布于 0.1.0 前复核）
> ==========================================
>
> 结论：**未全部完成**。设计文档声明的阶段一至七、九至十一均已有代码，
> 但逐字段核查生产引用后发现若干「配置已定义、代码未使用」的装饰性配置。
>
>   【已失效（2026-10-15，复审 issue #121）】该轮列出的装饰性配置已在 2026-10-09 的补齐轮次处理完毕：
>     归档见 `Docs/DevLog/20261009-16.md` §1「配置与运行时目录」（日志系统真正配置、日志目录落到用户级、
>     运行时目录统一、**`Security.KeyStore` 接入**标为 [x]）与 §2「工具与安全」（配置注入机制
>     `ToolSettings` + `Tool.configure` + `ToolRegistry.configureAll`）。密钥库一项的反证见上一节。
>
> （「已修复」与「已补齐（本轮）」的条目已归档到 Docs/DevLog/20261009-16.md）
>
> 仍属计划内（非缺口）：
>   - Extensions.*（扩展运行时）→ 0.3
>     【仍成立（2026-10-15，复审 issue #121）】`ExtensionManager` 类**已存在**
>     （`aha-core/.../core/extension/ExtensionManager.java`：`load(Path)` / `start` / `stop` / `extensions()`），
>     但 `grep -rn "ExtensionManager"` 在**非测试代码里零引用** ⇒ `Extensions.*` 配置**仍未接入生产路径**，
>     该项继续按 0.3 跟踪。
>   - aha-desktop / McpAdapter → 0.2
>     【半失效（2026-10-15，复审 issue #121）】**`aha-desktop` 这一半已交付**：main 下 39 个、test 下 35 个 Java 文件，
>     FXML 化四个批次、GUI 冒烟（`AhaDesktopSmokeTest`，CI `ui-smoke` 起 Xvfb 真开窗口）、主题 / 字体修复、
>     快照包与取件链路均已落地 ⇒ 读到本行时**不要**再理解成「桌面端还没做」。
>     **`McpAdapter` 这一半仍成立**：`aha-core/.../core/mcp/McpAdapter.java` 仍为 43 行骨架。
>   - LLM 供应商真实 API Key 端到端验收 → 需外部环境
>     【仍成立（2026-10-15，复审 issue #121）】未找到完成证据；仍需**外部环境**（真机 + 真实 API Key）才能验收。
>
> 0.2 桌面端 · 下一步待办（2026-10-08 汇总）
> ========================================
>
> （已完成项已归档到 Docs/DevLog/20261009-16.md）
>
> 下一步（按建议顺序）
>
> [待办]  3. 工具面板：把 Tools.Enabled 做成开关（现在只读展示 + 权限标注）
> [待办]  3b. 压缩上下文（Ctrl+K / `/compact`）与自动压缩设置：AgentService 还没有对应入口
> [待办]  3c. 会话内搜索（Ctrl+F）与 `F6` 焦点循环
> [待办]  8. 供应商增强：连接测试（等价 aha provider test）、密钥写入密钥库而非明文
>          【仍成立（2026-10-15，复审 issue #121）】已开 issue #77 跟踪；**范围已校正**：「密钥库本体与生产读取
>          路径」在 0.1 就已完成（反证见上一节），本项只剩**桌面端保存流程接入**（复用 `SecretStores.create`）
>          与**连接测试**两件事。
> [待办]  9. 记忆面板（项目记忆 + 候选区）与 /stats 用量面板 → 0.3 范围
> [待办] 10. 桌面端覆盖率门禁上线（D-05：0.2 开发期 0.30，收尾向 0.70 对齐）
> [待办] 11. jpackage 自包含安装包（D-08 残留：用户无需自备 JDK）
>
> 0.2 桌面端原生镜像（试验性，2026-10-08）
> ======================================
>
> （模块与工作流的需求、构建报告、构建契约、产物落点等**已完成项**已归档到
>  Docs/DevLog/20261009-16.md；排查过程见
>  Docs/Troubleshooting/TS-2610-014-Native-Skip-Silent-Override.md 等）
>
> 计划与跟踪（详见 Docs/Design/DesktopNativeDesign.md §7）
>
> [待办] N-05 首次真机验证三条腿的下载产物（双击即用、能开窗、能对话）
> [待办] N-06 填写对比表真实数字（原生镜像 JDK25 / JDK27 / JVM+Leyden AOT 三种形态）
>              测量口径：冷启动 = 启动到主窗口可见；驻留内存 = 静置 60 秒的工作集；
>              每个数字取 5 次中位数并记机器
> [待办] N-07 扩展 JDK 27 到 Linux/macOS；二进制内部版本号带上 PR 段；体积瘦身
> [待办] N-08（备选路径）若手工参数清单维护成本过高，评估 GluonFX 插件
>
> 已知风险（同设计文档第 6.1 节）
>
>   R1 JavaFX + native-image 需要一长串运行期初始化清单，首次跑很可能在某个类上失败
>   R2 反射 / 资源缺失导致构建成功但运行期才炸
>   R3 JavaFX 平台原生库可能未打进镜像（UnsatisfiedLinkError）
>   R4 GraalVM 未必有对应 JDK（尤其 JDK 27）——该腿失败即是答案（已隔离）
>   R5 产物体积大（几十 MB）；R6 未签名 → macOS Gatekeeper / Windows SmartScreen
>   R7 「只在 Windows 试」可能掩盖平台差异
>
> 本机怎么试（Windows）
>
>   .\mvnw -Pdesktop-native -pl aha-desktop-native,aha-desktop -am package -DskipTests
>   # 只验证管线不真编：加 -Dnative.skip=true
>   # JDK 27 实验分支：加 -Pnative-jdk27
>
>   注意 -pl 要显式带上 aha-desktop：profile 加入的模块不会被 -am 带上游
>   （症状是「在中央仓库找不到 aha-desktop」）
>
> 工作方式（用户明确要求，2026-10-08）
>
> [待办] 技能成熟判据（四条）：目标平台真机跑通 / 未验证条目清零 /
>          版本与发布链路可追溯 / **在至少一个别的项目上照它跑通过一次**
> [待办] 目前版本 0.1.0（试验中）：隔离、构建系统坑、版本号规则已实测；
>          真正的 native-image 编译与运行期行为**尚未验证**，逐条待回填
>
> 需要人工 / 平台侧（与桌面端并行）
>
> [待办] G-02 dev/main 规则集：必需审批 1 → 0、关 last_push_approval、main 补 Gate/Compat 必需检查
> [待办] G-04 CodeQL 与 Default setup 二选一（当前两者互斥，「分析并上传结果」必失败）
> [待办] G-05 关闭仓库的 squash / rebase 合并
> [待办] G-06 处置 0.1.0 的裸 tag（现行约定是 V<版本号>）
> [待办] F-16 Build/Gate 日志里的 [stderr]（sqlite native-access 警告）在 CI 侧确认并消除
> [待办] F-17 桌面端原生构建时确认 javafx.platform 的 recursive variable reference 是否出现
>
> 已知限制（写下来免得再踩）
>
> - 本机 WSL 无 libgtk-3，GUI 只能在 Windows 上跑。界面验证方式固定为：
>   纯逻辑抽出来单测（无图形环境）+ Windows 真机启动 + 截图核对；不要指望在 WSL 里开窗。
> - 一键启动：C:\Users\ACANX\run-aha-desktop.bat
>   控制台输出落 C:\Users\ACANX\aha-desktop-console.log —— 界面出问题先看它。
> - 桌面端包由 WSL 侧强制平台分类器打出（-Djavafx.platform=win），
>   解到 C:\Users\ACANX\aha-desktop-win 后运行。
