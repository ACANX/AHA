package com.acanx.module.aha.desktop.chat;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code @} 文件引用的候选与插入（纯逻辑）。
 *
 * <p>三条边界写在这里而不是散在界面里：</p>
 * <ul>
 *   <li><strong>跳过噪音目录</strong>（{@code .git}、{@code target}、{@code node_modules}…）：
 *       不跳的话候选里全是构建产物，用户永远选不到自己想引用的源文件；</li>
 *   <li><strong>有访问上限</strong>：项目大时不能为了一个下拉框把整棵树走完；</li>
 *   <li><strong>只给相对路径</strong>：绝对路径既长又会把本机目录结构带进对话。</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class FileMentions {

    /** 引用前缀。 */
    public static final char PREFIX = '@';

    /** 候选数量上限。 */
    public static final int MAX_RESULTS = 40;

    /** 遍历的目录 / 文件数量上限（防止在巨型目录上卡住界面）。 */
    public static final int MAX_VISITED = 20_000;

    /** 不参与候选的目录名（构建产物、依赖与版本控制目录）。 */
    public static final Set<String> SKIPPED_DIRS =
            Set.of(".git", ".idea", ".gradle", ".mvn", "target", "build", "out", "dist",
                    "node_modules", ".venv", "__pycache__", ".mypy_cache", ".cache");

    private FileMentions() {
    }

    /**
     * 扫描候选文件。
     *
     * @param root   项目根；为 {@code null} 或不存在时返回空列表
     * @param typed  已输入的前缀（不含 {@value #PREFIX}），可为空
     * @param limit  结果上限；{@code <= 0} 用 {@link #MAX_RESULTS}
     * @return 相对路径（用 {@code /} 分隔），短路径优先
     */
    public static List<String> candidates(Path root, String typed, int limit) {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        int max = limit > 0 ? limit : MAX_RESULTS;
        String needle = typed == null ? "" : typed.strip().toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        int[] visited = {0};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (visited[0] > MAX_VISITED) {
                        return FileVisitResult.TERMINATE;
                    }
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (!dir.equals(root) && (SKIPPED_DIRS.contains(name) || name.startsWith("."))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    visited[0]++;
                    if (visited[0] > MAX_VISITED) {
                        return FileVisitResult.TERMINATE;
                    }
                    String relative = relative(root, file);
                    if (needle.isEmpty() || relative.toLowerCase(Locale.ROOT).contains(needle)) {
                        hits.add(relative);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // 单个文件读不了（权限、被占用）不该让整个候选列表失败
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // 走不动就先返回已经拿到的，界面照常可用
            return List.copyOf(hits);
        }
        hits.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
        return hits.size() > max ? List.copyOf(hits.subList(0, max)) : List.copyOf(hits);
    }

    /**
     * 相对路径（统一用 {@code /}）。
     *
     * @param root 根
     * @param file 文件
     * @return 相对路径
     */
    static String relative(Path root, Path file) {
        String text = root.relativize(file).toString();
        return text.replace('\\', '/');
    }

    /**
     * 待补全的 {@code @} 片段。
     *
     * @param text  输入框全部文本
     * @param caret 光标位置
     * @return 片段（含前缀）；不在 {@code @} 片段里时返回 {@code null}
     */
    public static String token(String text, int caret) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int end = Math.max(0, Math.min(caret, text.length()));
        int start = end;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (c == PREFIX) {
                start--;
                break;
            }
            if (Character.isWhitespace(c) || c == '\n') {
                return null;
            }
            start--;
        }
        if (start >= end || text.charAt(start) != PREFIX) {
            return null;
        }
        return text.substring(start, end);
    }

    /**
     * 把候选插入输入框，替换正在输入的片段。
     *
     * @param text   输入框全部文本
     * @param caret  光标位置
     * @param path   要插入的相对路径
     * @return 新文本与新的光标位置
     */
    public static Insertion insert(String text, int caret, String path) {
        String source = text == null ? "" : text;
        String token = token(source, caret);
        String mention = PREFIX + path + " ";
        if (token == null) {
            int at = Math.max(0, Math.min(caret, source.length()));
            return new Insertion(source.substring(0, at) + mention + source.substring(at),
                    at + mention.length());
        }
        int start = caret - token.length();
        String updated = source.substring(0, start) + mention + source.substring(caret);
        return new Insertion(updated, start + mention.length());
    }

    /**
     * 插入结果。
     *
     * @param text  新文本
     * @param caret 新光标位置
     */
    public record Insertion(String text, int caret) {
    }
}
