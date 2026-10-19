package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.common.event.AgentEvent;
import com.acanx.module.aha.common.event.ContentEvent;
import com.acanx.module.aha.common.event.DoneEvent;
import com.acanx.module.aha.common.event.ErrorEvent;
import com.acanx.module.aha.common.event.ToolCallEvent;
import com.acanx.module.aha.common.event.ToolResultEvent;
import com.acanx.module.aha.common.event.UsageEvent;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import com.acanx.module.aha.common.tool.ToolKind;

/**
 * 流式渲染器：内联（inline）输出，不使用全屏备用缓冲区。
 *
 * <p>保持终端<b>原生 scrollback</b>，用户可继续用终端自身的搜索、复制、分页与重定向。
 * 为此只使用最基础的 SGR 颜色与换行，不发送光标定位 / 清屏等序列——那些在第三方终端
 * 与 tmux 下的行为并不一致。</p>
 *
 * @since 0.1.0
 */
public final class StreamRenderer implements Renderer {

    private final LineWrapper wrapper;
    private final Style style;
    private final MarkdownStream markdown;
    private final StatusLine status;
    private final ToolEnvironment environment;

    private final boolean unicode;

    /** 本回合是否已进入「生成中」（首个正文增量只切一次）。 */
    private boolean generating;

    /** 失败摘要（ERROR 事件设置，endTurn 时展示）。 */
    private String failureSummary;

    /** 当前工具块类别（结果正文据此沿用同一底色）；非工具块时为 {@code null}。 */
    private ToolKind currentKind;

    private final int width;

    /** 当前工具调用开始时间：结果行要显示耗时。 */
    private long toolStartedAt;

    private boolean toolRunning;

    /** 当前工具调用的参数：明细行要用（结果事件里没有）。 */
    private Map<String, Object> toolArgs = Map.of();

    /**
     * 创建渲染器。
     *
     * <p>包级可见：外部统一走 {@link Renderers}。</p>
     *
     * @param out         输出流
     * @param style       样式
     * @param width       折行宽度；{@code <= 0} 表示不折行
     * @param status      瞬时状态行
     * @param unicode     编码是否支持制表符与扩展符号
     * @param environment 工具执行环境（用于还原「在哪个终端、哪个目录」）
     */
    StreamRenderer(PrintStream out, Style style, int width, StatusLine status, boolean unicode,
                   ToolEnvironment environment) {
        this.wrapper = new LineWrapper(out, width);
        this.style = style;
        this.markdown = new MarkdownStream(style, unicode, width);
        this.status = status;
        this.unicode = unicode;
        this.width = width;
        this.environment = environment == null ? ToolEnvironment.none() : environment;
    }

    @Override
    public void beginTurn() {
        // 首次响应可能等好几秒，没有反馈用户会以为卡住了
        generating = false;
        failureSummary = null;
        status.runState(RunState.REQUESTING, "请求中…");
    }

    @Override
    public void awaitingApproval() {
        status.runState(RunState.AWAITING_APPROVAL, null);
    }

    @Override
    public void approvalResolved() {
        status.runState(RunState.TOOL_RUNNING, "执行工具…");
    }

    @Override
    public void cancelled() {
        status.idle();
    }

