package com.acanx.module.aha.cli.tty;

import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.common.exception.Exceptions;
import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.LineReader;
import org.jline.reader.Widget;

import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;

/**
 * 把 <b>Alt+V / Ctrl+V</b> 绑定成「读剪贴板 → 原子插入输入行」。
 *
 * <p><b>为什么必须有这条路</b>：终端自己处理的粘贴在<b>支持括号粘贴</b>的终端上没问题
 * （终端会把内容用 {@code ESC[200~ … ESC[201~} 包起来，JLine 收到的是整体，换行被换成字面换行）；
 * 而**传统 Windows 控制台（conhost）不支持括号粘贴** —— 它的粘贴是在宿主里逐字符灌入键盘事件的，
 * 粘贴里的每个换行都变成一次「回车」，于是多行内容被逐行提交。这条路上进程与终端之间没有任何
 * 可用来区分「粘贴的换行」与「用户按的回车」的信号，因此**只能在进程侧绕开终端**：
 * 自己去读剪贴板，再直接写进输入缓冲区。读剪贴板不经过终端输入流，逐行提交就不可能发生。</p>
 *
 * <p>键位选择的依据：{@code Ctrl+V} 在 conhost 与 Windows Terminal 里被宿主截获（我们收不到），
 * 所以**主键位是 {@code Alt+V}**（宿主不截获）；同时仍绑 {@code Ctrl+V}，在那些会把该键原样
 * 交给应用、而用户习惯按它的终端上（不少 Linux 终端）也能用。同一取舍见 pi 的
 * 「Windows 下把粘贴类键位改到 Alt+V，避开终端保留的 Ctrl+V」。</p>
 *
 * @see PastePlaceholder 大块内容折叠成 {@code [paste #1 +42 lines]}
 * @see com.acanx.module.aha.cli.session.PasteSessionCommand 不想按键、想直接发消息时用的 {@code /paste}
 * @since 0.2.0
 */
public final class ClipboardPaste {

    /** 注册到 JLine 的 widget 名（同时作为排查时的关键字）。 */
    public static final String WIDGET = "aha-clipboard-paste";

    /** 绑定键位：{@code Alt+V}（主）与 {@code Ctrl+V}（终端不截获时才收得到）。 */
    public static final List<String> KEYS = List.of("\033v", "\u0016");

    /** 超过这个字符数就额外提醒一次（内容仍会插入，不静默截断）。 */
    private static final int BIG_CHARS = 100_000;

    private ClipboardPaste() {
    }

    /**
     * 绑定键位。
     *
     * @param reader    JLine 行读取器
     * @param clipboard 剪贴板来源（生产上就是 {@link Clipboard#read}；测试注入替身）
     * @param out       提示输出（走 {@code printAbove}，与其它输出同一路径）
     * @return 本次会话的占位符表（输入循环要在提交前用它展开）
     */
    public static PastePlaceholder bind(LineReader reader,
                                        Supplier<Clipboard.Content> clipboard,
                                        PrintStream out) {
        PastePlaceholder placeholders = new PastePlaceholder();
        Widget widget = () -> insert(reader, clipboard, out, placeholders);
        reader.getWidgets().put(WIDGET, widget);
        KeyMap<Binding> main = reader.getKeyMaps().get(LineReader.MAIN);
        if (main != null) {
            main.bind(widget, KEYS);
        }
        return placeholders;
    }

    /**
     * widget 本体：读剪贴板 → 写入输入行缓冲区。
     *
     * <p>写成方法而不是内联 lambda，是为了让测试能直接驱动它（不依赖按键解码）。</p>
     *
     * @param reader       JLine 行读取器
     * @param clipboard    剪贴板来源
     * @param out          提示输出
     * @param placeholders 占位符表
     * @return 是否已消费该按键（失败时返回 {@code false}，让用户还能继续编辑）
     */
    static boolean insert(LineReader reader, Supplier<Clipboard.Content> clipboard,
                          PrintStream out, PastePlaceholder placeholders) {
        Clipboard.Content content;
        try {
            content = clipboard.get();
        } catch (AhaException e) {
            // 读不到要说清「怎么办」，而不是丢一个异常（error 文案由 Clipboard 组装）
            out.println("[warn] " + Exceptions.message(e));
            return false;
        } catch (RuntimeException e) {
            out.println("[warn] 读剪贴板失败：" + Exceptions.message(e));
            return false;
        }
        if (content.text().isBlank()) {
            out.println("[warn] 剪贴板里没有文本内容。");
            return false;
        }
        String compact = placeholders.compact(content.text());
        reader.getBuffer().write(compact);
        if (!compact.equals(content.text())) {
            // 折叠时必须说一声：否则用户看到输入行里只剩一个占位符，会以为内容丢了
            out.println("[paste] " + compact + " 已插入（来源：" + content.source()
                    + "；整块内容仍在，回车时自动展开）");
        }
        if (content.text().length() > BIG_CHARS) {
            out.println("[warn] 剪贴板内容有 " + content.text().length() + " 字符，可能超出模型上下文。");
        }
        return true;
    }
}
