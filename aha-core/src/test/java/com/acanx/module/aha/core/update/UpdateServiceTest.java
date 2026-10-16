package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 升级服务的发现 → 挑资产 → 下载 → 校验 → 解包 → 落暂存（issue #51 §4.1 ~ §4.3）。
 *
 * <p>这是 P1 与 P2 的接缝：前面每个类各有单测，这里验「按真实顺序串起来还成立」——
 * 而且是**离线**的（本机 mock 服务器 + 现造的包），符合 `TestingSpec.md` §5.1 的环境无关性要求。</p>
 *
 * @since 0.2.0
 */
class UpdateServiceTest {

    private static final AhaVersion TARGET = AhaVersion.parse("0.1.3.00243");

    private static final AhaVersion CURRENT = AhaVersion.parse("0.1.3.00242");

    private static final PlatformTag WINDOWS = PlatformTag.of("Windows 11", "amd64");

    private static final String ASSET_NAME = "aha-desktop-0.1.3.00243-windows-amd64.zip";

    @TempDir
    Path tempDir;

    private MiniServer server;

    private Map<String, byte[]> served;

    @BeforeEach
    void setUp() throws IOException {
        served = new LinkedHashMap<>();
        server = new MiniServer(served);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    /** 造一个真包（含包内清单）并把它和发布页响应一起挂到 mock 服务器上。 */
    private void givenAPublishedPackage() throws IOException {
        byte[] jar = "jar-bytes".getBytes(StandardCharsets.UTF_8);
        Path packageZip = tempDir.resolve(ASSET_NAME);
        String manifest = """
                {"kind":"desktop-jvm","version":"0.1.3.00243","platform":"windows-amd64","jdk":25,
                 "files":[{"path":"aha-desktop.jar","sha256":"%s","size":%d}]}
                """.formatted(sha256(jar), jar.length);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(packageZip))) {
            out.putNextEntry(new ZipEntry("aha-desktop.jar"));
            out.write(jar);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("AHA-PACKAGE.json"));
            out.write(manifest.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        byte[] zipBytes = Files.readAllBytes(packageZip);
        String zipDigest = sha256(zipBytes);

        served.put("/assets/" + ASSET_NAME, zipBytes);
        byte[] sums = (zipDigest + "  " + ASSET_NAME + "\n").getBytes(StandardCharsets.UTF_8);
        served.put("/assets/SHA256SUMS", sums);
        served.put("/repos/ACANX/AHA/releases?per_page=100&page=1",
                releasesJson(zipBytes.length, sums.length));
    }

    private byte[] releasesJson(int zipSize, int sumsSize) {
        return """
                [
                  {"tag_name":"V0.1.3.00243-aha-jvm","prerelease":true,"draft":false,
                   "published_at":"2026-10-16T03:25:00Z","assets":[
                     {"name":"%s","size":%d,"browser_download_url":"%s/assets/%s","state":"uploaded"},
                     {"name":"SHA256SUMS","size":%d,"browser_download_url":"%s/assets/SHA256SUMS","state":"uploaded"}]},
                  {"tag_name":"V0.1.3.00242-aha-jvm","prerelease":true,"draft":false,
                   "published_at":"2026-10-16T02:00:00Z","assets":[
                     {"name":"aha-desktop-0.1.3.00242-windows-amd64.zip","size":1,
                      "browser_download_url":"%s/assets/old.zip","state":"uploaded"}]}
                ]
                """.formatted(ASSET_NAME, zipSize, server.base(), ASSET_NAME, sumsSize, server.base(),
                        server.base())
                .getBytes(StandardCharsets.UTF_8);
    }

    private UpdateService service() {
        return service(StagingArea.DEFAULT_MIN_AGE);
    }

