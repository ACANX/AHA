package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.AppVersion;
import com.acanx.module.aha.common.exception.UpdateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 升级服务：把「发现 → 挑资产 → 下载 → 校验 → 解包 → 落暂存 → 写计划」串成一条路径
 * （issue #51 §4、§7 P1/P2）。
 *
 * <p>落在 {@code aha-core} 且<b>不依赖 JavaFX</b>：桌面端（现在的入口）与将来的 CLI
 * （`aha update`）共用同一套实现，不做两套。</p>
 *
 * <p><b>本类只把包放到暂存区、写好计划、拉起 helper</b>，绝不自己替换文件 ——
 * 正在运行的进程换不掉自己的文件（§4.5）。真正改安装目录的是外部 helper。</p>
 *
 * <p>每一步都先写日志（版本、来源 tag、资产名、校验结果）再动作，排障时有据可查（§4.7）。</p>
 *
 * @since 0.2.0
 */
public final class UpdateService {

    /** 发布页里的校验和资产名。 */
    public static final String SUMS_ASSET = PackageDownloader.SUMS_ASSET_NAME;

    private final UpdatePaths paths;

    private final StagingArea staging;

    private final UpdateLog log;

    private final GitHubReleaseClient client;

    private final PackageDownloader downloader;

    /**
     * 生产构造：真实的发布页与暂存区。
     *
     * @param paths 路径布局
     */
    public UpdateService(UpdatePaths paths) {
        this(paths, new GitHubReleaseClient(),
                new PackageDownloader());
    }

    /**
     * 可注入构造（测试用本机 mock 服务器）。
     *
     * @param paths      路径布局
     * @param client     发布页客户端
     * @param downloader 下载器
     */
    public UpdateService(UpdatePaths paths, GitHubReleaseClient client,
                         PackageDownloader downloader) {
        this.paths = paths;
        this.staging = new StagingArea(paths);
        this.log = new UpdateLog(paths);
        this.client = client;
        this.downloader = downloader;
    }

    /**
     * 列出某个通道某个形态的候选版本（版本从新到旧）。
     *
     * @param channel 通道
     * @param kind    形态
     * @return 候选版本
     * @throws UpdateException 发布页访问失败
     */
    public List<ReleaseFilter.ChannelRelease> list(ReleaseChannel channel, UpdateKind kind) {
        List<ReleaseInfo> releases = client.listReleases();
        List<ReleaseFilter.ChannelRelease> candidates = ReleaseFilter.select(releases, channel, kind);
        log.append("list", "通道 " + channel.key() + " / " + kind.targetWord()
                + "：候选 " + candidates.size() + " 个版本");
        return candidates;
    }

    /**
     * 检查更新：把「最新候选版本」解析成本平台可用的一份资产。
     *
     * <p><b>故意在这里用 {@link AssetSelector#select}</b>（挑不到就抛）而不是
     * {@link AssetSelector#available}（跳过挑不到的）：跳过等于静默退到更旧的版本，
     * 那正是 issue #210 的坑。</p>
     *
     * @param channel  通道
     * @param kind     形态
     * @param platform 本机平台
     * @param current  当前版本
     * @return 检查结果
     * @throws UpdateException 通道无版本 / 最新版缺本平台资产 / 发布页访问失败
     */
    public Check check(ReleaseChannel channel, UpdateKind kind, PlatformTag platform,
                       AhaVersion current) {
        List<ReleaseFilter.ChannelRelease> candidates = list(channel, kind);
        if (candidates.isEmpty()) {
            throw new UpdateException(UpdateException.CODE_ASSET_MISSING,
                    channel.label() + " 通道暂无 " + kind.targetWord() + " 的可用版本"
                            + "（可能是该通道还没有带资产的发版，或该形态在该通道不发版）");
        }
        ReleaseFilter.ChannelRelease newest = candidates.get(0);
        AssetInfo asset = AssetSelector.select(newest, channel, kind, platform);
        boolean newer = newest.version().compareTo(current) > 0;
        log.append("check", "当前 " + current.value() + "，最新 " + newest.version().value()
                + "（" + asset.name() + "），" + (newer ? "有更新" : "已是最新或更低"));
        return new Check(channel, kind, platform, current, newest, asset, newer);
    }

