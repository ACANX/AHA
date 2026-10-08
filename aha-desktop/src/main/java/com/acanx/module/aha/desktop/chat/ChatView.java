package com.acanx.module.aha.desktop.chat;

import java.util.Map;

/**
 * 对话界面对「对话内核」暴露的动作。
 *
 * <p>刻意不引用任何 JavaFX 类型：这样 {@link ChatController} 就能在**没有图形环境**的机器上
 * 被完整测试（用一个记录式实现断言「什么事件该产生什么界面动作」）。
 * 与 {@code FoldState} 是同一个思路——把可判定的部分从视图里抽出来。</p>
 *
 * @since 0.2.0
 */
public interface ChatView {

    /**
     * 追加一条用户消息。
     *
     * @param text 内容
     */
    void appendUser(String text);

    /**
     * 追加助手回复的增量（流式）。
     *
     * @param delta 增量文本
     */
    void appendAssistant(String delta);

    /**
     * 本轮助手回复结束。
     */
    void endAssistant();

    /**
     * 追加一条系统提示（非错误）。
     *
     * @param text 内容
     */
    void appendNotice(String text);

    /**
     * 展示错误。
     *
     * @param code    错误码
     * @param message 说明
     */
    void showError(String code, String message);

    /**
     * 开始一张工具调用卡片。
     *
     * @param kindLabel 类别标签（如「读取」）
     * @param toolName  工具名
     * @param target    操作目标（可为 {@code null}）
     * @param args      调用参数（可为 {@code null}）；展开卡片时显示
     * @return 卡片句柄，结果到达时原样回传
     */
    int beginToolCall(String kindLabel, String toolName, String target, Map<String, Object> args);

    /**
     * 更新工具卡片的执行结果。
     *
     * @param handle  卡片句柄
     * @param success 是否成功
     * @param output  输出文本（可为 {@code null}）
     * @param millis  耗时（毫秒）；不足 100ms 时界面不显示耗时
     */
    void finishToolCall(int handle, boolean success, String output, long millis);

    /**
     * 更新本轮用量。
     *
     * @param promptTokens     输入 token
     * @param completionTokens 输出 token
     */
    void setUsage(int promptTokens, int completionTokens);

    /**
     * 清空对话流（切换会话时先把旧的清掉）。
     */
    void clearConversation();

    /**
     * 设置忙碌状态（发送中）。
     *
     * @param busy 忙碌返回 {@code true}
     */
    void setBusy(boolean busy);
}
