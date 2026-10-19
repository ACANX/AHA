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

    /** 完成 / 失败态的保留时间（秒），之后回到空闲。 */
    private static final long AUTO_IDLE_SECONDS = 2;

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

    private final StatusBar status;

    private final Style style;

    private ScheduledFuture<?> task;

    /** 完成 / 失败态自动回到空闲的定时器。 */
    private ScheduledFuture<?> idleTask;

    private volatile RunState state = RunState.IDLE;

    private String detail;

    private String summary;

    private long stateStartedAt;

    private int frame;

    /**
     * 创建状态行（issue #330：底部常驻三项）。
     *
     * @param surface 状态行载体
     * @param enabled 是否启用（调用方按终端能力决定）
     * @param unicode 编码是否支持盲文帧
     * @param width   终端宽度；{@code <= 0} 表示未知
     * @param statusBar 状态栏（模式 / 模型与档位 / 工作目录）
     * @param style   样式（用于输入框下边框）
     */
    StatusLine(StatusSurface surface, boolean enabled, boolean unicode, int width,
               StatusBar statusBar, Style style) {
        this.surface = surface == null ? StatusSurface.NONE : surface;
        this.frames = unicode ? UNICODE_FRAMES : ASCII_FRAMES;
        this.bar = unicode ? "─" : "-";
        this.width = width;
        this.enabled = enabled && width > 0 && this.surface.available();
        this.status = statusBar == null ? StatusBar.legacy("") : statusBar;
        this.style = style == null ? Style.of(false) : style;
    }

    /**
     * 开始显示某个阶段。
     *
     * @param text 阶段文案（如「等待响应…」）
     */
    synchronized void show(String text) {
        runState(RunState.REQUESTING, text);
    }

    /**
     * 切换到某个运行状态（issue #342）。
     *
     * <p>状态切换立即绘制（验收要求 ≤ 200ms），耗时由 120ms 的 ticker 刷新。</p>
     *
     * @param next 目标状态
     * @param text 状态文案（可空，按状态取默认）
     */
    synchronized void runState(RunState next, String text) {
        if (!enabled || next == null) {
            return;
        }
        if (next == RunState.IDLE) {
            idle();
            return;
        }
        cancelIdle();
        state = next;
        detail = text;
        summary = null;
        stateStartedAt = System.nanoTime();
        paint();
        if (task == null) {
            task = TICKER.scheduleWithFixedDelay(this::paint, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 结束态：完成 / 失败 / 已中断（保留 {@value #AUTO_IDLE_SECONDS} 秒后回到空闲）。
     *
     * @param next   只能是 {@link RunState#DONE}、{@link RunState#FAILED} 或
     *               {@link RunState#INTERRUPTED}
     * @param reason 失败时的单行原因摘要（可空；中断态不使用）
     */
    synchronized void finish(RunState next, String reason) {
        if (!enabled || (next != RunState.DONE && next != RunState.FAILED
                && next != RunState.INTERRUPTED)) {
            return;
        }
        cancelIdle();
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        state = next;
        detail = null;
        summary = reason;
        paint();
        idleTask = TICKER.schedule(() -> {
            synchronized (StatusLine.this) {
                if (state == next) {
                    idle();
                }
            }
        }, AUTO_IDLE_SECONDS, TimeUnit.SECONDS);
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
        runState(RunState.TOOL_RUNNING, "执行工具 " + toolName + "…");
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
        cancelIdle();
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        state = RunState.IDLE;
        detail = null;
        summary = null;
        // 高度必须稳定：空闲时也照常输出边框行，否则底部区域会收缩、
        // 输入行下方那条线突然消失
        surface.update(List.of(rule(), status.text(width)));
    }

    /**
     * 刷新一帧。
     */
    synchronized void paint() {
        if (!enabled || state == RunState.IDLE) {
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
     * 组装状态行文案：转圈帧 + 当前阶段（+ 静态三项）。
     *
     * <p>不显示用量：用量四项等 0.6 落库（issue #331）；状态行回答「在干什么」，
     * 并在右侧常驻「模式 / 模型与档位 / 工作目录」（issue #330）。</p>
     *
     * @return 文案
     */
    private String compose() {
        String text = switch (state) {
            case REQUESTING -> framePrefix() + " " + (detail == null ? "请求中…" : detail);
            case GENERATING -> framePrefix() + " 生成中…";
            case TOOL_RUNNING -> framePrefix() + " " + (detail == null ? "执行工具…" : detail);
            case AWAITING_APPROVAL -> framePrefix() + " 等待审批…";
            case DONE -> "✓ 完成";
            case FAILED -> "✗ 失败";
            case INTERRUPTED -> "⏹ 已中断";
            default -> "";
        };
        if (state == RunState.REQUESTING || state == RunState.TOOL_RUNNING) {
            long millis = (System.nanoTime() - stateStartedAt) / 1_000_000L;
            if (millis >= 1000) {
                text += " " + String.format("%.1fs", millis / 1000.0);
            }
        }
        if (state == RunState.FAILED && summary != null && !summary.isBlank()) {
            text += " · " + summary.lines().findFirst().orElse("");
        }
        if (status.legacy()) {
            return clamp(text);
        }
        String combined = text + " · " + status.text(width);
        if (TextWidth.width(combined) <= width - 1) {
            return combined;
        }
        // 宽度不够时先保状态（它每秒都在变），三项由空闲帧继续展示
        return clamp(text);
    }

    /** 当前转圈帧。 */
    private String framePrefix() {
        return frames[frame % frames.length];
    }

    /** 取消「完成 / 失败自动回空闲」的定时器。 */
    private void cancelIdle() {
        if (idleTask != null) {
            idleTask.cancel(false);
            idleTask = null;
        }
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
