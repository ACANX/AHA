package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.AppVersion;
import com.acanx.module.aha.common.exception.UpdateException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 发布页客户端：读 GitHub Releases 列表（issue #51 §4.1）。
 *
 * <p>只用 JDK 自带的 {@link HttpClient}，不引入任何 HTTP 依赖；也不需要任何凭据 ——
 * 未认证请求的限额（60 次/时/IP）对「点一次检查更新发 1–2 个请求」远远够用，
 * 所以本类<b>不接受也不要</b> token。</p>
 *
 * <p><b>刻意不做重试</b>：清单查询失败时，重试只会放大限流（这正是 issue #51 §4.1
 * 「命中限流时明确提示，不做重试风暴」那条口径）。下载（P1-4）才需要重试，且只对 5xx 与网络中断重试。</p>
 *
 * <p>所有失败都抛 {@link UpdateException}，并用错误码把「限流 / 网络 / HTTP / 内容」分开，
 * 让界面能给出不同的下一步动作，而不是统一一句「更新失败」。</p>
 *
 * @since 0.2.0
 */
public final class GitHubReleaseClient {

    /** 默认仓库：本项目的发布页固定是它，不做可配置项（避免把资产来源变成用户可改的东西）。 */
    public static final String DEFAULT_REPOSITORY = "ACANX/AHA";

    /** 默认 API 基址。 */
    public static final URI DEFAULT_API_BASE = URI.create("https://api.github.com");

    /** 单页条数上限（GitHub 的上限就是 100）。 */
    public static final int DEFAULT_PER_PAGE = 100;

    /** 最多翻几页：200 条足够覆盖「三通道里最新的那些版本」。 */
    public static final int MAX_PAGES = 2;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final String API_VERSION = "2022-11-28";

    private final HttpClient http;

    private final ObjectMapper json;

    private final URI apiBase;

    private final String repository;

    private final int perPage;

    /** 默认构造：真实的 GitHub 发布页。 */
    public GitHubReleaseClient() {
        this(DEFAULT_API_BASE, DEFAULT_REPOSITORY, DEFAULT_PER_PAGE,
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                JsonMapper.builder().build());
    }

    /**
     * 可注入构造（测试用本机 mock 服务器；也可指向企业镜像）。
     *
     * @param apiBase    API 基址
     * @param repository 仓库（{@code owner/name}）
     * @param perPage    单页条数
     * @param http       HTTP 客户端
     * @param json       JSON 映射器
     */
    GitHubReleaseClient(URI apiBase, String repository, int perPage, HttpClient http,
                        ObjectMapper json) {
        this.apiBase = apiBase;
        this.repository = repository;
        this.perPage = perPage;
        this.http = http;
        this.json = json;
    }

    /**
     * 列出发布页上的 release（最新的在前，按 GitHub 的返回顺序）。
     *
     * <p>翻页到「不满一页」或到 {@link #MAX_PAGES} 为止；不区分通道 —— 通道过滤是
     * {@link ReleaseFilter} 的职责（同一份响应要供三个通道各自使用，所以只取一次）。</p>
     *
     * @return release 列表（不可变）
     * @throws UpdateException 限流 / 网络 / HTTP / 内容异常
     */
    public List<ReleaseInfo> listReleases() {
        List<ReleaseInfo> collected = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            List<ReleaseInfo> batch = fetchPage(page);
            collected.addAll(batch);
            if (batch.size() < perPage) {
                break;
            }
        }
        return List.copyOf(collected);
    }

    /**
     * 取一页 release。
     *
     * @param page 页码（从 1 开始）
     * @return 该页的 release
     */
    private List<ReleaseInfo> fetchPage(int page) {
        HttpRequest request = HttpRequest.newBuilder(releasesUri(page))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", "AHA/" + AppVersion.buildVersion())
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_NETWORK_ERROR,
                    "访问发布页失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpdateException(UpdateException.CODE_NETWORK_ERROR,
                    "访问发布页被中断", e);
        }
        int status = response.statusCode();
        if (status == 403 || status == 429) {
            throw new UpdateException(UpdateException.CODE_RATE_LIMITED,
                    "发布页查询被限流（HTTP " + status + "），请稍后再试" + resetHint(response));
        }
        if (status != 200) {
            throw new UpdateException(UpdateException.CODE_HTTP_ERROR,
                    "发布页返回 HTTP " + status);
        }
        return parse(response.body());
    }

    /**
     * 解析响应体。
     *
     * <p>这里捕 {@code RuntimeException} 而不是某个具体异常类：Jackson 3 的异常体系与
     * Jackson 2 不同（{@code JsonProcessingException} 已不存在），捕具体类会把「上游改类型」
     * 变成编译期/运行期的额外风险；而这一段的作用域很窄（就一次 {@code readValue}），
     * 消息里带上异常类名，诊断信息并不损失。</p>
     */
    private List<ReleaseInfo> parse(String body) {
        try {
            List<ReleaseInfo> releases = json.readValue(body, new TypeReference<List<ReleaseInfo>>() {
            });
            return releases == null ? List.of() : List.copyOf(releases);
        } catch (RuntimeException e) {
            throw new UpdateException(UpdateException.CODE_INVALID_RESPONSE,
                    "发布页返回的内容无法解析：" + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    e);
        }
    }

    /**
     * 组装 releases 的请求地址。
     *
     * @param page 页码
     * @return 请求地址
     */
    private URI releasesUri(int page) {
        return URI.create(apiBase + "/repos/" + repository + "/releases?per_page=" + perPage
                + "&page=" + page);
    }

    /**
     * 限流时补一句「配额什么时候恢复」。
     *
     * @param response 响应
     * @return 提示片段（取不到配额信息时为空串）
     */
    private static String resetHint(HttpResponse<?> response) {
        String reset = response.headers().firstValue("x-ratelimit-reset").orElse(null);
        if (reset == null) {
            return "";
        }
        try {
            return "；配额将在 " + Instant.ofEpochSecond(Long.parseLong(reset)) + " 重置";
        } catch (NumberFormatException e) {
            return "";
        }
    }
}
