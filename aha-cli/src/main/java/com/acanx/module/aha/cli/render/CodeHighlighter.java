package com.acanx.module.aha.cli.render;

import java.util.Locale;
import java.util.Set;

/**
 * 围栏代码的轻量语法高亮（issue #336）。
 *
 * <p>只做<b>单行</b>内的着色：注释、字符串、数字、关键字。不引入跨行状态机，
 * 因此行数与换行保持不变，不会与表格 / 折叠逻辑互相干扰。</p>
 *
 * <p>未知语言或未标语言时原样返回；无色终端（{@code Style.of(false)}）同样原样返回，
 * 不产生任何 ANSI 序列。</p>
 *
 * @since 0.1.5
 */
final class CodeHighlighter {

    private CodeHighlighter() {
    }

    /**
     * 高亮一行代码。
     *
     * @param line     该行内容（不含换行）
     * @param language 围栏语言标识，可为 {@code null}
     * @param style    样式
     * @return 高亮后的文本；未知语言 / 无色时原样返回
     */
    static String highlight(String line, String language, Style style) {
        if (line == null || line.isEmpty() || style == null || !style.colored()) {
            return line;
        }
        Lang lang = Lang.of(language);
        if (lang == null) {
            return line;
        }
        StringBuilder out = new StringBuilder(line.length() + 16);
        int index = 0;
        while (index < line.length()) {
            String comment = lang.commentAt(line, index);
            if (comment != null) {
                out.append(style.comment(comment));
                break;
            }
            char current = line.charAt(index);
            if (current == '"' || current == '\'' || (lang.backtick && current == '`')) {
                int end = index + 1;
                while (end < line.length() && line.charAt(end) != current) {
                    end++;
                }
                if (end < line.length()) {
                    end++;
                }
                out.append(style.string(line.substring(index, end)));
                index = end;
                continue;
            }
            if (Character.isDigit(current)) {
                int end = index + 1;
                while (end < line.length()
                        && (Character.isDigit(line.charAt(end)) || line.charAt(end) == '.')) {
                    end++;
                }
                out.append(style.number(line.substring(index, end)));
                index = end;
                continue;
            }
            if (Character.isLetter(current) || current == '_' || current == '$') {
                int end = index + 1;
                while (end < line.length()
                        && (Character.isLetterOrDigit(line.charAt(end))
                        || line.charAt(end) == '_' || line.charAt(end) == '$')) {
                    end++;
                }
                String word = line.substring(index, end);
                out.append(lang.keywords.contains(word.toLowerCase(Locale.ROOT))
                        ? style.keyword(word) : word);
                index = end;
                continue;
            }
            out.append(current);
            index++;
        }
        return out.toString();
    }

    /** 支持的语言及其词法信息。 */
    private enum Lang {

        JAVA(Set.of("public", "private", "protected", "class", "interface", "enum", "extends",
                "implements", "import", "package", "static", "final", "void", "int", "long",
                "double", "float", "boolean", "char", "byte", "short", "if", "else", "for",
                "while", "do", "switch", "case", "break", "continue", "return", "new", "try",
                "catch", "finally", "throw", "throws", "this", "super", "null", "true", "false",
                "abstract", "synchronized", "volatile", "transient", "native", "instanceof",
                "record", "var", "yield", "sealed", "permits", "non-sealed"), "//", false),

        PYTHON(Set.of("def", "class", "import", "from", "as", "if", "elif", "else", "for",
                "while", "with", "try", "except", "finally", "raise", "return", "yield",
                "lambda", "pass", "break", "continue", "global", "nonlocal", "and", "or",
                "not", "in", "is", "none", "true", "false", "async", "await", "match", "case"),
                "#", false),

        JAVASCRIPT(Set.of("const", "let", "var", "function", "return", "if", "else", "for",
                "while", "do", "switch", "case", "break", "continue", "class", "extends", "new",
                "this", "super", "import", "export", "default", "async", "await", "try", "catch",
                "finally", "throw", "typeof", "instanceof", "null", "undefined", "true", "false"),
                "//", true),

        TYPESCRIPT(Set.of("const", "let", "var", "function", "return", "if", "else", "for",
                "while", "do", "switch", "case", "break", "continue", "class", "extends", "new",
                "this", "super", "import", "export", "default", "async", "await", "try", "catch",
                "finally", "throw", "typeof", "instanceof", "null", "undefined", "true", "false",
                "interface", "type", "enum", "implements", "namespace", "declare", "readonly",
                "public", "private", "protected"), "//", true),

        JSON(Set.of("true", "false", "null"), null, false),

        YAML(Set.of("true", "false", "null", "yes", "no", "on", "off"), "#", false),

        SHELL(Set.of("if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case",
                "esac", "function", "return", "exit", "echo", "cd", "export", "local",
                "readonly", "set", "unset", "source"), "#", false),

        SQL(Set.of("select", "from", "where", "insert", "into", "values", "update", "set",
                "delete", "join", "left", "right", "inner", "outer", "on", "group", "by",
                "order", "having", "limit", "offset", "create", "table", "alter", "drop",
                "index", "and", "or", "not", "null", "is", "in", "like", "as", "distinct",
                "union", "all"), "--", false),

        XML(Set.of(), "<!--", false);

        private final Set<String> keywords;
        private final String lineComment;
        private final boolean backtick;

        Lang(Set<String> keywords, String lineComment, boolean backtick) {
            this.keywords = keywords;
            this.lineComment = lineComment;
            this.backtick = backtick;
        }

        /**
         * 从当前位置识别行注释。
         *
         * @param line  整行
         * @param index 当前位置
         * @return 注释文本（到行尾）；不是注释时返回 {@code null}
         */
        String commentAt(String line, int index) {
            if (lineComment == null) {
                return null;
            }
            return line.startsWith(lineComment, index) ? line.substring(index) : null;
        }

        /**
         * 解析语言标识。
         *
         * @param name 围栏语言，可为 {@code null}
         * @return 语言；未知时返回 {@code null}
         */
        static Lang of(String name) {
            if (name == null || name.isBlank()) {
                return null;
            }
            return switch (name.trim().toLowerCase(Locale.ROOT)) {
                case "java" -> JAVA;
                case "python", "py" -> PYTHON;
                case "javascript", "js", "jsx" -> JAVASCRIPT;
                case "typescript", "ts", "tsx" -> TYPESCRIPT;
                case "json" -> JSON;
                case "yaml", "yml" -> YAML;
                case "shell", "bash", "sh", "zsh" -> SHELL;
                case "sql" -> SQL;
                case "xml", "html" -> XML;
                default -> null;
            };
        }
    }
}
