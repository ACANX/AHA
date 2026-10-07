package com.acanx.module.aha.cli.session;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

/**
 * 会话内命令补全：输入 {@code /} 后按 Tab 列出候选。
 *
 * <p>解决「只能盲敲」的问题——此前用户没法在按回车之前知道有哪些命令、拼得对不对。</p>
 *
 * <p>候选带 {@code usage} 作为描述，菜单里能直接看到用法。模糊/容错匹配由 JLine 的
 * {@code COMPLETE_MATCHER_TYPO} 选项负责（如 {@code /cmpct} 也能匹配到 {@code /compact}），
 * 因此这里只负责把全部命令交出去。</p>
 *
 * <p>只在「整行以 {@code /} 开头且还没有空格」时给建议：命令名之后的部分是它自己的参数，
 * 由各命令自行解释。</p>
 *
 * @since 0.1.0
 */
public final class SessionCommandCompleter implements Completer {

    private final SessionCommandRegistry registry;

    /**
     * 创建补全器。
     *
     * @param registry 命令注册表
     */
    public SessionCommandCompleter(SessionCommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        if (line == null) {
            return;
        }
        String buffer = line.line();
        if (buffer == null || !buffer.startsWith("/") || buffer.contains(" ")) {
            return;
        }
        for (SessionCommand command : registry.commands()) {
            String value = "/" + command.name();
            candidates.add(new Candidate(value, value, null, command.description(), null, null,
                    true));
        }
    }
}
