package com.acanx.module.aha.cli.render;

import java.util.ArrayList;
import java.util.List;

/**
 * 底部状态栏：**运行模式 → 供应商 → 档位 → 模型 → 思考强度 → 目录[ (分支)]**（issue #330 / #353）。
 *
 * <p>只负责「取值 → 文案 → 按宽度压缩」，不碰终端；绘制仍由 {@link StatusLine} 交给
 * {@link com.acanx.module.aha.cli.tty.StatusSurface}。</p>
 *
 * <p><b>压缩顺序</b>（宽度不足时）：先丢「目录 + 分支」，再丢思考强度，再丢档位，最后按显示宽度硬截断。
 * 模式与供应商始终保留——前者决定审批行为，后者是「连的是哪个后端」的第一信息。</p>
 *
 * <p><b>为什么不含 token / 缓存</b>：那四项依赖 0.6 的用量落库，由 issue #331 单独跟进；
 * 本类只接静态数据，数据源变化不改渲染代码。</p>
 *
 * @since 0.1.5
 */
public final class StatusBar {

    /** 目录默认保留的尾部层数。 */
    static final int KEEP_LEVELS = 3;

    /** 缺失值的占位（供应商 / 模型 / 强度）。 */
    private static final String MISSING = "-";

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
     * 状态栏字段的不可变快照（便于单测与日志）。
     *
     * @param mode     运行模式
     * @param provider 供应商（配置键）
     * @param tier     档位（Ultra / Pro / Standard / Flash / Fallback）
     * @param model    模型名
     * @param effort   思考强度；暂无数据源时为空，展示为 {@code -}
     * @param cwd      工作目录；空表示不展示目录段
     * @param branch   Git 分支；空表示不是仓库或读取失败
     */
    record Context(Mode mode, String provider, String tier, String model, String effort,
                   String cwd, String branch) {
    }

    private volatile Context context;

    /** 兼容模式：只显示一个值（旧构造 / 旧测试使用），不参与多字段渲染。 */
    private final boolean legacy;

    private StatusBar(Context context, boolean legacy) {
        this.context = context;
        this.legacy = legacy;
    }

    /**
     * 创建状态栏。
     *
     * @param mode     运行模式；{@code null} 视为 {@link Mode#AUTO}
     * @param provider 供应商（可空）
     * @param tier     档位（可空）
     * @param model    模型名（可空）
     * @param effort   思考强度（可空）
     * @param cwd      工作目录（空表示不展示）
     * @param branch   Git 分支（可空）
     * @return 状态栏
     */
    public static StatusBar of(Mode mode, String provider, String tier, String model, String effort,
                               String cwd, String branch) {
        return new StatusBar(new Context(mode == null ? Mode.AUTO : mode, clean(provider),
                clean(tier), clean(model), clean(effort), clean(cwd), clean(branch)), false);
    }

    /**
     * 兼容构造：只显示一个值，保持旧行为（`StatusLine` 的旧签名与既有测试）。
     *
     * @param text 要显示的值
     * @return 状态栏
     */
    static StatusBar legacy(String text) {
        return new StatusBar(new Context(Mode.AUTO, "", "", clean(text), "", "", ""), true);
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
        context = new Context(mode, current.provider(), current.tier(), current.model(),
                current.effort(), current.cwd(), current.branch());
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
        // 顺序固定：模式 → 供应商 → 档位 → 模型 → 强度 → 目录[ (分支)]
        String mode = current.mode().label();
        String provider = missingAsDash(current.provider());
        String tier = current.tier();
        String model = missingAsDash(current.model());
        String effort = missingAsDash(current.effort());
        String dir = directory(current.cwd(), current.branch());

        List<List<String>> variants = new ArrayList<>();
        List<String> full = new ArrayList<>();
        full.add(mode);
        full.add(provider);
        int tierIndex = -1;
        if (!tier.isEmpty()) {
            tierIndex = full.size();
            full.add(tier);
        }
        full.add(model);
        int effortIndex = full.size();
        full.add(effort);
        if (!dir.isEmpty()) {
            full.add(dir);
        }
        variants.add(full);

        List<String> noDir = new ArrayList<>(full);
        if (!dir.isEmpty()) {
            noDir.remove(noDir.size() - 1);
        }
        variants.add(noDir);

        List<String> noEffort = new ArrayList<>(noDir);
        noEffort.remove(effortIndex);
        variants.add(noEffort);

        List<String> core = new ArrayList<>(noEffort);
        if (tierIndex >= 0) {
            core.remove(tierIndex);
        }
        variants.add(core);

        for (List<String> variant : variants) {
            String text = String.join(" · ", variant);
            if (width <= 0 || TextWidth.width(text) <= width - 1) {
                return text;
            }
        }
        return clamp(String.join(" · ", core), Math.max(1, width - 1));
    }

    /**
     * 目录段：`缩短后的路径 (分支)`；无目录时为空串。
     *
     * @param cwd    工作目录
     * @param branch Git 分支
     * @return 目录段
     */
    private static String directory(String cwd, String branch) {
        if (cwd.isEmpty()) {
            return "";
        }
        String dir = shorten(cwd, KEEP_LEVELS);
        return branch.isEmpty() ? dir : dir + " (" + branch + ")";
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
            return MISSING;
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

    private static String missingAsDash(String value) {
        return value == null || value.isBlank() ? MISSING : value;
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
