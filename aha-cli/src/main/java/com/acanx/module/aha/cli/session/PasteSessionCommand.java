package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.cli.tty.Clipboard;
import com.acanx.module.aha.common.exception.Exceptions;

import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;

/**
 * 会话内命令 {@code /paste}：把剪贴板内容作为<b>一条</b>消息发送。
 *
 * <p>存在的理由只有一个：<b>终端能力改不了，但入口可以绕开它</b>。
 * 传统 Windows 控制台（conhost）不支持「括号粘贴」，粘贴多行文本会被终端逐行提交
 * （每行单独成为一条消息，还会把半截内容发给模型）。走剪贴板就不经过终端，
 * 多行内容天然是一条消息。</p>
 *
 * <p>用法：{@code /paste [附加说明]}。带说明时，说明作为最后一段附在剪贴板内容之后
 * （例：{@code /paste 这段报错怎么修}）。命令本身<b>不发消息</b>：它把内容交给
 * {@link PendingInput}，由输入循环代替用户输入提交 —— 这样命令仍在回合线程上执行，
 * 与其他会话内命令的时序一致。</p>
 *
 * @since 0.1.0
 */
public final class PasteSessionCommand implements SessionCommand {

    /** 预览最多打印多少行（多了会把屏幕刷走，而用户本来就能在剪贴板里看到全文）。 */
    private static final int PREVIEW_LINES = 8;

    private final PendingInput pending;
    private final Supplier<Clipboard.Content> reader;

    /**
     * 创建命令（读真实系统剪贴板）。
     *
     * @param pending 内容交接点
     */
    public PasteSessionCommand(PendingInput pending) {
        this(pending, Clipboard::read);
    }

    /**
     * 创建命令并注入读取方式（测试用）。
     *
     * @param pending 内容交接点
     * @param reader  剪贴板读取器
     */
    public PasteSessionCommand(PendingInput pending, Supplier<Clipboard.Content> reader) {
        this.pending = pending;
        this.reader = reader;
    }

    @Override
    public String name() {
        return "paste";
    }

    @Override
    public String usage() {
        return "/paste [附加说明]";
    }

    @Override
    public String description() {
        return "把剪贴板内容作为一条消息发送（终端不支持多行粘贴时的替代入口）";
    }

    @Override
    public Outcome execute(SessionContext context, List<String> args) {
        PrintStream out = context.out();
        Clipboard.Content content;
        try {
            content = reader.get();
        } catch (RuntimeException e) {
            out.println("[error] " + Exceptions.message(e));
            return Outcome.CONTINUE;
        }
        String text = content.text();
        if (text.isBlank()) {
            out.println("剪贴板里没有文本内容（来源：" + content.source() + "）。");
            return Outcome.CONTINUE;
        }
        pending.put(compose(text, args));
        out.println("已读取剪贴板：" + lineCount(text) + " 行 / " + text.length()
                + " 字符（来源：" + content.source() + "）");
        printPreview(out, text);
        out.println("已作为本条消息提交"
                + (args == null || args.isEmpty() ? "（要附加说明：/paste <说明>）" : ""));
        return Outcome.CONTINUE;
    }

    /**
     * 拼出要发送的消息：剪贴板内容 + 可选的一段附加说明。
     *
     * @param text 剪贴板内容
     * @param args 附加说明
     * @return 消息文本
     */
    static String compose(String text, List<String> args) {
        String note = args == null || args.isEmpty() ? "" : String.join(" ", args).strip();
        return note.isEmpty() ? text : text.stripTrailing() + "\n\n" + note;
    }

    private static void printPreview(PrintStream out, String text) {
        String[] lines = text.split("\n", -1);
        out.println("  预览（前 " + Math.min(lines.length, PREVIEW_LINES) + " 行）：");
        for (int i = 0; i < lines.length && i < PREVIEW_LINES; i++) {
            out.println("  | " + lines[i]);
        }
        if (lines.length > PREVIEW_LINES) {
            out.println("  | …（还有 " + (lines.length - PREVIEW_LINES) + " 行）");
        }
    }

    private static int lineCount(String text) {
        return text.split("\n", -1).length;
    }
}
