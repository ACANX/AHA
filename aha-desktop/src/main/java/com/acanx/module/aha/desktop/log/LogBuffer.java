package com.acanx.module.aha.desktop.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 日志环形缓冲（纯逻辑）。
 *
 * <p>三件事刻意如此：</p>
 * <ul>
 *   <li><strong>有上限</strong>：界面看日志不能无限吃内存，超出容量丢最旧的；</li>
 *   <li><strong>过滤在读的时候做</strong>：切换级别不会丢掉已经收到的行，
 *       用户把门槛从 WARN 调回 DEBUG 时还能看到之前的记录；</li>
 *   <li><strong>不持有 log4j 类型</strong>：缓冲与过滤可以在无图形环境下测。</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class LogBuffer {

    /** 默认容量（行）。 */
    public static final int DEFAULT_CAPACITY = 2000;

    private final int capacity;

    private final Deque<LogLine> lines = new ArrayDeque<>();

    /** 累计收到多少行（含被挤掉的），用于「有没有新内容」判断。 */
    private long total;

    /**
     * @param capacity 容量（行）；小于 1 时退化为 {@link #DEFAULT_CAPACITY}
     */
    public LogBuffer(int capacity) {
        this.capacity = capacity < 1 ? DEFAULT_CAPACITY : capacity;
    }

    /** 用默认容量。 */
    public LogBuffer() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 追加一行；超出容量时挤掉最旧的。
     *
     * <p>线程安全：日志来自任意线程（虚拟线程也会写日志），因此这里是同步方法。</p>
     *
     * @param line 日志行；{@code null} 被忽略
     */
    public synchronized void add(LogLine line) {
        if (line == null) {
            return;
        }
        lines.addLast(line);
        total++;
        while (lines.size() > capacity) {
            lines.removeFirst();
        }
    }

    /**
     * 取达到门槛的行。
     *
     * @param threshold 门槛级别；{@code null} 视为全部
     * @return 日志行（时间正序）
     */
    public synchronized List<LogLine> snapshot(LogLevel threshold) {
        List<LogLine> hits = new ArrayList<>(lines.size());
        for (LogLine line : lines) {
            if (line.level().atLeast(threshold)) {
                hits.add(line);
            }
        }
        return hits;
    }

    /**
     * 渲染达到门槛的行。
     *
     * @param threshold 门槛级别
     * @param maxLines  最多渲染多少行（取最新的）；{@code <= 0} 表示不限
     * @return 文本
     */
    public synchronized String render(LogLevel threshold, int maxLines) {
        List<LogLine> hits = snapshot(threshold);
        if (maxLines > 0 && hits.size() > maxLines) {
            hits = hits.subList(hits.size() - maxLines, hits.size());
        }
        int width = 0;
        for (LogLine line : hits) {
            width = Math.max(width, Math.min(LogLine.LOGGER_MAX, line.shortLogger().length()));
        }
        StringBuilder out = new StringBuilder();
        for (LogLine line : hits) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line.render(width));
        }
        return out.toString();
    }

    /**
     * 清空缓冲（不影响「累计行数」，累计值只增不减，用于判断是否有新行）。
     */
    public synchronized void clear() {
        lines.clear();
    }

    /**
     * 当前行数。
     *
     * @return 行数
     */
    public synchronized int size() {
        return lines.size();
    }

    /**
     * 累计收到的行数（含已被挤掉的）。
     *
     * @return 累计行数
     */
    public synchronized long totalReceived() {
        return total;
    }

    /**
     * 容量。
     *
     * @return 容量（行）
     */
    public int capacity() {
        return capacity;
    }
}
