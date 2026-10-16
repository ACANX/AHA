package com.acanx.module.aha.cli.session;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 「把内容交回输入循环」的载体。
 *
 * <p>会话内命令在回合线程上执行，而消息由输入循环（主线程）提交，两者需要一个交接点：
 * 命令把内容放在这里，输入循环取走后代替用户输入去跑一轮。目前只有 {@code /paste} 用它
 * —— 它读剪贴板（不经过终端），因此终端不支持括号粘贴时也能把多行内容作为<b>一条</b>消息发出去。</p>
 *
 * <p>{@link #take()} 会清空：取到就消费掉，避免同一条内容被发送两次。</p>
 *
 * @since 0.1.0
 */
public final class PendingInput {

    private final AtomicReference<String> value = new AtomicReference<>();

    /**
     * 放入待提交的内容（同一回合内后放覆盖先放）。
     *
     * @param text 内容
     */
    public void put(String text) {
        value.set(text);
    }

    /**
     * 取走待提交的内容。
     *
     * @return 内容；没有时返回 {@code null}
     */
    public String take() {
        return value.getAndSet(null);
    }
}
