package com.acanx.module.aha.desktop.markdown;

import com.acanx.module.aha.desktop.markdown.MarkdownTask.Task;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MarkdownTask} 测试（issue #421）。
 *
 * @since 0.2.0
 */
class MarkdownTaskTest {

    @Test
    void uncheckedTask() {
        assertThat(MarkdownTask.of("[ ] 待办")).isEqualTo(new Task(false, "待办"));
        assertThat(MarkdownTask.of("[ ]")).isEqualTo(new Task(false, ""));
    }

    @Test
    void checkedTaskAcceptsLowerAndUpperCase() {
        assertThat(MarkdownTask.of("[x] 完成")).isEqualTo(new Task(true, "完成"));
        assertThat(MarkdownTask.of("[X] 也完成")).isEqualTo(new Task(true, "也完成"));
    }

    @Test
    void otherMarksAreNotTasks() {
        assertThat(MarkdownTask.of("[y] 不算")).isNull();
        assertThat(MarkdownTask.of("[] 空标记")).isNull();
        assertThat(MarkdownTask.of("普通列表项")).isNull();
        assertThat(MarkdownTask.of("* [ ] 带粗体标记")).isNull();
        assertThat(MarkdownTask.of(null)).isNull();
    }

    @Test
    void squareBracketWithoutSpaceStillWorks() {
        assertThat(MarkdownTask.of("[x]无空格")).isEqualTo(new Task(true, "无空格"));
    }
}