    /**
     * 暂存：下载校验和 → 下载包 → 校验 → 解包 → 用包内清单逐项核对。
     *
     * <p>任何一步失败都不会留下「半个可用包」：正式文件只在「校验和通过」之后才从
     * {@code .part} 改名过来，解包与清单核对都在暂存目录里做。</p>
     *
     * @param check      检查结果
     * @param progress   进度回调
     * @param cancel     取消信号
     * @return 暂存结果（新包根目录 + 动作列表）
     * @throws UpdateException 任一步失败
     */
    public Staged stage(Check check, PackageDownloader.ProgressListener progress,
                        PackageDownloader.CancelSignal cancel) {
        AhaVersion version = check.release().version();
        staging.prepare(version);
        String expected = expectedChecksum(check.release(), check.asset());
        // 落到 <版本>/pkg.zip（不是发布页上的资产名）：暂存目录的布局是契约的一部分（§4.2）
        PackageDownloader.Downloaded downloaded = downloader.downloadTo(check.asset(),
                staging.packageZip(version), expected, progress, cancel);
        log.append("download", check.asset().name() + " → " + downloaded.file()
                + "（校验" + (downloaded.checksumVerified() ? "通过" : "未提供") + "）");

        Path packageRoot = staging.newPackageDir(version);
        int files = PackageUnpacker.unpack(downloaded.file(), packageRoot);
        PackageManifest manifest = PackageManifest.read(packageRoot.resolve("AHA-PACKAGE.json"));
        manifest.verify(packageRoot, check.kind(), version, check.platform());
        log.append("unpack", "解出 " + files + " 个文件，包内清单核对通过（"
                + manifest.fileList().size() + " 项）");
        return new Staged(version, packageRoot, downloaded.file(), manifest,
                downloaded.checksumVerified());
    }

