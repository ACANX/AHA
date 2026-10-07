package com.acanx.module.aha.core.agent;

import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.llm.LlmClient;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;
import com.acanx.module.aha.core.llm.protocol.Choice;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.ToolCall;
import com.acanx.module.aha.core.memory.MemoryStore;

import java.util.List;
import java.util.Map;

/**
 * 上下文压缩器：把较早的对话交给模型摘要，再用摘要替换掉那批消息。
 *
 * <p>压缩是「不可逆」的——消息一旦被替换就只能靠摘要回忆，因此这里守两条底线：
 * 摘要生成失败时<b>绝不</b>改动历史；摘要为空时同样放弃，避免既丢上下文又没拿到摘要。</p>
 *
 * <p>摘要以 {@code system} 角色写回历史。三个适配器都会把 {@code system} 消息提到
 * 顶层系统提示词（Anthropic / Gemini 会与前缀合并），因此摘要天然位于保留消息之前，
 * 不必依赖位置技巧。</p>
 *
 * @since 0.1.0
 */
public final class ContextCompactor {

    /** 默认保留的最近消息条数（约 5 轮往返）。 */
    public static final int DEFAULT_KEEP_MESSAGES = 10;

    /** 摘要消息的标记，便于用户与模型区分「摘要」与真实历史。 */
    public static final String SUMMARY_HEADER = "[此前对话摘要]";

    /** 单条消息纳入摘要输入时的字符上限。 */
    private static final int MAX_MESSAGE_CHARS = 4000;

    /** 摘要输入的字符上限，避免压缩本身先超限。 */
    private static final int MAX_TRANSCRIPT_CHARS = 120_000;

    private static final int SUMMARY_MAX_TOKENS = 2048;

    private static final double SUMMARY_TEMPERATURE = 0.2d;

    private static final String SUMMARY_PROMPT = """
            你是对话上下文压缩器。把用户给出的多轮对话压缩成一份便于继续工作的摘要。

            要求：
            - 只依据给定内容，不要推测或补全未出现的信息
            - 保留：用户的目标与约束、已达成的结论与决定、涉及的文件路径与命令、报错与修复、未完成事项
            - 丢弃：寒暄、重复表述、已被推翻的中间过程
            - 若内容中已含更早的摘要，合并进新摘要，不要丢掉其中仍然有效的信息
            - 使用简体中文，直接输出摘要正文，不要前言，不要代码围栏
            """;

    private final LlmClient llmClient;

    private final MemoryStore memoryStore;

    /**
     * 构造压缩器。
     *
     * @param llmClient   LLM 客户端
     * @param memoryStore 记忆存储
     */
    public ContextCompactor(LlmClient llmClient, MemoryStore memoryStore) {
        this.llmClient = llmClient;
        this.memoryStore = memoryStore;
    }

    /**
     * 压缩结果。
     *
     * @param removed 被替换掉的消息条数
     * @param kept    原样保留的消息条数
     * @param total   压缩后的历史总条数（含摘要）
     * @param summary 摘要正文；未发生压缩时为 {@code null}
     * @param model   生成摘要所用的模型；未发生压缩时为 {@code null}
     */
    public record Outcome(int removed, int kept, int total, String summary, String model) {

        /**
         * 是否真的发生了压缩。
         *
         * @return 有消息被替换时为 {@code true}
         */
        public boolean changed() {
            return removed > 0;
        }

        /**
         * 未发生压缩的结果。
         *
         * @param count 当前消息条数
         * @return 结果
         */
        public static Outcome unchanged(int count) {
            return new Outcome(0, count, count, null, null);
        }
    }

    /**
     * 压缩会话上下文：保留下最近 {@code keepRecent} 条消息，其余压成一条摘要。
     *
     * <p>{@code keepRecent} 是<b>期望值</b>：为避免把工具调用与工具结果切开
     * （服务端会拒绝以 tool 消息开头的历史），实际保留条数可能更多。</p>
     *
     * @param sessionId     会话 ID
     * @param config        会话配置；用于取会话固化的模型
     * @param fallbackModel 会话未固化模型时的兜底模型
     * @param keepRecent    期望保留的最近消息条数
     * @return 压缩结果；无需压缩时 {@link Outcome#changed()} 为 {@code false}
     * @throws AhaException 摘要生成失败（此时历史未改动）
     */
    public Outcome compact(String sessionId, SessionConfig config, String fallbackModel,
                           int keepRecent) {
        List<ChatMessage> history = memoryStore.loadHistory(sessionId, Integer.MAX_VALUE);
        int total = history.size();
        int boundary = boundary(history, keepRecent);
        if (boundary <= 0) {
            return Outcome.unchanged(total);
        }
        List<ChatMessage> older = List.copyOf(history.subList(0, boundary));
        String model = config != null && config.model() != null && !config.model().isBlank()
                ? config.model()
                : fallbackModel;
        String summary = summarize(model, older);
        int removed = memoryStore.replaceHistory(sessionId, total - boundary, summaryMessage(boundary, summary));
        // removed = 被摘要替掉的原消息数；kept = 原样保留的原消息数；total = 摘要 + kept
        int kept = total - removed;
        return new Outcome(removed, kept, kept + 1, summary, model);
    }

