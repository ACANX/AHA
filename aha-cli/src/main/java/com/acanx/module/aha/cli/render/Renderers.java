package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.cli.tty.TerminalCapabilities.ColorDepth;
import com.acanx.module.aha.cli.tty.OutputEncoding;
import com.acanx.module.aha.cli.tty.StatusSurface;
import com.acanx.module.aha.cli.tty.TerminalCapabilities;

import java.io.PrintStream;

/**
 * 渲染器工厂。
 *
 * <p>「带色」与「纯文本」不是两个渲染实现，而是同一实现的两个 {@link Style}——
 * 折行、截断、块结构等逻辑与颜色无关，拆成两个类只会让折行逻辑重复一遍。</p>
 *
 * @since 0.1.0
 */
public final class Renderers {

    private Renderers() {
    }

    /**
     * 按终端能力与输出编码创建渲染器。
     *
     * @param capabilities 终端能力
     * @param encoding     输出编码（决定列表符号与转圈帧是否降级）
     * @param model        模型名，用于状态行；可为 {@code null}
     * @param out          输出流
     * @return 渲染器
     */
    public static Renderer of(TerminalCapabilities capabilities, OutputEncoding encoding,
                             String model, PrintStream out) {
        return of(capabilities, encoding, model, out, StatusSurface.NONE);
    }

    /**
     * 创建渲染器，并指定状态行载体。
     *
     * <p>「流式期间保持输入可用」下的输出是经 {@code printAbove} 逐行上抛的，
     * 原地重绘无从谈起；因此状态行必须另走底部常驻区（{@link StatusSurface}），
     * 而不是把 {@code \r} 重绘混进输出流。</p>
     *
     * @param capabilities 终端能力
     * @param encoding     输出编码（决定列表符号与转圈帧是否降级）
     * @param model        模型名，用于状态行；可为 {@code null}
     * @param out          输出流
     * @param statusSurface 状态行载体；传 {@link StatusSurface#NONE} 即不显示
     * @return 渲染器
     */
    public static Renderer of(TerminalCapabilities capabilities, OutputEncoding encoding,
                             String model, PrintStream out, StatusSurface statusSurface) {
        boolean color = capabilities != null && capabilities.colored();
        ColorDepth depth = color ? capabilities.depth() : ColorDepth.NONE;
        int width = capabilities == null ? 0 : capabilities.width();
        boolean tty = capabilities != null && capabilities.tty();
        boolean unicode = encoding == null || encoding.symbols();
        // 状态行只在 TTY 下启用；具体能否显示由载体自己判断（终端不支持时为 NONE）
        Style style = Style.of(depth);
        // 状态行的第一行是输入框下边框，因此它也要拿到样式
        StatusLine status = new StatusLine(statusSurface, tty, unicode, width, model, style);
        // 执行环境：明细行要如实告诉用户「在哪个终端、哪个目录」下跑的
        return new StreamRenderer(out, style, width, status, unicode, ToolEnvironment.current());
    }
}
