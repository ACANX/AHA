package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.common.model.SessionSummary;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话列表的展示口径（纯函数）。
 *
 * <p>两件事必须在这里定死，否则界面与测试会各说各话：**标题怎么截**、**时间怎么说**。
 * 时间一律用相对说法（「3 分钟前」），超过 7 天才落到绝对日期——会话列表里
 * 「2026-10-08 14:03」这种精确到分钟的时间戳对选择毫无帮助。</p>
 *
 * @since 0.2.0
 */
public final class SessionList {

    /** 标题在列表里的显示宽度上限（超出加省略号）。 */
    public static final int TITLE_MAX = 18;

    /** 超过这个天数就显示绝对日期。 */
    public static final int RELATIVE_DAYS = 7;

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private SessionList() {
    }

    /**
     * 会话列表的一行。
     *
     * @param id       会话 ID
     * @param title    标题（已按 {@link #TITLE_MAX} 截断）
     * @param meta     右侧元信息（消息条数 · 相对时间）
     * @param tooltip  悬停提示（完整标题 + 完整 ID + 完整时间）
     */
    public record Item(String id, String title, String meta, String tooltip) {

        /** 完整行文本（列表与测试共用）。 */
        public String line() {
            return title + "　" + meta;
        }
    }

    /**
     * 生成一行。
     *
     * @param summary   会话摘要
     * @param nowMillis 当前时间（毫秒）；注入以便相对时间可确定性断言
     * @return 列表行
     */
    public static Item item(SessionSummary summary, long nowMillis) {
        String full = summary.title();
        String title = trim(full);
        String meta = meta(summary.messageCount(), summary.createdAt(), nowMillis);
        String tooltip = full + "\n" + summary.id() + "\n创建于 "
                + DATE.format(Instant.ofEpochMilli(summary.createdAt()));
        return new Item(summary.id(), title, meta, tooltip);
    }

    /**
     * 元信息：消息条数 + 相对时间。
     *
     * @param messageCount 消息条数
     * @param createdAt    创建时间
     * @param nowMillis    当前时间
     * @return 文本
     */
    public static String meta(int messageCount, long createdAt, long nowMillis) {
        return messageCount + " 条 · " + relativeTime(createdAt, nowMillis);
    }

    /**
     * 相对时间。
     *
     * @param createdAt 时间点
     * @param nowMillis 当前时间
     * @return 「刚刚」/「N 分钟前」/「N 小时前」/「昨天」/「N 天前」/绝对日期
     */
    public static String relativeTime(long createdAt, long nowMillis) {
        long delta = nowMillis - createdAt;
        if (delta < 0) {
            // 时钟回拨或跨机器导出：说「刚刚」比说「-3 分钟前」靠谱
            return "刚刚";
        }
        long minutes = delta / 60_000;
        if (minutes < 1) {
            return "刚刚";
        }
        if (minutes < 60) {
            return minutes + " 分钟前";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " 小时前";
        }
        long days = hours / 24;
        if (days == 1) {
            return "昨天";
        }
        if (days < RELATIVE_DAYS) {
            return days + " 天前";
        }
        return DATE.format(Instant.ofEpochMilli(createdAt));
    }

    /**
     * 列表标题：超长截断。
     *
     * @param title 标题
     * @return 截断后的标题
     */
    public static String trim(String title) {
        if (title == null || title.isBlank()) {
            return SessionSummary.UNTITLED;
        }
        String flat = title.replace('\n', ' ').replace('\r', ' ').strip();
        return flat.length() <= TITLE_MAX ? flat : flat.substring(0, TITLE_MAX - 1) + "…";
    }

    /**
     * 按关键字过滤会话（标题或 ID 命中）。
     *
     * @param sessions 会话摘要
     * @param query    关键字，空白表示不过滤
     * @return 过滤后的列表（保持原顺序）
     */
    public static List<SessionSummary> filter(List<SessionSummary> sessions, String query) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return List.copyOf(sessions);
        }
        String needle = query.strip().toLowerCase(java.util.Locale.ROOT);
        List<SessionSummary> hits = new ArrayList<>();
        for (SessionSummary summary : sessions) {
            String title = summary.title() == null ? "" : summary.title().toLowerCase(java.util.Locale.ROOT);
            String id = summary.id() == null ? "" : summary.id().toLowerCase(java.util.Locale.ROOT);
            if (title.contains(needle) || id.contains(needle)) {
                hits.add(summary);
            }
        }
        return hits;
    }
}
