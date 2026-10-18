package com.acanx.module.aha.cli.session;

import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.LineReader;
import org.jline.reader.Reference;
import org.jline.reader.Widget;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 带「输入行下方候选面板」的 LineReader（issue #333）。
 *
 * <p>JLine 自带的候选菜单画在<b>输入行上方</b>，选中项用反色渲染（部分终端主题下是红底），
 * 且输入变化后菜单即关闭。这里改为：</p>
 *
 * <ul>
 *   <li>候选画在<b>输入行下方</b>，<b>一行一个</b>命令；</li>
 *   <li>选中项用 {@code ❯} + 青色加粗标记，其余用绿色；</li>
 *   <li>键入字符时按前缀<b>实时过滤</b>，选中项回到第一个；</li>
 *   <li>↑ / ↓ 移动选中，Tab 把选中项补进输入行，Enter 直接提交该命令，Esc 收起面板。</li>
 * </ul>
 *
 * <p>实现方式是覆盖 {@link #getDisplayedBufferWithPrompts(List)}：JLine 用它的返回值作为
 * 「整个显示内容」，在末尾追加候选行即可显示在输入行下方，且随重绘自动清除旧内容。</p>
 *
 * @since 0.1.5
 */
public final class SlashMenuReader extends LineReaderImpl {

    /** 面板最多同时显示多少行，超出时随选中项滚动。 */
    private static final int MAX_VISIBLE = 6;

    private static final AttributedStyle SELECTED =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold();

    private static final AttributedStyle NORMAL =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);

    private final SessionCommandRegistry registry;

    /** 当前可见候选（命令名，不含前导 {@code /}）。 */
    private List<String> visible = List.of();

    /** 选中项下标；无候选时为 {@code -1}。 */
    private int selected = -1;

    /**
     * 创建 reader。
     *
     * @param terminal 终端
     * @param registry 命令注册表
     * @param colored  终端是否支持颜色
     * @throws IOException 初始化失败
     */
    public SlashMenuReader(Terminal terminal, SessionCommandRegistry registry, boolean colored)
            throws IOException {
        super(terminal, "aha");
        this.registry = registry;
        setCompleter(new SessionCommandCompleter(registry));
        setHighlighter(new SessionCommandHighlighter(registry, colored));
        setOpt(LineReader.Option.BRACKETED_PASTE);
        setOpt(LineReader.Option.LIST_PACKED);
        setOpt(LineReader.Option.COMPLETE_MATCHER_TYPO);
        // 候选由本类自己画，关掉 JLine 的菜单/列表，避免出现两套候选
        unsetOpt(LineReader.Option.AUTO_LIST);
        unsetOpt(LineReader.Option.AUTO_MENU);
        unsetOpt(LineReader.Option.AUTO_MENU_LIST);
        install();
    }

    @Override
    public AttributedString getDisplayedBufferWithPrompts(List<AttributedString> lines) {
        AttributedString base = super.getDisplayedBufferWithPrompts(lines);
        if (visible.isEmpty()) {
            return base;
        }
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.append(base);
        int shown = Math.min(visible.size(), MAX_VISIBLE);
        int start = Math.max(0, Math.min(selected - shown + 1, visible.size() - shown));
        for (int i = 0; i < shown; i++) {
            int index = start + i;
            boolean active = index == selected;
            AttributedStyle style = active ? SELECTED : NORMAL;
            sb.append("\n");
            sb.append(new AttributedString((active ? "❯ " : "  ") + "/" + visible.get(index), style));
        }
        return sb.toAttributedString();
    }

    /**
     * 按当前缓冲区重新计算候选。
     */
    private void refresh() {
        String buffer = getBuffer().toString();
        if (!buffer.startsWith("/") || buffer.indexOf(' ') >= 0 || buffer.indexOf('\n') >= 0) {
            visible = List.of();
            selected = -1;
            return;
        }
        String prefix = buffer.substring(1).toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (SessionCommand command : registry.commands()) {
            if (command.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(command.name());
            }
        }
        visible = List.copyOf(matches);
        selected = matches.isEmpty() ? -1 : 0;
    }

    /**
     * 安装自定义 widget 与按键绑定。
     *
     * <p>方向键 / Enter / Backspace 直接<b>覆盖同名 widget</b>（而非绑定具体转义序列）：
     * 不同终端的方向键序列不一样，按 widget 名覆盖才能全部命中。候选不可见时一律转交原实现，
     * 保证普通输入行为完全不变。</p>
     */
    private void install() {
        Widget originalUp = getWidgets().get(LineReader.UP_LINE_OR_HISTORY);
        Widget originalDown = getWidgets().get(LineReader.DOWN_LINE_OR_HISTORY);
        Widget originalAccept = getWidgets().get(LineReader.ACCEPT_LINE);
        Widget originalBackspace = getWidgets().get(LineReader.BACKWARD_DELETE_CHAR);

        getWidgets().put("aha-slash-open", () -> {
            getBuffer().write("/");
            refresh();
            return true;
        });
        getWidgets().put("aha-slash-insert", () -> {
            getBuffer().write(getLastBinding());
            refresh();
            return true;
        });
        getWidgets().put("aha-slash-tab", () -> {
            if (visible.isEmpty() || selected < 0) {
                return true;
            }
            getBuffer().clear();
            getBuffer().write("/" + visible.get(selected));
            refresh();
            return true;
        });
        getWidgets().put("aha-slash-escape", () -> {
            if (visible.isEmpty()) {
                return false;
            }
            visible = List.of();
            selected = -1;
            return true;
        });

        getWidgets().put(LineReader.UP_LINE_OR_HISTORY, () -> {
            if (visible.isEmpty()) {
                return originalUp != null && originalUp.apply();
            }
            if (selected > 0) {
                selected--;
            }
            return true;
        });
        getWidgets().put(LineReader.DOWN_LINE_OR_HISTORY, () -> {
            if (visible.isEmpty()) {
                return originalDown != null && originalDown.apply();
            }
            if (selected < visible.size() - 1) {
                selected++;
            }
            return true;
        });
        getWidgets().put(LineReader.ACCEPT_LINE, () -> {
            if (visible.isEmpty() || selected < 0) {
                return originalAccept != null && originalAccept.apply();
            }
            getBuffer().clear();
            getBuffer().write("/" + visible.get(selected));
            visible = List.of();
            selected = -1;
            return originalAccept != null && originalAccept.apply();
        });
        getWidgets().put(LineReader.BACKWARD_DELETE_CHAR, () -> {
            boolean handled = originalBackspace != null && originalBackspace.apply();
            refresh();
            return handled;
        });

        KeyMap<Binding> main = getKeyMaps().get(LineReader.MAIN);
        main.bind(new Reference("aha-slash-open"), "/");
        main.bind(new Reference("aha-slash-tab"), "\t");
        main.bind(new Reference("aha-slash-escape"), "\u001b");
        Reference insert = new Reference("aha-slash-insert");
        for (char c = 'a'; c <= 'z'; c++) {
            main.bind(insert, String.valueOf(c));
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            main.bind(insert, String.valueOf(c));
        }
        for (char c = '0'; c <= '9'; c++) {
            main.bind(insert, String.valueOf(c));
        }
        main.bind(insert, "-");
    }
}
