package com.acanx.module.aha.desktop.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * 输入历史（↑ / ↓，纯逻辑）。
 *
 * <p>口径（{@code GUIDesign.md} 第 4.3 节「↑ / ↓ 历史输入」）：</p>
 * <ul>
 *   <li>空白输入不进历史——否则上下键会在一堆空行里翻；</li>
 *   <li>与上一条相同的内容不重复记（连续发两次同一句不必占两格）；</li>
 *   <li>有上限：历史的用处是「找回刚才那句」，不是无限存档；</li>
 *   <li>翻历史时**不丢草稿**：从第 0 格再往外翻会回到用户正在打的那句。</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class InputHistory {

    /** 保留条数。 */
    public static final int CAPACITY = 100;

    /** 第 0 格是「正在输入的内容」的位置。 */
    private static final int DRAFT = 0;

    private final int capacity;

    /** 下标从小到大即时间由近到远。 */
    private final List<String> entries = new ArrayList<>();

    /** 当前位置；0 = 草稿。 */
    private int cursor;

    /** 开始翻历史前正在打的内容。 */
    private String draft = "";

    /**
     * @param capacity 上限；小于 1 时用 {@link #CAPACITY}
     */
    public InputHistory(int capacity) {
        this.capacity = capacity < 1 ? CAPACITY : capacity;
    }

    /** 用默认容量。 */
    public InputHistory() {
        this(CAPACITY);
    }

    /**
     * 记一条（发送后调用）。
     *
     * @param text 输入内容；空白或与上一条相同则忽略
     */
    public void add(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String value = text.strip();
        if (!entries.isEmpty() && entries.get(0).equals(value)) {
            reset();
            return;
        }
        entries.add(0, value);
        while (entries.size() > capacity) {
            entries.remove(entries.size() - 1);
        }
        reset();
    }

    /**
     * 上一条（↑）。
     *
     * @param currentText 输入框当前内容（用于记住草稿）
     * @return 应当放进输入框的文本
     */
    public String previous(String currentText) {
        if (entries.isEmpty()) {
            return currentText;
        }
        if (cursor == DRAFT) {
            draft = currentText == null ? "" : currentText;
        }
        if (cursor < entries.size()) {
            cursor++;
        }
        return entries.get(cursor - 1);
    }

    /**
     * 下一条（↓）。
     *
     * @return 应当放进输入框的文本；回到草稿时是用户原来打的内容
     */
    public String next() {
        if (cursor > 1) {
            cursor--;
            return entries.get(cursor - 1);
        }
        // 先把草稿取出来再 reset：reset 会清空 draft，顺序反了就会把用户正在打的内容丢掉
        String value = draft;
        reset();
        return value;
    }

    /**
     * 是否正停在草稿位置。
     *
     * @return 是返回 {@code true}
     */
    public boolean atDraft() {
        return cursor == DRAFT;
    }

    /**
     * 历史条数。
     *
     * @return 条数
     */
    public int size() {
        return entries.size();
    }

    /**
     * 全部条目（时间由近到远；测试用）。
     *
     * @return 条目
     */
    public List<String> entries() {
        return List.copyOf(entries);
    }

    /** 回到草稿位置（发送或切换会话后）。 */
    public void reset() {
        cursor = DRAFT;
        draft = "";
    }
}
