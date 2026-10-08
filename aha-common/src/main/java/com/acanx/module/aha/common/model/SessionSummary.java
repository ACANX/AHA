package com.acanx.module.aha.common.model;

/**
 * 会话摘要（会话列表用）。
 *
 * <p>会话记录本身只存 ID、创建时间与配置 JSON，标题是「展示层概念」：用户重命名过就用那个名字，
 * 否则取该会话的**首条用户消息**——这比给用户看一串 UUID 有用得多。标题的兜底在这里统一，
 * 于是 CLI 与桌面端不必各写一套「没有标题时显示什么」。</p>
 *
 * @param id           会话 ID
 * @param title        标题；空白时落到 {@link #UNTITLED}
 * @param createdAt    创建时间（毫秒时间戳）
 * @param messageCount 已存储的消息条数
 * @since 0.2.0
 */
public record SessionSummary(String id, String title, long createdAt, int messageCount) {

    /** 会话没有任何可用标题时的占位。 */
    public static final String UNTITLED = "（未命名会话）";

    /**
     * 规范化标题。
     *
     * @param id           会话 ID
     * @param title        标题
     * @param createdAt    创建时间
     * @param messageCount 消息条数
     */
    public SessionSummary {
        title = title == null || title.isBlank() ? UNTITLED : title.strip();
    }

    /**
     * 短 ID：会话列表里用来区分同名会话。
     *
     * @return 前 8 位；ID 为空时返回空串
     */
    public String shortId() {
        if (id == null) {
            return "";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    /**
     * 是否有消息。
     *
     * @return 有消息返回 {@code true}
     */
    public boolean hasMessages() {
        return messageCount > 0;
    }
}
