package com.acanx.module.aha.core.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * 发布页上的一条 release（GitHub API 的<b>我们关心的子集</b>）。
 *
 * <p>{@code published_at} 刻意绑定成 {@code String} 再自行解析：发布页给的是 ISO-8601
 * 瞬时值（如 {@code 2026-10-16T05:43:26Z}），而把它交给 Jackson 的日期模块会引入
 * 「模块是否注册」这类与业务无关的失败面。</p>
 *
 * @param tag          tag 名（如 {@code V0.1.5.00346-aha-desktop-jvm}）
 * @param name         发行名（可能为 {@code null}）
 * @param prerelease   是否预发行
 * @param draft        是否草稿（草稿对用户不可见）
 * @param publishedAtRaw 发布时间（ISO-8601 原文）
 * @param assets       资产列表（可能为 {@code null}）
 * @since 0.2.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReleaseInfo(
        @JsonProperty("tag_name") String tag,
        @JsonProperty("name") String name,
        @JsonProperty("prerelease") boolean prerelease,
        @JsonProperty("draft") boolean draft,
        @JsonProperty("published_at") String publishedAtRaw,
        @JsonProperty("assets") List<AssetInfo> assets) {

    /**
     * 发布时间。
     *
     * @return 时间；字段缺失或格式不认识时返回 {@code null}（界面按「时间未知」显示，不因此报错）
     */
    public Instant publishedAt() {
        if (publishedAtRaw == null || publishedAtRaw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(publishedAtRaw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 资产列表，永不为 {@code null}。
     *
     * @return 不可变列表
     */
    public List<AssetInfo> assetList() {
        return assets == null ? List.of() : List.copyOf(assets);
    }
}
