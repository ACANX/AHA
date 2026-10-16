package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * 下载器：把发布页的资产下到暂存目录，并做到「要么是完整的、要么什么都不改」
 * （issue #51 §4.2、§4.3、§4.7）。
 *
 * <p>守住的口径：</p>
 * <ul>
 *   <li><b>不往安装目录写临时文件</b>：一切先落到 `~/.aha/update/&lt;版本&gt;/`（安装目录可能被占用或只读）；</li>
 *   <li><b>先写 {@code .part} 再改名</b>：中断/失败时留下的是 {@code .part}，正式文件只在
 *       「下载完整 + 校验和通过」之后才出现 —— 这就是 §4.3「不匹配即拒绝落地」的实现方式；</li>
 *   <li><b>复用同尺寸旧包</b>：同名同大小已在暂存目录里就直接用（沿用既有脚本的做法）；
 *       有校验和时会先核对，核对不过则丢弃重下（缓存坏了不该等价于失败）；</li>
 *   <li><b>重试有边界</b>：只对网络中断 / 超时 / 5xx 重试 2 次（指数退避）；<b>4xx 不重试</b>（重试无意义）；</li>
 *   <li><b>只跟随白名单跳转</b>：先判初始地址前缀，逐跳判主机（GitHub 资产会 302 到
 *       {@code objects.githubusercontent.com}）；</li>
 *   <li><b>可取消</b>：取消在下一个数据块生效，抛 {@code UPDATE_CANCELLED}（不是错误），`.part` 保留。</li>
 * </ul>
 *
 * <p>超时：连接 15s、单次请求 60s（与 §4.2 一致）；单次请求超时只覆盖到响应头（JDK 的实现如此），
 * 传输中途卡住由「重试」兜住。</p>
 *
 * @since 0.2.0
 */
public final class PackageDownloader {

    /** 摘要文件名：发布页上的资产（release 级，不是包内）。 */
    public static final String SUMS_ASSET_NAME = "SHA256SUMS";

    /** 默认允许的主机（issue #51 §4.7）。 */
    public static final Set<String> DEFAULT_ALLOWED_HOSTS = Set.of(
            "github.com", "objects.githubusercontent.com", "api.github.com");

    /** 默认允许的资产地址前缀：只从本仓库的发布页取件。 */
    public static final String DEFAULT_ALLOWED_URL_PREFIX =
            "https://github.com/ACANX/AHA/releases/download/";

    /** 连接超时。 */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    /** 单次请求超时。 */
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    /** 最多尝试次数（首次 + 2 次重试）。 */
    public static final int MAX_ATTEMPTS = 3;

    /** 首次退避（之后翻倍）。 */
    private static final Duration FIRST_BACKOFF = Duration.ofMillis(500);

    /** 最多跟随几次跳转。 */
    private static final int MAX_REDIRECTS = 5;

    /** 未完成文件的后缀。 */
    private static final String PART_SUFFIX = ".part";

    /** 读缓冲区大小。 */
    private static final int BUFFER_SIZE = 64 * 1024;

    private final HttpClient http;

    private final Set<String> allowedHosts;

    private final String allowedUrlPrefix;

    private final Duration firstBackoff;

