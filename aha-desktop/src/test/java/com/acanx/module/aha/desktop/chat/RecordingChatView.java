package com.acanx.module.aha.desktop.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 记录式 {@link ChatView}：把界面动作记下来供断言。
 *
 * @since 0.2.0
 */
final class RecordingChatView implements ChatView {

    /** 用户消息。 */
    final List<String> users = new ArrayList<>();

    /** 助手增量。 */
    final List<String> deltas = new ArrayList<>();

    /**
     * 界面动作的时间线（issue #403 的顺序回归用）。
     *
     * <p>只记「影响消息流顺序」的动作：正文段结束、工具卡片开启。断言这条序列，
     * 就等于断言「开场白在卡片之前、总结在卡片之后」——而每个动作在此前都被分别断言过。</p>
     */
    final List<String> timeline = new ArrayList<>();

    /**
     * 正文通道的时间线：增量与段收尾**按调用顺序**记在同一条列表里（PR #404 审查 P1-1）。
     *
     * <p>{@link #deltas} 与 {@link #ends} 各自只记「有哪些」，跨列表的先后关系无从断言；
     * 而分段渲染的缺陷恰恰是**跨类型的顺序**：段收尾排到下一段增量之后，两段就被并成一段。</p>
     */
    final List<String> assistantTimeline = new ArrayList<>();

    /** 通知。 */
    final List<String> notices = new ArrayList<>();

    /** 错误。 */
    final List<String> errors = new ArrayList<>();

    /** 工具卡片开启记录。 */
    final List<String> cards = new ArrayList<>();

    /** 工具卡片完成记录。 */
    final List<String> cardResults = new ArrayList<>();

    /** 结束次数。 */
    int ends;

    /** 最近一次用量。 */
    String usage;

    /** 忙碌状态变化。 */
    final List<Boolean> busy = new ArrayList<>();

    @Override
    public void appendUser(String text) {
        users.add(text);
    }

    @Override
    public void appendAssistant(String delta) {
        deltas.add(delta);
        assistantTimeline.add("delta:" + delta);
    }

    @Override
    public void endAssistant() {
        ends++;
        timeline.add("end-assistant");
        assistantTimeline.add("end");
    }

    @Override
    public void appendNotice(String text) {
        notices.add(text);
    }

    @Override
    public void showError(String code, String message) {
        errors.add(code + ": " + message);
    }

    @Override
    public int beginToolCall(String kindLabel, String toolName, String target, Map<String, Object> args) {
        cards.add(kindLabel + "|" + toolName + "|" + target + "|" + args);
        timeline.add("tool-card");
        return cards.size();
    }

    @Override
    public void finishToolCall(int handle, boolean success, String output, long millis) {
        cardResults.add(handle + "|" + success + "|" + millis + "|" + ToolCard.lines(output));
    }

    @Override
    public void setUsage(int promptTokens, int completionTokens) {
        usage = promptTokens + "/" + completionTokens;
    }

    /** 清空次数（切换会话时应当先清一次）。 */
    int clears;

    @Override
    public void clearConversation() {
        clears++;
        users.clear();
        deltas.clear();
    }

    @Override
    public void setBusy(boolean value) {
        busy.add(value);
    }
}