    /**
     * 备好换装：算动作、写计划、把 helper 复制到 {@code <AHA_HOME>/update/}。
     *
     * @param staged  暂存结果
     * @param layout  当前安装布局
     * @param current 当前版本（用于备份目录命名；拿不到时用目标版本）
     * @return 计划文件路径
     * @throws UpdateException 计划不合法或 helper 缺失
     */
    public Path prepareSwap(Staged staged, InstallLayout layout, AhaVersion current) {
        layout.requireEntryPoints();
        PackageManifest installed = readInstalledManifest(layout);
        List<UpdatePlan.Action> actions = SwapPlan.actions(staged.manifest(), installed);
        if (actions.isEmpty()) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "新包与已装包的文件清单完全一致，没有可执行的动作（可能装的就是这一版）");
        }
        AhaVersion backupVersion = current == null ? staged.version() : current;
        // 上一次换装还在跑（计划在、启动标记是刚写的、还没回执）⇒ 拒绍再写一份计划：
        // 两个 helper 同时换装会把安装目录搅成两半（2026-10-16 第二轮审查 P3）。
        if (swapMayBeInFlight(Files.isRegularFile(paths.planFile()),
                Files.isRegularFile(paths.helperStartedFile()),
                !Files.isRegularFile(paths.resultFile()))) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "上一次换装还在进行（helper 已启动、还没留下回执）——稍等片刻再试");
        }
        // 先把 helper 装到 <AHA_HOME>/update/，**再**写计划：反过来的话，helper 缺失
        // （包没带 / 本平台没实现）会留下一个永远执行不到的计划文件，下次启动还会把它
        // 当成「上次换装没做完」报告给用户（2026-10-16 审查 P2-10）。
        UpdateLauncher.installHelpers(layout.root(), paths);
        UpdatePlan plan = UpdatePlan.create(staged.version(), layout.root(), staged.packageRoot(),
                staging.backupDir(backupVersion), ProcessHandle.current().pid(),
                layout.restartCommand(), actions);
        plan.write(paths.planFile());
        log.append("plan", "换装计划已写好：" + paths.planFile() + "（动作 " + actions.size()
                + " 项，备份 " + staging.backupDir(backupVersion) + "）");
        return paths.planFile();
    }

    /**
     * 拉起 helper（调用方随后应尽快让 App 退出）。
     *
     * @param planFile 计划文件
     * @return 已启动的 helper 进程
     * @throws UpdateException 拉起失败
     */
    public Process launchHelper(Path planFile) {
        return launchHelper(planFile, HELPER_START_TIMEOUT);
    }

    /**
     * 拉起 helper，并**等它自报「已启动」**（等待上限可注入，便于测试）。
     *
     * <p>为什么必须等：App 一退出就没法再告诉用户任何事。helper 若被安全策略拦下、
     * 或被启动器塞进随父进程退出的作业对象，用户看到的是「App 关了、什么都没发生」。
     * 等不到标记就抛 {@link UpdateException#CODE_HELPER_NOT_STARTED}，App 留在前台报错
     * （2026-10-16 审查 P1-8）。</p>
     *
     * @param planFile     计划文件
     * @param startTimeout 等待「已启动」标记的上限
     * @return 已启动的 helper 进程
     * @throws UpdateException 拉起失败，或 helper 在超时内没有自报启动
     */
    public Process launchHelper(Path planFile, Duration startTimeout) {
        try {
            // 先清掉上一轮的标记：残留标记会让等待立刻通过，等于没等
            Files.deleteIfExists(paths.helperStartedFile());
        } catch (IOException e) {
            log.append("launch", "清不掉旧的 helper 启动标记（无碍）：" + e.getMessage());
        }
        Process helper = UpdateLauncher.launch(paths.root(), planFile);
        log.append("launch", "已拉起换装 helper（pid " + helper.pid() + "），等它自报「已启动」");
        waitForHelperStarted(startTimeout);
        log.append("launch", "helper 已自报启动，App 即将退出");
        return helper;
    }

    /** 等待 helper 自报启动的默认上限：够慢机器上 PowerShell 冷启动，又不至于让用户干等。 */
    public static final Duration HELPER_START_TIMEOUT = Duration.ofSeconds(20);

    /**
     * 等 helper 写「已启动」标记（包内可见：单测直接调它，不必真起进程）。
     *
     * @param timeout 等待上限
     * @throws UpdateException 超时或被中断
     */
    void waitForHelperStarted(Duration timeout) {
        Path marker = paths.helperStartedFile();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(marker)) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UpdateException(UpdateException.CODE_HELPER_NOT_STARTED,
                        "等待换装 helper 启动时被中断", e);
            }
        }
        throw new UpdateException(UpdateException.CODE_HELPER_NOT_STARTED,
                "换装程序没有启动（等 " + timeout.toSeconds() + " 秒没等到它的启动标记）——"
                        + "可能被安全策略拦下了。本次未做任何改动，App 继续运行；"
                        + "可手动到发布页取件，或查看 " + paths.logFile());
    }

    /**
     * 读取上次换装的回执（读完即消费）。
     *
     * @return 回执
     */
    public Optional<UpdateResult> consumeLastResult() {
        return UpdateResult.consume(paths.resultFile());
    }

    /**
     * 清理某个版本的暂存内容（换装成功后由下次启动调用；幂等）。
     *
     * @param version 版本
     * @param keepBackups 备份保留份数
     * @return 清理计数（暂存目录数 / 旧备份份数），给用户看的数字要如实（2026-10-16 审查 P2-⑤）
     */
    public Cleanup cleanupAfterSwap(AhaVersion version, int keepBackups) {
        int stagingRemoved = (staging.clear(version) ? 1 : 0) + staging.clearOthers(version);
        int backupsRemoved = staging.pruneBackups(keepBackups);
        log.append("cleanup", "已清理暂存 " + version.value() + " 及其它版本共 " + stagingRemoved
                + " 个，清理旧备份 " + backupsRemoved + " 份");
        return new Cleanup(stagingRemoved, backupsRemoved);
    }

    /**
     * 启动时的自更新收尾：**读上次的回执** + 清理暂存与旧备份。**不抛异常**。
     *
     * <p>此前这两件事在生产代码里都没有调用点（2026-10-16 审查 P1-5）：回执从不展示，
     * `pkg.zip` 与解包副本永不清理（每次升级/失败白占 50–110 MB），
     * `Update.BackupKeep` 成了死配置。收尾失败不影响启动，只在日志里留痕。</p>
     *
     * <p><b>换装可能还在跑时什么都不删</b>（2026-10-16 第二轮审查 P1-②）：用户在换装那几秒里
     * 再启动一次 App 时，helper 正要从 {@code <版本>/new} 拷文件、回滚还要用 {@code backup/}，
     * 这时清掉会把本次升级毁掉（而且回执会把锅甩给包：新包里没有这个文件）。</p>
     *
     * @param runningVersion 本次运行的版本（它的暂存目录清掉，其它版本的也一并清）
     * @param keepBackups    备份保留份数（来自设置 `Update.BackupKeep`）
     * @return 报告（回执 / 上次换装是否根本没开始 / 是否可能还在进行 / 清理计数 / 情况说明）
     */
    public StartupReport onStartup(AhaVersion runningVersion, int keepBackups) {
        Optional<UpdateResult> result = consumeLastResult();
        boolean planLeft = Files.isRegularFile(paths.planFile());
        boolean markerLeft = Files.isRegularFile(paths.helperStartedFile());
        boolean swapInFlight = swapMayBeInFlight(planLeft, markerLeft, result.isEmpty());
        String warning = null;
        int stagingCleared = 0;
        int backupsRemoved = 0;
        if (swapInFlight) {
            warning = "换装可能还在进行（" + paths.helperStartedFile()
                    + " 是刚写的）——本次不动暂存与备份，也不动计划文件";
            log.append("cleanup", warning);
        } else {
            try {
                Cleanup done = cleanupAfterSwap(runningVersion, keepBackups);
                stagingCleared = done.stagingRemoved();
                backupsRemoved = done.backupsRemoved();
            } catch (UpdateException e) {
                warning = e.getMessage();
                log.append("cleanup", "启动清理失败（不影响使用）：" + e.getMessage());
            }
            if (planLeft && markerLeft && result.isEmpty()) {
                // helper 起来过、却没留下回执（中途被杀 / 卡在中途）：把残留报告出来
                warning = "上次换装没有留下回执（可能中途失败了），已把残留暂存清掉";
                log.append("cleanup", warning);
            }
            // 换装 helper 用完就该消失（设计 §8.2 第 5 项的验收口径）：`.ps1` 自己删自己，
            // `.cmd` 是正在运行的批处理删不掉，由这里补刀 —— 不删的话用户「升级完看目录」
            // 会看到它，以为没生效。下次换装会重新拷（2026-10-16 第二轮审查 P2-④）。
            deleteHelperFiles();
            // 计划文件只用来判断「上一轮到底有没有跑起来」，报完就删，免得下次再报一遍；
            // 「已启动」标记同理：helper 正常结束时已自删，这里是兜底（比如 helper 被杀的残留）。
            try {
                Files.deleteIfExists(paths.helperStartedFile());
                Files.deleteIfExists(paths.planFile());
            } catch (IOException e) {
                log.append("cleanup", "清不掉旧的换装计划或启动标记（无碍）：" + e.getMessage());
            }
        }
        boolean helperNeverRan = planLeft && !markerLeft && result.isEmpty();
        return new StartupReport(result, helperNeverRan, swapInFlight, stagingCleared,
                backupsRemoved, warning);
    }

    /**
     * 换装是否可能还在跑：写了计划 + helper 自报过启动 + 还没有回执，且启动标记是**刚写的**。
     *
     * <p>为什么看标记的时间：helper 若被机器级策略拦下后死掉、或者中途被杀，残留物会永远
     * 停在那 —— 加一个窗口（{@link #SWAP_IN_FLIGHT_WINDOW}），超时就当残留清掉，
     * 免得暂存磁盘永远不回收。</p>
     *
     * @param planLeft   计划文件是否还在
     * @param markerLeft 「已启动」标记是否还在
     * @param noResult   回执是否不存在
     * @return 可能在跑返回 {@code true}
     */
    private boolean swapMayBeInFlight(boolean planLeft, boolean markerLeft, boolean noResult) {
        if (!planLeft || !markerLeft || !noResult) {
            return false;
        }
        try {
            Instant started = Files.getLastModifiedTime(paths.helperStartedFile()).toInstant();
            return Duration.between(started, Instant.now())
                    .compareTo(SWAP_IN_FLIGHT_WINDOW) < 0;
        } catch (IOException e) {
            log.append("cleanup", "读不到 helper 启动标记的时间（按『不在进行』处理）：" + e.getMessage());
            return false;
        }
    }

    /** 删掉用完的换装 helper（`.cmd` / `.ps1`；POSIX 的入口也一并试，不存在时无害）。 */
    private void deleteHelperFiles() {
        for (String name : List.of(UpdateLauncher.WINDOWS_ENTRY, UpdateLauncher.WINDOWS_SCRIPT,
                UpdateLauncher.POSIX_ENTRY)) {
            try {
                Files.deleteIfExists(paths.root().resolve(name));
            } catch (IOException e) {
                log.append("cleanup", "helper 删不掉（无碍）：" + name + "（" + e.getMessage() + "）");
            }
        }
    }

    /**
     * 当前运行版本。
     *
     * <p>{@code AppVersion.buildVersion()} 优先（带构建号，与发布资产同名），解析不了退回
     * {@code version()}。</p>
     *
     * @return 版本
     */
    public static AhaVersion runningVersion() {
        try {
            return AhaVersion.parse(AppVersion.buildVersion());
        } catch (RuntimeException e) {
            return AhaVersion.parse(AppVersion.version());
        }
    }

    /**
     * 换装可能还在跑的判定窗口：helper 自报启动后，最慢多久算「一定结束了」。
     *
     * <p>取 10 分钟：helper 自身的等待上限是 60s，剩下的就是拷贝与逐文件哈希自证（
     * 一个包 25–55 MB，慢盘 / 杀毒软件扫描下也不至于超过它）。</p>
     */
    public static final Duration SWAP_IN_FLIGHT_WINDOW = Duration.ofMinutes(10);

    /**
     * 启动收尾报告。
     *
     * @param result         上次换装的回执（没有则为空）
     * @param helperNeverRan 上一轮写了计划、helper 却没自报启动（**回执也不会存在**）——
     *                       这一条以前是不可见的「静默什么都没发生」
     * @param swapInFlight   换装可能**还在进行**（回执没出、启动标记是刚写的）：本次什么都没动，
     *                       换装结束后下次启动会给出结果
     * @param stagingCleared 清掉的暂存目录数（如实计数，不再固定 +1）
     * @param backupsRemoved 清掉的旧备份份数
     * @param warning        没做清理 / 清理失败 / 残留说明；无事为 {@code null}
     */
    public record StartupReport(Optional<UpdateResult> result, boolean helperNeverRan,
                                boolean swapInFlight, int stagingCleared, int backupsRemoved,
                                String warning) {
    }

    /**
     * 一次收尾清理的计数。
     *
     * @param stagingRemoved 清掉的暂存目录数
     * @param backupsRemoved 清掉的旧备份份数
     */
    public record Cleanup(int stagingRemoved, int backupsRemoved) {
    }

    /**
     * 升级日志（只读）。
     *
     * @return 日志
     */
    public UpdateLog log() {
        return log;
    }

    /**
     * 暂存区（只读用途）。
     *
     * @return 暂存区
     */
    public StagingArea staging() {
        return staging;
    }

    /** 从发布页的候选里取期望校验和（没有 SHA256SUMS 或没有本资产的行时为空）。 */
    private String expectedChecksum(ReleaseFilter.ChannelRelease release, AssetInfo asset) {
        Optional<AssetInfo> sums = release.assets().stream()
                .filter(each -> SUMS_ASSET.equals(each.name()))
                .findFirst();
        if (sums.isEmpty()) {
            log.append("checksum", "该版本未提供 " + SUMS_ASSET + "，退到包内清单 + 落地前自证");
            return null;
        }
        try {
            PackageDownloader.Downloaded downloaded = downloader.download(sums.get(),
                    staging.packageZip(release.version()).getParent());
            Map<String, String> parsed = Checksums.parse(Files.readString(downloaded.file()));
            Optional<String> digest = Checksums.digestOf(parsed, asset.name());
            if (digest.isEmpty()) {
                log.append("checksum", SUMS_ASSET + " 里没有 " + asset.name() + " 的行");
            }
            return digest.orElse(null);
        } catch (UpdateException e) {
            // 校验和资产下载失败：不当作致命（退到包内清单），但要留痕
            log.append("checksum", "下载 " + SUMS_ASSET + " 失败，退到包内清单：" + e.getMessage());
            return null;
        } catch (IOException e) {
            log.append("checksum", "读取 " + SUMS_ASSET + " 失败，退到包内清单：" + e.getMessage());
            return null;
        }
    }

    /** 读当前已装版本的包内清单（拿不到返回 {@code null}：老版本可能根本没有清单）。 */
    private PackageManifest readInstalledManifest(InstallLayout layout) {
        Path installed = layout.root().resolve("AHA-PACKAGE.json");
        if (!Files.isRegularFile(installed)) {
            return null;
        }
        try {
            return PackageManifest.read(installed);
        } catch (UpdateException e) {
            log.append("plan", "已装清单读不出来（按「只新增」处理）：" + e.getMessage());
            return null;
        }
    }

    /**
     * 检查结果。
     *
     * @param channel    通道
     * @param kind       形态
     * @param channel  通道
     * @param kind     形态
     * @param platform 本机平台
     * @param current  当前版本
     * @param release  最新候选版本
     * @param asset    该版本里命中的资产
     * @param newer    是否比当前新（{@code false} 表示「已是最新或可降级」）
     */
    public record Check(ReleaseChannel channel, UpdateKind kind, PlatformTag platform,
                        AhaVersion current, ReleaseFilter.ChannelRelease release, AssetInfo asset,
                        boolean newer) {
    }

    /**
     * 暂存结果。
     *
     * @param version     目标版本
     * @param packageRoot 解压后的新包根目录
     * @param packageZip  下载到的包
     * @param manifest    包内清单
     * @param checksumVerified 是否验过发布页校验和。{@code false} 表示该版本未提供
     *        {@code SHA256SUMS}（界面要**照实说**「该版本未提供发布页校验和」，不能假装验过）
     */
    public record Staged(AhaVersion version, Path packageRoot, Path packageZip,
                         PackageManifest manifest, boolean checksumVerified) {
    }
}