    /** 默认构造：真实 GitHub 资产地址。 */
    public PackageDownloader() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                DEFAULT_ALLOWED_HOSTS, DEFAULT_ALLOWED_URL_PREFIX, FIRST_BACKOFF);
    }

    /**
     * 可注入构造（测试指向本机 mock 服务器；也便于将来指向企业镜像）。
     *
     * @param http             HTTP 客户端
     * @param allowedHosts     允许的主机
     * @param allowedUrlPrefix 初始地址必须以此前缀开头
     * @param firstBackoff     首次退避时长
     */
    PackageDownloader(HttpClient http, Set<String> allowedHosts, String allowedUrlPrefix,
                      Duration firstBackoff) {
        this.http = http;
        this.allowedHosts = Set.copyOf(allowedHosts);
        this.allowedUrlPrefix = allowedUrlPrefix;
        this.firstBackoff = firstBackoff;
    }

    /**
     * 下载资产（不校验和）。
     *
     * @param asset     资产
     * @param targetDir 暂存目录
     * @return 结果
     * @throws UpdateException 地址不在白名单 / 下载失败 / 用户取消
     */
    public Downloaded download(AssetInfo asset, Path targetDir) {
        return download(asset, targetDir, null, ProgressListener.NONE, CancelSignal.NEVER);
    }

    /**
     * 下载资产并校验摘要。
     *
     * @param asset          资产
     * @param targetDir      暂存目录
     * @param expectedSha256 期望摘要；{@code null} 表示该版本未提供发布页校验和
     * @return 结果
     * @throws UpdateException 地址不在白名单 / 下载失败 / 校验和不符 / 用户取消
     */
    public Downloaded download(AssetInfo asset, Path targetDir, String expectedSha256) {
        return download(asset, targetDir, expectedSha256, ProgressListener.NONE, CancelSignal.NEVER);
    }

    /**
     * 下载资产（带进度与取消）。
     *
     * @param asset          资产
     * @param targetDir      暂存目录
     * @param expectedSha256 期望摘要；{@code null} 表示未提供
     * @param progress       进度回调
     * @param cancel         取消信号
     * @return 结果
     * @throws UpdateException 地址不在白名单 / 下载失败 / 校验和不符 / 用户取消
     */
    public Downloaded download(AssetInfo asset, Path targetDir, String expectedSha256,
                              ProgressListener progress, CancelSignal cancel) {
        if (asset == null || asset.name() == null || asset.name().isBlank()) {
            throw new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED, "资产名为空，无法下载");
        }
        URI uri = parseAndCheckSource(asset);
        Path target = targetDir.resolve(asset.name());
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED,
                    "创建暂存目录失败：" + targetDir + "（" + e.getMessage() + "）", e);
        }
        Downloaded reused = reuseCached(asset, target, expectedSha256);
        if (reused != null) {
            return reused;
        }
        Path part = target.resolveSibling(target.getFileName() + PART_SUFFIX);
        long bytes = fetchWithRetries(uri, part, asset, progress, cancel);
        String actual = null;
        if (expectedSha256 != null) {
            actual = Checksums.verify(part, expectedSha256, asset.name());
        }
        move(part, target);
        return new Downloaded(target, bytes, expectedSha256 != null, actual);
    }

    /** 先判地址：必须是我们允许的前缀，且主机在白名单里（§4.7）。 */
    private URI parseAndCheckSource(AssetInfo asset) {
        String url = asset.downloadUrl();
        if (url == null || url.isBlank()) {
            throw new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED,
                    "资产没有下载地址：" + asset.name());
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (RuntimeException e) {
            throw new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED,
                    "资产的下载地址不合法：" + url, e);
       }
        if (!url.startsWith(allowedUrlPrefix)) {
            throw new UpdateException(UpdateException.CODE_UNTRUSTED_SOURCE,
                    "下载地址不在允许的范围内（只从本仓库发布页取件）：" + url);
        }
        if (!allowedHosts.contains(host(uri))) {
            throw new UpdateException(UpdateException.CODE_UNTRUSTED_SOURCE,
                    "下载地址的主机不在白名单：" + host(uri) + "（允许：" + allowedHosts + "）");
        }
        return uri;
    }

    /** 复用暂存目录里同尺寸的旧包（有校验和时先核对，核对不过则丢弃重下）。 */
    private Downloaded reuseCached(AssetInfo asset, Path target, String expectedSha256) {
        if (!Files.isRegularFile(target)) {
            return null;
        }
        try {
            long size = Files.size(target);
            if (asset.size() > 0 && size != asset.size()) {
                return null;
            }
            if (expectedSha256 == null) {
                return new Downloaded(target, size, false, null);
            }
            try {
                String actual = Checksums.verify(target, expectedSha256, asset.name());
                return new Downloaded(target, size, true, actual);
            } catch (UpdateException mismatch) {
                // 缓存里的文件坏了：不该等价于「下载失败」，丢掉重下即可
                Files.deleteIfExists(target);
                return null;
            }
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED,
                    "检查已存在的文件失败：" + target + "（" + e.getMessage() + "）", e);
        }
    }

    /**
     * 带重试地取一次（含跳转跟随）。
     *
     * @param uri      起始地址
     * @param part     未完成文件的目标路径
     * @param asset    资产
     * @param progress 进度回调
     * @param cancel   取消信号
     */
    private long fetchWithRetries(URI uri, Path part, AssetInfo asset, ProgressListener progress,
                                  CancelSignal cancel) {
        UpdateException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Attempt result = fetchOnce(uri, part, asset, progress, cancel);
            if (result.failure() == null) {
                return result.bytes();
            }
            if (!result.retryable()) {
                throw result.failure();
            }
            last = result.failure();
            if (attempt < MAX_ATTEMPTS) {
                sleep(backoff(attempt), cancel);
            }
        }
        throw last == null ? new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED,
                "下载失败（未知原因）") : last;
    }

    /**
     * 取一次：跟随白名单内的跳转，边下边写 {@code .part}。
     *
     * <p>返回 {@code failure == null} 表示成功；{@code retryable} 决定要不要重试。
     * 用户取消<b>不</b>走返回值，而是直接抛出（构造上就不重试）。</p>
     */
    private Attempt fetchOnce(URI uri, Path part, AssetInfo asset, ProgressListener progress,
                              CancelSignal cancel) {
        URI current = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (cancel.cancelled()) {
                throw cancelled();
            }
            HttpResponse<InputStream> response;
            try {
                response = http.send(HttpRequest.newBuilder(current)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw cancelled();
            } catch (IOException e) {
                return retryable(UpdateException.CODE_NETWORK_ERROR,
                        "下载中断（第 " + (hop + 1) + " 次请求）：" + e.getMessage(), e);
            }
            int status = response.statusCode();
            if (isRedirect(status)) {
                String location = response.headers().firstValue("location").orElse(null);
                if (location == null) {
                    return fatal(UpdateException.CODE_DOWNLOAD_FAILED,
                            "下载地址返回 " + status + " 但没有 Location 头");
                }
                URI next = current.resolve(location);
                if (!allowedHosts.contains(host(next))) {
                    return fatal(UpdateException.CODE_UNTRUSTED_SOURCE,
                            "跳转目标的主机不在白名单：" + host(next) + "（允许：" + allowedHosts + "）");
                }
                closeQuietly(response);
                current = next;
                continue;
            }
            if (status >= 500) {
                closeQuietly(response);
                return retryable(UpdateException.CODE_HTTP_ERROR,
                        "发布页返回 HTTP " + status + "（稍后重试）");
            }
            if (status != 200) {
                closeQuietly(response);
                return fatal(UpdateException.CODE_HTTP_ERROR, "下载返回 HTTP " + status
                        + "（该地址可能已失效或需要授权，重试没有意义）");
            }
            return streamToFile(response, part, asset, progress, cancel);
        }
        return fatal(UpdateException.CODE_DOWNLOAD_FAILED,
                "跳转次数超过上限（" + MAX_REDIRECTS + "）：" + uri);
    }

    /** 把响应体流式写入 {@code .part}，边写边报进度、边看取消。 */
    private Attempt streamToFile(HttpResponse<InputStream> response, Path part, AssetInfo asset,
                                 ProgressListener progress, CancelSignal cancel) {
        long total = 0;
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(part)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (cancel.cancelled()) {
                    throw cancelled();
                }
                writeFatal(out, buffer, read, part);
                total += read;
                progress.onProgress(total, asset.size());
            }
            out.flush();
        } catch (IOException e) {
            return retryable(UpdateException.CODE_NETWORK_ERROR,
                    "下载中断于 " + total + " 字节：" + e.getMessage(), e);
        }
        if (asset.size() > 0 && total != asset.size()) {
            return retryable(UpdateException.CODE_DOWNLOAD_FAILED,
                    "下载不完整：" + asset.name() + " 期望 " + asset.size() + " 字节，实际 " + total
                            + " 字节");
        }
        return Attempt.ok(total);
    }

    /** 写盘失败是环境问题（磁盘满 / 只读），重试没有意义，直接抛。 */
    private static void writeFatal(OutputStream out, byte[] buffer, int length, Path part) {
        try {
            out.write(buffer, 0, length);
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED,
                    "写入文件失败：" + part + "（" + e.getMessage() + "）", e);
        }
    }

    /** 校验通过后把 {@code .part} 改名成正式文件（同目录内，优先原子移动）。 */
    private static void move(Path part, Path target) {
        try {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallback) {
                throw new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED,
                        "校验已通过但改名失败：" + part + " → " + target
                                + "（" + fallback.getMessage() + "）", fallback);
            }
        }
    }

    /** 退避时长（指数）。 */
    private Duration backoff(int attempt) {
        return firstBackoff.multipliedBy(1L << (attempt - 1));
    }

    /** 退避等待（可被取消）。 */
    private static void sleep(Duration duration, CancelSignal cancel) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw cancelled();
        }
        if (cancel.cancelled()) {
            throw cancelled();
        }
    }

    private static UpdateException cancelled() {
        return new UpdateException(UpdateException.CODE_CANCELLED,
                "下载已取消（已保留未完成的 .part 文件，可稍后续传）");
    }

    private static String host(URI uri) {
        return uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static void closeQuietly(HttpResponse<InputStream> response) {
        try {
            response.body().close();
        } catch (IOException ignored) {
            // 关闭失败可忽略
        }
    }

    private static Attempt retryable(String code, String message, Throwable cause) {
        return new Attempt(new UpdateException(code, message, cause), true, 0);
    }

    private static Attempt retryable(String code, String message) {
        return new Attempt(new UpdateException(code, message), true, 0);
    }

    private static Attempt fatal(String code, String message) {
        return new Attempt(new UpdateException(code, message), false, 0);
    }

    /**
     * 一次尝试的结论。
     *
     * @param failure   失败原因；成功为 {@code null}
     * @param retryable 是否值得重试
     * @param bytes     已下载字节数（成功时有效）
     */
    private record Attempt(UpdateException failure, boolean retryable, long bytes) {

        /** 成功。 */
        private static Attempt ok(long bytes) {
            return new Attempt(null, false, bytes);
        }
    }

    /**
     * 下载结果。
     *
     * @param file             落地的文件
     * @param size             字节数
     * @param checksumVerified 是否用发布页校验和验过（{@code false} 表示该版本未提供校验和，
     *                         界面必须照实标注，不能假装验过）
     * @param sha256           实际摘要；未校验时为 {@code null}
     */
    public record Downloaded(Path file, long size, boolean checksumVerified, String sha256) {
    }

    /**
     * 进度回调。
     *
     * @since 0.2.0
     */
    @FunctionalInterface
    public interface ProgressListener {

        /** 空回调。 */
        ProgressListener NONE = (downloaded, total) -> {
        };

        /**
         * 收到一段数据。
         *
         * @param downloaded 已下载字节数
         * @param total      总字节数（未知时为 0）
         */
        void onProgress(long downloaded, long total);
    }

    /**
     * 取消信号。
     *
     * @since 0.2.0
     */
    @FunctionalInterface
    public interface CancelSignal {

        /** 永不取消。 */
        CancelSignal NEVER = () -> false;

        /**
         * 是否已请求取消。
         *
         * @return 已取消返回 {@code true}
         */
        boolean cancelled();
    }
}
