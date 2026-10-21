package com.acanx.module.aha.core.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 轻量代码词法分析（issue #420 / tracking #408 S2.2）。
 *
 * <p>从 CLI 的 {@code CodeHighlighter}（issue #336）提炼而来：只做<b>单行</b>内的切分，
 * 不引入跨行状态机，因此行数与换行保持不变，不会与表格 / 折叠逻辑互相干扰。</p>
 *
 * <p>输出是 {@link CodeToken} 列表而不是着色字符串，CLI 与桌面端因此共用同一份词法规则：
 * CLI 按 {@code Kind} 选 ANSI 颜色，桌面端按 {@code Kind} 选 JavaFX 样式。
 * 未知语言返回整行一个 {@link CodeToken.Kind#PLAIN}，调用方据此退回单色。</p>
 *
 * @since 0.2.0
 */
public final class CodeLexer {

    private CodeLexer() {
    }

    /**
     * 语言是否受支持。
     *
     * @param language 围栏语言标识，可为 {@code null}
     * @return 支持返回 {@code true}
     */
    public static boolean isSupported(String language) {
        return Lang.of(language) != null;
    }

    /**
     * 切分一行代码。
     *
     * <p>所有记号按原顺序拼接后与输入行逐字节一致（注释到行尾，其余保留原文）。</p>
     *
     * @param line     该行内容（不含换行）
     * @param language 围栏语言标识，可为 {@code null}
     * @return 记号列表；空行返回空列表，未知语言返回整行一个 {@code PLAIN}
     */
    public static List<CodeToken> tokenize(String line, String language) {
        if (line == null || line.isEmpty()) {
            return List.of();
        }
        Lang lang = Lang.of(language);
        if (lang == null) {
            return List.of(new CodeToken(CodeToken.Kind.PLAIN, line));
        }
        List<CodeToken> tokens = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int index = 0;
        while (index < line.length()) {
            String comment = lang.commentAt(line, index);
            if (comment != null) {
                flush(tokens, plain);
                tokens.add(new CodeToken(CodeToken.Kind.COMMENT, comment));
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
                flush(tokens, plain);
                tokens.add(new CodeToken(CodeToken.Kind.STRING, line.substring(index, end)));
                index = end;
                continue;
            }
            if (Character.isDigit(current)) {
                int end = index + 1;
                while (end < line.length()
                        && (Character.isDigit(line.charAt(end)) || line.charAt(end) == '.')) {
                    end++;
                }
                flush(tokens, plain);
                tokens.add(new CodeToken(CodeToken.Kind.NUMBER, line.substring(index, end)));
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
                if (lang.keywords.contains(word.toLowerCase(Locale.ROOT))) {
                    flush(tokens, plain);
                    tokens.add(new CodeToken(CodeToken.Kind.KEYWORD, word));
                } else {
                    plain.append(word);
                }
                index = end;
                continue;
            }
            plain.append(current);
            index++;
        }
        flush(tokens, plain);
        return tokens;
    }

    private static void flush(List<CodeToken> tokens, StringBuilder plain) {
        if (plain.length() > 0) {
            tokens.add(new CodeToken(CodeToken.Kind.PLAIN, plain.toString()));
            plain.setLength(0);
        }
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

        XML(Set.of(), "<!--", false),

        GO(Set.of("package", "import", "func", "var", "const", "type", "struct", "interface",
                "map", "chan", "go", "defer", "select", "case", "switch", "for", "if", "else",
                "return", "range", "break", "continue", "fallthrough", "nil", "true", "false"),
                "//", false),

        RUST(Set.of("fn", "let", "mut", "const", "static", "struct", "enum", "impl", "trait",
                "pub", "use", "mod", "match", "if", "else", "for", "while", "loop", "break",
                "continue", "return", "self", "super", "crate", "as", "where", "dyn", "async",
                "await", "move", "ref", "type", "unsafe", "extern", "true", "false"),
                "//", false),

        CSHARP(Set.of("using", "namespace", "class", "interface", "struct", "enum", "public",
                "private", "protected", "internal", "static", "readonly", "const", "void", "int",
                "string", "bool", "var", "new", "return", "if", "else", "for", "foreach", "while",
                "do", "switch", "case", "break", "continue", "try", "catch", "finally", "throw",
                "async", "await", "null", "true", "false"), "//", false),

        C(Set.of("include", "define", "int", "void", "char", "float", "double", "struct",
                "union", "enum", "typedef", "static", "const", "return", "if", "else", "for",
                "while", "do", "switch", "case", "break", "continue", "goto", "sizeof"),
                "//", false),

        CPP(Set.of("include", "define", "int", "void", "char", "float", "double", "struct",
                "union", "enum", "typedef", "static", "const", "return", "if", "else", "for",
                "while", "do", "switch", "case", "break", "continue", "goto", "sizeof", "class",
                "namespace", "template", "public", "private", "protected", "virtual", "override",
                "new", "delete", "this", "nullptr", "true", "false", "using", "std", "auto"),
                "//", false),

        PHP(Set.of("function", "class", "interface", "extends", "implements", "public",
                "private", "protected", "static", "const", "return", "if", "else", "elseif",
                "for", "foreach", "while", "do", "switch", "case", "break", "continue", "echo",
                "print", "new", "try", "catch", "finally", "throw", "null", "true", "false",
                "namespace", "use", "array", "isset", "empty"), "//", false),

        RUBY(Set.of("def", "class", "module", "end", "if", "elsif", "else", "unless", "while",
                "until", "for", "do", "begin", "rescue", "ensure", "return", "yield", "puts",
                "require", "include", "extend", "attr_reader", "attr_writer", "attr_accessor",
                "nil", "true", "false", "self"), "#", false);

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
            // 围栏可能带附加信息（如 `java title="x"`），只取第一个 token
            String token = name.trim().split("\\s+", 2)[0];
            return switch (token.toLowerCase(Locale.ROOT)) {
                case "java" -> JAVA;
                case "python", "py" -> PYTHON;
                case "javascript", "js", "jsx" -> JAVASCRIPT;
                case "typescript", "ts", "tsx" -> TYPESCRIPT;
                case "json" -> JSON;
                case "yaml", "yml" -> YAML;
                case "shell", "bash", "sh", "zsh" -> SHELL;
                case "sql" -> SQL;
                case "xml", "html" -> XML;
                case "go", "golang" -> GO;
                case "rust", "rs" -> RUST;
                case "csharp", "cs", "c#" -> CSHARP;
                case "c" -> C;
                case "cpp", "c++", "cc", "cxx" -> CPP;
                case "php" -> PHP;
                case "ruby", "rb" -> RUBY;
                default -> null;
            };
        }
    }
}
