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

    /** 会话 ID（倒数第二行占位）。 */
    private final String sessionId;

    private ScheduledFuture<?> task;

    /** 完成 / 失败态自动回到空闲的定时器。 */
    private ScheduledFuture<?> idleTask;

    private volatile RunState state = RunState.IDLE;

    /** 是否处于暂停（授权问答 / printAbove 期间）：暂停时不重绘、不启动 ticker。 */
    private boolean suspended;

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
        this(surface, enabled, unicode, width, statusBar, style, null);
    }

    /**
     * 创建状态行，并指定倒数第二行展示的会话 ID。
     *
     * @param surface   状态行载体
     * @param enabled   是否启用
     * @param unicode   编码是否支持盲文帧
     * @param width     终端宽度
     * @param statusBar 状态栏
     * @param style     样式
     * @param sessionId 会话 ID（可空，空时显示 {@code -}）
     */
    StatusLine(StatusSurface surface, boolean enabled, boolean unicode, int width,
               StatusBar statusBar, Style style, String sessionId) {
        this.surface = surface == null ? StatusSurface.NONE : surface;
        this.frames = unicode ? UNICODE_FRAMES : ASCII_FRAMES;
        this.bar = unicode ? "─" : "-";
        this.width = width;
        this.enabled = enabled && width > 0 && this.surface.available();
        this.status = statusBar == null ? StatusBar.legacy("") : statusBar;
        this.style = style == null ? Style.of(false) : style;
        this.sessionId = sessionId;
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
        if (!suspended) {
            paint();
            if (task == null) {
                task = TICKER.scheduleWithFixedDelay(this::paint, TICK_MS, TICK_MS,
                        TimeUnit.MILLISECONDS);
            }
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
        suspended = false;
        state = RunState.IDLE;
        detail = null;
        summary = null;
        // 高度必须稳定：空闲时也照常输出边框行，否则底部区域会收缩、
        // 输入行下方那条线突然消失
        surface.update(List.of(rule(), combinedLine()));
    }

    /**
     * 恢复状态区：按当前状态重绘完整三行（动态横线 / 状态字段 / 静态参数）。
     *
     * <p>printAbove 之前状态区被 {@code suspend()} 让出；恢复时不能只调
     * {@code Status.restore()}（会用旧内容重绘、与正文混排），必须重新 {@code update}。</p>
     */
    synchronized void resume() {
        if (!enabled) {
            return;
        }
        suspended = false;
        if (state == RunState.IDLE) {
            surface.update(List.of(rule(), combinedLine()));
            return;
        }
        // 不立即 update：交给 120ms 后的 ticker，避免与 readLine 的输入行重绘竞争
        if (task == null) {
            task = TICKER.scheduleWithFixedDelay(this::paint, TICK_MS, TICK_MS,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 恢复并切换状态：只更新状态、重启 ticker，不立即重绘。
     *
     * <p>审批结束后立刻 {@code update} 会与主线程的输入行重绘交错，
     * 导致光标落到错误行；延迟到 ticker 再由 {@link #paint()} 刷新。</p>
     *
     * @param next 目标状态
     * @param text 状态文案
     */
    synchronized void resumeWith(RunState next, String text) {
        if (!enabled || next == null) {
            return;
        }
        suspended = false;
        cancelIdle();
        state = next;
        detail = text;
        summary = null;
        stateStartedAt = System.nanoTime();
        if (task == null) {
            task = TICKER.scheduleWithFixedDelay(this::paint, TICK_MS, TICK_MS,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 暂停状态区：停掉 120ms ticker 并让出底部区域。
     *
     * <p>授权问答等需要独占输入行的场景使用；恢复走 {@link #resume()}。</p>
     */
    synchronized void suspend() {
        if (!enabled) {
            return;
        }
        suspended = true;
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    /**
     * 刷新一帧。
     */
    synchronized void paint() {
        if (!enabled || suspended || state == RunState.IDLE) {
            return;
        }
        surface.update(List.of(dynamicLine(), combinedLine()));
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
        // 首列留空：JLine Status 的第一列在部分终端会残留上一帧字符（如正文行号）
        return style.frame(" " + bar.repeat(Math.max(0, cells - 1)));
    }

    /**
     * 动态状态行：`── ⠋ 生成中… ─────`（空闲时退化为纯横线）。
     *
     * <p>动态参数放在横线内（左对齐，右侧用横线填充），不再与静态参数挤在同一行。</p>
     *
     * @return 已着色文本
     */
    private String dynamicLine() {
        if (state == RunState.IDLE) {
            return rule();
        }
        String text = " ── " + dynamicText() + " ";
        int limit = width > 0 ? Math.max(1, width - 1) : 40;
        int fill = Math.max(0, limit - TextWidth.width(text));
        // 前缀 + 文本 + 填充可能超过终端宽度（clamp 只作用于动态文案），整体再截一次
        return style.frame(clamp(text + bar.repeat(fill)));
    }

    /**
     * 状态字段行：暂未实现字段的占位（倒第二行）。
     *
     * @return 文案
     */
    /**
     * 合并行：会话 ID · 目录 (分支) · 静态指标。
     *
     * <p>JLine {@code Status} 的多行在 {@code printAbove} 场景下会叠加错位，
     * 因此这里压成两行（动态横线 + 合并行），而不是三行。</p>
     *
     * <p>前导一个空格：JLine {@code Status} 的第一列在部分终端上会被占用，
     * 不加空格时首个字符会被吞掉（曾出现「自动」显示成「动」）。</p>
     *
     * @return 文案
     */
    private String combinedLine() {
        String id = sessionId == null || sessionId.isBlank() ? "-" : sessionId;
        StringBuilder text = new StringBuilder(" ").append(id);
        String location = status.location();
        if (!location.isEmpty()) {
            text.append("  ·  ").append(location);
        }
        String summary = status.summary(width);
        if (!summary.isEmpty()) {
            text.append("  ·  ").append(summary);
        }
        return clamp(text.toString());
    }

    /**
     * 动态状态文案：转圈帧 + 当前阶段 + 耗时 / 失败原因。
     *
     * @return 文案
     */
    private String dynamicText() {
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
