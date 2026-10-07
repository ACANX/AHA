package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.cli.tty.StatusSurface;

import java.util.List;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 状态行：等待期间显示转圈动画与当前阶段，工具执行时额外显示实时耗时。
 *
 * <p>绘制交给 {@link StatusSurface}——它在终端底部保留一行并支持原地重绘，
 * 因此这里的文本可以持续变化（转圈、秒数），而不像「打一行进正文」那样一锤定音。</p>
 *
 * <p>只负责文案组装与节流：每 {@link #TICK_MS} 刷新一次；终端不支持（{@code NONE}）
 * 或宽度未知时整体关闭。</p>
 *
 * @since 0.1.0
 */
final class StatusLine {

    /** Unicode 盲文帧。 */
    private static final String[] UNICODE_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    /** 编码不支持符号时的降级帧。 */
    private static final String[] ASCII_FRAMES = {"|", "/", "-", "\\"};

    /** 输入框边框线的字符（编码不支持制表符时为 ASCII 的 {@code -}）。 */
    private final String bar;

    /** 刷新间隔。 */
    private static final long TICK_MS = 120;

    /** 全局共享的守护调度器：状态行同一时刻只会有一个。 */
    private static final ScheduledExecutorService TICKER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "aha-status-line");
                thread.setDaemon(true);
                return thread;
            });

    private final StatusSurface surface;

    private final String[] frames;

    private final int width;

    private final boolean enabled;

    private final String model;

    private final Style style;

    private ScheduledFuture<?> task;

    /** 工具执行的开始时间；0 表示当前不在执行工具。 */
    private long toolStartedAt;

    private String phase;

    private int frame;

    /**
     * 创建状态行。
     *
     * @param surface 状态行载体
     * @param enabled 是否启用（调用方按终端能力决定）
     * @param unicode 编码是否支持盲文帧
     * @param width   终端宽度；{@code <= 0} 表示未知
     * @param model   模型名，可为 {@code null}
     * @param style   样式（用于输入框下边框）
     */
    StatusLine(StatusSurface surface, boolean enabled, boolean unicode, int width, String model,
               Style style) {
        this.surface = surface == null ? StatusSurface.NONE : surface;
        this.frames = unicode ? UNICODE_FRAMES : ASCII_FRAMES;
        this.bar = unicode ? "─" : "-";
        this.width = width;
        this.enabled = enabled && width > 0 && this.surface.available();
        this.model = model;
        this.style = style == null ? Style.of(false) : style;
    }

    /**
     * 开始显示某个阶段。
     *
     * @param text 阶段文案（如「等待响应…」）
     */
    synchronized void show(String text) {
        if (!enabled) {
            return;
        }
        this.phase = text;
        paint();
        if (task == null) {
            task = TICKER.scheduleWithFixedDelay(this::paint, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 开始显示工具执行阶段。
     *
     * <p>与 {@link #show(String)} 的区别是带上<b>实时耗时</b>：工具执行往往要好几秒，
     * 秒数持续跳动是「它还活着」的唯一证据。这里是唯一能原地重绘的位置，
     * 工具区块的末行只能给最终值。</p>
     *
     * @param toolName 工具名
     */
    synchronized void tool(String toolName) {
        this.toolStartedAt = System.nanoTime();
        show("执行工具 " + toolName + "…");
    }

    /**
     * 回到空闲状态。
     *
     * <p>不是「隐藏」而是「显示空闲文案」：底部那一行还兼着输入行下边框的职责，
     * 回合结束就撤掉的话，输入区会突然失去框线。</p>
     */
    synchronized void idle() {
        if (!enabled) {
            return;
        }
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        phase = null;
        toolStartedAt = 0;
        // 高度必须稳定：空闲时也照常输出边框行，否则底部区域会收缩、
        // 输入行下方那条线突然消失
        surface.update(List.of(rule(), model == null ? "" : model));
    }

    /**
     * 刷新一帧。
     */
    synchronized void paint() {
        if (!enabled || phase == null) {
            return;
        }
        surface.update(List.of(rule(), compose()));
        frame++;
    }

    /**
     * 输入框下边框。
     *
     * <p>与提示符带出的上边框同色（高亮紫），两者合起来把输入区框住。</p>
     *
     * @return 已着色的水平线
     */
    private String rule() {
        int cells = width > 0 ? Math.max(1, width - 1) : 40;
        return style.frame(bar.repeat(cells));
    }

    /**
     * 组装状态行文案：只有转圈帧与当前阶段。
     *
     * <p>不显示耗时与用量：耗时属于**这次操作**的信息，已由工具区块的末行承担；
     * 状态行只回答「它现在在干什么」。</p>
     *
     * @return 文案
     */
    private String compose() {
        StringBuilder text = new StringBuilder(frames[frame % frames.length])
                .append(' ').append(phase);
        if (toolStartedAt > 0) {
            text.append(' ').append(String.format("%.1fs",
                    (System.nanoTime() - toolStartedAt) / 1_000_000_000.0));
        }
        return clamp(text.toString());
    }

    /**
     * 按显示宽度截断，避免状态行折成两行。
     *
     * @param text 文案
     * @return 截断后的文案
     */
    private String clamp(String text) {
        int limit = width - 1;
        if (TextWidth.width(text) <= limit) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        int used = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int cell = TextWidth.charWidth(codePoint);
            if (used + cell > limit) {
                break;
            }
            result.appendCodePoint(codePoint);
            used += cell;
            index += Character.charCount(codePoint);
        }
        return result.toString();
    }
}