    @Override
    public void onEvent(AgentEvent event) {
        switch (event) {
            case ContentEvent content -> {
                if (!generating) {
                    generating = true;
                    status.runState(RunState.GENERATING, null);
                }
                wrapper.write(markdown.feed(content.delta()));
            }
            case ToolCallEvent call -> {
                // 区块前留空行：连续多次工具调用不隔开就会糊成一片
                wrapper.ensureLineStart();
                wrapper.newline();
                toolArgs = call.args() == null ? Map.of() : call.args();
                block(ToolBlock.head(call.toolName(), toolArgs, style, width));
                if (ToolKind.of(call.toolName()) == ToolKind.EXEC) {
                    // 命令单独一行，像在终端里敲下去的样子：目录 + 提示符 + 命令全文
                    block(ToolBlock.command(asText(toolArgs.get("command")), style, environment,
                            width));
                } else {
                    // 其余工具逐条列出参数（命令类没有别的信息可列）
                    for (String line : ToolBlock.details(call.toolName(), toolArgs, style, width,
                            environment)) {
                        block(line);
                    }
                }
                currentKind = ToolKind.of(call.toolName());
                toolStartedAt = System.nanoTime();
                toolRunning = true;
                // 工具执行是第二段「无输出」的等待：shell / http 可能跑好几秒，
                // 状态行里的实时秒数是「它还活着」的唯一证据
                status.tool(call.toolName());
            }
            case ToolResultEvent result -> {
                status.runState(RunState.GENERATING, null);
                long millis = toolRunning ? (System.nanoTime() - toolStartedAt) / 1_000_000L : 0L;
                toolRunning = false;
                boolean ok = result.success();
                print(ok ? ToolBlock.body(result.result(), style, width, ToolBlock.PREVIEW_LINES,
                                true, currentKind)
                        : ToolBlock.body(result.result(), style, width, ToolBlock.FAILURE_LINES,
                                false, currentKind),
                        ok);
                block(ToolBlock.result(ok, ok
                        ? ToolBlock.detail(true, result.result(), millis)
                        : ToolBlock.failureDetail(result.result(), millis), style, width, unicode));
                currentKind = null;
            }
            case ErrorEvent error -> {
                failureSummary = error.message();
                currentKind = null;
                block(style.error("[error] " + error.code()));
                for (String line : ToolBlock.body(error.message(), style, width,
                        ToolBlock.FAILURE_LINES, false, null)) {
                    block(line);
                }
            }
            case UsageEvent usage -> usage(usage);
            case DoneEvent ignored -> {
                // 结束原因不单列一行：正常完成时输出噪音，异常时已由 ErrorEvent 呈现
            }
            default -> {
                // 未知事件类型（如 1.0+ 新增）静默忽略，保证前向兼容
            }
        }
    }

    @Override
    public void endTurn() {
        wrapper.write(markdown.flush());
        wrapper.ensureLineStart();
        // 结束态保留 2 秒后回到空闲（#342：完成 / 失败都要肉眼可辨）
        if (failureSummary != null) {
            status.finish(RunState.FAILED, failureSummary);
        } else {
            status.finish(RunState.DONE, null);
        }
    }

    /**
     * 取值文本。
     *
     * @param value 值，可为 {@code null}
     * @return 文本；空值返回空串
     */
    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void usage(UsageEvent usage) {
        if (usage.promptTokens() <= 0 && usage.completionTokens() <= 0) {
            return;
        }
        status.idle();
        block(style.muted("[usage] 输入 " + usage.promptTokens()
                + " / 输出 " + usage.completionTokens() + " tokens"));
    }

    /**
     * 输出结果区：先给「结果」小标题，再逐行写出正文。
     *
     * <p>标题是必要的——否则缩进的正文既可能是命令的续行，也可能是执行结果，
     * 读者无从分辨。成功且无输出时给一句「无输出」，免得看起来像漏了内容。</p>
     *
     * @param lines   正文行
     * @param success 是否成功
     */
    private void print(List<String> lines, boolean success) {
        if (lines.isEmpty()) {
            return;
        }
        String label = style.muted(" ".repeat(ToolBlock.INDENT) + (success ? "结果" : "错误"));
        block(currentKind == null ? label : style.blockLine(currentKind, label, width));
        for (String line : lines) {
            block(line);
        }
    }

    /**
     * 输出一整块：先落到行首，写完再换行。
     *
     * @param text 已着色的文本
     */
    private void block(String text) {
        wrapper.ensureLineStart();
        wrapper.write(text);
        wrapper.newline();
    }
}
