package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        PackageDownloader.Downloaded downloaded = downloader.download(check.asset(),
                staging.packageZip(version).getParent(), expected, progress, cancel);
        log.append("download", check.asset().name() + " → " + downloaded.file()
                + "（校验" + (downloaded.checksumVerified() ? "通过" : "未提供") + "）");

        Path packageRoot = staging.newPackageDir(version);
        int files = PackageUnpacker.unpack(downloaded.file(), packageRoot);
        PackageManifest manifest = PackageManifest.read(packageRoot.resolve("AHA-PACKAGE.json"));
        manifest.verify(packageRoot, check.kind(), version, check.platform());
        log.append("unpack", "解出 " + files + " 个文件，包内清单核对通过（"
                + manifest.fileList().size() + " 项）");
        return new Staged(version, packageRoot, downloaded.file(), manifest);
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
        UpdatePlan plan = UpdatePlan.create(staged.version(), layout.root(), staged.packageRoot(),
                staging.backupDir(backupVersion), ProcessHandle.current().pid(),
                layout.restartCommand(), actions);
        plan.write(paths.planFile());
        UpdateLauncher.installHelpers(layout.root(), paths);
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
        Process helper = UpdateLauncher.launch(paths.root(), planFile);
        log.append("launch", "已拉起换装 helper（pid " + helper.pid() + "），App 即将退出");
        return helper;
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
     */
    public void cleanupAfterSwap(AhaVersion version, int keepBackups) {
        staging.clear(version);
        int removed = staging.pruneBackups(keepBackups);
        log.append("cleanup", "已清理暂存 " + version.value() + "，清理旧备份 " + removed + " 份");
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
     */
    public record Staged(AhaVersion version, Path packageRoot, Path packageZip,
                         PackageManifest manifest) {
    }
}
