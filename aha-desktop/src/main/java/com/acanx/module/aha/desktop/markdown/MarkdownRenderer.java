package com.acanx.module.aha.desktop.markdown;

import com.acanx.module.aha.desktop.view.Palette;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 把 {@link MarkdownBlock} / {@link MarkdownInline} 渲染成 JavaFX 节点（issue #415 / #416 / #417，
 * tracking #408）。
 *
 * <p>只用 {@code javafx-controls} 现成节点（{@link TextFlow} / {@link GridPane} / {@link VBox}），
 * 不引入 WebView 或第三方富文本库——原生镜像体积与构建成本是既有决策（见 {@code LogoImage}）。</p>
 *
 * <p>配色通过 {@code StyleApplier} 注入（生产环境是 {@code DesktopShell#themed}），
 * 因此主题切换时 {@code -fx-*} 字符串会被重新求值，富文本跟着换色，不需要重建节点树。</p>
 *
 * @since 0.2.0
 */
public final class MarkdownRenderer {

    /** 代码 / 引用等块的最大高度，超过后滚动。 */
    private static final double MAX_BLOCK_HEIGHT = 320;

    /** 每层列表缩进。 */
    private static final double LIST_INDENT = 14;

    /**
     * 样式登记器：与 {@code DesktopShell#themed} 同签名，主题切换时重新求值。
     */
    @FunctionalInterface
    public interface StyleApplier {

        /**
         * 登记一个节点的样式。
         *
         * @param node  节点
         * @param style 样式字符串（延迟求值）
         */
        void apply(Node node, Supplier<String> style);
    }

    private final StyleApplier styler;

    private final Consumer<String> onCopy;

    private final MarkdownParser blockParser = new MarkdownParser();

    private final MarkdownInlineParser inlineParser = new MarkdownInlineParser();

    /**
     * @param styler 样式登记器（主题切换时重刷）
     * @param onCopy 复制回调（代码块复制按钮、链接兜底）
     */
    public MarkdownRenderer(StyleApplier styler, Consumer<String> onCopy) {
        this.styler = styler;
        this.onCopy = onCopy;
    }

    /**
     * 渲染一段 Markdown 全文。
     *
     * @param markdown 原始 Markdown
     * @return 根节点
     */
    public Node render(String markdown) {
        return render(blockParser.parse(markdown));
    }

    /**
     * 渲染块列表。
     *
     * @param blocks 块列表
     * @return 根节点
     */
    public Node render(List<MarkdownBlock> blocks) {
        VBox root = new VBox(6);
        root.setFillWidth(true);
        for (MarkdownBlock block : blocks) {
            root.getChildren().add(renderBlock(block));
        }
        return root;
    }

    private Node renderBlock(MarkdownBlock block) {
        return switch (block) {
            case MarkdownBlock.Heading heading -> heading(heading);
            case MarkdownBlock.Paragraph paragraph -> inlineFlow(paragraph.text());
            case MarkdownBlock.Rule ignored -> rule();
            case MarkdownBlock.Fence fence -> fence(fence);
            case MarkdownBlock.ListBlock list -> list(list);
            case MarkdownBlock.Quote quote -> quote(quote);
            case MarkdownBlock.TableBlock table -> table(table);
        };
    }

    // ------------------------------------------------------------------ 块级

    private Node heading(MarkdownBlock.Heading heading) {
        double size = switch (heading.level()) {
            case 1 -> 19;
            case 2 -> 17;
            case 3 -> 16;
            default -> 15;
        };
        TextFlow flow = new TextFlow();
        for (MarkdownInline inline : inlineParser.parse(heading.text())) {
            flow.getChildren().add(inlineNode(inline, "-fx-font-weight: bold; -fx-font-size: "
                    + size + "px;"));
        }
        return flow;
    }

    private Node rule() {
        Separator separator = new Separator();
        separator.setMaxWidth(Double.MAX_VALUE);
        styler.apply(separator, () -> "-fx-background-color: " + Palette.BORDER + ";");
        return separator;
    }

    private Node fence(MarkdownBlock.Fence fence) {
        VBox box = new VBox(4);
        HBox head = new HBox(8);
        head.setPadding(new Insets(0, 0, 0, 4));
        Label language = new Label(fence.language().isEmpty() ? "text" : fence.language());
        styler.apply(language, () -> "-fx-text-fill: " + Palette.MUTED + "; -fx-font-size: 11px;");
        Hyperlink copy = new Hyperlink("复制");
        copy.setId("aha.markdown.copy");
        copy.setOnAction(event -> onCopy.accept(fence.content()));
        styler.apply(copy, () -> "-fx-text-fill: " + Palette.READ + "; -fx-font-size: 11px;");
        head.getChildren().addAll(language, copy);

        Label code = new Label(fence.content());
        code.setWrapText(false);
        styler.apply(code, () -> "-fx-font-family: monospace; -fx-text-fill: " + Palette.FOREGROUND
                + "; -fx-background-color: " + Palette.BLOCK_BACKGROUND + "; -fx-padding: 6;");

        ScrollPane scroll = new ScrollPane(code);
        scroll.setFitToHeight(false);
        scroll.setFitToWidth(false);
        scroll.setPrefHeight(Math.min(MAX_BLOCK_HEIGHT, 24 + fence.content().lines().count() * 18));
        styler.apply(scroll, () -> "-fx-background: transparent; -fx-background-color: transparent;");

        box.getChildren().addAll(head, scroll);
        return box;
    }

    private Node list(MarkdownBlock.ListBlock list) {
        VBox box = new VBox(2);
        int number = list.start();
        for (MarkdownBlock.ListItem item : list.items()) {
            String marker = list.ordered() ? (number++) + "." : "•";
            Label bullet = new Label(marker);
            styler.apply(bullet, () -> "-fx-text-fill: " + Palette.MUTED + ";");
            Node text = inlineFlow(item.text());
            HBox row = new HBox(6, bullet, text);
            row.setPadding(new Insets(0, 0, 0, item.indent() * LIST_INDENT));
            box.getChildren().add(row);
        }
        return box;
    }

    private Node quote(MarkdownBlock.Quote quote) {
        Region bar = new Region();
        bar.setMinWidth(2);
        bar.setPrefWidth(2);
        styler.apply(bar, () -> "-fx-background-color: " + Palette.BORDER + ";");

        VBox content = new VBox(4);
        for (MarkdownBlock block : quote.blocks()) {
            Node node = renderBlock(block);
            if (node instanceof TextFlow flow) {
                // 引用内的文本整体弱化：TextFlow 自身不支持文字色，逐段套样式
                flow.getChildren().forEach(child -> styler.apply(child,
                        () -> "-fx-fill: " + Palette.MUTED + ";"));
            }
            content.getChildren().add(node);
        }
        HBox box = new HBox(8, bar, content);
        HBox.setHgrow(content, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    private Node table(MarkdownBlock.TableBlock table) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(2);
        grid.setPadding(new Insets(2));

        for (int column = 0; column < table.header().size(); column++) {
            Label cell = new Label(table.header().get(column));
            styler.apply(cell, () -> "-fx-text-fill: " + Palette.FOREGROUND + "; -fx-font-weight: bold;");
            GridPane.setHalignment(cell, alignment(table.aligns(), column));
            grid.add(cell, column, 0);
        }
        for (int row = 0; row < table.rows().size(); row++) {
            List<String> cells = table.rows().get(row);
            for (int column = 0; column < cells.size(); column++) {
                Label cell = new Label(cells.get(column));
                styler.apply(cell, () -> "-fx-text-fill: " + Palette.FOREGROUND + ";");
                GridPane.setHalignment(cell, alignment(table.aligns(), column));
                grid.add(cell, column, row + 1);
            }
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToHeight(false);
        scroll.setFitToWidth(false);
        scroll.setPrefHeight(Math.min(MAX_BLOCK_HEIGHT,
                24 + table.rows().size() * 22));
        styler.apply(scroll, () -> "-fx-background: transparent; -fx-background-color: transparent;");
        return scroll;
    }

    private static javafx.geometry.HPos alignment(List<MarkdownBlock.Align> aligns, int column) {
        MarkdownBlock.Align align = column < aligns.size() ? aligns.get(column) : MarkdownBlock.Align.LEFT;
        return switch (align) {
            case LEFT -> javafx.geometry.HPos.LEFT;
            case CENTER -> javafx.geometry.HPos.CENTER;
            case RIGHT -> javafx.geometry.HPos.RIGHT;
        };
    }

    // ------------------------------------------------------------------ 行内

    private TextFlow inlineFlow(String text) {
        TextFlow flow = new TextFlow();
        for (MarkdownInline inline : inlineParser.parse(text)) {
            flow.getChildren().add(inlineNode(inline, ""));
        }
        return flow;
    }

    /**
     * 把一个行内元素变成节点。
     *
     * @param inline 行内元素
     * @param extra  追加样式（标题字号、引用弱化等）
     * @return 节点
     */
    private Node inlineNode(MarkdownInline inline, String extra) {
        return switch (inline) {
            case MarkdownInline.Text text -> styledText(text.text(), extra, "");
            case MarkdownInline.Code code -> styledText(code.text(), extra, "-fx-font-family: monospace;");
            case MarkdownInline.Strong strong -> styledText(plain(strong.children()),
                    extra, "-fx-font-weight: bold;");
            case MarkdownInline.Em em -> styledText(plain(em.children()), extra, "-fx-font-style: italic;");
            case MarkdownInline.Strike strike -> styledText(plain(strike.children()),
                    extra, "-fx-strikethrough: true;");
            case MarkdownInline.Link link -> link(link, extra);
        };
    }

    private Text styledText(String content, String extra, String own) {
        Text text = new Text(content);
        styler.apply(text, () -> "-fx-fill: " + Palette.FOREGROUND + "; " + own + extra);
        return text;
    }

    private Hyperlink link(MarkdownInline.Link link, String extra) {
        Hyperlink hyperlink = new Hyperlink(link.text());
        hyperlink.setPadding(Insets.EMPTY);
        hyperlink.setOnAction(event -> onCopy.accept(link.url()));
        styler.apply(hyperlink, () -> "-fx-text-fill: " + Palette.READ + "; " + extra);
        return hyperlink;
    }

    /** 嵌套行内元素拍平成文本（S1.2 的渲染只保留最外层样式）。 */
    private static String plain(List<MarkdownInline> inlines) {
        StringBuilder text = new StringBuilder();
        for (MarkdownInline inline : inlines) {
            switch (inline) {
                case MarkdownInline.Text value -> text.append(value.text());
                case MarkdownInline.Code value -> text.append(value.text());
                case MarkdownInline.Strong value -> text.append(plain(value.children()));
                case MarkdownInline.Em value -> text.append(plain(value.children()));
                case MarkdownInline.Strike value -> text.append(plain(value.children()));
                case MarkdownInline.Link value -> text.append(value.text());
            }
        }
        return text.toString();
    }
}
