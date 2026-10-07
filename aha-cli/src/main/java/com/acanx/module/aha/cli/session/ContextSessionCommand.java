package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.common.runtime.RuntimeDescriptor;
import com.acanx.module.aha.core.config.SystemPromptLoader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会话内命令 {@code /context}：查看当前上下文状态。
 *
 * <p>回答「这一轮到底会带多少上下文发给模型、用的是哪个模型、谁能被调用」，
 * 避免用户只能靠猜。展示项包括会话 ID、实际生效模型、端点、身份来源、
 * 历史消息条数、Token 估算、已启用工具与工作目录。</p>
 *
 * <p>用量、压缩阈值与自动压缩三处共用 {@link ContextSnapshot}，口径一致。</p>
 *
 * @since 0.1.0
 */
final class ContextSessionCommand implements SessionCommand {

    @Override
    public String name() {
        return "context";
    }

    @Override
    public String usage() {
        return "/context";
    }

    @Override
    public String description() {
        return "查看当前上下文（消息数、Token 估算、模型、工具）";
    }

    @Override
    public Outcome execute(SessionContext context, List<String> args) {
        PrintStream out = context.out();
        RuntimeDescriptor runtime = context.service().runtime();
        Map<String, String> attributes = runtime.attributes();
        Optional<ContextSnapshot> snapshot = ContextSnapshot.of(context);

        out.println("上下文");
        SessionOutput.field(out, "会话", context.sessionId());
        SessionOutput.field(out, "模型", describeModel(
                snapshot.map(ContextSnapshot::model).orElse(attributes.get("Model")),
                attributes.get("Provider")));
        SessionOutput.field(out, "端点", attributes.get("Endpoint"));
        SessionOutput.field(out, "身份",
                MemorySessionCommand.describeSource(MemorySessionCommand.source(context)));
        if (snapshot.isPresent()) {
            ContextSnapshot view = snapshot.get();
            SessionOutput.field(out, "历史", describeHistory(view));
            SessionOutput.field(out, "估算",
                    TokenEstimator.describe(view.tokens(), view.hasSystemPrompt()));
            SessionOutput.field(out, "工具", describeTools(view.tools()));
        }
        SessionOutput.field(out, "目录", runtime.workingDir());
        Path projectRoot = SystemPromptLoader.findProjectRoot(
                Path.of(System.getProperty("user.dir", ".")));
        if (projectRoot != null) {
            SessionOutput.field(out, "项目", projectRoot.toString());
        }
        return Outcome.CONTINUE;
    }

    /**
     * 组装模型描述。
     *
     * @param model    模型名
     * @param provider 供应商 ID
     * @return 描述文本
     */
    private static String describeModel(String model, String provider) {
        String name = model == null || model.isBlank() ? "（未知）" : model;
        return provider == null || provider.isBlank() ? name : name + "（" + provider + "）";
    }

    /**
     * 历史消息描述。
     *
     * <p>「窗口内」与「存储总量」可能不等：窗口只取最近 N 条，另有部分消息因缺少
     * {@code tool_call_id} 等无法完整重建而被跳过。两者都会导致数量差，无法从计数上
     * 区分，因此合并说明，免得用户以为数据丢了。</p>
     *
     * @param view 上下文快照
     * @return 描述文本
     */
    private static String describeHistory(ContextSnapshot view) {
        StringBuilder text = new StringBuilder();
        text.append(view.history().size()).append(" 条（窗口上限 ").append(view.window()).append("）");
        int missing = view.stored() - view.history().size();
        if (missing > 0) {
            text.append("；存储 ").append(view.stored()).append(" 条，其余 ")
                    .append(missing).append(" 条在窗口之外或无法完整重建");
        }
        return text.toString();
    }

    /**
     * 已启用工具描述。
     *
     * @param tools 工具名
     * @return 描述文本
     */
    private static String describeTools(List<String> tools) {
        return tools.isEmpty() ? "（无启用工具）"
                : tools.size() + " 个：" + String.join("、", tools);
    }
}
