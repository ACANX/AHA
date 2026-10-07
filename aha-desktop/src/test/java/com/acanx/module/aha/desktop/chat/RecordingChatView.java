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
    }

    @Override
    public void endAssistant() {
        ends++;
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

    @Override
    public void setBusy(boolean value) {
        busy.add(value);
    }
}
