package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.cli.tty.StatusSurface;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 记录状态行载体的测试替身。
 *
 * <p>状态行不再写进输出流（那会与正文混在一起、也无法原地重绘），
 * 因此断言改为针对载体收到的内容。</p>
 *
 * @since 0.1.0
 */
final class RecordingStatusSurface implements StatusSurface {

    private final List<String> painted = new ArrayList<>();

    private final List<String> borders = new ArrayList<>();

    private final AtomicInteger hides = new AtomicInteger();

    private final boolean available;

    RecordingStatusSurface() {
        this(true);
    }

    RecordingStatusSurface(boolean available) {
        this.available = available;
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public void update(List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        // 第一行是输入框下边框，其余是状态文本——分开记录，断言时才不会互相干扰
        borders.add(lines.get(0));
        painted.add(lines.get(lines.size() - 1));
    }

    @Override
    public void hide() {
        hides.incrementAndGet();
    }

    @Override
    public void close() {
    }

    /**
     * 已绘制的帧。
     *
     * @return 帧列表
     */
    List<String> painted() {
        return List.copyOf(painted);
    }

    /**
     * 已绘制的输入框下边框。
     *
     * @return 边框列表
     */
    List<String> borders() {
        return List.copyOf(borders);
    }

    /**
     * 最后一帧。
     *
     * @return 文本；从未绘制时返回 {@code null}
     */
    String last() {
        return painted.isEmpty() ? null : painted.get(painted.size() - 1);
    }

    /**
     * 让出底部区域的次数。
     *
     * @return 次数
     */
    int hides() {
        return hides.get();
    }
}