    /** 清理年龄下限可注入：验「删什么」的用例不必去改文件的修改时间。 */
    private UpdateService service(Duration cleanupMinAge) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return new UpdateService(UpdatePaths.at(tempDir.resolve("update")),
                new GitHubReleaseClient(server.base(), GitHubReleaseClient.DEFAULT_REPOSITORY, 100, http,
                        tools.jackson.databind.json.JsonMapper.builder().build()),
                new PackageDownloader(http, Set.of("127.0.0.1"), server.base().toString(),
                        Duration.ofMillis(1)),
                cleanupMinAge);
    }

    @Test
    void checksFindsTheNewestVersionWithAnAssetForThisPlatform() throws IOException {
        givenAPublishedPackage();

        UpdateService.Check check = service().check(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                WINDOWS, CURRENT);

        assertThat(check.release().version()).isEqualTo(TARGET);
        assertThat(check.asset().name()).isEqualTo(ASSET_NAME);
        assertThat(check.newer()).isTrue();
        assertThat(check.platform()).isEqualTo(WINDOWS);
    }

    @Test
    void stageDownloadsVerifiesUnpacksAndChecksTheManifest() throws IOException {
        givenAPublishedPackage();
        UpdateService service = service();

        UpdateService.Check check = service.check(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                WINDOWS, CURRENT);
        UpdateService.Staged staged = service.stage(check, PackageDownloader.ProgressListener.NONE,
                PackageDownloader.CancelSignal.NEVER);

        assertThat(staged.version()).isEqualTo(TARGET);
        assertThat(staged.packageRoot().resolve("aha-desktop.jar")).exists();
        assertThat(staged.manifest().fileList()).hasSize(1);
        // 正式 zip 已落地、没有残留 .part
        assertThat(staged.packageZip()).exists();
        assertThat(staged.packageZip().resolveSibling("pkg.zip.part")).doesNotExist();
        // 日志留痕（§4.7）
        assertThat(service.log().lines()).anySatisfy(line -> assertThat(line).contains("[check]"))
                .anySatisfy(line -> assertThat(line).contains("[download]"))
                .anySatisfy(line -> assertThat(line).contains("[unpack]"));
    }

    @Test
    void stageRefusesAPackageWhoseChecksumDoesNotMatch() throws IOException {
        givenAPublishedPackage();
        // 把发布页上的校验和改掉（模拟「下载被截断或篡改」）
        served.put("/assets/SHA256SUMS",
                ("0".repeat(64) + "  " + ASSET_NAME + "\n").getBytes(StandardCharsets.UTF_8));
        UpdateService service = service();
        UpdateService.Check check = service.check(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                WINDOWS, CURRENT);

        assertThatThrownBy(() -> service.stage(check, PackageDownloader.ProgressListener.NONE,
                PackageDownloader.CancelSignal.NEVER))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("校验和不符")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_CHECKSUM_MISMATCH));

        // 不落地：暂存目录里只有未完成的 pkg.zip.part，没有 pkg.zip；解包目录为空（prepare 先建了它）
        assertThat(service.staging().packageZip(TARGET)).doesNotExist();
        assertThat(service.staging().packageZip(TARGET).resolveSibling("pkg.zip.part")).exists();
        try (var files = Files.list(service.staging().newPackageDir(TARGET))) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void checkFailsLoudlyWhenNoVersionHasAnAssetForThisChannel() throws IOException {
        // 发布页上只有 dev 线的 -aha-jvm；Stable 通道（正式发版）此刻没有任何资产
        givenAPublishedPackage();

        assertThatThrownBy(() -> service().check(ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM,
                WINDOWS, CURRENT))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("暂无")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_ASSET_MISSING));
    }

    @Test
    void listReturnsCandidatesNewestFirst() throws IOException {
        givenAPublishedPackage();

        List<ReleaseFilter.ChannelRelease> candidates = service().list(ReleaseChannel.BETA,
                UpdateKind.DESKTOP_JVM);

        assertThat(candidates).extracting(release -> release.version().value())
                .containsExactly("0.1.3.00243", "0.1.3.00242");
    }

    /** 用 JDK 自己算摘要：不借 {@code Checksums}，否则等于「自己验自己」。 */
    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── 测试脚手架：按路径返回预置字节的极简服务器 ────────────────────────────

    private static final class MiniServer implements AutoCloseable {

        private final ServerSocket serverSocket;

        private final Thread thread;

        private final Map<String, byte[]> served;

        private final AtomicInteger calls = new AtomicInteger();

        MiniServer(Map<String, byte[]> served) throws IOException {
            this.served = served;
            this.serverSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
            this.thread = Thread.ofVirtual().name("mini-update").start(this::serve);
        }

        URI base() {
            return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort());
        }

        int callCount() {
            return calls.get();
        }

        @Override
        public void close() {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // 关闭失败可忽略
            }
            thread.interrupt();
        }

        private void serve() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    handle(socket);
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            String requestLine = readLine(in);
            if (requestLine == null) {
                return;
            }
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                // 只发 GET，没有请求体
            }
            String path = requestLine.split(" ")[1];
            calls.incrementAndGet();
            byte[] body = served.get(path);
            if (body == null) {
                respond(out, 404, "not found".getBytes(StandardCharsets.UTF_8));
            } else {
                respond(out, 200, body);
            }
            out.flush();
        }

        private static void respond(OutputStream out, int status, byte[] body) throws IOException {
            String head = "HTTP/1.1 " + status + " STATUS\r\n"
                    + "Content-Type: application/octet-stream\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            out.write(body);
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') {
                    return buffer.toString(StandardCharsets.ISO_8859_1).trim();
                }
                buffer.write(b);
            }
            return buffer.size() == 0 ? null : buffer.toString(StandardCharsets.ISO_8859_1).trim();
        }
    }
    @Test
    void startupConsumesTheResultAndCleansStagingAndOldBackups() throws IOException {
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        UpdateService service = service();
        Files.createDirectories(paths.root());
        // 上次换装的回执（helper 写的）
        Files.writeString(paths.resultFile(), """
                {"ok":true,"version":"0.1.3.00243","previousVersion":"0.1.3.00242","reason":"",
                 "rolledBack":false,"at":"2026-10-16T03:30:00Z"}
                """);
        // 本次运行版本的暂存 + 一次**失败换装**留下的目标版本暂存（各占 50–110 MB）
        Files.createDirectories(paths.versionDir(CURRENT));
        Files.writeString(paths.packageZip(CURRENT), "running");
        Files.createDirectories(paths.newPackageDir(TARGET));
        Files.writeString(paths.packageZip(TARGET), "failed-attempt");
        // 两份旧备份 + 一个人工目录
        Files.createDirectories(paths.backupDir(AhaVersion.parse("0.1.3.00240")));
        Files.writeString(paths.backupDir(AhaVersion.parse("0.1.3.00240")).resolve("x.jar"), "old");
        Files.createDirectories(paths.backupDir(AhaVersion.parse("0.1.3.00242")));
        Files.createDirectories(paths.root().resolve("手工目录"));

        UpdateService.StartupReport report = service(Duration.ZERO).onStartup(CURRENT, 1);

        // 回执读到了、读完即消费
        assertThat(report.result()).isPresent();
        assertThat(report.result().orElseThrow().ok()).isTrue();
        assertThat(paths.resultFile()).doesNotExist();
        assertThat(report.helperNeverRan()).isFalse();
        // 暂存：运行版本与失败目标版本都清掉；人工目录不动
        assertThat(paths.versionDir(CURRENT)).doesNotExist();
        assertThat(paths.versionDir(TARGET)).doesNotExist();
        assertThat(paths.root().resolve("手工目录")).exists();
        assertThat(report.stagingCleared()).isEqualTo(2);
        // 备份只留最近 1 份（版本号大的先留）；清理过程无警告
        assertThat(paths.backupDir(AhaVersion.parse("0.1.3.00242"))).exists();
        assertThat(paths.backupDir(AhaVersion.parse("0.1.3.00240"))).doesNotExist();
        assertThat(report.backupsRemoved()).isEqualTo(1);
        assertThat(report.warning()).isNull();
    }

    @Test
    void startupReportsAHelperThatNeverStarted() throws IOException {
        // 上一轮写了计划、helper 却没自报启动（也没有回执）⇒ 以前是静默的「什么都没发生」
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        Files.createDirectories(paths.root());
        Files.writeString(paths.planFile(), "{}");
        // 够老才会被删（太新的计划可能是「另一个进程刚开始换装」，见年龄下限）
        Files.setLastModifiedTime(paths.planFile(), FileTime.from(Instant.now().minus(Duration.ofMinutes(20))));

        UpdateService.StartupReport report = service().onStartup(CURRENT, 2);

        assertThat(report.result()).isEmpty();
        assertThat(report.helperNeverRan()).isTrue();
        // 计划文件只用来判断，报完（且够老）即删
        assertThat(paths.planFile()).doesNotExist();
    }

    @Test
    void startupSaysNothingWhenThereIsNothingToSay() {
        // 干净启动：没有回执、没有残留计划 ⇒ 不该打扰用户（文案层在 AhaDesktopSmokeTest 里验）
        UpdateService.StartupReport report = service().onStartup(CURRENT, 2);

        assertThat(report.result()).isEmpty();
        assertThat(report.helperNeverRan()).isFalse();
        assertThat(report.warning()).isNull();
    }

    @Test
    void startupLeavesAnInFlightSwapAlone() throws IOException {
        // 换装中又启动了一次 App：helper 正要用 <版本>/new 里的包、回滚还要用 backup/，
        // 这时**什么都不能删**（2026-10-16 第二轮审查 P1-②）
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        Files.createDirectories(paths.root());
        Files.writeString(paths.planFile(), "{}");
        Files.writeString(paths.helperStartedFile(), "2026-10-16T03:30:00Z");
        Files.createDirectories(paths.newPackageDir(TARGET));
        Files.writeString(paths.packageZip(TARGET), "in-flight");
        Files.createDirectories(paths.backupDir(CURRENT));
        Files.writeString(paths.backupDir(CURRENT).resolve("aha-desktop.jar"), "old");
        Files.writeString(paths.root().resolve(UpdateLauncher.WINDOWS_SCRIPT), "helper");

        UpdateService.StartupReport report = service().onStartup(CURRENT, 0);

        assertThat(report.swapInFlight()).as("标记是刚写的 ⇒ 判定为可能还在跑").isTrue();
        assertThat(report.stagingCleared()).isZero();
        assertThat(report.backupsRemoved()).isZero();
        assertThat(report.warning()).as("要如实告诉用户「本次没动它」").contains("可能还在进行");
        assertThat(paths.versionDir(TARGET)).as("helper 还要从这里拷文件").exists();
        assertThat(paths.backupDir(CURRENT).resolve("aha-desktop.jar")).exists();
        assertThat(paths.planFile()).as("helper 可能还没读计划，不能删").exists();
        assertThat(paths.root().resolve(UpdateLauncher.WINDOWS_SCRIPT)).as("helper 还在跑，不能删").exists();
    }

    @Test
    void startupKeepsEverythingWhileTheHelperProcessIsAlive() throws IOException {
        // 主判据：标记里写着 helper 的进程号，那个进程**还活着** ⇒ 不动任何东西。
        // 用本进程自己的 pid 当「活着的 helper」，确定性最好（不依赖计时）。
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        Files.createDirectories(paths.root());
        Files.writeString(paths.planFile(), "{}");
        Files.writeString(paths.helperStartedFile(),
                ProcessHandle.current().pid() + " 2026-10-16T03:30:00Z");
        Files.createDirectories(paths.newPackageDir(TARGET));
        Files.writeString(paths.packageZip(TARGET), "in-flight");
        Files.createDirectories(paths.backupDir(CURRENT));
        Files.writeString(paths.root().resolve(UpdateLauncher.WINDOWS_SCRIPT), "helper");

        UpdateService.StartupReport report = service().onStartup(CURRENT, 0);

        assertThat(report.swapInFlight()).as("标记里的进程号还活着 ⇒ 换装还在跑").isTrue();
        assertThat(report.stagingCleared()).isZero();
        assertThat(report.backupsRemoved()).isZero();
        assertThat(report.warning()).contains("可能还在进行");
        assertThat(paths.versionDir(TARGET)).exists();
        assertThat(paths.backupDir(CURRENT)).exists();
        assertThat(paths.planFile()).as("helper 可能还没读计划").exists();
        assertThat(paths.root().resolve(UpdateLauncher.WINDOWS_SCRIPT)).exists();
    }

    @Test
    void startupKeepsRecentLeftoversEvenAfterTheHelperDied() throws IOException {
        // 第二条保险：进程号已经不在了（helper 被杀），但文件是**刚出炉**的 ⇒
        // 年龄下限（10 分钟）仍然不许删 —— 万一进程号判断失误，这一层兜住。
        // 这也正是「升级后立刻删 .zip 包」这种做法的替代：新东西一律先留着。
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        Files.createDirectories(paths.root());
        Files.writeString(paths.planFile(), "{}");
        Files.writeString(paths.helperStartedFile(),
                "2147483000 2026-10-16T03:30:00Z");
        Files.createDirectories(paths.versionDir(TARGET));
        Files.writeString(paths.packageZip(TARGET), "just-downloaded");

        UpdateService.StartupReport report = service().onStartup(CURRENT, 2);


        assertThat(report.swapInFlight()).as("进程号已经不存在 ⇒ 不算在跑").isFalse();
        assertThat(paths.versionDir(TARGET)).as("太新的残留必须先留着").exists();
        assertThat(paths.planFile()).as("太新的计划也不删").exists();
        assertThat(paths.helperStartedFile()).exists();
        assertThat(report.stagingCleared()).isZero();
        assertThat(report.warning()).as("要如实说「本次刻意没动」").contains("没动");
    }

    @Test
    void startupTreatsAStaleSwapMarkerAsLeftovers() throws IOException {
        // helper 起来过、却从没写过回执，而且标记早已过期 ⇒ 按残留清理，并把这件事说出来
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        Files.createDirectories(paths.root());
        Files.writeString(paths.planFile(), "{}");
        Files.writeString(paths.helperStartedFile(), "2026-10-16T03:30:00Z");
        // 「20 分钟前」的残留：标记与暂存都够老 ⇒ 该清就清（年龄下限是 10 分钟）
        Instant old = Instant.now().minus(Duration.ofMinutes(20));
        Files.setLastModifiedTime(paths.helperStartedFile(), FileTime.from(old));
        Files.setLastModifiedTime(paths.planFile(), FileTime.from(old));
        Files.createDirectories(paths.versionDir(TARGET));
        Files.setLastModifiedTime(paths.versionDir(TARGET), FileTime.from(old));
        Files.writeString(paths.root().resolve(UpdateLauncher.WINDOWS_SCRIPT), "helper");

        UpdateService.StartupReport report = service().onStartup(CURRENT, 2);

        assertThat(report.swapInFlight()).isFalse();
        assertThat(paths.versionDir(TARGET)).doesNotExist();
        assertThat(paths.helperStartedFile()).doesNotExist();
        assertThat(paths.root().resolve(UpdateLauncher.WINDOWS_SCRIPT))
                .as("换装结束（或残留）时 helper 该消失——这是 §8.2 第 5 项的验收口径")
                .doesNotExist();
        assertThat(report.warning()).contains("没有留下回执");
    }

    @Test
    void startupCountsOnlyWhatItReallyRemoved() throws IOException {
        // P2-⑤：以前无论如何都 +1，什么都没清也会说「清理 1 个暂存目录」
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        Files.createDirectories(paths.root());

        UpdateService.StartupReport report = service().onStartup(CURRENT, 2);

        assertThat(report.stagingCleared()).isZero();
        assertThat(report.backupsRemoved()).isZero();
    }

    @Test
    void prepareSwapRefusesWhileAnotherSwapIsInFlight() throws IOException {
        // 两个 helper 同时换装会把安装目录搅成两半（2026-10-16 第二轮审查 P3）
        Path installRoot = tempDir.resolve("install");
        Files.createDirectories(installRoot);
        Files.writeString(installRoot.resolve("aha-desktop.jar"), "jar");
        Files.writeString(installRoot.resolve("AhaDesktop.bat"), "launcher");
        Files.writeString(installRoot.resolve(UpdateLauncher.WINDOWS_ENTRY), "cmd-helper");
        Files.writeString(installRoot.resolve(UpdateLauncher.WINDOWS_SCRIPT), "ps1-helper");
        Files.writeString(installRoot.resolve(UpdateLauncher.POSIX_ENTRY), "sh-helper");
        Path packageRoot = tempDir.resolve("newpkg");
        Files.createDirectories(packageRoot);
        Files.writeString(packageRoot.resolve("aha-desktop.jar"), "jar");
        PackageManifest manifest = PackageManifest.parse("""
                {"kind":"desktop-jvm","version":"0.1.3.00243","platform":"windows-amd64","jdk":25,
                 "files":[{"path":"aha-desktop.jar","sha256":"%s","size":3}]}
                """.formatted("0".repeat(64)));
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        Files.createDirectories(paths.root());
        Files.writeString(paths.planFile(), "{}");
        Files.writeString(paths.helperStartedFile(), "2026-10-16T03:30:00Z");
        UpdateService.Staged staged = new UpdateService.Staged(TARGET, packageRoot,
                paths.packageZip(TARGET), manifest, true);

        assertThatThrownBy(() -> service().prepareSwap(staged,
                InstallLayout.of(installRoot.resolve("aha-desktop.jar")), CURRENT))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("还在进行");

        // 换成「标记里写着活着的进程号」：同样要拒（主判据）
        Files.writeString(paths.helperStartedFile(),
                ProcessHandle.current().pid() + " 2026-10-16T03:30:00Z");
        assertThatThrownBy(() -> service().prepareSwap(staged,
                InstallLayout.of(installRoot.resolve("aha-desktop.jar")), CURRENT))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("还在进行");
    }

    @Test
    void waitingForTheHelperStartMarkerTimesOutLoudly() {
        UpdateService service = service();

        assertThatThrownBy(() -> service.waitForHelperStarted(Duration.ZERO))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("没有启动")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_HELPER_NOT_STARTED));
    }

    @Test
    void waitingForTheHelperStartMarkerReturnsAsSoonAsItAppears() throws Exception {
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        Files.createDirectories(paths.root());
        UpdateService service = service();

        Thread writer = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(80);
                Files.writeString(paths.helperStartedFile(), "started");
            } catch (Exception ignored) {
                // 测试线程，忽略
            }
        });
        service.waitForHelperStarted(Duration.ofSeconds(5));
        writer.join();

        assertThat(paths.helperStartedFile()).exists();
    }

    @Test
    void prepareSwapInstallsTheHelperBeforeWritingThePlan() throws IOException {
        // 装不了 helper 时**不能**留下计划文件：否则下次启动会把它当成「上次换装没做完」报告，
        // 而它其实永远不会被执行（2026-10-16 审查 P2-10）。
        Path installRoot = tempDir.resolve("install");
        Files.createDirectories(installRoot);
        Files.writeString(installRoot.resolve("aha-desktop.jar"), "jar");
        Files.writeString(installRoot.resolve("AhaDesktop.bat"), "launcher");
        Path packageRoot = tempDir.resolve("newpkg");
        Files.createDirectories(packageRoot);
        Files.writeString(packageRoot.resolve("aha-desktop.jar"), "jar");
        PackageManifest manifest = PackageManifest.parse("""
                {"kind":"desktop-jvm","version":"0.1.3.00243","platform":"windows-amd64","jdk":25,
                 "files":[{"path":"aha-desktop.jar","sha256":"%s","size":3}]}
                """.formatted("0".repeat(64)));
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        UpdateService service = service();
        UpdateService.Staged staged = new UpdateService.Staged(TARGET, packageRoot,
                paths.packageZip(TARGET), manifest, true);

        // 安装根里没有换装 helper ⇒ 必须在写计划之前就失败
        assertThatThrownBy(() -> service.prepareSwap(staged,
                InstallLayout.of(installRoot.resolve("aha-desktop.jar")), CURRENT))
                .isInstanceOf(UpdateException.class);
        assertThat(paths.planFile()).doesNotExist();
    }

    @Test
    void prepareSwapTakesTheHelperFromTheNewPackageWhenTheInstallRootHasNone() throws IOException {
        // 真机实测（2026-10-16）：正在运行的安装是「helper 还没随包分发」之前的包 ⇒ 安装根里没有；
        // 只认安装根就会报「该包不完整、无法自更新」而卡住升级（新包其实是完整的）。
        Path installRoot = tempDir.resolve("old-install");
        Files.createDirectories(installRoot);
        Files.writeString(installRoot.resolve("aha-desktop.jar"), "jar");
        Files.writeString(installRoot.resolve(InstallLayout.isWindows()
                ? InstallLayout.WINDOWS_LAUNCHER : InstallLayout.POSIX_LAUNCHER), "launcher");
        Path packageRoot = tempDir.resolve("new-pkg");
        Files.createDirectories(packageRoot);
        Files.writeString(packageRoot.resolve("aha-desktop.jar"), "jar");
        Files.writeString(packageRoot.resolve(UpdateLauncher.WINDOWS_ENTRY), "cmd-helper");
        Files.writeString(packageRoot.resolve(UpdateLauncher.WINDOWS_SCRIPT), "ps1-helper");
        Files.writeString(packageRoot.resolve(UpdateLauncher.POSIX_ENTRY), "sh-helper");
        PackageManifest manifest = PackageManifest.parse("""
                {"kind":"desktop-jvm","version":"0.1.3.00243","platform":"windows-amd64","jdk":25,
                 "files":[{"path":"aha-desktop.jar","sha256":"%s","size":3}]}
                """.formatted("0".repeat(64)));
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        UpdateService.Staged staged = new UpdateService.Staged(TARGET, packageRoot,
                paths.packageZip(TARGET), manifest, true);

        Path plan = service().prepareSwap(staged,
                InstallLayout.of(installRoot.resolve("aha-desktop.jar")), CURRENT);

        assertThat(plan).exists();
        String name = InstallLayout.isWindows()
                ? UpdateLauncher.WINDOWS_SCRIPT : UpdateLauncher.POSIX_ENTRY;
        assertThat(paths.root().resolve(name)).as("helper 从新包里取").hasContent(
                InstallLayout.isWindows() ? "ps1-helper" : "sh-helper");
    }

    @Test
    void launchHelperClearsAStaleMarkerAndWaitsForTheRealOne() throws Exception {
        // POSIX 的换装脚本尚未实现（Windows 走 apply-update.cmd），本用例只验「拉起 + 等标记」
        // 这条握手：残留标记不能让它秒过，必须等 helper 自己写出来。
        Assumptions.assumeFalse(InstallLayout.isWindows(),
                "本用例用 POSIX 的 apply-update.sh 假装 helper");
        Path installRoot = tempDir.resolve("install");
        Files.createDirectories(installRoot);
        Files.writeString(installRoot.resolve("aha-desktop.jar"), "jar");
        Files.writeString(installRoot.resolve("AhaDesktop.bat"), "launcher");
        // 假 helper：把「已启动」标记写到计划文件所在目录（就是 <AHA_HOME>/update/）
        Path fakeHelper = installRoot.resolve(UpdateLauncher.POSIX_ENTRY);
        Files.writeString(fakeHelper, "#!/bin/sh\n"
                + "root=$(dirname \"$1\")\n"
                + "sleep 1\n"
                + "echo started > \"$root/helper-started\"\n");
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        UpdateLauncher.installHelpers(installRoot, paths);
        // 先摆一个残留标记：如果 launchHelper 不清它，等待会立刻通过（等于没等）
        Files.createDirectories(paths.root());
        Files.writeString(paths.helperStartedFile(), "stale");
        Path plan = paths.root().resolve("update-plan.json");
        Files.writeString(plan, "{}");

        Process helper = service().launchHelper(plan, Duration.ofSeconds(15));

        assertThat(paths.helperStartedFile()).exists();
        assertThat(Files.readString(paths.helperStartedFile())).contains("started");
        helper.waitFor(5, TimeUnit.SECONDS);
    }

}