    /**
     * 计算切割位置：保留下标 {@code boundary} 及其后的消息。
     *
     * @param history    历史消息
     * @param keepRecent 期望保留条数
     * @return 切割下标；无需压缩时返回 0
     */
    private static int boundary(List<ChatMessage> history, int keepRecent) {
        int boundary = history.size() - Math.max(1, keepRecent);
        if (boundary <= 0) {
            return 0;
        }
        // 不能把工具调用与工具结果切开：以 tool 消息开头的历史会被服务端拒绝
        while (boundary > 0 && history.get(boundary).role() == Role.TOOL) {
            boundary--;
        }
        return boundary;
    }

    /**
     * 构造写回历史的摘要消息。
     *
     * @param covered 被摘要覆盖的消息条数
     * @param summary 摘要正文
     * @return 摘要消息
     */
    private static ChatMessage summaryMessage(int covered, String summary) {
        return ChatMessage.text(Role.SYSTEM,
                SUMMARY_HEADER + "（已压缩 " + covered + " 条更早的消息）\n\n" + summary);
    }

    /**
     * 请求模型生成摘要。
     *
     * @param model 模型名
     * @param older 待摘要的历史
     * @return 摘要正文
     * @throws AhaException 调用失败或返回为空
     */
    private String summarize(String model, List<ChatMessage> older) {
        ChatRequest request = new ChatRequest(
                model,
                List.of(ChatMessage.text(Role.SYSTEM, SUMMARY_PROMPT),
                        ChatMessage.text(Role.USER, renderTranscript(older))),
                List.of(), SUMMARY_TEMPERATURE, SUMMARY_MAX_TOKENS, false, Map.of());
        ChatResponse response;
        try {
            response = llmClient.chat(request).join();
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new AhaException("COMPACT_FAILED",
                    "生成摘要失败，历史未改动: " + cause.getMessage(), cause);
        }
        String summary = extract(response);
        if (summary == null || summary.isBlank()) {
            throw new AhaException("COMPACT_EMPTY", "模型未返回摘要内容，历史未改动");
        }
        return summary.strip();
    }

    /**
     * 从响应中取出正文。
     *
     * @param response 响应
     * @return 正文；缺失时返回 {@code null}
     */
    private static String extract(ChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return null;
        }
        Choice choice = response.choices().get(0);
        return choice == null || choice.message() == null ? null : choice.message().content();
    }

    /**
     * 把待摘要的历史渲染成带角色标注的纯文本。
     *
     * @param messages 历史消息
     * @return 文本
     */
    static String renderTranscript(List<ChatMessage> messages) {
        StringBuilder text = new StringBuilder();
        int omitted = 0;
        for (ChatMessage message : messages) {
            String line = renderMessage(message);
            if (text.length() + line.length() > MAX_TRANSCRIPT_CHARS) {
                omitted++;
                continue;
            }
            text.append(line).append('\n');
        }
        if (omitted > 0) {
            text.append("（另有 ").append(omitted).append(" 条内容过长未纳入，请以已有信息为准）\n");
        }
        return text.toString();
    }

    /**
     * 渲染单条消息。
     *
     * @param message 消息
     * @return 文本
     */
    private static String renderMessage(ChatMessage message) {
        Role role = message.role() == null ? Role.USER : message.role();
        StringBuilder line = new StringBuilder();
        line.append('[').append(switch (role) {
            case USER -> "用户";
            case ASSISTANT -> "助手";
            case TOOL -> "工具结果";
            case SYSTEM -> "系统";
        }).append("] ");
        if (message.toolCalls() != null) {
            for (ToolCall call : message.toolCalls()) {
                line.append("（调用工具 ").append(call.name()).append(' ')
                        .append(truncate(String.valueOf(call.arguments()))).append("）");
            }
        }
        line.append(truncate(message.content()));
        return line.toString();
    }

    /**
     * 截断过长的文本。
     *
     * @param text 文本
     * @return 截断后的文本
     */
    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_MESSAGE_CHARS
                ? text
                : text.substring(0, MAX_MESSAGE_CHARS) + "…（截断）";
    }
}
