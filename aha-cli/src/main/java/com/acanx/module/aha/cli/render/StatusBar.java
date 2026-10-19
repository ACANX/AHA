package com.acanx.module.aha.cli.render;

import java.util.ArrayList;
import java.util.List;

/**
 * 底部状态栏的静态三项：**运行模式 / 模型与档位 / 工作目录**（issue #330）。
 *
 * <p>只负责「取值 → 文案 → 按宽度压缩」，不碰终端；绘制仍由 {@link StatusLine} 交给
 * {@link com.acanx.module.aha.cli.tty.StatusSurface}。</p>
 *
 * <p><b>压缩顺序</b>（宽度不足时）：先缩短目录到「…/末层」，再去掉档位，最后按显示宽度硬截断。
 * 模式排在最前：它最短，也是「切错后后果最重」的一项，任何宽度下都必须可见。</p>
 *
 * <p><b>为什么不含 token / 缓存</b>：那四项依赖 0.6 的用量落库，由 issue #331 单独跟进；
 * 本类只接静态数据，数据源变化不改渲染代码。</p>
 *
 * @since 0.1.5
 */
public final class StatusBar {

    /** 目录默认保留的尾部层数。 */
    static final int KEEP_LEVELS = 3;

    /** 运行模式。 */
    public enum Mode {
        /** 自动：只读与网络自动放行，写 / 执行 / 管理需确认。 */
        AUTO("自动"),
        /** 完全授权：所有权限自动放行（`--yes`，或会话内选 `a`）。 */
        FULL("完全授权"),
        /** 严格：所有工具调用都需确认（含只读）。 */
        STRICT("严格");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        /**
         * 状态栏上的中文标签。
         *
         * @return 标签
         */
        String label() {
            return label;
        }
    }

    /**
     * 三项的不可变快照（便于单测与日志）。
     *
     * @param model  模型名
     * @param effort 思考强度 / 档位
     * @param cwd    当前工作目录
     * @param mode   运行模式
     */
    record Context(String model, String effort, String cwd, Mode mode) {
    }

    private volatile Context context;

    /** 兼容模式：只显示一个值（旧构造 / 旧测试使用），不参与三项渲染。 */
    private final boolean legacy;

    private StatusBar(Context context, boolean legacy) {
        this.context = context;
        this.legacy = legacy;
    }

    /**
     * 创建三项状态栏。
     *
     * @param model  模型名（可空）
     * @param effort 思考强度 / 档位（可空）
     * @param cwd    工作目录（可空）
     * @param mode   运行模式；{@code null} 视为 {@link Mode#AUTO}
     * @return 状态栏
     */
    public static StatusBar of(String model, String effort, String cwd, Mode mode) {
        return new StatusBar(new Context(clean(model), clean(effort), clean(cwd),
                mode == null ? Mode.AUTO : mode), false);
    }

    /**
     * 兼容构造：只显示一个值，保持旧行为（`StatusLine` 的旧签名与既有测试）。
     *
     * @param text 要显示的值
     * @return 状态栏
     */
    static StatusBar legacy(String text) {
        return new StatusBar(new Context(clean(text), "", "", Mode.AUTO), true);
    }

    /**
     * 当前快照。
     *
     * @return 快照
     */
    Context context() {
        return context;
    }

    /**
     * 是否兼容模式（只显示单值）。
     *
     * @return 兼容模式返回 {@code true}
     */
    boolean legacy() {
        return legacy;
    }

    /**
     * 切换模式：会话内授权后调用，下一次绘制即生效。
     *
     * @param mode 新模式；{@code null} 忽略
     */
    public void setMode(Mode mode) {
        if (mode == null) {
            return;
        }
        Context current = context;
        context = new Context(current.model(), current.effort(), current.cwd(), mode);
    }

    /**
     * 组装状态栏文案。
     *
     * @param width 终端宽度；{@code <= 0} 表示不限宽
     * @return 文案（不含换行）
     */
    String text(int width) {
        Context current = context;
        if (legacy) {
            return current.model();
        }
        String model = current.model().isEmpty() ? "-" : current.model();
        String effort = current.effort().isEmpty() ? "" : current.effort();
        String modelPart = effort.isEmpty() ? model : model + "/" + effort;
        // 顺序：模式（最短、最重要）→ 模型/档位 → 目录（可压缩）
        String full = current.mode().label() + " · " + modelPart
                + " · " + shorten(current.cwd(), KEEP_LEVELS);
        if (width <= 0 || TextWidth.width(full) <= width - 1) {
            return full;
        }
        String shortDir = current.mode().label() + " · " + modelPart
                + " · " + shorten(current.cwd(), 1);
        if (TextWidth.width(shortDir) <= width - 1) {
            return shortDir;
        }
        String noEffort = current.mode().label() + " · " + model;
        if (TextWidth.width(noEffort) <= width - 1) {
            return noEffort;
        }
        return clamp(noEffort, Math.max(1, width - 1));
    }

    /**
     * 目录截断：保留末尾 {@code keep} 层，前缀 {@code …}。
     *
     * @param cwd  原始路径（可空）
     * @param keep 保留层数
     * @return 截断后的路径；不足 {@code keep} 层时原样返回
     */
    static String shorten(String cwd, int keep) {
        if (cwd == null || cwd.isBlank()) {
            return "-";
        }
        String normalized = cwd.replace('\\', '/');
        List<String> parts = new ArrayList<>();
        for (String part : normalized.split("/")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        if (keep <= 0 || parts.size() <= keep) {
            return cwd;
        }
        String tail = String.join("/", parts.subList(parts.size() - keep, parts.size()));
        boolean windows = cwd.indexOf('\\') >= 0;
        return windows ? "…\\" + tail.replace('/', '\\') : "…/" + tail;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clamp(String text, int limit) {
        int used = 0;
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int cell = TextWidth.charWidth(codePoint);
            if (used + cell > limit) {
                break;
            }
            out.appendCodePoint(codePoint);
            used += cell;
            index += Character.charCount(codePoint);
        }
        return out.toString();
    }
}
