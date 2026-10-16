package com.acanx.module.aha.core.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 发布页上的一条资产（GitHub release asset 的<b>我们关心的子集</b>）。
 *
 * <p>刻意只绑定需要的字段，并忽略未知字段：GitHub 会不断给响应加字段，
 * 而「多了一个字段就解析失败」是最没必要的一类故障。</p>
 *
 * @param name        资产名（如 {@code aha-desktop-0.1.3.00242-windows-amd64.zip}）
 * @param size        字节数
 * @param downloadUrl 下载地址（{@code browser_download_url}）
 * @param state       上传状态（{@code uploaded} 才可用）
 * @since 0.2.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetInfo(
        @JsonProperty("name") String name,
        @JsonProperty("size") long size,
        @JsonProperty("browser_download_url") String downloadUrl,
        @JsonProperty("state") String state) {

    /**
     * 是否已上传完成。
     *
     * <p>状态字段缺失时按「可用」处理：老响应里没有这个字段，不该因此把包全判为不可用。</p>
     *
     * @return 可用返回 {@code true}
     */
    public boolean uploaded() {
        return state == null || "uploaded".equals(state);
    }
}
