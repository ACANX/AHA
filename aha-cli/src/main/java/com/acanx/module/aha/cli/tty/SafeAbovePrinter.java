package com.acanx.module.aha.cli.tty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * {@code LineReader#printAbove} 的安全包装：渲染异常不再冒到回合，降级为直写（issue #384）。
 *
 * <p>【为什么需要（2026-10-20 真机）】JLine 4.4.6 的宽字符计算在含 CJK 的长行上会用越界的
 * offset 调 JDK 的 grapheme {@code BreakIterator}：</p>
 *
 * <pre>
 * IllegalArgumentException: offset is out of bounds: 76
 *   at sun.util.locale.provider.BreakIteratorProviderImpl$GraphemeBreakIterator.following
 *   at org.jline.utils.WCWidth.charCountForGraphemeClusterBreakIterator
 *   ... Display.update → Status.redraw → LineReaderImpl.printAbove
 * </pre>
 *
 * <p>异常经 {@code AboveStream} 一路冒到 {@code ChatCommand#runTurn}，被当成
 * <b>「回合执行失败」</b> —— 用户白丢一整轮已经流式输出到一半的内容。显示宽度算错是
 * <b>渲染层</b>的问题，不该升级为业务失败。</p>
 *
 * <p>【策略】首次调用抛 {@link RuntimeException} 时记一条 WARN（只记一次），随后本实例
 * <b>永久降级为直写</b>（暂停状态区 → 写终端 → 恢复），后续行不再尝试 {@code printAbove}：
 * JLine 的宽度计算在 CJK 长行上是可复现的，每行重试只会刷屏。</p>
 *
 * <p>只捕 {@link RuntimeException}：JLine 的 {@code IllegalArgumentException} /
 * {@code IndexOutOfBoundsException} 都在这一族；{@link Error}（OOM 等）继续上抛，
 * 不被“渲染兜底”吞掉。</p>
 *
 * @since 0.1.5
 */
public final class SafeAbovePrinter implements Consumer<String> {

    private static final Logger LOG = LoggerFactory.getLogger(SafeAbovePrinter.class);

    private final Consumer<String> printAbove;

    private final Consumer<String> fallback;

    private final Runnable suspend;

    private final Runnable resume;

    /** 是否已降级（首次渲染异常后置位，之后不再尝试 printAbove）。 */
    private boolean degraded;

    /**
     * 创建安全包装。
     *
     * @param printAbove 正常路径（通常是 {@code reader::printAbove}）
     * @param fallback   降级路径（通常是 {@code terminal.writer()::println}）
     * @param suspend    降级直写前暂停状态区（可空）
     * @param resume     降级直写后恢复状态区（可空）
     */
    public SafeAbovePrinter(Consumer<String> printAbove, Consumer<String> fallback,
                            Runnable suspend, Runnable resume) {
        this.printAbove = printAbove;
        this.fallback = fallback;
        this.suspend = suspend;
        this.resume = resume;
    }

    @Override
    public synchronized void accept(String line) {
        if (!degraded) {
            try {
                printAbove.accept(line);
                return;
            } catch (RuntimeException e) {
                // 只记一次：同一个 JLine 缺陷会在每一行上复现，逐行打日志等于刷屏
                degraded = true;
                LOG.warn("printAbove 渲染失败（已降级为直写，后续输出不再走状态区重绘）：{}", e.toString());
            }
        }
        write(line);
    }

    private void write(String line) {
        try {
            if (suspend != null) {
                suspend.run();
            }
            try {
                fallback.accept(line);
            } finally {
                if (resume != null) {
                    resume.run();
                }
            }
        } catch (RuntimeException e) {
            // 连直写都失败时只能丢这一行：渲染层不能反过来杀掉回合
            LOG.warn("降级直写失败，本行已丢弃：{}", e.toString());
        }
    }

    /**
     * 是否已降级为直写。
     *
     * @return 已降级返回 {@code true}
     */
    public boolean degraded() {
        return degraded;
    }
}
