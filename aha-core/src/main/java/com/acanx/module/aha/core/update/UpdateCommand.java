package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.AppVersion;
import com.acanx.module.aha.common.exception.UpdateException;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 升级的**无界面入口**：{@code --update-to <版本> [--yes]}（issue #51 §7 P2、§11）。
 *
 * <p>P2 阶段先用启动参数触发（`AhaDesktop.bat --update-to 0.1.3.00243 --yes`），P3 有了界面之后
 * 它降级为「高级 / 脚本化入口」但仍然保留 —— 界面点不下去的时候，它是最快的排查手段。</p>
 *
 * <p>为什么放 {@code aha-core} 而不是桌面端：CLI 侧的 `aha update`（后置）要复用同一条路径，
 * 而这里没有任何 JavaFX 依赖。桌面端只在 {@code main} 开头问一句
 * 「是不是升级命令？」是就执行并退出，不再往下走界面启动。</p>
 *
 * <p>用法：</p>
 *
 * <pre>
 * --update-to 0.1.3.00243 [--yes] [--channel beta] [--kind desktop-jvm] [--check]
 * </pre>
 *
 * <p>{@code --yes} 的含义：非交互环境下确认执行换装。不给 {@code --yes} 时只做到「暂存 + 打印计划」，
 * 不拉起 helper、不退出界面 —— 避免脚本误触就把 App 换掉。</p>
 *
 * @since 0.2.0
 */
public final class UpdateCommand {

    /** 触发参数。 */
    public static final String ARG_UPDATE_TO = "--update-to";

    private UpdateCommand() {
    }

    /**
     * 解析出来的选项。
     *
     * @param targetVersion 目标版本
     * @param channel       通道
     * @param kind          形态（默认取运行形态：JVM 便携包 → {@code desktop-jvm}）
     * @param confirmed     是否已确认（{@code --yes}）
     * @param checkOnly     只检查不下载（{@code --check}）
     */
    public record Options(AhaVersion targetVersion, ReleaseChannel channel, UpdateKind kind,
                          boolean confirmed, boolean checkOnly) {
    }

    /**
     * 从命令行参数里解析升级选项。
     *
     * @param args 参数
     * @return 选项；不是升级命令时为空
     * @throws UpdateException 参数写了但不合法（版本不合法 / 通道不认识 / 形态不认识）
     */
    public static Optional<Options> parse(List<String> args) {
        String version = null;
        ReleaseChannel channel = ReleaseChannel.DEFAULT;
        UpdateKind kind = null;
        boolean confirmed = false;
        boolean checkOnly = false;
        for (int index = 0; index < args.size(); index++) {
            String arg = args.get(index);
            switch (arg) {
                case ARG_UPDATE_TO -> version = value(args, ++index, arg);
                case "--channel" -> channel = channelOf(value(args, ++index, arg));
                case "--kind" -> kind = kindOf(value(args, ++index, arg));
                case "--yes" -> confirmed = true;
                case "--check" -> checkOnly = true;
                default -> {
                    // 不是本命令的参数：留给界面启动去处理
                }
            }
        }
        if (version == null) {
            return Optional.empty();
        }
        AhaVersion parsed;
        try {
            parsed = AhaVersion.parse(version);
        } catch (RuntimeException e) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "目标版本不合法：" + version + "（应形如 0.1.3 / 0.1.3.00243 / 0.1.3.00243.10161125）", e);
        }
        return Optional.of(new Options(parsed, channel, kind, confirmed, checkOnly));
    }

    /**
     * 执行升级流程（同步、阻塞；调用方通常在 {@code main} 里）。
     *
     * @param options 选项
     * @param layout  安装布局
     * @param paths   路径布局
     * @param out     输出（进度与计划摘要都写这里；界面入口会换成窗口里的状态行）
     * @return 退出码：0 = 成功（或只检查）；1 = 失败
     */
    public static int run(Options options, InstallLayout layout, UpdatePaths paths, PrintStream out) {
        UpdateService service = new UpdateService(paths);
        AhaVersion current = AhaVersion.parse(AppVersion.buildVersion());
        UpdateKind kind = options.kind() == null
                ? (layout.jarBased() ? UpdateKind.DESKTOP_JVM : UpdateKind.DESKTOP_NATIVE)
                : options.kind();
        PlatformTag platform = PlatformTag.detect();
        try {
            out.println("当前版本：" + current.value() + "；通道 " + options.channel().key()
                    + "；形态 " + kind.targetWord() + "；平台 " + platform.value());
            List<ReleaseFilter.ChannelRelease> candidates = service.list(options.channel(), kind);
            out.println("该通道可用版本：" + candidates.size() + " 个");
            UpdateService.Check check = service.check(options.channel(), kind, platform, current);
            out.println("目标版本：" + check.release().version().value()
                    + "（" + check.asset().name() + "，"
                    + (check.newer() ? "升级" : "降级或重装") + "）");
            if (options.checkOnly()) {
                return 0;
            }
            if (!options.confirmed()) {
                out.println("未带 --yes：只检查下载与计划，不拉起换装 helper、不退出界面。");
            }
            UpdateService.Staged staged = service.stage(check,
                    (bytes, total) -> out.print("\r已下载 " + bytes + " / " + total + " 字节"),
                    PackageDownloader.CancelSignal.NEVER);
            out.println();
            out.println("已暂存：" + staged.packageRoot());
            Path plan = service.prepareSwap(staged, layout, current);
            out.println("换装计划：" + plan);
            if (!options.confirmed()) {
                return 0;
            }
            service.launchHelper(plan);
            out.println("已拉起换装 helper，请退出本程序以完成替换（helper 会等本进程退出）。");
            return 0;
        } catch (UpdateException e) {
            out.println("升级失败：" + e.getMessage());
            service.log().append("error", e.code() + " " + e.getMessage());
            return 1;
        }
    }

    /**
     * 参数是否包含升级命令（桌面端 {@code main} 用来决定要不要跳过界面启动）。
     *
     * @param args 参数
     * @return 是升级命令返回 {@code true}
     */
    public static boolean isUpdateInvocation(List<String> args) {
        return args.contains(ARG_UPDATE_TO);
    }

    private static String value(List<String> args, int index, String name) {
        if (index < 1 || index >= args.size() || args.get(index).startsWith("--")) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "参数 " + name + " 缺少取值");
        }
        return args.get(index);
    }

    private static ReleaseChannel channelOf(String text) {
        ReleaseChannel channel = ReleaseChannel.of(text);
        if (channel == null) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "不认识的通道：" + text + "（可选 stable / beta / alpha）");
        }
        return channel;
    }

    private static UpdateKind kindOf(String text) {
        UpdateKind kind = UpdateKind.of(text);
        if (kind == null) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "不认识的形态：" + text + "（可选 " + String.join(" / ", kindNames()) + "）");
        }
        return kind;
    }

    private static List<String> kindNames() {
        List<String> names = new ArrayList<>();
        for (UpdateKind kind : UpdateKind.values()) {
            names.add(kind.targetWord().toLowerCase(Locale.ROOT));
        }
        return names;
    }
}
