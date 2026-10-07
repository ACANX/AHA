package com.acanx.module.aha.cli.tty;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.InfoCmp;
import org.jline.utils.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 JLine {@link Status} 的状态行：终端底部保留一行。
 *
 * <p>之所以必须放在底部：这是 JLine 唯一支持**原地重绘**的位置。曾尝试把状态行放进提示符
 * 以实现「状态行在输入行上方」，它确实能显示，但靠 {@code setPrompt + redisplay} 刷新时
 * 会与流式正文经 {@code printAbove} 上抛**互相冲突**——正文整段消失且没有任何异常
 * （{@code PrintStream} 会吞掉），实测为竞态，不可交付。</p>
 *
 * <p>其它实测要点：{@code getStatus()} 的第二个参数是「不存在时是否创建」；
 * JLine 不处理窗口变化，需自行比对尺寸调用 {@code resize()}；
 * dumb 终端下它会返回对象却占不住底部区域，且 {@code close()} 会 NPE，因此按能力门禁。</p>
 *
 * @since 0.1.0
 */
public final class JLineStatusSurface implements StatusSurface {

    private static final Logger LOG = LoggerFactory.getLogger(JLineStatusSurface.class);

    private final Terminal terminal;

    private final Status status;

    private final boolean color;

    private Size size;

    private boolean suspended;

    private JLineStatusSurface(Terminal terminal, Status status, boolean color) {
        this.terminal = terminal;
        this.status = status;
        this.color = color;
        this.size = terminal.getSize();
    }

    /**
     * 创建状态行载体；终端不支持时返回 {@link StatusSurface#NONE}。
     *
     * @param terminal JLine 终端
     * @param color    是否解析 ANSI 颜色
     * @return 状态行载体
     */
    public static StatusSurface create(Terminal terminal, boolean color) {
        if (terminal == null) {
            return StatusSurface.NONE;
        }
        String type = terminal.getType();
        if (type == null || type.startsWith("dumb")) {
            return StatusSurface.NONE;
        }
        if (terminal.getStringCapability(InfoCmp.Capability.change_scroll_region) == null) {
            return StatusSurface.NONE;
        }
        Size current = terminal.getSize();
        if (current == null || current.getRows() <= 0) {
            return StatusSurface.NONE;
        }
        try {
            Status status = Status.getStatus(terminal, true);
            if (status == null) {
                return StatusSurface.NONE;
            }
            // 不用内置边框：它无法上色，而输入框的上下两条线需要同一种高亮紫。
            // 改为把下边线作为保留区的第一行（由 StatusLine 生成），占用高度不变。
            status.setBorder(false);
            return new JLineStatusSurface(terminal, status, color);
        } catch (RuntimeException e) {
            LOG.warn("状态行不可用，已禁用: {}", e.getMessage());
            return StatusSurface.NONE;
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void update(List<String> lines) {
        if (suspended) {
            status.restore();
            suspended = false;
        }
        resizeIfNeeded();
        List<AttributedString> attributed = new ArrayList<>(lines.size());
        for (String line : lines) {
            attributed.add(color ? AttributedString.fromAnsi(line) : new AttributedString(line));
        }
        status.update(attributed);
    }

    @Override
    public void hide() {
        if (!suspended) {
            status.suspend();
            suspended = true;
        }
    }

    @Override
    public void close() {
        if (suspended) {
            status.restore();
            suspended = false;
        }
        try {
            status.close();
        } catch (RuntimeException e) {
            LOG.debug("关闭状态行失败: {}", e.getMessage());
        }
    }

    /**
     * 窗口尺寸变化时重算滚动区域。
     */
    private void resizeIfNeeded() {
        Size current = terminal.getSize();
        if (current != null && !current.equals(size)) {
            size = current;
            status.resize(current);
        }
    }
}
